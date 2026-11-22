#include <dlfcn.h>
#include <GLES2/gl2.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <cmath>
#include <vector>
#include <algorithm>
#include <mutex>

#define LOG_TAG "FrancisGL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

struct FrancisConfig {
    bool loaded = false;
    float texScale = 0.50f;   // 0.25f .. 1.00f
    float lodBias = 2.0f;     // 0.0f .. 4.0f
    int maxTexSize = 256;     // optional cap
    bool enabled = true;
};

static FrancisConfig g_cfg;
static std::mutex g_initMutex;
static void* g_glesHandle = nullptr;

typedef void (*PFN_glTexImage2D)(
    GLenum target, GLint level, GLint internalformat,
    GLsizei width, GLsizei height, GLint border,
    GLenum format, GLenum type, const void* pixels);

typedef void (*PFN_glCompressedTexImage2D)(
    GLenum target, GLint level, GLenum internalformat,
    GLsizei width, GLsizei height, GLint border,
    GLsizei imageSize, const void* data);

typedef void (*PFN_glTexParameteri)(GLenum target, GLenum pname, GLint param);
typedef void (*PFN_glTexParameterf)(GLenum target, GLenum pname, GLfloat param);
typedef void (*PFN_glGenerateMipmap)(GLenum target);

static PFN_glTexImage2D real_glTexImage2D = nullptr;
static PFN_glCompressedTexImage2D real_glCompressedTexImage2D = nullptr;
static PFN_glTexParameteri real_glTexParameteri = nullptr;
static PFN_glTexParameterf real_glTexParameterf = nullptr;
static PFN_glGenerateMipmap real_glGenerateMipmap = nullptr;

static void* resolveSymbol(const char* name) {
    void* sym = dlsym(RTLD_NEXT, name);
    if (!sym) {
        if (!g_glesHandle) {
            g_glesHandle = dlopen("libGLESv2.so", RTLD_LAZY | RTLD_LOCAL);
            if (!g_glesHandle) {
                g_glesHandle = dlopen("/system/lib64/libGLESv2.so", RTLD_LAZY | RTLD_LOCAL);
            }
            if (!g_glesHandle) {
                g_glesHandle = dlopen("/system/lib/libGLESv2.so", RTLD_LAZY | RTLD_LOCAL);
            }
        }
        if (g_glesHandle) {
            sym = dlsym(g_glesHandle, name);
        }
    }
    return sym;
}

static void loadConfigOnce() {
    if (g_cfg.loaded) return;
    std::lock_guard<std::mutex> lock(g_initMutex);
    if (g_cfg.loaded) return;

    real_glTexImage2D = reinterpret_cast<PFN_glTexImage2D>(resolveSymbol("glTexImage2D"));
    real_glCompressedTexImage2D = reinterpret_cast<PFN_glCompressedTexImage2D>(resolveSymbol("glCompressedTexImage2D"));
    real_glTexParameteri = reinterpret_cast<PFN_glTexParameteri>(resolveSymbol("glTexParameteri"));
    real_glTexParameterf = reinterpret_cast<PFN_glTexParameterf>(resolveSymbol("glTexParameterf"));
    real_glGenerateMipmap = reinterpret_cast<PFN_glGenerateMipmap>(resolveSymbol("glGenerateMipmap"));

    FILE* fp = std::fopen("/data/local/tmp/francis_gl.cfg", "r");
    if (fp) {
        char line[128];
        while (std::fgets(line, sizeof(line), fp)) {
            float fval = 0.0f;
            int ival = 0;
            if (std::sscanf(line, "tex_scale=%f", &fval) == 1) {
                g_cfg.texScale = std::max(0.20f, std::min(1.0f, fval));
            } else if (std::sscanf(line, "lod_bias=%f", &fval) == 1) {
                g_cfg.lodBias = std::max(0.0f, std::min(4.0f, fval));
            } else if (std::sscanf(line, "enabled=%d", &ival) == 1) {
                g_cfg.enabled = (ival != 0);
            }
        }
        std::fclose(fp);
    }

    g_cfg.loaded = true;
    LOGI("FrancisGL initialized: enabled=%d texScale=%.2f lodBias=%.1f",
         g_cfg.enabled ? 1 : 0, g_cfg.texScale, g_cfg.lodBias);
}

// Fast 2x2 / box-filter texture downscaler for GL_RGBA or GL_RGB GL_UNSIGNED_BYTE
static void downsampleTexture(
    const uint8_t* src, int srcW, int srcH,
    uint8_t* dst, int dstW, int dstH, int channels) {
    const float scaleX = static_cast<float>(srcW) / static_cast<float>(dstW);
    const float scaleY = static_cast<float>(srcH) / static_cast<float>(dstH);

    for (int y = 0; y < dstH; ++y) {
        int sy0 = static_cast<int>(y * scaleY);
        int sy1 = std::min(srcH - 1, sy0 + 1);
        for (int x = 0; x < dstW; ++x) {
            int sx0 = static_cast<int>(x * scaleX);
            int sx1 = std::min(srcW - 1, sx0 + 1);

            const uint8_t* p00 = src + (sy0 * srcW + sx0) * channels;
            const uint8_t* p10 = src + (sy0 * srcW + sx1) * channels;
            const uint8_t* p01 = src + (sy1 * srcW + sx0) * channels;
            const uint8_t* p11 = src + (sy1 * srcW + sx1) * channels;

            uint8_t* out = dst + (y * dstW + x) * channels;
            for (int c = 0; c < channels; ++c) {
                int sum = static_cast<int>(p00[c]) + static_cast<int>(p10[c]) +
                          static_cast<int>(p01[c]) + static_cast<int>(p11[c]);
                out[c] = static_cast<uint8_t>(sum >> 2);
            }
        }
    }
}

} // namespace

extern "C" {

__attribute__((visibility("default")))
void glTexImage2D(
    GLenum target, GLint level, GLint internalformat,
    GLsizei width, GLsizei height, GLint border,
    GLenum format, GLenum type, const void* pixels) {
    loadConfigOnce();
    if (!real_glTexImage2D) return;

    if (g_cfg.enabled &&
        g_cfg.texScale < 0.95f &&
        target == GL_TEXTURE_2D &&
        level == 0 &&
        pixels != nullptr &&
        type == GL_UNSIGNED_BYTE &&
        (format == GL_RGBA || format == GL_RGB) &&
        width >= 64 && height >= 64) {

        int channels = (format == GL_RGBA) ? 4 : 3;
        int newW = std::max(16, static_cast<int>(width * g_cfg.texScale));
        int newH = std::max(16, static_cast<int>(height * g_cfg.texScale));

        std::vector<uint8_t> downBuf(static_cast<size_t>(newW) * static_cast<size_t>(newH) * channels);
        downsampleTexture(
            reinterpret_cast<const uint8_t*>(pixels), width, height,
            downBuf.data(), newW, newH, channels);

        real_glTexImage2D(target, level, internalformat, newW, newH, border, format, type, downBuf.data());
        if (real_glTexParameteri) {
            real_glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            real_glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
        return;
    }

    real_glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
}

__attribute__((visibility("default")))
void glCompressedTexImage2D(
    GLenum target, GLint level, GLenum internalformat,
    GLsizei width, GLsizei height, GLint border,
    GLsizei imageSize, const void* data) {
    loadConfigOnce();
    if (!real_glCompressedTexImage2D) return;

    real_glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, data);

    // When mipmap levels exist on compressed textures (ETC2/ASTC), shift GL_TEXTURE_BASE_LEVEL for true GLTools blur!
    if (g_cfg.enabled && target == GL_TEXTURE_2D && level > 0 && g_cfg.lodBias >= 1.0f && real_glTexParameteri) {
        int desiredBase = std::min(level, static_cast<int>(std::floor(g_cfg.lodBias)));
        if (desiredBase > 0) {
            real_glTexParameteri(target, GL_TEXTURE_BASE_LEVEL, desiredBase);
        }
    }
}

__attribute__((visibility("default")))
void glGenerateMipmap(GLenum target) {
    loadConfigOnce();
    if (!real_glGenerateMipmap) return;

    real_glGenerateMipmap(target);

    if (g_cfg.enabled && target == GL_TEXTURE_2D && g_cfg.lodBias >= 1.0f && real_glTexParameteri) {
        int baseLvl = std::max(1, std::min(3, static_cast<int>(std::floor(g_cfg.lodBias))));
        real_glTexParameteri(target, GL_TEXTURE_BASE_LEVEL, baseLvl);
    }
}

__attribute__((visibility("default")))
void glTexParameterf(GLenum target, GLenum pname, GLfloat param) {
    loadConfigOnce();
    if (!real_glTexParameterf) return;

    // Disable anisotropic filtering (0x84FE = GL_TEXTURE_MAX_ANISOTROPY_EXT) for lower GPU bandwidth
    if (g_cfg.enabled && pname == 0x84FE) {
        real_glTexParameterf(target, pname, 1.0f);
        return;
    }
    real_glTexParameterf(target, pname, param);
}

} // extern "C"

package com.francis.optimize;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RootEngine {

    public static class ExecResult {
        public final boolean success;
        public final String output;
        public ExecResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }
    }

    public static ExecResult runRoot(String... commands) {
        Process proc = null;
        DataOutputStream os = null;
        StringBuilder out = new StringBuilder();
        try {
            proc = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(proc.getOutputStream());
            for (String cmd : commands) {
                os.writeBytes(cmd + "\n");
            }
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader stdOut = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stdErr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            String line;
            while ((line = stdOut.readLine()) != null) {
                out.append(line).append("\n");
            }
            while ((line = stdErr.readLine()) != null) {
                out.append(line).append("\n");
            }
            int code = proc.waitFor();
            return new ExecResult(code == 0, out.toString().trim());
        } catch (Exception e) {
            return new ExecResult(false, e.getMessage() != null ? e.getMessage() : "Root unavailable");
        } finally {
            try { if (os != null) os.close(); } catch (Exception ignored) {}
            try { if (proc != null) proc.destroy(); } catch (Exception ignored) {}
        }
    }

    public static boolean hasRootAccess() {
        ExecResult res = runRoot("id");
        return res.success && res.output.contains("uid=0");
    }

    public static int[] getPhysicalSize() {
        ExecResult res = runRoot("wm size");
        if (res.output != null) {
            Matcher m = Pattern.compile("Physical size:\\s*(\\d+)x(\\d+)").matcher(res.output);
            if (m.find()) {
                try {
                    return new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) };
                } catch (Exception ignored) {}
            }
        }
        return new int[]{ 1280, 720 };
    }

    public static int getPhysicalDensity() {
        ExecResult res = runRoot("wm density");
        if (res.output != null) {
            Matcher m = Pattern.compile("Physical density:\\s*(\\d+)").matcher(res.output);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (Exception ignored) {}
            }
        }
        return 240;
    }

    public static ExecResult applyBlurProfile(
            Context ctx,
            String pkg,
            int resPercent,
            int texPercent,
            int lodTenths,
            int fpsCap,
            boolean enableGlHook,
            boolean disableHwOverlays,
            boolean disableAnims,
            boolean trimRam) {

        int[] phys = getPhysicalSize();
        int physDpi = getPhysicalDensity();

        double scale = Math.max(0.25, Math.min(1.0, resPercent / 100.0));
        int newW = Math.max(240, ((int) Math.round(phys[0] * scale) / 8) * 8);
        int newH = Math.max(240, ((int) Math.round(phys[1] * scale) / 8) * 8);
        int newDpi = Math.max(100, (int) Math.round(physDpi * scale));

        StringBuilder script = new StringBuilder();

        // 1. Write native GL hook config
        double texScale = Math.max(0.25, Math.min(1.0, texPercent / 100.0));
        double lodBias = lodTenths / 10.0;
        script.append(String.format(Locale.US,
                "printf 'enabled=%d\\ntex_scale=%.2f\\nlod_bias=%.1f\\n' > /data/local/tmp/francis_gl.cfg; ",
                enableGlHook ? 1 : 0, texScale, lodBias));
        script.append("chmod 644 /data/local/tmp/francis_gl.cfg; ");

        // 2. Deploy native OpenGL ES hook library (libfrancisgl.so) if toggled ON
        if (enableGlHook) {
            ApplicationInfo ai = ctx.getApplicationInfo();
            String libPath = ai.nativeLibraryDir + "/libfrancisgl.so";
            if (new File(libPath).exists()) {
                script.append("cp '").append(libPath).append("' /data/local/tmp/libfrancisgl.so; ");
                script.append("chmod 755 /data/local/tmp/libfrancisgl.so; ");
                script.append("setenforce 0 2>/dev/null; ");
                script.append("setprop wrap.").append(pkg).append(" 'LD_PRELOAD=/data/local/tmp/libfrancisgl.so'; ");
            }
        } else {
            script.append("setprop wrap.").append(pkg).append(" ''; ");
        }

        // 3. Apply Orientation-Preserving Resolution & DPI Downscale (Works on Android 9 Pie MSI App Player!)
        if (resPercent < 100) {
            script.append("wm size ").append(newW).append("x").append(newH).append("; ");
            script.append("wm density ").append(newDpi).append("; ");
            // Also apply Android 12+ GameManager downscale silently if supported
            script.append(String.format(Locale.US, "cmd game downscale %.2f %s 2>/dev/null; ", scale, pkg));
            script.append("cmd game mode performance ").append(pkg).append(" 2>/dev/null; ");
        } else {
            script.append("wm size reset; wm density reset; ");
            script.append("cmd game downscale disable ").append(pkg).append(" 2>/dev/null; ");
        }

        // 4. FPS & EGL swap properties
        script.append("setprop debug.egl.swapinterval 0 2>/dev/null; ");
        script.append("setprop debug.sf.fps ").append(fpsCap).append(" 2>/dev/null; ");

        // 5. Optional manual modules
        if (disableHwOverlays) {
            script.append("service call SurfaceFlinger 1008 i32 1 2>/dev/null; ");
            script.append("setprop debug.hwui.renderer skiagl 2>/dev/null; ");
        }
        if (disableAnims) {
            script.append("settings put global window_animation_scale 0; ");
            script.append("settings put global transition_animation_scale 0; ");
            script.append("settings put global animator_duration_scale 0; ");
        }
        if (trimRam) {
            script.append("am kill-all 2>/dev/null; ");
            script.append("pm trim-caches 9999999999 2>/dev/null; ");
        }

        // 6. Force-stop target game so Unity recreates EGL framebuffer at new low-res blurred size!
        script.append("am force-stop ").append(pkg).append("; ");
        script.append("sleep 0.5; ");
        script.append("monkey -p ").append(pkg).append(" -c android.intent.category.LAUNCHER 1 2>/dev/null; ");

        ExecResult res = runRoot(script.toString());
        String summary = String.format(Locale.US,
                "Applied %dx%d (%d%%) | DPI:%d | Tex:%.2fx | LOD:+%.1f | GLHook:%s",
                newW, newH, resPercent, newDpi, texScale, lodBias, enableGlHook ? "ON" : "OFF");
        return new ExecResult(res.success, summary + (res.output.isEmpty() ? "" : "\n" + res.output));
    }

    public static ExecResult restoreStock(String pkg) {
        String cmd =
                "wm size reset; " +
                "wm density reset; " +
                "setprop wrap." + pkg + " ''; " +
                "setprop wrap.com.dts.freefireth ''; " +
                "setprop wrap.com.dts.freefiremax ''; " +
                "rm -f /data/local/tmp/francis_gl.cfg /data/local/tmp/libfrancisgl.so; " +
                "cmd game downscale disable " + pkg + " 2>/dev/null; " +
                "service call SurfaceFlinger 1008 i32 0 2>/dev/null; " +
                "settings put global window_animation_scale 1; " +
                "settings put global transition_animation_scale 1; " +
                "settings put global animator_duration_scale 1; ";
        return runRoot(cmd);
    }
}

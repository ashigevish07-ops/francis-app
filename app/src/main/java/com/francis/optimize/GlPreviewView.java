package com.francis.optimize;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import java.util.Locale;

public class GlPreviewView extends View {

    private int resPercent = 45;
    private int texPercent = 45;
    private int lodTenths = 25;
    private int fpsCap = 150;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF boundsF = new RectF();
    private final Rect dstRect = new Rect();

    public GlPreviewView(Context context) {
        super(context);
        init();
    }

    public GlPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setFakeBoldText(true);
    }

    public void updateParams(int res, int tex, int lod, int fps) {
        this.resPercent = res;
        this.texPercent = tex;
        this.lodTenths = lod;
        this.fpsCap = fps;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 8 || h <= 8) return;

        float radius = 36f;
        boundsF.set(1f, 1f, w - 1f, h - 1f);
        dstRect.set(0, 0, w, h);

        // Compute low-resolution offscreen bitmap size to show real bilinear blur effect
        float resF = Math.max(0.15f, resPercent / 100f);
        float texF = Math.max(0.18f, texPercent / 100f);
        float lodF = 1.0f - (lodTenths / 70f);
        float combined = Math.max(0.08f, Math.min(1.0f, resF * 0.55f + texF * 0.35f + lodF * 0.10f));

        int lowW = Math.max(24, (int) (w * combined * 0.45f));
        int lowH = Math.max(14, (int) (h * combined * 0.45f));

        Bitmap lowBmp = Bitmap.createBitmap(lowW, lowH, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(lowBmp);

        int horizon = Math.max(2, (int) (lowH * 0.54f));

        // Monochrome sky gradient
        Paint skyP = new Paint();
        skyP.setShader(new LinearGradient(0, 0, 0, horizon,
                Color.rgb(52, 52, 52), Color.rgb(16, 16, 16), Shader.TileMode.CLAMP));
        sc.drawRect(0, 0, lowW, horizon, skyP);

        // Ground
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(24, 24, 24));
        sc.drawRect(0, horizon, lowW, lowH, paint);

        // FF-style structures & cover silhouettes
        paint.setColor(Color.rgb(82, 82, 82));
        sc.drawRect(lowW * 0.08f, horizon - lowH * 0.28f, lowW * 0.25f, horizon + 1, paint);

        paint.setColor(Color.rgb(138, 138, 138));
        sc.drawRect(lowW * 0.31f, horizon - lowH * 0.38f, lowW * 0.46f, horizon + 1, paint);

        paint.setColor(Color.rgb(74, 74, 74));
        sc.drawRect(lowW * 0.68f, horizon - lowH * 0.30f, lowW * 0.90f, horizon + 1, paint);

        // Character silhouette
        paint.setColor(Color.rgb(215, 215, 215));
        float px = lowW * 0.50f;
        float py = horizon + lowH * 0.05f;
        float pw = Math.max(2f, lowW * 0.055f);
        float ph = Math.max(5f, lowH * 0.26f);
        sc.drawCircle(px, py - ph * 0.22f, pw * 0.55f, paint);
        sc.drawRect(px - pw * 0.6f, py, px + pw * 0.6f, py + ph, paint);

        // Perspective lines
        paint.setColor(Color.rgb(76, 76, 76));
        paint.setStrokeWidth(1f);
        sc.drawLine(0, horizon, lowW, horizon, paint);
        sc.drawLine(lowW / 2f, horizon, 0, lowH, paint);
        sc.drawLine(lowW / 2f, horizon, lowW, lowH, paint);

        // Clip rounded frame and draw bilinearly blurred scene
        canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(boundsF, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        bmpPaint.setFilterBitmap(true);
        canvas.drawBitmap(lowBmp, null, dstRect, bmpPaint);
        lowBmp.recycle();

        // Top & Bottom HUD bars
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(165, 0, 0, 0));
        canvas.drawRect(0, 0, w, 56f, paint);
        canvas.drawRect(0, h - 54f, w, h, paint);

        // Crisp white crosshair (HUD stays sharp!)
        float cx = w / 2f;
        float cy = h / 2f;
        paint.setColor(Color.argb(230, 255, 255, 255));
        paint.setStrokeWidth(3f);
        canvas.drawLine(cx - 18f, cy, cx - 6f, cy, paint);
        canvas.drawLine(cx + 6f, cy, cx + 18f, cy, paint);
        canvas.drawLine(cx, cy - 18f, cx, cy - 6f, paint);
        canvas.drawLine(cx, cy + 6f, cx, cy + 18f, paint);

        // HUD Labels
        int outW = (int) (1280 * (resPercent / 100.0));
        int outH = (int) (720 * (resPercent / 100.0));
        textPaint.setTextSize(26f);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(String.format(Locale.US, "LIVE GL BLUR PREVIEW  |  %dx%d (%d%%)", outW, outH, resPercent),
                22f, 38f, textPaint);

        textPaint.setColor(Color.rgb(210, 210, 210));
        canvas.drawText(String.format(Locale.US, "TEX: %.2fx   |   LOD: +%.1f   |   CAP: %d FPS",
                texPercent / 100.0, lodTenths / 10.0, fpsCap), 22f, h - 18f, textPaint);

        canvas.restore();

        // Outer monochrome border
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.rgb(68, 68, 68));
        paint.setStrokeWidth(2.5f);
        canvas.drawRoundRect(boundsF, radius, radius, paint);
    }
}

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

/** ContextActionsContainer's glass surface, with a bounded snapshot for a separate Android popup window. */
public final class DevGramSwiftgramMenuBackground extends Drawable implements View.OnAttachStateChangeListener {
    private final View host, source;
    private final Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final int[] hostLocation = new int[2], sourceLocation = new int[2];
    private Bitmap bitmap;
    private BitmapShader shader;
    private int alpha = 255;
    private static boolean capturing;

    public DevGramSwiftgramMenuBackground(View host, View source) {
        this.host = host; this.source = source;
        host.addOnAttachStateChangeListener(this);
        acquire();
    }

    private void acquire() {
        if (capturing || bitmap != null || source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return;
        capturing = true;
        try {
            bitmap = Bitmap.createBitmap(Math.max(1, source.getWidth() / 8), Math.max(1, source.getHeight() / 8), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            // Hardware-only child layers can be absent on a bitmap canvas. Keep an opaque
            // material base so live message text never bleeds through the menu labels.
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            canvas.scale(.125f, .125f); source.draw(canvas);
            Utilities.stackBlurBitmap(bitmap, 8);
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); blur.setShader(shader);
        } catch (RuntimeException | OutOfMemoryError error) { release(); }
        finally { capturing = false; }
    }

    @Override public void draw(Canvas canvas) {
        if (getBounds().isEmpty()) return;
        boolean dark = Theme.isCurrentThemeDark();
        rect.set(getBounds());
        float radius = Math.min(AndroidUtilities.dp(30), rect.height() / 2);
        if (shader != null && bitmap != null && !bitmap.isRecycled()) {
            host.getLocationOnScreen(hostLocation); source.getLocationOnScreen(sourceLocation);
            matrix.setScale(source.getWidth() / (float) bitmap.getWidth(), source.getHeight() / (float) bitmap.getHeight());
            matrix.postTranslate(sourceLocation[0] - hostLocation[0], sourceLocation[1] - hostLocation[1]); shader.setLocalMatrix(matrix);
            blur.setAlpha(alpha); canvas.drawRoundRect(rect, radius, radius, blur);
        }
        tint.setColor(dark ? 0xe0292929 : 0xc7f9f9f9);
        tint.setAlpha(Math.round((shader == null ? 255 : dark ? 224 : 199) * alpha / 255f));
        canvas.drawRoundRect(rect, radius, radius, tint);
        edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(AndroidUtilities.dpf2(.5f));
        edge.setColor(dark ? 0x35ffffff : 0x60ffffff); edge.setAlpha(Math.round((dark ? 53 : 96) * alpha / 255f));
        rect.inset(AndroidUtilities.dpf2(.25f), AndroidUtilities.dpf2(.25f));
        canvas.drawRoundRect(rect, radius, radius, edge);
    }

    @Override public boolean getPadding(Rect padding) { int p = AndroidUtilities.dp(8); padding.set(p, p, p, p); return true; }
    @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void onViewAttachedToWindow(View view) { acquire(); }
    @Override public void onViewDetachedFromWindow(View view) { release(); }
    private void release() { blur.setShader(null); shader = null; if (bitmap != null) { bitmap.recycle(); bitmap = null; } }
}

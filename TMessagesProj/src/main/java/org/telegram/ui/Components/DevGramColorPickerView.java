package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

// DevGram: палитра цвета — квадрат насыщенности/яркости и полоса оттенка. Шейдеры создаются только
// при смене размера или оттенка, не на каждом кадре.
public class DevGramColorPickerView extends View {

    public interface Listener {
        void onColor(int color);
    }

    private final float[] hsv = {0f, 1f, 1f};
    private final Paint svPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF svRect = new RectF();
    private final RectF hueRect = new RectF();
    private float shaderHue = -1;
    private int shaderW, shaderH;
    private Listener listener;
    private int dragging; // 0 — нет, 1 — квадрат, 2 — оттенок

    public DevGramColorPickerView(Context context) {
        super(context);
        knob.setStyle(Paint.Style.STROKE);
        knob.setStrokeWidth(AndroidUtilities.dp(3));
        knob.setColor(Color.WHITE);
        knob.setShadowLayer(AndroidUtilities.dp(2), 0, 0, 0x66000000);
        setLayerType(LAYER_TYPE_SOFTWARE, knob); // тень у кольца-ползунка
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setColor(int color) {
        Color.colorToHSV(color, hsv);
        invalidate();
    }

    public int getColor() {
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(250), MeasureSpec.EXACTLY));
    }

    private void layoutRects() {
        float pad = AndroidUtilities.dp(20);
        svRect.set(pad, AndroidUtilities.dp(12), getWidth() - pad, getHeight() - AndroidUtilities.dp(62));
        hueRect.set(pad, getHeight() - AndroidUtilities.dp(40), getWidth() - pad, getHeight() - AndroidUtilities.dp(16));
    }

    private void updateShaders() {
        int w = (int) svRect.width(), h = (int) svRect.height();
        if (shaderHue == hsv[0] && shaderW == w && shaderH == h) return;
        shaderHue = hsv[0];
        shaderW = w;
        shaderH = h;
        int pure = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});
        Shader sat = new LinearGradient(svRect.left, 0, svRect.right, 0, Color.WHITE, pure, Shader.TileMode.CLAMP);
        Shader val = new LinearGradient(0, svRect.top, 0, svRect.bottom, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);
        svPaint.setShader(new ComposeShader(val, sat, PorterDuff.Mode.MULTIPLY));
        if (huePaint.getShader() == null || hueRect.width() != shaderW) {
            int[] colors = new int[7];
            for (int i = 0; i < 7; i++) colors[i] = Color.HSVToColor(new float[]{i * 60f % 360f, 1f, 1f});
            colors[6] = colors[0];
            huePaint.setShader(new LinearGradient(hueRect.left, 0, hueRect.right, 0, colors, null, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        layoutRects();
        updateShaders();
        float r = AndroidUtilities.dp(14);
        canvas.drawRoundRect(svRect, r, r, svPaint);
        canvas.drawRoundRect(hueRect, hueRect.height() / 2f, hueRect.height() / 2f, huePaint);

        float sx = svRect.left + hsv[1] * svRect.width();
        float sy = svRect.top + (1f - hsv[2]) * svRect.height();
        knobFill.setColor(getColor());
        canvas.drawCircle(sx, sy, AndroidUtilities.dp(11), knobFill);
        canvas.drawCircle(sx, sy, AndroidUtilities.dp(11), knob);

        float hx = hueRect.left + hsv[0] / 360f * hueRect.width();
        knobFill.setColor(Color.HSVToColor(new float[]{hsv[0], 1f, 1f}));
        canvas.drawCircle(hx, hueRect.centerY(), AndroidUtilities.dp(13), knobFill);
        canvas.drawCircle(hx, hueRect.centerY(), AndroidUtilities.dp(13), knob);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (y >= hueRect.top - AndroidUtilities.dp(12)) {
                    dragging = 2;
                } else if (svRect.contains(x, y)) {
                    dragging = 1;
                } else {
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                // fall through
            case MotionEvent.ACTION_MOVE:
                if (dragging == 1) {
                    hsv[1] = clamp((x - svRect.left) / svRect.width());
                    hsv[2] = 1f - clamp((y - svRect.top) / svRect.height());
                } else if (dragging == 2) {
                    hsv[0] = clamp((x - hueRect.left) / hueRect.width()) * 359.9f;
                }
                invalidate();
                if (listener != null && dragging != 0) listener.onColor(getColor());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = 0;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return false;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}

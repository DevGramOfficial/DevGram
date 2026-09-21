package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * DevGram: строка выбора цвета одного уровня «последнего захода».
 * Слева — крупное превью текущего цвета + HEX-подпись; справа — ряд свотчей:
 *   индекс 0        — «Авто» (кольцо, цвет по умолчанию для уровня),
 *   индексы 1..N    — пресеты палитры,
 *   индекс N+1      — «Своя» (радужный свотч → ввод HEX).
 * Клик отдаётся через onPick(index). Выбранный свотч обведён кольцом с галочкой.
 */
@SuppressLint("ViewConstructor")
public class DevGramColorTierRow extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hexPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Theme.ResourcesProvider rp;

    private final int[] palette;
    private final int autoColor;      // цвет свотча «Авто» (эффективный дефолт уровня)
    private int currentColor;         // 0 = авто, иначе ARGB
    private Utilities.Callback<Integer> onPick;

    // геометрия свотчей: [preview][gap][auto][p1..pN][custom]
    private final float[] swatchCx;   // центр X каждого свотча (0..N+1)
    private final int swatchCount;    // N+2

    public DevGramColorTierRow(Context context, Theme.ResourcesProvider rp, int[] palette, int autoColor, int currentColor) {
        super(context);
        this.rp = rp;
        this.palette = palette;
        this.autoColor = autoColor;
        this.currentColor = currentColor;
        this.swatchCount = palette.length + 2;
        this.swatchCx = new float[swatchCount];
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(AndroidUtilities.dp(2.5f));
        check.setStyle(Paint.Style.STROKE);
        check.setStrokeWidth(AndroidUtilities.dp(2f));
        check.setStrokeCap(Paint.Cap.ROUND);
        hexPaint.setTextSize(AndroidUtilities.dp(13));
        hexPaint.setTypeface(AndroidUtilities.bold());
    }

    public void setOnPick(Utilities.Callback<Integer> cb) { this.onPick = cb; }

    public void setCurrentColor(int color) { this.currentColor = color; invalidate(); }

    private int effectiveColor() {
        return currentColor != 0 ? currentColor : autoColor;
    }

    private int swatchColorAt(int i) {
        if (i == 0) return autoColor;                       // «Авто»
        if (i <= palette.length) return palette[i - 1];     // пресеты
        return 0;                                           // «Своя» (рисуется градиентом)
    }

    // выбран ли свотч i при текущем currentColor
    private boolean isSelected(int i) {
        if (i == 0) return currentColor == 0;
        if (i <= palette.length) return currentColor == palette[i - 1];
        // «Своя» — если цвет задан и его нет среди пресетов
        if (currentColor == 0) return false;
        for (int p : palette) {
            if (p == currentColor) return false;
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(72));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int h = getMeasuredHeight();
        final int cy = h / 2;
        final int previewR = AndroidUtilities.dp(20);
        final int previewCx = AndroidUtilities.dp(20) + previewR;

        // — превью текущего цвета —
        fill.setColor(effectiveColor());
        canvas.drawCircle(previewCx, cy, previewR, fill);
        ring.setColor(Theme.getColor(Theme.key_divider, rp));
        canvas.drawCircle(previewCx, cy, previewR, ring);

        // — HEX-подпись под превью —
        hexPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, rp));
        String label = currentColor == 0 ? "Авто" : String.format("#%06X", currentColor & 0xFFFFFF);
        float tw = hexPaint.measureText(label);
        canvas.drawText(label, previewCx - tw / 2f, h - AndroidUtilities.dp(6), hexPaint);

        // — ряд свотчей справа —
        final int r = AndroidUtilities.dp(13);
        final int left = previewCx + previewR + AndroidUtilities.dp(18);
        final int right = getMeasuredWidth() - AndroidUtilities.dp(16) - r;
        final float step = swatchCount > 1 ? (right - left) / (float) (swatchCount - 1) : 0;

        for (int i = 0; i < swatchCount; i++) {
            float cx = left + step * i;
            swatchCx[i] = cx;
            if (i == 0) {
                // «Авто» — кольцо
                ring.setColor(autoColor);
                canvas.drawCircle(cx, cy - AndroidUtilities.dp(4), r - AndroidUtilities.dp(1), ring);
            } else if (i <= palette.length) {
                fill.setColor(swatchColorAt(i));
                canvas.drawCircle(cx, cy - AndroidUtilities.dp(4), r, fill);
            } else {
                // «Своя» — радужный свотч (ввод HEX)
                fill.setShader(new LinearGradient(cx - r, cy - AndroidUtilities.dp(4) - r, cx + r, cy - AndroidUtilities.dp(4) + r,
                        new int[]{0xFFE91E63, 0xFFEAEA1E, 0xFF4CAF50, 0xFF2196F3}, null, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy - AndroidUtilities.dp(4), r, fill);
                fill.setShader(null);
            }
            if (isSelected(i)) {
                ring.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, rp));
                canvas.drawCircle(cx, cy - AndroidUtilities.dp(4), r + AndroidUtilities.dp(4), ring);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            int best = 0;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < swatchCount; i++) {
                float d = Math.abs(x - swatchCx[i]);
                if (d < bestD) { bestD = d; best = i; }
            }
            performHapticFeedback(3);
            if (onPick != null) onPick.run(best);
            return true;
        }
        return true;
    }

    // индекс свотча «Своя» (для фрагмента: открыть HEX-диалог)
    public int customIndex() { return swatchCount - 1; }
    // индекс свотча «Авто»
    public int autoIndex() { return 0; }
    // цвет пресета по индексу свотча (1..N)
    public int presetColor(int swatchIndex) { return palette[swatchIndex - 1]; }
    public int presetCount() { return palette.length; }
}

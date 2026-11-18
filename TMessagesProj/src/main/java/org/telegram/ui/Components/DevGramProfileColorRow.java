package org.telegram.ui.Components;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramColorCode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

/** The provided editor color row: native action row with a fading alpha band. */
public final class DevGramProfileColorRow extends FrameLayout {
    private final Band band;
    private final View cell;
    private final CharSequence title;

    private DevGramProfileColorRow(Context context, View view, CharSequence str) {
        super(context);
        this.title = str;
        this.cell = view;
        Band band = new Band(context);
        this.band = band;
        addView(view, new FrameLayout.LayoutParams(-1, -2));
        addView(band, new FrameLayout.LayoutParams(-1, -1, 5));
    }

    public static DevGramProfileColorRow create(Context context, CharSequence str, int i, final Runnable runnable) {
        DevGramReferenceHeaderRow editSettingRow = new DevGramReferenceHeaderRow(context, false);
        DevGramProfileColorRow editColorRow = new DevGramProfileColorRow(context, editSettingRow, str);
        editSettingRow.setTextAndValue(str, DevGramLocale.text("Изменить ›", "Change ›"), false);
        DevGramReferenceHeaderRow.decorateAction(editSettingRow);
        editColorRow.setColor(i);
        if (runnable != null) {
            editSettingRow.setOnClickListener(new View.OnClickListener() { // from class: cpb.DevGramProfileColorRow.1
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    runnable.run();
                }
            });
        }
        return editColorRow;
    }

    public void setColor(int i) {
        this.band.setColor(i);
        setContentDescription(this.title + DevGramLocale.text(", цвет ", ", color ") + DevGramColorCode.hex(i));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = DevGramReferenceActionButtons.clip(canvas, cell);
        try {
            super.dispatchDraw(canvas);
        } finally {
            if (save >= 0) canvas.restoreToCount(save);
        }
    }

    private static final class Band extends View {
        private static final float EDGE = 0.3f;
        private static final float START = 0.3f;
        private int color;
        private final Paint fade;
        private final Paint paint;
        private int shaderColor;
        private int shaderHeight;
        private int shaderWidth;

        Band(Context context) {
            super(context);
            this.paint = new Paint(1);
            Paint paint = new Paint(1);
            this.fade = paint;
            this.shaderColor = 1;
            this.shaderWidth = -1;
            this.shaderHeight = -1;
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            setImportantForAccessibility(2);
        }

        public void setColor(int i) {
            if (this.color != i) {
                this.color = i;
                this.shaderWidth = -1;
                invalidate();
            }
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            if (this.shaderWidth != width || this.shaderHeight != height || this.shaderColor != this.color) {
                Paint paint = this.paint;
                float f = width;
                int i = this.color;
                paint.setShader(new LinearGradient(0.3f * f, 0.0f, f, 0.0f, new int[]{i & 16777215, i}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
                this.fade.setShader(new LinearGradient(0.0f, 0.0f, 0.0f, height, new int[]{16777215, -1, -1, 16777215}, new float[]{0.0f, 0.3f, 0.7f, 1.0f}, Shader.TileMode.CLAMP));
                this.shaderWidth = width;
                this.shaderHeight = height;
                this.shaderColor = this.color;
            }
            float f2 = width;
            float f3 = height;
            int iSaveLayer = canvas.saveLayer(0.0f, 0.0f, f2, f3, null);
            canvas.drawRect(0.0f, 0.0f, f2, f3, this.paint);
            canvas.drawRect(0.0f, 0.0f, f2, f3, this.fade);
            canvas.restoreToCount(iSaveLayer);
        }
    }
}

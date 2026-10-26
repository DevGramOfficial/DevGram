package org.telegram.messenger.devgram.inline.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import java.util.Iterator;
import kotlin.jvm.internal.ArrayIteratorKt;
import kotlin.ranges.RangesKt;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationBadge;
import org.telegram.ui.Components.CubicBezierInterpolator;
public final class MathRevealAnimation {
    private MaskSpan maskSpan;
    private final Runnable onFinished;
    private long startedAt;
    private Layout targetLayout;
    private final TextView view;
    private final TextPaint paint = new TextPaint(1);
    private int rangeStart = -1;
    private int rangeEnd = -1;
    private float duration = 200.0f;
    private float[] fromX = new float[0];
    private float[] fromY = new float[0];
    private float[] toX = new float[0];
    private float[] toY = new float[0];

    public MathRevealAnimation(TextView textView, Runnable runnable) {
        this.view = textView;
        this.onFinished = runnable;
    }
    public static final class MaskSpan extends CharacterStyle implements UpdateAppearance {
        private final MathRevealAnimation owner;

        public MaskSpan(MathRevealAnimation mathRevealAnimation) {
            this.owner = mathRevealAnimation;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            if (this.owner.maskSpan == this) {
                tp.setAlpha(0);
            }
        }
    }

    public final boolean isRunning() {
        return this.rangeStart >= 0;
    }

    public final boolean isCaretOutside(int caret) {
        return caret < this.rangeStart || caret > this.rangeEnd;
    }

    public final void begin(Editable text, int from, int count, float[] originX, float[] originY) {
        this.rangeStart = from;
        this.rangeEnd = from + count;
        this.fromX = originX;
        this.fromY = originY;
        this.toX = new float[count];
        this.toY = new float[count];
        this.targetLayout = null;
        this.startedAt = SystemClock.elapsedRealtime();
        this.duration = Math.min(420.0f, (count * 28.0f) + 200.0f);
        MaskSpan maskSpan = new MaskSpan(this);
        this.maskSpan = maskSpan;
        text.setSpan(maskSpan, this.rangeStart, this.rangeEnd, 33);
    }

    public final void cancel() {
        stop(false);
    }

    public final void draw(Canvas canvas, int accent) {
        Layout layout = this.view.getLayout();
        CharSequence text = this.view.getText();
        if (layout == null || text == null || this.rangeEnd > text.length()) {
            stop(true);
            return;
        }
        int i = this.rangeEnd - this.rangeStart;
        if (this.targetLayout != layout) {
            this.targetLayout = layout;
            for (int i2 = 0; i2 < i; i2++) {
                int i3 = this.rangeStart + i2;
                this.toX[i2] = layout.getPrimaryHorizontal(i3);
                this.toY[i2] = layout.getLineBaseline(layout.getLineForOffset(i3));
            }
        }
        float fElapsedRealtime = SystemClock.elapsedRealtime() - this.startedAt;
        float f = 1.0f;
        float fCoerceIn = RangesKt.coerceIn(fElapsedRealtime / this.duration, 0.0f, 1.0f);
        float fCoerceIn2 = RangesKt.coerceIn(fElapsedRealtime / 800.0f, 0.0f, 1.0f);
        int iBlendARGB = ColorUtils.blendARGB(accent, this.view.getCurrentTextColor(), CubicBezierInterpolator.EASE_BOTH.getInterpolation(fCoerceIn2));
        int iAlpha = Color.alpha(iBlendARGB);
        float textSize = this.view.getPaint().getTextSize() * 0.32f;
        this.paint.set(this.view.getPaint());
        this.paint.setColor(iBlendARGB);
        int i4 = 0;
        while (i4 < i) {
            int i5 = this.rangeStart + i4;
            float fCascade = AndroidUtilities.cascade(fCoerceIn, i4, i, 3.5f);
            float interpolation = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(fCascade);
            float interpolation2 = CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(fCascade);
            float fLerp = AndroidUtilities.lerp(this.fromX[i4], this.toX[i4], interpolation);
            float fLerp2 = AndroidUtilities.lerp(this.fromY[i4], this.toY[i4], interpolation);
            float fLerp3 = AndroidUtilities.lerp(1.12f, f, interpolation2);
            this.paint.setAlpha((int) (iAlpha * AndroidUtilities.lerp(0.4f, f, interpolation)));
            int i6 = i5 + 1;
            float f2 = f;
            int iSave = canvas.save();
            canvas.scale(fLerp3, fLerp3, fLerp + (this.paint.measureText(text, i5, i6) / 2.0f), fLerp2 - textSize);
            try {
                CharSequence charSequence = text;
                canvas.drawText(charSequence, i5, i6, fLerp, fLerp2, this.paint);
                canvas.restoreToCount(iSave);
                i4++;
                text = charSequence;
                f = f2;
            } catch (Throwable th) {
                canvas.restoreToCount(iSave);
                throw th;
            }
        }
        float f3 = f;
        if (fCoerceIn < f3 || fCoerceIn2 < f3) {
            this.view.invalidate();
        } else {
            stop(true);
        }
    }

    private final void stop(boolean deferred) {
        MaskSpan maskSpan = this.maskSpan;
        if (this.rangeStart >= 0 || maskSpan != null) {
            this.rangeStart = -1;
            this.rangeEnd = -1;
            this.maskSpan = null;
            this.targetLayout = null;
            if (maskSpan != null) {
                if (deferred) {
                    AndroidUtilities.runOnUIThread(() -> removeMasks());
                } else {
                    removeMasks();
                }
            }
            this.onFinished.run();
            this.view.invalidate();
        }
    }
    public final void removeMasks() {
        CharSequence text = this.view.getText();
        if (text instanceof Spannable) {
            Spannable spannable = (Spannable) text;
            Iterator it = ArrayIteratorKt.iterator(spannable.getSpans(0, spannable.length(), MaskSpan.class));
            while (it.hasNext()) {
                spannable.removeSpan((MaskSpan) it.next());
            }
        }
    }
}

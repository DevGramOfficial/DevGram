package org.telegram.messenger.devgram.inline.ui;

import android.graphics.Canvas;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.widget.TextView;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationBadge;
public final class GhostTextLayout {
    private float cursorShiftX;
    private float cursorShiftY;
    private boolean detached;
    private float drawTop;
    private int extraHeight;
    private final GhostAlphaSpan ghostAlpha = new GhostAlphaSpan();
    private int insertOffset;
    private StaticLayout layout;
    private boolean movedText;
    private int paragraphEnd;
    private int paragraphStart;
    public static final class GhostAlphaSpan extends CharacterStyle implements UpdateAppearance {
        private float alpha = 1.0f;

        public final void setAlpha(float f) {
            this.alpha = f;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setAlpha((int) (tp.getAlpha() * this.alpha));
        }
    }

    public final int getParagraphStart() {
        return this.paragraphStart;
    }

    public final int getParagraphEnd() {
        return this.paragraphEnd;
    }

    public final boolean getDetached() {
        return this.detached;
    }

    public final int getExtraHeight() {
        return this.extraHeight;
    }

    public final float getCursorShiftX() {
        return this.cursorShiftX;
    }

    public final float getCursorShiftY() {
        return this.cursorShiftY;
    }

    public final void clear() {
        this.layout = null;
        this.insertOffset = 0;
        this.drawTop = 0.0f;
        this.movedText = false;
        this.detached = false;
        this.paragraphStart = 0;
        this.paragraphEnd = 0;
        this.extraHeight = 0;
        this.cursorShiftX = 0.0f;
        this.cursorShiftY = 0.0f;
    }

    public final boolean isEmpty() {
        return this.layout == null;
    }
    public final boolean getMovedText() {
        return this.movedText;
    }

    public final boolean build(TextView view, Layout real, int start, int end, int caret, CharSequence insert) {
        int width;
        clear();
        CharSequence text = view.getText();
        boolean z = false;
        if (text == null || start < 0 || end > text.length() || caret < start || caret > end || (width = real.getWidth()) <= 0) {
            return false;
        }
        int i = caret - start;
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text, start, end);
        spannableStringBuilder.insert(i, insert);
        spannableStringBuilder.setSpan(this.ghostAlpha, i, insert.length() + i, 33);
        StaticLayout staticLayoutNewLayout = newLayout(view, real, spannableStringBuilder, width);
        if (staticLayoutNewLayout == null) {
            return false;
        }
        int lineForOffset = real.getLineForOffset(start);
        int lineForOffset2 = real.getLineForOffset(end);
        this.layout = staticLayoutNewLayout;
        this.insertOffset = i;
        this.paragraphStart = start;
        this.paragraphEnd = end;
        this.drawTop = real.getLineTop(lineForOffset);
        this.extraHeight = Math.max(0, staticLayoutNewLayout.getHeight() - (real.getLineTop(lineForOffset2 + 1) - real.getLineTop(lineForOffset)));
        if (i > 0 && moved(real, staticLayoutNewLayout, (start + i) - 1, i - 1)) {
            z = true;
        }
        this.movedText = z;
        this.cursorShiftX = staticLayoutNewLayout.getPrimaryHorizontal(i) - real.getPrimaryHorizontal(caret);
        this.cursorShiftY = (staticLayoutNewLayout.getLineTop(staticLayoutNewLayout.getLineForOffset(i)) + this.drawTop) - real.getLineTop(real.getLineForOffset(caret));
        return true;
    }

    public final boolean buildDetached(TextView view, Layout real, int end, CharSequence insert) {
        clear();
        int width = real.getWidth();
        if (width <= 0) {
            return false;
        }
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(insert);
        spannableStringBuilder.setSpan(this.ghostAlpha, 0, spannableStringBuilder.length(), 33);
        StaticLayout staticLayoutNewLayout = newLayout(view, real, spannableStringBuilder, width);
        if (staticLayoutNewLayout == null) {
            return false;
        }
        this.layout = staticLayoutNewLayout;
        this.detached = true;
        this.drawTop = real.getLineBottom(real.getLineForOffset(end));
        this.extraHeight = staticLayoutNewLayout.getHeight();
        return true;
    }

    public final void setAlpha(float value) {
        this.ghostAlpha.setAlpha(value);
    }

    public final void draw(Canvas canvas) {
        StaticLayout staticLayout = this.layout;
        if (staticLayout == null) {
            return;
        }
        float f = this.drawTop;
        int iSave = canvas.save();
        canvas.translate(0.0f, f);
        try {
            staticLayout.draw(canvas);
        } finally {
            canvas.restoreToCount(iSave);
        }
    }

    public final void readInsertedPositions(int from, int count, float[] x, float[] y) {
        StaticLayout staticLayout = this.layout;
        if (staticLayout == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            int i2 = this.insertOffset + from + i;
            x[i] = staticLayout.getPrimaryHorizontal(i2);
            y[i] = this.drawTop + staticLayout.getLineBaseline(staticLayout.getLineForOffset(i2));
        }
    }

    private final boolean moved(Layout real, StaticLayout shadow, int realOffset, int shadowOffset) {
        return Math.abs(shadow.getPrimaryHorizontal(shadowOffset) - real.getPrimaryHorizontal(realOffset)) >= 0.5f || Math.abs(((float) shadow.getLineTop(shadow.getLineForOffset(shadowOffset))) - (((float) real.getLineTop(real.getLineForOffset(realOffset))) - this.drawTop)) >= 0.5f;
    }

    private final StaticLayout newLayout(TextView view, Layout real, CharSequence text, int width) {
        try {
            StaticLayout.Builder textDirection = StaticLayout.Builder.obtain(text, 0, text.length(), view.getPaint(), width).setAlignment(real.getAlignment()).setLineSpacing(view.getLineSpacingExtra(), view.getLineSpacingMultiplier()).setIncludePad(view.getIncludeFontPadding()).setBreakStrategy(view.getBreakStrategy()).setHyphenationFrequency(view.getHyphenationFrequency()).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR);
            int i = Build.VERSION.SDK_INT;
            if (i >= 26) {
                textDirection.setJustificationMode(view.getJustificationMode());
            }
            if (i >= 28) {
                textDirection.setUseLineSpacingFromFallbacks(view.isFallbackLineSpacing());
            }
            return textDirection.build();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}

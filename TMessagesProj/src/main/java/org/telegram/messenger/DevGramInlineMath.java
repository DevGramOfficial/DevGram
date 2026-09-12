package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.MetricAffectingSpan;
import android.text.style.UpdateAppearance;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Инлайн-калькулятор поля ввода — полный порт exteraGram (com.exteragram.messenger.math.inline):
 * набираешь «2+2=» — справа серым «призраком» появляется «4»; принимается пробелом/Enter/Tab/→
 * с анимацией «проявления» символов, отменяется Backspace сразу после. Вычисления — {@link DevGramCalc}.
 */
public final class DevGramInlineMath {

    public interface Delegate {
        int accentColor();
        void runProgrammatic(Runnable action);
    }

    private final TextView view;
    private final Delegate delegate;

    private final Ghost ghost = new Ghost();
    private final AnimatedFloat appear = new AnimatedFloat(220, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final Reveal reveal;

    private DevGramCalc.Suggestion suggestion;
    private boolean dirty;
    private int layoutWidth;
    private char decimalSep = '.';
    private Locale optionsLocale;

    private int undoStart = -1, undoEnd = -1;
    private int suppressedAt = -1;
    private boolean insertingSelf;
    private boolean caretMovedByTouch;
    private int swallowKeyCode;

    public DevGramInlineMath(TextView view) {
        this(view, null);
    }

    public DevGramInlineMath(TextView view, Delegate delegate) {
        this.view = view;
        this.delegate = delegate;
        this.reveal = new Reveal(view, view::invalidate);
    }

    private static boolean enabled() {
        return DevGramConfig.inlineCalc;
    }

    // ------------------------------------------------------------------ геометрия для поля
    public int getExtraBottom() { return ghost.extraHeight; }
    public boolean hasCursorShift() { return ghost.movedText; }
    public float getCursorShiftX() { return hasCursorShift() ? ghost.cursorShiftX : 0f; }
    public float getCursorShiftY() { return hasCursorShift() ? ghost.cursorShiftY : 0f; }

    /** Вырезать реальный абзац, который призрак перерисовывает (чтобы текст не двоился). */
    public void clipReplacedParagraph(Canvas canvas, int top) {
        Layout layout;
        if (ghost.isEmpty() || ghost.detached || (layout = view.getLayout()) == null) {
            return;
        }
        float f = top;
        canvas.clipRect(0f,
                f + layout.getLineTop(layout.getLineForOffset(ghost.paragraphStart)),
                view.getWidth(),
                f + layout.getLineBottom(layout.getLineForOffset(ghost.paragraphEnd)),
                android.graphics.Region.Op.DIFFERENCE);
    }

    // ------------------------------------------------------------------ отрисовка «призрака»/анимации
    public void draw(Canvas canvas, int left, int top, float clipTop, float clipBottom) {
        boolean running = reveal.isRunning();
        if (!running && ghost.isEmpty()) {
            appear.set(0f, true);
            return;
        }
        canvas.save();
        canvas.clipRect(0f, clipTop, view.getWidth(), clipBottom);
        canvas.translate(left, top);
        if (running) {
            reveal.draw(canvas, delegate != null ? delegate.accentColor() : view.getCurrentTextColor());
        } else {
            ghost.setAlpha(appear.set(1f) * 0.4f);
            ghost.draw(canvas);
        }
        canvas.restore();
    }

    // ------------------------------------------------------------------ события поля
    public void onTextChanged() {
        if (!insertingSelf) {
            reveal.cancel();
            clearUndo();
            suppressedAt = -1;
            caretMovedByTouch = false;
        }
        schedule();
    }

    public void invalidateState() {
        if (!insertingSelf) {
            int sel = view.getSelectionStart();
            if (sel != suppressedAt) suppressedAt = -1;
            if (sel != undoEnd || view.getSelectionEnd() != undoEnd) clearUndo();
            if (reveal.isRunning() && reveal.isCaretOutside(sel)) reveal.cancel();
        }
        schedule();
    }

    public void onTouchDown() {
        if (caretMovedByTouch) return;
        caretMovedByTouch = true;
        schedule();
    }

    public void onFocusChanged(boolean focused) {
        if (!focused) {
            reveal.cancel();
            clearUndo();
        }
        schedule();
    }

    public void cancel() {
        reveal.cancel();
        clearUndo();
        suppressedAt = -1;
        caretMovedByTouch = false;
        suggestion = null;
        ghost.clear();
        schedule();
    }

    public boolean updateOnMeasure() {
        int extra = ghost.extraHeight;
        Layout layout = view.getLayout();
        int width = layout != null ? layout.getWidth() : 0;
        if (width != layoutWidth) {
            layoutWidth = width;
            dirty = true;
        }
        update();
        return ghost.extraHeight != extra;
    }

    private void schedule() {
        dirty = true;
        view.invalidate();
        if (ghost.extraHeight == 0 && suggestion == null && !canTrigger()) {
            return;
        }
        view.requestLayout();
    }

    private boolean canTrigger() {
        CharSequence text;
        int sel;
        return enabled() && (text = view.getText()) != null
                && 1 <= (sel = view.getSelectionStart()) && sel <= text.length()
                && text.charAt(sel - 1) == '=';
    }

    private char decimalSeparator() {
        Locale locale = null;
        try {
            locale = LocaleController.getInstance().getCurrentLocale();
        } catch (Exception ignore) {}
        if (locale == null) locale = Locale.US;
        if (!locale.equals(optionsLocale)) {
            optionsLocale = locale;
            try {
                decimalSep = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
            } catch (Exception e) {
                decimalSep = '.';
            }
        }
        return decimalSep;
    }

    private void update() {
        if (!dirty) return;
        dirty = false;
        suggestion = null;
        ghost.clear();
        Layout layout;
        CharSequence text;
        int caret;
        if (!enabled() || reveal.isRunning() || suppressedAt >= 0 || !view.isFocused() || !view.isEnabled()
                || (layout = view.getLayout()) == null || (text = view.getText()) == null
                || (caret = view.getSelectionStart()) <= 0 || caret != view.getSelectionEnd()
                || caret > text.length()) {
            return;
        }
        DevGramCalc.Suggestion s = DevGramCalc.suggestionAt(text, caret, decimalSeparator());
        if (s == null) return;
        // не мешаем составному вводу IME и оформленным спанам вокруг '='
        if (text instanceof Spannable && BaseInputConnection.getComposingSpanStart((Spannable) text) != -1) {
            return;
        }
        if (text instanceof Spanned
                && ((Spanned) text).getSpans(caret - 1, caret, MetricAffectingSpan.class).length != 0) {
            return;
        }
        if (layout.getParagraphDirection(layout.getLineForOffset(caret)) != Layout.DIR_LEFT_TO_RIGHT) {
            return;
        }
        int lineStart = lastIndexOf(text, '\n', caret - 1) + 1;
        int lineEnd = indexOf(text, '\n', caret);
        if (lineEnd < 0) lineEnd = text.length();
        boolean lastLine = lineEnd >= text.length();
        if (ghost.build(view, layout, lineStart, lineEnd, caret, s.insertText)) {
            // Результат сдвигает существующий текст (movedText) или переносит строку (extraHeight):
            // на последней строке показываем «detached»-призрак снизу (без двоения текста),
            // иначе не показываем. Обычный результат в конце строки — inline-призрак.
            if (ghost.extraHeight > 0 || ghost.movedText) {
                if (lastLine && ghost.buildDetached(view, layout, lineEnd, s.insertText)) {
                    suggestion = s;
                } else {
                    ghost.clear();
                }
            } else {
                suggestion = s;
            }
        }
    }

    // ------------------------------------------------------------------ клавиатура
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (swallowKeyCode != 0 && event.getKeyCode() == swallowKeyCode) {
                swallowKeyCode = 0;
                return true;
            }
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0
                || event.isCtrlPressed() || event.isAltPressed() || event.isShiftPressed()) {
            return false;
        }
        boolean handled = false;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DEL:
                handled = undo();
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_TAB:
            case KeyEvent.KEYCODE_SPACE:
                handled = commit();
                break;
        }
        if (handled) swallowKeyCode = event.getKeyCode();
        return handled;
    }

    public InputConnection wrap(InputConnection connection) {
        if (connection == null) return null;
        return new InputConnectionWrapper(connection, true) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (text != null && text.length() == 1 && text.charAt(0) == ' ' && suggestion != null) {
                    finishComposingText();
                    if (commit()) return true;
                }
                return super.commitText(text, newCursorPosition);
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (beforeLength == 1 && afterLength == 0 && undo()) return true;
                return super.deleteSurroundingText(beforeLength, afterLength);
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                if (beforeLength == 1 && afterLength == 0 && undo()) return true;
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
            }
        };
    }

    // ------------------------------------------------------------------ вставка / отмена
    private boolean edit(Runnable block) {
        final boolean[] ok = {true};
        Runnable r = () -> {
            insertingSelf = true;
            try {
                block.run();
            } catch (Exception e) {
                FileLog.e(e);
                ok[0] = false;
            } finally {
                insertingSelf = false;
            }
        };
        if (delegate != null) {
            delegate.runProgrammatic(r);
        } else {
            r.run();
        }
        return ok[0];
    }

    private boolean commit() {
        final DevGramCalc.Suggestion s = suggestion;
        if (s == null) return false;
        CharSequence text = view.getText();
        if (!(text instanceof Editable)) return false;
        final Editable e = (Editable) text;
        if (s.insertAt != view.getSelectionStart() || view.getSelectionStart() != view.getSelectionEnd()
                || s.insertAt > e.length()) {
            return false;
        }
        int shift = s.insertText.length() - s.value.length();
        int count = s.value.length();
        float[] fromX = new float[count];
        float[] fromY = new float[count];
        ghost.readInsertedPositions(shift, count, fromX, fromY);
        BaseInputConnection.removeComposingSpans(e);
        if (!edit(() -> e.insert(s.insertAt, s.insertText))) {
            return false;
        }
        Selection.setSelection(e, s.insertAt + s.insertText.length());
        suggestion = null;
        ghost.clear();
        dirty = true;
        reveal.begin(e, s.insertAt + shift, count, fromX, fromY);
        undoStart = s.insertAt;
        undoEnd = s.insertAt + s.insertText.length();
        appear.set(0f, true);
        view.requestLayout();
        view.invalidate();
        return true;
    }

    private boolean undo() {
        if (undoStart < 0) return false;
        CharSequence text = view.getText();
        if (!(text instanceof Editable)) return false;
        final Editable e = (Editable) text;
        if (undoEnd > e.length() || view.getSelectionStart() != undoEnd || view.getSelectionEnd() != undoEnd) {
            clearUndo();
            return false;
        }
        reveal.cancel();
        final int at = undoStart;
        final int end = undoEnd;
        if (!edit(() -> e.delete(at, end))) {
            return false;
        }
        Selection.setSelection(e, at);
        clearUndo();
        suppressedAt = at;
        dirty = true;
        view.requestLayout();
        view.invalidate();
        return true;
    }

    private void clearUndo() { undoStart = -1; undoEnd = -1; }

    // ------------------------------------------------------------------ утилиты
    private static int lastIndexOf(CharSequence s, char c, int fromInclusive) {
        for (int i = Math.min(fromInclusive, s.length() - 1); i >= 0; i--) {
            if (s.charAt(i) == c) return i;
        }
        return -1;
    }
    private static int indexOf(CharSequence s, char c, int from) {
        for (int i = Math.max(0, from); i < s.length(); i++) {
            if (s.charAt(i) == c) return i;
        }
        return -1;
    }

    // ================================================================== «призрак» (порт GhostTextLayout)
    private static final class GhostAlphaSpan extends CharacterStyle implements UpdateAppearance {
        float alpha = 1f;
        @Override public void updateDrawState(TextPaint tp) { tp.setAlpha((int) (tp.getAlpha() * alpha)); }
    }

    private static final class Ghost {
        private final GhostAlphaSpan ghostAlpha = new GhostAlphaSpan();
        StaticLayout layout;
        int insertOffset;
        float drawTop;
        boolean movedText;
        boolean detached;
        int paragraphStart, paragraphEnd;
        int extraHeight;
        float cursorShiftX, cursorShiftY;

        boolean isEmpty() { return layout == null; }

        void clear() {
            layout = null; insertOffset = 0; drawTop = 0; movedText = false; detached = false;
            paragraphStart = 0; paragraphEnd = 0; extraHeight = 0; cursorShiftX = 0; cursorShiftY = 0;
        }

        void setAlpha(float a) { ghostAlpha.alpha = a; }

        boolean build(TextView view, Layout real, int start, int end, int caret, CharSequence insert) {
            clear();
            CharSequence text = view.getText();
            int width;
            if (text == null || start < 0 || end > text.length() || caret < start || caret > end
                    || (width = real.getWidth()) <= 0) {
                return false;
            }
            int i = caret - start;
            SpannableStringBuilder sb = new SpannableStringBuilder(text, start, end);
            sb.insert(i, insert);
            sb.setSpan(ghostAlpha, i, i + insert.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            StaticLayout nl = newLayout(view, real, sb, width);
            if (nl == null) return false;
            int lineStart = real.getLineForOffset(start);
            int lineEnd = real.getLineForOffset(end);
            layout = nl;
            insertOffset = i;
            paragraphStart = start;
            paragraphEnd = end;
            drawTop = real.getLineTop(lineStart);
            extraHeight = Math.max(0, nl.getHeight() - (real.getLineTop(lineEnd + 1) - real.getLineTop(lineStart)));
            movedText = i > 0 && moved(real, nl, (start + i) - 1, i - 1);
            cursorShiftX = nl.getPrimaryHorizontal(i) - real.getPrimaryHorizontal(caret);
            cursorShiftY = (nl.getLineTop(nl.getLineForOffset(i)) + drawTop) - real.getLineTop(real.getLineForOffset(caret));
            return true;
        }

        boolean buildDetached(TextView view, Layout real, int end, CharSequence insert) {
            clear();
            int width = real.getWidth();
            if (width <= 0) return false;
            SpannableStringBuilder sb = new SpannableStringBuilder(insert);
            sb.setSpan(ghostAlpha, 0, sb.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            StaticLayout nl = newLayout(view, real, sb, width);
            if (nl == null) return false;
            layout = nl;
            detached = true;
            drawTop = real.getLineBottom(real.getLineForOffset(end));
            extraHeight = nl.getHeight();
            return true;
        }

        void draw(Canvas canvas) {
            StaticLayout l = layout;
            if (l == null) return;
            canvas.save();
            canvas.translate(0, drawTop);
            l.draw(canvas);
            canvas.restore();
        }

        void readInsertedPositions(int from, int count, float[] x, float[] y) {
            StaticLayout l = layout;
            if (l == null) return;
            for (int i = 0; i < count; i++) {
                int off = insertOffset + from + i;
                x[i] = l.getPrimaryHorizontal(off);
                y[i] = drawTop + l.getLineBaseline(l.getLineForOffset(off));
            }
        }

        private boolean moved(Layout real, StaticLayout shadow, int realOffset, int shadowOffset) {
            return Math.abs(shadow.getPrimaryHorizontal(shadowOffset) - real.getPrimaryHorizontal(realOffset)) >= 0.5f
                    || Math.abs(shadow.getLineTop(shadow.getLineForOffset(shadowOffset))
                        - (real.getLineTop(real.getLineForOffset(realOffset)) - drawTop)) >= 0.5f;
        }

        private StaticLayout newLayout(TextView view, Layout real, CharSequence text, int width) {
            try {
                StaticLayout.Builder b = StaticLayout.Builder
                        .obtain(text, 0, text.length(), view.getPaint(), width)
                        .setAlignment(real.getAlignment())
                        .setLineSpacing(view.getLineSpacingExtra(), view.getLineSpacingMultiplier())
                        .setIncludePad(view.getIncludeFontPadding())
                        .setBreakStrategy(view.getBreakStrategy())
                        .setHyphenationFrequency(view.getHyphenationFrequency())
                        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR);
                if (Build.VERSION.SDK_INT >= 26) b.setJustificationMode(view.getJustificationMode());
                if (Build.VERSION.SDK_INT >= 28) b.setUseLineSpacingFromFallbacks(view.isFallbackLineSpacing());
                return b.build();
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }
    }

    // ================================================================== анимация проявления (порт MathRevealAnimation)
    private static final class Reveal {
        private final TextView view;
        private final Runnable onFinished;
        private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        private MaskSpan maskSpan;
        private long startedAt;
        private Layout targetLayout;
        private int rangeStart = -1, rangeEnd = -1;
        private float duration = 200f;
        private float[] fromX = new float[0], fromY = new float[0], toX = new float[0], toY = new float[0];

        Reveal(TextView view, Runnable onFinished) {
            this.view = view;
            this.onFinished = onFinished;
        }

        final class MaskSpan extends CharacterStyle implements UpdateAppearance {
            @Override public void updateDrawState(TextPaint tp) {
                if (Reveal.this.maskSpan == this) tp.setAlpha(0);
            }
        }

        boolean isRunning() { return rangeStart >= 0; }
        boolean isCaretOutside(int caret) { return caret < rangeStart || caret > rangeEnd; }

        void begin(Editable text, int from, int count, float[] originX, float[] originY) {
            rangeStart = from;
            rangeEnd = from + count;
            fromX = originX;
            fromY = originY;
            toX = new float[count];
            toY = new float[count];
            targetLayout = null;
            startedAt = SystemClock.elapsedRealtime();
            duration = Math.min(420f, count * 28f + 200f);
            maskSpan = new MaskSpan();
            text.setSpan(maskSpan, rangeStart, rangeEnd, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }

        void cancel() { stop(false); }

        void draw(Canvas canvas, int accent) {
            Layout layout = view.getLayout();
            CharSequence text = view.getText();
            if (layout == null || text == null || rangeEnd > text.length()) {
                stop(true);
                return;
            }
            int n = rangeEnd - rangeStart;
            if (targetLayout != layout) {
                targetLayout = layout;
                for (int i = 0; i < n; i++) {
                    int off = rangeStart + i;
                    toX[i] = layout.getPrimaryHorizontal(off);
                    toY[i] = layout.getLineBaseline(layout.getLineForOffset(off));
                }
            }
            float elapsed = SystemClock.elapsedRealtime() - startedAt;
            float t = clamp(elapsed / duration);
            float tColor = clamp(elapsed / 800f);
            int blend = ColorUtils.blendARGB(accent, view.getCurrentTextColor(),
                    CubicBezierInterpolator.EASE_BOTH.getInterpolation(tColor));
            int baseAlpha = Color.alpha(blend);
            float textSize = view.getPaint().getTextSize() * 0.32f;
            paint.set(view.getPaint());
            paint.setColor(blend);
            for (int i = 0; i < n; i++) {
                int off = rangeStart + i;
                float cascade = AndroidUtilities.cascade(t, i, n, 3.5f);
                float pMove = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(cascade);
                float pScale = CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(cascade);
                float x = AndroidUtilities.lerp(fromX[i], toX[i], pMove);
                float y = AndroidUtilities.lerp(fromY[i], toY[i], pMove);
                float scale = AndroidUtilities.lerp(1.12f, 1f, pScale);
                paint.setAlpha((int) (baseAlpha * AndroidUtilities.lerp(0.4f, 1f, pMove)));
                int save = canvas.save();
                canvas.scale(scale, scale, x + paint.measureText(text, off, off + 1) / 2f, y - textSize);
                try {
                    canvas.drawText(text, off, off + 1, x, y, paint);
                } finally {
                    canvas.restoreToCount(save);
                }
            }
            if (t < 1f || tColor < 1f) {
                view.invalidate();
            } else {
                stop(true);
            }
        }

        private void stop(boolean deferred) {
            MaskSpan span = maskSpan;
            if (rangeStart >= 0 || span != null) {
                rangeStart = -1;
                rangeEnd = -1;
                maskSpan = null;
                targetLayout = null;
                if (span != null) {
                    if (deferred) {
                        AndroidUtilities.runOnUIThread(this::removeMasks);
                    } else {
                        removeMasks();
                    }
                }
                if (onFinished != null) onFinished.run();
                view.invalidate();
            }
        }

        private void removeMasks() {
            CharSequence text = view.getText();
            if (text instanceof Spannable) {
                Spannable sp = (Spannable) text;
                MaskSpan[] spans = sp.getSpans(0, sp.length(), MaskSpan.class);
                for (MaskSpan s : spans) sp.removeSpan(s);
            }
        }

        private static float clamp(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    }
}

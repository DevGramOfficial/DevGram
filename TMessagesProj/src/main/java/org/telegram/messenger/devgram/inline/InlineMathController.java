package org.telegram.messenger.devgram.inline;

import org.telegram.messenger.devgram.KtCompat;

import android.graphics.Canvas;
import android.graphics.Region;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.MetricAffectingSpan;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.TextView;
import org.telegram.messenger.devgram.inline.math.MathExpression;
import org.telegram.messenger.devgram.inline.math.MathOptions;
import org.telegram.messenger.devgram.inline.math.MathSuggestion;
import org.telegram.messenger.devgram.inline.ui.GhostTextLayout;
import org.telegram.messenger.devgram.inline.ui.MathRevealAnimation;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Ref;
import kotlin.text.StringsKt;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.Components.TextStyleSpan;
public final class InlineMathController {
    private String announcedValue;
    private final AnimatedFloat appear;
    private boolean caretMovedByTouch;
    private final Delegate delegate;
    private boolean insertingSelf;
    private final MathRevealAnimation reveal;
    private Locale separatorLocale;
    private MathSuggestion suggestion;
    private int swallowKeyCode;
    private final TextView view;
    private final GhostTextLayout ghost = new GhostTextLayout();
    private final InlineRates rates = new InlineRates(() -> schedule());
    private boolean dirty = true;
    private int layoutWidth = -1;
    private final Runnable announce = () -> InlineMathController.$r8$lambda$gMzZBS1AVkYcs5bx9Ufpht8JZzw(this);
    private char decimalSeparator = '.';
    private int undoStart = -1;
    private int undoEnd = -1;
    private int suppressedAt = -1;
    public interface Delegate {
        int accentColor();

        void runProgrammatic(Runnable action);
    }

    public InlineMathController(TextView textView, Delegate delegate) {
        this.view = textView;
        this.delegate = delegate;
        this.appear = new AnimatedFloat(textView, 0L, 120L, CubicBezierInterpolator.EASE_OUT_QUINT);
        this.reveal = new MathRevealAnimation(textView, () -> dirty = true);
    }

    public static void $r8$lambda$gMzZBS1AVkYcs5bx9Ufpht8JZzw(InlineMathController inlineMathController) {
        MathSuggestion mathSuggestion = inlineMathController.suggestion;
        String value = mathSuggestion != null ? mathSuggestion.getValue() : null;
        if (value != null) {
            inlineMathController.announcedValue = value;
            AndroidUtilities.makeAccessibilityAnnouncement(LocaleController.formatString(R.string.InlineMathResultAnnouncement, value));
        }
    }

    public final int getExtraBottom() {
        return this.ghost.getExtraHeight();
    }

    public final boolean hasCursorShift() {
        return this.ghost.getMovedText();
    }

    public final float getCursorShiftX() {
        if (hasCursorShift()) {
            return this.ghost.getCursorShiftX();
        }
        return 0.0f;
    }

    public final float getCursorShiftY() {
        if (hasCursorShift()) {
            return this.ghost.getCursorShiftY();
        }
        return 0.0f;
    }

    public final void clipReplacedParagraph(Canvas canvas, int top) {
        Layout layout;
        if (this.ghost.isEmpty() || this.ghost.getDetached() || (layout = this.view.getLayout()) == null) {
            return;
        }
        float f = top;
        canvas.clipRect(0.0f, f + layout.getLineTop(layout.getLineForOffset(this.ghost.getParagraphStart())), this.view.getWidth(), f + layout.getLineBottom(layout.getLineForOffset(this.ghost.getParagraphEnd())), Region.Op.DIFFERENCE);
    }

    public final void draw(Canvas canvas, int left, int top, float clipTop, float clipBottom) {
        boolean zIsRunning = this.reveal.isRunning();
        if (!zIsRunning && this.ghost.isEmpty()) {
            this.appear.set(0.0f, true);
            return;
        }
        float width = this.view.getWidth();
        int iSave = canvas.save();
        canvas.clipRect(0.0f, clipTop, width, clipBottom);
        try {
            canvas.translate(left, top);
            if (zIsRunning) {
                MathRevealAnimation mathRevealAnimation = this.reveal;
                Delegate delegate = this.delegate;
                mathRevealAnimation.draw(canvas, delegate != null ? delegate.accentColor() : this.view.getCurrentTextColor());
            } else {
                this.ghost.setAlpha(this.appear.set(1.0f) * 0.4f);
                this.ghost.draw(canvas);
            }
        } finally {
            canvas.restoreToCount(iSave);
        }
    }

    public final void invalidateState() {
        if (!this.insertingSelf) {
            int selectionStart = this.view.getSelectionStart();
            if (selectionStart != this.suppressedAt) {
                this.suppressedAt = -1;
            }
            if (selectionStart != this.undoEnd || this.view.getSelectionEnd() != this.undoEnd) {
                clearUndo();
            }
            if (this.reveal.isRunning() && this.reveal.isCaretOutside(selectionStart)) {
                this.reveal.cancel();
            }
        }
        schedule();
    }

    public final void onTextChanged() {
        if (!this.insertingSelf) {
            this.reveal.cancel();
            clearUndo();
            this.suppressedAt = -1;
            this.caretMovedByTouch = false;
        }
        schedule();
    }

    public final void onTouchDown() {
        if (this.caretMovedByTouch) {
            return;
        }
        this.caretMovedByTouch = true;
        schedule();
    }

    public final void onFocusChanged(boolean focused) {
        if (!focused) {
            this.reveal.cancel();
            clearUndo();
        }
        schedule();
    }

    public final void cancel() {
        AndroidUtilities.cancelRunOnUIThread(this.announce);
        this.announcedValue = null;
        this.reveal.cancel();
        clearUndo();
        this.suppressedAt = -1;
        this.caretMovedByTouch = false;
        this.suggestion = null;
        this.ghost.clear();
        schedule();
    }

    public final boolean updateOnMeasure() {
        int extraHeight = this.ghost.getExtraHeight();
        Layout layout = this.view.getLayout();
        int width = layout != null ? layout.getWidth() : 0;
        if (width != this.layoutWidth) {
            this.layoutWidth = width;
            this.dirty = true;
        }
        update();
        if (AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
            updateAnnouncement();
        }
        return this.ghost.getExtraHeight() != extraHeight;
    }
    public final void schedule() {
        this.dirty = true;
        this.view.invalidate();
        if (this.ghost.getExtraHeight() == 0 && this.suggestion == null && !canTrigger()) {
            return;
        }
        this.view.requestLayout();
    }

    private final boolean canTrigger() {
        CharSequence text;
        int selectionStart;
        return InlineResults.INSTANCE.isEnabled() && (text = this.view.getText()) != null && 1 <= (selectionStart = this.view.getSelectionStart()) && selectionStart <= text.length() && text.charAt(selectionStart - 1) == '=';
    }

    private final void updateAnnouncement() {
        MathSuggestion mathSuggestion = this.suggestion;
        String value = mathSuggestion != null ? mathSuggestion.getValue() : null;
        if (value == null) {
            this.announcedValue = null;
            AndroidUtilities.cancelRunOnUIThread(this.announce);
        } else {
            if (Intrinsics.areEqual(value, this.announcedValue)) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(this.announce);
            AndroidUtilities.runOnUIThread(this.announce, 600L);
        }
    }

    private final MathOptions options() {
        Locale currentLocale = LocaleController.getInstance().getCurrentLocale();
        if (currentLocale == null) {
            currentLocale = Locale.US;
        }
        if (!Intrinsics.areEqual(currentLocale, this.separatorLocale)) {
            this.separatorLocale = currentLocale;
            this.decimalSeparator = DecimalFormatSymbols.getInstance(currentLocale).getDecimalSeparator();
        }
        return InlineResults.options$default(InlineResults.INSTANCE, this.rates, this.decimalSeparator, false, 4, null);
    }

    private final void update() {
        Layout layout;
        CharSequence text;
        int selectionStart;
        MathSuggestion mathSuggestionSuggestionAt;
        if (this.dirty) {
            this.dirty = false;
            this.suggestion = null;
            this.ghost.clear();
            if (!InlineResults.INSTANCE.isEnabled() || this.reveal.isRunning() || this.suppressedAt >= 0 || !this.view.isFocused() || !this.view.isEnabled() || (layout = this.view.getLayout()) == null || (text = this.view.getText()) == null || (selectionStart = this.view.getSelectionStart()) <= 0 || selectionStart != this.view.getSelectionEnd() || selectionStart > text.length() || (mathSuggestionSuggestionAt = MathExpression.INSTANCE.suggestionAt(text, selectionStart, options())) == null) {
                return;
            }
            if (!(text instanceof Spannable) || BaseInputConnection.getComposingSpanStart((Spannable) text) == -1) {
                if (text instanceof Spanned) {
                    if (!(((Spanned) text).getSpans(selectionStart + (-1), selectionStart, MetricAffectingSpan.class).length == 0)) {
                        return;
                    }
                }
                if (layout.getParagraphDirection(layout.getLineForOffset(selectionStart)) != 1) {
                    return;
                }
                int iLastIndexOf$default = KtCompat.lastIndexOf(text, '\n', selectionStart - 1, false, 4, (Object) null) + 1;
                int iIndexOf$default = KtCompat.indexOf(text, '\n', selectionStart, false, 4, (Object) null);
                if (iIndexOf$default < 0) {
                    iIndexOf$default = text.length();
                }
                int i = iIndexOf$default;
                boolean z = i >= text.length();
                if (hasUnsupportedSpans(text, iLastIndexOf$default, i)) {
                    if (z && this.ghost.buildDetached(this.view, layout, i, mathSuggestionSuggestionAt.getInsertText())) {
                        this.suggestion = mathSuggestionSuggestionAt;
                        return;
                    }
                    return;
                }
                if (this.ghost.build(this.view, layout, iLastIndexOf$default, i, selectionStart, mathSuggestionSuggestionAt.getInsertText())) {
                    if (((this.ghost.getExtraHeight() > 0 && !z) || (this.ghost.getMovedText() && this.caretMovedByTouch)) && (!z || !this.ghost.buildDetached(this.view, layout, i, mathSuggestionSuggestionAt.getInsertText()))) {
                        this.ghost.clear();
                    } else {
                        this.suggestion = mathSuggestionSuggestionAt;
                    }
                }
            }
        }
    }

    /* JADX WARN: Code duplicated, block: B:33:0x0052  */
    public final boolean onKeyEvent(KeyEvent event) {
        boolean zCommit = false;
        if (event.getAction() == 1) {
            if (this.swallowKeyCode == 0 || event.getKeyCode() != this.swallowKeyCode) {
                return false;
            }
            this.swallowKeyCode = 0;
            return true;
        }
        if (event.getAction() == 0 && event.getRepeatCount() == 0 && !event.isCtrlPressed() && !event.isAltPressed() && !event.isShiftPressed()) {
            int keyCode = event.getKeyCode();
            if (keyCode == 22) {
                zCommit = commit();
            } else if (keyCode == 67) {
                zCommit = undo();
            } else if (keyCode == 61 || keyCode == 62) {
                zCommit = commit();
            }
            if (zCommit) {
                this.swallowKeyCode = event.getKeyCode();
            }
        }
        return zCommit;
    }

    public final InputConnection wrap(InputConnection connection) {
        return new InputConnectionWrapper(connection, true) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (text != null && text.length() == 1 && text.charAt(0) == ' ' && InlineMathController.this.suggestion != null) {
                    finishComposingText();
                    if (InlineMathController.this.commit()) {
                        return true;
                    }
                }
                return super.commitText(text, newCursorPosition);
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (beforeLength == 1 && afterLength == 0 && InlineMathController.this.undo()) {
                    return true;
                }
                return super.deleteSurroundingText(beforeLength, afterLength);
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                if (beforeLength == 1 && afterLength == 0 && InlineMathController.this.undo()) {
                    return true;
                }
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
            }
        };
    }
    public final boolean commit() {
        final MathSuggestion mathSuggestion = this.suggestion;
        if (mathSuggestion == null) {
            return false;
        }
        CharSequence text = this.view.getText();
        final Editable editable = text instanceof Editable ? (Editable) text : null;
        if (editable == null || mathSuggestion.getInsertAt() != this.view.getSelectionStart() || this.view.getSelectionStart() != this.view.getSelectionEnd() || mathSuggestion.getInsertAt() > editable.length()) {
            return false;
        }
        int length = mathSuggestion.getInsertText().length() - mathSuggestion.getValue().length();
        int length2 = mathSuggestion.getValue().length();
        float[] fArr = new float[length2];
        float[] fArr2 = new float[length2];
        this.ghost.readInsertedPositions(length, length2, fArr, fArr2);
        BaseInputConnection.removeComposingSpans(editable);
        if (!edit(() -> InlineMathController.$r8$lambda$Jyx1kjthE6zjeXsuXiJsgXTst2w(editable, mathSuggestion))) {
            return false;
        }
        Selection.setSelection(editable, mathSuggestion.getInsertAt() + mathSuggestion.getInsertText().length());
        this.suggestion = null;
        this.ghost.clear();
        this.dirty = true;
        this.reveal.begin(editable, mathSuggestion.getInsertAt() + length, length2, fArr, fArr2);
        this.undoStart = mathSuggestion.getInsertAt();
        this.undoEnd = mathSuggestion.getInsertAt() + mathSuggestion.getInsertText().length();
        this.appear.set(0.0f, true);
        this.view.requestLayout();
        this.view.invalidate();
        return true;
    }

    public static Unit $r8$lambda$Jyx1kjthE6zjeXsuXiJsgXTst2w(Editable editable, MathSuggestion mathSuggestion) {
        editable.insert(mathSuggestion.getInsertAt(), mathSuggestion.getInsertText());
        return Unit.INSTANCE;
    }
    public final boolean undo() {
        if (this.undoStart < 0) {
            return false;
        }
        CharSequence text = this.view.getText();
        final Editable editable = text instanceof Editable ? (Editable) text : null;
        if (editable == null) {
            return false;
        }
        if (this.undoEnd > editable.length() || this.view.getSelectionStart() != this.undoEnd || this.view.getSelectionEnd() != this.undoEnd) {
            clearUndo();
            return false;
        }
        this.reveal.cancel();
        int i = this.undoStart;
        if (!edit(() -> InlineMathController.m1532$r8$lambda$1gikaSkfJZER69pmPHJy_iCYWQ(editable, this))) {
            return false;
        }
        Selection.setSelection(editable, i);
        clearUndo();
        this.suppressedAt = i;
        this.dirty = true;
        this.view.requestLayout();
        this.view.invalidate();
        return true;
    }
    public static Unit m1532$r8$lambda$1gikaSkfJZER69pmPHJy_iCYWQ(Editable editable, InlineMathController inlineMathController) {
        editable.delete(inlineMathController.undoStart, inlineMathController.undoEnd);
        return Unit.INSTANCE;
    }

    private final boolean edit(final Function0<Unit> block) {
        final Ref.BooleanRef booleanRef = new Ref.BooleanRef();
        booleanRef.element = true;
        Runnable runnable = () -> InlineMathController.$r8$lambda$zc12RH66DogtZoW52XBsfwesexA(this, block, booleanRef);
        Delegate delegate = this.delegate;
        if (delegate != null) {
            delegate.runProgrammatic(runnable);
        } else {
            runnable.run();
        }
        return booleanRef.element;
    }

    public static void $r8$lambda$zc12RH66DogtZoW52XBsfwesexA(InlineMathController inlineMathController, Function0 function0, Ref.BooleanRef booleanRef) {
        inlineMathController.insertingSelf = true;
        try {
            function0.invoke();
        } catch (Exception e) {
            FileLog.e(e);
            booleanRef.element = false;
        } finally {
            inlineMathController.insertingSelf = false;
        }
    }

    private final boolean hasUnsupportedSpans(CharSequence text, int start, int end) {
        if (!(text instanceof Spanned)) {
            return false;
        }
        Spanned spanned = (Spanned) text;
        if (!(spanned.getSpans(start, end, AnimatedEmojiSpan.class).length == 0)) {
            return true;
        }
        if (!(spanned.getSpans(start, end, QuoteSpan.class).length == 0)) {
            return true;
        }
        Object[] spans = spanned.getSpans(start, end, TextStyleSpan.class);
        for (Object obj : spans) {
            if (((TextStyleSpan) obj).isSpoiler()) {
                return true;
            }
        }
        return false;
    }

    private final void clearUndo() {
        this.undoStart = -1;
        this.undoEnd = -1;
    }
}

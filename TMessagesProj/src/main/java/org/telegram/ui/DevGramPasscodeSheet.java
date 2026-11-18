package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

// DevGram: красивый экран ввода пасскода — точки-индикаторы + цифровая клавиатура.
// Используется и для задания/смены пасскода (двухшаговый ввод с подтверждением),
// и для разблокировки скрытых чатов.
public class DevGramPasscodeSheet extends BottomSheet {

    private static final int LEN = 4;

    private final boolean setMode;
    private final Utilities.Callback<String> onCreated;                 // SET
    private final Utilities.CallbackReturn<String, Boolean> checker;    // ENTER
    private final Runnable onUnlocked;                                  // ENTER

    private final StringBuilder current = new StringBuilder();
    private String firstPin = null;

    private final View[] dots = new View[LEN];
    private LinearLayout dotsRow;
    private TextView titleView;
    private TextView errorView;

    private DevGramPasscodeSheet(Context context, boolean setMode,
                                 Utilities.Callback<String> onCreated,
                                 Utilities.CallbackReturn<String, Boolean> checker,
                                 Runnable onUnlocked) {
        super(context, false);
        this.setMode = setMode;
        this.onCreated = onCreated;
        this.checker = checker;
        this.onUnlocked = onUnlocked;
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground));
        buildUi(context);
    }

    public static void showSet(Context ctx, Utilities.Callback<String> onCreated) {
        new DevGramPasscodeSheet(ctx, true, onCreated, null, null).show();
    }

    public static void showEnter(Context ctx, Utilities.CallbackReturn<String, Boolean> checker, Runnable onUnlocked) {
        new DevGramPasscodeSheet(ctx, false, null, checker, onUnlocked).show();
    }

    private int accent() {
        return Theme.getColor(Theme.key_featuredStickers_addButton);
    }

    private void buildUi(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        // иконка-замок в цветном круге
        FrameLayout circle = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Theme.multAlpha(accent(), 0.14f));
        circle.setBackground(bg);
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.outline_header_lock_24);
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(accent(), android.graphics.PorterDuff.Mode.SRC_IN));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        circle.addView(icon, LayoutHelper.createFrame(30, 30, Gravity.CENTER));
        root.addView(circle, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        titleView = new TextView(ctx);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));

        errorView = new TextView(ctx);
        errorView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        errorView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        errorView.setGravity(Gravity.CENTER);
        root.addView(errorView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        // точки-индикаторы
        dotsRow = new LinearLayout(ctx);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < LEN; i++) {
            View dot = new View(ctx);
            dots[i] = dot;
            dotsRow.addView(dot, LayoutHelper.createLinear(14, 14, Gravity.CENTER, 9, 0, 9, 0));
        }
        root.addView(dotsRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 18, 0, 20));

        // клавиатура
        LinearLayout pad = new LinearLayout(ctx);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER_HORIZONTAL);
        int[][] rows = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {-1, 0, -2}};
        for (int[] r : rows) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int d : r) {
                row.addView(makeKey(ctx, d));
            }
            pad.addView(row, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
        root.addView(pad, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 4));

        updateTitle();
        updateDots();
        setCustomView(root);
    }

    private View makeKey(Context ctx, int digit) {
        FrameLayout key = new FrameLayout(ctx);
        key.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(accent(), 0.12f), Theme.RIPPLE_MASK_CIRCLE_AUTO));
        if (digit == -1) {
            // пустая ячейка
            key.setBackground(null);
        } else if (digit == -2) {
            // backspace
            ImageView bs = new ImageView(ctx);
            bs.setImageResource(R.drawable.msg_clear_input);
            bs.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    Theme.getColor(Theme.key_dialogTextBlack), android.graphics.PorterDuff.Mode.SRC_IN));
            bs.setScaleType(ImageView.ScaleType.CENTER);
            key.addView(bs, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
            key.setOnClickListener(v -> onBackspace());
        } else {
            TextView t = new TextView(ctx);
            t.setText(String.valueOf(digit));
            t.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 27);
            t.setTypeface(AndroidUtilities.getTypeface("fonts/rregular.ttf"));
            t.setGravity(Gravity.CENTER);
            key.addView(t, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            key.setOnClickListener(v -> onDigit(digit));
        }
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(76, 76, Gravity.CENTER, 6, 6, 6, 6);
        key.setLayoutParams(lp);
        return key;
    }

    private void onDigit(int d) {
        if (current.length() >= LEN) return;
        errorView.setText("");
        current.append(d);
        updateDots();
        if (current.length() == LEN) {
            AndroidUtilities.runOnUIThread(this::submit, 90);
        }
    }

    private void onBackspace() {
        if (current.length() > 0) {
            current.deleteCharAt(current.length() - 1);
            updateDots();
        }
    }

    private void submit() {
        String pin = current.toString();
        if (setMode) {
            if (firstPin == null) {
                firstPin = pin;
                current.setLength(0);
                updateDots();
                updateTitle();
            } else if (pin.equals(firstPin)) {
                if (onCreated != null) onCreated.run(pin);
                dismiss();
            } else {
                fail(DevGramLocale.getString(R.string.DevGramLockedChatsPasscodeMismatch));
                firstPin = null;
                updateTitle();
            }
        } else {
            boolean ok = checker != null && Boolean.TRUE.equals(checker.run(pin));
            if (ok) {
                if (onUnlocked != null) onUnlocked.run();
                dismiss();
            } else {
                fail(DevGramLocale.getString(R.string.DevGramLockedChatsWrongPasscode));
            }
        }
    }

    private void fail(String message) {
        errorView.setText(message);
        current.setLength(0);
        updateDots();
        AndroidUtilities.shakeView(dotsRow);
        try {
            dotsRow.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        } catch (Throwable ignore) {}
    }

    private void updateTitle() {
        if (setMode) {
            titleView.setText(firstPin == null
                    ? DevGramLocale.getString(R.string.DevGramLockedChatsNewPasscode)
                    : DevGramLocale.getString(R.string.DevGramLockedChatsRepeatPasscode));
        } else {
            titleView.setText(DevGramLocale.getString(R.string.DevGramLockedChatsEnterPasscode));
        }
    }

    private void updateDots() {
        int filledColor = accent();
        int emptyColor = Theme.getColor(Theme.key_dialogTextHint);
        for (int i = 0; i < LEN; i++) {
            boolean filled = i < current.length();
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            if (filled) {
                d.setColor(filledColor);
            } else {
                d.setColor(0x00000000);
                d.setStroke(AndroidUtilities.dp(1.5f), Theme.multAlpha(emptyColor, 0.6f));
            }
            dots[i].setBackground(d);
        }
    }
}

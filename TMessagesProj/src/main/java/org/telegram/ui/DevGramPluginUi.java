package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

final class DevGramPluginUi {
    interface ChoiceCallback { void run(int index); }
    interface TextCallback { void run(String value); }
    interface ReviewCallback { void run(int rating, String text); }

    private DevGramPluginUi() {}

    static void showChoices(BaseFragment fragment, String title, String subtitle,
                            String[] labels, int[] icons, int dangerFrom, ChoiceCallback callback) {
        Context context = fragment.getParentActivity();
        if (context == null) return;
        LinearLayout root = sheetRoot(context, fragment, title, subtitle);
        BottomSheet[] sheet = new BottomSheet[1];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean danger = i >= dangerFrom;
            root.addView(actionRow(context, fragment, labels[i], icons != null && i < icons.length ? icons[i] : 0,
                    danger, () -> { sheet[0].dismiss(); callback.run(index); }),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 0, 0, 0, 8));
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setApplyBottomPadding(false);
        builder.setCustomView(root);
        sheet[0] = builder.create();
        sheet[0].show();
    }

    static void showTextInput(BaseFragment fragment, String title, String subtitle, String hint,
                              String initial, String buttonText, boolean danger, TextCallback callback) {
        Context context = fragment.getParentActivity();
        if (context == null) return;
        LinearLayout root = sheetRoot(context, fragment, title, subtitle);
        EditText input = input(context, fragment, hint);
        input.setText(initial == null ? "" : initial);
        input.setSelection(input.length());
        root.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 14));
        ButtonWithCounterView button = new ButtonWithCounterView(context, fragment.getResourceProvider()).setRound();
        button.setText(buttonText, false);
        button.setColor(danger ? Theme.getColor(Theme.key_text_RedRegular, fragment.getResourceProvider())
                : Theme.getColor(Theme.key_featuredStickers_addButton, fragment.getResourceProvider()));
        root.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        BottomSheet.Builder builder = new BottomSheet.Builder(context, true, fragment.getResourceProvider());
        builder.setApplyBottomPadding(false);
        builder.setCustomView(root);
        BottomSheet sheet = builder.create();
        button.setOnClickListener(v -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (value.isEmpty()) {
                AndroidUtilities.shakeViewSpring(input);
                return;
            }
            sheet.dismiss();
            callback.run(value);
        });
        sheet.setOnShowListener(d -> { input.requestFocus(); AndroidUtilities.showKeyboard(input); });
        sheet.show();
    }

    static void showReview(BaseFragment fragment, int currentRating, String currentText, ReviewCallback callback) {
        Context context = fragment.getParentActivity();
        if (context == null) return;
        LinearLayout root = sheetRoot(context, fragment,
                currentText == null ? "Ваш отзыв" : "Изменить отзыв",
                "Оцените плагин и расскажите другим, чем он оказался полезен.");
        final int[] rating = {currentRating <= 0 ? 5 : currentRating};
        TextView[] stars = new TextView[5];
        LinearLayout starRow = new LinearLayout(context);
        starRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 5; i++) {
            final int score = i + 1;
            TextView star = text(context, "★", 34, true, 0xFFE0A400);
            star.setGravity(Gravity.CENTER);
            star.setOnClickListener(v -> { rating[0] = score; updateStars(stars, score); });
            stars[i] = star;
            starRow.addView(star, LayoutHelper.createLinear(48, 52));
        }
        updateStars(stars, rating[0]);
        root.addView(starRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 0, 0, 0, 10));
        EditText input = input(context, fragment, "Что понравилось или стоит улучшить?");
        input.setText(currentText == null ? "" : currentText);
        root.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));
        ButtonWithCounterView save = new ButtonWithCounterView(context, fragment.getResourceProvider()).setRound();
        save.setText(currentText == null ? "Опубликовать отзыв" : "Сохранить изменения", false);
        save.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, fragment.getResourceProvider()));
        root.addView(save, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        BottomSheet.Builder builder = new BottomSheet.Builder(context, true, fragment.getResourceProvider());
        builder.setApplyBottomPadding(false);
        builder.setCustomView(root);
        BottomSheet sheet = builder.create();
        save.setOnClickListener(v -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (value.isEmpty()) { AndroidUtilities.shakeViewSpring(input); return; }
            sheet.dismiss();
            callback.run(rating[0], value);
        });
        sheet.show();
    }

    static LinearLayout sheetRoot(Context context, BaseFragment fragment, String title, String subtitle) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(22));
        root.addView(text(context, title, 22, true,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, fragment.getResourceProvider())));
        if (subtitle != null && !subtitle.isEmpty()) root.addView(text(context, subtitle, 14, false,
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, fragment.getResourceProvider())),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 16));
        return root;
    }

    static LinearLayout actionRow(Context context, BaseFragment fragment, String title, int icon,
                                  boolean danger, Runnable action) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        int color = danger ? Theme.getColor(Theme.key_text_RedRegular, fragment.getResourceProvider())
                : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, fragment.getResourceProvider());
        if (icon != 0) {
            android.widget.ImageView image = new android.widget.ImageView(context);
            image.setImageResource(icon);
            image.setColorFilter(color);
            row.addView(image, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
        }
        row.addView(text(context, title, 16, true, color), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundGray, fragment.getResourceProvider()),
                Theme.getColor(Theme.key_listSelector, fragment.getResourceProvider())));
        row.setOnClickListener(v -> action.run());
        ScaleStateListAnimator.apply(row, .03f, 1.2f);
        return row;
    }

    static EditText input(Context context, BaseFragment fragment, String hint) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, fragment.getResourceProvider()));
        input.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, fragment.getResourceProvider()));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setMaxLines(7);
        input.setGravity(Gravity.TOP);
        input.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        input.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(15),
                Theme.getColor(Theme.key_dialogBackground, fragment.getResourceProvider())));
        return input;
    }

    static TextView text(Context context, String value, int size, boolean bold, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        text.setTextColor(color);
        text.setLineSpacing(AndroidUtilities.dp(2), 1f);
        if (bold) text.setTypeface(AndroidUtilities.bold());
        return text;
    }

    private static void updateStars(TextView[] stars, int selected) {
        for (int i = 0; i < stars.length; i++) stars[i].setAlpha(i < selected ? 1f : .28f);
    }
}

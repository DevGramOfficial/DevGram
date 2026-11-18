package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramAccountCleanup;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

// DevGram: «Другое» → «Очистить аккаунт». Красивый BottomSheet вместо системного AlertDialog:
// шаг настроек (что чистим) -> шаг серьёзного подтверждения (нужно набрать слово) -> прогресс ->
// готово. Личные чаты удаляются С РЕВОКОМ (у собеседника тоже), группы/каналы, где пользователь —
// создатель, удаляются целиком; остальные — просто выход. См. DevGramAccountCleanup.
public class DevGramAccountCleanupSheet {

    private static String getConfirmWord() {
        return DevGramLocale.text("УДАЛИТЬ");
    }

    public static void show(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        int account = fragment.getCurrentAccount();
        Theme.ResourcesProvider rp = fragment.getResourceProvider();

        DevGramAccountCleanup.Options o = new DevGramAccountCleanup.Options();
        o.contacts = true;
        o.chats = true;
        o.groups = true;
        o.drafts = true;
        o.savedMessages = false;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(12));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setApplyBottomPadding(false);
        builder.setCustomView(scroll);
        BottomSheet sheet = builder.create();
        // BottomSheet по умолчанию НЕ фокусируемый для IME (FLAG_ALT_FOCUSABLE_IM) — поле ввода
        // «УДАЛИТЬ» на шаге подтверждения иначе никогда не показывало клавиатуру.
        sheet.setFocusable(true);

        FrameLayout optionsStep = new FrameLayout(context);
        FrameLayout confirmStep = new FrameLayout(context);
        FrameLayout progressStep = new FrameLayout(context);
        FrameLayout doneStep = new FrameLayout(context);
        FrameLayout[] steps = {optionsStep, confirmStep, progressStep, doneStep};
        for (FrameLayout s : steps) {
            s.setVisibility(View.GONE);
            root.addView(s, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        fillOptionsStep(context, rp, optionsStep, o, () -> {
            confirmStep.removeAllViews();
            fillConfirmStep(context, rp, account, o, confirmStep, steps, sheet);
            optionsStep.setVisibility(View.GONE);
            confirmStep.setVisibility(View.VISIBLE);
        });
        TextView stageText = fillProgressStep(context, rp, progressStep);
        progressStep.setTag(stageText);
        fillDoneStep(context, rp, doneStep, sheet);

        optionsStep.setVisibility(View.VISIBLE);
        sheet.setCanDismissWithSwipe(true);
        sheet.show();
    }

    // ---------- шаг 1: что чистим ----------
    private static void fillOptionsStep(Context context, Theme.ResourcesProvider rp, FrameLayout holder,
                                         DevGramAccountCleanup.Options o, Runnable onContinue) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        holder.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_delete);
        icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_text_RedRegular, rp), PorterDuff.Mode.SRC_IN));
        col.addView(icon, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 10));

        TextView title = new TextView(context);
        title.setText(DevGramLocale.text("Очистить аккаунт"));
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        col.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView warn = new TextView(context);
        warn.setText(DevGramLocale.text("Личные чаты удаляются и у собеседника тоже. Группы и каналы, которыми вы владеете, ")
                + DevGramLocale.text("будут удалены целиком для всех участников. Это необратимо."));
        warn.setTextColor(Theme.getColor(Theme.key_text_RedRegular, rp));
        warn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        warn.setGravity(Gravity.CENTER);
        col.addView(warn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        col.addView(optionRow(context, rp, DevGramLocale.text("Контакты"), true, checked -> o.contacts = checked));
        col.addView(optionRow(context, rp, DevGramLocale.text("Личные чаты (с удалением у собеседника)"), true, checked -> o.chats = checked));
        col.addView(optionRow(context, rp, DevGramLocale.text("Группы и каналы (свои — удалить, остальные — выйти)"), true, checked -> o.groups = checked));
        col.addView(optionRow(context, rp, DevGramLocale.text("Сохранённые сообщения"), false, checked -> o.savedMessages = checked));
        col.addView(optionRow(context, rp, DevGramLocale.text("Черновики"), true, checked -> o.drafts = checked));

        TextView cont = redButton(context, rp, DevGramLocale.text("Продолжить"));
        col.addView(cont, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 4));
        cont.setOnClickListener(v -> {
            if (!o.any()) {
                return;
            }
            onContinue.run();
        });
    }

    private static CheckBoxCell optionRow(Context context, Theme.ResourcesProvider rp, String text, boolean defaultChecked, java.util.function.Consumer<Boolean> onChange) {
        CheckBoxCell cell = new CheckBoxCell(context, 4, 8, rp);
        cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        cell.setText(text, "", defaultChecked, false);
        cell.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        // Круглый чекбокс (type=4) без явного setCheckBoxColor рисуется дефолтными неинициализированными
        // цветами — почти чёрным на тёмной теме, галочки не видно. Явно задаём цвета под красную тему листа.
        cell.setCheckBoxColor(Theme.key_text_RedRegular, Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_checkboxCheck);
        onChange.accept(defaultChecked);
        cell.setOnClickListener(v -> {
            boolean now = !((CheckBoxCell) v).isChecked();
            ((CheckBoxCell) v).setChecked(now, true);
            onChange.accept(now);
        });
        return cell;
    }

    // ---------- шаг 2: серьёзное подтверждение (нужно набрать слово); пересобирается каждый раз ----------
    private static void fillConfirmStep(Context context, Theme.ResourcesProvider rp, int account, DevGramAccountCleanup.Options o,
                                         FrameLayout holder, FrameLayout[] steps, BottomSheet sheet) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        holder.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_delete);
        icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_text_RedRegular, rp), PorterDuff.Mode.SRC_IN));
        col.addView(icon, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 10));

        TextView title = new TextView(context);
        title.setText(DevGramLocale.text("Это необратимо"));
        title.setTextColor(Theme.getColor(Theme.key_text_RedBold, rp));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        col.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        DevGramAccountCleanup.Target t = DevGramAccountCleanup.count(account, o);
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        if (o.contacts) parts.add(t.contactsCount + " " + plural(t.contactsCount, DevGramLocale.text("контакт"), DevGramLocale.text("контакта"), DevGramLocale.text("контактов")));
        if (o.chats) parts.add(t.chatsCount + " " + plural(t.chatsCount, DevGramLocale.text("личный чат"), DevGramLocale.text("личных чата"), DevGramLocale.text("личных чатов")));
        if (o.groups) parts.add(t.groupsCount + " " + plural(t.groupsCount, DevGramLocale.text("группа/канал"), DevGramLocale.text("группы/канала"), DevGramLocale.text("групп/каналов")));
        if (o.savedMessages && t.savedMessagesPresent) parts.add(DevGramLocale.text("сохранённые сообщения"));
        if (o.drafts) parts.add(DevGramLocale.text("черновики"));
        String summary = DevGramLocale.text("Будут удалены: ") + android.text.TextUtils.join(", ", parts) + ".";

        TextView body = new TextView(context);
        body.setText(summary);
        body.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, rp));
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        body.setGravity(Gravity.CENTER);
        col.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        TextView hint = new TextView(context);
        hint.setText(DevGramLocale.text("Введите «") + getConfirmWord() + DevGramLocale.text("», чтобы подтвердить:"));
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        col.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint(getConfirmWord());
        input.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, rp));
        input.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        input.setCursorSize(AndroidUtilities.dp(20));
        input.setCursorWidth(1.5f);
        input.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        input.setSingleLine(true);
        col.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 0, 0, 16));
        input.postDelayed(() -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        }, 150);

        TextView confirmBtn = redButton(context, rp, DevGramLocale.text("Очистить аккаунт"));
        confirmBtn.setAlpha(0.4f);
        confirmBtn.setEnabled(false);
        col.addView(confirmBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        TextView back = new TextView(context);
        back.setText(DevGramLocale.text("Назад"));
        back.setGravity(Gravity.CENTER);
        back.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        back.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, rp));
        back.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4));
        col.addView(back, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        back.setOnClickListener(v -> {
            steps[1].setVisibility(View.GONE);
            steps[0].setVisibility(View.VISIBLE);
        });

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                boolean ok = getConfirmWord().contentEquals(s);
                confirmBtn.setEnabled(ok);
                confirmBtn.setAlpha(ok ? 1f : 0.4f);
            }
        });

        TextView stageText = (TextView) steps[2].getTag();
        confirmBtn.setOnClickListener(v -> {
            sheet.setCanDismissWithSwipe(false);
            steps[1].setVisibility(View.GONE);
            steps[2].setVisibility(View.VISIBLE);
            DevGramAccountCleanup.run(account, o, new DevGramAccountCleanup.Callback() {
                @Override
                public void onStage(String stage, int done, int total) {
                    AndroidUtilities.runOnUIThread(() -> stageText.setText(total > 0 ? stage + " (" + done + "/" + total + ")" : stage));
                }

                @Override
                public void onDone() {
                    AndroidUtilities.runOnUIThread(() -> {
                        steps[2].setVisibility(View.GONE);
                        steps[3].setVisibility(View.VISIBLE);
                        sheet.setCanDismissWithSwipe(true);
                        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
                    });
                }
            });
        });
    }

    // ---------- шаг 3: прогресс ----------
    private static TextView fillProgressStep(Context context, Theme.ResourcesProvider rp, FrameLayout holder) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        holder.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        RadialProgressView spinner = new RadialProgressView(context, rp);
        spinner.setSize(AndroidUtilities.dp(32));
        spinner.setProgressColor(Theme.getColor(Theme.key_text_RedRegular, rp));
        col.addView(spinner, LayoutHelper.createLinear(32, 32, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 16));

        TextView stage = new TextView(context);
        stage.setText(DevGramLocale.text("Очищаем аккаунт…"));
        stage.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        stage.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        stage.setGravity(Gravity.CENTER);
        col.addView(stage, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));
        return stage;
    }

    // ---------- шаг 4: готово ----------
    private static void fillDoneStep(Context context, Theme.ResourcesProvider rp, FrameLayout holder, BottomSheet sheet) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        holder.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.checkbig);
        icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, rp), PorterDuff.Mode.SRC_IN));
        col.addView(icon, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 10));

        TextView title = new TextView(context);
        title.setText(DevGramLocale.text("Готово"));
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        col.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView body = new TextView(context);
        body.setText(DevGramLocale.text("Аккаунт очищен по выбранным пунктам."));
        body.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, rp));
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        body.setGravity(Gravity.CENTER);
        col.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        TextView close = redButton(context, rp, DevGramLocale.text("Закрыть"));
        col.addView(close, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 4));
        close.setOnClickListener(v -> sheet.dismiss());
    }

    private static TextView redButton(Context context, Theme.ResourcesProvider rp, String text) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        btn.setTypeface(AndroidUtilities.bold());
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_text_RedRegular, rp),
                Theme.getColor(Theme.key_text_RedBold, rp)));
        btn.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        return btn;
    }

    private static String plural(int n, String one, String few, String many) {
        if (!DevGramLocale.isRussian()) return n == 1 ? one : many;
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return one;
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
        return many;
    }
}

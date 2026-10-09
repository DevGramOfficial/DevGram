package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

/**
 * DevGram: полное удаление Telegram-аккаунта прямо из клиента (account.deleteAccount).
 * НЕОБРАТИМО. Двойное подтверждение; при включённом 2FA запрашивает облачный пароль (SRP).
 */
public class DevGramDeleteAccount {

    // Красивый BottomSheet-предупреждение (стиль «Очистить аккаунт»): иконка, заголовок,
    // красный текст, красная кнопка «Удалить навсегда» + отмена.
    public static void confirmAndDelete(BaseFragment fragment) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final org.telegram.ui.ActionBar.Theme.ResourcesProvider rp = fragment.getResourceProvider();

        android.widget.LinearLayout col = new android.widget.LinearLayout(context);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(20), AndroidUtilities.dp(12));

        android.widget.ImageView icon = new android.widget.ImageView(context);
        icon.setImageResource(R.drawable.msg_delete);
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(
                Theme.getColor(Theme.key_text_RedRegular, rp), android.graphics.PorterDuff.Mode.SRC_IN));
        col.addView(icon, org.telegram.ui.Components.LayoutHelper.createLinear(52, 52, android.view.Gravity.CENTER_HORIZONTAL, 0, 4, 0, 12));

        android.widget.TextView title = new android.widget.TextView(context);
        title.setText("Удалить аккаунт");
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(android.view.Gravity.CENTER);
        col.addView(title, org.telegram.ui.Components.LayoutHelper.createLinear(-1, -2, 0, 0, 0, 8));

        android.widget.TextView warn = new android.widget.TextView(context);
        warn.setText("Аккаунт будет удалён НАВСЕГДА со всех устройств: чаты, контакты, а также каналы "
                + "и группы, созданные вами. Восстановить его будет невозможно.");
        warn.setTextColor(Theme.getColor(Theme.key_text_RedRegular, rp));
        warn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        warn.setGravity(android.view.Gravity.CENTER);
        col.addView(warn, org.telegram.ui.Components.LayoutHelper.createLinear(-1, -2, 0, 0, 0, 20));

        org.telegram.ui.ActionBar.BottomSheet.Builder builder = new org.telegram.ui.ActionBar.BottomSheet.Builder(context);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
        frame.addView(col, org.telegram.ui.Components.LayoutHelper.createFrame(-1, -2));
        builder.setCustomView(frame);
        final org.telegram.ui.ActionBar.BottomSheet sheet = builder.create();

        android.widget.TextView del = new android.widget.TextView(context);
        del.setText("Удалить навсегда");
        del.setGravity(android.view.Gravity.CENTER);
        del.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        del.setTypeface(AndroidUtilities.bold());
        del.setTextColor(0xFFFFFFFF);
        del.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_text_RedRegular, rp), Theme.getColor(Theme.key_text_RedBold, rp)));
        del.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        del.setOnClickListener(v -> {
            sheet.dismiss();
            confirmByTyping(fragment);
        });
        col.addView(del, org.telegram.ui.Components.LayoutHelper.createLinear(-1, -2, 0, 0, 0, 6));

        android.widget.TextView cancel = new android.widget.TextView(context);
        cancel.setText(LocaleController.getString(R.string.Cancel));
        cancel.setGravity(android.view.Gravity.CENTER);
        cancel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        cancel.setTypeface(AndroidUtilities.bold());
        cancel.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, rp));
        cancel.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12), 0, Theme.getColor(Theme.key_listSelector, rp)));
        cancel.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        cancel.setOnClickListener(v -> sheet.dismiss());
        col.addView(cancel, org.telegram.ui.Components.LayoutHelper.createLinear(-1, -2, 0, 0, 0, 6));

        sheet.show();
    }

    private static final String CONFIRM_WORD = "УДАЛИТЬ";
    private static final int CONFIRM_DELAY_SEC = 5;

    // Второй шаг: вписать слово УДАЛИТЬ; кнопка неактивна первые 5 секунд (защита от случайного нажатия)
    private static void confirmByTyping(BaseFragment fragment) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final Theme.ResourcesProvider rp = fragment.getResourceProvider();
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        edit.setHintText(CONFIRM_WORD);
        edit.setHintColor(Theme.getColor(Theme.key_dialogTextHint, rp));
        edit.setBackgroundColor(0);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        edit.setCursorSize(AndroidUtilities.dp(20));
        edit.setCursorWidth(1.5f);
        FrameLayout wrap = new FrameLayout(context);
        wrap.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        AlertDialog.Builder b = new AlertDialog.Builder(context, rp);
        b.setTitle("Точно удалить аккаунт?");
        b.setMessage("Это нельзя отменить. Чтобы подтвердить, впишите слово " + CONFIRM_WORD + ".");
        b.setView(wrap);
        b.setPositiveButton("Удалить", (d, w) -> checkPasswordAndDelete(fragment));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        fragment.showDialog(dialog);
        final android.widget.TextView button = (android.widget.TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button == null) {
            return;
        }
        button.setTextColor(Theme.getColor(Theme.key_text_RedBold, rp));
        final int[] left = {CONFIRM_DELAY_SEC};
        final Runnable refresh = () -> {
            boolean typed = edit.getText() != null && CONFIRM_WORD.equalsIgnoreCase(edit.getText().toString().trim());
            boolean ready = left[0] <= 0 && typed;
            button.setEnabled(ready);
            button.setAlpha(ready ? 1f : 0.4f);
            button.setText(left[0] > 0 ? "Удалить (" + left[0] + ")" : "Удалить");
        };
        refresh.run();
        edit.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) {}
            public void afterTextChanged(android.text.Editable s) { refresh.run(); }
        });
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (!dialog.isShowing()) return;
            left[0]--;
            refresh.run();
            if (left[0] > 0) AndroidUtilities.runOnUIThread(tick[0], 1000);
        };
        AndroidUtilities.runOnUIThread(tick[0], 1000);
        edit.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(edit), 100);
    }

    // Узнаём, включён ли 2FA. Если да — просим пароль, иначе удаляем сразу.
    private static void checkPasswordAndDelete(BaseFragment fragment) {
        final int account = fragment.getCurrentAccount();
        AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();

        TL_account.getPassword req = new TL_account.getPassword();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            progress.dismiss();
            if (error != null || !(response instanceof TL_account.Password)) {
                showError(fragment, error != null ? error.text : "Не удалось получить состояние пароля");
                return;
            }
            TL_account.Password pw = (TL_account.Password) response;
            if (pw.has_password) {
                askPassword(fragment, pw);
            } else {
                sendDelete(fragment, null);
            }
        }));
    }

    private static void askPassword(BaseFragment fragment, TL_account.Password pw) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintText("Облачный пароль (2FA)");
        edit.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        edit.setBackgroundColor(0);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setCursorSize(AndroidUtilities.dp(20));
        edit.setCursorWidth(1.5f);

        FrameLayout wrap = new FrameLayout(context);
        wrap.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Подтвердите паролем");
        b.setMessage("У аккаунта включена двухэтапная проверка. Введите облачный пароль, чтобы удалить аккаунт.");
        b.setView(wrap);
        b.setPositiveButton("Удалить", (d, w) -> {
            String password = edit.getText() != null ? edit.getText().toString() : "";
            if (password.isEmpty()) {
                showError(fragment, "Введите пароль");
                return;
            }
            computeSrpAndDelete(fragment, pw, password);
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        fragment.showDialog(dialog);
        edit.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(edit), 100);
    }

    private static void computeSrpAndDelete(BaseFragment fragment, TL_account.Password pw, String password) {
        final int account = fragment.getCurrentAccount();
        AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        Utilities.globalQueue.postRunnable(() -> {
            TLRPC.InputCheckPasswordSRP srp = null;
            try {
                if (pw.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo =
                            (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) pw.current_algo;
                    byte[] x = SRPHelper.getX(AndroidUtilities.getStringBytes(password), algo);
                    srp = SRPHelper.startCheck(x, pw.srp_id, pw.srp_B, algo);
                }
            } catch (Throwable ignore) {
            }
            final TLRPC.InputCheckPasswordSRP finalSrp = srp;
            AndroidUtilities.runOnUIThread(() -> {
                progress.dismiss();
                if (finalSrp == null) {
                    showError(fragment, "Не удалось обработать пароль");
                    return;
                }
                sendDelete(fragment, finalSrp);
            });
        });
    }

    private static void sendDelete(BaseFragment fragment, TLRPC.InputCheckPasswordSRP srp) {
        final int account = fragment.getCurrentAccount();
        AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();

        TL_account.deleteAccount req = new TL_account.deleteAccount();
        req.reason = "Deleted via DevGram";
        if (srp != null) {
            req.flags |= 1;
            req.password = srp;
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            progress.dismiss();
            if (error != null) {
                if ("PASSWORD_HASH_INVALID".equals(error.text)) {
                    showError(fragment, "Неверный облачный пароль");
                } else if (error.text != null && error.text.startsWith("FLOOD_WAIT")) {
                    showError(fragment, "Слишком часто. Попробуйте позже.");
                } else {
                    showError(fragment, error.text);
                }
                return;
            }
            // Успех — аккаунт удалён на сервере. Локально выходим и очищаемся.
            MessagesController.getInstance(account).performLogout(1);
        }));
    }

    private static void showError(BaseFragment fragment, String text) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(fragment).createErrorBulletin(
                text != null && !text.isEmpty() ? text : "Ошибка удаления аккаунта").show();
    }
}

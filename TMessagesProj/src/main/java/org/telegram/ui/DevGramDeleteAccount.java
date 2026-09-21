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

    public static void confirmAndDelete(BaseFragment fragment) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        // Шаг 1 — предупреждение.
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Удалить аккаунт");
        b.setMessage("Аккаунт будет удалён НАВСЕГДА со всех устройств: чаты, контакты, каналы и группы, "
                + "созданные вами, будут потеряны. Это действие необратимо.\n\nПродолжить?");
        b.setPositiveButton("Удалить", (d, w) -> confirmTwice(fragment));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        fragment.showDialog(dialog);
        AndroidUtilities.runOnUIThread(() -> {
            android.widget.TextView btn = (android.widget.TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (btn != null) {
                btn.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        });
    }

    private static void confirmTwice(BaseFragment fragment) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        // Шаг 2 — финальное подтверждение (защита от случайного нажатия).
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Точно удалить?");
        b.setMessage("Последнее предупреждение. Восстановить аккаунт после удаления невозможно.");
        b.setPositiveButton("Удалить навсегда", (d, w) -> checkPasswordAndDelete(fragment));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        fragment.showDialog(dialog);
        AndroidUtilities.runOnUIThread(() -> {
            android.widget.TextView btn = (android.widget.TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (btn != null) {
                btn.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        });
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

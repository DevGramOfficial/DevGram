/*
 * DevGram: замок настроек (4-значный код) для панели DevGram.
 *
 * Переиспользуемая обёртка над хранилищем ForkSettingsLock (mainconfig, PBKDF-подобный хеш+соль):
 * тот же код, что раньше защищал скрытую Fork-панель, теперь защищает наше меню DevGram.
 * Диалоги (ввод/смена/снятие кода, разблокировка) вынесены сюда статикой, чтобы их могли
 * вызывать разные экраны через BaseFragment. UI-часть (CodeFieldContainer/CodeNumberField)
 * лежит в этом же пакете org.telegram.ui, поэтому импорты не нужны.
 */

package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.forkgram.ForkSettingsLock;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class DevGramSettingsLock {

    public static boolean hasCode() {
        return ForkSettingsLock.hasCode();
    }

    // Подпись строки-кнопки: «Вкл»/«Выкл».
    public static String label() {
        return LocaleController.getString(ForkSettingsLock.hasCode() ? R.string.PasswordOn : R.string.PasswordOff);
    }

    // Управление кодом из меню: нет кода -> установить; есть -> подтвердить текущий, затем сменить/снять.
    public static void manage(BaseFragment fragment, Runnable onChanged) {
        if (ForkSettingsLock.hasCode()) {
            showCurrentCodeDialog(fragment, () -> showActions(fragment, onChanged));
        } else {
            showSetCodeDialog(fragment, false, onChanged);
        }
    }

    // Гейт входа: если код установлен — спросить его. onUnlocked при успехе, onCancel при отмене/назад.
    public static void promptUnlock(BaseFragment fragment, Runnable onUnlocked, Runnable onCancel) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final boolean[] passed = new boolean[1];
        final AlertDialog[] dialogRef = new AlertDialog[1];
        final Utilities.Callback<CodeFieldContainer> onCompleted = container -> {
            if (!ForkSettingsLock.checkCode(container.getCode())) {
                showCodeError(container);
                return;
            }
            passed[0] = true;
            AndroidUtilities.hideKeyboard(container);
            dialogRef[0].dismiss();
            if (onUnlocked != null) {
                onUnlocked.run();
            }
        };

        CodeFieldContainer container = createCodeContainer(context, onCompleted);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("DevGram");
        builder.setMessage(LocaleController.getString(R.string.ForkSettingsLockUnlockInfo));
        builder.setView(wrapCodeContainer(context, container));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.Continue), null);
        builder.setOnDismissListener(d -> {
            if (!passed[0] && onCancel != null) {
                onCancel.run();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialogRef[0] = dialog;
        if (fragment.showDialog(dialog) == null) {
            if (onCancel != null) {
                onCancel.run();
            }
            return;
        }
        focusCodeContainer(container);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setOnClickListener(v -> onCompleted.run(container));
        }
    }

    private static void showCurrentCodeDialog(BaseFragment fragment, Runnable onVerified) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final AlertDialog[] dialogRef = new AlertDialog[1];
        final Utilities.Callback<CodeFieldContainer> onCompleted = container -> {
            if (!ForkSettingsLock.checkCode(container.getCode())) {
                showCodeError(container);
                return;
            }
            AndroidUtilities.hideKeyboard(container);
            dialogRef[0].dismiss();
            onVerified.run();
        };

        CodeFieldContainer container = createCodeContainer(context, onCompleted);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.ForkSettingsLock));
        builder.setMessage(LocaleController.getString(R.string.ForkSettingsLockConfirmCurrentCodeInfo));
        builder.setView(wrapCodeContainer(context, container));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.Continue), null);

        AlertDialog dialog = builder.create();
        dialogRef[0] = dialog;
        if (fragment.showDialog(dialog) == null) {
            return;
        }
        focusCodeContainer(container);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setOnClickListener(v -> onCompleted.run(container));
        }
    }

    private static void showActions(BaseFragment fragment, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        CharSequence[] actions = new CharSequence[] {
            LocaleController.getString(R.string.ForkSettingsLockChangeCode),
            LocaleController.getString(R.string.ForkSettingsLockRemoveCode)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.ForkSettingsLock));
        builder.setItems(actions, (dialog, which) -> {
            if (which == 0) {
                showSetCodeDialog(fragment, true, onChanged);
            } else {
                showRemoveCodeDialog(fragment, onChanged);
            }
        });
        fragment.showDialog(builder.create());
    }

    private static void showSetCodeDialog(BaseFragment fragment, boolean replacing, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final AlertDialog[] dialogRef = new AlertDialog[1];
        final String[] firstCode = new String[1];
        final Utilities.Callback<CodeFieldContainer> onCompleted = container -> {
            String code = container.getCode();
            if (!ForkSettingsLock.isValidCode(code)) {
                showCodeError(container);
                return;
            }
            if (firstCode[0] == null) {
                firstCode[0] = code;
                clearCodeContainer(container);
                dialogRef[0].setMessage(LocaleController.getString(R.string.ForkSettingsLockConfirmCodeInfo));
                return;
            }
            if (!TextUtils.equals(firstCode[0], code)) {
                firstCode[0] = null;
                dialogRef[0].setMessage(LocaleController.getString(R.string.ForkSettingsLockCodesDoNotMatch));
                showCodeError(container);
                return;
            }
            ForkSettingsLock.setCode(code);
            AndroidUtilities.hideKeyboard(container);
            dialogRef[0].dismiss();
            if (onChanged != null) {
                onChanged.run();
            }
        };

        CodeFieldContainer container = createCodeContainer(context, onCompleted);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(replacing ? R.string.ForkSettingsLockChangeCode : R.string.ForkSettingsLockSetCode));
        builder.setMessage(LocaleController.getString(R.string.ForkSettingsLockInfo));
        builder.setView(wrapCodeContainer(context, container));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), null);

        AlertDialog dialog = builder.create();
        dialogRef[0] = dialog;
        if (fragment.showDialog(dialog) == null) {
            return;
        }
        focusCodeContainer(container);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setOnClickListener(v -> onCompleted.run(container));
        }
    }

    private static void showRemoveCodeDialog(BaseFragment fragment, Runnable onChanged) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(LocaleController.getString(R.string.ForkSettingsLockRemoveCode))
            .setMessage(LocaleController.getString(R.string.ForkSettingsLockRemoveCodeConfirm))
            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
            .setPositiveButton(LocaleController.getString(R.string.Remove), (d, which) -> {
                ForkSettingsLock.removeCode();
                if (onChanged != null) {
                    onChanged.run();
                }
            })
            .create();
        fragment.showDialog(dialog);
        TextView button = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private static CodeFieldContainer createCodeContainer(Context context, Utilities.Callback<CodeFieldContainer> onCompleted) {
        CodeFieldContainer container = new CodeFieldContainer(context) {
            @Override
            protected void processNextPressed() {
                onCompleted.run(this);
            }
        };
        container.setNumbersCount(4, CodeFieldContainer.TYPE_PASSCODE);
        for (CodeNumberField field : container.codeField) {
            field.setShowSoftInputOnFocusCompat(true);
            field.setTransformationMethod(PasswordTransformationMethod.getInstance());
            field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        }
        return container;
    }

    private static LinearLayout wrapCodeContainer(Context context, CodeFieldContainer container) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), AndroidUtilities.dp(4));
        layout.addView(container, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        return layout;
    }

    private static void focusCodeContainer(CodeFieldContainer container) {
        container.codeField[0].requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(container.codeField[0]), 100);
    }

    private static void clearCodeContainer(CodeFieldContainer container) {
        for (CodeNumberField field : container.codeField) {
            field.setText("");
        }
        container.codeField[0].requestFocus();
    }

    private static void showCodeError(CodeFieldContainer container) {
        try {
            container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {
        }
        clearCodeContainer(container);
        for (CodeNumberField field : container.codeField) {
            field.animateErrorProgress(1f);
        }
        AndroidUtilities.shakeViewSpring(container, 10, () -> AndroidUtilities.runOnUIThread(() -> {
            for (CodeNumberField field : container.codeField) {
                field.animateErrorProgress(0f);
            }
        }, 150));
    }
}

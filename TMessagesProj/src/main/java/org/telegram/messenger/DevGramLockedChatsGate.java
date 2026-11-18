package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

// DevGram: запрос разблокировки скрытых чатов — биометрия (с фолбэком на пасскод)
// или сразу пасскод. Дёргается при попытке раскрыть скрытые чаты.
public final class DevGramLockedChatsGate {

    public interface Callback {
        void onSuccess();
    }

    private DevGramLockedChatsGate() {}

    public static void prompt(Context ctx, Callback cb) {
        if (cb == null) return;
        // если ничего не настроено — просто пускаем (защиты нет)
        if (!DevGramLockedChats.hasPasscode() && !biometricAvailable(ctx)) {
            cb.onSuccess();
            return;
        }
        FragmentActivity fa = resolveActivity(ctx);
        if (DevGramLockedChats.biometricEnabled() && biometricAvailable(ctx) && fa != null) {
            showBiometric(ctx, fa, cb);
        } else {
            showPasscode(ctx, cb);
        }
    }

    private static boolean biometricAvailable(Context ctx) {
        try {
            int r = BiometricManager.from(ctx).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK);
            return r == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Throwable t) {
            return false;
        }
    }

    private static FragmentActivity resolveActivity(Context ctx) {
        Context c = ctx;
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof FragmentActivity) return (FragmentActivity) c;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        Activity a = LaunchActivity.instance;
        return a instanceof FragmentActivity ? (FragmentActivity) a : null;
    }

    private static void showBiometric(Context ctx, FragmentActivity fa, Callback cb) {
        try {
            BiometricPrompt prompt = new BiometricPrompt(fa,
                    ContextCompat.getMainExecutor(ctx),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            cb.onSuccess();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            // отмена/ошибка → предложим пасскод, если он есть
                            if (DevGramLockedChats.hasPasscode()
                                    && errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                    && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                // системная ошибка — оставим пользователю ручной путь
                            }
                            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                    && DevGramLockedChats.hasPasscode()) {
                                AndroidUtilities.runOnUIThread(() -> showPasscode(ctx, cb));
                            }
                        }
                    });
            BiometricPrompt.PromptInfo.Builder b = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(org.telegram.messenger.DevGramLocale.getString(R.string.DevGramLockedChats))
                    .setSubtitle(org.telegram.messenger.DevGramLocale.getString(R.string.DevGramLockedChatsUnlockHint))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK);
            if (DevGramLockedChats.hasPasscode()) {
                b.setNegativeButtonText(org.telegram.messenger.DevGramLocale.getString(R.string.DevGramLockedChatsUsePasscode));
            } else {
                b.setNegativeButtonText(LocaleController.getString(R.string.Cancel));
            }
            prompt.authenticate(b.build());
        } catch (Throwable t) {
            showPasscode(ctx, cb);
        }
    }

    private static void showPasscode(Context ctx, Callback cb) {
        if (!DevGramLockedChats.hasPasscode()) {
            // нет пасскода, но биометрия недоступна — пускаем
            cb.onSuccess();
            return;
        }
        org.telegram.ui.DevGramPasscodeSheet.showEnter(ctx,
                DevGramLockedChats::checkPasscode,
                cb::onSuccess);
    }
}

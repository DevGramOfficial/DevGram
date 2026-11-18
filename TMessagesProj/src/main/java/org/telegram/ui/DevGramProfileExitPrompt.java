package org.telegram.ui;

import org.telegram.messenger.DevGramLocale;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/** One exit decision per editor; dismissing the dialog always keeps the draft. */
final class DevGramProfileExitPrompt {
    private AlertDialog dialog;

    void show(BaseFragment host, Runnable save, Runnable discard) {
        if (host.getParentActivity() == null || dialog != null && dialog.isShowing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(host.getParentActivity(), host.getResourceProvider());
        builder.setTitle(DevGramLocale.text("Сохранить изменения?"));
        builder.setMessage(DevGramLocale.text("Вы изменили оформление профиля. Сохранить его перед выходом?"));
        builder.forceVerticalButtons();
        builder.setPositiveButton(DevGramLocale.text("Сохранить"), (d, w) -> save.run());
        builder.setNegativeButton(DevGramLocale.text("Выйти без сохранения"), (d, w) -> discard.run());
        builder.setNeutralButton(DevGramLocale.text("Продолжить редактирование"), null);
        AlertDialog value = builder.create();
        dialog = value;
        if (host.showDialog(value, d -> { if (dialog == value) dialog = null; }) == null) dialog = null;
    }

    void dismiss() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }
}

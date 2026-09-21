package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

/**
 * DevGram: подтверждение звонка — AlertDialog с аватаром собеседника у заголовка «Звонок»,
 * текстом «Позвонить <имя>?» и кнопками Отмена / Позвонить.
 */
public class DevGramCallConfirm {

    public static void show(Activity activity, TLRPC.User user, boolean videoCall, int account, Runnable onConfirm) {
        if (activity == null || user == null) {
            if (onConfirm != null) onConfirm.run();
            return;
        }
        final Context context = activity;
        final String name = ContactsController.formatName(user.first_name, user.last_name);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), 0);

        // — строка заголовка: аватар + «Звонок» —
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(20));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(account, user);
        avatar.setForUserOrChat(user, avatarDrawable);
        titleRow.addView(avatar, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        TextView title = new TextView(context);
        title.setText(videoCall ? "Видеозвонок" : "Звонок");
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        titleRow.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        content.addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        TextView message = new TextView(context);
        message.setText("Позвонить " + name + "?");
        message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        content.addView(message, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setView(content);
        b.setPositiveButton(LocaleController.getString(R.string.Call), (d, w) -> {
            if (onConfirm != null) onConfirm.run();
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.show();
    }
}

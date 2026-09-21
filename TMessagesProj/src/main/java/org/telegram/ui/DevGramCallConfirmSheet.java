package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

/**
 * DevGram: красивое подтверждение звонка (BottomSheet в стиле «Очистить аккаунт»):
 * аватар собеседника, «Позвонить <имя>?», зелёная кнопка звонка + отмена.
 */
public class DevGramCallConfirmSheet {

    public static void show(Context context, TLRPC.User user, boolean videoCall, int currentAccount, Runnable onConfirm) {
        if (context == null || user == null) {
            if (onConfirm != null) onConfirm.run();
            return;
        }
        final Theme.ResourcesProvider rp = null;
        final String name = ContactsController.formatName(user.first_name, user.last_name);

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(12));

        // — аватар собеседника —
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(40));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(currentAccount, user);
        avatar.setForUserOrChat(user, avatarDrawable);
        col.addView(avatar, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 6));

        // — маленький бейдж-иконка звонка под аватаром —
        ImageView callIcon = new ImageView(context);
        callIcon.setImageResource(videoCall ? R.drawable.msg_videocall : R.drawable.msg_calls_regular);
        callIcon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_chats_actionBackground, rp), PorterDuff.Mode.SRC_IN));
        col.addView(callIcon, LayoutHelper.createLinear(28, 28, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        // — заголовок —
        TextView title = new TextView(context);
        title.setText(videoCall ? "Видеозвонок" : "Звонок");
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        col.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        // — «Позвонить <имя>?» —
        TextView subtitle = new TextView(context);
        subtitle.setText("Позвонить пользователю " + name + "?");
        subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, rp));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setGravity(Gravity.CENTER);
        col.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setCustomView(wrap(context, col));
        final BottomSheet sheet = builder.create();

        // — зелёная кнопка «Позвонить» —
        TextView callBtn = new TextView(context);
        callBtn.setText(LocaleController.getString(R.string.Call));
        callBtn.setGravity(Gravity.CENTER);
        callBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        callBtn.setTypeface(AndroidUtilities.bold());
        callBtn.setTextColor(0xFFFFFFFF);
        callBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_chats_actionBackground, rp),
                Theme.getColor(Theme.key_chats_actionPressedBackground, rp)));
        callBtn.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        callBtn.setOnClickListener(v -> {
            sheet.dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        col.addView(callBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // — кнопка «Отмена» —
        TextView cancelBtn = new TextView(context);
        cancelBtn.setText(LocaleController.getString(R.string.Cancel));
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        cancelBtn.setTypeface(AndroidUtilities.bold());
        cancelBtn.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, rp));
        cancelBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_listSelector, rp),
                Theme.getColor(Theme.key_listSelector, rp)));
        cancelBtn.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        cancelBtn.setOnClickListener(v -> sheet.dismiss());
        col.addView(cancelBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        sheet.show();
    }

    private static View wrap(Context context, LinearLayout col) {
        FrameLayout frame = new FrameLayout(context);
        frame.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return frame;
    }
}

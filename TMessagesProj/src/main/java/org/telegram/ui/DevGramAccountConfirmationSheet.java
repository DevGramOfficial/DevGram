package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.DevGramBadgeEmojiDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

/** Explain account linking before an action, including Telegram's website-login alert. */
public final class DevGramAccountConfirmationSheet {
    private DevGramAccountConfirmationSheet() {}

    public static void show(BaseFragment fragment, Utilities.Callback<Boolean> done) {
        show(fragment, true, done);
    }

    public static void show(BaseFragment fragment, boolean supporter, Utilities.Callback<Boolean> done) {
        Context context = fragment.getParentActivity();
        if (context == null) { done.run(false); return; }
        Theme.ResourcesProvider resources = fragment.getResourceProvider();
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        ImageView icon = new ImageView(context) {
            private final DevGramBadgeEmojiDrawable plane = new DevGramBadgeEmojiDrawable(this, AndroidUtilities.dp(36),
                    org.telegram.ui.Components.AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS,
                    () -> Theme.isCurrentThemeDark() ? 0xffffffff : 0xff000000);
            {
                setScaleType(ScaleType.CENTER);
                plane.setCurrentAccount(fragment.getCurrentAccount());
                plane.set(DevGramBadges.EMOJI_SUPPORTER, false);
                setImageDrawable(plane);
            }
            @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); plane.attach(); }
            @Override protected void onDetachedFromWindow() { plane.detach(); super.onDetachedFromWindow(); }
        };
        icon.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(22),
                androidx.core.graphics.ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_dialogTextBlue2, resources), 24)));
        body.addView(icon, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));
        body.addView(text(context, resources, supporter ? "Панель поддержавших" : "DevGram и ваш аккаунт", 21, true), LayoutHelper.createLinear(-1, -2, 0, 0, 0, 8));
        body.addView(text(context, resources, supporter ? "Подтвердите аккаунт для настройки значка, обводки сообщений и доступа к бете."
                : "Для сохранения оформления, лайков и публикаций нужен вход в DevGram.", 15, false),
                LayoutHelper.createLinear(-1, -2, 0, 0, 0, 12));
        TextView website = text(context, resources, "Вход на api.devgram.space — это DevGram. Telegram может прислать уведомление об этом входе.", 13, false);
        website.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        website.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                androidx.core.graphics.ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_dialogTextBlue2, resources), 18)));
        body.addView(website, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 12));
        TextView privacy = text(context, resources, "Telegram передаст ID, имя, @username и фото. Номер телефона и переписки не передаются.", 13, false);
        privacy.setTextColor(Theme.getColor(Theme.key_dialogTextGray, resources));
        body.addView(privacy, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 20));
        ButtonWithCounterView confirm = new ButtonWithCounterView(context, resources).setRound();
        confirm.setText(DevGramLocale.text(supporter ? "Подтвердить аккаунт" : "Продолжить"), false);
        body.addView(confirm, LayoutHelper.createLinear(-1, 48));
        TextView later = text(context, resources, "Позже", 15, true);
        later.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resources));
        later.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resources), 2));
        body.addView(later, LayoutHelper.createLinear(-1, 48, 0, 4, 0, 0));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(body);
        BottomSheet sheet = new BottomSheet.Builder(context, true, resources).setCustomView(scroll).create();
        boolean[] answered = {false};
        confirm.setOnClickListener(view -> {
            if (answered[0]) return;
            answered[0] = true;
            confirm.setEnabled(false);
            sheet.dismiss();
            done.run(true);
        });
        later.setOnClickListener(view -> sheet.dismiss());
        // BaseFragment replaces Dialog's dismiss listener; the sheet's hide hook survives it.
        sheet.setOnHideListener(dialog -> { if (!answered[0]) { answered[0] = true; done.run(false); } });
        if (fragment.getVisibleDialog() != null && fragment.getVisibleDialog().isShowing()) {
            // Keep an open editor/comments sheet and its draft underneath the explanation.
            try { sheet.show(); }
            catch (RuntimeException error) {
                org.telegram.messenger.FileLog.e(error);
                if (!answered[0]) { answered[0] = true; done.run(false); }
            }
        } else if (fragment.showDialog(sheet) == null && !answered[0]) { answered[0] = true; done.run(false); }
    }

    private static TextView text(Context context, Theme.ResourcesProvider resources, String value, int size, boolean bold) {
        TextView text = new TextView(context);
        text.setText(DevGramLocale.text(value));
        text.setTextSize(size);
        text.setGravity(Gravity.CENTER);
        text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        if (bold) text.setTypeface(AndroidUtilities.bold());
        return text;
    }
}

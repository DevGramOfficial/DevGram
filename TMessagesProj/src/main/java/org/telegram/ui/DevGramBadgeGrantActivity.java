/*
 * DevGram: экран выдачи значка конкретному ID (отдельный фрагмент, НЕ диалог — иначе пикер эмодзи
 * закрывал бы форму). Сверху готовые роли в одно касание (значок + подпись + доступ), ниже живое
 * превью, а «Тонкая настройка» (любой эмодзи, своя подпись, доступ) свёрнута.
 */

package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class DevGramBadgeGrantActivity extends BaseFragment {

    private final long dialogId;
    private long selEmojiId = DevGramBadges.EMOJI_TEAM;
    private String selText = DevGramBadges.READY_TEXTS[0];
    private int selAccess = DevGramBadges.ACCESS_DEVELOPER | DevGramBadges.ACCESS_SUPPORTER;

    private TextView emojiRow;
    private TextView textRow;
    private BackupImageView badgePreview;
    private TextView previewTitle;
    private TextView previewText;
    private final TextView[] emojiOptions = new TextView[DevGramBadges.READY_EMOJI.length + 1];
    private final TextView[] textOptions = new TextView[DevGramBadges.READY_TEXTS.length + 1];
    private final TextView[] accessOptions = new TextView[2];
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow pickerPopup;
    private BackupImageView nameBadge;
    private long shownEmojiId;
    private final ArrayList<View> presetViews = new ArrayList<>();
    private final ArrayList<Preset> presets = new ArrayList<>();

    // Готовая роль: значок + подпись + доступ одной кнопкой
    private static class Preset {
        final String title, subtitle, text;
        final long emojiId;
        final int access;
        Preset(String title, String subtitle, long emojiId, String text, int access) {
            this.title = title;
            this.subtitle = subtitle;
            this.emojiId = emojiId;
            this.text = text;
            this.access = access;
        }
    }

    public DevGramBadgeGrantActivity(long dialogId) {
        this.dialogId = dialogId;
        DevGramBadges.Badge current = DevGramBadges.badgeOf(dialogId);
        if (current != null) {
            selEmojiId = current.emojiId;
            selText = current.text;
            selAccess = current.access;
        }
    }

    private static long shownId(long id) {
        return id < 0 ? -id : id;
    }

    @Override
    public View createView(Context context) {
        boolean existing = DevGramBadges.badgeOf(dialogId) != null;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(existing ? "Изменить значок" : "Новый значок");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        ScrollView scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, AndroidUtilities.dp(existing ? 130 : 88));

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(20));

        box.addView(createRecipientCard(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        box.addView(header(context, "Предпросмотр"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 18, 4, 8));
        box.addView(createPreviewCard(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        box.addView(header(context, "Роль"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 18, 4, 8));
        box.addView(createPresets(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        box.addView(createFineTuning(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        scroll.addView(box, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        footer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));

        TextView grantBtn = new TextView(context);
        grantBtn.setText(existing ? "Сохранить изменения" : "Выдать значок");
        grantBtn.setGravity(Gravity.CENTER);
        grantBtn.setTypeface(AndroidUtilities.bold());
        grantBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        grantBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        grantBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        grantBtn.setOnClickListener(v -> onGrant());
        footer.addView(grantBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

        if (existing) {
            TextView revokeBtn = new TextView(context);
            revokeBtn.setText("Снять значок");
            revokeBtn.setGravity(Gravity.CENTER);
            revokeBtn.setTypeface(AndroidUtilities.bold());
            revokeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            revokeBtn.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourceProvider));
            revokeBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12), 0,
                    Theme.multAlpha(Theme.getColor(Theme.key_text_RedBold, resourceProvider), 0.12f)));
            revokeBtn.setOnClickListener(v -> confirmRevoke());
            footer.addView(revokeBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 0, 6, 0, 0));
        }
        root.addView(footer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        refresh();

        return fragmentView = root;
    }

    private void fillPresets() {
        presets.clear();
        String[] t = DevGramBadges.READY_TEXTS;
        if (dialogId > 0) {
            presets.add(new Preset("Поддержавший", "Значок ✈️, функции для поддержавших",
                    DevGramBadges.EMOJI_SUPPORTER, t[1], DevGramBadges.ACCESS_SUPPORTER));
            presets.add(new Preset("Команда проекта", "Значок ✅, все функции и меню разработчика",
                    DevGramBadges.EMOJI_TEAM, t[0], DevGramBadges.ACCESS_DEVELOPER | DevGramBadges.ACCESS_SUPPORTER));
            presets.add(new Preset("Разработчик плагинов", "Значок 🧩, без доступа к функциям",
                    DevGramBadges.EMOJI_PLUGIN_DEV, t[4], 0));
        } else {
            presets.add(new Preset("Канал (верификация)", "Галочка ✅ у канала",
                    DevGramBadges.EMOJI_CHANNEL, t[2], 0));
            presets.add(new Preset("Официальный ресурс", "Значок ✈️ официальных каналов DevGram",
                    DevGramBadges.EMOJI_OFFICIAL, t[3], 0));
            presets.add(new Preset("Разработчик плагинов", "Значок 🧩 у канала с плагинами",
                    DevGramBadges.EMOJI_PLUGIN_DEV, t[4], 0));
        }
    }

    private int selectedPreset() {
        for (int i = 0; i < presets.size(); i++) {
            Preset p = presets.get(i);
            if (p.emojiId == selEmojiId && p.text.equals(selText) && (dialogId < 0 || p.access == selAccess)) {
                return i;
            }
        }
        return -1;
    }

    private View createPresets(Context context) {
        fillPresets();
        if (DevGramBadges.badgeOf(dialogId) == null && !presets.isEmpty()) {
            Preset p = presets.get(0);
            selEmojiId = p.emojiId;
            selText = p.text;
            selAccess = p.access;
        }
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        presetViews.clear();
        for (int i = 0; i < presets.size(); i++) {
            Preset p = presets.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
            BackupImageView emoji = new BackupImageView(context);
            emoji.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, p.emojiId));
            row.addView(emoji, LayoutHelper.createLinear(30, 30));
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(context);
            title.setText(p.title);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            texts.addView(title);
            TextView sub = new TextView(context);
            sub.setText(p.subtitle);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            texts.addView(sub, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 8, 0));
            android.widget.ImageView check = new android.widget.ImageView(context);
            check.setImageResource(R.drawable.msg_text_check);
            check.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
            check.setTag("check");
            row.addView(check, LayoutHelper.createLinear(24, 24));
            row.setOnClickListener(v -> {
                selEmojiId = p.emojiId;
                selText = p.text;
                selAccess = p.access;
                refresh();
            });
            presetViews.add(row);
            list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, i == 0 ? 0 : 8, 0, 0));
        }
        return list;
    }

    // Свёрнутый блок: любой эмодзи, своя подпись, доступ к функциям
    private View createFineTuning(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14));
        TextView title = new TextView(context);
        title.setText("Тонкая настройка");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        card.addView(title);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(header(context, "Значок"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 8));
        content.addView(createEmojiChoices(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(header(context, "Подпись"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 8));
        content.addView(createTextChoices(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (dialogId > 0) {
            content.addView(header(context, "Доступ к функциям"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 8));
            content.addView(createAccessChoices(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        // клики по чипам внутри не должны сворачивать карточку
        content.setClickable(true);
        card.addView(content, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        org.telegram.ui.Components.DevGramPluginInfoViews.makeCollapsible(card, selectedPreset() < 0);
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        return card;
    }

    private TextView header(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTypeface(AndroidUtilities.bold());
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourceProvider));
        return view;
    }

    private TextView choice(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        tv.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                Theme.getColor(Theme.key_listSelector, resourceProvider)));
        tv.setClickable(true);
        return tv;
    }

    private View createRecipientCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14));
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));

        TLObject peer = dialogId > 0 ? getMessagesController().getUser(dialogId) : getMessagesController().getChat(-dialogId);
        String name;
        String username = null;
        if (peer instanceof TLRPC.User) {
            name = UserObject.getUserName((TLRPC.User) peer);
            username = UserObject.getPublicUsername((TLRPC.User) peer);
        } else if (peer instanceof TLRPC.Chat) {
            name = ((TLRPC.Chat) peer).title;
            username = ChatObject.getPublicUsername((TLRPC.Chat) peer);
        } else {
            name = dialogId > 0 ? "Пользователь" : "Чат или канал";
        }

        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(28));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (peer != null) {
            avatarDrawable.setInfo(peer);
            avatar.setForUserOrChat(peer, avatarDrawable);
        } else {
            avatarDrawable.setInfo(dialogId, name, null);
            avatar.setImageDrawable(avatarDrawable);
        }
        card.addView(avatar, LayoutHelper.createLinear(56, 56));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        texts.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView sub = new TextView(context);
        sub.setText((username == null || username.isEmpty() ? "" : "@" + username + "  ·  ")
                + (dialogId > 0 ? "Пользователь" : "Чат") + "  ·  ID " + shownId(dialogId));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        texts.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        card.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));
        return card;
    }

    private View createEmojiChoices(Context context) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        for (int i = 0; i < DevGramBadges.READY_EMOJI.length; i++) {
            final int index = i;
            emojiOptions[i] = choice(context, DevGramBadges.READY_EMOJI_LABELS[i]);
            emojiOptions[i].setOnClickListener(v -> {
                selEmojiId = DevGramBadges.READY_EMOJI[index];
                refresh();
            });
            row.addView(emojiOptions[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, i == 0 ? 0 : 8, 0, 0, 0));
        }
        emojiRow = choice(context, "＋ Свой эмодзи");
        emojiRow.setOnClickListener(v -> openEmojiPicker());
        emojiOptions[DevGramBadges.READY_EMOJI.length] = emojiRow;
        row.addView(emojiRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 8, 0, 0, 0));
        scroll.addView(row);
        return scroll;
    }

    private View createTextChoices(Context context) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        for (int i = 0; i < DevGramBadges.READY_TEXTS.length; i++) {
            final int index = i;
            textOptions[i] = choice(context, DevGramBadges.READY_TEXT_LABELS[i]);
            textOptions[i].setOnClickListener(v -> {
                selText = DevGramBadges.READY_TEXTS[index];
                refresh();
            });
            row.addView(textOptions[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, i == 0 ? 0 : 8, 0, 0, 0));
        }
        textRow = choice(context, "＋ Своя подпись");
        textRow.setOnClickListener(v -> askCustomText());
        textOptions[DevGramBadges.READY_TEXTS.length] = textRow;
        row.addView(textRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 8, 0, 0, 0));
        scroll.addView(row);
        return scroll;
    }

    private View createAccessChoices(Context context) {
        LinearLayout row = new LinearLayout(context);
        accessOptions[0] = choice(context, "Поддержавший");
        accessOptions[0].setOnClickListener(v -> {
            selAccess ^= DevGramBadges.ACCESS_SUPPORTER;
            refresh();
        });
        row.addView(accessOptions[0], LayoutHelper.createLinear(0, 40, 1f));

        accessOptions[1] = choice(context, "Разработчик");
        accessOptions[1].setOnClickListener(v -> {
            selAccess ^= DevGramBadges.ACCESS_DEVELOPER;
            if ((selAccess & DevGramBadges.ACCESS_DEVELOPER) != 0) {
                selAccess |= DevGramBadges.ACCESS_SUPPORTER;
            }
            refresh();
        });
        row.addView(accessOptions[1], LayoutHelper.createLinear(0, 40, 1f, 8, 0, 0, 0));
        return row;
    }

    // Как увидят другие: значок слева от ника и плашка по нажатию на него
    private View createPreviewCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));

        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameBadge = new BackupImageView(context);
        nameRow.addView(nameBadge, LayoutHelper.createLinear(20, 20, 0, 0, 6, 0));
        previewTitle = new TextView(context);
        previewTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        previewTitle.setTypeface(AndroidUtilities.bold());
        previewTitle.setSingleLine(true);
        previewTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        previewTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        nameRow.addView(previewTitle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        card.addView(nameRow);

        LinearLayout bulletin = new LinearLayout(context);
        bulletin.setGravity(Gravity.CENTER_VERTICAL);
        bulletin.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        bulletin.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), Theme.getColor(Theme.key_undo_background, resourceProvider)));
        badgePreview = new BackupImageView(context);
        bulletin.addView(badgePreview, LayoutHelper.createLinear(28, 28));
        previewText = new TextView(context);
        previewText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        previewText.setTextColor(Theme.getColor(Theme.key_undo_infoColor, resourceProvider));
        bulletin.addView(previewText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));
        card.addView(bulletin, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        return card;
    }

    private void refresh() {
        int selectedBg = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider), 0.14f);
        int normalBg = Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider);
        int selectedText = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider);
        int normalText = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
        boolean readyEmoji = false;
        for (int i = 0; i < DevGramBadges.READY_EMOJI.length; i++) {
            boolean selected = selEmojiId == DevGramBadges.READY_EMOJI[i];
            readyEmoji |= selected;
            updateChoice(emojiOptions[i], selected, selectedBg, normalBg, selectedText, normalText);
        }
        updateChoice(emojiOptions[DevGramBadges.READY_EMOJI.length], !readyEmoji,
                selectedBg, normalBg, selectedText, normalText);
        boolean readyText = false;
        for (int i = 0; i < DevGramBadges.READY_TEXTS.length; i++) {
            boolean selected = DevGramBadges.READY_TEXTS[i].equals(selText);
            readyText |= selected;
            updateChoice(textOptions[i], selected, selectedBg, normalBg, selectedText, normalText);
        }
        updateChoice(textOptions[DevGramBadges.READY_TEXTS.length], !readyText,
                selectedBg, normalBg, selectedText, normalText);
        updateChoice(accessOptions[0], (selAccess & DevGramBadges.ACCESS_SUPPORTER) != 0,
                selectedBg, normalBg, selectedText, normalText);
        updateChoice(accessOptions[1], (selAccess & DevGramBadges.ACCESS_DEVELOPER) != 0,
                selectedBg, normalBg, selectedText, normalText);
        int preset = selectedPreset();
        for (int i = 0; i < presetViews.size(); i++) {
            View row = presetViews.get(i);
            boolean selected = i == preset;
            row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16),
                    selected ? selectedBg : normalBg, Theme.getColor(Theme.key_listSelector, resourceProvider)));
            View check = row.findViewWithTag("check");
            if (check != null) check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
        if (selEmojiId != shownEmojiId) {
            shownEmojiId = selEmojiId;
            if (badgePreview != null) {
                badgePreview.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(
                        currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, selEmojiId));
            }
            if (nameBadge != null) {
                nameBadge.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(
                        currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, selEmojiId));
            }
        }
        String name = peerDisplayName();
        if (previewTitle != null) previewTitle.setText(name);
        if (previewText != null) previewText.setText(selText.replace("{name}", name));
    }

    private void updateChoice(TextView view, boolean selected, int selectedBg, int normalBg,
                              int selectedText, int normalText) {
        if (view == null) return;
        view.setTypeface(selected ? AndroidUtilities.bold() : null);
        view.setTextColor(selected ? selectedText : normalText);
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18),
                selected ? selectedBg : normalBg, Theme.getColor(Theme.key_listSelector, resourceProvider)));
    }

    private String peerDisplayName() {
        if (dialogId > 0) {
            TLRPC.User user = getMessagesController().getUser(dialogId);
            if (user != null) return UserObject.getUserName(user);
            return "Пользователь " + shownId(dialogId);
        }
        TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
        if (chat != null && chat.title != null) return chat.title;
        return "Чат " + shownId(dialogId);
    }

    // Тот же пикер кастом-эмодзи, что статусы в чате: даёт document_id.
    private void openEmojiPicker() {
        if (pickerPopup != null || emojiRow == null) {
            return;
        }
        SelectAnimatedEmojiDialog layout = new SelectAnimatedEmojiDialog(
                this, getContext(), false, null, SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, resourceProvider) {
            @Override
            protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                if (documentId != null) {
                    selEmojiId = documentId;
                    refresh();
                }
                if (pickerPopup != null) {
                    pickerPopup.dismiss();
                    pickerPopup = null;
                }
            }
        };
        layout.setSelected(selEmojiId == 0 ? null : selEmojiId);
        pickerPopup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                pickerPopup = null;
            }
        };
        pickerPopup.showAsDropDown(emojiRow, AndroidUtilities.dp(16), AndroidUtilities.dp(4), Gravity.TOP | Gravity.LEFT);
        pickerPopup.dimBehind();
    }

    private void askCustomText() {
        Context context = getParentActivity();
        EditTextBoldCursor et = makeInput(context, "Текст подписи ({name} = имя)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setText(selText);

        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(et, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Своя подпись");
        b.setView(container);
        b.setPositiveButton(LocaleController.getString(R.string.Done), (d, w) -> {
            String t = et.getText().toString().trim();
            if (!t.isEmpty()) {
                selText = t;
                refresh();
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        et.requestFocus();
        AndroidUtilities.showKeyboard(et);
    }

    private void onGrant() {
        DevGramBadgesActivity.ensureAdminSignedIn(this, () -> {
            boolean existed = DevGramBadges.badgeOf(dialogId) != null;
            DevGramBadges.grantBadge(dialogId, selEmojiId, selText, selAccess);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            showDoneBulletin(existed ? "Значок обновлён" : "Значок выдан: " + peerDisplayName());
            finishFragment();
        });
    }

    private void confirmRevoke() {
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        b.setTitle("Снять значок?");
        b.setMessage("Значок пропадёт у " + peerDisplayName() + " у всех пользователей DevGram.");
        b.setPositiveButton("Снять", (d, w) -> DevGramBadgesActivity.ensureAdminSignedIn(this, () -> {
            DevGramBadges.revoke(dialogId);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            showDoneBulletin("Значок снят");
            finishFragment();
        }));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button != null) button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourceProvider));
    }

    // плашка на предыдущем экране — этот сейчас закроется
    private void showDoneBulletin(String text) {
        BaseFragment prev = getParentLayout() != null && getParentLayout().getFragmentStack().size() >= 2
                ? getParentLayout().getFragmentStack().get(getParentLayout().getFragmentStack().size() - 2) : null;
        if (prev != null) {
            AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(prev).createSimpleBulletin(R.raw.contact_check, text).show(), 300);
        }
    }

    private EditTextBoldCursor makeInput(Context context, String hint, int inputType) {
        return DevGramBadgesActivity.makeInput(this, context, hint, inputType);
    }
}

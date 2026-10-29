package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Locale;

// DevGram (команда): список выданных значков — поиск, фильтр по ролям и действия по нажатию.
public class DevGramBadgeListActivity extends BaseFragment implements org.telegram.messenger.NotificationCenter.NotificationCenterDelegate {

    static final int ROLE_TEAM = 1, ROLE_SUPPORTER = 2, ROLE_CHANNEL = 3, ROLE_PLUGIN = 4;
    private static final String[] FILTER_TITLES = {"Все", "Команда", "Поддержавшие", "Каналы", "🧩 Разработчики"};
    private static final int ID_ROW_BASE = 1000;

    private UniversalRecyclerView listView;
    private LinearLayout chipsRow;
    private final ArrayList<Long> shown = new ArrayList<>();
    private final java.util.HashMap<Long, BadgeRow> rows = new java.util.HashMap<>(); // не пересоздаём строки на каждый символ поиска
    private int filter;
    private String query = "";

    // Роль записи: по выданному командой значку и типу диалога (у каналов свой значок)
    static int roleOf(long dialogId, DevGramBadges.Badge b) {
        if (b == null) return 0;
        if (b.originalEmojiId == DevGramBadges.EMOJI_PLUGIN_DEV) return ROLE_PLUGIN;
        if (dialogId < 0) return ROLE_CHANNEL;
        if (b.originalEmojiId == DevGramBadges.EMOJI_TEAM || (b.access & DevGramBadges.ACCESS_DEVELOPER) != 0) return ROLE_TEAM;
        return ROLE_SUPPORTER;
    }

    static String roleLabel(int role) {
        switch (role) {
            case ROLE_TEAM: return "Команда";
            case ROLE_SUPPORTER: return "Поддержавший";
            case ROLE_CHANNEL: return "Канал";
            case ROLE_PLUGIN: return "Разработчик плагинов";
            default: return "";
        }
    }

    // список обновляется сам, когда из облака приходят изменения
    @Override
    public boolean onFragmentCreate() {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(this, org.telegram.messenger.NotificationCenter.reloadInterface);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(this, org.telegram.messenger.NotificationCenter.reloadInterface);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == org.telegram.messenger.NotificationCenter.reloadInterface) {
            rows.clear();
            update();
        }
    }

    @Override
    public View createView(Context context) {
        DevGramBadges.syncFromCloud();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Выданные значки");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        // поиск
        FrameLayout searchWrap = new FrameLayout(context);
        searchWrap.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(21), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.msg_search);
        searchIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        searchWrap.addView(searchIcon, LayoutHelper.createFrame(20, 20, Gravity.CENTER_VERTICAL | Gravity.LEFT, 14, 0, 0, 0));
        EditTextBoldCursor search = new EditTextBoldCursor(context);
        search.setHint("Имя, @юзернейм или ID");
        search.setSingleLine(true);
        search.setBackground(null);
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        search.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        search.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        search.setCursorSize(AndroidUtilities.dp(20));
        search.setCursorWidth(1.5f);
        search.setPadding(AndroidUtilities.dp(44), 0, AndroidUtilities.dp(14), 0);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT).replace("@", "");
                update();
            }
        });
        searchWrap.addView(search, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42));
        root.addView(searchWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 12, 10, 12, 6));

        // фильтр по ролям
        HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsRow = new LinearLayout(context);
        chipsRow.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        chipsScroll.addView(chipsRow);
        root.addView(chipsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 2, 0, 6));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        root.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        actionBar.setAdaptiveBackground(listView);
        rebuildChips();
        return fragmentView = root;
    }

    @Override
    public void onResume() {
        super.onResume();
        DevGramBadges.syncFromCloud();
        rows.clear(); // значок могли изменить на экране выдачи
        update();
    }

    private void update() {
        rebuildChips();
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }

    private void rebuildChips() {
        if (chipsRow == null) return;
        int[] counts = new int[FILTER_TITLES.length];
        for (long id : DevGramBadges.listGranted()) {
            counts[0]++;
            int role = roleOf(id, DevGramBadges.badgeOf(id));
            if (role > 0 && role < counts.length) counts[role]++;
        }
        chipsRow.removeAllViews();
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider);
        for (int i = 0; i < FILTER_TITLES.length; i++) {
            final int index = i;
            TextView chip = new TextView(getContext());
            chip.setText(FILTER_TITLES[i] + "  " + counts[i]);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            chip.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            boolean sel = filter == i;
            chip.setTypeface(sel ? AndroidUtilities.bold() : null);
            chip.setTextColor(sel ? Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18),
                    sel ? accent : Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                    Theme.getColor(Theme.key_listSelector, resourceProvider)));
            chip.setOnClickListener(v -> {
                filter = index;
                update();
            });
            chipsRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, i == 0 ? 0 : 8, 2, 0, 2));
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        shown.clear();
        for (long id : DevGramBadges.listGranted()) {
            DevGramBadges.Badge b = DevGramBadges.badgeOf(id);
            if (filter != 0 && roleOf(id, b) != filter) continue;
            if (!query.isEmpty()) {
                String hay = (nameFor(id) + " " + usernameFor(id) + " " + Math.abs(id)).toLowerCase(Locale.ROOT);
                if (!hay.contains(query)) continue;
            }
            shown.add(id);
        }
        if (shown.isEmpty()) {
            items.add(UItem.asShadow(DevGramBadges.listGranted().isEmpty() ? "Выданных значков пока нет." : "Ничего не найдено."));
            return;
        }
        for (int i = 0; i < shown.size(); i++) {
            long id = shown.get(i);
            BadgeRow row = rows.get(id);
            if (row == null) rows.put(id, row = new BadgeRow(getContext(), id));
            items.add(UItem.asCustom(ID_ROW_BASE + i, row));
        }
        items.add(UItem.asShadow("Нажмите на строку, чтобы изменить, открыть или снять значок."));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        int idx = item.id - ID_ROW_BASE;
        if (idx >= 0 && idx < shown.size()) showActions(shown.get(idx));
    }

    private void showActions(long dialogId) {
        TLObject peer = peerFor(dialogId);
        ArrayList<CharSequence> titles = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        titles.add("Изменить значок");
        icons.add(R.drawable.msg_edit);
        actions.add(() -> presentFragment(new DevGramBadgeGrantActivity(dialogId)));
        if (peer != null) {
            titles.add(dialogId > 0 ? "Открыть профиль" : "Открыть канал");
            icons.add(R.drawable.msg_openprofile);
            actions.add(() -> {
                Bundle args = new Bundle();
                if (dialogId > 0) args.putLong("user_id", dialogId);
                else args.putLong("chat_id", -dialogId);
                presentFragment(new ProfileActivity(args));
            });
        }
        titles.add("Скопировать ID");
        icons.add(R.drawable.msg_copy);
        actions.add(() -> {
            AndroidUtilities.addToClipboard(String.valueOf(Math.abs(dialogId)));
            BulletinFactory.of(this).createCopyBulletin("ID скопирован").show();
        });
        titles.add("Снять значок");
        icons.add(R.drawable.msg_delete);
        actions.add(() -> confirmRevoke(dialogId));
        int[] iconArr = new int[icons.size()];
        for (int i = 0; i < iconArr.length; i++) iconArr[i] = icons.get(i);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        String name = nameFor(dialogId);
        b.setTitle(name.isEmpty() ? "ID " + Math.abs(dialogId) : name);
        b.setItems(titles.toArray(new CharSequence[0]), iconArr, (d, which) -> actions.get(which).run());
        AlertDialog dialog = b.create();
        showDialog(dialog);
        dialog.setItemColor(titles.size() - 1, Theme.getColor(Theme.key_text_RedBold, resourceProvider), Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
    }

    private void confirmRevoke(long dialogId) {
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        String name = nameFor(dialogId);
        b.setTitle("Снять значок?");
        b.setMessage("Значок пропадёт у " + (name.isEmpty() ? "ID " + Math.abs(dialogId) : name) + " у всех пользователей DevGram.");
        b.setPositiveButton("Снять", (d, w) -> DevGramBadgesActivity.ensureAdminSignedIn(this, () -> {
            DevGramBadges.revoke(dialogId);
            rows.remove(dialogId);
            update();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Значок снят").show();
        }));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button != null) button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourceProvider));
    }

    private TLObject peerFor(long dialogId) {
        return dialogId > 0 ? getMessagesController().getUser(dialogId) : getMessagesController().getChat(-dialogId);
    }

    private String nameFor(long dialogId) {
        TLObject p = peerFor(dialogId);
        if (p instanceof TLRPC.User) return ContactsController.formatName(((TLRPC.User) p).first_name, ((TLRPC.User) p).last_name);
        if (p instanceof TLRPC.Chat) return ((TLRPC.Chat) p).title != null ? ((TLRPC.Chat) p).title : "";
        return "";
    }

    private String usernameFor(long dialogId) {
        TLObject p = peerFor(dialogId);
        String u = null;
        if (p instanceof TLRPC.User) u = UserObject.getPublicUsername((TLRPC.User) p);
        else if (p instanceof TLRPC.Chat) u = ChatObject.getPublicUsername((TLRPC.Chat) p);
        return u == null ? "" : u;
    }

    // строка: аватар, имя с живым значком, @юзернейм · ID · роль
    private class BadgeRow extends LinearLayout {
        BadgeRow(Context context, long dialogId) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            TLObject peer = peerFor(dialogId);
            DevGramBadges.Badge badge = DevGramBadges.badgeOf(dialogId);

            BackupImageView avatar = new BackupImageView(context);
            avatar.setRoundRadius(AndroidUtilities.dp(21));
            AvatarDrawable ad = new AvatarDrawable();
            String name = nameFor(dialogId);
            if (peer != null) {
                ad.setInfo(currentAccount, peer);
                avatar.setForUserOrChat(peer, ad);
            } else {
                ad.setInfo(dialogId, name.isEmpty() ? "?" : name, null);
                avatar.setImageDrawable(ad);
            }
            addView(avatar, LayoutHelper.createLinear(42, 42));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            LinearLayout nameRow = new LinearLayout(context);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            if (badge != null) {
                BackupImageView emoji = new BackupImageView(context);
                emoji.setAnimatedEmojiDrawable(DevGramBadges.tinted(AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, badge.emojiId), Theme.getColor(Theme.key_chats_verifiedBackground, resourceProvider)));
                nameRow.addView(emoji, LayoutHelper.createLinear(20, 20, 0, 0, 6, 0));
            }
            TextView title = new TextView(context);
            title.setText(name.isEmpty() ? (dialogId > 0 ? "Пользователь" : "Чат") : name);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            nameRow.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            texts.addView(nameRow);
            TextView sub = new TextView(context);
            String u = usernameFor(dialogId);
            sub.setText((u.isEmpty() ? "" : "@" + u + "  ·  ") + "ID " + Math.abs(dialogId) + "  ·  " + roleLabel(roleOf(dialogId, badge)));
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            texts.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));
        }
    }
}

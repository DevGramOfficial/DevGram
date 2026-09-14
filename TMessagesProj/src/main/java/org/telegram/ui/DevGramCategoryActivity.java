/*
 * DevGram: экран одной категории настроек мода.
 * Один фрагмент на все категории — разделы отличаются только набором опций.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.DevGramGeneralConfig;
import org.telegram.messenger.DevGramTranslator;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class DevGramCategoryActivity extends BaseFragment {

    public static final int CATEGORY_GENERAL = 0;
    public static final int CATEGORY_GHOST = 1;
    public static final int CATEGORY_SPY = 2;
    public static final int CATEGORY_APPEARANCE = 3;
    public static final int CATEGORY_CHATS = 4;
    public static final int CATEGORY_AI = 5; // DevGram: отдельный экран «ИИ-чат» (как AiPreferencesActivity у exteraGram)

    // DevGram: кнопки-переходы из «Чаты» (как asButtonWithSubtext AI/CHAT_SETTINGS у exteraGram)
    private static final int ID_OPEN_AI = 150;
    private static final int ID_OPEN_CHAT_SETTINGS = 151;
    // DevGram: экран AI Chat (повтор AiPreferencesActivity exteraGram)
    private static final int ID_AI_SERVICES = 152;
    private static final int ID_AI_ROLES = 153;
    private static final int ID_AI_HISTORY = 154;
    private static final int ID_AI_STREAMING = 155;
    private static final int ID_AI_RESP_ONLY = 156;
    private static final int ID_AI_QUOTE = 157;
    private static final int ID_AI_TEMPERATURE = 158;
    private static final int ID_AI_CLEAR_HISTORY = 159;
    private static final int MENU_OPEN_CHAT = 500; // пункт меню в шапке (не строка списка)
    private static final int MENU_GHOST_SCOPE = 501;

    // Режим призрака
    private static final int ID_GHOST_MASTER = 1;
    private static final int ID_GHOST_READ = 2;
    private static final int ID_GHOST_ONLINE = 3;
    private static final int ID_GHOST_TYPING = 4;
    private static final int ID_GHOST_STORIES = 705;
    private static final int ID_GHOST_OFFLINE = 706;
    private static final int ID_GHOST_MARK_READ = 707;
    private static final int ID_GHOST_SCHEDULED = 708;
    private static final int ID_GHOST_SOUND = 709;
    private static final int ID_GHOST_SUGGEST_STORY = 710;
    private static final int ID_GHOST_STATUS = 713;
    // Слежка
    private static final int ID_SAVE_DELETED = 5;
    private static final int ID_SAVE_HISTORY = 6;
    private static final int ID_SAVE_MEDIA = 10;
    private static final int ID_SAVE_BOTS = 11;
    private static final int ID_SAVE_READ_DATE = 714;
    private static final int ID_SAVE_LOCAL_ONLINE = 715;
    private static final int ID_MEDIA_SAVE_PATH = 719;
    private static final int ID_MEDIA_CACHE_LIMIT = 727;
    private static final int ID_CLEAR_SPY_DATABASE = 729;
    private static final int ID_EXPORT_SPY_DATABASE = 730;
    private static final int ID_IMPORT_SPY_DATABASE = 731;
    private static final int ID_DELETED_ICON = 700;      // значок пометки удалённого
    private static final int ID_DELETED_ICON_COLOR = 701; // цвет пометки удалённого
    private static final int ID_DELETED_TRANSPARENT = 702; // полупрозрачные удалённые
    private static final int ID_DELETED_PREVIEW = 703;   // превью удалённого сообщения
    private org.telegram.ui.Components.DevGramDeletedPreviewCell deletedPreviewCell;
    private org.telegram.ui.Components.DevGramDeletedColorRow deletedColorRow;
    // Основные
    private static final int ID_SHOW_CONTACTS = 7;
    private static final int ID_DISABLE_ADS = 14;
    private static final int ID_LOCAL_PREMIUM = 15;
    private static final int ID_STREAKS = 16;
    private static final int ID_VPN = 17;
    private static final int ID_VPN_DIAG = 19;
    private static final int ID_TRANSLATE_BUTTON = 160;
    private static final int ID_TRANSLATE_CHAT = 161;
    private static final int ID_TRANSLATION_PROVIDER = 162;
    private static final int ID_TRANSLATION_FORMALITY = 163;
    private static final int ID_TRANSLATION_TARGET = 164;
    private static final int ID_DO_NOT_TRANSLATE = 165;
    private static final int ID_RELATIVE_LAST_SEEN = 166;
    private static final int ID_INAPP_VIBRATION = 167;
    private static final int ID_FILTER_ZALGO = 168;
    private static final int ID_YANDEX_MAPS = 169;
    private static final int ID_DOWNLOAD_BOOST = 170;
    private static final int ID_UPLOAD_BOOST = 171;
    private static final int ID_CUSTOM_SAVE_PATH = 172;
    private static final int ID_HIDE_PHONE = 173;
    private static final int ID_SHOW_ID_DC = 174;
    private static final int ID_HIDE_ARCHIVE = 175;
    private static final int ID_ARCHIVE_ON_PULL = 176;
    private static final int ID_DISABLE_UNARCHIVE_SWIPE = 177;
    // Внешний вид
    private static final int ID_DESIGN_MODE = 40; // общий пресет дизайна: DevGram/iOS/Старый
    private static final int ID_NUMBER_ROUNDING = 20;
    private static final int ID_AVATAR_SHAPE = 21; // форма аватара (перенос из скрытых Fork-настроек)
    private static final int ID_SQUARE_FAB = 25;   // квадратная кнопка (FAB)
    private static final int ID_GLASS_MENU = 26;   // стеклянное меню сообщения
    private static final int ID_HIDE_EMOJI_CATEGORIES = 27; // скрыть строку категорий в поиске эмодзи
    private static final int ID_SYSTEM_EMOJI = 30;
    private static final int ID_FORCE_SNOW = 31;
    private static final int ID_FOLDER_TABS_STYLE = 32;
    private static final int ID_STICKER_SIZE = 33;
    private static final int ID_HIDE_STICKER_TIME = 130;
    private static final int ID_RECOGNITION_LANG = 131;
    private static final int ID_RECOGNITION_AI = 132;
    private static final int ID_REMEMBER_LAST_CAMERA = 133;
    private static final int ID_ZOOM_SLIDER = 134;
    private static final int ID_STATIC_ZOOM = 135;
    private static final int ID_PAUSE_MINIMIZE = 136;
    private static final int ID_CAMERA_TYPE = 137;
    private static final int ID_VIDEO_CAMERA = 138;
    private static final int ID_CENTER_TITLE = 34;
    // перенос рабочих настроек из скрытого Fork в наше меню
    private static final int ID_HIDE_ALL_CHATS = 35;
    private static final int ID_MINI_AVATARS = 36;
    private static final int ID_HIDE_CONTACTS_DIALOGS = 37;
    private static final int ID_LAST_SEEN_DOTS = 38;
    private static final int ID_CHAT_BLUR = 39;

    // ExteraGram 12.9.0: avatarCorners по умолчанию 28dp.
    private static int avatarCornersInit() {
        float f = org.telegram.messenger.AndroidUtilities.avatarCornersF();
        if (f < 0) {
            return org.telegram.messenger.AndroidUtilities.avatarCornersType()
                    == org.telegram.messenger.AndroidUtilities.AVATAR_CORNERS_SQUARE ? 0 : 28;
        }
        return Math.max(0, Math.min(30, (int) f));
    }

    private static boolean gPref(String key, boolean def) {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean(key, def);
    }

    private static void gToggle(String key, boolean def) {
        org.telegram.messenger.MessagesController.getGlobalMainSettings()
                .edit().putBoolean(key, !gPref(key, def)).apply();
    }

    private static int gInt(String key, int def) {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings().getInt(key, def);
    }

    // Подпись реакции двойного тапа: кастомный эмодзи (animated_...) → «Своя эмодзи», иначе сам эмодзи
    private String doubleTapReactionLabel() {
        String r = org.telegram.messenger.MediaDataController.getInstance(currentAccount).getDoubleTapReaction();
        if (r == null || r.isEmpty()) return "—";
        return r.startsWith("animated_") ? "Своя эмодзи" : r;
    }

    // MD3 (master + 5 подпунктов) и остальные настройки внешнего вида — pref-ключи dg_*.
    private static final int ID_SINGLE_CORNER = 40;
    private static final int ID_HIDE_STATUS = 41;
    private static final int ID_HIDE_STORIES = 42;
    private static final int ID_HIDE_FAB = 43;
    private static final int ID_HIDE_SEARCHBAR = 44;
    private static final int ID_TAB_COUNTER = 45;
    private static final int ID_SYSTEM_FONTS = 46;
    private static final int ID_GOOEY = 47;
    private static final int ID_SEPARATE_HEADERS = 49;
    private static final int ID_ACTIONBAR_TITLE = 59;
    private static final int ID_DIVIDER_STYLE = 60;
    private static final int ID_GLASS_OUTLINE = 61;
    private static final int ID_PILL_STACK = 62;
    private static final int ID_DISGUISE = 63; // DevGram: маскировка (иконка + имя приложения)
    private static final int ID_LOCKED_CHATS = 64; // DevGram: скрытые запароленные чаты

    // Варианты для строк-селекторов (как в exteraGram)
    private static final String[] ACTIONBAR_TITLE_OPTIONS = {"Название приложения", "Имя пользователя", "Имя", "Чаты"};
    private static final String[] DIVIDER_STYLE_OPTIONS = {"Скрыть", "Линия", "Сегменты"};
    // Порядок значений совпадает с enum ExteraGram: GLARE, SOLID, HIDDEN.
    private static final String[] GLASS_OUTLINE_OPTIONS = {"Блики", "Сплошная", "Скрыта"};
    private static final String[] TRANSLATION_PROVIDERS = {"Telegram", "Google", "Yandex", "DeepL"};
    private static final String[] TRANSLATION_FORMALITIES = {"По умолчанию", "Неформально", "Формально"};
    private static final String[] ID_DC_OPTIONS = {"Скрыть", "Telegram API", "Bot API"};

    // Показать меню выбора; пишет int-ключ, обновляет список
    private void showChoice(String title, String[] options, String key, int def) {
        if (getParentActivity() == null) return;
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        int cur = gInt(key, def);
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = (i == cur ? "• " : "    ") + options[i];
        }
        b.setItems(labels, (d, which) -> {
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putInt(key, which).apply();
            refreshList();
            if ("dg_actionBarTitle".equals(key)) {
                rebuildAppearanceScreens();
            } else if ("dg_dividerStyle".equals(key) && listView != null) {
                listView.invalidateItemDecorations();
            }
        });
        b.setNegativeButton("Отмена", null);
        showDialog(b.create());
    }

    private void showGhostSoundChoice() {
        if (getParentActivity() == null) return;
        final org.telegram.messenger.DevGramGhostSettings.Settings settings =
                org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
        final String[] options = {"Никогда", "Только в режиме призрака", "Всегда"};
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = (settings.sendWithoutSound == i ? "• " : "    ") + options[i];
        }
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                .setTitle("Отправлять без звука")
                .setItems(labels, (dialog, which) -> {
                    settings.sendWithoutSound = which;
                    settings.save();
                    refreshListImmediately();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static final long[] MEDIA_CACHE_LIMITS = {
            300L << 20, 1L << 30, 2L << 30, 5L << 30, 16L << 30, Long.MAX_VALUE
    };

    private static int mediaCacheLimitIndex() {
        long current = DevGramConfig.mediaCacheLimit();
        for (int i = 0; i < MEDIA_CACHE_LIMITS.length; i++) {
            if (MEDIA_CACHE_LIMITS[i] == current) return i;
        }
        return MEDIA_CACHE_LIMITS.length - 1;
    }

    private void showMediaSavingBottomSheet() {
        if (getParentActivity() == null) return;

        final org.telegram.ui.ActionBar.BottomSheet.Builder builder =
                new org.telegram.ui.ActionBar.BottomSheet.Builder(getParentActivity());
        builder.setApplyTopPadding(false);
        builder.setApplyBottomPadding(false);

        android.widget.LinearLayout content = new android.widget.LinearLayout(getParentActivity());
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        builder.setCustomView(content);

        org.telegram.ui.Cells.HeaderCell header = new org.telegram.ui.Cells.HeaderCell(
                getParentActivity(), Theme.key_dialogTextBlue2, 21, 15, false);
        header.setText("СОХРАНЯТЬ ВЛОЖЕНИЯ");
        content.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final String[] labels = {
                "В личных чатах", "В публичных каналах", "В приватных каналах",
                "В публичных группах", "В приватных группах"
        };
        final String[] keys = {
                "saveMediaPrivateChats", "saveMediaPublicChannels", "saveMediaPrivateChannels",
                "saveMediaPublicGroups", "saveMediaPrivateGroups"
        };
        final boolean[] values = {
                DevGramConfig.saveMediaPrivateChats(), DevGramConfig.saveMediaPublicChannels(),
                DevGramConfig.saveMediaPrivateChannels(), DevGramConfig.saveMediaPublicGroups(),
                DevGramConfig.saveMediaPrivateGroups()
        };
        final org.telegram.ui.Cells.TextCheckBoxCell[] cells =
                new org.telegram.ui.Cells.TextCheckBoxCell[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            org.telegram.ui.Cells.TextCheckBoxCell cell =
                    new org.telegram.ui.Cells.TextCheckBoxCell(getParentActivity(), true, false);
            cells[i] = cell;
            cell.setTextAndCheck(labels[i], values[i], i != labels.length - 1);
            cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            cell.setOnClickListener(v -> cell.setChecked(values[index] = !values[index]));
            content.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        }

        View divider = new View(getParentActivity());
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider, resourceProvider));
        content.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));

        final org.telegram.ui.Cells.MaxFileSizeCell cellular =
                new org.telegram.ui.Cells.MaxFileSizeCell(getParentActivity());
        cellular.setText("Максимальный размер по мобильной сети");
        cellular.setSize(DevGramConfig.mediaCellularLimit());
        content.addView(cellular, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 80));

        final org.telegram.ui.Cells.MaxFileSizeCell wifi =
                new org.telegram.ui.Cells.MaxFileSizeCell(getParentActivity());
        wifi.setText("Максимальный размер по Wi‑Fi");
        wifi.setSize(DevGramConfig.mediaWifiLimit());
        content.addView(wifi, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 80));

        android.widget.FrameLayout buttons = new android.widget.FrameLayout(getParentActivity());
        buttons.setPadding(org.telegram.messenger.AndroidUtilities.dp(8),
                org.telegram.messenger.AndroidUtilities.dp(8),
                org.telegram.messenger.AndroidUtilities.dp(8),
                org.telegram.messenger.AndroidUtilities.dp(8));
        content.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

        android.widget.TextView cancel = mediaSheetButton("ОТМЕНА");
        buttons.addView(cancel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        cancel.setOnClickListener(v -> builder.getDismissRunnable().run());

        android.widget.TextView save = mediaSheetButton("СОХРАНИТЬ");
        buttons.addView(save, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        save.setOnClickListener(v -> {
            for (int i = 0; i < keys.length; i++) {
                DevGramConfig.setMediaCategory(keys[i], values[i]);
            }
            DevGramConfig.setMediaCellularLimit(cellular.getSize());
            DevGramConfig.setMediaWifiLimit(wifi.getSize());
            builder.getDismissRunnable().run();
        });
        showDialog(builder.create());
    }

    private android.widget.TextView mediaSheetButton(String text) {
        android.widget.TextView button = new android.widget.TextView(getParentActivity());
        button.setText(text);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourceProvider));
        button.setGravity(Gravity.CENTER);
        button.setTypeface(org.telegram.messenger.AndroidUtilities.getTypeface(
                org.telegram.messenger.AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        button.setPadding(org.telegram.messenger.AndroidUtilities.dp(10), 0,
                org.telegram.messenger.AndroidUtilities.dp(10), 0);
        button.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector, resourceProvider), 6));
        return button;
    }

    private void confirmClearSpy() {
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                .setTitle("Очистить историю DevGram?")
                .setMessage("Будут удалены сохранённые удалёнки, редакции, время прочтения и вложения.")
                .setPositiveButton("Очистить", (d, w) -> {
                    org.telegram.messenger.DevGramMessagesController.getInstance().clean();
                    refreshListImmediately();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Данные очищены").show();
                }).setNegativeButton("Отмена", null).show();
    }

    private void importSpyDatabase() {
        try {
            android.content.ClipboardManager manager = (android.content.ClipboardManager)
                    getParentActivity().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = manager.getPrimaryClip();
            String data = clip == null || clip.getItemCount() == 0 ? null
                    : clip.getItemAt(0).coerceToText(getParentActivity()).toString();
            if (!org.telegram.messenger.DevGramMessagesController.getInstance().importData(data)) throw new IllegalArgumentException();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Резервная копия импортирована").show();
        } catch (Throwable e) {
            BulletinFactory.of(this).createErrorBulletin("В буфере нет резервной копии DevGram").show();
        }
    }

    private void rebuildAppearanceScreens() {
        if (parentLayout != null) {
            parentLayout.rebuildFragments(0);
        }
    }

    private void refreshThenRebuildAppearanceScreens() {
        refreshList();
        org.telegram.messenger.AndroidUtilities.runOnUIThread(this::rebuildAppearanceScreens, 180);
    }
    private static final int ID_MD3 = 51;
    private static final int ID_MD3_PROGRESS = 52;
    private static final int ID_MD3_SLIDER = 53;
    private static final int ID_MD3_SWITCH = 54;
    private static final int ID_MD3_TITLE = 55;
    private static final int ID_MD3_BOTTOMNAV = 56;

    private static final String[] MD3_KEYS = {"dg_md3_progress", "dg_md3_slider", "dg_md3_switch", "dg_md3_title", "dg_md3_bottomnav"};

    private static int md3Count() {
        int c = 0;
        for (int i = 0; i < MD3_KEYS.length; i++) {
            if (gPref(MD3_KEYS[i], i < 3)) c++;
        }
        return c;
    }

    private org.telegram.ui.Components.DevGramAvatarPreviewCell avatarPreview;
    private org.telegram.ui.Components.DevGramChatListPreviewCell chatListPreview;
    private org.telegram.ui.Components.DevGramFoldersPreviewCell foldersPreview;
    private org.telegram.ui.Components.DevGramFabPreviewCell fabPreview;
    private org.telegram.ui.Cells.ThemePreviewMessagesCell messagesPreview;
    private org.telegram.ui.Cells.ThemePreviewMessagesCell stickerSizePreview;
    private org.telegram.ui.Components.DevGramDoubleTapCell doubleTapPreview;

    // Реальное превью сообщений через ChatMessageCell (как у exteraGram) — базовый ThemePreviewMessagesCell.
    private View getMessagesPreview() {
        if (messagesPreview == null) {
            messagesPreview = new org.telegram.ui.Cells.ThemePreviewMessagesCell(getContext(), getParentLayout(),
                    org.telegram.ui.Cells.ThemePreviewMessagesCell.TYPE_DEVGRAM_MESSAGES);
        }
        return messagesPreview;
    }

    // Реальное превью размера стикеров через ChatMessageCell (как у exteraGram) — тип TYPE_STICKER_SIZE.
    private View getStickerSizePreview() {
        if (stickerSizePreview == null) {
            stickerSizePreview = new org.telegram.ui.Cells.ThemePreviewMessagesCell(getContext(), getParentLayout(),
                    org.telegram.ui.Cells.ThemePreviewMessagesCell.TYPE_STICKER_SIZE);
        }
        return stickerSizePreview;
    }

    // Превью двойного нажатия как у exteraGram — 2 схематичных пузыря + иконки действий (in/out).
    private View getDoubleTapPreview() {
        if (doubleTapPreview == null) {
            doubleTapPreview = new org.telegram.ui.Components.DevGramDoubleTapCell(getContext());
        }
        return doubleTapPreview;
    }

    private org.telegram.ui.Components.DevGramStickerShapeCell stickerShapeCell;
    private View getStickerShapePreview() {
        if (stickerShapeCell == null) {
            stickerShapeCell = new org.telegram.ui.Components.DevGramStickerShapeCell(getContext());
            stickerShapeCell.setOnPick(shape -> {
                if (stickerSizePreview != null) {
                    stickerSizePreview.reloadMessages();
                }
            });
        }
        return stickerShapeCell;
    }

    private View getAvatarPreview() {
        if (avatarPreview == null) {
            avatarPreview = new org.telegram.ui.Components.DevGramAvatarPreviewCell(getContext());
        }
        return avatarPreview;
    }

    private View getChatListPreview() {
        if (chatListPreview == null) {
            chatListPreview = new org.telegram.ui.Components.DevGramChatListPreviewCell(getContext());
        }
        return chatListPreview;
    }

    private View getFoldersPreview() {
        if (foldersPreview == null) {
            foldersPreview = new org.telegram.ui.Components.DevGramFoldersPreviewCell(getContext());
        }
        return foldersPreview;
    }

    private View getFabPreview() {
        if (fabPreview == null) {
            fabPreview = new org.telegram.ui.Components.DevGramFabPreviewCell(getContext());
            fabPreview.setOnPick(square -> {
                DevGramConfig.setSquareFab(square);
                refreshList();
            });
        }
        return fabPreview;
    }
    // Чаты
    private static final int ID_DISABLE_MARKDOWN = 22;
    private static final int ID_HIDE_KEYBOARD_ON_SCROLL = 23;
    private static final int ID_INLINE_CALC = 704;
    private static final int ID_DISABLE_GREETING = 24;
    private static final int ID_COMMA_AFTER_MENTION = 28;
    private static final int ID_TIME_WITH_SECONDS = 29;
    private static final int ID_REPLACE_FORWARD = 70;
    private static final int ID_MENTION_BY_NAME = 71;
    private static final int ID_HIDE_SEND_AS = 72;
    private static final int ID_DISABLE_LINK_PREVIEW = 73;
    private static final int ID_DISABLE_NEXT_CHANNEL = 74;
    private static final int ID_DISABLE_QUICK_REACTION = 75;
    private static final int ID_HIDE_MESSAGE_REACTIONS = 76;
    private static final int ID_HIDE_SAVED_TAGS = 77;
    private static final int ID_FULL_RECENT_STICKERS = 78;
    private static final int ID_SHOW_ARCHIVED_STICKERS = 79;
    private static final int ID_ALWAYS_HD = 82;
    private static final int ID_REMOVE_MESSAGE_TAIL = 83;
    private static final int ID_REPLACE_EDITED = 84;
    private static final int ID_HIDE_SHARE_BUTTON = 85;
    private static final int ID_DOUBLE_TAP_REACTION = 86;
    private static final int ID_DOUBLE_TAP_IN = 125;
    private static final int ID_DOUBLE_TAP_OUT = 126;
    private static final int ID_MSGMENU = 120;
    private static final int ID_MSGMENU_COPYPHOTO = 121;
    private static final int ID_MSGMENU_SAVE = 122;
    private static final int ID_MSGMENU_HISTORY = 123;
    private static final int ID_MSGMENU_REPORT = 124;
    private static final int ID_MSGMENU_REPEAT = 127;
    private static final int ID_MSGMENU_CLEAR = 128;
    private static final int ID_MSGMENU_DETAILS = 129;

    private static final String[] MSGMENU_KEYS = {"dg_msgmenu_copyphoto", "dg_msgmenu_save", "dg_msgmenu_repeat", "dg_msgmenu_clear", "dg_msgmenu_history", "dg_msgmenu_report", "dg_msgmenu_generate", "dg_msgmenu_details"};
    private static int msgMenuCount() {
        int c = 0;
        for (String k : MSGMENU_KEYS) if (gPref(k, true)) c++;
        return c;
    }
    private static int pauseMinCount() {
        int c = 0;
        if (gPref("dg_pauseVideoOnMinimize", false)) c++;
        if (gPref("dg_pauseVoiceOnMinimize", false)) c++;
        if (gPref("dg_pauseRoundOnMinimize", false)) c++;
        return c;
    }
    private static final int ID_AI_EDITOR = 87;
    private static final int ID_AI_SUMMARIES = 88;
    private static final int ID_PHOTO_HAS_STICKER = 89;
    private static final int ID_DISABLE_FLIP_PHOTOS = 91;
    private static final int ID_REAR_VIDEO_MESSAGES = 92;
    private static final int ID_DISABLE_VOLUME_AUTOPLAY = 93;
    private static final int ID_DISABLE_RECENT_FILES = 94;
    private static final int ID_DISABLE_AUTOPLAY_VOICE = 95;
    private static final int ID_PREFER_ORIGINAL_QUALITY = 96;
    private static final int ID_SWIPE_TO_PIP = 97;
    private static final int ID_PAUSE_VIDEO_MINIMIZE = 98;
    private static final int ID_DOUBLE_TAP_SEEK = 99;
    private static final int ID_AI_CHAT = 100;
    private static final int ID_AI_PROVIDER = 101;
    private static final int ID_REPLY_ELEMENTS = 102;
    private static final int ID_REPLY_COLORS = 103;
    private static final int ID_REPLY_EMOJI = 104;
    private static final int ID_REPLY_BACKGROUND = 105;
    private static final int ID_HIDE_REACTIONS = 106;
    private static final int ID_HIDE_REACTIONS_CHANNELS = 107;
    private static final int ID_HIDE_REACTIONS_GROUPS = 108;
    private static final int ID_HIDE_REACTIONS_PRIVATE = 109;
    private static final int ID_QUICK_TRANSITIONS = 110;
    private static final int ID_QUICK_TRANSITIONS_CHANNELS = 111;
    private static final int ID_QUICK_TRANSITIONS_TOPICS = 112;
    private static final int ID_SHOW_ONLINE_STATUS = 113;
    private static final int ID_SHOW_POLL_RESULTS = 114;
    private static final int ID_MSGMENU_GENERATE = 115;
    private static final int ID_GROUP_MESSAGE_MENU = 116;
    private static final int ID_HIDE_CAMERA_TILE = 117;
    private static final int ID_PAUSE_VOICE_MINIMIZE = 118;
    private static final int ID_PAUSE_ROUND_MINIMIZE = 119;
    private static final String[] DOUBLE_TAP_SEEK_OPTIONS = {"5 секунд", "10 секунд", "15 секунд", "30 секунд"};

    private final int category;
    private UniversalRecyclerView listView;
    private boolean ghostModeExpanded;

    public DevGramCategoryActivity(int category) {
        this.category = category;
    }

    public static String titleFor(int category) {
        switch (category) {
            case CATEGORY_GHOST:
                return "Режим призрака";
            case CATEGORY_SPY:
                return "Слежка";
            case CATEGORY_APPEARANCE:
                return "Внешний вид";
            case CATEGORY_CHATS:
                return "Чаты";
            case CATEGORY_AI:
                return "ИИ-чат";
            default:
                return "Основные";
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(titleFor(category));
        // На экране AI Chat — иконка «открыть ИИ-чат» в шапке.
        if (category == CATEGORY_AI) {
            actionBar.createMenu().addItem(MENU_OPEN_CHAT, R.drawable.msg2_ask_question);
        } else if (category == CATEGORY_GHOST) {
            // AyuGram keeps the global/per-account selector in the action bar rather
            // than exposing it as another preference row.
            actionBar.createMenu().addItem(MENU_GHOST_SCOPE, R.drawable.msg_contacts)
                    .setContentDescription("Общие настройки для аккаунтов");
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_OPEN_CHAT) {
                    if (org.telegram.messenger.DevGramAiClient.isConfigured()) {
                        presentFragment(new DevGramAiChatActivity());
                    } else {
                        showAiProviderDialog(true);
                    }
                } else if (id == MENU_GHOST_SCOPE) {
                    boolean global = !org.telegram.messenger.DevGramGhostSettings.useGlobal();
                    org.telegram.messenger.DevGramGhostSettings.setUseGlobal(global);
                    refreshListImmediately();
                    BulletinFactory.of(DevGramCategoryActivity.this).createSimpleBulletin(
                            R.raw.info,
                            global ? "Общие настройки для всех аккаунтов" : "Настройки только этого аккаунта"
                    ).show();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);

        DevGramSettingsLink.consumeHighlight(listView); // подсветить пункт, если открыто по ссылке
        return fragmentView = contentView;
    }

    private int ghostCount() {
        org.telegram.messenger.DevGramGhostSettings.Settings s =
                org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
        int c = 0;
        if (!s.sendReadMessages) c++;
        if (!s.sendReadStories) c++;
        if (!s.sendOnline) c++;
        if (!s.sendTyping) c++;
        if (s.sendOfflineAfterOnline) c++;
        return c;
    }

    private static String ghostLock(boolean locked) {
        return locked ? "  🔒" : "";
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (category == CATEGORY_GHOST) {
            org.telegram.messenger.DevGramGhostSettings.Settings ghost =
                    org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
            items.add(UItem.asHeader("Режим призрака"));
            items.add(UItem.asExpandableSwitch(ID_GHOST_MASTER, "Режим призрака", ghostCount() + "/5")
                    .setChecked(DevGramConfig.isGhostModeActive())
                    .setCollapsed(!ghostModeExpanded)
                    .setClickCallback(v -> {
                        DevGramConfig.toggleGhostMode();
                        refreshListImmediately();
                    }));
            if (ghostModeExpanded) {
                items.add(UItem.asRoundCheckbox(ID_GHOST_READ, "Не читать сообщения" + ghostLock(ghost.readLocked))
                        .setChecked(!ghost.sendReadMessages).pad());
                items.add(UItem.asRoundCheckbox(ID_GHOST_STORIES, "Не читать истории" + ghostLock(ghost.storiesLocked))
                        .setChecked(!ghost.sendReadStories).pad());
                items.add(UItem.asRoundCheckbox(ID_GHOST_ONLINE, "Не отправлять онлайн" + ghostLock(ghost.onlineLocked))
                        .setChecked(!ghost.sendOnline).pad());
                items.add(UItem.asRoundCheckbox(ID_GHOST_TYPING, "Не отправлять набор текста" + ghostLock(ghost.typingLocked))
                        .setChecked(!ghost.sendTyping).pad());
                items.add(UItem.asRoundCheckbox(ID_GHOST_OFFLINE, "Отправлять офлайн после онлайна" + ghostLock(ghost.offlineLocked))
                        .setChecked(ghost.sendOfflineAfterOnline).pad());
                items.add(UItem.asShadow("Зажмите параметр, чтобы заблокировать его от главного переключателя."));
            }
            items.add(UItem.asCheck(ID_GHOST_MARK_READ, "Читать при действиях")
                    .setChecked(ghost.markReadAfterAction));
            items.add(UItem.asShadow("Автоматически читает сообщение при отправке нового или при реакции на сообщение."));
            items.add(UItem.asCheck(ID_GHOST_SCHEDULED, "Использовать отложку")
                    .setChecked(ghost.useScheduledMessages));
            items.add(UItem.asShadow("Автоматически ставит задержку примерно 12 секунд при отправке сообщений. При использовании этой функции вы не будете появляться в сети."));
            String sound = ghost.sendWithoutSound == 2 ? "Всегда" : ghost.sendWithoutSound == 1 ? "Только в режиме призрака" : "Никогда";
            items.add(UItem.asButton(ID_GHOST_SOUND, "Отправлять без звука", sound));
            items.add(UItem.asShadow("Отправляет сообщения по умолчанию без звука."));
            items.add(UItem.asCheck(ID_GHOST_SUGGEST_STORY, "Предлагать призрака для сторис")
                    .setChecked(ghost.suggestBeforeStory));
            items.add(UItem.asShadow("Показывает предупреждение перед открытием сторис, предлагая включить режим призрака."));
        } else if (category == CATEGORY_SPY) {
            // Пометка удалённых: живое превью сверху + 3 функции (как у AyuGram), остальное ниже
            items.add(UItem.asHeader("Пометка удалённых"));
            if (deletedPreviewCell == null) {
                deletedPreviewCell = new org.telegram.ui.Components.DevGramDeletedPreviewCell(getContext());
            }
            items.add(UItem.asCustom(ID_DELETED_PREVIEW, deletedPreviewCell));
            items.add(UItem.asCheck(ID_DELETED_TRANSPARENT, "Полупрозрачные удалёнки")
                    .setChecked(DevGramConfig.getSemiTransparentDeleted()));
            items.add(UItem.asButton(ID_DELETED_ICON, "Метка удалёнок",
                    org.telegram.messenger.DevGramDeletedMark.getMark(resourceProvider)));
            if (org.telegram.messenger.DevGramDeletedMark.getIcon() != 0) {
                if (deletedColorRow == null) {
                    deletedColorRow = new org.telegram.ui.Components.DevGramDeletedColorRow(getContext(), resourceProvider);
                    deletedColorRow.setOnColorClick(idx -> {
                        org.telegram.messenger.DevGramDeletedMark.setColorIndex(idx);
                        if (deletedPreviewCell != null) deletedPreviewCell.refresh();
                        // Значение строки — Spannable с одинаковым zero-width символом для
                        // всех цветов. DiffUtil не видит смену span, поэтому нужен полный bind.
                        refreshListImmediately();
                    });
                }
                deletedColorRow.setSelected(org.telegram.messenger.DevGramDeletedMark.getColorIndex());
                items.add(UItem.asCustom(ID_DELETED_ICON_COLOR, deletedColorRow));
            }
            items.add(UItem.asShadow("Значок появляется у удалённых сообщений в строке времени."));
            // Сохранение — ниже
            items.add(UItem.asHeader("Режим шпиона"));
            items.add(UItem.asCheck(ID_SAVE_DELETED, "Сохранять удалённые")
                    .setChecked(DevGramConfig.saveDeletedMessages));
            items.add(UItem.asCheck(ID_SAVE_HISTORY, "Сохранять историю изменений")
                    .setChecked(DevGramConfig.saveMessagesHistory));
            items.add(UItem.asShadow(null));
            items.add(UItem.asCheck(ID_SAVE_BOTS, "Сохранять в чатах с ботами")
                    .setChecked(DevGramConfig.saveInBotChats));
            items.add(UItem.asShadow(null));
            items.add(UItem.asCheck(ID_SAVE_READ_DATE, "Сохранять точное время прочтения")
                    .setChecked(DevGramConfig.saveReadDate));
            items.add(UItem.asShadow("Локально сохраняет данные о прочтении сообщений. Используется, если Telegram не предоставит дату прочтения."));
            items.add(UItem.asCheck(ID_SAVE_LOCAL_ONLINE, "Запоминать замеченный онлайн")
                    .setChecked(DevGramConfig.saveLocalOnline));
            items.add(UItem.asShadow("Сохраняет последний известный онлайн для людей со скрытым последним посещением."));
            items.add(UItem.asButtonCheck(ID_SAVE_MEDIA, "Сохранять вложения", "Настроить чаты и лимиты")
                    .setChecked(DevGramConfig.saveMedia));
            items.add(UItem.asButton(ID_MEDIA_SAVE_PATH, "Папка вложений", DevGramConfig.savedMediaFolder()));
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader("Максимальный размер папки"));
            items.add(UItem.asSlideView(
                    new String[]{"300 МБ", "1 ГБ", "2 ГБ", "5 ГБ", "16 ГБ", "∞"},
                    mediaCacheLimitIndex(),
                    index -> DevGramConfig.setMediaCacheLimit(MEDIA_CACHE_LIMITS[index])
            ).setId(ID_MEDIA_CACHE_LIMIT));
            items.add(UItem.asShadow("Если папка превысит лимит, самые старые вложения будут удалены с устройства."));
            items.add(UItem.asButton(ID_EXPORT_SPY_DATABASE, "Скопировать резервную копию"));
            items.add(UItem.asButton(ID_IMPORT_SPY_DATABASE, "Импортировать резервную копию"));
            items.add(UItem.asButton(ID_CLEAR_SPY_DATABASE, "Очистить историю DevGram"));
            items.add(UItem.asShadow(null));
        } else if (category == CATEGORY_APPEARANCE) {
            // Секции и порядок — как в exteraGram 12.9.0:
            // Закругление аватарок → Список чатов → Папки с чатами → Внешний вид → Настройки размытия

            // — Дизайн — общий пресет внешнего вида приложения. Применяет комбинацию уже
            // существующих настроек этого экрана (радиус аватарок, MD3-эффекты, «липкая»
            // анимация, заголовок по центру, профиль в стиле iOS) — не отдельный рендер-код.
            // Отправная точка, а не жёсткая блокировка: любой пункт можно донастроить вручную
            // ниже после выбора пресета.
            items.add(UItem.asHeader("Дизайн"));
            items.add(UItem.asSlideView(
                    new String[]{"DevGram", "iOS", "Старый"},
                    org.telegram.messenger.DevGramConfig.getDesignMode(),
                    index -> {
                        org.telegram.messenger.DevGramConfig.applyDesignPreset(index);
                        refreshThenRebuildAppearanceScreens();
                    }).setId(ID_DESIGN_MODE));
            items.add(UItem.asShadow("Пресет внешнего вида. Ниже можно донастроить каждый пункт отдельно."));

            // — Закругление аватарок — непрерывный слайдер Квадрат..Круг + живое превью
            items.add(UItem.asHeader("Закругление аватарок"));
            items.add(UItem.asIntSlideView(1, 0, avatarCornersInit(), 30,
                    val -> val <= 0 ? "Квадрат" : (val >= 30 ? "Круг" : String.valueOf(val)),
                    val -> {
                        org.telegram.messenger.MessagesController.getGlobalMainSettings()
                                .edit().putFloat("avatarCornersF", val).apply();
                        if (avatarPreview != null) avatarPreview.invalidate();
                        if (chatListPreview != null) chatListPreview.invalidate();
                    }).setId(ID_AVATAR_SHAPE));
            items.add(UItem.asCustom(getAvatarPreview()));
            items.add(UItem.asCheck(ID_SINGLE_CORNER, "Единое закругление")
                    .setChecked(gPref("dg_singleCorner", false)));
            items.add(UItem.asShadow("Форумы будут иметь ту же форму, что и чаты."));

            // — Список чатов —
            items.add(UItem.asHeader("Список чатов"));
            items.add(UItem.asCustom(getChatListPreview()));
            items.add(UItem.asCheck(ID_FORCE_SNOW, "Принудительный снег")
                    .setChecked(DevGramConfig.forceSnow));
            // «Скрыть статус» — только если у пользователя есть эмодзи-статус/премиум
            if (chatListPreview != null && chatListPreview.userHasStatus()) {
                items.add(UItem.asCheck(ID_HIDE_STATUS, "Скрыть статус")
                        .setChecked(gPref("dg_hideStatus", false)));
            }
            items.add(UItem.asCheck(ID_CENTER_TITLE, "Заголовок по центру")
                    .setChecked(DevGramConfig.centerTitle));
            items.add(UItem.asCheck(ID_HIDE_STORIES, "Скрыть истории")
                    .setChecked(gPref("dg_hideStories", false)));
            items.add(UItem.asCheck(ID_HIDE_FAB, "Скрыть плавающую кнопку")
                    .setChecked(gPref("dg_hideFab", false)));
            items.add(UItem.asCheck(ID_HIDE_SEARCHBAR, "Скрыть строку поиска")
                    .setChecked(gPref("dg_hideSearchBar", false)));
            items.add(UItem.asCheck(ID_MINI_AVATARS, "Мини-аватарки отправителей")
                    .setChecked(!gPref("disableThumbsInDialogList", false)));
            items.add(UItem.asButton(ID_ACTIONBAR_TITLE, "Текст в заголовке",
                    ACTIONBAR_TITLE_OPTIONS[gInt("dg_actionBarTitle", 0)]));
            items.add(UItem.asShadow("Падающий снег появится в верхней панели."));

            // — Папки с чатами —
            items.add(UItem.asHeader("Папки с чатами"));
            items.add(UItem.asCustom(getFoldersPreview()));
            items.add(UItem.asSlideView(
                    new String[]{"Текст", "Значок + текст", "Значок"},
                    DevGramConfig.getFolderTabsStyle(),
                    index -> {
                        DevGramConfig.setFolderTabsStyle(index);
                        if (foldersPreview != null) foldersPreview.invalidate();
                    }).setId(ID_FOLDER_TABS_STYLE));
            items.add(UItem.asCheck(ID_TAB_COUNTER, "Счётчик уведомлений")
                    .setChecked(gPref("dg_tabCounter", true)));
            items.add(UItem.asCheck(ID_HIDE_ALL_CHATS, "Скрыть вкладку «Все чаты»")
                    .setChecked(gPref("hideAllChatsTab", false)));
            items.add(UItem.asShadow("Иконки папок синхронизируются с вашим аккаунтом."));

            // — Внешний вид —
            items.add(UItem.asHeader("Внешний вид"));
            items.add(UItem.asButton(ID_PILL_STACK, "Pill Stack", "Интерактивные виджеты в поле поиска"));
            items.add(UItem.asCustom(getFabPreview()));
            items.add(UItem.asCheck(ID_SYSTEM_FONTS, "Системные шрифты")
                    .setChecked(gPref("dg_systemFonts", true)));
            items.add(UItem.asCheck(ID_SYSTEM_EMOJI, "Системные эмодзи")
                    .setChecked(DevGramConfig.isUseSystemEmoji()));
            items.add(UItem.asCheck(ID_MD3, "Material Design 3  " + md3Count() + "/5")
                    .setChecked(gPref("dg_md3", true)));
            if (gPref("dg_md3", true)) {
                items.add(UItem.asRoundCheckbox(ID_MD3_PROGRESS, "Индикаторы загрузки")
                        .setChecked(gPref("dg_md3_progress", true)));
                items.add(UItem.asRoundCheckbox(ID_MD3_SLIDER, "Стиль слайдера")
                        .setChecked(gPref("dg_md3_slider", true)));
                items.add(UItem.asRoundCheckbox(ID_MD3_SWITCH, "Стиль переключателей")
                        .setChecked(gPref("dg_md3_switch", true)));
                items.add(UItem.asRoundCheckbox(ID_MD3_TITLE, "Заголовок чата")
                        .setChecked(gPref("dg_md3_title", false)));
                items.add(UItem.asRoundCheckbox(ID_MD3_BOTTOMNAV, "Нижняя панель навигации")
                        .setChecked(gPref("dg_md3_bottomnav", false)));
            }
            items.add(UItem.asCheck(ID_GOOEY, "«Липкая» анимация аватарок")
                    .setChecked(gPref("dg_gooey", true)));
            items.add(UItem.asHeader("Интерфейс DevGram"));
            items.add(UItem.asCheck(ID_GHOST_STATUS, "Показывать значок режима призрака")
                    .setChecked(DevGramConfig.displayGhostStatus));
            // DevGram: в режиме дизайна iOS «Контакты» всегда показаны в нижнем меню
            // (как в Settings.app/Telegram-iOS) — тумблер скрытия не показываем.
            if (!org.telegram.ui.Components.DevGramMaterial3.iosNavigation()) {
                items.add(UItem.asCheck(ID_SHOW_CONTACTS, "Показывать «Контакты» в нижнем меню")
                        .setChecked(getUserConfig().showContactsTab));
            }
            items.add(UItem.asCheck(ID_HIDE_EMOJI_CATEGORIES, "Скрыть категории в поиске эмодзи")
                    .setChecked(DevGramConfig.hideEmojiCategories));
            items.add(UItem.asShadow("Настройки навигации, профилей и панели выбора эмодзи."));

            // — Секции —
            items.add(UItem.asHeader("Секции"));
            items.add(UItem.asIntSlideView(1, 0, gInt("dg_sectionRadius", 20), 28,
                    val -> val <= 0 ? "Откл." : (val >= 28 ? "Макс" : val + " dp"),
                    val -> org.telegram.messenger.MessagesController.getGlobalMainSettings()
                            .edit().putInt("dg_sectionRadius", val).apply()).setId(ID_STICKER_SIZE + 100));
            items.add(UItem.asCheck(ID_SEPARATE_HEADERS, "Отделить заголовки")
                    .setChecked(gPref("dg_separateHeaders", true))
                    .setEnabled(gInt("dg_dividerStyle", 1) != 2));
            items.add(UItem.asButton(ID_DIVIDER_STYLE, "Тип разделителей",
                    DIVIDER_STYLE_OPTIONS[gInt("dg_dividerStyle", 1)]));
            items.add(UItem.asShadow(null));

            // — Настройки размытия — (порядок как у exteraGram: обводка → меню → размытие)
            items.add(UItem.asHeader("Настройки размытия"));
            items.add(UItem.asButton(ID_GLASS_OUTLINE, "Обводка стекла",
                    GLASS_OUTLINE_OPTIONS[gInt("dg_glassOutline", 0)]));
            items.add(UItem.asCheck(ID_GLASS_MENU, "Стеклянное меню сообщения")
                    .setChecked(DevGramConfig.glassMenu));
            items.add(UItem.asCheck(ID_CHAT_BLUR, "Принудительное размытие")
                    .setChecked(org.telegram.messenger.LiteMode.isEnabled(org.telegram.messenger.LiteMode.FLAG_CHAT_BLUR)));
            items.add(UItem.asShadow("Меню сообщения и панель реакций становятся матовым стеклом. "
                    + "Стеклу нужно размытие — включите «Принудительное размытие»."));

            // — Маскировка — подмена иконки и названия приложения в списке приложений.
            items.add(UItem.asHeader("Маскировка"));
            items.add(UItem.asButton(ID_DISGUISE, "Иконка и название", disguiseCurrentTitle()));
            items.add(UItem.asShadow("В списке приложений DevGram будет выглядеть как выбранное приложение "
                    + "(например, «Калькулятор»). Само приложение не меняется."));

            // — Скрытые чаты — прячем выбранные чаты за пасскодом/биометрией.
            items.add(UItem.asHeader(org.telegram.messenger.LocaleController.getString(R.string.DevGramLockedChats)));
            items.add(UItem.asButton(ID_LOCKED_CHATS,
                    org.telegram.messenger.LocaleController.getString(R.string.DevGramLockedChats),
                    lockedChatsSubtitle()));
            items.add(UItem.asShadow(org.telegram.messenger.LocaleController.getString(R.string.DevGramLockedChatsInfo)));
        } else if (category == CATEGORY_CHATS) {
            // Порядок и состав повторяют ChatsPreferencesActivity ExteraGram.
            items.add(UItem.asHeader("Стикеры"));
            items.add(UItem.asCustom(getStickerSizePreview()));
            items.add(UItem.asIntSlideView(1, 2, (int) DevGramConfig.getStickerSize(), 14,
                    val -> String.valueOf(val),
                    val -> {
                        DevGramConfig.setStickerSize(val);
                        if (stickerSizePreview != null) stickerSizePreview.reloadStickerSize();
                    }).setId(ID_STICKER_SIZE));
            items.add(UItem.asCheck(ID_HIDE_STICKER_TIME, "Скрыть время на стикерах")
                    .setChecked(DevGramConfig.hideStickerTime));
            items.add(UItem.asCheck(ID_FULL_RECENT_STICKERS, "Не ограничивать недавние стикеры")
                    .setChecked(gPref("fullRecentStickers", true)));
            items.add(UItem.asCheck(ID_SHOW_ARCHIVED_STICKERS, "Показывать архивные стикеры")
                    .setChecked(gPref("showArchivedStickers", false)));
            items.add(UItem.asCheck(ID_REPLY_ELEMENTS, "Оформление ответов  "
                    + ((gPref("dg_replyColors", true) ? 1 : 0)
                    + (gPref("dg_replyEmoji", true) ? 1 : 0)
                    + (gPref("dg_replyBackground", true) ? 1 : 0)) + "/3")
                    .setChecked(gPref("dg_replyElements", true)));
            if (gPref("dg_replyElements", true)) {
                items.add(UItem.asRoundCheckbox(ID_REPLY_COLORS, "Цвета отправителей")
                        .setChecked(gPref("dg_replyColors", true)));
                items.add(UItem.asRoundCheckbox(ID_REPLY_EMOJI, "Фоновый эмодзи")
                        .setChecked(gPref("dg_replyEmoji", true)));
                items.add(UItem.asRoundCheckbox(ID_REPLY_BACKGROUND, "Цветной фон ответа")
                        .setChecked(gPref("dg_replyBackground", true)));
            }
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("Форма стикеров"));
            items.add(UItem.asCustom(getStickerShapePreview()));
            items.add(UItem.asShadow(null));

            // Кнопки-переходы двухстрочным стилем (как asButtonWithSubtext AI/CHAT_SETTINGS у exteraGram):
            // «AI Chat» — все функции ИИ вынесены на отдельный экран; «Настройки чатов» — стоковый экран Telegram.
            items.add(UItem.asButtonWithSubtext(ID_OPEN_AI, R.drawable.msg2_ask_question, "AI Chat",
                    "Сервисы, роли, история и генерация"));
            items.add(UItem.asButtonWithSubtext(ID_OPEN_CHAT_SETTINGS, R.drawable.msg_discussion, "Настройки чатов",
                    "Размер текста, обои и темы"));
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("Стикеры и реакции"));
            items.add(UItem.asCheck(ID_DISABLE_QUICK_REACTION, "Отключить быструю реакцию")
                    .setChecked(gPref("disableQuickReaction", false)));
            items.add(UItem.asCheck(ID_HIDE_SAVED_TAGS, "Скрыть теги в Избранном")
                    .setChecked(gPref("hideSavedMessagesTags", false)));
            items.add(UItem.asCheck(ID_HIDE_REACTIONS, "Скрывать реакции  "
                    + ((gPref("dg_hideReactionsChannels", false) ? 1 : 0)
                    + (gPref("dg_hideReactionsGroups", false) ? 1 : 0)
                    + (gPref("dg_hideReactionsPrivate", false) ? 1 : 0)) + "/3")
                    .setChecked(gPref("dg_hideReactions", false)));
            if (gPref("dg_hideReactions", false)) {
                items.add(UItem.asRoundCheckbox(ID_HIDE_REACTIONS_CHANNELS, "В каналах")
                        .setChecked(gPref("dg_hideReactionsChannels", false)));
                items.add(UItem.asRoundCheckbox(ID_HIDE_REACTIONS_GROUPS, "В группах")
                        .setChecked(gPref("dg_hideReactionsGroups", false)));
                items.add(UItem.asRoundCheckbox(ID_HIDE_REACTIONS_PRIVATE, "В личных чатах")
                        .setChecked(gPref("dg_hideReactionsPrivate", false)));
            }
            items.add(UItem.asShadow("Настройка скрывает реакции только в выбранных типах чатов."));

            items.add(UItem.asHeader("Двойное нажатие"));
            items.add(UItem.asCustom(getDoubleTapPreview()));
            items.add(UItem.asButton(ID_DOUBLE_TAP_IN, "Входящие сообщения",
                    org.telegram.messenger.DevGramDoubleTapUtils.currentLabel(false)));
            items.add(UItem.asButton(ID_DOUBLE_TAP_OUT, "Исходящие сообщения",
                    org.telegram.messenger.DevGramDoubleTapUtils.currentLabel(true)));
            items.add(UItem.asButton(ID_DOUBLE_TAP_REACTION, "Реакция по двойному нажатию",
                    doubleTapReactionLabel()));
            items.add(UItem.asShadow("Действие по двойному нажатию — отдельно для входящих и исходящих. Для действия «Реакции» выбери саму реакцию выше."));

            items.add(UItem.asHeader("Чаты"));
            items.add(UItem.asCheck(ID_HIDE_SEND_AS, "Скрыть кнопку «Отправить от имени»")
                    .setChecked(gPref("hideSendAs", false)));
            items.add(UItem.asCheck(ID_HIDE_KEYBOARD_ON_SCROLL, "Скрывать клавиатуру при прокрутке")
                    .setChecked(DevGramConfig.hideKeyboardOnScroll));
            items.add(UItem.asCheck(ID_DISABLE_GREETING, "Скрыть приветственный стикер")
                    .setChecked(DevGramConfig.disableGreetingSticker));
            items.add(UItem.asCheck(ID_COMMA_AFTER_MENTION, "Запятая после упоминания")
                    .setChecked(DevGramConfig.addCommaAfterMention));
            items.add(UItem.asCheck(ID_DISABLE_MARKDOWN, "Отключить Markdown")
                    .setChecked(DevGramConfig.disableMarkdown));
            items.add(UItem.asCheck(ID_INLINE_CALC, "Калькулятор в поле ввода")
                    .setChecked(DevGramConfig.inlineCalc));
            items.add(UItem.asCheck(ID_REPLACE_FORWARD, "Заменять пересылку")
                    .setChecked(gPref("replaceForward", true)));
            items.add(UItem.asCheck(ID_MENTION_BY_NAME, "Упоминать по имени")
                    .setChecked(gPref("mentionByName", false)));
            items.add(UItem.asCheck(ID_DISABLE_LINK_PREVIEW, "Не создавать предпросмотр ссылок")
                    .setChecked(gPref("disableLinkPreviewByDefault", false)));
            items.add(UItem.asCheck(ID_QUICK_TRANSITIONS, "Быстрые переходы  "
                    + ((gPref("dg_quickTransitionsChannels", true) ? 1 : 0)
                    + (gPref("dg_quickTransitionsTopics", true) ? 1 : 0)) + "/2")
                    .setChecked(gPref("dg_quickTransitions", true)));
            if (gPref("dg_quickTransitions", true)) {
                items.add(UItem.asRoundCheckbox(ID_QUICK_TRANSITIONS_CHANNELS, "Между каналами")
                        .setChecked(gPref("dg_quickTransitionsChannels", true)));
                items.add(UItem.asRoundCheckbox(ID_QUICK_TRANSITIONS_TOPICS, "Между темами")
                        .setChecked(gPref("dg_quickTransitionsTopics", true)));
            }
            items.add(UItem.asShadow("Переход свайпом можно отдельно отключить для каналов и тем."));

            items.add(UItem.asHeader("Сообщения"));
            items.add(UItem.asCustom(getMessagesPreview()));
            items.add(UItem.asCheck(ID_REMOVE_MESSAGE_TAIL, "Убрать хвост сообщения")
                    .setChecked(DevGramConfig.removeMessageTail));
            items.add(UItem.asCheck(ID_REPLACE_EDITED, "Заменить «изменено» значком")
                    .setChecked(DevGramConfig.replaceEditedWithIcon));
            items.add(UItem.asCheck(ID_SHOW_ONLINE_STATUS, "Показывать онлайн на аватарах")
                    .setChecked(gPref("dg_showOnlineStatus", false)));
            items.add(UItem.asCheck(ID_HIDE_SHARE_BUTTON, "Скрыть кнопку «Поделиться»")
                    .setChecked(DevGramConfig.hideShareButton));
            items.add(UItem.asCheck(ID_SHOW_POLL_RESULTS, "Показывать результаты опроса до голоса")
                    .setChecked(gPref("dg_showPollResults", false)));
            // Мастер-группа «Меню сообщения» (как у exteraGram): галочки прячут пункты контекстного меню
            items.add(UItem.asCheck(ID_MSGMENU, "Меню сообщения  " + msgMenuCount() + "/8")
                    .setChecked(gPref("dg_msgmenu", true)));
            if (gPref("dg_msgmenu", true)) {
                items.add(UItem.asRoundCheckbox(ID_MSGMENU_COPYPHOTO, "Копировать фото")
                        .setChecked(gPref("dg_msgmenu_copyphoto", true)));
                items.add(UItem.asRoundCheckbox(ID_MSGMENU_SAVE, "Сохранить")
                        .setChecked(gPref("dg_msgmenu_save", true)));
                items.add(UItem.asRoundCheckbox(ID_MSGMENU_REPEAT, "Повторить")
                        .setChecked(gPref("dg_msgmenu_repeat", true)));
                items.add(UItem.asRoundCheckbox(ID_MSGMENU_CLEAR, "Очистить")
                        .setChecked(gPref("dg_msgmenu_clear", true)));
                items.add(UItem.asRoundCheckbox(ID_MSGMENU_HISTORY, "История сообщений")
                        .setChecked(gPref("dg_msgmenu_history", true)));
                items.add(UItem.asRoundCheckbox(ID_MSGMENU_REPORT, "Пожаловаться")
                        .setChecked(gPref("dg_msgmenu_report", true)));
                items.add(UItem.asRoundCheckbox(ID_MSGMENU_GENERATE, "Сгенерировать ответ")
                        .setChecked(gPref("dg_msgmenu_generate", true)));
                items.add(UItem.asRoundCheckbox(ID_MSGMENU_DETAILS, "Детали")
                        .setChecked(gPref("dg_msgmenu_details", true)));
            }
            items.add(UItem.asCheck(ID_GROUP_MESSAGE_MENU, "Группировать меню сообщения")
                    .setChecked(gPref("dg_groupMessageMenu", true)));
            items.add(UItem.asShadow("Изменения сразу отображаются на превью выше."));

            items.add(UItem.asHeader("Распознавание голоса"));
            items.add(UItem.asButton(ID_RECOGNITION_LANG, "Язык распознавания", recognitionLangLabel()));
            items.add(UItem.asCheck(ID_RECOGNITION_AI, "Обрабатывать результат ИИ")
                    .setChecked(DevGramConfig.isRecognitionAiPostProcessing()));
            items.add(UItem.asShadow("Офлайн-распознавание голосовых через Vosk — прямо на телефоне, без интернета "
                    + "(нужно скачать модель языка ~40–50 МБ). Работает по кнопке транскрипции у голосовых."));

            items.add(UItem.asHeader("Голосовые сообщения"));
            items.add(UItem.asCheck(ID_DISABLE_AUTOPLAY_VOICE, "Не включать следующее голосовое автоматически")
                    .setChecked(gPref("disableAutoplayNextVoice", false)));
            items.add(UItem.asShadow(null));

            // Разбивка как у exteraGram: Камера / Фото / Видео.
            items.add(UItem.asHeader("Камера"));
            items.add(UItem.asButton(ID_CAMERA_TYPE, "Тип камеры", cameraTypeLabel()));
            items.add(UItem.asButton(ID_VIDEO_CAMERA, "Камера кружков", videoCameraLabel()));
            items.add(UItem.asCheck(ID_REMEMBER_LAST_CAMERA, "Запоминать последнюю камеру")
                    .setChecked(DevGramConfig.isRememberLastCamera()));
            items.add(UItem.asCheck(ID_ZOOM_SLIDER, "Слайдер зума")
                    .setChecked(DevGramConfig.isZoomSlider()));
            items.add(UItem.asCheck(ID_STATIC_ZOOM, "Статичный зум")
                    .setChecked(DevGramConfig.isStaticZoom()));
            items.add(UItem.asShadow("«Запоминать» — кружки открываются той камерой, которой снимали в прошлый раз. "
                    + "«Слайдер зума» — ползунок зума в камере кружков. «Статичный зум» — не сбрасывать зум после жеста."));

            items.add(UItem.asHeader("Фото"));
            items.add(UItem.asCheck(ID_ALWAYS_HD, "Всегда отправлять фото в HD")
                    .setChecked(org.telegram.messenger.SharedConfig.photoHighQualityDefault));
            items.add(UItem.asCheck(ID_HIDE_CAMERA_TILE, "Скрыть плитку камеры")
                    .setChecked(gPref("dg_hideCameraTile", false)));
            items.add(UItem.asCheck(ID_PHOTO_HAS_STICKER, "Помечать фото со стикерами")
                    .setChecked(gPref("photoHasSticker", true)));
            items.add(UItem.asCheck(ID_DISABLE_FLIP_PHOTOS, "Не отражать фотографии")
                    .setChecked(gPref("disableFlipPhotos", false)));
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("Вложения"));
            items.add(UItem.asCheck(ID_DISABLE_RECENT_FILES, "Скрыть недавние файлы во вложениях")
                    .setChecked(gPref("disableRecentFilesAttachment", false)));
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("Видео"));
            items.add(UItem.asCheck(ID_PREFER_ORIGINAL_QUALITY, "Предпочитать исходное качество видео")
                    .setChecked(gPref("dg_preferOriginalQuality", false)));
            items.add(UItem.asButton(ID_DOUBLE_TAP_SEEK, "Перемотка двойным нажатием",
                    DOUBLE_TAP_SEEK_OPTIONS[Math.max(0, Math.min(3, gInt("dg_doubleTapSeek", 1)))]));
            items.add(UItem.asCheck(ID_SWIPE_TO_PIP, "Смахивание в картинку-в-картинке")
                    .setChecked(gPref("dg_swipeToPip", true)));
            items.add(UItem.asCheck(ID_DISABLE_VOLUME_AUTOPLAY, "Включать звук кнопками громкости")
                    .setChecked(!gPref("disablePlayVisibleVideoOnVolume", false)));
            // Мастер-группа «Пауза при сворачивании» (как у exteraGram): тумблер + галочки типов
            items.add(UItem.asCheck(ID_PAUSE_MINIMIZE, "Пауза при сворачивании  " + pauseMinCount() + "/3")
                    .setChecked(gPref("dg_pauseOnMinimize", false)));
            if (gPref("dg_pauseOnMinimize", false)) {
                items.add(UItem.asRoundCheckbox(ID_PAUSE_VIDEO_MINIMIZE, "Видео")
                        .setChecked(gPref("dg_pauseVideoOnMinimize", false)));
                items.add(UItem.asRoundCheckbox(ID_PAUSE_VOICE_MINIMIZE, "Голосовые")
                        .setChecked(gPref("dg_pauseVoiceOnMinimize", false)));
                items.add(UItem.asRoundCheckbox(ID_PAUSE_ROUND_MINIMIZE, "Кружки")
                        .setChecked(gPref("dg_pauseRoundOnMinimize", false)));
            }
            items.add(UItem.asShadow("Проигрываемое медиа встаёт на паузу, когда сворачиваешь приложение."));
        } else if (category == CATEGORY_AI) {
            // Экран «AI Chat» — повтор AiPreferencesActivity exteraGram.
            items.add(UItem.asTopView("AI Chat", "Генерируйте текст по сообщениям или перед отправкой.",
                    "RestrictedEmoji", "🤖"));
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("Основные"));
            items.add(UItem.asButton(ID_AI_SERVICES, R.drawable.msg_language, "Сервисы", aiServicesLabel()));
            items.add(UItem.asButton(ID_AI_ROLES, R.drawable.msg_openprofile, "Роли", aiRoleLabel()));
            items.add(UItem.asCheck(ID_AI_HISTORY, "История сообщений")
                    .setChecked(gPref("dg_aiSaveHistory", true)));
            if (gPref("dg_aiSaveHistory", true)) {
                items.add(UItem.asButton(ID_AI_CLEAR_HISTORY, R.drawable.msg_delete, "Очистить историю").red());
            }
            items.add(UItem.asShadow("История диалога позволяет ИИ понимать предыдущие запросы и "
                    + "учитывать их при генерации новых ответов."));

            items.add(UItem.asHeader("Генерация"));
            UItem streaming = UItem.asCheck(ID_AI_STREAMING, "Потоковая передача ответа")
                    .setChecked(gPref("dg_aiStreaming", true)).setMultiline(true);
            streaming.subtext = "Обеспечивает более плавное и быстрое отображение ответов.";
            items.add(streaming);
            items.add(UItem.asCheck(ID_AI_RESP_ONLY, "Показывать только ответ")
                    .setChecked(gPref("dg_aiShowResponseOnly", false)));
            items.add(UItem.asCheck(ID_AI_QUOTE, "Вставлять ответ как цитату")
                    .setChecked(gPref("dg_aiInsertAsQuote", true)));
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("Температура"));
            items.add(UItem.asIntSlideView(1, 0, aiTempInit(), 20,
                    val -> String.format(java.util.Locale.US, "%.1f", val / 10f),
                    val -> org.telegram.messenger.MessagesController.getGlobalMainSettings()
                            .edit().putFloat("dg_aiTemperature", val / 10f).apply()).setId(ID_AI_TEMPERATURE));
            items.add(UItem.asShadow("Температура управляет случайностью ответа: чем выше значение, "
                    + "тем креативнее ответ; чем ниже — тем точнее."));
        } else {
            long uid = getUserConfig().getClientUserId();
            boolean hasArrow = org.telegram.messenger.DevGramBadges.hasSupporterFeatures(uid);
            if (hasArrow) {
                items.add(UItem.asHeader("Прокси DevGram"));
                items.add(UItem.asCheck(ID_VPN, "Прокси")
                        .setChecked(org.telegram.messenger.DevGramProxy.isEnabled()));
                items.add(UItem.asShadow("Быстрое включение защищённого подключения DevGram."));
            }

            items.add(UItem.asHeader("Перевод сообщений"));
            items.add(UItem.asCheck(ID_TRANSLATE_BUTTON, "Показывать кнопку «Перевести»")
                    .setChecked(getMessagesController().getTranslateController().isContextTranslateEnabled()));
            items.add(UItem.asCheck(ID_TRANSLATE_CHAT, "Показывать перевод всего чата")
                    .setChecked(getMessagesController().getTranslateController().isChatTranslateEnabled()));
            int provider = DevGramGeneralConfig.getTranslationProvider();
            items.add(UItem.asButton(ID_TRANSLATION_PROVIDER, "Сервис перевода", TRANSLATION_PROVIDERS[provider]));
            if (provider == 3) {
                items.add(UItem.asButton(ID_TRANSLATION_FORMALITY, "Стиль перевода",
                        TRANSLATION_FORMALITIES[DevGramGeneralConfig.getTranslationFormality()]));
            }
            items.add(UItem.asButton(ID_TRANSLATION_TARGET, "Язык перевода", translationTargetLabel()));
            items.add(UItem.asButton(ID_DO_NOT_TRANSLATE, "Не переводить", doNotTranslateLabel()));
            items.add(UItem.asShadow("Настройки применяются к переводу отдельных сообщений и целых чатов."));

            items.add(UItem.asHeader("Основные"));
            items.add(UItem.asButtonCheck(ID_NUMBER_ROUNDING, "Отключить округление чисел", "1,23K → 1 234")
                    .setChecked(DevGramConfig.disableNumberRounding));
            items.add(UItem.asButtonCheck(ID_TIME_WITH_SECONDS, "Время с секундами", "12:34 → 12:34:56")
                    .setChecked(DevGramConfig.isFormatWithSeconds()));
            items.add(UItem.asCheck(ID_INAPP_VIBRATION, "Вибрация внутри приложения")
                    .setChecked(DevGramGeneralConfig.isInAppVibration()));
            items.add(UItem.asCheck(ID_FILTER_ZALGO, "Фильтровать Zalgo-текст")
                    .setChecked(DevGramGeneralConfig.isFilterZalgo()));
            items.add(UItem.asShadow("Избыточные комбинируемые символы будут скрыты, чтобы текст не ломал интерфейс."));

            if ((getMessagesController().availableMapProviders & 4) != 0) {
                items.add(UItem.asHeader("Карты"));
                items.add(UItem.asCheck(ID_YANDEX_MAPS, "Использовать Яндекс Карты")
                        .setChecked(DevGramGeneralConfig.isUseYandexMaps()));
                items.add(UItem.asShadow("Для статических карт и предпросмотра геопозиций будет использоваться Яндекс."));
            }

            items.add(UItem.asHeader("Ускорение загрузки"));
            items.add(UItem.asSlideView(new String[]{"Выкл.", "Быстро", "Ультра"},
                    DevGramGeneralConfig.getDownloadSpeedBoost(), DevGramGeneralConfig::setDownloadSpeedBoost)
                    .setId(ID_DOWNLOAD_BOOST));
            items.add(UItem.asCheck(ID_UPLOAD_BOOST, "Ускорять отправку файлов")
                    .setChecked(DevGramGeneralConfig.isUploadSpeedBoost()));
            items.add(UItem.asShadow("Увеличивает размер частей и число параллельных сетевых запросов."));

            items.add(UItem.asHeader("Хранилище"));
            items.add(UItem.asButton(ID_CUSTOM_SAVE_PATH, "Папка сохранения", customSavePathLabel()));
            items.add(UItem.asShadow("Подпапка внутри Изображений, Видео, Загрузок и Музыки. Пустое значение сохраняет прямо в системные папки."));

            items.add(UItem.asHeader("Профиль"));
            int fiveMinutesAgo = org.telegram.tgnet.ConnectionsManager.getInstance(currentAccount).getCurrentTime() - 300;
            items.add(UItem.asButtonCheck(ID_RELATIVE_LAST_SEEN, "Относительное время последнего посещения",
                    org.telegram.messenger.LocaleController.formatDateOnline(fiveMinutesAgo, null))
                    .setChecked(DevGramGeneralConfig.isRelativeLastSeen()));
            items.add(UItem.asCheck(ID_HIDE_PHONE, "Скрывать мой номер телефона")
                    .setChecked(DevGramGeneralConfig.isHidePhoneNumber()));
            items.add(UItem.asButton(ID_SHOW_ID_DC, "Показывать ID и датацентр",
                    ID_DC_OPTIONS[DevGramGeneralConfig.getShowIdAndDc()]));
            items.add(UItem.asShadow("ID отображается в профиле; датацентр определяется по фотографии профиля."));

            items.add(UItem.asHeader("Архивированные чаты"));
            items.add(UItem.asCheck(ID_HIDE_ARCHIVE, "Скрыть папку «Архив»")
                    .setChecked(DevGramGeneralConfig.isHideArchiveFolder()));
            if (!DevGramGeneralConfig.isHideArchiveFolder()) {
                items.add(UItem.asCheck(ID_ARCHIVE_ON_PULL, "Открывать архив свайпом вниз")
                        .setChecked(DevGramGeneralConfig.isArchiveOnPull()));
            }
            items.add(UItem.asCheck(ID_DISABLE_UNARCHIVE_SWIPE, "Отключить разархивацию свайпом")
                    .setChecked(DevGramGeneralConfig.isDisableUnarchiveSwipe()));
            items.add(UItem.asShadow("Защищает чаты в архиве от случайного возврата в основной список."));

            items.add(UItem.asHeader("DevGram"));
            items.add(UItem.asCheck(ID_DISABLE_ADS, "Скрывать рекламу")
                    .setChecked(DevGramConfig.disableAds));
            items.add(UItem.asCheck(ID_LOCAL_PREMIUM, "Локальный премиум")
                    .setChecked(DevGramConfig.localPremium));
            items.add(UItem.asCheck(ID_STREAKS, "Огоньки (серии)")
                    .setChecked(DevGramConfig.streaksEnabled));
            items.add(UItem.asShadow("🔥N рядом с именем — сколько дней подряд вы общаетесь в личке "
                    + "(показывается от 3 дней). Выключишь — огоньки пропадут у всех."));
        }
    }

    // ===== Пометка удалённых: кастомизация значка и цвета (порт AyuGram) =====

    private static final String[] DELETED_ICON_NAMES = {"Нет", "Корзина", "Крест", "Перечёркнутый глаз"};

    private CharSequence[] markNames(String[] base, int current) {
        CharSequence[] out = new CharSequence[base.length];
        for (int i = 0; i < base.length; i++) out[i] = (i == current ? "✓ " : "") + base[i];
        return out;
    }

    private void showDeletedIconPicker() {
        if (getParentActivity() == null) return;
        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle("Значок удалённых");
        int current = org.telegram.messenger.DevGramDeletedMark.getIcon();
        CharSequence[] names = markNames(DELETED_ICON_NAMES, current);
        b.setItems(names, org.telegram.messenger.DevGramDeletedMark.ICONS, (dlg, which) -> {
            org.telegram.messenger.DevGramDeletedMark.setIcon(which);
            if (deletedPreviewCell != null) deletedPreviewCell.refresh();
            // DiffUtil сравнивает текст Spannable без его image span и считает разные
            // значки одинаковыми. Неанимированное обновление гарантирует немедленный bind,
            // а также сразу добавляет или убирает строку выбора цвета для варианта «Нет».
            refreshListImmediately();
        });
        b.setNegativeButton(org.telegram.messenger.LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    // DevGram: подпись под пунктом «Маскировка» — имя текущей маски или «Выключена».
    private String disguiseCurrentTitle() {
        if (!org.telegram.messenger.DevGramDisguise.isDisguised()) {
            return "Выключена";
        }
        return org.telegram.messenger.LocaleController.getString(org.telegram.messenger.DevGramDisguise.current().title);
    }

    // DevGram: подпись под пунктом «Скрытые чаты».
    private CharSequence lockedChatsSubtitle() {
        if (!org.telegram.messenger.DevGramLockedChats.hasPasscode()) {
            return "Пасскод не задан";
        }
        int n = org.telegram.messenger.DevGramLockedChats.count();
        return n == 0 ? "Нет скрытых чатов" : (n + (n % 10 == 1 && n % 100 != 11 ? " чат" : " чат."));
    }

    // DevGram: красивое меню-каталог маскировок (карточки с реальными иконками приложений).
    private void showDisguiseDialog() {
        if (getParentActivity() == null) return;
        DevGramDisguiseSheet sheet = new DevGramDisguiseSheet(getParentActivity(), () -> {
            refreshListImmediately();
            boolean disguised = org.telegram.messenger.DevGramDisguise.isDisguised();
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.done,
                            disguised
                                    ? "Приложение замаскировано под «" + disguiseCurrentTitle() + "»"
                                    : "Маскировка снята")
                    .show();
        });
        showDialog(sheet);
    }

    // DevGram: зажатие настройки → «копировать / поделиться ссылкой» на неё
    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item == null || item.id <= 0) return false;
        if (category == CATEGORY_GHOST) {
            org.telegram.messenger.DevGramGhostSettings.Settings s =
                    org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
            if (item.id == ID_GHOST_READ) s.readLocked = !s.readLocked;
            else if (item.id == ID_GHOST_STORIES) s.storiesLocked = !s.storiesLocked;
            else if (item.id == ID_GHOST_ONLINE) s.onlineLocked = !s.onlineLocked;
            else if (item.id == ID_GHOST_TYPING) s.typingLocked = !s.typingLocked;
            else if (item.id == ID_GHOST_OFFLINE) s.offlineLocked = !s.offlineLocked;
            else return DevGramSettingsLink.showLinkOptions(this, view, DevGramSettingsLink.codeForCategory(category), item.id);
            s.save();
            refreshListImmediately();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Блокировка настройки изменена").show();
            return true;
        }
        // только у настраиваемых строк (у header/shadow id == 0)
        return DevGramSettingsLink.showLinkOptions(this,
                view, DevGramSettingsLink.codeForCategory(category), item.id);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_GHOST_MASTER) {
            ghostModeExpanded = !ghostModeExpanded;
        } else if (item.id == ID_GHOST_READ) {
            org.telegram.messenger.DevGramGhostSettings.Settings s = org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
            s.sendReadMessages = !s.sendReadMessages; s.save();
        } else if (item.id == ID_GHOST_STORIES) {
            org.telegram.messenger.DevGramGhostSettings.Settings s = org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
            s.sendReadStories = !s.sendReadStories; s.save();
        } else if (item.id == ID_GHOST_ONLINE) {
            org.telegram.messenger.DevGramGhostSettings.Settings s = org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
            s.sendOnline = !s.sendOnline; s.save();
        } else if (item.id == ID_GHOST_TYPING) {
            org.telegram.messenger.DevGramGhostSettings.Settings s = org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
            s.sendTyping = !s.sendTyping; s.save();
        } else if (item.id == ID_GHOST_OFFLINE) {
            org.telegram.messenger.DevGramGhostSettings.Settings s = org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
            s.sendOfflineAfterOnline = !s.sendOfflineAfterOnline; s.save();
        } else if (item.id == ID_GHOST_MARK_READ) {
            org.telegram.messenger.DevGramGhostSettings.Settings s = org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
            s.markReadAfterAction = !s.markReadAfterAction;
            if (s.markReadAfterAction) s.useScheduledMessages = false;
            s.save();
        } else if (item.id == ID_GHOST_SCHEDULED) {
            org.telegram.messenger.DevGramGhostSettings.Settings s = org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
            s.useScheduledMessages = !s.useScheduledMessages;
            if (s.useScheduledMessages) s.markReadAfterAction = false;
            s.save();
        } else if (item.id == ID_GHOST_SUGGEST_STORY) {
            org.telegram.messenger.DevGramGhostSettings.Settings s = org.telegram.messenger.DevGramGhostSettings.get(currentAccount);
            s.suggestBeforeStory = !s.suggestBeforeStory; s.save();
        } else if (item.id == ID_GHOST_STATUS) {
            DevGramConfig.setDisplayGhostStatus(!DevGramConfig.displayGhostStatus);
        } else if (item.id == ID_GHOST_SOUND) {
            showGhostSoundChoice();
            return;
        } else if (item.id == ID_SAVE_DELETED) {
            DevGramConfig.setSaveDeletedMessages(!DevGramConfig.saveDeletedMessages);
        } else if (item.id == ID_SAVE_HISTORY) {
            DevGramConfig.setSaveMessagesHistory(!DevGramConfig.saveMessagesHistory);
        } else if (item.id == ID_SAVE_MEDIA) {
            // As in AyuGram: the switch enables saving, tapping the rest of the
            // two-line row opens chat categories and network limits.
            boolean switchTap = org.telegram.messenger.LocaleController.isRTL
                    ? x <= org.telegram.messenger.AndroidUtilities.dp(76)
                    : x >= view.getMeasuredWidth() - org.telegram.messenger.AndroidUtilities.dp(76);
            if (switchTap) {
                DevGramConfig.setSaveMedia(!DevGramConfig.saveMedia);
            } else if (DevGramConfig.saveMedia) {
                showMediaSavingBottomSheet();
                return;
            }
        } else if (item.id == ID_SAVE_BOTS) {
            DevGramConfig.setSaveInBotChats(!DevGramConfig.saveInBotChats);
        } else if (item.id == ID_SAVE_READ_DATE) {
            DevGramConfig.setSaveReadDate(!DevGramConfig.saveReadDate);
        } else if (item.id == ID_SAVE_LOCAL_ONLINE) {
            DevGramConfig.setSaveLocalOnline(!DevGramConfig.saveLocalOnline);
        } else if (item.id == ID_MEDIA_SAVE_PATH) {
            showSavedMediaFolderDialog(); return;
        } else if (item.id == ID_CLEAR_SPY_DATABASE) {
            confirmClearSpy(); return;
        } else if (item.id == ID_EXPORT_SPY_DATABASE) {
            org.telegram.messenger.AndroidUtilities.addToClipboard(
                    org.telegram.messenger.DevGramMessagesController.getInstance().exportData());
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Резервная копия скопирована").show();
            return;
        } else if (item.id == ID_IMPORT_SPY_DATABASE) {
            importSpyDatabase(); return;
        } else if (item.id == ID_DELETED_TRANSPARENT) {
            DevGramConfig.setSemiTransparentDeleted(!DevGramConfig.getSemiTransparentDeleted());
            if (deletedPreviewCell != null) deletedPreviewCell.refresh();
        } else if (item.id == ID_DELETED_ICON) {
            showDeletedIconPicker();
            return;
        } else if (item.id == ID_DISABLE_ADS) {
            DevGramConfig.setDisableAds(!DevGramConfig.disableAds);
        } else if (item.id == ID_LOCAL_PREMIUM) {
            DevGramConfig.setLocalPremium(!DevGramConfig.localPremium);
        } else if (item.id == ID_STREAKS) {
            DevGramConfig.setStreaksEnabled(!DevGramConfig.streaksEnabled);
        } else if (item.id == ID_VPN) {
            boolean enable = !org.telegram.messenger.DevGramProxy.isEnabled();
            org.telegram.messenger.DevGramProxy.setEnabled(enable);
            DevGramConfig.setVpnEnabled(enable);
        } else if (item.id == ID_VPN_DIAG) {
            runProxyDiagnostics();
            return;
        } else if (item.id == ID_SHOW_CONTACTS) {
            getUserConfig().setShowContactsTab(!getUserConfig().showContactsTab);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.contactsTabVisibleToggled);
        } else if (item.id == ID_TRANSLATE_BUTTON) {
            org.telegram.messenger.TranslateController controller = getMessagesController().getTranslateController();
            controller.setContextTranslateEnabled(!controller.isContextTranslateEnabled());
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateSearchSettings);
            rebuildAllScreens();
            return;
        } else if (item.id == ID_TRANSLATE_CHAT) {
            if (!getUserConfig().isPremium() && !getMessagesController().getTranslateController().isChatTranslateEnabled()) {
                showDialog(new org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet(this, 13, false));
                return;
            }
            org.telegram.messenger.TranslateController controller = getMessagesController().getTranslateController();
            controller.setChatTranslateEnabled(!controller.isChatTranslateEnabled());
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateSearchSettings);
            rebuildAllScreens();
            return;
        } else if (item.id == ID_TRANSLATION_PROVIDER) {
            showChoice("Сервис перевода", TRANSLATION_PROVIDERS, "dg_translationProvider", 0);
            return;
        } else if (item.id == ID_TRANSLATION_FORMALITY) {
            showChoice("Стиль перевода", TRANSLATION_FORMALITIES, "dg_translationFormality", 0);
            return;
        } else if (item.id == ID_TRANSLATION_TARGET) {
            showTranslationTargetPicker();
            return;
        } else if (item.id == ID_DO_NOT_TRANSLATE) {
            presentFragment(new org.telegram.ui.RestrictedLanguagesSelectActivity());
            return;
        } else if (item.id == ID_NUMBER_ROUNDING) {
            DevGramConfig.setDisableNumberRounding(!DevGramConfig.disableNumberRounding);
            rebuildAllScreens();
            return;
        } else if (item.id == ID_RELATIVE_LAST_SEEN) {
            DevGramGeneralConfig.setRelativeLastSeen(!DevGramGeneralConfig.isRelativeLastSeen());
            rebuildAllScreens();
            return;
        } else if (item.id == ID_INAPP_VIBRATION) {
            DevGramGeneralConfig.setInAppVibration(!DevGramGeneralConfig.isInAppVibration());
            rebuildAllScreens();
            return;
        } else if (item.id == ID_FILTER_ZALGO) {
            DevGramGeneralConfig.setFilterZalgo(!DevGramGeneralConfig.isFilterZalgo());
            rebuildAllScreens();
            return;
        } else if (item.id == ID_YANDEX_MAPS) {
            DevGramGeneralConfig.setUseYandexMaps(!DevGramGeneralConfig.isUseYandexMaps());
            for (int account = 0; account < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; account++) {
                org.telegram.messenger.MessagesController mc = org.telegram.messenger.MessagesController.getInstance(account);
                mc.mapProvider = DevGramGeneralConfig.isUseYandexMaps()
                        ? 1 : org.telegram.messenger.MessagesController.getMainSettings(account).getInt("mapProvider", 2);
            }
        } else if (item.id == ID_UPLOAD_BOOST) {
            DevGramGeneralConfig.setUploadSpeedBoost(!DevGramGeneralConfig.isUploadSpeedBoost());
        } else if (item.id == ID_CUSTOM_SAVE_PATH) {
            showCustomSavePathDialog();
            return;
        } else if (item.id == ID_HIDE_PHONE) {
            DevGramGeneralConfig.setHidePhoneNumber(!DevGramGeneralConfig.isHidePhoneNumber());
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
            rebuildAllScreens();
            return;
        } else if (item.id == ID_SHOW_ID_DC) {
            showChoice("Показывать ID и датацентр", ID_DC_OPTIONS, "dg_showIdAndDc", 1);
            return;
        } else if (item.id == ID_HIDE_ARCHIVE) {
            DevGramGeneralConfig.setHideArchiveFolder(!DevGramGeneralConfig.isHideArchiveFolder());
            getMessagesController().checkArchiveFolder();
        } else if (item.id == ID_ARCHIVE_ON_PULL) {
            DevGramGeneralConfig.setArchiveOnPull(!DevGramGeneralConfig.isArchiveOnPull());
        } else if (item.id == ID_DISABLE_UNARCHIVE_SWIPE) {
            DevGramGeneralConfig.setDisableUnarchiveSwipe(!DevGramGeneralConfig.isDisableUnarchiveSwipe());
        } else if (item.id == ID_SQUARE_FAB) {
            DevGramConfig.setSquareFab(!DevGramConfig.squareFab);
        } else if (item.id == ID_GLASS_MENU) {
            DevGramConfig.setGlassMenu(!DevGramConfig.glassMenu);
        } else if (item.id == ID_HIDE_EMOJI_CATEGORIES) {
            DevGramConfig.setHideEmojiCategories(!DevGramConfig.hideEmojiCategories);
        } else if (item.id == ID_SYSTEM_EMOJI) {
            DevGramConfig.setUseSystemEmoji(!DevGramConfig.isUseSystemEmoji());
        } else if (item.id == ID_FORCE_SNOW) {
            DevGramConfig.setForceSnow(!DevGramConfig.forceSnow);
        } else if (item.id == ID_DISGUISE) {
            showDisguiseDialog();
            return;
        } else if (item.id == ID_LOCKED_CHATS) {
            org.telegram.messenger.DevGramLockedChatsGate.prompt(getParentActivity(),
                    () -> presentFragment(new DevGramLockedChatsActivity()));
            return;
        } else if (item.id == ID_CHAT_BLUR) {
            org.telegram.messenger.SharedConfig.toggleChatBlur();
        } else if (item.id == ID_SINGLE_CORNER) {
            gToggle("dg_singleCorner", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_HIDE_STATUS) {
            gToggle("dg_hideStatus", false);
            if (chatListPreview != null) chatListPreview.invalidate();
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_HIDE_STORIES) {
            gToggle("dg_hideStories", false);
            getNotificationCenter().postNotificationName(NotificationCenter.storiesEnabledUpdate);
        } else if (item.id == ID_HIDE_FAB) {
            gToggle("dg_hideFab", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_HIDE_SEARCHBAR) {
            gToggle("dg_hideSearchBar", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_TAB_COUNTER) {
            gToggle("dg_tabCounter", true);
            if (foldersPreview != null) foldersPreview.invalidate();
        } else if (item.id == ID_SYSTEM_FONTS) {
            gToggle("dg_systemFonts", true);
            org.telegram.messenger.AndroidUtilities.invalidateDevgramSystemFonts();
            showRestartRequiredBulletin();
        } else if (item.id == ID_PILL_STACK) {
            presentFragment(new PillStackPreferencesActivity());
            return;
        } else if (item.id == ID_GOOEY) {
            gToggle("dg_gooey", true);
        } else if (item.id == ID_ACTIONBAR_TITLE) {
            showChoice("Текст в заголовке", ACTIONBAR_TITLE_OPTIONS, "dg_actionBarTitle", 0);
            return;
        } else if (item.id == ID_DIVIDER_STYLE) {
            showChoice("Тип разделителей", DIVIDER_STYLE_OPTIONS, "dg_dividerStyle", 1);
            return;
        } else if (item.id == ID_GLASS_OUTLINE) {
            showChoice("Обводка стекла", GLASS_OUTLINE_OPTIONS, "dg_glassOutline", 0);
            return;
        } else if (item.id == ID_SEPARATE_HEADERS) {
            if (gInt("dg_dividerStyle", 1) != 2) {
                gToggle("dg_separateHeaders", true);
            }
        } else if (item.id == ID_MD3) {
            gToggle("dg_md3", true);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_MD3_PROGRESS) {
            gToggle("dg_md3_progress", true);
        } else if (item.id == ID_MD3_SLIDER) {
            gToggle("dg_md3_slider", true);
        } else if (item.id == ID_MD3_SWITCH) {
            gToggle("dg_md3_switch", true);
        } else if (item.id == ID_MD3_TITLE) {
            gToggle("dg_md3_title", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_MD3_BOTTOMNAV) {
            gToggle("dg_md3_bottomnav", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_CENTER_TITLE) {
            DevGramConfig.setCenterTitle(!DevGramConfig.centerTitle);
            if (chatListPreview != null) chatListPreview.invalidate();
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_HIDE_ALL_CHATS) {
            gToggle("hideAllChatsTab", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_MINI_AVATARS) {
            gToggle("disableThumbsInDialogList", false);
        } else if (item.id == ID_HIDE_CONTACTS_DIALOGS) {
            gToggle("hideContactsInDialogs", false);
        } else if (item.id == ID_LAST_SEEN_DOTS) {
            gToggle("enableLastSeenDots", false);
        } else if (item.id == ID_DISABLE_MARKDOWN) {
            DevGramConfig.setDisableMarkdown(!DevGramConfig.disableMarkdown);
        } else if (item.id == ID_HIDE_KEYBOARD_ON_SCROLL) {
            DevGramConfig.setHideKeyboardOnScroll(!DevGramConfig.hideKeyboardOnScroll);
        } else if (item.id == ID_INLINE_CALC) {
            DevGramConfig.setInlineCalc(!DevGramConfig.inlineCalc);
        } else if (item.id == ID_DISABLE_GREETING) {
            DevGramConfig.setDisableGreetingSticker(!DevGramConfig.disableGreetingSticker);
        } else if (item.id == ID_COMMA_AFTER_MENTION) {
            DevGramConfig.setAddCommaAfterMention(!DevGramConfig.addCommaAfterMention);
        } else if (item.id == ID_TIME_WITH_SECONDS) {
            DevGramConfig.setFormatWithSeconds(!DevGramConfig.isFormatWithSeconds());
            org.telegram.messenger.LocaleController.getInstance().recreateFormatters();
            rebuildAllScreens();
            return;
        } else if (item.id == ID_REPLACE_FORWARD) {
            gToggle("replaceForward", true);
        } else if (item.id == ID_MENTION_BY_NAME) {
            gToggle("mentionByName", false);
        } else if (item.id == ID_HIDE_SEND_AS) {
            gToggle("hideSendAs", false);
        } else if (item.id == ID_DISABLE_LINK_PREVIEW) {
            gToggle("disableLinkPreviewByDefault", false);
        } else if (item.id == ID_DISABLE_NEXT_CHANNEL) {
            gToggle("disableSlideToNextChannel", false);
        } else if (item.id == ID_REPLY_ELEMENTS) {
            gToggle("dg_replyElements", true);
        } else if (item.id == ID_REPLY_COLORS) {
            gToggle("dg_replyColors", true);
            if (messagesPreview != null) messagesPreview.reloadMessages();
        } else if (item.id == ID_REPLY_EMOJI) {
            gToggle("dg_replyEmoji", true);
            if (messagesPreview != null) messagesPreview.reloadMessages();
        } else if (item.id == ID_REPLY_BACKGROUND) {
            gToggle("dg_replyBackground", true);
            if (messagesPreview != null) messagesPreview.reloadMessages();
        } else if (item.id == ID_HIDE_REACTIONS) {
            gToggle("dg_hideReactions", false);
        } else if (item.id == ID_HIDE_REACTIONS_CHANNELS) {
            gToggle("dg_hideReactionsChannels", false);
        } else if (item.id == ID_HIDE_REACTIONS_GROUPS) {
            gToggle("dg_hideReactionsGroups", false);
        } else if (item.id == ID_HIDE_REACTIONS_PRIVATE) {
            gToggle("dg_hideReactionsPrivate", false);
        } else if (item.id == ID_QUICK_TRANSITIONS) {
            gToggle("dg_quickTransitions", true);
        } else if (item.id == ID_QUICK_TRANSITIONS_CHANNELS) {
            gToggle("dg_quickTransitionsChannels", true);
        } else if (item.id == ID_QUICK_TRANSITIONS_TOPICS) {
            gToggle("dg_quickTransitionsTopics", true);
        } else if (item.id == ID_DISABLE_QUICK_REACTION) {
            gToggle("disableQuickReaction", false);
        } else if (item.id == ID_HIDE_MESSAGE_REACTIONS) {
            gToggle("hideMessageReactions", false);
        } else if (item.id == ID_HIDE_SAVED_TAGS) {
            gToggle("hideSavedMessagesTags", false);
        } else if (item.id == ID_HIDE_STICKER_TIME) {
            DevGramConfig.setHideStickerTime(!DevGramConfig.hideStickerTime);
            if (stickerSizePreview != null) stickerSizePreview.reloadStickerSize();
        } else if (item.id == ID_FULL_RECENT_STICKERS) {
            gToggle("fullRecentStickers", true);
        } else if (item.id == ID_SHOW_ARCHIVED_STICKERS) {
            gToggle("showArchivedStickers", false);
            if (gPref("showArchivedStickers", false)) {
                org.telegram.messenger.MediaDataController.getInstance(currentAccount).loadArchivedStickerSets();
            }
        } else if (item.id == ID_ALWAYS_HD) {
            org.telegram.messenger.SharedConfig.photoHighQualityDefault =
                    !org.telegram.messenger.SharedConfig.photoHighQualityDefault;
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit()
                    .putBoolean("photoHighQualityDefault",
                            org.telegram.messenger.SharedConfig.photoHighQualityDefault).apply();
        } else if (item.id == ID_REMOVE_MESSAGE_TAIL) {
            DevGramConfig.setRemoveMessageTail(!DevGramConfig.removeMessageTail);
            if (messagesPreview != null) messagesPreview.reloadMessages();
        } else if (item.id == ID_REPLACE_EDITED) {
            DevGramConfig.setReplaceEditedWithIcon(!DevGramConfig.replaceEditedWithIcon);
            if (messagesPreview != null) messagesPreview.reloadMessages();
        } else if (item.id == ID_MSGMENU) {
            gToggle("dg_msgmenu", true);
        } else if (item.id == ID_MSGMENU_COPYPHOTO) {
            gToggle("dg_msgmenu_copyphoto", true);
        } else if (item.id == ID_MSGMENU_SAVE) {
            gToggle("dg_msgmenu_save", true);
        } else if (item.id == ID_MSGMENU_REPEAT) {
            gToggle("dg_msgmenu_repeat", true);
        } else if (item.id == ID_MSGMENU_CLEAR) {
            gToggle("dg_msgmenu_clear", true);
        } else if (item.id == ID_MSGMENU_HISTORY) {
            gToggle("dg_msgmenu_history", true);
        } else if (item.id == ID_MSGMENU_REPORT) {
            gToggle("dg_msgmenu_report", true);
        } else if (item.id == ID_MSGMENU_GENERATE) {
            gToggle("dg_msgmenu_generate", true);
        } else if (item.id == ID_MSGMENU_DETAILS) {
            gToggle("dg_msgmenu_details", true);
        } else if (item.id == ID_GROUP_MESSAGE_MENU) {
            gToggle("dg_groupMessageMenu", true);
        } else if (item.id == ID_RECOGNITION_LANG) {
            showRecognitionLanguagePicker();
            return;
        } else if (item.id == ID_RECOGNITION_AI) {
            DevGramConfig.setRecognitionAiPostProcessing(!DevGramConfig.isRecognitionAiPostProcessing());
        } else if (item.id == ID_SHOW_ONLINE_STATUS) {
            gToggle("dg_showOnlineStatus", false);
            if (messagesPreview != null) messagesPreview.reloadMessages();
        } else if (item.id == ID_SHOW_POLL_RESULTS) {
            gToggle("dg_showPollResults", false);
            if (messagesPreview != null) messagesPreview.reloadMessages();
        } else if (item.id == ID_HIDE_SHARE_BUTTON) {
            DevGramConfig.setHideShareButton(!DevGramConfig.hideShareButton);
            if (messagesPreview != null) messagesPreview.reloadMessages();
        } else if (item.id == ID_DOUBLE_TAP_IN) {
            showDoubleTapPicker(false);
            return;
        } else if (item.id == ID_DOUBLE_TAP_OUT) {
            showDoubleTapPicker(true);
            return;
        } else if (item.id == ID_DOUBLE_TAP_REACTION) {
            presentFragment(new org.telegram.ui.ReactionsDoubleTapManageActivity());
            return;
        } else if (item.id == ID_AI_EDITOR) {
            gToggle("hideAiEditor", false);
        } else if (item.id == ID_AI_SUMMARIES) {
            gToggle("dg_hideAiSummaries", false);
        } else if (item.id == ID_AI_CHAT) {
            if (!org.telegram.messenger.DevGramAiClient.isConfigured()) {
                showAiProviderDialog(true);
            } else {
                presentFragment(new DevGramAiChatActivity());
            }
            return;
        } else if (item.id == ID_AI_PROVIDER) {
            showAiProviderDialog(false);
            return;
        } else if (item.id == ID_OPEN_AI) {
            presentFragment(new DevGramCategoryActivity(CATEGORY_AI));
            return;
        } else if (item.id == ID_OPEN_CHAT_SETTINGS) {
            presentFragment(new org.telegram.ui.ThemeActivity(org.telegram.ui.ThemeActivity.THEME_TYPE_BASIC));
            return;
        } else if (item.id == ID_AI_SERVICES) {
            presentFragment(new DevGramAiServicesActivity());
            return;
        } else if (item.id == ID_AI_ROLES) {
            presentFragment(new DevGramAiRolesActivity());
            return;
        } else if (item.id == ID_AI_HISTORY) {
            gToggle("dg_aiSaveHistory", true);
        } else if (item.id == ID_AI_STREAMING) {
            gToggle("dg_aiStreaming", true);
        } else if (item.id == ID_AI_RESP_ONLY) {
            gToggle("dg_aiShowResponseOnly", false);
        } else if (item.id == ID_AI_QUOTE) {
            gToggle("dg_aiInsertAsQuote", true);
        } else if (item.id == ID_AI_CLEAR_HISTORY) {
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().remove("dg_aiHistory").apply();
            if (getParentActivity() != null) {
                android.widget.Toast.makeText(getParentActivity(), "История очищена", android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        } else if (item.id == ID_PHOTO_HAS_STICKER) {
            gToggle("photoHasSticker", true);
        } else if (item.id == ID_DISABLE_FLIP_PHOTOS) {
            gToggle("disableFlipPhotos", false);
        } else if (item.id == ID_REAR_VIDEO_MESSAGES) {
            gToggle("rearVideoMessages", false);
        } else if (item.id == ID_CAMERA_TYPE) {
            showCameraTypePicker();
            return;
        } else if (item.id == ID_VIDEO_CAMERA) {
            showVideoCameraPicker();
            return;
        } else if (item.id == ID_REMEMBER_LAST_CAMERA) {
            DevGramConfig.setRememberLastCamera(!DevGramConfig.isRememberLastCamera());
        } else if (item.id == ID_ZOOM_SLIDER) {
            DevGramConfig.setZoomSlider(!DevGramConfig.isZoomSlider());
        } else if (item.id == ID_STATIC_ZOOM) {
            DevGramConfig.setStaticZoom(!DevGramConfig.isStaticZoom());
        } else if (item.id == ID_DISABLE_VOLUME_AUTOPLAY) {
            gToggle("disablePlayVisibleVideoOnVolume", false);
        } else if (item.id == ID_HIDE_CAMERA_TILE) {
            gToggle("dg_hideCameraTile", false);
        } else if (item.id == ID_DISABLE_RECENT_FILES) {
            gToggle("disableRecentFilesAttachment", false);
        } else if (item.id == ID_DISABLE_AUTOPLAY_VOICE) {
            gToggle("disableAutoplayNextVoice", false);
        } else if (item.id == ID_PREFER_ORIGINAL_QUALITY) {
            gToggle("dg_preferOriginalQuality", false);
        } else if (item.id == ID_SWIPE_TO_PIP) {
            gToggle("dg_swipeToPip", true);
        } else if (item.id == ID_PAUSE_MINIMIZE) {
            gToggle("dg_pauseOnMinimize", false);
        } else if (item.id == ID_PAUSE_VIDEO_MINIMIZE) {
            gToggle("dg_pauseVideoOnMinimize", false);
        } else if (item.id == ID_PAUSE_VOICE_MINIMIZE) {
            gToggle("dg_pauseVoiceOnMinimize", false);
        } else if (item.id == ID_PAUSE_ROUND_MINIMIZE) {
            gToggle("dg_pauseRoundOnMinimize", false);
        } else if (item.id == ID_DOUBLE_TAP_SEEK) {
            showChoice("Перемотка двойным нажатием", DOUBLE_TAP_SEEK_OPTIONS,
                    "dg_doubleTapSeek", 1);
            return;
        } else {
            return;
        }
        if (category == CATEGORY_GHOST) {
            actionBar.setTitle(titleFor(category));
        }
        refreshList();
    }

    private void showRestartRequiredBulletin() {
        BulletinFactory.of(this).createSimpleBulletin(
                R.raw.info,
                "Для применения системного шрифта перезапустите DevGram",
                "Перезапустить",
                10_000,
                this::restartApplication
        ).show();
    }

    private void restartApplication() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        android.content.Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) {
            return;
        }
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        }
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(context, 9184, launch, flags);
        android.app.AlarmManager alarmManager = (android.app.AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        // Register the relaunch before closing the process. A short delay gives Android
        // time to persist the alarm; immediate killProcess() could lose the old 250 ms alarm.
        long triggerAt = System.currentTimeMillis() + 700;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
        android.app.Activity activity = getParentActivity();
        if (activity != null) {
            activity.finishAffinity();
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> android.os.Process.killProcess(android.os.Process.myPid()), 150);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Обновляем значения строк (Сервисы/Роли/…) при возврате с подэкранов.
        refreshList();
    }

    private void refreshList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void refreshListImmediately() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    private void rebuildAllScreens() {
        refreshList();
        if (parentLayout != null) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> parentLayout.rebuildFragments(0), 120);
        }
    }

    private static String translationTargetLabel() {
        String code = org.telegram.ui.Components.TranslateAlert2.getToLanguage();
        String name = org.telegram.ui.Components.TranslateAlert2.languageName(code);
        return name == null || name.isEmpty() ? code : org.telegram.ui.Components.TranslateAlert2.capitalFirst(name);
    }

    private static String doNotTranslateLabel() {
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        for (String code : org.telegram.ui.RestrictedLanguagesSelectActivity.getRestrictedLanguages()) {
            String name = org.telegram.ui.Components.TranslateAlert2.languageName(code);
            if (name != null && !name.isEmpty()) {
                labels.add(org.telegram.ui.Components.TranslateAlert2.capitalFirst(name));
            }
        }
        java.util.Collections.sort(labels, String.CASE_INSENSITIVE_ORDER);
        return android.text.TextUtils.join(", ", labels);
    }

    private void showTranslationTargetPicker() {
        java.util.ArrayList<org.telegram.messenger.TranslateController.Language> languages =
                new java.util.ArrayList<>(org.telegram.messenger.TranslateController.getLanguages());
        languages.removeIf(language -> language == null || android.text.TextUtils.isEmpty(language.code)
                || !DevGramTranslator.isTargetSupported(language.code));
        CharSequence[] labels = new CharSequence[languages.size()];
        for (int i = 0; i < languages.size(); i++) {
            org.telegram.messenger.TranslateController.Language language = languages.get(i);
            labels[i] = language.displayName + (android.text.TextUtils.isEmpty(language.ownDisplayName)
                    ? "" : " — " + language.ownDisplayName);
        }
        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle("Язык перевода");
        b.setItems(labels, (dialog, which) -> {
            org.telegram.ui.Components.TranslateAlert2.setToLanguage(languages.get(which).code);
            refreshList();
        });
        b.setNegativeButton("Отмена", null);
        showDialog(b.create());
    }

    private static String customSavePathLabel() {
        String path = DevGramGeneralConfig.getCustomSavePath();
        return android.text.TextUtils.isEmpty(path) ? "Системная папка" : path;
    }

    private org.telegram.ui.Components.EditTextBoldCursor createThemedDialogInput(String hint) {
        org.telegram.ui.Components.EditTextBoldCursor input =
                new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        input.setBackground(null);
        input.setLineColors(
                Theme.getColor(Theme.key_dialogInputField),
                Theme.getColor(Theme.key_dialogInputFieldActivated),
                Theme.getColor(Theme.key_text_RedBold));
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setCursorSize(org.telegram.messenger.AndroidUtilities.dp(20));
        input.setCursorWidth(1.5f);
        input.setHint(hint);
        input.setSingleLine(true);
        return input;
    }

    private void showCustomSavePathDialog() {
        if (getParentActivity() == null) return;
        final org.telegram.ui.Components.EditTextBoldCursor input = createThemedDialogInput("DevGram");
        input.setText(DevGramGeneralConfig.getCustomSavePath());
        int pad = org.telegram.messenger.AndroidUtilities.dp(20);
        android.widget.FrameLayout box = new android.widget.FrameLayout(getParentActivity());
        box.setPadding(pad, 0, pad, 0);
        box.addView(input, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT));
        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle("Папка сохранения");
        b.setView(box);
        b.setPositiveButton("Сохранить", (dialog, which) -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (!value.isEmpty() && !value.matches("^(?!\\.{1,2}$)[A-Za-zА-Яа-яЁё0-9._ -]{1,255}$")) {
                android.widget.Toast.makeText(getParentActivity(), "Недопустимое имя папки", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            DevGramGeneralConfig.setCustomSavePath(value);
            refreshList();
        });
        b.setNegativeButton("Отмена", null);
        showDialog(b.create());
        input.requestFocus();
        input.setSelection(input.length());
    }

    private void showSavedMediaFolderDialog() {
        if (getParentActivity() == null) return;
        final org.telegram.ui.Components.EditTextBoldCursor input = createThemedDialogInput("devgram_saved");
        input.setText(DevGramConfig.savedMediaFolder());
        int pad = org.telegram.messenger.AndroidUtilities.dp(20);
        android.widget.FrameLayout box = new android.widget.FrameLayout(getParentActivity());
        box.setPadding(pad, 0, pad, 0);
        box.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                .setTitle("Папка вложений")
                .setView(box)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!value.matches("^(?!\\.{1,2}$)[A-Za-zА-Яа-яЁё0-9._ -]{1,96}$")) {
                        android.widget.Toast.makeText(getParentActivity(), "Недопустимое имя папки",
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    org.telegram.messenger.DevGramMediaSaver.changeDirectory(value);
                    refreshListImmediately();
                })
                .setNegativeButton("Отмена", null)
                .show();
        input.requestFocus();
        input.setSelection(input.length());
    }

    private static String gPrefString(String key, String def) {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings().getString(key, def);
    }

    private void showAiProviderDialog(boolean openChatAfterSave) {
        if (getParentActivity() == null) return;
        android.widget.LinearLayout box = new android.widget.LinearLayout(getParentActivity());
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(20);
        box.setPadding(pad, 0, pad, 0);
        org.telegram.ui.Components.EditTextBoldCursor endpoint = createThemedDialogInput("Endpoint");
        endpoint.setText(gPrefString("dg_aiEndpoint", "https://api.openai.com/v1/chat/completions"));
        box.addView(endpoint, new android.widget.LinearLayout.LayoutParams(-1, -2));
        org.telegram.ui.Components.EditTextBoldCursor model = createThemedDialogInput("Модель");
        model.setText(gPrefString("dg_aiModel", "gpt-4o-mini"));
        box.addView(model, new android.widget.LinearLayout.LayoutParams(-1, -2));
        org.telegram.ui.Components.EditTextBoldCursor key = createThemedDialogInput("API-ключ");
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(gPrefString("dg_aiKey", ""));
        box.addView(key, new android.widget.LinearLayout.LayoutParams(-1, -2));
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle("Провайдер ИИ");
        builder.setView(box);
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit()
                    .putString("dg_aiEndpoint", endpoint.getText().toString().trim())
                    .putString("dg_aiModel", model.getText().toString().trim())
                    .putString("dg_aiKey", key.getText().toString().trim()).apply();
            refreshList();
            if (openChatAfterSave && org.telegram.messenger.DevGramAiClient.isConfigured()) {
                presentFragment(new DevGramAiChatActivity());
            }
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    // Диагностика прокси: soket-проверка блокирующая → гоним в фоне, результат в диалоге.
    private void runProxyDiagnostics() {
        final org.telegram.ui.ActionBar.AlertDialog progress =
                new org.telegram.ui.ActionBar.AlertDialog(getParentActivity(), org.telegram.ui.ActionBar.AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        new Thread(() -> {
            final String report = org.telegram.messenger.DevGramProxy.diagnose();
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                try {
                    progress.dismiss();
                } catch (Throwable ignore) {
                }
                if (getParentActivity() == null) {
                    return;
                }
                org.telegram.ui.ActionBar.AlertDialog.Builder b =
                        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
                b.setTitle("Диагностика прокси");
                b.setMessage(report);
                b.setPositiveButton("Копировать", (d, w) -> {
                    org.telegram.messenger.AndroidUtilities.addToClipboard(report);
                    org.telegram.ui.Components.BulletinFactory.of(this)
                            .createCopyBulletin("Скопировано").show();
                });
                b.setNegativeButton("Закрыть", null);
                showDialog(b.create());
            });
        }, "DevGramProxyDiag").start();
    }

    // Пикер действия двойного нажатия (отдельно для входящих/исходящих), с иконками действий.
    // Селектор «Тип камеры» (Camera 1 / Camera 2) — бэкенд API камеры через SharedConfig.useCamera2Force.
    private static String cameraTypeLabel() {
        if (org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean("dg_useCameraX", false)) {
            return "Camera X";
        }
        return org.telegram.messenger.SharedConfig.isUsingCamera2(org.telegram.messenger.UserConfig.selectedAccount)
                ? "Camera 2" : "Camera 1";
    }

    private void showCameraTypePicker() {
        CharSequence[] items = {"Camera 1", "Camera 2", "Camera X"};
        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle("Тип камеры");
        b.setItems(items, (d, which) -> {
            android.content.SharedPreferences p = org.telegram.messenger.MessagesController.getGlobalMainSettings();
            if (which == 2) {
                p.edit().putBoolean("dg_useCameraX", true).apply();
            } else {
                p.edit().putBoolean("dg_useCameraX", false).apply();
                org.telegram.messenger.SharedConfig.setUseCamera2Force(which == 1);
            }
            refreshList();
        });
        b.setNegativeButton("Отмена", null);
        showDialog(b.create());
    }

    // Селектор «Камера кружков» (Передняя / Задняя) — pref rearVideoMessages.
    // === AI Chat (повтор AiPreferencesActivity exteraGram) ===
    private static String aiServicesLabel() {
        if (!org.telegram.messenger.DevGramAiClient.isConfigured()) {
            return "Нет";
        }
        String model = gPrefString("dg_aiModel", "gpt-4o-mini");
        return model == null || model.isEmpty() ? "OpenAI" : model;
    }

    private static String aiRoleLabel() {
        return gPrefString("dg_aiRole", "Assistant");
    }

    private static int aiTempInit() {
        int v = Math.round(org.telegram.messenger.MessagesController.getGlobalMainSettings()
                .getFloat("dg_aiTemperature", 1.0f) * 10);
        return Math.max(0, Math.min(20, v));
    }

    private void showAiRolePicker() {
        final String[] roles = {"Assistant", "Переводчик", "Программист", "Редактор", "Собеседник"};
        final String[] prompts = {
                "",
                "Ты профессиональный переводчик. Переводи текст пользователя, сохраняя смысл и тон. Отвечай только переводом.",
                "Ты опытный программист. Помогай с кодом, давай точные и краткие ответы с примерами.",
                "Ты редактор. Улучшай текст пользователя: грамотность, ясность, стиль. Отвечай только исправленным текстом.",
                "Ты дружелюбный собеседник. Отвечай живо, по делу и с эмпатией."
        };
        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle("Роли");
        b.setItems(roles, (d, which) -> {
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit()
                    .putString("dg_aiRole", roles[which])
                    .putString("dg_aiSystemPrompt", prompts[which])
                    .apply();
            refreshList();
        });
        b.setNegativeButton("Отмена", null);
        showDialog(b.create());
    }

    // Камера кружков: тристейт как у exteraGram — 0 Передняя / 1 Задняя / 2 Спросить.
    private static String videoCameraLabel() {
        switch (gInt("dg_roundCameraMode", 0)) {
            case 1: return "Задняя";
            case 2: return "Спросить";
            default: return "Передняя";
        }
    }

    private void showVideoCameraPicker() {
        CharSequence[] items = {"Передняя", "Задняя", "Спросить"};
        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle("Камера кружков");
        b.setItems(items, (d, which) -> {
            android.content.SharedPreferences.Editor e =
                    org.telegram.messenger.MessagesController.getGlobalMainSettings().edit();
            e.putInt("dg_roundCameraMode", which);
            // Для Передняя/Задняя синхронизируем штатный флаг — его читает InstantCameraView.
            if (which == 0) e.putBoolean("rearVideoMessages", false);
            else if (which == 1) e.putBoolean("rearVideoMessages", true);
            e.apply();
            refreshList();
        });
        b.setNegativeButton("Отмена", null);
        showDialog(b.create());
    }

    // Метка текущего языка распознавания для строки настроек.
    private static String recognitionLangLabel() {
        String lang = DevGramConfig.getRecognitionLanguage();
        if ("none".equals(lang)) {
            return "Отключено";
        }
        boolean downloaded = org.telegram.messenger.DevGramVoiceRecognizer.getInstance().isDownloaded(lang);
        return recognitionLangName(lang) + (downloaded ? "" : " · не скачано");
    }

    private static String recognitionLangName(String code) {
        switch (code) {
            case "en": return "Английский";
            case "ru": return "Русский";
            case "de": return "Немецкий";
            case "es": return "Испанский";
            case "fr": return "Французский";
            case "it": return "Итальянский";
            case "pt": return "Португальский";
            case "nl": return "Нидерландский";
            case "pl": return "Польский";
            case "uk": return "Украинский";
            case "tr": return "Турецкий";
            case "cs": return "Чешский";
            case "ca": return "Каталанский";
            case "eo": return "Эсперанто";
            case "fa": return "Персидский";
            case "hi": return "Хинди";
            case "ja": return "Японский";
            case "ko": return "Корейский";
            case "uz": return "Узбекский";
            default: return code;
        }
    }

    // Пикер языка Vosk: «Отключено» + список языков (скачано / размер). При выборе — скачиваем модель.
    private void showRecognitionLanguagePicker() {
        java.util.List<org.telegram.messenger.DevGramVoiceRecognizer.RecognitionModel> models =
                org.telegram.messenger.DevGramVoiceRecognizer.getInstance().listAvailableModels();
        final java.util.List<String> codes = new java.util.ArrayList<>();
        final java.util.List<CharSequence> labels = new java.util.ArrayList<>();
        codes.add("none");
        labels.add("Отключено");
        for (org.telegram.messenger.DevGramVoiceRecognizer.RecognitionModel m : models) {
            codes.add(m.language);
            boolean dl = org.telegram.messenger.DevGramVoiceRecognizer.getInstance().isDownloaded(m.language);
            labels.add(recognitionLangName(m.language) + (dl ? "  ✓" : "  · " + (m.size / 1024 / 1024) + " МБ"));
        }
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle("Язык распознавания");
        builder.setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
            String code = codes.get(which);
            DevGramConfig.setRecognitionLanguage(code);
            org.telegram.messenger.forkgram.ForkOfflineTranscribe.invalidate();
            refreshList();
            if (!"none".equals(code)
                    && !org.telegram.messenger.DevGramVoiceRecognizer.getInstance().isDownloaded(code)) {
                downloadRecognitionModel(code);
            }
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void downloadRecognitionModel(final String code) {
        if (getParentActivity() == null) {
            return;
        }
        final org.telegram.ui.ActionBar.AlertDialog progress =
                new org.telegram.ui.ActionBar.AlertDialog(getParentActivity(), org.telegram.ui.ActionBar.AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.setMessage("Скачивание модели: " + recognitionLangName(code) + "…");
        progress.show();
        new Thread(() -> org.telegram.messenger.DevGramVoiceRecognizer.getInstance().downloadModel(code,
                new org.telegram.messenger.DevGramVoiceRecognizer.DownloadCallback() {
                    @Override
                    public void onProgress(float p) {
                        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progress.setMessage("Скачивание модели: " + recognitionLangName(code)
                                        + "  " + Math.round(p * 100) + "%");
                            } catch (Throwable ignore) {
                            }
                        });
                    }

                    @Override
                    public void onCompleted() {
                        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progress.dismiss();
                            } catch (Throwable ignore) {
                            }
                            refreshList();
                            if (getParentActivity() != null) {
                                android.widget.Toast.makeText(getParentActivity(),
                                        "Модель готова: " + recognitionLangName(code), android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progress.dismiss();
                            } catch (Throwable ignore) {
                            }
                            if (getParentActivity() != null) {
                                android.widget.Toast.makeText(getParentActivity(),
                                        "Ошибка загрузки модели", android.widget.Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }), "DevGramVoskDownload").start();
    }

    private void showDoubleTapPicker(boolean out) {
        CharSequence[] labels = org.telegram.messenger.DevGramDoubleTapUtils.labels(out);
        int[] icons = org.telegram.messenger.DevGramDoubleTapUtils.icons(out);
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle(out ? "Исходящие сообщения" : "Входящие сообщения");
        builder.setItems(labels, icons, (dialog, which) -> {
            if (out) {
                DevGramConfig.setDoubleTapActionOut(which);
            } else {
                DevGramConfig.setDoubleTapActionIn(which);
            }
            if (doubleTapPreview != null) {
                doubleTapPreview.updateIcons(out ? 1 : 0, true);
            }
            refreshList();
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }
}

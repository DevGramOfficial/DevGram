/*
 * DevGram: конфиг «режима призрака».
 * Логика портирована из AyuGram for Android (GPL v2+, © @Radolyn),
 * адаптирована под нашу базу и бренд.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;

public class DevGramConfig {
    private static final Object sync = new Object();
    private static boolean loaded;

    public static SharedPreferences preferences;

    // ВАЖНО: значения по умолчанию заданы прямо в полях, чтобы поведение было корректным
    // даже если loadConfig() ещё не отработал (иначе булины были бы false = ghost включён,
    // сохранение выключено). loadConfig() лишь перезаписывает их из SharedPreferences.
    // true  = вести себя как обычный клиент (отправлять пакеты);
    // false = скрывать активность (режим призрака).
    public static boolean sendReadPackets = true;   // статусы прочтения
    public static boolean sendOnlinePackets = true; // статус «в сети»
    public static boolean sendUploadTyping = true;  // «печатает…» / «загружает…»

    // Скрывать рекламу: спонсорские сообщения в каналах и ботах + реклама в видеоплеере.
    public static boolean disableAds = false;

    // Локальный премиум: клиент ведёт себя так, будто у аккаунта есть Telegram Premium —
    // открываются КЛИЕНТСКИЕ фичи (безлимит папок, премиум-эмодзи/стикеры/реакции, статусы
    // и т.п.). Серверные вещи (гигабайтные загрузки, премиум-реакции у собеседников) подделать
    // нельзя. Хук стоит в UserConfig.isPremium() и MessagesController.isPremiumUser() — только
    // для нашего аккаунта, чужим премиум не приписываем.
    public static boolean localPremium = false;

    // Огоньки-стрик (🔥N рядом с именем). По умолчанию включены.
    public static boolean streaksEnabled = true;

    // Встроенный WireGuard-VPN. По умолчанию выключен.
    public static boolean vpnEnabled = false;

    // Профиль в стиле iOS (центрированная шапка, стеклянные кнопки/карточки). По умолчанию ВКЛючён.
    public static boolean iosProfile = true;

    // --- сохранение истории ---
    // По умолчанию ВЫКЛЮЧЕНЫ, как и режим призрака: мод не должен ничего менять,
    // пока пользователь сам не включит нужную функцию.
    public static boolean saveDeletedMessages = false; // сохранять удалённые сообщения
    public static boolean saveMessagesHistory = false; // сохранять историю правок
    public static boolean saveMedia = false;           // сохранять вложения удалённых
    public static boolean saveInBotChats = false;      // сохранять и в диалогах с ботами
    public static boolean saveReadDate = false;
    public static boolean saveLocalOnline = false;
    public static boolean probeUsingOtherAccounts = false;

    // --- раздел «Сбор данных» ---
    // analytics — ВЫКЛ по умолчанию; crashes — ВКЛ по умолчанию, но реальный сбор пока заглушён
    // мастер-гейтом DevGramTelemetry.COLLECT_CRASHES (см. «не собирай пока что»).
    public static boolean analyticsEnabled = false;
    public static boolean crashlyticsEnabled = true;

    // --- раздел «Внешний вид» ---
    // Отключить округление чисел (1.2K -> 1 234) — как в exteraGram (DisableNumberRounding).
    public static boolean disableNumberRounding = false;
    // Квадратная кнопка (FAB) — в exteraGram ВКЛ по умолчанию (squareFab).
    public static boolean squareFab = true;
    // Стеклянное меню сообщения (размытый снимок чата под меню) — как exteraGram GlassMessageMenu.
    public static boolean glassMenu = true;
    // Скрыть строку категорий в поиске эмодзи/стикеров — в exteraGram ВКЛ по умолчанию (hideCategories).
    public static boolean hideEmojiCategories = true;
    // Снег поверх шапки всегда (exteraGram ForceSnow).
    public static boolean forceSnow = false;
    // Заголовок по центру шапки (exteraGram centerTitle).
    public static boolean centerTitle = false;
    public static boolean displayGhostStatus = false;

    // --- раздел «Чаты» (порт из exteraGram) ---
    public static boolean disableMarkdown = false;       // не преобразовывать **/`code` в форматирование
    public static boolean hideKeyboardOnScroll = true;   // скрывать клавиатуру при прокрутке чата (в exteraGram ВКЛ по умолчанию)
    public static boolean disableGreetingSticker = false; // не показывать приветственный стикер в пустом чате
    public static boolean addCommaAfterMention = true;    // запятая после @упоминания в начале строки (в exteraGram ВКЛ)
    public static boolean removeMessageTail = false;
    public static boolean replaceEditedWithIcon = false;
    public static boolean hideShareButton = false;
    public static boolean hideStickerTime = false; // скрыть время на стикерах (как exteraGram)
    public static boolean inlineCalc = true;       // результаты в поле ввода: «2+2=» → дописать «4»
    public static boolean inlineMath = true;       // калькулятор
    public static boolean inlineCurrency = true;   // курсы валют
    public static boolean inlineUnits = true;      // единицы измерения
    public static boolean inlineTimeZones = false; // часовые пояса
    public static String inlineTargetCurrency = ""; // валюта по умолчанию ("" — авто)
    public static int inlineAngleUnit = 0;         // 0 — градусы, 1 — радианы
    public static int inlineMeasureSystem = 0;     // 0 — авто, 1 — метрическая, 2 — имперская
    public static boolean inlineDigitGrouping = true; // разделять разряды
    public static boolean weatherInStories = false; // виджет погоды в историях (просит доступ к гео) — по умолчанию ВЫКЛ
    public static int updateChannel = 0;           // канал обновлений: 0 = основной, 1 = бета (нужен значок поддержавшего)

    // Действия свайпом по сообщению. Строка одновременно хранит включённые действия и их порядок.
    public static String swipeActions = DevGramSwipeAction.DEFAULT_ORDER;
    public static boolean swipeActionsLoop = true;
    public static boolean swipeActionsReversed = false;

    // --- гейт для разрешённых пакетов чтения (например, ручная отметка «прочитано») ---
    private static final Object readSync = new Object();
    private static boolean allowReadVal;
    private static int allowReadResetAfter;

    static {
        loadConfig();
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (loaded) {
                return;
            }
            if (ApplicationLoader.applicationContext == null) {
                return; // контекст ещё не готов — попробуем позже, поля пока держат дефолты
            }
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("devgram_ghost", Activity.MODE_PRIVATE);
            sendReadPackets = preferences.getBoolean("sendReadPackets", true);
            sendOnlinePackets = preferences.getBoolean("sendOnlinePackets", true);
            sendUploadTyping = preferences.getBoolean("sendUploadTyping", true);
            disableAds = preferences.getBoolean("disableAds", false);
            localPremium = preferences.getBoolean("localPremium", false);
            streaksEnabled = preferences.getBoolean("streaksEnabled", true);
            vpnEnabled = preferences.getBoolean("vpnEnabled", false);
            iosProfile = preferences.getBoolean("iosProfile", true);
            saveDeletedMessages = preferences.getBoolean("saveDeletedMessages", false);
            saveMessagesHistory = preferences.getBoolean("saveMessagesHistory", false);
            saveMedia = preferences.getBoolean("saveMedia", false);
            saveInBotChats = preferences.getBoolean("saveInBotChats", false);
            saveReadDate = preferences.getBoolean("saveReadDate", false);
            saveLocalOnline = preferences.getBoolean("saveLocalOnline", false);
            probeUsingOtherAccounts = preferences.getBoolean("probeUsingOtherAccounts", false);
            analyticsEnabled = preferences.getBoolean("analyticsEnabled", false);
            crashlyticsEnabled = preferences.getBoolean("crashlyticsEnabled", true);
            disableNumberRounding = preferences.getBoolean("disableNumberRounding", false);
            squareFab = preferences.getBoolean("squareFab", true);
            glassMenu = preferences.getBoolean("glassMenu", true);
            hideEmojiCategories = preferences.getBoolean("hideEmojiCategories", true);
            forceSnow = preferences.getBoolean("forceSnow", false);
            centerTitle = preferences.getBoolean("centerTitle", false);
            displayGhostStatus = preferences.getBoolean("displayGhostStatus", false);
            disableMarkdown = preferences.getBoolean("disableMarkdown", false);
            hideKeyboardOnScroll = preferences.getBoolean("hideKeyboardOnScroll", true);
            inlineCalc = preferences.getBoolean("inlineCalc", true);
            inlineMath = preferences.getBoolean("inlineMath", true);
            inlineCurrency = preferences.getBoolean("inlineCurrency", true);
            inlineUnits = preferences.getBoolean("inlineUnits", true);
            inlineTimeZones = preferences.getBoolean("inlineTimeZones", false);
            inlineTargetCurrency = preferences.getString("inlineTargetCurrency", "");
            inlineAngleUnit = preferences.getInt("inlineAngleUnit", 0);
            inlineMeasureSystem = preferences.getInt("inlineMeasureSystem", 0);
            inlineDigitGrouping = preferences.getBoolean("inlineDigitGrouping", true);
            weatherInStories = preferences.getBoolean("weatherInStories", false);
            updateChannel = preferences.getInt("updateChannel", 0);
            disableGreetingSticker = preferences.getBoolean("disableGreetingSticker", false);
            addCommaAfterMention = preferences.getBoolean("addCommaAfterMention", true);
            removeMessageTail = preferences.getBoolean("removeMessageTail", false);
            replaceEditedWithIcon = preferences.getBoolean("replaceEditedWithIcon", false);
            hideShareButton = preferences.getBoolean("hideShareButton", false);
            hideStickerTime = preferences.getBoolean("hideStickerTime", false);
            swipeActions = preferences.getString("swipeActions", DevGramSwipeAction.DEFAULT_ORDER);
            swipeActionsLoop = preferences.getBoolean("swipeActionsLoop", true);
            swipeActionsReversed = preferences.getBoolean("swipeActionsReversed", false);
            loaded = true;
        }
    }

    // --- раздел «Google»: тумблеры сбора данных ---
    public static void setAnalyticsEnabled(boolean v) {
        analyticsEnabled = v;
        if (preferences != null) {
            preferences.edit().putBoolean("analyticsEnabled", v).apply();
        }
        applyFirebaseCollection();
    }

    public static void setCrashlyticsEnabled(boolean v) {
        crashlyticsEnabled = v;
        if (preferences != null) {
            preferences.edit().putBoolean("crashlyticsEnabled", v).apply();
        }
        applyFirebaseCollection();
    }

    public static void setDisableNumberRounding(boolean v) {
        disableNumberRounding = v;
        if (preferences != null) {
            preferences.edit().putBoolean("disableNumberRounding", v).apply();
        }
    }

    public static void setSquareFab(boolean v) {
        squareFab = v;
        if (preferences != null) {
            preferences.edit().putBoolean("squareFab", v).apply();
        }
    }

    public static void setGlassMenu(boolean v) {
        glassMenu = v;
        if (preferences != null) {
            preferences.edit().putBoolean("glassMenu", v).apply();
        }
    }

    public static void setHideEmojiCategories(boolean v) {
        hideEmojiCategories = v;
        if (preferences != null) {
            preferences.edit().putBoolean("hideEmojiCategories", v).apply();
        }
    }

    public static void setForceSnow(boolean v) {
        forceSnow = v;
        if (preferences != null) {
            preferences.edit().putBoolean("forceSnow", v).apply();
        }
    }

    public static void setCenterTitle(boolean v) {
        centerTitle = v;
        if (preferences != null) {
            preferences.edit().putBoolean("centerTitle", v).apply();
        }
    }

    public static void setDisplayGhostStatus(boolean v) {
        displayGhostStatus = v;
        if (preferences != null) preferences.edit().putBoolean("displayGhostStatus", v).apply();
    }

    // Системные эмодзи (флаг живёт в SharedConfig — обёртка для нашего меню).
    public static boolean isUseSystemEmoji() {
        return org.telegram.messenger.SharedConfig.useSystemEmoji;
    }

    public static void setUseSystemEmoji(boolean v) {
        org.telegram.messenger.SharedConfig.useSystemEmoji = v;
        MessagesController.getGlobalMainSettings().edit().putBoolean("useSystemEmoji", v).apply();
    }

    // Обход запрета пересылки, сохранения и скриншотов (noforwards и купленные за звёзды медиа) —
    // всегда включён, без тумблера.
    public static boolean allowRestricted() {
        return true;
    }

    // Размер стикеров (0..N) — pref в глобальных настройках, читается ChatMessageCell/LocaleController.
    public static float getStickerSize() {
        return MessagesController.getGlobalMainSettings().getFloat("stickerSize", 14f);
    }

    public static void setStickerSize(float v) {
        MessagesController.getGlobalMainSettings().edit().putFloat("stickerSize", v).apply();
    }

    // Двойное нажатие — раздельные действия для входящих/исходящих (индекс в списке DevGramDoubleTapUtils).
    // По умолчанию 1 = «Реакции» (совпадает со старым поведением).
    public static int getDoubleTapActionIn() {
        return MessagesController.getGlobalMainSettings().getInt("dg_doubletap_in", 1);
    }

    public static void setDoubleTapActionIn(int v) {
        MessagesController.getGlobalMainSettings().edit().putInt("dg_doubletap_in", v).apply();
    }

    public static int getDoubleTapActionOut() {
        return MessagesController.getGlobalMainSettings().getInt("dg_doubletap_out", 1);
    }

    public static void setDoubleTapActionOut(int v) {
        MessagesController.getGlobalMainSettings().edit().putInt("dg_doubletap_out", v).apply();
    }

    // Стиль вкладок папок (0..2) — pref в глобальных настройках, читается FolderIcons.
    public static int getFolderTabsStyle() {
        return MessagesController.getGlobalMainSettings().getInt("folderTabsStyle", 0);
    }

    public static void setFolderTabsStyle(int v) {
        MessagesController.getGlobalMainSettings().edit().putInt("folderTabsStyle", v).apply();
    }

    // Общий переключатель дизайна приложения: DevGram (наш) / iOS / «Старый» (пресет в стиле
    // AyuGram — не отдельный рендер-код, а конкретная комбинация уже существующих
    // exteraGram-настроек внешнего вида: радиус аватарок, размер бейджа непрочитанных,
    // цветовая тема и т.п.).
    public static final int DESIGN_MODE_DEVGRAM = 0;
    public static final int DESIGN_MODE_IOS = 1;
    public static final int DESIGN_MODE_OLD = 2;

    public static int getDesignMode() {
        return MessagesController.getGlobalMainSettings().getInt("dg_designMode", DESIGN_MODE_DEVGRAM);
    }

    public static void setDesignMode(int v) {
        MessagesController.getGlobalMainSettings().edit().putInt("dg_designMode", v).apply();
    }

    // Применяет пресет для выбранного режима дизайна поверх уже существующих настроек
    // внешнего вида (это не новый рендер-код — decompile AyuGram/exteraGram показал, что там
    // те же ячейки/баблы, что и у нас, просто с другим набором значений тех же настроек:
    // радиус аватарок, MD3-эффекты, «липкая» анимация и т.п.). Пользователь может донастроить
    // каждый пункт вручную после применения пресета — это отправная точка, не жёсткая блокировка.
    public static void applyDesignPreset(int mode) {
        final boolean wasIos = org.telegram.ui.Components.DevGramMaterial3.iosNavigation();
        setDesignMode(mode);
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        switch (mode) {
            case DESIGN_MODE_IOS:
                // Круглые аватарки, без Material-эффектов (MD3/«липкая» анимация — андроидные),
                // заголовок по центру — как в iOS. Профиль в стиле iOS уже был отдельным
                // тумблером (iosProfile) — включаем его тоже.
                editor.putFloat("avatarCornersF", 30f);
                editor.putBoolean("dg_singleCorner", true);
                editor.putBoolean("dg_md3", false);
                editor.putBoolean("dg_gooey", false);
                editor.putBoolean("dg_centerTitle", true);
                editor.apply();
                DevGramIOSConfig.setCenterChatTitle(true);
                DevGramIOSConfig.setAdaptiveTitleWidth(true);
                DevGramIOSConfig.setShowUnreadCounter(true);
                DevGramIOSConfig.setIosUnreadCounter(true);
                DevGramIOSConfig.setIosInputPanel(true);
                DevGramIOSConfig.setNotificationSound(DevGramIOSConfig.NOTIFICATION_SOUND_IOS);
                DevGramIOSConfig.setIosMessageMenu(true);
                DevGramIOSConfig.setNativeMenuBlur(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
                DevGramIOSConfig.setShowWholeAlbumInMenu(true);
                DevGramIOSConfig.setRememberKeyboardForMenu(true);
                DevGramIOSConfig.setCompactMenuActions(true);
                setIosProfile(true);
                LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true);
                LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, true);
                // В iOS-режиме вкладки «Контакты» и «Звонки» всегда видны в нижнем меню
                // (как в Settings.app/Telegram-iOS) — включаем сразу. Важно: после прямого
                // изменения UserConfig обязательно шлём те же уведомления, что и родные
                // тумблеры показа/скрытия — иначе у уже открытого MainTabsActivity не
                // пересобираются позиции вкладок в ViewPager, и клик по табу попадает на
                // фрагмент, оставшийся от старой раскладки (баг: «Контакты» открывали «Настройки»).
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        UserConfig userConfig = UserConfig.getInstance(a);
                        boolean contactsWasHidden = !userConfig.showContactsTab;
                        userConfig.setShowContactsTab(true);
                        if (contactsWasHidden) {
                            NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.contactsTabVisibleToggled);
                        }
                        boolean callsWasHidden = !userConfig.showCallsTab;
                        userConfig.setShowCallsTab(true);
                        if (callsWasHidden) {
                            NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.callTabsVisibleToggled);
                        }
                    }
                }
                break;
            case DESIGN_MODE_OLD:
                // «Старый» вид: без MD3/«липкой» анимации и стеклянных эффектов — то, что
                // реально отличает AyuGram/классический Telegram от текущего DevGram, судя по
                // декомпиляции и реальным скриншотам — не форма баблов (там сток), а полностью
                // круглые аватарки (не 28dp, а честный круг) и новые Material3/glass-эффекты,
                // которых в AyuGram просто нет/выключены. Скриншот чата AyuGram также показал
                // сплошную белую линию-разделитель под шапкой чата вместо размытия/стекла —
                // выключаем FLAG_CHAT_BLUR/FLAG_LIQUID_GLASS, чтобы вернуть чёткую границу.
                editor.putFloat("avatarCornersF", 30f);
                editor.putBoolean("dg_singleCorner", false);
                editor.putBoolean("dg_md3", false);
                editor.putBoolean("dg_gooey", false);
                editor.putBoolean("dg_centerTitle", false);
                editor.apply();
                DevGramIOSConfig.setCenterChatTitle(false);
                DevGramIOSConfig.setIosInputPanel(false);
                DevGramIOSConfig.setIosMessageMenu(false);
                setIosProfile(false);
                LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, false);
                LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, false);
                resetContactsTabToHidden();
                resetCallsTabToHidden();
                break;
            default:
                // DevGram — текущий дизайн мода по умолчанию: аватарки полным кругом,
                // без «липкой» (gooey) анимации.
                editor.putFloat("avatarCornersF", 30f);
                editor.putBoolean("dg_singleCorner", false);
                editor.putBoolean("dg_md3", true);
                editor.putBoolean("dg_gooey", false);
                editor.putBoolean("dg_centerTitle", false);
                editor.apply();
                DevGramIOSConfig.setCenterChatTitle(false);
                DevGramIOSConfig.setIosInputPanel(false);
                DevGramIOSConfig.setIosMessageMenu(false);
                DevGramIOSConfig.setNotificationSound(DevGramIOSConfig.NOTIFICATION_SOUND_TELEGRAM);
                setIosProfile(true);
                LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true);
                LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, true);
                resetContactsTabToHidden();
                resetCallsTabToHidden();
                break;
        }

        // DevGram: вход/выход из iOS-режима полностью меняет схему нумерации позиций нижних
        // вкладок (не точечный сдвиг, как при обычном скрытии/показе Контактов/Звонков) —
        // шлём отдельное уведомление, чтобы MainTabsActivity сделал чистый пересбор кэша
        // фрагментов (см. mainTabsSchemeChanged в NotificationCenter/MainTabsActivity).
        final boolean isIos = org.telegram.ui.Components.DevGramMaterial3.iosNavigation();
        if (wasIos != isIos) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.mainTabsSchemeChanged);
                }
            }
        }
    }

    // DevGram: «Контакты» в нижнем меню по умолчанию скрыты везде, кроме iOS-режима (где их
    // нельзя скрыть) — если раньше их включили вручную или через iOS-пресет, при переключении
    // на DevGram/«Старый» возвращаем скрытое состояние.
    private static void resetContactsTabToHidden() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                UserConfig userConfig = UserConfig.getInstance(a);
                boolean contactsWasShown = userConfig.showContactsTab;
                userConfig.setShowContactsTab(false);
                if (contactsWasShown) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.contactsTabVisibleToggled);
                }
            }
        }
    }

    // DevGram: «Звонки» вместо «Настроек» в общем слоте — тоже только для iOS-режима (там
    // Звонки всегда включены и их нельзя скрыть). Если включили через iOS-пресет, при
    // переключении на DevGram/«Старый» возвращаем «Настройки» на их место в этом слоте.
    private static void resetCallsTabToHidden() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                UserConfig userConfig = UserConfig.getInstance(a);
                boolean callsWasShown = userConfig.showCallsTab;
                userConfig.setShowCallsTab(false);
                if (callsWasShown) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.callTabsVisibleToggled);
                }
            }
        }
    }

    public static void setDisableMarkdown(boolean v) {
        disableMarkdown = v;
        if (preferences != null) {
            preferences.edit().putBoolean("disableMarkdown", v).apply();
        }
    }

    public static void setHideKeyboardOnScroll(boolean v) {
        hideKeyboardOnScroll = v;
        if (preferences != null) {
            preferences.edit().putBoolean("hideKeyboardOnScroll", v).apply();
        }
    }

    public static void setInlineCalc(boolean v) {
        inlineCalc = v;
        if (preferences != null) {
            preferences.edit().putBoolean("inlineCalc", v).apply();
        }
    }

    public static void setInlineBoolean(String key, boolean v) {
        switch (key) {
            case "inlineMath": inlineMath = v; break;
            case "inlineCurrency": inlineCurrency = v; break;
            case "inlineUnits": inlineUnits = v; break;
            case "inlineTimeZones": inlineTimeZones = v; break;
            case "inlineDigitGrouping": inlineDigitGrouping = v; break;
            default: return;
        }
        if (preferences != null) {
            preferences.edit().putBoolean(key, v).apply();
        }
    }

    public static void setInlineTargetCurrency(String v) {
        inlineTargetCurrency = v == null ? "" : v;
        if (preferences != null) {
            preferences.edit().putString("inlineTargetCurrency", inlineTargetCurrency).apply();
        }
    }

    public static void setInlineAngleUnit(int v) {
        inlineAngleUnit = v;
        if (preferences != null) {
            preferences.edit().putInt("inlineAngleUnit", v).apply();
        }
    }

    public static void setInlineMeasureSystem(int v) {
        inlineMeasureSystem = v;
        if (preferences != null) {
            preferences.edit().putInt("inlineMeasureSystem", v).apply();
        }
    }

    public static void setWeatherInStories(boolean v) {
        weatherInStories = v;
        if (preferences != null) {
            preferences.edit().putBoolean("weatherInStories", v).apply();
        }
    }

    public static void setUpdateChannel(int v) {
        updateChannel = v;
        if (preferences != null) {
            preferences.edit().putInt("updateChannel", v).apply();
        }
    }

    // Персональный токен доступа к бете (выдаёт @officialdevgram_bot по значку поддержавшего).
    // Токен привязан к Telegram-аккаунту, поэтому хранится отдельно на каждый uid. Старый общий
    // «betaToken» (до разделения) — запасной вариант, пока аккаунт не получит свой.
    public static String betaToken = "";

    public static String getBetaToken() {
        return getBetaToken(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
    }

    public static String getBetaToken(long uid) {
        if (preferences == null) return "";
        String own = preferences.getString("betaToken_" + uid, "");
        if (!own.isEmpty()) return own;
        if (preferences.getBoolean("betaTokenNoLegacy_" + uid, false)) return "";
        return preferences.getString("betaToken", "");
    }

    public static void setBetaToken(long uid, String v) {
        if (preferences != null) {
            preferences.edit().putString("betaToken_" + uid, v == null ? "" : v).apply();
        }
    }

    // Сервер не принял токен этого аккаунта — забыть его (и общий запасной для этого аккаунта)
    public static void clearBetaToken(long uid) {
        if (preferences != null) {
            preferences.edit().remove("betaToken_" + uid).putBoolean("betaTokenNoLegacy_" + uid, true).apply();
        }
    }

    public static void setDisableGreetingSticker(boolean v) {
        disableGreetingSticker = v;
        if (preferences != null) {
            preferences.edit().putBoolean("disableGreetingSticker", v).apply();
        }
    }

    public static void setAddCommaAfterMention(boolean v) {
        addCommaAfterMention = v;
        if (preferences != null) {
            preferences.edit().putBoolean("addCommaAfterMention", v).apply();
        }
    }

    public static void setRemoveMessageTail(boolean v) {
        removeMessageTail = v;
        if (preferences != null) preferences.edit().putBoolean("removeMessageTail", v).apply();
        // Уже открытые чаты кэшируют Path бабла (Theme.MessageDrawable.PathDrawParams) и сами не
        // перерисуются, пока их не проскроллить — из-за этого хвост оставался на части сообщений,
        // а у остальных бабл уже пересчитывался (отсюда же и «съезжающий» левый край).
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.devgramBubbleStyleChanged);
    }

    public static void setReplaceEditedWithIcon(boolean v) {
        replaceEditedWithIcon = v;
        if (preferences != null) preferences.edit().putBoolean("replaceEditedWithIcon", v).apply();
    }

    public static void setHideShareButton(boolean v) {
        hideShareButton = v;
        if (preferences != null) preferences.edit().putBoolean("hideShareButton", v).apply();
    }

    public static void setHideStickerTime(boolean v) {
        hideStickerTime = v;
        if (preferences != null) preferences.edit().putBoolean("hideStickerTime", v).apply();
    }

    // Язык офлайн-распознавания голоса (Vosk). "none" = выключено (используется штатная серверная транскрипция).
    public static String getRecognitionLanguage() {
        return MessagesController.getGlobalMainSettings().getString("dg_recognitionLanguage", "none");
    }

    public static void setRecognitionLanguage(String lang) {
        MessagesController.getGlobalMainSettings().edit().putString("dg_recognitionLanguage", lang == null ? "none" : lang).apply();
    }

    public static boolean isVoskRecognitionEnabled() {
        return !"none".equals(getRecognitionLanguage());
    }

    // ИИ-постобработка распознанного текста (через DevGramAiClient).
    public static boolean isRecognitionAiPostProcessing() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_recognitionAi", false);
    }

    public static void setRecognitionAiPostProcessing(boolean v) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("dg_recognitionAi", v).apply();
    }

    // Запоминать последнюю использованную камеру для кружков (сохраняет rearVideoMessages при флипе).
    public static boolean isRememberLastCamera() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_rememberLastCamera", false);
    }

    public static void setRememberLastCamera(boolean v) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("dg_rememberLastCamera", v).apply();
    }

    // Слайдер зума в камере кружков (переиспользуем базовый ZoomControlView).
    public static boolean isZoomSlider() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_zoomSlider", false);
    }

    public static void setZoomSlider(boolean v) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("dg_zoomSlider", v).apply();
    }

    // Статичный зум — не сбрасывать зум после жеста pinch.
    public static boolean isStaticZoom() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_staticZoom", false);
    }

    public static void setStaticZoom(boolean v) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("dg_staticZoom", v).apply();
    }

    // Формат времени сообщений с секундами. Флаг живёт в глобальных настройках (getGlobalMainSettings),
    // оттуда его читает LocaleController — как форма аватара. Держим тут обёртку для нашего меню.
    public static boolean isFormatWithSeconds() {
        return MessagesController.getGlobalMainSettings().getBoolean("formatWithSeconds", false);
    }

    public static void setFormatWithSeconds(boolean v) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("formatWithSeconds", v).apply();
    }

    // Сбор данных БЕЗ Google-SDK (они ломали запуск — см. reference_devgram_no_firebase_sdk):
    // своя телеметрия пишет анонимно в RTDB devgram-d03e4 через REST. Здесь просто дёргаем её,
    // когда тумблеры «Google» меняются.
    public static void applyFirebaseCollection() {
        try {
            DevGramTelemetry.onSettingsChanged();
        } catch (Throwable ignore) {
        }
    }

    // Сброс всех настроек мода к значениям по умолчанию.
    public static void resetToDefaults() {
        synchronized (sync) {
            sendReadPackets = true;
            sendOnlinePackets = true;
            sendUploadTyping = true;
            disableAds = false;
            localPremium = false;
            streaksEnabled = true;
            vpnEnabled = false;
            iosProfile = true;
            saveDeletedMessages = false;
            saveMessagesHistory = false;
            saveMedia = false;
            saveInBotChats = false;
            saveReadDate = false;
            saveLocalOnline = false;
            probeUsingOtherAccounts = false;
            analyticsEnabled = false;
            crashlyticsEnabled = true;
            disableNumberRounding = false;
            squareFab = true;
            glassMenu = true;
            hideEmojiCategories = true;
            forceSnow = false;
            centerTitle = false;
            displayGhostStatus = false;
            disableMarkdown = false;
            hideKeyboardOnScroll = true;
            disableGreetingSticker = false;
            addCommaAfterMention = true;
            swipeActions = DevGramSwipeAction.DEFAULT_ORDER;
            swipeActionsLoop = true;
            swipeActionsReversed = false;
            if (preferences != null) {
                preferences.edit().clear().apply();
            }
        }
        DevGramGhostSettings.clearCache();
        applyFirebaseCollection();
    }

    // --- экспорт/импорт настроек мода (JSON) ---
    public static String exportToJson() {
        org.json.JSONObject o = new org.json.JSONObject();
        try {
            o.put("_formatVersion", 2);
            DevGramGhostSettings.Settings ghost = DevGramGhostSettings.get(UserConfig.selectedAccount);
            o.put("sendReadPackets", ghost.sendReadMessages);
            o.put("sendReadStoryPackets", ghost.sendReadStories);
            o.put("sendOnlinePackets", ghost.sendOnline);
            o.put("sendUploadTyping", ghost.sendTyping);
            o.put("sendOfflineAfterOnline", ghost.sendOfflineAfterOnline);
            o.put("markReadAfterAction", ghost.markReadAfterAction);
            o.put("useScheduledMessages", ghost.useScheduledMessages);
            o.put("sendWithoutSound", ghost.sendWithoutSound);
            o.put("suggestGhostBeforeStory", ghost.suggestBeforeStory);
            o.put("useGlobalGhostConfig", DevGramGhostSettings.useGlobal());
            o.put("disableAds", disableAds);
            o.put("localPremium", localPremium);
            o.put("streaksEnabled", streaksEnabled);
            o.put("vpnEnabled", vpnEnabled);
            o.put("iosProfile", iosProfile);
            o.put("saveDeletedMessages", saveDeletedMessages);
            o.put("saveMessagesHistory", saveMessagesHistory);
            o.put("saveMedia", saveMedia);
            o.put("saveInBotChats", saveInBotChats);
            o.put("saveReadDate", saveReadDate);
            o.put("saveLocalOnline", saveLocalOnline);
            o.put("probeUsingOtherAccounts", probeUsingOtherAccounts);
            o.put("analyticsEnabled", analyticsEnabled);
            o.put("crashlyticsEnabled", crashlyticsEnabled);
            o.put("disableNumberRounding", disableNumberRounding);
            o.put("squareFab", squareFab);
            o.put("glassMenu", glassMenu);
            o.put("hideEmojiCategories", hideEmojiCategories);
            o.put("forceSnow", forceSnow);
            o.put("centerTitle", centerTitle);
            o.put("displayGhostStatus", displayGhostStatus);
            o.put("disableMarkdown", disableMarkdown);
            o.put("hideKeyboardOnScroll", hideKeyboardOnScroll);
            o.put("disableGreetingSticker", disableGreetingSticker);
            o.put("addCommaAfterMention", addCommaAfterMention);
            o.put("swipeActions", swipeActions);
            o.put("swipeActionsLoop", swipeActionsLoop);
            o.put("swipeActionsReversed", swipeActionsReversed);
            o.put("removeMessageTail", removeMessageTail);
            o.put("replaceEditedWithIcon", replaceEditedWithIcon);
            o.put("hideShareButton", hideShareButton);
            o.put("hideStickerTime", hideStickerTime);
            o.put("inlineCalc", inlineCalc);
            o.put("inlineMath", inlineMath);
            o.put("inlineCurrency", inlineCurrency);
            o.put("inlineUnits", inlineUnits);
            o.put("inlineTimeZones", inlineTimeZones);
            o.put("inlineTargetCurrency", inlineTargetCurrency);
            o.put("inlineAngleUnit", inlineAngleUnit);
            o.put("inlineMeasureSystem", inlineMeasureSystem);
            o.put("inlineDigitGrouping", inlineDigitGrouping);
            o.put("weatherInStories", weatherInStories);
            o.put("updateChannel", updateChannel);

            // v2: полный типизированный снимок пользовательских настроек DevGram. Старые
            // плоские поля выше оставлены, чтобы новые файлы можно было открыть в старых
            // версиях клиента. Токены, API-ключи, история ИИ и содержимое плагинов намеренно
            // не экспортируются.
            o.put("_devgramPreferences", exportPreferences(preferences, false));
            o.put("_globalPreferences", exportPreferences(MessagesController.getGlobalMainSettings(), true));
            o.put("_accountPreferences", exportAccountPreferences(
                    MessagesController.getMainSettings(UserConfig.selectedAccount)));
        } catch (Throwable ignore) {
        }
        return o.toString();
    }

    // Применить настройки из JSON. true — успех.
    public static boolean importFromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        try {
            org.json.JSONObject o = new org.json.JSONObject(json.trim());
            importPreferences(o.optJSONObject("_devgramPreferences"), preferences, false);
            importPreferences(o.optJSONObject("_globalPreferences"),
                    MessagesController.getGlobalMainSettings(), true);
            importAccountPreferences(o.optJSONObject("_accountPreferences"),
                    MessagesController.getMainSettings(UserConfig.selectedAccount));
            DevGramGhostSettings.setUseGlobal(o.optBoolean("useGlobalGhostConfig", DevGramGhostSettings.useGlobal()));
            setSendReadPackets(o.optBoolean("sendReadPackets", sendReadPackets));
            setSendOnlinePackets(o.optBoolean("sendOnlinePackets", sendOnlinePackets));
            setSendUploadTyping(o.optBoolean("sendUploadTyping", sendUploadTyping));
            DevGramGhostSettings.Settings ghost = DevGramGhostSettings.get(UserConfig.selectedAccount);
            ghost.sendReadStories = o.optBoolean("sendReadStoryPackets", ghost.sendReadStories);
            ghost.sendOfflineAfterOnline = o.optBoolean("sendOfflineAfterOnline", ghost.sendOfflineAfterOnline);
            ghost.markReadAfterAction = o.optBoolean("markReadAfterAction", ghost.markReadAfterAction);
            ghost.useScheduledMessages = o.optBoolean("useScheduledMessages", ghost.useScheduledMessages);
            ghost.sendWithoutSound = Math.max(0, Math.min(2, o.optInt("sendWithoutSound", ghost.sendWithoutSound)));
            ghost.suggestBeforeStory = o.optBoolean("suggestGhostBeforeStory", ghost.suggestBeforeStory);
            if (ghost.markReadAfterAction && ghost.useScheduledMessages) ghost.useScheduledMessages = false;
            ghost.save();
            setDisableAds(o.optBoolean("disableAds", disableAds));
            setLocalPremium(o.optBoolean("localPremium", localPremium));
            setStreaksEnabled(o.optBoolean("streaksEnabled", streaksEnabled));
            setVpnEnabled(o.optBoolean("vpnEnabled", vpnEnabled));
            setIosProfile(o.optBoolean("iosProfile", iosProfile));
            setSaveDeletedMessages(o.optBoolean("saveDeletedMessages", saveDeletedMessages));
            setSaveMessagesHistory(o.optBoolean("saveMessagesHistory", saveMessagesHistory));
            setSaveMedia(o.optBoolean("saveMedia", saveMedia));
            setSaveInBotChats(o.optBoolean("saveInBotChats", saveInBotChats));
            setSaveReadDate(o.optBoolean("saveReadDate", saveReadDate));
            setSaveLocalOnline(o.optBoolean("saveLocalOnline", saveLocalOnline));
            setProbeUsingOtherAccounts(o.optBoolean("probeUsingOtherAccounts", probeUsingOtherAccounts));
            setAnalyticsEnabled(o.optBoolean("analyticsEnabled", analyticsEnabled));
            setCrashlyticsEnabled(o.optBoolean("crashlyticsEnabled", crashlyticsEnabled));
            setDisableNumberRounding(o.optBoolean("disableNumberRounding", disableNumberRounding));
            setSquareFab(o.optBoolean("squareFab", squareFab));
            setGlassMenu(o.optBoolean("glassMenu", glassMenu));
            setHideEmojiCategories(o.optBoolean("hideEmojiCategories", hideEmojiCategories));
            setForceSnow(o.optBoolean("forceSnow", forceSnow));
            setCenterTitle(o.optBoolean("centerTitle", centerTitle));
            setDisplayGhostStatus(o.optBoolean("displayGhostStatus", displayGhostStatus));
            setDisableMarkdown(o.optBoolean("disableMarkdown", disableMarkdown));
            setHideKeyboardOnScroll(o.optBoolean("hideKeyboardOnScroll", hideKeyboardOnScroll));
            setDisableGreetingSticker(o.optBoolean("disableGreetingSticker", disableGreetingSticker));
            setAddCommaAfterMention(o.optBoolean("addCommaAfterMention", addCommaAfterMention));
            setSwipeActions(o.optString("swipeActions", swipeActions));
            setSwipeActionsLoop(o.optBoolean("swipeActionsLoop", swipeActionsLoop));
            setSwipeActionsReversed(o.optBoolean("swipeActionsReversed", swipeActionsReversed));
            setRemoveMessageTail(o.optBoolean("removeMessageTail", removeMessageTail));
            setReplaceEditedWithIcon(o.optBoolean("replaceEditedWithIcon", replaceEditedWithIcon));
            setHideShareButton(o.optBoolean("hideShareButton", hideShareButton));
            setHideStickerTime(o.optBoolean("hideStickerTime", hideStickerTime));
            setInlineCalc(o.optBoolean("inlineCalc", inlineCalc));
            setInlineBoolean("inlineMath", o.optBoolean("inlineMath", inlineMath));
            setInlineBoolean("inlineCurrency", o.optBoolean("inlineCurrency", inlineCurrency));
            setInlineBoolean("inlineUnits", o.optBoolean("inlineUnits", inlineUnits));
            setInlineBoolean("inlineTimeZones", o.optBoolean("inlineTimeZones", inlineTimeZones));
            setInlineBoolean("inlineDigitGrouping", o.optBoolean("inlineDigitGrouping", inlineDigitGrouping));
            setInlineTargetCurrency(o.optString("inlineTargetCurrency", inlineTargetCurrency));
            setInlineAngleUnit(o.optInt("inlineAngleUnit", inlineAngleUnit));
            setInlineMeasureSystem(o.optInt("inlineMeasureSystem", inlineMeasureSystem));
            setWeatherInStories(o.optBoolean("weatherInStories", weatherInStories));
            setUpdateChannel(o.optInt("updateChannel", updateChannel));

            // Несколько штатных полей Telegram кэшируются в static-полях. Синхронизируем
            // их сразу после восстановления, чтобы не требовать перезапуска приложения.
            SharedPreferences global = MessagesController.getGlobalMainSettings();
            SharedConfig.useSystemEmoji = global.getBoolean("useSystemEmoji", SharedConfig.useSystemEmoji);
            SharedConfig.forceDisableTabletMode = global.getBoolean(
                    "forceDisableTabletMode", SharedConfig.forceDisableTabletMode);
            if (global.contains("lite_mode6")) {
                LiteMode.setAllFlags(global.getInt("lite_mode6", LiteMode.getValue(true)));
            }
            DevGramGhostSettings.clearCache();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static final java.util.HashSet<String> BACKUP_GLOBAL_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "avatarCornersF", "folderTabsStyle", "formatWithSeconds", "stickerSize",
                    "useSystemEmoji", "forceDisableTabletMode", "lite_mode6", "defaultFolderId",
                    "senderMiniAvatars", "hideAllChatsTab", "fullRecentStickers",
                    "showArchivedStickers", "disableQuickReaction", "hideSavedMessagesTags",
                    "disableAutoplayNextVoice", "disableFlipPhotos", "disableLinkPreviewByDefault",
                    "disableMotionPhoto", "disablePlayVisibleVideoOnVolume",
                    "disableRecentFilesAttachment", "enableLastSeenDots", "hideAiEditor",
                    "hideSendAs", "mentionByName", "photoHasSticker", "replaceForward",
                    "satelliteDataSaving", "translate_to_language"));

    private static final java.util.HashSet<String> BACKUP_ACCOUNT_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList("translate_button", "translate_chat_button"));

    private static boolean isSensitiveBackupKey(String key) {
        if (key == null) return true;
        String lower = key.toLowerCase(java.util.Locale.US);
        return lower.contains("token") || lower.contains("password") || lower.contains("secret")
                || lower.startsWith("auth") || lower.contains("_auth") || "dg_aikey".equals(lower)
                || "dg_aiservices".equals(lower) || "dg_aihistory".equals(lower)
                || lower.startsWith("betatoken");
    }

    private static boolean isBackupGlobalKey(String key) {
        return key != null && !isSensitiveBackupKey(key)
                && (key.startsWith("dg_") || BACKUP_GLOBAL_KEYS.contains(key));
    }

    private static org.json.JSONObject exportAccountPreferences(SharedPreferences source)
            throws org.json.JSONException {
        return exportPreferences(source, 2);
    }

    private static org.json.JSONObject exportPreferences(SharedPreferences source, boolean global)
            throws org.json.JSONException {
        return exportPreferences(source, global ? 1 : 0);
    }

    private static org.json.JSONObject exportPreferences(SharedPreferences source, int scope)
            throws org.json.JSONException {
        org.json.JSONObject result = new org.json.JSONObject();
        if (source == null) return result;
        for (java.util.Map.Entry<String, ?> entry : source.getAll().entrySet()) {
            String key = entry.getKey();
            if (isSensitiveBackupKey(key)) continue;
            if (scope == 1 && !isBackupGlobalKey(key)) continue;
            if (scope == 2 && !BACKUP_ACCOUNT_KEYS.contains(key)) continue;
            org.json.JSONObject encoded = encodePreference(entry.getValue());
            if (encoded != null) result.put(key, encoded);
        }
        return result;
    }

    private static org.json.JSONObject encodePreference(Object value) throws org.json.JSONException {
        if (value == null) return null;
        org.json.JSONObject encoded = new org.json.JSONObject();
        if (value instanceof Boolean) {
            encoded.put("type", "boolean").put("value", value);
        } else if (value instanceof Integer) {
            encoded.put("type", "int").put("value", value);
        } else if (value instanceof Long) {
            encoded.put("type", "long").put("value", String.valueOf(value));
        } else if (value instanceof Float) {
            encoded.put("type", "float").put("value", String.valueOf(value));
        } else if (value instanceof String) {
            encoded.put("type", "string").put("value", value);
        } else if (value instanceof java.util.Set) {
            org.json.JSONArray values = new org.json.JSONArray();
            java.util.ArrayList<String> sorted = new java.util.ArrayList<>();
            for (Object item : (java.util.Set<?>) value) sorted.add(String.valueOf(item));
            java.util.Collections.sort(sorted);
            for (String item : sorted) values.put(item);
            encoded.put("type", "strings").put("value", values);
        } else {
            return null;
        }
        return encoded;
    }

    private static void importAccountPreferences(org.json.JSONObject data, SharedPreferences target)
            throws org.json.JSONException {
        importPreferences(data, target, 2);
    }

    private static void importPreferences(org.json.JSONObject data, SharedPreferences target,
                                          boolean global) throws org.json.JSONException {
        importPreferences(data, target, global ? 1 : 0);
    }

    private static void importPreferences(org.json.JSONObject data, SharedPreferences target, int scope)
            throws org.json.JSONException {
        if (data == null || target == null) return;
        SharedPreferences.Editor editor = target.edit();
        java.util.Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (isSensitiveBackupKey(key)) continue;
            if (scope == 1 && !isBackupGlobalKey(key)) continue;
            if (scope == 2 && !BACKUP_ACCOUNT_KEYS.contains(key)) continue;
            org.json.JSONObject encoded = data.optJSONObject(key);
            if (encoded == null) continue;
            String type = encoded.optString("type", "");
            Object value = encoded.opt("value");
            switch (type) {
                case "boolean": editor.putBoolean(key, encoded.optBoolean("value")); break;
                case "int": editor.putInt(key, encoded.optInt("value")); break;
                case "long": editor.putLong(key, Long.parseLong(String.valueOf(value))); break;
                case "float": editor.putFloat(key, Float.parseFloat(String.valueOf(value))); break;
                case "string": editor.putString(key, encoded.optString("value", "")); break;
                case "strings":
                    org.json.JSONArray array = encoded.optJSONArray("value");
                    if (array == null) break;
                    java.util.HashSet<String> strings = new java.util.HashSet<>();
                    for (int i = 0; i < array.length(); i++) strings.add(array.optString(i));
                    editor.putStringSet(key, strings);
                    break;
            }
        }
        editor.apply();
    }

    // Режим призрака активен, когда скрыты все три индикатора активности.
    public static boolean isGhostModeActive() {
        return DevGramGhostSettings.isActive(UserConfig.selectedAccount);
    }

    public static void setGhostMode(boolean enabled) {
        DevGramGhostSettings.setActive(UserConfig.selectedAccount, enabled);
    }

    public static void toggleGhostMode() {
        setGhostMode(!isGhostModeActive());
    }

    public static void setSendReadPackets(boolean v) {
        sendReadPackets = v;
        DevGramGhostSettings.Settings s = DevGramGhostSettings.get(UserConfig.selectedAccount);
        s.sendReadMessages = v;
        s.save();
    }

    public static void setSendOnlinePackets(boolean v) {
        sendOnlinePackets = v;
        DevGramGhostSettings.Settings s = DevGramGhostSettings.get(UserConfig.selectedAccount);
        s.sendOnline = v;
        s.save();
    }

    public static void setSendUploadTyping(boolean v) {
        sendUploadTyping = v;
        DevGramGhostSettings.Settings s = DevGramGhostSettings.get(UserConfig.selectedAccount);
        s.sendTyping = v;
        s.save();
    }

    public static void setDisableAds(boolean v) {
        disableAds = v;
        preferences.edit().putBoolean("disableAds", v).apply();
    }

    public static void setLocalPremium(boolean v) {
        localPremium = v;
        preferences.edit().putBoolean("localPremium", v).apply();
        // применяем сразу ко всем активным аккаунтам: разблокируем папки/лимиты и обновляем UI,
        // чтобы не требовался перезапуск приложения
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                UserConfig.getInstance(a).notifyPremiumChanged();
            }
        }
    }

    public static void setSaveDeletedMessages(boolean v) {
        saveDeletedMessages = v;
        preferences.edit().putBoolean("saveDeletedMessages", v).apply();
    }

    public static void setVpnEnabled(boolean v) {
        vpnEnabled = v;
        preferences.edit().putBoolean("vpnEnabled", v).apply();
    }

    public static void setIosProfile(boolean v) {
        iosProfile = v;
        preferences.edit().putBoolean("iosProfile", v).apply();
    }

    public static void setStreaksEnabled(boolean v) {
        streaksEnabled = v;
        preferences.edit().putBoolean("streaksEnabled", v).apply();
        // перерисовать имена, чтобы огоньки появились/исчезли сразу
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                }
            }
        });
    }

    public static void setSaveMessagesHistory(boolean v) {
        saveMessagesHistory = v;
        preferences.edit().putBoolean("saveMessagesHistory", v).apply();
    }

    public static void setSaveMedia(boolean v) {
        saveMedia = v;
        preferences.edit().putBoolean("saveMedia", v).apply();
    }

    public static void setSaveInBotChats(boolean v) {
        saveInBotChats = v;
        preferences.edit().putBoolean("saveInBotChats", v).apply();
    }

    public static void setSaveReadDate(boolean v) {
        saveReadDate = v; preferences.edit().putBoolean("saveReadDate", v).apply();
    }

    public static void setSaveLocalOnline(boolean v) {
        saveLocalOnline = v; preferences.edit().putBoolean("saveLocalOnline", v).apply();
    }

    public static void setSwipeActions(String value) {
        swipeActions = value == null ? "" : value;
        if (preferences != null) preferences.edit().putString("swipeActions", swipeActions).apply();
    }

    public static void setSwipeActionsLoop(boolean value) {
        swipeActionsLoop = value;
        if (preferences != null) preferences.edit().putBoolean("swipeActionsLoop", value).apply();
    }

    public static void setSwipeActionsReversed(boolean value) {
        swipeActionsReversed = value;
        if (preferences != null) preferences.edit().putBoolean("swipeActionsReversed", value).apply();
    }

    public static void setProbeUsingOtherAccounts(boolean v) {
        probeUsingOtherAccounts = v; preferences.edit().putBoolean("probeUsingOtherAccounts", v).apply();
    }

    public static boolean saveMediaPrivateChats() { return preferences.getBoolean("saveMediaPrivateChats", true); }
    public static boolean saveMediaPublicChannels() { return preferences.getBoolean("saveMediaPublicChannels", false); }
    public static boolean saveMediaPrivateChannels() { return preferences.getBoolean("saveMediaPrivateChannels", true); }
    public static boolean saveMediaPublicGroups() { return preferences.getBoolean("saveMediaPublicGroups", false); }
    public static boolean saveMediaPrivateGroups() { return preferences.getBoolean("saveMediaPrivateGroups", true); }
    public static void setMediaCategory(String key, boolean value) { preferences.edit().putBoolean(key, value).apply(); }
    public static long mediaCellularLimit() { return preferences.getLong("mediaCellularLimit", 16L * 1024 * 1024); }
    public static long mediaWifiLimit() { return preferences.getLong("mediaWifiLimit", 64L * 1024 * 1024); }
    public static void setMediaCellularLimit(long value) { preferences.edit().putLong("mediaCellularLimit", value).apply(); }
    public static void setMediaWifiLimit(long value) { preferences.edit().putLong("mediaWifiLimit", value).apply(); }
    public static String savedMediaFolder() { return preferences.getString("savedMediaFolder", "devgram_saved"); }
    public static void setSavedMediaFolder(String value) {
        preferences.edit().putString("savedMediaFolder", value == null || value.isEmpty() ? "devgram_saved" : value).apply();
    }
    /** Bytes; Long.MAX_VALUE means unlimited. */
    public static long mediaCacheLimit() { return preferences.getLong("mediaCacheLimit", Long.MAX_VALUE); }
    public static void setMediaCacheLimit(long value) { preferences.edit().putLong("mediaCacheLimit", value).apply(); }

    // Пометка удалённого/изменённого сообщения в строке времени.
    public static String getDeletedMark() {
        return "🗑";
    }

    // DevGram: полупрозрачность удалённых сообщений (порт AyuGram semiTransparentDeletedMessages)
    public static boolean getSemiTransparentDeleted() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_semiTransparentDeleted", false);
    }

    public static void setSemiTransparentDeleted(boolean v) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("dg_semiTransparentDeleted", v).apply();
    }

    public static String getEditedMark() {
        return LocaleController.getString("EditedMessage", R.string.EditedMessage);
    }

    // --- read gate ---
    public static void setAllowReadPacket(boolean val, int resetAfter) {
        synchronized (readSync) {
            allowReadVal = val;
            allowReadResetAfter = resetAfter;
        }
    }

    public static boolean getAllowReadPacket() {
        if (sendReadPackets) {
            return true;
        }
        synchronized (readSync) {
            if (allowReadResetAfter == -1) {
                return allowReadVal;
            }
            if (allowReadResetAfter > 0) {
                allowReadResetAfter -= 1;
                boolean cur = allowReadVal;
                if (allowReadResetAfter == 0) {
                    allowReadVal = false;
                }
                return cur;
            }
            return allowReadVal;
        }
    }
}

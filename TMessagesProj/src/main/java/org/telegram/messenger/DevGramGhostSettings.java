/*
 * DevGram: расширенные настройки Ghost Mode.
 * Модель совместима по поведению с AyuGram, но хранится в собственном pref DevGram.
 */
package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;

import java.util.concurrent.ConcurrentHashMap;

public final class DevGramGhostSettings {
    private static final long GLOBAL = -1L;
    private static final ConcurrentHashMap<Long, Settings> cache = new ConcurrentHashMap<>();

    private DevGramGhostSettings() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("devgram_ghost", Activity.MODE_PRIVATE);
    }

    public static final class Settings {
        public final long userId;
        public boolean sendReadMessages;
        public boolean sendReadStories;
        public boolean sendOnline;
        public boolean sendTyping;
        public boolean sendOfflineAfterOnline;
        public boolean markReadAfterAction;
        public boolean useScheduledMessages;
        public boolean suggestBeforeStory;
        public boolean readLocked;
        public boolean storiesLocked;
        public boolean onlineLocked;
        public boolean typingLocked;
        public boolean offlineLocked;
        /** 0 — обычно, 1 — без звука в Ghost Mode, 2 — всегда без звука. */
        public int sendWithoutSound;

        private Settings(long userId) {
            this.userId = userId;
            final String suffix = userId == GLOBAL ? "" : "_" + userId;
            SharedPreferences p = prefs();
            // Старые ключи без суффикса автоматически становятся глобальными настройками.
            boolean legacyRead = userId == GLOBAL ? DevGramConfig.sendReadPackets : true;
            boolean legacyOnline = userId == GLOBAL ? DevGramConfig.sendOnlinePackets : true;
            boolean legacyTyping = userId == GLOBAL ? DevGramConfig.sendUploadTyping : true;
            sendReadMessages = p.getBoolean("sendReadPackets" + suffix, legacyRead);
            sendReadStories = p.getBoolean("sendReadStoryPackets" + suffix, true);
            sendOnline = p.getBoolean("sendOnlinePackets" + suffix, legacyOnline);
            sendTyping = p.getBoolean("sendUploadTyping" + suffix, legacyTyping);
            sendOfflineAfterOnline = p.getBoolean("sendOfflineAfterOnline" + suffix, false);
            markReadAfterAction = p.getBoolean("markReadAfterAction" + suffix, true);
            useScheduledMessages = p.getBoolean("useScheduledMessages" + suffix, false);
            suggestBeforeStory = p.getBoolean("suggestGhostBeforeStory" + suffix, true);
            sendWithoutSound = p.getInt("sendWithoutSound" + suffix, 0);
            readLocked = p.getBoolean("sendReadPacketsLocked" + suffix, false);
            storiesLocked = p.getBoolean("sendReadStoryPacketsLocked" + suffix, false);
            onlineLocked = p.getBoolean("sendOnlinePacketsLocked" + suffix, false);
            typingLocked = p.getBoolean("sendUploadTypingLocked" + suffix, false);
            offlineLocked = p.getBoolean("sendOfflineAfterOnlineLocked" + suffix, false);
        }

        public void save() {
            final String suffix = userId == GLOBAL ? "" : "_" + userId;
            prefs().edit()
                    .putBoolean("sendReadPackets" + suffix, sendReadMessages)
                    .putBoolean("sendReadStoryPackets" + suffix, sendReadStories)
                    .putBoolean("sendOnlinePackets" + suffix, sendOnline)
                    .putBoolean("sendUploadTyping" + suffix, sendTyping)
                    .putBoolean("sendOfflineAfterOnline" + suffix, sendOfflineAfterOnline)
                    .putBoolean("markReadAfterAction" + suffix, markReadAfterAction)
                    .putBoolean("useScheduledMessages" + suffix, useScheduledMessages)
                    .putBoolean("suggestGhostBeforeStory" + suffix, suggestBeforeStory)
                    .putInt("sendWithoutSound" + suffix, sendWithoutSound)
                    .putBoolean("sendReadPacketsLocked" + suffix, readLocked)
                    .putBoolean("sendReadStoryPacketsLocked" + suffix, storiesLocked)
                    .putBoolean("sendOnlinePacketsLocked" + suffix, onlineLocked)
                    .putBoolean("sendUploadTypingLocked" + suffix, typingLocked)
                    .putBoolean("sendOfflineAfterOnlineLocked" + suffix, offlineLocked)
                    .apply();
            if (userId == GLOBAL) {
                DevGramConfig.sendReadPackets = sendReadMessages;
                DevGramConfig.sendOnlinePackets = sendOnline;
                DevGramConfig.sendUploadTyping = sendTyping;
            }
            int account = UserConfig.selectedAccount;
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account)
                    .postNotificationName(NotificationCenter.mainUserInfoChanged));
        }
    }

    public static boolean useGlobal() {
        return prefs().getBoolean("useGlobalConfig", true);
    }

    public static void setUseGlobal(boolean value) {
        prefs().edit().putBoolean("useGlobalConfig", value).apply();
        int account = UserConfig.selectedAccount;
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account)
                .postNotificationName(NotificationCenter.mainUserInfoChanged));
    }

    public static long accountUserId(int account) {
        long id = UserConfig.getInstance(account).getClientUserId();
        return id == 0 ? GLOBAL : id;
    }

    public static Settings get(int account) {
        return getForUser(useGlobal() ? GLOBAL : accountUserId(account));
    }

    public static Settings getForUser(long userId) {
        Settings value = cache.get(userId);
        if (value == null) {
            value = new Settings(userId);
            Settings old = cache.putIfAbsent(userId, value);
            if (old != null) value = old;
        }
        return value;
    }

    public static void clearCache() {
        cache.clear();
    }

    public static boolean isActive(int account) {
        Settings s = get(account);
        if (s.sendReadMessages && !s.readLocked) return false;
        if (s.sendReadStories && !s.storiesLocked) return false;
        if (s.sendOnline && !s.onlineLocked) return false;
        if (s.sendTyping && !s.typingLocked) return false;
        return s.sendOfflineAfterOnline || s.offlineLocked;
    }

    public static void setActive(int account, boolean active) {
        Settings s = get(account);
        if (!s.readLocked) s.sendReadMessages = !active;
        if (!s.storiesLocked) s.sendReadStories = !active;
        if (!s.onlineLocked) s.sendOnline = !active;
        if (!s.typingLocked) s.sendTyping = !active;
        if (!s.offlineLocked) s.sendOfflineAfterOnline = active;
        s.save();
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces,
                    MessagesController.UPDATE_MASK_STATUS);
        });
    }

    public static boolean sendSilently(int account) {
        Settings s = get(account);
        return s.sendWithoutSound == 2 || (s.sendWithoutSound == 1 && isActive(account));
    }

    /** 0=default, 1=never send read/typing, 2=always send even in Ghost Mode. */
    public static int getDialogOverride(int account, long dialogId, String kind) {
        return prefs().getInt("dialog_" + kind + "_" + accountUserId(account) + "_" + dialogId, 0);
    }

    public static void setDialogOverride(int account, long dialogId, String kind, int value) {
        prefs().edit().putInt("dialog_" + kind + "_" + accountUserId(account) + "_" + dialogId, value).apply();
    }

    public static boolean shouldSendRead(int account, long dialogId) {
        int override = getDialogOverride(account, dialogId, "read");
        return override == 2 || (override == 0 && get(account).sendReadMessages);
    }

    public static boolean shouldSendTyping(int account, long dialogId) {
        int override = getDialogOverride(account, dialogId, "typing");
        return override == 2 || (override == 0 && get(account).sendTyping);
    }
}

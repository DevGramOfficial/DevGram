package org.telegram.messenger;

import android.content.SharedPreferences;
import android.os.Build;

/**
 * Settings shared by the iOS chat appearance and its live previews.
 *
 * Keep these in global_main_settings: chat UI objects are created before the DevGram settings
 * singleton on some cold starts, while this preferences file is already available there.
 */
public final class DevGramIOSConfig {
    public static final int NOTIFICATION_SOUND_DISABLED = 0;
    public static final int NOTIFICATION_SOUND_TELEGRAM = 1;
    public static final int NOTIFICATION_SOUND_IOS = 2;

    private DevGramIOSConfig() {
    }

    private static SharedPreferences preferences() {
        return MessagesController.getGlobalMainSettings();
    }

    private static boolean iosDesignDefault() {
        return DevGramConfig.getDesignMode() == DevGramConfig.DESIGN_MODE_IOS;
    }

    public static boolean centerChatTitle() {
        return preferences().getBoolean("dg_ios_center_chat_title",
                preferences().getBoolean("dg_centerTitle", DevGramConfig.centerTitle));
    }

    public static void setCenterChatTitle(boolean value) {
        preferences().edit()
                .putBoolean("dg_ios_center_chat_title", value)
                .putBoolean("dg_centerTitle", value)
                .apply();
        // Compatibility with the older DevGram appearance toggle.
        DevGramConfig.setCenterTitle(value);
    }

    public static boolean adaptiveTitleWidth() {
        return preferences().getBoolean("dg_ios_adaptive_title_width", true);
    }

    public static void setAdaptiveTitleWidth(boolean value) {
        preferences().edit().putBoolean("dg_ios_adaptive_title_width", value).apply();
    }

    public static boolean showUnreadCounter() {
        return preferences().getBoolean("dg_ios_show_unread_counter", centerChatTitle());
    }

    public static void setShowUnreadCounter(boolean value) {
        preferences().edit().putBoolean("dg_ios_show_unread_counter", value).apply();
    }

    public static boolean iosUnreadCounter() {
        return preferences().getBoolean("dg_ios_unread_counter_style", centerChatTitle());
    }

    public static void setIosUnreadCounter(boolean value) {
        preferences().edit().putBoolean("dg_ios_unread_counter_style", value).apply();
    }

    public static boolean iosInputPanel() {
        return preferences().getBoolean("dg_ios_input_panel", iosDesignDefault());
    }

    public static void setIosInputPanel(boolean value) {
        preferences().edit().putBoolean("dg_ios_input_panel", value).apply();
    }

    public static int notificationSound() {
        return preferences().getInt("dg_ios_notification_sound",
                iosDesignDefault() ? NOTIFICATION_SOUND_IOS : NOTIFICATION_SOUND_TELEGRAM);
    }

    public static void setNotificationSound(int value) {
        value = Math.max(NOTIFICATION_SOUND_DISABLED, Math.min(NOTIFICATION_SOUND_IOS, value));
        preferences().edit().putInt("dg_ios_notification_sound", value).apply();
    }

    public static boolean iosMessageMenu() {
        return preferences().getBoolean("dg_ios_message_menu", iosDesignDefault());
    }

    public static void setIosMessageMenu(boolean value) {
        preferences().edit().putBoolean("dg_ios_message_menu", value).apply();
    }

    public static boolean nativeMenuBlur() {
        return preferences().getBoolean("dg_ios_message_menu_native_blur",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
    }

    public static void setNativeMenuBlur(boolean value) {
        preferences().edit().putBoolean("dg_ios_message_menu_native_blur", value).apply();
    }

    public static boolean showWholeAlbumInMenu() {
        return preferences().getBoolean("dg_ios_message_menu_whole_album", false);
    }

    public static void setShowWholeAlbumInMenu(boolean value) {
        preferences().edit().putBoolean("dg_ios_message_menu_whole_album", value).apply();
    }

    public static boolean rememberKeyboardForMenu() {
        return preferences().getBoolean("dg_ios_message_menu_remember_keyboard", true);
    }

    public static void setRememberKeyboardForMenu(boolean value) {
        preferences().edit().putBoolean("dg_ios_message_menu_remember_keyboard", value).apply();
    }

    public static boolean compactMenuActions() {
        return preferences().getBoolean("dg_ios_message_menu_compact", false);
    }

    public static void setCompactMenuActions(boolean value) {
        preferences().edit().putBoolean("dg_ios_message_menu_compact", value).apply();
    }
}

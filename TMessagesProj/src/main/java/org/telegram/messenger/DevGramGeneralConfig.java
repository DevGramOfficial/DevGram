package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

/** Settings ported from exteraGram's GeneralPreferencesActivity. */
public final class DevGramGeneralConfig {
    private DevGramGeneralConfig() {}

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    private static boolean bool(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    private static void put(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    public static int getTranslationProvider() { return Math.max(0, Math.min(3, prefs().getInt("dg_translationProvider", 0))); }
    public static void setTranslationProvider(int value) { prefs().edit().putInt("dg_translationProvider", Math.max(0, Math.min(3, value))).apply(); }
    public static int getTranslationFormality() { return Math.max(0, Math.min(2, prefs().getInt("dg_translationFormality", 0))); }
    public static void setTranslationFormality(int value) { prefs().edit().putInt("dg_translationFormality", Math.max(0, Math.min(2, value))).apply(); }

    public static boolean isRelativeLastSeen() { return bool("dg_relativeLastSeen", false); }
    public static void setRelativeLastSeen(boolean value) { put("dg_relativeLastSeen", value); }
    public static boolean isInAppVibration() { return bool("dg_inAppVibration", true); }
    public static void setInAppVibration(boolean value) { put("dg_inAppVibration", value); }
    public static boolean isFilterZalgo() { return bool("dg_filterZalgo", true); }
    public static void setFilterZalgo(boolean value) { put("dg_filterZalgo", value); }
    public static boolean isUseYandexMaps() { return bool("dg_useYandexMaps", false); }
    public static void setUseYandexMaps(boolean value) { put("dg_useYandexMaps", value); }

    public static int getDownloadSpeedBoost() { return prefs().getInt("dg_downloadSpeedBoost", 0); }
    public static void setDownloadSpeedBoost(int value) { prefs().edit().putInt("dg_downloadSpeedBoost", Math.max(0, Math.min(2, value))).apply(); }
    public static boolean isUploadSpeedBoost() { return bool("dg_uploadSpeedBoost", false); }
    public static void setUploadSpeedBoost(boolean value) { put("dg_uploadSpeedBoost", value); }

    public static String getCustomSavePath() { return prefs().getString("dg_customSavePath", "DevGram"); }
    public static void setCustomSavePath(String value) { prefs().edit().putString("dg_customSavePath", value == null ? "" : value.trim()).apply(); }
    public static String mediaFolder(String defaultDirectory) {
        String path = getCustomSavePath();
        return TextUtils.isEmpty(path) ? defaultDirectory : new java.io.File(defaultDirectory, path).getPath();
    }

    public static boolean isHidePhoneNumber() { return bool("dg_hidePhoneNumber", false); }
    public static void setHidePhoneNumber(boolean value) { put("dg_hidePhoneNumber", value); }
    public static int getShowIdAndDc() { return prefs().getInt("dg_showIdAndDc", 1); }
    public static void setShowIdAndDc(int value) { prefs().edit().putInt("dg_showIdAndDc", Math.max(0, Math.min(2, value))).apply(); }

    public static boolean isHideArchiveFolder() { return bool("dg_hideArchiveFolder", false); }
    public static void setHideArchiveFolder(boolean value) { put("dg_hideArchiveFolder", value); }
    public static boolean isArchiveOnPull() { return bool("dg_archiveOnPull", false); }
    public static void setArchiveOnPull(boolean value) { put("dg_archiveOnPull", value); }
    public static boolean isDisableUnarchiveSwipe() { return bool("dg_disableUnarchiveSwipe", true); }
    public static void setDisableUnarchiveSwipe(boolean value) { put("dg_disableUnarchiveSwipe", value); }

    public static void applyHapticFeedbackSetting(View view) {
        if (view == null || isInAppVibration()) return;
        view.setHapticFeedbackEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyHapticFeedbackSetting(group.getChildAt(i));
            }
        }
    }
}

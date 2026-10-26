package org.telegram.messenger.devgram.utils.ui;

import android.os.Build;

import org.telegram.messenger.ApplicationLoader;

/** Системные цвета Material You для плеера. */
public final class MonetUtils {

    private MonetUtils() {
    }

    public static int getSecondaryContainerColor(boolean isDark) {
        if (Build.VERSION.SDK_INT >= 34) {
            return ApplicationLoader.applicationContext.getColor(isDark ? android.R.color.system_secondary_container_dark : android.R.color.system_secondary_container_light);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            return ApplicationLoader.applicationContext.getColor(isDark ? android.R.color.system_accent2_700 : android.R.color.system_accent2_100);
        }
        return 0;
    }

    public static int getOnSecondaryContainerColor(boolean isDark) {
        if (Build.VERSION.SDK_INT >= 34) {
            return ApplicationLoader.applicationContext.getColor(isDark ? android.R.color.system_on_secondary_container_dark : android.R.color.system_on_secondary_container_light);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            return ApplicationLoader.applicationContext.getColor(isDark ? android.R.color.system_accent2_100 : android.R.color.system_accent2_900);
        }
        return 0;
    }
}

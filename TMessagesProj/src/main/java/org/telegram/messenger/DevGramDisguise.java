package org.telegram.messenger;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import org.telegram.ui.LauncherIconController;
import org.telegram.ui.LauncherIconController.LauncherIcon;

import java.util.ArrayList;
import java.util.List;

// DevGram: маскировка приложения — подмена иконки И названия в списке приложений
// (например, DevGram выглядит как «Калькулятор»). Реализовано поверх штатного
// механизма activity-alias: у маскировочного alias свой android:label и android:icon,
// LauncherIconController включает нужный alias и гасит остальные.
//
// Добавить новую маску: 1) иконка (drawable/mipmap + adaptive), 2) activity-alias в
// манифесте с android:label и android:enabled="false", 3) запись в enum LauncherIcon
// с disguise=true (ключ = имя alias). Здесь править ничего не нужно — маски берутся
// из enum по флагу disguise.
public class DevGramDisguise {

    // Все доступные маскировки (иконки с флагом disguise).
    public static List<LauncherIcon> masks() {
        List<LauncherIcon> result = new ArrayList<>();
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (icon.disguise) {
                result.add(icon);
            }
        }
        return result;
    }

    // Текущая активная иконка (маска или обычная).
    public static LauncherIcon current() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (LauncherIconController.isEnabled(icon)) {
                return icon;
            }
        }
        return LauncherIcon.DEFAULT;
    }

    // Включена ли маскировка (иконка ≠ обычная DevGram).
    public static boolean isDisguised() {
        LauncherIcon cur = current();
        return cur != null && cur.disguise;
    }

    // Применить маску (иконка + имя меняются в лаунчере).
    public static void apply(LauncherIcon mask) {
        if (mask == null) {
            clear();
            return;
        }
        LauncherIconController.setIcon(mask);
    }

    // Снять маскировку — вернуть обычный DevGram.
    public static void clear() {
        LauncherIconController.setIcon(LauncherIcon.DEFAULT);
    }

    // Наша заготовка-иконка (цельный drawable) для превью, когда реального приложения нет.
    public static int previewRes(LauncherIcon mask) {
        switch (mask.key) {
            case "CalculatorIcon": return R.drawable.devgram_mask_calc_full;
            case "NotesIcon":      return R.drawable.devgram_mask_notes_full;
            case "WeatherIcon":    return R.drawable.devgram_mask_weather_full;
            case "ClockIcon":      return R.drawable.devgram_mask_clock_full;
            case "SettingsIcon":   return R.drawable.devgram_mask_settings_full;
            default:               return R.mipmap.icon_01_launcher;
        }
    }

    // Известные пакеты реальных приложений на телефоне для каждой маски — чтобы в меню
    // показать иконку/имя ИМЕННО так, как они выглядят у пользователя (Google/Samsung/MIUI/…).
    private static String[] systemPackagesFor(LauncherIcon mask) {
        switch (mask.key) {
            case "CalculatorIcon":
                return new String[]{"com.google.android.calculator", "com.android.calculator2",
                        "com.sec.android.app.popupcalculator", "com.miui.calculator",
                        "com.oneplus.calculator", "com.coloros.calculator"};
            case "NotesIcon":
                return new String[]{"com.google.android.keep", "com.samsung.android.app.notes",
                        "com.miui.notes", "com.coloros.note", "com.nearme.note"};
            case "WeatherIcon":
                return new String[]{"com.google.android.apps.weather", "com.sec.android.daemonapp",
                        "com.miui.weather2", "com.coloros.weather2", "com.oneplus.weather"};
            case "ClockIcon":
                return new String[]{"com.google.android.deskclock", "com.android.deskclock",
                        "com.sec.android.app.clockpackage", "com.coloros.alarmclock", "com.oneplus.deskclock"};
            case "SettingsIcon":
                return new String[]{"com.android.settings"};
            default:
                return new String[0];
        }
    }

    // Первый реально установленный пакет-аналог маски (или null).
    private static String installedPackageFor(Context ctx, LauncherIcon mask) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : systemPackagesFor(mask)) {
            try {
                pm.getApplicationInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignore) {
            }
        }
        return null;
    }

    // Реальная иконка приложения-аналога с телефона (или null, если такого приложения нет —
    // тогда в меню показываем нашу заготовку).
    public static Drawable realIcon(Context ctx, LauncherIcon mask) {
        String pkg = installedPackageFor(ctx, mask);
        if (pkg == null) return null;
        try {
            return ctx.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    // Реальное имя приложения-аналога с телефона (или наш дефолтный заголовок маски).
    public static CharSequence realLabel(Context ctx, LauncherIcon mask) {
        String pkg = installedPackageFor(ctx, mask);
        if (pkg != null) {
            try {
                return ctx.getPackageManager().getApplicationLabel(
                        ctx.getPackageManager().getApplicationInfo(pkg, 0));
            } catch (Exception ignore) {
            }
        }
        return DevGramLocale.getString(mask.title);
    }
}

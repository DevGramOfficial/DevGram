package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.drawable.icon_01_background_sa, R.mipmap.icon_01_foreground_sa, R.string.AppIconDefault),
        // DevGram: свои иконки. Ключи alias старые (Original/Vintage/…) — у кого была выбрана старая иконка,
        // после обновления получит новую на том же alias, а не пропавшую иконку в лаунчере.
        ZIMA("OriginalIcon", R.mipmap.icon_dg_zima_layer, R.mipmap.icon_dg_zima_layer, R.string.DevGramAppIconZima),
        MONET_LIGHT("VintageIcon", R.mipmap.icon_dg_monet_light_layer, R.mipmap.icon_dg_monet_light_layer, R.string.DevGramAppIconMonetLight),
        MONET_DARK("AquaIcon", R.mipmap.icon_dg_monet_dark_layer, R.mipmap.icon_dg_monet_dark_layer, R.string.DevGramAppIconMonetDark),
        SUNSET("PremiumIcon", R.mipmap.icon_dg_sunset_layer, R.mipmap.icon_dg_sunset_layer, R.string.DevGramAppIconSunset),
        AMETHYST("TurboIcon", R.mipmap.icon_dg_amethyst_layer, R.mipmap.icon_dg_amethyst_layer, R.string.DevGramAppIconAmethyst),
        AURORE("NoxIcon", R.mipmap.icon_dg_aurore_layer, R.mipmap.icon_dg_aurore_layer, R.string.DevGramAppIconAurore),
        GOOGLE("AdaptiveIcon", R.mipmap.icon_dg_google_layer, R.mipmap.icon_dg_google_layer, R.string.DevGramAppIconGoogle),
        GOOGLE26("Google26Icon", R.mipmap.icon_dg_google26_layer, R.mipmap.icon_dg_google26_layer, R.string.DevGramAppIconGoogle26),
        // DevGram: маскировка — alias с собственным android:label (меняет и иконку, и имя приложения).
        // disguise=true => не показывается в стоковом premium-селекторе иконок, только в разделе «Маскировка».
        CALCULATOR("CalculatorIcon", R.drawable.devgram_mask_calc_bg, R.drawable.devgram_mask_calc_fg, R.string.DevGramMaskCalculator, false, true),
        NOTES("NotesIcon", R.drawable.devgram_mask_notes_bg, R.drawable.devgram_mask_notes_fg, R.string.DevGramMaskNotes, false, true),
        WEATHER("WeatherIcon", R.drawable.devgram_mask_weather_bg, R.drawable.devgram_mask_weather_fg, R.string.DevGramMaskWeather, false, true),
        CLOCK("ClockIcon", R.drawable.devgram_mask_clock_bg, R.drawable.devgram_mask_clock_fg, R.string.DevGramMaskClock, false, true),
        SETTINGS_MASK("SettingsIcon", R.drawable.devgram_mask_settings_bg, R.drawable.devgram_mask_settings_fg, R.string.DevGramMaskSettings, false, true);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;
        public final boolean disguise; // DevGram

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this(key, background, foreground, title, premium, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium, boolean disguise) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
            this.disguise = disguise;
        }
    }
}

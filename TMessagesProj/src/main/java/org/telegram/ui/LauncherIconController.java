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

    // DevGram: иконка alias'а «как есть» из ресурсов. PackageManager.getActivityIcon для ВКЛЮЧЁННОГО
    // компонента на Samsung отдаёт иконку уже с темой лаунчера (серая рамка вокруг) — в приложении
    // она тогда выглядит иначе, чем остальные. Грузим ресурс сами: система лишь обрезает её по форме.
    public static android.graphics.drawable.Drawable loadIcon(Context ctx, LauncherIcon icon) {
        try {
            android.content.pm.ActivityInfo info = ctx.getPackageManager().getActivityInfo(icon.getComponentName(ctx),
                    PackageManager.MATCH_DISABLED_COMPONENTS);
            int res = info.getIconResource();
            if (res != 0) {
                return ctx.getDrawable(res);
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
        applySplashTheme(LaunchActivity.instance, icon);
    }

    // DevGram: текущая иконка приложения
    public static LauncherIcon current() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (icon != LauncherIcon.DEFAULT && isEnabled(icon)) {
                return icon;
            }
        }
        return LauncherIcon.DEFAULT;
    }

    // DevGram: заставка при следующих запусках — с выбранной иконкой (система берёт её из темы, а не из alias).
    // При маскировке — основная иконка DevGram.
    public static void applySplashTheme(android.app.Activity activity) {
        try {
            applySplashTheme(activity, current());
        } catch (Throwable ignore) {
        }
    }

    private static void applySplashTheme(android.app.Activity activity, LauncherIcon icon) {
        if (activity == null || android.os.Build.VERSION.SDK_INT < 33) {
            return;
        }
        int theme;
        switch (icon.disguise ? LauncherIcon.DEFAULT : icon) {
            case ZIMA: theme = R.style.Theme_TMessages_Start_DgIconZima; break;
            case MONET: theme = R.style.Theme_TMessages_Start_DgIconMonet; break;
            case SUNSET: theme = R.style.Theme_TMessages_Start_DgIconSunset; break;
            case AMETHYST: theme = R.style.Theme_TMessages_Start_DgIconAmethyst; break;
            case AURORE: theme = R.style.Theme_TMessages_Start_DgIconAurore; break;
            case GOOGLE: theme = R.style.Theme_TMessages_Start_DgIconGoogle; break;
            case GOOGLE26: theme = R.style.Theme_TMessages_Start_DgIconGoogle26; break;
            case SUP_SYNTHWAVE: theme = R.style.Theme_TMessages_Start_DgIconSupSynthwave; break;
            case SUP_MATRIX: theme = R.style.Theme_TMessages_Start_DgIconSupMatrix; break;
            case SUP_PIXEL: theme = R.style.Theme_TMessages_Start_DgIconSupPixel; break;
            case SUP_HOLO: theme = R.style.Theme_TMessages_Start_DgIconSupHolo; break;
            case SUP_GLITCH: theme = R.style.Theme_TMessages_Start_DgIconSupGlitch; break;
            case SUP_SPACE: theme = R.style.Theme_TMessages_Start_DgIconSupSpace; break;
            case SUP_TERMINAL: theme = R.style.Theme_TMessages_Start_DgIconSupTerminal; break;
            case SUP_BLUEPRINT: theme = R.style.Theme_TMessages_Start_DgIconSupBlueprint; break;
            default: theme = R.style.Theme_TMessages_Start; break;
        }
        try {
            activity.getSplashScreen().setSplashScreenTheme(theme);
        } catch (Throwable ignore) {
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.drawable.icon_01_background_sa, R.mipmap.icon_01_foreground_sa, R.string.AppIconDefault),
        // DevGram: свои иконки. В селекторе: фон прозрачный, передний план — картинка целиком (fullImage).
        // Ключи alias старые (Original/Vintage/…) — у кого была выбрана старая иконка,
        // после обновления получит новую на том же alias, а не пропавшую иконку в лаунчере.
        ZIMA("OriginalIcon", R.drawable.transparent, R.drawable.icon_dg_zima_preview, R.string.DevGramAppIconZima),
        // Monet: светлая картинка в mipmap-*, тёмная в mipmap-night-* — система сама переключает по теме
        MONET("VintageIcon", R.drawable.transparent, R.drawable.icon_dg_monet_preview, R.string.DevGramAppIconMonet),
        SUNSET("PremiumIcon", R.drawable.transparent, R.drawable.icon_dg_sunset_preview, R.string.DevGramAppIconSunset),
        AMETHYST("TurboIcon", R.drawable.transparent, R.drawable.icon_dg_amethyst_preview, R.string.DevGramAppIconAmethyst),
        AURORE("NoxIcon", R.drawable.transparent, R.drawable.icon_dg_aurore_preview, R.string.DevGramAppIconAurore),
        GOOGLE("AdaptiveIcon", R.drawable.transparent, R.drawable.icon_dg_google_preview, R.string.DevGramAppIconGoogle),
        GOOGLE26("Google26Icon", R.drawable.transparent, R.drawable.icon_dg_google26_preview, R.string.DevGramAppIconGoogle26),
        // DevGram: эксклюзивные иконки поддержавших (видны и выбираются только со значком)
        SUP_SYNTHWAVE("SupGoldIcon", R.drawable.transparent, R.drawable.icon_dg_sup_synthwave_preview, R.string.DevGramAppIconSupSynthwave, false, false, true),
        SUP_MATRIX("SupNeonIcon", R.drawable.transparent, R.drawable.icon_dg_sup_matrix_preview, R.string.DevGramAppIconSupMatrix, false, false, true),
        SUP_PIXEL("SupEmeraldIcon", R.drawable.transparent, R.drawable.icon_dg_sup_pixel_preview, R.string.DevGramAppIconSupPixel, false, false, true),
        SUP_HOLO("SupRubyIcon", R.drawable.transparent, R.drawable.icon_dg_sup_holo_preview, R.string.DevGramAppIconSupHolo, false, false, true),
        SUP_GLITCH("SupCarbonIcon", R.drawable.transparent, R.drawable.icon_dg_sup_glitch_preview, R.string.DevGramAppIconSupGlitch, false, false, true),
        SUP_SPACE("SupNightIcon", R.drawable.transparent, R.drawable.icon_dg_sup_space_preview, R.string.DevGramAppIconSupSpace, false, false, true),
        SUP_TERMINAL("SupTerminalIcon", R.drawable.transparent, R.drawable.icon_dg_sup_terminal_preview, R.string.DevGramAppIconSupTerminal, false, false, true),
        SUP_BLUEPRINT("SupBlueprintIcon", R.drawable.transparent, R.drawable.icon_dg_sup_blueprint_preview, R.string.DevGramAppIconSupBlueprint, false, false, true),
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
        public final boolean supporter; // DevGram: только для поддержавших

        // DevGram: картинка-превью целиком на всю карточку селектора (без вылета за края)
        public boolean isFullImage() {
            return background == R.drawable.transparent;
        }

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
            this(key, background, foreground, title, premium, disguise, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium, boolean disguise, boolean supporter) {
            this.supporter = supporter;
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
            this.disguise = disguise;
        }
    }
}

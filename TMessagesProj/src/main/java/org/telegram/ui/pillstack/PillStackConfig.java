package org.telegram.ui.pillstack;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.pillstack.pills.BasePill;
import org.telegram.ui.pillstack.pills.BtcPill;
import org.telegram.ui.pillstack.pills.CachePill;
import org.telegram.ui.pillstack.pills.GramPill;
import org.telegram.ui.pillstack.pills.ProxyPill;
import org.telegram.ui.pillstack.pills.UsdPill;
import org.telegram.ui.pillstack.pills.WeatherPill;
import org.telegram.ui.pillstack.pills.LastSeenPill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DevGram: порт «Pill Stack» из exteraGram (com.exteragram.messenger.pillstack.core.PillStackConfig +
 * PillRegistry + PillType). Реестр встроенных и плагинных виджетов + конфиг активных/скрытых пилюль,
 * бесконечной прокрутки и целевых валют. Хранилище — отдельный SharedPreferences «pillstackconfig».
 */
public final class PillStackConfig {

    // Идентификаторы встроенных виджетов (как PillType у exteraGram).
    public static final int WEATHER = 1;
    public static final int GRAM = 2;
    public static final int BTC = 3;
    public static final int USD = 4;
    public static final int CACHE = 5;
    public static final int PROXY = 6;
    public static final int LAST_SEEN = 100;

    public interface PillCreator {
        BasePill create(Context context, Theme.ResourcesProvider resourcesProvider);
    }

    public static final class PillInfo {
        public final int id;
        public final CharSequence name;
        public final int iconRes;
        public final int iconColorTop;
        public final int iconColorBottom;
        public final PillCreator creator;

        public PillInfo(int id, CharSequence name, int iconRes, int iconColorTop, int iconColorBottom, PillCreator creator) {
            this.id = id;
            this.name = name;
            this.iconRes = iconRes;
            this.iconColorTop = iconColorTop;
            this.iconColorBottom = iconColorBottom;
            this.creator = creator;
        }
    }

    private static final Map<Integer, PillInfo> registry = new LinkedHashMap<>();
    private static final List<Integer> activePills = new ArrayList<>();
    private static final List<Integer> hiddenPills = new ArrayList<>();
    private static final HashSet<Integer> pendingUpdates = new HashSet<>();
    private static final Object sync = new Object();
    private static boolean configLoaded;
    private static boolean batchRegistration;

    private static final int R_weather = org.telegram.messenger.R.drawable.weather_cloudy;
    private static final int R_gram = org.telegram.messenger.R.drawable.settings_gram_24;
    private static final int R_btc = org.telegram.messenger.R.drawable.pillstack_btc_settings;
    private static final int R_usd = org.telegram.messenger.R.drawable.pillstack_usd_settings;
    private static final int R_cache = org.telegram.messenger.R.drawable.msg_filled_storageusage;
    private static final int R_proxy = org.telegram.messenger.R.drawable.drawer_proxy_on;

    static {
        beginTransaction();
        register(new PillInfo(WEATHER, "Погода", R_weather, IconBackgroundColors.BLUE_ALT.top, IconBackgroundColors.BLUE_ALT.bottom, WeatherPill::new));
        register(new PillInfo(GRAM, "GRAM", R_gram, IconBackgroundColors.BLUE_LIGHT.top, IconBackgroundColors.BLUE_LIGHT.bottom, GramPill::new));
        register(new PillInfo(BTC, "BTC", R_btc, IconBackgroundColors.ORANGE_BRIGHT.top, IconBackgroundColors.ORANGE_BRIGHT.bottom, BtcPill::new));
        register(new PillInfo(USD, "USD", R_usd, IconBackgroundColors.GREEN_DEEP.top, IconBackgroundColors.GREEN_DEEP.bottom, UsdPill::new));
        register(new PillInfo(CACHE, "Использование памяти", R_cache, IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom, CachePill::new));
        register(new PillInfo(PROXY, "Прокси", R_proxy, IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom, ProxyPill::new));
        register(new PillInfo(LAST_SEEN, "Последний онлайн", org.telegram.messenger.R.drawable.msg_recent,
                IconBackgroundColors.BLUE_ALT.top, IconBackgroundColors.BLUE_ALT.bottom, LastSeenPill::new));
        endTransaction();
    }

    private PillStackConfig() { }

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("pillstackconfig", Context.MODE_PRIVATE);
    }

    // ===== реестр =====

    public static void beginTransaction() { batchRegistration = true; }

    public static void endTransaction() {
        batchRegistration = false;
        if (configLoaded) {
            sanitizePills();
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged));
        }
    }

    public static void register(PillInfo info) {
        if (info == null) return;
        synchronized (sync) {
            load();
            registry.put(info.id, info);
        }
        if (batchRegistration) return;
        sanitizePills();
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged));
    }

    public static void unregister(int id) {
        synchronized (sync) {
            load();
            if (registry.remove(id) == null) return;
            // Плагин выгружен окончательно — убираем его пилюлю из раскладки.
            activePills.remove((Integer) id);
            hiddenPills.remove((Integer) id);
            savePillsLayoutInternal(prefs());
        }
        if (batchRegistration) return;
        sanitizePills();
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged));
    }

    public static PillInfo getPillInfo(int id) { synchronized (sync) { load(); return registry.get(id); } }
    public static Collection<PillInfo> getRegisteredPills() { synchronized (sync) { load(); return new ArrayList<>(registry.values()); } }
    public static boolean isRegistered(int id) { synchronized (sync) { load(); return registry.containsKey(id); } }

    public static BasePill createPill(Context context, Theme.ResourcesProvider rp, int id) {
        PillInfo info = getPillInfo(id);
        if (info == null || info.creator == null) return null;
        try {
            return info.creator.create(context, rp);
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
            return null;
        }
    }

    // ===== конфиг активных/скрытых =====

    public static List<Integer> getActivePills() { synchronized (sync) { load(); return activePills; } }
    public static List<Integer> getHiddenPills() { synchronized (sync) { load(); return hiddenPills; } }

    // Дружелюбный дефолт DevGram (у exteraGram — пусто): Погода + USD + GRAM, как на скринах.
    public static ArrayList<Integer> getDefaultActivePills() {
        return new ArrayList<>(Arrays.asList(WEATHER, USD, GRAM));
    }

    private static void load() {
        if (configLoaded) return;
        SharedPreferences p = prefs();
        String a = p.getString("activePills", null);
        if (a != null) {
            parse(a, activePills);
            parse(p.getString("hiddenPills", ""), hiddenPills);
        } else {
            activePills.addAll(getDefaultActivePills());
            for (Integer id : registry.keySet()) if (!activePills.contains(id)) hiddenPills.add(id);
            savePillsLayoutInternal(p);
        }
        configLoaded = true;
        sanitizeInternal();
    }

    private static void parse(String value, List<Integer> target) {
        if (value == null || value.isEmpty()) return;
        for (String part : value.split(",")) {
            try { target.add(Integer.parseInt(part.trim())); } catch (Exception ignore) { }
        }
    }

    private static String serialize(List<Integer> values) {
        StringBuilder out = new StringBuilder();
        for (Integer v : values) { if (out.length() > 0) out.append(','); out.append(v); }
        return out.toString();
    }

    public static void sanitizePills() { synchronized (sync) { load(); sanitizeInternal(); } }

    private static void sanitizeInternal() {
        // Дедуп: один id не может быть одновременно в активных и скрытых.
        boolean changed = hiddenPills.removeIf(activePills::contains);
        // Каждый зарегистрированный виджет должен быть где-то (по умолчанию — в скрытых).
        for (Integer id : registry.keySet()) {
            if (!activePills.contains(id) && !hiddenPills.contains(id)) { hiddenPills.add(id); changed = true; }
        }
        // НЕ вырезаем незарегистрированные id: плагин мог ещё не загрузиться (иначе активная
        // плагин-пилюля слетала бы в скрытые после перезапуска). Не отрисованные id просто
        // пропускаются при сборке стека и в настройках, а unregister() чистит их явно.
        if (changed) savePillsLayoutInternal(prefs());
    }

    public static void savePillsLayout() { synchronized (sync) { load(); savePillsLayoutInternal(prefs()); } }

    private static void savePillsLayoutInternal(SharedPreferences p) {
        p.edit().putString("activePills", serialize(activePills)).putString("hiddenPills", serialize(hiddenPills)).apply();
    }

    // ===== простые настройки =====

    public static boolean getInfiniteScrolling() { return prefs().getBoolean("infiniteScrolling", true); }
    public static void setInfiniteScrolling(boolean v) { prefs().edit().putBoolean("infiniteScrolling", v).apply(); }

    public static boolean getUseCurrentLocation() { return prefs().getBoolean("useCurrentLocation", true); }
    public static void setUseCurrentLocation(boolean v) { prefs().edit().putBoolean("useCurrentLocation", v).apply(); }

    public static String getCustomWeatherLocation() { return prefs().getString("customWeatherLocation", null); }
    public static void setCustomWeatherLocation(String v) { prefs().edit().putString("customWeatherLocation", v).apply(); }
    public static String getCustomWeatherAddress() { return prefs().getString("customWeatherAddress", null); }
    public static void setCustomWeatherAddress(String v) { prefs().edit().putString("customWeatherAddress", v).apply(); }

    public static String getGramTargetCurrency() { return prefs().getString("gramTargetCurrency", "AUTO"); }
    public static void setGramTargetCurrency(String v) { prefs().edit().putString("gramTargetCurrency", v).apply(); }
    public static String getBtcTargetCurrency() { return prefs().getString("btcTargetCurrency", "AUTO"); }
    public static void setBtcTargetCurrency(String v) { prefs().edit().putString("btcTargetCurrency", v).apply(); }
    public static String getUsdTargetCurrency() { return prefs().getString("usdTargetCurrency", "AUTO"); }
    public static void setUsdTargetCurrency(String v) { prefs().edit().putString("usdTargetCurrency", v).apply(); }

    public static int getLastActivePillId() { return prefs().getInt("lastActivePillId", -1); }
    public static void saveLastActivePillId(int id) { prefs().edit().putInt("lastActivePillId", id).apply(); }

    // ===== точечные обновления виджетов =====

    public static void notifySettingsChanged(int... pillIds) {
        if (pillIds.length == 0) {
            for (PillInfo info : getRegisteredPills()) pendingUpdates.add(info.id);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackSettingsChanged);
            return;
        }
        Object[] boxed = new Object[pillIds.length];
        for (int i = 0; i < pillIds.length; i++) { pendingUpdates.add(pillIds[i]); boxed[i] = pillIds[i]; }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackSettingsChanged, boxed);
    }

    public static boolean checkAndClearPendingUpdate(int id) { return pendingUpdates.remove(Integer.valueOf(id)); }

    public static boolean shouldUpdatePill(Object[] args, int... pillIds) {
        if (args == null || args.length == 0 || pillIds.length == 0) return true;
        for (Object o : args) {
            if (o instanceof Integer) {
                for (int id : pillIds) if (((Integer) o) == id) return true;
            }
        }
        return false;
    }
}

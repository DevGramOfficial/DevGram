package org.telegram.ui.pillstack.pills;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** DevGram: порт ExchangeRates из exteraGram — курсы от Coinbase (USD-база), кэш 5 мин, один запрос в полёте. */
public final class ExchangeRates {

    private ExchangeRates() { }

    public static final String[] MAIN_CURRENCIES = {"USD", "EUR", "RUB", "GBP", "KZT", "TRY", "UAH", "PLN", "AED", "CNY", "JPY", "BYN", "ILS", "CZK", "INR", "TON", "BTC", "ETH", "SOL"};

    public static final class State {
        final Map<String, BigDecimal> usdRates;
        State(Map<String, BigDecimal> usdRates) { this.usdRates = usdRates; }

        public BigDecimal getUsdRate(String code) {
            if (code == null) return null;
            return usdRates.get(normalize(code));
        }

        public BigDecimal getRate(String base, String target) {
            BigDecimal b = getUsdRate(base), t = getUsdRate(target);
            if (b == null || t == null || t.signum() == 0) return null;
            return b.divide(t, 12, RoundingMode.HALF_UP);
        }
    }

    private static final Object sync = new Object();
    private static State cacheValue;
    private static long cacheTimestamp;
    private static boolean requestInFlight;
    private static final ArrayList<Utilities.Callback<State>> pendingCallbacks = new ArrayList<>();

    public static void clearCache() { cacheTimestamp = 0L; }

    private static boolean isStale() {
        return cacheValue == null || cacheTimestamp == 0 || System.currentTimeMillis() - cacheTimestamp >= 300000L;
    }

    public static boolean isSupportedCurrency(String code) {
        if (code == null) return false;
        String n = normalize(code);
        for (String c : MAIN_CURRENCIES) if (c.equals(n)) return true;
        return false;
    }

    public static String resolveTargetCurrency(int account, String selection) {
        String n = normalize(selection);
        if (!"AUTO".equals(n)) {
            return (TextUtils.isEmpty(n) || !isSupportedCurrency(n)) ? "USD" : n;
        }
        // AUTO: валюта региона пользователя (как getTargetCurrency у exteraGram): страна SIM/сети → локаль → USD.
        String region = regionCurrency();
        return isSupportedCurrency(region) ? region : "USD";
    }

    private static String regionCurrency() {
        // 1) страна SIM/сети (без разрешений) — самый надёжный источник валюты региона
        try {
            TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String iso = tm.getSimCountryIso();
                if (iso == null || iso.isEmpty()) iso = tm.getNetworkCountryIso();
                if (iso != null && iso.length() == 2) {
                    Currency cur = Currency.getInstance(new Locale("", iso.toUpperCase(Locale.ROOT)));
                    if (cur != null) return normalize(cur.getCurrencyCode());
                }
            }
        } catch (Throwable ignore) { }
        // 2) валюта локали устройства (регион локали)
        try {
            Currency cur = Currency.getInstance(Locale.getDefault());
            if (cur != null) return normalize(cur.getCurrencyCode());
        } catch (Throwable ignore) { }
        return "USD";
    }

    public static void fetch(final Utilities.Callback<State> callback) {
        if (callback == null) return;
        final State cached = cacheValue;
        if (cached != null && !isStale()) {
            AndroidUtilities.runOnUIThread(() -> callback.run(cached));
            return;
        }
        boolean startRequest;
        synchronized (sync) {
            pendingCallbacks.add(callback);
            startRequest = !requestInFlight;
            if (startRequest) requestInFlight = true;
        }
        if (!startRequest) return;
        org.telegram.messenger.DevGramNetworkQueue.postRunnable(ExchangeRates::doFetch);
    }

    private static void doFetch() {
        State result = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.coinbase.com/v2/exchange-rates?currency=USD");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "DevGram");
            int code = conn.getResponseCode();
            if (code == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                result = parse(sb.toString());
                if (result != null) {
                    cacheValue = result;
                    cacheTimestamp = System.currentTimeMillis();
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        if (result == null) result = cacheValue;
        complete(result);
    }

    private static State parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return null;
            JSONObject rates = data.optJSONObject("rates");
            if (rates == null) return null;
            HashMap<String, BigDecimal> map = new HashMap<>();
            for (String cur : MAIN_CURRENCIES) {
                BigDecimal usdValue = parseUsdRate(cur, rates);
                if (usdValue != null) map.put(cur, usdValue);
            }
            if (map.isEmpty()) return null;
            return new State(map);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Coinbase отдаёт «сколько <cur> за 1 USD»; храним USD-стоимость 1 единицы <cur> = 1 / rate.
    private static BigDecimal parseUsdRate(String cur, JSONObject rates) {
        if ("USD".equals(cur)) return BigDecimal.ONE;
        String s = rates.optString(cur, null);
        if (s == null || s.isEmpty()) return null;
        try {
            BigDecimal rate = new BigDecimal(s);
            if (rate.signum() == 0) return null;
            return BigDecimal.ONE.divide(rate, 16, RoundingMode.HALF_UP);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void complete(final State state) {
        final ArrayList<Utilities.Callback<State>> callbacks;
        synchronized (sync) {
            requestInFlight = false;
            callbacks = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (Utilities.Callback<State> cb : callbacks) cb.run(state);
        });
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase(Locale.ROOT);
    }
}

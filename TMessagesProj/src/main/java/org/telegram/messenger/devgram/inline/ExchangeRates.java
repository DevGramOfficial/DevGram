package org.telegram.messenger.devgram.inline;

import org.telegram.messenger.devgram.inline.InlineConfig;

import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.CacheFetcher;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
public abstract class ExchangeRates {
    private static final Set<String> CURRENCIES;
    public static final List<String> FIAT_CURRENCIES;
    private static volatile State cacheValue;
    private static final CacheFetcher<Integer, State> fetcher;
    private static final ArrayList<Utilities.Callback4<Boolean, State, Long, Boolean>> pendingRemote;
    private static boolean remoteInFlight;

    static {
        List<String> listUnmodifiableList = Collections.unmodifiableList(Arrays.asList("AED", "AFN", "ALL", "AMD", "AOA", "ARS", "AUD", "AWG", "AZN", "BAM", "BBD", "BDT", "BHD", "BIF", "BMD", "BND", "BOB", "BRL", "BSD", "BTN", "BWP", "BYN", "BZD", "CAD", "CDF", "CHF", "CLP", "CNY", "COP", "CRC", "CUP", "CVE", "CZK", "DJF", "DKK", "DOP", "DZD", "EGP", "ETB", "EUR", "FJD", "FKP", "GBP", "GEL", "GHS", "GIP", "GMD", "GNF", "GTQ", "GYD", "HKD", "HNL", "HTG", "HUF", "IDR", "ILS", "INR", "IQD", "IRR", "ISK", "JMD", "JOD", "JPY", "KES", "KGS", "KHR", "KMF", "KRW", "KWD", "KYD", "KZT", "LAK", "LBP", "LKR", "LRD", "LSL", "LYD", "MAD", "MDL", "MGA", "MKD", "MMK", "MNT", "MOP", "MRU", "MUR", "MVR", "MWK", "MXN", "MYR", "MZN", "NAD", "NGN", "NIO", "NOK", "NPR", "NZD", "OMR", "PAB", "PEN", "PGK", "PHP", "PKR", "PLN", "PYG", "QAR", "RON", "RSD", "RUB", "RWF", "SAR", "SBD", "SCR", "SDG", "SEK", "SGD", "SHP", "SOS", "SRD", "SSP", "SYP", "SZL", "THB", "TJS", "TMT", "TND", "TOP", "TRY", "TTD", "TWD", "TZS", "UAH", "UGX", "USD", "UYU", "UZS", "VES", "VND", "VUV", "WST", "XAF", "XCD", "XOF", "XPF", "YER", "ZAR", "ZMW"));
        FIAT_CURRENCIES = listUnmodifiableList;
        HashSet hashSet = new HashSet(listUnmodifiableList);
        CURRENCIES = hashSet;
        hashSet.addAll(Arrays.asList("TON", "BTC", "ETH", "SOL", "USDT"));
        pendingRemote = new ArrayList<>();
        fetcher = new CacheFetcher<Integer, State>(300000) {
            @Override
            public boolean saveLastTimeRequested() {
                return true;
            }

            @Override
            public void getLocal(int i, Integer num, Utilities.Callback2<Long, State> callback2) {
                callback2.run(0L, ExchangeRates.getCached());
            }

            @Override
            public void setLocal(int i, Integer num, State state, long j) {
                if (state == null) {
                    return;
                }
                ExchangeRates.cacheValue = state;
                try {
                    InlineConfig.getEditor().putString("exchangeRatesCache", InlineConfig.getGSON().toJson(state)).apply();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            @Override
            public void getRemote(int i, Integer num, long j, Utilities.Callback4<Boolean, State, Long, Boolean> callback4) {
                synchronized (ExchangeRates.pendingRemote) {
                    try {
                        ExchangeRates.pendingRemote.add(callback4);
                        if (ExchangeRates.remoteInFlight) {
                            return;
                        }
                        ExchangeRates.remoteInFlight = true;
                        Utilities.globalQueue.postRunnable(() -> {
                            java.net.HttpURLConnection connection = null;
                            try {
                                connection = (java.net.HttpURLConnection) new java.net.URL("https://api.coinbase.com/v2/exchange-rates?currency=USD").openConnection();
                                connection.setConnectTimeout(10000);
                                connection.setReadTimeout(10000);
                                if (connection.getResponseCode() != 200) {
                                    throw new IOException("Unexpected code " + connection.getResponseCode());
                                }
                                try (java.io.Reader reader = new java.io.InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                                    ExchangeRates.completeRemote(ExchangeRates.parseState(InlineConfig.getGSON().fromJson(reader, CoinbaseResponse.class)));
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                                ExchangeRates.completeRemote(null);
                            } finally {
                                if (connection != null) {
                                    connection.disconnect();
                                }
                            }
                        });
                    } catch (Throwable th) {
                        throw th;
                    }
                }
            }

            @Override
            public long getSavedLastTimeRequested(int i) {
                return InlineConfig.getPreferences().getLong("exchangeRatesTimestamp", 0L);
            }

            @Override
            public void setSavedLastTimeRequested(int i, long j) {
                InlineConfig.getEditor().putLong("exchangeRatesTimestamp", j).apply();
            }
        };
    }

    public static class CoinbaseResponse {

        @SerializedName("data")
        Data data;

        private CoinbaseResponse() {
        }
    }

    public static class Data {

        @SerializedName("currency")
        String currency;

        @SerializedName("rates")
        Map<String, String> rates;

        private Data() {
        }
    }

    public static final class State {
        public static final DecimalFormat formatter;
        private final long time;
        private final Map<String, BigDecimal> usdRates;



        public State(Map<String, BigDecimal> map, long j) {
            this.usdRates = map;
            this.time = j;
        }


        public final int hashCode() {
            return ((Long.hashCode(this.time) * 31) + Objects.hashCode(this.usdRates));
        }

        public long time() {
            return this.time;
        }


        public Map<String, BigDecimal> usdRates() {
            return this.usdRates;
        }

        static {
            DecimalFormat decimalFormat = new DecimalFormat("#.##");
            formatter = decimalFormat;
            decimalFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        }

        public BigDecimal getUsdRate(String str) {
            if (str == null) {
                return null;
            }
            return this.usdRates.get(ExchangeRates.normalize(str));
        }

        public BigDecimal getRate(String str, String str2) {
            BigDecimal usdRate = getUsdRate(str);
            BigDecimal usdRate2 = getUsdRate(str2);
            if (usdRate == null || usdRate2 == null || usdRate2.signum() == 0) {
                return null;
            }
            return usdRate.divide(usdRate2, 12, RoundingMode.HALF_UP);
        }
    }

    public static void completeRemote(State state) {
        int i;
        ArrayList arrayList;
        ArrayList<Utilities.Callback4<Boolean, State, Long, Boolean>> arrayList2 = pendingRemote;
        synchronized (arrayList2) {
            i = 0;
            remoteInFlight = false;
            arrayList = new ArrayList(arrayList2);
            arrayList2.clear();
        }
        int size = arrayList.size();
        while (i < size) {
            Object obj = arrayList.get(i);
            i++;
            Utilities.Callback4 callback4 = (Utilities.Callback4) obj;
            if (state == null) {
                callback4.run(Boolean.TRUE, null, 0L, Boolean.FALSE);
            } else {
                callback4.run(Boolean.FALSE, state, 0L, Boolean.TRUE);
            }
        }
    }

    public static State getCached() {
        if (cacheValue == null) {
            try {
                String string = InlineConfig.getPreferences().getString("exchangeRatesCache", null);
                if (string != null) {
                    cacheValue = (State) InlineConfig.getGSON().fromJson(string, State.class);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return cacheValue;
    }

    public static State peekCached() {
        return cacheValue;
    }

    public static void clearCache() {
        InlineConfig.getEditor().putLong("exchangeRatesTimestamp", 0L).apply();
        fetcher.forceRequest(0, 0);
    }

    public static List<String> getSupportedCurrencies() {
        return new ArrayList(CURRENCIES);
    }

    public static boolean isSupportedCurrency(String str) {
        return CURRENCIES.contains(normalize(str));
    }

    // Валюта страны: сначала по номеру телефона аккаунта, потом по стране системной локали.
    // Раньше брали Locale.getDefault(): у языка приложения «ru» нет страны, Currency падал,
    // и «своей» валютой становился USD — «4$=» переводилось в те же доллары и не показывалось.
    private static String localeCurrency(int account) {
        String byPhone = currencyByPhone(org.telegram.messenger.UserConfig.getInstance(account).getClientPhone());
        if (byPhone != null) {
            return byPhone;
        }
        java.util.Locale system = org.telegram.messenger.LocaleController.getInstance().getSystemDefaultLocale();
        String currency = currencyOfCountry(system != null ? system.getCountry() : null);
        return currency != null ? currency : "USD";
    }

    private static String currencyByPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return null;
        }
        try {
            String digits = org.telegram.PhoneFormat.PhoneFormat.stripExceptNumbers(phone);
            org.telegram.PhoneFormat.CallingCodeInfo info = digits.isEmpty() ? null : org.telegram.PhoneFormat.PhoneFormat.getInstance().findCallingCodeInfo(digits);
            if (info == null || info.countries == null || info.countries.isEmpty()) {
                return null;
            }
            // у одного кода бывает несколько стран (+7 — Россия и Казахстан): предпочитаем страну системы
            String country = info.countries.get(0);
            java.util.Locale system = org.telegram.messenger.LocaleController.getInstance().getSystemDefaultLocale();
            String systemCountry = system != null ? system.getCountry() : null;
            if (systemCountry != null && !systemCountry.isEmpty()) {
                for (String c : info.countries) {
                    if (systemCountry.equalsIgnoreCase(c)) {
                        country = c;
                        break;
                    }
                }
            }
            return country.startsWith("_") ? null : currencyOfCountry(country);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String currencyOfCountry(String country) {
        if (country == null || country.isEmpty()) {
            return null;
        }
        try {
            java.util.Currency currency = java.util.Currency.getInstance(new java.util.Locale("", country.toUpperCase(Locale.ROOT)));
            return currency != null ? currency.getCurrencyCode() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getLocalCurrency(int i) {
        String strNormalize = normalize(localeCurrency(i));
        if (CURRENCIES.contains(strNormalize)) {
            return strNormalize;
        }
        return null;
    }

    public static void fetch(Utilities.Callback<State> callback) {
        if (callback == null) {
            return;
        }
        fetcher.fetch(0, 0, callback);
    }
    public static State parseState(CoinbaseResponse coinbaseResponse) {
        Data data;
        if (coinbaseResponse == null || (data = coinbaseResponse.data) == null || data.rates == null) {
            return null;
        }
        HashMap map = new HashMap();
        for (String str : CURRENCIES) {
            BigDecimal usdRate = parseUsdRate(str, coinbaseResponse.data.rates);
            if (usdRate != null) {
                map.put(str, usdRate);
            }
        }
        if (map.isEmpty()) {
            return null;
        }
        return new State(map, System.currentTimeMillis());
    }

    private static BigDecimal parseUsdRate(String str, Map<String, String> map) {
        if ("USD".equals(str)) {
            return BigDecimal.ONE;
        }
        String str2 = map.get(str);
        if (str2 == null) {
            return null;
        }
        try {
            BigDecimal bigDecimal = new BigDecimal(str2);
            if (bigDecimal.signum() == 0) {
                return null;
            }
            return BigDecimal.ONE.divide(bigDecimal, 16, RoundingMode.HALF_UP);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
    public static String normalize(String str) {
        if (str == null) {
            return "";
        }
        return str.trim().toUpperCase(Locale.ROOT);
    }
}

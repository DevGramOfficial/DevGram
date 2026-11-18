package org.telegram.ui.pillstack.pills;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.FileLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** DevGram: порт PillStackCurrencies из exteraGram — форматирование фиатной цены и список целевых валют (названия по-русски). */
public final class PillStackCurrencies {

    private PillStackCurrencies() { }

    private static final HashSet<String> AMBIGUOUS_SYMBOLS = new HashSet<>(Arrays.asList("$", "kr", "Fr", "₩"));

    private static final class Info {
        final int nameKeyless;
        final String name;
        final String symbolOverride;
        final boolean suffixSymbol;
        Info(String name, String symbolOverride, boolean suffixSymbol) {
            this.nameKeyless = 0; this.name = name; this.symbolOverride = symbolOverride; this.suffixSymbol = suffixSymbol;
        }
    }

    private static final Map<String, Info> CURRENCIES = new LinkedHashMap<>();
    public static final String[] TARGET_CURRENCIES;

    static {
        add("USD", "Доллар США", "$", false);
        add("EUR", "Евро", null, false);
        add("RUB", "Российский рубль", "₽", true);
        add("GBP", "Фунт стерлингов", null, false);
        add("KZT", "Казахстанский тенге", "₸", true);
        add("TRY", "Турецкая лира", "₺", true);
        add("UAH", "Украинская гривна", "₴", true);
        add("PLN", "Польский злотый", "zł", true);
        add("AED", "Дирхам ОАЭ", null, false);
        add("CNY", "Китайский юань", "CN¥", false);
        add("JPY", "Японская иена", null, false);
        add("BYN", "Белорусский рубль", "Br", true);
        add("ILS", "Израильский шекель", "₪", false);
        add("CZK", "Чешская крона", "Kč", true);
        add("INR", "Индийская рупия", "₹", false);
        TARGET_CURRENCIES = new String[]{"AUTO", "AED", "BYN", "CNY", "CZK", "EUR", "GBP", "ILS", "INR", "JPY", "KZT", "PLN", "RUB", "TRY", "UAH", "USD"};
    }

    private static void add(String code, String name, String symbolOverride, boolean suffixSymbol) {
        CURRENCIES.put(normalize(code), new Info(name, symbolOverride, suffixSymbol));
    }

    public static CharSequence getTargetCurrencyLabel(String code) {
        if (code == null || "AUTO".equalsIgnoreCase(code)) return DevGramLocale.text("Авто");
        return getCurrencyLabelWithCode(code);
    }

    public static CharSequence getTargetCurrencySubtext(String code) {
        if (code == null || "AUTO".equalsIgnoreCase(code)) return DevGramLocale.text("Авто");
        return getCurrencyName(code);
    }

    public static String getCurrencyName(String code) {
        String n = normalize(code);
        Info i = CURRENCIES.get(n);
        return i == null ? n : DevGramLocale.text(i.name);
    }

    public static String getCurrencyLabelWithCode(String code) {
        String n = normalize(code);
        Info i = CURRENCIES.get(n);
        return i == null ? n : DevGramLocale.text(i.name) + " — " + n;
    }

    public static String[] getTargetCurrencies(String excludeBase) {
        if (excludeBase == null || excludeBase.isEmpty()) return TARGET_CURRENCIES;
        int count = 0;
        for (String c : TARGET_CURRENCIES) if (!excludeBase.equalsIgnoreCase(c)) count++;
        String[] out = new String[count];
        int idx = 0;
        for (String c : TARGET_CURRENCIES) if (!excludeBase.equalsIgnoreCase(c)) out[idx++] = c;
        return out;
    }

    public static String formatFiatPrice(BigDecimal value, String code) {
        if (value == null || code == null || code.isEmpty()) return null;
        try {
            int exp = Math.max(0, BillingController.getInstance().getCurrencyExp(code));
            BigDecimal scaled = value.setScale(exp, RoundingMode.HALF_UP);
            Locale locale = Locale.US;
            NumberFormat nf = NumberFormat.getNumberInstance(locale);
            nf.setGroupingUsed(true);
            nf.setMinimumFractionDigits(exp);
            nf.setMaximumFractionDigits(exp);
            String num = nf.format(scaled);
            String n = normalize(code);
            Info info = CURRENCIES.get(n);
            String symbol = info != null ? info.symbolOverride : null;
            boolean overridden = symbol != null;
            if (!overridden) {
                try { symbol = Currency.getInstance(n).getSymbol(locale); } catch (Exception e) { FileLog.e(e); }
            }
            if (symbol != null && !symbol.isEmpty() && !symbol.equalsIgnoreCase(n)) {
                if (!overridden && AMBIGUOUS_SYMBOLS.contains(symbol)) return num + " " + code;
                if (info != null && info.suffixSymbol) return num + " " + symbol;
                return symbol + num;
            }
            return num + " " + code;
        } catch (Exception ignore) {
            return null;
        }
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase(Locale.ROOT);
    }
}

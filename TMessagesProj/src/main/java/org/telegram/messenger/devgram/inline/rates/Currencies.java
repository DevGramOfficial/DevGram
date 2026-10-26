package org.telegram.messenger.devgram.inline.rates;

import org.telegram.messenger.devgram.inline.math.MathUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kotlin.Pair;
import kotlin.TuplesKt;
import kotlin.collections.CollectionsKt;
import kotlin.collections.MapsKt;
import kotlin.collections.SetsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.RangesKt;
import kotlin.text.Typography;
public final class Currencies {
    public static final Currencies INSTANCE = new Currencies();
    private static final Set<String> conversions;
    private static final Map<String, String> labels;
    private static final Set<String> lowercaseCodes;
    private static final Set<Character> symbolChars;
    private static final Map<Character, String> symbols;
    private static final Map<String, String> words;

    private Currencies() {
    }

    static {
        char c2;
        String str = "BTC";
        char c3 = 9733;
        Map<Character, String> mapMapOf = MapsKt.mapOf(TuplesKt.to(Character.valueOf(Typography.dollar), "USD"), TuplesKt.to(Character.valueOf(Typography.euro), "EUR"), TuplesKt.to((char) 8381, "RUB"), TuplesKt.to(Character.valueOf(Typography.pound), "GBP"), TuplesKt.to((char) 165, "JPY"), TuplesKt.to((char) 8376, "KZT"), TuplesKt.to((char) 8372, "UAH"), TuplesKt.to((char) 8378, "TRY"), TuplesKt.to((char) 8377, "INR"), TuplesKt.to((char) 8362, "ILS"), TuplesKt.to((char) 8361, "KRW"), TuplesKt.to((char) 8382, "GEL"), TuplesKt.to((char) 8380, "AZN"), TuplesKt.to((char) 1423, "AMD"), TuplesKt.to((char) 8363, "VND"), TuplesKt.to((char) 3647, "THB"), TuplesKt.to((char) 8369, "PHP"), TuplesKt.to((char) 8358, "NGN"), TuplesKt.to((char) 8383, "BTC"), TuplesKt.to((char) 11088, "XTR"), TuplesKt.to((char) 9733, "XTR"));
        symbols = mapMapOf;
        Set<Map.Entry<Character, String>> setEntrySet = mapMapOf.entrySet();
        ArrayList arrayList = new ArrayList();
        for (Object obj : setEntrySet) {
            Map.Entry entry = (Map.Entry) obj;
            String str2 = str;
            if (Intrinsics.areEqual(entry.getValue(), str2)) {
                c2 = c3;
            } else {
                c2 = c3;
                if (((Character) entry.getKey()).charValue() != c2) {
                    arrayList.add(obj);
                }
            }
            c3 = c2;
            str = str2;
        }
        String str3 = str;
        LinkedHashMap linkedHashMap = new LinkedHashMap(RangesKt.coerceAtLeast(MapsKt.mapCapacity(CollectionsKt.collectionSizeOrDefault(arrayList, 10)), 16));
        int size = arrayList.size();
        int i = 0;
        while (i < size) {
            Object obj2 = arrayList.get(i);
            i++;
            Map.Entry entry2 = (Map.Entry) obj2;
            Pair pair = TuplesKt.to(entry2.getValue(), String.valueOf(((Character) entry2.getKey()).charValue()));
            linkedHashMap.put(pair.getFirst(), pair.getSecond());
        }
        labels = linkedHashMap;
        lowercaseCodes = SetsKt.setOf(new String[]{"usd", "eur", "rub", "gbp", "kzt", "uah", "byn", "cny", "jpy", "pln", "czk", "aed", "inr", "ils", "chf", "cad", "aud", "uzs", "kgs", "azn", "krw", "thb", "vnd", "sgd", "hkd", "brl", "mxn", "sek", "huf", "btc", "eth", "usdt", "xtr"});
        conversions = SetsKt.setOf(new String[]{"в", "to", "in"});
        Map mapCreateMapBuilder = MapsKt.createMapBuilder();
        words$lambda$3$add(mapCreateMapBuilder, "USD", "доллар", "доллара", "долларов", "доллары", "долларах", "долл", "бакс", "бакса", "баксов", "баксы", "баксах", "dollar", "dollars", "buck", "bucks");
        words$lambda$3$add(mapCreateMapBuilder, "EUR", "евро", "euro", "euros");
        words$lambda$3$add(mapCreateMapBuilder, "RUB", "рубль", "рубля", "рублей", "рубли", "рублях", "руб", "р", "ruble", "rubles", "rouble", "roubles");
        words$lambda$3$add(mapCreateMapBuilder, "GBP", "фунт", "фунта", "фунтов", "фунты", "фунтах");
        words$lambda$3$add(mapCreateMapBuilder, "KZT", "тенге", "tenge");
        words$lambda$3$add(mapCreateMapBuilder, "UAH", "гривна", "гривны", "гривен", "гривнах", "грн", "hryvnia", "hryvnias");
        words$lambda$3$add(mapCreateMapBuilder, "CNY", "юань", "юаня", "юаней", "юани", "юанях", "yuan");
        words$lambda$3$add(mapCreateMapBuilder, "JPY", "иена", "иены", "иен", "иенах", "йена", "йены", "йен", "йенах", "yen");
        words$lambda$3$add(mapCreateMapBuilder, "TRY", "лира", "лиры", "лир", "лирах", "lira", "liras");
        words$lambda$3$add(mapCreateMapBuilder, "PLN", "злотый", "злотого", "злотых", "злотые", "zloty", "zlotys", "zł");
        words$lambda$3$add(mapCreateMapBuilder, "CZK", "kč");
        words$lambda$3$add(mapCreateMapBuilder, "INR", "рупия", "рупии", "рупий", "рупиях", "rupee", "rupees");
        words$lambda$3$add(mapCreateMapBuilder, "ILS", "шекель", "шекеля", "шекелей", "шекели", "шекелях", "shekel", "shekels");
        words$lambda$3$add(mapCreateMapBuilder, "CHF", "франк", "франка", "франков", "франки", "франках", "franc", "francs");
        words$lambda$3$add(mapCreateMapBuilder, "AED", "дирхам", "дирхама", "дирхамов", "дирхамы", "дирхамах", "dirham", "dirhams");
        words$lambda$3$add(mapCreateMapBuilder, "THB", "бат", "бата", "батов", "баты", "батах", "baht");
        words$lambda$3$add(mapCreateMapBuilder, "GEL", "лари", "lari");
        words$lambda$3$add(mapCreateMapBuilder, "AMD", "драм", "драмов", "драмы", "драмах", "dram", "drams");
        words$lambda$3$add(mapCreateMapBuilder, "UZS", "сум", "сумов", "сумы", "сумах");
        words$lambda$3$add(mapCreateMapBuilder, "KGS", "сом", "сомов", "сомах", "som");
        words$lambda$3$add(mapCreateMapBuilder, str3, "биткоин", "биткоина", "биткоинов", "биткоины", "биткоинах", "биткойн", "биткойна", "биткойнов", "биткойны", "биткойнах", "bitcoin", "bitcoins");
        words$lambda$3$add(mapCreateMapBuilder, "ETH", "эфириум", "эфириума", "эфириумов", "эфириумах", "ethereum", "ether");
        words$lambda$3$add(mapCreateMapBuilder, "GRAM", "gram", "grams", "грам", "грама", "грамов", "грамы", "грамах", "ton", "тон", "тона", "тонов", "тонах", "toncoin", "toncoins", "тонкоин", "тонкоина", "тонкоинов");
        words$lambda$3$add(mapCreateMapBuilder, "USDT", "tether");
        words$lambda$3$add(mapCreateMapBuilder, "XTR", "звезда", "звезды", "звёзды", "звезд", "звёзд", "звездах", "звёздах", "звездами", "звёздами", "звезду", "star", "stars");
        words = MapsKt.build(mapCreateMapBuilder);
        symbolChars = symbols.keySet();
    }

    public final Set<Character> getSymbolChars() {
        return symbolChars;
    }

    public final boolean isConversion(String word) {
        return conversions.contains(word.toLowerCase(Locale.ROOT));
    }

    public final MathUnit symbol(char c2, CurrencyRates rates) {
        String str = symbols.get(Character.valueOf(c2));
        if (str != null && rates.supports(str)) {
            return new MathUnit(str, Intrinsics.areEqual(str, "XTR") ? label(str) : String.valueOf(c2), null, 0.0d, 0.0d, null, 60, null);
        }
        return null;
    }

    public final MathUnit word(String word, CurrencyRates rates) {
        String str;
        String upperCase = word;
        String lowerCase = upperCase.toLowerCase(Locale.ROOT);
        String str2 = words.get(lowerCase);
        if (str2 == null) {
            for (int i = 0; i < upperCase.length(); i++) {
                char cCharAt = upperCase.charAt(i);
                if ('A' > cCharAt || cCharAt >= '[') {
                    if (!lowercaseCodes.contains(lowerCase)) {
                        return null;
                    }
                    upperCase = lowerCase.toUpperCase(Locale.ROOT);
                    break;
                }
            }
            str = upperCase;
        } else {
            str = str2;
        }
        if (rates.supports(str)) {
            return new MathUnit(str, str2 != null ? label(str) : str, null, 0.0d, 0.0d, null, 60, null);
        }
        return null;
    }

    public final MathUnit unit(String code) {
        return new MathUnit(code, label(code), null, 0.0d, 0.0d, null, 60, null);
    }

    public final int fractionDigits(String code) {
        if (Intrinsics.areEqual(code, "XTR")) {
            return 0;
        }
        try {
            return Currency.getInstance(code).getDefaultFractionDigits();
        } catch (IllegalArgumentException unused) {
            return -1;
        }
    }

    private final String label(String code) {
        String str = labels.get(code);
        return str == null ? code : str;
    }

    private static final void words$lambda$3$add(Map<String, String> map, String str, String... strArr) {
        for (String str2 : strArr) {
            map.put(str2, str);
        }
    }
}

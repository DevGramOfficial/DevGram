package org.telegram.messenger.devgram.inline.docs;

import java.util.List;
import kotlin.collections.CollectionsKt;
public final class InlineExamples {
    private static final List<InlineExampleGroup> all;
    private static final InlineExampleGroup arithmetic;
    private static final InlineExampleGroup arithmeticFunctions;
    private static final InlineExampleGroup bases;
    private static final InlineExampleGroup currencyDefault;
    private static final InlineExampleGroup currencyTarget;
    private static final InlineExampleGroup currentTime;
    private static final InlineExampleGroup durations;
    private static final InlineExampleGroup percentages;
    private static final InlineExampleGroup shorthands;
    private static final InlineExampleGroup timeZones;
    private static final InlineExampleGroup unitsDefault;
    private static final InlineExampleGroup unitsTarget;
    public static final InlineExamples INSTANCE = new InlineExamples();
    private static final List<String> functions = CollectionsKt.listOf(new String[]{"round", "min", "max", "abs", "sqrt", "log", "ln", "sin", "cos", "tan", "pi", "e"});

    private InlineExamples() {
    }

    static {
        InlineExampleGroup inlineExampleGroup = new InlineExampleGroup(CollectionsKt.listOf(new String[]{"(120 + 80) / 4 =", "√144 + 2^3 ="}), null, 2, null);
        arithmetic = inlineExampleGroup;
        InlineExampleGroup inlineExampleGroup2 = new InlineExampleGroup(CollectionsKt.listOf(new String[]{"max(12, 40, 7) =", "sin(30) ="}), null, 2, null);
        arithmeticFunctions = inlineExampleGroup2;
        InlineExampleGroup inlineExampleGroup3 = new InlineExampleGroup(CollectionsKt.listOf(new String[]{"hex(255) =", "dec(ff, 16) =", "0xff + 1 ="}), null, 2, null);
        bases = inlineExampleGroup3;
        InlineExampleGroup inlineExampleGroup4 = new InlineExampleGroup(CollectionsKt.listOf(new String[]{"2300 - 15% =", "12% of 1200 ="}), CollectionsKt.listOf(new String[]{"2300 - 15% =", "12% от 1200 ="}));
        percentages = inlineExampleGroup4;
        InlineExampleGroup inlineExampleGroup5 = new InlineExampleGroup(CollectionsKt.listOf("300k / 12 ="), CollectionsKt.listOf("300к / 12 ="));
        shorthands = inlineExampleGroup5;
        InlineExampleGroup inlineExampleGroup6 = new InlineExampleGroup(CollectionsKt.listOf(new String[]{"100€ =", "500 stars ="}), CollectionsKt.listOf(new String[]{"100€ =", "500 звёзд ="}));
        currencyDefault = inlineExampleGroup6;
        InlineExampleGroup inlineExampleGroup7 = new InlineExampleGroup(CollectionsKt.listOf(new String[]{"100 dollars to euros =", "5k ₽ to $ =", "50$ to GRAM ="}), CollectionsKt.listOf(new String[]{"100 долларов в евро =", "5к ₽ в $ =", "50$ в GRAM ="}));
        currencyTarget = inlineExampleGroup7;
        InlineExampleGroup inlineExampleGroup8 = new InlineExampleGroup(CollectionsKt.listOf(new String[]{"72°F =", "10 miles ="}), CollectionsKt.listOf(new String[]{"72°F =", "10 миль ="}));
        unitsDefault = inlineExampleGroup8;
        InlineExampleGroup inlineExampleGroup9 = new InlineExampleGroup(CollectionsKt.listOf(new String[]{"5 kg to lb =", "100 km/h to mph ="}), CollectionsKt.listOf(new String[]{"5 кг в фунтах =", "100 км/ч в mph ="}));
        unitsTarget = inlineExampleGroup9;
        InlineExampleGroup inlineExampleGroup10 = new InlineExampleGroup(CollectionsKt.listOf(new String[]{"90 min =", "2 h + 45 min ="}), CollectionsKt.listOf(new String[]{"90 мин =", "2 ч + 45 мин ="}));
        durations = inlineExampleGroup10;
        InlineExampleGroup inlineExampleGroup11 = new InlineExampleGroup(CollectionsKt.listOf(new String[]{"6pm est =", "12:00 in tokyo ="}), CollectionsKt.listOf(new String[]{"18:00 utc =", "12:00 в токио ="}));
        timeZones = inlineExampleGroup11;
        InlineExampleGroup inlineExampleGroup12 = new InlineExampleGroup(CollectionsKt.listOf("now in new york ="), CollectionsKt.listOf("сейчас в нью-йорке ="));
        currentTime = inlineExampleGroup12;
        all = CollectionsKt.listOf(new InlineExampleGroup[]{inlineExampleGroup, inlineExampleGroup2, inlineExampleGroup3, inlineExampleGroup4, inlineExampleGroup5, inlineExampleGroup6, inlineExampleGroup7, inlineExampleGroup8, inlineExampleGroup9, inlineExampleGroup10, inlineExampleGroup11, inlineExampleGroup12});
    }

    public final List<String> getFunctions() {
        return functions;
    }

    public final InlineExampleGroup getArithmetic() {
        return arithmetic;
    }

    public final InlineExampleGroup getArithmeticFunctions() {
        return arithmeticFunctions;
    }

    public final InlineExampleGroup getBases() {
        return bases;
    }

    public final InlineExampleGroup getPercentages() {
        return percentages;
    }

    public final InlineExampleGroup getShorthands() {
        return shorthands;
    }

    public final InlineExampleGroup getCurrencyDefault() {
        return currencyDefault;
    }

    public final InlineExampleGroup getCurrencyTarget() {
        return currencyTarget;
    }

    public final InlineExampleGroup getUnitsDefault() {
        return unitsDefault;
    }

    public final InlineExampleGroup getUnitsTarget() {
        return unitsTarget;
    }

    public final InlineExampleGroup getDurations() {
        return durations;
    }

    public final InlineExampleGroup getTimeZones() {
        return timeZones;
    }

    public final InlineExampleGroup getCurrentTime() {
        return currentTime;
    }
}

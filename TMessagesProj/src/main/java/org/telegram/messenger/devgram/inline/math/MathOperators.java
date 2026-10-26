package org.telegram.messenger.devgram.inline.math;

import org.telegram.messenger.devgram.KtCompat;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.TuplesKt;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.collections.MapsKt;
import kotlin.collections.SetsKt;
import kotlin.comparisons.ComparisonsKt;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function3;
import kotlin.jvm.internal.Intrinsics;
import kotlin.math.MathKt;
import kotlin.ranges.IntRange;
import kotlin.text.StringsKt;
public final class MathOperators {
    public static final MathOperators INSTANCE = new MathOperators();
    private static final Map<String, Integer> bases;
    private static final Map<String, Double> constants;
    private static final List<MathFunction> functions;
    private static final Set<String> gluedMultipliers;
    private static final List<InfixOperator> infix;
    private static final Map<String, Double> multipliers;
    private static final Set<String> percentOf;
    private static final List<PostfixOperator> postfix;
    private static final List<PrefixOperator> prefix;
    private static final Set<Character> symbolChars;
    private static final List<String> symbols;

    public static double $r8$lambda$1sHgS7I35umYlixCna5c35deAro(double d) {
        return d;
    }

    public final int radixFor(char prefix2) {
        if (prefix2 == 'B') {
            return 2;
        }
        if (prefix2 == 'O') {
            return 8;
        }
        if (prefix2 == 'X') {
            return 16;
        }
        if (prefix2 == 'b') {
            return 2;
        }
        if (prefix2 != 'o') {
            return prefix2 != 'x' ? 0 : 16;
        }
        return 8;
    }

    private MathOperators() {
    }

    static {
        List listListOf = CollectionsKt.listOf("+");
        UnitRule unitRule = UnitRule.SAME;
        List<InfixOperator> listListOf2 = CollectionsKt.listOf(new InfixOperator[]{new InfixOperator(listListOf, 10, unitRule, false, (obj, obj2, obj3) -> Double.valueOf(MathOperators.$r8$lambda$ZVrQvXZPmKA6MKcS5cA3hZbE5s0(((Double) obj).doubleValue(), ((Double) obj2).doubleValue(), ((Boolean) obj3).booleanValue())), 8, null), new InfixOperator(CollectionsKt.listOf(new String[]{"-", "−"}), 10, unitRule, false, (obj, obj2, obj3) -> Double.valueOf(MathOperators.m1536$r8$lambda$LSDKb6aiRHvNLPBAkDSZnVorXU(((Double) obj).doubleValue(), ((Double) obj2).doubleValue(), ((Boolean) obj3).booleanValue())), 8, null), new InfixOperator(CollectionsKt.listOf(new String[]{"*", "×"}), 20, UnitRule.SCALE, false, (obj, obj2, obj3) -> Double.valueOf(MathOperators.$r8$lambda$k5EXF16m3PjNI8p3YfxpxexwkF8(((Double) obj).doubleValue(), ((Double) obj2).doubleValue(), ((Boolean) obj3).booleanValue())), 8, null), new InfixOperator(CollectionsKt.listOf(new String[]{"/", "÷"}), 20, UnitRule.RATIO, false, (obj, obj2, obj3) -> Double.valueOf(MathOperators.$r8$lambda$kelnVd_Ka84nxlxlbuCSTjjpYkM(((Double) obj).doubleValue(), ((Double) obj2).doubleValue(), ((Boolean) obj3).booleanValue())), 8, null), new InfixOperator(CollectionsKt.listOf("^"), 40, UnitRule.NONE, true, (obj, obj2, obj3) -> {
double dDoubleValue = ((Double) obj).doubleValue();
                double dDoubleValue2 = ((Double) obj2).doubleValue();
                ((Boolean) obj3).booleanValue();
                return Double.valueOf(Math.pow(dDoubleValue, dDoubleValue2));
})});
        infix = listListOf2;
        prefix = CollectionsKt.listOf(new PrefixOperator[]{new PrefixOperator(CollectionsKt.listOf(new String[]{"-", "−"}), 30, false, obj -> Double.valueOf(MathOperators.m1541$r8$lambda$pYr6NR2WMrxTJl51URT4Km1qE(((Double) obj).doubleValue())), 4, null), new PrefixOperator(CollectionsKt.listOf("+"), 30, false, obj -> Double.valueOf(MathOperators.$r8$lambda$1sHgS7I35umYlixCna5c35deAro(((Double) obj).doubleValue())), 4, null), new PrefixOperator(CollectionsKt.listOf("√"), 40, true, obj -> Double.valueOf(Math.sqrt(((Double) obj).doubleValue())))});
        postfix = CollectionsKt.listOf(new PostfixOperator[]{new PostfixOperator(CollectionsKt.listOf("%"), true, obj -> Double.valueOf(MathOperators.$r8$lambda$9knPl1bWilrikXdX83ClBN7MwRE(((Double) obj).doubleValue()))), new PostfixOperator(CollectionsKt.listOf("!"), false, obj -> Double.valueOf(MathOperators.INSTANCE.factorial(((Double) obj).doubleValue())), 2, null)});
        MathFunction mathFunction = new MathFunction("sqrt", new IntRange(1, 1), null, obj -> Double.valueOf(Math.sqrt(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction2 = new MathFunction("cbrt", new IntRange(1, 1), null, obj -> Double.valueOf(Math.cbrt(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction3 = new MathFunction("abs", new IntRange(1, 1), null, obj -> Double.valueOf(Math.abs(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction4 = new MathFunction("sign", new IntRange(1, 1), null, obj -> Double.valueOf(Math.signum(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction5 = new MathFunction("round", new IntRange(1, 1), null, obj -> Double.valueOf(MathKt.roundToLong(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction6 = new MathFunction("floor", new IntRange(1, 1), null, obj -> Double.valueOf(Math.floor(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction7 = new MathFunction("ceil", new IntRange(1, 1), null, obj -> Double.valueOf(Math.ceil(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction8 = new MathFunction("fact", new IntRange(1, 1), null, obj -> Double.valueOf(MathOperators.INSTANCE.factorial(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction9 = new MathFunction("min", new IntRange(1, Integer.MAX_VALUE), null, obj -> Double.valueOf(ArraysKt.minOrThrow((double[]) obj)), 4, null);
        MathFunction mathFunction10 = new MathFunction("max", new IntRange(1, Integer.MAX_VALUE), null, obj -> Double.valueOf(ArraysKt.maxOrThrow((double[]) obj)), 4, null);
        MathFunction mathFunction11 = new MathFunction("log", new IntRange(1, 1), null, obj -> Double.valueOf(Math.log10(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction12 = new MathFunction("log2", new IntRange(1, 1), null, obj -> Double.valueOf(MathKt.log2(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction13 = new MathFunction("ln", new IntRange(1, 1), null, obj -> Double.valueOf(Math.log(((double[]) obj)[0])), 4, null);
        MathFunction mathFunction14 = new MathFunction("exp", new IntRange(1, 1), null, obj -> Double.valueOf(Math.exp(((double[]) obj)[0])), 4, null);
        IntRange intRange = new IntRange(1, 1);
        AngleUse angleUse = AngleUse.ARGUMENT;
        MathFunction mathFunction15 = new MathFunction("sin", intRange, angleUse, obj -> Double.valueOf(Math.sin(((double[]) obj)[0])));
        MathFunction mathFunction16 = new MathFunction("cos", new IntRange(1, 1), angleUse, obj -> Double.valueOf(Math.cos(((double[]) obj)[0])));
        MathFunction mathFunction17 = new MathFunction("tan", new IntRange(1, 1), angleUse, obj -> Double.valueOf(Math.tan(((double[]) obj)[0])));
        IntRange intRange2 = new IntRange(1, 1);
        AngleUse angleUse2 = AngleUse.RESULT;
        functions = CollectionsKt.listOf(new MathFunction[]{mathFunction, mathFunction2, mathFunction3, mathFunction4, mathFunction5, mathFunction6, mathFunction7, mathFunction8, mathFunction9, mathFunction10, mathFunction11, mathFunction12, mathFunction13, mathFunction14, mathFunction15, mathFunction16, mathFunction17, new MathFunction("asin", intRange2, angleUse2, obj -> Double.valueOf(Math.asin(((double[]) obj)[0]))), new MathFunction("acos", new IntRange(1, 1), angleUse2, obj -> Double.valueOf(Math.acos(((double[]) obj)[0]))), new MathFunction("atan", new IntRange(1, 1), angleUse2, obj -> Double.valueOf(Math.atan(((double[]) obj)[0]))), new MathFunction("atan2", new IntRange(2, 2), angleUse2, obj -> {
double[] dArr = (double[]) obj;
                return Double.valueOf(Math.atan2(dArr[0], dArr[1]));
}), new MathFunction("sinh", new IntRange(1, 1), null, obj -> Double.valueOf(Math.sinh(((double[]) obj)[0])), 4, null), new MathFunction("cosh", new IntRange(1, 1), null, obj -> Double.valueOf(Math.cosh(((double[]) obj)[0])), 4, null), new MathFunction("tanh", new IntRange(1, 1), null, obj -> Double.valueOf(Math.tanh(((double[]) obj)[0])), 4, null)});
        Map mapCreateMapBuilder = MapsKt.createMapBuilder();
        multipliers$lambda$35$add(mapCreateMapBuilder, 1000.0d, "к", "k", "тыс", "тысяча", "тысячи", "тысяч", "thousand", "thousands");
        multipliers$lambda$35$add(mapCreateMapBuilder, 1000000.0d, "кк", "kk", "млн", "миллион", "миллиона", "миллионов", "million", "millions");
        multipliers$lambda$35$add(mapCreateMapBuilder, 1.0E9d, "ккк", "kkk", "млрд", "миллиард", "миллиарда", "миллиардов", "billion", "billions");
        multipliers = MapsKt.build(mapCreateMapBuilder);
        gluedMultipliers = SetsKt.setOf(new String[]{"к", "k", "кк", "kk", "ккк", "kkk"});
        percentOf = SetsKt.setOf(new String[]{"от", "of"});
        bases = MapsKt.mapOf(TuplesKt.to("bin", 2), TuplesKt.to("oct", 8), TuplesKt.to("hex", 16), TuplesKt.to("dec", 10));
        Double dValueOf = Double.valueOf(3.141592653589793d);
        constants = MapsKt.mapOf(TuplesKt.to("pi", dValueOf), TuplesKt.to("π", dValueOf), TuplesKt.to("e", Double.valueOf(2.718281828459045d)));
        List listCreateListBuilder = CollectionsKt.createListBuilder();
        Iterator it = listListOf2.iterator();
        while (it.hasNext()) {
            listCreateListBuilder.addAll(((InfixOperator) it.next()).getSymbols());
        }
        Iterator it2 = prefix.iterator();
        while (it2.hasNext()) {
            listCreateListBuilder.addAll(((PrefixOperator) it2.next()).getSymbols());
        }
        Iterator it3 = postfix.iterator();
        while (it3.hasNext()) {
            listCreateListBuilder.addAll(((PostfixOperator) it3.next()).getSymbols());
        }
        List<String> listSortedWith = CollectionsKt.sortedWith(CollectionsKt.distinct(CollectionsKt.build(listCreateListBuilder)), new Comparator<String>() {
            @Override
            public final int compare(String t, String t2) {
                return ComparisonsKt.compareValues(Integer.valueOf(((String) t2).length()), Integer.valueOf(((String) t).length()));
            }
        });
        symbols = listSortedWith;
        Set setCreateSetBuilder = SetsKt.createSetBuilder();
        Iterator it4 = listSortedWith.iterator();
        while (true) {
            if (!it4.hasNext()) {
                break;
            }
            String str = (String) it4.next();
            for (int i = 0; i < str.length(); i++) {
                setCreateSetBuilder.add(Character.valueOf(str.charAt(i)));
            }
        }
        for (String str2 : constants.keySet()) {
            for (int i2 = 0; i2 < str2.length(); i2++) {
                char cCharAt = str2.charAt(i2);
                if (!Character.isLetter(cCharAt)) {
                    setCreateSetBuilder.add(Character.valueOf(cCharAt));
                }
            }
        }
        setCreateSetBuilder.add('(');
        setCreateSetBuilder.add(')');
        setCreateSetBuilder.add(';');
        symbolChars = SetsKt.build(setCreateSetBuilder);
    }

    public static double $r8$lambda$ZVrQvXZPmKA6MKcS5cA3hZbE5s0(double d, double d2, boolean z) {
        return z ? d + (d2 * d) : d + d2;
    }
    public static double m1536$r8$lambda$LSDKb6aiRHvNLPBAkDSZnVorXU(double d, double d2, boolean z) {
        return z ? d - (d2 * d) : d - d2;
    }

    public static double $r8$lambda$k5EXF16m3PjNI8p3YfxpxexwkF8(double d, double d2, boolean z) {
        return d * d2;
    }

    public static double $r8$lambda$kelnVd_Ka84nxlxlbuCSTjjpYkM(double d, double d2, boolean z) {
        return d / d2;
    }
    public static double m1541$r8$lambda$pYr6NR2WMrxTJl51URT4Km1qE(double d) {
        return -d;
    }

    public static double $r8$lambda$9knPl1bWilrikXdX83ClBN7MwRE(double d) {
        return d / 100.0d;
    }

    public final Map<String, Double> getMultipliers() {
        return multipliers;
    }

    public final Set<String> getGluedMultipliers() {
        return gluedMultipliers;
    }

    public final Set<String> getPercentOf() {
        return percentOf;
    }

    public final Map<String, Integer> getBases() {
        return bases;
    }

    public final String stripRadixPrefix(String digits, int radix) {
        return (digits.length() > 2 && digits.charAt(0) == '0' && radixFor(digits.charAt(1)) == radix) ? digits.substring(2) : digits;
    }

    public final Map<String, Double> getConstants() {
        return constants;
    }

    public final Set<Character> getSymbolChars() {
        return symbolChars;
    }

    public final String matchSymbol(CharSequence text, int at) {
        Object next;
        CharSequence charSequence;
        int i;
        Iterator it = symbols.iterator();
        while (it.hasNext()) {
            next = it.next();
            String str = (String) next;
            if (str.length() + at <= text.length()) {
                charSequence = text;
                i = at;
                if (KtCompat.regionMatches(charSequence, i, (CharSequence) str, 0, str.length(), false, 16, (Object) null)) {
                    return (String) next;
                }
            } else {
                charSequence = text;
                i = at;
            }
            text = charSequence;
            at = i;
        }
        next = null;
        return (String) next;
    }

    public final InfixOperator infixFor(String symbol) {
        Object next;
        Iterator it = infix.iterator();
        while (it.hasNext()) {
            next = it.next();
            if (((InfixOperator) next).getSymbols().contains(symbol)) {
                return (InfixOperator) next;
            }
        }
        next = null;
        return (InfixOperator) next;
    }

    public final PrefixOperator prefixFor(String symbol) {
        Object next;
        Iterator it = prefix.iterator();
        while (it.hasNext()) {
            next = it.next();
            if (((PrefixOperator) next).getSymbols().contains(symbol)) {
                return (PrefixOperator) next;
            }
        }
        next = null;
        return (PrefixOperator) next;
    }

    public final PostfixOperator postfixFor(String symbol) {
        Object next;
        Iterator it = postfix.iterator();
        while (it.hasNext()) {
            next = it.next();
            if (((PostfixOperator) next).getSymbols().contains(symbol)) {
                return (PostfixOperator) next;
            }
        }
        next = null;
        return (PostfixOperator) next;
    }

    public final MathFunction functionFor(String name) {
        Object next;
        Iterator it = functions.iterator();
        while (it.hasNext()) {
            next = it.next();
            if (Intrinsics.areEqual(((MathFunction) next).getName(), name)) {
                return (MathFunction) next;
            }
        }
        next = null;
        return (MathFunction) next;
    }
    public final double factorial(double value) {
        if (value < 0.0d || value != Math.floor(value) || value > 170.0d) {
            return Double.NaN;
        }
        int i = (int) value;
        double d = 1.0d;
        int i2 = 2;
        if (2 <= i) {
            while (true) {
                d *= (double) i2;
                if (i2 == i) {
                    break;
                }
                i2++;
            }
        }
        return d;
    }

    private static final void multipliers$lambda$35$add(Map<String, Double> map, double d, String... strArr) {
        for (String str : strArr) {
            map.put(str, Double.valueOf(d));
        }
    }
}

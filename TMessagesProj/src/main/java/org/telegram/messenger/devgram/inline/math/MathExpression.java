package org.telegram.messenger.devgram.inline.math;

import kotlin.NoWhenBranchMatchedException;

import org.telegram.messenger.devgram.inline.rates.Currencies;
import org.telegram.messenger.devgram.inline.rates.CurrencyRates;
import org.telegram.messenger.devgram.inline.time.TimeZoneOptions;
import org.telegram.messenger.devgram.inline.time.TimeZones;
import org.telegram.messenger.devgram.inline.units.Measures;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.IntRange;
import kotlin.text.StringsKt;
public final class MathExpression {
    public static final MathExpression INSTANCE = new MathExpression();
    private static final MathParseError error = new MathParseError();
    public final boolean isBlank(char c2) {
        return c2 == ' ' || c2 == '\t' || c2 == 160;
    }

    private MathExpression() {
    }
    public static final class MathParseError extends RuntimeException {
        public MathParseError() {
            super(null, null, false, false);
        }
    }
    public static final class Token {
        private final double number;
        private final String text;
        private final int type;
        private MathUnit unit;
        private final List<MathUnit> units;

        public Token(int i, double d, String str, List<MathUnit> list) {
            this.type = i;
            this.number = d;
            this.text = str;
            this.units = list;
            this.unit = (MathUnit) CollectionsKt.firstOrNull((List) list);
        }

        public /* synthetic */ Token(int i, double d, String str, List list, int i2, DefaultConstructorMarker defaultConstructorMarker) {
            this(i, (i2 & 2) != 0 ? 0.0d : d, (i2 & 4) != 0 ? "" : str, (i2 & 8) != 0 ? CollectionsKt.emptyList() : list);
        }

        public final double getNumber() {
            return this.number;
        }

        public final String getText() {
            return this.text;
        }

        public final int getType() {
            return this.type;
        }

        public final List<MathUnit> getUnits() {
            return this.units;
        }

        public final MathUnit getUnit() {
            return this.unit;
        }

        public final void setUnit(MathUnit mathUnit) {
            this.unit = mathUnit;
        }
    }
    public static final class Operand {
        private final boolean isPercent;
        private final MathUnit unit;
        private final double value;

        public Operand(double d, boolean z, MathUnit mathUnit) {
            this.value = d;
            this.isPercent = z;
            this.unit = mathUnit;
        }

        public /* synthetic */ Operand(double d, boolean z, MathUnit mathUnit, int i, DefaultConstructorMarker defaultConstructorMarker) {
            this(d, z, (i & 4) != 0 ? null : mathUnit);
        }

        public final MathUnit getUnit() {
            return this.unit;
        }

        public final double getValue() {
            return this.value;
        }
        public final boolean getIsPercent() {
            return this.isPercent;
        }
    }

    public final MathResult evaluate(CharSequence expression, MathOptions options) {
        try {
            return new Parser(expression, options).parse();
        } catch (MathParseError unused) {
            return null;
        }
    }

    public final String format(MathResult result) {
        String measure;
        if (result.getBase() != 10) {
            return MathFormat.INSTANCE.formatBase(result.getValue(), result.getBase());
        }
        MathUnit unit = result.getUnit();
        if (unit == null) {
            return MathFormat.INSTANCE.format(result.getValue(), result.getDecimalSeparator(), result.getGrouping());
        }
        if (unit.getKind() == UnitKind.CURRENCY) {
            measure = MathFormat.INSTANCE.formatMoney(result.getValue(), Currencies.INSTANCE.fractionDigits(unit.getCode()), result.getDecimalSeparator(), result.getGrouping());
        } else {
            if (result.getBreakdown()) {
                return Measures.INSTANCE.formatDuration(unit.toBase(result.getValue()), result.getDecimalSeparator(), result.getRussian());
            }
            measure = MathFormat.INSTANCE.formatMeasure(result.getValue(), result.getDecimalSeparator(), result.getGrouping());
        }
        if (measure == null) {
            return null;
        }
        return measure + " " + unit.label(result.getRussian());
    }

    public final MathSuggestion suggestionAt(CharSequence text, int caret, MathOptions options) {
        String str;
        String strValueAt;
        if (caret > 1 && caret <= text.length()) {
            int i = caret - 1;
            if (text.charAt(i) == '=') {
                int i2 = caret;
                while (i2 < text.length() && isBlank(text.charAt(i2))) {
                    i2++;
                }
                if (i2 < text.length()) {
                    char cCharAt = text.charAt(i2);
                    if (Character.isDigit(cCharAt) || cCharAt == '.' || cCharAt == ',') {
                        return null;
                    }
                }
                TimeZoneOptions timeZones = options.getTimeZones();
                if (timeZones != null && (strValueAt = TimeZones.INSTANCE.valueAt(text, i, timeZones)) != null) {
                    return INSTANCE.suggestion(text, caret, strValueAt);
                }
                Integer numExpressionStart = expressionStart(text, i);
                if (numExpressionStart != null) {
                    int iIntValue = numExpressionStart.intValue();
                    while (true) {
                        if (!looksLikeDate(text, iIntValue, i)) {
                            StringBuilder sb = new StringBuilder(i - iIntValue);
                            sb.append(text, iIntValue, i);
                            MathResult mathResultEvaluate = evaluate(sb, options);
                            if (mathResultEvaluate != null) {
                                if (!mathResultEvaluate.getHasOperation() || ((mathResultEvaluate.getUnit() == null && !options.getArithmetic()) || (str = format(mathResultEvaluate)) == null)) {
                                    return null;
                                }
                                return suggestion(text, caret, str);
                            }
                        }
                        Integer numNextCandidate = nextCandidate(text, iIntValue, i);
                        if (numNextCandidate == null) {
                            break;
                        }
                        iIntValue = numNextCandidate.intValue();
                    }
                }
            }
        }
        return null;
    }

    private final MathSuggestion suggestion(CharSequence text, int caret, String value) {
        String str;
        if (isBlank(text.charAt(caret - 2))) {
            str = " " + value;
        } else {
            str = value;
        }
        return new MathSuggestion(caret, str, value);
    }

    private final boolean looksLikeDate(CharSequence text, int from, int to) {
        Integer num;
        Integer num2;
        while (from < to && isBlank(text.charAt(from))) {
            from++;
        }
        while (to > from && isBlank(text.charAt(to - 1))) {
            to--;
        }
        if (to - from < 6) {
            return false;
        }
        ArrayList arrayList = new ArrayList(4);
        int i = 0;
        char c2 = ' ';
        while (from < to) {
            char cCharAt = text.charAt(from);
            if (Character.isDigit(cCharAt)) {
                i++;
            } else {
                if (cCharAt != '/' && cCharAt != '.' && cCharAt != '-') {
                    return false;
                }
                if (c2 == ' ') {
                    c2 = cCharAt;
                } else if (c2 != cCharAt) {
                    return false;
                }
                if (i == 0 || arrayList.size() == 2) {
                    return false;
                }
                arrayList.add(Integer.valueOf(i));
                i = 0;
            }
            from++;
        }
        if (i != 0 && arrayList.size() == 2) {
            arrayList.add(Integer.valueOf(i));
            switch (c2) {
                case '-':
                    Integer num3 = (Integer) arrayList.get(0);
                    if (num3 != null && num3.intValue() == 4 && ((Number) arrayList.get(1)).intValue() <= 2 && ((Number) arrayList.get(2)).intValue() <= 2) {
                        return true;
                    }
                    break;
                case '.':
                case '/':
                    return ((Number) arrayList.get(0)).intValue() <= 2 && ((Number) arrayList.get(1)).intValue() <= 2 && (((num = (Integer) arrayList.get(2)) != null && num.intValue() == 2) || ((num2 = (Integer) arrayList.get(2)) != null && num2.intValue() == 4));
                default:
                    return false;
            }
        }
        return false;
    }

    private final Integer nextCandidate(CharSequence text, int from, int end) {
        char c2 = ' ';
        while (from < end) {
            char cCharAt = text.charAt(from);
            from++;
            if (cCharAt == '(') {
                return Integer.valueOf(from);
            }
            if (isBlank(cCharAt)) {
                while (from < end && isBlank(text.charAt(from))) {
                    from++;
                }
                if (from >= end) {
                    return null;
                }
                if (cCharAt != 160 || !Character.isDigit(c2) || !Character.isDigit(text.charAt(from))) {
                    return Integer.valueOf(from);
                }
            } else {
                c2 = cCharAt;
            }
        }
        return null;
    }

    private final Integer expressionStart(CharSequence text, int equalsIndex) {
        int iMax = Math.max(0, equalsIndex - 64);
        int i = equalsIndex - 1;
        while (i >= iMax && isExpressionChar(text.charAt(i))) {
            i--;
        }
        do {
            i++;
            if (i >= equalsIndex) {
                break;
            }
        } while (isBlank(text.charAt(i)));
        if (i >= equalsIndex) {
            return null;
        }
        if (i > 0) {
            char cCharAt = text.charAt(i - 1);
            if (Character.isLetter(cCharAt)) {
                while (i < equalsIndex && !isBlank(text.charAt(i))) {
                    i++;
                }
                while (i < equalsIndex && isBlank(text.charAt(i))) {
                    i++;
                }
                if (i >= equalsIndex) {
                    return null;
                }
            } else if (Character.isDigit(cCharAt) || cCharAt == '_') {
                return null;
            }
        }
        return Integer.valueOf(i);
    }
    public final boolean isWordChar(char c2) {
        return Character.isLetter(c2) || Character.isDigit(c2) || c2 == 178;
    }

    private final boolean isExpressionChar(char c2) {
        return Character.isDigit(c2) || Character.isLetter(c2) || c2 == '.' || c2 == ',' || isBlank(c2) || c2 == 176 || c2 == 8451 || c2 == 8457 || c2 == 178 || c2 == 65039 || MathOperators.INSTANCE.getSymbolChars().contains(Character.valueOf(c2)) || Currencies.INSTANCE.getSymbolChars().contains(Character.valueOf(c2));
    }
    public static final class Parser {
        private int depth;
        private boolean expanded;
        private boolean explicitTarget;
        private boolean hasOperation;
        private final MathOptions options;
        private int position;
        private final boolean russian;
        private Character separator;
        private final CharSequence source;
        private final ArrayList<Token> tokens = new ArrayList<>();
        private int base = 10;

        public static final /* synthetic */ class WhenMappings {
            public static final /* synthetic */ int[] $EnumSwitchMapping$0;

            static {
                int[] iArr = new int[UnitRule.values().length];
                try {
                    iArr[UnitRule.SAME.ordinal()] = 1;
                } catch (NoSuchFieldError unused) {
                }
                try {
                    iArr[UnitRule.SCALE.ordinal()] = 2;
                } catch (NoSuchFieldError unused2) {
                }
                try {
                    iArr[UnitRule.RATIO.ordinal()] = 3;
                } catch (NoSuchFieldError unused3) {
                }
                try {
                    iArr[UnitRule.NONE.ordinal()] = 4;
                } catch (NoSuchFieldError unused4) {
                }
                $EnumSwitchMapping$0 = iArr;
            }
        }

        public Parser(CharSequence charSequence, MathOptions mathOptions) {
            this.source = charSequence;
            this.options = mathOptions;
            boolean z = false;
            for (int i = 0; i < charSequence.length(); i++) {
                char cCharAt = charSequence.charAt(i);
                if ((1072 <= cCharAt && cCharAt < 1104) || ((1040 <= cCharAt && cCharAt < 1072) || cCharAt == 1105 || cCharAt == 1025)) {
                    z = true;
                    break;
                }
            }
            this.russian = z;
        }

        public final MathResult parse() {
            MathUnit mathUnitImplicitTarget;
            tokenize();
            resolveUnits();
            Operand conversion = parseConversion();
            expect(10);
            MathUnit unit = conversion.getUnit();
            boolean z = false;
            if (unit != null) {
                if (unit.getKind() == UnitKind.DURATION) {
                    if (!this.explicitTarget && Measures.INSTANCE.canBreakDown(unit)) {
                        z = true;
                    }
                    if (!this.hasOperation && z) {
                        this.hasOperation = Measures.INSTANCE.breaksDown(conversion.getValue(), unit);
                    }
                } else if (!this.hasOperation && (mathUnitImplicitTarget = implicitTarget(unit, conversion.getValue())) != null) {
                    conversion = convert(conversion, mathUnitImplicitTarget);
                }
            }
            boolean z2 = z;
            if (this.expanded) {
                this.hasOperation = true;
            }
            double value = conversion.getValue();
            boolean z3 = this.hasOperation;
            Character ch = this.separator;
            return new MathResult(value, z3, ch != null ? ch.charValue() : this.options.getDecimalSeparator(), conversion.getUnit(), this.russian, z2, this.options.getGrouping(), this.base);
        }

        private final MathUnit implicitTarget(MathUnit unit, double value) {
            if (unit.getKind() != UnitKind.CURRENCY) {
                return Measures.INSTANCE.implicitTarget(unit, value, this.options.getImperial());
            }
            String localCurrency = this.options.getLocalCurrency();
            if (localCurrency == null) {
                localCurrency = "USD";
            }
            String str = Intrinsics.areEqual(localCurrency, unit.getCode()) ? "USD" : localCurrency;
            if (Intrinsics.areEqual(str, unit.getCode())) {
                return null;
            }
            return Currencies.INSTANCE.unit(str);
        }

        private final void tokenize() {
            int i;
            int i2;
            int number = 0;
            while (number < this.source.length()) {
                char cCharAt = this.source.charAt(number);
                MathExpression mathExpression = MathExpression.INSTANCE;
                if (!mathExpression.isBlank(cCharAt) && cCharAt != 65039) {
                    if (Character.isDigit(cCharAt) || ((cCharAt == '.' || cCharAt == ',') && (i = number + 1) < this.source.length() && Character.isDigit(this.source.charAt(i)))) {
                        number = readNumber(number);
                        continue;
                    } else if (cCharAt == '(') {
                        this.tokens.add(new Token(2, 0.0d, null, null, 14, null));
                    } else if (cCharAt == ')') {
                        this.tokens.add(new Token(3, 0.0d, null, null, 14, null));
                    } else if (cCharAt == ';' || (cCharAt == ',' && ((i2 = number + 1) == this.source.length() || mathExpression.isBlank(this.source.charAt(i2))))) {
                        this.tokens.add(new Token(5, 0.0d, null, null, 14, null));
                    } else if (cCharAt == 176) {
                        number = readTemperature(number);
                        continue;
                    } else if (Character.isLetter(cCharAt)) {
                        int iWordEnd = wordEnd(number);
                        number = addWord(this.source.subSequence(number, iWordEnd).toString(), iWordEnd);
                        continue;
                    } else {
                        MathUnit mathUnitSymbol = null;
                        MathUnit mathUnitSymbol2 = this.options.getUnits() ? Measures.INSTANCE.symbol(cCharAt) : null;
                        if (mathUnitSymbol2 == null) {
                            CurrencyRates rates = this.options.getRates();
                            if (rates != null) {
                                mathUnitSymbol = Currencies.INSTANCE.symbol(cCharAt, rates);
                            }
                        } else {
                            mathUnitSymbol = mathUnitSymbol2;
                        }
                        if (mathUnitSymbol != null) {
                            this.tokens.add(new Token(6, 0.0d, null, CollectionsKt.listOf(mathUnitSymbol), 6, null));
                        } else {
                            String strMatchSymbol = MathOperators.INSTANCE.matchSymbol(this.source, number);
                            if (strMatchSymbol == null) {
                                throw MathExpression.error;
                            }
                            this.tokens.add(new Token(1, 0.0d, strMatchSymbol, null, 10, null));
                            number += strMatchSymbol.length();
                            continue;
                        }
                    }
                }
                number++;
            }
            this.tokens.add(new Token(10, 0.0d, null, null, 14, null));
        }

        private final void resolveUnits() {
            Object next;
            HashSet hashSet = new HashSet();
            for (Token token : this.tokens) {
                if (token.getType() == 6 && token.getUnits().size() == 1) {
                    hashSet.add(token.getUnits().get(0).getKind());
                }
            }
            for (Token token2 : this.tokens) {
                if (token2.getType() == 6 && token2.getUnits().size() > 1) {
                    Iterator it = token2.getUnits().iterator();
                    do {
                        if (!it.hasNext()) {
                            next = null;
                            break;
                        }
                        next = it.next();
                    } while (!hashSet.contains(((MathUnit) next).getKind()));
                    MathUnit mathUnit = (MathUnit) next;
                    if (mathUnit == null) {
                        mathUnit = token2.getUnits().get(0);
                    }
                    token2.setUnit(mathUnit);
                }
            }
        }

        private final int wordEnd(int from) {
            while (from < this.source.length() && MathExpression.INSTANCE.isWordChar(this.source.charAt(from))) {
                from++;
            }
            return from;
        }

        private final int skipBlanks(int from) {
            while (from < this.source.length() && MathExpression.INSTANCE.isBlank(this.source.charAt(from))) {
                from++;
            }
            return from;
        }

        private final int readTemperature(int from) {
            int i;
            if (!this.options.getUnits()) {
                throw MathExpression.error;
            }
            int iSkipBlanks = skipBlanks(from + 1);
            if (iSkipBlanks >= this.source.length() || ((i = iSkipBlanks + 1) < this.source.length() && MathExpression.INSTANCE.isWordChar(this.source.charAt(i)))) {
                throw MathExpression.error;
            }
            MathUnit mathUnitTemperature = Measures.INSTANCE.temperature(this.source.charAt(iSkipBlanks));
            if (mathUnitTemperature == null) {
                throw MathExpression.error;
            }
            this.tokens.add(new Token(6, 0.0d, null, CollectionsKt.listOf(mathUnitTemperature), 6, null));
            return i;
        }

        /* JADX WARN: Multi-variable type inference failed */
        private final int addWord(String word, int end) {
            int i;
            MathUnit mathUnitWord;
            MathUnit mathUnitWord2;
            int compound;
            String lowerCase = word.toLowerCase(Locale.ROOT);
            MathOperators mathOperators = MathOperators.INSTANCE;
            if (mathOperators.getPercentOf().contains(lowerCase)) {
                this.tokens.add(new Token(8, 0.0d, null, null, 14, null));
                return end;
            }
            CurrencyRates rates = this.options.getRates();
            Currencies currencies = Currencies.INSTANCE;
            if (currencies.isConversion(word)) {
                this.tokens.add(new Token(7, 0.0d, null, null, 14, null));
                return end;
            }
            int iSkipBlanks = skipBlanks(end);
            boolean objArr = iSkipBlanks < this.source.length() && this.source.charAt(iSkipBlanks) == '(';
            if (objArr && Intrinsics.areEqual(lowerCase, "dec")) {
                this.tokens.add(new Token(4, 0.0d, lowerCase, null, 10, null));
                this.tokens.add(new Token(2, 0.0d, null, null, 14, null));
                int iSkipBlanks2 = skipBlanks(iSkipBlanks + 1);
                int iWordEnd = wordEnd(iSkipBlanks2);
                if (iWordEnd > iSkipBlanks2) {
                    this.tokens.add(new Token(9, 0.0d, this.source.subSequence(iSkipBlanks2, iWordEnd).toString(), null, 10, null));
                }
                return iWordEnd;
            }
            if (!objArr) {
                if (this.options.getUnits() && (compound = readCompound(word, end)) >= 0) {
                    return compound;
                }
                boolean z = mathOperators.getConstants().containsKey(lowerCase) || mathOperators.getBases().containsKey(lowerCase) || mathOperators.functionFor(lowerCase) != null;
                ArrayList arrayList = new ArrayList(2);
                if (rates != null && !z && (mathUnitWord2 = currencies.word(word, rates)) != null) {
                    arrayList.add(mathUnitWord2);
                }
                if (this.options.getUnits() && (mathUnitWord = Measures.INSTANCE.word(word)) != null) {
                    arrayList.add(mathUnitWord);
                }
                if (!arrayList.isEmpty()) {
                    this.tokens.add(new Token(6, 0.0d, null, arrayList, 6, null));
                    return (end >= this.source.length() || this.source.charAt(end) != '.' || ((i = end + 1) != this.source.length() && Character.isDigit(this.source.charAt(i)))) ? end : end + 1;
                }
            }
            this.tokens.add(new Token(4, 0.0d, lowerCase, null, 10, null));
            return end;
        }

        private final int readCompound(String first, int end) {
            boolean z = end < this.source.length() && this.source.charAt(end) == '/';
            int iSkipBlanks = z ? end + 1 : skipBlanks(end);
            if (iSkipBlanks >= this.source.length() || !Character.isLetter(this.source.charAt(iSkipBlanks)) || (!z && iSkipBlanks == end)) {
                return -1;
            }
            int iWordEnd = wordEnd(iSkipBlanks);
            MathUnit mathUnitCompound = Measures.INSTANCE.compound(first, z ? '/' : ' ', this.source.subSequence(iSkipBlanks, iWordEnd).toString());
            if (mathUnitCompound == null) {
                return -1;
            }
            this.tokens.add(new Token(6, 0.0d, null, CollectionsKt.listOf(mathUnitCompound), 6, null));
            return iWordEnd;
        }

        private final int readNumber(int from) {
            int iScanNumber;
            int i;
            boolean z = false;
            int iRadixFor = (this.source.charAt(from) != '0' || from + 2 >= this.source.length()) ? 0 : MathOperators.INSTANCE.radixFor(this.source.charAt(from + 1));
            if (iRadixFor != 0) {
                int i2 = from + 2;
                if (Character.digit(this.source.charAt(i2), iRadixFor) >= 0) {
                    int iWordEnd = wordEnd(i2);
                    this.tokens.add(new Token(0, parseDigits(this.source.subSequence(i2, iWordEnd).toString(), iRadixFor), null, null, 12, null));
                    this.expanded = true;
                    return iWordEnd;
                }
            }
            int iScanNumber2 = scanNumber(from, false);
            if ((this.options.getRates() != null || this.options.getUnits()) && (iScanNumber = scanNumber(from, true)) > iScanNumber2 && isAmountAt(iScanNumber)) {
                iScanNumber2 = iScanNumber;
            }
            StringBuilder sb = new StringBuilder(iScanNumber2 - from);
            while (true) {
                if (from >= iScanNumber2) {
                    break;
                }
                char cCharAt = this.source.charAt(from);
                if (!MathExpression.INSTANCE.isBlank(cCharAt)) {
                    sb.append(cCharAt != ',' ? cCharAt : '.');
                }
                from++;
            }
            Double doubleOrNull = StringsKt.toDoubleOrNull(sb.toString());
            if (doubleOrNull == null) {
                throw MathExpression.error;
            }
            double dDoubleValue = doubleOrNull.doubleValue();
            if (iScanNumber2 < this.source.length() && Character.isLetter(this.source.charAt(iScanNumber2))) {
                z = true;
            }
            int iSkipBlanks = z ? iScanNumber2 : skipBlanks(iScanNumber2);
            if ((iSkipBlanks > iScanNumber2 || z) && iSkipBlanks < this.source.length() && Character.isLetter(this.source.charAt(iSkipBlanks))) {
                int iWordEnd2 = wordEnd(iSkipBlanks);
                String lowerCase = this.source.subSequence(iSkipBlanks, iWordEnd2).toString().toLowerCase(Locale.ROOT);
                MathOperators mathOperators = MathOperators.INSTANCE;
                Double d = mathOperators.getMultipliers().get(lowerCase);
                if (d != null && (z || !mathOperators.getGluedMultipliers().contains(lowerCase))) {
                    dDoubleValue *= d.doubleValue();
                    this.expanded = true;
                    iScanNumber2 = (iWordEnd2 >= this.source.length() || this.source.charAt(iWordEnd2) != '.' || ((i = iWordEnd2 + 1) != this.source.length() && Character.isDigit(this.source.charAt(i)))) ? iWordEnd2 : i;
                }
            }
            this.tokens.add(new Token(0, dDoubleValue, null, null, 12, null));
            return iScanNumber2;
        }

        private final int scanNumber(int from, boolean grouping) {
            int i;
            int i2;
            int i3 = from;
            while (i3 < this.source.length() && Character.isDigit(this.source.charAt(i3))) {
                i3++;
            }
            int i4 = i3 - from;
            if (1 <= i4 && i4 < 4) {
                while (i3 + 3 < this.source.length() && isGroupSeparator(this.source.charAt(i3), grouping) && isDigitGroup(i3 + 1)) {
                    i3 += 4;
                    i4 += 3;
                }
            }
            char cCharAt = i3 < this.source.length() ? this.source.charAt(i3) : ' ';
            if ((cCharAt == '.' || cCharAt == ',') && (i = i3 + 1) < this.source.length() && Character.isDigit(this.source.charAt(i))) {
                i3 = i;
                while (i3 < this.source.length() && Character.isDigit(this.source.charAt(i3))) {
                    i3++;
                }
                i2 = i3 - i;
                if (cCharAt == ',' && i2 == 3 && 1 <= i4 && i4 < 4 && this.source.charAt(from) != '0') {
                    throw MathExpression.error;
                }
                if (this.separator == null) {
                    this.separator = Character.valueOf(cCharAt);
                }
            } else {
                i2 = 0;
            }
            if (i4 == 0 && i2 == 0) {
                throw MathExpression.error;
            }
            return i3;
        }

        private final boolean isGroupSeparator(char c2, boolean spaces) {
            if (c2 != 160) {
                return spaces && MathExpression.INSTANCE.isBlank(c2);
            }
            return true;
        }

        private final boolean isAmountAt(int end) {
            Token token = (Token) CollectionsKt.lastOrNull((List) this.tokens);
            if (token != null && token.getType() == 6) {
                return true;
            }
            int iSkipBlanks = skipBlanks(end);
            if (iSkipBlanks == this.source.length()) {
                return false;
            }
            char cCharAt = this.source.charAt(iSkipBlanks);
            CurrencyRates rates = this.options.getRates();
            if ((rates != null ? Currencies.INSTANCE.symbol(cCharAt, rates) : null) == null && (!this.options.getUnits() || (cCharAt != 176 && Measures.INSTANCE.symbol(cCharAt) == null))) {
                if (!Character.isLetter(cCharAt)) {
                    return false;
                }
                String string = this.source.subSequence(iSkipBlanks, wordEnd(iSkipBlanks)).toString();
                CurrencyRates rates2 = this.options.getRates();
                if ((rates2 != null ? Currencies.INSTANCE.word(string, rates2) : null) == null && (!this.options.getUnits() || Measures.INSTANCE.word(string) == null)) {
                    return false;
                }
            }
            return true;
        }

        private final boolean isDigitGroup(int at) {
            if (!Character.isDigit(this.source.charAt(at)) || !Character.isDigit(this.source.charAt(at + 1)) || !Character.isDigit(this.source.charAt(at + 2))) {
                return false;
            }
            int i = at + 3;
            return i == this.source.length() || !Character.isDigit(this.source.charAt(i));
        }

        private final Token peek() {
            return this.tokens.get(this.position);
        }

        private final Token next() {
            ArrayList<Token> arrayList = this.tokens;
            int i = this.position;
            this.position = i + 1;
            return arrayList.get(i);
        }

        private final Token expect(int type) {
            Token next = next();
            if (next.getType() == type) {
                return next;
            }
            throw MathExpression.error;
        }

        private final Operand parseExpression(int minPrecedence) {
            InfixOperator infixOperatorInfixFor;
            int i = this.depth + 1;
            this.depth = i;
            if (i > 32) {
                throw MathExpression.error;
            }
            try {
                Operand unary = parseUnary();
                while (true) {
                    Token tokenPeek = peek();
                    if (tokenPeek.getType() == 8) {
                        if (!unary.getIsPercent() || 20 < minPrecedence) {
                            break;
                        }
                        next();
                        Operand expression = parseExpression(21);
                        if (expression.getIsPercent()) {
                            throw MathExpression.error;
                        }
                        this.hasOperation = true;
                        unary = new Operand(unary.getValue() * expression.getValue(), false, expression.getUnit());
                    } else {
                        if (tokenPeek.getType() != 1 || (infixOperatorInfixFor = MathOperators.INSTANCE.infixFor(tokenPeek.getText())) == null || infixOperatorInfixFor.getPrecedence() < minPrecedence) {
                            break;
                        }
                        next();
                        Operand expression2 = parseExpression(infixOperatorInfixFor.getRightAssociative() ? infixOperatorInfixFor.getPrecedence() : infixOperatorInfixFor.getPrecedence() + 1);
                        this.hasOperation = true;
                        unary = combine(infixOperatorInfixFor, unary, expression2);
                    }
                }
                this.depth--;
                return unary;
            } catch (Throwable th) {
                this.depth--;
                throw th;
            }
        }

        private final Operand parseConversion() {
            Operand expression = parseExpression(0);
            while (peek().getType() == 7) {
                next();
                Token next = next();
                Integer num = next.getType() == 4 ? MathOperators.INSTANCE.getBases().get(next.getText()) : null;
                if (num != null) {
                    if (expression.getUnit() != null || peek().getType() != 10) {
                        throw MathExpression.error;
                    }
                    this.base = num.intValue();
                    this.hasOperation = true;
                } else {
                    if (next.getType() != 6) {
                        throw MathExpression.error;
                    }
                    MathUnit unit = next.getUnit();
                    if (unit == null) {
                        throw MathExpression.error;
                    }
                    expression = convert(expression, unit);
                    this.explicitTarget = true;
                }
            }
            return expression;
        }

        private final Operand convert(Operand operand, MathUnit target) {
            MathUnit unit = operand.getUnit();
            if (unit == null) {
                throw MathExpression.error;
            }
            this.hasOperation = true;
            return new Operand(convertValue(operand.getValue(), unit, target), false, target);
        }

        private final double convertValue(double value, MathUnit from, MathUnit to) {
            Double dRate;
            if (Intrinsics.areEqual(from.getCode(), to.getCode())) {
                return value;
            }
            if (from.getKind() != to.getKind()) {
                throw MathExpression.error;
            }
            if (from.getKind() == UnitKind.CURRENCY) {
                CurrencyRates rates = this.options.getRates();
                if (rates == null || (dRate = rates.rate(from.getCode(), to.getCode())) == null) {
                    throw MathExpression.error;
                }
                return value * dRate.doubleValue();
            }
            return to.fromBase(from.toBase(value));
        }

        /* JADX WARN: Code duplicated, block: B:31:0x007e A[PHI: r2
          0x007e: PHI (r2v3 double) = (r2v1 double), (r2v0 double) binds: [B:35:0x0088, B:30:0x007c] A[DONT_GENERATE, DONT_INLINE]] */
        private final Operand combine(InfixOperator operator, Operand left, Operand right) {
            MathUnit unit = left.getUnit();
            MathUnit unit2 = right.getUnit();
            if (unit == null && unit2 == null) {
                return new Operand(operator.getApply().invoke(Double.valueOf(left.getValue()), Double.valueOf(right.getValue()), Boolean.valueOf(right.getIsPercent())).doubleValue(), false, null, 4, null);
            }
            double value = right.getValue();
            int i = WhenMappings.$EnumSwitchMapping$0[operator.getUnits().ordinal()];
            if (i == 1) {
                if (unit != null && unit2 != null) {
                    value = convertValue(value, unit2, unit);
                }
                if (unit == null) {
                    unit = unit2;
                }
            } else if (i != 2) {
                if (i != 3) {
                    if (i == 4) {
                        throw MathExpression.error;
                    }
                    throw new NoWhenBranchMatchedException();
                }
                if (unit == null) {
                    throw MathExpression.error;
                }
                if (unit2 != null) {
                    value = convertValue(value, unit2, unit);
                    unit = null;
                }
            } else {
                if (unit != null && unit2 != null) {
                    throw MathExpression.error;
                }
                if (unit == null) {
                    unit = unit2;
                }
            }
            return new Operand(operator.getApply().invoke(Double.valueOf(left.getValue()), Double.valueOf(value), Boolean.valueOf(right.getIsPercent())).doubleValue(), false, unit);
        }

        private final Operand parseUnary() {
            PrefixOperator prefixOperatorPrefixFor;
            Token tokenPeek = peek();
            if (tokenPeek.getType() == 6) {
                MathUnit unit = tokenPeek.getUnit();
                if (unit == null) {
                    throw MathExpression.error;
                }
                if (unit.getKind() != UnitKind.CURRENCY) {
                    throw MathExpression.error;
                }
                next();
                Operand postfix = parsePostfix();
                if (postfix.getUnit() != null || postfix.getIsPercent()) {
                    throw MathExpression.error;
                }
                return new Operand(postfix.getValue(), false, unit);
            }
            if (tokenPeek.getType() == 1 && (prefixOperatorPrefixFor = MathOperators.INSTANCE.prefixFor(tokenPeek.getText())) != null) {
                next();
                Operand expression = parseExpression(prefixOperatorPrefixFor.getPrecedence());
                if (prefixOperatorPrefixFor.getOperation()) {
                    if (expression.getUnit() != null) {
                        throw MathExpression.error;
                    }
                    this.hasOperation = true;
                }
                return new Operand(prefixOperatorPrefixFor.getApply().invoke(Double.valueOf(expression.getValue())).doubleValue(), expression.getIsPercent(), expression.getUnit());
            }
            return parsePostfix();
        }

        private final Operand parsePostfix() {
            PostfixOperator postfixOperatorPostfixFor;
            Operand primary = parsePrimary();
            double value = primary.getValue();
            MathUnit unit = primary.getUnit();
            boolean percent = false;
            while (true) {
                Token tokenPeek = peek();
                if (tokenPeek.getType() == 6) {
                    if (unit != null || percent) {
                        break;
                    }
                    next();
                    unit = tokenPeek.getUnit();
                } else {
                    if (tokenPeek.getType() != 1 || (postfixOperatorPostfixFor = MathOperators.INSTANCE.postfixFor(tokenPeek.getText())) == null) {
                        return new Operand(value, percent, unit);
                    }
                    if (unit != null) {
                        throw MathExpression.error;
                    }
                    next();
                    value = postfixOperatorPostfixFor.getApply().invoke(Double.valueOf(value)).doubleValue();
                    this.hasOperation = true;
                    percent = postfixOperatorPostfixFor.getPercent();
                }
            }
            throw MathExpression.error;
        }

        private final Operand parsePrimary() {
            Token next = next();
            int type = next.getType();
            if (type == 0) {
                return new Operand(next.getNumber(), false, null, 4, null);
            }
            if (type == 2) {
                Operand conversion = parseConversion();
                expect(3);
                return new Operand(conversion.getValue(), false, conversion.getUnit());
            }
            if (type != 4) {
                throw MathExpression.error;
            }
            return new Operand(parseIdentifier(next.getText()), false, null, 4, null);
        }

        private final double parseIdentifier(String name) {
            MathOperators mathOperators = MathOperators.INSTANCE;
            Double d = mathOperators.getConstants().get(name);
            if (d != null) {
                return d.doubleValue();
            }
            Integer num = mathOperators.getBases().get(name);
            if (num != null) {
                return parseBase(name, num.intValue());
            }
            MathFunction mathFunctionFunctionFor = mathOperators.functionFor(name);
            if (mathFunctionFunctionFor == null) {
                throw MathExpression.error;
            }
            expect(2);
            ArrayList arrayList = new ArrayList(2);
            if (peek().getType() != 3) {
                arrayList.add(Double.valueOf(parseArgument()));
                while (peek().getType() == 5) {
                    next();
                    arrayList.add(Double.valueOf(parseArgument()));
                }
            }
            expect(3);
            IntRange arity = mathFunctionFunctionFor.getArity();
            int first = arity.getFirst();
            int last = arity.getLast();
            int size = arrayList.size();
            if (first > size || size > last) {
                throw MathExpression.error;
            }
            this.hasOperation = true;
            double[] doubleArray = CollectionsKt.toDoubleArray(arrayList);
            boolean z = this.options.getDegrees() && mathFunctionFunctionFor.getAngle() != AngleUse.NONE;
            if (z && mathFunctionFunctionFor.getAngle() == AngleUse.ARGUMENT) {
                int length = doubleArray.length;
                for (int i = 0; i < length; i++) {
                    doubleArray[i] = Math.toRadians(doubleArray[i]);
                }
            }
            double dDoubleValue = mathFunctionFunctionFor.getApply().invoke(doubleArray).doubleValue();
            if (mathFunctionFunctionFor.getAngle() != AngleUse.ARGUMENT || Math.abs(dDoubleValue) >= 1.0E-12d) {
                return (z && mathFunctionFunctionFor.getAngle() == AngleUse.RESULT) ? Math.toDegrees(dDoubleValue) : dDoubleValue;
            }
            return 0.0d;
        }

        private final double parseBase(String name, int radix) {
            double argument;
            int i;
            int iRadixFor = 0;
            boolean z = this.position == 1;
            expect(2);
            if (Intrinsics.areEqual(name, "dec")) {
                String text = expect(9).getText();
                if (peek().getType() == 5) {
                    next();
                    double argument2 = parseArgument();
                    if (argument2 != Math.floor(argument2) || 2 > (i = (int) argument2) || i >= 37) {
                        throw MathExpression.error;
                    }
                    argument = parseDigits(MathOperators.INSTANCE.stripRadixPrefix(text, i), i);
                } else {
                    if (text.length() > 2 && text.charAt(0) == '0') {
                        iRadixFor = MathOperators.INSTANCE.radixFor(text.charAt(1));
                    }
                    argument = iRadixFor != 0 ? parseDigits(text.substring(2), iRadixFor) : parseDigits(text, 10);
                }
            } else {
                argument = parseArgument();
            }
            expect(3);
            if (!Intrinsics.areEqual(name, "dec")) {
                if (!z || peek().getType() != 10) {
                    throw MathExpression.error;
                }
                this.base = radix;
            }
            this.hasOperation = true;
            return argument;
        }

        private final double parseDigits(String digits, int radix) {
            if (digits.length() == 0) {
                throw MathExpression.error;
            }
            int length = digits.length();
            long j = 0;
            for (int i = 0; i < length; i++) {
                int iDigit = Character.digit(digits.charAt(i), radix);
                if (iDigit < 0) {
                    throw MathExpression.error;
                }
                j = (j * ((long) radix)) + ((long) iDigit);
                if (j > 9007199254740992L) {
                    throw MathExpression.error;
                }
            }
            return j;
        }

        private final double parseArgument() {
            Operand expression = parseExpression(0);
            if (expression.getUnit() != null) {
                throw MathExpression.error;
            }
            return expression.getValue();
        }
    }
}

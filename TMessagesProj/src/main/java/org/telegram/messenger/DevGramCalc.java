package org.telegram.messenger;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Инлайн-калькулятор поля ввода (порт логики exteraGram: com.exteragram.messenger.math).
 * Пользователь набирает выражение и «=», клиент дописывает результат: 273+183=456,
 * 1039/19/78=0.701..., max(92;82;173)=173, log(1)=0.
 *
 * Чистая Java, без сторонних зависимостей. Recursive-descent (Pratt) парсер с приоритетами.
 * Точка входа: {@link #suggestionAt(CharSequence, int, char)} — вызывать когда перед кареткой '='.
 */
public final class DevGramCalc {

    private DevGramCalc() {}

    // ---------------------------------------------------------------- результат подсказки
    public static final class Suggestion {
        public final int insertAt;      // куда вставить (позиция каретки, сразу после '=')
        public final String insertText; // что вставить (может с ведущим пробелом)
        public final String value;      // чистое значение без ведущего пробела

        Suggestion(int insertAt, String insertText, String value) {
            this.insertAt = insertAt;
            this.insertText = insertText;
            this.value = value;
        }
    }

    // ---------------------------------------------------------------- операторы/функции
    private interface Infix { double apply(double a, double b, boolean rightPercent); }
    private interface Unary { double apply(double x); }
    private interface Func  { double apply(double[] args); }

    private static final class InfixOp {
        final String[] symbols; final int precedence; final boolean rightAssoc; final Infix apply;
        InfixOp(String[] s, int p, boolean r, Infix a) { symbols = s; precedence = p; rightAssoc = r; apply = a; }
    }
    private static final class PrefixOp {
        final String[] symbols; final int precedence; final boolean operation; final Unary apply;
        PrefixOp(String[] s, int p, boolean op, Unary a) { symbols = s; precedence = p; operation = op; apply = a; }
    }
    private static final class PostfixOp {
        final String[] symbols; final boolean percent; final Unary apply;
        PostfixOp(String[] s, boolean pc, Unary a) { symbols = s; percent = pc; apply = a; }
    }
    private static final class MathFunc {
        final String name; final int minArity, maxArity; final Func apply;
        MathFunc(String n, int min, int max, Func a) { name = n; minArity = min; maxArity = max; apply = a; }
    }

    private static final InfixOp[] INFIX = new InfixOp[] {
        new InfixOp(new String[]{"+"}, 10, false, (a, b, pc) -> pc ? a + b * a : a + b),
        new InfixOp(new String[]{"-", "−"}, 10, false, (a, b, pc) -> pc ? a - b * a : a - b),
        new InfixOp(new String[]{"*", "×"}, 20, false, (a, b, pc) -> a * b),
        new InfixOp(new String[]{"/", "÷"}, 20, false, (a, b, pc) -> a / b),
        new InfixOp(new String[]{"^"}, 40, true, (a, b, pc) -> Math.pow(a, b)),
    };
    private static final PrefixOp[] PREFIX = new PrefixOp[] {
        new PrefixOp(new String[]{"-", "−"}, 30, false, x -> -x),
        new PrefixOp(new String[]{"+"}, 30, false, x -> x),
        new PrefixOp(new String[]{"√"}, 40, true, Math::sqrt),
    };
    private static final PostfixOp[] POSTFIX = new PostfixOp[] {
        new PostfixOp(new String[]{"%"}, true, x -> x / 100.0),
        new PostfixOp(new String[]{"!"}, false, DevGramCalc::factorial),
    };
    private static final MathFunc[] FUNCTIONS = new MathFunc[] {
        new MathFunc("sqrt", 1, 1, a -> Math.sqrt(a[0])),
        new MathFunc("cbrt", 1, 1, a -> Math.cbrt(a[0])),
        new MathFunc("abs", 1, 1, a -> Math.abs(a[0])),
        new MathFunc("sign", 1, 1, a -> Math.signum(a[0])),
        new MathFunc("round", 1, 1, a -> (double) Math.round(a[0])),
        new MathFunc("floor", 1, 1, a -> Math.floor(a[0])),
        new MathFunc("ceil", 1, 1, a -> Math.ceil(a[0])),
        new MathFunc("fact", 1, 1, a -> factorial(a[0])),
        new MathFunc("min", 1, Integer.MAX_VALUE, DevGramCalc::minOf),
        new MathFunc("max", 1, Integer.MAX_VALUE, DevGramCalc::maxOf),
        new MathFunc("log", 1, 1, a -> Math.log10(a[0])),
        new MathFunc("log2", 1, 1, a -> Math.log(a[0]) / Math.log(2.0)),
        new MathFunc("ln", 1, 1, a -> Math.log(a[0])),
        new MathFunc("exp", 1, 1, a -> Math.exp(a[0])),
        new MathFunc("sin", 1, 1, a -> Math.sin(a[0])),
        new MathFunc("cos", 1, 1, a -> Math.cos(a[0])),
        new MathFunc("tan", 1, 1, a -> Math.tan(a[0])),
        new MathFunc("asin", 1, 1, a -> Math.asin(a[0])),
        new MathFunc("acos", 1, 1, a -> Math.acos(a[0])),
        new MathFunc("atan", 1, 1, a -> Math.atan(a[0])),
        new MathFunc("atan2", 2, 2, a -> Math.atan2(a[0], a[1])),
        new MathFunc("sinh", 1, 1, a -> Math.sinh(a[0])),
        new MathFunc("cosh", 1, 1, a -> Math.cosh(a[0])),
        new MathFunc("tanh", 1, 1, a -> Math.tanh(a[0])),
    };
    private static final Map<String, Double> CONSTANTS = new HashMap<>();
    private static final List<String> SYMBOLS = new ArrayList<>();   // отсортированы по длине убыв.
    private static final java.util.Set<Character> SYMBOL_CHARS = new java.util.HashSet<>();

    static {
        CONSTANTS.put("pi", Math.PI);
        CONSTANTS.put("π", Math.PI);
        CONSTANTS.put("e", Math.E);
        for (InfixOp o : INFIX)   for (String s : o.symbols) if (!SYMBOLS.contains(s)) SYMBOLS.add(s);
        for (PrefixOp o : PREFIX) for (String s : o.symbols) if (!SYMBOLS.contains(s)) SYMBOLS.add(s);
        for (PostfixOp o : POSTFIX) for (String s : o.symbols) if (!SYMBOLS.contains(s)) SYMBOLS.add(s);
        SYMBOLS.sort((x, y) -> Integer.compare(y.length(), x.length()));
        for (String s : SYMBOLS) for (int i = 0; i < s.length(); i++) SYMBOL_CHARS.add(s.charAt(i));
        for (String k : CONSTANTS.keySet())
            for (int i = 0; i < k.length(); i++)
                if (!Character.isLetter(k.charAt(i))) SYMBOL_CHARS.add(k.charAt(i));
        SYMBOL_CHARS.add('(');
        SYMBOL_CHARS.add(')');
        SYMBOL_CHARS.add(';');
    }

    private static double factorial(double v) {
        if (v < 0 || v != Math.floor(v) || v > 170) return Double.NaN;
        int n = (int) v; double r = 1.0;
        for (int i = 2; i <= n; i++) r *= i;
        return r;
    }
    private static double minOf(double[] a) { double m = a[0]; for (double x : a) m = Math.min(m, x); return m; }
    private static double maxOf(double[] a) { double m = a[0]; for (double x : a) m = Math.max(m, x); return m; }

    // ---------------------------------------------------------------- ошибка парсинга (управляющая)
    private static final class ParseError extends RuntimeException {
        static final ParseError I = new ParseError();
        private ParseError() { super(null, null, false, false); }
    }

    private static boolean isBlank(char c) { return c == ' ' || c == '\t' || c == 160; }

    // ---------------------------------------------------------------- токены
    private static final int T_NUM = 0, T_SYM = 1, T_LPAR = 2, T_RPAR = 3, T_IDENT = 4, T_SEMI = 5, T_EOF = 6;
    private static final class Token {
        final int type; final double number; final String text;
        Token(int t, double n, String s) { type = t; number = n; text = s; }
    }
    private static final class Operand {
        final double value; final boolean percent;
        Operand(double v, boolean p) { value = v; percent = p; }
    }
    private static final class Result {
        final double value; final boolean hasOperation; final char sep;
        Result(double v, boolean op, char s) { value = v; hasOperation = op; sep = s; }
    }

    // ---------------------------------------------------------------- парсер
    private static final class Parser {
        private final CharSequence src;
        private final char decimalSep;
        private final ArrayList<Token> tokens = new ArrayList<>();
        private int position, depth;
        private boolean hasOperation;
        private Character separator;

        Parser(CharSequence s, char sep) { src = s; decimalSep = sep; }

        Result parse() {
            tokenize();
            Operand r = parseExpression(0);
            expect(T_EOF);
            return new Result(r.value, hasOperation, separator != null ? separator : decimalSep);
        }

        private void tokenize() {
            int i = 0;
            while (i < src.length()) {
                char c = src.charAt(i);
                if (!isBlank(c)) {
                    if (Character.isDigit(c) || ((c == '.' || c == ',') && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1)))) {
                        i = readNumber(i);
                        continue;
                    } else if (c == '(') {
                        tokens.add(new Token(T_LPAR, 0, null));
                    } else if (c == ')') {
                        tokens.add(new Token(T_RPAR, 0, null));
                    } else if (c == ';') {
                        tokens.add(new Token(T_SEMI, 0, null));
                    } else if (Character.isLetter(c)) {
                        int j = i;
                        while (j < src.length() && (Character.isLetter(src.charAt(j)) || Character.isDigit(src.charAt(j)))) j++;
                        tokens.add(new Token(T_IDENT, 0, src.subSequence(i, j).toString().toLowerCase(Locale.ROOT)));
                        i = j;
                        continue;
                    } else {
                        String sym = matchSymbol(src, i);
                        if (sym == null) throw ParseError.I;
                        tokens.add(new Token(T_SYM, 0, sym));
                        i += sym.length();
                        continue;
                    }
                }
                i++;
            }
            tokens.add(new Token(T_EOF, 0, null));
        }

        private int readNumber(int from) {
            int i = from;
            while (i < src.length() && Character.isDigit(src.charAt(i))) i++;
            int intLen = i - from;
            char c = i < src.length() ? src.charAt(i) : ' ';
            int fracLen = 0;
            if ((c == '.' || c == ',') && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1))) {
                int fracStart = i + 1;
                i = fracStart;
                while (i < src.length() && Character.isDigit(src.charAt(i))) i++;
                fracLen = i - fracStart;
                // "1,234" c 3 знаками после запятой при 1..3 знаках до и ведущей не-нулём — это разделитель тысяч, не число
                if (c == ',' && fracLen == 3 && intLen >= 1 && intLen < 4 && src.charAt(from) != '0') throw ParseError.I;
                if (separator == null) separator = c;
            }
            if (intLen == 0 && fracLen == 0) throw ParseError.I;
            String num = src.subSequence(from, i).toString().replace(',', '.');
            try {
                tokens.add(new Token(T_NUM, Double.parseDouble(num), null));
            } catch (NumberFormatException e) {
                throw ParseError.I;
            }
            return i;
        }

        private Token peek() { return tokens.get(position); }
        private Token next() { return tokens.get(position++); }
        private Token expect(int type) {
            Token t = next();
            if (t.type != type) throw ParseError.I;
            return t;
        }

        private Operand parseExpression(int minPrecedence) {
            if (++depth > 32) throw ParseError.I;
            try {
                Operand left = parseUnary();
                while (true) {
                    Token t = peek();
                    if (t.type != T_SYM) break;
                    InfixOp op = infixFor(t.text);
                    if (op == null || op.precedence < minPrecedence) break;
                    next();
                    Operand right = parseExpression(op.rightAssoc ? op.precedence : op.precedence + 1);
                    hasOperation = true;
                    left = new Operand(op.apply.apply(left.value, right.value, right.percent), false);
                }
                return left;
            } finally {
                depth--;
            }
        }

        private Operand parseUnary() {
            Token t = peek();
            if (t.type == T_SYM) {
                PrefixOp op = prefixFor(t.text);
                if (op != null) {
                    next();
                    Operand r = parseExpression(op.precedence);
                    if (op.operation) hasOperation = true;
                    return new Operand(op.apply.apply(r.value), r.percent);
                }
            }
            return parsePostfix();
        }

        private Operand parsePostfix() {
            double v = parsePrimary();
            boolean percent = false;
            while (true) {
                Token t = peek();
                if (t.type != T_SYM) break;
                PostfixOp op = postfixFor(t.text);
                if (op == null) break;
                next();
                v = op.apply.apply(v);
                hasOperation = true;
                percent = op.percent;
            }
            return new Operand(v, percent);
        }

        private double parsePrimary() {
            Token t = next();
            switch (t.type) {
                case T_NUM: return t.number;
                case T_LPAR: {
                    Operand r = parseExpression(0);
                    expect(T_RPAR);
                    return r.value;
                }
                case T_IDENT: return parseIdentifier(t.text);
                default: throw ParseError.I;
            }
        }

        private double parseIdentifier(String name) {
            Double constant = CONSTANTS.get(name);
            if (constant != null) return constant;
            MathFunc fn = functionFor(name);
            if (fn == null) throw ParseError.I;
            expect(T_LPAR);
            ArrayList<Double> args = new ArrayList<>(2);
            if (peek().type != T_RPAR) {
                args.add(parseExpression(0).value);
                while (peek().type == T_SEMI) {
                    next();
                    args.add(parseExpression(0).value);
                }
            }
            expect(T_RPAR);
            if (args.size() < fn.minArity || args.size() > fn.maxArity) throw ParseError.I;
            hasOperation = true;
            double[] arr = new double[args.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = args.get(i);
            return fn.apply.apply(arr);
        }
    }

    private static String matchSymbol(CharSequence text, int at) {
        for (String s : SYMBOLS) {
            if (at + s.length() <= text.length()) {
                boolean match = true;
                for (int i = 0; i < s.length(); i++) {
                    if (text.charAt(at + i) != s.charAt(i)) { match = false; break; }
                }
                if (match) return s;
            }
        }
        return null;
    }
    private static InfixOp infixFor(String sym) {
        for (InfixOp o : INFIX) for (String s : o.symbols) if (s.equals(sym)) return o;
        return null;
    }
    private static PrefixOp prefixFor(String sym) {
        for (PrefixOp o : PREFIX) for (String s : o.symbols) if (s.equals(sym)) return o;
        return null;
    }
    private static PostfixOp postfixFor(String sym) {
        for (PostfixOp o : POSTFIX) for (String s : o.symbols) if (s.equals(sym)) return o;
        return null;
    }
    private static MathFunc functionFor(String name) {
        for (MathFunc f : FUNCTIONS) if (f.name.equals(name)) return f;
        return null;
    }

    private static Result evaluate(CharSequence expr, char sep) {
        try {
            return new Parser(expr, sep).parse();
        } catch (ParseError e) {
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- форматирование
    private static String format(double value, char sep) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return null;
        double abs = Math.abs(value);
        if (abs >= 1.0E12) return null;
        if (abs != 0.0 && abs < 1.0E-9) return null;
        BigDecimal rounded = BigDecimal.valueOf(value).round(new MathContext(12));
        BigDecimal stripped = rounded.signum() == 0 ? new BigDecimal(BigInteger.ZERO, 0) : rounded.stripTrailingZeros();
        String s = stripped.toPlainString();
        if (s.equals("-0")) s = "0";
        if (s.length() > 24) return null;
        return sep == '.' ? s : s.replace('.', sep);
    }

    // ---------------------------------------------------------------- главный вход
    /**
     * Если перед {@code caret} стоит '=', пытается вычислить выражение слева и вернуть подсказку с
     * результатом. Возвращает null, если выражения нет, результат — просто число/дата, или не считается.
     * @param decimalSep десятичный разделитель ('.' или ',') локали пользователя.
     */
    public static Suggestion suggestionAt(CharSequence text, int caret, char decimalSep) {
        if (text == null || caret <= 0 || caret > text.length()) return null;
        int eq = caret - 1;
        if (text.charAt(eq) != '=') return null;
        // если сразу после каретки уже стоит число — результат уже есть, не предлагаем
        int k = caret;
        while (k < text.length() && isBlank(text.charAt(k))) k++;
        if (k < text.length()) {
            char c = text.charAt(k);
            if (Character.isDigit(c) || c == '.' || c == ',') return null;
        }
        Integer start = expressionStart(text, eq);
        if (start == null) return null;
        int from = start;
        while (true) {
            if (!looksLikeDate(text, from, eq)) {
                StringBuilder sb = new StringBuilder(eq - from);
                sb.append(text, from, eq);
                Result r = evaluate(sb, decimalSep);
                if (r != null) {
                    if (r.hasOperation) {
                        String fmt = format(r.value, r.sep);
                        if (fmt != null) {
                            boolean leadingSpace = caret >= 2 && isBlank(text.charAt(caret - 2));
                            return new Suggestion(caret, leadingSpace ? " " + fmt : fmt, fmt);
                        }
                    }
                    return null;
                }
            }
            Integer nc = nextCandidate(text, from, eq);
            if (nc == null) break;
            from = nc;
        }
        return null;
    }

    private static Integer expressionStart(CharSequence text, int equalsIndex) {
        int max = Math.max(0, equalsIndex - 64);
        int i = equalsIndex - 1;
        while (i >= max && isExpressionChar(text.charAt(i))) i--;
        do { i++; } while (i < equalsIndex && isBlank(text.charAt(i)));
        if (i >= equalsIndex) return null;
        if (i > 0) {
            char c = text.charAt(i - 1);
            if (Character.isLetterOrDigit(c) || c == '_') return null;
        }
        return i;
    }

    private static boolean isExpressionChar(char c) {
        return Character.isDigit(c) || Character.isLetter(c) || c == '.' || c == ',' || isBlank(c) || SYMBOL_CHARS.contains(c);
    }

    // следующая точка старта выражения (после '(' или после блока пробелов) — на случай "текст (2+2="
    private static Integer nextCandidate(CharSequence text, int from, int to) {
        int i = from;
        while (i < to) {
            char c = text.charAt(i);
            i++;
            if (c == '(') return i;
            if (isBlank(c)) {
                while (i < to && isBlank(text.charAt(i))) i++;
                if (i < to) return i;
            }
        }
        return null;
    }

    // эвристика дат (dd/mm/yyyy, yyyy-mm-dd, dd.mm.yy) — чтобы «01/01/2024=» не считалось делением
    private static boolean looksLikeDate(CharSequence text, int from, int to) {
        while (from < to && isBlank(text.charAt(from))) from++;
        while (to > from && isBlank(text.charAt(to - 1))) to--;
        if (to - from < 6) return false;
        ArrayList<Integer> groups = new ArrayList<>(4);
        int digits = 0;
        char sep = ' ';
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits++;
            } else {
                if (c != '/' && c != '.' && c != '-') return false;
                if (sep == ' ') sep = c;
                else if (sep != c) return false;
                if (digits == 0 || groups.size() == 2) return false;
                groups.add(digits);
                digits = 0;
            }
        }
        if (digits != 0 && groups.size() == 2) {
            groups.add(digits);
            switch (sep) {
                case '-':
                    return groups.get(0) == 4 && groups.get(1) <= 2 && groups.get(2) <= 2;
                case '.':
                case '/':
                    return groups.get(0) <= 2 && groups.get(1) <= 2 && (groups.get(2) == 2 || groups.get(2) == 4);
                default:
                    return false;
            }
        }
        return false;
    }
}

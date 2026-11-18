package org.telegram.ui.Components.devgram.blocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/* Adapted from the user-supplied Custom Profile plugin by @RoflPlugins.
 * See docs/PROFILE_STUDIO_REFERENCE_AUDIT.md for the reference and restoration evidence. */
final class BlockScript {
    static final int MAX_LINES = 256;
    static final int MAX_NESTING = 8;
    static final int MAX_NODES = 96;
    static final int MAX_REPEAT = 16;
    static final int MAX_SOURCE = 16384;
    static final int MAX_STEPS = 2048;
    static final String SAMPLE = "// BlockScript 1\nlet left = 310\nrect x=0 y=0 w=1000 h=1000 color=$textColor alpha=12 radius=18\ngradient x=0 y=0 w=1000 h=1000 from=$serviceColor to=#0010141C radius=18\nimage source=$cover x=42 y=190 w=220 h=620 radius=14\ntext value=$label x=left y=390 size=12 color=$softColor maxw=620\ntext value=$value x=left y=590 size=17 weight=700 color=$textColor maxw=620\nif progress >= 0\n  progress value=progress x=left y=700 w=610 h=28 color=$serviceColor back=#33FFFFFF radius=3\nend\narrow x=940 y=500 size=22 color=$softColor stroke=2\n";
    final boolean animated;
    private final List<Line> lines;
    final String source;

    private static int clamp(int i, int i2, int i3) {
        return i < i2 ? i2 : i > i3 ? i3 : i;
    }

    private BlockScript(String str, List<Line> list) {
        this.source = str;
        this.animated = str.matches("(?s).*\\b(time|phase)\\b.*");
        this.lines = list;
    }

    static BlockScript compile(String str) throws Error {
        String strTrim = str == null ? "" : str.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (strTrim.length() == 0) {
            return null;
        }
        if (strTrim.length() > MAX_SOURCE) {
            throw new Error(0, "исходник длиннее 16384 знаков");
        }
        String[] strArrSplit = strTrim.split("\n", -1);
        if (strArrSplit.length > MAX_LINES) {
            throw new Error(0, "в программе больше 256 строк");
        }
        ArrayList arrayList = new ArrayList();
        int i = 0;
        int i2 = 0;
        while (i < strArrSplit.length) {
            String strStripComment = stripComment(strArrSplit[i]);
            i++;
            List<String> listWords = words(strStripComment, i);
            if (!listWords.isEmpty()) {
                String lowerCase = listWords.get(0).toLowerCase(Locale.US);
                if (!"end".equals(lowerCase) && !"else".equals(lowerCase)) {
                    int i3 = 61;
                    if ("let".equals(lowerCase)) {
                        String strJoin = join(listWords, 1);
                        int iIndexOf = strJoin.indexOf(61);
                        if (iIndexOf <= 0) {
                            throw new Error(i, "нужно: let имя = выражение");
                        }
                        String strTrim2 = strJoin.substring(0, iIndexOf).trim();
                        if (!strTrim2.matches("[A-Za-z_][A-Za-z0-9_]{0,31}")) {
                            throw new Error(i, "неверное имя переменной");
                        }
                        String strTrim3 = strJoin.substring(iIndexOf + 1).trim();
                        expression(strTrim3, i);
                        arrayList.add(new Line(i, lowerCase, strTrim2 + "=" + strTrim3, Collections.EMPTY_MAP));
                    } else if ("if".equals(lowerCase) || "repeat".equals(lowerCase)) {
                        String strJoin2 = join(listWords, 1);
                        expression(strJoin2, i);
                        i2++;
                        if (i2 > 8) {
                            throw new Error(i, "слишком глубокая вложенность");
                        }
                        arrayList.add(new Line(i, lowerCase, strJoin2, Collections.EMPTY_MAP));
                    } else {
                        if (!drawable(lowerCase)) {
                            throw new Error(i, "неизвестная команда «" + lowerCase + "»");
                        }
                        LinkedHashMap linkedHashMap = new LinkedHashMap();
                        int i4 = 1;
                        while (i4 < listWords.size()) {
                            String str2 = listWords.get(i4);
                            int iIndexOf2 = str2.indexOf(i3);
                            if (iIndexOf2 <= 0 || iIndexOf2 == str2.length() - 1) {
                                throw new Error(i, "параметр должен иметь вид имя=значение");
                            }
                            String lowerCase2 = str2.substring(0, iIndexOf2).toLowerCase(Locale.US);
                            String strSubstring = str2.substring(iIndexOf2 + 1);
                            if (!lowerCase2.matches("[a-z][a-z0-9_]{0,23}")) {
                                throw new Error(i, "неверное имя параметра");
                            }
                            linkedHashMap.put(lowerCase2, strSubstring);
                            i4++;
                            i3 = 61;
                        }
                        validateAnimation(linkedHashMap, i);
                        arrayList.add(new Line(i, lowerCase, "", Collections.unmodifiableMap(linkedHashMap)));
                    }
                } else {
                    if (i2 <= 0) {
                        throw new Error(i, "лишний " + lowerCase);
                    }
                    if ("end".equals(lowerCase)) {
                        i2--;
                    }
                    arrayList.add(new Line(i, lowerCase, "", Collections.EMPTY_MAP));
                }
            }
        }
        if (i2 != 0) {
            throw new Error(strArrSplit.length, "не хватает end");
        }
        return new BlockScript(strTrim, Collections.unmodifiableList(arrayList));
    }

    private static void validateAnimation(Map<String, String> map, int i) throws Error {
        String str = map.get("animationname");
        String str2 = map.get("animation");
        if (str == null && str2 != null) {
            String[] strArrSplit = str2.trim().toLowerCase(Locale.US).split("\\s+");
            str = strArrSplit.length == 0 ? "" : strArrSplit[0];
        }
        if (str != null && str.length() > 0 && !"none".equalsIgnoreCase(str) && !BlockAnimation.animationName(str.toLowerCase(Locale.US))) {
            throw new Error(i, "неизвестная анимация «" + str + "»");
        }
        String str3 = map.get("animationclock");
        if (str3 != null && !"local".equals(str3) && !"event".equals(str3) && !"track".equals(str3) && !"state".equals(str3)) {
            throw new Error(i, "animationclock: local, event, track или state");
        }
    }

    List<Node> run(Context context) {
        if (this.lines.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        HashMap map = new HashMap(context.numbers);
        ArrayList arrayList = new ArrayList();
        ArrayDeque arrayDeque = new ArrayDeque();
        int i = 0;
        int i2 = 0;
        boolean z = true;
        while (i2 < this.lines.size()) {
            int i3 = i + 1;
            if (i >= MAX_STEPS || arrayList.size() >= MAX_NODES) {
                break;
            }
            Line line = this.lines.get(i2);
            if ("if".equals(line.op)) {
                boolean z2 = z && value(line.body, map) != 0.0d;
                arrayDeque.push(new Frame("if", i2, z, z2, 0));
                z = z2;
            } else if ("else".equals(line.op)) {
                Frame frame = (Frame) arrayDeque.peek();
                if (frame != null && "if".equals(frame.kind)) {
                    z = frame.parentActive && !frame.firstActive;
                }
            } else if ("repeat".equals(line.op)) {
                int iClamp = z ? clamp((int) Math.round(value(line.body, map)), 0, 16) : 0;
                arrayDeque.push(new Frame("repeat", i2, z, z && iClamp > 0, iClamp));
                z = z && iClamp > 0;
                map.put("index", Double.valueOf(0.0d));
            } else if ("end".equals(line.op)) {
                Frame frame2 = (Frame) arrayDeque.peek();
                if (frame2 != null && "repeat".equals(frame2.kind) && frame2.left > 1) {
                    frame2.left--;
                    map.put("index", Double.valueOf(frame2.total - frame2.left));
                    i2 = frame2.start;
                } else if (frame2 != null) {
                    arrayDeque.pop();
                    z = frame2.parentActive;
                }
            } else if (z && "let".equals(line.op)) {
                int iIndexOf = line.body.indexOf(61);
                map.put(line.body.substring(0, iIndexOf), Double.valueOf(value(line.body.substring(iIndexOf + 1), map)));
            } else if (z) {
                arrayList.add(new Node(line.line, line.op, line.attrs, Collections.unmodifiableMap(new HashMap(map))));
            }
            i2++;
            i = i3;
        }
        return arrayList;
    }

    static double number(Node node, String str, double d) {
        String str2 = node.attrs.get(str);
        if (str2 != null && str2.length() != 0) {
            try {
                return value(str2, node.vars);
            } catch (Throwable unused) {
            }
        }
        return d;
    }

    static String text(Node node, String str, String str2, Context context) {
        String strReplace = node.attrs.get(str);
        if (strReplace != null) {
            if (strReplace.startsWith("$") && strReplace.indexOf(123) < 0) {
                String str3 = context.strings.get(strReplace.substring(1));
                if (str3 != null) {
                    return str3;
                }
            } else {
                for (Map.Entry<String, String> entry : context.strings.entrySet()) {
                    strReplace = strReplace.replace("${" + entry.getKey() + "}", entry.getValue());
                }
                return strReplace;
            }
        }
        return str2;
    }

    static int color(Node node, String str, int i, Context context) {
        String str2 = node.attrs.get(str);
        if (str2 != null) {
            if (str2.startsWith("$")) {
                Integer num = context.colors.get(str2.substring(1));
                if (num != null) {
                    return num.intValue();
                }
            } else {
                return BlockColor.color(str2, i);
            }
        }
        return i;
    }

    private static boolean drawable(String str) {
        return "rect".equals(str) || "gradient".equals(str) || "text".equals(str) || "pill".equals(str) || "image".equals(str) || "progress".equals(str) || "arrow".equals(str);
    }

    private static void expression(String str, int i) throws Error {
        try {
            new Expr(str, Collections.EMPTY_MAP).parse();
        } catch (RuntimeException e) {
            throw new Error(i, "неверное выражение: " + e.getMessage());
        }
    }

    private static double value(String str, Map<String, Double> map) {
        try {
            return new Expr(str, map).parse();
        } catch (Throwable unused) {
            return 0.0d;
        }
    }

    private static List<String> words(String str, int i) throws Error {
        int i2;
        ArrayList arrayList = new ArrayList();
        StringBuilder sb = new StringBuilder();
        int i3 = 0;
        char c = 0;
        while (i3 < str.length()) {
            char cCharAt = str.charAt(i3);
            if (c != 0) {
                if (cCharAt == '\\' && (i2 = i3 + 1) < str.length()) {
                    char cCharAt2 = str.charAt(i2);
                    if (cCharAt2 == 'n') {
                        cCharAt2 = '\n';
                    }
                    sb.append(cCharAt2);
                    i3 = i2;
                } else if (cCharAt == c) {
                    c = 0;
                } else {
                    sb.append(cCharAt);
                }
            } else if (cCharAt == '\'' || cCharAt == '\"') {
                c = cCharAt;
            } else if (Character.isWhitespace(cCharAt)) {
                if (sb.length() > 0) {
                    arrayList.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(cCharAt);
            }
            i3++;
        }
        if (c != 0) {
            throw new Error(i, "строка не закрыта кавычкой");
        }
        if (sb.length() > 0) {
            arrayList.add(sb.toString());
        }
        return arrayList;
    }

    private static String stripComment(String str) {
        int i = 0;
        char c = 0;
        while (true) {
            int i2 = i + 1;
            if (i2 >= str.length()) {
                return str;
            }
            char cCharAt = str.charAt(i);
            if (c != 0) {
                if (cCharAt == '\\') {
                    i = i2;
                } else if (cCharAt == c) {
                    c = 0;
                }
            } else if (cCharAt == '\'' || cCharAt == '\"') {
                c = cCharAt;
            } else if (cCharAt == '/' && str.charAt(i2) == '/') {
                return str.substring(0, i);
            }
            i++;
        }
    }

    private static String join(List<String> list, int i) {
        StringBuilder sb = new StringBuilder();
        while (i < list.size()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(list.get(i));
            i++;
        }
        return sb.toString();
    }

    static final class Context {
        final Map<String, Double> numbers = new HashMap();
        final Map<String, String> strings = new HashMap();
        final Map<String, Integer> colors = new HashMap();

        Context() {
        }

        Context number(String str, double d) {
            this.numbers.put(str, Double.valueOf(d));
            return this;
        }

        Context text(String str, String str2) {
            Map<String, String> map = this.strings;
            if (str2 == null) {
                str2 = "";
            }
            map.put(str, str2);
            return this;
        }

        Context color(String str, int i) {
            this.colors.put(str, Integer.valueOf(i));
            return this;
        }
    }

    static final class Node {
        final Map<String, String> attrs;
        final String kind;
        final int line;
        final Map<String, Double> vars;

        Node(int i, String str, Map<String, String> map, Map<String, Double> map2) {
            this.line = i;
            this.kind = str;
            this.attrs = map;
            this.vars = map2;
        }
    }

    static final class Error extends Exception {
        final int line;

        Error(int i,String str){super((i>0?"Line "+i+": ":"")+str);this.line=i;}
    }

    private static final class Line {
        final Map<String, String> attrs;
        final String body;
        final int line;
        final String op;

        Line(int i, String str, String str2, Map<String, String> map) {
            this.line = i;
            this.op = str;
            this.body = str2;
            this.attrs = map;
        }
    }

    private static final class Frame {
        final boolean firstActive;
        final String kind;
        int left;
        final boolean parentActive;
        final int start;
        final int total;

        Frame(String str, int i, boolean z, boolean z2, int i2) {
            this.kind = str;
            this.start = i;
            this.parentActive = z;
            this.firstActive = z2;
            this.total = i2;
            this.left = i2;
        }
    }

    private static final class Expr {
        int at,depth;
        final String source;
        final Map<String, Double> vars;

        Expr(String str, Map<String, Double> map) {
            this.source = str == null ? "" : str;
            this.vars = map;
        }

        double parse() {
            double dCompare = compare();
            space();
            if (this.at != this.source.length()) {
                throw new IllegalArgumentException("лишний символ «" + this.source.charAt(this.at) + "»");
            }
            return finite(dCompare);
        }

        /* JADX WARN: Removed duplicated region for block: B:11:0x0033  */
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        double compare(){
            double left=add();
            while(true){space();String operator=take("<=",">=","==","!=","<",">");if(operator==null)return left;double right=add();
                switch(operator){case "<=":left=left<=right?1:0;break;case ">=":left=left>=right?1:0;break;case "==":left=left==right?1:0;break;case "!=":left=left!=right?1:0;break;case "<":left=left<right?1:0;break;default:left=left>right?1:0;}
            }
        }

        double add() {
            double dMultiply = multiply();
            while (true) {
                space();
                if (eat('+')) {
                    dMultiply += multiply();
                } else {
                    if (!eat('-')) {
                        return finite(dMultiply);
                    }
                    dMultiply -= multiply();
                }
            }
        }

        double multiply() {
            double dUnary = unary();
            while (true) {
                space();
                if (eat('*')) {
                    dUnary *= unary();
                } else if (eat('/')) {
                    double dUnary2 = unary();
                    dUnary = dUnary2 == 0.0d ? 0.0d : dUnary / dUnary2;
                } else {
                    if (!eat('%')) {
                        return finite(dUnary);
                    }
                    double dUnary3 = unary();
                    if (dUnary3 != 0.0d) {
                        dUnary %= dUnary3;
                    }
                }
            }
        }

        double unary(){if(++depth>64){depth--;throw new IllegalArgumentException("expression nesting exceeds 64");}try{return atom();}finally{depth--;}}
        double atom() {
            space();
            if (eat('-')) {
                return -unary();
            }
            if (eat('+')) {
                return unary();
            }
            if (eat('!')) {
                return unary() == 0.0d ? 1.0d : 0.0d;
            }
            if (eat('(')) {
                double dCompare = compare();
                space();
                if (eat(')')) {
                    return dCompare;
                }
                throw new IllegalArgumentException("не хватает )");
            }
            if (this.at < this.source.length() && (Character.isDigit(this.source.charAt(this.at)) || this.source.charAt(this.at) == '.')) {
                int i = this.at;
                this.at = i + 1;
                while (this.at < this.source.length() && (Character.isDigit(this.source.charAt(this.at)) || this.source.charAt(this.at) == '.')) {
                    this.at++;
                }
                return Double.parseDouble(this.source.substring(i, this.at));
            }
            int i2 = this.at;
            while (this.at < this.source.length() && (Character.isLetterOrDigit(this.source.charAt(this.at)) || this.source.charAt(this.at) == '_')) {
                this.at++;
            }
            int i3 = this.at;
            if (i2 == i3) {
                throw new IllegalArgumentException("ожидалось число");
            }
            String strSubstring = this.source.substring(i2, i3);
            space();
            if(eat('(')){
                ArrayList<Double> arguments=new ArrayList<>();space();
                if(!eat(')')){while(true){arguments.add(compare());if(arguments.size()>16)throw new IllegalArgumentException("too many function arguments");space();if(eat(')'))break;if(!eat(','))throw new IllegalArgumentException("missing comma in "+strSubstring+"()");}}
                return function(strSubstring,arguments);
            }
            Double d = this.vars.get(strSubstring);
            if (d == null) {
                return 0.0d;
            }
            return d.doubleValue();
        }

        double function(String str, List<Double> list) {
            if ("sin".equals(str) && list.size() == 1) {
                return Math.sin(list.get(0).doubleValue());
            }
            if ("cos".equals(str) && list.size() == 1) {
                return Math.cos(list.get(0).doubleValue());
            }
            if ("abs".equals(str) && list.size() == 1) {
                return Math.abs(list.get(0).doubleValue());
            }
            if ("sqrt".equals(str) && list.size() == 1) {
                return Math.sqrt(Math.max(0.0d, list.get(0).doubleValue()));
            }
            if ("floor".equals(str) && list.size() == 1) {
                return Math.floor(list.get(0).doubleValue());
            }
            if ("ceil".equals(str) && list.size() == 1) {
                return Math.ceil(list.get(0).doubleValue());
            }
            if ("round".equals(str) && list.size() == 1) {
                return Math.round(list.get(0).doubleValue());
            }
            if ("min".equals(str) && list.size() == 2) {
                return Math.min(list.get(0).doubleValue(), list.get(1).doubleValue());
            }
            if ("max".equals(str) && list.size() == 2) {
                return Math.max(list.get(0).doubleValue(), list.get(1).doubleValue());
            }
            if ("pow".equals(str) && list.size() == 2) {
                return Math.pow(list.get(0).doubleValue(), list.get(1).doubleValue());
            }
            if ("clamp".equals(str) && list.size() == 3) {
                return Math.max(list.get(1).doubleValue(), Math.min(list.get(2).doubleValue(), list.get(0).doubleValue()));
            }
            throw new IllegalArgumentException("неизвестная функция «" + str + "»");
        }

        void space() {
            while (this.at < this.source.length() && Character.isWhitespace(this.source.charAt(this.at))) {
                this.at++;
            }
        }

        boolean eat(char c) {
            if (this.at >= this.source.length() || this.source.charAt(this.at) != c) {
                return false;
            }
            this.at++;
            return true;
        }

        String take(String... strArr) {
            for (String str : strArr) {
                if (this.source.startsWith(str, this.at)) {
                    this.at += str.length();
                    return str;
                }
            }
            return null;
        }

        static double finite(double d) {
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return 0.0d;
            }
            return d;
        }
    }
}

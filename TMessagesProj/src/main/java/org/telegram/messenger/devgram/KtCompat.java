package org.telegram.messenger.devgram;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Строковые помощники с поведением как у стандартной библиотеки Kotlin (вызовы с аргументами по умолчанию). */
public final class KtCompat {

    private KtCompat() {
    }

    public static int indexOf(CharSequence s, char c, int from, boolean ignoreCase, int mask, Object unused) {
        for (int i = Math.max(0, from); i < s.length(); i++) {
            if (eq(s.charAt(i), c, ignoreCase)) {
                return i;
            }
        }
        return -1;
    }

    public static int lastIndexOf(CharSequence s, char c, int from, boolean ignoreCase, int mask, Object unused) {
        if ((mask & 2) != 0) {
            from = s.length() - 1;
        }
        for (int i = Math.min(from, s.length() - 1); i >= 0; i--) {
            if (eq(s.charAt(i), c, ignoreCase)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean startsWith(CharSequence s, char c, boolean ignoreCase, int mask, Object unused) {
        return s.length() > 0 && eq(s.charAt(0), c, ignoreCase);
    }

    public static String replace(String s, char from, char to, boolean ignoreCase, int mask, Object unused) {
        if (!ignoreCase) {
            return s.replace(from, to);
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            sb.append(eq(ch, from, true) ? to : ch);
        }
        return sb.toString();
    }

    public static List<String> split(CharSequence s, char[] delimiters, boolean ignoreCase, int limit, int mask, Object unused) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean d = false;
            for (char dl : delimiters) {
                if (eq(ch, dl, ignoreCase)) {
                    d = true;
                    break;
                }
            }
            if (d && (limit <= 0 || out.size() < limit - 1)) {
                out.add(s.subSequence(start, i).toString());
                start = i + 1;
            }
        }
        out.add(s.subSequence(start, s.length()).toString());
        return out;
    }

    public static boolean regionMatches(CharSequence s, int offset, CharSequence other, int otherOffset, int length, boolean ignoreCase, int mask, Object unused) {
        if (offset < 0 || otherOffset < 0 || offset + length > s.length() || otherOffset + length > other.length()) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (!eq(s.charAt(offset + i), other.charAt(otherOffset + i), ignoreCase)) {
                return false;
            }
        }
        return true;
    }

    public static String joinToString(Iterable<?> items, CharSequence separator, CharSequence prefix, CharSequence postfix, int limit, CharSequence truncated, Object transform, int mask, Object unused) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null) {
            sb.append(prefix);
        }
        boolean first = true;
        for (Object o : items) {
            if (!first) {
                sb.append(separator == null ? ", " : separator);
            }
            sb.append(o);
            first = false;
        }
        if (postfix != null) {
            sb.append(postfix);
        }
        return sb.toString();
    }

    /** BigDecimal.stripTrailingZeros, у нуля — без экспоненты. */
    public static BigDecimal stripZeros(BigDecimal value) {
        return value.signum() == 0 ? new BigDecimal(BigInteger.ZERO, 0) : value.stripTrailingZeros();
    }

    private static boolean eq(char a, char b, boolean ignoreCase) {
        if (a == b) {
            return true;
        }
        return ignoreCase && (Character.toUpperCase(a) == Character.toUpperCase(b) || Character.toLowerCase(a) == Character.toLowerCase(b));
    }
}

package org.telegram.messenger.devgram.inline.math;

import org.telegram.messenger.devgram.KtCompat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import kotlin.text.Typography;
public final class MathFormat {
    public static final MathFormat INSTANCE = new MathFormat();

    private MathFormat() {
    }

    public final String format(double value, char decimalSeparator, boolean grouping) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        double dAbs = Math.abs(value);
        if (dAbs >= 1.0E12d) {
            return null;
        }
        if (dAbs != 0.0d && dAbs < 1.0E-9d) {
            return null;
        }
        String plainString = KtCompat.stripZeros(BigDecimal.valueOf(value).round(new MathContext(12))).toPlainString();
        if (grouping) {
            plainString = group(plainString);
        }
        return finish(plainString, decimalSeparator);
    }

    public final String formatMoney(double value, int fractionDigits, char decimalSeparator, boolean grouping) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        double dAbs = Math.abs(value);
        if (dAbs >= 1.0E12d) {
            return null;
        }
        BigDecimal bigDecimalValueOf = BigDecimal.valueOf(value);
        if (fractionDigits >= 0) {
            BigDecimal scale = bigDecimalValueOf.setScale(fractionDigits, RoundingMode.HALF_UP);
            if (scale.signum() != 0 || dAbs == 0.0d) {
                if (KtCompat.stripZeros(scale).scale() <= 0) {
                    scale = scale.setScale(0);
                }
                String plainString = scale.toPlainString();
                if (grouping) {
                    plainString = group(plainString);
                }
                return finish(plainString, decimalSeparator);
            }
        }
        if (dAbs != 0.0d && dAbs < 1.0E-9d) {
            return null;
        }
        String plainString2 = KtCompat.stripZeros(bigDecimalValueOf.round(new MathContext(Math.max(6, bigDecimalValueOf.precision() - bigDecimalValueOf.scale())))).toPlainString();
        if (grouping) {
            plainString2 = group(plainString2);
        }
        return finish(plainString2, decimalSeparator);
    }

    public final String formatMeasure(double value, char decimalSeparator, boolean grouping) {
        BigDecimal bigDecimalRound;
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        double dAbs = Math.abs(value);
        if (dAbs >= 1.0E12d) {
            return null;
        }
        if (dAbs != 0.0d && dAbs < 1.0E-9d) {
            return null;
        }
        BigDecimal bigDecimalValueOf = BigDecimal.valueOf(value);
        if (dAbs >= 1.0d) {
            bigDecimalRound = bigDecimalValueOf.setScale(2, RoundingMode.HALF_UP);
        } else {
            bigDecimalRound = bigDecimalValueOf.round(new MathContext(3));
        }
        String plainString = KtCompat.stripZeros(bigDecimalRound).toPlainString();
        if (grouping) {
            plainString = group(plainString);
        }
        return finish(plainString, decimalSeparator);
    }

    public final String formatBase(double value, int radix) {
        String str;
        if (Double.isNaN(value) || Double.isInfinite(value) || value != Math.floor(value) || Math.abs(value) > 9.007199254740992E15d) {
            return null;
        }
        long j = (long) value;
        String str2 = "";
        if (radix == 2) {
            str = "0b";
        } else if (radix == 8) {
            str = "0o";
        } else if (radix != 16) {
            str = "";
        } else {
            str = "0x";
        }
        String upperCase = Long.toString(Math.abs(j), radix).toUpperCase(Locale.ROOT);
        if (j < 0) {
            str2 = "-";
        }
        return str2 + str + upperCase;
    }

    /* JADX WARN: Type inference failed for: r9v2, types: [boolean] */
    private final String group(String plain) {
        int neg = plain.startsWith("-") ? 1 : 0;
        int iIndexOf$default = KtCompat.indexOf((CharSequence) plain, '.', 0, false, 6, (Object) null);
        if (iIndexOf$default < 0) {
            iIndexOf$default = plain.length();
        }
        int i = iIndexOf$default - neg;
        if (i <= 3) {
            return plain;
        }
        StringBuilder sb = new StringBuilder(plain.length() + ((i - 1) / 3));
        sb.append((CharSequence) plain, 0, neg);
        for (int i2 = neg; i2 < iIndexOf$default; i2++) {
            if (i2 > neg && (iIndexOf$default - i2) % 3 == 0) {
                sb.append(Typography.nbsp);
            }
            sb.append(plain.charAt(i2));
        }
        sb.append((CharSequence) plain, iIndexOf$default, plain.length());
        return sb.toString();
    }

    private final String finish(String plain, char decimalSeparator) {
        if (Intrinsics.areEqual(plain, "-0")) {
            plain = "0";
        }
        String str = plain;
        if (str.length() > 24) {
            return null;
        }
        return decimalSeparator == '.' ? str : KtCompat.replace(str, '.', decimalSeparator, false, 4, (Object) null);
    }
}

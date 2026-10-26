package org.telegram.messenger.devgram.inline.math;

import org.telegram.messenger.devgram.inline.rates.CurrencyRates;
import org.telegram.messenger.devgram.inline.time.TimeZoneOptions;
public final class MathOptions {
    private final boolean arithmetic;
    private final char decimalSeparator;
    private final boolean degrees;
    private final boolean grouping;
    private final boolean imperial;
    private final String localCurrency;
    private final CurrencyRates rates;
    private final TimeZoneOptions timeZones;
    private final boolean units;

    public MathOptions(char c2, CurrencyRates currencyRates, String str, boolean z, boolean z2, boolean z3, boolean z4, TimeZoneOptions timeZoneOptions, boolean z5) {
        this.decimalSeparator = c2;
        this.rates = currencyRates;
        this.localCurrency = str;
        this.arithmetic = z;
        this.units = z2;
        this.degrees = z3;
        this.grouping = z4;
        this.timeZones = timeZoneOptions;
        this.imperial = z5;
    }

    public final char getDecimalSeparator() {
        return this.decimalSeparator;
    }

    public final CurrencyRates getRates() {
        return this.rates;
    }

    public final String getLocalCurrency() {
        return this.localCurrency;
    }

    public final boolean getArithmetic() {
        return this.arithmetic;
    }

    public final boolean getUnits() {
        return this.units;
    }

    public final boolean getDegrees() {
        return this.degrees;
    }

    public final boolean getGrouping() {
        return this.grouping;
    }

    public final TimeZoneOptions getTimeZones() {
        return this.timeZones;
    }

    public final boolean getImperial() {
        return this.imperial;
    }
}

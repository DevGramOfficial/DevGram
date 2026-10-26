package org.telegram.messenger.devgram.inline.math;

public final class MathResult {
    private final int base;
    private final boolean breakdown;
    private final char decimalSeparator;
    private final boolean grouping;
    private final boolean hasOperation;
    private final boolean russian;
    private final MathUnit unit;
    private final double value;

    public MathResult(double d, boolean z, char c2, MathUnit mathUnit, boolean z2, boolean z3, boolean z4, int i) {
        this.value = d;
        this.hasOperation = z;
        this.decimalSeparator = c2;
        this.unit = mathUnit;
        this.russian = z2;
        this.breakdown = z3;
        this.grouping = z4;
        this.base = i;
    }

    public final double getValue() {
        return this.value;
    }

    public final boolean getHasOperation() {
        return this.hasOperation;
    }

    public final char getDecimalSeparator() {
        return this.decimalSeparator;
    }

    public final MathUnit getUnit() {
        return this.unit;
    }

    public final boolean getRussian() {
        return this.russian;
    }

    public final boolean getBreakdown() {
        return this.breakdown;
    }

    public final boolean getGrouping() {
        return this.grouping;
    }

    public final int getBase() {
        return this.base;
    }
}

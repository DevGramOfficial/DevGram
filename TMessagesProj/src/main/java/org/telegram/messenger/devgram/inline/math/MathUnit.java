package org.telegram.messenger.devgram.inline.math;

import kotlin.jvm.internal.DefaultConstructorMarker;
public final class MathUnit {
    private final String code;
    private final UnitKind kind;
    private final String label;
    private final double offset;
    private final String russianLabel;
    private final double scale;

    public MathUnit(String str, String str2, UnitKind unitKind, double d, double d2, String str3) {
        this.code = str;
        this.label = str2;
        this.kind = unitKind;
        this.scale = d;
        this.offset = d2;
        this.russianLabel = str3;
    }

    public final String getCode() {
        return this.code;
    }

    public /* synthetic */ MathUnit(String str, String str2, UnitKind unitKind, double d, double d2, String str3, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(str, str2, (i & 4) != 0 ? UnitKind.CURRENCY : unitKind, (i & 8) != 0 ? 1.0d : d, (i & 16) != 0 ? 0.0d : d2, (i & 32) != 0 ? str2 : str3);
    }

    public final UnitKind getKind() {
        return this.kind;
    }

    public final double getScale() {
        return this.scale;
    }

    public final String label(boolean russian) {
        return russian ? this.russianLabel : this.label;
    }

    public final double toBase(double value) {
        return (value * this.scale) + this.offset;
    }

    public final double fromBase(double value) {
        return (value - this.offset) / this.scale;
    }
}

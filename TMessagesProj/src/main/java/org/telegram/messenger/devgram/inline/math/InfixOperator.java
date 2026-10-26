package org.telegram.messenger.devgram.inline.math;

import java.util.List;
import kotlin.jvm.functions.Function3;
import kotlin.jvm.internal.DefaultConstructorMarker;
public final class InfixOperator {
    private final Function3<Double, Double, Boolean, Double> apply;
    private final int precedence;
    private final boolean rightAssociative;
    private final List<String> symbols;
    private final UnitRule units;

    /* JADX WARN: Multi-variable type inference failed */
    public InfixOperator(List<String> list, int i, UnitRule unitRule, boolean z, Function3<Double, Double, Boolean, Double> function3) {
        this.symbols = list;
        this.precedence = i;
        this.units = unitRule;
        this.rightAssociative = z;
        this.apply = function3;
    }

    public /* synthetic */ InfixOperator(List list, int i, UnitRule unitRule, boolean z, Function3 function3, int i2, DefaultConstructorMarker defaultConstructorMarker) {
        this(list, i, unitRule, (i2 & 8) != 0 ? false : z, function3);
    }

    public final List<String> getSymbols() {
        return this.symbols;
    }

    public final int getPrecedence() {
        return this.precedence;
    }

    public final UnitRule getUnits() {
        return this.units;
    }

    public final boolean getRightAssociative() {
        return this.rightAssociative;
    }

    public final Function3<Double, Double, Boolean, Double> getApply() {
        return this.apply;
    }
}

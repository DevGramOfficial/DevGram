package org.telegram.messenger.devgram.inline.math;

import java.util.List;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.DefaultConstructorMarker;
public final class PrefixOperator {
    private final Function1<Double, Double> apply;
    private final boolean operation;
    private final int precedence;
    private final List<String> symbols;

    /* JADX WARN: Multi-variable type inference failed */
    public PrefixOperator(List<String> list, int i, boolean z, Function1<Double, Double> function1) {
        this.symbols = list;
        this.precedence = i;
        this.operation = z;
        this.apply = function1;
    }

    public /* synthetic */ PrefixOperator(List list, int i, boolean z, Function1 function1, int i2, DefaultConstructorMarker defaultConstructorMarker) {
        this(list, i, (i2 & 4) != 0 ? false : z, function1);
    }

    public final List<String> getSymbols() {
        return this.symbols;
    }

    public final int getPrecedence() {
        return this.precedence;
    }

    public final boolean getOperation() {
        return this.operation;
    }

    public final Function1<Double, Double> getApply() {
        return this.apply;
    }
}

package org.telegram.messenger.devgram.inline.math;

import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.ranges.IntRange;
public final class MathFunction {
    private final AngleUse angle;
    private final Function1<double[], Double> apply;
    private final IntRange arity;
    private final String name;

    /* JADX WARN: Multi-variable type inference failed */
    public MathFunction(String str, IntRange intRange, AngleUse angleUse, Function1<double[], Double> function1) {
        this.name = str;
        this.arity = intRange;
        this.angle = angleUse;
        this.apply = function1;
    }

    public final String getName() {
        return this.name;
    }

    public final IntRange getArity() {
        return this.arity;
    }

    public /* synthetic */ MathFunction(String str, IntRange intRange, AngleUse angleUse, Function1 function1, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(str, intRange, (i & 4) != 0 ? AngleUse.NONE : angleUse, function1);
    }

    public final AngleUse getAngle() {
        return this.angle;
    }

    public final Function1<double[], Double> getApply() {
        return this.apply;
    }
}

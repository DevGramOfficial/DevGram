package org.telegram.ui.Components.devgram;

import kotlin.jvm.internal.Intrinsics;
public final /* data */ class InlineDocExample {
    private final String input;
    private final String result;

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InlineDocExample)) {
            return false;
        }
        InlineDocExample inlineDocExample = (InlineDocExample) other;
        return Intrinsics.areEqual(this.input, inlineDocExample.input) && Intrinsics.areEqual(this.result, inlineDocExample.result);
    }

    public int hashCode() {
        int iHashCode = this.input.hashCode() * 31;
        String str = this.result;
        return iHashCode + (str == null ? 0 : str.hashCode());
    }

    public String toString() {
        return "InlineDocExample(input=" + this.input + ", result=" + this.result + ")";
    }

    public InlineDocExample(String str, String str2) {
        this.input = str;
        this.result = str2;
    }

    public final String getInput() {
        return this.input;
    }

    public final String getResult() {
        return this.result;
    }
}

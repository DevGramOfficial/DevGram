package org.telegram.messenger.devgram.inline.math;

public final class MathSuggestion {
    private final int insertAt;
    private final String insertText;
    private final String value;

    public MathSuggestion(int i, String str, String str2) {
        this.insertAt = i;
        this.insertText = str;
        this.value = str2;
    }

    public final int getInsertAt() {
        return this.insertAt;
    }

    public final String getInsertText() {
        return this.insertText;
    }

    public final String getValue() {
        return this.value;
    }
}

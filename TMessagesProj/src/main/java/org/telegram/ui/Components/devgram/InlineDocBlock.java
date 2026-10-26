package org.telegram.ui.Components.devgram;

import java.util.List;
import kotlin.jvm.internal.Intrinsics;
public interface InlineDocBlock {
    public static final /* data */ class Text implements InlineDocBlock {
        private final CharSequence text;

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return (other instanceof Text) && Intrinsics.areEqual(this.text, ((Text) other).text);
        }

        public int hashCode() {
            return this.text.hashCode();
        }

        public String toString() {
            return "Text(text=" + ((Object) this.text) + ")";
        }

        public Text(CharSequence charSequence) {
            this.text = charSequence;
        }

        public final CharSequence getText() {
            return this.text;
        }
    }
    public static final /* data */ class Codes implements InlineDocBlock {
        private final List<String> codes;

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return (other instanceof Codes) && Intrinsics.areEqual(this.codes, ((Codes) other).codes);
        }

        public int hashCode() {
            return this.codes.hashCode();
        }

        public String toString() {
            return "Codes(codes=" + this.codes + ")";
        }

        public Codes(List<String> list) {
            this.codes = list;
        }

        public final List<String> getCodes() {
            return this.codes;
        }
    }
    public static final /* data */ class Examples implements InlineDocBlock {
        private final List<InlineDocExample> examples;

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return (other instanceof Examples) && Intrinsics.areEqual(this.examples, ((Examples) other).examples);
        }

        public int hashCode() {
            return this.examples.hashCode();
        }

        public String toString() {
            return "Examples(examples=" + this.examples + ")";
        }

        public Examples(List<InlineDocExample> list) {
            this.examples = list;
        }

        public final List<InlineDocExample> getExamples() {
            return this.examples;
        }
    }
}

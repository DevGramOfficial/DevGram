package org.telegram.messenger.devgram.inline.docs;

import java.util.List;
import kotlin.jvm.internal.DefaultConstructorMarker;
public final class InlineExampleGroup {
    private final List<String> english;
    private final List<String> russian;

    public InlineExampleGroup(List<String> list, List<String> list2) {
        this.english = list;
        this.russian = list2;
    }

    public /* synthetic */ InlineExampleGroup(List list, List list2, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(list, (i & 2) != 0 ? list : list2);
    }

    public final List<String> examples(boolean russian) {
        return russian ? this.russian : this.english;
    }
}

package org.telegram.ui.Components.devgram;

import java.util.List;
import kotlin.jvm.internal.Intrinsics;
public final /* data */ class InlineFeature {
    private final CharSequence badge;
    private final List<InlineDocBlock> docs;
    private final int icon;
    private final CharSequence subtitle;
    private final CharSequence title;

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InlineFeature)) {
            return false;
        }
        InlineFeature inlineFeature = (InlineFeature) other;
        return this.icon == inlineFeature.icon && Intrinsics.areEqual(this.title, inlineFeature.title) && Intrinsics.areEqual(this.subtitle, inlineFeature.subtitle) && Intrinsics.areEqual(this.badge, inlineFeature.badge) && Intrinsics.areEqual(this.docs, inlineFeature.docs);
    }

    public int hashCode() {
        int iHashCode = ((((Integer.hashCode(this.icon) * 31) + this.title.hashCode()) * 31) + this.subtitle.hashCode()) * 31;
        CharSequence charSequence = this.badge;
        int iHashCode2 = (iHashCode + (charSequence == null ? 0 : charSequence.hashCode())) * 31;
        List<InlineDocBlock> list = this.docs;
        return iHashCode2 + (list != null ? list.hashCode() : 0);
    }

    public String toString() {
        int i = this.icon;
        CharSequence charSequence = this.title;
        CharSequence charSequence2 = this.subtitle;
        CharSequence charSequence3 = this.badge;
        return "InlineFeature(icon=" + i + ", title=" + ((Object) charSequence) + ", subtitle=" + ((Object) charSequence2) + ", badge=" + ((Object) charSequence3) + ", docs=" + this.docs + ")";
    }

    /* JADX WARN: Multi-variable type inference failed */
    public InlineFeature(int i, CharSequence charSequence, CharSequence charSequence2, CharSequence charSequence3, List<InlineDocBlock> list) {
        this.icon = i;
        this.title = charSequence;
        this.subtitle = charSequence2;
        this.badge = charSequence3;
        this.docs = list;
    }

    public final int getIcon() {
        return this.icon;
    }

    public final CharSequence getTitle() {
        return this.title;
    }

    public final CharSequence getSubtitle() {
        return this.subtitle;
    }

    public final CharSequence getBadge() {
        return this.badge;
    }

    public final List<InlineDocBlock> getDocs() {
        return this.docs;
    }
}

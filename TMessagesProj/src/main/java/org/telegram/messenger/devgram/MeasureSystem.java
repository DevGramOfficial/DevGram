package org.telegram.messenger.devgram;

import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;
public enum MeasureSystem {
    AUTO,
    METRIC,
    IMPERIAL;

    private static final /* synthetic */ EnumEntries $ENTRIES = EnumEntriesKt.enumEntries(values());

    public static EnumEntries<MeasureSystem> getEntries() {
        return $ENTRIES;
    }
}

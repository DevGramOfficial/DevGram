package org.telegram.messenger;

/** Fixed-color files from the original DevGram set; never changes an assigned badge ID. */
public final class DevGramThemeEmoji {
    private static final long[][] PAIRS = {
            {5451743048423748161L, 5463309549970696459L},
            {5411424042932543123L, 5460843238015412595L},
            {5413368748289598440L, 5461004187619864291L},
            {5413671801182004970L, 5463364130415094238L},
            {5454141602974969320L, 5460681364992996118L},
            {5453904490715457710L, 5460925679912660958L},
            {5454000921321189089L, 5460925224646128620L}
    };

    public static long forTone(long id, boolean darkInk) {
        for (long[] pair : PAIRS) {
            if (id == pair[0] || id == pair[1]) return pair[darkInk ? 1 : 0];
        }
        return id;
    }

    private DevGramThemeEmoji() {}
}

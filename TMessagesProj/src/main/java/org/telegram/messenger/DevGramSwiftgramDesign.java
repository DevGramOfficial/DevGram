package org.telegram.messenger;

/** Swiftgram appearance selection and live badge-based access, independent of profile customization. */
public final class DevGramSwiftgramDesign {
    public static final String FEATURE = "design.ios";
    public static final String REFERENCE_COMMIT = "b460f1d8948e725331b34981cc2a563aeeae97fd";

    public static boolean available() { return BuildConfig.INCLUDE_IOS_DESIGN; }

    public static boolean selected() {
        return ApplicationLoader.applicationContext != null
                && ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", 0).getInt("dg_designMode", 0) == DevGramConfig.DESIGN_MODE_IOS;
    }

    public static boolean allowed() {
        return available() && ApplicationLoader.applicationContext != null && NativeLoader.loaded()
                && DevGramProfilePolicy.allowed(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId(), FEATURE);
    }

    private static final String[] SECTIONS = "navigation chatlist chat profiles settings menus media contacts calls stories animations".split(" ");
    private static volatile Access access;
    private static final class Access {
        final long uid, revision;
        final int badges, mask;
        Access(long uid, long revision, int badges, int mask) {
            this.uid = uid; this.revision = revision; this.badges = badges; this.mask = mask;
        }
    }

    private static int mask() {
        if (!available() || !NativeLoader.loaded() || !selected()) return 0;
        long uid = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        long revision = DevGramProfilePolicy.revision();
        int badges = DevGramBadges.styleVersion;
        Access snapshot = access;
        if (snapshot != null && snapshot.uid == uid && snapshot.revision == revision && snapshot.badges == badges) return snapshot.mask;
        int mask = DevGramProfilePolicy.allowed(uid, FEATURE) ? 1 : 0;
        if (mask != 0) for (int i = 0; i < SECTIONS.length; i++) {
            if (DevGramProfilePolicy.allowed(uid, FEATURE + "." + SECTIONS[i])) mask |= 2 << i;
        }
        access = new Access(uid, revision, badges, mask);
        return mask;
    }

    public static boolean enabled() { return (mask() & 1) != 0; }

    public static void refreshResources() {
        if (ApplicationLoader.applicationContext != null) org.telegram.ui.ActionBar.Theme.reloadAllResources(ApplicationLoader.applicationContext);
    }

    public static boolean section(String name) {
        for (int i = 0; i < SECTIONS.length; i++) if (SECTIONS[i].equals(name)) return (mask() & (2 << i)) != 0;
        return false;
    }

    public static final int NAVIGATION_HEIGHT = 62;
    public static final int NAVIGATION_INSET = 21;
    public static final int NAVIGATION_INNER_INSET = 4;
    public static final int NAVIGATION_ICON = 28;
    public static final int NAVIGATION_LABEL = 10;
    public static final int NAVIGATION_BADGE = 18;
    public static final int BAR_BUTTON = 44;
    public static final int SECTION_INSET = 16;
    public static final int SECTION_RADIUS = 26;
    public static final int ROW_HEIGHT = 44;
    public static final int MENU_ROW_HEIGHT = 44;
    public static final int MENU_RADIUS = 30;
    public static final int HEADER_TITLE = 17;
    public static final int LARGE_TITLE = 34;
    public static final int COMPOSE_MIN_HEIGHT = 40;
    public static final int COMPOSE_INSET = 8;
    public static final int CHAT_LIST_AVATAR = 60;
    public static final int CHAT_LIST_ROW = 76;
    public static final int TRANSITION_MS = 400;

    private DevGramSwiftgramDesign() {}
}

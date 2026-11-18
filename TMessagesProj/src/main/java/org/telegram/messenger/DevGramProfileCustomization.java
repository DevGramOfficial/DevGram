package org.telegram.messenger;

/** Device-wide opt-out. Saved cloud designs and account roles are never erased. */
public final class DevGramProfileCustomization {
    private DevGramProfileCustomization() {}
    public static boolean enabled() { return MessagesController.getGlobalMainSettings().getBoolean("devgramProfileCustomization", true); }
    public static void setEnabled(boolean enabled) {
        if (enabled == enabled()) return;
        MessagesController.getGlobalMainSettings().edit().putBoolean("devgramProfileCustomization", enabled).apply();
        if (enabled) DevGramProfilePolicy.start(); else DevGramProfilePolicy.stop();
        DevGramProfilePolicy.changed();
        DevGramMusicListener.refreshCurrent();
    }
}

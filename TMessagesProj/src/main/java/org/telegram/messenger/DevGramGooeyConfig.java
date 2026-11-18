package org.telegram.messenger;

/** One opt-in default for the settings switch and both avatar renderers. */
public final class DevGramGooeyConfig {
    public static final boolean DEFAULT_ENABLED = false;
    private DevGramGooeyConfig() {}

    public static boolean enabled() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_gooey", DEFAULT_ENABLED);
    }
}

package org.telegram.ui.Components;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DevGramConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.SettingsActivity;

/** Classic chrome measured from the owner's AyuGram 12.2.10 / 63429 reference APK. */
public final class DevGramClassicDesign {
    private DevGramClassicDesign() {}

    public static boolean enabled() {
        return ApplicationLoader.applicationContext != null
                && DevGramConfig.getDesignMode() == DevGramConfig.DESIGN_MODE_OLD;
    }

    public static BaseFragment settings(int account) {
        SettingsActivity settings = new SettingsActivity();
        settings.setCurrentAccount(account);
        return settings;
    }

    public static final int DRAWER_WIDTH = 320;
    public static final int DRAWER_EDGE = 56;
    public static final int DRAWER_HEADER_HEIGHT = 148;
    public static final int DRAWER_AVATAR_SIZE = 64;
    public static final int DRAWER_ROW_HEIGHT = 48;
    public static final int COMPOSE_HEIGHT = 48;
    public static final int FAB_SIZE = 56;
    public static final int FAB_MARGIN = 14;
    public static final int SUB_FAB_SIZE = 40;
    public static final int SUB_FAB_MARGIN = 22;
    public static final int SUB_FAB_BOTTOM = 82;
    public static final int FOLDER_TABS_HEIGHT = 48;
    public static final int HEADER_TITLE_SIZE = 20;
}

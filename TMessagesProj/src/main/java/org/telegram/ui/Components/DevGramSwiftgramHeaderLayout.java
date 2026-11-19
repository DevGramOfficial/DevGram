package org.telegram.ui.Components;

/** NavigationBarTitlePlacement.swift and PeerInfoSideInsets.swift geometry, in logical points. */
public final class DevGramSwiftgramHeaderLayout {
    public static float titleX(float width, float leftInset, float rightInset,
                               float leftTitleInset, float rightTitleInset, float titleWidth) {
        float band = width - leftInset - rightInset;
        float margin = Math.max(leftTitleInset - leftInset, rightTitleInset - rightInset);
        return titleWidth <= band - 2 * margin
                ? (float) Math.floor(leftInset + (band - titleWidth) / 2)
                : leftTitleInset + (float) Math.floor((width - leftTitleInset - rightTitleInset - titleWidth) / 2);
    }

    public static float chatLeadingInset(float backWidth) {
        return 16 + (backWidth > 0 ? backWidth + 8 : 0);
    }

    public static float chatTrailingInset(float menuWidth, boolean avatarVisible) {
        float controls = Math.max(menuWidth, avatarVisible ? 44 : 0);
        return 16 + controls + (controls > 0 ? 8 : 0);
    }

    public static float chatContentLimit(float width, float backWidth, float menuWidth, boolean avatarVisible) {
        return Math.max(0, width - chatLeadingInset(backWidth) - chatTrailingInset(menuWidth, avatarVisible) - 24);
    }

    public static float chatPillWidth(float width, float backWidth, float menuWidth, boolean avatarVisible, float contentWidth) {
        float available = Math.max(0, width - chatLeadingInset(backWidth) - chatTrailingInset(menuWidth, avatarVisible));
        return Math.min(available, Math.max(174, contentWidth + 24));
    }

    public static float sectionInset(float width) { return width >= 320 ? Math.max(16, (float) Math.floor((width - 674) / 2)) : 0; }
    private DevGramSwiftgramHeaderLayout() {}
}

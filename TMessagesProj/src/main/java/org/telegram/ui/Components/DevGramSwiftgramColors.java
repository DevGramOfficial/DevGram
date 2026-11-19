package org.telegram.ui.Components;

import android.util.SparseIntArray;
import org.telegram.messenger.DevGramSwiftgramDesign;
import org.telegram.ui.ActionBar.Theme;

/** Palette ported from Swiftgram's DefaultDay/DefaultDarkPresentationTheme, reference b460f1d8. */
public final class DevGramSwiftgramColors {
    private static final SparseIntArray KEYS = new SparseIntArray();
    static {
        KEYS.put(Theme.key_windowBackgroundGray, 1);
        KEYS.put(Theme.key_windowBackgroundWhite, 2);
        KEYS.put(Theme.key_windowBackgroundWhiteBlackText, 3);
        KEYS.put(Theme.key_dialogTextBlack, 4);
        KEYS.put(Theme.key_actionBarDefaultTitle, 5);
        KEYS.put(Theme.key_windowBackgroundWhiteGrayText, 6);
        KEYS.put(Theme.key_windowBackgroundWhiteGrayText2, 7);
        KEYS.put(Theme.key_windowBackgroundWhiteGrayText3, 8);
        KEYS.put(Theme.key_dialogTextGray, 9);
        KEYS.put(Theme.key_dialogTextGray2, 10);
        KEYS.put(Theme.key_actionBarDefaultSubtitle, 11);
        KEYS.put(Theme.key_windowBackgroundWhiteBlueText, 12);
        KEYS.put(Theme.key_windowBackgroundWhiteBlueText2, 13);
        KEYS.put(Theme.key_windowBackgroundWhiteBlueText3, 14);
        KEYS.put(Theme.key_windowBackgroundWhiteBlueText4, 15);
        KEYS.put(Theme.key_dialogTextBlue, 16);
        KEYS.put(Theme.key_dialogButton, 17);
        KEYS.put(Theme.key_dialogTextLink, 18);
        KEYS.put(Theme.key_actionBarDefaultIcon, 19);
        KEYS.put(Theme.key_actionBarDefaultSearch, 20);
        KEYS.put(Theme.key_featuredStickers_addButton, 21);
        KEYS.put(Theme.key_divider, 22);
        KEYS.put(Theme.key_listSelector, 23);
        
        KEYS.put(Theme.key_windowBackgroundWhiteGrayIcon, 25);
        KEYS.put(Theme.key_text_RedRegular, 26);
        KEYS.put(Theme.key_text_RedBold, 27);
        
        KEYS.put(Theme.key_switchTrack, 29);
        KEYS.put(Theme.key_switchTrackChecked, 30);
        KEYS.put(Theme.key_switchTrackBlueThumb, 31);
        KEYS.put(Theme.key_switchTrackBlueThumbChecked, 32);
        KEYS.put(Theme.key_actionBarDefault, 33);
        KEYS.put(Theme.key_glass_tabSelected, 34);
        KEYS.put(Theme.key_glass_tabSelectedText, 35);
        KEYS.put(Theme.key_glass_tabUnselected, 36);
        KEYS.put(Theme.key_actionBarDefaultSubmenuBackground, 37);
        KEYS.put(Theme.key_dialogBackground, 38);
        KEYS.put(Theme.key_actionBarDefaultSubmenuItem, 39);
        KEYS.put(Theme.key_actionBarDefaultSubmenuItemIcon, 40);
        KEYS.put(Theme.key_chats_unreadCounter, 41);
        KEYS.put(Theme.key_chats_unreadCounterMuted, 42);
        KEYS.put(Theme.key_chats_name, 43);
        KEYS.put(Theme.key_chats_message, 44);
        KEYS.put(Theme.key_chats_date, 45);
        KEYS.put(Theme.key_chat_inBubble, 46);
        KEYS.put(Theme.key_chat_outBubble, 47);
        KEYS.put(Theme.key_chat_messageTextIn, 48);
        KEYS.put(Theme.key_chat_messageTextOut, 49);
        KEYS.put(Theme.key_chat_outBubbleGradient1, 50);
        KEYS.put(Theme.key_chat_outBubbleGradient2, 51);
        KEYS.put(Theme.key_chat_outBubbleGradient3, 52);
        KEYS.put(Theme.key_chat_outBubbleGradientAnimated, 53);
    }

    public static int resolve(int key, int original) {
        if (!DevGramSwiftgramDesign.enabled()) return original;
        boolean dark = Theme.getActiveTheme() != null && Theme.isCurrentThemeDark();
        int accent = dark ? 0xff3e88f7 : 0xff0088ff;
        switch (KEYS.get(key)) {
            case 1: return dark ? 0xff000000 : 0xffefeff4;
            case 2: return dark ? 0xff1c1c1d : 0xffffffff;
            case 3:
            case 4:
            case 5:
                return dark ? 0xffffffff : 0xff000000;
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
                return dark ? 0xff98989e : 0xff8e8e93;
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
                return accent;
            case 22: return dark ? 0x8c545458 : 0xffc8c7cc;
            case 23:
            case 24:
                return dark ? 0xff313135 : 0xffe5e5ea;
            case 25:
                return dark ? 0xff98989e : 0xff8e8e93;
            case 26:
            case 27:
            case 28:
                return dark ? 0xffeb5545 : 0xffff3b30;
            case 29: return dark ? 0xff39393d : 0xffe9e9ea;
            case 30: return dark ? 0xff67ce67 : 0xff35c759;
            case 31:
            case 32: return 0xffffffff;
            case 33:
                return dark ? 0xff000000 : 0xfff8f8f8;
            case 34:
            case 35: return accent;
            case 36: return dark ? 0xff98989e : 0xff959595;
            case 37:
            case 38:
                return DevGramSwiftgramDesign.section("menus") ? (dark ? 0xff252525 : 0xfff9f9f9) : original;
            case 39: return dark ? 0xffffffff : 0xff000000;
            case 40: return dark ? 0xffffffff : 0xff000000;
            case 41: return DevGramSwiftgramDesign.section("chatlist") ? 0xffff3b30 : original;
            case 42: return DevGramSwiftgramDesign.section("chatlist") ? (dark ? 0xff545458 : 0xffc8c7cc) : original;
            case 43: return DevGramSwiftgramDesign.section("chatlist") ? (dark ? 0xffffffff : 0xff000000) : original;
            case 44:
            case 45: return DevGramSwiftgramDesign.section("chatlist") ? (dark ? 0xff98989e : 0xff8e8e93) : original;
            case 46: return DevGramSwiftgramDesign.section("chat") ? (dark ? 0xff1c1c1d : 0xffffffff) : original;
            case 47: return DevGramSwiftgramDesign.section("chat") ? (dark ? 0xff61bcf9 : 0xffe1ffc7) : original;
            case 48: return DevGramSwiftgramDesign.section("chat") ? (dark ? 0xffffffff : 0xff000000) : original;
            case 49: return DevGramSwiftgramDesign.section("chat") ? (dark ? 0xffffffff : 0xff000000) : original;
            case 50: return DevGramSwiftgramDesign.section("chat") ? (dark ? 0xff0088ff : 0) : original;
            case 51: return DevGramSwiftgramDesign.section("chat") ? (dark ? 0 : 0) : original;
            case 52: return DevGramSwiftgramDesign.section("chat") ? (dark ? 0 : 0) : original;
            case 53: return DevGramSwiftgramDesign.section("chat") ? (dark ? 0 : 0) : original;
            default: return original;
        }
    }

    private DevGramSwiftgramColors() {}
}

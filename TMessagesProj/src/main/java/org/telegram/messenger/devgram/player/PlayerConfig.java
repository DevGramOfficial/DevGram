package org.telegram.messenger.devgram.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/** Настройки нового музыкального плеера. */
public final class PlayerConfig {

    private PlayerConfig() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("devgram_player", Context.MODE_PRIVATE);
    }

    private static boolean b(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    private static void b(String key, boolean value, boolean unused) {
        prefs().edit().putBoolean(key, value).apply();
    }

    // входит в группу Material Design 3: выключена группа — старый плеер
    public static boolean getNewPlayerStyle() { return b("newPlayerStyle", false) && org.telegram.ui.Components.DevGramMaterial3.enabled(); }
    public static void setNewPlayerStyle(boolean v) { b("newPlayerStyle", v, true); }

    public static boolean getPlayerCoverColors() { return profileMusicAllowed("music.player")&&profileMusicPreference("devgramProfilePlayerCover","playerCover"); }
    public static void setPlayerCoverColors(boolean v) { b("playerCoverColors", v, true); }

    private static int activeStudioAccount = -1;
    public static void setStudioAccount(int account) { activeStudioAccount = account; }
    public static boolean studioFlag(String key, String feature, boolean fallback) {
        long uid = org.telegram.messenger.UserConfig.getInstance(activeStudioAccount < 0 ? org.telegram.messenger.UserConfig.selectedAccount : activeStudioAccount).getClientUserId();
        return org.telegram.messenger.DevGramProfilePolicy.allowed(uid, feature)
                && org.telegram.messenger.DevGramProfileDocument.number(uid, key, fallback ? 1 : 0) == 1;
    }

    private static boolean profileMusicAllowed(String feature){long uid=org.telegram.messenger.UserConfig.getInstance(activeStudioAccount<0?org.telegram.messenger.UserConfig.selectedAccount:activeStudioAccount).getClientUserId();return org.telegram.messenger.DevGramProfilePolicy.allowed(uid,feature);}
    public static boolean profileMusicPreference(String localKey,String previousField){android.content.SharedPreferences settings=org.telegram.messenger.MessagesController.getGlobalMainSettings();if(settings.contains(localKey))return settings.getBoolean(localKey,true);long uid=org.telegram.messenger.UserConfig.getInstance(activeStudioAccount<0?org.telegram.messenger.UserConfig.selectedAccount:activeStudioAccount).getClientUserId();return org.telegram.messenger.DevGramProfileDocument.number(uid,previousField,1)==1;}
    public static boolean getProfileLyricsMotion(){return profileMusicAllowed("music.lyrics.animated")&&profileMusicPreference("devgramProfileLyricsMotion","lyricsMotion");}
    public static boolean getPlayerMiniCoverColors() { return b("playerMiniCoverColors", false); }
    public static void setPlayerMiniCoverColors(boolean v) { b("playerMiniCoverColors", v, true); }

    public static boolean getLyricsOnline() { return b("lyricsOnline", false); }
    public static void setLyricsOnline(boolean v) { b("lyricsOnline", v, true); }

    public static int getLyricsProgress() { return prefs().getInt("lyricsProgress", 1); }
    public static void setLyricsProgress(int v) { prefs().edit().putInt("lyricsProgress", v).apply(); }

    public static boolean getPlayerMotionCovers() { return b("playerMotionCovers", false); }
    public static void setPlayerMotionCovers(boolean v) { b("playerMotionCovers", v, true); }

    public static boolean getPlayerRhythmBackground() { return b("playerRhythmBackground", true); }
    public static void setPlayerRhythmBackground(boolean v) { b("playerRhythmBackground", v, true); }

    public static boolean getPlayerParticles() { return b("playerParticles", false); }
    public static void setPlayerParticles(boolean v) { b("playerParticles", v, true); }

    public static boolean getPlayerSpeedPitch() { return b("playerSpeedPitch", false); }
    public static void setPlayerSpeedPitch(boolean v) { b("playerSpeedPitch", v, true); }

    public static int getPlayerMode() { return prefs().getInt("playerMode", 0); }
    public static void setPlayerMode(int v) { prefs().edit().putInt("playerMode", v).apply(); }
}

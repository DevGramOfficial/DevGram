package org.telegram.messenger.devgram.player;

import android.text.TextUtils;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.audioinfo.AudioInfo;
public final class TrackQuery {
    private final String album;
    private final String artist;
    private final int duration;
    private final String title;
    public static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern AUDIO_EXT = Pattern.compile("\\.(mp3|m4a|flac|ogg|oga|opus|wav|aac|alac|wma)$", 2);
    private static final Pattern DECORATION = Pattern.compile("\\s*[(\\[][^)\\]]*(official|lyric|video|audio|visualizer|remaster|hq|hd)[^)\\]]*[)\\]]", 2);



    public TrackQuery(String str, String str2, String str3, int i) {
        this.artist = str;
        this.title = str2;
        this.album = str3;
        this.duration = i;
    }

    public String album() {
        return this.album;
    }

    public String artist() {
        return this.artist;
    }

    public int duration() {
        return this.duration;
    }


    public final int hashCode() {
        return ((((((this.duration * 31) + Objects.hashCode(this.artist)) * 31) + Objects.hashCode(this.title)) * 31) + Objects.hashCode(this.album));
    }

    public String title() {
        return this.title;
    }


    public static TrackQuery forMessage(MessageObject messageObject) {
        int iIndexOf;
        if (messageObject == null || messageObject.isVoice() || messageObject.isRoundVideo()) {
            return null;
        }
        AudioInfo playingAudioInfo = PlayerCovers.getPlayingAudioInfo(messageObject);
        String strClean = clean(messageObject.getMusicAuthor(false));
        String strClean2 = clean(messageObject.getMusicTitle(false));
        if (strClean2 != null) {
            strClean2 = AUDIO_EXT.matcher(strClean2).replaceFirst("").replace('_', ' ').trim();
        }
        if (TextUtils.isEmpty(strClean) && strClean2 != null && (iIndexOf = strClean2.indexOf(" - ")) > 0) {
            strClean = strClean2.substring(0, iIndexOf).trim();
            strClean2 = strClean2.substring(iIndexOf + 3).trim();
        }
        String strStripDecorations = stripDecorations(strClean2);
        if (TextUtils.isEmpty(strStripDecorations)) {
            return null;
        }
        return new TrackQuery(strClean, strStripDecorations, clean(playingAudioInfo != null ? playingAudioInfo.getAlbum() : null), (int) Math.round(messageObject.getDuration()));
    }

    public String key() {
        StringBuilder sb = new StringBuilder();
        String str = this.artist;
        sb.append(str == null ? "" : str.toLowerCase(Locale.ROOT));
        sb.append("\u0001");
        sb.append(this.title.toLowerCase(Locale.ROOT));
        sb.append("\u0001");
        sb.append(this.duration);
        return sb.toString();
    }

    public static String normalize(String str) {
        return str == null ? "" : NOT_WORD.matcher(str.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static String clean(String str) {
        if (str == null) {
            return null;
        }
        String strTrim = str.trim();
        if (strTrim.isEmpty()) {
            return null;
        }
        return strTrim;
    }

    private static String stripDecorations(String str) {
        if (str == null) {
            return null;
        }
        String strTrim = DECORATION.matcher(str).replaceAll("").trim();
        return strTrim.isEmpty() ? str : strTrim;
    }
}

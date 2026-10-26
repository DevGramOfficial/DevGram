package org.telegram.messenger.devgram.player.lyrics;

import android.text.TextUtils;
import android.util.LruCache;
import org.telegram.messenger.devgram.player.PlayerCache;
import org.telegram.messenger.devgram.player.PlayerCovers;
import org.telegram.messenger.devgram.player.TrackQuery;
import org.telegram.messenger.devgram.player.PlayerHttp;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.audioinfo.AudioInfo;
public abstract class LyricsRepository {
    private static final LruCache<String, Lyrics> memory = new LruCache<>(24);
    private static final LruCache<String, Boolean> missing = new LruCache<>(64);

    public interface Callback {
        void onResult(Lyrics lyrics, int i);
    }

    public static Lyrics fromTags(MessageObject messageObject) {
        AudioInfo playingAudioInfo = PlayerCovers.getPlayingAudioInfo(messageObject);
        if (playingAudioInfo == null) {
            return null;
        }
        return Lyrics.parse(playingAudioInfo.getLyrics(), 0);
    }

    public static Lyrics getCached(TrackQuery trackQuery) {
        if (trackQuery == null) {
            return null;
        }
        return memory.get(trackQuery.key());
    }

    public static boolean isKnownMissing(TrackQuery trackQuery) {
        return (trackQuery == null || missing.get(trackQuery.key()) == null) ? false : true;
    }

    public static void loadCached(TrackQuery trackQuery, final Callback callback) {
        final String strKey = trackQuery.key();
        Lyrics lyrics = memory.get(strKey);
        if (lyrics != null) {
            callback.onResult(lyrics, 0);
        } else {
            Utilities.externalNetworkQueue.postRunnable(() -> LyricsRepository.m1749$r8$lambda$THaAn9GoH_trq56KdVX0Tfou1E(strKey, callback));
        }
    }
    public static /* synthetic */ void m1749$r8$lambda$THaAn9GoH_trq56KdVX0Tfou1E(final String str, final Callback callback) {
        final Lyrics disk = readDisk(str);
        AndroidUtilities.runOnUIThread(() -> LyricsRepository.$r8$lambda$IZAzn1zyheq1WFRkTgzLkJ99JOs(disk, str, callback));
    }

    public static /* synthetic */ void $r8$lambda$IZAzn1zyheq1WFRkTgzLkJ99JOs(Lyrics lyrics, String str, Callback callback) {
        if (lyrics != null) {
            memory.put(str, lyrics);
        }
        callback.onResult(lyrics, lyrics != null ? 0 : 1);
    }

    public static void fetch(final TrackQuery trackQuery, final Callback callback) {
        final String strKey = trackQuery.key();
        Lyrics lyrics = memory.get(strKey);
        if (lyrics != null) {
            callback.onResult(lyrics, 0);
        } else {
            Utilities.externalNetworkQueue.postRunnable(() -> LyricsRepository.$r8$lambda$UMIuYVQwRciJh9Clg31H4oHqeEM(strKey, trackQuery, callback));
        }
    }

    /* JADX WARN: Code duplicated, block: B:14:0x001f A[PHI: r5
      0x001f: PHI (r5v5 boolean) = (r5v3 boolean), (r5v1 boolean), (r5v1 boolean) binds: [B:28:0x003f, B:11:0x0019, B:13:0x001d] A[DONT_GENERATE, DONT_INLINE]] */
    public static /* synthetic */ void $r8$lambda$UMIuYVQwRciJh9Clg31H4oHqeEM(final String str, TrackQuery trackQuery, final Callback callback) {
        Lyrics lyricsRequestLrclib;
        boolean z;
        Lyrics lyricsPunctuate;
        Lyrics disk = readDisk(str);
        int i = 0;
        if (disk == null) {
            Lyrics lyricsRequestLrcmux = null;
            try {
                lyricsRequestLrclib = requestLrclib(trackQuery);
                z = false;
            } catch (Throwable th) {
                FileLog.e(th);
                lyricsRequestLrclib = null;
                z = true;
            }
            if (lyricsRequestLrclib == null || !(lyricsRequestLrclib.synced || lyricsRequestLrclib.instrumental)) {
                try {
                    lyricsRequestLrcmux = requestLrcmux(trackQuery);
                } catch (Throwable th2) {
                    FileLog.e(th2);
                    z = true;
                }
                if (lyricsRequestLrcmux != null && (lyricsRequestLrcmux.synced || lyricsRequestLrclib == null)) {
                    disk = (lyricsRequestLrclib == null || (lyricsPunctuate = punctuate(lyricsRequestLrcmux, lyricsRequestLrclib)) == null) ? lyricsRequestLrcmux : lyricsPunctuate;
                } else if (lyricsRequestLrclib != null) {
                    disk = lyricsRequestLrclib;
                }
            } else {
                disk = lyricsRequestLrclib;
            }
            if (disk == null) {
                i = z ? 2 : 1;
            } else if (!z) {
                writeDisk(str, disk);
            }
        }
        final Lyrics result = disk;
        final int status = i;
        AndroidUtilities.runOnUIThread(() -> LyricsRepository.$r8$lambda$h3hg8XQzNE_VaLXrdRUIgr1AiR0(result, str, status, callback));
    }

    public static /* synthetic */ void $r8$lambda$h3hg8XQzNE_VaLXrdRUIgr1AiR0(Lyrics lyrics, String str, int i, Callback callback) {
        if (lyrics != null) {
            memory.put(str, lyrics);
            missing.remove(str);
        } else if (i == 1) {
            missing.put(str, Boolean.TRUE);
        }
        callback.onResult(lyrics, i);
    }

    private static Lyrics requestLrclib(TrackQuery trackQuery) throws Exception {
        Lyrics lyricsFromLrclib;
        if (!TextUtils.isEmpty(trackQuery.artist())) {
            HttpUrl.Builder builderAddQueryParameter = HttpUrl.get("https://lrclib.net/api/get").newBuilder().addQueryParameter("artist_name", trackQuery.artist()).addQueryParameter("track_name", trackQuery.title());
            if (!TextUtils.isEmpty(trackQuery.album())) {
                builderAddQueryParameter.addQueryParameter("album_name", trackQuery.album());
            }
            if (trackQuery.duration() > 0) {
                builderAddQueryParameter.addQueryParameter("duration", String.valueOf(trackQuery.duration()));
            }
            // точный запрос иногда отвечает 5xx — тогда не сдаёмся, а ищем ниже через search
            String str = null;
            try {
                str = get(builderAddQueryParameter.build());
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (str != null && (lyricsFromLrclib = fromLrclib(new JSONObject(str))) != null) {
                return lyricsFromLrclib;
            }
        }
        HttpUrl.Builder builderAddQueryParameter2 = HttpUrl.get("https://lrclib.net/api/search").newBuilder().addQueryParameter("track_name", trackQuery.title());
        if (!TextUtils.isEmpty(trackQuery.artist())) {
            builderAddQueryParameter2.addQueryParameter("artist_name", trackQuery.artist());
        }
        String str2 = get(builderAddQueryParameter2.build());
        if (str2 == null) {
            return null;
        }
        JSONArray jSONArray = new JSONArray(str2);
        int i = Integer.MIN_VALUE;
        Lyrics lyrics = null;
        for (int i2 = 0; i2 < jSONArray.length(); i2++) {
            JSONObject jSONObjectOptJSONObject = jSONArray.optJSONObject(i2);
            Lyrics lyricsFromLrclib2 = jSONObjectOptJSONObject == null ? null : fromLrclib(jSONObjectOptJSONObject);
            if (lyricsFromLrclib2 != null) {
                int i3 = lyricsFromLrclib2.synced ? 40 : 0;
                if (trackQuery.duration() > 0 && jSONObjectOptJSONObject.has("duration")) {
                    int iAbs = (int) Math.abs(Math.round(jSONObjectOptJSONObject.optDouble("duration", 0.0d)) - ((long) trackQuery.duration()));
                    if (iAbs > 3) {
                        iAbs = Math.min(iAbs, 60) + 20;
                    }
                    i3 -= iAbs;
                }
                if (i3 > i) {
                    lyrics = lyricsFromLrclib2;
                    i = i3;
                }
            }
        }
        return lyrics;
    }

    private static String optString(JSONObject jSONObject, String str) {
        if (jSONObject == null || jSONObject.isNull(str)) {
            return null;
        }
        String strOptString = jSONObject.optString(str, null);
        if (TextUtils.isEmpty(strOptString)) {
            return null;
        }
        return strOptString;
    }

    private static Lyrics fromLrclib(JSONObject jSONObject) {
        Lyrics lyrics;
        if (jSONObject.optBoolean("instrumental", false)) {
            return Lyrics.instrumental(1, "LRCLIB");
        }
        Lyrics lyrics2 = Lyrics.parse(optString(jSONObject, "syncedLyrics"), 1, "LRCLIB");
        return ((lyrics2 == null || !lyrics2.synced) && (lyrics = Lyrics.parse(optString(jSONObject, "plainLyrics"), 1, "LRCLIB")) != null) ? lyrics : lyrics2;
    }

    private static Lyrics requestLrcmux(TrackQuery trackQuery) throws Exception {
        int iOptInt;
        HttpUrl.Builder builderAddQueryParameter = HttpUrl.get("https://api.lrcmux.dev/get").newBuilder().addQueryParameter("title", trackQuery.title()).addQueryParameter("level", "line").addQueryParameter("format", "json");
        if (!TextUtils.isEmpty(trackQuery.artist())) {
            builderAddQueryParameter.addQueryParameter("artist", trackQuery.artist());
        }
        if (!TextUtils.isEmpty(trackQuery.album())) {
            builderAddQueryParameter.addQueryParameter("album", trackQuery.album());
        }
        if (trackQuery.duration() > 0) {
            builderAddQueryParameter.addQueryParameter("duration", String.valueOf(trackQuery.duration()));
        }
        String str = get(builderAddQueryParameter.build());
        if (str == null) {
            return null;
        }
        JSONObject jSONObject = new JSONObject(str);
        JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("track");
        JSONObject jSONObjectOptJSONObject2 = jSONObject.optJSONObject("meta");
        int i = 0;
        if (trackQuery.duration() > 0 && jSONObjectOptJSONObject != null && (iOptInt = jSONObjectOptJSONObject.optInt("duration", 0)) > 0 && Math.abs(iOptInt - trackQuery.duration()) > 5) {
            return null;
        }
        JSONObject jSONObjectOptJSONObject3 = jSONObjectOptJSONObject2 != null ? jSONObjectOptJSONObject2.optJSONObject("source") : null;
        String strOptString = jSONObjectOptJSONObject3 != null ? optString(jSONObjectOptJSONObject3, "name") : null;
        String strConcat = strOptString != null ? "lrcmux · ".concat(strOptString) : "lrcmux";
        if (jSONObjectOptJSONObject2 != null && jSONObjectOptJSONObject2.optBoolean("instrumental", false)) {
            return Lyrics.instrumental(1, strConcat);
        }
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("lines");
        if (jSONArrayOptJSONArray == null || jSONArrayOptJSONArray.length() == 0) {
            return null;
        }
        if (jSONObjectOptJSONObject2 == null || "none".equals(jSONObjectOptJSONObject2.optString("level"))) {
            StringBuilder sb = new StringBuilder();
            while (i < jSONArrayOptJSONArray.length()) {
                JSONObject jSONObjectOptJSONObject4 = jSONArrayOptJSONArray.optJSONObject(i);
                if (jSONObjectOptJSONObject4 != null) {
                    sb.append(jSONObjectOptJSONObject4.optString("text", ""));
                    sb.append('\n');
                }
                i++;
            }
            return Lyrics.parse(sb.toString(), 1, strConcat);
        }
        ArrayList arrayList = new ArrayList(jSONArrayOptJSONArray.length());
        while (i < jSONArrayOptJSONArray.length()) {
            JSONObject jSONObjectOptJSONObject5 = jSONArrayOptJSONArray.optJSONObject(i);
            if (jSONObjectOptJSONObject5 != null) {
                arrayList.add(new Lyrics.Line(Math.max(0L, jSONObjectOptJSONObject5.optLong("start", 0L)), jSONObjectOptJSONObject5.optString("text", "").trim()));
            }
            i++;
        }
        if (arrayList.isEmpty()) {
            return null;
        }
        return Lyrics.fromTimedLines(arrayList, 1, strConcat);
    }

    private static int firstLetter(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (Character.isLetter(str.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String withCase(String str, String str2) {
        int iFirstLetter = firstLetter(str);
        int iFirstLetter2 = firstLetter(str2);
        if (iFirstLetter < 0 || iFirstLetter2 < 0) {
            return str;
        }
        boolean zIsUpperCase = Character.isUpperCase(str2.charAt(iFirstLetter2));
        char cCharAt = str.charAt(iFirstLetter);
        if (Character.isUpperCase(cCharAt) == zIsUpperCase) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(str.substring(0, iFirstLetter));
        sb.append(zIsUpperCase ? Character.toUpperCase(cCharAt) : Character.toLowerCase(cCharAt));
        sb.append(str.substring(iFirstLetter + 1));
        return sb.toString();
    }

    private static void appendWord(StringBuilder sb, String str) {
        if (str.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(str);
    }

    private static Lyrics punctuate(Lyrics lyrics, Lyrics lyrics2) {
        String str;
        ArrayList<Lyrics.Line> arrayList;
        String str2;
        ArrayList arrayList2;
        ArrayList arrayList3 = new ArrayList();
        ArrayList arrayList4 = new ArrayList();
        ArrayList<Lyrics.Line> arrayList5 = lyrics2.lines;
        int size = arrayList5.size();
        int i = 0;
        while (true) {
            str = "\\s+";
            if (i >= size) {
                break;
            }
            Lyrics.Line line = arrayList5.get(i);
            i++;
            for (String str3 : line.text.split("\\s+")) {
                String strNormalize = TrackQuery.normalize(str3);
                if (!strNormalize.isEmpty()) {
                    arrayList3.add(str3);
                    arrayList4.add(strNormalize);
                }
            }
        }
        Lyrics lyrics3 = null;
        if (arrayList4.isEmpty()) {
            return null;
        }
        ArrayList arrayList6 = new ArrayList(lyrics.lines.size());
        ArrayList<Lyrics.Line> arrayList7 = lyrics.lines;
        int size2 = arrayList7.size();
        int i2 = 0;
        int i3 = 0;
        int i4 = 0;
        int i5 = 0;
        while (i5 < size2) {
            Lyrics.Line line2 = arrayList7.get(i5);
            i5++;
            Lyrics.Line line3 = line2;
            StringBuilder sb = new StringBuilder();
            String[] strArrSplit = line3.text.split(str);
            Lyrics lyrics4 = lyrics3;
            int length = strArrSplit.length;
            int i6 = 0;
            while (i6 < length) {
                int i7 = length;
                String str4 = strArrSplit[i6];
                int i8 = i6;
                String strNormalize2 = TrackQuery.normalize(str4);
                if (strNormalize2.isEmpty()) {
                    appendWord(sb, str4);
                    arrayList2 = arrayList4;
                    arrayList = arrayList7;
                    str2 = str;
                } else {
                    i2++;
                    arrayList = arrayList7;
                    str2 = str;
                    int iMin = Math.min(i4 + 4, arrayList4.size());
                    int i9 = i4;
                    while (true) {
                        if (i9 >= iMin) {
                            arrayList2 = arrayList4;
                            i9 = -1;
                            break;
                        }
                        arrayList2 = arrayList4;
                        if (((String) arrayList4.get(i9)).equals(strNormalize2)) {
                            break;
                        }
                        i9++;
                        arrayList4 = arrayList2;
                    }
                    if (i9 < 0) {
                        appendWord(sb, str4);
                    } else {
                        i3++;
                        appendWord(sb, withCase((String) arrayList3.get(i9), str4));
                        i4 = i9 + 1;
                    }
                }
                i6 = i8 + 1;
                length = i7;
                arrayList7 = arrayList;
                str = str2;
                arrayList4 = arrayList2;
            }
            arrayList6.add(new Lyrics.Line(line3.time, sb.toString()));
            lyrics3 = lyrics4;
            arrayList7 = arrayList7;
            arrayList4 = arrayList4;
        }
        Lyrics lyrics5 = lyrics3;
        if (i2 == 0 || i3 < i2 * 0.9f) {
            return lyrics5;
        }
        return Lyrics.synced(arrayList6, lyrics.source, lyrics.provider + " + LRCLIB");
    }

    private static String get(HttpUrl httpUrl) throws Exception {
        Response responseExecute = PlayerHttp.client().newCall(new Request.Builder().url(httpUrl).header("User-Agent", "DevGram/" + BuildVars.BUILD_VERSION_STRING).header("Accept", "application/json").build()).execute();
        try {
            int iCode = responseExecute.code();
            if (iCode == 404) {
                responseExecute.close();
                return null;
            }
            if (iCode < 200 || iCode >= 300) {
                throw new IllegalStateException("lyrics http " + iCode);
            }
            String strString = responseExecute.body().string();
            responseExecute.close();
            return strString;
        } catch (Throwable th) {
            if (responseExecute != null) {
                try {
                    responseExecute.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private static File cacheFile(String str) {
        File fileDirectory = PlayerCache.directory("lyrics");
        if (fileDirectory == null) {
            return null;
        }
        return new File(fileDirectory, Utilities.MD5(str) + ".json");
    }

    private static Lyrics readDisk(String str) {
        String res;
        try {
            File fileCacheFile = cacheFile(str);
            if (fileCacheFile == null || !fileCacheFile.exists() || (res = AndroidUtilities.readRes(fileCacheFile)) == null) {
                return null;
            }
            JSONObject jSONObject = new JSONObject(res);
            if (jSONObject.optInt("v", 0) != 1) {
                return null;
            }
            String strOptString = optString(jSONObject, "provider");
            if (strOptString == null) {
                strOptString = "LRCLIB";
            }
            if (jSONObject.optBoolean("instrumental", false)) {
                return Lyrics.instrumental(1, strOptString);
            }
            return Lyrics.parse(optString(jSONObject, "text"), 1, strOptString);
        } catch (Throwable th) {
            FileLog.e(th);
            return null;
        }
    }

    private static void writeDisk(String str, Lyrics lyrics) {
        try {
            File fileCacheFile = cacheFile(str);
            if (fileCacheFile == null) {
                return;
            }
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("v", 1);
            jSONObject.put("provider", lyrics.provider);
            jSONObject.put("instrumental", lyrics.instrumental);
            jSONObject.put("text", lyrics.toText());
            FileOutputStream fileOutputStream = new FileOutputStream(fileCacheFile);
            try {
                fileOutputStream.write(jSONObject.toString().getBytes(StandardCharsets.UTF_8));
                fileOutputStream.close();
            } catch (Throwable th) {
                try {
                    fileOutputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Throwable th3) {
            FileLog.e(th3);
        }
    }
}

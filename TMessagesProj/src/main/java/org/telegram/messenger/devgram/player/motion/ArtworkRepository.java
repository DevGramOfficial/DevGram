package org.telegram.messenger.devgram.player.motion;

import android.text.TextUtils;
import android.util.LruCache;
import org.telegram.messenger.devgram.player.PlayerCache;
import org.telegram.messenger.devgram.player.TrackQuery;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
public abstract class ArtworkRepository {
    private static final LruCache<String, String> memory = new LruCache<>(128);
    private static final HashMap<String, ArrayList<Callback>> pending = new HashMap<>();

    public interface Callback {
        void onResult(String str);
    }

    public static String getCached(TrackQuery trackQuery) {
        if (trackQuery == null) {
            return null;
        }
        String str = memory.get(trackQuery.key());
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        return str;
    }

    public static boolean isResolved(TrackQuery trackQuery) {
        return trackQuery == null || memory.get(trackQuery.key()) != null;
    }

    public static void fetch(final TrackQuery trackQuery, Callback callback) {
        if (trackQuery == null) {
            return;
        }
        final String strKey = trackQuery.key();
        String str = memory.get(strKey);
        if (str != null) {
            if (str.isEmpty()) {
                str = null;
            }
            callback.onResult(str);
            return;
        }
        HashMap<String, ArrayList<Callback>> map = pending;
        ArrayList<Callback> arrayList = map.get(strKey);
        if (arrayList != null) {
            arrayList.add(callback);
            return;
        }
        ArrayList<Callback> arrayList2 = new ArrayList<>();
        arrayList2.add(callback);
        map.put(strKey, arrayList2);
        MotionCoverRepository.queue.postRunnable(() -> ArtworkRepository.$r8$lambda$WW0uUOdnJ8KxSBvfPOWe0cqRg44(trackQuery, strKey));
    }

    public static /* synthetic */ void $r8$lambda$WW0uUOdnJ8KxSBvfPOWe0cqRg44(TrackQuery trackQuery, final String str) {
        String loaded = null;
        boolean ok = false;
        try {
            loaded = load(trackQuery, str);
            ok = true;
        } catch (Throwable th) {
            FileLog.e(th);
        }
        final String strLoad = loaded;
        final boolean z = ok;
        AndroidUtilities.runOnUIThread(() -> ArtworkRepository.$r8$lambda$rATZQcBp0HUiV5FLq2dA_X1nlzY(z, str, strLoad));
    }

    public static /* synthetic */ void $r8$lambda$rATZQcBp0HUiV5FLq2dA_X1nlzY(boolean z, String str, String str2) {
        if (z) {
            memory.put(str, str2 != null ? str2 : "");
        }
        ArrayList<Callback> arrayListRemove = pending.remove(str);
        if (arrayListRemove != null) {
            int size = arrayListRemove.size();
            int i = 0;
            while (i < size) {
                Callback callback = arrayListRemove.get(i);
                i++;
                callback.onResult(str2);
            }
        }
    }

    private static String load(TrackQuery trackQuery, String str) throws Exception {
        File file;
        File file2;
        File fileDirectory = PlayerCache.directory("artwork");
        String strMD5 = Utilities.MD5(str + "\u00011");
        if (fileDirectory != null) {
            file = new File(fileDirectory, strMD5 + ".url");
        } else {
            file = null;
        }
        if (fileDirectory != null) {
            file2 = new File(fileDirectory, strMD5 + ".none");
        } else {
            file2 = null;
        }
        if (file != null && file.length() > 0) {
            String str2 = read(file);
            if (!TextUtils.isEmpty(str2)) {
                return str2;
            }
        }
        if (file2 != null && System.currentTimeMillis() - file2.lastModified() < 259200000) {
            return null;
        }
        String strFind = find(trackQuery);
        if (file == null || strFind == null) {
            if (file2 != null) {
                new FileOutputStream(file2).close();
            }
            return strFind;
        }
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        try {
            fileOutputStream.write(strFind.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
            return strFind;
        } catch (Throwable th) {
            try {
                fileOutputStream.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    private static String find(TrackQuery trackQuery) throws Exception {
        ArrayList<JSONObject> arrayListBestSongs = MotionCoverMatcher.bestSongs(MotionCoverRepository.searchSongs(trackQuery), trackQuery.artist(), trackQuery.title(), trackQuery.duration());
        int size = arrayListBestSongs.size();
        int i = 0;
        while (i < size) {
            JSONObject jSONObject = arrayListBestSongs.get(i);
            i++;
            JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("artwork");
            String strOptString = jSONObjectOptJSONObject != null ? jSONObjectOptJSONObject.optString("url") : "";
            if (strOptString.startsWith("https://")) {
                int iMax = Math.max(1, Math.min(1200, Math.min(jSONObjectOptJSONObject.optInt("width", 1200), jSONObjectOptJSONObject.optInt("height", 1200))));
                return strOptString.replace("{w}", String.valueOf(iMax)).replace("{h}", String.valueOf(iMax)).replace("{f}", "jpg");
            }
        }
        return null;
    }

    private static String read(File file) throws IOException {
        int iMin = (int) Math.min(file.length(), 4096L);
        byte[] bArr = new byte[iMin];
        FileInputStream fileInputStream = new FileInputStream(file);
        int i = 0;
        while (i < iMin) {
            try {
                int i2 = fileInputStream.read(bArr, i, iMin - i);
                if (i2 < 0) {
                    break;
                }
                i += i2;
            } catch (Throwable th) {
                try {
                    fileInputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        }
        fileInputStream.close();
        String strTrim = new String(bArr, 0, i, StandardCharsets.UTF_8).trim();
        if (strTrim.startsWith("https://")) {
            return strTrim;
        }
        return null;
    }
}

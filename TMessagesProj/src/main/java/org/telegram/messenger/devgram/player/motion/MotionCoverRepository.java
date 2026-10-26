package org.telegram.messenger.devgram.player.motion;

import android.text.TextUtils;
import android.util.Base64;
import org.telegram.messenger.devgram.player.PlayerCache;
import org.telegram.messenger.devgram.player.TrackQuery;
import org.telegram.messenger.devgram.player.PlayerHttp;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlin.jvm.internal.LongCompanionObject;
import kotlin.time.DurationKt;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
public abstract class MotionCoverRepository {
    private static volatile String pendingKey;
    private static String storefront;
    private static String token;
    private static long tokenExpiresAt;
    private static final Pattern SCRIPT = Pattern.compile("/assets/index(?:-legacy)?[~-][A-Za-z0-9_-]+\\.js");
    private static final Pattern JWT = Pattern.compile("ey[A-Za-z0-9_-]+\\.ey[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern RESOLUTION = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");
    private static final Pattern BANDWIDTH = Pattern.compile("[:,]BANDWIDTH=(\\d+)");
    private static final Pattern MAP_URI = Pattern.compile("URI=\"([^\"]+)\"");
    private static final Pattern MAP_RANGE = Pattern.compile("BYTERANGE=\"(\\d+)(?:@(\\d+))?\"");
    static final DispatchQueue queue = new DispatchQueue("playerMotionCovers");

    public interface Callback {
        void onResult(String str);
    }

    public static final class Reply {
        private final String body;
        private final int code;



        private Reply(int i, String str) {
            this.code = i;
            this.body = str;
        }

        public String body() {
            return this.body;
        }

        public int code() {
            return this.code;
        }


        public final int hashCode() {
            return ((this.code * 31) + Objects.hashCode(this.body));
        }

    }

    public static final class Part {
        private final long length;
        private final long offset;
        private final String url;



        private Part(String str, long j, long j2) {
            this.url = str;
            this.offset = j;
            this.length = j2;
        }


        public final int hashCode() {
            return ((((Long.hashCode(this.offset) * 31) + Long.hashCode(this.length)) * 31) + Objects.hashCode(this.url));
        }

        public long length() {
            return this.length;
        }

        public long offset() {
            return this.offset;
        }


        public String url() {
            return this.url;
        }
    }

    public static void fetch(final TrackQuery trackQuery, final int i, final Callback callback) {
        final String strKey = trackQuery.key();
        pendingKey = strKey;
        queue.postRunnable(() -> MotionCoverRepository.$r8$lambda$KIIp9LNnh9QVDfECp45XbwValMs(strKey, trackQuery, i, callback));
    }

    public static /* synthetic */ void $r8$lambda$KIIp9LNnh9QVDfECp45XbwValMs(String str, TrackQuery trackQuery, int i, final Callback callback) {
        String loaded = null;
        if (str.equals(pendingKey)) {
            try {
                loaded = load(trackQuery, str, i);
            } catch (Throwable th) {
                FileLog.e(th);
            }
        }
        final String strLoad = loaded;
        AndroidUtilities.runOnUIThread(() -> callback.onResult(strLoad));
    }

    private static String load(TrackQuery trackQuery, String str, int i) throws Exception {
        File fileDirectory = PlayerCache.directory("motion_covers");
        if (fileDirectory == null) {
            return null;
        }
        String strMD5 = Utilities.MD5(str + "\u00014");
        File file = new File(fileDirectory, strMD5 + ".mp4");
        if (file.length() > 0) {
            if (!file.setLastModified(System.currentTimeMillis())) {
                FileLog.d("motion covers: can't touch " + file);
            }
            return file.getAbsolutePath();
        }
        File file2 = new File(fileDirectory, strMD5 + ".none");
        if (System.currentTimeMillis() - file2.lastModified() < 259200000) {
            return null;
        }
        String strFindVideo = findVideo(trackQuery);
        if (strFindVideo == null) {
            new FileOutputStream(file2).close();
        } else {
            download(strFindVideo, file, i);
        }
        trim(fileDirectory);
        if (strFindVideo != null) {
            return file.getAbsolutePath();
        }
        return null;
    }

    public static JSONArray searchSongs(TrackQuery trackQuery) throws Exception {
        final String strTitle;
        if (TextUtils.isEmpty(trackQuery.artist())) {
            strTitle = trackQuery.title();
        } else {
            strTitle = trackQuery.artist() + " " + trackQuery.title();
        }
        JSONObject jSONObjectOptJSONObject = catalog("search", obj -> ((HttpUrl.Builder) obj).addQueryParameter("types", "songs").addQueryParameter("limit", String.valueOf(10)).addQueryParameter("term", strTitle)).optJSONObject("results");
        JSONObject jSONObjectOptJSONObject2 = jSONObjectOptJSONObject != null ? jSONObjectOptJSONObject.optJSONObject("songs") : null;
        if (jSONObjectOptJSONObject2 != null) {
            return jSONObjectOptJSONObject2.optJSONArray("data");
        }
        return null;
    }

    private static String findVideo(TrackQuery trackQuery) throws Exception {
        ArrayList<JSONObject> arrayListBestSongs = MotionCoverMatcher.bestSongs(searchSongs(trackQuery), trackQuery.artist(), trackQuery.title(), trackQuery.duration());
        int size = arrayListBestSongs.size();
        int i = 0;
        while (i < size) {
            JSONObject jSONObject = arrayListBestSongs.get(i);
            i++;
            JSONObject jSONObject2 = jSONObject;
            final String strOptString = jSONObject2.optString("isrc");
            if (!strOptString.isEmpty()) {
                String strPickVideo = MotionCoverMatcher.pickVideo(catalog("songs", obj -> ((HttpUrl.Builder) obj).addQueryParameter("include", "albums").addQueryParameter("extend[albums]", "editorialVideo").addQueryParameter("filter[isrc]", strOptString)).optJSONArray("data"), trackQuery.album(), jSONObject2.optString("albumName"), TextUtils.isEmpty(trackQuery.artist()) ? jSONObject2.optString("artistName") : trackQuery.artist());
                if (strPickVideo != null) {
                    return strPickVideo;
                }
            }
        }
        return null;
    }

    private static JSONObject catalog(String str, Utilities.Callback<HttpUrl.Builder> callback) throws Exception {
        if (storefront == null) {
            String lowerCase = Locale.getDefault().getCountry().toLowerCase(Locale.ROOT);
            if (lowerCase.length() != 2) {
                lowerCase = "us";
            }
            storefront = lowerCase;
        }
        Reply replyCatalogGet = catalogGet(str, callback, token(false));
        if (replyCatalogGet.code() == 401) {
            replyCatalogGet = catalogGet(str, callback, token(true));
        }
        if (replyCatalogGet.code() == 400 && !"us".equals(storefront)) {
            storefront = "us";
            replyCatalogGet = catalogGet(str, callback, token(false));
        }
        if (replyCatalogGet.code() < 200 || replyCatalogGet.code() >= 300) {
            throw new IllegalStateException(("apple music http ") + (replyCatalogGet.code()));
        }
        return new JSONObject(replyCatalogGet.body());
    }

    private static Reply catalogGet(String str, Utilities.Callback<HttpUrl.Builder> callback, String str2) throws Exception {
        HttpUrl.Builder builderNewBuilder = HttpUrl.get("https://amp-api.music.apple.com/v1/catalog/" + storefront + "/" + str).newBuilder();
        callback.run(builderNewBuilder);
        Response responseExecute = client().newCall(new Request.Builder().url(builderNewBuilder.build()).header("Authorization", "Bearer " + str2).header("Origin", "https://music.apple.com").header("Referer", "https://music.apple.com/").header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36").header("Accept", "application/json").build()).execute();
        try {
            Reply reply = new Reply(responseExecute.code(), responseExecute.body().string());
            responseExecute.close();
            return reply;
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

    private static String token(boolean z) throws Exception {
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (!z) {
            if (token == null) {
                readToken();
            }
            String str = token;
            if (str != null && jCurrentTimeMillis < tokenExpiresAt - DurationKt.MILLIS_IN_HOUR) {
                return str;
            }
        }
        HashSet hashSet = new HashSet();
        Matcher matcher = SCRIPT.matcher(getText("https://music.apple.com/us/browse"));
        while (matcher.find()) {
            if (hashSet.add(matcher.group())) {
                Matcher matcher2 = JWT.matcher(getText("https://music.apple.com" + matcher.group()));
                while (matcher2.find()) {
                    long jWebPlayerTokenExpiry = webPlayerTokenExpiry(matcher2.group());
                    if (jWebPlayerTokenExpiry > jCurrentTimeMillis) {
                        token = matcher2.group();
                        tokenExpiresAt = jWebPlayerTokenExpiry;
                        writeToken();
                        return token;
                    }
                }
            }
        }
        throw new IllegalStateException(("apple music token not found"));
    }

    private static long webPlayerTokenExpiry(String str) {
        try {
            String[] strArrSplit = str.split("\\.");
            byte[] bArrDecode = Base64.decode(strArrSplit[0], 8);
            Charset charset = StandardCharsets.UTF_8;
            JSONObject jSONObject = new JSONObject(new String(bArrDecode, charset));
            JSONObject jSONObject2 = new JSONObject(new String(Base64.decode(strArrSplit[1], 8), charset));
            if ("WebPlayKid".equals(jSONObject.optString("kid")) || "AMPWebPlay".equals(jSONObject2.optString("iss"))) {
                return jSONObject2.optLong("exp") * 1000;
            }
            return 0L;
        } catch (Exception unused) {
            return 0L;
        }
    }

    private static void readToken() {
        File fileDirectory = PlayerCache.directory("motion_covers");
        String res = fileDirectory != null ? AndroidUtilities.readRes(new File(fileDirectory, "token")) : null;
        if (res == null) {
            return;
        }
        String[] strArrSplit = res.trim().split("\n");
        if (strArrSplit.length == 2) {
            try {
                tokenExpiresAt = Long.parseLong(strArrSplit[1].trim());
                token = strArrSplit[0].trim();
            } catch (NumberFormatException e) {
                FileLog.e(e);
            }
        }
    }

    private static void writeToken() {
        File fileDirectory = PlayerCache.directory("motion_covers");
        if (fileDirectory == null) {
            return;
        }
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(fileDirectory, "token"));
            try {
                fileOutputStream.write((token + "\n" + tokenExpiresAt).getBytes(StandardCharsets.UTF_8));
                fileOutputStream.close();
            } catch (Throwable th) {
                try {
                    fileOutputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void download(String str, File file, int i) throws Exception {
        String strPickVariant = pickVariant(str, getText(str), i);
        if (strPickVariant == null) {
            throw new IllegalStateException(("no h264 variant in ") + str);
        }
        ArrayList<Part> parts = parseParts(strPickVariant, getText(strPickVariant));
        if (parts.isEmpty()) {
            throw new IllegalStateException(("empty playlist ".concat(strPickVariant)));
        }
        File file2 = new File(file.getPath() + ".tmp");
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            try {
                long jCopy = 0;
                if (isSingleFile(parts)) {
                    Part part = parts.get(parts.size() - 1);
                    copy(new Part(part.url(), 0L, part.length() + part.offset()), fileOutputStream, 0L);
                } else {
                    int size = parts.size();
                    int i2 = 0;
                    while (i2 < size) {
                        Part part2 = parts.get(i2);
                        i2++;
                        jCopy = copy(part2, fileOutputStream, jCopy);
                    }
                }
                fileOutputStream.close();
                if (file2.renameTo(file)) {
                    return;
                }
                throw new IllegalStateException("can't move " + file2);
            } catch (Throwable th) {
                try {
                    fileOutputStream.close();
                    throw th;
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                    throw th;
                }
            }
        } catch (Exception e) {
            delete(file2);
            throw e;
        }
    }

    private static String pickVariant(String str, String str2, int i) {
        int i2 = (int) (i * 0.9f);
        String[] strArrSplit = str2.split("\n");
        long j = 0;
        String str3 = null;
        int i3 = 0;
        int i4 = 0;
        while (true) {
            if (i3 >= strArrSplit.length - 1) {
                break;
            }
            String strTrim = strArrSplit[i3].trim();
            i3++;
            String strTrim2 = strArrSplit[i3].trim();
            if (strTrim.startsWith("#EXT-X-STREAM-INF:") && strTrim.contains("avc1") && !strTrim.contains("VIDEO-RANGE=PQ") && !strTrim.contains("VIDEO-RANGE=HLG") && !strTrim2.isEmpty() && !strTrim2.startsWith("#")) {
                Matcher matcher = RESOLUTION.matcher(strTrim);
                Matcher matcher2 = BANDWIDTH.matcher(strTrim);
                if (matcher.find()) {
                    int iMax = Math.max(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
                    long j2 = matcher2.find() ? Long.parseLong(matcher2.group(1)) : LongCompanionObject.MAX_VALUE;
                    boolean z = iMax >= i2;
                    if (str3 != null) {
                        if (z == (i4 >= i2)) {
                            if (iMax != i4) {
                                if (z) {
                                    if (iMax < i4) {
                                        str3 = strTrim2;
                                        i4 = iMax;
                                        j = j2;
                                    }
                                } else if (iMax > i4) {
                                    str3 = strTrim2;
                                    i4 = iMax;
                                    j = j2;
                                }
                            } else if (j2 < j) {
                                str3 = strTrim2;
                                i4 = iMax;
                                j = j2;
                            }
                        }
                    }
                    if (str3 == null || z) {
                        str3 = strTrim2;
                        i4 = iMax;
                        j = j2;
                    }
                }
            }
        }
        if (str3 != null) {
            return resolve(str, str3);
        }
        return null;
    }

    private static ArrayList<Part> parseParts(String str, String str2) {
        String strResolve;
        ArrayList<Part> arrayList = new ArrayList<>();
        String str3 = null;
        long j = -1;
        long j2 = -1;
        long j3 = 0;
        for (String str4 : str2.split("\n")) {
            String strTrim = str4.trim();
            if (strTrim.startsWith("#EXT-X-MAP:")) {
                Matcher matcher = MAP_URI.matcher(strTrim);
                if (matcher.find()) {
                    strResolve = resolve(str, matcher.group(1));
                    Matcher matcher2 = MAP_RANGE.matcher(strTrim);
                    if (matcher2.find()) {
                        long j4 = Long.parseLong(matcher2.group(1));
                        long j5 = matcher2.group(2) != null ? Long.parseLong(matcher2.group(2)) : 0L;
                        arrayList.add(new Part(strResolve, j5, j4));
                        j3 = j5 + j4;
                    } else {
                        arrayList.add(new Part(strResolve, 0L, -1L));
                        j3 = 0;
                    }
                    str3 = strResolve;
                }
            } else if (strTrim.startsWith("#EXT-X-BYTERANGE:")) {
                String strSubstring = strTrim.substring(17);
                int iIndexOf = strSubstring.indexOf(64);
                j2 = Long.parseLong(iIndexOf >= 0 ? strSubstring.substring(0, iIndexOf) : strSubstring);
                j = iIndexOf >= 0 ? Long.parseLong(strSubstring.substring(iIndexOf + 1)) : -1L;
            } else if (!strTrim.isEmpty() && !strTrim.startsWith("#")) {
                String strResolve2 = resolve(str, strTrim);
                if (j2 >= 0) {
                    if (j >= 0) {
                        j3 = j;
                    } else if (!strResolve2.equals(str3)) {
                        j3 = 0;
                    }
                    strResolve = strResolve2;
                    arrayList.add(new Part(strResolve2, j3, j2));
                    j3 += j2;
                } else {
                    strResolve = strResolve2;
                    arrayList.add(new Part(strResolve, 0L, -1L));
                    j3 = 0;
                }
                j = -1;
                j2 = -1;
                str3 = strResolve;
            }
        }
        return arrayList;
    }

    private static boolean isSingleFile(ArrayList<Part> arrayList) {
        String strUrl = arrayList.get(0).url();
        int size = arrayList.size();
        int i = 0;
        long length = 0;
        while (i < size) {
            Part part = arrayList.get(i);
            i++;
            Part part2 = part;
            if (!part2.url().equals(strUrl) || part2.length() < 0 || part2.offset() != length) {
                return false;
            }
            length += part2.length();
        }
        return true;
    }

    private static long copy(Part part, OutputStream outputStream, long j) throws java.io.IOException {
        Request.Builder builder = new Request.Builder().url(part.url()).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36");
        if (part.length() >= 0) {
            builder.header("Range", "bytes=" + part.offset() + "-" + ((part.offset() + part.length()) - 1));
        }
        try (Response response = client().newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null || (part.offset() > 0 && response.code() != 206)) {
                throw new IllegalStateException("motion cover http " + response.code());
            }
            try (InputStream in = body.byteStream()) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    j += n;
                    if (j > 50331648) {
                        throw new IllegalStateException("motion cover is too large");
                    }
                    outputStream.write(buffer, 0, n);
                }
            }
        }
        return j;
    }

    private static String getText(String str) throws Exception {
        Response responseExecute = client().newCall(new Request.Builder().url(str).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36").build()).execute();
        try {
            ResponseBody responseBodyBody = responseExecute.body();
            if (!responseExecute.isSuccessful()) {
                throw new IllegalStateException("http " + responseExecute.code() + " for " + str);
            }
            String strString = responseBodyBody.string();
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

    private static OkHttpClient client() {
        return PlayerHttp.client();
    }

    private static String resolve(String str, String str2) {
        return URI.create(str).resolve(str2).toString();
    }

    private static void trim(File file) {
        long jCurrentTimeMillis = System.currentTimeMillis();
        File[] fileArrListFiles = file.listFiles((file2, str) -> str.endsWith(".none"));
        if (fileArrListFiles != null) {
            for (File file2 : fileArrListFiles) {
                if (jCurrentTimeMillis - file2.lastModified() >= 259200000) {
                    delete(file2);
                }
            }
        }
        File[] fileArrListFiles2 = file.listFiles((file3, str) -> str.endsWith(".mp4"));
        if (fileArrListFiles2 == null) {
            return;
        }
        long length = 0;
        for (File file3 : fileArrListFiles2) {
            length += file3.length();
        }
        Arrays.sort(fileArrListFiles2, Comparator.comparingLong(obj -> ((File) obj).lastModified()));
        for (int i = 0; i < fileArrListFiles2.length - 1 && length > 209715200; i++) {
            length -= fileArrListFiles2[i].length();
            delete(fileArrListFiles2[i]);
        }
    }

    private static void delete(File file) {
        if (!file.exists() || file.delete()) {
            return;
        }
        FileLog.d("motion covers: can't delete " + file);
    }
}

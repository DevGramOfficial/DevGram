package org.telegram.messenger;

import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;

/** Public immutable font assets; local drafts use the exact same content hash as saved designs. */
public final class DevGramProfileFonts {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final LinkedHashMap<String, Typeface> faces = new LinkedHashMap<>(24, .75f, true);
    private static final HashSet<String> loading = new HashSet<>();
    private static final HashMap<String, Long> retryAt = new HashMap<>();
    private static int generation;
    private DevGramProfileFonts() {}
    private static File directory() { return new File(ApplicationLoader.applicationContext.getCacheDir(), "devgram-profile-fonts"); }
    private static String hash(JSONObject metadata) {
        String value = metadata == null ? "" : metadata.optString("sha256");
        return value.matches("[a-f0-9]{64}") ? value : "";
    }
    public static synchronized Typeface get(JSONObject metadata) {
        String digest = hash(metadata);
        if (digest.isEmpty() || !DevGramProfileCustomization.enabled()) return null;
        Typeface face = faces.get(digest);
        if (face != null) return face;
        if (loading.contains(digest) || retryAt.getOrDefault(digest, 0L) > System.currentTimeMillis()) return null;
        loading.add(digest);
        int requestGeneration = generation;
        DevGramNetworkQueue.postRunnable(() -> load(digest, requestGeneration));
        return null;
    }
    private static void load(String digest, int requestGeneration) {
        Typeface result = null;
        File target = new File(directory(), digest + ".ttf");
        try {
            if (!target.isFile()) {
                HttpURLConnection connection = (HttpURLConnection) new URL(DevGramProfilePolicy.BASE + "studio/fonts/" + digest + ".ttf").openConnection();
                try {
                    connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(12000); connection.setReadTimeout(20000);
                    if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > MAX_BYTES) throw new IOException();
                    byte[] bytes;
                    try (InputStream input = connection.getInputStream()) { bytes = read(input); }
                    if (!sha256(bytes).equals(digest)) throw new IOException();
                    synchronized (DevGramProfileFonts.class) {
                        if (generation != requestGeneration || !DevGramProfileCustomization.enabled()) {
                            if (generation == requestGeneration) loading.remove(digest);
                            return;
                        }
                        store(digest, bytes);
                    }
                } finally { connection.disconnect(); }
            }
            if (target.length() > MAX_BYTES) throw new IOException();
            result = Typeface.createFromFile(target);
        } catch (Exception ignored) { target.delete(); }
        final Typeface face = result;
        AndroidUtilities.runOnUIThread(() -> {
            synchronized (DevGramProfileFonts.class) {
                if (generation != requestGeneration) return;
                loading.remove(digest);
                if (face == null) retryAt.put(digest, System.currentTimeMillis() + 60000);
                else { remember(digest, face); retryAt.remove(digest); }
            }
            if (face != null) DevGramProfilePolicy.changed();
        });
    }
    private static void remember(String digest, Typeface face) {
        faces.put(digest, face);
        while (faces.size() > 24) faces.remove(faces.keySet().iterator().next());
    }
    private static byte[] read(InputStream input) throws IOException {
        if (input == null) throw new IOException();
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); if (output.size() > MAX_BYTES) throw new IOException(); }
        byte[] bytes = output.toByteArray();
        if (bytes.length < 12 || !((bytes[0] == 0 && bytes[1] == 1 && bytes[2] == 0 && bytes[3] == 0) || (bytes[0] == 'O' && bytes[1] == 'T' && bytes[2] == 'T' && bytes[3] == 'O'))) throw new IOException();
        return bytes;
    }
    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder(64);
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format(java.util.Locale.US, "%02x", value & 255));
        return result.toString();
    }
    private static void store(String digest, byte[] bytes) throws IOException {
        File folder = directory(); if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException();
        File temporary = new File(folder, digest + ".tmp"), target = new File(folder, digest + ".ttf");
        try (FileOutputStream output = new FileOutputStream(temporary)) { output.write(bytes); }
        if (!temporary.renameTo(target)) { temporary.delete(); throw new IOException(); }
        File[] files = folder.listFiles((dir, name) -> name.matches("[a-f0-9]{64}\\.ttf"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified)); long total = 0;
        for (File file : files) total += file.length();
        for (File file : files) { if (total <= 40L * 1024 * 1024) break; if (!file.equals(target)) { long size = file.length(); if (file.delete()) total -= size; } }
    }
    /** Selection uploads only a font asset. Publishing the draft remains a separate explicit save. */
    public static void upload(int account, Uri uri, String field, DevGramProfilePolicy.Callback callback) {
        final long uid = UserConfig.getInstance(account).getClientUserId();
        final String feature = field.equals("nameFontFile") ? "profile.font" : "chat.font";
        if (!DevGramProfilePolicy.allowed(uid, feature)) { callback.run(null, "feature_locked"); return; }
        DevGramNetworkQueue.postRunnable(() -> {
            JSONObject fields = null; String digest = ""; Typeface face = null;
            try {
                byte[] bytes;
                try (InputStream input = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) { bytes = read(input); }
                String name = "Font";
                try (android.database.Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0); }
                if (name == null || name.isEmpty()) name = "Font";
                digest = sha256(bytes); store(digest, bytes);
                face = Typeface.createFromFile(new File(directory(), digest + ".ttf"));
                fields = new JSONObject().put("field", field).put("name", name.substring(0, Math.min(80, name.length()))).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
            } catch (Exception ignored) {}
            final JSONObject data = fields; final String hash = digest; final Typeface selected = face;
            AndroidUtilities.runOnUIThread(() -> {
                if (data == null || selected == null) { callback.run(null, "bad_font"); return; }
                if (UserConfig.getInstance(account).getClientUserId() != uid || !DevGramProfilePolicy.allowed(uid, feature)) { callback.run(null, "feature_locked"); return; }
                DevGramProfilePolicy.request(account, "studio/fonts/upload", data, (result, error) -> {
                    if (result != null && DevGramProfilePolicy.allowed(uid, feature)) synchronized (DevGramProfileFonts.class) { remember(hash, selected); }
                    callback.run(result, error);
                });
            });
        });
    }
    public static synchronized void clear() {
        generation++; faces.clear(); loading.clear(); retryAt.clear();
        DevGramNetworkQueue.postRunnable(() -> { File[] files = directory().listFiles(); if (files != null) for (File file : files) file.delete(); });
    }
    public static long cacheBytes() { long size = 0; File[] files = directory().listFiles(); if (files != null) for (File file : files) size += file.length(); return size; }
}

package org.telegram.messenger;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Серверный запрет установки плагинов. Список хранится в SQLite на api.devgram.space. */
public final class DevGramPluginBlocks {

    private static final String API = "https://api.devgram.space/api/plugins";

    private DevGramPluginBlocks() { }

    public static final class Status {
        public final boolean success;
        public final boolean blocked;
        public final String reason;

        private Status(boolean success, boolean blocked, String reason) {
            this.success = success;
            this.blocked = blocked;
            this.reason = reason == null ? "" : reason;
        }
    }

    public interface Callback {
        void onResult(Status status);
    }

    /** Необратимый отпечаток структуры плагина. Исходный код на сервер не отправляется. */
    public static final class Fingerprint {
        public static final Fingerprint EMPTY = new Fingerprint("", "", 0);
        public final String structure;
        public final String simhash;
        public final int featureCount;

        private Fingerprint(String structure, String simhash, int featureCount) {
            this.structure = structure == null ? "" : structure;
            this.simhash = simhash == null ? "" : simhash;
            this.featureCount = Math.max(0, featureCount);
        }
    }

    public static boolean canManage(int account) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        return DevGramBadges.hasDeveloperFeatures(uid);
    }

    public static void check(String pluginId, String sha256, Callback callback) {
        check(pluginId, sha256, Fingerprint.EMPTY, callback);
    }

    public static void check(String pluginId, String sha256, Fingerprint fingerprint, Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            Status result = checkBlocking(pluginId, sha256, fingerprint);
            AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
        });
    }

    /** Только для уже фоновых потоков, например HTTP-потока Dev Server. */
    public static Status checkBlocking(String pluginId, String sha256) {
        return checkBlocking(pluginId, sha256, Fingerprint.EMPTY);
    }

    public static Status checkBlocking(String pluginId, String sha256, Fingerprint fingerprint) {
        HttpURLConnection connection = null;
        try {
            Fingerprint fp = fingerprint == null ? Fingerprint.EMPTY : fingerprint;
            String query = "?id=" + encode(pluginId) + "&sha256=" + encode(sha256)
                    + "&structure=" + encode(fp.structure) + "&simhash=" + encode(fp.simhash)
                    + "&featureCount=" + fp.featureCount;
            connection = (HttpURLConnection) new URL(API + "/block-status" + query).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return new Status(false, false, "Сервер вернул HTTP " + code);
            }
            JSONObject object = new JSONObject(read(connection.getInputStream()));
            return new Status(true, object.optBoolean("blocked", false), object.optString("reason", ""));
        } catch (Throwable error) {
            FileLog.e(error);
            return new Status(false, false, "Не удалось проверить блокировку плагина");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static void setBlocked(int account, String pluginId, String sha256, String name,
                                  boolean blocked, Callback callback) {
        setBlocked(account, pluginId, sha256, name, Fingerprint.EMPTY, blocked, callback);
    }

    public static void setBlocked(int account, String pluginId, String sha256, String name,
                                  Fingerprint fingerprint, boolean blocked, Callback callback) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        if (!DevGramBadges.hasDeveloperFeatures(uid)) {
            callback.onResult(new Status(false, false, "Нет прав разработчика"));
            return;
        }
        Runnable send = () -> sendChange(account, uid, pluginId, sha256, name, fingerprint, blocked, callback);
        if (DevGramConfig.getBetaToken().isEmpty()) {
            DevGramBeta.activate(account, (ok, error) -> {
                if (ok) send.run();
                else callback.onResult(new Status(false, false,
                        error == null ? "Не удалось получить токен разработчика" : error));
            });
        } else {
            send.run();
        }
    }

    private static void sendChange(int account, long uid, String pluginId, String sha256, String name,
                                   Fingerprint fingerprint, boolean blocked, Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            Status result = sendChangeBlocking(uid, DevGramConfig.getBetaToken(), pluginId, sha256,
                    name, fingerprint, blocked);
            AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
        });
    }

    private static Status sendChangeBlocking(long uid, String token, String pluginId, String sha256,
                                             String name, Fingerprint fingerprint, boolean blocked) {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject();
            body.put("uid", uid);
            body.put("token", token == null ? "" : token);
            body.put("pluginId", clean(pluginId));
            body.put("sha256", clean(sha256));
            body.put("name", name == null ? "" : name);
            Fingerprint fp = fingerprint == null ? Fingerprint.EMPTY : fingerprint;
            body.put("structure", fp.structure);
            body.put("simhash", fp.simhash);
            body.put("featureCount", fp.featureCount);
            body.put("reason", blocked ? "Заблокировано разработчиком DevGram" : "");
            body.put("blocked", blocked);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);

            connection = (HttpURLConnection) new URL(API + "/block").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "" : read(stream);
            if (code < 200 || code >= 300) {
                return new Status(false, false,
                        code == 403 ? "Сервер не подтвердил права разработчика" : "Сервер вернул HTTP " + code);
            }
            JSONObject object = new JSONObject(response);
            return new Status(object.optBoolean("ok", true), object.optBoolean("blocked", blocked),
                    object.optString("reason", ""));
        } catch (Throwable error) {
            FileLog.e(error);
            return new Status(false, false, "Не удалось изменить блокировку плагина");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(clean(value), "UTF-8");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static Fingerprint fingerprintSource(String source) {
        if (source == null || source.trim().isEmpty()) return Fingerprint.EMPTY;
        try {
            ArrayList<String> structural = new ArrayList<>();
            ArrayList<String> fuzzy = new ArrayList<>();
            tokenize(source, structural, fuzzy);
            return makeFingerprint(structural, fuzzy);
        } catch (Throwable error) {
            FileLog.e(error);
            return Fingerprint.EMPTY;
        }
    }

    /** Отпечаток всех открытых Python-исходников внутри .dgplugin в стабильном порядке. */
    public static Fingerprint fingerprintPackage(String path) {
        if (path == null || path.isEmpty()) return Fingerprint.EMPTY;
        try (ZipFile zip = new ZipFile(path)) {
            ArrayList<String> names = new ArrayList<>();
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.US).endsWith(".py")) {
                    names.add(entry.getName());
                }
            }
            Collections.sort(names);
            ArrayList<String> structural = new ArrayList<>();
            ArrayList<String> fuzzy = new ArrayList<>();
            for (String name : names) {
                structural.add("file:" + name.toLowerCase(Locale.US));
                ZipEntry entry = zip.getEntry(name);
                try (InputStream input = zip.getInputStream(entry)) {
                    tokenize(new String(readBytes(input), StandardCharsets.UTF_8), structural, fuzzy);
                }
            }
            return makeFingerprint(structural, fuzzy);
        } catch (Throwable error) {
            FileLog.e(error);
            return Fingerprint.EMPTY;
        }
    }

    private static Fingerprint makeFingerprint(ArrayList<String> structural, ArrayList<String> fuzzy) throws Exception {
        if (structural.isEmpty() || fuzzy.isEmpty()) return Fingerprint.EMPTY;
        String structure = sha256(android.text.TextUtils.join("\u001f", structural));
        ArrayList<String> features = new ArrayList<>();
        int width = fuzzy.size() < 4 ? 1 : 4;
        for (int i = 0; i + width <= fuzzy.size(); i++) {
            StringBuilder feature = new StringBuilder();
            for (int j = 0; j < width; j++) feature.append('\u001f').append(fuzzy.get(i + j));
            features.add(feature.toString());
        }
        if (features.isEmpty()) return new Fingerprint(structure, "", 0);
        int[] weights = new int[64];
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String feature : features) {
            byte[] hash = digest.digest(feature.getBytes(StandardCharsets.UTF_8));
            for (int bit = 0; bit < 64; bit++) {
                boolean one = (hash[bit >>> 3] & (1 << (7 - (bit & 7)))) != 0;
                weights[bit] += one ? 1 : -1;
            }
        }
        long value = 0;
        for (int bit = 0; bit < 64; bit++) if (weights[bit] >= 0) value |= 1L << (63 - bit);
        return new Fingerprint(structure, String.format(Locale.US, "%016x", value), features.size());
    }

    private static final Set<String> PYTHON_KEYWORDS = new HashSet<>(java.util.Arrays.asList(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
            "elif", "else", "except", "false", "finally", "for", "from", "global", "if", "import",
            "in", "is", "lambda", "none", "nonlocal", "not", "or", "pass", "raise", "return",
            "true", "try", "while", "with", "yield"));
    private static final Set<String> TWO_CHAR_OPERATORS = new HashSet<>(java.util.Arrays.asList(
            "==", "!=", "<=", ">=", ":=", "->", "**", "//", "<<", ">>", "+=", "-=", "*=",
            "/=", "%=", "&=", "|=", "^=", "@="));

    private static void tokenize(String source, ArrayList<String> structural, ArrayList<String> fuzzy) {
        int length = source.length();
        for (int i = 0; i < length; ) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '#') {
                while (i < length && source.charAt(i) != '\n' && source.charAt(i) != '\r') i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                boolean triple = i + 2 < length && source.charAt(i + 1) == quote && source.charAt(i + 2) == quote;
                i += triple ? 3 : 1;
                while (i < length) {
                    if (source.charAt(i) == '\\') { i = Math.min(length, i + 2); continue; }
                    if (triple && i + 2 < length && source.charAt(i) == quote
                            && source.charAt(i + 1) == quote && source.charAt(i + 2) == quote) { i += 3; break; }
                    if (!triple && source.charAt(i) == quote) { i++; break; }
                    i++;
                }
                structural.add("s"); fuzzy.add("s");
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < length && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) i++;
                String word = source.substring(start, i).toLowerCase(Locale.US);
                structural.add("i:" + word);
                fuzzy.add(PYTHON_KEYWORDS.contains(word) ? "k:" + word : "n");
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < length && Character.isDigit(source.charAt(i + 1)))) {
                i++;
                while (i < length) {
                    char n = source.charAt(i);
                    if (!(Character.isLetterOrDigit(n) || n == '.' || n == '_')) break;
                    i++;
                }
                structural.add("#"); fuzzy.add("#");
                continue;
            }
            String op = String.valueOf(c);
            if (i + 1 < length) {
                String two = source.substring(i, i + 2);
                if (TWO_CHAR_OPERATORS.contains(two)) {
                    op = two; i += 2;
                } else i++;
            } else i++;
            structural.add("o:" + op); fuzzy.add("o:" + op);
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest) result.append(Character.forDigit((b >>> 4) & 15, 16))
                .append(Character.forDigit(b & 15, 16));
        return result.toString();
    }

    private static byte[] readBytes(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) if (read > 0) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private static String read(InputStream input) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}

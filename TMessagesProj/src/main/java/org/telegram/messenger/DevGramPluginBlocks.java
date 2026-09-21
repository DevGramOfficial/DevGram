package org.telegram.messenger;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    public static boolean canManage(int account) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        return DevGramBadges.hasDeveloperFeatures(uid);
    }

    public static void check(String pluginId, String sha256, Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            Status result = checkBlocking(pluginId, sha256);
            AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
        });
    }

    /** Только для уже фоновых потоков, например HTTP-потока Dev Server. */
    public static Status checkBlocking(String pluginId, String sha256) {
        HttpURLConnection connection = null;
        try {
            String query = "?id=" + encode(pluginId) + "&sha256=" + encode(sha256);
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
        long uid = UserConfig.getInstance(account).getClientUserId();
        if (!DevGramBadges.hasDeveloperFeatures(uid)) {
            callback.onResult(new Status(false, false, "Нет прав разработчика"));
            return;
        }
        Runnable send = () -> sendChange(account, uid, pluginId, sha256, name, blocked, callback);
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
                                   boolean blocked, Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            Status result = sendChangeBlocking(uid, DevGramConfig.getBetaToken(), pluginId, sha256, name, blocked);
            AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
        });
    }

    private static Status sendChangeBlocking(long uid, String token, String pluginId, String sha256,
                                             String name, boolean blocked) {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject();
            body.put("uid", uid);
            body.put("token", token == null ? "" : token);
            body.put("pluginId", clean(pluginId));
            body.put("sha256", clean(sha256));
            body.put("name", name == null ? "" : name);
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

    private static String read(InputStream input) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}

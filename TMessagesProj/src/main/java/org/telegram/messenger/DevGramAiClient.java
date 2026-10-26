package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small OpenAI-compatible client used by DevGram AI chat. */
public final class DevGramAiClient {
    public interface Callback { void done(String result, Throwable error); }

    /** Потоковый колбэк: onDelta — кусочки ответа по мере генерации, onDone — финал/ошибка. */
    public interface StreamCallback {
        void onDelta(String delta);
        void onDone(String full, Throwable error);
    }

    private DevGramAiClient() {}

    public static boolean isConfigured() {
        return DevGramGeminiNano.isSelected()
                || !MessagesController.getGlobalMainSettings().getString("dg_aiKey", "").trim().isEmpty();
    }

    /** Одиночный запрос (без стрима) — редактор, пересказ, «Сгенерировать ответ». */
    public static void generate(String prompt, Callback callback) {
        generate(prompt, false, callback);
    }

    /** Isolated text-tool request which doesn't inherit the user's chat role or history. */
    public static void generateTool(String prompt, String systemPrompt, Callback callback) {
        JSONArray messages = new JSONArray();
        try {
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
        } catch (Throwable ignore) {}
        request(messages, false, new StreamCallback() {
            @Override public void onDelta(String delta) {}
            @Override public void onDone(String full, Throwable error) { callback.done(full, error); }
        }, systemPrompt == null ? "" : systemPrompt);
    }

    /**
     * Одиночный запрос с опциональной общей историей AI Chat — как у exteraGram.
     * При успешной генерации новая пара user/assistant сохраняется в dg_aiHistory.
     */
    public static void generate(String prompt, boolean useHistory, Callback callback) {
        generatePrompt(prompt, useHistory, true, callback);
    }

    /** Generates with optional context but leaves history untouched until the UI accepts the result. */
    public static void generatePreview(String prompt, boolean useHistory, Callback callback) {
        generatePrompt(prompt, useHistory, false, callback);
    }

    /** Preview variant used by the response sheet so partial text can be animated while it arrives. */
    public static void generatePreview(String prompt, boolean useHistory, boolean stream, StreamCallback callback) {
        JSONArray messages = buildPromptConversation(prompt, useHistory);
        request(messages, stream, callback);
    }

    private static void generatePrompt(String prompt, boolean useHistory, boolean saveResult, Callback callback) {
        JSONArray messages = buildPromptConversation(prompt, useHistory);
        final JSONArray conversation = messages;
        request(messages, false, new StreamCallback() {
            @Override public void onDelta(String delta) {}
            @Override public void onDone(String full, Throwable error) {
                if (saveResult && useHistory && error == null && full != null && !full.isEmpty()) {
                    try {
                        conversation.put(new JSONObject().put("role", "assistant").put("content", full));
                        MessagesController.getGlobalMainSettings().edit()
                                .putString("dg_aiHistory", trimConversationHistory(conversation).toString())
                                .apply();
                    } catch (Throwable ignore) {}
                }
                callback.done(full, error);
            }
        });
    }

    private static JSONArray buildPromptConversation(String prompt, boolean useHistory) {
        JSONArray messages = new JSONArray();
        try {
            if (useHistory) {
                String saved = MessagesController.getGlobalMainSettings().getString("dg_aiHistory", "");
                if (!saved.isEmpty()) {
                    JSONArray history = trimConversationHistory(new JSONArray(saved));
                    for (int i = 0; i < history.length(); i++) {
                        JSONObject message = history.optJSONObject(i);
                        if (message != null) {
                            messages.put(copyMessage(message));
                        }
                    }
                }
            }
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
        } catch (Throwable ignore) {}
        return messages;
    }

    public static void saveTurn(String prompt, String response) {
        if (!MessagesController.getGlobalMainSettings().getBoolean("dg_aiSaveHistory", true)
                || prompt == null || response == null) return;
        try {
            JSONArray conversation;
            String saved = MessagesController.getGlobalMainSettings().getString("dg_aiHistory", "");
            conversation = saved.isEmpty() ? new JSONArray() : new JSONArray(saved);
            conversation.put(new JSONObject().put("role", "user").put("content", prompt));
            conversation.put(new JSONObject().put("role", "assistant").put("content", response));
            MessagesController.getGlobalMainSettings().edit()
                    .putString("dg_aiHistory", trimConversationHistory(conversation).toString()).apply();
        } catch (Throwable ignore) {}
    }

    private static JSONObject copyMessage(JSONObject source) throws Exception {
        return new JSONObject()
                .put("role", source.optString("role"))
                .put("content", source.optString("content"));
    }

    /** Same limits as exteraGram: at most 32 messages and roughly 24k characters. */
    private static JSONArray trimConversationHistory(JSONArray source) {
        JSONArray reversed = new JSONArray();
        int characters = 0;
        for (int i = source.length() - 1; i >= 0 && reversed.length() < 32; i--) {
            JSONObject message = source.optJSONObject(i);
            if (message == null) continue;
            String role = message.optString("role", "");
            String content = message.optString("content", "");
            if (role.isEmpty() || content.isEmpty()) continue;
            characters += content.length();
            if (characters > 24000 && reversed.length() > 0) break;
            try {
                reversed.put(copyMessage(message));
            } catch (Throwable ignore) {}
        }

        JSONArray result = new JSONArray();
        for (int i = reversed.length() - 1; i >= 0; i--) {
            JSONObject message = reversed.optJSONObject(i);
            if (message != null) result.put(message);
        }
        while (result.length() > 0) {
            JSONObject first = result.optJSONObject(0);
            if (first == null || !"assistant".equals(first.optString("role"))) break;
            result.remove(0);
        }
        return result;
    }

    /** Диалог с историей + опциональный стриминг — экран ИИ-чата. */
    public static void generate(JSONArray conversation, boolean stream, StreamCallback callback) {
        request(conversation, stream, callback);
    }

    /** Тестовый запрос с явными кредами (без сохранения в prefs) — для «Проверить и сохранить». */
    public static void test(String endpoint, String model, String key, Callback callback) {
        new Thread(() -> {
            String result = null;
            Throwable error = null;
            HttpURLConnection connection = null;
            try {
                if (key == null || key.trim().isEmpty()) throw new IllegalStateException("Не указан API-ключ");
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + key);
                connection.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("model", model);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "user").put("content", "Say 'hi'."));
                body.put("messages", messages);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                InputStream in = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder resp = new StringBuilder();
                if (in != null) {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String l;
                        while ((l = r.readLine()) != null) resp.append(l);
                    }
                }
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + resp);
                result = new JSONObject(resp.toString()).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content");
            } catch (Throwable t) {
                error = t;
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String fr = result;
            final Throwable fe = error;
            AndroidUtilities.runOnUIThread(() -> callback.done(fr, fe));
        }, "DevGramAITest").start();
    }

    /** Убрать из запроса параметр, на который ругается сервис; false — ничего подходящего. */
    private static boolean dropUnsupportedParam(JSONObject body, String error) {
        String e = error == null ? "" : error.toLowerCase(java.util.Locale.ROOT);
        if ((e.contains("reasoning") || e.contains("thinking")) && (body.has("reasoning") || body.has("reasoning_effort"))) {
            body.remove("reasoning");
            body.remove("reasoning_effort");
            return true;
        }
        if (e.contains("temperature") && body.has("temperature")) {
            body.remove("temperature");
            return true;
        }
        if (e.contains("max_tokens") && body.has("max_tokens")) {
            try {
                body.put("max_completion_tokens", body.get("max_tokens"));
            } catch (Exception ignore) {
            }
            body.remove("max_tokens");
            return true;
        }
        if (e.contains("max_completion_tokens") && body.has("max_completion_tokens")) {
            body.remove("max_completion_tokens");
            return true;
        }
        return false;
    }

    private static void request(JSONArray conversation, boolean stream, StreamCallback callback) {
        request(conversation, stream, callback, null);
    }

    private static void request(JSONArray conversation, boolean stream, StreamCallback callback, String systemOverride) {
        if (DevGramGeminiNano.isSelected()) {
            DevGramGeminiNano.generate(conversation, stream, systemOverride, callback);
            return;
        }
        new Thread(() -> {
            String result = null;
            Throwable error = null;
            HttpURLConnection connection = null;
            StringBuilder full = new StringBuilder();
            try {
                SharedPreferences prefs = MessagesController.getGlobalMainSettings();
                String endpoint = prefs.getString("dg_aiEndpoint", "https://api.openai.com/v1/chat/completions");
                String model = prefs.getString("dg_aiModel", "gpt-4o-mini");
                String key = prefs.getString("dg_aiKey", "");
                if (key.trim().isEmpty()) throw new IllegalStateException("Сначала укажите API-ключ");

                JSONObject body = new JSONObject();
                body.put("model", model);
                // Температура из настроек AI Chat (слайдер 0.0–2.0), дефолт 1.0.
                body.put("temperature", prefs.getFloat("dg_aiTemperature", 1.0f));
                if (stream) {
                    body.put("stream", true);
                }
                // «Рассуждения» активного сервиса — параметр зависит от провайдера.
                if (prefs.getBoolean("dg_aiReasoning", false)) {
                    if ("OpenRouter".equals(prefs.getString("dg_aiProvider", ""))) {
                        body.put("reasoning", new JSONObject().put("effort", "medium"));
                    } else {
                        body.put("reasoning_effort", "medium");
                    }
                }

                JSONArray messages = new JSONArray();
                // Роль (системный промпт) из настроек AI Chat.
                String systemPrompt = systemOverride != null
                        ? systemOverride : prefs.getString("dg_aiSystemPrompt", "");
                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
                }
                for (int i = 0; i < conversation.length(); i++) {
                    JSONObject m = conversation.optJSONObject(i);
                    if (m != null) messages.put(m);
                }
                body.put("messages", messages);

                InputStream in = null;
                // Сервис может не понимать часть параметров (уровень рассуждений, temperature, max_tokens):
                // тогда убираем упомянутый в ошибке параметр и повторяем запрос
                for (int attempt = 0; ; attempt++) {
                    connection = (HttpURLConnection) new URL(endpoint).openConnection();
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(120000);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Authorization", "Bearer " + key);
                    connection.setDoOutput(true);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    int code = connection.getResponseCode();
                    in = code >= 200 && code < 300
                            ? connection.getInputStream() : connection.getErrorStream();
                    if (code >= 200 && code < 300) {
                        break;
                    }
                    StringBuilder err = new StringBuilder();
                    if (in != null) {
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                            String l;
                            while ((l = r.readLine()) != null) err.append(l);
                        }
                    }
                    if ((code == 400 || code == 422) && attempt < 3 && dropUnsupportedParam(body, err.toString())) {
                        connection.disconnect();
                        continue;
                    }
                    throw new IllegalStateException("HTTP " + code + ": " + err);
                }

                if (stream) {
                    StringBuilder raw = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            raw.append(line).append('\n');
                            if (!line.startsWith("data:")) continue;
                            String data = line.substring(5).trim();
                            if (data.isEmpty()) continue;
                            if ("[DONE]".equals(data)) break;
                            try {
                                JSONObject delta = new JSONObject(data).getJSONArray("choices")
                                        .getJSONObject(0).optJSONObject("delta");
                                String piece = delta != null ? delta.optString("content", "") : "";
                                if (!piece.isEmpty()) {
                                    full.append(piece);
                                    final String p = piece;
                                    AndroidUtilities.runOnUIThread(() -> callback.onDelta(p));
                                }
                            } catch (Throwable ignore) {}
                        }
                    }
                    // Фолбэк: сервер проигнорировал stream и вернул обычный JSON.
                    if (full.length() == 0) {
                        try {
                            String content = new JSONObject(raw.toString().trim()).getJSONArray("choices")
                                    .getJSONObject(0).getJSONObject("message").getString("content");
                            full.append(content);
                            final String c = content;
                            AndroidUtilities.runOnUIThread(() -> callback.onDelta(c));
                        } catch (Throwable ignore) {}
                    }
                    result = full.toString();
                } else {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }
                    result = new JSONObject(response.toString()).getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message").getString("content");
                }
            } catch (Throwable t) {
                error = t;
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String finalResult = result;
            final Throwable finalError = error;
            AndroidUtilities.runOnUIThread(() -> callback.onDone(finalResult, finalError));
        }, "DevGramAI").start();
    }
}

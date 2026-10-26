package org.telegram.messenger;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/** Translation providers used by the General settings, matching exteraGram. */
public final class DevGramTranslator {
    public interface Callback {
        void run(TLRPC.TL_textWithEntities result, Throwable error);
    }

    public interface BatchCallback {
        void run(ArrayList<TLRPC.TL_textWithEntities> result, Throwable error);
    }

    private static final Set<String> YANDEX_LANGUAGES = new HashSet<>(Arrays.asList(
            "az", "sq", "am", "en", "ar", "hy", "af", "eu", "ba", "be", "bn", "my", "bg", "bs",
            "cv", "cy", "hu", "vi", "ht", "nl", "mrj", "el", "ka", "gu", "da", "he", "yi", "id",
            "ga", "it", "is", "es", "kk", "kn", "ca", "ky", "zh", "ko", "xh", "km", "lo", "la",
            "lv", "lt", "lb", "mg", "ms", "ml", "mt", "mk", "mi", "mr", "mhr", "mn", "de", "ne",
            "no", "pa", "pap", "fa", "pl", "pt", "ro", "ru", "ceb", "sr", "si", "sk", "sl", "sw",
            "su", "tg", "th", "tl", "ta", "tt", "te", "tr", "udm", "uz", "uk", "ur", "fi", "fr",
            "hi", "hr", "cs", "sv", "gd", "et", "eo", "jv", "ja"
    ));
    private static final Set<String> DEEPL_LANGUAGES = new HashSet<>(Arrays.asList(
            "bg", "cs", "da", "de", "el", "en", "en-gb", "en-us", "es", "fi", "fr", "hu", "id", "it",
            "ja", "lt", "lv", "nl", "pl", "pt", "pt-br", "pt-pt", "ro", "ru", "sk", "sl", "sv", "tr",
            "uk", "zh"
    ));
    private static final String YANDEX_UUID = UUID.randomUUID().toString().replace("-", "");
    private static final ExecutorService TRANSLATION_EXECUTOR = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "DevGramTranslator");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private DevGramTranslator() {}

    public static boolean usesExternalProvider() {
        return DevGramGeneralConfig.getTranslationProvider() != 0;
    }

    public static boolean isTargetSupported(String language) {
        int provider = DevGramGeneralConfig.getTranslationProvider();
        String normalized = normalize(language);
        if (provider == 2) {
            return YANDEX_LANGUAGES.contains(primary(normalized));
        }
        if (provider == 3) {
            return DEEPL_LANGUAGES.contains(normalized) || DEEPL_LANGUAGES.contains(primary(normalized));
        }
        return true;
    }

    public static void translate(String text, String fromLanguage, String toLanguage, Callback callback) {
        if (callback == null) {
            return;
        }
        final String source = TextUtils.isEmpty(fromLanguage) || TranslateController.UNKNOWN_LANGUAGE.equals(fromLanguage)
                ? "auto" : normalize(fromLanguage);
        final String target = normalize(toLanguage);
        final int provider = resolvedProvider(target);
        TRANSLATION_EXECUTOR.execute(() -> {
            try {
                TLRPC.TL_textWithEntities input = new TLRPC.TL_textWithEntities();
                input.text = text;
                TLRPC.TL_textWithEntities result = translateBlocking(input, source, target, provider);
                AndroidUtilities.runOnUIThread(() -> callback.run(result, null));
            } catch (Throwable error) {
                FileLog.e(error);
                AndroidUtilities.runOnUIThread(() -> callback.run(null, error));
            }
        });
    }

    /** Translates a grouped chat request without using Telegram's Premium-only API. */
    public static void translateBatch(List<TLRPC.TL_textWithEntities> texts, String fromLanguage,
                                      String toLanguage, BatchCallback callback) {
        if (callback == null) {
            return;
        }
        final ArrayList<TLRPC.TL_textWithEntities> sourceTexts = texts == null
                ? new ArrayList<>() : new ArrayList<>(texts);
        final String source = TextUtils.isEmpty(fromLanguage) || TranslateController.UNKNOWN_LANGUAGE.equals(fromLanguage)
                ? "auto" : normalize(fromLanguage);
        final String target = normalize(toLanguage);
        final int provider = resolvedProvider(target);
        TRANSLATION_EXECUTOR.execute(() -> {
            try {
                ArrayList<TLRPC.TL_textWithEntities> result = new ArrayList<>(sourceTexts.size());
                for (TLRPC.TL_textWithEntities text : sourceTexts) {
                    TLRPC.TL_textWithEntities input = text;
                    if (input == null) {
                        input = new TLRPC.TL_textWithEntities();
                        input.text = "";
                    }
                    result.add(translateBlocking(input, source, target, provider));
                }
                AndroidUtilities.runOnUIThread(() -> callback.run(result, null));
            } catch (Throwable error) {
                FileLog.e(error);
                AndroidUtilities.runOnUIThread(() -> callback.run(null, error));
            }
        });
    }

    private static int resolvedProvider(String target) {
        int configuredProvider = DevGramGeneralConfig.getTranslationProvider();
        return isTargetSupported(target) ? configuredProvider : 1;
    }

    /** Перевод с entities: разметка передаётся сервису в виде HTML (Google, Яндекс, Microsoft, DeepL). */
    public static void translate(TLRPC.TL_textWithEntities text, String fromLanguage, String toLanguage, Callback callback) {
        if (callback == null) {
            return;
        }
        final String source = TextUtils.isEmpty(fromLanguage) || TranslateController.UNKNOWN_LANGUAGE.equals(fromLanguage)
                ? "auto" : normalize(fromLanguage);
        final String target = normalize(toLanguage);
        final int provider = resolvedProvider(target);
        TRANSLATION_EXECUTOR.execute(() -> {
            try {
                TLRPC.TL_textWithEntities result = translateBlocking(text, source, target, provider);
                AndroidUtilities.runOnUIThread(() -> callback.run(result, null));
            } catch (Throwable error) {
                FileLog.e(error);
                AndroidUtilities.runOnUIThread(() -> callback.run(null, error));
            }
        });
    }

    private static TLRPC.TL_textWithEntities translateBlocking(TLRPC.TL_textWithEntities input, String source,
                                                                String target, int provider) throws Exception {
        if (DevGramTranslationMarkup.hasFormatting(input)) {
            try {
                String html = DevGramTranslationMarkup.toHtml(input);
                // DeepL переносит HTML-теги в перевод как есть, поэтому разметка идёт тем же путём
                String translatedHtml = provider == 2 ? translateYandex(html, target, true)
                        : provider == 4 ? translateMicrosoft(html, source, target, true)
                        : provider == 3 ? translateDeepL(html, source, target)
                        : translateGoogleHtml(html, source, target);
                TLRPC.TL_textWithEntities formatted = TextUtils.isEmpty(translatedHtml) ? null : DevGramTranslationMarkup.fromHtml(translatedHtml, input);
                if (formatted != null && !TextUtils.isEmpty(formatted.text)) {
                    return formatted;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        String text = input.text == null ? "" : input.text;
        String translated;
        if (provider == 2) {
            translated = translateYandex(text, target, false);
        } else if (provider == 4) {
            translated = translateMicrosoft(text, source, target, false);
        } else if (provider == 3) {
            translated = translateDeepL(text, source, target);
        } else {
            translated = translateGoogle(text, source, target);
        }
        if (TextUtils.isEmpty(translated)) {
            throw new IllegalStateException("Empty translation response");
        }
        TLRPC.TL_textWithEntities result = new TLRPC.TL_textWithEntities();
        result.text = translated;
        return result;
    }

    private static String translateGoogle(String text, String from, String to) throws Exception {
        String url = "https://translate.googleapis.com/translate_a/single?dj=1&q="
                + URLEncoder.encode(text == null ? "" : text, "UTF-8")
                + "&sl=" + URLEncoder.encode(TextUtils.isEmpty(from) ? "auto" : from, "UTF-8")
                + "&tl=" + URLEncoder.encode(to, "UTF-8")
                + "&ie=UTF-8&oe=UTF-8&client=at&dt=t&otf=2";
        JSONObject json = new JSONObject(request("GET", url, null, null,
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"));
        JSONArray sentences = json.optJSONArray("sentences");
        StringBuilder result = new StringBuilder();
        if (sentences != null) {
            for (int i = 0; i < sentences.length(); i++) {
                JSONObject sentence = sentences.optJSONObject(i);
                if (sentence != null) {
                    result.append(sentence.optString("trans", ""));
                }
            }
        }
        return result.toString();
    }

    private static String translateMicrosoft(String text, String from, String to, boolean html) throws Exception {
        String path = "api.cognitive.microsofttranslator.com/translate?api-version=3.0&from="
                + ("auto".equalsIgnoreCase(from) ? "" : from) + "&to=" + to + "&textType=" + (html ? "html" : "plain");
        String body = new JSONArray().put(new JSONObject().put("Text", text == null ? "" : text)).toString();
        HttpURLConnection connection = (HttpURLConnection) new URL("https://" + path).openConnection();
        try {
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "okhttp/4.5.0");
            connection.setRequestProperty("X-Mt-Signature", microsoftSignature(path));
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder response = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
            }
            if (code < 200 || code >= 300 || !response.toString().startsWith("[")) {
                throw new IllegalStateException("Microsoft HTTP " + code + ": " + response);
            }
            return new JSONArray(response.toString()).getJSONObject(0).getJSONArray("translations").getJSONObject(0).optString("text");
        } finally {
            connection.disconnect();
        }
    }

    /** Подпись запроса, как у приложения Microsoft Translator. */
    private static String microsoftSignature(String path) throws Exception {
        String guid = UUID.randomUUID().toString().replace("-", "");
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
        format.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        String date = format.format(new java.util.Date()).toLowerCase(Locale.US) + "GMT";
        String data = ("MSTranslatorAndroidApp" + URLEncoder.encode(path, "UTF-8") + date + guid).toLowerCase(Locale.US);
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(android.util.Base64.decode("oik6PdDdMnOXemTbwvMn9de/h9lFnfBaCWbGMMZqqoSaQaqUOqjVGm5NqsmjcBI1x+sS9ugjB55HEJWRiFXYFw", 0), "HmacSHA256"));
        return "MSTranslatorAndroidApp::" + android.util.Base64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)), android.util.Base64.NO_WRAP) + "::" + date + "::" + guid;
    }

    private static String translateGoogleHtml(String html, String from, String to) throws Exception {
        String url = "https://translate.googleapis.com/translate_a/t?client=gtx&dt=t&format=html&ie=UTF-8&oe=UTF-8"
                + "&sl=" + URLEncoder.encode(TextUtils.isEmpty(from) ? "auto" : from, "UTF-8")
                + "&tl=" + URLEncoder.encode(to, "UTF-8");
        String response = request("POST", url, "q=" + URLEncoder.encode(html, "UTF-8"), "application/x-www-form-urlencoded",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36");
        Object parsed = new org.json.JSONTokener(response).nextValue();
        while (parsed instanceof JSONArray && ((JSONArray) parsed).length() > 0) {
            parsed = ((JSONArray) parsed).get(0);
        }
        return parsed instanceof String ? (String) parsed : null;
    }

    private static String translateYandex(String text, String to, boolean html) throws Exception {
        String body = "lang=" + URLEncoder.encode(to, "UTF-8") + (html ? "&format=html" : "")
                + "&text=" + URLEncoder.encode(text == null ? "" : text, "UTF-8");
        JSONObject json = new JSONObject(request("POST",
                "https://translate.yandex.net/api/v1/tr.json/translate?&srv=android&id=" + YANDEX_UUID + "-0-0",
                body, "application/x-www-form-urlencoded",
                "ru.yandex.translate/21.15.4.21402814 (Xiaomi Redmi K20 Pro; Android 11)"));
        JSONArray translations = json.optJSONArray("text");
        if (translations == null) {
            throw new IllegalStateException(json.optString("message", "Yandex translation failed"));
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < translations.length(); i++) {
            result.append(translations.optString(i, ""));
        }
        return result.toString();
    }

    private static String translateDeepL(String text, String from, String to) throws Exception {
        String normalizedTarget = normalize(to);
        String primaryTarget = primary(normalizedTarget);
        String regionalVariant = normalizedTarget.contains("-")
                ? primaryTarget + "-" + normalizedTarget.substring(normalizedTarget.indexOf('-') + 1).toUpperCase(Locale.US)
                : null;

        long id = ThreadLocalRandom.current().nextLong(1, 1_000_000_000L);
        JSONObject request = new JSONObject();
        request.put("method", "LMT_handle_texts");
        request.put("id", id);
        request.put("jsonrpc", "2.0");

        JSONObject params = new JSONObject();
        params.put("splitting", "newlines");
        params.put("priority", 1);
        JSONArray texts = new JSONArray();
        texts.put(new JSONObject().put("requestAlternatives", 0).put("text", text == null ? "" : text));
        params.put("texts", texts);
        params.put("lang", new JSONObject()
                .put("target_lang", primaryTarget)
                .put("source_lang_user_selected", "auto".equals(from) ? "" : primary(from)));
        JSONObject common = new JSONObject()
                .put("regionalVariant", regionalVariant == null ? JSONObject.NULL : regionalVariant)
                .put("wasSpoken", false);
        int formality = DevGramGeneralConfig.getTranslationFormality();
        common.put("formality", formality == 1 ? "informal" : formality == 2 ? "formal" : JSONObject.NULL);
        params.put("commonJobParams", common);
        params.put("timestamp", System.currentTimeMillis());
        request.put("params", params);

        String encoded = request.toString();
        encoded = ((id + 3) % 13 != 0 && (id + 5) % 29 != 0)
                ? encoded.replace("hod\":\"", "hod\": \"")
                : encoded.replace("hod\":\"", "hod\" : \"");
        JSONObject json = new JSONObject(request("POST", "https://www2.deepl.com/jsonrpc", encoded,
                "application/json; charset=utf-8", "DeepL/25.2.1(150) Android 14 (Pixel 7 Pro;aarch64)"));
        JSONObject result = json.optJSONObject("result");
        JSONArray translatedTexts = result == null ? null : result.optJSONArray("texts");
        StringBuilder translated = new StringBuilder();
        if (translatedTexts != null) {
            for (int i = 0; i < translatedTexts.length(); i++) {
                JSONObject item = translatedTexts.optJSONObject(i);
                if (item != null) {
                    translated.append(item.optString("text", ""));
                }
            }
        }
        return translated.toString();
    }

    private static String request(String method, String address, String body, String contentType, String userAgent) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder response = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
        }
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + ": " + response);
        }
        return response.toString();
    }

    private static String normalize(String language) {
        if (TextUtils.isEmpty(language)) {
            return "auto";
        }
        String normalized = language.trim().toLowerCase(Locale.US).replace('_', '-');
        return "nb".equals(normalized) ? "no" : normalized;
    }

    private static String primary(String language) {
        int separator = language == null ? -1 : language.indexOf('-');
        return separator > 0 ? language.substring(0, separator) : language;
    }
}

package org.telegram.messenger;

import android.os.Build;

import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.common.StreamingCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.prompt.Candidate;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerationConfig;
import com.google.mlkit.genai.prompt.GenerativeModel;
import com.google.mlkit.genai.prompt.ModelConfig;
import com.google.mlkit.genai.prompt.SystemInstruction;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Gemini Nano through Android AICore. Prompts and responses never leave the device. */
public final class DevGramGeminiNano {
    public static final int STATUS_UNKNOWN = -1;
    public static final int STATUS_UNAVAILABLE = 0;
    public static final int STATUS_DOWNLOADABLE = 1;
    public static final int STATUS_DOWNLOADING = 2;
    public static final int STATUS_AVAILABLE = 3;

    public interface DownloadListener {
        void onStarted(long bytes);
        void onProgress(long downloaded, int percent);
        void onCompleted();
        void onFailed(Throwable error);
    }

    private static volatile int status = STATUS_UNKNOWN;
    private static volatile boolean checking;
    private static volatile long downloadSize;
    private static volatile long downloaded;
    private static volatile String baseModelName = "";
    private static volatile int tokenLimit;
    private static volatile boolean systemPromptAvailable;
    private static volatile boolean thinkingModeAvailable;

    private DevGramGeminiNano() {}

    public static boolean isSelected() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_aiUseNano", false);
    }

    public static int getStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return STATUS_UNAVAILABLE;
        return status;
    }

    public static boolean isChecking() {
        return checking;
    }

    public static int getDownloadPercent() {
        if (downloadSize <= 0 || downloaded < 0) return -1;
        return Math.max(0, Math.min(100, (int) (downloaded * 100L / downloadSize)));
    }

    public static String getBaseModelName() {
        return baseModelName;
    }

    public static int getTokenLimit() {
        return tokenLimit;
    }

    public static boolean isSystemPromptAvailable() {
        return systemPromptAvailable;
    }

    public static boolean isThinkingModeAvailable() {
        return thinkingModeAvailable;
    }

    public static String statusText() {
        int current = getStatus();
        if (checking || current == STATUS_UNKNOWN) return "Проверка доступности…";
        if (current == STATUS_AVAILABLE) {
            String name = baseModelName == null || baseModelName.isEmpty() ? modelLabel() : baseModelName;
            return name + " · на устройстве";
        }
        if (current == STATUS_DOWNLOADABLE) return modelLabel() + " · требуется загрузка";
        if (current == STATUS_DOWNLOADING) {
            int percent = getDownloadPercent();
            return percent >= 0 ? "Загрузка · " + percent + "%" : "Модель загружается…";
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                ? "Требуется Android 8.0 или новее"
                : "Не поддерживается этим устройством";
    }

    public static String modelLabel() {
        boolean preview = MessagesController.getGlobalMainSettings().getBoolean("dg_aiNanoPreview", false);
        boolean fast = MessagesController.getGlobalMainSettings().getBoolean("dg_aiNanoFast", true);
        return "Gemini Nano · " + (preview ? "Preview" : "Stable") + " · " + (fast ? "Fast" : "Full");
    }

    private static GenerativeModel createModel() {
        boolean preview = MessagesController.getGlobalMainSettings().getBoolean("dg_aiNanoPreview", false);
        boolean fast = MessagesController.getGlobalMainSettings().getBoolean("dg_aiNanoFast", true);
        ModelConfig.Builder model = new ModelConfig.Builder();
        model.setReleaseStage(preview ? 1 : 0);
        model.setPreference(fast ? 1 : 2);
        GenerationConfig.Builder generation = new GenerationConfig.Builder();
        generation.setModelConfig(model.build());
        return Generation.INSTANCE.getClient(generation.build());
    }

    public static void configurationChanged(Runnable done) {
        status = STATUS_UNKNOWN;
        downloadSize = 0;
        downloaded = 0;
        baseModelName = "";
        tokenLimit = 0;
        systemPromptAvailable = false;
        thinkingModeAvailable = false;
        refresh(done);
    }

    public static void refresh(Runnable done) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            status = STATUS_UNAVAILABLE;
            if (done != null) AndroidUtilities.runOnUIThread(done);
            return;
        }
        if (checking) {
            if (done != null) AndroidUtilities.runOnUIThread(() -> refresh(done), 250);
            return;
        }
        checking = true;
        new Thread(() -> {
            GenerativeModel model = null;
            int checked = STATUS_UNAVAILABLE;
            try {
                model = createModel();
                GenerativeModelFutures futures = GenerativeModelFutures.from(model);
                Integer value = futures.checkStatus().get(10, TimeUnit.SECONDS);
                if (value != null) checked = value;
                if (checked == STATUS_AVAILABLE) readCapabilities(futures, false);
            } catch (Throwable error) {
                FileLog.e("Gemini Nano availability check failed", error);
            } finally {
                if (model != null) {
                    try { model.close(); } catch (Throwable ignore) {}
                }
            }
            status = checked;
            checking = false;
            if (done != null) AndroidUtilities.runOnUIThread(done);
        }, "DevGramNanoCheck").start();
    }

    public static void download(DownloadListener listener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            listener.onFailed(new IllegalStateException("Gemini Nano недоступен на этом устройстве"));
            return;
        }
        status = STATUS_DOWNLOADING;
        new Thread(() -> {
            GenerativeModel model = null;
            AtomicBoolean finished = new AtomicBoolean(false);
            try {
                model = createModel();
                GenerativeModelFutures.from(model).download(new DownloadCallback() {
                    @Override
                    public void onDownloadStarted(long bytes) {
                        downloadSize = bytes;
                        downloaded = 0;
                        AndroidUtilities.runOnUIThread(() -> listener.onStarted(bytes));
                    }

                    @Override
                    public void onDownloadProgress(long bytes) {
                        downloaded = bytes;
                        int percent = getDownloadPercent();
                        AndroidUtilities.runOnUIThread(() -> listener.onProgress(bytes, percent));
                    }

                    @Override
                    public void onDownloadCompleted() {
                        if (!finished.compareAndSet(false, true)) return;
                        status = STATUS_AVAILABLE;
                        AndroidUtilities.runOnUIThread(listener::onCompleted);
                    }

                    @Override
                    public void onDownloadFailed(GenAiException error) {
                        if (!finished.compareAndSet(false, true)) return;
                        status = STATUS_DOWNLOADABLE;
                        AndroidUtilities.runOnUIThread(() -> listener.onFailed(error));
                    }
                }).get();
                if (finished.compareAndSet(false, true)) {
                    status = STATUS_AVAILABLE;
                    AndroidUtilities.runOnUIThread(listener::onCompleted);
                }
            } catch (Throwable error) {
                if (finished.compareAndSet(false, true)) {
                    status = STATUS_DOWNLOADABLE;
                    AndroidUtilities.runOnUIThread(() -> listener.onFailed(error));
                }
            } finally {
                if (model != null) {
                    try { model.close(); } catch (Throwable ignore) {}
                }
            }
        }, "DevGramNanoDownload").start();
    }

    public static void generate(JSONArray conversation, boolean stream, String systemOverride,
            DevGramAiClient.StreamCallback callback) {
        new Thread(() -> {
            GenerativeModel model = null;
            try {
                model = createModel();
                GenerativeModelFutures futures = GenerativeModelFutures.from(model);
                Integer checked = futures.checkStatus().get(10, TimeUnit.SECONDS);
                status = checked == null ? STATUS_UNAVAILABLE : checked;
                if (status == STATUS_DOWNLOADABLE) throw new IllegalStateException("Сначала загрузите модель Gemini Nano");
                if (status == STATUS_DOWNLOADING) throw new IllegalStateException("Модель Gemini Nano ещё загружается");
                if (status != STATUS_AVAILABLE) throw new IllegalStateException("Gemini Nano недоступен на этом устройстве");

                readCapabilities(futures, true);
                String system = systemOverride != null
                        ? systemOverride : MessagesController.getGlobalMainSettings().getString("dg_aiSystemPrompt", "");
                String prompt = buildPrompt(conversation, systemPromptAvailable ? null : system);
                GenerateContentRequest.Builder request = systemPromptAvailable && system != null && !system.trim().isEmpty()
                        ? new GenerateContentRequest.Builder(new SystemInstruction(system.trim()), new TextPart(prompt))
                        : new GenerateContentRequest.Builder(new TextPart(prompt));
                request.setTemperature(Math.min(1.0f, MessagesController.getGlobalMainSettings().getFloat("dg_aiTemperature", 1.0f)));
                request.setMaxOutputTokens(tokenLimit > 0 ? Math.max(128, Math.min(1024, tokenLimit / 2)) : 1024);
                request.setEnableThinking(thinkingModeAvailable && MessagesController.getGlobalMainSettings()
                        .getBoolean("dg_aiNanoReasoning", false));
                StringBuffer streamed = new StringBuffer();
                ListenableFuture<GenerateContentResponse> generation;
                if (stream) {
                    generation = futures.generateContent(request.build(), new StreamingCallback() {
                        @Override
                        public void onNewText(String text) {
                            if (text == null || text.isEmpty()) return;
                            streamed.append(text);
                            AndroidUtilities.runOnUIThread(() -> callback.onDelta(text));
                        }
                    });
                } else {
                    generation = futures.generateContent(request.build());
                }
                GenerateContentResponse response = generation.get(120, TimeUnit.SECONDS);
                String answer = firstText(response);
                if (answer.isEmpty()) answer = streamed.toString().trim();
                if (answer.isEmpty()) throw new IllegalStateException("Модель не вернула ответ");
                String finalAnswer = answer;
                AndroidUtilities.runOnUIThread(() -> callback.onDone(finalAnswer, null));
            } catch (Throwable error) {
                FileLog.e("Gemini Nano generation failed", error);
                AndroidUtilities.runOnUIThread(() -> callback.onDone(null, unwrap(error)));
            } finally {
                if (model != null) {
                    try { model.close(); } catch (Throwable ignore) {}
                }
            }
        }, "DevGramNanoGenerate").start();
    }

    private static String buildPrompt(JSONArray conversation, String systemOverride) {
        StringBuilder prompt = new StringBuilder();
        String role = systemOverride != null
                ? systemOverride : MessagesController.getGlobalMainSettings().getString("dg_aiSystemPrompt", "");
        if (role != null && !role.trim().isEmpty()) {
            prompt.append("System instructions:\n").append(role.trim()).append("\n\n");
        }
        int start = Math.max(0, conversation.length() - 24);
        int chars = 0;
        for (int i = conversation.length() - 1; i >= start; i--) {
            JSONObject message = conversation.optJSONObject(i);
            if (message == null) continue;
            chars += message.optString("content", "").length();
            if (chars > 12000) {
                start = i + 1;
                break;
            }
        }
        for (int i = start; i < conversation.length(); i++) {
            JSONObject message = conversation.optJSONObject(i);
            if (message == null) continue;
            String content = message.optString("content", "").trim();
            if (content.isEmpty()) continue;
            prompt.append("assistant".equals(message.optString("role")) ? "Assistant" : "User")
                    .append(": ").append(content).append("\n\n");
        }
        prompt.append("Assistant:");
        return prompt.toString();
    }

    private static void readCapabilities(GenerativeModelFutures futures, boolean warmup) {
        try {
            String name = futures.getBaseModelName().get(10, TimeUnit.SECONDS);
            baseModelName = name == null ? "" : name;
        } catch (Throwable ignore) {}
        try {
            Integer limit = futures.getTokenLimit().get(10, TimeUnit.SECONDS);
            tokenLimit = limit == null ? 0 : limit;
        } catch (Throwable ignore) {}
        try {
            Boolean value = futures.isSystemPromptAvailable().get(10, TimeUnit.SECONDS);
            systemPromptAvailable = value != null && value;
        } catch (Throwable ignore) {}
        try {
            Boolean value = futures.isThinkingModeAvailable().get(10, TimeUnit.SECONDS);
            thinkingModeAvailable = value != null && value;
        } catch (Throwable ignore) {}
        if (warmup) {
            try { futures.warmup().get(30, TimeUnit.SECONDS); } catch (Throwable ignore) {}
        }
    }

    private static String firstText(GenerateContentResponse response) {
        if (response == null) return "";
        List<Candidate> candidates = response.getCandidates();
        if (candidates == null || candidates.isEmpty() || candidates.get(0) == null) return "";
        String text = candidates.get(0).getText();
        return text == null ? "" : text.trim();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }
}

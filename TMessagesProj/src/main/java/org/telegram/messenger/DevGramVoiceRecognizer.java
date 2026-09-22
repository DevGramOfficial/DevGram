package org.telegram.messenger;

import android.text.TextUtils;

import com.google.gson.Gson;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechStreamService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.net.HttpURLConnection;
import java.net.URL;

// DevGram: офлайн-распознавание голоса на Vosk (порт VoskRecognizer exteraGram, упрощён и адаптирован).
// Модели качаются с alphacephei.com в externalFilesDir/"Vosk Models"/<lang>. Движок из AAR com.alphacephei:vosk-android.
public class DevGramVoiceRecognizer {

    public static class RecognitionModel {
        public final String language;
        public final String url;
        public final long size;

        public RecognitionModel(String language, String url, long size) {
            this.language = language;
            this.url = url;
            this.size = size;
        }
    }

    public interface DownloadCallback {
        void onProgress(float progress);
        void onCompleted();
        void onError(Exception e);
    }

    public interface RecognitionCallback {
        void onChunk(String text);
        void onCompleted(String text);
        void onError(Exception e);
        void onNotDownloaded(String language);
        void onNotSupported(String language);
    }

    private static final DevGramVoiceRecognizer INSTANCE = new DevGramVoiceRecognizer();

    public static DevGramVoiceRecognizer getInstance() {
        return INSTANCE;
    }

    private final File modelsDir = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "Vosk Models");
    private final Map<String, Model> loadedModels = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final List<RecognitionModel> models = new ArrayList<>();

    private DevGramVoiceRecognizer() {
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS);
        } catch (Throwable ignore) {
        }
        String base = "https://alphacephei.com/vosk/models/";
        models.add(new RecognitionModel("ca", base + "vosk-model-small-ca-0.4.zip", 43405881L));
        models.add(new RecognitionModel("cs", base + "vosk-model-small-cs-0.4-rhasspy.zip", 46088666L));
        models.add(new RecognitionModel("de", base + "vosk-model-small-de-0.15.zip", 46499967L));
        models.add(new RecognitionModel("en", base + "vosk-model-small-en-us-0.15.zip", 41205931L));
        models.add(new RecognitionModel("eo", base + "vosk-model-small-eo-0.42.zip", 43839401L));
        models.add(new RecognitionModel("es", base + "vosk-model-small-es-0.42.zip", 39817833L));
        models.add(new RecognitionModel("fa", base + "vosk-model-small-fa-0.42.zip", 53431220L));
        models.add(new RecognitionModel("fr", base + "vosk-model-small-fr-0.22.zip", 42233323L));
        models.add(new RecognitionModel("hi", base + "vosk-model-small-hi-0.22.zip", 44458845L));
        models.add(new RecognitionModel("it", base + "vosk-model-small-it-0.22.zip", 49665141L));
        models.add(new RecognitionModel("ja", base + "vosk-model-small-ja-0.22.zip", 49704573L));
        models.add(new RecognitionModel("ko", base + "vosk-model-small-ko-0.22.zip", 86914329L));
        models.add(new RecognitionModel("nl", base + "vosk-model-small-nl-0.22.zip", 40441176L));
        models.add(new RecognitionModel("pl", base + "vosk-model-small-pl-0.22.zip", 52979372L));
        models.add(new RecognitionModel("pt", base + "vosk-model-small-pt-0.3.zip", 32453112L));
        models.add(new RecognitionModel("ru", base + "vosk-model-small-ru-0.22.zip", 46236750L));
        models.add(new RecognitionModel("tr", base + "vosk-model-small-tr-0.3.zip", 36855784L));
        models.add(new RecognitionModel("uk", base + "vosk-model-small-uk-v3-small.zip", 143914407L));
        models.add(new RecognitionModel("uz", base + "vosk-model-small-uz-0.22.zip", 51061189L));
    }

    public List<RecognitionModel> listAvailableModels() {
        return models;
    }

    public RecognitionModel findModel(String language) {
        for (RecognitionModel m : models) {
            if (m.language.equals(language)) {
                return m;
            }
        }
        return null;
    }

    public boolean isDownloaded(String language) {
        File dir = new File(modelsDir, language);
        return dir.exists() && !new File(dir, "model.zip").exists() && !isDirectoryEmpty(dir);
    }

    public List<RecognitionModel> listDownloadedModels() {
        List<RecognitionModel> result = new ArrayList<>();
        for (RecognitionModel m : models) {
            if (isDownloaded(m.language)) {
                result.add(m);
            }
        }
        return result;
    }

    private boolean isDirectoryEmpty(File dir) {
        String[] list = dir.list();
        return list == null || list.length == 0;
    }

    // Скачать и распаковать модель языка. Вызывать с фонового потока.
    public void downloadModel(String language, DownloadCallback callback) {
        RecognitionModel model = findModel(language);
        if (model == null) {
            callback.onError(new IllegalArgumentException("Model not found: " + language));
            return;
        }
        File dir = new File(modelsDir, language);
        try {
            if (new File(dir, "model.zip").exists()) {
                deleteDirectory(dir);
            }
            if (!dir.exists()) {
                dir.mkdirs();
            }
            HttpURLConnection conn = (HttpURLConnection) new URL(model.url).openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                callback.onError(new IOException("Failed to download, HTTP " + code));
                conn.disconnect();
                return;
            }
            File zip = new File(dir, "model.zip");
            long contentLength = conn.getContentLength();
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(zip)) {
                byte[] buf = new byte[8192];
                long written = 0;
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    written += read;
                    if (contentLength > 0) {
                        callback.onProgress((float) written / (float) contentLength);
                    }
                }
            } finally {
                conn.disconnect();
            }
            unpackZip(zip.getAbsolutePath(), dir.getAbsolutePath());
            if (!zip.delete()) {
                zip.deleteOnExit();
            }
            callback.onCompleted();
        } catch (Exception e) {
            FileLog.e(e);
            callback.onError(e);
        }
    }

    public void deleteModel(String language) {
        Model removed = loadedModels.remove(language);
        if (removed != null) {
            try {
                removed.close();
            } catch (Throwable ignore) {
            }
        }
        File dir = new File(modelsDir, language);
        if (dir.exists()) {
            deleteDirectory(dir);
        }
    }

    // Распознать голосовое сообщение по пути к файлу. Вызывать с фонового потока.
    public void recognize(String path, String language, RecognitionCallback callback) {
        if (findModel(language) == null) {
            callback.onNotSupported(language);
            return;
        }
        if (!isDownloaded(language)) {
            callback.onNotDownloaded(language);
            return;
        }
        try {
            if (!loadedModels.containsKey(language)) {
                loadedModels.put(language, new Model(modelsDir + "/" + language));
            }
            Model model = loadedModels.get(language);
            final DevGramVoiceConverter.LazyPcmInputStream pcm =
                    DevGramVoiceConverter.extractAndConvertToPcm(path, false);
            // Частоту берём из РЕАЛЬНОГО выхода декодера (mono 16-бит), а не из контейнера —
            // иначе Vosk слышит не ту скорость и распознаёт «криво».
            float sampleRate = pcm.getOutputSampleRate();
            final Recognizer recognizer;
            try {
                recognizer = new Recognizer(model, sampleRate);
            } catch (Throwable e) {
                try {
                    pcm.close();
                } catch (Throwable ignore) {
                }
                throw e;
            }
            final AtomicBoolean resourcesClosed = new AtomicBoolean();
            RecognitionListener listener = new RecognitionListener() {
                @Override
                public void onPartialResult(String hypothesis) {
                }

                @Override
                public void onResult(String hypothesis) {
                    String text = extractText(hypothesis);
                    if (!TextUtils.isEmpty(text)) {
                        callback.onChunk(text);
                    }
                }

                @Override
                public void onFinalResult(String hypothesis) {
                    try {
                        callback.onCompleted(extractText(hypothesis));
                    } finally {
                        closeRecognitionResources(recognizer, pcm, resourcesClosed);
                    }
                }

                @Override
                public void onError(Exception e) {
                    FileLog.e(e);
                    try {
                        callback.onError(e);
                    } finally {
                        closeRecognitionResources(recognizer, pcm, resourcesClosed);
                    }
                }

                @Override
                public void onTimeout() {
                }
            };
            final boolean started;
            try {
                started = new SpeechStreamService(recognizer, pcm, sampleRate).start(listener);
            } catch (Throwable e) {
                closeRecognitionResources(recognizer, pcm, resourcesClosed);
                throw e;
            }
            if (!started) {
                closeRecognitionResources(recognizer, pcm, resourcesClosed);
                callback.onError(new IOException("Не удалось запустить Vosk"));
                return;
            }
        } catch (Throwable e) {
            FileLog.e(e);
            callback.onError(e instanceof Exception ? (Exception) e : new RuntimeException(e));
        }
    }

    private static void closeRecognitionResources(Recognizer recognizer,
                                                   DevGramVoiceConverter.LazyPcmInputStream pcm,
                                                   AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            recognizer.close();
        } catch (Throwable ignore) {
        }
        try {
            pcm.close();
        } catch (Throwable ignore) {
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(String json) {
        try {
            Map<String, Object> map = gson.fromJson(json, Map.class);
            Object text = map != null ? map.get("text") : null;
            return text != null ? text.toString() : "";
        } catch (Throwable ignore) {
            return "";
        }
    }

    // Распаковка zip модели со срезанием верхней папки (как у exteraGram).
    private static void unpackZip(String zipPath, String targetDir) throws IOException {
        File target = new File(targetDir);
        if (!target.exists()) {
            target.mkdirs();
        }
        byte[] buf = new byte[4096];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry = zis.getNextEntry();
            String root = entry != null ? entry.getName().split("/")[0] : null;
            while (entry != null) {
                if (root != null && entry.getName().equals(root + "/")) {
                    entry = zis.getNextEntry();
                    continue;
                }
                String name = root != null ? entry.getName().substring(root.length() + 1) : entry.getName();
                File outFile = new File(target, name);
                File parent = outFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                if (!entry.isDirectory()) {
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int read;
                        while ((read = zis.read(buf)) > 0) {
                            fos.write(buf, 0, read);
                        }
                    }
                }
                entry = zis.getNextEntry();
            }
        }
    }

    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        if (!file.delete()) {
            FileLog.e("DevGramVoiceRecognizer: failed to delete " + file.getAbsolutePath());
        }
    }
}

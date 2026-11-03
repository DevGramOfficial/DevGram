package org.telegram.messenger.devgram.plugins;

import android.util.LruCache;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Запуск статического анализа плагина в фоне, с кэшем по хешу и сравнением с прошлой версией. */
public final class PluginAnalyzer {

    public static final int STATUS_OK = 0;
    public static final int STATUS_UNAVAILABLE = 1;
    public static final int STATUS_ERROR = 2;
    public static final int STATUS_TIMEOUT = 3;
    public static final int STATUS_CRASHED = 4;
    public static final int STATUS_TOO_LARGE = 5;
    public static final int STATUS_MEMORY = 6;

    private static final long MAX_SIZE = 64L * 1024 * 1024;
    private static final long TIMEOUT_MS = 45_000;
    private static final String ANALYZER_BUILD = "2";

    public static final class Result {
        public final int status;
        public final PluginAnalysisReport report;
        /** Пункты, которых не было в установленной версии (для обновления), иначе пусто. */
        public final List<PluginAnalysisReport.Statement> newStatements;

        Result(int status, PluginAnalysisReport report, List<PluginAnalysisReport.Statement> newStatements) {
            this.status = status;
            this.report = report;
            this.newStatements = newStatements;
        }
    }

    public interface Callback {
        void onResult(Result result);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DevGramPluginAnalyzer");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final ExecutorService WATCHDOG = Executors.newCachedThreadPool();
    private static final LruCache<String, String> CACHE = new LruCache<>(16);

    private PluginAnalyzer() {
    }

    /**
     * @param source      исходник .plugin (или null для пакета)
     * @param packagePath путь к .dgplugin (или null для исходника)
     * @param previous    установленная версия того же плагина (путь к файлу) — для пометки нового; может быть null
     */
    public static Future<?> analyze(String source, String packagePath, String name, String previous, Callback callback) {
        return WATCHDOG.submit(() -> {
            Result result;
            Future<String> job = EXECUTOR.submit(() -> run(source, packagePath, name));
            try {
                String json = job.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (json == null) {
                    result = new Result(STATUS_TOO_LARGE, null, new ArrayList<>());
                } else {
                    PluginAnalysisReport report = PluginAnalysisReport.parse(json);
                    result = new Result(STATUS_OK, report, diff(report, previous));
                }
            } catch (java.util.concurrent.TimeoutException e) {
                job.cancel(true);
                result = new Result(STATUS_TIMEOUT, null, new ArrayList<>());
            } catch (OutOfMemoryError e) {
                job.cancel(true);
                FileLog.e(e);
                result = new Result(STATUS_MEMORY, null, new ArrayList<>());
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                FileLog.e(cause == null ? e : cause);
                result = new Result(cause instanceof AnalyzerUnavailableException ? STATUS_UNAVAILABLE : STATUS_CRASHED,
                        null, new ArrayList<>());
            } catch (Throwable e) {
                FileLog.e(e);
                result = new Result(STATUS_ERROR, null, new ArrayList<>());
            }
            final Result fResult = result;
            AndroidUtilities.runOnUIThread(() -> callback.onResult(fResult));
        });
    }

    private static List<PluginAnalysisReport.Statement> diff(PluginAnalysisReport report, String previous) {
        ArrayList<PluginAnalysisReport.Statement> out = new ArrayList<>();
        if (previous == null || !new java.io.File(previous).isFile()) {
            return out;
        }
        try {
            boolean isPackage = previous.endsWith(".dgplugin");
            String old = isPackage ? run(null, previous, null) : run(readText(previous), null, previous);
            if (old == null) return out;
            HashSet<String> ids = new HashSet<>();
            for (PluginAnalysisReport.Statement s : PluginAnalysisReport.parse(old).statements) ids.add(s.id);
            for (PluginAnalysisReport.Statement s : report.statements) {
                if (!ids.contains(s.id)) out.add(s);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return out;
    }

    private static String run(String source, String packagePath, String name) throws Exception {
        String key;
        if (packagePath != null) {
            java.io.File f = new java.io.File(packagePath);
            if (f.length() > MAX_SIZE) return null;
            key = "p_" + org.telegram.messenger.DevGramPackages.sha256File(f);
        } else {
            if (source == null) source = "";
            if (source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SIZE) return null;
            key = "s_" + org.telegram.messenger.DevGramPlugins.sha256(source);
        }
        String cached = CACHE.get(key);
        if (cached != null) return cached;
        cached = readDiskCache(key);
        if (cached != null) {
            CACHE.put(key, cached);
            return cached;
        }
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            }
        } catch (Throwable e) {
            throw new AnalyzerUnavailableException(e);
        }
        PyObject module = Python.getInstance().getModule("devgram_analyzer");
        String json;
        try (PyObject r = packagePath != null
                ? module.callAttr("analyze_package", packagePath)
                : module.callAttr("analyze_source", source, name == null ? "plugin.py" : name)) {
            json = r.toString();
        }
        CACHE.put(key, json);
        writeDiskCache(key, json);
        return json;
    }

    private static java.io.File cacheFile(String key) {
        java.io.File root = new java.io.File(ApplicationLoader.applicationContext.getCacheDir(), "plugin_analysis");
        if (!root.isDirectory() && !root.mkdirs()) return null;
        return new java.io.File(root, "v" + ANALYZER_BUILD + "_" + key + ".json");
    }

    private static String readDiskCache(String key) {
        try {
            java.io.File file = cacheFile(key);
            if (file == null || !file.isFile() || file.length() <= 0 || file.length() > 4 * 1024 * 1024) return null;
            return readText(file.getAbsolutePath());
        } catch (Throwable e) {
            return null;
        }
    }

    private static void writeDiskCache(String key, String json) {
        if (json == null || json.isEmpty()) return;
        java.io.File file = cacheFile(key);
        if (file == null) return;
        java.io.File temp = new java.io.File(file.getParentFile(), file.getName() + ".tmp");
        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(temp), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(json);
        } catch (Throwable e) {
            return;
        }
        if (!temp.renameTo(file)) {
            // На некоторых прошивках rename поверх существующего файла не разрешён.
            if (file.exists()) file.delete();
            temp.renameTo(file);
        }
    }

    private static String readText(String path) throws java.io.IOException {
        byte[] data = java.nio.file.Files.readAllBytes(new java.io.File(path).toPath());
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static final class AnalyzerUnavailableException extends Exception {
        AnalyzerUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}

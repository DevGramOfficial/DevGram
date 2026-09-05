package org.telegram.messenger;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

// DevGram: своя телеметрия БЕЗ Google-SDK (SDK ломали запуск — см. reference_devgram_no_firebase_sdk).
// Пишем анонимно в твой RTDB devgram-d03e4 через чистый REST, как значки/каталог.
//   Analytics (analyticsEnabled)  -> /analytics/installs/{iid} = {ver,build,android,model,lang,opens,last}
//   Crashlytics (crashlyticsEnabled) -> краш сохраняется в файл при падении, выгружается на след. старте
//                                       в /crashes/{iid}/{ts} = {ver,build,android,model,thread,trace}
// installId — случайный анонимный UUID (в prefs), НЕ привязан к Telegram-аккаунту.
public class DevGramTelemetry {

    private static final String RTDB = "https://devgram-d03e4-default-rtdb.europe-west1.firebasedatabase.app";
    private static final String PREFS = "devgram_telemetry";

    // Мастер-гейт сбора крашей (кил-свитч). true — краши собираются и отправляются, когда включён
    // тумблер «Отчёты о сбоях» (он ВКЛ по умолчанию). Поставить false, чтобы разом заглушить сбор.
    private static final boolean COLLECT_CRASHES = true;

    private static volatile boolean crashHandlerInstalled = false;
    private static volatile String installId;

    // Реально ли сейчас собираем краши: мастер-гейт И тумблер пользователя.
    private static boolean crashesActive() {
        return COLLECT_CRASHES && DevGramConfig.crashlyticsEnabled;
    }

    // Вызвать один раз на старте приложения (ApplicationLoader).
    public static void init() {
        installCrashHandler(); // ставим всегда (чтобы работало и после включения тумблера без перезапуска)
        startMemoryWatchdog();  // ловим утечки/раздувание памяти ДО OOM (снапшот в краш-отчёт)
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (crashesActive()) {
                    uploadPendingCrashes();
                }
                if (DevGramConfig.analyticsEnabled) {
                    logAppOpen(true);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // Вызывается при переключении тумблеров «Сбор данных».
    public static void onSettingsChanged() {
        installCrashHandler();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (DevGramConfig.analyticsEnabled) {
                    logAppOpen(false); // при включении — обновить запись без накрутки счётчика открытий
                }
                if (crashesActive()) {
                    uploadPendingCrashes();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    private static synchronized String installId() {
        if (installId != null) {
            return installId;
        }
        try {
            SharedPreferences p = prefs();
            String id = p.getString("iid", null);
            if (id == null) {
                id = java.util.UUID.randomUUID().toString().replace("-", "");
                p.edit().putString("iid", id).apply();
            }
            installId = id;
        } catch (Throwable e) {
            installId = "unknown";
        }
        return installId;
    }

    // Запись об установке/открытии. incrementOpens=true — считаем это открытием приложения.
    private static void logAppOpen(boolean incrementOpens) {
        try {
            SharedPreferences p = prefs();
            int opens = p.getInt("opens", 0);
            if (incrementOpens) {
                opens++;
                p.edit().putInt("opens", opens).apply();
            }
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("ver", verName());
            o.put("build", verCode());
            o.put("android", android.os.Build.VERSION.SDK_INT);
            o.put("model", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            o.put("lang", java.util.Locale.getDefault().getLanguage());
            o.put("opens", opens);
            o.put("last", System.currentTimeMillis());
            put(RTDB + "/analytics/installs/" + installId() + ".json", o.toString());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---- краши ----
    private static synchronized void installCrashHandler() {
        if (crashHandlerInstalled) {
            return;
        }
        crashHandlerInstalled = true;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                if (crashesActive()) {
                    saveCrashToDisk(t, e); // на диск синхронно — надёжнее, чем сеть в умирающем процессе
                }
            } catch (Throwable ignore) {
            }
            if (prev != null) {
                prev.uncaughtException(t, e); // цепочка — краш не глотаем, штатная обработка остаётся
            }
        });
    }

    private static java.io.File crashesDir() {
        java.io.File d = new java.io.File(ApplicationLoader.applicationContext.getCacheDir(), "devgram_crashes");
        d.mkdirs();
        return d;
    }

    private static void saveCrashToDisk(Thread t, Throwable e) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            // Разбивка памяти в начало трейса — для OOM это решающая улика (где сидит память:
            // java-куча, графика/битмапы или нативная), а стек OOM показывает лишь место
            // неудачной аллокации-жертвы, а не причину.
            String peak = readHighMem();
            String trace = "[mem] " + memorySummary()
                    + (peak.isEmpty() ? "" : "\n[mem-peak] " + peak)
                    + "\n\n" + sw.toString();
            if (trace.length() > 8000) {
                trace = trace.substring(0, 8000);
            }
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("ver", verName());
            o.put("build", verCode());
            o.put("android", android.os.Build.VERSION.SDK_INT);
            o.put("model", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            o.put("thread", t != null ? t.getName() : "?");
            o.put("trace", trace);
            o.put("ts", System.currentTimeMillis());
            java.io.File f = new java.io.File(crashesDir(), System.currentTimeMillis() + ".json");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();
        } catch (Throwable ignore) {
        }
    }

    // Краткая разбивка памяти на момент краша (всё в МБ). Для OOM показывает, что именно
    // держит кучу: java_used (объекты), native (нативные аллокации), graphics (битмапы/GPU).
    private static String memorySummary() {
        StringBuilder sb = new StringBuilder();
        try {
            Runtime rt = Runtime.getRuntime();
            long max = rt.maxMemory() / 1048576L;
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
            long nativeHeap = android.os.Debug.getNativeHeapAllocatedSize() / 1048576L;
            sb.append("java_used=").append(used).append("MB/").append(max).append("MB");
            sb.append(" native=").append(nativeHeap).append("MB");
            try {
                android.os.Debug.MemoryInfo mi = new android.os.Debug.MemoryInfo();
                android.os.Debug.getMemoryInfo(mi);
                sb.append(" graphics=").append(memStatMb(mi, "summary.graphics"));
                sb.append(" total_pss=").append(memStatMb(mi, "summary.total-pss"));
            } catch (Throwable ignore) {
            }
        } catch (Throwable e) {
            return "?";
        }
        return sb.toString();
    }

    private static String memStatMb(android.os.Debug.MemoryInfo mi, String key) {
        try {
            return (Long.parseLong(mi.getMemoryStat(key)) / 1024L) + "MB";
        } catch (Throwable e) {
            return "?";
        }
    }

    // ---- memory-watchdog: снимок при высокой памяти → в лог и в следующий краш-отчёт ----
    // OOM-стек показывает лишь место неудачной аллокации, а не что забило память. Watchdog
    // раз в 30с проверяет кучу/нативку и при высоком уровне сохраняет разбивку — она попадёт
    // в след. краш-отчёт как [mem-peak], и по ней видно, что растёт (java/native/graphics).
    private static volatile android.os.Handler memHandler;

    private static synchronized void startMemoryWatchdog() {
        if (memHandler != null) {
            return;
        }
        try {
            android.os.HandlerThread ht = new android.os.HandlerThread("dg-memwatch");
            ht.start();
            memHandler = new android.os.Handler(ht.getLooper());
            memHandler.postDelayed(memTick, 30000);
        } catch (Throwable ignore) {
        }
    }

    private static final Runnable memTick = new Runnable() {
        @Override
        public void run() {
            try {
                Runtime rt = Runtime.getRuntime();
                long max = rt.maxMemory();
                long used = rt.totalMemory() - rt.freeMemory();
                long nativeMb = android.os.Debug.getNativeHeapAllocatedSize() / 1048576L;
                boolean high = (max > 0 && used * 100L / max >= 85L) || nativeMb >= 1200L;
                if (high) {
                    String snap = memorySummary();
                    persistHighMem(snap);
                    FileLog.d("[DevGram-memwatch] ВЫСОКАЯ ПАМЯТЬ: " + snap);
                }
            } catch (Throwable ignore) {
            }
            android.os.Handler h = memHandler;
            if (h != null) {
                h.postDelayed(this, 30000);
            }
        }
    };

    private static java.io.File highMemFile() {
        return new java.io.File(crashesDir(), "lastmem.txt");
    }

    private static void persistHighMem(String snap) {
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(highMemFile());
            fos.write((System.currentTimeMillis() + " " + snap).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();
        } catch (Throwable ignore) {
        }
    }

    private static String readHighMem() {
        try {
            java.io.File f = highMemFile();
            if (!f.exists()) {
                return "";
            }
            String s = readFile(f);
            return s == null ? "" : s.trim();
        } catch (Throwable e) {
            return "";
        }
    }

    private static void uploadPendingCrashes() {
        try {
            java.io.File[] files = crashesDir().listFiles();
            if (files == null) {
                return;
            }
            for (java.io.File f : files) {
                if (!f.getName().endsWith(".json")) {
                    continue;
                }
                String body = readFile(f);
                if (body == null) {
                    f.delete();
                    continue;
                }
                String ts = f.getName().replace(".json", "");
                if (put(RTDB + "/crashes/" + installId() + "/" + ts + ".json", body)) {
                    f.delete();
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---- утилиты ----
    private static String verName() {
        try {
            PackageInfo pi = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            return pi.versionName;
        } catch (Throwable e) {
            return "?";
        }
    }

    private static int verCode() {
        try {
            PackageInfo pi = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            return pi.versionCode;
        } catch (Throwable e) {
            return 0;
        }
    }

    private static String readFile(java.io.File f) {
        try {
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            int off = 0, r;
            while (off < data.length && (r = fis.read(data, off, data.length - off)) > 0) {
                off += r;
            }
            fis.close();
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }

    // PUT в RTDB (без auth — узлы analytics/crashes открыты на запись, как streaks). true при 2xx.
    private static boolean put(String url, String body) {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setRequestMethod("PUT");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            java.io.OutputStream os = c.getOutputStream();
            os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            os.close();
            int code = c.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}

package org.telegram.messenger;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// DevGram: отчёты о сбоях по системной причине завершения процесса (ApplicationExitInfo, Android 11+).
// Ловит то, что Java-обработчик не видит: нативные краши (SIGSEGV/SIGABRT в .so), ANR, сбои
// инициализации — и в том числе случившиеся посреди работы, а не только на старте. Для нативного
// краша система отдаёт tombstone (protobuf): сигнал, адрес сбоя, backtrace с функциями/.so/BuildId
// и последние строки лога процесса. Из logcat доступны только строки нашего uid — этого хватает
// и на лог процесса, и на бэктрейс от crash_dump (он работает под uid упавшего приложения).
// Каждый сбой отправляется в RTDB один раз (помним timestamp последнего отправленного).
public final class DevGramExitReport {

    private static final String PREFS = "devgram_exit_reports";
    private static final String KEY_LAST_REPORTED = "last_reported_ts";
    private static final int MAX_REPORTS_PER_START = 5;
    private static final int MAX_TRACE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_SECTION_CHARS = 100_000;
    private static final int LOG_TAIL_LINES = 150;
    private static final int OTHER_THREAD_FRAMES = 4;

    private static volatile boolean started;

    private DevGramExitReport() {
    }

    // Вызывать один раз на старте (DevGramTelemetry.init). Работает в своём потоке.
    public static void reportPendingAsync() {
        if (Build.VERSION.SDK_INT < 30 || started) {
            return;
        }
        started = true;
        Thread t = new Thread(() -> {
            try {
                reportPending();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }, "DevGramExitReport");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void reportPending() {
        if (!DevGramTelemetry.crashesActive()) {
            return;
        }
        SharedPreferences prefs = prefs();
        long last = prefs.getLong(KEY_LAST_REPORTED, 0L);
        if (last == 0L) {
            // первый запуск с новым сборщиком — не вываливаем всю историю, только последние сутки
            last = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        }
        List<ApplicationExitInfo> pending = new ArrayList<>();
        for (ApplicationExitInfo exit : exits(16)) {
            if (exit.getTimestamp() > last && isFailure(exit)) {
                pending.add(exit);
            }
        }
        Collections.reverse(pending); // от старых к новым — чтобы last_reported двигался монотонно
        int sent = 0;
        for (ApplicationExitInfo exit : pending) {
            if (sent >= MAX_REPORTS_PER_START) {
                break;
            }
            String report = buildReport(exit);
            if (!DevGramTelemetry.uploadReport(report)) {
                break; // нет сети — попробуем при следующем запуске
            }
            prefs.edit().putLong(KEY_LAST_REPORTED, exit.getTimestamp()).apply();
            sent++;
        }
    }

    // Описание последнего сбоя после указанного момента — для диалога «приложение упало».
    public static String describeLatestFailure(long since) {
        if (Build.VERSION.SDK_INT < 30) {
            return "";
        }
        for (ApplicationExitInfo exit : exits(8)) {
            if (exit.getTimestamp() + 2000 < since) {
                break;
            }
            if (isFailure(exit)) {
                return describe(exit);
            }
        }
        return "";
    }

    private static List<ApplicationExitInfo> exits(int max) {
        try {
            Context c = ApplicationLoader.applicationContext;
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                return am.getHistoricalProcessExitReasons(c.getPackageName(), 0, max);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return Collections.emptyList();
    }

    private static boolean isFailure(ApplicationExitInfo exit) {
        switch (exit.getReason()) {
            case ApplicationExitInfo.REASON_CRASH:
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
            case ApplicationExitInfo.REASON_ANR:
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return true;
            case ApplicationExitInfo.REASON_SIGNALED:
                // Нативный краш, когда сигнал перехватили в процессе и debuggerd не снял tombstone:
                // система пишет SIGNALED со статусом-сигналом (так выглядели краши 12.10.6).
                // SIGKILL (9) — обычное убийство системой, не сбой.
                return isFatalSignal(exit.getStatus());
            default:
                return false;
        }
    }

    private static boolean isFatalSignal(int sig) {
        return sig == 4 || sig == 5 || sig == 6 || sig == 7 || sig == 8 || sig == 11 || sig == 31;
    }

    private static String signalName(int sig) {
        switch (sig) {
            case 4: return "SIGILL";
            case 5: return "SIGTRAP";
            case 6: return "SIGABRT";
            case 7: return "SIGBUS";
            case 8: return "SIGFPE";
            case 9: return "SIGKILL";
            case 11: return "SIGSEGV";
            case 31: return "SIGSYS";
            default: return "сигнал " + sig;
        }
    }

    private static String buildReport(ApplicationExitInfo exit) {
        StringBuilder sb = new StringBuilder();
        sb.append("===== DevGram: отчёт о сбое =====\n");
        sb.append("устройство: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (").append(Build.DEVICE).append(")\n");
        sb.append("Android:    ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT)
                .append("), ").append(Build.FINGERPRINT).append('\n');
        sb.append("ABI:        ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?").append('\n');
        sb.append("версия:     ").append(currentVersion()).append(" — текущая; сбой мог быть в предыдущей\n\n");
        sb.append(describe(exit));
        return sb.toString();
    }

    private static String describe(ApplicationExitInfo exit) {
        StringBuilder sb = new StringBuilder();
        sb.append("причина:    ").append(reasonName(exit.getReason()));
        if (exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE
                || exit.getReason() == ApplicationExitInfo.REASON_SIGNALED) {
            sb.append(" (").append(signalName(exit.getStatus())).append(')');
        }
        sb.append('\n');
        if (exit.getDescription() != null && !exit.getDescription().isEmpty()) {
            sb.append("описание:   ").append(exit.getDescription()).append('\n');
        }
        sb.append("время:      ").append(formatTime(exit.getTimestamp())).append('\n');
        sb.append("процесс:    ").append(exit.getProcessName()).append(" pid=").append(exit.getPid())
                .append(", важность=").append(exit.getImportance()).append('\n');
        sb.append("память:     pss=").append(exit.getPss() / 1024).append("MB rss=").append(exit.getRss() / 1024).append("MB\n");

        String trace = readTrace(exit);
        if (exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE) {
            sb.append("\n===== нативный сбой (tombstone) =====\n");
            if (trace == null) {
                sb.append(Build.VERSION.SDK_INT < 31
                        ? "(tombstone недоступен: нужен Android 12+)\n"
                        : "(система не сохранила tombstone)\n");
            } else {
                sb.append(trace);
            }
        } else if (exit.getReason() == ApplicationExitInfo.REASON_ANR) {
            sb.append("\n===== ANR: дамп потоков =====\n");
            sb.append(trace != null ? trace : "(дамп потоков недоступен)\n");
        } else if (exit.getReason() == ApplicationExitInfo.REASON_CRASH) {
            String javaTrace = javaTraceNear(exit.getTimestamp());
            sb.append("\n===== Java-исключение =====\n");
            sb.append(javaTrace != null ? javaTrace : "(стек не сохранён — см. описание выше)\n");
        } else if (exit.getReason() == ApplicationExitInfo.REASON_SIGNALED) {
            sb.append("\n===== нативный сбой без tombstone =====\n")
                    .append("(сигнал перехвачен внутри процесса, debuggerd дамп не снял — смотри лог ниже:\n")
                    .append(" строка «Fatal signal …» и что происходило перед сбоем)\n");
        }
        // Лог упавшего процесса: приложению доступны собственные логи (тот же uid), в том числе
        // прошлого процесса, пока они в буфере. Для SIGNALED это единственный источник подробностей.
        // (в tombstone на части прошивок, напр. Samsung, буфер лога пустой — поэтому всегда)
        // Бэктрейс нативного сбоя пишет crash_dump под СВОИМ pid (тег DEBUG), поэтому в лог процесса
        // по --pid он не попадает. Берём его отдельно: блок DEBUG, где упомянут pid упавшего процесса.
        if (exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE || exit.getReason() == ApplicationExitInfo.REASON_SIGNALED) {
            String dump = crashDumpLogcat(exit.getPid());
            if (!dump.isEmpty()) {
                sb.append("\n===== вывод crash_dump (backtrace) =====\n").append(dump);
            }
        }
        {
            String processLog = processLogcat(exit.getPid());
            if (!processLog.isEmpty()) {
                sb.append("\n===== лог процесса pid=").append(exit.getPid()).append(" =====\n").append(processLog);
            }
        }
        // Бэктрейс нативного сбоя пишет crash_dump (тег DEBUG) из СВОЕГО pid, но под нашим uid —
        // по --pid его не видно, поэтому ищем блок, где упомянут pid упавшего процесса.
        if (exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE || exit.getReason() == ApplicationExitInfo.REASON_SIGNALED) {
            String dump = crashDumpLog(exit.getPid());
            if (!dump.isEmpty()) {
                sb.append("\n===== бэктрейс (crash_dump) =====\n").append(dump);
            }
        }
        return sb.toString();
    }

    // Нативный: tombstone в protobuf → текст. ANR: готовый текстовый дамп (main-поток — первым).
    private static String readTrace(ApplicationExitInfo exit) {
        byte[] data;
        try (InputStream in = exit.getTraceInputStream()) {
            if (in == null) {
                return null;
            }
            data = readAll(in);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
        if (data.length == 0) {
            return null;
        }
        try {
            if (exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                try {
                    return Tombstone.format(data);
                } catch (Throwable parseError) {
                    FileLog.e(parseError);
                    return "(структурный разбор не удался: " + parseError + ")\n" + rawStrings(data) + hexHead(data);
                }
            }
            return mainThreadFirst(new String(data, StandardCharsets.UTF_8));
        } catch (Throwable e) {
            FileLog.e(e);
            return "(не удалось разобрать трейс: " + e + ")\n";
        }
    }

    // Запасной разбор tombstone: все читаемые UTF-8 строки (имена .so, функций, BuildId, лог).
    private static String rawStrings(byte[] data) {
        StringBuilder out = new StringBuilder("----- строки из tombstone -----\n");
        int start = -1;
        for (int i = 0; i <= data.length; i++) {
            boolean printable = i < data.length && (data[i] >= 0x20 && data[i] < 0x7f || (data[i] & 0x80) != 0);
            if (printable) {
                if (start < 0) start = i;
            } else if (start >= 0) {
                if (i - start >= 6) {
                    out.append(new String(data, start, i - start, StandardCharsets.UTF_8)).append('\n');
                    if (out.length() > MAX_SECTION_CHARS) break;
                }
                start = -1;
            }
        }
        return out.toString();
    }

    private static String hexHead(byte[] data) {
        StringBuilder out = new StringBuilder("----- hex начала (").append(data.length).append(" байт) -----\n");
        for (int i = 0; i < Math.min(256, data.length); i++) {
            out.append(String.format(Locale.US, "%02x", data[i] & 0xff)).append(i % 32 == 31 ? '\n' : ' ');
        }
        return out.append('\n').toString();
    }

    private static String mainThreadFirst(String anr) {
        int start = anr.indexOf("\n\"main\"");
        String result = anr;
        if (start >= 0) {
            int end = anr.indexOf("\n\n", start + 1);
            String main = end > start ? anr.substring(start + 1, end) : anr.substring(start + 1);
            result = main + "\n\n----- остальные потоки -----\n" + anr;
        }
        return clip(result);
    }

    private static String crashDumpLogcat(int pid) {
        String raw = runLogcat(new String[]{"logcat", "-d", "-v", "threadtime", "-b", "crash,main", "-s", "DEBUG:I"});
        java.util.LinkedHashMap<String, StringBuilder> byDumper = new java.util.LinkedHashMap<>();
        java.util.HashSet<String> matched = new java.util.HashSet<>();
        String marker = "pid: " + pid + ",";
        for (String line : raw.split("\n")) {
            String[] cols = line.trim().split("\\s+", 4);
            if (cols.length < 4) continue;
            String dumper = cols[2]; // pid процесса crash_dump
            StringBuilder b = byDumper.get(dumper);
            if (b == null) byDumper.put(dumper, b = new StringBuilder());
            if (b.length() < 64 * 1024) b.append(line).append('\n');
            if (line.contains(marker)) matched.add(dumper);
        }
        StringBuilder out = new StringBuilder();
        for (String d : matched) out.append(byDumper.get(d));
        return out.toString();
    }

    private static String runLogcat(String[] cmd) {
        StringBuilder out = new StringBuilder();
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);
            final Process fp = p;
            Thread killer = new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    fp.destroy();
                } catch (Throwable ignored) {
                }
            });
            killer.setDaemon(true);
            killer.start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            killer.interrupt();
        } catch (Throwable ignored) {
        } finally {
            if (p != null) p.destroy();
        }
        return out.toString();
    }

    private static String crashDumpLog(int pid) {
        java.util.LinkedHashMap<String, StringBuilder> byDumper = new java.util.LinkedHashMap<>();
        String needle = "pid: " + pid + ",";
        String found = null;
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime", "-b", "crash,main",
                    "-t", "3000", "-s", "DEBUG:*"});
            final Process fp = p;
            Thread killer = new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    fp.destroy();
                } catch (Throwable ignored) {
                }
            });
            killer.setDaemon(true);
            killer.start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    // формат threadtime: дата время pid tid уровень тег: текст — группируем по pid crash_dump
                    String[] parts = line.trim().split("\\s+", 4);
                    if (parts.length < 4) continue;
                    String dumper = parts[2];
                    StringBuilder b = byDumper.get(dumper);
                    if (b == null) {
                        b = new StringBuilder();
                        byDumper.put(dumper, b);
                    }
                    if (b.length() < 64 * 1024) b.append(line).append('\n');
                    if (line.contains(needle)) found = dumper;
                }
            }
            killer.interrupt();
        } catch (Throwable e) {
            return "(logcat недоступен: " + e + ")\n";
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
        if (found == null) {
            return "(crash_dump не оставил бэктрейс в доступном логе)\n";
        }
        return byDumper.get(found).toString();
    }

    private static String processLogcat(int pid) {
        StringBuilder out = new StringBuilder();
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime",
                    "--pid=" + pid, "-t", "600"});
            final Process fp = p;
            Thread killer = new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    fp.destroy();
                } catch (Throwable ignored) {
                }
            });
            killer.setDaemon(true);
            killer.start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains("Kumiho-Kumiho")) {
                        continue; // спам прошивки Samsung вытесняет полезное
                    }
                    out.append(line).append('\n');
                }
            }
            killer.interrupt();
        } catch (Throwable e) {
            return "(logcat недоступен: " + e + ")\n";
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
        String s = out.toString();
        // берём хвост: самое важное — перед сбоем
        return s.length() > MAX_SECTION_CHARS ? "… (начало обрезано)\n" + s.substring(s.length() - MAX_SECTION_CHARS) : s;
    }

    // Java-стек ловит наш обработчик (DevGramPlugins) и кладёт в prefs вместе со временем.
    private static String javaTraceNear(long exitTs) {
        try {
            SharedPreferences p = ApplicationLoader.applicationContext.getSharedPreferences("devgram_plugins", Context.MODE_PRIVATE);
            String trace = p.getString("last_crash", null);
            long ts = p.getLong("last_crash_time", 0L);
            if (trace != null && !trace.isEmpty() && Math.abs(ts - exitTs) < 15_000) {
                return clip(trace) + '\n';
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static String currentVersion() {
        try {
            Context c = ApplicationLoader.applicationContext;
            android.content.pm.PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionName + " (" + pi.getLongVersionCode() + ")";
        } catch (Throwable e) {
            return BuildVars.BUILD_VERSION_STRING;
        }
    }

    private static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_CRASH: return "CRASH (Java)";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR: return "ANR (приложение зависло)";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_SIGNALED: return "SIGNALED";
            default: return "reason=" + reason;
        }
    }

    private static String formatTime(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(ts));
    }

    private static String clip(String s) {
        return s.length() > MAX_SECTION_CHARS ? s.substring(0, MAX_SECTION_CHARS) + "\n… (обрезано)\n" : s;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > MAX_TRACE_BYTES) {
                break;
            }
        }
        return out.toByteArray();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- tombstone.proto (system/core/debuggerd/proto) → текст в духе debuggerd ----
    private static final class Tombstone {
        String fingerprint, abortMessage;
        int pid, tid;
        final List<String> commandLine = new ArrayList<>();
        final List<String> causes = new ArrayList<>();
        int signalNumber, signalCode;
        String signalName, signalCodeName;
        boolean hasFaultAddress;
        long faultAddress;
        final List<Thread> threads = new ArrayList<>();
        final List<String> logs = new ArrayList<>();

        static final class Thread {
            int id;
            String name;
            final List<String> frames = new ArrayList<>();
            final List<String> notes = new ArrayList<>();
        }

        static String format(byte[] data) {
            Tombstone t = new Tombstone();
            t.parse(new Pb(data, 0, data.length));
            return t.toText();
        }

        void parse(Pb pb) {
            while (pb.hasMore()) {
                int tag = pb.tag();
                switch (tag >>> 3) {
                    case 2: if (pb.wt(tag, 2)) fingerprint = pb.string(); break;
                    case 5: if (pb.wt(tag, 0)) pid = (int) pb.varint(); break;
                    case 6: if (pb.wt(tag, 0)) tid = (int) pb.varint(); break;
                    case 9: if (pb.wt(tag, 2)) commandLine.add(pb.string()); break;
                    case 10: if (pb.wt(tag, 2)) parseSignal(pb.message()); break;
                    case 14: if (pb.wt(tag, 2)) abortMessage = pb.string(); break;
                    case 15: if (pb.wt(tag, 2)) parseCause(pb.message()); break;
                    case 16: if (pb.wt(tag, 2)) parseThreadEntry(pb.message()); break;
                    case 18: if (pb.wt(tag, 2)) parseLogBuffer(pb.message()); break;
                    default: pb.skip(tag);
                }
            }
        }

        void parseSignal(Pb pb) {
            while (pb.hasMore()) {
                int tag = pb.tag();
                switch (tag >>> 3) {
                    case 1: if (pb.wt(tag, 0)) signalNumber = (int) pb.varint(); break;
                    case 2: if (pb.wt(tag, 2)) signalName = pb.string(); break;
                    case 3: if (pb.wt(tag, 0)) signalCode = (int) pb.varint(); break;
                    case 4: if (pb.wt(tag, 2)) signalCodeName = pb.string(); break;
                    case 8: if (pb.wt(tag, 0)) hasFaultAddress = pb.varint() != 0; break;
                    case 9: if (pb.wt(tag, 0)) faultAddress = pb.varint(); break;
                    default: pb.skip(tag);
                }
            }
        }

        void parseCause(Pb pb) {
            while (pb.hasMore()) {
                int tag = pb.tag();
                if ((tag >>> 3) == 1 && pb.wt(tag, 2)) {
                    causes.add(pb.string());
                } else {
                    pb.skip(tag);
                }
            }
        }

        void parseThreadEntry(Pb pb) { // map<uint32, Thread>: 1 = key, 2 = value
            while (pb.hasMore()) {
                int tag = pb.tag();
                if ((tag >>> 3) == 2 && pb.wt(tag, 2)) {
                    threads.add(parseThread(pb.message()));
                } else {
                    pb.skip(tag);
                }
            }
        }

        Thread parseThread(Pb pb) {
            Thread th = new Thread();
            while (pb.hasMore()) {
                int tag = pb.tag();
                switch (tag >>> 3) {
                    case 1: if (pb.wt(tag, 0)) th.id = (int) pb.varint(); break;
                    case 2: if (pb.wt(tag, 2)) th.name = pb.string(); break;
                    case 4: if (pb.wt(tag, 2)) th.frames.add(parseFrame(pb.message(), th.frames.size())); break;
                    case 7: if (pb.wt(tag, 2)) th.notes.add(pb.string()); break;
                    default: pb.skip(tag);
                }
            }
            return th;
        }

        String parseFrame(Pb pb, int index) {
            long relPc = 0, fnOffset = 0;
            String fn = null, file = null, buildId = null;
            while (pb.hasMore()) {
                int tag = pb.tag();
                switch (tag >>> 3) {
                    case 1: if (pb.wt(tag, 0)) relPc = pb.varint(); break;
                    case 4: if (pb.wt(tag, 2)) fn = pb.string(); break;
                    case 5: if (pb.wt(tag, 0)) fnOffset = pb.varint(); break;
                    case 6: if (pb.wt(tag, 2)) file = pb.string(); break;
                    case 8: if (pb.wt(tag, 2)) buildId = pb.string(); break;
                    default: pb.skip(tag);
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "  #%02d pc %016x  %s", index, relPc, file != null ? file : "?"));
            if (fn != null && !fn.isEmpty()) {
                sb.append(" (").append(fn).append('+').append(fnOffset).append(')');
            }
            if (buildId != null && !buildId.isEmpty()) {
                sb.append(" (BuildId: ").append(buildId).append(')');
            }
            return sb.toString();
        }

        void parseLogBuffer(Pb pb) {
            String bufferName = "";
            while (pb.hasMore()) {
                int tag = pb.tag();
                switch (tag >>> 3) {
                    case 1: if (pb.wt(tag, 2)) bufferName = pb.string(); break;
                    case 2: if (pb.wt(tag, 2)) logs.add(parseLog(pb.message(), bufferName)); break;
                    default: pb.skip(tag);
                }
            }
        }

        String parseLog(Pb pb, String buffer) {
            String ts = "", tagName = "", msg = "";
            long logPid = 0, logTid = 0, prio = 0;
            while (pb.hasMore()) {
                int tag = pb.tag();
                switch (tag >>> 3) {
                    case 1: if (pb.wt(tag, 2)) ts = pb.string(); break;
                    case 2: if (pb.wt(tag, 0)) logPid = pb.varint(); break;
                    case 3: if (pb.wt(tag, 0)) logTid = pb.varint(); break;
                    case 4: if (pb.wt(tag, 0)) prio = pb.varint(); break;
                    case 5: if (pb.wt(tag, 2)) tagName = pb.string(); break;
                    case 6: if (pb.wt(tag, 2)) msg = pb.string(); break;
                    default: pb.skip(tag);
                }
            }
            String p = prio >= 2 && prio <= 7 ? String.valueOf("VDIWEF".charAt((int) prio - 2)) : "?";
            return ts + " " + logPid + " " + logTid + " " + p + " " + tagName + ": " + msg.trim();
        }

        String toText() {
            StringBuilder sb = new StringBuilder();
            if (fingerprint != null) sb.append("Build fingerprint: ").append(fingerprint).append('\n');
            if (!commandLine.isEmpty()) sb.append("Cmdline: ").append(String.join(" ", commandLine)).append('\n');
            sb.append("pid: ").append(pid).append(", tid: ").append(tid).append('\n');
            sb.append("signal ").append(signalNumber).append(" (").append(signalName).append("), code ")
                    .append(signalCode).append(" (").append(signalCodeName).append(')');
            if (hasFaultAddress) {
                sb.append(String.format(Locale.US, ", fault addr 0x%x", faultAddress));
            }
            sb.append('\n');
            if (abortMessage != null && !abortMessage.isEmpty()) {
                sb.append("Abort message: '").append(abortMessage).append("'\n");
            }
            for (String cause : causes) {
                sb.append("Cause: ").append(cause).append('\n');
            }
            Thread crashed = null;
            for (Thread th : threads) {
                if (th.id == tid) {
                    crashed = th;
                    break;
                }
            }
            if (crashed != null) {
                sb.append("\nупавший поток: \"").append(crashed.name).append("\" tid=").append(crashed.id).append('\n');
                for (String note : crashed.notes) sb.append("  NOTE: ").append(note).append('\n');
                for (String f : crashed.frames) sb.append(f).append('\n');
            }
            if (threads.size() > 1) {
                sb.append("\n----- остальные потоки (верх стека) -----\n");
                int shown = 0;
                for (Thread th : threads) {
                    if (th == crashed) continue;
                    if (shown++ >= 40) {
                        sb.append("… ещё ").append(threads.size() - 41).append(" потоков\n");
                        break;
                    }
                    sb.append('"').append(th.name).append("\" tid=").append(th.id).append('\n');
                    for (int i = 0; i < Math.min(OTHER_THREAD_FRAMES, th.frames.size()); i++) {
                        sb.append(th.frames.get(i)).append('\n');
                    }
                }
            }
            if (!logs.isEmpty()) {
                sb.append("\n----- лог процесса перед сбоем (последние ").append(Math.min(LOG_TAIL_LINES, logs.size())).append(") -----\n");
                for (int i = Math.max(0, logs.size() - LOG_TAIL_LINES); i < logs.size(); i++) {
                    sb.append(logs.get(i)).append('\n');
                }
            }
            return clip(sb.toString());
        }
    }

    // Минимальный protobuf-ридер: varint, length-delimited, пропуск остальных типов.
    private static final class Pb {
        private final byte[] b;
        private int pos;
        private final int end;

        Pb(byte[] b, int pos, int end) {
            this.b = b;
            this.pos = pos;
            this.end = end;
        }

        boolean hasMore() {
            return pos < end;
        }

        int tag() {
            return (int) varint();
        }

        // Совпал ли wire type поля с ожидаемым. Нет — поле пропускается целиком (поле из более
        // новой/вендорской схемы не должно сбивать разбор остального tombstone).
        boolean wt(int tag, int expected) {
            if ((tag & 7) == expected) {
                return true;
            }
            skip(tag);
            return false;
        }

        long varint() {
            long result = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= end) throw new IllegalStateException("varint за границей");
                byte x = b[pos++];
                result |= (long) (x & 0x7F) << shift;
                if ((x & 0x80) == 0) return result;
            }
            throw new IllegalStateException("битый varint");
        }

        private int length() {
            long len = varint();
            if (len < 0 || len > end - pos) throw new IllegalStateException("длина за границей");
            return (int) len;
        }

        String string() {
            int len = length();
            String s = new String(b, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }

        Pb message() {
            int len = length();
            Pb sub = new Pb(b, pos, pos + len);
            pos += len;
            return sub;
        }

        void skip(int tag) {
            switch (tag & 7) {
                case 0: varint(); break;
                case 1: pos += 8; break;
                case 2: {
                    // не «pos += length()»: pos читается ДО вызова, а length() сам двигает pos
                    int len = length();
                    pos += len;
                    break;
                }
                case 5: pos += 4; break;
                default: throw new IllegalStateException("неизвестный wire type " + (tag & 7));
            }
            if (pos > end) throw new IllegalStateException("пропуск за границей");
        }
    }
}

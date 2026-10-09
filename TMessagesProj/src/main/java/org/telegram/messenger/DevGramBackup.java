package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

// DevGram: облачная копия настроек для поддержавших. Файл .dgbackup уходит в «Избранное» самого
// пользователя (наш сервер не участвует) и переживает смену телефона. Внутри — настройки DevGram,
// фильтры, состояние плагинов, ключи DevGram из общих настроек и папка плагинов с их настройками.
// Ключи доступа и сессии в копию не попадают.
public final class DevGramBackup {

    public static final String EXT = ".dgbackup";
    public static final String TAG = "#devgram_backup";
    private static final int FORMAT = 1;
    private static final String[] PREFS = {"devgram_ghost", "devgram_filters", "devgram_plugins"};
    private static final String[] MAIN_KEYS = {"folderTabsStyle", "formatWithSeconds", "stickerSize", "useSystemEmoji"};

    public interface Done { void run(String error); }

    private DevGramBackup() {
    }

    private static boolean skipKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("token") || k.contains("session") || k.startsWith("last_crash");
    }

    private static boolean mainKey(String key) {
        if (key.startsWith("dg_")) return true;
        for (String k : MAIN_KEYS) if (k.equals(key)) return true;
        return false;
    }

    public static long lastBackupTime() {
        return MessagesController.getGlobalMainSettings().getLong("devgramLastBackup", 0);
    }

    // ---------- сохранение ----------

    public static void save(int account, Done done) {
        Utilities.globalQueue.postRunnable(() -> {
            File file;
            try {
                file = buildArchive(account);
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> done.run("Не удалось собрать копию"));
                return;
            }
            final File f = file;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    long self = UserConfig.getInstance(account).getClientUserId();
                    String caption = TAG + "\nКопия настроек DevGram " + new SimpleDateFormat("d MMM yyyy, HH:mm", new Locale("ru")).format(new Date())
                            + ".\nВосстановить: Настройки → DevGram → Поддержавшим → «Восстановить из облака».";
                    SendMessagesHelper.prepareSendingDocument(AccountInstance.getInstance(account), f.getAbsolutePath(), f.getAbsolutePath(),
                            null, caption, "application/zip", self, null, null, null, null, null, false, 0, null, null, false);
                    MessagesController.getGlobalMainSettings().edit().putLong("devgramLastBackup", System.currentTimeMillis()).apply();
                    done.run(null);
                } catch (Throwable e) {
                    FileLog.e(e);
                    done.run("Не удалось отправить копию");
                }
            });
        });
    }

    private static File buildArchive(int account) throws Exception {
        Context ctx = ApplicationLoader.applicationContext;
        String name = "DevGram-настройки-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date()) + EXT;
        File out = new File(ctx.getCacheDir(), name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            JSONObject meta = new JSONObject();
            meta.put("format", FORMAT);
            meta.put("created", System.currentTimeMillis());
            meta.put("versionName", BuildVars.BUILD_VERSION_STRING);
            meta.put("uid", UserConfig.getInstance(account).getClientUserId());
            putText(zip, "meta.json", meta.toString());
            for (String prefs : PREFS) {
                putText(zip, "prefs/" + prefs + ".json", dump(ctx.getSharedPreferences(prefs, Context.MODE_PRIVATE), false).toString());
            }
            putText(zip, "main.json", dump(MessagesController.getGlobalMainSettings(), true).toString());
            File plugins = DevGramPlugins.pluginsDir();
            addDir(zip, plugins, "plugins/");
        }
        return out;
    }

    private static JSONObject dump(SharedPreferences p, boolean onlyMain) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            if (skipKey(k) || onlyMain && !mainKey(k)) continue;
            Object v = e.getValue();
            JSONObject t = new JSONObject();
            if (v instanceof Boolean) t.put("t", "b");
            else if (v instanceof Integer) t.put("t", "i");
            else if (v instanceof Long) t.put("t", "l");
            else if (v instanceof Float) t.put("t", "f");
            else if (v instanceof String) t.put("t", "s");
            else if (v instanceof Set) {
                t.put("t", "ss");
                JSONArray a = new JSONArray();
                for (Object s : (Set<?>) v) a.put(String.valueOf(s));
                t.put("v", a);
                o.put(k, t);
                continue;
            } else continue;
            t.put("v", v);
            o.put(k, t);
        }
        return o;
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void addDir(ZipOutputStream zip, File dir, String prefix) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        byte[] buf = new byte[1 << 16];
        for (File f : files) {
            if (f.getName().equals("__pycache__")) continue;
            if (f.isDirectory()) {
                addDir(zip, f, prefix + f.getName() + "/");
            } else {
                zip.putNextEntry(new ZipEntry(prefix + f.getName()));
                try (InputStream in = new FileInputStream(f)) {
                    int n;
                    while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
                }
                zip.closeEntry();
            }
        }
    }

    // ---------- восстановление ----------

    // Ищем последнюю копию в «Избранном», качаем через Telegram и применяем.
    public static void restore(int account, Done done) {
        MessagesController mc = MessagesController.getInstance(account);
        long self = UserConfig.getInstance(account).getClientUserId();
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = mc.getInputPeer(self);
        req.q = TAG;
        req.filter = new TLRPC.TL_inputMessagesFilterDocument();
        req.limit = 10;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            TLRPC.Message found = null;
            if (response instanceof TLRPC.messages_Messages) {
                for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                    TLRPC.Document d = m.media != null ? m.media.document : null;
                    if (d != null && FileLoader.getDocumentFileName(d).endsWith(EXT)) {
                        if (found == null || m.date > found.date) found = m;
                    }
                }
            }
            if (found == null) {
                done.run("В «Избранном» нет копии настроек — сначала сохраните её");
                return;
            }
            download(account, found, done);
        }));
    }

    private static void download(int account, TLRPC.Message message, Done done) {
        TLRPC.Document doc = message.media.document;
        FileLoader loader = FileLoader.getInstance(account);
        File ready = loader.getPathToAttach(doc, true);
        if (ready != null && ready.exists() && ready.length() == doc.size) {
            applyAsync(ready, done);
            return;
        }
        String fileName = FileLoader.getAttachFileName(doc);
        NotificationCenter nc = NotificationCenter.getInstance(account);
        NotificationCenter.NotificationCenterDelegate[] holder = new NotificationCenter.NotificationCenterDelegate[1];
        holder[0] = (id, acc, args) -> {
            if (args.length == 0 || !fileName.equals(args[0])) return;
            if (id == NotificationCenter.fileLoaded || id == NotificationCenter.fileLoadFailed) {
                nc.removeObserver(holder[0], NotificationCenter.fileLoaded);
                nc.removeObserver(holder[0], NotificationCenter.fileLoadFailed);
                if (id == NotificationCenter.fileLoaded && args.length > 1 && args[1] instanceof File) {
                    applyAsync((File) args[1], done);
                } else {
                    done.run("Не удалось скачать копию");
                }
            }
        };
        nc.addObserver(holder[0], NotificationCenter.fileLoaded);
        nc.addObserver(holder[0], NotificationCenter.fileLoadFailed);
        loader.loadFile(doc, new MessageObject(account, message, false, false), FileLoader.PRIORITY_HIGH, 1);
    }

    private static void applyAsync(File file, Done done) {
        Utilities.globalQueue.postRunnable(() -> {
            String error = null;
            try {
                apply(file);
            } catch (Throwable e) {
                FileLog.e(e);
                error = "Копия повреждена или от несовместимой версии";
            }
            final String fError = error;
            AndroidUtilities.runOnUIThread(() -> done.run(fError));
        });
    }

    private static void apply(File file) throws Exception {
        Context ctx = ApplicationLoader.applicationContext;
        File pluginsDir = DevGramPlugins.pluginsDir();
        String pluginsRoot = pluginsDir.getCanonicalPath() + File.separator;
        boolean metaOk = false;
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory()) continue;
                byte[] data = readAll(zip);
                if (name.equals("meta.json")) {
                    metaOk = new JSONObject(new String(data, StandardCharsets.UTF_8)).optInt("format") == FORMAT;
                } else if (name.startsWith("prefs/") && name.endsWith(".json")) {
                    String prefs = name.substring(6, name.length() - 5);
                    if (!java.util.Arrays.asList(PREFS).contains(prefs)) continue;
                    load(ctx.getSharedPreferences(prefs, Context.MODE_PRIVATE), new JSONObject(new String(data, StandardCharsets.UTF_8)), false);
                } else if (name.equals("main.json")) {
                    load(MessagesController.getGlobalMainSettings(), new JSONObject(new String(data, StandardCharsets.UTF_8)), true);
                } else if (name.startsWith("plugins/")) {
                    File target = new File(pluginsDir, name.substring(8));
                    // только внутрь папки плагинов (защита от путей вида ../)
                    if (!target.getCanonicalPath().startsWith(pluginsRoot)) continue;
                    File parent = target.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        out.write(data);
                    }
                }
            }
        }
        if (!metaOk) throw new IllegalStateException("bad meta");
    }

    private static void load(SharedPreferences p, JSONObject o, boolean onlyMain) throws Exception {
        SharedPreferences.Editor ed = p.edit();
        for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            if (skipKey(k) || onlyMain && !mainKey(k)) continue;
            JSONObject t = o.getJSONObject(k);
            switch (t.optString("t")) {
                case "b": ed.putBoolean(k, t.getBoolean("v")); break;
                case "i": ed.putInt(k, t.getInt("v")); break;
                case "l": ed.putLong(k, t.getLong("v")); break;
                case "f": ed.putFloat(k, (float) t.getDouble("v")); break;
                case "s": ed.putString(k, t.getString("v")); break;
                case "ss": {
                    JSONArray a = t.getJSONArray("v");
                    Set<String> s = new HashSet<>();
                    for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                    ed.putStringSet(k, s);
                    break;
                }
            }
        }
        ed.commit();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // Настройки DevGram читаются при запуске — после восстановления приложение перезапускается
    public static void restartApp(Activity activity) {
        try {
            Intent i = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(i);
            }
        } catch (Throwable ignore) {
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}

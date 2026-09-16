/*
 * DevGram: мост Java <-> Python для системы плагинов (Chaquopy).
 * Плагины — .py-файлы в папке Android/data/<pkg>/files/plugins (её видно в файловом
 * менеджере — туда разработчик кладёт свои плагины). Каждый плагин — наследник
 * devgram.BasePlugin. Загрузка на старте, хуки дёргаются из клиента при событиях.
 */

package org.telegram.messenger;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DevGramPlugins {

    private static PyObject loaderModule;
    private static volatile boolean loaded;
    private static volatile boolean loading;
    private static volatile boolean loadRetryScheduled;
    private static volatile boolean wantsUpdatesFlag; // хоть один плагин подписан на TL-апдейты

    // Защита от «кирпича»: если прошлый запуск упал — авто-безопасный режим + ошибка в буфер.
    private static volatile boolean crashHandlerInstalled;
    private static volatile boolean pendingCrashNotice; // показать уведомление о крэше, когда появится активность

    // Реестр проверенных плагинов: множество SHA-256 доверенных исходников (из облака).
    private static final String RTDB = "https://devgram-d03e4-default-rtdb.europe-west1.firebasedatabase.app";
    // DevGram: реестр проверенных плагинов держит ПОСТОЯННЫЙ SSE-стрим — он занимал одно из 100
    // бесплатных соединений Firebase на каждом клиенте. Переехал на свой push-сервис (лимита нет),
    // формат REST/SSE совместим (тот же /plugins_verified.json и события put), сервер зеркалит запись
    // в Firebase для старых клиентов. Каталог/отзывы (разовые запросы) остаются на RTDB.
    private static final String VERIFIED_BASE = "https://api.devgram.space";
    private static final String FIREBASE_API_KEY = "AIzaSyAj-Fq-7707X54Yr8t51mFAkJmCLEKtYoU";

    // Отдельный пул потоков под сетевые запросы DevGram (каталог/бейджи/модерация и т.п.),
    // чтобы НЕ делить общий Utilities.globalQueue — это ОДИН поток на всё приложение, и на
    // холодном старте наши HTTP-запросы вставали в очередь ЗА прогревом ядра Telegram, а
    // ещё и друг за другом (fetchFilters/fetchCatalogFast/fetchModerators выполнялись
    // последовательно вместо параллельно) — из-за этого каталог плагинов открывался очень
    // долго. Свой пул с несколькими потоками решает обе проблемы разом.
    private static final java.util.concurrent.ExecutorService NET_QUEUE = java.util.concurrent.Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "DevGramNet");
        t.setDaemon(true);
        return t;
    });
    private static volatile String firebaseAnonToken;
    private static volatile String firebaseAnonUid;
    private static volatile java.util.Set<String> verifiedHashes = new java.util.HashSet<>();

    public static boolean startDevServer(int port, String token) { return DevGramDevServer.start(port, token); }
    public static void stopDevServer() { DevGramDevServer.stopServer(); }
    public static boolean isDevServerRunning() { return DevGramDevServer.isRunning(); }
    public static final int DEV_SERVER_PORT = 42690;

    /** Random local credential, persisted only in the app's private preferences. */
    public static String devServerToken() {
        String token = prefs().getString("dev_server_token", "");
        if (token == null || token.length() < 24) {
            token = generateDevServerToken();
            prefs().edit().putString("dev_server_token", token).apply();
        }
        return token;
    }

    /** Rotate the local credential and restart an enabled dev server with the new token. */
    public static synchronized String resetDevServerToken() {
        boolean shouldRestart = flag("dev_server", false) || isDevServerRunning();
        String token = generateDevServerToken();
        prefs().edit().putString("dev_server_token", token).commit();
        stopDevServer();
        if (shouldRestart && !isSafeMode()) {
            startDevServer(DEV_SERVER_PORT, token);
        }
        return token;
    }

    private static String generateDevServerToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static boolean setDevServerEnabled(boolean enabled) {
        if (enabled && isSafeMode()) {
            return false;
        }
        setFlag("dev_server", enabled);
        if (!enabled) {
            stopRemoteDebugger();
            stopDevServer();
            return true;
        }
        return startDevServer(DEV_SERVER_PORT, devServerToken());
    }

    public static String startRemoteDebugger(String platform, String host, int port) {
        try {
            loader();
            return Python.getInstance().getModule("devgram.dev_server")
                    .callAttr("start_debugger", platform, host, port, false).toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return "error:" + e.getClass().getSimpleName();
        }
    }

    public static String stopRemoteDebugger() {
        try {
            Python.getInstance().getModule("devgram.dev_server").callAttr("stop_debugger");
            return "stopped";
        } catch (Throwable e) {
            FileLog.e(e);
            return "error:" + e.getClass().getSimpleName();
        }
    }

    public static String remoteDebuggerStatus() {
        try {
            return Python.getInstance().getModule("devgram.dev_server").callAttr("debugger_status").toString();
        } catch (Throwable e) {
            return "unavailable";
        }
    }

    // Пример-плагин, который кладём при первом запуске (чтобы было что показать).
    private static final String SAMPLE = String.join("\n",
            "from devgram import BasePlugin",
            "",
            "class HelloPlugin(BasePlugin):",
            "    id = \"hello\"",
            "    name = \"Привет DevGram\"",
            "    version = \"1.0\"",
            "    author = \"DevGram\"",
            "    description = \"Пример: :love: -> сердечко в исходящих\"",
            "",
            "    def on_load(self):",
            "        self.log(\"пример-плагин загружен\")",
            "",
            "    def on_send_message(self, text):",
            "        return text.replace(\":love:\", \"❤️\").replace(\":shrug:\", \"¯\\\\_(ツ)_/¯\")",
            "");

    // true, если hello.py совпадает с исходным демо (его можно спокойно удалить). Если
    // пользователь переписал файл под тем же именем — вернёт false, и мы его не тронем.
    private static boolean isUntouchedSample(File f) {
        try {
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try {
                int off = 0, r;
                while (off < data.length && (r = in.read(data, off, data.length - off)) > 0) {
                    off += r;
                }
            } finally {
                in.close();
            }
            String content = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            return content.trim().equals(SAMPLE.trim());
        } catch (Throwable e) {
            return false;
        }
    }

    // Папка плагинов (внешняя files-папка — видна в файловом менеджере разработчику).
    public static File pluginsDir() {
        Context ctx = ApplicationLoader.applicationContext;
        File base = ctx.getExternalFilesDir(null);
        if (base == null) {
            base = ctx.getFilesDir();
        }
        File dir = new File(base, "plugins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static PyObject loader() {
        if (loaderModule == null) {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            }
            loaderModule = Python.getInstance().getModule("devgram_plugins");
        }
        return loaderModule;
    }

    // ===== Встроенный WebSocket-прокси (dgws.py) — локальный, без внешнего сервера =====
    public static boolean startWsProxy(String secretHex, String fakeTlsDomain, String host, int port) {
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            }
            Object r = Python.getInstance().getModule("dgws")
                    .callAttr("start", secretHex, fakeTlsDomain, host, port);
            return r != null && ((PyObject) r).toBoolean();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void stopWsProxy() {
        try {
            if (Python.isStarted()) {
                Python.getInstance().getModule("dgws").callAttr("stop");
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static String wsProxyLastError() {
        try {
            if (!Python.isStarted()) {
                return "python не запущен";
            }
            Object r = Python.getInstance().getModule("dgws").callAttr("last_error");
            return r == null ? "" : r.toString();
        } catch (Throwable e) {
            return "мост: " + e;
        }
    }

    public static boolean isWsProxyRunning() {
        try {
            if (!Python.isStarted()) {
                return false;
            }
            Object r = Python.getInstance().getModule("dgws").callAttr("is_running");
            return r != null && ((PyObject) r).toBoolean();
        } catch (Throwable e) {
            return false;
        }
    }

    // Загрузить все плагины (один раз на старте). Тяжёлое — звать в фоне.
    public static synchronized void loadAll() {
        if (loaded || loading) {
            return;
        }
        loading = true;

        // ---- защита от «кирпича» ----
        installCrashHandler();
        // если прошлый запуск не завершился штатно (boot_pending остался) ИЛИ был пойман краш —
        // значит приложение упало → принудительно включаем безопасный режим (хуки не привязываются)
        boolean prevCrash = prefs().getBoolean("boot_pending", false) || prefs().getBoolean("crashed", false);
        if (prevCrash) {
            setFlag("safe_mode", true);
            pendingCrashNotice = true;
        }
        prefs().edit().putBoolean("crashed", false).putBoolean("boot_pending", true).apply();

        fetchVerified();        // быстрый первый снимок реестра
        fetchBlocked();         // блок-лист запрещённых к публикации плагинов
        // Раньше держали постоянный SSE-стрим (1 соединение/устройство → упор в лимит Firebase
        // 100 одновременных). Заменили на периодический опрос — короткие GET'ы соединение не держат.
        scheduleRegistryPoll();
        initHooks();            // инициализируем Pine до загрузки плагинов (on_load может хукать)
        try {
            File dir = pluginsDir();
            // Раньше при первом запуске сюда клали демо-плагин hello.py («Привет DevGram»).
            // Больше не кладём и один раз подчищаем его у тех, у кого он уже лежит — но ТОЛЬКО
            // если файл не редактировали (содержимое совпадает с исходным SAMPLE), чтобы не
            // удалить плагин, который пользователь мог переписать под себя под тем же именем.
            File sample = new File(dir, "hello.py");
            if (sample.exists() && isUntouchedSample(sample)) {
                //noinspection ResultOfMethodCallIgnored
                sample.delete();
            }
            int n = loader().callAttr("load_dir", dir.getAbsolutePath()).toInt();
            applySavedEnabled();    // восстановить вкл/выкл, сохранённые пользователем
            refreshWantsUpdates();
            refreshRequestHooks();  // повесить хук TL-запросов, если плагины их используют
            loaded = true;
            loading = false;
            if (flag("dev_mode", false) && !isSafeMode()) {
                setDevServerEnabled(true);
            }
            FileLog.d("[DevGramPlugins] загружено плагинов: " + n + " из " + dir.getAbsolutePath());
        } catch (Throwable e) {
            FileLog.e(e);
            // Chaquopy/файловая система могут быть ещё не готовы после аварийного запуска.
            // Оставляем loaded=false, чтобы повторная попытка действительно восстановила реестр.
            loaded = false;
            loading = false;
            if (!loadRetryScheduled) {
                loadRetryScheduled = true;
                Utilities.globalQueue.postRunnable(() -> {
                    loadRetryScheduled = false;
                    loadAll();
                }, 2000);
            }
        }
    }

    // Хук исходящего текста — вернуть, возможно, изменённый текст.
    public static CharSequence onSendMessage(int account, CharSequence text) {
        if (text == null || !loaded || isSafeMode()) {
            return text;
        }
        try {
            PyObject r = loader().callAttr("dispatch_send", account, text.toString());
            return r == null ? text : r.toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return text;
        }
    }

    public static void onReceiveMessage(CharSequence text) {
        if (text == null || !loaded || isSafeMode()) {
            return;
        }
        try {
            loader().callAttr("dispatch_receive", text.toString());
        } catch (Throwable ignore) {
        }
    }

    // Список плагинов для менеджера: строки id␟name␟version␟author␟enabled.
    // DevGram: общий лог плагинов (буфер в Python-загрузчике) — экран «Логи плагинов».
    public static void logLine(String msg) {
        if (msg == null || msg.isEmpty()) {
            return;
        }
        try {
            loader().callAttr("dg_log", msg);
        } catch (Throwable ignore) {
        }
    }

    public static String pluginLogs() {
        try {
            String s = loader().callAttr("get_logs").toString();
            return s == null ? "" : s;
        } catch (Throwable e) {
            return "";
        }
    }

    public static void clearPluginLogs() {
        try {
            loader().callAttr("clear_logs");
        } catch (Throwable ignore) {
        }
    }

    public static List<String> listPlugins() {
        List<String> res = new ArrayList<>();
        if (!loaded) {
            loadAll();
        }
        try {
            PyObject list = loader().callAttr("list_plugins");
            for (PyObject o : list.asList()) {
                res.add(o.toString());
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    public static void setEnabled(String id, boolean enabled) {
        try {
            prefs().edit().putBoolean("enabled_" + id, enabled).apply(); // персистим состояние
            loader().callAttr("set_enabled", id, enabled);
            refreshWantsUpdates();
            refreshRequestHooks();
            logLine((enabled ? "включён плагин: " : "выключен плагин: ") + id);
        } catch (Throwable ignore) {
        }
    }

    // Применить сохранённые пользователем состояния вкл/выкл к загруженным плагинам.
    private static void applySavedEnabled() {
        try {
            for (String s : listPlugins()) {
                String[] f = s.split("", -1);
                String id = f.length > 0 ? f[0] : "";
                if (id.isEmpty()) {
                    continue;
                }
                boolean en = prefs().getBoolean("enabled_" + id, true);
                if (!en) {
                    loader().callAttr("set_enabled", id, false);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    // ===== настройки системы плагинов (флаги) =====

    private static android.content.SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("devgram_plugins", Context.MODE_PRIVATE);
    }

    public static boolean flag(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    public static void setFlag(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    // Безопасный режим: плагины не выполняют хуки (для отладки проблемного плагина).
    public static boolean isSafeMode() {
        return flag("safe_mode", false);
    }

    public static String pythonVersion() {
        try {
            return loader().callAttr("python_version").toString();
        } catch (Throwable e) {
            return "3.11";
        }
    }

    // ===== клиентское API для плагинов (вызывается из Python) =====

    public static int registerPill(String pluginId, String pillId, String name, String text,
                                   com.chaquo.python.PyObject valueCallback,
                                   com.chaquo.python.PyObject clickCallback,
                                   com.chaquo.python.PyObject longClickCallback,
                                   boolean enabled) {
        return DevGramPillStack.registerPluginPill(pluginId, pillId, name, text, valueCallback, clickCallback, longClickCallback, enabled);
    }

    public static void unregisterPills(String pluginId) {
        DevGramPillStack.unregisterPluginPills(pluginId);
    }

    // ---- вкладка плагина в панели Эмодзи/GIF/Стикеры (EmojiView.TAB_PLUGIN) ----
    public static void registerPanelTab(String pluginId, String title, com.chaquo.python.PyObject buildView, com.chaquo.python.PyObject actionClick, String iconPath) {
        DevGramPanelTabs.register(pluginId, title, buildView, actionClick, iconPath);
    }

    public static void unregisterPanelTab(String pluginId) {
        DevGramPanelTabs.unregister(pluginId);
    }

    public static List<String> panelTabPluginIds() {
        // DevGram: register_panel_tab() зовётся один раз в on_load() и не отписывается при
        // выключении плагина тумблером (set_enabled лишь ставит флаг p.enabled, сам плагин
        // остаётся «загружен» — тот же принцип, что у menu_items()/пилюль). Поэтому фильтруем
        // тут же, по факту чтения списка, а не полагаемся на unregisterPanelTab().
        List<String> res = new ArrayList<>();
        for (String id : DevGramPanelTabs.pluginIds()) {
            if (prefs().getBoolean("enabled_" + id, true)) {
                res.add(id);
            }
        }
        return res;
    }

    public static String panelTabTitle(String pluginId) {
        return DevGramPanelTabs.title(pluginId);
    }

    public static android.view.View buildPanelTabView(String pluginId, android.content.Context context, long dialogId) {
        if (!prefs().getBoolean("enabled_" + pluginId, true)) {
            return null;
        }
        return DevGramPanelTabs.buildView(pluginId, context, dialogId);
    }

    public static boolean panelTabHasAction(String pluginId) {
        return pluginId != null && prefs().getBoolean("enabled_" + pluginId, true) && DevGramPanelTabs.hasAction(pluginId);
    }

    public static void panelTabActionClick(String pluginId, android.view.View anchor, long dialogId) {
        if (pluginId == null || !prefs().getBoolean("enabled_" + pluginId, true)) {
            return;
        }
        DevGramPanelTabs.triggerAction(pluginId, anchor, dialogId);
    }

    public static android.graphics.drawable.Drawable buildPanelTabIcon(String pluginId, android.content.Context context) {
        if (pluginId == null || !prefs().getBoolean("enabled_" + pluginId, true)) {
            return null;
        }
        return DevGramPanelTabs.icon(pluginId, context);
    }

    public static long myId() {
        return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    // Отправить текстовое сообщение в диалог.
    public static void sendMessage(long dialogId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                int account = UserConfig.selectedAccount;
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                        text, dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false);
                SendMessagesHelper.getInstance(account).sendMessage(params);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // DevGram: то же самое, но с готовым списком entities (жирный текст/текстовые ссылки
    // и т.п.) — плагин строит их сам через jclass на TLRPC.TL_messageEntity* классы, тут
    // просто прокидываем список в SendMessageParams.of вместо null.
    public static void sendMessageEntities(long dialogId, String text, ArrayList<TLRPC.MessageEntity> entities) {
        if (text == null || text.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                int account = UserConfig.selectedAccount;
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                        text, dialogId, null, null, null, true, entities, null, null, true, 0, 0, null, false);
                SendMessagesHelper.getInstance(account).sendMessage(params);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // DevGram: обновить био/"о себе" ТЕКУЩЕГО аккаунта — тот же TL-запрос, что и штатный
    // ChangeBioActivity (account.updateProfile, flags|=4 под about). Нужен плагинам, которые
    // транслируют "сейчас играет" в профиль (см. reSwaga "Стрим в профиль").
    public static void updateProfileAbout(String about) {
        if (about == null) return;
        try {
            int account = UserConfig.selectedAccount;
            org.telegram.tgnet.tl.TL_account.updateProfile req = new org.telegram.tgnet.tl.TL_account.updateProfile();
            req.about = about;
            req.flags |= 4;
            org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                if (error != null) {
                    FileLog.e("DevGram updateProfileAbout: " + error.text);
                }
            });
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Короткое всплывающее уведомление (тост).
    public static void toast(String text) {
        if (text == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                android.widget.Toast.makeText(ApplicationLoader.applicationContext, text, android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignore) {
            }
        });
    }

    // Персистентные настройки плагина (ключ-значение).
    public static String pluginGet(String pluginId, String key, String def) {
        return prefs().getString("p_" + pluginId + "_" + key, def);
    }

    public static void pluginSet(String pluginId, String key, String value) {
        prefs().edit().putString("p_" + pluginId + "_" + key, value).apply();
    }

    // Маркер отмены отправки (плагин вернул False из on_send_message).
    public static final String CANCEL = "\u0000DEVGRAM_CANCEL\u0000";

    public static String userName(long uid) {
        try {
            org.telegram.tgnet.TLRPC.User u = MessagesController.getInstance(UserConfig.selectedAccount).getUser(uid);
            return u != null ? String.valueOf(UserObject.getUserName(u)) : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public static String chatName(long cid) {
        try {
            org.telegram.tgnet.TLRPC.Chat c = MessagesController.getInstance(UserConfig.selectedAccount).getChat(cid < 0 ? -cid : cid);
            return c != null && c.title != null ? c.title : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public static int currentAccount() {
        return UserConfig.selectedAccount;
    }

    // ---- client_utils: доступ к контроллерам (текущий аккаунт) ----
    public static Object accountInstance()      { return AccountInstance.getInstance(UserConfig.selectedAccount); }
    public static Object messagesController()    { return MessagesController.getInstance(UserConfig.selectedAccount); }
    public static Object connectionsManager()    { return org.telegram.tgnet.ConnectionsManager.getInstance(UserConfig.selectedAccount); }
    public static Object userConfig()            { return UserConfig.getInstance(UserConfig.selectedAccount); }
    public static Object sendMessagesHelper()    { return SendMessagesHelper.getInstance(UserConfig.selectedAccount); }
    public static Object mediaDataController()   { return MediaDataController.getInstance(UserConfig.selectedAccount); }
    public static Object contactsController()    { return ContactsController.getInstance(UserConfig.selectedAccount); }
    public static Object messagesStorage()       { return MessagesStorage.getInstance(UserConfig.selectedAccount); }
    public static Object notificationCenter()    { return NotificationCenter.getInstance(UserConfig.selectedAccount); }
    public static Object fileLoader()            { return FileLoader.getInstance(UserConfig.selectedAccount); }
    public static Object accountInstance(int account) { return AccountInstance.getInstance(account); }
    public static Object messagesController(int account) { return MessagesController.getInstance(account); }
    public static Object userConfig(int account) { return UserConfig.getInstance(account); }
    public static Object connectionsManager(int account) { return org.telegram.tgnet.ConnectionsManager.getInstance(account); }
    public static Object sendMessagesHelper(int account) { return SendMessagesHelper.getInstance(account); }
    public static Object mediaDataController(int account) { return MediaDataController.getInstance(account); }
    public static Object contactsController(int account) { return ContactsController.getInstance(account); }
    public static Object messagesStorage(int account) { return MessagesStorage.getInstance(account); }
    public static Object notificationCenter(int account) { return NotificationCenter.getInstance(account); }
    public static Object fileLoader(int account) { return FileLoader.getInstance(account); }
    public static Object mediaController() { return MediaController.getInstance(); }
    public static Object notificationsController(int account) { return NotificationsController.getInstance(account); }
    public static Object notificationsSettings(int account) { return MessagesController.getNotificationsSettings(account); }
    public static Object locationController(int account) { return LocationController.getInstance(account); }
    public static Object secretChatHelper(int account) { return SecretChatHelper.getInstance(account); }
    public static Object downloadController(int account) { return DownloadController.getInstance(account); }
    public static void sendText(int account, long dialogId, String text) {
        if (text == null || text.isEmpty()) return;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(text, dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false);
                SendMessagesHelper.getInstance(account).sendMessage(params);
            } catch (Throwable e) { FileLog.e(e); }
        });
    }

    public static void sendFormattedText(int account, long dialogId, String text, ArrayList<org.telegram.tgnet.TLRPC.MessageEntity> entities) {
        if (text == null || text.isEmpty()) return;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                        text, dialogId, null, null, null, true, entities, null, null,
                        true, 0, 0, null, false);
                SendMessagesHelper.getInstance(account).sendMessage(params);
            } catch (Throwable e) { FileLog.e(e); }
        });
    }

    // DEBUG: прогоняет файл через те же проверки, что делает
    // SendMessagesHelper.prepareSendingDocumentInternal ПЕРЕД отправкой (exists/length/
    // isInternalUri) — тот код асинхронный и глотает причину отказа молча (просто
    // показывает нейтральный «Вложение не поддерживается»), а тут можно увидеть точную
    // причину до вызова send_photo/send_file.
    public static String debugCheckAttachment(String path) {
        try {
            if (path == null || path.isEmpty()) return "path is null/empty";
            java.io.File f = new java.io.File(path);
            StringBuilder sb = new StringBuilder();
            sb.append("path=").append(path).append("\n");
            sb.append("exists=").append(f.exists()).append("\n");
            sb.append("length=").append(f.length()).append("\n");
            sb.append("canonicalPath=");
            try {
                sb.append(f.getCanonicalPath());
            } catch (Throwable e) {
                sb.append("<error: ").append(e).append(">");
            }
            sb.append("\n");
            sb.append("isInternalUri=").append(AndroidUtilities.isInternalUri(android.net.Uri.fromFile(f))).append("\n");
            sb.append("packageName=").append(ApplicationLoader.applicationContext.getPackageName());
            return sb.toString();
        } catch (Throwable e) {
            return "debugCheckAttachment crashed: " + e;
        }
    }

    // ---- client_utils: богатая отправка ----
    // Отправить фото из файла (path — абсолютный путь), с подписью.
    public static void sendPhoto(long dialogId, String path, String caption) {
        sendPhotoEntities(dialogId, path, caption, null);
    }

    // DevGram: то же, что sendPhoto, но с entities для подписи (жирный текст/текстовые ссылки).
    public static void sendPhotoEntities(long dialogId, String path, String caption, ArrayList<TLRPC.MessageEntity> entities) {
        if (path == null || path.isEmpty()) return;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                AccountInstance ai = AccountInstance.getInstance(UserConfig.selectedAccount);
                // 12.9.7: 17-арг сигнатура (…, int payStars, SendMessageChatArguments)
                SendMessagesHelper.prepareSendingPhoto(ai, path, null, dialogId, null, null, null,
                        caption, entities, null, null, 0, null, true, 0, 0, null);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // Отправить файл из пути (универсально: видео/аудио/документ — Telegram сам определит тип по mime).
    public static void sendFile(long dialogId, String path, String caption) {
        if (path == null || path.isEmpty()) return;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                AccountInstance ai = AccountInstance.getInstance(UserConfig.selectedAccount);
                // 12.9.7: 17-арг (…, String mime, String caption, long dialogId, …, InputContentInfoCompat, SendMessageChatArguments, boolean)
                SendMessagesHelper.prepareSendingDocument(ai, path, path, null, null, caption, dialogId,
                        null, null, null, null, null, true, 0, null, null, false);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // Отправить текст с ответом на сообщение (replyToMsgId — id сообщения в этом же диалоге; 0 = без ответа).
    public static void sendMessageReply(long dialogId, String text, int replyToMsgId) {
        if (text == null || text.isEmpty()) return;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                int account = UserConfig.selectedAccount;
                MessageObject reply = null;
                if (replyToMsgId != 0) {
                    org.telegram.tgnet.TLRPC.Message m = MessagesStorage.getInstance(account).getMessage(dialogId, replyToMsgId);
                    if (m != null) {
                        reply = new MessageObject(account, m, false, true);
                    }
                }
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                        text, dialogId, reply, null, null, true, null, null, null, true, 0, 0, null, false);
                SendMessagesHelper.getInstance(account).sendMessage(params);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // Отредактировать текст своего сообщения (через TL messages.editMessage).
    public static void editMessageText(long dialogId, int messageId, String newText) {
        if (newText == null) return;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                int account = UserConfig.selectedAccount;
                org.telegram.tgnet.TLRPC.TL_messages_editMessage req = new org.telegram.tgnet.TLRPC.TL_messages_editMessage();
                req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
                req.id = messageId;
                req.message = newText;
                req.flags |= 2048; // message
                org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                    if (error != null) {
                        FileLog.e("[DevGramPlugins] editMessage: " + error.text);
                    }
                });
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // Сырой TL-запрос: request — Java-объект TLObject (плагин строит через jclass),
    // callback (может быть null) зовётся с (response, errorText). Возвращает токен запроса.
    public static int sendRequest(final Object request, final com.chaquo.python.PyObject callback) {
        try {
            if (!(request instanceof org.telegram.tgnet.TLObject)) {
                return 0;
            }
            int account = UserConfig.selectedAccount;
            return org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest((org.telegram.tgnet.TLObject) request,
                    (response, error) -> {
                        if (callback == null) return;
                        try {
                            callback.call(response, error != null ? error.text : null);
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    });
        } catch (Throwable e) {
            FileLog.e(e);
            return 0;
        }
    }

    // ---- android_utils: потоки ----
    // Выполнить Python-функцию на UI-потоке (delayMs — задержка в мс).
    public static void runOnUi(final com.chaquo.python.PyObject fn, long delayMs) {
        if (fn == null) return;
        Runnable r = () -> {
            try {
                fn.call();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        };
        if (delayMs > 0) {
            AndroidUtilities.runOnUIThread(r, delayMs);
        } else {
            AndroidUtilities.runOnUIThread(r);
        }
    }

    // Выполнить Python-функцию в фоновой очереди (не блокирует UI).
    public static void runOnQueue(final com.chaquo.python.PyObject fn) {
        if (fn == null) return;
        NET_QUEUE.execute(() -> {
            try {
                fn.call();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // ---- android_utils: графика ----
    // Chaquopy-мост при вызове конструктора new Canvas(bitmap) НАПРЯМУЮ из Python иногда
    // резолвит внутренний системный конструктор Canvas(long) вместо публичного
    // Canvas(Bitmap) — тот на Android 9+ заблокирован политикой скрытых (non-SDK) API
    // (NoSuchMethodError: Canvas.<init>(J)V). Обычный вызов из скомпилированного Java-кода
    // (как здесь) этой проблеме не подвержен — javac разрешает перегрузку на этапе
    // компиляции, без участия рефлексии/hidden-API рантайма.
    public static android.graphics.Canvas newCanvas(android.graphics.Bitmap bitmap) {
        return new android.graphics.Canvas(bitmap);
    }

    // FileOutputStream(String) — та же беда: у класса есть скрытый пакетный конструктор
    // FileOutputStream(FileDescriptor, boolean), и Chaquopy при вызове new FileOutputStream(path)
    // из Python иногда резолвит именно его вместо публичного FileOutputStream(String) —
    // NoSuchMethodError на несуществующий (для вызывающего) не-static метод. Обычный вызов
    // из скомпилированного Java-кода перегрузку разрешает на этапе компиляции корректно.
    public static java.io.FileOutputStream newFileOutputStream(String path) throws java.io.FileNotFoundException {
        return new java.io.FileOutputStream(path);
    }

    // Paint.setColor существует в двух перегрузках: setColor(int) и setColor(long)
    // (вторая добавлена в API 26 для wide-gamut Color.pack()). Вызов через Chaquopy
    // с обычным Python int иногда резолвит long-перегрузку, которая трактует число
    // как упакованный цвет и лезет за ColorSpace по ID из младших битов — падает
    // IllegalArgumentException: Invalid ID: N. Обычный вызов из скомпилированного
    // Java-кода разрешает перегрузку на этапе компиляции и этой проблеме не подвержен.
    public static void paintSetColor(android.graphics.Paint paint, int color) {
        paint.setColor(color);
    }

    // Рисующие методы Canvas (drawRect/drawCircle/drawBitmap/drawText и т.д.) на
    // современном Android фактически объявлены в скрытом суперклассе BaseCanvas,
    // а не в самом Canvas. Обычный компилированный вызов canvas.drawRect(...) это
    // не замечает (invoke-virtual резолвится на этапе компиляции), а Chaquopy при
    // обращении из Python ищет метод рефлексией по классу-объявителю и упирается
    // в политику скрытых API (тот же класс проблемы, что и с Canvas(Bitmap) и
    // Paint.setColor выше). Поэтому весь набор рисующих операций плагинам отдаём
    // через явные обёртки из скомпилированного Java.
    public static void canvasDrawRect(android.graphics.Canvas c, float left, float top, float right, float bottom, android.graphics.Paint paint) {
        c.drawRect(left, top, right, bottom, paint);
    }

    public static void canvasDrawRoundRect(android.graphics.Canvas c, android.graphics.RectF rect, float rx, float ry, android.graphics.Paint paint) {
        c.drawRoundRect(rect, rx, ry, paint);
    }

    public static void canvasDrawCircle(android.graphics.Canvas c, float cx, float cy, float radius, android.graphics.Paint paint) {
        c.drawCircle(cx, cy, radius, paint);
    }

    public static void canvasDrawBitmap(android.graphics.Canvas c, android.graphics.Bitmap bitmap, float left, float top, android.graphics.Paint paint) {
        c.drawBitmap(bitmap, left, top, paint);
    }

    public static void canvasDrawText(android.graphics.Canvas c, String text, float x, float y, android.graphics.Paint paint) {
        c.drawText(text, x, y, paint);
    }

    public static void canvasClipPath(android.graphics.Canvas c, android.graphics.Path path) {
        c.clipPath(path);
    }

    public static void canvasSave(android.graphics.Canvas c) {
        c.save();
    }

    public static void canvasRestore(android.graphics.Canvas c) {
        c.restore();
    }

    // ---- android_utils: буфер обмена ----
    public static void copyToClipboard(String text) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("devgram", text));
                }
            } catch (Throwable ignore) {
            }
        });
    }

    public static String getClipboard() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                return t != null ? t.toString() : "";
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    // ---- file_utils: личная папка плагина ----
    private static File pluginDataDir(String pluginId) {
        String safe = pluginId == null ? "x" : pluginId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        File d = new File(pluginsDir().getParentFile(), "plugin_data/" + safe);
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static String readData(String pluginId, String name) {
        try {
            File f = new File(pluginDataDir(pluginId), name.replaceAll("[^a-zA-Z0-9_.\\-]", "_"));
            // DevGram: лимит размера хранилища плагина снят (оставлен разумный потолок от OOM).
            if (!f.exists() || f.length() > 256L * 1024 * 1024) {
                return "";
            }
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            int off = 0, r;
            while (off < data.length && (r = fis.read(data, off, data.length - off)) > 0) {
                off += r;
            }
            fis.close();
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return "";
        }
    }

    // Абсолютный путь к личной папке плагина (внешнее хранилище приложения, НЕ /data/data/...).
    // Нужен плагинам, которые сами пишут бинарные файлы (например, рендерят картинку) и потом
    // отправляют их через send_photo/send_file: tempfile.mkstemp() из Python-песочницы Chaquopy
    // создаёт файл во внутреннем кэше (/data/data/<package>/...), а AndroidUtilities.isInternalUri()
    // блокирует отправку ЛЮБОГО файла с таким путём как «Вложение не поддерживается» — это защита
    // от отправки внутренних файлов приложения, но она же ловит и легитимные временные файлы
    // плагина. Эта папка (getExternalFilesDir) под защиту не попадает.
    public static String pluginFilesDirPath(String pluginId) {
        return pluginDataDir(pluginId).getAbsolutePath();
    }

    public static void writeData(String pluginId, String name, String content) {
        try {
            File f = new File(pluginDataDir(pluginId), name.replaceAll("[^a-zA-Z0-9_.\\-]", "_"));
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(content);
            w.close();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---- пункты меню сообщения ----
    public static List<String> menuItems() {
        List<String> res = new ArrayList<>();
        if (!loaded || isSafeMode()) {
            return res;
        }
        try {
            PyObject list = loader().callAttr("menu_items");
            for (PyObject o : list.asList()) {
                res.add(o.toString());
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    public static void menuClick(String pluginId, String label, String messageText, long dialogId) {
        try {
            loader().callAttr("menu_click", pluginId, label, messageText == null ? "" : messageText, dialogId);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---- пункты меню длинного тапа по ССЫЛКЕ ----
    public static List<String> linkMenuItems(String url) {
        List<String> res = new ArrayList<>();
        if (!loaded || isSafeMode()) {
            return res;
        }
        try {
            PyObject list = loader().callAttr("link_menu_items", url == null ? "" : url);
            for (PyObject o : list.asList()) {
                res.add(o.toString());
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    public static void linkMenuClick(String pluginId, String label, String url, long dialogId) {
        try {
            loader().callAttr("link_menu_click", pluginId, label, url == null ? "" : url, dialogId);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---- перехват сырых TL-апдейтов ----
    public static boolean wantsUpdates() {
        return loaded && !isSafeMode() && wantsUpdatesFlag;
    }

    public static void refreshWantsUpdates() {
        try {
            wantsUpdatesFlag = loader().callAttr("wants_updates").toBoolean();
        } catch (Throwable e) {
            wantsUpdatesFlag = false;
        }
    }

    public static void onUpdate(int account, Object update) {
        try {
            loader().callAttr("dispatch_update", account, update);
        } catch (Throwable ignore) {
        }
    }

    public static boolean onIntent(android.content.Intent intent) {
        if (intent == null || !loaded || isSafeMode()) return false;
        try {
            return Python.getInstance().getModule("devgram.intents")
                    .callAttr("dispatch", intent).toBoolean();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean openIntent(android.content.Intent intent) {
        if (intent == null) return false;
        try {
            android.content.Context context = ApplicationLoader.applicationContext;
            org.telegram.ui.ActionBar.BaseFragment fragment = org.telegram.ui.LaunchActivity.getSafeLastFragment();
            if (fragment != null && fragment.getParentActivity() != null) {
                context = fragment.getParentActivity();
            } else {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** Open a Telegram dialog without exposing overloaded Fragment APIs to Python. */
    public static boolean openDialog(long dialogId) {
        if (dialogId == 0 || DialogObject.isFolderDialogId(dialogId)) return false;
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(() -> openDialogInternal(dialogId));
            return true;
        }
        return openDialogInternal(dialogId);
    }

    private static boolean openDialogInternal(long dialogId) {
        try {
            org.telegram.ui.ActionBar.BaseFragment fragment = org.telegram.ui.LaunchActivity.getSafeLastFragment();
            if (fragment == null || fragment.getParentLayout() == null) return false;
            android.os.Bundle args = new android.os.Bundle();
            if (DialogObject.isEncryptedDialog(dialogId)) {
                args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
            } else if (DialogObject.isUserDialog(dialogId)) {
                args.putLong("user_id", dialogId);
            } else if (DialogObject.isChatDialog(dialogId)) {
                args.putLong("chat_id", -dialogId);
            } else {
                return false;
            }
            int account = fragment.getCurrentAccount();
            if (account < 0) account = UserConfig.selectedAccount;
            if (!MessagesController.getInstance(account).checkCanOpenChat(args, fragment)) return false;
            return fragment.presentFragment(new org.telegram.ui.ChatActivity(args));
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // ---- диалоги / bulletins ----
    public static void bulletin(String text) {
        bulletin("info", text, org.telegram.ui.Components.Bulletin.DURATION_LONG, null, null);
    }

    public static void bulletin(String type, String text, int duration, String button, PyObject callback) {
        if (text == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                org.telegram.ui.ActionBar.BaseFragment f = org.telegram.ui.LaunchActivity.getSafeLastFragment();
                if (f != null) {
                    org.telegram.ui.Components.BulletinFactory factory = org.telegram.ui.Components.BulletinFactory.of(f);
                    org.telegram.ui.Components.Bulletin bulletin;
                    Runnable action = callback == null ? null : () -> callPythonCallback(callback);
                    if (button != null && !button.isEmpty()) {
                        int icon = "error".equals(type) ? R.raw.error
                                : "success".equals(type) ? R.raw.contact_check : R.raw.info;
                        bulletin = factory.createSimpleBulletin(icon, text, button,
                                Math.max(500, duration), action);
                    } else if ("error".equals(type)) {
                        bulletin = factory.createErrorBulletin(text).setDuration(Math.max(500, duration));
                    } else if ("success".equals(type)) {
                        bulletin = factory.createSuccessBulletin(text).setDuration(Math.max(500, duration));
                    } else {
                        bulletin = factory.createSimpleBulletin(R.raw.info, text)
                                .setDuration(Math.max(500, duration));
                    }
                    bulletin.show();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public static void alert(String title, String message) {
        alert(title, message, "OK", null, null, null, null, null);
    }

    public static void alert(String title, String message,
                             String positiveText, PyObject positiveCallback,
                             String negativeText, PyObject negativeCallback,
                             String neutralText, PyObject neutralCallback) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                org.telegram.ui.ActionBar.BaseFragment f = org.telegram.ui.LaunchActivity.getSafeLastFragment();
                android.app.Activity a = f != null ? f.getParentActivity() : null;
                if (a == null) {
                    return;
                }
                org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(a);
                b.setTitle(title == null ? "" : title);
                b.setMessage(message == null ? "" : message);
                if (positiveText != null && !positiveText.isEmpty()) {
                    b.setPositiveButton(positiveText, (dialog, which) -> callPythonCallback(positiveCallback));
                }
                if (negativeText != null && !negativeText.isEmpty()) {
                    b.setNegativeButton(negativeText, (dialog, which) -> callPythonCallback(negativeCallback));
                }
                if (neutralText != null && !neutralText.isEmpty()) {
                    b.setNeutralButton(neutralText, (dialog, which) -> callPythonCallback(neutralCallback));
                }
                org.telegram.ui.ActionBar.AlertDialog dialog = b.show();
                // DevGram: текст (особенно DEBUG-трейсбеки из плагинов) должен выделяться и
                // копироваться долгим тапом, а не только скриншотиться — стандартный AlertDialog
                // это не даёт по умолчанию.
                if (dialog != null && dialog.getMessageTextView() != null) {
                    dialog.getMessageTextView().setTextIsSelectable(true);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    private static void callPythonCallback(PyObject callback) {
        if (callback == null || isSafeMode()) return;
        try {
            callback.call();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---- настройки плагина (UI-страница) ----
    public static List<String> pluginSettings(String pluginId) {
        List<String> res = new ArrayList<>();
        try {
            PyObject list = loader().callAttr("plugin_settings", pluginId);
            for (PyObject o : list.asList()) {
                res.add(o.toString());
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    public static boolean hasSettings(String pluginId) {
        return !pluginSettings(pluginId).isEmpty();
    }

    // Живой View для строки devgram.ui.Custom (index — позиция в списке из pluginSettings,
    // который нужно звать первым, чтобы Python-сторона успела закэшировать сырые объекты).
    public static android.view.View pluginSettingsCustomView(String pluginId, int index) {
        try {
            PyObject view = loader().callAttr("plugin_settings_custom_view", pluginId, index);
            if (view == null) return null;
            return view.toJava(android.view.View.class);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void pluginSettingClick(String pluginId, String key) {
        try {
            loader().callAttr("plugin_setting_click", pluginId, key);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void pluginSettingChanged(String pluginId, String key, String value) {
        try { loader().callAttr("plugin_setting_changed", pluginId, key, value); }
        catch (Throwable e) { FileLog.e(e); }
    }

    // ===== защита от «кирпича» (авто-безопасный режим после краша) =====

    // Ставим глобальный перехватчик необработанных исключений: сохраняем стек в prefs,
    // помечаем краш, затем передаём управление штатному обработчику.
    private static synchronized void installCrashHandler() {
        if (crashHandlerInstalled) {
            return;
        }
        crashHandlerInstalled = true;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                prefs().edit()
                        .putBoolean("crashed", true)
                        .putString("last_crash", "DevGram — приложение упало:\n\n" + sw)
                        .apply();
            } catch (Throwable ignore) {
            }
            if (prev != null) {
                prev.uncaughtException(t, e); // штатная обработка (лог/рестарт)
            }
        });
    }

    // Зовётся из главной активности (onResume). Один раз собирает и показывает ПОЛНЫЙ отчёт о крэше
    // и с задержкой снимает boot-флаг (значит запуск прошёл успешно).
    public static void onMainResume() {
        // Автостарт-надёжность: если прокси включён, но Python-поток умер (крэш/долгий простой) —
        // переподнять локальный прокси. start() идемпотентен (если жив — no-op).
        try {
            if (DevGramProxy.isEnabled() && !isWsProxyRunning()) {
                DevGramProxy.ensureLocalStarted();
            }
        } catch (Throwable ignore) {
        }
        final boolean crashNoticeNow = pendingCrashNotice;
        if (pendingCrashNotice) {
            pendingCrashNotice = false;
            // сбор отчёта тяжёлый (logcat) — в фоне, затем показ на UI
            NET_QUEUE.execute(() -> {
                final String report = collectCrashReport();
                saveCrashReport(report);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        copyToClipboard(report);
                    } catch (Throwable ignore) {
                    }
                    showCrashDialog(report);
                });
            });
        }
        // DevGram: если включён безопасный режим — уведомить (один раз за запуск).
        // При свежем сбое отдельный notice не дублируем: краш-диалог уже всё объясняет.
        if (isSafeMode() && !safeModeNoticeShown && !crashNoticeNow) {
            safeModeNoticeShown = true;
            AndroidUtilities.runOnUIThread(DevGramPlugins::showSafeModeNotice, 1500);
        }
        // даём приложению 8 секунд «пожить»; если за это время упадёт — boot_pending останется
        AndroidUtilities.runOnUIThread(() -> {
            try {
                prefs().edit().putBoolean("boot_pending", false).apply();
            } catch (Throwable ignore) {
            }
        }, 8000);
    }

    // Штатный уход из приложения (onPause) — значит оно успешно поднялось, снимаем boot-флаг.
    // Краш/ANR сюда не доходит, поэтому ложных срабатываний при быстром выходе нет.
    public static void onMainPause() {
        try {
            prefs().edit().putBoolean("boot_pending", false).apply();
        } catch (Throwable ignore) {
        }
    }

    // Собрать ПОЛНЫЙ отчёт: устройство, версия, Java-стек (если был) + дамп logcat (нативный бэктрейс/ANR/логи Pine).
    private static String collectCrashReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== DevGram: отчёт о сбое =====\n");
        try {
            sb.append("время:      ").append(new java.util.Date().toString()).append('\n');
            sb.append("устройство: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append('\n');
            sb.append("Android:    ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
            try {
                Context c = ApplicationLoader.applicationContext;
                android.content.pm.PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
                sb.append("версия:     ").append(pi.versionName).append(" (").append(pi.versionCode).append(")\n");
            } catch (Throwable ignore) {
            }
            sb.append("хуки Pine:  ").append(hooksReady ? "инициализированы" : "нет").append('\n');
            sb.append("плагины:\n");
            try {
                for (String s : listPlugins()) {
                    String[] f = s.split("", -1);
                    if (f.length > 4) {
                        sb.append("   • ").append(f[1]).append(" v").append(f[2])
                                .append(" [").append("1".equals(f[4]) ? "вкл" : "выкл").append("]\n");
                    }
                }
            } catch (Throwable ignore) {
            }
        } catch (Throwable ignore) {
        }
        String javaTrace = prefs().getString("last_crash", "");
        if (javaTrace != null && !javaTrace.isEmpty()) {
            sb.append("\n===== необработанное исключение (Java) =====\n").append(javaTrace).append('\n');
        } else {
            sb.append("\n(Java-исключения не зафиксировано — вероятно нативный сбой или ANR; смотри logcat ниже)\n");
        }
        sb.append("\n===== logcat (последние строки процесса) =====\n").append(readLogcat());
        return sb.toString();
    }

    // Дамп собственных логов процесса (там и Java FATAL EXCEPTION, и логи Pine/Chaquopy перед падением).
    private static String readLogcat() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-t", "700"});
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder all = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                all.append(line).append('\n');
                if (all.length() > 80000) {
                    all.append("… (обрезано)\n");
                    break;
                }
            }
            r.close();
            return all.length() == 0 ? "(logcat пуст или недоступен)" : all.toString();
        } catch (Throwable e) {
            return "logcat недоступен: " + e;
        }
    }

    // Сохранить отчёт в файл во внешней папке приложения (можно достать файловым менеджером и переслать).
    private static void saveCrashReport(String report) {
        try {
            Context c = ApplicationLoader.applicationContext;
            java.io.File dir = c.getExternalFilesDir(null);
            if (dir == null) {
                dir = c.getFilesDir();
            }
            java.io.File f = new java.io.File(dir, "devgram_crash.txt");
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(report);
            w.close();
        } catch (Throwable ignore) {
        }
    }

    // Показать отчёт целиком в прокручиваемом выделяемом диалоге.
    // DevGram: показывали ли уже уведомление о безопасном режиме в этом запуске.
    private static volatile boolean safeModeNoticeShown = false;

    // DevGram: уведомить пользователя, что включён безопасный режим (плагины отключены),
    // с быстрым действием «Выключить режим». Показывается один раз за запуск.
    public static void showSafeModeNotice() {
        try {
            if (!isSafeMode()) {
                return;
            }
            org.telegram.ui.ActionBar.BaseFragment fr = org.telegram.ui.LaunchActivity.getSafeLastFragment();
            android.app.Activity a = fr != null ? fr.getParentActivity() : null;
            if (a == null) {
                safeModeNoticeShown = false; // покажем позже, когда появится активность
                return;
            }
            org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(a);
            b.setTitle("Безопасный режим включён");
            b.setMessage("Плагины сейчас отключены. Обычно режим включается автоматически после сбоя, чтобы приложение точно запустилось.\n\nНайдите и отключите проблемный плагин, затем выключите безопасный режим.");
            b.setPositiveButton("Выключить режим", (d, w) -> {
                setFlag("safe_mode", false);
                int n = reload();
                if (flag("dev_mode", false)) {
                    setDevServerEnabled(true);
                }
                try {
                    org.telegram.ui.ActionBar.BaseFragment f2 = org.telegram.ui.LaunchActivity.getSafeLastFragment();
                    if (f2 != null) {
                        org.telegram.ui.Components.BulletinFactory.of(f2)
                                .createSimpleBulletin(R.raw.contact_check,
                                        "Безопасный режим выключен. Загружено плагинов: " + n).show();
                    }
                } catch (Throwable ignore) {
                }
            });
            b.setNegativeButton("Оставить", null);
            b.show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void showCrashDialog(String report) {
        try {
            org.telegram.ui.ActionBar.BaseFragment fr = org.telegram.ui.LaunchActivity.getSafeLastFragment();
            android.app.Activity a = fr != null ? fr.getParentActivity() : null;
            if (a == null) {
                return;
            }
            android.widget.TextView tv = new android.widget.TextView(a);
            tv.setText(report);
            tv.setTextIsSelectable(true);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11);
            tv.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
            int pad = AndroidUtilities.dp(16);
            tv.setPadding(pad, pad, pad, pad);
            android.widget.ScrollView sv = new android.widget.ScrollView(a);
            sv.addView(tv);
            org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(a);
            b.setTitle("Отчёт о сбое");
            b.setView(sv);
            b.setPositiveButton("OK", null);
            b.setNeutralButton("Копировать", (d, w) -> {
                try {
                    copyToClipboard(report);
                } catch (Throwable ignore) {
                }
            });
            b.show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // DevGram: показать общий лог плагинов (кнопка «Логи плагинов» в «Системе плагинов»).
    public static void showPluginLogs() {
        try {
            org.telegram.ui.ActionBar.BaseFragment fr = org.telegram.ui.LaunchActivity.getSafeLastFragment();
            android.app.Activity a = fr != null ? fr.getParentActivity() : null;
            if (a == null) {
                return;
            }
            String logs = pluginLogs();
            if (logs == null || logs.trim().isEmpty()) {
                logs = "Логи пусты. Здесь появятся события загрузки и включения плагинов, а также ошибки хуков.";
            }
            final String shown = logs;
            android.widget.TextView tv = new android.widget.TextView(a);
            tv.setText(shown);
            tv.setTextIsSelectable(true);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11);
            tv.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
            int pad = AndroidUtilities.dp(16);
            tv.setPadding(pad, pad, pad, pad);
            android.widget.ScrollView sv = new android.widget.ScrollView(a);
            sv.addView(tv);
            org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(a);
            String stats = "";
            try {
                stats = loader().callAttr("log_stats").toString();
            } catch (Throwable ignore) {
            }
            b.setTitle(stats == null || stats.isEmpty() ? "Логи плагинов" : "Логи плагинов · " + stats);
            b.setView(sv);
            sv.post(() -> sv.fullScroll(android.view.View.FOCUS_DOWN));
            b.setPositiveButton("Закрыть", null);
            b.setNeutralButton("Копировать", (d, w) -> {
                try {
                    copyToClipboard(shown);
                } catch (Throwable ignore) {
                }
            });
            b.setNegativeButton("Очистить", (d, w) -> clearPluginLogs());
            b.show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Показать последний сохранённый отчёт (для кнопки в «Системе плагинов»).
    public static void showLastCrashReport() {
        try {
            Context c = ApplicationLoader.applicationContext;
            java.io.File dir = c.getExternalFilesDir(null);
            if (dir == null) {
                dir = c.getFilesDir();
            }
            java.io.File f = new java.io.File(dir, "devgram_crash.txt");
            if (!f.exists()) {
                alert("Отчёт о сбое", "Сохранённых отчётов нет — приложение не падало.");
                return;
            }
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            r.close();
            showCrashDialog(sb.toString());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ===== Xposed-хуки Java-методов (AliuHook / LSPlant) =====
    // Мигрировали с Pine на AliuHook (Java Xposed API поверх LSPlant + Dobby, как у exteraGram):
    // надёжная установка хуков на всех Android, хук инлайненных методов, method-replacement.
    private static volatile boolean hooksReady;

    // Снятие хуков по плагину (аналог XposedHookRecord у exteraGram): на reload/выгрузку снимаем.
    private static final java.util.Map<String, java.util.List<de.robv.android.xposed.XC_MethodHook.Unhook>>
            pluginUnhooks = new java.util.concurrent.ConcurrentHashMap<>();

    private static void trackUnhook(String pluginId, de.robv.android.xposed.XC_MethodHook.Unhook unhook) {
        if (unhook == null || pluginId == null) {
            return;
        }
        pluginUnhooks.computeIfAbsent(pluginId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(unhook);
    }

    // Снять все хуки плагина (перед reload/выгрузкой), чтобы старые колбэки не висели.
    public static void unhookPlugin(String pluginId) {
        java.util.List<de.robv.android.xposed.XC_MethodHook.Unhook> list = pluginUnhooks.remove(pluginId);
        if (list == null) {
            return;
        }
        for (de.robv.android.xposed.XC_MethodHook.Unhook u : list) {
            try {
                u.unhook();
            } catch (Throwable ignore) {
            }
        }
    }

    public static synchronized boolean initHooks() {
        if (hooksReady) {
            return true;
        }
        try {
            // AliuHook: нативка (libaliuhook/liblsplant) грузится в static-блоке XposedBridge при
            // первом обращении. Снимаем hidden-API ограничения и profile saver (стабильные хуки).
            de.robv.android.xposed.XposedBridge.disableHiddenApiRestrictions();
            de.robv.android.xposed.XposedBridge.disableProfileSaver();
            hooksReady = true;
        } catch (Throwable e) {
            FileLog.e(e); // несовместимое устройство — хуки просто не работают, приложение живёт
            hooksReady = false;
        }
        return hooksReady;
    }

    // Хукнуть метод/конструктор: before_hook/after_hook плагина зовутся до/после оригинала.
    public static boolean hook(String pluginId, String className, String methodName, List<String> paramTypeNames) {
        if (isSafeMode()) {
            return false; // безопасный режим — хуки вообще не привязываем (нечему падать)
        }
        if (!initHooks()) {
            return false;
        }
        try {
            Class<?> clazz = ApplicationLoader.applicationContext.getClassLoader().loadClass(className);
            Class<?>[] pts = new Class<?>[paramTypeNames == null ? 0 : paramTypeNames.size()];
            for (int i = 0; i < pts.length; i++) {
                pts[i] = resolveType(paramTypeNames.get(i));
            }
            java.lang.reflect.Member member;
            if ("<init>".equals(methodName)) {
                java.lang.reflect.Constructor<?> c = clazz.getDeclaredConstructor(pts);
                c.setAccessible(true);
                member = c;
            } else {
                java.lang.reflect.Method m = clazz.getDeclaredMethod(methodName, pts);
                m.setAccessible(true);
                member = m;
            }
            final String pid = pluginId;
            // Деоптимизируем метод перед хуком — иначе инлайненные/оптимизированные методы ART
            // могут не поймать хук. Это даёт возможность хукать то, что Pine не мог (как exteraGram).
            try {
                de.robv.android.xposed.XposedBridge.deoptimizeMethod(member);
            } catch (Throwable ignore) {
            }
            de.robv.android.xposed.XC_MethodHook.Unhook unhook = de.robv.android.xposed.XposedBridge.hookMethod(
                    member, new de.robv.android.xposed.XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            dispatchHook(pid, "before", param);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            dispatchHook(pid, "after", param);
                        }
                    });
            trackUnhook(pid, unhook);
            FileLog.d("[DevGramPlugins] hook: " + className + "." + methodName);
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // Резолв метода/конструктора по имени класса/метода/типов параметров.
    private static java.lang.reflect.Member resolveMember(
            String className, String methodName, List<String> paramTypeNames) throws Exception {
        Class<?> clazz = ApplicationLoader.applicationContext.getClassLoader().loadClass(className);
        Class<?>[] pts = new Class<?>[paramTypeNames == null ? 0 : paramTypeNames.size()];
        for (int i = 0; i < pts.length; i++) {
            pts[i] = resolveType(paramTypeNames.get(i));
        }
        if ("<init>".equals(methodName)) {
            java.lang.reflect.Constructor<?> c = clazz.getDeclaredConstructor(pts);
            c.setAccessible(true);
            return c;
        }
        java.lang.reflect.Method m = clazz.getDeclaredMethod(methodName, pts);
        m.setAccessible(true);
        return m;
    }

    // ===== Полный хук-API как у exteraGram: per-hook колбэки before/after/replace =====
    // DGMethodHook держит python-колбэки конкретного хука (аналог exteraGram PyMethodHook):
    // своя before/after функция на каждый хук + полная подмена метода (replace).
    static final class DGMethodHook extends de.robv.android.xposed.XC_MethodHook {
        private final String pluginId;
        private final com.chaquo.python.PyObject before;
        private final com.chaquo.python.PyObject after;
        private final com.chaquo.python.PyObject replace;

        DGMethodHook(String pluginId, int priority, com.chaquo.python.PyObject before,
                com.chaquo.python.PyObject after, com.chaquo.python.PyObject replace) {
            super(priority);
            this.pluginId = pluginId;
            this.before = before;
            this.after = after;
            this.replace = replace;
        }

        // Ошибка внутри хука → в «Логи плагинов» (уровень ERROR, тег плагина) + FileLog.
        private void hookError(MethodHookParam param, Throwable t) {
            FileLog.e(t);
            try {
                String where = (param != null && param.method != null) ? param.method.getName() : "?";
                loader().callAttr("dg_log", "hook " + where + ": " + t, "ERROR", pluginId);
            } catch (Throwable ignore) {
            }
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (isSafeMode()) {
                return;
            }
            try {
                if (replace != null) {
                    // Полная подмена: результат из python, оригинал не вызывается.
                    com.chaquo.python.PyObject r = replace.call(param);
                    param.setResult(r == null ? null : r.toJava(Object.class));
                    coerceResult(param);
                    return;
                }
                if (before != null) {
                    before.call(param);
                    coerceArgs(param);
                }
            } catch (Throwable t) {
                hookError(param, t);
            }
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (isSafeMode() || replace != null || after == null) {
                return;
            }
            try {
                after.call(param);
                coerceResult(param);
            } catch (Throwable t) {
                hookError(param, t);
            }
        }
    }

    // Хук ОДНОГО метода/конструктора со своими python-колбэками (before/after/replace).
    public static boolean hookCb(String pluginId, String className, String methodName,
            List<String> paramTypeNames, com.chaquo.python.PyObject before,
            com.chaquo.python.PyObject after, com.chaquo.python.PyObject replace, int priority) {
        if (isSafeMode() || !initHooks()) {
            return false;
        }
        try {
            java.lang.reflect.Member member = resolveMember(className, methodName, paramTypeNames);
            try {
                de.robv.android.xposed.XposedBridge.deoptimizeMethod(member);
            } catch (Throwable ignore) {
            }
            de.robv.android.xposed.XC_MethodHook.Unhook unhook = de.robv.android.xposed.XposedBridge.hookMethod(
                    member, new DGMethodHook(pluginId, priority, before, after, replace));
            trackUnhook(pluginId, unhook);
            FileLog.d("[DevGramPlugins] hookCb: " + className + "." + methodName);
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // Хук ВСЕХ перегрузок метода (или всех конструкторов, methodName="<init>") со своими колбэками.
    public static int hookAllCb(String pluginId, String className, String methodName,
            com.chaquo.python.PyObject before, com.chaquo.python.PyObject after,
            com.chaquo.python.PyObject replace, int priority) {
        if (isSafeMode() || !initHooks()) {
            return 0;
        }
        try {
            Class<?> clazz = ApplicationLoader.applicationContext.getClassLoader().loadClass(className);
            DGMethodHook cb = new DGMethodHook(pluginId, priority, before, after, replace);
            java.util.Set<de.robv.android.xposed.XC_MethodHook.Unhook> unhooks;
            if ("<init>".equals(methodName)) {
                unhooks = de.robv.android.xposed.XposedBridge.hookAllConstructors(clazz, cb);
            } else {
                unhooks = de.robv.android.xposed.XposedBridge.hookAllMethods(clazz, methodName, cb);
            }
            for (de.robv.android.xposed.XC_MethodHook.Unhook u : unhooks) {
                trackUnhook(pluginId, u);
            }
            return unhooks.size();
        } catch (Throwable e) {
            FileLog.e(e);
            return 0;
        }
    }

    // Деоптимизировать метод вручную (чтобы инлайненные call-сайты пошли через него) — для плагинов.
    public static boolean deoptimize(String className, String methodName, List<String> paramTypeNames) {
        if (!initHooks()) {
            return false;
        }
        try {
            return de.robv.android.xposed.XposedBridge.deoptimizeMethod(
                    resolveMember(className, methodName, paramTypeNames));
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // ===== Утилиты AliuHook для плагинов (паритет с exteraGram) =====
    // Вызвать ОРИГИНАЛ метода в обход хуков — типично внутри before/replace-колбэка,
    // чтобы условно выполнить оригинал и/или доработать его результат.
    public static Object invokeOriginal(de.robv.android.xposed.XC_MethodHook.MethodHookParam param) throws Throwable {
        return de.robv.android.xposed.XposedBridge.invokeOriginalMethod(
                param.method, param.thisObject, param.args);
    }

    // Сделать final-класс наследуемым (для dynamic_proxy/подклассов из плагина).
    public static boolean makeClassInheritable(String className) {
        try {
            return de.robv.android.xposed.XposedBridge.makeClassInheritable(
                    ApplicationLoader.applicationContext.getClassLoader().loadClass(className));
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // Создать экземпляр класса БЕЗ вызова конструктора.
    public static Object allocateInstance(String className) {
        try {
            return de.robv.android.xposed.XposedBridge.allocateInstance(
                    ApplicationLoader.applicationContext.getClassLoader().loadClass(className));
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Захукан ли метод сейчас.
    public static boolean isMethodHooked(String className, String methodName, List<String> paramTypeNames) {
        try {
            return de.robv.android.xposed.XposedBridge.isHooked(
                    resolveMember(className, methodName, paramTypeNames));
        } catch (Throwable e) {
            return false;
        }
    }

    // ============ Хуки TL-запросов/ответов ============
    // Плагин переопределяет on_send_request(name, req) и/или on_receive_response(name, resp, err).
    // Реализовано через Pine-хук ConnectionsManager.sendRequest(TLObject, RequestDelegate):
    // до оригинала — dispatch_request; делегат ответа оборачиваем java.lang.reflect.Proxy → dispatch_response.
    private static volatile boolean requestHooksInstalled;
    private static volatile boolean wantsRequestHooksFlag;

    public static void refreshRequestHooks() {
        try {
            wantsRequestHooksFlag = loader().callAttr("wants_request_hooks").toBoolean();
        } catch (Throwable e) {
            wantsRequestHooksFlag = false;
        }
        if (wantsRequestHooksFlag) {
            installRequestHooks();
        }
    }

    private static synchronized void installRequestHooks() {
        if (requestHooksInstalled || isSafeMode() || !initHooks()) {
            return;
        }
        try {
            Class<?> cm = Class.forName("org.telegram.tgnet.ConnectionsManager");
            Class<?> tlObject = Class.forName("org.telegram.tgnet.TLObject");
            final Class<?> reqDelegate = Class.forName("org.telegram.tgnet.RequestDelegate");
            java.lang.reflect.Method m = cm.getDeclaredMethod("sendRequest", tlObject, reqDelegate);
            m.setAccessible(true);
            de.robv.android.xposed.XposedBridge.hookMethod(m, new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam frame) {
                    if (!wantsRequestHooksFlag) return;
                    try {
                        final Object req = frame.args != null && frame.args.length > 0 ? frame.args[0] : null;
                        final String name = req != null ? req.getClass().getSimpleName() : "";
                        final int account = accountOfController(frame.thisObject);
                        // pre-хук: плагины видят исходящий запрос (могут менять поля объекта на месте)
                        try { loader().callAttr("dispatch_request", account, name, req); } catch (Throwable ignore) {}
                        // оборачиваем делегат ответа, чтобы поймать ответ/ошибку
                        final Object origDelegate = frame.args != null && frame.args.length > 1 ? frame.args[1] : null;
                        Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                                reqDelegate.getClassLoader(), new Class<?>[]{reqDelegate},
                                (p, method, mArgs) -> {
                                    if ("run".equals(method.getName()) && mArgs != null && mArgs.length == 2) {
                                        try {
                                            Object resp = mArgs[0];
                                            Object err = mArgs[1];
                                            String errText = null;
                                            if (err != null) {
                                                try { errText = String.valueOf(err.getClass().getField("text").get(err)); } catch (Throwable ignore) {}
                                            }
                                            loader().callAttr("dispatch_response", account, name, resp, errText);
                                        } catch (Throwable ignore) {}
                                    }
                                    if (origDelegate != null) {
                                        try {
                                            return method.invoke(origDelegate, mArgs);
                                        } catch (java.lang.reflect.InvocationTargetException ite) {
                                            throw ite.getCause() != null ? ite.getCause() : ite;
                                        }
                                    }
                                    return null;
                                });
                        if (frame.args != null && frame.args.length > 1) {
                            frame.args[1] = proxy;
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            });
            requestHooksInstalled = true;
            FileLog.d("[DevGramPlugins] request hooks installed");
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static int accountOfController(Object controller) {
        if (controller == null) return UserConfig.selectedAccount;
        try {
            java.lang.reflect.Field field = org.telegram.messenger.BaseController.class.getDeclaredField("currentAccount");
            field.setAccessible(true);
            return field.getInt(controller);
        } catch (Throwable ignore) {
            return UserConfig.selectedAccount;
        }
    }

    // ============ Class Proxy (DexMaker): реальные Java-подклассы из Python ============
    // Плагин: logic — Python-объект с методами-переопределениями; overrideNames — их имена.
    // Java-вызовы этих методов маршрутизируются в logic (первым аргументом идёт сам объект-proxy,
    // чтобы можно было звать super), остальные методы уходят в оригинал (ProxyBuilder.callSuper).
    // Текущий (proxy, method, args) во время исполнения override хранится в thread-local для call_super.
    private static final ThreadLocal<java.util.ArrayDeque<Object[]>> currentSuper =
            ThreadLocal.withInitial(java.util.ArrayDeque::new);

    private static Object defaultValue(Class<?> rt) {
        if (rt == boolean.class) return Boolean.FALSE;
        if (rt == int.class) return 0;
        if (rt == long.class) return 0L;
        if (rt == float.class) return 0f;
        if (rt == double.class) return 0d;
        if (rt == short.class) return (short) 0;
        if (rt == byte.class) return (byte) 0;
        if (rt == char.class) return (char) 0;
        return null;
    }

    public static Object subclass(String baseName, final com.chaquo.python.PyObject logic,
                                  java.util.List<String> overrideNames,
                                  java.util.List<String> argTypeNames,
                                  java.util.List<Object> args) {
        if (isSafeMode()) {
            return null;
        }
        try {
            final Class<?> base = ApplicationLoader.applicationContext.getClassLoader().loadClass(baseName);
            final java.util.HashSet<String> overrides = new java.util.HashSet<>(
                    overrideNames == null ? java.util.Collections.emptyList() : overrideNames);
            Class<?>[] ctorTypes = new Class<?>[argTypeNames == null ? 0 : argTypeNames.size()];
            for (int i = 0; i < ctorTypes.length; i++) {
                ctorTypes[i] = resolveType(argTypeNames.get(i));
            }
            Object[] ctorArgs = args == null ? new Object[0] : args.toArray(new Object[0]);

            java.io.File dexCache = ApplicationLoader.applicationContext.getDir("dexmaker", Context.MODE_PRIVATE);
            java.lang.reflect.InvocationHandler handler = (proxy, method, mArgs) -> {
                String name = method.getName();
                if (!overrides.contains(name)) {
                    return com.android.dx.stock.ProxyBuilder.callSuper(proxy, method, mArgs);
                }
                currentSuper.get().push(new Object[]{proxy, method, mArgs});
                try {
                    Object[] callArgs;
                    if (mArgs == null) {
                        callArgs = new Object[]{proxy};
                    } else {
                        callArgs = new Object[mArgs.length + 1];
                        callArgs[0] = proxy;
                        System.arraycopy(mArgs, 0, callArgs, 1, mArgs.length);
                    }
                    com.chaquo.python.PyObject r = logic.callAttr(name, callArgs);
                    Class<?> rt = method.getReturnType();
                    if (rt == void.class) {
                        return null;
                    }
                    return r == null ? defaultValue(rt) : r.toJava(rt);
                } catch (Throwable e) {
                    FileLog.e(e);
                    try {
                        return com.android.dx.stock.ProxyBuilder.callSuper(proxy, method, mArgs);
                    } catch (Throwable e2) {
                        Class<?> rt = method.getReturnType();
                        return rt == void.class ? null : defaultValue(rt);
                    }
                } finally {
                    currentSuper.get().pop();
                }
            };

            return com.android.dx.stock.ProxyBuilder.forClass(base)
                    .dexCache(dexCache)
                    .constructorArgTypes(ctorTypes)
                    .constructorArgValues(ctorArgs)
                    .handler(handler)
                    .build();
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Вызвать оригинальный (super) метод из тела Python-override. argsOverride == null → те же аргументы.
    public static Object callSuper(java.util.List<Object> argsOverride) {
        java.util.ArrayDeque<Object[]> stack = currentSuper.get();
        if (stack.isEmpty()) {
            return null;
        }
        Object[] cur = stack.peek();
        try {
            Object proxy = cur[0];
            java.lang.reflect.Method m = (java.lang.reflect.Method) cur[1];
            Object[] a = argsOverride != null ? argsOverride.toArray(new Object[0]) : (Object[]) cur[2];
            return com.android.dx.stock.ProxyBuilder.callSuper(proxy, m, a);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Визуальный хелпер для плагинов: скруглить углы View по контуру (на UI-потоке).
    public static void roundView(final android.view.View v, final int radiusDp) {
        if (v == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                final int r = AndroidUtilities.dp(radiusDp);
                v.setClipToOutline(true);
                v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(android.view.View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                    }
                });
                v.invalidateOutline();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // ============ Жидкое стекло (Liquid Glass) ============
    // corner/blur — в dp, tint — ARGB (напр. 0x26FFFFFF), border — яркость кромки 0..1.

    private static boolean onUiThread() {
        return android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
    }

    // Новая пустая стеклянная панель-контейнер. Плагин сам добавляет её куда нужно
    // (через addViewFrame) и кладёт внутрь свой контент. contextView — любая вью для
    // контекста/корня размытия (обычно frame.thisObject).
    public static android.view.View glassPanel(android.view.View contextView, int cornerDp, int blurDp, int tint, float border) {
        try {
            Context ctx = contextView != null ? contextView.getContext() : ApplicationLoader.applicationContext;
            org.telegram.ui.Components.DevGramGlassView g = new org.telegram.ui.Components.DevGramGlassView(ctx);
            g.setCornerRadiusDp(cornerDp).setBlurStrengthDp(blurDp).setTint(tint).setBorderLight(border);
            if (contextView != null) {
                g.setBlurRoot(contextView.getRootView());
            }
            return g;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Обернуть СУЩЕСТВУЮЩУЮ вью в стекло: target вынимается из родителя и кладётся внутрь
    // стеклянной панели на его же место (тот же индекс/LayoutParams). Работает в любом
    // контейнере. Фон target гасим в прозрачный, чтобы стекло было видно. Возвращает панель.
    public static android.view.View glassWrap(final android.view.View target, final int cornerDp, final int blurDp, final int tint, final float border) {
        if (target == null) {
            return null;
        }
        final android.view.View[] out = new android.view.View[1];
        Runnable r = () -> {
            try {
                android.view.ViewParent p = target.getParent();
                if (!(p instanceof android.view.ViewGroup)) {
                    return;
                }
                if (p instanceof org.telegram.ui.Components.DevGramGlassView) {
                    out[0] = (android.view.View) p; // уже в стекле — не оборачиваем повторно
                    return;
                }
                android.view.ViewGroup parent = (android.view.ViewGroup) p;
                int idx = parent.indexOfChild(target);
                android.view.ViewGroup.LayoutParams lp = target.getLayoutParams();
                org.telegram.ui.Components.DevGramGlassView g = new org.telegram.ui.Components.DevGramGlassView(target.getContext());
                g.setCornerRadiusDp(cornerDp).setBlurStrengthDp(blurDp).setTint(tint).setBorderLight(border);
                g.setBlurRoot(target.getRootView());
                parent.removeView(target);
                g.addView(target, new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                parent.addView(g, idx, lp); // стекло занимает исходное место target
                try { target.setBackgroundColor(0x00000000); } catch (Throwable ignore) {}
                out[0] = g;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        };
        if (onUiThread()) {
            r.run();
        } else {
            AndroidUtilities.runOnUIThread(r);
        }
        return out[0];
    }

    // Добавить child в parent с FrameLayout-параметрами. Размеры/отступы в dp;
    // ширина/высота: -1 = MATCH_PARENT, -2 = WRAP_CONTENT. gravity — как у Gravity.* (напр. 17 = center).
    public static void addViewFrame(final android.view.ViewGroup parent, final android.view.View child,
                                    final int widthDp, final int heightDp,
                                    final int leftDp, final int topDp, final int rightDp, final int bottomDp,
                                    final int gravity) {
        if (parent == null || child == null) {
            return;
        }
        Runnable r = () -> {
            try {
                int w = widthDp < 0 ? widthDp : AndroidUtilities.dp(widthDp);
                int h = heightDp < 0 ? heightDp : AndroidUtilities.dp(heightDp);
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(w, h);
                lp.leftMargin = AndroidUtilities.dp(leftDp);
                lp.topMargin = AndroidUtilities.dp(topDp);
                lp.rightMargin = AndroidUtilities.dp(rightDp);
                lp.bottomMargin = AndroidUtilities.dp(bottomDp);
                lp.gravity = gravity;
                parent.addView(child, lp);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        };
        if (onUiThread()) {
            r.run();
        } else {
            AndroidUtilities.runOnUIThread(r);
        }
    }

    // Убрать вью из своего родителя.
    public static void removeView(final android.view.View v) {
        if (v == null) {
            return;
        }
        Runnable r = () -> {
            try {
                android.view.ViewParent p = v.getParent();
                if (p instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) p).removeView(v);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        };
        if (onUiThread()) {
            r.run();
        } else {
            AndroidUtilities.runOnUIThread(r);
        }
    }

    // dp -> px (для расчётов из Python).
    public static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    private static Class<?> resolveType(String name) throws ClassNotFoundException {
        switch (name) {
            case "int": return int.class;
            case "long": return long.class;
            case "boolean": return boolean.class;
            case "byte": return byte.class;
            case "char": return char.class;
            case "short": return short.class;
            case "float": return float.class;
            case "double": return double.class;
            case "void": return void.class;
            default: return ApplicationLoader.applicationContext.getClassLoader().loadClass(name);
        }
    }

    private static void dispatchHook(String pluginId, String phase, Object frame) {
        if (isSafeMode()) {
            return; // безопасный режим — глушим хуки
        }
        try {
            loader().callAttr("dispatch_hook", pluginId, phase, frame);
            // Chaquopy боксит python-int как Long, а метод может ждать int/short/float и т.п.
            // Приводим аргументы (после before) и результат (после after) к нужным типам —
            // иначе падает на вызове оригинала: "argument has type int, got java.lang.Long".
            if (frame instanceof de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                de.robv.android.xposed.XC_MethodHook.MethodHookParam cf =
                        (de.robv.android.xposed.XC_MethodHook.MethodHookParam) frame;
                if ("before".equals(phase)) {
                    coerceArgs(cf);
                } else {
                    coerceResult(cf);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    // Привести элементы frame.args к типам параметров хукнутого метода.
    private static void coerceArgs(de.robv.android.xposed.XC_MethodHook.MethodHookParam cf) {
        try {
            Class<?>[] pts = paramTypesOf(cf.method);
            Object[] args = cf.args;
            if (pts == null || args == null) {
                return;
            }
            for (int i = 0; i < args.length && i < pts.length; i++) {
                args[i] = coerce(args[i], pts[i]);
            }
        } catch (Throwable ignore) {
        }
    }

    // Привести результат к типу возврата метода (если плагин подменил его числом «не того» бокса).
    private static void coerceResult(de.robv.android.xposed.XC_MethodHook.MethodHookParam cf) {
        try {
            if (!(cf.method instanceof java.lang.reflect.Method)) {
                return;
            }
            Class<?> rt = ((java.lang.reflect.Method) cf.method).getReturnType();
            Object r = cf.getResult();
            Object c = coerce(r, rt);
            if (c != r) {
                cf.setResult(c);
            }
        } catch (Throwable ignore) {
        }
    }

    private static Class<?>[] paramTypesOf(java.lang.reflect.Member m) {
        if (m instanceof java.lang.reflect.Method) {
            return ((java.lang.reflect.Method) m).getParameterTypes();
        }
        if (m instanceof java.lang.reflect.Constructor) {
            return ((java.lang.reflect.Constructor<?>) m).getParameterTypes();
        }
        return null;
    }

    // Число любого бокса -> точный тип, который ждёт Java (int->Integer, long->Long и т.д.).
    private static Object coerce(Object v, Class<?> t) {
        if (!(v instanceof Number) || t == null) {
            return v;
        }
        Number n = (Number) v;
        if (t == int.class || t == Integer.class) return n.intValue();
        if (t == long.class || t == Long.class) return n.longValue();
        if (t == short.class || t == Short.class) return n.shortValue();
        if (t == byte.class || t == Byte.class) return n.byteValue();
        if (t == float.class || t == Float.class) return n.floatValue();
        if (t == double.class || t == Double.class) return n.doubleValue();
        return v;
    }

    // Перезагрузить все плагины из папки (без перезапуска приложения). Возвращает число плагинов.
    public static int reload() {
        try {
            // Plugin pills belong to the current Python runtime and must not survive reload.
            for (String row : listPlugins()) {
                String[] parts = row.split("\u001f", -1);
                if (parts.length > 0) DevGramPillStack.unregisterPluginPills(parts[0]);
            }
            // Снять все хуки плагинов перед перезагрузкой — AliuHook требует явного unhook
            // (иначе останутся висеть старые колбэки от предыдущей версии плагина).
            for (String pid : new java.util.ArrayList<>(pluginUnhooks.keySet())) {
                unhookPlugin(pid);
            }
            loaded = true;
            int n = loader().callAttr("reload_all", pluginsDir().getAbsolutePath()).toInt();
            refreshWantsUpdates();
            return n;
        } catch (Throwable e) {
            FileLog.e(e);
            return 0;
        }
    }

    /** Hot-reload one plugin, matching the development workflow of the Python SDK. */
    public static int reloadPlugin(String pluginId) {
        try {
            DevGramPillStack.unregisterPluginPills(pluginId);
            int n = loader().callAttr("reload_plugin", pluginId).toInt();
            if (n > 0) {
                refreshWantsUpdates();
                refreshRequestHooks();
            }
            return n;
        } catch (Throwable e) {
            FileLog.e(e);
            return 0;
        }
    }

    // Удалить плагин: файл + из реестра.
    public static boolean delete(String pluginId, String fileName) {
        try {
            DevGramPillStack.unregisterPluginPills(pluginId);
            if (fileName != null && !fileName.isEmpty()) {
                File f = new File(pluginsDir(), fileName);
                if (f.exists()) {
                    f.delete();
                }
            }
            loader().callAttr("unload_plugin", pluginId);
            // Для .dgplugin дочищаем распакованные файлы (.devgram/<id>); для .py — no-op.
            loader().callAttr("delete_package_files", pluginId);
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // ===== реестр проверенных плагинов =====

    public static String sha256(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    // Проверен ли плагин нами: только хеш в облачном реестре после решения модератора.
    public static boolean isVerified(String source) {
        return source != null && isVerifiedByHash(sha256(source));
    }

    // Тот же реестр, но по готовому хешу (для .dgplugin-пакетов ключ — sha256 архива).
    public static boolean isVerifiedByHash(String hash) {
        return hash != null && !hash.isEmpty() && verifiedHashes.contains(hash);
    }

    // Локальный (не облачный) набор хешей плагинов, доверенных через канал 🧩. Переживает
    // облачную синхронизацию реестра (её fetch/stream не трогает этот набор).
    private static java.util.Set<String> channelTrusted;

    private static java.util.Set<String> trustedFromChannel() {
        if (channelTrusted == null) {
            channelTrusted = new java.util.HashSet<>(
                    ApplicationLoader.applicationContext
                            .getSharedPreferences("devgram_plugins_trusted", 0)
                            .getStringSet("hashes", new java.util.HashSet<>()));
        }
        return channelTrusted;
    }

    // Пометить плагин доверенным, потому что он опубликован в канале-разработчике (🧩).
    public static void trustFromChannel(String source) {
        if (source != null) trustFromChannelByHash(sha256(source));
    }

    public static void trustFromChannelByHash(String hash) {
        if (hash == null || hash.isEmpty()) return;
        java.util.Set<String> set = new java.util.HashSet<>(trustedFromChannel());
        if (set.add(hash)) {
            channelTrusted = set;
            ApplicationLoader.applicationContext
                    .getSharedPreferences("devgram_plugins_trusted", 0)
                    .edit().putStringSet("hashes", set).apply();
        }
    }

    // Подтянуть реестр проверенных хешей из облака (на старте).
    public static void fetchVerified() {
        NET_QUEUE.execute(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(VERIFIED_BASE + "/plugins_verified.json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() != 200) {
                    return;
                }
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                String json = sb.toString().trim();
                if (json.isEmpty() || "null".equals(json)) {
                    return;
                }
                org.json.JSONObject o = new org.json.JSONObject(json);
                java.util.Set<String> set = new java.util.HashSet<>();
                for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                    set.add(it.next().toLowerCase());
                }
                verifiedHashes = set;
                FileLog.d("[DevGramPlugins] проверенных плагинов в реестре: " + set.size());
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // Может ли текущий пользователь управлять реестром (вошёл как админ команды).
    public static boolean canManageVerified() {
        return DevGramBadges.getAdminToken() != null;
    }

    // Пометить проверенным (командой): пишет хеш в облако + локально (оптимистично).
    public static boolean verify(String source) {
        return source != null && verifyByHash(sha256(source));
    }

    public static boolean verifyByHash(String hash) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || hash == null || hash.isEmpty()) {
            return false;
        }
        java.util.Set<String> set = new java.util.HashSet<>(verifiedHashes);
        set.add(hash);
        verifiedHashes = set;
        final String h = hash;
        NET_QUEUE.execute(() ->
                httpVerified("PUT", VERIFIED_BASE + "/plugins_verified/" + h + ".json?auth=" + token, "\"verified\""));
        return true;
    }

    // Снять проверку (командой): удаляет хеш из облака + локально.
    public static boolean unverify(String source) {
        return source != null && unverifyByHash(sha256(source));
    }

    public static boolean unverifyByHash(String hash) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || hash == null || hash.isEmpty()) {
            return false;
        }
        java.util.Set<String> set = new java.util.HashSet<>(verifiedHashes);
        set.remove(hash);
        verifiedHashes = set;
        final String h = hash;
        NET_QUEUE.execute(() ->
                httpVerified("DELETE", VERIFIED_BASE + "/plugins_verified/" + h + ".json?auth=" + token, null));
        return true;
    }

    // ================= Каталог плагинов (облако RTDB /plugins_catalog) =================
    // Запись каталога: карточка плагина с метаданными + исходником для установки.
    public static class CatalogEntry {
        public String id = "", name = "", author = "", version = "", desc = "", icon = "", channel = "", source = "", filter = "";
        // .dgplugin-пакет: бинарь хостится в архивном канале (packageChat/packageMsg), source пуст
        public boolean isPackage;
        public long packageSize;
        public String packageSha = "";
        public long packageChat;   // id архивного канала (пишет modbot после одобрения)
        public int packageMsg;     // id сообщения-документа в архиве
        public double rating;
        public int reviews;
        public long submitterId, submittedAt, updatedAt;
        public String submitterUid = "";
        public String submitterName = "", rejectionReason = "", submissionState = "";
        public boolean update, visible = true;
    }

    public interface CatalogCallback {
        void onResult(java.util.ArrayList<CatalogEntry> entries);
    }

    public static class Review {
        public long userId;
        public String name = "", text = "";
        public int rating;
        public long date;
    }

    public interface ReviewsCallback {
        void onResult(java.util.ArrayList<Review> reviews);
    }

    public static void fetchReviews(String pluginId, ReviewsCallback cb) {
        final String key = safeKey(pluginId == null ? "" : pluginId);
        NET_QUEUE.execute(() -> {
            java.util.ArrayList<Review> result = new java.util.ArrayList<>();
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugin_reviews/" + key + ".json").openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    String json = readHttp(c.getInputStream());
                    if (!json.isEmpty() && !"null".equals(json)) {
                        org.json.JSONObject root = new org.json.JSONObject(json);
                        for (java.util.Iterator<String> it = root.keys(); it.hasNext();) {
                            org.json.JSONObject o = root.optJSONObject(it.next());
                            if (o == null) continue;
                            Review r = new Review();
                            r.userId = o.optLong("userId");
                            r.name = o.optString("name", "Пользователь");
                            r.text = o.optString("text", "");
                            r.rating = o.optInt("rating", 0);
                            r.date = o.optLong("date", 0);
                            result.add(r);
                        }
                        result.sort((a, b) -> Long.compare(b.date, a.date));
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
            AndroidUtilities.runOnUIThread(() -> cb.onResult(result));
        });
    }

    public static void saveReview(String pluginId, int rating, String text, BoolCallback cb) {
        long userId = myId();
        String name = "Пользователь";
        TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId);
        if (user != null) name = UserObject.getUserName(user);
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("userId", userId);
            o.put("name", name);
            o.put("rating", Math.max(1, Math.min(5, rating)));
            o.put("text", text == null ? "" : text.trim());
            o.put("date", System.currentTimeMillis());
            o.put("ownerUid", firebaseAnonUid());
            String url = RTDB + "/plugin_reviews/" + safeKey(pluginId) + "/" + userId + ".json";
            String body = o.toString();
            NET_QUEUE.execute(() -> {
                boolean ok = httpVerified("PUT", url, body);
                if (ok) refreshReviewStats(pluginId);
                AndroidUtilities.runOnUIThread(() -> cb.onResult(ok));
            });
        } catch (Throwable e) {
            AndroidUtilities.runOnUIThread(() -> cb.onResult(false));
        }
    }

    public static void deleteOwnReview(String pluginId, BoolCallback cb) {
        NET_QUEUE.execute(() -> {
            boolean ok = httpVerified("DELETE", RTDB + "/plugin_reviews/" + safeKey(pluginId) + "/" + myId() + ".json", null);
            if (ok) refreshReviewStats(pluginId);
            AndroidUtilities.runOnUIThread(() -> cb.onResult(ok));
        });
    }

    /** Удаление чужого отзыва для участника DevGram с правом Разработчик. */
    public static void deleteReviewAsDeveloper(String pluginId, long reviewUserId, BoolCallback cb) {
        if (!DevGramBadges.hasDeveloperFeatures(myId())) {
            AndroidUtilities.runOnUIThread(() -> cb.onResult(false));
            return;
        }
        NET_QUEUE.execute(() -> {
            boolean ok = httpVerified("DELETE", RTDB + "/plugin_reviews/" + safeKey(pluginId) + "/" + reviewUserId + ".json", null);
            if (ok) refreshReviewStats(pluginId);
            AndroidUtilities.runOnUIThread(() -> cb.onResult(ok));
        });
    }

    private static void refreshReviewStats(String pluginId) {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugin_reviews/" + safeKey(pluginId) + ".json").openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            int count = 0;
            double sum = 0;
            if (c.getResponseCode() == 200) {
                String json = readHttp(c.getInputStream());
                if (!json.isEmpty() && !"null".equals(json)) {
                    org.json.JSONObject root = new org.json.JSONObject(json);
                    for (java.util.Iterator<String> it = root.keys(); it.hasNext();) {
                        org.json.JSONObject review = root.optJSONObject(it.next());
                        if (review == null) continue;
                        sum += Math.max(1, Math.min(5, review.optInt("rating", 0)));
                        count++;
                    }
                }
            }
            org.json.JSONObject stats = new org.json.JSONObject();
            stats.put("rating", count == 0 ? 0 : sum / count);
            stats.put("reviews", count);
            httpVerified("PATCH", RTDB + "/plugins_catalog/" + safeKey(pluginId) + ".json", stats.toString());
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static void reportReview(String pluginId, long reviewUserId, String reason, BoolCallback cb) {
        try {
            String ownerUid = firebaseAnonUid();
            String token = firebaseAnonToken();
            if (ownerUid == null || ownerUid.isEmpty() || token == null || token.isEmpty()) {
                cb.onResult(false);
                return;
            }
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("pluginId", pluginId);
            o.put("reviewUserId", reviewUserId);
            o.put("reporterId", myId());
            o.put("reason", reason == null ? "" : reason.trim());
            o.put("date", System.currentTimeMillis());
            o.put("ownerUid", ownerUid);
            String key = safeKey(pluginId) + "_" + reviewUserId + "_" + myId();
            org.json.JSONObject receipt = new org.json.JSONObject();
            receipt.put("ownerUid", ownerUid);
            receipt.put("reporterId", myId());
            receipt.put("reviewUserId", reviewUserId);
            receipt.put("date", System.currentTimeMillis());
            org.json.JSONObject update = new org.json.JSONObject();
            update.put("plugin_review_reports/" + key, o);
            update.put("plugin_review_report_receipts/" + safeKey(pluginId) + "/" + reviewUserId + "/" + ownerUid, receipt);
            NET_QUEUE.execute(() -> {
                boolean ok = httpVerified("PATCH", RTDB + "/.json?auth=" + token, update.toString());
                AndroidUtilities.runOnUIThread(() -> cb.onResult(ok));
            });
        } catch (Throwable e) {
            cb.onResult(false);
        }
    }

    public static void hasReportedReview(String pluginId, long reviewUserId, BoolCallback cb) {
        NET_QUEUE.execute(() -> {
            boolean reported = false;
            java.net.HttpURLConnection c = null;
            try {
                String uid = firebaseAnonUid();
                String token = firebaseAnonToken();
                if (uid != null && !uid.isEmpty() && token != null && !token.isEmpty()) {
                    String url = RTDB + "/plugin_review_report_receipts/" + safeKey(pluginId) + "/" + reviewUserId
                            + "/" + uid + ".json?auth=" + java.net.URLEncoder.encode(token, "UTF-8");
                    c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(15000);
                    if (c.getResponseCode() == 200) {
                        String raw = readHttp(c.getInputStream()).trim();
                        reported = !raw.isEmpty() && !"null".equals(raw);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
            final boolean result = reported;
            AndroidUtilities.runOnUIThread(() -> cb.onResult(result));
        });
    }

    public static void reportPlugin(String pluginId, String reason, BoolCallback cb) {
        try {
            String ownerUid = firebaseAnonUid();
            String token = firebaseAnonToken();
            if (ownerUid == null || ownerUid.isEmpty() || token == null || token.isEmpty()) {
                cb.onResult(false);
                return;
            }
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("pluginId", pluginId == null ? "" : pluginId);
            o.put("reporterId", myId());
            o.put("reason", reason == null ? "" : reason.trim());
            o.put("date", System.currentTimeMillis());
            o.put("ownerUid", ownerUid);
            String key = safeKey(pluginId == null ? "" : pluginId) + "_" + myId();
            org.json.JSONObject receipt = new org.json.JSONObject();
            receipt.put("ownerUid", ownerUid);
            receipt.put("reporterId", myId());
            receipt.put("date", System.currentTimeMillis());
            org.json.JSONObject update = new org.json.JSONObject();
            update.put("plugin_reports/" + key, o);
            update.put("plugin_report_receipts/" + safeKey(pluginId == null ? "" : pluginId) + "/" + ownerUid, receipt);
            NET_QUEUE.execute(() -> {
                boolean ok = httpVerified("PATCH", RTDB + "/.json?auth=" + token, update.toString());
                AndroidUtilities.runOnUIThread(() -> cb.onResult(ok));
            });
        } catch (Throwable e) { cb.onResult(false); }
    }

    public static void hasReportedPlugin(String pluginId, BoolCallback cb) {
        NET_QUEUE.execute(() -> {
            boolean reported = false;
            java.net.HttpURLConnection c = null;
            try {
                String uid = firebaseAnonUid();
                String token = firebaseAnonToken();
                if (uid != null && !uid.isEmpty() && token != null && !token.isEmpty()) {
                    String url = RTDB + "/plugin_report_receipts/" + safeKey(pluginId == null ? "" : pluginId)
                            + "/" + uid + ".json?auth=" + java.net.URLEncoder.encode(token, "UTF-8");
                    c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    if (c.getResponseCode() == 200) {
                        String raw = readHttp(c.getInputStream()).trim();
                        reported = !raw.isEmpty() && !"null".equals(raw);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
            final boolean result = reported;
            AndroidUtilities.runOnUIThread(() -> cb.onResult(result));
        });
    }

    // token — токен модератора (RTDB-правило на /plugin_history требует именно admin/mod auth).
    // Раньше запрос уходил вообще без ?auth= и сервер всегда отклонял запись (403) — история
    // модераторских действий по факту никогда не сохранялась. Вызовы от обычного пользователя
    // (заявка на публикацию) токена не имеют — передают null, запись как и раньше не пишется:
    // это ограничение самого правила (история — только для admin/mod), а не баг этого вызова.
    private static void appendPluginHistory(String pluginId, String action, String details, long actorId, String actorName, String token) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("action", action);
            o.put("details", details == null ? "" : details);
            o.put("actorId", actorId);
            o.put("actorName", actorName == null ? "" : actorName);
            o.put("date", System.currentTimeMillis());
            String key = System.currentTimeMillis() + "_" + Math.abs((action + actorId).hashCode());
            String url = RTDB + "/plugin_history/" + safeKey(pluginId) + "/" + key + ".json";
            if (token != null && !token.isEmpty()) {
                url += "?auth=" + token;
            }
            httpVerified("PUT", url, o.toString());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static class HistoryEntry {
        public String action = "", details = "", actorName = "";
        public long actorId, date;
    }

    public interface HistoryCallback { void onResult(java.util.ArrayList<HistoryEntry> items); }

    public static void fetchPluginHistory(String pluginId, HistoryCallback cb) {
        final String id = safeKey(pluginId == null ? "" : pluginId);
        NET_QUEUE.execute(() -> {
            java.util.ArrayList<HistoryEntry> result = new java.util.ArrayList<>();
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugin_history/" + id + ".json").openConnection();
                c.setConnectTimeout(12000); c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    String raw = readHttp(c.getInputStream());
                    if (!raw.isEmpty() && !"null".equals(raw)) {
                        org.json.JSONObject root = new org.json.JSONObject(raw);
                        for (java.util.Iterator<String> it = root.keys(); it.hasNext();) {
                            org.json.JSONObject o = root.optJSONObject(it.next()); if (o == null) continue;
                            HistoryEntry h = new HistoryEntry();
                            h.action = o.optString("action", ""); h.details = o.optString("details", "");
                            h.actorId = o.optLong("actorId"); h.actorName = o.optString("actorName", ""); h.date = o.optLong("date");
                            result.add(h);
                        }
                        result.sort((a, b) -> Long.compare(b.date, a.date));
                    }
                }
            } catch (Throwable e) { FileLog.e(e); } finally { if (c != null) c.disconnect(); }
            AndroidUtilities.runOnUIThread(() -> cb.onResult(result));
        });
    }

    public static class ReviewReport {
        public String key = "", pluginId = "", reason = "";
        public long reviewUserId, reporterId, date;
    }

    public interface ReportsCallback { void onResult(java.util.ArrayList<ReviewReport> items); }

    public static void fetchReviewReports(ReportsCallback cb) {
        NET_QUEUE.execute(() -> {
            java.util.ArrayList<ReviewReport> result = new java.util.ArrayList<>();
            java.net.HttpURLConnection c = null;
            try {
                String token = DevGramBadges.getAdminToken();
                if (token == null) { AndroidUtilities.runOnUIThread(() -> cb.onResult(result)); return; }
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugin_review_reports.json?auth=" + java.net.URLEncoder.encode(token, "UTF-8")).openConnection();
                c.setConnectTimeout(12000); c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    String raw = readHttp(c.getInputStream());
                    if (!raw.isEmpty() && !"null".equals(raw)) {
                        org.json.JSONObject root = new org.json.JSONObject(raw);
                        for (java.util.Iterator<String> it = root.keys(); it.hasNext();) {
                            String key = it.next(); org.json.JSONObject o = root.optJSONObject(key); if (o == null) continue;
                            ReviewReport r = new ReviewReport(); r.key = key; r.pluginId = o.optString("pluginId", "");
                            r.reason = o.optString("reason", ""); r.reviewUserId = o.optLong("reviewUserId");
                            r.reporterId = o.optLong("reporterId"); r.date = o.optLong("date"); result.add(r);
                        }
                        result.sort((a, b) -> Long.compare(b.date, a.date));
                    }
                }
            } catch (Throwable e) { FileLog.e(e); } finally { if (c != null) c.disconnect(); }
            AndroidUtilities.runOnUIThread(() -> cb.onResult(result));
        });
    }

    public static class PluginReport {
        public String key = "", pluginId = "", reason = "";
        public long reporterId, date;
    }
    public interface PluginReportsCallback { void onResult(java.util.ArrayList<PluginReport> items); }
    public static void fetchPluginReports(PluginReportsCallback cb) {
        NET_QUEUE.execute(() -> {
            java.util.ArrayList<PluginReport> result = new java.util.ArrayList<>(); java.net.HttpURLConnection c = null;
            try {
                String token = DevGramBadges.getAdminToken();
                if (token != null) {
                    c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugin_reports.json?auth=" + java.net.URLEncoder.encode(token, "UTF-8")).openConnection();
                    if (c.getResponseCode() == 200) {
                        String raw = readHttp(c.getInputStream());
                        if (!raw.isEmpty() && !"null".equals(raw)) {
                            org.json.JSONObject root = new org.json.JSONObject(raw);
                            for (java.util.Iterator<String> it = root.keys(); it.hasNext();) { String key = it.next(); org.json.JSONObject o = root.optJSONObject(key); if (o == null) continue; PluginReport r = new PluginReport(); r.key=key; r.pluginId=o.optString("pluginId",""); r.reason=o.optString("reason",""); r.reporterId=o.optLong("reporterId"); r.date=o.optLong("date"); result.add(r); }
                            result.sort((a,b)->Long.compare(b.date,a.date));
                        }
                    }
                }
            } catch(Throwable e){FileLog.e(e);} finally {if(c!=null)c.disconnect();}
            AndroidUtilities.runOnUIThread(() -> cb.onResult(result));
        });
    }
    public static boolean resolvePluginReport(PluginReport report, boolean hidePlugin, String reason) {
        String token=DevGramBadges.getAdminToken(); if(token==null||report==null)return false;
        final String why=reason==null?"":reason.trim();NET_QUEUE.execute(()->{ if(hidePlugin) { try { org.json.JSONObject o=new org.json.JSONObject(); o.put("hidden",true);o.put("reason",why);o.put("date",System.currentTimeMillis()); httpVerified("PUT",RTDB+"/plugins_hidden/"+safeKey(report.pluginId)+".json?auth="+token,o.toString()); httpVerified("PATCH",RTDB+"/plugins_catalog/"+safeKey(report.pluginId)+".json?auth="+token,"{\"visible\":false}"); }catch(Throwable e){FileLog.e(e);} } notifyPluginUser(report.reporterId,hidePlugin?"plugin_report_accepted":"plugin_report_rejected",hidePlugin?"Жалоба подтверждена":"Жалоба отклонена",why,report.pluginId,token);httpVerified("DELETE",RTDB+"/plugin_reports/"+report.key+".json?auth="+token,null); }); return true;
    }
    public static boolean resolvePluginReport(PluginReport report,boolean hidePlugin){return resolvePluginReport(report,hidePlugin,hidePlugin?"Нарушение подтверждено":"Нарушение не подтверждено");}
    public static boolean resolvePluginReport(PluginReport report,int action,String reason){String token=DevGramBadges.getAdminToken();if(token==null||report==null)return false;final String why=reason==null?"":reason.trim();NET_QUEUE.execute(()->{if(action>0){try{org.json.JSONObject o=new org.json.JSONObject();o.put("hidden",true);o.put("reason",why);o.put("date",System.currentTimeMillis());o.put("blocked",action==2);httpVerified("PUT",RTDB+"/plugins_hidden/"+safeKey(report.pluginId)+".json?auth="+token,o.toString());String raw=readNode(RTDB+"/plugins_catalog/"+safeKey(report.pluginId)+".json");httpVerified("PATCH",RTDB+"/plugins_catalog/"+safeKey(report.pluginId)+".json?auth="+token,"{\"visible\":false}");if(action==2&&raw!=null&&!raw.isEmpty()&&!"null".equals(raw)){String source=new org.json.JSONObject(raw).optString("source","");String hash=sha256(source);if(!hash.isEmpty())httpVerified("PUT",RTDB+"/plugins_blocked/"+hash+".json?auth="+token,"\"blocked\"");}}catch(Throwable e){FileLog.e(e);}}notifyPluginUser(report.reporterId,action>0?"plugin_report_accepted":"plugin_report_rejected",action>0?"Жалоба подтверждена":"Жалоба отклонена",why,report.pluginId,token);httpVerified("DELETE",RTDB+"/plugin_reports/"+report.key+".json?auth="+token,null);});return true;}

    public static boolean resolveReviewReport(ReviewReport report, boolean deleteReview, String reason) {
        String token = DevGramBadges.getAdminToken(); if (token == null || report == null) return false;
        final String why=reason==null?"":reason.trim();NET_QUEUE.execute(() -> {
            if (deleteReview) {
                httpVerified("DELETE", RTDB + "/plugin_reviews/" + safeKey(report.pluginId) + "/" + report.reviewUserId + ".json?auth=" + token, null);
                refreshReviewStats(report.pluginId);
            }
            notifyPluginUser(report.reporterId,deleteReview?"review_report_accepted":"review_report_rejected",deleteReview?"Отзыв удалён по жалобе":"Жалоба на отзыв отклонена",why,report.pluginId,token);
            httpVerified("DELETE", RTDB + "/plugin_review_reports/" + report.key + ".json?auth=" + token, null);
        });
        return true;
    }
    public static boolean resolveReviewReport(ReviewReport report,boolean deleteReview){return resolveReviewReport(report,deleteReview,deleteReview?"Нарушение подтверждено":"Нарушение не подтверждено");}

    private static String readHttp(java.io.InputStream in) throws Exception {
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString().trim();
    }
    static String readPublicHttp(java.io.InputStream in) throws Exception { return readHttp(in); }
    private static void notifyPluginUser(long userId,String type,String title,String reason,String pluginId,String token){if(userId<=0||token==null)return;try{org.json.JSONObject o=new org.json.JSONObject();o.put("type",type);o.put("title",title);o.put("message",(pluginId==null||pluginId.isEmpty()?"":"Плагин: "+pluginId+"\n")+"Причина: "+(reason==null||reason.isEmpty()?"не указана":reason));o.put("pluginId",pluginId==null?"":pluginId);o.put("reason",reason==null?"":reason);o.put("date",System.currentTimeMillis());String key=System.currentTimeMillis()+"_"+Math.abs((type+pluginId+userId).hashCode());httpVerified("PUT",RTDB+"/plugin_user_notifications/"+userId+"/"+key+".json?auth="+token,o.toString());}catch(Throwable e){FileLog.e(e);}}

    // Забрать опубликованный каталог.
    public static void fetchCatalog(final CatalogCallback cb) {
        fetchEntries("plugins_catalog", entries -> refreshCatalogReviewStats(entries, cb));
    }

    public static void fetchCatalogFast(final CatalogCallback initial, final CatalogCallback statsUpdated) {
        fetchEntries("plugins_catalog", entries -> {
            initial.onResult(entries);
            refreshCatalogReviewStats(entries, statsUpdated);
        });
    }

    private static void refreshCatalogReviewStats(java.util.ArrayList<CatalogEntry> entries, CatalogCallback cb) {
        NET_QUEUE.execute(() -> {
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugin_reviews.json").openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(18000);
                if (c.getResponseCode() == 200) {
                    String raw = readHttp(c.getInputStream());
                    org.json.JSONObject allReviews = raw.isEmpty() || "null".equals(raw) ? null : new org.json.JSONObject(raw);
                    for (CatalogEntry entry : entries) {
                        int count = 0;
                        double sum = 0;
                        org.json.JSONObject pluginReviews = allReviews == null ? null : allReviews.optJSONObject(safeKey(entry.id));
                        if (pluginReviews != null) {
                            for (java.util.Iterator<String> it = pluginReviews.keys(); it.hasNext();) {
                                org.json.JSONObject review = pluginReviews.optJSONObject(it.next());
                                if (review == null) continue;
                                int value = review.optInt("rating", 0);
                                if (value < 1 || value > 5) continue;
                                sum += value;
                                count++;
                            }
                        }
                        entry.reviews = count;
                        entry.rating = count == 0 ? 0 : sum / count;
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
            AndroidUtilities.runOnUIThread(() -> cb.onResult(entries));
        });
    }
    public static void fetchCatalogEntry(String pluginId, CatalogCallback cb) {
        final String key=safeKey(pluginId==null?"":pluginId);NET_QUEUE.execute(()->{java.util.ArrayList<CatalogEntry> out=new java.util.ArrayList<>();try{String raw=readNode(RTDB+"/plugins_catalog/"+key+".json");if(raw!=null&&!raw.isEmpty()&&!"null".equals(raw)){org.json.JSONObject o=new org.json.JSONObject(raw);CatalogEntry e=new CatalogEntry();e.id=o.optString("id",key);e.name=o.optString("name",e.id);e.author=o.optString("author","");e.version=o.optString("version","");e.desc=o.optString("desc","");e.icon=o.optString("icon","");e.channel=o.optString("channel","");e.source=o.optString("source","");e.filter=o.optString("filter","");e.rating=o.optDouble("rating",0);e.reviews=o.optInt("reviews",0);e.submitterId=o.optLong("submitterId",0);e.submittedAt=o.optLong("submittedAt",0);e.updatedAt=o.optLong("updatedAt",0);e.visible=o.optBoolean("visible",true);e.isPackage=o.optBoolean("isPackage",false);e.packageSize=o.optLong("packageSize",0);e.packageSha=o.optString("packageSha","");e.packageChat=o.optLong("packageChat",0);e.packageMsg=o.optInt("packageMsg",0);out.add(e);}}catch(Throwable ex){FileLog.e(ex);}AndroidUtilities.runOnUIThread(()->cb.onResult(out));});
    }

    // Забрать заявки на модерацию.
    public static void fetchPending(final CatalogCallback cb) {
        fetchEntries("plugins_pending", cb);
    }

    public static void fetchRejected(final CatalogCallback cb) {
        fetchEntries("plugins_rejected", cb);
    }

    public static void fetchMySubmissions(final CatalogCallback cb) {
        final long uid = myId();
        java.util.ArrayList<CatalogEntry> result = new java.util.ArrayList<>();
        final int[] remaining = {3};
        CatalogCallback pending = items -> {
            for (CatalogEntry e : items) { e.submissionState = "pending"; if (e.submitterId == uid) result.add(e); }
            if (--remaining[0] == 0) cb.onResult(result);
        };
        CatalogCallback published = items -> {
            for (CatalogEntry e : items) { e.submissionState = "published"; if (e.submitterId == uid) result.add(e); }
            if (--remaining[0] == 0) cb.onResult(result);
        };
        CatalogCallback rejected = items -> {
            for (CatalogEntry e : items) { e.submissionState = "rejected"; if (e.submitterId == uid) result.add(e); }
            if (--remaining[0] == 0) cb.onResult(result);
        };
        fetchEntries("plugins_pending", pending);
        fetchEntries("plugins_catalog", published);
        fetchEntries("plugins_rejected", rejected);
    }

    // Общий загрузчик списка CatalogEntry из узла RTDB (catalog / pending).
    private static void fetchEntries(final String node, final CatalogCallback cb) {
        NET_QUEUE.execute(() -> {
            final java.util.ArrayList<CatalogEntry> list = new java.util.ArrayList<>();
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/" + node + ".json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();
                    String json = sb.toString().trim();
                    if (!json.isEmpty() && !"null".equals(json)) {
                        org.json.JSONObject obj = new org.json.JSONObject(json);
                        for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                            String key = it.next();
                            org.json.JSONObject o = obj.optJSONObject(key);
                            if (o == null) {
                                continue;
                            }
                            CatalogEntry e = new CatalogEntry();
                            e.id = o.optString("id", key);
                            e.name = o.optString("name", e.id);
                            e.author = o.optString("author", "");
                            e.version = o.optString("version", "");
                            e.desc = o.optString("desc", "");
                            e.icon = o.optString("icon", "");
                            e.channel = o.optString("channel", "");
                            e.source = o.optString("source", "");
                            e.filter = o.optString("filter", "");
                            e.rating = o.optDouble("rating", 0);
                            e.reviews = o.optInt("reviews", 0);
                            e.submitterId = o.optLong("submitterId", 0);
                            e.submitterUid = o.optString("submitterUid", "");
                            e.submitterName = o.optString("submitterName", "");
                            e.submittedAt = o.optLong("submittedAt", 0);
                            e.updatedAt = o.optLong("updatedAt", 0);
                            e.update = o.optBoolean("update", false);
                            e.rejectionReason = o.optString("reason", "");
                            e.visible = o.optBoolean("visible", true);
                            e.isPackage = o.optBoolean("isPackage", false);
                            e.packageSize = o.optLong("packageSize", 0);
                            e.packageSha = o.optString("packageSha", "");
                            e.packageChat = o.optLong("packageChat", 0);
                            e.packageMsg = o.optInt("packageMsg", 0);
                            if ("plugins_catalog".equals(node) && !e.visible) {
                                continue;
                            }
                            list.add(e);
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
            AndroidUtilities.runOnUIThread(() -> cb.onResult(list));
        });
    }

    public interface BoolCallback {
        void onResult(boolean value);
    }

    public interface SubmissionCallback {
        void onResult(int status); // 0 нет заявки, 1 модерация, 2 одобрено, 3 отклонено, 4 заблокировано
    }

    // Плагин уже «в обработке», повторно публиковать незачем: на модерации (plugins_pending),
    // одобрен (plugins_catalog), отклонён (plugins_rejected) или заблокирован по файлу (plugins_blocked).
    // Лёгкая shallow-проверка по ключу (id / хеш источника), результат — на UI-потоке.
    public static void isPluginSubmitted(final String id, final String source, final BoolCallback cb) {
        getPluginSubmissionStatus(id, source, status -> cb.onResult(status != 0));
    }

    public static void getPluginSubmissionStatus(final String id, final String source, final SubmissionCallback cb) {
        if (id == null || id.trim().isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> cb.onResult(0));
            return;
        }
        final String key = safeKey(id.trim());
        NET_QUEUE.execute(() -> {
            int status = 0;
            if (nodeHasKey("plugins_pending", key)) status = 1;
            else if (catalogEntryIsVisible(key)) status = 2;
            else if (nodeHasKey("plugins_rejected", key)) status = 3;
            else if (source != null && !source.isEmpty() && nodeHasKey("plugins_blocked", sha256(source))) status = 4;
            final int result = status;
            AndroidUtilities.runOnUIThread(() -> cb.onResult(result));
        });
    }

    private static boolean catalogEntryIsVisible(String key){String raw=readNode(RTDB+"/plugins_catalog/"+key+".json");if(raw==null||raw.isEmpty()||"null".equals(raw))return false;try{return new org.json.JSONObject(raw).optBoolean("visible",true);}catch(Throwable ignore){return false;}}

    public static void canWithdrawPending(String id, BoolCallback cb) {
        final String key = safeKey(id == null ? "" : id);
        NET_QUEUE.execute(() -> {
            boolean allowed = false;
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugins_pending/" + key + "/submitterId.json").openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                if (c.getResponseCode() == 200) {
                    String value = readHttp(c.getInputStream()).replace("\"", "");
                    allowed = String.valueOf(myId()).equals(value);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
            final boolean result = allowed;
            AndroidUtilities.runOnUIThread(() -> cb.onResult(result));
        });
    }

    public static void withdrawPending(String id, BoolCallback cb) {
        canWithdrawPending(id, allowed -> {
            if (!allowed) {
                cb.onResult(false);
                return;
            }
            NET_QUEUE.execute(() -> {
                boolean ok = httpVerified("DELETE", RTDB + "/plugins_pending/" + safeKey(id) + ".json", null);
                AndroidUtilities.runOnUIThread(() -> cb.onResult(ok));
            });
        });
    }

    public interface StringCallback {
        void onResult(String value);
    }

    public static void fetchRejectionReason(String id, StringCallback cb) {
        final String key = safeKey(id == null ? "" : id);
        NET_QUEUE.execute(() -> {
            String reason = "";
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugins_rejected/" + key + "/reason.json").openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                if (c.getResponseCode() == 200) {
                    String raw = readHttp(c.getInputStream());
                    if (!"null".equals(raw)) reason = new org.json.JSONTokener(raw).nextValue().toString();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
            final String result = reason;
            AndroidUtilities.runOnUIThread(() -> cb.onResult(result));
        });
    }

    public static void clearRejectedForResubmit(String id) {
        NET_QUEUE.execute(() ->
                httpVerified("DELETE", RTDB + "/plugins_rejected/" + safeKey(id) + ".json", null));
    }

    // true, если по пути RTDB/node/key есть значение (shallow — тянет только сам ключ, не весь узел).
    private static boolean nodeHasKey(String node, String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/" + node + "/" + key + ".json?shallow=true").openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            if (c.getResponseCode() == 200) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                String s = sb.toString().trim();
                return !s.isEmpty() && !"null".equals(s);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
        return false;
    }

    private static org.json.JSONObject entryJson(CatalogEntry e) throws Exception {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("id", e.id);
        o.put("name", e.name);
        o.put("author", e.author);
        o.put("version", e.version);
        o.put("desc", e.desc);
        o.put("icon", e.icon);
        o.put("channel", e.channel);
        o.put("source", e.source);
        o.put("filter", e.filter == null ? "" : e.filter);
        if (e.rating > 0) o.put("rating", e.rating);
        if (e.reviews > 0) o.put("reviews", e.reviews);
        o.put("submitterId", e.submitterId);
        o.put("submitterUid", e.submitterUid == null ? "" : e.submitterUid);
        o.put("submitterName", e.submitterName == null ? "" : e.submitterName);
        o.put("submittedAt", e.submittedAt);
        o.put("updatedAt", e.updatedAt);
        o.put("update", e.update);
        if (e.isPackage) {
            o.put("isPackage", true);
            if (e.packageSize > 0) o.put("packageSize", e.packageSize);
            o.put("packageSha", e.packageSha == null ? "" : e.packageSha);
            if (e.packageChat != 0) o.put("packageChat", e.packageChat);
            if (e.packageMsg != 0) o.put("packageMsg", e.packageMsg);
        }
        return o;
    }

    private static String safeKey(String id) {
        return id.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    public static String validateCatalogEntry(CatalogEntry e) {
        if (e == null) return "Пустая заявка";
        if (e.id == null || !e.id.matches("[a-zA-Z0-9_.\\-]{2,64}")) return "ID: 2–64 символа, только буквы, цифры, _ . и -";
        if (e.name == null || e.name.trim().length() < 2) return "Укажите название плагина";
        if (e.version == null || e.version.trim().isEmpty()) return "Укажите версию плагина";
        if (e.author == null || e.author.trim().isEmpty()) return "Укажите автора плагина";
        if (e.isPackage) {
            // .dgplugin-пакет: исходника нет, бинарь хостится в архиве.
            // DevGram: лимит размера пакета снят (нужны большие нативные плагины, напр. VPN-ядро).
            if (e.packageSize <= 0) return "Пустой пакет";
            return "";
        }
        if (e.source == null || e.source.trim().isEmpty()) return "Исходник плагина пуст";
        // DevGram: лимит размера исходника плагина снят.
        if (parseMeta(e.source).isEmpty()) return "Не удалось разобрать метаданные или синтаксис плагина";
        String lower = e.source.toLowerCase(java.util.Locale.US);
        if (lower.contains("import subprocess") || lower.contains("from subprocess") || lower.contains("os.system(")) {
            return "Запрещён запуск системных команд (subprocess/os.system)";
        }
        return "";
    }

    // Отправить плагин НА МОДЕРАЦИЮ (узел plugins_pending, запись открыта). Модератор одобрит →
    // плагин попадёт в каталог. Заблокированный файл отправить нельзя. Код: 1 — ок, 0 — ошибка, -1 — заблокирован.
    public static int publishToCatalog(CatalogEntry e) {
        if (e == null || e.id == null || e.id.isEmpty()) {
            return 0;
        }
        if (isBlocked(e.source)) {
            return -1;
        }
        if (!validateCatalogEntry(e).isEmpty()) {
            return -2;
        }
        try {
            long userId = myId();
            TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId);
            e.submitterId = userId;
            e.submitterName = user == null ? String.valueOf(userId) : UserObject.getUserName(user);
            e.submittedAt = System.currentTimeMillis();
            e.updatedAt = e.submittedAt;
            e.submitterUid = firebaseAnonUid();
            final String body = entryJson(e).toString();
            final String key = safeKey(e.id);
            NET_QUEUE.execute(() ->
            {
                httpVerified("PUT", RTDB + "/plugins_pending/" + key + ".json", body);
                appendPluginHistory(e.id, e.update ? "update_submitted" : "submitted", e.version, e.submitterId, e.submitterName, null);
            });
            return 1;
        } catch (Throwable ex) {
            FileLog.e(ex);
            return 0;
        }
    }

    /** Асинхронная публикация с подтверждением фактической записи Firebase. */
    public static void publishToCatalog(CatalogEntry e, SubmissionCallback cb) {
        if (e == null || e.id == null || e.id.isEmpty()) { AndroidUtilities.runOnUIThread(() -> cb.onResult(0)); return; }
        if (isBlocked(e.source)) { AndroidUtilities.runOnUIThread(() -> cb.onResult(-1)); return; }
        if (!validateCatalogEntry(e).isEmpty()) { AndroidUtilities.runOnUIThread(() -> cb.onResult(-2)); return; }
        NET_QUEUE.execute(() -> {
            int result = 0;
            try {
                long userId = myId();
                TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId);
                e.submitterId = userId;
                e.submitterName = user == null ? String.valueOf(userId) : UserObject.getUserName(user);
                e.submittedAt = System.currentTimeMillis(); e.updatedAt = e.submittedAt;
                e.submitterUid = firebaseAnonUid();
                if (e.submitterUid != null && !e.submitterUid.isEmpty()) {
                    String key = safeKey(e.id);
                    boolean written = false;
                    for (int attempt = 0; attempt < 3 && !written; attempt++) {
                        written = httpVerified("PUT", RTDB + "/plugins_pending/" + key + ".json", entryJson(e).toString());
                    }
                    if (written && nodeHasKey("plugins_pending", key)) {
                        appendPluginHistory(e.id, e.update ? "update_submitted" : "submitted", e.version, e.submitterId, e.submitterName, null);
                        result = 1;
                    }
                }
            } catch (Throwable ex) { FileLog.e(ex); }
            final int value = result;
            AndroidUtilities.runOnUIThread(() -> cb.onResult(value));
        });
    }

    // Одобрить заявку (модератор): перенести из pending в каталог + убрать из pending. Нужен токен.
    public static boolean approvePending(CatalogEntry e) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || e == null || e.id == null || e.id.isEmpty()) {
            return false;
        }
        try {
            final String body = entryJson(e).toString();
            final String key = safeKey(e.id);
            final String hash = sha256(e.source == null ? "" : e.source);
            NET_QUEUE.execute(() -> {
                String previous = readNode(RTDB + "/plugins_catalog/" + key + ".json");
                if (previous != null && !previous.isEmpty() && !"null".equals(previous))
                    httpVerified("PUT", RTDB + "/plugin_backups/" + key + "/" + System.currentTimeMillis() + ".json?auth=" + token, previous);
                httpVerified("PUT", RTDB + "/plugins_catalog/" + key + ".json?auth=" + token, body);
                if (!hash.isEmpty()) {
                    httpVerified("PUT", VERIFIED_BASE + "/plugins_verified/" + hash + ".json?auth=" + token, "\"verified\"");
                    java.util.Set<String> set = new java.util.HashSet<>(verifiedHashes);
                    set.add(hash);
                    verifiedHashes = set;
                }
                httpVerified("DELETE", RTDB + "/plugins_pending/" + key + ".json?auth=" + token, null);
                httpVerified("DELETE", RTDB + "/plugins_rejected/" + key + ".json?auth=" + token, null);
                appendPluginHistory(e.id, "approved", e.version, myId(), "Модератор", token);
            });
            return true;
        } catch (Throwable ex) {
            FileLog.e(ex);
            return false;
        }
    }

    public static boolean rollbackPlugin(String pluginId, String backupKey) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || pluginId == null || backupKey == null) return false;
        String raw = readNode(RTDB + "/plugin_backups/" + safeKey(pluginId) + "/" + safeKey(backupKey) + ".json");
        if (raw == null || raw.isEmpty() || "null".equals(raw)) return false;
        final String key = safeKey(pluginId);
        NET_QUEUE.execute(() -> { httpVerified("PUT", RTDB + "/plugins_catalog/" + key + ".json?auth=" + token, raw); appendPluginHistory(pluginId, "rollback", backupKey, myId(), "Модератор", token); });
        return true;
    }

    private static String readNode(String url) {
        java.net.HttpURLConnection c = null;
        try { c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(12000); return c.getResponseCode() == 200 ? readHttp(c.getInputStream()) : ""; }
        catch (Throwable e) { FileLog.e(e); return ""; } finally { if (c != null) c.disconnect(); }
    }

    // Отклонить заявку (модератор): убрать из pending. block=true — ещё и заблокировать файл.
    public static boolean rejectPending(CatalogEntry e, boolean block, String reason) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || e == null || e.id == null || e.id.isEmpty()) {
            return false;
        }
        final String key = safeKey(e.id);
        final String hash = (block && e.source != null) ? sha256(e.source) : null;
        if (hash != null) {
            java.util.Set<String> set = new java.util.HashSet<>(blockedHashes());
            set.add(hash);
            blocked = set;
        }
        NET_QUEUE.execute(() -> {
            httpVerified("DELETE", RTDB + "/plugins_pending/" + key + ".json?auth=" + token, null);
            try {
                org.json.JSONObject rejected = entryJson(e);
                rejected.put("reason", reason == null ? "" : reason.trim());
                rejected.put("rejectedAt", System.currentTimeMillis());
                httpVerified("PUT", RTDB + "/plugins_rejected/" + key + ".json?auth=" + token, rejected.toString());
            } catch (Throwable ex) {
                FileLog.e(ex);
            }
            if (hash != null) {
                httpVerified("PUT", RTDB + "/plugins_blocked/" + hash + ".json?auth=" + token, "\"blocked\"");
            }
            appendPluginHistory(e.id, block ? "rejected_blocked" : "rejected", reason, myId(), "Модератор", token);
            notifyPluginUser(e.submitterId,"publication_rejected","Публикация отклонена",reason,e.id,token);
        });
        return true;
    }

    public static boolean rejectPending(CatalogEntry e, boolean block) {
        return rejectPending(e, block, "");
    }

    // Удалить публикацию из каталога без блокировки файла: её можно будет отправить повторно.
    public static boolean catalogDelete(CatalogEntry entry, String reason) {
        String pluginId=entry==null?null:entry.id;
        String token = DevGramBadges.getAdminToken();
        if (token == null || pluginId == null || pluginId.isEmpty()) {
            return false;
        }
        final String safe = safeKey(pluginId);
        NET_QUEUE.execute(() -> {httpVerified("DELETE", RTDB + "/plugins_catalog/" + safe + ".json?auth=" + token, null);notifyPluginUser(entry.submitterId,"plugin_deleted","Плагин удалён из каталога",reason,entry.id,token);appendPluginHistory(entry.id,"deleted",reason,myId(),"Модератор",token);});
        return true;
    }
    public static boolean catalogDelete(String pluginId){CatalogEntry e=new CatalogEntry();e.id=pluginId;return catalogDelete(e,"Удалено модератором");}

    // Удалить плагин из каталога командой + НАВСЕГДА заблокировать его файл (по хешу исходника),
    // чтобы больше нельзя было опубликовать. source — исходник удаляемого плагина.
    public static boolean catalogDeleteAndBlock(CatalogEntry entry, String reason) {
        String pluginId=entry==null?null:entry.id;String source=entry==null?null:entry.source;
        String token = DevGramBadges.getAdminToken();
        if (token == null || pluginId == null || pluginId.isEmpty()) {
            return false;
        }
        final String safe = safeKey(pluginId);
        final String hash = source == null ? null : sha256(source);
        if (hash != null) {
            java.util.Set<String> set = new java.util.HashSet<>(blockedHashes());
            set.add(hash);
            blocked = set;
        }
        NET_QUEUE.execute(() -> {
            httpVerified("DELETE", RTDB + "/plugins_catalog/" + safe + ".json?auth=" + token, null);
            if (hash != null) {
                httpVerified("PUT", RTDB + "/plugins_blocked/" + hash + ".json?auth=" + token, "\"blocked\"");
            }
            notifyPluginUser(entry.submitterId,"plugin_deleted_blocked","Плагин удалён и заблокирован",reason,entry.id,token);appendPluginHistory(entry.id,"deleted_blocked",reason,myId(),"Модератор",token);
        });
        return true;
    }
    public static boolean catalogDeleteAndBlock(String pluginId,String source){CatalogEntry e=new CatalogEntry();e.id=pluginId;e.source=source;return catalogDeleteAndBlock(e,"Удалено и заблокировано модератором");}

    // ---- модераторы каталога (/moderators/{uid} = {"email":..,"tg":<telegram_id>}) ----
    // Модератор привязан к Telegram-ID: кнопка модерации показывается только владельцу этого ID
    // (обычные юзеры её не видят), а реальная защита записи — серверная (Firebase auth по UID).
    private static volatile java.util.Set<String> moderatorUids = new java.util.HashSet<>();
    private static volatile java.util.Set<Long> moderatorTgIds = new java.util.HashSet<>();

    // Запись модератора для UI-списка.
    public static class Moderator {
        public final String uid, email;
        public final long tg;
        public Moderator(String uid, String email, long tg) {
            this.uid = uid; this.email = email; this.tg = tg;
        }
    }

    public interface ModeratorsCallback {
        void onResult(java.util.ArrayList<Moderator> list);
    }

    public static void fetchModerators(final ModeratorsCallback cb) {
        NET_QUEUE.execute(() -> {
            final java.util.ArrayList<Moderator> list = new java.util.ArrayList<>();
            final java.util.Set<String> uids = new java.util.HashSet<>();
            final java.util.Set<Long> tgs = new java.util.HashSet<>();
            java.net.HttpURLConnection c = null;
            try {
                String token = DevGramBadges.getAdminToken();
                if (token == null || token.isEmpty()) {
                    if (cb != null) {
                        AndroidUtilities.runOnUIThread(() -> cb.onResult(null));
                    }
                    return;
                }
                String url = RTDB + "/moderators.json?auth=" + java.net.URLEncoder.encode(token, "UTF-8");
                c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                int code = c.getResponseCode();
                if (code == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String json = sb.toString().trim();
                    if (!json.isEmpty() && !"null".equals(json)) {
                        org.json.JSONObject obj = new org.json.JSONObject(json);
                        for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                            String uid = it.next();
                            String email = "";
                            long tg = 0;
                            org.json.JSONObject o = obj.optJSONObject(uid);
                            if (o != null) { // новый формат {email, tg}
                                email = o.optString("email", "");
                                tg = o.optLong("tg", 0);
                            } else { // старый формат — просто email-строка
                                email = obj.optString(uid, "");
                            }
                            list.add(new Moderator(uid, email, tg));
                            uids.add(uid);
                            if (tg != 0) tgs.add(tg);
                        }
                    }
                    moderatorUids = uids;
                    moderatorTgIds = tgs;
                } else {
                    FileLog.e("DevGram moderators fetch failed: HTTP " + code);
                    if (cb != null) {
                        AndroidUtilities.runOnUIThread(() -> cb.onResult(null));
                    }
                    return;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
            if (cb != null) {
                AndroidUtilities.runOnUIThread(() -> cb.onResult(list));
            }
        });
    }

    // Может ли ВОШЕДШИЙ аккаунт модерировать (главный админ или его UID в списке модераторов).
    public static boolean isModerator() {
        if (DevGramBadges.isMainAdmin()) {
            return true;
        }
        String uid = DevGramBadges.getAdminUid();
        return uid != null && moderatorUids.contains(uid);
    }

    // Показывать ли вход в модерацию этому Telegram-аккаунту (по кэшу модераторов).
    // Главный админ (зашитая команда) — всегда; иначе — если его tg-id есть среди модераторов.
    public static boolean canSeeModeration(long myTgId) {
        if (DevGramBadges.isTeam(myTgId)) {
            return true;
        }
        return moderatorTgIds.contains(myTgId);
    }

    // Добавить модератора: создать ему Firebase-аккаунт (email/пароль) и записать в /moderators
    // объект {email, tg}. tgId — Telegram-ID модератора (чтобы кнопка показалась именно ему).
    // Только главный админ (правила RTDB не пустят чужой токен). cb: null-ошибка при успехе.
    public static void addModerator(String email, String password, long tgId, DevGramBadges.Callback cb) {
        String token = DevGramBadges.getAdminToken();
        if (token == null) {
            cb.onResult(false, "нужен вход главного админа");
            return;
        }
        DevGramBadges.signUpModerator(email, password, (uid, err) -> {
            if (uid == null) {
                cb.onResult(false, err);
                return;
            }
            final String em = email;
            final long tg = tgId;
            NET_QUEUE.execute(() -> {
                boolean saved = false;
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("email", em);
                    o.put("tg", tg);
                    saved = httpVerified("PUT", RTDB + "/moderators/" + uid + ".json?auth=" + token, o.toString());
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                if (!saved) {
                    AndroidUtilities.runOnUIThread(() -> cb.onResult(false,
                            "аккаунт создан, но право модератора не сохранилось. Войдите главным администратором и повторите"));
                    return;
                }
                java.util.Set<String> setU = new java.util.HashSet<>(moderatorUids);
                setU.add(uid);
                moderatorUids = setU;
                if (tg != 0) {
                    java.util.Set<Long> setT = new java.util.HashSet<>(moderatorTgIds);
                    setT.add(tg);
                    moderatorTgIds = setT;
                }
                fetchModerators(list -> cb.onResult(true, null));
            });
        });
    }

    // Персональная настройка модератора: получать ли уведомления о модерации (в чат-бота).
    // Хранится в /mod_notify/{uid} (true/false); серверный воркер это учитывает. Локальный кэш.
    private static volatile Boolean modNotifyCached; // null — не загружено

    public static boolean getModNotify() {
        return modNotifyCached == null || modNotifyCached; // по умолчанию включено
    }

    public static void setModNotify(boolean enabled) {
        String token = DevGramBadges.getAdminToken();
        String uid = DevGramBadges.getAdminUid();
        if (token == null || uid == null) {
            return;
        }
        modNotifyCached = enabled;
        NET_QUEUE.execute(() ->
                httpVerified("PUT", RTDB + "/mod_notify/" + uid + ".json?auth=" + token, enabled ? "true" : "false"));
    }

    // Подтянуть текущее значение флага уведомлений для вошедшего модератора.
    public static void loadModNotify() {
        final String uid = DevGramBadges.getAdminUid();
        if (uid == null) {
            return;
        }
        NET_QUEUE.execute(() -> {
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/mod_notify/" + uid + ".json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    String s = br.readLine();
                    br.close();
                    modNotifyCached = !"false".equals(s == null ? "" : s.trim());
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    public static void removeModerator(String uid, DevGramBadges.Callback cb) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || uid == null || uid.isEmpty()) {
            if (cb != null) cb.onResult(false, "нужен вход главного администратора");
            return;
        }
        NET_QUEUE.execute(() -> {
            boolean removed = httpVerified("DELETE", RTDB + "/moderators/" + uid + ".json?auth=" + token, null);
            if (!removed) {
                if (cb != null) AndroidUtilities.runOnUIThread(() -> cb.onResult(false, "Firebase не удалил право модератора"));
                return;
            }
            fetchModerators(list -> {
                if (cb != null) cb.onResult(list != null, list != null ? null : "не удалось обновить список модераторов");
            });
        });
    }

    // ---- блок-лист (запрещённые к публикации файлы) ----
    private static volatile java.util.Set<String> blocked = new java.util.HashSet<>();

    private static java.util.Set<String> blockedHashes() {
        return blocked;
    }

    public static boolean isBlocked(String source) {
        return source != null && blocked.contains(sha256(source));
    }

    public static void fetchBlocked() {
        NET_QUEUE.execute(() -> {
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugins_blocked.json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String json = sb.toString().trim();
                    java.util.Set<String> set = new java.util.HashSet<>();
                    if (!json.isEmpty() && !"null".equals(json)) {
                        org.json.JSONObject obj = new org.json.JSONObject(json);
                        for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                            set.add(it.next().toLowerCase());
                        }
                    }
                    blocked = set;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    // ---- фильтры/категории каталога (управляет команда) ----
    public interface FiltersCallback {
        void onResult(java.util.ArrayList<String> filters);
    }

    public static void fetchFilters(final FiltersCallback cb) {
        NET_QUEUE.execute(() -> {
            final java.util.ArrayList<String> list = new java.util.ArrayList<>();
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugins_filters.json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String json = sb.toString().trim();
                    if (!json.isEmpty() && !"null".equals(json)) {
                        if (json.startsWith("[")) {
                            org.json.JSONArray array = new org.json.JSONArray(json);
                            for (int i = 0; i < array.length(); i++) {
                                String v = array.optString(i, "");
                                if (!v.isEmpty() && !list.contains(v)) list.add(v);
                            }
                        } else {
                            // Совместимость со старым объектом {hash: name}.
                            org.json.JSONObject obj = new org.json.JSONObject(json);
                            for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                                String v = obj.optString(it.next(), "");
                                if (!v.isEmpty() && !list.contains(v)) list.add(v);
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
            AndroidUtilities.runOnUIThread(() -> cb.onResult(list));
        });
    }

    public static void addFilter(String name, Utilities.Callback<Boolean> callback) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || name == null || name.trim().isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> callback.run(false));
            return;
        }
        final String nm = name.trim();
        final String key = Integer.toHexString(nm.hashCode() & 0x7fffffff);
        try {
            final String body = org.json.JSONObject.quote(nm);
            NET_QUEUE.execute(() -> {
                boolean ok = httpVerified("PUT", RTDB + "/plugins_filters/" + key + ".json?auth=" + token, body);
                AndroidUtilities.runOnUIThread(() -> callback.run(ok));
            });
        } catch (Throwable e) {
            AndroidUtilities.runOnUIThread(() -> callback.run(false));
        }
    }

    // Сохранить полный упорядоченный список категорий. JSON-массив сохраняет порядок в Firebase.
    public static void saveFilters(java.util.List<String> filters, Utilities.Callback<Boolean> callback) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || filters == null) {
            AndroidUtilities.runOnUIThread(() -> callback.run(false));
            return;
        }
        org.json.JSONArray array = new org.json.JSONArray();
        java.util.HashSet<String> unique = new java.util.HashSet<>();
        for (String filter : filters) {
            String value = filter == null ? "" : filter.trim();
            if (!value.isEmpty() && unique.add(value)) array.put(value);
        }
        NET_QUEUE.execute(() -> {
            boolean ok = httpVerified("PUT", RTDB + "/plugins_filters.json?auth=" + token, array.toString());
            AndroidUtilities.runOnUIThread(() -> callback.run(ok));
        });
    }

    public static boolean removeFilter(String name) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || name == null) {
            return false;
        }
        final String key = Integer.toHexString(name.trim().hashCode() & 0x7fffffff);
        NET_QUEUE.execute(() ->
                httpVerified("DELETE", RTDB + "/plugins_filters/" + key + ".json?auth=" + token, null));
        return true;
    }

    // DevGram: файл установленного плагина (.dgplugin приоритетно, затем .py/.plugin) или null.
    // Нужен для «Обновить в каталоге»: берём локально обновлённый автором файл.
    public static File pluginInstalledFile(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            return null;
        }
        String safe = pluginId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        File dir = pluginsDir();
        File pkg = new File(dir, safe + ".dgplugin");
        if (pkg.isFile()) return pkg;
        File py = new File(dir, safe + ".py");
        if (py.isFile()) return py;
        File pl = new File(dir, safe + ".plugin");
        if (pl.isFile()) return pl;
        return null;
    }

    // Установлен ли уже плагин с таким id (обычный исходник или пакет .dgplugin).
    public static boolean isInstalled(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            return false;
        }
        String safe = pluginId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        File dir = pluginsDir();
        if (new File(dir, safe + ".py").isFile()
                || new File(dir, safe + ".plugin").isFile()
                || new File(dir, safe + ".dgplugin").isFile()) {
            return true;
        }
        // Поддерживаем плагины, добавленные до унификации имён файлов.
        for (String row : listPlugins()) {
            String[] fields = row.split("\u001f", -1);
            if (fields.length > 0 && pluginId.equals(fields[0])) return true;
        }
        return false;
    }

    private static boolean httpVerified(String method, String urlStr, String body) {
        java.net.HttpURLConnection c = null;
        try {
            if ((urlStr.contains("/plugin_reviews/") || urlStr.contains("/plugin_reports/") || urlStr.contains("/plugin_review_reports/") || urlStr.contains("/plugins_pending/")) && !urlStr.contains("auth=")) {
                String auth = firebaseAnonToken();
                if (auth == null) return false;
                urlStr += (urlStr.contains("?") ? "&" : "?") + "auth=" + java.net.URLEncoder.encode(auth, "UTF-8");
            }
            c = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            if (body != null) {
                c.setDoOutput(true);
                c.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
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

    private static String firebaseAnonUid() { firebaseAnonToken(); return firebaseAnonUid == null ? "" : firebaseAnonUid; }

    // Package-private (не private): переиспользуется из DevGramStreaks для авторизации
    // запросов к /streaks (тот же анонимный Firebase-аккаунт на пользователя устройства).
    static synchronized String firebaseAnonToken() {
        if (firebaseAnonToken != null) return firebaseAnonToken;
        java.net.HttpURLConnection c = null;
        try {
            String savedRefresh = prefs().getString("firebase_refresh", null);
            if (savedRefresh != null && !savedRefresh.isEmpty()) {
                String form = "grant_type=refresh_token&refresh_token=" + java.net.URLEncoder.encode(savedRefresh, "UTF-8");
                c = (java.net.HttpURLConnection) new java.net.URL("https://securetoken.googleapis.com/v1/token?key=" + FIREBASE_API_KEY).openConnection();
                c.setRequestMethod("POST"); c.setConnectTimeout(12000); c.setReadTimeout(15000); c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                c.getOutputStream().write(form.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (c.getResponseCode() >= 200 && c.getResponseCode() < 300) {
                    String raw = readHttp(c.getInputStream());
                    org.json.JSONObject refreshed = new org.json.JSONObject(raw);
                    firebaseAnonToken = refreshed.optString("id_token", null);
                    firebaseAnonUid = refreshed.optString("user_id", null);
                    String nextRefresh = refreshed.optString("refresh_token", savedRefresh);
                    prefs().edit().putString("firebase_refresh", nextRefresh).putString("firebase_uid", firebaseAnonUid).apply();
                    return firebaseAnonToken;
                }
                c.disconnect(); c = null;
            }
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("returnSecureToken", true);
            c = (java.net.HttpURLConnection) new java.net.URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + FIREBASE_API_KEY).openConnection();
            c.setRequestMethod("POST"); c.setConnectTimeout(12000); c.setReadTimeout(15000); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (c.getResponseCode() >= 200 && c.getResponseCode() < 300) {
                String raw = readHttp(c.getInputStream());
                org.json.JSONObject created = new org.json.JSONObject(raw);
                firebaseAnonToken = created.optString("idToken", null);
                firebaseAnonUid = created.optString("localId", null);
                prefs().edit().putString("firebase_refresh", created.optString("refreshToken", "")).putString("firebase_uid", firebaseAnonUid).apply();
            }
        } catch (Throwable e) { FileLog.e(e); } finally { if (c != null) c.disconnect(); }
        return firebaseAnonToken;
    }

    // Периодический опрос реестра проверенных + блок-листа (вместо постоянного SSE-стрима —
    // экономим одновременные соединения Firebase).
    private static boolean registryPollScheduled;

    private static void scheduleRegistryPoll() {
        if (registryPollScheduled) {
            return;
        }
        registryPollScheduled = true;
        final Runnable[] r = new Runnable[1];
        r[0] = () -> {
            fetchVerified();
            fetchBlocked();
            AndroidUtilities.runOnUIThread(r[0], 150000);
        };
        AndroidUtilities.runOnUIThread(r[0], 150000);
    }

    // ---- live-стрим реестра: у всех обновляется сразу, без перезахода ----
    private static Thread verifiedStreamThread;

    public static synchronized void startVerifiedStream() {
        if (verifiedStreamThread != null) {
            return;
        }
        verifiedStreamThread = new Thread(DevGramPlugins::verifiedStreamLoop, "DevGramVerifiedStream");
        verifiedStreamThread.setDaemon(true);
        verifiedStreamThread.start();
    }

    private static void verifiedStreamLoop() {
        while (true) {
            java.net.HttpURLConnection conn = null;
            try {
                conn = (java.net.HttpURLConnection) new java.net.URL(VERIFIED_BASE + "/plugins_verified.json").openConnection();
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(90000);
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                String line, event = null;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        handleVerifiedStream(event, line.substring(5).trim());
                    }
                }
                br.close();
            } catch (Throwable ignore) {
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void handleVerifiedStream(String event, String data) {
        if (!"put".equals(event) && !"patch".equals(event)) {
            return;
        }
        try {
            org.json.JSONObject d = new org.json.JSONObject(data);
            String path = d.optString("path", "/");
            Object payload = d.opt("data");
            java.util.Set<String> set = new java.util.HashSet<>(verifiedHashes);
            if ("/".equals(path)) {
                set.clear();
                if (payload instanceof org.json.JSONObject) {
                    org.json.JSONObject o = (org.json.JSONObject) payload;
                    for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                        set.add(it.next().toLowerCase());
                    }
                }
            } else {
                String hash = (path.startsWith("/") ? path.substring(1) : path).toLowerCase();
                if (payload == null || org.json.JSONObject.NULL.equals(payload)) {
                    set.remove(hash);
                } else {
                    set.add(hash);
                }
            }
            verifiedHashes = set;
        } catch (Throwable ignore) {
        }
    }

    // Безопасно (без выполнения) распарсить метаданные плагина из исходника .plugin.
    // Возвращает id␟name␟version␟author␟description или "" если это не плагин DevGram.
    public static String parseMeta(String source) {
        if (source == null) {
            return "";
        }
        try {
            PyObject r = loader().callAttr("parse_meta", source);
            return r == null ? "" : r.toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return "";
        }
    }

    // Установить плагин: сохранить исходник в папку и загрузить. enable — включить сразу.
    public static boolean install(String source, String pluginId, boolean enable) {
        if (source == null) {
            return false;
        }
        try {
            String safe = pluginId == null ? "" : pluginId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            if (safe.isEmpty()) {
                safe = "plugin_" + Math.abs(source.hashCode());
            }
            File f = new File(pluginsDir(), safe + ".py");
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(source);
            w.close();
            loaded = true; // реестр активен
            int n = loader().callAttr("load_from_file", f.getAbsolutePath()).toInt();
            if (n > 0 && !enable && pluginId != null && !pluginId.isEmpty()) {
                loader().callAttr("set_enabled", pluginId, false);
            }
            return n > 0;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean installPackage(String sourcePath, String pluginId, boolean enable) {
        if (sourcePath == null) return false;
        File tmp = null;
        File backup = null;
        File dst = null;
        try {
            File src = new File(sourcePath);
            PyObject validation = loader().callAttr("validate_package", sourcePath);
            if (validation != null && !validation.toString().isEmpty()) return false;
            if (pluginId == null || pluginId.isEmpty()) {
                PyObject meta = loader().callAttr("package_meta", sourcePath);
                String raw = meta == null ? "" : meta.toString();
                pluginId = raw.isEmpty() ? "package" : raw.split("\u001f", -1)[0];
            }
            String safe = pluginId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            dst = new File(pluginsDir(), safe + ".dgplugin");
            tmp = new File(pluginsDir(), safe + ".dgplugin.installing");
            backup = new File(pluginsDir(), safe + ".dgplugin.backup");
            java.io.FileInputStream in = new java.io.FileInputStream(src);
            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            in.close(); out.getFD().sync(); out.close();
            if (backup.exists()) backup.delete();
            if (dst.exists() && !dst.renameTo(backup)) {
                tmp.delete();
                return false;
            }
            if (!tmp.renameTo(dst)) {
                if (backup.exists()) backup.renameTo(dst);
                return false;
            }
            loaded = true;
            int count = loader().callAttr("load_from_file", dst.getAbsolutePath()).toInt();
            if (count <= 0) {
                dst.delete();
                if (backup.exists() && backup.renameTo(dst)) {
                    try { loader().callAttr("load_from_file", dst.getAbsolutePath()); } catch (Throwable restoreError) { FileLog.e(restoreError); }
                }
                return false;
            }
            if (backup.exists()) backup.delete();
            if (count > 0 && !enable && pluginId != null) loader().callAttr("set_enabled", pluginId, false);
            return count > 0;
        } catch (Throwable e) {
            FileLog.e(e);
            if (tmp != null) tmp.delete();
            if (backup != null && backup.exists()) {
                if (dst != null) dst.delete();
                if (backup.renameTo(dst)) {
                    try { loader().callAttr("load_from_file", dst.getAbsolutePath()); } catch (Throwable restoreError) { FileLog.e(restoreError); }
                }
            }
            return false;
        }
    }

    public static String packageValidationError(String sourcePath) {
        try {
            PyObject result = loader().callAttr("validate_package", sourcePath);
            return result == null ? "Пакет повреждён" : result.toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return "Не удалось проверить пакет";
        }
    }

    public static String packageMeta(String sourcePath) {
        try {
            PyObject result = loader().callAttr("package_meta", sourcePath);
            return result == null ? "" : result.toString();
        } catch (Throwable e) { FileLog.e(e); return ""; }
    }

    // Прочитать исходник входного файла (main.py) из архива .dgplugin — для скана возможностей
    // в карточке установки (общей с .plugin). Возвращает "" при ошибке.
    public static String packageMainSource(String sourcePath) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(sourcePath)) {
            String main = "main.py";
            java.util.zip.ZipEntry mf = zf.getEntry("manifest.json");
            if (mf != null) {
                try { main = new org.json.JSONObject(readZipEntry(zf, mf)).optString("main", "main.py"); }
                catch (Throwable ignore) { }
            }
            java.util.zip.ZipEntry e = zf.getEntry(main);
            return e != null ? readZipEntry(zf, e) : "";
        } catch (Throwable e) {
            FileLog.e(e);
            return "";
        }
    }

    private static String readZipEntry(java.util.zip.ZipFile zf, java.util.zip.ZipEntry e) throws java.io.IOException {
        java.io.InputStream in = zf.getInputStream(e);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}

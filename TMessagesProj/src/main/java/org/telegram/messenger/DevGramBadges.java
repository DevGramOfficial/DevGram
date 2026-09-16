/*
 * DevGram: значок «команда / поддержавший / канал / официальный» рядом с именем.
 *
 * Хранилище — ОБЩЕЕ, в облаке Firebase Realtime Database (проект devgram-d03e4), узел
 * "badges": { "<dialogId>": <role> }. Так значки видят ВСЕ пользователи, а не только тот,
 * кто выдал. Айпи нашего сервера при этом не участвует вообще — приложение общается только
 * с Google (Firebase), поэтому айпи максимально скрыт.
 *
 * Читают все (правило ".read": true), пишет только команда (вход по Firebase Auth, правило
 * ".write" по admin-uid). Локальный SharedPreferences используется как КЭШ: слушатель RTDB
 * зеркалит облако в кэш, а roleOf() читает из кэша (быстро и работает офлайн). Базовая команда
 * зашита в код (TEAM_HARDCODED) — на случай пустого/недоступного облака.
 */

package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class DevGramBadges {

    public static final int ROLE_TEAM = 0;       // команда проекта (пользователь)
    public static final int ROLE_SUPPORTER = 1;  // поддержавший форк (пользователь)
    public static final int ROLE_OFFICIAL = 2;   // официальный канал/ресурс DevGram
    public static final int ROLE_CHANNEL = 3;    // обычный канал (верификация DevGram)

    public static final int ACCESS_SUPPORTER = 1;
    public static final int ACCESS_DEVELOPER = 1 << 1;

    // Кастом-эмодзи Telegram, которые рисуются как значок рядом с именем (по document_id).
    public static final long EMOJI_TEAM      = 5413368748289598440L; // ✅ команда проекта
    public static final long EMOJI_SUPPORTER = 5411424042932543123L; // ✈️ поддержавшие форк
    public static final long EMOJI_CHANNEL   = 5413368748289598440L; // ✅ обычные каналы (верификация)
    public static final long EMOJI_OFFICIAL  = 5413671801182004970L; // ✈️ официальные каналы DevGram
    public static final long EMOJI_PLUGIN_DEV = 5451743048423748161L; // ⚙️ канал-разработчик плагинов
    // алиас для совместимости со старым кодом (= эмодзи поддержавших)
    public static final long EMOJI_TEAM_SUPPORTER = EMOJI_SUPPORTER;

    // Базовая команда — зашита в код, снять из интерфейса нельзя.
    private static final HashSet<Long> TEAM_HARDCODED = new HashSet<>(Arrays.asList(
            7101191373L
    ));

    // Значок = произвольный кастом-эмодзи (document_id) + подпись-шаблон. В подписи "{name}"
    // заменяется на имя профиля при показе плашки. Хранится в облаке как объект {"e":..,"t":".."}.
    public static class Badge {
        public final long emojiId;
        public final String text;
        public final int access;
        public Badge(long emojiId, String text) {
            this(emojiId, text, 0);
        }
        public Badge(long emojiId, String text, int access) {
            this.emojiId = emojiId;
            this.text = text == null ? "" : text;
            this.access = access;
        }
    }

    // Готовые подписи (шаблоны с {name}) — для быстрого выбора в админке.
    public static final String[] READY_TEXTS = {
            "{name} — команда проекта DevGram",
            "{name} поддержал(а) разработку DevGram и получил(а) уникальный значок",
            "{name} — канал, верифицированный DevGram",
            "{name} является официальным ресурсом DevGram",
            "{name} — проверенный разработчик плагинов DevGram",
    };
    public static final String[] READY_TEXT_LABELS = {
            "Команда проекта", "Поддержавший", "Канал (верификация)", "Официальный ресурс", "Разработчик плагинов",
    };
    // Готовые значки-эмодзи — для быстрого выбора.
    public static final long[] READY_EMOJI = {EMOJI_TEAM, EMOJI_SUPPORTER, EMOJI_OFFICIAL, EMOJI_PLUGIN_DEV};
    public static final String[] READY_EMOJI_LABELS = {"✅ Команда", "✈️ Поддержавший", "✈️ Официальный", "🧩 Разработчик плагинов"};

    // Значок по умолчанию для старой int-роли (обратная совместимость с облаком).
    private static Badge defaultBadge(int role) {
        switch (role) {
            case ROLE_OFFICIAL:  return new Badge(EMOJI_OFFICIAL, READY_TEXTS[3]);
            case ROLE_CHANNEL:   return new Badge(EMOJI_CHANNEL, READY_TEXTS[2]);
            case ROLE_TEAM:      return new Badge(EMOJI_TEAM, READY_TEXTS[0], ACCESS_DEVELOPER | ACCESS_SUPPORTER);
            case ROLE_SUPPORTER:
            default:             return new Badge(EMOJI_SUPPORTER, READY_TEXTS[1], ACCESS_SUPPORTER);
        }
    }

    private static SharedPreferences prefs;

    private static SharedPreferences prefs() {
        if (prefs == null && ApplicationLoader.applicationContext != null) {
            prefs = ApplicationLoader.applicationContext.getSharedPreferences("devgram_badges", 0);
        }
        return prefs;
    }

    private static String key(long dialogId) {
        return Long.toString(dialogId);
    }

    // ================= облако (Firebase через REST, без SDK) =================
    // Всё общение — ТОЛЬКО с серверами Google (firebaseio.com / identitytoolkit.googleapis.com),
    // поэтому айпи нашего сервера нигде не участвует и максимально скрыт. Чтение открыто всем
    // (правило ".read": true), запись — по токену админа (вход email/пароль через Auth REST).

    // URL Realtime Database (регион europe-west1). Без завершающего слэша — ниже добавляем пути.
    // DevGram: значки переехали с Firebase RTDB на свой push-сервис api.devgram.space.
    // У Firebase Spark лимит 100 одновременных SSE-соединений (каждый онлайн-клиент держит стрим) —
    // при росте аудитории он пробивался. Наш сервер (gevent за Cloudflare) лимита не имеет, а формат
    // REST/SSE полностью совместим (тот же /badges.json и события put/patch), поэтому меняется только
    // база. Запись (грант) идёт тем же PUT/DELETE с ?auth=<idToken> — сервер проверяет админа и
    // зеркалит в Firebase, чтобы старые версии клиента тоже видели значки в переходный период.
    private static final String RTDB_BASE = "https://api.devgram.space";
    private static final String API_KEY = "AIzaSyAj-Fq-7707X54Yr8t51mFAkJmCLEKtYoU";

    private static volatile String adminIdToken; // токен админа после входа (нужен для записи)
    private static volatile String adminUid;     // UID вошедшего (главный админ или модератор)
    private static volatile String adminRefreshToken; // refresh-токен: idToken живёт 1ч, обновляем им
    private static volatile long adminTokenAt;   // когда получен idToken (мс), для проактивного refresh

    // Главный admin-UID (владелец). Только он управляет модераторами и значками.
    public static final String MAIN_ADMIN_UID = "cZUBpCEJWVNAZEuChkG52gdgs0K2";
    private static boolean syncStarted;

    public interface Callback {
        void onResult(boolean ok, String error);
    }

    // Сообщить всему UI, что значки изменились: reloadInterface (профиль/шапка) + updateInterfaces
    // (список чатов пересобирает ячейки через DialogCell.update). Всегда на UI-потоке.
    private static void notifyBadgesChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                }
            }
        });
    }

    // Забрать значки из облака в локальный кэш (в фоне). Вызываем на старте и при открытии
    // экрана значков / после выдачи.
    public static void syncFromCloud() {
        Utilities.globalQueue.postRunnable(() -> {
            String json = httpSend("GET", RTDB_BASE + "/badges.json", null);
            if (json == null) {
                return;
            }
            try {
                SharedPreferences p = prefs();
                if (p == null) {
                    return;
                }
                SharedPreferences.Editor ed = p.edit().clear();
                if (!"null".equals(json.trim())) {
                    JSONObject obj = new JSONObject(json);
                    for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                        String k = it.next();
                        try {
                            Long.parseLong(k); // валидируем ключ-dialogId
                            // храним сырое значение: старое — int-роль, новое — объект {"e","t"}
                            ed.putString(k, obj.get(k).toString());
                        } catch (Throwable ignore) {
                        }
                    }
                }
                ed.apply();
                notifyBadgesChanged();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    private static Thread streamThread;
    private static final long POLL_INTERVAL_MS = 150000; // ~2.5 мин — периодический опрос
    private static boolean pollScheduled;

    public static void startSync() {
        if (syncStarted) {
            return;
        }
        syncStarted = true;
        syncFromCloud(); // быстрый первый снимок
        // ВАЖНО: постоянный SSE-стрим держал 1 соединение на устройство (лимит бесплатного
        // Firebase — 100 одновременных). Заменили на периодический опрос: короткие GET'ы
        // соединение не держат, поэтому число юзеров лимит соединений не упирает.
        schedulePoll();
    }

    private static void schedulePoll() {
        if (pollScheduled) {
            return;
        }
        pollScheduled = true;
        final Runnable[] r = new Runnable[1];
        r[0] = () -> {
            syncFromCloud();
            AndroidUtilities.runOnUIThread(r[0], POLL_INTERVAL_MS);
        };
        AndroidUtilities.runOnUIThread(r[0], POLL_INTERVAL_MS);
    }

    // Живой поток изменений через Firebase REST Server-Sent Events: держим долгую connection к
    // badges.json (Accept: text/event-stream) и применяем события put/patch к кэшу. Как только
    // кому-то выдали/сняли значок — он появляется/исчезает у всех сразу. Чтение открыто, токен
    // не нужен. При обрыве — переподключаемся.
    private static synchronized void startStream() {
        if (streamThread != null) {
            return;
        }
        streamThread = new Thread(DevGramBadges::streamLoop, "DevGramBadgesStream");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    private static void streamLoop() {
        while (true) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(RTDB_BASE + "/badges.json");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(90000); // > keep-alive Firebase (~30с): тишина 90с → реконнект
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line, event = null;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("event:")) {
                            event = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            handleStreamData(event, line.substring(5).trim());
                        }
                    }
                }
            } catch (Throwable ignore) {
                // обрыв/таймаут — переподключимся
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            try {
                Thread.sleep(5000); // пауза перед реконнектом
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void handleStreamData(String event, String data) {
        if (!"put".equals(event) && !"patch".equals(event)) {
            return; // keep-alive и прочее игнорируем
        }
        try {
            JSONObject d = new JSONObject(data);
            String path = d.optString("path", "/");
            Object payload = d.opt("data");
            SharedPreferences p = prefs();
            if (p == null) {
                return;
            }
            SharedPreferences.Editor ed = p.edit();
            if ("/".equals(path)) {
                // полный снимок (при подключении или полной замене)
                ed.clear();
                if (payload instanceof JSONObject) {
                    JSONObject obj = (JSONObject) payload;
                    for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                        String k = it.next();
                        putBadge(ed, k, obj.opt(k));
                    }
                }
            } else {
                // изменение одного значка: path = "/<dialogId>"
                String k = path.startsWith("/") ? path.substring(1) : path;
                int slash = k.indexOf('/');
                if (slash >= 0) {
                    k = k.substring(0, slash);
                }
                putBadge(ed, k, payload);
            }
            ed.apply();
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void putBadge(SharedPreferences.Editor ed, String key, Object value) {
        try {
            Long.parseLong(key); // только валидные dialogId
        } catch (Throwable e) {
            return;
        }
        if (value == null || value == JSONObject.NULL) {
            ed.remove(key); // значок снят
        } else {
            // сырое значение как строка: int-роль ("0") или объект ({"e":..,"t":".."})
            ed.putString(key, String.valueOf(value));
        }
    }

    public static boolean isSignedIn() {
        return adminIdToken != null;
    }

    // Токен админа для записи из других модулей (реестр проверенных плагинов).
    public static String getAdminToken() {
        return adminIdToken;
    }

    // UID вошедшего аккаунта (или null). Главный админ, если совпадает с MAIN_ADMIN_UID.
    public static String getAdminUid() {
        return adminUid;
    }

    public static boolean isMainAdmin() {
        return MAIN_ADMIN_UID.equals(adminUid);
    }

    // Вход команды/модератора: Firebase Auth REST (email/пароль → idToken + UID). Держим в сессии.
    public static void signIn(String email, String password, Callback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                body.put("returnSecureToken", true);
                String resp = httpSend("POST",
                        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY,
                        body.toString());
                if (resp != null) {
                    JSONObject r = new JSONObject(resp);
                    if (r.has("idToken")) {
                        adminIdToken = r.getString("idToken");
                        adminUid = r.optString("localId", null);
                        adminRefreshToken = r.optString("refreshToken", null);
                        adminTokenAt = System.currentTimeMillis();
                        AndroidUtilities.runOnUIThread(() -> cb.onResult(true, null));
                        return;
                    }
                }
                adminIdToken = null;
                adminUid = null;
                adminRefreshToken = null;
                AndroidUtilities.runOnUIThread(() -> cb.onResult(false, "неверный email или пароль"));
            } catch (Throwable e) {
                adminIdToken = null;
                adminUid = null;
                AndroidUtilities.runOnUIThread(() -> cb.onResult(false, e.getMessage()));
            }
        });
    }

    // Создать аккаунт модератору (Firebase Auth signUp). Возвращает его UID в колбэк (или null+ошибка).
    // Требует, чтобы вызывающий был вошедшим админом (проверка прав — на уровне RTDB-правил при записи UID).
    public interface UidCallback {
        void onResult(String uid, String error);
    }

    public static void signUpModerator(String email, String password, UidCallback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                body.put("returnSecureToken", true);
                String resp = httpSend("POST",
                        "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + API_KEY,
                        body.toString());
                if (resp != null) {
                    JSONObject r = new JSONObject(resp);
                    if (r.has("localId")) {
                        final String uid = r.getString("localId");
                        AndroidUtilities.runOnUIThread(() -> cb.onResult(uid, null));
                        return;
                    }
                    if (r.has("error")) {
                        final String msg = r.getJSONObject("error").optString("message", "ошибка");
                        AndroidUtilities.runOnUIThread(() -> cb.onResult(null, msg));
                        return;
                    }
                }
                AndroidUtilities.runOnUIThread(() -> cb.onResult(null, "не удалось создать аккаунт"));
            } catch (Throwable e) {
                AndroidUtilities.runOnUIThread(() -> cb.onResult(null, e.getMessage()));
            }
        });
    }

    // Простой HTTP: GET/PUT/DELETE/POST. Возвращает тело ответа (2xx) или null.
    private static String httpSend(String method, String urlStr, String body) {
        return httpSend(method, urlStr, body, false);
    }

    private static String httpSend(String method, String urlStr, String body, boolean formEncoded) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", formEncoded
                        ? "application/x-www-form-urlencoded"
                        : "application/json; charset=utf-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            boolean ok = code >= 200 && code < 300;
            InputStream is = ok ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }
            }
            if (ok) {
                return sb.toString();
            }
            FileLog.e("DevGramBadges http " + method + " " + code + ": " + sb);
            return null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // Значок диалога ({emoji, text}) или null, если значка нет. (>0 — пользователь, <0 — чат)
    public static Badge badgeOf(long dialogId) {
        // Сначала — выданный (кастомный) значок из облака/кэша. Он имеет приоритет над хардкодом,
        // чтобы можно было выдать себе/команде любой значок и он реально показывался.
        SharedPreferences p = prefs();
        if (p != null) {
            Badge b = parseBadge(p.getAll().get(key(dialogId)));
            if (b != null) {
                return b;
            }
        }
        // Фолбэк: базовая команда зашита в код (на случай пустого/недоступного облака).
        if (dialogId > 0 && TEAM_HARDCODED.contains(dialogId)) {
            return defaultBadge(ROLE_TEAM);
        }
        return null;
    }

    // Разобрать сырое значение из кэша: объект {"e","t"} (новое) или int-роль (старое).
    private static Badge parseBadge(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equals(s)) {
            return null;
        }
        if (s.startsWith("{")) {
            try {
                JSONObject o = new JSONObject(s);
                long emojiId = o.optLong("e", 0);
                int access = o.has("p") ? o.optInt("p", 0) : legacyAccess(emojiId);
                return new Badge(emojiId, o.optString("t", ""), access);
            } catch (Throwable ignore) {
                return null;
            }
        }
        try {
            return defaultBadge(Integer.parseInt(s));
        } catch (Throwable ignore) {
            return null;
        }
    }

    // Грубая роль по эмодзи (для старых геттеров и гейтинга прокси). -1 если значка нет.
    public static int roleOf(long dialogId) {
        Badge b = badgeOf(dialogId);
        if (b == null) {
            return -1;
        }
        if (b.emojiId == EMOJI_CHANNEL) {
            return ROLE_CHANNEL;
        }
        if (b.emojiId == EMOJI_OFFICIAL) {
            return ROLE_OFFICIAL;
        }
        return ROLE_TEAM;
    }

    public static boolean isBadged(long dialogId) {
        return badgeOf(dialogId) != null;
    }

    // Команда = зашитый список ИЛИ пользователь (id>0) со значком-эмодзи команды (✅).
    // Каналы с тем же ✅ хранятся под отрицательным id, поэтому с командой не пересекаются.
    public static boolean isTeam(long userId) {
        if (userId <= 0) {
            return false;
        }
        if (TEAM_HARDCODED.contains(userId)) {
            return true;
        }
        return emojiIdOf(userId) == EMOJI_TEAM;
    }

    public static boolean isSupporter(long userId) {
        return userId > 0 && emojiIdOf(userId) == EMOJI_SUPPORTER;
    }

    private static int legacyAccess(long emojiId) {
        if (emojiId == EMOJI_TEAM) {
            return ACCESS_DEVELOPER | ACCESS_SUPPORTER;
        }
        if (emojiId == EMOJI_SUPPORTER) {
            return ACCESS_SUPPORTER;
        }
        return 0;
    }

    public static boolean hasSupporterFeatures(long userId) {
        if (userId <= 0) {
            return false;
        }
        Badge badge = badgeOf(userId);
        return isTeam(userId) || badge != null && (badge.access & (ACCESS_SUPPORTER | ACCESS_DEVELOPER)) != 0;
    }

    public static boolean hasDeveloperFeatures(long userId) {
        if (userId <= 0) {
            return false;
        }
        Badge badge = badgeOf(userId);
        return isTeam(userId) || badge != null && (badge.access & ACCESS_DEVELOPER) != 0;
    }

    public static boolean isOfficialChat(long chatId) {
        return chatId > 0 && emojiIdOf(-chatId) == EMOJI_OFFICIAL;
    }

    // Канал-разработчик плагинов (значок 🧩): плагины из него считаются проверенными.
    // dialogId — как в чате (у канала он отрицательный).
    public static boolean isPluginDevChannel(long dialogId) {
        return dialogId != 0 && emojiIdOf(dialogId) == EMOJI_PLUGIN_DEV;
    }

    // document_id кастом-эмодзи для значка этого диалога (0 — значка нет).
    public static long emojiIdOf(long dialogId) {
        Badge b = badgeOf(dialogId);
        return b == null ? 0 : b.emojiId;
    }

    // --- управление (экран «Значки DevGram») ---

    // Выдать значок. rawId — то, что ввёл разработчик (без знака); роль определяет тип.
    // Пишем в облако (PUT с токеном админа) — после записи перечитываем облако у себя, а у
    // остальных подтянется при следующей синхронизации. Без токена — локальный фолбэк.
    // Готовая роль -> дефолтный значок (обёртка над grantBadge).
    public static void grant(long rawId, int role) {
        if (rawId == 0) {
            return;
        }
        boolean isChannelRole = role == ROLE_OFFICIAL || role == ROLE_CHANNEL;
        long dialogId = isChannelRole ? -Math.abs(rawId) : Math.abs(rawId);
        Badge b = defaultBadge(role);
        grantBadge(dialogId, b.emojiId, b.text);
    }

    // Выдать произвольный значок: эмодзи (document_id) + подпись-шаблон (с {name}).
    // dialogId: >0 — пользователь, <0 — чат/канал. Пишем в облако объектом {"e","t"}.
    public static void grantBadge(long dialogId, long emojiId, String textTemplate) {
        grantBadge(dialogId, emojiId, textTemplate, legacyAccess(emojiId));
    }

    // p — битовая маска доступа к пользовательским функциям. Она не даёт административных прав.
    // Firebase idToken живёт ~1 час. Обновляем его refresh-токеном ДО истечения, иначе
    // запись значка в облако молча падает с 401 → «иногда не выдаётся». Вызывать в globalQueue.
    // Возвращает актуальный idToken (или текущий, если refresh невозможен/не нужен).
    private static String ensureFreshToken() {
        String rt = adminRefreshToken;
        if (rt == null || rt.isEmpty()) {
            return adminIdToken; // legacy-сессия без refresh — вернём что есть
        }
        // обновляем, если токену >50 мин или его нет
        if (adminIdToken != null && System.currentTimeMillis() - adminTokenAt < 50 * 60 * 1000L) {
            return adminIdToken;
        }
        try {
            String form = "grant_type=refresh_token&refresh_token="
                    + java.net.URLEncoder.encode(rt, "UTF-8");
            String resp = httpSend("POST",
                    "https://securetoken.googleapis.com/v1/token?key=" + API_KEY, form, true);
            if (resp != null) {
                JSONObject r = new JSONObject(resp);
                String fresh = r.optString("id_token", null);
                if (fresh != null && !fresh.isEmpty()) {
                    adminIdToken = fresh;
                    adminRefreshToken = r.optString("refresh_token", rt);
                    adminTokenAt = System.currentTimeMillis();
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return adminIdToken;
    }

    // Приводит id канала из bot-API формата (-100xxxxxxxxxx) к внутреннему dialogId (-xxxxxxxxxx).
    // Иначе значок пишется под ключом, по которому клиент его НЕ ищет, и он «не появляется».
    // Пользователи (>0) и обычные чаты не трогаем.
    public static long normalizeDialogId(long id) {
        if (id < -1000000000000L) { // -100 + channel_id  →  -channel_id
            return id + 1000000000000L;
        }
        return id;
    }

    public static void grantBadge(long rawDialogId, long emojiId, String textTemplate, int access) {
        final long dialogId = normalizeDialogId(rawDialogId);
        if (dialogId == 0) {
            return;
        }
        final String json;
        try {
            JSONObject o = new JSONObject();
            o.put("e", emojiId);
            o.put("t", textTemplate == null ? "" : textTemplate);
            o.put("p", access);
            json = o.toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return;
        }
        // оптимистично пишем локально — облако подтянется следом
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putString(key(dialogId), json).apply();
        }
        if (adminIdToken != null) {
            Utilities.globalQueue.postRunnable(() -> {
                // свежий токен (idToken живёт 1ч) + ретрай с принудительным refresh при 401,
                // иначе запись молча падает и значок «иногда не выдаётся».
                String tok = ensureFreshToken();
                String res = httpSend("PUT", RTDB_BASE + "/badges/" + dialogId + ".json?auth=" + tok, json);
                if (res == null) {
                    adminTokenAt = 0; // форсируем refresh
                    tok = ensureFreshToken();
                    res = httpSend("PUT", RTDB_BASE + "/badges/" + dialogId + ".json?auth=" + tok, json);
                }
                // syncFromCloud только если запись прошла — иначе затрём локальный значок пустотой
                if (res != null) {
                    syncFromCloud();
                } else {
                    FileLog.e("DevGramBadges: не удалось выдать значок " + dialogId + " (запись в облако провалилась)");
                }
            });
        }
        notifyBadgesChanged();
    }

    public static void revoke(long dialogId) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().remove(key(dialogId)).apply();
        }
        if (adminIdToken != null) {
            Utilities.globalQueue.postRunnable(() -> {
                String tok = ensureFreshToken();
                String res = httpSend("DELETE", RTDB_BASE + "/badges/" + dialogId + ".json?auth=" + tok, null);
                if (res == null) {
                    adminTokenAt = 0;
                    tok = ensureFreshToken();
                    httpSend("DELETE", RTDB_BASE + "/badges/" + dialogId + ".json?auth=" + tok, null);
                }
                syncFromCloud();
            });
        }
    }

    // Все выданные из интерфейса значки (без зашитой команды): список dialogId.
    // Значок каждого читается через badgeOf(id).
    public static ArrayList<Long> listGranted() {
        ArrayList<Long> res = new ArrayList<>();
        SharedPreferences p = prefs();
        if (p != null) {
            for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
                try {
                    res.add(Long.parseLong(e.getKey()));
                } catch (Throwable ignore) {
                }
            }
        }
        return res;
    }

    public static String roleName(int role) {
        switch (role) {
            case ROLE_TEAM: return "Команда проекта";
            case ROLE_OFFICIAL: return "Официальный канал DevGram";
            case ROLE_CHANNEL: return "Канал";
            default: return "Поддержавший";
        }
    }

    // Подпись, всплывающая по тапу на значок. В шаблоне "{name}" заменяется на имя профиля.
    public static CharSequence badgeText(long dialogId, CharSequence name) {
        Badge b = badgeOf(dialogId);
        if (b == null) {
            return "";
        }
        String nm = name == null ? "" : name.toString();
        return b.text.replace("{name}", nm);
    }

    // Плашка по тапу на значок: иконка = выданный значок (кастом-эмодзи), текст — многострочный (не режется).
    public static void showBadgeBulletin(org.telegram.ui.Components.BulletinFactory bf, long dialogId, CharSequence name) {
        if (bf == null) {
            return;
        }
        CharSequence text = badgeText(dialogId, name);
        long emojiId = emojiIdOf(dialogId);
        org.telegram.tgnet.TLRPC.Document doc = null;
        try {
            doc = org.telegram.ui.Components.AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, emojiId);
        } catch (Throwable ignore) {
        }
        org.telegram.ui.Components.Bulletin b;
        if (doc != null) {
            // эмодзи-значок как иконка + многострочный текст
            b = bf.createSimpleMultiBulletin(doc, text);
        } else {
            // фолбэк: статичная картинка по роли + делаем текст многострочным вручную
            int res = emojiId == EMOJI_CHANNEL ? R.drawable.devgram_channel : R.drawable.devgram_supporter;
            android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(
                    ApplicationLoader.applicationContext, res);
            b = bf.createSimpleBulletin(d, text);
            try {
                if (b.getLayout() instanceof org.telegram.ui.Components.Bulletin.LottieLayout) {
                    android.widget.TextView tv = ((org.telegram.ui.Components.Bulletin.LottieLayout) b.getLayout()).textView;
                    tv.setSingleLine(false);
                    tv.setMaxLines(3);
                    tv.setEllipsize(null);
                }
            } catch (Throwable ignore) {
            }
        }
        // DevGram: у описания любого значка показываем переход к инструкции по поддержке проекта.
        // Все места показа значков используют этот единый метод, включая кастомные значки.
        try {
            if (b.getLayout() instanceof org.telegram.ui.Components.Bulletin.ButtonLayout) {
                org.telegram.ui.Components.Bulletin.ButtonLayout layout =
                        (org.telegram.ui.Components.Bulletin.ButtonLayout) b.getLayout();
                layout.setButton(new org.telegram.ui.Components.Bulletin.UndoButton(
                        layout.getContext(), true, null)
                        .setText("Подробнее")
                        .setUndoAction(() -> {
                            org.telegram.ui.ActionBar.BaseFragment fragment =
                                    org.telegram.ui.LaunchActivity.getSafeLastFragment();
                            if (fragment != null) {
                                org.telegram.ui.DevGramSupportSheet.show(fragment);
                            }
                        }));
            }
        } catch (Throwable ignore) {
        }
        b.show();
    }
}

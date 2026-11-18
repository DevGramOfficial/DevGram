/*
 * DevGram: «огоньки» (стрик) как в Snapchat/TikTok — сколько дней ПОДРЯД вы общаетесь
 * с человеком в личке. День засчитывается, если в этот календарный день ОБА написали.
 *
 * Хранилище — ОБЛАКО Firebase Realtime Database (проект devgram-d03e4), узел
 * streaks/{мой_id}/{id_собеседника} = "day,out,in,streak,streakDay". Стрик личный, поэтому
 * лежит под id владельца. Приложение общается только с Google — айпи сервера не участвует.
 * Локальный SharedPreferences — быстрый кэш для отрисовки и офлайна; облако его зеркалит.
 */

package org.telegram.messenger;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.ForegroundDetector;

import java.util.Calendar;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

public class DevGramStreaks {

    private static final String RTDB_BASE = "https://devgram-d03e4-default-rtdb.europe-west1.firebasedatabase.app";
    private static final int MIN_SHOWN = 3;   // огонёк показываем только начиная с 3 дней подряд
    private static final int MAX_RESTORES = 5; // максимум восстановлений на пользователя

    private static SharedPreferences prefs;
    private static boolean syncStarted;
    private static boolean watcherStarted;
    private static int lastWatcherDay = -1; // для перерисовки огоньков при смене дня

    private static SharedPreferences prefs() {
        if (prefs == null && ApplicationLoader.applicationContext != null) {
            prefs = ApplicationLoader.applicationContext.getSharedPreferences("devgram_streaks", 0);
        }
        return prefs;
    }

    private static String key(long dialogId) {
        return Long.toString(dialogId);
    }

    // Правило RTDB на /streaks требует auth != null (и на чтение, и на запись) — раньше сюда
    // ходили вообще без токена, поэтому ЛЮБАЯ запись всегда отклонялась сервером (401,
    // молча проглатывалось в httpSend), а чтение было полностью открыто кому угодно без
    // приложения. Анонимный Firebase-токен уже есть в DevGramPlugins (используется для
    // отзывов/жалоб на плагины) — переиспользуем его здесь же.
    private static String withAuth(String url) {
        try {
            String token = DevGramPlugins.firebaseAnonToken();
            if (token == null || token.isEmpty()) {
                return url;
            }
            return url + (url.contains("?") ? "&" : "?") + "auth=" + java.net.URLEncoder.encode(token, "UTF-8");
        } catch (Throwable e) {
            return url;
        }
    }

    // Подпись по тапу на огонёк.
    public static CharSequence streakText(CharSequence name, int count) {
        if (name == null) {
            name = "";
        }
        return "У вас с " + name + " серия уже " + count + " " + pluralDays(count) + " 🔥";
    }

    private static String pluralDays(int n) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return "день";
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "дня";
        return "дней";
    }

    // Номер календарного дня (по локальному времени).
    // «День» для огоньков — по ЕДИНОМУ московскому времени (UTC+3), одинаково у всех независимо
    // от часового пояса устройства. Иначе у собеседников из разных поясов день считался бы
    // по-разному и серии расходились.
    private static final long MSK_OFFSET_MS = 3 * 3600000L;

    private static int today() {
        long now = System.currentTimeMillis();
        return (int) ((now + MSK_OFFSET_MS) / 86400000L);
    }

    private static boolean tracked(long dialogId, int accountId) {
        if (dialogId <= 0) {
            return false; // только приватные чаты (пользователи)
        }
        if (dialogId == UserConfig.getInstance(accountId).getClientUserId()) {
            return false; // не чат с самим собой
        }
        TLRPC.User u = MessagesController.getInstance(accountId).getUser(dialogId);
        return u == null || !u.bot; // боты не считаем
    }

    // Подтянуть свои стрики из облака в кэш (на старте приложения).
    public static void startSync() {
        if (syncStarted) {
            return;
        }
        syncStarted = true;
        doSync();
    }

    // Пока приложение открыто, периодически проверяем потерю огонька (локально, без сети),
    // и точечно — сразу после локальной полуночи, когда огонёк реально «слетает».
    // Нужно на случай, когда пользователь безвылазно сидит в приложении.
    public static void startLossWatcher() {
        if (watcherStarted) {
            return;
        }
        watcherStarted = true;
        scheduleNextLossCheck();
    }

    private static void scheduleNextLossCheck() {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                int t = today();
                // сменился день (полночь) — перерисуем огоньки: продлённые вчера станут серыми
                if (lastWatcherDay != -1 && t != lastWatcherDay) {
                    NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                }
                lastWatcherDay = t;
                if (DevGramConfig.streaksEnabled) {
                    ForegroundDetector fd = ForegroundDetector.getInstance();
                    if (fd == null || fd.isForeground()) {
                        checkLostStreaks();
                    }
                    checkExpiringStreaks(); // напоминание «серия скоро сгорит» — и в фоне тоже
                }
            } catch (Throwable ignore) {
            }
            scheduleNextLossCheck();
        }, nextLossCheckDelay());
    }

    private static long nextLossCheckDelay() {
        try {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, 1);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 10);
            c.set(Calendar.MILLISECOND, 0);
            long untilMidnight = c.getTimeInMillis() - System.currentTimeMillis();
            long periodic = 15 * 60 * 1000L; // не реже раза в 15 минут
            return Math.max(30 * 1000L, Math.min(periodic, untilMidnight));
        } catch (Throwable e) {
            return 15 * 60 * 1000L;
        }
    }

    // Сведение расхождений: для каждой живой серии сравниваем со встречной записью собеседника
    // (streaks/{dialogId}/{myId}) и берём БОЛЬШЕЕ значение. Так огонёк у обоих сходится к одному
    // числу, даже если раньше разъехался (пропуски событий, старые часовые пояса). Сеть — в фоне.
    private static void reconcile(SharedPreferences p, long myId) {
        try {
            int tnow = today();
            SharedPreferences.Editor ed = p.edit();
            boolean any = false;
            for (String k : new java.util.ArrayList<>(p.getAll().keySet())) {
                if (k.indexOf('_') >= 0 || "lastRefresh".equals(k)) {
                    continue; // мета-ключи не трогаем
                }
                String mine = p.getString(k, null);
                if (mine == null) {
                    continue;
                }
                String[] a = mine.split(",");
                if (a.length < 5) {
                    continue;
                }
                int myStreak, myStreakDay;
                long dialogId;
                try {
                    myStreak = Integer.parseInt(a[3]);
                    myStreakDay = Integer.parseInt(a[4]);
                    dialogId = Long.parseLong(k);
                } catch (Throwable e) {
                    continue;
                }
                String otherJson = httpSend("GET", withAuth(RTDB_BASE + "/streaks/" + dialogId + "/" + myId + ".json"), null);
                if (otherJson == null) {
                    continue;
                }
                otherJson = otherJson.trim();
                if ("null".equals(otherJson) || otherJson.isEmpty()) {
                    continue;
                }
                if (otherJson.startsWith("\"") && otherJson.endsWith("\"") && otherJson.length() >= 2) {
                    otherJson = otherJson.substring(1, otherJson.length() - 1);
                }
                String[] b = otherJson.split(",");
                if (b.length < 5) {
                    continue;
                }
                int oStreak, oStreakDay;
                try {
                    oStreak = Integer.parseInt(b[3]);
                    oStreakDay = Integer.parseInt(b[4]);
                } catch (Throwable e) {
                    continue;
                }
                // берём встречное, если оно больше и серия жива (последний день — вчера/сегодня)
                if (oStreak > myStreak && oStreakDay >= tnow - 1 && oStreakDay >= myStreakDay) {
                    String merged = a[0] + "," + a[1] + "," + a[2] + "," + oStreak + "," + oStreakDay
                            + "," + (a.length > 5 ? a[5] : "0");
                    ed.putString(k, merged);
                    any = true;
                    final String mm = merged;
                    final long did = dialogId;
                    httpSend("PUT", withAuth(RTDB_BASE + "/streaks/" + myId + "/" + did + ".json"), "\"" + mm + "\"");
                }
            }
            if (any) {
                ed.apply();
            }
        } catch (Throwable ignore) {
        }
    }

    // Повторная проверка при выходе в foreground (без live-стрима). Не чаще раза в 20 минут.
    public static void refresh() {
        if (!DevGramConfig.streaksEnabled) {
            return;
        }
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - p.getLong("lastRefresh", 0) < 20 * 60 * 1000L) {
            return;
        }
        p.edit().putLong("lastRefresh", now).apply();
        doSync();
    }

    // GET облачного состояния -> синхронизируем кэш (в т.ч. УДАЛЯЕМ серии, которых больше нет
    // в облаке) -> проверка потерянных огоньков.
    private static void doSync() {
        DevGramNetworkQueue.postRunnable(() -> {
            long myId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            if (myId == 0) {
                return;
            }
            String json = httpSend("GET", withAuth(RTDB_BASE + "/streaks/" + myId + ".json"), null);
            if (json == null) {
                return; // сетевая ошибка — кэш не трогаем
            }
            SharedPreferences p = prefs();
            if (p == null) {
                return;
            }
            JSONObject obj = null;
            String trimmed = json.trim();
            if (!"null".equals(trimmed)) {
                try {
                    obj = new JSONObject(trimmed);
                } catch (Throwable e) {
                    return; // не JSON (ошибка/доступ) — кэш не трогаем
                }
            }
            // Слияние облака и кэша. ВАЖНО: серии в облаке НИКОГДА не удаляются (только GET/PUT),
            // поэтому «ключа нет в облаке» = PUT из onMessage не долетел, а НЕ «серия удалена».
            // Раньше такие серии стирались из кэша — это и была причина «огонёк слетает, хотя
            // продлил». Теперь: локальное НЕ удаляем; если оно свежее облака (или его нет в
            // облаке) — до-заливаем в облако (self-heal). Облаком перезаписываем локальное
            // только если оно реально свежее (см. cmpFresh: по streakDay, streak, day, out+in).
            try {
                final long myIdF = myId;
                final int tnow = today();
                SharedPreferences.Editor ed = p.edit();
                // 1) облачные записи -> кэш, но не затираем более свежее локальное
                if (obj != null) {
                    for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                        String k = it.next();
                        String cloudVal = obj.getString(k);
                        String localVal = p.getString(k, null);
                        if (localVal == null || cmpFresh(cloudVal, localVal) > 0) {
                            ed.putString(k, cloudVal); // облако новее (или локального ещё нет)
                        } else if (cmpFresh(localVal, cloudVal) > 0) {
                            repushCloud(myIdF, k, localVal); // локальное новее — чиним облако
                        }
                    }
                }
                // 2) локальные серии, которых нет в облаке. Если серия ещё живая/восстановимая —
                // «ключа нет» значит PUT не долетел, до-заливаем (self-heal, как раньше). Если же
                // серия уже НАВСЕГДА мертва (isDeadForever) — значит её удалили как мусор (см.
                // purgeDeadStreak), и «ключа нет» — это ожидаемо: просто подчищаем и локальную
                // копию, а не воскрешаем её обратно в облако.
                for (String existing : new java.util.ArrayList<>(p.getAll().keySet())) {
                    if (existing.indexOf('_') >= 0 || "lastRefresh".equals(existing)) {
                        continue; // мета-ключи (_r/_notified/_reminded, lastRefresh) не трогаем
                    }
                    if (obj == null || !obj.has(existing)) {
                        String lv = p.getString(existing, null);
                        if (lv != null && lv.split(",").length >= 5) {
                            if (isDeadForever(lv, tnow)) {
                                ed.remove(existing).remove(existing + "_notified").remove(existing + "_reminded");
                            } else {
                                repushCloud(myIdF, existing, lv);
                            }
                        }
                    }
                }
                ed.apply();
                // Свести расхождения серий: у собеседника СВОЯ запись streaks/{dialogId}/{myId}.
                // Берём большее значение (у кого больше засчитанных дней — вернее), чтобы у обоих
                // огонёк сошёлся к одному числу. Свою запись при этом подтягиваем в облаке.
                reconcile(p, myId);
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                    checkLostStreaks(); // кэш уже свежий — сразу проверяем потери
                });
            } catch (Throwable ignore) {
            }
        });
    }

    // Дозалить локальное состояние серии в облако (self-heal, когда push из onMessage не долетел
    // или локальное новее). Fire-and-forget в фоне.
    private static void repushCloud(long myId, String dialogKey, String state) {
        DevGramNetworkQueue.postRunnable(() ->
                httpSend("PUT", withAuth(RTDB_BASE + "/streaks/" + myId + "/" + dialogKey + ".json"), "\"" + state + "\""));
    }

    // Серия НАВСЕГДА мертва (не просто «сегодня ещё не продлена», а окончательно, без шанса на
    // восстановление): цепочка порвана (streakDay < t-1) и либо она никогда не доходила до
    // показа (streak < MIN_SHOWN), либо истекло окно восстановления (7 дней), либо кончились
    // попытки восстановления. Для такой записи хранить состояние в облаке больше незачем.
    private static boolean isDeadForever(String state, int t) {
        try {
            String[] a = state.split(",");
            int streak = Integer.parseInt(a[3]);
            int streakDay = Integer.parseInt(a[4]);
            int restores = a.length > 5 ? Integer.parseInt(a[5]) : 0;
            return streakDay < t - 1 && (streak < MIN_SHOWN || streakDay < t - 7 || restores >= MAX_RESTORES);
        } catch (Throwable e) {
            return false;
        }
    }

    // Стереть навсегда мёртвую серию: и локальный кэш, и облачную запись (иначе она бесполезно
    // занимает место в Firebase). Вызывается только для isDeadForever() — живые и восстановимые
    // серии никогда не трогаем (см. комментарий в doSync).
    private static void purgeDeadStreak(int account, long dialogId, String key) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().remove(key).remove(key + "_notified").remove(key + "_reminded").apply();
        }
        long myId = UserConfig.getInstance(account).getClientUserId();
        if (myId != 0) {
            DevGramNetworkQueue.postRunnable(() ->
                    httpSend("DELETE", withAuth(RTDB_BASE + "/streaks/" + myId + "/" + dialogId + ".json"), null));
        }
    }

    // «Свежесть» состояния серии для слияния кэш<->облако. Возвращает >0 если a свежее b, <0 если
    // b свежее, 0 если равны. Порядок сравнения: streakDay (кто позже засчитал день) -> streak ->
    // day (последняя активность) -> out+in (сколько отметок за сегодня). Так более полное/позднее
    // состояние всегда побеждает, и продление серии не откатывается устаревшей копией.
    private static int cmpFresh(String a, String b) {
        long[] fa = fresh(a), fb = fresh(b);
        for (int i = 0; i < fa.length; i++) {
            if (fa[i] != fb[i]) {
                return fa[i] > fb[i] ? 1 : -1;
            }
        }
        return 0;
    }

    private static long[] fresh(String s) {
        long streakDay = Long.MIN_VALUE, streak = 0, day = Long.MIN_VALUE, marks = 0;
        if (s != null) {
            try {
                String[] a = s.split(",");
                day = Long.parseLong(a[0]);
                marks = Long.parseLong(a[1]) + Long.parseLong(a[2]); // out + in
                streak = Long.parseLong(a[3]);
                streakDay = Long.parseLong(a[4]);
            } catch (Throwable ignore) {
            }
        }
        return new long[]{streakDay, streak, day, marks};
    }

    // Зафиксировать новое сообщение. Состояние: day,out,in,streak,streakDay.
    public static void onMessage(int accountId, long dialogId, boolean outgoing) {
        if (!tracked(dialogId, accountId)) {
            return;
        }
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        int t = today();
        int day = t, out = 0, in = 0, streak = 0, streakDay = -100, restores = 0;
        String s = p.getString(key(dialogId), null);
        if (s != null) {
            try {
                String[] a = s.split(",");
                day = Integer.parseInt(a[0]);
                out = Integer.parseInt(a[1]);
                in = Integer.parseInt(a[2]);
                streak = Integer.parseInt(a[3]);
                streakDay = Integer.parseInt(a[4]);
                restores = a.length > 5 ? Integer.parseInt(a[5]) : 0;
            } catch (Throwable ignore) {
            }
        }
        if (t != day) { // новый день — сбрасываем отметки «сегодня»
            day = t;
            out = 0;
            in = 0;
        }
        if (outgoing) {
            out = 1;
        } else {
            in = 1;
        }
        boolean streakChanged = false;
        if (out == 1 && in == 1 && streakDay != t) { // сегодня написали ОБА — засчитываем день
            if (streakDay == t - 1) {
                streak += 1;
            } else {
                streak = 1;
            }
            streakDay = t;
            streakChanged = true;
        }
        String newState = day + "," + out + "," + in + "," + streak + "," + streakDay + "," + restores;
        if (newState.equals(s)) {
            return; // ничего не изменилось
        }
        p.edit().putString(key(dialogId), newState).apply();
        // пишем в облако (под своим id)
        long myId = UserConfig.getInstance(accountId).getClientUserId();
        DevGramNetworkQueue.postRunnable(() ->
                httpSend("PUT", withAuth(RTDB_BASE + "/streaks/" + myId + "/" + dialogId + ".json"), "\"" + newState + "\""));
        if (streakChanged) {
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getInstance(accountId).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME));
        }
    }

    // Текущий стрик диалога (0 — нет). Жив, если последний засчитанный день — вчера или сегодня.
    public static int getStreak(long dialogId) {
        if (!DevGramConfig.streaksEnabled) {
            return 0;
        }
        SharedPreferences p = prefs();
        if (p == null || dialogId <= 0) {
            return 0;
        }
        String s = p.getString(key(dialogId), null);
        if (s == null) {
            return 0;
        }
        try {
            String[] a = s.split(",");
            int streak = Integer.parseInt(a[3]);
            int streakDay = Integer.parseInt(a[4]);
            if (streakDay < today() - 1) {
                return 0; // стрик умер (пропустили день)
            }
            return streak >= MIN_SHOWN ? streak : 0; // огонёк показываем только от 3
        } catch (Throwable e) {
            return 0;
        }
    }

    // ===== потеря стрика и восстановление =====

    // Счёт серии, если она сейчас потеряна, но восстановима (для сообщения прямо в чате). Иначе 0.
    public static int lostRestorableStreak(long dialogId) {
        if (!DevGramConfig.streaksEnabled) {
            return 0;
        }
        SharedPreferences p = prefs();
        if (p == null || dialogId <= 0) {
            return 0;
        }
        String s = p.getString(key(dialogId), null);
        if (s == null) {
            return 0;
        }
        try {
            String[] a = s.split(",");
            int streak = Integer.parseInt(a[3]);
            int streakDay = Integer.parseInt(a[4]);
            int restores = a.length > 5 ? Integer.parseInt(a[5]) : 0;
            int t = today();
            if (streak >= MIN_SHOWN && streakDay < t - 1 && streakDay >= t - 7 && restores < MAX_RESTORES) {
                return streak;
            }
        } catch (Throwable ignore) {
        }
        return 0;
    }

    // Сколько восстановлений осталось на диалог.
    public static int restoresLeft(long dialogId) {
        return Math.max(0, MAX_RESTORES - restoresUsed(dialogId));
    }

    // true = серия продлена сегодня (активный цветной огонёк).
    // false = ещё не продлена сегодня, но жива (серый огонёк).
    public static boolean isStreakActiveToday(long dialogId) {
        SharedPreferences p = prefs();
        if (p == null || dialogId <= 0) {
            return true;
        }
        String s = p.getString(key(dialogId), null);
        if (s == null) {
            return true;
        }
        try {
            int streakDay = Integer.parseInt(s.split(",")[4]);
            return streakDay >= today();
        } catch (Throwable e) {
            return true;
        }
    }

    // Счётчик восстановлений хранится в самом облачном состоянии (6-е поле),
    // поэтому после переустановки лимит не сбрасывается.
    private static int restoresUsed(long dialogId) {
        SharedPreferences p = prefs();
        if (p == null) {
            return 0;
        }
        String s = p.getString(key(dialogId), null);
        if (s == null) {
            return 0;
        }
        try {
            String[] a = s.split(",");
            return a.length > 5 ? Integer.parseInt(a[5]) : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    // На старте: ищем только что потерянные стрики и шлём уведомление с кнопкой «Восстановить».
    public static void checkLostStreaks() {
        if (!DevGramConfig.streaksEnabled) {
            return;
        }
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        int t = today();
        int account = UserConfig.selectedAccount;
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            if (k.indexOf('_') >= 0 || !(e.getValue() instanceof String)) {
                continue; // мета-ключи (_r, _notified)
            }
            long dialogId;
            try {
                dialogId = Long.parseLong(k);
            } catch (Throwable ex) {
                continue;
            }
            try {
                String[] a = ((String) e.getValue()).split(",");
                int streak = Integer.parseInt(a[3]);
                int streakDay = Integer.parseInt(a[4]);
                int restores = a.length > 5 ? Integer.parseInt(a[5]) : 0;
                // потерян: был >= MIN_SHOWN, пропущен день (streakDay < вчера), не старше недели
                // и остались восстановления. Флаг _notified локальный — после переустановки
                // он пропадает, поэтому уведомление возвращается (по облачному состоянию).
                boolean lost = streak >= MIN_SHOWN && streakDay < t - 1 && streakDay >= t - 7 && restores < MAX_RESTORES;
                if (lost) {
                    if (p.getInt(dialogId + "_notified", Integer.MIN_VALUE) == streakDay) {
                        continue; // уже уведомляли про эту потерю в этой установке
                    }
                    p.edit().putInt(dialogId + "_notified", streakDay).apply();
                    notifyLost(account, dialogId, streak);
                } else if (isDeadForever((String) e.getValue(), t)) {
                    // огонёк навсегда потух (0 и без шанса на восстановление) — незачем зря
                    // занимать место в облаке и в локальном кэше, стираем запись целиком.
                    purgeDeadStreak(account, dialogId, k);
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private static void notifyLost(int account, long dialogId, int streak) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) {
                return;
            }
            TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
            String name = String.valueOf(u != null ? UserObject.getUserName(u) : ("id " + dialogId));
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            String channelId = "devgram_streaks";
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(channelId, "Огоньки DevGram", NotificationManager.IMPORTANCE_DEFAULT));
            }
            boolean canRestore = restoresUsed(dialogId) < MAX_RESTORES;
            String text = "Твой огонёк с " + name + " (" + streak + " 🔥) потерян из-за неактивности."
                    + (canRestore ? "" : " Восстановления закончились.");
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channelId)
                    .setSmallIcon(org.telegram.messenger.devgram.icons.IconManager.INSTANCE.notificationIcon())
                    .setContentTitle("Огонёк потерян 🔥")
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true);
            if (canRestore) {
                Intent i = new Intent(ctx, DevGramStreakReceiver.class);
                i.setAction("devgram.restore." + dialogId);
                i.putExtra("dialogId", dialogId);
                i.putExtra("account", account);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
                PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) dialogId, i, flags);
                b.addAction(0, "Восстановить (" + (MAX_RESTORES - restoresUsed(dialogId)) + ")", pi);
            }
            nm.notify((int) dialogId, b.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Восстановить стрик: снова активен (на сутки), счёт НЕ меняется. Лимит MAX_RESTORES на диалог.
    public static void restore(int account, long dialogId) {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        int used = restoresUsed(dialogId);
        if (used >= MAX_RESTORES) {
            return;
        }
        String s = p.getString(key(dialogId), null);
        if (s == null) {
            return;
        }
        try {
            int streak = Integer.parseInt(s.split(",")[3]);
            int t = today();
            // оживляем: streakDay=t (жив сегодня и завтра), счёт тот же, сегодня не плюсуем.
            // Счётчик восстановлений (used+1) уходит в облако вместе с состоянием.
            String newState = t + ",0,0," + streak + "," + t + "," + (used + 1);
            p.edit().putString(key(dialogId), newState).apply();
            long myId = UserConfig.getInstance(account).getClientUserId();
            DevGramNetworkQueue.postRunnable(() ->
                    httpSend("PUT", withAuth(RTDB_BASE + "/streaks/" + myId + "/" + dialogId + ".json"), "\"" + newState + "\""));
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME));
        } catch (Throwable ignore) {
        }
    }

    // ===== напоминание «серия скоро сгорит» =====

    // Если серия жива, но сегодня не продлена, и до полуночи осталось <=3 часа —
    // шлём напоминание «не дай серии прерваться». Раз в день на контакт.
    public static void checkExpiringStreaks() {
        if (!DevGramConfig.streaksEnabled) {
            return;
        }
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        long msLeft = msUntilLocalMidnight();
        if (msLeft <= 0 || msLeft > 3 * 3600000L) {
            return; // напоминаем только в последние 3 часа перед полуночью
        }
        int hoursLeft = (int) Math.max(1, Math.round(msLeft / 3600000.0));
        int t = today();
        int account = UserConfig.selectedAccount;
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            if (k.indexOf('_') >= 0 || !(e.getValue() instanceof String)) {
                continue;
            }
            long dialogId;
            try {
                dialogId = Long.parseLong(k);
            } catch (Throwable ex) {
                continue;
            }
            try {
                String[] a = ((String) e.getValue()).split(",");
                int streak = Integer.parseInt(a[3]);
                int streakDay = Integer.parseInt(a[4]);
                // жив, но сегодня ещё не продлён (серый) и достаточно длинный
                if (streak >= MIN_SHOWN && streakDay == t - 1) {
                    if (p.getInt(dialogId + "_reminded", Integer.MIN_VALUE) == t) {
                        continue; // уже напоминали сегодня
                    }
                    p.edit().putInt(dialogId + "_reminded", t).apply();
                    notifyExpiring(account, dialogId, streak, hoursLeft);
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private static long msUntilLocalMidnight() {
        try {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, 1);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis() - System.currentTimeMillis();
        } catch (Throwable e) {
            return -1;
        }
    }

    private static void notifyExpiring(int account, long dialogId, int streak, int hoursLeft) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) {
                return;
            }
            TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
            String name = String.valueOf(u != null ? UserObject.getUserName(u) : ("id " + dialogId));
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            String channelId = "devgram_streaks";
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(channelId, "Огоньки DevGram", NotificationManager.IMPORTANCE_DEFAULT));
            }
            String text = "Серия с " + name + " (" + streak + " 🔥) прервётся через ~" + hoursLeft + " ч. Напиши, чтобы не потерять!";
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channelId)
                    .setSmallIcon(org.telegram.messenger.devgram.icons.IconManager.INSTANCE.notificationIcon())
                    .setContentTitle("Не дай серии прерваться 🔥")
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true);
            Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (open != null) {
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
                b.setContentIntent(PendingIntent.getActivity(ctx, (int) dialogId, open, flags));
            }
            nm.notify("streak_expire", (int) dialogId, b.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String httpSend(String method, String urlStr, String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
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
            return ok ? sb.toString() : null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}

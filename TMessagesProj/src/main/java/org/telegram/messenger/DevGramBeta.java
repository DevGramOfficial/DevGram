package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Бета-канал DevGram. Доступ только поддержавшим (значок ACCESS_SUPPORTER).
 *
 * Активация происходит В ФОНЕ, человек ничего не пишет боту: клиент сам отправляет
 * @officialdevgram_bot команду «/beta_token», бот видит реальный telegram-id, проверяет значок,
 * кладёт hash(token) в Firebase /beta_tokens/{uid} и отвечает сообщением «DEVGRAM_BETA_TOKEN:...».
 * Мы ловим ответ, сохраняем токен и подчищаем служебную переписку. Дальше AppUpdater качает
 * бету с api.devgram.space, предъявляя uid+token (проверка значка — на сервере, обойти нельзя).
 */
public final class DevGramBeta {

    public static final String BOT_USERNAME = DevGramPackages.CATALOG_BOT_USERNAME; // officialdevgram_bot
    private static final String TOKEN_MARKER = "DEVGRAM_BETA_TOKEN:";

    private DevGramBeta() {}

    /** Есть ли у текущего пользователя доступ к бете (значок поддержавшего/команды). */
    public static boolean hasAccess(int account) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        return DevGramBadges.hasSupporterFeatures(uid);
    }

    /** Уже получен персональный токен? */
    public static boolean hasToken() {
        return !DevGramConfig.getBetaToken().isEmpty();
    }

    public static boolean hasToken(int account) {
        return !DevGramConfig.getBetaToken(UserConfig.getInstance(account).getClientUserId()).isEmpty();
    }

    public interface Callback { void onResult(boolean ok, String error); }

    /**
     * Фоново получить токен доступа к бете. Если значка нет — бот откажет (ok=false).
     * Идемпотентно: если токен уже есть, сразу ok=true.
     */
    public static void activate(final int account, final Callback callback) {
        if (hasToken(account)) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(true, null));
            return;
        }
        if (!hasAccess(account)) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(false, "Нужен значок поддержавшего"));
            return;
        }
        // «Вход через Telegram» (без переписки с ботом) — только с согласия пользователя; бот — запасной путь
        askConsent(account, agreed -> {
            if (!agreed) {
                callback.onResult(false, "Подтверждение отменено");
                return;
            }
            loginViaTelegram(account, ok -> {
                if (ok) {
                    callback.onResult(true, null);
                } else {
                    activateViaBot(account, callback);
                }
            });
        });
    }

    // ---- согласие на подтверждение аккаунта (один раз на аккаунт) ----
    private static void askConsent(final int account, final Utilities.Callback<Boolean> done) {
        final long uid = UserConfig.getInstance(account).getClientUserId();
        final android.content.SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        final String key = "devgramAuthConsent_" + uid;
        if (prefs.getBoolean(key, false)) {
            done.run(true);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            org.telegram.ui.ActionBar.BaseFragment fragment = org.telegram.ui.LaunchActivity.getSafeLastFragment();
            if (fragment == null || fragment.getParentActivity() == null) {
                done.run(false);
                return;
            }
            final boolean[] answered = {false};
            org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
            b.setTitle("Подтверждение аккаунта");
            b.setMessage(AndroidUtilities.replaceTags(
                    "Чтобы сохранить ваш значок, обводку сообщений и доступ к бете на сервере DevGram, нужно подтвердить, что это именно ваш аккаунт.\n\n"
                    + "**Как это работает:** так же, как «Войти через Telegram» на сайтах. Telegram сам передаст серверу DevGram ваш ID, имя, юзернейм и фото профиля, подписанные Telegram, — подделать их нельзя.\n\n"
                    + "**Что получаем мы:** только эти данные, чтобы выдать вам личный ключ доступа. Номер телефона, переписки и контакты не передаются.\n\n"
                    + "**Что получаете вы:** выбор значка и обводки сообщений, которые увидят все пользователи DevGram, и бета-версии — без переписки с ботом."));
            b.setPositiveButton("Согласиться", (d, w) -> {
                answered[0] = true;
                prefs.edit().putBoolean(key, true).apply();
                done.run(true);
            });
            b.setNegativeButton("Отказаться", (d, w) -> {
                answered[0] = true;
                done.run(false);
            });
            org.telegram.ui.ActionBar.AlertDialog dialog = b.create();
            dialog.setOnDismissListener(d -> {
                if (!answered[0]) {
                    answered[0] = true;
                    done.run(false);
                }
            });
            fragment.showDialog(dialog);
        });
    }

    // ---- «Вход через Telegram» ----
    // Telegram сам подписывает данные аккаунта токеном нашего бота (домен привязан через /setdomain),
    // сервер сверяет подпись и выдаёт персональный токен. Боту ничего не пишется.
    private static final String AUTH_HOST = "api.devgram.space";
    private static final String AUTH_URL = "https://" + AUTH_HOST + "/api/auth/telegram";

    private static void loginViaTelegram(final int account, final Utilities.Callback<Boolean> done) {
        TLRPC.TL_messages_requestUrlAuth req = new TLRPC.TL_messages_requestUrlAuth();
        req.url = AUTH_URL;
        req.flags |= 4; // url
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_urlAuthResultAccepted) {
                fetchToken(account, ((TLRPC.TL_urlAuthResultAccepted) response).url, done);
            } else if (response instanceof TLRPC.TL_urlAuthResultRequest) {
                TLRPC.TL_messages_acceptUrlAuth accept = new TLRPC.TL_messages_acceptUrlAuth();
                accept.url = AUTH_URL;
                accept.flags |= 4; // url
                accept.write_allowed = false;
                ConnectionsManager.getInstance(account).sendRequest(accept, (r2, e2) -> {
                    if (r2 instanceof TLRPC.TL_urlAuthResultAccepted) {
                        fetchToken(account, ((TLRPC.TL_urlAuthResultAccepted) r2).url, done);
                    } else {
                        FileLog.e("DevGramBeta: acceptUrlAuth " + (e2 != null ? e2.text : String.valueOf(r2)));
                        AndroidUtilities.runOnUIThread(() -> done.run(false));
                    }
                });
            } else {
                FileLog.e("DevGramBeta: requestUrlAuth " + (error != null ? error.text : String.valueOf(response)));
                AndroidUtilities.runOnUIThread(() -> done.run(false));
            }
        });
    }

    private static void fetchToken(final int account, final String signedUrl, final Utilities.Callback<Boolean> done) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            try {
                android.net.Uri uri = android.net.Uri.parse(signedUrl);
                // подписанную ссылку открываем только на нашем сервере
                if (signedUrl != null && "https".equals(uri.getScheme()) && AUTH_HOST.equals(uri.getHost())) {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(signedUrl).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    int code = conn.getResponseCode();
                    java.io.InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                    StringBuilder sb = new StringBuilder();
                    if (is != null) {
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                        }
                    }
                    conn.disconnect();
                    org.json.JSONObject r = new org.json.JSONObject(sb.toString());
                    long uid = UserConfig.getInstance(account).getClientUserId();
                    String token = r.optString("token", "");
                    if (r.optBoolean("ok", false) && r.optLong("uid", 0) == uid && !token.isEmpty()) {
                        DevGramConfig.setBetaToken(uid, token);
                        ok = true;
                    } else {
                        FileLog.e("DevGramBeta: auth " + code + " " + sb);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final boolean fOk = ok;
            AndroidUtilities.runOnUIThread(() -> done.run(fOk));
        });
    }

    // ---- запасной путь: токен от бота по команде /beta_token ----
    private static void activateViaBot(final int account, final Callback callback) {
        DevGramPackages.resolveUsernameThen(account, BOT_USERNAME, (user, chat) -> {
            if (user == null) {
                callback.onResult(false, "Бот недоступен");
                return;
            }
            final long botId = user.id;
            listenForToken(account, botId, callback);
            DevGramPlugins.sendMessage(botId, "/beta_token");
        });
    }

    private static void listenForToken(final int account, final long botId, final Callback callback) {
        final NotificationCenter nc = NotificationCenter.getInstance(account);
        final boolean[] done = {false};
        final NotificationCenter.NotificationCenterDelegate[] holder = new NotificationCenter.NotificationCenterDelegate[1];

        final Runnable timeout = () -> {
            if (done[0]) return;
            done[0] = true;
            if (holder[0] != null) nc.removeObserver(holder[0], NotificationCenter.didReceiveNewMessages);
            callback.onResult(false, "Не удалось активировать (нет ответа)");
        };

        holder[0] = (id, acc, args) -> {
            if (done[0] || id != NotificationCenter.didReceiveNewMessages) return;
            try {
                long dialogId = (Long) args[0];
                if (dialogId != botId) return;
                @SuppressWarnings("unchecked")
                ArrayList<MessageObject> msgs = (ArrayList<MessageObject>) args[1];
                for (MessageObject mo : msgs) {
                    if (mo.getDialogId() != botId || mo.isOutOwner()) continue;
                    String text = mo.messageOwner != null ? mo.messageOwner.message : null;
                    if (text == null) continue;
                    int idx = text.indexOf(TOKEN_MARKER);
                    if (idx < 0) {
                        // бот отказал (нет значка и т.п.) — тоже финал
                        if (text.contains("Бета") || text.contains("поддержав") || text.contains("🔒")) {
                            done[0] = true;
                            AndroidUtilities.cancelRunOnUIThread(timeout);
                            nc.removeObserver(holder[0], NotificationCenter.didReceiveNewMessages);
                            // didReceiveNewMessages может прийти с фонового потока обработки апдейтов —
                            // колбэк дёргает UI (обновление списка/буллетин), поэтому строго на UI-поток.
                            AndroidUtilities.runOnUIThread(() -> callback.onResult(false, "Бот отказал в доступе"));
                            return;
                        }
                        continue;
                    }
                    String token = text.substring(idx + TOKEN_MARKER.length()).trim();
                    int nl = token.indexOf('\n');
                    if (nl >= 0) token = token.substring(0, nl);
                    token = token.trim();
                    if (token.isEmpty()) continue;
                    done[0] = true;
                    AndroidUtilities.cancelRunOnUIThread(timeout);
                    nc.removeObserver(holder[0], NotificationCenter.didReceiveNewMessages);
                    DevGramConfig.setBetaToken(UserConfig.getInstance(account).getClientUserId(), token);
                    // подчистим служебную переписку с ботом (не мусорим в списке чатов)
                    cleanupBotChat(account, botId);
                    // строго на UI-поток: didReceiveNewMessages мог прийти с фонового потока,
                    // а колбэк обновляет экран (иначе «сразу не обновляется» при получении токена).
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(true, null));
                    return;
                }
            } catch (Exception ignore) {}
        };
        nc.addObserver(holder[0], NotificationCenter.didReceiveNewMessages);
        AndroidUtilities.runOnUIThread(timeout, 25000);
    }

    /** Удалить диалог с ботом целиком (у себя), чтобы служебные /beta_token не мозолили глаза. */
    private static void cleanupBotChat(final int account, final long botId) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                MessagesController.getInstance(account).deleteDialog(botId, 1, false);
            } catch (Exception ignore) {}
        }, 1500);
    }
}

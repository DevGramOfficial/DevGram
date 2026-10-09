package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;


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
        // «Вход через Telegram» (без переписки с ботом) — только с согласия пользователя
        askConsent(account, agreed -> {
            if (!agreed) {
                callback.onResult(false, "Подтверждение отменено");
                return;
            }
            loginViaTelegram(account, ok -> callback.onResult(ok, ok ? null : "Не удалось подтвердить аккаунт"));
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

    // Служебный пост с кнопкой входа (login_url) в публичном канале @devgramarchive. Вход по обычной
    // ссылке Telegram подписывает только для своих доменов, а кнопка входа работает для домена бота.
    // Приложение «нажимает» кнопку программно — писать боту и подписываться на канал не нужно.
    private static final int AUTH_MSG_ID = 10;

    private static void loginViaTelegram(final int account, final Utilities.Callback<Boolean> done) {
        DevGramPackages.resolveUsernameThen(account, DevGramPackages.ARCHIVE_USERNAME, (user, chat) -> {
            if (chat == null) {
                AndroidUtilities.runOnUIThread(() -> done.run(false));
                return;
            }
            final TLRPC.InputPeer peer = MessagesController.getInstance(account).getInputPeer(-chat.id);
            TLRPC.TL_channels_getMessages get = new TLRPC.TL_channels_getMessages();
            get.channel = MessagesController.getInstance(account).getInputChannel(chat.id);
            get.id.add(AUTH_MSG_ID);
            ConnectionsManager.getInstance(account).sendRequest(get, (response, error) -> {
                int buttonId = -1;
                if (response instanceof TLRPC.messages_Messages) {
                    for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                        if (m == null || m.id != AUTH_MSG_ID || !(m.reply_markup instanceof TLRPC.TL_replyInlineMarkup)) continue;
                        for (org.telegram.tgnet.tl.TL_keyboard.KeyboardInlineButtonRow row : ((TLRPC.TL_replyInlineMarkup) m.reply_markup).rows) {
                            for (org.telegram.tgnet.tl.TL_keyboard.KeyboardInlineButton btn : row.buttons) {
                                org.telegram.tgnet.tl.TL_keyboard.TL_inlineButtonTypeUrlAuth auth = org.telegram.messenger.utils.tlutils.TLKeyboardHelper
                                        .getType(btn, org.telegram.tgnet.tl.TL_keyboard.TL_inlineButtonTypeUrlAuth.class);
                                if (auth != null) buttonId = auth.button_id;
                            }
                        }
                    }
                }
                if (buttonId < 0) {
                    FileLog.e("DevGramBeta: нет кнопки входа " + (error != null ? error.text : ""));
                    AndroidUtilities.runOnUIThread(() -> done.run(false));
                    return;
                }
                final int fButtonId = buttonId;
                TLRPC.TL_messages_requestUrlAuth req = new TLRPC.TL_messages_requestUrlAuth();
                req.flags |= 2; // peer + msg_id + button_id
                req.peer = peer;
                req.msg_id = AUTH_MSG_ID;
                req.button_id = fButtonId;
                ConnectionsManager.getInstance(account).sendRequest(req, (r1, e1) -> {
                    if (r1 instanceof TLRPC.TL_urlAuthResultAccepted) {
                        fetchToken(account, ((TLRPC.TL_urlAuthResultAccepted) r1).url, done);
                    } else if (r1 instanceof TLRPC.TL_urlAuthResultRequest) {
                        TLRPC.TL_messages_acceptUrlAuth accept = new TLRPC.TL_messages_acceptUrlAuth();
                        accept.flags |= 2;
                        accept.peer = peer;
                        accept.msg_id = AUTH_MSG_ID;
                        accept.button_id = fButtonId;
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
                        FileLog.e("DevGramBeta: requestUrlAuth " + (e1 != null ? e1.text : String.valueOf(r1)));
                        AndroidUtilities.runOnUIThread(() -> done.run(false));
                    }
                });
            });
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

    // ---- закрытый канал беты: персональная ссылка-заявка ----
    // Сервер выдаёт её только поддержавшему (ключ + значок) и подписывает его id; бот одобряет заявку
    // только от этого же человека и сразу отзывает ссылку — переслать её другому бесполезно.
    public interface InviteCallback { void onResult(String link, String error); }

    public static long getBetaChannel() {
        return MessagesController.getGlobalMainSettings().getLong("devgramBetaChannel", 0);
    }

    public static boolean isBetaChannelMember(int account) {
        long channel = getBetaChannel();
        if (channel == 0) return false;
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(channel);
        return chat != null && !ChatObject.isNotInChat(chat);
    }

    public static void requestInvite(final int account, final InviteCallback callback) {
        activate(account, (ok, error) -> {
            if (!ok) {
                callback.onResult(null, error);
                return;
            }
            final long uid = UserConfig.getInstance(account).getClientUserId();
            final String token = DevGramConfig.getBetaToken(uid);
            Utilities.globalQueue.postRunnable(() -> {
                String link = null;
                String err = "Не удалось получить ссылку";
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL("https://" + AUTH_HOST
                            + "/api/beta/invite?uid=" + uid + "&token=" + java.net.URLEncoder.encode(token, "UTF-8")).openConnection();
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
                    if (code == 403) {
                        err = "Нет доступа к бете";
                    } else if (code == 503) {
                        err = "Канал беты ещё не готов";
                    } else if (code == 200) {
                        org.json.JSONObject r = new org.json.JSONObject(sb.toString());
                        String l = r.optString("link", "");
                        if (r.optBoolean("ok") && l.startsWith("https://t.me/")) {
                            link = l;
                            MessagesController.getGlobalMainSettings().edit().putLong("devgramBetaChannel", r.optLong("channel", 0)).apply();
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                final String fLink = link, fErr = link != null ? null : err;
                AndroidUtilities.runOnUIThread(() -> callback.onResult(fLink, fErr));
            });
        });
    }
}

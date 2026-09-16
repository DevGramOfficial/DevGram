package org.telegram.messenger;

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

    public interface Callback { void onResult(boolean ok, String error); }

    /**
     * Фоново получить токен доступа к бете. Если значка нет — бот откажет (ok=false).
     * Идемпотентно: если токен уже есть, сразу ok=true.
     */
    public static void activate(final int account, final Callback callback) {
        if (hasToken()) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(true, null));
            return;
        }
        if (!hasAccess(account)) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(false, "Нужен значок поддержавшего"));
            return;
        }
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
                    DevGramConfig.setBetaToken(token);
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

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

// DevGram: откуда на самом деле пришёл файл плагина. Раньше «проверенный разработчик» решался
// только по чату, где лежит файл, — после пересылки из канала-разработчика (🧩) плагин становился
// «неизвестным источником». Доверяем только тому, что нельзя подделать со стороны:
//   1) файл открыт прямо в канале-разработчике;
//   2) пересылка: исходный канал из fwd_from / saved_from_peer (ставит сервер Telegram);
//   3) тот же документ (id) уже встречался в канале-разработчике на этом устройстве;
//   4) поиск по имени файла в каналах-разработчиках и сверка id документа — ловит пересылку без
//      автора, «Поделиться» и повторную отправку того же документа (id у них совпадает).
// Перезалитый файл (новый id) остаётся неизвестным — иначе любой выдал бы свой код за чужой,
// назвав файл так же.
public final class DevGramPluginOrigin {

    private static final String PREFS = "devgram_plugin_origin";
    private static final int SEARCH_LIMIT = 50;

    public interface Callback {
        void onFound(long devChannelDialogId);
    }

    private DevGramPluginOrigin() {
    }

    // Быстрая синхронная проверка (1–3). Возвращает диалог канала-разработчика или dialogId как есть.
    public static long originDialog(MessageObject message, long dialogId) {
        if (DevGramBadges.isPluginDevChannel(dialogId)) {
            remember(message, dialogId);
            return dialogId;
        }
        if (message == null || message.messageOwner == null) {
            return dialogId;
        }
        TLRPC.MessageFwdHeader fwd = message.messageOwner.fwd_from;
        if (fwd != null) {
            long from = channelDialog(fwd.from_id);
            if (DevGramBadges.isPluginDevChannel(from)) {
                remember(message, from);
                return from;
            }
            long saved = channelDialog(fwd.saved_from_peer);
            if (DevGramBadges.isPluginDevChannel(saved)) {
                remember(message, saved);
                return saved;
            }
        }
        long cached = cachedOrigin(message);
        if (DevGramBadges.isPluginDevChannel(cached)) {
            return cached;
        }
        return dialogId;
    }

    // Медленная проверка (4): ищет этот же документ в каналах-разработчиках. Колбэк — только при
    // находке, на UI-потоке.
    public static void findInDevChannels(int account, MessageObject message, Callback callback) {
        TLRPC.Document doc = message != null ? message.getDocument() : null;
        String fileName = message != null ? message.getDocumentName() : null;
        if (doc == null || doc.id == 0 || fileName == null || fileName.isEmpty()) {
            return;
        }
        ArrayList<Long> channels = new ArrayList<>();
        for (Long dialogId : DevGramBadges.listGranted()) {
            if (dialogId != null && DevGramBadges.isPluginDevChannel(dialogId)) {
                channels.add(dialogId);
            }
        }
        searchNext(account, channels, 0, doc.id, fileName, message, callback);
    }

    private static void searchNext(int account, ArrayList<Long> channels, int index, long docId, String fileName,
                                   MessageObject message, Callback callback) {
        if (index >= channels.size()) {
            return;
        }
        long dialogId = channels.get(index);
        MessagesController mc = MessagesController.getInstance(account);
        // нужен access_hash: канал должен быть известен клиенту (подписка или открывали раньше)
        TLRPC.Chat chat = mc.getChat(-dialogId);
        TLRPC.InputPeer peer = chat != null ? mc.getInputPeer(dialogId) : null;
        if (peer == null) {
            searchNext(account, channels, index + 1, docId, fileName, message, callback);
            return;
        }
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = peer;
        req.q = fileName;
        req.filter = new TLRPC.TL_inputMessagesFilterDocument();
        req.limit = SEARCH_LIMIT;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            boolean found = false;
            if (response instanceof TLRPC.messages_Messages) {
                for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                    TLRPC.Document d = m.media != null ? m.media.document : null;
                    if (d != null && d.id == docId) {
                        found = true;
                        break;
                    }
                }
            }
            if (found) {
                AndroidUtilities.runOnUIThread(() -> {
                    remember(message, dialogId);
                    callback.onFound(dialogId);
                });
            } else {
                searchNext(account, channels, index + 1, docId, fileName, message, callback);
            }
        });
    }

    private static long channelDialog(TLRPC.Peer peer) {
        if (peer instanceof TLRPC.TL_peerChannel && peer.channel_id != 0) {
            return -peer.channel_id;
        }
        return 0;
    }

    private static void remember(MessageObject message, long devChannelDialogId) {
        TLRPC.Document doc = message != null ? message.getDocument() : null;
        if (doc == null || doc.id == 0) {
            return;
        }
        prefs().edit().putLong("doc_" + doc.id, devChannelDialogId).apply();
    }

    private static long cachedOrigin(MessageObject message) {
        TLRPC.Document doc = message.getDocument();
        if (doc == null || doc.id == 0) {
            return 0;
        }
        return prefs().getLong("doc_" + doc.id, 0);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

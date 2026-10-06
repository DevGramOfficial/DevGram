package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// DevGram: откуда на самом деле пришёл файл плагина. «Проверенный разработчик» (🧩) ставится только
// по признакам, которые нельзя подделать со стороны:
//   1) файл открыт прямо в канале-разработчике;
//   2) пересылка: исходный канал из fwd_from / saved_from_peer (ставит сервер Telegram);
//   3) тот же документ или тот же файл (SHA-256) уже встречался в канале-разработчике на этом устройстве;
//   4) поиск в каналах-разработчиках: совпал id документа (пересылка без автора, «Поделиться»)
//      ИЛИ содержимое файла из поста байт в байт совпало с нашим (скачали и отправили заново,
//      отправили из списка плагинов, переименовали). Совпадение проверяется по SHA-256 скачанной
//      из канала копии — подделать нельзя: свой код с чужим хешем не собрать.
public final class DevGramPluginOrigin {

    private static final String PREFS = "devgram_plugin_origin";
    private static final int SEARCH_LIMIT = 50;
    private static final long MAX_COMPARE_BYTES = 32L * 1024 * 1024; // крупнее — не качаем ради сверки
    private static final int CHUNK = 512 * 1024;

    public interface Callback {
        void onFound(long devChannelDialogId);
    }

    private DevGramPluginOrigin() {
    }

    // Быстрая синхронная проверка (1–3). Возвращает диалог канала-разработчика или dialogId как есть.
    public static long originDialog(MessageObject message, long dialogId, File localFile) {
        String sha = localFile != null ? fileSha(localFile) : null;
        if (DevGramBadges.isPluginDevChannel(dialogId)) {
            remember(message, sha, dialogId);
            return dialogId;
        }
        if (message != null && message.messageOwner != null) {
            TLRPC.MessageFwdHeader fwd = message.messageOwner.fwd_from;
            if (fwd != null) {
                long from = channelDialog(fwd.from_id);
                if (DevGramBadges.isPluginDevChannel(from)) {
                    remember(message, sha, from);
                    return from;
                }
                long saved = channelDialog(fwd.saved_from_peer);
                if (DevGramBadges.isPluginDevChannel(saved)) {
                    remember(message, sha, saved);
                    return saved;
                }
            }
            long byDoc = cachedByDocument(message);
            if (DevGramBadges.isPluginDevChannel(byDoc)) {
                return byDoc;
            }
        }
        long byHash = sha != null ? prefs().getLong("sha_" + sha, 0) : 0;
        if (DevGramBadges.isPluginDevChannel(byHash)) {
            return byHash;
        }
        return dialogId;
    }

    // Медленная проверка (4) в фоне. Колбэк — только при находке, на UI-потоке.
    public static void findInDevChannels(int account, MessageObject message, File localFile,
                                         String pluginName, String pluginId, Callback callback) {
        if (localFile == null || !localFile.exists()) {
            return;
        }
        ArrayList<Long> channels = new ArrayList<>();
        for (Long dialogId : DevGramBadges.listGranted()) {
            if (dialogId != null && DevGramBadges.isPluginDevChannel(dialogId)) {
                channels.add(dialogId);
            }
        }
        if (channels.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                long found = search(account, message, localFile, pluginName, pluginId, channels);
                if (found != 0) {
                    AndroidUtilities.runOnUIThread(() -> callback.onFound(found));
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }, "DevGramPluginOrigin").start();
    }

    private static long search(int account, MessageObject message, File localFile, String pluginName,
                               String pluginId, ArrayList<Long> channels) {
        String localSha = fileSha(localFile);
        long localSize = localFile.length();
        long docId = message != null && message.getDocument() != null ? message.getDocument().id : 0;
        // Что искать: имя файла (с расширением и без), название и id плагина
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String docName = message != null ? message.getDocumentName() : null;
        for (String q : new String[]{docName, stripExt(docName), localFile.getName(), stripExt(localFile.getName()), pluginName, pluginId}) {
            if (!TextUtils.isEmpty(q) && q.trim().length() >= 2) {
                queries.add(q.trim());
            }
        }
        MessagesController mc = MessagesController.getInstance(account);
        HashSet<Long> checkedDocs = new HashSet<>();
        for (long dialogId : channels) {
            // нужен access_hash: канал должен быть известен клиенту (подписка или открывали раньше)
            TLRPC.InputPeer peer = mc.getChat(-dialogId) != null ? mc.getInputPeer(dialogId) : null;
            if (peer == null) {
                continue;
            }
            for (String q : queries) {
                TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                req.peer = peer;
                req.q = q;
                req.filter = new TLRPC.TL_inputMessagesFilterDocument();
                req.limit = SEARCH_LIMIT;
                TLObject response = request(account, req, 0, ConnectionsManager.ConnectionTypeGeneric);
                if (!(response instanceof TLRPC.messages_Messages)) {
                    continue;
                }
                for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                    TLRPC.Document d = m.media != null ? m.media.document : null;
                    if (d == null || !checkedDocs.add(d.id)) {
                        continue;
                    }
                    if (docId != 0 && d.id == docId) {
                        remember(message, localSha, dialogId);
                        return dialogId;
                    }
                    // перезалитый файл: тот же размер → скачиваем копию из канала и сверяем содержимое
                    if (localSha != null && d.size == localSize && d.size <= MAX_COMPARE_BYTES) {
                        String remoteSha = downloadSha(account, d);
                        if (localSha.equals(remoteSha)) {
                            remember(message, localSha, dialogId);
                            return dialogId;
                        }
                    }
                }
            }
        }
        return 0;
    }

    private static String downloadSha(int account, TLRPC.Document d) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            TLRPC.TL_inputDocumentFileLocation location = new TLRPC.TL_inputDocumentFileLocation();
            location.id = d.id;
            location.access_hash = d.access_hash;
            location.file_reference = d.file_reference;
            location.thumb_size = "";
            long offset = 0;
            while (offset < d.size) {
                TLRPC.TL_upload_getFile req = new TLRPC.TL_upload_getFile();
                req.location = location;
                req.offset = offset;
                req.limit = CHUNK;
                TLObject response = request(account, req, d.dc_id, ConnectionsManager.ConnectionTypeDownload);
                if (!(response instanceof TLRPC.TL_upload_file)) {
                    return null;
                }
                NativeByteBuffer bytes = ((TLRPC.TL_upload_file) response).bytes;
                int len = bytes != null ? bytes.limit() : 0;
                if (len <= 0) {
                    break;
                }
                byte[] chunk = new byte[len];
                bytes.buffer.position(0);
                bytes.buffer.get(chunk, 0, len);
                md.update(chunk);
                offset += len;
                if (len < CHUNK) {
                    break;
                }
            }
            return offset == d.size ? hex(md.digest()) : null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Синхронный запрос с таймаутом (вызывать только из фонового потока).
    private static TLObject request(int account, TLObject req, int dcId, int connectionType) {
        final TLObject[] result = new TLObject[1];
        CountDownLatch latch = new CountDownLatch(1);
        if (dcId != 0) {
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error, responseTime) -> {
                result[0] = error == null ? response : null;
                latch.countDown();
            }, ConnectionsManager.RequestFlagForceDownload | ConnectionsManager.RequestFlagFailOnServerErrors, connectionType, dcId);
        } else {
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                result[0] = error == null ? response : null;
                latch.countDown();
            });
        }
        try {
            latch.await(25, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {
        }
        return result[0];
    }

    private static long channelDialog(TLRPC.Peer peer) {
        if (peer instanceof TLRPC.TL_peerChannel && peer.channel_id != 0) {
            return -peer.channel_id;
        }
        return 0;
    }

    private static void remember(MessageObject message, String sha, long devChannelDialogId) {
        SharedPreferences.Editor editor = prefs().edit();
        TLRPC.Document doc = message != null ? message.getDocument() : null;
        if (doc != null && doc.id != 0) {
            editor.putLong("doc_" + doc.id, devChannelDialogId);
        }
        if (sha != null) {
            editor.putLong("sha_" + sha, devChannelDialogId);
        }
        editor.apply();
    }

    private static long cachedByDocument(MessageObject message) {
        TLRPC.Document doc = message.getDocument();
        if (doc == null || doc.id == 0) {
            return 0;
        }
        return prefs().getLong("doc_" + doc.id, 0);
    }

    private static String fileSha(File file) {
        try {
            String sha = DevGramPackages.sha256File(file);
            return TextUtils.isEmpty(sha) ? null : sha.toLowerCase(Locale.US);
        } catch (Throwable e) {
            return null;
        }
    }

    private static String stripExt(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

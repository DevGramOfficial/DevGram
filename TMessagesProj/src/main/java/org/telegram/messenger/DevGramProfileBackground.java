package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

// DevGram: свой фон профиля поддержавших (фото/видео/GIF). Файл лежит постом в публичном архиве
// (@devgramarchive) — все пользователи качают его через Telegram, без нашего сервера. В значке хранится
// только номер поста; его ставит бот после модерации. Загрузка — тихой отправкой боту с подписью «dgbg».
public final class DevGramProfileBackground {

    public static final int MAX_VIDEO_SECONDS = 15;
    public static final int MAX_VIDEO_SIDE = 720;
    public static final String CAPTION = "dgbg";

    public interface Callback { void run(MessageObject media); }

    private static final HashMap<Integer, MessageObject> cache = new HashMap<>();
    private static final HashMap<Integer, ArrayList<Callback>> pending = new HashMap<>();

    private DevGramProfileBackground() {
    }

    // Пост с фоном из архива (кэшируется на время работы приложения). null — нет или недоступен.
    public static void load(int account, int msgId, Callback callback) {
        if (msgId <= 0) {
            callback.run(null);
            return;
        }
        MessageObject cached = cache.get(msgId);
        if (cached == null) cached = fromDisk(account, msgId);
        if (cached != null) {
            cache.put(msgId, cached);
            callback.run(cached);
            return;
        }
        ArrayList<Callback> waiting = pending.get(msgId);
        if (waiting != null) {
            waiting.add(callback);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(callback);
        pending.put(msgId, waiting);
        DevGramPackages.resolveUsernameThen(account, DevGramPackages.ARCHIVE_USERNAME, (user, chat) -> {
            if (chat == null) {
                finish(msgId, null);
                return;
            }
            TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
            req.channel = MessagesController.getInputChannel(chat);
            req.id.add(msgId);
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                MessageObject result = null;
                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    MessagesController.getInstance(account).putUsers(res.users, false);
                    MessagesController.getInstance(account).putChats(res.chats, false);
                    for (TLRPC.Message m : res.messages) {
                        if (m.id == msgId && m.media != null && (m.media.photo != null || m.media.document != null)) {
                            result = new MessageObject(account, m, false, false);
                            break;
                        }
                    }
                }
                if (result != null) {
                    cache.put(msgId, result);
                    toDisk(msgId, result.messageOwner);
                }
                finish(msgId, result);
            }));
        });
    }

    // Пост с фоном храним и на диске: после первого раза фон берётся без запросов в сеть
    private static android.content.SharedPreferences diskPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("devgram_bg_cache", android.content.Context.MODE_PRIVATE);
    }

    private static MessageObject fromDisk(int account, int msgId) {
        try {
            String b64 = diskPrefs().getString(String.valueOf(msgId), null);
            if (b64 == null) return null;
            org.telegram.tgnet.SerializedData data = new org.telegram.tgnet.SerializedData(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP));
            TLRPC.Message m = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
            data.cleanup();
            if (m == null || m.media == null) return null;
            return new MessageObject(account, m, false, false);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void toDisk(int msgId, TLRPC.Message m) {
        try {
            org.telegram.tgnet.SerializedData data = new org.telegram.tgnet.SerializedData(m.getObjectSize());
            m.serializeToStream(data);
            String b64 = android.util.Base64.encodeToString(data.toByteArray(), android.util.Base64.NO_WRAP);
            data.cleanup();
            android.content.SharedPreferences p = diskPrefs();
            android.content.SharedPreferences.Editor ed = p.edit();
            if (p.getAll().size() > 300) ed.clear(); // не копим бесконечно
            ed.putString(String.valueOf(msgId), b64).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Крошечная размытая миниатюра, которая лежит прямо в посте — показывается сразу, без загрузки
    public static TLRPC.PhotoSize strippedThumb(java.util.ArrayList<TLRPC.PhotoSize> sizes) {
        if (sizes == null) return null;
        for (TLRPC.PhotoSize s : sizes) {
            if (s instanceof TLRPC.TL_photoStrippedSize) return s;
        }
        return null;
    }

    private static void finish(int msgId, MessageObject media) {
        ArrayList<Callback> waiting = pending.remove(msgId);
        if (waiting == null) return;
        for (Callback cb : waiting) cb.run(media);
    }

    // Отправить выбранный файл боту на модерацию. mime — тип файла (image/…, video/…, image/gif).
    // Фото сжимаются как обычные фото, видео — до 15 с, без звука и не больше 720p. done(null) — ушло.
    public static void upload(int account, String path, String mime, Utilities.Callback<String> done) {
        if (path == null) {
            done.run("Не удалось открыть файл");
            return;
        }
        final boolean gif = "image/gif".equals(mime);
        final boolean video = mime != null && mime.startsWith("video/");
        VideoEditedInfo info = null;
        if (video) {
            info = SendMessagesHelper.createDevGramBackgroundVideo(path, MAX_VIDEO_SECONDS, MAX_VIDEO_SIDE);
            if (info == null) {
                done.run("Этот формат видео не поддерживается");
                return;
            }
        }
        final VideoEditedInfo fInfo = info;
        DevGramPackages.resolveUsernameThen(account, DevGramPackages.CATALOG_BOT_USERNAME, (user, chat) -> {
            if (user == null) {
                done.run("Нет связи с ботом DevGram");
                return;
            }
            try {
                AccountInstance ai = AccountInstance.getInstance(account);
                if (gif) {
                    // GIF уходит документом — Telegram сам превращает его в лёгкое видео
                    SendMessagesHelper.prepareSendingDocument(ai, path, path, null, CAPTION, null, user.id,
                            null, null, null, null, null, false, 0, null, null, false);
                } else {
                    SendMessagesHelper.SendingMediaInfo m = new SendMessagesHelper.SendingMediaInfo();
                    m.path = path;
                    m.caption = CAPTION;
                    m.isVideo = video;
                    m.videoEditedInfo = fInfo;
                    ArrayList<SendMessagesHelper.SendingMediaInfo> list = new ArrayList<>();
                    list.add(m);
                    SendMessagesHelper.prepareSendingMedia(ai, list, user.id, null, null, null, null, false, false, null,
                            false, 0, 0, 0, false, null, null, 0, false, 0, 0, null);
                }
                done.run(null);
            } catch (Throwable e) {
                FileLog.e(e);
                done.run("Не удалось отправить файл");
            }
        });
    }
}

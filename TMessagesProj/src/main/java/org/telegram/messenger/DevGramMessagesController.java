/*
 * DevGram: хранилище удалённых сообщений и истории правок.
 * Идея и точки интеграции портированы из AyuGram for Android (GPL v2+, © @Radolyn).
 * Реализация своя: весь TLRPC.Message сериализуется в BLOB (serializeToStream/TLdeserialize),
 * что сохраняет текст, форматирование, медиа-метаданные, реакции, форвард и reply «бесплатно».
 * Хранилище — обычный Android SQLite (без Room), чтобы не менять сборку.
 */

package org.telegram.messenger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.LongSparseArray;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DevGramMessagesController {

    private static volatile DevGramMessagesController instance;

    public static DevGramMessagesController getInstance() {
        if (instance == null) {
            synchronized (DevGramMessagesController.class) {
                if (instance == null) {
                    instance = new DevGramMessagesController();
                }
            }
        }
        return instance;
    }

    private final DbHelper helper;

    private DevGramMessagesController() {
        helper = new DbHelper(ApplicationLoader.applicationContext);
    }

    // ================= сериализация =================

    private static byte[] serialize(TLRPC.Message m) {
        try {
            SerializedData data = new SerializedData(m.getObjectSize());
            m.serializeToStream(data);
            byte[] bytes = data.toByteArray();
            data.cleanup();
            return bytes;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static TLRPC.Message deserialize(byte[] bytes) {
        try {
            SerializedData data = new SerializedData(bytes);
            int constructor = data.readInt32(false);
            TLRPC.Message m = TLRPC.Message.TLdeserialize(data, constructor, false);
            data.cleanup();
            return m;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // ================= удалённые =================

    // Диалоги с ботами по умолчанию не сохраняем: там своя лента служебного мусора,
    // который бот сам же и переписывает, и база разрастается без пользы.
    public static boolean skipDialog(int accountId, long dialogId) {
        if (DevGramConfig.saveInBotChats || dialogId <= 0) {
            return false;
        }
        TLRPC.User user = MessagesController.getInstance(accountId).getUser(dialogId);
        return user != null && user.bot;
    }

    public void onMessageDeleted(int accountId, TLRPC.Message msg, long dialogId, long topicId, int messageId, int catchTime) {
        if (!DevGramConfig.saveDeletedMessages || msg == null || skipDialog(accountId, dialogId)) {
            return;
        }
        long userId = UserConfig.getInstance(accountId).getClientUserId();
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            // уже сохранено?
            try (Cursor c = db.rawQuery(
                    "SELECT 1 FROM deleted_messages WHERE userId=? AND dialogId=? AND messageId=? LIMIT 1",
                    new String[]{Long.toString(userId), Long.toString(dialogId), Integer.toString(messageId)})) {
                if (c.moveToFirst()) {
                    return;
                }
            }
            // сохраняем сам файл вложения на диск, пока он ещё в кеше — иначе после
            // очистки кеша останется битый превью. Путь пишем в attachPath, чтобы медиа
            // грузилось из локальной копии (как в AyuGram).
            try {
                if (msg.dialog_id == 0) {
                    msg.dialog_id = dialogId;
                }
                String mediaPath = saveMediaFile(accountId, msg);
                if (mediaPath != null) {
                    msg.attachPath = mediaPath;
                }
            } catch (Throwable ignore) {
            }
            byte[] data = serialize(msg);
            if (data == null) {
                return;
            }
            ContentValues cv = new ContentValues();
            cv.put("userId", userId);
            cv.put("dialogId", dialogId);
            cv.put("topicId", topicId);
            cv.put("messageId", messageId);
            cv.put("groupedId", msg.grouped_id);
            cv.put("date", msg.date);
            cv.put("catchTime", catchTime);
            cv.put("data", data);
            db.insert("deleted_messages", null, cv);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Копирует файл вложения из кеша в постоянную папку DevGram. Возвращает путь копии
    // или null, если сохранять нечего/файла нет на диске.
    private static String saveMediaFile(int accountId, TLRPC.Message msg) {
        try {
            if (!DevGramConfig.saveMedia || msg == null || msg.media == null) {
                return null;
            }
            File src = FileLoader.getInstance(accountId).getPathToMessage(msg);
            if (src == null || !src.exists() || src.length() == 0) {
                return null;
            }
            File dir = new File(ApplicationLoader.getFilesDirFixed(), "devgram_saved");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File dst = new File(dir, Math.abs(msg.dialog_id) + "_" + msg.id + "_" + src.getName());
            if (!dst.exists() || dst.length() != src.length()) {
                if (!AndroidUtilities.copyFileSafe(src, dst)) {
                    return null;
                }
            }
            return dst.getAbsolutePath();
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Запись удалёнки вместе с датой удаления (catchTime) — для экрана удалёнок и «Деталей».
    public static class DeletedEntry {
        public TLRPC.Message message;
        public int catchTime; // когда поймали удаление
    }

    public List<DeletedEntry> getDeletedEntries(long userId, long dialogId, long topicId) {
        ArrayList<DeletedEntry> res = new ArrayList<>();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT data, catchTime FROM deleted_messages WHERE userId=? AND dialogId=? AND topicId=? ORDER BY messageId ASC",
                    new String[]{Long.toString(userId), Long.toString(dialogId), Long.toString(topicId)})) {
                while (c.moveToNext()) {
                    TLRPC.Message m = deserialize(c.getBlob(0));
                    if (m != null) {
                        DeletedEntry e = new DeletedEntry();
                        e.message = m;
                        e.catchTime = c.getInt(1);
                        res.add(e);
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    // Убрать одну удалёнку из хранилища (пункт «Удалить» в меню).
    public void deleteDeletedMessage(long userId, long dialogId, int messageId) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.delete("deleted_messages", "userId=? AND dialogId=? AND messageId=?",
                    new String[]{Long.toString(userId), Long.toString(dialogId), Integer.toString(messageId)});
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public List<TLRPC.Message> getDeletedMessages(long userId, long dialogId, long topicId) {
        ArrayList<TLRPC.Message> res = new ArrayList<>();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT data FROM deleted_messages WHERE userId=? AND dialogId=? AND topicId=? ORDER BY messageId ASC",
                    new String[]{Long.toString(userId), Long.toString(dialogId), Long.toString(topicId)})) {
                while (c.moveToNext()) {
                    TLRPC.Message m = deserialize(c.getBlob(0));
                    if (m != null) {
                        res.add(m);
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    // ================= история правок =================

    public void onMessageEdited(int accountId, TLRPC.Message oldMessage, long dialogId, long topicId, int messageId) {
        if (!DevGramConfig.saveMessagesHistory || oldMessage == null || skipDialog(accountId, dialogId)) {
            return;
        }
        DevGramMediaSaver.saveMessage(accountId, oldMessage);
        long userId = UserConfig.getInstance(accountId).getClientUserId();
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            // не дублируем, если последняя ревизия совпадает по editDate/date
            try (Cursor c = db.rawQuery(
                    "SELECT date FROM edited_messages WHERE userId=? AND dialogId=? AND messageId=? ORDER BY fakeId DESC LIMIT 1",
                    new String[]{Long.toString(userId), Long.toString(dialogId), Integer.toString(messageId)})) {
                if (c.moveToFirst() && c.getInt(0) == oldMessage.edit_date && oldMessage.edit_date != 0) {
                    return;
                }
            }
            byte[] data = serialize(oldMessage);
            if (data == null) {
                return;
            }
            ContentValues cv = new ContentValues();
            cv.put("userId", userId);
            cv.put("dialogId", dialogId);
            cv.put("topicId", topicId);
            cv.put("messageId", messageId);
            cv.put("date", oldMessage.edit_date != 0 ? oldMessage.edit_date : oldMessage.date);
            cv.put("catchTime", (int) (System.currentTimeMillis() / 1000));
            cv.put("data", data);
            db.insert("edited_messages", null, cv);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public boolean hasAnyRevisions(long userId, long dialogId, int messageId) {
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT 1 FROM edited_messages WHERE userId=? AND dialogId=? AND messageId=? LIMIT 1",
                    new String[]{Long.toString(userId), Long.toString(dialogId), Integer.toString(messageId)})) {
                return c.moveToFirst();
            }
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // Все ревизии (старые версии) сообщения по возрастанию времени.
    public List<TLRPC.Message> getRevisions(long userId, long dialogId, int messageId) {
        ArrayList<TLRPC.Message> res = new ArrayList<>();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT data FROM edited_messages WHERE userId=? AND dialogId=? AND messageId=? ORDER BY fakeId ASC",
                    new String[]{Long.toString(userId), Long.toString(dialogId), Integer.toString(messageId)})) {
                while (c.moveToNext()) {
                    TLRPC.Message m = deserialize(c.getBlob(0));
                    if (m != null) {
                        res.add(m);
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    // ================= разрешённые удаления =================
    // Если пользователь САМ удаляет сообщение (через меню), удаление «разрешается» и
    // проходит по-настоящему. Чужие (серверные) удаления не разрешены → остаются с пометкой.
    private final LongSparseArray<java.util.HashSet<Integer>> deletePermitted = new LongSparseArray<>();

    public static void permitDelete(long dialogId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        DevGramMessagesController c = getInstance();
        for (Integer id : ids) {
            if (id != null) {
                c.permitDeleteMessage(dialogId, id);
            }
        }
    }

    public void permitDeleteMessage(long dialogId, int messageId) {
        synchronized (deletePermitted) {
            java.util.HashSet<Integer> set = deletePermitted.get(dialogId);
            if (set == null) {
                set = new java.util.HashSet<>();
                deletePermitted.put(dialogId, set);
            }
            set.add(messageId);
        }
    }

    public boolean isDeletePermitted(long dialogId, int messageId) {
        synchronized (deletePermitted) {
            java.util.HashSet<Integer> set = deletePermitted.get(dialogId);
            return set != null && set.contains(messageId);
        }
    }

    public void consumeDeletePermit(long dialogId, int messageId) {
        synchronized (deletePermitted) {
            java.util.HashSet<Integer> set = deletePermitted.get(dialogId);
            if (set != null) {
                set.remove(messageId);
            }
        }
    }

    // ================= ответ на удалёнку =================
    // Связь «наше_отправленное_сообщение → удалёнка, на которую отвечаем».
    // Ключ — ФИНАЛЬНЫЙ серверный id нашего сообщения (стабилен при перезаходе).
    // Нужно, потому что серверу reply_to мы не шлём (иначе MESSAGE_ID_INVALID), а
    // локальный reply_to Telegram может затираться серверным ответом. Эта связь —
    // независимый источник правды: по ней на входе в чат восстанавливаем цитату.

    public void saveReplyToDeleted(long userId, long dialogId, int ownerMsgId, int replyToMsgId) {
        if (ownerMsgId == 0 || replyToMsgId == 0) {
            return;
        }
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("userId", userId);
            cv.put("dialogId", dialogId);
            cv.put("ownerMsgId", ownerMsgId);
            cv.put("replyToMsgId", replyToMsgId);
            db.insertWithOnConflict("reply_to_deleted", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Карта ownerMsgId → replyToMsgId для диалога (для восстановления цитат при загрузке).
    public android.util.SparseIntArray getReplyToDeletedLinks(long userId, long dialogId) {
        android.util.SparseIntArray res = new android.util.SparseIntArray();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT ownerMsgId, replyToMsgId FROM reply_to_deleted WHERE userId=? AND dialogId=?",
                    new String[]{Long.toString(userId), Long.toString(dialogId)})) {
                while (c.moveToNext()) {
                    res.put(c.getInt(0), c.getInt(1));
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    public void clean() {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("DELETE FROM deleted_messages");
            db.execSQL("DELETE FROM edited_messages");
            db.execSQL("DELETE FROM reply_to_deleted");
            DevGramMediaSaver.clear();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ================= схема =================

    private static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context context) {
            super(context, "devgram_messages.db", null, 3);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS deleted_messages (" +
                    "fakeId INTEGER PRIMARY KEY AUTOINCREMENT, userId INTEGER, dialogId INTEGER, topicId INTEGER, " +
                    "messageId INTEGER, groupedId INTEGER, date INTEGER, catchTime INTEGER, data BLOB)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_deleted ON deleted_messages (userId, dialogId, messageId)");
            db.execSQL("CREATE TABLE IF NOT EXISTS edited_messages (" +
                    "fakeId INTEGER PRIMARY KEY AUTOINCREMENT, userId INTEGER, dialogId INTEGER, topicId INTEGER, " +
                    "messageId INTEGER, date INTEGER, catchTime INTEGER, data BLOB)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_edited ON edited_messages (userId, dialogId, messageId)");
            createReplyTable(db);
        }

        private void createReplyTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS reply_to_deleted (" +
                    "userId INTEGER, dialogId INTEGER, ownerMsgId INTEGER, replyToMsgId INTEGER, " +
                    "PRIMARY KEY(userId, dialogId, ownerMsgId))");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Номер не понижаем: SQLiteOpenHelper падает на понижении версии.
            if (oldVersion < 3) {
                createReplyTable(db);
            }
        }
    }
}

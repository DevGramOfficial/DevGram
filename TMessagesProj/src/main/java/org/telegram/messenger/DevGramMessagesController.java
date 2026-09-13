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
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ConcurrentHashMap<String, Integer> lastSeenCache = new ConcurrentHashMap<>();

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
            if (msg.dialog_id == 0) msg.dialog_id = dialogId;
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
            db.execSQL("DELETE FROM message_read_times");
            db.execSQL("DELETE FROM content_read_times");
            db.execSQL("DELETE FROM local_last_seen");
            lastSeenCache.clear();
            DevGramMediaSaver.clear();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public String exportData() {
        JSONObject root = new JSONObject();
        try {
            root.put("version", 4);
            SQLiteDatabase db = helper.getReadableDatabase();
            root.put("deleted", exportMessages(db, "deleted_messages"));
            root.put("edited", exportMessages(db, "edited_messages"));
            root.put("reads", exportRows(db, "SELECT userId,dialogId,maxMessageId,date FROM message_read_times", 4));
            root.put("contentReads", exportRows(db, "SELECT userId,dialogId,messageId,date FROM content_read_times", 4));
            root.put("lastSeen", exportRows(db, "SELECT userId,peerId,date FROM local_last_seen", 3));
            root.put("replies", exportRows(db, "SELECT userId,dialogId,ownerMsgId,replyToMsgId FROM reply_to_deleted", 4));
        } catch (Throwable e) { FileLog.e(e); }
        return root.toString();
    }

    private static JSONArray exportMessages(SQLiteDatabase db, String table) throws Exception {
        JSONArray out = new JSONArray();
        try (Cursor c = db.rawQuery("SELECT userId,dialogId,topicId,messageId,date,catchTime,data,groupedId FROM " + table, null)) {
            while (c.moveToNext()) out.put(new JSONObject().put("u", c.getLong(0)).put("d", c.getLong(1))
                    .put("t", c.getLong(2)).put("m", c.getInt(3)).put("date", c.getInt(4))
                    .put("catch", c.getInt(5)).put("data", Base64.encodeToString(c.getBlob(6), Base64.NO_WRAP))
                    .put("g", c.getLong(7)));
        }
        return out;
    }

    private static JSONArray exportRows(SQLiteDatabase db, String sql, int columns) throws Exception {
        JSONArray out = new JSONArray();
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) { JSONArray row = new JSONArray(); for (int i = 0; i < columns; i++) row.put(c.getLong(i)); out.put(row); }
        }
        return out;
    }

    public boolean importData(String json) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            JSONObject root = new JSONObject(json);
            importMessages(db, "deleted_messages", root.optJSONArray("deleted"));
            importMessages(db, "edited_messages", root.optJSONArray("edited"));
            importRows(db, "message_read_times", new String[]{"userId","dialogId","maxMessageId","date"}, root.optJSONArray("reads"));
            importRows(db, "content_read_times", new String[]{"userId","dialogId","messageId","date"}, root.optJSONArray("contentReads"));
            importRows(db, "local_last_seen", new String[]{"userId","peerId","date"}, root.optJSONArray("lastSeen"));
            importRows(db, "reply_to_deleted", new String[]{"userId","dialogId","ownerMsgId","replyToMsgId"}, root.optJSONArray("replies"));
            db.setTransactionSuccessful();
            lastSeenCache.clear();
            return true;
        } catch (Throwable e) { FileLog.e(e); return false; }
        finally { db.endTransaction(); }
    }

    private static void importMessages(SQLiteDatabase db, String table, JSONArray rows) throws Exception {
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.getJSONObject(i); ContentValues v = new ContentValues();
            v.put("userId", o.getLong("u")); v.put("dialogId", o.getLong("d")); v.put("topicId", o.optLong("t"));
            v.put("messageId", o.getInt("m")); v.put("date", o.optInt("date")); v.put("catchTime", o.optInt("catch"));
            v.put("groupedId", o.optLong("g"));
            v.put("data", Base64.decode(o.getString("data"), Base64.DEFAULT));
            db.insertWithOnConflict(table, null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private static void importRows(SQLiteDatabase db, String table, String[] columns, JSONArray rows) throws Exception {
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.getJSONArray(i); ContentValues v = new ContentValues();
            for (int j = 0; j < columns.length; j++) v.put(columns[j], row.getLong(j));
            db.insertWithOnConflict(table, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    // ================= точное время прочтения / локальный онлайн =================

    public void saveMessageRead(int account, long dialogId, int maxMessageId, int date) {
        if (!DevGramConfig.saveReadDate || dialogId == 0 || maxMessageId == 0) return;
        try {
            ContentValues cv = new ContentValues();
            cv.put("userId", UserConfig.getInstance(account).getClientUserId());
            cv.put("dialogId", dialogId); cv.put("maxMessageId", maxMessageId);
            cv.put("date", date > 0 ? date : ConnectionsManager.getInstance(account).getCurrentTime());
            helper.getWritableDatabase().insert("message_read_times", null, cv);
        } catch (Throwable e) { FileLog.e(e); }
    }

    public void saveContentRead(int account, long dialogId, List<Integer> messageIds, int date) {
        if (!DevGramConfig.saveReadDate || messageIds == null) return;
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            long userId = UserConfig.getInstance(account).getClientUserId();
            int actualDate = date > 0 ? date : ConnectionsManager.getInstance(account).getCurrentTime();
            for (Integer id : messageIds) {
                ContentValues cv = new ContentValues(); cv.put("userId", userId); cv.put("dialogId", dialogId);
                cv.put("messageId", id); cv.put("date", actualDate);
                db.insertWithOnConflict("content_read_times", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } catch (Throwable e) { FileLog.e(e); }
    }

    public int getMessageReadDate(long userId, long dialogId, int messageId) {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT date FROM message_read_times WHERE userId=? AND dialogId=? AND maxMessageId>=? ORDER BY date ASC LIMIT 1",
                new String[]{Long.toString(userId), Long.toString(dialogId), Integer.toString(messageId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Throwable e) { FileLog.e(e); return 0; }
    }

    public int getContentReadDate(long userId, long dialogId, int messageId) {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT date FROM content_read_times WHERE userId=? AND (dialogId=? OR dialogId=0) AND messageId=? ORDER BY dialogId DESC LIMIT 1",
                new String[]{Long.toString(userId), Long.toString(dialogId), Integer.toString(messageId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Throwable e) { FileLog.e(e); return 0; }
    }

    public void saveLastSeen(int account, long peerId, int date) {
        if (!DevGramConfig.saveLocalOnline || peerId <= 0 || peerId == UserConfig.getInstance(account).getClientUserId()) return;
        try {
            int actualDate = date > 0 ? date : ConnectionsManager.getInstance(account).getCurrentTime();
            if (getLastSeen(account, peerId) >= actualDate) return;
            ContentValues cv = new ContentValues(); cv.put("userId", UserConfig.getInstance(account).getClientUserId());
            cv.put("peerId", peerId); cv.put("date", actualDate);
            helper.getWritableDatabase().insertWithOnConflict("local_last_seen", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            lastSeenCache.put(account + ":" + peerId, cv.getAsInteger("date"));
        } catch (Throwable e) { FileLog.e(e); }
    }

    public int getLastSeen(int account, long peerId) {
        String key = account + ":" + peerId;
        Integer cached = lastSeenCache.get(key);
        if (cached != null) return cached;
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT date FROM local_last_seen WHERE userId=? AND peerId=? LIMIT 1",
                new String[]{Long.toString(UserConfig.getInstance(account).getClientUserId()), Long.toString(peerId)})) {
            int value = c.moveToFirst() ? c.getInt(0) : 0;
            lastSeenCache.put(key, value);
            return value;
        } catch (Throwable e) { FileLog.e(e); return 0; }
    }

    // ================= схема =================

    private static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context context) {
            super(context, "devgram_messages.db", null, 4);
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
            createSpyTables(db);
        }

        private void createReplyTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS reply_to_deleted (" +
                    "userId INTEGER, dialogId INTEGER, ownerMsgId INTEGER, replyToMsgId INTEGER, " +
                    "PRIMARY KEY(userId, dialogId, ownerMsgId))");
        }

        private void createSpyTables(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS message_read_times (fakeId INTEGER PRIMARY KEY AUTOINCREMENT, userId INTEGER, dialogId INTEGER, maxMessageId INTEGER, date INTEGER)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_read ON message_read_times(userId, dialogId, maxMessageId)");
            db.execSQL("CREATE TABLE IF NOT EXISTS content_read_times (userId INTEGER, dialogId INTEGER, messageId INTEGER, date INTEGER, PRIMARY KEY(userId, dialogId, messageId))");
            db.execSQL("CREATE TABLE IF NOT EXISTS local_last_seen (userId INTEGER, peerId INTEGER, date INTEGER, PRIMARY KEY(userId, peerId))");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Номер не понижаем: SQLiteOpenHelper падает на понижении версии.
            if (oldVersion < 3) {
                createReplyTable(db);
            }
            if (oldVersion < 4) createSpyTables(db);
        }
    }
}

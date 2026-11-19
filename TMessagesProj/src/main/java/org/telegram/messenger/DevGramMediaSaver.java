/*
 * DevGram: сохранение вложений удалённых и отредактированных сообщений.
 *
 * Само сообщение мы храним в отдельной базе, но файл (фото, видео, кружок, голосовое,
 * стикер, гифка, документ) живёт в общем кеше Telegram и может быть вычищен. Поэтому
 * все файлы сообщения «закрепляются» в собственной папке рядом с кешем.
 *
 * Копия по возможности делается жёсткой ссылкой: файл на диске остаётся один и тот же,
 * место не тратится вообще, но удаление оригинала уже не уничтожает данные. Если файловая
 * система ссылок не умеет (например, внешняя карта с FAT) — честно копируем байты.
 *
 * Имена файлов совпадают с теми, что генерирует FileLoader.getAttachFileName(), поэтому
 * поиск сохранённой копии в FileLoader.getPathToAttach() — это просто проверка наличия
 * файла с тем же именем в нашей папке, без всяких таблиц соответствия.
 */

package org.telegram.messenger;

import android.system.Os;
import android.text.TextUtils;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class DevGramMediaSaver {

    private static volatile File dir;
    private static final Object fileLock = new Object();
    private static final DispatchQueue saveQueue = new DispatchQueue("devgramMediaSaver");
    private static final ConcurrentHashMap<String, PendingSave> pending = new ConcurrentHashMap<>();
    private static volatile long generation;

    public static File getDir() {
        if (dir == null) {
            synchronized (DevGramMediaSaver.class) {
                if (dir == null) {
                    File base = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE);
                    File parent = base != null ? base.getParentFile() : null;
                    if (parent == null) {
                        parent = ApplicationLoader.getFilesDirFixed();
                    }
                    File d = new File(parent, DevGramConfig.savedMediaFolder());
                    try {
                        if (!d.exists() && d.mkdirs()) {
                            new File(d, ".nomedia").createNewFile();
                        }
                    } catch (Throwable ignore) {
                    }
                    dir = d;
                }
            }
        }
        return dir;
    }

    public static void resetDirectory() {
        synchronized (DevGramMediaSaver.class) {
            dir = null;
        }
    }

    public static void changeDirectory(String folderName) {
        synchronized (fileLock) {
            changeDirectoryInternal(folderName);
        }
    }

    private static void changeDirectoryInternal(String folderName) {
        File oldDir = getDir();
        File[] oldFiles = oldDir != null ? oldDir.listFiles() : null;
        DevGramConfig.setSavedMediaFolder(folderName);
        resetDirectory();
        File newDir = getDir();
        if (oldDir == null || oldDir.equals(newDir) || oldFiles == null) return;
        for (File oldFile : oldFiles) {
            if (".nomedia".equals(oldFile.getName())) continue;
            File target = new File(newDir, oldFile.getName());
            if (target.exists() || oldFile.renameTo(target)) continue;
            copy(oldFile, target);
            if (target.exists() && target.length() == oldFile.length()) {
                oldFile.delete();
            }
        }
    }

    // Сохранённая копия по имени файла из FileLoader.getAttachFileName().
    public static File getSaved(String fileName) {
        if (TextUtils.isEmpty(fileName) || !new File(fileName).getName().equals(fileName)) {
            return null;
        }
        try {
            File f = new File(getDir(), fileName);
            return f.exists() && f.length() > 0 ? f : null;
        } catch (Throwable e) {
            return null;
        }
    }

    // Задержки повторных попыток: если файл на момент удаления ещё не был скачан, мы просим
    // его догрузить и возвращаемся позже. Ссылка на файл после удаления живёт недолго,
    // так что смысл имеют только ближайшие попытки.
    private static final long[] RETRY_DELAYS = {5000, 20000, 60000};

    // Закрепить все файлы сообщения. Вызывать с фонового потока — здесь дисковые операции.
    public static void saveMessage(int account, TLRPC.Message message) {
        if (!DevGramConfig.saveMedia || message == null || !allowedForDialog(account, message)) {
            return;
        }
        try {
            ArrayList<Attachment> attaches = new ArrayList<>();
            collect(message, attaches);
            long currentGeneration = generation;
            for (int i = 0; i < attaches.size(); i++) {
                keep(account, attaches.get(i), message, 0, currentGeneration);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Called on the storage queue before expiry. No speculative downloads: only
    // complete files already available for the user's viewing can be retained.
    static File saveSelfDestructingMessage(int account, TLRPC.Message message) {
        synchronized (fileLock) {
            if (!DevGramConfig.saveMedia || !allowedForDialog(account, message)) return null;
            ArrayList<Attachment> attachments = new ArrayList<>();
            collect(message, attachments);
            TLRPC.MessageMedia media = MessageObject.getMedia(message);
            File primary = null;
            for (Attachment attachment : attachments) {
                TLObject object = attachment.object;
                String name = FileLoader.getAttachFileName(object);
                if (TextUtils.isEmpty(name)) continue;
                File destination = new File(getDir(), name);
                long expected = expectedSize(object);
                if (!complete(destination, expected)) {
                    File source = FileLoader.getInstance(account).getPathToAttach(object, null, true, true);
                    if (!complete(source, expected)) {
                        source = FileLoader.getInstance(account).getPathToAttach(object, null, false, true);
                    }
                    if (!complete(source, expected) && object == media.document && !TextUtils.isEmpty(message.attachPath)) {
                        source = new File(message.attachPath);
                    }
                    if (complete(source, expected)) {
                        pin(source, destination, expected);
                    } else {
                        File cache = FileLoader.getInstance(account).getPathToAttach(object, null, true, true);
                        if (cache != null) {
                            File encrypted = new File(cache.getAbsolutePath() + ".enc");
                            File key = new File(FileLoader.getInternalCacheDir(), cache.getName() + ".enc.key");
                            long limit = DevGramConfig.mediaCacheLimit();
                            if (complete(encrypted, expected) && (limit <= 0 || encrypted.length() <= limit)) {
                                try {
                                    trimFor(encrypted.length());
                                    DevGramEncryptedMediaCopy.copy(encrypted, key, destination, expected);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        }
                    }
                }
                if (complete(destination, expected)) {
                    if (media.document != null && !(media.document instanceof TLRPC.TL_documentEmpty)) {
                        if (object == media.document) primary = destination;
                    } else if (attachment.owner == media.photo && object instanceof TLRPC.PhotoSize
                            && (primary == null || primary.length() < destination.length())) {
                        primary = destination;
                    }
                }
            }
            // Only advertise photo sizes that really survived. Otherwise the
            // decoder can select an unavailable larger size after returning to chat.
            if (primary != null && media.photo != null) {
                for (int i = media.photo.sizes.size() - 1; i >= 0; i--) {
                    TLRPC.PhotoSize size = media.photo.sizes.get(i);
                    if (!isInlinePreview(size) && getSaved(FileLoader.getAttachFileName(size)) == null) {
                        media.photo.sizes.remove(i);
                    }
                }
            }
            return primary != null && primary.isFile() ? primary : null;
        }
    }

    private static boolean allowedForDialog(int account, TLRPC.Message message) {
        long did = message.dialog_id != 0 ? message.dialog_id : MessageObject.getDialogId(message);
        if (did > 0) return DevGramConfig.saveMediaPrivateChats();
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-did);
        if (chat == null) return true;
        boolean publicChat = !TextUtils.isEmpty(chat.username) || chat.usernames != null && !chat.usernames.isEmpty();
        if (chat.megagroup || !ChatObject.isChannel(chat)) {
            return publicChat ? DevGramConfig.saveMediaPublicGroups() : DevGramConfig.saveMediaPrivateGroups();
        }
        return publicChat ? DevGramConfig.saveMediaPublicChannels() : DevGramConfig.saveMediaPrivateChannels();
    }

    // ================= сбор вложений =================

    // Размер фотографии сам по себе не позволяет запросить докачку — нужен владелец,
    // поэтому носим его рядом.
    private static class Attachment {
        final TLObject object;
        final TLRPC.Photo owner;

        Attachment(TLObject object, TLRPC.Photo owner) {
            this.object = object;
            this.owner = owner;
        }
    }

    private static class PendingSave {
        final Attachment attachment;
        final TLRPC.Message parent;
        final long generation;

        PendingSave(Attachment attachment, TLRPC.Message parent, long generation) {
            this.attachment = attachment;
            this.parent = parent;
            this.generation = generation;
        }
    }

    // The download may finish after the short recovery retries have expired, or
    // under a custom filename. Pin the actual completed file, off the loader queue.
    public static void onFileLoaded(int account, String fileName, File file) {
        String key = account + ":" + fileName;
        PendingSave request = pending.remove(key);
        if (request == null) return;
        saveQueue.postRunnable(() -> {
            synchronized (fileLock) {
                if (canKeep(account, request.parent, request.generation)) {
                    pin(file, new File(getDir(), fileName), expectedSize(request.attachment.object));
                }
            }
        });
    }

    private static void collect(TLRPC.Message message, ArrayList<Attachment> out) {
        TLRPC.MessageMedia media = MessageObject.getMedia(message);
        if (media != null) {
            addPhoto(media.photo, out);
            addDocument(media.document, out);
            addPhoto(media.video_cover, out);
            for (int i = 0; i < media.alt_documents.size(); i++) {
                addDocument(media.alt_documents.get(i), out);
            }
            if (media.webpage != null) {
                addPhoto(media.webpage.photo, out);
                addDocument(media.webpage.document, out);
            }
            if (media.game != null) {
                addPhoto(media.game.photo, out);
                addDocument(media.game.document, out);
            }
        }
        if (message.action != null) {
            addPhoto(message.action.photo, out);
        }
    }

    private static void addPhoto(TLRPC.Photo photo, ArrayList<Attachment> out) {
        if (photo == null) {
            return;
        }
        // Сохраняем ВСЕ размеры: заранее не известно, какой из них попросит интерфейс.
        for (int i = 0; i < photo.sizes.size(); i++) {
            TLRPC.PhotoSize size = photo.sizes.get(i);
            if (isInlinePreview(size)) {
                continue; // это не файлы, а превью прямо внутри сообщения
            }
            out.add(new Attachment(size, photo));
        }
        for (int i = 0; i < photo.video_sizes.size(); i++) {
            TLRPC.VideoSize size = photo.video_sizes.get(i);
            if (size instanceof TLRPC.TL_videoSize) {
                out.add(new Attachment(size, photo));
            }
        }
    }

    private static void addDocument(TLRPC.Document document, ArrayList<Attachment> out) {
        if (document == null) {
            return;
        }
        out.add(new Attachment(document, null));
        for (int i = 0; i < document.thumbs.size(); i++) {
            TLRPC.PhotoSize size = document.thumbs.get(i);
            if (isInlinePreview(size)) {
                continue;
            }
            out.add(new Attachment(size, null));
        }
    }

    private static boolean isInlinePreview(TLRPC.PhotoSize size) {
        return size == null || size instanceof TLRPC.TL_photoSizeEmpty
                || size instanceof TLRPC.TL_photoStrippedSize || size instanceof TLRPC.TL_photoPathSize;
    }

    // ================= закрепление файла =================

    private static boolean canKeep(int account, TLRPC.Message parent, long requestGeneration) {
        return requestGeneration == generation && DevGramConfig.saveMedia
                && allowedForDialog(account, parent);
    }

    private static long expectedSize(TLObject attach) {
        if (attach instanceof TLRPC.Document) return ((TLRPC.Document) attach).size;
        if (attach instanceof TLRPC.PhotoSize) return ((TLRPC.PhotoSize) attach).size;
        if (attach instanceof TLRPC.VideoSize) return ((TLRPC.VideoSize) attach).size;
        return 0;
    }

    private static void keep(int account, Attachment attachment, TLRPC.Message parent, int attempt, long requestGeneration) {
        synchronized (fileLock) {
            keepInternal(account, attachment, parent, attempt, requestGeneration);
        }
    }

    private static void keepInternal(int account, Attachment attachment, TLRPC.Message parent, int attempt, long requestGeneration) {
        if (!canKeep(account, parent, requestGeneration)) return;
        TLObject attach = attachment.object;
        String name;
        try {
            name = FileLoader.getAttachFileName(attach);
        } catch (Throwable e) {
            return;
        }
        if (TextUtils.isEmpty(name)) {
            return;
        }
        File dst = new File(getDir(), name);
        long expected = expectedSize(attach);
        if (complete(dst, expected)) {
            pending.remove(account + ":" + name);
            return; // уже закреплено
        }
        File src = existing(FileLoader.getInstance(account).getPathToAttach(attach, null, false, true));
        if (!complete(src, expected)) {
            // ttl-медиа и часть превью лежат только в кеше
            src = existing(FileLoader.getInstance(account).getPathToAttach(attach, null, true, true));
        }
        // Outgoing voice/video/documents can still live at their original local path.
        if (!complete(src, expected) && attach instanceof TLRPC.Document
                && MessageObject.getMedia(parent) != null && MessageObject.getMedia(parent).document == attach
                && !TextUtils.isEmpty(parent.attachPath)) {
            src = existing(new File(parent.attachPath));
        }
        if (!complete(src, expected)) {
            requestDownload(account, attachment, parent, attempt, requestGeneration);
            return;
        }
        pin(src, dst, expected);
        if (complete(dst, expected)) pending.remove(account + ":" + name);
    }

    private static boolean complete(File file, long expected) {
        return file != null && file.isFile() && file.length() > 0
                && (expected <= 0 || file.length() == expected);
    }

    private static void pin(File src, File dst, long expected) {
        if (!complete(src, expected) || complete(dst, expected) || src.equals(dst)) return;
        long folderLimit = DevGramConfig.mediaCacheLimit();
        if (folderLimit > 0 && src.length() > folderLimit) return;
        trimFor(src.length());
        // A partial destination must never masquerade as an available attachment.
        if (dst.exists() && !dst.delete()) return;
        if (!hardLink(src, dst)) {
            copy(src, dst);
        }
    }

    // Network caps control additional downloads, not files already on this device.
    private static boolean downloadAllowed(TLObject attach) {
        long networkLimit = ApplicationLoader.isConnectedToWiFi()
                ? DevGramConfig.mediaWifiLimit() : DevGramConfig.mediaCellularLimit();
        long size = expectedSize(attach);
        return networkLimit <= 0 || size > 0 && size <= networkLimit;
    }

    // Файла нет на диске — просим скачать и проверяем ещё раз чуть позже.
    private static void requestDownload(int account, Attachment attachment, TLRPC.Message parent, int attempt, long requestGeneration) {
        if (attempt >= RETRY_DELAYS.length || !downloadAllowed(attachment.object)) {
            return;
        }
        String key = account + ":" + FileLoader.getAttachFileName(attachment.object);
        PendingSave request = new PendingSave(attachment, parent, requestGeneration);
        if (pending.putIfAbsent(key, request) == null) {
            // Bound retained metadata even when Telegram cannot serve a deleted file.
            saveQueue.postRunnable(() -> pending.remove(key, request), 10 * 60 * 1000L);
        }
        try {
            TLObject attach = attachment.object;
            if (attach instanceof TLRPC.Document) {
                FileLoader.getInstance(account).loadFile((TLRPC.Document) attach, parent, FileLoader.PRIORITY_LOW, 1);
            } else if (attach instanceof TLRPC.PhotoSize && attachment.owner != null) {
                ImageLocation location = ImageLocation.getForPhoto((TLRPC.PhotoSize) attach, attachment.owner);
                if (location != null) {
                    FileLoader.getInstance(account).loadFile(location, parent, null, FileLoader.PRIORITY_LOW, 1);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        final int next = attempt + 1;
        saveQueue.postRunnable(() -> keep(account, attachment, parent, next, requestGeneration), RETRY_DELAYS[attempt]);
    }

    private static File existing(File f) {
        return f != null && f.exists() && f.length() > 0 ? f : null;
    }

    private static boolean hardLink(File src, File dst) {
        try {
            Os.link(src.getAbsolutePath(), dst.getAbsolutePath());
            return dst.exists() && dst.length() > 0;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void copy(File src, File dst) {
        File temp = null;
        try {
            temp = File.createTempFile(".retaining-", ".tmp", dst.getParentFile());
            long length = src.length();
            try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buf = new byte[64 * 1024];
                int read;
                while ((read = in.read(buf)) > 0) {
                    out.write(buf, 0, read);
                }
                out.getFD().sync();
            }
            if (temp.length() != length || !temp.renameTo(dst)) {
                throw new java.io.IOException("Could not retain complete media file");
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (temp != null) temp.delete();
        }
    }

    // ================= обслуживание =================

    public static long getSize() {
        long size = 0;
        try {
            File[] files = getDir().listFiles();
            if (files != null) {
                for (File f : files) {
                    size += f.length();
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return size;
    }

    public static void clear() {
        synchronized (fileLock) {
            generation++;
            pending.clear();
            try {
                File[] files = getDir().listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (!".nomedia".equals(f.getName())) {
                            f.delete();
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    private static void trimFor(long incoming) {
        long limit = DevGramConfig.mediaCacheLimit();
        if (limit == Long.MAX_VALUE || limit <= 0) return;
        File[] files = getDir().listFiles();
        if (files == null) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        long size = getSize();
        for (File f : files) {
            if (size + incoming <= limit) break;
            if (!".nomedia".equals(f.getName())) {
                long length = f.length();
                if (f.delete()) size -= length;
            }
        }
    }
}

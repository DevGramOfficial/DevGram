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

public class DevGramMediaSaver {

    private static volatile File dir;

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
        if (TextUtils.isEmpty(fileName)) {
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
            for (int i = 0; i < attaches.size(); i++) {
                keep(account, attaches.get(i), message, 0);
            }
        } catch (Throwable e) {
            FileLog.e(e);
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

    private static void keep(int account, Attachment attachment, TLRPC.Message parent, int attempt) {
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
        if (dst.exists() && dst.length() > 0) {
            return; // уже закреплено
        }
        File src = existing(FileLoader.getInstance(account).getPathToAttach(attach, null, false, true));
        if (src == null) {
            // ttl-медиа и часть превью лежат только в кеше
            src = existing(FileLoader.getInstance(account).getPathToAttach(attach, null, true, true));
        }
        if (src == null) {
            requestDownload(account, attachment, parent, attempt);
            return;
        }
        long networkLimit = ApplicationLoader.isConnectedToWiFi()
                ? DevGramConfig.mediaWifiLimit() : DevGramConfig.mediaCellularLimit();
        if (networkLimit > 0 && src.length() > networkLimit) return;
        if (src.equals(dst)) {
            return;
        }
        trimFor(src.length());
        if (!hardLink(src, dst)) {
            copy(src, dst);
        }
    }

    // Файла нет на диске — просим скачать и проверяем ещё раз чуть позже.
    private static void requestDownload(int account, Attachment attachment, TLRPC.Message parent, int attempt) {
        if (attempt >= RETRY_DELAYS.length) {
            return;
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
        Utilities.globalQueue.postRunnable(() -> keep(account, attachment, parent, next), RETRY_DELAYS[attempt]);
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
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[64 * 1024];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
        } catch (Throwable e) {
            FileLog.e(e);
            try {
                dst.delete(); // недокачанный огрызок хуже, чем ничего
            } catch (Throwable ignore) {
            }
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

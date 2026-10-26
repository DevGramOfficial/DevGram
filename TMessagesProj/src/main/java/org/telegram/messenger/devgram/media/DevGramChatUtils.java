package org.telegram.messenger.devgram.media;

import android.text.TextUtils;

import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;

/** Помощники для подробностей сообщения. */
public final class DevGramChatUtils {

    public static final DispatchQueue utilsQueue = new DispatchQueue("devgramUtils");

    private DevGramChatUtils() {
    }

    /** Владелец стикерпака по его ID (создатель набора). */
    public static long extractOwnerId(long id) {
        long owner = id >> 32;
        if (((id >> 16) & 255) == 63) {
            owner |= 2147483648L;
        }
        return ((id >> 24) & 255) != 0 ? owner + 4294967296L : owner;
    }

    public static String getDCName(int dc) {
        switch (dc) {
            case 1:
            case 3:
                return "Miami FL, USA";
            case 2:
            case 4:
                return "Amsterdam, NL";
            case 5:
                return "Singapore, SG";
            default:
                return null;
        }
    }

    private static boolean fileExists(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.isFile();
    }

    public static String getPathToMessage(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        FileLoader loader = FileLoader.getInstance(messageObject.currentAccount);
        if (messageObject.messageOwner != null && fileExists(messageObject.messageOwner.attachPath)) {
            return messageObject.messageOwner.attachPath;
        }
        if (messageObject.messageOwner != null) {
            String path = loader.getPathToMessage(messageObject.messageOwner).toString();
            if (fileExists(path)) {
                return path;
            }
        }
        if (messageObject.getDocument() != null) {
            String path = loader.getPathToAttach(messageObject.getDocument(), false).toString();
            if (fileExists(path)) {
                return path;
            }
            path = loader.getPathToAttach(messageObject.getDocument(), true).toString();
            if (fileExists(path)) {
                return path;
            }
        }
        return null;
    }

    /** Пользователь по ID среди уже известных клиенту. */
    public static void searchUserById(Long id, Utilities.Callback<TLRPC.User> callback) {
        TLRPC.User user = id == null || id == 0 ? null : MessagesController.getInstance(UserConfig.selectedAccount).getUser(id);
        callback.run(user);
    }
}

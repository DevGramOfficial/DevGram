package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.io.File;

/** Local retention policy for expiring media; Telegram's read receipts stay unchanged. */
public final class DevGramSelfDestructingMedia {
    public static boolean enabled(int account, long dialogId) {
        return dialogId != 0 && DevGramConfig.saveDeletedMessages && DevGramConfig.saveMedia
                && !DialogObject.isEncryptedDialog(dialogId)
                && !DevGramMessagesController.skipDialog(account, dialogId);
    }

    public static boolean candidate(TLRPC.Message message) {
        return message != null && message.media != null && message.media.ttl_seconds > 0
                && (message.media instanceof TLRPC.TL_messageMediaPhoto
                    || message.media instanceof TLRPC.TL_messageMediaDocument);
    }

    // This receives a deserialized copy, never the object being displayed by the viewer.
    static boolean retain(int account, TLRPC.Message message) {
        if (!candidate(message)) return false;
        long dialogId = message.dialog_id != 0 ? message.dialog_id : MessageObject.getDialogId(message);
        if (!enabled(account, dialogId)
                || DevGramMessagesController.getInstance().isDeletePermitted(dialogId, message.id)) return false;
        TLRPC.Message saved = DevGramMessagesController.getInstance().getDeletedMessage(
                UserConfig.getInstance(account).getClientUserId(), dialogId, message.id);
        if (restore(account, message, saved)) return true;
        File file = DevGramMediaSaver.saveSelfDestructingMessage(account, message);
        if (file == null) return false;
        int ttl = message.ttl, mediaTtl = message.media.ttl_seconds, flags = message.media.flags;
        int destroyTime = message.destroyTime;
        long destroyTimeMillis = message.destroyTimeMillis;
        boolean deleted = message.devgramDeleted;
        String attachPath = message.attachPath;
        clearExpiry(message);
        message.attachPath = file.getAbsolutePath();
        if (DevGramMessagesController.getInstance().onSelfDestructingMediaSaved(account, message, dialogId)) return true;
        message.ttl = ttl;
        message.media.ttl_seconds = mediaTtl;
        message.media.flags = flags;
        message.destroyTime = destroyTime;
        message.destroyTimeMillis = destroyTimeMillis;
        message.devgramDeleted = deleted;
        message.attachPath = attachPath;
        return false;
    }

    public static boolean restore(int account, TLRPC.Message target, TLRPC.Message saved) {
        if (target == null || saved == null || target.id != saved.id || !candidate(target)
                || !enabled(account, MessageObject.getDialogId(target))
                || DevGramMessagesController.getInstance().isDeletePermitted(MessageObject.getDialogId(target), target.id)
                || saved.media == null || saved.media.ttl_seconds != 0
                || saved.dialog_id != 0 && saved.dialog_id != MessageObject.getDialogId(target)) return false;
        File file = FileLoader.getInstance(account).getPathToMessage(saved);
        if (file == null || !file.isFile() || file.length() == 0) return false;
        target.media = saved.media;
        target.attachPath = file.getAbsolutePath();
        clearExpiry(target);
        return true;
    }

    private static void clearExpiry(TLRPC.Message message) {
        message.media.ttl_seconds = 0;
        message.media.flags &= ~4; // flags.2?int in both photo and document media.
        message.ttl = 0;
        message.destroyTime = 0;
        message.destroyTimeMillis = 0;
        message.devgramDeleted = true;
    }

    private DevGramSelfDestructingMedia() {}
}

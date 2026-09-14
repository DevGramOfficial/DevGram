package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

/** Re-sends protected/deleted messages as new messages instead of server-side forwarding. */
public final class DevGramSmartForward {
    private DevGramSmartForward() {}

    public static boolean needs(int account, ArrayList<MessageObject> messages) {
        if (messages == null) return false;
        for (MessageObject m : messages) if (m != null && m.messageOwner != null && (m.getId() <= 0 || m.messageOwner.noforwards
                || MessagesController.getInstance(account).isPeerNoForwards(m.getDialogId()))) return true;
        return false;
    }

    public static void resend(int account, ArrayList<MessageObject> messages, long target, boolean hideCaption,
                              boolean notify, int scheduleDate) {
        AccountInstance instance = AccountInstance.getInstance(account);
        for (MessageObject m : messages) {
            resendOne(instance, account, m, target, hideCaption, notify, scheduleDate, 30);
        }
    }

    private static void resendOne(AccountInstance instance, int account, MessageObject m, long target,
                                  boolean hideCaption, boolean notify, int scheduleDate, int downloadAttempts) {
        if (m == null || m.messageOwner == null) return;
        String caption = hideCaption || m.messageOwner.message == null ? "" : m.messageOwner.message;
        ArrayList<TLRPC.MessageEntity> entities = hideCaption ? null : m.messageOwner.entities;
        if (m.isMediaEmpty() || m.type == MessageObject.TYPE_TEXT) {
            instance.getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(
                    caption, target, null, null, m.messageOwner.media == null ? null : m.messageOwner.media.webpage,
                    true, entities, null, null, notify, scheduleDate, 0, null, false));
            return;
        }
        File file = localFile(account, m);
        if (file == null) {
            if (downloadAttempts > 0) {
                if (downloadAttempts == 30) requestDownload(account, m);
                AndroidUtilities.runOnUIThread(() -> resendOne(instance, account, m, target,
                        hideCaption, notify, scheduleDate, downloadAttempts - 1), 2000);
            } else {
                FileLog.e("DevGram smart forward: media is not available after download attempt");
            }
            return;
        }
        if (m.isPhoto() || m.isVideo()) {
            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
            info.path = file.getAbsolutePath(); info.caption = caption; info.entities = entities;
            info.isVideo = m.isVideo(); info.hasMediaSpoilers = m.hasMediaSpoilers();
            ArrayList<SendMessagesHelper.SendingMediaInfo> media = new ArrayList<>(); media.add(info);
            SendMessagesHelper.prepareSendingMedia(instance, media, target, null, null, null, null,
                    false, false, null, notify, scheduleDate, 0, 0, false, null,
                    SendMessageChatArguments.EMPTY, 0, false, 0, 0, null);
        } else {
            SendMessagesHelper.prepareSendingDocument(instance, file.getAbsolutePath(), file.getAbsolutePath(), null,
                    caption, m.getMimeType(), target, null, null, null, null, null, notify, scheduleDate,
                    null, SendMessageChatArguments.EMPTY, false);
        }
    }

    private static File localFile(int account, MessageObject m) {
        try {
            if (m.messageOwner.attachPath != null) {
                File f = new File(m.messageOwner.attachPath); if (f.exists() && f.length() > 0) return f;
            }
            File f = FileLoader.getInstance(account).getPathToMessage(m.messageOwner);
            if (f != null && f.exists() && f.length() > 0) return f;
            if (m.getDocument() != null) return DevGramMediaSaver.getSaved(FileLoader.getAttachFileName(m.getDocument()));
        } catch (Throwable e) { FileLog.e(e); }
        return null;
    }

    private static void requestDownload(int account, MessageObject m) {
        try {
            if (m.getDocument() != null) FileLoader.getInstance(account).loadFile(m.getDocument(), m.messageOwner, FileLoader.PRIORITY_NORMAL, 1);
            else if (m.messageOwner.media != null && m.messageOwner.media.photo != null) {
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(m.messageOwner.media.photo.sizes, AndroidUtilities.getPhotoSize());
                if (size != null) FileLoader.getInstance(account).loadFile(ImageLocation.getForPhoto(size, m.messageOwner.media.photo), m.messageOwner, null, FileLoader.PRIORITY_NORMAL, 1);
            }
        } catch (Throwable e) { FileLog.e(e); }
    }
}

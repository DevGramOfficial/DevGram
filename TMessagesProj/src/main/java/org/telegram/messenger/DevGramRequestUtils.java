package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;

import java.lang.reflect.Field;

/** Small, fail-safe request classifier used by Ghost Mode. */
public final class DevGramRequestUtils {
    private DevGramRequestUtils() {}

    public static boolean isReadMessage(TLObject object) {
        return object instanceof TLRPC.TL_messages_readHistory
                || object instanceof TLRPC.TL_messages_readEncryptedHistory
                || object instanceof TLRPC.TL_messages_readDiscussion
                || object instanceof TLRPC.TL_messages_readMessageContents
                || object instanceof TLRPC.TL_messages_readSavedHistory
                || object instanceof TLRPC.TL_channels_readHistory
                || object instanceof TLRPC.TL_channels_readMessageContents
                || object instanceof TLRPC.TL_messages_markDialogUnread;
    }

    public static boolean isReadStory(TLObject object) {
        return object instanceof TL_stories.TL_stories_readStories
                || object instanceof TL_stories.TL_stories_incrementStoryViews;
    }

    public static boolean isOutgoingAction(TLObject object) {
        return object instanceof TLRPC.TL_messages_sendMessage
                || object instanceof TLRPC.TL_messages_sendMedia
                || object instanceof TLRPC.TL_messages_sendMultiMedia
                || object instanceof TLRPC.TL_messages_forwardMessage
                || object instanceof TLRPC.TL_messages_forwardMessages
                || object instanceof TLRPC.TL_messages_sendInlineBotResult
                || object instanceof TLRPC.TL_messages_sendEncrypted
                || object instanceof TLRPC.TL_messages_sendEncryptedFile
                || object instanceof TLRPC.TL_messages_sendEncryptedMultiMedia
                || object instanceof TLRPC.TL_messages_sendEncryptedService
                || object instanceof TLRPC.TL_messages_sendReaction
                || object instanceof TLRPC.TL_messages_sendPaidReaction
                || object instanceof TLRPC.TL_messages_sendVote
                || object instanceof TL_stories.TL_stories_sendStory
                || object instanceof TL_stories.TL_stories_sendReaction;
    }

    public static long getDialogId(TLObject request) {
        Object peer = field(request, "peer");
        if (!(peer instanceof TLRPC.InputPeer)) peer = field(request, "to_peer");
        if (!(peer instanceof TLRPC.InputPeer)) peer = field(request, "channel");
        if (peer == null) peer = field(request, "parent_peer");
        if (peer instanceof TLRPC.InputPeer) {
            return getDialogId((TLRPC.InputPeer) peer);
        }
        if (peer instanceof TLRPC.InputChannel) {
            return -((TLRPC.InputChannel) peer).channel_id;
        }
        if (peer instanceof TLRPC.TL_inputEncryptedChat) {
            return DialogObject.makeEncryptedDialogId(((TLRPC.TL_inputEncryptedChat) peer).chat_id);
        }
        return 0;
    }

    public static int getMessageId(TLObject request) {
        Object value = field(request, "msg_id");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static long getDialogId(TLRPC.InputPeer peer) {
        if (peer == null) return 0;
        if (peer.user_id != 0) return peer.user_id;
        if (peer.chat_id != 0) return -peer.chat_id;
        if (peer.channel_id != 0) return -peer.channel_id;
        return 0;
    }

    private static Object field(Object object, String name) {
        if (object == null) return null;
        try {
            Field f = object.getClass().getField(name);
            return f.get(object);
        } catch (Throwable ignore) {
            return null;
        }
    }
}

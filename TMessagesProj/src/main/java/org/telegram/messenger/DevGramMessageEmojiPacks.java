package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.ArrayList;

/** Эмодзи-паки сообщения помимо текста: кастомные реакции и иконки инлайн-кнопок. */
public final class DevGramMessageEmojiPacks {

    private DevGramMessageEmojiPacks() {
    }

    public static ArrayList<TLRPC.InputStickerSet> collect(int account, MessageObject messageObject) {
        ArrayList<TLRPC.InputStickerSet> sets = new ArrayList<>();
        if (messageObject == null || messageObject.messageOwner == null) {
            return sets;
        }
        TLRPC.TL_messageReactions reactions = messageObject.messageOwner.reactions;
        if (reactions != null) {
            for (TLRPC.ReactionCount count : reactions.results) {
                if (count.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                    add(sets, AnimatedEmojiDrawable.findStickerSet(account, ((TLRPC.TL_reactionCustomEmoji) count.reaction).document_id));
                }
            }
        }
        if (messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
            for (TL_keyboard.KeyboardInlineButtonRow row : ((TLRPC.TL_replyInlineMarkup) messageObject.messageOwner.reply_markup).rows) {
                for (TL_keyboard.KeyboardInlineButton button : row.buttons) {
                    if (button.style != null && button.style.icon != 0) {
                        add(sets, AnimatedEmojiDrawable.findStickerSet(account, button.style.icon));
                    }
                }
            }
        }
        return sets;
    }

    public static void addAll(ArrayList<TLRPC.InputStickerSet> to, ArrayList<TLRPC.InputStickerSet> from) {
        for (TLRPC.InputStickerSet set : from) {
            add(to, set);
        }
    }

    private static void add(ArrayList<TLRPC.InputStickerSet> sets, TLRPC.InputStickerSet set) {
        if (set == null) {
            return;
        }
        for (TLRPC.InputStickerSet existing : sets) {
            if (existing.id == set.id) {
                return;
            }
        }
        sets.add(set);
    }
}

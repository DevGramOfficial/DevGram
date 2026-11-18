/*
 * DevGram: экран истории изменений сообщения — в виде мини-чата (как в AyuGram).
 * Каждая сохранённая ревизия и текущая версия рисуются настоящими бабблами
 * (ChatMessageCell) на фоне-обоях, снизу вверх, как реальная переписка.
 * Данные — из DevGramMessagesController (BLOB сериализованного TLRPC.Message).
 */

package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramMessagesController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.List;

public class DevGramMessageHistoryActivity extends BaseFragment {

    private final long dialogId;
    private final int messageId;
    private final TLRPC.Message currentMessage;
    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private RecyclerListView listView;

    public DevGramMessageHistoryActivity(long dialogId, int messageId, TLRPC.Message currentMessage) {
        this.dialogId = dialogId;
        this.messageId = messageId;
        this.currentMessage = currentMessage;
    }

    @Override
    public boolean onFragmentCreate() {
        loadMessages();
        return super.onFragmentCreate();
    }

    private void loadMessages() {
        long selfId = getUserConfig().getClientUserId();
        List<TLRPC.Message> revisions = DevGramMessagesController.getInstance().getRevisions(selfId, dialogId, messageId);

        for (TLRPC.Message rev : revisions) {
            if (rev == null) {
                continue;
            }
            try {
                messages.add(new MessageObject(currentAccount, rev, true, true));
            } catch (Throwable ignore) {
            }
        }

        // текущая (действующая) версия — как последнее сообщение. Берём весь TLRPC.Message
        // (с entities), чтобы кастом/премиум-эмодзи отрисовались, а не превращались в текст.
        if (currentMessage != null) {
            try {
                messages.add(new MessageObject(currentAccount, cloneMessage(currentMessage), true, true));
            } catch (Throwable ignore) {
            }
        }
    }

    // Клон сообщения (serialize/deserialize) — чтобы не держать ссылку на «живой» объект чата.
    private static TLRPC.Message cloneMessage(TLRPC.Message m) {
        try {
            org.telegram.tgnet.NativeByteBuffer buf = new org.telegram.tgnet.NativeByteBuffer(m.getObjectSize());
            m.serializeToStream(buf);
            buf.position(0);
            TLRPC.Message c = TLRPC.Message.TLdeserialize(buf, buf.readInt32(false), false);
            buf.reuse();
            return c != null ? c : m;
        } catch (Throwable e) {
            return m;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(DevGramLocale.text("История изменений"));
        actionBar.setSubtitle(messages.size() + " " + pluralVersions(messages.size()));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        // isActionBarVisible()/useRootView() → false: actionBar лежит НАД contentView, поэтому
        // обои не надо повторно сдвигать/клипать на высоту экшн-бара (иначе чёрная полоса сверху).
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }

            @Override
            protected boolean useRootView() {
                return false;
            }
        };
        contentView.setOccupyStatusBar(false);
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());

        listView = new RecyclerListView(context);
        // как в чате: версии прижаты книзу, свежая (текущая) — снизу, вверх листаем к старым
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(new Adapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        listView.setClipToPadding(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        fragmentView = contentView;
        return fragmentView;
    }

    private String pluralVersions(int n) {
        if (!DevGramLocale.isRussian()) return n == 1 ? DevGramLocale.text("версия") : DevGramLocale.text("версий");
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return DevGramLocale.text("версия");
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return DevGramLocale.text("версии");
        return DevGramLocale.text("версий");
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ChatMessageCell cell = new ChatMessageCell(parent.getContext(), currentAccount);
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            });
            cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            MessageObject message = messages.get(position);
            // как в AyuGram: без аватарок — все ревизии от одного отправителя
            cell.isChat = false;
            cell.setFullyDraw(true);
            cell.setMessageObject(message, null, false, false, position == 0);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }
}

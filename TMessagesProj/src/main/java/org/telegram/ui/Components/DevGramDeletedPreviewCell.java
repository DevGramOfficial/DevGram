package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;

/**
 * DevGram: живое превью «пометки удалённого сообщения» (порт DeletedMessagePreviewCell из AyuGram, GPL).
 * Показывает мок-сообщение с флагом devgramDeleted=true на фоне обоев чата — метка (значок+цвет) и
 * полупрозрачность рисуются самим ChatMessageCell по конфигу. refresh() пересобирает при смене настроек.
 */
@SuppressLint("ViewConstructor")
public class DevGramDeletedPreviewCell extends LinearLayout {

    private final ChatMessageCell cell;
    private MessageObject messageObject;
    private final Drawable shadowDrawable;
    private Drawable backgroundDrawable;

    public DevGramDeletedPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setOrientation(VERTICAL);
        setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11));
        shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);

        cell = new ChatMessageCell(context, UserConfig.selectedAccount);
        cell.isChat = true;
        cell.setFullyDraw(true);
        addView(cell, LayoutHelper.createLinear(-1, -2));

        buildMessage();
        cell.setMessageObject(messageObject, null, false, false, false);
    }

    private void buildMessage() {
        int now = (int) (System.currentTimeMillis() / 1000);
        TLRPC.TL_message m = new TLRPC.TL_message();
        m.message = DevGramLocale.text("Это сообщение удалили, но оно осталось у вас 👻");
        m.date = now - 3540;
        m.dialog_id = 1L;
        m.id = 1;
        TLRPC.TL_peerUser from = new TLRPC.TL_peerUser();
        from.user_id = 1L;
        m.from_id = from;
        m.media = new TLRPC.TL_messageMediaEmpty();
        m.out = false;
        TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
        peer.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        m.peer_id = peer;
        m.flags |= 256;
        m.devgramDeleted = true;

        messageObject = new MessageObject(UserConfig.selectedAccount, m, true, false);
        messageObject.forceAvatar = true;
        messageObject.resetLayout();
        messageObject.eventId = 1L;
    }

    /** Пересобрать сообщение и перерисовать (после смены значка/цвета/прозрачности).
     * Строим СВЕЖИЙ MessageObject — иначе ChatMessageCell не перегенерирует лэйаут строки
     * времени (значок/цвет применялись бы только после переоткрытия экрана). */
    public void refresh() {
        buildMessage();
        cell.setMessageObject(messageObject, null, false, false, false);
        cell.requestLayout();
        cell.invalidate();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable wp = Theme.getCachedWallpaperNonBlocking();
        if (wp == null) wp = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
        backgroundDrawable = wp;
        if (backgroundDrawable instanceof ColorDrawable || backgroundDrawable instanceof GradientDrawable) {
            backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            backgroundDrawable.draw(canvas);
        } else if (backgroundDrawable instanceof BitmapDrawable) {
            BitmapDrawable bd = (BitmapDrawable) backgroundDrawable;
            int mh = getMeasuredHeight();
            float scale = Math.max(getMeasuredWidth() / (float) bd.getIntrinsicWidth(), mh / (float) bd.getIntrinsicHeight());
            int w = (int) Math.ceil(bd.getIntrinsicWidth() * scale);
            int h = (int) Math.ceil(bd.getIntrinsicHeight() * scale);
            int dx = (getMeasuredWidth() - w) / 2;
            int dy = (mh - h) / 2;
            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), mh);
            bd.setBounds(dx, dy, dx + w, dy + h);
            bd.draw(canvas);
            canvas.restore();
        }
        shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        shadowDrawable.draw(canvas);
    }
}

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью «Список чатов» (как ChatListPreviewCell в exteraGram) — макет верхней панели
// диалогов: заголовок + реальный эмодзи-статус пользователя + меню ⋮. Отражает «Заголовок по центру».
public class DevGramChatListPreviewCell extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dots = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final int currentAccount = UserConfig.selectedAccount;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusDrawable;
    private final boolean hasStatus;

    public DevGramChatListPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setTextSize(AndroidUtilities.dp(17));

        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        Long emojiStatusId = user != null ? UserObject.getEmojiStatusDocumentId(user) : null;
        boolean premium = user != null && MessagesController.getInstance(currentAccount).isPremiumUser(user);
        hasStatus = emojiStatusId != null || premium;

        if (hasStatus) {
            statusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, AndroidUtilities.dp(20));
            if (emojiStatusId != null) {
                statusDrawable.set(emojiStatusId, false);
                statusDrawable.setParticles(user.emoji_status instanceof TLRPC.TL_emojiStatusCollectible, false);
            } else {
                Drawable star = getResources().getDrawable(R.drawable.msg_premium_liststar).mutate();
                star.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_verifiedBackground), PorterDuff.Mode.MULTIPLY));
                statusDrawable.set(star, false);
            }
            statusDrawable.setColor(Theme.getColor(Theme.key_profile_verifiedBackground));
        }
    }

    public boolean userHasStatus() {
        return hasStatus;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (statusDrawable != null) statusDrawable.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (statusDrawable != null) statusDrawable.detach();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(72), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int grey = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int gr = Color.red(grey), gg = Color.green(grey), gb = Color.blue(grey);
        float w = getMeasuredWidth();
        float cy = getMeasuredHeight() / 2f;
        boolean rtl = LocaleController.isRTL;

        // карточка
        paint.setColor(Color.argb(20, gr, gg, gb));
        rect.set(AndroidUtilities.dp(14), AndroidUtilities.dp(10), w - AndroidUtilities.dp(14),
                getMeasuredHeight() - AndroidUtilities.dp(10));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

        float sidePad = AndroidUtilities.dp(30);

        // меню ⋮ у правого края
        dots.setColor(Theme.getColor(Theme.key_actionBarDefaultIcon));
        float dotX = rtl ? sidePad : w - sidePad;
        for (int i = -1; i <= 1; i++) {
            canvas.drawCircle(dotX, cy + i * AndroidUtilities.dp(6), AndroidUtilities.dp(2), dots);
        }

        // заголовок + реальный эмодзи-статус
        titlePaint.setColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        String title = "DevGram";
        float tw = titlePaint.measureText(title);
        boolean hide = MessagesController.getGlobalMainSettings().getBoolean("dg_hideStatus", false);
        boolean drawStatus = statusDrawable != null && !hide;
        float statusSize = drawStatus ? AndroidUtilities.dp(20) : 0;
        float statusGap = drawStatus ? AndroidUtilities.dp(6) : 0;
        float blockW = tw + statusGap + statusSize;

        float bx = org.telegram.messenger.DevGramIOSConfig.centerChatTitle() ? (w - blockW) / 2f
                : (rtl ? w - sidePad - blockW : sidePad);
        canvas.drawText(title, bx, cy + AndroidUtilities.dp(6), titlePaint);
        if (drawStatus) {
            float ex = bx + tw + statusGap;
            statusDrawable.setBounds((int) ex, (int) (cy - statusSize / 2f),
                    (int) (ex + statusSize), (int) (cy + statusSize / 2f));
            statusDrawable.draw(canvas);
        }
    }
}

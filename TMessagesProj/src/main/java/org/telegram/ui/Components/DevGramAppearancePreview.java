package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

// DevGram: живое превью оформления поддержавшего — кусочек чата на обоях: аватарка, ник со значком
// и сообщение в выбранной обводке. Меняется сразу при выборе, ещё до сохранения на сервере.
public class DevGramAppearancePreview extends View implements NotificationCenter.NotificationCenterDelegate {

    private final DevGramMessageFrame.Cache shaderCache = new DevGramMessageFrame.Cache();

    private final int account;
    private final Theme.ResourcesProvider resourcesProvider;
    private final ImageReceiver avatarImage = new ImageReceiver(this);
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF card = new RectF();
    private final RectF bubble = new RectF();
    private final Path clip = new Path();
    private final String name;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable badge;
    private long badgeId;
    private int frameStyle;
    private StaticLayout textLayout;
    private int textLayoutWidth;

    public void setDocument(org.json.JSONObject document) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        org.json.JSONObject effective = org.telegram.messenger.DevGramProfileDocument.effective(document, uid);
        textPaint.setTypeface(org.telegram.messenger.DevGramProfileDocument.messageFont(effective));
        setFrame(DevGramMessageFrame.fromDocument(effective, effective.optInt("messageFrame", DevGramBadges.borderOf(uid))));
        DevGramBadges.Badge actual = DevGramBadges.badgeOf(uid); setBadge(actual == null ? 0 : DevGramBadges.emojiIdOf(uid));
    }

    private static String getSample() {
        return DevGramLocale.text("Привет! Так выглядит моё сообщение 👋");
    }

    public DevGramAppearancePreview(Context context, int account, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.account = account;
        this.resourcesProvider = resourcesProvider;
        TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
        name = me != null ? UserObject.getUserName(me) : DevGramLocale.text("Вы");
        avatarDrawable.setInfo(account, me);
        avatarImage.setRoundRadius(AndroidUtilities.dp(18));
        avatarImage.setForUserOrChat(me, avatarDrawable);
        namePaint.setTextSize(AndroidUtilities.dp(14));
        namePaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(AndroidUtilities.dp(15));
        timePaint.setTextSize(AndroidUtilities.dp(11));
        textPaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn, resourcesProvider));
        long uid = UserConfig.getInstance(account).getClientUserId();
        DevGramBadges.Badge b = DevGramBadges.badgeOf(uid);
        setBadge(b != null ? DevGramBadges.emojiIdOf(uid) : 0);
        setFrame(DevGramBadges.borderOf(uid));
    }

    public void setBadge(long emojiId) {
        if (badgeId == emojiId && badge != null) return;
        if (badge != null && isAttachedToWindow()) badge.detach();
        badgeId = emojiId;
        if (emojiId != 0) {
            badge = new DevGramBadgeEmojiDrawable(this, AndroidUtilities.dp(18), AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, () -> textPaint.getColor());
            badge.setCurrentAccount(account);
            badge.set(emojiId, false);
        } else {
            badge = null;
        }
        if (badge != null && isAttachedToWindow()) badge.attach();
        invalidate();
    }

    public void setFrame(int styleId) {
        frameStyle = styleId;
        textLayout = null; // цвет текста зависит от стиля
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(176));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float pad = AndroidUtilities.dp(14);
        card.set(pad, AndroidUtilities.dp(10), getWidth() - pad, getHeight() - AndroidUtilities.dp(6));
        float r = AndroidUtilities.dp(20);
        clip.rewind();
        clip.addRoundRect(card, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        Drawable wallpaper = Theme.getCachedWallpaperNonBlocking();
        if (wallpaper != null) {
            wallpaper.setBounds((int) card.left, (int) card.top - AndroidUtilities.dp(60), (int) card.right, (int) card.bottom + AndroidUtilities.dp(60));
            wallpaper.draw(canvas);
        } else {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        }

        // аватарка
        float avatarSize = AndroidUtilities.dp(36);
        float avatarX = card.left + AndroidUtilities.dp(12), avatarY = card.bottom - AndroidUtilities.dp(18) - avatarSize;
        avatarImage.setImageCoords(avatarX, avatarY, avatarSize, avatarSize);
        avatarImage.draw(canvas);

        // пузырь
        DevGramMessageFrame.Style st = DevGramMessageFrame.get(frameStyle);
        float left = avatarX + avatarSize + AndroidUtilities.dp(10);
        float maxW = card.right - left - AndroidUtilities.dp(40);
        int textW = (int) Math.min(maxW - AndroidUtilities.dp(24), textPaint.measureText(getSample()));
        textPaint.setColor(st != null && st.text != 0 ? st.text : Theme.getColor(Theme.key_chat_messageTextIn, resourcesProvider));
        if (textLayout == null || textLayoutWidth != textW) {
            textLayoutWidth = textW;
            CharSequence text = org.telegram.messenger.Emoji.replaceEmoji(getSample(), textPaint.getFontMetricsInt(), false);
            textLayout = new StaticLayout(text, textPaint, Math.max(1, textW), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }
        float nameH = AndroidUtilities.dp(20);
        float bubbleH = AndroidUtilities.dp(12) + nameH + textLayout.getHeight() + AndroidUtilities.dp(18);
        float nameW = namePaint.measureText(name) + AndroidUtilities.dp(26);
        float bubbleW = Math.max(textLayout.getWidth(), nameW) + AndroidUtilities.dp(24);
        bubble.set(left, avatarY + avatarSize - bubbleH, left + bubbleW, avatarY + avatarSize);
        float br = AndroidUtilities.dp(16);
        Paint fill = st != null ? DevGramMessageFrame.fillPaint(shaderCache, frameStyle, bubble.left, bubble.top, bubble.right, bubble.bottom, 1f) : null;
        if (fill == null) {
            bubblePaint.setColor(Theme.getColor(Theme.key_chat_inBubble, resourcesProvider));
            fill = bubblePaint;
        }
        canvas.drawRoundRect(bubble, br, br, fill);
        if (st != null) DevGramMessageFrame.draw(canvas, bubble, br, shaderCache, frameStyle, 1f);

        // ник со значком
        float x = bubble.left + AndroidUtilities.dp(12), y = bubble.top + AndroidUtilities.dp(10);
        namePaint.setColor(Theme.getColor(Theme.key_avatar_nameInMessageBlue, resourcesProvider));
        String shownName = TextUtils.ellipsize(name, namePaint, maxW - AndroidUtilities.dp(40), TextUtils.TruncateAt.END).toString();
        // значок — слева от ника, как в DevGram
        float nameX = x;
        if (badge != null) {
            int s = AndroidUtilities.dp(18);
            badge.setBounds((int) x, (int) (y + AndroidUtilities.dp(1)), (int) x + s, (int) (y + AndroidUtilities.dp(1)) + s);
            badge.draw(canvas);
            nameX = x + s + AndroidUtilities.dp(4);
        }
        canvas.drawText(shownName, nameX, y + AndroidUtilities.dp(14), namePaint);
        // текст и время
        canvas.save();
        canvas.translate(x, y + nameH + AndroidUtilities.dp(2));
        textLayout.draw(canvas);
        canvas.restore();
        timePaint.setColor(st != null && st.text != 0 ? (st.text & 0x00ffffff) | 0x99000000 : Theme.getColor(Theme.key_chat_inTimeText, resourcesProvider));
        String time = "12:34";
        canvas.drawText(time, bubble.right - AndroidUtilities.dp(10) - timePaint.measureText(time), bubble.bottom - AndroidUtilities.dp(7), timePaint);
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
        if (badge != null) badge.attach();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarImage.onDetachedFromWindow();
        if (badge != null) badge.detach();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        textLayout = null;
        invalidate();
    }
}

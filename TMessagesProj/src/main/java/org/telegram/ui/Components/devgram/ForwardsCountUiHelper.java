package org.telegram.ui.Components.devgram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Cells.ChatMessageCell;

/** Счётчик пересылок рядом с просмотрами в пузыре сообщения канала. */
public final class ForwardsCountUiHelper {

    public static boolean enabled() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_showForwardsCount", false);
    }

    // своя иконка на обычное и на выделенное состояние: общий colorFilter при подсветке
    // сообщения перекрашивал и обычную, и значок пропадал
    private final Drawable[] drawables = new Drawable[2];
    private final PorterDuffColorFilter[] colorFilters = new PorterDuffColorFilter[2];
    private final int[] colors = new int[2];

    private StaticLayout layout, lastLayout, animateLayout;
    private boolean animating;
    private float animateFromX, lastX;
    private float textSize;
    private Typeface typeface;
    private int count;
    private int width;
    private boolean allowed = true;

    private static int getCount(MessageObject messageObject, MessageObject.GroupedMessages group) {
        if (!enabled() || messageObject == null || messageObject.messageOwner == null
                || (messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) == 0
                || messageObject.scheduled || messageObject.notime || messageObject.isSponsored()
                || messageObject.isQuickReply()) {
            return 0;
        }
        int max = messageObject.messageOwner.forwards;
        // у альбома счётчик на последнем элементе — берём максимум по всем сообщениям группы
        if (group != null) {
            for (int i = 0; i < group.messages.size(); i++) {
                TLRPC.Message message = group.messages.get(i).messageOwner;
                if (message != null) {
                    max = Math.max(max, message.forwards);
                }
            }
        }
        return Math.max(0, max);
    }

    public boolean isChanged(MessageObject messageObject, MessageObject.GroupedMessages group) {
        return count != (allowed ? getCount(messageObject, group) : 0);
    }

    public int measure(Context context, MessageObject messageObject, MessageObject.GroupedMessages group, boolean allowed) {
        this.allowed = allowed;
        count = allowed ? getCount(messageObject, group) : 0;
        if (count == 0) {
            layout = null;
            width = 0;
            return 0;
        }
        if (drawables[0] == null) {
            for (int i = 0; i < drawables.length; i++) {
                drawables[i] = ContextCompat.getDrawable(context, R.drawable.msg_reply_small).mutate();
            }
        }
        Drawable drawable = drawables[0];
        TextPaint paint = Theme.chat_timePaint;
        String text = LocaleController.formatShortNumber(count, null);
        if (layout == null || !TextUtils.equals(layout.getText(), text) || textSize != paint.getTextSize() || typeface != paint.getTypeface()) {
            textSize = paint.getTextSize();
            typeface = paint.getTypeface();
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, (int) Math.ceil(paint.measureText(text))))
                    .setIncludePad(false).build();
        }
        width = (int) Math.ceil(layout.getWidth() + drawable.getIntrinsicWidth() * paint.getTextSize() / drawable.getIntrinsicHeight() + AndroidUtilities.dp(10));
        return width;
    }

    public void recordDrawingState() {
        lastLayout = layout;
    }

    public boolean animateChange() {
        animateFromX = lastX;
        animating = layout != lastLayout;
        animateLayout = animating ? lastLayout : null;
        return animating;
    }

    public void resetAnimation() {
        animating = false;
        animateLayout = null;
    }

    /** @return ширина, на которую сдвинуть следующие элементы строки времени */
    public float draw(Canvas canvas, ChatMessageCell cell, float timeX, float timeY, float offsetX, float alpha, float progress, boolean selected) {
        if (layout == null && animateLayout == null) {
            return 0;
        }
        ChatMessageCell.TransitionParams tp = cell.transitionParams;
        float changeProgress = tp.animateChangeProgress;
        boolean inAnimation = animating && animateLayout == null;
        boolean outAnimation = animating && layout == null;
        float x = (tp.shouldAnimateTimeX ? cell.timeX : timeX) + offsetX;
        if (tp.shouldAnimateTimeX && !inAnimation && !(cell.getMessageObject().isRoundVideo() && tp.animateDrawBackground)) {
            x = outAnimation ? animateFromX : AndroidUtilities.lerp(animateFromX, x, changeProgress);
        }
        MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
        if (group != null && group.transitionParams.backgroundChangeBounds) {
            x += group.transitionParams.offsetRight;
        }
        if (tp.animateBackgroundBoundsInner) {
            x += cell.getAnimationOffsetX();
        }
        lastX = x;

        TextPaint paint = Theme.chat_timePaint;
        int oldAlpha = paint.getAlpha();
        int textAlpha = (int) (oldAlpha * progress);
        int colorKey;
        if (cell.shouldDrawTimeOnMedia()) {
            colorKey = cell.getMessageObject().shouldDrawWithoutBackground() ? Theme.key_chat_serviceText : Theme.key_chat_mediaViews;
        } else if (cell.getMessageObject().isOutOwner()) {
            colorKey = selected ? Theme.key_chat_outViewsSelected : Theme.key_chat_outViews;
        } else {
            colorKey = selected ? Theme.key_chat_inViewsSelected : Theme.key_chat_inViews;
        }
        int state = selected ? 1 : 0;
        int color = cell.getThemedColor(colorKey);
        if (colorFilters[state] == null || colors[state] != color) {
            colors[state] = color;
            colorFilters[state] = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
        Drawable drawable = drawables[state];
        drawable.setColorFilter(colorFilters[state]);
        float iconWidth = BaseCell.setDrawableBounds(drawable, x, timeY, paint.getTextSize());

        canvas.save();
        if (progress != 1f) {
            float scale = .5f + .5f * progress;
            StaticLayout l = layout != null ? layout : animateLayout;
            canvas.scale(scale, scale, x + (iconWidth + AndroidUtilities.dp(3) + l.getWidth()) / 2f, drawable.getBounds().centerY());
        }
        float iconAlpha = inAnimation ? changeProgress : outAnimation ? 1f - changeProgress : 1f;
        drawable.setAlpha((int) (255 * alpha * iconAlpha));
        // стрелка «ответить», отражённая по горизонтали, — значок пересылки
        canvas.save();
        canvas.scale(-1f, 1f, drawable.getBounds().exactCenterX(), 0);
        drawable.draw(canvas);
        canvas.restore();
        canvas.translate(x + iconWidth + AndroidUtilities.dp(3), timeY);
        if (animateLayout != null) {
            paint.setAlpha((int) (textAlpha * (1f - changeProgress)));
            animateLayout.draw(canvas);
        }
        if (layout != null) {
            paint.setAlpha((int) (textAlpha * (animating ? changeProgress : 1f)));
            layout.draw(canvas);
        }
        canvas.restore();
        paint.setAlpha(oldAlpha);
        return width;
    }

    public void appendAccessibilityText(SpannableStringBuilder sb) {
        if (count > 0) {
            sb.append("\n").append(String.format(LocaleController.getPluralString("Shares", count), AndroidUtilities.formatCount(count)));
        }
    }
}

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.DevGramSwipeAction;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;
import java.util.List;

/** The same live chat preview used by Extera: real wallpaper, real message cell and swipe springs. */
public class DevGramSwipeActionsPreviewCell extends FrameLayout {
    private final ArrayList<DevGramSwipeAction> actions = new ArrayList<>();
    private final Theme.ResourcesProvider resourcesProvider;
    private final DevGramSwipeActionsHelper helper;
    private final ChatMessageCell messageCell;
    private final Drawable fallbackBackground;
    private final Drawable shadowDrawable;
    private Drawable backgroundDrawable;
    private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
    private ValueAnimator cycle;
    private List<DevGramSwipeAction> pending;
    private boolean looped;
    private int selected;
    private float slide;

    public DevGramSwipeActionsPreviewCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);
        fallbackBackground = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom,
                Theme.key_windowBackgroundGrayShadow);
        helper = new DevGramSwipeActionsHelper(this, resourcesProvider);

        int account = UserConfig.selectedAccount;
        int now = (int) (System.currentTimeMillis() / 1000L) - 3600;
        long me = UserConfig.getInstance(account).getClientUserId();

        TLRPC.TL_message message = new TLRPC.TL_message();
        message.message = "Проведите влево и выберите действие";
        message.date = now;
        message.dialog_id = 1L;
        message.flags = 256;
        message.from_id = peer(me);
        message.peer_id = peer(0);
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.id = 1;
        message.out = false;

        TLRPC.TL_message reply = new TLRPC.TL_message();
        reply.message = "Так выглядит выбор действия";
        reply.date = now;
        reply.dialog_id = 1L;
        reply.flags = 256;
        reply.from_id = peer(me);
        reply.peer_id = peer(1);
        reply.media = new TLRPC.TL_messageMediaEmpty();
        reply.id = 2;
        reply.out = true;

        TLRPC.TL_messageReplyHeader replyHeader = new TLRPC.TL_messageReplyHeader();
        replyHeader.flags |= 16;
        replyHeader.reply_to_msg_id = reply.id;
        message.reply_to = replyHeader;
        message.flags |= 8;

        MessageObject messageObject = new MessageObject(account, message, true, false);
        messageObject.replyMessageObject = new MessageObject(account, reply, true, false);
        messageObject.customReplyName = "Вы";
        messageObject.resetLayout();
        messageObject.eventId = 1L;

        messageCell = new ChatMessageCell(context, account);
        messageCell.isChat = false;
        messageCell.setFullyDraw(true);
        messageCell.setMessageObject(messageObject, null, false, false, false);
        addView(messageCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));
    }

    private static TLRPC.TL_peerUser peer(long id) {
        TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
        peer.user_id = id;
        return peer;
    }

    @Override
    public void dispatchSetPressed(boolean pressed) {
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    public void setActions(List<DevGramSwipeAction> value) {
        ArrayList<DevGramSwipeAction> next = new ArrayList<>();
        if (value != null) next.addAll(value);
        if (next.equals(actions) && looped == DevGramConfig.swipeActionsLoop) return;
        if (cycle == null || actions.isEmpty() || next.isEmpty()) {
            applyActions(next);
        } else {
            pending = next;
        }
    }

    public void updateActions() {
        setActions(DevGramSwipeAction.enabled());
    }

    private void applyActions(List<DevGramSwipeAction> value) {
        actions.clear();
        actions.addAll(value);
        looped = DevGramConfig.swipeActionsLoop;
        selected = 0;
        helper.start(actions);
        helper.select(0);
        updateReaction();
        restartCycle();
    }

    private void updateReaction() {
        if (!actions.contains(DevGramSwipeAction.REACTION)) return;
        String reaction = MediaDataController.getInstance(UserConfig.selectedAccount).getDoubleTapReaction();
        if (reaction == null) return;
        ReactionsLayoutInBubble.VisibleReaction visible = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(reaction);
        helper.setReaction(UserConfig.selectedAccount, visible.emojicon, visible.documentId);
    }

    private void restartCycle() {
        if (cycle != null) {
            cycle.cancel();
            cycle = null;
        }
        slide = 0f;
        messageCell.setSlidingOffset(0f);
        if (actions.isEmpty() || !isAttachedToWindow()) {
            invalidate();
            return;
        }
        int selectionSteps = actions.size() + (looped ? 1 : 0);
        cycle = ValueAnimator.ofFloat(0f, 1f);
        cycle.setDuration((selectionSteps + 2L) * 1100L);
        cycle.setInterpolator(new LinearInterpolator());
        cycle.setRepeatCount(ValueAnimator.INFINITE);
        cycle.addUpdateListener(animation -> updateCycle(selectionSteps, (float) animation.getAnimatedValue()));
        cycle.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                if (pending != null) {
                    ArrayList<DevGramSwipeAction> next = new ArrayList<>(pending);
                    pending = null;
                    AndroidUtilities.runOnUIThread(() -> applyActions(next));
                } else {
                    selected = 0;
                    helper.start(actions);
                    helper.select(0);
                    updateReaction();
                }
            }
        });
        cycle.start();
    }

    private void updateCycle(int selectionSteps, float progress) {
        float segment = 1f / (selectionSteps + 2f);
        if (progress < segment) {
            slide = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(progress / segment);
        } else if (progress > 1f - segment) {
            slide = 1f - CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(
                    (progress - (1f - segment)) / segment);
        } else {
            slide = 1f;
        }
        if (progress >= segment && progress <= 1f - segment) {
            int step = Math.max(0, Math.min(selectionSteps - 1,
                    (int) ((progress - segment) / segment)));
            int index = step % actions.size();
            if (index != selected) {
                selected = index;
                helper.select(index);
            }
        }
        messageCell.setSlidingOffset(-slide * AndroidUtilities.dp(72));
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (actions.isEmpty()) return;
        float offset = -slide * AndroidUtilities.dp(72);
        helper.draw(canvas, offset, false,
                messageCell.getTop() + messageCell.getMeasuredHeight() / 2f,
                messageCell.getBackgroundDrawableRight() + offset);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable wallpaper = Theme.getCachedWallpaperNonBlocking();
        if (wallpaper == null) wallpaper = fallbackBackground;
        if (wallpaper != backgroundDrawable) {
            if (backgroundGradientDisposable != null) {
                backgroundGradientDisposable.dispose();
                backgroundGradientDisposable = null;
            }
            backgroundDrawable = wallpaper;
        }
        backgroundDrawable.setAlpha(backgroundDrawable == fallbackBackground ? 150 : 255);
        drawWallpaper(canvas, backgroundDrawable);
        shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        shadowDrawable.draw(canvas);
    }

    private void drawWallpaper(Canvas canvas, Drawable wallpaper) {
        if (wallpaper instanceof ColorDrawable || wallpaper instanceof GradientDrawable
                || wallpaper instanceof MotionBackgroundDrawable) {
            wallpaper.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            if (wallpaper instanceof BackgroundGradientDrawable) {
                backgroundGradientDisposable = ((BackgroundGradientDrawable) wallpaper)
                        .drawExactBoundsSize(canvas, this);
            } else {
                wallpaper.draw(canvas);
            }
            return;
        }
        if (wallpaper instanceof BitmapDrawable) {
            BitmapDrawable bitmap = (BitmapDrawable) wallpaper;
            bitmap.setFilterBitmap(true);
            canvas.save();
            if (bitmap.getTileModeX() == Shader.TileMode.REPEAT) {
                float scale = 2f / AndroidUtilities.density;
                canvas.scale(scale, scale);
                wallpaper.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale),
                        (int) Math.ceil(getMeasuredHeight() / scale));
            } else {
                float scale = Math.max(getMeasuredWidth() / (float) wallpaper.getIntrinsicWidth(),
                        getMeasuredHeight() / (float) wallpaper.getIntrinsicHeight());
                int width = (int) Math.ceil(wallpaper.getIntrinsicWidth() * scale);
                int height = (int) Math.ceil(wallpaper.getIntrinsicHeight() * scale);
                int x = (getMeasuredWidth() - width) / 2;
                int y = (getMeasuredHeight() - height) / 2;
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
                wallpaper.setBounds(x, y, x + width, y + height);
            }
            wallpaper.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(150), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (cycle == null) {
            helper.start(actions);
            updateReaction();
            restartCycle();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (cycle != null) {
            cycle.cancel();
            cycle = null;
        }
        helper.detach();
        if (backgroundGradientDisposable != null) {
            backgroundGradientDisposable.dispose();
            backgroundGradientDisposable = null;
        }
    }
}

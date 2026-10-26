package org.telegram.messenger.devgram.player.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import org.telegram.messenger.devgram.player.PlayerCovers;
import org.telegram.messenger.devgram.utils.ui.Material3Tokens;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
public class PlayerCoverView extends View {
    private int awaitingDirection;
    private float compact;
    private int containerColor;
    private final Paint containerPaint;
    private final RectF coverRect;
    private boolean crossfade;
    private int current;
    private Delegate delegate;
    private float downX;
    private float downY;
    private float dragStartOffset;
    private boolean dragging;
    private final RectF drawRect;
    private float hideProgress;
    private final MessageObject[] messages;
    private final String[] motionPaths;
    private final ImageReceiver[] motionReceivers;
    private boolean motionRunning;
    private final long[] motionShownAt;
    private int neighbor;
    private int neighborDirection;
    private float offset;
    private final SpringAnimation offsetSpring;
    private float offsetVelocity;
    private int outgoing;
    private int outgoingDirection;
    private float pauseScale;
    private final SpringAnimation pauseSpring;
    private Drawable placeholderIcon;
    private int placeholderIconColor;
    private final boolean[] placeholders;
    private boolean playing;
    private final int[] radii;
    private float radius;
    private final ImageReceiver[] receivers;
    private final float[] slotScales;
    private float swap;
    private final SpringAnimation swapSpring;
    private final int touchSlop;
    private boolean tracking;
    private VelocityTracker velocityTracker;

    public interface Delegate {
        boolean canDrag();

        MessageObject getNeighbor(int i);

        void onCoverLoaded(MessageObject messageObject, ImageReceiver imageReceiver);

        void onSwipe(int i);
    }

    public PlayerCoverView(Context context) {
        super(context);
        this.receivers = new ImageReceiver[3];
        this.messages = new MessageObject[3];
        this.placeholders = new boolean[3];
        this.radii = new int[3];
        this.slotScales = new float[3];
        this.motionReceivers = new ImageReceiver[3];
        this.motionPaths = new String[3];
        this.motionShownAt = new long[3];
        this.containerPaint = new Paint(1);
        this.coverRect = new RectF();
        this.drawRect = new RectF();
        this.neighbor = -1;
        this.outgoing = -1;
        this.swap = 1.0f;
        this.pauseScale = 1.0f;
        this.playing = true;
        this.motionRunning = true;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        for (int i__ = 0; i__ < 3; i__++) {
            final int i = i__;
            this.receivers[i] = new ImageReceiver(this);
            this.receivers[i].setDelegate((imageReceiver, z, z2, z3) -> lambda_new_0(i, imageReceiver, z, z2, z3));
            this.motionReceivers[i] = new ImageReceiver(this);
            this.motionReceivers[i].setCrossfadeAlpha((byte) 2);
            this.slotScales[i] = 1.0f;
        }
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.player_cover_placeholder);
        this.placeholderIcon = drawable != null ? drawable.mutate() : null;
        lambda_new_1(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.player_cover_placeholder));
        SpringAnimation springAnimation = new SpringAnimation(new FloatValueHolder(0.0f));
        this.offsetSpring = springAnimation;
        springAnimation.setMinimumVisibleChange(0.5f);
        springAnimation.setSpring(new SpringForce(0.0f).setDampingRatio(0.8f).setStiffness(380.0f));
        springAnimation.addUpdateListener((dynamicAnimation, f, f2) -> lambda_new_2(dynamicAnimation, f, f2));
        springAnimation.addEndListener((dynamicAnimation, z, f, f2) -> lambda_new_3(dynamicAnimation, z, f, f2));
        SpringAnimation springAnimationCreateSpring = Material3Tokens.createSpring(0.8f, 380.0f, 0.002f, obj -> lambda_new_4((Float) obj));
        this.swapSpring = springAnimationCreateSpring;
        springAnimationCreateSpring.addEndListener((dynamicAnimation, z, f, f2) -> lambda_new_5(dynamicAnimation, z, f, f2));
        this.pauseSpring = Material3Tokens.createSpring(0.8f, 380.0f, 5.0E-4f, obj -> lambda_new_6((Float) obj));
    }
    public /* synthetic */ void lambda_new_0(int i, ImageReceiver imageReceiver, boolean z, boolean z2, boolean z3) {
        Delegate delegate;
        MessageObject messageObject;
        if (z) {
            if ((i != this.current && i != this.neighbor) || (delegate = this.delegate) == null || (messageObject = this.messages[i]) == null) {
                return;
            }
            delegate.onCoverLoaded(messageObject, imageReceiver);
        }
    }
    public /* synthetic */ void lambda_new_1(Drawable drawable) {
        Drawable drawableMutate = drawable.mutate();
        this.placeholderIcon = drawableMutate;
        drawableMutate.setColorFilter(new PorterDuffColorFilter(this.placeholderIconColor, PorterDuff.Mode.SRC_IN));
        invalidate();
    }
    public /* synthetic */ void lambda_new_2(DynamicAnimation dynamicAnimation, float f, float f2) {
        this.offset = f;
        this.offsetVelocity = f2;
        invalidate();
    }
    public /* synthetic */ void lambda_new_3(DynamicAnimation dynamicAnimation, boolean z, float f, float f2) {
        if (z) {
            return;
        }
        onOffsetSettled();
    }
    public /* synthetic */ void lambda_new_4(Float f) {
        this.swap = f.floatValue();
        invalidate();
    }
    public /* synthetic */ void lambda_new_5(DynamicAnimation dynamicAnimation, boolean z, float f, float f2) {
        if (z || !this.crossfade) {
            return;
        }
        releaseOutgoing();
    }
    public /* synthetic */ void lambda_new_6(Float f) {
        this.pauseScale = f.floatValue();
        invalidate();
    }

    private void animateOffset(float f, float f2) {
        this.offsetSpring.cancel();
        this.offsetSpring.setStartValue(this.offset);
        this.offsetSpring.setStartVelocity(f2);
        this.offsetSpring.getSpring().setFinalPosition(f);
        this.offsetSpring.start();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setColors(int i, int i2) {
        this.containerColor = i;
        this.placeholderIconColor = i2;
        Drawable drawable = this.placeholderIcon;
        if (drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(i2, PorterDuff.Mode.SRC_IN));
        }
        invalidate();
    }

    public void setCoverRect(float f, float f2, float f3, float f4, float f5) {
        RectF rectF = this.coverRect;
        if (rectF.left == f && rectF.top == f2 && rectF.width() == f3 && this.radius == f4 && this.compact == f5) {
            return;
        }
        this.coverRect.set(f, f2, f + f3, f3 + f2);
        this.radius = f4;
        this.compact = f5;
        updateMotionPlayback();
        invalidate();
    }

    public RectF getCoverRect() {
        return this.coverRect;
    }

    public float getCoverRadius() {
        return this.radius;
    }

    public float getHideProgress() {
        return this.hideProgress;
    }

    public float getCompactProgress() {
        return this.compact;
    }

    public void setHideProgress(float f) {
        if (this.hideProgress != f) {
            this.hideProgress = f;
            updateMotionPlayback();
            invalidate();
        }
    }

    public void setPlaying(boolean z, boolean z2) {
        this.playing = z;
        updateMotionPlayback();
        float f = z ? 1.0f : 0.94f;
        if (!z2 || !isAttachedToWindow()) {
            this.pauseSpring.cancel();
            this.pauseScale = f;
            invalidate();
        } else {
            if (this.pauseSpring.getSpring().getFinalPosition() == f && (this.pauseSpring.isRunning() || this.pauseScale == f)) {
                return;
            }
            Material3Tokens.animate(this.pauseSpring, this.pauseScale, f);
        }
    }

    public void setMotionCover(MessageObject messageObject, String str) {
        MessageObject messageObject2 = this.messages[this.current];
        if (messageObject2 == null || messageObject == null || messageObject2.getId() != messageObject.getId() || messageObject2.getDialogId() != messageObject.getDialogId() || TextUtils.equals(this.motionPaths[this.current], str)) {
            return;
        }
        clearMotion(this.current);
        if (str != null) {
            String[] strArr = this.motionPaths;
            int i = this.current;
            strArr[i] = str;
            this.motionReceivers[i].setAllowStartAnimation(this.motionRunning);
            this.motionReceivers[this.current].setImage(ImageLocation.getForPath(str), "360_360", null, null, 0L, null, messageObject2, 1);
        }
        invalidate();
    }

    private void clearMotion(int i) {
        String[] strArr = this.motionPaths;
        if (strArr[i] != null) {
            strArr[i] = null;
            this.motionShownAt[i] = 0;
            this.motionReceivers[i].clearImage();
        }
    }

    private float getMotionAlpha(int i) {
        if (this.motionPaths[i] == null) {
            return 0.0f;
        }
        if (this.motionShownAt[i] == 0) {
            AnimatedFileDrawable animation = this.motionReceivers[i].getAnimation();
            if (animation == null || !animation.hasBitmap()) {
                return 0.0f;
            }
            this.motionShownAt[i] = SystemClock.elapsedRealtime();
        }
        float fClamp01 = Utilities.clamp01((SystemClock.elapsedRealtime() - this.motionShownAt[i]) / 400.0f);
        if (fClamp01 < 1.0f) {
            invalidate();
        }
        return CubicBezierInterpolator.EASE_OUT.getInterpolation(fClamp01);
    }

    private void updateMotionPlayback() {
        boolean z = this.playing && this.compact < 1.0f && this.hideProgress < 1.0f;
        if (z == this.motionRunning) {
            return;
        }
        this.motionRunning = z;
        for (int i = 0; i < 3; i++) {
            if (this.motionPaths[i] != null) {
                this.motionReceivers[i].setAllowStartAnimation(z);
                ImageReceiver[] imageReceiverArr = this.motionReceivers;
                if (z) {
                    imageReceiverArr[i].startAnimation();
                } else {
                    imageReceiverArr[i].stopAnimation();
                }
            }
        }
    }

    public boolean hasBitmap() {
        boolean[] zArr = this.placeholders;
        int i = this.current;
        return !zArr[i] && this.receivers[i].hasBitmapImage();
    }

    public ImageReceiver getImageReceiver() {
        return this.receivers[this.current];
    }

    public MessageObject getMessageObject() {
        return this.messages[this.current];
    }

    private float getPage() {
        return this.coverRect.width() + AndroidUtilities.dpf2(24.0f);
    }

    private int freeSlot() {
        for (int i = 0; i < 3; i++) {
            if (i != this.current && i != this.neighbor && i != this.outgoing) {
                return i;
            }
        }
        int i2 = this.outgoing;
        this.outgoing = -1;
        return i2;
    }

    private void load(int i, MessageObject messageObject) {
        this.messages[i] = messageObject;
        this.slotScales[i] = 1.0f;
        clearMotion(i);
        setSlotRadius(i, this.radius);
        if (messageObject == null) {
            this.receivers[i].clearImage();
            this.placeholders[i] = true;
        } else {
            this.placeholders[i] = !PlayerCovers.setCover(this.receivers[i], messageObject, 360, true);
        }
    }

    private void setSlotRadius(int i, float f) {
        int iMax = (int) Math.max(1.0f, f);
        int[] iArr = this.radii;
        if (iArr[i] != iMax) {
            iArr[i] = iMax;
            this.receivers[i].setRoundRadius(iMax);
            this.motionReceivers[i].setRoundRadius(iMax);
        }
    }

    private void releaseSlot(int i) {
        if (i < 0 || i == this.current) {
            return;
        }
        this.receivers[i].clearImage();
        clearMotion(i);
        this.messages[i] = null;
        this.placeholders[i] = false;
        this.slotScales[i] = 1.0f;
    }

    private void handOverPauseScale(int i) {
        this.slotScales[i] = this.pauseScale;
        this.pauseSpring.cancel();
        float f = this.slotScales[this.current];
        this.pauseScale = f;
        float f2 = this.playing ? 1.0f : 0.94f;
        if (f != f2) {
            Material3Tokens.animate(this.pauseSpring, f, f2);
        }
    }

    private void releaseOutgoing() {
        releaseSlot(this.outgoing);
        this.outgoing = -1;
        this.crossfade = false;
        this.swap = 1.0f;
        invalidate();
    }

    private void releaseNeighbor() {
        releaseSlot(this.neighbor);
        this.neighbor = -1;
    }

    private void notifyCurrentLoaded() {
        if (this.delegate != null) {
            MessageObject[] messageObjectArr = this.messages;
            int i = this.current;
            if (messageObjectArr[i] == null || this.placeholders[i] || !this.receivers[i].hasBitmapImage()) {
                return;
            }
            Delegate delegate = this.delegate;
            MessageObject[] messageObjectArr2 = this.messages;
            int i2 = this.current;
            delegate.onCoverLoaded(messageObjectArr2[i2], this.receivers[i2]);
        }
    }

    public void setCover(MessageObject messageObject, boolean z, int i) {
        int i2 = this.awaitingDirection;
        if (i2 != 0 && this.neighbor >= 0 && this.neighborDirection == i2) {
            this.awaitingDirection = 0;
            this.swapSpring.cancel();
            releaseSlot(this.outgoing);
            this.outgoing = this.current;
            this.outgoingDirection = i2;
            this.crossfade = false;
            this.swap = 1.0f;
            int i3 = this.neighbor;
            this.current = i3;
            this.neighbor = -1;
            if (this.messages[i3] != messageObject) {
                load(i3, messageObject);
            }
            handOverPauseScale(this.outgoing);
            this.offset += i2 * getPage();
            animateOffset(0.0f, this.offsetVelocity);
            notifyCurrentLoaded();
            invalidate();
            return;
        }
        this.awaitingDirection = 0;
        abortDrag();
        releaseNeighbor();
        boolean z2 = z && isAttachedToWindow() && !this.coverRect.isEmpty();
        if (z2 && this.compact < 0.5f && this.hideProgress < 0.5f) {
            this.swapSpring.cancel();
            releaseSlot(this.outgoing);
            this.outgoing = this.current;
            this.outgoingDirection = i;
            this.crossfade = false;
            this.swap = 1.0f;
            int iFreeSlot = freeSlot();
            this.current = iFreeSlot;
            load(iFreeSlot, messageObject);
            handOverPauseScale(this.outgoing);
            this.offset += i * getPage();
            animateOffset(0.0f, 0.0f);
        } else {
            SpringAnimation springAnimation = this.offsetSpring;
            if (z2) {
                springAnimation.cancel();
                this.offset = 0.0f;
                releaseSlot(this.outgoing);
                this.outgoing = this.current;
                this.outgoingDirection = i;
                this.crossfade = true;
                int iFreeSlot2 = freeSlot();
                this.current = iFreeSlot2;
                load(iFreeSlot2, messageObject);
                handOverPauseScale(this.outgoing);
                this.swap = 0.0f;
                Material3Tokens.animate(this.swapSpring, 0.0f, 1.0f);
            } else {
                springAnimation.cancel();
                this.swapSpring.cancel();
                this.offset = 0.0f;
                this.swap = 1.0f;
                releaseSlot(this.outgoing);
                this.outgoing = -1;
                this.crossfade = false;
                load(this.current, messageObject);
            }
        }
        notifyCurrentLoaded();
        invalidate();
    }

    public void refreshCover() {
        MessageObject[] messageObjectArr = this.messages;
        int i = this.current;
        MessageObject messageObject = messageObjectArr[i];
        if (messageObject != null) {
            this.placeholders[i] = !PlayerCovers.setCover(this.receivers[i], messageObject, 360, true);
            invalidate();
        }
    }

    public void cancelPendingSwipe() {
        if (this.awaitingDirection != 0) {
            this.awaitingDirection = 0;
            animateOffset(0.0f, this.offsetVelocity);
        }
    }

    private void onOffsetSettled() {
        if (this.awaitingDirection != 0 || Math.abs(this.offset) >= 1.0f) {
            return;
        }
        this.offset = 0.0f;
        if (!this.crossfade) {
            releaseSlot(this.outgoing);
            this.outgoing = -1;
        }
        if (!this.tracking) {
            releaseNeighbor();
        }
        invalidate();
    }

    private void ensureNeighbor(int i) {
        int i2 = this.neighbor;
        if (i2 < 0 || this.neighborDirection != i) {
            if (this.outgoing >= 0 && !this.crossfade && this.outgoingDirection == (-i)) {
                releaseNeighbor();
                this.neighbor = this.outgoing;
                this.neighborDirection = i;
                this.outgoing = -1;
                return;
            }
            if (i2 < 0) {
                this.neighbor = freeSlot();
            }
            this.neighborDirection = i;
            int i3 = this.neighbor;
            Delegate delegate = this.delegate;
            load(i3, delegate != null ? delegate.getNeighbor(i) : null);
        }
    }

    private float rubber(float f) {
        float fDpf2 = AndroidUtilities.dpf2(56.0f);
        return Math.signum(f) * fDpf2 * (1.0f - (1.0f / (((Math.abs(f) / fDpf2) * 0.55f) + 1.0f)));
    }

    private void setDragOffset(float f) {
        if (f != 0.0f) {
            int i = f < 0.0f ? 1 : -1;
            Delegate delegate = this.delegate;
            if (delegate != null && delegate.canDrag()) {
                ensureNeighbor(i);
            } else {
                f = rubber(f);
            }
        }
        this.offset = f;
        invalidate();
    }

    private void abortDrag() {
        if (this.tracking || this.dragging) {
            this.tracking = false;
            this.dragging = false;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            recycleVelocityTracker();
        }
    }

    private void recycleVelocityTracker() {
        VelocityTracker velocityTracker = this.velocityTracker;
        if (velocityTracker != null) {
            velocityTracker.recycle();
            this.velocityTracker = null;
        }
    }

    private void finishDrag(float f, boolean z) {
        Delegate delegate;
        float page = getPage();
        int i = this.offset < 0.0f ? 1 : -1;
        boolean z2 = Math.abs(f) > AndroidUtilities.dpf2(600.0f) && Math.signum(f) == ((float) (-i));
        if (!z && Math.abs(this.offset) > 0.5f && (delegate = this.delegate) != null && delegate.canDrag() && (Math.abs(this.offset) > 0.3f * page || z2)) {
            ensureNeighbor(i);
            this.awaitingDirection = i;
            animateOffset((-i) * page, f);
            performHapticFeedback(3);
            this.delegate.onSwipe(i);
            return;
        }
        if (z) {
            f = 0.0f;
        }
        animateOffset(0.0f, f);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int i = 0; i < 3; i++) {
            this.receivers[i].onAttachedToWindow();
            this.motionReceivers[i].onAttachedToWindow();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        abortDrag();
        this.offsetSpring.cancel();
        this.swapSpring.cancel();
        this.pauseSpring.cancel();
        this.awaitingDirection = 0;
        this.offset = 0.0f;
        this.swap = 1.0f;
        releaseNeighbor();
        releaseSlot(this.outgoing);
        this.outgoing = -1;
        this.crossfade = false;
        for (int i = 0; i < 3; i++) {
            this.receivers[i].onDetachedFromWindow();
            this.motionReceivers[i].onDetachedFromWindow();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        float f;
        float f2;
        if (this.coverRect.isEmpty()) {
            return;
        }
        float f3 = this.hideProgress;
        if (f3 >= 1.0f) {
            return;
        }
        float f4 = 1.0f - f3;
        float page = getPage();
        int i = this.outgoing;
        if (i >= 0) {
            float fLerp = AndroidUtilities.lerp(this.slotScales[i], 1.0f, this.compact);
            boolean z = this.crossfade;
            int i2 = this.outgoing;
            if (z) {
                float fDpf2 = (-this.outgoingDirection) * AndroidUtilities.dpf2(28.0f);
                float f5 = this.swap;
                drawLayer(canvas, i2, fDpf2 * f5, fLerp, f4 * (1.0f - Utilities.clamp01(f5)));
                f = f4;
                f2 = page;
            } else {
                f = f4;
                f2 = page;
                drawSide(canvas, i2, this.offset - (this.outgoingDirection * page), f2, f, fLerp);
            }
        } else {
            f = f4;
            f2 = page;
        }
        int i3 = this.neighbor;
        if (i3 >= 0) {
            drawSide(canvas, i3, (this.neighborDirection * f2) + this.offset, f2, f, AndroidUtilities.lerp(this.slotScales[i3], 1.0f, this.compact));
        }
        float fLerp2 = AndroidUtilities.lerp(this.pauseScale, 1.0f, this.compact);
        if (this.outgoing >= 0 && this.crossfade) {
            float fDpf3 = this.outgoingDirection * AndroidUtilities.dpf2(28.0f);
            float f6 = this.swap;
            drawLayer(canvas, this.current, (1.0f - f6) * fDpf3, fLerp2 * AndroidUtilities.lerp(0.94f, 1.0f, Utilities.clamp01(f6)), f * Utilities.clamp01(this.swap));
            return;
        }
        drawSide(canvas, this.current, this.offset, f2, f, fLerp2);
    }

    private void drawSide(Canvas canvas, int i, float f, float f2, float f3, float f4) {
        float fClamp01 = f2 > 0.0f ? Utilities.clamp01(Math.abs(f) / f2) : 0.0f;
        drawLayer(canvas, i, f, AndroidUtilities.lerp(1.0f, 0.86f, fClamp01) * f4, f3 * AndroidUtilities.lerp(1.0f, 0.55f, fClamp01));
    }

    private void drawLayer(Canvas canvas, int i, float f, float f2, float f3) {
        if (f3 <= 0.0f || i < 0) {
            return;
        }
        this.drawRect.set(this.coverRect);
        this.drawRect.offset(f, 0.0f);
        RectF rectF = this.drawRect;
        if (rectF.right < 0.0f || rectF.left > getWidth()) {
            return;
        }
        if (f2 != 1.0f) {
            float fWidth = (this.drawRect.width() * (1.0f - f2)) / 2.0f;
            this.drawRect.inset(fWidth, fWidth);
        }
        float fMin = Math.min(this.radius * f2, this.drawRect.width() / 2.0f);
        ImageReceiver imageReceiver = this.receivers[i];
        boolean z = !this.placeholders[i] && imageReceiver.hasBitmapImage();
        if (!z || imageReceiver.getCurrentAlpha() < 1.0f) {
            this.containerPaint.setColor(this.containerColor);
            this.containerPaint.setAlpha((int) (Color.alpha(this.containerColor) * f3));
            canvas.drawRoundRect(this.drawRect, fMin, fMin, this.containerPaint);
            if (this.placeholderIcon != null && (this.placeholders[i] || !z)) {
                int iWidth = (int) (this.drawRect.width() * 0.3f);
                float f4 = iWidth / 2.0f;
                int iCenterX = (int) (this.drawRect.centerX() - f4);
                int iCenterY = (int) (this.drawRect.centerY() - f4);
                this.placeholderIcon.setBounds(iCenterX, iCenterY, iCenterX + iWidth, iWidth + iCenterY);
                this.placeholderIcon.setAlpha((int) (255.0f * f3));
                this.placeholderIcon.draw(canvas);
            }
        }
        if (!this.placeholders[i]) {
            setSlotRadius(i, fMin);
            RectF rectF2 = this.drawRect;
            imageReceiver.setImageCoords(rectF2.left, rectF2.top, rectF2.width(), this.drawRect.height());
            imageReceiver.setAlpha(f3);
            imageReceiver.draw(canvas);
        }
        float motionAlpha = getMotionAlpha(i);
        if (motionAlpha > 0.0f) {
            ImageReceiver imageReceiver2 = this.motionReceivers[i];
            setSlotRadius(i, fMin);
            RectF rectF3 = this.drawRect;
            imageReceiver2.setImageCoords(rectF3.left, rectF3.top, rectF3.width(), this.drawRect.height());
            imageReceiver2.setAlpha(f3 * motionAlpha);
            imageReceiver2.draw(canvas);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        if (motionEvent.getActionMasked() != 0 || this.coverRect.contains(motionEvent.getX(), motionEvent.getY())) {
            return super.dispatchTouchEvent(motionEvent);
        }
        return false;
    }

    @Override
    @SuppressLint({"ClickableViewAccessibility"})
    public boolean onTouchEvent(MotionEvent motionEvent) {
        Delegate delegate;
        float xVelocity;
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked == 0) {
            if (this.hideProgress > 0.5f || this.compact > 0.5f || this.awaitingDirection != 0 || (delegate = this.delegate) == null || !delegate.canDrag()) {
                return false;
            }
            this.tracking = true;
            this.dragging = false;
            this.downX = motionEvent.getX();
            this.downY = motionEvent.getY();
            recycleVelocityTracker();
            VelocityTracker velocityTrackerObtain = VelocityTracker.obtain();
            this.velocityTracker = velocityTrackerObtain;
            velocityTrackerObtain.addMovement(motionEvent);
            return true;
        }
        if (actionMasked != 1) {
            if (actionMasked == 2) {
                if (!this.tracking) {
                    return false;
                }
                VelocityTracker velocityTracker = this.velocityTracker;
                if (velocityTracker != null) {
                    velocityTracker.addMovement(motionEvent);
                }
                float x = motionEvent.getX() - this.downX;
                float y = motionEvent.getY() - this.downY;
                if (!this.dragging && Math.abs(x) > this.touchSlop && Math.abs(x) > Math.abs(y)) {
                    this.dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    this.offsetSpring.cancel();
                    this.dragStartOffset = this.offset;
                    this.downX = motionEvent.getX();
                }
                if (this.dragging) {
                    setDragOffset((this.dragStartOffset + motionEvent.getX()) - this.downX);
                }
                return true;
            }
            if (actionMasked != 3) {
                return super.onTouchEvent(motionEvent);
            }
        }
        if (!this.tracking) {
            return false;
        }
        this.tracking = false;
        if (this.dragging) {
            this.dragging = false;
            getParent().requestDisallowInterceptTouchEvent(false);
            VelocityTracker velocityTracker2 = this.velocityTracker;
            if (velocityTracker2 != null) {
                velocityTracker2.addMovement(motionEvent);
                this.velocityTracker.computeCurrentVelocity(1000);
                xVelocity = this.velocityTracker.getXVelocity();
            } else {
                xVelocity = 0.0f;
            }
            finishDrag(xVelocity, motionEvent.getActionMasked() == 3);
        }
        recycleVelocityTracker();
        return true;
    }
}

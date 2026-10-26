package org.telegram.messenger.devgram.player.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import androidx.dynamicanimation.animation.SpringAnimation;
import org.telegram.messenger.devgram.utils.ui.Material3Tokens;
import java.util.List;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.RLottieDrawable;
public class PlayerControlsView extends View {
    private int accentColor;
    private final AccessibilityHelper accessibilityHelper;
    private final RectF[] buttonRects;
    private final Paint containerPaint;
    private Delegate delegate;
    private final Runnable holdRunnable;
    private boolean holding;
    private final Rect iconBounds;
    private boolean loading;
    private final CircularProgressDrawable loadingDrawable;
    private final RLottieDrawable nextDrawable;
    private int onTonalColor;
    private boolean playEnabled;
    private final PlayPauseDrawable playPauseDrawable;
    private boolean playing;
    private int pressed;
    private final RLottieDrawable prevDrawable;
    private final float[] radii;
    private final SpringAnimation[] radiusSprings;
    private boolean sizesInitialized;
    private PorterDuffColorFilter skipIconFilter;
    private int tonalColor;
    private boolean touchInside;
    private final SpringAnimation[] widthSprings;
    private final float[] widths;

    public interface Delegate {
        void onHold(int i, boolean z);

        void onNext();

        void onPlayPause();

        void onPrevious();
    }

    public static int getHeightDp() {
        return 72;
    }
    public /* synthetic */ void lambda_new_0() {
        int i = this.pressed;
        if (i == 0 || i == 2) {
            this.holding = true;
            performHapticFeedback(0);
            Delegate delegate = this.delegate;
            if (delegate != null) {
                delegate.onHold(this.pressed == 0 ? -1 : 1, true);
            }
        }
    }

    public PlayerControlsView(Context context) {
        super(context);
        this.containerPaint = new Paint(1);
        this.buttonRects = new RectF[]{new RectF(), new RectF(), new RectF()};
        this.iconBounds = new Rect();
        this.widths = new float[3];
        this.radii = new float[3];
        this.widthSprings = new SpringAnimation[3];
        this.radiusSprings = new SpringAnimation[3];
        this.playEnabled = true;
        this.pressed = -1;
        this.holdRunnable = () -> lambda_new_0();
        this.prevDrawable = createSkipDrawable();
        this.nextDrawable = createSkipDrawable();
        PlayPauseDrawable playPauseDrawable = new PlayPauseDrawable(28);
        this.playPauseDrawable = playPauseDrawable;
        playPauseDrawable.setCallback(this);
        playPauseDrawable.setParent(this);
        CircularProgressDrawable circularProgressDrawable = new CircularProgressDrawable(AndroidUtilities.dpf2(26.0f), AndroidUtilities.dpf2(3.0f), 0);
        this.loadingDrawable = circularProgressDrawable;
        circularProgressDrawable.setCallback(this);
        circularProgressDrawable.setVisible(false, false);
        for (int i__ = 0; i__ < 3; i__++) {
            final int i = i__;
            this.widthSprings[i] = createSpring(obj -> lambda_new_1(i, (Float) obj));
            this.radiusSprings[i] = createSpring(obj -> lambda_new_2(i, (Float) obj));
        }
        AccessibilityHelper accessibilityHelper = new AccessibilityHelper(this);
        this.accessibilityHelper = accessibilityHelper;
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper);
    }
    public /* synthetic */ void lambda_new_1(int i, Float f) {
        this.widths[i] = f.floatValue();
    }
    public /* synthetic */ void lambda_new_2(int i, Float f) {
        this.radii[i] = f.floatValue();
    }

    private RLottieDrawable createSkipDrawable() {
        RLottieDrawable rLottieDrawable = new RLottieDrawable(R.raw.player_prev, AndroidUtilities.dp(20.0f), AndroidUtilities.dp(20.0f), false, null);
        rLottieDrawable.setAllowDecodeSingleFrame(true);
        rLottieDrawable.setMasterParent(this);
        rLottieDrawable.setCallback(this);
        return rLottieDrawable;
    }

    private SpringAnimation createSpring(final Utilities.Callback<Float> callback) {
        return Material3Tokens.createSpring(0.6f, 800.0f, 0.1f, obj -> lambda_createSpring_3(callback, (Float) obj));
    }
    public /* synthetic */ void lambda_createSpring_3(Utilities.Callback callback, Float f) {
        callback.run(f);
        invalidate();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setColors(int i, int i2, int i3, int i4) {
        this.tonalColor = i;
        this.accentColor = i3;
        if (this.onTonalColor != i2 || this.skipIconFilter == null) {
            this.onTonalColor = i2;
            PorterDuffColorFilter porterDuffColorFilter = new PorterDuffColorFilter(i2, PorterDuff.Mode.SRC_IN);
            this.skipIconFilter = porterDuffColorFilter;
            this.prevDrawable.setColorFilter(porterDuffColorFilter);
            this.nextDrawable.setColorFilter(this.skipIconFilter);
        }
        this.playPauseDrawable.setColor(i4);
        this.loadingDrawable.setColor(i4);
        invalidate();
    }

    public void setPlaying(boolean z, boolean z2) {
        this.playPauseDrawable.setPause(z, z2);
        if (this.playing != z) {
            this.playing = z;
            updateTargets(z2);
            this.accessibilityHelper.invalidateVirtualView(1);
        }
    }

    public void setLoading(boolean z) {
        if (this.loading != z) {
            this.loading = z;
            this.loadingDrawable.setVisible(z && isAttachedToWindow(), false);
            invalidate();
        }
    }

    public void setPlayEnabled(boolean z) {
        if (this.playEnabled != z) {
            this.playEnabled = z;
            invalidate();
        }
    }

    public void playSkipAnimation(boolean z) {
        RLottieDrawable rLottieDrawable = z ? this.nextDrawable : this.prevDrawable;
        rLottieDrawable.setProgress(0.0f, false);
        if (isAttachedToWindow()) {
            rLottieDrawable.start();
        }
        invalidate();
    }

    private float getPlayWidth() {
        return AndroidUtilities.dpf2(96.0f);
    }

    private float getPlayHeight() {
        return AndroidUtilities.dpf2(72.0f);
    }

    private float getSkipSize() {
        return AndroidUtilities.dpf2(56.0f);
    }

    /* JADX WARN: Code duplicated, block: B:31:0x007b  */
    /* JADX WARN: Code duplicated, block: B:37:0x00a2  */
    private void updateTargets(boolean z) {
        float f;
        float f2;
        float f3;
        float playHeight;
        float f4;
        float[] fArr;
        int i;
        float[] fArr2;
        float skipSize = getSkipSize();
        float playWidth = this.playing ? getPlayWidth() : getPlayHeight();
        int i2 = this.pressed;
        if (i2 == 1) {
            f2 = 1.15f * playWidth;
            f3 = skipSize - ((playWidth * 0.15f) / 2.0f);
            f = f3;
        } else if (i2 == 0) {
            f2 = playWidth - (0.15f * skipSize);
            f3 = 1.15f * skipSize;
            f = skipSize;
        } else if (i2 == 2) {
            f = 1.15f * skipSize;
            f2 = playWidth - (0.15f * skipSize);
            f3 = skipSize;
        } else {
            f = skipSize;
            f2 = playWidth;
            f3 = f;
        }
        float fDpf2 = skipSize / 2.0f;
        float fDpf3 = i2 == 0 ? AndroidUtilities.dpf2(12.0f) : fDpf2;
        if (this.pressed == 2) {
            fDpf2 = AndroidUtilities.dpf2(12.0f);
        }
        // кнопка play: нажата — 16dp, играет — 24dp, на паузе — круг
        if (this.pressed == 1) {
            playHeight = AndroidUtilities.dpf2(16.0f);
        } else if (this.playing) {
            playHeight = AndroidUtilities.dpf2(24.0f);
        } else {
            playHeight = getPlayHeight() / 2.0f;
        }
        fArr = new float[]{f3, f2, f};
        fArr2 = new float[]{fDpf3, playHeight, fDpf2};
        for (i = 0; i < 3; i++) {
            if (!z && this.sizesInitialized && isAttachedToWindow()) {
                Material3Tokens.animate(this.widthSprings[i], this.widths[i], fArr[i]);
                Material3Tokens.animate(this.radiusSprings[i], this.radii[i], fArr2[i]);
            } else {
                this.widthSprings[i].cancel();
                this.radiusSprings[i].cancel();
                this.widths[i] = fArr[i];
                this.radii[i] = fArr2[i];
            }
        }
        this.sizesInitialized = true;
        invalidate();
    }

    @Override
    public void onMeasure(int i, int i2) {
        setMeasuredDimension(View.MeasureSpec.getSize(i), (int) Math.ceil(getPlayHeight()));
    }

    @Override
    public void onSizeChanged(int i, int i2, int i3, int i4) {
        super.onSizeChanged(i, i2, i3, i4);
        if (i != i3) {
            updateTargets(false);
        }
    }
    public void computeRects() {
        float width = getWidth() / 2.0f;
        float height = getHeight() / 2.0f;
        float fDpf2 = AndroidUtilities.dpf2(8.0f);
        float[] fArr = this.widths;
        float f = fArr[0] + fArr[1] + fArr[2];
        float f2 = fDpf2 * 2.0f;
        float f3 = width - ((f + f2) / 2.0f);
        float skipSize = getSkipSize();
        float playHeight = getPlayHeight();
        float f4 = skipSize / 2.0f;
        float f5 = height - f4;
        float f6 = f4 + height;
        this.buttonRects[0].set(f3, f5, this.widths[0] + f3, f6);
        float[] fArr2 = this.widths;
        float f7 = fArr2[0] + f3 + fDpf2;
        float f8 = playHeight / 2.0f;
        this.buttonRects[1].set(f7, height - f8, fArr2[1] + f7, height + f8);
        float[] fArr3 = this.widths;
        float f9 = f3 + fArr3[0] + fArr3[1] + f2;
        this.buttonRects[2].set(f9, f5, fArr3[2] + f9, f6);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (!this.sizesInitialized) {
            updateTargets(false);
        }
        computeRects();
        drawSkip(canvas, this.buttonRects[0], this.radii[0], this.prevDrawable, false);
        drawSkip(canvas, this.buttonRects[2], this.radii[2], this.nextDrawable, true);
        drawPlay(canvas, this.buttonRects[1], this.radii[1]);
    }

    private void drawSkip(Canvas canvas, RectF rectF, float f, RLottieDrawable rLottieDrawable, boolean z) {
        this.containerPaint.setColor(this.tonalColor);
        float fMin = Math.min(f, Math.min(rectF.width(), rectF.height()) / 2.0f);
        canvas.drawRoundRect(rectF, fMin, fMin, this.containerPaint);
        int iDp = AndroidUtilities.dp(20.0f);
        float f2 = iDp / 2.0f;
        int iCenterX = (int) (rectF.centerX() - f2);
        int iCenterY = (int) (rectF.centerY() - f2);
        rLottieDrawable.setBounds(iCenterX, iCenterY, iCenterX + iDp, iDp + iCenterY);
        canvas.save();
        if (z) {
            canvas.rotate(180.0f, rectF.centerX(), rectF.centerY());
        }
        rLottieDrawable.draw(canvas);
        canvas.restore();
    }

    private void drawPlay(Canvas canvas, RectF rectF, float f) {
        this.containerPaint.setColor(this.accentColor);
        if (!this.playEnabled) {
            Paint paint = this.containerPaint;
            paint.setAlpha((int) (paint.getAlpha() * 0.6f));
        }
        float fMin = Math.min(f, Math.min(rectF.width(), rectF.height()) / 2.0f);
        canvas.drawRoundRect(rectF, fMin, fMin, this.containerPaint);
        if (this.loading) {
            int iDp = AndroidUtilities.dp(26.0f) / 2;
            this.loadingDrawable.setBounds(((int) rectF.centerX()) - iDp, ((int) rectF.centerY()) - iDp, ((int) rectF.centerX()) + iDp, ((int) rectF.centerY()) + iDp);
            this.loadingDrawable.draw(canvas);
        } else {
            float fDp = AndroidUtilities.dp(28.0f) / 2.0f;
            this.iconBounds.set((int) (rectF.centerX() - fDp), (int) (rectF.centerY() - fDp), (int) (rectF.centerX() + fDp), (int) (rectF.centerY() + fDp));
            this.playPauseDrawable.setBounds(this.iconBounds);
            this.playPauseDrawable.setAlpha(this.playEnabled ? 255 : 153);
            this.playPauseDrawable.draw(canvas);
        }
    }

    @Override
    public boolean verifyDrawable(Drawable drawable) {
        return drawable == this.prevDrawable || drawable == this.nextDrawable || drawable == this.playPauseDrawable || drawable == this.loadingDrawable || super.verifyDrawable(drawable);
    }

    @Override
    public void invalidateDrawable(Drawable drawable) {
        invalidate();
    }
    public int hitTest(float f, float f2) {
        computeRects();
        float fDpf2 = AndroidUtilities.dpf2(4.0f);
        for (int i = 0; i < 3; i++) {
            RectF rectF = this.buttonRects[i];
            if (f >= rectF.left - fDpf2 && f <= rectF.right + fDpf2 && f2 >= 0.0f && f2 <= getHeight()) {
                return i;
            }
        }
        return -1;
    }
    public boolean isButtonEnabled(int i) {
        return i != 1 || this.playEnabled;
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent motionEvent) {
        return this.accessibilityHelper.dispatchHoverEvent(motionEvent) || super.dispatchHoverEvent(motionEvent);
    }

    @Override
    @SuppressLint({"ClickableViewAccessibility"})
    public boolean onTouchEvent(MotionEvent motionEvent) {
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked == 0) {
            int iHitTest = hitTest(motionEvent.getX(), motionEvent.getY());
            if (iHitTest < 0 || !isButtonEnabled(iHitTest)) {
                return false;
            }
            this.pressed = iHitTest;
            this.holding = false;
            this.touchInside = true;
            updateTargets(true);
            if (iHitTest != 1) {
                AndroidUtilities.runOnUIThread(this.holdRunnable, 300L);
            }
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (actionMasked != 1) {
            if (actionMasked == 2) {
                if (this.pressed < 0) {
                    return false;
                }
                if (hitTest(motionEvent.getX(), motionEvent.getY()) != this.pressed && this.touchInside && !this.holding) {
                    this.touchInside = false;
                    AndroidUtilities.cancelRunOnUIThread(this.holdRunnable);
                    release();
                }
                return true;
            }
            if (actionMasked != 3) {
                return super.onTouchEvent(motionEvent);
            }
        }
        if (this.pressed < 0) {
            return false;
        }
        AndroidUtilities.cancelRunOnUIThread(this.holdRunnable);
        int i = this.pressed;
        boolean z = this.holding;
        boolean z2 = motionEvent.getActionMasked() == 1 && this.touchInside && !z;
        release();
        if (z) {
            Delegate delegate = this.delegate;
            if (delegate != null) {
                delegate.onHold(i == 0 ? -1 : 1, false);
            }
        } else if (z2) {
            performClick(i);
        }
        return true;
    }

    private void release() {
        this.pressed = -1;
        this.holding = false;
        this.touchInside = false;
        getParent().requestDisallowInterceptTouchEvent(false);
        updateTargets(true);
    }
    public void performClick(int i) {
        Delegate delegate = this.delegate;
        if (delegate == null) {
            return;
        }
        if (i == 0) {
            playSkipAnimation(false);
            this.delegate.onPrevious();
        } else if (i == 2) {
            playSkipAnimation(true);
            this.delegate.onNext();
        } else {
            delegate.onPlayPause();
        }
        this.accessibilityHelper.sendEventForVirtualView(i, 1);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.prevDrawable.setCallback(this);
        this.nextDrawable.setCallback(this);
        this.loadingDrawable.setVisible(this.loading, false);
    }

    @Override
    public void onDetachedFromWindow() {
        Delegate delegate;
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(this.holdRunnable);
        if (this.holding && (delegate = this.delegate) != null) {
            delegate.onHold(this.pressed == 0 ? -1 : 1, false);
        }
        this.pressed = -1;
        this.holding = false;
        this.prevDrawable.stop();
        this.nextDrawable.stop();
        this.loadingDrawable.setVisible(false, false);
        for (int i = 0; i < 3; i++) {
            this.widthSprings[i].cancel();
            this.radiusSprings[i].cancel();
        }
        this.sizesInitialized = false;
    }

    public void recycle() {
        this.prevDrawable.recycle(true);
        this.nextDrawable.recycle(true);
    }

    public class AccessibilityHelper extends ExploreByTouchHelper {
        public AccessibilityHelper(View view) {
            super(view);
        }

        @Override
        public int getVirtualViewAt(float f, float f2) {
            int iHitTest = PlayerControlsView.this.hitTest(f, f2);
            if (iHitTest < 0) {
                return Integer.MIN_VALUE;
            }
            return iHitTest;
        }

        @Override
        public void getVisibleVirtualViews(List<Integer> list) {
            list.add(0);
            list.add(1);
            list.add(2);
        }

        @Override
        public void onPopulateNodeForVirtualView(int i, AccessibilityNodeInfoCompat accessibilityNodeInfoCompat) {
            PlayerControlsView.this.computeRects();
            RectF rectF = PlayerControlsView.this.buttonRects[Math.max(0, Math.min(2, i))];
            accessibilityNodeInfoCompat.setBoundsInParent(new Rect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom));
            accessibilityNodeInfoCompat.setClassName(Button.class.getName());
            accessibilityNodeInfoCompat.setEnabled(PlayerControlsView.this.isButtonEnabled(i));
            accessibilityNodeInfoCompat.addAction(16);
            if (i == 0) {
                accessibilityNodeInfoCompat.setContentDescription(LocaleController.getString(R.string.AccDescrPrevious));
            } else if (i == 2) {
                accessibilityNodeInfoCompat.setContentDescription(LocaleController.getString(R.string.Next));
            } else {
                accessibilityNodeInfoCompat.setContentDescription(LocaleController.getString(PlayerControlsView.this.playing ? R.string.AccActionPause : R.string.AccActionPlay));
            }
        }

        @Override
        public boolean onPerformActionForVirtualView(int i, int i2, Bundle bundle) {
            if (i2 != 16 || !PlayerControlsView.this.isButtonEnabled(i)) {
                return false;
            }
            PlayerControlsView.this.performClick(i);
            return true;
        }
    }
}

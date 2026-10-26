package org.telegram.messenger.devgram.player.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.SpeedIconDrawable;
public class SpeedTextDrawable extends Drawable implements Drawable.Callback {
    private final AnimatedTextView.AnimatedTextDrawable textDrawable;

    @Override
    public int getOpacity() {
        return -3;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    public SpeedTextDrawable() {
        AnimatedTextView.AnimatedTextDrawable animatedTextDrawable = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        this.textDrawable = animatedTextDrawable;
        animatedTextDrawable.setCallback(this);
        animatedTextDrawable.setAnimationProperties(0.3f, 0L, 165L, CubicBezierInterpolator.EASE_OUT_QUINT);
        animatedTextDrawable.setGravity(17);
        animatedTextDrawable.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
        animatedTextDrawable.setTextSize(AndroidUtilities.dp(16.0f));
    }

    public void setValue(float f, boolean z) {
        String str = SpeedIconDrawable.formatNumber(f) + "x";
        if (z && TextUtils.equals(this.textDrawable.getText(), str)) {
            return;
        }
        this.textDrawable.cancelAnimation();
        this.textDrawable.setText(str, z);
        invalidateSelf();
    }

    public void setColor(int i) {
        this.textDrawable.setTextColor(i);
        invalidateSelf();
    }

    @Override
    public void invalidateDrawable(Drawable drawable) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable drawable, Runnable runnable, long j) {
        scheduleSelf(runnable, j);
    }

    @Override
    public void unscheduleDrawable(Drawable drawable, Runnable runnable) {
        unscheduleSelf(runnable);
    }

    @Override
    public void draw(Canvas canvas) {
        this.textDrawable.setBounds(getBounds());
        this.textDrawable.draw(canvas);
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(48.0f);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(24.0f);
    }

    @Override
    public void setAlpha(int i) {
        this.textDrawable.setAlpha(i);
    }
}

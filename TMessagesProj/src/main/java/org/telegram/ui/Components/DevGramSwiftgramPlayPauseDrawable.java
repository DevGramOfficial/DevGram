package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramSwiftgramDesign;
import org.telegram.messenger.R;

/** Original GlobalMusicPlayer artwork with a reversible, finite source-style transition. */
public final class DevGramSwiftgramPlayPauseDrawable extends PlayPauseDrawable {
    private final Drawable play, pause;
    private float from, value, target;
    private long started;
    private int opacity = 255;

    public DevGramSwiftgramPlayPauseDrawable(Context context) {
        super(28);
        play = ContextCompat.getDrawable(context, R.drawable.swiftgram_chrome_player_play).mutate();
        pause = ContextCompat.getDrawable(context, R.drawable.swiftgram_chrome_player_pause).mutate();
    }

    @Override public void setPause(boolean paused, boolean animated) {
        float next = paused ? 1 : 0;
        if (next == target) return;
        from = value;
        target = next;
        started = animated && DevGramSwiftgramDesign.section("animations") ? SystemClock.uptimeMillis() : 0;
        if (started == 0) value = target;
        invalidateSelf();
    }

    @Override public void draw(@NonNull Canvas canvas) {
        if (started != 0) {
            float fraction = Math.min(1, (SystemClock.uptimeMillis() - started) / 200f);
            value = from + (target - from) * fraction;
            if (fraction >= 1) started = 0; else invalidateSelf();
        }
        Rect bounds = getBounds();
        drawIcon(canvas, play, bounds, 1 - value);
        drawIcon(canvas, pause, bounds, value);
    }

    private void drawIcon(Canvas canvas, Drawable icon, Rect bounds, float alpha) {
        if (alpha <= 0) return;
        int width = icon.getIntrinsicWidth(), height = icon.getIntrinsicHeight();
        float scale = Math.min(1f, Math.min(bounds.width() / (float) Math.max(1, width), bounds.height() / (float) Math.max(1, height)));
        width = Math.round(width * scale); height = Math.round(height * scale);
        icon.setBounds(bounds.centerX() - width / 2, bounds.centerY() - height / 2, bounds.centerX() + (width + 1) / 2, bounds.centerY() + (height + 1) / 2);
        icon.setAlpha(Math.round(opacity * alpha));
        icon.draw(canvas);
    }

    @Override public int getIntrinsicWidth() { return AndroidUtilities.dp(40); }
    @Override public int getIntrinsicHeight() { return AndroidUtilities.dp(40); }
    @Override public void setAlpha(int alpha) { opacity = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) {
        if (play != null) { play.setColorFilter(filter); pause.setColorFilter(filter); }
        invalidateSelf();
    }
}

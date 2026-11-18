package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.ui.ActionBar.Theme;

/** Hold to place a header part; vertical scrolling remains available before the hold. */
public final class DevGramPositionPad extends View {
    public interface Changed { void position(int x, int y); }
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Theme.ResourcesProvider resources;
    private final Changed changed;
    private final Runnable locked;
    private float downX, downY;
    private int x, y;
    private boolean dragging, available;
    private final Runnable hold = this::beginDrag;
    private void beginDrag() {
        if (!available) { if (locked != null) locked.run(); return; }
        dragging = true; getParent().requestDisallowInterceptTouchEvent(true);
        DevGramStudioControls.haptic(this,HapticFeedbackConstants.LONG_PRESS); move(downX, downY);
    }

    public DevGramPositionPad(Context context, int x, int y, boolean available, Theme.ResourcesProvider resources, Changed changed, Runnable locked) {
        super(context); this.x = x; this.y = y; this.available = available; this.resources = resources; this.changed = changed; this.locked = locked;
        setContentDescription(DevGramLocale.text("Удерживайте и двигайте точку, чтобы изменить расположение."));
        setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_windowBackgroundGray, resources), Theme.getColor(Theme.key_divider, resources)));
    }
    public void available(boolean value) { available = value; if (!value) cancel(); invalidate(); }
    public void position(int x, int y) { if (!dragging) { this.x = x; this.y = y; invalidate(); } }
    private static int dp(float n) { return AndroidUtilities.dp(n); }
    private float edge() { return Math.max(1, Math.min(getWidth() - dp(40), getHeight() - dp(40))); }
    private float left() { return (getWidth() - edge()) / 2; }
    private float top() { return (getHeight() - edge()) / 2; }
    private void move(float px, float py) {
        x = Math.max(-45, Math.min(45, Math.round(((px - left()) / edge() - .5f) * 90)));
        y = Math.max(-45, Math.min(45, Math.round(((py - top()) / edge() - .5f) * 90)));
        changed.position(x, y); invalidate();
    }
    private void cancel() { removeCallbacks(hold); dragging = false; if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false); }
    @Override protected void onDetachedFromWindow() { cancel(); super.onDetachedFromWindow(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); postDelayed(hold, 240); return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) move(event.getX(), event.getY());
                else if (Math.hypot(event.getX() - downX, event.getY() - downY) > dp(8)) removeCallbacks(hold);
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) move(event.getX(), event.getY());
                else if (!available && locked != null) locked.run();
                cancel(); performClick(); return true;
            case MotionEvent.ACTION_CANCEL: cancel(); return true;
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDraw(Canvas canvas) {
        float side = edge(), left = left(), top = top();
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resources);
        ink.setStrokeWidth(dp(1)); ink.setColor(Theme.getColor(Theme.key_divider, resources));
        for (int i = 0; i <= 6; i++) {
            float at = side * i / 6;
            canvas.drawLine(left + at, top, left + at, top + side, ink);
            canvas.drawLine(left, top + at, left + side, top + at, ink);
        }
        float cx = left + side * (x / 90f + .5f), cy = top + side * (y / 90f + .5f);
        ink.setColor(accent); ink.setAlpha(available ? 90 : 35);
        canvas.drawLine(left + side / 2, top, left + side / 2, top + side, ink);
        canvas.drawLine(left, top + side / 2, left + side, top + side / 2, ink);
        canvas.drawCircle(cx, cy, dp(dragging ? 18 : 14), ink);
        ink.setAlpha(available ? 255 : 90); canvas.drawCircle(cx, cy, dp(6), ink);
        ink.setTextSize(dp(12)); ink.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("X " + x + "%   Y " + y + "%", getWidth() / 2f, getHeight() - dp(4), ink);
    }
}

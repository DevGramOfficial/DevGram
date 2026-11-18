package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.Supplier;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.Theme;

/** A real surface preview with center and direction handles, shared by gradient/fade editors. */
public final class DevGramSurfacePad extends View {
    public interface Changed { void set(int x, int y, int angle, int spread); }
    private final Supplier<JSONObject> document;
    private final Changed changed;
    private final Runnable locked;
    private final String prefix, effect;
    private final boolean fade;
    private final DevGramProfilePageRenderer renderer;
    private final Theme.ResourcesProvider resources;
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF board = new RectF();
    private final Path clip = new Path();
    private boolean available, dragging, center;
    private float downX, downY;
    private final Runnable hold = this::begin;

    public DevGramSurfacePad(Context context, int account, long uid, String prefix, boolean fade,
                             Supplier<JSONObject> document, boolean available, Theme.ResourcesProvider resources,
                             Changed changed, Runnable locked) {
        super(context); this.prefix = prefix; this.fade = fade; this.effect = fade ? "Fade" : "Gradient";
        this.document = document; this.available = available; this.resources = resources; this.changed = changed; this.locked = locked;
        renderer = new DevGramProfilePageRenderer(this, account, uid, prefix); renderer.setStill(true);
        setContentDescription(DevGramLocale.text("Удерживайте центр или конец линии и перетаскивайте, чтобы настроить эффект."));
    }
    private static int dp(float n) { return AndroidUtilities.dp(n); }
    public void available(boolean value) { available = value; if (!value) cancel(); invalidate(); }
    private boolean radial(JSONObject doc) { return doc.optInt(prefix + effect + "Type") == (fade ? 2 : 1); }
    private float centerX(JSONObject doc) { return board.left + board.width() * doc.optInt(prefix + effect + "CenterX", 50) / 100f; }
    private float centerY(JSONObject doc) { return board.top + board.height() * doc.optInt(prefix + effect + "CenterY", 50) / 100f; }
    private void begin() {
        if (!available) { if (locked != null) locked.run(); return; }
        dragging = true; JSONObject doc = document.get();
        center = radial(doc) || Math.hypot(downX - centerX(doc), downY - centerY(doc)) <= dp(22);
        getParent().requestDisallowInterceptTouchEvent(true);
        DevGramStudioControls.haptic(this,HapticFeedbackConstants.LONG_PRESS); move(downX, downY);
    }
    private void move(float x, float y) {
        JSONObject doc = document.get();
        int cx = doc.optInt(prefix + effect + "CenterX", 50), cy = doc.optInt(prefix + effect + "CenterY", 50);
        int angle = doc.optInt(prefix + effect + "Angle"), spread = doc.optInt(prefix + effect + "Spread", 100);
        if (center) {
            cx = Math.max(0, Math.min(100, Math.round((x - board.left) * 100 / Math.max(1, board.width()))));
            cy = Math.max(0, Math.min(100, Math.round((y - board.top) * 100 / Math.max(1, board.height()))));
        } else {
            float dx = x - centerX(doc), dy = y - centerY(doc);
            angle = Math.round((float) Math.toDegrees(fade?Math.atan2(dx/Math.max(1,board.width()),-dy/Math.max(1,board.height())):Math.atan2(dy, dx)) + 360) % 360;
            spread = Math.max(20, Math.min(200, Math.round(fade?(float)Math.hypot(dx/Math.max(1,board.width()),dy/Math.max(1,board.height()))*200:(float) Math.hypot(dx, dy) * 200 / Math.max(1, Math.max(board.width(), board.height())))));
        }
        changed.set(cx, cy, angle, spread); invalidate();
    }
    private void cancel() { removeCallbacks(hold); dragging = false; if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); renderer.attach(); }
    @Override protected void onDetachedFromWindow() { cancel(); renderer.detach(); super.onDetachedFromWindow(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: downX = event.getX(); downY = event.getY(); postDelayed(hold, 240); return true;
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
        JSONObject doc = document.get(); board.set(dp(16), dp(12), getWidth() - dp(16), getHeight() - dp(12));
        int save = canvas.save(); clip.rewind(); clip.addRoundRect(board, dp(16), dp(16), Path.Direction.CW); canvas.clipPath(clip);
        ink.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resources));canvas.drawRect(board, ink);
        canvas.translate(board.left, board.top); renderer.draw(canvas, (int)board.width(), (int)board.height(), doc); canvas.restoreToCount(save);
        float x = centerX(doc), y = centerY(doc);
        float radius = DevGramSurfaceGeometry.radius(board.width(), board.height(), doc.optInt(prefix + effect + "Spread", 100)) / 2f;
        double angle = Math.toRadians(doc.optInt(prefix + effect + "Angle"));
        float[] direction=fade?DevGramSurfaceGeometry.fadeLine(board.width(),board.height(),doc.optInt(prefix+effect+"CenterX",50),doc.optInt(prefix+effect+"CenterY",50),doc.optInt(prefix+effect+"Angle"),doc.optInt(prefix+effect+"Spread",100)):null;
        float endX = fade?board.left+direction[2]:x + (float)Math.cos(angle) * radius, endY = fade?board.top+direction[3]:y + (float)Math.sin(angle) * radius;
        ink.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resources)); ink.setAlpha(available ? 255 : 90); ink.setStrokeWidth(dp(2));
        if (radial(doc)) { ink.setStyle(Paint.Style.STROKE);canvas.drawCircle(x, y, radius, ink);ink.setStyle(Paint.Style.FILL); }
        else { canvas.drawLine(x, y, endX, endY, ink);canvas.drawCircle(endX, endY, dp(6), ink); }
        ink.setStyle(Paint.Style.STROKE);canvas.drawCircle(x, y, dp(8), ink);ink.setStyle(Paint.Style.FILL);canvas.drawCircle(x, y, dp(3), ink);
    }
}

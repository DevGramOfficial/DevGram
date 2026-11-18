package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramContour;
import org.telegram.messenger.DevGramLocale;
import org.telegram.ui.ActionBar.Theme;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Hold, then draw a closed outline, as in the reference; scrolling never erases the shape. */
public final class DevGramShapePad extends View {
    private final ArrayList<float[]> points = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Consumer<JSONArray> changed;
    private ArrayList<float[]> previous;
    private boolean drawing;
    private boolean available = true;
    private Runnable locked;
    private float downX, downY;
    private final Runnable hold = this::begin;
    public DevGramShapePad(Context context, JSONArray initial, Consumer<JSONArray> changed) {
        super(context); this.changed = changed;
        if (initial != null) for (int i = 0; i < Math.min(160, initial.length()); i++) {
            JSONArray point = initial.optJSONArray(i);
            if (point != null) points.add(new float[]{point.optInt(0)/1000f, point.optInt(1)/1000f});
        }
        setContentDescription(DevGramLocale.text("Удерживайте палец, затем нарисуйте контур аватара."));
        setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_windowBackgroundGray), Theme.getColor(Theme.key_divider)));
    }
    private static int dp(float n) { return AndroidUtilities.dp(n); }
    private float insetX() { return (getWidth() - edge()) / 2; }
    private float insetY() { return (getHeight() - edge()) / 2; }
    private float edge() { return Math.max(1, Math.min(getWidth(), getHeight()) - dp(48)); }
    private void begin() {
        if (!available) { if (locked != null) locked.run();return; }
        drawing = true; previous = new ArrayList<>(points); points.clear(); add(downX, downY);
        getParent().requestDisallowInterceptTouchEvent(true); DevGramStudioControls.haptic(this,HapticFeedbackConstants.LONG_PRESS);
    }
    private void add(float px, float py) {
        float x = Math.max(0, Math.min(1, (px - insetX()) / edge())), y = Math.max(0, Math.min(1, (py - insetY()) / edge()));
        if (!points.isEmpty()) {
            float[] last = points.get(points.size() - 1);
            if (Math.hypot(last[0] - x, last[1] - y) < .02f) return;
        }
        if (points.size() < 2048) points.add(new float[]{x, y});
        if (points.size() >= 3) emit();
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        float x = insetX(), y = insetY(), edge = edge();
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(Theme.getColor(Theme.key_divider));
        for (int i = 0; i <= 4; i++) {
            float at = edge * i / 4;
            canvas.drawLine(x + at, y, x + at, y + edge, paint);canvas.drawLine(x, y + at, x + edge, y + at, paint);
        }
        canvas.drawCircle(x + edge / 2, y + edge / 2, edge / 2, paint);
        path.rewind();
        for (int i = 0; i < points.size(); i++) {
            float[] p = points.get(i);
            if (i == 0) path.moveTo(x + p[0] * edge, y + p[1] * edge);else path.lineTo(x + p[0] * edge, y + p[1] * edge);
        }
        if (points.size() >= 3) path.close();
        paint.setColor(accent); paint.setStyle(Paint.Style.FILL); paint.setAlpha(35); canvas.drawPath(path, paint);
        paint.setAlpha(255); paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2)); canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        if (drawing && !points.isEmpty()) { float[] last = points.get(points.size() - 1);canvas.drawCircle(x + last[0] * edge, y + last[1] * edge, dp(5), paint); }
    }
    private void end(boolean cancel) {
        removeCallbacks(hold);
        if (drawing && (cancel || points.size() < 3)) {
            points.clear(); if (previous != null) points.addAll(previous);emit();
        }
        previous = null; drawing = false;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }
    @Override protected void onDetachedFromWindow() { end(true);super.onDetachedFromWindow(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!available) { if (event.getActionMasked() == MotionEvent.ACTION_UP && locked != null) locked.run();return true; }
        if (event.getPointerCount() > 1) { end(true);return true; }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: downX=event.getX();downY=event.getY();postDelayed(hold,240);return true;
            case MotionEvent.ACTION_MOVE:
                if (drawing) add(event.getX(), event.getY());
                else if (Math.hypot(event.getX()-downX,event.getY()-downY)>dp(8)) removeCallbacks(hold);
                return true;
            case MotionEvent.ACTION_UP: if(drawing)add(event.getX(),event.getY());end(false);performClick();return true;
            case MotionEvent.ACTION_CANCEL: end(true);return true;
        }
        return true;
    }
    public void apply() { if(points.size()>=3)emit(); }
    public void setAccess(boolean value, Runnable locked) { this.available=value;this.locked=locked;if(!value)end(true); }
    public void reset() { end(false);points.clear();emit();invalidate(); }
    private void emit() {
        float[] raw = new float[points.size()*2];for(int i=0;i<points.size();i++){raw[i*2]=points.get(i)[0];raw[i*2+1]=points.get(i)[1];}
        float[] sampled = DevGramContour.resample(raw,160);JSONArray result=new JSONArray();
        for(int i=0;i<sampled.length;i+=2){JSONArray point=new JSONArray();point.put(Math.round(sampled[i]*1000));point.put(Math.round(sampled[i+1]*1000));result.put(point);}
        changed.accept(result);
    }
    @Override public boolean performClick(){super.performClick();return true;}
}

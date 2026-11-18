package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import org.json.JSONObject;
import org.telegram.messenger.DevGramImageCrop;

/** Keeps the original animated receiver; only its cover rectangle changes. */
public final class DevGramProfileImageView extends BackupImageView {
    private final DevGramImageCrop crop = new DevGramImageCrop();
    private final Path clip = new Path();
    private final RectF viewport = new RectF();
    private final float[] radii = new float[8];
    private boolean clipDirty = true;
    private ScaleGestureDetector detector;
    private int pointer = -1;
    private float lastX, lastY, focusX, focusY;

    public DevGramProfileImageView(Context context) { super(context); }

    public void setCrop(JSONObject row) {
        crop.set(row.optInt("imageFocusX", 500), row.optInt("imageFocusY", 500), row.optInt("imageScale", 100));
        invalidate();
    }

    public void saveCrop(JSONObject row) {
        try { row.put("imageFocusX", crop.focusX()).put("imageFocusY", crop.focusY()).put("imageScale", crop.scale()); }
        catch (org.json.JSONException ignored) {}
    }

    public void resetCrop() { crop.set(500, 500, 100); invalidate(); }

    @Override public void setRoundRadius(int radius) { setRoundRadius(radius, radius, radius, radius); }
    @Override public void setRoundRadius(int tl, int tr, int bl, int br) {
        radii[0] = radii[1] = Math.max(0, tl);
        radii[2] = radii[3] = Math.max(0, tr);
        radii[4] = radii[5] = Math.max(0, br);
        radii[6] = radii[7] = Math.max(0, bl);
        imageReceiver.setRoundRadius(0);
        clipDirty = true;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clipDirty = true;
    }

    private void layoutCrop() {
        crop.layout(getWidth(), getHeight(), imageReceiver.getBitmapWidth(), imageReceiver.getBitmapHeight());
    }

    @Override protected void onDraw(Canvas canvas) {
        layoutCrop();
        if (clipDirty) {
            clipDirty = false;
            viewport.set(0, 0, getWidth(), getHeight());
            clip.rewind();
            clip.addRoundRect(viewport, radii, Path.Direction.CW);
        }
        int save = canvas.save();
        canvas.clipPath(clip);
        imageReceiver.setImageCoords(crop.left, crop.top, crop.width, crop.height);
        imageReceiver.draw(canvas);
        canvas.restoreToCount(save);
    }

    public void enableCropGestures() {
        detector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector d) {
                focusX = d.getFocusX(); focusY = d.getFocusY();
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector d) {
                crop.move(d.getFocusX() - focusX, d.getFocusY() - focusY);
                crop.zoom(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                focusX = d.getFocusX(); focusY = d.getFocusY();
                invalidate();
                return true;
            }
        });
        detector.setQuickScaleEnabled(false);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (detector == null) return super.onTouchEvent(event);
        layoutCrop();
        detector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pointer = event.getPointerId(0); lastX = event.getX(); lastY = event.getY();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1 && !detector.isInProgress()) {
            int index = event.findPointerIndex(pointer);
            if (index >= 0) {
                crop.move(event.getX(index) - lastX, event.getY(index) - lastY);
                lastX = event.getX(index); lastY = event.getY(index);
                invalidate();
            }
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            int remaining = event.getActionIndex() == 0 ? 1 : 0;
            pointer = event.getPointerId(remaining);
            lastX = event.getX(remaining); lastY = event.getY(remaining);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pointer = -1;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            if (action == MotionEvent.ACTION_UP) performClick();
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}

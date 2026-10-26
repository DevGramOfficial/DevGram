package org.telegram.ui.Components.devgram;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Разделитель двух панелей в планшетном режиме: перетаскивается пальцем, ширина левой панели
 * запоминается долей от ширины окна, у стандартной ширины (35%) есть «прилипание».
 */
public final class TabletSplitUiHelper {

    private static final String PREF = "dg_tabletLeftFraction";
    private static final float DEFAULT_FRACTION = .35f;

    public interface Callback {
        boolean canResize();

        void onResized();
    }

    private final ViewGroup container;
    private final Callback callback;
    private final HandleView handleView;
    private final int touchSlop;

    private int insetLeft;
    private int contentWidth;
    private int leftWidth;

    private boolean tracking;
    private boolean dragging;
    private float startX, startY;
    private int startWidth, rawWidth, dragWidth, defaultWidth;
    private boolean snapped;
    private float snapProgress;
    private ValueAnimator snapAnimator;

    public TabletSplitUiHelper(ViewGroup container, Callback callback) {
        this.container = container;
        this.callback = callback;
        this.handleView = new HandleView(container.getContext());
        this.touchSlop = ViewConfiguration.get(container.getContext()).getScaledTouchSlop();
    }

    public static float leftFraction() {
        return MessagesController.getGlobalMainSettings().getFloat(PREF, DEFAULT_FRACTION);
    }

    private static void setLeftFraction(float fraction) {
        MessagesController.getGlobalMainSettings().edit().putFloat(PREF, fraction).apply();
    }

    public static int gutter() {
        return AndroidUtilities.dp(12);
    }

    /** Начало правой панели при заданной ширине окна — для расчётов вне LaunchActivity. */
    public static int getRightPaneStart(int fullWidth, int insetLeft, int insetRight) {
        return insetLeft + getLeftWidth(fullWidth - insetLeft - insetRight, leftFraction()) + gutter();
    }

    private static int getLeftWidth(int content, float fraction) {
        int min = AndroidUtilities.dp(320);
        int max = Math.max(min, Math.min((int) (content * .6f), content - gutter() - AndroidUtilities.dp(360)));
        return Utilities.clamp(Math.round(content * fraction), max, min);
    }

    public View getHandleView() {
        return handleView;
    }

    public void update(int width, int insetLeft, int insetRight) {
        this.insetLeft = insetLeft;
        contentWidth = width - insetLeft - insetRight;
        leftWidth = insetLeft + (dragging ? dragWidth : getLeftWidth(contentWidth, leftFraction()));
    }

    public int getLeftPaneWidth() {
        return leftWidth;
    }

    public int getRightPaneStart() {
        return leftWidth + gutter();
    }

    public void measureHandle(int height) {
        handleView.measure(View.MeasureSpec.makeMeasureSpec(gutter() + AndroidUtilities.dp(16) * 2, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    }

    public void layoutHandle() {
        int x = leftWidth - AndroidUtilities.dp(16);
        handleView.layout(x, 0, x + handleView.getMeasuredWidth(), handleView.getMeasuredHeight());
    }

    public boolean onInterceptTouchEvent(MotionEvent event) {
        return handleTouch(event, true);
    }

    public boolean onTouchEvent(MotionEvent event) {
        return handleTouch(event, false);
    }

    private boolean handleTouch(MotionEvent event, boolean intercept) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                tracking = handleView.getVisibility() == View.VISIBLE && contentWidth > 0
                        && Math.abs(event.getX() - leftWidth - gutter() / 2f) <= AndroidUtilities.dp(14)
                        && callback.canResize();
                startX = event.getX();
                startY = event.getY();
                return tracking && !intercept;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) {
                    return false;
                }
                if (!dragging) {
                    float dx = event.getX() - startX;
                    float dy = event.getY() - startY;
                    if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                        tracking = false;
                        return false;
                    }
                    if (Math.abs(dx) <= touchSlop) {
                        return !intercept;
                    }
                    startDrag(event.getX());
                }
                updateDrag(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean wasDragging = dragging;
                tracking = false;
                if (wasDragging) {
                    finishDrag();
                }
                return wasDragging;
        }
        return false;
    }

    private void startDrag(float x) {
        dragging = true;
        startX = x;
        startWidth = rawWidth = dragWidth = leftWidth - insetLeft;
        defaultWidth = getLeftWidth(contentWidth, DEFAULT_FRACTION);
        snapped = startWidth == defaultWidth;
        snapProgress = snapped ? 1f : 0f;
        ViewParent parent = container.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
        handleView.invalidate();
    }

    private void updateDrag(float x) {
        rawWidth = getLeftWidth(contentWidth, (startWidth + x - startX) / contentWidth);
        int distance = Math.abs(rawWidth - defaultWidth);
        // гистерезис: прилипаем ближе 12dp, отлипаем дальше 32dp
        boolean snap = snapped ? distance < AndroidUtilities.dp(32) : distance < AndroidUtilities.dp(12);
        if (snap != snapped) {
            snapped = snap;
            if (snap) {
                AndroidUtilities.vibrateCursor(handleView);
            }
            animateSnap(snap);
        }
        applyDragWidth();
    }

    private void animateSnap(boolean snap) {
        if (snapAnimator != null) {
            snapAnimator.cancel();
        }
        snapAnimator = ValueAnimator.ofFloat(snapProgress, snap ? 1f : 0f);
        snapAnimator.addUpdateListener(a -> {
            snapProgress = (float) a.getAnimatedValue();
            applyDragWidth();
        });
        snapAnimator.setDuration(220);
        snapAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        snapAnimator.start();
    }

    private void applyDragWidth() {
        if (!dragging) {
            return;
        }
        int width = AndroidUtilities.lerp(rawWidth, defaultWidth, snapProgress);
        if (width != dragWidth) {
            dragWidth = width;
            container.requestLayout();
        }
    }

    private void finishDrag() {
        if (snapAnimator != null) {
            snapAnimator.cancel();
            snapAnimator = null;
        }
        if (snapped) {
            dragWidth = defaultWidth;
        }
        dragging = false;
        boolean changed = dragWidth != startWidth;
        if (changed) {
            setLeftFraction(snapped ? DEFAULT_FRACTION : dragWidth / (float) contentWidth);
        }
        container.requestLayout();
        handleView.invalidate();
        if (changed) {
            callback.onResized();
        }
    }

    public final class HandleView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path gutterPath = new Path();
        private final Path cornerPath = new Path();
        private final Path circlePath = new Path();
        private final AnimatedFloat activeProgress = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        public HandleView(Context context) {
            super(context);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            // полоса между панелями + вогнутые углы, которые скругляют края обеих панелей
            float r = AndroidUtilities.dp(16);
            float left = r, right = w - r;
            gutterPath.rewind();
            gutterPath.addRect(left, 0, right, h, Path.Direction.CW);
            addCorner(left, 0, left - r, r);
            addCorner(left, h, left - r, h - r);
            addCorner(right, 0, right + r, r);
            addCorner(right, h, right + r, h - r);
        }

        private void addCorner(float x, float y, float cx, float cy) {
            float r = AndroidUtilities.dp(16);
            cornerPath.rewind();
            cornerPath.addRect(Math.min(x, cx), Math.min(y, cy), Math.max(x, cx), Math.max(y, cy), Path.Direction.CW);
            circlePath.rewind();
            circlePath.addCircle(cx, cy, r, Path.Direction.CW);
            cornerPath.op(circlePath, Path.Op.DIFFERENCE);
            gutterPath.op(cornerPath, Path.Op.UNION);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float active = activeProgress.set(dragging);
            float pillWidth = AndroidUtilities.lerp(AndroidUtilities.dpf2(4), AndroidUtilities.dpf2(6), active);
            float pillHeight = AndroidUtilities.lerp(AndroidUtilities.dpf2(36), AndroidUtilities.dpf2(72), active);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            canvas.drawPath(gutterPath, paint);
            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), AndroidUtilities.lerp(.5f, 1f, active)));
            RectF rect = AndroidUtilities.rectTmp;
            rect.set(cx - pillWidth / 2f, cy - pillHeight / 2f, cx + pillWidth / 2f, cy + pillHeight / 2f);
            canvas.drawRoundRect(rect, pillWidth / 2f, pillWidth / 2f, paint);
        }
    }
}

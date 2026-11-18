package org.telegram.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramProfilePolicy;
import org.telegram.messenger.DevGramProfileRows;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.ActionBar.Theme;

/** A lifted snapshot of the actual row, with stable-ID drop targets and edge scrolling. */
final class DevGramProfileRowDrag extends View {
    private final DevGramProfileEditor editor;
    private final FrameLayout root;
    private final String from;
    private final Bitmap image;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private static final class RowState {
        final int position;
        final float alpha, translation;
        float target = Float.NaN;
        RowState(View row, int position) { this.position = position; alpha = row.getAlpha(); translation = row.getTranslationY(); }
        void restore(View row) { row.animate().cancel(); row.setAlpha(alpha); row.setTranslationY(translation); }
    }
    private final IdentityHashMap<View, RowState> states = new IdentityHashMap<>();
    private final ArrayList<String> order;
    private final int[] positions, heights;
    private final int fromIndex;
    private int insertion;
    private final float fingerOffset, left, width;
    private float fingerY, top, originalTop;
    private String destination;
    private boolean after, stopped;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            int edge = dp(72), bottom = getHeight() - getPaddingBottom();
            float low = AndroidUtilities.statusBarHeight + dp(100);
            float speed = fingerY < low ? -Math.min(18, (low - fingerY) / 4)
                    : fingerY > bottom - edge ? Math.min(18, (fingerY - bottom + edge) / 4) : 0;
            if (speed != 0) originalTop -= editor.host.devgramEditorScroll(dp(speed));
            update(fingerY);
            postOnAnimation(this);
        }
    };

    DevGramProfileRowDrag(DevGramProfileEditor editor, FrameLayout root, View row, String key, float y) {
        super(root.getContext());
        this.editor = editor;
        this.root = root;
        from = key;
        order = editor.host.devgramEditorRows();
        fromIndex = order.indexOf(from);
        insertion = fromIndex;
        positions = new int[order.size()];
        int maximum = -1;
        for (int i = 0; i < order.size(); i++) { positions[i] = editor.host.devgramEditorPosition(order.get(i)); maximum = Math.max(maximum, positions[i]); }
        heights = new int[maximum + 1];
        int[] rowPoint = new int[2], rootPoint = new int[2];
        row.getLocationOnScreen(rowPoint);
        root.getLocationOnScreen(rootPoint);
        originalTop = rowPoint[1] - rootPoint[1];
        left = rowPoint[0] - rootPoint[0];
        width = row.getWidth();
        fingerOffset = y - originalTop;
        int height = Math.max(1, Math.min(row.getHeight(), root.getHeight()));
        image = Bitmap.createBitmap(Math.max(1, row.getWidth()), height, Bitmap.Config.ARGB_8888);
        row.draw(new Canvas(image));
        top = originalTop;
        setElevation(dp(12));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        root.addView(this, new FrameLayout.LayoutParams(-1, -1));
        org.telegram.ui.Components.DevGramStudioControls.haptic(row,HapticFeedbackConstants.LONG_PRESS);
        ViewParent parent = root.getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        update(y);
        postOnAnimation(tick);
    }

    private static int dp(float value) { return AndroidUtilities.dp(value); }

    void update(float y) {
        if (stopped) return;
        if (!DevGramProfilePolicy.allowed(editor.uid, "profile.rows") || !order.equals(editor.host.devgramEditorRows())) { finish(false); return; }
        for (int i = 0; i < order.size(); i++) if (positions[i] != editor.host.devgramEditorPosition(order.get(i))) { finish(false); return; }
        fingerY = y;
        top = y - fingerOffset;
        float centerY = top + image.getHeight() / 2f;
        float nearest = Float.MAX_VALUE;
        destination = null;
        int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        ArrayList<View> rows = editor.host.devgramEditorVisibleRows();
        // A recycled view may now contain a different row. Never transfer its
        // old drag translation or hidden alpha to the new binding.
        for (View row : new ArrayList<>(states.keySet())) {
            RowState state = states.get(row);
            if (!rows.contains(row) || state.position != editor.host.devgramEditorPosition(row)) {
                states.remove(row).restore(row);
            }
        }
        for (View row : rows) {
            int position = editor.host.devgramEditorPosition(row);
            RowState state = states.get(row);
            if (state == null) { state = new RowState(row, position); states.put(row, state); }
            if (position >= 0 && position < heights.length) heights[position] = row.getHeight();
            String key = editor.host.devgramEditorRowKey(row);
            int[] point = new int[2]; row.getLocationOnScreen(point);
            float rowTop = point[1] - origin[1] - row.getTranslationY() + state.translation;
            if (from.equals(key)) {
                row.setAlpha(0);
                originalTop = rowTop;
                continue;
            }
            if (key == null) continue;
            float rowCenter = rowTop + row.getHeight() / 2f;
            float distance = Math.abs(rowCenter - centerY);
            if (distance < nearest) {
                nearest = distance;
                destination = key;
                after = centerY > rowCenter;
            }
        }
        int target = destination == null ? -1 : order.indexOf(destination);
        int next = target < 0 ? fromIndex : target + (after ? 1 : 0) - (target > fromIndex ? 1 : 0);
        next = Math.max(0, Math.min(order.size() - 1, next));
        if (insertion != next) {
            insertion = next;
            org.telegram.ui.Components.DevGramStudioControls.haptic(this, HapticFeedbackConstants.CLOCK_TICK);
        }
        float[] offsets = DevGramProfileRows.dragOffsets(heights, positions, fromIndex, insertion);
        for (View row : rows) {
            RowState state = states.get(row);
            if (from.equals(editor.host.devgramEditorRowKey(row))) continue;
            float offset = state.position >= 0 && state.position < offsets.length ? offsets[state.position] : 0;
            float desired = state.translation + offset;
            // Set a new animation only when the insertion slot changes. Restarting
            // it every frame prevents the neighbours from visibly reaching it.
            if (Float.isNaN(state.target) || Math.abs(state.target - desired) > .5f) {
                state.target = desired;
                row.animate().cancel();
                if (Math.abs(row.getTranslationY() - desired) < .5f) row.setTranslationY(desired);
                else row.animate().translationY(desired).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            }
        }
        invalidate();
    }

    void finish(boolean commit) {
        if (stopped) return;
        stopped = true;
        removeCallbacks(tick);
        java.util.HashMap<String, Float> previous = new java.util.HashMap<>();
        int[] origin = new int[2]; root.getLocationOnScreen(origin);
        for (View row : editor.host.devgramEditorVisibleRows()) {
            int[] point = new int[2]; row.getLocationOnScreen(point);
            previous.put(editor.host.devgramEditorAnimationKey(row), (float) point[1]);
        }
        previous.put(from, origin[1] + top);
        for (View row : states.keySet()) states.get(row).restore(row);
        states.clear();
        ViewParent parent = root.getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
        root.removeView(this);
        image.recycle();
        editor.host.devgramEditorCancelTouch();
        if (commit && destination != null && editor.reorder(from, destination, after)) editor.host.devgramAnimateReorderedRows(previous);
    }

    @Override protected void onDraw(Canvas canvas) {
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, editor.host.getResourceProvider());
        bounds.set(left + dp(4), top, left + width - dp(4), top + image.getHeight());
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, editor.host.getResourceProvider()));
        paint.setShadowLayer(dp(6), 0, dp(3), 0x50000000);
        paint.setAlpha(235);
        canvas.drawRoundRect(bounds, dp(12), dp(12), paint);
        paint.clearShadowLayer();
        canvas.drawBitmap(image, null, bounds, paint);
        if (destination != null) {
            // The indicator belongs below the lifted card and is painted last,
            // so neither its bitmap nor another row can cover the insertion cue.
            float marker = Math.min(getHeight() - dp(8), top + image.getHeight() + dp(8));
            paint.setAlpha(255);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, editor.host.getResourceProvider()));
            bounds.set(dp(16), marker - dp(3), getWidth() - dp(16), marker + dp(3));
            canvas.drawRoundRect(bounds, dp(3), dp(3), paint);
            paint.setColor(accent);
            bounds.set(dp(16), marker - dp(1.5f), getWidth() - dp(16), marker + dp(1.5f));
            canvas.drawRoundRect(bounds, dp(1.5f), dp(1.5f), paint);
        }
    }
}

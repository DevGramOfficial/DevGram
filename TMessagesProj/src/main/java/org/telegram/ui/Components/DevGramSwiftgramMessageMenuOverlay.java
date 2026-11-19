package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import org.telegram.ui.Cells.ChatMessageCell;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramSwiftgramDesign;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

/** Swiftgram ContextController extraction surface. All native menu/reaction/submenu actions stay intact. */
public final class DevGramSwiftgramMessageMenuOverlay extends FrameLayout implements DevGramMessageMenuSurface {
    private final View menu, shade;
    private final ImageView backdrop;
    private final FrameLayout preview, scrollContent;
    private final ScrollView scrollHost;
    private final ArrayList<Bitmap> snapshots = new ArrayList<>();
    private final java.util.HashSet<Bitmap> copying = new java.util.HashSet<>();
    private final Runnable closeAction;
    private final int[] position = new int[2];
    private final Rect sourceBounds = new Rect(), screenBounds = new Rect();
    private final int requestedX, requestedY;
    private boolean closing, released, entered;

    public DevGramSwiftgramMessageMenuOverlay(Context context, View screen, List<View> messages,
                                             View menu, int menuX, int menuY, Runnable closeAction) {
        super(context);
        this.menu = menu; this.closeAction = closeAction; requestedX = menuX; requestedY = menuY;
        setClipChildren(false); setFocusable(true); setClickable(true);
        screen.getLocationInWindow(position);
        screenBounds.set(position[0], position[1], position[0] + screen.getWidth(), position[1] + screen.getHeight());
        android.app.Activity activity = AndroidUtilities.findActivity(context);
        backdrop = capture(activity == null ? screen : activity.getWindow().getDecorView(), true);
        if (backdrop != null) { addView(backdrop, new LayoutParams(-1, -1)); backdrop.setAlpha(0); }
        shade = new View(context);
        shade.setBackgroundColor(Theme.isCurrentThemeDark() ? 0x4d000000 : 0x33000a26);
        shade.setAlpha(0);
        addView(shade, new LayoutParams(-1, -1));
        scrollHost = new ScrollView(context);
        scrollHost.setOverScrollMode(OVER_SCROLL_NEVER);
        scrollHost.setClipToPadding(false);
        scrollContent = new FrameLayout(context);
        scrollContent.setOnClickListener(v -> closeAnimated());
        scrollHost.addView(scrollContent, new ScrollView.LayoutParams(-1, -2));
        addView(scrollHost, new LayoutParams(-1, -1));
        preview = new FrameLayout(context);
        preview.setClipChildren(false); preview.setClipToPadding(false);
        ArrayList<View> sources = new ArrayList<>();
        ArrayList<Rect> visibleBounds = new ArrayList<>();
        boolean first = true;
        if (messages != null) for (View message : messages) {
            if (message == null || message.getWidth() == 0 || message.getHeight() == 0) continue;
            message.getLocationInWindow(position);
            Rect bounds = new Rect(position[0], position[1], position[0] + message.getWidth(), position[1] + message.getHeight());
            if (message instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) message;
                // Extract the bubble, not the RecyclerView row and its wallpaper.
                bounds.set(position[0] + cell.getBackgroundDrawableLeft(), position[1] + cell.getBackgroundDrawableTop(),
                        position[0] + cell.getBackgroundDrawableRight(), position[1] + cell.getBackgroundDrawableBottom());
            }
            if (!Rect.intersects(bounds, screenBounds)) continue;
            sources.add(message); visibleBounds.add(bounds);
            if (first) { sourceBounds.set(bounds); first = false; } else sourceBounds.union(bounds);
        }
        for (int i = 0; i < sources.size(); i++) {
            View message = sources.get(i);
            Rect bounds = visibleBounds.get(i);
            message.getLocationInWindow(position);
            final int cropX = bounds.left - position[0], cropY = bounds.top - position[1];
            FrameLayout copy = new FrameLayout(context);
            copy.setClipChildren(true);
            if (message instanceof ChatMessageCell) {
                ChatMessageCell extracted = ((ChatMessageCell) message).createContextPreview();
                extracted.setOnTouchListener((v, event) -> true);
                LayoutParams cellParams = new LayoutParams(message.getWidth(), message.getHeight());
                cellParams.leftMargin = -cropX; cellParams.topMargin = -cropY;
                copy.addView(extracted, cellParams);
            } else {
                View surface = new View(context) {
                    @Override protected void onDraw(Canvas canvas) {
                        int save = canvas.save(); canvas.translate(-cropX, -cropY);
                        message.draw(canvas); canvas.restoreToCount(save);
                    }
                };
                copy.addView(surface, new LayoutParams(-1, -1));
            }
            LayoutParams params = new LayoutParams(bounds.width(), bounds.height());
            params.leftMargin = bounds.left - sourceBounds.left; params.topMargin = bounds.top - sourceBounds.top;
            preview.addView(copy, params);
        }
        preview.setPivotX(0); preview.setPivotY(0);
        scrollContent.addView(preview, new LayoutParams(Math.max(1, sourceBounds.width()), Math.max(1, sourceBounds.height())));
        if (menu.getParent() instanceof ViewGroup) ((ViewGroup) menu.getParent()).removeView(menu);
        scrollContent.addView(menu, new LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));
        menu.setAlpha(0); preview.setAlpha(0);
    }

    private ImageView capture(View source, boolean blur) {
        if (source == null) return null;
        source.getLocationInWindow(position);
        return capture(source, blur, new Rect(position[0], position[1], position[0] + source.getWidth(), position[1] + source.getHeight()));
    }

    private ImageView capture(View source, boolean blur, Rect bounds) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return null;
        Bitmap bitmap = null;
        try {
            // A backdrop needs no full-resolution allocation. Tall message previews also have a fixed pixel budget.
            float factor = blur ? .125f : Math.min(1, (float) Math.sqrt(2_000_000d / Math.max(1, (long) sourceBounds.width() * sourceBounds.height())));
            bitmap = Bitmap.createBitmap(Math.max(1, Math.round(bounds.width() * factor)), Math.max(1, Math.round(bounds.height() * factor)), Bitmap.Config.ARGB_8888);
            snapshots.add(bitmap);
            ImageView view = new ImageView(getContext());
            view.setImageBitmap(bitmap); view.setScaleType(ImageView.ScaleType.FIT_XY);
            android.app.Activity activity = AndroidUtilities.findActivity(getContext());
            if (Build.VERSION.SDK_INT >= 26 && activity != null) {
                // View.draw cannot capture hardware-only chat layers or bitmaps.
                // PixelCopy reads the underlying activity surface, not this popup.
                Bitmap target = bitmap;
                copying.add(target);
                Window window = activity.getWindow();
                PixelCopy.request(window, new Rect(bounds), target, result -> {
                    copying.remove(target);
                    if (released) { if (!target.isRecycled()) target.recycle(); return; }
                    if (target.isRecycled()) return;
                    if (result == PixelCopy.SUCCESS) {
                        if (blur) Utilities.stackBlurBitmap(target, 8);
                        view.invalidate();
                    }
                }, new Handler(Looper.getMainLooper()));
            } else {
                source.getLocationInWindow(position);
                Canvas canvas = new Canvas(bitmap); canvas.scale(factor, factor);
                canvas.translate(position[0] - bounds.left, position[1] - bounds.top); source.draw(canvas);
                if (blur) Utilities.stackBlurBitmap(bitmap, 8);
            }
            return view;
        } catch (RuntimeException | OutOfMemoryError error) {
            copying.remove(bitmap);
            if (bitmap != null && !snapshots.contains(bitmap)) bitmap.recycle();
            return null;
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        int top = AndroidUtilities.statusBarHeight, bottom = AndroidUtilities.navigationBarHeight;
        WindowInsets insets = getRootWindowInsets();
        if (insets != null) { top = Math.max(top, insets.getSystemWindowInsetTop()); bottom = Math.max(bottom, insets.getSystemWindowInsetBottom()); }
        int gap = AndroidUtilities.dp(8);
        menu.measure(MeasureSpec.makeMeasureSpec(Math.max(1, width - 2 * gap), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(Math.max(1, (int) ((height - top - bottom - gap * 2) * .65f)), MeasureSpec.AT_MOST));
        menu.getLayoutParams().height = menu.getMeasuredHeight();
        int viewport = Math.max(1, height - top - bottom - gap * 2);
        DevGramSwiftgramContextLayout.Result placement = DevGramSwiftgramContextLayout.placeScrollable(width, 0, viewport, gap,
                sourceBounds.left, sourceBounds.top - top - gap, sourceBounds.width(), sourceBounds.height(),
                requestedX, menu.getMeasuredWidth(), menu.getMeasuredHeight());
        int contentHeight = Math.max(viewport, (int) Math.ceil(placement.menuY + menu.getMeasuredHeight() + gap));
        // ScrollView measures its direct child with UNSPECIFIED height and ignores
        // layoutParams.height; the minimum must include both message and actions.
        scrollContent.setMinimumHeight(contentHeight);
        scrollContent.getLayoutParams().height = contentHeight;
        scrollHost.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(viewport, MeasureSpec.EXACTLY));
        preview.measure(MeasureSpec.makeMeasureSpec(Math.max(1, sourceBounds.width()), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(1, sourceBounds.height()), MeasureSpec.EXACTLY));
        if (backdrop != null) backdrop.measure(widthSpec, heightSpec);
        shade.measure(widthSpec, heightSpec);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        getLocationInWindow(position);
        if (backdrop != null) backdrop.layout(0, 0, getWidth(), getHeight());
        shade.layout(0, 0, getWidth(), getHeight());
        int safeTop = AndroidUtilities.statusBarHeight, safeBottom = AndroidUtilities.navigationBarHeight;
        WindowInsets insets = getRootWindowInsets();
        if (insets != null) { safeTop = Math.max(safeTop, insets.getSystemWindowInsetTop()); safeBottom = Math.max(safeBottom, insets.getSystemWindowInsetBottom()); }
        int gap = AndroidUtilities.dp(8);
        int scrollTop = safeTop + gap, viewport = Math.max(1, getHeight() - safeBottom - gap - scrollTop);
        scrollHost.layout(0, scrollTop, getWidth(), scrollTop + viewport);
        DevGramSwiftgramContextLayout.Result result = DevGramSwiftgramContextLayout.placeScrollable(getWidth(), 0,
                viewport, gap, sourceBounds.left - position[0], sourceBounds.top - position[1] - scrollTop,
                sourceBounds.width(), sourceBounds.height(), requestedX - position[0], menu.getMeasuredWidth(), menu.getMeasuredHeight());
        int x = Math.round(result.previewX), y = Math.round(result.previewY);
        preview.layout(x, y, x + preview.getMeasuredWidth(), y + preview.getMeasuredHeight());
        x = Math.round(result.menuX); y = Math.round(result.menuY);
        menu.layout(x, y, x + menu.getMeasuredWidth(), y + menu.getMeasuredHeight());
        if (!entered) {
            entered = true;
            scrollHost.scrollTo(0, Math.max(0, Math.round(result.menuY + menu.getMeasuredHeight() + gap - viewport)));
            enter(result.previewScale);
        }
    }

    private void enter(float targetScale) {
        boolean animate = DevGramSwiftgramDesign.section("animations");
        long duration = animate ? 420 : 0;
        if (backdrop != null) backdrop.animate().alpha(1).setDuration(animate ? 200 : 0).start();
        shade.animate().alpha(1).setDuration(animate ? 200 : 0).start();
        preview.setAlpha(1);
        preview.setTranslationX(sourceBounds.left - position[0] - preview.getLeft());
        preview.setTranslationY(sourceBounds.top - position[1] - scrollHost.getTop() + scrollHost.getScrollY() - preview.getTop());
        preview.setScaleX(1); preview.setScaleY(1);
        preview.animate().translationX(0).translationY(0).scaleX(targetScale).scaleY(targetScale)
                .setDuration(duration).setInterpolator(DevGramSwiftgramSpring::context).start();
        menu.setPivotX(Math.max(0, Math.min(menu.getWidth(), sourceBounds.centerX() - position[0] - menu.getLeft())));
        menu.setPivotY(requestedY < sourceBounds.centerY() ? menu.getHeight() : 0);
        menu.setScaleX(.1f); menu.setScaleY(.1f);
        menu.animate().alpha(1).scaleX(1).scaleY(1).setDuration(duration).setInterpolator(DevGramSwiftgramSpring::context).start();
    }

    @Override public boolean isClosing() { return closing; }
    @Override public void closeAnimated() {
        if (closing) return;
        closing = true;
        long duration = DevGramSwiftgramDesign.section("animations") ? 200 : 0;
        if (backdrop != null) backdrop.animate().alpha(0).setDuration(duration).start();
        shade.animate().alpha(0).setDuration(duration).start();
        preview.animate().alpha(0).translationX(sourceBounds.left - position[0] - preview.getLeft())
                .translationY(sourceBounds.top - position[1] - scrollHost.getTop() + scrollHost.getScrollY() - preview.getTop()).scaleX(1).scaleY(1).setDuration(duration).start();
        menu.animate().alpha(0).scaleX(.1f).scaleY(.1f).setDuration(duration).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                menu.animate().setListener(null);
                if (closeAction != null) closeAction.run();
            }
        }).start();
    }

    @Override public void release() {
        if (released) return;
        released = true;
        menu.animate().setListener(null).cancel(); preview.animate().cancel(); shade.animate().cancel();
        if (backdrop != null) { backdrop.animate().cancel(); backdrop.setImageDrawable(null); }
        preview.removeAllViews();
        for (Bitmap bitmap : snapshots) if (!copying.contains(bitmap) && !bitmap.isRecycled()) bitmap.recycle();
        snapshots.clear();
    }
    @Override protected void onDetachedFromWindow() { release(); super.onDetachedFromWindow(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) closeAnimated();
        return true;
    }
}

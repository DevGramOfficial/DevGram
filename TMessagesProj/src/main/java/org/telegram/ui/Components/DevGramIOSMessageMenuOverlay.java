package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import org.telegram.ui.ActionBar.Theme;

/**
 * Full-screen iOS/Cherrygram message context-menu surface. The menu itself remains
 * Telegram's production ActionBarPopupWindowLayout, so every action and swipe-back
 * submenu keeps its original handler; this class owns only the presentation layer.
 */
public class DevGramIOSMessageMenuOverlay extends FrameLayout {
    private final ArrayList<ImageView> messageCopies = new ArrayList<>();
    private final View menu;
    private final Runnable closeAction;
    private final View screenCopy;
    private boolean closing;

    public DevGramIOSMessageMenuOverlay(Context context, View screen, List<View> messages,
                                        View menu, int menuX, int menuY,
                                        boolean nativeBlur, Runnable closeAction) {
        super(context);
        this.menu = menu;
        this.closeAction = closeAction;
        setClipChildren(false);
        setFocusable(true);
        setClickable(true);

        screenCopy = snapshotView(screen, true);
        if (screenCopy != null) {
            screenCopy.setAlpha(0f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                screenCopy.setRenderEffect(RenderEffect.createBlurEffect(dp(18), dp(18), Shader.TileMode.CLAMP));
            }
            addView(screenCopy, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        View shade = new View(context);
        shade.setBackground(new ColorDrawable(Theme.isCurrentThemeDark() ? 0x72000000 : 0x52000000));
        shade.setAlpha(0f);
        shade.setTag("shade");
        addView(shade, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        int[] location = new int[2];
        if (messages != null) {
            for (View source : messages) {
                if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) continue;
                ImageView copy = (ImageView) snapshotView(source, false);
                if (copy == null) continue;
                source.getLocationInWindow(location);
                FrameLayout.LayoutParams params = LayoutHelper.createFrame(source.getWidth(), source.getHeight(), Gravity.TOP | Gravity.LEFT);
                params.leftMargin = location[0];
                params.topMargin = location[1];
                copy.setLayoutParams(params);
                copy.setPivotX(source.getWidth() / 2f);
                copy.setPivotY(source.getHeight() / 2f);
                copy.setAlpha(0f);
                copy.setScaleX(.96f);
                copy.setScaleY(.96f);
                addView(copy);
                messageCopies.add(copy);
            }
        }

        if (menu.getParent() instanceof ViewGroup) {
            ((ViewGroup) menu.getParent()).removeView(menu);
        }
        FrameLayout.LayoutParams menuParams = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        menuParams.leftMargin = Math.max(dp(6), menuX);
        menuParams.topMargin = Math.max(dp(8), menuY);
        addView(menu, menuParams);
        menu.setPivotX(menu.getMeasuredWidth() / 2f);
        menu.setPivotY(dp(24));
        menu.setAlpha(0f);
        menu.setScaleX(.86f);
        menu.setScaleY(.86f);

        post(this::startEnterAnimation);
    }

    private View snapshotView(View source, boolean fill) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            source.draw(canvas);
            ImageView image = new ImageView(getContext());
            image.setImageBitmap(bitmap);
            image.setScaleType(fill ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.CENTER);
            return image;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void startEnterAnimation() {
        if (screenCopy != null) screenCopy.animate().alpha(1f).setDuration(180).start();
        View shade = findViewWithTag("shade");
        if (shade != null) shade.animate().alpha(1f).setDuration(220).start();
        for (ImageView copy : messageCopies) {
            copy.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(-dp(2))
                    .setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
        menu.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
    }

    public void closeAnimated() {
        if (closing) return;
        closing = true;
        if (screenCopy != null) screenCopy.animate().alpha(0f).setDuration(180).start();
        View shade = findViewWithTag("shade");
        if (shade != null) shade.animate().alpha(0f).setDuration(180).start();
        for (ImageView copy : messageCopies) {
            copy.animate().alpha(0f).scaleX(.96f).scaleY(.96f).translationY(0)
                    .setDuration(220).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }
        menu.animate().alpha(0f).scaleX(.9f).scaleY(.9f).setDuration(220)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        menu.animate().setListener(null);
                        if (closeAction != null) closeAction.run();
                    }
                }).start();
    }

    public boolean isClosing() {
        return closing;
    }

    public void release() {
        recycleBitmap(screenCopy);
        for (ImageView copy : messageCopies) {
            recycleBitmap(copy);
        }
        messageCopies.clear();
    }

    private void recycleBitmap(View view) {
        if (!(view instanceof ImageView)) return;
        ImageView imageView = (ImageView) view;
        if (imageView.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) imageView.getDrawable()).getBitmap();
            imageView.setImageDrawable(null);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            closeAnimated();
        }
        return true;
    }
}

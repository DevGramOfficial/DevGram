package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.View;

import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;

import org.telegram.messenger.MessagesController;

// Material 3 widgets and feature gates ported from exteraGram's getNew*Style implementation.
public class DevGramMaterial3 {

    // DevGram design tokens. Keeping these here prevents navigation, glass, FABs and
    // chat chrome from slowly drifting into unrelated radius/spacing systems.
    public static final int RADIUS_LARGE_DP = 28;
    public static final int RADIUS_MEDIUM_DP = 16;
    public static final int RADIUS_SMALL_DP = 12;
    public static final int HORIZONTAL_INSET_DP = 8;
    public static final int CHAT_HEADER_FADE_DP = 78;
    public static final int CHAT_HEADER_FADE_ZONE_DP = 42;
    public static final float GLASS_ALPHA_LIGHT = .90f;
    public static final float GLASS_ALPHA_DARK = .93f;

    public static boolean enabled() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_md3", true);
    }

    public static boolean loadingEnabled() {
        return enabled() && MessagesController.getGlobalMainSettings().getBoolean("dg_md3_progress", true);
    }

    public static boolean navigationEnabled() {
        return enabled() && MessagesController.getGlobalMainSettings().getBoolean("dg_md3_bottomnav", false);
    }

    public static boolean profileButtonsEnabled() {
        return enabled() && MessagesController.getGlobalMainSettings().getBoolean("dg_md3_profile", false);
    }

    public static boolean fabEnabled() {
        return enabled() && MessagesController.getGlobalMainSettings().getBoolean("dg_md3_fab", false);
    }

    public static final int TRANSITION_DEFAULT = 0;
    public static final int TRANSITION_AOSP = 1;
    public static final int TRANSITION_SPRING = 2;

    public static int transitionAnimation() {
        int value = MessagesController.getGlobalMainSettings().getInt("dg_transitionAnimation", TRANSITION_SPRING);
        return value < TRANSITION_DEFAULT || value > TRANSITION_SPRING ? TRANSITION_SPRING : value;
    }

    public static float springTransitionInterpolation(float progress) {
        if (progress <= 0f) {
            return 0f;
        }
        if (progress >= 1f) {
            return 1f;
        }
        return (float) (1.0 - Math.exp(-7.25 * progress) * Math.cos(10.5 * progress));
    }

    // DevGram: нижний таб-бар в режиме дизайна iOS — та же плавающая скруглённая капсула на
    // всю ширину, что и в MD3-режиме (сверено по реальным скриншотам Swiftgram: там именно
    // плавающая капсула с отступами от краёв, а не плоская стыкованная полоса).
    public static boolean iosNavigation() {
        return org.telegram.messenger.DevGramConfig.getDesignMode() == org.telegram.messenger.DevGramConfig.DESIGN_MODE_IOS;
    }

    public static int mainTabsHeightDp() {
        return iosNavigation() ? 64 : (navigationEnabled() ? 64 : 72);
    }

    // Материаловым виджетам нужна тема Material3 в контексте, иначе не заинфлейтятся/упадут.
    public static Context themed(Context base) {
        return new ContextThemeWrapper(base, com.google.android.material.R.style.Theme_Material3_DayNight);
    }

    // Обёртка над круговым M3-индикатором: пересоздаёт drawable при смене размера/цвета, сам анимируется.
    public static class CircularHost {
        private IndeterminateDrawable<CircularProgressIndicatorSpec> drawable;
        private int lastSize = -1, lastColor = 0, lastThickness = -1;
        private final View host;

        public CircularHost(View host) {
            this.host = host;
        }

        private void ensure(int sizePx, int thicknessPx, int color) {
            if (drawable != null && sizePx == lastSize && color == lastColor && thicknessPx == lastThickness) {
                return;
            }
            lastSize = sizePx;
            lastColor = color;
            lastThickness = thicknessPx;
            Context ctx = themed(host.getContext());
            CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(ctx, null);
            spec.indicatorSize = sizePx;
            spec.trackThickness = thicknessPx;
            spec.indicatorInset = 0;
            spec.indicatorColors = new int[]{color};
            spec.trackColor = (color & 0x00FFFFFF) | 0x22000000;
            drawable = IndeterminateDrawable.createCircularDrawable(ctx, spec);
            drawable.setVisible(true, false);
            drawable.setCallback(new android.graphics.drawable.Drawable.Callback() {
                @Override public void invalidateDrawable(android.graphics.drawable.Drawable who) { host.invalidate(); }
                @Override public void scheduleDrawable(android.graphics.drawable.Drawable who, Runnable what, long when) {
                    host.postDelayed(what, when - android.os.SystemClock.uptimeMillis());
                }
                @Override public void unscheduleDrawable(android.graphics.drawable.Drawable who, Runnable what) {
                    host.removeCallbacks(what);
                }
            });
        }

        // Нарисовать индикатор с центром (cx,cy). Возвращает false, если не удалось (тогда рисуй по-старому).
        public boolean draw(Canvas canvas, float cx, float cy, int sizePx, int thicknessPx, int color, int alpha) {
            try {
                ensure(sizePx, thicknessPx, color);
                drawable.setAlpha(alpha);
                int half = sizePx / 2;
                drawable.setBounds((int) (cx - half), (int) (cy - half), (int) (cx + half), (int) (cy + half));
                drawable.draw(canvas);
                return true;
            } catch (Throwable e) {
                return false;
            }
        }
    }
}

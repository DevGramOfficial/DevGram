package org.telegram.ui.Components.devgram;

import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.DevGramPlugins;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.Components.GroupCallPip;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.ViewPagerActivity;

import java.util.function.BooleanSupplier;

/**
 * Снимает наш колбэк «назад», когда на главном экране закрывать нечего:
 * тогда система сама показывает анимацию выхода на рабочий стол.
 */
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public final class PredictiveBackNavigationHelper implements ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnWindowFocusChangeListener {

    private static final Class<?>[] BACK_HANDLER_CLASSES = {
        LaunchActivity.class, ActionBarLayout.class, BaseFragment.class, ViewPagerActivity.class, MainTabsActivity.class, DialogsActivity.class
    };

    private final LaunchActivity activity;
    private final OnBackInvokedCallback callback;
    private final BooleanSupplier canUseSystemBack;
    private final View decorView;
    private boolean detached;
    private boolean gestureInProgress;
    private boolean registered;

    public PredictiveBackNavigationHelper(LaunchActivity activity, OnBackInvokedCallback callback, BooleanSupplier blocked) {
        this.activity = activity;
        this.callback = callback;
        this.decorView = activity.getWindow().getDecorView();
        this.canUseSystemBack = () -> !blocked.getAsBoolean() && canUseSystemBack(activity);
        decorView.getViewTreeObserver().addOnPreDrawListener(this);
        decorView.getViewTreeObserver().addOnWindowFocusChangeListener(this);
        update();
    }

    private static boolean canUseSystemBack(LaunchActivity activity) {
        INavigationLayout layout = activity.getActionBarLayout();
        if (!(layout instanceof ActionBarLayout) || !((ActionBarLayout) layout).canUseSystemBack()
                || hasFragments(activity.getLayersActionBarLayout()) || hasFragments(activity.getRightActionBarLayout())
                || DevGramPlugins.hasHookOn("onBackPressed", BACK_HANDLER_CLASSES)
                || GroupCallPip.onBackPressed(false) || !activity.onBackPressed(false)) {
            return false;
        }
        BaseFragment fragment = layout.getLastFragment();
        if (fragment instanceof MainTabsActivity) {
            return ((MainTabsActivity) fragment).canUseSystemBack();
        }
        return fragment instanceof DialogsActivity && ((DialogsActivity) fragment).canUseSystemBack();
    }

    private static boolean hasFragments(INavigationLayout layout) {
        return layout != null && !layout.getFragmentStack().isEmpty();
    }

    public static void invalidate() {
        LaunchActivity activity = LaunchActivity.instance;
        if (activity != null) {
            activity.getWindow().getDecorView().invalidate();
        }
    }

    public void setGestureInProgress(boolean inProgress) {
        gestureInProgress = inProgress;
        if (!inProgress) {
            decorView.invalidate();
        }
    }

    private void update() {
        if (detached || gestureInProgress || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        boolean system = canUseSystemBack.getAsBoolean();
        if (registered == !system) {
            return;
        }
        if (system) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
        } else {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        }
        registered = !system;
    }

    @Override
    public boolean onPreDraw() {
        update();
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            update();
        }
    }

    public void detach() {
        detached = true;
        ViewTreeObserver observer = decorView.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(this);
            observer.removeOnWindowFocusChangeListener(this);
        }
        if (registered) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
            registered = false;
        }
    }
}

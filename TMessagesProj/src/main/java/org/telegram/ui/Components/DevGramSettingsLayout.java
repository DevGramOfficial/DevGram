package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;

import java.util.ArrayList;

/** Shared exteraGram-style glass header used by DevGram settings screens. */
public class DevGramSettingsLayout extends FrameLayout implements Theme.Colorable {
    private final ActionBar actionBar;
    private final UniversalRecyclerView listView;
    private final Theme.ResourcesProvider resourcesProvider;
    private final BlurredBackgroundSourceColor sourceColor = new BlurredBackgroundSourceColor();
    private final @Nullable DownscaleScrollableNoiseSuppressor noiseSuppressor;
    private final @Nullable BlurredBackgroundSourceRenderNode glassSource;
    private final ArrayList<RectF> blurPositions = new ArrayList<>(1);
    private final RectF actionBarPosition = new RectF();
    private final IBlur3Capture blurCapture;
    private final ChatActivityFadeView fadeView;
    private ValueAnimator fadeAnimator;
    private boolean fadeShown;
    private float fadeAlpha;

    public DevGramSettingsLayout(Context context, ActionBar actionBar, UniversalRecyclerView listView,
                                 Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.actionBar = actionBar;
        this.listView = listView;
        this.resourcesProvider = resourcesProvider;
        blurPositions.add(actionBarPosition);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            noiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            glassSource = new BlurredBackgroundSourceRenderNode(sourceColor);
        } else {
            noiseSuppressor = null;
            glassSource = null;
        }

        updateColors();
        actionBar.setCastShadows(false);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setAddToContainer(false);
        if (actionBar.menu == null) {
            actionBar.createMenu();
        }

        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(
                glassSource != null ? glassSource : sourceColor);
        factory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        factory.setSourceRootView(new ViewPositionWatcher(this), this);
        actionBar.setGlassPadding(dp(12));
        actionBar.setGlassTitleTextSize(20);
        actionBar.setupGlass(factory, BlurredBackgroundProviderImpl.topPanelChatActivity(resourcesProvider));
        actionBar.setDrawGlassMiddlePill(false);
        actionBar.setGlassShadowAlpha(0f);
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector, resourcesProvider), false);

        listView.adapter.setApplyBackground(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight(), 0, 0);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        fadeView = new ChatActivityFadeView(context);
        fadeView.setupColorKey(Theme.key_windowBackgroundGray);
        fadeView.setFadeTopAlpha(0);
        addView(fadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.FILL_HORIZONTAL | Gravity.TOP));

        blurCapture = new ViewGroupPartRenderer(listView, this, listView::drawChild);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (noiseSuppressor != null) {
                    noiseSuppressor.onScrolled(dx, dy);
                    invalidateBlur();
                }
                updateHeaderFade(true);
            }
        });
        listView.addEdgeEffectListener(() -> listView.postOnAnimation(this::invalidateBlur));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateTopFade();
        updateHeaderFade(false);
    }

    private void updateTopFade() {
        fadeView.setFadeZoneTop(actionBar.getMeasuredHeight() + dp(42));
        fadeView.setFadeHeightTop(dp(78));
        fadeView.setTopFadeColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        fadeView.setFadeTopAlpha(Math.round(fadeAlpha * 255f));
    }

    private void updateHeaderFade(boolean animated) {
        boolean shown = listView.canScrollVertically(-1);
        if (fadeShown == shown) {
            return;
        }
        fadeShown = shown;
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
            fadeAnimator = null;
        }
        float target = shown ? 1f : 0f;
        if (!animated) {
            setFadeAlpha(target);
            return;
        }
        fadeAnimator = ValueAnimator.ofFloat(fadeAlpha, target);
        fadeAnimator.setDuration(320);
        fadeAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        fadeAnimator.addUpdateListener(animation -> setFadeAlpha((float) animation.getAnimatedValue()));
        fadeAnimator.start();
    }

    private void setFadeAlpha(float alpha) {
        fadeAlpha = alpha;
        fadeView.setFadeTopAlpha(Math.round(alpha * 255f));
    }

    private void invalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || noiseSuppressor == null
                || !SharedConfig.chatBlurEnabled() || getMeasuredWidth() <= 0 || getMeasuredHeight() <= 0) {
            return;
        }
        int extra = dp(48);
        actionBarPosition.set(0, -extra, getMeasuredWidth(), actionBar.getMeasuredHeight() + extra);
        noiseSuppressor.setupRenderNodes(blurPositions, 1);
        noiseSuppressor.invalidateResultRenderNodes(blurCapture, getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && noiseSuppressor != null && glassSource != null) {
            invalidateBlur();
            if (!glassSource.inRecording()) {
                Canvas recording = glassSource.beginRecording(getMeasuredWidth(), getMeasuredHeight());
                recording.drawColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
                if (SharedConfig.chatBlurEnabled()) {
                    noiseSuppressor.draw(recording, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
                }
                glassSource.endRecording();
            }
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public void updateColors() {
        int color = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
        setBackgroundColor(color);
        sourceColor.setColor(color);
        if (fadeView != null) {
            fadeView.updateColors();
            updateTopFade();
        }
    }
}

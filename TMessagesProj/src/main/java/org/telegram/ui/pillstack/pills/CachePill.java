package org.telegram.ui.pillstack.pills;

import org.telegram.ui.pillstack.PillStackConfig;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PillStackPreferencesActivity;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** DevGram: порт CachePill из exteraGram — размер кэша с кольцевым индикатором занятости памяти. */
@SuppressLint("ViewConstructor")
public class CachePill extends BasePill implements NotificationCenter.NotificationCenterDelegate {
    private static final AtomicLong lastKnownCacheSize = new AtomicLong(-1);
    private static float lastKnownProgress = -1.0f;
    private final AtomicBoolean calculating = new AtomicBoolean(false);
    private final ImageView iconView;
    private final LinearLayout layout;
    private final StorageProgressDrawable progressDrawable;
    private final AnimatedTextView textView;

    @Override
    public long getRefreshInterval() { return 180000L; }

    public CachePill(Context context, Theme.ResourcesProvider rp) {
        super(context, rp);
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumWidth(AndroidUtilities.dp(48));
        layout.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(8), 0);
        addView(layout, LayoutHelper.createFrame(-2, 28, (org.telegram.messenger.LocaleController.isRTL ? 3 : 5) | 16));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        layout.addView(iconView, LayoutHelper.createLinear(16, 16, 16, 0, 0, 6, 0));
        progressDrawable = new StorageProgressDrawable(iconView);
        iconView.setImageDrawable(progressDrawable);

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(AndroidUtilities.dp(13));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setIncludeFontPadding(false);
        textView.adaptWidth = true;
        layout.addView(textView, LayoutHelper.createLinear(-2, -2, 16));

        setLoadingTargetView(layout);
        updateColors();
        ScaleStateListAnimator.apply(layout);

        if (lastKnownCacheSize.get() != -1 && !isRefreshDue()) {
            setData(lastKnownCacheSize.get(), lastKnownProgress, false);
        } else {
            iconView.setVisibility(GONE);
            textView.setVisibility(GONE);
        }
    }

    @Override
    public int getPillId() { return PillStackConfig.CACHE; }

    @Override
    public void onUpdateData(boolean forceRefresh) {
        boolean unknown = lastKnownCacheSize.get() == -1;
        if ((forceRefresh || unknown || isRefreshDue()) && calculating.compareAndSet(false, true)) {
            if (forceRefresh || unknown) CacheControlActivity.resetCalculatedTotalSIze();
            startLoading();
            ImageLoader.getInstance().checkMediaPaths(() ->
                    CacheControlActivity.calculateTotalSize(total ->
                            CacheControlActivity.getDeviceTotalSize((deviceTotal, deviceFree) -> {
                                float progress = deviceTotal > 0 ? (deviceTotal - deviceFree) / (float) deviceTotal : 0f;
                                lastKnownCacheSize.set(total);
                                lastKnownProgress = progress;
                                calculating.set(false);
                                setData(total, progress, true);
                            })));
        }
    }

    private void setData(long size, float progress, boolean animated) {
        stopLoading();
        String fileSize = AndroidUtilities.formatFileSize(size);
        if (animated && (textView.getText() == null || !TextUtils.equals(textView.getText(), fileSize) || textView.getVisibility() == GONE)) {
            animateSizeChange();
        }
        textView.setText(fileSize, animated);
        progressDrawable.setProgress(progress, animated);
        iconView.setVisibility(VISIBLE);
        textView.setVisibility(VISIBLE);
        markDataUpdated();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        onUpdateData(PillStackConfig.checkAndClearPendingUpdate(getPillId()));
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pillStackSettingsChanged);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pillStackSettingsChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.pillStackSettingsChanged && PillStackConfig.shouldUpdatePill(args, getPillId())) {
            PillStackConfig.checkAndClearPendingUpdate(getPillId());
            onUpdateData(true);
        }
    }

    @Override
    public void onPillClicked() { openCacheSettings(); }

    @Override
    public boolean onPillLongClicked() {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) return false;
        ItemOptions.makeOptions(fragment, this)
                .add(R.drawable.msg2_data, DevGramLocale.text("Использование памяти"), this::openCacheSettings)
                .addGap()
                .add(R.drawable.msg_retry, DevGramLocale.text("Обновить"), () -> onUpdateData(true))
                .add(R.drawable.msg_settings, DevGramLocale.text("Настройки"), () -> fragment.presentFragment(new PillStackPreferencesActivity()))
                .setDrawScrim(false)
                .setDimAlpha(0)
                .show();
        return true;
    }

    private void openCacheSettings() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) fragment.presentFragment(new CacheControlActivity());
    }

    @Override
    public void updateColors() {
        int color = getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.75f);
        layout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                Theme.isCurrentThemeDark() ? getThemedColor(Theme.key_windowBackgroundWhite) : Theme.multAlpha(color, 0.09f),
                Theme.multAlpha(color, 0.1f)));
        textView.setTextColor(color);
        progressDrawable.setColor(color);
        updateLoadingColors();
    }

    public static class StorageProgressDrawable extends Drawable {
        private final AnimatedFloat animatedProgress;
        private int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;
        private final RectF rectF = new RectF();

        public StorageProgressDrawable(View view) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            animatedProgress = new AnimatedFloat(view, 650L, CubicBezierInterpolator.EASE_OUT_QUINT);
        }

        public void setProgress(float value, boolean animated) {
            progress = Math.max(0.05f, Math.min(value, 1.0f));
            if (!animated) animatedProgress.force(progress);
            invalidateSelf();
        }

        public void setColor(int c) { color = c; invalidateSelf(); }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            float size = Math.min(w, h) - AndroidUtilities.dp(2);
            float dx = (w - size) / 2f, dy = (h - size) / 2f;
            rectF.set(dx, dy, dx + size, dy + size);
            float p = animatedProgress.set(progress);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setColor(color);
            paint.setAlpha(50);
            canvas.drawCircle(w / 2f, h / 2f, size / 2f, paint);
            paint.setAlpha(255);
            canvas.drawArc(rectF, -90f, p * 360f, false, paint);
        }

        @Override public int getOpacity() { return -3; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    }
}

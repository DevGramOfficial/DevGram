package org.telegram.ui.pillstack.pills;

import org.telegram.ui.pillstack.PillStackConfig;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PillStackPreferencesActivity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicReference;

/** DevGram: порт RatePill из exteraGram — крипто/валютный виджет со значением курса и выбором целевой валюты. */
@SuppressLint("ViewConstructor")
public abstract class RatePill extends BasePill implements NotificationCenter.NotificationCenterDelegate {

    public static final class RateCache {
        final AtomicReference<String> cachedPrice = new AtomicReference<>();
        final AtomicReference<String> cachedCurrency = new AtomicReference<>();
    }

    private final ColoredBackground background;
    private final String baseCurrency;
    private final RateCache cache;
    private final int iconResId;
    private final int scale;
    private final ImageView iconView;
    private final LinearLayout layout;
    private final AnimatedTextView textView;
    private boolean requestInFlight;

    public abstract String getTargetSelection();
    public abstract void setTargetSelection(String selection);

    @Override
    public long getRefreshInterval() { return 300000L; }

    public RatePill(Context context, Theme.ResourcesProvider rp, RateCache cache, String baseCurrency, int scale, int iconResId, ColoredBackground background) {
        super(context, rp);
        this.cache = cache;
        this.baseCurrency = baseCurrency;
        this.scale = scale;
        this.iconResId = iconResId;
        this.background = background;

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setMinimumWidth(AndroidUtilities.dp(48));
        layout.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        addView(layout, LayoutHelper.createFrame(-2, 28, (LocaleController.isRTL ? 3 : 5) | 16));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        layout.addView(iconView, LayoutHelper.createLinear(16, 16, 16, 0, 0, 4, 0));

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(AndroidUtilities.dp(13));
        textView.setIncludeFontPadding(false);
        textView.setTypeface(AndroidUtilities.bold());
        textView.adaptWidth = true;
        layout.addView(textView, LayoutHelper.createLinear(-2, -2, 16));

        setLoadingTargetView(layout);
        updateColors();
        ScaleStateListAnimator.apply(layout);

        String cachedPrice = cache.cachedPrice.get();
        if (cachedPrice != null) setData(cachedPrice, false);
    }

    public String[] getTargetCurrencies() { return PillStackCurrencies.TARGET_CURRENCIES; }

    @Override
    public void onPillClicked() {
        if (iconView.getVisibility() == VISIBLE && textView.getText() != null && TextUtils.equals(textView.getText(), DevGramLocale.text("Повторить"))) {
            onUpdateData(true);
        } else {
            onPillLongClicked();
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (PillStackConfig.checkAndClearPendingUpdate(getPillId()) || cache.cachedPrice.get() == null || isRefreshDue()) {
            onUpdateData(true);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pillStackSettingsChanged);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pillStackSettingsChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.pillStackSettingsChanged && PillStackConfig.shouldUpdatePill(args, getPillId()) && "AUTO".equals(getTargetSelection())) {
            PillStackConfig.checkAndClearPendingUpdate(getPillId());
            onUpdateData(true);
        }
    }

    @Override
    public boolean onPillLongClicked() {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) return false;
        final ItemOptions options = ItemOptions.makeOptions(fragment, this, true);
        final ItemOptions swipeback = options.makeSwipeback()
                .add(R.drawable.ic_ab_back, DevGramLocale.text("Назад"), options::closeSwipeback)
                .addGap();
        final String selection = getTargetSelection();
        for (final String cur : getTargetCurrencies()) {
            swipeback.addChecked(cur.equalsIgnoreCase(selection), PillStackCurrencies.getTargetCurrencyLabel(cur), () -> {
                options.dismiss();
                if (cur.equalsIgnoreCase(selection)) return;
                setTargetSelection(cur);
                onUpdateData(false);
            });
        }
        ActionBarMenuSubItem sub = new ActionBarMenuSubItem(options.getContext(), false, false, resourcesProvider);
        sub.setTextAndIcon(DevGramLocale.text("Целевая валюта"), R.drawable.msg_language);
        sub.setSubtext(PillStackCurrencies.getTargetCurrencySubtext(getTargetSelection()));
        sub.setItemHeight(56);
        sub.setOnClickListener(v -> options.openSwipeback(swipeback));
        options.add(sub); // add(ActionBarMenuSubItem) возвращает void — дальше чейним по options
        options.addGap()
                .add(R.drawable.msg_retry, DevGramLocale.text("Обновить"), () -> onUpdateData(true))
                .add(R.drawable.msg_settings, DevGramLocale.text("Настройки"), () -> fragment.presentFragment(new PillStackPreferencesActivity()))
                .setSwipebackGravity(!LocaleController.isRTL, false)
                .setDrawScrim(false)
                .setGravity(LocaleController.isRTL ? 3 : 5)
                .setDimAlpha(0)
                .show();
        return true;
    }

    @Override
    public void onUpdateData(boolean forceRefresh) {
        final String target = ExchangeRates.resolveTargetCurrency(UserConfig.selectedAccount, getTargetSelection());
        String cachedPrice = cache.cachedPrice.get();
        if (!TextUtils.equals(target, cache.cachedCurrency.get())) cachedPrice = null;
        if (!forceRefresh && cachedPrice != null && !isRefreshDue()) {
            setData(cachedPrice, false);
            return;
        }
        if (requestInFlight) return;
        requestInFlight = true;
        if (forceRefresh) animateSizeChange();
        startLoading();
        if (cachedPrice == null && cache.cachedPrice.get() == null) {
            iconView.setVisibility(GONE);
            textView.setVisibility(GONE);
        } else {
            iconView.setImageResource(iconResId);
            iconView.setVisibility(VISIBLE);
            textView.setVisibility(VISIBLE);
        }
        if (forceRefresh) ExchangeRates.clearCache();
        ExchangeRates.fetch(state -> onRates(target, state));
    }

    private void onRates(String target, ExchangeRates.State state) {
        requestInFlight = false;
        if (state == null) {
            String p = cache.cachedPrice.get();
            if (p != null) setData(p, true); else setErrorState(true);
            return;
        }
        BigDecimal rate = state.getRate(baseCurrency, target);
        if (rate == null) {
            String p = cache.cachedPrice.get();
            if (p != null) setData(p, true); else setErrorState(true);
            return;
        }
        String price = formatPrice(rate, target);
        cache.cachedPrice.set(price);
        cache.cachedCurrency.set(target);
        setData(price, true);
        markDataUpdated();
    }

    private String formatPrice(BigDecimal value, String target) {
        String fiat = PillStackCurrencies.formatFiatPrice(value, target);
        if (fiat != null) return fiat;
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString() + " " + target;
    }

    private void setErrorState(boolean animated) {
        stopLoading();
        if (animated) animateSizeChange();
        iconView.setImageResource(R.drawable.msg_retry);
        iconView.setVisibility(VISIBLE);
        textView.setText(DevGramLocale.text("Повторить"), animated);
        textView.setVisibility(VISIBLE);
    }

    private void setData(String price, boolean animated) {
        stopLoading();
        if (animated) animateSizeChange();
        iconView.setImageResource(iconResId);
        iconView.setVisibility(VISIBLE);
        textView.setText(price, animated);
        textView.setVisibility(VISIBLE);
    }

    @Override
    public void setPressed(boolean pressed) {
        if (loading) pressed = false;
        super.setPressed(pressed);
        layout.setPressed(pressed);
    }

    @Override
    public void updateColors() {
        layout.setBackground(background);
        textView.setTextColor(0xFFFFFFFF);
        iconView.setColorFilter(0xFFFFFFFF);
        updateLoadingColors();
    }

    @Override
    public void updateLoadingColors() {
        LoadingDrawable d = loadingDrawable;
        if (d != null) d.setColors(Theme.multAlpha(0xFFFFFFFF, 0.1f), Theme.multAlpha(0xFFFFFFFF, 0.3f));
    }
}

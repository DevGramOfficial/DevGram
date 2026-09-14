package org.telegram.ui.pillstack.pills;

import org.telegram.ui.pillstack.PillStackConfig;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PillStackPreferencesActivity;
import org.telegram.ui.Stories.recorder.Weather;

/** DevGram: порт WeatherPill из exteraGram — температура/иконка погоды по геолокации. */
@SuppressLint("ViewConstructor")
public class WeatherPill extends BasePill implements NotificationCenter.NotificationCenterDelegate {
    private final ImageView iconView;
    private final LinearLayout layout;
    private boolean showingWeather;
    private final AnimatedTextView textView;

    @Override
    public long getRefreshInterval() { return 1200000L; }

    public WeatherPill(Context context, Theme.ResourcesProvider rp) {
        super(context, rp);
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumWidth(AndroidUtilities.dp(48));
        layout.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        addView(layout, LayoutHelper.createFrame(-2, 28, (LocaleController.isRTL ? 3 : 5) | 16));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        layout.addView(iconView, LayoutHelper.createLinear(16, 16, 16, 0, 0, 4, 0));

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(AndroidUtilities.dp(13));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setIncludeFontPadding(false);
        textView.adaptWidth = true;
        NotificationCenter.listenEmojiLoading(textView);
        layout.addView(textView, LayoutHelper.createLinear(-2, -2, 16));

        setLoadingTargetView(layout);
        updateColors();
        ScaleStateListAnimator.apply(layout);

        Weather.State cached = Weather.getCached();
        if (cached != null) setData(cached, false);
    }

    @Override
    public int getPillId() { return PillStackConfig.WEATHER; }

    @Override
    public void onPillClicked() { onPillLongClicked(); }

    @Override
    public boolean onPillLongClicked() {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) return false;
        final String curCity = org.telegram.ui.Stories.recorder.Weather.getManualCity();
        final boolean hasCity = !android.text.TextUtils.isEmpty(curCity);
        ItemOptions options = ItemOptions.makeOptions(fragment, this)
                .add(R.drawable.msg_retry, "Обновить", () -> onUpdateData(true))
                .add(R.drawable.msg_map, hasCity ? "Город: " + curCity : "Выбрать город", () -> showCitySearch(fragment));
        if (hasCity) {
            options.add(R.drawable.msg_location, "Обратно по геолокации", () -> {
                org.telegram.ui.Stories.recorder.Weather.clearManualCity();
                onUpdateData(true);
            });
        }
        options.add(R.drawable.msg_settings, "Настройки", () -> fragment.presentFragment(new PillStackPreferencesActivity()))
                .setDrawScrim(false)
                .setDimAlpha(0)
                .show();
        return true;
    }

    // DevGram: экран поиска города (полноэкранный фрагмент — клавиатура в ActionBar работает
    // стабильно на всех прошивках, в отличие от BottomSheet).
    private void showCitySearch(BaseFragment fragment) {
        fragment.presentFragment(new org.telegram.ui.DevGramWeatherCityActivity().setOnChosen(this::onUpdateData0));
    }

    private void onUpdateData0() {
        onUpdateData(true);
    }

    @Override
    public void onUpdateData(boolean forceRefresh) {
        startLoading();
        Weather.fetchForPill(forceRefresh, state -> {
            if (state != null) {
                markDataUpdated();
                postDelayed(() -> setData(state, true), 300L);
            } else {
                postDelayed(() -> setErrorState(true), 300L);
            }
        });
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (PillStackConfig.checkAndClearPendingUpdate(getPillId()) || Weather.getCached() == null || isRefreshDue()) {
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
        if (id == NotificationCenter.pillStackSettingsChanged && PillStackConfig.shouldUpdatePill(args, getPillId())) {
            PillStackConfig.checkAndClearPendingUpdate(getPillId());
            onUpdateData(true);
        }
    }

    private void setErrorState(boolean animated) {
        stopLoading();
        if (animated) animateSizeChange();
        iconView.setImageResource(R.drawable.msg_retry);
        iconView.setVisibility(VISIBLE);
        textView.setText("Повторить", animated);
        showingWeather = false;
    }

    public void setData(Weather.State state, boolean animated) {
        stopLoading();
        if (state == null) return;
        if (animated) animateSizeChange();
        int iconRes = getWeatherIconRes(state.getEmoji());
        if (iconRes != 0) {
            iconView.setImageResource(iconRes);
            iconView.setVisibility(VISIBLE);
            textView.setText(state.getTemperature(), animated);
        } else {
            iconView.setVisibility(GONE);
            textView.setText(Emoji.replaceEmoji(String.format("%s %s", state.getEmoji(), state.getTemperature()), textView.getPaint().getFontMetricsInt(), true), animated);
        }
        showingWeather = true;
    }

    private int getWeatherIconRes(String emoji) {
        if (emoji == null) return 0;
        switch (emoji) {
            case "☀": return R.drawable.weather_sunny;
            case "☁": return R.drawable.weather_cloudy;
            case "⚡":
            case "⛈": return R.drawable.weather_thunderstorm;
            case "⛅":
            case "🌤": return R.drawable.weather_partly_cloudy;
            case "❄":
            case "🌨": return R.drawable.weather_snowy;
            case "🌓":
            case "🌔":
            case "🌖":
            case "🌗":
            case "🌚":
            case "🌛":
            case "🌜":
            case "🌝": return R.drawable.weather_night;
            case "🌦":
            case "🌧": return R.drawable.weather_rainy;
            case "😶‍🌫": return R.drawable.weather_foggy;
            default: return 0;
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        if (loading) pressed = false;
        super.setPressed(pressed);
        layout.setPressed(pressed);
    }

    @Override
    public void updateColors() {
        int color = getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.75f);
        layout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                Theme.isCurrentThemeDark() ? getThemedColor(Theme.key_windowBackgroundWhite) : Theme.multAlpha(color, 0.09f),
                Theme.multAlpha(color, 0.1f)));
        textView.setTextColor(color);
        iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        updateLoadingColors();
    }
}

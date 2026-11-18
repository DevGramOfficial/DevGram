package org.telegram.ui.pillstack.pills;

import org.telegram.ui.pillstack.PillStackConfig;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PillStackPreferencesActivity;
import org.telegram.ui.ProxyListActivity;

/** DevGram: порт ProxyPill из exteraGram — статус прокси и пинг в мс. */
@SuppressLint("ViewConstructor")
public class ProxyPill extends BasePill implements NotificationCenter.NotificationCenterDelegate {
    private final ImageView iconView;
    private int lastAccount;
    private final LinearLayout layout;
    private final AnimatedTextView textView;

    @Override
    public long getRefreshInterval() { return 0L; }

    public ProxyPill(Context context, Theme.ResourcesProvider rp) {
        super(context, rp);
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumWidth(AndroidUtilities.dp(48));
        layout.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(10), 0);
        addView(layout, LayoutHelper.createFrame(-2, 28, (LocaleController.isRTL ? 3 : 5) | 16));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        layout.addView(iconView, LayoutHelper.createLinear(16, 16, 16, 0, 0, 2, 0));

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(AndroidUtilities.dp(13));
        textView.setIncludeFontPadding(false);
        textView.setTypeface(AndroidUtilities.bold());
        textView.adaptWidth = true;
        layout.addView(textView, LayoutHelper.createLinear(-2, -2, 16));

        setLoadingTargetView(layout);
        updateColors();
        ScaleStateListAnimator.apply(layout);
        onUpdateData(false);
    }

    @Override
    public int getPillId() { return PillStackConfig.PROXY; }

    @Override
    public void onUpdateData(boolean forceRefresh) {
        boolean enabled = SharedConfig.isProxyEnabled();
        int connectionState = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        boolean connected = connectionState == ConnectionsManager.ConnectionStateConnected || connectionState == ConnectionsManager.ConnectionStateUpdating;
        String current = textView.getText() != null ? textView.getText().toString() : "";
        String result;
        SharedConfig.ProxyInfo proxy = SharedConfig.currentProxy;
        if (!enabled || proxy == null) {
            iconView.setImageResource(R.drawable.drawer_proxy_off);
            result = DevGramLocale.text("Прокси");
            stopLoading();
        } else if (connected) {
            long ping = Utilities.clamp(proxy.ping, 9999L, 0L);
            iconView.setImageResource(R.drawable.drawer_proxy_on);
            result = ping > 0 ? (ping + DevGramLocale.text(" мс")) : DevGramLocale.text("Подключено");
            stopLoading();
        } else {
            iconView.setImageResource(R.drawable.drawer_proxy_off);
            result = DevGramLocale.text("Подключение…");
            startLoading();
        }
        if (forceRefresh || !TextUtils.equals(current, result)) {
            if (forceRefresh) animateSizeChange();
            textView.setText(result, forceRefresh);
        }
        updateColors();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        onUpdateData(true);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        lastAccount = UserConfig.selectedAccount;
        NotificationCenter.getInstance(lastAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged || id == NotificationCenter.didUpdateConnectionState) {
            onUpdateData(true);
        }
    }

    @Override
    public void onPillClicked() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) fragment.presentFragment(new ProxyListActivity());
    }

    @Override
    public boolean onPillLongClicked() {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) return false;
        ItemOptions.makeOptions(fragment, this)
                .add(R.drawable.msg_settings, DevGramLocale.text("Настройки"), () -> fragment.presentFragment(new PillStackPreferencesActivity()))
                .setDrawScrim(false)
                .setDimAlpha(0)
                .show();
        return true;
    }

    @Override
    public void setPressed(boolean pressed) {
        if (loading) pressed = false;
        super.setPressed(pressed);
        layout.setPressed(pressed);
    }

    @Override
    public void updateColors() {
        int color;
        boolean enabled = SharedConfig.isProxyEnabled();
        int connectionState = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        boolean connected = connectionState == ConnectionsManager.ConnectionStateConnected || connectionState == ConnectionsManager.ConnectionStateUpdating;
        if (enabled && SharedConfig.currentProxy != null && connected) {
            color = getThemedColor(Theme.key_windowBackgroundWhiteGreenText);
        } else {
            color = getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.75f);
        }
        layout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                Theme.isCurrentThemeDark() ? getThemedColor(Theme.key_windowBackgroundWhite) : Theme.multAlpha(color, 0.09f),
                Theme.multAlpha(color, 0.1f)));
        textView.setTextColor(color);
        iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        updateLoadingColors();
    }
}

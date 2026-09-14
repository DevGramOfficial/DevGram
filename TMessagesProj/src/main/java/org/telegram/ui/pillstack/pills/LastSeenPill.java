package org.telegram.ui.pillstack.pills;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramGhostSettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.pillstack.PillStackConfig;

/** Current account visibility status with manual refresh. */
@SuppressLint("ViewConstructor")
public class LastSeenPill extends BasePill implements NotificationCenter.NotificationCenterDelegate {
    private final LinearLayout layout;
    private final ImageView icon;
    private final AnimatedTextView text;
    private int observedAccount = -1;

    public LastSeenPill(Context context, Theme.ResourcesProvider rp) {
        super(context, rp);
        layout = new LinearLayout(context); layout.setGravity(Gravity.CENTER); layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(10), 0);
        addView(layout, LayoutHelper.createFrame(-2, 28, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));
        icon = new ImageView(context); icon.setImageResource(R.drawable.msg_recent);
        layout.addView(icon, LayoutHelper.createLinear(16, 16, 0, 0, 5, 0));
        text = new AnimatedTextView(context, true, true, true); text.setTextSize(AndroidUtilities.dp(13));
        text.setTypeface(AndroidUtilities.bold()); text.setIncludeFontPadding(false); text.adaptWidth = true;
        layout.addView(text, LayoutHelper.createLinear(-2, -2));
        ScaleStateListAnimator.apply(layout); updateColors(); onUpdateData(false);
    }

    @Override public int getPillId() { return PillStackConfig.LAST_SEEN; }
    @Override public long getRefreshInterval() { return 60000L; }
    @Override public void onPillClicked() { onUpdateData(true); }
    @Override public boolean onPillLongClicked() { onUpdateData(true); return true; }
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        observedAccount = UserConfig.selectedAccount;
        NotificationCenter.getInstance(observedAccount)
                .addObserver(this, NotificationCenter.mainUserInfoChanged);
        onUpdateData(false);
    }
    @Override public void onDetachedFromWindow() {
        if (observedAccount >= 0) {
            NotificationCenter.getInstance(observedAccount)
                    .removeObserver(this, NotificationCenter.mainUserInfoChanged);
            observedAccount = -1;
        }
        super.onDetachedFromWindow();
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mainUserInfoChanged) onUpdateData(true);
    }
    @Override public void onUpdateData(boolean forceRefresh) {
        int account = UserConfig.selectedAccount;
        String value;
        if (DevGramGhostSettings.isActive(account)) value = "Призрак";
        else value = LocaleController.formatUserStatus(account, MessagesController.getInstance(account).getUser(UserConfig.getInstance(account).getClientUserId()));
        text.setText(value, forceRefresh); markDataUpdated();
    }
    @Override public void updateColors() {
        int color = getThemedColor(Theme.key_windowBackgroundWhiteBlackText, .75f);
        layout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                Theme.isCurrentThemeDark() ? getThemedColor(Theme.key_windowBackgroundWhite) : Theme.multAlpha(color, .09f), Theme.multAlpha(color, .1f)));
        text.setTextColor(color); icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
    }
}

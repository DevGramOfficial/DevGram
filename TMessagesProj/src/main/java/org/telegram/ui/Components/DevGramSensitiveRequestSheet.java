package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramSensitiveRequests;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

/** Окно «Подтвердите действие» для опасного запроса из плагина. */
public class DevGramSensitiveRequestSheet extends BottomSheet {

    private final DevGramSensitiveRequests.Result result;
    private final ButtonWithCounterView allowButton;
    private boolean answered;

    public DevGramSensitiveRequestSheet(Context context, Theme.ResourcesProvider resourcesProvider, int account, TLObject request, DevGramSensitiveRequests.Result result) {
        super(context, false, resourcesProvider);
        this.result = result;
        fixNavigationBar();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(18), AndroidUtilities.dp(18), AndroidUtilities.dp(18));
        icon.setImageResource(R.drawable.sensitive_request_stop);
        icon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_featuredStickers_buttonText), PorterDuff.Mode.SRC_IN));
        icon.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(80), getThemedColor(Theme.key_text_RedBold)));
        layout.addView(icon, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        TextView title = new TextView(context);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(20);
        title.setTypeface(AndroidUtilities.bold());
        title.setText("Подтвердите действие");
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 16, 24, 0));

        TextView category = new TextView(context);
        category.setGravity(Gravity.CENTER_HORIZONTAL);
        category.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        category.setTextSize(14);
        category.setText(DevGramSensitiveRequests.getCategory(request));
        layout.addView(category, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 6, 24, 0));

        TableView table = new TableView(context, resourcesProvider);
        table.addRow("Действие", DevGramSensitiveRequests.getTitle(request));
        java.util.ArrayList<CharSequence[]> rows = DevGramSensitiveRequests.details(account, request);
        for (int i = 0; i < rows.size(); i++) {
            CharSequence[] row = rows.get(i);
            if (i == rows.size() - 1) {
                table.addRowMonospaced(row[0], row[1].toString(), 13, null);
            } else {
                table.addRow(row[0], row[1]);
            }
        }
        layout.addView(table, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 20, 16, 0));

        TextView warning = new TextView(context);
        warning.setGravity(Gravity.CENTER_HORIZONTAL);
        warning.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        warning.setTextSize(13);
        warning.setText("Это действие запустил плагин. Если вы не ждали его — нажмите «Отклонить».");
        layout.addView(warning, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 14, 24, 0));

        allowButton = new ButtonWithCounterView(context, true, resourcesProvider);
        allowButton.setText("Разрешить", false);
        allowButton.setEnabled(false);
        allowButton.setOnClickListener(v -> answer(true));
        layout.addView(allowButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 20, 16, 8));
        // кнопка оживает не сразу, чтобы случайное нажатие не подтвердило действие
        AndroidUtilities.runOnUIThread(() -> allowButton.setEnabled(true), 1500);

        ButtonWithCounterView declineButton = new ButtonWithCounterView(context, false, resourcesProvider);
        declineButton.setText("Отклонить", false);
        declineButton.setOnClickListener(v -> answer(false));
        layout.addView(declineButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 16, 8));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(layout);
        setCustomView(scroll);
        setOnDismissListener(d -> answer(false));
    }

    private void answer(boolean allowed) {
        if (answered) {
            return;
        }
        answered = true;
        result.run(allowed);
        dismiss();
    }
}

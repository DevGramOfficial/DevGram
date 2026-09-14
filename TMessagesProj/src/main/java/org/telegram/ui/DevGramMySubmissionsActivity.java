package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;

public class DevGramMySubmissionsActivity extends BaseFragment {
    private final ArrayList<DevGramPlugins.CatalogEntry> rows = new ArrayList<>();
    private LinearLayout content;
    private RadialProgressView progress;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Мои заявки");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(32));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(content, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        progress = new RadialProgressView(context, resourceProvider);
        progress.setSize(AndroidUtilities.dp(28));
        root.addView(progress, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
        DevGramPlugins.fetchMySubmissions(items -> {
            if (content == null) return;
            rows.clear();
            rows.addAll(items);
            progress.setVisibility(View.GONE);
            render(context);
        });
        return fragmentView = root;
    }

    private void render(Context context) {
        content.removeAllViews();
        int pending = 0, published = 0, rejected = 0;
        for (DevGramPlugins.CatalogEntry item : rows) {
            if ("published".equals(item.submissionState)) published++;
            else if ("pending".equals(item.submissionState)) pending++;
            else rejected++;
        }
        LinearLayout summary = surface(context, 22);
        summary.addView(text(context, "Публикации", 20, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)));
        summary.addView(text(context, published + " опубликовано  •  " + pending + " на проверке  •  " + rejected + " отклонено",
                13, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 0));
        content.addView(summary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));
        if (rows.isEmpty()) {
            TextView empty = text(context, "Заявок пока нет\nОпубликовать плагин можно из менеджера плагинов.", 15, false,
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, AndroidUtilities.dp(70), 0, 0);
            content.addView(empty);
            return;
        }
        for (DevGramPlugins.CatalogEntry entry : rows) {
            content.addView(card(context, entry), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        }
    }

    private View card(Context context, DevGramPlugins.CatalogEntry entry) {
        LinearLayout card = surface(context, 19);
        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(context, TextUtils.isEmpty(entry.name) ? entry.id : entry.name, 17, true,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        String state = "published".equals(entry.submissionState) ? "ОПУБЛИКОВАН"
                : "pending".equals(entry.submissionState) ? "НА ПРОВЕРКЕ" : "ОТКЛОНЁН";
        int color = "published".equals(entry.submissionState) ? 0xFF35A866
                : "pending".equals(entry.submissionState) ? Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider)
                : Theme.getColor(Theme.key_text_RedRegular, resourceProvider);
        TextView chip = text(context, state, 10, true, color);
        chip.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        chip.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(9), Theme.multAlpha(color, .13f)));
        head.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
        card.addView(head);
        String meta = (TextUtils.isEmpty(entry.version) ? "" : "Версия " + entry.version)
                + (TextUtils.isEmpty(entry.filter) ? "" : (TextUtils.isEmpty(entry.version) ? "" : "  •  ") + entry.filter);
        if (!meta.isEmpty()) card.addView(text(context, meta, 13, false,
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        if (!TextUtils.isEmpty(entry.rejectionReason) && !"published".equals(entry.submissionState) && !"pending".equals(entry.submissionState)) {
            TextView reason = text(context, entry.rejectionReason, 14, false, Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
            reason.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
            reason.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.multAlpha(
                    Theme.getColor(Theme.key_text_RedRegular, resourceProvider), .1f)));
            card.addView(reason, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));
        }
        card.setOnClickListener(v -> presentFragment(new DevGramPluginHistoryActivity(entry.id, entry.name)));
        ScaleStateListAnimator.apply(card, .02f, 1.2f);
        return card;
    }

    private LinearLayout surface(Context context, int radius) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(radius),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider), Theme.getColor(Theme.key_listSelector, resourceProvider)));
        view.setElevation(AndroidUtilities.dp(1));
        return view;
    }

    private TextView text(Context context, String value, int size, boolean bold, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        text.setTextColor(color);
        text.setLineSpacing(AndroidUtilities.dp(2), 1f);
        if (bold) text.setTypeface(AndroidUtilities.bold());
        return text;
    }
}

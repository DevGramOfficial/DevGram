package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
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

import java.util.ArrayList;

public class DevGramPluginHistoryActivity extends BaseFragment {
    private final String pluginId;
    private final String pluginName;
    private final ArrayList<DevGramPlugins.HistoryEntry> history = new ArrayList<>();
    private LinearLayout content;

    public DevGramPluginHistoryActivity(String id, String name) {
        pluginId = id;
        pluginName = name;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("История публикации");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(32));
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(content, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        render(context, true);
        DevGramPlugins.fetchPluginHistory(pluginId, items -> {
            if (content == null) return;
            history.clear();
            history.addAll(items);
            render(context, false);
        });
        return fragmentView = scroll;
    }

    private void render(Context context, boolean loading) {
        content.removeAllViews();
        LinearLayout hero = surface(context, 22);
        hero.addView(label(context, pluginName, 20, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)));
        hero.addView(label(context, loading ? "Загружаем события…" : history.size() + " " + eventWord(history.size()), 14, false,
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        content.addView(hero, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));
        if (loading) return;
        if (history.isEmpty()) {
            TextView empty = label(context, "История пока пуста", 16, false,
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, AndroidUtilities.dp(70), 0, 0);
            content.addView(empty);
            return;
        }
        for (DevGramPlugins.HistoryEntry item : history) {
            content.addView(eventCard(context, item), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        }
    }

    private View eventCard(Context context, DevGramPlugins.HistoryEntry item) {
        LinearLayout card = surface(context, 18);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(13), AndroidUtilities.dp(14), AndroidUtilities.dp(13));
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.TOP);
        int color = statusColor(item.action);
        TextView mark = label(context, statusMark(item.action), 16, true, color);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Theme.multAlpha(color, .13f));
        mark.setBackground(bg);
        row.addView(mark, LayoutHelper.createLinear(40, 40, Gravity.TOP));
        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label(context, statusTitle(item.action), 16, true,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)));
        String actor = item.actorName.isEmpty() ? String.valueOf(item.actorId) : item.actorName;
        String meta = actor + "  •  " + android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", item.date);
        info.addView(label(context, meta, 12, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        if (!item.details.isEmpty()) info.addView(label(context, item.details, 14, false,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        row.addView(info, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));
        card.addView(row);
        return card;
    }

    private String statusTitle(String action) {
        if ("submitted".equals(action)) return "Заявка отправлена";
        if ("update_submitted".equals(action)) return "Обновление отправлено";
        if ("approved".equals(action)) return "Опубликовано";
        if ("rejected".equals(action)) return "Отклонено";
        if ("rejected_blocked".equals(action)) return "Отклонено и заблокировано";
        return action;
    }

    private String statusMark(String action) {
        if ("approved".equals(action)) return "✓";
        if (action != null && action.startsWith("rejected")) return "!";
        return "↑";
    }

    private int statusColor(String action) {
        if ("approved".equals(action)) return 0xFF35A866;
        if (action != null && action.startsWith("rejected")) return Theme.getColor(Theme.key_text_RedRegular, resourceProvider);
        return Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider);
    }

    private LinearLayout surface(Context context, int radius) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(AndroidUtilities.dp(17), AndroidUtilities.dp(15), AndroidUtilities.dp(17), AndroidUtilities.dp(15));
        view.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(radius),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        view.setElevation(AndroidUtilities.dp(1));
        return view;
    }

    private TextView label(Context context, String value, int size, boolean bold, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        text.setTextColor(color);
        text.setLineSpacing(AndroidUtilities.dp(2), 1f);
        if (bold) text.setTypeface(AndroidUtilities.bold());
        return text;
    }

    private String eventWord(int count) {
        int n = count % 100, n1 = count % 10;
        return n > 10 && n < 20 ? "событий" : n1 == 1 ? "событие" : n1 >= 2 && n1 <= 4 ? "события" : "событий";
    }
}

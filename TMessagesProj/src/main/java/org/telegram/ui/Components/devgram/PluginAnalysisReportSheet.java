package org.telegram.ui.Components.devgram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.devgram.plugins.PluginAnalysisReport;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Лист «Отчёт проверки»: пункты по степени опасности, у каждого — значения и строки кода. */
public class PluginAnalysisReportSheet extends BottomSheet {

    private final Theme.ResourcesProvider rp;
    private final FrameLayout root;

    /**
     * @param confirmTitle если задан — внизу кнопка подтверждения риска («Понимаю риски, установить»)
     */
    public PluginAnalysisReportSheet(BaseFragment fragment, PluginAnalysisReport report,
                                     List<PluginAnalysisReport.Statement> newStatements,
                                     CharSequence confirmTitle, Runnable onConfirm) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        rp = fragment.getResourceProvider();
        Context context = fragment.getParentActivity();
        int gray = Theme.getColor(Theme.key_windowBackgroundGray, rp);
        fixNavigationBar(gray);

        root = new FrameLayout(context);
        root.setBackgroundColor(gray);
        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(gray);
        scroll.addView(list);

        TextView title = new TextView(context);
        title.setText("Отчёт проверки");
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        list.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 16, 22, 4));

        HashSet<String> fresh = new HashSet<>();
        if (newStatements != null) {
            for (PluginAnalysisReport.Statement s : newStatements) fresh.add(s.id);
        }
        if (!fresh.isEmpty()) {
            ArrayList<PluginAnalysisReport.Statement> group = new ArrayList<>();
            for (PluginAnalysisReport.Statement s : report.statements) {
                if (fresh.contains(s.id)) group.add(s);
            }
            addGroup(context, list, report, "Новое в этой версии", group);
        }
        int[][] order = {{PluginAnalysisReport.SEVERITY_CRITICAL}, {PluginAnalysisReport.SEVERITY_DANGER},
                {PluginAnalysisReport.SEVERITY_CAUTION}, {PluginAnalysisReport.SEVERITY_INFO, PluginAnalysisReport.SEVERITY_NONE}};
        String[] names = {"Критично", "Опасно", "Требует внимания", "Другое"};
        for (int g = 0; g < order.length; g++) {
            ArrayList<PluginAnalysisReport.Statement> group = new ArrayList<>();
            for (PluginAnalysisReport.Statement s : report.statements) {
                if (fresh.contains(s.id)) continue;
                for (int sev : order[g]) {
                    if (s.severity == sev) group.add(s);
                }
            }
            addGroup(context, list, report, names[g], group);
        }

        if (report.incomplete()) {
            header(context, list, "Ограничения проверки");
            boolean timeout = false, partial = false;
            for (String l : report.limits) {
                if (l.startsWith("timeout")) timeout = true;
                else partial = true;
            }
            if (timeout) list.addView(limitView(context, R.drawable.plugin_analysis_timer, "Не хватило времени", "Проверка остановилась, не дойдя до конца кода"));
            if (partial) list.addView(limitView(context, R.drawable.plugin_analysis_question, "Неполная проверка", "Часть плагина слишком сложна или не читается"));
        }

        TextView footer = new TextView(context);
        footer.setText("Статическая проверка не видит код, который плагин скачивает или расшифровывает во время работы.");
        footer.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, rp));
        footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        list.addView(footer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 12, 22, confirmTitle != null ? 80 : 16));

        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (confirmTitle != null && onConfirm != null) {
            ButtonWithCounterView button = new ButtonWithCounterView(context, true, rp);
            PluginAnalysisViews.setDangerColors(button, rp);
            button.setText(confirmTitle, false);
            button.setOnClickListener(v -> {
                dismiss();
                onConfirm.run();
            });
            FrameLayout bottom = new FrameLayout(context);
            bottom.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, rp));
            bottom.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 16, 8, 16, 8));
            root.addView(bottom, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 64, Gravity.BOTTOM));
        }
        setCustomView(root);
    }

    private void addGroup(Context context, LinearLayout list, PluginAnalysisReport report, String name,
                          List<PluginAnalysisReport.Statement> group) {
        if (group.isEmpty()) return;
        header(context, list, name);
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        int radius = AndroidUtilities.dp(MessagesController.getGlobalMainSettings().getInt("dg_sectionRadius", 20));
        section.setBackground(Theme.createRoundRectDrawable(radius, Theme.getColor(Theme.key_windowBackgroundWhite, rp)));
        for (int i = 0; i < group.size(); i++) {
            PluginAnalysisReport.Statement s = group.get(i);
            int[] more = new int[1];
            ArrayList<PluginAnalysisViews.EvidenceLine> lines = new ArrayList<>();
            for (PluginAnalysisReport.Evidence e : report.evidenceOf(s, 6, more)) {
                lines.add(new PluginAnalysisViews.EvidenceLine(report.artifactName(e.artifact), e.line, e.excerpt));
            }
            ArrayList<CharSequence> notes = new ArrayList<>();
            if (s.rules.contains("tl.unresolved_request")) notes.add("Часть запросов к Telegram определить не удалось");
            if ("java_hooks".equals(s.id) && (s.rules.contains("hooks.unresolved") || s.rules.contains("hooks.external"))) {
                notes.add("Цель части хуков определить не удалось");
            }
            List<String> displayValues = report.displayValues(s);
            ArrayList<String> values = new ArrayList<>(displayValues.size() > 12 ? displayValues.subList(0, 12) : displayValues);
            PluginAnalysisViews.StatementView view = new PluginAnalysisViews.StatementView(context, rp, s.iconRes(),
                    PluginAnalysisViews.severityColor(s.severity, rp), s.name(), s.description(), values, notes, lines, more[0],
                    text -> {
                        AndroidUtilities.addToClipboard(text);
                        BulletinFactory.of(root, rp).createCopyBulletin("Скопировано").show();
                    });
            section.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            if (i < group.size() - 1) {
                section.addView(divider(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 64, 0, 0, 0));
            }
        }
        list.addView(section, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 0));
    }

    private void header(Context context, LinearLayout list, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, rp));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tv.setTypeface(AndroidUtilities.bold());
        list.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 16, 22, 4));
    }

    private android.view.View divider(Context context) {
        android.view.View v = new android.view.View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), Theme.dividerPaint);
            }
        };
        v.setWillNotDraw(false);
        return v;
    }

    private LinearLayout limitView(Context context, int icon, String title, String subtitle) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        PluginAnalysisViews.TintedIcon ti = new PluginAnalysisViews.TintedIcon(context);
        ti.bind(icon, Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, rp));
        row.addView(ti.container, LayoutHelper.createLinear(40, 40));
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(context);
        PluginAnalysisViews.textMetrics(t, 16, 20, AndroidUtilities.bold());
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp));
        t.setText(title);
        TextView s = new TextView(context);
        PluginAnalysisViews.textMetrics(s, 13, 17, Typeface.DEFAULT);
        s.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, rp));
        s.setText(subtitle);
        texts.addView(t);
        texts.addView(s, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        return row;
    }
}

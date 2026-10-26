package org.telegram.ui.Components.devgram;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.devgram.plugins.PluginAnalysisReport;
import org.telegram.messenger.devgram.plugins.PluginAnalyzer;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Вид результатов анализа плагина: сводная карточка и раскрывающиеся пункты отчёта. */
public final class PluginAnalysisViews {

    private PluginAnalysisViews() {
    }

    // ---- цвета ----

    public static int severityColor(int severity, Theme.ResourcesProvider rp) {
        int red = Theme.getColor(Theme.key_text_RedRegular, rp);
        int green = Theme.getColor(Theme.key_windowBackgroundWhiteGreenText, rp);
        switch (severity) {
            case PluginAnalysisReport.SEVERITY_CRITICAL: return red;
            case PluginAnalysisReport.SEVERITY_DANGER: return readable(blendHue(red, green, .25f), rp);
            case PluginAnalysisReport.SEVERITY_CAUTION: return readable(blendHue(red, green, .38f), rp);
            default: return Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, rp);
        }
    }

    public static int safeColor(Theme.ResourcesProvider rp) {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGreenText, rp);
    }

    private static int readable(int color, Theme.ResourcesProvider rp) {
        int bg = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite, rp), 255);
        if (ColorUtils.calculateLuminance(bg) <= .5) {
            return color;
        }
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        while (ColorUtils.calculateContrast(color, bg) < 3.0 && hsl[2] > .05f) {
            hsl[2] -= .02f;
            color = ColorUtils.HSLToColor(hsl);
        }
        return color;
    }

    private static int blendHue(int a, int b, float t) {
        float[] ha = new float[3], hb = new float[3], out = new float[3];
        ColorUtils.colorToHSL(a, ha);
        ColorUtils.colorToHSL(b, hb);
        ColorUtils.blendHSL(ha, hb, t, out);
        return ColorUtils.HSLToColor(out);
    }

    static int innerRadius() {
        return AndroidUtilities.dp(10);
    }

    static void textMetrics(TextView view, float size, float line, Typeface typeface) {
        view.setTypeface(typeface);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        view.setIncludeFontPadding(false);
        Paint.FontMetricsInt fm = view.getPaint().getFontMetricsInt();
        view.setLineSpacing(Math.max(0, AndroidUtilities.dp(line) - (fm.descent - fm.ascent)), 1f);
    }

    /** Значок на тонированной плашке. */
    static final class TintedIcon {
        final FrameLayout container;
        final ImageView icon;

        TintedIcon(Context context) {
            container = new FrameLayout(context);
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            container.addView(icon, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        }

        void bind(int iconRes, int color) {
            container.setBackground(Theme.createRoundRectDrawable(innerRadius(), Theme.multAlpha(color, .15f)));
            if (iconRes == 0) {
                icon.setImageDrawable(null);
            } else {
                icon.setImageResource(iconRes);
                icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
        }
    }

    /** Чипсы в несколько строк. */
    static final class ChipsLayout extends ViewGroup {
        private final int gap = AndroidUtilities.dp(6);

        ChipsLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int x = 0, y = 0, row = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                c.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
                if (x > 0 && x + c.getMeasuredWidth() > width) {
                    y += row + gap;
                    x = 0;
                    row = 0;
                }
                x += c.getMeasuredWidth() + gap;
                row = Math.max(row, c.getMeasuredHeight());
            }
            setMeasuredDimension(width, getChildCount() == 0 ? 0 : y + row);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int x = 0, y = 0, row = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (x > 0 && x + c.getMeasuredWidth() > width) {
                    y += row + gap;
                    x = 0;
                    row = 0;
                }
                int left = LocaleController.isRTL ? width - x - c.getMeasuredWidth() : x;
                c.layout(left, y, left + c.getMeasuredWidth(), y + c.getMeasuredHeight());
                x += c.getMeasuredWidth() + gap;
                row = Math.max(row, c.getMeasuredHeight());
            }
        }
    }

    // ---- сводная карточка ----

    /** Карточка «Проверяем код… / Угрозы не найдены / Найдены угрозы» с категориями. */
    @SuppressLint("ViewConstructor")
    public static final class SummaryView extends LinearLayout {
        private final Theme.ResourcesProvider rp;
        private final TintedIcon icon;
        private final RadialProgressView progress;
        private final TextView title;
        private final TextView subtitle;
        private final ImageView arrow;
        private final View divider;
        private final ChipsLayout chips;

        public SummaryView(Context context, Theme.ResourcesProvider rp) {
            super(context);
            this.rp = rp;
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(HORIZONTAL);
            header.setMinimumHeight(AndroidUtilities.dp(40));
            icon = new TintedIcon(context);
            progress = new RadialProgressView(context, rp);
            progress.setSize(AndroidUtilities.dp(18));
            progress.setStrokeWidth(2f);
            icon.container.addView(progress, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
            header.addView(icon.container, LayoutHelper.createLinear(40, 40, Gravity.TOP));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            title = new TextView(context);
            textMetrics(title, 16, 20, AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp));
            subtitle = new TextView(context);
            textMetrics(subtitle, 13, 17, Typeface.DEFAULT);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, rp));
            texts.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
            header.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

            arrow = arrowView(context, rp);
            arrow.setRotation(LocaleController.isRTL ? 90 : -90);
            header.addView(arrow, LayoutHelper.createLinear(42, 42, Gravity.TOP, 4, -1, 0, 0));
            addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            divider = new View(context);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider, rp));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, 1);
            lp.topMargin = AndroidUtilities.dp(12);
            lp.bottomMargin = AndroidUtilities.dp(11);
            addView(divider, lp);
            chips = new ChipsLayout(context);
            addView(chips, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            setRunning();
        }

        public void setRunning() {
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton, rp);
            icon.bind(0, accent);
            progress.setProgressColor(accent);
            progress.setVisibility(VISIBLE);
            title.setText("Проверяем код…");
            subtitle.setText("Обычно это занимает несколько секунд");
            subtitle.setVisibility(VISIBLE);
            setChips(null);
            setClick(null);
        }

        public void setFailed(int status) {
            progress.setVisibility(GONE);
            icon.bind(R.drawable.plugin_analysis_warning, severityColor(PluginAnalysisReport.SEVERITY_DANGER, rp));
            title.setText("Проверка не завершилась");
            subtitle.setText(status == PluginAnalyzer.STATUS_TIMEOUT ? "Проверка заняла слишком много времени и была остановлена."
                    : status == PluginAnalyzer.STATUS_TOO_LARGE ? "Файл слишком большой для проверки."
                    : "Анализатор сообщил об ошибке.");
            setChips(null);
            setClick(null);
        }

        public void setReport(PluginAnalysisReport report, int newCount, OnClickListener open) {
            progress.setVisibility(GONE);
            int max = report.maxSeverity;
            boolean threats = max >= PluginAnalysisReport.SEVERITY_DANGER;
            boolean caution = max == PluginAnalysisReport.SEVERITY_CAUTION;
            int color = max >= PluginAnalysisReport.SEVERITY_CAUTION
                    ? severityColor(threats ? PluginAnalysisReport.SEVERITY_CRITICAL : max, rp) : safeColor(rp);
            icon.bind(max >= PluginAnalysisReport.SEVERITY_CAUTION ? R.drawable.plugin_analysis_warning : R.drawable.plugin_analysis_shield, color);

            SpannableStringBuilder t = new SpannableStringBuilder(threats ? "Найдены угрозы" : caution ? "Есть предупреждения" : "Угрозы не найдены");
            if (newCount > 0) {
                t.append("  ");
                int start = t.length();
                t.append("новых: ").append(String.valueOf(newCount));
                t.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_featuredStickers_addButton, rp)), start, t.length(), 0);
            }
            title.setText(t);

            SpannableStringBuilder sub = new SpannableStringBuilder();
            if (threats) {
                appendCount(sub, report.count(PluginAnalysisReport.SEVERITY_CRITICAL), "критично");
                appendCount(sub, report.count(PluginAnalysisReport.SEVERITY_DANGER), "опасно");
            } else {
                sub.append(caution ? "Нет критичных и опасных проблем" : "Ничего подозрительного не найдено");
            }
            subtitle.setText(sub);
            subtitle.setVisibility(sub.length() > 0 ? VISIBLE : GONE);

            // категории: самая опасная степень в каждой
            LinkedHashMap<PluginAnalysisReport.Category, Integer> worst = new LinkedHashMap<>();
            for (PluginAnalysisReport.Statement s : report.statements) {
                PluginAnalysisReport.Category c = s.category();
                Integer prev = worst.get(c);
                worst.put(c, Math.max(prev == null ? -1 : prev, s.severity));
            }
            ArrayList<Map.Entry<PluginAnalysisReport.Category, Integer>> list = new ArrayList<>(worst.entrySet());
            java.util.Collections.sort(list, (a, b) -> {
                int cmp = Integer.compare(b.getValue(), a.getValue());
                return cmp != 0 ? cmp : Integer.compare(a.getKey().ordinal(), b.getKey().ordinal());
            });
            setChips(list);
            boolean hasReport = !report.statements.isEmpty() || report.incomplete();
            setClick(hasReport ? open : null);
        }

        private void appendCount(SpannableStringBuilder sb, int count, String word) {
            if (count <= 0) return;
            if (sb.length() > 0) sb.append(" · ");
            int start = sb.length();
            sb.append(String.valueOf(count));
            sb.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp)), start, sb.length(), 0);
            sb.append(" ").append(word);
        }

        private void setChips(List<Map.Entry<PluginAnalysisReport.Category, Integer>> list) {
            chips.removeAllViews();
            if (list != null) {
                for (Map.Entry<PluginAnalysisReport.Category, Integer> e : list) {
                    chips.addView(chip(getContext(), e.getKey().iconRes, e.getKey().title, severityColor(e.getValue(), rp)));
                }
            }
            boolean empty = chips.getChildCount() == 0;
            chips.setVisibility(empty ? GONE : VISIBLE);
            divider.setVisibility(empty ? GONE : VISIBLE);
        }

        private void setClick(OnClickListener l) {
            setOnClickListener(l);
            setClickable(l != null);
            setBackground(l != null ? Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, rp), 2) : null);
            arrow.setVisibility(l != null ? VISIBLE : GONE);
        }
    }

    static TextView chip(Context context, int iconRes, String text, int color) {
        TextView tv = new TextView(context);
        textMetrics(tv, 13, 17, AndroidUtilities.bold());
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setSingleLine(true);
        tv.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(10), 0);
        tv.setBackground(Theme.createRoundRectDrawable(innerRadius(), Theme.multAlpha(color, .15f)));
        tv.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        Drawable d = ContextCompat.getDrawable(context, iconRes);
        if (d != null) {
            d = d.mutate();
            d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            d.setBounds(0, 0, AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            tv.setCompoundDrawablesRelative(d, null, null, null);
        }
        tv.setText(text);
        return tv;
    }

    static ImageView arrowView(Context context, Theme.ResourcesProvider rp) {
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = AndroidUtilities.dp(11);
        iv.setPadding(p, p, p, p);
        iv.setImageResource(R.drawable.arrow_more);
        iv.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, rp), PorterDuff.Mode.MULTIPLY));
        return iv;
    }

    // ---- пункт отчёта ----

    /** Пункт отчёта: значок категории, название, описание; по нажатию раскрывает значения и строки кода. */
    @SuppressLint("ViewConstructor")
    static final class StatementView extends LinearLayout {
        private final Theme.ResourcesProvider rp;
        private final LinearLayout docs;
        private final ImageView arrow;
        private final Typeface mono = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO);
        private final CopyHandler onCopy;
        private boolean expanded;

        StatementView(Context context, Theme.ResourcesProvider rp, int iconRes, int color, CharSequence name,
                      CharSequence description, List<String> values, List<CharSequence> notes,
                      List<EvidenceLine> lines, int more, CopyHandler onCopy) {
            super(context);
            this.rp = rp;
            this.onCopy = onCopy;
            setOrientation(VERTICAL);

            FrameLayout header = new FrameLayout(context);
            header.setMinimumHeight(AndroidUtilities.dp(64));
            header.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, rp), 2));
            TintedIcon icon = new TintedIcon(context);
            icon.bind(iconRes, color);
            boolean rtl = LocaleController.isRTL;
            header.addView(icon.container, LayoutHelper.createFrame(40, 40, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 12, 12, 12, 0));
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            TextView title = new TextView(context);
            textMetrics(title, 16, 20, AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp));
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setText(name);
            texts.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            if (!TextUtils.isEmpty(description)) {
                TextView sub = new TextView(context);
                textMetrics(sub, 13, 17, Typeface.DEFAULT);
                sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, rp));
                sub.setText(description);
                texts.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
            }
            header.addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, rtl ? 57 : 64, 12, rtl ? 64 : 57, 12));
            arrow = arrowView(context, rp);
            header.addView(arrow, LayoutHelper.createFrame(42, 42, (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 11, 11, 11, 0));
            addView(header);

            docs = new LinearLayout(context);
            docs.setOrientation(VERTICAL);
            docs.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            docs.setVisibility(GONE);
            addView(docs);

            boolean hasDocs = !values.isEmpty() || !notes.isEmpty() || !lines.isEmpty();
            arrow.setVisibility(hasDocs ? VISIBLE : GONE);
            if (hasDocs) {
                buildDocs(values, notes, lines, more);
                header.setOnClickListener(v -> setExpanded(!expanded));
            } else {
                header.setClickable(false);
            }
        }

        void setExpanded(boolean value) {
            expanded = value;
            arrow.animate().rotation(value ? 180 : 0).setDuration(250).start();
            docs.setVisibility(value ? VISIBLE : GONE);
        }

        private void buildDocs(List<String> values, List<CharSequence> notes, List<EvidenceLine> lines, int more) {
            boolean head = !values.isEmpty() || !notes.isEmpty();
            if (head) {
                LinearLayout block = block();
                for (String v : values) block.addView(copying(mono(v, Theme.key_windowBackgroundWhiteBlackText), v));
                for (CharSequence n : notes) {
                    TextView tv = new TextView(getContext());
                    textMetrics(tv, 13, 18, Typeface.DEFAULT);
                    tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, rp));
                    tv.setText(n);
                    block.addView(inset(tv));
                }
                docs.addView(block, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            if (lines.isEmpty()) return;
            // группируем по файлу
            LinkedHashMap<String, List<EvidenceLine>> groups = new LinkedHashMap<>();
            for (EvidenceLine l : lines) {
                List<EvidenceLine> g = groups.get(l.file);
                if (g == null) {
                    g = new ArrayList<>();
                    groups.put(l.file, g);
                }
                g.add(l);
            }
            Paint measure = mono("", Theme.key_windowBackgroundWhiteGrayText).getPaint();
            int i = 0;
            for (Map.Entry<String, List<EvidenceLine>> e : groups.entrySet()) {
                LinearLayout block = block();
                if (!TextUtils.isEmpty(e.getKey())) block.addView(inset(mono(e.getKey(), Theme.key_windowBackgroundWhiteGrayText)));
                int gutter = 0;
                for (EvidenceLine l : e.getValue()) gutter = Math.max(gutter, (int) measure.measureText(String.valueOf(l.line)));
                for (EvidenceLine l : e.getValue()) {
                    LinearLayout row = new LinearLayout(getContext());
                    row.setOrientation(HORIZONTAL);
                    TextView num = mono(l.line > 0 ? String.valueOf(l.line) : "", Theme.key_windowBackgroundWhiteGrayText);
                    num.setGravity(Gravity.END);
                    LayoutParams nlp = new LayoutParams(gutter, LayoutParams.WRAP_CONTENT);
                    nlp.setMarginEnd(AndroidUtilities.dp(12));
                    row.addView(num, nlp);
                    TextView code = mono(breaks(l.code), Theme.key_windowBackgroundWhiteBlackText);
                    code.setMaxLines(4);
                    code.setEllipsize(TextUtils.TruncateAt.END);
                    row.addView(code, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                    block.addView(copying(row, l.code));
                }
                if (i == groups.size() - 1 && more > 0) {
                    block.addView(inset(mono("Ещё мест: " + more, Theme.key_windowBackgroundWhiteGrayText)));
                }
                docs.addView(block, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, (i > 0 || head) ? 8 : 0, 0, 0));
                i++;
            }
        }

        private LinearLayout block() {
            LinearLayout l = new LinearLayout(getContext());
            l.setOrientation(VERTICAL);
            l.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8));
            l.setBackground(Theme.createRoundRectDrawable(innerRadius(), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp), .06f)));
            return l;
        }

        private <T extends View> T inset(T v) {
            v.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4));
            return v;
        }

        private TextView mono(CharSequence text, int colorKey) {
            TextView tv = new TextView(getContext());
            textMetrics(tv, 13, 18, mono);
            tv.setTextColor(Theme.getColor(colorKey, rp));
            tv.setText(text);
            return tv;
        }

        private <T extends View> T copying(T v, CharSequence text) {
            inset(v);
            v.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, rp), AndroidUtilities.dp(6), AndroidUtilities.dp(6)));
            v.setOnClickListener(x -> {
                if (onCopy != null) onCopy.copy(text);
            });
            return v;
        }

        // разрешаем перенос строки кода после точек, скобок и запятых
        private static CharSequence breaks(CharSequence code) {
            StringBuilder sb = new StringBuilder(code.length() + 8);
            for (int i = 0; i < code.length(); i++) {
                char c = code.charAt(i);
                sb.append(c);
                if ((c == '.' || c == '(' || c == ',' || c == '[' || c == '=') && i + 1 < code.length() && !Character.isWhitespace(code.charAt(i + 1))) {
                    sb.append('​');
                }
            }
            return sb;
        }
    }

    interface CopyHandler {
        void copy(CharSequence text);
    }

    static final class EvidenceLine {
        final String file;
        final int line;
        final String code;

        EvidenceLine(String file, int line, String code) {
            this.file = file;
            this.line = line;
            this.code = code;
        }
    }
}

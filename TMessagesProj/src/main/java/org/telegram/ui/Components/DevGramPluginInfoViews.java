package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramPluginInfo;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

// DevGram: строки блоков «Что умеет плагин» и «Сведения» — общие для листа установки и карточки каталога.
public final class DevGramPluginInfoViews {

    private DevGramPluginInfoViews() {
    }

    public static void addCapabilities(Context context, LinearLayout card, ArrayList<DevGramPluginInfo.Capability> caps) {
        for (DevGramPluginInfo.Capability c : caps) {
            TextView row = new TextView(context);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append(c.emoji).append("  ");
            int start = sb.length();
            sb.append(c.title);
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), 0);
            if (c.level == DevGramPluginInfo.LEVEL_DANGER) {
                sb.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_text_RedBold)), start, sb.length(), 0);
            }
            if (c.detail != null && !c.detail.isEmpty()) {
                int ds = sb.length();
                sb.append("\n").append(c.detail);
                sb.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)), ds, sb.length(), 0);
                sb.setSpan(new RelativeSizeSpan(0.92f), ds, sb.length(), 0);
            }
            row.setText(sb);
            row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            row.setLineSpacing(AndroidUtilities.dp(2), 1f);
            card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }
    }

    public static void addRow(Context context, LinearLayout card, String key, String value, Runnable onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView k = new TextView(context);
        k.setText(key);
        k.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        k.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        row.addView(k, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextColor(Theme.getColor(onClick != null ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteBlackText));
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        v.setGravity(Gravity.RIGHT);
        row.addView(v, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1.4f));
        if (onClick != null) {
            row.setOnClickListener(x -> onClick.run());
        }
        card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
    }

    public static String shortHash(String hash) {
        return hash.length() > 16 ? hash.substring(0, 16) + "…" : hash;
    }
}

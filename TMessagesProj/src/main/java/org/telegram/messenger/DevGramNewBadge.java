package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.FilterCreateActivity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// DevGram: плашка «NEW» у новых пунктов настроек. Показывается сутки с момента, как человек
// впервые увидел пункт, потом пропадает навсегда. Список ключей обновлять к каждому релизу:
// убирать старые, добавлять новые.
public final class DevGramNewBadge {

    public static final String APP_ICON = "appIcon";
    public static final String MATERIAL3 = "material3";
    public static final String TRANSITION_ANIMATION = "transitionAnimation";
    public static final String MINI_AVATARS = "senderMiniAvatars";
    public static final String SWIPE_ACTIONS = "swipeActions";
    public static final String ALLOW_RESTRICTED = "allowRestricted";
    public static final String AI_REPLACE_EDITOR = "aiReplaceEditor";
    public static final String AI_REPLACE_SUMMARIES = "aiReplaceSummaries";
    public static final String AI_HISTORY = "aiHistory";
    public static final String GEMINI_NANO = "geminiNano";
    public static final String CRASH_TEST = "crashTest";
    public static final String WEB_PUBLISH = "webPublish";

    private static final Set<String> NEW_FEATURES = new HashSet<>(Arrays.asList(
            APP_ICON, MATERIAL3, TRANSITION_ANIMATION, MINI_AVATARS, SWIPE_ACTIONS, ALLOW_RESTRICTED,
            AI_REPLACE_EDITOR, AI_REPLACE_SUMMARIES, AI_HISTORY, GEMINI_NANO, CRASH_TEST, WEB_PUBLISH
    ));

    private static final long SHOW_FOR_MS = 24L * 60 * 60 * 1000;

    private DevGramNewBadge() {
    }

    public static boolean isNew(String key) {
        if (!NEW_FEATURES.contains(key)) {
            return false;
        }
        SharedPreferences prefs = prefs();
        if (prefs.getBoolean("done_" + key, false)) {
            return false;
        }
        long firstShown = prefs.getLong("shown_" + key, 0);
        long now = System.currentTimeMillis();
        if (firstShown == 0) {
            prefs.edit().putLong("shown_" + key, now).apply();
            return true;
        }
        if (Math.abs(now - firstShown) <= SHOW_FOR_MS) {
            return true;
        }
        prefs.edit().remove("shown_" + key).putBoolean("done_" + key, true).apply();
        return false;
    }

    // Текст пункта с плашкой «NEW» справа (если пункт ещё новый).
    public static CharSequence mark(CharSequence text, String key) {
        return isNew(key) ? withBadge(text) : text;
    }

    public static CharSequence withBadge(CharSequence text) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text == null ? "" : text);
        sb.append("  d");
        FilterCreateActivity.NewSpan span = new FilterCreateActivity.NewSpan(10f);
        span.setText("NEW");
        span.setTypeface(AndroidUtilities.bold());
        span.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        sb.setSpan(span, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("devgram_new_badges", Context.MODE_PRIVATE);
    }
}

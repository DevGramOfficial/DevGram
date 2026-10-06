package org.telegram.messenger;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

// DevGram: перевод текстов плагинов (описание в листе установки, пункты настроек) в один тап.
// Кнопка показывается, только если язык текста отличается от языка приложения. Переводит
// выбранный в настройках переводчик (DevGramTranslator), Telegram Premium не нужен.
public final class DevGramPluginTranslation {

    public interface NeedCallback {
        void run(boolean needed);
    }

    public interface ResultCallback {
        // null — не удалось перевести
        void run(Map<String, String> translations);
    }

    private DevGramPluginTranslation() {
    }

    public static String targetLanguage() {
        try {
            return LocaleController.getInstance().getCurrentLocale().getLanguage().toLowerCase(Locale.US);
        } catch (Throwable e) {
            return "ru";
        }
    }

    // Нужен ли перевод: язык текста ≠ языку приложения. Без детектора — показываем кнопку всегда.
    public static void checkNeeded(Collection<String> texts, NeedCallback callback) {
        StringBuilder sample = new StringBuilder();
        for (String t : texts) {
            if (!TextUtils.isEmpty(t)) {
                if (sample.length() > 0) sample.append('\n');
                sample.append(t);
            }
            if (sample.length() > 1000) break;
        }
        if (sample.length() == 0) {
            callback.run(false);
            return;
        }
        if (!LanguageDetector.hasSupport()) {
            callback.run(true);
            return;
        }
        final String target = targetLanguage();
        LanguageDetector.detectLanguage(sample.toString(), lang -> AndroidUtilities.runOnUIThread(() -> {
            String l = lang == null ? "" : lang.toLowerCase(Locale.US);
            callback.run(TextUtils.isEmpty(l) || "und".equals(l) || !primary(l).equals(primary(target)));
        }), e -> AndroidUtilities.runOnUIThread(() -> callback.run(true)));
    }

    public static void translate(Collection<String> texts, ResultCallback callback) {
        ArrayList<String> unique = new ArrayList<>(new LinkedHashSet<>(texts));
        unique.removeIf(TextUtils::isEmpty);
        HashMap<String, String> result = new HashMap<>();
        if (unique.isEmpty()) {
            callback.run(result);
            return;
        }
        final String target = targetLanguage();
        final int[] left = {unique.size()};
        final boolean[] failed = {false};
        for (String text : unique) {
            DevGramTranslator.translate(text, null, target, (res, error) -> {
                if (res != null && !TextUtils.isEmpty(res.text)) {
                    result.put(text, res.text);
                } else {
                    failed[0] = true;
                }
                if (--left[0] == 0) {
                    callback.run(failed[0] && result.isEmpty() ? null : result);
                }
            });
        }
    }

    private static String primary(String lang) {
        int i = lang.indexOf('-');
        if (i < 0) i = lang.indexOf('_');
        return i > 0 ? lang.substring(0, i) : lang;
    }
}

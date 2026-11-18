package org.telegram.ui;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

// DevGram: экспорт/импорт настроек мода. Формат — наш файл .devgram (внутри обычный JSON).
// Тап по такому файлу в чате или выбор через «Импорт» ведут в один обработчик: сравниваем с
// текущими настройками, показываем отличия и предлагаем применить; если отличий нет — бюллетень.
public class DevGramSettingsIO {

    public static final String EXT = ".devgram";
    public static final String EXPORT_NAME = "DevGram_settings" + EXT;

    // ключ настройки -> человекочитаемое название (для показа отличий)
    private static String[][] getLabels() {
        return new String[][]{
            {"sendReadPackets", DevGramLocale.text("Статусы прочтения")},
            {"sendReadStoryPackets", DevGramLocale.text("Просмотры историй")},
            {"sendOnlinePackets", DevGramLocale.text("Статус «в сети»")},
            {"sendUploadTyping", DevGramLocale.text("«Печатает…»")},
            {"sendOfflineAfterOnline", DevGramLocale.text("Офлайн после активности")},
            {"markReadAfterAction", DevGramLocale.text("Прочитать после действия")},
            {"useScheduledMessages", DevGramLocale.text("Отложенная отправка в режиме призрака")},
            {"sendWithoutSound", DevGramLocale.text("Отправка без звука в режиме призрака")},
            {"suggestGhostBeforeStory", DevGramLocale.text("Предлагать режим перед историей")},
            {"useGlobalGhostConfig", DevGramLocale.text("Общие настройки режима призрака")},
            {"disableAds", DevGramLocale.text("Скрывать рекламу")},
            {"localPremium", DevGramLocale.text("Локальный премиум")},
            {"streaksEnabled", DevGramLocale.text("Огоньки (серии)")},
            {"vpnEnabled", DevGramLocale.text("Прокси")},
            {"saveDeletedMessages", DevGramLocale.text("Сохранять удалённые")},
            {"saveMessagesHistory", DevGramLocale.text("Сохранять историю изменений")},
            {"saveMedia", DevGramLocale.text("Сохранять вложения")},
            {"saveInBotChats", DevGramLocale.text("Сохранять в чатах с ботами")},
            {"saveReadDate", DevGramLocale.text("Сохранять время прочтения")},
            {"saveLocalOnline", DevGramLocale.text("Запоминать замеченный онлайн")},
            {"probeUsingOtherAccounts", DevGramLocale.text("Проверять онлайн через другие аккаунты")},
            {"analyticsEnabled", "Google Analytics"},
            {"crashlyticsEnabled", "Google Crashlytics"},
            {"disableNumberRounding", DevGramLocale.text("Отключить округление чисел")},
            {"squareFab", DevGramLocale.text("Квадратная кнопка (FAB)")},
            {"glassMenu", DevGramLocale.text("Стеклянные меню")},
            {"hideEmojiCategories", DevGramLocale.text("Скрыть категории в поиске эмодзи")},
            {"forceSnow", DevGramLocale.text("Снег в шапке")},
            {"centerTitle", DevGramLocale.text("Заголовок по центру")},
            {"displayGhostStatus", DevGramLocale.text("Значок режима призрака")},
            {"disableMarkdown", DevGramLocale.text("Отключить Markdown")},
            {"hideKeyboardOnScroll", DevGramLocale.text("Скрывать клавиатуру при прокрутке")},
            {"disableGreetingSticker", DevGramLocale.text("Скрыть приветственный стикер")},
            {"addCommaAfterMention", DevGramLocale.text("Запятая после упоминания")},
            {"removeMessageTail", DevGramLocale.text("Убрать хвост сообщения")},
            {"replaceEditedWithIcon", DevGramLocale.text("Значок изменённого сообщения")},
            {"hideShareButton", DevGramLocale.text("Скрыть кнопку пересылки")},
            {"hideStickerTime", DevGramLocale.text("Скрыть время на стикерах")},
            {"inlineCalc", DevGramLocale.text("Результаты в поле ввода")},
            {"inlineMath", DevGramLocale.text("Калькулятор")},
            {"inlineCurrency", DevGramLocale.text("Курсы валют")},
            {"inlineUnits", DevGramLocale.text("Единицы измерения")},
            {"inlineTimeZones", DevGramLocale.text("Часовые пояса")},
            {"inlineTargetCurrency", DevGramLocale.text("Целевая валюта")},
            {"inlineAngleUnit", DevGramLocale.text("Единицы углов")},
            {"inlineMeasureSystem", DevGramLocale.text("Система измерения")},
            {"inlineDigitGrouping", DevGramLocale.text("Разделение разрядов")},
            {"weatherInStories", DevGramLocale.text("Погода в историях")},
            {"updateChannel", DevGramLocale.text("Канал обновлений")},
            {"swipeActions", DevGramLocale.text("Действия свайпом")},
            {"swipeActionsLoop", DevGramLocale.text("Циклический выбор свайпом")},
            {"swipeActionsReversed", DevGramLocale.text("Обратное направление свайпа")},
            {"dg_transitionAnimation", DevGramLocale.text("Анимация переходов")},
            {"dg_md3", "Material 3"},
            {"dg_gooey", DevGramLocale.text("Expressive-анимации")},
            {"avatarCornersF", DevGramLocale.text("Закругление аватарок")},
            {"folderTabsStyle", DevGramLocale.text("Стиль вкладок папок")},
            {"stickerSize", DevGramLocale.text("Размер стикеров")},
            {"formatWithSeconds", DevGramLocale.text("Время с секундами")},
            {"useSystemEmoji", DevGramLocale.text("Системные эмодзи")},
            {"forceDisableTabletMode", DevGramLocale.text("Отключить планшетный режим")},
            {"translate_button", DevGramLocale.text("Кнопка перевода сообщений")},
            {"translate_chat_button", DevGramLocale.text("Перевод целых чатов")},
    };
    }

    // Записать текущие настройки в файл .devgram (в кэше). Возвращает File или null.
    public static java.io.File writeExportFile() {
        try {
            java.io.File dir = new java.io.File(AndroidUtilities.getCacheDir(), "devgram");
            dir.mkdirs();
            java.io.File f = new java.io.File(dir, EXPORT_NAME);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(DevGramConfig.exportToJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();
            return f;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Прочитать текст файла настроек по Uri (для «Импорт» через системный выбор файла).
    public static String readUri(android.content.Context ctx, android.net.Uri uri) {
        if (ctx == null || uri == null) {
            return null;
        }
        try {
            java.io.InputStream in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 1024 * 1024) {
                bos.write(buf, 0, n);
                total += n;
            }
            in.close();
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Прочитать текст файла настроек с диска (для тапа по .devgram в чате).
    public static String readFile(java.io.File f) {
        if (f == null || !f.exists() || f.length() > 1024 * 1024) {
            return null;
        }
        try {
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            int off = 0, r;
            while (off < data.length && (r = fis.read(data, off, data.length - off)) > 0) {
                off += r;
            }
            fis.close();
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Обработать импорт: сравнить с текущими, показать отличия + «Применить», либо бюллетень «не отличаются».
    // onApplied — необязательный колбэк (например, обновить список настроек), может быть null.
    public static void handleImport(BaseFragment fragment, String content, Runnable onApplied) {
        if (fragment == null) {
            return;
        }
        android.content.Context ctx = fragment.getParentActivity();
        if (ctx == null) {
            return;
        }
        org.json.JSONObject o;
        try {
            o = new org.json.JSONObject(content == null ? "" : content.trim());
        } catch (Throwable e) {
            BulletinFactory.of(fragment).createErrorBulletin(DevGramLocale.text("Файл не похож на настройки DevGram")).show();
            return;
        }
        DevGramConfig.loadConfig();
        org.json.JSONObject current;
        try {
            current = new org.json.JSONObject(DevGramConfig.exportToJson());
        } catch (Throwable e) {
            BulletinFactory.of(fragment).createErrorBulletin(DevGramLocale.text("Не удалось прочитать текущие настройки")).show();
            return;
        }
        java.util.ArrayList<String> diffs = new java.util.ArrayList<>();
        boolean extended = o.optInt("_formatVersion", 1) >= 2
                && (o.has("_devgramPreferences") || o.has("_globalPreferences"));
        if (extended) {
            appendSnapshotDiffs(diffs, o.optJSONObject("_devgramPreferences"),
                    current.optJSONObject("_devgramPreferences"));
            appendSnapshotDiffs(diffs, o.optJSONObject("_globalPreferences"),
                    current.optJSONObject("_globalPreferences"));
            appendSnapshotDiffs(diffs, o.optJSONObject("_accountPreferences"),
                    current.optJSONObject("_accountPreferences"));
            if (o.has("_launcherIcon")
                    && !sameValue(current.opt("_launcherIcon"), o.opt("_launcherIcon"))) {
                diffs.add(DevGramLocale.text("• Иконка приложения: ") + formatValue(current.opt("_launcherIcon"))
                        + " → " + formatValue(o.opt("_launcherIcon")));
            }
        } else {
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("_") || !current.has(key)) continue;
                Object oldValue = current.opt(key);
                Object newValue = o.opt(key);
                if (!sameValue(oldValue, newValue)) {
                    diffs.add("• " + labelFor(key) + ": " + formatValue(oldValue)
                            + " → " + formatValue(newValue));
                }
            }
        }
        if (diffs.isEmpty()) {
            BulletinFactory.of(fragment).createErrorBulletin(DevGramLocale.text("Похоже, эти настройки не отличаются от текущих.")).show();
            return;
        }
        final String json = content;
        StringBuilder sb = new StringBuilder();
        sb.append(DevGramLocale.text("Отличия (")).append(diffs.size()).append("):\n");
        int shown = Math.min(diffs.size(), 40);
        for (int i = 0; i < shown; i++) {
            sb.append(diffs.get(i)).append('\n');
        }
        if (diffs.size() > shown) {
            sb.append(DevGramLocale.text("…и ещё ")).append(diffs.size() - shown).append('\n');
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(DevGramLocale.text("Импорт настроек DevGram"));
        b.setMessage(sb.toString().trim());
        b.setPositiveButton(DevGramLocale.text("Применить"), (d, w) -> {
            boolean ok = DevGramConfig.importFromJson(json);
            if (ok && onApplied != null) {
                onApplied.run();
            }
            BulletinFactory.of(fragment)
                    .createSimpleBulletin(R.raw.contact_check, ok ? DevGramLocale.text("Настройки применены") : DevGramLocale.text("Не удалось применить")).show();
        });
        b.setNegativeButton(DevGramLocale.text("Отмена"), null);
        fragment.showDialog(b.create());
    }

    private static void appendSnapshotDiffs(java.util.ArrayList<String> diffs,
                                            org.json.JSONObject imported,
                                            org.json.JSONObject current) {
        if (imported == null) return;
        java.util.Iterator<String> keys = imported.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            org.json.JSONObject newEntry = imported.optJSONObject(key);
            org.json.JSONObject oldEntry = current == null ? null : current.optJSONObject(key);
            if (newEntry == null || sameValue(oldEntry, newEntry)) continue;
            Object oldValue = oldEntry == null ? null : oldEntry.opt("value");
            Object newValue = newEntry.opt("value");
            diffs.add("• " + labelFor(key) + ": " + formatValue(oldValue)
                    + " → " + formatValue(newValue));
        }
    }

    private static boolean sameValue(Object a, Object b) {
        if (a == b) return true;
        if (a == null || a == org.json.JSONObject.NULL || b == null || b == org.json.JSONObject.NULL) {
            return false;
        }
        if (a instanceof org.json.JSONObject && b instanceof org.json.JSONObject) {
            org.json.JSONObject ao = (org.json.JSONObject) a;
            org.json.JSONObject bo = (org.json.JSONObject) b;
            return ao.optString("type").equals(bo.optString("type"))
                    && sameValue(ao.opt("value"), bo.opt("value"));
        }
        if (a instanceof org.json.JSONArray && b instanceof org.json.JSONArray) {
            return a.toString().equals(b.toString());
        }
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private static String formatValue(Object value) {
        if (value == null || value == org.json.JSONObject.NULL) return DevGramLocale.text("По умолчанию");
        if (value instanceof Boolean) return (Boolean) value ? DevGramLocale.text("Вкл") : DevGramLocale.text("Выкл");
        if (value instanceof org.json.JSONArray) {
            return ((org.json.JSONArray) value).length() + DevGramLocale.text(" знач.");
        }
        String text = String.valueOf(value);
        if (text.isEmpty()) return DevGramLocale.text("Пусто");
        return text.length() > 48 ? text.substring(0, 45) + "…" : text;
    }

    private static String labelFor(String key) {
        for (String[] pair : getLabels()) {
            if (pair[0].equals(key)) return pair[1];
        }
        String clean = key == null ? DevGramLocale.text("Настройка") : key;
        if (clean.startsWith("dg_")) clean = clean.substring(3);
        clean = clean.replace('_', ' ');
        return clean.isEmpty() ? DevGramLocale.text("Настройка") : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }
}

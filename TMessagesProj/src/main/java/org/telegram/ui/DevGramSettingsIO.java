package org.telegram.ui;

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
    private static final String[][] LABELS = {
            {"sendReadPackets", "Статусы прочтения"},
            {"sendReadStoryPackets", "Просмотры историй"},
            {"sendOnlinePackets", "Статус «в сети»"},
            {"sendUploadTyping", "«Печатает…»"},
            {"sendOfflineAfterOnline", "Офлайн после активности"},
            {"markReadAfterAction", "Прочитать после действия"},
            {"useScheduledMessages", "Отложенная отправка в режиме призрака"},
            {"sendWithoutSound", "Отправка без звука в режиме призрака"},
            {"suggestGhostBeforeStory", "Предлагать режим перед историей"},
            {"useGlobalGhostConfig", "Общие настройки режима призрака"},
            {"disableAds", "Скрывать рекламу"},
            {"localPremium", "Локальный премиум"},
            {"streaksEnabled", "Огоньки (серии)"},
            {"vpnEnabled", "Прокси"},
            {"iosProfile", "Профиль в стиле iOS"},
            {"saveDeletedMessages", "Сохранять удалённые"},
            {"saveMessagesHistory", "Сохранять историю изменений"},
            {"saveMedia", "Сохранять вложения"},
            {"saveInBotChats", "Сохранять в чатах с ботами"},
            {"saveReadDate", "Сохранять время прочтения"},
            {"saveLocalOnline", "Запоминать замеченный онлайн"},
            {"probeUsingOtherAccounts", "Проверять онлайн через другие аккаунты"},
            {"analyticsEnabled", "Google Analytics"},
            {"crashlyticsEnabled", "Google Crashlytics"},
            {"disableNumberRounding", "Отключить округление чисел"},
            {"squareFab", "Квадратная кнопка (FAB)"},
            {"glassMenu", "Стеклянные меню"},
            {"hideEmojiCategories", "Скрыть категории в поиске эмодзи"},
            {"forceSnow", "Снег в шапке"},
            {"centerTitle", "Заголовок по центру"},
            {"displayGhostStatus", "Значок режима призрака"},
            {"disableMarkdown", "Отключить Markdown"},
            {"hideKeyboardOnScroll", "Скрывать клавиатуру при прокрутке"},
            {"disableGreetingSticker", "Скрыть приветственный стикер"},
            {"addCommaAfterMention", "Запятая после упоминания"},
            {"removeMessageTail", "Убрать хвост сообщения"},
            {"replaceEditedWithIcon", "Значок изменённого сообщения"},
            {"hideShareButton", "Скрыть кнопку пересылки"},
            {"hideStickerTime", "Скрыть время на стикерах"},
            {"inlineCalc", "Результаты в поле ввода"},
            {"inlineMath", "Калькулятор"},
            {"inlineCurrency", "Курсы валют"},
            {"inlineUnits", "Единицы измерения"},
            {"inlineTimeZones", "Часовые пояса"},
            {"inlineTargetCurrency", "Целевая валюта"},
            {"inlineAngleUnit", "Единицы углов"},
            {"inlineMeasureSystem", "Система измерения"},
            {"inlineDigitGrouping", "Разделение разрядов"},
            {"weatherInStories", "Погода в историях"},
            {"updateChannel", "Канал обновлений"},
            {"swipeActions", "Действия свайпом"},
            {"swipeActionsLoop", "Циклический выбор свайпом"},
            {"swipeActionsReversed", "Обратное направление свайпа"},
            {"dg_designMode", "Стиль интерфейса"},
            {"dg_transitionAnimation", "Анимация переходов"},
            {"dg_ios_center_chat_title", "Заголовок чата по центру"},
            {"dg_ios_adaptive_title_width", "Адаптивная ширина заголовка"},
            {"dg_ios_show_unread_counter", "Счётчик непрочитанных в шапке"},
            {"dg_ios_unread_counter_style", "Счётчик в стиле iOS"},
            {"dg_ios_input_panel", "Поле ввода в стиле iOS"},
            {"dg_ios_notification_sound", "Звук уведомлений"},
            {"dg_ios_message_menu", "Меню сообщений в стиле iOS"},
            {"dg_md3", "Material 3"},
            {"dg_gooey", "Expressive-анимации"},
            {"avatarCornersF", "Закругление аватарок"},
            {"folderTabsStyle", "Стиль вкладок папок"},
            {"stickerSize", "Размер стикеров"},
            {"formatWithSeconds", "Время с секундами"},
            {"useSystemEmoji", "Системные эмодзи"},
            {"forceDisableTabletMode", "Отключить планшетный режим"},
            {"translate_button", "Кнопка перевода сообщений"},
            {"translate_chat_button", "Перевод целых чатов"},
    };

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
            BulletinFactory.of(fragment).createErrorBulletin("Файл не похож на настройки DevGram").show();
            return;
        }
        DevGramConfig.loadConfig();
        org.json.JSONObject current;
        try {
            current = new org.json.JSONObject(DevGramConfig.exportToJson());
        } catch (Throwable e) {
            BulletinFactory.of(fragment).createErrorBulletin("Не удалось прочитать текущие настройки").show();
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
            BulletinFactory.of(fragment).createErrorBulletin("Похоже, эти настройки не отличаются от текущих.").show();
            return;
        }
        final String json = content;
        StringBuilder sb = new StringBuilder();
        sb.append("Отличия (").append(diffs.size()).append("):\n");
        int shown = Math.min(diffs.size(), 40);
        for (int i = 0; i < shown; i++) {
            sb.append(diffs.get(i)).append('\n');
        }
        if (diffs.size() > shown) {
            sb.append("…и ещё ").append(diffs.size() - shown).append('\n');
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Импорт настроек DevGram");
        b.setMessage(sb.toString().trim());
        b.setPositiveButton("Применить", (d, w) -> {
            boolean ok = DevGramConfig.importFromJson(json);
            if (ok && onApplied != null) {
                onApplied.run();
            }
            BulletinFactory.of(fragment)
                    .createSimpleBulletin(R.raw.contact_check, ok ? "Настройки применены" : "Не удалось применить").show();
        });
        b.setNegativeButton("Отмена", null);
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
        if (value == null || value == org.json.JSONObject.NULL) return "По умолчанию";
        if (value instanceof Boolean) return (Boolean) value ? "Вкл" : "Выкл";
        if (value instanceof org.json.JSONArray) {
            return ((org.json.JSONArray) value).length() + " знач.";
        }
        String text = String.valueOf(value);
        if (text.isEmpty()) return "Пусто";
        return text.length() > 48 ? text.substring(0, 45) + "…" : text;
    }

    private static String labelFor(String key) {
        for (String[] pair : LABELS) {
            if (pair[0].equals(key)) return pair[1];
        }
        String clean = key == null ? "Настройка" : key;
        if (clean.startsWith("dg_")) clean = clean.substring(3);
        clean = clean.replace('_', ' ');
        return clean.isEmpty() ? "Настройка" : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }
}

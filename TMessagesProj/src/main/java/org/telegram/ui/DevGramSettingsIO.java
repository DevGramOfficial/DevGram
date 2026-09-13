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
        java.util.ArrayList<String> diffs = new java.util.ArrayList<>();
        for (String[] kv : LABELS) {
            String key = kv[0];
            if (!o.has(key)) {
                continue;
            }
            boolean cur = currentValue(key);
            boolean neu = o.optBoolean(key, cur);
            if (cur != neu) {
                diffs.add("• " + kv[1] + ": " + (cur ? "Вкл" : "Выкл") + " → " + (neu ? "Вкл" : "Выкл"));
            }
        }
        if (diffs.isEmpty()) {
            BulletinFactory.of(fragment).createErrorBulletin("Похоже, эти настройки не отличаются от текущих.").show();
            return;
        }
        final String json = content;
        StringBuilder sb = new StringBuilder();
        sb.append("Отличия (").append(diffs.size()).append("):\n");
        for (String d : diffs) {
            sb.append(d).append('\n');
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

    private static boolean currentValue(String key) {
        switch (key) {
            case "sendReadPackets": return org.telegram.messenger.DevGramGhostSettings.get(org.telegram.messenger.UserConfig.selectedAccount).sendReadMessages;
            case "sendReadStoryPackets": return org.telegram.messenger.DevGramGhostSettings.get(org.telegram.messenger.UserConfig.selectedAccount).sendReadStories;
            case "sendOnlinePackets": return org.telegram.messenger.DevGramGhostSettings.get(org.telegram.messenger.UserConfig.selectedAccount).sendOnline;
            case "sendUploadTyping": return org.telegram.messenger.DevGramGhostSettings.get(org.telegram.messenger.UserConfig.selectedAccount).sendTyping;
            case "sendOfflineAfterOnline": return org.telegram.messenger.DevGramGhostSettings.get(org.telegram.messenger.UserConfig.selectedAccount).sendOfflineAfterOnline;
            case "markReadAfterAction": return org.telegram.messenger.DevGramGhostSettings.get(org.telegram.messenger.UserConfig.selectedAccount).markReadAfterAction;
            case "useScheduledMessages": return org.telegram.messenger.DevGramGhostSettings.get(org.telegram.messenger.UserConfig.selectedAccount).useScheduledMessages;
            case "suggestGhostBeforeStory": return org.telegram.messenger.DevGramGhostSettings.get(org.telegram.messenger.UserConfig.selectedAccount).suggestBeforeStory;
            case "useGlobalGhostConfig": return org.telegram.messenger.DevGramGhostSettings.useGlobal();
            case "disableAds": return DevGramConfig.disableAds;
            case "localPremium": return DevGramConfig.localPremium;
            case "streaksEnabled": return DevGramConfig.streaksEnabled;
            case "vpnEnabled": return DevGramConfig.vpnEnabled;
            case "iosProfile": return DevGramConfig.iosProfile;
            case "saveDeletedMessages": return DevGramConfig.saveDeletedMessages;
            case "saveMessagesHistory": return DevGramConfig.saveMessagesHistory;
            case "saveMedia": return DevGramConfig.saveMedia;
            case "saveInBotChats": return DevGramConfig.saveInBotChats;
            case "saveReadDate": return DevGramConfig.saveReadDate;
            case "saveLocalOnline": return DevGramConfig.saveLocalOnline;
            case "probeUsingOtherAccounts": return DevGramConfig.probeUsingOtherAccounts;
            case "analyticsEnabled": return DevGramConfig.analyticsEnabled;
            case "crashlyticsEnabled": return DevGramConfig.crashlyticsEnabled;
            case "disableNumberRounding": return DevGramConfig.disableNumberRounding;
            case "squareFab": return DevGramConfig.squareFab;
            case "glassMenu": return DevGramConfig.glassMenu;
            case "hideEmojiCategories": return DevGramConfig.hideEmojiCategories;
            case "forceSnow": return DevGramConfig.forceSnow;
            case "centerTitle": return DevGramConfig.centerTitle;
            case "displayGhostStatus": return DevGramConfig.displayGhostStatus;
            case "disableMarkdown": return DevGramConfig.disableMarkdown;
            case "hideKeyboardOnScroll": return DevGramConfig.hideKeyboardOnScroll;
            case "disableGreetingSticker": return DevGramConfig.disableGreetingSticker;
            case "addCommaAfterMention": return DevGramConfig.addCommaAfterMention;
            default: return false;
        }
    }
}

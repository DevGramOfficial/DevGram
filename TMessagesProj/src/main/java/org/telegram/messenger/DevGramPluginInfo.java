package org.telegram.messenger;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// DevGram: что умеет плагин — по его исходнику. Показывается в листе установки, чтобы перед
// установкой было видно, к чему плагин получит доступ. Это эвристика по коду (не песочница):
// LEVEL_DANGER — то, чем чаще всего злоупотребляют; LEVEL_CAUTION — серьёзный доступ; LEVEL_INFO — обычное.
public final class DevGramPluginInfo {

    public static final int LEVEL_INFO = 0;
    public static final int LEVEL_CAUTION = 1;
    public static final int LEVEL_DANGER = 2;

    public static final class Capability {
        public final String emoji;
        public final String title;
        public final String detail;
        public final int level;

        Capability(String emoji, String title, String detail, int level) {
            this.emoji = emoji;
            this.title = title;
            this.detail = detail;
            this.level = level;
        }
    }

    private static final Pattern URL_HOST = Pattern.compile("https?://([a-z0-9.-]+\\.[a-z]{2,})", Pattern.CASE_INSENSITIVE);

    private DevGramPluginInfo() {
    }

    public static ArrayList<Capability> capabilities(String source) {
        ArrayList<Capability> list = new ArrayList<>();
        if (TextUtils.isEmpty(source)) {
            return list;
        }
        String s = source.toLowerCase(Locale.US);

        // ---- опасное ----
        if (has(s, "eval(", "exec(", "compile(", "__import__(")) {
            list.add(new Capability("⚠️", "Выполняет динамический код",
                    "eval/exec — может запустить код, которого не видно в самом файле", LEVEL_DANGER));
        }
        if (has(s, "marshal.loads", "zlib.decompress", "b64decode") && has(s, "exec(", "eval(")) {
            list.add(new Capability("🕶️", "Скрывает часть кода",
                    "распаковывает и выполняет закодированный фрагмент — содержимое не проверить глазами", LEVEL_DANGER));
        }
        if (has(s, "subprocess", "os.system(", "os.popen(", "pty.spawn")) {
            list.add(new Capability("💻", "Запускает системные команды", "subprocess / os.system", LEVEL_DANGER));
        }
        if (has(s, "send_message(", "send_text(", "sendmessage(", "sendmessageshelper", "send_document(", "send_media(")) {
            list.add(new Capability("📤", "Отправляет сообщения от вашего имени",
                    "может писать в чаты без вашего участия", LEVEL_DANGER));
        }
        if (has(s, "delete_messages(", "deletemessages(", "delete_message(")) {
            list.add(new Capability("🗑️", "Удаляет сообщения", null, LEVEL_DANGER));
        }

        // ---- требует внимания ----
        if (has(s, "on_send_message_hook", "send_message_hook")) {
            list.add(new Capability("✏️", "Видит и меняет ваши исходящие сообщения",
                    "перехватывает текст перед отправкой", LEVEL_CAUTION));
        }
        if (has(s, "wants_request_hooks", "pre_request_hook", "post_request_hook", "on_request")) {
            list.add(new Capability("📡", "Перехватывает запросы к серверу Telegram",
                    "может читать и подменять данные, которыми клиент обменивается с сервером", LEVEL_CAUTION));
        }
        if (has(s, "on_update_hook", "on_updates_hook", "on_update(", "on_updates(")) {
            list.add(new Capability("📥", "Читает входящие события",
                    "новые сообщения, правки, статусы — всё, что приходит от сервера", LEVEL_CAUTION));
        }
        if (has(s, "hook_method", "hook_all_methods", "hook_all_constructors", "hook_constructor",
                "xposedbridge", "find_class(", "jclass(")) {
            list.add(new Capability("🪝", "Встраивается в код приложения",
                    "Java-хуки: меняет поведение экранов и функций DevGram", LEVEL_CAUTION));
        }
        if (has(s, "http://", "https://", "requests.", "urllib", "socket.", "httpx", "aiohttp")) {
            LinkedHashSet<String> hosts = new LinkedHashSet<>();
            Matcher m = URL_HOST.matcher(source);
            while (m.find() && hosts.size() < 6) {
                hosts.add(m.group(1).toLowerCase(Locale.US));
            }
            list.add(new Capability("🌐", "Обращается к интернету",
                    hosts.isEmpty() ? "адреса формируются в коде" : TextUtils.join(", ", hosts), LEVEL_CAUTION));
        }
        if (has(s, "os.remove(", "os.unlink(", "shutil.rmtree", "os.rmdir(")) {
            list.add(new Capability("🗂️", "Удаляет файлы", null, LEVEL_CAUTION));
        } else if (has(s, "open(", "pathlib", "os.path", "os.listdir", "shutil.")) {
            list.add(new Capability("🗂️", "Работает с файлами", "читает или записывает файлы на устройстве", LEVEL_INFO));
        }
        if (has(s, "clipboard", "clipdata", "setprimaryclip")) {
            list.add(new Capability("📋", "Доступ к буферу обмена", null, LEVEL_CAUTION));
        }

        // ---- обычное ----
        if (has(s, "register_pill")) {
            list.add(new Capability("💊", "Виджеты в строке поиска", "Pill Stack", LEVEL_INFO));
        }
        if (has(s, "add_menu_item", "menu_item", "register_menu")) {
            list.add(new Capability("📋", "Добавляет пункты меню", null, LEVEL_INFO));
        }
        if (has(s, "create_settings", "devgram.ui", "plugin_settings")) {
            list.add(new Capability("⚙️", "Свои настройки", null, LEVEL_INFO));
        }
        if (has(s, "bulletin", "alert", "show_toast", "notification")) {
            list.add(new Capability("🔔", "Показывает уведомления и диалоги", null, LEVEL_INFO));
        }
        if (has(s, "threading", "timer(", "run_on_queue", "schedule(")) {
            list.add(new Capability("⏱️", "Работает в фоне", "потоки и таймеры", LEVEL_INFO));
        }
        String requirements = metaValue(source, "__requirements__");
        if (!TextUtils.isEmpty(requirements)) {
            list.add(new Capability("📦", "Устанавливает Python-пакеты", requirements, LEVEL_CAUTION));
        }
        // сначала опасное, потом внимание, потом обычное
        list.sort((a, b) -> Integer.compare(b.level, a.level));
        return list;
    }

    // Значение метаданных вида __name__ = "..." / ['a', 'b'] из исходника плагина.
    public static String metaValue(String source, String key) {
        if (TextUtils.isEmpty(source)) return null;
        Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*(.+)$").matcher(source);
        if (!m.find()) return null;
        String v = m.group(1).trim();
        v = v.replaceAll("^[\\[(]|[\\])]$", "").replace("\"", "").replace("'", "").trim();
        return v.isEmpty() ? null : v;
    }

    public static int codeLines(String source) {
        if (TextUtils.isEmpty(source)) return 0;
        int n = 0;
        for (String line : source.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) n++;
        }
        return n;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f КБ", bytes / 1024f);
        return String.format(Locale.US, "%.1f МБ", bytes / 1048576f);
    }

    private static boolean has(String s, String... needles) {
        for (String n : needles) {
            if (s.contains(n)) return true;
        }
        return false;
    }
}

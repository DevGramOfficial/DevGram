package org.telegram.messenger.devgram.plugins;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Отчёт статического анализа плагина: пункты для пользователя и правила с доказательствами. */
public final class PluginAnalysisReport {

    public static final int SEVERITY_NONE = -1;
    public static final int SEVERITY_INFO = 0;
    public static final int SEVERITY_CAUTION = 1;
    public static final int SEVERITY_DANGER = 2;
    public static final int SEVERITY_CRITICAL = 3;

    public enum Category {
        CODE(R.drawable.plugin_analysis_code, "Код"),
        ACCOUNT(R.drawable.plugin_analysis_account, "Аккаунт"),
        MESSAGES(R.drawable.plugin_analysis_messages, "Сообщения"),
        NETWORK(R.drawable.plugin_analysis_network, "Сеть"),
        FILES(R.drawable.plugin_analysis_files, "Файлы"),
        DEVICE(R.drawable.plugin_analysis_device, "Устройство"),
        APP(R.drawable.plugin_analysis_app, "Приложение"),
        PACKAGES(R.drawable.plugin_analysis_packages, "Пакеты"),
        OTHER(R.drawable.plugin_analysis_info, "Другое");

        public final int iconRes;
        public final String title;

        Category(int iconRes, String title) {
            this.iconRes = iconRes;
            this.title = title;
        }
    }

    private static final class Entry {
        final Category category;
        final String name;
        final String description;

        Entry(Category category, String name, String description) {
            this.category = category;
            this.name = name;
            this.description = description;
        }
    }

    private static final HashMap<String, Entry> ENTRIES = new HashMap<>();

    private static void entry(String id, Category category, String name, String description) {
        ENTRIES.put(id, new Entry(category, name, description));
    }

    static {
        entry("dynamic_code", Category.CODE, "Динамический код", "Выполняет код, которого нет в файле плагина");
        entry("embedded_code", Category.CODE, "Упакованный код", "Содержит закодированный код и запускает его");
        entry("loads_code", Category.CODE, "Скомпилированный код", "Подгружает дополнительный скомпилированный код");
        entry("reflection_dynamic", Category.CODE, "Динамические вызовы", "Выбирает, что вызвать, уже во время работы");
        entry("native_code", Category.CODE, "Нативный код", "Запускает нативный код, который нельзя проверить");
        entry("anti_analysis", Category.CODE, "Защита от проверки", "Пытается скрыть, что делает");
        entry("hidden_parts", Category.CODE, "Нечитаемые части", "Содержит части, которые не удалось прочитать");
        entry("remote_code", Category.CODE, "Удалённый код", "Может скачать и выполнить код, который вы не видели");
        entry("remote_control", Category.CODE, "Удалённое управление", "Может управляться удалённо");
        entry("hides_behavior", Category.CODE, "Скрытое поведение", "Скрывает часть своих действий");
        entry("network", Category.NETWORK, "Доступ в интернет", "Подключается к интернету");
        entry("exfiltrate_messages", Category.NETWORK, "Выгрузка данных", "Может отправить ваши сообщения или контакты в интернет");
        entry("exfiltrate_files", Category.NETWORK, "Выгрузка файлов", "Может отправить ваши файлы в интернет");
        entry("device_id_upload", Category.NETWORK, "Отправка ID устройства", "Отправляет идентификаторы устройства в интернет");
        entry("webview_bridge", Category.NETWORK, "Мост для веб-страниц", "Позволяет веб-страницам вызывать код приложения");
        entry("files", Category.FILES, "Доступ к файлам", "Читает и записывает файлы");
        entry("telegram_files", Category.FILES, "Загрузки Telegram", "Скачивает файлы через Telegram");
        entry("processes", Category.APP, "Системные процессы", "Запускает системные процессы");
        entry("app_exit", Category.APP, "Перезапуск приложения", "Может закрыть или перезапустить приложение");
        entry("host_tampering", Category.APP, "Вмешательство в приложение", "Меняет работу приложения или других плагинов");
        entry("java_hooks", Category.APP, "Перехват методов", "Меняет поведение приложения через хуки");
        entry("ui_hooks", Category.APP, "Хуки важных экранов", "Меняет экраны входа, оплаты или безопасности");
        entry("persistence", Category.APP, "Управление плагинами", "Может устанавливать или менять плагины");
        entry("app_settings", Category.APP, "Настройки приложения", "Меняет настройки приложения");
        entry("background", Category.APP, "Работа в фоне", "Работает в фоне или реагирует на системные события");
        entry("intents", Category.APP, "Другие приложения", "Открывает другие приложения или передаёт им данные");
        entry("privacy", Category.DEVICE, "Личные данные", "Получает доступ к личным данным устройства");
        entry("system_overlay", Category.DEVICE, "Окна поверх приложений", "Может показывать свои окна поверх других приложений");
        entry("sms", Category.DEVICE, "SMS", "Может читать или отправлять SMS с телефона");
        entry("screen_capture", Category.DEVICE, "Снимок экрана", "Может снимать экран или отключать защиту от скриншотов");
        entry("installs_apps", Category.DEVICE, "Установка приложений", "Может устанавливать другие приложения");
        entry("media_library", Category.DEVICE, "Фото и медиа", "Читает ваши фото и медиафайлы");
        entry("telegram_leak", Category.MESSAGES, "Утечка через Telegram", "Отправляет ваши личные данные сообщениями Telegram");
        entry("notifications", Category.MESSAGES, "Уведомления", "Читает уведомления приложения");
        entry("messages_read", Category.MESSAGES, "Чтение сообщений", "Читает ваши сообщения и чаты");
        entry("messages_send", Category.MESSAGES, "Отправка сообщений", "Отправляет, пересылает или редактирует сообщения");
        entry("messages_delete", Category.MESSAGES, "Удаление сообщений", "Удаляет сообщения");
        entry("contacts", Category.MESSAGES, "Доступ к контактам", "Читает ваши контакты");
        entry("forward_messages", Category.MESSAGES, "Пересылка сообщений", "Может пересылать ваши сообщения внутри Telegram");
        entry("session_via_telegram", Category.ACCOUNT, "Утечка сессии", "Может передать сессию вашего аккаунта через Telegram");
        entry("telegram_api", Category.ACCOUNT, "Запросы к Telegram", "Отправляет запросы от вашего имени");
        entry("sensitive_requests", Category.ACCOUNT, "Операции с аккаунтом", "Может переводить деньги, подарки или управление аккаунтом");
        entry("intercepts_requests", Category.ACCOUNT, "Перехват запросов", "Читает и меняет запросы к Telegram");
        entry("session_data", Category.ACCOUNT, "Данные сессии", "Обращается к данным сессии аккаунта");
        entry("hidden_account_actions", Category.ACCOUNT, "Скрытые действия", "Может действовать от вашего имени незаметно");
        entry("account_takeover", Category.ACCOUNT, "Захват аккаунта", "Может получить доступ к вашему аккаунту");
        entry("wallet_access", Category.ACCOUNT, "Криптокошелёк", "Обращается к вашему криптокошельку");
        entry("third_party_code", Category.PACKAGES, "Непроверенные пакеты", "Устанавливает сторонние пакеты, которые не проверялись");
    }

    public static final class Statement {
        public final String id;
        public final int severity;
        public final List<String> values;
        public final List<String> rules;

        Statement(String id, int severity, List<String> values, List<String> rules) {
            this.id = id;
            this.severity = severity;
            this.values = values;
            this.rules = rules;
        }

        public Category category() {
            Entry e = ENTRIES.get(id);
            return e == null ? Category.OTHER : e.category;
        }

        public String name() {
            Entry e = ENTRIES.get(id);
            return e == null ? id : e.name;
        }

        public String description() {
            Entry e = ENTRIES.get(id);
            return e == null ? null : e.description;
        }
    }

    public static final class Evidence {
        public final String artifact;
        public final int line;
        public final String excerpt;
        public final String scope;

        Evidence(String artifact, int line, String excerpt, String scope) {
            this.artifact = artifact;
            this.line = line;
            this.excerpt = excerpt;
            this.scope = scope;
        }
    }

    public static final class Finding {
        public final String rule;
        public final List<String> capabilities;
        public final List<Evidence> evidence;
        public final int total;

        Finding(String rule, List<String> capabilities, List<Evidence> evidence, int total) {
            this.rule = rule;
            this.capabilities = capabilities;
            this.evidence = evidence;
            this.total = total;
        }
    }

    public final int maxSeverity;
    public final List<Statement> statements;
    public final Map<String, Finding> findings;
    public final List<String> limits;
    public final Map<String, String> artifacts;

    private PluginAnalysisReport(int maxSeverity, List<Statement> statements, Map<String, Finding> findings,
                                 List<String> limits, Map<String, String> artifacts) {
        this.maxSeverity = maxSeverity;
        this.statements = statements;
        this.findings = findings;
        this.limits = limits;
        this.artifacts = artifacts;
    }

    public static int severityOf(String label) {
        if (label == null) return SEVERITY_NONE;
        switch (label) {
            case "info": return SEVERITY_INFO;
            case "caution": return SEVERITY_CAUTION;
            case "danger": return SEVERITY_DANGER;
            case "critical": return SEVERITY_CRITICAL;
            default: return SEVERITY_NONE;
        }
    }

    private static List<String> strings(JSONArray array) {
        if (array == null) return Collections.emptyList();
        ArrayList<String> list = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            String s = array.optString(i, "");
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    public static PluginAnalysisReport parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        LinkedHashMap<String, Finding> findings = new LinkedHashMap<>();
        JSONArray fa = root.optJSONArray("findings");
        if (fa != null) {
            for (int i = 0; i < fa.length(); i++) {
                JSONObject f = fa.optJSONObject(i);
                if (f == null) continue;
                ArrayList<Evidence> evidence = new ArrayList<>();
                JSONArray ea = f.optJSONArray("evidence");
                if (ea != null) {
                    for (int j = 0; j < ea.length(); j++) {
                        JSONObject e = ea.optJSONObject(j);
                        if (e == null) continue;
                        JSONObject loc = e.optJSONObject("location");
                        evidence.add(new Evidence(loc == null ? "" : loc.optString("artifact"), loc == null ? 0 : loc.optInt("line"),
                                e.optString("excerpt"), loc == null ? "" : loc.optString("scope")));
                    }
                }
                findings.put(f.optString("rule"), new Finding(f.optString("rule"), strings(f.optJSONArray("capabilities")),
                        evidence, f.optInt("evidence_total", evidence.size())));
            }
        }
        ArrayList<Statement> statements = new ArrayList<>();
        JSONArray sa = root.optJSONArray("statements");
        if (sa != null) {
            for (int i = 0; i < sa.length(); i++) {
                JSONObject s = sa.optJSONObject(i);
                if (s == null) continue;
                statements.add(new Statement(s.optString("id"), severityOf(s.optString("severity")),
                        strings(s.optJSONArray("values")), strings(s.optJSONArray("rules"))));
            }
        }
        Collections.sort(statements, (a, b) -> Integer.compare(b.severity, a.severity));
        HashMap<String, String> artifacts = new HashMap<>();
        JSONArray aa = root.optJSONArray("artifacts");
        if (aa != null) {
            for (int i = 0; i < aa.length(); i++) {
                JSONObject a = aa.optJSONObject(i);
                if (a != null) artifacts.put(a.optString("id"), a.optString("name"));
            }
        }
        return new PluginAnalysisReport(severityOf(root.optString("max_severity")), statements, findings,
                strings(root.optJSONArray("limits")), artifacts);
    }

    public int count(int severity) {
        int n = 0;
        for (Statement s : statements) {
            if (s.severity == severity) n++;
        }
        return n;
    }

    /** Доказательства пункта: строки кода из всех его правил, без повторов. */
    public List<Evidence> evidenceOf(Statement statement, int limit, int[] more) {
        ArrayList<Evidence> out = new ArrayList<>();
        int total = 0;
        for (String rule : statement.rules) {
            Finding f = findings.get(rule);
            if (f == null) continue;
            total += f.total;
            for (Evidence e : f.evidence) {
                boolean dup = false;
                for (Evidence o : out) {
                    if (o.line == e.line && o.artifact.equals(e.artifact)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup && out.size() < limit) out.add(e);
            }
        }
        if (more != null) more[0] = Math.max(0, total - out.size());
        return out;
    }

    public String artifactName(String id) {
        String name = artifacts.get(id);
        return name == null ? "" : name;
    }

    public boolean incomplete() {
        return !limits.isEmpty();
    }
}

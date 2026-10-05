/*
 * DevGram: встроенный WebSocket-прокси, СКРЫТЫЙ из стандартного меню Telegram.
 *
 * Прокси поднимается ЛОКАЛЬНО внутри приложения (Chaquopy-Python, порт проекта tg-ws-proxy
 * от Flowseal) на 127.0.0.1:1443 и заворачивает MTProto в WebSocket прямо к дата-центрам
 * Telegram — ВНЕШНИЙ СЕРВЕР НЕ НУЖЕН. Приложение цепляется к 127.0.0.1:1443 как к обычному
 * MTProto-прокси через native_setProxySettings; в «Данные и память -> Прокси» его не видно.
 * Состояние — скрытый флаг devgram_proxy_on; на старте поднимается из ConnectionsManager.init().
 *
 * Обход основан на проекте tg-ws-proxy: https://github.com/Flowseal/tg-ws-proxy (MIT).
 * Отдельное спасибо Flowseal за такой божественный обход — WebSocket-бридж прямо к DC
 * Telegram, без своего сервера. Респект. Ядро портировано в Chaquopy (пакет dgwsproxy),
 * запускается из dgws.py.
 */

package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;

public class DevGramProxy {

    // Локальный прокси: слушает на loopback, наружу ходит сам через WebSocket к DC Telegram.
    public static final String ADDRESS = "127.0.0.1";
    public static final int PORT = 1443;
    // Секрет приложения (hex): ee + 16-байт-секрет + hex("store.steampowered.com"). fake-TLS.
    public static final String SECRET = "ee7e088b6d4e15c1ca1ba71945b16ac6ec73746f72652e737465616d706f77657265642e636f6d";

    // Параметры для локального Python-прокси (dgws.py): 16-байт секрет (без ee-префикса) + домен.
    private static final String LOCAL_SECRET = "7e088b6d4e15c1ca1ba71945b16ac6ec";
    private static final String FAKE_TLS_DOMAIN = "store.steampowered.com";

    // старые адреса (прежние серверы) — вычищаем из видимого списка/настроек при миграции
    private static final String[] OLD_ADDRESSES = {"uk.ovavpn.fun", "ro.ovavpn.fun", "31.56.187.225"};

    // Скрытый флаг состояния (в тех же mainconfig-prefs, что читает ConnectionsManager.init()).
    private static final String FLAG = "devgram_proxy_on";

    // Поднять локальный WS-прокси (идемпотентно). Вызывать перед setProxySettings на наш адрес.
    public static void ensureLocalStarted() {
        try {
            DevGramPlugins.startWsProxy(LOCAL_SECRET, FAKE_TLS_DOMAIN, ADDRESS, PORT);
        } catch (Throwable ignore) {
        }
    }

    // Диагностика локального прокси: поднять и проверить, слушает ли 127.0.0.1:1443, показать ошибку.
    public static String diagnose() {
        StringBuilder sb = new StringBuilder();
        ensureLocalStarted();
        // ждём подъёма слушателя
        boolean listening = false;
        for (int i = 0; i < 20 && !listening; i++) {
            try {
                java.net.Socket s = new java.net.Socket();
                s.connect(new java.net.InetSocketAddress(ADDRESS, PORT), 400);
                s.close();
                listening = true;
            } catch (Throwable e) {
                try { Thread.sleep(300); } catch (InterruptedException ignore) {}
            }
        }
        boolean running = DevGramPlugins.isWsProxyRunning();
        String err = DevGramPlugins.wsProxyLastError();
        sb.append("Слушает 127.0.0.1:1443: ").append(listening ? "ДА ✅" : "НЕТ ❌").append('\n');
        sb.append("Python-поток жив: ").append(running ? "да" : "нет").append('\n');
        sb.append("Прокси включён: ").append(isEnabled() ? "да" : "нет").append('\n');
        if (err != null && !err.isEmpty()) {
            sb.append("\nОшибка:\n").append(err.length() > 900 ? err.substring(0, 900) + "…" : err);
        } else if (listening) {
            sb.append("\nЛокальный прокси поднят. Если Telegram всё равно «Соединение…» — проблема в WSS к DC (сеть/блокировка).");
        }
        return sb.toString();
    }

    public static void setEnabled(boolean enable) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(FLAG, enable).apply();
        if (enable) {
            ensureLocalStarted();
            ConnectionsManager.setProxySettings(true, ourProxySettings());
        } else {
            try {
                DevGramPlugins.stopWsProxy();
            } catch (Throwable ignore) {
            }
            applyStandardOrOff(); // вернуть стандартный прокси (если у юзера есть) либо выключить
        }
    }

    public static boolean isEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean(FLAG, false);
    }

    // Миграция со старых версий, где наш прокси был виден в меню: убираем его из видимого
    // списка и стандартных настроек, переводим в скрытый режим, применяем итоговое состояние.
    public static void migrate() {
        SharedPreferences p = MessagesController.getGlobalMainSettings();
        // стандартные prefs указывают на наш адрес (текущий или старый) -> в скрытый флаг и очистить
        if (isOurs(p.getString("proxy_ip", ""))) {
            boolean wasOn = p.getBoolean("proxy_enabled", false);
            p.edit()
                    .putBoolean(FLAG, wasOn || isEnabled())
                    .putBoolean("proxy_enabled", false)
                    .putBoolean("proxy_enabled_calls", false)
                    .putString("proxy_ip", "")
                    .putString("proxy_secret", "")
                    .putString("proxy_user", "")
                    .putString("proxy_pass", "")
                    .putInt("proxy_port", 1080)
                    .apply();
        }
        // убрать наш прокси (текущий и старые) из видимого списка
        try {
            SharedConfig.loadProxyList();
            for (SharedConfig.ProxyInfo info : new java.util.ArrayList<>(SharedConfig.proxyList)) {
                if (isOurs(info.settings.getAddress())) {
                    SharedConfig.deleteProxy(info);
                }
            }
        } catch (Throwable ignore) {
        }
        // применить итоговое состояние (скрытый локальный прокси, если включён)
        if (isEnabled()) {
            ensureLocalStarted();
            ConnectionsManager.setProxySettings(true, ourProxySettings());
        }
    }

    private static boolean isOurs(String address) {
        if (TextUtils.isEmpty(address)) {
            return false;
        }
        if (ADDRESS.equals(address)) {
            return true;
        }
        for (String old : OLD_ADDRESSES) {
            if (old.equals(address)) {
                return true;
            }
        }
        return false;
    }

    // Применить стандартный прокси из настроек Telegram, либо выключить прокси совсем.
    private static void applyStandardOrOff() {
        SharedPreferences p = MessagesController.getGlobalMainSettings();
        String ip = p.getString("proxy_ip", "");
        if (p.getBoolean("proxy_enabled", false) && !TextUtils.isEmpty(ip)) {
            ConnectionsManager.setProxySettings(true, org.telegram.utils.proxy.ProxySettings.fromSharedPreferences(p));
        } else {
            ConnectionsManager.setProxySettings(false, null);
        }
    }

    // Наш скрытый MTProto fake-TLS прокси в виде ProxySettings (новый API 12.10.4).
    // ВАЖНО: тип именно MTPROTO — только он сохраняет secret в конструкторе ProxySettings;
    // при Type.SOCKS5 секрет обнуляется (secret = "") → native получает пустой секрет,
    // fake-TLS не поднимается и прокси молча не работает.
    private static org.telegram.utils.proxy.ProxySettings ourProxySettings() {
        return org.telegram.utils.proxy.ProxySettings.builder()
                .setType(org.telegram.utils.proxy.ProxySettings.Type.MTPROTO)
                .setAddress(ADDRESS)
                .setPort(PORT)
                .setSecret(SECRET)
                .build();
    }
}

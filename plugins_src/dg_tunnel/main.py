# Туннель — VPN/прокси-клиент для DevGram на движке sing-box.
#
# Идея та же, что у exitFy: нативное ядро (sing-box) поднимает локальный SOCKS,
# а встроенный прокси Telegram направляется на 127.0.0.1 — «включил и готово».
# Отличие: ядро вшито в плагин (libbox.so), сервера не нужны — работаем с ТВОИМИ
# подписками/ссылками (VLESS, VMess, Trojan, Shadowsocks, Hysteria, Hysteria2, TUIC).
#
# Как ядро грузится (важно для Android 10+):
#   • нативный бинарь запускать из data-папки нельзя (W^X), а .so грузить — можно;
#   • gomobile-классы в static-блоке зовут System.loadLibrary("gojni"), поэтому
#     используем DexClassLoader с librarySearchPath = папка с libgojni.so —
#     тогда loadLibrary находит нашу .so из data-папки;
#   • Libbox.setup() → Libbox.newService(config, PlatformInterface) → start().
#   • PlatformInterface (его требует sing-box) реализуем из плагина через стандартный
#     java.lang.reflect.Proxy + InvocationHandler — без правки ядра DevGram.

import base64
import json
import time

from java import jclass
from devgram import BasePlugin
from devgram.ui.settings import Custom
from devgram.client_utils import get_last_fragment

_Integer = jclass("java.lang.Integer")
_App = jclass("org.telegram.messenger.ApplicationLoader")

# --- Android/Telegram UI классы для кастомного дашборда (карточки как у exitFy) ---
_LL = jclass("android.widget.LinearLayout")
_LLP = jclass("android.widget.LinearLayout$LayoutParams")
_FL = jclass("android.widget.FrameLayout")
_TV = jclass("android.widget.TextView")
_View = jclass("android.view.View")
_GD = jclass("android.graphics.drawable.GradientDrawable")
_Ripple = jclass("android.graphics.drawable.RippleDrawable")
_CSL = jclass("android.content.res.ColorStateList")
_Theme = jclass("org.telegram.ui.ActionBar.Theme")
_AU = jclass("org.telegram.messenger.AndroidUtilities")
_Array = jclass("java.lang.reflect.Array")
_CharSeq = jclass("java.lang.CharSequence")
_TypedValue = jclass("android.util.TypedValue")
_DIP = 1  # TypedValue.COMPLEX_UNIT_DIP

_LOCAL_PORT = 8964            # локальный SOCKS/HTTP порт нашего ядра
_LISTEN = "127.0.0.1"
_PROTos = ("vless", "vmess", "trojan", "ss", "hysteria2", "hy2", "hysteria", "tuic")


# =============================================================== разбор ссылок
def _b64pad(s):
    s = s.strip().replace("-", "+").replace("_", "/")
    return s + "=" * (-len(s) % 4)


def _b64decode(s):
    return base64.b64decode(_b64pad(s))


def _q(query):
    """Разбор query-строки в dict (без внешних зависимостей)."""
    out = {}
    for part in (query or "").split("&"):
        if not part:
            continue
        if "=" in part:
            k, v = part.split("=", 1)
        else:
            k, v = part, ""
        out[_unquote(k)] = _unquote(v)
    return out


def _ishex(c):
    return c in "0123456789abcdefABCDEF"


def _unquote(s):
    """Percent-decode с ПРАВИЛЬНОЙ склейкой в UTF-8 (иначе имена-эмодзи ломаются в кракозябры)."""
    try:
        out = bytearray()
        i = 0
        n = len(s)
        while i < n:
            c = s[i]
            if c == "+":
                out.append(0x20)
                i += 1
            elif c == "%" and i + 2 < n and _ishex(s[i + 1]) and _ishex(s[i + 2]):
                out.append(int(s[i + 1:i + 3], 16))
                i += 3
            else:
                out.extend(c.encode("utf-8"))
                i += 1
        return out.decode("utf-8", "replace")
    except Exception:
        return s


def _split_uri(uri):
    """scheme://[userinfo@]host:port[?query][#frag] → (scheme, userinfo, host, port, query, frag)."""
    scheme, rest = uri.split("://", 1)
    frag = ""
    if "#" in rest:
        rest, frag = rest.split("#", 1)
        frag = _unquote(frag)
    query = ""
    if "?" in rest:
        rest, query = rest.split("?", 1)
    userinfo = ""
    if "@" in rest:
        userinfo, rest = rest.rsplit("@", 1)
    host, port = rest, 0
    if rest.startswith("["):  # IPv6
        host = rest[1:rest.index("]")]
        after = rest[rest.index("]") + 1:]
        if after.startswith(":"):
            port = int(after[1:])
    elif ":" in rest:
        host, p = rest.rsplit(":", 1)
        try:
            port = int(p)
        except Exception:
            port = 0
    return scheme.lower(), userinfo, host, port, query, frag


def _tls_block(p, default_sni=""):
    """Собрать tls-блок sing-box из query-параметров (security/tls/reality/utls)."""
    security = (p.get("security") or "").lower()
    if security in ("tls", "reality", "xtls") or p.get("sni") or p.get("pbk"):
        tls = {"enabled": True}
        sni = p.get("sni") or p.get("peer") or default_sni
        if sni:
            tls["server_name"] = sni
        if p.get("allowInsecure") in ("1", "true") or p.get("insecure") in ("1", "true"):
            tls["insecure"] = True
        alpn = p.get("alpn")
        if alpn:
            tls["alpn"] = [a for a in alpn.split(",") if a]
        fp = p.get("fp")
        if fp:
            tls["utls"] = {"enabled": True, "fingerprint": fp}
        pbk = p.get("pbk")
        if pbk:
            reality = {"enabled": True, "public_key": pbk}
            if p.get("sid"):
                reality["short_id"] = p.get("sid")
            tls["reality"] = reality
        return tls
    return None


def _transport_block(p):
    """ws/grpc/http/httpupgrade → transport-блок sing-box."""
    net = (p.get("type") or p.get("net") or "tcp").lower()
    if net in ("ws", "websocket"):
        t = {"type": "ws"}
        if p.get("path"):
            t["path"] = p.get("path")
        host = p.get("host")
        if host:
            t["headers"] = {"Host": host}
        return t
    if net == "grpc":
        return {"type": "grpc", "service_name": p.get("serviceName") or p.get("path") or ""}
    if net in ("http", "h2"):
        t = {"type": "http"}
        if p.get("host"):
            t["host"] = [h for h in p.get("host").split(",") if h]
        if p.get("path"):
            t["path"] = p.get("path")
        return t
    if net == "httpupgrade":
        t = {"type": "httpupgrade"}
        if p.get("path"):
            t["path"] = p.get("path")
        if p.get("host"):
            t["host"] = p.get("host")
        return t
    return None


def parse_share(uri):
    """Ссылка-подписка → outbound-словарь sing-box (tag выставляется позже). Бросает при ошибке."""
    uri = uri.strip()
    scheme = uri.split("://", 1)[0].lower()

    if scheme == "vmess":
        raw = json.loads(_b64decode(uri.split("://", 1)[1]).decode("utf-8", "replace"))
        out = {
            "type": "vmess",
            "server": raw.get("add"),
            "server_port": int(raw.get("port")),
            "uuid": raw.get("id"),
            "security": raw.get("scy") or "auto",
            "alter_id": int(raw.get("aid") or 0),
        }
        p = {
            "type": raw.get("net"), "host": raw.get("host"), "path": raw.get("path"),
            "sni": raw.get("sni") or raw.get("host"), "security": raw.get("tls"),
            "alpn": raw.get("alpn"), "serviceName": raw.get("path"), "fp": raw.get("fp"),
        }
        tls = _tls_block(p)
        if raw.get("tls") in ("tls", "reality") and not tls:
            tls = {"enabled": True, "server_name": raw.get("sni") or raw.get("host") or raw.get("add")}
        if tls:
            out["tls"] = tls
        tr = _transport_block(p)
        if tr:
            out["transport"] = tr
        name = raw.get("ps") or raw.get("add")
        return out, name

    sc, userinfo, host, port, query, frag = _split_uri(uri)
    p = _q(query)
    name = frag or host

    if sc == "vless":
        out = {"type": "vless", "server": host, "server_port": port, "uuid": userinfo}
        if p.get("flow"):
            out["flow"] = p.get("flow")
        tls = _tls_block(p, default_sni=host)
        if tls:
            out["tls"] = tls
        tr = _transport_block(p)
        if tr:
            out["transport"] = tr
        return out, name

    if sc == "trojan":
        out = {"type": "trojan", "server": host, "server_port": port, "password": _unquote(userinfo)}
        tls = _tls_block(p, default_sni=host)
        if tls is None:
            tls = {"enabled": True, "server_name": host}
        out["tls"] = tls
        tr = _transport_block(p)
        if tr:
            out["transport"] = tr
        return out, name

    if sc == "ss":
        # SIP002: ss://base64(method:pass)@host:port  ИЛИ  ss://base64(method:pass@host:port)
        method = password = None
        if userinfo:
            try:
                dec = _b64decode(userinfo).decode("utf-8", "replace")
                method, password = dec.split(":", 1)
            except Exception:
                if ":" in userinfo:
                    method, password = _unquote(userinfo).split(":", 1)
        if method is None:
            dec = _b64decode(uri.split("://", 1)[1].split("#")[0].split("?")[0]).decode("utf-8", "replace")
            creds, hp = dec.rsplit("@", 1)
            method, password = creds.split(":", 1)
            host, port = hp.rsplit(":", 1)
            port = int(port)
        return {"type": "shadowsocks", "server": host, "server_port": int(port),
                "method": method, "password": password}, name

    if sc in ("hysteria2", "hy2"):
        out = {"type": "hysteria2", "server": host, "server_port": port,
               "password": _unquote(userinfo)}
        tls = {"enabled": True, "server_name": p.get("sni") or host}
        if p.get("insecure") in ("1", "true"):
            tls["insecure"] = True
        if p.get("obfs"):
            out["obfs"] = {"type": p.get("obfs"), "password": p.get("obfs-password") or ""}
        out["tls"] = tls
        return out, name

    if sc == "hysteria":
        out = {"type": "hysteria", "server": host, "server_port": port}
        if p.get("auth"):
            out["auth_str"] = p.get("auth")
        if p.get("upmbps"):
            out["up_mbps"] = int(p.get("upmbps"))
        if p.get("downmbps"):
            out["down_mbps"] = int(p.get("downmbps"))
        tls = {"enabled": True, "server_name": p.get("peer") or p.get("sni") or host}
        if p.get("insecure") in ("1", "true"):
            tls["insecure"] = True
        if p.get("alpn"):
            tls["alpn"] = [a for a in p.get("alpn").split(",") if a]
        out["tls"] = tls
        return out, name

    if sc == "tuic":
        # tuic://uuid:password@host:port?...
        uuid, password = userinfo, ""
        if ":" in userinfo:
            uuid, password = userinfo.split(":", 1)
        out = {"type": "tuic", "server": host, "server_port": port,
               "uuid": _unquote(uuid), "password": _unquote(password)}
        if p.get("congestion_control"):
            out["congestion_control"] = p.get("congestion_control")
        tls = {"enabled": True, "server_name": p.get("sni") or host}
        if p.get("allow_insecure") in ("1", "true") or p.get("insecure") in ("1", "true"):
            tls["insecure"] = True
        if p.get("alpn"):
            tls["alpn"] = [a for a in p.get("alpn").split(",") if a]
        out["tls"] = tls
        return out, name

    raise ValueError("неизвестный протокол: " + sc)


def build_config(outbound, call_relays=None):
    """Полный конфиг sing-box: локальный mixed-инбаунд + выбранный outbound.

    call_relays — список {"port": локальный_UDP_порт, "ip": relay_ip, "rport": relay_port}
    для заворота ЗВОНКОВ: sing-box сам делает UDP-relay (direct inbound c override) —
    Telegram шлёт на 127.0.0.1:port, ядро заворачивает на relay_ip:rport через VLESS.
    Никакого Python в пути данных — как у exitFy (нативный relay в ядре)."""
    out = dict(outbound)
    out["tag"] = "proxy"
    server_host = str(out.get("server") or "")
    inbounds = [{"type": "mixed", "tag": "mixed-in", "listen": _LISTEN, "listen_port": _LOCAL_PORT}]
    for r in (call_relays or []):
        inbounds.append({
            "type": "direct",
            "tag": "call-%d" % int(r["port"]),
            "listen": _LISTEN,
            "listen_port": int(r["port"]),
            "network": "udp",
            "override_address": str(r["ip"]),
            "override_port": int(r["rport"]),
        })
    cfg = {
        "log": {"level": "info", "timestamp": False},
        "dns": {
            "servers": [{"tag": "dns-direct", "address": "1.1.1.1", "detour": "direct"}],
            "final": "dns-direct",
            "strategy": "prefer_ipv4",
        },
        "inbounds": inbounds,
        "outbounds": [out, {"type": "direct", "tag": "direct"}],
        "route": {
            "rules": ([{"domain": [server_host], "outbound": "direct"}] if server_host else []),
            "final": "proxy",
            "auto_detect_interface": False,
        },
    }
    return json.dumps(cfg, separators=(",", ":"))


# =============================================================== плагин
class Tunnel(BasePlugin):
    id = "devgram.tunnel"
    name = "Туннель"
    version = "1.3.10"
    author = "@DevGramPlugins"
    description = "VPN на sing-box: свои подписки/ссылки, трафик Telegram через выбранный сервер."
    min_app_version = "12.10.3"

    # -------------------------------------------------------------- lifecycle
    def on_load(self):
        self._box = None            # libbox.BoxService
        self._loader = None         # DexClassLoader с libbox
        self._libbox = None         # класс libbox.Libbox
        self._platform = None       # реализация PlatformInterface (Proxy)
        self._connected = False
        self._setup_done = False
        self._dash_view = None       # кэш дашборда (НЕ пересоздаём → нет краша аниматора)
        self._connect_ts = 0         # момент подключения (для аптайма)
        self._tick_gen = 0           # поколение таймера живого статуса
        self._call_hooks = []        # хуки «звонки через прокси»
        self._autoref_gen = 0        # поколение авто-обновления подписок
        # Закрываем «осиротевший» BoxService от прошлого reload плагина (libbox живёт
        # на процесс — старый сервис мог остаться держать порт 8964).
        try:
            holder = self._box_holder()
            old = holder.get("box")
            if old is not None:
                try:
                    old.getClass().getMethod("close").invoke(old)
                    self.log("закрыл осиротевшее ядро от прошлой сессии")
                except Exception:
                    pass
                holder["box"] = None
        except Exception:
            pass
        # Снимаем «мёртвый» прокси от прошлого процесса (ядро умерло при перезапуске,
        # а запись прокси 127.0.0.1 осталась → Telegram висел бы без сети).
        try:
            self.set_setting("conn", "0")
            self._disable_proxy_sync()
        except Exception:
            pass
        self._schedule_autorefresh()
        self._start_enabled_watcher()
        # АВТОЗАПУСК при старте приложения — ТОЛЬКО если пользователь явно включил опцию
        # «Подключаться при запуске». По умолчанию ВЫКЛ: выключил туннель — он остаётся
        # выключенным, сам не лезет обратно. И только если плагин реально включён.
        try:
            if (self.get_setting("auto_start", "0") == "1"
                    and self.get_setting("want", "0") == "1"
                    and self._active_server()):
                def maybe_start():
                    if getattr(self, "enabled", True):     # плагин не отключили за это время
                        self._connect()
                self.log("Туннель: автозапуск (опция включена)")
                self.run_on_ui(maybe_start, 2500)
        except Exception as e:
            self.log("autostart: " + str(e))
        self.log("Туннель загружен")

    def _start_enabled_watcher(self):
        """Самозащита: ядро DevGram при выключении плагина в списке лишь ставит
        self.enabled=False, но НЕ зовёт on_unload. Ловим это сами и гасим всё
        (прокси + ядро sing-box), чтобы отключённый плагин не работал в фоне."""
        self._ew_gen = getattr(self, "_ew_gen", 0) + 1
        gen = self._ew_gen

        def watch():
            if gen != self._ew_gen:
                return
            try:
                if not getattr(self, "enabled", True):
                    # плагин отключили — полностью останавливаемся
                    active = (self._box is not None) or (self.get_setting("conn", "0") == "1")
                    if active:
                        self._flog("плагин отключён в списке — останавливаю туннель")
                        try:
                            self._remove_call_hooks()
                        except Exception:
                            pass
                        self._disable_proxy_sync()
                        self._stop_service()
                        self.set_setting("conn", "0")
                        self._tick_gen += 1
                        self._wd_gen = getattr(self, "_wd_gen", 0) + 1
                    self.run_on_ui(watch, 3000)  # продолжаем следить (вдруг включат назад)
                    return
            except Exception:
                pass
            self.run_on_ui(watch, 2000)

        self.run_on_ui(watch, 2000)

    def on_unload(self):
        # гасим таймеры/хуки/форвардеры
        self._tick_gen = getattr(self, "_tick_gen", 0) + 1
        self._autoref_gen = getattr(self, "_autoref_gen", 0) + 1
        self._wd_gen = getattr(self, "_wd_gen", 0) + 1
        try:
            self._remove_call_hooks()
        except Exception:
            pass
        try:
            self._disable_proxy_sync()
        except Exception as e:
            self.log("unload proxy: " + str(e))
        try:
            self._stop_service()
        except Exception as e:
            self.log("unload stop: " + str(e))
        self.log("Туннель выгружен")

    def _flog(self, msg):
        """Лог и в журнал плагина, и в файл <data>/devgram_tunnel/tunnel.log —
        файл переживает зависание UI, его можно достать через файловый менеджер."""
        try:
            self.log(msg)
        except Exception:
            pass
        try:
            path = self._files_dir() + "/tunnel.log"
            fw = jclass("java.io.FileWriter")(path, True)
            fw.write(time.strftime("%H:%M:%S ") + str(msg) + "\n")
            fw.close()
        except Exception:
            pass

    # -------------------------------------------------------------- хранилище серверов
    def _servers(self):
        try:
            return json.loads(self.get_setting("servers", "[]")) or []
        except Exception:
            return []

    def _save_servers(self, servers):
        self.set_setting("servers", json.dumps(servers, ensure_ascii=False))

    def _active_index(self):
        try:
            i = int(self.get_setting("active", "0"))
        except Exception:
            i = 0
        n = len(self._servers())
        return i if 0 <= i < n else (0 if n else -1)

    def _active_server(self):
        i = self._active_index()
        servers = self._servers()
        return servers[i] if 0 <= i < len(servers) else None

    # ---- подписки (список источников) ----
    def _subs(self):
        try:
            subs = json.loads(self.get_setting("subs", "[]")) or []
        except Exception:
            subs = []
        # миграция со старой одиночной sub_url
        old = self.get_setting("sub_url", "").strip()
        if old and not any(s.get("url") == old for s in subs):
            subs.append({"url": old, "name": self._sub_name(old)})
            self._save_subs(subs)
        return subs

    def _save_subs(self, subs):
        self.set_setting("subs", json.dumps(subs, ensure_ascii=False))

    @staticmethod
    def _sub_name(url):
        try:
            host = url.split("://", 1)[1].split("/", 1)[0]
            return host or url[:24]
        except Exception:
            return url[:24]

    def _add_uri(self, uri, source="manual"):
        uri = (uri or "").strip()
        if not uri:
            return 0
        added = 0
        servers = self._servers()
        for line in uri.replace("\r", "\n").split("\n"):
            line = line.strip()
            if not line or "://" not in line:
                continue
            try:
                outbound, name = parse_share(line)
                servers.append({"name": name or line[:24], "uri": line,
                                "outbound": outbound, "source": source})
                added += 1
            except Exception as e:
                self.log("не разобрал ссылку: %s → %s" % (line[:40], e))
        if added:
            self._save_servers(servers)
        return added

    def _fetch_subscription(self, url):
        """Скачать подписку (список ссылок, часто base64) и добавить все сервера."""
        try:
            conn = jclass("java.net.URL")(url).openConnection()
            conn.setRequestProperty("User-Agent", "DevGram-Tunnel")
            conn.setConnectTimeout(15000)
            conn.setReadTimeout(15000)
            code = int(conn.getResponseCode())
            if code != 200:
                self.log("подписка HTTP %d" % code)
                return 0
            isr = jclass("java.io.InputStreamReader")(conn.getInputStream(), "UTF-8")
            br = jclass("java.io.BufferedReader")(isr)
            sb = []
            while True:
                ln = br.readLine()
                if ln is None:
                    break
                sb.append(str(ln))
            br.close()
            body = "\n".join(sb).strip()
        except Exception as e:
            self.log("ошибка скачивания подписки: " + str(e))
            return 0
        # тело может быть base64-списком ссылок
        text = body
        if "://" not in body:
            try:
                text = _b64decode(body).decode("utf-8", "replace")
            except Exception:
                pass
        # серверы этого источника удаляем и заменяем свежими (как у exitFy)
        servers = [s for s in self._servers() if s.get("source") != url]
        self._save_servers(servers)
        n = self._add_uri(text, source=url)
        # регистрируем/обновляем подписку в списке
        subs = self._subs()
        found = False
        for s in subs:
            if s.get("url") == url:
                s["count"] = n
                found = True
                break
        if not found:
            subs.append({"url": url, "name": self._sub_name(url), "count": n})
        self._save_subs(subs)
        return n

    # -------------------------------------------------------------- ядро (libbox)
    def _files_dir(self):
        ctx = _App.applicationContext
        d = ctx.getDir("devgram_tunnel", 0)
        return str(d.getAbsolutePath())

    def _ensure_loaded(self):
        """Извлечь .so/.dex, поднять DexClassLoader, вызвать Libbox.setup().

        КРИТИЧНО: нативную .so нельзя открыть в двух ClassLoader'ах одного процесса
        («Shared library already opened»). Поэтому loader/libbox грузим ОДИН РАЗ на
        процесс и кешируем в sys — кеш переживает перезагрузку/переустановку плагина.
        """
        import sys
        holder = sys.__dict__.setdefault("_devgram_tunnel_native", {})
        if holder.get("ready"):
            self._loader = holder["loader"]
            self._libbox = holder["libbox"]
            if getattr(self, "_platform", None) is None:
                self._platform = self._make_platform()
            self._setup_done = True
            return True

        root = self._files_dir()
        File = jclass("java.io.File")
        lib_dir = File(root, "lib")
        lib_dir.mkdirs()
        so_dst = File(lib_dir, "libgojni.so")
        dex_dst = File(root, "libbox.dex")
        odex_dir = File(root, "odex")
        odex_dir.mkdirs()
        work = File(root, "work")
        work.mkdirs()
        tmp = File(root, "tmp")
        tmp.mkdirs()
        # копируем ассеты (если ещё нет или изменился размер)
        self._copy_asset("libgojni.so", so_dst)
        self._copy_asset("libbox.dex", dex_dst)
        # КРИТИЧНО: Android 8+ запрещает грузить DEX из ПЕРЕЗАПИСЫВАЕМОГО файла
        # («Writable dex file … is not allowed»). Делаем dex read-only → проверка проходит.
        try:
            dex_dst.setReadOnly()
        except Exception as e:
            self.log("setReadOnly dex: " + str(e))

        DexClassLoader = jclass("dalvik.system.DexClassLoader")
        parent = _App.applicationContext.getClassLoader()
        # librarySearchPath = папка с libgojni.so → System.loadLibrary("gojni") её найдёт
        self._loader = DexClassLoader(
            str(dex_dst.getAbsolutePath()),
            str(odex_dir.getAbsolutePath()),
            str(lib_dir.getAbsolutePath()),
            parent,
        )
        self._libbox = self._loader.loadClass("libbox.Libbox")
        self.log("libbox загружен, версия ядра: " + str(self._call_static(self._libbox, "version")))

        # Libbox.setup(SetupOptions)
        SetupOptions = self._loader.loadClass("libbox.SetupOptions")
        opts = SetupOptions.newInstance()
        opts.setBasePath(root)
        opts.setWorkingPath(str(work.getAbsolutePath()))
        opts.setTempPath(str(tmp.getAbsolutePath()))
        try:
            opts.setUsername("")
        except Exception:
            pass
        self._invoke_static(self._libbox, "setup", ["libbox.SetupOptions"], [opts])
        self._platform = self._make_platform()
        # кешируем на процесс (loader/libbox держат .so — второй раз грузить нельзя)
        holder["loader"] = self._loader
        holder["libbox"] = self._libbox
        holder["ready"] = True
        self._setup_done = True
        return True

    def _copy_asset(self, name, dst_file):
        src = self.asset_path(name)
        try:
            if dst_file.exists() and int(dst_file.length()) == int(jclass("java.io.File")(src).length()):
                return
        except Exception:
            pass
        # перезапись возможного read-only файла (dex мы делаем read-only)
        try:
            if dst_file.exists():
                dst_file.setWritable(True)
                dst_file.delete()
        except Exception:
            pass
        fis = jclass("java.io.FileInputStream")(src)
        fos = jclass("java.io.FileOutputStream")(dst_file)
        try:
            buf = jclass("java.lang.reflect.Array").newInstance(jclass("java.lang.Byte").TYPE, 65536)
            while True:
                n = fis.read(buf)
                if n <= 0:
                    break
                fos.write(buf, 0, n)
            fos.flush()
        finally:
            fis.close()
            fos.close()
        self.log("ассет скопирован: %s" % name)

    # ------ рефлексия статических методов libbox (класс из нашего загрузчика) ------
    @staticmethod
    def _call_static(clazz, method):
        m = clazz.getMethod(method)
        return m.invoke(None)

    @staticmethod
    def _invoke_static(clazz, method, arg_type_names, args):
        types = []
        for t in arg_type_names:
            types.append(clazz.getClassLoader().loadClass(t))
        ClassArr = jclass("java.lang.Class")
        jtypes = jclass("java.lang.reflect.Array").newInstance(ClassArr, len(types))
        ObjArr = jclass("java.lang.Object")
        jargs = jclass("java.lang.reflect.Array").newInstance(ObjArr, len(args))
        for i in range(len(types)):
            jclass("java.lang.reflect.Array").set(jtypes, i, types[i])
            jclass("java.lang.reflect.Array").set(jargs, i, args[i])
        m = clazz.getMethod(method, jtypes)
        return m.invoke(None, jargs)

    def _make_platform(self):
        """Реализация libbox.PlatformInterface через стандартный Proxy+InvocationHandler."""
        plugin = self

        def invoke(self_, proxy, method, args):
            name = str(method.getName())
            try:
                if name in ("useProcFS", "underNetworkExtension", "includeAllNetworks",
                            "usePlatformAutoDetectInterfaceControl"):
                    return False
                if name == "writeLog":
                    plugin._flog("[core] " + (str(args[0]) if args else ""))
                    return None
                if name in ("findConnectionOwner", "uidByPackageName"):
                    return _Integer(-1)
                if name == "openTun":
                    raise RuntimeError("tun не используется")
                if name == "packageNameByUid":
                    return ""
                if name == "hashCode":
                    return _Integer(id(proxy) & 0x7fffffff)
                if name == "equals":
                    return bool(args and proxy is args[0])
                if name == "toString":
                    return "DevGramTunnelPlatform"
            except Exception as e:
                plugin.log("platform.%s: %s" % (name, e))
            # getInterfaces/readWIFIState/void-методы → null/None
            return None

        InvocationHandler = jclass("java.lang.reflect.InvocationHandler")
        handler = self.implement(InvocationHandler, invoke=invoke)
        iface = self._loader.loadClass("libbox.PlatformInterface")
        ClassArr = jclass("java.lang.Class")
        arr = jclass("java.lang.reflect.Array").newInstance(ClassArr, 1)
        jclass("java.lang.reflect.Array").set(arr, 0, iface)
        Proxy = jclass("java.lang.reflect.Proxy")
        return Proxy.newProxyInstance(self._loader, arr, handler)

    def _port_free(self):
        """Порт 8964 реально свободен? (bind-тест — прошлый сервис отпустил его)"""
        import socket
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 0)
            s.bind((_LISTEN, _LOCAL_PORT))
            return True
        except Exception:
            return False
        finally:
            try:
                s.close()
            except Exception:
                pass

    def _start_service(self, outbound, call_relays=None):
        import threading
        lock = getattr(self, "_svc_lock", None)
        if lock is None:
            lock = self._svc_lock = threading.Lock()
        with lock:                       # не даём двум пересборкам гоняться за портом
            self._ensure_loaded()
            config = build_config(outbound, call_relays)
            # ДЕДУП: если ядро уже поднято с таким же конфигом — не рестартим
            # (иначе autoreconnect/watchdog/звонок гоняют бесконечные рестарты → bind in use).
            if self._box is not None and getattr(self, "_cur_config", None) == config:
                self._flog("ядро уже с нужным конфигом — рестарт не нужен")
                return
            self._stop_service()
            self._flog("конфиг: " + config)
            try:
                self._invoke_static(self._libbox, "checkConfig", ["java.lang.String"], [config])
                self._flog("checkConfig: OK")
            except Exception as e:
                self._flog("checkConfig FAILED: " + str(e))
                raise
            # ждём, пока прошлый сервис реально отпустит порт (до ~5с)
            for _ in range(50):
                if self._port_free():
                    break
                time.sleep(0.1)
            NewServiceTypes = ["java.lang.String", "libbox.PlatformInterface"]
            last = None
            for attempt in range(8):
                box = self._invoke_static(self._libbox, "newService", NewServiceTypes,
                                          [config, self._platform])
                try:
                    box.getClass().getMethod("start").invoke(box)
                    self._box = box
                    self._cur_config = config
                    self._store_box(box)   # кеш на процесс (переживает reload плагина)
                    self._flog("ядро запущено (SOCKS %s:%d, relay=%d, попытка %d)"
                               % (_LISTEN, _LOCAL_PORT, len(call_relays or []), attempt + 1))
                    return
                except Exception as e:
                    last = e
                    try:
                        box.getClass().getMethod("close").invoke(box)
                    except Exception:
                        pass
                    if "address already in use" in str(e) or "bind" in str(e):
                        time.sleep(0.4)
                        continue
                    raise
            self._flog("start FAILED после ретраев: " + str(last))
            raise last

    @staticmethod
    def _box_holder():
        import sys
        return sys.__dict__.setdefault("_devgram_tunnel_native", {})

    def _store_box(self, box):
        self._box_holder()["box"] = box

    def _stop_service(self):
        self._cur_config = None
        # Закрываем И свой box, И «осиротевший» из процесс-кеша (от прошлого reload
        # плагина): libbox живёт на процесс, старый BoxService мог остаться держать порт.
        holder = self._box_holder()
        candidates = []
        if self._box is not None:
            candidates.append(self._box)
        cached = holder.get("box")
        if cached is not None and cached is not self._box:
            candidates.append(cached)
        for b in candidates:
            try:
                b.getClass().getMethod("close").invoke(b)
                self.log("ядро остановлено")
            except Exception as e:
                self.log("stop: " + str(e))
        self._box = None
        holder["box"] = None

    # -------------------------------------------------------------- само-тест туннеля
    def _selftest(self):
        """Реально ли трафик идёт через локальный SOCKS: открываем SOCKS5 на
        127.0.0.1:port, коннектимся к www.gstatic.com:80, запрашиваем /generate_204.
        Успех = туннель пропускает трафик. Пишет результат в лог."""
        import socket
        import struct
        s = None
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(12)
            s.connect((_LISTEN, _LOCAL_PORT))
            s.sendall(b"\x05\x01\x00")            # ver5, 1 метод, без авторизации
            g = s.recv(2)
            if g != b"\x05\x00":
                raise RuntimeError("socks greeting %r" % g)
            host = b"www.gstatic.com"
            req = b"\x05\x01\x00\x03" + bytes([len(host)]) + host + struct.pack(">H", 80)
            s.sendall(req)                        # CONNECT www.gstatic.com:80
            rep = s.recv(10)
            if len(rep) < 2 or rep[1] != 0x00:
                raise RuntimeError("socks connect rep=%d" % (rep[1] if len(rep) > 1 else -1))
            s.sendall(b"GET /generate_204 HTTP/1.1\r\nHost: www.gstatic.com\r\nConnection: close\r\n\r\n")
            data = s.recv(80)
            ok = (b" 204" in data) or (b" 200" in data)
            self._flog("selftest: %s :: %r" % ("OK — трафик идёт" if ok else "ПЛОХО (нет 2xx)", data[:48]))
            return ok
        except Exception as e:
            self._flog("selftest FAILED: %s" % e)
            return False
        finally:
            try:
                if s is not None:
                    s.close()
            except Exception:
                pass

    # -------------------------------------------------------------- прокси Telegram
    def _set_tg_proxy(self, enabled):
        # ВАЖНО: смену прокси Telegram делает на UI-потоке (как ProxyListActivity);
        # вызов из фонового потока подвешивал приложение.
        def apply():
            try:
                SharedConfig = jclass("org.telegram.messenger.SharedConfig")
                ConnectionsManager = jclass("org.telegram.tgnet.ConnectionsManager")
                NotificationCenter = jclass("org.telegram.messenger.NotificationCenter")
                MessagesController = jclass("org.telegram.messenger.MessagesController")
                prefs = MessagesController.getGlobalMainSettings()
                editor = prefs.edit()
                if enabled:
                    ProxyInfo = jclass("org.telegram.messenger.SharedConfig$ProxyInfo")
                    info = ProxyInfo(_LISTEN, _LOCAL_PORT, "", "", "")
                    try:
                        SharedConfig.currentProxy = info
                        # Java ArrayList не итерируется питоновским list(); проверяем дубль через size()/get()
                        pl = SharedConfig.proxyList
                        dup = False
                        for i in range(int(pl.size())):
                            if pl.get(i) is info:
                                dup = True
                                break
                        if not dup:
                            pl.add(0, info)
                    except Exception as e:
                        self._flog("proxyList: " + str(e))
                    editor.putBoolean("proxy_enabled", True)
                    editor.putString("proxy_ip", _LISTEN)
                    editor.putInt("proxy_port", _LOCAL_PORT)
                    editor.putString("proxy_user", "")
                    editor.putString("proxy_pass", "")
                    editor.putString("proxy_secret", "")
                    editor.commit()
                    ConnectionsManager.setProxySettings(True, _LISTEN, _LOCAL_PORT, "", "", "")
                else:
                    editor.putBoolean("proxy_enabled", False)
                    editor.commit()
                    ConnectionsManager.setProxySettings(False, "", 0, "", "", "")
                    # ПОЛНОСТЬЮ убираем наш прокси, иначе Telegram остаётся в «соединении»
                    # пока вручную не пере-тогнешь прокси. Чистим currentProxy и список.
                    try:
                        cur = SharedConfig.currentProxy
                        if cur is not None and str(cur.address) == _LISTEN and int(cur.port) == _LOCAL_PORT:
                            SharedConfig.currentProxy = None
                        pl = SharedConfig.proxyList
                        i = 0
                        while i < int(pl.size()):
                            it = pl.get(i)
                            if str(it.address) == _LISTEN and int(it.port) == _LOCAL_PORT:
                                pl.remove(i)
                            else:
                                i += 1
                    except Exception as e:
                        self._flog("proxy cleanup: " + str(e))
                NotificationCenter.getGlobalInstance().postNotificationName(
                    NotificationCenter.proxySettingsChanged)
                self._flog("прокси Telegram: " + ("вкл 127.0.0.1:%d" % _LOCAL_PORT if enabled else "выкл"))
            except Exception as e:
                self._flog("set_tg_proxy: " + str(e))
        self.run_on_ui(apply)

    def _disable_proxy_sync(self):
        """Снять ТОЛЬКО наш прокси (127.0.0.1:port), не трогая чужой. Синхронно."""
        try:
            MessagesController = jclass("org.telegram.messenger.MessagesController")
            prefs = MessagesController.getGlobalMainSettings()
            if (prefs.getBoolean("proxy_enabled", False)
                    and str(prefs.getString("proxy_ip", "")) == _LISTEN
                    and int(prefs.getInt("proxy_port", 0)) == _LOCAL_PORT):
                prefs.edit().putBoolean("proxy_enabled", False).commit()
                jclass("org.telegram.tgnet.ConnectionsManager").setProxySettings(False, "", 0, "", "", "")
                self._flog("наш прокси снят (clean slate)")
        except Exception as e:
            self.log("disable_proxy_sync: " + str(e))

    # -------------------------------------------------------------- подключение
    def _connect(self):
        server = self._active_server()
        if not server:
            self.bulletin("Сначала добавьте сервер", kind="error")
            return
        self.set_setting("conn", "1")
        self.set_setting("want", "1")   # намерение «подключено» — переживёт перезапуск
        self._set_busy(True)
        self.bulletin("Подключение…", kind="info")

        def worker():
            try:
                self._flog("=== connect: %s (%s) ===" % (server.get("name"), server.get("outbound", {}).get("type")))
                self._start_service(server.get("outbound"))
                # САМО-ТЕСТ: реально ли трафик идёт через туннель (HTTP через локальный SOCKS)
                ok = self._selftest()
                self._set_tg_proxy(True)
                self._connected = True
                self._connect_ts = time.time()
                self.set_setting("conn", "1")
                self._start_tick()                    # живой статус (аптайм)
                if self.get_setting("calls_via_proxy", "0") == "1":
                    self._install_call_hooks()        # звонки через прокси
                if ok:
                    self.bulletin("Подключено: " + str(server.get("name")), kind="success")
                else:
                    self.bulletin("Ядро запущено, но туннель не пропускает трафик (см. лог)", kind="error")
            except Exception as e:
                self._connected = False
                self.set_setting("conn", "0")
                try:
                    self._stop_service()
                    self._set_tg_proxy(False)
                except Exception:
                    pass
                self._flog("connect FAILED: " + str(e))
                self.bulletin("Не удалось подключиться: " + str(e), kind="error")
            self._refresh_ui()

        self.run_on_queue(worker)

    def _disconnect(self):
        self.set_setting("conn", "0")
        self.set_setting("want", "0")   # ручное отключение — не переподключаться при старте
        self._set_busy(True)

        def worker():
            try:
                self._remove_call_hooks()
                self._set_tg_proxy(False)
                self._stop_service()
            except Exception as e:
                self.log("disconnect: " + str(e))
            self._connected = False
            self._connect_ts = 0
            self._tick_gen += 1                       # гасим таймер аптайма
            self._wd_gen = getattr(self, '_wd_gen', 0) + 1  # гасим watchdog
            self.set_setting("conn", "0")
            self.bulletin("Отключено", kind="info")
            self._refresh_ui()

        self.run_on_queue(worker)

    def _toggle(self):
        if self.get_setting("conn", "0") == "1":
            self._disconnect()
        else:
            self._connect()


    # ============================================================== кастомный дашборд
    # Дизайн 1:1 с exitFy: колонка карточек (белые скруглённые r18), внутри —
    # иконки-бейджи (круг accent@alpha + тон accent), ACCENT_SURFACE-строки r14,
    # большая кнопка PRIMARY, OUTLINE-кнопки. Все цвета из темы.
    #   Карточки: Состояние подключения · Активный сервер · Источник серверов ·
    #             Добавить сервер · Дополнительно.

    def _dp(self, v):
        return int(_AU.dp(float(v)))

    def _color(self, key):
        try:
            return int(_Theme.getColor(getattr(_Theme, key)))
        except Exception:
            return int(_Theme.getColor(_Theme.key_windowBackgroundWhiteGrayText2))

    @staticmethod
    def _alpha(color, a):
        return (int(color) & 0x00FFFFFF) | ((int(a) & 0xFF) << 24)

    def _accent(self):
        return self._color("key_windowBackgroundWhiteBlueText")

    def _rounded(self, color, radius):
        gd = _GD()
        gd.setColor(int(color))
        gd.setCornerRadius(float(self._dp(radius)))
        return gd

    def _ripple(self, content, highlight):
        return _Ripple(_CSL.valueOf(int(highlight)), content, None)

    def _tv(self, ctx, text, size, color_key, bold=False):
        tv = _TV(ctx)
        tv.setText(str(text))
        tv.setTextSize(_DIP, float(size))
        tv.setTextColor(self._color(color_key))
        if bold:
            tv.setTypeface(_AU.bold())
        return tv

    def _lp(self, w, h, top=0, left=0, weight=None):
        lp = _LLP(w, h) if weight is None else _LLP(w, h, float(weight))
        if top:
            lp.topMargin = self._dp(top)
        if left:
            lp.leftMargin = self._dp(left)
        return lp

    def _badge(self, ctx, drawable_name, size=48):
        iv = jclass("android.widget.ImageView")(ctx)
        try:
            iv.setImageResource(getattr(jclass("org.telegram.messenger.R$drawable"), drawable_name))
        except Exception:
            pass
        try:
            iv.setScaleType(jclass("android.widget.ImageView$ScaleType").CENTER_INSIDE)
            iv.setColorFilter(self._accent(), jclass("android.graphics.PorterDuff$Mode").SRC_IN)
        except Exception:
            pass
        pad = max(self._dp(9), self._dp(size / 4.0))
        iv.setPadding(pad, pad, pad, pad)
        iv.setBackground(self._rounded(self._alpha(self._accent(), 0x24), size / 2.0))
        return iv

    def _card(self, ctx, clickable=False, on_click=None, vertical=True):
        c = _LL(ctx)
        c.setOrientation(1 if vertical else 0)
        if not vertical:
            c.setGravity(16)
        c.setPadding(self._dp(16), self._dp(15), self._dp(16), self._dp(15))
        c.setMinimumHeight(self._dp(64))
        base = self._rounded(self._color("key_windowBackgroundWhite"), 18)
        if clickable:
            c.setBackground(self._ripple(base, self._alpha(self._accent(), 30)))
            c.setClickable(True)
            c.setFocusable(True)
            if on_click:
                c.setOnClickListener(self.on_click(lambda v: on_click()))
        else:
            c.setBackground(base)
        return c

    def _accent_row(self, ctx, on_click=None):
        r = _LL(ctx)
        r.setOrientation(0)
        r.setGravity(16)
        r.setMinimumHeight(self._dp(48))
        base = self._rounded(self._alpha(self._accent(), 0x14), 14)
        if on_click:
            r.setBackground(self._ripple(base, self._alpha(self._accent(), 30)))
            r.setClickable(True)
            r.setFocusable(True)
            r.setOnClickListener(self.on_click(lambda v: on_click()))
        else:
            r.setBackground(base)
        p = self._dp(8)
        r.setPadding(p, p, p, p)
        return r

    def _primary_button(self, ctx, label, on_click):
        tv = self._tv(ctx, label, 16, "key_featuredStickers_buttonText", bold=True)
        tv.setGravity(17)
        tv.setAllCaps(False)
        tv.setMinHeight(self._dp(50))
        p = self._dp(12)
        tv.setPadding(p, p, p, p)
        tv.setBackground(self._ripple(self._rounded(self._color("key_featuredStickers_addButton"), 14),
                                      self._alpha(0xFFFFFF, 60)))
        tv.setClickable(True)
        tv.setFocusable(True)
        tv.setOnClickListener(self.on_click(lambda v: on_click()))
        return tv

    def _outline_button(self, ctx, label, on_click):
        tv = self._tv(ctx, label, 15, "key_windowBackgroundWhiteBlueText", bold=True)
        tv.setGravity(17)
        tv.setAllCaps(False)
        tv.setMinHeight(self._dp(48))
        p = self._dp(12)
        tv.setPadding(p, self._dp(8), p, self._dp(8))
        gd = _GD()
        gd.setColor(0)
        gd.setCornerRadius(float(self._dp(14)))
        gd.setStroke(self._dp(1.2), self._color("key_divider"))
        tv.setBackground(self._ripple(gd, self._alpha(self._accent(), 30)))
        tv.setClickable(True)
        tv.setFocusable(True)
        tv.setOnClickListener(self.on_click(lambda v: on_click()))
        return tv

    def _square_btn(self, ctx, drawable_name, on_click):
        iv = jclass("android.widget.ImageView")(ctx)
        try:
            iv.setImageResource(getattr(jclass("org.telegram.messenger.R$drawable"), drawable_name))
            iv.setScaleType(jclass("android.widget.ImageView$ScaleType").CENTER_INSIDE)
            iv.setColorFilter(self._accent(), jclass("android.graphics.PorterDuff$Mode").SRC_IN)
        except Exception:
            pass
        p = self._dp(13)
        iv.setPadding(p, p, p, p)
        iv.setBackground(self._ripple(self._rounded(self._alpha(self._accent(), 0x14), 14),
                                      self._alpha(self._accent(), 30)))
        iv.setClickable(True)
        iv.setFocusable(True)
        iv.setOnClickListener(self.on_click(lambda v: on_click()))
        return iv

    def _list_row(self, ctx, icon, title, subtitle="", value="", on_click=None,
                  checked=None, danger=False):
        """Строка-настройка в белой карточке: бейдж-иконка + заголовок(+подпись) +
        значение справа / галочка (для тумблеров). Кликабельная (ripple)."""
        row = self._card(ctx, clickable=bool(on_click), on_click=on_click, vertical=False)
        row.addView(self._badge(ctx, icon, 46), _LLP(self._dp(46), self._dp(46)))
        mid = _LL(ctx)
        mid.setOrientation(1)
        title_color = "key_text_RedRegular" if danger else "key_windowBackgroundWhiteBlackText"
        mid.addView(self._tv(ctx, title, 16, title_color, bold=True))
        if subtitle:
            mid.addView(self._tv(ctx, subtitle, 13, "key_windowBackgroundWhiteGrayText2"),
                        self._lp(-1, -2, top=2))
        row.addView(mid, self._lp(0, -2, left=13, weight=1.0))
        if checked is not None:
            mark = self._tv(ctx, "✓" if checked else "", 18,
                            "key_windowBackgroundWhiteBlueText", bold=True)
            row.addView(mark)
        elif value:
            row.addView(self._tv(ctx, value, 14, "key_windowBackgroundWhiteBlueText"))
        return row

    # ---------------------------------------------------------------- сборка экрана
    def _build_advanced(self, ctx):
        col = _LL(ctx)
        col.setOrientation(1)
        col.setBackgroundColor(self._color("key_windowBackgroundGray"))
        col.setPadding(self._dp(12), self._dp(12), self._dp(12), self._dp(24))

        calls = self.get_setting("calls_via_proxy", "0") == "1"

        def toggle_calls():
            new = "0" if self.get_setting("calls_via_proxy", "0") == "1" else "1"
            self.set_setting("calls_via_proxy", new)
            if self.get_setting("conn", "0") == "1":
                if new == "1":
                    self._install_call_hooks()
                else:
                    self._remove_call_hooks()
            self.bulletin("Звонки через прокси: " + ("вкл" if new == "1" else "выкл"), kind="info")
            self._rebuild_advanced()

        failover = self.get_setting("failover", "0") == "1"

        def toggle(key, default="0"):
            def h():
                new = "0" if self.get_setting(key, default) == "1" else "1"
                self.set_setting(key, new)
                self._rebuild_advanced()
            return h

        # --- Подключение ---
        col.addView(self._tv(ctx, "ПОДКЛЮЧЕНИЕ", 12, "key_windowBackgroundWhiteGrayText2", bold=True),
                    self._lp(-1, -2, top=4, left=6))
        col.addView(self._list_row(
            ctx, "msg_language", "Тип проверки задержки",
            "Через туннель (весь путь) или TCP до сервера",
            value=("Туннель" if self._ping_type() == "proxy" else "TCP"),
            on_click=lambda: self._pick_ping_type(ctx)), self._lp(-1, -2, top=8))
        col.addView(self._list_row(
            ctx, "msg_stats", "Автопроверка задержки",
            "Периодически и всегда по TCP, чтобы не прерывать подключение",
            value=self._autocheck_label(),
            on_click=lambda: self._pick_autocheck(ctx)), self._lp(-1, -2, top=10))
        col.addView(self._list_row(
            ctx, "msg_retry", "Менять сервер при обрыве",
            "Failover — переключиться на другой сервер",
            on_click=toggle("failover"), checked=failover), self._lp(-1, -2, top=10))
        auto_start = self.get_setting("auto_start", "0") == "1"
        col.addView(self._list_row(
            ctx, "msg_msgbubble", "Подключаться при запуске",
            "Автоматически включать туннель при старте приложения",
            on_click=toggle("auto_start"), checked=auto_start), self._lp(-1, -2, top=10))

        # --- Подписки ---
        col.addView(self._tv(ctx, "ПОДПИСКИ И СЕРВЕРЫ", 12, "key_windowBackgroundWhiteGrayText2", bold=True),
                    self._lp(-1, -2, top=16, left=6))
        col.addView(self._list_row(
            ctx, "msg_autodelete", "Авто-обновление подписок",
            "Периодически тянуть подписку заново",
            value=self._autoref_label(),
            on_click=lambda: self._pick_autoref(ctx)), self._lp(-1, -2, top=8))
        col.addView(self._list_row(
            ctx, "msg_reset", "Пингануть все серверы",
            "Замерить задержку каждого",
            on_click=self._ping_all), self._lp(-1, -2, top=10))

        # --- Звонки ---
        col.addView(self._tv(ctx, "ЗВОНКИ", 12, "key_windowBackgroundWhiteGrayText2", bold=True),
                    self._lp(-1, -2, top=16, left=6))
        col.addView(self._list_row(
            ctx, "msg_calls", "Звонки через прокси",
            "Состояние: " + self._calls_state(),
            on_click=toggle_calls, checked=calls), self._lp(-1, -2, top=8))

        # --- Прочее ---
        col.addView(self._tv(ctx, "ПРОЧЕЕ", 12, "key_windowBackgroundWhiteGrayText2", bold=True),
                    self._lp(-1, -2, top=16, left=6))
        col.addView(self._list_row(
            ctx, "msg_log", "Показать лог",
            "Диагностика подключения (копируется)",
            on_click=lambda: self._show_log(ctx)), self._lp(-1, -2, top=8))
        col.addView(self._list_row(
            ctx, "msg_delete", "Удалить все серверы", "",
            on_click=self._on_clear, danger=True), self._lp(-1, -2, top=10))

        # --- О компонентах ---
        ver = self._core_version()
        col.addView(self._tv(
            ctx, "Ядро: sing-box %s  ·  плагин %s" % (ver, self.version),
            12, "key_windowBackgroundWhiteGrayText2"), self._lp(-1, -2, top=18, left=6))

        self._adv_col = col
        return col

    def _pick_ping_type(self, ctx):
        labels = ["Через туннель (весь путь)", "TCP до сервера"]
        cur = 0 if self._ping_type() == "proxy" else 1

        def on_sel(which):
            self.set_setting("ping_type", "proxy" if which == 0 else "tcp")
            self._rebuild_advanced()
        self._radio_dialog(ctx, "Тип проверки задержки", labels, cur, on_sel)

    def _autocheck_label(self):
        m = self._auto_check_minutes()
        if m <= 0:
            return "выкл"
        return "1 час" if m == 60 else ("6 часов" if m == 360 else "%d мин" % m)

    def _pick_autocheck(self, ctx):
        labels = ["выкл", "каждые 15 мин", "каждый час", "каждые 6 часов"]
        cur = self.AUTO_CHECK_MINUTES.index(self._auto_check_minutes())

        def on_sel(which):
            self.set_setting("auto_check_minutes", str(self.AUTO_CHECK_MINUTES[which]))
            self._rebuild_advanced()
        self._radio_dialog(ctx, "Автопроверка задержки", labels, cur, on_sel)

    def _rebuild_advanced(self):
        """Перестроить экран «Дополнительно» на месте (обновить галочку/значение)."""
        def upd():
            col = getattr(self, "_adv_col", None)
            if col is None:
                return
            try:
                ctx = col.getContext()
                fresh = self._build_advanced(ctx)
                parent = col.getParent()
                if parent is not None:
                    parent.removeView(col)
                    parent.addView(fresh)
            except Exception as e:
                self.log("rebuild_advanced: " + str(e))
        self.run_on_ui(upd)

    # ============================================================== экран «Источник серверов»
    _PROTO_FILTER = ("", "vless", "vmess", "trojan", "shadowsocks", "hysteria", "hysteria2", "tuic")

    def _open_sources(self):
        try:
            frag = get_last_fragment()
            if frag is None:
                self.bulletin("Экран недоступен", kind="error")
                return
            frag.presentFragment(self._make_screen("Источник серверов", self._build_sources))
        except Exception as e:
            self._flog("open sources: " + str(e))
            self.bulletin("Не удалось открыть: " + str(e), kind="error")

    def _rebuild_sources(self):
        def upd():
            col = getattr(self, "_src_col", None)
            if col is None:
                return
            try:
                fresh = self._build_sources(col.getContext())
                parent = col.getParent()
                if parent is not None:
                    parent.removeView(col)
                    parent.addView(fresh)
            except Exception as e:
                self.log("rebuild_sources: " + str(e))
        self.run_on_ui(upd)

    def _filtered_servers(self):
        proto = self.get_setting("filter_proto", "")
        query = self.get_setting("filter_query", "").strip().lower()
        out = []
        for i, s in enumerate(self._servers()):
            t = str(s.get("outbound", {}).get("type", "")).lower()
            if proto and t != proto:
                continue
            if query and query not in str(s.get("name", "")).lower():
                continue
            out.append((i, s))
        return out

    def _section(self, ctx, col, title):
        col.addView(self._tv(ctx, title, 12, "key_windowBackgroundWhiteGrayText2", bold=True),
                    self._lp(-1, -2, top=16, left=6))

    def _per_page(self):
        try:
            v = int(self.get_setting("per_page", "20"))
        except Exception:
            v = 20
        return v if v in (10, 20, 50, 100) else 20

    def _build_sources(self, ctx):
        # Структура 1:1 с exitFy (fragment c0): ИСТОЧНИК И ФИЛЬТРЫ / ДЕЙСТВИЯ /
        # СОХРАНЁННЫЕ ПОДПИСКИ / СЕРВЕРЫ (с пагинацией). Провайдер-пункты (их бэкенд) опущены.
        col = _LL(ctx)
        col.setOrientation(1)
        col.setBackgroundColor(self._color("key_windowBackgroundGray"))
        col.setPadding(self._dp(12), self._dp(12), self._dp(12), self._dp(24))

        subs = self._subs()
        flt = self._filtered_servers()

        # ===== ИСТОЧНИК И ФИЛЬТРЫ =====
        self._section(ctx, col, "ИСТОЧНИК И ФИЛЬТРЫ")
        q = self.get_setting("filter_query", "").strip()
        col.addView(self._list_row(
            ctx, "msg_edit", "Поиск", "По имени сервера или источника",
            value=(q if q else None),
            on_click=lambda: self._input_dialog(ctx, "Поиск серверов", "Имя сервера", self._on_search)),
            self._lp(-1, -2, top=8))
        proto = self.get_setting("filter_proto", "")
        col.addView(self._list_row(
            ctx, "msg_settings", "Протокол", "Показывать только выбранный протокол",
            value=(proto.upper() if proto else "Все"),
            on_click=lambda: self._pick_proto(ctx)), self._lp(-1, -2, top=10))

        # ===== ДЕЙСТВИЯ =====
        self._section(ctx, col, "ДЕЙСТВИЯ")
        col.addView(self._list_row(
            ctx, "msg_retry", "Обновить подписки",
            "Загрузить свежие серверы из сохранённых источников",
            on_click=self._refresh_all_subs), self._lp(-1, -2, top=8))
        col.addView(self._list_row(
            ctx, "msg_speed", "Проверить страницу",
            "Пинг видимых серверов",
            on_click=self._ping_page), self._lp(-1, -2, top=10))
        col.addView(self._list_row(
            ctx, "msg_edit", "Добавить ключ сервера",
            "VLESS, VMess, Trojan, Shadowsocks, Hysteria или TUIC",
            on_click=lambda: self._input_dialog(ctx, "Добавить ключ сервера",
                                                "vless://…", self._on_add_key)), self._lp(-1, -2, top=10))
        col.addView(self._list_row(
            ctx, "msg_download", "Добавить подписку",
            "HTTP- или HTTPS-ссылка",
            on_click=lambda: self._input_dialog(ctx, "Добавить подписку",
                                                "https://…", self._on_add_sub_url)), self._lp(-1, -2, top=10))
        col.addView(self._list_row(
            ctx, "msg_delete", "Очистить серверы",
            "Подписки сохранятся, подключение выключится",
            on_click=self._on_clear, danger=True), self._lp(-1, -2, top=10))

        # ===== СОХРАНЁННЫЕ ПОДПИСКИ =====
        self._section(ctx, col, "СОХРАНЁННЫЕ ПОДПИСКИ")
        if subs:
            for sub in subs:
                cnt = sub.get("count")
                sub_txt = ("Серверов: %d" % cnt) if isinstance(cnt, int) else "Нажмите обновить"
                card = self._card(ctx, vertical=False)
                card.addView(self._badge(ctx, "msg_folders", 46), _LLP(self._dp(46), self._dp(46)))
                mid = _LL(ctx)
                mid.setOrientation(1)
                mid.addView(self._tv(ctx, sub.get("name") or self._sub_name(sub.get("url", "")),
                                     16, "key_windowBackgroundWhiteBlackText", bold=True))
                mid.addView(self._tv(ctx, sub_txt, 13, "key_windowBackgroundWhiteGrayText2"),
                            self._lp(-1, -2, top=2))
                card.addView(mid, self._lp(0, -2, left=13, weight=1.0))
                card.addView(self._square_btn(ctx, "msg_retry",
                             (lambda u: (lambda: self._refresh_one_sub(u)))(sub.get("url"))),
                             self._lp(self._dp(44), self._dp(44), left=6))
                card.addView(self._square_btn(ctx, "msg_delete",
                             (lambda u: (lambda: self._del_sub(u)))(sub.get("url"))),
                             self._lp(self._dp(44), self._dp(44), left=6))
                col.addView(card, self._lp(-1, -2, top=8))
        else:
            col.addView(self._tv(ctx, "Сохранённых подписок нет.", 13,
                                 "key_windowBackgroundWhiteGrayText2"), self._lp(-1, -2, top=8, left=6))

        # ===== СЕРВЕРЫ (с пагинацией) =====
        per = self._per_page()
        total = len(flt)
        pages = max(1, (total + per - 1) // per)
        try:
            page = int(self.get_setting("srv_page", "0"))
        except Exception:
            page = 0
        page = max(0, min(page, pages - 1))
        self._section(ctx, col, "СЕРВЕРЫ (%d)" % total)
        active_i = self._active_index()
        start = page * per
        for idx, s in flt[start:start + per]:
            proto_t = str(s.get("outbound", {}).get("type", "")).upper()
            ping = s.get("ping", None)
            tail = ("  ·  %d мс" % ping) if (isinstance(ping, int) and ping >= 0) else ""
            checked = (idx == active_i)
            col.addView(self._list_row(
                ctx, "drawer_proxy_on" if checked else "msg_language",
                str(s.get("name") or "Сервер"), proto_t + tail,
                on_click=(lambda j: (lambda: self._select_and_close(j)))(idx),
                checked=checked if checked else None), self._lp(-1, -2, top=8))
        if not flt:
            col.addView(self._tv(ctx, "Серверов нет. Добавьте ключ или подписку.",
                                 13, "key_windowBackgroundWhiteGrayText2"), self._lp(-1, -2, top=8, left=6))

        # пагинация (только если >1 страницы)
        if pages > 1:
            nav = _LL(ctx)
            nav.setOrientation(0)
            prev_b = self._outline_button(ctx, "‹ Назад",
                                          (lambda: self._srv_page(page - 1, pages)))
            next_b = self._outline_button(ctx, "Вперёд ›",
                                          (lambda: self._srv_page(page + 1, pages)))
            nav.addView(prev_b, self._lp(0, -2, weight=1.0))
            nav.addView(next_b, self._lp(0, -2, left=8, weight=1.0))
            col.addView(nav, self._lp(-1, -2, top=10))
            col.addView(self._tv(ctx, "Страница %d из %d" % (page + 1, pages), 12,
                                 "key_windowBackgroundWhiteGrayText2"), self._lp(-1, -2, top=6, left=6))
        col.addView(self._list_row(
            ctx, "msg_stats", "Серверов на странице", "",
            value=str(per), on_click=lambda: self._pick_perpage(ctx)), self._lp(-1, -2, top=10))

        self._src_col = col
        return col

    def _srv_page(self, p, pages):
        self.set_setting("srv_page", str(max(0, min(p, pages - 1))))
        self._rebuild_sources()

    def _pick_perpage(self, ctx):
        vals = (10, 20, 50, 100)
        labels = [str(v) for v in vals]
        cur = vals.index(self._per_page()) if self._per_page() in vals else 1

        def on_sel(which):
            self.set_setting("per_page", str(vals[which]))
            self.set_setting("srv_page", "0")
            self._rebuild_sources()
        self._radio_dialog(ctx, "Серверов на странице", labels, cur, on_sel)

    def _refresh_all_subs(self):
        subs = self._subs()
        if not subs:
            self.bulletin("Нет сохранённых подписок", kind="info")
            return
        self.bulletin("Обновляю подписки…", kind="info")

        def worker():
            total = 0
            for sub in subs:
                total += self._fetch_subscription(sub.get("url"))
            self.bulletin("Обновлено: %d серверов" % total, kind="success")
            self._rebuild_sources()
            self._refresh_ui()
        self.run_on_queue(worker)

    def _ping_page(self):
        per = self._per_page()
        try:
            page = int(self.get_setting("srv_page", "0"))
        except Exception:
            page = 0
        flt = self._filtered_servers()[page * per: page * per + per]
        if not flt:
            self.bulletin("На странице нет серверов", kind="info")
            return
        self.bulletin("Проверяю страницу…", kind="info")

        def worker():
            servers = self._servers()
            for idx, s in flt:
                o = s.get("outbound", {})
                p = self._tcp_ping(o.get("server"), int(o.get("server_port") or 0), timeout=4000)
                if 0 <= idx < len(servers):
                    servers[idx]["ping"] = p
            self._save_servers(servers)
            self.bulletin("Готово", kind="success")
            self._rebuild_sources()
        self.run_on_queue(worker)

    # обработчики экрана источников
    def _on_add_key(self, text):
        n = self._add_uri(text, source="manual")
        self.bulletin(("Добавлено: %d" % n) if n else "Не разобрал ссылку", kind="success" if n else "error")
        self._rebuild_sources()
        self._refresh_ui()

    def _on_add_sub_url(self, url):
        url = (url or "").strip()
        if not url.lower().startswith("http"):
            self.bulletin("Подписка должна быть http(s)-ссылкой", kind="error")
            return
        self.bulletin("Загрузка подписки…", kind="info")

        def worker():
            n = self._fetch_subscription(url)
            self.bulletin(("Добавлено из подписки: %d" % n) if n else "Из подписки ничего не добавлено",
                          kind="success" if n else "error")
            self._rebuild_sources()
            self._refresh_ui()
        self.run_on_queue(worker)

    def _refresh_one_sub(self, url):
        self.bulletin("Обновляю…", kind="info")

        def worker():
            n = self._fetch_subscription(url)
            self.bulletin("Обновлено: %d серверов" % n, kind="success")
            self._rebuild_sources()
            self._refresh_ui()
        self.run_on_queue(worker)

    def _del_sub(self, url):
        subs = [s for s in self._subs() if s.get("url") != url]
        self._save_subs(subs)
        servers = [s for s in self._servers() if s.get("source") != url]
        self._save_servers(servers)
        self.bulletin("Подписка удалена", kind="info")
        self._rebuild_sources()
        self._refresh_ui()

    def _pick_proto(self, ctx):
        labels = ["Все протоколы", "VLESS", "VMess", "Trojan", "Shadowsocks", "Hysteria", "Hysteria2", "TUIC"]
        cur = self.get_setting("filter_proto", "")
        sel = self._PROTO_FILTER.index(cur) if cur in self._PROTO_FILTER else 0

        def on_sel(which):
            self.set_setting("filter_proto", self._PROTO_FILTER[which])
            self._rebuild_sources()
        self._radio_dialog(ctx, "Протокол", labels, sel, on_sel)

    def _on_search(self, text):
        self.set_setting("filter_query", (text or "").strip())
        self._rebuild_sources()

    def _select_and_close(self, idx):
        self._select_server(idx)
        self._rebuild_sources()
    def _build_dashboard(self, ctx):
        connected = self.get_setting("conn", "0") == "1"
        active = self._active_server()
        servers = self._servers()

        col = _LL(ctx)
        col.setOrientation(1)
        col.setBackgroundColor(self._color("key_windowBackgroundGray"))
        col.setPadding(self._dp(12), self._dp(10), self._dp(12), self._dp(24))

        # ===== 1. Состояние подключения =====
        c1 = self._card(ctx, vertical=True)
        head = _LL(ctx)
        head.setOrientation(0)
        head.setGravity(16)
        self._ui_icon = self._badge(ctx, "drawer_proxy_on" if connected else "drawer_proxy_off", 56)
        head.addView(self._ui_icon, _LLP(self._dp(56), self._dp(56)))
        info = _LL(ctx)
        info.setOrientation(1)
        self._ui_status = self._tv(ctx, "Подключено" if connected else "Отключено",
                                   21, "key_windowBackgroundWhiteBlackText", bold=True)
        info.addView(self._ui_status)
        self._ui_substatus = self._tv(ctx, (active.get("name") if active else "Сервер не выбран"),
                                      13, "key_windowBackgroundWhiteGrayText")
        info.addView(self._ui_substatus, self._lp(-1, -2, top=2))
        head.addView(info, self._lp(0, -2, left=14, weight=1.0))
        c1.addView(head, _LLP(-1, -2))
        self._ui_btn = self._primary_button(ctx, "Отключиться" if connected else "Подключиться", self._toggle)
        c1.addView(self._ui_btn, self._lp(-1, -2, top=15))
        col.addView(c1, _LLP(-1, -2))

        # ===== 2. Активный сервер =====
        c2 = self._card(ctx, vertical=True)
        row2 = self._accent_row(ctx, on_click=lambda: self._pick_server(ctx))
        row2.addView(self._badge(ctx, "msg_language", 48), _LLP(self._dp(48), self._dp(48)))
        col2 = _LL(ctx)
        col2.setOrientation(1)
        self._ui_server = self._tv(ctx, (active.get("name") if active else "Сервер не выбран"),
                                   18, "key_windowBackgroundWhiteBlackText", bold=True)
        col2.addView(self._ui_server)
        self._ui_server_detail = self._tv(
            ctx, ((active.get("outbound", {}).get("type", "").upper()) if active else "Нажмите, чтобы выбрать"),
            14, "key_windowBackgroundWhiteBlueText")
        col2.addView(self._ui_server_detail, self._lp(-1, -2, top=3))
        row2.addView(col2, self._lp(0, -2, left=13, weight=1.0))
        c2.addView(row2, _LLP(-1, -2))
        c2.addView(self._outline_button(ctx, "Проверить задержку", self._ping_active),
                   self._lp(-1, -2, top=13))
        col.addView(c2, self._lp(-1, -2, top=10))

        # ===== 3. Источник серверов (открывает отдельный экран) =====
        n_subs = len(self._subs())
        n_srv = len(servers)
        c3 = self._card(ctx, clickable=True, vertical=False, on_click=lambda: self._open_sources())
        c3.addView(self._badge(ctx, "msg_folders", 48), _LLP(self._dp(48), self._dp(48)))
        col3 = _LL(ctx)
        col3.setOrientation(1)
        col3.addView(self._tv(ctx, "Источник серверов", 17, "key_windowBackgroundWhiteBlackText", bold=True))
        sub_txt = ("Подписок: %d · серверов: %d" % (n_subs, n_srv)) if (n_subs or n_srv) else "Добавьте ссылку или подписку"
        col3.addView(self._tv(ctx, sub_txt, 13, "key_windowBackgroundWhiteGrayText"), self._lp(-1, -2, top=2))
        c3.addView(col3, self._lp(0, -2, left=13, weight=1.0))
        chev = self._tv(ctx, "›", 22, "key_windowBackgroundWhiteGrayText2")
        c3.addView(chev)
        col.addView(c3, self._lp(-1, -2, top=10))

        # ===== 5. Дополнительно =====
        c5 = self._card(ctx, clickable=True, vertical=False, on_click=lambda: self._advanced(ctx))
        c5.addView(self._badge(ctx, "msg_settings", 48), _LLP(self._dp(48), self._dp(48)))
        col5 = _LL(ctx)
        col5.setOrientation(1)
        col5.addView(self._tv(ctx, "Дополнительно", 17, "key_windowBackgroundWhiteBlackText", bold=True))
        col5.addView(self._tv(ctx, "Звонки через прокси, очистка серверов", 13, "key_windowBackgroundWhiteGrayText"),
                     self._lp(-1, -2, top=2))
        c5.addView(col5, self._lp(0, -2, left=13, weight=1.0))
        col.addView(c5, self._lp(-1, -2, top=10))

        return col

    def _refresh_ui(self):
        def upd():
            connected = self.get_setting("conn", "0") == "1"
            active = self._active_server()
            try:
                if getattr(self, "_ui_status", None) is not None:
                    self._ui_status.setText("Подключено" if connected else "Отключено")
                if getattr(self, "_ui_substatus", None) is not None:
                    self._ui_substatus.setText(active.get("name") if active else "Сервер не выбран")
                if getattr(self, "_ui_btn", None) is not None:
                    self._ui_btn.setText("Отключиться" if connected else "Подключиться")
                    self._ui_btn.setEnabled(True)
                if getattr(self, "_ui_icon", None) is not None:
                    try:
                        rd = jclass("org.telegram.messenger.R$drawable")
                        self._ui_icon.setImageResource(rd.drawer_proxy_on if connected else rd.drawer_proxy_off)
                    except Exception:
                        pass
                if getattr(self, "_ui_server", None) is not None:
                    self._ui_server.setText(active.get("name") if active else "Сервер не выбран")
                if getattr(self, "_ui_server_detail", None) is not None:
                    self._ui_server_detail.setText(
                        (active.get("outbound", {}).get("type", "").upper()) if active else "Нажмите, чтобы выбрать")
                self._ui_source_detail = None  # карточка подписки убрана
            except Exception as e:
                self.log("refresh_ui: " + str(e))
        self.run_on_ui(upd)

    def _set_busy(self, busy):
        def upd():
            try:
                if getattr(self, "_ui_btn", None) is not None:
                    self._ui_btn.setEnabled(not busy)
                    if busy:
                        self._ui_btn.setText("…")
            except Exception:
                pass
        self.run_on_ui(upd)

    # ---------------------------------------------------------------- диалоги
    def _activity(self, fallback_ctx=None):
        try:
            frag = get_last_fragment()
            act = frag.getParentActivity() if frag is not None else None
            if act is not None:
                return act
        except Exception:
            pass
        try:
            inst = jclass("org.telegram.ui.LaunchActivity").instance
            if inst is not None:
                return inst
        except Exception:
            pass
        return fallback_ctx or _App.applicationContext

    def _builder(self, act):
        return jclass("org.telegram.ui.ActionBar.AlertDialog$Builder")(act)

    def _btn_listener(self, fn):
        # У Telegram-AlertDialog СВОЙ тип листенера кнопок: onClick(AlertDialog, int).
        BTN = jclass("org.telegram.ui.ActionBar.AlertDialog$OnButtonClickListener")
        return self.implement(BTN, onClick=lambda s, dlg, which: fn())

    def _radio_dialog(self, ctx, title, labels, selected, on_select,
                      neutral=None, on_neutral=None,
                      positive=None, on_positive=None):
        """Порт диалога exitFy: список RadioColorCell (радио-кнопки, подсветка выбранного,
        selector-фон), заголовок, опц. нейтральная кнопка («Все серверы»/«Удалить»).
        labels — список str; selected — индекс отмеченного; on_select(i)."""
        act = self._activity(ctx)
        RadioColorCell = jclass("org.telegram.ui.Cells.RadioColorCell")
        Builder = jclass("org.telegram.ui.ActionBar.AlertDialog$Builder")
        box = _LL(act)
        box.setOrientation(1)
        holder = {"dlg": None}
        for i, lab in enumerate(labels):
            cell = RadioColorCell(act)
            cell.setPadding(self._dp(4), 0, self._dp(4), 0)
            cell.setCheckColor(self._color("key_radioBackground"), self._color("key_dialogRadioBackgroundChecked"))
            cell.setTextAndValue(str(lab), i == selected)
            cell.setBackground(_Theme.createSelectorDrawable(self._color("key_listSelector"), 2))

            def mk(idx):
                def h(v):
                    try:
                        if holder["dlg"] is not None:
                            holder["dlg"].dismiss()
                    except Exception:
                        pass
                    on_select(idx)
                return h
            cell.setOnClickListener(self.on_click(mk(i)))
            box.addView(cell, _LLP(-1, -2))
        b = Builder(act)
        b.setTitle(title)
        try:
            b.makeCustomMaxHeight()
        except Exception:
            pass
        b.setView(box)
        if neutral and on_neutral:
            b.setNeutralButton(neutral, self._btn_listener(on_neutral))
        b.setNegativeButton("Отмена", None)
        dlg = b.create()
        holder["dlg"] = dlg
        dlg.show()

    def _server_label(self, i, s):
        name = str(s.get("name") or ("Сервер %d" % (i + 1)))
        proto = str(s.get("outbound", {}).get("type", "")).upper()
        ping = s.get("ping", None)
        tail = ""
        if ping is not None and ping >= 0:
            tail = "  ·  %d мс" % ping
        elif ping is not None and ping < 0:
            tail = "  ·  —"
        return name + ("  ·  " + proto if proto else "") + tail

    def _pick_server(self, ctx):
        servers = self._servers()
        if not servers:
            self._input_dialog(ctx, "Ссылка сервера", "vless://…", self._on_add_node)
            return
        labels = [self._server_label(i, s) for i, s in enumerate(servers)]
        title = "Сервер (%d)" % len(servers)
        self._radio_dialog(
            ctx, title, labels, self._active_index(), self._select_server,
            neutral="Удалить", on_neutral=lambda: self._delete_dialog(ctx))

    def _delete_dialog(self, ctx):
        servers = self._servers()
        if not servers:
            return
        labels = [self._server_label(i, s) for i, s in enumerate(servers)]
        self._radio_dialog(ctx, "Удалить сервер", labels, -1, self._delete_server)

    def _delete_server(self, idx):
        servers = self._servers()
        if 0 <= idx < len(servers):
            name = servers[idx].get("name")
            del servers[idx]
            self._save_servers(servers)
            ai = self._active_index()
            if ai >= len(servers):
                self.set_setting("active", "0")
            self.bulletin("Удалён: " + str(name), kind="info")
            self._refresh_ui()

    def _select_server(self, idx):
        self.set_setting("active", str(int(idx)))
        self._refresh_ui()
        if self.get_setting("conn", "0") == "1":
            self._connect()

    def _input_dialog(self, ctx, title, hint, callback):
        act = self._activity(ctx)
        et = jclass("org.telegram.ui.Components.EditTextBoldCursor")(act)
        et.setTextSize(_DIP, 16.0)
        et.setTextColor(self._color("key_dialogTextBlack"))
        et.setCursorColor(self._color("key_dialogTextBlack"))
        try:
            et.setHintTextColor(self._color("key_dialogTextHint"))
        except Exception:
            pass
        et.setHint(hint)
        box = _LL(act)
        box.setOrientation(1)
        box.setPadding(self._dp(22), self._dp(6), self._dp(22), 0)
        box.addView(et, _LLP(-1, -2))
        b = self._builder(act)
        b.setTitle(title)
        b.setView(box)
        b.setPositiveButton("Добавить", self._btn_listener(lambda: callback(str(et.getText()))))
        b.setNegativeButton("Отмена", None)
        b.show()
        try:
            et.requestFocus()
            _AU.showKeyboard(et)
        except Exception:
            pass

    _AUTOREF_CHOICES = (0, 30, 60, 360, 720)  # минуты (0 = выкл)

    def _autoref_label(self):
        try:
            m = int(self.get_setting("autoref_min", "0"))
        except Exception:
            m = 0
        if m <= 0:
            return "выкл"
        if m < 60:
            return "%d мин" % m
        return "%d ч" % (m // 60)

    def _advanced(self, ctx):
        # Полноэкранный экран-карточки (как дашборд), а не серый список.
        try:
            frag = get_last_fragment()
            if frag is None:
                self.bulletin("Экран недоступен", kind="error")
                return
            frag.presentFragment(self._make_screen("Дополнительно", self._build_advanced))
        except Exception as e:
            self._flog("open advanced: " + str(e))
            self.bulletin("Не удалось открыть: " + str(e), kind="error")

    def _pick_autoref(self, ctx):
        labels = ["выкл", "30 мин", "1 час", "6 часов", "12 часов"]
        try:
            cur = int(self.get_setting("autoref_min", "0"))
        except Exception:
            cur = 0
        sel = self._AUTOREF_CHOICES.index(cur) if cur in self._AUTOREF_CHOICES else 0

        def on_sel(which):
            self.set_setting("autoref_min", str(self._AUTOREF_CHOICES[which]))
            self.bulletin("Авто-обновление: " + labels[which], kind="info")
        self._radio_dialog(ctx, "Авто-обновление подписок", labels, sel, on_sel)

    def _read_log(self):
        try:
            path = self._files_dir() + "/tunnel.log"
            f = jclass("java.io.File")(path)
            if not f.exists():
                return "(лог пуст)"
            n = int(f.length())
            fis = jclass("java.io.FileInputStream")(f)
            skip = max(0, n - 12000)  # последние ~12 КБ
            if skip:
                fis.skip(skip)
            buf = _Array.newInstance(jclass("java.lang.Byte").TYPE, n - skip)
            fis.read(buf)
            fis.close()
            return str(jclass("java.lang.String")(buf, "UTF-8"))
        except Exception as e:
            return "ошибка чтения лога: " + str(e)

    def _show_log(self, ctx):
        text = self._read_log()
        try:
            self.copy(text)
        except Exception:
            pass
        act = self._activity(ctx)
        b = self._builder(act)
        b.setTitle("Лог Туннеля (скопирован)")
        b.setMessage(text[-3500:])
        try:
            b.setMessageTextViewClickable(False)
        except Exception:
            pass
        b.setPositiveButton("OK", None)
        b.show()

    # ---------------------------------------------------------------- действия
    def _on_add(self, text):
        """Единый ввод: сам понимает, ссылка(и)-конфиг это или URL подписки."""
        text = (text or "").strip()
        if not text:
            return
        # подписка = http(s)-ссылка БЕЗ протокольной схемы конфига внутри
        low = text.lower()
        looks_config = any(("%s://" % p) in low for p in _PROTos)
        if (low.startswith("http://") or low.startswith("https://")) and not looks_config:
            self._on_add_sub(text)
            return
        n = self._add_uri(text)
        self.bulletin(("Добавлено серверов: %d" % n) if n else "Не похоже на ссылку/подписку",
                      kind="success" if n else "error")
        self._refresh_ui()

    def _on_add_node(self, text):
        # оставлено для совместимости — теперь всё через умный _on_add
        self._on_add(text)

    def _on_add_sub(self, url):
        url = (url or "").strip()
        if not url:
            return
        self.bulletin("Загрузка подписки…", kind="info")

        def worker():
            n = self._fetch_subscription(url)
            self.set_setting("sub_url", url)
            self.bulletin(("Из подписки добавлено: %d" % n) if n else "Из подписки ничего не добавлено",
                          kind="success" if n else "error")
            self._refresh_ui()
        self.run_on_queue(worker)

    def _refresh_subs(self):
        url = self.get_setting("sub_url", "").strip()
        if not url:
            self.bulletin("Подписка не задана", kind="info")
            return
        self._on_add_sub(url)

    # -------------------------------------------------------------- пинг
    def _tcp_ping(self, host, port, timeout=5000):
        """RTT прямого TCP-коннекта до host:port в мс, либо -1."""
        import socket
        s = None
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(timeout / 1000.0)
            t0 = time.time()
            s.connect((host, int(port)))
            return int((time.time() - t0) * 1000)
        except Exception:
            return -1
        finally:
            try:
                if s is not None:
                    s.close()
            except Exception:
                pass

    def _tunnel_ping(self, timeout=8000):
        """RTT ЧЕРЕЗ туннель: SOCKS5 CONNECT к www.gstatic.com:80 + запрос, в мс, либо -1."""
        import socket
        import struct
        s = None
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(timeout / 1000.0)
            s.connect((_LISTEN, _LOCAL_PORT))
            s.sendall(b"\x05\x01\x00")
            if s.recv(2) != b"\x05\x00":
                return -1
            host = b"www.gstatic.com"
            s.sendall(b"\x05\x01\x00\x03" + bytes([len(host)]) + host + struct.pack(">H", 80))
            if s.recv(10)[1:2] != b"\x00":
                return -1
            t0 = time.time()
            s.sendall(b"GET /generate_204 HTTP/1.1\r\nHost: www.gstatic.com\r\nConnection: close\r\n\r\n")
            data = s.recv(32)
            if not data:
                return -1
            return int((time.time() - t0) * 1000)
        except Exception:
            return -1
        finally:
            try:
                if s is not None:
                    s.close()
            except Exception:
                pass

    def _ping_type(self):
        return self.get_setting("ping_type", "proxy")   # proxy | tcp

    def _ping_active(self, silent=False):
        server = self._active_server()
        if not server:
            if not silent:
                self.bulletin("Сервер не выбран", kind="error")
            return
        out = server.get("outbound", {})
        host, port = out.get("server"), int(out.get("server_port") or 0)
        if not silent:
            self.bulletin("Проверка задержки…", kind="info")

        def worker():
            connected = self.get_setting("conn", "0") == "1"
            # тип пинга: proxy = через туннель (весь путь), tcp = до сервера
            if self._ping_type() == "proxy" and connected:
                dt = self._tunnel_ping()
                label = "через туннель"
            else:
                dt = self._tcp_ping(host, port)
                label = "до сервера"
            if dt >= 0:
                if not silent:
                    self.bulletin("Задержка %s: %d мс" % (label, dt), kind="success")
                self._live_ping = dt
                self._refresh_ui()
            else:
                self._live_ping = -1
                if not silent:
                    self.bulletin("Сервер недоступен", kind="error")
        self.run_on_queue(worker)

    def _ping_all(self):
        """Пингануть все серверы (прямой TCP), сохранить мс в каждый и обновить список."""
        self.bulletin("Пингую серверы…", kind="info")

        def worker():
            servers = self._servers()
            for s in servers:
                o = s.get("outbound", {})
                s["ping"] = self._tcp_ping(o.get("server"), int(o.get("server_port") or 0), timeout=4000)
            self._save_servers(servers)
            best = min((s.get("ping", -1) for s in servers if s.get("ping", -1) >= 0), default=-1)
            self.bulletin(("Готово. Лучший: %d мс" % best) if best >= 0 else "Ни один сервер не ответил",
                          kind="success" if best >= 0 else "error")
            self._refresh_ui()
        self.run_on_queue(worker)

    def _on_clear(self):
        self._save_servers([])
        self.set_setting("active", "0")
        self.bulletin("Серверы удалены", kind="info")
        self._refresh_ui()

    # ============================================================== живой статус (аптайм)
    def _uptime(self):
        if not getattr(self, "_connect_ts", 0):
            return ""
        sec = int(time.time() - self._connect_ts)
        h, m, s = sec // 3600, (sec % 3600) // 60, sec % 60
        return ("%d:%02d:%02d" % (h, m, s)) if h else ("%02d:%02d" % (m, s))

    def _start_tick(self):
        self._tick_gen += 1
        gen = self._tick_gen

        def tick():
            if gen != self._tick_gen or self.get_setting("conn", "0") != "1":
                return
            try:
                if getattr(self, "_ui_substatus", None) is not None:
                    active = self._active_server()
                    nm = str(active.get("name")) if active else ""
                    up = self._uptime()
                    lp = getattr(self, "_live_ping", None)
                    ping_s = ("  ·  %d мс" % lp) if (lp is not None and lp >= 0) else ""
                    self._ui_substatus.setText((nm + " · " + up + ping_s) if up else nm)
            except Exception:
                pass
            self.run_on_ui(tick, 1000)

        self.run_on_ui(tick, 1000)
        self._start_watchdog()

    # ============================================================== watchdog: авто-пинг + failover
    # Как у exitFy: автопроверка задержки — интервал в МИНУТАХ (0/15/60/360), ВСЕГДА по TCP
    # (чтобы не прерывать подключение). Failover — отдельный лёгкий чек живости туннеля.
    AUTO_CHECK_MINUTES = (0, 15, 60, 360)

    def _auto_check_minutes(self):
        try:
            v = int(self.get_setting("auto_check_minutes", "0"))
        except Exception:
            v = 0
        return v if v in self.AUTO_CHECK_MINUTES else 0

    def _start_watchdog(self):
        self._wd_gen = getattr(self, "_wd_gen", 0) + 1
        gen = self._wd_gen
        self._wd_fails = 0
        self._wd_last_autocheck = time.time()

        def beat():
            if gen != self._wd_gen or self.get_setting("conn", "0") != "1":
                return

            def check():
                # 1) Автопроверка задержки — раз в N минут, ВСЕГДА TCP (не рвёт коннект)
                mins = self._auto_check_minutes()
                if mins > 0 and (time.time() - self._wd_last_autocheck) >= mins * 60:
                    self._wd_last_autocheck = time.time()
                    srv = self._active_server()
                    if srv:
                        o = srv.get("outbound", {})
                        dt = self._tcp_ping(o.get("server"), int(o.get("server_port") or 0), timeout=5000)
                        self._live_ping = dt
                        self._flog("автопроверка (TCP): %d мс" % dt)
                        self._refresh_ui()
                # 2) Failover — быстрый TCP-чек живости сервера каждый тик (60с)
                if self.get_setting("failover", "0") == "1":
                    srv = self._active_server()
                    if srv:
                        o = srv.get("outbound", {})
                        alive = self._tcp_ping(o.get("server"), int(o.get("server_port") or 0), timeout=5000) >= 0
                        if alive:
                            self._wd_fails = 0
                        else:
                            self._wd_fails += 1
                            self._flog("watchdog: сервер не отвечает %d/2" % self._wd_fails)
                            if self._wd_fails >= 2:
                                self._wd_fails = 0
                                self._failover_next()
                self.run_on_ui(beat, 60000)
            self.run_on_queue(check)

        self.run_on_ui(beat, 60000)

    def _failover_next(self):
        servers = self._servers()
        if len(servers) < 2:
            return
        cur = self._active_index()
        nxt = (cur + 1) % len(servers)
        self._flog("failover: %d → %d (%s)" % (cur, nxt, servers[nxt].get("name")))
        self.set_setting("active", str(nxt))
        self.bulletin("Обрыв — переключаюсь: " + str(servers[nxt].get("name")), kind="info")
        self._connect()

    # ============================================================== версия компонентов
    def _core_version(self):
        try:
            import sys
            holder = sys.__dict__.get("_devgram_tunnel_native")
            if holder and holder.get("libbox"):
                return str(self._call_static(holder["libbox"], "version"))
        except Exception:
            pass
        return "не загружено"

    def _calls_state(self):
        relays = getattr(self, "_call_relays", {}) or {}
        on = self.get_setting("calls_via_proxy", "0") == "1"
        if not on:
            return "выкл"
        if not self._call_hooks:
            return "вкл (ожидание)"
        return "вкл · релеев звонка: %d (нативный relay в ядре)" % len(relays)

    # ============================================================== авто-обновление подписок
    def _schedule_autorefresh(self):
        self._autoref_gen += 1
        gen = self._autoref_gen
        self._autoref_last = time.time()

        def loop():
            if gen != self._autoref_gen:
                return
            try:
                mins = int(self.get_setting("autoref_min", "0"))
            except Exception:
                mins = 0
            if mins > 0 and (time.time() - getattr(self, "_autoref_last", 0)) >= mins * 60:
                self._autoref_last = time.time()
                url = self.get_setting("sub_url", "").strip()
                if url:
                    self.run_on_queue(lambda: self._fetch_subscription(url))
            self.run_on_ui(loop, 60000)

        self.run_on_ui(loop, 60000)

    # ============================================================== звонки через прокси
    # Голос идёт по UDP к релеям Telegram. Заворачиваем НАТИВНО силами sing-box:
    # для каждого релея — direct-inbound (network=udp, override_address/port) на локальном
    # порту. Telegram шлёт на 127.0.0.1:port → ядро гонит на релей через VLESS. Питона в
    # пути данных НЕТ (в отличие от прошлой SOCKS-UDP-версии, которая не работала).
    # Пул локальных портов фиксирован → endpoint подменяется мгновенно в хуке, а ядро
    # с нужными inbound'ами пересобирается в фоне (короткое моргание туннеля — Telegram
    # переживает благодаря ICE-ретраям).
    _CALL_PORT_BASE = 8970          # локальные порты call-relay: 8970..8977

    def _relay_local_port(self, ip, port):
        """Назначить (или вернуть) стабильный локальный порт для релея ip:port."""
        cr = getattr(self, "_call_relays", None)
        if cr is None:
            cr = self._call_relays = {}   # (ip,port) -> local_port
        key = (str(ip), int(port))
        if key in cr:
            return (cr[key], False)          # уже был — пересборка не нужна
        if len(cr) >= 8:
            return (-1, False)
        lp = self._CALL_PORT_BASE + len(cr)
        cr[key] = lp
        return (lp, True)                    # новый — понадобится пересборка ядра

    def _rebuild_core_now(self):
        """СИНХРОННО пересобрать ядро с текущими call-relay — чтобы relay-порты уже
        слушали к моменту, когда Telegram/WebRTC начнёт слать (иначе ICE их отбракует).
        Вызывается прямо в хуке звонка перед возвратом подменённых endpoint'ов."""
        if self.get_setting("conn", "0") != "1":
            return
        server = self._active_server()
        if not server:
            return
        relays = [{"port": lp, "ip": k[0], "rport": k[1]}
                  for k, lp in (getattr(self, "_call_relays", {}) or {}).items()]
        try:
            self._flog("звонок: пересобираю ядро с %d relay-inbound (sync)" % len(relays))
            self._start_service(server.get("outbound"), call_relays=relays)
            self._set_tg_proxy(True)
        except Exception as e:
            self._flog("call rebuild FAILED: " + str(e))

    def _schedule_core_rebuild(self):
        """Асинхронно вернуть базовое ядро (без call-relay) — при завершении звонка."""
        def do():
            self._rebuild_core_now()
        self.run_on_queue(do)

    def _endpoint_class(self):
        for name in ("org.telegram.messenger.voip.Instance$Endpoint",):
            try:
                c = jclass(name)
                if c is not None:
                    return c
            except Exception:
                pass
        return None

    def _install_call_hooks(self):
        if self._call_hooks:
            return
        try:
            self._call_relays = {}    # (ip,port) -> локальный порт
            self._endpoint_cls = self._endpoint_class()
            installed = 0
            # 1:1-звонки: makeInstance(..., Endpoint[] endpoints=args[3], ...) — все перегрузки
            try:
                installed += int(self.hook_all("org.telegram.messenger.voip.Instance", "makeInstance",
                                               before=self._hook_make_instance) or 0)
            except Exception as e:
                self._flog("call hook makeInstance: %s" % e)
            # групповые звонки: setJoinResponsePayload(String payload=args[0])
            try:
                installed += int(self.hook_all("org.telegram.messenger.voip.NativeInstance",
                                               "setJoinResponsePayload",
                                               before=self._hook_join_payload) or 0)
            except Exception as e:
                self._flog("call hook setJoinResponsePayload: %s" % e)
            self._call_hooks = [True] if installed else []
            self._flog("хуки звонков установлены: %d (endpoint_cls=%s)"
                       % (installed, "ok" if self._endpoint_cls else "нет"))
        except Exception as e:
            self._flog("install_call_hooks: " + str(e))

    def _remove_call_hooks(self):
        if self._call_hooks:
            try:
                self.unhook_all()   # у плагина других хуков нет — снимаем все
            except Exception:
                pass
        self._call_hooks = []
        # сбрасываем relay-inbound'ы и возвращаем базовое ядро (без call-relay)
        had = bool(getattr(self, "_call_relays", None))
        self._call_relays = {}
        if had and self.get_setting("conn", "0") == "1":
            self._schedule_core_rebuild()

    def _hook_make_instance(self, frame):
        # makeInstance(version, config, path, Endpoint[] endpoints, proxy, ...) → args[3]
        try:
            args = frame.args
            if len(args) < 4 or args[3] is None or self._endpoint_cls is None:
                return
            endpoints = args[3]
            n = int(jclass("java.lang.reflect.Array").getLength(endpoints))
            replaced = 0
            need_rebuild = False
            for i in range(n):
                ep = jclass("java.lang.reflect.Array").get(endpoints, i)
                if ep is None:
                    continue
                try:
                    if bool(ep.tcp):          # TCP-релеи не заворачиваем (только UDP)
                        continue
                    ip = str(ep.ipv4 or "")
                    port = int(ep.port)
                except Exception:
                    continue
                if not ip or port <= 0:
                    continue
                lp, fresh = self._relay_local_port(ip, port)
                if lp <= 0:
                    continue
                need_rebuild = need_rebuild or fresh
                try:
                    rep = self._endpoint_cls(
                        bool(ep.isRtc), int(ep.id), "127.0.0.1", "", int(lp),
                        int(ep.type), ep.peerTag, bool(ep.turn), bool(ep.stun),
                        ep.username, ep.password, False)
                    try:
                        rep.reflectorId = int(ep.reflectorId)
                    except Exception:
                        pass
                    jclass("java.lang.reflect.Array").set(endpoints, i, rep)
                    replaced += 1
                except Exception as e:
                    self._flog("endpoint replace: " + str(e))
            # СНАЧАЛА поднимаем relay-порты (синхронно), ПОТОМ отдаём звонку endpoints
            if need_rebuild:
                self._rebuild_core_now()
            if replaced:
                self._flog("звонок 1:1: завёрнуто релеев %d" % replaced)
        except Exception as e:
            self._flog("hook_make_instance: " + str(e))

    def _hook_join_payload(self, frame):
        try:
            if frame.args and frame.args[0] is not None:
                s = str(frame.args[0])
                new, need_rebuild = self._route_call_json(s)
                nrelays = len(getattr(self, "_call_relays", {}) or {})
                self._flog("join_payload: релеев=%d rebuild=%s changed=%s"
                           % (nrelays, need_rebuild, new != s))
                if need_rebuild or (new != s and self._box is not None
                                    and getattr(self, "_cur_config", "") and "call-" not in self._cur_config):
                    # порты готовы ДО того как WebRTC начнёт слать; пересобираем, если ядро
                    # ещё без call-inbound'ов (даже когда релеи уже в кэше)
                    self._rebuild_core_now()
                if new != s:
                    frame.args[0] = new
                    self._flog("звонок групповой: candidates завёрнуты (%d)" % nrelays)
        except Exception as e:
            self._flog("hook_join_payload: " + str(e))

    def _route_call_json(self, payload):
        try:
            data = json.loads(payload)
            cands = data.get("transport", {}).get("candidates", [])
            if not isinstance(cands, list):
                return payload, False
            changed = False
            need_rebuild = False
            for c in cands:
                if not isinstance(c, dict):
                    continue
                ip, port = str(c.get("ip", "")), int(c.get("port", 0) or 0)
                if not ip or port <= 0:
                    continue
                lp, fresh = self._relay_local_port(ip, port)
                if lp > 0:
                    # КЛЮЧЕВОЕ: порт в candidate — СТРОКА (как у exitFy). int ломает парсер
                    # WebRTC у Telegram → candidate отбраковывается и трафик не идёт.
                    c["ip"], c["port"] = "127.0.0.1", str(lp)
                    changed = True
                    need_rebuild = need_rebuild or fresh
            return (json.dumps(data, ensure_ascii=False, separators=(",", ":")) if changed else payload), need_rebuild
        except Exception as e:
            self._flog("route_call_json: " + str(e))
            return payload, False

    # ---------------------------------------------------------------- экран настроек
    # Как у exitFy: в настройках плагина — только кнопка «Открыть», сам дашборд —
    # отдельный полноэкранный Fragment со ScrollView. Так мы НЕ кладём тяжёлый
    # кастомный view в RecyclerView настроек (его item-аниматор крашился setScaleX=null).
    def create_settings(self):
        from devgram.ui.settings import Card, Text as _T
        connected = self.get_setting("conn", "0") == "1"
        active = self._active_server()
        if connected and active:
            sub = "Подключено: " + str(active.get("name"))
        else:
            sub = "Серверы и подключение — внутри"
        return [
            Card(key="open", text="Открыть Туннель", subtitle=sub, icon="🌐", color=0xFF2E7D32 if connected else 0xFF3B82F6),
            _T(key="hint", text="Движок sing-box · VLESS · VMess · Trojan · Shadowsocks · Hysteria · Hysteria2 · TUIC"),
        ]

    def on_setting_click(self, key):
        if key == "open":
            self._open_dashboard()

    # ---------------------------------------------------------------- дашборд-фрагмент
    def _open_dashboard(self):
        try:
            frag = get_last_fragment()
            if frag is None:
                self.bulletin("Экран недоступен", kind="error")
                return
            frag.presentFragment(self._make_fragment())
        except Exception as e:
            self.log("open_dashboard: " + str(e))
            self.bulletin("Не удалось открыть: " + str(e), kind="error")

    def _make_fragment(self):
        return self._make_screen("Туннель", self._build_dashboard)

    def _make_screen(self, title, builder):
        """Полноэкранный BaseFragment со ScrollView: actionbar + собранный builder(ctx)."""
        plugin = self

        class FragLogic:
            def createView(self, this, context):
                sv = jclass("android.widget.ScrollView")(context)
                try:
                    sv.setFillViewport(True)
                    sv.setBackgroundColor(plugin._color("key_windowBackgroundGray"))
                except Exception:
                    pass
                try:
                    content = builder(context)
                    FLP = jclass("android.widget.FrameLayout$LayoutParams")
                    sv.addView(content, FLP(-1, -2))
                except Exception as e:
                    plugin._flog("build screen '%s': %s" % (title, e))
                try:
                    this.fragmentView = sv
                except Exception:
                    pass
                try:
                    ab = this.getActionBar()
                    ab.setBackButtonImage(jclass("org.telegram.messenger.R$drawable").ic_ab_back)
                    ab.setTitle(title)
                    ab.setActionBarMenuOnItemClick(plugin._ab_click(this))
                except Exception as e:
                    plugin.log("actionbar: " + str(e))
                return sv

        return self.java_class("org.telegram.ui.ActionBar.BaseFragment", FragLogic())

    def _ab_click(self, frag):
        base = "org.telegram.ui.ActionBar.ActionBar$ActionBarMenuOnItemClick"

        class ClickLogic:
            def onItemClick(self, this, item_id):
                try:
                    if int(item_id) == -1:
                        frag.finishFragment()
                except Exception:
                    pass

        return self.java_class(base, ClickLogic())

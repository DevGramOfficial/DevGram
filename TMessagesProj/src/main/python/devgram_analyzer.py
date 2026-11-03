"""Статический анализ плагинов DevGram: читает код через ast, ничего не выполняя.

analyze_source(source, name) / analyze_package(path) -> JSON-отчёт:
  findings   — конкретные правила с доказательствами (файл, строка, фрагмент кода);
  statements — понятные пользователю пункты («Подключается к интернету»), собранные из правил;
  max_severity, limits (что проверить не удалось), artifacts (файлы).
"""

import ast
import base64
import csv
import hashlib
import io
import json
import re
import sys
import time
import zipfile

VERSION = "1"
TIME_LIMIT = 8.0
MAX_EVIDENCE = 6
MAX_FILE = 2 * 1024 * 1024

SEVERITY_ORDER = {"info": 0, "caution": 1, "danger": 2, "critical": 3}

# пункт отчёта -> степень опасности
STATEMENT_SEVERITY = {
    "account_takeover": "critical",
    "session_via_telegram": "critical",
    "remote_code": "critical",
    "hidden_account_actions": "critical",
    "exfiltrate_messages": "danger",
    "exfiltrate_files": "danger",
    "device_id_upload": "danger",
    "telegram_leak": "danger",
    "dynamic_code": "danger",
    "embedded_code": "danger",
    "mvel_code": "danger",
    "hidden_parts": "danger",
    "loads_code": "danger",
    "native_code": "danger",
    "anti_analysis": "danger",
    "remote_control": "danger",
    "hides_behavior": "danger",
    "processes": "danger",
    "host_tampering": "danger",
    "sensitive_requests": "danger",
    "session_data": "danger",
    "sms": "danger",
    "screen_capture": "danger",
    "installs_apps": "danger",
    "wallet_access": "danger",
    "messages_delete": "danger",
    "system_overlay": "danger",
    "ui_hooks": "danger",
    "intercepts_requests": "danger",
    "traffic_interception": "danger",
    "reflection_dynamic": "caution",
    "network": "caution",
    "files": "caution",
    "telegram_files": "caution",
    "java_hooks": "caution",
    "persistence": "caution",
    "app_settings": "caution",
    "privacy": "caution",
    "media_library": "caution",
    "webview_bridge": "caution",
    "intents": "caution",
    "notifications": "caution",
    "third_party_code": "caution",
    "telegram_api": "caution",
    "messages_send": "caution",
    "messages_read": "caution",
    "typed_text": "caution",
    "forward_messages": "caution",
    "contacts": "caution",
    "app_exit": "caution",
    "background": "info",
    "verified_packages": "info",
    "unused_files": "info",
}

NETWORK_MODULES = {"urllib", "urllib2", "urllib3", "requests", "http", "httpx", "aiohttp", "socket", "websocket",
                   "websockets", "ftplib", "smtplib", "telnetlib", "ssl", "asyncio.open_connection"}
STDLIB_HINT = set(getattr(sys, "stdlib_module_names", ())) | {"java", "android", "devgram", "com", "org", "kotlin",
                                                              "base_plugin", "__future__"}

CALLS = {
    # код
    "eval": ("code.eval", ["dynamic_code"]),
    "exec": ("code.exec", ["dynamic_code"]),
    "compile": ("code.compile", ["dynamic_code"]),
    "__import__": ("code.import", ["dynamic_code"]),
    "importlib.import_module": ("code.import", ["dynamic_code"]),
    "marshal.loads": ("code.marshal", ["loads_code"]),
    "types.CodeType": ("code.codetype", ["loads_code"]),
    "importlib.util.spec_from_file_location": ("code.load_file", ["loads_code"]),
    "zipimport.zipimporter": ("code.load_file", ["loads_code"]),
    "ctypes.CDLL": ("native.ctypes", ["native_code"]),
    "ctypes.cdll.LoadLibrary": ("native.ctypes", ["native_code"]),
    "sys.settrace": ("anti.trace", ["anti_analysis"]),
    "sys.gettrace": ("anti.trace", ["anti_analysis"]),
    "sys.setprofile": ("anti.trace", ["anti_analysis"]),
    # процессы и выход
    "os.system": ("proc.system", ["processes"]),
    "os.popen": ("proc.system", ["processes"]),
    "subprocess.run": ("proc.subprocess", ["processes"]),
    "subprocess.Popen": ("proc.subprocess", ["processes"]),
    "subprocess.call": ("proc.subprocess", ["processes"]),
    "subprocess.check_output": ("proc.subprocess", ["processes"]),
    "subprocess.check_call": ("proc.subprocess", ["processes"]),
    "pty.spawn": ("proc.system", ["processes"]),
    "sys.exit": ("app.exit", ["app_exit"]),
    "os._exit": ("app.exit", ["app_exit"]),
    "os.kill": ("app.exit", ["app_exit"]),
    # файлы
    "open": ("files.open", ["files"]),
    "io.open": ("files.open", ["files"]),
    "os.remove": ("files.modify", ["files"]),
    "os.unlink": ("files.modify", ["files"]),
    "os.rename": ("files.modify", ["files"]),
    "os.replace": ("files.modify", ["files"]),
    "os.makedirs": ("files.modify", ["files"]),
    "os.listdir": ("files.list", ["files"]),
    "os.walk": ("files.list", ["files"]),
    "os.scandir": ("files.list", ["files"]),
    "shutil.copy": ("files.modify", ["files"]),
    "shutil.copyfile": ("files.modify", ["files"]),
    "shutil.move": ("files.modify", ["files"]),
    "shutil.rmtree": ("files.modify", ["files"]),
    "glob.glob": ("files.list", ["files"]),
    # фон
    "threading.Thread": ("bg.thread", ["background"]),
    "threading.Timer": ("bg.thread", ["background"]),
    "asyncio.run": ("bg.async", ["background"]),
    "asyncio.create_task": ("bg.async", ["background"]),
    "asyncio.new_event_loop": ("bg.async", ["background"]),
    "plugin.run_on_queue": ("bg.queue", ["background"]),
    # API плагина DevGram
    "plugin.send_message": ("msg.send", ["messages_send"]),
    "plugin.send_text": ("msg.send", ["messages_send"]),
    "plugin.send_photo": ("msg.send", ["messages_send"]),
    "plugin.send_file": ("msg.send", ["messages_send"]),
    "plugin.send_document": ("msg.send", ["messages_send"]),
    "plugin.send_media": ("msg.send", ["messages_send"]),
    "plugin.edit_message": ("msg.send", ["messages_send"]),
    "devgram.get_client.send_text": ("msg.send", ["messages_send"]),
    "plugin.delete_messages": ("msg.delete", ["messages_delete"]),
    "plugin.delete_message": ("msg.delete", ["messages_delete"]),
    "plugin.forward_messages": ("msg.forward", ["forward_messages"]),
    "plugin.send_request": ("tl.request", ["telegram_api"]),
    "plugin.hook": ("hooks.java", ["java_hooks"]),
    "plugin.hook_method": ("hooks.java", ["java_hooks"]),
    "plugin.hook_all_methods": ("hooks.java", ["java_hooks"]),
    "plugin.hook_constructor": ("hooks.java", ["java_hooks"]),
    "plugin.clipboard": ("privacy.clipboard", ["privacy"]),
    "plugin.copy": ("app.clipboard_write", []),
}

# Java-классы (по вхождению в имя) -> правило
JAVA_CLASSES = [
    ("okhttp3.Interceptor", "net.interceptor", ["traffic_interception"]),
    ("okhttp3", "net.java", ["network"]),
    ("java.net.URL", "net.java", ["network"]),
    ("java.net.Socket", "net.java", ["network"]),
    ("java.net.ServerSocket", "net.server", ["network", "remote_control"]),
    ("HttpURLConnection", "net.java", ["network"]),
    ("dalvik.system.DexClassLoader", "code.dex", ["loads_code"]),
    ("dalvik.system.PathClassLoader", "code.dex", ["loads_code"]),
    ("dalvik.system.InMemoryDexClassLoader", "code.dex", ["loads_code"]),
    ("java.lang.System", "native.system", []),
    ("java.lang.Runtime", "proc.runtime", ["processes"]),
    ("java.lang.ProcessBuilder", "proc.runtime", ["processes"]),
    ("java.lang.reflect", "reflect.java", ["reflection_dynamic"]),
    ("org.mvel", "code.mvel", ["mvel_code"]),
    ("mvel2", "code.mvel", ["mvel_code"]),
    ("android.telephony.SmsManager", "device.sms", ["sms"]),
    ("android.provider.Telephony", "device.sms", ["sms"]),
    ("android.media.projection", "device.screen", ["screen_capture"]),
    ("android.view.PixelCopy", "device.screen", ["screen_capture"]),
    ("android.content.pm.PackageInstaller", "device.install", ["installs_apps"]),
    ("android.provider.MediaStore", "device.media", ["media_library"]),
    ("android.webkit.WebView", "web.view", ["webview_bridge"]),
    ("android.widget.EditText", "ui.typed_text", ["typed_text"]),
    ("android.text.TextWatcher", "ui.typed_text", ["typed_text"]),
    ("android.location", "device.location", ["privacy"]),
    ("android.media.AudioRecord", "device.mic", ["privacy"]),
    ("android.media.MediaRecorder", "device.mic", ["privacy"]),
    ("android.hardware.camera2", "device.camera", ["privacy"]),
    ("android.content.ClipboardManager", "privacy.clipboard", ["privacy"]),
    ("android.accounts.AccountManager", "device.accounts", ["privacy"]),
    ("android.telephony.TelephonyManager", "device.ids", ["privacy"]),
    ("android.provider.Settings", "device.settings", []),
    ("android.provider.ContactsContract", "device.contacts", ["contacts", "privacy"]),
    ("android.app.NotificationManager", "app.notifications", ["notifications"]),
    ("android.service.notification", "app.notifications", ["notifications"]),
    ("android.content.Intent", "app.intent", ["intents"]),
    ("android.app.AlarmManager", "bg.alarm", ["background"]),
    ("android.app.job.JobScheduler", "bg.alarm", ["background"]),
    ("android.content.BroadcastReceiver", "bg.receiver", ["background"]),
    ("org.telegram.tgnet.ConnectionsManager", "tl.connections", ["telegram_api"]),
    ("org.telegram.messenger.SendMessagesHelper", "msg.send_java", ["messages_send"]),
    ("org.telegram.messenger.MessagesStorage", "msg.read_java", ["messages_read"]),
    ("org.telegram.messenger.ContactsController", "contacts.java", ["contacts"]),
    ("org.telegram.messenger.FileLoader", "tgfiles.loader", ["telegram_files"]),
    ("org.telegram.messenger.UserConfig", "session.userconfig", []),
    ("org.telegram.messenger.DevGramPlugins", "host.plugins", ["host_tampering"]),
    ("org.telegram.messenger.DevGramConfig", "host.config", ["app_settings"]),
    ("org.telegram.messenger.SharedConfig", "host.config", ["app_settings"]),
    ("org.telegram.messenger.BuildVars", "host.buildvars", ["host_tampering"]),
    ("org.telegram.ui.Wallet", "wallet.ui", ["wallet_access"]),
    ("Wallet", "wallet.java", ["wallet_access"]),
    ("TonConnect", "wallet.java", ["wallet_access"]),
]

# метод Java (по имени вызова) -> правило
JAVA_METHODS = {
    "loadLibrary": ("native.load", ["native_code"]),
    "load": None,
    "exec": None,
    "exit": ("app.exit", ["app_exit"]),
    "killProcess": ("app.exit", ["app_exit"]),
    "finishAffinity": ("app.exit", ["app_exit"]),
    "forName": ("reflect.java", ["reflection_dynamic"]),
    "getDeclaredMethod": ("reflect.java", ["reflection_dynamic"]),
    "getDeclaredField": ("reflect.java", ["reflection_dynamic"]),
    "setAccessible": ("reflect.java", ["reflection_dynamic"]),
    "addJavascriptInterface": ("web.bridge", ["webview_bridge"]),
    "evaluateJavascript": ("web.bridge", ["webview_bridge"]),
    "addInterceptor": ("net.interceptor", ["traffic_interception"]),
    "addNetworkInterceptor": ("net.interceptor", ["traffic_interception"]),
    "setProxy": ("net.interceptor", ["traffic_interception"]),
    "onTextChanged": ("ui.typed_text", ["typed_text"]),
    "afterTextChanged": ("ui.typed_text", ["typed_text"]),
    "startActivity": ("app.intent", ["intents"]),
    "sendBroadcast": ("app.intent", ["intents"]),
    "startService": ("app.intent", ["intents"]),
    "registerReceiver": ("bg.receiver", ["background"]),
    "sendRequest": ("tl.request", ["telegram_api"]),
    "sendMessage": ("msg.send_java", ["messages_send"]),
    "deleteMessages": ("msg.delete", ["messages_delete"]),
    "deleteDialog": ("msg.delete", ["messages_delete"]),
    "forwardMessages": ("msg.forward", ["forward_messages"]),
    "getClientPhone": ("session.phone", ["session_data"]),
    "getAuthKey": ("session.key", ["session_data"]),
    "getPasscodeHash": ("session.passcode", ["session_data"]),
    "getCurrentUser": ("session.user", []),
    "getPrimaryClip": ("privacy.clipboard", ["privacy"]),
    "getLastKnownLocation": ("device.location", ["privacy"]),
    "requestLocationUpdates": ("device.location", ["privacy"]),
    "getDeviceId": ("device.ids", ["privacy"]),
    "getImei": ("device.ids", ["privacy"]),
    "getActiveNotifications": ("app.notifications", ["notifications"]),
    "clearFlags": None,
    "addView": None,
    "getGlobalMainSettings": ("host.config", ["app_settings"]),
    "loadFile": ("tgfiles.loader", ["telegram_files"]),
    "getPathToAttach": ("tgfiles.loader", ["telegram_files"]),
    "getPathToMessage": ("tgfiles.loader", ["telegram_files"]),
    "installPackage": ("plugins.install", ["persistence"]),
    "install": None,
}

STRING_PATTERNS = [
    (re.compile(r"^(https?|wss?)://", re.I), "net.endpoints", ["network"]),
    (re.compile(r"^\d{1,3}(\.\d{1,3}){3}(:\d+)?$"), "net.raw_ip", ["network"]),
    (re.compile(r"tgnet\.dat|cache4\.db|auth_key|authKey"), "session.files", ["session_data"]),
    (re.compile(r"android_id|ANDROID_ID"), "device.android_id", ["privacy"]),
    (re.compile(r"content://sms"), "device.sms", ["sms"]),
    (re.compile(r"content://media"), "device.media", ["media_library"]),
    (re.compile(r"application/vnd\.android\.package-archive|\.apk$"), "device.install", ["installs_apps"]),
    (re.compile(r"TYPE_APPLICATION_OVERLAY|SYSTEM_ALERT_WINDOW"), "device.overlay", ["system_overlay"]),
    (re.compile(r"FLAG_SECURE"), "device.screen", ["screen_capture"]),
    (re.compile(r"passcodeHash|passcode"), "session.passcode", ["session_data"]),
]

UI_HOOK_HINTS = ("LoginActivity", "Passcode", "TwoStep", "PaymentForm", "Payment", "ChangePhone", "PrivacySettings",
                 "Stars", "Gift", "Wallet", "SessionsActivity", "Passport")

UI_HANDLERS = {"on_menu_click", "on_setting_click", "on_click", "on_setting_changed", "on_pill_click",
               "on_command", "on_send_message"}

TITLES = {
    "code.eval": "eval()", "code.exec": "exec()", "code.compile": "compile()", "code.import": "динамический импорт",
    "net.endpoints": "адреса в коде", "net.raw_ip": "IP-адреса", "tl.request": "запросы к Telegram",
}


class Finding(object):
    def __init__(self, rule, capabilities):
        self.rule = rule
        self.capabilities = list(capabilities)
        self.values = []
        self.evidence = []
        self.total = 0

    def add(self, artifact, node, lines, scope, symbol, value=None):
        if value is not None and value not in self.values and len(self.values) < 40:
            self.values.append(value)
        self.total += 1
        if len(self.evidence) >= MAX_EVIDENCE:
            return
        line = getattr(node, "lineno", 0) or 0
        end = getattr(node, "end_lineno", line) or line
        excerpt = lines[line - 1].strip() if 0 < line <= len(lines) else ""
        if len(excerpt) > 160:
            excerpt = excerpt[:157] + "…"
        for e in self.evidence:
            if e["location"]["artifact"] == artifact and e["location"]["line"] == line:
                return
        self.evidence.append({
            "location": {"artifact": artifact, "line": line, "end_line": end, "scope": scope or ""},
            "excerpt": excerpt,
            "symbol": symbol or "",
        })


class Analysis(object):
    def __init__(self):
        self.findings = {}
        self.artifacts = []
        self.limits = []
        self.started = time.time()
        self.sensitive = _sensitive_names()
        self.flags = {}

    def finding(self, rule, caps):
        f = self.findings.get(rule)
        if f is None:
            f = Finding(rule, caps)
            self.findings[rule] = f
        else:
            for c in caps:
                if c not in f.capabilities:
                    f.capabilities.append(c)
        return f

    def timed_out(self):
        return time.time() - self.started > TIME_LIMIT


def _sensitive_names():
    try:
        from java import jclass
        return set(str(x) for x in jclass("org.telegram.messenger.DevGramSensitiveRequests").names())
    except Exception:
        return set()


def _dotted(node):
    parts = []
    while isinstance(node, ast.Attribute):
        parts.append(node.attr)
        node = node.value
    if isinstance(node, ast.Name):
        parts.append(node.id)
    elif isinstance(node, ast.Call):
        inner = _dotted(node.func)
        if inner:
            parts.append(inner)
    else:
        return None
    return ".".join(reversed(parts))


def _const_str(node):
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    return None


class Visitor(ast.NodeVisitor):
    def __init__(self, analysis, artifact, source):
        self.a = analysis
        self.artifact = artifact
        self.lines = source.splitlines()
        self.aliases = {}
        self.java_vars = {}
        self.scope = []
        self.class_methods = set()

    # ---- служебное ----
    def hit(self, node, rule, caps, symbol="", value=None):
        self.a.finding(rule, caps).add(self.artifact, node, self.lines, ".".join(self.scope), symbol, value)
        for c in caps:
            self.a.flags.setdefault(c, set()).add(".".join(self.scope[:1]) or "<module>")

    def resolve(self, name):
        if not name:
            return name
        head, _, rest = name.partition(".")
        if head == "self":
            return "plugin" + ("." + rest if rest else "")
        if head in self.aliases:
            full = self.aliases[head]
            return full + ("." + rest if rest else "")
        if head in self.java_vars:
            return "java:" + self.java_vars[head] + ("." + rest if rest else "")
        return name

    # ---- импорты ----
    def visit_Import(self, node):
        for a in node.names:
            self.aliases[(a.asname or a.name).split(".")[0]] = a.name if a.asname else a.name.split(".")[0]
            self._module(node, a.name)
        self.generic_visit(node)

    def visit_ImportFrom(self, node):
        mod = node.module or ""
        for a in node.names:
            self.aliases[a.asname or a.name] = (mod + "." + a.name) if mod else a.name
        if mod and not node.level:
            self._module(node, mod)
        self.generic_visit(node)

    def _module(self, node, mod):
        root = mod.split(".")[0]
        if root in ("urllib", "urllib2", "urllib3", "requests", "http", "httpx", "aiohttp", "socket",
                    "websocket", "websockets", "ftplib", "smtplib", "telnetlib"):
            self.hit(node, "net.module", ["network"], mod, root)
        elif root in ("ctypes", "cffi"):
            self.hit(node, "native.ctypes", ["native_code"], mod, root)
        elif root in ("subprocess", "pty"):
            self.hit(node, "proc.subprocess", ["processes"], mod, root)
        elif root == "marshal":
            self.hit(node, "code.marshal", ["loads_code"], mod, root)
        elif root in ("devgram_plugins",):
            self.hit(node, "host.plugins", ["host_tampering"], mod, root)
        elif root not in STDLIB_HINT and not root.startswith("_"):
            self.hit(node, "packages.import", ["third_party_code"], mod, root)

    # ---- области видимости ----
    def visit_FunctionDef(self, node):
        self.scope.append(node.name)
        if len(self.scope) >= 1:
            name = node.name
            if name in ("on_send_request", "on_request", "on_receive_response", "on_response", "on_send_request_hook",
                        "pre_request_hook", "post_request_hook"):
                self.hit(node, "tl.intercept", ["intercepts_requests"], name)
            elif name in ("on_receive_message", "on_update", "on_new_message", "on_message"):
                self.hit(node, "msg.read_hook", ["messages_read"], name)
            elif name in ("on_send_message", "on_send_message_hook"):
                self.hit(node, "msg.outgoing_hook", ["messages_read"], name)
        self.generic_visit(node)
        self.scope.pop()

    visit_AsyncFunctionDef = visit_FunctionDef

    def visit_ClassDef(self, node):
        self.scope.append(node.name)
        self.generic_visit(node)
        self.scope.pop()

    # ---- присваивания: var = jclass("...") ----
    def visit_Assign(self, node):
        if isinstance(node.value, ast.Call):
            fn = self.resolve(_dotted(node.value.func) or "")
            if fn.split(".")[-1] in ("jclass", "java_class", "find_class") and node.value.args:
                s = _const_str(node.value.args[0])
                if s:
                    for t in node.targets:
                        if isinstance(t, ast.Name):
                            self.java_vars[t.id] = s
        self.generic_visit(node)

    # ---- строки ----
    def visit_Constant(self, node):
        if isinstance(node.value, str):
            s = node.value
            if len(s) > 600 and re.fullmatch(r"[A-Za-z0-9+/=\s]+", s):
                self.a.finding("data.opaque", ["hidden_parts"]).add(self.artifact, node, self.lines,
                                                                 ".".join(self.scope), "data:opaque", None)
            elif len(s) < 400:
                for rx, rule, caps in STRING_PATTERNS:
                    if rx.search(s):
                        self.hit(node, rule, caps, "str", s if rule.startswith("net.") else None)
                        break
                if s.startswith("TL_") and len(s) < 80:
                    self._tl(node, s)
                for prefix, rule, caps in JAVA_CLASSES:
                    if s.startswith(("org.", "android.", "java.", "dalvik.", "okhttp3")) and prefix in s:
                        self.hit(node, rule, caps, s)
                        break
        self.generic_visit(node)

    def _tl(self, node, name):
        short = name.split(".")[-1].split("$")[-1]
        self.hit(node, "tl.request", ["telegram_api"], name, short)
        if short in self.a.sensitive or short.replace("TL_", "", 1) in self.a.sensitive:
            self.hit(node, "tl.sensitive", ["sensitive_requests"], name, short)
            if not (set(self.scope) & UI_HANDLERS):
                self.hit(node, "tl.hidden", ["hidden_account_actions"], name, short)
        if "auth_" in short or short.startswith("auth") or "exportLoginToken" in short:
            if "exportAuthorization" in short or "acceptLoginToken" in short or "exportLoginToken" in short:
                self.hit(node, "tl.session", ["session_via_telegram"], name, short)

    # ---- вызовы ----
    def visit_Call(self, node):
        raw = _dotted(node.func) or ""
        fn = self.resolve(raw)
        last = fn.split(".")[-1] if fn else ""
        args = node.args

        rule = CALLS.get(fn)
        if rule is None and fn.startswith("plugin."):
            rule = CALLS.get("plugin." + last)
        if rule:
            r, caps = rule
            if caps:
                self.hit(node, r, caps, fn)
            if r == "files.open" and args:
                p = _const_str(args[0])
                if p and re.search(r"tgnet|cache4|/data/data/org\.(telegram|devgram)", p):
                    self.hit(node, "session.files", ["session_data"], fn, p)
            if r == "hooks.java":
                self._hook_target(node)

        if last in ("jclass", "java_class", "find_class") and args:
            s = _const_str(args[0])
            if s is None:
                self.hit(node, "reflect.dynamic_class", ["reflection_dynamic"], fn)
            else:
                for prefix, r2, caps in JAVA_CLASSES:
                    if prefix in s:
                        if caps:
                            self.hit(node, r2, caps, s, s if r2.startswith(("host.", "wallet")) else None)
                        break
                if ".TLRPC$" in s or "$TL_" in s or s.split("$")[-1].startswith("TL_"):
                    self._tl(node, s)

        if last in ("tl", "TL") and args:
            s = _const_str(args[0])
            if s:
                self._tl(node, s)

        if last in ("getattr", "setattr", "delattr") and fn in ("getattr", "setattr", "delattr") and len(args) >= 2:
            if _const_str(args[1]) is None:
                self.hit(node, "reflect.getattr", ["reflection_dynamic"], fn)

        if fn.startswith("java:") or last in JAVA_METHODS:
            jm = JAVA_METHODS.get(last)
            if jm:
                r, caps = jm
                if caps:
                    self.hit(node, r, caps, fn)
            cls = fn[5:].rsplit(".", 1)[0] if fn.startswith("java:") else ""
            if cls:
                for prefix, r2, caps in JAVA_CLASSES:
                    if prefix in cls and caps:
                        self.hit(node, r2, caps, cls + "#" + last)
                        break
            if last in ("exec",) and ("Runtime" in fn or "ProcessBuilder" in fn):
                self.hit(node, "proc.runtime", ["processes"], fn)
            if last in ("load",) and "System" in fn:
                self.hit(node, "native.load", ["native_code"], fn)
            if last == "clearFlags" and args and "FLAG_SECURE" in ast.dump(args[0]):
                self.hit(node, "device.screen", ["screen_capture"], fn)

        if self._is_upload(node, fn, last):
            self.hit(node, "net.upload", ["network"], fn)
            self.a.flags.setdefault("_upload", set()).add(".".join(self.scope[:1]) or "<module>")

        if last in ("b64decode", "decodebytes", "decompress", "a85decode", "b32decode", "unhexlify") and \
                any(isinstance(p, ast.Call) for p in ast.walk(node)):
            self.a.flags.setdefault("_decoder", set()).add(".".join(self.scope[:1]) or "<module>")

        self.generic_visit(node)

    def _is_upload(self, node, fn, last):
        """Отправка данных наружу: POST/PUT, тело запроса, multipart, отправка в сокет."""
        kw = {k.arg for k in node.keywords if k.arg}
        net_call = fn.split(".")[0] in ("requests", "httpx", "aiohttp", "urllib", "http", "session") or \
            "urlopen" in fn or "Request" in last or fn.startswith("java:okhttp3")
        if last in ("post", "put", "patch") and (net_call or "session" in fn.lower() or "client" in fn.lower()):
            return True
        if net_call and kw & {"data", "files", "json"}:
            return True
        if last in ("send", "sendall", "sendto") and ("socket" in fn or "sock" in fn.lower() or "ws" in fn.lower()):
            return True
        if last in ("create", "addFormDataPart") and ("RequestBody" in fn or "MultipartBody" in fn):
            return True
        if last == "setDoOutput":
            return True
        for a in node.args:
            v = _const_str(a)
            if v in ("POST", "PUT", "PATCH"):
                return True
        return False

    def _hook_target(self, node):
        cls = None
        if node.args:
            first = node.args[0]
            cls = _const_str(first)
            if cls is None and isinstance(first, ast.Name):
                cls = self.java_vars.get(first.id)
            if cls is None and isinstance(first, ast.Call):
                inner = first.args[0] if first.args else None
                cls = _const_str(inner) if inner is not None else None
        method = _const_str(node.args[1]) if len(node.args) > 1 else None
        if cls is None:
            self.hit(node, "hooks.unresolved", ["java_hooks"], "?")
            return
        target = cls + ("#" + method if method else "")
        self.a.finding("hooks.java", ["java_hooks"]).add(self.artifact, node, self.lines, ".".join(self.scope),
                                                       target, target)
        if any(h in cls for h in UI_HOOK_HINTS):
            self.hit(node, "hooks.ui", ["ui_hooks"], target, target)
        if "ConnectionsManager" in cls and method and method.startswith("sendRequest"):
            self.hit(node, "tl.intercept", ["intercepts_requests"], target)
        if "DevGramPlugins" in cls or "DevGramSensitiveRequests" in cls:
            self.hit(node, "host.plugins", ["host_tampering"], target, target)


def _analyze_python(analysis, artifact, name, source):
    analysis.artifacts.append({"id": artifact, "name": name, "kind": "python"})
    try:
        tree = ast.parse(source, filename=name)
    except SyntaxError:
        analysis.limits.append("unanalyzed:" + name)
        return
    v = Visitor(analysis, artifact, source)
    v.visit(tree)
    if analysis.timed_out():
        analysis.limits.append("timeout")


def _derive(analysis):
    """Составные пункты: сочетания возможностей, которые вместе опаснее."""
    caps = set()
    for f in analysis.findings.values():
        caps.update(f.capabilities)

    def combo(rule, cap, need_all, need_any=()):
        if all(c in caps for c in need_all) and (not need_any or any(c in caps for c in need_any)):
            f = analysis.finding(rule, [cap])
            src = [analysis.findings[r] for r in analysis.findings if
                   any(c in analysis.findings[r].capabilities for c in list(need_all) + list(need_any))]
            for s in src:
                for e in s.evidence[:2]:
                    if len(f.evidence) < MAX_EVIDENCE and e not in f.evidence:
                        f.evidence.append(e)
                f.total += 1
            caps.add(cap)

    decoder = bool(analysis.flags.get("_decoder"))
    if decoder and "dynamic_code" in caps:
        combo("code.packed", "embedded_code", ["dynamic_code"])
    combo("code.remote", "remote_code", ["network", "dynamic_code"])
    upload = bool(analysis.flags.get("_upload"))
    if upload:
        combo("leak.messages", "exfiltrate_messages", ["network"], ["messages_read", "contacts"])
        combo("leak.files", "exfiltrate_files", ["network", "files"])
    if upload and ("device.ids" in analysis.findings or "device.android_id" in analysis.findings):
        combo("leak.device_id", "device_id_upload", ["network", "privacy"])
    combo("leak.telegram", "telegram_leak", ["messages_send"], ["session_data"])
    if upload or "session_via_telegram" in caps:
        combo("account.takeover", "account_takeover", ["session_data"], ["network", "session_via_telegram"])
    send_scopes = analysis.flags.get("messages_send", set())
    del_scopes = analysis.flags.get("messages_delete", set())
    if send_scopes & del_scopes - {"<module>"}:
        combo("hides.send_delete", "hides_behavior", ["messages_send", "messages_delete"])


def _report(analysis):
    statements = {}
    for f in analysis.findings.values():
        for c in f.capabilities:
            st = statements.get(c)
            if st is None:
                st = {"id": c, "severity": STATEMENT_SEVERITY.get(c, "info"), "title": c, "values": [], "rules": [],
                      "capabilities": [c]}
                statements[c] = st
            if f.rule not in st["rules"]:
                st["rules"].append(f.rule)
            for v in f.values:
                if v not in st["values"] and len(st["values"]) < 40:
                    st["values"].append(v)
    findings = []
    for f in analysis.findings.values():
        if not f.capabilities:
            continue
        sev = max((STATEMENT_SEVERITY.get(c, "info") for c in f.capabilities), key=lambda s: SEVERITY_ORDER[s])
        findings.append({"rule": f.rule, "severity": sev, "title": TITLES.get(f.rule, f.rule), "values": f.values,
                         "capabilities": f.capabilities, "evidence": f.evidence, "evidence_total": f.total})
    stmt_list = sorted(statements.values(), key=lambda s: -SEVERITY_ORDER[s["severity"]])
    max_sev = stmt_list[0]["severity"] if stmt_list else ""
    return json.dumps({
        "analyzer": {"version": VERSION},
        "max_severity": max_sev,
        "statements": stmt_list,
        "findings": findings,
        "limits": analysis.limits,
        "artifacts": analysis.artifacts,
        "stats": {"elapsed_ms": int((time.time() - analysis.started) * 1000)},
    }, ensure_ascii=False)


def analyze_source(source, name="plugin.py"):
    a = Analysis()
    _analyze_python(a, "0", name or "plugin.py", source or "")
    _derive(a)
    return _report(a)


def _verified_wheel(data):
    """PyPI wheel считается проверенным, только когда RECORD покрывает файлы и хеши совпадают."""
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as wheel:
            records = [n for n in wheel.namelist() if n.endswith(".dist-info/RECORD")]
            if len(records) != 1:
                return False
            rows = csv.reader(io.StringIO(wheel.read(records[0]).decode("utf-8", "replace")))
            checked = 0
            for name, digest, size in rows:
                if not digest:
                    continue
                algorithm, encoded = digest.split("=", 1)
                if algorithm not in hashlib.algorithms_available:
                    return False
                payload = wheel.read(name)
                actual = base64.urlsafe_b64encode(hashlib.new(algorithm, payload).digest()).rstrip(b"=").decode("ascii")
                if actual != encoded or (size and int(size) != len(payload)):
                    return False
                checked += 1
            return checked > 0
    except Exception:
        return False


def analyze_package(path):
    a = Analysis()
    try:
        with zipfile.ZipFile(path) as zf:
            index = 0
            wheels = []
            verified_wheels = []
            unused = []
            for info in zf.infolist():
                n = info.filename
                low = n.lower()
                if info.is_dir():
                    continue
                if low.startswith("wheels/") or low.endswith(".whl"):
                    wheel_name = n.split("/")[-1]
                    if info.file_size <= MAX_FILE * 8 and _verified_wheel(zf.read(info)):
                        verified_wheels.append(wheel_name)
                    else:
                        wheels.append(wheel_name)
                    continue
                if low.endswith((".mvel", ".mvel2")):
                    f = a.finding("code.mvel_file", ["mvel_code"])
                    f.values.append(n)
                    f.total += 1
                    continue
                if low.endswith(".py"):
                    if info.file_size > MAX_FILE:
                        a.limits.append("unanalyzed:" + n)
                        continue
                    src = zf.read(info).decode("utf-8", "replace")
                    _analyze_python(a, str(index), n, src)
                    index += 1
                elif low.endswith((".so", ".dylib")):
                    a.artifacts.append({"id": str(index), "name": n, "kind": "native"})
                    a.finding("native.library", ["native_code"]).values.append(n)
                    a.findings["native.library"].total += 1
                    index += 1
                elif low.endswith((".pyc", ".dex", ".jar")):
                    a.artifacts.append({"id": str(index), "name": n, "kind": "compiled"})
                    a.finding("code.compiled", ["loads_code"]).values.append(n)
                    a.findings["code.compiled"].total += 1
                    index += 1
                elif low.endswith((".js", ".class", ".bin")):
                    unused.append(n)
                if a.timed_out():
                    a.limits.append("timeout")
                    break
            if wheels:
                f = a.finding("packages.wheels", ["third_party_code"])
                for w in wheels:
                    f.values.append(w.split("-")[0])
                    f.total += 1
            if verified_wheels:
                f = a.finding("packages.verified_wheels", ["verified_packages"])
                for w in verified_wheels:
                    f.values.append(w.split("-")[0])
                    f.total += 1
            if unused:
                f = a.finding("package.unused_files", ["unused_files"])
                for n in unused[:40]:
                    f.values.append(n)
                    f.total += 1
    except Exception as e:
        a.limits.append("unanalyzed:" + str(e)[:80])
    _derive(a)
    return _report(a)

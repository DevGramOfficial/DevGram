"""
DevGram: загрузчик и реестр плагинов. Вызывается из Java (DevGramPlugins).
Импортирует .py-файлы, находит наследников devgram.BasePlugin, хранит их,
и раздаёт события (хуки) по включённым плагинам.
"""

import ast
import gc
import importlib.machinery
import importlib.util
import os
import sys
import traceback
import json
import zipfile
import shutil
import types

from java import jclass

_FileLog = jclass("org.telegram.messenger.FileLog")
_PluginWatchdog = jclass("org.telegram.messenger.DevGramPluginWatchdog")
_SEP = "\x1f"  # разделитель полей для передачи в Java

_plugins = []  # экземпляры плагинов
_known_plugins = {}  # id -> лёгкие метаданные; выключенный экземпляр в памяти не держим
_package_roots = {}
_package_manifests = {}
_package_archives = {}  # plugin_id -> путь к установленному .dgplugin (для единой карточки: _filename)

# Уровень API плагинов DevGram. Бампать при добавлении нового plugin-facing API
# (сейчас: полный хук-API AliuHook, java_class/implement, эффекты/canvas).
DEVGRAM_PLUGIN_API = 3


def _app_version():
    """Версия приложения ('12.10.1.0') через PackageManager — надёжно."""
    try:
        ctx = jclass("org.telegram.messenger.ApplicationLoader").applicationContext
        info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0)
        return str(info.versionName or "")
    except Exception:
        try:
            return str(jclass("org.telegram.messenger.BuildConfig").BUILD_VERSION_STRING or "")
        except Exception:
            return ""


def _ver_tuple(s):
    """'12.10.1' -> (12, 10, 1). Нечисловые хвосты компонент игнорируются."""
    out = []
    for part in str(s or "").strip().split("."):
        num = ""
        for ch in part:
            if ch.isdigit():
                num += ch
            else:
                break
        if num == "" and not out:
            continue  # пропустить ведущий мусор
        out.append(int(num) if num else 0)
    return tuple(out)


def _ver_ge(app, need):
    """app >= need покомпонентно (с добивкой нулями до одинаковой длины)."""
    a, b = _ver_tuple(app), _ver_tuple(need)
    n = max(len(a), len(b))
    a += (0,) * (n - len(a))
    b += (0,) * (n - len(b))
    return a >= b


def _compat_error(min_app, min_devgram):
    """'' если плагин совместим с этой сборкой, иначе — сообщение для пользователя."""
    min_app = str(min_app or "").strip()
    if min_app:
        cur = _app_version()
        if cur and not _ver_ge(cur, min_app):
            return "Плагину нужна версия DevGram %s или новее (у вас %s)" % (min_app, cur)
    try:
        need = int(str(min_devgram or "0").strip() or "0")
    except Exception:
        need = 0
    if need > DEVGRAM_PLUGIN_API:
        return ("Плагину нужен уровень API плагинов %d, в этой сборке — %d. Обновите DevGram."
                % (need, DEVGRAM_PLUGIN_API))
    return ""


def _normalized_requirement_name(value):
    """Return distribution name from a simple requirement without accepting pip options/URLs."""
    value = str(value or '').strip()
    if not value or len(value) > 128 or value.startswith(('-', '.', '/')) or '://' in value:
        return ''
    name = []
    for char in value:
        if char.isalnum() or char in '._-':
            name.append(char)
        else:
            break
    return ''.join(name).replace('_', '-').lower()


def _validate_package_manifest(manifest, names):
    if not isinstance(manifest, dict):
        raise ValueError('manifest must be an object')
    plugin_id = str(manifest.get('id', '')).strip()
    main = str(manifest.get('main', 'main.py')).strip()
    if not main.lower().endswith('.py'):
        raise ValueError('Точка входа плагина должна быть открытым файлом .py')
    if any(str(name).lower().endswith(('.pyc', '.pyo')) for name in names):
        raise ValueError('Байткод .pyc/.pyo запрещён; добавьте открытые исходники .py')
    if (not plugin_id or len(plugin_id) > 64 or
            not all(c.isalnum() or c in '._-' for c in plugin_id) or
            main not in names or main.startswith('/') or '..' in main.split('/')):
        raise ValueError('invalid package manifest')
    requirements = manifest.get('requirements', [])
    if requirements is None:
        requirements = []
    if not isinstance(requirements, list) or len(requirements) > 32:
        raise ValueError('requirements must be a list of at most 32 package names')
    wheels = [os.path.basename(name).lower().replace('_', '-') for name in names
              if name.startswith('wheels/') and name.endswith('.whl')]
    for requirement in requirements:
        package = _normalized_requirement_name(requirement)
        if not package:
            raise ValueError('unsafe requirement: ' + str(requirement))
        if not any(filename.startswith(package + '-') for filename in wheels):
            raise ValueError('missing bundled wheel for requirement: ' + package)
    return plugin_id, main


import time as _time

_LOG_BUF = []      # кольцевой буфер полных логов плагинов (экран «Логи плагинов»)
_LOG_MAX = 1000    # сколько строк держим
_LOG_MAX_ENTRY = 16384
_LOG_MAX_CHARS = 2 * 1024 * 1024
_log_chars = 0


def _log(msg, level="INFO", plugin=None):
    """Записать строку в общий лог плагинов.
    level: INFO/LIFE/EVENT/REQUEST/HOOK/API/ERROR/NET (для фильтра и наглядности).
    plugin: id плагина (тег), если известен."""
    global _log_chars
    line = str(msg)
    if len(line) > _LOG_MAX_ENTRY:
        line = line[:_LOG_MAX_ENTRY] + "\n… [запись обрезана]"
    try:
        ts = _time.strftime("%H:%M:%S")
    except Exception:
        ts = ""
    tag = ("[%s] " % plugin) if plugin else ""
    entry = "[%s] %-7s %s%s" % (ts, str(level), tag, line)
    try:
        _LOG_BUF.append(entry)
        _log_chars += len(entry)
        while _LOG_BUF and (len(_LOG_BUF) > _LOG_MAX or _log_chars > _LOG_MAX_CHARS):
            _log_chars -= len(_LOG_BUF.pop(0))
    except Exception:
        pass
    try:
        _FileLog.d("[DevGramPlugins] " + entry)
    except Exception:
        pass


def _err(where, plugin=None):
    """Записать текущее исключение (с трейсбеком) в лог как ERROR."""
    _log("%s\n%s" % (where, traceback.format_exc().rstrip()), level="ERROR", plugin=plugin)


def get_logs(level=None, plugin=None):
    """Весь буфер логов одной строкой. Опц. фильтр по level и/или plugin."""
    try:
        lines = _LOG_BUF
        if level:
            key = " %-7s " % str(level)
            lines = [x for x in lines if key in x]
        if plugin:
            key = "[%s] " % plugin
            lines = [x for x in lines if key in x]
        return "\n".join(lines)
    except Exception:
        return ""


def log_stats():
    """Сводка по уровням для шапки экрана логов: 'ERROR:3 REQUEST:120 …'."""
    try:
        from collections import Counter
        c = Counter()
        for x in _LOG_BUF:
            parts = x.split("] ", 1)
            lvl = parts[1].split(" ", 1)[0].strip() if len(parts) > 1 else "?"
            c[lvl] += 1
        return "  ".join("%s:%d" % (k, c[k]) for k in
                         ("ERROR", "REQUEST", "HOOK", "EVENT", "API", "NET", "LIFE", "INFO") if c.get(k))
    except Exception:
        return ""


def clear_logs():
    global _log_chars
    try:
        del _LOG_BUF[:]
        _log_chars = 0
    except Exception:
        pass


def dg_log(msg, level="INFO", plugin=None):
    """Записать строку в общий лог плагинов (вызывается из Java и из BasePlugin)."""
    _log(msg, level, plugin)


def _remove_package_paths(plugin_id, evict_modules=False):
    root = _package_roots.get(str(plugin_id), '')
    wheels = os.path.join(root, 'wheels') if root else ''
    if wheels:
        for path in list(sys.path):
            if os.path.abspath(path).startswith(os.path.abspath(wheels) + os.sep):
                while path in sys.path:
                    sys.path.remove(path)
        if evict_modules:
            for name, module in list(sys.modules.items()):
                module_file = str(getattr(module, '__file__', '') or '')
                if module_file and os.path.abspath(module_file).startswith(os.path.abspath(wheels) + os.sep):
                    sys.modules.pop(name, None)


def hello():
    return "DevGram Python OK, version " + sys.version.split()[0]


def python_version():
    return sys.version.split()[0]


def unload_plugin(plugin_id, forget=False):
    _unload_id(plugin_id)
    _release_plugin_imports(plugin_id)
    if forget:
        _known_plugins.pop(str(plugin_id or ""), None)
    return True


def enter_safe_mode():
    """Остановить активные плагины, сохранив пользовательские состояния тумблеров."""
    for plugin in list(_plugins):
        plugin_id = str(getattr(plugin, "id", ""))
        _unload_id(plugin_id)
        _release_plugin_imports(plugin_id)
    _last_settings_objects.clear()
    _release_sdk_modules()
    return True


def reload_all(dir_path):
    """Выгрузить все плагины и загрузить папку заново (без перезапуска приложения)."""
    for p in list(_plugins):
        _unload_id(getattr(p, "id", ""))
    _known_plugins.clear()
    _last_settings_objects.clear()
    for name in list(sys.modules):
        if name.startswith("devgram_plugin_") or name.startswith("devgram_package_"):
            sys.modules.pop(name, None)
    for root in list(_package_roots.values()):
        wheels = os.path.join(root, 'wheels')
        if os.path.isdir(wheels):
            for filename in os.listdir(wheels):
                wheel_path = os.path.join(wheels, filename)
                while wheel_path in sys.path:
                    sys.path.remove(wheel_path)
    _package_roots.clear()
    _package_manifests.clear()
    _package_archives.clear()
    _release_sdk_modules()
    _log("── перезагрузка плагинов ──")
    n = load_dir(dir_path)
    _log("перезагрузка завершена: активно %d плагинов" % n)
    return n


def _release_sdk_modules():
    """Выгрузить публичный SDK и его реестры между полными перезапусками движка."""
    # Метаданные карточек остаются в _known_plugins и не требуют BasePlugin.
    for name in list(sys.modules):
        if name == "devgram" or name.startswith("devgram."):
            sys.modules.pop(name, None)
    importlib.invalidate_caches()
    gc.collect()


def reload_plugin(plugin_id):
    """Reload one installed plugin and leave every other plugin untouched."""
    plugin_id = str(plugin_id or "")
    plugin = next((item for item in _plugins if str(item.id) == plugin_id), None)
    record = _known_plugins.get(plugin_id, {})
    path = ((getattr(plugin, "_archive_path", "") or getattr(plugin, "_path", ""))
            if plugin is not None else str(record.get("path", "") or ""))
    package_root = (getattr(plugin, "_package_root", "") if plugin is not None
                    else str(record.get("package_root", "") or ""))
    should_be_active = plugin is not None or (_saved_enabled(plugin_id) and not
                                               jclass("org.telegram.messenger.DevGramPlugins").isSafeMode())
    if not path or not os.path.isfile(path):
        return -1
    _unload_id(plugin_id)
    if package_root:
        _remove_package_paths(plugin_id, True)
    importlib.invalidate_caches()
    prefix = ("devgram_package_" + ''.join(c if c.isalnum() else '_' for c in plugin_id)) if package_root else "devgram_plugin_"
    for name in list(sys.modules):
        if (name == prefix or name.startswith(prefix + '.')) if package_root else (
                name.startswith(prefix) and getattr(sys.modules.get(name), "__file__", None) == path):
            sys.modules.pop(name, None)
    result = load_from_file(path)
    if should_be_active and not any(str(item.id) == plugin_id for item in _plugins):
        return 0
    return result


def parse_meta(source):
    """БЕЗ выполнения кода (ast) извлечь метаданные из исходника .plugin.
    Возвращает 'id␟name␟version␟author␟description␟icon' или '' если это не плагин DevGram."""
    try:
        tree = ast.parse(source)
    except Exception:
        return ""
    meta = {"id": "", "name": "", "version": "", "author": "", "description": "", "icon": ""}
    aliases = {"__id__": "id", "__name__": "name", "__version__": "version", "__author__": "author", "__description__": "description", "__icon__": "icon"}
    scopes = [tree.body]
    scopes.extend(node.body for node in ast.walk(tree) if isinstance(node, ast.ClassDef))
    for body in scopes:
        for item in body:
            if isinstance(item, ast.Assign):
                for t in item.targets:
                    if isinstance(t, ast.Name) and (t.id in meta or t.id in aliases):
                        v = item.value
                        if isinstance(v, ast.Constant) and isinstance(v.value, str):
                            meta[aliases.get(t.id, t.id)] = v.value
    if not meta["name"] and not meta["id"]:
        return ""
    return _SEP.join([meta["id"], meta["name"], meta["version"], meta["author"], meta["description"], meta["icon"]])


def _unload_id(plugin_id):
    """Выгрузить уже загруженный плагин с таким id (для переустановки)."""
    plugin_id = str(plugin_id or "")
    try:
        for p in list(_plugins):
            if str(p.id) == plugin_id:
                _remember_plugin(p)
                try:
                    _invoke_unload(p)
                except Exception:
                    _err("on_unload", plugin=plugin_id)
                _plugins.remove(p)
    finally:
        _last_settings_objects.pop(plugin_id, None)
        _cleanup_java_runtime(plugin_id)


def _cleanup_java_runtime(plugin_id):
    """Снять все Java-хуки и UI-колбэки, даже если on_unload плагина упал."""
    if not plugin_id:
        return
    try:
        jclass("org.telegram.messenger.DevGramPlugins").cleanupPluginRuntime(plugin_id)
    except Exception:
        _err("cleanup runtime", plugin=plugin_id)


def _remember_plugin(plugin, enabled=None):
    """Сохранить только данные карточки и путь, не удерживая экземпляр/модуль плагина."""
    plugin_id = str(getattr(plugin, "id", ""))
    if not plugin_id:
        return
    archive_path = str(getattr(plugin, "_archive_path", "") or "")
    source_path = str(getattr(plugin, "_path", "") or "")
    _known_plugins[plugin_id] = {
        "id": plugin_id,
        "name": str(getattr(plugin, "name", "")),
        "version": str(getattr(plugin, "version", "")),
        "author": str(getattr(plugin, "author", "")),
        "enabled": bool(getattr(plugin, "enabled", True) if enabled is None else enabled),
        "filename": str(getattr(plugin, "_filename", "")),
        "description": str(getattr(plugin, "description", "")),
        "icon": str(getattr(plugin, "icon", "")),
        "path": archive_path or source_path,
        "package_root": str(getattr(plugin, "_package_root", "") or ""),
    }


def _remember_metadata(plugin_id, name, version, author, description, icon,
                       path, filename, enabled, package_root=""):
    """Сохранить карточку плагина, не импортируя и не создавая его класс."""
    plugin_id = str(plugin_id or "")
    if not plugin_id:
        return False
    _known_plugins[plugin_id] = {
        "id": plugin_id,
        "name": str(name or plugin_id),
        "version": str(version or ""),
        "author": str(author or ""),
        "enabled": bool(enabled),
        "filename": str(filename or os.path.basename(path)),
        "description": str(description or ""),
        "icon": str(icon or ""),
        "path": str(path or ""),
        "package_root": str(package_root or ""),
    }
    return True


def _saved_enabled(plugin_id):
    try:
        return bool(jclass("org.telegram.messenger.DevGramPlugins").isPluginEnabled(str(plugin_id)))
    except Exception:
        return True


def _persist_enabled(plugin_id, enabled):
    try:
        jclass("org.telegram.messenger.DevGramPlugins").persistPluginEnabled(
            str(plugin_id), bool(enabled))
    except Exception:
        pass


def _may_start_plugin(plugin_id):
    try:
        bridge = jclass("org.telegram.messenger.DevGramPlugins")
        return not bridge.isSafeMode() and bridge.isPluginEnabled(str(plugin_id))
    except Exception:
        return True


def _plain_metadata(path):
    """Метаданные обычного .plugin/.py через AST, без исполнения его верхнего уровня."""
    try:
        with open(path, "r", encoding="utf-8-sig") as source_file:
            row = parse_meta(source_file.read())
        if not row:
            return None
        fields = row.split(_SEP)
        fields += [""] * (6 - len(fields))
        if not fields[0]:
            return None
        return fields[:6]
    except Exception:
        return None


def _release_plugin_imports(plugin_id):
    """Убрать импортированные модули и wheel-пути выгруженного плагина."""
    plugin_id = str(plugin_id or "")
    record = _known_plugins.get(plugin_id, {})
    package_root = str(record.get("package_root", "") or _package_roots.get(plugin_id, "") or "")
    source_path = str(record.get("path", "") or "")
    if package_root:
        _remove_package_paths(plugin_id, True)
        prefix = "devgram_package_" + ''.join(c if c.isalnum() else '_' for c in plugin_id)
        root = _package_roots.pop(plugin_id, "") or package_root
        _package_manifests.pop(plugin_id, None)
        if root and os.path.isdir(root):
            shutil.rmtree(root, ignore_errors=True)
    else:
        prefix = "devgram_plugin_" + os.path.splitext(os.path.basename(source_path))[0]
    for name in list(sys.modules):
        if name == prefix or name.startswith(prefix + '.'):
            sys.modules.pop(name, None)
    importlib.invalidate_caches()


class _PluginExecution:
    def __init__(self, plugin):
        self.plugin = plugin
        self.token = 0

    def __enter__(self):
        try:
            self.token = _PluginWatchdog.onExecutionStarted(
                str(getattr(self.plugin, "id", "")),
                str(getattr(self.plugin, "name", "")),
                str(getattr(self.plugin, "_filename", "")))
        except Exception:
            self.token = 0
        return self

    def __exit__(self, exc_type, exc_value, traceback_value):
        try:
            _PluginWatchdog.onExecutionFinished(self.token)
        except Exception:
            pass
        return False


def _watch(plugin):
    return _PluginExecution(plugin)


def _invoke_load(plugin):
    """Запустить экземпляр ровно один раз и отметить успешный lifecycle."""
    if bool(getattr(plugin, "_runtime_loaded", False)):
        return
    plugin._runtime_loaded = True
    try:
        with _watch(plugin):
            modern = getattr(plugin, "on_plugin_load", None)
            if modern is not None and "on_plugin_load" in type(plugin).__dict__:
                modern()
            else:
                plugin.on_load()
    except Exception:
        try:
            _invoke_unload(plugin)
        except Exception:
            _err("rollback on_load", plugin=str(getattr(plugin, "id", "")))
        raise


def _invoke_unload(plugin):
    """Не вызывать on_unload повторно для уже остановленного экземпляра."""
    if not bool(getattr(plugin, "_runtime_loaded", False)):
        return
    try:
        with _watch(plugin):
            modern = getattr(plugin, "on_plugin_unload", None)
            if modern is not None and "on_plugin_unload" in type(plugin).__dict__:
                modern()
            else:
                plugin.on_unload()
    finally:
        plugin._runtime_loaded = False


def load_from_file(path):
    """Загрузить один .py/.plugin-файл. Возвращает число найденных плагинов."""
    from devgram import BasePlugin
    if path.endswith('.dgplugin'):
        return load_package(path)
    # Отключённые плагины и safe mode не должны выполнять даже код уровня модуля.
    # Карточку менеджера строим из литеральных метаданных через AST.
    metadata = _plain_metadata(path)
    if metadata is not None and not _may_start_plugin(metadata[0]):
        plugin_id, name, version, author, description, icon = metadata
        _unload_id(plugin_id)
        _remember_metadata(plugin_id, name, version, author, description, icon,
                           path, os.path.basename(path), _saved_enabled(plugin_id))
        _release_plugin_imports(plugin_id)
        return 1
    found = 0
    try:
        package_id = next((key for key, root in _package_roots.items() if os.path.abspath(path).startswith(os.path.abspath(root) + os.sep)), None)
        if package_id:
            package_name = "devgram_package_" + ''.join(c if c.isalnum() else '_' for c in package_id)
            if package_name not in sys.modules:
                package = types.ModuleType(package_name); package.__path__ = [_package_roots[package_id]]; sys.modules[package_name] = package
            rel = os.path.relpath(path, _package_roots[package_id]).replace(os.sep, '.')
            mod_name = package_name + '.' + os.path.splitext(rel)[0]
        else:
            mod_name = "devgram_plugin_" + os.path.splitext(os.path.basename(path))[0]
        spec = importlib.util.spec_from_file_location(mod_name, path)
        if spec is None or spec.loader is None:
            # CPython/некоторые версии Chaquopy не считают .plugin исходным суффиксом.
            loader = importlib.machinery.SourceFileLoader(mod_name, path)
            spec = importlib.util.spec_from_loader(mod_name, loader)
        module = importlib.util.module_from_spec(spec)
        sys.modules[mod_name] = module
        try:
            spec.loader.exec_module(module)
        except Exception:
            sys.modules.pop(mod_name, None)
            raise
        for attr in dir(module):
            obj = getattr(module, attr)
            if isinstance(obj, type) and issubclass(obj, BasePlugin) and obj is not BasePlugin:
                inst = obj()
                for public, modern in (("id", "__id__"), ("name", "__name__"), ("version", "__version__"), ("author", "__author__"), ("description", "__description__"), ("icon", "__icon__")):
                    if modern in module.__dict__ and not (modern == "__name__" and str(module.__dict__[modern]).startswith("devgram_plugin_")):
                        setattr(inst, public, module.__dict__[modern])
                inst._path = path
                inst._filename = os.path.basename(path)
                inst._package_root = _package_roots.get(package_id, "")
                if package_id:
                    manifest = _package_manifests.get(package_id, {})
                    for key in ('id', 'name', 'version', 'author', 'description', 'icon'):
                        if key in manifest: setattr(inst, key, manifest[key])
                    # единая карточка: имя файла = сам .dgplugin (а не внутренний main.py),
                    # чтобы «Поделиться»/«Удалить» работали как у .py. Покрывает и hot-reload.
                    archive = _package_archives.get(package_id, "")
                    if archive:
                        inst._filename = os.path.basename(archive)
                        inst._archive_path = archive
                _mf = _package_manifests.get(package_id, {}) if package_id else {}
                _cerr = _compat_error(
                    _mf.get('min_app_version') or getattr(inst, 'min_app_version', ''),
                    _mf.get('min_devgram') or _mf.get('min_sdk')
                        or getattr(inst, 'min_devgram', '') or getattr(inst, 'min_sdk', ''))
                if _cerr:
                    _log("несовместимый плагин пропущен %s: %s" % (getattr(inst, 'id', '?'), _cerr))
                    continue
                _unload_id(inst.id)  # если уже загружен такой id — заменяем (переустановка)
                inst._runtime_loaded = False
                _remember_plugin(inst)
                if _may_start_plugin(inst.id):
                    _plugins.append(inst)
                    try:
                        _invoke_load(inst)
                    except Exception:
                        if inst in _plugins:
                            _plugins.remove(inst)
                        inst.enabled = False
                        _remember_plugin(inst, False)
                        _persist_enabled(inst.id, False)
                        _cleanup_java_runtime(str(getattr(inst, "id", "")))
                        _release_plugin_imports(str(getattr(inst, "id", "")))
                        _err("on_load " + str(getattr(inst, "id", "?")), plugin=getattr(inst, "id", None))
                else:
                    _remember_plugin(inst, _saved_enabled(inst.id))
                    _release_plugin_imports(str(getattr(inst, "id", "")))
                found += 1
                _log("загружен: %s" % inst.name, level="LIFE", plugin=inst.id)
    except Exception:
        _err("load " + str(path))
    return found

def load_package(path):
    """Load a .dgplugin archive with manifest.json, main.py and optional modules."""
    if not zipfile.is_zipfile(path):
        _log('invalid .dgplugin: ' + path); return 0
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            # DevGram: лимиты на размер пакета/файлов сняты по требованию (нужны большие
            # нативные плагины, напр. VPN-ядро). Оставляем только структурные проверки.
            if (len(infos) > 4096 or len(archive.namelist()) != len(set(archive.namelist()))):
                raise ValueError('package structure invalid')
            names = set(archive.namelist())
            if 'manifest.json' not in names:
                _log('missing manifest.json'); return 0
            manifest = json.loads(archive.read('manifest.json').decode('utf-8'))
            plugin_id, main = _validate_package_manifest(manifest, names)
            # Старый экземпляр должен получить on_unload, пока его assets/wheels ещё существуют.
            _unload_id(plugin_id)
            _remove_package_paths(plugin_id, True)
            root = os.path.join(os.path.dirname(path), '.devgram', plugin_id)
            if not _may_start_plugin(plugin_id):
                if os.path.isdir(root):
                    shutil.rmtree(root, ignore_errors=True)
                _package_roots.pop(plugin_id, None)
                _package_manifests.pop(plugin_id, None)
                _package_archives[plugin_id] = path
                _remember_metadata(
                    plugin_id, manifest.get('name'), manifest.get('version'),
                    manifest.get('author'), manifest.get('description'), manifest.get('icon'),
                    path, os.path.basename(path), _saved_enabled(plugin_id))
                return 1
            if os.path.isdir(root): shutil.rmtree(root)
            for name in names:
                if name.startswith('/') or '..' in name.split('/'):
                    raise ValueError('unsafe archive path')
                target = os.path.join(root, name)
                if name.endswith('/'):
                    os.makedirs(target, exist_ok=True); continue
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with archive.open(name) as src, open(target, 'wb') as dst:
                    shutil.copyfileobj(src, dst)
        _package_roots[plugin_id] = root
        _package_manifests[plugin_id] = manifest
        wheels = os.path.join(root, 'wheels')
        if os.path.isdir(wheels):
            for filename in sorted(os.listdir(wheels)):
                if filename.endswith('.whl'):
                    sys.path.insert(0, os.path.join(wheels, filename))
        _package_archives[plugin_id] = path  # запомнить путь архива → load_from_file проставит _filename
        return load_from_file(os.path.join(root, main))
    except Exception:
        _err('package load ' + str(path)); return 0


def delete_package_files(plugin_id):
    """Удалить распакованные файлы установленного .dgplugin (сам архив удаляет Java).
    Для обычных .py/.plugin — безопасный no-op."""
    plugin_id = str(plugin_id or "")
    try:
        _remove_package_paths(plugin_id, True)
        root = _package_roots.pop(plugin_id, "")
        _package_manifests.pop(plugin_id, None)
        _package_archives.pop(plugin_id, None)
        if root and os.path.isdir(root):
            shutil.rmtree(root, ignore_errors=True)
        return True
    except Exception:
        _err('delete_package_files ' + str(plugin_id), plugin=plugin_id); return False

def package_meta(path):
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            names_list = archive.namelist()
            if (len(infos) > 4096 or len(names_list) != len(set(names_list))):
                return ''
            data = json.loads(archive.read('manifest.json').decode('utf-8'))
            _validate_package_manifest(data, set(names_list))
        return _SEP.join(str(data.get(key, '')) for key in ('id', 'name', 'version', 'author', 'description', 'icon'))
    except Exception: return ''


def validate_package(path):
    """Return an empty string when valid, otherwise a user-facing validation error."""
    try:
        if not zipfile.is_zipfile(path):
            return 'Файл не является архивом .dgplugin'
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            names_list = archive.namelist()
            if len(infos) > 4096:
                return 'В пакете слишком много файлов'
            if len(names_list) != len(set(names_list)):
                return 'В пакете есть файлы с повторяющимися именами'
            # DevGram: ограничения на размер пакета/файлов сняты (нужны большие нативные плагины).
            names = set(names_list)
            if 'manifest.json' not in names:
                return 'В пакете отсутствует manifest.json'
            manifest = json.loads(archive.read('manifest.json').decode('utf-8'))
            _validate_package_manifest(manifest, names)
            compat = _compat_error(manifest.get('min_app_version'),
                                   manifest.get('min_devgram') or manifest.get('min_sdk'))
            if compat:
                return compat
        return ''
    except Exception as error:
        return str(error) or 'Пакет повреждён'


def load_dir(dir_path):
    """Загрузить все .py из папки. Возвращает число загруженных плагинов."""
    if not dir_path or not os.path.isdir(dir_path):
        return 0
    n = 0
    for f in sorted(os.listdir(dir_path)):
        if f.endswith(".py") or f.endswith(".plugin") or f.endswith(".dgplugin"):
            n += load_from_file(os.path.join(dir_path, f))
    return n


CANCEL = "\x00DEVGRAM_CANCEL\x00"  # маркер отмены отправки (сверяется в Java)


def dispatch_send(account, text):
    """Хук исходящего текста: плагины меняют текст (return str), отменяют (return False) или ничего (None)."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            with _watch(p):
                from devgram import BasePlugin, HookStrategy
                if type(p).on_send_message_hook is not BasePlugin.on_send_message_hook:
                    hooked = p.on_send_message_hook(account, text)
                    strategy = getattr(hooked, "strategy", HookStrategy.PASS)
                    if strategy == HookStrategy.CANCEL:
                        return CANCEL
                    r = getattr(hooked, "result", None)
                    if r is None:
                        r = getattr(hooked, "params", None)
                else:
                    r = p.on_send_message(text)
                if r is False:
                    _log("on_send_message → отменил отправку", level="EVENT", plugin=p.id)
                    return CANCEL
                if r is not None and r != text:
                    _log("on_send_message изменил текст", level="EVENT", plugin=p.id)
                    text = r
        except Exception:
            _err("on_send_message", plugin=getattr(p, "id", None))
    return text


def dispatch_receive(text):
    """Хук входящего текста (только чтение/логика)."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            with _watch(p):
                p.on_receive_message(text)
        except Exception:
            _err("on_receive_message", plugin=getattr(p, "id", None))


def dispatch_hook(plugin_id, phase, frame):
    """Мост Pine-хука в плагин: phase = 'before'/'after'."""
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            if not getattr(p, "enabled", True):
                return
            try:
                with _watch(p):
                    if phase == "before":
                        p.before_hook(frame)
                    else:
                        p.after_hook(frame)
            except Exception:
                _err("hook %s" % phase, plugin=plugin_id)
            return


def wants_updates():
    """True, если хоть один включённый плагин переопределил on_update (чтобы не грузить зря)."""
    from devgram import BasePlugin
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        if (type(p).on_update is not BasePlugin.on_update
                or type(p).on_update_hook is not BasePlugin.on_update_hook):
            return True
    return False


def wants_request_hooks():
    """True, если хоть один включённый плагин переопределил on_send_request/on_receive_response."""
    from devgram import BasePlugin
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        if (type(p).on_send_request is not BasePlugin.on_send_request
                or type(p).on_receive_response is not BasePlugin.on_receive_response
                or type(p).on_send_request_hook is not BasePlugin.on_send_request_hook
                or type(p).on_receive_response_hook is not BasePlugin.on_receive_response_hook):
            return True
    return False


def dispatch_request(account, name, request):
    """Хук исходящего TL-запроса (плагины могут менять поля объекта на месте)."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            with _watch(p):
                from devgram import BasePlugin
                if type(p).on_send_request_hook is not BasePlugin.on_send_request_hook:
                    _log("→ %s (acc %s)" % (name, account), level="REQUEST", plugin=p.id)
                    p.on_send_request_hook(account, name, request)
                elif type(p).on_send_request is not BasePlugin.on_send_request:
                    _log("→ %s" % name, level="REQUEST", plugin=p.id)
                    p.on_send_request(name, request)
        except Exception:
            _err("on_send_request %s" % name, plugin=getattr(p, "id", None))


def dispatch_response(account, name, response, error):
    """Хук ответа сервера на TL-запрос (только чтение)."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            with _watch(p):
                from devgram import BasePlugin
                has_hook = type(p).on_receive_response_hook is not BasePlugin.on_receive_response_hook
                has_plain = type(p).on_receive_response is not BasePlugin.on_receive_response
                if has_hook or has_plain:
                    err = "" if error is None else (" ERR:" + str(error))
                    _log("← %s%s" % (name, err), level="REQUEST", plugin=p.id)
                if has_hook:
                    p.on_receive_response_hook(account, name, response, error)
                elif has_plain:
                    p.on_receive_response(name, response, error)
        except Exception:
            _err("on_receive_response %s" % name, plugin=getattr(p, "id", None))


def dispatch_update(account, update):
    """Хук сырых TL-апдейтов (частые — в лог пишем только ошибки)."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            with _watch(p):
                from devgram import BasePlugin
                if type(p).on_update_hook is not BasePlugin.on_update_hook:
                    p.on_update_hook(update.getClass().getSimpleName(), account, update)
                elif type(p).on_update is not BasePlugin.on_update:
                    p.on_update(update)
        except Exception:
            _err("on_update", plugin=getattr(p, "id", None))


def menu_items():
    """Пункты меню сообщения от всех включённых плагинов: строки 'pluginId␟label'."""
    res = []
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            with _watch(p):
                for label in (p.menu_items() or []):
                    res.append(_SEP.join([str(p.id), str(label)]))
        except Exception:
            _err("menu_items", plugin=getattr(p, "id", None))
    return res


def menu_click(plugin_id, label, message_text, dialog_id):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            try:
                with _watch(p):
                    _log("меню сообщения: %s" % label, level="EVENT", plugin=plugin_id)
                    p.on_menu_click(label, message_text, dialog_id)
            except Exception:
                _err("on_menu_click", plugin=plugin_id)
            return


def link_menu_items(url):
    """Пункты меню ссылки от всех включённых плагинов: строки 'pluginId␟label'."""
    res = []
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            with _watch(p):
                for label in (p.link_menu_items(url) or []):
                    res.append(_SEP.join([str(p.id), str(label)]))
        except Exception:
            _err("link_menu_items", plugin=getattr(p, "id", None))
    return res


def link_menu_click(plugin_id, label, url, dialog_id):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            try:
                with _watch(p):
                    _log("меню ссылки: %s" % label, level="EVENT", plugin=plugin_id)
                    p.on_link_menu_click(label, url, dialog_id)
            except Exception:
                _err("on_link_menu_click", plugin=plugin_id)
            return


_last_settings_objects = {}  # plugin_id -> «сырые» Setting-объекты последнего вызова
                              # plugin_settings() (нужно, чтобы potом отдать Java живой
                              # View для devgram.ui.Custom — его нельзя засунуть в строку).

def plugin_settings(plugin_id):
    """Строки настроек плагина: 'type␟key␟title'."""
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            try:
                with _watch(p):
                    res = []
                    raw = []
                    source = p.create_settings() if "create_settings" in type(p).__dict__ else p.settings()
                    for r in (source or []):
                        raw.append(r)
                        row = r.as_row() if hasattr(r, "as_row") else r
                        t = str(row[0])
                        k = str(row[1]) if len(row) > 1 else ""
                        title = str(row[2]) if len(row) > 2 else ""
                        extra = str(row[3]) if len(row) > 3 else ""
                        res.append(_SEP.join([t, k, title, extra]))
                    _last_settings_objects[str(plugin_id)] = raw
                    return res
            except Exception:
                _err("settings", plugin=getattr(p, "id", None))
            return []
    return []

def plugin_settings_custom_view(plugin_id, index):
    """Живой Java View для строки devgram.ui.Custom по её индексу в ПОСЛЕДНЕМ списке,
    отданном plugin_settings() — тот всегда зовётся первым и наполняет кэш заново."""
    objs = _last_settings_objects.get(str(plugin_id))
    if not objs or index < 0 or index >= len(objs):
        return None
    return getattr(objs[index], "view", None)


def plugin_setting_click(plugin_id, key):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            _log("настройка-кнопка: %s" % key, level="EVENT", plugin=plugin_id)
            try:
                with _watch(p):
                    p.on_setting_click(key)
            except Exception:
                _err("on_setting_click", plugin=plugin_id)
            return

def plugin_setting_changed(plugin_id, key, value):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            _log("настройка изменена: %s = %s" % (key, value), level="EVENT", plugin=plugin_id)
            try:
                with _watch(p):
                    source = p.create_settings() if "create_settings" in type(p).__dict__ else p.settings()
                    for item in (source or []):
                        if getattr(item, "key", None) == key and getattr(item, "on_change", None):
                            item.on_change(value)
                    callback = getattr(p, "on_setting_changed", None)
                    if callback: callback(key, value)
            except Exception:
                _err("on_setting_changed", plugin=plugin_id)
            return


def list_plugins():
    """Для менеджера: 'id␟name␟version␟author␟enabled␟filename␟description␟icon'."""
    for plugin in _plugins:
        _remember_plugin(plugin)
    res = []
    for record in _known_plugins.values():
        en = 1 if bool(record.get("enabled", True)) else 0
        res.append(_SEP.join([
            str(record.get("id", "")), str(record.get("name", "")),
            str(record.get("version", "")), str(record.get("author", "")), str(en),
            str(record.get("filename", "")), str(record.get("description", "")),
            str(record.get("icon", "")),
        ]))
    return res


def _set_record_enabled(plugin_id, enabled):
    record = _known_plugins.get(str(plugin_id or ""))
    if record is not None:
        record["enabled"] = bool(enabled)


def set_enabled(plugin_id, enabled):
    plugin_id = str(plugin_id or "")
    for p in _plugins:
        if str(p.id) == plugin_id:
            was = bool(getattr(p, "enabled", True))
            p.enabled = bool(enabled)
            _remember_plugin(p, enabled)
            # ВАЖНО: при выключении вызываем on_unload (плагин должен снять хуки, остановить
            # фоновые сервисы/ядро/прокси), при включении — on_load. Иначе disabled-плагин
            # продолжает работать (потоки/нативка/хуки не гейтятся флагом enabled).
            try:
                if was and not enabled:
                    _unload_id(plugin_id)
                    _release_plugin_imports(plugin_id)
                elif enabled and not bool(getattr(p, "_runtime_loaded", False)):
                    if jclass("org.telegram.messenger.DevGramPlugins").isSafeMode():
                        return True
                    _cleanup_java_runtime(plugin_id)
                    _invoke_load(p)
            except Exception:
                if enabled:
                    _cleanup_java_runtime(plugin_id)
                _err("set_enabled lifecycle", plugin=plugin_id)
            _log("включён" if enabled else "выключен", level="LIFE", plugin=plugin_id)
            return True

    record = _known_plugins.get(plugin_id)
    if record is None:
        return False
    _set_record_enabled(plugin_id, enabled)
    if not enabled:
        _cleanup_java_runtime(plugin_id)
        _release_plugin_imports(plugin_id)
        _log("выключен", level="LIFE", plugin=plugin_id)
        return True
    if jclass("org.telegram.messenger.DevGramPlugins").isSafeMode():
        return True
    path = str(record.get("path", "") or "")
    if not path or not os.path.isfile(path):
        _log("не найден файл плагина: " + path, level="ERROR", plugin=plugin_id)
        record["enabled"] = False
        _persist_enabled(plugin_id, False)
        return False
    load_from_file(path)
    loaded = any(str(plugin.id) == plugin_id for plugin in _plugins)
    if not loaded:
        record["enabled"] = False
        _persist_enabled(plugin_id, False)
    _log("включён" if loaded else "ошибка включения", level="LIFE" if loaded else "ERROR", plugin=plugin_id)
    return loaded


def count():
    return len(_known_plugins)

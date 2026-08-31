"""
DevGram: загрузчик и реестр плагинов. Вызывается из Java (DevGramPlugins).
Импортирует .py-файлы, находит наследников devgram.BasePlugin, хранит их,
и раздаёт события (хуки) по включённым плагинам.
"""

import ast
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
_SEP = "\x1f"  # разделитель полей для передачи в Java

_plugins = []  # экземпляры плагинов
_package_roots = {}
_package_manifests = {}
_package_archives = {}  # plugin_id -> путь к установленному .dgplugin (для единой карточки: _filename)


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


_LOG_BUF = []  # общий кольцевой буфер логов плагинов (для экрана «Логи плагинов»)


def _log(msg):
    line = str(msg)
    try:
        import time as _t
        entry = "[%s] %s" % (_t.strftime("%H:%M:%S"), line)
    except Exception:
        entry = line
    try:
        _LOG_BUF.append(entry)
        extra = len(_LOG_BUF) - 400
        if extra > 0:
            del _LOG_BUF[:extra]
    except Exception:
        pass
    _FileLog.d("[DevGramPlugins] " + line)


def get_logs():
    """Весь буфер логов одной строкой (для показа в приложении)."""
    try:
        return "\n".join(_LOG_BUF)
    except Exception:
        return ""


def clear_logs():
    try:
        del _LOG_BUF[:]
    except Exception:
        pass


def dg_log(msg):
    """Записать строку в общий лог плагинов (вызывается из Java для системных событий)."""
    _log(msg)


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


def unload_plugin(plugin_id):
    _unload_id(plugin_id)
    return True


def reload_all(dir_path):
    """Выгрузить все плагины и загрузить папку заново (без перезапуска приложения)."""
    for p in list(_plugins):
        try:
            p.on_unload()
        except Exception:
            pass
    _plugins.clear()
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
    _log("── перезагрузка плагинов ──")
    n = load_dir(dir_path)
    _log("перезагрузка завершена: активно %d плагинов" % n)
    return n


def reload_plugin(plugin_id):
    """Reload one installed plugin and leave every other plugin untouched."""
    plugin_id = str(plugin_id or "")
    plugin = next((item for item in _plugins if str(item.id) == plugin_id), None)
    if plugin is None:
        return -1
    path = getattr(plugin, "_path", "")
    package_root = getattr(plugin, "_package_root", "")
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
    return load_from_file(path)


def parse_meta(source):
    """БЕЗ выполнения кода (ast) извлечь метаданные из исходника .plugin.
    Возвращает 'id␟name␟version␟author␟description␟icon' или '' если это не плагин DevGram."""
    try:
        tree = ast.parse(source)
    except Exception:
        return ""
    meta = {"id": "", "name": "", "version": "", "author": "", "description": "", "icon": ""}
    aliases = {"__id__": "id", "__name__": "name", "__version__": "version", "__author__": "author", "__description__": "description", "__icon__": "icon"}
    for node in ast.walk(tree):
        if isinstance(node, ast.ClassDef):
            for item in node.body:
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
    for p in list(_plugins):
        if str(p.id) == str(plugin_id):
            try:
                modern = getattr(p, "on_plugin_unload", None)
                if modern is not None and "on_plugin_unload" in type(p).__dict__: modern()
                else: p.on_unload()
            except Exception:
                pass
            _plugins.remove(p)


def load_from_file(path):
    """Загрузить один .py/.plugin-файл. Возвращает число найденных плагинов."""
    from devgram import BasePlugin
    if path.endswith('.dgplugin'):
        return load_package(path)
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
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
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
                _unload_id(inst.id)  # если уже загружен такой id — заменяем (переустановка)
                _plugins.append(inst)
                try:
                    modern = getattr(inst, "on_plugin_load", None)
                    if modern is not None and "on_plugin_load" in type(inst).__dict__:
                        modern()
                    else:
                        inst.on_load()
                except Exception:
                    _log("on_load error: " + traceback.format_exc())
                found += 1
                _log("loaded plugin: %s (%s)" % (inst.name, inst.id))
    except Exception:
        _log("load error %s: %s" % (path, traceback.format_exc()))
    return found

def load_package(path):
    """Load a .dgplugin archive with manifest.json, main.py and optional modules."""
    if not zipfile.is_zipfile(path):
        _log('invalid .dgplugin: ' + path); return 0
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            if (len(infos) > 512 or len(archive.namelist()) != len(set(archive.namelist())) or
                    sum(info.file_size for info in infos) > 32 * 1024 * 1024 or
                    any(info.file_size > 8 * 1024 * 1024 for info in infos)):
                raise ValueError('package is too large')
            names = set(archive.namelist())
            if 'manifest.json' not in names:
                _log('missing manifest.json'); return 0
            manifest = json.loads(archive.read('manifest.json').decode('utf-8'))
            plugin_id, main = _validate_package_manifest(manifest, names)
            _remove_package_paths(plugin_id, True)
            root = os.path.join(os.path.dirname(path), '.devgram', plugin_id)
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
        _log('package load error: ' + traceback.format_exc()); return 0


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
        _log("delete_package_files error: " + traceback.format_exc()); return False

def package_meta(path):
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            names_list = archive.namelist()
            if (len(infos) > 512 or len(names_list) != len(set(names_list)) or
                    sum(info.file_size for info in infos) > 32 * 1024 * 1024 or
                    any(info.file_size > 8 * 1024 * 1024 for info in infos)):
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
            if len(infos) > 512:
                return 'В пакете слишком много файлов'
            if len(names_list) != len(set(names_list)):
                return 'В пакете есть файлы с повторяющимися именами'
            if sum(info.file_size for info in infos) > 32 * 1024 * 1024:
                return 'Размер распакованного пакета превышает 32 МБ'
            if any(info.file_size > 8 * 1024 * 1024 for info in infos):
                return 'Один из файлов пакета превышает 8 МБ'
            names = set(names_list)
            if 'manifest.json' not in names:
                return 'В пакете отсутствует manifest.json'
            manifest = json.loads(archive.read('manifest.json').decode('utf-8'))
            _validate_package_manifest(manifest, names)
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
                return CANCEL
            if r is not None:
                text = r
        except Exception:
            _log("on_send_message error: " + traceback.format_exc())
    return text


def dispatch_receive(text):
    """Хук входящего текста (только чтение/логика)."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            p.on_receive_message(text)
        except Exception:
            _log("on_receive_message error: " + traceback.format_exc())


def dispatch_hook(plugin_id, phase, frame):
    """Мост Pine-хука в плагин: phase = 'before'/'after'."""
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            if not getattr(p, "enabled", True):
                return
            try:
                if phase == "before":
                    p.before_hook(frame)
                else:
                    p.after_hook(frame)
            except Exception:
                _log("hook dispatch error: " + traceback.format_exc())
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
            from devgram import BasePlugin
            if type(p).on_send_request_hook is not BasePlugin.on_send_request_hook:
                p.on_send_request_hook(account, name, request)
            else:
                p.on_send_request(name, request)
        except Exception:
            _log("on_send_request error: " + traceback.format_exc())


def dispatch_response(account, name, response, error):
    """Хук ответа сервера на TL-запрос (только чтение)."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            from devgram import BasePlugin
            if type(p).on_receive_response_hook is not BasePlugin.on_receive_response_hook:
                p.on_receive_response_hook(account, name, response, error)
            else:
                p.on_receive_response(name, response, error)
        except Exception:
            _log("on_receive_response error: " + traceback.format_exc())


def dispatch_update(account, update):
    """Хук сырых TL-апдейтов."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            from devgram import BasePlugin
            if type(p).on_update_hook is not BasePlugin.on_update_hook:
                p.on_update_hook(update.getClass().getSimpleName(), account, update)
            else:
                p.on_update(update)
        except Exception:
            _log("on_update error: " + traceback.format_exc())


def menu_items():
    """Пункты меню сообщения от всех включённых плагинов: строки 'pluginId␟label'."""
    res = []
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            for label in (p.menu_items() or []):
                res.append(_SEP.join([str(p.id), str(label)]))
        except Exception:
            _log("menu_items error: " + traceback.format_exc())
    return res


def menu_click(plugin_id, label, message_text, dialog_id):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            try:
                p.on_menu_click(label, message_text, dialog_id)
            except Exception:
                _log("on_menu_click error: " + traceback.format_exc())
            return


def link_menu_items(url):
    """Пункты меню ссылки от всех включённых плагинов: строки 'pluginId␟label'."""
    res = []
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            for label in (p.link_menu_items(url) or []):
                res.append(_SEP.join([str(p.id), str(label)]))
        except Exception:
            _log("link_menu_items error: " + traceback.format_exc())
    return res


def link_menu_click(plugin_id, label, url, dialog_id):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            try:
                p.on_link_menu_click(label, url, dialog_id)
            except Exception:
                _log("on_link_menu_click error: " + traceback.format_exc())
            return


_last_settings_objects = {}  # plugin_id -> «сырые» Setting-объекты последнего вызова
                              # plugin_settings() (нужно, чтобы potом отдать Java живой
                              # View для devgram.ui.Custom — его нельзя засунуть в строку).

def plugin_settings(plugin_id):
    """Строки настроек плагина: 'type␟key␟title'."""
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            try:
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
                _log("settings error: " + traceback.format_exc())
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
            try:
                p.on_setting_click(key)
            except Exception:
                _log("on_setting_click error: " + traceback.format_exc())
            return

def plugin_setting_changed(plugin_id, key, value):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            try:
                source = p.create_settings() if "create_settings" in type(p).__dict__ else p.settings()
                for item in (source or []):
                    if getattr(item, "key", None) == key and getattr(item, "on_change", None):
                        item.on_change(value)
                callback = getattr(p, "on_setting_changed", None)
                if callback: callback(key, value)
            except Exception:
                _log("on_setting_changed error: " + traceback.format_exc())
            return


def list_plugins():
    """Для менеджера: 'id␟name␟version␟author␟enabled␟filename␟description␟icon'."""
    res = []
    for p in _plugins:
        en = 1 if getattr(p, "enabled", True) else 0
        res.append(_SEP.join([
            str(p.id), str(p.name), str(p.version), str(p.author),
            str(en), str(getattr(p, "_filename", "")), str(getattr(p, "description", "")),
            str(getattr(p, "icon", "")),
        ]))
    return res


def set_enabled(plugin_id, enabled):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            p.enabled = bool(enabled)
            return True
    return False


def count():
    return len(_plugins)

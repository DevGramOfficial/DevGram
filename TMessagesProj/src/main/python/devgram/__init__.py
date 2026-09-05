"""
DevGram: Python API плагинов (аналог base_plugin у exteraGram).

Плагин — это .py-файл, который определяет класс-наследник BasePlugin.
Загрузчик (Java: DevGramPlugins) импортирует файл, находит наследника BasePlugin,
создаёт его, зовёт on_load(); хуки вызываются из Java при событиях клиента.

Минимальный плагин:

    from devgram import BasePlugin

    class MyPlugin(BasePlugin):
        id = "my_plugin"
        name = "Мой плагин"
        version = "1.0"
        author = "Vlad"

        def on_load(self):
            self.log("плагин загружен")

        def on_send_message(self, text):
            # вернуть изменённый текст, либо None — оставить как есть
            return text.replace(":love:", "❤️")
"""

from java import jclass
import os
import json

_FileLog = jclass("org.telegram.messenger.FileLog")
_Plugins = jclass("org.telegram.messenger.DevGramPlugins")

class HookStrategy:
    PASS = "pass"
    MODIFY = "modify"
    CANCEL = "cancel"

class HookResult:
    def __init__(self, strategy=HookStrategy.PASS, params=None, result=None):
        self.strategy, self.params, self.result = strategy, params, result

class AccountClient:
    def __init__(self, account): self.account = int(account)
    def get_messages_controller(self): return _Plugins.messagesController(self.account)
    def get_user_config(self): return _Plugins.userConfig(self.account)
    def get_connections_manager(self): return _Plugins.connectionsManager(self.account)
    def send_text(self, dialog_id, text): _Plugins.sendText(self.account, int(dialog_id), str(text))

def get_selected_account(): return int(_Plugins.currentAccount())
def get_client(account=None): return AccountClient(get_selected_account() if account is None else account)


class BasePlugin:
    # метаданные (переопредели в своём плагине)
    id = "plugin"
    name = "Plugin"
    version = "1.0"
    author = ""
    description = ""
    icon = ""  # URL картинки-аватарки плагина (png/jpg) — показывается в списке плагинов
    # совместимость (информационно; загрузчик может предупредить при несоответствии)
    min_app_version = ""   # минимальная версия приложения (напр. "12.9")
    min_sdk = ""           # минимальная версия API плагинов DevGram (напр. "2")
    requirements = []      # список pip-зависимостей (если движок их поддерживает)
    sdk_version = "1"

    def __init__(self):
        self.enabled = True
        self._package_root = ""

    # ---- жизненный цикл ----
    def on_load(self):
        """Вызывается один раз при загрузке плагина."""
        pass

    def on_unload(self):
        """Вызывается при выгрузке/отключении плагина."""
        pass

    # ---- хуки сообщений ----
    def on_send_message(self, text):
        """Перед отправкой текста. Вернуть новый текст, None (без изменений) или False (отменить отправку)."""
        return None

    def on_receive_message(self, text):
        """При получении текстового сообщения (только для чтения/логики)."""
        return None

    def on_update(self, update):
        """Сырой TL-апдейт (Java-объект TLRPC.Update). Читай поля через рефлексию. Только чтение."""
        pass

    # ---- хуки TL-запросов/ответов (client-level) ----
    def on_send_request(self, name, request):
        """Перед отправкой TL-запроса на сервер. name — имя класса запроса (напр. 'TL_messages_sendMessage'),
        request — Java-объект TLObject. Можно менять поля объекта на месте. Только для стандартного
        2-арг ConnectionsManager.sendRequest. Переопредели, чтобы включить хук."""
        return None

    def on_receive_response(self, name, response, error):
        """После ответа сервера на TL-запрос. name — имя запроса, response — Java-объект ответа (или None),
        error — текст ошибки (или None). Только чтение. Переопредели, чтобы включить хук."""
        return None

    def on_send_request_hook(self, account, name, request):
        """Account-aware variant of on_send_request. Override this instead of the legacy callback."""
        return None

    def on_receive_response_hook(self, account, name, response, error):
        """Account-aware variant of on_receive_response. Override this instead of the legacy callback."""
        return None

    # ---- меню сообщения ----
    def menu_items(self):
        """Названия пунктов, которые плагин добавляет в меню длинного тапа по сообщению."""
        return []

    def on_menu_click(self, label, message_text, dialog_id):
        """Клик по пункту меню, добавленному этим плагином."""
        pass

    def link_menu_items(self, url):
        """Пункты, которые плагин добавляет в меню длинного тапа по ССЫЛКЕ.
        url — ссылка, по которой открыто меню. Верните список строк-подписей."""
        return []

    def on_link_menu_click(self, label, url, dialog_id):
        """Клик по пункту меню ссылки, добавленному этим плагином."""
        pass

    # ---- клиентское API (client_utils) ----
    def toast(self, text):
        """Короткое всплывающее уведомление."""
        _Plugins.toast(str(text))

    def me(self):
        """ID текущего аккаунта."""
        return _Plugins.myId()

    def get_setting(self, key, default=""):
        """Прочитать сохранённую настройку плагина."""
        return _Plugins.pluginGet(self.id, str(key), str(default))

    def set_setting(self, key, value):
        """Сохранить настройку плагина (переживает перезапуск)."""
        _Plugins.pluginSet(self.id, str(key), str(value))

    def user_name(self, uid):
        """Имя пользователя по id (или '')."""
        return _Plugins.userName(int(uid))

    def chat_name(self, cid):
        """Имя чата/канала по id (или '')."""
        return _Plugins.chatName(int(cid))

    def send_message(self, dialog_id, text, reply_to=0, entities=None):
        """Отправить текст в диалог. reply_to — id сообщения для ответа (0 = без ответа).
        entities — java.util.ArrayList<TLRPC.MessageEntity> (жирный текст/текстовые ссылки),
        построенный самим плагином через jclass; см. BasePlugin.text_link_entity/bold_entity."""
        if entities is not None:
            _Plugins.sendMessageEntities(int(dialog_id), str(text), entities)
        elif reply_to:
            _Plugins.sendMessageReply(int(dialog_id), str(text), int(reply_to))
        else:
            _Plugins.sendMessage(int(dialog_id), str(text))

    @staticmethod
    def new_entities_list():
        """Пустой java.util.ArrayList под message entities для send_message/send_photo."""
        return jclass("java.util.ArrayList")()

    @staticmethod
    def bold_entity(offset, length):
        """TL_messageEntityBold — offset/length в UTF-16 code units (для строк без
        суррогатных пар — эмодзи и т.п. — совпадает с обычной len())."""
        e = jclass("org.telegram.tgnet.TLRPC$TL_messageEntityBold")()
        e.offset, e.length = int(offset), int(length)
        return e

    @staticmethod
    def text_link_entity(offset, length, url):
        """TL_messageEntityTextUrl — кликабельный текст с произвольной подписью (как
        markdown [текст](ссылка)); offset/length — см. bold_entity."""
        e = jclass("org.telegram.tgnet.TLRPC$TL_messageEntityTextUrl")()
        e.offset, e.length, e.url = int(offset), int(length), str(url)
        return e

    def debug_check_attachment(self, path):
        """DEBUG: прогнать файл через те же проверки, что делает клиент перед отправкой
        (exists/length/isInternalUri) — помогает понять причину «Вложение не
        поддерживается», которая иначе теряется в асинхронной отправке."""
        return str(_Plugins.debugCheckAttachment(str(path)))

    def update_profile_about(self, about):
        """Обновить био/"о себе" ТЕКУЩЕГО аккаунта (тот же запрос, что штатный экран
        изменения био). Не создаёт отдельного экрана выбора аккаунта — работает с тем,
        что сейчас выбран в приложении (UserConfig.selectedAccount)."""
        _Plugins.updateProfileAbout(str(about))

    # ---- богатая отправка (client_utils) ----
    def send_photo(self, dialog_id, path, caption="", entities=None):
        """Отправить фото из файла (path — абсолютный путь). entities — см. send_message."""
        if entities is not None:
            _Plugins.sendPhotoEntities(int(dialog_id), str(path), str(caption), entities)
        else:
            _Plugins.sendPhoto(int(dialog_id), str(path), str(caption))

    def send_file(self, dialog_id, path, caption=""):
        """Отправить файл (видео/аудио/документ — тип определяется автоматически)."""
        _Plugins.sendFile(int(dialog_id), str(path), str(caption))

    # алиасы под привычные имена
    def send_document(self, dialog_id, path, caption=""):
        self.send_file(dialog_id, path, caption)

    def send_video(self, dialog_id, path, caption=""):
        self.send_file(dialog_id, path, caption)

    def send_audio(self, dialog_id, path, caption=""):
        self.send_file(dialog_id, path, caption)

    def edit_message(self, dialog_id, message_id, text):
        """Отредактировать текст своего сообщения."""
        _Plugins.editMessageText(int(dialog_id), int(message_id), str(text))

    def send_request(self, request, callback=None):
        """Отправить сырой TL-запрос (request — Java-объект TLObject, строится через tl()).
        callback(response, error_text) зовётся при ответе. Возвращает токен запроса."""
        return _Plugins.sendRequest(request, callback)

    @staticmethod
    def tl(class_name):
        """Создать пустой TL-объект по короткому имени, напр. tl('TL_messages_getHistory').
        Возвращает Java-объект; поля заполняй как атрибуты."""
        return jclass("org.telegram.tgnet.TLRPC$" + str(class_name))()

    # ---- доступ к контроллерам (текущий аккаунт) ----
    def current_account(self):
        return _Plugins.currentAccount()

    def client(self, account=None):
        return get_client(account)

    def get_hook_account(self):
        return self.current_account()

    def messages_controller(self):
        return _Plugins.messagesController()

    def connections_manager(self):
        return _Plugins.connectionsManager()

    def user_config(self):
        return _Plugins.userConfig()

    def send_messages_helper(self):
        return _Plugins.sendMessagesHelper()

    def media_data_controller(self):
        return _Plugins.mediaDataController()

    def contacts_controller(self):
        return _Plugins.contactsController()

    def messages_storage(self):
        return _Plugins.messagesStorage()

    def notification_center(self):
        return _Plugins.notificationCenter()

    def file_loader(self):
        return _Plugins.fileLoader()

    def account_instance(self):
        return _Plugins.accountInstance()

    # ---- потоки (android_utils) ----
    def run_on_ui(self, fn, delay=0):
        """Выполнить функцию на UI-потоке (delay — задержка в мс)."""
        _Plugins.runOnUi(fn, int(delay))

    def run_on_queue(self, fn):
        """Выполнить функцию в фоновой очереди (не блокирует UI)."""
        _Plugins.runOnQueue(fn)

    # ---- реализация Java-интерфейсов из Python (class-proxy для интерфейсов) ----
    @staticmethod
    def implement(interface, **methods):
        """Создать Java-объект, реализующий интерфейс(ы), методами на Python.
        interface — Java-класс (jclass) или кортеж классов; methods — {имя_метода: функция(self, *args)}.
        Пример: l = self.implement(jclass('android.view.View$OnClickListener'), onClick=lambda s,v: ...).
        Покрывает слушатели/делегаты (OnClickListener, RequestDelegate, NotificationCenterDelegate)."""
        from java import dynamic_proxy
        ifaces = interface if isinstance(interface, tuple) else (interface,)
        proxy_cls = type("DevGramProxy", (dynamic_proxy(*ifaces),), dict(methods))
        return proxy_cls()

    def on_click(self, fn):
        """Java View.OnClickListener из Python-функции fn(view)."""
        OCL = jclass("android.view.View$OnClickListener")
        return self.implement(OCL, onClick=lambda s, v: fn(v))

    def on_long_click(self, fn):
        """Java View.OnLongClickListener из Python-функции fn(view)->bool."""
        OLCL = jclass("android.view.View$OnLongClickListener")
        return self.implement(OLCL, onLongClick=lambda s, v: bool(fn(v)))

    # ---- настоящий Java-подкласс из Python (Class Proxy на DexMaker) ----
    @staticmethod
    def _alist(items):
        """Python-список -> java.util.ArrayList (Chaquopy не конвертит list->List сам)."""
        al = jclass("java.util.ArrayList")()
        if items:
            for x in items:
                al.add(x)
        return al

    @staticmethod
    def _override_names(logic):
        """Имена методов, определённых в классе logic (для маршрутизации в override)."""
        names = set()
        for cls in type(logic).__mro__:
            if cls is object:
                break
            for k, v in cls.__dict__.items():
                if callable(v) and not k.startswith("__"):
                    names.add(k)
        return names

    def java_class(self, base_class_name, logic, arg_types=None, args=None):
        """Создать РЕАЛЬНЫЙ Java-подкласс base_class_name (напр. 'android.view.View').
        logic — Python-объект: его одноимённые методы переопределяют Java-методы; сигнатура
        метода — (self, this, *java_args), где this — сам Java-объект (для call_super).
        arg_types/args — типы и значения аргументов конструктора базового класса
        (напр. arg_types=['android.content.Context'], args=[context]).
        Возвращает Java-инстанс подкласса (или None). Пример:

            class Logic:
                def onDraw(self, this, canvas):
                    BasePlugin.java_super()          # super.onDraw(canvas)
                    canvas.drawColor(0x2200FF00)
            view = self.java_class('android.view.View', Logic(),
                                   arg_types=['android.content.Context'], args=[ctx])
        """
        try:
            names = self._override_names(logic)
            return _Plugins.subclass(
                str(base_class_name), logic,
                self._alist(list(names)),
                self._alist(arg_types),
                self._alist(args),
            )
        except Exception as e:
            self.log("java_class: " + str(e))
            return None

    @staticmethod
    def java_super(*args):
        """Вызвать оригинальный (super) метод из тела Python-override.
        Без аргументов — те же аргументы, что пришли; с аргументами — переопределить их.
        Возвращает результат super-метода."""
        if args:
            al = jclass("java.util.ArrayList")()
            for x in args:
                al.add(x)
            return _Plugins.callSuper(al)
        return _Plugins.callSuper(None)

    # ---- android_utils ----
    def copy(self, text):
        """Скопировать текст в буфер обмена."""
        _Plugins.copyToClipboard(str(text))

    def clipboard(self):
        """Прочитать текст из буфера обмена."""
        return _Plugins.getClipboard()

    def open_dialog(self, dialog_id):
        """Open a Telegram user, group, channel or secret dialog by dialog ID."""
        return bool(_Plugins.openDialog(int(dialog_id)))

    def new_canvas(self, bitmap):
        """Create an android.graphics.Canvas for the given Bitmap.

        Use this instead of calling jclass('android.graphics.Canvas')(bitmap) directly:
        constructing Canvas from Python can hit an Android hidden-API restriction
        (NoSuchMethodError: Canvas.<init>(J)V) where Chaquopy resolves the internal
        native-pointer constructor instead of the public Canvas(Bitmap) one. This calls
        `new Canvas(bitmap)` from compiled Java instead, which has no such ambiguity."""
        return _Plugins.newCanvas(bitmap)

    def new_file_output_stream(self, path):
        """Create a java.io.FileOutputStream for the given path (str).

        Use this instead of calling jclass('java.io.FileOutputStream')(path) directly:
        FileOutputStream has a hidden package-private constructor
        FileOutputStream(FileDescriptor, boolean), and Chaquopy can resolve THAT one
        instead of the public FileOutputStream(String) one, raising NoSuchMethodError.
        This calls `new FileOutputStream(path)` from compiled Java instead, which has
        no such ambiguity."""
        return _Plugins.newFileOutputStream(str(path))

    def paint_color(self, paint, color):
        """Set an ARGB int color on an android.graphics.Paint.

        Use this instead of paint.setColor(color) directly: Paint has both
        setColor(int) and setColor(long) overloads (the long one added in API 26
        for wide-gamut colors). Calling through Chaquopy with a plain Python int
        can resolve to the long overload, which misinterprets the value as a
        packed color and throws IllegalArgumentException: Invalid ID: N. This
        calls Paint.setColor(int) from compiled Java, which has no such ambiguity."""
        _Plugins.paintSetColor(paint, color)

    def canvas_draw_rect(self, canvas, left, top, right, bottom, paint):
        """canvas.drawRect(left, top, right, bottom, paint), routed through compiled Java.

        Most Canvas draw methods (drawRect, drawCircle, drawBitmap, drawText, ...) are
        actually declared on a hidden superclass (BaseCanvas) on modern Android. Calling
        them from compiled Java resolves fine (the compiler links the call directly), but
        calling canvas.drawRect(...) from Python through Chaquopy resolves the method by
        reflection on its declaring class and hits Android's hidden-API restriction there.
        Use these canvas_draw_*/canvas_save/canvas_restore/canvas_clip_path helpers instead
        of calling Canvas methods directly from plugin code."""
        _Plugins.canvasDrawRect(canvas, float(left), float(top), float(right), float(bottom), paint)

    def canvas_draw_round_rect(self, canvas, rect, rx, ry, paint):
        """canvas.drawRoundRect(rect, rx, ry, paint). See canvas_draw_rect for why this exists."""
        _Plugins.canvasDrawRoundRect(canvas, rect, float(rx), float(ry), paint)

    def canvas_draw_circle(self, canvas, cx, cy, radius, paint):
        """canvas.drawCircle(cx, cy, radius, paint). See canvas_draw_rect for why this exists."""
        _Plugins.canvasDrawCircle(canvas, float(cx), float(cy), float(radius), paint)

    def canvas_draw_bitmap(self, canvas, bitmap, left, top, paint):
        """canvas.drawBitmap(bitmap, left, top, paint). See canvas_draw_rect for why this exists."""
        _Plugins.canvasDrawBitmap(canvas, bitmap, float(left), float(top), paint)

    def canvas_draw_text(self, canvas, text, x, y, paint):
        """canvas.drawText(text, x, y, paint). See canvas_draw_rect for why this exists."""
        _Plugins.canvasDrawText(canvas, text, float(x), float(y), paint)

    def canvas_clip_path(self, canvas, path):
        """canvas.clipPath(path). See canvas_draw_rect for why this exists."""
        _Plugins.canvasClipPath(canvas, path)

    def canvas_save(self, canvas):
        """canvas.save(). See canvas_draw_rect for why this exists."""
        _Plugins.canvasSave(canvas)

    def canvas_restore(self, canvas):
        """canvas.restore(). See canvas_draw_rect for why this exists."""
        _Plugins.canvasRestore(canvas)

    # ---- file_utils (файлы в личной папке плагина) ----
    def read_file(self, name):
        """Прочитать файл из личной папки плагина (или '')."""
        return _Plugins.readData(self.id, str(name))

    def write_file(self, name, content):
        """Записать файл в личную папку плагина."""
        _Plugins.writeData(self.id, str(name), str(content))

    def plugin_files_dir(self):
        """Абсолютный путь к личной папке плагина на внешнем хранилище приложения.

        Используйте эту папку (не tempfile/системный temp) для файлов, которые затем
        отправляются через send_photo/send_file: tempfile.mkstemp() создаёт файл во
        внутреннем кэше приложения (/data/data/<package>/...), а Android-клиент
        блокирует отправку любого файла с таким путём как «неподдерживаемое вложение»
        (защита от отправки внутренних файлов приложения). Эта папка — снаружи, под
        защиту не попадает."""
        return str(_Plugins.pluginFilesDirPath(self.id))

    # ---- диалоги / bulletins ----
    def bulletin(self, text, kind="info", duration=2750, button=None, callback=None):
        """Показать Telegram-плашку. kind: info/success/error."""
        _Plugins.bulletin(str(kind), str(text), int(duration),
                          None if button is None else str(button), callback)

    def alert(self, title, message="", positive="OK", on_positive=None,
              negative=None, on_negative=None, neutral=None, on_neutral=None):
        """Показать диалог с кнопками и callback-функциями."""
        _Plugins.alert(str(title), str(message),
                       None if positive is None else str(positive), on_positive,
                       None if negative is None else str(negative), on_negative,
                       None if neutral is None else str(neutral), on_neutral)

    # ---- UI: страница настроек плагина ----
    def settings(self):
        """Строки настроек экрана плагина. Каждая — кортеж:
        ("header", "", "Заголовок")               — раздел
        ("switch", "ключ", "Название")            — переключатель (хранится 1/0)
        ("text",   "ключ", "Название")            — текстовое поле
        ("button", "ключ", "Название")            — кнопка (зовёт on_setting_click)
        Значения читаются/пишутся через get_setting/set_setting."""
        return []

    def create_settings(self):
        return self.settings()

    def on_setting_changed(self, key, value):
        pass

    def register_pill(self, pill_id, name, text="", value=None, on_click=None, on_long_click=None, enabled=False):
        """Register an interactive Pill Stack widget owned by this plugin."""
        return _Plugins.registerPill(str(self.id), str(pill_id), str(name), str(text), value, on_click, on_long_click, bool(enabled))

    def unregister_pills(self):
        _Plugins.unregisterPills(str(self.id))

    def register_panel_tab(self, title, build_view, on_action=None, icon_path=None):
        """Register a custom tab next to Emoji/GIF/Stickers in the message input panel.
        build_view(context, dialog_id) is called each time the tab is shown/selected and
        must return a live android.view.View (build a fresh one, don't cache — that's how
        the tab stays up to date, e.g. showing the currently playing track). dialog_id is
        the chat the panel is currently attached to.
        on_action(anchor_view, dialog_id), if given, wires a ready-made button in the same
        corner of the panel as the built-in tab buttons (like reSwaga's send arrow) — no
        core changes needed for this, it's a generic slot. If you want your own button(s)
        inside the tab instead (any position, any look), just add them in build_view and
        skip on_action entirely.
        icon_path, if given (an absolute path, e.g. from self.asset_path('icon.png')), makes
        the tab show that icon instead of the title text — same behaviour as the built-in
        Emoji/GIF/Stickers icon tabs. Re-registering (e.g. every on_load()) is normal and
        cheap — the icon/title/callbacks simply get replaced, nothing needs to be undone
        first for this to "persist" across app restarts."""
        _Plugins.registerPanelTab(str(self.id), str(title), build_view, on_action,
                                   None if not icon_path else str(icon_path))

    def unregister_panel_tab(self):
        _Plugins.unregisterPanelTab(str(self.id))

    def asset_path(self, name):
        """Absolute path to an asset bundled under assets/ in a .dgplugin package."""
        if not self._package_root: return ""
        root = os.path.abspath(os.path.join(self._package_root, "assets"))
        path = os.path.abspath(os.path.join(root, str(name)))
        return path if path.startswith(root + os.sep) and os.path.isfile(path) else ""

    def string(self, key, default=None, locale=None, **values):
        """Read a localized string from locales/<language>.json with English fallback."""
        if not self._package_root: return default if default is not None else str(key)
        if locale is None:
            try: locale = str(jclass("org.telegram.messenger.LocaleController").getInstance().getCurrentLocale().getLanguage())
            except Exception: locale = "en"
        data = {}
        for language in ("en", str(locale)):
            path = os.path.join(self._package_root, "locales", language + ".json")
            try:
                with open(path, encoding="utf-8") as stream: data.update(json.load(stream))
            except Exception: pass
        text = str(data.get(str(key), default if default is not None else key))
        try: return text.format(**values)
        except Exception: return text

    def on_setting_click(self, key):
        """Клик по кнопке-настройке (type='button')."""
        pass

    # ---- Xposed-хуки Java-методов (AliuHook/LSPlant) ----
    def hook(self, class_name, method_name, *param_types, before=None, after=None, replace=None, priority=0):
        """Хукнуть Java-метод/конструктор ЛЮБОГО класса (профиль, чаты, настройки — везде).
        param_types — типы аргументов: 'int','long','boolean','java.lang.String' и т.п.
        method_name='<init>' — конструктор.

        Два режима (как у exteraGram):
        • Per-hook колбэки: hook(cls, m, ...types, before=fn, after=fn, replace=fn, priority=0)
          — своя функция на ЭТОТ хук. fn(frame): frame.args/thisObject/getResult/setResult/method.
          replace=fn — ПОЛНАЯ подмена метода (оригинал не вызывается, вернёшь результат из fn).
        • Глобальный: hook(cls, m, ...types) без колбэков — сработают self.before_hook/after_hook.
        Возвращает True при успехе."""
        pts = jclass("java.util.ArrayList")()
        for t in param_types:
            pts.add(str(t))
        if before is not None or after is not None or replace is not None:
            return _Plugins.hookCb(self.id, str(class_name), str(method_name), pts,
                                   before, after, replace, int(priority))
        return _Plugins.hook(self.id, str(class_name), str(method_name), pts)

    add_hook = hook

    def hook_all(self, class_name, method_name, before=None, after=None, replace=None, priority=0):
        """Хукнуть ВСЕ перегрузки метода (или все конструкторы, method_name='<init>')
        со своими колбэками. Возвращает число установленных хуков."""
        return _Plugins.hookAllCb(self.id, str(class_name), str(method_name),
                                  before, after, replace, int(priority))

    def deoptimize(self, class_name, method_name, *param_types):
        """Деоптимизировать метод — чтобы инлайненные call-сайты пошли через него
        (полезно перед хуком «неуловимого» метода). Возвращает True при успехе."""
        pts = jclass("java.util.ArrayList")()
        for t in param_types:
            pts.add(str(t))
        return _Plugins.deoptimize(str(class_name), str(method_name), pts)

    def invoke_original(self, frame):
        """Вызвать ОРИГИНАЛ метода в обход хуков — обычно внутри before/replace-колбэка,
        чтобы условно выполнить оригинал или доработать его результат. Возвращает результат."""
        return _Plugins.invokeOriginal(frame)

    def unhook_all(self):
        """Снять ВСЕ хуки, установленные этим плагином."""
        _Plugins.unhookPlugin(self.id)
        return True

    def make_class_inheritable(self, class_name):
        """Сделать final-класс наследуемым (для подклассов/dynamic_proxy из плагина)."""
        return _Plugins.makeClassInheritable(str(class_name))

    def allocate_instance(self, class_name):
        """Создать экземпляр класса БЕЗ вызова конструктора."""
        return _Plugins.allocateInstance(str(class_name))

    def is_hooked(self, class_name, method_name, *param_types):
        """Захукан ли сейчас этот метод."""
        pts = jclass("java.util.ArrayList")()
        for t in param_types:
            pts.add(str(t))
        return _Plugins.isMethodHooked(str(class_name), str(method_name), pts)

    def add_on_send_message_hook(self):
        return True

    def on_update_hook(self, update_name, account, update):
        return HookResult()

    def on_send_message_hook(self, account, params):
        return HookResult()

    def before_hook(self, frame):
        """До оригинала. frame.args — аргументы (можно менять: frame.args[0]=...),
        frame.thisObject — объект, frame.setResult(x) — заменить результат и ПРОПУСТИТЬ оригинал,
        frame.method — какой метод сработал."""
        pass

    def after_hook(self, frame):
        """После оригинала. frame.getResult() / frame.setResult(x) — прочитать/подменить результат."""
        pass

    # ---- визуальные эффекты (ui_effects) ----
    @staticmethod
    def _i32(color):
        """ARGB -> знаковый java int (0xFF.. не влезает в signed int)."""
        color = int(color)
        return color - 0x100000000 if color >= 0x80000000 else color

    @staticmethod
    def _api():
        try:
            return jclass("android.os.Build$VERSION").SDK_INT
        except Exception:
            return 0

    # --- Жидкое стекло (Liquid Glass) — настоящий backdrop-blur, работает на всех Android ---

    def glass(self, view, corner=22, blur=18, tint=0x26FFFFFF, border=0.6):
        """Обернуть СУЩЕСТВУЮЩУЮ вью в жидкое стекло (размывается фон ПОЗАДИ неё).
        view вынимается из родителя и кладётся внутрь стеклянной панели на то же место.
        corner — скругление (dp), blur — сила размытия (dp), tint — тонировка ARGB
        (напр. 0x26FFFFFF), border — яркость светящейся кромки 0..1.
        Возвращает стеклянную панель (в неё же можно добавлять контент) или None.
        Обычно: g = self.glass(frame.thisObject)  # напр. поле ввода / шапку чата."""
        try:
            return _Plugins.glassWrap(view, int(corner), int(blur), self._i32(tint), float(border))
        except Exception as e:
            self.log("glass: " + str(e))
            return None

    def glass_panel(self, context_view=None, corner=22, blur=18, tint=0x26FFFFFF, border=0.6):
        """Создать НОВУЮ пустую стеклянную панель-контейнер (FrameLayout). Добавь её куда нужно
        через add_view(...) и клади внутрь свой контент. context_view — любая вью для контекста
        и корня размытия (обычно frame.thisObject). Возвращает панель или None."""
        try:
            return _Plugins.glassPanel(context_view, int(corner), int(blur), self._i32(tint), float(border))
        except Exception as e:
            self.log("glass_panel: " + str(e))
            return None

    def add_view(self, parent, child, width=-1, height=-1, left=0, top=0, right=0, bottom=0, gravity=0):
        """Добавить child во ViewGroup parent. width/height в dp (или -1=на весь размер, -2=по контенту),
        отступы left/top/right/bottom в dp, gravity как android Gravity (17 = центр, 80 = снизу, 48 = сверху)."""
        try:
            _Plugins.addViewFrame(parent, child, int(width), int(height),
                                  int(left), int(top), int(right), int(bottom), int(gravity))
            return True
        except Exception as e:
            self.log("add_view: " + str(e))
            return False

    def remove_view(self, view):
        """Убрать вью из её родителя."""
        try:
            _Plugins.removeView(view)
            return True
        except Exception:
            return False

    def dp(self, value):
        """Перевести dp в пиксели (для расчётов размеров)."""
        return _Plugins.dp(float(value))

    @staticmethod
    def rgba(r, g, b, a=255):
        """Собрать ARGB-цвет из компонент 0..255 (удобно для tint)."""
        return ((int(a) & 0xFF) << 24) | ((int(r) & 0xFF) << 16) | ((int(g) & 0xFF) << 8) | (int(b) & 0xFF)

    def blur(self, view, radius=40.0):
        """Заморозить СОБСТВЕННЫЙ контент View (не фон!). Android 12+ (API 31). True при успехе.
        Для стеклянной панели с размытием ФОНА используй glass()/glass_panel().
        Обычно зовётся из before_hook/after_hook, где view = frame.thisObject."""
        try:
            if self._api() < 31 or view is None:
                return False
            RE = jclass("android.graphics.RenderEffect")
            Shader = jclass("android.graphics.Shader")
            view.setRenderEffect(RE.createBlurEffect(float(radius), float(radius), Shader.TileMode.CLAMP))
            return True
        except Exception as e:
            self.log("blur: " + str(e))
            return False

    def unblur(self, view):
        """Снять размытие с View."""
        try:
            view.setRenderEffect(None)
            return True
        except Exception:
            return False

    def tint(self, view, argb):
        """Полупрозрачная заливка фона View (ARGB, напр. 0x55101018 — стеклянная тонировка)."""
        try:
            if view is None:
                return False
            view.setBackgroundColor(self._i32(argb))
            return True
        except Exception as e:
            self.log("tint: " + str(e))
            return False

    def round_corners(self, view, radius_dp=18):
        """Скруглить углы View (обрезка по контуру)."""
        try:
            if view is None:
                return False
            _Plugins.roundView(view, int(radius_dp))
            return True
        except Exception as e:
            self.log("round: " + str(e))
            return False

    # ---- утилиты ----
    def log(self, msg):
        _FileLog.d("[DevGramPlugin:%s] %s" % (self.id, msg))

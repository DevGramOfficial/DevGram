# Экстеро-значки — значок exteraGram отдельным РЯДОМ в профиле DevGram
# (как телефон/юзернейм/ID), а не плашкой.
#
# Как это работает (через полный хук-API DevGram):
#   • ProfileActivity.onResume → берём id юзера/чата, спрашиваем API exteraGram:
#     GET https://api.exteragram.app/api/v1/profiles/{id};
#   • если есть значок — ВСТРАИВАЕМ ряд в систему списка профиля:
#       — хук ProfileActivity.updateRowsIds (after): увеличиваем rowCount на 1
#         (наш ряд = последний). Так и getItemCount, и DiffUtil (updateListAnimated
#         строит diff от rowCount) видят ряд СОГЛАСОВАННО → нет 'Inconsistency detected';
#       — хуки адаптера getItemViewType/onBindViewHolder/isEnabled: позиция rowCount-1
#         это наш ряд (TextDetailCell «exteraGram / статус»);
#   • тап (processOnClickOrPress) → буллетин с прем-эмодзи значка + ПОЛНЫЙ текст
#     (badge.text, а если пусто — «Имя — спонсор/разработчик exteraGram»), как у экстеры.
#
# Ключевое: rowCount меняем ТОЛЬКО когда активны, и активируемся на UI-потоке.
# Ничего не пересобираем — это .dgplugin, ставится в рантайме.

import json

from java import jclass
from devgram import BasePlugin

_AnimatedEmoji = jclass("org.telegram.ui.Components.AnimatedEmojiDrawable")
_BulletinFactory = jclass("org.telegram.ui.Components.BulletinFactory")
_Long = jclass("java.lang.Long")

_API = "https://api.exteragram.app/api/v1/profiles/"
_ADAPTER = "org.telegram.ui.ProfileActivity$ListAdapter"
_PROFILE = "org.telegram.ui.ProfileActivity"
_VH = "androidx.recyclerview.widget.RecyclerView$ViewHolder"
_VIEW_TYPE_TEXT_DETAIL = 2  # ряд как телефон/ID (TextDetailCell)

_field_cache = {}


class ExteraBadges(BasePlugin):
    id = "extera.badges"
    name = "Экстеро-значки"
    version = "1.0.4"
    author = "@DevGram"
    description = "Значок exteraGram отдельным рядом в профиле (как телефон/юзернейм), тап — полный текст."
    min_app_version = "12.9"

    # ----------------------------------------------------------------- lifecycle
    def on_load(self):
        self._cache = {}    # peer_id -> data(dict с badge) | None (пишется из фона)
        self._active = {}   # peer_id -> True (СТАВИТСЯ ТОЛЬКО НА UI-ПОТОКЕ)
        self.hook(_PROFILE, "onResume", after=self._on_resume)
        # КЛЮЧЕВОЕ: rowCount включает наш ряд → getItemCount и DiffUtil согласованы
        self.hook(_PROFILE, "updateRowsIds", after=self._on_update_rows)
        # DiffUtil: регистрируем наш ряд в fillPositions с уникальным id, иначе
        # areItemsTheSame вернёт false (get даёт -1) → фантомный remove/add →
        # кастомный item-animator профиля крутит scaleX на null-view → вылет.
        self.hook("org.telegram.ui.ProfileActivity$DiffCallback",
                  "fillPositions", "android.util.SparseIntArray", after=self._h_fill)
        # адаптер: наш ряд = последняя позиция (rowCount-1)
        self.hook(_ADAPTER, "getItemViewType", "int", before=self._h_view_type)
        self.hook(_ADAPTER, "onBindViewHolder", _VH, "int", before=self._h_bind)
        self.hook(_ADAPTER, "isEnabled", _VH, before=self._h_is_enabled)
        # тап по ряду
        self.hook(_PROFILE, "processOnClickOrPress",
                  "int", "android.view.View", "float", "float", before=self._h_click)
        self.log("Экстеро-значки загружены (ряд в профиле)")

    def on_unload(self):
        self.unhook_all()
        self._active.clear()
        self.log("Экстеро-значки выгружены")

    # ------------------------------------------------------------------- reflection
    @staticmethod
    def _ii(v):
        try:
            return int(v)
        except Exception:
            try:
                return int(v.intValue())
            except Exception:
                return -1

    @staticmethod
    def _field(obj, name):
        cls = obj.getClass()
        key = (cls.getName(), name)
        f = _field_cache.get(key)
        if f is not None:
            return f
        c = cls
        while c is not None:
            try:
                f = c.getDeclaredField(name)
                f.setAccessible(True)
                _field_cache[key] = f
                return f
            except Exception:
                try:
                    c = c.getSuperclass()
                except Exception:
                    return None
        return None

    def _int(self, obj, name, default=-1):
        f = self._field(obj, name)
        try:
            return f.getInt(obj) if f is not None else default
        except Exception:
            return default

    def _set_int(self, obj, name, value):
        f = self._field(obj, name)
        try:
            if f is not None:
                f.setInt(obj, int(value))
                return True
        except Exception:
            pass
        return False

    def _long(self, obj, name):
        f = self._field(obj, name)
        try:
            return f.getLong(obj) if f is not None else 0
        except Exception:
            return 0

    def _obj(self, obj, name):
        f = self._field(obj, name)
        try:
            return f.get(obj) if f is not None else None
        except Exception:
            return None

    def _call_void(self, obj, name):
        try:
            m = obj.getClass().getDeclaredMethod(name)
            m.setAccessible(True)
            m.invoke(obj)
            return True
        except Exception as e:
            self.log("call %s: %s" % (name, e))
            return False

    # ------------------------------------------------------------------- helpers
    def _peer(self, profile):
        uid = self._long(profile, "userId")
        if uid != 0:
            return uid
        return self._long(profile, "chatId")

    def _is_active(self, profile):
        try:
            return bool(self._active.get(self._peer(profile)))
        except Exception:
            return False

    def _insert_pos(self, profile):
        """Позиция нашего ряда = последняя (rowCount-1). -1 если не активны."""
        if not self._is_active(profile):
            return -1
        rc = self._int(profile, "rowCount", -1)
        return rc - 1 if rc > 0 else -1

    def _profile_of_adapter(self, adapter):
        return self._obj(adapter, "this$0")

    def _data(self, profile):
        try:
            return self._cache.get(self._peer(profile))
        except Exception:
            return None

    # ------------------------------------------------------------------- network
    def _fetch(self, peer_id):
        if peer_id in self._cache:
            return self._cache[peer_id]
        data = None
        conn = None
        try:
            url = jclass("java.net.URL")(_API + str(peer_id))
            conn = url.openConnection()
            conn.setRequestProperty("User-Agent", "exteraGram")
            conn.setRequestProperty("Accept", "application/json")
            conn.setConnectTimeout(10000)
            conn.setReadTimeout(10000)
            if conn.getResponseCode() == 200:
                isr = jclass("java.io.InputStreamReader")(conn.getInputStream(), "UTF-8")
                br = jclass("java.io.BufferedReader")(isr)
                sb = jclass("java.lang.StringBuilder")()
                line = br.readLine()
                while line is not None:
                    sb.append(line)
                    line = br.readLine()
                br.close()
                obj = json.loads(str(sb.toString()))
                if isinstance(obj, dict) and obj.get("badge"):
                    data = obj
        except Exception as e:
            self.log("fetch %s: %s" % (peer_id, e))
        finally:
            try:
                if conn is not None:
                    conn.disconnect()
            except Exception:
                pass
        self._cache[peer_id] = data
        return data

    # ------------------------------------------------------------------- hooks
    def _on_resume(self, frame):
        try:
            profile = frame.thisObject
            if profile is None:
                return
            peer = self._peer(profile)
            if peer == 0:
                return
            if peer in self._cache:
                if self._cache[peer]:
                    self.run_on_ui(lambda: self._activate(profile, peer))
                return

            def work():
                if self._fetch(peer):
                    self.run_on_ui(lambda: self._activate(profile, peer))
            self.run_on_queue(work)
        except Exception as e:
            self.log("on_resume: " + str(e))

    def _activate(self, profile, peer):
        """UI-поток: включаем ряд и пересобираем список (rowCount++ через updateRowsIds)."""
        try:
            self._active[peer] = True
            listView = self._obj(profile, "listView")
            if listView is not None:
                try:
                    if listView.isComputingLayout():
                        self.run_on_ui(lambda: self._activate_now(profile), 50)
                        return
                except Exception:
                    pass
            self._activate_now(profile)
        except Exception as e:
            self.log("activate: " + str(e))

    def _activate_now(self, profile):
        try:
            # updateRowsIds пересчитает ряды, наш after-хук увеличит rowCount на 1
            self._call_void(profile, "updateRowsIds")
            adapter = self._obj(profile, "listAdapter")
            if adapter is not None:
                adapter.notifyDataSetChanged()
        except Exception as e:
            self.log("activate_now: " + str(e))

    def _on_update_rows(self, frame):
        # after: когда активны — включаем наш ряд в счётчик (rowCount += 1).
        # Так getItemCount и DiffUtil (updateListAnimated) остаются согласованными.
        try:
            profile = frame.thisObject
            if self._is_active(profile):
                rc = self._int(profile, "rowCount", -1)
                if rc >= 0:
                    self._set_int(profile, "rowCount", rc + 1)
        except Exception:
            pass

    _ROW_ID = 900001  # уникальный id нашего ряда для DiffUtil (не пересекается с pointer'ами)

    def _h_fill(self, frame):
        # after: добавить наш ряд (позиция rowCount-1) в карту позиций DiffUtil,
        # чтобы он считался стабильным существующим элементом (без add/remove-анимации).
        try:
            profile = self._obj(frame.thisObject, "this$0")
            if not self._is_active(profile):
                return
            rc = self._int(profile, "rowCount", -1)
            if rc > 0:
                frame.args[0].put(rc - 1, self._ROW_ID)
        except Exception:
            pass

    def _h_view_type(self, frame):
        try:
            profile = self._profile_of_adapter(frame.thisObject)
            ins = self._insert_pos(profile)
            if ins >= 0 and self._ii(frame.args[0]) == ins:
                frame.setResult(_VIEW_TYPE_TEXT_DETAIL)
        except Exception:
            pass

    def _h_bind(self, frame):
        try:
            profile = self._profile_of_adapter(frame.thisObject)
            ins = self._insert_pos(profile)
            if ins >= 0 and self._ii(frame.args[1]) == ins:
                holder = frame.args[0]
                self._bind_row(profile, holder.itemView)
                frame.setResult(None)  # пропустить оригинал (void)
        except Exception:
            pass

    def _h_is_enabled(self, frame):
        try:
            profile = self._profile_of_adapter(frame.thisObject)
            ins = self._insert_pos(profile)
            if ins >= 0 and self._ii(frame.args[0].getAdapterPosition()) == ins:
                frame.setResult(True)
        except Exception:
            pass

    def _h_click(self, frame):
        try:
            profile = frame.thisObject
            ins = self._insert_pos(profile)
            if ins >= 0 and self._ii(frame.args[0]) == ins:
                self._show_bulletin(profile)
                frame.setResult(True)
        except Exception:
            pass

    # ------------------------------------------------------------------- UI
    def _status_short(self, data):
        s = (data or {}).get("status")
        if s == "DEVELOPER":
            return "Разработчик"
        if s == "SUPPORTER":
            return "Спонсор"
        return "Значок"

    def _bind_row(self, profile, cell):
        try:
            cell.setTextAndValue("exteraGram", self._status_short(self._data(profile)), False)
        except Exception as e:
            self.log("bind_row: " + str(e))

    def _peer_name(self, profile):
        try:
            mc = profile.getMessagesController()
            uid = self._long(profile, "userId")
            if uid != 0:
                user = mc.getUser(_Long(uid))
                return str(user.first_name) if user is not None and user.first_name else ""
            cid = self._long(profile, "chatId")
            if cid != 0:
                chat = mc.getChat(_Long(cid))
                return str(chat.title) if chat is not None and chat.title else ""
        except Exception:
            pass
        return ""

    def _full_text(self, profile, data):
        badge = (data or {}).get("badge") or {}
        t = badge.get("text")
        if t:
            return str(t).replace("**", "").strip()
        name = self._peer_name(profile)
        s = (data or {}).get("status")
        role = "разработчик exteraGram" if s == "DEVELOPER" else \
               "спонсор exteraGram" if s == "SUPPORTER" else "значок exteraGram"
        if name:
            return "%s — %s" % (name, role)
        return role[0].upper() + role[1:]

    def _show_bulletin(self, profile):
        try:
            data = self._data(profile)
            if not data:
                return
            doc_id = int(((data.get("badge") or {}).get("documentId")) or 0)
            if doc_id == 0:
                return
            text = self._full_text(profile, data)
            account = int(profile.getCurrentAccount())
            factory = _BulletinFactory.of(profile)
            doc = None
            try:
                doc = _AnimatedEmoji.findDocument(account, doc_id)
            except Exception:
                doc = None
            if doc is not None:
                factory.createEmojiBulletin(doc, text).show()
            else:
                factory.createEmojiBulletin(doc_id, text, None).show()
        except Exception as e:
            self.log("bulletin: " + str(e))

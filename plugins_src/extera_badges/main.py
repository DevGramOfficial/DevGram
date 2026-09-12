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
_Integer = jclass("java.lang.Integer")

_API = "https://api.exteragram.app/api/v1/profiles/"
_ADAPTER = "org.telegram.ui.ProfileActivity$ListAdapter"
_PROFILE = "org.telegram.ui.ProfileActivity"
_VH = "androidx.recyclerview.widget.RecyclerView$ViewHolder"
_VIEW_TYPE_TEXT_DETAIL = 19  # TextDetailCell МНОГОСТРОЧНЫЙ (MULTILINE) — текст значка не обрезается
_CACHE_TYPE_EMOJI_STATUS = 7  # анимированный прем-эмодзи (как emoji-статус)

_field_cache = {}


class ExteraBadges(BasePlugin):
    id = "extera.badges"
    name = "Экстеро-значки"
    version = "1.1.5"
    author = "@DevGramPlugins"
    description = "Значок exteraGram отдельным рядом в профиле (как телефон/юзернейм), тап — полный текст."
    min_app_version = "12.10.3"

    # ----------------------------------------------------------------- lifecycle
    def on_load(self):
        self._cache = {}    # peer_id -> data(dict с badge) | None (пишется из фона)
        self._active = {}   # peer_id -> True (СТАВИТСЯ ТОЛЬКО НА UI-ПОТОКЕ)
        self._pos = {}      # peer_id -> позиция нашего ряда (перед bioRow «о себе»)
        self._row_fields = None  # кэш int-полей *Row (для сдвига при mid-insert)
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
        """Позиция нашего ряда (перед bioRow «о себе»), выставленная в _on_update_rows.
        -1 если не активны."""
        if not self._is_active(profile):
            return -1
        try:
            return self._pos.get(self._peer(profile), -1)
        except Exception:
            return -1

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

    def _row_field_list(self, profile):
        """int-поля ProfileActivity с именем *Row (позиции рядов) — для сдвига. Кэш."""
        if self._row_fields is not None:
            return self._row_fields
        fields = []
        try:
            cls = profile.getClass()
            for f in cls.getDeclaredFields():
                name = f.getName()
                if name.endswith("Row") and f.getType().getName() == "int":
                    f.setAccessible(True)
                    fields.append(f)
        except Exception as e:
            self.log("row_field_list: " + str(e))
        self._row_fields = fields
        return fields

    def _on_update_rows(self, frame):
        # after: вставляем наш ряд ПЕРЕД bioRow («о себе»). Сдвигаем все ряды на позиции
        # >= точки вставки на +1, бампаем rowCount. Так ряд оказывается над «о себе»,
        # а getItemCount/DiffUtil остаются согласованными.
        try:
            profile = frame.thisObject
            peer = self._peer(profile)
            if not self._active.get(peer):
                self._pos.pop(peer, None)
                return
            rc = self._int(profile, "rowCount", -1)
            if rc < 0:
                return
            # Якорь «о себе»: bioRow / userInfoRow / channelInfoRow. Если нет —
            # ставим перед разделителем инфо-секции (не в самый низ), иначе в конец.
            bio = self._int(profile, "bioRow", -1)
            uinfo = self._int(profile, "userInfoRow", -1)
            cinfo = self._int(profile, "channelInfoRow", -1)
            sect = self._int(profile, "infoSectionRow", -1)
            anchor = -1
            for cand in (bio, uinfo, cinfo, sect):
                if cand >= 0:
                    anchor = cand
                    break
            insert = anchor if anchor >= 0 else rc
            self.log("update_rows: bio=%d uinfo=%d cinfo=%d sect=%d rc=%d → insert=%d"
                     % (bio, uinfo, cinfo, sect, rc, insert))
            if insert < rc:  # mid-insert: сдвигаем ряды на позиции >= insert
                for f in self._row_field_list(profile):
                    try:
                        val = f.getInt(profile)
                        if val >= insert:      # (-1 не сдвинется: insert >= 0)
                            f.setInt(profile, val + 1)
                    except Exception:
                        pass
            self._set_int(profile, "rowCount", rc + 1)
            self._pos[peer] = insert
        except Exception as e:
            self.log("on_update_rows: " + str(e))

    _ROW_ID = 900001  # уникальный id нашего ряда для DiffUtil (не пересекается с pointer'ами)

    def _h_fill(self, frame):
        # after: регистрируем наш ряд в карте позиций DiffUtil (по нашей позиции перед
        # bioRow), чтобы он считался стабильным элементом — без фантомной add/remove-анимации.
        try:
            profile = self._obj(frame.thisObject, "this$0")
            pos = self._insert_pos(profile)
            if pos >= 0:
                frame.args[0].put(pos, self._ROW_ID)
        except Exception:
            pass

    def _h_view_type(self, frame):
        try:
            profile = self._profile_of_adapter(frame.thisObject)
            ins = self._insert_pos(profile)
            if ins >= 0 and self._ii(frame.args[0]) == ins:
                # ВАЖНО: getItemViewType возвращает int — ставим ЯВНЫЙ java Integer,
                # иначе Chaquopy боксит python-int как Long → ClassCastException при скролле.
                frame.setResult(_Integer(_VIEW_TYPE_TEXT_DETAIL))
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

    _EMOJI_TAG = "dg_extera_badge_emoji"

    def _build_emoji_view(self, ctx, emoji, size_px):
        """View, который сам рисует анимированный прем-эмодзи значка (надёжно, в отличие
        от setImage в ImageView — тот AnimatedEmojiDrawable не грузит/не анимирует)."""
        class _EmojiLogic:
            def onAttachedToWindow(self, this):
                BasePlugin.java_super()
                try:
                    emoji.addView(this)   # перерисовка при загрузке/анимации
                except Exception:
                    pass

            def onDetachedFromWindow(self, this):
                try:
                    emoji.removeView(this)
                except Exception:
                    pass
                BasePlugin.java_super()

            def onDraw(self, this, canvas):
                try:
                    emoji.setBounds(0, 0, int(this.getWidth()), int(this.getHeight()))
                    emoji.draw(canvas)
                except Exception:
                    pass

        view = self.java_class("android.view.View", _EmojiLogic(),
                               arg_types=["android.content.Context"], args=[ctx])
        if view is not None:
            try:
                view.setWillNotDraw(False)
            except Exception:
                pass
        return view

    def _bind_row(self, profile, cell):
        try:
            data = self._data(profile) or {}
            # В ряду сразу выводим ТОТ ЖЕ текст, что exteraGram показывает по нажатию на значок.
            cell.setTextAndValue("exteraGram", self._full_text(profile, data), False)
            # убрать значок от прошлой привязки этой же ячейки (RecyclerView переиспользует cell)
            try:
                old = cell.findViewWithTag(self._EMOJI_TAG)
                if old is not None:
                    cell.removeView(old)
            except Exception:
                pass
            # Иконка значка (прем-эмодзи badge.documentId) справа в ряду.
            doc_id = int((data.get("badge") or {}).get("documentId") or 0)
            if doc_id:
                account = int(profile.getCurrentAccount())
                emoji = _AnimatedEmoji.make(account, _CACHE_TYPE_EMOJI_STATUS, doc_id)
                # КРАСИМ в акцентный цвет темы: монохромные прем-эмодзи по умолчанию чёрные
                # и не видны в тёмной теме (как emoji-статусы в клиенте — setColorFilter).
                try:
                    Theme = jclass("org.telegram.ui.ActionBar.Theme")
                    PorterDuff = jclass("android.graphics.PorterDuff")
                    PDCF = jclass("android.graphics.PorterDuffColorFilter")
                    color = int(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon))
                    emoji.setColorFilter(PDCF(color, PorterDuff.Mode.SRC_IN))
                except Exception as e:
                    self.log("emoji color: " + str(e))
                ev = self._build_emoji_view(cell.getContext(), emoji, self.dp(24))
                if ev is not None:
                    ev.setTag(self._EMOJI_TAG)
                    # 21 = Gravity.RIGHT | CENTER_VERTICAL
                    self.add_view(cell, ev, width=24, height=24, right=16, gravity=21)
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
        # Тот же текст, что выводит exteraGram: если у значка есть свой текст (badge.text)
        # — он; иначе точные их формулировки (строки Supporter/Developer из их ресурсов,
        # английские — у exteraGram нет русской локали, показывают английский).
        badge = (data or {}).get("badge") or {}
        t = badge.get("text")
        if t:
            return str(t).replace("**", "").strip()
        name = self._peer_name(profile) or "Пользователь"
        s = (data or {}).get("status")
        if s == "DEVELOPER":
            return "%s — участник команды разработки exteraGram." % name
        if s == "SUPPORTER":
            return "%s поддержал(а) разработку exteraGram и получил(а) уникальный значок." % name
        return "У %s есть значок exteraGram." % name

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

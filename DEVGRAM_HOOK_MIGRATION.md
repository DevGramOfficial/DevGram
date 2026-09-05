# DevGram: миграция движка хуков Pine → AliuHook/LSPlant (как у exteraGram)

Цель: полноценная система хуков/плагинов уровня exteraGram — надёжная установка хуков на всех Android/устройствах, хук инлайненных методов, method-replacement — **без переписывания существующих `.dgplugin`** (сохраняем plugin-facing API 1:1).

## Что мы меняем и что НЕТ
- **Меняем:** внутренний слой установки хуков `Pine` → **AliuHook** (Java-реализация Xposed API поверх **LSPlant** + Dobby; то же, что у exteraGram).
- **НЕ меняем:** Python-API для плагинов (`hook`/`after_hook`/`on_send_request`/`on_receive_response`/`on_update`/`jclass`/`dynamic_proxy`, доступ `frame.args`/`frame.thisObject`/`frame.result`). `jclass`/`dynamic_proxy` остаются на Chaquopy `java` + dexmaker — AliuHook их НЕ заменяет (он только про хук методов).

## Как устроено СЕЙЧАС у нас (Pine) — что трогаем
Файл `TMessagesProj/src/main/java/org/telegram/messenger/DevGramPlugins.java`:
- `initHooks()` (~1513) → `Pine.ensureInitialized()`.
- `hook(pluginId, className, methodName, paramTypes)` (~1528) → резолв `Member` → `Pine.hook(member, MethodHook{ beforeCall/afterCall → dispatchHook(pid, phase, frame) })`.
- `dispatchHook(pid, phase, frame)` (~1925) → Python `dispatch_hook(pluginId, phase, frame)` + `coerceArgs`/`coerceResult` (фикс Chaquopy Long→int).
- Доступ к `Pine.CallFrame`: `cf.args`, `cf.thisObject`, `cf.getResult()`, `cf.setResult()`, `cf.method`.
- Request-хуки (`installRequestHooks`, ~1585): Pine-хук `ConnectionsManager.sendRequest(TLObject, RequestDelegate)` → `dispatch_request` + Proxy-обёртка делегата → `dispatch_response`.
- Прочие event-хуки (on_send_message, on_update, menu_items) — тоже через Pine.
- Зависимости (`TMessagesProj/build.gradle:38-42`): `top.canyie.pine:core:0.3.0`, `com.linkedin.dexmaker:dexmaker:2.28.3`. Нативка `libpine.so`.
- Python-мост: `TMessagesProj/src/main/python/devgram_plugins.py:423` (`"""Мост Pine-хука в плагин"""`) — `dispatch_hook`, читает `frame.args`/`frame.result`.

## Как у exteraGram (цель) — образец
Декомпил на сервере: `/tmp/claude-1000/-home/.../scratchpad/extera_src/sources`.
- Xposed API (= AliuHook): `de/robv/android/xposed/{XposedBridge,XC_MethodHook,XC_MethodReplacement,HookCallbackDispatcher}.java`.
- Мост в Python: `com/exteragram/messenger/plugins/xposed/PyMethodHook.java` — `class PyMethodHook extends XC_MethodHook`, поля `pluginId` + `PyObject pythonCallback`, `beforeHook`/`afterHook` (питон-callable, достаются `getCallbackIfPresent`), override `beforeHookedMethod(param)`/`afterHookedMethod(param)` → зовут питон; поддержка `priority`, `HookFilter` (before/after), disable/close. `PyMethodReplacement` — полная подмена метода.
- Контроллер: `com/exteragram/messenger/plugins/PluginsController.java` — `addXposedHook(pluginId, Unhook)`/`addXposedHooks(...)`, хранит `XposedHookRecord`(Unhook) в `ConcurrentHashMap<pluginId, Set<HookRecord>>`, `removeHooksByPluginId` при выгрузке; event-хуки `executePreRequestHook/executePostRequestHook/executeUpdateHook/executeUpdatesHook/executeSendMessageHook`.
- `HookRecord`/`EventHookRecord`/`XposedHookRecord`/`HookFilter` — модель хуков.

## Маппинг Pine → Xposed (ЧИСТЫЙ, поэтому плагины не трогаем)
| Pine | Xposed/AliuHook |
|---|---|
| `Pine.ensureInitialized()` | `AliuHook.init()` (LSPlant + hidden-API bypass) |
| `Pine.hook(member, MethodHook)` → `Unhook`? | `XposedBridge.hookMethod(member, XC_MethodHook)` → `XC_MethodHook.Unhook` |
| `MethodHook.beforeCall(frame)` | `XC_MethodHook.beforeHookedMethod(param)` |
| `MethodHook.afterCall(frame)` | `XC_MethodHook.afterHookedMethod(param)` |
| `frame.args` | `param.args` |
| `frame.thisObject` | `param.thisObject` |
| `frame.getResult()/setResult()` | `param.getResult()/setResult()` |
| `frame.method` | `param.method` |
| скип оригинала (result в before) | set result/throwable в `beforeHookedMethod` → оригинал не вызовется |
| — (нет) | `XC_MethodReplacement` — полная подмена (НОВОЕ) |

## Шаги (по фазам)
**Фаза 0 — подготовка зависимости.**
- Подключить AliuHook: maven Aliucord (`maven { url 'https://maven.aliucord.com/snapshots' }`) + `implementation 'com.aliucord:Aliuhook:<ver>'` (уточнить артефакт/версию по maven.aliucord.com или jitpack `com.github.Aliucord:AliuHook`). Тянет `de.robv.android.xposed.*` + нативку `libaliuhook.so` + LSPlant. Убрать `top.canyie.pine:core` (или оставить временно за флагом на время миграции).
- Сверить, что нативка кладётся для arm64-v8a + armeabi-v7a.

**Фаза 1 — адаптер движка (без смены Python-API).**
- Новый класс-обёртка `DevGramHookEngine` (или прямо в DevGramPlugins): 
  - `init()` = `AliuHook.init()` вместо `Pine.ensureInitialized()`.
  - `hook(member, pid)` = `XposedBridge.hookMethod(member, new XC_MethodHook(){ before/after → dispatchHook(pid, phase, param) })`, вернуть `Unhook`.
- В `dispatchHook`/Python-мосте заменить работу с `Pine.CallFrame` на `XC_MethodHook.MethodHookParam`. `param.args`/`param.thisObject` — поля; `getResult()/setResult()` — методы. Подправить `devgram_plugins.py:423` мост, если питон читал `frame.result` как поле → сделать через `getResult()` (или дать Java-обёртку `frame` с теми же геттерами, чтобы питон вообще не трогать — ПРЕДПОЧТИТЕЛЬНО: класс `DGCallFrame` с полями `args/thisObject/result`, синхронизируемый с param — тогда plugin API 1:1 без правок питона).
- `coerceArgs/coerceResult` — оставить (Chaquopy Long→int актуально и здесь): применять к `param.args` в before и `param.getResult()` в after.

**Фаза 2 — учёт/очистка хуков на плагин.**
- Хранить `XC_MethodHook.Unhook` per pluginId (аналог `XposedHookRecord`): `Map<pluginId, List<Unhook>>`. На reload/выгрузку/disable — `unhook.unhook()` для всех (сейчас Pine-хуки просто копятся; проверить, есть ли у нас снятие).

**Фаза 3 — перевести спец-хуки на новый движок.**
- `installRequestHooks` (sendRequest), on_send_message, on_update, menu-хуки — переписать `Pine.hook` → `XposedBridge.hookMethod`. Логика dispatch_request/response + Proxy-делегат остаётся (Proxy не зависит от движка).

**Фаза 4 — новые возможности (то, ради чего затевали).**
- `XC_MethodReplacement` → дать плагинам `replace_hook`/полную подмену метода (у Pine кривовато).
- Хук инлайненных методов — AliuHook/LSPlant деоптимизирует (проверить, что profile-saver отключён — у AliuHook есть утилита).
- Хук всех конструкторов/`hookAllMethods` — если нужно.

**Фаза 5 — тесты и выкатка.**
- Прогнать реальные плагины (media downloader, аним-обои, панель) — before/after/скип-оригинала/подмена результата.
- Проверить на нескольких Android (12–15) и устройствах, где Pine раньше промахивался.
- Собрать локально (`assembleAfatRelease`), залить в релиз v12.9.7 (см. [[feedback_devgram_delivery]] — только локально, не пушить).

## Риски / откат
- LSPlant/AliuHook инициализируется раньше загрузки плагинов (как `initHooks()` сейчас). Порядок: init движка → загрузка плагинов (on_load может хукать).
- Держать флаг `USE_ALIUHOOK` или ветку — чтобы можно было временно откатиться на Pine, пока не проверим все плагины.
- `jclass`/`dynamic_proxy`/dexmaker — НЕ трогать (не связано с движком хука).
- Safe mode: как и сейчас — при safe mode движок не инициализируем / хуки не ставим.

## Что получаем
- Надёжная установка хуков «у всех» (шире покрытие Android/устройств, меньше промахов/крашей).
- Хук инлайненных/оптимизированных методов TG.
- Полноценная method-replacement.
- API плагинов не меняется → существующие `.dgplugin` работают без правок.

package org.telegram.messenger;

import com.chaquo.python.PyObject;

import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.pillstack.PillStackConfig;
import org.telegram.ui.pillstack.pills.PluginPill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DevGram: тонкий фасад над {@link PillStackConfig} — сохраняет API моста Python-плагинов
 * (регистрация пилюль виджетов в строке поиска), которым пользуется {@link DevGramPlugins}.
 */
public final class DevGramPillStack {

    private DevGramPillStack() { }

    private static final Object lock = new Object();
    private static final Map<String, ArrayList<Integer>> pluginPills = new HashMap<>();
    private static final Map<Integer, CallbackRefs> pillCallbacks = new HashMap<>();

    private static final class CallbackRefs {
        final PyObject value;
        final PyObject click;
        final PyObject longClick;

        CallbackRefs(PyObject value, PyObject click, PyObject longClick) {
            this.value = value;
            this.click = click;
            this.longClick = longClick;
        }

        void closeExcept(CallbackRefs keep) {
            Set<PyObject> closed = Collections.newSetFromMap(new IdentityHashMap<>());
            for (PyObject callback : new PyObject[]{value, click, longClick}) {
                if (callback == null || (keep != null && (callback == keep.value
                        || callback == keep.click || callback == keep.longClick)) || !closed.add(callback)) {
                    continue;
                }
                try {
                    callback.close();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    /** Мост Python: колбэки могут быть null; value-колбэк возвращает текущий текст пилюли. */
    public static int registerPluginPill(String pluginId, String pillId, String name, String text,
                                         PyObject valueCallback, PyObject clickCallback, PyObject longClickCallback,
                                         boolean enabled) {
        if (pluginId == null || pillId == null) {
            new CallbackRefs(valueCallback, clickCallback, longClickCallback).closeExcept(null);
            return 0;
        }
        final String key = pluginId + ":" + pillId;
        final int id = 1000000 + (key.hashCode() & 0x3fffffff);
        final String pillName = (name == null || name.isEmpty()) ? pillId : name;

        final PluginPill.ValueProvider valueProvider = valueCallback == null ? null : () -> {
            long token = DevGramPluginWatchdog.onExecutionStarted(pluginId, pillName, "");
            try (PyObject r = valueCallback.call()) { return r == null ? null : r.toString(); }
            catch (Throwable e) { FileLog.e(e); return null; }
            finally { DevGramPluginWatchdog.onExecutionFinished(token); }
        };
        final Runnable clickRunnable = clickCallback == null ? null : () -> {
            long token = DevGramPluginWatchdog.onExecutionStarted(pluginId, pillName, "");
            try (PyObject ignored = clickCallback.call()) { } catch (Throwable e) { FileLog.e(e); }
            finally { DevGramPluginWatchdog.onExecutionFinished(token); }
        };
        final Runnable longClickRunnable = longClickCallback == null ? null : () -> {
            long token = DevGramPluginWatchdog.onExecutionStarted(pluginId, pillName, "");
            try (PyObject ignored = longClickCallback.call()) { } catch (Throwable e) { FileLog.e(e); }
            finally { DevGramPluginWatchdog.onExecutionFinished(token); }
        };

        CallbackRefs callbacks = new CallbackRefs(valueCallback, clickCallback, longClickCallback);
        CallbackRefs previous;
        synchronized (lock) {
            previous = pillCallbacks.put(id, callbacks);
            PillStackConfig.register(new PillStackConfig.PillInfo(
                    id, pillName, R.drawable.msg_settings,
                    IconBackgroundColors.GRAY.top, IconBackgroundColors.GRAY.bottom,
                    (context, rp) -> new PluginPill(context, rp, id, text, valueProvider, clickRunnable, longClickRunnable)));

            if (enabled && !PillStackConfig.getActivePills().contains(id)) {
                PillStackConfig.getHiddenPills().remove((Integer) id);
                PillStackConfig.getActivePills().add(id);
                PillStackConfig.savePillsLayout();
            }

            ArrayList<Integer> ids = pluginPills.get(pluginId);
            if (ids == null) { ids = new ArrayList<>(); pluginPills.put(pluginId, ids); }
            if (!ids.contains(id)) ids.add(id);
        }
        if (previous != null) previous.closeExcept(callbacks);
        // плагины регистрируют пилюли из потока загрузки: перестраивать шапку можно только в главном
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged));
        return id;
    }

    public static void unregisterPluginPills(String pluginId) {
        ArrayList<Integer> ids;
        synchronized (lock) { ids = pluginPills.remove(pluginId); }
        if (ids != null) {
            for (int id : ids) {
                CallbackRefs callbacks;
                synchronized (lock) { callbacks = pillCallbacks.remove(id); }
                PillStackConfig.unregister(id);
                if (callbacks != null) callbacks.closeExcept(null);
            }
        }
    }

    /** Удалить колбэки всех Python-плагинов перед полной перезагрузкой runtime. */
    public static void unregisterAllPluginPills() {
        List<String> pluginIds;
        synchronized (lock) { pluginIds = new ArrayList<>(pluginPills.keySet()); }
        for (String pluginId : pluginIds) unregisterPluginPills(pluginId);
    }

    public static List<Integer> getActive() { return PillStackConfig.getActivePills(); }
    public static List<Integer> getHidden() { return PillStackConfig.getHiddenPills(); }
}

package org.telegram.messenger;

import com.chaquo.python.PyObject;

import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.pillstack.PillStackConfig;
import org.telegram.ui.pillstack.pills.PluginPill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DevGram: тонкий фасад над {@link PillStackConfig} — сохраняет API моста Python-плагинов
 * (регистрация пилюль виджетов в строке поиска), которым пользуется {@link DevGramPlugins}.
 */
public final class DevGramPillStack {

    private DevGramPillStack() { }

    private static final Object lock = new Object();
    private static final Map<String, ArrayList<Integer>> pluginPills = new HashMap<>();

    /** Мост Python: колбэки могут быть null; value-колбэк возвращает текущий текст пилюли. */
    public static int registerPluginPill(String pluginId, String pillId, String name, String text,
                                         PyObject valueCallback, PyObject clickCallback, PyObject longClickCallback,
                                         boolean enabled) {
        if (pluginId == null || pillId == null) return 0;
        final String key = pluginId + ":" + pillId;
        final int id = 1000000 + (key.hashCode() & 0x3fffffff);
        final String pillName = (name == null || name.isEmpty()) ? pillId : name;

        final PluginPill.ValueProvider valueProvider = valueCallback == null ? null : () -> {
            try (PyObject r = valueCallback.call()) { return r == null ? null : r.toString(); }
            catch (Throwable e) { FileLog.e(e); return null; }
        };
        final Runnable clickRunnable = clickCallback == null ? null : () -> {
            try (PyObject ignored = clickCallback.call()) { } catch (Throwable e) { FileLog.e(e); }
        };
        final Runnable longClickRunnable = longClickCallback == null ? null : () -> {
            try (PyObject ignored = longClickCallback.call()) { } catch (Throwable e) { FileLog.e(e); }
        };

        synchronized (lock) {
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
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged);
        return id;
    }

    public static void unregisterPluginPills(String pluginId) {
        ArrayList<Integer> ids;
        synchronized (lock) { ids = pluginPills.remove(pluginId); }
        if (ids != null) for (int id : ids) PillStackConfig.unregister(id);
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

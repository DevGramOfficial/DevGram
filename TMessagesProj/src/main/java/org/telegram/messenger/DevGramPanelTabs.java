package org.telegram.messenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.chaquo.python.PyObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DevGram: реестр вкладок плагинов в панели Эмодзи/GIF/Стикеры (EmojiView, TAB_PLUGIN).
 * Один плагин — одна вкладка (повторная регистрация переписывает предыдущую).
 */
public final class DevGramPanelTabs {

    private DevGramPanelTabs() { }

    private static final class Entry {
        final String title;
        final PyObject buildView;
        final PyObject actionClick;
        final String iconPath; // абсолютный путь к PNG (asset_path плагина) или null
        volatile Bitmap iconBitmap;

        Entry(String title, PyObject buildView, PyObject actionClick, String iconPath) {
            this.title = title;
            this.buildView = buildView;
            this.actionClick = actionClick;
            this.iconPath = iconPath;
        }
    }

    private static final Object lock = new Object();
    private static final Map<String, Entry> tabs = new LinkedHashMap<>();

    /** Мост Python: buildView(context, dialogId) -> View; actionClick(anchorView, dialogId) — может быть null. */
    public static void register(String pluginId, String title, PyObject buildView, PyObject actionClick, String iconPath) {
        if (pluginId == null) {
            closeCallbacks(buildView, actionClick);
            return;
        }
        Entry previous;
        synchronized (lock) {
            previous = tabs.put(pluginId,
                    new Entry(title == null ? "" : title, buildView, actionClick, iconPath));
        }
        closeEntry(previous, buildView, actionClick);
        notifyChanged();
    }

    public static void unregister(String pluginId) {
        if (pluginId == null) return;
        Entry removed;
        synchronized (lock) {
            removed = tabs.remove(pluginId);
        }
        closeEntry(removed, null, null);
        if (removed != null) notifyChanged();
    }

    /** Удалить все вкладки плагинов перед пересозданием Python runtime. */
    public static void clear() {
        List<Entry> removed;
        synchronized (lock) {
            removed = new ArrayList<>(tabs.values());
            tabs.clear();
        }
        for (Entry entry : removed) {
            closeEntry(entry, null, null);
        }
        if (!removed.isEmpty()) notifyChanged();
    }

    private static void closeEntry(Entry entry, PyObject keepFirst, PyObject keepSecond) {
        if (entry == null) return;
        entry.iconBitmap = null;
        closeCallbacksExcept(keepFirst, keepSecond, entry.buildView, entry.actionClick);
    }

    private static void closeCallbacks(PyObject... callbacks) {
        closeCallbacksExcept(null, null, callbacks);
    }

    private static void closeCallbacksExcept(PyObject keepFirst, PyObject keepSecond, PyObject... callbacks) {
        Set<PyObject> closed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PyObject callback : callbacks) {
            if (callback == null || callback == keepFirst || callback == keepSecond || !closed.add(callback)) {
                continue;
            }
            try {
                callback.close();
            } catch (Throwable ignore) {
            }
        }
    }

    private static void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance()
                .postNotificationName(NotificationCenter.pluginPanelTabsChanged));
    }

    public static List<String> pluginIds() {
        synchronized (lock) {
            return new ArrayList<>(tabs.keySet());
        }
    }

    public static String title(String pluginId) {
        synchronized (lock) {
            Entry e = tabs.get(pluginId);
            return e == null ? "" : e.title;
        }
    }

    public static View buildView(String pluginId, Context context, long dialogId) {
        PyObject cb;
        synchronized (lock) {
            Entry e = tabs.get(pluginId);
            cb = e == null ? null : e.buildView;
        }
        if (cb == null) return null;
        long token = DevGramPluginWatchdog.onExecutionStarted(pluginId, title(pluginId), "");
        try (PyObject v = cb.call(context, dialogId)) {
            return v == null ? null : v.toJava(View.class);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            DevGramPluginWatchdog.onExecutionFinished(token);
        }
    }

    public static boolean hasAction(String pluginId) {
        synchronized (lock) {
            Entry e = tabs.get(pluginId);
            return e != null && e.actionClick != null;
        }
    }

    public static void triggerAction(String pluginId, View anchor, long dialogId) {
        PyObject cb;
        synchronized (lock) {
            Entry e = tabs.get(pluginId);
            cb = e == null ? null : e.actionClick;
        }
        if (cb == null) return;
        long token = DevGramPluginWatchdog.onExecutionStarted(pluginId, title(pluginId), "");
        try (PyObject ignored = cb.call(anchor, dialogId)) {
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            DevGramPluginWatchdog.onExecutionFinished(token);
        }
    }

    // DevGram: иконка вкладки — простой путь к PNG (self.asset_path(...) на Python-стороне),
    // не колбэк — картинка не зависит от контекста чата и грузится/масштабируется целиком тут,
    // без обратного похода в Python на каждую перестройку EmojiView (та вызывается часто).
    public static Drawable icon(String pluginId, Context context) {
        if (context == null) return null;
        Entry entry;
        synchronized (lock) {
            entry = tabs.get(pluginId);
        }
        String path = entry == null ? null : entry.iconPath;
        if (path == null || path.isEmpty()) return null;
        try {
            Bitmap bitmap = entry.iconBitmap;
            if (bitmap != null && !bitmap.isRecycled()) {
                return new BitmapDrawable(context.getResources(), bitmap);
            }

            int size = AndroidUtilities.dp(24);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap raw = BitmapFactory.decodeFile(path, options);
            if (raw == null) return null;
            Bitmap decoded = raw.getWidth() == size && raw.getHeight() == size
                    ? raw : Bitmap.createScaledBitmap(raw, size, size, true);
            if (decoded != raw) {
                raw.recycle();
            }

            synchronized (lock) {
                if (tabs.get(pluginId) != entry) {
                    decoded.recycle();
                    return null;
                }
                bitmap = entry.iconBitmap;
                if (bitmap == null || bitmap.isRecycled()) {
                    entry.iconBitmap = decoded;
                    bitmap = decoded;
                } else if (bitmap != decoded) {
                    decoded.recycle();
                }
            }
            return new BitmapDrawable(context.getResources(), bitmap);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }
}

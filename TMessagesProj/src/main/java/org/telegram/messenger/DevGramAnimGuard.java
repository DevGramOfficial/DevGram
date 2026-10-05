package org.telegram.messenger;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Property;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

// DevGram: ловушка для самого частого краша — NPE «setScaleX(float) on a null object reference»
// внутри AnimatorSet (62 отчёта на 11 устройствах, все версии с 12.9.3). Где-то анимацию создают
// для view, который ещё/уже null; падает она позже, в Choreographer, и в стеке нет ни одной
// нашей строки — по отчёту место не найти.
// Перехватываем фабрики ObjectAnimator.of…(target, …): при target == null подставляем
// отвязанную заглушку-View (анимация ни на что не влияет, приложение не падает) и один раз
// отправляем в RTDB стек МЕСТА СОЗДАНИЯ — по нему чиним уже по-настоящему.
public final class DevGramAnimGuard {

    private static final String PREFS = "devgram_anim_guard";
    private static volatile boolean installed;
    private static volatile View dummy;

    private DevGramAnimGuard() {
    }

    // Вызывать один раз на старте (ApplicationLoader). Не ставится в безопасном режиме.
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Свой предохранитель от цикла падений: safe_mode плагинов выставляется позже (loadAll в фоне).
        // Если прошлый запуск с ловушкой не прожил 15 с — отключаем её до следующего обновления.
        String version = BuildVars.BUILD_VERSION_STRING;
        if (version.equals(prefs.getString("disabled_version", null))) {
            return;
        }
        if (prefs.getBoolean("boot_pending", false)) {
            prefs.edit().putString("disabled_version", version).putBoolean("boot_pending", false).commit();
            return;
        }
        try {
            if (DevGramPlugins.isSafeMode() || !DevGramPlugins.initHooks()) {
                return;
            }
            prefs.edit().putBoolean("boot_pending", true).commit();
            AndroidUtilities.runOnUIThread(() -> prefs.edit().putBoolean("boot_pending", false).apply(), 15_000);
            de.robv.android.xposed.XC_MethodHook hook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length > 0 && param.args[0] == null) {
                        param.args[0] = dummyTarget();
                        report(param.args.length > 1 ? param.args[1] : null);
                    }
                }
            };
            for (Method m : ObjectAnimator.class.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) && m.getName().startsWith("of")
                        && p.length > 0 && p[0] == Object.class) {
                    de.robv.android.xposed.XposedBridge.hookMethod(m, hook);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e); // хуки недоступны — просто живём без ловушки
        }
    }

    private static View dummyTarget() {
        View v = dummy;
        if (v == null) {
            synchronized (DevGramAnimGuard.class) {
                if (dummy == null) {
                    dummy = new View(ApplicationLoader.applicationContext);
                }
                v = dummy;
            }
        }
        return v;
    }

    private static void report(Object property) {
        try {
            String propertyName = property instanceof Property ? ((Property<?, ?>) property).getName() : String.valueOf(property);
            StackTraceElement[] stack = new Throwable().getStackTrace();
            StringBuilder trace = new StringBuilder();
            StringBuilder key = new StringBuilder(propertyName);
            int appFrames = 0;
            for (StackTraceElement e : stack) {
                String cls = e.getClassName();
                if (cls.startsWith("de.robv.") || cls.startsWith("org.lsposed.") || cls.startsWith("io.github.")
                        || cls.equals(DevGramAnimGuard.class.getName()) || cls.startsWith(DevGramAnimGuard.class.getName() + "$")
                        || cls.startsWith("LSPHooker") || cls.startsWith("java.lang.reflect.")) {
                    continue;
                }
                trace.append("  at ").append(e).append('\n');
                if (!cls.startsWith("android.") && !cls.startsWith("java.") && appFrames < 4) {
                    key.append('|').append(cls).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
                    appFrames++;
                }
            }
            String id = Integer.toHexString(key.toString().hashCode());
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (prefs.getBoolean(id, false) || !DevGramTelemetry.crashesActive()) {
                return; // это место уже отправляли (или отчёты выключены)
            }
            prefs.edit().putBoolean(id, true).apply();
            String report = "===== DevGram: перехвачена анимация с пустой целью (не краш) =====\n"
                    + "Раньше здесь был краш NullPointerException: setScaleX/… on a null object reference.\n"
                    + "Ловушка подставила заглушку, приложение не упало. Ниже — место СОЗДАНИЯ анимации.\n\n"
                    + "устройство: " + Build.MANUFACTURER + ' ' + Build.MODEL + ", Android " + Build.VERSION.RELEASE
                    + " (API " + Build.VERSION.SDK_INT + ")\n"
                    + "версия:     " + BuildVars.BUILD_VERSION_STRING + "\n"
                    + "свойство:   " + propertyName + "\n"
                    + "поток:      " + Thread.currentThread().getName() + "\n\n"
                    + "Signal: anim-null-target\n"
                    + trace;
            Utilities.globalQueue.postRunnable(() -> DevGramTelemetry.uploadReport(report));
        } catch (Throwable ignore) {
        }
    }
}

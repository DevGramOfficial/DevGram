package org.telegram.messenger;

import android.app.Activity;
import android.content.Intent;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Обнаруживает вызовы плагинов, которые слишком долго блокируют поток. */
public final class DevGramPluginWatchdog {

    private static final long DEFAULT_TIMEOUT_SECONDS = 5L;
    private static final AtomicLong nextToken = new AtomicLong();
    private static final Map<Thread, Execution> executions = new ConcurrentHashMap<>();
    private static final Set<String> reportedPlugins = ConcurrentHashMap.newKeySet();
    private static final ScheduledThreadPoolExecutor scheduler;

    static {
        scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "dg-plugin-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
    }

    private DevGramPluginWatchdog() {
    }

    private static final class Execution {
        final long token;
        final String pluginId;
        final String pluginName;
        final String fileName;
        final long timeoutSeconds;
        volatile ScheduledFuture<?> check;

        Execution(long token, String pluginId, String pluginName, String fileName, long timeoutSeconds) {
            this.token = token;
            this.pluginId = pluginId;
            this.pluginName = pluginName;
            this.fileName = fileName;
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static long onExecutionStarted(String pluginId, String pluginName, String fileName) {
        return onExecutionStarted(pluginId, pluginName, fileName, DEFAULT_TIMEOUT_SECONDS);
    }

    /** Lifecycle запуска может законно ждать сеть/сервис дольше обычного callback. */
    public static long onExecutionStarted(String pluginId, String pluginName, String fileName,
                                          long timeoutSeconds) {
        if (pluginId == null || pluginId.isEmpty()) {
            return 0L;
        }
        timeoutSeconds = Math.max(1L, Math.min(timeoutSeconds, 120L));
        Thread thread = Thread.currentThread();
        Execution execution = new Execution(nextToken.incrementAndGet(), pluginId,
                pluginName == null || pluginName.isEmpty() ? pluginId : pluginName,
                fileName == null ? "" : fileName, timeoutSeconds);
        Execution previous = executions.put(thread, execution);
        if (previous != null && previous.check != null) {
            previous.check.cancel(false);
        }
        execution.check = scheduler.schedule(() -> onTimeout(thread, execution),
                timeoutSeconds, TimeUnit.SECONDS);
        return execution.token;
    }

    public static void onExecutionFinished(long token) {
        if (token == 0L) {
            return;
        }
        Thread thread = Thread.currentThread();
        Execution execution = executions.get(thread);
        if (execution == null || execution.token != token || !executions.remove(thread, execution)) {
            return;
        }
        if (execution.check != null) {
            execution.check.cancel(false);
        }
        if (!hasActiveExecution(execution.pluginId)) {
            reportedPlugins.remove(execution.pluginId);
        }
    }

    public static void cancelPlugin(String pluginId) {
        if (pluginId == null) {
            return;
        }
        for (Map.Entry<Thread, Execution> item : executions.entrySet()) {
            Execution execution = item.getValue();
            if (pluginId.equals(execution.pluginId) && executions.remove(item.getKey(), execution)
                    && execution.check != null) {
                execution.check.cancel(false);
            }
        }
        reportedPlugins.remove(pluginId);
    }

    private static void onTimeout(Thread thread, Execution execution) {
        if (executions.get(thread) != execution || !reportedPlugins.add(execution.pluginId)) {
            return;
        }
        FileLog.e("[DevGramPlugins] плагин не отвечает: " + execution.pluginId
                + ", поток=" + thread.getName());
        AndroidUtilities.runOnUIThread(() -> showAlert(execution));
    }

    private static void showAlert(Execution execution) {
        if (!reportedPlugins.contains(execution.pluginId) || !hasActiveExecution(execution.pluginId)) {
            return;
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null || activity.isFinishing()) {
            reportedPlugins.remove(execution.pluginId);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, fragment.getResourceProvider());
        builder.setTitle("Плагин не отвечает");
        builder.setMessage("Плагин «" + execution.pluginName
                + "» выполняется дольше " + execution.timeoutSeconds
                + " секунд и может блокировать DevGram.");
        builder.setPositiveButton("Подождать", null);
        builder.setNegativeButton("Отключить", (dialog, which) -> {
            DevGramPlugins.forceDisableWithoutPython(execution.pluginId);
            restartApp(activity);
        });
        builder.setNeutralButton("Удалить", (dialog, which) -> {
            DevGramPlugins.forceDeleteWithoutPython(execution.pluginId, execution.fileName);
            restartApp(activity);
        });
        builder.show();
    }

    private static boolean hasActiveExecution(String pluginId) {
        for (Execution active : executions.values()) {
            if (pluginId.equals(active.pluginId)) return true;
        }
        return false;
    }

    private static void restartApp(Activity activity) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
                activity.finishAffinity();
                if (intent != null) {
                    activity.startActivity(intent);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            System.exit(0);
        }, 200L);
    }
}

package org.telegram.messenger;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Optional DevGram I/O must never hold up Telegram's DNS recovery or media preparation. */
public final class DevGramNetworkQueue {
    // Keep the previous FIFO ordering of these tasks, without using Utilities.globalQueue.
    // In particular, badge/admin token refreshes must not race their following writes.
    private static final ThreadPoolExecutor worker = createWorker();

    private DevGramNetworkQueue() { }

    private static ThreadPoolExecutor createWorker() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "DevGramNetworkIo");
                    thread.setDaemon(true);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public static void postRunnable(Runnable task) {
        worker.execute(task);
    }
}

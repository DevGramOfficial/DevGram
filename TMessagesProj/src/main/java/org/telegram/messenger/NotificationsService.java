/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import android.util.Log;

public class NotificationsService extends Service {

    private static final int NOTIFICATION_ID = 9999;
    private static final String CHANNEL_ID = "push_service_channel";

    private static volatile boolean foregroundStartPending;
    private static volatile boolean stopWhenForeground;

    private Notification notification;

    public static void onForegroundStartRequested() {
        stopWhenForeground = false;
        foregroundStartPending = true;
    }

    public static void onForegroundStartFailed() {
        foregroundStartPending = false;
    }

    public static boolean isForegroundStartPending() {
        return foregroundStartPending;
    }

    public static void requestStopWhenForeground() {
        stopWhenForeground = true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        moveToForeground();
        ApplicationLoader.postInitApplication();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        moveToForeground();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {
        super.onDestroy();
        foregroundStartPending = false;
        stopWhenForeground = false;
        SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
        if (preferences.getBoolean("pushService", true)) {
            Intent intent = new Intent("org.telegram.start");
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
    }

    private void moveToForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForeground(NOTIFICATION_ID, getNotification());
            } catch (Throwable e) {
                Log.d("DevGram", "Failed to move push service to foreground");
            }
        }
        foregroundStartPending = false;
        if (stopWhenForeground) {
            stopSelf();
        }
    }

    private Notification getNotification() {
        if (notification == null) {
            // DevGram: канал минимальной важности -> уведомление скрыто из статус-бара,
            // свёрнуто внизу шторки (как у ExteraGram). Сервис держит фон, не мозолит глаза.
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "DevGram", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            notificationManager.createNotificationChannel(channel);
            notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setShowWhen(false)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle("DevGram").build();
        }
        return notification;
    }
}

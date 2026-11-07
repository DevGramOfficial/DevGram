package org.telegram.messenger;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import org.json.JSONObject;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

/** Персональные решения модерации: системное уведомление при первом запуске/возврате. */
public final class DevGramPluginNotifications {
    private static final String RTDB="https://devgram-d03e4-default-rtdb.europe-west1.firebasedatabase.app";
    private static final String BASE_CHANNEL = "devgram_notifications";
    private static final long POLL_INTERVAL_MS = 8000L;
    private static boolean running;
    private static boolean active;
    private static final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!active) return;
            checkNow();
            AndroidUtilities.runOnUIThread(this, POLL_INTERVAL_MS);
        }
    };
    public static void init(){AndroidUtilities.runOnUIThread(()->setActive(true),5000);}
    /**
     * Register a notification channel as soon as the process starts. Galaxy Wearable
     * builds its app picker from packages with registered notification channels; if
     * the user has not received a message yet, DevGram could otherwise be missing.
     */
    public static void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ApplicationLoader.applicationContext == null) {
            return;
        }
        try {
            NotificationManager nm = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(BASE_CHANNEL) == null) {
                NotificationChannel channel = new NotificationChannel(
                        BASE_CHANNEL,
                        "Уведомления DevGram",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Основные уведомления DevGram");
                nm.createNotificationChannel(channel);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
    public static void setActive(boolean value){active=value;AndroidUtilities.cancelRunOnUIThread(pollRunnable);if(value){checkNow();AndroidUtilities.runOnUIThread(pollRunnable,POLL_INTERVAL_MS);}}
    public static void checkNow(){if(running)return;long id=UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();if(id==0)return;running=true;Utilities.globalQueue.postRunnable(()->load(id));}
    private static void load(long userId){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(RTDB+"/plugin_user_notifications/"+userId+".json").openConnection();c.setConnectTimeout(10000);c.setReadTimeout(12000);if(c.getResponseCode()!=200)return;String raw=DevGramPlugins.readPublicHttp(c.getInputStream());if(raw.isEmpty()||"null".equals(raw))return;JSONObject root=new JSONObject(raw);android.content.SharedPreferences p=ApplicationLoader.applicationContext.getSharedPreferences("devgram_plugin_notices",0);for(Iterator<String>it=root.keys();it.hasNext();){String key=it.next();if(p.getBoolean(key,false))continue;JSONObject o=root.optJSONObject(key);if(o==null)continue;show(key,o.optString("title","Каталог плагинов"),o.optString("message","Решение модерации обновлено"));p.edit().putBoolean(key,true).apply();}}catch(Throwable e){FileLog.e(e);}finally{if(c!=null)c.disconnect();running=false;}}
    private static void show(String key,String title,String message){Context ctx=ApplicationLoader.applicationContext;NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);String channel="devgram_catalog";if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(channel,"Каталог DevGram",NotificationManager.IMPORTANCE_DEFAULT));NotificationCompat.Builder b=new NotificationCompat.Builder(ctx,channel).setSmallIcon(R.drawable.notification).setContentTitle(title).setContentText(message).setStyle(new NotificationCompat.BigTextStyle().bigText(message)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT);nm.notify(key.hashCode(),b.build());AndroidUtilities.runOnUIThread(()->{BaseFragment fragment=LaunchActivity.getSafeLastFragment();if(fragment!=null&&fragment.getParentActivity()!=null)BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,title,message).show();});}
    private DevGramPluginNotifications(){}
}

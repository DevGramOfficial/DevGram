package org.telegram.messenger;

import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.os.SystemClock;
import java.lang.ref.WeakReference;
import java.util.*;

/** Own customization caches only. Telegram network/media caches keep their native lifecycle. */
public final class DevGramCustomizationMemory implements ComponentCallbacks2 {
    public interface Client {long bytes();int pool();void trim(boolean critical);}
    public static final int PAINTER=0,CHAT=1,PREVIEW=2;
    private static final ArrayList<Entry> clients=new ArrayList<>();
    private static final class Entry {final WeakReference<Client> client;long touched;Entry(Client c){client=new WeakReference<>(c);touched=SystemClock.uptimeMillis();}}
    private static final DevGramCustomizationMemory INSTANCE=new DevGramCustomizationMemory();
    private static boolean registered,watching;
    private static long lastTrim;
    private static final Runnable watch=new Runnable(){public void run(){prune();if(clients.isEmpty()){watching=false;return;}if(tight()&&SystemClock.uptimeMillis()-lastTrim>=(critical()?10000:20000)){lastTrim=SystemClock.uptimeMillis();trimAll(critical());}AndroidUtilities.runOnUIThread(this,3000);}};
    private DevGramCustomizationMemory(){}
    private static long max(){return Runtime.getRuntime().maxMemory();}
    private static long used(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    public static boolean tight(){return DevGramCustomizationMemoryBudget.tight(used(),max());}
    public static boolean critical(){return DevGramCustomizationMemoryBudget.critical(used(),max());}
    public static long limit(int pool){return pool==PREVIEW?DevGramCustomizationMemoryBudget.previews(max()):pool==CHAT?DevGramCustomizationMemoryBudget.chatFrames(max()):DevGramCustomizationMemoryBudget.painter(max());}
    private static void prune(){clients.removeIf(e->e.client.get()==null);}
    public static void touch(Client client){prune();for(Entry e:clients)if(e.client.get()==client){e.touched=SystemClock.uptimeMillis();return;}clients.add(new Entry(client));if(!registered&&ApplicationLoader.applicationContext!=null){registered=true;ApplicationLoader.applicationContext.registerComponentCallbacks(INSTANCE);}if(!watching){watching=true;AndroidUtilities.runOnUIThread(watch,3000);}}
    public static void remove(Client client){clients.removeIf(e->e.client.get()==null||e.client.get()==client);}
    /** Reserve before creating a cache bitmap, evicting least recently drawn owners first. */
    public static boolean allow(Client current,long extra){
        if(extra<=0||tight()||extra>DevGramCustomizationMemoryBudget.animation(max())||current.bytes()+extra>DevGramCustomizationMemoryBudget.painter(max()))return false;
        touch(current);long total=extra;for(Entry e:clients){Client c=e.client.get();if(c!=null&&c.pool()==current.pool())total+=c.bytes();}
        if(total>limit(current.pool())){ArrayList<Entry> oldest=new ArrayList<>(clients);oldest.sort(Comparator.comparingLong(e->e.touched));for(Entry e:oldest){Client c=e.client.get();if(c!=null&&c!=current&&c.pool()==current.pool()){long before=c.bytes();c.trim(false);total-=Math.max(0,before-c.bytes());if(total<=limit(current.pool()))break;}}}
        return total<=limit(current.pool());
    }
    private static void trimAll(boolean critical){for(Entry e:new ArrayList<>(clients)){Client c=e.client.get();if(c!=null)c.trim(critical);}org.telegram.ui.Components.DevGramMusicCoverView.trimMemory();prune();}
    @Override public void onTrimMemory(int level){if(level>=TRIM_MEMORY_RUNNING_LOW)AndroidUtilities.runOnUIThread(()->trimAll(level>=TRIM_MEMORY_RUNNING_CRITICAL));}
    @Override public void onLowMemory(){AndroidUtilities.runOnUIThread(()->trimAll(true));}
    @Override public void onConfigurationChanged(Configuration config){}
}

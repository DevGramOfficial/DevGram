package org.telegram.messenger;

import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.ui.DevGramStudioActivity;
import java.lang.ref.WeakReference;
import java.util.*;

/** Public display data, fetched in batches. UI-thread cache; no HTTP in drawing. */
public final class DevGramClanDirectory {
    public interface Listener { void onClanTagChanged(); }
    private static final class Entry {
        JSONObject tag; long checked,version; boolean loading;
        final ArrayList<WeakReference<Listener>> listeners=new ArrayList<>();
    }
    private static final LinkedHashMap<Long,Entry> cache=new LinkedHashMap<>(64,.75f,true);
    private static final LinkedHashSet<Long> pending=new LinkedHashSet<>();
    private static boolean scheduled;
    private static int generation;
    private static long policyRevision=DevGramProfilePolicy.revision();
    static { DevGramProfilePolicy.listen(()->{
        long revision=DevGramProfilePolicy.revision();boolean changed=revision!=policyRevision;policyRevision=revision;
        if(changed){generation++;pending.clear();for(Entry e:cache.values()){e.checked=0;e.loading=false;}}
        for(Entry e:new ArrayList<>(cache.values()))notifyListeners(e);
    }); }
    public static long peer(Object value){
        if(value instanceof org.telegram.tgnet.TLRPC.User)return ((org.telegram.tgnet.TLRPC.User)value).id;
        if(value instanceof org.telegram.tgnet.TLRPC.Chat)return DevGramCommunityProfiles.subject((org.telegram.tgnet.TLRPC.Chat)value);
        return 0;
    }
    private static Entry entry(long peer){
        Entry e=cache.get(peer);if(e==null){
            if(cache.size()>=2048){Iterator<Map.Entry<Long,Entry>> it=cache.entrySet().iterator();while(it.hasNext()){Entry old=it.next().getValue();old.listeners.removeIf(r->r.get()==null);if(!old.loading&&old.listeners.isEmpty()){it.remove();break;}}if(cache.size()>=2048){Long oldest=cache.keySet().iterator().next();cache.remove(oldest);pending.remove(oldest);}}
            e=new Entry();cache.put(peer,e);
        }return e;
    }
    public static void observe(long peer,Listener listener){
        if(peer==0)return;Entry e=entry(peer);boolean found=false;
        for(int i=e.listeners.size()-1;i>=0;i--){Listener value=e.listeners.get(i).get();if(value==null)e.listeners.remove(i);else found|=value==listener;}
        if(!found)e.listeners.add(new WeakReference<>(listener));
    }
    public static JSONObject peek(long peer){
        if(peer==0||!DevGramProfileCustomization.enabled())return null;
        Entry e=entry(peer);long now=SystemClock.elapsedRealtime();long ttl=e.tag==null?120000:60000;
        if(!e.loading&&(e.checked==0||now-e.checked>=ttl)){e.loading=true;pending.add(peer);if(!scheduled){scheduled=true;AndroidUtilities.runOnUIThread(DevGramClanDirectory::flush,120);}}
        JSONObject tag=e.tag;
        long policyUser=peer>0?peer:tag==null?0:tag.optLong("owner");
        return tag!=null&&DevGramProfilePolicy.allowed(policyUser,"workshop.clans")&&DevGramProfilePolicy.allowed(tag.optLong("owner",policyUser),"workshop.clans")?tag:null;
    }
    public static void accept(long peer,JSONObject tag){Entry e=entry(peer);e.version++;pending.remove(peer);e.tag=tag;e.checked=SystemClock.elapsedRealtime();e.loading=false;notifyListeners(e);}
    public static void invalidate(long peer){Entry e=entry(peer);e.version++;e.loading=false;pending.remove(peer);e.checked=0;notifyListeners(e);}
    private static void notifyListeners(Entry e){for(WeakReference<Listener> ref:new ArrayList<>(e.listeners)){Listener l=ref.get();if(l!=null)l.onClanTagChanged();}e.listeners.removeIf(ref->ref.get()==null);}
    private static void flush(){
        scheduled=false;if(pending.isEmpty())return;
        ArrayList<Long> ids=new ArrayList<>();Iterator<Long> iterator=pending.iterator();while(iterator.hasNext()&&ids.size()<200){ids.add(iterator.next());iterator.remove();}
        if(!pending.isEmpty()){scheduled=true;AndroidUtilities.runOnUIThread(DevGramClanDirectory::flush,120);}
        int revision=generation;HashMap<Long,Entry> requested=new HashMap<>();HashMap<Long,Long> versions=new HashMap<>();JSONArray peers=new JSONArray();for(long id:ids){peers.put(id);Entry e=cache.get(id);if(e!=null){requested.put(id,e);versions.put(id,e.version);}}
        DevGramProfilePolicy.executeWorkshop(()->{
            JSONObject response=null;try{response=DevGramProfilePolicy.http("POST","studio/clans/peers",DevGramStudioActivity.json("peers",peers));}catch(Exception ignored){}
            JSONObject result=response;
            AndroidUtilities.runOnUIThread(()->{
                if(revision!=generation)return;
                JSONObject tags=result!=null&&result.optBoolean("ok")?result.optJSONObject("tags"):null;
                for(long id:ids){Entry e=cache.get(id);if(e==null||e!=requested.get(id)||e.version!=versions.get(id))continue;e.loading=false;e.checked=SystemClock.elapsedRealtime()-(tags==null?(e.tag==null?110000:50000):0);
                    if(tags!=null&&tags.has(Long.toString(id))){e.tag=tags.optJSONObject(Long.toString(id));notifyListeners(e);}
                }
            });
        });
    }
    private DevGramClanDirectory(){}
}

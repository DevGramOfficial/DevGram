package org.telegram.messenger;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import java.util.ArrayList;
import java.util.HashMap;

/** Small per-profile cache; stale callbacks cannot attach to another account. */
public final class DevGramClanTags {
    private static final class Entry {JSONObject tag;long fetched;boolean loading;final ArrayList<Runnable> waiting=new ArrayList<>();}
    private static final HashMap<Long,Entry> cache=new HashMap<>();
    public static void accept(long uid,JSONObject tag){Entry e=cache.get(uid);if(e==null){e=new Entry();cache.put(uid,e);}e.tag=tag;DevGramClanDirectory.accept(uid,tag);e.fetched=android.os.SystemClock.elapsedRealtime();}
    public static void invalidate(long uid){DevGramClanDirectory.invalidate(uid);Entry e=cache.get(uid);if(e!=null)e.fetched=0;}
    public static void bind(BaseFragment host,SimpleTextView name,long uid,boolean editing,Runnable changed){
        name.bindDevGramClanPeer(host.getCurrentAccount(),uid,editing);
        boolean enabled=DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(uid,"workshop.clans");
        Entry e=cache.get(uid);if(e==null){if(cache.size()>1024)cache.clear();e=new Entry();cache.put(uid,e);}
        boolean own=uid==host.getUserConfig().getClientUserId();
        if(!own||!enabled||e.loading||android.os.SystemClock.elapsedRealtime()-e.fetched<30000)return;
        e.loading=true;e.waiting.add(()->{if(name.isAttachedToWindow()&&host.getParentActivity()!=null)changed.run();});Entry target=e;int account=host.getCurrentAccount();long viewer=host.getUserConfig().getClientUserId();
        if(own&&target.tag!=null){
            long source=target.tag.optLong("chat");TLRPC.Chat community=host.getMessagesController().getChat(Math.abs(source));
            if(community!=null){
                DevGramProfilePolicy.requestDirect(account,"studio/clans",org.telegram.ui.DevGramStudioActivity.json("action","membership","chat",source,"present",!ChatObject.isNotInChat(community)),(result,error)->{
                    target.loading=false;target.fetched=android.os.SystemClock.elapsedRealtime();
                    boolean different=result!=null&&!String.valueOf(target.tag).equals(String.valueOf(result.optJSONObject("tag")));
                    if(result!=null){target.tag=result.optJSONObject("tag");DevGramClanDirectory.accept(uid,target.tag);}ArrayList<Runnable> pending=new ArrayList<>(target.waiting);target.waiting.clear();if(different&&UserConfig.getInstance(account).getClientUserId()==viewer)for(Runnable callback:pending)callback.run();
                });return;
            }
        }
        DevGramProfilePolicy.executeWorkshop(()->{JSONObject response=null;try{response=DevGramProfilePolicy.http("GET","studio/clans/profile/"+uid+".json",null);}catch(Exception ignored){}JSONObject result=response;
            AndroidUtilities.runOnUIThread(()->{target.loading=false;target.fetched=android.os.SystemClock.elapsedRealtime();boolean different=result!=null&&!String.valueOf(target.tag).equals(String.valueOf(result.optJSONObject("tag")));if(result!=null){target.tag=result.optJSONObject("tag");DevGramClanDirectory.accept(uid,target.tag);}ArrayList<Runnable> pending=new ArrayList<>(target.waiting);target.waiting.clear();if(different&&UserConfig.getInstance(account).getClientUserId()==viewer)for(Runnable callback:pending)callback.run();});
        });
    }
    private DevGramClanTags(){}
}

package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Public metadata is cached independently of tokens and filtered again on every display. */
public final class DevGramProfileIntegrations {
    private static class Entry { JSONArray cards = new JSONArray(); long at; boolean loading; ArrayList<Utilities.Callback<JSONArray>> callbacks = new ArrayList<>(); }
    private static final LinkedHashMap<Long, Entry> cache = new LinkedHashMap<>(32, .75f, true);
    private static final LinkedHashMap<String,Entry> rows=new LinkedHashMap<>(32,.75f,true);
    private DevGramProfileIntegrations() {}
    public static void clear() { cache.clear();rows.clear(); }
    public static void invalidateRow(long uid,String block){String prefix=uid+":"+block+":";rows.entrySet().removeIf(e->e.getKey().startsWith(prefix));}
    private static void received(JSONArray cards){for(int i=0;i<cards.length();i++){JSONObject card=cards.optJSONObject(i);if(card!=null)try{card.put("_receivedAt",android.os.SystemClock.elapsedRealtime());}catch(Exception ignored){}}}
    public static void loadSteamLibrary(int account, long uid, JSONObject row, DevGramProfilePolicy.Callback callback) {
        String block = row.optString("id");
        boolean own = row.optBoolean("ownOnly");
        if (!block.matches("[A-Za-z0-9_-]{1,36}") || row.optInt("service") != 2
                || !DevGramProfileCustomization.enabled() || !DevGramProfilePolicy.allowed(uid, "profile.blocks")
                || !DevGramProfilePolicy.allowed(uid, "profile.integrations")
                || own && DevGramCommunityProfiles.owner(uid) != UserConfig.getInstance(account).getClientUserId()) {
            callback.run(null, "feature_locked"); return;
        }
        DevGramProfilePolicy.Callback done = (response, error) -> callback.run(response == null ? null : response.optJSONObject("library"), error);
        if (own) {
            try { DevGramProfilePolicy.request(account, "studio/integrations/self/steam",
                    new JSONObject().put("block", block).put("subject", uid), done); }
            catch (Exception error) { callback.run(null, "bad_document"); }
        } else {
            DevGramNetworkQueue.postRunnable(() -> {
                if (!DevGramProfileCustomization.enabled() || !DevGramProfilePolicy.allowed(uid, "profile.blocks")
                        || !DevGramProfilePolicy.allowed(uid, "profile.integrations")) {
                    AndroidUtilities.runOnUIThread(() -> done.run(null, "feature_locked")); return;
                }
                JSONObject data = null;
                try { data = DevGramProfilePolicy.http("GET", "studio/integrations/" + uid + "/steam.json?block=" + block, null); }
                catch (Exception ignored) {}
                JSONObject result = data;
                AndroidUtilities.runOnUIThread(() -> done.run(result, result == null ? "network" : null));
            });
        }
    }
    public static void loadRow(int account,long uid,JSONObject row,Utilities.Callback<JSONArray> callback){
        boolean own=row.optBoolean("ownOnly");long viewer=UserConfig.getInstance(account).getClientUserId();String identity=row.optString("id");
        if(!identity.matches("[A-Za-z0-9_-]{1,36}")||own&&DevGramCommunityProfiles.owner(uid)!=viewer||!DevGramProfileCustomization.enabled()||!DevGramProfilePolicy.allowed(uid,"profile.blocks")||!DevGramProfilePolicy.allowed(uid,"profile.integrations")){callback.run(new JSONArray());return;}
        String key=uid+":"+identity+":"+row.optInt("service")+":"+row.optString("url")+":"+row.optJSONArray("parts")+":"+own+":"+(own?viewer:0);
        Entry entry=rows.get(key);if(entry==null){entry=new Entry();rows.put(key,entry);while(rows.size()>128)rows.remove(rows.keySet().iterator().next());}
        if(System.currentTimeMillis()-entry.at<(row.optInt("service")==0||row.optInt("service")==3||row.optInt("service")==4||row.optInt("service")==5||row.optInt("service")==8?15000:60000)){callback.run(entry.cards);return;}entry.callbacks.add(callback);if(entry.loading)return;entry.loading=true;Entry target=entry;
        DevGramProfilePolicy.Callback done=(result,error)->{target.loading=false;target.at=System.currentTimeMillis();JSONArray cards=result==null?null:result.optJSONArray("cards");target.cards=cards==null?new JSONArray():cards;received(target.cards);ArrayList<Utilities.Callback<JSONArray>> listeners=target.callbacks;target.callbacks=new ArrayList<>();for(Utilities.Callback<JSONArray> listener:listeners)listener.run(DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(uid,"profile.blocks")&&DevGramProfilePolicy.allowed(uid,"profile.integrations")&&(!own||UserConfig.getInstance(account).getClientUserId()==viewer)?target.cards:new JSONArray());};
        if(own){try{DevGramProfilePolicy.request(account,"studio/integrations/self",new JSONObject().put("block",identity).put("subject",uid),done);}catch(Exception e){done.run(null,"bad_document");}}
        else DevGramNetworkQueue.postRunnable(()->{if(!DevGramProfileCustomization.enabled()){AndroidUtilities.runOnUIThread(()->done.run(null,"feature_locked"));return;}JSONObject result=null;String error=null;try{result=DevGramProfilePolicy.http("GET","studio/integrations/"+uid+".json?block="+identity,null);}catch(Exception e){error="network";}JSONObject value=result;String failure=error;AndroidUtilities.runOnUIThread(()->done.run(value,failure));});
    }
    public static void load(long uid, Utilities.Callback<JSONArray> callback) {
        if (!DevGramProfilePolicy.allowed(uid, "profile.integrations")) { callback.run(new JSONArray()); return; }
        Entry entry = cache.get(uid);
        if (entry == null) { entry = new Entry(); cache.put(uid, entry); if (cache.size() > 128) cache.remove(cache.keySet().iterator().next()); }
        if (System.currentTimeMillis() - entry.at < 600000) { callback.run(entry.cards); return; }
        entry.callbacks.add(callback); if (entry.loading) return; entry.loading = true; Entry target = entry;
        DevGramNetworkQueue.postRunnable(() -> {
            JSONArray cards = null; try { cards = DevGramProfilePolicy.http("GET", "studio/integrations/" + uid + ".json", null).optJSONArray("cards"); } catch (Exception ignored) {}
            final JSONArray value = cards == null ? new JSONArray() : cards;
            AndroidUtilities.runOnUIThread(() -> {
                target.loading = false; target.at = System.currentTimeMillis(); target.cards = value;
                ArrayList<Utilities.Callback<JSONArray>> listeners = target.callbacks; target.callbacks = new ArrayList<>();
                for (Utilities.Callback<JSONArray> listener : listeners) listener.run(DevGramProfilePolicy.allowed(uid, "profile.integrations") ? value : new JSONArray());
            });
        });
    }
}

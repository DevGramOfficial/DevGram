package org.telegram.messenger;

import org.json.JSONObject;
import java.util.LinkedHashMap;

/** Public archive metadata; tokens and account roles never enter this cache. */
public final class DevGramProfileMedia {
    private static final LinkedHashMap<Long,JSONObject> assets=new LinkedHashMap<>(32,.75f,true);
    private DevGramProfileMedia(){}
    public static synchronized void remember(JSONObject asset){
        if(asset==null||asset.optLong("id")<=0||asset.optInt("message")<=0)return;
        assets.put(asset.optLong("id"),DevGramProfileDocument.copy(asset));
        if(assets.size()>256)assets.remove(assets.keySet().iterator().next());
    }
    public static boolean any(long uid){return DevGramProfilePolicy.allowed(uid,"profile.media")||DevGramProfilePolicy.allowed(uid,"profile.media.animated");}
    public static int type(JSONObject document,String slot){
        long id=document.optLong(slot+"Asset");JSONObject media=document.optJSONObject("media"),asset=media==null?null:media.optJSONObject(slot);
        if(asset!=null&&(id<=0||asset.optLong("id")==id))return asset.optString("kind").equals("video")?1:0;
        synchronized(DevGramProfileMedia.class){asset=assets.get(id);if(asset!=null)return asset.optString("kind").equals("video")?1:0;}
        return document.optInt(slot+"MediaType");
    }
    public static String feature(JSONObject document,String slot){return type(document,slot)==1?"profile.media.animated":"profile.media";}
    private static JSONObject permitted(JSONObject asset,long uid){return DevGramProfilePolicy.allowed(uid,asset.optString("kind").equals("video")?"profile.media.animated":"profile.media")?asset:new JSONObject();}
    public static JSONObject resolve(JSONObject document,long uid,String slot){
        if(!any(uid)||!DevGramProfilePolicy.allowed(uid,feature(document,slot)))return new JSONObject();
        long id=document.optLong(slot+"Asset",-1);
        if(id==0)return new JSONObject();
        JSONObject media=document.optJSONObject("media"),asset=media==null?null:media.optJSONObject(slot);
        if(asset!=null&&(id<0||asset.optLong("id")==id)){remember(asset);return permitted(asset,uid);}
        synchronized(DevGramProfileMedia.class){if(id>0&&assets.containsKey(id))return permitted(DevGramProfileDocument.copy(assets.get(id)),uid);}
        JSONObject publicDocument=DevGramProfileDocument.get(uid);media=publicDocument.optJSONObject("media");asset=media==null?null:media.optJSONObject(slot);
        if(asset!=null&&(id<0||asset.optLong("id")==id)){remember(asset);return permitted(asset,uid);}
        return null;
    }
    public static int message(JSONObject document,long uid,String slot){JSONObject media=resolve(document,uid,slot);return media==null?-1:media.optInt("message");}
    public static void select(JSONObject document,String slot,JSONObject asset){
        try{long id=asset==null?0:asset.optLong("id");document.put(slot+"Asset",id);document.put(slot+"MediaType",asset!=null&&asset.optString("kind").equals("video")?1:0);JSONObject media=document.optJSONObject("media");if(media==null)media=new JSONObject();media.put(slot,asset==null?new JSONObject():DevGramProfileDocument.copy(asset));document.put("media",media);remember(asset);}catch(Exception ignored){}
    }
}

package org.telegram.messenger;

import android.content.Context;
import org.json.*;

/** Per-account media-player consent; only media sessions are enumerated. */
public final class DevGramMusicSources {
    private DevGramMusicSources() {}
    public static boolean selected(long uid,String source){java.util.Set<String> selected=MessagesController.getGlobalMainSettings().getStringSet("devgramMusicSources_"+uid,java.util.Collections.emptySet());return "devgram".equals(source)||DevGramProfilePolicy.allowed(uid,"profile.integrations")&&(selected.contains(source)||selected.contains("devgram.auto"));}
    public static boolean publicSource(long uid,String source){java.util.Set<String> selected=MessagesController.getGlobalMainSettings().getStringSet("devgramPublicMusicSources_"+uid,java.util.Collections.emptySet());return "devgram".equals(source)?!MessagesController.getGlobalMainSettings().getBoolean("devgramPrivatePlayerOnly_"+uid,false):selected.contains(source)||selected.contains("devgram.auto");}
    public static void sync(int account){
        long uid=UserConfig.getInstance(account).getClientUserId();if(!DevGramProfilePolicy.allowed(uid,"profile.integrations")||!DevGramProfilePolicy.allowed(uid,"profile.blocks"))return;
        java.util.HashSet<String> selected=new java.util.HashSet<>(),publicSources=new java.util.HashSet<>();JSONArray rows=DevGramProfileDocument.raw(uid).optJSONArray("blocks");
        if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null||DevGramProfileRowSpec.type(row)!=12||row.optString("url").isEmpty())continue;String source="";
            if(row.optInt("service")==8)source=row.optString("url");
            else if(row.optInt("service")==3||row.optInt("service")==4||row.optInt("service")==5){JSONArray parts=row.optJSONArray("parts");boolean playing=parts==null||parts.length()==0;if(parts!=null)for(int j=0;j<parts.length();j++)playing|=parts.optInt(j)==0;if(playing)source=row.optInt("service")==3?"ru.yandex.music":row.optInt("service")==4?"com.spotify.music":"com.soundcloud.android";}
            if(!source.matches("[A-Za-z0-9_.-]{1,80}"))continue;selected.add(source);if(!row.optBoolean("ownOnly"))publicSources.add(source);
        }
        MessagesController.getGlobalMainSettings().edit().putStringSet("devgramMusicSources_"+uid,selected).putStringSet("devgramPublicMusicSources_"+uid,publicSources).putBoolean("devgramPrivatePlayerOnly_"+uid,selected.contains("devgram")&&!publicSources.contains("devgram")||selected.contains("devgram.auto")&&!publicSources.contains("devgram.auto")).apply();
    }
    public static String name(String source){if("devgram".equals(source))return "DevGram";if("devgram.auto".equals(source))return DevGramLocale.text("Автоматически: активный плеер");try{Context context=ApplicationLoader.applicationContext;return context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(source,0)).toString();}catch(Exception e){return source;}}
    public static JSONArray available(){return DevGramMusicListener.availableSources();}
}

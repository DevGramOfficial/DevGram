package org.telegram.messenger;

import org.json.JSONObject;

/** Only public connection state leaves the API; service credentials stay on the VPS. */
public final class DevGramServiceConnections {
    private DevGramServiceConnections(){}
    public static boolean oauth(int service){return service>=0&&service<=7&&service!=6;}
    public static void request(int account,int service,String action,DevGramProfilePolicy.Callback callback){request(account,service,action,null,callback);}
    public static void request(int account,int service,String action,org.json.JSONArray parts,DevGramProfilePolicy.Callback callback){
        long owner=UserConfig.getInstance(account).getClientUserId();if(!oauth(service)||!DevGramProfileCustomization.enabled()||!DevGramProfilePolicy.allowed(owner,"profile.integrations")){callback.run(null,"feature_locked");return;}
        JSONObject body=new JSONObject();try{String language=LocaleController.getInstance().getCurrentLocaleInfo().shortName;if(parts!=null)body.put("parts",new org.json.JSONArray(parts.toString()));body.put("provider",DevGramProfileRowSpec.PROVIDERS[service]).put("language",language.equals("ru")||language.startsWith("ru-")?"ru":"en");}catch(Exception ignored){}
        DevGramProfilePolicy.request(account,"studio/connect/"+action,body,(result,error)->{if(UserConfig.getInstance(account).getClientUserId()!=owner||!DevGramProfileCustomization.enabled())return;callback.run(result,error);});
    }
}

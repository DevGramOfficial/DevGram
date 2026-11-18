package org.telegram.messenger;
import org.json.JSONObject;
/** Catalog Firebase identity and Telegram identity are independently checked by the server. */
public final class DevGramWorkshopModerationApi {
    public static void request(int account,JSONObject fields,DevGramProfilePolicy.Callback callback){
        long viewer=UserConfig.getInstance(account).getClientUserId();
        DevGramBadges.withAdminToken((token,error)->{
            if(viewer!=UserConfig.getInstance(account).getClientUserId()||!DevGramProfileCustomization.enabled()){callback.run(null,"account_changed");return;}
            if(token==null||token.isEmpty()){callback.run(null,"moderator_session");return;}
            try{JSONObject data=new JSONObject(fields.toString());data.put("moderatorToken",token);DevGramProfilePolicy.request(account,"studio/reports",data,callback);}
            catch(Exception e){callback.run(null,"bad_document");}
        });
    }
}

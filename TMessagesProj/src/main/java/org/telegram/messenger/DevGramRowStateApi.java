package org.telegram.messenger;

import org.json.JSONObject;

/** Android/account transport kept outside the deterministic row-state controller. */
public final class DevGramRowStateApi {
    private DevGramRowStateApi() {}
    public static DevGramRowStateController create(int account,long profile,JSONObject row,Runnable changed,java.util.function.Consumer<String> failed){
        return create(account,profile,row,changed,failed,false);
    }
    public static DevGramRowStateController create(int account,long profile,JSONObject row,Runnable changed,java.util.function.Consumer<String> failed,boolean authenticatedRead){
        long viewer=UserConfig.getInstance(account).getClientUserId();String identity=row.optString("id"),feature=DevGramProfileRowSpec.feature(DevGramProfileRowSpec.type(row));boolean privateRow=row.optBoolean("ownOnly");
        return new DevGramRowStateController((fields,readOnly,callback)->{
            try{
                JSONObject body=new JSONObject(fields.toString()).put("profile",profile).put("block",identity);
                if(!readOnly||privateRow||authenticatedRead){DevGramProfilePolicy.request(account,"studio/rows/state",body,(result,error)->callback.done(result,error));return;}
                String route="studio/rows/"+profile+"/"+identity+".json?version="+body.optInt("version",-1)+"&x="+body.optInt("x")+"&y="+body.optInt("y")+"&span="+body.optInt("span",32);
                DevGramNetworkQueue.postRunnable(()->{
                    JSONObject value=null;String failure=null;try{value=DevGramProfilePolicy.http("GET",route,null);if(!value.optBoolean("ok")){failure=value.optString("error","network");value=null;}}catch(Exception e){failure="network";}
                    final JSONObject result=value;final String error=failure;AndroidUtilities.runOnUIThread(()->callback.done(result,error));
                });
            }catch(Exception e){callback.done(null,"bad_document");}
        },()->DevGramProfileCustomization.enabled()&&UserConfig.getInstance(account).isClientActivated()&&UserConfig.getInstance(account).getClientUserId()==viewer&&DevGramProfilePolicy.allowed(profile,"profile.blocks")&&DevGramProfilePolicy.allowed(profile,feature),value->android.util.Base64.decode(value,android.util.Base64.DEFAULT),changed,failed);
    }
}

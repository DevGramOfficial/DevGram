package org.telegram.messenger;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.DevGramProfileStyleActivity;
import org.telegram.ui.DevGramStudioActivity;
import org.telegram.messenger.browser.Browser;
import java.util.concurrent.ConcurrentHashMap;

/** Community subjects use Bot API IDs; users retain positive Telegram user IDs. */
public final class DevGramCommunityProfiles {
    private static final ConcurrentHashMap<Long, Long> owners = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> fetched = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> revisions = new ConcurrentHashMap<>();
    private static final java.util.Set<Long> loading = ConcurrentHashMap.newKeySet();
    public static long subject(TLRPC.Chat chat) { return chat == null ? 0 : ChatObject.isChannel(chat) ? -1000000000000L-chat.id : -chat.id; }
    public static long chatId(long subject) { return subject <= -1000000000000L ? -subject-1000000000000L : -subject; }
    public static TLRPC.Chat localChat(int account,long id) {
        MessagesController controller=MessagesController.getInstance(account);
        TLRPC.Chat chat=controller.getChat(Math.abs(id));
        if(chat==null){chat=MessagesStorage.getInstance(account).getChatSync(Math.abs(id));if(chat!=null)controller.putChat(chat,true);}
        return chat;
    }
    public static boolean musicActivity(long uid){return DevGramProfilePolicy.allowed(uid,"profile.communities")&&MessagesController.getGlobalMainSettings().getBoolean("devgramCommunityMusic_"+uid,false);}
    public static long owner(long subject) { return subject < 0 ? owners.getOrDefault(subject,0L) : subject; }
    public static String kind(TLRPC.Chat chat) { return chat != null && chat.forum ? "forum" : chat != null && chat.broadcast && !chat.megagroup ? "channel" : "group"; }
    public static void ensure(long subject) {
        if (subject >= 0 || !DevGramProfileCustomization.enabled() || System.currentTimeMillis()-fetched.getOrDefault(subject,0L)<60000 || !loading.add(subject)) return;
        long revision=revisions.getOrDefault(subject,0L);
        DevGramProfilePolicy.executeWorkshop(()->{
            JSONObject result=null;
            try { result=DevGramProfilePolicy.http("GET","studio/community/"+(-subject)+".json",null); } catch(Exception ignored) { }
            JSONObject response=result;
            AndroidUtilities.runOnUIThread(()->{
                loading.remove(subject);
                if (!DevGramProfileCustomization.enabled() || revision!=revisions.getOrDefault(subject,0L)) return;
                if (response!=null && response.optBoolean("ok")) { accept(subject,response,false); fetched.put(subject,System.currentTimeMillis()); }
            });
        });
    }
    public static void acceptWorkshop(int account,JSONObject result){long subject=result.optLong("subject");if(subject>=0||result.optLong("owner")!=UserConfig.getInstance(account).getClientUserId())return;revisions.merge(subject,1L,Long::sum);accept(subject,result,true);}
    public static void refresh(long subject) { fetched.remove(subject); ensure(subject); }
    private static void accept(long subject,JSONObject result,boolean owned) {
        owners.put(subject,result.optLong("owner"));
        if(owned&&result.has("musicActivity"))MessagesController.getGlobalMainSettings().edit().putBoolean("devgramCommunityMusic_"+result.optLong("owner"),result.optBoolean("musicActivity")).apply();
        DevGramProfileDocument.storeCommunity(subject,result.optJSONObject("document"),owned);
        DevGramProfileStyle.storeSaved(subject,new DevGramProfileStyle.Style(result.optJSONObject("theme")));
        DevGramProfilePolicy.changed();
    }
    public static void load(int account,long subject,DevGramProfilePolicy.Callback callback) {
        if (!DevGramProfileCustomization.enabled()) { callback.run(null,"feature_locked"); return; }
        DevGramProfilePolicy.request(account,"studio/communities",DevGramStudioActivity.json("action","load","subject",subject),(result,error)->{
            if(result!=null&&DevGramProfileCustomization.enabled()){revisions.merge(subject,1L,Long::sum);accept(subject,result,true);}
            callback.run(DevGramProfileCustomization.enabled()?result:null,error);
        });
    }
    public static void save(int account,long subject,JSONObject document,DevGramProfileStyle.Style theme,DevGramProfilePolicy.Callback callback) {
        if (!DevGramProfileCustomization.enabled()) { callback.run(null,"feature_locked"); return; }
        DevGramProfilePolicy.request(account,"studio/communities",DevGramStudioActivity.json("action","save","subject",subject,"document",document,"theme",(theme==null?DevGramProfileStyle.raw(subject):theme).effective(UserConfig.getInstance(account).getClientUserId()).json()),(result,error)->{
            if(result!=null&&DevGramProfileCustomization.enabled()){revisions.merge(subject,1L,Long::sum);accept(subject,result,true);}
            callback.run(DevGramProfileCustomization.enabled()?result:null,error);
        });
    }
    public static void patch(int account,long subject,JSONObject source,String[] keys,DevGramProfilePolicy.Callback callback){
        JSONObject selected=new JSONObject();org.json.JSONArray fields=new org.json.JSONArray();
        try{JSONObject active=DevGramProfileDocument.effective(source,subject);for(String key:keys){fields.put(key);if(active.has(key))selected.put(key,active.opt(key));}}catch(Exception ignored){}
        mutate(account,subject,DevGramStudioActivity.json("action","patch","subject",subject,"document",selected,"fields",fields),callback);
    }
    public static void reset(int account,long subject,DevGramProfilePolicy.Callback callback){mutate(account,subject,DevGramStudioActivity.json("action","reset","subject",subject),callback);}
    private static void mutate(int account,long subject,JSONObject data,DevGramProfilePolicy.Callback callback){
        if(!DevGramProfileCustomization.enabled()){callback.run(null,"feature_locked");return;}
        DevGramProfilePolicy.request(account,"studio/communities",data,(result,error)->{if(result!=null&&DevGramProfileCustomization.enabled()){revisions.merge(subject,1L,Long::sum);accept(subject,result,true);}callback.run(DevGramProfileCustomization.enabled()?result:null,error);});
    }
    public static void verify(BaseFragment host,long id,String title) {
        TLRPC.Chat chat=localChat(host.getCurrentAccount(),id);
        boolean tag=host instanceof org.telegram.ui.DevGramClanTagsActivity;
        if(chat==null||!chat.creator){
            String error=chat==null?"community_local_chat_missing":"clan_owner_required";
            if(tag)((org.telegram.ui.DevGramClanTagsActivity)host).acceptVerification(null,error);
            else if(host.getParentActivity()!=null)BulletinFactory.of(host).createErrorBulletin(DevGramCommunityBinding.errorText(error)).show();
            return;
        }
        String route=tag?"studio/clans":"studio/communities";
        JSONObject data=tag?DevGramStudioActivity.json("action","status","chat",id):DevGramStudioActivity.json("action","load","subject",subject(chat));
        DevGramCommunityBinding.run(host,chat,route,data,(result,error)->{
            if(host.getParentActivity()==null||!DevGramProfileCustomization.enabled())return;
            if(tag)((org.telegram.ui.DevGramClanTagsActivity)host).acceptVerification(result,error);
            else if(result!=null)showEditor(host,chat,result);
            else if(!"cancelled".equals(error))BulletinFactory.of(host).createErrorBulletin(DevGramCommunityBinding.errorText(error)).show();
        });
    }
    private static void showEditor(BaseFragment host,TLRPC.Chat chat,JSONObject result){
        long subject=subject(chat);revisions.merge(subject,1L,Long::sum);accept(subject,result,true);
        DevGramStudioActivity.Draft draft=new DevGramStudioActivity.Draft();draft.subject=subject;draft.scope=kind(chat);draft.document=DevGramProfileDocument.editable(result.optJSONObject("document"));draft.theme=new DevGramProfileStyle.Style(result.optJSONObject("theme"));host.presentFragment(new DevGramProfileStyleActivity(draft));
    }
    public static void openEditor(BaseFragment host,TLRPC.Chat chat) {
        if(chat==null||!chat.creator||!org.telegram.ui.DevGramAppearanceAccess.check(host,"profile.communities"))return;
        load(host.getCurrentAccount(),subject(chat),(result,error)->{
            if(host.getParentActivity()==null||!DevGramProfileCustomization.enabled())return;
            if(result!=null)showEditor(host,chat,result);
            else if("community_verification_required".equals(error)||"clan_owner_required".equals(error)||"clan_bot_required".equals(error))verify(host,ChatObject.isChannel(chat)?chat.id:-chat.id,chat.title);
            else BulletinFactory.of(host).createErrorBulletin(DevGramLocale.text("Не удалось проверить доступ к сообществу")).show();
        });
    }
}

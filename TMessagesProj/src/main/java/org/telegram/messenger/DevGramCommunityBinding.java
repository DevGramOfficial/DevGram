package org.telegram.messenger;

import org.json.JSONObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.DevGramSensitiveRequestSheet;
import org.telegram.ui.DevGramStudioActivity;

/** A short bot visit brackets a server-verified operation, including cleanup. */
public final class DevGramCommunityBinding {
    private static final java.util.Set<String> running=new java.util.HashSet<>();
    private static final java.util.Set<String> consented=new java.util.HashSet<>();
    public static String errorText(String error){
        String message="community_local_chat_missing".equals(error)?"Не удалось загрузить данные сообщества. Откройте его профиль и повторите.":"clan_owner_required".equals(error)?"Подтвердить владение может только владелец сообщества.":"network".equals(error)?"Не удалось выполнить действие. Проверьте доступ и соединение.":"community_bot_cleanup".equals(error)?"Бот ещё выходит из сообщества. Повторите действие через несколько секунд.":"community_bind_busy".equals(error)?"Проверка уже выполняется. Дождитесь завершения.":"community_bind_expired".equals(error)?"Время подтверждения истекло. Повторите проверку.":"community_bot_existing_member".equals(error)?"Бот уже находится в сообществе. Его права сохранены; проверьте доступ бота и повторите.":"community_bot_add_failed".equals(error)?"Не удалось временно добавить бота. Проверьте права владельца и доступность бота.":"Не удалось подтвердить владение. Повторите проверку.";
        return DevGramLocale.text(message);
    }
    public static boolean retry(int account,String route,JSONObject data,DevGramProfilePolicy.Callback callback){
        if(!DevGramProfileCustomization.enabled()||route.equals("studio/communities/bind"))return false;
        long subject=data.optLong("subject",data.optLong("profile"));
        if(route.equals("studio/clans")){
            if(!data.optString("action").equals("configure"))return false;
            long id=data.optLong("chat");subject=id<0?id:id>0?-1000000000000L-id:0;
        }
        if(subject>=0)return false;
        BaseFragment host=org.telegram.ui.LaunchActivity.getLastFragment();
        if(host==null||host.getCurrentAccount()!=account)return false;
        long id=DevGramCommunityProfiles.chatId(subject);TLRPC.Chat chat=MessagesController.getInstance(account).getChat(id);
        if(chat==null)chat=MessagesStorage.getInstance(account).getChatSync(id);
        if(chat==null||!chat.creator)return false;
        run(host,chat,route,data,callback);return true;
    }

    private final BaseFragment host;
    private final int account;
    private final long uid,subject;
    private final TLRPC.Chat chat;
    private final String route,key;
    private final JSONObject fields;
    private final DevGramProfilePolicy.Callback callback;
    private String visit="",token="";
    private JSONObject pending;
    private String pendingError;
    private boolean complete;
    private AlertDialog progress;
    private TLRPC.User bot;
    private int polls;
    private long expires;
    private DevGramCommunityBinding(BaseFragment host,TLRPC.Chat chat,String route,JSONObject fields,DevGramProfilePolicy.Callback callback){
        this.host=host;this.chat=chat;account=host.getCurrentAccount();uid=UserConfig.getInstance(account).getClientUserId();subject=DevGramCommunityProfiles.subject(chat);this.route=route;this.fields=fields;this.callback=callback;key=identityKey(account,uid,subject);
    }
    public static void run(BaseFragment host,TLRPC.Chat chat,String route,JSONObject fields,DevGramProfilePolicy.Callback callback){
        if(host==null||chat==null||!chat.creator||host.getParentActivity()==null||!DevGramProfileCustomization.enabled()){callback.run(null,"clan_owner_required");return;}
        DevGramCommunityBinding flow=new DevGramCommunityBinding(host,chat,route,fields,callback);
        if(!running.add(flow.key)){callback.run(null,"community_bind_busy");return;}
        flow.start();
    }
    private static String identityKey(int account,long uid,long subject){return account+":"+uid+":"+subject;}
    private boolean valid(){return !complete&&host.getParentActivity()!=null&&host==org.telegram.ui.LaunchActivity.getLastFragment()&&host.getCurrentAccount()==account&&UserConfig.getInstance(account).getClientUserId()==uid&&DevGramProfileCustomization.enabled();}
    private void start(){
        DevGramProfilePolicy.requestDirect(account,"studio/communities/bind",DevGramStudioActivity.json("action","start","subject",subject),(result,error)->{
            token=DevGramConfig.getBetaToken(uid);
            if(result==null){finish(null,error);return;}
            visit=result.optString("visit");expires=result.optLong("expires")*1000L;
            if(!valid()){finish(null,"account_changed");return;}
            if(result.optBoolean("ready")){operate();return;}
            long expected=result.optLong("bot");String username=result.optString("botUsername");
            if(expected<=0||!username.matches("[A-Za-z][A-Za-z0-9_]{3,31}")){finish(null,"clan_bot_required");return;}
            TLRPC.TL_contacts_resolveUsername resolve=new TLRPC.TL_contacts_resolveUsername();resolve.username=username;
            ConnectionsManager.getInstance(account).sendRequest(resolve,(response,failure)->AndroidUtilities.runOnUIThread(()->{
                if(!valid()){finish(null,"account_changed");return;}
                if(failure!=null||!(response instanceof TLRPC.TL_contacts_resolvedPeer)){finish(null,"clan_bot_required");return;}
                TLRPC.TL_contacts_resolvedPeer peer=(TLRPC.TL_contacts_resolvedPeer)response;
                MessagesController.getInstance(account).putUsers(peer.users,false);
                for(TLRPC.User user:peer.users)if(user.id==expected&&user.bot)bot=user;
                if(bot==null){finish(null,"clan_bot_required");return;}
                if(consented.contains(key+":"+bot.id))approve();else confirm();
            }));
        });
    }
    private TLObject promotion(){
        if(ChatObject.isChannel(chat)){
            TLRPC.TL_channels_editAdmin request=new TLRPC.TL_channels_editAdmin();request.channel=MessagesController.getInputChannel(chat);request.user_id=MessagesController.getInstance(account).getInputUser(bot);request.admin_rights=new TLRPC.TL_chatAdminRights();request.admin_rights.other=true;return request;
        }
        TLRPC.TL_messages_editChatAdmin request=new TLRPC.TL_messages_editChatAdmin();request.chat_id=chat.id;request.user_id=MessagesController.getInstance(account).getInputUser(bot);request.is_admin=true;return request;
    }
    private void confirm(){
        TLObject request=promotion();
        host.showDialog(new DevGramSensitiveRequestSheet(host.getParentActivity(),host.getResourceProvider(),account,request,allowed->{
            if(!allowed){finish(null,"cancelled");return;}
            consented.add(key+":"+bot.id);approve();
        },DevGramLocale.text(ChatObject.isChannel(chat)?"DevGram временно добавит проверочного бота с минимальными правами, проверит владельца через API и автоматически выведет бота после завершения. Сообщения в сообщество не отправляются.":"DevGram временно добавит проверочного бота в администраторы группы, проверит владельца через API и автоматически выведет бота после завершения. Сообщения в группу не отправляются.")));
    }
    private void approve(){
        if(System.currentTimeMillis()+10000>=expires){finish(null,"community_bind_expired");return;}
        if(!valid()){finish(null,"account_changed");return;}
        progress=new AlertDialog(host.getParentActivity(),AlertDialog.ALERT_TYPE_SPINNER,host.getResourceProvider());progress.setCanCancel(false);progress.show();
        // Read the actual role immediately before the grant; never replace a bot
        // that was already a member or an administrator of this community.
        if(ChatObject.isChannel(chat)){
            TLRPC.TL_channels_getParticipant check=new TLRPC.TL_channels_getParticipant();check.channel=MessagesController.getInputChannel(chat);check.participant=MessagesController.getInstance(account).getInputPeer(bot.id);
            ConnectionsManager.getInstance(account).sendRequest(check,(response,error)->AndroidUtilities.runOnUIThread(()->{
                if(error==null){finish(null,"community_bot_existing_member");return;}
                if(!"USER_NOT_PARTICIPANT".equals(error.text)){finish(null,"community_bot_add_failed");return;}
                if(!valid()){finish(null,"account_changed");return;}sendPromotion(promotion());
            }));
        }else{
            TLRPC.TL_messages_getFullChat check=new TLRPC.TL_messages_getFullChat();check.chat_id=chat.id;
            ConnectionsManager.getInstance(account).sendRequest(check,(response,error)->AndroidUtilities.runOnUIThread(()->{
                if(error!=null||!(response instanceof TLRPC.TL_messages_chatFull)){finish(null,"community_bot_add_failed");return;}
                TLRPC.ChatParticipants participants=((TLRPC.TL_messages_chatFull)response).full_chat.participants;
                if(!(participants instanceof TLRPC.TL_chatParticipants)){finish(null,"community_bot_add_failed");return;}
                for(TLRPC.ChatParticipant member:participants.participants)if(member.user_id==bot.id){finish(null,"community_bot_existing_member");return;}
                if(!valid()){finish(null,"account_changed");return;}
                TLRPC.TL_messages_addChatUser add=new TLRPC.TL_messages_addChatUser();add.chat_id=chat.id;add.user_id=MessagesController.getInstance(account).getInputUser(bot);add.fwd_limit=0;
                ConnectionsManager.getInstance(account).sendRequest(add,(added,failure)->AndroidUtilities.runOnUIThread(()->{
                    if(failure!=null){finish(null,"community_bot_add_failed");return;}
                    if(added instanceof TLRPC.Updates)MessagesController.getInstance(account).processUpdates((TLRPC.Updates)added,false);
                    if(!valid()){finish(null,"account_changed");return;}sendPromotion(promotion());
                }));
            }));
        }
    }
    private void sendPromotion(TLObject request){
        ConnectionsManager.getInstance(account).sendRequest(request,(response,error)->AndroidUtilities.runOnUIThread(()->{
            if(error!=null){finish(null,"community_bot_add_failed");return;}
            if(response instanceof TLRPC.Updates)MessagesController.getInstance(account).processUpdates((TLRPC.Updates)response,false);
            poll();
        }));
    }
    private void poll(){
        if(!valid()){finish(null,"account_changed");return;}
        if(++polls>90||System.currentTimeMillis()+5000>=expires){finish(null,"community_bind_expired");return;}
        DevGramProfilePolicy.requestDirect(account,"studio/communities/bind",DevGramStudioActivity.json("action","status","visit",visit),(result,error)->{
            if(result==null){finish(null,error);return;}
            String failure=result.optString("error");
            if(!failure.isEmpty()){finish(null,failure);return;}
            if(result.optBoolean("ready")){operate();return;}
            if(result.optBoolean("finished")){finish(null,"community_bot_add_failed");return;}
            AndroidUtilities.runOnUIThread(this::poll,700);
        });
    }
    private void operate(){
        if(!valid()){finish(null,"account_changed");return;}
        DevGramProfilePolicy.requestDirect(account,route,fields,this::finish);
    }
    private void finish(JSONObject result,String error){
        if(complete)return;complete=true;pending=result;pendingError=error;
        if(progress!=null){progress.dismiss();progress=null;}
        if(visit.isEmpty()){deliver();return;}
        JSONObject body=DevGramStudioActivity.json("uid",uid,"token",token,"action","finish","visit",visit);
        DevGramProfilePolicy.executeWorkshop(()->{
            boolean cleaned=false;
            for(int attempt=0;attempt<2&&!cleaned;attempt++)try{JSONObject response=DevGramProfilePolicy.http("POST","studio/communities/bind",body);cleaned=response.optBoolean("ok")&&response.optBoolean("cleaned");}catch(Exception ignored){}
            boolean done=cleaned;
            AndroidUtilities.runOnUIThread(()->{if(!done&&pendingError==null){pending=null;pendingError="community_bot_cleanup";}deliver();});
        });
    }
    private void deliver(){
        running.remove(key);
        if(UserConfig.getInstance(account).getClientUserId()!=uid||!DevGramProfileCustomization.enabled()){callback.run(null,"account_changed");return;}
        callback.run(pending,pendingError);
    }
}

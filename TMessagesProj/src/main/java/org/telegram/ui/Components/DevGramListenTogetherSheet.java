package org.telegram.ui.Components;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;

public final class DevGramListenTogetherSheet {
    private static String t(String value){return DevGramLocale.text(value);}
    public static void show(BaseFragment host,long owner,JSONObject row){
        if(host==null||host.getParentActivity()==null||!org.telegram.ui.DevGramAppearanceAccess.check(host,"music.listening"))return;long viewer=host.getUserConfig().getClientUserId();String block=row.optString("id");
        JSONObject request=new JSONObject();try{request.put("owner",owner).put("block",block).put("action","status");}catch(Exception ignored){}
        DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/listen/state",request,(data,error)->{
            if(host.getParentActivity()==null||host.getUserConfig().getClientUserId()!=viewer)return;
            if(data==null){Toast.makeText(host.getParentActivity(),t("Сохраните строку и подключите музыкальный сервис, чтобы открыть прослушивание."),Toast.LENGTH_LONG).show();return;}
            LinearLayout body=DevGramStudioControls.column(host.getParentActivity());body.setPadding(AndroidUtilities.dp(22),0,AndroidUtilities.dp(22),AndroidUtilities.dp(18));
            JSONObject track=data.optJSONObject("track");TextView song=DevGramStudioControls.text(host.getParentActivity(),"",17,true,host.getResourceProvider());song.setText(track==null?"":track.optString("title",track.optString("track")));body.addView(song,LayoutHelper.createLinear(-1,-2,0,8,0,10));
            JSONArray room=data.optJSONArray("room");LinearLayout people=new LinearLayout(host.getParentActivity());people.setGravity(Gravity.CENTER_VERTICAL);
            if(room!=null)for(int i=0;i<Math.min(5,room.length());i++){long uid=room.optLong(i);org.telegram.tgnet.TLRPC.User user=host.getMessagesController().getUser(uid);BackupImageView avatar=new BackupImageView(host.getParentActivity());avatar.setRoundRadius(AndroidUtilities.dp(14));AvatarDrawable fallback=new AvatarDrawable();if(user!=null){fallback.setInfo(user);avatar.setForUserOrChat(user,fallback);}else{fallback.setInfo(uid,t("Участник"),"");avatar.setImageDrawable(fallback);}people.addView(avatar,LayoutHelper.createLinear(28,28,0,0,4,0));}
            TextView count=DevGramStudioControls.text(host.getParentActivity(),"",13,false,host.getResourceProvider());count.setText(t("Комната")+" · "+(room==null?0:room.length()));people.addView(count);body.addView(people,LayoutHelper.createLinear(-1,40,0,0,0,12));
            BottomSheet sheet=new BottomSheet.Builder(host.getParentActivity(),false,host.getResourceProvider()).setTitle(t("Совместное прослушивание")).setCustomView(body).create();sheet.fixNavigationBarKey(Theme.key_dialogBackground);
            boolean follows=DevGramListenTogether.follows(owner,block);TextView join=button(host,viewer==org.telegram.messenger.DevGramCommunityProfiles.owner(owner)?"Открыть плеер":follows?"Отключиться":"Слушать вместе",true);join.setEnabled(viewer==org.telegram.messenger.DevGramCommunityProfiles.owner(owner)||data.optBoolean("enabled")||follows);join.setAlpha(join.isEnabled()?1:.5f);join.setOnClickListener(v->{if(viewer==org.telegram.messenger.DevGramCommunityProfiles.owner(owner)){String service=data.optString("provider");String link=track==null?"":track.optString("link");if(link.isEmpty())link=service.equals("spotify")?"https://open.spotify.com/":service.equals("soundcloud")?"https://soundcloud.com/":"https://music.yandex.ru/";org.telegram.messenger.browser.Browser.openUrl(host.getParentActivity(),link);return;}if(follows){DevGramListenTogether.stop();sheet.dismiss();}else{join.setEnabled(false);DevGramListenTogether.start(host.getCurrentAccount(),owner,block,sheet::dismiss);}});body.addView(join,LayoutHelper.createLinear(-1,48));
            TextView note=DevGramStudioControls.text(host.getParentActivity(),data.optBoolean("enabled")?"При подключении звук начнётся на вашем устройстве. Трек, пауза и позиция синхронизируются с владельцем профиля.":"Владелец профиля не включил совместное прослушивание.",13,false,host.getResourceProvider());body.addView(note,LayoutHelper.createLinear(-1,-2,0,12,0,12));
            if(viewer==org.telegram.messenger.DevGramCommunityProfiles.owner(owner)){org.telegram.ui.Cells.TextCheckCell toggle=new org.telegram.ui.Cells.TextCheckCell(host.getParentActivity(),host.getResourceProvider());toggle.setTextAndCheck(t("Совместное прослушивание"),data.optBoolean("enabled"),false);toggle.setHeight(56);toggle.setOnClickListener(v->{sheet.dismiss();setSharing(host,data.optString("provider"),!data.optBoolean("enabled"),()->{});});body.addView(toggle,LayoutHelper.createLinear(-1,56,0,4,0,0));}
            TextView close=button(host,"Закрыть",false);close.setOnClickListener(v->sheet.dismiss());body.addView(close,LayoutHelper.createLinear(-1,48,0,4,0,0));host.showDialog(sheet);
        });
    }
    public static void setSharing(BaseFragment host,String provider,boolean enabled,Runnable changed){
        final long viewer=host.getUserConfig().getClientUserId();final int account=host.getCurrentAccount();
        Runnable save=()->{if(host.getParentActivity()==null||host.getCurrentAccount()!=account||host.getUserConfig().getClientUserId()!=viewer)return;JSONObject request=new JSONObject();try{request.put("provider",provider).put("enabled",enabled);}catch(Exception ignored){}DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/listen/share",request,(data,error)->{if(host.getParentActivity()==null||host.getCurrentAccount()!=account||host.getUserConfig().getClientUserId()!=viewer)return;Toast.makeText(host.getParentActivity(),t(data==null?"Не удалось изменить совместное прослушивание":enabled?"Совместное прослушивание включено":"Совместное прослушивание выключено"),Toast.LENGTH_LONG).show();if(data!=null){DevGramProfileIntegrations.clear();changed.run();}});};
        if(enabled){if(!org.telegram.ui.DevGramAppearanceAccess.check(host,"music.listening")||!org.telegram.ui.DevGramAppearanceAccess.check(host,"music.activity"))return;DevGramShareListeningWarning.show(host,provider,save);}else save.run();
    }
    private static TextView button(BaseFragment host,String label,boolean primary){TextView v=DevGramStudioControls.text(host.getParentActivity(),label,15,primary,host.getResourceProvider());v.setGravity(Gravity.CENTER);v.setPadding(AndroidUtilities.dp(8),0,AndroidUtilities.dp(8),0);v.setTextColor(Theme.getColor(primary?Theme.key_featuredStickers_buttonText:Theme.key_windowBackgroundWhiteBlueText,host.getResourceProvider()));v.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),Theme.getColor(primary?Theme.key_featuredStickers_addButton:Theme.key_windowBackgroundGray,host.getResourceProvider()),Theme.getColor(Theme.key_listSelector,host.getResourceProvider())));return v;}
}

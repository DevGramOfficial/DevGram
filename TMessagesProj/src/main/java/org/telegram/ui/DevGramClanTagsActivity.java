package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.ArrayList;

/** Community-owned tag editor and the reference-style profile tag chooser. */
public final class DevGramClanTagsActivity extends DevGramSettingsFragment {
    private final long chat;
    private UniversalRecyclerView list;
    private JSONArray clans=new JSONArray();
    private JSONObject info,clan;
    private String tag="",bot="";
    private long emoji,selected,viewer;
    private boolean enabled=true,loading,closed,dirty,loaded;
    private int generation;
    private int minMembers=DevGramProfilePolicy.clanMinMembers();
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow emojiWindow;
    public DevGramClanTagsActivity(){this(0);}
    public DevGramClanTagsActivity(long chat){this.chat=chat;}
    private final Runnable policyChanged=this::onPolicyChanged;
    private void onPolicyChanged(){if(closed)return;if(!DevGramProfileCustomization.enabled()||!DevGramProfilePolicy.allowed(viewer,"workshop.clans")){dismissCurrentDialog();DevGramAccessNavigation.close(this);return;}minMembers=DevGramProfilePolicy.clanMinMembers();if(info!=null)try{info.put("eligible",info.optInt("members")>=minMembers);}catch(Exception ignored){}if(list!=null)list.adapter.update(true);}
    public long communityId(){return chat;}
    public void refreshAfterVerification(){load();}
    public void acceptVerification(JSONObject result,String error){
        if(!active())return;generation++;loading=false;
        if(result==null){list.adapter.update(true);if(!"cancelled".equals(error))showError(error);return;}
        info=result;minMembers=result.optInt("minMembers",DevGramProfilePolicy.clanMinMembers());clan=result.optJSONObject("clan");
        if(!dirty&&clan!=null){tag=clan.optString("tag");emoji=clan.optLong("emoji");enabled=clan.optBoolean("enabled");}
        list.adapter.update(true);
        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,t("Владение подтверждено")).show();
    }

    private String t(String s){return DevGramLocale.text(s);}
    private boolean active(){return !closed&&getParentActivity()!=null&&viewer==getUserConfig().getClientUserId()&&DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(viewer,"workshop.clans");}
    @Override public boolean onFragmentCreate(){return DevGramProfileCustomization.enabled()&&super.onFragmentCreate();}
    @Override public View createView(Context context){
        viewer=getUserConfig().getClientUserId();actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle(t(chat==0?"Выберите тег":"Тег клана"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1&&onBackPressed(true))finishFragment();}});
        DevGramProfilePolicy.listen(policyChanged);list=new UniversalRecyclerView(this,this::fill,this::click,null);fragmentView=new DevGramSettingsLayout(context,actionBar,list,resourceProvider);return fragmentView;
    }
    private void fill(ArrayList<UItem> items,UniversalAdapter adapter){
        if(chat==0){
            items.add(UItem.asShadow(t("Тег появится рядом с вашим именем. Выберите сообщество, в котором состоите. Источник показывает, кому принадлежит тег.")));
            for(int i=0;i<clans.length();i++){JSONObject value=clans.optJSONObject(i);if(value!=null)items.add(UItem.asCustom(new DevGramClanTagChoiceView(getParentActivity(),currentAccount,value,value.optLong("chat")==selected,resourceProvider,()->choose(value))));}
            if(clans.length()==0)items.add(UItem.asShadow(t(loading?"Загружаю теги кланов…":"Нет доступных тегов. Они появятся после вступления в сообщество с настроенным кланом.")));
            if(selected!=0)items.add(UItem.asButton(1,R.drawable.msg_delete,t("Снять тег")).red());
            items.add(UItem.asShadow(String.format(java.util.Locale.ROOT,t("Для создания тега откройте управление своим каналом, группой или форумом → Тег клана. Нужно не менее %d участников."),minMembers)));
        }else{
            TLRPC.Chat local=DevGramCommunityProfiles.localChat(currentAccount,chat);String title=info==null?local==null?"":local.title:info.optString("title");
            JSONObject preview=DevGramStudioActivity.json("chat",chat,"title",title,"tag",tag.isEmpty()?t("ТЕГ"):tag,"emoji",emoji,"count",clan==null?0:clan.optInt("count"));
            items.add(UItem.asCustom(new DevGramClanTagChoiceView(getParentActivity(),currentAccount,preview,false,resourceProvider,()->{})));
            items.add(UItem.asHeader(t("Оформление тега")));
            items.add(UItem.asButton(3,t("Тег"),tag.isEmpty()?t("Не задан"):tag));
            items.add(UItem.asButton(4,R.drawable.msg_premium_badge,t("Премиум-эмодзи"),emoji==0?t("Выбрать"):t("Выбрано")));
            items.add(UItem.asCheck(5,t("Показывать тег")).setChecked(enabled));
            items.add(UItem.asShadow(t("Тег состоит из премиум-эмодзи и 1–5 букв или специальных символов. Участник сообщества сам выбирает его для своего профиля.")));
            items.add(UItem.asHeader(t("Сообщество")));items.add(UItem.asButton(0,t("Канал или группа"),title));items.add(UItem.asButton(0,t("Подписчики"),info==null?"—":String.valueOf(info.optInt("members"))));
            items.add(UItem.asShadow(loading?t("Проверяем подтверждение владельца и число подписчиков…"):info==null?t("Подтвердите владение через бота. DevGram временно добавит его в сообщество и выведет после проверки."):info.optBoolean("eligible")?t("Владение подтверждено. Участники могут выбрать опубликованный тег для своего профиля."):String.format(java.util.Locale.ROOT,t("Для тега нужно не менее %d участников."),minMembers)));
            if(info==null&&!loading)items.add(UItem.asButton(6,R.drawable.msg_link,t("Подтвердить через бота")));
            items.add(UItem.asButton(7,R.drawable.ic_ab_done,t("Сохранить тег")).setEnabled(!loading&&info!=null&&(info.optBoolean("eligible")||clan!=null&&!enabled)));
            if(clan!=null&&clan.optBoolean("enabled")&&!dirty)items.add(UItem.asButton(9,R.drawable.msg_contact,t("Выбрать этот тег для профиля")));
        }
        items.add(UItem.asButton(8,R.drawable.msg_retry,t(chat==0||info!=null?"Обновить":"Проверить снова")).setEnabled(!loading));items.add(UItem.asShadow(null));
    }
    private void click(UItem item,View view,int position,float x,float y){
        if(!active()||loading)return;
        switch(item.id){
            case 1:choose(null);break;case 2:owned();break;case 3:editTag();break;case 4:pickEmoji(view);break;case 5:enabled=!enabled;dirty=true;list.adapter.update(true);break;
            case 6:if(info==null){loading=true;list.adapter.update(true);TLRPC.Chat community=DevGramCommunityProfiles.localChat(currentAccount,chat);DevGramCommunityProfiles.verify(this,chat,community==null?"":community.title);}break;
            case 7:save();break;case 8:load();break;case 9:if(clan!=null)choose(clan);break;
        }
    }
    private void owned(){
        ArrayList<TLRPC.Chat> owned=new ArrayList<>();for(TLRPC.Chat c:getMessagesController().getChats().values())if(c.creator&&!ChatObject.isNotInChat(c))owned.add(c);
        owned.sort((a,b)->a.title.compareToIgnoreCase(b.title));if(owned.isEmpty()){new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("Создать тег сообщества")).setMessage(t("Откройте ваш канал или группу, нажмите «Изменить» и выберите «Тег клана». Доступно владельцу сообщества." )).setPositiveButton(t("Понятно"),null).show();return;}
        String[] titles=new String[owned.size()];for(int i=0;i<titles.length;i++)titles[i]=owned.get(i).title;
        new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("Выберите сообщество")).setItems(titles,(d,w)->presentFragment(new DevGramClanTagsActivity(ChatObject.isChannel(owned.get(w))?owned.get(w).id:-owned.get(w).id))).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show();
    }
    private void editTag(){
        EditTextBoldCursor field=new EditTextBoldCursor(getParentActivity());field.setText(tag);field.setTextSize(18);field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack,resourceProvider));field.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint,resourceProvider));field.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack,resourceProvider));field.setHighlightColor(Theme.getColor(Theme.key_chat_textSelectBackground,resourceProvider));field.setSingleLine(true);field.setHint(t("От 1 до 5 символов"));field.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(),Theme.getColor(Theme.key_dialogInputField,resourceProvider),Theme.getColor(Theme.key_dialogInputFieldActivated,resourceProvider)));field.setPadding(0,AndroidUtilities.dp(8),0,AndroidUtilities.dp(8));
        FrameLayout input=new FrameLayout(getParentActivity());input.addView(field,LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,48,Gravity.TOP,24,8,24,8));
        AlertDialog dialog=new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("Тег клана")).setView(input).setPositiveButton(t("Применить"),null).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).create();showDialog(dialog);
        field.setSelection(field.length());field.requestFocus();AndroidUtilities.runOnUIThread(()->AndroidUtilities.showKeyboard(field),100);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=field.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);boolean invalid=value.codePointCount(0,value.length())<1||value.codePointCount(0,value.length())>5;for(int i=0;i<value.length();i++)invalid|=Character.isWhitespace(value.charAt(i))||Character.isISOControl(value.charAt(i))||Character.getType(value.charAt(i))==Character.FORMAT;if(invalid){field.setError(t("Тег должен содержать от 1 до 5 символов без пробелов"));return;}tag=value;dirty=true;dialog.dismiss();list.adapter.update(true);});
    }
    private void pickEmoji(View source){
        if(getParentActivity()==null||source==null)return;
        if(emojiWindow!=null)emojiWindow.dismiss();
        SelectAnimatedEmojiDialog picker=new SelectAnimatedEmojiDialog(this,getParentActivity(),false,null,SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS,resourceProvider){
            @Override protected void onEmojiSelected(View view,Long documentId,TLRPC.Document document,TL_stars.TL_starGiftUnique gift,Integer until){
                long chosen=documentId!=null?documentId:document==null?0:document.id;
                if(chosen>0&&active()){emoji=chosen;dirty=true;list.adapter.update(true);}
                if(emojiWindow!=null)emojiWindow.dismiss();
            }
        };
        int width=Math.min(AndroidUtilities.dp(340),AndroidUtilities.displaySize.x-AndroidUtilities.dp(16));
        int height=Math.min(AndroidUtilities.dp(380),AndroidUtilities.displaySize.y-AndroidUtilities.statusBarHeight-AndroidUtilities.dp(32));
        emojiWindow=new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(picker,width,height);
        emojiWindow.setFocusable(true);emojiWindow.setOutsideTouchable(true);
        emojiWindow.setOnDismissListener(()->emojiWindow=null);
        int[] location=new int[2];source.getLocationInWindow(location);
        int left=Math.max(AndroidUtilities.dp(8),Math.min(location[0]+source.getWidth()-width,AndroidUtilities.displaySize.x-width-AndroidUtilities.dp(8)));
        emojiWindow.showAsDropDown(source,left-location[0],AndroidUtilities.dp(4));emojiWindow.dimBehind();
    }
    private void load(){
        if(!active()||loading)return;loading=true;int request=++generation;JSONObject body=DevGramStudioActivity.json("action",chat==0?"available":"status");
        if(chat!=0)try{body.put("chat",chat);}catch(Exception ignored){}else{JSONArray ids=new JSONArray();for(TLRPC.Chat c:getMessagesController().getChats().values())if(!ChatObject.isNotInChat(c)&&ids.length()<200)ids.put(ChatObject.isChannel(c)?c.id:-c.id);try{body.put("chats",ids);}catch(Exception ignored){}}
        list.adapter.update(true);DevGramProfilePolicy.request(currentAccount,"studio/clans",body,(result,error)->{if(!active()||request!=generation)return;loading=false;loaded=true;if(result==null){if(chat==0)showError(error);else{info=null;setup();}}else if(chat==0){clans=result.optJSONArray("clans");if(clans==null)clans=new JSONArray();selected=result.optLong("selected");}else{info=result;minMembers=result.optInt("minMembers",DevGramProfilePolicy.clanMinMembers());clan=result.optJSONObject("clan");bot=result.optString("botUsername");if(!dirty&&clan!=null){tag=clan.optString("tag");emoji=clan.optLong("emoji");enabled=clan.optBoolean("enabled");}}list.adapter.update(true);});
    }
    private void setup(){DevGramProfilePolicy.request(currentAccount,"studio/clans",DevGramStudioActivity.json("action","setup"),(r,e)->{if(active()&&r!=null){bot=r.optString("botUsername");minMembers=r.optInt("minMembers",DevGramProfilePolicy.clanMinMembers());list.adapter.update(true);}});}
    private void choose(JSONObject value){
        if(!active()||loading)return;loading=true;DevGramProfilePolicy.request(currentAccount,"studio/clans",DevGramStudioActivity.json("action","select","chat",value==null?0:value.optLong("chat")),(result,error)->{if(!active())return;loading=false;if(result==null){showError(error);return;}DevGramClanTags.accept(viewer,result.optJSONObject("tag"));getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces,MessagesController.UPDATE_MASK_NAME);DevGramProfilePolicy.changed();finishFragment();});
    }
    private void save(){
        if(!active()||loading)return;if(tag.isEmpty()||emoji==0){showError("bad_clan_tag");return;}loading=true;list.adapter.update(true);
        DevGramProfilePolicy.request(currentAccount,"studio/clans",DevGramStudioActivity.json("action","configure","chat",chat,"tag",tag,"emoji",emoji,"enabled",enabled),(result,error)->{if(!active())return;loading=false;if(result==null)showError(error);else{dirty=false;DevGramClanTags.invalidate(viewer);DevGramClanDirectory.invalidate(chat>0?-1000000000000L-chat:chat);DevGramProfilePolicy.changed();BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,t("Тег клана сохранён")).show();load();}list.adapter.update(true);});
    }
    private void showError(String error){if("cancelled".equals(error))return;if(error!=null&&error.startsWith("community_")){BulletinFactory.of(this).createErrorBulletin(DevGramCommunityBinding.errorText(error)).show();return;}String text="clan_owner_required".equals(error)?"Тег может настроить только владелец сообщества":"clan_members_required".equals(error)?"Для тега нужно не менее 100 подписчиков.":"clan_tag_taken".equals(error)?"Этот тег уже занят другим кланом":"clan_member_required".equals(error)?"Вступите в сообщество, чтобы выбрать его тег":"bad_clan_tag".equals(error)?"Заполните тег и выберите премиум-эмодзи":"Не удалось выполнить действие. Проверьте доступ и соединение.";BulletinFactory.of(this).createErrorBulletin("clan_members_required".equals(error)?String.format(java.util.Locale.ROOT,t("Для тега нужно не менее %d участников."),minMembers):t(text)).show();}
    public static void showTag(BaseFragment host,JSONObject tag){DevGramClanTagSheet.show(host,tag);}
    private static void openSource(BaseFragment host,JSONObject tag){String username=tag.optString("username");if(username.matches("[A-Za-z][A-Za-z0-9_]{3,31}"))Browser.openUrl(host.getContext(),"https://t.me/"+username);else if(host.getMessagesController().getChat(Math.abs(tag.optLong("chat")))!=null){Bundle args=new Bundle();args.putLong("chat_id",Math.abs(tag.optLong("chat")));host.presentFragment(new ChatActivity(args));}}
    @Override public void onResume(){super.onResume();load();}
    @Override public boolean onBackPressed(boolean invoked){if(!dirty)return super.onBackPressed(invoked);if(invoked)new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("Выйти без сохранения оформления?")).setPositiveButton(t("Выйти"),(d,w)->finishFragment()).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show();return false;}
    @Override public void onFragmentDestroy(){closed=true;generation++;DevGramProfilePolicy.unlisten(policyChanged);if(emojiWindow!=null)emojiWindow.dismiss();super.onFragmentDestroy();}
}

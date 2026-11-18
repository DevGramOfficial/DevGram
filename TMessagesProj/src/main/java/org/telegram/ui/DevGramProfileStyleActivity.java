package org.telegram.ui;

import android.content.Context;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.ArrayList;

/** Reference settings hierarchy. In-place editing is an explicit action within this screen. */
public class DevGramProfileStyleActivity extends DevGramSettingsFragment {
    private final DevGramStudioActivity.Draft draft;
    private final String page;
    private final boolean load;
    private UniversalRecyclerView list;
    private long owner;
    private boolean closed,saving,resetting;
    private final DevGramProfileExitPrompt exitPrompt = new DevGramProfileExitPrompt();
    private final Runnable live=this::onLive;
    private void onLive(){if((!DevGramProfileCustomization.enabled()||draft.subject<0&&!DevGramProfilePolicy.allowed(getUserConfig().getClientUserId(),"profile.communities")||owner!=0&&getUserConfig().getClientUserId()!=owner)&&!closed){dismissCurrentDialog();DevGramAccessNavigation.close(this);return;}if(list!=null)list.adapter.update(true);}

    public DevGramProfileStyleActivity(){this(null);}
    public DevGramProfileStyleActivity(DevGramStudioActivity.Draft draft){this("home",draft==null?new DevGramStudioActivity.Draft():draft,draft==null);}
    public static DevGramProfileStyleActivity music(DevGramStudioActivity.Draft draft){return new DevGramProfileStyleActivity("music",draft,false);}
    private DevGramProfileStyleActivity(String page,DevGramStudioActivity.Draft draft,boolean load){this.page=page;this.draft=draft;this.load=load;}
    private String t(String text){return DevGramLocale.text(text);}
    @Override public boolean onFragmentCreate(){return DevGramProfileCustomization.enabled()&&super.onFragmentCreate();}
    @Override public View createView(Context context){
        owner=getUserConfig().getClientUserId();actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle(t(page.equals("home")?"Кастомизация":page.equals("display")?"Отображение":page.equals("music")?"Музыка и плеер":page.equals("power")?"Экономия энергии":page.equals("updates")?"Обновления":"О разделе"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1&&onBackPressed(true))finishFragment();else if(id==100)save();}});
        if(page.equals("home"))actionBar.createMenu().addItem(100,R.drawable.ic_ab_done).setContentDescription(t("Сохранить оформление"));
        if(load){draft.document=DevGramProfileDocument.raw(draft.target(currentAccount));draft.theme=DevGramProfileStyle.raw(draft.target(currentAccount));JSONObject loadingDocument=draft.document;DevGramProfileDocument.load(currentAccount,draft.subject,(result,error)->{if(closed||getUserConfig().getClientUserId()!=owner)return;if(result!=null&&!draft.dirty&&draft.document==loadingDocument)draft.document=DevGramProfileDocument.editable(result.optJSONObject("document"));live.run();});}
        list=new UniversalRecyclerView(this,this::fill,this::click,null);fragmentView=new DevGramSettingsLayout(context,actionBar,list,resourceProvider);DevGramProfilePolicy.listen(live);return fragmentView;
    }
    private void row(ArrayList<UItem> items,int id,int icon,String title,String subtitle,String feature){items.add(UItem.asButtonWithSubtext(id,icon,DevGramAppearanceAccess.label(this,feature,t(title)),t(subtitle)));}
    private void toggle(ArrayList<UItem> items,int id,String key,String title,String description,boolean fallback){items.add(UItem.asCheck(id,t(title)).setChecked(MessagesController.getGlobalMainSettings().getBoolean(key,fallback)));if(!description.isEmpty())items.add(UItem.asShadow(t(description)));}
    private void musicToggle(ArrayList<UItem> items,int id,String key,String previous,String title,String description,String feature){items.add(UItem.asCheck(id,DevGramAppearanceAccess.label(this,feature,t(title))).setChecked(org.telegram.messenger.devgram.player.PlayerConfig.profileMusicPreference(key,previous)));items.add(UItem.asShadow(t(description)));}
    private void fill(ArrayList<UItem> items,UniversalAdapter adapter){
        switch(page){
            case "home":
                items.add(UItem.asHeader(t("Оформление")));
                row(items,1,R.drawable.msg_colors,"Оформить профиль","Фон, аватар, имя, карточки и расположение элементов",null);
                row(items,2,R.drawable.msg_photo_settings,"Отображение","Что видно в чатах и списках",null);
                row(items,3,R.drawable.msg_discussion,"Пузырёк сообщений","Цвета и текст ваших сообщений","chat.bubble");
                row(items,4,R.drawable.msg_gallery,"Бейджик профиля","Изображение на вашей карточке профиля","chat.badge");
                row(items,5,R.drawable.msg_gallery,"Музыка и плеер","Обложка и текст песни",null);
                row(items,6,R.drawable.msg_photo_settings,"Студия рамок","Слои, узлы и источники изображений","avatar.nodes");
                row(items,7,R.drawable.msg_photos,"Мастерская","Профили и рамки сообщества",null);
                row(items,10,R.drawable.msg_groups,"Тег клана","Выбрать тег сообщества рядом с именем","workshop.clans");
                row(items,8,R.drawable.msg_customize,"ИИ-помощник DevGram","Создание оформления по вашему описанию","profile.assistant");
                row(items,11,R.drawable.msg_notifications,"Уведомления","Активность, новости и служебные сообщения","profile.notices");
                row(items,9,R.drawable.msg_help,"Гайд по оформлению","Подсказки на настоящих элементах редактора",null);
                items.add(UItem.asShadow(null));items.add(UItem.asHeader(t("Производительность")));
                row(items,20,R.drawable.msg_autodelete,"Энергосбережение","Анимации рамок и нагрузка",null);
                row(items,21,R.drawable.msg_secret,"Данные и память","Кэш, лимиты и очистка",null);
                items.add(UItem.asShadow(null));items.add(UItem.asHeader(t("Приложение")));
                row(items,30,R.drawable.msg_language,"Язык","Язык настроек следует языку приложения",null);
                row(items,31,R.drawable.msg_download,"Обновления","Новые версии DevGram и история изменений",null);
                row(items,32,R.drawable.msg_info,"Помощь","Вопросы и ответы сообщества",null);
                if(DevGramBadges.hasDeveloperFeatures(owner))row(items,33,R.drawable.msg_customize,"Настройки разработчика","Доступ к функциям для всех и поддержавших",null);
                items.add(UItem.asShadow(null));row(items,34,R.drawable.msg_info,"О кастомизации DevGram","Канал, поддержка проекта и документация",null);
                items.add(UItem.asShadow(null));items.add(UItem.asButton(35,R.drawable.msg_delete,t(resetting?"Сбрасываю оформление…":"Сбросить по умолчанию")).red().setEnabled(!saving));
                if(draft.dirty)items.add(UItem.asShadow(t("Изменения в черновике. Нажмите галочку, чтобы сохранить оформление.")));
                break;
            case "display":
                row(items,205,R.drawable.msg_customize,"Быстрая настройка","Живой пример отображения рамок, бейджиков и сообщений",null);
                items.add(UItem.asHeader(t("В чатах и списках")));
                toggle(items,201,"devgramChatDecor","Рамки вне профиля","Рамки в чатах и списках пользователей",true);
                toggle(items,202,"devgramChatBadges","Фоновые бейджики","Изображения на карточках пользователей",true);
                toggle(items,203,"devgramChatBubbles","Пузырьки сообщений","Оформление ваших сообщений и сообщений собеседников",true);
                items.add(UItem.asHeader(t("Отклик")));toggle(items,204,"devgramCustomizationHaptics","Виброотклик","Отклик на нажатия и действия при кастомизации",true);break;
            case "music":
                items.add(UItem.asHeader(t("Плеер")));musicToggle(items,221,"devgramProfilePlayerCover","playerCover","Обложка под плеером","Цвета обложки в оформлении плеера","music.player");
                items.add(UItem.asHeader(t("Текст песни")));musicToggle(items,222,"devgramProfileLyricsMotion","lyricsMotion","Анимированный фон","Движение цветов обложки за текстом песни","music.lyrics.animated");break;
            case "power":
                items.add(UItem.asHeader(t("Статичные рамки")));
                toggle(items,211,"devgramBakeOutside","В чатах и списках","Останавливать анимацию рамок вне профиля",false);
                toggle(items,212,"devgramBakeProfile","В профилях","Останавливать анимацию рамки в профиле",false);
                items.add(UItem.asShadow(t("Снижает нагрузку и расход батареи. Оформление остаётся видимым.")));break;
            case "updates":
                row(items,301,R.drawable.msg_download,"Проверить обновления","Проверить новую версию DevGram",null);
                row(items,302,R.drawable.msg_info,"История изменений","Обновления в канале DevGram",null);break;
            default:
                row(items,401,R.drawable.devgram_channel,"Канал DevGram","Новости и обновления проекта",null);
                row(items,402,R.drawable.msg_input_like,"Поддержать DevGram","Помочь с сервером и развитием проекта",null);
                row(items,403,R.drawable.msg_info,"Документация","Возможности и инструкции DevGram",null);break;
        }
        items.add(UItem.asShadow(null));
    }
    private void child(String page){presentFragment(new DevGramProfileStyleActivity(page,draft,false));}
    private void click(UItem item,View view,int position,float x,float y){
        if(saving||closed||getUserConfig().getClientUserId()!=owner)return;
        switch(item.id){
            case 1:presentFragment(new DevGramProfileEditActivity(draft,false,currentAccount));break;
            case 2:child("display");break;
            case 3:presentFragment(new DevGramMessageBubbleActivity(draft));break;
            case 4:if(DevGramAppearanceAccess.check(this,DevGramAppearanceAccess.allowed(this,"chat.badge.animated")?"chat.badge.animated":"chat.badge"))presentFragment(new DevGramChatBadgeActivity(draft));break;
            case 5:child("music");break;
            case 6:if(DevGramAppearanceAccess.check(this,"avatar.nodes"))presentFragment(new DevGramFrameGraphActivity(draft));break;
            case 7:presentFragment(new DevGramWorkshopActivity(draft));break;
            case 11:DevGramNoticesSheet.show(this);break;
            case 10:if(DevGramAppearanceAccess.check(this,"workshop.clans"))presentFragment(new DevGramClanTagsActivity());break;
            case 8:DevGramProfileDesignAssistant.show(this,draft);break;
            case 9:presentFragment(new DevGramProfileEditActivity(draft,true,currentAccount));break;
            case 20:child("power");break;
            case 21:presentFragment(DevGramStudioActivity.forDraft("storage",draft));break;
            case 30:presentFragment(new LanguageSelectActivity());break;
            case 31:child("updates");break;
            case 32:Browser.openUrl(getContext(),"https://t.me/DevGramForum");break;
            case 33:presentFragment(new DevGramProfilePolicyActivity());break;
            case 34:child("about");break;
            case 35:confirmReset();break;
            case 205:DevGramQuickSetup.show(this);break;case 201:local("devgramChatDecor",true);break;case 202:local("devgramChatBadges",true);break;case 203:local("devgramChatBubbles",true);break;case 204:local("devgramCustomizationHaptics",true);break;
            case 221:if(DevGramAppearanceAccess.check(this,"music.player"))local("devgramProfilePlayerCover",org.telegram.messenger.devgram.player.PlayerConfig.profileMusicPreference("devgramProfilePlayerCover","playerCover"));break;case 222:if(DevGramAppearanceAccess.check(this,"music.lyrics.animated"))local("devgramProfileLyricsMotion",org.telegram.messenger.devgram.player.PlayerConfig.profileMusicPreference("devgramProfileLyricsMotion","lyricsMotion"));break;
            case 211:local("devgramBakeOutside",false);break;case 212:local("devgramBakeProfile",false);break;
            case 301:org.telegram.messenger.forkgram.AppUpdater.checkForDevGram(getParentActivity(),getParentActivity(),true);break;
            case 302:case 401:Browser.openUrl(getContext(),"https://t.me/DevGramNews");break;
            case 402:DevGramSupportSheet.show(this);break;
            case 403:Browser.openUrl(getContext(),"https://docs.devgram.space");break;
        }
    }
    private void local(String key,boolean fallback){android.content.SharedPreferences preferences=MessagesController.getGlobalMainSettings();preferences.edit().putBoolean(key,!preferences.getBoolean(key,fallback)).apply();DevGramProfilePolicy.changed();live.run();}
    private void confirmReset(){
        if(saving||closed||getUserConfig().getClientUserId()!=owner)return;
        AlertDialog.Builder dialog=new AlertDialog.Builder(getParentActivity(),resourceProvider);
        dialog.setTitle(t("Сбросить все настройки оформления?"));
        dialog.setMessage(t("Оформление профиля, собственные строки, сообщения и бейджик вернутся к значениям по умолчанию. Настройки отображения на этом устройстве также будут сброшены. Сохранённые проекты и работы мастерской останутся."));
        dialog.setNegativeButton(DevGramLocale.getString(R.string.Cancel),null);
        dialog.setPositiveButton(t("Сбросить"),(d,w)->resetAppearance());showDialog(dialog.create());
    }
    private void resetAppearance(){
        if(saving||closed||getUserConfig().getClientUserId()!=owner)return;
        saving=true;resetting=true;live.run();
        DevGramProfileDocument.reset(currentAccount,draft.subject,(result,error)->{
            if(closed||getUserConfig().getClientUserId()!=owner)return;
            saving=false;resetting=false;
            if(result==null||result.optJSONObject("document")==null){BulletinFactory.of(this).createErrorBulletin(t("Не удалось сбросить оформление")).show();live.run();return;}
            draft.document=DevGramProfileDocument.editable(result.optJSONObject("document"));
            draft.theme=new DevGramProfileStyle.Style(result.optJSONObject("theme"));draft.dirty=false;
            if(draft.onChanged!=null)draft.onChanged.run();if(draft.onSaved!=null)draft.onSaved.run();
            DevGramProfilePolicy.changed();live.run();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,t("Оформление сброшено")).show();
        });
    }
    private void save(){ save(null); }
    private void save(Runnable afterSave){
        if(saving||closed||getUserConfig().getClientUserId()!=owner)return;
        if(!draft.dirty){if(afterSave!=null)afterSave.run();return;}
        saving=true;
        Runnable document=()->DevGramProfileDocument.save(currentAccount,draft.subject,draft.document,draft.theme,(result,error)->{saving=false;if(closed||getUserConfig().getClientUserId()!=owner)return;if(result!=null){draft.document=DevGramProfileDocument.editable(result.optJSONObject("document"));draft.dirty=false;if(draft.onSaved!=null)draft.onSaved.run();BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,t("Оформление сохранено")).show();if(afterSave!=null)afterSave.run();}else BulletinFactory.of(this).createErrorBulletin(t("Не удалось сохранить оформление")).show();live.run();});
        if(draft.subject<0||draft.theme==null)document.run();else DevGramProfileStyle.save(currentAccount,draft.theme.effective(owner),(style,error)->{if(closed||getUserConfig().getClientUserId()!=owner)return;if(error==null){draft.theme=style;document.run();}else{saving=false;if(!closed)BulletinFactory.of(this).createErrorBulletin(t("Не удалось сохранить оформление")).show();}});
    }
    @Override public void onResume(){super.onResume();live.run();}
    @Override public boolean onBackPressed(boolean invoked){
        if(saving)return false;
        if(!page.equals("home")||!draft.dirty)return super.onBackPressed(invoked);
        if(invoked)exitPrompt.show(this,()->save(()->finishFragment()),()->{
            if(closed||saving||getUserConfig().getClientUserId()!=owner)return;
            draft.document=DevGramProfileDocument.copy(DevGramProfileDocument.raw(draft.target(currentAccount)));draft.theme=DevGramProfileStyle.raw(draft.target(currentAccount));draft.dirty=false;
            if(draft.onChanged!=null)draft.onChanged.run();finishFragment();
        });
        return false;
    }
    @Override public boolean canBeginSlide(){
        if(saving)return false;
        if(page.equals("home")&&draft.dirty){onBackPressed(true);return false;}
        return super.canBeginSlide();
    }
    @Override public void onFragmentDestroy(){closed=true;exitPrompt.dismiss();DevGramProfilePolicy.unlisten(live);super.onFragmentDestroy();}
}

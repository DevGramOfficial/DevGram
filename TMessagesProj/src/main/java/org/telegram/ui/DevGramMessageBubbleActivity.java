package org.telegram.ui;

import android.app.Activity;
import android.content.*;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.ArrayList;

/** The supplied plugin's message bubble sheet, with an independent publish action. */
public final class DevGramMessageBubbleActivity extends DevGramSettingsFragment {
    private static final int FONT=7394;private final DevGramStudioActivity.Draft draft;
    private UniversalRecyclerView list;private DevGramProfileBubblePreview preview;private long owner;private boolean closed,busy,uploading;private int generation;
    private final Runnable live=this::refresh;
    private void refresh(){if(closed)return;if(!DevGramProfileCustomization.enabled()){DevGramAccessNavigation.close(this);return;}if(preview!=null)preview.setDocument(draft.document);if(list!=null)list.adapter.update(true);}
    public DevGramMessageBubbleActivity(DevGramStudioActivity.Draft draft){this.draft=draft;}
    private String t(String text){return DevGramLocale.text(text);}private boolean allowed(String feature){return !closed&&getUserConfig().getClientUserId()==owner&&DevGramAppearanceAccess.allowed(this,feature);}
    @Override public View createView(Context context){owner=getUserConfig().getClientUserId();actionBar.setTitle(t("Пузырёк сообщений"));actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){public void onItemClick(int id){if(id==-1)finishFragment();}});preview=new DevGramProfileBubblePreview(context,owner);preview.setDocument(draft.document);list=new UniversalRecyclerView(this,this::fill,this::click,null);fragmentView=new DevGramSettingsLayout(context,actionBar,list,resourceProvider);DevGramProfilePolicy.listen(live);return fragmentView;}
    private void put(String key,Object value){String feature=DevGramProfileDocument.FEATURES.get(key);if(!allowed(feature))return;try{draft.document.put(key,value);draft.dirty=true;if(draft.onChanged!=null)draft.onChanged.run();preview.setDocument(draft.document);}catch(Exception ignored){}}
    private boolean enabled(){return draft.document.optInt("bubbleEnabled",draft.document.has("bubbleColor")?1:0)==1;}
    private void fill(ArrayList<UItem> items,UniversalAdapter adapter){
        Context context=getParentActivity();items.add(UItem.asCustom(preview,168));items.add(UItem.asCustom(DevGramReferenceEditor.section(context,"Пузырёк своих сообщений")));
        DevGramReferenceHeaderRow enabled=new DevGramReferenceHeaderRow(context,true);enabled.setTextAndCheck(DevGramAppearanceAccess.label(this,"chat.bubble",t("Свой цвет пузырька")),enabled(),true);enabled.setOnClickListener(v->{if(!DevGramAppearanceAccess.check(this,"chat.bubble"))return;boolean next=!enabled();if(next&&!draft.document.has("bubbleColor"))put("bubbleColor",-13938056&0xffffff);put("bubbleEnabled",next?1:0);live.run();});items.add(UItem.asCustom(enabled));
        if(enabled()){
            DevGramReferenceHeaderRow gradient=new DevGramReferenceHeaderRow(context,true);gradient.setTextAndCheck(DevGramAppearanceAccess.label(this,"chat.bubble",t("Градиент")),draft.document.optInt("bubbleGradient")==1,true);gradient.setOnClickListener(v->{if(DevGramAppearanceAccess.check(this,"chat.bubble")){put("bubbleGradient",draft.document.optInt("bubbleGradient")==1?0:1);live.run();}});items.add(UItem.asCustom(gradient));
            color(items,"bubbleColor",draft.document.optInt("bubbleGradient")==1?"Первый цвет":"Цвет пузырька",-13938056);
            if(draft.document.optInt("bubbleGradient")==1){color(items,"bubbleColor2","Второй цвет",-7459358);items.add(UItem.asCustom(DevGramReferenceEditor.slider(this,"Направление",draft.document.optInt("bubbleAngle"),0,360,"°",allowed("chat.bubble"),value->put("bubbleAngle",value),()->DevGramAppearanceAccess.check(this,"chat.bubble"))));}
            color(items,"textColor","Цвет текста",0xffffffff);
        }
        items.add(UItem.asCustom(DevGramReferenceEditor.section(context,"Шрифт сообщений")));String[] fonts={"Как в клиенте","Тонкий","Засечки","Моно","Курсив","Узкий","Свой"};int selected=draft.document.optInt("bubbleFont",draft.document.has("chatFontFile")?6:0);
        DevGramHeaderSegments font=new DevGramHeaderSegments(context,fonts,selected,index->{if(!DevGramAppearanceAccess.check(this,"chat.font"))return;put("bubbleFont",index);if(index!=6)put("chatFontFile",null);live.run();});font.bind(selected,allowed("chat.font"));items.add(UItem.asCustom(font));
        if(selected==6){JSONObject metadata=draft.document.optJSONObject("chatFontFile");DevGramReferenceHeaderRow file=new DevGramReferenceHeaderRow(context,false);file.setTextAndValue(DevGramAppearanceAccess.label(this,"chat.font",t(uploading?"Загружаю шрифт…":"Файл своего шрифта")),metadata==null?t("TTF или OTF до 5 МБ"):metadata.optString("name"),false);DevGramReferenceHeaderRow.decorateAction(file);file.setOnClickListener(v->chooseFont());items.add(UItem.asCustom(file));}
        items.add(UItem.asCustom(DevGramReferenceEditor.help(this,"Шрифт видят и другие.","Шрифт видят и другие. Свой файл передаётся вместе с настройками и загружается у собеседника при открытии переписки.")));
        items.add(UItem.asCustom(DevGramReferenceEditor.help(this,"Цвет ваших сообщений виден всем.","Цвет ваших сообщений виден всем. Кнопка ниже публикует только настройки сообщений; оформление профиля сохраняется отдельно.")));
        items.add(UItem.asCustom(DevGramReferenceEditor.primary(this,busy?"Публикую…":"Опубликовать",this::publish)));items.add(UItem.asShadow(null));
    }
    private void chooseFont(){if(closed||uploading||!DevGramAppearanceAccess.check(this,"chat.font"))return;try{getParentActivity().startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),FONT);}catch(Exception e){error("Не удалось открыть выбор файла");}}
    private void color(ArrayList<UItem> items,String key,String title,int fallback){
        int current=DevGramProfileBubbleColors.color(draft.document,key,fallback);
        DevGramProfileColorRow row=DevGramProfileColorRow.create(getParentActivity(),DevGramAppearanceAccess.label(this,"chat.bubble",t(title)),current,()->{
            if(!DevGramAppearanceAccess.check(this,"chat.bubble")||!allowed("chat.bubble"))return;
            DevGramProfileColorPicker.show(this,t(title),DevGramProfileBubbleColors.color(draft.document,key,fallback),fallback,value->{if(!allowed("chat.bubble"))return;try{draft.document.put(key,value&0xffffff);draft.document.put(DevGramProfileBubbleColors.alphaKey(key),value>>>24);draft.dirty=true;if(draft.onChanged!=null)draft.onChanged.run();preview.setDocument(draft.document);}catch(Exception ignored){}},live);
        });items.add(UItem.asCustom(row));
    }
    private void click(UItem item,View view,int position,float x,float y){if(closed||busy||getUserConfig().getClientUserId()!=owner)return;switch(item.id){case 1:if(DevGramAppearanceAccess.check(this,"chat.bubble")){boolean next=!enabled();if(next&&!draft.document.has("bubbleColor"))put("bubbleColor",0x5077cf);put("bubbleEnabled",next?1:0);live.run();}break;case 2:if(DevGramAppearanceAccess.check(this,"chat.bubble")){put("bubbleGradient",draft.document.optInt("bubbleGradient")==1?0:1);live.run();}break;case 3:if(!uploading&&DevGramAppearanceAccess.check(this,"chat.font"))try{getParentActivity().startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),FONT);}catch(Exception e){error("Не удалось открыть выбор файла");}break;case 4:publish();break;}}
    private void publish(){if(busy||!allowed("chat.bubble")&&!allowed("chat.font"))return;busy=true;live.run();DevGramProfileDocument.saveMessages(currentAccount,draft.subject,draft.document,(result,error)->{if(closed)return;busy=false;if(result==null){error("Не удалось сохранить настройки сообщений");live.run();return;}JSONObject saved=result.optJSONObject("document");if(saved!=null)try{for(String field:DevGramProfileDocument.MESSAGE_FIELDS){if(saved.has(field))draft.document.put(field,saved.opt(field));else draft.document.remove(field);}}catch(Exception ignored){}if(draft.onChanged!=null)draft.onChanged.run();BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,t("Настройки сообщений опубликованы")).show();live.run();});}
    @Override public void onActivityResultFragment(int requestCode,int resultCode,Intent data){if(requestCode!=FONT||resultCode!=Activity.RESULT_OK||data==null||data.getData()==null||!allowed("chat.font"))return;uploading=true;int request=++generation;live.run();DevGramProfileFonts.upload(currentAccount,data.getData(),"chatFontFile",(result,error)->{if(closed||request!=generation)return;uploading=false;if(result!=null&&allowed("chat.font")){put("chatFontFile",result.optJSONObject("font"));put("bubbleFont",6);}else error("Не удалось загрузить шрифт");live.run();});}
    private void error(String value){if(getParentActivity()!=null)BulletinFactory.of(this).createErrorBulletin(t(value)).show();}
    @Override public void onFragmentDestroy(){closed=true;generation++;DevGramProfilePolicy.unlisten(live);super.onFragmentDestroy();}
}

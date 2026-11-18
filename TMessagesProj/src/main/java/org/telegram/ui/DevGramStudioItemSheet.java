package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.*;
import java.util.ArrayList;
import java.util.function.IntConsumer;

/** Dedicated visual editors replace the old chains of numeric/hex input prompts. */
final class DevGramStudioItemSheet {
    private final BaseFragment host;
    private final DevGramStudioActivity.Draft draft;
    private final String kind;
    private final int index;
    private final JSONObject item;
    private final String feature;
    private final Context context;
    private final LinearLayout body;
    private final DevGramStudioPreview preview;
    private final Runnable changed;
    private final IntConsumer operation;
    private final ArrayList<Runnable> bindings=new ArrayList<>();
    private BottomSheet sheet;
    private final Runnable policy=this::refreshAccess;

    static void show(BaseFragment host,DevGramStudioActivity.Draft draft,String kind,int index,Runnable changed,IntConsumer operation){
        if(kind.equals("blocks")){JSONArray blocks=draft.document.optJSONArray("blocks");JSONObject row=blocks==null?null:blocks.optJSONObject(index);if(row!=null)DevGramRowEditActivity.show(host,draft,row,changed,()->operation.accept(2));return;}
        String feature=kind.equals("layers")?"avatar.layers":kind.equals("nodes")?"avatar.nodes":"profile.blocks";
        if(!DevGramAppearanceAccess.check(host,feature))return;
        JSONArray array=draft.document.optJSONArray(kind);JSONObject item=array==null?null:array.optJSONObject(index);if(item==null)return;
        if(kind.equals("blocks")){if(item.optString("type").equals("image"))feature="profile.blocks.media";else if(item.optString("type").equals("code"))feature="profile.blocks.code";if(!DevGramAppearanceAccess.check(host,feature))return;}
        new DevGramStudioItemSheet(host,draft,kind,index,item,feature,changed,operation).show();
    }
    private DevGramStudioItemSheet(BaseFragment host,DevGramStudioActivity.Draft draft,String kind,int index,JSONObject item,String feature,Runnable changed,IntConsumer operation){
        this.host=host;this.draft=draft;this.kind=kind;this.index=index;this.item=item;this.feature=feature;this.changed=changed;this.operation=operation;context=host.getParentActivity();body=DevGramStudioControls.column(context);
        preview=new DevGramStudioPreview(context,host.getCurrentAccount(),host.getUserConfig().getClientUserId());preview.setFrameOnly(!kind.equals("blocks"));preview.set(draft.document,draft.theme);body.addView(preview,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(220)));
    }
    private String t(String s){return DevGramLocale.text(s);}
    private void refreshAccess(){for(Runnable binding:bindings)binding.run();preview.set(draft.document,draft.theme);}
    private boolean access(){return DevGramAppearanceAccess.allowed(host,feature);}
    private void put(String key,Object value){if(!access())return;try{item.put(key,value);draft.dirty=true;preview.set(draft.document,draft.theme);if(draft.onChanged!=null)draft.onChanged.run();changed.run();}catch(Exception ignored){}}
    private void header(String text){TextView label=DevGramStudioControls.text(context,text,14,true,host.getResourceProvider());label.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(14),AndroidUtilities.dp(20),AndroidUtilities.dp(4));label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,host.getResourceProvider()));body.addView(label);}
    private void slider(String key,String title,int min,int max,int fallback,String unit){DevGramStudioControls.Slider slider=new DevGramStudioControls.Slider(context,host.getResourceProvider());Runnable bind=()->slider.bind(DevGramAppearanceAccess.label(host,feature,t(title)),item.optInt(key,fallback),min,max,unit,access(),n->put(key,n),()->{},()->DevGramAppearanceAccess.check(host,feature));bindings.add(bind);bind.run();body.addView(slider,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(82)));}
    private void field(String key,String title,int max,boolean multiline,String required){
        header(title);EditText edit=new EditText(context);edit.setTextSize(15);edit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,host.getResourceProvider()));edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,host.getResourceProvider()));edit.setSingleLine(!multiline);edit.setMaxLines(multiline?6:1);edit.setHint(t(title));edit.setText(item.optString(key));edit.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(8),AndroidUtilities.dp(20),AndroidUtilities.dp(8));edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});body.addView(edit);
        Runnable binding=()->{boolean allowed=access()&&(required==null||DevGramAppearanceAccess.allowed(host,required));edit.setEnabled(allowed);edit.setAlpha(allowed?1:.4f);};bindings.add(binding);binding.run();
        edit.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int a,int c,int f){}@Override public void afterTextChanged(Editable s){}@Override public void onTextChanged(CharSequence s,int a,int b,int c){if(required==null||DevGramAppearanceAccess.allowed(host,required))put(key,s.toString());}});
    }
    private void color(){header("Цвет");DevGramColorPickerView picker=new DevGramColorPickerView(context,host.getResourceProvider());picker.setColor(0xff000000|item.optInt("color",0x9fbcff));body.addView(DevGramStudioControls.swatches(context,item.optInt("color",0x9fbcff),host.getResourceProvider(),value->{if(access()){picker.setColor(0xff000000|value);put("color",value);}}));picker.setListener(value->put("color",value&0xffffff));body.addView(picker);}
    private void show(){
        String title=kind.equals("layers")?"Слой рамки":kind.equals("nodes")?"Узел":"Блок профиля";
        if(kind.equals("layers")){
            header("Оформление слоя");color();slider("alpha","Прозрачность",0,100,100,"%");slider("size","Размер",1,100,50,"%");slider("speed","Скорость",-100,100,20,"");slider("rotation","Поворот",-360,360,0,"°");
            if(item.optString("type").equals("sticker")){header("Наклейка");String[] symbols={"✦","★","♥","❄","☾","✿","⚡","☀","◆"};body.addView(DevGramStudioControls.pills(context,symbols,-1,host.getResourceProvider(),i->put("text",symbols[i])));field("text","Своя наклейка",12,false,null);}
        }else if(kind.equals("nodes")){
            header("Параметры узла");slider("value","Значение",-10000,10000,100,"");
            JSONArray layers=draft.document.optJSONArray("layers");int count=layers==null?0:layers.length();
            if(count>0){header("Привязанный слой");String[] labels=new String[count];for(int i=0;i<count;i++)labels[i]=t("Слой")+" "+(i+1);body.addView(DevGramStudioControls.pills(context,labels,item.optInt("layer"),host.getResourceProvider(),i->put("layer",i)));}
            inputs(0,"Первый вход");inputs(1,"Второй вход");
        }else{
            field("title","Заголовок",80,false,null);field("text","Текст",2000,true,null);
            String type=item.optString("type");if(!type.equals("text")&&!type.equals("code"))field("url","Ссылка",1024,false,type.equals("image")?"profile.blocks.media":null);
            slider("height","Высота",80,480,180," dp");
            rowDesign();
        }
        LinearLayout actions=new LinearLayout(context);String[] labels=kind.equals("nodes")?new String[]{"Удалить"}:new String[]{"Переместить выше","Переместить ниже","Удалить"};
        for(int i=0;i<labels.length;i++){int op=kind.equals("nodes")?2:i;TextView action=DevGramStudioControls.text(context,labels[i],13,true,host.getResourceProvider());action.setGravity(Gravity.CENTER);action.setTextColor(Theme.getColor(op==2?Theme.key_text_RedRegular:Theme.key_windowBackgroundWhiteBlueText,host.getResourceProvider()));action.setOnClickListener(v->{if(DevGramAppearanceAccess.check(host,feature)){sheet.dismiss();operation.accept(op);}});actions.addView(action,new LinearLayout.LayoutParams(0,AndroidUtilities.dp(56),1));}body.addView(actions);
        ScrollView scroll=new ScrollView(context){@Override protected void onMeasure(int w,int h){super.onMeasure(w,DevGramStudioControls.sheetHeightSpec(h,.78f));}};scroll.addView(body);
        sheet=new BottomSheet.Builder(context,true,host.getResourceProvider()).setTitle(t(title)+" "+(index+1)).setCustomView(scroll).create();DevGramProfilePolicy.listen(policy);host.showDialog(sheet,d->{DevGramProfilePolicy.unlisten(policy);preview.release();});
    }
    private void rowDesign(){TextView row=DevGramStudioControls.text(context,"Студия оформления блока",15,true,host.getResourceProvider());row.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(18),AndroidUtilities.dp(20),AndroidUtilities.dp(18));Runnable bind=()->row.setText(DevGramAppearanceAccess.label(host,"profile.blocks.code",t("Студия оформления блока")));bindings.add(bind);bind.run();row.setOnClickListener(v->{if(DevGramAppearanceAccess.check(host,"profile.blocks.code")){sheet.dismiss();host.presentFragment(new DevGramBlockDesignActivity(draft,item,changed));}});body.addView(row);}
    private void inputs(int slot,String title){
        header(title);JSONArray nodes=draft.document.optJSONArray("nodes");String[] options=new String[index+1];options[0]=t("Нет");int selected=0;JSONArray old=item.optJSONArray("inputs");
        for(int i=0;i<index;i++){int id=nodes.optJSONObject(i).optInt("id");options[i+1]="#"+id+" · "+nodes.optJSONObject(i).optString("type");if(old!=null&&old.length()>slot&&old.optInt(slot)==id)selected=i+1;}
        body.addView(DevGramStudioControls.pills(context,options,selected,host.getResourceProvider(),i->{
            JSONArray previous=item.optJSONArray("inputs"),next=new JSONArray();if(slot==1&&i>0&&(previous==null||previous.length()==0)){BulletinFactory.of(host).createErrorBulletin(t("Сначала выберите первый вход")).show();return;}
            if(slot==1&&previous!=null&&previous.length()>0)next.put(previous.optInt(0));if(i>0)next.put(nodes.optJSONObject(i-1).optInt("id"));if(slot==0&&i>0&&previous!=null&&previous.length()>1)next.put(previous.optInt(1));put("inputs",next);
        }));
    }
}

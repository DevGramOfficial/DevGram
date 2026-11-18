package org.telegram.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.*;

/** A reviewed snapshot, separate from saving the account's profile. */
final class DevGramWorkshopPublishSheet {
    private static String t(String s){return DevGramLocale.text(s);}
    static void show(BaseFragment host,DevGramStudioActivity.Draft draft,JSONObject existing){show(host,draft,existing,"profile");}
    static void showFrame(BaseFragment host,DevGramStudioActivity.Draft draft){show(host,draft,null,"frame");}
    private static void show(BaseFragment host,DevGramStudioActivity.Draft draft,JSONObject existing,String initialKind){
        if(host.getParentActivity()==null||!DevGramProfileCustomization.enabled()||!DevGramAppearanceAccess.check(host,"workshop.publish"))return;
        android.content.Context context=host.getParentActivity();LinearLayout body=DevGramStudioControls.column(context);body.setPadding(AndroidUtilities.dp(16),0,AndroidUtilities.dp(16),AndroidUtilities.dp(20));
        String[] kind={existing==null?initialKind:existing.optString("kind","profile")};
        String[] scopes={"profile","channel","group","forum"};String[] scope={existing==null?draft.scope:existing.optString("scope","profile")};
        LinearLayout scopeOptions=DevGramStudioControls.column(context);Runnable[] renderScope=new Runnable[1];renderScope[0]=()->{scopeOptions.removeAllViews();scopeOptions.addView(DevGramStudioControls.pills(context,new String[]{"Профиль","Канал","Группа","Форум"},Math.max(0,java.util.Arrays.asList(scopes).indexOf(scope[0])),host.getResourceProvider(),i->{scope[0]=scopes[i];renderScope[0].run();}));};renderScope[0].run();body.addView(scopeOptions);
        DevGramStudioPreview preview=new DevGramStudioPreview(context,host.getCurrentAccount(),draft.target(host.getCurrentAccount()));preview.setFrameOnly(kind[0].equals("frame"));preview.set(draft.document,draft.theme);body.addView(preview,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(250)));
        if(existing==null){
            LinearLayout types=DevGramStudioControls.column(context);body.addView(types);
            Runnable[] renderTypes=new Runnable[1];
            renderTypes[0]=()->{types.removeAllViews();types.addView(DevGramStudioControls.pills(context,new String[]{"Профиль","Рамка"},kind[0].equals("frame")?1:0,host.getResourceProvider(),i->{kind[0]=i==0?"profile":"frame";preview.setFrameOnly(i==1);renderTypes[0].run();}));};
            renderTypes[0].run();
        }
        EditText title=field(context,host,"Название работы",80);if(existing!=null)title.setText(existing.optString("title"));body.addView(title,LayoutHelper.createLinear(-1,54));
        EditText description=field(context,host,"Описание работы",2000);description.setSingleLine(false);description.setMaxLines(5);if(existing!=null)description.setText(existing.optString("description"));body.addView(description,LayoutHelper.createLinear(-1,-2,0,8,0,8));
        TextView note=DevGramStudioControls.text(context,"Работа появится в мастерской после проверки. При обновлении прежняя одобренная версия остаётся доступной до одобрения новой.",13,false,host.getResourceProvider());note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,host.getResourceProvider()));body.addView(note,LayoutHelper.createLinear(-1,-2,0,10,0,12));
        TextView status=DevGramStudioControls.text(context,"",13,false,host.getResourceProvider());body.addView(status);
        TextView send=DevGramStudioControls.text(context,existing==null?"Отправить на проверку":"Отправить обновление",15,true,host.getResourceProvider());send.setGravity(Gravity.CENTER);send.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText,host.getResourceProvider()));send.setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_featuredStickers_addButton,host.getResourceProvider()),0));body.addView(send,LayoutHelper.createLinear(-1,48,0,8,0,0));
        ScrollView scroll=new ScrollView(context){@Override protected void onMeasure(int w,int h){super.onMeasure(w,DevGramStudioControls.sheetHeightSpec(h,.82f));}};scroll.addView(body);
        BottomSheet sheet=new BottomSheet.Builder(context,true,host.getResourceProvider()).setTitle(t("Публикация в мастерской")).setCustomView(scroll).create();host.showDialog(sheet,d->preview.release());
        send.setOnClickListener(v->{
            if(title.getText().toString().trim().isEmpty()){title.requestFocus();title.setError(t("Введите название"));return;}
            if(kind[0].equals("frame")&&(draft.document.optJSONArray("layers")==null||draft.document.optJSONArray("layers").length()==0)&&!draft.document.has("frameGraph")){status.setText(t("Рамка пуста: добавьте хотя бы один слой в студии рамок."));return;}
            send.setEnabled(false);send.setText(t("Отправляю…"));
            JSONObject data=DevGramStudioActivity.json("action","publish","id",existing==null?0:existing.optLong("id"),"kind",kind[0],"scope",scope[0],"title",title.getText().toString().trim(),"description",description.getText().toString().trim(),"document",DevGramProfileDocument.editable(DevGramProfileDocument.effective(draft.document,host.getUserConfig().getClientUserId())),"theme",(draft.theme==null?DevGramProfileStyle.raw(host.getUserConfig().getClientUserId()):draft.theme).effective(host.getUserConfig().getClientUserId()).json());
            org.telegram.tgnet.TLRPC.User user=host.getUserConfig().getCurrentUser();
            if(user!=null)try{data.put("author",DevGramStudioActivity.json("name",UserObject.getUserName(user),"username",UserObject.getPublicUsername(user)==null?"":UserObject.getPublicUsername(user)));}catch(Exception ignored){}
            byte[] portrait=preview.portrait();
            long viewer=host.getUserConfig().getClientUserId();
            DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/textures/upload",DevGramStudioActivity.json("kind","workshop","data",android.util.Base64.encodeToString(portrait,android.util.Base64.NO_WRAP)),(uploaded,uploadError)->{
                if(!sheet.isShowing()||host.getUserConfig().getClientUserId()!=viewer||!DevGramProfileCustomization.enabled())return;
                if(uploaded==null){send.setEnabled(true);send.setText(t("Отправить на проверку"));status.setText(t("Не удалось сохранить предпросмотр"));return;}
                try{data.put("preview",uploaded.optString("source"));}catch(Exception ignored){}
                DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/workshop",data,(result,error)->{if(!sheet.isShowing())return;if(result==null){send.setEnabled(true);send.setText(t("Отправить на проверку"));status.setText(t("Не удалось выполнить действие. Проверьте доступ и соединение."));}else{send.setText(t("На проверке"));status.setText(t("Работа отправлена на проверку. Статус доступен в разделе «Мои работы»."));}});
            });
        });
    }
    private static EditText field(android.content.Context context,BaseFragment host,String hint,int max){EditText field=new EditText(context);field.setSingleLine(true);field.setTextSize(16);field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,host.getResourceProvider()));field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,host.getResourceProvider()));field.setHint(t(hint));field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});return field;}
}

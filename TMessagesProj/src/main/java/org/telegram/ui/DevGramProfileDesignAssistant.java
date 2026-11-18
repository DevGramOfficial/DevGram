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
import org.telegram.ui.Components.devgram.frame.FrameAssistantScope;
import org.telegram.ui.Components.devgram.frame.FrameAiPlan;
import org.telegram.ui.Components.devgram.frame.FrameNodeDoc;

/** Design-only assistant using the user's configured AI provider and server-validated proposals. */
final class DevGramProfileDesignAssistant {
    private static String t(String value){return DevGramLocale.text(value);}
    static void show(BaseFragment host,DevGramStudioActivity.Draft draft){show(host,draft,false);}
    static void showFrame(BaseFragment host,DevGramStudioActivity.Draft draft){show(host,draft,true);}
    private static void show(BaseFragment host,DevGramStudioActivity.Draft draft,boolean frameOnly){
        if(host.getParentActivity()==null || !DevGramProfileCustomization.enabled()||!DevGramAppearanceAccess.check(host,"profile.assistant"))return;
        if(frameOnly&&!DevGramAppearanceAccess.check(host,"avatar.nodes"))return;
        android.content.Context context=host.getParentActivity();
        final String createLabel=frameOnly?"Создать рамку":"Создать оформление";
        LinearLayout body=DevGramStudioControls.column(context);body.setPadding(AndroidUtilities.dp(18),AndroidUtilities.dp(8),AndroidUtilities.dp(18),AndroidUtilities.dp(20));
        TextView note=DevGramStudioControls.text(context,frameOnly?"Опишите рамку или изменения узлов. Помощнику передаются только ваш запрос и схема рамки; остальные настройки профиля не меняются.":"Опишите желаемое оформление. Помощник использует выбранный сервис ИИ DevGram; ему передаются ваш запрос и параметры оформления.",13,false,host.getResourceProvider());body.addView(note);
        EditText input=new EditText(context);input.setTextSize(16);input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,host.getResourceProvider()));input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,host.getResourceProvider()));input.setHint(t(frameOnly?"Например: тонкая неоновая рамка с искрами и плавным вращением":"Например: тёмный профиль, лавандовый градиент и тонкая светящаяся рамка"));input.setMinLines(2);input.setMaxLines(5);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});body.addView(input,new LinearLayout.LayoutParams(-1,-2));
        String[] ideas=frameOnly?new String[]{"Неоновая рамка","Искры вокруг аватара","Тонкое свечение"}:new String[]{"Спокойный минимализм","Северное сияние","Неоновая рамка"};body.addView(DevGramStudioControls.pills(context,ideas,-1,host.getResourceProvider(),i->input.setText(t(ideas[i]))));
        TextView response=DevGramStudioControls.text(context,"",14,false,host.getResourceProvider());response.setTextIsSelectable(true);response.setPadding(0,AndroidUtilities.dp(12),0,AndroidUtilities.dp(12));body.addView(response);
        DevGramStudioPreview preview=new DevGramStudioPreview(context,host.getCurrentAccount(),draft.target(host.getCurrentAccount()));preview.setFrameOnly(frameOnly);preview.setVisibility(View.GONE);body.addView(preview,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(260)));
        TextView apply=button(context,frameOnly?"Примерить рамку":"Примерить оформление",host);apply.setVisibility(View.GONE);body.addView(apply,LayoutHelper.createLinear(-1,48,0,8,0,8));
        TextView send=button(context,createLabel,host);body.addView(send,LayoutHelper.createLinear(-1,48,0,8,0,0));
        TextView services=DevGramStudioControls.text(context,"Настроить сервис ИИ",14,true,host.getResourceProvider());services.setGravity(Gravity.CENTER);services.setPadding(0,AndroidUtilities.dp(14),0,0);services.setOnClickListener(v->host.presentFragment(new DevGramAiServicesActivity()));body.addView(services);
        ScrollView scroll=new ScrollView(context){@Override protected void onMeasure(int w,int h){super.onMeasure(w,DevGramStudioControls.sheetHeightSpec(h,.8f));}};scroll.addView(body);
        BottomSheet sheet=new BottomSheet.Builder(context,true,host.getResourceProvider()).setTitle(t(frameOnly?"Помощник по узлам":"ИИ-помощник DevGram")).setCustomView(scroll).create();
        final FrameAiPlan[] actionPlan={null};final JSONObject[] proposal={null};final DevGramProfileStyle.Style[] theme={null};final boolean[] busy={false};
        final long assistantUid=host.getUserConfig().getClientUserId();
        final int assistantAccount=host.getCurrentAccount();
        final String historyScope=DevGramDesignAiHistory.scope(assistantAccount,assistantUid,draft.target(assistantAccount),frameOnly?DevGramDesignAiHistory.NODES:DevGramDesignAiHistory.PROFILE,null);
        final DevGramAiClient.ToolRequest[] running={null};final int[] generation={0};
        Runnable stop=()->{generation[0]++;if(running[0]!=null)running[0].cancel();running[0]=null;busy[0]=false;send.setEnabled(true);send.setText(t(createLabel));};
        Runnable access=()->{if(host.getCurrentAccount()!=assistantAccount||!DevGramProfileCustomization.enabled()||host.getUserConfig().getClientUserId()!=assistantUid||!DevGramProfilePolicy.allowed(assistantUid,"profile.assistant")||frameOnly&&!DevGramProfilePolicy.allowed(assistantUid,"avatar.nodes"))sheet.dismiss();};
        DevGramProfilePolicy.listen(access);host.showDialog(sheet,d->{stop.run();DevGramProfilePolicy.unlisten(access);preview.release();});
        apply.setOnClickListener(v->{
            if(proposal[0]==null||!DevGramAppearanceAccess.check(host,"profile.assistant"))return;
            if(frameOnly&&actionPlan[0]!=null&&host instanceof DevGramFrameGraphActivity){
                if(((DevGramFrameGraphActivity)host).playAi(actionPlan[0],null)){sheet.dismiss();return;}
                response.setText(t("Схема изменилась. Запросите новый вариант перед применением."));apply.setVisibility(View.GONE);return;
            }
            if(frameOnly){if(!DevGramAppearanceAccess.check(host,"avatar.nodes"))return;draft.document=FrameAssistantScope.merge(draft.document,proposal[0]);}else{draft.document=DevGramProfileDocument.restorePrivate(proposal[0],draft.document);draft.theme=theme[0];}draft.dirty=true;
            if(draft.onChanged!=null)draft.onChanged.run();
            response.setText(t(frameOnly?"Рамка применена к черновику. Остальные настройки профиля не изменены.":"Вариант применён к черновику. Галочка в профиле сохранит его, крестик отменит правку."));apply.setVisibility(View.GONE);
        });
        send.setOnClickListener(v->{
            if(busy[0]){stop.run();response.setText(t("Запрос остановлен"));return;}if(input.getText().toString().trim().isEmpty())return;
            if(!DevGramAppearanceAccess.check(host,"profile.assistant")||frameOnly&&!DevGramAppearanceAccess.check(host,"avatar.nodes"))return;
            if(!DevGramAiClient.isConfigured()){response.setText(t("Сначала выберите сервис ИИ в настройках DevGram."));return;}
            actionPlan[0]=null;proposal[0]=null;final int requestGeneration=++generation[0];final String userRequest=input.getText().toString().trim();busy[0]=true;send.setText(t("Остановить"));apply.setVisibility(View.GONE);response.setText("");
            JSONObject available=new JSONObject();long uid=host.getUserConfig().getClientUserId();
            try{for(java.util.Map.Entry<String,String> field:DevGramProfileDocument.FEATURES.entrySet())if((!frameOnly||FrameAssistantScope.field(field.getKey()))&&!java.util.Arrays.asList("media","nowPlaying","backgroundMsg","backgroundVideo").contains(field.getKey())&&DevGramProfilePolicy.allowed(uid,field.getValue()))available.put(field.getKey(),true);}catch(Exception ignored){}
            JSONObject features=new JSONObject();try{for(String feature:DevGramProfilePolicy.DEFAULTS.keySet())features.put(feature,DevGramProfilePolicy.allowed(uid,feature));}catch(Exception ignored){}
            String system="You are DevGram's profile design assistant. Answer in the user's app language. Return one JSON object with reply (helpful text), document (the full proposed public profile document), and optional theme. For explanations return reply only. Keep existing values and stable block IDs unless asked to change them. Use only the schema, available fields, and allowed feature IDs below. Never output account roles, credentials, network requests, arbitrary JavaScript or native code. Components, CSS and BlockScript use a bounded native drawing language. Never invent media asset IDs; preserve existing IDs or use zero to clear. Do not output read-only media, nowPlaying, backgroundMsg or backgroundVideo. No changes have been saved until the user explicitly saves the draft.\nSchema: "+(frameOnly?FrameAssistantScope.schema():schema())+"\nAvailable fields: "+available+"\nFeature access: "+features;
            if(frameOnly)system+="\nFRAME STUDIO MODE: only propose document.frameGraph (or legacy layers/nodes), never a profile theme or other profile fields. This is a frame-only draft. Use the typed node catalog and codec above; connect decorations to the Frame output. Prefer an actions object for edits on the existing graph. "+FrameAiPlan.schema()+" Return reply only for explanations.";
            String prompt="App language: "+LocaleController.getInstance().getCurrentLocaleInfo().shortName+"\nCurrent document: "+(frameOnly?FrameAssistantScope.frame(draft.document):DevGramProfileDocument.publicValue(DevGramProfileDocument.editable(draft.document)))+"\nCurrent theme: "+(frameOnly?new JSONObject():(draft.theme==null?DevGramProfileStyle.raw(uid):draft.theme).json())+"\nUser request: "+input.getText().toString().trim();
            running[0]=DevGramAiClient.generateTool(DevGramDesignAiHistory.history(historyScope),prompt,system,(answer,error)->AndroidUtilities.runOnUIThread(()->{
                if(!sheet.isShowing()||requestGeneration!=generation[0]||host.getCurrentAccount()!=assistantAccount||host.getUserConfig().getClientUserId()!=assistantUid)return;
                if(error!=null||answer==null){busy[0]=false;send.setEnabled(true);send.setText(t(createLabel));response.setText(t("Не удалось получить ответ. Проверьте настройки сервиса ИИ."));return;}
                JSONObject result;
                try{String raw=answer.trim();if(frameOnly&&raw.contains("<cpb_nodes>")){int a=raw.indexOf("<cpb_nodes>")+11,b=raw.indexOf("</cpb_nodes>",a);if(b>a)raw="{\"actions\":"+raw.substring(a,b)+"}";}if(raw.startsWith("```")){int first=raw.indexOf('\n'),last=raw.lastIndexOf("```");if(first>=0&&last>first)raw=raw.substring(first+1,last).trim();}if(raw.length()>1048576)throw new IllegalArgumentException();result=new JSONObject(raw);}
                catch(Exception ignored){busy[0]=false;send.setEnabled(true);send.setText(t(createLabel));response.setText(answer.length()>12000?answer.substring(0,12000):answer);DevGramDesignAiHistory.remember(historyScope,userRequest,answer);return;}
                response.setText(result.optString("reply"));
                if(frameOnly){JSONObject actions=result.optJSONObject("actions");if(actions==null&&result.has("add_node"))actions=result;
                    if(actions!=null)try{FrameAiPlan plan=FrameAiPlan.compile(FrameNodeDoc.parse(draft.document.optJSONObject("frameGraph")==null?"":draft.document.optJSONObject("frameGraph").toString()),actions);actionPlan[0]=plan;result.put("document",new JSONObject().put("frameGraph",new JSONObject(plan.after)));}catch(Exception invalid){busy[0]=false;send.setText(t(createLabel));response.setText(t("Помощник предложил недоступные или некорректные параметры. Уточните запрос и попробуйте снова."));return;}}

                if(result.optJSONObject("document")==null){DevGramDesignAiHistory.remember(historyScope,userRequest,result.optString("reply"));busy[0]=false;send.setEnabled(true);send.setText(t(createLabel));return;}
                JSONObject candidate=frameOnly?FrameAssistantScope.frame(result.optJSONObject("document")):result.optJSONObject("document");
                if(frameOnly&&!FrameAssistantScope.hasFrame(candidate)){busy[0]=false;send.setEnabled(true);send.setText(t(createLabel));return;}
                JSONObject request=DevGramStudioActivity.json("document",candidate,"assistant",true);
                try{if(!frameOnly&&result.optJSONObject("theme")!=null)request.put("theme",result.optJSONObject("theme"));}catch(Exception ignored){}
                DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/validate",request,(validated,failure)->{
                    if(!sheet.isShowing()||requestGeneration!=generation[0]||host.getCurrentAccount()!=assistantAccount||host.getUserConfig().getClientUserId()!=assistantUid)return;busy[0]=false;send.setEnabled(true);send.setText(t(createLabel));
                    if(validated==null){response.setText(t("Помощник предложил недоступные или некорректные параметры. Уточните запрос и попробуйте снова."));return;}
                    DevGramDesignAiHistory.remember(historyScope,userRequest,result.optString("reply"));proposal[0]=validated.optJSONObject("document");theme[0]=validated.optJSONObject("theme")==null?draft.theme:new DevGramProfileStyle.Style(validated.optJSONObject("theme"));
                    if(frameOnly&&!FrameAssistantScope.hasFrame(proposal[0])){response.setText(t("Помощник предложил недоступные или некорректные параметры. Уточните запрос и попробуйте снова."));return;}
                    if(actionPlan[0]!=null&&!FrameNodeDoc.parse(proposal[0].optJSONObject("frameGraph").toString()).encode().equals(actionPlan[0].after)){actionPlan[0]=null;response.setText(t("Помощник предложил недоступные или некорректные параметры. Уточните запрос и попробуйте снова."));return;}
                    if(actionPlan[0]!=null){FrameAiPlan plan=actionPlan[0];response.append("\n\n"+t("План действий")+": "+plan.steps.size()+" · "+t("Узлы")+" +"+plan.added+" / −"+plan.removed+" · "+t("Соединения")+" +"+plan.linked);apply.setText(t("Показать и применить действия"));}
                    preview.set(frameOnly?FrameAssistantScope.merge(draft.document,proposal[0]):proposal[0],frameOnly?draft.theme:theme[0]);preview.setVisibility(View.VISIBLE);apply.setVisibility(View.VISIBLE);
                });
            }));
        });
    }
    private static String schema(){try(java.io.InputStream stream=ApplicationLoader.applicationContext.getResources().openRawResource(R.raw.devgram_profile_schema)){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=stream.read(buffer))>0)bytes.write(buffer,0,count);return bytes.toString("UTF-8");}catch(Exception ignored){return "{}";}}
    private static TextView button(android.content.Context context,String label,BaseFragment host){TextView text=DevGramStudioControls.text(context,label,15,true,host.getResourceProvider());text.setGravity(Gravity.CENTER);text.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText,host.getResourceProvider()));text.setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_featuredStickers_addButton,host.getResourceProvider()),0));return text;}
}

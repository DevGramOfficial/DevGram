package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Color;
import android.os.Build;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import org.telegram.ui.Components.devgram.blocks.DevGramNativeBlockView;
import java.util.ArrayDeque;
import java.util.regex.Pattern;

/** Three source tabs, actual native preview, history and explicitly applied AI proposals. */
public final class DevGramBlockDesignActivity extends BaseFragment {
    private static final String[] FIELDS={"components","css","script"};
    private final DevGramStudioActivity.Draft draft;
    private final JSONObject target;
    private JSONObject working;
    private final Runnable changed;
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private final int[] selections=new int[3];
    private EditText editor;
    private TextView status;
    private DevGramNativeBlockView preview;
    private int tab,revision,aiGeneration;
    private boolean binding,closed,dirty,valid,busy;
    private long viewer;
    private DevGramAiClient.ToolRequest aiRequest;
    private void stopAi(){aiGeneration++;if(aiRequest!=null)aiRequest.cancel();aiRequest=null;busy=false;}
    private final Runnable validate=this::validateSource;
    private final Runnable access=this::accessChanged;
    public DevGramBlockDesignActivity(DevGramStudioActivity.Draft draft,JSONObject block,Runnable changed){this.draft=draft;target=block;working=DevGramProfileDocument.copy(block);this.changed=changed;}
    private String t(String value){return DevGramLocale.text(value);}
    private boolean allowed(){return !closed&&DevGramProfileCustomization.enabled()&&getUserConfig().getClientUserId()==viewer&&DevGramAppearanceAccess.allowed(this,"profile.blocks.code");}
    @Override public View createView(Context context){
        viewer=getUserConfig().getClientUserId();actionBar.setTitle(t("Дизайн блока"));actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)back();else if(id==1)apply();else if(id==2)ai();}});
        actionBar.createMenu().addItem(2,R.drawable.msg_customize).setContentDescription(t("ИИ-помощник DevGram"));actionBar.createMenu().addItem(1,R.drawable.ic_ab_done).setContentDescription(t("Применить"));
        FrameLayout root=new FrameLayout(context);root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray,resourceProvider));
        LinearLayout content=DevGramStudioControls.column(context);content.setPadding(0,AndroidUtilities.statusBarHeight+ActionBar.getCurrentActionBarHeight(),0,AndroidUtilities.navigationBarHeight);root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        preview=new DevGramNativeBlockView(context,viewer,resourceProvider);ScrollView previewScroll=new ScrollView(context);previewScroll.addView(preview,new ScrollView.LayoutParams(-1,-2));content.addView(previewScroll,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(170)));
        content.addView(DevGramStudioControls.pills(context,new String[]{"Компоненты","CSS","BlockScript"},0,resourceProvider,this::select));
        LinearLayout tools=new LinearLayout(context);String[] names={"Отменить действие","Вернуть действие","Пример","Гайд по коду"};for(int i=0;i<names.length;i++){int op=i;TextView button=DevGramStudioControls.text(context,names[i],12,true,resourceProvider);button.setGravity(Gravity.CENTER);button.setPadding(2,AndroidUtilities.dp(12),2,AndroidUtilities.dp(12));button.setOnClickListener(v->{if(op<2)history(op==1);else if(op==2)sample();else help();});tools.addView(button,new LinearLayout.LayoutParams(0,-2,1));}content.addView(tools);
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);editor=new EditText(context);editor.setGravity(Gravity.TOP|Gravity.LEFT);editor.setTextSize(13);editor.setTypeface(Typeface.MONOSPACE);editor.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,resourceProvider));editor.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resourceProvider));editor.setPadding(AndroidUtilities.dp(16),AndroidUtilities.dp(12),AndroidUtilities.dp(16),AndroidUtilities.dp(24));editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);editor.setHorizontallyScrolling(true);scroll.addView(editor,new ScrollView.LayoutParams(-1,-1));content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=DevGramStudioControls.text(context,"",12,false,resourceProvider);status.setPadding(AndroidUtilities.dp(16),AndroidUtilities.dp(8),AndroidUtilities.dp(16),AndroidUtilities.dp(8));content.addView(status);
        editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(binding)return;remember();try{working.put(FIELDS[tab],s.toString());}catch(Exception ignored){}dirty=true;valid=false;revision++;AndroidUtilities.cancelRunOnUIThread(validate);AndroidUtilities.runOnUIThread(validate,250);}public void afterTextChanged(Editable e){if(!binding)highlight(e);}});
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{int bottom=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime()).bottom;content.setPadding(0,AndroidUtilities.statusBarHeight+ActionBar.getCurrentActionBarHeight(),0,bottom);return insets;});
        actionBar.setAddToContainer(false);root.addView(actionBar,LayoutHelper.createFrame(-1,-2,Gravity.TOP));fragmentView=root;DevGramProfilePolicy.listen(access);bind();validateSource();return root;
    }
    private void accessChanged(){if(!allowed()){revision++;stopAi();if(!DevGramProfileCustomization.enabled()&&fragmentView!=null)DevGramAccessNavigation.close(this);if(editor!=null)editor.setEnabled(false);if(status!=null)status.setText(t("Оформление блока недоступно"));if(preview!=null)preview.invalidate();}else if(editor!=null){editor.setEnabled(true);validateSource();}}
    private void select(int next){if(editor==null)return;selections[tab]=editor.getSelectionStart();tab=next;bind();}
    private void bind(){binding=true;editor.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(tab==0?8192:16384)});editor.setText(working.optString(FIELDS[tab]));editor.setSelection(Math.max(0,Math.min(editor.length(),selections[tab])));highlight(editor.getText());binding=false;editor.setEnabled(allowed());}
    private void highlight(Editable text){for(ForegroundColorSpan span:text.getSpans(0,text.length(),ForegroundColorSpan.class))text.removeSpan(span);String source=text.toString();String[] patterns={"(?m)//.*$|/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->","\\b(?:let|if|else|end|repeat|rect|gradient|text|pill|image|progress|arrow|component)\\b","#[a-fA-F0-9]{3,8}\\b|\\$[A-Za-z_][A-Za-z0-9_]*","\"[^\"\\n]*\"|'[^'\\n]*'"};int[] colors={0xff82939d,0xff659ee8,0xffb589e2,0xff69a885};for(int i=0;i<patterns.length;i++){java.util.regex.Matcher matcher=Pattern.compile(patterns[i]).matcher(source);while(matcher.find())text.setSpan(new ForegroundColorSpan(colors[i]),matcher.start(),matcher.end(),Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);}}
    private void remember(){undo.push(working.toString());while(undo.size()>30)undo.removeLast();redo.clear();}
    private void history(boolean forward){if(!allowed())return;ArrayDeque<String> from=forward?redo:undo,to=forward?undo:redo;if(from.isEmpty())return;try{to.push(working.toString());working=new JSONObject(from.pop());dirty=true;revision++;bind();validateSource();}catch(Exception ignored){}}
    private void sample(){if(!allowed())return;remember();try{working.put(FIELDS[tab],DevGramNativeBlockView.sample(FIELDS[tab]));dirty=true;revision++;bind();validateSource();}catch(Exception ignored){}}
    private void validateSource(){
        if(!allowed())return;int request=++revision;valid=false;JSONObject candidate=DevGramProfileDocument.copy(working);status.setText(t("Проверка оформления…"));
        Utilities.globalQueue.postRunnable(()->{DevGramNativeBlockView.Diagnostic error=DevGramNativeBlockView.validate(candidate);AndroidUtilities.runOnUIThread(()->{if(!allowed()||request!=revision)return;valid=error==null;if(valid){status.setText(t("Оформление готово к применению"));preview.set(candidate);}else{String message=t("Ошибка оформления блока")+": "+error.section+(error.line>0?" · "+t("Строка")+" "+error.line:"");status.setText(message);}});});
    }
    private void apply(){if(!allowed()){DevGramAppearanceAccess.check(this,"profile.blocks.code");return;}if(!valid){validateSource();return;}try{for(String key:FIELDS)target.put(key,working.optString(key));draft.dirty=true;if(draft.onChanged!=null)draft.onChanged.run();changed.run();dirty=false;finishFragment();}catch(Exception ignored){}}
    private void back(){if(!dirty){finishFragment();return;}new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("Отменить правку блока?")).setMessage(t("Изменения в этом редакторе ещё не применены к черновику.")).setPositiveButton(t("Отменить правку"),(d,w)->finishFragment()).setNegativeButton(t("Продолжить редактирование"),null).show();}
    @Override public boolean onBackPressed(boolean invoked){if(invoked)back();return false;}
    private void help(){new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("Гайд по коду")).setMessage(t("Компоненты описывают элементы: rect, gradient, text, pill, image, progress и arrow. CSS располагает их по id и задаёт цвета, размеры, переходы и анимации. BlockScript добавляет вычисления, if/else, repeat и рисование. Координаты BlockScript — от 0 до 1000; CSS поддерживает dp и проценты. Кнопка «Пример» вставляет рабочий пример текущей вкладки. Изображения используют только известные источники $cover и $poster. Проверяйте результат в превью и нажмите галочку, чтобы применить его к черновику.")).setPositiveButton(t("Понятно"),null).show();}
    private void ai(){
        if(busy){stopAi();status.setText(t("Запрос остановлен"));return;}if(!allowed())return;if(!DevGramAiClient.isConfigured()){presentFragment(new DevGramAiServicesActivity());return;}
        EditText prompt=new EditText(getParentActivity());prompt.setTextColor(Theme.getColor(Theme.key_dialogTextBlack,resourceProvider));prompt.setHint(t("Опишите оформление блока"));prompt.setMinLines(2);prompt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
        new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("ИИ-помощник DevGram")).setView(prompt).setPositiveButton(t("Создать оформление"),(dialog,which)->{
            if(prompt.getText().toString().trim().isEmpty())return;busy=true;final int request=++aiGeneration;final String userRequest=prompt.getText().toString().trim();
            final String historyScope=DevGramDesignAiHistory.scope(currentAccount,viewer,draft.target(currentAccount),DevGramDesignAiHistory.BLOCKS,target.optString("id"));
            status.setText(t("Создаю оформление… Нажмите помощника, чтобы остановить."));
            String system="Return exactly one JSON object with string fields components, css, script, using DevGram's native block dialect. No HTML, JavaScript, external URLs or host APIs. Components max8192 chars, CSS and BlockScript max16384 each. Components grammar and CSS and BlockScript examples follow:\n"+DevGramNativeBlockView.sample("components")+"\n"+DevGramNativeBlockView.sample("css")+"\n"+DevGramNativeBlockView.sample("script");
            aiRequest=DevGramAiClient.generateTool(DevGramDesignAiHistory.history(historyScope),userRequest+"\nCurrent sources: "+working,system,(answer,error)->{
                JSONObject proposed=null;try{if(error==null){String raw=answer.trim();if(raw.startsWith("```")){int a=raw.indexOf('\n'),b=raw.lastIndexOf("```");if(a>=0&&b>a)raw=raw.substring(a+1,b);}if(raw.length()<=131072){JSONObject fields=new JSONObject(raw),candidate=DevGramProfileDocument.copy(working);for(String key:FIELDS){Object value=fields.opt(key);if(!(value instanceof String))throw new IllegalArgumentException();candidate.put(key,value);}if(DevGramNativeBlockView.validate(candidate)==null)proposed=candidate;}}}catch(Exception ignored){}final JSONObject result=proposed;
                AndroidUtilities.runOnUIThread(()->{if(!allowed()||request!=aiGeneration)return;busy=false;if(result==null){status.setText(t("Не удалось получить корректное оформление блока"));return;}DevGramDesignAiHistory.remember(historyScope,userRequest,answer);new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("Применить вариант ИИ?")).setMessage(t("Вариант изменит код в редакторе. В профиль он попадёт после сохранения черновика.")).setPositiveButton(t("Применить"),(d,w)->{if(!allowed())return;remember();working=result;dirty=true;revision++;bind();validateSource();}).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show();});
            });
        }).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show();
    }
    @Override public void onFragmentDestroy(){closed=true;revision++;stopAi();AndroidUtilities.cancelRunOnUIThread(validate);DevGramProfilePolicy.unlisten(access);if(preview!=null)preview.release();super.onFragmentDestroy();}
}

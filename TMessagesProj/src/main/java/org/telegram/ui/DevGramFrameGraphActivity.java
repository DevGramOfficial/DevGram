package org.telegram.ui;

import android.app.Activity;
import android.content.*;
import android.graphics.PointF;
import android.view.*;
import android.widget.*;
import android.text.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import org.telegram.ui.Components.devgram.frame.*;
import java.util.*;

/** Full native frame graph: catalog, typed wiring, every knob, sources and shared draft. */
public final class DevGramFrameGraphActivity extends DevGramSettingsFragment {
    private final DevGramStudioActivity.Draft draft;
    private FrameNodeDoc graph;
    private JSONObject bound;
    private DevGramTypedNodeCanvas canvas;
    private DevGramStudioPreview preview;
    private LinearLayout content,layersPanel,nodesPanel,layerStrip,layerSettings,tabs;
    private int tab,selectedLayer,openEffect;
    private TextView removeNodes,cloneNodes;
    private LinearLayout nodeTools;
    private View canvasSettingsButton;
    private boolean rebuildingLayers;
    private DevGramFrameGuide guide;
    private TextView status;
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private boolean closed,uploading;
    private String aiStatus;private int aiPlayback;private Runnable aiTick;private Runnable aiFinished;
    public void stopAiPlayback(){aiStatus=null;aiPlayback++;if(aiTick!=null)AndroidUtilities.cancelRunOnUIThread(aiTick);aiTick=null;if(canvas!=null)canvas.endAi();Runnable finished=aiFinished;aiFinished=null;if(status!=null){status.setOnClickListener(null);sync();}if(finished!=null)finished.run();}
    public boolean playAi(FrameAiPlan plan,Runnable finished){
        if(!allowed()||canvas==null||!graph.encode().equals(plan.before))return false;stopAiPlayback();openTab(1);aiFinished=finished;canvas.beginAi();status.setOnClickListener(v->stopAiPlayback());final int run=++aiPlayback;final int[] at={0};final String[] expected={plan.before};final boolean[] applying={false};
        undo.push(plan.before);while(undo.size()>30)undo.removeLast();redo.clear();
        aiTick=new Runnable(){public void run(){if(run!=aiPlayback)return;if(!allowed()||!graph.encode().equals(expected[0])){stopAiPlayback();return;}if(at[0]>=plan.steps.size()){stopAiPlayback();return;}FrameAiPlan.Step step=plan.steps.get(at[0]);aiStatus=t("ИИ-помощник DevGram")+" · "+(at[0]+1)+" / "+plan.steps.size()+" · "+t("Остановить");status.setText(aiStatus);try{if(!applying[0]){canvas.aimAi(step);applying[0]=true;AndroidUtilities.runOnUIThread(this,420);}else{step.apply(graph);expected[0]=graph.encode();commit(null);canvas.finishAiStep(step);at[0]++;applying[0]=false;AndroidUtilities.runOnUIThread(this,step.kind.equals("link")||step.kind.equals("move")?900:220);}}catch(Exception error){FileLog.e(error);stopAiPlayback();}}};AndroidUtilities.runOnUIThread(aiTick);return true;
    }
    private long owner;
    private int imageNode,imageKnob,imageGeneration;
    private int insetLeft,insetTop,insetRight,insetBottom;
    private final android.graphics.Rect tabBounds=new android.graphics.Rect();
    private final int[] rootPosition=new int[2];
    private final ViewTreeObserver.OnPreDrawListener safeArea=()->{updateInsets();return true;};
    private Runnable previousChanged;
    private boolean notifyingDraft;
    private final Runnable changedBridge=this::draftChanged;
    private void attachDraftBridge(){if(draft.onChanged!=changedBridge){previousChanged=draft.onChanged;draft.onChanged=changedBridge;}}
    private void draftChanged(){if(closed||notifyingDraft)return;notifyingDraft=true;try{if(previousChanged!=null&&previousChanged!=changedBridge)previousChanged.run();sync();}finally{notifyingDraft=false;}}
    private final Runnable live=()->{if(!allowed()){stopAiPlayback();if(guide!=null)guide.close();imageGeneration++;uploading=false;dismissCurrentDialog();if(canvas!=null)canvas.refreshAccess();if(!DevGramProfileCustomization.enabled()&&!closed)DevGramAccessNavigation.close(this);}sync();};
    private static final int PICK_IMAGE=7391;private DevGramFrameProjectsSheet projects;
    public DevGramFrameGraphActivity(DevGramStudioActivity.Draft draft){this.draft=draft;}
    private String t(String text){return DevGramLocale.text(text);}
    private boolean allowed(){return !closed&&getUserConfig().getClientUserId()==owner&&DevGramAppearanceAccess.allowed(this,"avatar.nodes");}
    private boolean access(){if(closed||getUserConfig().getClientUserId()!=owner)return false;return allowed()||DevGramAppearanceAccess.check(this,"avatar.nodes");}
    @Override public boolean onFragmentCreate(){return DevGramProfileCustomization.enabled()&&super.onFragmentCreate();}
    @Override public View createView(Context context){
        owner=getUserConfig().getClientUserId();projects=new DevGramFrameProjectsSheet(this,draft,resourceProvider);bound=draft.document.optJSONObject("frameGraph");graph=FrameNodeDoc.parse(bound==null?"":bound.toString());
        actionBar.setTitle(t("Студия рамок"));actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();else if(id==1)catalog();else if(id==2)DevGramProfileDesignAssistant.showFrame(DevGramFrameGraphActivity.this,draft);else if(id==3)more();}});
        actionBar.createMenu().addItem(2,R.drawable.msg_customize).setContentDescription(t("ИИ-помощник DevGram"));actionBar.createMenu().addItem(3,R.drawable.ic_ab_other).setContentDescription(t("Дополнительные действия"));
        FrameLayout root=new FrameLayout(context);root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray,resourceProvider));
        content=DevGramStudioControls.column(context);root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        tabs=new LinearLayout(context);String[] tabNames={"Слои","Ноды"};
        for(int i=0;i<tabNames.length;i++){int index=i;TextView button=chip(tabNames[i],()->openTab(index));tabs.addView(button,new LinearLayout.LayoutParams(0,AndroidUtilities.dp(44),1));}content.addView(tabs);
        layersPanel=DevGramStudioControls.column(context);content.addView(layersPanel,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout stage=new FrameLayout(context);
        preview=new DevGramStudioPreview(context,currentAccount,draft.target(currentAccount));preview.setFrameOnly(true);preview.set(draft.document,draft.theme);stage.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        layerStrip=DevGramStudioControls.column(context);layerStrip.setPadding(AndroidUtilities.dp(6),AndroidUtilities.dp(6),AndroidUtilities.dp(6),AndroidUtilities.dp(6));layerStrip.setBackground(DevGramStudioControls.surface(0xeef3f4f6,0x22000000));
        ScrollView stripScroll=new ScrollView(context);stripScroll.setVerticalScrollBarEnabled(false);stripScroll.addView(layerStrip);FrameLayout.LayoutParams stripParams=new FrameLayout.LayoutParams(AndroidUtilities.dp(52),-2,Gravity.TOP|Gravity.RIGHT);stripParams.setMargins(0,AndroidUtilities.dp(8),AndroidUtilities.dp(10),AndroidUtilities.dp(8));stage.addView(stripScroll,stripParams);layersPanel.addView(stage,new LinearLayout.LayoutParams(-1,0,1.1f));
        HorizontalScrollView shapes=new HorizontalScrollView(context);shapes.setHorizontalScrollBarEnabled(false);LinearLayout shapeRow=new LinearLayout(context);String[] shapeNames={"Круг","Скруглённый","Сквиркл","Квадрат","Шестиугольник","Пятиугольник","Звезда","Сердце","Цветок"};for(int i=0;i<shapeNames.length;i++){int shape=i;shapeRow.addView(chip(shapeNames[i],()->preview.setFramePreviewShape(shape)));}shapes.addView(shapeRow);layersPanel.addView(shapes);
        layerSettings=DevGramStudioControls.column(context);ScrollView settingsScroll=new ScrollView(context);settingsScroll.addView(layerSettings);layersPanel.addView(settingsScroll,new LinearLayout.LayoutParams(-1,0,1.4f));
        nodesPanel=DevGramStudioControls.column(context);content.addView(nodesPanel,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout toolbar=new LinearLayout(context);toolbar.setGravity(Gravity.CENTER_VERTICAL);HorizontalScrollView toolScroll=new HorizontalScrollView(context);toolScroll.setHorizontalScrollBarEnabled(false);LinearLayout tools=new LinearLayout(context);nodeTools=tools;
        tools.addView(chip("Добавить узел",this::catalog));removeNodes=chip("Убрать",()->new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("Узлы")).setMessage(t("Убрать выделенные узлы с полотна?")).setPositiveButton(t("Убрать"),(d,w)->canvas.delete()).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show());tools.addView(removeNodes);
        cloneNodes=chip("Клонировать",()->canvas.duplicate());tools.addView(cloneNodes);tools.addView(chip("Помощник",()->DevGramProfileDesignAssistant.showFrame(this,draft)));for(int i=0;i<tools.getChildCount();i++){LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(-2,-2);spacing.setMargins(AndroidUtilities.dp(4),0,AndroidUtilities.dp(4),0);tools.getChildAt(i).setLayoutParams(spacing);}toolScroll.addView(tools);toolbar.addView(toolScroll,new LinearLayout.LayoutParams(0,-2,1));
        ImageView gear=new ImageView(context);gear.setImageResource(R.drawable.msg_settings);gear.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resourceProvider));gear.setPadding(AndroidUtilities.dp(8),AndroidUtilities.dp(8),AndroidUtilities.dp(8),AndroidUtilities.dp(8));gear.setContentDescription(t("Настройки полотна"));gear.setOnClickListener(v->canvasSettings());canvasSettingsButton=gear;toolbar.addView(gear,new LinearLayout.LayoutParams(AndroidUtilities.dp(44),AndroidUtilities.dp(44)));nodesPanel.addView(toolbar);
        canvas=new DevGramTypedNodeCanvas(context,this,()->graph,this::commit,this::node,()->DevGramAppearanceAccess.check(this,"avatar.nodes"));canvas.setSelectionListener(this::selectionChanged);nodesPanel.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));applyCanvasSkin();
        status=DevGramStudioControls.text(context,"",12,false,resourceProvider);status.setPadding(AndroidUtilities.dp(16),AndroidUtilities.dp(9),AndroidUtilities.dp(16),AndroidUtilities.dp(9));content.addView(status);
        actionBar.setAddToContainer(false);root.addView(actionBar,LayoutHelper.createFrame(-1,-2,Gravity.TOP));fragmentView=root;
        root.getViewTreeObserver().addOnPreDrawListener(safeArea);attachDraftBridge();DevGramProfilePolicy.listen(live);onInsets(0,AndroidUtilities.statusBarHeight,0,Math.max(getBottomInset(),AndroidUtilities.navigationBarHeight));FrameNodeLayers.frame(graph);sync();openTab(0);return root;
    }
    @Override public void onInsets(int left,int top,int right,int bottom){super.onInsets(left,top,right,bottom);insetLeft=left;insetTop=top;insetRight=right;insetBottom=bottom;updateInsets();}
    private int tabInset(View view){
        if(view.getVisibility()!=View.VISIBLE||view.getAlpha()<.05f)return 0;
        if(view instanceof MainTabsLayout&&view.getGlobalVisibleRect(tabBounds)&&tabBounds.top>rootPosition[1]&&tabBounds.bottom-rootPosition[1]>=fragmentView.getHeight()-AndroidUtilities.dp(160))return Math.max(0,fragmentView.getHeight()-(tabBounds.top-rootPosition[1]));
        int value=0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)value=Math.max(value,tabInset(group.getChildAt(i)));}return value;
    }
    private void updateInsets(){
        if(content==null||fragmentView==null)return;int bottom=insetBottom;View root=fragmentView.getRootView();fragmentView.getLocationOnScreen(rootPosition);
        if(getParentActivity()!=null&&root==getParentActivity().getWindow().getDecorView())bottom=Math.max(bottom,tabInset(root));
        if(android.os.Build.VERSION.SDK_INT>=30&&fragmentView.getRootWindowInsets()!=null)bottom=Math.max(bottom,fragmentView.getRootWindowInsets().getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime()).bottom);
        int top=insetTop+ActionBar.getCurrentActionBarHeight();if(content.getPaddingLeft()!=insetLeft||content.getPaddingTop()!=top||content.getPaddingRight()!=insetRight||content.getPaddingBottom()!=bottom)content.setPadding(insetLeft,top,insetRight,bottom);
    }
    private void sync(){
        if(closed||preview==null)return;
        JSONObject current=draft.document.optJSONObject("frameGraph");if(current!=bound){bound=current;graph=FrameNodeDoc.parse(current==null?"":current.toString());if(canvas!=null){canvas.clearSelection();canvas.post(canvas::fit);}}
        preview.set(draft.document,draft.theme);status.setText(aiStatus!=null?aiStatus:uploading?t("Загружаю изображение…"):t("Узлы")+": "+graph.count()+" / 512  ·  "+t("Соединения")+": "+graph.wires().size());if(tab==0&&!rebuildingLayers)rebuildLayers();
    }
    private void commit(String before){
        if(!allowed())return;String after=graph.encode();if(after.equals(before))return;
        if(before!=null){undo.push(before);while(undo.size()>30)undo.removeLast();redo.clear();}
        try{bound=new JSONObject(after);draft.document.put("frameGraph",bound);draft.dirty=true;draftChanged();if(canvas!=null)canvas.graphChanged();}catch(Exception e){FileLog.e(e);}
    }
    private void history(boolean forward){if(!access())return;ArrayDeque<String> from=forward?redo:undo,to=forward?undo:redo;if(from.isEmpty())return;to.push(graph.encode());graph=FrameNodeDoc.parse(from.pop());commit(null);canvas.clearSelection();}
    private void more(){
        ItemOptions menu=ItemOptions.makeOptions(this,actionBar);
        menu.add(R.drawable.msg_saved,t("Мои проекты"),projects::show);menu.add(R.drawable.msg_add,t("Сохранить проект"),projects::saveCurrent);
        menu.add(R.drawable.msg_download,t("Импортировать рамку"),projects::importFrame);menu.add(R.drawable.msg_share,t("Экспортировать рамку"),this::exportFrame);
        menu.add(R.drawable.photo_undo2,t("Отменить действие"),()->history(false));menu.add(R.drawable.msg_retry,t("Вернуть действие"),()->history(true));menu.add(R.drawable.msg_select,t("Выделить несколько узлов"),()->{openTab(1);canvas.selectionMode();});
        menu.add(R.drawable.msg_copy,t("Дублировать выделенные"),()->canvas.duplicate());menu.add(R.drawable.msg_delete,t("Удалить выделенные"),()->canvas.delete());
        menu.add(R.drawable.msg_photos,t("Предпросмотр рамки"),()->openTab(0));menu.add(R.drawable.msg_settings,t("Настройки полотна"),this::canvasSettings);
        menu.add(R.drawable.msg_download,t("Ноды с сервера"),()->DevGramNodeStoreUi.show(this));
        menu.add(R.drawable.msg_info,t("Гайд по узлам"),this::startNodeGuide);
        menu.show();
    }
    private TextView row(LinearLayout body,String label,Runnable action){TextView row=DevGramStudioControls.text(getParentActivity(),label,15,true,resourceProvider);row.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(16),AndroidUtilities.dp(20),AndroidUtilities.dp(16));row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector,resourceProvider),2));row.setOnClickListener(v->{if(access())action.run();});body.addView(row);return row;}
    private void header(LinearLayout body,String label){TextView text=DevGramStudioControls.text(getParentActivity(),label,13,true,resourceProvider);text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resourceProvider));text.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(14),AndroidUtilities.dp(20),AndroidUtilities.dp(4));body.addView(text);}
    private BottomSheet sheet(String title,View body){ScrollView scroll=new ScrollView(getParentActivity()){@Override protected void onMeasure(int width,int height){super.onMeasure(width,DevGramStudioControls.sheetHeightSpec(height,.78f));}};scroll.addView(body);BottomSheet result=new BottomSheet.Builder(getParentActivity(),true,resourceProvider).setTitle(t(title)).setCustomView(scroll).create();showDialog(result);return result;}
    private TextView chip(String title,Runnable action){
        TextView view=DevGramStudioControls.text(getParentActivity(),title,13,true,resourceProvider);view.setGravity(Gravity.CENTER);view.setPadding(AndroidUtilities.dp(12),AndroidUtilities.dp(10),AndroidUtilities.dp(12),AndroidUtilities.dp(10));view.setMinHeight(AndroidUtilities.dp(44));view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector,resourceProvider),2));view.setOnClickListener(v->action.run());return view;
    }
    private void openTab(int value){
        tab=value;layersPanel.setVisibility(value==0?View.VISIBLE:View.GONE);nodesPanel.setVisibility(value==1?View.VISIBLE:View.GONE);
        for(int i=0;i<tabs.getChildCount();i++){TextView title=(TextView)tabs.getChildAt(i);boolean selected=i==value;title.setSelected(selected);title.setTextColor(Theme.getColor(selected?Theme.key_windowBackgroundWhiteBlueText:Theme.key_windowBackgroundWhiteGrayText,resourceProvider));title.setBackground(DevGramStudioControls.surface(selected?Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resourceProvider),.12f):0,0));}
        if(value==0)rebuildLayers();else canvas.post(canvas::fit);
        selectionChanged(canvas.selectedCount());
    }
    private void selectionChanged(int count){
        if(removeNodes==null)return;removeNodes.setVisibility(count>0?View.VISIBLE:View.GONE);cloneNodes.setVisibility(count>0?View.VISIBLE:View.GONE);removeNodes.setText(t("Убрать")+(count>1?" ("+count+")":""));cloneNodes.setText(t("Клонировать")+(count>1?" ("+count+")":""));
    }
    private void changeGraph(Runnable change){if(!access())return;String old=graph.encode();change.run();commit(old);}
    private void addLayer(int type){
        if(!access())return;String old=graph.encode();String source=type==11?"blank:bead":type==14?"blank:dot":type==10?"blank:rim":"blank:gem";int id=FrameNodeLayers.add(graph,type,source);if(id==0){graph=FrameNodeDoc.parse(old);error("На полотне больше нет места для узлов");return;}selectedLayer=id;openEffect=0;commit(old);rebuildLayers();
    }
    private void rebuildLayers(){
        if(layerSettings==null||rebuildingLayers)return;rebuildingLayers=true;
        try{
            layerSettings.removeAllViews();layerStrip.removeAllViews();List<Integer> layers=FrameNodeLayers.shapes(graph);
            if(!layers.contains(selectedLayer))selectedLayer=layers.isEmpty()?0:layers.get(layers.size()-1);
            for(int i=layers.size()-1;i>=0;i--){int id=layers.get(i);FrameNodeDoc.Node layer=graph.node(id);TextView choose=chip(Integer.toString(i+1),()->{selectedLayer=id;openEffect=0;rebuildLayers();});choose.setTextColor(id==selectedLayer?0xff1674cc:0xff30343b);choose.setContentDescription(t(FrameNodeType.name(layer.type)));choose.setBackground(DevGramStudioControls.surface(id==selectedLayer?0x333390ec:0,0));layerStrip.addView(choose,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(42)));}
            TextView add=chip("+",()->DevGramFrameNodeMenu.show(this,FrameNodeType::makesLayer,this::addLayer));add.setTextColor(0xff1674cc);add.setContentDescription(t("Добавить украшение"));layerStrip.addView(add,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(42)));
            if(layers.isEmpty()){
                header(layerSettings,"С чего начать");row(layerSettings,"Обод вокруг аватара",()->addLayer(10));row(layerSettings,"Бусины по краю",()->addLayer(11));row(layerSettings,"Обод с бусинами",()->{addLayer(10);addLayer(11);});row(layerSettings,"Камень сбоку",()->addLayer(12));row(layerSettings,"Искры",()->{if(!access())return;String old=graph.encode();int id=FrameNodeLayers.add(graph,14,"blank:dot");if(id<=0){graph=FrameNodeDoc.parse(old);error("На полотне больше нет места для узлов");return;}selectedLayer=id;int flow=FrameNodeLayers.attach(graph,id,26);graph.set(flow,0,1);for(int type:new int[]{20,21,25,24})FrameNodeLayers.attach(graph,id,type);commit(old);rebuildLayers();});
                header(layerSettings,"Или взять готовое");row(layerSettings,"Рамки из мастерской",()->{if(access())presentFragment(DevGramWorkshopActivity.forSection(draft,"home","frame"));});row(layerSettings,"Своя картинка с устройства",()->{addLayer(10);int image=FrameNodeLayers.image(graph,selectedLayer);if(image>0)sources(image,0);});
                TextView note=DevGramStudioControls.text(getParentActivity(),"Рамка ложится на контур аватара. Переключайте форму образца рядом с ним; личный профиль от этого не меняется.",13,false,resourceProvider);note.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(12),AndroidUtilities.dp(20),AndroidUtilities.dp(18));layerSettings.addView(note);return;
            }
            int id=selectedLayer;FrameNodeDoc.Node layer=graph.node(id);header(layerSettings,FrameNodeType.name(layer.type));int image=FrameNodeLayers.image(graph,id);if(image>0){DevGramReferenceHeaderRow source=new DevGramReferenceHeaderRow(getParentActivity(),false);source.setTextAndValue(t("Картинка"),t(FrameBlanks.label(graph.node(image).text(0)))+" ›",false);DevGramReferenceHeaderRow.decorateAction(source);source.setOnClickListener(v->{if(access())sources(image,0);});layerSettings.addView(source);}
            knobs(layerSettings,id);
            header(layerSettings,"Эффекты");for(int effect:FrameNodeLayers.edits(graph,id)){FrameNodeDoc.Node node=graph.node(effect);row(layerSettings,(openEffect==effect?"▾  ":"▸  ")+t(FrameNodeType.name(node.type)),()->{openEffect=openEffect==effect?0:effect;rebuildLayers();});if(openEffect==effect){knobs(layerSettings,effect);row(layerSettings,"Убрать эффект",()->changeGraph(()->{FrameNodeLayers.detach(graph,effect);openEffect=0;}));}}
            row(layerSettings,"Добавить эффект",()->DevGramFrameNodeMenu.show(this,FrameNodeType::editsLayer,type->changeGraph(()->{openEffect=FrameNodeLayers.attach(graph,id,type);})));
            header(layerSettings,"Это украшение");row(layerSettings,"Убрать под остальные",()->changeGraph(()->FrameNodeLayers.move(graph,id,true)));row(layerSettings,"Поднять поверх остальных",()->changeGraph(()->FrameNodeLayers.move(graph,id,false)));
            row(layerSettings,"Удалить украшение",()->new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("Удалить украшение?")).setPositiveButton(t("Удалить"),(d,w)->changeGraph(()->FrameNodeLayers.remove(graph,id))).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show());
        }finally{rebuildingLayers=false;}
    }
    private android.content.SharedPreferences canvasPrefs(){return MessagesController.getGlobalMainSettings();}
    private void applyCanvasSkin(){
        FrameNodeSkin custom=FrameNodeSkin.parse(canvasPrefs().getString("devgramFrameCanvasOwn",""));canvas.setSkin(FrameNodeSkin.of(canvasPrefs().getInt("devgramFrameCanvasSkin",0),custom,Theme.isCurrentThemeDark()));canvas.setWireStyle(canvasPrefs().getInt("devgramFrameWireStyle",0));canvas.setDodge(canvasPrefs().getBoolean("devgramFrameWireDodge",false));
    }
    private void choices(LinearLayout body,String[] labels,java.util.function.IntSupplier current,java.util.function.IntConsumer change){LinearLayout holder=DevGramStudioControls.column(getParentActivity());body.addView(holder);Runnable[] bind={null};bind[0]=()->{holder.removeAllViews();holder.addView(DevGramStudioControls.pills(getParentActivity(),labels,current.getAsInt(),resourceProvider,value->{if(!access())return;change.accept(value);bind[0].run();}));};bind[0].run();}
    private void canvasSettings(){
        LinearLayout body=DevGramStudioControls.column(getParentActivity());header(body,"Настройки полотна");
        row(body,"Уместить полотно",()->{dismissCurrentDialog();canvas.fit();});row(body,"Прибрать полотно",()->{dismissCurrentDialog();changeGraph(()->canvas.tidy());canvas.fit();});
        header(body,"Провода");String[] wireNames={"Кривые","Прямые","Угловые"};choices(body,wireNames,()->canvasPrefs().getInt("devgramFrameWireStyle",0),value->{canvasPrefs().edit().putInt("devgramFrameWireStyle",value).apply();applyCanvasSkin();});
        org.telegram.ui.Cells.TextCheckCell dodge=new org.telegram.ui.Cells.TextCheckCell(getParentActivity(),resourceProvider);dodge.setTextAndCheck(t("Обходить узлы"),canvasPrefs().getBoolean("devgramFrameWireDodge",false),true);dodge.setOnClickListener(v->{if(!access())return;boolean enabled=!canvasPrefs().getBoolean("devgramFrameWireDodge",false);canvasPrefs().edit().putBoolean("devgramFrameWireDodge",enabled).apply();dodge.setChecked(enabled);applyCanvasSkin();});body.addView(dodge);
        header(body,"Тема полотна");String[] names={"Как в клиенте","Тёмная","Светлая","Своя"};choices(body,names,()->canvasPrefs().getInt("devgramFrameCanvasSkin",0),value->{canvasPrefs().edit().putInt("devgramFrameCanvasSkin",value).apply();applyCanvasSkin();});row(body,"Своя тема полотна",()->{dismissCurrentDialog();canvasTheme();});row(body,"Гайд по узлам",()->{dismissCurrentDialog();startNodeGuide();});sheet("Настройки полотна",body);
    }
    private void canvasTheme(){
        if(!allowed()||getParentActivity()==null)return;
        LinearLayout body=DevGramStudioControls.column(getParentActivity());for(int i=0;i<FrameNodeSkin.ROLES;i++){int role=i;FrameNodeSkin stored=FrameNodeSkin.parse(canvasPrefs().getString("devgramFrameCanvasOwn",""));FrameNodeSkin current=stored==null?FrameNodeSkin.DARK:stored;TextView value=row(body,FrameNodeSkin.NAMES[i],()->{dismissCurrentDialog();FrameNodeSkin old=FrameNodeSkin.parse(canvasPrefs().getString("devgramFrameCanvasOwn",""));FrameNodeSkin initial=old==null?FrameNodeSkin.DARK:old;DevGramProfileColorPicker.show(this,t(FrameNodeSkin.NAMES[role]),initial.role(role),FrameNodeSkin.DARK.role(role),color->{if(!allowed())return;canvasPrefs().edit().putString("devgramFrameCanvasOwn",initial.with(role,color).encode()).putInt("devgramFrameCanvasSkin",3).apply();applyCanvasSkin();},this::canvasTheme);});android.graphics.drawable.ColorDrawable swatch=new android.graphics.drawable.ColorDrawable(current.role(i));swatch.setBounds(0,0,AndroidUtilities.dp(22),AndroidUtilities.dp(22));value.setCompoundDrawables(null,null,swatch,null);}
        row(body,"Сбросить тему полотна",()->{canvasPrefs().edit().putString("devgramFrameCanvasOwn",FrameNodeSkin.DARK.encode()).apply();applyCanvasSkin();dismissCurrentDialog();canvasTheme();});sheet("Своя тема полотна",body);
    }
    private void startNodeGuide(){
        if(!access())return;openTab(1);if(guide!=null)guide.close();ViewGroup tools=nodeTools;
        String[] descriptions={"Нажмите «Добавить узел»: каталог разделён на раскрывающиеся категории. Поиск находит узел по названию и описанию.","Узлы образуют цепочку: источник изображения → украшение → эффекты → вывод «Рамка». Без подключения к выводу украшение не появится в профиле.","Тяните провод от цветной точки выхода к совместимому входу. Входы параметров раскрываются через меню узла. Касание узла открывает его настройки.","Перемещайте полотно одним пальцем, меняйте масштаб двумя. Долгое нажатие открывает действия узла: клонирование, удаление и раскрытие параметров.","Здесь меняются тема и цвета полотна, вид проводов и расположение узлов. «Уместить полотно» возвращает всю схему в видимую область.","На вкладке «Слои» можно собирать рамку без ручной разводки: выбрать украшение, изменить источник, добавить эффекты и порядок слоёв."};
        View[] targets={tools.getChildAt(0),canvas,canvas,canvas,canvasSettingsButton,tabs.getChildAt(0)};
        guide=new DevGramFrameGuide(getParentActivity(),resourceProvider,targets,descriptions,content.getPaddingBottom());((FrameLayout)fragmentView).addView(guide,new FrameLayout.LayoutParams(-1,-1));
    }
    private void numberRow(LinearLayout body,int id,int knob){
        FrameNodeDoc.Node target=graph.node(id);FrameNodeType.Knob field=FrameNodeType.of(target.type).knobs[knob];
        body.addView(new DevGramFrameValueControl(getParentActivity(),resourceProvider,field.name,target.value(knob),this::allowed,value->set(id,knob,value),()->{
            if(!access()||graph.node(id)==null)return;EditText input=new EditText(getParentActivity());input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);input.setText(Integer.toString(graph.node(id).value(knob)));input.setSelectAllOnFocus(true);input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack,resourceProvider));input.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(8),AndroidUtilities.dp(20),AndroidUtilities.dp(8));new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t(field.name)).setView(input).setPositiveButton(t("Применить"),(d,w)->{try{set(id,knob,(int)Math.max(-1073741824L,Math.min(1073741823L,Long.parseLong(input.getText().toString().trim()))));if(tab==0)rebuildLayers();else node(id);}catch(NumberFormatException e){error("Введите целое число");}}).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show();
        }));
    }
    private void exportFrame(){
        if(!access())return;if(FrameNodeBuild.spec(graph).isEmpty()){error("Сначала соберите рамку из украшений");return;}LinearLayout body=DevGramStudioControls.column(getParentActivity());row(body,"Получить файл .dgframe",()->{dismissCurrentDialog();projects.exportCurrent();});row(body,"Опубликовать в мастерской",()->{dismissCurrentDialog();DevGramWorkshopPublishSheet.showFrame(this,draft);});sheet("Экспорт рамки",body);
    }
    private void catalog(){
        if(!access())return;
        DevGramFrameNodeMenu.show(this,type->(type&0x40000000)==0||DevGramNodePackages.installed(type),type->{if(!access())return;String old=graph.encode();PointF center=canvas.center();int id=graph.add(type,Math.round(center.x)-110,Math.round(center.y)-40);if(id==0){error("На полотне больше нет места для узлов");return;}if(type==1)graph.set(id,0,"blank:ring");commit(old);canvas.focus(id);});
    }
    private void set(int id,int knob,int value){if(!allowed())return;String old=graph.encode();graph.set(id,knob,value);boolean rebuilding=rebuildingLayers;rebuildingLayers=true;try{commit(old);}finally{rebuildingLayers=rebuilding;}}
    private void text(int id,int knob,String value){if(!allowed())return;String old=graph.encode();graph.set(id,knob,value);commit(old);}
    private void node(int id){
        if(!access())return;FrameNodeDoc.Node target=graph.node(id);if(target==null)return;FrameNodeType.Kind kind=FrameNodeType.of(target.type);LinearLayout body=DevGramStudioControls.column(getParentActivity());
        TextView hint=DevGramStudioControls.text(getParentActivity(),kind.hint,13,false,resourceProvider);hint.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(12),AndroidUtilities.dp(20),AndroidUtilities.dp(12));body.addView(hint);
        knobs(body,id);
        header(body,"Соединения");row(body,target.open?"Скрыть входы параметров":"Показать входы параметров",()->{String old=graph.encode();graph.toggle(id);commit(old);dismissCurrentDialog();node(id);});
        for(FrameNodeDoc.Wire connection:new ArrayList<>(graph.wires()))if(connection.to==id){FrameNodeDoc.Node from=graph.node(connection.from);String label="✕  #"+connection.from+" "+t(FrameNodeType.name(from.type))+" → "+(connection.pin>=100?t(kind.knobs[connection.pin-100].name):t(FrameNodeType.pinName(kind,connection.pin)));row(body,label,()->{String old=graph.encode();graph.cut(id,connection.pin);commit(old);dismissCurrentDialog();node(id);});}
        row(body,"Готово",this::dismissCurrentDialog);sheet(kind.name,body);
    }
    private void knobs(LinearLayout body,int id){
        FrameNodeDoc.Node target=graph.node(id);if(target==null)return;FrameNodeType.Kind kind=FrameNodeType.of(target.type);
        for(int k=0;k<kind.knobs();k++){
            final int knob=k;FrameNodeType.Knob field=kind.knobs[k];if(field.kind!=2&&field.kind!=4&&field.kind!=0)header(body,field.name);
            if(field.kind==1){String[] labels=new String[field.options.length];for(int i=0;i<labels.length;i++)labels[i]=t(field.options[i]);choices(body,labels,()->graph.node(id)==null?0:graph.node(id).value(knob)-field.min,i->set(id,knob,field.min+i));}
            else if(field.kind==2){
                final DevGramProfileColorRow[] row={null};row[0]=DevGramProfileColorRow.create(getParentActivity(),t(field.name),target.value(k),()->DevGramProfileColorPicker.show(this,t(field.name),graph.node(id).value(knob),field.start,value->{if(allowed()){set(id,knob,value);row[0].setColor(value);}},()->{if(allowed()&&getParentActivity()!=null){if(tab==0)rebuildLayers();else node(id);}}));body.addView(row[0]);
            }
            else if(field.kind==3){row(body,FrameBlanks.label(target.text(k)),()->sources(id,knob));}
            else if(field.kind==5){
                JSONArray blocks=draft.document.optJSONArray("blocks");ArrayList<String> identities=new ArrayList<>(),labels=new ArrayList<>();identities.add("");labels.add(t("Нет"));if(blocks!=null)for(int i=0;i<blocks.length();i++){JSONObject block=blocks.optJSONObject(i);if(block==null||DevGramProfileRowSpec.type(block)!=8)continue;identities.add(block.optString("id","legacy"+i));labels.add(block.optString("title").isEmpty()?t("Блок профиля")+" "+(i+1):block.optString("title"));}
                choices(body,labels.toArray(new String[0]),()->graph.node(id)==null?0:identities.indexOf(graph.node(id).text(knob)),i->text(id,knob,identities.get(i)));
            }else if(field.kind==6){TextView note=DevGramStudioControls.text(getParentActivity(),"Соедините этот вход с красным выходом живого числа.",13,false,resourceProvider);note.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(8),AndroidUtilities.dp(20),AndroidUtilities.dp(8));body.addView(note);}
            else if(field.kind==4){numberRow(body,id,knob);}
            else{
                int min=field.min,max=field.max;
                DevGramStudioControls.Slider slider=new DevGramStudioControls.Slider(getParentActivity(),resourceProvider);slider.bind(t(field.name),target.value(k),min,max,field.role==3?"°":"",true,value->set(id,knob,value),()->{},()->access());body.addView(slider,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(82)));
            }
        }
    }
    private void sources(int id,int knob){
        if(!access())return;LinearLayout body=DevGramStudioControls.column(getParentActivity());
        row(body,"Загрузить своё изображение",()->{imageNode=id;imageKnob=knob;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);try{getParentActivity().startActivityForResult(intent,PICK_IMAGE);}catch(Exception e){error("Не удалось открыть выбор файла");}});
        header(body,"Готовые источники");for(String source:FrameBlanks.ALL){LinearLayout row=new LinearLayout(getParentActivity());row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(10),AndroidUtilities.dp(20),AndroidUtilities.dp(10));ImageView image=new ImageView(getParentActivity());image.setImageBitmap(FrameBlanks.bitmap(source));image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);row.addView(image,new LinearLayout.LayoutParams(AndroidUtilities.dp(42),AndroidUtilities.dp(42)));TextView name=DevGramStudioControls.text(getParentActivity(),FrameBlanks.label(source),15,true,resourceProvider);row.addView(name,LayoutHelper.createLinear(-1,-2,16,0,0,0));row.setOnClickListener(v->{text(id,knob,source);dismissCurrentDialog();if(tab==0)rebuildLayers();else node(id);});body.addView(row);}
        sheet("Источник изображения",body);
    }
    @Override public void onActivityResultFragment(int requestCode,int resultCode,Intent data){
        if(projects!=null)projects.result(requestCode,resultCode,data);
        if(requestCode!=PICK_IMAGE||resultCode!=Activity.RESULT_OK||data==null||data.getData()==null||!allowed()||uploading)return;
        int id=imageNode,knob=imageKnob,generation=++imageGeneration;uploading=true;sync();
        DevGramProfileTextures.upload(currentAccount,data.getData(),(result,error)->{if(closed||generation!=imageGeneration)return;uploading=false;if(result!=null&&allowed()&&graph.node(id)!=null){text(id,knob,result.optString("source"));dismissCurrentDialog();if(tab==0)rebuildLayers();else node(id);}else error("Не удалось загрузить изображение рамки");sync();});
    }
    private void error(String message){if(getParentActivity()!=null)BulletinFactory.of(this).createErrorBulletin(t(message)).show();}
    private void info(String title,String message){AlertDialog.Builder dialog=new AlertDialog.Builder(getParentActivity(),resourceProvider);dialog.setTitle(t(title));dialog.setMessage(t(message));dialog.setPositiveButton(t("Понятно"),null);showDialog(dialog.create());}
    @Override public void onResume(){super.onResume();sync();}
    @Override public void onFragmentDestroy(){closed=true;stopAiPlayback();if(guide!=null)guide.close();if(projects!=null)projects.close();imageGeneration++;if(fragmentView!=null&&fragmentView.getViewTreeObserver().isAlive())fragmentView.getViewTreeObserver().removeOnPreDrawListener(safeArea);if(draft.onChanged==changedBridge)draft.onChanged=previousChanged;DevGramProfilePolicy.unlisten(live);if(preview!=null)preview.release();super.onFragmentDestroy();}
}

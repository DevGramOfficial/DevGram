package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.ArrayList;

/** Report inbox is independent of the publication approval queue. */
public final class DevGramWorkshopReportsActivity extends DevGramSettingsFragment {
    private UniversalRecyclerView list;
    private JSONArray cases=new JSONArray();
    private JSONObject selected;
    private boolean resolved,loading,closed;
    private int generation,next=-1;
    private long viewer;
    private String loadError;
    private boolean detailLoaded;
    private DevGramWorkshopPreviewView preview;
    private LinearLayout previewContainer;
    private View previewRow(){
        if(preview==null){previewContainer=null;return null;}
        if(previewContainer==null||previewContainer.getChildAt(0)!=preview)
            previewContainer=(LinearLayout)DevGramWorkshopReportUi.wrap(this,preview);
        return previewContainer;
    }
    public DevGramWorkshopReportsActivity(){this(null);}
    private DevGramWorkshopReportsActivity(JSONObject selected){this.selected=selected;}
    public static void open(org.telegram.ui.ActionBar.BaseFragment host){
        DevGramBadgesActivity.ensureAdminSignedIn(host,()->DevGramPlugins.fetchModerators(m->{
            if(host.getParentActivity()==null)return;
            if(DevGramPlugins.canSeeModeration(host.getUserConfig().getClientUserId())&&DevGramPlugins.isModerator())host.presentFragment(new DevGramWorkshopReportsActivity());
            else BulletinFactory.of(host).createErrorBulletin(DevGramLocale.text("Нет доступа к модерации")).show();
        }));
    }
    private String t(String s){return DevGramLocale.text(s);}
    private boolean active(){return !closed&&getParentActivity()!=null&&getUserConfig().getClientUserId()==viewer&&DevGramPlugins.canSeeModeration(viewer)&&DevGramPlugins.isModerator()&&DevGramProfileCustomization.enabled();}
    private final Runnable policy=()->{if(!active()){dismissCurrentDialog();DevGramAccessNavigation.close(this);}else if(list!=null)list.adapter.update(true);};
    @Override public boolean onFragmentCreate(){return DevGramPlugins.canSeeModeration(getUserConfig().getClientUserId())&&DevGramPlugins.isModerator()&&DevGramProfileCustomization.enabled()&&super.onFragmentCreate();}
    @Override public View createView(Context context){
        viewer=getUserConfig().getClientUserId();actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle(t("Жалобы мастерской"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        list=new UniversalRecyclerView(this,this::fill,this::click,null);fragmentView=new DevGramSettingsLayout(context,actionBar,list,resourceProvider);
        if(selected!=null&&selected.has("document")){preview=new DevGramWorkshopPreviewView(context,currentAccount,selected,false);preview.setActive(true);}
        DevGramProfilePolicy.listen(policy);return fragmentView;
    }
    private void fill(ArrayList<UItem> items,UniversalAdapter adapter){
        if(selected==null){
            items.add(UItem.asCustom(DevGramWorkshopReportUi.hero(this,"Модерация мастерской",t("Проверьте оформление, изучите обращения и сохраните решение."),cases.length())));
            items.add(UItem.asCustom(DevGramStudioControls.pills(getParentActivity(),new String[]{"Входящие","Рассмотренные"},resolved?1:0,resourceProvider,i->{generation++;loading=false;resolved=i==1;cases=new JSONArray();next=-1;load(false);}),52));
            for(int i=0;i<cases.length();i++){JSONObject value=cases.optJSONObject(i);if(value!=null)items.add(UItem.asCustom(reportCard(value)));}
            if(loadError!=null||cases.length()==0)items.add(UItem.asCustom(DevGramWorkshopReportUi.state(this,loadError!=null,loading,resolved?"Рассмотренных жалоб пока нет":"Входящих жалоб нет",loadError!=null?DevGramWorkshopReportUi.error(loadError):loading?"Получаем очередь жалоб и историю решений.":"Новые обращения появятся здесь. История рассмотренных жалоб сохраняется.",()->load(false))));
            else items.add(UItem.asButton(1,R.drawable.msg_retry,t("Обновить")).setEnabled(!loading));
            if(next>=0)items.add(UItem.asButton(2,t("Загрузить ещё")).setEnabled(!loading));
        }else{
            items.add(UItem.asCustom(DevGramWorkshopReportUi.hero(this,"Разбор жалоб",selected.optString("title"),selected.optInt("count"))));
            if(preview!=null)items.add(UItem.asCustom(previewRow(),300));
            JSONObject author=selected.optJSONObject("author");LinearLayout summary=DevGramWorkshopReportUi.card(this);summary.addView(DevGramWorkshopReportUi.text(this,selected.optString("title"),20,true));summary.addView(DevGramWorkshopReportUi.muted(this,t("Автор")+": "+(author==null?String.valueOf(selected.optLong("uid")):author.optString("name")),13),LayoutHelper.createLinear(-1,-2,0,7,0,0));
            if(selected.optString("state").equals("approved"))summary.addView(DevGramWorkshopReportUi.button(this,"Открыть работу",()->presentFragment(DevGramWorkshopActivity.openWork(selected.optLong("id")))));items.add(UItem.asCustom(DevGramWorkshopReportUi.wrap(this,summary)));
            JSONArray reports=selected.optJSONArray("reports");boolean open=false;
            if(reports!=null)for(int i=0;i<reports.length();i++){JSONObject report=reports.optJSONObject(i);if(report==null)continue;boolean pending=report.optString("decision").isEmpty();open|=pending;LinearLayout card=DevGramWorkshopReportUi.card(this);card.addView(DevGramWorkshopReportUi.chip(this,pending?"На рассмотрении":report.optString("decision").equals("dismiss")?"Жалоба отклонена":"Жалоба подтверждена",pending?0:1),LayoutHelper.createLinear(-2,-2));card.addView(DevGramWorkshopReportUi.evidence(this,"Причина обращения",report.optString("text")),LayoutHelper.createLinear(-1,-2,0,12,0,0));card.addView(DevGramWorkshopReportUi.muted(this,reportMetadata(report),12),LayoutHelper.createLinear(-1,-2,0,10,0,0));if(!report.optString("reason").isEmpty())card.addView(DevGramWorkshopReportUi.evidence(this,"Ответ модератора",report.optString("reason")),LayoutHelper.createLinear(-1,-2,0,12,0,0));items.add(UItem.asCustom(DevGramWorkshopReportUi.wrap(this,card)));}
            if(loadError!=null)items.add(UItem.asCustom(DevGramWorkshopReportUi.state(this,true,false,"",DevGramWorkshopReportUi.error(loadError),this::loadDetail)));
            if(open&&detailLoaded){items.add(UItem.asHeader(t("Решение")));items.add(UItem.asCustom(decision("dismiss","Отклонить жалобы","Нарушение не подтвердилось",false)));items.add(UItem.asCustom(decision("hide","Скрыть из мастерской","Работу можно будет отправить повторно",true)));items.add(UItem.asCustom(decision("block","Скрыть и заблокировать","Запретить повторную публикацию этого оформления",true)));items.add(UItem.asShadow(t("Решение и пояснение увидит пользователь.")));}
        }
        items.add(UItem.asShadow(null));
    }
    private View reportCard(JSONObject value){
        LinearLayout card=DevGramWorkshopReportUi.card(this);card.addView(DevGramWorkshopReportUi.chip(this,resolved?"Рассмотрено":"Требует проверки",resolved?1:0),LayoutHelper.createLinear(-2,-2));card.addView(DevGramWorkshopReportUi.text(this,value.optString("title"),18,true),LayoutHelper.createLinear(-1,-2,0,10,0,0));
        JSONArray reports=value.optJSONArray("reports");JSONObject recent=reports==null?null:reports.optJSONObject(0);TextView reason=DevGramWorkshopReportUi.text(this,recent==null?"":recent.optString("text"),15,false);reason.setMaxLines(3);reason.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(reason,LayoutHelper.createLinear(-1,-2,0,10,0,0));card.addView(DevGramWorkshopReportUi.muted(this,t("Жалоб")+": "+value.optInt("count")+" · "+DevGramWorkshopReportUi.date(recent==null?0:recent.optLong("created")),12),LayoutHelper.createLinear(-1,-2,0,10,0,0));card.addView(DevGramWorkshopReportUi.button(this,resolved?"Посмотреть решение":"Рассмотреть жалобы",()->presentFragment(new DevGramWorkshopReportsActivity(value))));card.setOnClickListener(v->{if(active()&&!loading)presentFragment(new DevGramWorkshopReportsActivity(value));});return DevGramWorkshopReportUi.wrap(this,card);
    }
    private View decision(String code,String title,String subtitle,boolean destructive){LinearLayout card=DevGramWorkshopReportUi.card(this);TextView heading=DevGramWorkshopReportUi.text(this,t(title),16,true);heading.setTextColor(Theme.getColor(destructive?Theme.key_text_RedRegular:Theme.key_windowBackgroundWhiteBlueText,resourceProvider));card.addView(heading);card.addView(DevGramWorkshopReportUi.muted(this,t(subtitle),13),LayoutHelper.createLinear(-1,-2,0,6,0,0));card.setAlpha(loading?.45f:1);card.setOnClickListener(v->{if(!loading)decide(selected,code);});return DevGramWorkshopReportUi.wrap(this,card);}
    private void loadDetail(){
        if(!active()||loading||selected==null)return;loading=true;loadError=null;int request=++generation;list.adapter.update(true);
        DevGramWorkshopModerationApi.request(currentAccount,DevGramStudioActivity.json("action","case","work",selected.optLong("id"),"state",selected.optString("reportState","open")),(result,error)->{if(!active()||request!=generation)return;loading=false;boolean firstLoad=!detailLoaded;if(result!=null&&result.optJSONObject("case")!=null){selected=result.optJSONObject("case");detailLoaded=true;if(preview!=null)preview.release();preview=new DevGramWorkshopPreviewView(getParentActivity(),currentAccount,selected,false);preview.setActive(true);}else loadError=error==null?"network":error;list.adapter.update(true);if(firstLoad&&detailLoaded)list.scrollToPosition(0);});
    }
    private String reportMetadata(JSONObject report){java.util.ArrayList<String> parts=new java.util.ArrayList<>();org.telegram.tgnet.TLRPC.User person=getMessagesController().getUser(report.optLong("uid"));parts.add(t("Отправитель: ")+(person==null?"ID "+report.optLong("uid"):UserObject.getUserName(person)));if(report.optInt("version")>0)parts.add(t("Версия")+" "+report.optInt("version"));String date=DevGramWorkshopReportUi.date(report.optLong("created"));if(!date.isEmpty())parts.add(date);return android.text.TextUtils.join(" · ",parts);}
    private void decide(JSONObject value,String decision){
        if(!active()||loading)return;DevGramPluginUi.showTextInput(this,t(decision.equals("dismiss")?"Отклонить жалобы":decision.equals("block")?"Скрыть и заблокировать":"Скрыть из мастерской"),t("Решение и пояснение увидит пользователь."),t("Коротко объясните решение"),"",t("Подтвердить"),!decision.equals("dismiss"),why->resolve(value,decision,why));
    }
    private void resolve(JSONObject value,String decision,String why){
        if(!active()||loading)return;JSONArray versions=new JSONArray(),reports=value.optJSONArray("reports");if(reports!=null)for(int i=0;i<reports.length();i++){JSONObject r=reports.optJSONObject(i);if(r!=null&&r.optString("decision").isEmpty())versions.put(DevGramStudioActivity.json("uid",r.optLong("uid"),"revision",r.optInt("revision")));}
        loading=true;list.adapter.update(true);DevGramWorkshopModerationApi.request(currentAccount,DevGramStudioActivity.json("action","resolve","work",value.optLong("id"),"reports",versions,"decision",decision,"reason",why),(result,error)->{if(!active())return;loading=false;if(result==null){BulletinFactory.of(this).createErrorBulletin(t("Не удалось сохранить решение. Обновите жалобы и повторите.")).show();list.adapter.update(true);}else if(selected!=null)finishFragment();else{cases=new JSONArray();load(false);}});
    }
    private void click(UItem item,View view,int position,float x,float y){
        if(!active()||loading)return;
        if(selected==null){if(item.id>=100){JSONObject c=cases.optJSONObject(item.id-100);if(c!=null)presentFragment(new DevGramWorkshopReportsActivity(c));}else if(item.id==1)load(false);else if(item.id==2)load(true);}
        else if(item.id==3)presentFragment(DevGramWorkshopActivity.openWork(selected.optLong("id")));
        else if(item.id==4||item.id==5)decide(selected,item.id==5?"hide":"dismiss");
    }
    private void load(boolean more){
        if(!active()||loading)return;loading=true;loadError=null;int request=++generation;int offset=more?next:0;if(list!=null)list.adapter.update(true);
        DevGramWorkshopModerationApi.request(currentAccount,DevGramStudioActivity.json("action","list","state",resolved?"resolved":"open","offset",Math.max(0,offset)),(result,error)->{if(!active()||request!=generation)return;loading=false;if(result==null){loadError=error==null?"network":error;}else{if(!more)cases=new JSONArray();JSONArray found=result.optJSONArray("cases");if(found!=null)for(int i=0;i<found.length();i++)cases.put(found.optJSONObject(i));next=result.isNull("next")?-1:result.optInt("next",-1);}list.adapter.update(true);});
    }
    @Override public void onResume(){super.onResume();if(selected==null)load(false);else loadDetail();}
    @Override public void onFragmentDestroy(){closed=true;generation++;DevGramProfilePolicy.unlisten(policy);if(preview!=null)preview.release();previewContainer=null;super.onFragmentDestroy();}
}

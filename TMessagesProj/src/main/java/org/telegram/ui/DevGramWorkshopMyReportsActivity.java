package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.ArrayList;

/** Only the signed-in reporter can read their own report decisions. */
public final class DevGramWorkshopMyReportsActivity extends DevGramSettingsFragment {
    private UniversalRecyclerView list;private JSONArray reports=new JSONArray();private boolean closed,loading;private long viewer;private int filter,generation;private String error;
    private String t(String s){return DevGramLocale.text(s);}
    private final Runnable policy=this::changed;
    private void changed(){if(!DevGramProfileCustomization.enabled()){dismissCurrentDialog();DevGramAccessNavigation.close(this);}else if(list!=null)list.adapter.update(true);}
    @Override public boolean onFragmentCreate(){return DevGramProfileCustomization.enabled()&&super.onFragmentCreate();}
    @Override public View createView(Context context){viewer=getUserConfig().getClientUserId();actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle(t("Мои жалобы"));actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});list=new UniversalRecyclerView(this,this::fill,this::click,null);fragmentView=new DevGramSettingsLayout(context,actionBar,list,resourceProvider);DevGramProfilePolicy.listen(policy);return fragmentView;}
    private void fill(ArrayList<UItem> items,UniversalAdapter adapter){
        int pending=0;for(int i=0;i<reports.length();i++)if(reports.optJSONObject(i)!=null&&reports.optJSONObject(i).optString("state").equals("open"))pending++;
        items.add(UItem.asCustom(DevGramWorkshopReportUi.hero(this,"Ваши обращения",t("Здесь появляются статусы жалоб и ответы модераторов."),reports.length())));
        items.add(UItem.asCustom(DevGramStudioControls.pills(getParentActivity(),new String[]{"Все","На рассмотрении","С решением"},filter,resourceProvider,i->{filter=i;list.adapter.update(true);}),52));
        int found=0;
        for(int i=0;i<reports.length();i++){
            JSONObject report=reports.optJSONObject(i);if(report==null)continue;boolean open=report.optString("state").equals("open")&&!report.optString("workState").equals("deleted");if(filter==1&&!open||filter==2&&open)continue;found++;
            LinearLayout card=DevGramWorkshopReportUi.card(this);card.addView(DevGramWorkshopReportUi.text(this,report.optString("title").isEmpty()?t("Работа удалена"):report.optString("title"),18,true));
            boolean dismissed=report.optString("decision").equals("dismiss");card.addView(DevGramWorkshopReportUi.chip(this,report.optString("workState").equals("deleted")?"Работа удалена":open?"На рассмотрении":dismissed?"Жалоба отклонена":"Жалоба подтверждена",open?0:dismissed?2:1),LayoutHelper.createLinear(-2,-2,0,9,0,10));
            card.addView(DevGramWorkshopReportUi.evidence(this,"Ваша жалоба",report.optString("text")),LayoutHelper.createLinear(-1,-2));
            card.addView(DevGramWorkshopReportUi.muted(this,DevGramWorkshopReportUi.date(report.optLong("created")),12),LayoutHelper.createLinear(-1,-2,0,10,0,0));
            if(!open){String reason=report.optString("reason");card.addView(DevGramWorkshopReportUi.evidence(this,"Ответ модератора",reason.isEmpty()?t(report.optString("workState").equals("deleted")?"Публикация больше недоступна":dismissed?"Нарушение не подтвердилось":"Оформление скрыто из мастерской"):reason),LayoutHelper.createLinear(-1,-2,0,12,0,0));}
            if(report.optString("workState","approved").equals("approved"))card.addView(DevGramWorkshopReportUi.button(this,"Открыть работу",()->presentFragment(DevGramWorkshopActivity.openWork(report.optLong("work")))));
            items.add(UItem.asCustom(DevGramWorkshopReportUi.wrap(this,card)));
        }
        if(error!=null||found==0)items.add(UItem.asCustom(DevGramWorkshopReportUi.state(this,error!=null,loading,"Здесь пока нет жалоб",error!=null?DevGramWorkshopReportUi.error(error):loading?"Получаем ваши обращения и решения модераторов.":"Жалобы на оформления и результаты их проверки появятся здесь.",this::load)));
        else items.add(UItem.asButton(1,R.drawable.msg_retry,t("Обновить")).setEnabled(!loading));
        items.add(UItem.asShadow(null));
    }
    private void click(UItem item,View view,int p,float x,float y){if(item.id==1)load();}
    private void load(){if(closed||loading||viewer!=getUserConfig().getClientUserId()||!DevGramProfileCustomization.enabled())return;loading=true;error=null;int request=++generation;if(list!=null)list.adapter.update(true);DevGramProfilePolicy.request(currentAccount,"studio/reports",DevGramStudioActivity.json("action","receipts"),(result,failure)->{if(closed||request!=generation||viewer!=getUserConfig().getClientUserId()||!DevGramProfileCustomization.enabled())return;loading=false;if(result!=null){reports=result.optJSONArray("reports");if(reports==null)reports=new JSONArray();}else error=failure==null?"network":failure;list.adapter.update(true);});}
    @Override public void onResume(){super.onResume();load();}@Override public void onFragmentDestroy(){closed=true;generation++;DevGramProfilePolicy.unlisten(policy);super.onFragmentDestroy();}
}

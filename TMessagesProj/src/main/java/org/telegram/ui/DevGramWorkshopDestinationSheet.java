package org.telegram.ui;

import android.view.Gravity;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.function.LongConsumer;

/** The server supplies verified targets; every installation rechecks their owner. */
final class DevGramWorkshopDestinationSheet {
    static void show(BaseFragment host,LongConsumer selected){
        if(!DevGramProfileCustomization.enabled()||host.getParentActivity()==null)return;
        long viewer=host.getUserConfig().getClientUserId();
        DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/communities",DevGramStudioActivity.json("action","list"),(result,error)->{
            if(host.getParentActivity()==null||host.getUserConfig().getClientUserId()!=viewer||!DevGramProfileCustomization.enabled())return;
            if(result==null){BulletinFactory.of(host).createErrorBulletin(DevGramLocale.text("Не удалось загрузить подтверждённые сообщества")).show();return;}
            android.content.Context context=host.getParentActivity();Theme.ResourcesProvider resources=host.getResourceProvider();
            LinearLayout body=DevGramStudioControls.column(context);body.setPadding(AndroidUtilities.dp(16),AndroidUtilities.dp(8),AndroidUtilities.dp(16),AndroidUtilities.dp(24));
            TextView note=DevGramStudioControls.text(context,"Выберите, куда применить оформление. Показаны только подтверждённые сообщества.",14,false,resources);note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resources));body.addView(note,LayoutHelper.createLinear(-1,-2,4,0,4,16));
            ScrollView scroll=new ScrollView(context){@Override protected void onMeasure(int w,int h){super.onMeasure(w,DevGramStudioControls.sheetHeightSpec(h,.78f));}};scroll.addView(body);
            BottomSheet sheet=new BottomSheet.Builder(context,true,resources).setTitle(DevGramLocale.text("Куда установить оформление?")).setCustomView(scroll).create();
            add(host,body,R.drawable.msg_contact,"Личный профиль",UserObject.getUserName(host.getUserConfig().getCurrentUser()),()->{sheet.dismiss();selected.accept(viewer);});
            JSONArray targets=result.optJSONArray("targets");
            if(targets!=null)for(int i=0;i<targets.length();i++){
                JSONObject target=targets.optJSONObject(i);if(target==null||target.optLong("subject")>=0)continue;
                long subject=target.optLong("subject");String kind=target.optString("kind");
                String subtitle=DevGramLocale.text(kind.equals("channel")?"Канал":kind.equals("forum")?"Форум":"Группа")+" · "+DevGramLocale.text("Владение подтверждено")+" · "+target.optInt("members");
                add(host,body,kind.equals("channel")?R.drawable.msg_channel:kind.equals("forum")?R.drawable.msg_topics:R.drawable.msg_groups,target.optString("title"),subtitle,()->{
                    if(!DevGramAppearanceAccess.check(host,"profile.communities"))return;
                    sheet.dismiss();selected.accept(subject);
                });
            }
            TextView hint=DevGramStudioControls.text(context,"Сообщества подтверждаются через управление каналом, группой или форумом → Оформление сообщества.",13,false,resources);hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resources));body.addView(hint,LayoutHelper.createLinear(-1,-2,4,12,4,0));
            host.showDialog(sheet);
        });
    }
    private static void add(BaseFragment host,LinearLayout body,int icon,String title,String subtitle,Runnable click){
        android.content.Context context=body.getContext();Theme.ResourcesProvider resources=host.getResourceProvider();int accent=Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources);
        LinearLayout card=new LinearLayout(context);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(AndroidUtilities.dp(14),AndroidUtilities.dp(16),AndroidUtilities.dp(14),AndroidUtilities.dp(16));card.setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_windowBackgroundGray,resources),Theme.multAlpha(accent,.18f)));
        ImageView image=new ImageView(context);image.setImageResource(icon);image.setColorFilter(accent);card.addView(image,LayoutHelper.createLinear(28,28,0,0,14,0));
        LinearLayout texts=DevGramStudioControls.column(context);TextView name=DevGramStudioControls.text(context,"",16,true,resources);name.setText(title);texts.addView(name);TextView detail=DevGramStudioControls.text(context,"",12,false,resources);detail.setText(subtitle);detail.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resources));texts.addView(detail,LayoutHelper.createLinear(-1,-2,0,5,0,0));card.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        ImageView arrow=new ImageView(context);arrow.setImageResource(R.drawable.msg_arrowright);arrow.setColorFilter(accent);card.addView(arrow,LayoutHelper.createLinear(20,20));
        card.setOnClickListener(v->{if(DevGramProfileCustomization.enabled())click.run();});body.addView(card,LayoutHelper.createLinear(-1,-2,0,0,0,10));
    }
}

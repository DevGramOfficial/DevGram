package org.telegram.ui;

import android.view.View;
import android.widget.*;
import java.lang.ref.WeakReference;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.*;

/** Account-scoped onboarding; never resets existing display/energy preferences. */
public final class DevGramQuickSetup {
    private static final String SEEN="devgramCustomizationQuickSetup_v1_";
    private static WeakReference<BaseFragment> pending=new WeakReference<>(null);
    private static BottomSheet active;
    private static Runnable scheduled;
    private DevGramQuickSetup(){}
    public static void schedule(BaseFragment host){
        if(host==null||host.getUserConfig().getClientUserId()==0||!DevGramProfileCustomization.enabled()||active!=null||MessagesController.getGlobalMainSettings().getBoolean(SEEN+host.getUserConfig().getClientUserId(),false))return;
        if(scheduled!=null)AndroidUtilities.cancelRunOnUIThread(scheduled);pending=new WeakReference<>(host);long uid=host.getUserConfig().getClientUserId();
        scheduled=()->{BaseFragment current=pending.get();scheduled=null;if(current==null||current.getUserConfig().getClientUserId()!=uid||current.isPaused()||current.getParentActivity()==null||!current.getParentActivity().hasWindowFocus()||current.visibleDialog!=null||LaunchActivity.getSafeLastFragment()!=current||!DevGramProfileCustomization.enabled())return;show(current);};AndroidUtilities.runOnUIThread(scheduled,1600);
    }
    public static void show(BaseFragment host){
        if(active!=null||host==null||host.getParentActivity()==null||!DevGramProfileCustomization.enabled())return;
        long uid=host.getUserConfig().getClientUserId();Theme.ResourcesProvider resources=host.getResourceProvider();LinearLayout body=DevGramStudioControls.column(host.getParentActivity());
        TextView note=DevGramStudioControls.text(host.getParentActivity(),"Выберите, какое оформление видеть. Изменения сразу видны на примере.",14,false,resources);note.setPadding(AndroidUtilities.dp(24),AndroidUtilities.dp(8),AndroidUtilities.dp(24),AndroidUtilities.dp(12));body.addView(note);
        DevGramQuickSetupPreview preview=new DevGramQuickSetupPreview(host.getParentActivity(),host.getCurrentAccount(),uid,resources);body.addView(preview,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(168)));
        ScrollView scroll=new ScrollView(host.getParentActivity()){@Override protected void onMeasure(int w,int h){super.onMeasure(w,DevGramStudioControls.sheetHeightSpec(h,.34f));}};LinearLayout rows=DevGramStudioControls.column(host.getParentActivity());scroll.addView(rows);body.addView(scroll);
        String[] keys={"devgramChatDecor","devgramChatBadges","devgramChatBubbles","devgramBakeOutside","devgramBakeProfile"};String[] names={"Рамки вне профиля","Бейджики","Пузырьки сообщений","Запекание вне профиля","Запекание в профиле"};String[] details={"В списках чатов, участников и в переписке.","Изображения на фоне строк чатов.","Оформление сообщений пользователей DevGram.","Рамки застывают: меньше нагрузка и расход батареи.","Такой же статичный показ рамок в профиле."};
        for(int i=0;i<keys.length;i++){String key=keys[i];boolean fallback=i<3;TextCheckCell cell=new TextCheckCell(host.getParentActivity(),resources);cell.setTextAndCheck(DevGramLocale.text(names[i]),MessagesController.getGlobalMainSettings().getBoolean(key,fallback),false);cell.setOnClickListener(v->{if(host.getUserConfig().getClientUserId()!=uid||!DevGramProfileCustomization.enabled()){if(active!=null)active.dismiss();return;}boolean enabled=!MessagesController.getGlobalMainSettings().getBoolean(key,fallback);MessagesController.getGlobalMainSettings().edit().putBoolean(key,enabled).apply();cell.setChecked(enabled);DevGramProfilePolicy.changed();preview.refresh();});rows.addView(cell);TextView hint=DevGramStudioControls.text(host.getParentActivity(),details[i],12,false,resources);hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resources));hint.setPadding(AndroidUtilities.dp(24),0,AndroidUtilities.dp(24),AndroidUtilities.dp(12));rows.addView(hint);}
        BottomSheet sheet=new BottomSheet.Builder(host.getParentActivity(),false,resources).setTitle(DevGramLocale.text("Быстрая настройка")).setCustomView(body).create();active=sheet;TextView done=DevGramStudioControls.text(host.getParentActivity(),"Готово",16,true,resources);done.setGravity(android.view.Gravity.CENTER);done.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText,resources));done.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),Theme.getColor(Theme.key_featuredStickers_addButton,resources)));LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(48));dp.setMargins(AndroidUtilities.dp(16),AndroidUtilities.dp(12),AndroidUtilities.dp(16),AndroidUtilities.dp(12));body.addView(done,dp);done.setOnClickListener(v->sheet.dismiss());
        Runnable policy=()->{if(host.getUserConfig().getClientUserId()!=uid||!DevGramProfileCustomization.enabled())sheet.dismiss();};DevGramProfilePolicy.listen(policy);sheet.fixNavigationBarKey(Theme.key_windowBackgroundWhite);host.showDialog(sheet,d->{DevGramProfilePolicy.unlisten(policy);preview.release();if(active==sheet)active=null;if(host.getUserConfig().getClientUserId()==uid)MessagesController.getGlobalMainSettings().edit().putBoolean(SEEN+uid,true).apply();});
    }
}

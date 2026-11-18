package org.telegram.ui.Components;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;

/** ShareWarn: illustration, explicit consent, five-second enable delay and cancel. */
public final class DevGramShareListeningWarning {
    private static String t(String value){return DevGramLocale.text(value);}
    public static void show(BaseFragment host,String provider,Runnable confirmed){
        if(host.getParentActivity()==null)return;
        LinearLayout body=DevGramStudioControls.column(host.getParentActivity());body.setPadding(AndroidUtilities.dp(22),0,AndroidUtilities.dp(22),AndroidUtilities.dp(18));
        RLottieImageView illustration=new RLottieImageView(host.getParentActivity());illustration.setAnimation(R.raw.utyan_private,104,104);body.addView(illustration,LayoutHelper.createLinear(104,104,Gravity.CENTER_HORIZONTAL,0,4,0,12));
        TextView note=DevGramStudioControls.text(host.getParentActivity(),"Другие пользователи DevGram смогут подключиться к вашей музыке. Для получения записи используется ваше подключение к сервису; ключи не передаются слушателям. Для Spotify полная запись доступна через отдельно подключённую Яндекс.Музыку с включённым прослушиванием, иначе сервис может предоставить только короткий отрывок. Доступность зависит от разрешений и подписки музыкального сервиса. Вы сможете отключить прослушивание в любой момент.",14,false,host.getResourceProvider());body.addView(note,LayoutHelper.createLinear(-1,-2,0,0,0,18));
        BottomSheet sheet=new BottomSheet.Builder(host.getParentActivity(),false,host.getResourceProvider()).setTitle(t("Осторожно!")).setCustomView(body).create();sheet.fixNavigationBarKey(Theme.key_dialogBackground);
        TextView enable=DevGramStudioControls.text(host.getParentActivity(),"",16,true,host.getResourceProvider());enable.setGravity(Gravity.CENTER);enable.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText,host.getResourceProvider()));enable.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),Theme.getColor(Theme.key_featuredStickers_addButton,host.getResourceProvider()),Theme.getColor(Theme.key_listSelector,host.getResourceProvider())));body.addView(enable,LayoutHelper.createLinear(-1,48));
        TextView cancel=DevGramStudioControls.text(host.getParentActivity(),"Отмена",16,false,host.getResourceProvider());cancel.setGravity(Gravity.CENTER);cancel.setOnClickListener(v->sheet.dismiss());body.addView(cancel,LayoutHelper.createLinear(-1,48,0,8,0,0));
        final int[] remaining={5};final boolean[] dismissed={false};Runnable timer=new Runnable(){public void run(){if(dismissed[0])return;enable.setEnabled(remaining[0]==0);enable.setAlpha(remaining[0]==0?1:.5f);enable.setText(remaining[0]==0?t("Включить"):String.format(t("Включить (%s с)"),remaining[0]));if(remaining[0]-->0)enable.postDelayed(this,1000);}};
        enable.setEnabled(false);enable.setOnClickListener(v->{if(remaining[0]<0&&!dismissed[0]){sheet.dismiss();confirmed.run();}});
        sheet.setOnDismissListener(()->{dismissed[0]=true;enable.removeCallbacks(timer);illustration.stopAnimation();});host.showDialog(sheet);illustration.playAnimation();timer.run();
    }
}

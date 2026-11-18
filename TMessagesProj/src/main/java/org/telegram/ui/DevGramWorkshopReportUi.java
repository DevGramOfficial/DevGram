package org.telegram.ui;
import android.view.*;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;

/** Shared report hierarchy: heading, readable evidence, status and an explicit next action. */
final class DevGramWorkshopReportUi {
    static String t(String value){return DevGramLocale.text(value);}
    static int dp(float value){return AndroidUtilities.dp(value);}
    static LinearLayout column(BaseFragment host,int pad){LinearLayout view=DevGramStudioControls.column(host.getParentActivity());view.setPadding(dp(pad),dp(pad),dp(pad),dp(pad));return view;}
    static TextView text(BaseFragment host,String value,int size,boolean bold){TextView view=DevGramStudioControls.text(host.getParentActivity(),"",size,bold,host.getResourceProvider());view.setText(value);return view;}
    static TextView muted(BaseFragment host,String value,int size){TextView view=text(host,value,size,false);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,host.getResourceProvider()));return view;}
    static LinearLayout card(BaseFragment host){LinearLayout view=column(host,18);view.setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_windowBackgroundWhite,host.getResourceProvider()),Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,host.getResourceProvider()),.15f)));return view;}
    static View wrap(BaseFragment host,View inner){LinearLayout root=column(host,0);root.setPadding(dp(16),dp(5),dp(16),dp(5));root.addView(inner,new LinearLayout.LayoutParams(-1,-2));return root;}
    static View hero(BaseFragment host,String title,String subtitle,int count){
        LinearLayout body=card(host);int accent=Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,host.getResourceProvider());body.setBackground(DevGramStudioControls.surface(Theme.multAlpha(accent,.10f),0));
        LinearLayout heading=new LinearLayout(host.getParentActivity());heading.setGravity(Gravity.CENTER_VERTICAL);ImageView icon=new ImageView(host.getParentActivity());icon.setImageResource(org.telegram.messenger.R.drawable.msg_report);icon.setColorFilter(accent);heading.addView(icon,LayoutHelper.createLinear(24,24,0,0,12,0));heading.addView(text(host,t(title),22,true),new LinearLayout.LayoutParams(0,-2,1));if(count>=0){TextView badge=text(host,String.valueOf(count),24,true);badge.setTextColor(accent);heading.addView(badge);}body.addView(heading);body.addView(muted(host,subtitle,13),LayoutHelper.createLinear(-1,-2,0,12,0,0));return wrap(host,body);
    }
    static TextView chip(BaseFragment host,String label,int state){int tint=Theme.getColor(state==0?Theme.key_windowBackgroundWhiteBlueText:state==1?Theme.key_windowBackgroundWhiteGreenText:Theme.key_text_RedRegular,host.getResourceProvider());TextView view=text(host,t(label),11,true);view.setTextColor(tint);view.setPadding(dp(9),dp(5),dp(9),dp(5));view.setBackground(DevGramStudioControls.surface(Theme.multAlpha(tint,.11f),0));return view;}
    static View evidence(BaseFragment host,String label,String value){LinearLayout view=column(host,13);view.setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_windowBackgroundGray,host.getResourceProvider()),0));view.addView(muted(host,t(label),11));TextView content=text(host,value,15,false);content.setTextIsSelectable(true);content.setLineSpacing(dp(3),1);view.addView(content,LayoutHelper.createLinear(-1,-2,0,6,0,0));return view;}
    static View button(BaseFragment host,String label,Runnable action){TextView view=text(host,t(label),14,true);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,host.getResourceProvider()));view.setGravity(Gravity.CENTER_VERTICAL);view.setPadding(0,dp(12),0,dp(2));view.setOnClickListener(v->action.run());return view;}
    static View state(BaseFragment host,boolean error,boolean loading,String emptyTitle,String detail,Runnable retry){LinearLayout card=card(host);ImageView icon=new ImageView(host.getParentActivity());icon.setImageResource(error?R.drawable.msg_retry:R.drawable.ic_ab_done);icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,host.getResourceProvider()));card.addView(icon,LayoutHelper.createLinear(36,36,Gravity.CENTER_HORIZONTAL));TextView title=text(host,t(loading?"Загружаю…":error?"Не удалось загрузить жалобы":emptyTitle),18,true);title.setGravity(Gravity.CENTER);card.addView(title,LayoutHelper.createLinear(-1,-2,0,12,0,0));TextView note=muted(host,t(detail),14);note.setGravity(Gravity.CENTER);card.addView(note,LayoutHelper.createLinear(-1,-2,0,8,0,0));if(!loading)card.addView(button(host,error?"Попробовать снова":"Обновить",retry),LayoutHelper.createLinear(-1,-2,0,8,0,0));return wrap(host,card);}
    static String date(long seconds){return seconds<=0?"":new java.text.SimpleDateFormat("dd.MM.yyyy · HH:mm",java.util.Locale.ROOT).format(new java.util.Date(seconds*1000));}
    static String error(String code){return "moderator_session".equals(code)?"Войдите в аккаунт модератора заново. Сессия не обновилась.":"forbidden".equals(code)?"Доступ модератора не подтверждён. Проверьте аккаунт каталога.":"stale_report".equals(code)?"Жалоба уже изменилась. Обновите список перед решением.":"Проверьте соединение и повторите загрузку. Ваши жалобы сохранены.";}
}

package org.telegram.ui.Components;

import android.content.Context;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;

public final class DevGramClanTagChoiceView extends LinearLayout {
    private final DevGramClanTagDrawable tag;
    public DevGramClanTagChoiceView(Context context,int account,JSONObject value,boolean chosen,Theme.ResourcesProvider resources,Runnable click){
        super(context);setGravity(Gravity.CENTER_VERTICAL);setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(14),AndroidUtilities.dp(20),AndroidUtilities.dp(14));
        setBackground(DevGramStudioControls.surface(chosen?androidx.core.graphics.ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources),20):Theme.getColor(Theme.key_windowBackgroundWhite,resources),0));setSelected(chosen);
        org.telegram.tgnet.TLRPC.Chat chat=MessagesController.getInstance(account).getChat(Math.abs(value.optLong("chat")));BackupImageView avatar=new BackupImageView(context);avatar.setRoundRadius(AndroidUtilities.dp(22));AvatarDrawable fallback=new AvatarDrawable();if(chat!=null){fallback.setInfo(account,chat);avatar.setForUserOrChat(chat,fallback);}else{fallback.setInfo(value.optLong("chat"),value.optString("title"),"");avatar.setImageDrawable(fallback);}addView(avatar,LayoutHelper.createLinear(44,44,0,0,12,0));
        LinearLayout labels=DevGramStudioControls.column(context);TextView title=DevGramStudioControls.text(context,"",15,true,resources);title.setText(value.optString("title"));title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(title);
        TextView source=DevGramStudioControls.text(context,"",12,false,resources);source.setText((value.optString("username").isEmpty()?DevGramLocale.text("Сообщество"):"@"+value.optString("username"))+" · "+value.optInt("count"));source.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resources));source.setSingleLine(true);source.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(source);addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        ImageView pill=new ImageView(context);tag=new DevGramClanTagDrawable(pill,account,value,()->Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,resources));tag.color(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources));pill.setImageDrawable(tag);addView(pill,LayoutHelper.createLinear(Math.round(tag.getIntrinsicWidth()/AndroidUtilities.density),20,8,0,0,0));
        setContentDescription(value.optString("title")+" · "+value.optString("tag"));setOnClickListener(v->click.run());
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();tag.attach();}
    @Override protected void onDetachedFromWindow(){tag.detach();super.onDetachedFromWindow();}
}

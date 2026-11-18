package org.telegram.ui.Components;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.*;
import org.telegram.ui.ActionBar.*;

/** Native equivalent of the reference's compact profile comments panel. */
public final class DevGramProfileCommentsView extends LinearLayout {
    private final long profile,viewer;
    private final int account,count;
    private final boolean editing;
    private final Theme.ResourcesProvider resources;
    private boolean attached,busy;
    private int generation;
    private String signature="";
    private final Runnable policy=()->{if(attached){if(!valid()){generation++;busy=false;signature="";removeAllViews();}else load();}};
    private final Runnable refresh=new Runnable(){public void run(){if(attached){if(isShown()&&hasWindowFocus())load();postDelayed(this,30000);}}};
    public DevGramProfileCommentsView(Context context,int account,long profile,int count,Theme.ResourcesProvider resources,boolean editing){
        super(context);this.account=account;this.profile=profile;this.count=Math.max(1,Math.min(10,count));this.resources=resources;this.editing=editing;viewer=UserConfig.getInstance(account).getClientUserId();
        setOrientation(VERTICAL);setPadding(dp(16),dp(10),dp(16),dp(12));render(null);
    }
    private static int dp(float v){return AndroidUtilities.dp(v);}
    private String t(String v){return DevGramLocale.text(v);}
    private int color(int key){return Theme.getColor(key,resources);}
    private boolean valid(){return attached&&UserConfig.getInstance(account).getClientUserId()==viewer&&DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(profile,"profile.comments");}
    private TextView text(String value,int size,boolean bold){TextView view=DevGramStudioControls.text(getContext(),"",size,bold,resources);view.setText(value);return view;}
    private BaseFragment host(){BaseFragment host=LaunchActivity.getSafeLastFragment();return host!=null&&host.getCurrentAccount()==account?host:null;}
    private void open(){BaseFragment host=host();if(!editing&&valid()&&host!=null)DevGramProfileCommentsSheet.show(host,profile);}
    private void openAuthor(long uid){BaseFragment host=host();if(editing||!valid()||host==null||uid<=0)return;android.os.Bundle arguments=new android.os.Bundle();arguments.putLong("user_id",uid);ProfileActivity profile=new ProfileActivity(arguments);profile.setCurrentAccount(account);host.presentFragment(profile);}
    private String name(JSONObject item){org.telegram.tgnet.TLRPC.User user=MessagesController.getInstance(account).getUser(item.optLong("uid"));JSONObject author=item.optJSONObject("author");return user!=null?UserObject.getUserName(user):author!=null&&!author.optString("name").isEmpty()?author.optString("name"):t("Пользователь");}
    private String time(long seconds){long elapsed=System.currentTimeMillis()/1000-seconds;if(seconds<=0||elapsed<0)return "";if(elapsed<60)return t("только что");if(elapsed<3600)return elapsed/60+t(" мин");if(elapsed<86400)return elapsed/3600+t(" ч");if(elapsed<172800)return t("вчера");return new java.text.SimpleDateFormat("d MMM",DevGramLocale.isRussian()?new java.util.Locale("ru"):java.util.Locale.US).format(new java.util.Date(seconds*1000));}
    private void load(){if(!valid()||busy)return;busy=true;int request=++generation;
        DevGramProfilePolicy.request(account,"studio/comments",DevGramStudioActivity.json("action","get","profile",profile,"limit",count),(value,error)->{if(!valid()||request!=generation)return;busy=false;if(value!=null){String next=value.toString();if(!next.equals(signature)){signature=next;render(value);}}});
    }
    private void render(JSONObject page){removeAllViews();int total=page==null?0:page.optInt("total");TextView heading=text(t("Комментарии")+(total>0?" · "+total:""),16,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-2,-2);hp.bottomMargin=dp(8);addView(heading,hp);JSONArray rows=page==null?null:page.optJSONArray("comments");
        if(rows==null||rows.length()==0){TextView empty=text(t("Пока нет комментариев — будьте первым"),14,false);empty.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));empty.setPadding(0,dp(2),0,dp(6));addView(empty);}else for(int i=0;i<Math.min(count,rows.length());i++){JSONObject item=rows.optJSONObject(i);if(item!=null)addView(row(item));}
        TextView footer=text(total>count?t("Написать · показать все (")+total+")":t("Написать комментарий"),14,true);footer.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText));footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(6),0,dp(2));footer.setOnClickListener(v->open());addView(footer);
    }
    private View row(JSONObject item){LinearLayout row=new LinearLayout(getContext());row.setOrientation(HORIZONTAL);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(10);row.setLayoutParams(rp);
        long uid=item.optLong("uid");BackupImageView avatar=new BackupImageView(getContext());avatar.setRoundRadius(dp(18));org.telegram.tgnet.TLRPC.User user=MessagesController.getInstance(account).getUser(uid);AvatarDrawable fallback=new AvatarDrawable();if(user!=null){fallback.setInfo(user);avatar.setForUserOrChat(user,fallback);}else{fallback.setInfo(uid,name(item),"");avatar.setImageDrawable(fallback);}row.addView(avatar,LayoutHelper.createLinear(36,36,0,0,10,0));avatar.setOnClickListener(v->openAuthor(uid));
        LinearLayout details=DevGramStudioControls.column(getContext());row.addView(details,new LinearLayout.LayoutParams(0,-2,1));LinearLayout title=new LinearLayout(getContext());title.setGravity(Gravity.CENTER_VERTICAL);
        TextView author=text(name(item),13,true);author.setSingleLine();author.setEllipsize(TextUtils.TruncateAt.END);int[] colors=DevGramNickColor.of(uid);author.setTextColor(colors==null?color(Theme.key_windowBackgroundWhiteBlackText):colors[0]);title.addView(author,new LinearLayout.LayoutParams(0,-2,1));TextView date=text(time(item.optLong("created")),12,false);date.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));date.setPadding(dp(6),0,0,0);title.addView(date);details.addView(title);
        TextView body=text(item.optBoolean("deleted")?t("Комментарий удалён"):item.optString("text"),14,false);body.setPadding(0,dp(1),0,0);details.addView(body);
        DevGramCommentLikeView like=new DevGramCommentLikeView(getContext(),color(Theme.key_windowBackgroundWhiteGrayText));like.state(item.optInt("likes"),item.optBoolean("liked"));LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)like.getLayoutParams();lp.gravity=Gravity.CENTER_VERTICAL;row.addView(like,lp);like.setVisibility(item.optBoolean("deleted")?View.GONE:View.VISIBLE);
        like.setOnClickListener(v->{BaseFragment host=host();if(editing||!valid()||busy||host==null||!DevGramAppearanceAccess.check(host,"profile.comments"))return;busy=true;int request=++generation;boolean liked=item.optBoolean("liked");like.state(Math.max(0,item.optInt("likes")+(liked?-1:1)),!liked);
            DevGramProfilePolicy.request(account,"studio/comments",DevGramStudioActivity.json("action","like","profile",profile,"limit",count,"comment",item.optLong("id"),"liked",!liked),(result,error)->{if(!valid()||request!=generation)return;busy=false;if(result==null)like.state(item.optInt("likes"),liked);else{signature=result.toString();render(result);}});});return row;
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;DevGramProfilePolicy.listen(policy);load();postDelayed(refresh,30000);}
    @Override protected void onDetachedFromWindow(){attached=false;busy=false;generation++;removeCallbacks(refresh);DevGramProfilePolicy.unlisten(policy);super.onDetachedFromWindow();}
}

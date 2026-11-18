package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** Author cover, overlapping Telegram avatar, styled name, ranking and separate vote totals. */
public final class DevGramWorkshopAuthorView extends FrameLayout {
    private final BaseFragment host;
    private final long uid;
    private final JSONObject author;
    private final Cover cover;
    private final BackupImageView avatar;
    private final TextView name, handle;
    private JSONObject document;
    private boolean released;
    

    public DevGramWorkshopAuthorView(Context context, BaseFragment host, JSONObject author, Runnable open) {
        super(context); this.host=host;this.author=author;this.uid=author.optLong("uid");
        document=author.optJSONObject("document");if(document==null)document=new JSONObject();
        setBackground(Theme.createRoundRectDrawable(dp(12),0xff171d2b));setClipToOutline(true);
        cover=new Cover(context);addView(cover,LayoutHelper.createFrame(-1,116));
        LinearLayout body=DevGramStudioControls.column(context);body.setPadding(dp(16),dp(78),dp(16),dp(14));addView(body,LayoutHelper.createFrame(-1,-2));
        LinearLayout identity=new LinearLayout(context);identity.setGravity(Gravity.CENTER_VERTICAL);body.addView(identity,LayoutHelper.createLinear(-1,82));
        FrameLayout ring=new FrameLayout(context);ring.setPadding(dp(3),dp(3),dp(3),dp(3));ring.setBackground(Theme.createRoundRectDrawable(dp(41),0xff171d2b));
        avatar=new BackupImageView(context);avatar.setRoundRadius(dp(38));ring.addView(avatar,LayoutHelper.createFrame(76,76));ring.setOnClickListener(v->open.run());ring.setContentDescription(t("Открыть профиль"));identity.addView(ring,LayoutHelper.createLinear(82,82));
        LinearLayout title=DevGramStudioControls.column(context);identity.addView(title,LayoutHelper.createLinear(0,-2,1f,12,0,0,0));
        DevGramNameEffects effects=new DevGramNameEffects();
        name=new TextView(context){@Override protected void onDraw(Canvas canvas){effects.set(document,1);effects.apply(getPaint(),getPaint().measureText(getText().toString()),getHeight(),true);super.onDraw(canvas);effects.restore(getPaint());}};
        name.setTextSize(21);name.setTypeface(DevGramProfileDocument.nameFont(document));name.setTextColor(Color.WHITE);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);title.addView(name);
        int rank=author.optInt("rank");if(rank>0){TextView ranking=text(t("Место в рейтинге")+" · #"+rank,12,true);ranking.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,host.getResourceProvider()));title.addView(ranking);}
        handle=text("",13,false);handle.setSingleLine(true);handle.setEllipsize(android.text.TextUtils.TruncateAt.END);handle.setTextColor(0xffb8c3d7);title.addView(handle);
        LinearLayout facts=new LinearLayout(context);facts.setGravity(Gravity.CENTER_VERTICAL);body.addView(facts,LayoutHelper.createLinear(-1,-2,0,12,0,0));
        fact(facts,author.optInt("works"),"Работ");fact(facts,author.optInt("likes"),"Лайки мастерской");fact(facts,author.optInt("profile_likes"),"Лайки профиля");
        TextView profile=text(t("Открыть профиль"),14,true);profile.setGravity(Gravity.CENTER);profile.setTextColor(Color.WHITE);profile.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10),0x18ffffff,0x30ffffff));profile.setOnClickListener(v->open.run());body.addView(profile,LayoutHelper.createLinear(-1,40,0,12,0,0));
        person();
    }
    private void fact(LinearLayout target,int count,String label){LinearLayout box=DevGramStudioControls.column(getContext());box.setGravity(Gravity.CENTER);box.setPadding(dp(3),dp(8),dp(3),dp(8));TextView value=text(author.optBoolean("loading")?"…":String.valueOf(count),17,true);value.setGravity(Gravity.CENTER);value.setTextColor(Color.WHITE);box.addView(value);TextView caption=text(t(label),10,false);caption.setTextColor(0xffb8c3d7);caption.setGravity(Gravity.CENTER);caption.setMaxLines(2);box.addView(caption);target.addView(box,new LinearLayout.LayoutParams(0,-2,1));}
    public void person(){if(released)return;TLRPC.User user=host.getMessagesController().getUser(uid);String label=user==null?author.optString("name"):UserObject.getUserName(user);if(label.isEmpty())label=t("Автор")+" #"+uid;name.setText(label);String username=user==null?author.optString("username"):UserObject.getPublicUsername(user);handle.setText(username==null||username.isEmpty()?"":("@"+username));handle.setVisibility(handle.length()==0?GONE:VISIBLE);AvatarDrawable placeholder=new AvatarDrawable();if(user!=null){placeholder.setInfo(user);avatar.setForUserOrChat(user,placeholder);}else{placeholder.setInfo(uid,label,null);avatar.setImageDrawable(placeholder);}}
    public void refreshStyle(){if(released)return;name.setTypeface(DevGramProfileDocument.nameFont(document));name.invalidate();}
    public void setActive(boolean active){cover.renderer.setActive(active);}
    public void release(){released=true;cover.renderer.detach();avatar.getImageReceiver().clearImage();}
    private int dp(float v){return AndroidUtilities.dp(v);}
    private String t(String s){return DevGramLocale.text(s);}
    private TextView text(String s,int size,boolean bold){return DevGramStudioControls.text(getContext(),s,size,bold,host.getResourceProvider());}
    private final class Cover extends View {
        final DevGramProfilePageRenderer renderer;
        final Paint shade=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader gradient;
        private int height;
        Cover(Context context){super(context);renderer=new DevGramProfilePageRenderer(this,host.getCurrentAccount(),uid,"banner");renderer.setStill(true);}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();renderer.attach();}
        @Override protected void onDetachedFromWindow(){renderer.detach();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(0xff172033);renderer.draw(canvas,getWidth(),getHeight(),document);if(gradient==null||height!=getHeight()){height=getHeight();gradient=new LinearGradient(0,height*.35f,0,height,Color.TRANSPARENT,0x99000000,Shader.TileMode.CLAMP);}shade.setShader(gradient);canvas.drawRect(0,0,getWidth(),getHeight(),shade);}
    }
}

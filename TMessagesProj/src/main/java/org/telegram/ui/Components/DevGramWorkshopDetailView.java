package org.telegram.ui.Components;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.DevGramAppearanceAccess;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Workshop work page: portrait/info split, author gallery and a pinned action bar. */
public final class DevGramWorkshopDetailView extends LinearLayout {
    private final BaseFragment host;
    private final IntConsumer action;
    private final Consumer<JSONObject> open;
    private final Runnable author;
    private final ArrayList<DevGramWorkshopPreviewView> previews=new ArrayList<>();
    private final ScrollView scroll;
    private final LinearLayout body,bar;
    private LinearLayout more,commentList;
    private TextView apply,likeCount;
    private View like,favorite;
    private JSONObject work;
    private boolean closed;
    private int generation;
    public DevGramWorkshopDetailView(Context context,BaseFragment host,IntConsumer action,Consumer<JSONObject> open,Runnable author) {
        super(context);this.host=host;this.action=action;this.open=open;this.author=author;
        setOrientation(VERTICAL);setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        body=DevGramStudioControls.column(context);body.setPadding(dp(16),dp(8),dp(16),dp(20));scroll.addView(body);
        addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        View divider=new View(context);divider.setBackgroundColor(color(Theme.key_divider));addView(divider,new LinearLayout.LayoutParams(-1,1));
        bar=new LinearLayout(context);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(16),dp(10),dp(16),dp(14));addView(bar,new LinearLayout.LayoutParams(-1,-2));
        status("Загружаю…");
    }
    private int dp(float n){return AndroidUtilities.dp(n);}
    private int color(int key){return Theme.getColor(key,host.getResourceProvider());}
    private String t(String value){return DevGramLocale.text(value);}
    private TextView text(String value,int size,boolean bold){return DevGramStudioControls.text(getContext(),value,size,bold,host.getResourceProvider());}
    private boolean previewsActive;
    public void setPreviewsActive(boolean active){previewsActive=active;for(DevGramWorkshopPreviewView preview:previews)preview.setActive(active);}
    private void releasePreviews(){for(DevGramWorkshopPreviewView preview:previews)preview.release();previews.clear();}
    public void status(String value){if(work!=null)return;body.removeAllViews();TextView message=text(value,15,false);message.setGravity(Gravity.CENTER);message.setPadding(0,dp(48),0,dp(48));body.addView(message);bar.setVisibility(GONE);}
    public void bind(JSONObject value,JSONArray comments,boolean liked,boolean saved,int likes,boolean busy,Consumer<LinearLayout> sources){
        if(closed)return;
        work=value;int request=++generation;releasePreviews();body.removeAllViews();bar.removeAllViews();bar.setVisibility(VISIBLE);
        LinearLayout hero=new LinearLayout(getContext()){
            @Override protected void onMeasure(int w,int h){
                int width=MeasureSpec.getSize(w),previewWidth=Math.min(dp(220),Math.max(dp(96),Math.round(width*.37f)));
                View preview=getChildAt(0);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)preview.getLayoutParams();int previewHeight=Math.round(previewWidth*476f/211);if(lp.width!=previewWidth||lp.height!=previewHeight){lp.width=previewWidth;lp.height=previewHeight;preview.setLayoutParams(lp);}
                super.onMeasure(w,h);
            }
        };hero.setGravity(Gravity.TOP);
        DevGramWorkshopPreviewView preview=preview(value,true);preview.setOnClickListener(v->fullscreen());hero.addView(preview,new LinearLayout.LayoutParams(dp(132),dp(298)));
        LinearLayout info=DevGramStudioControls.column(getContext());info.setPadding(dp(12),dp(12),0,0);
        TextView title=text("",18,true);title.setText(value.optString("title"));info.addView(title);
        TextView by=text("",12,false);by.setText(authorName(value.optLong("uid")));by.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));by.setPadding(0,dp(6),0,dp(4));by.setOnClickListener(v->author.run());by.setContentDescription(t("Другие работы автора"));info.addView(by);
        TextView version=text("",11,false);version.setText("v"+value.optInt("version",1)+"  ·  "+t(value.optString("kind").equals("frame")?"Рамка":"Профиль"));version.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));info.addView(version);
        if(value.optString("state").equals("pending")){TextView pending=text("На проверке",12,true);pending.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText));info.addView(pending);}
        long uid=value.optLong("uid");boolean own=uid==host.getUserConfig().getClientUserId(),developer=DevGramBadges.hasDeveloperFeatures(host.getUserConfig().getClientUserId());
        if(own||developer||value.optString("kind").equals("frame")){
            TextView menu=button("Другие действия",R.drawable.msg_settings,false);menu.setOnClickListener(v->{PopupMenu popup=new PopupMenu(getContext(),menu);if(work.optString("kind").equals("frame"))popup.getMenu().add(0,17,0,t("Скачать рамку"));if(own)popup.getMenu().add(0,18,1,t("Обновить работу"));if(own||developer)popup.getMenu().add(0,14,2,t("Удалить публикацию"));popup.setOnMenuItemClickListener(item->{action.accept(item.getItemId());return true;});popup.show();});info.addView(menu,LayoutHelper.createLinear(-1,44,0,10,0,0));
        }
        TextView source=button("Исходники",R.drawable.msg_download,false);info.addView(source,LayoutHelper.createLinear(-1,48,0,8,0,0));
        LinearLayout assets=DevGramStudioControls.column(getContext());assets.setVisibility(GONE);info.addView(assets,new LinearLayout.LayoutParams(-1,-2));boolean[] loaded={false};
        source.setOnClickListener(v->{boolean show=assets.getVisibility()!=VISIBLE;if(show&&!loaded[0]){sources.accept(assets);loaded[0]=true;}assets.setVisibility(show?VISIBLE:GONE);source.setText(t("Исходники")+(show?"  ▴":"  ▾"));});
        String description=value.optString("description").trim();if(!description.isEmpty()){TextView detail=text("",13,false);detail.setText(description);detail.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));detail.setPadding(0,dp(12),0,dp(4));info.addView(detail);}
        hero.addView(info,new LinearLayout.LayoutParams(0,-2,1));body.addView(hero,new LinearLayout.LayoutParams(-1,-2));
        more=DevGramStudioControls.column(getContext());body.addView(more);loadAuthor(value,request);
        TextView heading=text("Комментарии",15,true);heading.setPadding(0,dp(24),0,dp(12));body.addView(heading);
        TextView add=button(DevGramAppearanceAccess.label(host,"workshop.social",t("Добавить комментарий")),R.drawable.msg_edit,false);add.setOnClickListener(v->action.accept(12));body.addView(add,LayoutHelper.createLinear(-1,44));
        commentList=DevGramStudioControls.column(getContext());body.addView(commentList);comments(comments);
        apply=button(DevGramAppearanceAccess.label(host,"workshop.install",t("Установить в профиль")),0,true);apply.setSingleLine(true);if(android.os.Build.VERSION.SDK_INT>=26)apply.setAutoSizeTextTypeUniformWithConfiguration(11,14,1,2);apply.setOnClickListener(v->action.accept(10));bar.addView(apply,new LinearLayout.LayoutParams(0,dp(48),1));
        View copy=icon("Копировать ссылку",R.drawable.msg_copy);copy.setOnClickListener(v->action.accept(16));bar.addView(copy,LayoutHelper.createLinear(48,48,4,0,0,0));
        favorite=icon("В избранное",R.drawable.msg_fave);favorite.setOnClickListener(v->action.accept(15));bar.addView(favorite,LayoutHelper.createLinear(48,48,4,0,0,0));
        LinearLayout heart=new LinearLayout(getContext());heart.setGravity(Gravity.CENTER);heart.setPadding(dp(6),0,dp(6),0);heart.setBackground(DevGramStudioControls.surface(color(Theme.key_windowBackgroundGray),8));
        ImageView heartIcon=new ImageView(getContext());heartIcon.setImageResource(R.drawable.msg_input_like);heartIcon.setColorFilter(color(Theme.key_windowBackgroundWhiteBlueText));heart.addView(heartIcon,LayoutHelper.createLinear(22,22));
        likeCount=text("",13,true);heart.addView(likeCount,LayoutHelper.createLinear(-2,-2,4,0,0,0));like=heart;heart.setOnClickListener(v->action.accept(11));bar.addView(heart,LayoutHelper.createLinear(72,48,4,0,0,0));
        boolean pending=value.optString("state").equals("pending");apply.setVisibility(pending?GONE:VISIBLE);like.setVisibility(pending?GONE:VISIBLE);
        votes(liked,saved,likes,busy);scroll.scrollTo(0,0);
    }
    private String authorName(long uid){TLRPC.User user=host.getMessagesController().getUser(uid);return user==null?t("Автор")+" · "+uid:UserObject.getUserName(user);}
    private TextView button(CharSequence label,int icon,boolean primary){TextView v=text("",14,true);v.setText(label instanceof String?t(label.toString()):label);v.setGravity(Gravity.CENTER);v.setPadding(dp(8),0,dp(8),0);v.setTextColor(color(primary?Theme.key_featuredStickers_buttonText:Theme.key_windowBackgroundWhiteBlueText));v.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8),color(primary?Theme.key_featuredStickers_addButton:Theme.key_windowBackgroundGray),color(Theme.key_listSelector)));if(icon!=0){android.graphics.drawable.Drawable d=getResources().getDrawable(icon).mutate();d.setColorFilter(v.getCurrentTextColor(),android.graphics.PorterDuff.Mode.SRC_IN);d.setBounds(0,0,dp(20),dp(20));v.setCompoundDrawablePadding(dp(5));v.setCompoundDrawables(d,null,null,null);}return v;}
    private ImageView icon(String label,int drawable){ImageView v=new ImageView(getContext());v.setImageResource(drawable);v.setScaleType(ImageView.ScaleType.CENTER);v.setColorFilter(color(Theme.key_windowBackgroundWhiteBlueText));v.setContentDescription(t(label));v.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8),color(Theme.key_windowBackgroundGray),color(Theme.key_listSelector)));return v;}
    public void votes(boolean liked,boolean saved,int count,boolean busy){if(like==null)return;likeCount.setText(String.valueOf(Math.max(0,count)));like.setSelected(liked);like.setContentDescription(t(liked?"Убрать лайк":"Нравится")+" · "+count);favorite.setSelected(saved);favorite.setContentDescription(t(saved?"Убрать из избранного":"В избранное"));int accent=color(Theme.key_windowBackgroundWhiteBlueText);like.setBackground(DevGramStudioControls.surface(liked?androidx.core.graphics.ColorUtils.setAlphaComponent(accent,35):color(Theme.key_windowBackgroundGray),8));favorite.setBackground(DevGramStudioControls.surface(saved?androidx.core.graphics.ColorUtils.setAlphaComponent(accent,35):color(Theme.key_windowBackgroundGray),8));like.setEnabled(!busy);favorite.setEnabled(!busy);like.setAlpha(busy?.55f:1);favorite.setAlpha(busy?.55f:1);}
    public void applying(boolean busy){if(apply==null)return;apply.setEnabled(!busy);apply.setText(busy?t("Устанавливаю…"):DevGramAppearanceAccess.label(host,"workshop.install",t("Установить в профиль")));apply.setAlpha(busy?.65f:1);}
    private DevGramWorkshopPreviewView preview(JSONObject value,boolean motion){DevGramWorkshopPreviewView view=new DevGramWorkshopPreviewView(getContext(),host.getCurrentAccount(),value,motion);view.setActive(previewsActive);previews.add(view);return view;}
    private void fullscreen(){if(closed||work==null)return;DevGramWorkshopPreviewView large=new DevGramWorkshopPreviewView(getContext(),host.getCurrentAccount(),work,true);large.setActive(previewsActive);previews.add(large);FrameLayout root=new FrameLayout(getContext());root.setBackgroundColor(0xff101114);root.addView(large,new FrameLayout.LayoutParams(-1,Math.round(Math.min(AndroidUtilities.displaySize.y*.78f,AndroidUtilities.displaySize.x*476f/211)),Gravity.CENTER));AlertDialog dialog=new AlertDialog.Builder(host.getParentActivity(),host.getResourceProvider()).setView(root).setPositiveButton(t("Закрыть"),null).create();host.showDialog(dialog,d->{previews.remove(large);large.release();});}
    private void comments(JSONArray values){commentList.removeAllViews();if(values.length()==0){TextView empty=text("Пока нет комментариев",13,false);empty.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));empty.setPadding(0,dp(14),0,dp(10));commentList.addView(empty);}for(int i=0;i<values.length();i++){JSONObject value=values.optJSONObject(i);if(value==null)continue;int index=i;LinearLayout row=DevGramStudioControls.column(getContext());row.setPadding(0,dp(14),0,dp(14));TextView name=text("",13,true);name.setText(authorName(value.optLong("uid")));row.addView(name);TextView message=text("",14,false);message.setText(value.optString("text"));row.addView(message);row.setOnLongClickListener(v->{action.accept(1000+index);return true;});commentList.addView(row);}}
    private void loadAuthor(JSONObject selected,int request){String route="studio/workshop.json?section=new&kind="+selected.optString("kind","profile")+"&author="+selected.optLong("uid");DevGramProfilePolicy.executeWorkshop(()->{JSONObject result=null;try{result=DevGramProfilePolicy.http("GET",route,null);}catch(Exception ignored){}JSONObject received=result;AndroidUtilities.runOnUIThread(()->{if(closed||generation!=request||!DevGramProfileCustomization.enabled()||received==null)return;JSONArray values=received.optJSONArray("works");if(values==null)return;LinearLayout cards=new LinearLayout(getContext());for(int i=0;i<values.length();i++){JSONObject item=values.optJSONObject(i);if(item==null||item.optLong("id")==selected.optLong("id"))continue;LinearLayout card=DevGramStudioControls.column(getContext());DevGramWorkshopPreviewView image=preview(item,false);card.addView(image,LayoutHelper.createLinear(156,288));TextView title=text("",13,true);title.setText(item.optString("title"));title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,dp(8),0,0);card.addView(title);card.setOnClickListener(v->open.accept(item));cards.addView(card,LayoutHelper.createLinear(156,-2,0,0,8,0));}if(cards.getChildCount()>0){TextView heading=text("Другие работы автора",15,true);heading.setPadding(0,dp(24),0,dp(12));more.addView(heading);HorizontalScrollView gallery=new HorizontalScrollView(getContext());gallery.setHorizontalScrollBarEnabled(false);gallery.addView(cards);more.addView(gallery);}});});}
    public void release(){closed=true;generation++;releasePreviews();}
}

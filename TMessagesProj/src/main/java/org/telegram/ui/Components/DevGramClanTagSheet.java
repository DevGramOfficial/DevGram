package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

/** Reference tag sheet: animated community card, rank/top and a direct action. */
public final class DevGramClanTagSheet {
    private static String t(String value){return DevGramLocale.text(value);}
    private static TextView text(Context c,String value,int size,boolean bold,Theme.ResourcesProvider resources){
        TextView v=DevGramStudioControls.text(c,"",size,bold,resources);v.setText(value);return v;
    }
    private static LinearLayout content(BaseFragment host){LinearLayout root=new LinearLayout(host.getParentActivity());root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(16),dp(16),dp(16));return root;}
    private static int dp(float value){return AndroidUtilities.dp(value);}
    private static void present(BaseFragment host,BottomSheet sheet,LinearLayout root){ScrollView scroll=new ScrollView(host.getParentActivity()){@Override protected void onMeasure(int width,int height){super.onMeasure(width,DevGramStudioControls.sheetHeightSpec(height,.85f));}};scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(root);sheet.setCustomView(scroll);sheet.fixNavigationBar();host.showDialog(sheet);}
    public static void show(BaseFragment host,JSONObject tag){
        if(host.getParentActivity()==null||tag==null||!DevGramProfileCustomization.enabled())return;
        int account=host.getCurrentAccount();long viewer=host.getUserConfig().getClientUserId();Theme.ResourcesProvider resources=host.getResourceProvider();
        BottomSheet sheet=new BottomSheet(host.getParentActivity(),false,resources);LinearLayout root=content(host);
        Preview preview=new Preview(host.getParentActivity(),account,tag,resources);root.addView(preview,LayoutHelper.createLinear(-1,-2));
        TextView title=text(host.getParentActivity(),tag.optString("title",tag.optString("tag")),20,true,resources);title.setGravity(Gravity.CENTER);title.setSingleLine(false);title.setEllipsize(null);root.addView(title,LayoutHelper.createLinear(-1,-2,0,10,0,0));
        Runnable source=()->{if(host.getUserConfig().getClientUserId()!=viewer||!DevGramProfileCustomization.enabled())return;sheet.dismiss();openSource(host,tag);};
        TextView author=text(host.getParentActivity(),tag.optString("tag"),14,false,resources);author.setGravity(Gravity.CENTER);author.setSingleLine(false);author.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources));author.setPadding(0,dp(4),0,dp(2));author.setOnClickListener(v->source.run());root.addView(author,LayoutHelper.createLinear(-1,-2));
        TextView note=text(host.getParentActivity(),t(tag.optBoolean("official")?"Официальное сообщество DevGram":"Сообщество, которому принадлежит этот клановый тег"),14,false,resources);note.setGravity(Gravity.CENTER);note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resources));root.addView(note,LayoutHelper.createLinear(-1,-2,0,4,0,16));
        TLRPC.Chat chat=DevGramCommunityProfiles.localChat(account,tag.optLong("chat"));boolean member=chat!=null&&!ChatObject.isNotInChat(chat);
        String openLabel=t(chat==null?"Открыть сообщество":chat.broadcast&&!chat.megagroup?"Открыть канал":"Открыть группу");
        if(member){ButtonWithCounterView open=new ButtonWithCounterView(host.getParentActivity(),false,resources);open.setText(openLabel,false);open.setOnClickListener(v->source.run());root.addView(open,LayoutHelper.createLinear(-1,48,0,0,0,8));}
        ButtonWithCounterView primary=new ButtonWithCounterView(host.getParentActivity(),true,resources);primary.setText(member?t("Выбрать тег"):openLabel,false);root.addView(primary,LayoutHelper.createLinear(-1,48));
        primary.setOnClickListener(v->{
            if(!member){source.run();return;}
            if(host.getUserConfig().getClientUserId()!=viewer||!DevGramProfileCustomization.enabled())return;
            primary.setEnabled(false);primary.setLoading(true);
            // Supply the native membership snapshot before selecting after a
            // temporary verifier has left. The server still controls selection.
            JSONArray joined=new JSONArray();for(TLRPC.Chat c:host.getMessagesController().getChats().values())if(!ChatObject.isNotInChat(c)&&joined.length()<199)joined.put(ChatObject.isChannel(c)?c.id:-c.id);joined.put(tag.optLong("chat"));
            DevGramProfilePolicy.request(account,"studio/clans",org.telegram.ui.DevGramStudioActivity.json("action","available","chats",joined),(available,firstError)->{
                if(!sheet.isShowing()||host.getUserConfig().getClientUserId()!=viewer)return;
                if(available==null){primary.setEnabled(true);primary.setLoading(false);error(host);return;}
                DevGramProfilePolicy.request(account,"studio/clans",org.telegram.ui.DevGramStudioActivity.json("action","select","chat",tag.optLong("chat")),(result,failure)->{
                    if(!sheet.isShowing()||host.getUserConfig().getClientUserId()!=viewer)return;
                    primary.setEnabled(true);primary.setLoading(false);
                    if(result==null){error(host);return;}
                    DevGramClanTags.accept(viewer,result.optJSONObject("tag"));host.getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces,MessagesController.UPDATE_MASK_NAME);sheet.dismiss();
                    BulletinFactory.of(host).createSimpleBulletin(org.telegram.messenger.R.raw.contact_check,t("Тег выбран")).show();
                });
            });
        });
        Runnable policy=()->{if(!DevGramProfileCustomization.enabled()||host.getUserConfig().getClientUserId()!=viewer)sheet.dismiss();};
        DevGramProfilePolicy.listen(policy);sheet.setOnDismissListener(d->DevGramProfilePolicy.unlisten(policy));
        present(host,sheet,root);
        DevGramProfilePolicy.executeWorkshop(()->{
            JSONObject response=null;try{response=DevGramProfilePolicy.http("GET","studio/clans.json",null);}catch(Exception ignored){}
            JSONArray values=response!=null&&response.optBoolean("ok")?response.optJSONArray("clans"):null;
            AndroidUtilities.runOnUIThread(()->{if(!sheet.isShowing()||host.getUserConfig().getClientUserId()!=viewer||values==null)return;int rank=tag.optInt("rank");for(int i=0;i<values.length();i++){JSONObject item=values.optJSONObject(i);if(item!=null&&item.optLong("chat")==tag.optLong("chat")){rank=item.optInt("rank");break;}}preview.setRank(rank,()->{sheet.dismiss();AndroidUtilities.runOnUIThread(()->showTop(host,values),120);});});
        });
    }
    private static void error(BaseFragment host){if(host.getParentActivity()!=null)BulletinFactory.of(host).createErrorBulletin(t("Не удалось выполнить действие. Проверьте доступ и соединение.")).show();}
    private static void openSource(BaseFragment host,JSONObject tag){
        if(host.getParentActivity()==null)return;
        String username=tag.optString("username");TLRPC.Chat chat=DevGramCommunityProfiles.localChat(host.getCurrentAccount(),tag.optLong("chat"));
        if(chat!=null){android.os.Bundle args=new android.os.Bundle();args.putLong("chat_id",chat.id);host.presentFragment(new org.telegram.ui.ChatActivity(args));}
        else if(username.matches("[A-Za-z][A-Za-z0-9_]{3,31}"))org.telegram.messenger.browser.Browser.openUrl(host.getContext(),"https://t.me/"+username);
        else error(host);
    }
    private static void showTop(BaseFragment host,JSONArray values){
        if(host.getParentActivity()==null||!DevGramProfileCustomization.enabled())return;
        BottomSheet sheet=new BottomSheet(host.getParentActivity(),false,host.getResourceProvider());LinearLayout root=content(host);
        root.addView(text(host.getParentActivity(),t("Топ кланов"),20,true,host.getResourceProvider()));
        TextView note=text(host.getParentActivity(),t(values.length()==0?"В рейтинге пока нет действующих кланов.":"Место определяется числом людей, использующих тег."),14,false,host.getResourceProvider());note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,host.getResourceProvider()));root.addView(note,LayoutHelper.createLinear(-1,-2,0,8,0,12));
        for(int i=0;i<values.length();i++){JSONObject value=values.optJSONObject(i);if(value==null)continue;View row=new TopRow(host.getParentActivity(),host.getCurrentAccount(),value,host.getResourceProvider());row.setContentDescription(value.optString("title")+" · "+value.optString("tag")+" · "+value.optInt("count"));row.setOnClickListener(v->{sheet.dismiss();AndroidUtilities.runOnUIThread(()->show(host,value),120);});root.addView(row,LayoutHelper.createLinear(-1,78,0,0,0,7));}
        long viewer=host.getUserConfig().getClientUserId();Runnable policy=()->{if(!DevGramProfileCustomization.enabled()||host.getUserConfig().getClientUserId()!=viewer)sheet.dismiss();};
        DevGramProfilePolicy.listen(policy);sheet.setOnDismissListener(d->DevGramProfilePolicy.unlisten(policy));
        present(host,sheet,root);
    }
    private static final class TopRow extends View {
        private final JSONObject tag;private final Theme.ResourcesProvider resources;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final android.text.TextPaint measure=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box=new RectF();private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;
        private final String rank,source;private int width=-1;private String fittedTitle,fittedSource;
        TopRow(Context c,int account,JSONObject tag,Theme.ResourcesProvider resources){
            super(c);this.tag=tag;this.resources=resources;rank=Integer.toString(Math.max(1,tag.optInt("rank")));
            int count=tag.optInt("count"),last=count%10,hundred=count%100;
            String ru=count+" "+(last==1&&hundred!=11?"профиль":last>=2&&last<=4&&(hundred<12||hundred>14)?"профиля":"профилей");
            source=tag.optString("tag")+" · "+DevGramLocale.text(ru,count+(count==1?" profile":" profiles"));
            emoji=new DevGramBadgeEmojiDrawable(this,dp(32),AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS,()->Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,resources));emoji.setCurrentAccount(account);emoji.set(tag.optLong("emoji"),false);setClickable(true);setFocusable(true);
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();emoji.attach();}
        @Override protected void onDetachedFromWindow(){emoji.detach();super.onDetachedFromWindow();}
        @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);int blue=Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources),black=Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,resources),gray=Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resources);
            paint.setColor((blue&0xffffff)|((isPressed()?25:13)<<24));box.set(0,dp(2),getWidth(),getHeight()-dp(2));canvas.drawRoundRect(box,dp(15),dp(15),paint);
            paint.setTypeface(AndroidUtilities.bold());paint.setTextSize(dp(tag.optInt("rank")>=100?13:16));paint.setColor(tag.optInt("rank")<=3?blue:gray);canvas.drawText(rank,dp(22)-paint.measureText(rank)/2f,getHeight()/2f-(paint.ascent()+paint.descent())/2f,paint);
            paint.setColor((blue&0xffffff)|(26<<24));canvas.drawCircle(dp(55),getHeight()/2f,dp(20),paint);emoji.setColor(black);emoji.setBounds(dp(39),(getHeight()-dp(32))/2,dp(71),(getHeight()+dp(32))/2);emoji.draw(canvas);
            if(width!=getWidth()){width=getWidth();measure.setTypeface(AndroidUtilities.bold());measure.setTextSize(dp(15));fittedTitle=TextUtils.ellipsize(tag.optString("title",tag.optString("tag")),measure,Math.max(0,width-dp(96)),TextUtils.TruncateAt.END).toString();measure.setTypeface(android.graphics.Typeface.DEFAULT);measure.setTextSize(dp(12.5f));fittedSource=TextUtils.ellipsize(source,measure,Math.max(0,width-dp(96)),TextUtils.TruncateAt.END).toString();}
            paint.setTypeface(AndroidUtilities.bold());paint.setTextSize(dp(15));paint.setColor(black);canvas.drawText(fittedTitle,dp(84),dp(31),paint);paint.setTypeface(android.graphics.Typeface.DEFAULT);paint.setTextSize(dp(12.5f));paint.setColor(gray);canvas.drawText(fittedSource,dp(84),dp(53),paint);
        }
    }
    private static final class Preview extends View {
        private final JSONObject tag;
        private final Theme.ResourcesProvider resources;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box=new RectF(),rankHit=new RectF();
        private final android.graphics.drawable.Drawable trophy;private int trophyColor;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;
        private int rank;private Runnable rankAction;private boolean rankPressed;
        private final android.text.TextPaint measure=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private int titleWidth=-1;private String rankText;private android.text.StaticLayout titleLayout;
        Preview(Context c,int account,JSONObject tag,Theme.ResourcesProvider resources){
            super(c);trophy=c.getResources().getDrawable(org.telegram.messenger.R.drawable.devgram_clan_trophy).mutate();this.tag=tag;this.resources=resources;rank=tag.optInt("rank");rankText=rank>0?Integer.toString(rank):"—";emoji=new DevGramBadgeEmojiDrawable(this,dp(38),AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS,()->Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,resources));emoji.setCurrentAccount(account);emoji.set(tag.optLong("emoji"),false);
            setClickable(true);setContentDescription(tag.optString("title")+" · "+tag.optString("tag"));
        }
        void setRank(int rank,Runnable action){this.rank=rank;rankText=rank>0?Integer.toString(rank):"—";rankAction=action;setContentDescription(tag.optString("title")+" · "+tag.optString("tag")+" · "+t("Место в топе кланов: ")+(rank>0?rank:"—"));invalidate();}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();emoji.attach();}
        @Override protected void onDetachedFromWindow(){emoji.detach();super.onDetachedFromWindow();}
        private void measureTitle(int width){
            if(titleWidth==width&&titleLayout!=null)return;titleWidth=width;
            measure.setTypeface(AndroidUtilities.bold());measure.setTextSize(dp(18));
            String title=tag.optString("title");if(title.isEmpty())title=tag.optString("tag");
            int available=Math.max(1,width-dp(145)-(tag.optBoolean("official")?dp(23):0));
            titleLayout=android.text.StaticLayout.Builder.obtain(title,0,title.length(),measure,available).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build();
        }
        @Override protected void onMeasure(int width,int height){int w=View.MeasureSpec.getSize(width);measureTitle(w);setMeasuredDimension(w,Math.max(dp(96),titleLayout.getHeight()+dp(62)));}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);int blue=Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources),black=Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,resources);
            paint.setStyle(Paint.Style.FILL);paint.setColor((blue&0xffffff)|(24<<24));box.set(0,dp(4),getWidth(),getHeight()-dp(4));canvas.drawRoundRect(box,dp(18),dp(18),paint);
            emoji.setColor(black);emoji.setBounds(dp(18),(getHeight()-dp(38))/2,dp(56),(getHeight()+dp(38))/2);emoji.draw(canvas);
            measureTitle(getWidth());measure.setColor(black);float x=dp(70),titleY=(getHeight()-titleLayout.getHeight()-dp(29))/2f;
            int saved=canvas.save();canvas.translate(x,titleY);titleLayout.draw(canvas);canvas.restoreToCount(saved);
            if(tag.optBoolean("official")){int last=titleLayout.getLineCount()-1;float cx=x+titleLayout.getLineRight(last)+dp(11),cy=titleY+titleLayout.getLineBaseline(last)-dp(6);paint.setColor(blue);canvas.drawCircle(cx,cy,dp(8),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.7f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(Color.WHITE);canvas.drawLine(cx-dp(3.5f),cy,cx-dp(.8f),cy+dp(2.7f),paint);canvas.drawLine(cx-dp(.8f),cy+dp(2.7f),cx+dp(4.2f),cy-dp(3),paint);paint.setStyle(Paint.Style.FILL);}
            paint.setTypeface(AndroidUtilities.bold());paint.setTextSize(dp(12));String label=tag.optString("tag");float y=titleY+titleLayout.getHeight()+dp(7),w=paint.measureText(label)+dp(16),tagX=x+(titleLayout.getWidth()-w)/2f;box.set(tagX,y,tagX+w,y+dp(22));paint.setColor((blue&0xffffff)|(42<<24));canvas.drawRoundRect(box,dp(7),dp(7),paint);paint.setColor(blue);canvas.drawText(label,tagX+dp(8),y+dp(15),paint);
            float right=getWidth()-dp(12);rankHit.set(right-dp(58),dp(5),right+dp(5),dp(48));paint.setTextSize(dp(14));paint.setColor(black);String number=rankText;canvas.drawText(number,right-dp(25)-paint.measureText(number),dp(29),paint);
            if(trophyColor!=blue){trophyColor=blue;trophy.setTint(blue);}trophy.setBounds(Math.round(right-dp(19)),dp(11),Math.round(right),dp(30));trophy.draw(canvas);
        }
        @Override public boolean onTouchEvent(MotionEvent event){int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN&&rankAction!=null&&rankHit.contains(event.getX(),event.getY())){rankPressed=true;return true;}if(rankPressed&&(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)){rankPressed=false;if(action==MotionEvent.ACTION_UP&&rankHit.contains(event.getX(),event.getY()))rankAction.run();return true;}return super.onTouchEvent(event);}
    }
    private DevGramClanTagSheet(){}
}

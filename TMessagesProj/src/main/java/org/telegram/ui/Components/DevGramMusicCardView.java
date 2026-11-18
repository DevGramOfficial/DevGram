package org.telegram.ui.Components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.Theme;

/** Native counterpart of ProfileIntegrationCell's cover, service tint, chip and live progress. */
public final class DevGramMusicCardView extends LinearLayout {
    private final Theme.ResourcesProvider provider;
    private final TextView elapsed,total,titleView,artistView;private final ImageView state;
    private final LinearLayout times;private final DevGramMusicCoverView artwork;
    private JSONObject lyricsTrack=new JSONObject();private long lyricsOwner;private int lyricsAccount;private String lyricsBlock="";
    public void setLyricsTarget(int account,long owner,String block){lyricsAccount=account;lyricsOwner=owner;lyricsBlock=block==null?"":block;}
    private final JSONObject lyricsRow;
    private void openLyrics() {
        org.telegram.ui.ActionBar.BaseFragment host=org.telegram.ui.LaunchActivity.getSafeLastFragment();
        if(host==null)return;
        final int account=lyricsAccount;final long owner=lyricsOwner,viewer=UserConfig.getInstance(account).getClientUserId();
        final JSONObject requestRow=DevGramProfileDocument.copy(lyricsRow);
        try{requestRow.put("parts",new org.json.JSONArray().put(0));}catch(Exception ignored){}
        DevGramLyricsSheet.show(host,new DevGramLyricsSheet.Source(){
            private boolean closed,loading;private DevGramMusicEvents events;
            private final Runnable poll=new Runnable(){public void run(){if(!valid())return;load(false);AndroidUtilities.runOnUIThread(this,15000);}};
            private boolean valid(){return !closed&&host.getCurrentAccount()==account&&UserConfig.getInstance(account).getClientUserId()==viewer&&DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(owner,"profile.blocks")&&DevGramProfilePolicy.allowed(owner,"profile.integrations");}
            public void start(){if(!valid())return;if(!requestRow.optString("id").isEmpty()){events=new DevGramMusicEvents(account,owner,requestRow.optString("id"),()->load(true));poll.run();}}
            public void close(){closed=true;AndroidUtilities.cancelRunOnUIThread(poll);if(events!=null){events.close();events=null;}}
            private void load(boolean fresh){
                if(!valid()||loading)return;
                if(fresh)DevGramProfileIntegrations.invalidateRow(owner,requestRow.optString("id"));
                loading=true;DevGramProfileIntegrations.loadRow(account,owner,requestRow,cards->{
                    loading=false;if(!valid())return;
                    for(int i=0;i<cards.length();i++){
                        JSONObject card=cards.optJSONObject(i);if(card==null||!DevGramProfileRowSpec.PROVIDERS[requestRow.optInt("service")].equals(card.optString("provider"))||!requestRow.optString("url").equals(card.optString("handle")))continue;
                        JSONObject track=card.optJSONObject("nowPlaying");
                        if(track==null&&!card.optString("track").isEmpty()){track=DevGramProfileDocument.copy(card);try{track.put("title",card.optString("track"));}catch(Exception ignored){}}
                        updateTrack(track,card.optLong("serverTime"),card.optLong("_receivedAt",SystemClock.elapsedRealtime()),DevGramLocale.text("Музыка сейчас не играет"));break;
                    }
                });
            }
            public DevGramLyricsSheet.State current(){
                if(!valid())return null;String title=lyricsTrack.optString("title",lyricsTrack.optString("track"));if(title.isEmpty())return null;
                org.telegram.messenger.devgram.player.TrackQuery query=new org.telegram.messenger.devgram.player.TrackQuery(lyricsTrack.optString("artist"),title,lyricsTrack.optString("album"),(int)(lyricsTrack.optLong("duration")/1000));
                return new DevGramLyricsSheet.State(query,clock==null?0:clock.positionAt(SystemClock.elapsedRealtime()),clock!=null&&clock.playing&&clock.liveAt(SystemClock.elapsedRealtime()),artwork.getImageReceiver().getBitmap(),owner,lyricsBlock);
            }
            public void seek(long millis){DevGramLyricsSheet.State state=current();MessageObject playing=MediaController.getInstance().getPlayingMessageObject();if(state!=null&&DevGramLyricsAudioSource.local(account,state.query)!=null&&playing!=null)MediaController.getInstance().seekToProgressMs(playing,millis);}
        });
    }
    private String artworkKey="",songKey="",roomKey="";private View roomView;private TextView noticeView;
    private final java.util.ArrayList<TextView> factViews=new java.util.ArrayList<>();
    private final Progress line;
    private final Rect visibleRect=new Rect();
    private DevGramPlaybackState clock;private LinearLayout chips;
    private boolean attached;
    private long lastSecond=-1;
    private final ViewTreeObserver.OnScrollChangedListener scroll=this::sync;
    private final Runnable frame=new Runnable(){public void run(){if(!visible())return;long now=SystemClock.elapsedRealtime();long position=clock==null?0:clock.positionAt(now);if(position/1000!=lastSecond){lastSecond=position/1000;elapsed.setText(DevGramPlaybackState.clock(position));}line.invalidate();if(clock!=null&&clock.playing&&clock.liveAt(now)&&clock.positionAt(now)<clock.duration)postDelayed(this,100);}};
    private static int dp(float n){return AndroidUtilities.dp(n);}
    private int color(int key){return Theme.getColor(key,provider);}
    private TextView text(String value,int size,boolean bold,boolean soft){TextView v=DevGramStudioControls.text(getContext(),"",size,bold,provider);v.setText(value);v.setTextColor(color(soft?Theme.key_windowBackgroundWhiteGrayText2:Theme.key_windowBackgroundWhiteBlackText));v.setMaxLines(1);v.setEllipsize(android.text.TextUtils.TruncateAt.END);return v;}
    public DevGramMusicCardView(Context context,int service,JSONObject row,JSONObject playing,long serverTime,long receivedAt,Theme.ResourcesProvider provider,boolean preview,Runnable listen,Runnable open){
        super(context);this.provider=provider;lyricsRow=DevGramProfileDocument.copy(row);setOrientation(VERTICAL);setPadding(dp(13),dp(7),dp(13),dp(7));
        int[] colors={0xffd51007,0xff24292f,0xff1b2838,0xffffcc00,0xff1db954,0xffff5500,0xff0077ff,0xff5865f2,0xff546e7a};int accent=colors[Math.max(0,Math.min(8,service))];
        GradientDrawable base=new GradientDrawable();base.setColor((color(Theme.key_windowBackgroundWhiteBlackText)&0xffffff)|0x1f000000);base.setCornerRadius(dp(16));GradientDrawable tint=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{(accent&0xffffff)|0x2b000000,accent&0xffffff});tint.setCornerRadius(dp(16));setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{base,tint}));
        JSONObject track=playing==null?new JSONObject():playing;lyricsTrack=track;lyricsAccount=UserConfig.selectedAccount;
        String title=track.optString("title",track.optString("track")),artist=track.optString("artist");
        if(title.isEmpty())title=row.optString("_musicEmptyLabel",DevGramLocale.text(preview?"Название трека":"Музыка сейчас не играет"));
        if(artist.isEmpty()&&preview)artist=DevGramLocale.text("Исполнитель");
        String cover=track.optString("cover"),encoded=track.optString("coverData");
        LinearLayout main=new LinearLayout(context);main.setGravity(Gravity.CENTER_VERTICAL);main.setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
        FrameLayout art=new FrameLayout(context);GradientDrawable tile=new GradientDrawable();tile.setColor(accent);tile.setCornerRadius(dp(13));art.setBackground(tile);
        ImageView fallback=new ImageView(context);fallback.setImageDrawable(DevGramServiceCards.mark(context,service));fallback.setPadding(dp(11),dp(11),dp(11),dp(11));fallback.setScaleType(ImageView.ScaleType.CENTER);fallback.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);art.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        artwork=new DevGramMusicCoverView(context);artwork.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);art.addView(artwork,new FrameLayout.LayoutParams(-1,-1));songKey=track.optString("title",track.optString("track"))+"\n"+track.optString("artist");artworkKey=encoded+"\n"+cover;if(!encoded.isEmpty()||cover.startsWith("https://"))artwork.bind(encoded,cover);
        main.addView(art,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout names=new LinearLayout(context);names.setOrientation(VERTICAL);String label=row.optString("title");names.addView(text(label.isEmpty()?DevGramLocale.text(DevGramProfileRowSpec.SERVICES[service]):label,12,false,true));titleView=text(title,16,true,false);names.addView(titleView);artistView=text(artist,12,false,true);artistView.setVisibility(artist.isEmpty()?GONE:VISIBLE);names.addView(artistView);
        LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(0,-2,1);nameParams.setMarginStart(dp(11));nameParams.setMarginEnd(dp(8));main.addView(names,nameParams);
        state=new ImageView(context);state.setScaleType(ImageView.ScaleType.CENTER_INSIDE);state.setPadding(dp(5),dp(8),dp(5),dp(8));state.setColorFilter(color(Theme.key_windowBackgroundWhiteBlackText));String current=track.optString("state",track.optBoolean("playing",true)?"playing":"paused");state.setImageResource("paused".equals(current)?R.drawable.ic_action_pause:R.drawable.ic_action_play);state.setContentDescription(DevGramLocale.text("paused".equals(current)?"На паузе":"Воспроизводится"));state.setVisibility(playing!=null&&(current.equals("playing")||current.equals("paused"))?VISIBLE:GONE);main.addView(state,new LinearLayout.LayoutParams(dp(32),dp(40)));
        addView(main,new LinearLayout.LayoutParams(-1,-2));
        long duration=track.optLong("duration"),position=track.optLong("position");long updated=track.optLong("updatedMs",track.optLong("updated")*1000);
        clock=new DevGramPlaybackState(position,duration,"playing".equals(current),track.optDouble("speed",1),updated,serverTime,receivedAt);
        line=new Progress(context,accent);addView(line,LayoutHelper.createLinear(-1,3,0,7,0,0));
        times=new LinearLayout(context);elapsed=text(DevGramPlaybackState.clock(clock.positionAt(SystemClock.elapsedRealtime())),11,false,true);total=text(DevGramPlaybackState.clock(duration),11,false,true);total.setGravity(Gravity.RIGHT);times.addView(elapsed,new LinearLayout.LayoutParams(0,-2,1));times.addView(total,new LinearLayout.LayoutParams(-2,-2));addView(times,LayoutHelper.createLinear(-1,-2,0,4,0,0));if(duration<=0||!current.equals("playing")&&!current.equals("paused")){line.setVisibility(GONE);times.setVisibility(GONE);}
        if(!preview){LinearLayout lyricsActions=chips();TextView lyrics=chip(DevGramLocale.text("Текст песни"),R.drawable.formatting_quote);lyrics.setOnClickListener(v->openLyrics());lyricsActions.addView(lyrics,new LinearLayout.LayoutParams(-2,dp(24)));}
        if(service==3||service==4||service==5){LinearLayout actions=chips();TextView together=chip(DevGramLocale.text("Прослушивание"),R.drawable.msg_voice_headphones);together.setOnClickListener(v->listen.run());LinearLayout.LayoutParams togetherLayout=new LinearLayout.LayoutParams(-2,dp(24));if(actions.getChildCount()>0)togetherLayout.setMarginStart(dp(6));actions.addView(together,togetherLayout);TextView source=chip(DevGramLocale.text("Открыть"),R.drawable.msg_openin);source.setOnClickListener(v->open.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(24));lp.setMarginStart(dp(6));actions.addView(source,lp);}
    }
    private LinearLayout chips(){if(chips!=null)return chips;chips=new LinearLayout(getContext());chips.setGravity(Gravity.CENTER_VERTICAL);DevGramMusicChipsView scroll=new DevGramMusicChipsView(getContext());scroll.addView(chips,new android.widget.HorizontalScrollView.LayoutParams(-2,dp(24)));addView(scroll,LayoutHelper.createLinear(-1,24,0,8,0,0));return chips;}
    public void setFacts(java.util.List<String> facts){
        while(factViews.size()>facts.size()){TextView value=factViews.remove(factViews.size()-1);chips.removeView(value);}
        for(int i=0;i<facts.size();i++){String fact=facts.get(i);if(i<factViews.size()){TextView value=factViews.get(i);if(!fact.contentEquals(value.getText()))value.setText(fact);continue;}LinearLayout line=chips();TextView value=chip(fact,0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(24));if(line.getChildCount()>0)lp.setMarginStart(dp(6));line.addView(value,lp);factViews.add(value);}
    }
    public void setNotice(String notice){if(noticeView==null){if(notice.isEmpty())return;noticeView=text(notice,12,false,true);noticeView.setSingleLine(false);noticeView.setMaxLines(4);noticeView.setEllipsize(null);addView(noticeView,LayoutHelper.createLinear(-1,-2,0,8,0,0));}if(!notice.contentEquals(noticeView.getText()))noticeView.setText(notice);noticeView.setVisibility(notice.isEmpty()?GONE:VISIBLE);}
    /** Update the existing card: playback packets never replace its cover or chip viewport. */
    public void updateTrack(JSONObject value,long serverTime,long receivedAt,String emptyLabel){
        JSONObject track=value==null?new JSONObject():value;lyricsTrack=track;String title=track.optString("title",track.optString("track")),artist=track.optString("artist"),identity=title+"\n"+artist;
        if(title.isEmpty())title=emptyLabel;if(!title.contentEquals(titleView.getText()))titleView.setText(title);if(!artist.contentEquals(artistView.getText()))artistView.setText(artist);artistView.setVisibility(artist.isEmpty()?GONE:VISIBLE);
        String encoded=track.optString("coverData"),cover=track.optString("cover"),key=encoded+"\n"+cover;
        if(!key.equals(artworkKey)&&(!encoded.isEmpty()||cover.startsWith("https://")||!identity.equals(songKey))){artworkKey=key;artwork.bind(encoded,cover);}songKey=identity;
        String current=track.optString("state",track.optBoolean("playing",true)?"playing":"paused");boolean live=value!=null&&(current.equals("playing")||current.equals("paused"));state.setVisibility(live?VISIBLE:GONE);
        boolean progress=live&&track.optLong("duration")>0;line.setVisibility(progress?VISIBLE:GONE);times.setVisibility(progress?VISIBLE:GONE);String duration=DevGramPlaybackState.clock(track.optLong("duration"));if(!duration.contentEquals(total.getText()))total.setText(duration);updateClock(track,serverTime,receivedAt);
    }
    public void updateClock(JSONObject track,long serverTime,long receivedAt){
        if(track==null)return;String value=track.optString("state",track.optBoolean("playing",true)?"playing":"paused");long updated=track.optLong("updatedMs",track.optLong("updated")*1000);
        clock=new DevGramPlaybackState(track.optLong("position"),track.optLong("duration"),value.equals("playing"),track.optDouble("speed",1),updated,serverTime,receivedAt);
        state.setImageResource(value.equals("paused")?R.drawable.ic_action_pause:R.drawable.ic_action_play);state.setContentDescription(DevGramLocale.text(value.equals("paused")?"На паузе":"Воспроизводится"));lastSecond=-1;sync();
    }
    public void setRoom(int account,org.json.JSONArray people,Runnable open){
        String key=people==null?"":people.toString();if(key.equals(roomKey))return;roomKey=key;if(roomView!=null){removeView(roomView);roomView=null;}if(people==null||people.length()==0)return;
        LinearLayout room=new LinearLayout(getContext());room.setGravity(Gravity.CENTER_VERTICAL);FrameLayout faces=new FrameLayout(getContext());int count=Math.min(5,people.length());
        for(int i=count-1;i>=0;i--){long uid=people.optLong(i);org.telegram.tgnet.TLRPC.User user=MessagesController.getInstance(account).getUser(uid);BackupImageView image=new BackupImageView(getContext());image.setRoundRadius(dp(10));AvatarDrawable fallback=new AvatarDrawable();if(user!=null){fallback.setInfo(user);image.setForUserOrChat(user,fallback);}else{fallback.setInfo(uid,DevGramLocale.text("Участник"),"");image.setImageDrawable(fallback);}faces.addView(image,LayoutHelper.createFrame(20,20,Gravity.LEFT|Gravity.CENTER_VERTICAL,i*14,0,0,0));}
        room.addView(faces,new LinearLayout.LayoutParams(dp(20+(count-1)*14),dp(24)));TextView label=text(DevGramLocale.text("Комната")+" · "+people.length(),12,false,false);room.addView(label,LayoutHelper.createLinear(-2,-2,7,0,0,0));room.setOnClickListener(v->open.run());roomView=room;addView(room,LayoutHelper.createLinear(-1,28,0,7,0,0));
    }
    private TextView chip(String label,int icon){TextView v=text(label,12,false,false);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);if(icon!=0){android.graphics.drawable.Drawable d=getResources().getDrawable(icon).mutate();d.setTint(v.getCurrentTextColor());d.setBounds(0,0,dp(16),dp(16));v.setCompoundDrawablePadding(dp(5));v.setCompoundDrawablesRelative(d,null,null,null);}GradientDrawable back=new GradientDrawable();back.setColor((v.getCurrentTextColor()&0xffffff)|0x18000000);back.setCornerRadius(dp(16));v.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(12),(v.getCurrentTextColor()&0xffffff)|0x24000000,color(Theme.key_listSelector)));return v;}
    private boolean visible(){return attached&&isShown()&&getWindowVisibility()==VISIBLE&&hasWindowFocus()&&getGlobalVisibleRect(visibleRect);}
    private void sync(){removeCallbacks(frame);if(visible())post(frame);}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){super.onLayout(changed,left,top,right,bottom);if(line!=null)sync();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;getViewTreeObserver().addOnScrollChangedListener(scroll);sync();}
    @Override protected void onDetachedFromWindow(){attached=false;removeCallbacks(frame);if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnScrollChangedListener(scroll);super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);sync();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(line!=null)sync();}
    private final class Progress extends View {private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF rect=new RectF();private final int accent;Progress(Context c,int accent){super(c);this.accent=accent;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}@Override protected void onDraw(Canvas canvas){float p=clock==null?-1:clock.progressAt(SystemClock.elapsedRealtime());if(p<0)return;rect.set(0,0,getWidth(),getHeight());paint.setColor((color(Theme.key_windowBackgroundWhiteBlackText)&0xffffff)|0x33000000);canvas.drawRoundRect(rect,getHeight()/2f,getHeight()/2f,paint);rect.right=getWidth()*p;paint.setColor(accent);canvas.drawRoundRect(rect,getHeight()/2f,getHeight()/2f,paint);}}
}

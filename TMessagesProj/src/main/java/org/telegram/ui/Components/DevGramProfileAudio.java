package org.telegram.ui.Components;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import java.io.File;
import java.util.function.ToLongFunction;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.messenger.voip.VoIPService;

/** One foreground profile soundtrack. Galleries and editor thumbnails never own a player. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class DevGramProfileAudio implements NotificationCenter.NotificationCenterDelegate {
    private final View host;
    private final int account;
    private final long owner,viewer;
    private final ToLongFunction<String> position;
    private final BooleanSupplier stories;
    private final AudioManager audio;
    private final PowerManager power;
    private JSONObject document=new JSONObject();
    private ExoPlayer player;
    private MessageObject object;
    private AudioManager.OnAudioFocusChangeListener focus;
    private boolean visible,closed,loading,requested,suppressed;
    private int message,generation,focusEpoch,failedMessage;
    private String slot="page";
    private long lastOwnStop=-10000;
    private final Runnable poll=this::tick;
    private final Runnable policy=this::tick;
    public static boolean enabled(){return MessagesController.getGlobalMainSettings().getBoolean("devgramProfileSound",true);}
    public static void setEnabled(boolean enabled){MessagesController.getGlobalMainSettings().edit().putBoolean("devgramProfileSound",enabled).apply();DevGramProfilePolicy.changed();}
    public DevGramProfileAudio(View host,int account,long owner,ToLongFunction<String> position,BooleanSupplier stories){
        this.host=host;this.account=account;this.owner=owner;this.position=position;this.stories=stories;viewer=UserConfig.getInstance(account).getClientUserId();
        audio=(AudioManager)host.getContext().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        power=(PowerManager)host.getContext().getApplicationContext().getSystemService(Context.POWER_SERVICE);
    }
    public void update(JSONObject document){this.document=document;tick();}
    public void setVisible(boolean value){
        if(closed||visible==value)return;visible=value;generation++;
        if(value){suppressed=false;failedMessage=0;listen(true);tick();}
        else{host.removeCallbacks(poll);loading=false;object=null;message=0;requested=false;stop();listen(false);}
    }
    private void listen(boolean value){
        NotificationCenter local=NotificationCenter.getInstance(account),global=NotificationCenter.getGlobalInstance();
        for(int event:new int[]{NotificationCenter.messagePlayingDidStart,NotificationCenter.recordStarted}){if(value)local.addObserver(this,event);else local.removeObserver(this,event);}
        if(value){global.addObserver(this,NotificationCenter.didStartedCall);global.addObserver(this,NotificationCenter.playerDidStartPlaying);DevGramProfilePolicy.listen(policy);}else{global.removeObserver(this,NotificationCenter.didStartedCall);global.removeObserver(this,NotificationCenter.playerDidStartPlaying);DevGramProfilePolicy.unlisten(policy);}
    }
    @Override public void didReceivedNotification(int id,int account,Object...args){silence();}
    private void silence(){suppressed=true;stop();}
    private boolean competing(){MediaController media=MediaController.getInstance();return stories.getAsBoolean()||VoIPService.getSharedInstance()!=null||media.isRecordingAudio()||(media.getPlayingMessageObject()!=null&&!media.isMessagePaused())||audio==null||audio.getMode()!=AudioManager.MODE_NORMAL;}
    private int selected(){
        JSONObject banner=DevGramProfileMedia.resolve(document,owner,"banner"),page=DevGramProfileMedia.resolve(document,owner,"page");
        return DevGramProfileAudioPolicy.select(document.optInt("bannerMode")==3&&banner!=null&&banner.optString("kind").equals("video"),document.optInt("bannerSound")==1,document.optInt("bannerVolume",20),document.optInt("pageMode")==3&&page!=null&&page.optString("kind").equals("video"),document.optInt("pageSound")==1,document.optInt("pageVolume",20));
    }
    private void later(){if(visible&&!closed)host.postDelayed(poll,500);}
    private void tick(){
        host.removeCallbacks(poll);if(closed||!visible)return;
        if(UserConfig.getInstance(account).getClientUserId()!=viewer){setVisible(false);return;}
        int selected=selected();
        boolean allowed=DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(owner,"profile.media.animated");
        if(!enabled()||!allowed||selected==0){generation++;loading=false;object=null;message=0;requested=false;stop();return;}
        String nextSlot=selected==1?"banner":"page";
        int next=DevGramProfileMedia.message(document,owner,nextSlot);
        if(next<=0){stop();return;}
        if(next!=message||!slot.equals(nextSlot)){
            stop();int request=++generation;message=next;slot=nextSlot;object=null;loading=true;requested=false;failedMessage=0;suppressed=false;
            DevGramProfileBackground.load(account,next,result->{if(closed||!visible||request!=generation)return;loading=false;object=result;if(result==null)failedMessage=next;tick();});
        }
        if(failedMessage==message)return;
        if(competing()){silence();return;}
        if(!DevGramProfileAudioPolicy.mayPlay(visible,host.isShown()&&host.isAttachedToWindow(),power==null||power.isInteractive(),enabled(),allowed,suppressed,false)){stop();later();return;}
        if(object==null){later();return;}
        org.telegram.tgnet.TLRPC.Document file=object.getDocument();if(file==null){failedMessage=message;stop();return;}
        File path=FileLoader.getInstance(account).getPathToAttach(file,true);
        if(!path.isFile()){File original=FileLoader.getInstance(account).getPathToMessage(object.messageOwner);if(original.isFile())path=original;}
        if(!path.isFile()){
            if(!requested){requested=true;FileLoader.getInstance(account).loadFile(file,object,FileLoader.PRIORITY_LOW,1);}
            later();return;
        }
        try {
        if(player==null){
            if(audio.isMusicActive()){if(SystemClock.uptimeMillis()-lastOwnStop<800){later();return;}silence();return;}
            final int epoch=++focusEpoch;focus=change->{if(change<0)AndroidUtilities.runOnUIThread(()->{if(visible&&epoch==focusEpoch)silence();});};
            if(audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){focus=null;suppressed=true;return;}
            DefaultTrackSelector tracks=new DefaultTrackSelector(host.getContext());tracks.setParameters(tracks.getParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,true).build());
            ExoPlayer current=new ExoPlayer.Builder(host.getContext().getApplicationContext()).setTrackSelector(tracks).build();player=current;
            current.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),false);
            current.setHandleAudioBecomingNoisy(true);current.setRepeatMode(Player.REPEAT_MODE_ALL);
            current.addListener(new Player.Listener(){
                @Override public void onPlayerError(PlaybackException error){if(player==current){failedMessage=message;silence();}}
                @Override public void onTracksChanged(Tracks value){if(player!=current||value.getGroups().isEmpty())return;boolean found=false;for(Tracks.Group group:value.getGroups())if(group.getType()==C.TRACK_TYPE_AUDIO)found=true;if(!found){failedMessage=message;silence();}}
                @Override public void onPlayWhenReadyChanged(boolean ready,int reason){if(player==current&&!ready&&reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)silence();}
            });
            current.setVolume(DevGramProfileAudioPolicy.volume(document.optInt(slot+"Volume",20))/100f);current.setMediaItem(MediaItem.fromUri(Uri.fromFile(path)));current.prepare();
            long video=position.applyAsLong(slot);if(video>=0)current.seekTo(video);current.play();
        }else{
            player.setVolume(DevGramProfileAudioPolicy.volume(document.optInt(slot+"Volume",20))/100f);
            // The independent image decoder may pause or reuse cached frames. Never rewind audio to its clock.
        }
        } catch (RuntimeException error) {FileLog.e(error);failedMessage=message;silence();return;}
        later();
    }
    private void stop(){
        focusEpoch++;ExoPlayer old=player;player=null;if(old!=null){lastOwnStop=SystemClock.uptimeMillis();old.release();}
        if(focus!=null){audio.abandonAudioFocus(focus);focus=null;}
    }
    public void release(){if(closed)return;setVisible(false);closed=true;generation++;host.removeCallbacks(poll);stop();}
}

package org.telegram.messenger;

import android.os.SystemClock;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONObject;

/** Follows the sharing owner's snapshots. An unchanged song never restarts on a heartbeat. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class DevGramListenTogether {
    private static ExoPlayer player;private static int account,generation;private static long owner,viewer,lastBeat;private static String block="",trackKey="";private static boolean preview,polling,refreshPending;
    private static final Runnable tick=DevGramListenTogether::poll;
    private static final Runnable policy=()->{if(player!=null&&(!DevGramProfileCustomization.enabled()||!DevGramProfilePolicy.allowed(owner,"profile.integrations")||!DevGramProfilePolicy.allowed(owner,"music.activity")||!DevGramProfilePolicy.allowed(owner,"music.listening")||!DevGramProfilePolicy.allowed(viewer,"music.listening")))stop();};
    private DevGramListenTogether(){}
    public static boolean follows(long uid,String id){return player!=null&&owner==uid&&block.equals(id);}
    private static JSONObject payload(String action){JSONObject p=new JSONObject();try{p.put("owner",owner).put("block",block).put("action",action);}catch(Exception ignored){}return p;}
    public static void start(int selectedAccount,long uid,String id,Runnable changed){
        stop();if(!DevGramProfilePolicy.allowed(UserConfig.getInstance(selectedAccount).getClientUserId(),"music.listening")){changed.run();return;}account=selectedAccount;owner=uid;block=id;viewer=UserConfig.getInstance(account).getClientUserId();int request=++generation;final int startedAccount=selectedAccount;final long startedOwner=uid;final String startedBlock=id;
        DevGramProfilePolicy.request(account,"studio/listen/state",payload("resolve"),(data,error)->{if(request!=generation)return;if(UserConfig.getInstance(startedAccount).getClientUserId()!=viewer||!DevGramProfileCustomization.enabled()||!DevGramProfilePolicy.allowed(viewer,"music.listening")||!DevGramProfilePolicy.allowed(owner,"music.listening")){stop();return;}
            if(data==null||!data.optBoolean("available")||data.optJSONObject("stream")==null){Toast.makeText(ApplicationLoader.applicationContext,DevGramLocale.text("Сервис не предоставил аудио для совместного прослушивания. Проверьте подключение и разрешения музыкального сервиса."),Toast.LENGTH_LONG).show();changed.run();return;}
            apply(data,true);DevGramProfilePolicy.listen(policy);DevGramProfilePolicy.request(account,"studio/listen/state",payload("join"),(joined,failure)->{if(request!=generation){try{JSONObject leave=new JSONObject().put("owner",startedOwner).put("block",startedBlock).put("action","leave");DevGramProfilePolicy.request(startedAccount,"studio/listen/state",leave,(d,e)->{});}catch(Exception ignored){}return;}if(joined==null){stop();changed.run();return;}lastBeat=SystemClock.elapsedRealtime();changed.run();AndroidUtilities.runOnUIThread(tick,2000);});
        });
    }
    private static void apply(JSONObject data,boolean initial){
        JSONObject track=data.optJSONObject("track");if(track==null)track=new JSONObject();String key=track.optString("trackId")+"\n"+track.optString("title",track.optString("track"))+"\n"+track.optString("artist");
        if(initial){JSONObject stream=data.optJSONObject("stream");if(stream==null)return;String url=stream.optString("url");if(!url.startsWith("https://"))return;
            if(player==null){player=new ExoPlayer.Builder(ApplicationLoader.applicationContext).build();player.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);
            player.addListener(new Player.Listener(){@Override public void onPlayerError(androidx.media3.common.PlaybackException error){stop();}@Override public void onPlayWhenReadyChanged(boolean ready,int reason){if(!ready&&reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)stop();}});}
            preview=stream.optBoolean("previewOnly");trackKey=key;player.setMediaItem(MediaItem.fromUri(url));player.prepare();
        }
        if(player==null)return;
        long updated=track.optLong("updatedMs",track.optLong("updated")*1000),now=SystemClock.elapsedRealtime();boolean playing=track.optString("state",track.optBoolean("playing",true)?"playing":"paused").equals("playing");
        DevGramPlaybackState clock=new DevGramPlaybackState(track.optLong("position"),track.optLong("duration"),playing,track.optDouble("speed",1),updated,data.optLong("serverTime"),now);
        long target=preview?0:clock.positionAt(now);if(initial||!preview&&Math.abs(player.getCurrentPosition()-target)>1800)player.seekTo(target);
        player.setPlayWhenReady(playing);
    }
    private static void poll(){
        if(player==null)return;if(polling){refreshPending=true;return;}if(UserConfig.getInstance(account).getClientUserId()!=viewer||!DevGramProfileCustomization.enabled()){stop();return;}int request=generation;
        String action=SystemClock.elapsedRealtime()-lastBeat>25000?"heartbeat":"status";polling=true;
        DevGramProfilePolicy.request(account,"studio/listen/state",payload(action),(data,error)->{if(request!=generation||player==null)return;polling=false;if(refreshPending){refreshPending=false;AndroidUtilities.runOnUIThread(tick);return;}if(data==null||!data.optBoolean("enabled")){stop();return;}
            if(action.equals("heartbeat"))lastBeat=SystemClock.elapsedRealtime();JSONObject track=data.optJSONObject("track");if(track==null){stop();return;}String key=track.optString("trackId")+"\n"+track.optString("title",track.optString("track"))+"\n"+track.optString("artist");
            if(!key.equals(trackKey)){player.setPlayWhenReady(false);polling=true;DevGramProfilePolicy.request(account,"studio/listen/state",payload("resolve"),(next,failure)->{if(request!=generation||player==null)return;polling=false;if(refreshPending){refreshPending=false;AndroidUtilities.runOnUIThread(tick);return;}if(next==null||!next.optBoolean("available")){stop();return;}apply(next,true);AndroidUtilities.runOnUIThread(tick,2000);});return;}apply(data,false);AndroidUtilities.runOnUIThread(tick,2000);
        });
    }
    public static void refresh(){if(player!=null){AndroidUtilities.cancelRunOnUIThread(tick);AndroidUtilities.runOnUIThread(tick);}}
    public static void stop(){
        generation++;polling=false;refreshPending=false;AndroidUtilities.cancelRunOnUIThread(tick);DevGramProfilePolicy.unlisten(policy);
        if(player!=null){player.release();player=null;DevGramProfilePolicy.request(account,"studio/listen/state",payload("leave"),(d,e)->{});}trackKey="";
    }
}

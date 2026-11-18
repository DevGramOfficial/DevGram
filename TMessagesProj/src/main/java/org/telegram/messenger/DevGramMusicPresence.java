package org.telegram.messenger;

import android.os.SystemClock;
import org.json.JSONObject;
import org.json.JSONArray;

/** Explicit account-scoped music sharing. Voice messages and notification bodies are never published. */
public final class DevGramMusicPresence {
    private static JSONObject external;private static JSONArray externalSources=new JSONArray();
    private static long externalAt;
    private static boolean started, sent;
    private static long publishingUid;
    private static long lastSent;
    private static final java.util.HashMap<String,String> sentCovers=new java.util.HashMap<>();
    private static String lastTrack = "";
    private static JSONArray lastSnapshot=new JSONArray();
    private static final DispatchQueue publishQueue=new DispatchQueue("DevGram-music-publish");
    private static final java.util.LinkedHashMap<Long,Runnable> pendingPublishes=new java.util.LinkedHashMap<>();
    private static boolean publishing;
    private static synchronized void enqueue(long uid,Runnable action){pendingPublishes.put(uid,action);if(publishing)return;publishing=true;publishQueue.postRunnable(()->{for(;;){Runnable next;synchronized(DevGramMusicPresence.class){if(pendingPublishes.isEmpty()){publishing=false;return;}long first=pendingPublishes.keySet().iterator().next();next=pendingPublishes.remove(first);}try{next.run();}catch(Exception ignored){}}});}
    private static final Runnable tick = DevGramMusicPresence::update;
    private static final NotificationCenter.NotificationCenterDelegate playback=(id,account,args)->{if(enabled()){AndroidUtilities.cancelRunOnUIThread(tick);AndroidUtilities.runOnUIThread(tick);}};
    private static final Runnable policyChanged = () -> { AndroidUtilities.cancelRunOnUIThread(tick); AndroidUtilities.runOnUIThread(tick); };
    private DevGramMusicPresence() {}

    public static void configure(int account, boolean enabled) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        android.content.SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        if (!DevGramProfilePolicy.allowed(uid,"music.activity")) {start();policyChanged.run();return;}
        enabled |= DevGramCommunityProfiles.musicActivity(uid) || DevGramProfileDocument.number(uid,"activity",0)==1;
        if (enabled) prefs.edit().putLong("devgramPresenceUid", uid).apply();
        else if (prefs.getLong("devgramPresenceUid", 0) == uid) prefs.edit().remove("devgramPresenceUid").apply();
        DevGramMusicSources.sync(account);start(); policyChanged.run();
        DevGramMusicListener.refreshCurrent();
    }
    public static void start() {
        if (started) return; started = true;
        DevGramProfilePolicy.listen(policyChanged);
        for(int i=0;i<UserConfig.MAX_ACCOUNT_COUNT;i++){NotificationCenter center=NotificationCenter.getInstance(i);center.addObserver(playback,NotificationCenter.messagePlayingDidStart);center.addObserver(playback,NotificationCenter.messagePlayingPlayStateChanged);center.addObserver(playback,NotificationCenter.messagePlayingDidSeek);center.addObserver(playback,NotificationCenter.messagePlayingSpeedChanged);center.addObserver(playback,NotificationCenter.messagePlayingDidReset);center.addObserver(playback,NotificationCenter.messagePlayingGoingToStop);}
        AndroidUtilities.runOnUIThread(tick);
    }
    public static void artworkReady(){if(started)AndroidUtilities.runOnUIThread(policyChanged);}
    public static void external(JSONObject value) {
        externalSources=new JSONArray();external = value; externalAt = SystemClock.elapsedRealtime();
        if (started) policyChanged.run();
    }
    public static void externalSources(JSONArray values){externalSources=values;external=values.optJSONObject(0);externalAt=SystemClock.elapsedRealtime();if(started)policyChanged.run();}
    private static int account(long uid) {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) if (UserConfig.getInstance(i).isClientActivated() && UserConfig.getInstance(i).getClientUserId() == uid) return i;
        return -1;
    }
    public static boolean enabled() {
        long uid = MessagesController.getGlobalMainSettings().getLong("devgramPresenceUid", 0);
        return account(uid) >= 0 && DevGramProfilePolicy.allowed(uid, "music.activity") && (DevGramProfileDocument.number(uid, "activity", 0) == 1 || DevGramCommunityProfiles.musicActivity(uid));
    }
    private static String clean(String value) { if (value == null) return ""; value = value.replaceAll("[\\p{Cntrl}]", " "); return value.length() > 200 ? value.substring(0, 200) : value; }
    private static void update() {
        AndroidUtilities.cancelRunOnUIThread(tick);
        long uid = MessagesController.getGlobalMainSettings().getLong("devgramPresenceUid", 0);
        int account = account(uid);
        boolean enabled = account >= 0 && DevGramProfilePolicy.allowed(uid, "music.activity") && (DevGramProfileDocument.number(uid, "activity", 0) == 1 || DevGramCommunityProfiles.musicActivity(uid));
        if (!enabled) { externalSources=new JSONArray();external = null; externalAt = 0; lastTrack = ""; lastSent = 0; }
        JSONObject value = null;JSONArray sources=new JSONArray();
        if (enabled) {
            MediaController controller = MediaController.getInstance(); MessageObject playing = controller.getPlayingMessageObject();
            if (playing != null && playing.currentAccount == account && playing.isMusic() &&DevGramMusicSources.selected(uid,"devgram")) {
                value = new JSONObject(); try { value.put("source","devgram").put("title", clean(playing.getMusicTitle())).put("artist", clean(playing.getMusicAuthor()))
                        .put("position", Math.max(0, playing.audioProgressSec) * 1000L).put("duration", Math.max(0, Math.round(playing.getDuration()*1000))).put("state",controller.isMessagePaused()?"paused":"playing").put("speed",controller.isMessagePaused()?0:Math.max(0,Math.min(4,controller.getPlaybackSpeed(true))));
                    org.telegram.messenger.audioinfo.AudioInfo info=controller.getAudioInfo();if(info!=null){value.put("album",clean(info.getAlbum()));DevGramMusicArtwork.remember(uid,"devgram",value.optString("title"),value.optString("artist"),info.getCover());} } catch (Exception ignored) {}
            }
            if(value!=null)try{sources.put(DevGramProfileDocument.copy(value).put("source","devgram").put("app","DevGram"));}catch(Exception ignored){}
            if(!DevGramMusicSources.publicSource(uid,"devgram"))value=null;
            if(SystemClock.elapsedRealtime()-externalAt<90000){for(int i=0;i<externalSources.length()&&sources.length()<8;i++){JSONObject source=externalSources.optJSONObject(i);if(source!=null&&DevGramMusicSources.selected(uid,source.optString("source"))){JSONObject sample=DevGramProfileDocument.copy(source);try{if("playing".equals(sample.optString("state","playing"))){long pos=sample.optLong("position")+(long)((SystemClock.elapsedRealtime()-externalAt)*sample.optDouble("speed",1));sample.put("position",sample.optLong("duration")>0?Math.min(sample.optLong("duration"),pos):pos);}}catch(Exception ignored){}sources.put(sample);}}if(value==null)for(int i=0;i<sources.length();i++){JSONObject source=sources.optJSONObject(i);if(DevGramMusicSources.publicSource(uid,source.optString("source"))){value=DevGramProfileDocument.copy(source);break;}}}
        }
        if (sent && (!enabled || publishingUid != uid || sources.length()==0)) { publish(publishingUid, null,new JSONArray()); sent = false; }
        long now = SystemClock.elapsedRealtime();
        if (enabled && sources.length()>0) {
            StringBuilder signature=new StringBuilder(Long.toString(uid));for(int i=0;i<sources.length();i++){JSONObject source=sources.optJSONObject(i);signature.append(":").append(source.optString("source")).append(":").append(source.optString("title")).append(":").append(source.optString("artist")).append(":").append(source.optString("state")).append(":").append(source.optDouble("speed",1)).append(":").append(DevGramMusicArtwork.version(uid,source.optString("source"),source.optString("title"),source.optString("artist")));}String track=signature.toString();
            if (now - lastSent >= 250 && (!track.equals(lastTrack) || seeked(sources,now) || now - lastSent >= 25000)) {
                publish(uid, value,sources); publishingUid = uid; sent = true; lastSent = now; lastTrack = track;try{lastSnapshot=new JSONArray(sources.toString());}catch(Exception ignored){}
            }
        }
        if (enabled) AndroidUtilities.runOnUIThread(tick, Math.max(250, now - lastSent < 250 ? 250 - (now - lastSent) : 30000));
    }
    private static boolean seeked(JSONArray sources,long now){
        if(sources.length()!=lastSnapshot.length())return true;
        for(int i=0;i<sources.length();i++){JSONObject a=sources.optJSONObject(i),b=lastSnapshot.optJSONObject(i);if(a==null||b==null)return true;
            long expected=b.optLong("position")+(long)(Math.max(0,now-lastSent)*b.optDouble("speed",1));if(b.optLong("duration")>0)expected=Math.min(expected,b.optLong("duration"));
            if(Math.abs(a.optLong("position")-expected)>1500)return true;
        }return false;
    }
    private static void publish(long uid, JSONObject value,JSONArray sources) {
        if (uid <= 0 || account(uid) < 0) return;
        String token = DevGramConfig.getBetaToken(uid); if (token.isEmpty()) return;
        JSONObject data = value == null ? new JSONObject() : DevGramProfileDocument.copy(value);data.remove("source");data.remove("app");data.remove("state");data.remove("speed");data.remove("album");
        try { data.put("uid", uid).put("token", token).put("playing", value != null&&!"paused".equals(value.optString("state"))); } catch (Exception ignored) { return; }
        JSONObject sourceData=new JSONObject();try{sourceData.put("uid",uid).put("token",token).put("sources",new JSONArray(sources.toString()));}catch(Exception e){return;}
        enqueue(uid,() -> {
            if(!enabled()){sentCovers.clear();DevGramMusicArtwork.clear();}
            java.util.HashMap<String,String> pendingCovers=new java.util.HashMap<>();
            if(enabled()&&MessagesController.getGlobalMainSettings().getLong("devgramPresenceUid",0)==uid)for(int i=0;i<sources.length();i++){
                JSONObject source=sources.optJSONObject(i);if(source==null)continue;
                String key=uid+":"+source.optString("source"),cover=DevGramMusicArtwork.encoded(uid,source.optString("source"),source.optString("title"),source.optString("artist"));
                String version=source.optString("title")+"\n"+source.optString("artist")+"\n"+cover;
                if(!version.equals(sentCovers.get(key)))try{sourceData.getJSONArray("sources").getJSONObject(i).put("coverData",cover);pendingCovers.put(key,version);}catch(Exception ignored){}
            }
            if(value!=null&&!DevGramMusicSources.publicSource(uid,value.optString("source","devgram"))){try{data.remove("title");data.remove("artist");data.remove("position");data.remove("duration");data.put("playing",false);}catch(Exception ignored){}}if((value!=null||sources.length()>0)&&(!enabled()||MessagesController.getGlobalMainSettings().getLong("devgramPresenceUid",0)!=uid)){try{data.remove("title");data.remove("artist");data.remove("position");data.remove("duration");data.put("playing",false);sourceData.put("sources",new JSONArray());}catch(Exception ignored){}} try { DevGramProfilePolicy.http("POST", "studio/activity", data); } catch (Exception ignored) {}try{DevGramProfilePolicy.http("POST","studio/activity/sources",sourceData);sentCovers.putAll(pendingCovers);}catch(Exception ignored){} });
    }
}

package org.telegram.messenger;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Reads media sessions after the user grants Android notification-listener access. */
public class DevGramMusicListener extends NotificationListenerService {
    private static java.lang.ref.WeakReference<DevGramMusicListener> active = new java.lang.ref.WeakReference<>(null);
    public static void refreshCurrent() { DevGramMusicListener listener = active.get(); if (listener != null) listener.handler.post(listener::syncSubscriptions); }
    public static JSONArray availableSources(){JSONArray result=new JSONArray();try{result.put(new JSONObject().put("source","devgram").put("app","DevGram"));DevGramMusicListener listener=active.get();if(listener==null||!listener.connected||listener.sessions==null)return result;java.util.HashSet<String> seen=new java.util.HashSet<>();for(MediaController controller:listener.sessions.getActiveSessions(new ComponentName(listener,listener.getClass()))){String source=controller.getPackageName();if(source.equals(listener.getPackageName())||!source.matches("[A-Za-z0-9_.-]{1,80}")||!seen.add(source))continue;result.put(new JSONObject().put("source",source).put("app",DevGramMusicSources.name(source)));}}catch(Exception ignored){}return result;}
    private MediaSessionManager sessions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<MediaController> controllers = new ArrayList<>();
    private boolean connected,watching;
    private final Runnable policy=()->handler.post(this::syncSubscriptions);
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { update(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { update(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::sessions;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if(watching){update();handler.postDelayed(this,30000);} }
    };
    @Override public void onListenerConnected() {
        active = new java.lang.ref.WeakReference<>(this);
        connected=true;
        super.onListenerConnected(); sessions = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        DevGramProfilePolicy.listen(policy);syncSubscriptions();
    }
    private void syncSubscriptions(){
        if(!connected||sessions==null)return;
        boolean desired=DevGramProfileCustomization.enabled()&&DevGramMusicPresence.enabled();
        if(!desired){stopWatching();DevGramMusicPresence.external(null);return;}
        if(watching){update();return;}
        try {
            sessions.addOnActiveSessionsChangedListener(changed, new ComponentName(this, getClass()), handler);
            watching=true;
            sessions(sessions.getActiveSessions(new ComponentName(this, getClass())));
            handler.removeCallbacks(refresh); handler.postDelayed(refresh, 30000);
        } catch (SecurityException ignored) {stopWatching();}
    }
    private void stopWatching(){
        handler.removeCallbacks(refresh);
        if(watching&&sessions!=null)sessions.removeOnActiveSessionsChangedListener(changed);
        watching=false;
        for(MediaController controller:controllers)controller.unregisterCallback(callback);
        controllers.clear();
    }
    private void sessions(List<MediaController> current) {
        if(!watching||!DevGramProfileCustomization.enabled())return;
        for (MediaController controller : controllers) controller.unregisterCallback(callback); controllers.clear();
        if (current != null) for (MediaController controller : current) { controllers.add(controller); controller.registerCallback(callback, handler); }
        update();
    }
    private static String clean(String value) { if (value == null) return ""; value = value.replaceAll("[\\p{Cntrl}]", " "); return value.length() > 200 ? value.substring(0, 200) : value; }
    private void update() {
        if (!DevGramMusicPresence.enabled()) { DevGramMusicPresence.external(null); return; }
        JSONArray sources=new JSONArray();long uid=MessagesController.getGlobalMainSettings().getLong("devgramPresenceUid",0);
        for (MediaController controller : controllers) {
            if (getPackageName().equals(controller.getPackageName())||!DevGramMusicSources.selected(uid,controller.getPackageName())||sources.length()>=7) continue;
            PlaybackState state = controller.getPlaybackState(); MediaMetadata metadata = controller.getMetadata();
            if (state == null || (state.getState() != PlaybackState.STATE_PLAYING && state.getState() != PlaybackState.STATE_PAUSED) || metadata == null) continue;
            String title = clean(metadata.getString(MediaMetadata.METADATA_KEY_TITLE)); if (title.isEmpty()) continue;
            long duration = Math.max(0, Math.min(7 * 86400000L, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)));
            long position = Math.max(0, state.getPosition());
            if (state.getState()==PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime() > 0) position += (long) ((android.os.SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime()) * state.getPlaybackSpeed());
            String artist=clean(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
            android.graphics.Bitmap artwork=null;try{artwork=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(artwork==null)artwork=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);if(artwork==null)artwork=metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);}catch(Exception ignored){}
            String artworkUri=metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);if(artworkUri==null)artworkUri=metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);if(artworkUri==null)artworkUri=metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
            DevGramMusicArtwork.remember(uid,controller.getPackageName(),title,artist,artwork,artworkUri);
            try { sources.put(new JSONObject().put("source",controller.getPackageName()).put("app",clean(DevGramMusicSources.name(controller.getPackageName())).substring(0,Math.min(80,clean(DevGramMusicSources.name(controller.getPackageName())).length()))).put("title", title).put("artist", clean(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)))
                    .put("album",clean(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM))).put("state",state.getState()==PlaybackState.STATE_PLAYING?"playing":"paused").put("speed",state.getState()==PlaybackState.STATE_PLAYING?Math.max(0,Math.min(4,state.getPlaybackSpeed())):0).put("position", Math.max(0, Math.min(7 * 86400000L, position))).put("duration", duration)); } catch (Exception ignored) {}
        }
        DevGramMusicPresence.externalSources(sources);
    }
    @Override public void onListenerDisconnected() { cleanup(); super.onListenerDisconnected(); }
    @Override public void onDestroy() { cleanup(); super.onDestroy(); }
    private void cleanup() {connected=false;DevGramProfilePolicy.unlisten(policy);stopWatching();if(active.get()==this)active.clear();DevGramMusicPresence.external(null);}
}

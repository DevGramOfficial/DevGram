package org.telegram.messenger;

import android.os.SystemClock;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/** One authoritative live policy for editing AND rendering existing appearance. */
public final class DevGramProfilePolicy {
    public static final String BASE = "https://api.devgram.space/badges/";
    public static final LinkedHashMap<String, Integer> DEFAULTS = new LinkedHashMap<>();
    static {
        String free = "profile.notices profile.comments profile.communities profile.palette profile.gradient profile.opacity profile.cards profile.layout profile.rows avatar.frame avatar.projects profile.pattern.dots profile.blocks music.player music.lyrics music.activity music.listening profile.assistant profile.integrations workshop.publish workshop.install workshop.social workshop.clans profile.import profile.export profile.media chat.badge profile.blocks.media avatar.frame.gradient avatar.shape profile.nick profile.font chat.font";
        String paid = "profile.aurora profile.mesh profile.blur profile.cards.glass profile.cards.volume profile.buttons profile.layout.advanced avatar.frame.orbit avatar.frame.stars avatar.layers avatar.nodes profile.pattern.stars profile.pattern.waves profile.pattern.grid profile.name.effects profile.thought badge.choice chat.bubble profile.blocks.code profile.blocks.counter profile.blocks.pixels music.lyrics.animated profile.export.layers profile.media.animated chat.badge.animated profile.blocks.media.animated";
        for (String key : free.split(" ")) DEFAULTS.put(key, 0);
        for (String key : paid.split(" ")) DEFAULTS.put(key, 1);
    }
    private static volatile Map<String, Integer> tiers = new LinkedHashMap<>(DEFAULTS);
    private static volatile long revision = -1;
    private static volatile int clanMinMembers = 100;
    public static int clanMinMembers() { return clanMinMembers; }
    private static boolean started;
    private static volatile int streamGeneration;
    private static Thread streamThread;
    private static volatile HttpURLConnection streamConnection;
    private static final CopyOnWriteArraySet<Runnable> listeners = new CopyOnWriteArraySet<>();

    private DevGramProfilePolicy() {}
    public static long revision() { return revision; }
    public static java.util.List<String> features(){return new java.util.ArrayList<>(tiers.keySet());}

    public static int tier(String feature) {
        Integer value = tiers.get(feature);
        return value == null ? 3 : value;
    }

    public static boolean allowed(long uid, String feature) {
        if (!DevGramProfileCustomization.enabled()) return false;
        if (uid < 0) { long owner=DevGramCommunityProfiles.owner(uid); return owner>0 && allowed(owner,"profile.communities") && allowed(owner,feature); }
        int tier = tier(feature);
        return tier == 0 || tier == 1 && DevGramBadges.hasSupporterFeatures(uid)
                || tier == 2 && DevGramBadges.hasDeveloperFeatures(uid);
    }

    public static void listen(Runnable callback) { listeners.add(callback); start(); }
    public static void unlisten(Runnable callback) { listeners.remove(callback); }

    public static void changed() {
        AndroidUtilities.runOnUIThread(DevGramBadges::appearanceChanged);
    }
    public static void dispatchListeners() {
        for (Runnable listener : listeners) {
            try { listener.run(); } catch (Exception e) { FileLog.e(e); }
        }
        org.telegram.messenger.devgram.player.MusicPlayerSheet.applyStudioPolicy();
    }

    public static synchronized void start() {
        if (started || ApplicationLoader.applicationContext == null || !DevGramProfileCustomization.enabled()) return;
        started = true;
        final int generation = ++streamGeneration;
        try {
            String saved = MessagesController.getGlobalMainSettings().getString("devgramProfilePolicy", "");
            if (!saved.isEmpty()) accept(new JSONObject(saved));
        } catch (Exception e) { FileLog.e(e); }
        Thread thread = new Thread(() -> {
            long delay = 1000;
            while (generation == streamGeneration && DevGramProfileCustomization.enabled()) {
                HttpURLConnection connection = null;
                long opened = SystemClock.elapsedRealtime();
                try {
                    connection = (HttpURLConnection) new URL(BASE + "profile-policy.json").openConnection();
                    synchronized (DevGramProfilePolicy.class) {
                        if (generation != streamGeneration || !started || !DevGramProfileCustomization.enabled()) return;
                        streamConnection = connection;
                    }
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(60000);
                    connection.setRequestProperty("Accept", "text/event-stream");
                    if (connection.getResponseCode() != 200) throw new java.io.IOException("policy HTTP " + connection.getResponseCode());
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (generation == streamGeneration && (line = reader.readLine()) != null) {
                            if (line.startsWith("data: ") && line.length() < 16000) accept(new JSONObject(line.substring(6)));
                        }
                    }
                } catch (Exception e) {
                    // Mobile connections routinely reconnect; do not log URLs or credentials.
                } finally {
                    if (connection != null) connection.disconnect();
                    synchronized (DevGramProfilePolicy.class) {
                        if (streamConnection == connection) streamConnection = null;
                    }
                }
                if (SystemClock.elapsedRealtime() - opened > 30000) delay = 1000;
                try { Thread.sleep(delay); } catch (InterruptedException ignored) { return; }
                delay = Math.min(delay * 2, 30000);
            }
        }, "DevGramProfilePolicy");
        thread.setDaemon(true);
        streamThread = thread;
        thread.start();
    }

    public static synchronized void stop() {
        ++streamGeneration; started = false;
        if (streamThread != null) streamThread.interrupt();
        HttpURLConnection connection = streamConnection; streamConnection = null;
        if (connection != null) DevGramNetworkQueue.postRunnable(connection::disconnect);
    }

    private static synchronized void accept(JSONObject response) throws Exception {
        long nextRevision = response.getLong("revision");
        if (nextRevision <= revision) return;
        JSONObject values = response.getJSONObject("tiers");
        LinkedHashMap<String, Integer> next = new LinkedHashMap<>(DEFAULTS);
        java.util.Iterator<String> names=values.keys();
        while(names.hasNext()&&next.size()<256){
            String key=names.next();if(key.length()>80||!key.matches("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+"))continue;
            int value=values.optInt(key,-1);if(value>=0&&value<=3)next.put(key,value);
        }
        tiers = next;
        JSONObject limits = response.optJSONObject("limits");
        clanMinMembers = limits == null ? 100 : Math.max(0, Math.min(10000000, limits.optInt("clanMinMembers", 100)));
        revision = nextRevision;
        MessagesController.getGlobalMainSettings().edit().putString("devgramProfilePolicy", response.toString()).apply();
        changed();
    }

    /** Interactive workshop I/O must not wait behind the app's background work.
     * A fixed pool limits concurrent requests; idle workers disappear after 30 s.
     * Authenticated replies still pass through send()'s account and token checks.
     */
    private static final java.util.concurrent.ThreadPoolExecutor workshopIo = createWorkshopIo();
    private static java.util.concurrent.ThreadPoolExecutor createWorkshopIo() {
        java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(
                3, 3, 30, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "DevGramWorkshopIo");
                    thread.setDaemon(true);
                    return thread;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
    public static void executeWorkshop(Runnable task) { workshopIo.execute(task); }

    public interface Callback { void run(JSONObject result, String error); }

    /** All mutations use the account's Telegram-authenticated token, never a client-side role claim. */
    public static void request(int account, String route, JSONObject fields, Callback callback) {
        requestDirect(account, route, fields, (result,error)->{
            if(result==null&&("clan_bot_required".equals(error)||"community_verification_required".equals(error))&&DevGramCommunityBinding.retry(account,route,fields,callback))return;
            callback.run(result,error);
        });
    }

    public static void requestDirect(int account, String route, JSONObject fields, Callback callback) {
        final long uid = UserConfig.getInstance(account).getClientUserId();
        DevGramBeta.authenticate(account, (ok, error) -> {
            if (!ok) { callback.run(null, error); return; }
            send(account, uid, route, fields, callback, true);
        });
    }

    private static void send(int account, long uid, String route, JSONObject fields, Callback callback, boolean retry) {
        Runnable task = () -> {
            JSONObject response = null;
            String error = null;
            try {
                JSONObject data = new JSONObject(fields.toString());
                data.put("uid", uid);
                data.put("token", DevGramConfig.getBetaToken(uid));
                response = http("POST", route, data);
                if (!response.optBoolean("ok")) error = response.optString("error", "network");
            } catch (Exception e) { error = "network"; }
            final JSONObject result = response;
            final String failure = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (UserConfig.getInstance(account).getClientUserId() != uid) { callback.run(null, "account_changed"); return; }
                if ("token".equals(failure) && retry) {
                    DevGramConfig.clearBetaToken(uid);
                    DevGramBeta.authenticate(account, (ok, authError) -> {
                        if (ok) send(account, uid, route, fields, callback, false);
                        else callback.run(null, authError);
                    });
                } else callback.run(failure == null ? result : null, failure);
            });
        };
        if (route.startsWith("studio/workshop") || route.startsWith("studio/works/") || route.startsWith("studio/reports") || route.startsWith("studio/communities")) executeWorkshop(task);
        else DevGramNetworkQueue.postRunnable(task);
    }

    public static JSONObject http(String method, String route, JSONObject body) throws Exception {
        if (route.startsWith("/") || route.contains(":") || route.contains("..")) throw new IllegalArgumentException();
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE + route).openConnection();
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod(method);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int code = connection.getResponseCode();
            java.io.InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new java.io.IOException("empty response");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder text = new StringBuilder();
                char[] buf = new char[4096]; int count;
                while ((count = reader.read(buf)) != -1) {
                    text.append(buf, 0, count);
                    if (text.length() > (route.startsWith("studio/profiles")?16:route.startsWith("studio/")?4:1) * 1024 * 1024) throw new java.io.IOException("response too large");
                }
                return new JSONObject(text.toString());
            }
        } finally { connection.disconnect(); }
    }

    public static void refresh(Callback callback){executeWorkshop(()->{JSONObject value=null;try{value=http("GET","profile-policy.json",null);accept(value);}catch(Exception ignored){value=null;}JSONObject result=value;AndroidUtilities.runOnUIThread(()->callback.run(result,result==null?"network":null));});}

    public static void setTier(int account, String key, int tier, Callback callback) {
        try {
            JSONObject fields = new JSONObject().put("feature", key).put("tier", tier).put("revision", revision);
            request(account, "profile-policy", fields, (result, error) -> {
                if (result != null) {
                    try { accept(result.getJSONObject("policy")); } catch (Exception e) { FileLog.e(e); }
                }
                callback.run(result, error);
            });
        } catch (Exception e) { callback.run(null, "bad_policy"); }
    }
    public static void setClanMinMembers(int account, int value, Callback callback) {
        try {
            request(account, "profile-policy/limits", new JSONObject().put("limit", "clanMinMembers").put("value", value).put("revision", revision), (result, error) -> {
                if (result != null) try { accept(result.getJSONObject("policy")); } catch (Exception e) { FileLog.e(e); }
                callback.run(result, error);
            });
        } catch (Exception e) { callback.run(null, "bad_policy"); }
    }
}

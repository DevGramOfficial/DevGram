package org.telegram.messenger;

import org.json.JSONObject;
import java.util.LinkedHashMap;

/** Public appearance cache, separate from authentication, badges and unsaved editor drafts. */
public final class DevGramProfileStyle {
    public static final String[] KEYS = {"palette", "background", "frame", "shape", "pattern", "dim"};
    public static final int[] MAX = {7, 4, 4, 9, 4, 100};
    public static class Style {
        private final int[] values = {0, 0, 0, 0, 0, 35};
        public Style() {}
        public Style(JSONObject json) {
            if (json == null) return;
            for (int i = 0; i < KEYS.length; i++) values[i] = Math.max(0, Math.min(MAX[i], json.optInt(KEYS[i], values[i])));
        }
        public int get(String key) { for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return values[i]; return 0; }
        public Style with(String key, int value) {
            Style copy = new Style(json());
            for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) copy.values[i] = Math.max(0, Math.min(MAX[i], value));
            return copy;
        }
        public JSONObject json() {
            JSONObject result = new JSONObject();
            try { result.put("v", 1); for (int i = 0; i < KEYS.length; i++) result.put(KEYS[i], values[i]); } catch (Exception ignored) {}
            return result;
        }
        public Style effective(long uid) {
            Style copy = this;
            for (String key : KEYS) {
                String feature = feature(key, get(key));
                if (feature != null && !DevGramProfilePolicy.allowed(uid, feature)) copy = copy.with(key, key.equals("dim") ? 35 : 0);
            }
            return copy;
        }
    }

    public static String feature(String key, int value) {
        if (key.equals("dim")) return "profile.opacity";
        if (value == 0) return null;
        switch (key) {
            case "palette": return "profile.palette";
            case "background": return value == 1 ? "profile.palette" : value == 2 ? "profile.gradient" : value == 3 ? "profile.aurora" : "profile.mesh";
            case "frame": return value == 1 ? "avatar.frame" : value == 2 ? "avatar.frame.gradient" : value == 3 ? "avatar.frame.orbit" : "avatar.frame.stars";
            case "shape": return "avatar.shape";
            case "pattern": return value == 1 ? "profile.pattern.dots" : value == 2 ? "profile.pattern.stars" : value == 3 ? "profile.pattern.waves" : "profile.pattern.grid";
        }
        return null;
    }

    private static class Entry { Style style; long fetched, revision; boolean loading; }
    private static final LinkedHashMap<Long, Entry> cache = new LinkedHashMap<>(32, .75f, true);
    private DevGramProfileStyle() {}
    public static void clearCache() {
        cache.clear();
        android.content.SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        android.content.SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) if (key.startsWith("devgramProfileStyle_")) editor.remove(key);
        editor.apply(); DevGramProfilePolicy.changed();
    }

    private static Entry entry(long uid) {
        Entry entry = cache.get(uid);
        if (entry == null) {
            entry = new Entry();
            try {
                String saved = MessagesController.getGlobalMainSettings().getString("devgramProfileStyle_" + uid, "{}");
                entry.style = new Style(new JSONObject(saved));
            } catch (Exception e) { entry.style = new Style(); }
            cache.put(uid, entry);
            if (cache.size() > 128) {
                Long oldest = cache.keySet().iterator().next();
                cache.remove(oldest);
                MessagesController.getGlobalMainSettings().edit().remove("devgramProfileStyle_" + oldest).apply();
            }
        }
        return entry;
    }

    public static Style raw(long uid) { return uid != 0 ? entry(uid).style : new Style(); }
    public static Style get(long uid) { return DevGramProfileCustomization.enabled() ? raw(uid).effective(uid) : new Style(); }
    public interface Callback { void run(Style style, String error); }

    public static void load(long uid, Callback callback) {
        if (!DevGramProfileCustomization.enabled()) { if (callback != null) callback.run(new Style(),null);return; }
        if (uid < 0) { DevGramCommunityProfiles.ensure(uid); if(callback!=null)callback.run(raw(uid),null); return; }
        if (uid == 0) { if (callback != null) callback.run(new Style(), null); return; }
        DevGramProfilePolicy.start();
        Entry entry = entry(uid);
        if (entry.loading || System.currentTimeMillis() - entry.fetched < 30000) {
            if (callback != null) callback.run(entry.style, null);
            return;
        }
        entry.loading = true;
        long revision = entry.revision;
        DevGramNetworkQueue.postRunnable(() -> {
            Style value = null;
            try {
                JSONObject result = DevGramProfilePolicy.http("GET", "profiles/" + uid + ".json", null);
                if (result.optBoolean("ok")) value = new Style(result.getJSONObject("style"));
            } catch (Exception ignored) {}
            final Style loaded = value;
            AndroidUtilities.runOnUIThread(() -> {
                entry.loading = false;
                if (cache.get(uid) != entry || entry.revision != revision) {
                    if (callback != null) callback.run(raw(uid), null);
                    return;
                }
                if (loaded != null) {
                    entry.style = loaded;
                    entry.fetched = System.currentTimeMillis();
                    persist(uid, loaded);
                    DevGramProfilePolicy.changed();
                }
                if (callback != null) callback.run(entry.style, loaded == null ? "network" : null);
            });
        });
    }

    private static void persist(long uid, Style value) {
        MessagesController.getGlobalMainSettings().edit().putString("devgramProfileStyle_" + uid, value.json().toString()).apply();
    }

    static void storeSaved(long uid, Style style) {
        Entry entry = entry(uid);
        entry.revision++;entry.style = style; entry.fetched = System.currentTimeMillis(); persist(uid, style);
    }

    public static void save(int account, Style style, Callback callback) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        try {
            DevGramProfilePolicy.request(account, "profiles/self", new JSONObject().put("style", style.json()), (result, error) -> {
                if (UserConfig.getInstance(account).getClientUserId()!=uid) { callback.run(null,"account_changed");return; }
                if (result != null) {
                    Style saved = new Style(result.optJSONObject("style"));
                    storeSaved(uid, saved);
                    DevGramProfilePolicy.changed(); callback.run(saved, null);
                } else callback.run(null, error);
            });
        } catch (Exception e) { callback.run(null, "network"); }
    }
}

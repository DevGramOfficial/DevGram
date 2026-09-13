package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Pattern;

/** Regex filters and local shadow bans, modeled after AyuGram filters. */
public final class DevGramFilterController {
    public static final class Rule {
        public String id = UUID.randomUUID().toString();
        public String text = "";
        public long dialogId;
        public boolean enabled = true;
        public boolean caseInsensitive;
        public boolean reversed;
        private transient Pattern compiled;

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("text", text).put("dialogId", dialogId)
                    .put("enabled", enabled).put("caseInsensitive", caseInsensitive).put("reversed", reversed);
        }

        static Rule fromJson(JSONObject o) {
            Rule r = new Rule();
            r.id = o.optString("id", r.id);
            r.text = o.optString("text", "");
            r.dialogId = o.optLong("dialogId", 0);
            r.enabled = o.optBoolean("enabled", true);
            r.caseInsensitive = o.optBoolean("caseInsensitive", false);
            r.reversed = o.optBoolean("reversed", false);
            return r;
        }

        boolean matches(CharSequence value) {
            try {
                if (compiled == null) compiled = Pattern.compile(text,
                        caseInsensitive ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
                boolean found = compiled.matcher(value).find();
                return reversed ? !found : found;
            } catch (Throwable ignore) {
                return false;
            }
        }
    }

    private static final Object sync = new Object();
    private static ArrayList<Rule> rules;

    private DevGramFilterController() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("devgram_filters", Activity.MODE_PRIVATE);
    }

    public static boolean isEnabled() { return prefs().getBoolean("enabled", false); }
    public static void setEnabled(boolean value) { prefs().edit().putBoolean("enabled", value).apply(); }
    public static boolean filterPrivateChats() { return prefs().getBoolean("private", false); }
    public static void setFilterPrivateChats(boolean value) { prefs().edit().putBoolean("private", value).apply(); }
    public static boolean hideBlocked() { return prefs().getBoolean("blocked", false); }
    public static void setHideBlocked(boolean value) { prefs().edit().putBoolean("blocked", value).apply(); }

    public static ArrayList<Rule> getRules() {
        synchronized (sync) {
            if (rules == null) {
                rules = new ArrayList<>();
                try {
                    JSONArray a = new JSONArray(prefs().getString("rules", "[]"));
                    for (int i = 0; i < a.length(); i++) rules.add(Rule.fromJson(a.getJSONObject(i)));
                } catch (Throwable e) { FileLog.e(e); }
            }
            return new ArrayList<>(rules);
        }
    }

    public static void saveRules(ArrayList<Rule> value) {
        synchronized (sync) {
            rules = new ArrayList<>(value);
            JSONArray a = new JSONArray();
            for (Rule r : rules) {
                try { a.put(r.toJson()); } catch (Throwable ignore) {}
            }
            prefs().edit().putString("rules", a.toString()).apply();
        }
    }

    public static void addRule(Rule rule) {
        ArrayList<Rule> all = getRules(); all.add(rule); saveRules(all);
    }

    public static void removeRule(String id) {
        ArrayList<Rule> all = getRules();
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id.equals(id)) all.remove(i);
        saveRules(all);
    }

    private static HashSet<Long> shadowBans() {
        HashSet<Long> result = new HashSet<>();
        for (String id : prefs().getStringSet("shadow_bans", new HashSet<>())) {
            try { result.add(Long.parseLong(id)); } catch (Throwable ignore) {}
        }
        return result;
    }

    public static boolean isShadowBanned(long id) { return shadowBans().contains(id); }
    public static ArrayList<Long> getShadowBans() { return new ArrayList<>(shadowBans()); }
    public static void setShadowBanned(long id, boolean value) {
        HashSet<String> set = new HashSet<>(prefs().getStringSet("shadow_bans", new HashSet<>()));
        if (value) set.add(Long.toString(id)); else set.remove(Long.toString(id));
        prefs().edit().putStringSet("shadow_bans", set).apply();
    }

    public static boolean isFiltered(int account, MessageObject message) {
        if (!isEnabled() || message == null || message.isOut() || message.isOutOwner()) return false;
        long sender = message.messageOwner.from_id == null ? 0 : MessageObject.getPeerId(message.messageOwner.from_id);
        long dialogId = message.getDialogId();
        if (sender != 0 && (isShadowBanned(sender)
                || hideBlocked() && MessagesController.getInstance(account).blockePeers.indexOfKey(sender) >= 0)) return true;
        if (isShadowBanned(dialogId)) return true;
        CharSequence text = message.messageText;
        if (TextUtils.isEmpty(text)) text = message.messageOwner.message;
        if (TextUtils.isEmpty(text)) return false;
        for (Rule rule : getRules()) {
            boolean appliesHere = rule.dialogId == dialogId
                    || rule.dialogId == 0 && (dialogId <= 0 || filterPrivateChats());
            if (rule.enabled && appliesHere && rule.matches(text)) return true;
        }
        return false;
    }

    public static String exportJson() {
        JSONArray a = new JSONArray();
        for (Rule rule : getRules()) try { a.put(rule.toJson()); } catch (Throwable ignore) {}
        JSONObject result = new JSONObject();
        try {
            result.put("version", 1).put("rules", a).put("shadowBans", new JSONArray(prefs().getStringSet("shadow_bans", new HashSet<>())));
        } catch (Throwable ignore) {}
        return result.toString();
    }

    public static boolean importJson(String json, boolean merge) {
        try {
            JSONObject root = new JSONObject(json);
            ArrayList<Rule> result = merge ? getRules() : new ArrayList<>();
            HashSet<String> ids = new HashSet<>();
            for (Rule r : result) ids.add(r.id);
            JSONArray a = root.getJSONArray("rules");
            for (int i = 0; i < a.length(); i++) {
                Rule r = Rule.fromJson(a.getJSONObject(i));
                if (!ids.contains(r.id)) result.add(r);
            }
            saveRules(result);
            JSONArray bans = root.optJSONArray("shadowBans");
            if (bans != null) {
                HashSet<String> set = merge ? new HashSet<>(prefs().getStringSet("shadow_bans", new HashSet<>())) : new HashSet<>();
                for (int i = 0; i < bans.length(); i++) set.add(bans.getString(i));
                prefs().edit().putStringSet("shadow_bans", set).apply();
            }
            return true;
        } catch (Throwable e) {
            FileLog.e(e); return false;
        }
    }
}

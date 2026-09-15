package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
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
        public final HashSet<Long> excludedDialogs = new HashSet<>();
        private transient Pattern compiled;
        private transient String compiledText;
        private transient int compiledFlags;

        JSONObject toJson() throws Exception {
            JSONArray exclusions = new JSONArray();
            for (Long dialog : excludedDialogs) exclusions.put(dialog);
            return new JSONObject().put("id", id).put("text", text).put("dialogId", dialogId)
                    .put("enabled", enabled).put("caseInsensitive", caseInsensitive).put("reversed", reversed)
                    .put("excludedDialogs", exclusions);
        }

        static Rule fromJson(JSONObject o) {
            Rule r = new Rule();
            r.id = o.optString("id", r.id);
            r.text = o.optString("text", "");
            r.dialogId = o.optLong("dialogId", 0);
            r.enabled = o.optBoolean("enabled", true);
            r.caseInsensitive = o.optBoolean("caseInsensitive", false);
            r.reversed = o.optBoolean("reversed", false);
            JSONArray exclusions = o.optJSONArray("excludedDialogs");
            if (exclusions != null) {
                for (int i = 0; i < exclusions.length(); i++) r.excludedDialogs.add(exclusions.optLong(i));
            }
            return r;
        }

        boolean matches(CharSequence value) {
            try {
                int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
                if (compiled == null || !TextUtils.equals(compiledText, text) || compiledFlags != flags) {
                    compiled = Pattern.compile(text, flags);
                    compiledText = text;
                    compiledFlags = flags;
                }
                boolean found = compiled.matcher(value).find();
                return reversed ? !found : found;
            } catch (Throwable ignore) {
                return false;
            }
        }
    }

    private static final Object sync = new Object();
    private static ArrayList<Rule> rules;
    private static HashSet<Long> shadowBanCache;

    private DevGramFilterController() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("devgram_filters", Activity.MODE_PRIVATE);
    }

    public static boolean isEnabled() { return prefs().getBoolean("enabled", false); }
    public static void setEnabled(boolean value) { prefs().edit().putBoolean("enabled", value).apply(); notifyChanged(); }
    public static boolean filterPrivateChats() { return prefs().getBoolean("private", false); }
    public static void setFilterPrivateChats(boolean value) { prefs().edit().putBoolean("private", value).apply(); notifyChanged(); }
    public static boolean hideBlocked() { return prefs().getBoolean("blocked", false); }
    public static void setHideBlocked(boolean value) { prefs().edit().putBoolean("blocked", value).apply(); notifyChanged(); }

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
        notifyChanged();
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
        synchronized (sync) {
            if (shadowBanCache == null) {
                shadowBanCache = new HashSet<>();
                for (String id : prefs().getStringSet("shadow_bans", new HashSet<>())) {
                    try { shadowBanCache.add(Long.parseLong(id)); } catch (Throwable ignore) {}
                }
            }
            return new HashSet<>(shadowBanCache);
        }
    }

    public static boolean isShadowBanned(long id) {
        synchronized (sync) {
            if (shadowBanCache == null) shadowBans();
            return shadowBanCache.contains(id);
        }
    }
    public static ArrayList<Long> getShadowBans() { return new ArrayList<>(shadowBans()); }
    public static void setShadowBanned(long id, boolean value) {
        HashSet<String> set = new HashSet<>(prefs().getStringSet("shadow_bans", new HashSet<>()));
        if (value) set.add(Long.toString(id)); else set.remove(Long.toString(id));
        prefs().edit().putStringSet("shadow_bans", set).apply();
        synchronized (sync) {
            if (shadowBanCache == null) shadowBans();
            if (value) shadowBanCache.add(id); else shadowBanCache.remove(id);
        }
        notifyChanged();
    }

    public static void clearAll() {
        saveRules(new ArrayList<>());
        prefs().edit().remove("shadow_bans").apply();
        synchronized (sync) { shadowBanCache = new HashSet<>(); }
        notifyChanged();
    }

    public static boolean isFiltered(int account, MessageObject message) {
        return isFiltered(account, message, null);
    }

    public static boolean isFiltered(int account, MessageObject message, MessageObject.GroupedMessages group) {
        if (!isEnabled() || message == null || message.isOut() || message.isOutOwner()) return false;
        if (group != null) {
            MessageObject primary = group.findPrimaryMessageObject();
            if (primary != null) message = primary;
        }
        if (isBlockedSource(account, message)) return true;
        long dialogId = message.getDialogId();
        CharSequence text = extractAllText(message, group);
        if (TextUtils.isEmpty(text)) return false;
        for (Rule rule : getRules()) {
            boolean appliesHere = rule.dialogId == dialogId
                    || rule.dialogId == 0 && !rule.excludedDialogs.contains(dialogId)
                    && (dialogId <= 0 || filterPrivateChats());
            if (rule.enabled && appliesHere && rule.matches(text)) return true;
        }
        return false;
    }

    /** True for locally shadow-banned/Telegram-blocked senders and forwarded/via-bot sources. */
    public static boolean isBlocked(int account, long peerId) {
        if (!isEnabled() || peerId == 0 || UserConfig.getInstance(account).getClientUserId() == peerId) return false;
        return isShadowBanned(peerId)
                || hideBlocked() && MessagesController.getInstance(account).blockePeers.indexOfKey(peerId) >= 0;
    }

    private static boolean isBlockedSource(int account, MessageObject message) {
        if (message.messageOwner == null) return false;
        long dialogId = message.getDialogId();
        if (isBlocked(account, dialogId)) return true;
        if (message.messageOwner.via_bot_id != 0 && isBlocked(account, message.messageOwner.via_bot_id)) return true;
        long sender = message.messageOwner.from_id == null ? 0 : MessageObject.getPeerId(message.messageOwner.from_id);
        if (sender != dialogId && isBlocked(account, sender)) return true;
        if (message.messageOwner.fwd_from != null && message.messageOwner.fwd_from.from_id != null
                && isBlocked(account, MessageObject.getPeerId(message.messageOwner.fwd_from.from_id))) return true;
        return false;
    }

    private static CharSequence extractAllText(MessageObject message, MessageObject.GroupedMessages group) {
        StringBuilder result = new StringBuilder();
        if (group != null && group.messages != null) {
            for (MessageObject item : group.messages) appendMessageText(result, item);
        } else {
            appendMessageText(result, message);
        }
        if (message.messageOwner != null) {
            if (message.messageOwner.entities != null) {
                for (TLRPC.MessageEntity entity : message.messageOwner.entities) {
                    if (entity instanceof TLRPC.TL_messageEntityTextUrl && !TextUtils.isEmpty(entity.url)) {
                        result.append('\n').append(entity.url);
                    }
                }
            }
            appendButtons(result, message.messageOwner.reply_markup);
        }
        result.append("\n<type>").append(message.type).append("</type>");
        return result;
    }

    private static void appendButtons(StringBuilder result, TLRPC.ReplyMarkup markup) {
        if (markup instanceof TLRPC.TL_replyKeyboardMarkup) {
            for (TL_keyboard.KeyboardButtonRow row : ((TLRPC.TL_replyKeyboardMarkup) markup).rows) {
                for (TL_keyboard.KeyboardButton button : row.buttons) appendButton(result, button);
            }
        } else if (markup instanceof TLRPC.TL_replyInlineMarkup) {
            for (TL_keyboard.KeyboardInlineButtonRow row : ((TLRPC.TL_replyInlineMarkup) markup).rows) {
                for (TL_keyboard.KeyboardInlineButton button : row.buttons) appendButton(result, button);
            }
        }
    }

    private static void appendButton(StringBuilder result, TL_keyboard.KeyboardButtonProto button) {
        String text = button.getText();
        String url = button.getUrl();
        if (!TextUtils.isEmpty(text)) result.append('\n').append(text);
        if (!TextUtils.isEmpty(url)) result.append(' ').append(url);
    }

    private static void appendMessageText(StringBuilder result, MessageObject message) {
        if (message == null || message.messageOwner == null) return;
        CharSequence text = message.messageText;
        if (!TextUtils.isEmpty(text)) result.append(text).append('\n');
        if (!TextUtils.isEmpty(message.caption)) result.append(message.caption).append('\n');
        if (!TextUtils.isEmpty(message.messageOwner.message)
                && (TextUtils.isEmpty(text) || !TextUtils.equals(text, message.messageOwner.message))) {
            result.append(message.messageOwner.message).append('\n');
        }
        if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
            TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) message.messageOwner.media).poll;
            if (poll != null) {
                if (poll.question != null && !TextUtils.isEmpty(poll.question.text)) result.append(poll.question.text).append('\n');
                if (poll.answers != null) for (TLRPC.PollAnswer answer : poll.answers) {
                    if (answer != null && answer.text != null && !TextUtils.isEmpty(answer.text.text)) result.append(answer.text.text).append('\n');
                }
            }
        }
        try {
            if (message.isVoiceTranscriptionOpen() && !TextUtils.isEmpty(message.getVoiceTranscription())) {
                result.append(message.getVoiceTranscription()).append('\n');
            }
        } catch (Throwable ignore) {}
    }

    private static void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                NotificationCenter center = NotificationCenter.getInstance(account);
                center.postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_MESSAGE_TEXT);
                center.postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        });
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
                synchronized (sync) { shadowBanCache = null; }
            }
            notifyChanged();
            return true;
        } catch (Throwable e) {
            FileLog.e(e); return false;
        }
    }
}

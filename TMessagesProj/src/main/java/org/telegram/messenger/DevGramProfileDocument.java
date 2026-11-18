package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/** Validated public studio documents. Never contains tokens, badge roles or account credentials. */
public final class DevGramProfileDocument {
    public static final LinkedHashMap<String, String> FEATURES = new LinkedHashMap<>();
    static {
        String[] pairs = {"bannerMode|profile.palette", "bannerColor|profile.palette", "bannerColor2|profile.gradient", "bannerAlpha|profile.opacity", "bannerDim|profile.opacity", "bannerBlend|profile.palette", "bannerBlendRadius|profile.palette", "bannerAsset|profile.media", "pageAsset|profile.media", "media|profile.media", "pageMode|profile.palette", "pageColor|profile.palette", "pageColor2|profile.gradient", "pageAlpha|profile.opacity", "pageDim|profile.opacity", "pageBlur|profile.blur", "avatarRadius|avatar.shape", "avatarRound|avatar.shape", "avatarAlpha|avatar.shape", "avatarDim|avatar.shape", "avatarFade|avatar.shape", "avatarFadeRadius|avatar.shape", "customShape|avatar.shape", "layout|profile.layout", "avatarScale|profile.layout.advanced", "avatarX|profile.layout.advanced", "avatarY|profile.layout.advanced",
            "nameScale|profile.layout.advanced", "nameX|profile.layout.advanced", "nameY|profile.layout.advanced", "font|profile.font",
            "statusScale|profile.layout.advanced", "statusX|profile.layout.advanced", "statusY|profile.layout.advanced",
            "buttonsScale|profile.layout.advanced", "buttonsX|profile.layout.advanced", "buttonsY|profile.layout.advanced",
            "nickColor|profile.nick", "nickColor2|profile.nick", "messageFrame|chat.bubble", "cardColor|profile.cards", "cardAlpha|profile.cards",
            "buttonShape|profile.buttons", "blur|profile.blur", "bubble|chat.bubble", "bubbleColor|chat.bubble", "textColor|chat.bubble", "chatFont|chat.font",
            "playerCover|music.player", "lyrics|music.lyrics", "lyricsMotion|music.lyrics.animated", "activity|music.activity",
            "blocks|profile.blocks", "integrations|profile.integrations", "layers|avatar.layers", "nodes|avatar.nodes", "rows|profile.rows", "hiddenRows|profile.rows",
            "badge|chat.badge", "clan|workshop.clans", "nowPlaying|music.activity", "backgroundMsg|profile.media", "backgroundVideo|profile.media"};
        for (String pair : pairs) { int index = pair.indexOf('|'); FEATURES.put(pair.substring(0, index), pair.substring(index + 1)); }
        for (String part : DevGramHeaderGeometry.PARTS) {
            for (String suffix : new String[]{"OffsetX", "OffsetY", "ScaleX", "ScaleY", "Rotation", "AnchorX", "AnchorY", "AlignX", "AlignY"}) {
                FEATURES.put(part + suffix, "profile.layout.advanced");
            }
        }
        FEATURES.put("headerMode","profile.layout");FEATURES.put("headerConfig","profile.layout.advanced");
        FEATURES.put("commentsEnabled","profile.comments");FEATURES.put("commentsCount","profile.comments");
        for (String surface : new String[]{"banner", "page"}) {
            FEATURES.put(surface+"MediaType","profile.media");FEATURES.put(surface+"Sound","profile.media");FEATURES.put(surface+"Volume","profile.media");
            for (String suffix : new String[]{"Color3", "Use3", "GradientType", "GradientAngle", "GradientSpread", "GradientCenterX", "GradientCenterY"}) FEATURES.put(surface + suffix, "profile.gradient");
            for (String suffix : new String[]{"FadeType", "FadeAngle", "FadeSpread", "FadeCenterX", "FadeCenterY"}) FEATURES.put(surface + suffix, "profile.opacity");
        }
        for(String suffix:new String[]{"Enabled","Join","RadiusEnabled","Radius"})FEATURES.put("card"+suffix,"profile.cards");
        FEATURES.put("cardBlur","profile.blur");
        for(String suffix:new String[]{"Glass","GlassBlur","GlassBend","GlassThick","GlassLight"})FEATURES.put("card"+suffix,"profile.cards.glass");
        for(String suffix:new String[]{"Volume","LightAngle","LightColor","SideLight","CenterBrightness","LightRadius","LightDistance","LightStrength"})FEATURES.put("card"+suffix,"profile.cards.volume");
        for(String suffix:new String[]{"CopyCards","Color","Alpha","Blur"})FEATURES.put("button"+suffix,"profile.buttons");
        FEATURES.put("titleText", "profile.font");
        FEATURES.put("nameFontFile", "profile.font"); FEATURES.put("chatFontFile", "chat.font");
        FEATURES.put("frameGraph", "avatar.nodes");
        for(String field:new String[]{"bubbleEnabled","bubbleGradient","bubbleColor2","bubbleAngle","bubbleAlpha","bubbleColor2Alpha","bubbleTextAlpha"})FEATURES.put(field,"chat.bubble");FEATURES.put("bubbleFont","chat.font");
        FEATURES.put("profileTextEnabled", "profile.palette");FEATURES.put("profileTextColor", "profile.palette");FEATURES.put("profilePalette", "profile.palette");
        for(String suffix:new String[]{"MediaType","Enabled","Source","Width","Alpha","Fade","X","Y"})FEATURES.put("chatBadge"+suffix,"chat.badge");
        FEATURES.put("nameSize", "profile.font");FEATURES.put("nameFont", "profile.font");FEATURES.put("nameColorEnabled", "profile.nick");
        FEATURES.put("nickAlpha", "profile.nick");FEATURES.put("nickAlpha2", "profile.nick");
        for(String suffix:new String[]{"Effect","Speed","Angle","Glow","GlowRadius","GlowStrength","Shadow","ShadowColor","ShadowRadius","ShadowStrength","ShadowX","ShadowY"})FEATURES.put("name"+suffix,"profile.name.effects");
        for(String suffix:new String[]{"Text","Color","Background","Alpha","TextAlpha","FontCopy","Font","Shadow","ShadowColor","ShadowRadius","ShadowStrength","ShadowX","ShadowY"})FEATURES.put("thought"+suffix,"profile.thought");
    }
    private static final LinkedHashMap<Long, Entry> cache = new LinkedHashMap<>(32, .75f, true);
    private static final LinkedHashSet<Long> waiting = new LinkedHashSet<>();
    private static boolean scheduled;
    private static volatile int cacheGeneration;
    private static final Runnable trimTask = () -> DevGramNetworkQueue.postRunnable(DevGramProfileDocument::trimCache);
    private static final android.graphics.Typeface[] FONTS = {
        AndroidUtilities.bold(), android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD),
        android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL),
        android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD),
        android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
    };
    private static final android.graphics.Typeface[] NAME_FONTS = {
        FONTS[0],android.graphics.Typeface.create("sans-serif-thin",0),FONTS[1],FONTS[2],
        android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.ITALIC),FONTS[3]
    };
    public static android.graphics.Typeface nameFont(JSONObject doc) {
        android.graphics.Typeface custom = DevGramProfileFonts.get(doc.optJSONObject("nameFontFile"));
        if (custom != null) return custom;
        return doc.has("nameFont") ? NAME_FONTS[Math.max(0,Math.min(5,doc.optInt("nameFont")))] : font(doc.optInt("font"));
    }
    private static final android.graphics.Typeface[] MESSAGE_FONTS = {
        null,android.graphics.Typeface.create("sans-serif",0),android.graphics.Typeface.create("serif",1),
        android.graphics.Typeface.create("monospace",1),android.graphics.Typeface.create("sans-serif",3),
        android.graphics.Typeface.create("sans-serif-condensed",1)
    };
    public static android.graphics.Typeface messageFont(JSONObject doc) {
        if(doc.has("bubbleFont")){int selected=Math.max(0,Math.min(6,doc.optInt("bubbleFont")));if(selected==6)return DevGramProfileFonts.get(doc.optJSONObject("chatFontFile"));return MESSAGE_FONTS[selected];}
        android.graphics.Typeface custom = DevGramProfileFonts.get(doc.optJSONObject("chatFontFile"));
        return custom != null ? custom : doc.optInt("chatFont") == 0 ? null : font(doc.optInt("chatFont"));
    }
    public static android.graphics.Typeface thoughtFont(JSONObject doc) {
        return doc.optInt("thoughtFontCopy",1)==1 ? nameFont(doc) : NAME_FONTS[Math.max(0,Math.min(5,doc.optInt("thoughtFont")))];
    }
    private static class Entry {
        JSONObject raw = new JSONObject(), effective = new JSONObject();
        JSONObject ownerRaw,ownerEffective;
        long ownerPolicy=Long.MIN_VALUE;
        int ownerAccess=-1;
        long loaded, revision, policy = Long.MIN_VALUE;
        int access = -1;
        boolean fromServer;
    }
    private DevGramProfileDocument() {}

    public static JSONObject copy(JSONObject value) {
        try { return new JSONObject(value.toString()); } catch (Exception ignored) { return new JSONObject(); }
    }

    private static synchronized Entry entry(long uid) {
        Entry entry = cache.get(uid);
        if (entry == null) {
            entry = new Entry();
            Entry target = entry;
            DevGramNetworkQueue.postRunnable(() -> {
                JSONObject saved = null;
                try {
                    java.io.File file = new java.io.File(cacheDirectory(), uid + ".json");
                    int days = MessagesController.getGlobalMainSettings().getInt("devgramStudioCacheDays", 7);
                    if (file.isFile() && file.length() <= 1048576 && (days == 0 || System.currentTimeMillis() - file.lastModified() < days * 86400000L)) {
                        try (java.io.FileInputStream stream = new java.io.FileInputStream(file)) {
                            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
                            while ((n = stream.read(buf)) > 0) { bytes.write(buf, 0, n); if (bytes.size() > 1048576) throw new java.io.IOException(); }
                            saved = new JSONObject(bytes.toString("UTF-8"));
                        }
                    }
                } catch (Exception ignored) {}
                final JSONObject value = saved;
                if (value != null) AndroidUtilities.runOnUIThread(() -> {
                    synchronized (DevGramProfileDocument.class) {
                        if (cache.get(uid) != target || target.fromServer) return;
                        target.raw = value; target.policy = Long.MIN_VALUE;
                    }
                    changed();
                });
            });
            cache.put(uid, entry);
            if (cache.size() > 128) {
                Long old = cache.keySet().iterator().next(); cache.remove(old);
            }
        }
        return entry;
    }

    public static JSONObject editable(JSONObject doc) { JSONObject result = copy(doc);JSONObject media=result.optJSONObject("media");if(media!=null){DevGramProfileMedia.remember(media.optJSONObject("banner"));DevGramProfileMedia.remember(media.optJSONObject("page"));} result.remove("nowPlaying"); result.remove("backgroundMsg"); result.remove("backgroundVideo");result.remove("media");return result; }
    public static synchronized JSONObject raw(long uid) { Entry entry=uid!=0?entry(uid):null;return entry==null?new JSONObject():editable(entry.ownerRaw==null?entry.raw:entry.ownerRaw); }
    public static JSONObject publicValue(JSONObject source){JSONObject result=copy(source);JSONArray blocks=result.optJSONArray("blocks");if(blocks!=null){JSONArray visible=new JSONArray();for(int i=0;i<blocks.length();i++){JSONObject block=blocks.optJSONObject(i);if(block!=null&&!block.optBoolean("ownOnly"))visible.put(block);}try{if(visible.length()==0)result.remove("blocks");else result.put("blocks",visible);}catch(Exception ignored){}}return result;}
    /** Public AI proposals cannot replace or erase rows that were never sent to the service. */
    public static JSONObject restorePrivate(JSONObject proposed,JSONObject current){
        JSONObject result=publicValue(proposed);JSONArray old=current.optJSONArray("blocks"),incoming=result.optJSONArray("blocks"),merged=new JSONArray();java.util.HashSet<String> privateIds=new java.util.HashSet<>();int privateCount=0;
        if(old!=null)for(int i=0;i<old.length();i++){JSONObject row=old.optJSONObject(i);if(row!=null&&row.optBoolean("ownOnly")){privateIds.add(row.optString("id"));privateCount++;}}
        int next=0,publicLimit=Math.max(0,64-privateCount);java.util.ArrayList<JSONObject> publicRows=new java.util.ArrayList<>();if(incoming!=null)for(int i=0;i<incoming.length()&&publicRows.size()<publicLimit;i++){JSONObject row=incoming.optJSONObject(i);if(row!=null&&!privateIds.contains(row.optString("id")))publicRows.add(row);}
        if(old!=null)for(int i=0;i<old.length();i++){JSONObject row=old.optJSONObject(i);if(row!=null&&row.optBoolean("ownOnly"))merged.put(copy(row));else if(next<publicRows.size())merged.put(publicRows.get(next++));}while(next<publicRows.size())merged.put(publicRows.get(next++));
        try{if(merged.length()>0)result.put("blocks",merged);}catch(Exception ignored){}return result;
    }

    public static JSONObject effective(JSONObject raw, long uid) {
        if (!DevGramProfileCustomization.enabled()) return new JSONObject();
        JSONObject value = copy(raw);
        for (String key : FEATURES.keySet()) {
            String feature=FEATURES.get(key);
            if(feature.equals("profile.media")){
                if(key.equals("media")){if(!DevGramProfileMedia.any(uid))value.remove(key);continue;}
                if(key.startsWith("banner")||key.startsWith("page"))feature=DevGramProfileMedia.feature(raw,key.startsWith("banner")?"banner":"page");
            }else if(feature.equals("chat.badge")&&raw.optInt("chatBadgeMediaType")==1)feature="chat.badge.animated";
            if(!DevGramProfilePolicy.allowed(uid,feature))value.remove(key);
        }
        for(String slot:new String[]{"banner","page"})if(!DevGramProfilePolicy.allowed(uid,DevGramProfileMedia.feature(raw,slot))&&(raw.has(slot+"Asset")||raw.has("media")))try{
            value.put(slot+"Asset",0);value.put(slot+"MediaType",0);JSONObject metadata=value.optJSONObject("media");if(metadata!=null)metadata.put(slot,new JSONObject());
            if(slot.equals("banner")){value.put("backgroundMsg",0);value.put("backgroundVideo",0);}
        }catch(Exception ignored){}
        if(value.has("frameGraph"))try{value.put("frameGraph",raw.optJSONObject("frameGraph"));}catch(Exception ignored){}
        if(value.optInt("headerMode")==4&&!DevGramProfilePolicy.allowed(uid,"profile.layout.advanced"))value.remove("headerMode");
        if (value.optInt("layout") == 3 && !DevGramProfilePolicy.allowed(uid, "profile.layout.advanced")) value.remove("layout");
        if(value.optInt("pageMode")>=2&&!DevGramProfilePolicy.allowed(uid,value.optInt("pageMode")==3?DevGramProfileMedia.feature(raw,"page"):pageFeature(value.optInt("pageMode"))))value.remove("pageMode");
        if(value.optInt("bannerMode")>=2&&!DevGramProfilePolicy.allowed(uid,value.optInt("bannerMode")==3?DevGramProfileMedia.feature(raw,"banner"):pageFeature(value.optInt("bannerMode"))))value.remove("bannerMode");
        JSONArray blocks = value.optJSONArray("blocks");
        if (blocks != null) {
            JSONArray filtered = new JSONArray();
            for (int i = 0; i < Math.min(64, blocks.length()); i++) {
                JSONObject block = blocks.optJSONObject(i);
                if (block == null) continue;
                String type = block.optString("type");
                if(block.has("rowType")&&!DevGramProfilePolicy.allowed(uid,DevGramProfileRowSpec.feature(block)))continue;
                if (type.equals("image") && !DevGramProfilePolicy.allowed(uid, block.optInt("mediaType")==1?"profile.blocks.media.animated":"profile.blocks.media") || type.equals("code") && !DevGramProfilePolicy.allowed(uid, "profile.blocks.code")) continue;
                block = copy(block);
                if (!DevGramProfilePolicy.allowed(uid, "profile.blocks.code")) { block.remove("css"); block.remove("script");block.remove("components"); }
                filtered.put(block);
            }
            try { value.put("blocks", filtered); } catch (Exception ignored) {}
        }
        return value;
    }

    public static synchronized JSONObject get(long uid) {
        if (uid == 0 || !DevGramProfileCustomization.enabled()) return new JSONObject();
        ensure(uid);
        Entry entry = entry(uid);
        long policyOwner=DevGramCommunityProfiles.owner(uid);
        int access = (DevGramBadges.hasSupporterFeatures(policyOwner) ? 1 : 0) | (DevGramBadges.hasDeveloperFeatures(policyOwner) ? 2 : 0);
        if (entry.policy != DevGramProfilePolicy.revision() || entry.access != access) {
            entry.effective = publicValue(effective(entry.raw, uid)); entry.policy = DevGramProfilePolicy.revision(); entry.access = access;
        }
        return entry.effective;
    }
    public static synchronized JSONObject get(long uid,int account){
        if(uid<=0||uid!=UserConfig.getInstance(account).getClientUserId()||!UserConfig.getInstance(account).isClientActivated()||!DevGramProfileCustomization.enabled())return get(uid);
        Entry entry=entry(uid);if(entry.ownerRaw==null)return get(uid);
        int access=(DevGramBadges.hasSupporterFeatures(uid)?1:0)|(DevGramBadges.hasDeveloperFeatures(uid)?2:0);
        if(entry.ownerEffective==null||entry.ownerPolicy!=DevGramProfilePolicy.revision()||entry.ownerAccess!=access){entry.ownerEffective=effective(entry.ownerRaw,uid);entry.ownerPolicy=DevGramProfilePolicy.revision();entry.ownerAccess=access;}
        return entry.ownerEffective;
    }
    private static synchronized void ownerStore(long uid,JSONObject doc){Entry entry=entry(uid);entry.revision++;entry.ownerRaw=copy(doc);entry.ownerPolicy=Long.MIN_VALUE;store(uid,publicValue(doc));}

    /** A response fetched before a local save/reset must never restore old data. */
    private static final class ReadStamp {
        final Entry entry;
        final long revision;
        ReadStamp(Entry entry) { this.entry=entry;this.revision=entry.revision; }
    }
    private static synchronized ReadStamp readStamp(long uid) { return new ReadStamp(entry(uid)); }
    private static synchronized boolean current(long uid, ReadStamp stamp) {
        return cache.get(uid)==stamp.entry && stamp.entry.revision==stamp.revision;
    }

    public static int number(long uid, String field, int fallback) { return get(uid).optInt(field, fallback); }
    public static String pageFeature(int mode){return mode==2?"profile.gradient":mode==3?"profile.media":mode==4?"profile.aurora":mode==5?"profile.mesh":"profile.palette";}
    public static android.graphics.Typeface font(int index) {
        return FONTS[Math.max(0, Math.min(4, index))];
    }

    public static synchronized void ensure(long uid) {
        if (!DevGramProfileCustomization.enabled()) return;
        if (uid < 0) { DevGramCommunityProfiles.ensure(uid); return; }
        if (uid == 0 || System.currentTimeMillis() - entry(uid).loaded < 60000) return;
        waiting.add(uid);
        if (scheduled) return;
        scheduled = true;
        AndroidUtilities.runOnUIThread(DevGramProfileDocument::loadBatch, 150);
    }

    private static synchronized void loadBatch() {
        scheduled = false;
        if (!DevGramProfileCustomization.enabled()) { waiting.clear(); return; }
        ArrayList<Long> ids = new ArrayList<>();
        for (Long uid : waiting) { ids.add(uid); if (ids.size() == 5) break; }
        waiting.removeAll(ids);
        if (!waiting.isEmpty()) { scheduled = true; AndroidUtilities.runOnUIThread(DevGramProfileDocument::loadBatch, 150); }
        if (ids.isEmpty()) return;
        java.util.HashMap<Long,ReadStamp> stamps = new java.util.HashMap<>();
        for (long uid : ids) stamps.put(uid, readStamp(uid));
        StringBuilder query = new StringBuilder();
        for (long uid : ids) { entry(uid).loaded = System.currentTimeMillis(); if (query.length() > 0) query.append(','); query.append(uid); }
        DevGramNetworkQueue.postRunnable(() -> {
            if (!DevGramProfileCustomization.enabled()) return;
            JSONObject result = null;
            try { result = DevGramProfilePolicy.http("GET", "studio/profiles.json?ids=" + query, null).optJSONObject("profiles"); } catch (Exception ignored) {}
            final JSONObject profiles = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (profiles == null) return;
                for (long uid : ids) {
                    JSONObject doc = profiles.optJSONObject(Long.toString(uid));
                    if (doc != null && current(uid, stamps.get(uid))) store(uid, doc);
                }
                changed();
            });
        });
    }

    private static synchronized void store(long uid, JSONObject doc) {
        Entry entry = entry(uid); entry.raw = copy(doc); entry.policy = Long.MIN_VALUE; entry.loaded = System.currentTimeMillis(); entry.fromServer = true;
        int generation = cacheGeneration; byte[] bytes = doc.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        DevGramNetworkQueue.postRunnable(() -> {
            if (generation != cacheGeneration) return;
            try {
                java.io.File dir = cacheDirectory(); if (!dir.isDirectory() && !dir.mkdirs()) return;
                java.io.File temporary = new java.io.File(dir, uid + ".tmp"), target = new java.io.File(dir, uid + ".json");
                try (java.io.FileOutputStream stream = new java.io.FileOutputStream(temporary)) { stream.write(bytes); }
                if (!temporary.renameTo(target)) temporary.delete();
            } catch (Exception ignored) {}
        });
        AndroidUtilities.cancelRunOnUIThread(trimTask); AndroidUtilities.runOnUIThread(trimTask, 1500);
    }

    public static void changed() {
        DevGramProfilePolicy.changed();
    }

    public static synchronized void clearCache() {
        DevGramProfileIntegrations.clear();
        DevGramProfileFonts.clear();
        cacheGeneration++;
        cache.clear(); waiting.clear();
        android.content.SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        android.content.SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) if (key.startsWith("devgramStudio_")) editor.remove(key);
        editor.apply(); changed();
        DevGramNetworkQueue.postRunnable(() -> {
            java.io.File[] files = cacheDirectory().listFiles();
            if (files != null) for (java.io.File file : files) if (file.getName().matches("[0-9]+\\.(json|tmp)")) file.delete();
        });
    }
    private static java.io.File cacheDirectory() { return new java.io.File(ApplicationLoader.applicationContext.getCacheDir(), "devgram-profile-studio"); }
    public static long cacheBytes() { long size = DevGramProfileFonts.cacheBytes(); java.io.File[] files = cacheDirectory().listFiles(); if (files != null) for (java.io.File file : files) if (file.getName().matches("[0-9]+\\.json")) size += file.length(); return size; }
    public static void trimCache() {
        java.io.File[] listed = cacheDirectory().listFiles(); if (listed == null) return;
        java.util.ArrayList<java.io.File> files = new java.util.ArrayList<>(); long total = 0;
        int days = MessagesController.getGlobalMainSettings().getInt("devgramStudioCacheDays", 7);
        for (java.io.File file : listed) if (file.getName().matches("[0-9]+\\.json")) {
            if (days > 0 && System.currentTimeMillis() - file.lastModified() > days * 86400000L) { file.delete(); continue; }
            files.add(file); total += file.length();
        }
        java.util.Collections.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        long limit = Math.max(4, Math.min(32, MessagesController.getGlobalMainSettings().getInt("devgramStudioCacheMb", 8))) * 1024L * 1024;
        int count = files.size();
        for (java.io.File file : files) {
            if (total <= limit && count <= 128) break;
            long size = file.length(); if (file.delete()) { total -= size; count--; }
        }
    }
    public static synchronized void refresh(long uid) { if (uid < 0) DevGramCommunityProfiles.refresh(uid); else if (uid > 0) { entry(uid).loaded = 0; ensure(uid); } }

    public static void load(int account, DevGramProfilePolicy.Callback callback) {
        if (!DevGramProfileCustomization.enabled()) { callback.run(new JSONObject(),null);return; }
        final long uid=UserConfig.getInstance(account).getClientUserId();
        final ReadStamp stamp=readStamp(uid);
        DevGramProfilePolicy.request(account,"studio/self/load",new JSONObject(),(result,error)->{
            if(UserConfig.getInstance(account).getClientUserId()!=uid){callback.run(null,"account_changed");return;}
            if(!DevGramProfileCustomization.enabled()||!current(uid,stamp)){callback.run(null,"stale_document");return;}
            if(result!=null&&result.optJSONObject("document")!=null){ownerStore(uid,result.optJSONObject("document"));changed();}
            callback.run(result,error);
        });
    }

    public static synchronized void storeCommunity(long subject,JSONObject doc,boolean owned) { if(doc==null)return;if(owned)ownerStore(subject,doc);else store(subject,doc); }
    public static void load(int account,long subject,DevGramProfilePolicy.Callback callback) { if(subject<0)DevGramCommunityProfiles.load(account,subject,callback);else load(account,callback); }
    public static void save(int account,long subject,JSONObject document,DevGramProfileStyle.Style theme,DevGramProfilePolicy.Callback callback) { if(subject<0)DevGramCommunityProfiles.save(account,subject,document,theme,callback);else save(account,document,callback); }

    public static void save(int account, JSONObject document, DevGramProfilePolicy.Callback callback) {
        try {
            long uid = UserConfig.getInstance(account).getClientUserId();
            DevGramProfilePolicy.request(account, "studio/self", new JSONObject().put("document", editable(effective(document, uid))), (result, error) -> {
                if(UserConfig.getInstance(account).getClientUserId()!=uid){callback.run(null,"account_changed");return;}
                if (result != null) { ownerStore(uid, result.optJSONObject("document")); changed();load(account,(full,failure)->{}); }
                callback.run(result, error);
            });
        } catch (Exception e) { callback.run(null, "bad_document"); }
    }
    public static void reset(int account,long subject,DevGramProfilePolicy.Callback callback){if(subject<0)DevGramCommunityProfiles.reset(account,subject,callback);else reset(account,callback);}
    public static void saveMessages(int account,long subject,JSONObject source,DevGramProfilePolicy.Callback callback){if(subject<0)DevGramCommunityProfiles.patch(account,subject,source,MESSAGE_FIELDS,callback);else saveMessages(account,source,callback);}
    public static void saveBadge(int account,long subject,JSONObject source,JSONObject previous,DevGramProfilePolicy.Callback callback){if(subject<0)DevGramCommunityProfiles.patch(account,subject,source,new String[]{"chatBadgeEnabled","chatBadgeMediaType","chatBadgeSource","chatBadgeWidth","chatBadgeAlpha","chatBadgeFade","chatBadgeX","chatBadgeY"},callback);else saveBadge(account,source,previous,callback);}
    public static void acceptWorkshop(int account, JSONObject result) {
        if(result.optLong("subject")<0){DevGramCommunityProfiles.acceptWorkshop(account,result);return;}
        long uid=UserConfig.getInstance(account).getClientUserId();
        if(!result.optBoolean("applied")||result.optJSONObject("document")==null)return;
        ownerStore(uid,result.optJSONObject("document"));
        if(!result.optString("kind").equals("frame"))DevGramProfileStyle.storeSaved(uid,new DevGramProfileStyle.Style(result.optJSONObject("theme")));
        changed();
    }
    public static final String[] MESSAGE_FIELDS={"bubbleAlpha","bubbleColor2Alpha","bubbleTextAlpha","bubbleEnabled","bubbleGradient","bubbleColor","bubbleColor2","bubbleAngle","textColor","bubbleFont","chatFont","chatFontFile","messageFrame","bubble"};
    public static final String[] BADGE_FIELDS={"chatBadgeEnabled","chatBadgeMediaType","chatBadgeSource","chatBadgeWidth","chatBadgeAlpha","chatBadgeFade","chatBadgeX","chatBadgeY"};
    public static void saveBadge(int account, JSONObject source, JSONObject baseline, DevGramProfilePolicy.Callback callback) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        try {
            JSONObject selected = new JSONObject(), active = effective(source, uid);
            JSONArray fields = new JSONArray();
            for (String field : BADGE_FIELDS) {
                if (java.util.Objects.equals(source.opt(field),baseline.opt(field))) continue;
                fields.put(field);
                if (active.has(field)) selected.put(field, active.opt(field));
            }
            DevGramProfilePolicy.request(account, "studio/badge", new JSONObject().put("document", selected).put("fields", fields), (result, error) -> {
                if (UserConfig.getInstance(account).getClientUserId() != uid) { callback.run(null,"account_changed");return; }
                if (result != null && result.optJSONObject("document") != null) { ownerStore(uid, result.optJSONObject("document")); changed(); }
                callback.run(result, error);
            });
        } catch (Exception e) { callback.run(null, "bad_document"); }
    }
    public static void reset(int account, DevGramProfilePolicy.Callback callback) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        try {
            DevGramProfilePolicy.request(account, "studio/reset", new JSONObject().put("confirm", true), (result, error) -> {
                if (UserConfig.getInstance(account).getClientUserId() != uid) { callback.run(null,"account_changed");return; }
                if (result != null && result.optJSONObject("document") != null) {
                    ownerStore(uid, result.optJSONObject("document"));
                    DevGramProfileStyle.storeSaved(uid, new DevGramProfileStyle.Style(result.optJSONObject("theme")));
                    resetLocalAppearance(account, uid);
                    changed();
                }
                callback.run(result, error);
            });
        } catch (Exception e) { callback.run(null, "bad_document"); }
    }
    private static void resetLocalAppearance(int account, long uid) {
        android.content.SharedPreferences.Editor preferences=MessagesController.getGlobalMainSettings().edit();
        for(String key:new String[]{"devgramChatDecor","devgramChatBadges","devgramChatBubbles","devgramCustomizationHaptics","devgramBakeOutside","devgramBakeProfile","devgramProfilePlayerCover","devgramProfileLyricsMotion"})preferences.remove(key);
        preferences.remove("devgramMusicSources_"+uid).remove("devgramPublicMusicSources_"+uid).remove("devgramPrivatePlayerOnly_"+uid).apply();
        DevGramMusicPresence.configure(account,false);
    }
    public static void saveMessages(int account,JSONObject source,DevGramProfilePolicy.Callback callback){
        long uid=UserConfig.getInstance(account).getClientUserId();JSONObject selected=new JSONObject(),active=effective(source,uid);try{for(String field:MESSAGE_FIELDS)if(active.has(field))selected.put(field,active.opt(field));}catch(Exception ignored){}
        try{DevGramProfilePolicy.request(account,"studio/messages",new JSONObject().put("document",selected),(result,error)->{if(UserConfig.getInstance(account).getClientUserId()!=uid){callback.run(null,"account_changed");return;}if(result!=null&&result.optJSONObject("document")!=null){ownerStore(uid,result.optJSONObject("document"));changed();}callback.run(result,error);});}catch(Exception e){callback.run(null,"bad_document");}
    }
}

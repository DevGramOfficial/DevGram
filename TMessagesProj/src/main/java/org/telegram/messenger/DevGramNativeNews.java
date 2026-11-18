package org.telegram.messenger;

import android.util.Base64;
import org.json.*;
import org.telegram.tgnet.*;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.iv.RichTextStyle;
import java.lang.reflect.Field;
import java.util.*;

/** Complete native TL articles, bounded decoding and fresh public-archive media references. */
public final class DevGramNativeNews {
    public static final int MAX_BYTES = 512 * 1024;
    private DevGramNativeNews() {}
    public interface Ready { void run(TL_iv.RichMessage rich, String error); }
    public static String clip(String value, int limit) { if (value.length() <= limit) return value; int end = limit; if (Character.isHighSurrogate(value.charAt(end - 1))) end--; return value.substring(0, end); }
    public static String digest(byte[] data) throws Exception { byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(data); StringBuilder out = new StringBuilder(); for (byte b : hash) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return out.toString(); }
    public static JSONObject encode(TL_iv.RichMessage rich, String title) throws Exception {
        SerializedData data = new SerializedData(); rich.serializeToStream(data); byte[] bytes = data.toByteArray(); data.cleanup();
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("too_large");
        JSONArray assets = new JSONArray(); for (TLRPC.Photo photo : rich.photos) assets.put(new JSONObject().put("kind", "photo").put("id", Long.toString(photo.id))); for (TLRPC.Document doc : rich.documents) assets.put(new JSONObject().put("kind", "document").put("id", Long.toString(doc.id)));
        StringBuilder body = new StringBuilder(); summary(rich.blocks, body, 0);
        String text = clip(body.toString().trim(), 8000); if (title == null || title.trim().isEmpty()) title = text.isEmpty() ? DevGramLocale.text("Новости DevGram") : text.split("\n", 2)[0];
        return new JSONObject().put("format", 1).put("payload", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("hash", digest(bytes)).put("title", clip(title.trim(), 160)).put("body", text).put("assets", assets);
    }
    private static void summary(Object object, StringBuilder out, int depth) {
        if (object == null || depth > 48 || out.length() >= 8000) return;
        if (object instanceof Iterable) { for (Object value : (Iterable<?>) object) summary(value, out, depth + 1); return; }
        if (!(object instanceof TL_iv.PageBlock)) return;
        TL_iv.PageBlock block = (TL_iv.PageBlock) object;
        if (block.text != null) { String text = RichTextStyle.plainOf(block.text); if (!text.isEmpty()) out.append(text).append('\n'); }
        for (String field : new String[]{"blocks", "items"}) try { summary(block.getClass().getField(field).get(block), out, depth + 1); } catch (Exception ignored) {}
    }
    private static final class SafeData extends SerializedData {
        private boolean count;
        private int reads, elements;
        SafeData(byte[] bytes) { super(bytes); }
        @Override public int readInt32(boolean strict) {
            if (remaining() < 4 || ++reads > 60000) throw new IllegalArgumentException("bad_article");
            if ((reads & 31) == 0 && Thread.currentThread().getStackTrace().length > 240) throw new IllegalArgumentException("article_depth");
            int value = super.readInt32(true);
            if (count) { if (value < 0 || value > 4096 || (elements += value) > 8192) throw new IllegalArgumentException("article_vector"); count = false; }
            else count = value == 0x1cb5c415;
            return value;
        }
    }
    public static TL_iv.RichMessage decode(JSONObject document) {
        try {
            if (document == null || document.optInt("format") != 1 || document.optString("payload").length() > (MAX_BYTES + 2) / 3 * 4) return null;
            byte[] bytes = Base64.decode(document.getString("payload"), Base64.NO_WRAP); if (bytes.length > MAX_BYTES || !digest(bytes).equals(document.optString("hash"))) return null;
            SafeData data = new SafeData(bytes);
            try {
                TL_iv.RichMessage rich = TL_iv.RichMessage.TLdeserialize(data, data.readInt32(true), true);
                if (rich == null || data.remaining() != 0 || rich.part || rich.blocks.size() > 512 || rich.photos.size() + rich.documents.size() > 64) return null;
                JSONArray assets = document.optJSONArray("assets"); if (assets == null || assets.length() != rich.photos.size() + rich.documents.size()) return null;
                int slot = 0;
                for (TLRPC.Photo photo : rich.photos) { JSONObject asset = assets.optJSONObject(slot++); if (asset == null || !asset.optString("kind").equals("photo") || !asset.optString("id").equals(Long.toString(photo.id))) return null; }
                for (TLRPC.Document doc : rich.documents) { JSONObject asset = assets.optJSONObject(slot++); if (asset == null || !asset.optString("kind").equals("document") || !asset.optString("id").equals(Long.toString(doc.id))) return null; }
                return rich;
            }
            finally { data.cleanup(); }
        } catch (Throwable ignored) { return null; }
    }
    public static void show(BaseFragment host, TL_iv.RichMessage rich, String title, String id) {
        if (host.getParentActivity() == null || rich == null || !DevGramProfileCustomization.enabled()) return;
        TLRPC.TL_webPage web = new TLRPC.TL_webPage(); web.url = "https://api.devgram.space/badges/news/" + id; web.display_url = web.url; web.title = title; web.site_name = "DevGram"; web.flags = 2 | 4 | 1024;
        TL_iv.TL_page page = new TL_iv.TL_page(); page.url = web.url; page.rtl = rich.rtl; page.blocks.addAll(rich.blocks); page.photos.addAll(rich.photos); page.documents.addAll(rich.documents); page.devgramNews = true; web.cached_page = page;
        host.createArticleViewer(false).open(web, web.url);
    }
    public static void prepare(int account, JSONObject result, Ready callback) {
        DevGramNetworkQueue.postRunnable(() -> {
            TL_iv.RichMessage rich = decode(result.optJSONObject("document"));
            AndroidUtilities.runOnUIThread(() -> {
                if (rich == null) { callback.run(null, "Не удалось открыть статью. Обновите DevGram."); return; }
                JSONObject archive = result.optJSONObject("archive"); JSONArray assets = result.optJSONObject("document").optJSONArray("assets");
                if (assets == null || assets.length() == 0 || archive == null || archive.length() == 0 || result.optBoolean("owner") && archive.length() < assets.length()) { callback.run(rich, null); return; }
                DevGramPackages.resolveUsernameThen(account, DevGramPackages.ARCHIVE_USERNAME, (user, chat) -> {
                    if (chat == null) { callback.run(null, "Не удалось загрузить медиа статьи"); return; }
                    TLRPC.TL_channels_getMessages request = new TLRPC.TL_channels_getMessages(); request.channel = MessagesController.getInputChannel(chat);
                    for (Iterator<String> keys = archive.keys(); keys.hasNext();) { int message = archive.optInt(keys.next()); if (message > 0 && !request.id.contains(message)) request.id.add(message); }
                    ConnectionsManager.getInstance(account).sendRequest(request, (response, failure) -> AndroidUtilities.runOnUIThread(() -> {
                        if (!(response instanceof TLRPC.messages_Messages)) { callback.run(null, "Не удалось загрузить медиа статьи"); return; }
                        TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response; MessagesController.getInstance(account).putUsers(messages.users, false); MessagesController.getInstance(account).putChats(messages.chats, false);
                        HashMap<Integer, TLRPC.MessageMedia> media = new HashMap<>(); for (TLRPC.Message message : messages.messages) if (message.media != null) media.put(message.id, message.media);
                        HashMap<Long, Long> photos = new HashMap<>(), documents = new HashMap<>(); ArrayList<TLRPC.Photo> newPhotos = new ArrayList<>(); ArrayList<TLRPC.Document> newDocs = new ArrayList<>();
                        for (int i = 0; i < assets.length(); i++) try {
                            JSONObject asset = assets.getJSONObject(i); TLRPC.MessageMedia item = media.get(archive.optInt(Integer.toString(i))); if (item == null) { callback.run(null, "Не удалось загрузить медиа статьи"); return; }
                            long old = Long.parseLong(asset.getString("id"));
                            if (asset.optString("kind").equals("photo") && item.photo != null) { photos.put(old, item.photo.id); newPhotos.add(item.photo); }
                            else if (asset.optString("kind").equals("document") && item.document != null) { documents.put(old, item.document.id); newDocs.add(item.document); }
                            else { callback.run(null, "Не удалось загрузить медиа статьи"); return; }
                        } catch (Exception e) { callback.run(null, "Не удалось загрузить медиа статьи"); return; }
                        rich.photos = newPhotos; rich.documents = newDocs; replaceIds(rich.blocks, photos, documents, new IdentityHashMap<>(), 0); callback.run(rich, null);
                    }));
                });
            });
        });
    }
    private static void replaceIds(Object value, Map<Long,Long> photos, Map<Long,Long> documents, IdentityHashMap<Object,Boolean> seen, int depth) {
        if (value == null || depth > 80 || seen.put(value, true) != null) return;
        if (value instanceof Iterable) { for (Object item : (Iterable<?>) value) replaceIds(item, photos, documents, seen, depth + 1); return; }
        if (!value.getClass().getName().startsWith("org.telegram.tgnet.tl.TL_iv$") && !value.getClass().getName().startsWith("org.telegram.tgnet.tl.TL_keyboard$")) return;
        for (Field field : value.getClass().getFields()) try {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            String key = field.getName();
            if (field.getType() == long.class) { Map<Long,Long> map = key.equals("photo_id") || key.equals("poster_photo_id") ? photos : key.equals("video_id") || key.equals("audio_id") || key.equals("document_id") ? documents : null; if (map != null && map.containsKey(field.getLong(value))) field.setLong(value, map.get(field.getLong(value))); }
            else if (!field.getType().isPrimitive() && !key.equals("parentRichText")) replaceIds(field.get(value), photos, documents, seen, depth + 1);
        } catch (Exception ignored) {}
    }
    public static void read(BaseFragment host, long notice) {
        long viewer = host.getUserConfig().getClientUserId();
        try { DevGramProfilePolicy.request(host.getCurrentAccount(), "studio/notices/article", new JSONObject().put("id", notice), (result, error) -> {
            if (host.getParentActivity() == null || host.getUserConfig().getClientUserId() != viewer || !DevGramProfileCustomization.enabled()) return;
            if (result == null) { BulletinFactory.of(host).createErrorBulletin(DevGramLocale.text("not_found".equals(error)?"Новость удалена или недоступна":"Не удалось загрузить статью")).show(); return; }
            prepare(host.getCurrentAccount(), result, (rich, failure) -> { if (host.getParentActivity() == null || host.getUserConfig().getClientUserId() != viewer || !DevGramProfilePolicy.allowed(viewer,"profile.notices")) return; if (rich == null) BulletinFactory.of(host).createErrorBulletin(DevGramLocale.text(failure)).show(); else show(host, rich, result.optJSONObject("document").optString("title"), result.optString("id")); });
        }); } catch (Exception ignored) {}
    }
    public static void cover(BackupImageView image, int account, int message, long identity) {
        image.setTag(identity); image.getImageReceiver().setAllowStartAnimation(false);
        DevGramProfileBackground.load(account, message, media -> {
            if (media == null || !DevGramProfileCustomization.enabled() || !Long.valueOf(identity).equals(image.getTag()) || media.messageOwner.media == null || media.messageOwner.media.photo == null) return;
            TLRPC.Photo photo = media.messageOwner.media.photo; TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 800);
            image.setImage(ImageLocation.getForPhoto(size, photo), "800_450", (android.graphics.drawable.Drawable) null, media);
        });
    }
    private static long pendingNotice;
    public static boolean handleIntent(android.content.Intent intent) {
        android.net.Uri uri = intent == null ? null : intent.getData();
        if (uri == null || !"tg".equalsIgnoreCase(uri.getScheme()) || !"dgnews".equalsIgnoreCase(uri.getHost())) return false;
        try { long notice = Long.parseLong(uri.getQueryParameter("id")); if (notice > 0 && notice < (1L << 53)) { pendingNotice = notice; AndroidUtilities.runOnUIThread(DevGramNativeNews::flushPending, 500); } } catch (Exception ignored) {}
        return true;
    }
    public static void flushPending() {
        if (pendingNotice == 0 || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter || !UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) return;
        BaseFragment host = org.telegram.ui.LaunchActivity.getSafeLastFragment(); if (host == null || host.getParentActivity() == null) return;
        long notice = pendingNotice; pendingNotice = 0;
        if (!DevGramProfileCustomization.enabled() || !DevGramProfilePolicy.allowed(host.getUserConfig().getClientUserId(), "profile.notices")) return;
        read(host, notice);
    }
    public static void sendMedia(int account, TL_iv.RichMessage rich, String stage, JSONArray assets, Utilities.Callback<String> done) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        DevGramPackages.resolveUsernameThen(account, DevGramPackages.CATALOG_BOT_USERNAME, (user, chat) -> {
            if (UserConfig.getInstance(account).getClientUserId() != owner) { done.run("Писать новости могут только разработчики"); return; }
            if (user == null) { done.run("Нет связи с ботом DevGram"); return; }
            sendNext(account, owner, user, rich, stage, assets, 0, done);
        });
    }
    private static void sendNext(int account, long owner, TLRPC.User bot, TL_iv.RichMessage rich, String stage, JSONArray assets, int index, Utilities.Callback<String> done) {
        if (index >= assets.length()) { done.run(null); return; }
        if (UserConfig.getInstance(account).getClientUserId() != owner || !DevGramProfileCustomization.enabled() || !DevGramBadges.hasDeveloperFeatures(owner)) { done.run("Писать новости могут только разработчики"); return; }
        JSONObject asset = assets.optJSONObject(index); if (asset == null) { done.run("Не удалось загрузить медиа статьи"); return; }
        if (asset.optInt("message") > 0) { sendNext(account, owner, bot, rich, stage, assets, index + 1, done); return; }
        TLRPC.TL_messages_sendMedia request = new TLRPC.TL_messages_sendMedia(); TLRPC.TL_inputPeerUser peer = new TLRPC.TL_inputPeerUser(); peer.user_id = bot.id; peer.access_hash = bot.access_hash; request.peer = peer; request.message = "dgnews:" + stage + ":" + index; request.silent = true; request.random_id = Utilities.random.nextLong();
        long id; try { id = Long.parseLong(asset.optString("id")); } catch (Exception e) { done.run("Не удалось загрузить медиа статьи"); return; }
        if (asset.optString("kind").equals("photo")) {
            TLRPC.Photo photo = null; for (TLRPC.Photo item : rich.photos) if (item.id == id) photo = item;
            if (photo == null) { done.run("Не удалось загрузить медиа статьи"); return; }
            TLRPC.TL_inputPhoto reference = new TLRPC.TL_inputPhoto(); reference.id = photo.id; reference.access_hash = photo.access_hash; reference.file_reference = photo.file_reference; TLRPC.TL_inputMediaPhoto media = new TLRPC.TL_inputMediaPhoto(); media.id = reference; request.media = media;
        } else {
            TLRPC.Document doc = null; for (TLRPC.Document item : rich.documents) if (item.id == id) doc = item;
            if (doc == null) { done.run("Не удалось загрузить медиа статьи"); return; }
            TLRPC.TL_inputDocument reference = new TLRPC.TL_inputDocument(); reference.id = doc.id; reference.access_hash = doc.access_hash; reference.file_reference = doc.file_reference; TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument(); media.id = reference; request.media = media;
        }
        ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> { if (error != null) done.run("Не удалось загрузить медиа статьи"); else sendNext(account, owner, bot, rich, stage, assets, index + 1, done); }));
    }
}

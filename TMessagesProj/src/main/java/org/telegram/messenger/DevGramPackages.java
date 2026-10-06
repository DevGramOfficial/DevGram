package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/**
 * DevGram: хостинг бинарей .dgplugin для каталога через Telegram (архив @devgramarchive).
 *
 * Публикация: файл отправляется боту @officialdevgram_bot с подписью «dgpkg:<id>»; бот
 * (modbot) после одобрения перекладывает его в @devgramarchive и пишет координаты
 * (packageChat/packageMsg) прямо в запись каталога. Установка: по этим координатам
 * скачиваем документ из архива и ставим через installPackage.
 */
public final class DevGramPackages {

    public static final String ARCHIVE_USERNAME = "devgramarchive";
    public static final String CATALOG_BOT_USERNAME = "officialdevgram_bot";

    private DevGramPackages() { }

    public interface PeerCallback { void run(TLRPC.User user, TLRPC.Chat chat); }

    // ---------- резолв юзернейма (бот/канал) ----------
    public static void resolveUsernameThen(int account, String username, PeerCallback cb) {
        TLObject cached = MessagesController.getInstance(account).getUserOrChat(username);
        if (cached instanceof TLRPC.User) { cb.run((TLRPC.User) cached, null); return; }
        if (cached instanceof TLRPC.Chat) { cb.run(null, (TLRPC.Chat) cached); return; }
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_contacts_resolvedPeer) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                MessagesController.getInstance(account).putUsers(res.users, false);
                MessagesController.getInstance(account).putChats(res.chats, false);
                MessagesStorage.getInstance(account).putUsersAndChats(res.users, res.chats, true, true);
                TLRPC.User u = res.users.isEmpty() ? null : res.users.get(0);
                TLRPC.Chat c = res.chats.isEmpty() ? null : res.chats.get(0);
                cb.run(u, c);
            } else {
                cb.run(null, null);
            }
        }));
    }

    // ---------- публикация пакета ----------
    // Отправляет .dgplugin боту (dgpkg:<id>), затем создаёт заявку в каталог (pending).
    public static void publishPackage(String path, DevGramPlugins.CatalogEntry e, DevGramPlugins.SubmissionCallback cb) {
        if (e == null || path == null || path.isEmpty()) { AndroidUtilities.runOnUIThread(() -> cb.onResult(0)); return; }
        final File f = new File(path);
        if (!f.exists()) { AndroidUtilities.runOnUIThread(() -> cb.onResult(0)); return; }
        if (!DevGramPlugins.packageValidationError(path).isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> cb.onResult(-3));
            return;
        }
        e.isPackage = true;
        e.packageSize = f.length();
        e.source = "";
        final int account = UserConfig.selectedAccount;
        Utilities.globalQueue.postRunnable(() -> {
            final String sha = sha256File(f);
            AndroidUtilities.runOnUIThread(() -> {
                e.packageSha = sha;
                // Файл боту — best-effort (у бота должен оказаться файл к моменту одобрения).
                resolveUsernameThen(account, CATALOG_BOT_USERNAME, (user, chat) -> {
                    if (user != null) DevGramPlugins.sendFile(user.id, path, "dgpkg:" + e.id);
                });
                // Заявку регистрируем ВСЕГДА — статус «⏳ На модерации» появится сразу,
                // как у .plugin (статусы ключуются по id: pending/catalog/rejected).
                DevGramPlugins.publishToCatalog(e, cb);
            });
        });
    }

    // ---------- установка пакета из каталога ----------
    public static void installCatalogPackage(final DevGramPlugins.CatalogEntry e, final Utilities.Callback<Boolean> whenDone) {
        if (e == null || !e.isPackage || e.packageMsg == 0) { done(whenDone, false); return; }
        DevGramPluginBlocks.check(e.id, e.packageSha, status -> {
            if (!status.success || status.blocked) {
                done(whenDone, false);
                return;
            }
            installCatalogPackageAllowed(e, whenDone);
        });
    }

    private static void installCatalogPackageAllowed(final DevGramPlugins.CatalogEntry e,
                                                     final Utilities.Callback<Boolean> whenDone) {
        fetchCatalogPackage(e, file -> {
            if (file == null) { done(whenDone, false); return; }
            finishInstall(file, e.id, e.packageSha, whenDone);
        });
    }

    // Скачать .dgplugin каталога из архивного канала (без установки) — для установки и для
    // карточки каталога (возможности, сведения). null — не удалось. Колбэк на UI-потоке.
    public static void fetchCatalogPackage(final DevGramPlugins.CatalogEntry e, final Utilities.Callback<File> whenDone) {
        if (e == null || !e.isPackage || e.packageMsg == 0) { AndroidUtilities.runOnUIThread(() -> whenDone.run(null)); return; }
        final int account = UserConfig.selectedAccount;
        resolveUsernameThen(account, ARCHIVE_USERNAME, (user, chat) -> {
            if (chat == null) { whenDone.run(null); return; }
            TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
            req.channel = MessagesController.getInstance(account).getInputChannel(chat.id);
            req.id.add(e.packageMsg);
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (!(response instanceof TLRPC.messages_Messages)) { whenDone.run(null); return; }
                TLRPC.messages_Messages mm = (TLRPC.messages_Messages) response;
                MessagesController.getInstance(account).putUsers(mm.users, false);
                MessagesController.getInstance(account).putChats(mm.chats, false);
                TLRPC.Document doc = null;
                for (int i = 0; i < mm.messages.size(); i++) {
                    TLRPC.Message m = mm.messages.get(i);
                    if (m != null && m.media != null && m.media.document != null) { doc = m.media.document; break; }
                }
                if (doc == null) { whenDone.run(null); return; }
                download(account, doc, whenDone);
            }));
        });
    }

    private static void download(final int account, final TLRPC.Document doc, final Utilities.Callback<File> whenDone) {
        File existing = FileLoader.getInstance(account).getPathToAttach(doc, true);
        if (existing != null && existing.exists() && existing.length() > 0) {
            whenDone.run(existing);
            return;
        }
        final String attachName = FileLoader.getAttachFileName(doc);
        final NotificationCenter nc = NotificationCenter.getInstance(account);
        final NotificationCenter.NotificationCenterDelegate[] holder = new NotificationCenter.NotificationCenterDelegate[1];
        holder[0] = (id, acc, args) -> {
            if (args.length == 0 || !attachName.equals(args[0])) return;
            if (id == NotificationCenter.fileLoaded || id == NotificationCenter.fileLoadFailed) {
                nc.removeObserver(holder[0], NotificationCenter.fileLoaded);
                nc.removeObserver(holder[0], NotificationCenter.fileLoadFailed);
                File file = id == NotificationCenter.fileLoaded ? FileLoader.getInstance(account).getPathToAttach(doc, true) : null;
                whenDone.run(file != null && file.exists() && file.length() > 0 ? file : null);
            }
        };
        nc.addObserver(holder[0], NotificationCenter.fileLoaded);
        nc.addObserver(holder[0], NotificationCenter.fileLoadFailed);
        FileLoader.getInstance(account).loadFile(doc, "dgpkg", FileLoader.PRIORITY_HIGH, 1);
    }

    private static void finishInstall(final File file, final String pluginId, final String expectedSha,
                                      final Utilities.Callback<Boolean> whenDone) {
        if (file == null || !file.exists() || file.length() == 0) { done(whenDone, false); return; }
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            try {
                String actualSha = sha256File(file);
                if (expectedSha == null || expectedSha.isEmpty() || expectedSha.equalsIgnoreCase(actualSha)) {
                    DevGramPluginBlocks.Fingerprint fingerprint =
                            DevGramPluginBlocks.fingerprintPackage(file.getAbsolutePath());
                    DevGramPluginBlocks.Status block =
                            DevGramPluginBlocks.checkBlocking(pluginId, actualSha, fingerprint);
                    if (block.success && !block.blocked) {
                        ok = DevGramPlugins.installPackage(file.getAbsolutePath(), pluginId, true);
                    } else if (!block.success) {
                        FileLog.e("DevGramPackages: plugin block status unavailable for " + pluginId);
                    } else {
                        FileLog.e("DevGramPackages: blocked plugin " + pluginId);
                    }
                } else {
                    FileLog.e("DevGramPackages: sha mismatch for " + pluginId);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final boolean res = ok;
            AndroidUtilities.runOnUIThread(() -> whenDone.run(res));
        });
    }

    private static void done(Utilities.Callback<Boolean> cb, boolean v) {
        if (cb != null) AndroidUtilities.runOnUIThread(() -> cb.run(v));
    }

    // ---------- sha256 файла ----------
    public static String sha256File(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return "";
        }
    }
}

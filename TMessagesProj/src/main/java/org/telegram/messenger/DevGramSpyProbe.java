package org.telegram.messenger;

import android.util.LongSparseArray;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

/** Debounced status probe, optionally using another logged-in account. */
public final class DevGramSpyProbe {
    private static final LongSparseArray<Long> requested = new LongSparseArray<>();
    private DevGramSpyProbe() {}

    public static void probe(int targetAccount, long userId) {
        if (!DevGramConfig.probeUsingOtherAccounts || userId <= 0) return;
        synchronized (requested) {
            long now = System.currentTimeMillis();
            Long last = requested.get(userId);
            if (last != null && now - last < 60_000) return;
            requested.put(userId, now);
        }
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (account == targetAccount) continue;
            if (!UserConfig.getInstance(account).isClientActivated()) continue;
            TLRPC.InputUser input = MessagesController.getInstance(account).getInputUser(userId);
            if (input == null) continue;
            TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers(); req.id.add(input);
            final int sourceAccount = account;
            ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> {
                if (!(res instanceof Vector)) return;
                for (Object object : ((Vector) res).objects) if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    int now = ConnectionsManager.getInstance(sourceAccount).getCurrentTime();
                    if (user.status != null && user.status.expires > now) {
                        DevGramMessagesController.getInstance().saveLastSeen(targetAccount, userId, now);
                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(targetAccount)
                                .postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_STATUS));
                    }
                }
            });
        }
    }
}

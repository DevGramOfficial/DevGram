package org.telegram.messenger.devgram.player;

import java.util.function.BooleanSupplier;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
public final class CoverColorsWait {
    private final BooleanSupplier enabled;
    private boolean expired;
    private MessageObject messageObject;
    private final Runnable onTimeout;
    private boolean scheduled;
    private final Runnable timeout = () -> expire();

    public CoverColorsWait(BooleanSupplier booleanSupplier, Runnable runnable) {
        this.enabled = booleanSupplier;
        this.onTimeout = runnable;
    }

    public boolean shouldWait(MessageObject messageObject) {
        if (this.messageObject != messageObject) {
            cancel();
            this.messageObject = messageObject;
        }
        if (this.expired || !isPending(messageObject)) {
            return false;
        }
        if (!this.scheduled) {
            this.scheduled = true;
            AndroidUtilities.runOnUIThread(this.timeout, 1000L);
        }
        return true;
    }

    public void cancel() {
        AndroidUtilities.cancelRunOnUIThread(this.timeout);
        this.scheduled = false;
        this.expired = false;
    }
    public void expire() {
        this.scheduled = false;
        this.expired = true;
        this.onTimeout.run();
    }

    private boolean isPending(MessageObject messageObject) {
        return this.enabled.getAsBoolean() && messageObject != null && !messageObject.isVoice() && !messageObject.isRoundVideo() && CoverColorExtractor.getCachedSeed(messageObject) == null && PlayerCovers.hasCover(messageObject);
    }
}

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.core.graphics.ColorUtils;
import java.util.function.IntSupplier;
import org.telegram.messenger.DevGramThemeEmoji;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

/** Selects an existing white/black TGS file for DevGram decoration slots only. */
public final class DevGramBadgeEmojiDrawable extends AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable {
    private final IntSupplier foreground;
    private long sourceId;
    private int sourceCacheType;
    private int lastForeground;
    private boolean hasForeground, darkInk;

    public DevGramBadgeEmojiDrawable(View host, int size) {
        this(host, false, size, AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS, null);
    }

    public DevGramBadgeEmojiDrawable(View host, int size, int cacheType) {
        this(host, false, size, cacheType, null);
    }

    public DevGramBadgeEmojiDrawable(View host, int size, int cacheType, IntSupplier foreground) {
        this(host, false, size, cacheType, foreground);
    }

    public DevGramBadgeEmojiDrawable(View host, boolean invalidateParent, int size, int cacheType, IntSupplier foreground) {
        super(host, invalidateParent, size, cacheType);
        particlesRelativeToBounds = true;
        this.foreground = foreground != null ? foreground : host instanceof SimpleTextView
                ? () -> ((SimpleTextView) host).getTextColor()
                : () -> Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    private long visibleId() {
        int color = foreground.getAsInt() | 0xff000000;
        if (!hasForeground || lastForeground != color) {
            hasForeground = true;
            lastForeground = color;
            darkInk = ColorUtils.calculateLuminance(color) < 0.179;
        }
        return DevGramThemeEmoji.forTone(sourceId, darkInk);
    }

    @Override public boolean set(long documentId, int cacheType, boolean animated) {
        sourceId = documentId;
        sourceCacheType = cacheType;
        return super.set(visibleId(), cacheType, animated);
    }

    @Override public void set(TLRPC.Document document, int cacheType, boolean animated) {
        if (document != null && DevGramThemeEmoji.forTone(document.id, true) != DevGramThemeEmoji.forTone(document.id, false)) {
            set(document.id, cacheType, animated);
        } else {
            sourceId = 0;
            super.set(document, cacheType, animated);
        }
    }

    @Override public void set(Drawable drawable, boolean animated) {
        sourceId = 0;
        super.set(drawable, animated);
    }

    @Override public void draw(Canvas canvas) {
        if (sourceId != 0) {
            // The native setter returns immediately for the current ID, including pending downloads.
            if (super.set(visibleId(), sourceCacheType, false)) {
                super.removeOldDrawable();
            }
        }
        super.draw(canvas);
    }
}

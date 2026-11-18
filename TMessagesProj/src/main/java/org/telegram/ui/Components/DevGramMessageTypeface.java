package org.telegram.ui.Components;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramProfileDocument;
import org.telegram.messenger.DevGramProfileCustomization;
import org.telegram.messenger.MessagesController;
import java.util.LinkedHashMap;

/** Reference ChatBubbles: draw-only typeface, native line metrics, bounded probe cache. */
public final class DevGramMessageTypeface {
    private static final String PROBE = "Привет, как дела? Hello there 0123456789";
    private static final LinkedHashMap<Key, Float> WIDTHS = new LinkedHashMap<>(32, .75f, true);
    private static final Paint RULER = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long sender = Long.MIN_VALUE, checked;
    private int version = Integer.MIN_VALUE;
    private Typeface selected, lastFont, lastBase;
    private int lastSize = -1;
    private float lastScale = 1;

    public Typeface resolve(long uid) {
        int revision = DevGramBadges.styleVersion;
        long now = SystemClock.uptimeMillis();
        if (sender != uid || version != revision || now - checked >= 60000) {
            sender = uid; version = revision; checked = now;
            selected = uid > 0 && DevGramProfileCustomization.enabled()
                    && MessagesController.getGlobalMainSettings().getBoolean("devgramChatBubbles", true)
                    ? DevGramProfileDocument.messageFont(DevGramProfileDocument.get(uid)) : null;
        }
        return selected;
    }

    public float squeeze(Typeface font, Typeface base, float size) {
        int rounded = Math.round(size);
        if (font == lastFont && base == lastBase && rounded == lastSize) return lastScale;
        Key key = new Key(font, base, rounded);
        float result;
        synchronized (WIDTHS) {
            Float cached = WIDTHS.get(key);
            if (cached != null) result = cached;
            else {
                RULER.setTextSize(size); RULER.setTextScaleX(1); RULER.setTypeface(base);
                float original = RULER.measureText(PROBE);
                RULER.setTypeface(font);
                float custom = RULER.measureText(PROBE);
                result = original > 0 && custom > original ? original / custom : 1;
                WIDTHS.put(key, result);
                if (WIDTHS.size() > 128) WIDTHS.remove(WIDTHS.keySet().iterator().next());
            }
        }
        lastFont = font; lastBase = base; lastSize = rounded; lastScale = result;
        return result;
    }

    private static final class Key {
        final Typeface font, base;
        final int size;
        Key(Typeface font, Typeface base, int size) { this.font=font; this.base=base; this.size=size; }
        @Override public int hashCode() { return (System.identityHashCode(font)*31+System.identityHashCode(base))*31+size; }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Key)) return false;
            Key key=(Key)value;
            return font==key.font && base==key.base && size==key.size;
        }
    }
}

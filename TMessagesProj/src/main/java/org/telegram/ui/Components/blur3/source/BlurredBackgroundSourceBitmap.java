package org.telegram.ui.Components.blur3.source;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.utils.glass.source.GlassSourceBitmap;

/** Compatibility name for DevGram consumers of the shared bitmap glass source. */
public class BlurredBackgroundSourceBitmap extends GlassSourceBitmap implements BlurredBackgroundSource {
    @Override
    public BlurredBackgroundDrawable createDrawable() {
        return new BlurredBackgroundDrawable(this);
    }
}

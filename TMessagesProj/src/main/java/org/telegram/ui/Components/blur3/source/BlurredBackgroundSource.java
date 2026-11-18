package org.telegram.ui.Components.blur3.source;

import android.graphics.Canvas;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.utils.glass.source.GlassSource;

public interface BlurredBackgroundSource extends GlassSource {
    default BlurredBackgroundDrawable createDrawable() {
        return new BlurredBackgroundDrawable(this);
    }

    default void dispatchOnDrawablesRelativePositionChange() {}
}


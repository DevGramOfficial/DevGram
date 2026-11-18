package org.telegram.ui.Components.blur3.source;

import android.graphics.Canvas;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.utils.glass.source.GlassSource;

public class BlurredBackgroundSourceWrapped implements BlurredBackgroundSource {
    @Override
    public BlurredBackgroundDrawable createDrawable() {
        return new BlurredBackgroundDrawable(this);
    }


    private BlurredBackgroundSource sourceInternal;

    public BlurredBackgroundSource getSource() {
        return sourceInternal;
    }

    public void setSource(BlurredBackgroundSource sourceInternal) {
        this.sourceInternal = sourceInternal;
    }

    public void setSource(GlassSource source) {
        if (source == null) {
            sourceInternal = null;
        } else if (source instanceof BlurredBackgroundSource) {
            sourceInternal = (BlurredBackgroundSource) source;
        } else {
            sourceInternal = new BlurredBackgroundSource() {
                @Override
                public void draw(android.graphics.Canvas canvas, float left, float top, float right, float bottom) {
                    source.draw(canvas, left, top, right, bottom);
                }
            };
        }
    }

    @Override
    public void draw(Canvas canvas, float left, float top, float right, float bottom) {
        if (sourceInternal != null) {
            sourceInternal.draw(canvas, left, top, right, bottom);
        }
    }
}


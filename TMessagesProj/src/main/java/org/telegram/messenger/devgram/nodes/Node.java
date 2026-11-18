package org.telegram.messenger.devgram.nodes;
import android.graphics.Bitmap;
public interface Node {
    String id();String name();String hint();int category();Knob[] knobs();
    Bitmap filter(Bitmap source,int[] values);
    void render(RenderContext context);
}

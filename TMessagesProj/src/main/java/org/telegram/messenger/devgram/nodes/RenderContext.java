package org.telegram.messenger.devgram.nodes;
import android.graphics.Bitmap;
import android.graphics.Paint;
public interface RenderContext {int alpha();int[] knobs();Bitmap image();void drawImage(Bitmap image,Paint paint);}

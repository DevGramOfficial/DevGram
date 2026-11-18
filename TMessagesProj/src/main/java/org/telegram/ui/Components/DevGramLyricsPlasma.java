package org.telegram.ui.Components;
import org.telegram.messenger.FileLog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

public final class DevGramLyricsPlasma extends View {
    private static final float EDGE = 0.16f;
    private static final float FALL = 0.06f;
    private static final int FRAME_MS = 33;
    private static final int GRID = 40;
    private static final int GRID_MAX_ROWS = 72;
    private static final int LUT = 256;
    private static final float RISE = 0.5f;
    private static final int SIN = 1024;
    private static final float[] SINE = new float[1024];
    private static final int TONES = 3;
    private static final float TO_SINE = 162.97466f;
    private volatile float bassIn;
    private float beat;
    private final RectF box;
    private int cols;
    private boolean dark;
    private float energy;
    private volatile float energyIn;
    private float[] fade;
    private Bitmap frame;
    private long lastFrame;
    private final int[] lut;
    private final Paint paint;
    private int[] pixels;
    private int rows;
    private boolean running;
    private float time;
    private final int[] tones;

    private static float clamp(float f, float f2, float f3) {
        if (f < f2) {
            return f2;
        }
        return f > f3 ? f3 : f;
    }

    static {
        for (int i = 0; i < 1024; i++) {
            SINE[i] = (float) Math.sin((((double) (i * 2)) * 3.141592653589793d) / 1024.0d);
        }
    }

    public DevGramLyricsPlasma(Context context) {
        super(context);
        this.paint = new Paint(2);
        this.box = new RectF();
        this.lut = new int[LUT];
        this.tones = new int[3];
        this.dark = true;
        learn(null, true);
    }

    public void pulse(float f, float f2) {
        this.bassIn = f;
        this.energyIn = f2;
    }

    public void start() {
        this.running = true;
        this.lastFrame = 0L;
        invalidate();
    }

    public void stop() {
        this.running = false;
    }

    public void learn(Bitmap bitmap, boolean z) {
        int[] iArr;
        this.dark = z;
        int[] iArrPick = bitmap == null ? null : pick(bitmap);
        if (iArrPick == null) {
            if (z) {
                iArr = new int[]{-10797146, -13669453, -6078836};
            } else {
                iArr = new int[]{-4610318, -6371339, -1003300};
            }
            iArrPick = iArr;
        }
        for (int i = 0; i < 3; i++) {
            this.tones[i] = tune(iArrPick[i % iArrPick.length], z);
        }
        palette();
        invalidate();
    }

    private static int[] pick(Bitmap bitmap) {
        int i = 13;
        float[] fArr = new float[13];
        float[] fArr2 = new float[13];
        float[] fArr3 = new float[13];
        float[] fArr4 = new float[13];
        float[] fArr5 = new float[3];
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int i2 = 0;
        float f = 0.0f;
        while (true) {
            float f2 = 360.0f;
            char c = 1;
            if (i2 >= height) {
                break;
            }
            int i3 = 0;
            while (i3 < width) {
                int pixel = bitmap.getPixel(i3, i2);
                Color.colorToHSV(pixel, fArr5);
                float f3 = fArr5[2];
                if (f3 >= 0.1f) {
                    float f4 = fArr5[c];
                    float f5 = (f3 * f4) + 0.2f;
                    f += f5;
                    int i4 = 12;
                    if (f4 >= 0.12f) {
                        i4 = ((int) ((fArr5[0] / f2) * 12.0f)) % 12;
                    }
                    fArr[i4] = fArr[i4] + f5;
                    fArr2[i4] = fArr2[i4] + (Color.red(pixel) * f5);
                    fArr3[i4] = fArr3[i4] + (Color.green(pixel) * f5);
                    fArr4[i4] = fArr4[i4] + (Color.blue(pixel) * f5);
                }
                i3++;
                f2 = 360.0f;
                c = 1;
            }
            i2++;
        }
        if (f == 0.0f) {
            return null;
        }
        int[] iArr = new int[3];
        boolean[] zArr = new boolean[13];
        int i5 = 0;
        int i6 = 0;
        while (i5 < 3) {
            int i7 = -1;
            for (int i8 = 0; i8 < i; i8++) {
                if (!zArr[i8]) {
                    float f6 = fArr[i8];
                    if (f6 > 0.03f * f && (i7 < 0 || f6 > fArr[i7])) {
                        i7 = i8;
                    }
                }
            }
            if (i7 < 0) {
                break;
            }
            zArr[i7] = true;
            iArr[i6] = Color.rgb(Math.round(fArr2[i7] / fArr[i7]), Math.round(fArr3[i7] / fArr[i7]), Math.round(fArr4[i7] / fArr[i7]));
            i5++;
            i6++;
            i = 13;
        }
        if (i6 == 0) {
            return null;
        }
        while (i6 < 3) {
            Color.colorToHSV(iArr[0], fArr5);
            fArr5[0] = (fArr5[0] + (i6 == 1 ? 40.0f : 320.0f)) % 360.0f;
            iArr[i6] = Color.HSVToColor(fArr5);
            i6++;
        }
        return iArr;
    }

    private static int tune(int i, boolean z) {
        float[] fArr = new float[3];
        Color.colorToHSV(i, fArr);
        float fClamp = fArr[1];
        boolean z2 = fClamp < 0.12f;
        if (z) {
            if (!z2) {
                fClamp = clamp(fClamp * 1.2f, 0.45f, 1.0f);
            }
            fArr[1] = fClamp;
            fArr[2] = clamp(fArr[2], 0.36f, 0.62f);
        } else {
            if (!z2) {
                fClamp = clamp(fClamp, 0.28f, 0.65f);
            }
            fArr[1] = fClamp;
            fArr[2] = clamp(fArr[2] + 0.25f, 0.8f, 1.0f);
        }
        return Color.HSVToColor(fArr);
    }

    private void palette() {
        for (int i = 0; i < LUT; i++) {
            float f = (i / 256.0f) * 3.0f;
            int i2 = (int) f;
            float f2 = f - i2;
            int[] iArr = this.tones;
            int i3 = iArr[i2 % 3];
            int i4 = iArr[(i2 + 1) % 3];
            this.lut[i] = Color.rgb(Math.round(Color.red(i3) + ((Color.red(i4) - Color.red(i3)) * f2)), Math.round(Color.green(i3) + ((Color.green(i4) - Color.green(i3)) * f2)), Math.round(Color.blue(i3) + ((Color.blue(i4) - Color.blue(i3)) * f2)));
        }
    }

    @Override // android.view.View
    protected void onSizeChanged(int i, int i2, int i3, int i4) {
        super.onSizeChanged(i, i2, i3, i4);
        if (i <= 0 || i2 <= 0) {
            return;
        }
        this.cols = 40;
        float f = i;
        int iMax = Math.max(8, Math.min(72, Math.round((i2 * 40) / f)));
        this.rows = iMax;
        this.pixels = new int[this.cols * iMax];
        this.fade = new float[iMax];
        int iMax2 = Math.max(1, Math.round(iMax * EDGE));
        for (int i5 = 0; i5 < this.rows; i5++) {
            float f2 = iMax2;
            float fMin = Math.min(Math.min(1.0f, (i5 + 0.5f) / f2), Math.min(1.0f, ((this.rows - i5) - 0.5f) / f2));
            this.fade[i5] = fMin * fMin * (3.0f - (fMin * 2.0f));
        }
        Bitmap bitmap = this.frame;
        this.frame = null;
        if (bitmap != null) {
            bitmap.recycle();
        }
        try {
            this.frame = Bitmap.createBitmap(this.cols, this.rows, Bitmap.Config.ARGB_8888);
        } catch (Throwable th) {
            FileLog.e(th);
        }
        this.box.set(0.0f, 0.0f, f, i2);
    }

    private static float sine(float f) {
        return SINE[((int) (f * TO_SINE)) & 1023];
    }

    private void step() {
        long jUptimeMillis = SystemClock.uptimeMillis();
        long j = this.lastFrame;
        float fMin = j == 0 ? 0.033f : Math.min(0.1f, (jUptimeMillis - j) / 1000.0f);
        this.lastFrame = jUptimeMillis;
        float f = this.bassIn;
        float f2 = this.energyIn;
        float f3 = this.beat;
        float f4 = f - f3;
        float f5 = FALL;
        float f6 = f3 + (f4 * (f > f3 ? 0.5f : FALL));
        this.beat = f6;
        float f7 = this.energy;
        float f8 = f2 - f7;
        if (f2 > f7) {
            f5 = 0.5f;
        }
        float f9 = f7 + (f8 * f5);
        this.energy = f9;
        float f10 = this.time + (fMin * ((f9 * 1.1f) + 0.45f));
        this.time = f10;
        float f11 = 1.0f - (f6 * 0.18f);
        float fSine = (sine(f10 * 0.37f) * 0.4f) + 0.5f;
        float fSine2 = (sine((this.time * 0.29f) + 1.7f) * 0.4f) + 0.5f;
        float f12 = this.rows / (float) this.cols;
        int iRound = Math.round((this.dark ? 168 : 144) + (this.beat * 80.0f));
        int i = 255;
        int iMin = Math.min(255, iRound);
        int i2 = 0;
        int i3 = 0;
        while (true) {
            int i4 = this.rows;
            if (i2 < i4) {
                float f13 = (i2 / (float) i4) * f12;
                int iRound2 = Math.round(iMin * this.fade[i2]);
                int i5 = 0;
                while (i5 < this.cols) {
                    int i6 = this.cols;
                        float f14 = i5 / (float) i6;
                        float f15 = f14 - fSine;
                        float f16 = f13 - (fSine2 * f12);
                        this.pixels[i3] = (this.lut[((int) ((sine((3.1f * f14 * f11) + this.time) + sine(((2.6f * f13) * f11) - (this.time * 0.8f)) + sine(((f14 + f13) * 2.2f * f11) + (this.time * 1.3f)) + sine(((((float) Math.sqrt((f15 * f15) + (f16 * f16))) * 5.5f) * f11) - (this.time * 1.1f)) + 4.0f) * 32.0f)) & i] & 16777215) | (iRound2 << 24);
                        i5++;
                        i3++;
                        i = 255;
                }
                i2++;
                i = 255;
            } else {
                Bitmap bitmap = this.frame;
                int[] iArr = this.pixels;
                int i7 = this.cols;
                bitmap.setPixels(iArr, 0, i7, 0, 0, i7, i4);
                return;
            }
        }
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        if (this.frame == null || this.pixels == null) {
            return;
        }
        if (this.running || this.lastFrame == 0) {
            try {
                step();
            } catch (Throwable th) {
                this.running = false;
                FileLog.e(th);
            }
        }
        canvas.drawBitmap(this.frame, (Rect) null, this.box, this.paint);
        if (this.running) {
            postInvalidateDelayed(33L);
        }
    }

    @Override // android.view.View
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}

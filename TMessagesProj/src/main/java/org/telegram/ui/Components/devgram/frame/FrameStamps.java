package org.telegram.ui.Components.devgram.frame;

/** Native port of the user-supplied Custom Profile frame geometry. */
public final class FrameStamps {
    public static final float CORNER_DEGREES = 20.0f;
    public static final float CORNER_WINDOW = 0.02f;
    public static final int STRIDE = 3;

    private FrameStamps() {
    }

    public static int repeated(FrameContour frameContour, int i, float f, float f2, float[] fArr, float[] fArr2) {
        if (frameContour == null || frameContour.isEmpty() || i <= 0 || fArr == null || fArr2 == null || fArr2.length < 4 || fArr.length < i * 3) {
            return 0;
        }
        for (int i2 = 0; i2 < i; i2++) {
            place(frameContour, f + (i2 / (float)i), f2, fArr, i2 * 3, fArr2);
        }
        return i;
    }

    public static boolean single(FrameContour frameContour, float f, float f2, float[] fArr, float[] fArr2) {
        if (frameContour == null || frameContour.isEmpty() || fArr == null || fArr.length < 3 || fArr2 == null || fArr2.length < 4) {
            return false;
        }
        place(frameContour, f, f2, fArr, 0, fArr2);
        return true;
    }

    public static boolean sticker(float f, float f2, int i, float f3, float[] fArr) {
        if (fArr == null || fArr.length < 3 || f3 <= 0.0f) {
            return false;
        }
        fArr[0] = f * f3;
        fArr[1] = f2 * f3;
        fArr[2] = i;
        return true;
    }

    public static boolean hung(float f, float f2, float f3, float f4, float[] fArr) {
        if (fArr == null || fArr.length < 2) {
            return false;
        }
        float f5 = f - 0.5f;
        float f6 = f2 - 0.5f;
        if (f3 != 0.0f) {
            double radians = Math.toRadians(f3);
            float fCos = (float) Math.cos(radians);
            float fSin = (float) Math.sin(radians);
            float f7 = (f5 * fCos) - (f6 * fSin);
            f6 = (f6 * fCos) + (f5 * fSin);
            f5 = f7;
        }
        if (f4 != 0.0f) {
            float fSqrt = (float) Math.sqrt((f5 * f5) + (f6 * f6));
            if (fSqrt > 1.0E-4f) {
                f5 += (f5 / fSqrt) * f4;
                f6 += (f6 / fSqrt) * f4;
            }
        }
        fArr[0] = f5 + 0.5f;
        fArr[1] = f6 + 0.5f;
        return true;
    }

    public static boolean atCorner(float[] fArr, int i, float f) {
        if (fArr != null && i > 0) {
            float fWrap = FrameContour.wrap(f);
            for (int i2 = 0; i2 < i && i2 < fArr.length; i2++) {
                float fAbs = Math.abs(fWrap - FrameContour.wrap(fArr[i2]));
                if (Math.min(fAbs, 1.0f - fAbs) <= 0.02f) {
                    return true;
                }
            }
        }
        return false;
    }

    public static float at(int i, int i2, float f) {
        if (i2 <= 0) {
            return FrameContour.wrap(f);
        }
        return FrameContour.wrap(f + (i / (float)i2));
    }

    private static void place(FrameContour frameContour, float f, float f2, float[] fArr, int i, float[] fArr2) {
        frameContour.posTan(f, fArr2);
        fArr[i] = fArr2[0] + (frameContour.normalX(fArr2[3]) * f2);
        fArr[i + 1] = fArr2[1] + (frameContour.normalY(fArr2[2]) * f2);
        fArr[i + 2] = (float) Math.toDegrees(Math.atan2(fArr2[3], fArr2[2]));
    }
}

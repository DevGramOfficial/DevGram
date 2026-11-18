package org.telegram.ui.Components.devgram.frame;

/** Native port of the user-supplied Custom Profile frame geometry. */
public final class FrameStrip {
    public static final int COLUMNS_MAX = 192;
    public static final int COLUMNS_MIN = 24;
    public static final int ROWS = 2;
    private static final float STEP = 4.0f;
    private static volatile int lastFolded;

    private FrameStrip() {
    }

    public static int folded() {
        return lastFolded;
    }

    public static int columns(float f) {
        if (Float.isNaN(f) || f <= 0.0f) {
            return 24;
        }
        return Math.max(24, Math.min(COLUMNS_MAX, Math.round(f / STEP)));
    }

    public static int vertsLength(int i, int i2) {
        return (Math.max(1, i) + 1) * (Math.max(1, i2) + 1) * 2;
    }

    public static boolean verts(FrameRibbon frameRibbon, int i, int i2, float f, float f2, float f3, float[] fArr) {
        FrameRibbon frameRibbon2 = frameRibbon;
        int iMax = Math.max(1, i);
        int iMax2 = Math.max(1, i2);
        int iVertsLength = vertsLength(iMax, iMax2);
        if (fArr == null || fArr.length < iVertsLength) {
            return false;
        }
        float fMin = Math.min(f, f2);
        float fMax = Math.max(f, f2);
        if (frameRibbon2 == null || !frameRibbon2.prepare((fMin + fMax) / 2.0f)) {
            for (int i3 = 0; i3 < iVertsLength; i3++) {
                fArr[i3] = 0.0f;
            }
            return false;
        }
        float fLengthAt = frameRibbon2.lengthAt(f3);
        float f4 = frameRibbon2.total();
        int i4 = 0;
        int iSeek = 0;
        while (i4 <= iMax) {
            iSeek = frameRibbon2.seek(((i4 * f4) / iMax) + fLengthAt, iSeek);
            float fSpotX = frameRibbon2.spotX();
            float fSpotY = frameRibbon2.spotY();
            float fSpotNX = frameRibbon2.spotNX();
            float fSpotNY = frameRibbon2.spotNY();
            for (int i5 = 0; i5 <= iMax2; i5++) {
                float f5 = fMax + ((fMin - fMax) * (i5 / (float)iMax2));
                int i6 = (((iMax + 1) * i5) + i4) * 2;
                fArr[i6] = (fSpotNX * f5) + fSpotX;
                fArr[i6 + 1] = fSpotY + (f5 * fSpotNY);
            }
            i4++;
            frameRibbon2 = frameRibbon;
        }
        int iUnfold = 0;
        for (int i7 = 0; i7 <= iMax2; i7++) {
            int i8 = (iMax + 1) * i7 * 2;
            iUnfold += FrameFold.unfold(fArr, i8, iMax);
            int i9 = (iMax * 2) + i8;
            fArr[i9] = fArr[i8];
            fArr[i9 + 1] = fArr[i8 + 1];
        }
        if (iUnfold > 0) {
            lastFolded = iUnfold;
        }
        return true;
    }
}

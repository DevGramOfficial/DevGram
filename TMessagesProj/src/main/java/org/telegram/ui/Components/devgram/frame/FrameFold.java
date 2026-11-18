package org.telegram.ui.Components.devgram.frame;

/** Native port of the user-supplied Custom Profile frame geometry. */
public final class FrameFold {
    private static final float LOOK_AHEAD = 0.2f;

    private FrameFold() {
    }

    public static int unfold(float[] fArr, int i, int i2) {
        int i3;
        if (fArr == null || i2 < 4 || i < 0 || (i2 * 2) + i > fArr.length) {
            return 0;
        }
        int iMax = Math.max(2, Math.round(i2 * 0.2f));
        int i4 = 0;
        int i5 = 0;
        for (int i6 = 0; i6 < i2; i6 = i3) {
            int i7 = i4 + 1;
            if (i4 > i2) {
                break;
            }
            int i8 = i6 + 1;
            float fDistance = distance(fArr, i, i2, i6, i8);
            i3 = i8;
            for (int i9 = i6 + 2; i9 <= i6 + iMax; i9++) {
                if (distance(fArr, i, i2, i6, i9) < fDistance) {
                    i3 = i9;
                }
            }
            while (i8 < i3) {
                int i10 = i3 % i2;
                set(fArr, i, i8 % i2, get(fArr, i, i10, 0), get(fArr, i, i10, 1));
                i5++;
                i8++;
            }
            i4 = i7;
        }
        return i5;
    }

    private static float distance(float[] fArr, int i, int i2, int i3, int i4) {
        int i5 = i3 % i2;
        int i6 = i4 % i2;
        float f = get(fArr, i, i5, 0) - get(fArr, i, i6, 0);
        float f2 = get(fArr, i, i5, 1) - get(fArr, i, i6, 1);
        return (f * f) + (f2 * f2);
    }

    private static float get(float[] fArr, int i, int i2, int i3) {
        return fArr[i + (i2 * 2) + i3];
    }

    private static void set(float[] fArr, int i, int i2, float f, float f2) {
        int i3 = i + (i2 * 2);
        fArr[i3] = f;
        fArr[i3 + 1] = f2;
    }
}

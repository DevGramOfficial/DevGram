package org.telegram.ui.Components;
import org.telegram.messenger.AndroidUtilities;

/** Adapted from the user supplied Custom Profile editor. */
final class DevGramReferenceActionShape {
    static final int BOTTOM = 8;
    static final int LEFT = 1;
    static final int RIGHT = 4;
    static final int TOP = 2;

    private DevGramReferenceActionShape() {
    }

    static int neighbor(float f, float f2, float f3, float f4, float f5, float f6, float f7, float f8, float f9, float f10) {
        int i = 0;
        if (Math.abs(f2 - f6) <= f10 && Math.abs(f4 - f8) <= f10) {
            if (f5 >= f3 && f5 - f3 <= f9) {
                i = 4;
            }
            if (f7 <= f && f - f7 <= f9) {
                i |= 1;
            }
        }
        if (Math.abs(f - f5) <= f10 && Math.abs(f3 - f7) <= f10) {
            if (f6 >= f4 && f6 - f4 <= f9) {
                i |= 8;
            }
            if (f8 <= f2 && f2 - f8 <= f9) {
                return i | 2;
            }
        }
        return i;
    }

    static float[] radii(float f, float f2, int i, float f3) {
        return radii(f, f2, i, f3, Float.MAX_VALUE);
    }

    static float[] radii(float f, float f2, int i, float f3, float maximumRadius) {
        float fMax = Math.max(0.0f, Math.min(maximumRadius, Math.min(f, f2) / 2.0f));
        float fMin = Math.min(fMax, f3);
        float f4 = (i & 3) != 0 ? fMin : fMax;
        float f5 = (i & 6) != 0 ? fMin : fMax;
        float f6 = (i & 12) != 0 ? fMin : fMax;
        if ((i & 9) != 0) {
            fMax = fMin;
        }
        return new float[]{f4, f4, f5, f5, f6, f6, fMax, fMax};
    }
}

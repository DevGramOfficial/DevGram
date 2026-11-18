package org.telegram.ui.Components.devgram.frame;

/** Native port of the user-supplied Custom Profile frame core; bounded data only. */
public final class FrameContour {
    public static final FrameContour EMPTY = new FrameContour(new float[0], new float[0], 0.0f, 1.0f, 0.0f, 0.0f);
    private final float length;
    private final float[] lengths;
    private final float middleX;
    private final float middleY;
    private final float outward;
    private final float[] points;

    private static float normalizeDegrees(float f) {
        float f2 = f % 360.0f;
        if (f2 > 180.0f) {
            f2 -= 360.0f;
        }
        return f2 < -180.0f ? f2 + 360.0f : f2;
    }

    private FrameContour(float[] fArr, float[] fArr2, float f, float f2, float f3, float f4) {
        this.points = fArr;
        this.lengths = fArr2;
        this.length = f;
        this.outward = f2;
        this.middleX = f3;
        this.middleY = f4;
    }

    public static FrameContour of(float[] fArr) {
        return of(fArr, Float.NaN, Float.NaN);
    }

    public static FrameContour of(float[] fArr, float f, float f2) {
        float f3;
        if (fArr == null || fArr.length < 6) {
            return EMPTY;
        }
        int length = fArr.length / 2;
        int i = length * 2;
        float[] fArr2 = new float[i];
        System.arraycopy(fArr, 0, fArr2, 0, i);
        for (int i2 = 0; i2 < i; i2++) {
            if (Float.isNaN(fArr2[i2]) || Float.isInfinite(fArr2[i2])) {
                return EMPTY;
            }
        }
        float[] fArrFromTop = fromTop(fArr2);
        float[] fArr3 = new float[length + 1];
        float fSqrt = 0.0f;
        int i3 = 0;
        while (i3 < length) {
            int i4 = i3 + 1;
            int i5 = (i4 % length) * 2;
            int i6 = i3 * 2;
            float f4 = fArrFromTop[i5] - fArrFromTop[i6];
            float f5 = fArrFromTop[i5 + 1] - fArrFromTop[i6 + 1];
            fSqrt += (float) Math.sqrt((f4 * f4) + (f5 * f5));
            fArr3[i4] = fSqrt;
            i3 = i4;
        }
        if (fSqrt <= 0.0f) {
            return EMPTY;
        }
        if (Float.isNaN(f) || Float.isNaN(f2)) {
            float fMax = -3.4028235E38f;
            float fMax2 = -3.4028235E38f;
            float fMin = Float.MAX_VALUE;
            float fMin2 = Float.MAX_VALUE;
            for (int i7 = 0; i7 < length; i7++) {
                int i8 = i7 * 2;
                fMin = Math.min(fMin, fArrFromTop[i8]);
                fMax = Math.max(fMax, fArrFromTop[i8]);
                int i9 = i8 + 1;
                fMin2 = Math.min(fMin2, fArrFromTop[i9]);
                fMax2 = Math.max(fMax2, fArrFromTop[i9]);
            }
            f2 = (fMin2 + fMax2) / 2.0f;
            f3 = (fMin + fMax) / 2.0f;
        } else {
            f3 = f;
        }
        return new FrameContour(fArrFromTop, fArr3, fSqrt, orientation(fArrFromTop) >= 0.0f ? 1.0f : -1.0f, f3, f2);
    }

    private static float[] fromTop(float[] fArr) {
        int length = fArr.length / 2;
        float f = 0.0f;
        float f2 = 0.0f;
        for (int i = 0; i < length; i++) {
            int i2 = i * 2;
            f += fArr[i2];
            f2 += fArr[i2 + 1];
        }
        float f3 = length;
        float f4 = f / f3;
        float f5 = f2 / f3;
        if (orientation(fArr) < 0.0f) {
            fArr = reversed(fArr);
        }
        double d = Double.MAX_VALUE;
        int i3 = 0;
        for (int i4 = 0; i4 < length; i4++) {
            int i5 = i4 * 2;
            double dAbs = Math.abs(Math.atan2(fArr[i5 + 1] - f5, fArr[i5] - f4) + 1.5707963267948966d);
            double dMin = Math.min(dAbs, 6.283185307179586d - dAbs);
            if (dMin < d) {
                i3 = i4;
                d = dMin;
            }
        }
        if (i3 == 0) {
            return fArr;
        }
        float[] fArr2 = new float[fArr.length];
        for (int i6 = 0; i6 < length; i6++) {
            int i7 = i6 * 2;
            int i8 = ((i3 + i6) % length) * 2;
            fArr2[i7] = fArr[i8];
            fArr2[i7 + 1] = fArr[i8 + 1];
        }
        return fArr2;
    }

    private static float[] reversed(float[] fArr) {
        int length = fArr.length / 2;
        float[] fArr2 = new float[fArr.length];
        for (int i = 0; i < length; i++) {
            int i2 = i * 2;
            int i3 = ((length - 1) - i) * 2;
            fArr2[i2] = fArr[i3];
            fArr2[i2 + 1] = fArr[i3 + 1];
        }
        return fArr2;
    }

    public float length() {
        return this.length;
    }

    int nodeCount() {
        return this.points.length / 2;
    }

    float nodeX(int i) {
        return this.points[i * 2];
    }

    float nodeY(int i) {
        return this.points[(i * 2) + 1];
    }

    float nodeAt(int i) {
        float f = this.length;
        if (f > 0.0f && i >= 0) {
            float[] fArr = this.lengths;
            if (i < fArr.length) {
                return fArr[i] / f;
            }
        }
        return 0.0f;
    }

    float outward() {
        return this.outward;
    }

    public boolean isEmpty() {
        return this.length <= 0.0f;
    }

    public void posTan(float f, float[] fArr) {
        if (fArr == null || fArr.length < 4) {
            return;
        }
        if (isEmpty()) {
            fArr[0] = 0.0f;
            fArr[1] = 0.0f;
            fArr[2] = 1.0f;
            fArr[3] = 0.0f;
            return;
        }
        float fWrap = wrap(f) * this.length;
        int iSegment = segment(fWrap);
        float[] fArr2 = this.points;
        int i = iSegment + 1;
        int length = i % (fArr2.length / 2);
        int i2 = iSegment * 2;
        float f2 = fArr2[i2];
        float f3 = fArr2[i2 + 1];
        int i3 = length * 2;
        float f4 = fArr2[i3];
        float f5 = fArr2[i3 + 1];
        float[] fArr3 = this.lengths;
        float f6 = fArr3[i];
        float f7 = fArr3[iSegment];
        float f8 = f6 - f7;
        float f9 = f8 > 0.0f ? (fWrap - f7) / f8 : 0.0f;
        float f10 = f4 - f2;
        fArr[0] = f2 + (f10 * f9);
        float f11 = f5 - f3;
        fArr[1] = f3 + (f9 * f11);
        float fSqrt = (float) Math.sqrt((f10 * f10) + (f11 * f11));
        if (fSqrt <= 0.0f) {
            fArr[2] = 1.0f;
            fArr[3] = 0.0f;
        } else {
            fArr[2] = f10 / fSqrt;
            fArr[3] = f11 / fSqrt;
        }
    }

    public void point(float f, float f2, float[] fArr) {
        if (fArr == null || fArr.length < 2) {
            return;
        }
        float[] fArr2 = new float[4];
        posTan(f, fArr2);
        fArr[0] = fArr2[0] + (normalX(fArr2[3]) * f2);
        fArr[1] = fArr2[1] + (normalY(fArr2[2]) * f2);
    }

    public void point(float f, float f2, float[] fArr, float[] fArr2) {
        if (fArr == null || fArr.length < 4 || fArr2 == null || fArr2.length < 2) {
            return;
        }
        posTan(f, fArr);
        fArr2[0] = fArr[0] + (normalX(fArr[3]) * f2);
        fArr2[1] = fArr[1] + (normalY(fArr[2]) * f2);
    }

    public float angle(float f) {
        float[] sample = new float[4]; posTan(f, sample);
        return (float) Math.toDegrees(Math.atan2(sample[3], sample[2]));
    }

    public float turned(float f, float f2) {
        return (isEmpty() || f2 == 0.0f) ? f : atAngle(around(f) + f2);
    }

    public float around(float f) {
        if (isEmpty()) {
            return 0.0f;
        }
        float[] sample = new float[4]; posTan(f, sample);
        return (float) Math.toDegrees(Math.atan2(sample[1] - this.middleY, sample[0] - this.middleX));
    }

    public float atAngle(float f) {
        if (isEmpty()) {
            return 0.0f;
        }
        float fNormalizeDegrees = normalizeDegrees(f);
        int length = this.points.length / 2;
        int i = 0;
        while (i < length) {
            int i2 = i + 1;
            int i3 = i2 % length;
            float fNormalizeDegrees2 = normalizeDegrees(nodeAngle(i) - fNormalizeDegrees);
            float fNormalizeDegrees3 = normalizeDegrees(nodeAngle(i3) - fNormalizeDegrees);
            if (fNormalizeDegrees2 == 0.0f) {
                return this.lengths[i] / this.length;
            }
            if ((fNormalizeDegrees2 < 0.0f) != (fNormalizeDegrees3 < 0.0f) && Math.abs(fNormalizeDegrees2) + Math.abs(fNormalizeDegrees3) < 180.0f) {
                float fAbs = Math.abs(fNormalizeDegrees2) / (Math.abs(fNormalizeDegrees2) + Math.abs(fNormalizeDegrees3));
                float[] fArr = this.lengths;
                float f2 = fArr[i];
                float f3 = this.length;
                float f4 = f2 / f3;
                if (i3 != 0) {
                    length = i3;
                }
                return f4 + (((fArr[length] / f3) - f4) * fAbs);
            }
            i = i2;
        }
        return 0.0f;
    }

    private float nodeAngle(int i) {
        float[] fArr = this.points;
        int i2 = i * 2;
        return (float) Math.toDegrees(Math.atan2(fArr[i2 + 1] - this.middleY, fArr[i2] - this.middleX));
    }

    public float normalX(float f) {
        return f * this.outward;
    }

    public float normalY(float f) {
        return (-f) * this.outward;
    }

    public int corners(float f, float[] fArr) {
        int i = 0;
        if (fArr == null || fArr.length == 0 || isEmpty()) {
            return 0;
        }
        int length = this.points.length / 2;
        int i2 = 0;
        while (i < length && i2 < fArr.length) {
            int i3 = i + 1;
            float[] fArr2 = this.points;
            int i4 = i * 2;
            float f2 = fArr2[i4];
            int i5 = (((i + length) - 1) % length) * 2;
            float f3 = f2 - fArr2[i5];
            float f4 = fArr2[i4 + 1];
            int i6 = (i3 % length) * 2;
            if (turnDegrees(f3, f4 - fArr2[i5 + 1], fArr2[i6] - f2, fArr2[i6 + 1] - f4) >= f) {
                fArr[i2] = this.lengths[i] / this.length;
                i2++;
            }
            i = i3;
        }
        return i2;
    }

    private static float turnDegrees(float f, float f2, float f3, float f4) {
        float fSqrt = (float) Math.sqrt((f * f) + (f2 * f2));
        float fSqrt2 = (float) Math.sqrt((f3 * f3) + (f4 * f4));
        if (fSqrt <= 0.0f || fSqrt2 <= 0.0f) {
            return 0.0f;
        }
        return (float) Math.toDegrees(Math.acos(Style.clampF(((f * f3) + (f2 * f4)) / (fSqrt * fSqrt2), -1.0f, 1.0f)));
    }

    static float wrap(float f) {
        if (Float.isNaN(f) || Float.isInfinite(f)) {
            return 0.0f;
        }
        float f2 = f % 1.0f;
        return f2 < 0.0f ? f2 + 1.0f : f2;
    }

    private int segment(float f) {
        int length = (this.points.length / 2) - 1;
        int i = 0;
        while (i < length) {
            int i2 = ((i + length) + 1) >>> 1;
            if (this.lengths[i2] <= f) {
                i = i2;
            } else {
                length = i2 - 1;
            }
        }
        return i;
    }

    private static float orientation(float[] fArr) {
        int length = fArr.length / 2;
        float f = 0.0f;
        int i = 0;
        while (i < length) {
            int i2 = i + 1;
            int i3 = i * 2;
            int i4 = (i2 % length) * 2;
            f += (fArr[i3] * fArr[i4 + 1]) - (fArr[i4] * fArr[i3 + 1]);
            i = i2;
        }
        return f;
    }
}

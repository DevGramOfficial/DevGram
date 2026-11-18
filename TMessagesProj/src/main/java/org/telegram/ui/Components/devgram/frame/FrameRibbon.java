package org.telegram.ui.Components.devgram.frame;

/** Native port of the user-supplied Custom Profile frame geometry. */
public final class FrameRibbon {
    private static final int JOIN_ROOM = 512;
    private static final float JOIN_STEP = 6.0f;
    private static final float MID_STEP = 4.0f;
    private static final float TRIM_LIMIT = 0.45f;
    private int count;
    private int sides;
    private float spotNX;
    private float spotNY;
    private float spotX;
    private float spotY;
    private float total;
    private FrameContour contour = FrameContour.EMPTY;
    private float[] x = new float[0];
    private float[] y = new float[0];
    private float[] nx = new float[0];
    private float[] ny = new float[0];
    private float[] at = new float[0];
    private float[] len = new float[0];
    private float[] sideX = new float[0];
    private float[] sideY = new float[0];
    private float[] sideNX = new float[0];
    private float[] sideNY = new float[0];
    private float[] sideLen = new float[0];
    private int[] sideNode = new int[0];
    private float[] cutIn = new float[0];
    private float[] cutOut = new float[0];
    private int key = Integer.MIN_VALUE;

    public void contour(FrameContour frameContour) {
        if (frameContour == null) {
            frameContour = FrameContour.EMPTY;
        }
        if (frameContour == this.contour) {
            return;
        }
        this.contour = frameContour;
        this.count = 0;
        this.total = 0.0f;
        this.key = Integer.MIN_VALUE;
    }

    public FrameContour contour() {
        return this.contour;
    }

    long bytes() {
        return (this.x.length + this.y.length + this.nx.length + this.ny.length + this.at.length + this.len.length + this.sideX.length + this.sideY.length + this.sideNX.length + this.sideNY.length + this.sideLen.length + this.sideNode.length + this.cutIn.length + this.cutOut.length) * 4;
    }

    public boolean isEmpty() {
        return this.count < 2 || this.total <= 0.0f;
    }

    public boolean prepare(float f) {
        if (this.contour.isEmpty()) {
            this.count = 0;
            this.total = 0.0f;
            return false;
        }
        int iRound = Math.round(((Float.isNaN(f) || Float.isInfinite(f)) ? 0.0f : Math.max(0.0f, f)) * MID_STEP);
        if (iRound == this.key && this.count >= 2) {
            return this.total > 0.0f;
        }
        this.key = iRound;
        build(iRound / MID_STEP);
        return !isEmpty();
    }

    public float total() {
        return this.total;
    }

    public int count() {
        return this.count;
    }

    public float lengthAt(float f) {
        if (isEmpty()) {
            return 0.0f;
        }
        float fWrap = FrameContour.wrap(f);
        int i = this.count - 1;
        int i2 = 0;
        while (i2 < i) {
            int i3 = ((i2 + i) + 1) >>> 1;
            if (this.at[i3] <= fWrap) {
                i2 = i3;
            } else {
                i = i3 - 1;
            }
        }
        float[] fArr = this.at;
        float f2 = fArr[i2];
        int i4 = i2 + 1;
        float f3 = i4 < this.count ? fArr[i4] : 1.0f;
        float fClampF = Style.clampF(f3 > f2 ? (fWrap - f2) / (f3 - f2) : 0.0f, 0.0f, 1.0f);
        float[] fArr2 = this.len;
        float f4 = fArr2[i2];
        return f4 + ((fArr2[i4] - f4) * fClampF);
    }

    public int seek(float f, int i) {
        int i2;
        if (isEmpty()) {
            this.spotX = 0.0f;
            this.spotY = 0.0f;
            this.spotNX = 0.0f;
            this.spotNY = 0.0f;
            return 0;
        }
        float f2 = this.total;
        float f3 = f % f2;
        if (f3 < 0.0f) {
            f3 += f2;
        }
        if (i < 0 || i >= this.count || this.len[i] > f3) {
            i = 0;
        }
        while (true) {
            i2 = this.count;
            if (i >= i2 - 1) {
                break;
            }
            int i3 = i + 1;
            if (this.len[i3] >= f3) {
                break;
            }
            i = i3;
        }
        int i4 = i + 1;
        int i5 = i4 % i2;
        float[] fArr = this.len;
        float f4 = fArr[i4];
        float f5 = fArr[i];
        float f6 = f4 - f5;
        float fClampF = Style.clampF(f6 > 0.0f ? (f3 - f5) / f6 : 0.0f, 0.0f, 1.0f);
        float[] fArr2 = this.x;
        float f7 = fArr2[i];
        this.spotX = f7 + ((fArr2[i5] - f7) * fClampF);
        float[] fArr3 = this.y;
        float f8 = fArr3[i];
        this.spotY = f8 + ((fArr3[i5] - f8) * fClampF);
        float[] fArr4 = this.nx;
        float f9 = fArr4[i];
        float f10 = f9 + ((fArr4[i5] - f9) * fClampF);
        float[] fArr5 = this.ny;
        float f11 = fArr5[i];
        float f12 = f11 + ((fArr5[i5] - f11) * fClampF);
        float fSqrt = (float) Math.sqrt((f10 * f10) + (f12 * f12));
        if (fSqrt <= 0.0f) {
            this.spotNX = this.nx[i];
            this.spotNY = this.ny[i];
            return i;
        }
        this.spotNX = f10 / fSqrt;
        this.spotNY = f12 / fSqrt;
        return i;
    }

    public float spotX() {
        return this.spotX;
    }

    public float spotY() {
        return this.spotY;
    }

    public float spotNX() {
        return this.spotNX;
    }

    public float spotNY() {
        return this.spotNY;
    }

    private void build(float f) {
        this.count = 0;
        this.sides = 0;
        this.total = 0.0f;
        int iNodeCount = this.contour.nodeCount();
        if (iNodeCount < 3) {
            return;
        }
        room(iNodeCount);
        collectSides(iNodeCount);
        if (this.sides < 3) {
            this.count = 0;
            return;
        }
        float fOutward = this.contour.outward();
        trimInnerCorners(f, fOutward);
        layStations(fOutward);
        measure(f);
    }

    private void collectSides(int i) {
        int i2 = 0;
        while (i2 < i) {
            int i3 = i2 + 1;
            int i4 = i3 % i;
            float fNodeX = this.contour.nodeX(i4) - this.contour.nodeX(i2);
            float fNodeY = this.contour.nodeY(i4) - this.contour.nodeY(i2);
            float fSqrt = (float) Math.sqrt((fNodeX * fNodeX) + (fNodeY * fNodeY));
            if (fSqrt > 0.0f) {
                int[] iArr = this.sideNode;
                int i5 = this.sides;
                iArr[i5] = i2;
                this.sideLen[i5] = fSqrt;
                float f = fNodeX / fSqrt;
                this.sideX[i5] = f;
                float f2 = fNodeY / fSqrt;
                this.sideY[i5] = f2;
                this.sideNX[i5] = this.contour.normalX(f2);
                this.sideNY[this.sides] = this.contour.normalY(f);
                float[] fArr = this.cutIn;
                int i6 = this.sides;
                fArr[i6] = 0.0f;
                this.cutOut[i6] = 0.0f;
                this.sides = i6 + 1;
            }
            i2 = i3;
        }
    }

    private void trimInnerCorners(float f, float f2) {
        int i = 0;
        while (true) {
            int i2 = this.sides;
            if (i >= i2) {
                return;
            }
            int i3 = ((i + i2) - 1) % i2;
            float f3 = 0.0f;
            float cornerTurn = turn(i3, i);
            if (cornerTurn * f2 < 0.0f) {
                float fTan = ((float) Math.tan(Math.abs(cornerTurn) / 2.0d)) * f;
                float[] fArr = this.sideLen;
                float fMin = Math.min(fArr[i3], fArr[i]) * TRIM_LIMIT;
                if (!Float.isNaN(fTan) && fTan >= 0.0f) {
                    f3 = fTan;
                }
                float fMin2 = Math.min(f3, fMin);
                this.cutOut[i3] = fMin2;
                this.cutIn[i] = fMin2;
            }
            i++;
        }
    }

    private void layStations(float f) {
        float length = this.contour.length();
        int i = 0;
        while (true) {
            int i2 = this.sides;
            if (i >= i2) {
                return;
            }
            int i3 = ((i + i2) - 1) % i2;
            int i4 = this.sideNode[i];
            float fNodeX = this.contour.nodeX(i4);
            float fNodeY = this.contour.nodeY(i4);
            float fTurn = turn(i3, i);
            if (fTurn * f > 0.0f) {
                fan(fNodeX, fNodeY, i3, fTurn, this.contour.nodeAt(i4));
            }
            float f2 = this.sideX[i];
            float f3 = this.cutIn[i];
            add(fNodeX + (f2 * f3), fNodeY + (this.sideY[i] * f3), this.sideNX[i], this.sideNY[i], this.contour.nodeAt(i4) + (this.cutIn[i] / length));
            int[] iArr = this.sideNode;
            int i5 = i + 1;
            int i6 = this.sides;
            int i7 = iArr[i5 % i6];
            float fNodeAt = i == i6 + (-1) ? 1.0f : this.contour.nodeAt(i7);
            float fNodeX2 = this.contour.nodeX(i7) - (this.sideX[i] * this.cutOut[i]);
            float fNodeY2 = this.contour.nodeY(i7);
            float f4 = this.sideY[i];
            float f5 = this.cutOut[i];
            add(fNodeX2, fNodeY2 - (f4 * f5), this.sideNX[i], this.sideNY[i], fNodeAt - (f5 / length));
            i = i5;
        }
    }

    private void fan(float f, float f2, int i, float f3, float f4) {
        double d = f3;
        int iCeil = (int) Math.ceil(Math.abs(Math.toDegrees(d)) / 6.0d);
        for (int i2 = 1; i2 < iCeil; i2++) {
            double d2 = (i2 * d) / iCeil;
            float fCos = (float) Math.cos(d2);
            float fSin = (float) Math.sin(d2);
            float f5 = this.sideNX[i];
            float f6 = this.sideNY[i];
            add(f, f2, (f5 * fCos) - (f6 * fSin), (f5 * fSin) + (f6 * fCos), f4);
        }
    }

    private float turn(int i, int i2) {
        float[] fArr = this.sideNX;
        float f = fArr[i];
        float f2 = fArr[i2];
        float[] fArr2 = this.sideNY;
        float f3 = fArr2[i];
        float f4 = fArr2[i2];
        return (float) Math.atan2((f * f4) - (f3 * f2), (f * f2) + (f3 * f4));
    }

    private void measure(float f) {
        this.len[0] = 0.0f;
        int i = 1;
        while (true) {
            int i2 = this.count;
            if (i <= i2) {
                int i3 = i % i2;
                float[] fArr = this.x;
                int i4 = i - 1;
                float f2 = fArr[i4];
                float[] fArr2 = this.nx;
                float f3 = f2 + (fArr2[i4] * f);
                float[] fArr3 = this.y;
                float f4 = fArr3[i4];
                float[] fArr4 = this.ny;
                float f5 = f4 + (fArr4[i4] * f);
                float f6 = (fArr[i3] + (fArr2[i3] * f)) - f3;
                float f7 = (fArr3[i3] + (fArr4[i3] * f)) - f5;
                float[] fArr5 = this.len;
                fArr5[i] = fArr5[i4] + ((float) Math.sqrt((f6 * f6) + (f7 * f7)));
                i++;
            } else {
                this.total = this.len[i2];
                return;
            }
        }
    }

    private void add(float f, float f2, float f3, float f4, float f5) {
        int i = this.count;
        float[] fArr = this.x;
        if (i >= fArr.length) {
            return;
        }
        fArr[i] = f;
        this.y[i] = f2;
        this.nx[i] = f3;
        this.ny[i] = f4;
        this.at[i] = Style.clampF(f5, 0.0f, 1.0f);
        this.count++;
    }

    private void room(int i) {
        int i2 = i * 2;
        int i3 = i2 + 512;
        if (this.x.length < i3) {
            this.x = new float[i3];
            this.y = new float[i3];
            this.nx = new float[i3];
            this.ny = new float[i3];
            this.at = new float[i3];
            this.len = new float[i2 + 513];
        }
        if (this.sideLen.length < i) {
            this.sideX = new float[i];
            this.sideY = new float[i];
            this.sideNX = new float[i];
            this.sideNY = new float[i];
            this.sideLen = new float[i];
            this.sideNode = new int[i];
            this.cutIn = new float[i];
            this.cutOut = new float[i];
        }
    }
}

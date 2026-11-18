package org.telegram.ui.Components.devgram.export;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

public final class ProfileGifPalette {
        public static int component5(int i, int i2) {
        int i3;
        if (i2 == 0) {
            i3 = i >>> 10;
        } else {
            if (i2 != 1) {
                return i & 31;
            }
            i3 = i >>> 5;
        }
        return i3 & 31;
    }

    static final class Result {
        final byte[] indexed;
        final byte[] palette;

        Result(byte[] bArr, byte[] bArr2) {
            this.indexed = bArr;
            this.palette = bArr2;
        }
    }

    private ProfileGifPalette() {
    }

    static Result quantize(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] iArr = new int[width * height];
        bitmap.getPixels(iArr, 0, width, 0, 0, width, height);
        return quantize(iArr);
    }

    static Result quantize(int[] iArr) {
        int iSelectBox;
        int[] iArr2 = new int[32768];
        int length = iArr.length;
        int[] iArr3 = new int[length];
        int i = 0;
        for (int i2 = 0; i2 < iArr.length; i2++) {
            int iOpaqueOnBlack = opaqueOnBlack(iArr[i2]);
            int i3 = ((iOpaqueOnBlack >>> 3) & 31) | (((iOpaqueOnBlack >>> 19) & 31) << 10) | (((iOpaqueOnBlack >>> 11) & 31) << 5);
            iArr3[i2] = i3;
            iArr2[i3] = iArr2[i3] + 1;
        }
        int i4 = 0;
        for (int i5 = 0; i5 < 32768; i5++) {
            if (iArr2[i5] > 0) {
                i4++;
            }
        }
        int[] iArr4 = new int[i4];
        int i6 = 0;
        for (int i7 = 0; i7 < 32768; i7++) {
            if (iArr2[i7] > 0) {
                iArr4[i6] = i7;
                i6++;
            }
        }
        ArrayList arrayList = new ArrayList(256);
        arrayList.add(new Box(iArr4, 0, i4, iArr2));
        while (arrayList.size() < 256 && (iSelectBox = selectBox(arrayList)) >= 0) {
            Box box = (Box) arrayList.remove(iSelectBox);
            Box boxSplit = box.split(iArr4, iArr2);
            arrayList.add(box);
            arrayList.add(boxSplit);
        }
        byte[] bArr = new byte[768];
        byte[] bArr2 = new byte[32768];
        int i8 = 0;
        while (i8 < arrayList.size()) {
            Box box2 = (Box) arrayList.get(i8);
            int i9 = box2.start;
            int iComponent8 = 0;
            int i10 = 0;
            int iComponent9 = 0;
            int iComponent10 = 0;
            while (i9 < box2.end) {
                int i11 = iArr4[i9];
                int i12 = iArr2[i11];
                iComponent8 += component8(i11, i) * i12;
                iComponent9 += component8(i11, 1) * i12;
                iComponent10 += component8(i11, 2) * i12;
                i10 += i12;
                bArr2[i11] = (byte) i8;
                i9++;
                i = 0;
            }
            int i13 = i8 * 3;
            bArr[i13] = (byte) (iComponent8 / Math.max(1, i10));
            bArr[i13 + 1] = (byte) (iComponent9 / Math.max(1, i10));
            bArr[i13 + 2] = (byte) (iComponent10 / Math.max(1, i10));
            i8++;
            i = 0;
        }
        if (!arrayList.isEmpty()) {
            int size = (arrayList.size() - 1) * 3;
            for (int size2 = arrayList.size(); size2 < 256; size2++) {
                int i14 = size2 * 3;
                bArr[i14] = bArr[size];
                bArr[i14 + 1] = bArr[size + 1];
                bArr[i14 + 2] = bArr[size + 2];
            }
        }
        byte[] bArr3 = new byte[length];
        for (int i15 = 0; i15 < length; i15++) {
            bArr3[i15] = bArr2[iArr3[i15]];
        }
        return new Result(bArr3, bArr);
    }

    private static int selectBox(List<Box> list) {
        long j = -1;
        int i = -1;
        for (int i2 = 0; i2 < list.size(); i2++) {
            Box box = list.get(i2);
            if (box.end - box.start >= 2 && box.range() != 0) {
                long jRange = ((long) box.range()) * ((long) box.range()) * ((long) box.total);
                if (jRange > j) {
                    i = i2;
                    j = jRange;
                }
            }
        }
        return i;
    }

    private static int opaqueOnBlack(int i) {
        int i2 = i >>> 24;
        if (i2 == 255) {
            return i;
        }
        return (((i & 255) * i2) / 255) | (-16777216) | (((((i >>> 16) & 255) * i2) / 255) << 16) | (((((i >>> 8) & 255) * i2) / 255) << 8);
    }

    private static int component8(int i, int i2) {
        int iComponent5 = component5(i, i2);
        return (iComponent5 >>> 2) | (iComponent5 << 3);
    }

    private static final class Box {
        int end;
        int maxB;
        int maxG;
        int maxR;
        int minB;
        int minG;
        int minR;
        int start;
        int total;

        Box(int[] iArr, int i, int i2, int[] iArr2) {
            this.start = i;
            this.end = i2;
            measure(iArr, iArr2);
        }

        int range() {
            return Math.max(this.maxR - this.minR, Math.max(this.maxG - this.minG, this.maxB - this.minB));
        }

        Box split(int[] iArr, int[] iArr2) {
            int i;
            int i2;
            int i3 = this.maxR;
            int i4 = this.minR;
            int i5 = i3 - i4;
            int i6 = this.maxG;
            int i7 = this.minG;
            int i8 = 0;
            if (i5 < i6 - i7 || i3 - i4 < this.maxB - this.minB) {
                i = i6 - i7 >= this.maxB - this.minB ? 1 : 2;
            } else {
                i = 0;
            }
            sortByChannel(iArr, this.start, this.end, i);
            int iMax = Math.max(1, this.total / 2);
            int i9 = this.start;
            int i10 = i9 + 1;
            while (true) {
                i2 = this.end;
                if (i9 >= i2 - 1) {
                    break;
                }
                i8 += iArr2[iArr[i9]];
                if (i8 >= iMax) {
                    i10 = i9 + 1;
                    break;
                }
                i9++;
            }
            Box box = new Box(iArr, i10, i2, iArr2);
            this.end = i10;
            measure(iArr, iArr2);
            return box;
        }

        private void measure(int[] iArr, int[] iArr2) {
            this.minB = 31;
            this.minG = 31;
            this.minR = 31;
            this.maxB = 0;
            this.maxG = 0;
            this.maxR = 0;
            this.total = 0;
            for (int i = this.start; i < this.end; i++) {
                int i2 = iArr[i];
                int iComponent5 = ProfileGifPalette.component5(i2, 0);
                int iComponent6 = ProfileGifPalette.component5(i2, 1);
                int iComponent7 = ProfileGifPalette.component5(i2, 2);
                this.minR = Math.min(this.minR, iComponent5);
                this.maxR = Math.max(this.maxR, iComponent5);
                this.minG = Math.min(this.minG, iComponent6);
                this.maxG = Math.max(this.maxG, iComponent6);
                this.minB = Math.min(this.minB, iComponent7);
                this.maxB = Math.max(this.maxB, iComponent7);
                this.total += iArr2[i2];
            }
        }

        private static void sortByChannel(int[] iArr, int i, int i2, int i3) {
            int i4;
            int[] iArr2 = new int[32];
            int i5 = i;
            while (true) {
                if (i5 >= i2) {
                    break;
                }
                int iComponent5 = ProfileGifPalette.component5(iArr[i5], i3);
                iArr2[iComponent5] = iArr2[iComponent5] + 1;
                i5++;
            }
            int[] iArr3 = new int[32];
            for (i4 = 1; i4 < 32; i4++) {
                int i6 = i4 - 1;
                iArr3[i4] = iArr3[i6] + iArr2[i6];
            }
            int i7 = i2 - i;
            int[] iArr4 = new int[i7];
            for (int i8 = i; i8 < i2; i8++) {
                int iComponent6 = ProfileGifPalette.component5(iArr[i8], i3);
                int i9 = iArr3[iComponent6];
                iArr3[iComponent6] = i9 + 1;
                iArr4[i9] = iArr[i8];
            }
            System.arraycopy(iArr4, 0, iArr, i, i7);
        }
    }
}

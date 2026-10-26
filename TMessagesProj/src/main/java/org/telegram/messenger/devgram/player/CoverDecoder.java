package org.telegram.messenger.devgram.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
public abstract class CoverDecoder {
    public static Bitmap decode(byte[] bArr) {
        Bitmap bitmapCreateScaledBitmap = null;
        if (bArr != null && bArr.length != 0) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bArr, 0, bArr.length, options);
            int iMax = Math.max(options.outWidth, options.outHeight);
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            while (true) {
                iMax /= 2;
                if (iMax < 1280) {
                    break;
                }
                options.inSampleSize *= 2;
            }
            Bitmap bitmapDecodeByteArray = BitmapFactory.decodeByteArray(bArr, 0, bArr.length, options);
            if (bitmapDecodeByteArray == null) {
                return null;
            }
            int iMax2 = Math.max(bitmapDecodeByteArray.getWidth(), bitmapDecodeByteArray.getHeight());
            if (iMax2 <= 1600) {
                return bitmapDecodeByteArray;
            }
            float f = 1280.0f / iMax2;
            bitmapCreateScaledBitmap = Bitmap.createScaledBitmap(bitmapDecodeByteArray, Math.max(1, Math.round(bitmapDecodeByteArray.getWidth() * f)), Math.max(1, Math.round(bitmapDecodeByteArray.getHeight() * f)), true);
            if (bitmapCreateScaledBitmap != bitmapDecodeByteArray) {
                bitmapDecodeByteArray.recycle();
            }
        }
        return bitmapCreateScaledBitmap;
    }
}

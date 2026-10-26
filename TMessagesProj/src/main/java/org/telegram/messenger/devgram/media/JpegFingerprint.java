package org.telegram.messenger.devgram.media;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.telegram.messenger.FileLog;
public class JpegFingerprint {
    public int components;
    public boolean exif;
    public boolean frameBeforeQuantization;
    public int height;
    public int horizontalSampling;
    public int huffmanTables;
    public String iccDescription;
    public int iccLength;
    public int iccVersion;
    public int iccYear;
    public int jfifDensityX;
    public int jfifDensityY;
    public boolean jfifThumbnail;
    public boolean progressive;
    public boolean quantizationTable;
    public boolean restartInterval;
    public int verticalSampling;
    public int width;
    public boolean xmp;
    private static final int[] ZIGZAG = {0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28, 35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63};
    private static final int[] STANDARD_LUMA = {16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99};
    public int jfifVersion = -1;
    public int jfifUnits = -1;
    public int quality = -1;
    public String markerOrder = "";

    public static JpegFingerprint parse(String str) {
        File file = new File(str);
        if (!file.exists()) {
            return null;
        }
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file), 16384)) {
            if (in.read() != 0xFF || in.read() != 0xD8) {
                return null;
            }
            JpegFingerprint fp = new JpegFingerprint();
            StringBuilder order = new StringBuilder();
            int read = 2;
            while (read < 262144) {
                if (in.read() != 0xFF) {
                    break;
                }
                int marker = in.read();
                while (marker == 0xFF) {
                    marker = in.read();
                    read++;
                }
                read += 2;
                if (marker < 0 || marker == 0xD9 || marker == 0xDA) {
                    break;
                }
                if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                    continue;
                }
                int hi = in.read(), lo = in.read();
                if (hi < 0 || lo < 0) {
                    break;
                }
                int length = ((hi << 8) | lo) - 2;
                read += 2;
                if (length < 0 || read + length > 262144) {
                    break;
                }
                byte[] data = new byte[length];
                int got = 0;
                while (got < length) {
                    int n = in.read(data, got, length - got);
                    if (n <= 0) {
                        break;
                    }
                    got += n;
                }
                if (got < length) {
                    break;
                }
                read += length;
                if (order.length() > 0) {
                    order.append(',');
                }
                order.append(Integer.toHexString(marker));
                fp.readSegment(marker, data);
            }
            fp.markerOrder = order.toString();
            return fp;
        } catch (IOException e) {
            FileLog.e(e);
            return null;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    private void readSegment(int i, byte[] bArr) {
        int i2 = 0;
        boolean z = false;
        if (i == 224) {
            if (bArr.length < 14 || !startsWith(bArr, "JFIF")) {
                return;
            }
            this.jfifVersion = ((bArr[5] & 0xFF) << 8) | (bArr[6] & 0xFF);
            this.jfifUnits = bArr[7] & 0xFF;
            this.jfifDensityX = ((bArr[8] & 0xFF) << 8) | (bArr[9] & 0xFF);
            this.jfifDensityY = ((bArr[10] & 0xFF) << 8) | (bArr[11] & 0xFF);
            this.jfifThumbnail = (bArr[12] == 0 && bArr[13] == 0) ? false : true;
            return;
        }
        if (i == 225) {
            this.exif = this.exif || startsWith(bArr, "Exif");
            this.xmp = this.xmp || startsWith(bArr, "http://ns.adobe.com/xap/1.0/");
            return;
        }
        if (i == 226) {
            if (this.iccLength == 0 && bArr.length >= 50 && startsWith(bArr, "ICC_PROFILE")) {
                this.iccLength = ((bArr[14] & 0xFF) << 24) | ((bArr[15] & 0xFF) << 16) | ((bArr[16] & 0xFF) << 8) | (bArr[17] & 0xFF);
                this.iccVersion = ((bArr[22] & 0xFF) << 8) | (bArr[23] & 0xFF);
                this.iccYear = ((bArr[38] & 0xFF) << 8) | (bArr[39] & 0xFF);
                this.iccDescription = readIccDescription(bArr, 14);
                return;
            }
            return;
        }
        if (i == 219) {
            while (i2 < bArr.length) {
                byte b2 = bArr[i2];
                int i3 = (b2 & 240) >> 4;
                int z2 = b2 & 15;
                int i4 = i2 + 1;
                int i5 = (i3 == 0 ? 64 : 128) + i4;
                if (i5 > bArr.length) {
                    return;
                }
                if (z2 == 0 && i3 == 0 && !this.quantizationTable) {
                    this.quantizationTable = true;
                    this.quality = standardQuality(bArr, i4);
                }
                i2 = i5;
            }
            return;
        }
        if (i == 196) {
            int i6 = 0;
            while (i6 + 17 <= bArr.length) {
                int i7 = 0;
                for (int i8 = 0; i8 < 16; i8++) {
                    i7 += bArr[i6 + 1 + i8] & 0xFF;
                }
                this.huffmanTables++;
                i6 += i7 + 17;
            }
            return;
        }
        if (i == 221) {
            if (bArr.length >= 2 && (((bArr[0] & 0xFF) << 8) | (bArr[1] & 0xFF)) > 0) {
                z = true;
            }
            this.restartInterval = z;
            return;
        }
        if (i < 192 || i > 194 || bArr.length < 9) {
            return;
        }
        this.progressive = i == 194;
        this.frameBeforeQuantization = !this.quantizationTable;
        this.height = ((bArr[1] & 0xFF) << 8) | (bArr[2] & 0xFF);
        this.width = ((bArr[3] & 0xFF) << 8) | (bArr[4] & 0xFF);
        this.components = bArr[5] & 0xFF;
        byte b3 = bArr[7];
        this.horizontalSampling = (b3 & 240) >> 4;
        this.verticalSampling = b3 & 15;
    }

    private static int readInt(byte[] bArr, int i) {
        return (bArr[i + 3] & 0xFF) | ((bArr[i] & 0xFF) << 24) | ((bArr[i + 1] & 0xFF) << 16) | ((bArr[i + 2] & 0xFF) << 8);
    }

    private static String readIccDescription(byte[] bArr, int i) {
        int i2;
        try {
            int i3 = readInt(bArr, i + 128);
            for (int i4 = 0; i4 < Math.min(i3, 64); i4++) {
                int i5 = i + 132 + (i4 * 12);
                if (i5 + 12 > bArr.length) {
                    break;
                }
                if (bArr[i5] == 100 && bArr[i5 + 1] == 101 && bArr[i5 + 2] == 115 && bArr[i5 + 3] == 99) {
                    int i6 = readInt(bArr, i5 + 4) + i;
                    int i7 = readInt(bArr, i5 + 8);
                    if (i6 < i || i7 < 12 || (i2 = i6 + i7) > bArr.length) {
                        break;
                    }
                    byte b2 = bArr[i6];
                    if (b2 != 100) {
                        if (b2 != 109) {
                            break;
                        }
                        int i8 = readInt(bArr, i6 + 20);
                        int i9 = readInt(bArr, i6 + 24) + i6;
                        if (i8 <= 0 || i9 < i6 || i9 + i8 > i2) {
                            break;
                        }
                        return new String(bArr, i9, i8, StandardCharsets.UTF_16BE);
                    }
                    int iMin = Math.min(readInt(bArr, i6 + 8) - 1, i7 - 12);
                    if (iMin > 0) {
                        return new String(bArr, i6 + 12, iMin, StandardCharsets.US_ASCII);
                    }
                    return null;
                }
            }
        } catch (Exception unused) {
        }
        return null;
    }

    /** Качество, при котором стандартная таблица квантования яркости совпадает с файлом, или -1. */
    private static int standardQuality(byte[] bArr, int i) {
        for (int q = 100; q >= 1; q--) {
            int scale = q < 50 ? 5000 / q : 200 - q * 2;
            boolean match = true;
            for (int k = 0; k < 64; k++) {
                int v = (STANDARD_LUMA[ZIGZAG[k]] * scale + 50) / 100;
                v = Math.max(1, Math.min(255, v));
                if (v != (bArr[i + k] & 0xFF)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return q;
            }
        }
        return -1;
    }

    private static boolean startsWith(byte[] bArr, String str) {
        if (bArr.length <= str.length()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (bArr[i] != ((byte) str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public String describe() {
        String str;
        StringBuilder sb = new StringBuilder();
        if (this.jfifUnits >= 0) {
            sb.append(String.format(Locale.US, "jfif=%d.%02d/u%d/%dx%d", Integer.valueOf(this.jfifVersion >> 8), Integer.valueOf(this.jfifVersion & 255), Integer.valueOf(this.jfifUnits), Integer.valueOf(this.jfifDensityX), Integer.valueOf(this.jfifDensityY)));
        } else {
            sb.append("jfif=none");
        }
        if (this.jfifThumbnail) {
            sb.append("/thumb");
        }
        int i = this.iccLength;
        if (i > 0) {
            Locale locale = Locale.US;
            Integer numValueOf = Integer.valueOf(i);
            Integer numValueOf2 = Integer.valueOf(this.iccVersion);
            Integer numValueOf3 = Integer.valueOf(this.iccYear);
            String str2 = this.iccDescription;
            if (str2 == null) {
                str2 = "?";
            }
            sb.append(String.format(locale, " icc=%d/%04x/%d/%s", numValueOf, numValueOf2, numValueOf3, str2));
        } else {
            sb.append(" icc=none");
        }
        int i2 = this.quality;
        if (i2 > 0) {
            str = String.format(Locale.US, " dqt=q%d", Integer.valueOf(i2));
        } else {
            str = this.quantizationTable ? " dqt=custom" : " dqt=none";
        }
        sb.append(str);
        Locale locale2 = Locale.US;
        sb.append(String.format(locale2, " sof=%s/%dc/%dx%d/%dx%d", this.progressive ? "prog" : "base", Integer.valueOf(this.components), Integer.valueOf(this.horizontalSampling), Integer.valueOf(this.verticalSampling), Integer.valueOf(this.width), Integer.valueOf(this.height)));
        sb.append(String.format(locale2, " dht=%d", Integer.valueOf(this.huffmanTables)));
        if (this.exif) {
            sb.append(" exif");
        }
        if (this.xmp) {
            sb.append(" xmp");
        }
        if (this.restartInterval) {
            sb.append(" dri");
        }
        sb.append(" order=");
        sb.append(this.markerOrder);
        return sb.toString();
    }
}

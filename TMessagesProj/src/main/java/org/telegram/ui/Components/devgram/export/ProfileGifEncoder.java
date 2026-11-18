package org.telegram.ui.Components.devgram.export;

import android.graphics.Bitmap;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

public final class ProfileGifEncoder implements Closeable {
    private static final int COLOR_BITS = 8;
    static final int DEFAULT_DELAY_MS = 50;
    private boolean finished;
    private final int height;
    private final OutputStream output;
    private final int width;

    public ProfileGifEncoder(File file, int i, int i2) throws IOException {
        this.width = i;
        this.height = i2;
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file), 65536);
        this.output = bufferedOutputStream;
        writeAscii("GIF89a");
        writeShort(i);
        writeShort(i2);
        bufferedOutputStream.write(247);
        bufferedOutputStream.write(0);
        bufferedOutputStream.write(0);
        writePalette();
        writeLoopExtension();
    }

    public void addFrame(Bitmap bitmap, int i) throws IOException {
        if (this.finished || bitmap == null || bitmap.getWidth() != this.width || bitmap.getHeight() != this.height) {
            throw new IllegalArgumentException("invalid GIF frame");
        }
        ProfileGifPalette.Result resultQuantize = ProfileGifPalette.quantize(bitmap);
        writeFrame(resultQuantize.indexed, resultQuantize.palette, i);
    }

    void addIndexedFrame(byte[] bArr, int i) throws IOException {
        writeFrame(bArr, null, i);
    }

    void addIndexedFrame(byte[] bArr, byte[] bArr2, int i) throws IOException {
        writeFrame(bArr, bArr2, i);
    }

    private void writeFrame(byte[] bArr, byte[] bArr2, int i) throws IOException {
        if (this.finished || bArr == null || bArr.length != this.width * this.height) {
            throw new IllegalArgumentException("invalid indexed GIF frame");
        }
        if (bArr2 != null && bArr2.length != 768) {
            throw new IllegalArgumentException("invalid local GIF palette");
        }
        this.output.write(33);
        this.output.write(249);
        this.output.write(4);
        this.output.write(4);
        writeShort(Math.max(2, Math.round(i / 10.0f)));
        this.output.write(0);
        this.output.write(0);
        this.output.write(44);
        writeShort(0);
        writeShort(0);
        writeShort(this.width);
        writeShort(this.height);
        this.output.write(bArr2 != null ? 135 : 0);
        if (bArr2 != null) {
            this.output.write(bArr2);
        }
        writeLzw(bArr);
    }

    public void finish() throws IOException {
        if (this.finished) {
            return;
        }
        this.finished = true;
        this.output.write(59);
        this.output.flush();
        this.output.close();
    }

    @Override // java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
        if (this.finished) {
            return;
        }
        this.finished = true;
        this.output.close();
    }

    static int targetWidth(int i) {
        return Math.max(1, Math.min(480, i));
    }

    static int targetHeight(int i, int i2) {
        return Math.max(1, Math.round(i2 * (targetWidth(i) / (float) i)));
    }

    private void writePalette() throws IOException {
        for (int i = 0; i < 256; i++) {
            this.output.write(((((i >>> 5) & 7) * 255) + 3) / 7);
            this.output.write(((((i >>> 2) & 7) * 255) + 3) / 7);
            this.output.write((((i & 3) * 255) + 1) / 3);
        }
    }

    private void writeLoopExtension() throws IOException {
        this.output.write(33);
        this.output.write(255);
        this.output.write(11);
        writeAscii("NETSCAPE2.0");
        this.output.write(3);
        this.output.write(1);
        writeShort(0);
        this.output.write(0);
    }

    private void writeLzw(byte[] bArr) throws IOException {
        this.output.write(8);
        BlockWriter blockWriter = new BlockWriter(this.output);
        BitWriter bitWriter = new BitWriter(blockWriter);
        HashMap map = new HashMap(4096);
        bitWriter.write(256, 9);
        int iIntValue = bArr[0] & 255;
        int i = 9;
        int i2 = 258;
        for (int i3 = 1; i3 < bArr.length; i3++) {
            int i4 = bArr[i3] & 255;
            int i5 = (iIntValue << 8) | i4;
            Integer num = (Integer) map.get(Integer.valueOf(i5));
            if (num != null) {
                iIntValue = num.intValue();
            } else {
                bitWriter.write(iIntValue, i);
                if (i2 < 4096) {
                    int i6 = i2 + 1;
                    map.put(Integer.valueOf(i5), Integer.valueOf(i2));
                    if (i6 == (1 << i) + 1 && i < 12) {
                        i++;
                    }
                    i2 = i6;
                } else {
                    bitWriter.write(256, i);
                    map.clear();
                    i = 9;
                    i2 = 258;
                }
                iIntValue = i4;
            }
        }
        bitWriter.write(iIntValue, i);
        bitWriter.write(257, i);
        bitWriter.flush();
        blockWriter.finish();
    }

    private void writeShort(int i) throws IOException {
        this.output.write(i & 255);
        this.output.write((i >>> 8) & 255);
    }

    private void writeAscii(String str) throws IOException {
        for (int i = 0; i < str.length(); i++) {
            this.output.write(str.charAt(i));
        }
    }

    private static final class BitWriter {
        private int bits;
        private int current;
        private final BlockWriter output;

        BitWriter(BlockWriter blockWriter) {
            this.output = blockWriter;
        }

        void write(int i, int i2) throws IOException {
            int i3 = this.current;
            int i4 = this.bits;
            this.current = (i << i4) | i3;
            this.bits = i4 + i2;
            while (this.bits >= 8) {
                this.output.write(this.current & 255);
                this.current >>>= 8;
                this.bits -= 8;
            }
        }

        void flush() throws IOException {
            if (this.bits > 0) {
                this.output.write(this.current & 255);
            }
            this.current = 0;
            this.bits = 0;
        }
    }

    private static final class BlockWriter {
        private final byte[] block = new byte[255];
        private final OutputStream output;
        private int size;

        BlockWriter(OutputStream outputStream) {
            this.output = outputStream;
        }

        void write(int i) throws IOException {
            byte[] bArr = this.block;
            int i2 = this.size;
            int i3 = i2 + 1;
            this.size = i3;
            bArr[i2] = (byte) i;
            if (i3 == bArr.length) {
                flush();
            }
        }

        public void finish() throws IOException {
            flush();
            this.output.write(0);
        }

        private void flush() throws IOException {
            int i = this.size;
            if (i == 0) {
                return;
            }
            this.output.write(i);
            this.output.write(this.block, 0, this.size);
            this.size = 0;
        }
    }
}

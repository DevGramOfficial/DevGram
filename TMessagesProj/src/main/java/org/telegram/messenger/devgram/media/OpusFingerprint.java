package org.telegram.messenger.devgram.media;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
public class OpusFingerprint {
    public final String vendor;

    private OpusFingerprint(String str) {
        this.vendor = str;
    }

    /** vendor из заголовка OpusTags (второй пакет Ogg-потока), или null. */
    public static OpusFingerprint parse(String str) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(str)))) {
            java.io.ByteArrayOutputStream packet = new java.io.ByteArrayOutputStream();
            byte[] lacing = new byte[255];
            int packetIndex = 0;
            int total = 0;
            while (total + 27 <= 262144) {
                if (in.readInt() != 0x4F676753 || in.readUnsignedByte() != 0) {
                    return null;
                }
                in.readUnsignedByte(); // флаги страницы
                in.readLong();         // позиция
                in.readInt();          // серийный номер
                in.readInt();          // номер страницы
                in.readInt();          // контрольная сумма
                int segments = in.readUnsignedByte();
                total += 27 + segments;
                in.readFully(lacing, 0, segments);
                for (int i = 0; i < segments; i++) {
                    int len = lacing[i] & 0xFF;
                    total += len;
                    if (total > 262144 || packet.size() + len > 65536) {
                        return null;
                    }
                    byte[] buf = new byte[len];
                    in.readFully(buf);
                    packet.write(buf);
                    if (len == 255) {
                        continue;
                    }
                    byte[] p = packet.toByteArray();
                    packet.reset();
                    if (packetIndex++ == 0) {
                        if (p.length < 19 || !"OpusHead".equals(new String(p, 0, 8, StandardCharsets.US_ASCII))) {
                            return null;
                        }
                        continue;
                    }
                    if (p.length < 16 || !"OpusTags".equals(new String(p, 0, 8, StandardCharsets.US_ASCII))) {
                        return null;
                    }
                    ByteBuffer bb = ByteBuffer.wrap(p, 8, p.length - 8).order(ByteOrder.LITTLE_ENDIAN);
                    int vendorLength = bb.getInt();
                    if (vendorLength < 0 || vendorLength > bb.remaining()) {
                        return null;
                    }
                    return new OpusFingerprint(new String(p, bb.position(), vendorLength, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException ignore) {
        }
        return null;
    }
}

package org.telegram.messenger.devgram.media;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
public class Mp4Fingerprint {
    public String audioHandler;
    public String audioLanguage;
    private int boxes;
    public final ArrayList<String> compatibleBrands = new ArrayList<>();
    public String encoder;
    public boolean fragmented;
    public String majorBrand;
    public long minorVersion;
    private boolean movie;
    public String videoHandler;
    public String videoLanguage;

    public static class Track {
        String language;
        boolean media;
        String name;
        String type;

        private Track() {
        }
    }

    public static Mp4Fingerprint parse(String str) {
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(str, "r");
            try {
                Mp4Fingerprint mp4Fingerprint = new Mp4Fingerprint();
                mp4Fingerprint.readBoxes(randomAccessFile, randomAccessFile.length(), "", null);
                if (mp4Fingerprint.majorBrand == null || !mp4Fingerprint.movie || mp4Fingerprint.videoHandler == null || mp4Fingerprint.audioHandler == null || mp4Fingerprint.videoLanguage == null || mp4Fingerprint.audioLanguage == null) {
                    mp4Fingerprint = null;
                }
                randomAccessFile.close();
                return mp4Fingerprint;
            } catch (Throwable th) {
                try {
                    randomAccessFile.close();
                    throw th;
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                    throw th;
                }
            }
        } catch (IOException unused) {
            return null;
        }
    }

    private void readBoxes(RandomAccessFile f, long end, String parent, Track track) throws IOException {
        while (f.getFilePointer() < end) {
            long start = f.getFilePointer();
            if (++boxes > 4096) {
                throw new IOException();
            }
            long left = end - start;
            if (left < 8) {
                throw new IOException();
            }
            long size = f.readInt() & 0xFFFFFFFFL;
            byte[] tag = new byte[4];
            f.readFully(tag);
            String type = new String(tag, StandardCharsets.ISO_8859_1);
            int header = 8;
            if (size == 1) {
                if (left < 16) {
                    throw new IOException();
                }
                size = f.readLong();
                header = 16;
            } else if (size == 0) {
                size = left;
            }
            if (size < header || size > left) {
                throw new IOException();
            }
            long next = start + size;
            long body = size - header;
            if (type.equals("ftyp") && parent.isEmpty()) {
                if (majorBrand != null || body < 8 || body > 256 || body % 4 != 0) {
                    throw new IOException();
                }
                f.readFully(tag);
                majorBrand = new String(tag, StandardCharsets.ISO_8859_1);
                minorVersion = f.readInt() & 0xFFFFFFFFL;
                while (f.getFilePointer() < next) {
                    f.readFully(tag);
                    compatibleBrands.add(new String(tag, StandardCharsets.ISO_8859_1));
                }
            } else if (type.equals("moov") && parent.isEmpty()) {
                if (movie) {
                    throw new IOException();
                }
                movie = true;
                readBoxes(f, next, type, null);
            } else if (type.equals("trak") && parent.equals("moov")) {
                Track t = new Track();
                readBoxes(f, next, type, t);
                if ("vide".equals(t.type)) {
                    if (videoHandler != null) {
                        throw new IOException();
                    }
                    videoHandler = t.name;
                    videoLanguage = t.language;
                } else if ("soun".equals(t.type)) {
                    if (audioHandler != null) {
                        throw new IOException();
                    }
                    audioHandler = t.name;
                    audioLanguage = t.language;
                }
            } else if (type.equals("mdia") && parent.equals("trak")) {
                if (track.media) {
                    throw new IOException();
                }
                track.media = true;
                readBoxes(f, next, type, track);
            } else if (type.equals("hdlr") && parent.equals("mdia")) {
                if (track.type != null || body < 24 || body > 256 || f.readInt() != 0) {
                    throw new IOException();
                }
                f.readInt();
                f.readFully(tag);
                track.type = new String(tag, StandardCharsets.ISO_8859_1);
                f.skipBytes(12);
                int n = (int) body - 24;
                byte[] name = new byte[n];
                f.readFully(name);
                int len = 0;
                while (len < n && name[len] != 0) {
                    len++;
                }
                track.name = new String(name, 0, len, StandardCharsets.UTF_8);
            } else if (type.equals("mdhd") && parent.equals("mdia")) {
                if (track.language != null || body < 24) {
                    throw new IOException();
                }
                int versionFlags = f.readInt();
                if ((versionFlags != 0 && versionFlags != 0x01000000) || body < (versionFlags != 0 ? 36 : 24)) {
                    throw new IOException();
                }
                f.skipBytes(versionFlags != 0 ? 28 : 16);
                int lang = f.readUnsignedShort();
                track.language = new String(new char[]{(char) (((lang >> 10) & 31) + 96), (char) (((lang >> 5) & 31) + 96), (char) ((lang & 31) + 96)});
            } else if (type.equals("udta") && parent.equals("moov")) {
                readBoxes(f, next, type, null);
            } else if (type.equals("meta") && (parent.equals("udta") || parent.equals("moov"))) {
                if (body < 4 || f.readInt() != 0) {
                    throw new IOException();
                }
                readBoxes(f, next, type, null);
            } else if ((type.equals("ilst") && parent.equals("meta")) || (type.equals("\u00a9too") && parent.equals("ilst"))) {
                readBoxes(f, next, type, null);
            } else if (type.equals("data") && parent.equals("\u00a9too")) {
                if (encoder != null || body < 8 || body > 1024 || f.readInt() != 1) {
                    throw new IOException();
                }
                f.readInt();
                byte[] value = new byte[(int) body - 8];
                f.readFully(value);
                encoder = new String(value, StandardCharsets.UTF_8);
            } else if (type.equals("mvex") || type.equals("moof")) {
                fragmented = true;
            }
            f.seek(next);
        }
    }

    public String describe() {
        return "ftyp=" + this.majorBrand + "/" + this.minorVersion + "/" + String.join(",", this.compatibleBrands) + " video=" + this.videoHandler + "/" + this.videoLanguage + " audio=" + this.audioHandler + "/" + this.audioLanguage + " encoder=" + this.encoder + " fragmented=" + this.fragmented;
    }
}

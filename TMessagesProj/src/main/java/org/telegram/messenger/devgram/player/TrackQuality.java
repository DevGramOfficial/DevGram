package org.telegram.messenger.devgram.player;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Locale;
import kotlin.UByte;
import kotlin.jvm.internal.ByteCompanionObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
public abstract class TrackQuality {
    private static final HashMap<Long, Info> cache = new HashMap<>();

    public static final class Info {
        public int bitrateKbps;
        public int bitsPerSample;
        public String format;
        public boolean probed;
        public int sampleRate;

        public String formatBitrate() {
            int i = this.bitrateKbps;
            if (i > 0) {
                return LocaleController.formatString(R.string.PlayerQualityKbps, Integer.valueOf(i));
            }
            return null;
        }

        public String formatSampleRate() {
            int i = this.sampleRate;
            if (i <= 0) {
                return null;
            }
            float f = i / 1000.0f;
            int i2 = (int) f;
            return LocaleController.formatString(R.string.PlayerQualityKhz, f == ((float) i2) ? String.valueOf(i2) : String.format(Locale.US, "%.1f", Float.valueOf(f)));
        }

        public String formatBitDepth() {
            int i = this.bitsPerSample;
            if (i > 0) {
                return LocaleController.formatString(R.string.PlayerQualityBits, Integer.valueOf(i));
            }
            return null;
        }
    }

    public static Info get(MessageObject messageObject) {
        TLRPC.Document document = messageObject != null ? messageObject.getDocument() : null;
        if (document == null) {
            return null;
        }
        Info info = cache.get(Long.valueOf(document.id));
        return info != null ? info : describe(document, messageObject.getDuration(), null);
    }

    public static void load(MessageObject messageObject, final Utilities.Callback<Info> callback) {
        final File localFile;
        final TLRPC.Document document = messageObject != null ? messageObject.getDocument() : null;
        if (document == null || cache.containsKey(Long.valueOf(document.id)) || (localFile = PlayerActions.getLocalFile(messageObject)) == null || !localFile.exists()) {
            return;
        }
        final double duration = messageObject.getDuration();
        Utilities.globalQueue.postRunnable(() -> TrackQuality.$r8$lambda$8fFWQ2ykKnM4TqvGVTMRJuKmh3Y(localFile, document, duration, callback));
    }

    public static /* synthetic */ void $r8$lambda$8fFWQ2ykKnM4TqvGVTMRJuKmh3Y(File file, final TLRPC.Document document, final double d, final Utilities.Callback callback) {
        final Probe probe = probe(file);
        AndroidUtilities.runOnUIThread(() -> TrackQuality.$r8$lambda$j1Tnmng1YqD1A9xbJq1i80jz9yc(document, d, probe, callback));
    }

    public static /* synthetic */ void $r8$lambda$j1Tnmng1YqD1A9xbJq1i80jz9yc(TLRPC.Document document, double d, Probe probe, Utilities.Callback callback) {
        Info infoDescribe = describe(document, d, probe);
        cache.put(Long.valueOf(document.id), infoDescribe);
        callback.run(infoDescribe);
    }

    private static Info describe(TLRPC.Document document, double d, Probe probe) {
        String fromDocument;
        Info info = new Info();
        info.probed = probe != null;
        if (probe == null || (fromDocument = probe.format) == null) {
            fromDocument = formatFromDocument(document);
        }
        info.format = fromDocument;
        long j = probe != null ? probe.bitrate : 0L;
        if (j <= 0) {
            long j2 = document.size;
            if (j2 > 0 && d > 1.0d) {
                j = (long) ((j2 * 8) / d);
            }
        }
        info.bitrateKbps = (int) Math.round(j / 1000.0d);
        if (probe != null) {
            info.sampleRate = probe.sampleRate;
            info.bitsPerSample = probe.bitsPerSample;
        }
        return info;
    }

    private static String formatFromDocument(TLRPC.Document document) {
        String documentFileName = FileLoader.getDocumentFileName(document);
        int iLastIndexOf = documentFileName != null ? documentFileName.lastIndexOf(46) : -1;
        if (iLastIndexOf >= 0 && iLastIndexOf < documentFileName.length() - 1) {
            String strSubstring = documentFileName.substring(iLastIndexOf + 1);
            Locale locale = Locale.ROOT;
            String lowerCase = strSubstring.toLowerCase(locale);
            lowerCase.getClass();
            switch (lowerCase) {
                case "aac":
                case "m4a":
                    return "AAC";
                case "oga":
                case "ogg":
                    return "OGG";
                default:
                    if (lowerCase.length() <= 4) {
                        return lowerCase.toUpperCase(locale);
                    }
                    return null;
            }
        }
        return formatFromMime(document.mime_type);
    }

    private static String formatFromMime(String str) {
        if (str == null) {
            return null;
        }
        String lowerCase = str.toLowerCase(Locale.ROOT);
        lowerCase.getClass();
        switch (lowerCase) {
            case "audio/x-flac":
            case "audio/flac":
                return "FLAC";
            case "audio/vorbis":
                return "Vorbis";
            case "audio/x-m4a":
            case "audio/mp4a-latm":
            case "audio/aac":
            case "audio/mp4":
                return "AAC";
            case "audio/x-wav":
            case "audio/raw":
            case "audio/wav":
                return "WAV";
            case "audio/ac3":
                return "AC3";
            case "audio/mp3":
            case "audio/mpeg":
                return "MP3";
            case "audio/ogg":
                return "OGG";
            case "audio/alac":
                return "ALAC";
            case "audio/eac3":
                return "E-AC3";
            case "audio/opus":
                return "Opus";
            default:
                return null;
        }
    }

    private static Probe probe(File file) {
        Probe probe = new Probe();
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            try {
                mediaExtractor.setDataSource(file.getAbsolutePath());
                for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                    MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
                    String string = trackFormat.getString("mime");
                    if (string != null && string.startsWith("audio/")) {
                        probe.format = formatFromMime(string);
                        if (trackFormat.containsKey("sample-rate")) {
                            probe.sampleRate = trackFormat.getInteger("sample-rate");
                        }
                        if (!trackFormat.containsKey("bitrate")) {
                            break;
                        }
                        probe.bitrate = trackFormat.getInteger("bitrate");
                        break;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            mediaExtractor.release();
            if ("FLAC".equals(probe.format)) {
                probe.bitsPerSample = readFlacBitsPerSample(file);
            }
            return probe;
        } catch (Throwable th) {
            mediaExtractor.release();
            throw th;
        }
    }

    private static int readFlacBitsPerSample(File file) {
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            try {
                byte[] bArr = new byte[26];
                if (fileInputStream.read(bArr) == 26 && bArr[0] == 102 && bArr[1] == 76 && bArr[2] == 97 && bArr[3] == 67 && (bArr[4] & ByteCompanionObject.MAX_VALUE) == 0) {
                    int i = ((((bArr[21] & UByte.MAX_VALUE) | ((bArr[20] & UByte.MAX_VALUE) << 8)) >> 4) & 31) + 1;
                    fileInputStream.close();
                    return i;
                }
                fileInputStream.close();
                return 0;
            } catch (Throwable th) {
                try {
                    fileInputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Exception e) {
            FileLog.e(e);
            return 0;
        }
    }

    public static final class Probe {
        long bitrate;
        int bitsPerSample;
        String format;
        int sampleRate;

        private Probe() {
        }
    }
}

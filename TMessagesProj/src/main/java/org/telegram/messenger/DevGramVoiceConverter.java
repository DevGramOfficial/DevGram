package org.telegram.messenger;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

// DevGram: декодер голосового (ogg/opus и т.п.) в PCM для Vosk.
// Vosk принимает ТОЛЬКО 16-бит signed mono little-endian PCM, поэтому конвертер:
//   • читает реальный формат ВЫХОДА декодера (INFO_OUTPUT_FORMAT_CHANGED), а не контейнера;
//   • сводит любое число каналов в моно (усреднением);
//   • приводит PCM_FLOAT / 8-бит к 16-бит;
//   • отдаёт только целые сэмплы (чётное число байт), без рассинхрона между чтениями.
// Частоту дискретизации берём из выхода декодера (getOutputSampleRate) — её же передаём в Recognizer.
public abstract class DevGramVoiceConverter {

    // Частота дискретизации из контейнера (fallback, если реальный выход ещё неизвестен). По умолчанию 48000.
    public static int getSampleRate(String path) {
        MediaExtractor extractor = new MediaExtractor();
        int result = -1;
        try {
            extractor.setDataSource(path);
            int tracks = extractor.getTrackCount();
            for (int i = 0; i < tracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString("mime");
                if (mime != null && mime.startsWith("audio/") && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    result = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    break;
                }
            }
        } catch (IOException e) {
            FileLog.e(e);
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            try {
                extractor.release();
            } catch (Throwable ignore) {
            }
        }
        return result == -1 ? 48000 : result;
    }

    public static LazyPcmInputStream extractAndConvertToPcm(String path, boolean limitToOneMinute) throws IOException {
        return new LazyPcmInputStream(path, limitToOneMinute);
    }

    // Ленивый поток: декодирует аудио порциями через MediaCodec и на лету сводит в mono 16-бит PCM.
    public static class LazyPcmInputStream extends InputStream {
        private static final long ONE_MINUTE_US = 60_000_000L;

        private final MediaCodec codec;
        private final MediaExtractor extractor;
        private final boolean limitToOneMinute;
        private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        // Реальный формат выхода декодера (обновляется по INFO_OUTPUT_FORMAT_CHANGED).
        private int outputSampleRate;
        private int outputChannels = 1;
        private int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;

        private ByteBuffer currentOutputBuffer; // уже сведённый в mono 16-бит
        // EOS входа и выхода — разные состояния. После постановки input EOS MediaCodec ещё
        // может отдать несколько PCM-буферов, поэтому завершаться можно только по output EOS.
        private boolean inputEosQueued;
        private boolean outputEosReached;
        private long totalDecodedDurationUs;

        public LazyPcmInputStream(String path, boolean limitToOneMinute) throws IOException {
            this.limitToOneMinute = limitToOneMinute;
            extractor = new MediaExtractor();
            extractor.setDataSource(path);

            int audioTrack = -1;
            MediaFormat trackFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString("mime");
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    trackFormat = f;
                    break;
                }
            }
            if (audioTrack < 0 || trackFormat == null) {
                try { extractor.release(); } catch (Throwable ignore) {}
                throw new IOException("Not an audio file");
            }

            // Стартовые значения из контейнера (уточним по реальному выходу декодера).
            if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                outputSampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            }
            if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                outputChannels = Math.max(1, trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            }

            String mime = trackFormat.getString("mime");
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(trackFormat, null, null, 0);
            codec.start();
            extractor.selectTrack(audioTrack);

            // Прогреваем декодер до первого PCM-чанка, чтобы знать реальный формат выхода
            // (sample rate / каналы / encoding) ещё до создания Vosk-Recognizer.
            currentOutputBuffer = nextOutputBuffer();
        }

        // Реальная частота дискретизации PCM на выходе (для Vosk-Recognizer).
        public int getOutputSampleRate() {
            return outputSampleRate > 0 ? outputSampleRate : 48000;
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            if (read(one, 0, 1) == -1) {
                return -1;
            }
            return one[0] & 0xFF;
        }

        @Override
        public int read(byte[] out, int offset, int length) {
            if (currentOutputBuffer == null && outputEosReached) {
                return -1;
            }
            int written = 0;
            while (written < length) {
                if (currentOutputBuffer == null || !currentOutputBuffer.hasRemaining()) {
                    currentOutputBuffer = nextOutputBuffer();
                    if (currentOutputBuffer == null) {
                        break;
                    }
                }
                int chunk = Math.min(length - written, currentOutputBuffer.remaining());
                currentOutputBuffer.get(out, offset + written, chunk);
                written += chunk;
            }
            return written > 0 ? written : -1;
        }

        // Возвращает следующий блок PCM, УЖЕ сведённый в mono 16-бит little-endian.
        private ByteBuffer nextOutputBuffer() {
            while (true) {
                if (outputEosReached) {
                    return null;
                }
                if (!inputEosQueued) {
                    int inIndex = codec.dequeueInputBuffer(10_000L);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                        int size = inBuf != null ? extractor.readSampleData(inBuf, 0) : -1;
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEosQueued = true;
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outFormat = codec.getOutputFormat();
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        outputSampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        outputChannels = Math.max(1, outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                    }
                    if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                    continue;
                }
                if (outIndex >= 0) {
                    ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                    ByteBuffer mono16 = null;
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset);
                        outBuf.limit(bufferInfo.offset + bufferInfo.size);
                        mono16 = toMono16(outBuf, pcmEncoding, outputChannels);
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    totalDecodedDurationUs = bufferInfo.presentationTimeUs;
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEosReached = true;
                    }
                    if (limitToOneMinute && totalDecodedDurationUs >= ONE_MINUTE_US) {
                        outputEosReached = true;
                    }
                    if (mono16 != null && mono16.hasRemaining()) {
                        return mono16;
                    }
                    if (outputEosReached) {
                        return null;
                    }
                    // пустой блок — продолжаем декодировать
                }
            }
        }

        // Приводит произвольный PCM (16-бит/float/8-бит, N каналов) к mono 16-бит signed little-endian.
        private static ByteBuffer toMono16(ByteBuffer in, int encoding, int channels) {
            in.order(ByteOrder.LITTLE_ENDIAN);
            final int ch = Math.max(1, channels);
            final ByteBuffer out;
            switch (encoding) {
                case AudioFormat.ENCODING_PCM_FLOAT: {
                    int floats = in.remaining() / 4;
                    int frames = floats / ch;
                    out = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
                    for (int f = 0; f < frames; f++) {
                        float acc = 0f;
                        for (int c = 0; c < ch; c++) {
                            acc += in.getFloat();
                        }
                        float v = acc / ch;
                        if (v > 1f) v = 1f; else if (v < -1f) v = -1f;
                        out.putShort((short) Math.round(v * 32767f));
                    }
                    break;
                }
                case AudioFormat.ENCODING_PCM_8BIT: {
                    int frames = in.remaining() / ch;
                    out = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
                    for (int f = 0; f < frames; f++) {
                        int acc = 0;
                        for (int c = 0; c < ch; c++) {
                            acc += ((in.get() & 0xFF) - 128) << 8; // unsigned 8-бит -> signed 16-бит
                        }
                        out.putShort((short) (acc / ch));
                    }
                    break;
                }
                default: { // ENCODING_PCM_16BIT
                    ShortBuffer shorts = in.asShortBuffer();
                    int frames = shorts.remaining() / ch;
                    out = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
                    if (ch == 1) {
                        for (int f = 0; f < frames; f++) {
                            out.putShort(shorts.get());
                        }
                    } else {
                        for (int f = 0; f < frames; f++) {
                            int acc = 0;
                            for (int c = 0; c < ch; c++) {
                                acc += shorts.get();
                            }
                            out.putShort((short) (acc / ch));
                        }
                    }
                    break;
                }
            }
            out.flip();
            return out;
        }

        @Override
        public void close() throws IOException {
            try {
                codec.stop();
                codec.release();
            } catch (Throwable ignore) {
            }
            try {
                extractor.release();
            } catch (Throwable ignore) {
            }
            super.close();
        }
    }
}

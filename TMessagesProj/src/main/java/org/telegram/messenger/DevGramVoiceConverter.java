package org.telegram.messenger;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

// Декодер голосового (ogg/opus и т.п.) в PCM для Vosk. Поток намеренно повторяет
// тракт Extera: MediaCodec уже отдаёт подходящий PCM, поэтому не интерпретируем и
// не перекодируем его повторно. Некоторые vendor-кодеки сообщают неточный
// KEY_PCM_ENCODING, из-за чего дополнительная конвертация портила звук для Vosk.
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

        private ByteBuffer currentOutputBuffer;
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

            String mime = trackFormat.getString("mime");
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(trackFormat, null, null, 0);
            codec.start();
            extractor.selectTrack(audioTrack);
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
                    continue;
                }
                if (outIndex >= 0) {
                    ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                    ByteBuffer pcm = null;
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset);
                        outBuf.limit(bufferInfo.offset + bufferInfo.size);
                        // Копируем до releaseOutputBuffer: буфер MediaCodec после release
                        // больше не принадлежит приложению.
                        pcm = ByteBuffer.allocate(bufferInfo.size);
                        pcm.put(outBuf);
                        pcm.flip();
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    totalDecodedDurationUs = bufferInfo.presentationTimeUs;
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEosReached = true;
                    }
                    if (limitToOneMinute && totalDecodedDurationUs >= ONE_MINUTE_US) {
                        outputEosReached = true;
                    }
                    if (pcm != null && pcm.hasRemaining()) {
                        return pcm;
                    }
                    if (outputEosReached) {
                        return null;
                    }
                    // пустой блок — продолжаем декодировать
                }
            }
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

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;

// DevGram: видеосообщения без лимита в минуту. Кружок пишется непрерывно сколько нужно, а при
// отправке режется на минутные кружки (по ключевым кадрам — без перекодирования, энкодер кладёт
// ключевой кадр каждую секунду) и они уходят по очереди: следующий — только когда сервер принял
// предыдущий, чтобы у получателя они стояли строго по порядку.
public final class DevGramLongRound {

    public static final long SEGMENT_MS = 60_000;
    // предохранитель от забытой записи: дальше энкодер сам останавливает звук
    public static final long MAX_TOTAL_MS = 60L * 60_000;

    private static final long SEGMENT_US = SEGMENT_MS * 1000 - 100_000; // с запасом, чтобы кусок был < 60 с
    private static final long WAIT_SENT_MS = 5 * 60_000; // если сервер молчит — шлём следующий всё равно

    public interface Sender {
        // отправить один готовый кружок (вызывается на UI-потоке)
        void send(File part, VideoEditedInfo info);
    }

    private DevGramLongRound() {
    }

    public static boolean needsSplit(long durationMs) {
        return durationMs > SEGMENT_MS + 500;
    }

    // Нарезать [startMs, endMs) исходника (endMs <= 0 — до конца) на минутные куски и отправить
    // по очереди. Нарезка — в фоне, отправка — на UI-потоке.
    public static void splitAndSend(int account, long dialogId, File source, long startMs, long endMs, Sender sender) {
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<File> parts = split(source, startMs * 1000, endMs > 0 ? endMs * 1000 : Long.MAX_VALUE);
            AndroidUtilities.runOnUIThread(() -> {
                if (parts.isEmpty()) {
                    FileLog.e("DevGramLongRound: не удалось нарезать " + source);
                    return;
                }
                new Queue(account, dialogId, parts, sender).next();
            });
        });
    }

    // ---- нарезка ----

    @SuppressLint("WrongConstant")
    static ArrayList<File> split(File source, long startUs, long endUs) {
        ArrayList<File> result = new ArrayList<>();
        MediaExtractor probe = new MediaExtractor();
        try {
            probe.setDataSource(source.getAbsolutePath());
            int videoTrack = -1, audioTrack = -1;
            for (int i = 0; i < probe.getTrackCount(); i++) {
                String mime = probe.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (videoTrack < 0 && mime.startsWith("video/")) videoTrack = i;
                else if (audioTrack < 0 && mime.startsWith("audio/")) audioTrack = i;
            }
            if (videoTrack < 0) {
                return result;
            }
            // ключевые кадры видео и конец записи
            ArrayList<Long> keyframes = new ArrayList<>();
            long lastVideoUs = 0;
            probe.selectTrack(videoTrack);
            while (true) {
                long t = probe.getSampleTime();
                if (t < 0) break;
                if ((probe.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) keyframes.add(t);
                lastVideoUs = Math.max(lastVideoUs, t);
                if (!probe.advance()) break;
            }
            long end = Math.min(endUs, lastVideoUs + 1);
            // границы кусков — на ключевых кадрах, каждый кусок не длиннее минуты
            ArrayList<Long> cuts = new ArrayList<>();
            long from = keyframeAtOrBefore(keyframes, Math.max(0, startUs));
            cuts.add(from);
            while (end - from > SEGMENT_US) {
                long next = keyframeAtOrBefore(keyframes, from + SEGMENT_US);
                if (next <= from) next = from + SEGMENT_US; // ключевых кадров нет — режем по времени
                cuts.add(next);
                from = next;
            }
            cuts.add(end);
            File dir = new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "round_parts");
            dir.mkdirs();
            String base = "round_" + System.currentTimeMillis();
            for (int i = 0; i + 1 < cuts.size(); i++) {
                long a = cuts.get(i), b = cuts.get(i + 1);
                if (b - a < 300_000) continue; // хвост короче 0.3 с не отправляем
                File out = new File(dir, base + "_" + (i + 1) + ".mp4");
                if (writeSegment(source, out, videoTrack, audioTrack, a, b)) {
                    result.add(out);
                } else {
                    for (File f : result) f.delete();
                    result.clear();
                    return result;
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            result.clear();
        } finally {
            probe.release();
        }
        return result;
    }

    private static long keyframeAtOrBefore(ArrayList<Long> keyframes, long t) {
        long best = keyframes.isEmpty() ? t : keyframes.get(0);
        for (long k : keyframes) {
            if (k <= t) best = k;
            else break;
        }
        return best;
    }

    @SuppressLint("WrongConstant")
    private static boolean writeSegment(File source, File out, int videoTrack, int audioTrack, long fromUs, long toUs) {
        MediaMuxer muxer = null;
        MediaExtractor video = new MediaExtractor();
        MediaExtractor audio = audioTrack >= 0 ? new MediaExtractor() : null;
        try {
            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            video.setDataSource(source.getAbsolutePath());
            MediaFormat videoFormat = video.getTrackFormat(videoTrack);
            int outVideo = muxer.addTrack(videoFormat);
            int outAudio = -1;
            MediaFormat audioFormat = null;
            if (audio != null) {
                audio.setDataSource(source.getAbsolutePath());
                audioFormat = audio.getTrackFormat(audioTrack);
                outAudio = muxer.addTrack(audioFormat);
            }
            muxer.start();
            ByteBuffer buffer = ByteBuffer.allocateDirect(Math.max(maxInput(videoFormat), maxInput(audioFormat)));
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            copy(video, videoTrack, muxer, outVideo, fromUs, toUs, buffer, info, true);
            if (audio != null) {
                copy(audio, audioTrack, muxer, outAudio, fromUs, toUs, buffer, info, false);
            }
            muxer.stop();
            return out.length() > 0;
        } catch (Throwable e) {
            FileLog.e(e);
            out.delete();
            return false;
        } finally {
            try { if (muxer != null) muxer.release(); } catch (Throwable ignore) { }
            video.release();
            if (audio != null) audio.release();
        }
    }

    @SuppressLint("WrongConstant")
    private static void copy(MediaExtractor ex, int track, MediaMuxer muxer, int outTrack, long fromUs, long toUs,
                             ByteBuffer buffer, MediaCodec.BufferInfo info, boolean isVideo) {
        ex.selectTrack(track);
        ex.seekTo(fromUs, isVideo ? MediaExtractor.SEEK_TO_PREVIOUS_SYNC : MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        while (true) {
            long t = ex.getSampleTime();
            if (t < 0 || t >= toUs) break;
            buffer.clear();
            int size = ex.readSampleData(buffer, 0);
            if (size < 0) break;
            if (t >= fromUs || isVideo) {
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = Math.max(0, t - fromUs);
                info.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                muxer.writeSampleData(outTrack, buffer, info);
            }
            if (!ex.advance()) break;
        }
    }

    private static int maxInput(MediaFormat format) {
        if (format != null && format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            return Math.max(format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), 64 * 1024);
        }
        return 1024 * 1024;
    }

    // ---- очередь отправки ----

    private static final class Queue implements NotificationCenter.NotificationCenterDelegate {
        private final int account;
        private final long dialogId;
        private final ArrayList<File> parts;
        private final Sender sender;
        private int index = -1;
        private String currentPath;
        private int localId;
        private final Runnable timeout = this::advance;

        Queue(int account, long dialogId, ArrayList<File> parts, Sender sender) {
            this.account = account;
            this.dialogId = dialogId;
            this.parts = parts;
            this.sender = sender;
        }

        void next() {
            index++;
            if (index >= parts.size()) {
                return;
            }
            File part = parts.get(index);
            currentPath = part.getAbsolutePath();
            localId = 0;
            boolean last = index == parts.size() - 1;
            if (!last) {
                NotificationCenter nc = NotificationCenter.getInstance(account);
                nc.addObserver(this, NotificationCenter.didReceiveNewMessages);
                nc.addObserver(this, NotificationCenter.messageReceivedByServer);
                nc.addObserver(this, NotificationCenter.messageSendError);
                AndroidUtilities.runOnUIThread(timeout, WAIT_SENT_MS);
            }
            VideoEditedInfo info = new VideoEditedInfo();
            info.roundVideo = true;
            info.startTime = -1;
            info.endTime = -1;
            info.estimatedSize = Math.max(1, part.length());
            info.framerate = 25;
            info.resultWidth = info.originalWidth = 360;
            info.resultHeight = info.originalHeight = 360;
            info.originalPath = currentPath;
            info.estimatedDuration = durationMs(part);
            sender.send(part, info);
        }

        private void advance() {
            NotificationCenter nc = NotificationCenter.getInstance(account);
            nc.removeObserver(this, NotificationCenter.didReceiveNewMessages);
            nc.removeObserver(this, NotificationCenter.messageReceivedByServer);
            nc.removeObserver(this, NotificationCenter.messageSendError);
            AndroidUtilities.cancelRunOnUIThread(timeout);
            next();
        }

        @Override
        public void didReceivedNotification(int id, int acc, Object... args) {
            if (id == NotificationCenter.didReceiveNewMessages) {
                if (localId != 0 || args.length < 2 || !(args[0] instanceof Long) || (Long) args[0] != dialogId) return;
                @SuppressWarnings("unchecked")
                ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
                for (MessageObject m : messages) {
                    if (m != null && m.messageOwner != null && currentPath.equals(m.messageOwner.attachPath)) {
                        localId = m.getId();
                        break;
                    }
                }
            } else if (id == NotificationCenter.messageReceivedByServer || id == NotificationCenter.messageSendError) {
                // ошибка тоже двигает очередь: остальные куски не должны застрять из-за одного
                if (localId != 0 && args.length > 0 && args[0] instanceof Integer && (Integer) args[0] == localId) {
                    advance();
                }
            }
        }

        private static long durationMs(File file) {
            MediaExtractor ex = new MediaExtractor();
            try {
                ex.setDataSource(file.getAbsolutePath());
                long max = 0;
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    MediaFormat f = ex.getTrackFormat(i);
                    if (f.containsKey(MediaFormat.KEY_DURATION)) max = Math.max(max, f.getLong(MediaFormat.KEY_DURATION));
                }
                return max / 1000;
            } catch (Throwable e) {
                return SEGMENT_MS;
            } finally {
                ex.release();
            }
        }
    }
}

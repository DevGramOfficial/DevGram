package org.telegram.messenger.devgram.player.rhythm;

import android.os.SystemClock;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
public final class RhythmTap implements TeeAudioProcessor.AudioBufferSink {
    private static volatile RhythmTap current;
    private static volatile boolean enabled;
    private boolean analyzerReady;
    private int channelCount;
    private long consumedFrames;
    private int count;
    private int head;
    private boolean playing;
    private long positionAtNs;
    private int sampleRate;
    private final RhythmAnalyzer analyzer = new RhythmAnalyzer(frame -> onFrame(frame));
    private final long[] frameTime = new long[512];
    private final float[] frameBass = new float[512];
    private final float[] frameMid = new float[512];
    private final float[] frameHigh = new float[512];
    private final float[] frameEnergy = new float[512];
    private final float[] frameBeat = new float[512];
    private final float[] frameBpm = new float[512];
    private final float[] frameHat = new float[512];
    private final float[] frameIntensity = new float[512];
    private final byte[] frameFlags = new byte[512];
    private int encoding = 0;
    private float[] mono = new float[0];
    private volatile long baseTimeUs = -9223372036854775807L;
    private long positionUs = -9223372036854775807L;
    private float speed = 1.0f;

    public static final class State {
        public float bass;
        public float beat;
        public float bpm;
        public boolean downbeat;
        public boolean drop;
        public float energy;
        public float hat;
        public float high;
        public float intensity = 0.5f;
        public float mid;
        public int phrases;

        public void clearEvents() {
            this.hat = 0.0f;
            this.beat = 0.0f;
            this.drop = false;
            this.downbeat = false;
            this.phrases = 0;
        }
    }

    public static void setEnabled(boolean z) {
        enabled = z;
    }

    public static RhythmTap getCurrent() {
        if (enabled) {
            return current;
        }
        return null;
    }

    public AudioProcessor createProcessor() {
        return new TeeAudioProcessor(this);
    }

    public AudioSink wrap(AudioSink audioSink) {
        return new ForwardingAudioSink(audioSink) {
            @Override
            public boolean handleBuffer(ByteBuffer byteBuffer, long j, int i) throws AudioSink.InitializationException, AudioSink.WriteException {
                if (RhythmTap.this.baseTimeUs == -9223372036854775807L) {
                    RhythmTap.this.baseTimeUs = j;
                    RhythmTap.this.consumedFrames = 0L;
                }
                return super.handleBuffer(byteBuffer, j, i);
            }

            @Override
            public long getCurrentPositionUs(boolean z) {
                long currentPositionUs = super.getCurrentPositionUs(z);
                RhythmTap.this.onPosition(currentPositionUs);
                return currentPositionUs;
            }

            @Override
            public void setPlaybackParameters(PlaybackParameters playbackParameters) {
                super.setPlaybackParameters(playbackParameters);
                synchronized (RhythmTap.this) {
                    RhythmTap.this.speed = playbackParameters.speed;
                }
            }

            @Override
            public void play() {
                super.play();
                RhythmTap.this.setPlaying(true);
            }

            @Override
            public void pause() {
                super.pause();
                RhythmTap.this.setPlaying(false);
            }

            @Override
            public void flush() {
                super.flush();
                RhythmTap.this.clear();
            }

            @Override
            public void reset() {
                super.reset();
                RhythmTap.this.clear();
                if (RhythmTap.current == RhythmTap.this) {
                    RhythmTap.current = null;
                }
            }
        };
    }

    @Override
    public void flush(int i, int i2, int i3) {
        this.sampleRate = i;
        this.channelCount = i2;
        this.encoding = i3;
        this.analyzerReady = false;
        clear();
    }

    @Override
    public void handleBuffer(ByteBuffer byteBuffer) {
        int i;
        int i2 = this.encoding;
        if (i2 == 2) {
            i = 2;
        } else {
            i = 4;
            if (i2 != 4) {
                i = 0;
            }
        }
        if (i == 0 || this.channelCount <= 0 || this.sampleRate <= 0 || this.baseTimeUs == -9223372036854775807L) {
            return;
        }
        int iRemaining = byteBuffer.remaining() / (this.channelCount * i);
        if (!enabled) {
            this.analyzerReady = false;
            this.consumedFrames += (long) iRemaining;
            return;
        }
        current = this;
        if (!this.analyzerReady) {
            this.analyzer.reset(this.sampleRate);
            this.analyzerReady = true;
            clearFrames();
        }
        ByteBuffer byteBufferOrder = byteBuffer.duplicate().order(ByteOrder.nativeOrder());
        int iPosition = byteBufferOrder.position();
        if (this.mono.length < iRemaining) {
            this.mono = new float[iRemaining];
        }
        float f = 1.0f / this.channelCount;
        for (int i3 = 0; i3 < iRemaining; i3++) {
            float f2 = 0.0f;
            int i5 = this.channelCount;
            for (int i4 = 0; i4 < i5; i4++) {
                int i6 = (((i5 * i3) + i4) * i) + iPosition;
                f2 += i == 2 ? byteBufferOrder.getShort(i6) / 32768.0f : byteBufferOrder.getFloat(i6);
            }
            this.mono[i3] = f2 * f;
        }
        this.analyzer.process(this.mono, iRemaining, this.baseTimeUs + ((this.consumedFrames * 1000000) / ((long) this.sampleRate)));
        this.consumedFrames += (long) iRemaining;
    }
    public synchronized void onFrame(RhythmAnalyzer.Frame frame) {
        try {
            int i = this.count;
            if (i == 512) {
                this.head = (this.head + 1) % 512;
                this.count = i - 1;
            }
            int i2 = this.head;
            int i3 = this.count;
            int i4 = (i2 + i3) % 512;
            this.frameTime[i4] = frame.timeUs;
            this.frameBass[i4] = frame.bass;
            this.frameMid[i4] = frame.mid;
            this.frameHigh[i4] = frame.high;
            this.frameEnergy[i4] = frame.energy;
            this.frameBeat[i4] = frame.beat;
            this.frameBpm[i4] = frame.bpm;
            this.frameHat[i4] = frame.hat;
            this.frameIntensity[i4] = frame.intensity;
            this.frameFlags[i4] = (byte) ((frame.downbeat ? 1 : 0) | (frame.phrase ? 2 : 0) | (frame.drop ? 4 : 0));
            this.count = i3 + 1;
        } catch (Throwable th) {
            throw th;
        }
    }

    public synchronized boolean read(State state) {
        try {
            long j = this.positionUs;
            if (j == -9223372036854775807L) {
                return false;
            }
            long jMin = j + 40000;
            if (this.playing) {
                jMin += Math.min(120000L, (long) (((SystemClock.elapsedRealtimeNanos() - this.positionAtNs) / 1000.0f) * this.speed));
            }
            boolean z = false;
            while (this.count > 0) {
                long[] jArr = this.frameTime;
                int i = this.head;
                long j2 = jArr[i];
                if (j2 > jMin) {
                    break;
                }
                state.bass = this.frameBass[i];
                state.mid = this.frameMid[i];
                state.high = this.frameHigh[i];
                state.energy = this.frameEnergy[i];
                state.bpm = this.frameBpm[i] * this.speed;
                state.intensity = this.frameIntensity[i];
                if (jMin - j2 <= 300000) {
                    state.beat = Math.max(state.beat, this.frameBeat[i]);
                    state.hat = Math.max(state.hat, this.frameHat[this.head]);
                    byte b2 = this.frameFlags[this.head];
                    state.downbeat |= (b2 & 1) != 0;
                    state.drop |= (b2 & 4) != 0;
                    if ((b2 & 2) != 0) {
                        state.phrases++;
                    }
                }
                this.head = (this.head + 1) % 512;
                this.count--;
                z = true;
            }
            return z;
        } catch (Throwable th) {
            throw th;
        }
    }
    public synchronized void onPosition(long j) {
        if (j == Long.MIN_VALUE) {
            return;
        }
        this.positionUs = j;
        this.positionAtNs = SystemClock.elapsedRealtimeNanos();
    }
    public synchronized void setPlaying(boolean z) {
        this.playing = z;
        this.positionAtNs = SystemClock.elapsedRealtimeNanos();
    }

    private synchronized void clearFrames() {
        this.count = 0;
        this.head = 0;
    }
    public synchronized void clear() {
        this.count = 0;
        this.head = 0;
        this.positionUs = -9223372036854775807L;
        this.baseTimeUs = -9223372036854775807L;
        this.consumedFrames = 0L;
        this.analyzerReady = false;
    }
}

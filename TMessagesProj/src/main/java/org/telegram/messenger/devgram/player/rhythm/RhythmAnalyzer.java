package org.telegram.messenger.devgram.player.rhythm;

import java.lang.reflect.Array;
import java.util.Arrays;
import org.telegram.messenger.FourierTransform;
final class RhythmAnalyzer {
    private static final int LOOKAHEAD = Math.max(3, 4);
    private static final float[] RELATED_TEMPO_RATIOS = {2.0f, 0.5f, 1.5f, 0.6666667f};
    private final float[] autocorrelation;
    private final float[] barScores;
    private int beatWindowMax;
    private float bpm;
    private float brightness;
    private float candidateBpm;
    private int candidateCount;
    private final float[] centered;
    private final float[] column;
    private float confidence;
    private final float[] currentChroma;
    private int delay;
    private int downbeatCount;
    private int eventCount;
    private final long[] eventFrame;
    private int eventHead;
    private final float[] eventWeight;
    private float fastEnergy;
    private int filled;
    private float fps;
    private long frame;
    private long grid;
    private float gridBpm;
    private int gridFrameCount;
    private final long[] gridFrames;
    private float harmonicPeak;
    private boolean hasPreviousChroma;
    private final float[] hatNovelty;
    private float hatPeak;
    private final float[] hatRaw;
    private final float[] intensityHistory;
    private long lastDropFrame;
    private long lastHatFrame;
    private long lastOnsetFrame;
    private int lastPhraseDownbeat;
    private float lastPhraseIntensity;
    private long lastTempoFrame;
    private final float[] levelPeaks;
    private final Listener listener;
    private boolean locked;
    private final float[] lowNovelty;
    private float lowNoveltyPeak;
    private int misses;
    private double nextBeat;
    private final float[] novelty;
    private float noveltyPeak;
    private int onsetCount;
    private final long[] onsetFrame;
    private int onsetHead;
    private final float[] onsetStrength;
    private final Frame output;
    private final float[][] pastLevels;
    private final float[] pendingBeat;
    private final boolean[] pendingDownbeat;
    private final boolean[] pendingDrop;
    private final float[] pendingHat;
    private final float[] pendingIntensity;
    private final float[][] pendingLevels;
    private final boolean[] pendingPhrase;
    private final long[] pendingTime;
    private int phaseDisagreements;
    private int position;
    private final float[] previousChroma;
    private float rangeHigh;
    private float rangeLow;
    private final float[] rawNovelty;
    private float referenceEnergy;
    private int sampleRate;
    private float shortEnergy;
    private int sinceHop;
    private float slowEnergy;
    private final double[] tempoScores;
    private final float[] window = new float[2048];
    private final float[] samples = new float[2048];
    private final float[] frameSamples = new float[2048];
    private final FourierTransform.FFT fft = new FourierTransform.FFT(2048, 48000.0f);
    private final int[] bandFrom = new int[24];
    private final int[] bandTo = new int[24];
    private final float[] bandWeight = new float[24];
    private final int[] bandKind = new int[24];
    private final boolean[] bandHat = new boolean[24];
    private final float[] bandHistory = new float[24576];
    private final float[] bandLevel = new float[24];

    public static final class Frame {
        float bass;
        float beat;
        float bpm;
        boolean downbeat;
        boolean drop;
        float energy;
        float hat;
        float high;
        float intensity;
        float mid;
        boolean phrase;
        long timeUs;
    }

    public interface Listener {
        void onFrame(Frame frame);
    }

    private static float square(float f) {
        return f * f;
    }

    public RhythmAnalyzer(Listener listener) {
        Class cls = Float.TYPE;
        this.pastLevels = (float[][]) Array.newInstance((Class<?>) cls, 2, 24);
        this.novelty = new float[1024];
        this.rawNovelty = new float[1024];
        this.lowNovelty = new float[1024];
        this.hatNovelty = new float[1024];
        this.hatRaw = new float[1024];
        this.intensityHistory = new float[1024];
        this.autocorrelation = new float[1024];
        this.tempoScores = new double[1024];
        this.centered = new float[1024];
        this.levelPeaks = new float[4];
        this.onsetFrame = new long[16];
        this.onsetStrength = new float[16];
        this.eventFrame = new long[128];
        this.eventWeight = new float[128];
        this.pendingTime = new long[64];
        this.pendingLevels = (float[][]) Array.newInstance((Class<?>) cls, 4, 64);
        this.pendingBeat = new float[64];
        this.pendingHat = new float[64];
        this.pendingIntensity = new float[64];
        this.pendingDownbeat = new boolean[64];
        this.pendingPhrase = new boolean[64];
        this.pendingDrop = new boolean[64];
        this.output = new Frame();
        this.barScores = new float[4];
        this.gridFrames = new long[2];
        this.previousChroma = new float[24];
        this.currentChroma = new float[24];
        this.column = new float[256];
        this.listener = listener;
        for (int i = 0; i < 2048; i++) {
            this.window[i] = (float) (0.5d - (Math.cos((((double) i) * 6.283185307179586d) / 2047.0d) * 0.5d));
        }
        reset(0);
    }

    public void reset(int i) {
        this.sampleRate = i;
        this.fps = i > 0 ? i / 512.0f : 0.0f;
        this.sinceHop = 0;
        this.filled = 0;
        this.position = 0;
        this.frame = 0L;
        this.harmonicPeak = 1.0E-4f;
        this.hatPeak = 1.0E-4f;
        this.lowNoveltyPeak = 1.0E-4f;
        this.noveltyPeak = 1.0E-4f;
        Arrays.fill(this.levelPeaks, 1.0E-4f);
        for (float[] fArr : this.pastLevels) {
            Arrays.fill(fArr, 0.0f);
        }
        Arrays.fill(this.novelty, 0.0f);
        Arrays.fill(this.rawNovelty, 0.0f);
        Arrays.fill(this.lowNovelty, 0.0f);
        Arrays.fill(this.hatNovelty, 0.0f);
        Arrays.fill(this.hatRaw, 0.0f);
        Arrays.fill(this.intensityHistory, 0.0f);
        Arrays.fill(this.bandHistory, 0.0f);
        Arrays.fill(this.barScores, 0.0f);
        this.eventHead = 0;
        this.eventCount = 0;
        this.onsetHead = 0;
        this.onsetCount = 0;
        this.lastDropFrame = -4611686018427387904L;
        this.lastHatFrame = -4611686018427387904L;
        this.lastOnsetFrame = -4611686018427387904L;
        this.gridFrameCount = 0;
        this.hasPreviousChroma = false;
        this.grid = 0L;
        this.gridBpm = 0.0f;
        this.downbeatCount = 0;
        this.lastPhraseDownbeat = -16;
        this.lastPhraseIntensity = 0.0f;
        this.brightness = 0.0f;
        this.referenceEnergy = 0.0f;
        this.slowEnergy = 0.0f;
        this.fastEnergy = 0.0f;
        this.shortEnergy = 0.0f;
        this.rangeHigh = -1.0f;
        this.rangeLow = -1.0f;
        this.candidateBpm = 0.0f;
        this.confidence = 0.0f;
        this.bpm = 0.0f;
        this.candidateCount = 0;
        this.lastTempoFrame = 0L;
        this.locked = false;
        this.phaseDisagreements = 0;
        this.misses = 0;
        if (i <= 0) {
            return;
        }
        int iMax = Math.max(2, Math.round(this.fps * 0.05f));
        this.beatWindowMax = iMax;
        this.delay = Math.min(63, LOOKAHEAD + (iMax * 2) + 2);
        float f = i / 2048.0f;
        double dPow = Math.pow(400.0d, 0.041666666666666664d);
        int iMin = 1;
        for (int i2 = 0; i2 < 24; i2++) {
            double dPow2 = Math.pow(dPow, i2) * 30.0d;
            double d = dPow2 * dPow;
            double d2 = f;
            int iMax2 = Math.max(iMin, (int) Math.floor(dPow2 / d2));
            iMin = Math.min(1023, Math.max(iMax2 + 1, (int) Math.ceil(d / d2)));
            this.bandFrom[i2] = iMax2;
            this.bandTo[i2] = iMin;
            double dSqrt = Math.sqrt(dPow2 * d);
            int[] iArr = this.bandKind;
            int i3 = dSqrt < 150.0d ? 0 : dSqrt < 2000.0d ? 1 : 2;
            iArr[i2] = i3;
            this.bandWeight[i2] = i3 == 0 ? 1.0f : i3 == 1 ? 0.6f : 0.4f;
            this.bandHat[i2] = dSqrt > 5000.0d;
        }
    }

    public float getBpm() {
        if (this.locked) {
            return this.bpm;
        }
        return 0.0f;
    }

    public void process(float[] fArr, int i, long j) {
        if (this.sampleRate <= 0) {
            return;
        }
        for (int i2 = 0; i2 < i; i2++) {
            float[] fArr2 = this.samples;
            int i3 = this.position;
            fArr2[i3] = fArr[i2];
            this.position = (i3 + 1) & 2047;
            int i4 = this.filled;
            if (i4 < 2048) {
                this.filled = i4 + 1;
            }
            int i5 = this.sinceHop + 1;
            this.sinceHop = i5;
            if (i5 >= 512 && this.filled == 2048) {
                this.sinceHop = 0;
                int i6 = this.sampleRate;
                analyze((((((long) (i2 + 1)) * 1000000) / ((long) i6)) + j) - (1024000000 / ((long) i6)));
            }
        }
    }

    private void analyze(long j) {
        int i;
        float fSquare;
        int i2;
        double d = 0.0d;
        for (int i3 = 0; i3 < 2048; i3++) {
            float f = this.samples[(this.position + i3) & 2047];
            if (i3 >= 1536) {
                d += (double) (f * f);
            }
            this.frameSamples[i3] = f * this.window[i3];
        }
        this.fft.forward(this.frameSamples);
        float fSqrt = (float) Math.sqrt(d / 512.0d);
        boolean z = fSqrt < 1.0E-4f;
        float[][] fArr = this.pastLevels;
        long j2 = this.frame;
        float[] fArr2 = fArr[(int) (j2 % 2)];
        int i4 = (int) (j2 & 1023);
        int i5 = 0;
        int i6 = 0;
        int i7 = 0;
        float f2 = 0.0f;
        int i8 = 0;
        float f3 = 0.0f;
        float f4 = 0.0f;
        float f5 = 0.0f;
        int i9 = 0;
        float f6 = 0.0f;
        float f7 = 0.0f;
        float f8 = 0.0f;
        int i10 = 0;
        while (i8 < 24) {
            int i11 = this.bandFrom[i8];
            float band = 0.0f;
            while (true) {
                i2 = this.bandTo[i8];
                if (i11 >= i2) {
                    break;
                }
                band += this.fft.getBand(i11);
                i11++;
            }
            float fLog1p = z ? 0.0f : (float) Math.log1p(((band * 1000.0f) * 0.001953125f) / (i2 - this.bandFrom[i8]));
            this.bandLevel[i8] = fLog1p;
            this.bandHistory[(i4 * 24) + i8] = fLog1p;
            float fMax = fArr2[i8];
            if (i8 > 0) {
                fMax = Math.max(fMax, fArr2[i8 - 1]);
            }
            if (i8 < 23) {
                fMax = Math.max(fMax, fArr2[i8 + 1]);
            }
            float fMax2 = Math.max(0.0f, fLog1p - fMax);
            float f9 = this.bandWeight[i8];
            f3 += f9 * fMax2;
            f4 += f9;
            int i12 = this.bandKind[i8];
            if (i12 == 0) {
                f5 += fMax2;
                i9++;
            }
            if (this.bandHat[i8]) {
                f6 += fMax2;
                i7++;
            }
            if (i12 == 0) {
                f2 += fLog1p;
                i5++;
            } else if (i12 == 1) {
                f7 += fLog1p;
                i6++;
            } else {
                f8 += fLog1p;
                i10++;
            }
            i8++;
            z = z;
        }
        boolean z2 = z;
        int i13 = i10;
        System.arraycopy(this.bandLevel, 0, fArr2, 0, 24);
        float f10 = f3 / f4;
        int i14 = (int) (this.frame & 1023);
        this.rawNovelty[i14] = f10;
        float fMax3 = Math.max(f10, this.noveltyPeak * 0.998f);
        this.noveltyPeak = fMax3;
        this.novelty[i14] = z2 ? 0.0f : f10 / Math.max(fMax3, 1.0E-4f);
        float fMax4 = f5 / Math.max(1, i9);
        float fMax5 = Math.max(fMax4, this.lowNoveltyPeak * 0.998f);
        this.lowNoveltyPeak = fMax5;
        this.lowNovelty[i14] = z2 ? 0.0f : fMax4 / Math.max(fMax5, 1.0E-4f);
        float fMax6 = f6 / Math.max(1, i7);
        this.hatRaw[i14] = fMax6;
        float fMax7 = Math.max(fMax6, this.hatPeak * 0.998f);
        this.hatPeak = fMax7;
        this.hatNovelty[i14] = z2 ? 0.0f : fMax6 / Math.max(fMax7, 1.0E-4f);
        int i15 = (int) (this.frame % 64);
        this.pendingTime[i15] = j;
        this.pendingBeat[i15] = 0.0f;
        this.pendingHat[i15] = 0.0f;
        boolean[] zArr = this.pendingDownbeat;
        boolean[] zArr2 = this.pendingPhrase;
        this.pendingDrop[i15] = false;
        zArr2[i15] = false;
        zArr[i15] = false;
        float[] fArr3 = this.pendingLevels[0];
        if (z2) {
            fSquare = 0.0f;
            i = 1;
        } else {
            i = 1;
            fSquare = square(normalize(0, f2 / Math.max(1, i5)));
        }
        fArr3[i15] = fSquare;
        this.pendingLevels[i][i15] = z2 ? 0.0f : square(normalize(i, f7 / Math.max(i, i6)));
        this.pendingLevels[2][i15] = z2 ? 0.0f : square(normalize(2, f8 / Math.max(i, i13)));
        this.pendingLevels[3][i15] = z2 ? 0.0f : normalize(3, fSqrt);
        updateSections(fSqrt, z2 ? 0.0f : f8 / Math.max(1, i13), i15);
        long j3 = this.frame - ((long) LOOKAHEAD);
        if (j3 >= 10) {
            pickOnset(j3);
            pickHat(j3);
            if (this.frame - this.lastTempoFrame >= Math.round(this.fps * 0.5f) && this.frame >= Math.round(this.fps * 3.0f)) {
                this.lastTempoFrame = this.frame;
                updateTempo();
                checkPhase(j3);
            }
            track(j3);
        }
        long j4 = this.frame - ((long) this.delay);
        if (j4 >= 0) {
            int i16 = (int) (j4 % 64);
            Frame frame = this.output;
            frame.timeUs = this.pendingTime[i16];
            float[][] fArr4 = this.pendingLevels;
            frame.bass = fArr4[0][i16];
            frame.mid = fArr4[1][i16];
            frame.high = fArr4[2][i16];
            frame.energy = fArr4[3][i16];
            frame.beat = this.pendingBeat[i16];
            frame.bpm = getBpm();
            Frame frame2 = this.output;
            frame2.hat = this.pendingHat[i16];
            frame2.intensity = this.pendingIntensity[i16];
            frame2.downbeat = this.pendingDownbeat[i16];
            frame2.phrase = this.pendingPhrase[i16];
            frame2.drop = this.pendingDrop[i16];
            this.listener.onFrame(frame2);
        }
        this.frame++;
    }

    private void pickOnset(long j) {
        int i = (int) (j & 1023);
        float f = this.novelty[i];
        if (this.rawNovelty[i] < 0.02f || j - this.lastOnsetFrame < Math.round(this.fps * 0.05f)) {
            return;
        }
        for (long j2 = j - 3; j2 <= j + 3; j2++) {
            float f2 = this.novelty[(int) (j2 & 1023)];
            if (j2 < j && f2 >= f) {
                return;
            }
            if (j2 > j && f2 > f) {
                return;
            }
        }
        float f3 = 0.0f;
        for (long j3 = j - 10; j3 <= 4 + j; j3++) {
            f3 += this.novelty[(int) (j3 & 1023)];
        }
        if (f < (f3 / 15.0f) + 0.07f) {
            return;
        }
        this.lastOnsetFrame = j;
        int i2 = this.onsetHead;
        int i3 = this.onsetCount;
        int i4 = (i2 + i3) % 16;
        if (i3 == 16) {
            this.onsetHead = (i2 + 1) % 16;
        } else {
            this.onsetCount = i3 + 1;
        }
        this.onsetFrame[i4] = j;
        this.onsetStrength[i4] = Math.min(1.0f, f);
        addEvent(j, 1.0f);
    }

    private void pickHat(long j) {
        long j2 = 1023;
        int i = (int) (j & 1023);
        float f = this.hatNovelty[i];
        if (this.hatRaw[i] < 0.02f || j - this.lastHatFrame < Math.round(this.fps * 0.06f)) {
            return;
        }
        long j3 = j - 2;
        while (true) {
            long j4 = j + 2;
            if (j3 > j4) {
                long j5 = j - 8;
                float f2 = 0.0f;
                while (j5 <= j4) {
                    long j6 = j2;
                    f2 += this.hatNovelty[(int) (j5 & j6)];
                    j5++;
                    j2 = j6;
                }
                if (f < (f2 / 11.0f) + 0.12f) {
                    return;
                }
                this.lastHatFrame = j;
                if (j > this.frame - ((long) this.delay)) {
                    int i2 = (int) (j % 64);
                    float[] fArr = this.pendingHat;
                    fArr[i2] = Math.max(fArr[i2], Math.min(1.0f, f));
                }
                addEvent(j, 0.5f);
                return;
            }
            float f3 = this.hatNovelty[(int) (j3 & 1023)];
            if (j3 < j && f3 >= f) {
                return;
            }
            if (j3 > j && f3 > f) {
                return;
            } else {
                j3++;
            }
        }
    }

    private void addEvent(long j, float f) {
        int i = this.eventHead;
        int i2 = this.eventCount;
        int i3 = (i + i2) % 128;
        if (i2 == 128) {
            this.eventHead = (i + 1) % 128;
        } else {
            this.eventCount = i2 + 1;
        }
        this.eventFrame[i3] = j;
        this.eventWeight[i3] = f;
    }

    private void updateSections(float f, float f2, int i) {
        if (this.frame == 0) {
            this.slowEnergy = f;
            this.fastEnergy = f;
            this.shortEnergy = f;
        }
        float f3 = this.shortEnergy;
        this.shortEnergy = f3 + ((f - f3) * smoothing(2.0f));
        float f4 = this.fastEnergy;
        this.fastEnergy = f4 + ((f - f4) * smoothing(0.25f));
        float f5 = this.slowEnergy;
        this.slowEnergy = f5 + ((f - f5) * smoothing(4.0f));
        float f6 = this.brightness;
        this.brightness = f6 + ((f2 - f6) * smoothing(2.0f));
        this.referenceEnergy = Math.max(this.shortEnergy, this.referenceEnergy * 0.99995f);
        long jRound = this.frame - ((long) Math.round(this.fps * 2.0f));
        while (true) {
            int i2 = this.eventCount;
            if (i2 <= 0) {
                break;
            }
            long[] jArr = this.eventFrame;
            int i3 = this.eventHead;
            if (jArr[i3] >= jRound) {
                break;
            }
            this.eventHead = (i3 + 1) % 128;
            this.eventCount = i2 - 1;
        }
        float f7 = 0.0f;
        for (int i4 = 0; i4 < this.eventCount; i4++) {
            f7 += this.eventWeight[(this.eventHead + i4) % 128];
        }
        float fClamp01 = (clamp01((decibels(this.shortEnergy) - (-30.0f)) / 22.0f) * 0.45f) + (clamp01((f7 / 2.0f) / 8.0f) * 0.35f) + (clamp01(this.brightness / 3.0f) * 0.2f);
        if (this.rangeLow < 0.0f) {
            this.rangeHigh = fClamp01;
            this.rangeLow = fClamp01;
        }
        float f8 = this.rangeHigh;
        float f9 = fClamp01 > f8 ? fClamp01 : f8 - 1.2E-4f;
        this.rangeHigh = f9;
        float f10 = this.rangeLow;
        float f11 = fClamp01 < f10 ? fClamp01 : f10 + 1.2E-4f;
        this.rangeLow = f11;
        float fClamp02 = this.frame < ((long) Math.round(this.fps * 3.0f)) ? 0.5f : clamp01((fClamp01 * 0.45f) + ((f9 - f11 > 0.12f ? (fClamp01 - f11) / (f9 - f11) : 0.5f) * 0.55f));
        float[] fArr = this.intensityHistory;
        long j = this.frame;
        fArr[(int) (j & 1023)] = fClamp02;
        this.pendingIntensity[i] = fClamp02;
        long jRound2 = j - ((long) Math.round(this.fps * 2.0f));
        if (this.frame - this.lastDropFrame <= Math.round(this.fps * 6.0f) || this.frame <= Math.round(this.fps * 3.0f)) {
            return;
        }
        float f12 = this.fastEnergy;
        if (f12 <= this.slowEnergy * 1.8f || this.intensityHistory[(int) (jRound2 & 1023)] >= 0.45f || f12 <= this.referenceEnergy * 0.5f) {
            return;
        }
        this.lastDropFrame = this.frame;
        this.pendingDrop[i] = true;
    }

    private float smoothing(float f) {
        return 1.0f - ((float) Math.exp((-1.0d) / ((double) (this.fps * f))));
    }

    private static float decibels(float f) {
        return ((float) Math.log10(Math.max(f, 1.0E-6f))) * 20.0f;
    }

    private static float clamp01(float f) {
        return Math.max(0.0f, Math.min(1.0f, f));
    }

    private void gridStep(long j, float f) {
        long j2;
        float f2;
        int i = (int) (this.grid % 4);
        float fMax = 0.0f;
        for (long j3 = j - 1; j3 <= j + 1; j3++) {
            fMax = Math.max(fMax, this.lowNovelty[(int) (1023 & j3)]);
        }
        float[] fArr = this.barScores;
        fArr[i] = (fArr[i] * 0.85f) + (fMax * 0.07499999f);
        this.gridBpm = this.bpm;
        int i2 = this.gridFrameCount;
        long[] jArr = this.gridFrames;
        if (i2 == 2) {
            jArr[0] = jArr[1];
            jArr[1] = j;
        } else {
            this.gridFrameCount = i2 + 1;
            jArr[i2] = j;
        }
        if (this.gridFrameCount == 2) {
            long[] jArr2 = this.gridFrames;
            long j4 = jArr2[1];
            j2 = 1;
            f2 = 0.0f;
            long j5 = jArr2[0];
            if (j4 - j5 >= 4) {
                intervalChroma(j5, j4);
                if (this.hasPreviousChroma) {
                    float fAbs = 0.0f;
                    int i3 = 0;
                    for (int i4 = 0; i4 < 24; i4++) {
                        if (this.bandKind[i4] != 2) {
                            fAbs += Math.abs(this.currentChroma[i4] - this.previousChroma[i4]);
                            i3++;
                        }
                    }
                    float fMax2 = fAbs / Math.max(1, i3);
                    float fMax3 = Math.max(fMax2, this.harmonicPeak * 0.99f);
                    this.harmonicPeak = fMax3;
                    int i5 = (int) ((this.grid + 3) % 4);
                    float[] fArr2 = this.barScores;
                    fArr2[i5] = (fArr2[i5] * 0.85f) + ((fMax2 * 0.14999998f) / Math.max(fMax3, 1.0E-4f));
                }
                System.arraycopy(this.currentChroma, 0, this.previousChroma, 0, 24);
                this.hasPreviousChroma = true;
            }
        } else {
            j2 = 1;
            f2 = 0.0f;
        }
        this.grid += j2;
        int i6 = 0;
        for (int i7 = 1; i7 < 4; i7++) {
            float[] fArr3 = this.barScores;
            if (fArr3[i7] > fArr3[i6]) {
                i6 = i7;
            }
        }
        float fMax4 = f2;
        for (int i8 = 0; i8 < 4; i8++) {
            if (i8 != i6) {
                fMax4 = Math.max(fMax4, this.barScores[i8]);
            }
        }
        long j6 = this.frame;
        if (j <= j6 - ((long) this.delay) || j > j6) {
            return;
        }
        int i9 = (int) (j % 64);
        if (this.grid >= 8 && this.barScores[i6] > fMax4 * 1.12f && i == i6) {
            this.pendingDownbeat[i9] = f > f2;
            int i10 = this.downbeatCount + 1;
            this.downbeatCount = i10;
            float f3 = this.intensityHistory[(int) (j & 1023)];
            int i11 = i10 - this.lastPhraseDownbeat;
            if (f > f2 && i11 >= 4 && (i11 >= 8 || Math.abs(f3 - this.lastPhraseIntensity) > 0.3f)) {
                this.pendingPhrase[i9] = true;
                this.lastPhraseDownbeat = this.downbeatCount;
                this.lastPhraseIntensity = f3;
            }
        }
        if (f > f2) {
            emitBeat(j, f);
        }
    }

    private void intervalChroma(long j, long j2) {
        float[] fArr;
        int iMin = (int) Math.min(256L, j2 - j);
        for (int i = 0; i < 24; i++) {
            if (this.bandKind[i] == 2) {
                this.currentChroma[i] = 0.0f;
            } else {
                int i2 = 0;
                while (true) {
                    fArr = this.column;
                    if (i2 >= iMin) {
                        break;
                    }
                    fArr[i2] = this.bandHistory[(((int) ((((long) i2) + j) & 1023)) * 24) + i];
                    i2++;
                }
                Arrays.sort(fArr, 0, iMin);
                this.currentChroma[i] = this.column[iMin / 2];
            }
        }
    }

    private void track(long j) {
        if (!this.locked) {
            int iFindOnset = findOnset(j, j);
            if (iFindOnset < 0) {
                return;
            }
            float f = this.onsetStrength[iFindOnset];
            if (this.bpm <= 0.0f || this.confidence < 0.1f) {
                if (f >= 0.35f) {
                    emitBeat(j, f * 0.8f);
                    return;
                }
                return;
            }
            this.locked = true;
            this.phaseDisagreements = 0;
            this.misses = 0;
            double dPeriod = period();
            double dBestGrid = bestGrid(j);
            double d = j;
            boolean z = Math.abs(dBestGrid - d) <= ((double) beatWindow(dPeriod));
            if (!z) {
                d = dBestGrid + dPeriod;
            }
            realignGrid(d);
            this.nextBeat = dBestGrid + dPeriod;
            if (z) {
                gridStep(j, (f * 0.6f) + 0.4f);
                return;
            }
            return;
        }
        double dPeriod2 = period();
        int iBeatWindow = beatWindow(dPeriod2);
        while (this.locked) {
            double d2 = j;
            double d3 = this.nextBeat;
            if (d2 < ((double) iBeatWindow) + d3) {
                return;
            }
            long jRound = Math.round(d3);
            long j2 = iBeatWindow;
            long j3 = jRound - j2;
            long j4 = j2 + jRound;
            int iFindOnset2 = findOnset(j3, j4);
            if (iFindOnset2 >= 0) {
                gridStep(this.onsetFrame[iFindOnset2], (this.onsetStrength[iFindOnset2] * 0.6f) + 0.4f);
                this.nextBeat = this.onsetFrame[iFindOnset2] + dPeriod2;
                this.misses = 0;
            } else {
                gridStep(jRound, (maxNovelty(j3, j4) >= 0.12f || energyAt(jRound) >= 0.35f) ? 0.3f : 0.0f);
                this.nextBeat += dPeriod2;
                int i = this.misses + 1;
                this.misses = i;
                if (i > 4) {
                    this.locked = false;
                }
            }
        }
    }

    private int beatWindow(double d) {
        return Math.max(2, Math.min(this.beatWindowMax, (int) Math.round(d * 0.11999999731779099d)));
    }

    private double bestGrid(long j) {
        double d;
        double dPeriod = period();
        int iMax = Math.max(2, (int) (((double) (this.fps * 4.0f)) / dPeriod));
        double d2 = j;
        double d3 = -1.0d;
        int i = 0;
        while (i < ((int) Math.ceil(dPeriod))) {
            double d4 = 0.0d;
            int i2 = 0;
            while (i2 < iMax) {
                int i3 = i;
                long jRound = Math.round((j - ((long) i)) - (((double) i2) * dPeriod));
                if (jRound < 1 || jRound > this.frame) {
                    d = dPeriod;
                } else {
                    long j2 = jRound - 1;
                    float fMax = 0.0f;
                    while (true) {
                        d = dPeriod;
                        if (j2 > Math.min(jRound + 1, this.frame)) {
                            break;
                        }
                        int i4 = (int) (1023 & j2);
                        fMax = Math.max(fMax, this.novelty[i4] + (this.lowNovelty[i4] * 1.5f));
                        j2++;
                        dPeriod = d;
                    }
                    d4 += (double) fMax;
                }
                i2++;
                i = i3;
                dPeriod = d;
            }
            double d5 = dPeriod;
            int i5 = i;
            if (d4 > d3) {
                d2 = j - ((long) i5);
                d3 = d4;
            }
            i = i5 + 1;
            dPeriod = d5;
        }
        return d2;
    }

    private void checkPhase(long j) {
        if (this.locked) {
            double dPeriod = period();
            double dBestGrid = bestGrid(j);
            double d = (this.nextBeat - dBestGrid) % dPeriod;
            if (d < 0.0d) {
                d += dPeriod;
            }
            if (Math.min(d, dPeriod - d) <= beatWindow(dPeriod)) {
                this.phaseDisagreements = 0;
                return;
            }
            int i = this.phaseDisagreements + 1;
            this.phaseDisagreements = i;
            if (i >= 2) {
                this.phaseDisagreements = 0;
                double d2 = dBestGrid + dPeriod;
                this.nextBeat = d2;
                realignGrid(d2);
            }
        }
    }

    /* JADX WARN: Code duplicated, block: B:14:0x0049  */
    private void realignGrid(double d) {
        int i = this.gridFrameCount;
        if (i == 0) {
            return;
        }
        long jRound = Math.round((d - this.gridFrames[i - 1]) / period()) - 1;
        if (jRound >= -1 && jRound <= 16) {
            float f = this.gridBpm;
            if (f > 0.0f && Math.abs(this.bpm - f) / this.gridBpm < 0.05f) {
                this.grid = Math.max(0L, this.grid + jRound);
            } else {
                this.grid = 0L;
                Arrays.fill(this.barScores, 0.0f);
            }
        } else {
            this.grid = 0L;
            Arrays.fill(this.barScores, 0.0f);
        }
        this.gridFrameCount = 0;
        this.hasPreviousChroma = false;
    }

    /* JADX WARN: Code duplicated, block: B:12:0x0023  */
    private int findOnset(long j, long j2) {
        int i = -1;
        for (int i2 = 0; i2 < this.onsetCount; i2++) {
            int i3 = (this.onsetHead + i2) % 16;
            long j3 = this.onsetFrame[i3];
            if (j3 >= j && j3 <= j2) {
                if (i >= 0) {
                    float[] fArr = this.onsetStrength;
                    if (fArr[i3] > fArr[i]) {
                        i = i3;
                    }
                } else {
                    i = i3;
                }
            }
        }
        return i;
    }

    private float energyAt(long j) {
        long j2 = this.frame;
        if (j <= j2 - ((long) this.delay) || j > j2) {
            return 0.0f;
        }
        return this.pendingLevels[3][(int) (j % 64)];
    }

    private float maxNovelty(long j, long j2) {
        float fMax = 0.0f;
        for (long jMax = Math.max(0L, j); jMax <= j2 && jMax <= this.frame; jMax++) {
            fMax = Math.max(fMax, this.novelty[(int) (1023 & jMax)]);
        }
        return fMax;
    }

    private void emitBeat(long j, float f) {
        long j2 = this.frame;
        if (j <= j2 - ((long) this.delay) || j > j2) {
            return;
        }
        int i = (int) (j % 64);
        float[] fArr = this.pendingBeat;
        fArr[i] = Math.max(fArr[i], Math.min(1.0f, f));
    }

    private double period() {
        return (((double) this.fps) * 60.0d) / ((double) this.bpm);
    }

    private void updateTempo() {
        double d;
        int iMin = (int) Math.min(Math.min(this.frame, 1024L), Math.round(this.fps * 6.0f));
        int iFloor = (int) Math.floor((this.fps * 60.0f) / 200.0f);
        int iCeil = (int) Math.ceil((this.fps * 60.0f) / 60.0f);
        int i = iCeil + 1;
        int i2 = i * 2;
        if (iMin <= i2 + 1) {
            return;
        }
        double d2 = 0.0d;
        for (int i3 = 0; i3 < iMin; i3++) {
            d2 += (double) this.novelty[(int) (((this.frame - ((long) iMin)) + ((long) i3)) & 1023)];
        }
        double d3 = 0.0d;
        double d4 = d2 / ((double) iMin);
        for (int i4 = 0; i4 < iMin; i4++) {
            this.centered[i4] = (float) (((double) this.novelty[(int) (((this.frame - ((long) iMin)) + ((long) i4)) & 1023)]) - d4);
        }
        for (int i5 = 0; i5 <= i2; i5++) {
            double d5 = 0.0d;
            for (int i6 = i5; i6 < iMin; i6++) {
                float[] fArr = this.centered;
                d5 += (double) (fArr[i6] * fArr[i6 - i5]);
            }
            this.autocorrelation[i5] = (float) (d5 / ((double) (iMin - i5)));
        }
        float f = this.autocorrelation[0];
        float f2 = 0.0f;
        if (f <= 0.0f) {
            return;
        }
        double[] dArr = this.tempoScores;
        int i7 = 1;
        int iMax = Math.max(1, iFloor - 1);
        int i8 = -1;
        double d6 = 0.0d;
        while (true) {
            d = d3;
            if (iMax > i) {
                break;
            }
            int i9 = i7;
            double dLog = (Math.log(((((double) this.fps) * 60.0d) / ((double) iMax)) / 120.0d) / Math.log(2.0d)) / 1.0d;
            double[] dArr2 = dArr;
            double dExp = Math.exp(dLog * (-0.5d) * dLog) * (((double) Math.max(f2, this.autocorrelation[iMax])) + (((double) Math.max(f2, this.autocorrelation[iMax * 2])) * 0.5d));
            dArr2[iMax] = dExp;
            if (iMax >= iFloor && iMax <= iCeil && dExp > d6) {
                i8 = iMax;
                d6 = dExp;
            }
            iMax++;
            d3 = d;
            i7 = i9;
            dArr = dArr2;
            f2 = 0.0f;
        }
        double[] dArr3 = dArr;
        int i10 = i7;
        if (i8 < 0) {
            return;
        }
        double dMax = i8;
        double d7 = dArr3[i8 - 1];
        double d8 = dArr3[i8 + 1];
        double d9 = (d7 - (d6 * 2.0d)) + d8;
        if (d9 < d) {
            dMax += Math.max(-0.5d, Math.min(0.5d, ((d7 - d8) * 0.5d) / d9));
        }
        float f3 = (float) ((((double) this.fps) * 60.0d) / dMax);
        this.confidence = Math.max(0.0f, this.autocorrelation[i8]) / f;
        float f4 = this.bpm;
        if (f4 <= 0.0f || Math.abs(f3 - f4) / this.bpm < 0.05f) {
            float f5 = this.bpm;
            if (f5 > 0.0f) {
                f3 = ((f3 - f5) * 0.2f) + f5;
            }
            this.bpm = f3;
            this.candidateCount = 0;
            return;
        }
        float f6 = this.candidateBpm;
        if (f6 > 0.0f && Math.abs(f3 - f6) / this.candidateBpm < 0.05f) {
            int i11 = this.candidateCount + 1;
            this.candidateCount = i11;
            if (i11 >= (isRelatedTempo(f3) ? 4 : 2)) {
                this.bpm = f3;
                this.candidateCount = 0;
                this.locked = false;
                return;
            }
            return;
        }
        this.candidateBpm = f3;
        this.candidateCount = i10;
    }

    private boolean isRelatedTempo(float f) {
        for (float f2 : RELATED_TEMPO_RATIOS) {
            if (Math.abs(f - (this.bpm * f2)) / (this.bpm * f2) < 0.05f) {
                return true;
            }
        }
        return false;
    }

    private float normalize(int i, float f) {
        float[] fArr = this.levelPeaks;
        fArr[i] = Math.max(f, fArr[i] * 0.999f);
        return f / Math.max(this.levelPeaks[i], 1.0E-4f);
    }
}

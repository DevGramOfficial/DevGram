package org.telegram.messenger.devgram.player.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.devgram.player.rhythm.RhythmTap;
import java.util.Arrays;
import java.util.Random;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
public class RhythmBackground {
    private boolean active;
    private float angle;
    private boolean animating;
    private float bass;
    private float bloom;
    private RadialGradient bloomShader;
    private float bpm;
    private float colorShift;
    private int colorTarget;
    private float coverRadius;
    private float energy;
    private float high;
    private long lastFrameNs;
    private float mid;
    private float particlePresence;
    private float particleTime;
    private boolean particlesEnabled;
    private boolean particlesReady;
    private float presence;
    private float pulse;
    private int scrimColor;
    private LinearGradient scrimShader;
    private float shimmer;
    private float spin;
    private float surge;
    private static final float[] BLOB_ORBIT_X = {0.3f, 0.34f, 0.26f, 0.38f};
    private static final float[] BLOB_ORBIT_Y = {0.26f, 0.3f, 0.22f, 0.34f};
    private static final float[] BLOB_ANGLE = {0.0f, 1.5707964f, 3.1415927f, 4.712389f};
    private static final float[] BLOB_SPIN = {1.0f, 0.75f, -1.25f, 0.6f};
    private static final float[] BLOB_RADIUS = {1.02f, 0.94f, 0.96f, 0.86f};
    private static final float[] BLOB_ALPHA_DARK = {0.31f, 0.26f, 0.29f, 0.23f};
    private static final float[] BLOB_ALPHA_LIGHT = {0.22f, 0.19f, 0.2f, 0.16f};
    private static final float[] GLOW_STOPS = {0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f};
    private static final float[] GLOW_FALLOFF = {1.0f, 0.88f, 0.6f, 0.32f, 0.12f, 0.0f};
    private static final int[] BLOB_BAND = {0, 1, 0, 2};
    private final Paint blobPaint = new Paint(5);
    private final Paint scrimPaint = new Paint(5);
    private final Paint particlePaint = new Paint(1);
    private final Matrix matrix = new Matrix();
    private final RadialGradient[] shaders = new RadialGradient[4];
    private final int[] colors = new int[4];
    private final RhythmTap.State state = new RhythmTap.State();
    private final Random random = new Random();
    private final int[] particleColors = new int[4];
    private final float[] particleX = new float[80];
    private final float[] particleY = new float[80];
    private final float[] particleVx = new float[80];
    private final float[] particleVy = new float[80];
    private final float[] particleLife = new float[80];
    private final float[] particleDuration = new float[80];
    private final float[] particleBrightness = new float[80];
    private final float[] particleSpark = new float[80];
    private final float[] particleRadius = new float[80];
    private final RectF bounds = new RectF();
    private final RectF cover = new RectF();
    private final float[] flowForce = new float[2];
    private boolean dark = true;
    private float intensity = 0.5f;
    private float targetIntensity = 0.5f;

    public void setColors(int[] iArr, int i, boolean z) {
        this.dark = z;
        if (!Arrays.equals(this.colors, iArr)) {
            System.arraycopy(iArr, 0, this.colors, 0, 4);
            for (int i2 = 0; i2 < 4; i2++) {
                this.shaders[i2] = createGlow(this.colors[i2]);
            }
            this.bloomShader = createGlow(ColorUtils.blendARGB(this.colors[0], -1, 0.45f));
        }
        if (this.scrimShader == null || this.scrimColor != i) {
            this.scrimColor = i;
            this.scrimShader = new LinearGradient(0.0f, 0.0f, 0.0f, 1.0f, ColorUtils.setAlphaComponent(i, 0), ColorUtils.setAlphaComponent(i, 183), Shader.TileMode.CLAMP);
        }
        for (int i3 = 0; i3 < 4; i3++) {
            int[] iArr2 = this.particleColors;
            int[] iArr3 = this.colors;
            iArr2[i3] = z ? ColorUtils.blendARGB(-1, iArr3[i3], 0.3f) : ColorUtils.blendARGB(iArr3[i3], -16777216, 0.35f);
        }
    }

    private static RadialGradient createGlow(int i) {
        int length = GLOW_STOPS.length;
        int[] iArr = new int[length];
        for (int i2 = 0; i2 < length; i2++) {
            iArr[i2] = ColorUtils.setAlphaComponent(i, Math.round(GLOW_FALLOFF[i2] * 255.0f));
        }
        return new RadialGradient(0.0f, 0.0f, 100.0f, iArr, GLOW_STOPS, Shader.TileMode.CLAMP);
    }

    public void setParticlesEnabled(boolean z) {
        this.particlesEnabled = z;
    }

    public void setActive(boolean z) {
        this.active = z;
    }

    public void setAnimating(boolean z) {
        if (this.animating != z) {
            this.animating = z;
            this.lastFrameNs = 0L;
        }
    }

    public boolean isVisible() {
        return this.active || this.presence > 0.003f;
    }

    public boolean draw(Canvas canvas, RectF rectF, float f, RectF rectF2, float f2, float f3, float f4) {
        RectF rectF3;
        RadialGradient radialGradient;
        float f5;
        RectF rectF4 = rectF;
        long jElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
        long j = this.lastFrameNs;
        float fMin = j == 0 ? 0.0f : Math.min(0.05f, (jElapsedRealtimeNanos - j) / 1.0E9f);
        this.lastFrameNs = jElapsedRealtimeNanos;
        this.bounds.set(rectF4);
        this.cover.set(rectF2);
        this.coverRadius = f2;
        step(fMin, (1.0f - Utilities.clamp01(f4)) * f3);
        float fClamp01 = 1.0f - (Utilities.clamp01(f4) * 0.6f);
        float fCenterX = this.cover.isEmpty() ? rectF4.centerX() : this.cover.centerX();
        float fCenterY = this.cover.isEmpty() ? rectF4.centerY() : this.cover.centerY();
        int i = 0;
        int i2 = 1;
        if (this.presence <= 0.003f || rectF4.isEmpty() || this.shaders[0] == null) {
            return this.active != ((this.presence > 0.003f ? 1 : (this.presence == 0.003f ? 0 : -1)) > 0);
        }
        float fWidth = rectF4.width();
        float fHeight = rectF4.height();
        float[] fArr = this.dark ? BLOB_ALPHA_DARK : BLOB_ALPHA_LIGHT;
        float fCenterX2 = rectF4.centerX();
        float f6 = rectF4.top + (0.42f * fHeight);
        float f7 = (this.intensity * 0.45f) + 0.7f;
        int iFloor = (int) Math.floor(this.colorShift);
        float f8 = this.colorShift - iFloor;
        while (i < 4) {
            double d = (this.angle * BLOB_SPIN[i]) + BLOB_ANGLE[i];
            float fCos = fCenterX2 + (((float) Math.cos(d)) * BLOB_ORBIT_X[i] * fWidth);
            float fSin = f6 + (((float) Math.sin(d)) * BLOB_ORBIT_Y[i] * fHeight);
            int i3 = BLOB_BAND[i];
            if (i3 == 0) {
                f5 = this.bass;
            } else {
                f5 = i3 == i2 ? this.mid : this.high;
            }
            float f9 = BLOB_RADIUS[i] * fWidth;
            float f10 = this.pulse;
            float f11 = f9 * ((f10 * 0.05f) + 1.0f + (f5 * 0.1f));
            float fMin2 = Math.min(0.95f, (fArr[i] + (f10 * 0.08f) + (this.energy * 0.12f)) * f7) * this.presence * fClamp01;
            int i4 = i + iFloor;
            int i5 = iFloor;
            int i6 = i;
            drawBlob(canvas, rectF4, f, this.shaders[i4 % 4], fCos, fSin, f11, (1.0f - f8) * fMin2);
            if (f8 > 0.003f) {
                drawBlob(canvas, rectF, f, this.shaders[(i4 + 1) % 4], fCos, fSin, f11, fMin2 * f8);
            }
            i = i6 + 1;
            rectF4 = rectF;
            i2 = 1;
            iFloor = i5;
        }
        int i7 = iFloor;
        float f12 = this.pulse;
        if (f12 > 0.003f) {
            drawBlob(canvas, rectF, f, this.shaders[i7 % 4], fCenterX, fCenterY, 0.7f * fWidth * ((f12 * 0.12f) + 1.0f), f12 * 0.14f * this.presence * fClamp01);
        }
        float f13 = this.bloom;
        if (f13 <= 0.003f || (radialGradient = this.bloomShader) == null) {
            rectF3 = rectF;
        } else {
            rectF3 = rectF;
            drawBlob(canvas, rectF3, f, radialGradient, fCenterX2, f6, fWidth * (((1.0f - f13) * 0.9f) + 0.6f), f13 * 0.5f * this.presence * fClamp01);
        }
        if (this.particlePresence > 0.003f && this.particlesReady) {
            drawParticles(canvas);
        }
        this.matrix.setScale(1.0f, 0.48000002f * fHeight);
        this.matrix.postTranslate(0.0f, rectF3.top + (fHeight * 0.52f));
        this.scrimShader.setLocalMatrix(this.matrix);
        this.scrimPaint.setShader(this.scrimShader);
        this.scrimPaint.setAlpha((int) (this.presence * 255.0f));
        canvas.drawRoundRect(rectF3, f, f, this.scrimPaint);
        return needsFrame();
    }

    private void drawBlob(Canvas canvas, RectF rectF, float f, RadialGradient radialGradient, float f2, float f3, float f4, float f5) {
        if (f5 <= 0.0f || f4 <= 0.0f) {
            return;
        }
        float f6 = f4 / 100.0f;
        this.matrix.setScale(f6, f6);
        this.matrix.postTranslate(f2, f3);
        radialGradient.setLocalMatrix(this.matrix);
        this.blobPaint.setShader(radialGradient);
        this.blobPaint.setAlpha((int) (Math.min(1.0f, f5) * 255.0f));
        canvas.drawRoundRect(rectF, f, f, this.blobPaint);
    }

    private void drawParticles(Canvas canvas) {
        float f = (this.intensity * 0.2f) + 0.16f;
        for (int i = 0; i < 80; i++) {
            float fSin = ((float) Math.sin(((double) this.particleLife[i]) * 3.141592653589793d)) * this.particleBrightness[i];
            float f2 = this.particleSpark[i] * this.shimmer;
            float fMin = Math.min(1.0f, fSin * ((0.68f * f2) + f)) * this.particlePresence * this.presence;
            if (fMin >= 0.01f) {
                this.particlePaint.setColor(this.particleColors[i % 4]);
                this.particlePaint.setAlpha((int) (fMin * 255.0f));
                canvas.drawCircle(this.particleX[i], this.particleY[i], this.particleRadius[i] * ((f2 * 0.5f) + 1.0f), this.particlePaint);
            }
        }
    }

    private void spawnParticle(int i) {
        float fWidth = this.cover.width() / 2.0f;
        double dNextFloat = ((double) this.random.nextFloat()) * 3.141592653589793d * 2.0d;
        float fCos = (float) Math.cos(dNextFloat);
        float fSin = (float) Math.sin(dNextFloat);
        float fMax = Math.max(Math.abs(fCos), Math.abs(fSin));
        float fMin = Math.min(Math.abs(fCos), Math.abs(fSin)) / fMax;
        float f = (fWidth / fMax) - (((this.coverRadius * 0.3f) * fMin) * fMin);
        this.particleX[i] = this.cover.centerX() + (fCos * f);
        this.particleY[i] = this.cover.centerY() + (f * fSin);
        float fWidth2 = this.bounds.width() * coverScale() * ((this.random.nextFloat() * 0.08f) + 0.06f);
        this.particleVx[i] = fCos * fWidth2;
        this.particleVy[i] = fSin * fWidth2;
        this.particleLife[i] = 0.0f;
        this.particleDuration[i] = (this.random.nextFloat() * 2.0f) + 0.5f;
        this.particleBrightness[i] = (this.random.nextFloat() * 0.4f) + 0.6f;
    }

    private float coverScale() {
        return Math.max(0.4f, Math.min(1.0f, this.cover.width() / Math.max(1.0f, this.bounds.width())));
    }

    private void ensureParticles() {
        float f;
        if (this.particlesReady || this.cover.isEmpty() || this.bounds.isEmpty()) {
            return;
        }
        int i = 0;
        while (true) {
            if (i >= 80) {
                break;
            }
            spawnParticle(i);
            this.particleLife[i] = this.random.nextFloat();
            this.particleSpark[i] = 0.0f;
            this.particleRadius[i] = AndroidUtilities.dpf2(this.random.nextFloat() < 0.2f ? 1.5f : 0.9f);
            i++;
        }
        this.particlesReady = true;
        for (f = 0.0f; f < 3.0f; f += 0.033333335f) {
            stepParticles(0.033333335f, 1.0f);
        }
    }

    private void stepParticles(float f, float f2) {
        float fWidth = this.bounds.width();
        float f3 = (float) (18.84955592153876d / ((double) fWidth));
        float f4 = 0.65f * f * f2;
        this.particleTime += f4;
        float f5 = 0.07f * fWidth;
        float f6 = 0.05f * fWidth;
        float f7 = 1.0f;
        float f8 = 0.13f * fWidth * ((this.surge * 1.5f) + 1.0f);
        float f9 = -f;
        float fExp = (float) Math.exp(1.2f * f9);
        float fExp2 = (float) Math.exp(f9 / 0.14f);
        float fDpf2 = AndroidUtilities.dpf2(8.0f);
        int i = 0;
        while (i < 80) {
            float[] fArr = this.particleLife;
            float f10 = fArr[i] + ((this.particleDuration[i] * f4) / 1.4f);
            fArr[i] = f10;
            if (f10 >= f7 || !isInside(this.particleX[i], this.particleY[i], fDpf2)) {
                spawnParticle(i);
            } else {
                float f11 = this.particleX[i];
                float f12 = this.particleTime;
                flow(f11 + (0.04f * f12 * fWidth), this.particleY[i], f12, f3);
                float fCenterX = this.particleX[i] - this.cover.centerX();
                float fCenterY = this.particleY[i] - this.cover.centerY();
                i = i;
                fExp = fExp;
                float fMax = Math.max(1.0f, (float) Math.hypot(fCenterX, fCenterY));
                float[] fArr2 = this.particleVx;
                float f13 = fArr2[i];
                float[] fArr3 = this.flowForce;
                fArr2[i] = f13 + (((fArr3[0] * f5) + ((fCenterX / fMax) * f6)) * f4);
                float[] fArr4 = this.particleVy;
                float f14 = fArr4[i] + (((fArr3[1] * f5) + ((fCenterY / fMax) * f6)) * f4);
                fArr4[i] = f14;
                float fHypot = (float) Math.hypot(fArr2[i], f14);
                if (fHypot > f8) {
                    float f15 = (((fHypot - f8) * fExp) + f8) / fHypot;
                    float[] fArr5 = this.particleVx;
                    fArr5[i] = fArr5[i] * f15;
                    float[] fArr6 = this.particleVy;
                    fArr6[i] = fArr6[i] * f15;
                }
                float[] fArr7 = this.particleX;
                fArr7[i] = fArr7[i] + (this.particleVx[i] * f4);
                float[] fArr8 = this.particleY;
                fArr8[i] = fArr8[i] + (this.particleVy[i] * f4);
                float[] fArr9 = this.particleSpark;
                fArr9[i] = fArr9[i] * fExp2;
            }
            i++;
            fExp = fExp;
            f7 = 1.0f;
        }
    }

    private boolean isInside(float f, float f2, float f3) {
        RectF rectF = this.bounds;
        return f >= rectF.left - f3 && f <= rectF.right + f3 && f2 >= rectF.top - f3 && f2 <= rectF.bottom + f3;
    }

    private void flow(float f, float f2, float f3, float f4) {
        double d = f4;
        double d2 = 0.9d * d;
        double d3 = f;
        double d4 = f3;
        double d5 = (d2 * d3) + (d4 * 0.6d);
        double d6 = f2;
        double d7 = ((0.7d * d) * d6) - (0.45d * d4);
        double d8 = (1.7d * d * ((d3 * 0.6d) + (d6 * 0.8d))) + (d4 * 0.8d);
        double d9 = 0.85d * d;
        double dCos = (d2 * Math.cos(d5) * Math.cos(d7)) + (0.6d * d9 * Math.cos(d8));
        double dSin = (d * (-0.7d) * Math.sin(d5) * Math.sin(d7)) + (d9 * 0.8d * Math.cos(d8));
        double dMax = Math.max(1.0E-6d, Math.hypot(dCos, dSin));
        float[] fArr = this.flowForce;
        fArr[0] = (float) (dSin / dMax);
        fArr[1] = (float) ((-dCos) / dMax);
    }

    private void kickParticles(float f) {
        if (this.particlesReady) {
            float fWidth = this.bounds.width();
            for (int i = 0; i < 80; i++) {
                float fCenterX = this.particleX[i] - this.cover.centerX();
                float fCenterY = this.particleY[i] - this.cover.centerY();
                float fMax = Math.max(1.0f, (float) Math.hypot(fCenterX, fCenterY));
                float fNextFloat = 0.09f * fWidth * f * ((this.random.nextFloat() * 0.4f) + 0.6f);
                float[] fArr = this.particleVx;
                fArr[i] = fArr[i] + ((fCenterX / fMax) * fNextFloat);
                float[] fArr2 = this.particleVy;
                fArr2[i] = fArr2[i] + ((fCenterY / fMax) * fNextFloat);
            }
        }
    }

    private void sparkle(float f) {
        for (int i = 0; i < 80; i++) {
            this.particleSpark[i] = this.random.nextFloat() < f ? 1.0f : Math.max(this.particleSpark[i], 0.15f);
        }
    }

    private void step(float f, float f2) {
        float f3;
        float f4;
        float f5;
        float f6;
        RhythmTap current = this.animating ? RhythmTap.getCurrent() : null;
        float f7 = 0.0f;
        if (current != null) {
            this.state.clearEvents();
            current.read(this.state);
            float f8 = this.state.beat;
            if (f8 > 0.0f) {
                float fMin = Math.min(1.0f, f8 / 0.7f);
                float f9 = this.pulse;
                RhythmTap.State state = this.state;
                this.pulse = Math.max(f9, state.downbeat ? 0.75f * fMin : state.beat * 0.28f);
                float f10 = this.surge;
                RhythmTap.State state2 = this.state;
                this.surge = Math.max(f10, state2.downbeat ? fMin * 1.6f : state2.beat * 0.6f);
                RhythmTap.State state3 = this.state;
                if (!state3.downbeat) {
                    fMin = state3.beat * 0.35f;
                }
                kickParticles(fMin);
            }
            RhythmTap.State state4 = this.state;
            int i = state4.phrases;
            if (i > 0) {
                this.colorTarget += i;
            }
            float f11 = state4.hat;
            if (f11 > 0.35f) {
                this.shimmer = Math.max(this.shimmer, f11);
                sparkle(0.4f);
            }
            if (this.state.drop) {
                this.bloom = 1.0f;
                this.surge = Math.max(this.surge, 3.0f);
                this.shimmer = 1.0f;
                kickParticles(2.6f);
                sparkle(1.0f);
            }
            RhythmTap.State state5 = this.state;
            this.bpm = state5.bpm;
            f3 = state5.energy;
            f4 = state5.bass;
            f5 = state5.mid;
            f6 = state5.high;
            this.targetIntensity = state5.intensity;
        } else {
            f3 = 0.0f;
            f4 = 0.0f;
            f5 = 0.0f;
            f6 = 0.0f;
        }
        this.presence = approach(this.presence, this.active ? 1.0f : 0.0f, f, 0.25f, 0.25f);
        this.energy = approach(this.energy, f3, f, 0.12f, 0.35f);
        this.bass = approach(this.bass, f4, f, 0.04f, 0.22f);
        this.mid = approach(this.mid, f5, f, 0.08f, 0.3f);
        this.high = approach(this.high, f6, f, 0.05f, 0.25f);
        this.intensity = approach(this.intensity, this.animating ? this.targetIntensity : 0.5f, f, 1.2f, 1.2f);
        float f12 = -f;
        this.pulse *= (float) Math.exp(f12 / 0.22f);
        this.surge *= (float) Math.exp(f12 / 0.3f);
        this.bloom *= (float) Math.exp(f12 / 0.9f);
        this.shimmer *= (float) Math.exp(f12 / 0.12f);
        float fApproach = approach(this.colorShift, this.colorTarget, f, 0.7f, 0.7f);
        this.colorShift = fApproach;
        int i2 = this.colorTarget;
        if (i2 >= 4 && fApproach >= 4.0f) {
            this.colorTarget = i2 - 4;
            this.colorShift = fApproach - 4.0f;
        }
        float f13 = (this.intensity * 0.8f) + 0.6f;
        float f14 = this.particlePresence;
        if (!this.animating || !this.particlesEnabled) {
            f2 = 0.0f;
        }
        float fApproach2 = approach(f14, f2, f, 0.3f, 0.6f);
        this.particlePresence = fApproach2;
        if (fApproach2 > 0.003f) {
            ensureParticles();
            if (this.particlesReady && f > 0.0f) {
                stepParticles(f, ((this.surge * 0.6f) + 1.0f) * f13 * (this.animating ? 1.0f : 0.3f));
            }
        }
        if (this.animating) {
            float f15 = this.bpm;
            f7 = f15 > 0.0f ? ((f15 / 60.0f) / 16.0f) * f13 : (this.energy * 0.06f) + 0.02f;
        }
        float fApproach3 = approach(this.spin, f7, f, 0.6f, 0.6f);
        this.spin = fApproach3;
        this.angle = (float) ((((double) this.angle) + (((double) (((f * fApproach3) * ((this.surge * 1.6f) + 1.0f)) * 2.0f)) * 3.141592653589793d)) % 376.99111843077515d);
    }

    private boolean needsFrame() {
        if (this.animating) {
            return true;
        }
        return Math.abs(this.presence - (this.active ? 1.0f : 0.0f)) > 0.003f || this.pulse > 0.003f || this.energy > 0.003f || this.bass > 0.003f || this.mid > 0.003f || this.high > 0.003f || this.bloom > 0.003f || this.shimmer > 0.003f || this.particlePresence > 0.003f || Math.abs(this.colorShift - ((float) this.colorTarget)) > 0.003f || this.spin > 3.0E-4f;
    }

    private static float approach(float f, float f2, float f3, float f4, float f5) {
        if (f3 <= 0.0f) {
            return f;
        }
        if (f2 <= f) {
            f4 = f5;
        }
        return f + ((f2 - f) * (1.0f - ((float) Math.exp((-f3) / f4))));
    }
}

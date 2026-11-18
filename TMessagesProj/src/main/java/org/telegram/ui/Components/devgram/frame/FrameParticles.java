package org.telegram.ui.Components.devgram.frame;

import org.telegram.ui.Components.devgram.frame.FrameSpec;

/** Native port of the user-supplied Custom Profile frame geometry. */
public final class FrameParticles {
    private static final float CHAOS_LIFE = 0.6f;
    private static final float CHAOS_SIZE = 0.5f;
    private static final float CHAOS_WAY = 0.7f;
    private static final float FADE_OUT = 0.34f;
    private static final float GOLDEN_TIME = 0.618034f;
    private static final float GRAVITY_REACH = 1.2f;
    private static final float GROWTH = 0.35f;
    public static final float LIFE = 2.6f;
    private static final float LIGHT_UP = 0.18f;
    private static final float PLASTIC_X = 0.7548777f;
    private static final float PLASTIC_Y = 0.5698403f;
    private static final float SIZE_MIN = 0.05f;
    public static final int STRIDE = 5;
    private static final float SWIRL_REACH = 0.5f;
    private static final float SWIRL_TURNS = 1.2f;
    private static final float TWINKLE_TIMES = 3.0f;

    private static float frac(float f) {
        float f2 = f % 1.0f;
        return f2 < 0.0f ? f2 + 1.0f : f2;
    }

    public static float random(int i, int i2) {
        int i3 = (i * 374761393) + (i2 * 668265263);
        int i4 = (i3 ^ (i3 >>> 13)) * 1274126177;
        return ((i4 ^ (i4 >>> 16)) >>> 8) / 1.6777216E7f;
    }

    private FrameParticles() {
    }

    public static int place(FrameSpec.Layer layer, float f, float f2, float f3, float f4, float f5, float[] fArr) {
        int i;
        int i2;
        float f6;
        float f7;
        float f8;
        float f9;
        int i3;
        int i4 = 0;
        if (layer == null || (i = layer.repeat) <= 0 || f2 <= 0.0f || fArr == null || fArr.length < i * 5) {
            return 0;
        }
        float fLife = life(layer.speed);
        float f10 = f2 / 2.0f;
        float fPart = part(layer.chaos);
        float f11 = 0.5f;
        float fPart2 = part(layer.swirl) * f5 * 0.5f;
        float f12 = (layer.gravity / 100.0f) * f5 * 1.2f;
        float fPart3 = part(layer.twinkle);
        while (i4 < i) {
            float f13 = i4;
            float fFrac = frac((f / Math.max(SIZE_MIN, (((CHAOS_LIFE * fPart) * spread(i4, 1)) + 1.0f) * fLife)) + frac((GOLDEN_TIME * f13) + f11));
            float fFrac2 = (f3 - f10) + (frac((PLASTIC_X * f13) + f11) * f2);
            float fFrac3 = (f4 - f10) + (frac((f13 * PLASTIC_Y) + f11) * f2);
            float f14 = fFrac2 - f3;
            float f15 = fFrac3 - f4;
            float f16 = fLife;
            float fSqrt = (float) Math.sqrt((f14 * f14) + (f15 * f15));
            if (layer.flow == 0 || fSqrt <= 1.0E-4f) {
                i2 = i;
                f6 = 1.2f;
                f7 = 1.0f;
                double radians = Math.toRadians(layer.course + (layer.scatter * spread(i4, 2)));
                float fSin = (float) Math.sin(radians);
                f8 = (float) (-Math.cos(radians));
                f9 = fSin;
            } else {
                float f17 = layer.flow == 2 ? -1.0f : 1.0f;
                f6 = 1.2f;
                f7 = 1.0f;
                i2 = i;
                double radians2 = Math.toRadians(layer.course + (layer.scatter * spread(i4, 2)));
                float fCos = (float) Math.cos(radians2);
                float fSin2 = (float) Math.sin(radians2);
                float f18 = (f14 / fSqrt) * f17;
                float f19 = (f15 / fSqrt) * f17;
                f9 = (f18 * fCos) - (f19 * fSin2);
                f8 = (f18 * fSin2) + (f19 * fCos);
            }
            float fSpread = ((CHAOS_WAY * fPart * spread(i4, 3)) + f7) * f5;
            float f20 = fFrac2 + (f9 * fSpread * fFrac);
            float f21 = fFrac3 + (fSpread * f8 * fFrac);
            if (fPart2 > 0.0f) {
                i3 = 4;
                float fSin3 = fPart2 * fFrac * ((float) Math.sin(((((random(i4, 4) * 2.0f) + f6) * fFrac) + random(i4, 5)) * 6.283185307179586d));
                f20 += (-f8) * fSin3;
                f21 += f9 * fSin3;
            } else {
                i3 = 4;
            }
            int i5 = i4 * 5;
            fArr[i5] = f20;
            fArr[i5 + 1] = f21 + (f12 * fFrac * fFrac);
            fArr[i5 + 2] = layer.turn + (layer.twist * fFrac * sign(i4, 6));
            fArr[i5 + 3] = fade(fFrac) * twinkle(fPart3, fFrac, random(i4, 7));
            fArr[i5 + i3] = Math.max(SIZE_MIN, ((fFrac * GROWTH) + 0.65f) * ((fPart * 0.5f * spread(i4, 8)) + f7));
            i4++;
            i = i2;
            fLife = f16;
            f11 = 0.5f;
        }
        return i;
    }

    public static float life(int i) {
        return 260.0f / Math.max(1, i);
    }

    public static float fade(float f) {
        float fClampF = Style.clampF(f, 0.0f, 1.0f);
        return Style.clampF(Math.min(fClampF / LIGHT_UP, (1.0f - fClampF) / FADE_OUT), 0.0f, 1.0f);
    }

    public static float twinkle(float f, float f2, float f3) {
        if (f <= 0.0f) {
            return 1.0f;
        }
        return 1.0f - ((Style.clampF(f, 0.0f, 1.0f) * 0.75f) * (1.0f - ((((float) Math.cos(((f2 * TWINKLE_TIMES) + f3) * 6.283185307179586d)) * 0.5f) + 0.5f)));
    }

    public static float spread(int i, int i2) {
        return (random(i, i2) * 2.0f) - 1.0f;
    }

    private static float sign(int i, int i2) {
        return random(i, i2) < 0.5f ? -1.0f : 1.0f;
    }

    private static float part(int i) {
        return Style.clampF(i / 100.0f, 0.0f, 1.0f);
    }
}

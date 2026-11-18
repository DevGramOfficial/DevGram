package org.telegram.ui.Components.devgram.frame;


import org.telegram.ui.Components.devgram.frame.FrameSpec;
import java.util.List;

/** Native port of the user-supplied Custom Profile frame geometry. */
public final class FrameLive {
    private static final float BEAT_SHOW = 0.09f;
    private static final long CYCLE_MS = 3600000;

    private static float peak(int i) {
        if (i != 1) {
            return i != 3 ? 0.5f : 0.2f;
        }
        return 0.0f;
    }

    private FrameLive() {
    }

    public static FrameSpec.Layer now(FrameSpec.Layer layer, float seconds) {
        if (layer == null || layer.alive.isEmpty()) {
            return layer;
        }
        float fUptimeMillis = seconds;
        FrameSpec.Builder builderAlive = new FrameSpec.Builder(layer).bake(false).alive((List<FrameSpec.Alive>) null);
        for (int i = 0; i < layer.alive.size(); i++) {
            FrameSpec.Alive alive = layer.alive.get(i);
            apply(builderAlive, layer, alive, alive.span * wave(alive, fUptimeMillis));
        }
        return builderAlive.build();
    }

    private static float wave(FrameSpec.Alive alive, float f) {
        float f2 = ((f * alive.speed) / 60.0f) + (alive.phase / 100.0f);
        float fFloor = f2 - ((float) Math.floor(f2));
        int i = alive.wave;
        if (i == 1) {
            return fFloor;
        }
        if (i == 2) {
            return fFloor < 0.5f ? 0.0f : 1.0f;
        }
        if (i != 3) {
            return (float) (0.5d - (Math.cos(fFloor * 6.283185307179586d) * 0.5d));
        }
        return fFloor < 0.2f ? fFloor / 0.2f : 1.0f - ((fFloor - 0.2f) / 0.8f);
    }

    public static boolean beating(int i, int i2, int i3, float seconds) {
        float fMax = Math.max(1, Math.min(FrameSpec.LIVE_SPEED_MAX, i));
        float fUptimeMillis = (((seconds) * fMax) / 60.0f) + (i3 / 100.0f);
        float fFloor = fUptimeMillis - ((float) Math.floor(fUptimeMillis));
        if (i2 < 0 || i2 > 3) {
            i2 = 0;
        }
        float fPeak = fFloor - peak(i2);
        if (fPeak < 0.0f) {
            fPeak += 1.0f;
        }
        return (fPeak * 60.0f) / fMax <= BEAT_SHOW;
    }

    public static boolean showing(FrameSpec.Layer layer, float seconds) {
        FrameSpec.Queue queue = layer == null ? null : layer.queue;
        if (queue == null) {
            return true;
        }
        if (queue.speed <= 0) {
            return queue.branch == 0;
        }
        long jPasses = passes(queue.speed, queue.wave, queue.phase, seconds);
        return (queue.random ? pick(jPasses, queue.count) : (int) Math.floorMod(jPasses, (long) queue.count)) == queue.branch;
    }

    private static long passes(int i, int i2, int i3, float seconds) {
        return (long) Math.floor(((((seconds) * i) / 60.0d) + (i3 / 100.0d)) - peak(i2));
    }

    private static int pick(long j, int i) {
        if (i <= 1) {
            return 0;
        }
        int iSpread = spread(j, i);
        return iSpread == spread(j - 1, i) ? (iSpread + 1) % i : iSpread;
    }

    private static int spread(long j, int i) {
        long j2 = j * (-7046029254386353131L);
        long j3 = (j2 ^ (j2 >>> 29)) * (-4658895280553007687L);
        return (int) Math.floorMod(j3 ^ (j3 >>> 32), i);
    }

    private static void apply(FrameSpec.Builder builder, FrameSpec.Layer layer, FrameSpec.Alive alive, float f) {
        int iRound = Math.round(f);
        switch (alive.field) {
            case 1:
                builder.width(layer.width + iRound);
                break;
            case 2:
                builder.offset(layer.offset + iRound);
                break;
            case 3:
                builder.at(layer.at + (f / 100.0f));
                break;
            case 4:
                builder.scale(layer.scale + iRound);
                break;
            case 5:
                builder.repeat(layer.repeat + iRound);
                break;
            case 6:
                builder.spin(layer.spin + iRound);
                break;
            case 7:
                builder.turn(layer.turn + iRound);
                break;
            case 8:
                builder.x(layer.x + (f / 100.0f));
                break;
            case 9:
                builder.y(layer.y + (f / 100.0f));
                break;
            case 10:
                builder.spread(layer.spread + iRound);
                break;
            case 11:
                builder.speed(layer.speed + iRound);
                break;
            case 12:
                builder.course(layer.course + iRound);
                break;
            case 13:
                builder.field(layer.field + iRound);
                break;
            case 14:
                builder.scatter(layer.scatter + iRound);
                break;
            case FrameSpec.F_SWIRL /* 15 */:
                builder.swirl(layer.swirl + iRound);
                break;
            case 16:
                builder.chaos(layer.chaos + iRound);
                break;
            case FrameSpec.F_GRAVITY /* 17 */:
                builder.gravity(layer.gravity + iRound);
                break;
            case FrameSpec.F_TWIST /* 18 */:
                builder.twist(layer.twist + iRound);
                break;
            case FrameSpec.F_TWINKLE /* 19 */:
                builder.twinkle(layer.twinkle + iRound);
                break;
            case 20:
                builder.orbit(layer.orbit + iRound);
                break;
            case 21:
                builder.glow(layer.glow + iRound);
                break;
            case 22:
                builder.glowRadius(layer.glowRadius + iRound);
                break;
            case 23:
                builder.visible(layer.visible + iRound);
                break;
        }
    }
}

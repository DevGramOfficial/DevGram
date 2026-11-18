package org.telegram.ui.Components.devgram.blocks;

import java.util.Locale;
import java.util.Map;

/* Adapted from the user-supplied Custom Profile plugin by @RoflPlugins.
 * See docs/PROFILE_STUDIO_REFERENCE_AUDIT.md for the reference and restoration evidence. */
final class BlockAnimation {
    /* JADX INFO: Access modifiers changed from: private */
    public static float clamp(float f, float f2, float f3) {
        return f < f2 ? f2 : f > f3 ? f3 : f;
    }

    private BlockAnimation() {
    }

    static Spec parse(Map<String, String> map) {
        String str;
        String lowerCase = text(map, "animation", "").trim().toLowerCase(Locale.US);
        String lowerCase2 = text(map, "animationname", "").trim().toLowerCase(Locale.US);
        long jTime = time(text(map, "animationduration", ""), 1600L);
        long jTime2 = time(text(map, "animationdelay", ""), 0L);
        String lowerCase3 = text(map, "animationtiming", "ease-in-out").trim().toLowerCase(Locale.US);
        String lowerCase4 = text(map, "animationiterations", "1").trim().toLowerCase(Locale.US);
        String lowerCase5 = text(map, "animationdirection", "normal").trim().toLowerCase(Locale.US);
        if (lowerCase.length() <= 0 || "none".equals(lowerCase)) {
            str = lowerCase2;
        } else {
            String[] strArrSplit = lowerCase.split("\\s+");
            int length = strArrSplit.length;
            int i = 0;
            int i2 = 0;
            while (i < length) {
                String[] strArr = strArrSplit;
                String str2 = strArr[i];
                if (lowerCase2.length() == 0 && animationName(str2)) {
                    lowerCase2 = str2;
                } else {
                    String str3 = lowerCase2;
                    if (str2.endsWith("ms") || str2.endsWith("s")) {
                        int i3 = i2 + 1;
                        if (i2 == 0) {
                            jTime = time(str2, jTime);
                        } else {
                            jTime2 = time(str2, jTime2);
                        }
                        i2 = i3;
                    } else if (easing(str2)) {
                        lowerCase3 = str2;
                    } else if ("infinite".equals(str2) || number(str2)) {
                        lowerCase4 = str2;
                    } else if ("normal".equals(str2) || "reverse".equals(str2) || "alternate".equals(str2) || "alternate-reverse".equals(str2)) {
                        lowerCase5 = str2;
                    }
                    lowerCase2 = str3;
                }
                i++;
                strArrSplit = strArr;
            }
            str = lowerCase2;
        }
        long j = jTime2;
        String str4 = lowerCase3;
        String str5 = lowerCase5;
        if (!animationName(str)) {
            return Spec.NONE;
        }
        float fDecimal = decimal(text(map, "animationdistance", "6"), 6.0f);
        float fClamp = clamp(decimal(text(map, "animationintensity", "1"), 1.0f), 0.0f, 4.0f);
        float fDecimal2 = decimal(text(map, "animationphase", "0"), 0.0f);
        if (text(map, "animationphase", "").contains("%")) {
            fDecimal2 /= 100.0f;
        }
        return new Spec(str, Math.max(80L, Math.min(60000L, jTime)), j, str4, "infinite".equals(lowerCase4) ? -1 : Math.max(1, Math.min(100, Math.round(decimal(lowerCase4, 1.0f)))), str5, fDecimal, fClamp, fDecimal2);
    }

    static boolean animationName(String str) {
        return "float,pulse,breathe,spin,sway,bounce,heartbeat,reveal,drift,fade,slide,slide-up,zoom,track-swap,".contains(str + ",");
    }

    private static boolean easing(String str) {
        return "linear,ease,ease-in,ease-out,ease-in-out,".contains(str + ",");
    }

    private static boolean number(String str) {
        try {
            Float.parseFloat(str);
            return true;
        } catch (Throwable unused) {
            return false;
        }
    }

    private static String text(Map<String, String> map, String str, String str2) {
        String str3 = map.get(str);
        return str3 == null ? str2 : str3;
    }

    private static long time(String str, long j) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.US);
        if (lowerCase.endsWith("ms")) {
            return Math.round(Double.parseDouble(lowerCase.substring(0, lowerCase.length() - 2)));
        }
        if (lowerCase.endsWith("s")) {
            return Math.round(Double.parseDouble(lowerCase.substring(0, lowerCase.length() - 1)) * 1000.0d);
        }
        return lowerCase.length() == 0 ? j : Math.round(Double.parseDouble(lowerCase));
    }

    private static float decimal(String str, float f) {
        try {
            return Float.parseFloat(str.trim().toLowerCase(Locale.US).replace("dp", "").replace("%", ""));
        } catch (Throwable unused) {
            return f;
        }
    }

    static final class Spec {
        static final Spec NONE = new Spec("", 1, 0, "linear", 0, "normal", 0.0f, 0.0f, 0.0f);
        final long delay;
        final String direction;
        final float distance;
        final long duration;
        final String easing;
        final float intensity;
        final int iterations;
        final String name;
        final float phase;

        Spec(String str, long j, long j2, String str2, int i, String str3, float f, float f2, float f3) {
            this.name = str;
            this.duration = j;
            this.delay = j2;
            this.easing = str2;
            this.iterations = i;
            this.direction = str3;
            this.distance = f;
            this.intensity = f2;
            this.phase = f3;
        }

        Frame frame(long j) {
            float f=0;
            float f2=1;
            float f3=0;
            float fMax;
            float f4=0;
            float f5=0;
            float f6=0;
            float f7=0;
            if (this.name.length() == 0) {
                return Frame.STILL;
            }
            float f8 = j - this.delay;
            float f9 = this.phase;
            long j2 = this.duration;
            double d = f8 + (f9 * j2);
            if (d < 0.0d) {
                return new Frame(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, true);
            }
            double dMax = d / j2;
            int i = this.iterations;
            boolean z = i < 0 || dMax < ((double) i);
            if (!z) {
                dMax = Math.max(0.0d, i - 1.0E-6d);
            }
            int iFloor = (int) Math.floor(dMax);
            float fFloor = (float) (dMax - Math.floor(dMax));
            boolean z2 = "reverse".equals(this.direction) || "alternate-reverse".equals(this.direction);
            if (("alternate".equals(this.direction) || "alternate-reverse".equals(this.direction)) && (iFloor & 1) == 1) {
                z2 = !z2;
            }
            if (z2) {
                fFloor = 1.0f - fFloor;
            }
            float fEase = ease(fFloor, this.easing);
            double d2 = fEase * 3.141592653589793d * 2.0d;
            float fSin = (float) Math.sin(d2);
            if (!"float".equals(this.name)) {
                if (!"drift".equals(this.name)) {
                    if (!"pulse".equals(this.name)) {
                        if ("breathe".equals(this.name)) {
                            float f10 = (fSin * 0.5f) + 0.5f;
                            float f11 = this.intensity;
                            float f12 = (0.025f * f10 * f11) + 1.0f;
                            fEase = 1.0f - (((1.0f - f10) * 0.16f) * f11);
                            f2 = f12;
                        } else {
                            if ("spin".equals(this.name)) {
                                f6 = fEase * 360.0f * this.intensity;
                            } else if ("sway".equals(this.name)) {
                                f6 = fSin * 4.0f * this.intensity;
                            } else if ("bounce".equals(this.name)) {
                                f4 = (-((float) Math.abs(Math.sin(d2)))) * this.distance;
                                f5 = this.intensity;
                            } else if ("heartbeat".equals(this.name)) {
                                fMax = (Math.max(peak(fFloor, 0.18f, 0.07f), peak(fFloor, 0.36f, 0.055f) * 0.65f) * 0.09f * this.intensity) + 1.0f;
                            } else {
                                if ("reveal".equals(this.name)) {
                                    f3 = (1.0f - fEase) * this.distance;
                                    f2 = (0.06f * fEase) + 0.94f;
                                    f = 0.0f;
                                    f7 = 0.0f;
                                    return new Frame(f, f3, f2, f2, f7, BlockAnimation.clamp(fEase, 0.0f, 1.0f), z);
                                }
                                if ("fade".equals(this.name)) {
                                    f = 0.0f;
                                } else if ("slide".equals(this.name) || "track-swap".equals(this.name)) {
                                    float f13 = (1.0f - fEase) * this.distance * this.intensity;
                                    if ("track-swap".equals(this.name)) {
                                        f2 = (0.03f * fEase) + 0.97f;
                                        f = f13;
                                        f3 = 0.0f;
                                        f7 = 0.0f;
                                    } else {
                                        f = f13;
                                    }
                                } else if ("slide-up".equals(this.name)) {
                                    f3 = (1.0f - fEase) * this.distance * this.intensity;
                                    f = 0.0f;
                                    f2 = 1.0f;
                                    f7 = 0.0f;
                                } else if ("zoom".equals(this.name)) {
                                    f2 = (0.18f * fEase) + 0.82f;
                                } else {
                                    fEase = 1.0f;
                                    f = 0.0f;
                                }
                                return new Frame(f, f3, f2, f2, f7, BlockAnimation.clamp(fEase, 0.0f, 1.0f), z);
                            }
                            f7 = f6;
                            fEase = 1.0f;
                            f = 0.0f;
                            f3 = 0.0f;
                            f2 = 1.0f;
                            return new Frame(f, f3, f2, f2, f7, BlockAnimation.clamp(fEase, 0.0f, 1.0f), z);
                        }
                        f = 0.0f;
                        f3 = 0.0f;
                        f7 = 0.0f;
                        return new Frame(f, f3, f2, f2, f7, BlockAnimation.clamp(fEase, 0.0f, 1.0f), z);
                    }
                    fMax = (fSin * 0.045f * this.intensity) + 1.0f;
                    f2 = fMax;
                    fEase = 1.0f;
                    f = 0.0f;
                    f3 = 0.0f;
                    f7 = 0.0f;
                    return new Frame(f, f3, f2, f2, f7, BlockAnimation.clamp(fEase, 0.0f, 1.0f), z);
                }
                f = fSin * this.distance * this.intensity;
                fEase = 1.0f;
                f3 = 0.0f;
                f2 = 1.0f;
                f7 = 0.0f;
                return new Frame(f, f3, f2, f2, f7, BlockAnimation.clamp(fEase, 0.0f, 1.0f), z);
            }
            f4 = (-fSin) * this.distance;
            f5 = this.intensity;
            f3 = f4 * f5;
            fEase = 1.0f;
            f = 0.0f;
            f2 = 1.0f;
            f7 = 0.0f;
            return new Frame(f, f3, f2, f2, f7, BlockAnimation.clamp(fEase, 0.0f, 1.0f), z);
        }

        private static float peak(float f, float f2, float f3) {
            float f4 = (f - f2) / f3;
            return (float) Math.exp((-f4) * f4);
        }

        private static float ease(float f, String str) {
            float fPow;
            float fClamp = BlockAnimation.clamp(f, 0.0f, 1.0f);
            if ("linear".equals(str)) {
                return fClamp;
            }
            if ("ease-in".equals(str)) {
                return fClamp * fClamp;
            }
            if ("ease-out".equals(str)) {
                float f2 = 1.0f - fClamp;
                fPow = f2 * f2;
            } else {
                if ("ease".equals(str)) {
                    return fClamp * fClamp * (3.0f - (fClamp * 2.0f));
                }
                if (fClamp < 0.5f) {
                    return 2.0f * fClamp * fClamp;
                }
                fPow = ((float) Math.pow((fClamp * (-2.0f)) + 2.0f, 2.0d)) / 2.0f;
            }
            return 1.0f - fPow;
        }
    }

    static final class Frame {
        static final Frame STILL = new Frame(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, false);
        final boolean active;
        final float alpha;
        final float rotation;
        final float scaleX;
        final float scaleY;
        final float x;
        final float y;

        Frame(float f, float f2, float f3, float f4, float f5, float f6, boolean z) {
            this.x = f;
            this.y = f2;
            this.scaleX = f3;
            this.scaleY = f4;
            this.rotation = f5;
            this.alpha = f6;
            this.active = z;
        }
    }
}

package org.telegram.messenger.devgram.utils.ui;

import androidx.core.graphics.ColorUtils;
public abstract class ColorTones {
    private static final double[] lab = new double[3];

    public static boolean isLightSurface(int i) {
        return getTone(i) > 50.0f;
    }

    public static float getTone(int i) {
        double[] dArr = lab;
        ColorUtils.colorToLAB(i, dArr);
        return (float) dArr[0];
    }

    public static int withTone(int i, float f, double d) {
        double[] dArr = lab;
        ColorUtils.colorToLAB(i, dArr);
        double dHypot = Math.hypot(dArr[1], dArr[2]);
        if (dHypot > d && dHypot > 0.0d) {
            double d2 = d / dHypot;
            dArr[1] = dArr[1] * d2;
            dArr[2] = dArr[2] * d2;
        }
        return ColorUtils.LABToColor(f, dArr[1], dArr[2]);
    }
}

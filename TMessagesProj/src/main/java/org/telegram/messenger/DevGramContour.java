package org.telegram.messenger;

/** Bounded normalized contours; resample long strokes by path distance, not event timing. */
public final class DevGramContour {
    private DevGramContour() { }
    public static float[] resample(float[] source, int maximum) {
        if (source == null || source.length < 6 || source.length % 2 != 0 || maximum < 3) return new float[0];
        float[] clean = new float[source.length];
        for (int i = 0; i < source.length; i++) {
            if (Float.isNaN(source[i]) || Float.isInfinite(source[i])) return new float[0];
            clean[i] = Math.max(0, Math.min(1, source[i]));
        }
        int count = clean.length / 2;
        if (count <= maximum) return clean;
        double[] lengths = new double[count + 1];
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            lengths[i + 1] = lengths[i] + Math.hypot(clean[next * 2] - clean[i * 2], clean[next * 2 + 1] - clean[i * 2 + 1]);
        }
        if (lengths[count] == 0) return new float[0];
        float[] result = new float[maximum * 2];
        int segment = 0;
        for (int i = 0; i < maximum; i++) {
            double at = lengths[count] * i / maximum;
            while (segment + 1 < count && lengths[segment + 1] < at) segment++;
            double distance = lengths[segment + 1] - lengths[segment];
            float progress = distance == 0 ? 0 : (float)((at - lengths[segment]) / distance);
            int next = (segment + 1) % count;
            result[i * 2] = clean[segment * 2] + (clean[next * 2] - clean[segment * 2]) * progress;
            result[i * 2 + 1] = clean[segment * 2 + 1] + (clean[next * 2 + 1] - clean[segment * 2 + 1]) * progress;
        }
        return result;
    }
}

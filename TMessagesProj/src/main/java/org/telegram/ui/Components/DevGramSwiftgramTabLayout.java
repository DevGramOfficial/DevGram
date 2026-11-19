package org.telegram.ui.Components;

/** Port of tabBarSystemItemsLayout / tabBarLensSelectionFrame in Swiftgram b460f1d8. Values are dp. */
public final class DevGramSwiftgramTabLayout {
    public static final class Layout {
        public final float width;
        public final float[] x, widths;
        Layout(float width, float[] x, float[] widths) { this.width = width; this.x = x; this.widths = widths; }
    }

    public static Layout measure(float[] titles, float available, boolean accessory, float scale) {
        int count = titles.length;
        if (count == 0) return new Layout(8, new float[0], new float[0]);
        boolean flexible = count > 3 || accessory;
        float side = count > 4 ? 15 : 24;
        float spacing = flexible ? -1 : 32;
        float gaps = Math.max(0, count - 1);
        float[] lengths = new float[count];
        float maximum = 0, total = 0;
        for (int i = 0; i < count; i++) {
            lengths[i] = Math.max(54, titles[i] + (flexible ? 12 : 0));
            maximum = Math.max(maximum, lengths[i]);
            total += lengths[i];
        }
        float width = flexible ? available : Math.min(available,
                (float) Math.floor((side * 2 + gaps * spacing + count * maximum) * scale + .5f) / scale);
        float initial = width - side * 2;
        float clampedMax = Math.min(Math.max(maximum, 0), initial);
        float equal = (initial - gaps * (spacing < 0 ? 2 : spacing)) / count;
        float fitted = Math.min(clampedMax, equal);
        float inset = side, items = initial;
        if (!flexible) {
            java.util.Arrays.fill(lengths, fitted);
            total = fitted * count;
        } else {
            boolean equalLengths;
            float capped = -1;
            if (gaps > 0 && (initial - total) / gaps < 14) {
                equalLengths = false; capped = 14;
            } else if (54 >= clampedMax) {
                equalLengths = true;
            } else {
                equalLengths = clampedMax <= equal; capped = 18;
            }
            if (capped >= 0) { inset = Math.min(side, capped); items = width - inset * 2; }
            if (equalLengths) {
                float length = count < 2 ? equal : Math.max(fitted, items / count - (inset - 4));
                java.util.Arrays.fill(lengths, length);
                total = length * count;
            } else if (total < items) {
                float factor = items / total;
                for (int i = 0; i < count; i++) lengths[i] *= factor;
                total = items;
            }
        }
        float overflow = total - items + (flexible ? 0 : gaps * spacing);
        if (overflow > 0) {
            float reduction = overflow / count;
            for (int i = 0; i < count; i++) lengths[i] -= reduction;
            total = items;
            // Android accessibility fonts can exceed the iOS title-size range.
            // Keep every hit region positive without changing the source fixtures.
            boolean invalid = false;
            float positive = 0;
            for (float length : lengths) { invalid |= length <= 0; positive += Math.max(1, length); }
            if (invalid) for (int i = 0; i < count; i++) lengths[i] = Math.max(1, lengths[i]) * items / positive;
        }
        float gap = gaps > 0 ? (flexible ? (items - total) / gaps : spacing) : 0;
        float outset = inset - 4, position = inset;
        float[] x = new float[count], widths = new float[count];
        for (int i = 0; i < count; i++) {
            x[i] = position - outset;
            widths[i] = lengths[i] + outset * 2;
            position += lengths[i] + gap;
        }
        return new Layout(width, x, widths);
    }

    public static float[] lens(Layout layout, int selected, float height) {
        if (layout.widths.length == 0) return new float[]{0, Math.min(56, layout.width)};
        selected = Math.max(0, Math.min(layout.widths.length - 1, selected));
        float width = layout.widths[selected], x = layout.x[selected];
        float itemHeight = height - 8;
        if (width < itemHeight) {
            float growth = (float) Math.floor((itemHeight * 1.2f - width) * -.5f);
            x += growth; width -= growth * 2;
        }
        float length = width + 8;
        return new float[]{Math.max(0, Math.min(x - 4, layout.width - length)), length};
    }

    private DevGramSwiftgramTabLayout() {}
}

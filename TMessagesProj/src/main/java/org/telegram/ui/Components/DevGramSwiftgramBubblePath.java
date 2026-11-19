package org.telegram.ui.Components;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

/** ChatMessageBubbleImages.swift's round-rect form, lower quadratic wing and elliptical tail cutout. */
public final class DevGramSwiftgramBubblePath {
    private final Path wing = new Path(), cutout = new Path(), clip = new Path();
    private final RectF body = new RectF(), ellipse = new RectF();
    private final Matrix mirror = new Matrix();
    private final float[] radii = new float[8];

    public void build(Path result, Rect bounds, float density, float mainRadius, float auxiliaryRadius,
                      boolean outgoing, boolean topNear, boolean bottomNear, boolean buttonsBelow, boolean hideTail, boolean media) {
        float padding = 2 * density;
        body.set(bounds.left + padding, bounds.top + padding, bounds.right - (media ? 2 : 8) * density, bounds.bottom - padding);
        if (body.width() <= 0 || body.height() <= 0) { result.rewind(); return; }
        float maxRadius = Math.min(mainRadius, Math.min(body.width(), body.height()) / 2);
        float minRadius = Math.min(auxiliaryRadius, maxRadius);
        for (int i = 0; i < 8; i++) radii[i] = maxRadius;
        if (topNear) radii[2] = radii[3] = minRadius;
        if (bottomNear || buttonsBelow) radii[4] = radii[5] = minRadius;
        if (buttonsBelow) radii[6] = radii[7] = minRadius;
        result.rewind(); result.addRoundRect(body, radii, Path.Direction.CW);
        if (!media && !bottomNear && !buttonsBelow && !hideTail) {
            // Reference fixedMainDiameter=33; bottomEllipse=(24,16,27,17), topEllipse=(33,14,23,21).
            float right = body.right, bottom = body.bottom;
            wing.rewind();
            if (mainRadius >= 14 * density) {
                wing.moveTo(right - 9 * density, bottom - 8.5f * density);
                wing.quadTo(right - 9 * density, bottom, right + 4.5f * density, bottom);
                wing.quadTo(right + 18 * density, bottom, right + 18 * density, bottom - 8.5f * density);
                wing.close();
            } else wing.addRect(right - 11 * density, bottom - 8.5f * density, right + 18 * density, bottom, Path.Direction.CW);
            result.op(wing, Path.Op.UNION);
            wing.rewind(); wing.addRect(right - 16.5f * density, bottom - 17 * density, right, bottom - 8 * density, Path.Direction.CW);
            result.op(wing, Path.Op.UNION);
            ellipse.set(right, bottom - 19 * density, right + 23 * density, bottom + 2 * density);
            cutout.rewind(); cutout.addOval(ellipse, Path.Direction.CW); result.op(cutout, Path.Op.DIFFERENCE);
            clip.rewind(); clip.addRect(bounds.left, bounds.top, bounds.right, bounds.bottom, Path.Direction.CW); result.op(clip, Path.Op.INTERSECT);
        }
        if (!outgoing) {
            mirror.setScale(-1, 1, bounds.exactCenterX(), bounds.exactCenterY()); result.transform(mirror);
        }
    }
}

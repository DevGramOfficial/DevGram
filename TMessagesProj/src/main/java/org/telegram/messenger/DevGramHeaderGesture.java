package org.telegram.messenger;

/** Geometry-only two-finger gesture. Pointer IDs are tracked by the editor, not their indices. */
public final class DevGramHeaderGesture {
    private final float distance, initialX, initialY, initialRotation;
    private float lastAngle, rotationDelta;
    private boolean angleReliable = true;
    private final float centerX, centerY;
    public float scaleX, scaleY, rotation, panX, panY;

    public DevGramHeaderGesture(float x1, float y1, float x2, float y2, float sx, float sy, float rotate) {
        distance = Math.max(1, (float) Math.hypot(x2 - x1, y2 - y1));
        centerX = (x1 + x2) / 2; centerY = (y1 + y2) / 2;
        initialX = clamp(sx, 10, 300); initialY = clamp(sy, 10, 300); initialRotation = normalize(rotate);
        lastAngle = angle(x1, y1, x2, y2);
        scaleX = initialX; scaleY = initialY; rotation = initialRotation;
    }
    public void update(float x1, float y1, float x2, float y2) {
        if (!Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(x2) || !Float.isFinite(y2)) return;
        float span = (float) Math.hypot(x2 - x1, y2 - y1);
        float ratio = clamp(span / distance, Math.max(10 / initialX, 10 / initialY), Math.min(300 / initialX, 300 / initialY));
        scaleX = initialX * ratio; scaleY = initialY * ratio;
        // Near-coincident fingers still resize, but their angle cannot cause a 180-degree flip.
        if (span < Math.max(1, distance * .08f)) angleReliable = false;
        else {
            float next = angle(x1, y1, x2, y2);
            if (angleReliable) rotationDelta += normalize(next - lastAngle);
            lastAngle = next; angleReliable = true;
        }
        rotation = normalize(initialRotation + rotationDelta);
        panX = (x1 + x2) / 2 - centerX; panY = (y1 + y2) / 2 - centerY;
    }
    private static float angle(float x1, float y1, float x2, float y2) { return (float) Math.toDegrees(Math.atan2(y2 - y1, x2 - x1)); }
    private static float normalize(float value) { if (!Float.isFinite(value)) return 0; value %= 360; return value > 180 ? value - 360 : value < -180 ? value + 360 : value; }
    private static float clamp(float value, float low, float high) { return Math.max(low, Math.min(high, Float.isFinite(value) ? value : low)); }
}

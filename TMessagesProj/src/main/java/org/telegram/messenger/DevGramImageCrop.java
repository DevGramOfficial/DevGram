package org.telegram.messenger;

/** Resolution-independent cover crop. Focus is measured over the available image overflow. */
public final class DevGramImageCrop {
    private float x = 500, y = 500, zoom = 100;
    private float viewportWidth, viewportHeight, imageWidth, imageHeight;
    public float left, top, width, height;

    private static float clamp(float value, float min, float max) {
        return Float.isNaN(value) || Float.isInfinite(value) ? min : Math.max(min, Math.min(max, value));
    }

    public void set(int x, int y, int zoom) {
        this.x = clamp(x, 0, 1000);
        this.y = clamp(y, 0, 1000);
        this.zoom = clamp(zoom, 100, 400);
        update();
    }

    public int focusX() { return Math.round(x); }
    public int focusY() { return Math.round(y); }
    public int scale() { return Math.round(zoom); }

    public void layout(float viewportWidth, float viewportHeight, float imageWidth, float imageHeight) {
        this.viewportWidth = Math.max(0, viewportWidth);
        this.viewportHeight = Math.max(0, viewportHeight);
        this.imageWidth = Math.max(1, imageWidth);
        this.imageHeight = Math.max(1, imageHeight);
        update();
    }

    private void update() {
        if (imageWidth <= 0 || imageHeight <= 0) return;
        float cover = Math.max(viewportWidth / imageWidth, viewportHeight / imageHeight) * zoom / 100;
        width = Math.max(viewportWidth, imageWidth * cover);
        height = Math.max(viewportHeight, imageHeight * cover);
        left = -(width - viewportWidth) * x / 1000;
        top = -(height - viewportHeight) * y / 1000;
    }

    public void move(float dx, float dy) {
        if (width > viewportWidth) x = clamp(x - dx * 1000 / (width - viewportWidth), 0, 1000);
        if (height > viewportHeight) y = clamp(y - dy * 1000 / (height - viewportHeight), 0, 1000);
        update();
    }

    public void zoom(float factor, float focusX, float focusY) {
        if (width <= 0 || height <= 0 || factor <= 0 || Float.isNaN(factor) || Float.isInfinite(factor)) return;
        float sourceX = (focusX - left) / width, sourceY = (focusY - top) / height;
        zoom = clamp(zoom * factor, 100, 400);
        update();
        if (width > viewportWidth) x = clamp((sourceX * width - focusX) * 1000 / (width - viewportWidth), 0, 1000);
        if (height > viewportHeight) y = clamp((sourceY * height - focusY) * 1000 / (height - viewportHeight), 0, 1000);
        update();
    }
}

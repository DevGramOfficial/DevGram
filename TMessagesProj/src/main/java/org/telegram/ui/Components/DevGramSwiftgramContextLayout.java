package org.telegram.ui.Components;

/** Extracted preview and menu placement, constrained to the Android safe viewport. Pixels in/out. */
public final class DevGramSwiftgramContextLayout {
    public static final class Result {
        public final float menuX, menuY, previewX, previewY, previewScale;
        Result(float menuX, float menuY, float previewX, float previewY, float scale) {
            this.menuX = menuX; this.menuY = menuY; this.previewX = previewX; this.previewY = previewY; previewScale = scale;
        }
    }

    public static Result place(float width, float top, float bottom, float gap,
                               float sourceX, float sourceY, float sourceWidth, float sourceHeight,
                               float menuX, float menuWidth, float menuHeight) {
        float available = Math.max(1, bottom - top);
        float previewRoom = Math.max(1, available - Math.min(menuHeight, available - gap) - gap);
        float scale = Math.min(1, Math.min((width - 2 * gap) / Math.max(1, sourceWidth), previewRoom / Math.max(1, sourceHeight)));
        scale = Math.max(.001f, scale);
        float previewWidth = sourceWidth * scale, previewHeight = sourceHeight * scale;
        float px = clamp(sourceX, gap, width - gap - previewWidth);
        float py = clamp(sourceY, top, bottom - previewHeight);
        float my = py + previewHeight + gap;
        if (my + menuHeight > bottom) {
            if (py - gap - menuHeight >= top) my = py - gap - menuHeight;
            else { my = Math.max(top, bottom - menuHeight); py = Math.max(top, my - gap - previewHeight); }
        }
        return new Result(clamp(menuX, gap, width - gap - menuWidth), my, px, py, scale);
    }

    /** Tall extracted content stays readable inside the shared scrolling surface. */
    public static Result placeScrollable(float width, float top, float bottom, float gap,
                               float sourceX, float sourceY, float sourceWidth, float sourceHeight,
                               float menuX, float menuWidth, float menuHeight) {
        float scale = Math.min(1, Math.max(.001f, (width - 2 * gap) / Math.max(1, sourceWidth)));
        float previewHeight = sourceHeight * scale;
        if (previewHeight + gap + menuHeight <= bottom - top) {
            return place(width, top, bottom, gap, sourceX, sourceY, sourceWidth, sourceHeight, menuX, menuWidth, menuHeight);
        }
        return new Result(clamp(menuX, gap, width - gap - menuWidth), top + previewHeight + gap,
                clamp(sourceX, gap, width - gap - sourceWidth * scale), top, scale);
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(value, Math.max(min, max))); }
    private DevGramSwiftgramContextLayout() {}
}

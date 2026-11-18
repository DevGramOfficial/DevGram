package org.telegram.messenger;

/** Coordinates used by both the surface renderer and the direction/center pads. */
public final class DevGramSurfaceGeometry {
    private DevGramSurfaceGeometry() { }
    public static float[] line(float width, float height, int centerX, int centerY, int angle, int spread) {
        float x = width * Math.max(0, Math.min(100, centerX)) / 100f;
        float y = height * Math.max(0, Math.min(100, centerY)) / 100f;
        float half = radius(width, height, spread) / 2f;
        double radians = Math.toRadians(angle);
        float dx = (float) Math.cos(radians) * half, dy = (float) Math.sin(radians) * half;
        return new float[]{x - dx, y - dy, x + dx, y + dy};
    }
    public static float radius(float width, float height, int spread) {
        return Math.max(1, Math.max(width, height) * Math.max(20, Math.min(200, spread)) / 100f);
    }
    /** Fade direction uses 0=up and scales each axis to the surface bounds. */
    public static float[] fadeLine(float width,float height,int centerX,int centerY,int angle,int spread) {
        float x=width*Math.max(0,Math.min(100,centerX))/100f,y=height*Math.max(0,Math.min(100,centerY))/100f;
        double radians=Math.toRadians(angle);float share=Math.max(20,Math.min(200,spread))/200f;
        float dx=(float)Math.sin(radians)*width*share,dy=-(float)Math.cos(radians)*height*share;
        return new float[]{x-dx,y-dy,x+dx,y+dy};
    }
}

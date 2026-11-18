package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import java.util.Arrays;

/** Bounded data-flow graph, not Java reflection or executable plugin code. */
public final class DevGramFrameStudioRenderer {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] values = new float[512], opacity = new float[12], rotation = new float[12], scale = new float[12];
    private final int[] hue = new int[12];
    private final float[] hsv = new float[3];
    private final DevGramProfilePainter shape = new DevGramProfilePainter();
    private final org.telegram.ui.Components.devgram.frame.DevGramTypedFrameRenderer typed = new org.telegram.ui.Components.devgram.frame.DevGramTypedFrameRenderer();
    public void setSources(android.view.View host,org.telegram.messenger.ImageReceiver avatar,long uid,int account){typed.sources(host,avatar,uid,account);}
    public void release(){typed.release();}

    public void draw(Canvas canvas, float left, float top, float size, int avatarShape, JSONObject document, boolean motion) {
        if(document.has("frameGraph")){typed.draw(canvas,left,top,size,avatarShape,document,motion);return;}
        typed.release();
        shape.setAvatarDocument(document);
        JSONArray layers = document.optJSONArray("layers"), nodes = document.optJSONArray("nodes");
        if (layers == null || size <= 0) return;
        float time = motion ? SystemClock.uptimeMillis() / 1000f : 0;
        Arrays.fill(values, 0); Arrays.fill(opacity, 1); Arrays.fill(rotation, 0); Arrays.fill(scale, 1); Arrays.fill(hue, -1);
        if (nodes != null) for (int i = 0; i < Math.min(512, nodes.length()); i++) {
            JSONObject node = nodes.optJSONObject(i); if (node == null) continue;
            int id = node.optInt("id", -1), layer = node.optInt("layer"); if (id < 0 || id >= 512 || layer < 0 || layer >= 12) continue;
            JSONArray inputs = node.optJSONArray("inputs"); int first = inputs != null ? inputs.optInt(0, -1) : -1, second = inputs != null ? inputs.optInt(1, -1) : -1;
            float param = node.optInt("value", 100) / 100f;
            float a = first >= 0 && first < 512 ? values[first] : param, b = second >= 0 && second < 512 ? values[second] : param;
            String type = node.optString("type"); float value = a;
            switch (type) {
                case "time": value = time * param; break; case "sine": value = (float) Math.sin(a); break;
                case "multiply": value = a * b; break; case "add": value = a + b; break;
                case "opacity": opacity[layer] = Math.max(0, Math.min(1, Math.abs(a))); break;
                case "rotation": rotation[layer] = a * 360; break; case "scale": scale[layer] = Math.max(.1f, Math.min(3, Math.abs(a))); break;
                case "color": hue[layer] = ((int) (a * 360) % 360 + 360) % 360; break;
            }
            values[id] = Float.isNaN(value) || Float.isInfinite(value) ? 0 : Math.max(-10000, Math.min(10000, value));
        }
        float cx = left + size / 2, cy = top + size / 2;
        for (int i = 0; i < Math.min(12, layers.length()); i++) {
            JSONObject layer = layers.optJSONObject(i); if (layer == null) continue;
            int color = 0xff000000 | layer.optInt("color", 0xffffff);
            if (hue[i] >= 0) { Color.colorToHSV(color, hsv); hsv[0] = hue[i]; color = Color.HSVToColor(hsv); }
            int alpha = (int) (255 * Math.max(0, Math.min(100, layer.optInt("alpha", 100))) / 100f * opacity[i]);
            float thickness = Math.max(1, size * (.008f + layer.optInt("size", 50) / 100f * .045f));
            float angle = layer.optInt("rotation") + rotation[i] + time * layer.optInt("speed", 20);
            float inset = size * .015f + i * size * .012f;
            paint.setColor(color); paint.setAlpha(alpha); paint.setStrokeWidth(thickness); paint.setStyle(Paint.Style.STROKE);
            canvas.save(); canvas.scale(scale[i], scale[i], cx, cy); canvas.rotate(angle, cx, cy);
            String type = layer.optString("type");
            if (type.equals("ring") || type.equals("ribbon")) {
                if (type.equals("ribbon")) { paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{size * .09f, size * .05f}, 0)); }
                canvas.drawPath(shape.avatarPath(left + inset + thickness / 2, top + inset + thickness / 2, size - inset * 2 - thickness, avatarShape), paint);
                paint.setPathEffect(null);
            } else if (type.equals("glow")) {
                for (int pass = 4; pass >= 1; pass--) {
                    paint.setStrokeWidth(thickness * pass * 1.4f); paint.setAlpha(alpha / (pass * 3));
                    canvas.drawPath(shape.avatarPath(left + inset + thickness * 2, top + inset + thickness * 2, size - inset * 2 - thickness * 4, avatarShape), paint);
                }
            } else {
                paint.setStyle(Paint.Style.FILL); int count = type.equals("sticker") ? 4 : type.equals("stars") ? 10 : 18;
                for (int dot = 0; dot < count; dot++) {
                    double theta = dot * Math.PI * 2 / count; float radius = size / 2 - inset - thickness;
                    float x = cx + (float) Math.cos(theta) * radius, y = cy + (float) Math.sin(theta) * radius;
                    float r = thickness * (type.equals("stars") ? 1.1f : .65f);
                    if (type.equals("sticker")) { paint.setTextSize(size * .13f); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText(layer.optString("text", "✦"), x, y + size * .045f, paint); }
                    else if (type.equals("stars")) { canvas.drawRect(x - r * 1.8f, y - r * .3f, x + r * 1.8f, y + r * .3f, paint); canvas.drawRect(x - r * .3f, y - r * 1.8f, x + r * .3f, y + r * 1.8f, paint); }
                    else canvas.drawCircle(x, y, r, paint);
                }
            }
            canvas.restore();
        }
        paint.setAlpha(255); paint.setStyle(Paint.Style.FILL);
    }

    public void graph(Canvas canvas, float width, float height, JSONObject document) {
        JSONArray nodes = document.optJSONArray("nodes"); if (nodes == null) return;
        int count = Math.min(512, nodes.length()); float dx = width / 4f, dy = AndroidUtilities.dp(54);
        for (int i = 0; i < count; i++) {
            JSONObject node = nodes.optJSONObject(i); if (node == null) continue;
            float x = dx * (i % 4) + dx / 2, y = dy * (i / 4) + dy / 2;
            JSONArray inputs = node.optJSONArray("inputs");
            paint.setColor(0x667c93ff); paint.setStrokeWidth(AndroidUtilities.dp(2));
            if (inputs != null) for (int k = 0; k < inputs.length(); k++) for (int j = 0; j < i; j++) {
                if (nodes.optJSONObject(j).optInt("id") == inputs.optInt(k)) canvas.drawLine(dx * (j % 4) + dx / 2, dy * (j / 4) + dy / 2, x, y, paint);
            }
            paint.setColor(0xff465b9d); rect.set(x - dx * .38f, y - dy * .33f, x + dx * .38f, y + dy * .33f);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);
            paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(AndroidUtilities.dp(10));
            canvas.drawText("#" + node.optInt("id") + " " + node.optString("type"), x, y + AndroidUtilities.dp(4), paint);
        }
    }
}

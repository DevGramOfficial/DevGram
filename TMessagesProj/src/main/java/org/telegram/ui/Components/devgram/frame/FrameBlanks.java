package org.telegram.ui.Components.devgram.frame;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import java.util.HashMap;
import java.util.Map;

/** Native port of the user-supplied Custom Profile frame geometry. */
public final class FrameBlanks {
    public static final String SCHEME = "blank:";
    public static final String RIM = "blank:rim";
    public static final String RING = "blank:ring";
    public static final String STRIPE = "blank:stripe";
    public static final String BEAD = "blank:bead";
    public static final String DOT = "blank:dot";
    public static final String GEM = "blank:gem";
    public static final String STAR = "blank:star";
    public static final String SPARK = "blank:spark";
    public static final String[] ALL = {RIM, RING, STRIPE, BEAD, DOT, GEM, STAR, SPARK};
    private static final Map<String, Bitmap> CACHE = new HashMap();

    private FrameBlanks() {
    }

    public static boolean is(String str) {
        return str != null && str.startsWith(SCHEME);
    }

    public static String label(String str) {
        if(str!=null&&str.startsWith("asset:"))return "Своя картинка";
        if (str == null || str.length() == 0) {
            return Loc.t("выбрать", "choose");
        }
        if (is(str)) {
            String strSubstring = str.substring(6);
            if (RIM.endsWith(strSubstring)) {
                return Loc.t("лента", "ribbon");
            }
            if (BEAD.endsWith(strSubstring)) {
                return Loc.t("бусина", "bead");
            }
            if (DOT.endsWith(strSubstring)) {
                return Loc.t("точка", "dot");
            }
            if (GEM.endsWith(strSubstring)) {
                return Loc.t("камень", "gem");
            }
            if (STAR.endsWith(strSubstring)) {
                return Loc.t("звезда", "star");
            }
            if (SPARK.endsWith(strSubstring)) {
                return Loc.t("искра", "spark");
            }
            if (STRIPE.endsWith(strSubstring)) {
                return Loc.t("штрих", "stroke");
            }
            return RING.endsWith(strSubstring) ? Loc.t("кольцо", "ring") : strSubstring;
        }
        int iMax = Math.max(str.lastIndexOf(47), str.lastIndexOf(92));
        if (iMax >= 0) {
            str = str.substring(iMax + 1);
        }
        if (str.length() <= 18) {
            return str;
        }
        return str.substring(0, 17) + "…";
    }

    private static Bitmap star() {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(-1);
        Path path = new Path();
        float f = 64 / 2.0f;
        for (int i = 0; i < 10; i++) {
            float f2 = i % 2 == 0 ? f - 1.0f : 0.42f * f;
            double d = (i * 0.6283185307179586d) - 1.5707963267948966d;
            float fCos = (((float) Math.cos(d)) * f2) + f;
            float fSin = (((float) Math.sin(d)) * f2) + f;
            if (i == 0) {
                path.moveTo(fCos, fSin);
            } else {
                path.lineTo(fCos, fSin);
            }
        }
        path.close();
        canvas.drawPath(path, paint);
        return bitmapCreateBitmap;
    }

    private static Bitmap spark() {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(-1);
        float f = 64;
        float f2 = f / 2.0f;
        float f3 = 0.14f * f;
        Path path = new Path();
        path.moveTo(f2, 0.0f);
        float f4 = f2 + f3;
        float f5 = f2 - f3;
        path.quadTo(f4, f5, f, f2);
        path.quadTo(f4, f4, f2, f);
        path.quadTo(f5, f4, 0.0f, f2);
        path.quadTo(f5, f5, f2, 0.0f);
        path.close();
        canvas.drawPath(path, paint);
        return bitmapCreateBitmap;
    }

    private static Bitmap stripe() {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(24, 48, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(-1);
        Path path = new Path();
        float f = 24;
        path.moveTo(0.62f * f, 0.0f);
        path.lineTo(f, 0.0f);
        float f2 = 48;
        path.lineTo(f * 0.38f, f2);
        path.lineTo(0.0f, f2);
        path.close();
        canvas.drawPath(path, paint);
        return bitmapCreateBitmap;
    }

    private static Bitmap ring() {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(8, 48, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(-1);
        canvas.drawRect(0.0f, 0.0f, 8, 48, paint);
        return bitmapCreateBitmap;
    }

    public static void clear() {
        Map<String, Bitmap> map = CACHE;
        synchronized (map) {
            map.clear();
        }
    }

    public static Bitmap bitmap(String str) {
        if (!is(str)) {
            return null;
        }
        Map<String, Bitmap> map = CACHE;
        synchronized (map) {
            Bitmap bitmap = map.get(str);
            if (bitmap != null && !bitmap.isRecycled()) {
                return bitmap;
            }
            Bitmap bitmapDraw = draw(str);
            if (bitmapDraw != null) {
                map.put(str, bitmapDraw);
            }
            return bitmapDraw;
        }
    }

    private static Bitmap draw(String str) {
        try {
            if (RIM.equals(str)) {
                return rim();
            }
            if (BEAD.equals(str)) {
                return bead();
            }
            if (DOT.equals(str)) {
                return dot();
            }
            if (GEM.equals(str)) {
                return gem();
            }
            if (STAR.equals(str)) {
                return star();
            }
            if (SPARK.equals(str)) {
                return spark();
            }
            if (STRIPE.equals(str)) {
                return stripe();
            }
            if (RING.equals(str)) {
                return ring();
            }
            return null;
        } catch (Throwable th) {
            org.telegram.messenger.FileLog.e(th);
            return null;
        }
    }

    private static Bitmap rim() {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(8, 48, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        float f = 48;
        paint.setShader(new LinearGradient(0.0f, 0.0f, 0.0f, f, new int[]{-6645094, -1, -4934476, -7566196}, new float[]{0.0f, 0.35f, 0.75f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0.0f, 0.0f, 8, f, paint);
        return bitmapCreateBitmap;
    }

    private static Bitmap bead() {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        float f = 64;
        paint.setShader(new RadialGradient(f * 0.38f, f * 0.34f, f * 0.66f, new int[]{-1, -2302756, -7434610}, new float[]{0.0f, 0.45f, 1.0f}, Shader.TileMode.CLAMP));
        float f2 = f / 2.0f;
        canvas.drawCircle(f2, f2, f2 - 1.0f, paint);
        return bitmapCreateBitmap;
    }

    private static Bitmap dot() {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(-1);
        float f = 32 / 2.0f;
        canvas.drawCircle(f, f, f - 1.0f, paint);
        return bitmapCreateBitmap;
    }

    private static Bitmap gem() {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        Path path = new Path();
        float f = 64;
        float f2 = f / 2.0f;
        path.moveTo(f2, 1.0f);
        float f3 = f - 1.0f;
        path.lineTo(f3, f2);
        path.lineTo(f2, f3);
        path.lineTo(1.0f, f2);
        path.close();
        paint.setShader(new LinearGradient(0.0f, 0.0f, f, f, new int[]{-1, -3618616, -7303024}, new float[]{0.0f, 0.5f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
        paint.setColor(1728053247);
        Path path2 = new Path();
        path2.moveTo(f2, 1.0f);
        path2.lineTo(f3, f2);
        path2.lineTo(f2, f2);
        path2.close();
        canvas.drawPath(path2, paint);
        return bitmapCreateBitmap;
    }
}

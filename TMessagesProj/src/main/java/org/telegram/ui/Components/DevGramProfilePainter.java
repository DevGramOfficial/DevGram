package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DevGramProfileStyle;
import org.telegram.messenger.MessagesController;

/** Shared native renderer: the editor preview and the actual profile use identical drawing. */
public final class DevGramProfilePainter implements org.telegram.messenger.DevGramCustomizationMemory.Client {
    private final float units;
    public DevGramProfilePainter() { this(AndroidUtilities.density); }
    public DevGramProfilePainter(float units) { this.units = units; }
    private float px(float value) { return units * value; }
    public static final int[][] PALETTES = {
        {0xff172557, 0xff5077cf, 0xff79d8eb}, {0xff241744, 0xff9a5ac9, 0xfff4b2dc},
        {0xff123c3b, 0xff329a89, 0xffb8e9ae}, {0xff492040, 0xffc15883, 0xffffc1a0},
        {0xff422a18, 0xffb57838, 0xffffd888}, {0xff171c2e, 0xff5c648d, 0xffbbc4f7},
        {0xff16364e, 0xff358eaf, 0xffbcf2ed}, {0xff321642, 0xffa249bd, 0xffed99ff}
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private Shader background, aurora, ring;
    private int lastPalette = -1, lastMode = -1;
    private float lastWidth, lastHeight, ringSize;
    private int ringPalette = -1;
    private long lastPowerCheck;
    private int lastPowerRevision=Integer.MIN_VALUE;
    private boolean animate;
    private org.json.JSONObject avatarDocument;
    private final Paint avatarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.graphics.Bitmap avatarMask;
    @Override public long bytes(){return avatarMask==null||avatarMask.isRecycled()?0:avatarMask.getAllocationByteCount();}
    @Override public int pool(){return org.telegram.messenger.DevGramCustomizationMemory.PAINTER;}
    @Override public void trim(boolean critical){if(avatarMask!=null){avatarMask.recycle();avatarMask=null;avatarMaskKey=null;}}

    private String avatarMaskKey;
    public void setAvatarDocument(org.json.JSONObject document) { avatarDocument = document; }

    public void invalidateMotion(){lastPowerCheck=0;}
    public boolean motion() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastPowerCheck > 1500 || lastPowerCheck == 0 || lastPowerRevision!=org.telegram.messenger.DevGramBadges.styleVersion) {
            lastPowerRevision=org.telegram.messenger.DevGramBadges.styleVersion;
            lastPowerCheck = now;
            animate = MessagesController.getGlobalMainSettings().getBoolean("devgramProfileMotion", true);
            try {
                Context context = ApplicationLoader.applicationContext;
                PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                animate &= power == null || !power.isPowerSaveMode();
                animate &= Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0;
            } catch (Exception ignored) {}
        }
        return animate;
    }
    public boolean frameMotion(){return motion()&&!MessagesController.getGlobalMainSettings().getBoolean("devgramBakeProfile",false);}

    public void background(Canvas canvas, float width, float height, DevGramProfileStyle.Style style) {
        int mode = style.get("background"), palette = style.get("palette");
        if (mode == 0 || width <= 0 || height <= 0) return;
        int[] colors = PALETTES[palette];
        if (lastPalette != palette || lastMode != mode || lastWidth != width || lastHeight != height) {
            lastPalette = palette; lastMode = mode; lastWidth = width; lastHeight = height;
            background = new LinearGradient(0, 0, width, height, colors, null, Shader.TileMode.MIRROR);
            aurora = new RadialGradient(width * .5f, height * .3f, Math.max(width, height) * .7f,
                    new int[]{(colors[2] & 0xffffff) | 0x99000000, colors[1] & 0xffffff}, null, Shader.TileMode.CLAMP);
        }
        paint.setShader(mode == 1 ? null : background);
        paint.setColor(colors[0]);
        paint.setAlpha(255);
        float t = motion() ? SystemClock.uptimeMillis() / 7000f : 0;
        if (mode >= 3) {
            matrix.reset();
            matrix.setRotate((float) Math.sin(t * .4f) * 25, width / 2, height / 2);
            matrix.postTranslate((float) Math.sin(t) * width * .2f, (float) Math.cos(t * .7f) * height * .1f);
            background.setLocalMatrix(matrix);
        } else background.setLocalMatrix(null);
        canvas.drawRect(0, 0, width, height, paint);
        if (mode >= 3) {
            matrix.setTranslate((float) Math.sin(t * .6f) * width * .35f, (float) Math.cos(t * .8f) * height * .3f);
            aurora.setLocalMatrix(matrix);
            paint.setShader(aurora);
            canvas.drawRect(0, 0, width, height, paint);
        }
        if (mode == 4) {
            canvas.save();
            canvas.rotate(180, width / 2, height / 2);
            paint.setAlpha(125);
            canvas.drawRect(0, 0, width, height, paint);
            canvas.restore();
        }
        paint.setShader(null);
        paint.setColor(Color.BLACK);
        paint.setAlpha(style.get("dim") * 255 / 100);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setAlpha(255);
    }

    public void pattern(Canvas canvas, float width, float height, DevGramProfileStyle.Style style) {
        int mode = style.get("pattern");
        if (mode == 0) return;
        float t = motion() && mode > 1 ? SystemClock.uptimeMillis() / 1200f : 0;
        detail.setShader(null); detail.setColor(0x38ffffff); detail.setStyle(Paint.Style.FILL);
        if (mode == 1 || mode == 2) {
            for (int i = 0; i < (mode == 1 ? 70 : 28); i++) {
                float x = ((i * .6180339f) % 1) * width;
                float y = ((i * .381966f + .13f) % 1) * height;
                float r = px(mode == 1 ? 1 : 1.5f + (float) Math.sin(i * 3.1f + t) * .7f);
                if (mode == 2) {
                    detail.setAlpha(55 + (int) (55 * (.5f + .5f * Math.sin(t + i))));
                    canvas.drawRect(x - r * 2, y - r * .35f, x + r * 2, y + r * .35f, detail);
                    canvas.drawRect(x - r * .35f, y - r * 2, x + r * .35f, y + r * 2, detail);
                } else canvas.drawCircle(x, y, r, detail);
            }
        } else {
            detail.setStyle(Paint.Style.STROKE); detail.setStrokeWidth(px(.7f));
            if (mode == 3) {
                for (int row = 0; row < 7; row++) {
                    path.reset();
                    for (int step = 0; step <= 24; step++) {
                        float x = width * step / 24f;
                        float y = height * row / 6f + (float) Math.sin(step * .25f + row * .8f + t * .2f) * px(16);
                        if (step == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    }
                    canvas.drawPath(path, detail);
                }
            } else {
                float gap = px(32);
                for (float x = 0; x < width; x += gap) canvas.drawLine(x, 0, x, height, detail);
                for (float y = 0; y < height; y += gap) canvas.drawLine(0, y, width, y, detail);
            }
            detail.setStyle(Paint.Style.FILL);
        }
    }

    public Path avatarPath(float left, float top, float size, int shape) {
        path.reset(); bounds.set(left, top, left + size, top + size);
        if (shape <= 3) {
            float radius = shape == 1 ? size * (avatarDocument == null ? .24f : avatarDocument.optInt("avatarRadius", 18) / 72f) : shape == 2 ? size * .37f : shape == 3 ? 0 : size / 2;
            path.addRoundRect(bounds, radius, radius, Path.Direction.CW); return path;
        }
        org.json.JSONArray points = avatarDocument == null ? null : avatarDocument.optJSONArray("customShape");
        int count = shape == 4 ? 6 : shape == 5 ? 5 : shape == 6 ? 10 : shape == 7 ? 64 : shape == 8 ? 64 : points == null ? 0 : points.length();
        if (count < 3) { path.addOval(bounds,Path.Direction.CW); return path; }
        float round = avatarDocument == null ? 0 : avatarDocument.optInt("avatarRound") / 100f;
        for (int i=0;i<count;i++) {
            double angle = -Math.PI / 2 + i * Math.PI * 2 / count;
            float x,y;
            if(shape==9){org.json.JSONArray point=points.optJSONArray(i);x=point==null?.5f:point.optInt(0)/1000f;y=point==null?.5f:point.optInt(1)/1000f;}
            else if(shape==7){double a=i*Math.PI*2/count;x=.5f+(float)(16*Math.pow(Math.sin(a),3))/34f;y=.48f-(float)(13*Math.cos(a)-5*Math.cos(2*a)-2*Math.cos(3*a)-Math.cos(4*a))/34f;}
            else {float radius=shape==6&&i%2==1?.23f:shape==8?.42f+.07f*(float)Math.cos(angle*8):.48f;x=.5f+(float)Math.cos(angle)*radius;y=.5f+(float)Math.sin(angle)*radius;}
            if(round>0&&shape!=9){x=x*(1-round)+(.5f+(float)Math.cos(angle)*.48f)*round;y=y*(1-round)+(.5f+(float)Math.sin(angle)*.48f)*round;}
            if(i==0)path.moveTo(left+x*size,top+y*size);else path.lineTo(left+x*size,top+y*size);
        }
        path.close();
        return path;
    }

    /** Shared image clipping, opacity, dimming and soft alpha edge for profile and previews. */
    public void avatar(Canvas canvas,float left,float top,float size,int shape,org.json.JSONObject doc,Runnable draw) {
        setAvatarDocument(doc);int alpha=doc.optInt("avatarAlpha",100)*255/100;
        int save=canvas.saveLayerAlpha(left-1,top-1,left+size+1,top+size+1,alpha,Canvas.ALL_SAVE_FLAG);
        canvas.clipPath(avatarPath(left,top,size,shape));draw.run();
        int dim=doc.optInt("avatarDim");
        if(dim>0){avatarPaint.setColor(dim*255/100<<24);canvas.drawRect(left,top,left+size,top+size,avatarPaint);}
        int fade=doc.optInt("avatarFade");
        if(fade>0){
            String key=shape+":"+doc.optInt("avatarRadius",18)+":"+doc.optInt("avatarRound")+":"+fade+":"+doc.optInt("avatarFadeRadius",50)+":"+doc.optJSONArray("customShape");
            if(avatarMask!=null&&avatarMask.isRecycled())avatarMask=null;
            org.telegram.messenger.DevGramCustomizationMemory.touch(this);
            if(avatarMask==null&&!org.telegram.messenger.DevGramCustomizationMemory.allow(this,256L*256*4)){canvas.restoreToCount(save);return;}
            if(avatarMask==null||!key.equals(avatarMaskKey)){
                if(avatarMask!=null)avatarMask.recycle();
                avatarMaskKey=key;
                try{avatarMask=android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888);}catch(OutOfMemoryError pressure){avatarMask=null;canvas.restoreToCount(save);return;}Canvas mask=new Canvas(avatarMask);
                Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.WHITE);
                float radius=2+doc.optInt("avatarFadeRadius",50)*.22f;ink.setMaskFilter(new android.graphics.BlurMaskFilter(radius,android.graphics.BlurMaskFilter.Blur.NORMAL));
                float inset=fade*.15f;mask.save();mask.translate(inset,inset);mask.drawPath(avatarPath(0,0,256-inset*2,shape),ink);mask.restore();
            }
            avatarPaint.setColor(Color.WHITE);avatarPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            canvas.drawBitmap(avatarMask,null,new RectF(left,top,left+size,top+size),avatarPaint);avatarPaint.setXfermode(null);
        }
        canvas.restoreToCount(save);
    }

    public void frame(Canvas canvas, float left, float top, float size, DevGramProfileStyle.Style style) {
        frame(canvas,left,top,size,style,frameMotion());
    }
    public void frame(Canvas canvas,float left,float top,float size,DevGramProfileStyle.Style style,boolean motion) {
        int frame = style.get("frame"), palette = style.get("palette");
        if (frame == 0 || size <= 0) return;
        int[] colors = PALETTES[palette];
        float stroke = Math.max(px(1.4f), size * .025f);
        float inset = stroke * 1.5f;
        float cx = left + size / 2, cy = top + size / 2;
        if (ring == null || ringSize != size || ringPalette != palette) {
            ringSize = size; ringPalette = palette;
            ring = new SweepGradient(0, 0, new int[]{colors[2], colors[1], 0xffffffff, colors[1], colors[2]}, null);
        }
        float angle = motion && frame >= 3 ? SystemClock.uptimeMillis() / 50f % 360 : 0;
        matrix.reset(); matrix.setRotate(angle); matrix.postTranslate(cx, cy); ring.setLocalMatrix(matrix);
        detail.setShader(frame == 1 ? null : ring); detail.setColor(colors[2]);
        detail.setAlpha(255); detail.setStyle(Paint.Style.STROKE); detail.setStrokeWidth(stroke);
        canvas.drawPath(avatarPath(left + inset, top + inset, size - inset * 2, style.get("shape")), detail);
        if (frame >= 3) {
            detail.setAlpha(85); detail.setStrokeWidth(stroke * .45f);
            canvas.drawPath(avatarPath(left + inset * 2.3f, top + inset * 2.3f, size - inset * 4.6f, style.get("shape")), detail);
            detail.setStyle(Paint.Style.FILL); detail.setShader(null); detail.setColor(Color.WHITE);
            for (int i = 0; i < (frame == 3 ? 3 : 8); i++) {
                double a = Math.toRadians(angle + i * (frame == 3 ? 120 : 45));
                float x = cx + (float) Math.cos(a) * (size / 2 - inset);
                float y = cy + (float) Math.sin(a) * (size / 2 - inset);
                float r = stroke * (frame == 3 ? 1.1f : .7f);
                canvas.drawCircle(x, y, r, detail);
                if (frame == 4) { canvas.drawRect(x - r * 2, y - r * .25f, x + r * 2, y + r * .25f, detail); }
            }
        }
        detail.setStyle(Paint.Style.FILL); detail.setShader(null);
    }

    public void animate(View view, DevGramProfileStyle.Style style) {
        if (view.isShown() && view.hasWindowFocus() && motion()
                && (style.get("background") >= 3 || style.get("frame") >= 3 && frameMotion() || style.get("pattern") >= 2)) {
            view.postInvalidateDelayed(33);
        }
    }
}

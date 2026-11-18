package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Cursor geometry, easing and speech layout from the supplied reference. */
public final class DevGramNodeCursor {
    private static final long HOP_MS = 420;
    private static final float OVER_CUBE = 1.9f;
    private static final float OVER_SQUARE = 0.9f;
    private static final float PRESS = 0.82f;
    private static final float REST_FAR = 70.0f;
    private static final long REST_FIRST_MS = 2200;
    private static final long REST_HOP_MS = 900;
    private static final float REST_NEAR = 18.0f;
    private static final long REST_WAIT_MS = 2600;
    private static final float SIZE = 46.0f;
    private static final int SPEECH_CHARS = 34;
    private static final int SPEECH_LINES = 4;
    private final Path arrow;
    private boolean bendRight;
    private float bendX;
    private float bendY;
    private final long born;
    private final RectF box;
    private float dragX;
    private float dragY;
    private final Paint edge;
    private DevGramNodeFace face;
    private final Paint fill;
    private float fromX;
    private float fromY;
    private long hop;
    private int hops;
    private String label;
    private boolean loop;
    private final Paint plate;
    private boolean pressed;
    private long restAt;
    private boolean resting;
    private float[][] route;
    private boolean shown;
    private final List<String> speech;
    private boolean spinning;
    private long startedAt;
    private final Paint text;
    private float toX;
    private float toY;

    private static float ease(float f) {
        if (f >= 1.0f) {
            return 1.0f;
        }
        if (f <= 0.0f) {
            return 0.0f;
        }
        float f2 = f - 1.0f;
        return (f2 * f2 * ((f2 * OVER_CUBE) + OVER_SQUARE)) + 1.0f;
    }

    private static float smooth(float f) {
        if (f <= 0.0f) {
            return 0.0f;
        }
        if (f >= 1.0f) {
            return 1.0f;
        }
        return f * f * (3.0f - (f * 2.0f));
    }

    private static float spline(float f, float f2, float f3, float f4, float f5) {
        float f6 = f5 * f5;
        float f7 = -f;
        return ((f2 * 2.0f) + ((f7 + f3) * f5) + (((((f * 2.0f) - (5.0f * f2)) + (4.0f * f3)) - f4) * f6) + ((((f7 + (f2 * 3.0f)) - (f3 * 3.0f)) + f4) * f6 * f5)) * 0.5f;
    }

    public DevGramNodeCursor() {
        Paint paint = new Paint(1);
        this.fill = paint;
        Paint paint2 = new Paint(1);
        this.edge = paint2;
        Paint paint3 = new Paint(1);
        this.text = paint3;
        Paint paint4 = new Paint(1);
        this.plate = paint4;
        this.arrow = new Path();
        this.box = new RectF();
        this.label = "";
        this.speech = new ArrayList();
        this.hop = HOP_MS;
        this.dragX = Float.NaN;
        this.born = System.currentTimeMillis();
        paint.setColor(-1);
        paint2.setColor(-15724268);
        paint2.setStyle(Paint.Style.STROKE);
        paint2.setStrokeWidth(2.2f);
        paint4.setColor(-435154668);
        paint3.setColor(-1);
        paint3.setTextSize(20.0f);
        paint3.setTypeface(Typeface.create(Typeface.DEFAULT, 1));
    }

    public void appear(float f, float f2) {
        this.fromX = f;
        this.fromY = f2;
        this.toX = f;
        this.toY = f2;
        this.startedAt = 0L;
        this.shown = true;
        this.pressed = false;
    }

    public void rest(boolean z) {
        this.resting = z;
        this.restAt = System.currentTimeMillis() + REST_FIRST_MS;
    }

    public boolean resting() {
        return this.resting;
    }

    private void wander() {
        if (this.resting && this.shown && this.route == null && System.currentTimeMillis() >= this.restAt && arrived()) {
            double dRandom = Math.random() * 3.141592653589793d * 2.0d;
            float fRandom = (((float) Math.random()) * REST_FAR) + REST_NEAR;
            moveTo(this.toX + (((float) Math.cos(dRandom)) * fRandom), this.toY + (((float) Math.sin(dRandom)) * fRandom), ((long) (Math.random() * 900.0d)) + REST_HOP_MS);
            this.restAt = System.currentTimeMillis() + REST_WAIT_MS + ((long) (Math.random() * 2600.0d));
        }
    }

    public void hide() {
        this.resting = false;
        this.shown = false;
        this.pressed = false;
        this.label = "";
        DevGramNodeFace frameNodeFace = this.face;
        if (frameNodeFace != null) {
            frameNodeFace.clear();
        }
    }

    public void wear(View view, int i) {
        if (this.face == null) {
            if (view == null || i < 0) {
                return;
            } else {
                this.face = new DevGramNodeFace(view);
            }
        }
        this.face.wear(i);
    }

    public boolean shown() {
        return this.shown;
    }

    public void glide(List<float[]> list, long j) {
        glide(list, j, false, 1.0f);
    }

    public void glide(List<float[]> list, long j, boolean z, float f) {
        if (list == null || list.size() < 2) {
            return;
        }
        this.spinning = z;
        float fPace = pace(f);
        float[][] fArr = new float[list.size() + 1][];
        fArr[0] = new float[]{x(), y()};
        int i = 0;
        while (i < list.size()) {
            int i2 = i + 1;
            fArr[i2] = new float[]{list.get(i)[0], list.get(i)[1]};
            i = i2;
        }
        this.route = fArr;
        this.loop = false;
        if (j <= 0) {
            j = span(fArr);
        }
        this.hop = (long) (j / fPace);
        this.startedAt = System.currentTimeMillis();
        this.shown = true;
    }

    static float pace(float f) {
        if (f <= 0.0f) {
            return 1.0f;
        }
        return Math.max(0.7f, Math.min(1.4f, f));
    }

    private static long span(float[][] fArr) {
        double dHypot = 0.0d;
        for (int i = 1; i < fArr.length; i++) {
            float[] fArr2 = fArr[i];
            float f = fArr2[0];
            float[] fArr3 = fArr[i - 1];
            dHypot += Math.hypot(f - fArr3[0], fArr2[1] - fArr3[1]);
        }
        return Math.max(1100L, Math.min(14000L, (long) (dHypot * 1.15d)));
    }

    public void jump(float f, float f2) {
        this.route = null;
        this.spinning = false;
        this.fromX = f;
        this.fromY = f2;
        this.toX = f;
        this.toY = f2;
        this.hop = 1L;
        this.startedAt = System.currentTimeMillis() - 2;
        this.shown = true;
    }

    public void moveTo(float f, float f2, long j) {
        this.route = null;
        boolean z = false;
        this.spinning = false;
        float fX = x();
        float fY = y();
        this.fromX = fX;
        this.fromY = fY;
        this.toX = f;
        this.toY = f2;
        this.hops++;
        float fHypot = (float) Math.hypot(f - fX, f2 - fY);
        if (fHypot > 600.0f && this.hops % 7 == 3) {
            z = true;
        }
        this.loop = z;
        this.bendRight = !this.bendRight;
        bend(fHypot);
        if (j <= 0) {
            j = HOP_MS + ((long) Math.min(520.0f, fHypot * 0.35f));
        }
        this.hop = j;
        this.startedAt = System.currentTimeMillis();
        this.shown = true;
    }

    private void bend(float f) {
        float f2 = this.fromX;
        float f3 = this.toX;
        float f4 = (f2 + f3) / 2.0f;
        float f5 = this.fromY;
        float f6 = this.toY;
        float f7 = (f5 + f6) / 2.0f;
        if (f < 1.0f) {
            this.bendX = f4;
            this.bendY = f7;
            return;
        }
        float f8 = (-(f6 - f5)) / f;
        float f9 = (f3 - f2) / f;
        float fMin = Math.min(220.0f, f * (this.loop ? 0.75f : 0.22f));
        if (!this.bendRight) {
            fMin = -fMin;
        }
        this.bendX = f4 + (f8 * fMin);
        this.bendY = f7 + (f9 * fMin);
    }

    public void say(String str) {
        if (str == null) {
            str = "";
        }
        this.label = str;
    }

    public void speak(String str) {
        this.speech.clear();
        if (str == null) {
            return;
        }
        String strReplace = str.trim().replace('\n', ' ');
        while (strReplace.contains("  ")) {
            strReplace = strReplace.replace("  ", " ");
        }
        while (true) {
            int length = strReplace.length();
            int i = SPEECH_CHARS;
            if (length <= 0 || this.speech.size() >= 4) {
                break;
            }
            if (strReplace.length() <= SPEECH_CHARS) {
                this.speech.add(strReplace);
                break;
            }
            int iLastIndexOf = strReplace.lastIndexOf(32, SPEECH_CHARS);
            if (iLastIndexOf > 0) {
                i = iLastIndexOf;
            }
            this.speech.add(strReplace.substring(0, i).trim());
            strReplace = strReplace.substring(i).trim();
        }
        if (strReplace.length() <= SPEECH_CHARS || this.speech.size() < 4) {
            return;
        }
        int size = this.speech.size() - 1;
        this.speech.set(size, this.speech.get(size) + "...");
    }

    public void press(boolean z) {
        this.pressed = z;
        if (z) {
            return;
        }
        this.dragX = Float.NaN;
    }

    public void dragFrom(float f, float f2) {
        this.dragX = f;
        this.dragY = f2;
    }

    public boolean arrived() {
        return nowFraction() >= 1.0f;
    }

    public float x() {
        float fCurve;
        float fBreath;
        if (this.route != null) {
            fCurve = along(nowFraction(), 0);
            fBreath = breath(0.7f, 6.0f);
        } else {
            fCurve = curve(nowFraction(), this.fromX, this.bendX, this.toX);
            fBreath = breath(0.7f, 6.0f);
        }
        return fCurve + fBreath;
    }

    public float y() {
        float fCurve;
        float fBreath;
        if (this.route != null) {
            fCurve = along(nowFraction(), 1);
            fBreath = breath(1.3f, 4.0f);
        } else {
            fCurve = curve(nowFraction(), this.fromY, this.bendY, this.toY);
            fBreath = breath(1.3f, 4.0f);
        }
        return fCurve + fBreath;
    }

    private float along(float f, int i) {
        float[][] fArr = this.route;
        if (fArr == null || fArr.length < 2) {
            return i == 0 ? this.toX : this.toY;
        }
        float fSmooth = smooth(f);
        int length = fArr.length - 1;
        float f2 = fSmooth * length;
        int iFloor = (int) Math.floor(f2);
        if (iFloor >= length) {
            return fArr[length][i];
        }
        return spline(fArr[Math.max(0, iFloor - 1)][i], fArr[iFloor][i], fArr[iFloor + 1][i], fArr[Math.min(length, iFloor + 2)][i], f2 - iFloor);
    }

    private static float curve(float f, float f2, float f3, float f4) {
        float fEase = ease(f);
        float f5 = 1.0f - fEase;
        return (f5 * f5 * f2) + (f5 * 2.0f * fEase * f3) + (fEase * fEase * f4);
    }

    private float breath(float f, float f2) {
        return (float) (Math.sin(((System.currentTimeMillis() - this.born) / 1000.0d) * ((double) f)) * ((double) f2));
    }

    public boolean draw(Canvas canvas, float f) {
        Canvas canvas2;
        if (!this.shown) {
            return false;
        }
        wander();
        float fMax = Math.max(0.35f, 1.0f / Math.max(0.2f, f)) / org.telegram.messenger.AndroidUtilities.density;
        float fX = x();
        float fY = y();
        float f2 = SIZE * fMax * (this.pressed ? PRESS : 1.0f);
        int iSave = canvas.save();
        canvas.rotate(tilt(), fX, fY);
        this.arrow.rewind();
        this.arrow.moveTo(fX, fY);
        this.arrow.lineTo(fX, fY + f2);
        this.arrow.lineTo((0.28f * f2) + fX, (0.74f * f2) + fY);
        this.arrow.lineTo((0.52f * f2) + fX, (1.16f * f2) + fY);
        this.arrow.lineTo((0.7f * f2) + fX, (1.06f * f2) + fY);
        this.arrow.lineTo((0.46f * f2) + fX, (0.66f * f2) + fY);
        float f3 = fX + (0.8f * f2);
        this.arrow.lineTo(f3, (0.62f * f2) + fY);
        this.arrow.close();
        this.fill.setColor(this.pressed ? -6565633 : -1);
        this.edge.setStrokeWidth(2.2f * fMax);
        DevGramNodeFace frameNodeFace = this.face;
        if (frameNodeFace == null || !frameNodeFace.draw(canvas, fX, fY, f2)) {
            canvas.drawPath(this.arrow, this.fill);
            canvas.drawPath(this.arrow, this.edge);
        }
        canvas.restoreToCount(iSave);
        if (Float.isNaN(this.dragX)) {
            canvas2 = canvas;
        } else {
            this.edge.setColor(-8665857);
            this.edge.setStrokeWidth(3.0f * fMax);
            canvas2 = canvas;
            canvas2.drawLine(this.dragX, this.dragY, fX, fY, this.edge);
            this.edge.setColor(-15724268);
        }
        if (!this.speech.isEmpty()) {
            speech(canvas2, fX, fY, f2, fMax);
        }
        if (this.label.length() > 0) {
            float f4 = 20.0f * fMax;
            this.text.setTextSize(f4);
            float f5 = fY + (1.2f * f2);
            this.box.set(f3, f5, this.text.measureText(this.label) + f4 + f3, (30.0f * fMax) + f5);
            float f6 = 10.0f * fMax;
            canvas2.drawRoundRect(this.box, f6, f6, this.plate);
            canvas2.drawText(this.label, this.box.left + f6, this.box.bottom - (fMax * 9.0f), this.text);
        }
        return !arrived();
    }

    private void speech(Canvas canvas, float f, float f2, float f3, float f4) {
        this.text.setTextSize(22.0f * f4);
        float f5 = 28.0f * f4;
        float f6 = f4 * 14.0f;
        float fMax = 0.0f;
        for (int i = 0; i < this.speech.size(); i++) {
            fMax = Math.max(fMax, this.text.measureText(this.speech.get(i)));
        }
        float f7 = f + (f3 * 0.6f);
        this.box.set(f7, (f2 - ((this.speech.size() * f5) + f6)) - f6, fMax + f7 + (2.0f * f6), f2 - (0.4f * f6));
        canvas.drawRoundRect(this.box, f6, f6, this.plate);
        for (int i2 = 0; i2 < this.speech.size(); i2++) {
            canvas.drawText(this.speech.get(i2), this.box.left + f6, this.box.top + (0.8f * f6) + ((i2 + 0.75f) * f5), this.text);
        }
    }

    private float tilt() {
        float fNowFraction = nowFraction();
        if (fNowFraction >= 1.0f) {
            return breath(OVER_SQUARE, 3.0f);
        }
        if (this.route != null) {
            return this.spinning ? fNowFraction * 720.0f : Math.max(-16.0f, Math.min(16.0f, (along(Math.min(1.0f, 0.04f + fNowFraction), 0) - along(fNowFraction, 0)) * 0.6f));
        }
        float f = (this.toX - this.fromX) * 0.02f;
        if (f > 14.0f) {
            f = 14.0f;
        }
        if (f < -14.0f) {
            f = -14.0f;
        }
        return (f * ((float) Math.sin(((double) fNowFraction) * 3.141592653589793d))) + (this.loop ? ease(fNowFraction) * 360.0f : 0.0f);
    }

    private float nowFraction() {
        if (this.startedAt <= 0) {
            return 1.0f;
        }
        float fCurrentTimeMillis = (System.currentTimeMillis() - this.startedAt) / (float) this.hop;
        if (fCurrentTimeMillis >= 1.0f) {
            return 1.0f;
        }
        if (fCurrentTimeMillis <= 0.0f) {
            return 0.0f;
        }
        return fCurrentTimeMillis;
    }

    private static float at(float f, float f2, float f3) {
        return f2 + ((f3 - f2) * ease(f));
    }
}

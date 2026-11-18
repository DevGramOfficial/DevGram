package org.telegram.ui.Components;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramProfileDocument;
import org.telegram.messenger.DevGramProfileCustomization;
import org.telegram.ui.ActionBar.Theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;

/** The supplied Custom Profile bubble preview, adapted to the native document. */
public final class DevGramProfileBubblePreview extends View {
    private JSONObject document=new JSONObject();
    private final long uid;
    public void setDocument(JSONObject value){document=DevGramProfileDocument.copy(value);invalidate();}
    private final Paint fill;
    private Typeface own;
    private final RectF rect;
    private final TextPaint text;

    public DevGramProfileBubblePreview(Context context,long uid) {
        super(context);
        this.fill = new Paint(1);
        TextPaint textPaint = new TextPaint(1);
        this.text = textPaint;
        this.rect = new RectF();
        this.uid=uid;
        setWillNotDraw(false);
        textPaint.setTextSize(AndroidUtilities.dpf2(14.5f));
    }

    @Override // android.view.View
    protected void onMeasure(int i, int i2) {
        setMeasuredDimension(View.MeasureSpec.getSize(i), AndroidUtilities.dp(168.0f));
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        boolean zIsDark = isDark();
        int iDp = AndroidUtilities.dp(12.0f);
        this.fill.setShader(null);
        this.fill.setColor(zIsDark ? -15395302 : -1315600);
        this.rect.set(iDp, AndroidUtilities.dp(6.0f), width - iDp, getHeight() - AndroidUtilities.dp(6.0f));
        canvas.drawRoundRect(this.rect, AndroidUtilities.dpf2(18.0f), AndroidUtilities.dpf2(18.0f), this.fill);
        int i = zIsDark ? -14013648 : -1;
        int i2 = zIsDark ? -1250068 : -16053492;
        float fDp = AndroidUtilities.dp(18.0f);
        this.own = null;
        float fBubble = bubble(canvas, width, false, DevGramLocale.text("Как тебе новый цвет? 🎨"), i, 0, 0, false, 0, i2, fDp) + AndroidUtilities.dp(9.0f);
        JSONObject cfg=DevGramProfileDocument.effective(document,uid);
        boolean bool = cfg.optInt("bubbleEnabled",cfg.has("bubbleColor")?1:0)==1;
        boolean bool2 = cfg.optInt("bubbleGradient")==1;
        int color = DevGramProfileBubbleColors.color(cfg,"bubbleColor",-13938056);
        int color2 = DevGramProfileBubbleColors.color(cfg,"bubbleColor2",-7459358);
        int i3 = cfg.optInt("bubbleAngle");
        int i4 = bool ? color : -13397780;
        int color3 = bool ? DevGramProfileBubbleColors.color(cfg,"textColor",-1) : -1;
        boolean z = bool && bool2;
        this.own = DevGramProfileDocument.messageFont(cfg);
        int i5 = i4;
        boolean z2 = z;
        bubble(canvas, width, true, DevGramLocale.text("И цвет текста тоже 🔥"), i5, color, color2, z2, i3, color3, bubble(canvas, width, true, DevGramLocale.text("Смотри, свой пузырёк!"), i5, color, color2, z2, i3, color3, fBubble) + AndroidUtilities.dp(6.0f));
    }

    private float bubble(Canvas canvas, int i, boolean z, String str, int i2, int i3, int i4, boolean z2, int i5, int i6, float f) {
        float fDpf = AndroidUtilities.dpf2(13.0f);
        float fDpf2 = AndroidUtilities.dpf2(36.0f);
        float f2 = i;
        this.text.setTypeface(this.own);
        float fMin = Math.min(this.text.measureText(str) + (fDpf * 2.0f), 0.64f * f2);
        float fDp = AndroidUtilities.dp(20.0f);
        if (z) {
            fDp = (f2 - fDp) - fMin;
        }
        float f3 = fMin + fDp;
        float f4 = f + fDpf2;
        this.rect.set(fDp, f, f3, f4);
        if (z2) {
            this.fill.setShader(gradient(this.rect, i3, i4, i5));
        } else {
            this.fill.setShader(null);
            this.fill.setColor(i2);
        }
        canvas.drawRoundRect(this.rect, AndroidUtilities.dpf2(17.0f), AndroidUtilities.dpf2(17.0f), this.fill);
        this.fill.setShader(null);
        this.text.setColor(i6);
        float fDescent = ((fDpf2 / 2.0f) + f) - ((this.text.descent() + this.text.ascent()) / 2.0f);
        int iSave = canvas.save();
        canvas.clipRect(fDp, f, f3, f4);
        canvas.drawText(str, fDp + fDpf, fDescent, this.text);
        canvas.restoreToCount(iSave);
        return f4;
    }

    private LinearGradient gradient(RectF rectF, int i, int i2, int i3) {
        double radians = Math.toRadians(i3);
        float fCenterX = rectF.centerX();
        float fCenterY = rectF.centerY();
        float fMax = Math.max(rectF.width(), rectF.height()) / 2.0f;
        float fCos = ((float) Math.cos(radians)) * fMax;
        float fSin = ((float) Math.sin(radians)) * fMax;
        return new LinearGradient(fCenterX - fCos, fCenterY - fSin, fCenterX + fCos, fCenterY + fSin, i, i2, Shader.TileMode.CLAMP);
    }

    private boolean isDark() {
        return Theme.isCurrentThemeDark();
    }
}

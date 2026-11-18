package org.telegram.ui.Components;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import java.util.function.IntConsumer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Layout preset cards adapted from the user supplied Custom Profile reference. */
public final class DevGramHeaderLayoutCards extends View {
    private static final int BORDER_DP = 2;
    private static final int CARD_RADIUS_DP = 12;
    private static final int GAP_DP = 9;
    private static final int HEIGHT_DP = 96;
    private static final int IDLE_ALPHA = 26;
    private static final int LABEL_DP = 22;
    private static final int PICKED_ALPHA = 46;
    private static final int SIDE_DP = 21;
    private static final int TEXT_DP = 12;
    private final int accent;
    private final RectF bar;
    private final Paint fill;
    private final Paint label;
    private int selected;
    private boolean basicLocked,customLocked;
    public void setLocked(boolean basic,boolean custom){if(basicLocked!=basic||customLocked!=custom){basicLocked=basic;customLocked=custom;invalidate();}}
    private String caption(int index){return DevGramLocale.text(LABELS[index])+((MODES[index]==4?customLocked:basicLocked)?" 🔒":"");}
    private final RectF shape;
    private final IntConsumer sink;
    private final Paint stroke;
    private final int textColor;
    static final int[] MODES = {0, 1, 4};
    static final String[] LABELS = {"Telegram", "Слева", "Своя"};

    public DevGramHeaderLayoutCards(Context context, int i, IntConsumer intSink,Theme.ResourcesProvider provider) {
        super(context);
        this.fill = new Paint(1);
        Paint paint = new Paint(1);
        this.stroke = paint;
        Paint paint2 = new Paint(1);
        this.label = paint2;
        this.shape = new RectF();
        this.bar = new RectF();
        this.sink = intSink;
        this.selected = i;
        int iThemeColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,provider);
        this.accent = iThemeColor;
        this.textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,provider);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dpf2(2.0f));
        paint.setColor(iThemeColor);
        paint2.setTextSize(AndroidUtilities.dpf2(12.0f));
        paint2.setTextAlign(Paint.Align.CENTER);
        setContentDescription(DevGramLocale.text("Компоновка шапки: ", "Header layout: ") + DevGramLocale.text(LABELS[index(i)]));
    }

    static View create(Context context, int i, IntConsumer intSink) {
        if (context == null) {
            return null;
        }
        return new DevGramHeaderLayoutCards(context, i, intSink,null);
    }

    public void setSelected(int i) {
        if (this.selected != i) {
            this.selected = i;
            setContentDescription(DevGramLocale.text("Компоновка шапки: ", "Header layout: ") + DevGramLocale.text(LABELS[index(i)]));
            invalidate();
        }
    }

    @Override // android.view.View
    protected void onMeasure(int i, int i2) {
        setMeasuredDimension(View.MeasureSpec.getSize(i), AndroidUtilities.dp(96.0f) + (AndroidUtilities.dp(9.0f) * 2));
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        float fDpf = AndroidUtilities.dpf2(21.0f);
        float fDpf2 = AndroidUtilities.dpf2(9.0f);
        float width = getWidth() - (fDpf * 2.0f);
        int[] iArr = MODES;
        float length = (width - ((iArr.length - 1) * fDpf2)) / iArr.length;
        float fDpf3 = AndroidUtilities.dpf2(96.0f);
        float fDpf4 = AndroidUtilities.dpf2(12.0f);
        int i = 0;
        while (true) {
            int[] iArr2 = MODES;
            if (i >= iArr2.length) {
                return;
            }
            float f = ((length + fDpf2) * i) + fDpf;
            this.shape.set(f, fDpf2, f + length, fDpf2 + fDpf3);
            boolean z = iArr2[i] == this.selected;
            this.fill.setShader(null);
            this.fill.setColor(this.accent);
            this.fill.setAlpha(((z ? PICKED_ALPHA : 26) * 255) / 100);
            canvas.drawRoundRect(this.shape, fDpf4, fDpf4, this.fill);
            if (z) {
                canvas.drawRoundRect(this.shape, fDpf4, fDpf4, this.stroke);
            }
            drawPreview(canvas, this.shape, iArr2[i]);
            this.label.setColor(z ? this.accent : this.textColor);
            canvas.drawText(caption(i), this.shape.centerX(), (this.shape.bottom - (AndroidUtilities.dpf2(22.0f) / 2.0f)) - ((this.label.descent() + this.label.ascent()) / 2.0f), this.label);
            i++;
        }
    }

    private void drawPreview(Canvas canvas, RectF rectF, int i) {
        DevGramHeaderLayoutCards editLayoutCards;
        float fDpf = AndroidUtilities.dpf2(9.0f);
        float f = rectF.top + fDpf;
        float fDpf2 = rectF.bottom - AndroidUtilities.dpf2(22.0f);
        float fDpf3 = AndroidUtilities.dpf2(15.0f);
        this.fill.setColor(this.textColor);
        this.fill.setAlpha(160);
        if (i == 1) {
            float fDpf4 = ((f + fDpf2) / 2.0f) - AndroidUtilities.dpf2(4.0f);
            float f2 = fDpf3 / 2.0f;
            canvas.drawCircle(rectF.left + fDpf + f2, fDpf4, f2, this.fill);
            bar(canvas, rectF.left + fDpf + fDpf3 + AndroidUtilities.dpf2(5.0f), fDpf4 - AndroidUtilities.dpf2(5.0f), AndroidUtilities.dpf2(28.0f), AndroidUtilities.dpf2(4.0f));
            editLayoutCards = this;
            editLayoutCards.bar(canvas, rectF.left + fDpf + fDpf3 + AndroidUtilities.dpf2(5.0f), fDpf4 + AndroidUtilities.dpf2(2.0f), AndroidUtilities.dpf2(18.0f), AndroidUtilities.dpf2(3.0f));
        } else if (i == 4) {
            float f3 = fDpf3 / 2.0f;
            canvas.drawCircle(rectF.centerX() + AndroidUtilities.dpf2(10.0f), f + f3 + AndroidUtilities.dpf2(4.0f), f3, this.fill);
            bar(canvas, rectF.left + fDpf, AndroidUtilities.dpf2(20.0f) + f, AndroidUtilities.dpf2(26.0f), AndroidUtilities.dpf2(4.0f));
            editLayoutCards = this;
            editLayoutCards.bar(canvas, rectF.left + fDpf + AndroidUtilities.dpf2(8.0f), f + AndroidUtilities.dpf2(28.0f), AndroidUtilities.dpf2(16.0f), AndroidUtilities.dpf2(3.0f));
        } else {
            float fCenterX = rectF.centerX();
            float f4 = fDpf3 / 2.0f;
            canvas.drawCircle(fCenterX, f + f4 + AndroidUtilities.dpf2(2.0f), f4, this.fill);
            float f5 = f + fDpf3;
            bar(canvas, fCenterX - AndroidUtilities.dpf2(15.0f), AndroidUtilities.dpf2(7.0f) + f5, AndroidUtilities.dpf2(30.0f), AndroidUtilities.dpf2(4.0f));
            editLayoutCards = this;
            editLayoutCards.bar(canvas, fCenterX - AndroidUtilities.dpf2(9.0f), f5 + AndroidUtilities.dpf2(15.0f), AndroidUtilities.dpf2(18.0f), AndroidUtilities.dpf2(3.0f));
        }
        editLayoutCards.fill.setAlpha(90);
        float fDpf5 = fDpf2 - AndroidUtilities.dpf2(9.0f);
        float fWidth = (rectF.width() - (2.0f * fDpf)) / 3.0f;
        int i2 = 0;
        while (i2 < 3) {
            editLayoutCards.bar(canvas, AndroidUtilities.dpf2(1.0f) + rectF.left + fDpf + (i2 * fWidth), fDpf5, fWidth - AndroidUtilities.dpf2(3.0f), AndroidUtilities.dpf2(6.0f));
            i2++;
            editLayoutCards = this;
        }
    }

    private void bar(Canvas canvas, float f, float f2, float f3, float f4) {
        this.bar.set(f, f2, f3 + f, f2 + f4);
        float f5 = f4 / 2.0f;
        canvas.drawRoundRect(this.bar, f5, f5, this.fill);
    }

    @Override // android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        try {
            return handleTouch(motionEvent);
        } catch (Throwable th) {
            FileLog.e(th);
            return false;
        }
    }

    private boolean handleTouch(MotionEvent motionEvent) {
        int i = 0;
        if (motionEvent.getActionMasked() != 1) {
            return motionEvent.getActionMasked() == 0;
        }
        float fDpf = AndroidUtilities.dpf2(21.0f);
        float fDpf2 = AndroidUtilities.dpf2(9.0f);
        float width = getWidth() - (2.0f * fDpf);
        int[] iArr = MODES;
        float length = (width - ((iArr.length - 1) * fDpf2)) / iArr.length;
        while (true) {
            int[] iArr2 = MODES;
            if (i >= iArr2.length) {
                return true;
            }
            float f = ((length + fDpf2) * i) + fDpf;
            if (motionEvent.getX() >= f && motionEvent.getX() <= f + length) {
                int i2 = iArr2[i];
                if (this.selected != i2) {
                    setSelected(i2);
                    IntConsumer intSink = this.sink;
                    if (intSink != null) {
                        intSink.accept(i2);
                    }
                }
                return true;
            }
            i++;
        }
    }

    private static int index(int i) {
        int i2 = 0;
        while (true) {
            int[] iArr = MODES;
            if (i2 >= iArr.length) {
                return 0;
            }
            if (iArr[i2] == i) {
                return i2;
            }
            i2++;
        }
    }
}

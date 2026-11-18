package org.telegram.ui.Components;

import org.telegram.messenger.AndroidUtilities;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.widget.LinearLayout;

public final class DevGramCommentLikeView extends View {
    private static final float HEART_DP = 15.0f;
    private static final int HEIGHT_DP = 28;
    static final int LIKED_COLOR = -2087857;
    private static final int WIDTH_DP = 44;
    private final Paint digits;
    private final Paint heart;
    private int idle;
    private boolean liked;
    private int likes;
    private final Path path;

    public DevGramCommentLikeView(Context context, int i) {
        super(context);
        this.heart = new Paint(1);
        Paint paint = new Paint(1);
        this.digits = paint;
        this.path = new Path();
        this.idle = i;
        paint.setTextSize(AndroidUtilities.dpf2(12.0f));
        paint.setTextAlign(Paint.Align.LEFT);
        setLayoutParams(new LinearLayout.LayoutParams(AndroidUtilities.dp(44.0f), AndroidUtilities.dp(28.0f)));
    }

    public void state(int i, boolean z) {
        this.likes = Math.max(i, 0);
        this.liked = z;
        invalidate();
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        float fDpf = AndroidUtilities.dpf2(HEART_DP);
        float height = (getHeight() - fDpf) / 2.0f;
        int i = this.liked ? LIKED_COLOR : this.idle;
        this.heart.setColor(i);
        this.heart.setStyle(this.liked ? Paint.Style.FILL : Paint.Style.STROKE);
        this.heart.setStrokeWidth(AndroidUtilities.dpf2(1.4f));
        shape(0.0f, height, fDpf);
        canvas.drawPath(this.path, this.heart);
        if (this.likes > 0) {
            this.digits.setColor(i);
            canvas.drawText(String.valueOf(this.likes), fDpf + 0.0f + AndroidUtilities.dpf2(4.0f), (getHeight() / 2.0f) - ((this.digits.descent() + this.digits.ascent()) / 2.0f), this.digits);
        }
    }

    private void shape(float f, float f2, float f3) {
        float f4 = f3 / 2.0f;
        float f5 = f3 / 4.0f;
        this.path.reset();
        float f6 = f + f4;
        float f7 = f2 + f3;
        this.path.moveTo(f6, f7);
        float f8 = 0.4f * f5;
        float f9 = f2 + f4;
        float f10 = 0.2f * f5;
        float f11 = f2 - (0.3f * f5);
        this.path.cubicTo(f - f8, f9, f + f10, f11, f6, f2 + (f5 * 0.9f));
        float f12 = f + f3;
        this.path.cubicTo(f12 - f10, f11, f12 + f8, f9, f6, f7);
        this.path.close();
    }
}

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.ui.ActionBar.Theme;

// DevGram: живое превью оформления сообщений (хвост пузыря, «изменено», кнопка «поделиться»).
public class DevGramMessagesPreviewCell extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint time = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bubble = new RectF();
    private final Path tail = new Path();

    public DevGramMessagesPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        text.setTextSize(AndroidUtilities.dp(14));
        time.setTextSize(AndroidUtilities.dp(11));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(150), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        // фон-«обои»
        paint.setColor(Theme.getColor(Theme.key_chat_wallpaper));
        canvas.drawRect(0, 0, w, getHeight(), paint);

        float m = AndroidUtilities.dp(14);
        // входящее (слева)
        float inW = AndroidUtilities.dp(210);
        drawBubble(canvas, false, m, AndroidUtilities.dp(16), m + inW, AndroidUtilities.dp(66),
                DevGramLocale.text("Привет! Это превью"), "12:41");
        // исходящее (справа)
        float outW = AndroidUtilities.dp(190);
        drawBubble(canvas, true, w - m - outW, AndroidUtilities.dp(84), w - m, AndroidUtilities.dp(134),
                DevGramLocale.text("Настройки видны сразу"), "12:42");
    }

    private void drawBubble(Canvas canvas, boolean out, float l, float t, float r, float b,
                            String value, String tm) {
        bubble.set(l, t, r, b);
        paint.setColor(Theme.getColor(out ? Theme.key_chat_outBubble : Theme.key_chat_inBubble));
        float radius = AndroidUtilities.dp(14);
        canvas.drawRoundRect(bubble, radius, radius, paint);

        // хвост пузыря
        if (!DevGramConfig.removeMessageTail) {
            tail.reset();
            if (out) {
                tail.moveTo(r - AndroidUtilities.dp(6), b - AndroidUtilities.dp(14));
                tail.lineTo(r + AndroidUtilities.dp(6), b);
                tail.lineTo(r - AndroidUtilities.dp(10), b);
            } else {
                tail.moveTo(l + AndroidUtilities.dp(6), b - AndroidUtilities.dp(14));
                tail.lineTo(l - AndroidUtilities.dp(6), b);
                tail.lineTo(l + AndroidUtilities.dp(10), b);
            }
            tail.close();
            canvas.drawPath(tail, paint);
        }

        float pad = AndroidUtilities.dp(11);
        // текст сообщения (обрезаем, чтобы влезал)
        text.setColor(Theme.getColor(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
        float maxTextW = (r - l) - pad * 2;
        CharSequence ell = TextUtils.ellipsize(value, text, maxTextW, TextUtils.TruncateAt.END);
        canvas.drawText(ell, 0, ell.length(), l + pad, t + AndroidUtilities.dp(23), text);

        // «изменено» + время в правом нижнем углу пузыря
        time.setColor(Theme.getColor(out ? Theme.key_chat_outTimeText : Theme.key_chat_inTimeText));
        String status = (DevGramConfig.replaceEditedWithIcon ? "✎ " : DevGramLocale.text("изменено ")) + tm;
        float statusW = time.measureText(status);
        canvas.drawText(status, r - pad - statusW, b - AndroidUtilities.dp(9), time);

        // кнопка «поделиться» справа от входящего пузыря
        if (!DevGramConfig.hideShareButton && !out) {
            float cx = r + AndroidUtilities.dp(20);
            float cy = b - AndroidUtilities.dp(14);
            paint.setColor(Theme.getColor(Theme.key_chat_serviceBackground));
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(14), paint);
            paint.setColor(Theme.getColor(Theme.key_chat_serviceText));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
            float a = AndroidUtilities.dp(5);
            // стрелка ↗
            canvas.drawLine(cx - a, cy + a, cx + a, cy - a, paint);
            canvas.drawLine(cx, cy - a, cx + a, cy - a, paint);
            canvas.drawLine(cx + a, cy - a, cx + a, cy + a, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}

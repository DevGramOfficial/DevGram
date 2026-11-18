package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.Theme;

// DevGram: выбор формы стикеров — 3 карточки (По умолчанию / Закруглённая / Сообщение), как у exteraGram.
public class DevGramStickerShapeCell extends View {

    public interface OnPick { void pick(int shape); }

    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path bubble = new Path();
    private OnPick onPick;

    private static String[] getTitles() {
        return new String[]{DevGramLocale.text("По умолчанию"), DevGramLocale.text("Закруглённая"), DevGramLocale.text("Сообщение")};
    }

    public DevGramStickerShapeCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(AndroidUtilities.dp(2));
        label.setTextSize(AndroidUtilities.dp(12));
    }

    public void setOnPick(OnPick p) { onPick = p; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(118), MeasureSpec.EXACTLY));
    }

    private RectF cardRect(int i) {
        float pad = AndroidUtilities.dp(14), gap = AndroidUtilities.dp(10);
        float cw = (getWidth() - pad * 2 - gap * 2) / 3f;
        float left = pad + i * (cw + gap);
        return new RectF(left, AndroidUtilities.dp(8), left + cw, AndroidUtilities.dp(84));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int accent = Theme.getColor(Theme.key_switchTrack);
        int grey = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int sel = MessagesController.getGlobalMainSettings().getInt("dg_stickerShape", 0);
        for (int i = 0; i < 3; i++) {
            RectF cr = cardRect(i);
            card.setColor(Color.argb(22, Color.red(grey), Color.green(grey), Color.blue(grey)));
            canvas.drawRoundRect(cr, AndroidUtilities.dp(12), AndroidUtilities.dp(12), card);
            if (i == sel) {
                border.setColor(accent);
                rect.set(cr.left + AndroidUtilities.dp(1), cr.top + AndroidUtilities.dp(1),
                        cr.right - AndroidUtilities.dp(1), cr.bottom - AndroidUtilities.dp(1));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), border);
            }
            // образец формы стикера
            shape.setColor(Color.argb(150, Color.red(grey), Color.green(grey), Color.blue(grey)));
            float s = AndroidUtilities.dp(40);
            float scx = cr.centerX(), scy = cr.centerY();
            rect.set(scx - s / 2, scy - s / 2, scx + s / 2, scy + s / 2);
            if (i == 0) {          // квадрат (по умолчанию)
                canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), shape);
            } else if (i == 1) {   // закруглённая
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), shape);
            } else {               // форма сообщения (пузырь с хвостом)
                bubble.reset();
                bubble.addRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), Path.Direction.CW);
                canvas.drawPath(bubble, shape);
                canvas.drawRect(rect.left, rect.bottom - AndroidUtilities.dp(10),
                        rect.left + AndroidUtilities.dp(10), rect.bottom, shape);
            }
            // подпись
            label.setColor(i == sel ? accent : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            String t = getTitles()[i];
            float tw = Math.min(label.measureText(t), cr.width());
            canvas.drawText(t, cr.centerX() - tw / 2f, cr.bottom + AndroidUtilities.dp(20), label);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            for (int i = 0; i < 3; i++) {
                if (cardRect(i).contains(e.getX(), e.getY())) {
                    MessagesController.getGlobalMainSettings().edit().putInt("dg_stickerShape", i).apply();
                    invalidate();
                    if (onPick != null) onPick.pick(i);
                    return true;
                }
            }
        }
        return true;
    }
}

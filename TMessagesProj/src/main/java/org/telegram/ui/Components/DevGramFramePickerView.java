package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

// DevGram: выбор обводки сообщений — сетка мини-пузырей с живым превью каждого стиля.
// Первая карточка — «Без обводки» (0).
public class DevGramFramePickerView extends View implements org.telegram.messenger.NotificationCenter.NotificationCenterDelegate {

    private DevGramMessageFrame.Cache[] shaderCaches = new DevGramMessageFrame.Cache[0]; // градиенты превью не пересоздаются каждый кадр

    public interface OnSelect {
        void run(int styleId);
    }

    private static final int COLS = 3;
    private static final int CELL_H = 92;

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF bubble = new RectF();
    private int selected;
    private int pressed = -1;
    private OnSelect onSelect;

    public DevGramFramePickerView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(AndroidUtilities.dp(2));
        titlePaint.setTextSize(AndroidUtilities.dp(12));
        titlePaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setOnSelect(OnSelect onSelect) {
        this.onSelect = onSelect;
    }

    public void setSelected(int styleId) {
        selected = styleId;
        invalidate();
    }

    private int count() {
        return DevGramMessageFrame.STYLES.length + 1;
    }

    private int styleAt(int index) {
        return index == 0 ? 0 : DevGramMessageFrame.STYLES[index - 1].id;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        int rows = (count() + COLS - 1) / COLS;
        setMeasuredDimension(width, getPaddingTop() + getPaddingBottom() + rows * AndroidUtilities.dp(CELL_H));
    }

    private void cellRect(int index, RectF out) {
        float cellW = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) COLS;
        int col = index % COLS, row = index / COLS;
        float l = getPaddingLeft() + col * cellW, t = getPaddingTop() + row * AndroidUtilities.dp(CELL_H);
        out.set(l + AndroidUtilities.dp(5), t + AndroidUtilities.dp(5), l + cellW - AndroidUtilities.dp(5), t + AndroidUtilities.dp(CELL_H) - AndroidUtilities.dp(5));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        cardPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        bubblePaint.setColor(Theme.getColor(Theme.key_chat_outBubble, resourcesProvider));
        ringPaint.setColor(accent);
        titlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        float cardR = AndroidUtilities.dp(14);
        for (int i = 0; i < count(); i++) {
            cellRect(i, rect);
            int style = styleAt(i);
            cardPaint.setAlpha(i == pressed ? 200 : 255);
            canvas.drawRoundRect(rect, cardR, cardR, cardPaint);
            if (style == selected) canvas.drawRoundRect(rect, cardR, cardR, ringPaint);
            // мини-пузырь
            float bw = rect.width() * 0.62f, bh = AndroidUtilities.dp(26);
            float cx = rect.centerX(), top = rect.top + AndroidUtilities.dp(18);
            bubble.set(cx - bw / 2f, top, cx + bw / 2f, top + bh);
            float br = AndroidUtilities.dp(10);
            if (shaderCaches.length != count()) shaderCaches = new DevGramMessageFrame.Cache[count()];
            if (shaderCaches[i] == null) shaderCaches[i] = new DevGramMessageFrame.Cache();
            Paint fill = style != 0 ? DevGramMessageFrame.fillPaint(shaderCaches[i], style, bubble.left, bubble.top, bubble.right, bubble.bottom, 1f) : null;
            canvas.drawRoundRect(bubble, br, br, fill != null ? fill : bubblePaint);
            if (style != 0) DevGramMessageFrame.draw(canvas, bubble, br, shaderCaches[i], style, 1f);
            String title = style == 0 ? "Без обводки" : DevGramMessageFrame.get(style).title;
            canvas.drawText(title, cx, rect.bottom - AndroidUtilities.dp(10), titlePaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(this, org.telegram.messenger.NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(this, org.telegram.messenger.NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        invalidate(); // догрузились картинки эмодзи-украшений
    }

    private int hit(float x, float y) {
        for (int i = 0; i < count(); i++) {
            cellRect(i, rect);
            if (rect.contains(x, y)) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pressed = hit(e.getX(), e.getY());
                invalidate();
                return pressed >= 0;
            case MotionEvent.ACTION_UP: {
                int i = hit(e.getX(), e.getY());
                if (i >= 0 && i == pressed && onSelect != null && styleAt(i) != selected) onSelect.run(styleAt(i));
                pressed = -1;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                return true;
        }
        return true;
    }
}

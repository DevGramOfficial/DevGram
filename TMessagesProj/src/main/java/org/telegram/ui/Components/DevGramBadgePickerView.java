package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

// DevGram: сетка значков на выбор для поддержавших. Первый — значок, выданный командой.
// Выбранный обведён кольцом акцентного цвета; нажатие — onSelect(emojiId), где 0 = выданный.
public class DevGramBadgePickerView extends View {

    public interface OnSelect {
        void run(long emojiId);
    }

    private static final int CELL = 64;
    private static final int ICON = 40;

    private final int account;
    private final Theme.ResourcesProvider resourcesProvider;
    private final ArrayList<Long> ids = new ArrayList<>();       // 0 — выданный значок
    private final ArrayList<AnimatedEmojiDrawable> drawables = new ArrayList<>();
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long originalEmojiId;
    private long selected;
    private OnSelect onSelect;
    private boolean attached;
    private int pressed = -1;

    public DevGramBadgePickerView(Context context, int account, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.account = account;
        this.resourcesProvider = resourcesProvider;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
    }

    public void setOnSelect(OnSelect onSelect) {
        this.onSelect = onSelect;
    }

    public void setData(long originalEmojiId, long currentEmojiId, ArrayList<DevGramBadges.Option> options) {
        for (AnimatedEmojiDrawable d : drawables) if (d != null && attached) d.removeView(this);
        ids.clear();
        drawables.clear();
        this.originalEmojiId = originalEmojiId;
        ids.add(0L);
        drawables.add(originalEmojiId != 0 ? AnimatedEmojiDrawable.make(account, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, originalEmojiId) : null);
        for (DevGramBadges.Option o : options) {
            if (o.emojiId == originalEmojiId) continue;
            ids.add(o.emojiId);
            drawables.add(AnimatedEmojiDrawable.make(account, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, o.emojiId));
        }
        selected = currentEmojiId == originalEmojiId ? 0 : currentEmojiId;
        if (attached) for (AnimatedEmojiDrawable d : drawables) if (d != null) d.addView(this);
        requestLayout();
        invalidate();
    }

    public void setSelected(long emojiId) {
        selected = emojiId == originalEmojiId ? 0 : emojiId;
        invalidate();
    }

    private int columns(int width) {
        return Math.max(1, (width - getPaddingLeft() - getPaddingRight()) / AndroidUtilities.dp(CELL));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        int cols = columns(width);
        int rows = (ids.size() + cols - 1) / cols;
        setMeasuredDimension(width, getPaddingTop() + getPaddingBottom() + Math.max(1, rows) * AndroidUtilities.dp(CELL));
    }

    private float cellCenterX(int index, int cols) {
        int col = index % cols;
        float cell = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) cols;
        return getPaddingLeft() + cell * col + cell / 2f;
    }

    private float cellCenterY(int index, int cols) {
        int row = index / cols;
        return getPaddingTop() + AndroidUtilities.dp(CELL) * row + AndroidUtilities.dp(CELL) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int cols = columns(getWidth());
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        ringPaint.setColor(accent);
        bgPaint.setColor(Theme.multAlpha(accent, 0.10f));
        float r = AndroidUtilities.dp(CELL / 2f - 4);
        for (int i = 0; i < ids.size(); i++) {
            float cx = cellCenterX(i, cols), cy = cellCenterY(i, cols);
            boolean sel = ids.get(i) == selected;
            if (sel || i == pressed) canvas.drawCircle(cx, cy, r, bgPaint);
            if (sel) canvas.drawCircle(cx, cy, r, ringPaint);
            AnimatedEmojiDrawable d = drawables.get(i);
            if (d != null) {
                int half = AndroidUtilities.dp(ICON / 2f);
                d.setBounds((int) cx - half, (int) cy - half, (int) cx + half, (int) cy + half);
                d.draw(canvas);
            }
        }
    }

    private int hit(float x, float y) {
        int cols = columns(getWidth());
        float r = AndroidUtilities.dp(CELL / 2f);
        for (int i = 0; i < ids.size(); i++) {
            float dx = x - cellCenterX(i, cols), dy = y - cellCenterY(i, cols);
            if (dx * dx + dy * dy <= r * r) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            pressed = hit(e.getX(), e.getY());
            invalidate();
            return pressed >= 0;
        } else if (e.getAction() == MotionEvent.ACTION_UP) {
            int i = hit(e.getX(), e.getY());
            if (i >= 0 && i == pressed && onSelect != null) {
                long id = ids.get(i);
                if (id != selected) onSelect.run(id);
            }
            pressed = -1;
            invalidate();
            return true;
        } else if (e.getAction() == MotionEvent.ACTION_CANCEL) {
            pressed = -1;
            invalidate();
        }
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        for (AnimatedEmojiDrawable d : drawables) if (d != null) d.addView(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        for (AnimatedEmojiDrawable d : drawables) if (d != null) d.removeView(this);
    }
}

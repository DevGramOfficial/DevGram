package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

// DevGram: кнопка «перевести / показать оригинал» для текстов плагинов. Три состояния:
// перевести → загрузка (крутилка) → вернуть оригинал; иконки сменяются с плавным переходом.
public class DevGramTranslateButton extends View {

    public static final int STATE_TRANSLATE = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_RESET = 2;

    private final Drawable translateIcon;
    private final Drawable resetIcon;
    private final CircularProgressDrawable progress;
    private final AnimatedFloat loading;
    private final AnimatedFloat switching;
    private Drawable icon;
    private Drawable previousIcon;
    private int state = STATE_TRANSLATE;

    public DevGramTranslateButton(Context context, int color) {
        super(context);
        translateIcon = ContextCompat.getDrawable(context, R.drawable.msg_translate).mutate();
        resetIcon = ContextCompat.getDrawable(context, R.drawable.photo_undo2).mutate();
        PorterDuffColorFilter filter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        translateIcon.setColorFilter(filter);
        resetIcon.setColorFilter(filter);
        progress = new CircularProgressDrawable(color);
        loading = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        switching = new AnimatedFloat(1f, this, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        icon = translateIcon;
        setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        updateDescription();
    }

    public int getState() {
        return state;
    }

    public void setState(int newState) {
        if (state == newState) return;
        state = newState;
        if (newState != STATE_LOADING) {
            Drawable next = newState == STATE_RESET ? resetIcon : translateIcon;
            if (next != icon) {
                previousIcon = icon;
                icon = next;
                switching.set(0f, true);
            }
        }
        updateDescription();
        invalidate();
    }

    private void updateDescription() {
        setContentDescription(state == STATE_RESET ? "Показать оригинал" : "Перевести");
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float l = loading.set(state == STATE_LOADING ? 1f : 0f);
        int shift = AndroidUtilities.dp(10);
        if (l < 1f) {
            float s = switching.set(1f);
            float alpha = 1f - l;
            float scale = AndroidUtilities.lerp(1f, 0.6f, l);
            canvas.save();
            canvas.translate(0, -shift * l);
            if (previousIcon != null) {
                if (s < 1f) {
                    drawIcon(canvas, previousIcon, (1f - s) * alpha, AndroidUtilities.lerp(1f, 0.4f, s) * scale);
                } else {
                    previousIcon = null;
                }
            }
            drawIcon(canvas, icon, alpha * s, scale * AndroidUtilities.lerp(0.4f, 1f, s));
            canvas.restore();
        }
        if (l > 0f) {
            int y = (int) ((1f - l) * shift);
            progress.setBounds(0, y, getWidth(), getHeight() + y);
            progress.setAlpha((int) (255 * l));
            progress.draw(canvas);
            invalidate();
        }
    }

    private void drawIcon(Canvas canvas, Drawable d, float alpha, float scale) {
        if (alpha <= 0f) return;
        int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
        int left = (getWidth() - w) / 2, top = (getHeight() - h) / 2;
        d.setBounds(left, top, left + w, top + h);
        d.setAlpha((int) (255 * alpha));
        canvas.save();
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
        d.draw(canvas);
        canvas.restore();
    }
}

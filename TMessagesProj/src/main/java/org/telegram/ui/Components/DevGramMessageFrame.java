package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;

// DevGram: обводка сообщений поддержавших (как рамки чатов в TikTok) — градиентная рамка вокруг
// пузыря и украшения-эмодзи по углам. Стиль выбирает сам поддержавший (поле «b» его значка),
// видят все пользователи DevGram.
public final class DevGramMessageFrame {

    public static final class Style {
        public final int id;
        public final String title;
        final int[] colors;
        // украшения по углам: верх-лево, верх-право, низ-лево, низ-право (null — нет)
        final String tl, tr, bl, br;

        Style(int id, String title, int[] colors, String tl, String tr, String bl, String br) {
            this.id = id;
            this.title = title;
            this.colors = colors;
            this.tl = tl;
            this.tr = tr;
            this.bl = bl;
            this.br = br;
        }
    }

    public static final Style[] STYLES = {
            new Style(1, "Сакура", new int[]{0xffFF8FB8, 0xffFFC2D9, 0xffFF6FA5}, null, "🌸", "🌸", null),
            new Style(2, "Котики", new int[]{0xffF6B26B, 0xffFFE0A3, 0xffE69138}, "🐱", "🐶", "🐾", "🐾"),
            new Style(3, "Звёзды", new int[]{0xffFFE082, 0xffFFB300, 0xffFFD54F}, null, "⭐", "✨", null),
            new Style(4, "Огонь", new int[]{0xffFFAB00, 0xffFF6A00, 0xffFF1744}, "🔥", null, null, "🔥"),
            new Style(5, "Сердечки", new int[]{0xffFF4D8D, 0xffFF8A80, 0xffFF1F6B}, null, "💖", "💕", null),
            new Style(6, "Лёд", new int[]{0xff80D8FF, 0xffE1F5FE, 0xffB388FF}, "❄️", null, null, "❄️"),
            new Style(7, "Корона", new int[]{0xffFFD700, 0xffFFF3B0, 0xffFF9F00}, null, "👑", null, "✨"),
            new Style(8, "Бабочки", new int[]{0xffB388FF, 0xffE1BEE7, 0xff7C4DFF}, "🦋", null, null, "🦋"),
            new Style(9, "Неон", new int[]{0xff00E5FF, 0xff651FFF, 0xffFF00E5}, null, "✨", null, null),
            new Style(10, "Природа", new int[]{0xff69F0AE, 0xffB9F6CA, 0xff00C853}, "🍀", null, null, "🌿"),
            new Style(11, "Космос", new int[]{0xff7C4DFF, 0xff3D5AFE, 0xff1A237E}, null, "🚀", "🌙", null),
            new Style(12, "Радуга", new int[]{0xffFF5252, 0xffFFD740, 0xff69F0AE, 0xff40C4FF, 0xffE040FB}, null, "🌈", null, null),
    };

    private static final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF tmpRect = new RectF();

    static {
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    private DevGramMessageFrame() {
    }

    public static Style get(int id) {
        for (Style s : STYLES) {
            if (s.id == id) return s;
        }
        return null;
    }

    // rect — тело пузыря (без хвоста), radius — радиус его скругления.
    public static void draw(Canvas canvas, RectF rect, float radius, int styleId, float alpha) {
        Style s = get(styleId);
        if (s == null || alpha <= 0f || rect.width() <= 0 || rect.height() <= 0) return;
        float stroke = AndroidUtilities.dp(2.4f);
        strokePaint.setStrokeWidth(stroke);
        strokePaint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, s.colors, null, Shader.TileMode.CLAMP));
        strokePaint.setAlpha((int) (255 * alpha));
        float inset = stroke / 2f;
        RectF r = tmpRect;
        r.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset);
        float rad = Math.max(0, radius - inset);
        canvas.drawRoundRect(r, rad, rad, strokePaint);

        // украшения сидят на углах, наполовину выходя за пузырь; маленький пузырь — украшения меньше
        float size = Math.min(AndroidUtilities.dp(24), Math.max(AndroidUtilities.dp(16), rect.height() * 0.6f));
        float off = size * 0.35f;
        drawDecor(canvas, s.tl, rect.left - off + size / 2f, rect.top - off + size / 2f, size, -14, alpha);
        drawDecor(canvas, s.tr, rect.right + off - size / 2f, rect.top - off + size / 2f, size, 14, alpha);
        drawDecor(canvas, s.bl, rect.left - off + size / 2f, rect.bottom + off - size / 2f, size, 10, alpha);
        drawDecor(canvas, s.br, rect.right + off - size / 2f, rect.bottom + off - size / 2f, size, -10, alpha);
    }

    private static void drawDecor(Canvas canvas, String emoji, float cx, float cy, float size, float rotation, float alpha) {
        if (emoji == null) return;
        Drawable d = Emoji.getEmojiDrawable(emoji);
        if (d == null) return;
        int half = (int) (size / 2f);
        d.setBounds((int) cx - half, (int) cy - half, (int) cx + half, (int) cy + half);
        d.setAlpha((int) (255 * alpha));
        canvas.save();
        canvas.rotate(rotation, cx, cy);
        d.draw(canvas);
        canvas.restore();
        d.setAlpha(255);
    }
}

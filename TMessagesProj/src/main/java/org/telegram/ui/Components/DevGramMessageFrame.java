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
        // заливка всего пузыря и подобранные к ней цвета текста/ссылок (как чаты в TikTok)
        int[] fill;
        public int text, link;

        Style(int id, String title, int[] colors, String tl, String tr, String bl, String br) {
            this.id = id;
            this.title = title;
            this.colors = colors;
            this.tl = tl;
            this.tr = tr;
            this.bl = bl;
            this.br = br;
        }

        Style fill(int text, int link, int... fill) {
            this.text = text;
            this.link = link;
            this.fill = fill;
            return this;
        }
    }

    public static final Style[] STYLES = {
            new Style(1, "Сакура", new int[]{0xffFF8FB8, 0xffFFC2D9, 0xffFF6FA5}, null, "🌸", "🌸", null).fill(0xff4A1F33, 0xffD81B60, 0xffFFE3EE, 0xffFFD1E3),
            new Style(2, "Котики", new int[]{0xffF6B26B, 0xffFFE0A3, 0xffE69138}, "🐱", "🐶", "🐾", "🐾").fill(0xff4A3418, 0xffC26A00, 0xffFFF4D6, 0xffFFE9B8),
            new Style(3, "Звёзды", new int[]{0xffFFE082, 0xffFFB300, 0xffFFD54F}, null, "⭐", "✨", null).fill(0xff4A3B10, 0xffB8860B, 0xffFFF8DC, 0xffFFEFB0),
            new Style(4, "Огонь", new int[]{0xffFFAB00, 0xffFF6A00, 0xffFF1744}, "🔥", null, null, "🔥").fill(0xff4A1A0C, 0xffD84315, 0xffFFE0CC, 0xffFFC7B0),
            new Style(5, "Сердечки", new int[]{0xffFF4D8D, 0xffFF8A80, 0xffFF1F6B}, null, "💖", "💕", null).fill(0xff4A1426, 0xffE91E63, 0xffFFE1EA, 0xffFFC9D8),
            new Style(6, "Лёд", new int[]{0xff80D8FF, 0xffE1F5FE, 0xffB388FF}, "❄️", null, null, "❄️").fill(0xff13324A, 0xff0277BD, 0xffE3F6FF, 0xffE9E3FF),
            new Style(7, "Корона", new int[]{0xffFFD700, 0xffFFF3B0, 0xffFF9F00}, null, "👑", null, "✨").fill(0xff4A3A0A, 0xffB07A00, 0xffFFF6D0, 0xffFFE7A0),
            new Style(8, "Бабочки", new int[]{0xffB388FF, 0xffE1BEE7, 0xff7C4DFF}, "🦋", null, null, "🦋").fill(0xff2E1A4A, 0xff6A1B9A, 0xffF1E6FF, 0xffE3D4FF),
            new Style(9, "Неон", new int[]{0xff00E5FF, 0xff651FFF, 0xffFF00E5}, null, "✨", null, null).fill(0xffF3E9FF, 0xff00E5FF, 0xff1B1033, 0xff2A0F3D),
            new Style(10, "Природа", new int[]{0xff69F0AE, 0xffB9F6CA, 0xff00C853}, "🍀", null, null, "🌿").fill(0xff133A22, 0xff2E7D32, 0xffE6FAEE, 0xffD2F5DF),
            new Style(11, "Космос", new int[]{0xff7C4DFF, 0xff3D5AFE, 0xff1A237E}, null, "🚀", "🌙", null).fill(0xffEDEBFF, 0xff8C9EFF, 0xff1C1F4A, 0xff2A1747),
            new Style(12, "Радуга", new int[]{0xffFF5252, 0xffFFD740, 0xff69F0AE, 0xff40C4FF, 0xffE040FB}, null, "🌈", null, null).fill(0xff2B2B2B, 0xff3949AB, 0xffFFE7E7, 0xffFFF6D6, 0xffE3FBEA, 0xffE1F4FF, 0xffF6E4FF),
    };

    private static final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    // Кисть заливки пузыря (градиент по его границам). null — у стиля нет заливки.
    public static Paint fillPaint(int styleId, float left, float top, float right, float bottom, float alpha) {
        Style s = get(styleId);
        if (s == null || s.fill == null) return null;
        fillPaint.setShader(s.fill.length == 1 ? null : new LinearGradient(left, top, right, bottom, s.fill, null, Shader.TileMode.CLAMP));
        if (s.fill.length == 1) fillPaint.setColor(s.fill[0]);
        fillPaint.setAlpha((int) (255 * alpha));
        return fillPaint;
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

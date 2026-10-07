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
        // тонкая рамка одним цветом (на тон темнее заливки, как в TikTok); 0 — без рамки
        int border;
        boolean gradientBorder; // градиентная рамка — только у «Неона»
        boolean onEdge;         // украшения на кромке (выглядывают из-за края), иначе — на углах

        Style(int id, String title, int[] colors, String tl, String tr, String bl, String br) {
            this.id = id;
            this.title = title;
            this.colors = colors;
            this.tl = tl;
            this.tr = tr;
            this.bl = bl;
            this.br = br;
        }

        Style border(int color, boolean gradient, boolean onEdge) {
            this.border = color;
            this.gradientBorder = gradient;
            this.onEdge = onEdge;
            return this;
        }

        Style fill(int text, int link, int... fill) {
            this.text = text;
            this.link = link;
            this.fill = fill;
            return this;
        }
    }

    public static final Style[] STYLES = {
            new Style(1, "Сакура", new int[]{0xffFF8FB8, 0xffFFC2D9, 0xffFF6FA5}, null, "🌸", "🌸", null).fill(0xff4A1F33, 0xffD81B60, 0xffFFE3EE, 0xffFFD1E3).border(0, false, false),
            new Style(2, "Котики", new int[]{0xffF6B26B, 0xffFFE0A3, 0xffE69138}, "🐱", "🐶", "🐾", "🐾").fill(0xff4A3418, 0xffC26A00, 0xffFFF4D6, 0xffFFE9B8).border(0xffC98B4B, false, true),
            new Style(3, "Звёзды", new int[]{0xffFFE082, 0xffFFB300, 0xffFFD54F}, null, "⭐", "✨", null).fill(0xff4A3B10, 0xffB8860B, 0xffFFF8DC, 0xffFFEFB0).border(0xffE0B23A, false, false),
            new Style(4, "Огонь", new int[]{0xffFFAB00, 0xffFF6A00, 0xffFF1744}, "🔥", null, null, "🔥").fill(0xff4A1A0C, 0xffD84315, 0xffFFE0CC, 0xffFFC7B0).border(0xffFF7A3D, false, true),
            new Style(5, "Сердечки", new int[]{0xffFF4D8D, 0xffFF8A80, 0xffFF1F6B}, null, "💖", "💕", null).fill(0xff4A1426, 0xffE91E63, 0xffFFE1EA, 0xffFFC9D8).border(0, false, false),
            new Style(6, "Лёд", new int[]{0xff80D8FF, 0xffE1F5FE, 0xffB388FF}, "❄️", null, null, "❄️").fill(0xff13324A, 0xff0277BD, 0xffE3F6FF, 0xffE9E3FF).border(0xff8FC9EE, false, true),
            new Style(7, "Корона", new int[]{0xffFFD700, 0xffFFF3B0, 0xffFF9F00}, null, "👑", null, "✨").fill(0xff4A3A0A, 0xffB07A00, 0xffFFF6D0, 0xffFFE7A0).border(0xffD4A52A, false, false),
            new Style(8, "Бабочки", new int[]{0xffB388FF, 0xffE1BEE7, 0xff7C4DFF}, "🦋", null, null, "🦋").fill(0xff2E1A4A, 0xff6A1B9A, 0xffF1E6FF, 0xffE3D4FF).border(0xffB39DDB, false, true),
            new Style(9, "Неон", new int[]{0xff00E5FF, 0xff651FFF, 0xffFF00E5}, null, "✨", null, null).fill(0xffF3E9FF, 0xff00E5FF, 0xff1B1033, 0xff2A0F3D).border(0, true, false),
            new Style(10, "Природа", new int[]{0xff69F0AE, 0xffB9F6CA, 0xff00C853}, "🍀", null, null, "🌿").fill(0xff133A22, 0xff2E7D32, 0xffE6FAEE, 0xffD2F5DF).border(0xff6CC48A, false, true),
            new Style(11, "Космос", new int[]{0xff7C4DFF, 0xff3D5AFE, 0xff1A237E}, null, "🚀", "🌙", null).fill(0xffEDEBFF, 0xff8C9EFF, 0xff1C1F4A, 0xff2A1747).border(0xff7C83FF, false, false),
            new Style(12, "Радуга", new int[]{0xffFF5252, 0xffFFD740, 0xff69F0AE, 0xff40C4FF, 0xffE040FB}, null, "🌈", null, null).fill(0xff2B2B2B, 0xff3949AB, 0xffFFE7E7, 0xffFFF6D6, 0xffE3FBEA, 0xffE1F4FF, 0xffF6E4FF).border(0, false, false),
            new Style(13, "Мишки", new int[]{0xffB9895F, 0xffEFD9C3}, "🧸", "🍯", null, null).fill(0xff4A3322, 0xffA0522D, 0xffF7EBDD, 0xffEFD9C3).border(0xffB9895F, false, true),
            new Style(14, "Зайчики", new int[]{0xffF2A7C3, 0xffFCE4EC}, "🐰", "🥕", null, null).fill(0xff4A2033, 0xffC2185B, 0xffFFF0F5, 0xffFCE4EC).border(0xffF2A7C3, false, true),
            new Style(15, "Лягушата", new int[]{0xff8BC34A, 0xffD4F0C4}, "🐸", null, null, "🪷").fill(0xff1F3A15, 0xff388E3C, 0xffE8F8E0, 0xffD4F0C4).border(0xff8BC34A, false, true),
            new Style(16, "Осень", new int[]{0xffE39A5B, 0xffFFD9B8}, null, "🍂", "🍁", null).fill(0xff4A2A10, 0xffD2691E, 0xffFFEAD6, 0xffFFD9B8).border(0xffE39A5B, false, false),
            new Style(17, "Море", new int[]{0xff4DD0E1, 0xffB2EBF2}, "🐬", null, null, "🐚").fill(0xff0B3A44, 0xff00838F, 0xffE0F7FA, 0xffB2EBF2).border(0xff4DD0E1, false, true),
            new Style(18, "Клубничка", new int[]{0xffFF8A9B, 0xffFFD6DE}, null, "🍓", "🍰", null).fill(0xff4A1520, 0xffE53950, 0xffFFE8EC, 0xffFFD6DE).border(0, false, false),
            new Style(19, "Ночь", new int[]{0xff3D4785, 0xff1E2448}, null, "🌙", "⭐", null).fill(0xffE8EBFF, 0xffFFD54F, 0xff10142B, 0xff1E2448).border(0xff3D4785, false, false),
            new Style(20, "Конфетти", new int[]{0xffCE93D8, 0xffFFE0B2}, "🎉", null, null, "🎊").fill(0xff33264A, 0xff7B1FA2, 0xffFFF5E6, 0xffF3E8FF).border(0, false, false),
            new Style(21, "Вкусняшки", new int[]{0xffD9A066, 0xffFFE2C2}, null, "🍩", "🍪", null).fill(0xff4A2E12, 0xffE65100, 0xffFFF1E0, 0xffFFE2C2).border(0xffD9A066, false, false),
            new Style(22, "Хэллоуин", new int[]{0xffFF8F00, 0xff3B2410}, "🎃", "👻", null, "🦇").fill(0xffFFE7CC, 0xffFF9800, 0xff2A1A10, 0xff3B2410).border(0xffFF8F00, false, true),
            new Style(23, "Новый год", new int[]{0xff66BB6A, 0xffFFEBEE}, "🎄", "🎁", null, "❄️").fill(0xff1B3A20, 0xffC62828, 0xffE8F5E9, 0xffFFEBEE).border(0xff66BB6A, false, true),
            new Style(24, "Единороги", new int[]{0xffCE93D8, 0xffE1F5FE}, null, "🦄", "🌈", null).fill(0xff3A1F4A, 0xff8E24AA, 0xffF3E5F5, 0xffE1F5FE).border(0xffCE93D8, false, false),
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

    // Кисть-обводка по контуру пузыря (вместе с хвостом — её рисует сам MessageDrawable). null — без рамки.
    public static Paint borderPaint(int styleId, float left, float top, float right, float bottom, float alpha) {
        Style s = get(styleId);
        if (s == null || s.border == 0 && !s.gradientBorder) return null;
        strokePaint.setStrokeWidth(AndroidUtilities.dp(s.gradientBorder ? 2f : 1.5f));
        if (s.gradientBorder) {
            strokePaint.setShader(new LinearGradient(left, top, right, bottom, s.colors, null, Shader.TileMode.CLAMP));
        } else {
            strokePaint.setShader(null);
            strokePaint.setColor(s.border);
        }
        strokePaint.setAlpha((int) (255 * alpha));
        return strokePaint;
    }

    // Рамка по прямоугольнику (для превью) + украшения. rect — тело пузыря, radius — его скругление.
    public static void draw(Canvas canvas, RectF rect, float radius, int styleId, float alpha) {
        draw(canvas, rect, radius, styleId, alpha, true);
    }

    // drawBorder=false — рамку уже нарисовали по настоящему контуру пузыря, нужны только украшения
    public static void draw(Canvas canvas, RectF rect, float radius, int styleId, float alpha, boolean drawBorder) {
        Style s = get(styleId);
        if (s == null || alpha <= 0f || rect.width() <= 0 || rect.height() <= 0) return;
        if (drawBorder && (s.border != 0 || s.gradientBorder)) {
            float stroke = AndroidUtilities.dp(s.gradientBorder ? 2f : 1.5f);
            strokePaint.setStrokeWidth(stroke);
            if (s.gradientBorder) {
                strokePaint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, s.colors, null, Shader.TileMode.CLAMP));
            } else {
                strokePaint.setShader(null);
                strokePaint.setColor(s.border);
            }
            strokePaint.setAlpha((int) (255 * alpha));
            float inset = stroke / 2f;
            RectF r = tmpRect;
            r.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset);
            float rad = Math.max(0, radius - inset);
            canvas.drawRoundRect(r, rad, rad, strokePaint);
        }

        float size = Math.min(AndroidUtilities.dp(26), Math.max(AndroidUtilities.dp(18), rect.height() * 0.7f));
        if (s.onEdge) {
            // как зверушки у TikTok: выглядывают из-за верхней кромки и стоят на нижней, чуть отступив от углов
            float inX = Math.min(size * 0.85f, rect.width() * 0.25f);
            drawDecor(canvas, s.tl, rect.left + inX, rect.top - size * 0.12f, size, -8, alpha);
            drawDecor(canvas, s.tr, rect.right - inX, rect.top - size * 0.12f, size, 8, alpha);
            drawDecor(canvas, s.bl, rect.left + inX, rect.bottom + size * 0.05f, size * 0.8f, 0, alpha);
            drawDecor(canvas, s.br, rect.right - inX, rect.bottom + size * 0.05f, size * 0.8f, 0, alpha);
        } else {
            // как цветы у TikTok: сидят на самом углу и выходят за пузырь
            float out = size * 0.12f;
            drawDecor(canvas, s.tl, rect.left - out, rect.top - out, size, -12, alpha);
            drawDecor(canvas, s.tr, rect.right + out, rect.top - out, size, 12, alpha);
            drawDecor(canvas, s.bl, rect.left - out, rect.bottom + out, size, -8, alpha);
            drawDecor(canvas, s.br, rect.right + out, rect.bottom + out, size, 8, alpha);
        }
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

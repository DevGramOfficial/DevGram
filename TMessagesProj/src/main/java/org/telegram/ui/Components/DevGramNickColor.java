package org.telegram.ui.Components;

import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.LongSparseArray;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.DevGramBadges;
import org.telegram.ui.ActionBar.Theme;

// DevGram: цвет или градиент ника поддержавших — виден всем пользователям DevGram (поле n в значке).
// Цвет пользователя кэшируется и пересчитывается только при изменении значков; градиент — у каждого
// места отрисовки свой (Cache) и пересоздаётся, только когда меняются цвета или ширина имени.
public final class DevGramNickColor {

    public static final class Preset {
        public final String title;
        public final int[] colors;

        Preset(String title, int... colors) {
            this.title = title;
            this.colors = colors;
        }
    }

    // подобраны так, чтобы читались и в светлой, и в тёмной теме
    public static final Preset[] PRESETS = {
            new Preset("Красный", 0xFFE5484D),
            new Preset("Оранжевый", 0xFFF2811D),
            new Preset("Золотой", 0xFFE0A100),
            new Preset("Зелёный", 0xFF30A46C),
            new Preset("Бирюзовый", 0xFF12A594),
            new Preset("Голубой", 0xFF0090D8),
            new Preset("Синий", 0xFF3E63DD),
            new Preset("Фиолетовый", 0xFF8E4EC6),
            new Preset("Розовый", 0xFFD6409F),
            new Preset("Закат", 0xFFFF6A3D, 0xFFE0389A),
            new Preset("Неон", 0xFF00B4D8, 0xFFB04BF2),
            new Preset("Океан", 0xFF1E88E5, 0xFF00BFA5),
            new Preset("Аврора", 0xFF2EC27E, 0xFF7C4DFF),
            new Preset("Огонь", 0xFFFF3D00, 0xFFFFB300),
            new Preset("Лаванда", 0xFF9575CD, 0xFFF06292),
            new Preset("Мята", 0xFF26A69A, 0xFF9CCC65),
            new Preset("Золото", 0xFFB8860B, 0xFFFFC94D),
            new Preset("Вишня", 0xFFC2185B, 0xFF7B1FA2),
    };

    public static final class Cache {
        private int c0, c1, bg;
        private float width;
        private Shader shader;
    }

    // Минимальная контрастность ника с фоном (как у крупного текста по WCAG). Ниже — цвет
    // подмешивается к белому (тёмная тема) или чёрному (светлая), пока ник не станет читаемым:
    // градиент «светлый → чёрный» на тёмной теме иначе растворяется в фоне.
    private static final double MIN_CONTRAST = 3.0;

    public static int readable(int color, int background) {
        int c = color | 0xFF000000;
        int bg = background | 0xFF000000;
        if (ColorUtils.calculateContrast(c, bg) >= MIN_CONTRAST) return c;
        int target = ColorUtils.calculateLuminance(bg) < 0.5 ? 0xFFFFFFFF : 0xFF000000;
        for (int i = 1; i < 10; i++) {
            int mixed = ColorUtils.blendARGB(c, target, i / 10f);
            if (ColorUtils.calculateContrast(mixed, bg) >= MIN_CONTRAST) return mixed;
        }
        return target;
    }

    private static int background() {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    private static final LongSparseArray<int[]> cache = new LongSparseArray<>();
    private static final int[] NONE = new int[0];
    private static int cacheVersion = -1;

    private DevGramNickColor() {
    }

    // Цвета ника (1 — сплошной, 2 — градиент) или null. Только из UI-потока.
    public static int[] of(long userId) {
        if (userId <= 0) return null;
        int version = DevGramBadges.styleVersion;
        if (version != cacheVersion) {
            cache.clear();
            cacheVersion = version;
        }
        int[] c = cache.get(userId);
        if (c == null) {
            c = DevGramBadges.nickColorsOf(userId);
            if (c == null || c.length == 0) c = NONE;
            cache.put(userId, c);
        }
        return c.length == 0 ? null : c;
    }

    // Подкрасить кисть перед отрисовкой имени. true — кисть изменена, после отрисовки вызвать reset().
    public static boolean apply(Paint paint, long userId, float width, Cache holder) {
        int[] c = of(userId);
        return c != null && apply(paint, c, width, holder);
    }

    public static boolean apply(Paint paint, int[] c, float width, Cache holder) {
        return apply(paint, c, width, holder, false);
    }

    // forceShader — и сплошной цвет кладём шейдером: так его не перебьёт setTextColor (SimpleTextView в шапках)
    public static boolean apply(Paint paint, int[] c, float width, Cache holder, boolean forceShader) {
        int alpha = paint.getAlpha();
        final int bg = background();
        final int raw1 = c.length > 1 ? c[1] : c[0];
        if (!forceShader && c.length == 1 || width <= 0) {
            paint.setShader(null);
            paint.setColor(readable(c[0], bg));
        } else {
            Shader shader;
            int first;
            if (holder != null && holder.shader != null && holder.c0 == c[0] && holder.c1 == raw1 && holder.width == width && holder.bg == bg) {
                shader = holder.shader;
                first = readable(c[0], bg);
            } else {
                first = readable(c[0], bg);
                shader = new LinearGradient(0, 0, width, 0, first, readable(raw1, bg), Shader.TileMode.CLAMP);
                if (holder != null) {
                    holder.c0 = c[0];
                    holder.c1 = raw1;
                    holder.bg = bg;
                    holder.width = width;
                    holder.shader = shader;
                }
            }
            paint.setColor(first); // цвет под шейдером — для подкраски эмодзи в имени
            paint.setShader(shader);
        }
        paint.setAlpha(alpha);
        return true;
    }

    public static void reset(Paint paint) {
        paint.setShader(null);
    }
}

package org.telegram.messenger.devgram.utils.ui;

import android.graphics.Paint;
import android.text.TextUtils;

import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.DevGramMaterial3;
import org.telegram.ui.Components.Text;

/** Размеры и анимации кнопок профиля в стиле Material 3: пилюля с иконкой, подпись под ней. */
public final class ProfileActionsUiHelper {

    private ProfileActionsUiHelper() {
    }

    public static boolean isMaterial3Style() {
        // явно включённый стиль перекрывает круглые iOS-кнопки
        return DevGramMaterial3.profileButtonsEnabled();
    }

    /** Высота полосы кнопок под шапкой профиля. */
    public static int getBandHeight() {
        return AndroidUtilities.dp(isMaterial3Style() ? 102 : 74);
    }

    public static float getSidePadding() {
        return AndroidUtilities.dpf2(12);
    }

    /** Промежуток между пилюлями: 12dp, но не меньше 8dp, если кнопок много. */
    public static float getContainerGap(float width, int count) {
        float gap = AndroidUtilities.dpf2(12);
        if (count < 2) {
            return gap;
        }
        return Math.max(AndroidUtilities.dpf2(8), Math.min(gap, (width - AndroidUtilities.dpf2(64) * count) / (count - 1)));
    }

    public static float getContainerHeight() {
        return AndroidUtilities.dpf2(56);
    }

    /** При нажатии пилюля из круглой становится скруглённым прямоугольником. */
    public static float getCornerRadius(float pressProgress) {
        return Math.max(0f, AndroidUtilities.lerp(getContainerHeight() / 2f, AndroidUtilities.dpf2(12), pressProgress));
    }

    public static int getIconSize() {
        return AndroidUtilities.dp(24);
    }

    public static int getStateLayerColor(int color) {
        return ColorUtils.setAlphaComponent(color, Math.round(255 * 0.1f));
    }

    public static float getLabelAlpha(float fraction) {
        return Utilities.clamp01((fraction - 0.6f) / 0.4f);
    }

    public static Text createLabel(CharSequence text) {
        return new Text(text, 14, AndroidUtilities.bold());
    }

    public static float getLabelTopOffset(Text text) {
        Paint.FontMetricsInt metrics = text.getFontMetricsInt();
        return AndroidUtilities.dpf2(6) + metrics.ascent - metrics.top;
    }

    /** Ужимает подпись до 0.8 масштаба, а дальше обрезает многоточием; возвращает масштаб. */
    public static float fitLabel(Text text, CharSequence title, float width) {
        text.setText(title);
        float available = width - AndroidUtilities.dpf2(8);
        float current = text.getCurrentWidth();
        if (current <= available || available <= 0) {
            return 1f;
        }
        float scale = available / current;
        if (scale >= 0.8f) {
            return scale;
        }
        text.setText(TextUtils.ellipsize(title, text.paint, available / 0.8f, TextUtils.TruncateAt.END));
        return 0.8f;
    }

    public static <K> SpringAnimation createPressSpring(K target, FloatPropertyCompat<K> property) {
        return new SpringAnimation(target, property)
                .setMinimumVisibleChange(0.001f)
                .setSpring(new SpringForce().setDampingRatio(0.6f).setStiffness(800f));
    }
}

package org.telegram.messenger.devgram.utils.ui;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import org.telegram.messenger.Utilities;
public abstract class Material3Tokens {
    public static SpringAnimation createSpring(float f, float f2, float f3, final Utilities.Callback<Float> callback) {
        SpringAnimation springAnimation = new SpringAnimation(new FloatValueHolder(0.0f));
        springAnimation.setMinimumVisibleChange(f3);
        springAnimation.setSpring(new SpringForce(0.0f).setDampingRatio(f).setStiffness(f2));
        springAnimation.addUpdateListener((dynamicAnimation, f4, f5) -> callback.run(Float.valueOf(f4)));
        return springAnimation;
    }

    public static void animate(SpringAnimation springAnimation, float f, float f2) {
        springAnimation.cancel();
        springAnimation.setStartValue(f);
        springAnimation.getSpring().setFinalPosition(f2);
        springAnimation.start();
    }
}

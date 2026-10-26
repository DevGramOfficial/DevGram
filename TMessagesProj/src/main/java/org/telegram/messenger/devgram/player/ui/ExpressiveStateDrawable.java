package org.telegram.messenger.devgram.player.ui;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.SpringAnimation;
import org.telegram.messenger.devgram.utils.ui.Material3Tokens;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
public class ExpressiveStateDrawable extends Drawable implements Drawable.Callback {
    private boolean alwaysShowContainer;
    private int containerColor;
    private int contentColor;
    private int dotColor;
    private float dotOffset;
    private float innerRadius;
    private float inset;
    private Runnable onProgressChanged;
    private int outlineColor;
    private float pressShape;
    private final SpringAnimation pressShapeSpring;
    private boolean pressed;
    private final float pressedRadius;
    private final RippleDrawable ripple;
    private float selectContainer;
    private final SpringAnimation selectContainerSpring;
    private float selectShape;
    private final SpringAnimation selectShapeSpring;
    private boolean selected;
    private int selectedContainerColor;
    private float selectedRadius;
    private final Paint containerPaint = new Paint(1);
    private final Paint dotPaint = new Paint(1);
    private final Paint outlinePaint = new Paint(1);
    private final RectF rect = new RectF();
    private final Path shapePath = new Path();
    private final float[] radii = new float[8];
    private final Rect rippleBounds = new Rect();
    private float insetY = -1.0f;
    private int innerSide = 0;
    private int alpha = 255;

    @Override
    public int getOpacity() {
        return -3;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    public ExpressiveStateDrawable(float f) {
        float fDpf2 = AndroidUtilities.dpf2(f);
        this.pressedRadius = fDpf2;
        this.selectedRadius = fDpf2;
        this.pressShapeSpring = createSpring(0.6f, 800.0f, obj -> lambda_new_0((Float) obj));
        this.selectShapeSpring = createSpring(0.6f, 800.0f, obj -> lambda_new_1((Float) obj));
        this.selectContainerSpring = createSpring(1.0f, 1600.0f, obj -> lambda_new_2((Float) obj));
        RippleDrawable rippleDrawable = new RippleDrawable(ColorStateList.valueOf(0), null, new ColorDrawable(-1));
        this.ripple = rippleDrawable;
        rippleDrawable.setCallback(this);
    }
    public /* synthetic */ void lambda_new_0(Float f) {
        this.pressShape = f.floatValue();
    }
    public /* synthetic */ void lambda_new_1(Float f) {
        this.selectShape = f.floatValue();
    }
    public /* synthetic */ void lambda_new_2(Float f) {
        this.selectContainer = Utilities.clamp01(f.floatValue());
    }

    private SpringAnimation createSpring(float f, float f2, final Utilities.Callback<Float> callback) {
        return Material3Tokens.createSpring(f, f2, 0.002f, obj -> lambda_createSpring_3(callback, (Float) obj));
    }
    public /* synthetic */ void lambda_createSpring_3(Utilities.Callback callback, Float f) {
        callback.run(f);
        notifyProgress();
        invalidateSelf();
    }

    private void notifyProgress() {
        Runnable runnable = this.onProgressChanged;
        if (runnable != null) {
            runnable.run();
        }
    }

    public ExpressiveStateDrawable setOnProgressChanged(Runnable runnable) {
        this.onProgressChanged = runnable;
        return this;
    }

    public float getPressProgress() {
        return this.pressShape;
    }

    public float getSelectProgress() {
        return this.selectContainer;
    }

    public ExpressiveStateDrawable setInnerCorners(int i, float f) {
        this.innerSide = i;
        this.innerRadius = AndroidUtilities.dpf2(f);
        invalidateSelf();
        return this;
    }

    public ExpressiveStateDrawable setDot(int i, float f) {
        float fDpf2 = AndroidUtilities.dpf2(f);
        if (this.dotColor == i && this.dotOffset == fDpf2) {
            return this;
        }
        this.dotColor = i;
        this.dotOffset = fDpf2;
        invalidateSelf();
        return this;
    }

    public ExpressiveStateDrawable setSelectedContainerColor(int i) {
        if (this.selectedContainerColor != i) {
            this.selectedContainerColor = i;
            invalidateSelf();
        }
        return this;
    }

    public ExpressiveStateDrawable setSelectedRadius(float f) {
        this.selectedRadius = AndroidUtilities.dpf2(f);
        invalidateSelf();
        return this;
    }

    public ExpressiveStateDrawable setInset(float f) {
        this.inset = Math.max(0.0f, AndroidUtilities.dpf2(f));
        this.insetY = -1.0f;
        updateRippleBounds();
        invalidateSelf();
        return this;
    }

    public ExpressiveStateDrawable setInset(float f, float f2) {
        this.inset = Math.max(0.0f, AndroidUtilities.dpf2(f));
        this.insetY = Math.max(0.0f, AndroidUtilities.dpf2(f2));
        updateRippleBounds();
        invalidateSelf();
        return this;
    }

    public ExpressiveStateDrawable setOutline(int i, float f) {
        float fDpf2 = AndroidUtilities.dpf2(f);
        if (this.outlineColor == i && this.outlinePaint.getStrokeWidth() == fDpf2) {
            return this;
        }
        this.outlineColor = i;
        this.outlinePaint.setStyle(Paint.Style.STROKE);
        this.outlinePaint.setStrokeWidth(fDpf2);
        invalidateSelf();
        return this;
    }

    public int getContentColor() {
        return this.contentColor;
    }

    public ExpressiveStateDrawable setColors(int i, int i2) {
        if (this.contentColor == i && this.containerColor == i2) {
            return this;
        }
        this.contentColor = i;
        this.containerColor = i2;
        this.ripple.setColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(i, (int) (Color.alpha(i) * 0.1f))));
        invalidateSelf();
        return this;
    }

    public ExpressiveStateDrawable setAlwaysShowContainer(boolean z) {
        if (this.alwaysShowContainer != z) {
            this.alwaysShowContainer = z;
            invalidateSelf();
        }
        return this;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean z, boolean z2) {
        if (this.selected == z) {
            if (z2) {
                return;
            }
            if (this.selectShape == (z ? 1.0f : 0.0f)) {
                return;
            }
        }
        this.selected = z;
        float f = z ? 1.0f : 0.0f;
        SpringAnimation springAnimation = this.selectShapeSpring;
        if (z2) {
            Material3Tokens.animate(springAnimation, this.selectShape, f);
            Material3Tokens.animate(this.selectContainerSpring, this.selectContainer, f);
            return;
        }
        springAnimation.cancel();
        this.selectContainerSpring.cancel();
        this.selectContainer = f;
        this.selectShape = f;
        notifyProgress();
        invalidateSelf();
    }

    public void setPressedState(boolean z) {
        if (this.pressed == z) {
            return;
        }
        this.pressed = z;
        Material3Tokens.animate(this.pressShapeSpring, this.pressShape, z ? 1.0f : 0.0f);
    }

    public float getCornerRadius() {
        Rect bounds = getBounds();
        float fWidth = bounds.width() - (this.inset * 2.0f);
        float fHeight = bounds.height();
        float f = this.insetY;
        if (f < 0.0f) {
            f = this.inset;
        }
        float fMax = Math.max(0.0f, Math.min(fWidth, fHeight - (f * 2.0f)) / 2.0f);
        return Math.max(0.0f, Math.min(fMax, AndroidUtilities.lerp(AndroidUtilities.lerp(fMax, Math.min(this.selectedRadius, fMax), this.selectShape), Math.min(this.pressedRadius, fMax), this.pressShape)));
    }

    @Override
    public boolean onStateChange(int[] iArr) {
        boolean z = false;
        boolean z2 = false;
        boolean z3 = false;
        for (int i : iArr) {
            if (i == 16842919) {
                z2 = true;
            } else if (i == 16842910) {
                z3 = true;
            }
        }
        if (z2 && z3) {
            z = true;
        }
        setPressedState(z);
        return this.ripple.setState(iArr);
    }

    @Override
    public void setHotspot(float f, float f2) {
        this.ripple.setHotspot(f, f2);
    }

    @Override
    public boolean setVisible(boolean z, boolean z2) {
        this.ripple.setVisible(z, z2);
        return super.setVisible(z, z2);
    }

    @Override
    public void onBoundsChange(Rect rect) {
        super.onBoundsChange(rect);
        updateRippleBounds();
    }

    private void updateRippleBounds() {
        Rect bounds = getBounds();
        int iRound = Math.round(this.inset);
        float f = this.insetY;
        if (f < 0.0f) {
            f = this.inset;
        }
        int iRound2 = Math.round(f);
        this.rippleBounds.set(bounds.left + iRound, bounds.top + iRound2, bounds.right - iRound, bounds.bottom - iRound2);
        if (this.rippleBounds.width() < 0 || this.rippleBounds.height() < 0) {
            this.rippleBounds.setEmpty();
        }
        this.ripple.setBounds(this.rippleBounds);
    }

    @Override
    public void jumpToCurrentState() {
        this.pressShapeSpring.cancel();
        this.pressShape = this.pressed ? 1.0f : 0.0f;
        this.ripple.jumpToCurrentState();
        notifyProgress();
        invalidateSelf();
    }

    public Drawable createShapeFill(int i) {
        return new ShapeFill(i);
    }
    public boolean prepareShape(Rect rect) {
        this.rect.set(rect);
        RectF rectF = this.rect;
        float f = this.inset;
        float f2 = this.insetY;
        if (f2 < 0.0f) {
            f2 = f;
        }
        rectF.inset(f, f2);
        return !this.rect.isEmpty();
    }
    public float getInnerRadius(float f) {
        return this.innerSide == 0 ? f : Math.max(0.0f, Math.min(f, AndroidUtilities.lerp(Math.min(this.innerRadius, f), f, this.selectShape)));
    }

    @Override
    public void invalidateDrawable(Drawable drawable) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable drawable, Runnable runnable, long j) {
        scheduleSelf(runnable, j);
    }

    @Override
    public void unscheduleDrawable(Drawable drawable, Runnable runnable) {
        unscheduleSelf(runnable);
    }

    @Override
    public void draw(Canvas canvas) {
        int i;
        int i2;
        if (prepareShape(getBounds())) {
            float cornerRadius = getCornerRadius();
            float innerRadius = getInnerRadius(cornerRadius);
            float f = this.alwaysShowContainer ? 1.0f : this.selectContainer;
            if (f > 0.0f && (i2 = this.containerColor) != 0) {
                this.containerPaint.setColor(i2);
                Paint paint = this.containerPaint;
                paint.setAlpha((int) (((paint.getAlpha() * f) * this.alpha) / 255.0f));
                drawShape(canvas, cornerRadius, innerRadius, this.containerPaint);
            }
            if (this.selectContainer > 0.0f && (i = this.selectedContainerColor) != 0) {
                this.containerPaint.setColor(i);
                Paint paint2 = this.containerPaint;
                paint2.setAlpha((int) (((paint2.getAlpha() * this.selectContainer) * this.alpha) / 255.0f));
                drawShape(canvas, cornerRadius, innerRadius, this.containerPaint);
            }
            if (this.contentColor != 0) {
                int iSave = canvas.save();
                canvas.clipPath(buildShape(cornerRadius, innerRadius));
                this.ripple.draw(canvas);
                canvas.restoreToCount(iSave);
            }
            if (this.outlineColor != 0) {
                float strokeWidth = this.outlinePaint.getStrokeWidth() / 2.0f;
                this.rect.inset(strokeWidth, strokeWidth);
                this.outlinePaint.setColor(this.outlineColor);
                this.outlinePaint.setAlpha((int) ((Color.alpha(this.outlineColor) * this.alpha) / 255.0f));
                float f2 = cornerRadius - strokeWidth;
                canvas.drawRoundRect(this.rect, Math.max(0.0f, f2), Math.max(0.0f, f2), this.outlinePaint);
                float f3 = -strokeWidth;
                this.rect.inset(f3, f3);
            }
            int i3 = this.dotColor;
            if (i3 == 0 || this.selectContainer <= 0.0f) {
                return;
            }
            this.dotPaint.setColor(i3);
            this.dotPaint.setAlpha((int) (((Color.alpha(this.dotColor) * this.selectContainer) * this.alpha) / 255.0f));
            canvas.drawCircle(this.rect.centerX(), this.rect.centerY() + this.dotOffset, AndroidUtilities.dpf2(2.0f) * ((this.selectContainer * 0.5f) + 0.5f), this.dotPaint);
        }
    }
    public void drawShape(Canvas canvas, float f, float f2, Paint paint) {
        if (this.innerSide == 0 || f2 == f) {
            canvas.drawRoundRect(this.rect, f, f, paint);
        } else {
            canvas.drawPath(buildShape(f, f2), paint);
        }
    }

    private Path buildShape(float f, float f2) {
        int i = this.innerSide;
        float f3 = (i != 0 && i == 1) ? f2 : f;
        if (i != 0 && i == 2) {
            f = f2;
        }
        float[] fArr = this.radii;
        fArr[7] = f3;
        fArr[6] = f3;
        fArr[1] = f3;
        fArr[0] = f3;
        fArr[5] = f;
        fArr[4] = f;
        fArr[3] = f;
        fArr[2] = f;
        this.shapePath.rewind();
        this.shapePath.addRoundRect(this.rect, this.radii, Path.Direction.CW);
        return this.shapePath;
    }

    @Override
    public void setAlpha(int i) {
        this.alpha = i;
        this.ripple.setAlpha(i);
        invalidateSelf();
    }

    public final class ShapeFill extends Drawable {
        private final Paint paint;

        @Override
        public int getOpacity() {
            return -3;
        }

        public ShapeFill(int i) {
            Paint paint = new Paint(1);
            this.paint = paint;
            paint.setColor(i);
        }

        @Override
        public void draw(Canvas canvas) {
            if (ExpressiveStateDrawable.this.prepareShape(getBounds())) {
                float cornerRadius = ExpressiveStateDrawable.this.getCornerRadius();
                ExpressiveStateDrawable expressiveStateDrawable = ExpressiveStateDrawable.this;
                expressiveStateDrawable.drawShape(canvas, cornerRadius, expressiveStateDrawable.getInnerRadius(cornerRadius), this.paint);
            }
        }

        @Override
        public void setAlpha(int i) {
            this.paint.setAlpha(i);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            this.paint.setColorFilter(colorFilter);
        }
    }
}

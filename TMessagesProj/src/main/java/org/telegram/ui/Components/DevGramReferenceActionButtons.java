package org.telegram.ui.Components;
import org.telegram.messenger.AndroidUtilities;

import android.graphics.Outline;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import java.util.Map;
import java.util.WeakHashMap;

/** Adapted from the user supplied Custom Profile editor. */
final class DevGramReferenceActionButtons {
    private static final Map<View, Shape> BUTTONS = new WeakHashMap();
    private static final Map<ViewGroup, Boolean> GROUPS = new WeakHashMap();
    private static final View.OnLayoutChangeListener LAYOUT = new View.OnLayoutChangeListener() {
        @Override // android.view.View.OnLayoutChangeListener
        public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
            if ((view instanceof ViewGroup) && DevGramReferenceActionButtons.GROUPS.containsKey(view)) {
                DevGramReferenceActionButtons.refresh((ViewGroup) view);
            }
            if (DevGramReferenceActionButtons.BUTTONS.containsKey(view)) {
                DevGramReferenceActionButtons.refreshParent(view);
            }
        }
    };
    private static final View.OnAttachStateChangeListener ATTACH = new View.OnAttachStateChangeListener() {
        @Override // android.view.View.OnAttachStateChangeListener
        public void onViewDetachedFromWindow(View view) {
        }

        @Override // android.view.View.OnAttachStateChangeListener
        public void onViewAttachedToWindow(View view) {
            DevGramReferenceActionButtons.refreshParent(view);
        }
    };

    private DevGramReferenceActionButtons() {
    }

    static <T extends View> T style(T t) {
        return style(t, 0, 0, Float.MAX_VALUE);
    }

    static <T extends View> T style(T t, int horizontalInset, int verticalInset, float maximumRadius) {
        if (t == null) {
            return null;
        }
        Map<View, Shape> map = BUTTONS;
        if (!map.containsKey(t)) {
            Shape shape = new Shape();
            shape.horizontalInset = horizontalInset;
            shape.verticalInset = verticalInset;
            shape.maximumRadius = maximumRadius;
            map.put(t, shape);
            t.setOutlineProvider(shape);
            t.setClipToOutline(true);
            t.addOnLayoutChangeListener(LAYOUT);
            t.addOnAttachStateChangeListener(ATTACH);
        }
        refreshParent(t);
        return t;
    }

    // Outline paths cannot clip asymmetric corners on older Android versions.
    // Use the same cached path for the row's background, text and overlays.
    static int clip(Canvas canvas, View view) {
        Shape shape = BUTTONS.get(view);
        if (shape == null || shape.w <= 0 || shape.h <= 0) return -1;
        int save = canvas.save();
        canvas.clipPath(shape.path);
        return save;
    }

    /** Adapted from the user supplied Custom Profile editor. */
    public static void refreshParent(View view) {
        if (view.getParent() instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view.getParent();
            Map<ViewGroup, Boolean> map = GROUPS;
            if (!map.containsKey(viewGroup)) {
                map.put(viewGroup, Boolean.TRUE);
                viewGroup.addOnLayoutChangeListener(LAYOUT);
            }
            refresh(viewGroup);
            return;
        }
        apply(view, 0);
    }

    /** Adapted from the user supplied Custom Profile editor. */
    public static void refresh(ViewGroup viewGroup) {
        float fDpf = AndroidUtilities.dpf2(12.0f);
        float fDpf2 = AndroidUtilities.dpf2(4.0f);
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View childAt = viewGroup.getChildAt(i);
            if (BUTTONS.containsKey(childAt) && childAt.getVisibility() == 0) {
                int iNeighbor = 0;
                for (int i2 = 0; i2 < viewGroup.getChildCount(); i2++) {
                    View childAt2 = viewGroup.getChildAt(i2);
                    if (i != i2 && BUTTONS.containsKey(childAt2) && childAt2.getVisibility() == 0 && childAt2.getWidth() > 0 && childAt2.getHeight() > 0) {
                        iNeighbor |= DevGramReferenceActionShape.neighbor(childAt.getLeft(), childAt.getTop(), childAt.getRight(), childAt.getBottom(), childAt2.getLeft(), childAt2.getTop(), childAt2.getRight(), childAt2.getBottom(), fDpf, fDpf2);
                    }
                }
                apply(childAt, iNeighbor);
            }
        }
    }

    private static void apply(View view, int i) {
        Shape shape = BUTTONS.get(view);
        if (shape == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }
        int width = view.getWidth();
        int height = view.getHeight();
        if (shape.w == width && shape.h == height && shape.connections == i && shape.background == view.getBackground()) {
            return;
        }
        shape.w = width;
        shape.h = height;
        shape.connections = i;
        float f = Math.max(0, width - 2 * shape.horizontalInset);
        float f2 = Math.max(0, height - 2 * shape.verticalInset);
        shape.radii = DevGramReferenceActionShape.radii(f, f2, i, AndroidUtilities.dpf2(6.0f), shape.maximumRadius);
        int iRound = i == 0 ? Math.round(shape.radii[0] / AndroidUtilities.density) : 6;
        if (shape.nativeRadius != iRound) {
            shape.nativeRadius = iRound;
            if (view instanceof org.telegram.ui.Stories.recorder.ButtonWithCounterView) ((org.telegram.ui.Stories.recorder.ButtonWithCounterView)view).setRoundRadius(iRound);
        }
        Drawable background = view.getBackground();
        if (background instanceof ColorDrawable) {
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(((ColorDrawable) background).getColor());
            view.setBackground(gradientDrawable);
            background = gradientDrawable;
        }
        round(background, shape.radii);
        if (view.getClass().getName().contains("ButtonWithCounterView") && (view instanceof ViewGroup)) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i2 = 0; i2 < viewGroup.getChildCount(); i2++) {
                round(viewGroup.getChildAt(i2).getBackground(), shape.radii);
            }
        }
        shape.background = background;
        shape.path.reset();
        shape.path.addRoundRect(new RectF(shape.horizontalInset, shape.verticalInset,
                width - shape.horizontalInset, height - shape.verticalInset), shape.radii, Path.Direction.CW);
        view.invalidateOutline();
        view.invalidate();
    }

    private static void round(Drawable drawable, float[] fArr) {
        if (drawable == null) {
            return;
        }
        Drawable drawableMutate = drawable.mutate();
        if (drawableMutate instanceof GradientDrawable) {
            ((GradientDrawable) drawableMutate).setCornerRadii(fArr);
            return;
        }
        if (drawableMutate instanceof ShapeDrawable) {
            ((ShapeDrawable) drawableMutate).setShape(new RoundRectShape(fArr, null, null));
            return;
        }
        int i = 0;
        if (drawableMutate instanceof LayerDrawable) {
            LayerDrawable layerDrawable = (LayerDrawable) drawableMutate;
            while (i < layerDrawable.getNumberOfLayers()) {
                round(layerDrawable.getDrawable(i), fArr);
                i++;
            }
            return;
        }
        if (drawableMutate instanceof InsetDrawable) {
            round(((InsetDrawable) drawableMutate).getDrawable(), fArr);
            return;
        }
        if ((drawableMutate instanceof StateListDrawable) && Build.VERSION.SDK_INT >= 29) {
            StateListDrawable stateListDrawable = (StateListDrawable) drawableMutate;
            while (i < stateListDrawable.getStateCount()) {
                round(stateListDrawable.getStateDrawable(i), fArr);
                i++;
            }
            return;
        }
        if (drawableMutate.getCurrent() != drawableMutate) {
            round(drawableMutate.getCurrent(), fArr);
        }
    }

    private static final class Shape extends ViewOutlineProvider {
        Drawable background;
        int connections;
        int h;
        int nativeRadius;
        int horizontalInset;
        int verticalInset;
        float maximumRadius;
        final Path path;
        float[] radii;
        int w;

        private Shape() {
            this.connections = -1;
            this.nativeRadius = -1;
            this.path = new Path();
        }

        @Override // android.view.ViewOutlineProvider
        public void getOutline(View view, Outline outline) {
            int i;
            int i2 = this.w;
            if (i2 <= 0 || (i = this.h) <= 0 || this.radii == null) {
                outline.setEmpty();
                return;
            }
            if (this.connections == 0) {
                outline.setRoundRect(horizontalInset, verticalInset,
                        i2 - horizontalInset, i - verticalInset, this.radii[0]);
            } else if (Build.VERSION.SDK_INT >= 30) {
                outline.setPath(this.path);
            } else {
                outline.setConvexPath(this.path);
            }
        }
    }
}

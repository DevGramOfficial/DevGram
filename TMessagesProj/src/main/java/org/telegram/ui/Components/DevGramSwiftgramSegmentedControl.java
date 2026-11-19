package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramSwiftgramDesign;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

/** ItemListControllerSegmentedTitleView / HorizontalTabsComponent: 44pt glass and a 3pt inset lens. */
public final class DevGramSwiftgramSegmentedControl extends FrameLayout {
    private final TextView[] labels;
    private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF lens = new RectF();
    private final float[] natural;
    private int selected;
    private float selection;
    private ValueAnimator animator;

    public DevGramSwiftgramSegmentedControl(Context context, String[] titles, int initial, Utilities.Callback<Integer> changed) {
        super(context);
        setWillNotDraw(false);
        selected = initial; selection = initial;
        labels = new TextView[titles.length]; natural = new float[titles.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView label = labels[i] = new TextView(context);
            label.setText(titles[i]); label.setTextSize(15);
            label.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setIncludeFontPadding(false); label.setGravity(Gravity.CENTER);
            // The natural cell width includes 16pt side space. Keep the label's
            // own box unpadded so a narrow navigation row can use that space.
            label.setPadding(0, 0, 0, 0);
            label.setSelected(i == selected);
            natural[i] = label.getPaint().measureText(titles[i]) + AndroidUtilities.dp(32);
            label.setOnClickListener(v -> {
                if (selected == index) return;
                selected = index;
                for (int j = 0; j < labels.length; j++) labels[j].setSelected(j == selected);
                if (animator != null) animator.cancel();
                if (DevGramSwiftgramDesign.section("animations")) {
                    animator = ValueAnimator.ofFloat(selection, selected);
                    animator.setDuration(300); animator.setInterpolator(DevGramSwiftgramSpring::navigation);
                    animator.addUpdateListener(a -> { selection = (float) a.getAnimatedValue(); invalidate(); });
                    animator.start();
                } else { selection = selected; invalidate(); }
                changed.run(index);
            });
            addView(label);
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        float desired = AndroidUtilities.dp(6);
        for (float width : natural) desired += width;
        int width = resolveSize((int) Math.ceil(desired), widthSpec), height = AndroidUtilities.dp(44);
        float total = desired - AndroidUtilities.dp(6), available = Math.max(0, width - AndroidUtilities.dp(6));
        for (int i = 0; i < labels.length; i++) labels[i].measure(MeasureSpec.makeMeasureSpec(Math.round(available * natural[i] / Math.max(1, total)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - AndroidUtilities.dp(6), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = AndroidUtilities.dp(3), y = AndroidUtilities.dp(3);
        for (TextView label : labels) { label.layout(x, y, x + label.getMeasuredWidth(), y + label.getMeasuredHeight()); x += label.getMeasuredWidth(); }
    }

    @Override protected void onDraw(Canvas canvas) {
        int first = Math.max(0, Math.min(labels.length - 1, (int) Math.floor(selection))), next = Math.min(labels.length - 1, first + 1);
        float fraction = selection - first;
        lens.set(labels[first].getLeft() + (labels[next].getLeft() - labels[first].getLeft()) * fraction, AndroidUtilities.dp(3), labels[first].getRight() + (labels[next].getRight() - labels[first].getRight()) * fraction, getHeight() - AndroidUtilities.dp(3));
        lensPaint.setColor(Theme.isCurrentThemeDark() ? 0x28ffffff : 0x18000000);
        canvas.drawRoundRect(lens, lens.height() / 2, lens.height() / 2, lensPaint);
    }
    @Override protected void onDetachedFromWindow() { if (animator != null) animator.cancel(); super.onDetachedFromWindow(); }
}

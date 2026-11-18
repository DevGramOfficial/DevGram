package org.telegram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DevGramStudioControls;
import org.telegram.ui.Components.LayoutHelper;

/** The reference's 36 dp cancel/tick pill grows into its save/workshop panel. */
final class DevGramProfileEditBar extends FrameLayout {
    private final LinearLayout pill, panel;
    private final Theme.ResourcesProvider resources;
    private ValueAnimator animation;
    private boolean expanded, busy;

    DevGramProfileEditBar(Context context, Theme.ResourcesProvider resources, Runnable cancel, Runnable save, Runnable workshop) {
        super(context); this.resources = resources;
        setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_dialogBackground, resources), Theme.getColor(Theme.key_divider, resources)));
        setElevation(dp(6)); setClipToOutline(true);
        pill = new LinearLayout(context); pill.setGravity(Gravity.CENTER);
        pill.addView(icon(R.drawable.msg_close, "Отменить правку", cancel), LayoutHelper.createLinear(46, -1));
        View separator = new View(context);separator.setBackgroundColor(Theme.getColor(Theme.key_divider, resources));
        pill.addView(separator, LayoutHelper.createLinear(1, -1, 0, 8, 0, 8));
        pill.addView(icon(R.drawable.ic_ab_done, "Сохранить оформление", () -> expand(true)), LayoutHelper.createLinear(46, -1));
        addView(pill, LayoutHelper.createFrame(93, 36, Gravity.TOP | Gravity.LEFT));
        panel = DevGramStudioControls.column(context); panel.setVisibility(GONE); panel.setAlpha(0);
        panel.addView(action("Сохранить", save), LayoutHelper.createLinear(-1, 46));
        View line = new View(context); line.setBackgroundColor(Theme.getColor(Theme.key_divider, resources));
        panel.addView(line, LayoutHelper.createLinear(-1, 1, 14, 0, 14, 0));
        panel.addView(action("Мастерская", () -> { expand(false); workshop.run(); }), LayoutHelper.createLinear(-1, 46));
        addView(panel, LayoutHelper.createFrame(200, 93, Gravity.TOP | Gravity.LEFT));
    }

    private static int dp(float value) { return AndroidUtilities.dp(value); }
    private View icon(int resource, String description, Runnable click) {
        ImageView icon = new ImageView(getContext()); icon.setImageResource(resource); icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setColorFilter(Theme.getColor(Theme.key_dialogTextBlack, resources)); icon.setContentDescription(DevGramLocale.text(description));
        icon.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resources), 2));
        icon.setOnClickListener(v -> click.run()); return icon;
    }
    private View action(String title, Runnable click) {
        TextView row = DevGramStudioControls.text(getContext(), title, 14, false, resources); row.setGravity(Gravity.CENTER);
        row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resources), 2));
        row.setOnClickListener(v -> click.run()); return row;
    }
    boolean expanded() { return expanded; }
    void setBusy(boolean value) {
        busy = value; setAlpha(value ? .5f : 1);
        enable(pill, !value && !expanded); enable(panel, !value && expanded);
    }
    private void enable(ViewGroup group, boolean value) {
        for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).setEnabled(value);
    }
    void expand(boolean value) {
        if (busy) return;
        if (expanded == value && animation == null) return;
        expanded = value;
        if (animation != null) animation.cancel();
        int fromWidth = getWidth() > 0 ? getWidth() : dp(93), fromHeight = getHeight() > 0 ? getHeight() : dp(36);
        float fromPanel = panel.getAlpha(), fromPill = pill.getAlpha();
        pill.setVisibility(VISIBLE);panel.setVisibility(VISIBLE);
        panel.setClickable(value);pill.setClickable(!value);
        enable(panel, value);enable(pill, !value);
        animation = ValueAnimator.ofFloat(0, 1);animation.setDuration(220);animation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animation.addUpdateListener(animator -> {
            float progress = (float) animator.getAnimatedValue();
            ViewGroup.LayoutParams params = getLayoutParams();
            if (params == null) return;
            params.width = Math.round(fromWidth + (dp(value ? 200 : 93) - fromWidth) * progress);
            params.height = Math.round(fromHeight + (dp(value ? 93 : 36) - fromHeight) * progress);setLayoutParams(params);
            panel.setAlpha(fromPanel + ((value ? 1 : 0) - fromPanel) * progress);
            pill.setAlpha(fromPill + ((value ? 0 : 1) - fromPill) * progress);
            if (progress == 1) { panel.setVisibility(value ? VISIBLE : GONE);pill.setVisibility(value ? GONE : VISIBLE);animation = null; }
        });animation.start();
    }
    @Override protected void onDetachedFromWindow() { if (animation != null) { animation.cancel();animation = null; }super.onDetachedFromWindow(); }
}

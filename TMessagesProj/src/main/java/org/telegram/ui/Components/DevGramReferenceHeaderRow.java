package org.telegram.ui.Components;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import androidx.core.graphics.ColorUtils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Setting row and switch adapted from the supplied Custom Profile editor. */
public final class DevGramReferenceHeaderRow extends LinearLayout {
    private boolean checked;
    public final TextView textView;
    private final Toggle toggle;
    public final TextView valueTextView;

    public DevGramReferenceHeaderRow(Context context, boolean z) {
        super(context);
        setOrientation(1);
        setMinimumHeight(AndroidUtilities.dp(64.0f));
        setPadding(AndroidUtilities.dp(24.0f), AndroidUtilities.dp(14.0f), AndroidUtilities.dp(24.0f), AndroidUtilities.dp(14.0f));
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(1);
        TextView textView = new TextView(context);
        this.textView = textView;
        textView.setTextSize(16.0f);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setGravity(8388611);
        linearLayout.addView(textView, new LinearLayout.LayoutParams(-1, -2));
        TextView textView2 = new TextView(context);
        this.valueTextView = textView2;
        textView2.setTextSize(13.0f);
        textView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        textView2.setVisibility(8);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = AndroidUtilities.dp(4.0f);
        linearLayout.addView(textView2, layoutParams);
        LinearLayout linearLayout2 = new LinearLayout(context);
        linearLayout2.setGravity(16);
        linearLayout2.addView(linearLayout, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Toggle toggle = z ? new Toggle(context) : null;
        this.toggle = toggle;
        if (toggle != null) {
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(AndroidUtilities.dp(46.0f), AndroidUtilities.dp(28.0f));
            layoutParams2.leftMargin = AndroidUtilities.dp(16.0f);
            linearLayout2.addView(toggle, layoutParams2);
            setBackground(Theme.getSelectorDrawable(false));
        }
        linearLayout.setImportantForAccessibility(4);
        addView(linearLayout2, new LinearLayout.LayoutParams(-1, -2));
    }

    public static void decorateAction(View view) {
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        int horizontal = AndroidUtilities.dp(12), vertical = AndroidUtilities.dp(3);
        android.graphics.drawable.InsetDrawable inset = new android.graphics.drawable.InsetDrawable(
                fill, horizontal, vertical, horizontal, vertical);
        int left = view.getPaddingLeft(), top = view.getPaddingTop();
        int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 40)), inset, null));
        // InsetDrawable padding must not replace the row's content padding.
        view.setPadding(left, top, right, bottom);
        // Multiline rows must not turn into tall capsules with text in their corners.
        DevGramReferenceActionButtons.style(view, horizontal, vertical, AndroidUtilities.dp(24));
    }

    @Override
    public void draw(Canvas canvas) {
        int save = DevGramReferenceActionButtons.clip(canvas, this);
        try {
            super.draw(canvas);
        } finally {
            if (save >= 0) canvas.restoreToCount(save);
        }
    }

    public void setHelp(View help){setPadding(AndroidUtilities.dp(24),AndroidUtilities.dp(12),AndroidUtilities.dp(24),AndroidUtilities.dp(12));addView(help,new LinearLayout.LayoutParams(-1,-2));}
    public void setTextAndValue(CharSequence charSequence, CharSequence charSequence2, boolean z) {
        String str;
        this.textView.setText(charSequence);
        this.valueTextView.setText(charSequence2);
        this.valueTextView.setVisibility((charSequence2 == null || charSequence2.length() == 0) ? 8 : 0);
        StringBuilder sb = new StringBuilder();
        sb.append((Object) charSequence);
        if (charSequence2 == null || charSequence2.length() == 0) {
            str = "";
        } else {
            str = ", " + ((Object) charSequence2);
        }
        sb.append(str);
        setContentDescription(sb.toString());
    }

    public void setTextAndCheck(CharSequence charSequence, boolean z, boolean z2) {
        this.textView.setText(charSequence);
        setContentDescription(charSequence);
        boolean changed=this.checked!=z;this.checked = z;
        Toggle toggle = this.toggle;
        if (toggle != null && changed) {
            toggle.snap(z);
        }
    }

    public boolean isChecked() {
        return this.checked;
    }

    public void setChecked(boolean z) {
        if (this.checked == z) {
            return;
        }
        this.checked = z;
        Toggle toggle = this.toggle;
        if (toggle != null) {
            toggle.move(z);
        }
        sendAccessibilityEvent(2048);
    }

    @Override // android.view.View
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo accessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(accessibilityNodeInfo);
        accessibilityNodeInfo.setClassName(this.toggle != null ? "android.widget.Switch" : "android.widget.Button");
        if (this.toggle != null) {
            accessibilityNodeInfo.setCheckable(true);
            accessibilityNodeInfo.setChecked(this.checked);
        }
    }

    private static final class Toggle extends View {
        private ValueAnimator animator;
        private final Paint paint;
        private float position;

        Toggle(Context context) {
            super(context);
            this.paint = new Paint(1);
            setImportantForAccessibility(2);
        }

        void snap(boolean z) {
            ValueAnimator valueAnimator = this.animator;
            if (valueAnimator != null) {
                valueAnimator.cancel();
            }
            this.position = z ? 1.0f : 0.0f;
            invalidate();
        }

        void move(boolean z) {
            ValueAnimator valueAnimator = this.animator;
            if (valueAnimator != null) {
                valueAnimator.cancel();
            }
            if (!isAttachedToWindow()) {
                snap(z);
                return;
            }
            ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(this.position, z ? 1.0f : 0.0f);
            this.animator = valueAnimatorOfFloat;
            valueAnimatorOfFloat.setDuration(180L);
            this.animator.setInterpolator(new DecelerateInterpolator());
            this.animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: cpb.DevGramReferenceHeaderRow.Toggle.1
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public void onAnimationUpdate(ValueAnimator valueAnimator2) {
                    Toggle.this.position = ((Float) valueAnimator2.getAnimatedValue()).floatValue();
                    Toggle.this.invalidate();
                }
            });
            this.animator.start();
        }

        @Override // android.view.View
        protected void onDetachedFromWindow() {
            ValueAnimator valueAnimator = this.animator;
            if (valueAnimator != null) {
                valueAnimator.end();
                this.animator = null;
            }
            super.onDetachedFromWindow();
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            float width = getWidth();
            float height = getHeight();
            int iThemeColor = Theme.getColor(Theme.key_switchTrack);
            int iThemeColor2 = Theme.getColor(Theme.key_switchTrackChecked);
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(ColorUtils.blendARGB(iThemeColor, iThemeColor2, this.position));
            float f = height / 2.0f;
            canvas.drawRoundRect(0.0f, 0.0f, width, height, f, f, this.paint);
            float fDpf = f - AndroidUtilities.dpf2(3.0f);
            float f2 = f + (((width - f) - f) * (getLayoutDirection() == 1 ? 1.0f - this.position : this.position));
            this.paint.setColor(0xffffffff);
            canvas.drawCircle(f2, f, fDpf, this.paint);
        }
    }
}

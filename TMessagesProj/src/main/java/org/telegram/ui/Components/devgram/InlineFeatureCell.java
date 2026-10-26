package org.telegram.ui.Components.devgram;

import kotlin.NoWhenBranchMatchedException;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.util.List;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.math.MathKt;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
@SuppressLint({"ViewConstructor"})
public final class InlineFeatureCell extends FrameLayout {
    private ValueAnimator animator;
    private final ImageView arrowView;
    private final TextView badgeView;
    private List<? extends InlineDocBlock> boundDocs;
    private int boundId;
    private final LinearLayout docsView;
    private boolean expanded;
    private final FrameLayout header;
    private final ImageView iconView;
    private final Typeface mono;
    private boolean needDivider;
    private float progress;
    private final Theme.ResourcesProvider resourcesProvider;
    private final TextView subtitleView;
    private final TextView titleView;

    public InlineFeatureCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.mono = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO);
        FrameLayout frameLayout = new FrameLayout(context);
        this.header = frameLayout;
        ImageView imageView = new ImageView(context);
        this.iconView = imageView;
        TextView textView = new TextView(context);
        this.titleView = textView;
        TextView textView2 = new TextView(context);
        this.badgeView = textView2;
        TextView textView3 = new TextView(context);
        this.subtitleView = textView3;
        ImageView imageView2 = new ImageView(context);
        this.arrowView = imageView2;
        LinearLayout linearLayout = new LinearLayout(context);
        this.docsView = linearLayout;
        setWillNotDraw(false);
        boolean z = LocaleController.isRTL;
        int i = z ? 5 : 3;
        int i2 = z ? 3 : 5;
        frameLayout.setBackground(Theme.createSelectorDrawable(color(Theme.key_listSelector), 2));
        ImageView.ScaleType scaleType = ImageView.ScaleType.CENTER;
        imageView.setScaleType(scaleType);
        int i3 = Theme.key_windowBackgroundWhiteGrayIcon;
        int iColor = color(i3);
        PorterDuff.Mode mode = PorterDuff.Mode.MULTIPLY;
        imageView.setColorFilter(new PorterDuffColorFilter(iColor, mode));
        int i4 = i | 16;
        frameLayout.addView(imageView, LayoutHelper.createFrame(24, 24.0f, i4, 21.0f, 0.0f, 21.0f, 0.0f));
        textView.setTypeface(AndroidUtilities.regular());
        textView.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(1, 16.0f);
        textView.setSingleLine(true);
        TextUtils.TruncateAt truncateAt = TextUtils.TruncateAt.END;
        textView.setEllipsize(truncateAt);
        textView.setGravity(i);
        textView2.setTypeface(AndroidUtilities.regular());
        int i5 = Theme.key_windowBackgroundWhiteGrayText2;
        textView2.setTextColor(color(i5));
        int i6 = i2;
        textView2.setTextSize(1, 11.0f);
        textView2.setSingleLine(true);
        textView2.setPadding(AndroidUtilities.dp(6.0f), AndroidUtilities.dp(1.0f), AndroidUtilities.dp(6.0f), AndroidUtilities.dp(1.0f));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(AndroidUtilities.dp(6.0f));
        gradientDrawable.setColor(chipColor());
        textView2.setBackground(gradientDrawable);
        textView3.setTypeface(AndroidUtilities.regular());
        textView3.setTextColor(color(i5));
        textView3.setTextSize(1, 13.0f);
        textView3.setSingleLine(true);
        textView3.setEllipsize(truncateAt);
        LinearLayout linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(i4);
        boolean z2 = LocaleController.isRTL;
        linearLayout2.addView(textView2, LayoutHelper.createLinear(-2, -2, 16, z2 ? 6 : 0, 0, z2 ? 0 : 6, 0));
        linearLayout2.addView(textView3, LayoutHelper.createLinear(0, -2, 1.0f));
        LinearLayout linearLayout3 = new LinearLayout(context);
        linearLayout3.setOrientation(1);
        linearLayout3.addView(textView, LayoutHelper.createLinear(-1, -2));
        linearLayout3.addView(linearLayout2, LayoutHelper.createLinear(-1, -2, 0.0f, 3.0f, 0.0f, 0.0f));
        boolean z3 = LocaleController.isRTL;
        frameLayout.addView(linearLayout3, LayoutHelper.createFrame(-1, -2.0f, i4, z3 ? 56.0f : 71.0f, 0.0f, z3 ? 71.0f : 56.0f, 0.0f));
        imageView2.setScaleType(scaleType);
        imageView2.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.arrow_more));
        imageView2.setColorFilter(new PorterDuffColorFilter(color(i3), mode));
        frameLayout.addView(imageView2, LayoutHelper.createFrame(24, 24.0f, i6 | 16, 18.0f, 0.0f, 18.0f, 0.0f));
        addView(frameLayout);
        linearLayout.setOrientation(1);
        linearLayout.setClickable(true);
        linearLayout.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 21.0f : 71.0f), AndroidUtilities.dp(2.0f), AndroidUtilities.dp(LocaleController.isRTL ? 71.0f : 21.0f), AndroidUtilities.dp(14.0f));
        addView(linearLayout);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = View.MeasureSpec.getSize(widthMeasureSpec);
        this.header.measure(View.MeasureSpec.makeMeasureSpec(size, TLObject.FLAG_30), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60.0f), TLObject.FLAG_30));
        this.docsView.measure(View.MeasureSpec.makeMeasureSpec(size, TLObject.FLAG_30), View.MeasureSpec.makeMeasureSpec(0, 0));
        setMeasuredDimension(size, this.header.getMeasuredHeight() + MathKt.roundToInt(this.docsView.getMeasuredHeight() * this.progress) + (this.needDivider ? 1 : 0));
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        FrameLayout frameLayout = this.header;
        frameLayout.layout(0, 0, frameLayout.getMeasuredWidth(), this.header.getMeasuredHeight());
        this.docsView.layout(0, this.header.getMeasuredHeight(), this.docsView.getMeasuredWidth(), this.header.getMeasuredHeight() + this.docsView.getMeasuredHeight());
    }

    @Override
    public void onDraw(Canvas canvas) {
        Paint paint;
        if (this.needDivider) {
            float fDp = AndroidUtilities.dp(68.0f);
            float measuredHeight = getMeasuredHeight() - 1.0f;
            Theme.ResourcesProvider resourcesProvider = this.resourcesProvider;
            if (resourcesProvider == null || (paint = resourcesProvider.getPaint("paintDivider")) == null) {
                paint = Theme.dividerPaint;
            }
            Paint paint2 = paint;
            if (LocaleController.isRTL) {
                canvas.drawLine(0.0f, measuredHeight, getMeasuredWidth() - fDp, measuredHeight, paint2);
            } else {
                canvas.drawLine(fDp, measuredHeight, getMeasuredWidth(), measuredHeight, paint2);
            }
        }
    }

    public final void set(int id, InlineFeature feature, boolean divider, View.OnClickListener onClick) {
        ValueAnimator valueAnimator = this.animator;
        if (valueAnimator != null) {
            valueAnimator.removeAllListeners();
            valueAnimator.cancel();
        }
        this.animator = null;
        boolean z = feature.getDocs() != null;
        boolean z2 = this.boundId == id && this.expanded != z;
        this.boundId = id;
        this.expanded = z;
        this.header.setTag(Integer.valueOf(id));
        this.header.setOnClickListener(onClick);
        this.iconView.setImageResource(feature.getIcon());
        this.titleView.setText(feature.getTitle());
        this.subtitleView.setText(feature.getSubtitle());
        this.badgeView.setText(feature.getBadge());
        this.badgeView.setVisibility(feature.getBadge() == null ? 8 : 0);
        if (feature.getDocs() != null && !Intrinsics.areEqual(feature.getDocs(), this.boundDocs)) {
            this.boundDocs = feature.getDocs();
            buildDocs(feature.getDocs(), onClick);
        }
        float f = z ? 1.0f : 0.0f;
        this.arrowView.animate().cancel();
        ImageView imageView = this.arrowView;
        if (z2) {
            ViewPropertyAnimator duration = imageView.animate().rotation(180.0f * f).setDuration(340L);
            CubicBezierInterpolator cubicBezierInterpolator = CubicBezierInterpolator.EASE_OUT_QUINT;
            duration.setInterpolator(cubicBezierInterpolator).start();
            ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(this.progress, f);
            valueAnimatorOfFloat.setDuration(340L);
            valueAnimatorOfFloat.setInterpolator(cubicBezierInterpolator);
            valueAnimatorOfFloat.addUpdateListener(valueAnimator2 -> setProgress(((Float) valueAnimator2.getAnimatedValue()).floatValue()));
            valueAnimatorOfFloat.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }

                @Override
                public void onAnimationStart(Animator animator) {
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    if (InlineFeatureCell.this.expanded) {
                        return;
                    }
                    InlineFeatureCell.this.clearDocs();
                }
            });
            valueAnimatorOfFloat.start();
            this.animator = valueAnimatorOfFloat;
        } else {
            imageView.setRotation(180.0f * f);
            setProgress(f);
            if (!z) {
                clearDocs();
            }
        }
        this.needDivider = divider;
        requestLayout();
        invalidate();
    }
    public final void setProgress(float value) {
        this.progress = value;
        this.docsView.setAlpha(value);
        requestLayout();
    }
    public final void clearDocs() {
        this.boundDocs = null;
        this.docsView.removeAllViews();
    }

    private final void buildDocs(List<? extends InlineDocBlock> blocks, View.OnClickListener onClick) {
        this.docsView.removeAllViews();
        int i = 0;
        for (Object obj : blocks) {
            int i2 = i + 1;
            if (i < 0) {
                CollectionsKt.throwIndexOverflow();
            }
            InlineDocBlock inlineDocBlock = (InlineDocBlock) obj;
            int i3 = i == 0 ? 0 : 8;
            if (inlineDocBlock instanceof InlineDocBlock.Text) {
                this.docsView.addView(text((InlineDocBlock.Text) inlineDocBlock), LayoutHelper.createLinear(-1, -2, 0.0f, i3, 0.0f, 0.0f));
            } else if (inlineDocBlock instanceof InlineDocBlock.Codes) {
                this.docsView.addView(codes((InlineDocBlock.Codes) inlineDocBlock), LayoutHelper.createLinear(-1, -2, 0.0f, i3, 0.0f, 0.0f));
            } else {
                if (!(inlineDocBlock instanceof InlineDocBlock.Examples)) {
                    throw new NoWhenBranchMatchedException();
                }
                int i4 = 0;
                for (Object obj2 : ((InlineDocBlock.Examples) inlineDocBlock).getExamples()) {
                    int i5 = i4 + 1;
                    if (i4 < 0) {
                        CollectionsKt.throwIndexOverflow();
                    }
                    this.docsView.addView(example((InlineDocExample) obj2, onClick), LayoutHelper.createLinear(-2, -2, LocaleController.isRTL ? 5 : 3, 0, i4 == 0 ? i3 : 6, 0, 0));
                    i4 = i5;
                }
            }
            i = i2;
        }
    }

    private final TextView text(InlineDocBlock.Text block) {
        TextView textView = new TextView(getContext());
        textView.setText(block.getText());
        textView.setTypeface(AndroidUtilities.regular());
        textView.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText2));
        textView.setTextSize(1, 14.0f);
        textView.setLineSpacing(AndroidUtilities.dp(2.0f), 1.0f);
        textView.setGravity(LocaleController.isRTL ? 5 : 3);
        return textView;
    }

    private final TextView codes(InlineDocBlock.Codes block) {
        TextView textView = new TextView(getContext());
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        int iColor = color(Theme.key_windowBackgroundWhiteBlackText);
        int i = 0;
        for (Object obj : block.getCodes()) {
            int i2 = i + 1;
            if (i < 0) {
                CollectionsKt.throwIndexOverflow();
            }
            String str = (String) obj;
            if (i > 0) {
                spannableStringBuilder.append(' ');
            }
            int length = spannableStringBuilder.length();
            spannableStringBuilder.append((CharSequence) str);
            spannableStringBuilder.setSpan(new CodeSpan(this.mono, chipColor(), iColor), length, spannableStringBuilder.length(), 33);
            i = i2;
        }
        textView.setText(spannableStringBuilder);
        textView.setTypeface(AndroidUtilities.regular());
        textView.setTextSize(1, 14.0f);
        textView.setLineSpacing(AndroidUtilities.dp(10.0f), 1.0f);
        textView.setPadding(0, AndroidUtilities.dp(3.0f), 0, AndroidUtilities.dp(3.0f));
        return textView;
    }

    private final TextView example(InlineDocExample example, View.OnClickListener onClick) {
        TextView textView = new TextView(getContext());
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(example.getInput());
        if (example.getResult() != null) {
            int length = spannableStringBuilder.length();
            spannableStringBuilder.append(' ').append((CharSequence) example.getResult());
            spannableStringBuilder.setSpan(new ForegroundColorSpan(Theme.multAlpha(color(Theme.key_windowBackgroundWhiteBlackText), 0.4f)), length, spannableStringBuilder.length(), 33);
        }
        textView.setText(spannableStringBuilder);
        textView.setTypeface(this.mono);
        int i = Theme.key_windowBackgroundWhiteBlackText;
        textView.setTextColor(color(i));
        textView.setTextSize(1, 13.0f);
        textView.setPadding(AndroidUtilities.dp(8.0f), AndroidUtilities.dp(4.0f), AndroidUtilities.dp(8.0f), AndroidUtilities.dp(4.0f));
        textView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6.0f), chipColor(), Theme.multAlpha(color(i), 0.12f)));
        textView.setTag(example.getInput());
        textView.setOnClickListener(onClick);
        return textView;
    }

    private final int chipColor() {
        return Theme.multAlpha(color(Theme.key_windowBackgroundWhiteBlackText), 0.06f);
    }

    private final int color(int key) {
        return Theme.getColor(key, this.resourcesProvider);
    }
    public static final class CodeSpan extends ReplacementSpan {
        private final int background;
        private final int color;
        private final Typeface typeface;
        private final TextPaint textPaint = new TextPaint(1);
        private final Paint backgroundPaint = new Paint(1);
        private final RectF rect = new RectF();

        public CodeSpan(Typeface typeface, int i, int i2) {
            this.typeface = typeface;
            this.background = i;
            this.color = i2;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            prepare(paint);
            if (fm != null) {
                paint.getFontMetricsInt(fm);
            }
            return (int) (this.textPaint.measureText(text, start, end) + AndroidUtilities.dp(12.0f));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            prepare(paint);
            float f = y;
            this.rect.set(x, (this.textPaint.ascent() + f) - AndroidUtilities.dp(3.0f), this.textPaint.measureText(text, start, end) + x + AndroidUtilities.dp(12.0f), this.textPaint.descent() + f + AndroidUtilities.dp(3.0f));
            this.backgroundPaint.setColor(this.background);
            canvas.drawRoundRect(this.rect, AndroidUtilities.dp(6.0f), AndroidUtilities.dp(6.0f), this.backgroundPaint);
            canvas.drawText(text, start, end, x + AndroidUtilities.dp(6.0f), f, this.textPaint);
        }

        private final void prepare(Paint paint) {
            this.textPaint.setTextSize(paint.getTextSize() * 0.93f);
            this.textPaint.setTypeface(this.typeface);
            this.textPaint.setColor(this.color);
        }
    }
    public static final class Factory extends UItem.UItemFactory<InlineFeatureCell> {
        public static final Companion INSTANCE = new Companion(null);

        @Override
        public boolean isClickable() {
            return false;
        }

        @Override
        public InlineFeatureCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new InlineFeatureCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            Object obj = item.object;
            InlineFeature inlineFeature = obj instanceof InlineFeature ? (InlineFeature) obj : null;
            if (inlineFeature == null) {
                return;
            }
            ((InlineFeatureCell) view).set(item.id, inlineFeature, divider, item.clickCallback);
        }

        @Override
        public boolean equals(UItem a2, UItem b2) {
            return a2.id == b2.id;
        }

        @Override
        public boolean contentsEquals(UItem a2, UItem b2) {
            return a2.id == b2.id && Intrinsics.areEqual(a2.object, b2.object);
        }
        public static final class Companion {
            public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
                this();
            }

            private Companion() {
            }

            @JvmStatic
            public final UItem of(int id, InlineFeature feature, View.OnClickListener onClick) {
                UItem uItemOfFactory = UItem.ofFactory(Factory.class);
                uItemOfFactory.id = id;
                uItemOfFactory.text = feature.getTitle();
                uItemOfFactory.object = feature;
                uItemOfFactory.clickCallback = onClick;
                return uItemOfFactory;
            }
        }

        static {
            UItem.UItemFactory.setup(new Factory());
        }
    }
}

package org.telegram.messenger.devgram.player.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import org.telegram.messenger.devgram.player.lyrics.Lyrics;
import java.util.ArrayList;
import kotlin.jvm.internal.LongCompanionObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
@SuppressLint({"ViewConstructor"})
public class LyricsView extends FrameLayout {
    private static final FloatPropertyCompat<NestedScrollView> SCROLL_Y = new FloatPropertyCompat<NestedScrollView>("scrollY") {
        @Override
        public float getValue(NestedScrollView nestedScrollView) {
            return nestedScrollView.getScrollY();
        }

        @Override
        public void setValue(NestedScrollView nestedScrollView, float f) {
            nestedScrollView.scrollTo(0, Math.round(f));
        }
    };
    private long catchUpAtMs;
    private long catchUpMs;
    private long clockAtMs;
    private boolean clockPlaying;
    private long clockPositionMs;
    private float clockSpeed;
    private TextView creditView;
    private int currentIndex;
    private Delegate delegate;
    private final ButtonWithCounterView emptyButton;
    private final ImageView emptyImageView;
    private final RLottieImageView emptyStickerView;
    private final TextView emptySubtitleView;
    private final TextView emptyTitleView;
    private final LinearLayout emptyView;
    private long lastDisplayPositionMs;
    private final ArrayList<View> lineViews;
    private final LinesLayout linesLayout;
    private Lyrics lyrics;
    private boolean pendingSnap;
    private int progressMode;
    private final RadialProgressView progressView;
    private final SpringAnimation scrollSpring;
    private final NestedScrollView scrollView;
    private int secondaryColor;
    private int state;
    private int textColor;
    private long userScrollUntil;
    private int viewportHeight;

    public interface Delegate {
        void onConsent();

        void onRetry();

        void onSeekTo(long j);
    }

    @SuppressLint({"ClickableViewAccessibility"})
    public LyricsView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.lineViews = new ArrayList<>();
        this.state = 0;
        this.currentIndex = -1;
        this.clockSpeed = 1.0f;
        this.lastDisplayPositionMs = -1L;
        this.progressMode = 1;
        NestedScrollView nestedScrollView = new NestedScrollView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == 0) {
                    LyricsView.this.onUserScroll();
                }
                return super.onInterceptTouchEvent(motionEvent);
            }

            @Override
            public boolean onTouchEvent(MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == 0 || motionEvent.getActionMasked() == 2) {
                    LyricsView.this.onUserScroll();
                }
                return super.onTouchEvent(motionEvent);
            }

            @Override
            public void onMeasure(int i, int i2) {
                int size = View.MeasureSpec.getSize(i2);
                if (size != LyricsView.this.viewportHeight) {
                    LyricsView.this.viewportHeight = size;
                    LyricsView.this.pendingSnap = true;
                    LyricsView.this.linesLayout.forceLayout();
                }
                super.onMeasure(i, i2);
            }

            @Override
            public void onLayout(boolean z, int i, int i2, int i3, int i4) {
                super.onLayout(z, i, i2, i3, i4);
                if (!LyricsView.this.pendingSnap || getHeight() <= 0) {
                    return;
                }
                LyricsView.this.pendingSnap = false;
                if (SystemClock.elapsedRealtime() >= LyricsView.this.userScrollUntil) {
                    LyricsView.this.snapToCurrent();
                }
            }
        };
        this.scrollView = nestedScrollView;
        nestedScrollView.setVerticalScrollBarEnabled(false);
        nestedScrollView.setOverScrollMode(2);
        nestedScrollView.setVerticalFadingEdgeEnabled(true);
        nestedScrollView.setFadingEdgeLength(AndroidUtilities.dp(48.0f));
        nestedScrollView.setClipToPadding(false);
        addView(nestedScrollView, LayoutHelper.createFrame(-1, -1.0f));
        LinesLayout linesLayout = new LinesLayout(context);
        this.linesLayout = linesLayout;
        linesLayout.setPadding(AndroidUtilities.dp(20.0f), 0, AndroidUtilities.dp(20.0f), 0);
        nestedScrollView.addView(linesLayout, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout linearLayout = new LinearLayout(context);
        this.emptyView = linearLayout;
        linearLayout.setOrientation(1);
        linearLayout.setGravity(17);
        linearLayout.setPadding(AndroidUtilities.dp(32.0f), 0, AndroidUtilities.dp(32.0f), 0);
        addView(linearLayout, LayoutHelper.createFrame(-1, -1.0f));
        FrameLayout frameLayout = new FrameLayout(context);
        RLottieImageView rLottieImageView = new RLottieImageView(context);
        this.emptyStickerView = rLottieImageView;
        rLottieImageView.setAnimation(R.raw.utyan_empty, 120, 120);
        rLottieImageView.setOnClickListener(view -> lambda_new_0(view));
        frameLayout.addView(rLottieImageView, LayoutHelper.createFrame(120, 120, 17));
        ImageView imageView = new ImageView(context);
        this.emptyImageView = imageView;
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.music_empty);
        frameLayout.addView(imageView, LayoutHelper.createFrame(120, 120, 17));
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(120, 120, 1));
        TextView textView = new TextView(context);
        this.emptyTitleView = textView;
        textView.setTextSize(1, 20.0f);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(17);
        linearLayout.addView(textView, LayoutHelper.createLinear(-2, -2, 1, 0, 12, 0, 0));
        TextView textView2 = new TextView(context);
        this.emptySubtitleView = textView2;
        textView2.setTextSize(1, 14.0f);
        textView2.setTypeface(AndroidUtilities.regular());
        textView2.setGravity(17);
        textView2.setMaxWidth(AndroidUtilities.dp(272.0f));
        textView2.setLineSpacing(AndroidUtilities.dp(2.0f), 1.0f);
        linearLayout.addView(textView2, LayoutHelper.createLinear(-2, -2, 1, 0, 8, 0, 0));
        ButtonWithCounterView round = new ButtonWithCounterView(context, resourcesProvider).setRound();
        this.emptyButton = round;
        round.setOnClickListener(view -> lambda_new_1(view));
        linearLayout.addView(round, LayoutHelper.createLinear(256, 48, 1, 0, 20, 0, 0));
        RadialProgressView radialProgressView = new RadialProgressView(context, resourcesProvider);
        this.progressView = radialProgressView;
        radialProgressView.setSize(AndroidUtilities.dp(48.0f));
        addView(radialProgressView, LayoutHelper.createFrame(64, 64, 17));
        SpringAnimation springAnimation = new SpringAnimation(nestedScrollView, SCROLL_Y);
        this.scrollSpring = springAnimation;
        springAnimation.setSpring(new SpringForce(0.0f).setDampingRatio(0.9f).setStiffness(180.0f));
        springAnimation.setMinimumVisibleChange(1.0f);
        applyState(false);
    }
    public /* synthetic */ void lambda_new_0(View view) {
        this.emptyStickerView.setProgress(0.0f);
        this.emptyStickerView.playAnimation();
    }
    public /* synthetic */ void lambda_new_1(View view) {
        Delegate delegate = this.delegate;
        if (delegate == null) {
            return;
        }
        int i = this.state;
        if (i == 1) {
            delegate.onConsent();
        } else if (i == 6) {
            delegate.onRetry();
        }
    }

    public void setProgressMode(int i) {
        if (this.progressMode != i) {
            this.progressMode = i;
            int i2 = this.currentIndex;
            if (i2 < 0 || i2 >= this.lineViews.size()) {
                return;
            }
            this.lineViews.get(this.currentIndex).invalidate();
        }
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setColors(int i, int i2, int i3, int i4) {
        this.textColor = i;
        this.secondaryColor = i2;
        TextView textView = this.creditView;
        if (textView != null) {
            textView.setTextColor(i2);
        }
        this.emptyTitleView.setTextColor(i);
        this.emptySubtitleView.setTextColor(i2);
        this.emptyImageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(i2, 179), PorterDuff.Mode.SRC_IN));
        this.emptyButton.setColor(i3);
        this.emptyButton.setTextColor(i4);
        this.progressView.setProgressColor(i3);
        ArrayList<View> arrayList = this.lineViews;
        int size = arrayList.size();
        int i5 = 0;
        while (i5 < size) {
            View view = arrayList.get(i5);
            i5++;
            View view2 = view;
            if (view2 instanceof TextView) {
                ((TextView) view2).setTextColor(i);
            } else if (view2 instanceof DotsView) {
                ((DotsView) view2).setColor(i);
            }
        }
    }

    public int getState() {
        return this.state;
    }

    public void setState(int i, CharSequence charSequence, CharSequence charSequence2, CharSequence charSequence3) {
        this.state = i;
        if (i != 3) {
            this.lyrics = null;
            clearLines();
        }
        this.emptyTitleView.setText(charSequence);
        this.emptySubtitleView.setText(charSequence2);
        this.emptySubtitleView.setVisibility(TextUtils.isEmpty(charSequence2) ? 8 : 0);
        boolean zIsEmpty = TextUtils.isEmpty(charSequence3);
        ButtonWithCounterView buttonWithCounterView = this.emptyButton;
        if (!zIsEmpty) {
            buttonWithCounterView.setText(charSequence3, false);
            this.emptyButton.setVisibility(0);
        } else {
            buttonWithCounterView.setVisibility(8);
        }
        applyState(true);
    }

    /* JADX WARN: Type inference fix 'apply assigned field type' failed
    java.lang.UnsupportedOperationException: ArgType.getObject(), call class: class jadx.core.dex.instructions.args.ArgType$UnknownArg
    	at jadx.core.dex.instructions.args.ArgType.getObject(ArgType.java:596)
    	at jadx.core.dex.attributes.nodes.ClassTypeVarsAttr.getTypeVarsMapFor(ClassTypeVarsAttr.java:35)
    	at jadx.core.dex.nodes.utils.TypeUtils.replaceClassGenerics(TypeUtils.java:177)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.insertExplicitUseCast(FixTypesVisitor.java:397)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryFieldTypeWithNewCasts(FixTypesVisitor.java:359)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.applyFieldType(FixTypesVisitor.java:309)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.visit(FixTypesVisitor.java:94)
     */
    public void setLyrics(Lyrics lyrics, CharSequence charSequence) {
        View view;
        this.state = 3;
        this.lyrics = lyrics;
        int i = -1;
        this.currentIndex = -1;
        this.userScrollUntil = 0L;
        this.scrollSpring.cancel();
        clearLines();
        Context context = getContext();
        boolean z = lyrics.synced;
        LinesLayout linesLayout = this.linesLayout;
        boolean z2 = true;
        if (z) {
            linesLayout.topSpacer = new View(context);
            LinesLayout linesLayout2 = this.linesLayout;
            linesLayout2.addView(linesLayout2.topSpacer, LayoutHelper.createLinear(-1, 0));
            int i2 = 0;
            while (i2 < lyrics.lines.size()) {
                Lyrics.Line line = lyrics.lines.get(i2);
                if (TextUtils.isEmpty(line.text)) {
                    DotsView dotsView = new DotsView(context);
                    dotsView.setColor(this.textColor);
                    this.linesLayout.addView(dotsView, LayoutHelper.createLinear(i, 41));
                    view = dotsView;
                } else {
                    LineView lineView = new LineView(context);
                    styleLine(lineView, z2);
                    lineView.setText(line.text);
                    int i3 = i2 + 1;
                    long j = i3 < lyrics.lines.size() ? lyrics.lines.get(i3).time : LongCompanionObject.MAX_VALUE;
                    long jMax = Math.max(2000L, ((long) line.text.length()) * 200);
                    long j2 = line.time;
                    lineView.setTiming(j2, Math.min(Math.max(1L, j - j2), jMax) + j2);
                    this.linesLayout.addView(lineView, LayoutHelper.createLinear(-1, -2));
                    view = lineView;
                }
                view.setAlpha(0.3f);
                if (view instanceof LineView) {
                    view.setScaleX(0.95f);
                    view.setScaleY(0.95f);
                }
                final long j3 = line.time;
                view.setOnClickListener(view2 -> lambda_setLyrics_2(j3, view2));
                this.lineViews.add(view);
                i2++;
                context = context;
                i = -1;
                z2 = true;
            }
            Context context2 = context;
            addCredit(context2, charSequence, 28.0f);
            this.linesLayout.bottomSpacer = new View(context2);
            LinesLayout linesLayout3 = this.linesLayout;
            linesLayout3.addView(linesLayout3.bottomSpacer, LayoutHelper.createLinear(-1, 0));
        } else {
            linesLayout.addView(new View(context), LayoutHelper.createLinear(-1, 12));
            for (int i4 = 0; i4 < lyrics.lines.size(); i4++) {
                Lyrics.Line line2 = lyrics.lines.get(i4);
                if (TextUtils.isEmpty(line2.text)) {
                    this.linesLayout.addView(new View(context), LayoutHelper.createLinear(-1, 18));
                } else {
                    TextView textViewCreateLine = createLine(context, false);
                    textViewCreateLine.setText(line2.text);
                    textViewCreateLine.setAlpha(0.92f);
                    this.linesLayout.addView(textViewCreateLine, LayoutHelper.createLinear(-1, -2));
                    this.lineViews.add(textViewCreateLine);
                }
            }
            addCredit(context, charSequence, 28.0f);
            this.linesLayout.addView(new View(context), LayoutHelper.createLinear(-1, 96));
        }
        this.scrollView.scrollTo(0, 0);
        this.pendingSnap = true;
        applyState(true);
    }
    public /* synthetic */ void lambda_setLyrics_2(long j, View view) {
        this.userScrollUntil = 0L;
        this.lastDisplayPositionMs = -1L;
        this.catchUpMs = 0L;
        Delegate delegate = this.delegate;
        if (delegate != null) {
            delegate.onSeekTo(j);
        }
    }

    private void addCredit(Context context, CharSequence charSequence, float f) {
        if (TextUtils.isEmpty(charSequence)) {
            return;
        }
        TextView textView = new TextView(context);
        this.creditView = textView;
        textView.setTextSize(1, 13.0f);
        this.creditView.setTypeface(AndroidUtilities.regular());
        this.creditView.setTextColor(this.secondaryColor);
        this.creditView.setText(charSequence);
        this.linesLayout.addView(this.creditView, LayoutHelper.createLinear(-1, -2, 0.0f, f, 0.0f, 0.0f));
    }

    private TextView createLine(Context context, boolean z) {
        TextView textView = new TextView(context);
        styleLine(textView, z);
        return textView;
    }

    private void styleLine(TextView textView, boolean z) {
        textView.setTextColor(this.textColor);
        if (z) {
            textView.setTextSize(1, 22.0f);
            textView.setTypeface(AndroidUtilities.bold());
            setLineHeight(textView, 29.0f);
            textView.setPadding(0, AndroidUtilities.dp(7.0f), 0, AndroidUtilities.dp(7.0f));
            return;
        }
        textView.setTextSize(1, 18.0f);
        textView.setTypeface(AndroidUtilities.regular());
        setLineHeight(textView, 26.0f);
        textView.setPadding(0, AndroidUtilities.dp(2.0f), 0, AndroidUtilities.dp(2.0f));
        textView.setTextIsSelectable(false);
    }

    private static void setLineHeight(TextView textView, float f) {
        Paint.FontMetricsInt fontMetricsInt = textView.getPaint().getFontMetricsInt();
        int i = fontMetricsInt.descent - fontMetricsInt.ascent;
        textView.setIncludeFontPadding(false);
        textView.setLineSpacing(Math.max(0, AndroidUtilities.dp(f) - i), 1.0f);
    }

    private void clearLines() {
        ArrayList<View> arrayList = this.lineViews;
        int size = arrayList.size();
        int i = 0;
        while (i < size) {
            View view = arrayList.get(i);
            i++;
            view.animate().cancel();
        }
        this.lineViews.clear();
        this.linesLayout.removeAllViews();
        LinesLayout linesLayout = this.linesLayout;
        linesLayout.topSpacer = null;
        linesLayout.bottomSpacer = null;
        this.creditView = null;
        this.currentIndex = -1;
    }

    private void applyState(boolean z) {
        int i = this.state;
        boolean z2 = true;
        boolean z3 = i == 3;
        boolean z4 = i == 2;
        boolean z5 = (z3 || z4 || i == 0) ? false : true;
        this.scrollView.setVisibility(z3 ? 0 : 8);
        this.progressView.setVisibility(z4 ? 0 : 8);
        int i2 = this.state;
        if (i2 != 4 && i2 != 6) {
            z2 = false;
        }
        this.emptyStickerView.setVisibility(z2 ? 0 : 8);
        this.emptyImageView.setVisibility(z2 ? 8 : 0);
        if (z5 && z2) {
            this.emptyStickerView.setProgress(0.0f);
            this.emptyStickerView.playAnimation();
        }
        if (z5 && this.emptyView.getVisibility() != 0 && z) {
            this.emptyView.setAlpha(0.0f);
            this.emptyView.setTranslationY(AndroidUtilities.dp(12.0f));
            this.emptyView.setVisibility(0);
            this.emptyView.animate().alpha(1.0f).translationY(0.0f).setDuration(320L).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            return;
        }
        this.emptyView.animate().cancel();
        this.emptyView.setAlpha(1.0f);
        this.emptyView.setTranslationY(0.0f);
        this.emptyView.setVisibility(z5 ? 0 : 8);
    }
    public void onUserScroll() {
        this.userScrollUntil = SystemClock.elapsedRealtime() + 3000;
        this.scrollSpring.cancel();
    }

    public void setPosition(long j, boolean z) {
        Lyrics lyrics;
        if (this.state != 3 || (lyrics = this.lyrics) == null || !lyrics.synced || this.lineViews.isEmpty()) {
            return;
        }
        boolean z2 = this.clockPlaying;
        this.clockPositionMs = j;
        this.clockAtMs = SystemClock.elapsedRealtime();
        this.clockPlaying = z;
        this.clockSpeed = MediaController.getInstance().getPlaybackSpeed(true);
        int iIndexAt = this.lyrics.indexAt(j);
        int i = this.currentIndex;
        if (iIndexAt == i && !z2) {
            long j2 = this.lastDisplayPositionMs;
            if (j2 >= 0 && j > j2 && j - j2 <= 1000) {
                this.catchUpMs = j - j2;
                this.catchUpAtMs = this.clockAtMs;
            }
        }
        if (iIndexAt != i) {
            this.currentIndex = iIndexAt;
            this.lastDisplayPositionMs = -1L;
            this.catchUpMs = 0L;
            if (i >= 0 && i < this.lineViews.size()) {
                highlight(this.lineViews.get(i), false);
            }
            if (iIndexAt >= 0 && iIndexAt < this.lineViews.size()) {
                highlight(this.lineViews.get(iIndexAt), true);
            }
        }
        int i2 = 0;
        while (i2 < this.lineViews.size()) {
            View view = this.lineViews.get(i2);
            if (view instanceof DotsView) {
                ((DotsView) view).setActive(i2 == this.currentIndex && z);
            }
            i2++;
        }
        int i3 = this.currentIndex;
        if (i3 >= 0 && i3 < this.lineViews.size()) {
            this.lineViews.get(this.currentIndex).invalidate();
        }
        autoScroll();
    }
    public long getDisplayPositionMs() {
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        long interpolation = this.clockPositionMs;
        if (this.clockPlaying) {
            interpolation += (long) ((jElapsedRealtime - this.clockAtMs) * this.clockSpeed);
        }
        long j = this.catchUpMs;
        if (j > 0) {
            float f = (jElapsedRealtime - this.catchUpAtMs) / 600.0f;
            if (f >= 1.0f) {
                this.catchUpMs = 0L;
            } else {
                interpolation -= (long) (j * (1.0f - CubicBezierInterpolator.DEFAULT.getInterpolation(f)));
            }
        }
        long j2 = this.lastDisplayPositionMs;
        if (j2 >= 0 && interpolation < j2 && j2 - interpolation < 300) {
            interpolation = j2;
        }
        this.lastDisplayPositionMs = interpolation;
        return interpolation;
    }

    private static void highlight(View view, boolean z) {
        view.animate().cancel();
        float f = (z || !(view instanceof LineView)) ? 1.0f : 0.95f;
        ViewPropertyAnimator interpolator = view.animate().alpha(z ? 1.0f : 0.3f).scaleX(f).scaleY(f).setDuration(350L).setInterpolator(CubicBezierInterpolator.DEFAULT);
        if (view instanceof LineView) {
            final LineView lineView = (LineView) view;
            lineView.setFadingProgress(z ? -1.0f : lineView.drawnProgress);
            lineView.drawnProgress = -1.0f;
            if (!z) {
                interpolator.withEndAction(() -> lineView.setFadingProgress(-1.0f));
            }
        }
        interpolator.start();
    }

    private float getCurrentScrollTarget() {
        ArrayList<View> arrayList = this.lineViews;
        View view = arrayList.get(Math.max(0, Math.min(arrayList.size() - 1, this.currentIndex)));
        return Math.max(0.0f, Math.min(Math.max(0, this.linesLayout.getHeight() - this.scrollView.getHeight()), (view.getTop() + (view.getHeight() / 2.0f)) - (this.scrollView.getHeight() * 0.5f)));
    }
    public void snapToCurrent() {
        Lyrics lyrics = this.lyrics;
        if (lyrics == null || !lyrics.synced || this.lineViews.isEmpty() || this.scrollView.getHeight() <= 0) {
            return;
        }
        this.scrollSpring.cancel();
        this.scrollView.scrollTo(0, Math.round(getCurrentScrollTarget()));
    }

    private void autoScroll() {
        if (SystemClock.elapsedRealtime() < this.userScrollUntil || this.lineViews.isEmpty() || this.scrollView.getHeight() <= 0) {
            return;
        }
        float currentScrollTarget = getCurrentScrollTarget();
        if (Math.abs(this.scrollSpring.getSpring().getFinalPosition() - currentScrollTarget) > 0.5f || (!this.scrollSpring.isRunning() && Math.abs(this.scrollView.getScrollY() - currentScrollTarget) > 0.5f)) {
            this.scrollSpring.getSpring().setFinalPosition(currentScrollTarget);
            if (this.scrollSpring.isRunning()) {
                return;
            }
            this.scrollSpring.setStartValue(this.scrollView.getScrollY());
            this.scrollSpring.start();
        }
    }

    public void jumpToCurrent() {
        this.userScrollUntil = 0L;
        this.scrollSpring.cancel();
        if (this.scrollView.getHeight() > 0 && !this.scrollView.isLayoutRequested()) {
            snapToCurrent();
        } else {
            this.pendingSnap = true;
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.scrollSpring.cancel();
    }

    public class LineView extends TextView {
        private float drawnProgress;
        private long end;
        private float fadingProgress;
        private long start;

        public LineView(Context context) {
            super(context);
            this.drawnProgress = -1.0f;
            this.fadingProgress = -1.0f;
        }

        public void setTiming(long j, long j2) {
            this.start = j;
            this.end = j2;
        }

        public void setFadingProgress(float f) {
            if (this.fadingProgress != f) {
                this.fadingProgress = f;
                invalidate();
            }
        }

        @Override
        public void onSizeChanged(int i, int i2, int i3, int i4) {
            super.onSizeChanged(i, i2, i3, i4);
            Layout layout = getLayout();
            setPivotX((layout == null || layout.getLineCount() <= 0 || layout.getParagraphDirection(0) != -1) ? 0.0f : i);
            setPivotY(i2 / 2.0f);
        }

        @Override
        public void onDraw(Canvas canvas) {
            float fClamp01;
            LineView lineView;
            Canvas canvas2;
            Layout layout = getLayout();
            boolean z = LyricsView.this.lineViews.indexOf(this) == LyricsView.this.currentIndex;
            if (layout == null || LyricsView.this.progressMode == 0 || (!z && this.fadingProgress < 0.0f)) {
                super.onDraw(canvas);
                return;
            }
            if (z) {
                long displayPositionMs = LyricsView.this.getDisplayPositionMs();
                long j = this.start;
                // дробное деление: в long выходил 0 до конца строки, и заливка по словам не рисовалась
                fClamp01 = Utilities.clamp01((float) (displayPositionMs - j) / Math.max(1L, this.end - j));
            } else {
                fClamp01 = this.fadingProgress;
            }
            float f = fClamp01;
            if (z) {
                this.drawnProgress = f;
            }
            int currentTextColor = getCurrentTextColor();
            float alpha = getAlpha();
            TextPaint paint = getPaint();
            canvas.save();
            canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop());
            float fClamp02 = Utilities.clamp01((alpha - 0.3f) / 0.7f);
            paint.setColor(ColorUtils.setAlphaComponent(currentTextColor, (int) (Color.alpha(currentTextColor) * Math.min(1.0f, AndroidUtilities.lerp(0.3f, 0.45f, fClamp02) / Math.max(alpha, 0.01f)))));
            layout.draw(canvas);
            int alphaComponent = ColorUtils.setAlphaComponent(currentTextColor, (int) (Color.alpha(currentTextColor) * Math.min(1.0f, z ? 1.0f : fClamp02 / Math.max(alpha, 0.01f))));
            paint.setColor(alphaComponent);
            if (LyricsView.this.progressMode == 2) {
                lineView = this;
                canvas2 = canvas;
                lineView.drawVerticalFill(canvas2, layout, paint, alphaComponent, f);
            } else {
                lineView = this;
                canvas2 = canvas;
                lineView.drawHorizontalFill(canvas2, layout, paint, alphaComponent, f);
            }
            paint.setShader(null);
            paint.setColor(currentTextColor);
            canvas2.restore();
            if (z) {
                if (((!LyricsView.this.clockPlaying && LyricsView.this.catchUpMs <= 0) || f >= 1.0f) && alpha >= 1.0f) {
                    return;
                }
            } else if (f >= 1.0f) {
                return;
            }
            lineView.postInvalidateOnAnimation();
        }

        private void drawVerticalFill(Canvas canvas, Layout layout, TextPaint textPaint, int i, float f) {
            if (f <= 0.0f) {
                return;
            }
            float fDpf2 = AndroidUtilities.dpf2(10.0f);
            float height = f * (layout.getHeight() + fDpf2);
            textPaint.setShader(new LinearGradient(0.0f, height - fDpf2, 0.0f, height, i, i & 16777215, Shader.TileMode.CLAMP));
            canvas.save();
            canvas.clipRect(0.0f, 0.0f, layout.getWidth(), height);
            layout.draw(canvas);
            canvas.restore();
        }

        private void drawHorizontalFill(Canvas canvas, Layout layout, TextPaint textPaint, int i, float f) {
            LinearGradient linearGradient;
            float fDpf2 = AndroidUtilities.dpf2(28.0f);
            float lineWidth = 0.0f;
            for (int i2 = 0; i2 < layout.getLineCount(); i2++) {
                lineWidth += layout.getLineWidth(i2) + fDpf2;
            }
            float f2 = f * lineWidth;
            for (int i3 = 0; i3 < layout.getLineCount() && f2 > 0.0f; i3++) {
                float lineWidth2 = layout.getLineWidth(i3) + fDpf2;
                float fMin = Math.min(lineWidth2, f2);
                f2 -= lineWidth2;
                boolean z = layout.getParagraphDirection(i3) == -1;
                float lineLeft = layout.getLineLeft(i3);
                float lineRight = layout.getLineRight(i3);
                float f3 = z ? lineRight - fMin : lineLeft + fMin;
                if (fMin < lineWidth2) {
                    linearGradient = new LinearGradient(z ? f3 + fDpf2 : f3 - fDpf2, 0.0f, f3, 0.0f, i, i & 16777215, Shader.TileMode.CLAMP);
                } else {
                    linearGradient = null;
                }
                textPaint.setShader(linearGradient);
                canvas.save();
                if (z) {
                    lineLeft = f3;
                }
                float lineTop = layout.getLineTop(i3);
                if (!z) {
                    lineRight = f3;
                }
                canvas.clipRect(lineLeft, lineTop, lineRight, layout.getLineBottom(i3));
                layout.draw(canvas);
                canvas.restore();
            }
        }
    }

    public class LinesLayout extends LinearLayout {
        View bottomSpacer;
        View topSpacer;

        public LinesLayout(Context context) {
            super(context);
            setOrientation(1);
        }

        @Override
        public void onMeasure(int i, int i2) {
            super.onMeasure(i, i2);
            if (this.topSpacer == null || this.bottomSpacer == null || LyricsView.this.lineViews.isEmpty() || LyricsView.this.viewportHeight <= 0) {
                return;
            }
            View view = (View) LyricsView.this.lineViews.get(0);
            View view2 = (View) LyricsView.this.lineViews.get(LyricsView.this.lineViews.size() - 1);
            int measuredHeight = 0;
            for (int iIndexOfChild = indexOfChild(view2) + 1; iIndexOfChild < getChildCount(); iIndexOfChild++) {
                View childAt = getChildAt(iIndexOfChild);
                if (childAt != this.bottomSpacer && childAt.getVisibility() != 8) {
                    LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) childAt.getLayoutParams();
                    measuredHeight += childAt.getMeasuredHeight() + layoutParams.topMargin + layoutParams.bottomMargin;
                }
            }
            int iMax = Math.max(0, Math.min(AndroidUtilities.dp(32.0f), Math.round((LyricsView.this.viewportHeight * 0.5f) - (view.getMeasuredHeight() / 2.0f))));
            int iMax2 = Math.max(0, Math.min(AndroidUtilities.dp(48.0f), Math.round((LyricsView.this.viewportHeight * 0.5f) - (view2.getMeasuredHeight() / 2.0f)) - measuredHeight));
            LinearLayout.LayoutParams layoutParams2 = (LinearLayout.LayoutParams) this.topSpacer.getLayoutParams();
            LinearLayout.LayoutParams layoutParams3 = (LinearLayout.LayoutParams) this.bottomSpacer.getLayoutParams();
            if (layoutParams2.height == iMax && layoutParams3.height == iMax2) {
                return;
            }
            layoutParams2.height = iMax;
            layoutParams3.height = iMax2;
            super.onMeasure(i, i2);
        }
    }

    public static class DotsView extends View {
        private boolean active;
        private final Paint paint;
        private long startTime;

        public DotsView(Context context) {
            super(context);
            this.paint = new Paint(1);
        }

        public void setColor(int i) {
            this.paint.setColor(i);
            invalidate();
        }

        public void setActive(boolean z) {
            if (this.active != z) {
                this.active = z;
                this.startTime = SystemClock.elapsedRealtime();
                invalidate();
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
            boolean z;
            float f;
            int i;
            float fDpf2 = AndroidUtilities.dpf2(9.0f) / 2.0f;
            float f2 = 1.15f * fDpf2;
            float height = getHeight() / 2.0f;
            long jElapsedRealtime = SystemClock.elapsedRealtime();
            int i2 = 0;
            while (true) {
                z = this.active;
                if (i2 >= 3) {
                    break;
                }
                if (z) {
                    float fCos = (float) (0.5d - (Math.cos(((double) (((((((jElapsedRealtime - this.startTime) - (((long) i2) * 180)) % 1400) + 1400) % 1400) / 1400.0f) * 2.0f)) * 3.141592653589793d) * 0.5d));
                    f = (fCos * 0.45f) + 0.7f;
                    i = (int) (((fCos * 0.55f) + 0.45f) * 255.0f);
                } else {
                    f = 1.0f;
                    i = 255;
                }
                int alpha = this.paint.getAlpha();
                this.paint.setAlpha(i);
                canvas.drawCircle((i2 * (AndroidUtilities.dpf2(9.0f) + AndroidUtilities.dpf2(7.0f))) + f2, height, f * fDpf2, this.paint);
                this.paint.setAlpha(alpha);
                i2++;
            }
            if (z) {
                postInvalidateOnAnimation();
            }
        }
    }
}

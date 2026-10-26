package org.telegram.messenger.devgram.player.mini;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import org.telegram.messenger.devgram.player.PlayerConfig;
import org.telegram.messenger.devgram.player.CoverColorExtractor;
import org.telegram.messenger.devgram.player.CoverColorsWait;
import org.telegram.messenger.devgram.player.PlayerColors;
import org.telegram.messenger.devgram.player.PlayerCovers;
import org.telegram.messenger.devgram.player.ui.ExpressiveStateDrawable;
import org.telegram.messenger.devgram.utils.ui.ColorTones;
import org.telegram.messenger.devgram.utils.ui.Material3Tokens;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedLinearLayout;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ChatActivityTopPanelLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DialogsActivityTopPanelLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.RLottieImageView;
public class MiniPlayerUiHelper implements NotificationCenter.NotificationCenterDelegate {
    private int accentColor;
    private final CoverColorsWait accentWait;
    private boolean active;
    private MessageObject boundMessage;
    private int closeColor;
    private final ImageView closeIcon;
    private boolean closeMode;
    private float closeProgress;
    private final SpringAnimation closeSpring;
    private float collapseFrom;
    private final SpringAnimation collapseSpring;
    private NotificationCenter.ObserversGroup colorObservers;
    private boolean coverTinted;
    private final CoverView coverView;
    private boolean darkSurface;
    private float displayedProgress;
    private final FrameLayout host;
    private float lastProgress;
    private long lastWaveFrame;
    private final ExpressiveStateDrawable nextBackground;
    private final FrameLayout nextButton;
    private final RLottieImageView nextIcon;
    private Runnable onColorsChanged;
    private int placeholderColor;
    private final ExpressiveStateDrawable playBackground;
    private final ImageView playButton;
    private final PlayPauseDrawable playPauseDrawable;
    private boolean playing;
    private final ExpressiveStateDrawable prevBackground;
    private final RLottieImageView prevButton;
    private float previousTarget;
    private float resetFrom;
    private long resetStart;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint ringPaint;
    private final RectF ringRect;
    private int ringTrackColor;
    private View selectorView;
    private final ExpressiveStateDrawable silentBackground;
    private View silentButton;
    private ImageView silentImage;
    private LegacyState silentLegacy;
    private ColorFilter silentLegacyFilter;
    private final ExpressiveStateDrawable speedBackground;
    private View speedButton;
    private LegacyState speedLegacy;
    private int subtitleColor;
    private final AudioPlayerAlert.ClippingTextViewSwitcher subtitleView;
    private ValueAnimator surfaceAnimator;
    private int surfaceColor;
    private int surfaceTarget;
    private View surfaceView;
    private boolean swipeClosing;
    private float swipeCollapse;
    private float swipeDownX;
    private float swipeDownY;
    private boolean swipeDragging;
    private float swipeOffset;
    private final SpringAnimation swipeSpring;
    private float swipeStartOffset;
    private boolean swipeTracking;
    private VelocityTracker swipeVelocity;
    private int titleColor;
    private final AudioPlayerAlert.ClippingTextViewSwitcher titleView;
    private final int touchSlop;
    private boolean voice;
    private float waveFactor;
    private final Path wavePath;
    private float wavePhase;
    private final SpringAnimation waveSpring;

    public static boolean isActive(MiniPlayerUiHelper miniPlayerUiHelper) {
        return miniPlayerUiHelper != null && miniPlayerUiHelper.active;
    }

    public MiniPlayerUiHelper(final FrameLayout frameLayout, Theme.ResourcesProvider resourcesProvider) {
        Paint paint = new Paint(1);
        this.ringPaint = paint;
        this.wavePath = new Path();
        this.ringRect = new RectF();
        this.accentWait = new CoverColorsWait(() -> PlayerConfig.getPlayerMiniCoverColors(), () -> updateColors());
        this.host = frameLayout;
        this.resourcesProvider = resourcesProvider;
        final Context context = frameLayout.getContext();
        resolveColors();
        CoverView coverView = new CoverView(context, obj -> lambda_new_0((ImageReceiver) obj));
        this.coverView = coverView;
        frameLayout.addView(coverView, LayoutHelper.createFrame(36, 36.0f, 51, 6.0f, 6.0f, 0.0f, 0.0f));
        AudioPlayerAlert.ClippingTextViewSwitcher clippingTextViewSwitcher = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            public TextView createTextView() {
                return MiniPlayerUiHelper.this.createLine(context, 14, true);
            }
        };
        this.titleView = clippingTextViewSwitcher;
        frameLayout.addView(clippingTextViewSwitcher, LayoutHelper.createFrame(-1, 20.0f, 51, 50.0f, 6.0f, 122.0f, 0.0f));
        AudioPlayerAlert.ClippingTextViewSwitcher clippingTextViewSwitcher2 = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            public TextView createTextView() {
                return MiniPlayerUiHelper.this.createLine(context, 13, false);
            }
        };
        this.subtitleView = clippingTextViewSwitcher2;
        frameLayout.addView(clippingTextViewSwitcher2, LayoutHelper.createFrame(-1, 18.0f, 51, 50.0f, 24.0f, 122.0f, 0.0f));
        RLottieImageView rLottieImageView = new RLottieImageView(context);
        this.prevButton = rLottieImageView;
        ImageView.ScaleType scaleType = ImageView.ScaleType.CENTER;
        rLottieImageView.setScaleType(scaleType);
        rLottieImageView.setAnimation(R.raw.player_prev, 16, 16);
        ExpressiveStateDrawable expressiveStateDrawableCreateBackground = createBackground();
        this.prevBackground = expressiveStateDrawableCreateBackground;
        rLottieImageView.setBackground(expressiveStateDrawableCreateBackground);
        rLottieImageView.setContentDescription(LocaleController.getString(R.string.AccDescrPrevious));
        rLottieImageView.setOnClickListener(view -> lambda_new_1(view));
        frameLayout.addView(rLottieImageView, createSlot(0));
        ImageView imageView = new ImageView(context);
        this.playButton = imageView;
        imageView.setScaleType(scaleType);
        PlayPauseDrawable playPauseDrawable = new PlayPauseDrawable(14);
        this.playPauseDrawable = playPauseDrawable;
        imageView.setImageDrawable(playPauseDrawable);
        ExpressiveStateDrawable expressiveStateDrawableCreateBackground2 = createBackground();
        this.playBackground = expressiveStateDrawableCreateBackground2;
        imageView.setBackground(expressiveStateDrawableCreateBackground2);
        imageView.setOnClickListener(view -> MiniPlayerUiHelper.m1750$r8$lambda$9AAMrontLDwv7isNKLJ6ZT7Xj0(view));
        frameLayout.addView(imageView, createSlot(1));
        FrameLayout frameLayout2 = new FrameLayout(context);
        this.nextButton = frameLayout2;
        ExpressiveStateDrawable expressiveStateDrawableCreateBackground3 = createBackground();
        this.nextBackground = expressiveStateDrawableCreateBackground3;
        frameLayout2.setBackground(expressiveStateDrawableCreateBackground3);
        RLottieImageView rLottieImageView2 = new RLottieImageView(context);
        this.nextIcon = rLottieImageView2;
        rLottieImageView2.setScaleType(scaleType);
        rLottieImageView2.setAnimation(R.raw.player_prev, 16, 16);
        rLottieImageView2.setRotation(180.0f);
        frameLayout2.addView(rLottieImageView2, LayoutHelper.createFrame(-1, -1.0f));
        ImageView imageView2 = new ImageView(context);
        this.closeIcon = imageView2;
        imageView2.setScaleType(scaleType);
        imageView2.setImageResource(R.drawable.ic_close_white);
        frameLayout2.addView(imageView2, LayoutHelper.createFrame(-1, -1.0f));
        frameLayout2.setOnClickListener(view -> lambda_new_3(view));
        frameLayout.addView(frameLayout2, createSlot(2));
        this.silentBackground = createBackground();
        this.speedBackground = createBackground();
        this.closeSpring = Material3Tokens.createSpring(0.6f, 800.0f, 0.002f, obj -> applyCloseProgress(((Float) obj).floatValue()));
        applyCloseProgress(0.0f);
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.swipeSpring = Material3Tokens.createSpring(0.9f, 700.0f, 1.0f, obj -> lambda_new_4((Float) obj));
        this.collapseSpring = Material3Tokens.createSpring(1.0f, 700.0f, 0.002f, obj -> lambda_new_5(frameLayout, (Float) obj));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(AndroidUtilities.dpf2(2.5f));
        this.waveSpring = Material3Tokens.createSpring(1.0f, 380.0f, 0.002f, obj -> lambda_new_6(frameLayout, (Float) obj));
        frameLayout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                MiniPlayerUiHelper.this.coverView.setCoverRadius(AndroidUtilities.dp(MiniPlayerUiHelper.this.resolveCoverRadiusDp()));
                MiniPlayerUiHelper.this.observeColors(true);
                if (MiniPlayerUiHelper.this.active) {
                    MiniPlayerUiHelper.this.updateColors();
                    MiniPlayerUiHelper.this.requestCoverColors();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                MiniPlayerUiHelper.this.observeColors(false);
                MiniPlayerUiHelper.this.closeSpring.cancel();
                MiniPlayerUiHelper miniPlayerUiHelper = MiniPlayerUiHelper.this;
                miniPlayerUiHelper.applyCloseProgress(miniPlayerUiHelper.closeMode ? 1.0f : 0.0f);
                MiniPlayerUiHelper.this.waveSpring.cancel();
                MiniPlayerUiHelper miniPlayerUiHelper2 = MiniPlayerUiHelper.this;
                miniPlayerUiHelper2.waveFactor = miniPlayerUiHelper2.playing ? 1.0f : 0.0f;
                MiniPlayerUiHelper.this.lastWaveFrame = 0L;
            }
        });
        observeColors(frameLayout.isAttachedToWindow());
        setOwnViewsVisible(false);
        updateColors();
    }
    public /* synthetic */ void lambda_new_0(ImageReceiver imageReceiver) {
        requestCoverColors();
    }
    public /* synthetic */ void lambda_new_1(View view) {
        if (MediaController.getInstance().getPlayingMessageObject() == null) {
            return;
        }
        MediaController.getInstance().playPreviousMessage();
        this.prevButton.setProgress(0.0f);
        this.prevButton.playAnimation();
    }
    public static /* synthetic */ void m1750$r8$lambda$9AAMrontLDwv7isNKLJ6ZT7Xj0(View view) {
        MediaController mediaController = MediaController.getInstance();
        MessageObject playingMessageObject = mediaController.getPlayingMessageObject();
        if (playingMessageObject == null) {
            return;
        }
        if (mediaController.isMessagePaused()) {
            mediaController.playMessage(playingMessageObject);
        } else {
            mediaController.pauseMessage(playingMessageObject);
        }
    }
    public /* synthetic */ void lambda_new_3(View view) {
        onNextOrCloseClick();
    }
    public /* synthetic */ void lambda_new_4(Float f) {
        this.swipeOffset = Math.max(0.0f, f.floatValue());
        applySwipeShrink();
    }
    public /* synthetic */ void lambda_new_5(FrameLayout frameLayout, Float f) {
        this.swipeCollapse = Utilities.clamp01(f.floatValue());
        frameLayout.invalidate();
    }
    public /* synthetic */ void lambda_new_6(FrameLayout frameLayout, Float f) {
        this.waveFactor = Utilities.clamp01(f.floatValue());
        frameLayout.invalidate();
    }
    public void observeColors(boolean z) {
        NotificationCenter.ObserversGroup observersGroup;
        if (z && this.colorObservers == null) {
            this.colorObservers = NotificationCenter.getGlobalInstance().createObserversGroup(this).add(NotificationCenter.playerColorsChanged);
        } else {
            if (z || (observersGroup = this.colorObservers) == null) {
                return;
            }
            observersGroup.removeAllObservers();
            this.colorObservers = null;
        }
    }

    @Override
    public void didReceivedNotification(int i, int i2, Object... objArr) {
        if (i == NotificationCenter.playerColorsChanged && this.active) {
            updateColors();
            requestCoverColors();
        }
    }
    public void requestCoverColors() {
        if (!this.active || this.voice || this.boundMessage == null || !PlayerConfig.getPlayerMiniCoverColors()) {
            return;
        }
        final MessageObject messageObject = this.boundMessage;
        ImageReceiver currentReceiver = this.coverView.getCurrentReceiver();
        if (currentReceiver == null || !currentReceiver.hasBitmapImage()) {
            return;
        }
        CoverColorExtractor.extract(messageObject, currentReceiver, obj -> lambda_requestCoverColors_7(messageObject, (Integer) obj));
    }
    public /* synthetic */ void lambda_requestCoverColors_7(MessageObject messageObject, Integer num) {
        if (this.boundMessage == messageObject) {
            updateColors();
        }
    }

    private ExpressiveStateDrawable createBackground() {
        return new ExpressiveStateDrawable(15.0f).setInset(3.0f);
    }

    private static FrameLayout.LayoutParams createSlot(int i) {
        return LayoutHelper.createFrame(36, 36.0f, 53, 0.0f, 6.0f, ((2 - i) * 36) + 6, 0.0f);
    }

    private int resolveCapsuleRadiusDp() {
        for (ViewParent parent = this.host.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ChatActivityTopPanelLayout) {
                return 18;
            }
        }
        return 18;
    }
    public int resolveCoverRadiusDp() {
        return Math.max(0, Math.min(18, Math.min(resolveCapsuleRadiusDp(), 24) - 6));
    }

    private void updateCoverRadius(float f) {
        if (f <= 0.0f || this.voice) {
            this.coverView.setRadiusOverride(-1.0f);
        } else {
            this.coverView.setRadiusOverride(Math.max(0.0f, Math.min(AndroidUtilities.dp(36.0f) / 2.0f, Math.min(AndroidUtilities.dp(resolveCapsuleRadiusDp()), (AndroidUtilities.dp(48.0f) - f) / 2.0f) - AndroidUtilities.dp(6.0f))));
        }
    }
    public TextView createLine(Context context, int i, boolean z) {
        TextView textView = new TextView(context);
        textView.setMaxLines(1);
        textView.setLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(19);
        textView.setTextSize(1, i);
        textView.setTextColor(z ? this.titleColor : this.subtitleColor);
        textView.setTypeface(z ? AndroidUtilities.bold() : AndroidUtilities.regular());
        return textView;
    }

    public void onStyleApplied(boolean z, View view, View view2, View view3, View view4, View view5, ImageView imageView, View view6, View view7) {
        this.speedButton = view4;
        this.silentButton = view5;
        this.silentImage = imageView;
        ValueAnimator valueAnimator = this.surfaceAnimator;
        if (valueAnimator != null) {
            valueAnimator.cancel();
            this.surfaceAnimator = null;
        }
        this.surfaceColor = 0;
        this.surfaceTarget = 0;
        if (!z) {
            view6 = null;
        }
        this.surfaceView = view6;
        if (!z) {
            view7 = null;
        }
        this.selectorView = view7;
        boolean z2 = this.active;
        this.active = z;
        if (z) {
            if (!z2) {
                this.speedLegacy = LegacyState.capture(view4);
                this.silentLegacy = LegacyState.capture(view5);
                this.silentLegacyFilter = imageView != null ? imageView.getColorFilter() : null;
            }
            if (view != null) {
                view.setVisibility(8);
            }
            if (view3 != null) {
                view3.setVisibility(8);
            }
            if (view2 != null) {
                view2.setVisibility(4);
            }
            if (view4 != null) {
                view4.setLayoutParams(createSlot(0));
                view4.setBackground(this.speedBackground);
            }
            if (view5 != null) {
                view5.setLayoutParams(createSlot(0));
                view5.setBackground(this.silentBackground);
            }
            setOwnViewsVisible(true);
            updateColors();
            return;
        }
        if (z2) {
            if (view2 != null) {
                view2.setVisibility(0);
            }
            LegacyState legacyState = this.speedLegacy;
            if (legacyState != null) {
                legacyState.restore(view4);
                view4.setAlpha(1.0f);
                view4.setEnabled(true);
                if (view4.getVisibility() == 4) {
                    view4.setVisibility(8);
                }
            }
            LegacyState legacyState2 = this.silentLegacy;
            if (legacyState2 != null) {
                legacyState2.restore(view5);
                view5.setVisibility(8);
            }
            if (imageView != null) {
                imageView.setColorFilter(this.silentLegacyFilter);
            }
            this.silentLegacy = null;
            this.speedLegacy = null;
            this.silentLegacyFilter = null;
            setOwnViewsVisible(false);
            this.boundMessage = null;
        }
    }

    private void setOwnViewsVisible(boolean z) {
        int i = z ? 0 : 8;
        this.coverView.setVisibility(i);
        this.titleView.setVisibility(i);
        this.subtitleView.setVisibility(i);
        this.playButton.setVisibility(i);
        this.nextButton.setVisibility(i);
        if (z) {
            return;
        }
        this.prevButton.setVisibility(8);
    }

    public void bind(MessageObject messageObject, boolean z) {
        if (!this.active || messageObject == null) {
            return;
        }
        MediaController mediaController = MediaController.getInstance();
        boolean z2 = false;
        if (!this.swipeDragging && (this.swipeClosing || this.swipeOffset != 0.0f)) {
            this.swipeSpring.cancel();
            this.collapseSpring.cancel();
            this.swipeClosing = false;
            this.swipeCollapse = 0.0f;
            this.swipeOffset = 0.0f;
            updateCoverRadius(0.0f);
            this.host.requestLayout();
            this.host.invalidate();
        }
        MessageObject messageObject2 = this.boundMessage;
        boolean z3 = messageObject2 != messageObject;
        if (z3) {
            boolean z4 = z && messageObject2 != null;
            this.boundMessage = messageObject;
            this.voice = messageObject.isVoice() || messageObject.isRoundVideo();
            String musicAuthor = messageObject.getMusicAuthor();
            String musicTitle = messageObject.getMusicTitle();
            this.titleView.setText(this.voice ? musicAuthor : musicTitle, z4);
            AudioPlayerAlert.ClippingTextViewSwitcher clippingTextViewSwitcher = this.subtitleView;
            if (this.voice) {
                musicAuthor = musicTitle;
            }
            clippingTextViewSwitcher.setText(musicAuthor, z4);
            this.coverView.setCoverRadius(AndroidUtilities.dp(resolveCoverRadiusDp()));
            this.coverView.set(messageObject, this.voice, z4);
            updateColors();
            requestCoverColors();
            this.nextIcon.setVisibility(this.voice ? 8 : 0);
            this.nextButton.setContentDescription(LocaleController.getString(this.voice ? R.string.AccDescrClosePlayer : R.string.Next));
            if (!this.voice) {
                PlayerCovers.preloadNeighbors(messageObject.currentAccount);
            }
        }
        boolean z5 = mediaController.isSilent;
        boolean z6 = !z5 && (this.voice || messageObject.getDuration() >= 600.0d);
        this.prevButton.setVisibility((z5 || z6) ? 8 : 0);
        View view = this.speedButton;
        if (view != null) {
            view.setAlpha(z6 ? 1.0f : 0.0f);
            this.speedButton.setEnabled(z6);
            this.speedButton.setVisibility(z6 ? 0 : 4);
        }
        View view2 = this.silentButton;
        if (view2 != null) {
            view2.setVisibility(z5 ? 0 : 8);
        }
        boolean zIsMessagePaused = mediaController.isMessagePaused();
        this.playPauseDrawable.setPause(!zIsMessagePaused, z);
        this.playButton.setContentDescription(LocaleController.getString(zIsMessagePaused ? R.string.AccActionPlay : R.string.AccActionPause));
        setPlaying(!zIsMessagePaused, z);
        if (this.voice) {
            setCloseMode(true, false);
            return;
        }
        if (z && !z3) {
            z2 = true;
        }
        setCloseMode(zIsMessagePaused, z2);
    }

    private void setPlaying(boolean z, boolean z2) {
        if (this.playing == z) {
            if (z2) {
                return;
            }
            if (this.waveFactor == (z ? 1.0f : 0.0f)) {
                return;
            }
        }
        this.playing = z;
        this.lastWaveFrame = 0L;
        if (z2 && this.host.isAttachedToWindow()) {
            Material3Tokens.animate(this.waveSpring, this.waveFactor, z ? 1.0f : 0.0f);
            return;
        }
        this.waveSpring.cancel();
        this.waveFactor = z ? 1.0f : 0.0f;
        this.host.invalidate();
    }

    private void setCloseMode(boolean z, boolean z2) {
        if (this.closeMode != z) {
            this.closeMode = z;
            if (!this.voice) {
                this.nextButton.setContentDescription(LocaleController.getString(z ? R.string.AccDescrClosePlayer : R.string.Next));
            }
        }
        float f = z ? 1.0f : 0.0f;
        SpringAnimation springAnimation = this.closeSpring;
        if (z2) {
            Material3Tokens.animate(springAnimation, this.closeProgress, f);
        } else {
            springAnimation.cancel();
            applyCloseProgress(f);
        }
    }
    public void applyCloseProgress(float f) {
        this.closeProgress = f;
        float f2 = 0.4f * f;
        float f3 = 1.0f - f2;
        float f4 = 1.0f - f;
        this.nextIcon.setAlpha(Utilities.clamp01(f4));
        this.nextIcon.setRotation(180.0f - (f * 90.0f));
        this.nextIcon.setScaleX(f3);
        this.nextIcon.setScaleY(f3);
        float f5 = f2 + 0.6f;
        this.closeIcon.setAlpha(Utilities.clamp01(f));
        this.closeIcon.setRotation(f4 * 90.0f);
        this.closeIcon.setScaleX(f5);
        this.closeIcon.setScaleY(f5);
        this.nextBackground.setColors(ColorUtils.blendARGB(this.accentColor, this.closeColor, Utilities.clamp01(f)), 0);
    }

    private void onNextOrCloseClick() {
        MediaController mediaController = MediaController.getInstance();
        if (mediaController.getPlayingMessageObject() == null) {
            return;
        }
        if (this.voice) {
            mediaController.cleanupPlayer(true, true);
            return;
        }
        if (this.closeMode && mediaController.isMessagePaused()) {
            mediaController.cleanupPlayer(true, true);
            return;
        }
        mediaController.playNextMessage();
        this.nextIcon.setProgress(0.0f);
        this.nextIcon.playAnimation();
    }

    public int getSpeedColor(float f) {
        return Math.abs(f - 1.0f) < 0.01f ? this.closeColor : this.accentColor;
    }

    private void resolveColors() {
        int color = Theme.getColor(Theme.key_inappPlayerPlayPause, this.resourcesProvider);
        if (!this.accentWait.shouldWait(this.boundMessage)) {
            this.accentColor = PlayerColors.getMiniPlayerAccent(color, this.resourcesProvider);
        }
        this.coverTinted = PlayerConfig.getPlayerMiniCoverColors() && this.accentColor != color;
        int color2 = Theme.getColor(Theme.key_inappPlayerClose, this.resourcesProvider);
        if (this.coverTinted) {
            color2 = PlayerColors.getMiniPlayerSecondary(color2, this.resourcesProvider);
        }
        this.closeColor = color2;
        int color3 = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, this.resourcesProvider);
        if (this.coverTinted) {
            color3 = PlayerColors.getMiniPlayerText(color3, this.resourcesProvider);
        }
        this.titleColor = color3;
        this.subtitleColor = this.closeColor;
        boolean zIsLightSurface = ColorTones.isLightSurface(Theme.getColor(Theme.key_windowBackgroundWhite, this.resourcesProvider));
        this.darkSurface = !zIsLightSurface;
        this.placeholderColor = ColorUtils.setAlphaComponent(this.accentColor, (int) ((!zIsLightSurface ? 0.2f : 0.14f) * 255.0f));
        this.ringTrackColor = ColorUtils.setAlphaComponent(this.accentColor, 76);
    }

    public void setOnColorsChanged(Runnable runnable) {
        this.onColorsChanged = runnable;
    }

    public void updateColors() {
        ImageView imageView;
        resolveColors();
        int i = this.accentColor;
        PorterDuff.Mode mode = PorterDuff.Mode.SRC_IN;
        PorterDuffColorFilter porterDuffColorFilter = new PorterDuffColorFilter(i, mode);
        this.prevButton.setColorFilter(porterDuffColorFilter);
        this.nextIcon.setColorFilter(porterDuffColorFilter);
        this.playPauseDrawable.setColor(this.accentColor);
        this.playButton.invalidate();
        this.closeIcon.setColorFilter(new PorterDuffColorFilter(this.closeColor, mode));
        int i2 = 0;
        this.prevBackground.setColors(this.accentColor, 0);
        this.playBackground.setColors(this.accentColor, 0);
        this.silentBackground.setColors(this.accentColor, 0);
        this.speedBackground.setColors(this.accentColor, 0);
        applyCloseProgress(this.closeProgress);
        while (i2 < 2) {
            AudioPlayerAlert.ClippingTextViewSwitcher clippingTextViewSwitcher = this.titleView;
            TextView textView = i2 == 0 ? clippingTextViewSwitcher.getTextView() : clippingTextViewSwitcher.getNextTextView();
            AudioPlayerAlert.ClippingTextViewSwitcher clippingTextViewSwitcher2 = this.subtitleView;
            TextView textView2 = i2 == 0 ? clippingTextViewSwitcher2.getTextView() : clippingTextViewSwitcher2.getNextTextView();
            if (textView != null) {
                textView.setTextColor(this.titleColor);
            }
            if (textView2 != null) {
                textView2.setTextColor(this.subtitleColor);
            }
            i2++;
        }
        if (this.active && (imageView = this.silentImage) != null) {
            imageView.setColorFilter(porterDuffColorFilter);
        }
        this.coverView.setColors(this.accentColor, this.placeholderColor);
        applySurfaceTint();
        this.host.invalidate();
        Runnable runnable = this.onColorsChanged;
        if (runnable != null) {
            runnable.run();
        }
    }

    private void applySurfaceTint() {
        int alphaComponent;
        Drawable selectorDrawable;
        if (this.surfaceView == null) {
            return;
        }
        if (this.coverTinted) {
            alphaComponent = ColorUtils.setAlphaComponent(this.accentColor, (int) ((this.darkSurface ? 0.12f : 0.08f) * 255.0f));
        } else {
            alphaComponent = 0;
        }
        if (alphaComponent != this.surfaceTarget || (this.surfaceAnimator == null && this.surfaceColor != alphaComponent)) {
            this.surfaceTarget = alphaComponent;
            View view = this.selectorView;
            if (view != null) {
                if (this.coverTinted) {
                    selectorDrawable = Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(this.accentColor, 40), 2);
                } else {
                    selectorDrawable = Theme.getSelectorDrawable(false);
                }
                view.setBackground(selectorDrawable);
            }
            ValueAnimator valueAnimator = this.surfaceAnimator;
            if (valueAnimator != null) {
                valueAnimator.cancel();
                this.surfaceAnimator = null;
            }
            if (!this.host.isAttachedToWindow() || !this.active) {
                setSurfaceColor(alphaComponent);
                return;
            }
            final int alphaComponent2 = Color.alpha(this.surfaceColor) == 0 ? ColorUtils.setAlphaComponent(alphaComponent, 0) : this.surfaceColor;
            if (Color.alpha(alphaComponent) == 0) {
                alphaComponent = ColorUtils.setAlphaComponent(this.surfaceColor, 0);
            }
            final int alphaTarget = alphaComponent;
            ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(0.0f, 1.0f);
            this.surfaceAnimator = valueAnimatorOfFloat;
            valueAnimatorOfFloat.setDuration(250L);
            this.surfaceAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            this.surfaceAnimator.addUpdateListener(valueAnimator2 -> lambda_applySurfaceTint_8(alphaComponent2, alphaTarget, valueAnimator2));
            this.surfaceAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (MiniPlayerUiHelper.this.surfaceAnimator == animator) {
                        MiniPlayerUiHelper.this.surfaceAnimator = null;
                        MiniPlayerUiHelper miniPlayerUiHelper = MiniPlayerUiHelper.this;
                        miniPlayerUiHelper.setSurfaceColor(miniPlayerUiHelper.surfaceTarget);
                    }
                }
            });
            this.surfaceAnimator.start();
        }
    }
    public /* synthetic */ void lambda_applySurfaceTint_8(int i, int i2, ValueAnimator valueAnimator) {
        setSurfaceColor(ColorUtils.blendARGB(i, i2, ((Float) valueAnimator.getAnimatedValue()).floatValue()));
    }
    public void setSurfaceColor(int i) {
        this.surfaceColor = i;
        View view = this.surfaceView;
        if (view != null) {
            view.setBackgroundColor(i);
        }
    }

    public int beginSwipeDraw(Canvas canvas) {
        float entryVisibility;
        float swipeShrink = getSwipeShrink();
        if (this.swipeClosing) {
            float fDp = AndroidUtilities.dp(48.0f);
            entryVisibility = fDp - ((fDp - this.collapseFrom) * getEntryVisibility());
            updateCoverRadius(entryVisibility);
            this.host.postInvalidateOnAnimation();
        } else {
            entryVisibility = swipeShrink;
        }
        if (entryVisibility <= 0.0f) {
            return -1;
        }
        if (this.swipeClosing) {
            swipeShrink = this.collapseFrom;
        }
        int iSaveLayerAlpha = canvas.saveLayerAlpha(0.0f, 0.0f, this.host.getMeasuredWidth(), this.host.getMeasuredHeight(), (int) ((1.0f - (Utilities.clamp01(swipeShrink / (AndroidUtilities.dp(48.0f) * 0.3f)) * 0.35f)) * (this.swipeClosing ? 1.0f - this.swipeCollapse : 1.0f) * 255.0f));
        canvas.translate(0.0f, -entryVisibility);
        return iSaveLayerAlpha;
    }

    private float getEntryVisibility() {
        View view = this.host;
        for (ViewParent parent = view.getParent(); parent instanceof View; parent = parent.getParent()) {
            if (parent instanceof AnimatedLinearLayout) {
                return Utilities.clamp01(view.getAlpha());
            }
            view = (View) parent;
        }
        return 1.0f;
    }

    public boolean isSwipeClosing() {
        return this.swipeClosing;
    }

    public int getSwipeShrink() {
        if (this.swipeClosing) {
            return Math.round(this.collapseFrom);
        }
        if (this.swipeOffset <= 0.0f) {
            return 0;
        }
        float fDp = AndroidUtilities.dp(48.0f) * 0.3f;
        return Math.round(fDp * ((float) Math.tanh(this.swipeOffset / (3.0f * fDp))));
    }

    private void applySwipeShrink() {
        updateCoverRadius(getSwipeShrink());
        setPanelLayoutAnimationSkipped(true);
        this.host.requestLayout();
        this.host.invalidate();
    }

    private void setPanelLayoutAnimationSkipped(boolean z) {
        for (ViewParent parent = this.host.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof AnimatedLinearLayout) {
                return;
            }
        }
    }

    public boolean onInterceptSwipe(MotionEvent motionEvent) {
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked == 0) {
            trackSwipeDown(motionEvent);
            return false;
        }
        if (actionMasked != 1) {
            if (actionMasked == 2) {
                if (this.swipeTracking && !this.swipeDragging) {
                    this.swipeVelocity.addMovement(motionEvent);
                    updateSwipe(motionEvent);
                }
                return this.swipeDragging;
            }
            if (actionMasked != 3) {
                return this.swipeDragging;
            }
        }
        if (!this.swipeDragging) {
            resetSwipeTracking();
        }
        return this.swipeDragging;
    }

    public boolean onSwipeTouchEvent(MotionEvent motionEvent, Predicate<MotionEvent> predicate) {
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked == 0) {
            trackSwipeDown(motionEvent);
            return false;
        }
        if (actionMasked != 1) {
            if (actionMasked == 2) {
                if (!this.swipeTracking) {
                    return false;
                }
                this.swipeVelocity.addMovement(motionEvent);
                boolean z = this.swipeDragging;
                updateSwipe(motionEvent);
                if (!z && this.swipeDragging) {
                    MotionEvent motionEventObtain = MotionEvent.obtain(motionEvent);
                    motionEventObtain.setAction(3);
                    predicate.test(motionEventObtain);
                    motionEventObtain.recycle();
                }
                return this.swipeDragging;
            }
            if (actionMasked != 3) {
                return this.swipeDragging;
            }
        }
        if (this.swipeDragging) {
            VelocityTracker velocityTracker = this.swipeVelocity;
            if (velocityTracker != null) {
                velocityTracker.addMovement(motionEvent);
            }
            finishSwipe(motionEvent.getActionMasked() == 1);
            return true;
        }
        resetSwipeTracking();
        return false;
    }

    private void trackSwipeDown(MotionEvent motionEvent) {
        boolean z = (!this.active || this.boundMessage == null || this.swipeClosing || MediaController.getInstance().getPlayingMessageObject() == null) ? false : true;
        this.swipeTracking = z;
        this.swipeDragging = false;
        if (z) {
            this.swipeSpring.cancel();
            this.swipeDownX = motionEvent.getX();
            this.swipeDownY = motionEvent.getY();
            this.swipeStartOffset = this.swipeOffset;
            VelocityTracker velocityTracker = this.swipeVelocity;
            if (velocityTracker == null) {
                this.swipeVelocity = VelocityTracker.obtain();
            } else {
                velocityTracker.clear();
            }
            this.swipeVelocity.addMovement(motionEvent);
        }
    }

    private void updateSwipe(MotionEvent motionEvent) {
        float x = motionEvent.getX() - this.swipeDownX;
        float y = this.swipeDownY - motionEvent.getY();
        if (!this.swipeDragging) {
            if (y > this.touchSlop && y > Math.abs(x) * 1.2f) {
                this.swipeDragging = true;
                this.swipeDownY -= this.touchSlop;
                ViewParent parent = this.host.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                this.host.cancelLongPress();
                this.host.setPressed(false);
            } else {
                float fAbs = Math.abs(x);
                int i = this.touchSlop;
                if (fAbs > i || y < (-i)) {
                    resetSwipeTracking();
                    return;
                }
                return;
            }
        }
        this.swipeOffset = Math.max(0.0f, (this.swipeStartOffset + this.swipeDownY) - motionEvent.getY());
        applySwipeShrink();
    }

    /* JADX WARN: Code duplicated, block: B:15:0x0035  */
    private void finishSwipe(boolean z) {
        float f;
        boolean z2;
        VelocityTracker velocityTracker = this.swipeVelocity;
        if (velocityTracker != null) {
            velocityTracker.computeCurrentVelocity(1000);
            f = -this.swipeVelocity.getYVelocity();
        } else {
            f = 0.0f;
        }
        if (z) {
            float f2 = this.swipeOffset;
            if (f2 <= 0.0f || (f2 < AndroidUtilities.dp(40.0f) && f <= AndroidUtilities.dp(500.0f))) {
                z2 = false;
            } else {
                z2 = true;
            }
        } else {
            z2 = false;
        }
        resetSwipeTracking();
        this.swipeSpring.cancel();
        if (z2) {
            this.collapseFrom = getSwipeShrink();
            this.swipeCollapse = 0.0f;
            this.swipeClosing = true;
            this.collapseSpring.cancel();
            this.collapseSpring.setStartValue(0.0f);
            this.collapseSpring.getSpring().setFinalPosition(1.0f);
            this.collapseSpring.start();
            setPanelLayoutAnimationSkipped(false);
            performSwipeClose();
            return;
        }
        this.swipeSpring.setStartValue(this.swipeOffset);
        this.swipeSpring.setStartVelocity(0.0f);
        this.swipeSpring.getSpring().setFinalPosition(0.0f);
        this.swipeSpring.start();
    }

    private void resetSwipeTracking() {
        this.swipeTracking = false;
        this.swipeDragging = false;
        VelocityTracker velocityTracker = this.swipeVelocity;
        if (velocityTracker != null) {
            velocityTracker.recycle();
            this.swipeVelocity = null;
        }
    }

    private void performSwipeClose() {
        MediaController mediaController = MediaController.getInstance();
        if (mediaController.getPlayingMessageObject() != null) {
            mediaController.cleanupPlayer(true, true);
        }
    }

    public void drawProgress(Canvas canvas, MessageObject messageObject) {
        float f;
        float f2;
        boolean z;
        if (messageObject != null) {
            this.lastProgress = messageObject.audioProgress;
        }
        long jCurrentAnimationTimeMillis = AnimationUtils.currentAnimationTimeMillis();
        float fClamp01 = Utilities.clamp01(this.lastProgress);
        if (fClamp01 <= 0.02f && this.previousTarget - fClamp01 >= 0.05f) {
            this.resetFrom = this.displayedProgress;
            this.resetStart = jCurrentAnimationTimeMillis;
        }
        this.previousTarget = fClamp01;
        long j = this.resetStart;
        if (j != 0) {
            float f3 = (jCurrentAnimationTimeMillis - j) / 400.0f;
            if (f3 >= 1.0f) {
                this.resetStart = 0L;
                f = 0.0f;
            } else {
                float fMax = 1.0f - Math.max(0.0f, f3);
                f = this.resetFrom * fMax * fMax * fMax;
            }
        } else {
            f = 0.0f;
        }
        this.displayedProgress = Math.max(fClamp01, f);
        if (this.resetStart != 0 && this.host.isShown()) {
            this.host.postInvalidateOnAnimation();
        }
        if (this.playButton.getVisibility() != 0 || this.playButton.getWidth() == 0) {
            return;
        }
        float x = this.playButton.getX() + (this.playButton.getWidth() / 2.0f);
        float y = this.playButton.getY() + (this.playButton.getHeight() / 2.0f);
        float fDpf2 = AndroidUtilities.dpf2(15.0f);
        float fDpf3 = AndroidUtilities.dpf2(2.5f);
        float f4 = this.displayedProgress;
        float f5 = (float) (((double) fDpf2) * 6.283185307179586d);
        this.ringRect.set(x - fDpf2, y - fDpf2, x + fDpf2, y + fDpf2);
        if (f4 < 1.0f) {
            float fDpf4 = f4 > 0.0f ? ((AndroidUtilities.dpf2(2.0f) + fDpf3) / f5) * 360.0f : 0.0f;
            float f6 = ((1.0f - f4) * 360.0f) - (fDpf4 * 2.0f);
            if (f6 > 0.0f) {
                this.ringPaint.setColor(this.ringTrackColor);
                canvas.drawArc(this.ringRect, ((f4 * 360.0f) - 90.0f) + fDpf4, f6, false, this.ringPaint);
            }
        }
        if (f4 <= 0.0f) {
            this.lastWaveFrame = 0L;
            return;
        }
        long jCurrentAnimationTimeMillis2 = AnimationUtils.currentAnimationTimeMillis();
        boolean z2 = this.playing && this.waveFactor > 0.0f;
        if (z2) {
            long j2 = this.lastWaveFrame;
            if (j2 != 0) {
                this.wavePhase = (this.wavePhase + ((((((jCurrentAnimationTimeMillis2 - j2) / 1000.0f) * 7.0f) / 11.0f) * 2.0f) * 3.1415927f)) % 6283.1855f;
            }
        }
        this.lastWaveFrame = z2 ? jCurrentAnimationTimeMillis2 : 0L;
        if (f4 < 0.1f) {
            f2 = f4 / 0.1f;
        } else {
            f2 = f4 > 0.9f ? (1.0f - f4) / 0.1f : 1.0f;
        }
        float fDpf5 = AndroidUtilities.dpf2(1.0f) * this.waveFactor * Utilities.clamp01(f2);
        this.ringPaint.setColor(this.accentColor);
        if (fDpf5 < 0.25f) {
            canvas.drawArc(this.ringRect, -90.0f, f4 * 360.0f, false, this.ringPaint);
            z = z2;
        } else {
            float f7 = f5 * f4;
            float fDpf6 = AndroidUtilities.dpf2(11.0f);
            int iMax = Math.max(2, (int) Math.ceil(f7 / AndroidUtilities.dpf2(1.0f)));
            this.wavePath.rewind();
            int i = 0;
            while (i <= iMax) {
                float f8 = (i * f7) / iMax;
                float f9 = x;
                float f10 = y;
                double d = ((double) (f8 / fDpf2)) - 1.5707963267948966d;
                float fSin = (((float) Math.sin((((double) ((f8 / fDpf6) * 2.0f)) * 3.141592653589793d) + ((double) this.wavePhase))) * fDpf5) + fDpf2;
                float fCos = (((float) Math.cos(d)) * fSin) + f9;
                boolean z3 = z2;
                float fSin2 = f10 + (((float) Math.sin(d)) * fSin);
                Path path = this.wavePath;
                if (i == 0) {
                    path.moveTo(fCos, fSin2);
                } else {
                    path.lineTo(fCos, fSin2);
                }
                i++;
                z2 = z3;
                x = f9;
                y = f10;
            }
            z = z2;
            canvas.drawPath(this.wavePath, this.ringPaint);
        }
        if (z && this.host.isShown()) {
            this.host.postInvalidateDelayed(Math.max(0L, (jCurrentAnimationTimeMillis2 + 10) - SystemClock.uptimeMillis()));
        }
    }

    public static final class LegacyState {
        private final Drawable background;
        private final ViewGroup.LayoutParams params;



        private LegacyState(ViewGroup.LayoutParams layoutParams, Drawable drawable) {
            this.params = layoutParams;
            this.background = drawable;
        }


        public final int hashCode() {
            return ((Objects.hashCode(this.params) * 31) + Objects.hashCode(this.background));
        }


        public static LegacyState capture(View view) {
            if (view != null) {
                return new LegacyState(view.getLayoutParams(), view.getBackground());
            }
            return null;
        }

        public void restore(View view) {
            view.setLayoutParams(this.params);
            view.setBackground(this.background);
        }
    }

    public static class CoverView extends View {
        private final AvatarDrawable[] avatarDrawables;
        private int containerColor;
        private int coverRadius;
        private int current;
        private final Utilities.Callback<ImageReceiver> onImageSet;
        private final Drawable placeholderIcon;
        private final Paint placeholderPaint;
        private final boolean[] placeholders;
        private final float[] radii;
        private float radiusOverride;
        private final ImageReceiver[] receivers;
        private float swap;
        private final SpringAnimation swapSpring;
        private final boolean[] voices;

        public CoverView(Context context, Utilities.Callback<ImageReceiver> callback) {
            super(context);
            this.receivers = new ImageReceiver[2];
            this.avatarDrawables = new AvatarDrawable[2];
            this.placeholders = new boolean[2];
            this.radii = new float[2];
            this.voices = new boolean[2];
            this.coverRadius = AndroidUtilities.dp(12.0f);
            this.placeholderPaint = new Paint(1);
            this.swap = 1.0f;
            this.radiusOverride = -1.0f;
            this.onImageSet = callback;
            for (int i__ = 0; i__ < 2; i__++) {
            final int i = i__;
                this.receivers[i] = new ImageReceiver(this);
                this.receivers[i].setRoundRadius(this.coverRadius);
                this.receivers[i].setDelegate((imageReceiver, z, z2, z3) -> lambda_new_0(i, imageReceiver, z, z2, z3));
                this.radii[i] = this.coverRadius;
                this.avatarDrawables[i] = new AvatarDrawable();
            }
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.files_music);
            this.placeholderIcon = drawable != null ? drawable.mutate() : null;
            SpringAnimation springAnimationCreateSpring = Material3Tokens.createSpring(0.8f, 380.0f, 0.002f, obj -> lambda_new_1((Float) obj));
            this.swapSpring = springAnimationCreateSpring;
            springAnimationCreateSpring.addEndListener((dynamicAnimation, z, f, f2) -> lambda_new_2(dynamicAnimation, z, f, f2));
        }
        public /* synthetic */ void lambda_new_0(int i, ImageReceiver imageReceiver, boolean z, boolean z2, boolean z3) {
            Utilities.Callback<ImageReceiver> callback;
            if (z && i == this.current && (callback = this.onImageSet) != null) {
                callback.run(imageReceiver);
            }
        }
        public /* synthetic */ void lambda_new_1(Float f) {
            this.swap = f.floatValue();
            invalidate();
        }
        public /* synthetic */ void lambda_new_2(DynamicAnimation dynamicAnimation, boolean z, float f, float f2) {
            if (z) {
                return;
            }
            this.receivers[1 - this.current].clearImage();
            this.placeholders[1 - this.current] = false;
        }

        public void setCoverRadius(int i) {
            if (this.coverRadius == i) {
                return;
            }
            this.coverRadius = i;
            for (int i2 = 0; i2 < 2; i2++) {
                if (!this.voices[i2]) {
                    this.radii[i2] = i;
                    this.receivers[i2].setRoundRadius(i);
                }
            }
            invalidate();
        }

        public void setRadiusOverride(float f) {
            if (this.radiusOverride == f) {
                return;
            }
            this.radiusOverride = f;
            for (int i = 0; i < 2; i++) {
                this.receivers[i].setRoundRadius((int) ((f < 0.0f || this.voices[i]) ? this.radii[i] : f));
            }
            invalidate();
        }

        public ImageReceiver getCurrentReceiver() {
            boolean[] zArr = this.placeholders;
            int i = this.current;
            if (zArr[i]) {
                return null;
            }
            return this.receivers[i];
        }

        public void setColors(int i, int i2) {
            this.containerColor = i2;
            Drawable drawable = this.placeholderIcon;
            if (drawable != null) {
                drawable.setColorFilter(new PorterDuffColorFilter(i, PorterDuff.Mode.SRC_IN));
            }
            invalidate();
        }

        public void set(MessageObject messageObject, boolean z, boolean z2) {
            if (z2) {
                this.current = 1 - this.current;
            }
            ImageReceiver[] imageReceiverArr = this.receivers;
            int i = this.current;
            ImageReceiver imageReceiver = imageReceiverArr[i];
            this.voices[i] = z;
            this.radii[i] = z ? AndroidUtilities.dp(18.0f) : this.coverRadius;
            imageReceiver.setRoundRadius((int) this.radii[this.current]);
            this.placeholders[this.current] = false;
            if (z) {
                TLObject voicePeer = PlayerCovers.getVoicePeer(messageObject);
                this.avatarDrawables[this.current].setInfo(messageObject.currentAccount, voicePeer);
                imageReceiver.setForUserOrChat(voicePeer, this.avatarDrawables[this.current]);
            } else if (!PlayerCovers.setCover(imageReceiver, messageObject, 36, false)) {
                this.placeholders[this.current] = true;
            }
            if (z2) {
                this.swap = 0.0f;
                Material3Tokens.animate(this.swapSpring, 0.0f, 1.0f);
            } else {
                this.swapSpring.cancel();
                this.swap = 1.0f;
                this.receivers[1 - this.current].clearImage();
                this.placeholders[1 - this.current] = false;
            }
            invalidate();
        }

        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();
            for (ImageReceiver imageReceiver : this.receivers) {
                imageReceiver.onAttachedToWindow();
            }
        }

        @Override
        public void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            this.swapSpring.cancel();
            this.swap = 1.0f;
            for (ImageReceiver imageReceiver : this.receivers) {
                imageReceiver.onDetachedFromWindow();
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
            float f = this.swap;
            if (f >= 1.0f) {
                drawLayer(canvas, this.current, 1.0f, 1.0f);
                return;
            }
            float fClamp01 = Utilities.clamp01(f);
            drawLayer(canvas, 1 - this.current, 1.0f - (fClamp01 * 0.2f), 1.0f - Utilities.clamp01(fClamp01 / 0.5f));
            drawLayer(canvas, this.current, Math.min(1.0f, (this.swap * 0.2f) + 0.8f), Utilities.clamp01((fClamp01 - 0.15f) / 0.85f));
        }

        private void drawLayer(Canvas canvas, int i, float f, float f2) {
            if (f2 <= 0.0f || f <= 0.0f) {
                return;
            }
            float width = getWidth();
            float height = getHeight();
            float f3 = this.radiusOverride;
            if (f3 < 0.0f || this.voices[i]) {
                f3 = this.radii[i];
            }
            float f4 = f3;
            canvas.save();
            canvas.scale(f, f, width / 2.0f, height / 2.0f);
            this.placeholderPaint.setColor(this.containerColor);
            this.placeholderPaint.setAlpha((int) (Color.alpha(this.containerColor) * f2));
            canvas.drawRoundRect(0.0f, 0.0f, width, height, f4, f4, this.placeholderPaint);
            ImageReceiver imageReceiver = this.receivers[i];
            if (this.placeholderIcon != null && !this.voices[i] && (this.placeholders[i] || !imageReceiver.hasBitmapImage() || imageReceiver.getCurrentAlpha() < 1.0f)) {
                int iDp = AndroidUtilities.dp(20.0f);
                float f5 = iDp;
                int i2 = (int) ((width - f5) / 2.0f);
                int i3 = (int) ((height - f5) / 2.0f);
                this.placeholderIcon.setBounds(i2, i3, i2 + iDp, iDp + i3);
                this.placeholderIcon.setAlpha((int) (255.0f * f2));
                this.placeholderIcon.draw(canvas);
            }
            if (!this.placeholders[i]) {
                imageReceiver.setImageCoords(0.0f, 0.0f, width, height);
                imageReceiver.setAlpha(f2);
                imageReceiver.draw(canvas);
            }
            canvas.restore();
        }
    }
}

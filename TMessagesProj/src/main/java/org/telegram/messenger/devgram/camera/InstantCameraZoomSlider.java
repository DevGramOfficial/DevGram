package org.telegram.messenger.devgram.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.utils.recorder.round.RoundVideoSession;

/** Ползунок зума камеры кружков: работает с любым бэкендом камеры через {@link Target}. */
@SuppressLint("ViewConstructor")
public class InstantCameraZoomSlider extends CameraZoomSliderView {

    /** Камера, которой управляет ползунок. */
    public interface Target {
        /** [минимум, максимум] коэффициента зума; null — камера ещё не готова. */
        float[] zoomRange(Context context);

        /** Идентификатор Camera2 для отметок объективов, null — без них. */
        String cameraId();

        boolean isFront();

        void setZoomRatio(float ratio);

        /** Кадров в секунду — чтобы не слать зум чаще кадра; 0 — неизвестно. */
        default int frameRate() {
            return 0;
        }
    }

    public static Target roundVideo(RoundVideoSession session, String cameraId, boolean front, int frameRate) {
        return new Target() {
            @Override
            public float[] zoomRange(Context context) {
                return RoundVideoCamera2Helper.readZoomRange(context, cameraId);
            }

            @Override
            public String cameraId() {
                return cameraId;
            }

            @Override
            public boolean isFront() {
                return front;
            }

            @Override
            public void setZoomRatio(float ratio) {
                session.setZoomRatio(ratio);
            }

            @Override
            public int frameRate() {
                return frameRate;
            }
        };
    }

    public static Target camera2(org.telegram.messenger.camera.Camera2Session session) {
        return new Target() {
            @Override
            public float[] zoomRange(Context context) {
                return session.isInitiated() ? new float[]{session.getMinZoom(), session.getMaxZoom()} : null;
            }

            @Override
            public String cameraId() {
                return session.getCameraId();
            }

            @Override
            public boolean isFront() {
                return session.isFrontface();
            }

            @Override
            public void setZoomRatio(float ratio) {
                session.setZoom(ratio);
            }
        };
    }

    public static Target cameraX(org.telegram.messenger.camera.DevGramCameraXSession session, boolean front) {
        return new Target() {
            @Override
            public float[] zoomRange(Context context) {
                return session.isInitiated() ? new float[]{session.getMinZoom(), session.getMaxZoom()} : null;
            }

            @Override
            public String cameraId() {
                return null;
            }

            @Override
            public boolean isFront() {
                return front;
            }

            @Override
            public void setZoomRatio(float ratio) {
                session.setZoom(ratio);
            }
        };
    }

    /** Camera1 знает только ступени зума: коэффициент переводим в ближайшую ступень. */
    public static Target camera1(org.telegram.messenger.camera.CameraSession session, boolean front) {
        return new Target() {
            private float[] ratios;
            private int lastIndex = -1;

            @Override
            public float[] zoomRange(Context context) {
                if (!session.isInitied()) {
                    return null;
                }
                ratios = session.devgramZoomRatios();
                if (ratios == null) {
                    return null;
                }
                return ratios.length < 2 ? new float[]{1f, 1f} : new float[]{ratios[0], ratios[ratios.length - 1]};
            }

            @Override
            public String cameraId() {
                return null;
            }

            @Override
            public boolean isFront() {
                return front;
            }

            @Override
            public void setZoomRatio(float ratio) {
                if (ratios == null || ratios.length < 2) {
                    return;
                }
                int index = 0;
                float best = Float.MAX_VALUE;
                for (int i = 0; i < ratios.length; i++) {
                    float d = Math.abs(ratios[i] - ratio);
                    if (d < best) {
                        best = d;
                        index = i;
                    }
                }
                if (index == lastIndex) {
                    return;
                }
                lastIndex = index;
                int last = ratios.length - 1;
                // CameraSession превращает долю обратно в индекс через (int) — добавка спасает от округления вниз
                session.setZoom(index <= 0 ? 0f : index >= last ? 1f : (index + 0.001f) / last);
            }
        };
    }

    public static final AnimationProperties.FloatProperty<InstantCameraZoomSlider> OPEN_ALPHA = new AnimationProperties.FloatProperty<InstantCameraZoomSlider>("openAlpha") {
        @Override
        public void setValue(InstantCameraZoomSlider object, float value) {
            object.setOpenAlpha(value);
        }

        @Override
        public Float get(InstantCameraZoomSlider object) {
            return object.getOpenAlpha();
        }
    };

    private final Theme.ResourcesProvider resourcesProvider;
    private final Runnable bindRunnable = this::tryBind;
    private final Runnable zoomFlushRunnable = this::flushPendingZoom;

    private Target session;
    private Object bindKey;
    private String cameraId;
    private boolean front;
    private int frameRate;

    private boolean animateNextConfiguration;
    private boolean switchingCamera;
    private int bindRetries;

    private float openAlpha;
    private float appearProgress;
    private ValueAnimator appearAnimator;
    private ValueAnimator resetZoomAnimator;
    private ValueAnimator stepZoomAnimator;

    private BlurredBackgroundDrawable blurBackground;
    private float blurCornerRadius = -1f;

    private float defaultZoom = 1f;
    private float displayOneZoom = 1f;
    private float[] opticalZoomRatios = new float[0];

    private float pendingZoom = Float.NaN;
    private float lastAppliedZoom = Float.NaN;
    private long lastZoomAppliedAt;
    private boolean zoomFlushScheduled;

    private int textureViewSize;
    private float baseTranslationY;

    public InstantCameraZoomSlider(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setVisibility(View.GONE);
        applyAppearProgress();
        setOnZoomChangeListener(this::applyZoom);
        applyTelegramColors();
    }

    public float getOpenAlpha() {
        return openAlpha;
    }

    public void setOpenAlpha(float value) {
        if (openAlpha != value) {
            openAlpha = value;
            setAlpha(value * appearProgress);
        }
    }

    private void setAppearProgress(float value) {
        if (appearProgress != value) {
            appearProgress = value;
            applyAppearProgress();
        }
    }

    private void applyAppearProgress() {
        setAlpha(openAlpha * appearProgress);
        float scale = 0.9f + appearProgress * 0.1f;
        setScaleX(scale);
        setScaleY(scale);
    }

    private void showAnimated() {
        setEnabled(true);
        if (!DevGramConfig.isZoomSlider()) {
            hideImmediately();
            return;
        }
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
        }
        if (appearAnimator != null || appearProgress >= 1f) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(appearProgress, 1f);
        appearAnimator = animator;
        animator.addUpdateListener(a -> setAppearProgress((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (appearAnimator == a) {
                    appearAnimator = null;
                    setAppearProgress(1f);
                }
            }
        });
        animator.setDuration(180);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    private void hideImmediately() {
        cancelAppearAnimation();
        setAppearProgress(0f);
        setVisibility(View.GONE);
    }

    private void cancelAppearAnimation() {
        ValueAnimator animator = appearAnimator;
        if (animator != null) {
            appearAnimator = null;
            animator.cancel();
        }
    }

    private void applyTelegramColors() {
        int background = Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider);
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        int onAccent = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);
        int text = Theme.getColor(Theme.key_chat_messagePanelText, resourcesProvider);
        setColors(background, text, accent, accent, onAccent);
        setToggleTextColor(text);
    }

    public void setBlurBackground(BlurredBackgroundDrawable drawable) {
        if (blurBackground != drawable) {
            blurBackground = drawable;
            blurCornerRadius = -1f;
            invalidate();
        }
    }

    @Override
    public boolean drawPillBackground(Canvas canvas, RectF bounds, float radius) {
        BlurredBackgroundDrawable drawable = blurBackground;
        if (drawable == null) {
            return false;
        }
        if (blurCornerRadius != radius) {
            blurCornerRadius = radius;
            drawable.setRadius(radius);
        }
        drawable.setBounds(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
        drawable.draw(canvas);
        return true;
    }

    /** Привязка к настроенной камере нового рекордера; повторный вызов с той же камерой ничего не делает. */
    public void bindSession(RoundVideoSession session, RoundVideoSession.CameraCapabilities capabilities) {
        String activeCameraId = capabilities.getActiveCameraId();
        RoundVideoSession.CameraFacing activeFacing = capabilities.getActiveFacing();
        if (session == null || activeCameraId == null || activeFacing == null) {
            return;
        }
        RoundVideoSession.FrameRate rate = capabilities.getActiveFrameRate();
        bindTarget(roundVideo(session, activeCameraId, activeFacing == RoundVideoSession.CameraFacing.FRONT, rate == null ? 0 : rate.getValue()),
                session.hashCode() + ":" + activeCameraId);
    }

    /** Привязка к произвольной камере; key отличает одну камеру от другой (после переключения — новый key). */
    public void bindTarget(Target target, Object key) {
        if (target == null || key == null) {
            return;
        }
        if (this.session != null && key.equals(bindKey)) {
            return;
        }
        boolean animate = getVisibility() == View.VISIBLE && isLaidOut() && (switchingCamera || this.session != null);
        switchingCamera = false;
        if (animate) {
            prepareZoomConfigurationTransition();
        }
        resetBinding(!animate);
        this.session = target;
        bindKey = key;
        cameraId = target.cameraId();
        front = target.isFront();
        frameRate = target.frameRate();
        animateNextConfiguration = animate;
        if (animate) {
            setEnabled(false);
            setExpanded(false, true);
        }
        tryBind();
    }

    public void beginCameraSwitch() {
        if (session != null && getVisibility() == View.VISIBLE && isLaidOut()) {
            switchingCamera = true;
            prepareZoomConfigurationTransition();
            setEnabled(false);
            setExpanded(false, true);
        }
    }

    public void unbindSession() {
        switchingCamera = false;
        resetBinding(true);
    }

    private void resetBinding(boolean hide) {
        setExternalZoomGestureActive(false);
        cancelZoomAnimation();
        resetZoomThrottle();
        setZoom(getZoom());
        session = null;
        bindKey = null;
        cameraId = null;
        front = false;
        opticalZoomRatios = new float[0];
        defaultZoom = 1f;
        displayOneZoom = 1f;
        bindRetries = 0;
        removeCallbacks(bindRunnable);
        animateNextConfiguration = false;
        if (hide) {
            cancelZoomConfigurationTransition();
            setEnabled(true);
            setExpanded(false, false);
            hideImmediately();
        }
    }

    private void tryBind() {
        if (session == null) {
            return;
        }
        float[] range = session.zoomRange(getContext());
        if (range == null) {
            retryBinding();
            return;
        }
        float min = range[0];
        float max = range[1];
        if (!Float.isFinite(min) || !Float.isFinite(max) || min <= 0f || max <= min) {
            // зум не поддерживается — ползунок не показываем
            animateNextConfiguration = false;
            cancelZoomConfigurationTransition();
            setEnabled(true);
            hideImmediately();
            return;
        }
        defaultZoom = Utilities.clamp(1f, max, min);
        displayOneZoom = 1f;
        setDisplayNormalizationFactor(1f);
        opticalZoomRatios = cameraId == null ? new float[0] : CameraLensStops.opticalZoomRatios(getContext(), cameraId, min);
        float[] toggleStops = CameraLensStops.buildToggleStops(front, min, max, opticalZoomRatios);
        float[] rulerStops = CameraLensStops.buildRulerStops(min, max, toggleStops);
        float zoom = Utilities.clamp(defaultZoom, max, min);
        boolean animate = animateNextConfiguration;
        animateNextConfiguration = false;
        setZoomConfiguration(min, max, toggleStops, rulerStops, zoom, animate);
        if (!animate) {
            setExpanded(false, false);
        }
        session.setZoomRatio(zoom);
        showAnimated();
    }

    private void retryBinding() {
        if (session != null && bindRetries++ < 25) {
            postDelayed(bindRunnable, 100);
            return;
        }
        animateNextConfiguration = false;
        cancelZoomConfigurationTransition();
        setEnabled(true);
        hideImmediately();
    }

    public void beginPinchZoomGesture() {
        if (session == null) {
            return;
        }
        discardPendingZoom();
        setZoom(getZoom());
        setExternalZoomGestureActive(true);
    }

    /** Зум щипком: ratio — уже итоговый коэффициент. */
    public void setPinchZoomRatio(float ratio) {
        if (session == null || !Float.isFinite(ratio)) {
            return;
        }
        float value = Utilities.clamp(ratio, getMaximumZoom(), getMinimumZoom());
        setZoom(value);
        session.setZoomRatio(value);
    }

    public void finishPinchZoomGesture() {
        setExternalZoomGestureActive(false);
        if (!DevGramConfig.isStaticZoom()) {
            animateToResetZoom();
        }
    }

    private void animateToResetZoom() {
        if (session == null) {
            return;
        }
        cancelZoomAnimation();
        float zoom = getZoom();
        float target = Utilities.clamp(defaultZoom, getMaximumZoom(), getMinimumZoom());
        // на широкоугольный (меньше 1×) не сбрасываем — его выбрали сознательно
        boolean keepWide = zoom < displayOneZoom && target >= displayOneZoom && getMinimumZoom() < displayOneZoom;
        if (keepWide || Math.abs(zoom - target) <= 0.001f) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(zoom, target);
        resetZoomAnimator = animator;
        animator.addUpdateListener(a -> setPinchZoomRatio((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (resetZoomAnimator == a) {
                    resetZoomAnimator = null;
                }
            }
        });
        animator.setDuration(350);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    /** Шаг зума кнопками громкости. */
    public void stepZoom(boolean in) {
        if (session == null || stepZoomAnimator != null && stepZoomAnimator.isRunning()) {
            return;
        }
        cancelZoomAnimation();
        discardPendingZoom();
        setZoom(getZoom());
        setExpanded(true, true);
        float zoom = getZoom();
        float min = getMinimumZoom();
        float max = getMaximumZoom();
        float one = Utilities.clamp(displayOneZoom, max, min);
        double span = max / one;
        float step = (float) Math.pow(span, 1.0 / Math.max(1, (int) Math.round(Math.log(span) / Math.log(1.75))));
        float target;
        if (in) {
            target = zoom >= one ? zoom * step : one;
        } else {
            target = zoom > one ? Math.max(one, zoom / step) : min;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(zoom, Utilities.clamp(target, max, min));
        stepZoomAnimator = animator;
        animator.setDuration(175);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.addUpdateListener(a -> setPinchZoomRatio((float) a.getAnimatedValue()));
        animator.start();
    }

    public void cancelZoomAnimation() {
        ValueAnimator animator = resetZoomAnimator;
        if (animator != null) {
            resetZoomAnimator = null;
            animator.cancel();
        }
        animator = stepZoomAnimator;
        if (animator != null) {
            stepZoomAnimator = null;
            animator.cancel();
        }
    }

    public boolean isBound() {
        return session != null;
    }

    // Изменения от ползунка шлём в камеру не чаще одного раза за кадр.
    private void applyZoom(float zoom) {
        pendingZoom = zoom;
        scheduleZoomFlush();
    }

    private void scheduleZoomFlush() {
        if (zoomFlushScheduled) {
            return;
        }
        zoomFlushScheduled = true;
        postOnAnimation(zoomFlushRunnable);
    }

    private void flushPendingZoom() {
        zoomFlushScheduled = false;
        float zoom = pendingZoom;
        if (Float.isNaN(zoom) || session == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!Float.isNaN(lastAppliedZoom)) {
            if (zoom == lastAppliedZoom) {
                return;
            }
            if (now - lastZoomAppliedAt < Math.max(1, 1000 / (frameRate > 0 ? frameRate : 30))) {
                scheduleZoomFlush();
                return;
            }
        }
        lastAppliedZoom = zoom;
        lastZoomAppliedAt = now;
        session.setZoomRatio(zoom);
    }

    private void discardPendingZoom() {
        removeCallbacks(zoomFlushRunnable);
        zoomFlushScheduled = false;
        pendingZoom = Float.NaN;
    }

    private void resetZoomThrottle() {
        discardPendingZoom();
        lastAppliedZoom = Float.NaN;
        lastZoomAppliedAt = 0;
    }

    public void setTextureViewSize(int size) {
        if (textureViewSize != size) {
            textureViewSize = size;
            applyPosition();
        }
    }

    public void setBaseTranslationY(float value) {
        if (baseTranslationY != value) {
            baseTranslationY = value;
            applyPosition();
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || getAlpha() < 0.5f) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyPosition();
    }

    // под кружком, с отступом 80dp от его центра
    private void applyPosition() {
        setTranslationY(baseTranslationY + textureViewSize / 2f + AndroidUtilities.dp(80) - getMeasuredHeight() / 2f);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (session != null && (getVisibility() != View.VISIBLE || !isEnabled() || animateNextConfiguration)) {
            bindRetries = 0;
            tryBind();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(bindRunnable);
        discardPendingZoom();
        if (appearAnimator != null) {
            cancelAppearAnimation();
            setAppearProgress(getVisibility() == View.VISIBLE ? 1f : 0f);
        }
    }
}

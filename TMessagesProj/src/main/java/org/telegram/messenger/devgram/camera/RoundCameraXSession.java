package org.telegram.messenger.devgram.camera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Looper;
import android.util.Range;
import android.util.Size;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ConcurrentCamera;
import androidx.camera.core.DisplayOrientedMeteringPointFactory;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MirrorMode;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.common.util.concurrent.ListenableFuture;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Stories.recorder.DualCameraView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CameraX-сессия для записи кружков: одна камера или пара фронт+тыл одновременно (если устройство
 * умеет), подбор размера превью под круглое видео, 60 к/с, стабилизация, зум, фокус, фонарик.
 */
public class RoundCameraXSession {

    private static final Map<CameraSelector, Boolean> STABILIZATION_SUPPORT_CACHE = new ConcurrentHashMap<>();
    private static final Range<Integer> FPS_60_RANGE = new Range<>(60, 60);
    private static final Range<Integer> FPS_30_RANGE = new Range<>(30, 30);
    private static final Size FALLBACK_SENSOR_ASPECT = new Size(4, 3);
    private static volatile Boolean seamlessSwitchingAvailableCache;

    public static class CameraLifecycle implements LifecycleOwner {
        private final LifecycleRegistry lifecycleRegistry;

        public CameraLifecycle() {
            lifecycleRegistry = new LifecycleRegistry(this);
            lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        }

        public void start() {
            try {
                if (lifecycleRegistry.getCurrentState() != Lifecycle.State.DESTROYED) {
                    lifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public void stop() {
            try {
                lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return lifecycleRegistry;
        }
    }

    private final CameraLifecycle lifecycle;
    private final Preview.SurfaceProvider surfaceProviderPrimary;
    private Preview.SurfaceProvider surfaceProviderSecondary;
    private final boolean extendedFrameRate;
    private final boolean stabilization;
    private final int roundResolution;

    private ProcessCameraProvider provider;
    private CameraSelector cameraSelector;
    private Camera camera, cameraFront, cameraBack;
    private CameraControl cameraControl, cameraControlFront, cameraControlBack;
    private Preview previewUseCase, previewUseCaseBack;
    private boolean isFrontface;
    private boolean isInitiated;
    private boolean isDualMode;
    private boolean isBinding;
    private volatile int recordingFrameRate = 30;
    private boolean isTorchOn;

    /**
     * @param extendedFrameRate пробовать 60 к/с
     * @param roundResolution   сторона итогового кружка — превью подбирается не меньше неё
     */
    public RoundCameraXSession(CameraLifecycle lifecycle, Preview.SurfaceProvider surfaceProvider,
                               boolean extendedFrameRate, boolean stabilization, int roundResolution) {
        this.lifecycle = lifecycle;
        this.surfaceProviderPrimary = surfaceProvider;
        this.extendedFrameRate = extendedFrameRate;
        this.stabilization = stabilization;
        this.roundResolution = roundResolution;
    }

    public void setSecondSurfaceProvider(Preview.SurfaceProvider surfaceProvider) {
        surfaceProviderSecondary = surfaceProvider;
        if (isInitiated && isDualMode && previewUseCaseBack != null) {
            previewUseCaseBack.setSurfaceProvider(surfaceProvider);
        }
    }

    public boolean isReady() {
        return isInitiated && !isBinding && camera != null;
    }

    public boolean isDualMode() {
        return isDualMode;
    }

    public boolean isActiveCameraFrontface() {
        Camera c = camera;
        if (c == null) return isFrontface;
        int facing = c.getCameraInfo().getLensFacing();
        if (facing == CameraSelector.LENS_FACING_FRONT) return true;
        if (facing == CameraSelector.LENS_FACING_BACK) return false;
        return isFrontface;
    }

    public int getRecordingFrameRate() {
        return recordingFrameRate;
    }

    public void initCamera(Context context, boolean front, boolean dual, Runnable onBound) {
        isFrontface = front;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            try {
                provider = future.get();
                if (lifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
                    return;
                }
                isDualMode = dual && supportsConcurrentFrontBackPair();
                rebindCamera();
                lifecycle.start();
                if (onBound != null) {
                    onBound.run();
                }
            } catch (Exception e) {
                FileLog.e(e);
                isInitiated = false;
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private boolean supportsConcurrentFrontBackPair() {
        if (provider == null) return false;
        for (List<CameraInfo> infos : provider.getAvailableConcurrentCameraInfos()) {
            boolean front = false, back = false;
            for (CameraInfo info : infos) {
                if (info.getLensFacing() == CameraSelector.LENS_FACING_FRONT) front = true;
                if (info.getLensFacing() == CameraSelector.LENS_FACING_BACK) back = true;
            }
            if (front && back) return true;
        }
        return false;
    }

    public void switchCamera() {
        isFrontface = !isFrontface;
        if (isDualMode && cameraFront != null && cameraBack != null) {
            updateActiveControl(isFrontface);
            updateTorchState();
        } else {
            rebindCamera();
        }
    }

    public void resetInactiveCameraZoom() {
        if (!isDualMode) return;
        if (isFrontface) {
            applyInitialZoom(cameraBack, cameraControlBack);
        } else {
            applyInitialZoom(cameraFront, cameraControlFront);
        }
    }

    private void updateActiveControl(boolean front) {
        camera = front ? cameraFront : cameraBack;
        cameraControl = front ? cameraControlFront : cameraControlBack;
    }

    public void closeCamera() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(this::closeCamera);
            return;
        }
        try {
            if (provider != null) {
                provider.unbindAll();
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (previewUseCase != null) previewUseCase.setSurfaceProvider(null);
                if (previewUseCaseBack != null) previewUseCaseBack.setSurfaceProvider(null);
            } catch (Exception e) {
                FileLog.e(e);
            }
            lifecycle.stop();
            isInitiated = false;
            clearCameraReferences();
        }
    }

    private void clearCameraReferences() {
        cameraBack = cameraFront = camera = null;
        cameraControlBack = cameraControlFront = cameraControl = null;
        previewUseCaseBack = previewUseCase = null;
    }

    public boolean isFlashAvailable() {
        Camera c = camera;
        return c != null && c.getCameraInfo().hasFlashUnit();
    }

    public void setTorchEnabled(boolean enabled) {
        isTorchOn = enabled;
        updateTorchState();
    }

    private void updateTorchState() {
        try {
            if (isDualMode) {
                if (cameraControlFront != null) cameraControlFront.enableTorch(false);
                if (cameraControlBack == null || cameraBack == null || !cameraBack.getCameraInfo().hasFlashUnit()) return;
                cameraControlBack.enableTorch(isTorchOn && !isFrontface);
                return;
            }
            if (camera == null || cameraControl == null || !camera.getCameraInfo().hasFlashUnit()) return;
            boolean back = camera.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_BACK;
            cameraControl.enableTorch(isTorchOn && back);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private Preview buildPreview(CameraSelector selector, boolean stabilized, Range<Integer> frameRate, boolean prefer60) {
        Size sensorAspect = getSensorAspect(selector);
        Set<Size> fps60Sizes = prefer60 ? get60FpsCapableSizes(selector) : Collections.emptySet();
        Preview.Builder builder = new Preview.Builder().setResolutionSelector(new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionFilter((list, rotation) -> sortRoundPreviewSizes(list, sensorAspect, fps60Sizes, roundResolution))
                .setAllowedResolutionMode(ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
                .build());
        if (frameRate != null) {
            builder.setTargetFrameRate(frameRate);
        }
        builder.setPreviewStabilizationEnabled(!isDualMode && stabilized);
        // зеркалит сам GL-конвейер кружков, как и с Camera2
        builder.setMirrorMode(MirrorMode.MIRROR_MODE_OFF);
        return builder.build();
    }

    private boolean isPreviewStabilizationSupported(CameraSelector selector) {
        if (selector == null || provider == null) return false;
        try {
            if (!provider.hasCamera(selector)) return false;
            Boolean cached = STABILIZATION_SUPPORT_CACHE.get(selector);
            if (cached != null) return cached;
            boolean supported = Preview.getPreviewCapabilities(provider.getCameraInfo(selector)).isStabilizationSupported();
            STABILIZATION_SUPPORT_CACHE.put(selector, supported);
            return supported;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private Camera tryBindSingleAtFrameRate(Range<Integer> range, boolean stabilized, boolean prefer60) {
        try {
            Set<Range<Integer>> supported = provider.getCameraInfo(cameraSelector).getSupportedFrameRateRanges();
            if (!supported.contains(range)) return null;
            return bindSingle(range, stabilized, prefer60);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private Camera tryBindSingleAtExtendedFrameRate(boolean stabilized) {
        try {
            Range<Integer> range = selectExtendedFpsRange(provider.getCameraInfo(cameraSelector).getSupportedFrameRateRanges());
            if (range == null) return null;
            return bindSingle(range, stabilized, true);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private Camera bindSingle(Range<Integer> range, boolean stabilized, boolean prefer60) {
        try {
            provider.unbindAll();
            previewUseCase = buildPreview(cameraSelector, stabilized, range, prefer60);
            previewUseCase.setSurfaceProvider(surfaceProviderPrimary);
            Camera bound = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase);
            if (bound != null && range != null) {
                recordingFrameRate = range.getUpper();
            }
            return bound;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Range<Integer> selectExtendedFpsRange(Collection<Range<Integer>> ranges) {
        if (ranges == null) return null;
        Range<Integer> best = null;
        for (Range<Integer> range : ranges) {
            if (range == null || !range.getUpper().equals(FPS_60_RANGE.getUpper())) continue;
            if (FPS_60_RANGE.equals(range)) return range;
            if (best == null || range.getLower() > best.getLower()) best = range;
        }
        return best;
    }

    private void rebindCamera() {
        if (provider == null || lifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED || isBinding) {
            return;
        }
        isBinding = true;
        recordingFrameRate = 30;
        clearCameraReferences();
        try {
            provider.unbindAll();
            if (isDualMode) {
                bindDualUseCases();
            } else {
                bindSingleUseCases();
            }
            isInitiated = camera != null;
        } catch (Exception e) {
            FileLog.e(e);
            isInitiated = false;
            try {
                provider.unbindAll();
            } catch (Exception ignore) {
            }
            clearCameraReferences();
        } finally {
            isBinding = false;
        }
    }

    private void bindSingleUseCases() {
        try {
            cameraSelector = isFrontface ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
            if (!provider.hasCamera(cameraSelector)) {
                isInitiated = false;
                return;
            }
            boolean stabilized = stabilization && isPreviewStabilizationSupported(cameraSelector);
            if (extendedFrameRate) {
                camera = tryBindSingleAtExtendedFrameRate(stabilized);
                if (camera == null && stabilized) {
                    camera = tryBindSingleAtExtendedFrameRate(false);
                }
            }
            if (camera == null) {
                camera = tryBindSingleAtFrameRate(FPS_30_RANGE, stabilized, false);
            }
            if (camera == null) {
                camera = bindSingle(null, stabilized, false);
            }
            if (camera == null && stabilized) {
                camera = bindSingle(null, false, false);
            }
            if (camera == null) {
                isInitiated = false;
                return;
            }
            cameraControl = camera.getCameraControl();
            observeCameraState(camera);
            applyInitialZoom(camera, cameraControl);
            updateTorchState();
        } catch (Exception e) {
            FileLog.e(e);
            isInitiated = false;
        }
    }

    private void bindDualUseCases() {
        Range<Integer> range = extendedFrameRate ? FPS_60_RANGE : FPS_30_RANGE;
        ConcurrentCamera concurrent = tryBindConcurrentCameras(range);
        if (concurrent == null && extendedFrameRate) {
            range = FPS_30_RANGE;
            concurrent = tryBindConcurrentCameras(range);
        }
        if (concurrent == null) {
            range = null;
            concurrent = tryBindConcurrentCameras(null);
        }
        if (concurrent == null) {
            isDualMode = false;
            bindSingleUseCases();
            return;
        }
        if (range != null) {
            recordingFrameRate = range.getLower();
        }
        for (Camera c : concurrent.getCameras()) {
            if (c.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_FRONT) {
                cameraFront = c;
                cameraControlFront = c.getCameraControl();
                applyInitialZoom(cameraFront, cameraControlFront);
            } else {
                cameraBack = c;
                cameraControlBack = c.getCameraControl();
                applyInitialZoom(cameraBack, cameraControlBack);
            }
            observeCameraState(c);
        }
        updateActiveControl(isFrontface);
        updateTorchState();
    }

    private ConcurrentCamera tryBindConcurrentCameras(Range<Integer> range) {
        try {
            boolean is60 = FPS_60_RANGE.equals(range);
            CameraSelector front = CameraSelector.DEFAULT_FRONT_CAMERA;
            previewUseCase = buildPreview(front, false, range, is60);
            previewUseCase.setSurfaceProvider(surfaceProviderPrimary);
            CameraSelector back = CameraSelector.DEFAULT_BACK_CAMERA;
            previewUseCaseBack = buildPreview(back, false, range, is60);
            if (surfaceProviderSecondary != null) {
                previewUseCaseBack.setSurfaceProvider(surfaceProviderSecondary);
            }
            ArrayList<ConcurrentCamera.SingleCameraConfig> configs = new ArrayList<>(2);
            configs.add(new ConcurrentCamera.SingleCameraConfig(front, new UseCaseGroup.Builder().addUseCase(previewUseCase).build(), lifecycle));
            configs.add(new ConcurrentCamera.SingleCameraConfig(back, new UseCaseGroup.Builder().addUseCase(previewUseCaseBack).build(), lifecycle));
            return provider.bindToLifecycle(configs);
        } catch (Exception e) {
            FileLog.e(e);
            try {
                provider.unbindAll();
            } catch (Exception e2) {
                FileLog.e(e2);
            }
            return null;
        }
    }

    private void observeCameraState(Camera c) {
        try {
            c.getCameraInfo().getCameraState().observe(lifecycle, state -> {
                CameraState.StateError error = state.getError();
                if (error != null) {
                    FileLog.e("CameraX camera state error: code=" + error.getCode() + " type=" + state.getType());
                    if (error.getCause() != null) {
                        FileLog.e(error.getCause());
                    }
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void applyInitialZoom(Camera c, CameraControl control) {
        if (control == null || c == null) return;
        control.setZoomRatio(1f);
        if (!wantsWideAngleStart(c)) return;
        LiveData<ZoomState> zoomState = c.getCameraInfo().getZoomState();
        if (zoomState.getValue() != null) {
            applyWideAngle(control, zoomState.getValue());
        } else {
            zoomState.observe(lifecycle, new Observer<ZoomState>() {
                @Override
                public void onChanged(ZoomState state) {
                    if (state == null) return;
                    zoomState.removeObserver(this);
                    if (wantsWideAngleStart(c)) {
                        applyWideAngle(control, state);
                    }
                }
            });
        }
    }

    private void applyWideAngle(CameraControl control, ZoomState state) {
        if (state == null || state.getMinZoomRatio() >= 1f) return;
        control.setZoomRatio(state.getMinZoomRatio());
    }

    private boolean wantsWideAngleStart(Camera c) {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean("dg_startWithWideAngle", false)
                && c != null && c.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_BACK;
    }

    public void setZoomRatio(float ratio) {
        if (cameraControl != null) cameraControl.setZoomRatio(ratio);
    }

    public float getMinZoomRatio() {
        ZoomState state = camera == null ? null : camera.getCameraInfo().getZoomState().getValue();
        return state == null ? 1f : state.getMinZoomRatio();
    }

    public float getMaxZoomRatio() {
        ZoomState state = camera == null ? null : camera.getCameraInfo().getZoomState().getValue();
        return state == null ? 1f : state.getMaxZoomRatio();
    }

    public String getActiveCameraId() {
        if (camera == null) return null;
        try {
            return Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public void focusToPoint(float x, float y, float width, float height) {
        Display display;
        if (cameraControl == null || camera == null || width <= 0 || height <= 0 || (display = getDefaultDisplay()) == null) {
            return;
        }
        try {
            cameraControl.startFocusAndMetering(new FocusMeteringAction.Builder(
                    new DisplayOrientedMeteringPointFactory(display, camera.getCameraInfo(), width, height).createPoint(x, y),
                    FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE).build());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private Size getSensorAspect(CameraSelector selector) {
        try {
            if (provider != null && selector != null && provider.hasCamera(selector)) {
                Rect rect = Camera2CameraInfo.from(provider.getCameraInfo(selector)).getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if (rect != null && rect.width() > 0 && rect.height() > 0) {
                    return new Size(rect.width(), rect.height());
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return FALLBACK_SENSOR_ASPECT;
    }

    private Set<Size> get60FpsCapableSizes(CameraSelector selector) {
        HashSet<Size> sizes = new HashSet<>();
        try {
            if (provider != null && selector != null && provider.hasCamera(selector)) {
                StreamConfigurationMap map = Camera2CameraInfo.from(provider.getCameraInfo(selector)).getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] outputs = map != null ? map.getOutputSizes(SurfaceTexture.class) : null;
                if (outputs != null) {
                    for (Size size : outputs) {
                        long minFrameDuration = map.getOutputMinFrameDuration(SurfaceTexture.class, size);
                        if (minFrameDuration > 0 && minFrameDuration <= 16_666_667L) {
                            sizes.add(size);
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return sizes;
    }

    // Размеры превью по пригодности для кружка: не меньше кружка, не больше FullHD, умеет 60 к/с,
    // затем ближе к соотношению сенсора и к целевой стороне.
    private static List<Size> sortRoundPreviewSizes(List<Size> list, Size sensorAspect, Set<Size> fps60Sizes, int roundResolution) {
        ArrayList<Size> sorted = new ArrayList<>(list);
        int target = Math.min(1440, Math.max(1024, roundResolution * 2));
        double aspect = aspectRatioOf(sensorAspect);
        boolean prefer60 = !fps60Sizes.isEmpty();
        sorted.sort(Comparator.<Size>comparingInt(s -> previewTier(s, roundResolution, prefer60, fps60Sizes))
                .thenComparingDouble(s -> previewScore(s, aspect, target))
                .thenComparingLong(s -> (long) s.getWidth() * s.getHeight()));
        return sorted;
    }

    private static int previewTier(Size size, int roundResolution, boolean prefer60, Set<Size> fps60Sizes) {
        if (Math.min(size.getWidth(), size.getHeight()) < roundResolution) return 3;
        if (Math.max(size.getWidth(), size.getHeight()) > 1920 || (long) size.getWidth() * size.getHeight() > 2_100_000L) return 2;
        return !prefer60 || fps60Sizes.contains(size) ? 0 : 1;
    }

    private static double previewScore(Size size, double aspect, int target) {
        int min = Math.min(size.getWidth(), size.getHeight());
        int max = Math.max(size.getWidth(), size.getHeight());
        if (min <= 0 || target <= 0) return Double.MAX_VALUE;
        return Math.max(0, (double) max / min - aspect) / aspect * 2.0
                + (double) Math.abs(min - target) / target
                + (1.0 - (double) min / max) * 0.5;
    }

    private static double aspectRatioOf(Size size) {
        int min = Math.min(size.getWidth(), size.getHeight());
        int max = Math.max(size.getWidth(), size.getHeight());
        return min > 0 ? (double) max / min : 4.0 / 3.0;
    }

    private static Display getDefaultDisplay() {
        DisplayManager dm = (DisplayManager) ApplicationLoader.applicationContext.getSystemService(Context.DISPLAY_SERVICE);
        return dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
    }

    public static boolean isRoundDualAvailable(Context context) {
        return DualCameraView.roundDualAvailableStatic(context) && isSeamlessSwitchingAvailable(context);
    }

    public static boolean isSeamlessSwitchingAvailable(Context context) {
        if (seamlessSwitchingAvailableCache == null) {
            seamlessSwitchingAvailableCache = SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE
                    && SharedConfig.allowPreparingHevcPlayers() && hasConcurrentFrontBackPair(context);
        }
        return seamlessSwitchingAvailableCache;
    }

    private static boolean hasConcurrentFrontBackPair(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 30) return false;
        PackageManager pm = context.getPackageManager();
        if (pm == null || !pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)) return false;
        CameraManager cm = context.getSystemService(CameraManager.class);
        if (cm == null) return false;
        try {
            for (Set<String> ids : cm.getConcurrentCameraIds()) {
                boolean front = false, back = false;
                for (String id : ids) {
                    Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                    if (facing == null) continue;
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) front = true;
                    else if (facing == CameraCharacteristics.LENS_FACING_BACK) back = true;
                }
                if (front && back) return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }
}

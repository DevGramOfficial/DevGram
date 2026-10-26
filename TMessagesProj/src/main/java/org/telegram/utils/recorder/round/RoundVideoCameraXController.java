package org.telegram.utils.recorder.round;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.camera.core.SurfaceRequest;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.devgram.camera.RoundCameraXSession;

import java.io.IOException;

/** Источник кадров для кружков на CameraX: тот же GL-конвейер и кодек, что и у Camera2. */
final class RoundVideoCameraXController extends RoundVideoCamera {

    private final Context context;
    private final CameraManager cameraManager;
    private final TextureView previewView;
    private final int outputSize;
    private final int videoBitrate;
    private final boolean dualCameraEnabled;
    private final boolean extendedFrameRate;
    private final RoundVideoSession.CameraResolution cameraResolution;
    private final boolean compositionEnabled;
    private final RoundVideoDiagnostics diagnostics;
    private final RoundVideoCameraController.Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private HandlerThread workerThread;
    private Handler workerHandler;
    private RoundCameraXSession cameraXSession;
    private boolean opening;
    private boolean pipelineReady;
    private boolean switchingCamera;
    private SurfaceRequest pendingPrimaryRequest;
    private SurfaceRequest pendingSecondaryRequest;
    private RoundVideoCodecRecorder commonStartRecorder;
    private long segmentStartedNs;

    private volatile boolean active;
    private volatile boolean stopping;
    private volatile boolean cameraConfigured;
    private volatile boolean awaitingSwitchPreviewFrame;
    private volatile boolean pipelineDual;
    private volatile RoundVideoSession.CameraFacing activeFacing;
    private volatile RoundVideoSession.CameraFacing requestedFacing;
    private volatile RoundVideoSession.FrameRate activeFrameRate = RoundVideoSession.FrameRate.FPS_30;
    private volatile RoundVideoGlProcessor glProcessor;
    private volatile RoundVideoCodecRecorder recorder;
    private volatile RoundVideoMp4Writer writer;
    private volatile Surface previewSurface;
    private volatile Size primaryStreamSize;
    private volatile Size secondaryStreamSize;
    private volatile long timelineOffsetUs;

    private final Runnable commonStartTimeoutRunnable = () -> {
        RoundVideoCodecRecorder expected = commonStartRecorder;
        if (!active || stopping || expected == null || recorder != expected) return;
        commonStartRecorder = null;
        reportError(new IllegalStateException("Timed out waiting for synchronized audio and video start"));
    };

    private final TextureView.SurfaceTextureListener previewListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            openCameraIfReady();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            if (active) {
                reportError(new IllegalStateException("Preview SurfaceTexture was destroyed"));
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            if (awaitingSwitchPreviewFrame) {
                awaitingSwitchPreviewFrame = false;
                callback.onCameraSwitchFirstFrame();
            }
        }
    };

    RoundVideoCameraXController(
            @NonNull Context context,
            @NonNull TextureView previewView,
            int outputSize,
            int videoBitrate,
            boolean dualCameraEnabled,
            @NonNull RoundVideoSession.CameraResolution cameraResolution,
            @NonNull RoundVideoSession.FrameRate frameRate,
            boolean compositionEnabled,
            @NonNull RoundVideoDiagnostics diagnostics,
            @NonNull RoundVideoCameraController.Callback callback
    ) {
        this.context = context.getApplicationContext();
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.previewView = previewView;
        this.outputSize = outputSize;
        this.videoBitrate = videoBitrate;
        this.dualCameraEnabled = dualCameraEnabled;
        this.cameraResolution = cameraResolution;
        this.extendedFrameRate = frameRate == RoundVideoSession.FrameRate.FPS_60;
        this.compositionEnabled = compositionEnabled;
        this.diagnostics = diagnostics;
        this.callback = callback;
    }

    @Override
    void startSegment(@NonNull RoundVideoMp4Writer writer, long timelineOffsetUs, @NonNull RoundVideoSession.CameraFacing facing) {
        ensureThread();
        this.writer = writer;
        this.timelineOffsetUs = timelineOffsetUs;
        requestedFacing = facing;
        active = true;
        stopping = false;
        cameraConfigured = false;
        pipelineReady = false;
        switchingCamera = false;
        commonStartRecorder = null;
        segmentStartedNs = SystemClock.elapsedRealtimeNanos();
        diagnostics.log("CameraX segment start: facing=" + facing + ", timelineOffsetUs=" + timelineOffsetUs
                + ", textureAvailable=" + previewView.isAvailable());
        previewView.setSurfaceTextureListener(previewListener);
        openCameraIfReady();
    }

    @Override
    boolean setCameraFacing(@NonNull RoundVideoSession.CameraFacing facing) {
        requestedFacing = facing;
        RoundCameraXSession session = cameraXSession;
        if (!active || session == null || !session.isReady() || switchingCamera || facing == activeFacing) {
            return false;
        }
        RoundVideoGlProcessor processor = glProcessor;
        if (pipelineDual && session.isDualMode() && processor != null) {
            // обе камеры уже снимают — просто переключаем вход GL без перепривязки
            callback.onCameraSwitchStarted(facing);
            session.switchCamera();
            session.resetInactiveCameraZoom();
            updateActiveCamera(session);
            RoundVideoSession.CameraFacing newFacing = activeFacing;
            processor.setSecondaryInputActive(newFacing == RoundVideoSession.CameraFacing.BACK, () -> mainHandler.post(() -> {
                if (active && activeFacing == newFacing) {
                    callback.onCameraSwitchFirstFrame();
                }
            }));
            diagnostics.log("CameraX dual switch: to=" + newFacing);
            notifyConfigured(session, true);
            return true;
        }
        switchingCamera = true;
        callback.onCameraSwitchStarted(facing);
        if (processor != null && activeFacing != null) {
            processor.beginCameraSwitch(activeFacing, facing);
        }
        diagnostics.log("CameraX switch started: from=" + activeFacing + ", to=" + facing);
        session.switchCamera();
        if (!session.isReady()) {
            reportError(new IllegalStateException("CameraX rebind failed after camera switch"));
        }
        return true;
    }

    @Override
    boolean setZoom(float progress) {
        RoundCameraXSession session = cameraXSession;
        if (!active || session == null) return false;
        if (progress <= 0f) return true;
        float max = Math.max(0f, Math.min(4f, session.getMaxZoomRatio()) - 1f);
        session.setZoomRatio(1f + Math.min(1f, progress) * max);
        return true;
    }

    @Override
    boolean setZoomRatio(float ratio) {
        RoundCameraXSession session = cameraXSession;
        if (!active || session == null || !Float.isFinite(ratio) || ratio <= 0f) return false;
        session.setZoomRatio(Math.max(session.getMinZoomRatio(), Math.min(session.getMaxZoomRatio(), ratio)));
        return true;
    }

    @Override
    boolean setFocusPoint(float x, float y) {
        RoundCameraXSession session = cameraXSession;
        Size size = pipelineDual && activeFacing == RoundVideoSession.CameraFacing.BACK ? secondaryStreamSize : primaryStreamSize;
        if (!active || session == null || !session.isReady() || size == null) return false;
        // превью — центральный квадрат кадра, переводим точку в координаты всего кадра
        boolean rotated = isStreamRotated(session.getActiveCameraId());
        float width = rotated ? size.getHeight() : size.getWidth();
        float height = rotated ? size.getWidth() : size.getHeight();
        float side = Math.min(width, height);
        float fx = (Math.max(0f, Math.min(1f, x)) - .5f) * side / width + .5f;
        float fy = (Math.max(0f, Math.min(1f, y)) - .5f) * side / height + .5f;
        session.focusToPoint(fx, fy, 1f, 1f);
        return true;
    }

    @Override
    boolean setTorchEnabled(boolean enabled) {
        RoundCameraXSession session = cameraXSession;
        if (!active || session == null) return false;
        session.setTorchEnabled(enabled);
        return true;
    }

    @Override
    boolean stopSegment() {
        Handler handler = workerHandler;
        if (!active || stopping || handler == null) return false;
        stopping = true;
        diagnostics.log("CameraX segment stop requested");
        handler.post(() -> {
            cancelCommonStartWait();
            requestRecorderStop();
            mainHandler.post(() -> {
                active = false;
                closeCamera();
                handler.post(() -> {
                    releasePipeline();
                    stopping = false;
                    callback.onRecordingStopped();
                });
            });
        });
        return true;
    }

    @Override
    void release() {
        active = false;
        previewView.setSurfaceTextureListener(null);
        closeCamera();
        Handler handler = workerHandler;
        HandlerThread thread = workerThread;
        workerHandler = null;
        workerThread = null;
        if (handler == null || thread == null) return;
        handler.post(() -> {
            cancelCommonStartWait();
            requestRecorderStop();
            releasePipeline();
            thread.quitSafely();
        });
    }

    private void ensureThread() {
        if (workerThread != null) return;
        workerThread = new HandlerThread("RoundVideoCameraX");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    private void openCameraIfReady() {
        if (!active || opening || cameraXSession != null || !previewView.isAvailable()) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            reportError(new SecurityException("Camera permission is not granted"));
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            reportError(new SecurityException("Audio recording permission is not granted"));
            return;
        }
        SurfaceTexture texture = previewView.getSurfaceTexture();
        if (texture == null) return;
        int glPreview = Math.max(1, Math.min(720, Math.min(previewView.getWidth(), previewView.getHeight())));
        texture.setDefaultBufferSize(glPreview, glPreview);
        if (previewSurface == null) {
            previewSurface = new Surface(texture);
        }
        previewView.setTransform(new Matrix());
        opening = true;
        RoundCameraXSession session = new RoundCameraXSession(new RoundCameraXSession.CameraLifecycle(),
                request -> onSurfaceRequested(request, false), extendedFrameRate, true, outputSize);
        session.setSecondSurfaceProvider(request -> onSurfaceRequested(request, true));
        cameraXSession = session;
        boolean dual = dualCameraEnabled && RoundCameraXSession.isRoundDualAvailable(context);
        diagnostics.log("CameraX open requested: facing=" + requestedFacing + ", dual=" + dual + ", glPreview=" + glPreview);
        session.initCamera(context, requestedFacing == RoundVideoSession.CameraFacing.FRONT, dual, () -> onCameraBound(session));
    }

    private void onCameraBound(RoundCameraXSession session) {
        if (cameraXSession != session || !active) return;
        opening = false;
        if (!session.isReady()) {
            reportError(new IllegalStateException("Unable to bind CameraX camera"));
            return;
        }
        updateActiveCamera(session);
        diagnostics.log("CameraX bound: id=" + session.getActiveCameraId() + ", dual=" + session.isDualMode()
                + ", facing=" + activeFacing + ", fps=" + activeFrameRate.getValue()
                + ", segmentElapsedMs=" + elapsedMs(segmentStartedNs));
        if (recorder != null) {
            onPipelineReady();
            return;
        }
        Surface surface = previewSurface;
        RoundVideoSession.FrameRate frameRate = activeFrameRate;
        boolean dual = session.isDualMode();
        Handler handler = workerHandler;
        if (handler != null) {
            handler.post(() -> createPipeline(surface, frameRate, dual));
        }
    }

    private void createPipeline(Surface preview, RoundVideoSession.FrameRate frameRate, boolean dual) {
        if (!active || stopping) return;
        try {
            RoundVideoCodecRecorder codec = new RoundVideoCodecRecorder(writer, timelineOffsetUs, outputSize,
                    videoBitrate, frameRate.getValue(), diagnostics, this::reportError);
            recorder = codec;
            Surface encoderSurface = codec.prepare();
            RoundVideoGlProcessor processor = new RoundVideoGlProcessor(new Size(outputSize, outputSize), encoderSurface,
                    preview, dual, outputSize, outputSize, false, compositionEnabled, diagnostics, 0, codec, this::reportError);
            processor.setFirstFrameListener(() -> postToWorker(this::startRecordingAfterFirstFrame));
            glProcessor = processor;
            pipelineDual = dual;
            processor.start();
            mainHandler.post(this::onPipelineReady);
        } catch (IOException | RuntimeException e) {
            reportError(e);
        }
    }

    private void onPipelineReady() {
        if (!active) return;
        pipelineReady = true;
        servePendingRequests();
    }

    private void onSurfaceRequested(SurfaceRequest request, boolean secondary) {
        if (!active || cameraXSession == null) {
            request.willNotProvideSurface();
            return;
        }
        diagnostics.log("CameraX surface requested: input=" + (secondary ? 1 : 0) + ", size=" + request.getResolution()
                + ", segmentElapsedMs=" + elapsedMs(segmentStartedNs));
        SurfaceRequest previous = secondary ? pendingSecondaryRequest : pendingPrimaryRequest;
        if (previous != null && previous != request) {
            previous.willNotProvideSurface();
        }
        if (secondary) {
            pendingSecondaryRequest = request;
        } else {
            pendingPrimaryRequest = request;
        }
        servePendingRequests();
    }

    private void servePendingRequests() {
        if (!pipelineReady) return;
        SurfaceRequest primary = pendingPrimaryRequest;
        SurfaceRequest secondary = pendingSecondaryRequest;
        pendingPrimaryRequest = null;
        pendingSecondaryRequest = null;
        boolean dual = pipelineDual;
        if (!dual && secondary != null) {
            secondary.willNotProvideSurface();
            secondary = null;
        }
        if (primary == null && secondary == null) return;
        RoundCameraXSession session = cameraXSession;
        RoundVideoGlProcessor processor = glProcessor;
        Handler handler = workerHandler;
        if (session == null || processor == null || handler == null) {
            willNotProvideSurfaces(primary, secondary);
            return;
        }
        Size primarySize = primary != null ? primary.getResolution() : null;
        Size secondarySize = secondary != null ? secondary.getResolution() : secondaryStreamSize;
        boolean realtime = isTimestampRealtime(session.getActiveCameraId());
        boolean secondaryActive = dual && !session.isActiveCameraFrontface();
        SurfaceRequest fPrimary = primary, fSecondary = secondary;
        handler.post(() -> {
            if (primarySize != null) {
                try {
                    processor.updateInputConfiguration(primarySize, Math.min(primarySize.getWidth(), primarySize.getHeight()), realtime);
                } catch (RuntimeException e) {
                    mainHandler.post(() -> willNotProvideSurfaces(fPrimary, fSecondary));
                    reportError(e);
                    return;
                }
            }
            if (dual && secondarySize != null) {
                processor.configureSecondaryInput(secondarySize, Math.min(secondarySize.getWidth(), secondarySize.getHeight()), realtime);
            }
            if (dual) {
                processor.setSecondaryInputActive(secondaryActive);
            }
            mainHandler.post(() -> provideSurfaces(fPrimary, fSecondary, processor));
        });
    }

    private void provideSurfaces(SurfaceRequest primary, SurfaceRequest secondary, RoundVideoGlProcessor processor) {
        RoundCameraXSession session = cameraXSession;
        if (!active || session == null || glProcessor != processor) {
            willNotProvideSurfaces(primary, secondary);
            return;
        }
        if (primary != null) {
            primaryStreamSize = primary.getResolution();
            provideSurface(primary, processor.getCameraSurface(), false);
        }
        if (secondary != null) {
            secondaryStreamSize = secondary.getResolution();
            provideSurface(secondary, processor.getSecondaryCameraSurface(), true);
        }
        updateActiveCamera(session);
        boolean backInSecondary = pipelineDual && activeFacing == RoundVideoSession.CameraFacing.BACK;
        if (cameraConfigured && (backInSecondary ? secondary : primary) == null) return;
        if ((backInSecondary ? secondaryStreamSize : primaryStreamSize) == null) return;
        boolean wasSwitching = switchingCamera;
        switchingCamera = false;
        cameraConfigured = true;
        if (wasSwitching) {
            processor.onCameraSessionConfigured();
            awaitingSwitchPreviewFrame = true;
        }
        diagnostics.log("CameraX surfaces provided: primary=" + (primary == null ? "kept" : primaryStreamSize)
                + ", secondary=" + (secondary == null ? "kept" : secondaryStreamSize)
                + ", facing=" + activeFacing + ", switch=" + wasSwitching
                + ", segmentElapsedMs=" + elapsedMs(segmentStartedNs));
        notifyConfigured(session, wasSwitching);
        if (requestedFacing != null && requestedFacing != activeFacing) {
            mainHandler.post(() -> setCameraFacing(requestedFacing));
        }
    }

    private void provideSurface(SurfaceRequest request, Surface surface, boolean secondary) {
        request.provideSurface(surface, ContextCompat.getMainExecutor(context), result ->
                diagnostics.log("CameraX surface released: input=" + (secondary ? 1 : 0) + ", result=" + result.getResultCode()));
    }

    private static void willNotProvideSurfaces(SurfaceRequest primary, SurfaceRequest secondary) {
        if (primary != null) primary.willNotProvideSurface();
        if (secondary != null) secondary.willNotProvideSurface();
    }

    private void notifyConfigured(RoundCameraXSession session, boolean cameraSwitch) {
        Size size = pipelineDual && activeFacing == RoundVideoSession.CameraFacing.BACK ? secondaryStreamSize : primaryStreamSize;
        callback.onConfigured(new RoundVideoCameraController.CameraInfo(session.getActiveCameraId(), activeFacing,
                cameraResolution, activeFrameRate, size, size, session.getMaxZoomRatio(),
                activeFacing == RoundVideoSession.CameraFacing.BACK && session.isFlashAvailable()), cameraSwitch);
    }

    private void updateActiveCamera(RoundCameraXSession session) {
        activeFacing = session.isActiveCameraFrontface() ? RoundVideoSession.CameraFacing.FRONT : RoundVideoSession.CameraFacing.BACK;
        activeFrameRate = session.getRecordingFrameRate() >= 60 ? RoundVideoSession.FrameRate.FPS_60 : RoundVideoSession.FrameRate.FPS_30;
    }

    private void startRecordingAfterFirstFrame() {
        RoundVideoCodecRecorder codec = recorder;
        if (!active || stopping || !cameraConfigured || codec == null || codec.isStarted()) return;
        try {
            commonStartRecorder = codec;
            Handler handler = workerHandler;
            if (handler != null) {
                handler.removeCallbacks(commonStartTimeoutRunnable);
                handler.postDelayed(commonStartTimeoutRunnable, 3000);
            }
            // видео стартует, когда готов звук; о начале записи сообщаем, когда пошли оба потока
            codec.startRecording(
                    () -> postToWorker(() -> startVideoAfterAudioReady(codec)),
                    () -> postToWorker(() -> announceRecordingStarted(codec))
            );
            diagnostics.log("CameraX first frame received; audio startup requested: segmentElapsedMs=" + elapsedMs(segmentStartedNs));
        } catch (RuntimeException e) {
            cancelCommonStartWait();
            reportError(e);
        }
    }

    private void startVideoAfterAudioReady(RoundVideoCodecRecorder codec) {
        RoundVideoGlProcessor processor = glProcessor;
        if (!active || stopping || recorder != codec || processor == null) return;
        try {
            processor.startRecording(codec.getTimeOriginNs());
        } catch (RuntimeException e) {
            cancelCommonStartWait();
            reportError(e);
        }
    }

    private void announceRecordingStarted(RoundVideoCodecRecorder codec) {
        if (!active || stopping || recorder != codec) return;
        cancelCommonStartWait();
        diagnostics.log("CameraX common A/V start completed: segmentElapsedMs=" + elapsedMs(segmentStartedNs));
        callback.onRecordingStarted();
    }

    private void requestRecorderStop() {
        RoundVideoCodecRecorder codec = recorder;
        RoundVideoGlProcessor processor = glProcessor;
        if (codec == null) return;
        long stopPresentationTimeUs = codec.requestStop();
        if (processor != null && stopPresentationTimeUs != Long.MAX_VALUE) {
            processor.stopRecordingAt(stopPresentationTimeUs);
        }
    }

    private void releasePipeline() {
        RoundVideoGlProcessor processor = glProcessor;
        glProcessor = null;
        if (processor != null) processor.stop();
        Surface surface = previewSurface;
        previewSurface = null;
        if (surface != null) surface.release();
        RoundVideoCodecRecorder codec = recorder;
        recorder = null;
        if (codec != null) codec.stop();
        primaryStreamSize = null;
        secondaryStreamSize = null;
        pipelineDual = false;
    }

    private void closeCamera() {
        RoundCameraXSession session = cameraXSession;
        cameraXSession = null;
        opening = false;
        pipelineReady = false;
        switchingCamera = false;
        cameraConfigured = false;
        awaitingSwitchPreviewFrame = false;
        SurfaceRequest primary = pendingPrimaryRequest;
        SurfaceRequest secondary = pendingSecondaryRequest;
        pendingPrimaryRequest = null;
        pendingSecondaryRequest = null;
        willNotProvideSurfaces(primary, secondary);
        if (session != null) session.closeCamera();
    }

    private void cancelCommonStartWait() {
        Handler handler = workerHandler;
        if (handler != null) handler.removeCallbacks(commonStartTimeoutRunnable);
        commonStartRecorder = null;
    }

    private void postToWorker(Runnable runnable) {
        Handler handler = workerHandler;
        if (handler != null) handler.post(runnable);
    }

    private boolean isTimestampRealtime(String cameraId) {
        CameraCharacteristics characteristics = getCharacteristics(cameraId);
        Integer source = characteristics == null ? null : characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        return source != null && source == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;
    }

    private boolean isStreamRotated(String cameraId) {
        CameraCharacteristics characteristics = getCharacteristics(cameraId);
        Integer orientation = characteristics == null ? null : characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (orientation == null) return false;
        int displayRotation = previewView.getDisplay() == null ? 0 : previewView.getDisplay().getRotation() * 90;
        return (orientation - displayRotation + 360) % 180 != 0;
    }

    private CameraCharacteristics getCharacteristics(String cameraId) {
        if (cameraId == null || cameraManager == null) return null;
        try {
            return cameraManager.getCameraCharacteristics(cameraId);
        } catch (Exception e) {
            diagnostics.error("CameraX characteristics unavailable", e);
            return null;
        }
    }

    private void reportError(Exception error) {
        diagnostics.error("CameraX camera error", error);
        callback.onError(error);
    }

    private static long elapsedMs(long startedNs) {
        return (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L;
    }
}

package org.telegram.utils.recorder.round;

import androidx.annotation.NonNull;

/** Общий интерфейс источника кадров для кружков. */
abstract class RoundVideoCamera {

    abstract void startSegment(@NonNull RoundVideoMp4Writer writer, long timelineOffsetUs, @NonNull RoundVideoSession.CameraFacing facing);

    abstract boolean setCameraFacing(@NonNull RoundVideoSession.CameraFacing facing);

    abstract boolean setZoom(float progress);

    /** Абсолютный коэффициент зума (меньше 1 — широкоугольный объектив). */
    abstract boolean setZoomRatio(float ratio);

    /** Фокус и экспозиция по точке в долях превью. */
    abstract boolean setFocusPoint(float x, float y);

    abstract boolean setTorchEnabled(boolean enabled);

    abstract boolean stopSegment();

    abstract void release();
}

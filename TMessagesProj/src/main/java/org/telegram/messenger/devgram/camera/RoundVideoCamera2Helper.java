package org.telegram.messenger.devgram.camera;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Build;
import android.util.Range;
import android.util.Size;

import org.telegram.messenger.FileLog;

/** Фокус в точку и диапазон зума для Camera2-рекордера кружков. */
public final class RoundVideoCamera2Helper {

    private RoundVideoCamera2Helper() {
    }

    /**
     * Область фокуса/экспозиции под точкой (x, y) в долях круглого превью.
     * cropSize — сторона квадрата, вырезаемого из кадра, rotation — поворот дисплея в градусах.
     */
    public static MeteringRectangle createFocusRegion(CameraCharacteristics characteristics, Size size, int cropSize, int rotation, float zoomRatio, float x, float y) {
        Rect active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (active == null || cropSize <= 0 || size.getWidth() <= 0 || size.getHeight() <= 0) {
            return null;
        }
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean front = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
        int orientation = sensorOrientation == null ? 0 : sensorOrientation;
        int angle = (front ? orientation + rotation : orientation - rotation + 360) % 360;
        boolean swapped = angle == 90 || angle == 270;
        float viewWidth = swapped ? size.getHeight() : size.getWidth();
        float viewHeight = swapped ? size.getWidth() : size.getHeight();
        float crop = Math.min(cropSize, Math.min(viewWidth, viewHeight));

        float u = (clamp(x, 0f, 1f) - 0.5f) * crop / viewWidth + 0.5f;
        float v = (clamp(y, 0f, 1f) - 0.5f) * crop / viewHeight + 0.5f;
        if (front) {
            u = 1f - u;
        }
        // переводим координаты экрана в координаты сенсора
        float sensorX;
        float sensorY;
        if (angle == 90) {
            sensorX = v;
            sensorY = 1f - u;
        } else if (angle == 180) {
            sensorX = 1f - u;
            sensorY = 1f - v;
        } else if (angle == 270) {
            sensorX = 1f - v;
            sensorY = u;
        } else {
            sensorX = u;
            sensorY = v;
        }

        float left = 0f;
        float top = 0f;
        float width = active.width();
        float height = active.height();
        if (!usesZoomRatio(characteristics) && zoomRatio > 1f) {
            width /= zoomRatio;
            height /= zoomRatio;
            left = (active.width() - width) / 2f;
            top = (active.height() - height) / 2f;
        }
        float aspect = size.getWidth() / (float) size.getHeight();
        float frameWidth;
        float frameHeight;
        if (width / height > aspect) {
            frameHeight = height;
            frameWidth = aspect * height;
        } else {
            frameWidth = width;
            frameHeight = width / aspect;
        }
        int cx = Math.round(left + (width - frameWidth) / 2f + sensorX * frameWidth);
        int cy = Math.round(top + (height - frameHeight) / 2f + sensorY * frameHeight);
        int half = Math.max(1, Math.round(Math.min(frameWidth, frameHeight) / 6f / 2f));
        int maxX = active.width() - 1;
        int maxY = active.height() - 1;
        Rect rect = new Rect(clamp(cx - half, 0, maxX), clamp(cy - half, 0, maxY), clamp(cx + half, 0, maxX), clamp(cy + half, 0, maxY));
        if (rect.isEmpty()) {
            return null;
        }
        return new MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX - 1);
    }

    /** Ставит области AE/AF; false — если камера не умеет автофокус по области. */
    public static boolean applyFocusRegion(CaptureRequest.Builder builder, CameraCharacteristics characteristics, MeteringRectangle region) {
        MeteringRectangle[] regions = {region};
        Integer maxAe = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        if (maxAe != null && maxAe > 0) {
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, regions);
        }
        Integer maxAf = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        int[] afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (maxAf == null || maxAf <= 0 || !contains(afModes, CaptureRequest.CONTROL_AF_MODE_AUTO)) {
            return false;
        }
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_REGIONS, regions);
        return true;
    }

    /** [минимум, максимум] зума камеры; минимум меньше 1 — есть широкоугольный объектив. */
    public static float[] readZoomRange(Context context, String cameraId) {
        if (cameraId == null) {
            return null;
        }
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) {
                return null;
            }
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            if (usesZoomRatio(characteristics)) {
                Range<Float> range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                return new float[]{range.getLower(), range.getUpper()};
            }
            Float max = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            return new float[]{1f, max == null ? 1f : max};
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static boolean usesZoomRatio(CameraCharacteristics characteristics) {
        return Build.VERSION.SDK_INT >= 30 && characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) != null;
    }

    private static boolean contains(int[] values, int value) {
        if (values == null) {
            return false;
        }
        for (int v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

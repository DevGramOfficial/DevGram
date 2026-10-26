package org.telegram.ui.Pdf.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseLongArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;
import androidx.core.graphics.ColorUtils;
import org.telegram.ui.Pdf.PdfDocumentRenderer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.capture.IBlur3Hash;
@SuppressLint({"ViewConstructor"})
public class PdfPagesView extends View implements IBlur3Capture {
    private final SparseLongArray baseAppearTime;
    private final LinkedHashMap<Integer, Bitmap> bases;
    private final long basesBudget;
    private long basesBytes;
    private final Paint bitmapPaint;
    private int bottomInset;
    private float contentHeight;
    private int currentMatchIndex;
    private int currentMatchPage;
    private int currentPage;
    private final Delegate delegate;
    private boolean destroyed;
    private final SparseArray<Detail> details;
    private boolean draggingThumb;
    private final float[] failedDetailWidth;
    private final SparseBooleanArray failedPages;
    private int firstVisible;
    private float flingOffsetY;
    private final GestureDetector gestureDetector;
    private final Paint highlightPaint;
    private PdfDocumentRenderer.Job inFlight;
    private final AnimatedTextView.AnimatedTextDrawable indicatorText;
    private final Runnable invalidateRunnable;
    private long lastActivityTime;
    private int lastJobType;
    private int lastVisible;
    private final SparseArray<ArrayList<PdfDocumentRenderer.Link>> links;
    private final boolean[] measured;
    private int nextMeasurePage;
    private final AnimatedFloat overlayAlpha;
    private final Paint overlayPaint;
    private View overlayView;
    private float overlayVisibility;
    private final float[] pageAspect;
    private final int pageCount;
    private final int pageGap;
    private final int pagePadding;
    private final float[] pageTop;
    private int pageWidth;
    private final Paint placeholderPaint;
    private final RectF rect;
    private final RectF rect2;
    private final PdfDocumentRenderer renderer;
    private final Theme.ResourcesProvider resourcesProvider;
    private final RectF sampleRect;
    private final ScaleGestureDetector scaleDetector;
    private boolean scaledInGesture;
    private boolean scaling;
    private float scrollX;
    private float scrollY;
    private final OverScroller scroller;
    private int searchCrawled;
    private int searchGeneration;
    private final SparseArray<ArrayList<RectF[]>> searchMatches;
    private String searchQuery;
    private int searchStartPage;
    private int searchTotal;
    private int searchVersion;
    private final LoadingDrawable skeletonDrawable;
    private final Path skeletonPath;
    private final RectF skeletonRect;
    private final PdfTextSelection textSelection;
    private boolean textSelectionEnabled;
    private float thumbGrabOffset;
    private final AnimatedFloat thumbOnLightBackground;
    private int topInset;
    private float zoom;
    private ValueAnimator zoomAnimator;

    public interface Delegate {
        void onLinkClick(String str);

        void onSearchUpdated(int i, int i2, boolean z);

        void onShareText(String str);

        void onTextCopied();

        void onTranslateText(CharSequence charSequence, String str, Runnable runnable);

        void onViewportChanged(float f, float f2);
    }

    public static final class Detail {
        private final Bitmap bitmap;
        private final float displayWidth;
        private final RectF region;

        private Detail(Bitmap bitmap, RectF rectF, float f) {
            this.bitmap = bitmap;
            this.region = rectF;
            this.displayWidth = f;
        }

        public Bitmap bitmap() {
            return this.bitmap;
        }

        public float displayWidth() {
            return this.displayWidth;
        }

        public RectF region() {
            return this.region;
        }
    }


    public PdfPagesView(Context context, PdfDocumentRenderer pdfDocumentRenderer, Delegate delegate, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.pagePadding = AndroidUtilities.dp(8.0f);
        this.pageGap = AndroidUtilities.dp(8.0f);
        this.zoom = 1.0f;
        this.bases = new LinkedHashMap<>(16, 0.75f, true);
        this.details = new SparseArray<>();
        this.links = new SparseArray<>();
        this.failedPages = new SparseBooleanArray();
        this.lastJobType = -1;
        this.nextMeasurePage = 1;
        this.searchMatches = new SparseArray<>();
        this.currentMatchPage = -1;
        this.lastVisible = -1;
        this.currentPage = -1;
        this.baseAppearTime = new SparseLongArray();
        this.bitmapPaint = new Paint(6);
        this.placeholderPaint = new Paint();
        Paint paint = new Paint(1);
        this.highlightPaint = paint;
        this.overlayPaint = new Paint(1);
        AnimatedTextView.AnimatedTextDrawable animatedTextDrawable = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
        this.indicatorText = animatedTextDrawable;
        LoadingDrawable loadingDrawable = new LoadingDrawable();
        this.skeletonDrawable = loadingDrawable;
        this.skeletonPath = new Path();
        this.skeletonRect = new RectF();
        Runnable invalidateOverlayRunnable = this::invalidateOverlay;
        CubicBezierInterpolator cubicBezierInterpolator = CubicBezierInterpolator.EASE_OUT_QUINT;
        this.overlayAlpha = new AnimatedFloat(invalidateOverlayRunnable, 0L, 220L, cubicBezierInterpolator);
        this.thumbOnLightBackground = new AnimatedFloat((() -> this.invalidateOverlay()), 0L, 220L, cubicBezierInterpolator);
        this.rect = new RectF();
        this.rect2 = new RectF();
        this.sampleRect = new RectF();
        this.invalidateRunnable = (() -> this.invalidateOverlay());
        this.renderer = pdfDocumentRenderer;
        this.delegate = delegate;
        this.resourcesProvider = resourcesProvider;
        int i = pdfDocumentRenderer.pageCount;
        this.pageCount = i;
        float[] fArr = new float[i];
        this.pageAspect = fArr;
        this.failedDetailWidth = new float[i];
        boolean[] zArr = new boolean[i];
        this.measured = zArr;
        this.pageTop = new float[i];
        Arrays.fill(fArr, pdfDocumentRenderer.firstPageHeight / pdfDocumentRenderer.firstPageWidth);
        zArr[0] = true;
        this.basesBudget = Math.min(67108864L, Runtime.getRuntime().maxMemory() / 4);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        animatedTextDrawable.setTextSize(AndroidUtilities.dp(13.0f));
        animatedTextDrawable.setTypeface(AndroidUtilities.bold());
        animatedTextDrawable.setGravity(17);
        animatedTextDrawable.setAnimationProperties(0.25f, 0L, 280L, cubicBezierInterpolator);
        animatedTextDrawable.setCallback(this);
        loadingDrawable.setCallback(this);
        this.textSelection = new PdfTextSelection(this, delegate, resourcesProvider);
        updateColors();
        this.scroller = new OverScroller(context);
        GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent motionEvent) {
                return true;
            }

            @Override
            public boolean onDown(MotionEvent motionEvent) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent2, float f, float f2) {
                PdfPagesView pdfPagesView = PdfPagesView.this;
                pdfPagesView.setScrollInternal(pdfPagesView.scrollX + f, PdfPagesView.this.scrollY + f2);
                PdfPagesView.this.noteActivity();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent2, float f, float f2) {
                if (PdfPagesView.this.scaledInGesture) {
                    return false;
                }
                PdfPagesView.this.fling(f, f2);
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
                boolean zIsActive = PdfPagesView.this.textSelection.isActive();
                PdfPagesView pdfPagesView = PdfPagesView.this;
                if (zIsActive) {
                    pdfPagesView.textSelection.clear();
                    return true;
                }
                return pdfPagesView.handleTap(motionEvent.getX(), motionEvent.getY());
            }

            @Override
            public void onLongPress(MotionEvent motionEvent) {
                if (!PdfPagesView.this.textSelectionEnabled || PdfPagesView.this.scaledInGesture || PdfPagesView.this.draggingThumb) {
                    return;
                }
                for (int i2 = PdfPagesView.this.firstVisible; i2 <= PdfPagesView.this.lastVisible; i2++) {
                    PdfPagesView pdfPagesView = PdfPagesView.this;
                    pdfPagesView.getPageRect(i2, pdfPagesView.rect);
                    if (PdfPagesView.this.rect.contains(motionEvent.getX(), motionEvent.getY())) {
                        PdfPagesView.this.getParent().requestDisallowInterceptTouchEvent(true);
                        PdfPagesView.this.scroller.forceFinished(true);
                        PdfPagesView.this.textSelection.startAt(i2, motionEvent.getX(), motionEvent.getY());
                        return;
                    }
                }
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == 1 && !PdfPagesView.this.scaledInGesture) {
                    PdfPagesView pdfPagesView = PdfPagesView.this;
                    pdfPagesView.animateZoom(pdfPagesView.zoom > 1.05f ? 1.0f : 2.5f, motionEvent.getX(), motionEvent.getY());
                }
                return true;
            }
        });
        this.gestureDetector = gestureDetector;
        gestureDetector.setIsLongpressEnabled(false);
        this.scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
                PdfPagesView.this.scaling = true;
                PdfPagesView.this.scaledInGesture = true;
                PdfPagesView.this.scroller.forceFinished(true);
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
                PdfPagesView pdfPagesView = PdfPagesView.this;
                pdfPagesView.setZoomInternal(Utilities.clamp(pdfPagesView.zoom * scaleGestureDetector.getScaleFactor(), 5.0f, 1.0f), scaleGestureDetector.getFocusX(), scaleGestureDetector.getFocusY());
                PdfPagesView.this.noteActivity();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
                PdfPagesView.this.scaling = false;
            }
        });
    }

    public void setInsets(int i, int i2) {
        if (this.topInset == i && this.bottomInset == i2) {
            return;
        }
        this.topInset = i;
        this.bottomInset = i2;
        setScrollInternal(this.scrollX, this.scrollY);
        invalidate();
        invalidateOverlay();
    }

    public View createOverlayView(Context context) {
        View view = new View(context) {
            @Override
            public void onDraw(Canvas canvas) {
                PdfPagesView.this.drawOverlay(canvas);
            }

            @Override
            public boolean verifyDrawable(Drawable drawable) {
                return drawable == PdfPagesView.this.indicatorText || super.verifyDrawable(drawable);
            }
        };
        this.overlayView = view;
        this.indicatorText.setCallback(view);
        return this.overlayView;
    }

    public void setTextSelectionEnabled(boolean z) {
        boolean z2 = z && this.renderer.supportsSearch;
        this.textSelectionEnabled = z2;
        this.gestureDetector.setIsLongpressEnabled(z2);
        if (this.textSelectionEnabled) {
            return;
        }
        this.textSelection.clear();
    }

    public boolean hasTextSelection() {
        return this.textSelection.isActive();
    }

    public void clearTextSelection() {
        this.textSelection.clear();
    }

    public void updateColors() {
        this.textSelection.updateColors();
        int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, this.resourcesProvider);
        this.placeholderPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, this.resourcesProvider));
        this.skeletonDrawable.setColors(Theme.multAlpha(color, 0.06f), Theme.multAlpha(color, 0.13f));
        invalidate();
        invalidateOverlay();
    }

    @Override
    public boolean verifyDrawable(Drawable drawable) {
        return drawable == this.skeletonDrawable || drawable == this.indicatorText || super.verifyDrawable(drawable);
    }

    public boolean isZoomed() {
        return this.zoom > 1.01f;
    }

    public void search(String str) {
        String strTrim = str == null ? null : str.trim();
        boolean zIsEmpty = TextUtils.isEmpty(strTrim);
        String str2 = this.searchQuery;
        if (zIsEmpty) {
            if (str2 == null) {
                return;
            } else {
                this.searchQuery = null;
            }
        } else if (strTrim.equals(str2)) {
            return;
        } else {
            this.searchQuery = strTrim;
        }
        this.searchGeneration++;
        this.searchVersion++;
        this.searchMatches.clear();
        this.searchStartPage = Math.max(0, this.currentPage);
        this.searchCrawled = 0;
        this.searchTotal = 0;
        this.currentMatchPage = -1;
        this.currentMatchIndex = 0;
        invalidate();
        notifySearchUpdated();
        scheduleNext();
    }

    public void searchNavigate(boolean z) {
        int i;
        int i2 = this.currentMatchPage;
        if (i2 < 0) {
            return;
        }
        ArrayList<RectF[]> arrayList = this.searchMatches.get(i2);
        if (z && arrayList != null && this.currentMatchIndex + 1 < arrayList.size()) {
            this.currentMatchIndex++;
        } else if (z || (i = this.currentMatchIndex) <= 0) {
            int i3 = 1;
            while (true) {
                int i4 = this.pageCount;
                if (i3 > i4) {
                    break;
                }
                int i5 = this.currentMatchPage;
                int i6 = (z ? i5 + i3 : (i5 - i3) + i4) % i4;
                ArrayList<RectF[]> arrayList2 = this.searchMatches.get(i6);
                if (arrayList2 != null && !arrayList2.isEmpty()) {
                    this.currentMatchPage = i6;
                    this.currentMatchIndex = z ? 0 : arrayList2.size() - 1;
                    break;
                }
                i3++;
            }
        } else {
            this.currentMatchIndex = i - 1;
        }
        this.searchVersion++;
        scrollToCurrentMatch();
        notifySearchUpdated();
        invalidate();
    }

    public void destroy() {
        this.destroyed = true;
        this.scroller.forceFinished(true);
        ValueAnimator valueAnimator = this.zoomAnimator;
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        removeCallbacks(this.invalidateRunnable);
        this.textSelection.clear();
        clearBitmaps();
    }

    @Override
    public void onSizeChanged(int i, int i2, int i3, int i4) {
        super.onSizeChanged(i, i2, i3, i4);
        int iMax = Math.max(1, i - (this.pagePadding * 2));
        int i5 = this.pageWidth;
        if (iMax != i5) {
            boolean z = i5 > 0;
            float maxScrollX = getMaxScrollX();
            float f = maxScrollX > 0.0f ? this.scrollX / maxScrollX : 0.0f;
            int pageAt = z ? getPageAt(getAnchorContentY()) : 0;
            float anchorContentY = z ? (getAnchorContentY() - this.pageTop[pageAt]) / getPageHeight(pageAt) : 0.0f;
            this.pageWidth = iMax;
            clearBitmaps();
            updateLayout();
            if (z) {
                this.scrollY = ((this.pageTop[pageAt] + (anchorContentY * getPageHeight(pageAt))) * this.zoom) + getCenterOffset();
                this.scrollX = f * getMaxScrollX();
            } else {
                noteActivity();
            }
        }
        setScrollInternal(this.scrollX, this.scrollY);
    }

    @Override
    @SuppressLint({"ClickableViewAccessibility"})
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (this.destroyed) {
            return false;
        }
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked == 0) {
            this.scaledInGesture = false;
            ValueAnimator valueAnimator = this.zoomAnimator;
            if (valueAnimator != null) {
                valueAnimator.cancel();
            }
            this.scroller.forceFinished(true);
            if (this.textSelection.onDown(motionEvent.getX(), motionEvent.getY())) {
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (isThumbHit(motionEvent.getX(), motionEvent.getY())) {
                this.draggingThumb = true;
                this.thumbGrabOffset = motionEvent.getY() - getThumbTop();
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidateOverlay();
                return true;
            }
        }
        if (this.textSelection.isDragging()) {
            boolean zIsLongPressDrag = this.textSelection.isLongPressDrag();
            if (actionMasked == 2) {
                this.textSelection.onMove(motionEvent.getX(), motionEvent.getY());
            } else if (actionMasked == 1 || actionMasked == 3) {
                this.textSelection.onUp();
            }
            if (zIsLongPressDrag) {
                this.gestureDetector.onTouchEvent(motionEvent);
            }
            return true;
        }
        if (!this.draggingThumb) {
            if (actionMasked == 5) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            this.scaleDetector.onTouchEvent(motionEvent);
            this.gestureDetector.onTouchEvent(motionEvent);
            if (actionMasked == 1 || actionMasked == 3) {
                this.scaling = false;
                onInteractionSettled();
            }
            return true;
        }
        if (actionMasked == 2) {
            float trackHeight = getTrackHeight() - getThumbHeight();
            if (trackHeight > 0.0f) {
                setScrollInternal(this.scrollX, Utilities.clamp(((motionEvent.getY() - this.thumbGrabOffset) - getTrackTop()) / trackHeight, 1.0f, 0.0f) * getMaxScrollY());
            }
            noteActivity();
        } else if (actionMasked == 1 || actionMasked == 3) {
            this.draggingThumb = false;
            noteActivity();
            onInteractionSettled();
        }
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent motionEvent) {
        if (!this.destroyed && motionEvent.isFromSource(2) && motionEvent.getActionMasked() == 8) {
            float axisValue = motionEvent.getAxisValue(9);
            if (axisValue != 0.0f) {
                if ((motionEvent.getMetaState() & 4096) != 0) {
                    setZoomInternal(Utilities.clamp(this.zoom * (axisValue > 0.0f ? 1.1f : 0.9090909f), 5.0f, 1.0f), motionEvent.getX(), motionEvent.getY());
                } else {
                    setScrollInternal(this.scrollX, this.scrollY - (axisValue * AndroidUtilities.dp(64.0f)));
                }
                noteActivity();
                onInteractionSettled();
                return true;
            }
        }
        return super.onGenericMotionEvent(motionEvent);
    }

    @Override
    public boolean canScrollHorizontally(int i) {
        float f = this.scrollX;
        if (i < 0) {
            return f > 0.5f;
        }
        return f < getMaxScrollX() - 0.5f;
    }

    @Override
    public boolean canScrollVertically(int i) {
        float f = this.scrollY;
        if (i < 0) {
            return f > 0.5f;
        }
        return f < getMaxScrollY() - 0.5f;
    }

    @Override
    public void computeScroll() {
        if (this.scroller.isFinished() || !this.scroller.computeScrollOffset()) {
            return;
        }
        setScrollInternal(this.scroller.getCurrX(), this.scroller.getCurrY() + this.flingOffsetY);
        noteActivity();
        if (this.scroller.isFinished()) {
            onInteractionSettled();
        } else {
            postInvalidateOnAnimation();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        drawPages(canvas, 0.0f, getHeight());
        if (this.overlayView == null) {
            drawOverlay(canvas);
        }
    }

    @Override
    public void capture(Canvas canvas, RectF rectF) {
        canvas.save();
        canvas.clipRect(rectF);
        drawPages(canvas, rectF.top, rectF.bottom);
        canvas.restore();
    }

    @Override
    public void captureCalculateHash(IBlur3Hash iBlur3Hash, RectF rectF) {
        iBlur3Hash.addF(this.scrollX);
        iBlur3Hash.addF(this.scrollY);
        iBlur3Hash.addF(this.zoom);
        iBlur3Hash.add(getWidth());
        iBlur3Hash.add(getHeight());
        iBlur3Hash.add(this.topInset);
        iBlur3Hash.add(this.searchVersion);
        iBlur3Hash.add(this.textSelection.getVersion());
        if (this.pageWidth <= 0) {
            return;
        }
        int pageAt = getPageAt(getContentY(rectF.bottom));
        for (int pageAt2 = getPageAt(getContentY(rectF.top)); pageAt2 <= pageAt; pageAt2++) {
            iBlur3Hash.add(System.identityHashCode(this.bases.get(Integer.valueOf(pageAt2))));
            Detail detail = this.details.get(pageAt2);
            iBlur3Hash.add(detail == null ? 0L : System.identityHashCode(detail.bitmap()));
        }
    }

    private void drawPages(Canvas canvas, float f, float f2) {
        int i;
        long j;
        float fMin;
        float f3 = f;
        float f4 = f2;
        if (this.pageWidth <= 0 || f4 <= f3) {
            return;
        }
        int pageAt = getPageAt(getContentY(f3));
        int pageAt2 = getPageAt(getContentY(f4));
        long jUptimeMillis = SystemClock.uptimeMillis();
        while (pageAt <= pageAt2) {
            getPageRect(pageAt, this.rect);
            RectF rectF = this.rect;
            if (rectF.bottom < f3 || rectF.top > f4) {
                i = pageAt2;
                j = jUptimeMillis;
            } else {
                Bitmap bitmap = this.bases.get(Integer.valueOf(pageAt));
                long j2 = bitmap != null ? this.baseAppearTime.get(pageAt) : 0L;
                if (j2 > 0) {
                    fMin = Math.min(1.0f, (jUptimeMillis - j2) / 200.0f);
                    if (fMin >= 1.0f) {
                        this.baseAppearTime.delete(pageAt);
                    } else {
                        invalidate();
                    }
                } else {
                    fMin = 1.0f;
                }
                if (bitmap == null || fMin < 1.0f) {
                    canvas.drawRect(this.rect, this.placeholderPaint);
                    if (!this.failedPages.get(pageAt)) {
                        drawSkeleton(canvas, this.rect);
                    }
                }
                if (bitmap != null) {
                    this.bitmapPaint.setAlpha(Math.round(CubicBezierInterpolator.EASE_OUT.getInterpolation(fMin) * 255.0f));
                    canvas.drawBitmap(bitmap, (Rect) null, this.rect, this.bitmapPaint);
                    this.bitmapPaint.setAlpha(255);
                }
                Detail detail = this.details.get(pageAt);
                if (detail != null) {
                    this.rect2.set(this.rect.left + (detail.region().left * this.rect.width()), this.rect.top + (detail.region().top * this.rect.height()), this.rect.left + (detail.region().right * this.rect.width()), this.rect.top + (detail.region().bottom * this.rect.height()));
                    canvas.drawBitmap(detail.bitmap(), (Rect) null, this.rect2, this.bitmapPaint);
                }
                ArrayList<RectF[]> arrayList = this.searchMatches.get(pageAt);
                if (arrayList != null) {
                    float fDp = AndroidUtilities.dp(1.0f);
                    int i2 = 0;
                    while (i2 < arrayList.size()) {
                        this.highlightPaint.setColor((pageAt == this.currentMatchPage && i2 == this.currentMatchIndex) ? -22733 : -6551);
                        RectF[] rectFArr = arrayList.get(i2);
                        int length = rectFArr.length;
                        int i3 = 0;
                        while (i3 < length) {
                            RectF rectF2 = rectFArr[i3];
                            RectF rectF3 = this.rect2;
                            RectF rectF4 = this.rect;
                            float fWidth = (rectF4.left + (rectF2.left * rectF4.width())) - fDp;
                            RectF rectF5 = this.rect;
                            int i4 = pageAt2;
                            float fHeight = (rectF5.top + (rectF2.top * rectF5.height())) - fDp;
                            RectF rectF6 = this.rect;
                            long j3 = jUptimeMillis;
                            float fWidth2 = rectF6.left + (rectF2.right * rectF6.width()) + fDp;
                            RectF rectF7 = this.rect;
                            rectF3.set(fWidth, fHeight, fWidth2, rectF7.top + (rectF2.bottom * rectF7.height()) + fDp);
                            float f5 = 2.0f * fDp;
                            canvas.drawRoundRect(this.rect2, f5, f5, this.highlightPaint);
                            i3++;
                            pageAt2 = i4;
                            jUptimeMillis = j3;
                        }
                        i2++;
                    }
                }
                i = pageAt2;
                j = jUptimeMillis;
                this.textSelection.drawHighlight(canvas, pageAt, this.rect);
            }
            pageAt++;
            f3 = f;
            f4 = f2;
            pageAt2 = i;
            jUptimeMillis = j;
        }
    }

    private void drawSkeleton(Canvas canvas, RectF rectF) {
        float fWidth = rectF.width();
        float f = rectF.left + (0.1f * fWidth);
        float f2 = 0.8f * fWidth;
        float fMax = Math.max(AndroidUtilities.dp(3.0f), fWidth * 0.02f);
        float f3 = 2.2f * fMax;
        float fHeight = rectF.bottom - (rectF.height() * 0.08f);
        float fHeight2 = rectF.top + (rectF.height() * 0.08f);
        this.skeletonPath.rewind();
        float f4 = fMax * 1.5f;
        this.skeletonRect.set(f, fHeight2, (0.55f * f2) + f, fHeight2 + f4);
        float f5 = 0.75f * fMax;
        this.skeletonPath.addRoundRect(this.skeletonRect, f5, f5, Path.Direction.CW);
        float f6 = fHeight2 + f4 + (1.5f * f3);
        int i = 0;
        while (true) {
            float f7 = f6 + fMax;
            if (f7 <= fHeight) {
                boolean z = i % 5 == 4;
                this.skeletonRect.set(f, f6, ((z ? 0.6f : 1.0f) * f2) + f, f7);
                float f8 = fMax / 2.0f;
                this.skeletonPath.addRoundRect(this.skeletonRect, f8, f8, Path.Direction.CW);
                f6 += z ? 2.0f * f3 : f3;
                i++;
            } else {
                this.skeletonDrawable.usePath(this.skeletonPath);
                this.skeletonDrawable.setBounds(rectF);
                this.skeletonDrawable.draw(canvas);
                return;
            }
        }
    }
    public void drawOverlay(Canvas canvas) {
        this.textSelection.drawHandles(canvas);
        float f = this.overlayAlpha.set(this.draggingThumb || SystemClock.uptimeMillis() - this.lastActivityTime < 1200);
        this.overlayVisibility = f;
        if (f <= 0.0f) {
            return;
        }
        if (this.pageCount > 1 && this.currentPage >= 0) {
            float currentWidth = this.indicatorText.getCurrentWidth() + AndroidUtilities.dp(20.0f);
            float width = (getWidth() - currentWidth) / 2.0f;
            this.rect2.set(width, this.topInset + AndroidUtilities.dp(10.0f), currentWidth + width, this.topInset + AndroidUtilities.dp(36.0f));
            this.overlayPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_mediaTimeBackground, this.resourcesProvider), f));
            canvas.drawRoundRect(this.rect2, AndroidUtilities.dp(13.0f), AndroidUtilities.dp(13.0f), this.overlayPaint);
            int color = Theme.getColor(Theme.key_chat_mediaTimeText, this.resourcesProvider);
            this.indicatorText.setTextColor(color);
            this.indicatorText.setAlpha(Math.round(Color.alpha(color) * f));
            this.indicatorText.setBounds(this.rect2);
            this.indicatorText.draw(canvas);
        }
        if (hasThumb()) {
            float thumbTop = getThumbTop();
            float width2 = getWidth() - AndroidUtilities.dp(3.0f);
            this.rect2.set(width2 - AndroidUtilities.dp(this.draggingThumb ? 6.0f : 4.0f), thumbTop, width2, getThumbHeight() + thumbTop);
            AnimatedFloat animatedFloat = this.thumbOnLightBackground;
            float fCenterX = this.rect2.centerX();
            RectF rectF = this.rect2;
            this.overlayPaint.setColor(Theme.multAlpha(this.draggingThumb ? Theme.getColor(Theme.key_fastScrollActive, this.resourcesProvider) : ColorUtils.blendARGB(-1275068417, -1979711488, animatedFloat.set(isLightBehind(fCenterX, rectF.top, rectF.bottom))), f));
            canvas.drawRoundRect(this.rect2, AndroidUtilities.dp(3.0f), AndroidUtilities.dp(3.0f), this.overlayPaint);
        }
    }

    private boolean isLightBehind(float f, float f2, float f3) {
        float brightnessAt = 0.0f;
        for (int i = 0; i < 5; i++) {
            brightnessAt += getBrightnessAt(f, (((f3 - f2) * (i + 0.5f)) / 5.0f) + f2);
        }
        return brightnessAt / 5.0f > 0.6f;
    }

    private float getBrightnessAt(float f, float f2) {
        int pageAt = getPageAt(getContentY(f2));
        getPageRect(pageAt, this.sampleRect);
        if (!this.sampleRect.contains(f, f2)) {
            return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundGray, this.resourcesProvider));
        }
        Bitmap bitmap = this.bases.get(Integer.valueOf(pageAt));
        if (bitmap == null || bitmap.isRecycled()) {
            return AndroidUtilities.computePerceivedBrightness(this.placeholderPaint.getColor());
        }
        RectF rectF = this.sampleRect;
        int iClamp = Utilities.clamp((int) (((f - rectF.left) / rectF.width()) * bitmap.getWidth()), bitmap.getWidth() - 1, 0);
        RectF rectF2 = this.sampleRect;
        return AndroidUtilities.computePerceivedBrightness(bitmap.getPixel(iClamp, Utilities.clamp((int) (((f2 - rectF2.top) / rectF2.height()) * bitmap.getHeight()), bitmap.getHeight() - 1, 0)));
    }

    private boolean hasThumb() {
        return getMaxScrollY() > ((float) getHeight()) * 0.5f;
    }

    private float getTrackTop() {
        return this.topInset + AndroidUtilities.dp(8.0f);
    }

    private float getTrackHeight() {
        return ((getHeight() - this.topInset) - this.bottomInset) - AndroidUtilities.dp(16.0f);
    }

    private float getThumbHeight() {
        return Math.min(getTrackHeight(), Math.max(AndroidUtilities.dp(36.0f), (getTrackHeight() * getHeight()) / Math.max(1.0f, ((this.contentHeight * this.zoom) + this.topInset) + this.bottomInset)));
    }

    private float getThumbTop() {
        float maxScrollY = getMaxScrollY();
        return getTrackTop() + ((getTrackHeight() - getThumbHeight()) * (maxScrollY > 0.0f ? this.scrollY / maxScrollY : 0.0f));
    }

    private boolean isThumbHit(float f, float f2) {
        if (hasThumb() && this.overlayVisibility >= 0.5f && f >= getWidth() - AndroidUtilities.dp(32.0f)) {
            float thumbTop = getThumbTop();
            if (f2 >= thumbTop - AndroidUtilities.dp(16.0f) && f2 <= thumbTop + getThumbHeight() + AndroidUtilities.dp(16.0f)) {
                return true;
            }
        }
        return false;
    }
    public void noteActivity() {
        this.lastActivityTime = SystemClock.uptimeMillis();
        removeCallbacks(this.invalidateRunnable);
        postDelayed(this.invalidateRunnable, 1200L);
        invalidateOverlay();
        this.textSelection.onViewportChanged();
    }
    public void onInteractionSettled() {
        if (isSettled()) {
            this.textSelection.onSettled();
        }
        scheduleNext();
    }

    public int getTopInset() {
        return this.topInset;
    }

    public int getPageAtY(float f) {
        return getPageAt(getContentY(f));
    }

    public boolean scrollForSelection(float f) {
        float f2 = this.scrollY;
        setScrollInternal(this.scrollX, f + f2);
        if (this.scrollY == f2) {
            return false;
        }
        noteActivity();
        return true;
    }

    public int getBottomInset() {
        return this.bottomInset;
    }

    public void invalidateOverlay() {
        View view = this.overlayView;
        if (view != null) {
            view.invalidate();
        } else {
            invalidate();
        }
    }
    public boolean handleTap(float f, float f2) {
        for (int i = this.firstVisible; i <= this.lastVisible; i++) {
            getPageRect(i, this.rect);
            if (this.rect.contains(f, f2)) {
                ArrayList<PdfDocumentRenderer.Link> arrayList = this.links.get(i);
                if (arrayList != null) {
                    RectF rectF = this.rect;
                    float fWidth = (f - rectF.left) / rectF.width();
                    RectF rectF2 = this.rect;
                    float fHeight = (f2 - rectF2.top) / rectF2.height();
                    float fDp = AndroidUtilities.dp(6.0f) / this.rect.width();
                    float fDp2 = AndroidUtilities.dp(6.0f) / this.rect.height();
                    for (int i2 = 0; i2 < arrayList.size(); i2++) {
                        PdfDocumentRenderer.Link link = arrayList.get(i2);
                        RectF rectF3 = link.bounds;
                        if (fWidth >= rectF3.left - fDp && fWidth <= rectF3.right + fDp && fHeight >= rectF3.top - fDp2 && fHeight <= rectF3.bottom + fDp2) {
                            String str = link.url;
                            if (str != null) {
                                this.delegate.onLinkClick(str);
                            } else {
                                int i3 = link.page;
                                if (i3 >= 0 && i3 < this.pageCount) {
                                    this.scroller.forceFinished(true);
                                    float f3 = this.scrollX;
                                    float[] fArr = this.pageTop;
                                    int i4 = link.page;
                                    setScrollInternal(f3, (((fArr[i4] + (link.y * getPageHeight(i4))) * this.zoom) + getCenterOffset()) - this.pagePadding);
                                    noteActivity();
                                    onInteractionSettled();
                                }
                            }
                            return true;
                        }
                    }
                    break;
                }
                return false;
            }
        }
        return false;
    }

    private void scrollToCurrentMatch() {
        int i = this.currentMatchPage;
        ArrayList<RectF[]> arrayList = i < 0 ? null : this.searchMatches.get(i);
        if (arrayList == null || this.currentMatchIndex >= arrayList.size()) {
            return;
        }
        float fMin = 1.0f;
        boolean z = false;
        float fMin2 = 1.0f;
        float fMax = 0.0f;
        float fMax2 = 0.0f;
        for (RectF rectF : arrayList.get(this.currentMatchIndex)) {
            fMin = Math.min(fMin, rectF.left);
            fMin2 = Math.min(fMin2, rectF.top);
            fMax = Math.max(fMax, rectF.right);
            fMax2 = Math.max(fMax2, rectF.bottom);
        }
        getPageRect(this.currentMatchPage, this.rect);
        RectF rectF2 = this.rect;
        float fWidth = rectF2.left + (fMin * rectF2.width());
        RectF rectF3 = this.rect;
        float fWidth2 = rectF3.left + (fMax * rectF3.width());
        RectF rectF4 = this.rect;
        float fHeight = rectF4.top + (fMin2 * rectF4.height());
        RectF rectF5 = this.rect;
        float fHeight2 = rectF5.top + (fMax2 * rectF5.height());
        float fDp = this.topInset + AndroidUtilities.dp(8.0f);
        float height = (getHeight() - this.bottomInset) - AndroidUtilities.dp(8.0f);
        boolean z2 = fHeight >= fDp && fHeight2 <= height;
        if (fWidth >= 0.0f && fWidth2 <= getWidth()) {
            z = true;
        }
        if (z2 && z) {
            return;
        }
        float width = z ? 0.0f : ((fWidth + fWidth2) / 2.0f) - (getWidth() / 2.0f);
        float f = z2 ? 0.0f : ((fHeight + fHeight2) / 2.0f) - ((fDp + height) / 2.0f);
        float fClamp = Utilities.clamp(this.scrollX + width, getMaxScrollX(), 0.0f);
        float fClamp2 = Utilities.clamp(this.scrollY + f, getMaxScrollY(), 0.0f);
        this.scroller.forceFinished(true);
        this.flingOffsetY = 0.0f;
        this.scroller.startScroll(Math.round(this.scrollX), Math.round(this.scrollY), Math.round(fClamp - this.scrollX), Math.round(fClamp2 - this.scrollY), 320);
        postInvalidateOnAnimation();
    }

    private void notifySearchUpdated() {
        int size;
        if (this.currentMatchPage >= 0) {
            size = this.currentMatchIndex;
            for (int i = 0; i < this.searchMatches.size(); i++) {
                if (this.searchMatches.keyAt(i) < this.currentMatchPage) {
                    size += this.searchMatches.valueAt(i).size();
                }
            }
        } else {
            size = -1;
        }
        this.delegate.onSearchUpdated(size, this.searchTotal, this.searchQuery == null || this.searchCrawled >= this.pageCount);
    }
    public void fling(float f, float f2) {
        this.scroller.forceFinished(true);
        this.flingOffsetY = 0.0f;
        this.scroller.fling(Math.round(this.scrollX), Math.round(this.scrollY), Math.round(-f), Math.round(-f2), 0, Math.round(getMaxScrollX()), 0, Math.round(getMaxScrollY()));
        postInvalidateOnAnimation();
    }
    public void animateZoom(final float f, final float f2, final float f3) {
        ValueAnimator valueAnimator = this.zoomAnimator;
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        final float f4 = this.zoom;
        ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(0.0f, 1.0f);
        valueAnimatorOfFloat.addUpdateListener(valueAnimator2 -> lambda_animateZoom_0(f4, f, f2, f3, valueAnimator2));
        valueAnimatorOfFloat.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                if (PdfPagesView.this.zoomAnimator == animator) {
                    PdfPagesView.this.zoomAnimator = null;
                    PdfPagesView.this.onInteractionSettled();
                }
            }
        });
        valueAnimatorOfFloat.setDuration(280L);
        valueAnimatorOfFloat.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        this.zoomAnimator = valueAnimatorOfFloat;
        valueAnimatorOfFloat.start();
    }
    public /* synthetic */ void lambda_animateZoom_0(float f, float f2, float f3, float f4, ValueAnimator valueAnimator) {
        setZoomInternal(AndroidUtilities.lerp(f, f2, ((Float) valueAnimator.getAnimatedValue()).floatValue()), f3, f4);
        noteActivity();
    }
    public void setZoomInternal(float f, float f2, float f3) {
        if (this.pageWidth <= 0) {
            return;
        }
        float f4 = (this.scrollX + f2) / this.zoom;
        float contentOffset = ((this.scrollY + f3) - getContentOffset()) / this.zoom;
        this.zoom = f;
        setScrollInternal((f4 * f) - f2, ((contentOffset * f) + getContentOffset()) - f3);
    }
    public void setScrollInternal(float f, float f2) {
        float f3 = this.scrollX;
        float f4 = this.scrollY;
        this.scrollX = Utilities.clamp(f, getMaxScrollX(), 0.0f);
        this.scrollY = Utilities.clamp(f2, getMaxScrollY(), 0.0f);
        onViewportChanged();
        this.delegate.onViewportChanged(this.scrollX - f3, this.scrollY - f4);
    }

    private float getMaxScrollX() {
        return Math.max(0.0f, (getWidth() * this.zoom) - getWidth());
    }

    private float getMaxScrollY() {
        return Math.max(0.0f, (((this.contentHeight * this.zoom) + this.topInset) + this.bottomInset) - getHeight());
    }

    private float getCenterOffset() {
        return Math.max(0.0f, (((getHeight() - this.topInset) - this.bottomInset) - (this.contentHeight * this.zoom)) / 2.0f);
    }

    private float getContentOffset() {
        return this.topInset + getCenterOffset();
    }

    private float getContentY(float f) {
        return ((f + this.scrollY) - getContentOffset()) / this.zoom;
    }

    private float getAnchorContentY() {
        return getContentY(this.topInset);
    }

    private float getPageHeight(int i) {
        return this.pageWidth * this.pageAspect[i];
    }

    public void getPageRect(int i, RectF rectF) {
        float f = this.pagePadding;
        float f2 = this.zoom;
        rectF.left = (f * f2) - this.scrollX;
        float contentOffset = ((this.pageTop[i] * f2) - this.scrollY) + getContentOffset();
        rectF.top = contentOffset;
        rectF.right = rectF.left + (this.pageWidth * this.zoom);
        rectF.bottom = contentOffset + (getPageHeight(i) * this.zoom);
    }

    private int getPageAt(float f) {
        int i = this.pageCount - 1;
        int i2 = 0;
        while (i2 < i) {
            int i3 = ((i2 + i) + 1) >>> 1;
            if (this.pageTop[i3] <= f) {
                i2 = i3;
            } else {
                i = i3 - 1;
            }
        }
        return i2;
    }

    private void updateLayout() {
        float pageHeight = this.pagePadding;
        for (int i = 0; i < this.pageCount; i++) {
            this.pageTop[i] = pageHeight;
            pageHeight += getPageHeight(i);
            if (i < this.pageCount - 1) {
                pageHeight += this.pageGap;
            }
        }
        this.contentHeight = pageHeight + this.pagePadding;
    }

    private void relayoutKeepingAnchor() {
        float anchorContentY = getAnchorContentY();
        int pageAt = getPageAt(anchorContentY);
        float pageHeight = (anchorContentY - this.pageTop[pageAt]) / getPageHeight(pageAt);
        float f = this.scrollY;
        updateLayout();
        setScrollInternal(this.scrollX, ((this.pageTop[pageAt] + (pageHeight * getPageHeight(pageAt))) * this.zoom) + getCenterOffset());
        this.flingOffsetY += this.scrollY - f;
        invalidate();
    }

    private void onViewportChanged() {
        if (this.pageWidth <= 0 || getHeight() <= 0) {
            return;
        }
        this.firstVisible = getPageAt(getContentY(0.0f));
        this.lastVisible = getPageAt(getContentY(getHeight()));
        for (int size = this.details.size() - 1; size >= 0; size--) {
            int iKeyAt = this.details.keyAt(size);
            if (iKeyAt < this.firstVisible || iKeyAt > this.lastVisible) {
                AndroidUtilities.recycleBitmap(this.details.valueAt(size).bitmap());
                this.details.removeAt(size);
            }
        }
        int pageAt = (this.scrollY < getMaxScrollY() - 1.0f || getMaxScrollY() <= 0.0f) ? getPageAt(getContentY(((this.topInset + getHeight()) - this.bottomInset) / 2.0f)) : this.pageCount - 1;
        if (pageAt != this.currentPage) {
            AnimatedTextView.AnimatedTextDrawable animatedTextDrawable = this.indicatorText;
            String string = LocaleController.formatString(R.string.Of, Integer.valueOf(pageAt + 1), Integer.valueOf(this.pageCount));
            int i = this.currentPage;
            animatedTextDrawable.setText(string, i >= 0 && this.overlayVisibility > 0.0f, pageAt < i);
            this.currentPage = pageAt;
        }
        invalidate();
        invalidateOverlay();
        scheduleNext();
    }

    private boolean isSettled() {
        return !this.scaling && this.zoomAnimator == null && this.scroller.isFinished();
    }

    public void scheduleNext() {
        PdfDocumentRenderer.Job jobPickJob;
        if (this.destroyed || this.inFlight != null || this.pageWidth <= 0 || getHeight() <= 0 || (jobPickJob = pickJob()) == null) {
            return;
        }
        this.inFlight = jobPickJob;
        this.renderer.run(jobPickJob, obj -> onJobDone((PdfDocumentRenderer.Job) obj));
    }

    private PdfDocumentRenderer.Job pickJob() {
        PdfDocumentRenderer.Job jobTakePendingJob;
        int i;
        int i2;
        int i3;
        int i4;
        if ((this.lastJobType != 4 || !needsVisibleBase()) && (jobTakePendingJob = this.textSelection.takePendingJob()) != null) {
            return jobTakePendingJob;
        }
        if (needsBase(this.currentPage)) {
            return createBaseJob(this.currentPage);
        }
        for (int i5 = this.firstVisible; i5 <= this.lastVisible; i5++) {
            if (needsBase(i5)) {
                return createBaseJob(i5);
            }
        }
        if (this.searchQuery != null && (i3 = this.searchCrawled) < (i4 = this.pageCount)) {
            int i6 = (this.searchStartPage + i3) % i4;
            return PdfDocumentRenderer.Job.search(i6, Math.min(16, Math.min(i4 - i6, i4 - i3)), this.searchQuery, this.searchGeneration);
        }
        if (isSettled()) {
            for (int i7 = this.firstVisible; i7 <= this.lastVisible; i7++) {
                PdfDocumentRenderer.Job jobCreateDetailJob = createDetailJob(i7);
                if (jobCreateDetailJob != null) {
                    return jobCreateDetailJob;
                }
            }
        }
        int i8 = this.lastVisible;
        int i9 = 1;
        if (i8 + 1 < this.pageCount && needsBase(i8 + 1)) {
            return createBaseJob(this.lastVisible + 1);
        }
        int i10 = this.firstVisible;
        if (i10 - 1 >= 0 && needsBase(i10 - 1)) {
            return createBaseJob(this.firstVisible - 1);
        }
        while (true) {
            i = this.nextMeasurePage;
            i2 = this.pageCount;
            if (i >= i2 || !this.measured[i]) {
                break;
            }
            this.nextMeasurePage = i + 1;
        }
        if (i >= i2) {
            return null;
        }
        while (i9 < 8) {
            int i11 = this.nextMeasurePage;
            if (i11 + i9 >= this.pageCount || this.measured[i11 + i9]) {
                break;
            }
            i9++;
        }
        return PdfDocumentRenderer.Job.measure(this.nextMeasurePage, i9);
    }

    private boolean needsVisibleBase() {
        if (needsBase(this.currentPage)) {
            return true;
        }
        for (int i = this.firstVisible; i <= this.lastVisible; i++) {
            if (needsBase(i)) {
                return true;
            }
        }
        return false;
    }

    private boolean needsBase(int i) {
        return i >= 0 && i < this.pageCount && !this.bases.containsKey(Integer.valueOf(i)) && !this.failedPages.get(i);
    }

    private PdfDocumentRenderer.Job createBaseJob(int i) {
        return PdfDocumentRenderer.Job.base(i, this.pageWidth, 3686400, this.renderer.supportsSearch);
    }

    private PdfDocumentRenderer.Job createDetailJob(int i) {
        Bitmap bitmap = this.bases.get(Integer.valueOf(i));
        if (bitmap == null) {
            return null;
        }
        getPageRect(i, this.rect);
        Detail detail = this.details.get(i);
        if (this.rect.width() <= bitmap.getWidth() * 1.1f) {
            if (detail != null) {
                AndroidUtilities.recycleBitmap(detail.bitmap());
                this.details.remove(i);
            }
            return null;
        }
        float fMax = Math.max(this.rect.left, 0.0f);
        RectF rectF = this.rect;
        float fWidth = (fMax - rectF.left) / rectF.width();
        float fMin = Math.min(this.rect.right, getWidth());
        RectF rectF2 = this.rect;
        float fWidth2 = (fMin - rectF2.left) / rectF2.width();
        float fMax2 = Math.max(this.rect.top, 0.0f);
        RectF rectF3 = this.rect;
        float fHeight = (fMax2 - rectF3.top) / rectF3.height();
        float fMin2 = Math.min(this.rect.bottom, getHeight());
        RectF rectF4 = this.rect;
        float fHeight2 = (fMin2 - rectF4.top) / rectF4.height();
        if (fWidth2 <= fWidth || fHeight2 <= fHeight || Math.abs(this.failedDetailWidth[i] - this.rect.width()) < 1.0f) {
            return null;
        }
        if (detail != null && Math.abs(detail.displayWidth() - this.rect.width()) < 1.0f && detail.region().left <= fWidth + 1.0E-4f && detail.region().right >= fWidth2 - 1.0E-4f && detail.region().top <= fHeight + 1.0E-4f && detail.region().bottom >= fHeight2 - 1.0E-4f) {
            return null;
        }
        float height = (getHeight() * 0.25f) / this.rect.height();
        float fMax3 = Math.max(0.0f, fHeight - height);
        float fMin3 = Math.min(1.0f, height + fHeight2);
        float fWidth3 = (fWidth2 - fWidth) * this.rect.width();
        float fHeight3 = (fMin3 - fMax3) * this.rect.height();
        if (fWidth3 * fHeight3 > 8294400.0f || fHeight3 > 4096.0f) {
            fHeight3 = (fHeight2 - fHeight) * this.rect.height();
        } else {
            fHeight2 = fMin3;
            fHeight = fMax3;
        }
        float fMin4 = Math.min(Math.min(1.0f, (float) Math.sqrt(8294400.0f / Math.max(1.0f, fWidth3 * fHeight3))), 4096.0f / Math.max(fWidth3, fHeight3));
        return PdfDocumentRenderer.Job.detail(i, this.rect.width() * fMin4, this.rect.width(), fWidth, fHeight, fWidth2, fHeight2, Math.max(1, Math.round(fWidth3 * fMin4)), Math.max(1, Math.round(fHeight3 * fMin4)));
    }
    public void onJobDone(PdfDocumentRenderer.Job job) {
        if (this.destroyed) {
            Bitmap bitmap = job.bitmap;
            if (bitmap != null) {
                bitmap.recycle();
                return;
            }
            return;
        }
        int i = job.type;
        if (i != 0) {
            if (i == 1) {
                Bitmap bitmap2 = job.bitmap;
                if (bitmap2 == null) {
                    this.failedDetailWidth[job.page] = job.displayWidth;
                } else {
                    int i2 = job.page;
                    if (i2 < this.firstVisible || i2 > this.lastVisible) {
                        AndroidUtilities.recycleBitmap(bitmap2);
                    } else {
                        Detail detail = this.details.get(i2);
                        if (detail != null) {
                            AndroidUtilities.recycleBitmap(detail.bitmap());
                        }
                        this.details.put(job.page, new Detail(job.bitmap, new RectF(job.left, job.top, job.right, job.bottom), job.displayWidth));
                    }
                }
            } else if (i != 2) {
                if (i != 3) {
                    if (i == 4) {
                        this.textSelection.onJobDone(job);
                    }
                } else if (job.generation == this.searchGeneration && this.searchQuery != null) {
                    onSearchJobDone(job);
                }
            } else if (applyPageSizes(job)) {
                relayoutKeepingAnchor();
            }
        } else if (job.bitmap == null) {
            this.failedPages.put(job.page, true);
        } else {
            applyPageSize(job.page, job.pageWidth, job.pageHeight, true);
            putBase(job.page, job.bitmap);
            ArrayList<PdfDocumentRenderer.Link> arrayList = job.links;
            if (arrayList != null) {
                this.links.put(job.page, arrayList);
            }
        }
        if (this.inFlight == job) {
            this.inFlight = null;
        }
        this.lastJobType = job.type;
        invalidate();
        scheduleNext();
    }

    private void onSearchJobDone(PdfDocumentRenderer.Job job) {
        boolean zApplyPageSizes = applyPageSizes(job);
        int iMax = Math.max(1, job.measuredCount);
        int i = 0;
        boolean z = false;
        while (i < iMax) {
            ArrayList<ArrayList<RectF[]>> arrayList = job.matches;
            ArrayList<RectF[]> arrayList2 = (arrayList == null || i >= arrayList.size()) ? null : job.matches.get(i);
            if (arrayList2 != null && !arrayList2.isEmpty()) {
                this.searchMatches.put(job.page + i, arrayList2);
                this.searchTotal += arrayList2.size();
                if (this.currentMatchPage < 0) {
                    this.currentMatchPage = job.page + i;
                    this.currentMatchIndex = 0;
                    z = true;
                }
            }
            i++;
        }
        this.searchCrawled += iMax;
        this.searchVersion++;
        if (zApplyPageSizes) {
            relayoutKeepingAnchor();
        }
        if (z) {
            scrollToCurrentMatch();
        }
        notifySearchUpdated();
    }

    private boolean applyPageSizes(PdfDocumentRenderer.Job job) {
        int iMax = Math.max(1, job.measuredCount);
        boolean zApplyPageSize = false;
        for (int i = 0; i < iMax; i++) {
            float[] fArr = job.sizes;
            float f = 0.0f;
            float f2 = fArr != null ? fArr[i * 2] : 0.0f;
            if (fArr != null) {
                f = fArr[(i * 2) + 1];
            }
            zApplyPageSize |= applyPageSize(job.page + i, f2, f, false);
        }
        return zApplyPageSize;
    }

    private boolean applyPageSize(int i, float f, float f2, boolean z) {
        this.measured[i] = true;
        if (f <= 0.0f || f2 <= 0.0f) {
            return false;
        }
        float f3 = f2 / f;
        if (Math.abs(this.pageAspect[i] - f3) < 5.0E-4f) {
            return false;
        }
        this.pageAspect[i] = f3;
        Detail detail = this.details.get(i);
        if (detail != null) {
            AndroidUtilities.recycleBitmap(detail.bitmap());
            this.details.remove(i);
        }
        if (z) {
            relayoutKeepingAnchor();
        }
        return true;
    }

    private void putBase(int i, Bitmap bitmap) {
        if (i >= this.firstVisible && i <= this.lastVisible) {
            this.baseAppearTime.put(i, SystemClock.uptimeMillis());
        } else {
            this.baseAppearTime.delete(i);
        }
        Bitmap bitmapPut = this.bases.put(Integer.valueOf(i), bitmap);
        if (bitmapPut != null) {
            this.basesBytes -= (long) bitmapPut.getByteCount();
            AndroidUtilities.recycleBitmap(bitmapPut);
        }
        this.basesBytes += (long) bitmap.getByteCount();
        Iterator<Map.Entry<Integer, Bitmap>> it = this.bases.entrySet().iterator();
        while (this.basesBytes > this.basesBudget && it.hasNext()) {
            Map.Entry<Integer, Bitmap> next = it.next();
            int iIntValue = next.getKey().intValue();
            if (iIntValue < this.firstVisible - 1 || iIntValue > this.lastVisible + 1) {
                this.basesBytes -= (long) next.getValue().getByteCount();
                AndroidUtilities.recycleBitmap(next.getValue());
                it.remove();
            }
        }
    }

    private void clearBitmaps() {
        ArrayList arrayList = new ArrayList(this.bases.values());
        for (int i = 0; i < this.details.size(); i++) {
            arrayList.add(this.details.valueAt(i).bitmap());
        }
        this.bases.clear();
        this.details.clear();
        this.baseAppearTime.clear();
        this.basesBytes = 0L;
        this.failedPages.clear();
        AndroidUtilities.recycleBitmaps(arrayList);
    }
}

package org.telegram.ui.Pdf.ui;

import android.R;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Magnifier;
import androidx.core.graphics.ColorUtils;
import org.telegram.ui.Pdf.PdfDocumentRenderer;
import java.util.ArrayList;
import kotlin.jvm.internal.LongCompanionObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.FloatingActionMode;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
final class PdfTextSelection {
    private final ActionMode.Callback2 actionCallback;
    private FloatingActionMode actionMode;
    private boolean actionsHiddenByViewport;
    private boolean actionsShown;
    private final Runnable autoScrollRunnable;
    private long autoScrollTime;
    private boolean autoScrolling;
    private final PdfPagesView.Delegate delegate;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean dragPending;
    private float dragX;
    private float dragY;
    private int draggingHandle;
    private int fixedIndex;
    private int fixedPage;
    private float fixedX;
    private float fixedY;
    private final SparseArray<PdfDocumentRenderer.SelectedPage> fullPages;
    private int generation;
    private final Paint handlePaint;
    private final Path handlePath;
    private final AnimatedFloat handlesScale;
    private final Runnable hideActionsRunnable;
    private boolean longPressDrag;
    private float longPressX;
    private float longPressY;
    private Magnifier magnifier;
    private int movingPage;
    private final RectF pageRect;
    private SparseArray<PdfDocumentRenderer.SelectedPage> pages;
    private PdfDocumentRenderer.Job pendingJob;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint selectionPaint;
    private final RectF startArea;
    private boolean started;
    private final RectF stopArea;
    private String text;
    private final int touchSlop;
    private String translateFromLanguage;
    private int version;
    private final PdfPagesView view;
    public class AnonymousClass2 extends ActionMode.Callback2 {
        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
        }

        public AnonymousClass2() {
        }

        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            menu.add(0, R.id.copy, 0, R.string.copy);
            menu.add(0, 1, 1, LocaleController.getString(org.telegram.messenger.R.string.ShareFile));
            menu.add(0, 2, 2, LocaleController.getString(org.telegram.messenger.R.string.TranslateMessage));
            menu.add(0, R.id.selectAll, 3, R.string.selectAll);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, final Menu menu) {
            PdfTextSelection.this.translateFromLanguage = null;
            PdfTextSelection.this.updateTranslateItem(menu);
            if (PdfTextSelection.this.text == null || !LanguageDetector.hasSupport()) {
                return true;
            }
            LanguageDetector.detectLanguage(PdfTextSelection.this.text, str -> lambda_onPrepareActionMode_0(menu, str), exc -> lambda_onPrepareActionMode_1(menu, exc));
            return true;
        }
        public /* synthetic */ void lambda_onPrepareActionMode_0(Menu menu, String str) {
            PdfTextSelection.this.translateFromLanguage = str;
            PdfTextSelection.this.updateTranslateItem(menu);
        }
        public /* synthetic */ void lambda_onPrepareActionMode_1(Menu menu, Exception exc) {
            FileLog.e(exc);
            PdfTextSelection.this.translateFromLanguage = null;
            PdfTextSelection.this.updateTranslateItem(menu);
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            if (!PdfTextSelection.this.isActive()) {
                return true;
            }
            int itemId = menuItem.getItemId();
            if (itemId == 16908321) {
                AndroidUtilities.addToClipboard(PdfTextSelection.this.text);
                PdfTextSelection.this.delegate.onTextCopied();
                PdfTextSelection.this.clear();
            } else if (itemId == 1) {
                PdfTextSelection.this.delegate.onShareText(PdfTextSelection.this.text);
                PdfTextSelection.this.clear();
            } else if (itemId == 2) {
                PdfPagesView.Delegate delegate = PdfTextSelection.this.delegate;
                String str = PdfTextSelection.this.text;
                String str2 = PdfTextSelection.this.translateFromLanguage;
                final PdfTextSelection pdfTextSelection = PdfTextSelection.this;
                delegate.onTranslateText(str, str2, () -> pdfTextSelection.showActions());
                PdfTextSelection.this.hideActions();
            } else if (itemId == 16908319) {
                int iKeyAt = PdfTextSelection.this.pages.keyAt(0);
                int iKeyAt2 = PdfTextSelection.this.pages.keyAt(PdfTextSelection.this.pages.size() - 1);
                PdfTextSelection pdfTextSelection2 = PdfTextSelection.this;
                pdfTextSelection2.pendingJob = PdfDocumentRenderer.Job.select(pdfTextSelection2.generation, iKeyAt, 0, 0.0f, 0.0f, iKeyAt2, Integer.MAX_VALUE, 0.0f, 0.0f, PdfTextSelection.this.getMissingFullPages(iKeyAt, iKeyAt2));
                PdfTextSelection.this.hideActions();
                PdfTextSelection.this.view.scheduleNext();
            }
            return true;
        }

        @Override
        public void onGetContentRect(ActionMode actionMode, View view, Rect rect) {
            if (PdfTextSelection.this.isActive()) {
                float topInset = PdfTextSelection.this.view.getTopInset();
                float height = PdfTextSelection.this.view.getHeight() - PdfTextSelection.this.view.getBottomInset();
                float fMax = -3.4028235E38f;
                float fMin = Float.MAX_VALUE;
                float fMin2 = Float.MAX_VALUE;
                int i = 0;
                float fMax2 = -3.4028235E38f;
                while (i < 2 && fMin > fMax) {
                    for (int i2 = 0; i2 < PdfTextSelection.this.pages.size(); i2++) {
                        PdfDocumentRenderer.SelectedPage selectedPage = (PdfDocumentRenderer.SelectedPage) PdfTextSelection.this.pages.valueAt(i2);
                        PdfTextSelection.this.view.getPageRect(selectedPage.page, PdfTextSelection.this.pageRect);
                        RectF[] rectFArr = selectedPage.rects;
                        int length = rectFArr.length;
                        int i3 = 0;
                        while (i3 < length) {
                            RectF rectF = rectFArr[i3];
                            float fHeight = PdfTextSelection.this.pageRect.top + (rectF.top * PdfTextSelection.this.pageRect.height());
                            float fHeight2 = PdfTextSelection.this.pageRect.top + (rectF.bottom * PdfTextSelection.this.pageRect.height());
                            if (i != 0 || (fHeight2 >= topInset && fHeight <= height)) {
                                fMin = Math.min(fMin, PdfTextSelection.this.pageRect.left + (PdfTextSelection.this.pageRect.width() * rectF.left));
                                fMin2 = Math.min(fMin2, fHeight);
                                fMax = Math.max(fMax, PdfTextSelection.this.pageRect.left + (rectF.right * PdfTextSelection.this.pageRect.width()));
                                fMax2 = Math.max(fMax2, fHeight2);
                            }
                            i3++;
                            i = i;
                        }
                    }
                    i++;
                }
                float fClamp = Utilities.clamp(fMin2, height, topInset);
                rect.set(Math.round(Utilities.clamp(fMin, PdfTextSelection.this.view.getWidth(), 0.0f)), Math.round(fClamp), Math.round(Utilities.clamp(fMax, PdfTextSelection.this.view.getWidth(), 0.0f)), Math.round(Utilities.clamp(fMax2, height, fClamp)) + 1);
            }
        }
    }

    public PdfTextSelection(PdfPagesView pdfPagesView, PdfPagesView.Delegate delegate, Theme.ResourcesProvider resourcesProvider) {
        Paint paint = new Paint(1);
        this.selectionPaint = paint;
        this.handlePaint = new Paint(1);
        this.handlePath = new Path();
        this.pageRect = new RectF();
        this.startArea = new RectF();
        this.stopArea = new RectF();
        this.pages = new SparseArray<>();
        this.fullPages = new SparseArray<>();
        this.draggingHandle = 0;
        this.movingPage = -1;
        this.fixedPage = -1;
        this.fixedIndex = -1;
        this.autoScrollRunnable = () -> autoScroll();
        this.hideActionsRunnable = new Runnable() {
            @Override
            public void run() {
                if (PdfTextSelection.this.actionMode == null || PdfTextSelection.this.actionsShown) {
                    return;
                }
                PdfTextSelection.this.actionMode.hide(LongCompanionObject.MAX_VALUE);
                AndroidUtilities.runOnUIThread(this, 1000L);
            }
        };
        this.actionCallback = new AnonymousClass2();
        this.view = pdfPagesView;
        this.delegate = delegate;
        this.resourcesProvider = resourcesProvider;
        this.touchSlop = ViewConfiguration.get(pdfPagesView.getContext()).getScaledTouchSlop();
        this.handlesScale = new AnimatedFloat((() -> pdfPagesView.invalidateOverlay()), 0L, 250L, CubicBezierInterpolator.EASE_OUT_QUINT);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        updateColors();
    }

    public void updateColors() {
        int color = Theme.getColor(Theme.key_chat_TextSelectionCursor, this.resourcesProvider);
        this.selectionPaint.setColor(ColorUtils.blendARGB(-1, (-16777216) | color, 0.35f));
        this.handlePaint.setColor(color);
    }

    public boolean isActive() {
        return this.pages.size() > 0;
    }

    public boolean isDragging() {
        return this.draggingHandle != 0;
    }

    public boolean isLongPressDrag() {
        return this.longPressDrag;
    }

    public int getVersion() {
        return this.version;
    }

    public PdfDocumentRenderer.Job takePendingJob() {
        PdfDocumentRenderer.Job job = this.pendingJob;
        this.pendingJob = null;
        return job;
    }

    public void startAt(int i, float f, float f2) {
        clear();
        this.view.getPageRect(i, this.pageRect);
        RectF rectF = this.pageRect;
        float fClamp = Utilities.clamp((f - rectF.left) / rectF.width(), 1.0f, 0.0f);
        RectF rectF2 = this.pageRect;
        float fClamp2 = Utilities.clamp((f2 - rectF2.top) / rectF2.height(), 1.0f, 0.0f);
        this.started = true;
        this.movingPage = i;
        this.pendingJob = PdfDocumentRenderer.Job.select(this.generation, i, -1, fClamp, fClamp2, i, -1, fClamp, fClamp2, null);
        this.draggingHandle = 2;
        this.longPressDrag = true;
        this.longPressX = f;
        this.longPressY = f2;
        this.dragX = f;
        this.dragY = f2;
        this.dragOffsetX = 0.0f;
        this.dragOffsetY = 0.0f;
        this.view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS, 1);
        this.view.scheduleNext();
    }

    public boolean onDown(float f, float f2) {
        if (!isActive()) {
            return false;
        }
        if (this.startArea.contains(f, f2)) {
            this.draggingHandle = 1;
        } else {
            if (!this.stopArea.contains(f, f2)) {
                return false;
            }
            this.draggingHandle = 2;
        }
        int i = this.draggingHandle;
        SparseArray<PdfDocumentRenderer.SelectedPage> sparseArray = this.pages;
        PdfDocumentRenderer.SelectedPage selectedPageValueAt = i == 1 ? sparseArray.valueAt(0) : sparseArray.valueAt(sparseArray.size() - 1);
        this.view.getPageRect(selectedPageValueAt.page, this.pageRect);
        int i2 = this.draggingHandle;
        RectF[] rectFArr = selectedPageValueAt.rects;
        RectF rectF = i2 == 1 ? rectFArr[0] : rectFArr[rectFArr.length - 1];
        RectF rectF2 = this.pageRect;
        float fWidth = rectF2.left + ((i2 == 1 ? rectF.left : rectF.right) * rectF2.width());
        float fCenterY = this.pageRect.top + (rectF.centerY() * this.pageRect.height());
        this.dragOffsetX = fWidth - f;
        this.dragOffsetY = fCenterY - f2;
        this.dragX = f;
        this.dragY = f2;
        this.movingPage = selectedPageValueAt.page;
        this.longPressDrag = false;
        captureFixedBoundary(this.draggingHandle == 1);
        hideActions();
        return true;
    }

    public void onMove(float f, float f2) {
        if (!this.longPressDrag || Math.hypot(f - this.longPressX, f2 - this.longPressY) >= this.touchSlop) {
            this.longPressX = Float.NaN;
            this.dragX = f;
            this.dragY = f2;
            startAutoScroll();
            if (!isActive()) {
                this.dragPending = true;
            } else {
                requestDragSelection();
                showMagnifier();
            }
        }
    }

    public void onUp() {
        this.draggingHandle = 0;
        this.longPressDrag = false;
        this.dragPending = false;
        stopAutoScroll();
        hideMagnifier();
        showActions();
        this.view.invalidateOverlay();
    }

    public boolean clear() {
        boolean z = this.started;
        this.generation++;
        this.version++;
        this.started = false;
        this.pages = new SparseArray<>();
        this.fullPages.clear();
        this.text = null;
        this.movingPage = -1;
        this.fixedPage = -1;
        this.fixedIndex = -1;
        this.pendingJob = null;
        this.draggingHandle = 0;
        this.longPressDrag = false;
        this.dragPending = false;
        stopAutoScroll();
        this.startArea.setEmpty();
        this.stopArea.setEmpty();
        hideMagnifier();
        finishActions();
        this.view.invalidate();
        this.view.invalidateOverlay();
        return z;
    }

    public void onViewportChanged() {
        if (isActive() && this.draggingHandle == 0 && this.actionsShown) {
            hideActions();
            this.actionsHiddenByViewport = true;
        }
    }

    public void onSettled() {
        if (this.actionsHiddenByViewport) {
            showActions();
        }
    }

    public void onJobDone(PdfDocumentRenderer.Job job) {
        if (job.generation == this.generation && this.started) {
            SparseArray<PdfDocumentRenderer.SelectedPage> sparseArray = new SparseArray<>();
            ArrayList<PdfDocumentRenderer.SelectedPage> arrayList = job.selectedPages;
            if (arrayList != null) {
                int size = arrayList.size();
                int i = 0;
                while (i < size) {
                    PdfDocumentRenderer.SelectedPage selectedPage = arrayList.get(i);
                    i++;
                    PdfDocumentRenderer.SelectedPage selectedPage2 = selectedPage;
                    int i2 = selectedPage2.page;
                    if (i2 > job.page && i2 < job.stopPage) {
                        this.fullPages.put(i2, selectedPage2);
                    } else if (selectedPage2.rects.length > 0) {
                        sparseArray.put(i2, selectedPage2);
                    }
                }
                for (int i3 = job.page + 1; i3 < job.stopPage; i3++) {
                    PdfDocumentRenderer.SelectedPage selectedPage3 = this.fullPages.get(i3);
                    if (selectedPage3 != null && selectedPage3.rects.length > 0) {
                        sparseArray.put(i3, selectedPage3);
                    }
                }
            }
            if (sparseArray.size() == 0) {
                if (isActive()) {
                    return;
                }
                clear();
                return;
            }
            String strJoinText = joinText(sparseArray);
            boolean zIsActive = isActive();
            if (zIsActive && this.draggingHandle != 0 && !this.autoScrolling && !strJoinText.equals(this.text)) {
                AndroidUtilities.vibrateCursor(this.view);
            }
            this.text = strJoinText;
            this.pages = sparseArray;
            this.version++;
            if (!zIsActive) {
                this.handlesScale.set(0.0f, true);
                captureFixedBoundary(false);
                if (this.dragPending) {
                    this.dragPending = false;
                    requestDragSelection();
                }
            }
            if (this.draggingHandle == 0) {
                showActions();
            } else {
                showMagnifier();
            }
            this.view.invalidate();
            this.view.invalidateOverlay();
        }
    }

    public void drawHighlight(Canvas canvas, int i, RectF rectF) {
        PdfDocumentRenderer.SelectedPage selectedPage = this.pages.get(i);
        if (selectedPage == null) {
            return;
        }
        for (RectF rectF2 : selectedPage.rects) {
            canvas.drawRect(rectF.left + (rectF2.left * rectF.width()), rectF.top + (rectF2.top * rectF.height()), rectF.left + (rectF2.right * rectF.width()), rectF.top + (rectF2.bottom * rectF.height()), this.selectionPaint);
        }
    }

    public void drawHandles(Canvas canvas) {
        this.startArea.setEmpty();
        this.stopArea.setEmpty();
        if (isActive()) {
            float f = this.handlesScale.set(1.0f);
            PdfDocumentRenderer.SelectedPage selectedPageValueAt = this.pages.valueAt(0);
            RectF rectF = selectedPageValueAt.rects[0];
            this.view.getPageRect(selectedPageValueAt.page, this.pageRect);
            RectF rectF2 = this.pageRect;
            float fWidth = rectF2.left + (rectF.left * rectF2.width());
            RectF rectF3 = this.pageRect;
            drawHandle(canvas, fWidth, rectF3.top + (rectF.bottom * rectF3.height()), true, f, this.startArea);
            SparseArray<PdfDocumentRenderer.SelectedPage> sparseArray = this.pages;
            PdfDocumentRenderer.SelectedPage selectedPageValueAt2 = sparseArray.valueAt(sparseArray.size() - 1);
            RectF[] rectFArr = selectedPageValueAt2.rects;
            RectF rectF4 = rectFArr[rectFArr.length - 1];
            this.view.getPageRect(selectedPageValueAt2.page, this.pageRect);
            RectF rectF5 = this.pageRect;
            float fWidth2 = rectF5.left + (rectF4.right * rectF5.width());
            RectF rectF6 = this.pageRect;
            drawHandle(canvas, fWidth2, rectF6.top + (rectF4.bottom * rectF6.height()), false, f, this.stopArea);
        }
    }

    private void drawHandle(Canvas canvas, float f, float f2, boolean z, float f3, RectF rectF) {
        if (f2 < this.view.getTopInset() || f2 > this.view.getHeight() - this.view.getBottomInset()) {
            return;
        }
        float fDp = AndroidUtilities.dp(22.0f);
        if (z) {
            f -= fDp;
        }
        canvas.save();
        canvas.translate(f, f2);
        canvas.scale(f3, f3, z ? fDp : 0.0f, 0.0f);
        this.handlePath.rewind();
        Path path = this.handlePath;
        float f4 = fDp / 2.0f;
        Path.Direction direction = Path.Direction.CCW;
        path.addCircle(f4, f4, f4, direction);
        this.handlePath.addRect(z ? f4 : 0.0f, 0.0f, z ? fDp : f4, f4, direction);
        canvas.drawPath(this.handlePath, this.handlePaint);
        canvas.restore();
        rectF.set(f, f2 - fDp, f + fDp, f2 + fDp);
        rectF.inset(-AndroidUtilities.dp(8.0f), -AndroidUtilities.dp(8.0f));
    }

    private static String joinText(SparseArray<PdfDocumentRenderer.SelectedPage> sparseArray) {
        if (sparseArray.size() == 1) {
            return sparseArray.valueAt(0).text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sparseArray.size(); i++) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                sb.append('\n');
            }
            sb.append(sparseArray.valueAt(i).text);
        }
        return sb.toString();
    }
    public int[] getMissingFullPages(int i, int i2) {
        int i3 = i + 1;
        int i4 = 0;
        int i5 = 0;
        for (int i6 = i3; i6 < i2; i6++) {
            if (this.fullPages.get(i6) == null) {
                i5++;
            }
        }
        if (i5 == 0) {
            return null;
        }
        int[] iArr = new int[i5];
        while (i3 < i2) {
            if (this.fullPages.get(i3) == null) {
                iArr[i4] = i3;
                i4++;
            }
            i3++;
        }
        return iArr;
    }

    private void captureFixedBoundary(boolean z) {
        int i;
        SparseArray<PdfDocumentRenderer.SelectedPage> sparseArray = this.pages;
        PdfDocumentRenderer.SelectedPage selectedPageValueAt = z ? sparseArray.valueAt(sparseArray.size() - 1) : sparseArray.valueAt(0);
        RectF[] rectFArr = selectedPageValueAt.rects;
        RectF rectF = z ? rectFArr[rectFArr.length - 1] : rectFArr[0];
        float f = z ? rectF.right : rectF.left;
        float f2 = Float.MAX_VALUE;
        int i2 = -1;
        for (int i3 = 0; i3 < 2; i3++) {
            if (!Float.isNaN(selectedPageValueAt.boundaryX[i3])) {
                float fAbs = Math.abs(selectedPageValueAt.boundaryX[i3] - f) + Math.abs(selectedPageValueAt.boundaryY[i3] - rectF.centerY());
                if (fAbs < f2) {
                    i2 = i3;
                    f2 = fAbs;
                }
            }
        }
        int i4 = selectedPageValueAt.page;
        this.fixedPage = i4;
        this.fixedIndex = -1;
        if (i2 >= 0) {
            this.fixedX = selectedPageValueAt.boundaryX[i2];
            this.fixedY = selectedPageValueAt.boundaryY[i2];
            return;
        }
        int[] iArr = selectedPageValueAt.boundaryIndex;
        int i5 = iArr[0];
        if (i5 >= 0 && (i = iArr[1]) >= 0) {
            this.fixedIndex = z ? Math.max(i5, i) : Math.min(i5, i);
            return;
        }
        this.view.getPageRect(i4, this.pageRect);
        float fMin = Math.min(rectF.width() / 2.0f, AndroidUtilities.dp(2.0f) / this.pageRect.width());
        this.fixedX = z ? f - fMin : f + fMin;
        this.fixedY = rectF.centerY();
    }

    private void requestDragSelection() {
        float f = this.dragX + this.dragOffsetX;
        float f2 = this.dragY + this.dragOffsetY;
        int pageAtY = this.view.getPageAtY(f2);
        this.movingPage = pageAtY;
        this.view.getPageRect(pageAtY, this.pageRect);
        RectF rectF = this.pageRect;
        float fClamp = Utilities.clamp((f - rectF.left) / rectF.width(), 1.0f, 0.0f);
        RectF rectF2 = this.pageRect;
        float fClamp2 = Utilities.clamp((f2 - rectF2.top) / rectF2.height(), 1.0f, 0.0f);
        int i = this.movingPage;
        int i2 = this.fixedPage;
        if (i > i2) {
            this.pendingJob = PdfDocumentRenderer.Job.select(this.generation, i2, this.fixedIndex, this.fixedX, this.fixedY, i, -1, fClamp, fClamp2, getMissingFullPages(i2, i));
        } else if (i < i2 || this.draggingHandle == 1) {
            this.pendingJob = PdfDocumentRenderer.Job.select(this.generation, i, -1, fClamp, fClamp2, i2, this.fixedIndex, this.fixedX, this.fixedY, getMissingFullPages(i, i2));
        } else {
            this.pendingJob = PdfDocumentRenderer.Job.select(this.generation, i2, this.fixedIndex, this.fixedX, this.fixedY, i, -1, fClamp, fClamp2, null);
        }
        this.view.scheduleNext();
    }

    private void startAutoScroll() {
        if (this.autoScrolling || getAutoScrollSpeed() == 0.0f) {
            return;
        }
        this.autoScrolling = true;
        this.autoScrollTime = SystemClock.uptimeMillis();
        this.view.postOnAnimation(this.autoScrollRunnable);
    }

    private void stopAutoScroll() {
        this.autoScrolling = false;
        this.view.removeCallbacks(this.autoScrollRunnable);
    }

    private float getAutoScrollSpeed() {
        float fMin;
        float fDp = AndroidUtilities.dp(64.0f);
        float topInset = this.view.getTopInset() + fDp;
        float height = (this.view.getHeight() - this.view.getBottomInset()) - fDp;
        float f = this.dragY;
        if (f < topInset) {
            fMin = -Math.min(1.0f, (topInset - f) / fDp);
        } else {
            if (f <= height) {
                return 0.0f;
            }
            fMin = Math.min(1.0f, (f - height) / fDp);
        }
        return Math.signum(fMin) * AndroidUtilities.lerp(AndroidUtilities.dp(120.0f), AndroidUtilities.dp(1600.0f), Math.abs(fMin));
    }
    public void autoScroll() {
        float autoScrollSpeed = this.draggingHandle == 0 ? 0.0f : getAutoScrollSpeed();
        long jUptimeMillis = SystemClock.uptimeMillis();
        float fMin = (Math.min(32L, jUptimeMillis - this.autoScrollTime) * autoScrollSpeed) / 1000.0f;
        this.autoScrollTime = jUptimeMillis;
        if (autoScrollSpeed == 0.0f || !this.view.scrollForSelection(fMin)) {
            this.autoScrolling = false;
            return;
        }
        if (isActive()) {
            requestDragSelection();
            showMagnifier();
        }
        this.view.postOnAnimation(this.autoScrollRunnable);
    }

    private void showMagnifier() {
        if (Build.VERSION.SDK_INT < 28 || !isActive() || this.draggingHandle == 0) {
            return;
        }
        float f = this.dragY + this.dragOffsetY;
        PdfDocumentRenderer.SelectedPage selectedPage = this.pages.get(this.movingPage);
        if (selectedPage != null) {
            this.view.getPageRect(selectedPage.page, this.pageRect);
            float fCenterY = this.pageRect.top + (selectedPage.rects[0].centerY() * this.pageRect.height());
            float f2 = this.pageRect.top;
            RectF[] rectFArr = selectedPage.rects;
            float fCenterY2 = f2 + (rectFArr[rectFArr.length - 1].centerY() * this.pageRect.height());
            f = Math.abs(f - fCenterY) < Math.abs(f - fCenterY2) ? fCenterY : fCenterY2;
        }
        float fClamp = Utilities.clamp(this.dragX + this.dragOffsetX, this.view.getWidth(), 0.0f);
        if (this.magnifier == null) {
            this.magnifier = createMagnifier();
        }
        this.magnifier.show(fClamp, f);
    }

    private Magnifier createMagnifier() {
        if (Build.VERSION.SDK_INT >= 29) {
            return new Magnifier.Builder(this.view).build();
        }
        return new Magnifier(this.view);
    }

    private void hideMagnifier() {
        Magnifier magnifier;
        if (Build.VERSION.SDK_INT < 28 || (magnifier = this.magnifier) == null) {
            return;
        }
        magnifier.dismiss();
        this.magnifier = null;
    }
    public void showActions() {
        if (isActive() && this.draggingHandle == 0) {
            if (this.actionMode == null) {
                FloatingActionMode floatingActionMode = new FloatingActionMode(this.view.getContext(), this.actionCallback, this.view, new FloatingToolbar(this.view.getContext(), this.view, 1, this.resourcesProvider));
                this.actionMode = floatingActionMode;
                this.actionCallback.onCreateActionMode(floatingActionMode, floatingActionMode.getMenu());
            }
            this.actionsHiddenByViewport = false;
            if (!this.actionsShown) {
                ActionMode.Callback2 callback2 = this.actionCallback;
                FloatingActionMode floatingActionMode2 = this.actionMode;
                callback2.onPrepareActionMode(floatingActionMode2, floatingActionMode2.getMenu());
            }
            this.actionMode.updateViewLocationInWindow();
            this.actionMode.invalidateContentRect();
            if (this.actionsShown) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(this.hideActionsRunnable);
            this.actionsShown = true;
            this.actionMode.hide(1L);
        }
    }
    public void hideActions() {
        if (this.actionMode != null && this.actionsShown) {
            this.actionsShown = false;
            this.hideActionsRunnable.run();
        }
        this.actionsShown = false;
    }

    private void finishActions() {
        AndroidUtilities.cancelRunOnUIThread(this.hideActionsRunnable);
        this.actionsShown = false;
        this.actionsHiddenByViewport = false;
        FloatingActionMode floatingActionMode = this.actionMode;
        if (floatingActionMode != null) {
            floatingActionMode.finish();
            this.actionMode = null;
        }
    }
    public void updateTranslateItem(Menu menu) {
        MenuItem menuItemFindItem = menu.findItem(2);
        if (menuItemFindItem != null) {
            String str = this.translateFromLanguage;
            menuItemFindItem.setVisible(((str == null || org.telegram.ui.RestrictedLanguagesSelectActivity.getRestrictedLanguages().contains(str)) && LanguageDetector.hasSupport()) ? false : true);
        }
    }
}

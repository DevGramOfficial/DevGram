package org.telegram.ui.Pdf;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;
import android.graphics.pdf.content.PdfPageGotoLinkContent;
import android.graphics.pdf.content.PdfPageLinkContent;
import android.graphics.pdf.content.PdfPageTextContent;
import android.graphics.pdf.models.PageMatchBounds;
import android.graphics.pdf.models.selection.PageSelection;
import android.graphics.pdf.models.selection.SelectionBoundary;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.ext.SdkExtensions;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
public final class PdfDocumentRenderer {
    private boolean closed;
    public final float firstPageHeight;
    public final float firstPageWidth;
    private final Matrix matrix = new Matrix();
    public final int pageCount;
    private final DispatchQueue queue;
    private final DocumentSource source;
    public final boolean supportsSearch;

    public interface DocumentSource {
        void close();

        int getPageCount();

        PageSource openPage(int i);

        boolean supportsText();
    }

    public interface PageSource extends AutoCloseable {
        @Override
        void close();

        List<PdfPageGotoLinkContent> getGotoLinks();

        int getHeight();

        List<PdfPageLinkContent> getLinkContents();

        int getWidth();

        void render(Bitmap bitmap, Matrix matrix);

        List<PageMatchBounds> searchText(String str);

        PageSelection selectContent(SelectionBoundary selectionBoundary, SelectionBoundary selectionBoundary2);
    }

    public static final class Job {
        public Bitmap bitmap;
        public float bottom;
        public final int count;
        public float displayWidth;
        public int[] fullPages;
        public int generation;
        public int height;
        public float left;
        public ArrayList<Link> links;
        public boolean loadLinks;
        public ArrayList<ArrayList<RectF[]>> matches;
        public int maxPixels;
        public int measuredCount;
        public final int page;
        public float pageHeight;
        public float pageWidth;
        public float pageWidthPx;
        public String query;
        public float right;
        public ArrayList<SelectedPage> selectedPages;
        public float[] sizes;
        public float startX;
        public float startY;
        public int stopPage;
        public float stopX;
        public float stopY;
        public int targetWidth;
        public float top;
        public final int type;
        public int width;
        public int startIndex = -1;
        public int stopIndex = -1;

        private Job(int i, int i2, int i3) {
            this.type = i;
            this.page = i2;
            this.count = i3;
        }

        public static Job base(int i, int i2, int i3, boolean z) {
            Job job = new Job(0, i, 1);
            job.targetWidth = i2;
            job.maxPixels = i3;
            job.loadLinks = z;
            return job;
        }

        public static Job detail(int i, float f, float f2, float f3, float f4, float f5, float f6, int i2, int i3) {
            Job job = new Job(1, i, 1);
            job.pageWidthPx = f;
            job.displayWidth = f2;
            job.left = f3;
            job.top = f4;
            job.right = f5;
            job.bottom = f6;
            job.width = i2;
            job.height = i3;
            return job;
        }

        public static Job measure(int i, int i2) {
            return new Job(2, i, i2);
        }

        public static Job search(int i, int i2, String str, int i3) {
            Job job = new Job(3, i, i2);
            job.query = str;
            job.generation = i3;
            return job;
        }

        public static Job select(int i, int i2, int i3, float f, float f2, int i4, int i5, float f3, float f4, int[] iArr) {
            Job job = new Job(4, i2, (i4 - i2) + 1);
            job.generation = i;
            job.startIndex = i3;
            job.startX = f;
            job.startY = f2;
            job.stopPage = i4;
            job.stopIndex = i5;
            job.stopX = f3;
            job.stopY = f4;
            job.fullPages = iArr;
            return job;
        }
    }

    public static final class SelectedPage {
        public final int[] boundaryIndex = {-1, -1};
        public final float[] boundaryX = {Float.NaN, Float.NaN};
        public final float[] boundaryY = {Float.NaN, Float.NaN};
        public final int page;
        public final RectF[] rects;
        public final String text;

        public SelectedPage(int i, String str, RectF[] rectFArr) {
            this.page = i;
            this.text = str;
            this.rects = rectFArr;
        }
    }

    public static final class Link {
        public final RectF bounds;
        public final int page;
        public final String url;
        public final float y;

        public Link(RectF rectF, String str, int i, float f) {
            this.bounds = rectF;
            this.url = str;
            this.page = i;
            this.y = f;
        }
    }

    private PdfDocumentRenderer(DispatchQueue dispatchQueue, DocumentSource documentSource, float f, float f2) {
        this.queue = dispatchQueue;
        this.source = documentSource;
        this.pageCount = documentSource.getPageCount();
        this.firstPageWidth = f;
        this.firstPageHeight = f2;
        this.supportsSearch = documentSource.supportsText();
    }

    public static void open(final File file, final Utilities.Callback2<PdfDocumentRenderer, Throwable> callback2) {
        final DispatchQueue dispatchQueue = new DispatchQueue("pdfRenderer");
        dispatchQueue.postRunnable(() -> PdfDocumentRenderer.m1551$r8$lambda$mSblVtdzpNg3oM9KLrkzAT74oI(file, dispatchQueue, callback2));
    }
    public static /* synthetic */ void m1551$r8$lambda$mSblVtdzpNg3oM9KLrkzAT74oI(File file, DispatchQueue dispatchQueue, final Utilities.Callback2<PdfDocumentRenderer, Throwable> callback2) {
        DocumentSource source = null;
        try {
            source = createSource(file);
            if (source.getPageCount() <= 0) {
                throw new IOException("pdf has no pages");
            }
            PageSource page = source.openPage(0);
            float width, height;
            try {
                width = Math.max(1, page.getWidth());
                height = Math.max(1, page.getHeight());
            } finally {
                try {
                    page.close();
                } catch (Throwable ignore) {
                }
            }
            final PdfDocumentRenderer renderer = new PdfDocumentRenderer(dispatchQueue, source, width, height);
            AndroidUtilities.runOnUIThread(() -> callback2.run(renderer, null));
        } catch (Throwable t) {
            if (source != null) {
                try {
                    source.close();
                } catch (Throwable ignore) {
                }
            }
            dispatchQueue.recycle();
            AndroidUtilities.runOnUIThread(() -> callback2.run(null, t));
        }
    }

    private static DocumentSource createSource(File file) throws IOException {
        int i = Build.VERSION.SDK_INT;
        if (i >= 31 && i < 35 && SdkExtensions.getExtensionVersion(31) >= 13) {
            ParcelFileDescriptor parcelFileDescriptorOpen = ParcelFileDescriptor.open(file, 268435456);
            try {
                return new PreVDocumentSource(parcelFileDescriptorOpen);
            } catch (IOException | RuntimeException e) {
                FileLog.e(e);
                closeQuietly(parcelFileDescriptorOpen);
            }
        }
        ParcelFileDescriptor parcelFileDescriptorOpen2 = ParcelFileDescriptor.open(file, 268435456);
        try {
            return new FrameworkDocumentSource(parcelFileDescriptorOpen2);
        } catch (IOException | RuntimeException e2) {
            closeQuietly(parcelFileDescriptorOpen2);
            throw e2;
        }
    }

    private static void closeQuietly(ParcelFileDescriptor parcelFileDescriptor) {
        try {
            parcelFileDescriptor.close();
        } catch (Throwable unused) {
        }
    }

    public void run(final Job job, final Utilities.Callback<Job> callback) {
        this.queue.postRunnable(() -> lambda_run_4(job, callback));
    }
    public /* synthetic */ void lambda_run_4(final Job job, final Utilities.Callback callback) {
        if (!this.closed) {
            try {
                int i = job.type;
                if (i == 0) {
                    renderBase(job);
                } else if (i == 1) {
                    renderDetail(job);
                } else if (i == 2) {
                    measure(job);
                } else if (i == 3) {
                    search(job);
                } else if (i == 4) {
                    select(job);
                }
            } catch (Throwable th) {
                FileLog.e(th);
                Bitmap bitmap = job.bitmap;
                if (bitmap != null) {
                    bitmap.recycle();
                    job.bitmap = null;
                }
            }
        }
        AndroidUtilities.runOnUIThread(() -> callback.run(job));
    }

    public void close() {
        this.queue.postRunnable(() -> lambda_close_5());
    }
    public /* synthetic */ void lambda_close_5() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        try {
            this.source.close();
        } catch (Throwable th) {
            FileLog.e(th);
        }
        this.queue.recycle();
    }

    private void renderBase(Job job) {
        PageSource pageSourceOpenPage = this.source.openPage(job.page);
        try {
            job.pageWidth = Math.max(1, pageSourceOpenPage.getWidth());
            float fMax = Math.max(1, pageSourceOpenPage.getHeight());
            job.pageHeight = fMax;
            float f = job.targetWidth;
            float f2 = job.pageWidth;
            float fSqrt = f / f2;
            float f3 = f2 * fMax * fSqrt * fSqrt;
            int i = job.maxPixels;
            if (f3 > i) {
                fSqrt = (float) Math.sqrt(i / (f2 * fMax));
            }
            float fMin = Math.min(fSqrt, 4096.0f / Math.max(job.pageWidth, job.pageHeight));
            int iMax = Math.max(1, Math.round(job.pageWidth * fMin));
            int iMax2 = Math.max(1, Math.round(job.pageHeight * fMin));
            Bitmap bitmapCreateBitmap = Bitmap.createBitmap(iMax, iMax2, Bitmap.Config.ARGB_8888);
            job.bitmap = bitmapCreateBitmap;
            bitmapCreateBitmap.eraseColor(-1);
            this.matrix.setScale(iMax / job.pageWidth, iMax2 / job.pageHeight);
            pageSourceOpenPage.render(job.bitmap, this.matrix);
            if (job.loadLinks && this.source.supportsText()) {
                job.links = loadLinks(pageSourceOpenPage, job.pageWidth, job.pageHeight);
            }
            pageSourceOpenPage.close();
        } catch (Throwable th) {
            if (pageSourceOpenPage != null) {
                try {
                    pageSourceOpenPage.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private void renderDetail(Job job) {
        PageSource pageSourceOpenPage = this.source.openPage(job.page);
        try {
            job.pageWidth = Math.max(1, pageSourceOpenPage.getWidth());
            float fMax = Math.max(1, pageSourceOpenPage.getHeight());
            job.pageHeight = fMax;
            float f = job.pageWidthPx / job.pageWidth;
            Bitmap bitmapCreateBitmap = Bitmap.createBitmap(job.width, job.height, Bitmap.Config.ARGB_8888);
            job.bitmap = bitmapCreateBitmap;
            bitmapCreateBitmap.eraseColor(-1);
            this.matrix.setScale(f, f);
            this.matrix.postTranslate((-job.left) * job.pageWidthPx, (-job.top) * fMax * f);
            pageSourceOpenPage.render(job.bitmap, this.matrix);
            pageSourceOpenPage.close();
        } catch (Throwable th) {
            if (pageSourceOpenPage != null) {
                try {
                    pageSourceOpenPage.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private void measure(Job job) {
        job.sizes = new float[job.count * 2];
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        int i = 0;
        while (i < job.count) {
            if (i != 0 && SystemClock.elapsedRealtime() - jElapsedRealtime >= 24) {
                return;
            }
            int i2 = i + 1;
            job.measuredCount = i2;
            try {
                PageSource pageSourceOpenPage = this.source.openPage(job.page + i);
                try {
                    int i3 = i * 2;
                    job.sizes[i3] = Math.max(1, pageSourceOpenPage.getWidth());
                    job.sizes[i3 + 1] = Math.max(1, pageSourceOpenPage.getHeight());
                    pageSourceOpenPage.close();
                } catch (Throwable th) {
                    if (pageSourceOpenPage != null) {
                        try {
                            pageSourceOpenPage.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                FileLog.e(th3);
            }
            i = i2;
        }
    }

    @SuppressLint({"NewApi"})
    private void search(Job job) {
        job.sizes = new float[job.count * 2];
        job.matches = new ArrayList<>(job.count);
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        int i = 0;
        while (i < job.count) {
            if (i != 0 && SystemClock.elapsedRealtime() - jElapsedRealtime >= 40) {
                return;
            }
            int i2 = i + 1;
            job.measuredCount = i2;
            ArrayList<RectF[]> arrayList = new ArrayList<>();
            try {
                PageSource pageSourceOpenPage = this.source.openPage(job.page + i);
                try {
                    float fMax = Math.max(1, pageSourceOpenPage.getWidth());
                    float fMax2 = Math.max(1, pageSourceOpenPage.getHeight());
                    float[] fArr = job.sizes;
                    int i3 = i * 2;
                    fArr[i3] = fMax;
                    fArr[i3 + 1] = fMax2;
                    List<PageMatchBounds> listSearchText = pageSourceOpenPage.searchText(job.query);
                    for (int i4 = 0; i4 < listSearchText.size(); i4++) {
                        List bounds = ((PageMatchBounds) listSearchText.get(i4)).getBounds();
                        if (!bounds.isEmpty()) {
                            int size = bounds.size();
                            RectF[] rectFArr = new RectF[size];
                            for (int i5 = 0; i5 < size; i5++) {
                                rectFArr[i5] = normalize((RectF) bounds.get(i5), fMax, fMax2);
                            }
                            arrayList.add(rectFArr);
                        }
                    }
                    pageSourceOpenPage.close();
                } catch (Throwable th) {
                    if (pageSourceOpenPage != null) {
                        try {
                            pageSourceOpenPage.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                FileLog.e(th3);
            }
            job.matches.add(arrayList);
            i = i2;
        }
    }

    private void select(Job job) {
        job.selectedPages = new ArrayList<>();
        int i = job.page;
        int i2 = job.stopPage;
        int i3 = job.startIndex;
        if (i == i2) {
            addSelection(job, i, i3, job.startX, job.startY, job.stopIndex, job.stopX, job.stopY, false);
            return;
        }
        addSelection(job, i, i3, job.startX, job.startY, Integer.MAX_VALUE, 0.0f, 0.0f, false);
        int[] iArr = job.fullPages;
        if (iArr != null) {
            for (int i4 : iArr) {
                addSelection(job, i4, 0, 0.0f, 0.0f, Integer.MAX_VALUE, 0.0f, 0.0f, true);
            }
        }
        addSelection(job, job.stopPage, 0, 0.0f, 0.0f, job.stopIndex, job.stopX, job.stopY, false);
    }

    private void addSelection(Job job, int i, int i2, float f, float f2, int i3, float f3, float f4, boolean z) {
        SelectedPage selectedPageSelectPage = null;
        try {
            PageSource pageSourceOpenPage = this.source.openPage(i);
            try {
                selectedPageSelectPage = selectPage(pageSourceOpenPage, i, i2, f, f2, i3, f3, f4);
                if (pageSourceOpenPage != null) {
                    pageSourceOpenPage.close();
                }
            } catch (Throwable th) {
                if (pageSourceOpenPage == null) {
                    throw th;
                }
                try {
                    pageSourceOpenPage.close();
                    throw th;
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                    throw th;
                }
            }
        } catch (Throwable th3) {
            FileLog.e(th3);
        }
        if (selectedPageSelectPage != null) {
            job.selectedPages.add(selectedPageSelectPage);
        } else if (z) {
            job.selectedPages.add(new SelectedPage(i, "", new RectF[0]));
        }
    }

    @SuppressLint({"NewApi"})
    private static SelectedPage selectPage(PageSource pageSource, int i, int i2, float f, float f2, int i3, float f3, float f4) {
        SelectionBoundary selectionBoundaryM;
        SelectionBoundary selectionBoundaryM2;
        float fMax = Math.max(1, pageSource.getWidth());
        float fMax2 = Math.max(1, pageSource.getHeight());
        if (i2 >= 0) {
            selectionBoundaryM = new SelectionBoundary(i2);
        } else {
            selectionBoundaryM = new SelectionBoundary((new Point(Math.round(f * fMax), Math.round(f2 * fMax2))));
        }
        if (i3 >= 0) {
            selectionBoundaryM2 = new SelectionBoundary(i3);
        } else {
            selectionBoundaryM2 = new SelectionBoundary((new Point(Math.round(f3 * fMax), Math.round(f4 * fMax2))));
        }
        PageSelection pageSelectionSelectContent = pageSource.selectContent(selectionBoundaryM, selectionBoundaryM2);
        SelectedPage selectedPage = null;
        if (pageSelectionSelectContent == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        ArrayList arrayList = new ArrayList();
        Iterator it = pageSelectionSelectContent.getSelectedTextContents().iterator();
        while (it.hasNext()) {
            PdfPageTextContent pdfPageTextContentM = (PdfPageTextContent) (it.next());
            sb.append(pdfPageTextContentM.getText());
            Iterator it2 = pdfPageTextContentM.getBounds().iterator();
            while (it2.hasNext()) {
                arrayList.add(normalize((RectF) it2.next(), fMax, fMax2));
            }
        }
        if (sb.length() != 0 && !arrayList.isEmpty()) {
            int i4 = 0;
            selectedPage = new SelectedPage(i, sb.toString(), (RectF[]) arrayList.toArray(new RectF[0]));
            while (i4 < 2) {
                SelectionBoundary start = i4 == 0 ? pageSelectionSelectContent.getStart() : pageSelectionSelectContent.getStop();
                selectedPage.boundaryIndex[i4] = start.getIndex();
                Point point = start.getPoint();
                if (point != null) {
                    selectedPage.boundaryX[i4] = point.x / fMax;
                    selectedPage.boundaryY[i4] = point.y / fMax2;
                }
                i4++;
            }
        }
        return selectedPage;
    }

    @SuppressLint({"NewApi"})
    private static ArrayList<Link> loadLinks(PageSource pageSource, float f, float f2) {
        ArrayList<Link> arrayList = new ArrayList<>();
        try {
            List<PdfPageLinkContent> linkContents = pageSource.getLinkContents();
            for (int i = 0; i < linkContents.size(); i++) {
                PdfPageLinkContent pdfPageLinkContentM = (PdfPageLinkContent) (linkContents.get(i));
                String string = pdfPageLinkContentM.getUri().toString();
                Iterator it = pdfPageLinkContentM.getBounds().iterator();
                while (it.hasNext()) {
                    arrayList.add(new Link(normalize((RectF) it.next(), f, f2), string, -1, 0.0f));
                }
            }
        } catch (Throwable th) {
            FileLog.e(th);
        }
        try {
            List<PdfPageGotoLinkContent> gotoLinks = pageSource.getGotoLinks();
            for (int i2 = 0; i2 < gotoLinks.size(); i2++) {
                PdfPageGotoLinkContent pdfPageGotoLinkContentM = (PdfPageGotoLinkContent) (gotoLinks.get(i2));
                PdfPageGotoLinkContent.Destination destination = pdfPageGotoLinkContentM.getDestination();
                float fClamp = Utilities.clamp(destination.getYCoordinate() / f2, 1.0f, 0.0f);
                Iterator it2 = pdfPageGotoLinkContentM.getBounds().iterator();
                while (it2.hasNext()) {
                    arrayList.add(new Link(normalize((RectF) it2.next(), f, f2), null, destination.getPageNumber(), fClamp));
                }
            }
        } catch (Throwable th2) {
            FileLog.e(th2);
        }
        return arrayList;
    }

    private static RectF normalize(RectF rectF, float f, float f2) {
        return new RectF(Math.min(rectF.left, rectF.right) / f, Math.min(rectF.top, rectF.bottom) / f2, Math.max(rectF.left, rectF.right) / f, Math.max(rectF.top, rectF.bottom) / f2);
    }

    public static final class FrameworkDocumentSource implements DocumentSource {
        private Object renderParams;
        private final PdfRenderer renderer;

        public FrameworkDocumentSource(ParcelFileDescriptor parcelFileDescriptor) throws IOException {
            this.renderer = new PdfRenderer(parcelFileDescriptor);
        }

        @Override
        public int getPageCount() {
            return this.renderer.getPageCount();
        }

        @Override
        public boolean supportsText() {
            return Build.VERSION.SDK_INT >= 35;
        }

        @Override
        public void close() {
            this.renderer.close();
        }

        @Override
        public PageSource openPage(int i) {
            final PdfRenderer.Page pageOpenPage = this.renderer.openPage(i);
            return new PageSource() {
                @Override
                public int getWidth() {
                    return pageOpenPage.getWidth();
                }

                @Override
                public int getHeight() {
                    return pageOpenPage.getHeight();
                }

                @Override
                public void render(Bitmap bitmap, Matrix matrix) {
                    if (Build.VERSION.SDK_INT >= 35) {
                        if (FrameworkDocumentSource.this.renderParams == null) {
                            FrameworkDocumentSource.this.renderParams = new RenderParams.Builder(1).setRenderFlags(6).build();
                        }
                        pageOpenPage.render(bitmap, (Rect) null, matrix, (RenderParams) FrameworkDocumentSource.this.renderParams);
                        return;
                    }
                    pageOpenPage.render(bitmap, null, matrix, 1);
                }

                @Override
                public List<PageMatchBounds> searchText(String str) {
                    return Build.VERSION.SDK_INT >= 35 ? pageOpenPage.searchText(str) : Collections.EMPTY_LIST;
                }

                @Override
                public List<PdfPageLinkContent> getLinkContents() {
                    return Build.VERSION.SDK_INT >= 35 ? pageOpenPage.getLinkContents() : Collections.EMPTY_LIST;
                }

                @Override
                public List<PdfPageGotoLinkContent> getGotoLinks() {
                    return Build.VERSION.SDK_INT >= 35 ? pageOpenPage.getGotoLinks() : Collections.EMPTY_LIST;
                }

                @Override
                public PageSelection selectContent(SelectionBoundary selectionBoundary, SelectionBoundary selectionBoundary2) {
                    if (Build.VERSION.SDK_INT >= 35) {
                        return pageOpenPage.selectContent(selectionBoundary, selectionBoundary2);
                    }
                    return null;
                }

                @Override
                public void close() {
                    pageOpenPage.close();
                }
            };
        }
    }

    public static final class PreVDocumentSource implements DocumentSource {
        private final RenderParams renderParams = new RenderParams.Builder(1).setRenderFlags(6).build();
        private final PdfRendererPreV renderer;

        @Override
        public boolean supportsText() {
            return true;
        }

        public PreVDocumentSource(ParcelFileDescriptor parcelFileDescriptor) throws IOException {
            this.renderer = new PdfRendererPreV(parcelFileDescriptor);
        }

        @Override
        public int getPageCount() {
            return this.renderer.getPageCount();
        }

        @Override
        public void close() {
            this.renderer.close();
        }

        @Override
        public PageSource openPage(int i) {
            final PdfRendererPreV.Page pageOpenPage = this.renderer.openPage(i);
            return new PageSource() {
                @Override
                public int getWidth() {
                    return pageOpenPage.getWidth();
                }

                @Override
                public int getHeight() {
                    return pageOpenPage.getHeight();
                }

                @Override
                public void render(Bitmap bitmap, Matrix matrix) {
                    pageOpenPage.render(bitmap, (Rect) null, matrix, PreVDocumentSource.this.renderParams);
                }

                @Override
                public List<PageMatchBounds> searchText(String str) {
                    return pageOpenPage.searchText(str);
                }

                @Override
                public List<PdfPageLinkContent> getLinkContents() {
                    return pageOpenPage.getLinkContents();
                }

                @Override
                public List<PdfPageGotoLinkContent> getGotoLinks() {
                    return pageOpenPage.getGotoLinks();
                }

                @Override
                public PageSelection selectContent(SelectionBoundary selectionBoundary, SelectionBoundary selectionBoundary2) {
                    return pageOpenPage.selectContent(selectionBoundary, selectionBoundary2);
                }

                @Override
                public void close() {
                    pageOpenPage.close();
                }
            };
        }
    }
}

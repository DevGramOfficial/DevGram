package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AppIconBulletinLayout;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

// DevGram: выбор иконки приложения отдельным экраном (как у exteraGram): большое превью с
// подсветкой в цвет иконки, сетка иконок, кнопка «Применить». Маскировки (Калькулятор и т.п.)
// здесь не показываются — у них свой раздел.
public class DevGramAppIconsActivity extends BaseFragment {

    private LauncherIconController.LauncherIcon appliedIcon;
    private LauncherIconController.LauncherIcon previewIcon;

    private ContentView contentView;
    private UniversalRecyclerView listView;
    private HeroView heroView;
    private FrameLayout buttonContainer;
    private ButtonWithCounterView button;
    private BlurredBackgroundSourceColor glassSource;

    private int accentColor;
    private ValueAnimator colorAnimator;
    private int heroHeight;
    private int bottomInset;
    private int scrolled;
    private float collapse;

    @Override
    @Deprecated
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public View createView(Context context) {
        appliedIcon = currentIcon();
        previewIcon = appliedIcon != null ? appliedIcon : LauncherIconController.LauncherIcon.DEFAULT;
        accentColor = accentOf(context, previewIcon);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.AppIcon));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_listSelector), false);
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        contentView = new ContentView(context);

        glassSource = new BlurredBackgroundSourceColor();
        glassSource.setColor(getTopColor());
        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(glassSource);
        factory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        factory.setSourceRootView(new ViewPositionWatcher(contentView), contentView);
        actionBar.setGlassPadding(dp(12));
        actionBar.setGlassTitleTextSize(20);
        actionBar.setupGlass(factory, BlurredBackgroundProviderImpl.topPanelChatActivity(getResourceProvider()));
        actionBar.setDrawGlassMiddlePill(false);
        actionBar.setGlassShadowAlpha(0f);

        heroView = new HeroView(context);
        heroView.set(previewIcon, false);
        contentView.addView(heroView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSpanCount(4);
        listView.adapter.setApplyBackground(false);
        listView.setDrawSelection(false);
        listView.setClipToPadding(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateCollapse();
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        button = new ButtonWithCounterView(context, true, getResourceProvider());
        button.setRoundRadius(24);
        button.setOnClickListener(v -> onButtonClick());
        updateButton(false);
        buttonContainer = new FrameLayout(context);
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 12, 16, 12));
        buttonContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateListPadding());
        contentView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        contentView.addView(actionBar);
        return fragmentView = contentView;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        bottomInset = bottom;
        if (buttonContainer != null) {
            buttonContainer.setPadding(0, 0, 0, bottomInset);
        }
        updateListPadding();
    }

    @Override
    public boolean isLightStatusBar() {
        return !AndroidUtilities.isDarkColor(getTopColor());
    }

    // ---- данные ----

    private static ArrayList<LauncherIconController.LauncherIcon> availableIcons() {
        ArrayList<LauncherIconController.LauncherIcon> list = new ArrayList<>();
        for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
            if (!icon.disguise) {
                list.add(icon);
            }
        }
        return list;
    }

    // Текущая иконка; при маскировке — null (ни одна из этих иконок сейчас не стоит).
    private static LauncherIconController.LauncherIcon currentIcon() {
        for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
            if (LauncherIconController.isEnabled(icon)) {
                return icon.disguise ? null : icon;
            }
        }
        return LauncherIconController.LauncherIcon.DEFAULT;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        for (LauncherIconController.LauncherIcon icon : availableIcons()) {
            items.add(IconCell.Factory.of(icon, icon == previewIcon));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (!(item.object instanceof LauncherIconController.LauncherIcon) || item.object == previewIcon) {
            return;
        }
        previewIcon = (LauncherIconController.LauncherIcon) item.object;
        heroView.set(previewIcon, true);
        animateAccent(accentOf(getContext(), previewIcon));
        updateButton(true);
        listView.adapter.update(true);
    }

    private void onButtonClick() {
        if (previewIcon == null) {
            return;
        }
        if (previewIcon == appliedIcon) {
            finishFragment();
            return;
        }
        LauncherIconController.setIcon(previewIcon);
        appliedIcon = previewIcon;
        updateButton(true);
        Bulletin.make(this, new AppIconBulletinLayout(getContext(), appliedIcon, getResourceProvider()), Bulletin.DURATION_SHORT).show();
    }

    private void updateButton(boolean animated) {
        if (button != null) {
            button.setText(previewIcon == appliedIcon ? "Оставить" : "Применить", animated);
        }
    }

    // ---- цвет подсветки ----

    private void animateAccent(int to) {
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        final int from = accentColor;
        if (from == to) {
            return;
        }
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(280);
        colorAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        colorAnimator.addUpdateListener(a -> {
            accentColor = ColorUtils.blendARGB(from, to, (float) a.getAnimatedValue());
            invalidateHeader();
        });
        colorAnimator.start();
    }

    private void invalidateHeader() {
        if (contentView != null) {
            contentView.invalidate();
        }
        if (glassSource != null) {
            glassSource.setColor(getTopColor());
            actionBar.invalidate();
        }
        if (getParentActivity() != null) {
            AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), isLightStatusBar());
        }
    }

    private int getBaseColor() {
        int base = getThemedColor(Theme.key_windowBackgroundGray);
        return accentColor == 0 ? base : ColorUtils.blendARGB(base, accentColor, 0.25f);
    }

    private int getTopColor() {
        return accentColor == 0 ? getBaseColor() : ColorUtils.blendARGB(getBaseColor(), accentColor, 0.7f * (1f - collapse * 0.6f));
    }

    // Средний «живой» цвет иконки: насыщенные пиксели весят больше серых/белых фоновых.
    private static int accentOf(Context context, LauncherIconController.LauncherIcon icon) {
        try {
            Drawable d = previewDrawable(context, icon);
            Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 24, 24);
            d.draw(new Canvas(bitmap));
            float[] hsv = new float[3];
            double r = 0, g = 0, b = 0, w = 0;
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    int c = bitmap.getPixel(x, y);
                    if (Color.alpha(c) < 128) continue;
                    Color.colorToHSV(c, hsv);
                    double weight = hsv[1] * hsv[2] + 0.02;
                    r += Color.red(c) * weight;
                    g += Color.green(c) * weight;
                    b += Color.blue(c) * weight;
                    w += weight;
                }
            }
            bitmap.recycle();
            if (w <= 0) return 0;
            int avg = Color.rgb((int) (r / w), (int) (g / w), (int) (b / w));
            Color.colorToHSV(avg, hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.15f);
            return Color.HSVToColor(hsv);
        } catch (Throwable e) {
            return 0;
        }
    }

    // ---- превью иконки: квадрат со скруглением, как в селекторе ----

    // Иконка из ресурсов alias'а: система обрезает её по форме иконок телефона, поэтому все иконки
    // в сетке одной формы с основной. Запасной вариант — готовое превью.
    static Drawable previewDrawable(Context context, LauncherIconController.LauncherIcon icon) {
        Drawable d = LauncherIconController.loadIcon(context, icon);
        if (d != null) {
            return d;
        }
        Drawable single = icon.isFullImage() ? context.getDrawable(icon.foreground) : context.getDrawable(R.mipmap.ic_launcher);
        return new RoundIconDrawable(single, null, null);
    }

    private static final class RoundIconDrawable extends Drawable {
        private final Drawable single, background, foreground;
        private final Path clip = new Path();

        RoundIconDrawable(Drawable single, Drawable background, Drawable foreground) {
            this.single = single;
            this.background = background;
            this.foreground = foreground;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            clip.rewind();
            AndroidUtilities.rectTmp.set(b);
            float r = b.width() * 0.225f;
            clip.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            if (single != null) {
                single.setBounds(b);
                single.draw(canvas);
            } else {
                // слои адаптивной иконки 108dp, видна центральная зона 72dp → растягиваем на 1.5
                int ext = b.width() / 4;
                if (background != null) {
                    background.setBounds(b.left - ext, b.top - ext, b.right + ext, b.bottom + ext);
                    background.draw(canvas);
                }
                if (foreground != null) {
                    foreground.setBounds(b.left - ext, b.top - ext, b.right + ext, b.bottom + ext);
                    foreground.draw(canvas);
                }
            }
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ---- раскладка и сворачивание шапки ----

    private void updateListPadding() {
        if (listView == null) return;
        int top = heroHeight + dp(10);
        int bottom = buttonContainer != null && buttonContainer.getHeight() > 0 ? buttonContainer.getHeight() : bottomInset + dp(72);
        if (listView.getPaddingTop() != top || listView.getPaddingBottom() != bottom) {
            listView.setPadding(dp(8), top, dp(8), bottom);
        }
        updateCollapse();
    }

    private int getCollapseRange() {
        return Math.max(0, heroHeight - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight);
    }

    private void updateCollapse() {
        if (listView == null || heroView == null) return;
        int s = scrolled;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) == 0) {
                s = listView.getPaddingTop() - child.getTop();
                break;
            }
        }
        scrolled = Math.max(0, s);
        int range = getCollapseRange();
        float c = range <= 0 ? 0f : Math.min(1f, scrolled / (float) range);
        if (Math.abs(c - collapse) > 0.0005f) {
            collapse = c;
            heroView.setCollapse(c);
            if (actionBar.getTitleTextView() != null) {
                actionBar.getTitleTextView().setAlpha(Math.max(0f, (c - 0.6f) / 0.4f));
            }
            invalidateHeader();
        }
    }

    private class ContentView extends FrameLayout {
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path panelPath = new Path();
        private int glowColor, glowW, glowH;

        ContentView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            heroView.setPadding(0, AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() - dp(12), 0, dp(36));
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int span = Math.max(3, Math.min(6, Math.round((width - dp(16)) / (float) dp(96))));
            if (listView.getSpanCount() != span) {
                listView.setSpanCount(span);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int h = heroView.getMeasuredHeight();
            if (h > 0 && h != heroHeight) {
                heroHeight = h;
                post(DevGramAppIconsActivity.this::updateListPadding);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            updateCollapse();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            canvas.drawColor(getBaseColor());
            if (accentColor == 0 || collapse >= 1f) return;
            float cx = getWidth() / 2f;
            float cy = heroView.getY() + heroView.getPaddingTop() + heroView.getPreviewSize() / 2f;
            if (glowColor != accentColor || glowW != getWidth() || glowH != getHeight()) {
                glowColor = accentColor;
                glowW = getWidth();
                glowH = getHeight();
                float radius = Math.max(getWidth(), heroHeight) * 0.9f;
                if (radius <= 0) return;
                glowPaint.setShader(new RadialGradient(cx, cy, radius,
                        new int[]{ColorUtils.setAlphaComponent(accentColor, 200), ColorUtils.setAlphaComponent(accentColor, 0)},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            }
            glowPaint.setAlpha((int) ((1f - collapse) * 255));
            canvas.drawRect(0, 0, getWidth(), getHeight(), glowPaint);
        }

        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            if (child != listView) {
                return super.drawChild(canvas, child, drawingTime);
            }
            // белая панель со скруглёнными верхними углами под сеткой иконок
            float r = dp(20);
            float top = listView.getY() + heroHeight - getCollapseRange() * collapse;
            AndroidUtilities.rectTmp.set(0, top, getWidth(), getHeight());
            panelPath.rewind();
            panelPath.addRoundRect(AndroidUtilities.rectTmp, new float[]{r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);
            panelPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
            canvas.drawPath(panelPath, panelPaint);
            canvas.save();
            canvas.clipPath(panelPath);
            boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return result;
        }
    }

    // ---- большое превью сверху ----

    private class HeroView extends LinearLayout {
        private final ImageView preview;
        private final TextView title;
        private final TextView subtitle;
        private final int previewSize = dp(112);

        HeroView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            preview = new ImageView(context);
            addView(preview, LayoutHelper.createLinear(112, 112, Gravity.CENTER_HORIZONTAL));
            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            title.setTypeface(AndroidUtilities.bold());
            title.setGravity(Gravity.CENTER);
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 16, 24, 0));
            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitle.setText("Иконка приложения DevGram");
            addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 0));
        }

        int getPreviewSize() {
            return previewSize;
        }

        void set(LauncherIconController.LauncherIcon icon, boolean animated) {
            preview.setImageDrawable(previewDrawable(getContext(), icon));
            title.setText(LocaleController.getString(icon.title));
            if (animated) {
                preview.setScaleX(0.9f);
                preview.setScaleY(0.9f);
                preview.animate().scaleX(1f).scaleY(1f).setDuration(320)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();
            }
        }

        void setCollapse(float c) {
            float a = Math.max(0f, 1f - c * 1.4f);
            preview.setAlpha(a);
            title.setAlpha(a);
            subtitle.setAlpha(a);
            float s = 1f - 0.25f * c;
            preview.setScaleX(s);
            preview.setScaleY(s);
            setTranslationY(-getCollapseRange() * c * 0.5f);
        }
    }

    // ---- ячейка сетки ----

    private static class IconCell extends LinearLayout {
        private final ImageView preview;
        private final CheckBox2 checkBox;
        private final TextView title;
        private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Theme.ResourcesProvider resourcesProvider;
        private float selection;
        private ValueAnimator selectionAnimator;

        IconCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            setClipChildren(false);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(dp(4), dp(10), dp(4), dp(12));

            FrameLayout container = new FrameLayout(context);
            container.setClipChildren(false);
            preview = new ImageView(context);
            container.addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            checkBox = new CheckBox2(context, 24, resourcesProvider);
            checkBox.setColor(Theme.key_featuredStickers_addButton, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(4);
            container.addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 1, 1));
            addView(container, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL));

            title = new TextView(context);
            title.setMaxLines(2);
            title.setGravity(Gravity.CENTER);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
            ScaleStateListAnimator.apply(this, .05f, 1.2f);
        }

        void set(LauncherIconController.LauncherIcon icon, boolean selected, boolean animated) {
            preview.setImageDrawable(previewDrawable(getContext(), icon));
            title.setText(LocaleController.getString(icon.title));
            checkBox.setChecked(selected, animated);
            float to = selected ? 1f : 0f;
            if (selectionAnimator != null) {
                selectionAnimator.cancel();
                selectionAnimator = null;
            }
            if (!animated || selection == to) {
                selection = to;
                invalidate();
                return;
            }
            selectionAnimator = ValueAnimator.ofFloat(selection, to).setDuration(220);
            selectionAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            selectionAnimator.addUpdateListener(a -> {
                selection = (float) a.getAnimatedValue();
                invalidate();
            });
            selectionAnimator.start();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (selection <= 0f) return;
            int white = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            selectionPaint.setColor(ColorUtils.blendARGB(white, accent, selection * 0.08f));
            AndroidUtilities.rectTmp.set(dp(2), dp(2), getWidth() - dp(2), getHeight() - dp(2));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(16), dp(16), selectionPaint);
        }

        public static class Factory extends UItem.UItemFactory<IconCell> {
            static {
                setup(new Factory());
            }

            @Override
            public IconCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new IconCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((IconCell) view).set((LauncherIconController.LauncherIcon) item.object, item.checked, true);
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a.object == b.object && a.checked == b.checked;
            }

            static UItem of(LauncherIconController.LauncherIcon icon, boolean selected) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = icon.ordinal() + 1;
                item.object = icon;
                item.checked = selected;
                item.spanCount = 1;
                return item;
            }
        }
    }
}

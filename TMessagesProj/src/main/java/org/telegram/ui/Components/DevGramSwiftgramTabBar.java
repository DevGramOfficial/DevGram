package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramSwiftgramDesign;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.glass.GlassTabView;

/** New source-derived Swiftgram tab presentation; Android tab controllers keep all original actions. */
public final class DevGramSwiftgramTabBar extends FrameLayout {
    private final GlassTabView[] nativeTabs;
    private final Tab[] tabs;
    private final FrameLayout platter;
    private final View lens;
    private final ImageView search;
    private DevGramSwiftgramTabLayout.Layout layout;
    private int selected = 2;
    private float lensX, lensWidth, downX, downY;
    private boolean dragging;
    private ValueAnimator animation;
    private final int slop;
    private boolean searchVisible = true;
    public interface LongPressHandler { boolean open(int index, View anchor); }
    private LongPressHandler longPressHandler;
    public void setLongPressHandler(LongPressHandler handler) { longPressHandler = handler; }

    public DevGramSwiftgramTabBar(Context context, GlassTabView[] nativeTabs, Runnable openSearch) {
        super(context);
        this.nativeTabs = nativeTabs;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        setClipToPadding(false);
        platter = new FrameLayout(context);
        platter.setClipChildren(false);
        platter.setClipToPadding(false);
        addView(platter);
        lens = new View(context) {
            final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
            final RectF rect = new RectF();
            @Override protected void onDraw(Canvas canvas) {
                boolean dark = Theme.isCurrentThemeDark();
                rect.set(AndroidUtilities.dpf2(1), AndroidUtilities.dpf2(1), getWidth() - AndroidUtilities.dpf2(1), getHeight() - AndroidUtilities.dpf2(1));
                fill.setColor(dark ? 0x28ffffff : 0x15000000);
                canvas.drawRoundRect(rect, rect.height()/2, rect.height()/2, fill);
                edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(AndroidUtilities.dpf2(.5f));
                edge.setColor(dark ? 0x35ffffff : 0x55ffffff);
                canvas.drawRoundRect(rect, rect.height()/2, rect.height()/2, edge);
            }
        };
        lens.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        platter.addView(lens);
        int[] labels = {R.string.MainTabsProfile, R.string.MainTabsCalls, R.string.MainTabsChats, R.string.Settings};
        int[] icons = {R.raw.swiftgram_tab_contacts, R.raw.swiftgram_tab_calls, R.raw.swiftgram_tab_chats, R.raw.swiftgram_tab_settings};
        tabs = new Tab[nativeTabs.length];
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            Tab tab = tabs[i] = new Tab(context, LocaleController.getString(labels[i]), icons[i]);
            tab.setOnClickListener(v -> { nativeTabs[index].performClick(); sync(true); });
            tab.setOnLongClickListener(v -> longPressHandler != null ? longPressHandler.open(index, v) : nativeTabs[index].performLongClick());
            platter.addView(tab);
        }
        search = new ImageView(context);
        search.setImageResource(R.drawable.swiftgram_outline_header_search);
        search.setScaleType(ImageView.ScaleType.CENTER);
        search.setColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon));
        search.setContentDescription(LocaleController.getString(R.string.Search));
        search.setOnClickListener(v -> openSearch.run());
        addView(search);
        sync(false);
    }

    public void setGlass(BlurredBackgroundDrawable bar, BlurredBackgroundDrawable searchGlass) {
        bar.setRadius(AndroidUtilities.dp(heightDp() / 2f));
        searchGlass.setRadius(AndroidUtilities.dp(heightDp() / 2f));
        platter.setBackground(bar); search.setBackground(searchGlass);
    }

    public View platter() { return platter; }
    public View searchButton() { return search; }
    public static int heightDp() { return GlassTabView.hideLabels() ? 48 : DevGramSwiftgramDesign.NAVIGATION_HEIGHT; }

    public void sync(boolean animated) {
        int next = selected;
        for (int i = 0; i < tabs.length; i++) {
            if (nativeTabs[i].isTabSelected()) next = i;
            tabs[i].setBadge(nativeTabs[i].counterText());
        }
        boolean changed = next != selected;
        selected = next;
        for (int i = 0; i < tabs.length; i++) tabs[i].select(i == selected, animated && changed);
        if (layout != null && !dragging && changed) settleLens(animated);
    }

    /** Keep the lens attached to the native pager during an interactive swipe. */
    public void setPageProgress(float position, boolean interactive) {
        if (!interactive || dragging || layout == null) return;
        if (animation != null) animation.cancel();
        int first = Math.max(0, Math.min(tabs.length - 1, (int) Math.floor(position)));
        int second = Math.min(tabs.length - 1, first + 1);
        float fraction = Math.max(0, Math.min(1, position - first));
        float[] a = DevGramSwiftgramTabLayout.lens(layout, first, heightDp());
        float[] b = DevGramSwiftgramTabLayout.lens(layout, second, heightDp());
        setLens((a[0] + (b[0] - a[0]) * fraction) * AndroidUtilities.density,
                (a[1] + (b[1] - a[1]) * fraction) * AndroidUtilities.density);
    }

    public void setSearchVisible(boolean value) {
        if (searchVisible == value) return;
        searchVisible = value; search.setVisibility(value ? VISIBLE : GONE); requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = AndroidUtilities.dp(heightDp());
        setMeasuredDimension(width, height);
        float available = width / AndroidUtilities.density - (searchVisible ? heightDp() + 8 : 0);
        float[] widths = new float[tabs.length];
        for (int i = 0; i < tabs.length; i++) widths[i] = GlassTabView.hideLabels() ? 0 : tabs[i].label.getPaint().measureText(tabs[i].label.getText().toString()) / AndroidUtilities.density;
        layout = DevGramSwiftgramTabLayout.measure(widths, Math.max(8, available), searchVisible, AndroidUtilities.density);
        platter.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(layout.width), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        for (int i = 0; i < tabs.length; i++) tabs[i].measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(layout.widths[i]), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - AndroidUtilities.dp(8), MeasureSpec.EXACTLY));
        search.measure(MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        settleLens(false);
        lens.measure(MeasureSpec.makeMeasureSpec(Math.round(lensWidth), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (layout == null) return;
        int width = AndroidUtilities.dp(layout.width);
        int total = width + (searchVisible ? getHeight() + AndroidUtilities.dp(8) : 0);
        int x = (getWidth() - total) / 2;
        platter.layout(x, 0, x + width, getHeight());
        for (int i = 0; i < tabs.length; i++) {
            int left = AndroidUtilities.dp(layout.x[i]);
            tabs[i].layout(left, AndroidUtilities.dp(4), left + tabs[i].getMeasuredWidth(), getHeight() - AndroidUtilities.dp(4));
        }
        lens.layout(0, 0, Math.round(lensWidth), getHeight());
        lens.setTranslationX(lensX);
        search.layout(x + width + AndroidUtilities.dp(8), 0, x + width + AndroidUtilities.dp(8) + getHeight(), getHeight());
    }

    private void settleLens(boolean animated) {
        float[] rect = DevGramSwiftgramTabLayout.lens(layout, selected, heightDp());
        float x = rect[0] * AndroidUtilities.density, width = rect[1] * AndroidUtilities.density;
        if (animation != null) animation.cancel();
        if (!animated || !DevGramSwiftgramDesign.section("animations")) { setLens(x, width); return; }
        float startX = lensX, startWidth = lensWidth;
        animation = ValueAnimator.ofFloat(0, 1);
        animation.setDuration(DevGramSwiftgramDesign.TRANSITION_MS);
        animation.setInterpolator(DevGramSwiftgramSpring::navigation);
        animation.addUpdateListener(a -> { float f = (float) a.getAnimatedValue(); setLens(startX + (x-startX)*f, startWidth+(width-startWidth)*f); });
        animation.start();
    }

    private void setLens(float x, float width) {
        lensX = x; lensWidth = width;
        lens.setTranslationX(x);
        if (lens.getWidth() != Math.round(width) && getHeight() > 0) lens.layout(0, 0, Math.round(width), getHeight());
        lens.invalidate();
    }

    private int nearest(float x) {
        float closest = Float.MAX_VALUE; int result = selected;
        for (int i = 0; i < tabs.length; i++) {
            float distance = Math.abs(x - platter.getLeft() - (layout.x[i] + layout.widths[i]/2) * AndroidUtilities.density);
            if (distance < closest) { closest = distance; result = i; }
        }
        return result;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { downX=e.getX(); downY=e.getY(); dragging=false; }
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE && Math.abs(e.getX()-downX)>slop && Math.abs(e.getY()-downY)<AndroidUtilities.dp(28) && downX<platter.getRight()) {
            dragging=true; if(animation!=null)animation.cancel(); getParent().requestDisallowInterceptTouchEvent(true); return true;
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!dragging || layout == null) return super.onTouchEvent(e);
        int index=nearest(e.getX());
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE) {
            float[] rect=DevGramSwiftgramTabLayout.lens(layout,index,heightDp());
            float width=rect[1]*AndroidUtilities.density;
            setLens(Math.max(0,Math.min(e.getX()-platter.getLeft()-width/2,platter.getWidth()-width)),width);
        } else if(e.getActionMasked()==MotionEvent.ACTION_UP || e.getActionMasked()==MotionEvent.ACTION_CANCEL) {
            dragging=false; if(e.getActionMasked()==MotionEvent.ACTION_UP)nativeTabs[index].performClick(); sync(true); settleLens(true);
        }
        return true;
    }

    @Override protected void onDetachedFromWindow() { if(animation!=null)animation.cancel(); super.onDetachedFromWindow(); }

    private static final class Tab extends FrameLayout {
        final TextView label, badge;
        final RLottieImageView icon;
        boolean selected;
        Tab(Context context,String title,int raw) {
            super(context); setClipChildren(false); setClipToPadding(false); setFocusable(true); setContentDescription(title);
            icon=new RLottieImageView(context); icon.setAnimation(raw,48,48); icon.setAutoRepeat(false); icon.setProgress(1);
            addView(icon,LayoutHelper.createFrame(48,48,Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,-4,0,0));
            label=new TextView(context); label.setText(title); label.setTextSize(10); label.setIncludeFontPadding(false);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);
            label.setVisibility(GlassTabView.hideLabels()?GONE:VISIBLE); addView(label,LayoutHelper.createFrame(-1,12,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,0,0,7));
            badge=new TextView(context);badge.setTextSize(12);badge.setIncludeFontPadding(false);badge.setGravity(Gravity.CENTER);badge.setTextColor(0xffffffff);badge.setPadding(AndroidUtilities.dp(5),0,AndroidUtilities.dp(5),0);badge.setMinWidth(AndroidUtilities.dp(18));badge.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(9),0xffff3b30));badge.setVisibility(GONE);
            addView(badge,LayoutHelper.createFrame(-2,18,Gravity.TOP|Gravity.CENTER_HORIZONTAL,17,-2,0,0));
            select(false,false);
        }
        void select(boolean value,boolean animated) {
            boolean changed=selected!=value;selected=value;setSelected(value);
            int color=Theme.getColor(value?Theme.key_glass_tabSelected:Theme.key_glass_tabUnselected);
            icon.setColorFilter(color);label.setTextColor(color);label.setTypeface(Typeface.create(value?"sans-serif-semibold":"sans-serif-medium",Typeface.NORMAL));
            if(changed&&value&&animated&&DevGramSwiftgramDesign.section("animations")){icon.setProgress(0);icon.playAnimation();}
        }
        void setBadge(String value) {if(TextUtils.equals(badge.getText(),value==null?"":value))return;badge.setText(value);badge.setVisibility(TextUtils.isEmpty(value)?GONE:VISIBLE);setContentDescription(label.getText()+(TextUtils.isEmpty(value)?"":", "+value));}
    }
}

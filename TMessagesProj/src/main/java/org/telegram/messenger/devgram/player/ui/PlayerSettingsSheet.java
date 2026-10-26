package org.telegram.messenger.devgram.player.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.devgram.player.PlayerConfig;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
public class PlayerSettingsSheet extends BottomSheetWithRecyclerListView {
    private UniversalAdapter adapter;
    private boolean capturing;
    private final SwitchingResourcesProvider colors;
    private boolean coverColorsExpanded;
    private final Delegate delegate;
    private Bitmap snapshot;
    private ValueAnimator snapshotAnimator;
    private final Paint snapshotPaint;
    private int snapshotTop;

    public interface Delegate {
        void onCoverColorsChanged();

        void onLyricsOnlineChanged();

        void onLyricsProgressChanged();

        void onMotionCoversChanged();

        void onRhythmBackgroundChanged();
    }

    public PlayerSettingsSheet(Context context, BaseFragment baseFragment, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        this(context, baseFragment, new SwitchingResourcesProvider(resourcesProvider), delegate);
    }

    private PlayerSettingsSheet(Context context, BaseFragment baseFragment, SwitchingResourcesProvider switchingResourcesProvider, Delegate delegate) {
        super(context, baseFragment, false, false, false, true, BottomSheetWithRecyclerListView.ActionBarType.SLIDING, switchingResourcesProvider);
        this.snapshotPaint = new Paint(2);
        this.colors = switchingResourcesProvider;
        this.delegate = delegate;
        this.takeTranslationIntoAccount = true;
        applyColors();
        RecyclerListView recyclerListView = this.recyclerListView;
        int i = this.backgroundPaddingLeft;
        recyclerListView.setPadding(i, 0, i, AndroidUtilities.dp(12.0f));
        this.recyclerListView.setClipToPadding(false);
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator() {
            @Override
            public void onMoveAnimationUpdate(RecyclerView.ViewHolder viewHolder) {
                super.onMoveAnimationUpdate(viewHolder);
                PlayerSettingsSheet.this.containerView.invalidate();
            }
        };
        defaultItemAnimator.setSupportsChangeAnimations(false);
        defaultItemAnimator.setDelayAnimations(false);
        defaultItemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        defaultItemAnimator.setDurations(350L);
        this.recyclerListView.setItemAnimator(defaultItemAnimator);
        this.recyclerListView.setSections();
        this.recyclerListView.setOnItemClickListener((view, i2) -> lambda_new_0(view, i2));
        this.adapter.update(false);
    }
    public /* synthetic */ void lambda_new_0(View view, int i) {
        UItem item = this.adapter.getItem(i - 1);
        if (item != null) {
            onItemClick(item, view);
        }
    }

    public void updateColors(Theme.ResourcesProvider resourcesProvider) {
        if (this.colors.current == resourcesProvider) {
            return;
        }
        captureSnapshot();
        this.colors.current = resourcesProvider;
        applyColors();
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) this.recyclerListView.getLayoutManager();
        int iFindFirstVisibleItemPosition = linearLayoutManager != null ? linearLayoutManager.findFirstVisibleItemPosition() : -1;
        View viewFindViewByPosition = iFindFirstVisibleItemPosition != -1 ? linearLayoutManager.findViewByPosition(iFindFirstVisibleItemPosition) : null;
        int top = viewFindViewByPosition != null ? viewFindViewByPosition.getTop() - this.recyclerListView.getPaddingTop() : 0;
        resetAdapter(getContext());
        if (viewFindViewByPosition != null) {
            linearLayoutManager.scrollToPositionWithOffset(iFindFirstVisibleItemPosition, top);
        }
        fadeSnapshot();
    }

    private void applyColors() {
        int themedColor = getThemedColor(Theme.key_windowBackgroundGray);
        setBackgroundColor(themedColor);
        fixNavigationBar(themedColor);
        this.recyclerListView.setSelectorDrawableColor(getThemedColor(Theme.key_settings_listSelector));
        ActionBar actionBar = this.actionBar;
        if (actionBar != null) {
            actionBar.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            this.actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            this.actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
            this.actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
        }
        this.containerView.invalidate();
    }

    private void captureSnapshot() {
        int width = this.containerView.getWidth();
        int iMax = Math.max(0, this.shadowDrawable.getBounds().top);
        int height = this.containerView.getHeight() - iMax;
        if (!this.containerView.isAttachedToWindow() || width <= 0 || height <= 0) {
            return;
        }
        try {
            Bitmap bitmapCreateBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmapCreateBitmap);
            canvas.translate(0.0f, -iMax);
            this.capturing = true;
            this.containerView.draw(canvas);
            this.capturing = false;
            releaseSnapshot();
            this.snapshot = bitmapCreateBitmap;
            this.snapshotTop = iMax;
            this.snapshotPaint.setAlpha(255);
        } catch (OutOfMemoryError unused) {
        }
    }

    private void fadeSnapshot() {
        if (this.snapshot == null) {
            return;
        }
        ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(1.0f, 0.0f);
        this.snapshotAnimator = valueAnimatorOfFloat;
        valueAnimatorOfFloat.setDuration(350L);
        this.snapshotAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        this.snapshotAnimator.addUpdateListener(valueAnimator -> lambda_fadeSnapshot_1(valueAnimator));
        this.snapshotAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                if (PlayerSettingsSheet.this.snapshotAnimator == animator) {
                    PlayerSettingsSheet.this.snapshotAnimator = null;
                    PlayerSettingsSheet.this.releaseSnapshot();
                }
            }
        });
        this.snapshotAnimator.start();
    }
    public /* synthetic */ void lambda_fadeSnapshot_1(ValueAnimator valueAnimator) {
        this.snapshotPaint.setAlpha((int) (((Float) valueAnimator.getAnimatedValue()).floatValue() * 255.0f));
        this.containerView.invalidate();
    }
    public void releaseSnapshot() {
        ValueAnimator valueAnimator = this.snapshotAnimator;
        if (valueAnimator != null) {
            this.snapshotAnimator = null;
            valueAnimator.cancel();
        }
        Bitmap bitmap = this.snapshot;
        if (bitmap != null) {
            bitmap.recycle();
            this.snapshot = null;
            this.containerView.invalidate();
        }
    }

    @Override
    public void postDrawInternal(Canvas canvas, View view) {
        Bitmap bitmap = this.snapshot;
        if (bitmap != null && !this.capturing && view == this.containerView) {
            canvas.drawBitmap(bitmap, 0.0f, this.snapshotTop, this.snapshotPaint);
        }
        super.postDrawInternal(canvas, view);
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        releaseSnapshot();
    }

    @Override
    public CharSequence getTitle() {
        return LocaleController.getString(R.string.PlayerSettings);
    }

    @Override
    public RecyclerListView.SelectionAdapter createAdapter(RecyclerListView recyclerListView) {
        UniversalAdapter universalAdapter = new UniversalAdapter(recyclerListView, getContext(), this.currentAccount, 0, true, (obj, obj2) -> fillItems((ArrayList) obj, (UniversalAdapter) obj2), this.resourcesProvider);
        this.adapter = universalAdapter;
        universalAdapter.setApplyBackground(false);
        return this.adapter;
    }
    public void fillItems(ArrayList<UItem> arrayList, UniversalAdapter universalAdapter) {
        if (this.delegate == null) {
            return;
        }
        arrayList.add(UItem.asShadow(null));
        arrayList.add(UItem.asHeader(LocaleController.getString(R.string.Appearance)));
        boolean playerCoverColors = PlayerConfig.getPlayerCoverColors();
        boolean playerMiniCoverColors = PlayerConfig.getPlayerMiniCoverColors();
        final int i = (playerCoverColors ? 1 : 0) + (playerMiniCoverColors ? 1 : 0);
        arrayList.add(UItem.asExpandableSwitch(1, LocaleController.getString(R.string.PlayerCoverColors), i + "/2").setChecked(i > 0).setCollapsed(!this.coverColorsExpanded).setClickCallback(view -> lambda_fillItems_2(i, view)));
        if (this.coverColorsExpanded) {
            arrayList.add(UItem.asRoundCheckbox(6, LocaleController.getString(R.string.PlayerCoverColorsFull)).setChecked(playerCoverColors).pad());
            arrayList.add(UItem.asRoundCheckbox(7, LocaleController.getString(R.string.PlayerCoverColorsMini)).setChecked(playerMiniCoverColors).pad());
        }
        arrayList.add(UItem.asCheck(2, LocaleController.getString(R.string.PlayerMotionCovers), LocaleController.getString(R.string.PlayerMotionCoversEnabled), true).setChecked(PlayerConfig.getPlayerMotionCovers()));
        arrayList.add(UItem.asCheck(5, LocaleController.getString(R.string.PlayerRhythmBackground), LocaleController.getString(R.string.PlayerRhythmBackgroundInfo), true).setChecked(PlayerConfig.getPlayerRhythmBackground()));
        arrayList.add(UItem.asCheck(8, LocaleController.getString(R.string.PlayerParticles), LocaleController.getString(R.string.PlayerParticlesInfo), true).setChecked(PlayerConfig.getPlayerParticles()));
        arrayList.add(UItem.asShadow(null));
        arrayList.add(UItem.asHeader(LocaleController.getString(R.string.PlayerLyrics)));
        arrayList.add(UItem.asCheck(3, LocaleController.getString(R.string.PlayerLyricsOnline), LocaleController.getString(R.string.PlayerLyricsOnlineInfo), true).setChecked(PlayerConfig.getLyricsOnline()));
        arrayList.add(UItem.asShadow(null));
        arrayList.add(UItem.asHeader(LocaleController.getString(R.string.PlayerLyricsProgress)));
        arrayList.add(UItem.asSlideView(9, new String[]{LocaleController.getString(R.string.BlurOff), LocaleController.getString(R.string.PlayerLyricsProgressHorizontal), LocaleController.getString(R.string.PlayerLyricsProgressVertical)}, PlayerConfig.getLyricsProgress(), obj -> lambda_fillItems_3((Integer) obj)));
        arrayList.add(UItem.asShadow(LocaleController.getString(R.string.PlayerLyricsProgressInfo)));
    }
    public /* synthetic */ void lambda_fillItems_2(int i, View view) {
        setCoverColors(i == 0, i == 0);
    }
    public /* synthetic */ void lambda_fillItems_3(Integer num) {
        PlayerConfig.setLyricsProgress(num.intValue());
        this.delegate.onLyricsProgressChanged();
    }

    private void onItemClick(UItem uItem, View view) {
        switch (uItem.id) {
            case 1:
                this.coverColorsExpanded = !this.coverColorsExpanded;
                this.adapter.update(true);
                break;
            case 2:
                boolean z = !PlayerConfig.getPlayerMotionCovers();
                PlayerConfig.setPlayerMotionCovers(z);
                setChecked(view, z);
                this.delegate.onMotionCoversChanged();
                break;
            case 3:
                boolean z2 = !PlayerConfig.getLyricsOnline();
                PlayerConfig.setLyricsOnline(z2);
                setChecked(view, z2);
                this.delegate.onLyricsOnlineChanged();
                break;
            case 5:
                boolean z3 = !PlayerConfig.getPlayerRhythmBackground();
                PlayerConfig.setPlayerRhythmBackground(z3);
                setChecked(view, z3);
                this.delegate.onRhythmBackgroundChanged();
                break;
            case 6:
                setCoverColors(!PlayerConfig.getPlayerCoverColors(), PlayerConfig.getPlayerMiniCoverColors());
                break;
            case 7:
                setCoverColors(PlayerConfig.getPlayerCoverColors(), !PlayerConfig.getPlayerMiniCoverColors());
                break;
            case 8:
                boolean z4 = !PlayerConfig.getPlayerParticles();
                PlayerConfig.setPlayerParticles(z4);
                setChecked(view, z4);
                this.delegate.onRhythmBackgroundChanged();
                break;
        }
    }

    private void setCoverColors(boolean z, boolean z2) {
        PlayerConfig.setPlayerCoverColors(z);
        PlayerConfig.setPlayerMiniCoverColors(z2);
        this.adapter.update(true);
        this.delegate.onCoverColorsChanged();
    }

    private static void setChecked(View view, boolean z) {
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(z);
        }
    }

    public static final class SwitchingResourcesProvider implements Theme.ResourcesProvider {
        Theme.ResourcesProvider current;

        public SwitchingResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
            this.current = resourcesProvider;
        }

        @Override
        public int getColor(int i) {
            Theme.ResourcesProvider resourcesProvider = this.current;
            return resourcesProvider != null ? resourcesProvider.getColor(i) : Theme.getColor(i);
        }

        @Override
        public int getColorOrDefault(int i) {
            Theme.ResourcesProvider resourcesProvider = this.current;
            return resourcesProvider != null ? resourcesProvider.getColorOrDefault(i) : Theme.getColor(i);
        }

        @Override
        public int getCurrentColor(int i) {
            Theme.ResourcesProvider resourcesProvider = this.current;
            return resourcesProvider != null ? resourcesProvider.getCurrentColor(i) : Theme.getColor(i);
        }

        @Override
        public void setAnimatedColor(int i, int i2) {
            Theme.ResourcesProvider resourcesProvider = this.current;
            if (resourcesProvider != null) {
                resourcesProvider.setAnimatedColor(i, i2);
            }
        }

        @Override
        public Drawable getDrawable(String str) {
            Theme.ResourcesProvider resourcesProvider = this.current;
            return resourcesProvider != null ? resourcesProvider.getDrawable(str) : Theme.getThemeDrawable(str);
        }

        @Override
        public Paint getPaint(String str) {
            Theme.ResourcesProvider resourcesProvider = this.current;
            return resourcesProvider != null ? resourcesProvider.getPaint(str) : Theme.getThemePaint(str);
        }

        @Override
        public boolean hasGradientService() {
            Theme.ResourcesProvider resourcesProvider = this.current;
            return resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
        }

        @Override
        public boolean isDark() {
            Theme.ResourcesProvider resourcesProvider = this.current;
            return resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        }


        @Override
        public void applyServiceShaderMatrix(int i, int i2, float f, float f2) {
            Theme.ResourcesProvider resourcesProvider = this.current;
            if (resourcesProvider != null) {
                resourcesProvider.applyServiceShaderMatrix(i, i2, f, f2);
            } else {
                Theme.applyServiceShaderMatrix(i, i2, f, f2);
            }
        }

        @Override
        public ColorFilter getAnimatedEmojiColorFilter() {
            Theme.ResourcesProvider resourcesProvider = this.current;
            return resourcesProvider != null ? resourcesProvider.getAnimatedEmojiColorFilter() : Theme.getAnimatedEmojiColorFilter(null);
        }
    }
}

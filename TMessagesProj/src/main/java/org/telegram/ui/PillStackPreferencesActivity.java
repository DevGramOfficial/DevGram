package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.pillstack.PillStackConfig;

import java.util.ArrayList;
import java.util.List;

/** DevGram: порт PillStackPreferencesActivity из exteraGram — настройки виджетов строки поиска (цветные иконки + перетаскивание + сброс). */
public class PillStackPreferencesActivity extends BaseFragment {
    private static final int INFINITE = 1000;
    private static final String INFO = "Настройте видимые виджеты. Нажмите, чтобы показать или скрыть, удерживайте и перетаскивайте для изменения порядка.";

    private UniversalRecyclerView listView;
    private ActionBarMenuItem resetItem;
    private Drawable reorderIcon;
    private int activeSectionId = -1;
    private int hiddenSectionId = -1;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Pill Stack");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == 0) resetToDefault();
            }
        });
        resetItem = actionBar.createMenu().addItem(0, R.drawable.msg_reset);
        resetItem.setContentDescription("Сбросить");

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.allowReorder(true);
        listView.listenReorder(this::updateConfigFromReorder);
        root.addView(listView, LayoutHelper.createFrame(-1, -1, Gravity.FILL));
        fragmentView = root;

        updateResetButtonVisibility();
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (reorderIcon == null) reorderIcon = ContextCompat.getDrawable(getContext(), R.drawable.list_reorder);

        items.add(UItem.asHeader("Настройки"));
        items.add(UItem.asCheck(INFINITE, "Бесконечная прокрутка").setChecked(PillStackConfig.getInfiniteScrolling()));
        items.add(UItem.asShadow(null));

        activeSectionId = -1;
        hiddenSectionId = -1;

        if (!PillStackConfig.getActivePills().isEmpty()) {
            activeSectionId = addSection(items, adapter, "Активные виджеты", PillStackConfig.getActivePills());
            items.add(UItem.asShadow(INFO));
        }
        if (!PillStackConfig.getHiddenPills().isEmpty()) {
            hiddenSectionId = addSection(items, adapter, "Скрытые виджеты", PillStackConfig.getHiddenPills());
            if (PillStackConfig.getActivePills().isEmpty()) items.add(UItem.asShadow(INFO));
        }
    }

    private int addSection(ArrayList<UItem> items, UniversalAdapter adapter, String title, List<Integer> ids) {
        adapter.whiteSectionStart();
        items.add(UItem.asHeader(title));
        int sectionId = adapter.reorderSectionStart();
        for (Integer id : ids) {
            PillStackConfig.PillInfo info = PillStackConfig.getPillInfo(id);
            if (info != null) items.add(createMenuItem(id, info));
        }
        adapter.reorderSectionEnd();
        adapter.whiteSectionEnd();
        return sectionId;
    }

    private UItem createMenuItem(int id, PillStackConfig.PillInfo info) {
        UItem item = UItem.asButton(id, info.iconRes, info.name);
        item.bind = view -> {
            if (view instanceof TextCell) {
                TextCell cell = (TextCell) view;
                cell.setColorfulIcon(info.iconColorTop, info.iconColorBottom, info.iconRes);
                cell.setImageLeft(21);
                cell.setOffsetFromImage(65);
                if (reorderIcon != null) {
                    cell.getValueImageView().setVisibility(View.VISIBLE);
                    cell.getValueImageView().setImageDrawable(reorderIcon);
                    cell.getValueImageView().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourceProvider), PorterDuff.Mode.MULTIPLY));
                }
            }
        };
        return item;
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == INFINITE) {
            PillStackConfig.setInfiniteScrolling(!PillStackConfig.getInfiniteScrolling());
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged);
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
            return;
        }
        if (PillStackConfig.getActivePills().contains(id)) {
            PillStackConfig.getActivePills().remove((Integer) id);
            if (!PillStackConfig.getHiddenPills().contains(id)) PillStackConfig.getHiddenPills().add(0, id);
        } else if (PillStackConfig.getHiddenPills().contains(id)) {
            PillStackConfig.getHiddenPills().remove((Integer) id);
            PillStackConfig.getActivePills().add(id);
            if (id == PillStackConfig.WEATHER) {
                // Re-enabling the widget is an explicit choice to retry location access.
                PillStackConfig.resetWeatherLocationPromptAttempted();
            }
        }
        saveAndNotify();
    }

    private void updateConfigFromReorder(int sectionId, ArrayList<UItem> newItems) {
        ArrayList<Integer> order = new ArrayList<>();
        for (UItem u : newItems) order.add(u.id);
        if (sectionId == activeSectionId) {
            PillStackConfig.getActivePills().clear();
            PillStackConfig.getActivePills().addAll(order);
        } else if (sectionId == hiddenSectionId) {
            PillStackConfig.getHiddenPills().clear();
            PillStackConfig.getHiddenPills().addAll(order);
        }
        saveAndNotify();
    }

    private void saveAndNotify() {
        PillStackConfig.savePillsLayout();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged);
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        updateResetButtonVisibility();
    }

    private void resetToDefault() {
        PillStackConfig.getActivePills().clear();
        PillStackConfig.getActivePills().addAll(PillStackConfig.getDefaultActivePills());
        PillStackConfig.getHiddenPills().clear();
        for (PillStackConfig.PillInfo info : PillStackConfig.getRegisteredPills()) {
            if (!PillStackConfig.getActivePills().contains(info.id)) PillStackConfig.getHiddenPills().add(info.id);
        }
        saveAndNotify();
    }

    private void updateResetButtonVisibility() {
        if (resetItem == null) return;
        boolean isDefault = PillStackConfig.getActivePills().equals(PillStackConfig.getDefaultActivePills());
        if (!isDefault && resetItem.getVisibility() == View.GONE) {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, true, 0.5f, true);
        } else if (isDefault && resetItem.getVisibility() == View.VISIBLE) {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, false, 0.5f, true);
        }
    }
}

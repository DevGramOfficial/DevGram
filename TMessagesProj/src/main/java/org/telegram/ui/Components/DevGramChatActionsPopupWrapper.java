/*
 * DevGram: per-chat actions shown from the three-dot menu.
 *
 * The placement and three-state ghost exclusions follow AyuGram's
 * ActionsPopupWrapper, adapted to DevGramGhostSettings.
 */
package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramGhostSettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class DevGramChatActionsPopupWrapper {

    public final FrameLayout swipeBack;

    private final BaseFragment fragment;
    private final PopupSwipeBackLayout swipeBackLayout;
    private final Theme.ResourcesProvider resourcesProvider;
    private final long dialogId;
    private final LinearLayout mainPage;
    private final LinearLayout detailPage;
    private final LinearLayout detailOptions;
    private boolean detailOpen;

    public DevGramChatActionsPopupWrapper(
            BaseFragment fragment,
            PopupSwipeBackLayout swipeBackLayout,
            long dialogId,
            boolean includeHistory,
            boolean includeExclusions,
            Runnable dismissPopup,
            Runnable openHistory,
            Theme.ResourcesProvider resourcesProvider
    ) {
        this.fragment = fragment;
        this.swipeBackLayout = swipeBackLayout;
        this.dialogId = dialogId;
        this.resourcesProvider = resourcesProvider;

        Activity activity = fragment.getParentActivity();
        swipeBack = new FrameLayout(activity) {
            @Override
            protected void onDetachedFromWindow() {
                resetDetail();
                super.onDetachedFromWindow();
            }
        };
        mainPage = page(activity);
        detailPage = page(activity);
        detailPage.setBackgroundColor(Theme.getColor(
                Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
        detailPage.setVisibility(View.GONE);
        swipeBack.addView(mainPage, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        swipeBack.addView(detailPage, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        mainPage.addView(backItem(activity, () -> swipeBackLayout.closeForeground()),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        mainPage.addView(createGap(activity), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        if (includeHistory) {
            mainPage.addView(actionItem(activity, R.drawable.msg_archive, "История удалённых", () -> {
                dismissPopup.run();
                openHistory.run();
            }, false), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
        if (includeExclusions) {
            mainPage.addView(actionItem(activity, R.drawable.msg_view_file, "Чтение",
                    () -> openDetail("read"), true),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            mainPage.addView(actionItem(activity, R.drawable.msg_edit, "Статус «печатает»",
                    () -> openDetail("typing"), true),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        detailPage.addView(backItem(activity, this::closeDetail),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        detailPage.addView(createGap(activity), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
        detailOptions = page(activity);
        detailPage.addView(detailOptions, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private LinearLayout page(Context context) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        return page;
    }

    private ActionBarMenuSubItem backItem(Context context, Runnable callback) {
        ActionBarMenuSubItem item = new ActionBarMenuSubItem(context, true, false, resourcesProvider);
        item.setTextAndIcon(LocaleController.getString(R.string.Back), R.drawable.msg_arrow_back);
        item.setMinimumWidth(AndroidUtilities.dp(196));
        item.setOnClickListener(v -> callback.run());
        return item;
    }

    private ActionBarMenuSubItem actionItem(
            Context context, int icon, String title, Runnable callback, boolean hasDetail
    ) {
        ActionBarMenuSubItem item = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
        item.setTextAndIcon(title, icon);
        item.setMinimumWidth(AndroidUtilities.dp(220));
        if (hasDetail) item.setRightIcon(R.drawable.msg_arrowright);
        item.setOnClickListener(v -> callback.run());
        return item;
    }

    private View createGap(Context context) {
        return new ActionBarPopupWindow.GapView(context, resourcesProvider);
    }

    private void openDetail(String kind) {
        detailOptions.removeAllViews();
        final int[] values = {0, 1, 2};
        final String[] labels = "read".equals(kind)
                ? new String[]{"Как в настройках", "Не отмечать", "Всегда отмечать"}
                : new String[]{"Как в настройках", "Скрывать", "Показывать"};
        final ArrayList<ActionBarMenuSubItem> rows = new ArrayList<>(values.length);
        int selected = DevGramGhostSettings.getDialogOverride(
                fragment.getCurrentAccount(), dialogId, kind);
        for (int i = 0; i < values.length; i++) {
            final int value = values[i];
            ActionBarMenuSubItem row = new ActionBarMenuSubItem(
                    fragment.getParentActivity(), true, i == 0, i == values.length - 1, resourcesProvider);
            row.setTextAndIcon(labels[i], 0);
            row.setMinimumWidth(AndroidUtilities.dp(220));
            row.setChecked(value == selected);
            row.setOnClickListener(v -> {
                DevGramGhostSettings.setDialogOverride(
                        fragment.getCurrentAccount(), dialogId, kind, value);
                for (int j = 0; j < rows.size(); j++) {
                    rows.get(j).setChecked(values[j] == value);
                }
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            });
            rows.add(row);
            detailOptions.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        detailOpen = true;
        swipeBackLayout.setSwipeBackDisallowed(true);
        mainPage.setVisibility(View.GONE);
        detailPage.setVisibility(View.VISIBLE);
        resizeForeground(detailPage);
    }

    private void closeDetail() {
        if (!detailOpen) return;
        detailOpen = false;
        swipeBackLayout.setSwipeBackDisallowed(false);
        detailPage.setVisibility(View.GONE);
        mainPage.setVisibility(View.VISIBLE);
        resizeForeground(mainPage);
    }

    private void resizeForeground(View page) {
        swipeBack.post(() -> {
            int width = swipeBack.getWidth() > 0 ? swipeBack.getWidth() : AndroidUtilities.dp(220);
            page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int index = swipeBackLayout.indexOfChild(swipeBack);
            if (index >= 0) {
                swipeBackLayout.setNewForegroundHeight(index, page.getMeasuredHeight(), true);
            }
        });
    }

    private void resetDetail() {
        detailOpen = false;
        swipeBackLayout.setSwipeBackDisallowed(false);
        detailPage.setVisibility(View.GONE);
        mainPage.setVisibility(View.VISIBLE);
    }
}

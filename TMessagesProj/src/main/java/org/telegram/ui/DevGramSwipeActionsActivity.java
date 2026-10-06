package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.DevGramSwipeAction;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.DevGramSwipeActionsPreviewCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Swipe actions settings, laid out like Extera's original screen. */
public class DevGramSwipeActionsActivity extends DevGramSettingsFragment {
    private static final int ID_PREVIEW = 1;
    private static final int ID_LOOP = 2;
    private static final int ID_REVERSED = 3;

    private final ArrayList<DevGramSwipeAction> enabled = new ArrayList<>();
    private UniversalRecyclerView listView;
    private DevGramSwipeActionsPreviewCell preview;

    @Override
    public boolean onFragmentCreate() {
        enabled.clear();
        enabled.addAll(DevGramSwipeAction.enabled());
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Действия свайпом");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        preview = new DevGramSwipeActionsPreviewCell(context, resourceProvider);
        preview.setActions(enabled);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.allowReorder(true);
        listView.listenReorder(this::whenReordered);
        org.telegram.ui.Components.DevGramSettingsLayout root =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        return fragmentView = root;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(ID_PREVIEW, preview));
        items.add(UItem.asShadow("Проведите по сообщению влево и, не отпуская палец, двигайте вверх или вниз для выбора действия."));

        adapter.whiteSectionStart();
        items.add(UItem.asHeader("Поведение"));
        items.add(UItem.asCheck(ID_LOOP, "Циклический выбор")
                .setChecked(DevGramConfig.swipeActionsLoop));
        items.add(UItem.asCheck(ID_REVERSED, "Обратное направление")
                .setChecked(DevGramConfig.swipeActionsReversed));
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow("При смене действия срабатывает короткий виброотклик. Обратное направление меняет порядок выбора вверх и вниз."));

        addSection(items, adapter, "Включённые действия", enabled, true);
        items.add(UItem.asShadow("Перетаскивайте действия, чтобы изменить их порядок. Нажмите на действие, чтобы отключить его."));

        ArrayList<DevGramSwipeAction> disabled = DevGramSwipeAction.disabled(enabled);
        if (!disabled.isEmpty()) {
            addSection(items, adapter, "Выключенные действия", disabled, false);
            items.add(UItem.asShadow(null));
        }
    }

    private void addSection(ArrayList<UItem> items, UniversalAdapter adapter, String title,
                            ArrayList<DevGramSwipeAction> actions, boolean isEnabled) {
        adapter.whiteSectionStart();
        items.add(UItem.asHeader(title));
        adapter.reorderSectionStart();
        for (DevGramSwipeAction action : actions) {
            items.add(ActionCell.Factory.of(action, isEnabled));
        }
        adapter.reorderSectionEnd();
        adapter.whiteSectionEnd();
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_LOOP) {
            DevGramConfig.setSwipeActionsLoop(!DevGramConfig.swipeActionsLoop);
            preview.updateActions();
            listView.adapter.update(true);
            return;
        }
        if (item.id == ID_REVERSED) {
            DevGramConfig.setSwipeActionsReversed(!DevGramConfig.swipeActionsReversed);
            listView.adapter.update(true);
            return;
        }
        if (!(item.object instanceof DevGramSwipeAction)) return;
        DevGramSwipeAction action = (DevGramSwipeAction) item.object;
        if (!enabled.remove(action)) {
            int insertion = 0;
            for (DevGramSwipeAction candidate : DevGramSwipeAction.values()) {
                if (candidate == action) break;
                if (enabled.contains(candidate)) insertion++;
            }
            enabled.add(insertion, action);
        }
        save();
        listView.adapter.update(true);
    }

    private void whenReordered(int sectionId, ArrayList<UItem> items) {
        if (sectionId != 0) return;
        enabled.clear();
        for (UItem item : items) {
            if (item.object instanceof DevGramSwipeAction) {
                enabled.add((DevGramSwipeAction) item.object);
            }
        }
        save();
    }

    private void save() {
        DevGramConfig.setSwipeActions(DevGramSwipeAction.serialize(enabled));
        if (preview != null) preview.setActions(enabled);
    }

    private static class ActionCell extends FrameLayout {
        private final ImageView icon;
        private final TextView title;
        private final ImageView reorder;
        private boolean divider;

        ActionCell(Context context) {
            super(context);
            setWillNotDraw(false);
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            addView(icon, LayoutHelper.createFrameRelatively(48, 50,
                    Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            title = new TextView(context);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER_VERTICAL);
            addView(title, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, 50,
                    Gravity.START, 60, 0, 56, 0));

            reorder = new ImageView(context);
            reorder.setScaleType(ImageView.ScaleType.CENTER);
            reorder.setImageResource(R.drawable.list_reorder);
            addView(reorder, LayoutHelper.createFrameRelatively(50, 50,
                    Gravity.END | Gravity.CENTER_VERTICAL));
        }

        void set(DevGramSwipeAction action, boolean divider) {
            title.setText(action.title);
            icon.setImageResource(action.iconRes);
            icon.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            reorder.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
            this.divider = divider;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (divider) {
                canvas.drawLine(AndroidUtilities.dp(60), getHeight() - 1, getWidth(),
                        getHeight() - 1, Theme.dividerPaint);
            }
        }

        static final class Factory extends UItem.UItemFactory<ActionCell> {
            static {
                setup(new Factory());
            }

            @Override
            public ActionCell createView(Context context, RecyclerListView listView, int currentAccount,
                                         int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new ActionCell(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                                 UniversalRecyclerView listView) {
                ((ActionCell) view).set((DevGramSwipeAction) item.object, divider);
            }

            static UItem of(DevGramSwipeAction action, boolean enabled) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = 100 + action.ordinal();
                item.object = action;
                item.checked = enabled;
                return item;
            }
        }
    }
}

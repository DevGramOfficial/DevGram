package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.recorder.Weather;

import java.util.ArrayList;
import java.util.List;

// DevGram: экран поиска города для виджета погоды. Поле поиска — в ActionBar
// (как в поиске контактов/чатов), поэтому клавиатура открывается системно и
// стабильно на всех прошивках (в отличие от BottomSheet, где One UI её глушил).
public class DevGramWeatherCityActivity extends BaseFragment {

    public interface OnChosen {
        void run();
    }

    private OnChosen onChosen;
    private final List<Weather.CityResult> results = new ArrayList<>();
    private Adapter adapter;
    private TextView hintView;
    private ActionBarMenuItem searchItem;
    private String lastQuery = "";
    private final Runnable searchRunnable = () -> doSearch(lastQuery);

    public DevGramWeatherCityActivity setOnChosen(OnChosen onChosen) {
        this.onChosen = onChosen;
        return this;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Город погоды");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.msg_search).setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {}

                    @Override
                    public void onSearchCollapse() {
                        // Уходим с экрана, если поиск свернули (экран сам по себе — поиск).
                        finishFragment();
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        lastQuery = editText.getText().toString().trim();
                        AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                        if (lastQuery.length() < 2) {
                            results.clear();
                            if (adapter != null) adapter.notifyDataSetChanged();
                            setHint("Начните вводить название города");
                            return;
                        }
                        setHint("Поиск…");
                        AndroidUtilities.runOnUIThread(searchRunnable, 450); // дебаунс
                    }
                });
        searchItem.setSearchFieldHint("Введите город…");

        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        hintView = new TextView(context);
        hintView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        hintView.setGravity(Gravity.CENTER);
        hintView.setText("Начните вводить название города");
        content.addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 30, 0, 30, 0));

        RecyclerListView listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= results.size()) return;
            Weather.CityResult r = results.get(position);
            Weather.setManualCity(r.title, r.lat, r.lng);
            if (onChosen != null) onChosen.run();
            finishFragment();
        });
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = content;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Разворачиваем поиск и открываем клавиатуру сразу при входе.
        AndroidUtilities.runOnUIThread(() -> {
            if (searchItem != null) {
                searchItem.openSearch(true);
            }
        }, 100);
    }

    private void setHint(String text) {
        if (hintView == null) return;
        hintView.setText(text);
        hintView.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void doSearch(String query) {
        if (query.length() < 2) return;
        Weather.searchCity(query, list -> {
            if (!query.equals(lastQuery)) return; // устаревший ответ
            results.clear();
            results.addAll(list);
            if (adapter != null) adapter.notifyDataSetChanged();
            if (results.isEmpty()) {
                setHint("Ничего не найдено");
            } else if (hintView != null) {
                hintView.setVisibility(View.GONE);
            }
        });
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new CityRow(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((CityRow) holder.itemView).bind(results.get(position));
        }

        @Override
        public int getItemCount() { return results.size(); }
    }

    // Строка результата: название города + регион/страна.
    private class CityRow extends FrameLayout {
        private final TextView titleView, subtitleView;

        CityRow(Context context) {
            super(context);
            setBackgroundDrawable(Theme.getSelectorDrawable(false));
            setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));

            titleView = new TextView(context);
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setMaxLines(1);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.TOP, 0, 6, 0, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setMaxLines(1);
            subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.TOP, 0, 30, 0, 0));

            setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(60)));
        }

        void bind(Weather.CityResult r) {
            titleView.setText(r.title);
            subtitleView.setText(r.subtitle);
            subtitleView.setVisibility(r.subtitle == null || r.subtitle.isEmpty() ? GONE : VISIBLE);
        }
    }
}

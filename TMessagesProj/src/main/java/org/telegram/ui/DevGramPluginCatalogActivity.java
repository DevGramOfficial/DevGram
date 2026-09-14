/*
 * DevGram: Каталог плагинов. Плагины публикуют разработчики каналов со значком 🧩 (или команда),
 * выбирая фильтр-категорию. Команда управляет фильтрами и может удалить плагин с блокировкой файла.
 * Поиск, фильтры-чипы, аватарки, описание, канал, установка. Админ-действия требуют входа команды.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class DevGramPluginCatalogActivity extends BaseFragment {

    private static final String FILTER_INSTALLED = "\u0000installed";

    private RecyclerListView listView;
    private Adapter adapter;
    private final ArrayList<DevGramPlugins.CatalogEntry> all = new ArrayList<>();
    private final ArrayList<DevGramPlugins.CatalogEntry> shown = new ArrayList<>();
    private final ArrayList<String> filters = new ArrayList<>();
    private boolean loading = true;
    private boolean team;
    private String query = "";
    private String activeFilter = "";
    private LinearLayout chipRow;
    private TextView catalogSummary;
    private LinearLayout emptyView;
    private TextView emptyTitle;
    private TextView emptySubtitle;
    private TextView emptyAction;
    private TextView sortButton;
    private EditText searchField;
    private ImageView searchClear;
    private org.telegram.ui.ActionBar.ActionBarMenuItem moderationItem;
    private int sortMode;
    private int loadGeneration;
    private boolean destroyed;
    private RadialProgressView progressView;
    private final java.util.HashSet<String> installing = new java.util.HashSet<>();

    @Override
    public View createView(Context context) {
        destroyed = false;
        team = DevGramBadges.isTeam(getUserConfig().getClientUserId());

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Каталог плагинов");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    openModeration();
                } else if (id == 2) {
                    presentFragment(new DevGramMySubmissionsActivity());
                } else if (id >= 10 && id <= 12) {
                    sortMode = id - 10;
                    applyFilter();
                }
            }
        });
        // вход в панель модерации — скрыт по умолчанию, показываем только модераторам (по tg-id)
        moderationItem = actionBar.createMenu().addItem(1, R.drawable.msg_shareout);
        moderationItem.setVisibility(View.GONE);
        org.telegram.ui.ActionBar.ActionBarMenuItem more = actionBar.createMenu().addItem(3, R.drawable.ic_ab_other);
        more.addSubItem(2, R.drawable.msg_info, "Мои заявки");

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        // --- поиск (в «пилюле») ---
        FrameLayout searchWrap = new FrameLayout(context);
        searchWrap.setElevation(AndroidUtilities.dp(1));
        searchWrap.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(24),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.msg_search);
        searchIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        searchWrap.addView(searchIcon, LayoutHelper.createFrame(20, 20, Gravity.CENTER_VERTICAL | Gravity.LEFT, 14, 0, 0, 0));
        searchField = new EditText(context);
        searchField.setHint("Название, автор или описание");
        searchField.setSingleLine(true);
        searchField.setBackground(null);
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        searchField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        searchField.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        searchField.setPadding(AndroidUtilities.dp(44), 0, AndroidUtilities.dp(48), 0);
        searchField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase();
                if (searchClear != null) searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }
        });
        searchWrap.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        searchClear = new ImageView(context);
        searchClear.setImageResource(R.drawable.msg_close);
        searchClear.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        searchClear.setScaleType(ImageView.ScaleType.CENTER);
        searchClear.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        searchClear.setVisibility(View.GONE);
        searchClear.setOnClickListener(v -> searchField.setText(""));
        searchWrap.addView(searchClear, LayoutHelper.createFrame(44, 44, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 2, 0));
        container.addView(searchWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 12, 12, 12, 10));

        // --- категории каталога ---
        LinearLayout categoryHeader = new LinearLayout(context);
        categoryHeader.setOrientation(LinearLayout.HORIZONTAL);
        categoryHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView categoryTitle = new TextView(context);
        categoryTitle.setText("Подборка плагинов");
        categoryTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        categoryTitle.setTypeface(AndroidUtilities.bold());
        categoryTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        heading.addView(categoryTitle);
        catalogSummary = new TextView(context);
        catalogSummary.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        catalogSummary.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        heading.addView(catalogSummary, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        categoryHeader.addView(heading, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        sortButton = pill(context, sortTitle(), true);
        sortButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        sortButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(7));
        sortButton.setOnClickListener(v -> showSortMenu());
        categoryHeader.addView(sortButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        container.addView(categoryHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 2, 16, 10));

        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipToPadding(false);
        chipRow = new LinearLayout(context);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        chipScroll.addView(chipRow, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        container.addView(chipScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        rebuildChips();

        // --- список карточек ---
        FrameLayout listWrap = new FrameLayout(context);
        listView = new RecyclerListView(context, resourceProvider);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(12));
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listWrap.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        ImageView emptyIcon = new ImageView(context);
        emptyIcon.setImageResource(R.drawable.devgram_plugins);
        emptyIcon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
        emptyIcon.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(28),
                Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider), .12f)));
        emptyIcon.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(18), AndroidUtilities.dp(18), AndroidUtilities.dp(18));
        emptyView.addView(emptyIcon, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL));
        emptyTitle = new TextView(context);
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        emptyTitle.setTypeface(AndroidUtilities.bold());
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        emptyView.addView(emptyTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));
        emptySubtitle = new TextView(context);
        emptySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptySubtitle.setGravity(Gravity.CENTER);
        emptySubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        emptyView.addView(emptySubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        emptyAction = new TextView(context);
        emptyAction.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyAction.setTypeface(AndroidUtilities.bold());
        emptyAction.setGravity(Gravity.CENTER);
        emptyAction.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        emptyAction.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        emptyAction.setOnClickListener(v -> {
            if (!query.isEmpty() || !activeFilter.isEmpty()) {
                activeFilter = "";
                if (searchField != null) searchField.setText("");
                rebuildChips();
                applyFilter();
            } else loadAll();
        });
        emptyView.addView(emptyAction, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));
        emptyView.setVisibility(View.GONE);
        listWrap.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // DevGram: крутящийся индикатор загрузки каталога — раньше при входе был только
        // текст «Загрузка каталога…» без какой-либо анимации.
        progressView = new RadialProgressView(context, resourceProvider);
        progressView.setSize(AndroidUtilities.dp(28));
        progressView.setVisibility(View.GONE);
        listWrap.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        container.addView(listWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        loadAll();
        return fragmentView = container;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (fragmentView != null && !destroyed) loadAll();
    }

    // ---------- чипы ----------
    private void rebuildChips() {
        if (destroyed || chipRow == null || !chipRow.isAttachedToWindow() && fragmentView != null) return;
        Context context = chipRow.getContext();
        if (context == null) return;
        chipRow.removeAllViews();
        ArrayList<String> names = new ArrayList<>();
        names.add("");
        names.add(FILTER_INSTALLED);
        names.addAll(filters);
        for (String name : names) {
            final String f = name;
            boolean on = activeFilter.equals(f);
            TextView chip = new TextView(context);
            int count = countForFilter(f);
            String label = name.isEmpty() ? "Все" : FILTER_INSTALLED.equals(name) ? "Установленные" : name;
            chip.setText((on ? "✓  " : "") + label + "  " + count);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chip.setTypeface(on ? AndroidUtilities.bold() : Typeface.DEFAULT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            chip.setTextColor(on ? Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(20),
                    on ? Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider)
                            : Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                    Theme.getColor(Theme.key_listSelector, resourceProvider)));
            chip.setOnClickListener(v -> { activeFilter = f; rebuildChips(); applyFilter(); });
            if (team && !f.isEmpty() && !FILTER_INSTALLED.equals(f)) {
                chip.setOnLongClickListener(v -> { showFilterActions(f); return true; });
            }
            chipRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
        if (team) {
            TextView add = new TextView(context);
            add.setText("＋  Категория");
            add.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            add.setTypeface(AndroidUtilities.bold());
            add.setGravity(Gravity.CENTER);
            add.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            add.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
            add.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(17),
                    Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
            add.setOnClickListener(v -> addFilterDialog());
            chipRow.addView(add, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
        if (catalogSummary != null) {
            catalogSummary.setText(shown.size() + " из " + all.size());
        }
    }

    private int countForFilter(String filter) {
        if (filter == null || filter.isEmpty()) return all.size();
        int count = 0;
        for (DevGramPlugins.CatalogEntry entry : all) {
            if (FILTER_INSTALLED.equals(filter) ? DevGramPlugins.isInstalled(entry.id) : filter.equals(entry.filter)) count++;
        }
        return count;
    }

    // ---------- данные ----------
    private void loadAll() {
        final int generation = ++loadGeneration;
        loading = true;
        // DevGram: applyFilter() (она же включает индикатор загрузки) раньше вызывалась
        // только ВНУТРИ асинхронных колбэков fetchFilters/fetchCatalogFast — то есть уже
        // ПОСЛЕ того, как loading успевал стать false. Индикатор загрузки из-за этого не
        // показывался вообще ни разу, с самого начала (ни старый текст, ни новая крутилка).
        // Вызываем сразу, синхронно — до того, как сеть успеет ответить.
        applyFilter();
        DevGramPlugins.fetchFilters(f -> {
            if (!isLoadActive(generation)) return;
            filters.clear();
            filters.addAll(f);
            rebuildChips();
        });
        DevGramPlugins.fetchCatalogFast(entries -> {
            if (!isLoadActive(generation)) return;
            all.clear();
            all.addAll(entries);
            loading = false;
            applyFilter();
            // Фильтры и каталог загружаются параллельно. Категории могли отрисоваться раньше,
            // когда список all был ещё пустым, поэтому обновляем их счётчики после каталога.
            rebuildChips();
        }, entries -> {
            if (!isLoadActive(generation)) return;
            all.clear();
            all.addAll(entries);
            applyFilter();
            rebuildChips();
        });
        // показать вход в модерацию только модераторам (по их Telegram-ID)
        DevGramPlugins.fetchModerators(m -> {
            if (isLoadActive(generation) && moderationItem != null) {
                long myTg = getUserConfig().getClientUserId();
                moderationItem.setVisibility(DevGramPlugins.canSeeModeration(myTg) ? View.VISIBLE : View.GONE);
            }
        });
    }

    private boolean isLoadActive(int generation) {
        return !destroyed && generation == loadGeneration && fragmentView != null && getParentActivity() != null;
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        loadGeneration++;
        chipRow = null;
        catalogSummary = null;
        emptyView = null;
        emptyTitle = null;
        emptySubtitle = null;
        emptyAction = null;
        sortButton = null;
        searchField = null;
        searchClear = null;
        moderationItem = null;
        adapter = null;
        listView = null;
        super.onFragmentDestroy();
    }

    private boolean matches(DevGramPlugins.CatalogEntry e) {
        if (FILTER_INSTALLED.equals(activeFilter)) {
            if (!DevGramPlugins.isInstalled(e.id)) return false;
        } else if (!activeFilter.isEmpty() && !activeFilter.equals(e.filter)) return false;
        if (query.isEmpty()) return true;
        return (e.name + " " + e.desc + " " + e.author + " " + e.channel).toLowerCase().contains(query);
    }

    private void applyFilter() {
        shown.clear();
        for (DevGramPlugins.CatalogEntry e : all) {
            if (matches(e)) shown.add(e);
        }
        if (sortMode == 1) shown.sort((a, b) -> Double.compare(b.rating, a.rating));
        else if (sortMode == 2) shown.sort((a, b) -> Integer.compare(b.reviews, a.reviews));
        else shown.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        if (adapter != null) adapter.notifyDataSetChanged();
        if (catalogSummary != null) catalogSummary.setText(shown.size() + " из " + all.size());
        if (sortButton != null) sortButton.setText(sortTitle());
        if (progressView != null) {
            progressView.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (emptyView != null) {
            if (loading) {
                emptyView.setVisibility(View.GONE);
            } else if (shown.isEmpty()) {
                boolean filtered = !query.isEmpty() || !activeFilter.isEmpty();
                emptyTitle.setText(all.isEmpty() ? "Каталог пока пуст" : "Ничего не найдено");
                emptySubtitle.setText(all.isEmpty()
                        ? "Новые плагины появятся здесь после проверки командой DevGram."
                        : "Попробуйте изменить запрос или выбрать другую категорию.");
                emptyAction.setText(filtered ? "Сбросить фильтры" : "Обновить");
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
            }
        }
    }

    private String sortTitle() {
        return sortMode == 1 ? "★ Рейтинг" : sortMode == 2 ? "Отзывы" : "Сначала новые";
    }

    private void showSortMenu() {
        String[] choices = {"Сначала новые", "По рейтингу", "По количеству отзывов"};
        DevGramPluginUi.showChoices(this, "Сортировка", "Выберите порядок карточек в каталоге.",
                choices, new int[]{R.drawable.msg_recent, R.drawable.msg_fave, R.drawable.msg_discussion},
                choices.length, which -> {
                    sortMode = which;
                    applyFilter();
                });
    }

    // ---------- карточка плагина ----------
    private View createCard(Context context, DevGramPlugins.CatalogEntry e) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(22),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                Theme.getColor(Theme.key_listSelector, resourceProvider)));
        card.setElevation(AndroidUtilities.dp(1));
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(15), AndroidUtilities.dp(16), AndroidUtilities.dp(15));
        card.setOnClickListener(v -> presentFragment(new DevGramPluginDetailsActivity(e)));
        ScaleStateListAnimator.apply(card, .018f, 1.2f);

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.devgram_plugins);
        icon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        icon.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider)));
        icon.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14));
        icon.setClipToOutline(true);
        if (e.icon != null && !e.icon.isEmpty()) loadIcon(icon, e.icon);
        head.addView(icon, LayoutHelper.createLinear(62, 62, Gravity.CENTER_VERTICAL));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(TextUtils.isEmpty(e.name) ? e.id : e.name);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        titleCol.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView author = new TextView(context);
        author.setText(TextUtils.isEmpty(e.author) ? "Автор не указан" : e.author);
        author.setSingleLine(true);
        author.setEllipsize(TextUtils.TruncateAt.END);
        author.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        author.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        titleCol.addView(author, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        head.addView(titleCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 13, 0, 8, 0));

        if (team) {
            ImageView del = new ImageView(context);
            del.setImageResource(R.drawable.msg_delete);
            del.setColorFilter(Theme.getColor(Theme.key_text_RedRegular));
            del.setScaleType(ImageView.ScaleType.CENTER);
            del.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
            del.setOnClickListener(v -> confirmDelete(e));
            head.addView(del, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL));
        }
        card.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout badges = new LinearLayout(context);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setGravity(Gravity.CENTER_VERTICAL);
        if (!e.version.isEmpty()) badges.addView(pill(context, "v" + e.version, false),
                LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
        if (e.rating > 0) badges.addView(pill(context,
                        String.format(java.util.Locale.US, "★ %.1f · %d", e.rating, e.reviews), true),
                LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
        if (!e.filter.isEmpty()) badges.addView(pill(context, e.filter, false),
                LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
        if (DevGramPlugins.isInstalled(e.id)) badges.addView(pill(context, "✓ Установлен", true));
        if (badges.getChildCount() > 0) card.addView(badges,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        TextView desc = new TextView(context);
        desc.setText(TextUtils.isEmpty(e.desc) ? "Описание плагина не указано." : e.desc);
        desc.setTextColor(Theme.getColor(TextUtils.isEmpty(e.desc)
                ? Theme.key_windowBackgroundWhiteGrayText2 : Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        desc.setMaxLines(3);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        desc.setLineSpacing(AndroidUtilities.dp(2), 1f);
        card.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        if (!e.channel.isEmpty()) {
            TextView ch = new TextView(context);
            ch.setText("🧩  " + e.channel);
            ch.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            ch.setSingleLine(true);
            ch.setEllipsize(TextUtils.TruncateAt.END);
            ch.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
            ch.setOnClickListener(v -> openChannel(e.channel));
            bottom.addView(ch, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        } else {
            TextView details = new TextView(context);
            details.setText("Подробнее");
            details.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            details.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
            bottom.addView(details, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        }

        boolean installed = DevGramPlugins.isInstalled(e.id);
        boolean busy = installing.contains(e.id);
        TextView btn = new TextView(context);
        btn.setText(busy ? "Загрузка…" : installed ? "Обновить" : "Установить");
        btn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        btn.setTypeface(AndroidUtilities.bold());
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        btn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(20),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        btn.setEnabled(!busy);
        btn.setAlpha(busy ? .65f : 1f);
        btn.setOnClickListener(v -> install(e));
        ScaleStateListAnimator.apply(btn, .04f, 1.2f);
        bottom.addView(btn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        card.addView(bottom, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        LinearLayout outer = new LinearLayout(context);
        outer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(5), AndroidUtilities.dp(12), AndroidUtilities.dp(5));
        outer.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return outer;
    }

    private TextView pill(Context ctx, String text, boolean accent) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int col = accent ? Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider)
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider);
        t.setTextColor(col);
        t.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(3), AndroidUtilities.dp(7), AndroidUtilities.dp(3));
        t.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(7),
                Theme.multAlpha(col, 0.12f)));
        return t;
    }

    // ---------- действия ----------
    private void install(DevGramPlugins.CatalogEntry e) {
        if (e.isPackage) {
            // .dgplugin-пакет: качаем бинарь из архивного канала и ставим
            if (e.packageMsg == 0) {
                BulletinFactory.of(this).createErrorBulletin("Пакет ещё не размещён в архиве").show();
                return;
            }
            installing.add(e.id);
            if (adapter != null) adapter.notifyDataSetChanged();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Скачиваю пакет «" + e.name + "»…").show();
            org.telegram.messenger.DevGramPackages.installCatalogPackage(e, ok -> {
                    installing.remove(e.id);
                    if (adapter != null) adapter.notifyDataSetChanged();
                    BulletinFactory.of(this).createSimpleBulletin(ok ? R.raw.contact_check : R.raw.error,
                            ok ? "Плагин установлен: " + e.name : "Не удалось установить пакет").show();
            });
            return;
        }
        if (e.source == null || e.source.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin("У плагина нет исходника в каталоге").show();
            return;
        }
        DevGramPlugins.trustFromChannel(e.source);
        boolean ok = DevGramPlugins.install(e.source, e.id, true);
        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                ok ? "Плагин установлен: " + e.name : "Не удалось установить").show();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void confirmDelete(DevGramPlugins.CatalogEntry e) {
        String[] actions = {"Удалить с возможностью публикации", "Удалить и заблокировать файл"};
        DevGramPluginUi.showChoices(this, "Удалить «" + e.name + "»",
                "При блокировке этот же файл нельзя будет опубликовать повторно.", actions,
                new int[]{R.drawable.msg_delete, R.drawable.msg_block}, 0,
                which -> DevGramPluginUi.showTextInput(this,
                        which == 1 ? "Удалить и заблокировать" : "Удалить из каталога",
                        "Причина сохранится в истории плагина.", "Укажите причину удаления",
                        "", which == 1 ? "Удалить и заблокировать" : "Удалить плагин", true,
                        reason -> performCatalogDelete(e, reason, which == 1)));
    }

    private void performCatalogDelete(DevGramPlugins.CatalogEntry entry, String reason, boolean block) {
        ensureAdmin(() -> {
            boolean ok = block ? DevGramPlugins.catalogDeleteAndBlock(entry, reason)
                    : DevGramPlugins.catalogDelete(entry, reason);
            if (!ok) {
                BulletinFactory.of(this).createErrorBulletin("Не удалось удалить плагин").show();
                return;
            }
            all.remove(entry);
            applyFilter();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                    block ? "Удалено и заблокировано" : "Удалено из каталога").show();
        });
    }

    private void addFilterDialog() {
        DevGramPluginUi.showTextInput(this, "Новая категория",
                "Категория появится в строке фильтров каталога.", "Название категории",
                "", "Добавить категорию", false, name -> {
            ensureAdmin(() -> {
                ArrayList<String> updated = new ArrayList<>(filters);
                if (!updated.contains(name)) updated.add(name);
                DevGramPlugins.saveFilters(updated, ok -> {
                    if (ok) {
                        filters.clear();
                        filters.addAll(updated);
                        rebuildChips();
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Фильтр добавлен").show();
                    } else {
                        BulletinFactory.of(this).createErrorBulletin("Не удалось сохранить фильтр в каталоге").show();
                    }
                });
            });
        });
    }

    private void confirmRemoveFilter(String name) {
        DevGramPluginUi.showChoices(this, "Удалить категорию?",
                "Плагины останутся в каталоге, исчезнет только категория «" + name + "».",
                new String[]{"Удалить категорию"}, new int[]{R.drawable.msg_delete}, 0,
                ignored -> ensureAdmin(() -> {
            ArrayList<String> updated = new ArrayList<>(filters);
            updated.remove(name);
            DevGramPlugins.saveFilters(updated, ok -> {
                if (ok) {
                    filters.clear();
                    filters.addAll(updated);
                    if (activeFilter.equals(name)) activeFilter = "";
                    rebuildChips();
                    applyFilter();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Категория удалена").show();
                } else {
                    BulletinFactory.of(this).createErrorBulletin("Не удалось удалить категорию").show();
                }
            });
        }));
    }

    private void showFilterActions(String name) {
        int index = filters.indexOf(name);
        if (index < 0) return;
        ArrayList<String> actions = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();
        if (index > 0) {
            actions.add("Переместить левее");
            ids.add(-1);
            icons.add(R.drawable.msg_arrow_back);
        }
        if (index < filters.size() - 1) {
            actions.add("Переместить правее");
            ids.add(1);
            icons.add(R.drawable.msg_arrow_forward);
        }
        actions.add("Удалить категорию");
        ids.add(0);
        icons.add(R.drawable.msg_delete);
        int[] iconArray = new int[icons.size()];
        for (int i = 0; i < icons.size(); i++) iconArray[i] = icons.get(i);
        DevGramPluginUi.showChoices(this, name, "Управление категорией каталога",
                actions.toArray(new String[0]), iconArray, actions.size() - 1, which -> {
            int action = ids.get(which);
            if (action == 0) {
                confirmRemoveFilter(name);
            } else {
                moveFilter(name, action);
            }
        });
    }

    private void moveFilter(String name, int direction) {
        int from = filters.indexOf(name);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= filters.size()) return;
        ArrayList<String> reordered = new ArrayList<>(filters);
        java.util.Collections.swap(reordered, from, to);
        ensureAdmin(() -> DevGramPlugins.saveFilters(reordered, ok -> {
            if (ok) {
                filters.clear();
                filters.addAll(reordered);
                rebuildChips();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Порядок категорий сохранён").show();
            } else {
                BulletinFactory.of(this).createErrorBulletin("Не удалось изменить порядок категорий").show();
            }
        }));
    }

    private void openChannel(String channel) {
        String u = channel.trim();
        if (u.startsWith("@")) u = u.substring(1);
        if (u.startsWith("http")) Browser.openUrl(getContext(), u);
        else if (!u.isEmpty()) Browser.openUrl(getContext(), "https://t.me/" + u);
    }

    // Открыть панель модерации: вход → проверка прав (главный админ или модератор) → переход.
    private void openModeration() {
        ensureAdmin(() -> DevGramPlugins.fetchModerators(m -> {
            if (DevGramPlugins.isModerator()) {
                presentFragment(new DevGramModerationActivity());
            } else {
                BulletinFactory.of(this).createErrorBulletin("Нет доступа к модерации").show();
            }
        }));
    }

    // ---------- вход команды (для админ-действий) ----------
    private void ensureAdmin(Runnable onReady) {
        if (DevGramPlugins.canManageVerified()) {
            onReady.run();
            return;
        }
        Context context = getParentActivity();
        if (context == null) return;
        EditTextBoldCursor emailEt = makeInput(context, "Email команды", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
        EditTextBoldCursor passEt = makeInput(context, "Пароль", InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        passEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        box.addView(emailEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        box.addView(passEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 12f, 0f, 0f));
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Вход команды DevGram");
        b.setView(box);
        b.setPositiveButton("Войти", (d, w) -> {
            String email = emailEt.getText().toString().trim();
            String pass = passEt.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) return;
            DevGramBadges.signIn(email, pass, (ok, err) -> {
                if (ok) onReady.run();
                else Toast.makeText(getParentActivity(), "Не удалось войти: " + err, Toast.LENGTH_LONG).show();
            });
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        emailEt.requestFocus();
        AndroidUtilities.showKeyboard(emailEt);
    }

    private EditTextBoldCursor makeInput(Context context, String hint, int inputType) {
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        et.setInputType(inputType);
        et.setHint(hint);
        et.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        et.setCursorSize(AndroidUtilities.dp(20));
        et.setCursorWidth(1.5f);
        et.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        return et;
    }

    // ---------- аватарка ----------
    private void loadIcon(final ImageView iv, final String url) {
        final Context ctx = getContext();
        if (ctx == null) return;
        java.io.File dir = new java.io.File(ctx.getCacheDir(), "devgram_icons");
        dir.mkdirs();
        final java.io.File cache = new java.io.File(dir, Integer.toHexString(url.hashCode()) + ".img");
        if (cache.exists() && cache.length() > 0) {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(cache.getAbsolutePath());
            if (bmp != null) { applyIcon(iv, bmp); return; }
        }
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "DevGram");
                java.io.InputStream in = c.getInputStream();
                java.io.File tmp = new java.io.File(cache.getAbsolutePath() + ".tmp");
                java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                c.disconnect();
                tmp.renameTo(cache);
                final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(cache.getAbsolutePath());
                if (bmp != null) AndroidUtilities.runOnUIThread(() -> applyIcon(iv, bmp));
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
            }
        }).start();
    }

    private void applyIcon(ImageView iv, android.graphics.Bitmap bmp) {
        iv.setColorFilter(null);
        iv.setPadding(0, 0, 0, 0);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(bmp);
    }

    // ---------- адаптер ----------
    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            FrameLayout holder = new FrameLayout(parent.getContext());
            holder.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return new RecyclerListView.Holder(holder);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            FrameLayout holder = (FrameLayout) h.itemView;
            holder.removeAllViews();
            holder.addView(createCard(holder.getContext(), shown.get(position)),
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}

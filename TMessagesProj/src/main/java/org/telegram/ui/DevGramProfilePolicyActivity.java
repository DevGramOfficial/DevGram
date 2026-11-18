package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import org.telegram.ui.Cells.TextSettingsCell;

/** Live access policy. Catalog moderator roles are intentionally managed elsewhere. */
public class DevGramProfilePolicyActivity extends DevGramSettingsFragment {
    public static final LinkedHashMap<String, String> LABELS = new LinkedHashMap<>();
    static {
        String[] entries = {
            "profile.notices|Уведомления кастомизации", "profile.comments|Комментарии профиля", "profile.communities|Оформление каналов, групп и форумов",
            "profile.palette|Палитра профиля", "profile.gradient|Статичный градиент", "profile.aurora|Северное сияние", "profile.mesh|Живой шёлк",
            "profile.media|Статичные фоны профиля", "profile.media.animated|Анимированные фоны профиля", "profile.opacity|Затемнение фона", "profile.blur|Размытие фона",
            "profile.cards|Цвет карточек", "profile.cards.glass|Жидкое стекло карточек", "profile.cards.volume|Объём карточек", "profile.buttons|Оформление кнопок", "profile.layout|Компоновка профиля", "profile.layout.advanced|Своя расстановка элементов",
            "profile.rows|Порядок и видимость строк", "avatar.shape|Форма аватара", "avatar.frame|Базовая рамка", "avatar.frame.gradient|Градиентная рамка",
            "avatar.frame.orbit|Орбитальная рамка", "avatar.frame.stars|Звёздная рамка", "avatar.layers|Слои рамки", "avatar.nodes|Студия узлов",
            "avatar.projects|Проекты рамок", "profile.pattern.dots|Точки на фоне", "profile.pattern.stars|Звёздная пыль", "profile.pattern.waves|Волны на фоне", "profile.pattern.grid|Сетка на фоне",
            "profile.nick|Цвет ника", "profile.name.effects|Эффекты имени", "profile.thought|Облачко мысли", "badge.choice|Выбор значка", "profile.font|Шрифт имени", "chat.bubble|Оформление сообщений", "chat.font|Шрифт сообщений", "chat.badge|Статичные фоновые бейджи", "chat.badge.animated|Анимированные фоновые бейджи",
            "profile.blocks|Блоки профиля", "profile.blocks.media|Статичные изображения и мини-баннеры", "profile.blocks.media.animated|Анимированные изображения и мини-баннеры", "profile.blocks.code|CSS и сценарии блоков", "profile.blocks.counter|Счётчики нажатий", "profile.blocks.pixels|Пиксельные полотна",
            "music.player|Оформление плеера", "music.lyrics|Текст песни", "music.lyrics.animated|Анимированный фон текста песни", "music.activity|Музыкальная активность",
            "profile.integrations|Внешние профили", "workshop.publish|Публикация в мастерской", "workshop.install|Установка из мастерской", "workshop.social|Лайки и комментарии",
            "workshop.clans|Теги кланов", "profile.import|Импорт оформления", "profile.export|Экспорт оформления", "profile.export.layers|Экспорт слоёв", "profile.assistant|ИИ-помощник", "music.listening|Совместное прослушивание"
        };
        for (String entry : entries) { int i = entry.indexOf('|'); LABELS.put(entry.substring(0, i), entry.substring(i + 1)); }
    }
    private static final LinkedHashMap<String, String> NOTES = new LinkedHashMap<>();
    static {
        NOTES.put("profile.palette", "Цвета фона, текста и основных элементов профиля.");
        NOTES.put("profile.gradient", "Цвета, направление и форма статичных градиентов.");
        NOTES.put("profile.aurora", "Плавное движение цвета в фоне профиля.");
        NOTES.put("profile.mesh", "Анимированный многослойный фон с переходами цвета.");
        NOTES.put("profile.media", "Статичные изображения в баннере и фоне страницы.");
        NOTES.put("profile.media.animated", "GIF и видео в баннере и фоне страницы.");
        NOTES.put("profile.opacity", "Прозрачность, затемнение и затухание фона.");
        NOTES.put("profile.blur", "Размытие фона, карточек и кнопок.");
        NOTES.put("profile.cards", "Цвет, прозрачность, скругление и объединение карточек.");
        NOTES.put("profile.cards.glass", "Стеклянная поверхность, преломление и блики карточек.");
        NOTES.put("profile.cards.volume", "Объёмное освещение, направление и сила света.");
        NOTES.put("profile.buttons", "Цвет, прозрачность и форма кнопок действий.");
        NOTES.put("profile.layout", "Готовые варианты расположения шапки профиля.");
        NOTES.put("profile.layout.advanced", "Размер, положение, поворот и крепление элементов шапки.");
        NOTES.put("profile.rows", "Перестановка и скрытие строк в профиле.");
        NOTES.put("avatar.shape", "Форма, скругление и прозрачность аватара.");
        NOTES.put("avatar.frame", "Простые встроенные рамки аватара.");
        NOTES.put("avatar.frame.gradient", "Встроенная рамка с переходами цвета.");
        NOTES.put("avatar.frame.orbit", "Встроенная рамка с движением по орбите.");
        NOTES.put("avatar.frame.stars", "Встроенная рамка со звёздами.");
        NOTES.put("avatar.layers", "Многослойные рамки, наклейки, узоры и свечение.");
        NOTES.put("avatar.nodes", "Редактор узлов, источников и связей рамки.");
        NOTES.put("avatar.projects", "Сохранение и открытие проектов рамок.");
        NOTES.put("profile.pattern.dots", "Узор из точек поверх фона профиля.");
        NOTES.put("profile.pattern.stars", "Звёзды и частицы поверх фона.");
        NOTES.put("profile.pattern.waves", "Волновой узор поверх фона.");
        NOTES.put("profile.pattern.grid", "Сетка поверх фона профиля.");
        NOTES.put("profile.nick", "Свой цвет имени и переходы между цветами.");
        NOTES.put("profile.name.effects", "Анимации, свечение и тень имени.");
        NOTES.put("profile.thought", "Текст и оформление облачка возле аватара.");
        NOTES.put("badge.choice", "Выбор своего значка из доступного списка.");
        NOTES.put("profile.font", "Размер и шрифт имени, подпись в шапке.");
        NOTES.put("chat.bubble", "Цвет, прозрачность и градиент пузырьков сообщений.");
        NOTES.put("chat.font", "Шрифты текста сообщений.");
        NOTES.put("chat.badge", "Статичное изображение на карточке пользователя в списках.");
        NOTES.put("chat.badge.animated", "Анимированное изображение на карточке пользователя в списках.");
        NOTES.put("profile.blocks", "Свои текстовые строки, заголовки, ссылки и переключатели.");
        NOTES.put("profile.blocks.media", "Статичные картинки и мини-баннеры внутри профиля.");
        NOTES.put("profile.blocks.media.animated", "Анимированные картинки и мини-баннеры внутри профиля.");
        NOTES.put("profile.blocks.code", "Компоненты, CSS и сценарии оформления блоков.");
        NOTES.put("profile.blocks.counter", "Строки со счётчиком нажатий.");
        NOTES.put("profile.blocks.pixels", "Интерактивные пиксельные полотна.");
        NOTES.put("music.player", "Обложка и оформление музыкальной карточки.");
        NOTES.put("music.lyrics", "Параметры отображения текста песни.");
        NOTES.put("music.lyrics.animated", "Движущийся фон в оформлении текста песни.");
        NOTES.put("music.activity", "Передача текущего трека, обложки, времени и паузы.");
        NOTES.put("profile.integrations", "Строки музыкальных сервисов, Discord, Steam и GitHub.");
        NOTES.put("workshop.publish", "Отправка профилей и рамок на публикацию.");
        NOTES.put("workshop.install", "Установка работ и добавление в избранное.");
        NOTES.put("profile.notices", "Лента активности, колокольчик и всплывающие уведомления по категориям.");
        NOTES.put("profile.comments", "Обсуждения под профилем, ответы, лайки и модерация владельцем.");
        NOTES.put("workshop.social", "Лайки, комментарии и отправка жалоб.");
        NOTES.put("workshop.clans", "Создание тега сообщества и выбор тега для профиля.");
        NOTES.put("profile.communities", "Полный редактор оформления подтверждённых сообществ.");
        NOTES.put("profile.import", "Загрузка оформления из файла.");
        NOTES.put("profile.export", "Сохранение оформления в файл или изображение.");
        NOTES.put("profile.export.layers", "Экспорт отдельных слоёв оформления.");
        NOTES.put("profile.assistant", "Создание оформления через выбранный пользователем сервис ИИ.");
        NOTES.put("music.listening", "Включение совместного прослушивания и подключение к нему.");
    }

    private static final String[] CATEGORIES = {"Все разделы", "Фон и атмосфера", "Компоновка и карточки", "Аватар и рамки", "Имя и сообщения", "Блоки и интеграции", "Музыка и активность", "Мастерская и файлы", "Другие функции"};
    private static final String[] TIER_NOTES = {"Доступно каждому пользователю DevGram.", "Нужен значок с функционалом для поддержавших.", "Нужен значок с функционалом разработчика.", "Функция отключена у всех. Сохранённое оформление останется в данных."};
    private UniversalRecyclerView list;
    private EditText search;
    private View searchBar;
    private boolean saving, closed, refreshing;
    private String query = "", pending;
    private int tierFilter = -1, categoryFilter;
    private long viewer;
    private BottomSheet chooser;
    private final Runnable searchChanged = () -> update(false);
    private final Runnable policyChanged = () -> {
        if (closed) return;
        if (!active()) { dismissCurrentDialog(); DevGramAccessNavigation.close(this); return; }
        if (chooser != null) { chooser.dismiss(); chooser = null; }
        update(false);
    };
    public static String title(String feature) {
        String label = LABELS.get(feature);
        return label == null ? feature : DevGramLocale.text(label);
    }
    public static String[] tiers() { return new String[]{DevGramLocale.text("Всем пользователям"), DevGramLocale.text("Поддержавшим"), DevGramLocale.text("Разработчикам"), DevGramLocale.text("Никому")}; }
    private String t(String value) { return DevGramLocale.text(value); }
    private int dp(float n) { return AndroidUtilities.dp(n); }
    private int color(int key) { return Theme.getColor(key, resourceProvider); }
    private int accent() { return color(Theme.key_windowBackgroundWhiteBlueText); }
    private int audienceColor(int tier) {
        return tier == 0 ? 0xff23866c : tier == 1 ? 0xffa86e16 : tier == 2 ? 0xff8b62cc : color(Theme.key_text_RedRegular);
    }
    private boolean active() { return !closed && getParentActivity() != null && getUserConfig().getClientUserId() == viewer && DevGramProfileCustomization.enabled() && DevGramBadges.hasDeveloperFeatures(viewer); }
    private ArrayList<String> features() {
        LinkedHashSet<String> all = new LinkedHashSet<>(LABELS.keySet());
        all.addAll(DevGramProfilePolicy.DEFAULTS.keySet()); all.addAll(DevGramProfilePolicy.features());
        return new ArrayList<>(all);
    }
    private void update(boolean animated) { if (list != null && !closed) list.adapter.update(animated); }
    private TextView text(String value, int size, boolean bold) { return DevGramStudioControls.text(getParentActivity(), value, size, bold, resourceProvider); }
    private TextView muted(String value, int size) { TextView v = text(value, size, false); v.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText)); v.setLineSpacing(dp(2), 1); return v; }
    private LinearLayout column(int padding) { LinearLayout v = DevGramStudioControls.column(getParentActivity()); v.setPadding(dp(padding), dp(padding), dp(padding), dp(padding)); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(getParentActivity()); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private View wrap(View inner) { LinearLayout v = column(0); v.setPadding(dp(16), dp(5), dp(16), dp(5)); v.addView(inner, new LinearLayout.LayoutParams(-1, -2)); return v; }
    private void surface(View v, int fill, int stroke) { v.setBackground(DevGramStudioControls.surface(fill, stroke)); }
    private ImageView icon(int drawable, int tint) { ImageView v = new ImageView(getParentActivity()); v.setImageResource(drawable); v.setColorFilter(tint); v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return v; }
    private static int category(String key) {
        if (key.startsWith("avatar.")) return 3;
        if (key.startsWith("chat.") || key.equals("profile.nick") || key.equals("profile.name.effects") || key.equals("profile.thought") || key.equals("profile.font") || key.equals("badge.choice")) return 4;
        if (key.startsWith("profile.blocks") || key.equals("profile.integrations")) return 5;
        if (key.startsWith("music.")) return 6;
        if (key.startsWith("workshop.") || key.startsWith("profile.import") || key.startsWith("profile.export") || key.equals("profile.assistant")) return 7;
        if (key.startsWith("profile.layout") || key.equals("profile.rows") || key.startsWith("profile.cards") || key.equals("profile.buttons")) return 2;
        return LABELS.containsKey(key) ? 1 : 8;
    }
    private int categoryIcon(int group) { return group == 1 ? R.drawable.msg_colors : group == 3 || group == 4 ? R.drawable.msg_edit : group == 6 ? R.drawable.msg_filled_data_music : R.drawable.msg_settings; }

    @Override public View createView(Context context) {
        viewer = getUserConfig().getClientUserId();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back); actionBar.setTitle(t("Доступ к функциям"));
        actionBar.createMenu().addItem(1, R.drawable.msg_retry).setContentDescription(t("Обновить"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); else if (id == 1) refresh(); }
        });
        search = new EditText(context); search.setSingleLine(true); search.setTextSize(15);
        search.setHint(t("Найти функцию")); search.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText)); search.setHintTextColor(color(Theme.key_windowBackgroundWhiteGrayText));
        search.setBackground(null); search.setPadding(dp(10), 0, dp(4), 0);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v, action, event) -> { AndroidUtilities.hideKeyboard(search); return true; });
        LinearLayout searchRow = row(); searchRow.setPadding(dp(14), 0, dp(4), 0); surface(searchRow, color(Theme.key_windowBackgroundWhite), color(Theme.key_divider));
        searchRow.addView(icon(R.drawable.msg_search, color(Theme.key_windowBackgroundWhiteGrayText)), new LinearLayout.LayoutParams(dp(22), dp(22)));
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView clear = text("×", 24, false); clear.setGravity(Gravity.CENTER); clear.setContentDescription(t("Очистить поиск")); clear.setOnClickListener(v -> search.setText("")); searchRow.addView(clear, new LinearLayout.LayoutParams(dp(44), dp(48)));
        searchBar = wrap(searchRow);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString().trim().toLowerCase(Locale.ROOT); AndroidUtilities.cancelRunOnUIThread(searchChanged); AndroidUtilities.runOnUIThread(searchChanged, 160);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        list = new UniversalRecyclerView(this, this::fill, this::click, null);
        DevGramProfilePolicy.listen(policyChanged);
        fragmentView = new DevGramSettingsLayout(context, actionBar, list, resourceProvider);
        refresh(); return fragmentView;
    }
    private void refresh() {
        if (refreshing || !active()) return;
        refreshing = true;
        DevGramProfilePolicy.refresh((result, error) -> {
            refreshing = false; if (!active()) return; update(false);
            if (result == null) BulletinFactory.of(this).createErrorBulletin(t("Не удалось обновить доступ. Проверьте соединение и повторите.")).show();
        });
    }
    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!DevGramProfileCustomization.enabled()) { items.add(UItem.asShadow(t("Кастомизация профилей отключена"))); return; }
        if (!DevGramBadges.hasDeveloperFeatures(getUserConfig().getClientUserId())) { items.add(UItem.asShadow(t("Нужен функционал разработчика в значке."))); return; }
        ArrayList<String> keys = features(); int[] counts = new int[4]; for (String key : keys) counts[DevGramProfilePolicy.tier(key)]++;
        LinearLayout hero = column(18); surface(hero, Theme.multAlpha(accent(), .10f), 0);
        LinearLayout top = row(); TextView heading = text("Кому доступны функции", 22, true); top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1)); top.addView(icon(R.drawable.msg_settings, accent()), new LinearLayout.LayoutParams(dp(30), dp(30))); hero.addView(top);
        TextView summary = text("", 13, true); summary.setText(t("Всего функций") + ": " + keys.size() + "  ·  " + t("Включено") + ": " + (keys.size() - counts[3])); summary.setTextColor(accent()); hero.addView(summary, LayoutHelper.createLinear(-1, -2, 0, 10, 0, 6));
        hero.addView(muted("Обновляется в реальном времени", 12)); items.add(UItem.asCustom(wrap(hero), -2));
        TextSettingsCell limit = new TextSettingsCell(getParentActivity());
        limit.setTextAndValue(t("Участников для создания тега"), String.valueOf(DevGramProfilePolicy.clanMinMembers()), false);
        limit.setOnClickListener(v -> editClanMinimum()); items.add(UItem.asCustom(wrap(limit), -2));
        LinearLayout audiences = column(0); audiences.setPadding(dp(12), 0, dp(12), 0);
        for (int start = 0; start < 4; start += 2) {
            LinearLayout line = row();
            for (int j = start; j < start + 2; j++) {
                final int target = j; boolean selected = tierFilter == j; int tint = audienceColor(j);
                LinearLayout tile = column(13); surface(tile, selected ? Theme.multAlpha(tint, .14f) : color(Theme.key_windowBackgroundWhite), selected ? tint : color(Theme.key_divider));
                TextView amount = text("", 25, true); amount.setText(counts[j] + (selected ? "  ✓" : "")); amount.setTextColor(tint); tile.addView(amount);
                TextView label = text("", 13, true); label.setText(tiers()[j]); tile.addView(label, LayoutHelper.createLinear(-1, -2, 0, 4, 0, 0));
                tile.setSelected(selected); tile.setContentDescription(tiers()[j] + ": " + counts[j]); tile.setOnClickListener(v -> { tierFilter = tierFilter == target ? -1 : target; update(false); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1); lp.setMargins(dp(4), dp(4), dp(4), dp(4)); line.addView(tile, lp);
            }
            audiences.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
        items.add(UItem.asCustom(audiences, -2)); items.add(UItem.asCustom(searchBar, -2));
        LinearLayout tools = row(); tools.setPadding(dp(18), dp(8), dp(18), dp(4));
        TextView category = text("", 14, true); category.setText(t(CATEGORIES[categoryFilter]) + "  ▾"); category.setPadding(dp(13), dp(12), dp(13), dp(12)); category.setTextColor(accent()); surface(category, Theme.multAlpha(accent(), .09f), 0); category.setOnClickListener(v -> chooseCategory()); tools.addView(category, new LinearLayout.LayoutParams(0, -2, 1));
        TextView reset = text("Сбросить", 13, true); reset.setTextColor(accent()); reset.setPadding(dp(14), dp(12), 0, dp(12)); reset.setEnabled(tierFilter != -1 || categoryFilter != 0 || !query.isEmpty()); reset.setAlpha(reset.isEnabled() ? 1 : .4f); reset.setOnClickListener(v -> { tierFilter = -1; categoryFilter = 0; search.setText(""); update(false); }); tools.addView(reset); items.add(UItem.asCustom(tools, -2));
        int found = 0; ArrayList<UItem> results = new ArrayList<>();
        for (int group = 1; group < CATEGORIES.length; group++) {
            boolean header = false;
            for (String key : keys) {
                if (category(key) != group || categoryFilter != 0 && categoryFilter != group || tierFilter >= 0 && DevGramProfilePolicy.tier(key) != tierFilter) continue;
                String note = NOTES.get(key); String searchable = title(key) + " " + key + " " + (note == null ? "" : t(note));
                if (!query.isEmpty() && !searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
                if (!header) { results.add(UItem.asHeader(t(CATEGORIES[group]))); header = true; }
                results.add(UItem.asCustom(wrap(card(key)), -2)); found++;
            }
        }
        TextView foundText = muted("", 12); foundText.setText(t("Найдено функций") + ": " + found); foundText.setPadding(dp(20), dp(6), dp(20), dp(4)); items.add(UItem.asCustom(foundText, -2)); items.addAll(results);
        if (found == 0) items.add(UItem.asShadow(t("Ничего не найдено. Измените запрос или сбросьте фильтры.")));
        items.add(UItem.asShadow(t("Нажмите на функцию, чтобы выбрать аудиторию. Настройки оформления сохраняются при отключении доступа.")));
        items.add(UItem.asShadow(t("Изменения применяются сразу у всех пользователей в сети. Уже включённые функции отключатся, если доступ к ним закрыт.")));
    }
    private View card(String key) {
        LinearLayout card = column(15); surface(card, color(Theme.key_windowBackgroundWhite), color(Theme.key_divider));
        LinearLayout heading = row(); ImageView symbol = icon(categoryIcon(category(key)), accent()); symbol.setPadding(dp(8), dp(8), dp(8), dp(8)); surface(symbol, Theme.multAlpha(accent(), .09f), 0); heading.addView(symbol, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView name = text("", 16, true); name.setText(title(key)); heading.addView(name, LayoutHelper.createLinear(0, -2, 1f, 12, 0, 8, 0)); heading.addView(icon(R.drawable.msg_arrowright, color(Theme.key_windowBackgroundWhiteGrayText)), new LinearLayout.LayoutParams(dp(20), dp(20))); card.addView(heading);
        TextView detail = muted(NOTES.containsKey(key) ? NOTES.get(key) : "Новая функция сервера. Доступ настраивается так же, как для остальных функций.", 13); card.addView(detail, LayoutHelper.createLinear(-1, -2, 0, 10, 0, 10));
        int tier = DevGramProfilePolicy.tier(key), tint = audienceColor(tier); TextView status = text("", 12, true); status.setText(key.equals(pending) ? t("Сохраняю…") : tiers()[tier]); status.setTextColor(tint); status.setPadding(dp(10), dp(6), dp(10), dp(6)); surface(status, Theme.multAlpha(tint, .12f), 0); card.addView(status, new LinearLayout.LayoutParams(-2, -2));
        card.setContentDescription(title(key) + ". " + detail.getText() + ". " + status.getText()); card.setEnabled(!saving); card.setAlpha(key.equals(pending) ? .6f : 1); card.setOnClickListener(v -> chooseAudience(key)); return card;
    }
    private void showSheet(LinearLayout body) {
        ScrollView scroll = new ScrollView(getParentActivity()) { @Override protected void onMeasure(int w, int h) { super.onMeasure(w, DevGramStudioControls.sheetHeightSpec(h, .82f)); } };
        scroll.setFillViewport(false); scroll.addView(body); chooser = new BottomSheet.Builder(getParentActivity(), false, resourceProvider).setCustomView(scroll).create(); showDialog(chooser);
    }
    private void chooseCategory() {
        if (!active()) return; AndroidUtilities.hideKeyboard(search); LinearLayout body = column(20); body.addView(text("Выберите раздел", 21, true));
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int chosen = i; int count = 0; for (String key : features()) if (i == 0 || category(key) == i) count++;
            TextView option = text("", 16, i == categoryFilter); option.setText(t(CATEGORIES[i]) + "  ·  " + count + (i == categoryFilter ? "  ✓" : "")); option.setPadding(dp(12), dp(16), dp(12), dp(16)); option.setTextColor(i == categoryFilter ? accent() : color(Theme.key_windowBackgroundWhiteBlackText)); option.setOnClickListener(v -> { if (chooser != null) chooser.dismiss(); chooser = null; categoryFilter = chosen; update(false); }); body.addView(option, new LinearLayout.LayoutParams(-1, -2));
        }
        showSheet(body);
    }
    private void chooseAudience(String key) {
        if (!active() || saving) return; AndroidUtilities.hideKeyboard(search); LinearLayout body = column(20);
        TextView name = text("", 22, true); name.setText(title(key)); body.addView(name);
        body.addView(muted(NOTES.containsKey(key) ? NOTES.get(key) : "Новая функция сервера. Доступ настраивается так же, как для остальных функций.", 14), LayoutHelper.createLinear(-1, -2, 0, 8, 0, 18));
        for (int i = 0; i < 4; i++) {
            final int chosen = i; boolean selected = DevGramProfilePolicy.tier(key) == i; int tint = audienceColor(i);
            LinearLayout option = column(15); surface(option, selected ? Theme.multAlpha(tint, .12f) : color(Theme.key_windowBackgroundGray), selected ? tint : color(Theme.key_divider));
            LinearLayout line = row(); TextView label = text("", 16, true); label.setText(tiers()[i]); label.setTextColor(tint); line.addView(label, new LinearLayout.LayoutParams(0, -2, 1)); TextView check = text(selected ? "●" : "○", 23, false); check.setTextColor(tint); line.addView(check); option.addView(line);
            option.addView(muted(TIER_NOTES[i], 13), LayoutHelper.createLinear(-1, -2, 0, 5, 16, 0)); option.setSelected(selected); option.setContentDescription(tiers()[i] + ". " + t(TIER_NOTES[i]));
            option.setOnClickListener(v -> { if (chooser != null) chooser.dismiss(); chooser = null; change(key, chosen); }); body.addView(option, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 9));
        }
        body.addView(muted("Обновляется в реальном времени", 12), LayoutHelper.createLinear(-1, -2, 0, 5, 0, 8)); showSheet(body);
    }
    private void change(String key, int tier) {
        if (!active() || saving || tier == DevGramProfilePolicy.tier(key)) return;
        saving = true; pending = key; update(false);
        DevGramProfilePolicy.setTier(currentAccount, key, tier, (result, error) -> {
            saving = false; pending = null; if (!active()) return; update(false);
            if (result == null) {
                if ("revision".equals(error) || "conflict".equals(error)) refresh();
                BulletinFactory.of(this).createErrorBulletin(t("Не удалось изменить доступ. Обновите экран и попробуйте снова.")).show();
            } else BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, t("Доступ обновлён у пользователей в сети")).show();
        });
    }
    private void click(UItem item, View view, int position, float x, float y) {}
    private void editClanMinimum() {
        if (!active() || saving) return;
        org.telegram.messenger.forkgram.ForkDialogs.createFieldAlert(getParentActivity(), t("Участников для создания тега"), String.valueOf(DevGramProfilePolicy.clanMinMembers()), value -> {
            int number;
            try { number = Integer.parseInt(value.trim()); if (number < 0 || number > 10000000) throw new NumberFormatException(); }
            catch (NumberFormatException e) { BulletinFactory.of(this).createErrorBulletin(t("Введите число от 0 до 10000000")).show(); return null; }
            saving = true;
            DevGramProfilePolicy.setClanMinMembers(currentAccount, number, (result, error) -> {
                saving = false; if (!active()) return;
                if (result == null) { refresh(); BulletinFactory.of(this).createErrorBulletin(t("Не удалось изменить доступ. Обновите экран и попробуйте снова.")).show(); }
                update(false);
            });
            return null;
        });
    }
    @Override public void onResume() { super.onResume(); if (list != null) policyChanged.run(); }
    @Override public void onFragmentDestroy() { closed = true; AndroidUtilities.cancelRunOnUIThread(searchChanged); DevGramProfilePolicy.unlisten(policyChanged); if (chooser != null) chooser.dismiss(); chooser = null; super.onFragmentDestroy(); }
}

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.view.Gravity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Native editors for the public profile document. All child screens share one explicit draft. */
public class DevGramStudioActivity extends DevGramSettingsFragment {
    public static class Draft {
        public long subject;
        public String scope="profile";
        public long target(int account){return subject==0?UserConfig.getInstance(account).getClientUserId():subject;}
        public JSONObject document = new JSONObject();
        public DevGramProfileStyle.Style theme;
        public boolean dirty;
        public Runnable onSaved;
        public Runnable onChanged;
        public java.util.function.Consumer<Boolean> onExport;
        public Boolean pendingExport;
    }
    private final String page;
    private final Draft draft;
    private UniversalRecyclerView list;
    private DevGramStudioPreview preview;
    private DevGramProfileBlocksView blocksPreview;
    private DevGramAppearancePreview messagePreview;
    private DevGramNodeCanvas nodeCanvas;
    private boolean saving, closed;
    private long owner;
    private final DevGramProfileExitPrompt exitPrompt = new DevGramProfileExitPrompt();
    private boolean fontUploading;
    private String selectedFontField;
    private int fontRequest;
    private byte[] exportData;
    private int selectedElement;
    private final java.util.HashMap<String, DevGramStudioControls.Slider> sliders = new java.util.HashMap<>();
    private final ArrayList<DevGramStudioPreview> sheetPreviews = new ArrayList<>();
    private final Runnable live = this::updateLive;
    private void updateLive() {
        if (!DevGramProfileCustomization.enabled()) {
            dismissCurrentDialog();
            if (preview != null) preview.release();
            if (blocksPreview != null) blocksPreview.release();
            for (DevGramStudioPreview view : sheetPreviews) view.release();
            if (fragmentView != null && !isFinishing()) DevGramAccessNavigation.close(this);
            return;
        }
        if (nodeCanvas != null) nodeCanvas.refreshAccess();
        if (list != null) list.adapter.update(true);
        if (preview != null) preview.set(draft.document, draft.theme);
        if (messagePreview != null) messagePreview.setDocument(draft.document);
        if (blocksPreview != null) blocksPreview.set(getUserConfig().getClientUserId(), DevGramProfileDocument.effective(draft.document, getUserConfig().getClientUserId()));
        for (DevGramStudioPreview view : sheetPreviews) view.set(draft.document, draft.theme);
    }
    private static final int IMPORT = 7380, EXPORT = 7381, IMPORT_FONT = 7382;
    public DevGramStudioActivity() { this("home", new Draft()); }
    public DevGramStudioActivity(DevGramProfileStyle.Style theme, Runnable onSaved) {
        this("home", new Draft()); draft.theme = theme; draft.onSaved = onSaved;
    }
    private DevGramStudioActivity(String page, Draft draft) { this.page = page; this.draft = draft; }
    public static org.telegram.ui.ActionBar.BaseFragment forDraft(String page, Draft draft) { return page.equals("nodes")?new DevGramFrameGraphActivity(draft):new DevGramStudioActivity(page, draft); }
    public static String t(String text) { return DevGramLocale.text(text); }
    @Override public boolean onFragmentCreate() { return DevGramProfileCustomization.enabled() && super.onFragmentCreate(); }

    @Override public View createView(Context context) {
        owner = getUserConfig().getClientUserId();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(title());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1 && onBackPressed(true)) finishFragment(); }
        });
        if (page.equals("home")) {
            draft.document = DevGramProfileDocument.raw(draft.target(currentAccount));
            DevGramProfileDocument.load(currentAccount, draft.subject, (result, error) -> {
                if (!draft.dirty && result != null) draft.document = DevGramProfileDocument.editable(result.optJSONObject("document"));
                live.run();
            });
        }
        preview = new DevGramStudioPreview(context, currentAccount, draft.target(currentAccount));
        preview.set(draft.document, draft.theme);
        if (page.equals("messages")) { messagePreview = new DevGramAppearancePreview(context, currentAccount, resourceProvider); messagePreview.setDocument(draft.document); }
        if (page.equals("layout")) preview.setEditor(new DevGramStudioPreview.Editor() {
            @Override public boolean select(int element) {
                if (!access("profile.layout.advanced")) return false;
                selectedElement = element; live.run(); return true;
            }
            @Override public void move(int element, int x, int y, boolean stopped) {
                String prefix = DevGramHeaderGeometry.PARTS[element];
                putPreview(prefix + "OffsetX", x); putPreview(prefix + "OffsetY", y);
                if (stopped) live.run();
            }
        });
        if (page.equals("blocks")) {
            blocksPreview = new DevGramProfileBlocksView(context,currentAccount,resourceProvider);
            blocksPreview.set(getUserConfig().getClientUserId(), DevGramProfileDocument.effective(draft.document, getUserConfig().getClientUserId()));
        }
        list = new UniversalRecyclerView(this, this::fill, this::click, null);
        DevGramProfilePolicy.listen(live);
        return fragmentView = new DevGramSettingsLayout(context, actionBar, list, resourceProvider);
    }

    private String title() {
        switch (page) {
            case "layout": return t("Компоновка и карточки"); case "layers": return t("Студия рамок");
            case "nodes": return t("Студия узлов"); case "blocks": return t("Блоки профиля");
            case "music": return t("Музыка и активность"); case "integrations": return t("Внешние профили");
            case "messages": return t("Имя и сообщения"); case "files": return t("Проекты и файлы");
            case "fonts": return t("Свои шрифты");
            case "storage": return t("Данные и память"); default: return t("Студия профиля");
        }
    }

    private void button(ArrayList<UItem> items, int id, String title, String feature) {
        items.add(UItem.asButton(id, t(title), DevGramAppearanceAccess.label(this, feature,
                feature == null || DevGramAppearanceAccess.allowed(this, feature) ? "" : DevGramProfilePolicyActivity.tiers()[DevGramProfilePolicy.tier(feature)])));
    }
    private void number(ArrayList<UItem> items, int id, String key, String title, int fallback) {
        if (key.endsWith("Color")) {
            items.add(UItem.asHeader(t(title)));
            items.add(UItem.asCustom(DevGramStudioControls.swatches(getParentActivity(), draft.document.optInt(key, -1), resourceProvider,
                    selected -> { if (field(key)) put(key, selected); }), 56));
            items.add(UItem.asButton(id, R.drawable.msg_colors, t("Другой цвет"), DevGramAppearanceAccess.label(this, DevGramProfileDocument.FEATURES.get(key), draft.document.has(key) ? String.format(java.util.Locale.US, "#%06X", draft.document.optInt(key)) : t("Как в Telegram"))));
            return;
        }
        int low = 0, high = 100; String unit = "%";
        if (key.endsWith("ScaleX") || key.endsWith("ScaleY")) { low = 10; high = 300; }
        else if (key.endsWith("OffsetX") || key.endsWith("OffsetY")) { low = -45; high = 45; }
        else if (key.endsWith("Rotation")) { low = -180; high = 180; unit = "°"; }
        else if (key.endsWith("Scale")) { low = key.startsWith("avatar") || key.startsWith("buttons") ? 75 : 80; high = low == 75 ? 125 : 130; }
        else if (key.endsWith("X") || key.endsWith("Y")) { low = -40; high = 40; unit = ""; }
        else if (key.equals("cardAlpha")) low = 0;
        else if (key.equals("blur")) { high = 20; unit = ""; }
        DevGramStudioControls.Slider slider = sliders.get(key);
        if (slider == null) { slider = new DevGramStudioControls.Slider(getParentActivity(), resourceProvider); sliders.put(key, slider); }
        String feature = DevGramProfileDocument.FEATURES.get(key);
        slider.bind(DevGramAppearanceAccess.label(this, feature, t(title)), draft.document.optInt(key, fallback), low, high, unit,
                DevGramAppearanceAccess.allowed(this, feature), n -> putPreview(key, n), live, () -> field(key));
        items.add(UItem.asCustom(slider, 82));
    }

    private void visualChoices(ArrayList<UItem> items, String key, String title, String[] labels) {
        items.add(UItem.asHeader(t(title)));
        CharSequence[] accessible = new CharSequence[labels.length];
        for (int i = 0; i < labels.length; i++) accessible[i] = DevGramAppearanceAccess.label(this,
                key.equals("layout") && i == 3 ? "profile.layout.advanced" : DevGramProfileDocument.FEATURES.get(key), t(labels[i]));
        int columns = key.equals("layout") || key.equals("buttonShape") ? 2 : 3;
        items.add(UItem.asCustom(DevGramStudioControls.choices(getParentActivity(), key.equals("chatFont") ? "font" : key, labels, accessible,
                draft.document.optInt(key), draft.theme, resourceProvider, i -> {
                    if (access(key.equals("layout") && i == 3 ? "profile.layout.advanced" : DevGramProfileDocument.FEATURES.get(key))) {
                        if(key.equals("font")||key.equals("nameFont"))draft.document.remove("nameFontFile");
                        if(key.equals("chatFont"))draft.document.remove("chatFontFile");
                        put(key, i);
                    }
                }), (labels.length + columns - 1) / columns * 124 + 8));
    }
    private void headerAnchor(ArrayList<UItem> items,String part,String axis,String title) {
        items.add(UItem.asHeader(t(title)));
        String[] labels={"Как в Telegram","Аватар","Имя","Статус","Кнопки","Подпись над аватаром"};
        items.add(UItem.asCustom(DevGramStudioControls.pills(getParentActivity(),labels,draft.document.optInt(part+"Anchor"+axis),resourceProvider,index->{
            if(!access("profile.layout.advanced"))return;
            int[] targets=new int[5];int selected=0;
            for(int i=0;i<5;i++){String key=DevGramHeaderGeometry.PARTS[i];targets[i]=draft.document.optInt(key+"Anchor"+axis);if(key.equals(part))selected=i;}
            targets[selected]=index;
            if(DevGramHeaderGeometry.cyclic(selected,targets)){error(t("Эта привязка создаёт замкнутый круг"));return;}
            put(part+"Anchor"+axis,index);
        }),52));
        String[] edges=axis.equals("X")?new String[]{"Слева","По центру","Справа"}:new String[]{"Сверху","По центру","Снизу"};
        items.add(UItem.asCustom(DevGramStudioControls.pills(getParentActivity(),edges,draft.document.optInt(part+"Align"+axis),resourceProvider,index->{if(access("profile.layout.advanced"))put(part+"Align"+axis,index);}),52));
    }
    private void check(ArrayList<UItem> items, int id, String key, String title) {
        items.add(UItem.asCheck(id, DevGramAppearanceAccess.label(this, DevGramProfileDocument.FEATURES.get(key), t(title))).setChecked(DevGramAppearanceAccess.allowed(this, DevGramProfileDocument.FEATURES.get(key)) && draft.document.optInt(key, key.equals("lyrics") || key.equals("lyricsMotion") ? 1 : 0) == 1));
    }

    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (messagePreview != null) items.add(UItem.asCustom(messagePreview, 176));
        else items.add(UItem.asCustom(preview, 280));
        items.add(UItem.asShadow(t(page.equals("layout") ? "Перетаскивайте аватар, имя, статус и кнопки прямо в предпросмотре. Размер меняется ползунком." : "Изменения видны в предпросмотре. Сохраните их, чтобы обновить свой профиль у других пользователей DevGram.")));
        switch (page) {
            case "home":
                items.add(UItem.asCustom(DevGramStudioControls.grid(getParentActivity(),
                        new String[]{"Компоновка и карточки", "Студия рамок", "Блоки профиля", "Имя и сообщения", "Музыка и активность", "Внешние профили", "Мастерская оформлений", "Проекты и файлы", "Данные и диагностика"},
                        new String[]{"Расположение, размеры и цвета", "Слои, эффекты и анимации", "Текст, изображения и ссылки", "Шрифты и пузырьки", "Плеер и текущий трек", "Discord, Steam, GitHub", "Готовые работы сообщества", "Сохранение, импорт и экспорт", "Кэш и подключение"},
                        new int[]{R.drawable.msg_customize, R.drawable.msg_photo_settings, R.drawable.msg_add, R.drawable.msg_discussion, R.drawable.msg_gallery, R.drawable.msg_link, R.drawable.msg_photos, R.drawable.msg_settings, R.drawable.msg_secret},
                        resourceProvider, i -> { String[] pages = {"layout","layers","blocks","messages","music","integrations","","files","storage"}; if (i == 6) presentFragment(new DevGramWorkshopActivity(draft)); else child(pages[i]); }), 762)); break;
            case "layout":
                visualChoices(items, "layout", "Компоновка профиля", new String[]{"Как в Telegram", "По центру", "Компактная", "Своя компоновка"});
                items.add(UItem.asCustom(DevGramStudioControls.pills(getParentActivity(), new String[]{"Аватар", "Имя", "Статус", "Кнопки", "Подпись над аватаром"}, selectedElement, resourceProvider, i -> { selectedElement = i; preview.selectElement(i); live.run(); }), 52));
                String prefix = DevGramHeaderGeometry.PARTS[selectedElement];
                items.add(UItem.asCustom(new DevGramPositionPad(getParentActivity(), draft.document.optInt(prefix+"OffsetX"),draft.document.optInt(prefix+"OffsetY"),
                        DevGramAppearanceAccess.allowed(this,"profile.layout.advanced"),resourceProvider,(x,y)->{putPreview(prefix+"OffsetX",x);putPreview(prefix+"OffsetY",y);},()->access("profile.layout.advanced")),220));
                number(items, 9101, prefix + "OffsetX", "По горизонтали", 0);
                number(items, 9102, prefix + "OffsetY", "По вертикали", 0);
                number(items, 9103, prefix + "ScaleX", "Ширина", 100);
                number(items, 9104, prefix + "ScaleY", "Высота", 100);
                number(items, 9105, prefix + "Rotation", "Поворот", 0);
                headerAnchor(items,prefix,"X","По горизонтали");headerAnchor(items,prefix,"Y","По вертикали");
                if(selectedElement==4)button(items,9114,"Подпись над аватаром","profile.font");
                number(items, 108, "cardColor", "Цвет карточек", 0);
                number(items, 109, "cardAlpha", "Непрозрачность карточек, %", 100);
                visualChoices(items, "buttonShape", "Форма кнопок", new String[]{"Как в Telegram", "Капсулы", "Скруглённые", "Круглые"});
                number(items, 111, "blur", "Размытие фона", 0);
                button(items, 112, "Порядок строк", "profile.rows"); button(items, 113, "Видимость строк", "profile.rows"); break;
            case "layers":
                JSONArray layers = array("layers");
                for (int i = 0; i < layers.length(); i++) button(items, 1000 + i, (i + 1) + ". " + layerTitle(layers.optJSONObject(i).optString("type")), "avatar.layers");
                button(items, 201, "Добавить слой", "avatar.layers"); button(items, 202, "Студия узлов", "avatar.nodes");
                button(items, 203, "Сохранить проект", "avatar.projects"); button(items, 204, "Мои проекты", "avatar.projects"); break;
            case "nodes":
                if(nodeCanvas==null)nodeCanvas=new DevGramNodeCanvas(getParentActivity(),this,()->draft.document,nodes->put("nodes",nodes),this::editNode,()->access("avatar.nodes"));
                items.add(UItem.asCustom(nodeCanvas,400));
                LinearLayout canvasActions=new LinearLayout(getParentActivity());
                String[] canvasLabels={"Отменить действие","Вернуть действие","Показать всю схему"};
                for(int i=0;i<canvasLabels.length;i++){int op=i;TextView control=DevGramStudioControls.text(getParentActivity(),canvasLabels[i],12,true,getResourceProvider());control.setGravity(Gravity.CENTER);control.setPadding(AndroidUtilities.dp(4),AndroidUtilities.dp(12),AndroidUtilities.dp(4),AndroidUtilities.dp(12));control.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,getResourceProvider()));control.setOnClickListener(v->{if(op==2)nodeCanvas.fit();else nodeCanvas.undo(op==1);});canvasActions.addView(control,new LinearLayout.LayoutParams(0,-2,1));}
                items.add(UItem.asCustom(canvasActions));
                items.add(UItem.asShadow(t("Перемещайте полотно одним пальцем, меняйте масштаб двумя. Перетаскивайте узлы за карточку, соединяйте правый выход с левым входом. Удерживайте узел для дополнительных действий.")));
                JSONArray nodes = array("nodes");
                for (int i = 0; i < nodes.length(); i++) {
                    JSONObject node = nodes.optJSONObject(i);
                    button(items, 2000 + i, "#" + node.optInt("id") + " · " + nodeTitle(node.optString("type")), "avatar.nodes");
                }
                button(items, 301, "Добавить узел", "avatar.nodes"); break;
            case "blocks":
                if (blocksPreview != null) items.add(UItem.asCustom(blocksPreview));
                JSONArray blocks = array("blocks");
                for (int i = 0; i < blocks.length(); i++) {
                    JSONObject block = blocks.optJSONObject(i);
                    String feature=block.has("rowType")?DevGramProfileRowSpec.feature(block):block.optString("type").equals("image")?(block.optInt("mediaType")==1?"profile.blocks.media.animated":"profile.blocks.media"):block.optString("type").equals("code")?"profile.blocks.code":"profile.blocks";
                    items.add(UItem.asButton(3000 + i, DevGramAppearanceAccess.label(this, feature, block.optString("title").isEmpty() ? blockTitle(block.optString("type")) : block.optString("title"))));
                }
                button(items, 401, "Добавить блок", "profile.blocks"); break;
            case "messages":
                button(items, 509, "Свои шрифты", null);
                visualChoices(items, "font", "Шрифт имени", new String[]{"Как в Telegram", "С засечками", "Моноширинный", "Узкий", "Рукописный"});
                visualChoices(items, "chatFont", "Шрифт сообщений", new String[]{"Как в Telegram", "С засечками", "Моноширинный", "Узкий", "Рукописный"});
                button(items, 503, "Цвет ника", "profile.nick"); button(items, 504, "Обводка сообщений", "chat.bubble");
                number(items, 505, "bubbleColor", "Цвет пузырька", 0); number(items, 506, "textColor", "Цвет текста сообщений", 0);
                items.add(UItem.asButton(507, t("Подпись на профиле"), DevGramAppearanceAccess.label(this, "chat.badge", draft.document.optString("badge"))));
                items.add(UItem.asShadow(t("Короткая подпись рядом с именем в оформлении профиля. Например: разработчик, художник или название команды.")));
                items.add(UItem.asButton(508, t("Тег клана"), DevGramAppearanceAccess.label(this, "workshop.clans", draft.document.optString("clan")))); break;
            case "fonts":
                fontSection(items, "nameFontFile", "Шрифт имени", 521, 522);
                fontSection(items, "chatFontFile", "Шрифт сообщений", 523, 524);
                items.add(UItem.asShadow(t("Загрузите TTF или OTF до 5 МБ. Шрифт увидят пользователи DevGram после сохранения оформления. Он также переносится с работой из мастерской.")));
                break;
            case "music":
                check(items, 601, "playerCover", "Обложка в оформлении плеера"); check(items, 602, "lyrics", "Текст песни");
                check(items, 603, "lyricsMotion", "Анимированный фон текста песни"); check(items, 604, "activity", "Показывать музыкальную активность");
                button(items, 605, "Подключить плееры Android", "music.activity");
                items.add(UItem.asShadow(t("Музыкальная активность публикует название и исполнителя текущего трека в вашем профиле. Для других плееров разрешите доступ к уведомлениям. Отключение настройки прекращает публикацию."))); break;
            case "integrations":
                JSONArray integrations = array("integrations");
                for (int i = 0; i < integrations.length(); i++) button(items, 4000 + i, integrations.optJSONObject(i).optString("provider") + " · " + integrations.optJSONObject(i).optString("handle"), "profile.integrations");
                JSONArray serviceRows = array("blocks");
                for (int i = 0; i < serviceRows.length(); i++) { JSONObject row=serviceRows.optJSONObject(i); if(row!=null&&DevGramProfileRowSpec.type(row)==12) { int service=Math.max(0,Math.min(8,row.optInt("service"))); button(items,3000+i,t(DevGramProfileRowSpec.SERVICES[service])+(row.optString("url").isEmpty()?"":" · "+row.optString("url")),"profile.integrations"); } }
                button(items, 701, "Подключить внешний профиль", "profile.integrations");
                items.add(UItem.asShadow(t("Войдите в сервис через браузер. После подтверждения учётная запись появится здесь автоматически. Сохраните оформление, чтобы показать выбранные данные в профиле."))); break;
            case "files":
                button(items, 203, "Сохранить проект", "avatar.projects"); button(items, 204, "Мои проекты", "avatar.projects");
                button(items, 801, "Импортировать оформление", "profile.import"); button(items, 802, "Экспортировать оформление", "profile.export");
                button(items, 803, "Сохранить изображение профиля", "profile.export"); button(items, 804, "Экспортировать слои", "profile.export.layers");
                button(items, 805, "Опубликовать в мастерской", "workshop.publish"); break;
            case "storage":
                items.add(UItem.asShadow(t("Кэш оформлений") + ": " + AndroidUtilities.formatFileSize(DevGramProfileDocument.cacheBytes())));
                items.add(UItem.asButton(904, t("Лимит кэша оформлений"), MessagesController.getGlobalMainSettings().getInt("devgramStudioCacheMb", 8) + " MB"));
                int cacheDays = MessagesController.getGlobalMainSettings().getInt("devgramStudioCacheDays", 7);
                items.add(UItem.asButton(905, t("Автоочистка кэша"), t(cacheDays == 0 ? "Никогда" : cacheDays == 1 ? "1 день" : cacheDays == 30 ? "30 дней" : "7 дней")));
                button(items, 901, "Очистить кэш оформлений", null); button(items, 902, "Проверить связь с сервером", null);
                button(items, 903, "Диагностика оформления", null);
                items.add(UItem.asShadow(t("Очистка затрагивает только загруженные оформления. Ваши аккаунты, сообщения, плагины и сохранённые проекты останутся на месте."))); break;
        }
        items.add(UItem.asShadow(null));
        button(items, 999, saving ? "Сохраняю…" : "Сохранить оформление", null);
    }

    private JSONArray array(String key) { JSONArray a = draft.document.optJSONArray(key); return a == null ? new JSONArray() : a; }
    private void fontSection(ArrayList<UItem> items,String field,String title,int upload,int reset) {
        items.add(UItem.asHeader(t(title)));
        JSONObject metadata=draft.document.optJSONObject(field);
        TextView sample=DevGramStudioControls.text(getParentActivity(),"DevGram · Абвгд AaBb 123",23,false,resourceProvider);
        JSONObject effective=DevGramProfileDocument.effective(draft.document,getUserConfig().getClientUserId());
        sample.setTypeface(field.equals("nameFontFile")?DevGramProfileDocument.nameFont(effective):DevGramProfileDocument.messageFont(effective));
        sample.setGravity(Gravity.CENTER);sample.setPadding(AndroidUtilities.dp(16),AndroidUtilities.dp(12),AndroidUtilities.dp(16),AndroidUtilities.dp(12));
        items.add(UItem.asCustom(sample,64));
        button(items,upload,fontUploading?"Загружаю шрифт…":"Выбрать файл шрифта",DevGramProfileDocument.FEATURES.get(field));
        if(metadata!=null){items.add(UItem.asShadow(metadata.optString("name")));button(items,reset,"Убрать свой шрифт",DevGramProfileDocument.FEATURES.get(field));}
    }
    private void putPreview(String key, Object value) { try { draft.document.put(key, value); draft.dirty = true; if (preview != null) preview.set(draft.document, draft.theme); if (messagePreview != null) messagePreview.setDocument(draft.document); for (DevGramStudioPreview view : sheetPreviews) view.set(draft.document, draft.theme); if (blocksPreview != null) blocksPreview.set(getUserConfig().getClientUserId(), DevGramProfileDocument.effective(draft.document, getUserConfig().getClientUserId())); if (draft.onChanged != null) draft.onChanged.run(); } catch (Exception ignored) {} }
    private void put(String key, Object value) { putPreview(key, value); live.run(); }
    private void child(String name) { presentFragment(forDraft(name,draft)); }
    private boolean access(String key) { return DevGramAppearanceAccess.check(this, key); }
    private boolean field(String key) { return access(DevGramProfileDocument.FEATURES.get(key)); }

    private void click(UItem item, View view, int position, float x, float y) {
        if (saving) return;
        int id = item.id;
        if (id >= 4000 && id < 4007) { editIntegration(id - 4000); return; }
        if (id >= 3000 && id < 3064) { editBlock(id - 3000); return; }
        if (id >= 2000 && id < 2512) { editNode(id - 2000); return; }
        if (id >= 1000 && id < 1012) { editLayer(id - 1000); return; }
        switch (id) {
            case 1: child("layout"); break; case 2: child("layers"); break;
            case 3: child("blocks"); break; case 4: child("messages"); break;
            case 5: child("music"); break; case 6: child("integrations"); break;
            case 7: presentFragment(new DevGramWorkshopActivity(draft)); break;
            case 8: child("files"); break; case 9: child("storage"); break;
            case 101: choose("Компоновка профиля", new String[]{"Как в Telegram", "По центру", "Компактная", "Своя компоновка"}, i -> { if (access(i == 3 ? "profile.layout.advanced" : "profile.layout")) put("layout", i); }); break;
            case 102: editNumber("avatarScale", "Размер аватара, %", 75, 125, 100); break;
            case 103: editNumber("avatarX", "Аватар: смещение по горизонтали", -40, 40, 0); break;
            case 104: editNumber("avatarY", "Аватар: смещение по вертикали", -40, 40, 0); break;
            case 105: editNumber("nameScale", "Размер имени, %", 80, 130, 100); break;
            case 106: editNumber("nameX", "Имя: смещение по горизонтали", -40, 40, 0); break;
            case 107: editNumber("nameY", "Имя: смещение по вертикали", -40, 40, 0); break;
            case 108: color("cardColor", "Цвет карточек"); break; case 109: editNumber("cardAlpha", "Непрозрачность карточек, %", 0, 100, 100); break;
            case 110: if (field("buttonShape")) choose("Форма кнопок", new String[]{"Как в Telegram", "Капсулы", "Скруглённые", "Круглые"}, i -> put("buttonShape", i)); break;
            case 111: editNumber("blur", "Размытие фона", 0, 20, 0); break;
            case 112: rows(false); break; case 113: rows(true); break;
            case 114: editNumber("statusScale", "Размер статуса, %", 80, 130, 100); break;
            case 115: editNumber("statusX", "Статус: смещение по горизонтали", -40, 40, 0); break;
            case 116: editNumber("statusY", "Статус: смещение по вертикали", -40, 40, 0); break;
            case 117: editNumber("buttonsScale", "Размер кнопок, %", 75, 125, 100); break;
            case 118: editNumber("buttonsX", "Кнопки: смещение по горизонтали", -40, 40, 0); break;
            case 119: editNumber("buttonsY", "Кнопки: смещение по вертикали", -40, 40, 0); break;
            case 201: addLayer(); break; case 202: if (access("avatar.nodes")) child("nodes"); break;
            case 203: saveProject(); break; case 204: projects(); break; case 301: addNode(); break; case 401: addBlock(); break;
            case 501: font("font"); break; case 502: font("chatFont"); break;
            case 509: child("fonts"); break;
            case 521: pickFont("nameFontFile"); break; case 523: pickFont("chatFontFile"); break;
            case 522: if(field("nameFontFile"))put("nameFontFile",null); break;
            case 524: if(field("chatFontFile"))put("chatFontFile",null); break;
            case 503: if (access("profile.nick")) presentFragment(new DevGramNickColorActivity(draft)); break;
            case 504: if (access("chat.bubble")) presentFragment(new DevGramFrameChoiceActivity(draft)); break;
            case 505: color("bubbleColor", "Цвет пузырька"); break; case 506: color("textColor", "Цвет текста сообщений"); break;
            case 507: if (field("badge")) input("Подпись на профиле", draft.document.optString("badge"), 24, false, s -> put("badge", s)); break;
            case 508: if (DevGramAppearanceAccess.check(this,"workshop.clans")) presentFragment(new DevGramClanTagsActivity()); break;
            case 601: toggle("playerCover"); break; case 602: toggle("lyrics"); break; case 603: toggle("lyricsMotion"); break; case 604: toggle("activity"); break;
            case 605: if (access("music.activity")) getParentActivity().startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); break;
            case 701: addIntegration(); break; case 801: importFile(); break; case 802: exportTemplate(); break;
            case 803: exportImage(false); break; case 804: exportImage(true); break; case 805: publish(); break;
            case 901: confirm("Очистить кэш оформлений?", () -> { DevGramProfileDocument.clearCache(); DevGramProfileStyle.clearCache(); success("Кэш оформлений очищен"); }); break;
            case 902: ping(); break;
            case 903: info("Диагностика оформления", "DevGram " + BuildVars.BUILD_VERSION_STRING + "\n" + t("Ревизия доступов") + ": " + DevGramProfilePolicy.revision() + "\n" + t("Размер оформления") + ": " + draft.document.toString().getBytes(StandardCharsets.UTF_8).length + " B"); break;
            case 904: choose("Лимит кэша оформлений", new String[]{"4 MB", "8 MB", "16 MB", "32 MB"}, i -> { MessagesController.getGlobalMainSettings().edit().putInt("devgramStudioCacheMb", new int[]{4,8,16,32}[i]).apply(); org.telegram.messenger.DevGramNetworkQueue.postRunnable(DevGramProfileDocument::trimCache); live.run(); }); break;
            case 905: choose("Автоочистка кэша", new String[]{"1 день", "7 дней", "30 дней", "Никогда"}, i -> { MessagesController.getGlobalMainSettings().edit().putInt("devgramStudioCacheDays", new int[]{1,7,30,0}[i]).apply(); org.telegram.messenger.DevGramNetworkQueue.postRunnable(DevGramProfileDocument::trimCache); live.run(); }); break;
            case 9114: if(field("titleText"))input("Подпись над аватаром",draft.document.optString("titleText"),40,false,value->put("titleText",value));break;
            case 999: save(); break;
        }
    }

    public void input(String title, String value, int limit, boolean multiline, Consumer<String> done) {
        if (getParentActivity() == null) return;
        LinearLayout container = new LinearLayout(getParentActivity()); container.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(4), AndroidUtilities.dp(22), 0);
        EditText edit = new EditText(getParentActivity()); edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider)); edit.setTextSize(16);
        edit.setSingleLine(!multiline); edit.setMaxLines(multiline ? 10 : 1); edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        edit.setText(value); edit.setSelection(edit.length()); container.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog.Builder dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        dialog.setTitle(t(title)); dialog.setView(container);
        dialog.setPositiveButton(t("Готово"), (d, w) -> done.accept(edit.getText().toString().trim()));
        dialog.setNegativeButton(DevGramLocale.getString(R.string.Cancel), null); showDialog(dialog.create());
    }
    private void choose(String title, String[] values, Consumer<Integer> done) {
        CharSequence[] labels = new CharSequence[values.length]; for (int i = 0; i < values.length; i++) labels[i] = t(values[i]);
        chooseRaw(t(title), labels, done);
    }
    private void chooseRaw(String title, CharSequence[] labels, Consumer<Integer> done) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        dialog.setTitle(title); dialog.setItems(labels, (d, i) -> done.accept(i)); showDialog(dialog.create());
    }
    private void editNumber(String key, String title, int low, int high, int fallback) {
        if (!field(key)) return;
        DevGramStudioControls.Slider slider=new DevGramStudioControls.Slider(getParentActivity(),resourceProvider);
        slider.bind(t(title),draft.document.optInt(key,fallback),low,high,key.endsWith("Scale")||key.equals("cardAlpha")?"%":"",true,n->putPreview(key,n),live,()->{});
        BottomSheet sheet=new BottomSheet.Builder(getParentActivity(),false,resourceProvider).setTitle(t(title)).setCustomView(slider).create();showDialog(sheet);
    }
    private void color(String key, String title) {
        if (!field(key)) return;
        LinearLayout content = new LinearLayout(getParentActivity()); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        DevGramColorPickerView picker = new DevGramColorPickerView(getParentActivity(),resourceProvider);
        int[] color = {0xff000000 | draft.document.optInt(key, 0x5077cf)};
        picker.setColor(color[0]); content.addView(picker, new LinearLayout.LayoutParams(-1, -2));
        android.widget.TextView value = new android.widget.TextView(getParentActivity()); value.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider)); value.setGravity(android.view.Gravity.CENTER); value.setText(String.format(java.util.Locale.US, "#%06X", color[0] & 0xffffff));
        content.addView(value, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(32)));
        picker.setListener(selected -> { color[0] = selected; value.setText(String.format(java.util.Locale.US, "#%06X", selected & 0xffffff)); });
        AlertDialog.Builder dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider); dialog.setTitle(t(title)); dialog.setView(content);
        dialog.setPositiveButton(t("Применить"), (d, w) -> put(key, color[0] & 0xffffff));
        dialog.setNeutralButton(t("Как в Telegram"), (d, w) -> { draft.document.remove(key); draft.dirty = true; live.run(); });
        dialog.setNegativeButton(DevGramLocale.getString(R.string.Cancel), null); showDialog(dialog.create());
    }
    private void toggle(String key) { if (field(key)) put(key, draft.document.optInt(key, key.equals("lyrics") || key.equals("lyricsMotion") ? 1 : 0) == 1 ? 0 : 1); }
    private void font(String key) { if (field(key)) choose("Шрифт", new String[]{"Как в Telegram", "С засечками", "Моноширинный", "Узкий", "Рукописный"}, i -> {draft.document.remove(key.equals("chatFont")?"chatFontFile":"nameFontFile");put(key, i);}); }
    private void pickFont(String field) {
        if(fontUploading||!field(field))return;
        selectedFontField=field;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,t("Выберите шрифт TTF или OTF"));
        try{getParentActivity().startActivityForResult(intent,IMPORT_FONT);}catch(Exception e){error(t("Не удалось открыть выбор файла"));selectedFontField=null;}
    }
    private void rows(boolean hidden) {
        if (!access("profile.rows")) return;
        String[] keys = hidden ? new String[]{"bio", "username", "phone", "birthday", "channel", "music", "blocks", "integrations", "balance"} : new String[]{"bio", "username", "phone", "birthday", "channel", "music", "blocks", "integrations"};
        String[] labels = hidden ? new String[]{"О себе", "Имя пользователя", "Телефон", "День рождения", "Канал", "Музыка", "Блоки профиля", "Внешние профили", "Баланс"} : new String[]{"О себе", "Имя пользователя", "Телефон", "День рождения", "Канал", "Музыка", "Блоки профиля", "Внешние профили"};
        choose(hidden ? "Видимость строк" : "Порядок строк", labels, i -> {
            String key = hidden ? "hiddenRows" : "rows"; JSONArray old = array(key), next = new JSONArray(); boolean found = false;
            if (!hidden) next.put(keys[i]);
            for (int j = 0; j < old.length(); j++) { if (old.optString(j).equals(keys[i])) found = true; else next.put(old.optString(j)); }
            if (hidden && !found) next.put(keys[i]); put(key, next);
            success(hidden ? found ? "Строка показана" : "Строка скрыта" : "Строка перемещена в начало");
        });
    }
    private static String layerTitle(String key) {
        switch (key) { case "ring": return t("Кольцо"); case "glow": return t("Свечение"); case "dots": return t("Частицы"); case "stars": return t("Звёзды"); case "sticker": return t("Наклейка"); default: return t("Лента"); }
    }
    private static String nodeTitle(String key) {
        switch (key) { case "time": return t("Время"); case "sine": return t("Синус"); case "multiply": return t("Умножение"); case "add": return t("Сложение"); case "opacity": return t("Прозрачность"); case "rotation": return t("Поворот"); case "scale": return t("Размер"); default: return t("Цвет"); }
    }
    private static String blockTitle(String key) {
        switch (key) { case "text": return t("Текст"); case "link": return t("Ссылка"); case "image": return t("Изображение"); case "code": return t("CSS и сценарий"); default: return t("Музыкальная ссылка"); }
    }
    private void addLayer() {
        if (!access("avatar.layers")) return;
        JSONArray layers = array("layers"); if (layers.length() >= 12) { error(t("Не больше 12 слоёв")); return; }
        String[] keys = {"ring", "glow", "dots", "stars", "sticker", "ribbon"}; String[] labels = new String[keys.length]; for (int i = 0; i < keys.length; i++) labels[i] = layerTitle(keys[i]);
        choose("Добавить слой", labels, i -> { try { layers.put(new JSONObject().put("type", keys[i]).put("color", 0x9fbcff).put("alpha", 100).put("size", 50).put("speed", 20).put("rotation", 0).put("text", "✦")); put("layers", layers); editLayer(layers.length() - 1); } catch (Exception ignored) {} });
    }
    private void editLayer(int index) {
        DevGramStudioItemSheet.show(this,draft,"layers",index,live,operation->moveOrRemove("layers",index,operation));
    }
    private void moveOrRemove(String key, int index, int operation) {
        JSONArray a = array(key), next = new JSONArray();
        int target = Math.max(0, Math.min(a.length() - 1, index + (operation == 0 ? -1 : 1)));
        for (int j = 0; j < a.length(); j++) {
            if (operation == 2 && j == index) continue;
            next.put(operation < 2 ? a.opt(j == index ? target : j == target ? index : j) : a.opt(j));
        }
        if(key.equals("blocks")&&operation==2){JSONObject removed=a.optJSONObject(index);String id=removed==null?"":removed.optString("id","legacy"+index);for(String field:new String[]{"rows","hiddenRows"}){JSONArray old=array(field),kept=new JSONArray();for(int i=0;i<old.length();i++)if(!old.optString(i).equals("block:"+id))kept.put(old.optString(i));putPreview(field,kept);}}
        put(key, next);
    }
    private void addNode() {
        if (!access("avatar.nodes")) return;
        JSONArray nodes = array("nodes"); if (nodes.length() >= 512) { error(t("Не больше 512 узлов")); return; }
        String[] keys = {"time", "sine", "multiply", "add", "opacity", "rotation", "scale", "color"}; String[] labels = new String[keys.length]; for (int i = 0; i < keys.length; i++) labels[i] = nodeTitle(keys[i]);
        choose("Добавить узел", labels, i -> { try {
            int id = 0; boolean used; do { used = false; for (int j = 0; j < nodes.length(); j++) if (nodes.optJSONObject(j).optInt("id") == id) used = true; if (used) id++; } while (used);
            nodes.put(new JSONObject().put("id", id).put("type", keys[i]).put("inputs", new JSONArray()).put("value", 100).put("layer", 0)); put("nodes", nodes); editNode(nodes.length() - 1);
        } catch (Exception ignored) {} });
    }
    private void editNode(int index) {
        DevGramStudioItemSheet.show(this,draft,"nodes",index,live,operation->{
            JSONArray nodes=array("nodes");JSONObject removed=nodes.optJSONObject(index);if(removed==null)return;
            int deleted=removed.optInt("id");JSONArray next=new JSONArray();
            for(int j=0;j<nodes.length();j++)if(j!=index){JSONObject node=nodes.optJSONObject(j);JSONArray old=node.optJSONArray("inputs"),kept=new JSONArray();if(old!=null)for(int k=0;k<old.length();k++)if(old.optInt(k)!=deleted)kept.put(old.optInt(k));try{node.put("inputs",kept);}catch(Exception ignored){}next.put(node);}
            put("nodes",next);
        });
    }
    private void addBlock() {
        if (!access("profile.blocks")) return;
        JSONArray blocks = array("blocks"); if (blocks.length() >= 64) { error(t("Не больше 64 блоков")); return; }
        JSONObject row=DevGramProfileRowSpec.create();blocks.put(row);put("blocks",blocks);editBlock(blocks.length()-1);
    }
    private void editBlock(int index) {
        DevGramStudioItemSheet.show(this,draft,"blocks",index,live,operation->moveOrRemove("blocks",index,operation));
    }
    private void addIntegration() {
        openServiceEditor(null);
    }
    private void openServiceEditor(JSONObject legacy) {
        if (!access("profile.integrations") || !access("profile.blocks")) return;
        JSONArray blocks=array("blocks"); if(blocks.length()>=64){error(t("Не больше 64 блоков"));return;}
        JSONObject row=DevGramProfileRowSpec.create();DevGramProfileRowSpec.type(row,12);
        try {
            if(legacy!=null){String provider=legacy.optString("provider");for(int i=0;i<DevGramProfileRowSpec.PROVIDERS.length;i++)if(provider.equals(DevGramProfileRowSpec.PROVIDERS[i]))row.put("service",i);row.put("url",legacy.optString("handle")).put("accounts",new JSONObject().put(provider,legacy.optString("handle")));JSONArray kept=new JSONArray(),old=array("integrations");for(int i=0;i<old.length();i++)if(old.optJSONObject(i)!=legacy)kept.put(old.optJSONObject(i));put("integrations",kept);}
            blocks.put(row);put("blocks",blocks);editBlock(blocks.length()-1);
        }catch(Exception ignored){}
    }
    private void editIntegration(int index) {
        if (!access("profile.integrations")) return;
        JSONObject item = array("integrations").optJSONObject(index); if (item == null) return;
        choose(item.optString("provider"), new String[]{"Открыть профиль", "Изменить", "Удалить"}, i -> {
            if (i == 2) moveOrRemove("integrations", index, 2);
            else if (i == 0) org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), DevGramProfileBlocksView.integrationUrl(item));
            else openServiceEditor(item);
        });
    }

    private void saveProject() {
        if (!access("avatar.projects")) return;
        input("Название проекта", "", 80, false, title -> request("studio/projects", json("action", "save", "title", title, "document", draft.document,
                "theme", (draft.theme == null ? DevGramProfileStyle.raw(getUserConfig().getClientUserId()) : draft.theme).json()), result -> success("Проект сохранён")));
    }
    private void projects() {
        if (!access("avatar.projects")) return;
        request("studio/projects", json("action", "list"), result -> {
            JSONArray projects = result.optJSONArray("projects");
            if (projects == null || projects.length() == 0) { info("Мои проекты", t("Пока нет сохранённых проектов")); return; }
            String[] labels = new String[projects.length()]; for (int i = 0; i < labels.length; i++) labels[i] = projects.optJSONObject(i).optString("title");
            chooseRaw(t("Мои проекты"), labels, i -> chooseRaw(labels[i], new String[]{t("Применить"), t("Удалить")}, action -> {
                JSONObject project = projects.optJSONObject(i);
                if (action == 0) { draft.document = DevGramProfileDocument.copy(project.optJSONObject("document")); draft.theme = new DevGramProfileStyle.Style(project.optJSONObject("theme")); draft.dirty = true; live.run(); }
                else request("studio/projects", json("action", "delete", "id", project.optLong("id")), ignored -> success("Проект удалён"));
            }));
        });
    }
    private void publish() {
        DevGramWorkshopPublishSheet.show(this,draft,null);
    }
    private void importFile() {
        if (!access("profile.import")) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        getParentActivity().startActivityForResult(intent, IMPORT);
    }
    private void exportTemplate() {
        if (!access("profile.export")) return;
        JSONObject value = json("format", "devgram-profile-1", "document", draft.document,
                "theme", (draft.theme == null ? DevGramProfileStyle.raw(getUserConfig().getClientUserId()) : draft.theme).json());
        export(value.toString().getBytes(StandardCharsets.UTF_8), "application/json", "DevGram-profile.dgprofile");
    }
    private void exportImage(boolean layers) {
        if (!access(layers ? "profile.export.layers" : "profile.export")) return;
        if(draft.onExport!=null){draft.onExport.accept(layers);return;}
        draft.pendingExport=layers;presentFragment(new DevGramProfileEditActivity(draft,false,currentAccount));
    }
    private void export(byte[] bytes, String mime, String name) {
        exportData = bytes;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, name);
        getParentActivity().startActivityForResult(intent, EXPORT);
    }
    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if(requestCode==IMPORT_FONT){
            String field=selectedFontField;selectedFontField=null;
            if(resultCode!=Activity.RESULT_OK||data==null||data.getData()==null||field==null||!field(field))return;
            int request=++fontRequest;long owner=getUserConfig().getClientUserId();fontUploading=true;live.run();
            DevGramProfileFonts.upload(currentAccount,data.getData(),field,(result,error)->{
                if(request!=fontRequest||getUserConfig().getClientUserId()!=owner||isFinishing())return;
                fontUploading=false;
                if(result!=null&&field(field)){put(field,result.optJSONObject("font"));success("Шрифт добавлен в черновик");}
                else{live.run();error(t("feature_locked".equals(error)?"Функция недоступна":"font_limit".equals(error)?"Лимит шрифтов: 40 файлов или 20 МБ":"bad_font".equals(error)?"Нужен корректный шрифт TTF или OTF до 5 МБ":"Не удалось загрузить шрифт"));}
            });return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) { exportData = null; return; }
        if (requestCode == EXPORT && exportData != null) {
            byte[] bytes = exportData; exportData = null;
            Utilities.globalQueue.postRunnable(() -> {
                boolean ok = false;
                try (OutputStream stream = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(data.getData())) { if (stream != null) { stream.write(bytes); ok = true; } } catch (Exception ignored) {}
                final boolean success = ok; AndroidUtilities.runOnUIThread(() -> { if (success) success("Файл сохранён"); else error(t("Не удалось сохранить файл")); });
            });
        } else if (requestCode == IMPORT && access("profile.import")) {
            Utilities.globalQueue.postRunnable(() -> {
                JSONObject imported = null;
                try (InputStream stream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(data.getData())) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
                    while (stream != null && (n = stream.read(buffer)) > 0) { bytes.write(buffer, 0, n); if (bytes.size() > 1048576) throw new IllegalArgumentException(); }
                    imported = new JSONObject(bytes.toString("UTF-8")); if (!"devgram-profile-1".equals(imported.optString("format"))&&!"devgram-frame-1".equals(imported.optString("format"))) throw new IllegalArgumentException();
                } catch (Exception ignored) { imported = null; }
                final JSONObject value = imported;
                AndroidUtilities.runOnUIThread(() -> {
                    if (value == null || value.optJSONObject("document") == null) { error(t("Файл оформления не поддерживается или повреждён")); return; }
                    importFonts(value.optJSONObject("document"),0,()->request("studio/validate", json("document", value.optJSONObject("document")), result -> {
                        JSONObject importedDoc=result.optJSONObject("document");
                        if(value.optString("format").equals("devgram-frame-1")){for(String key:new String[]{"layers","nodes","frameGraph"})try{if(importedDoc!=null&&importedDoc.has(key))draft.document.put(key,importedDoc.opt(key));else draft.document.remove(key);}catch(Exception ignored){}}
                        else{draft.document = DevGramProfileDocument.copy(importedDoc);if (value.optJSONObject("theme") != null) draft.theme = new DevGramProfileStyle.Style(value.optJSONObject("theme")).effective(getUserConfig().getClientUserId());}
                        draft.dirty = true;if(draft.onChanged!=null)draft.onChanged.run();live.run(); success("Оформление импортировано. Сохраните изменения для публикации.");
                    }));
                });
            });
        }
    }
    private void ping() {
        long start = android.os.SystemClock.elapsedRealtime();
        org.telegram.messenger.DevGramNetworkQueue.postRunnable(() -> {
            boolean ok = false; try { ok = DevGramProfilePolicy.http("GET", "profile-policy.json", null).has("tiers"); } catch (Exception ignored) {}
            final boolean success = ok; long elapsed = android.os.SystemClock.elapsedRealtime() - start;
            AndroidUtilities.runOnUIThread(() -> info("Проверка связи", success ? t("Сервер доступен") + ": " + elapsed + " ms" : t("Нет соединения с сервером")));
        });
    }
    private void importFonts(JSONObject document,int index,Runnable done) {
        if(!DevGramProfileCustomization.enabled()||isFinishing())return;
        String[] fields={"nameFontFile","chatFontFile"};
        if(index>=fields.length){JSONArray textures=DevGramProfileTextures.sources(document);if(textures.length()==0)done.run();else request("studio/textures/import",json("sources",textures),result->done.run());return;}
        String field=fields[index];JSONObject metadata=document.optJSONObject(field);
        if(metadata==null){importFonts(document,index+1,done);return;}
        if(!field(field))return;
        request("studio/fonts/import",json("field",field,"font",metadata),result->importFonts(document,index+1,done));
    }
    private void save() { save(null); }
    private void save(Runnable afterSave) {
        if(saving||closed||getUserConfig().getClientUserId()!=owner)return;
        if(!draft.dirty){if(afterSave!=null)afterSave.run();return;}
        saving = true; live.run();
        Runnable saveDoc = () -> DevGramProfileDocument.save(currentAccount, draft.subject, draft.document, draft.theme, (result, error) -> {
            saving = false;
            if(closed||getUserConfig().getClientUserId()!=owner)return;
            if (result != null) { draft.document = DevGramProfileDocument.editable(result.optJSONObject("document")); draft.dirty = false; if (draft.onSaved != null) draft.onSaved.run(); org.telegram.messenger.DevGramMusicPresence.configure(currentAccount, draft.document.optInt("activity") == 1); success("Оформление сохранено"); if(afterSave!=null)afterSave.run(); }
            else error(t("Не удалось сохранить оформление. Проверьте подключение и доступ к функциям."));
            live.run();
        });
        if (draft.subject < 0 || draft.theme == null) saveDoc.run();
        else DevGramProfileStyle.save(currentAccount, draft.theme.effective(getUserConfig().getClientUserId()), (style, error) -> {
            if(closed||getUserConfig().getClientUserId()!=owner)return;
            if (error == null) { draft.theme = style; saveDoc.run(); }
            else { saving = false; live.run(); error(t("Не удалось сохранить оформление")); }
        });
    }
    private void request(String route, JSONObject data, Consumer<JSONObject> done) {
        DevGramProfilePolicy.request(currentAccount, route, data, (result, error) -> {
            if (result == null) error(t("Не удалось выполнить действие. Проверьте доступ и соединение.")); else done.accept(result);
        });
    }
    public static JSONObject json(Object... pairs) {
        JSONObject value = new JSONObject(); try { for (int i = 0; i < pairs.length; i += 2) value.put((String) pairs[i], pairs[i + 1]); } catch (Exception ignored) {} return value;
    }
    private void success(String text) { if (getParentActivity() != null) BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, t(text)).show(); }
    private void error(String text) { if (getParentActivity() != null) BulletinFactory.of(this).createErrorBulletin(text).show(); }
    private void info(String title, String message) { AlertDialog.Builder d = new AlertDialog.Builder(getParentActivity(), resourceProvider); d.setTitle(t(title)); d.setMessage(message); d.setPositiveButton(t("Понятно"), null); showDialog(d.create()); }
    private void confirm(String title, Runnable done) { AlertDialog.Builder d = new AlertDialog.Builder(getParentActivity(), resourceProvider); d.setTitle(t(title)); d.setPositiveButton(t("Продолжить"), (dialog, w) -> done.run()); d.setNegativeButton(DevGramLocale.getString(R.string.Cancel), null); showDialog(d.create()); }
    @Override public void onResume() { super.onResume(); live.run(); }
    @Override public boolean onBackPressed(boolean invoked) {
        if (saving) return false;
        if (!page.equals("home") || !draft.dirty) return super.onBackPressed(invoked);
        if (invoked) exitPrompt.show(this, () -> save(() -> finishFragment()), () -> {
            if(closed||saving||getUserConfig().getClientUserId()!=owner)return;
            draft.document=DevGramProfileDocument.copy(DevGramProfileDocument.raw(draft.target(currentAccount)));draft.theme=DevGramProfileStyle.raw(draft.target(currentAccount));draft.dirty=false;
            if(draft.onChanged!=null)draft.onChanged.run();finishFragment();
        });
        return false;
    }
    @Override public boolean canBeginSlide() {
        if(saving)return false;
        if(page.equals("home")&&draft.dirty){onBackPressed(true);return false;}
        return super.canBeginSlide();
    }
    @Override public void onFragmentDestroy() { closed=true;exitPrompt.dismiss();fontRequest++;selectedFontField=null;DevGramProfilePolicy.unlisten(live); exportData = null; if (preview != null) preview.release(); if (blocksPreview != null) blocksPreview.release(); super.onFragmentDestroy(); }
}

package org.telegram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DevGramFilterController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** AyuGram-inspired filter manager with separate shared, per-chat and local-ban screens. */
public class DevGramFiltersActivity extends BaseFragment {
    private static final int MODE_ROOT = 0, MODE_RULES = 1, MODE_BANS = 2;
    private static final int ID_MASTER = 1, ID_PRIVATE = 2, ID_BLOCKED = 3, ID_SHARED = 4, ID_BANS = 5;
    private static final int ID_SELECT_CHAT = 8;
    private static final int ID_IMPORT = 9, ID_EXPORT = 10, ID_CLEAR = 11, ID_ADD = 12;
    private static final int DIALOG_BASE = 1000, RULE_BASE = 10000, BAN_BASE = 20000;

    private final int mode;
    private final long scopeDialogId;
    private UniversalRecyclerView listView;
    private final ArrayList<DevGramFilterController.Rule> visibleRules = new ArrayList<>();
    private final ArrayList<Long> dialogScopes = new ArrayList<>();
    private ArrayList<Long> bans = new ArrayList<>();

    public DevGramFiltersActivity() {
        this(MODE_ROOT, 0);
    }

    private DevGramFiltersActivity(int mode, long scopeDialogId) {
        this.mode = mode;
        this.scopeDialogId = scopeDialogId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(screenTitle());
        if (mode == MODE_ROOT) {
            ActionBarMenuItem more = actionBar.createMenu().addItem(0, R.drawable.ic_ab_other);
            more.addSubItem(ID_SELECT_CHAT, 0, "Выбрать чат");
            more.addSubItem(ID_IMPORT, R.drawable.msg_archive, "Импорт базы данных");
            more.addSubItem(ID_EXPORT, R.drawable.msg_unarchive, "Экспорт базы данных");
            more.addSubItem(ID_CLEAR, R.drawable.msg_clear, "Очистить");
        } else {
            actionBar.createMenu().addItem(ID_ADD, R.drawable.msg_add);
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == ID_ADD) {
                    if (mode == MODE_RULES) editRule(null); else selectChat(true);
                } else if (id == ID_SELECT_CHAT) selectChat(false);
                else if (id == ID_IMPORT) importClipboard();
                else if (id == ID_EXPORT) {
                    AndroidUtilities.addToClipboard(DevGramFilterController.exportJson());
                    BulletinFactory.of(DevGramFiltersActivity.this).createCopyLinkBulletin().show();
                } else if (id == ID_CLEAR) confirmClear();
            }
        });

        FrameLayout content = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = content;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private String screenTitle() {
        if (mode == MODE_BANS) return "Локальная блокировка";
        if (mode == MODE_RULES) return scopeDialogId == 0 ? "Общие фильтры" : "Фильтры чата";
        return "Фильтры сообщений";
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (mode == MODE_RULES) fillRules(items);
        else if (mode == MODE_BANS) fillBans(items);
        else fillRoot(items);
    }

    private void fillRoot(ArrayList<UItem> items) {
        ArrayList<DevGramFilterController.Rule> all = DevGramFilterController.getRules();
        int sharedCount = 0;
        LinkedHashMap<Long, Integer> scopedCounts = new LinkedHashMap<>();
        for (DevGramFilterController.Rule rule : all) {
            if (rule.dialogId == 0) sharedCount++;
            else scopedCounts.put(rule.dialogId, scopedCounts.containsKey(rule.dialogId) ? scopedCounts.get(rule.dialogId) + 1 : 1);
        }

        items.add(UItem.asHeader("Основные"));
        items.add(UItem.asCheck(ID_MASTER, "Включить фильтры").setChecked(DevGramFilterController.isEnabled()));
        items.add(UItem.asCheck(ID_PRIVATE, "Общие фильтры в личных чатах").setChecked(DevGramFilterController.filterPrivateChats()));
        items.add(UItem.asCheck(ID_BLOCKED, "Скрывать сообщения заблокированных").setChecked(DevGramFilterController.hideBlocked()));
        items.add(UItem.asShadow("Фильтрация выполняется только на этом устройстве. Отправитель не узнает, что сообщение скрыто."));

        items.add(UItem.asButton(ID_SHARED, "Общие фильтры",
                countLabel(sharedCount, "фильтр", "фильтра", "фильтров")));
        int banCount = DevGramFilterController.getShadowBans().size();
        items.add(UItem.asButton(ID_BANS, "Локальная блокировка",
                countLabel(banCount, "запись", "записи", "записей")));

        dialogScopes.clear();
        if (!scopedCounts.isEmpty()) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader("Отдельные чаты"));
            for (Map.Entry<Long, Integer> entry : scopedCounts.entrySet()) {
                long did = entry.getKey();
                int index = dialogScopes.size();
                dialogScopes.add(did);
                String subtitle = countLabel(entry.getValue(), "фильтр", "фильтра", "фильтров");
                TLObject object = dialogObject(did);
                if (object != null) {
                    UItem item = UItem.asProfileCell(object);
                    item.id = DIALOG_BASE + index;
                    item.subtext = subtitle;
                    items.add(item);
                } else {
                    items.add(UItem.asButton(DIALOG_BASE + index, dialogTitle(did), subtitle));
                }
            }
        }
        items.add(UItem.asShadow("Добавить отдельный набор можно через меню ⋮ → «Выбрать чат»."));
    }

    private void fillRules(ArrayList<UItem> items) {
        visibleRules.clear();
        for (DevGramFilterController.Rule rule : DevGramFilterController.getRules()) {
            if (rule.dialogId == scopeDialogId) visibleRules.add(rule);
        }
        items.add(UItem.asHeader(scopeDialogId == 0 ? "Общий набор" : dialogTitle(scopeDialogId)));
        if (visibleRules.isEmpty()) {
            items.add(UItem.asShadow("В этом наборе пока нет фильтров. Для добавления нажмите +."));
            return;
        }
        for (int i = 0; i < visibleRules.size(); i++) {
            DevGramFilterController.Rule rule = visibleRules.get(i);
            UItem item = UItem.asButton(RULE_BASE + i, rule.text, ruleDescription(rule));
            item.onBind(view -> view.setAlpha(rule.enabled ? 1f : 0.5f));
            items.add(item);
        }
        items.add(UItem.asShadow("Нажмите для изменения. Удерживайте фильтр, чтобы включить, выключить или удалить его."));
    }

    private void fillBans(ArrayList<UItem> items) {
        bans = DevGramFilterController.getShadowBans();
        items.add(UItem.asHeader("Скрытые отправители"));
        if (bans.isEmpty()) {
            items.add(UItem.asShadow("Список пуст. Нажмите +, чтобы выбрать пользователя или чат."));
            return;
        }
        for (int i = 0; i < bans.size(); i++) {
            long did = bans.get(i);
            TLObject object = dialogObject(did);
            if (object != null) {
                UItem item = UItem.asProfileCell(object);
                item.id = BAN_BASE + i;
                item.subtext = "Локально скрыт · нажмите для удаления";
                items.add(item);
            } else {
                items.add(UItem.asButton(BAN_BASE + i, dialogTitle(did), "ID " + did));
            }
        }
        items.add(UItem.asShadow("Для добавления нажмите +. При локальной блокировке отправитель не получает уведомлений."));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MASTER) DevGramFilterController.setEnabled(!DevGramFilterController.isEnabled());
        else if (item.id == ID_PRIVATE) DevGramFilterController.setFilterPrivateChats(!DevGramFilterController.filterPrivateChats());
        else if (item.id == ID_BLOCKED) DevGramFilterController.setHideBlocked(!DevGramFilterController.hideBlocked());
        else if (item.id == ID_SHARED) { presentFragment(new DevGramFiltersActivity(MODE_RULES, 0)); return; }
        else if (item.id == ID_BANS) { presentFragment(new DevGramFiltersActivity(MODE_BANS, 0)); return; }
        else if (item.id >= DIALOG_BASE && item.id < RULE_BASE) {
            int index = item.id - DIALOG_BASE;
            if (index >= 0 && index < dialogScopes.size()) presentFragment(new DevGramFiltersActivity(MODE_RULES, dialogScopes.get(index)));
            return;
        } else if (item.id >= RULE_BASE && item.id < BAN_BASE) {
            int index = item.id - RULE_BASE;
            if (index >= 0 && index < visibleRules.size()) {
                editRule(visibleRules.get(index));
                return;
            }
        } else if (item.id >= BAN_BASE) {
            int index = item.id - BAN_BASE;
            if (index >= 0 && index < bans.size()) confirmRemoveBan(bans.get(index));
            return;
        }
        refresh();
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < RULE_BASE || item.id >= BAN_BASE) return false;
        int index = item.id - RULE_BASE;
        if (index < 0 || index >= visibleRules.size()) return false;
        DevGramFilterController.Rule rule = visibleRules.get(index);
        new AlertDialog.Builder(getParentActivity()).setTitle(rule.text)
                .setItems(new CharSequence[]{rule.enabled ? "Выключить" : "Включить", "Изменить", "Удалить"}, (dialog, which) -> {
                    if (which == 0) { rule.enabled = !rule.enabled; saveVisibleRule(rule); refresh(); }
                    else if (which == 1) editRule(rule);
                    else confirmRemoveRule(rule);
                }).setNegativeButton("Отмена", null).show();
        return true;
    }

    private void editRule(DevGramFilterController.Rule old) {
        Context context = getParentActivity();
        if (context == null) return;
        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(6), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
        scrollView.addView(content, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        OutlineTextContainerView outline = new OutlineTextContainerView(context, resourceProvider);
        outline.setText("Регулярное выражение");
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        input.setHint("Например: реклама|спам");
        input.setBackground(null);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        input.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider));
        input.setCursorWidth(1.5f);
        input.setCursorSize(AndroidUtilities.dp(20));
        if (old != null) { input.setText(old.text); input.setSelection(input.length()); }
        input.setOnFocusChangeListener((v, focused) -> outline.animateSelection(focused, !TextUtils.isEmpty(input.getText())));
        outline.attachEditText(input);
        outline.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        content.addView(outline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 4));

        TextView hint = new TextView(context);
        hint.setText("Можно использовать обычный текст или регулярное выражение Java.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        content.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 12, 10, 0));

        TextCheckCell enabled = optionCell(context, "Фильтр включён", old == null || old.enabled);
        TextCheckCell insensitive = optionCell(context, "Без учёта регистра", old != null && old.caseInsensitive);
        TextCheckCell reversed = optionCell(context, "Обратный фильтр", old != null && old.reversed);
        content.addView(enabled, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
        content.addView(insensitive, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
        content.addView(reversed, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

        TextView error = new TextView(context);
        error.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        error.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        error.setVisibility(View.GONE);
        content.addView(error, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 4, 10, 0));

        ButtonWithCounterView save = new ButtonWithCounterView(context, resourceProvider).setRound();
        save.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
        save.setText(old == null ? "Добавить фильтр" : "Сохранить изменения", false);
        content.addView(save, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 8, 8, 0, 0));

        BottomSheet.Builder builder = new BottomSheet.Builder(context, true, resourceProvider);
        builder.setTitle(old == null ? "Новый фильтр" : "Изменить фильтр", true).setCustomView(scrollView);
        BottomSheet sheet = builder.create();
        save.setOnClickListener(v -> {
            String pattern = input.getText().toString().trim();
            if (TextUtils.isEmpty(pattern)) {
                error.setText("Введите текст или регулярное выражение"); error.setVisibility(View.VISIBLE);
                outline.animateError(1f); AndroidUtilities.shakeViewSpring(outline); return;
            }
            try { Pattern.compile(pattern, insensitive.isChecked() ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0); }
            catch (Throwable t) {
                error.setText("Некорректное регулярное выражение"); error.setVisibility(View.VISIBLE);
                outline.animateError(1f); AndroidUtilities.shakeViewSpring(outline); return;
            }
            DevGramFilterController.Rule rule = old == null ? new DevGramFilterController.Rule() : old;
            rule.text = pattern; rule.dialogId = scopeDialogId; rule.enabled = enabled.isChecked();
            rule.caseInsensitive = insensitive.isChecked(); rule.reversed = reversed.isChecked();
            if (old == null) DevGramFilterController.addRule(rule); else saveVisibleRule(rule);
            sheet.dismiss(); refresh();
        });
        sheet.setOnShowListener(dialog -> { input.requestFocus(); AndroidUtilities.showKeyboard(input); });
        sheet.show();
    }

    private TextCheckCell optionCell(Context context, String text, boolean checked) {
        TextCheckCell cell = new TextCheckCell(context, 0, true, resourceProvider);
        cell.setTextAndCheck(text, checked, false);
        cell.setBackground(Theme.getSelectorDrawable(false, resourceProvider));
        cell.setOnClickListener(v -> cell.setChecked(!cell.isChecked()));
        return cell;
    }

    private void saveVisibleRule(DevGramFilterController.Rule changed) {
        ArrayList<DevGramFilterController.Rule> all = DevGramFilterController.getRules();
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(changed.id)) { all.set(i, changed); break; }
        DevGramFilterController.saveRules(all);
    }

    private void confirmRemoveRule(DevGramFilterController.Rule rule) {
        new AlertDialog.Builder(getParentActivity()).setTitle("Удалить фильтр?").setMessage(rule.text)
                .setPositiveButton("Удалить", (dialog, which) -> { DevGramFilterController.removeRule(rule.id); refresh(); })
                .setNegativeButton("Отмена", null).show();
    }

    private void selectChat(boolean addToBans) {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true); args.putBoolean("checkCanWrite", false);
        args.putBoolean("canSelectTopics", false); args.putBoolean("allowSwitchAccount", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids.isEmpty()) return false;
            MessagesStorage.TopicKey key = dids.get(0);
            if (addToBans) { DevGramFilterController.setShadowBanned(key.dialogId, true); fragment.finishFragment(); }
            else presentFragment(new DevGramFiltersActivity(MODE_RULES, key.dialogId), true);
            return true;
        });
        presentFragment(picker);
    }

    private void confirmRemoveBan(long did) {
        new AlertDialog.Builder(getParentActivity()).setTitle("Убрать из локальной блокировки?").setMessage(dialogTitle(did))
                .setPositiveButton("Убрать", (dialog, which) -> { DevGramFilterController.setShadowBanned(did, false); refresh(); })
                .setNegativeButton("Отмена", null).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(getParentActivity()).setTitle("Очистить все фильтры?")
                .setMessage("Будут удалены регулярные фильтры и список локальной блокировки.")
                .setPositiveButton("Очистить", (dialog, which) -> { DevGramFilterController.clearAll(); refresh(); })
                .setNegativeButton("Отмена", null).show();
    }

    private void importClipboard() {
        try {
            ClipboardManager manager = (ClipboardManager) getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData data = manager.getPrimaryClip();
            String value = data == null || data.getItemCount() == 0 ? "" : data.getItemAt(0).coerceToText(getParentActivity()).toString();
            if (!DevGramFilterController.importJson(value, true)) throw new IllegalArgumentException();
            refresh(); BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Фильтры импортированы").show();
        } catch (Throwable t) { BulletinFactory.of(this).createErrorBulletin("В буфере нет подходящего набора фильтров").show(); }
    }


    private String ruleDescription(DevGramFilterController.Rule rule) {
        return (rule.caseInsensitive ? "Без учёта регистра" : "С учётом регистра") + (rule.reversed ? " · обратный" : "");
    }

    private String countLabel(int count, String one, String few, String many) {
        int mod100 = count % 100, mod10 = count % 10;
        String word = mod100 >= 11 && mod100 <= 14 ? many : mod10 == 1 ? one : mod10 >= 2 && mod10 <= 4 ? few : many;
        return count + " " + word;
    }

    private TLObject dialogObject(long did) {
        try {
            if (DialogObject.isEncryptedDialog(did)) return null;
            if (DialogObject.isUserDialog(did)) return getMessagesController().getUser(did);
            return getMessagesController().getChat(-did);
        } catch (Throwable t) { return null; }
    }

    private String dialogTitle(long did) {
        try {
            if (DialogObject.isEncryptedDialog(did)) return "Секретный чат";
            if (DialogObject.isUserDialog(did)) {
                TLRPC.User user = getMessagesController().getUser(did);
                if (user != null) {
                    if (UserObject.isUserSelf(user)) return LocaleController.getString(R.string.SavedMessages);
                    String name = ContactsController.formatName(user.first_name, user.last_name);
                    if (!TextUtils.isEmpty(name)) return name;
                }
            } else {
                TLRPC.Chat chat = getMessagesController().getChat(-did);
                if (chat != null && !TextUtils.isEmpty(chat.title)) return chat.title;
            }
        } catch (Throwable ignore) {}
        return "Чат " + did;
    }

    private void refresh() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }
}

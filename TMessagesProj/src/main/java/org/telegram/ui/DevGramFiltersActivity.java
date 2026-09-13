package org.telegram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramFilterController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class DevGramFiltersActivity extends BaseFragment {
    private static final int ID_MASTER = 1, ID_PRIVATE = 2, ID_BLOCKED = 3, ID_ADD = 4,
            ID_EXPORT = 5, ID_IMPORT = 6, ID_ADD_BAN = 7, RULE_BASE = 1000, BAN_BASE = 12000;
    private UniversalRecyclerView listView;
    private ArrayList<DevGramFilterController.Rule> rules = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Фильтры сообщений");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        rules = DevGramFilterController.getRules();
        FrameLayout content = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::click, this::longClick);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_MASTER, "Включить фильтры").setChecked(DevGramFilterController.isEnabled()));
        items.add(UItem.asCheck(ID_PRIVATE, "Применять общие фильтры в личных чатах").setChecked(DevGramFilterController.filterPrivateChats()));
        items.add(UItem.asCheck(ID_BLOCKED, "Скрывать сообщения заблокированных").setChecked(DevGramFilterController.hideBlocked()));
        items.add(UItem.asShadow("Фильтрация выполняется только на устройстве. Отправитель не узнает, что сообщение скрыто."));
        items.add(UItem.asHeader("Регулярные выражения"));
        items.add(UItem.asButton(ID_ADD, R.drawable.msg_add, "Добавить фильтр"));
        for (int i = 0; i < rules.size(); i++) {
            DevGramFilterController.Rule r = rules.get(i);
            String where = r.dialogId == 0 ? "Все чаты" : "Чат " + r.dialogId;
            String flags = (r.caseInsensitive ? " · без регистра" : "") + (r.reversed ? " · обратный" : "");
            items.add(UItem.asCheck(RULE_BASE + i, r.text + "\n" + where + flags).setChecked(r.enabled));
        }
        if (rules.isEmpty()) items.add(UItem.asShadow("Фильтров пока нет."));
        else items.add(UItem.asShadow("Нажмите, чтобы включить или выключить. Зажмите для изменения или удаления."));
        items.add(UItem.asHeader("Локальная блокировка"));
        items.add(UItem.asButton(ID_ADD_BAN, R.drawable.msg_block, "Добавить пользователя или чат по ID"));
        ArrayList<Long> bans = DevGramFilterController.getShadowBans();
        for (int i = 0; i < bans.size(); i++) {
            items.add(UItem.asButton(BAN_BASE + i, "ID " + bans.get(i), "Нажмите, чтобы удалить"));
        }
        items.add(UItem.asShadow("Сообщения и реакции выбранных пользователей скрываются только на этом устройстве."));
        items.add(UItem.asHeader("Перенос"));
        items.add(UItem.asButton(ID_EXPORT, R.drawable.msg_copy, "Скопировать набор фильтров"));
        items.add(UItem.asButton(ID_IMPORT, R.drawable.msg_download, "Импортировать из буфера"));
        items.add(UItem.asShadow(null));
    }

    private void click(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MASTER) DevGramFilterController.setEnabled(!DevGramFilterController.isEnabled());
        else if (item.id == ID_PRIVATE) DevGramFilterController.setFilterPrivateChats(!DevGramFilterController.filterPrivateChats());
        else if (item.id == ID_BLOCKED) DevGramFilterController.setHideBlocked(!DevGramFilterController.hideBlocked());
        else if (item.id == ID_ADD) { editRule(null); return; }
        else if (item.id == ID_ADD_BAN) { addShadowBan(); return; }
        else if (item.id == ID_EXPORT) {
            AndroidUtilities.addToClipboard(DevGramFilterController.exportJson());
            BulletinFactory.of(this).createCopyLinkBulletin().show(); return;
        } else if (item.id == ID_IMPORT) { importClipboard(); return; }
        else if (item.id >= RULE_BASE && item.id - RULE_BASE < rules.size()) {
            DevGramFilterController.Rule r = rules.get(item.id - RULE_BASE);
            r.enabled = !r.enabled; DevGramFilterController.saveRules(rules);
        } else if (item.id >= BAN_BASE) {
            ArrayList<Long> bans = DevGramFilterController.getShadowBans();
            int i = item.id - BAN_BASE;
            if (i >= 0 && i < bans.size()) DevGramFilterController.setShadowBanned(bans.get(i), false);
        }
        refresh();
    }

    private boolean longClick(UItem item, View view, int position, float x, float y) {
        int i = item.id - RULE_BASE;
        if (i < 0 || i >= rules.size()) return false;
        DevGramFilterController.Rule r = rules.get(i);
        new AlertDialog.Builder(getParentActivity()).setTitle(r.text)
                .setItems(new CharSequence[]{"Изменить", "Удалить"}, (d, which) -> {
                    if (which == 0) editRule(r); else {
                        DevGramFilterController.removeRule(r.id); rules = DevGramFilterController.getRules(); refresh();
                    }
                }).setNegativeButton("Отмена", null).show();
        return true;
    }

    private EditText input(Context context, String hint, String value) {
        EditText e = new EditText(context);
        e.setHint(hint); e.setText(value); e.setTextSize(16);
        e.setSingleLine(true); e.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        e.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        e.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        return e;
    }

    private void editRule(DevGramFilterController.Rule old) {
        Context c = getParentActivity(); if (c == null) return;
        LinearLayout box = new LinearLayout(c); box.setOrientation(LinearLayout.VERTICAL);
        EditText regex = input(c, "Регулярное выражение", old == null ? "" : old.text);
        EditText dialogId = input(c, "ID чата, 0 — все чаты", old == null ? "0" : Long.toString(old.dialogId));
        dialogId.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        CheckBox insensitive = new CheckBox(c); insensitive.setText("Без учёта регистра");
        CheckBox reversed = new CheckBox(c); reversed.setText("Обратный фильтр (скрывать несовпадения)");
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
        insensitive.setTextColor(textColor); reversed.setTextColor(textColor);
        if (old != null) { insensitive.setChecked(old.caseInsensitive); reversed.setChecked(old.reversed); }
        box.addView(regex); box.addView(dialogId); box.addView(insensitive); box.addView(reversed);
        new AlertDialog.Builder(c).setTitle(old == null ? "Новый фильтр" : "Изменить фильтр").setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String pattern = regex.getText().toString().trim(); if (pattern.isEmpty()) return;
                    try { java.util.regex.Pattern.compile(pattern); } catch (Throwable e) {
                        BulletinFactory.of(this).createErrorBulletin("Некорректное регулярное выражение").show(); return;
                    }
                    DevGramFilterController.Rule r = old == null ? new DevGramFilterController.Rule() : old;
                    r.text = pattern; r.caseInsensitive = insensitive.isChecked(); r.reversed = reversed.isChecked();
                    try { r.dialogId = Long.parseLong(dialogId.getText().toString()); } catch (Throwable ignore) { r.dialogId = 0; }
                    if (old == null) rules.add(r);
                    DevGramFilterController.saveRules(rules); refresh();
                }).setNegativeButton("Отмена", null).show();
    }

    private void importClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData data = cm.getPrimaryClip();
            String text = data == null || data.getItemCount() == 0 ? "" : data.getItemAt(0).coerceToText(getParentActivity()).toString();
            if (!DevGramFilterController.importJson(text, true)) throw new IllegalArgumentException();
            rules = DevGramFilterController.getRules(); refresh();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Фильтры импортированы").show();
        } catch (Throwable e) { BulletinFactory.of(this).createErrorBulletin("В буфере нет подходящего набора фильтров").show(); }
    }

    private void addShadowBan() {
        Context c = getParentActivity(); if (c == null) return;
        EditText id = input(c, "ID пользователя или отрицательный ID чата", "");
        id.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        new AlertDialog.Builder(c).setTitle("Локальная блокировка").setView(id)
                .setPositiveButton("Добавить", (d, w) -> {
                    try {
                        long value = Long.parseLong(id.getText().toString().trim());
                        DevGramFilterController.setShadowBanned(value, true); refresh();
                    } catch (Throwable e) { BulletinFactory.of(this).createErrorBulletin("Укажите корректный ID").show(); }
                }).setNegativeButton("Отмена", null).show();
    }

    private void refresh() { if (listView != null) listView.adapter.update(true); }
}

package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.DevGramAiHistoryMessageCell;
import org.telegram.ui.Components.DevGramSettingsLayout;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Saved AI-chat turns, with local history controls. */
public class DevGramAiHistoryActivity extends DevGramSettingsFragment {
    private static final int ID_SAVE = 1;
    private static final int ID_CLEAR = 2;
    private static final int ID_MESSAGE_BASE = 100;

    private UniversalRecyclerView listView;
    private final ArrayList<Entry> entries = new ArrayList<>();

    private static final class Entry {
        final int id;
        final JSONObject message;
        final String content;
        boolean expanded;

        Entry(int id, JSONObject message) {
            this.id = id;
            this.message = message;
            this.content = message.optString("content", "").trim();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("История ИИ-чата");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        reloadEntries();
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        return fragmentView = new DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
    }

    private JSONArray history() {
        try {
            return new JSONArray(MessagesController.getGlobalMainSettings().getString("dg_aiHistory", "[]"));
        } catch (Throwable ignore) {
            return new JSONArray();
        }
    }

    private void reloadEntries() {
        entries.clear();
        JSONArray history = history();
        for (int i = 0; i < history.length(); i++) {
            JSONObject message = history.optJSONObject(i);
            if (message != null && !TextUtils.isEmpty(message.optString("content"))) {
                entries.add(new Entry(ID_MESSAGE_BASE + i, message));
            }
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean save = MessagesController.getGlobalMainSettings().getBoolean("dg_aiSaveHistory", true);
        items.add(UItem.asCheck(ID_SAVE, "Сохранять историю").setChecked(save));
        items.add(UItem.asShadow("История хранится только на устройстве и позволяет модели учитывать предыдущие сообщения."));

        if (entries.isEmpty()) return;

        items.add(UItem.asHeader("Сообщения: " + entries.size()));
        for (Entry entry : entries) {
            items.add(DevGramAiHistoryMessageCell.Factory.asHistoryCell(
                    entry.id, entry.message, entry.content, entry.expanded));
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_CLEAR, R.drawable.msg_delete, "Очистить историю").red());
        items.add(UItem.asShadow(null));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SAVE) {
            boolean old = MessagesController.getGlobalMainSettings().getBoolean("dg_aiSaveHistory", true);
            MessagesController.getGlobalMainSettings().edit().putBoolean("dg_aiSaveHistory", !old).apply();
            listView.adapter.update(true);
        } else if (item.id == ID_CLEAR) {
            if (getParentActivity() == null) return;
            org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                    new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
            builder.setTitle("Очистить историю?");
            builder.setMessage("Все сохранённые запросы и ответы ИИ будут удалены с устройства.");
            builder.setNegativeButton("Отмена", null);
            builder.setPositiveButton("Очистить", (dialog, which) -> {
                MessagesController.getGlobalMainSettings().edit().remove("dg_aiHistory").apply();
                entries.clear();
                listView.adapter.update(true);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "История очищена").show();
            });
            showDialog(builder.create());
        } else {
            Entry entry = findEntry(item.id);
            if (entry != null) {
                entry.expanded = !entry.expanded;
                listView.adapter.update(true);
            }
        }
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        Entry entry = findEntry(item.id);
        if (entry == null) return false;
        ItemOptions options = ItemOptions.makeOptions(this, view);
        options.add(R.drawable.msg_copy, "Копировать", () -> {
            if (AndroidUtilities.addToClipboard(entry.content)) {
                BulletinFactory.of(this).createCopyBulletin("Текст скопирован").show();
            }
        });
        options.add(R.drawable.msg_delete, "Удалить", true, () -> deleteTurn(entry.id));
        options.setScrimViewBackground(listView.getClipBackground(view));
        options.show();
        return true;
    }

    private Entry findEntry(int id) {
        for (Entry entry : entries) if (entry.id == id) return entry;
        return null;
    }

    private void deleteTurn(int id) {
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id == id) { index = i; break; }
        }
        if (index < 0) return;
        int from = "assistant".equals(entries.get(index).message.optString("role")) ? index - 1 : index;
        int to = from + 1;
        if (from < 0 || to >= entries.size()
                || "assistant".equals(entries.get(from).message.optString("role"))
                || !"assistant".equals(entries.get(to).message.optString("role"))) {
            from = to = index;
        }
        for (int i = to; i >= from; i--) entries.remove(i);
        JSONArray saved = new JSONArray();
        for (Entry entry : entries) saved.put(entry.message);
        MessagesController.getGlobalMainSettings().edit().putString("dg_aiHistory", saved.toString()).apply();
        listView.adapter.update(true);
    }
}

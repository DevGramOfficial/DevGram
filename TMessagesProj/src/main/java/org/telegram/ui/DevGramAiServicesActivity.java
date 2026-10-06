package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Сервисы ИИ (повтор ServicesActivity exteraGram): список сервисов + «Новый сервис». Выбранный = активный. */
public class DevGramAiServicesActivity extends DevGramSettingsFragment {
    private static final int ID_NEW = 1;
    private static final int ID_BASE = 100;

    private UniversalRecyclerView listView;

    public static JSONArray services() {
        try {
            return new JSONArray(MessagesController.getGlobalMainSettings().getString("dg_aiServices", "[]"));
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    private static int selected() {
        return MessagesController.getGlobalMainSettings().getInt("dg_aiServiceSel", 0);
    }

    // Применяем выбранный сервис в активные ключи, которые читает DevGramAiClient.
    public static void applyActive(int index) {
        JSONObject s = services().optJSONObject(index);
        if (s == null) return;
        MessagesController.getGlobalMainSettings().edit()
                .putString("dg_aiEndpoint", s.optString("endpoint", "https://api.openai.com/v1/chat/completions"))
                .putString("dg_aiModel", s.optString("model", "gpt-4o-mini"))
                .putString("dg_aiKey", s.optString("key", ""))
                .putString("dg_aiProvider", s.optString("provider", ""))
                .putBoolean("dg_aiReasoning", s.optBoolean("reasoning", false))
                .putInt("dg_aiServiceSel", index)
                .apply();
    }

    // Разовая миграция: если сервисов нет, но старый ключ задан — создаём один сервис из легаси-настроек.
    private void seedIfNeeded() {
        JSONArray arr = services();
        if (arr.length() > 0) return;
        String key = MessagesController.getGlobalMainSettings().getString("dg_aiKey", "");
        if (key.trim().isEmpty()) return;
        try {
            JSONObject s = new JSONObject()
                    .put("name", "OpenAI")
                    .put("provider", "OpenAI")
                    .put("endpoint", MessagesController.getGlobalMainSettings().getString("dg_aiEndpoint", "https://api.openai.com/v1/chat/completions"))
                    .put("model", MessagesController.getGlobalMainSettings().getString("dg_aiModel", "gpt-4o-mini"))
                    .put("key", key)
                    .put("reasoning", false);
            arr.put(s);
            MessagesController.getGlobalMainSettings().edit()
                    .putString("dg_aiServices", arr.toString())
                    .putInt("dg_aiServiceSel", 0)
                    .apply();
        } catch (Throwable ignore) {}
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Сервисы");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        seedIfNeeded();

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        return fragmentView = content;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView("Сервисы", "Выберите подходящий сервис для взаимодействия с языковыми моделями.",
                "RestrictedEmoji", "🔑"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Сервисы"));
        JSONArray arr = services();
        int sel = selected();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;
            items.add(UItem.asRadio(ID_BASE + i, s.optString("name", s.optString("provider", "Сервис")), s.optString("model"))
                    .setChecked(i == sel));
        }
        items.add(UItem.asButton(ID_NEW, R.drawable.msg_add, "Новый сервис").accent());
        items.add(UItem.asShadow("Долгое нажатие на сервис — изменить или удалить."));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_NEW) {
            presentFragment(new DevGramAiServiceEditActivity(-1));
        } else if (item.id >= ID_BASE) {
            int i = item.id - ID_BASE;
            applyActive(i);
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        }
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < ID_BASE || getParentActivity() == null) {
            return false;
        }
        final int i = item.id - ID_BASE;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setItems(new CharSequence[]{"Изменить", "Удалить"}, (d, which) -> {
            if (which == 0) {
                presentFragment(new DevGramAiServiceEditActivity(i));
            } else {
                JSONArray arr = services();
                arr.remove(i);
                int sel = Math.max(0, Math.min(selected(), arr.length() - 1));
                MessagesController.getGlobalMainSettings().edit()
                        .putString("dg_aiServices", arr.toString()).putInt("dg_aiServiceSel", sel).apply();
                if (arr.length() > 0) applyActive(sel);
                else MessagesController.getGlobalMainSettings().edit().putString("dg_aiKey", "").apply();
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
            }
        });
        showDialog(b.create());
        return true;
    }
}

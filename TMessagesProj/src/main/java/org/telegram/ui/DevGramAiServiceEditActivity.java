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
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Редактор сервиса ИИ (повтор EditServiceActivity exteraGram): провайдер + inline поля + Проверить и сохранить. */
public class DevGramAiServiceEditActivity extends DevGramSettingsFragment {
    private static final int ID_PROVIDER_BASE = 10;
    private static final int ID_REASONING = 23;
    private static final int ID_VERIFY = 24;
    private static final int ID_DELETE = 25;

    // {label, endpoint-пресет, model-пресет}. «Свой» — без пресета.
    private static final String[][] PROVIDERS = {
            {"Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.0-flash"},
            {"OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"},
            {"OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "openai/gpt-4o-mini"},
            {"Свой", "", ""},
    };

    private int editIndex; // -1 = новый сервис; после сохранения указывает на созданную запись
    private String provider = "OpenAI";
    private String initEndpoint = "https://api.openai.com/v1/chat/completions";
    private String initModel = "gpt-4o-mini";
    private String initKey = "";
    private boolean reasoning = false;

    private EditTextCell urlCell, modelCell, keyCell;
    private UniversalRecyclerView listView;

    public DevGramAiServiceEditActivity(int editIndex) {
        this.editIndex = editIndex;
        if (editIndex >= 0) {
            JSONObject s = DevGramAiServicesActivity.services().optJSONObject(editIndex);
            if (s != null) {
                provider = s.optString("provider", provider);
                initEndpoint = s.optString("endpoint", initEndpoint);
                initModel = s.optString("model", initModel);
                initKey = s.optString("key", initKey);
                reasoning = s.optBoolean("reasoning", false);
            }
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(editIndex >= 0 ? "Изменить сервис" : "Новый сервис");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        urlCell = new EditTextCell(context, "Endpoint", false);
        urlCell.setText(initEndpoint);
        modelCell = new EditTextCell(context, "Модель", false);
        modelCell.setText(initModel);
        keyCell = new EditTextCell(context, "API-ключ", false);
        keyCell.setText(initKey);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Провайдер"));
        for (int i = 0; i < PROVIDERS.length; i++) {
            items.add(UItem.asRadio(ID_PROVIDER_BASE + i, PROVIDERS[i][0]).setChecked(provider.equals(PROVIDERS[i][0])));
        }
        items.add(UItem.asShadow("Выберите подходящий сервис для взаимодействия с языковыми моделями."));

        items.add(UItem.asHeader("Данные сервиса"));
        items.add(UItem.asCustom(urlCell));
        items.add(UItem.asCustom(modelCell));
        items.add(UItem.asCustom(keyCell));
        UItem reas = UItem.asCheck(ID_REASONING, "Рассуждения").setChecked(reasoning).setMultiline(true);
        reas.subtext = "Позволяет поддерживаемым моделям этого сервиса тратить больше токенов на обдумывание ответа.";
        items.add(reas);
        items.add(UItem.asShadow(null));

        items.add(UItem.asButton(ID_VERIFY, R.drawable.ic_ab_done, "Проверить и сохранить").accent());
        if (editIndex >= 0) {
            items.add(UItem.asButton(ID_DELETE, R.drawable.msg_delete, "Удалить").red());
        }
        items.add(UItem.asShadow(null));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_PROVIDER_BASE && item.id < ID_REASONING) {
            int i = item.id - ID_PROVIDER_BASE;
            provider = PROVIDERS[i][0];
            if (!PROVIDERS[i][1].isEmpty()) urlCell.setText(PROVIDERS[i][1]);
            if (!PROVIDERS[i][2].isEmpty()) modelCell.setText(PROVIDERS[i][2]);
            update();
        } else if (item.id == ID_REASONING) {
            reasoning = !reasoning;
            update();
        } else if (item.id == ID_VERIFY) {
            verifyAndSave();
        } else if (item.id == ID_DELETE) {
            confirmDelete();
        }
    }

    private void update() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }

    private String endpoint() { return urlCell.getText().toString().trim(); }
    private String model() { return modelCell.getText().toString().trim(); }
    private String key() { return keyCell.getText().toString().trim(); }

    // «Проверить и сохранить» (как ServiceTestAndSave у exteraGram): тестовый запрос → сохранение при успехе.
    private void verifyAndSave() {
        if (getParentActivity() == null) return;
        if (key().isEmpty()) {
            android.widget.Toast.makeText(getParentActivity(), "Укажите API-ключ", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        showDialog(progress);
        org.telegram.messenger.DevGramAiClient.test(endpoint(), model(), key(), (result, error) -> {
            try { progress.dismiss(); } catch (Throwable ignore) {}
            if (getParentActivity() == null) return;
            if (error == null) {
                save();
                finishFragment();
                return;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle("Сервис не отвечает");
            b.setMessage(String.valueOf(error.getMessage()));
            b.setPositiveButton("Сохранить всё равно", (d, w) -> { save(); finishFragment(); });
            b.setNegativeButton("Отмена", null);
            showDialog(b.create());
        });
    }

    private void confirmDelete() {
        if (getParentActivity() == null || editIndex < 0) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Удалить сервис?");
        b.setMessage(provider);
        b.setPositiveButton("Удалить", (d, w) -> {
            JSONArray arr = DevGramAiServicesActivity.services();
            if (editIndex < arr.length()) arr.remove(editIndex);
            int sel = Math.max(0, Math.min(MessagesController.getGlobalMainSettings().getInt("dg_aiServiceSel", 0), arr.length() - 1));
            MessagesController.getGlobalMainSettings().edit()
                    .putString("dg_aiServices", arr.toString()).putInt("dg_aiServiceSel", sel).apply();
            if (arr.length() > 0) DevGramAiServicesActivity.applyActive(sel);
            else MessagesController.getGlobalMainSettings().edit().putString("dg_aiKey", "").apply();
            finishFragment();
        });
        b.setNegativeButton("Отмена", null);
        showDialog(b.create());
    }

    private void save() {
        JSONArray arr = DevGramAiServicesActivity.services();
        try {
            JSONObject s = new JSONObject()
                    .put("name", provider)
                    .put("provider", provider)
                    .put("endpoint", endpoint())
                    .put("model", model())
                    .put("key", key())
                    .put("reasoning", reasoning);
            int selIndex;
            if (editIndex >= 0 && editIndex < arr.length()) {
                arr.put(editIndex, s);
                selIndex = editIndex;
            } else {
                arr.put(s);
                selIndex = arr.length() - 1;
            }
            MessagesController.getGlobalMainSettings().edit()
                    .putString("dg_aiServices", arr.toString())
                    .putInt("dg_aiServiceSel", selIndex)
                    .apply();
            DevGramAiServicesActivity.applyActive(selIndex);
            editIndex = selIndex;
        } catch (Throwable ignore) {}
    }
}

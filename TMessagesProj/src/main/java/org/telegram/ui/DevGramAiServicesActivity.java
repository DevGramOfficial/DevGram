package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramGeminiNano;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Сервисы ИИ (повтор ServicesActivity exteraGram): список сервисов + «Новый сервис». Выбранный = активный. */
public class DevGramAiServicesActivity extends DevGramSettingsFragment {
    private static final int ID_NEW = 1;
    private static final int ID_NANO = 2;
    private static final int ID_BASE = 100;

    private UniversalRecyclerView listView;
    private final Runnable statusPoll = new Runnable() {
        @Override public void run() {
            if (getContext() == null) return;
            refreshNano();
            if (DevGramGeminiNano.getStatus() == DevGramGeminiNano.STATUS_DOWNLOADING) {
                AndroidUtilities.runOnUIThread(this, 5000);
            }
        }
    };

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
                .putBoolean("dg_aiUseNano", false)
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
        DevGramGeminiNano.refresh(() -> {
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        });
        return fragmentView = content;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        AndroidUtilities.cancelRunOnUIThread(statusPoll);
        AndroidUtilities.runOnUIThread(statusPoll, 5000);
    }

    @Override
    public void onPause() {
        AndroidUtilities.cancelRunOnUIThread(statusPoll);
        super.onPause();
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(statusPoll);
        super.onFragmentDestroy();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView("Сервисы", "Выберите подходящий сервис для взаимодействия с языковыми моделями.",
                "RestrictedEmoji", "🔑"));
        items.add(UItem.asHeader("Сервисы"));
        UItem nano = UItem.asRadio2(ID_NANO, org.telegram.messenger.DevGramNewBadge.mark("Gemini Nano", org.telegram.messenger.DevGramNewBadge.GEMINI_NANO), DevGramGeminiNano.statusText())
                .setChecked(DevGramGeminiNano.isSelected());
        nano.multiline = true;
        items.add(nano);
        JSONArray arr = services();
        int sel = selected();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;
            items.add(UItem.asRadio2(ID_BASE + i, s.optString("name", s.optString("provider", "Сервис")), s.optString("model"))
                    .setChecked(!DevGramGeminiNano.isSelected() && i == sel));
        }
        UItem add = UItem.asButton(ID_NEW, R.drawable.msg_add, "Новый сервис").accent();
        add.pad = 61;
        items.add(add);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_NEW) {
            presentFragment(new DevGramAiServiceEditActivity(-1));
        } else if (item.id == ID_NANO) {
            int status = DevGramGeminiNano.getStatus();
            if (status == DevGramGeminiNano.STATUS_AVAILABLE) {
                MessagesController.getGlobalMainSettings().edit().putBoolean("dg_aiUseNano", true).apply();
                listView.adapter.update(true);
            } else if (status == DevGramGeminiNano.STATUS_DOWNLOADABLE) {
                confirmNanoDownload();
            } else if (status == DevGramGeminiNano.STATUS_UNKNOWN) {
                refreshNano();
            } else if (status == DevGramGeminiNano.STATUS_UNAVAILABLE) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle("Gemini Nano недоступен");
                builder.setMessage("AICore не предоставил локальную модель для этого устройства или выбранного варианта.");
                builder.setNegativeButton("Закрыть", null);
                builder.setPositiveButton("Проверить", (dialog, which) -> refreshNano());
                showDialog(builder.create());
            }
        } else if (item.id >= ID_BASE) {
            int i = item.id - ID_BASE;
            applyActive(i);
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        }
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_NANO) {
            ItemOptions options = ItemOptions.makeOptions(this, view);
            boolean preview = MessagesController.getGlobalMainSettings().getBoolean("dg_aiNanoPreview", false);
            boolean fast = MessagesController.getGlobalMainSettings().getBoolean("dg_aiNanoFast", true);
            options.addChecked(preview, "Preview-модель", () -> setNanoOption("dg_aiNanoPreview", !preview));
            options.addChecked(fast, "Быстрая модель", () -> setNanoOption("dg_aiNanoFast", !fast));
            if (DevGramGeminiNano.isThinkingModeAvailable()) {
                boolean reasoning = MessagesController.getGlobalMainSettings()
                        .getBoolean("dg_aiNanoReasoning", false);
                options.addChecked(reasoning, "Рассуждение", () -> {
                    MessagesController.getGlobalMainSettings().edit()
                            .putBoolean("dg_aiNanoReasoning", !reasoning).apply();
                    listView.adapter.update(true);
                });
            }
            options.addGap();
            options.add(R.drawable.msg_retry, "Обновить статус", this::refreshNano);
            options.add(R.drawable.msg_settings, "Управление AICore", this::openAiCoreSettings);
            options.setScrimViewBackground(listView.getClipBackground(view));
            options.show();
            return true;
        }
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

    private void setNanoOption(String key, boolean value) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(key, value).apply();
        DevGramGeminiNano.configurationChanged(() -> {
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        });
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }

    private void refreshNano() {
        DevGramGeminiNano.refresh(() -> {
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        });
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }

    private void confirmNanoDownload() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Загрузить Gemini Nano?");
        builder.setMessage("Модель загрузится один раз через Android AICore. После этого запросы будут обрабатываться прямо на устройстве.");
        builder.setNegativeButton("Отмена", null);
        builder.setPositiveButton("Загрузить", (dialog, which) -> {
            DevGramGeminiNano.download(new DevGramGeminiNano.DownloadListener() {
                @Override public void onStarted(long bytes) { updateNanoRow(); }
                @Override public void onProgress(long downloaded, int percent) { updateNanoRow(); }
                @Override public void onCompleted() {
                    MessagesController.getGlobalMainSettings().edit().putBoolean("dg_aiUseNano", true).apply();
                    DevGramGeminiNano.refresh(this::updateAfterRefresh);
                    updateNanoRow();
                }
                private void updateAfterRefresh() { updateNanoRow(); }
                @Override public void onFailed(Throwable error) {
                    updateNanoRow();
                    if (getParentActivity() == null) return;
                    AlertDialog.Builder failed = new AlertDialog.Builder(getParentActivity());
                    failed.setTitle("Не удалось загрузить модель");
                    failed.setMessage(error != null && error.getMessage() != null
                            ? error.getMessage() : "AICore вернул ошибку загрузки.");
                    failed.setPositiveButton("Закрыть", null);
                    showDialog(failed.create());
                }
            });
            updateNanoRow();
        });
        showDialog(builder.create());
    }

    private void updateNanoRow() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }

    private void openAiCoreSettings() {
        if (getParentActivity() == null) return;
        try {
            Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS")
                    .setData(Uri.parse("package:com.google.android.aicore"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getParentActivity().startActivity(intent);
        } catch (Throwable ignore) {}
    }
}

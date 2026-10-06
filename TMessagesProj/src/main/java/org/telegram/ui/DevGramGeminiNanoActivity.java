package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import org.telegram.messenger.DevGramGeminiNano;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Components.DevGramSettingsLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Configuration and one-time download screen for Gemini Nano. */
public class DevGramGeminiNanoActivity extends DevGramSettingsFragment {
    private static final int ID_ENABLE = 1;
    private static final int ID_PREVIEW = 2;
    private static final int ID_FAST = 3;
    private static final int ID_DOWNLOAD = 4;
    private static final int ID_REFRESH = 5;
    private static final int ID_REASONING = 6;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Gemini Nano");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        DevGramSettingsLayout content = new DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        DevGramGeminiNano.refresh(this::refresh);
        return fragmentView = content;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean selected = DevGramGeminiNano.isSelected();
        boolean preview = MessagesController.getGlobalMainSettings().getBoolean("dg_aiNanoPreview", false);
        boolean fast = MessagesController.getGlobalMainSettings().getBoolean("dg_aiNanoFast", true);
        boolean reasoning = MessagesController.getGlobalMainSettings().getBoolean("dg_aiNanoReasoning", false);
        int status = DevGramGeminiNano.getStatus();

        items.add(UItem.asTopView("Gemini Nano",
                "Модель работает прямо на устройстве. Текст запросов и ответов не отправляется на сервер.",
                "RestrictedEmoji", "✨"));
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(ID_ENABLE, "Использовать Gemini Nano").setChecked(selected));
        items.add(UItem.asShadow("Модель загружается один раз через системный сервис Android AICore."));

        items.add(UItem.asHeader("Версия модели"));
        items.add(UItem.asCheck(ID_PREVIEW, "Preview-модель").setChecked(preview).setMultiline(true));
        items.get(items.size() - 1).subtext = preview
                ? "Новые возможности, но поведение может меняться."
                : "Стабильная версия модели.";
        items.add(UItem.asCheck(ID_FAST, "Быстрая модель").setChecked(fast).setMultiline(true));
        items.get(items.size() - 1).subtext = fast
                ? "Быстрее отвечает и расходует меньше ресурсов."
                : "Полная модель с приоритетом качества.";
        if (DevGramGeminiNano.isThinkingModeAvailable()) {
            items.add(UItem.asCheck(ID_REASONING, "Режим рассуждения").setChecked(reasoning).setMultiline(true));
            items.get(items.size() - 1).subtext = "Модель тратит больше времени на сложные ответы.";
        }
        items.add(UItem.asShadow("Доступность и загрузка проверяются отдельно для каждой комбинации."));

        items.add(UItem.asHeader("Состояние"));
        if (status == DevGramGeminiNano.STATUS_DOWNLOADABLE) {
            items.add(UItem.asButton(ID_DOWNLOAD, R.drawable.msg_download, "Загрузить модель",
                    DevGramGeminiNano.statusText()).accent());
        } else if (status == DevGramGeminiNano.STATUS_DOWNLOADING) {
            UItem row = UItem.asButton(ID_DOWNLOAD, R.drawable.msg_download, "Модель загружается",
                    DevGramGeminiNano.statusText());
            row.enabled = false;
            items.add(row);
        } else {
            items.add(UItem.asButton(ID_REFRESH, R.drawable.msg_retry, "Проверить доступность",
                    DevGramGeminiNano.statusText()));
        }
        if (status == DevGramGeminiNano.STATUS_AVAILABLE) {
            if (!DevGramGeminiNano.getBaseModelName().isEmpty()) {
                items.add(UItem.asButton(0, "Базовая модель", DevGramGeminiNano.getBaseModelName()));
            }
            if (DevGramGeminiNano.getTokenLimit() > 0) {
                items.add(UItem.asButton(0, "Лимит контекста",
                        DevGramGeminiNano.getTokenLimit() + " токенов"));
            }
        }
        items.add(UItem.asShadow("Gemini Nano поддерживается только частью устройств Android 8.0+ с AICore."));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ENABLE) {
            if (DevGramGeminiNano.getStatus() == DevGramGeminiNano.STATUS_UNAVAILABLE) {
                Toast.makeText(getParentActivity(), "Gemini Nano недоступен на этом устройстве", Toast.LENGTH_LONG).show();
                return;
            }
            MessagesController.getGlobalMainSettings().edit()
                    .putBoolean("dg_aiUseNano", !DevGramGeminiNano.isSelected()).apply();
            refresh();
        } else if (item.id == ID_PREVIEW || item.id == ID_FAST) {
            String key = item.id == ID_PREVIEW ? "dg_aiNanoPreview" : "dg_aiNanoFast";
            boolean old = MessagesController.getGlobalMainSettings().getBoolean(key, item.id == ID_FAST);
            MessagesController.getGlobalMainSettings().edit().putBoolean(key, !old).apply();
            DevGramGeminiNano.configurationChanged(this::refresh);
            refresh();
        } else if (item.id == ID_REASONING) {
            boolean old = MessagesController.getGlobalMainSettings()
                    .getBoolean("dg_aiNanoReasoning", false);
            MessagesController.getGlobalMainSettings().edit()
                    .putBoolean("dg_aiNanoReasoning", !old).apply();
            refresh();
        } else if (item.id == ID_REFRESH) {
            DevGramGeminiNano.refresh(this::refresh);
            refresh();
        } else if (item.id == ID_DOWNLOAD) {
            DevGramGeminiNano.download(new DevGramGeminiNano.DownloadListener() {
                @Override public void onStarted(long bytes) { refresh(); }
                @Override public void onProgress(long downloaded, int percent) { refresh(); }
                @Override public void onCompleted() {
                    MessagesController.getGlobalMainSettings().edit().putBoolean("dg_aiUseNano", true).apply();
                    Toast.makeText(getParentActivity(), "Gemini Nano готов к работе", Toast.LENGTH_SHORT).show();
                    refresh();
                }
                @Override public void onFailed(Throwable error) {
                    Toast.makeText(getParentActivity(), error.getMessage() == null ? "Не удалось загрузить модель" : error.getMessage(), Toast.LENGTH_LONG).show();
                    refresh();
                }
            });
            refresh();
        }
    }
}

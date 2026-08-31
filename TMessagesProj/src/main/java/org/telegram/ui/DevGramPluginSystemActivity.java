/*
 * DevGram: «Система плагинов» — настройки плагин-системы (кнопка «i» из списка плагинов).
 * Только реально работающие опции.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class DevGramPluginSystemActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    private static final int ID_SAFE_MODE = 1;
    private static final int ID_COMPACT = 2;
    private static final int ID_DEV_MODE = 3;
    private static final int ID_RELOAD = 10;
    private static final int ID_CRASH = 11;
    private static final int ID_LOGS = 12;
    private static final int ID_DOCS = 20;
    private static final int ID_VERIFIED = 21;
    private static final int ID_DEV_TOKEN = 22;

    private static final String DOCS_URL = "https://docs.devgram.space";
    private static final String VERIFIED_URL = "https://t.me/addlist/IUMDGoyECehlN2Fi";

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Система плагинов");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout content = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Настройки"));
        items.add(UItem.asCheck(ID_SAFE_MODE, "Безопасный режим").setChecked(DevGramPlugins.isSafeMode()));
        items.add(UItem.asShadow("Все плагины неактивны — так можно найти и отключить проблемный плагин."));
        items.add(UItem.asCheck(ID_COMPACT, "Компактный вид").setChecked(DevGramPlugins.flag("compact_view", false)));
        items.add(UItem.asShadow("Скрывает описание в карточках — список короче."));
        items.add(UItem.asCheck(ID_DEV_MODE, "Режим разработчика").setChecked(DevGramPlugins.flag("dev_mode", false)));
        items.add(UItem.asShadow("Показывает имя файла плагина в списке."));

        items.add(UItem.asHeader("Python"));
        items.add(UItem.asShadow("Встроенный Python: v" + DevGramPlugins.pythonVersion() + " (в составе приложения)."));
        items.add(UItem.asButton(ID_RELOAD, R.drawable.msg_reset, "Перезагрузить плагины"));
        items.add(UItem.asShadow("Перечитывает папку с плагинами без перезапуска приложения."));
        items.add(UItem.asButton(ID_DEV_TOKEN, R.drawable.msg_info, "Токен dev server", "Нужен утилите загрузки .dgplugin"));

        items.add(UItem.asHeader("Диагностика"));
        items.add(UItem.asButton(ID_LOGS, R.drawable.msg_info, "Логи плагинов"));
        items.add(UItem.asShadow("Общий журнал: загрузка и включение плагинов, ошибки хуков."));
        items.add(UItem.asButton(ID_CRASH, R.drawable.msg_info, "Показать отчёт о сбое"));
        items.add(UItem.asShadow("Полный лог последнего падения (устройство, стек, logcat). Сохранён в файле devgram_crash.txt."));

        items.add(UItem.asHeader("Ссылки"));
        items.add(UItem.asButton(ID_DOCS, R.drawable.msg_info, "Документация"));
        items.add(UItem.asButton(ID_VERIFIED, R.drawable.devgram_plugins, "Проверенные плагины", "Папка Telegram"));
        items.add(UItem.asShadow(null));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SAFE_MODE) {
            boolean safeMode = !DevGramPlugins.isSafeMode();
            DevGramPlugins.setFlag("safe_mode", safeMode);
            DevGramPlugins.logLine(safeMode ? "включён безопасный режим" : "выключен безопасный режим");
            if (safeMode) {
                DevGramPlugins.setDevServerEnabled(false);
            } else {
                int n = DevGramPlugins.reload();
                if (DevGramPlugins.flag("dev_mode", false)) {
                    DevGramPlugins.setDevServerEnabled(true);
                }
                BulletinFactory.of(this).createSimpleBulletin(
                        R.raw.contact_check,
                        "Безопасный режим выключен. Загружено плагинов: " + n
                ).show();
            }
        } else if (item.id == ID_COMPACT) {
            DevGramPlugins.setFlag("compact_view", !DevGramPlugins.flag("compact_view", false));
        } else if (item.id == ID_DEV_MODE) {
            boolean enabled = !DevGramPlugins.flag("dev_mode", false);
            DevGramPlugins.setFlag("dev_mode", enabled);
            DevGramPlugins.logLine(enabled ? "включён режим разработчика" : "выключен режим разработчика");
            if (enabled) {
                DevGramPlugins.setDevServerEnabled(true);
            } else {
                DevGramPlugins.setDevServerEnabled(false);
            }
        } else if (item.id == ID_DEV_TOKEN) {
            showDevServerTokenDialog();
            return;
        } else if (item.id == ID_RELOAD) {
            int n = DevGramPlugins.reload();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Перезагружено плагинов: " + n).show();
            return;
        } else if (item.id == ID_LOGS) {
            DevGramPlugins.showPluginLogs();
            return;
        } else if (item.id == ID_CRASH) {
            DevGramPlugins.showLastCrashReport();
            return;
        } else if (item.id == ID_DOCS) {
            Browser.openUrl(getContext(), DOCS_URL);
            return;
        } else if (item.id == ID_VERIFIED) {
            Browser.openUrl(getContext(), VERIFIED_URL);
            return;
        } else {
            return;
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void showDevServerTokenDialog() {
        String connectionInfo = "Порт: " + DevGramPlugins.DEV_SERVER_PORT
                + "\nТокен: " + DevGramPlugins.devServerToken();
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                .setTitle("Токен dev server")
                .setMessage(connectionInfo + "\n\nНе передавайте токен другим приложениям.")
                .setNeutralButton("Сбросить", (dialog, which) -> confirmDevServerTokenReset())
                .setNegativeButton("Скопировать", (dialog, which) -> {
                    org.telegram.messenger.AndroidUtilities.addToClipboard(connectionInfo);
                    BulletinFactory.of(this).createCopyBulletin("Порт и токен скопированы").show();
                })
                .setPositiveButton("Готово", null)
                .show();
    }

    private void confirmDevServerTokenReset() {
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                .setTitle("Сбросить токен?")
                .setMessage("Старый токен сразу перестанет работать. Dev server перезапустится с новым токеном.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сбросить", (dialog, which) -> {
                    DevGramPlugins.resetDevServerToken();
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Создан новый токен").show();
                        showDevServerTokenDialog();
                    }, 160);
                })
                .show();
    }
}

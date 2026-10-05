package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
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

// DevGram: раздел «Другое» — поддержка проекта, сбор данных и сервисные действия (экспорт/импорт/сброс,
// удаление аккаунта). Открывается кнопкой «Другое» из главного экрана настроек DevGram.
public class DevGramOtherActivity extends BaseFragment {

    private static final int ID_SUPPORT_TON = 3;
    private static final int ID_CARD_BASE = 9000; // динамические карты: ID_CARD_BASE + индекс
    private static final int ID_CRASHLYTICS = 10;
    private static final int ID_ANALYTICS = 11;
    private static final int ID_CRASH_TEST = 12;
    private static final int ID_EXPORT = 20;
    private static final int ID_IMPORT = 21;
    private static final int ID_RESET = 22;
    private static final int ID_DELETE_ACCOUNT = 23;
    private static final int ID_CLEANUP_ACCOUNT = 24;
    private static final int REQ_IMPORT = 9021;

    private static final String CARD_VTB = "2200248804848272";
    private static final String CARD_SBER = "2202206831130013";
    private static final String CARD_ALFA = "2200150522613204";
    private static final String CARD_TBANK = "2200396111123788";
    private static final String TON_ADDRESS = "UQD9m9PQ5BfQa_sAA09HqJ2WLfmFDoyPxsZ8kSEyD-lXMYsx";
    private static final String LINK_TONKEEPER = "https://app.tonkeeper.com/transfer/" + TON_ADDRESS;

    private UniversalRecyclerView listView;
    private java.util.List<org.telegram.messenger.DevGramRequisites.Card> supportCards = new java.util.ArrayList<>();
    private String supportTon = "";

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Другое");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);

        DevGramSettingsLink.consumeHighlight(listView);
        // Подтягиваем свежие реквизиты с сервера и обновляем список при ответе.
        org.telegram.messenger.DevGramRequisites.refresh(this::refreshList);
        return fragmentView = contentView;
    }

    // DevGram: зажатие пункта → «копировать / поделиться ссылкой»
    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item == null || item.id <= 0) return false;
        return DevGramSettingsLink.showLinkOptions(this, view, "other", item.id);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context ctx = getContext();

        // Поддержка — реквизиты; серая подпись снизу кликабельна и открывает лист «Поддержать DevGram».
        items.add(UItem.asHeader("Поддержка"));
        boolean dark = resourceProvider != null ? resourceProvider.isDark() : Theme.isCurrentThemeDark();
        // Реквизиты берутся с сервера (api.devgram.space), владелец меняет их через бота.
        org.telegram.messenger.DevGramRequisites.Data req = org.telegram.messenger.DevGramRequisites.load();
        supportCards = req.cards;
        supportTon = req.tonkeeper;
        items.add(bankButton(ctx, ID_SUPPORT_TON, dark ? R.drawable.devgram_ton_dark : R.drawable.devgram_ton_light,
                "Tonkeeper", null));
        for (int i = 0; i < supportCards.size(); i++) {
            org.telegram.messenger.DevGramRequisites.Card c = supportCards.get(i);
            items.add(bankButton(ctx, ID_CARD_BASE + i,
                    org.telegram.messenger.DevGramRequisites.bankIcon(c.bank, dark),
                    c.bank, formatCard(c.number), c.logo));
        }
        items.add(UItem.asShadow(supportCaption()));

        // Сбор данных
        items.add(UItem.asHeader("Сбор данных"));
        items.add(UItem.asCheck(ID_CRASHLYTICS, "Отчёты о сбоях").setChecked(DevGramConfig.crashlyticsEnabled));
        items.add(UItem.asCheck(ID_ANALYTICS, "Аналитика").setChecked(DevGramConfig.analyticsEnabled));
        if (DevGramConfig.crashlyticsEnabled) {
            items.add(UItem.asButton(ID_CRASH_TEST, R.drawable.msg_report, "Проверить отчёт о сбое"));
        }
        items.add(UItem.asShadow("Анонимные данные (версия, модель устройства, факт запуска, стек-трейсы "
                + "сбоев) отправляются в базу DevGram, чтобы мы видели статистику и чинили ошибки. Без "
                + "Google-SDK и без привязки к аккаунту. Отчёты о сбоях включены по умолчанию, аналитика "
                + "— выключена. Любой тумблер можно отключить."));

        // Настройки мода
        items.add(UItem.asHeader("Настройки"));
        items.add(UItem.asButton(ID_EXPORT, R.drawable.msg_share, "Экспортировать настройки"));
        items.add(UItem.asButton(ID_IMPORT, R.drawable.msg_download, "Импортировать настройки"));
        items.add(UItem.asButton(ID_RESET, R.drawable.msg_reset, "Сбросить настройки"));
        items.add(UItem.asShadow("Экспорт отправит файл .devgram выбранному чату. Импорт покажет отличия и "
                + "предложит применить."));
        items.add(UItem.asButton(ID_CLEANUP_ACCOUNT, R.drawable.msg_clearcache, "Очистить аккаунт").red());
        items.add(UItem.asButton(ID_DELETE_ACCOUNT, R.drawable.msg_delete, "Удалить аккаунт").red());
        items.add(UItem.asShadow("«Очистить аккаунт» удаляет контакты, чаты и группы, но сам аккаунт "
                + "Telegram остаётся — им можно пользоваться дальше."));
    }

    // Серая подпись под реквизитами: часть «Поддержите разработку» кликабельна (цвет как у всего
    // текста подписи, без выделения), открывает лист «Поддержать DevGram».
    private CharSequence supportCaption() {
        String full = "Поддержи DevGram донатом и забери эксклюзивный значок в профиль.";
        String link = "Поддержи DevGram донатом";
        android.text.SpannableString sp = new android.text.SpannableString(full);
        sp.setSpan(new android.text.style.ClickableSpan() {
            @Override
            public void onClick(View widget) {
                DevGramSupportSheet.show(DevGramOtherActivity.this);
            }

            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                ds.setUnderlineText(true);
                ds.setTypeface(AndroidUtilities.bold());
            }
        }, 0, link.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SUPPORT_TON) {
            openTonkeeper();
        } else if (item.id >= ID_CARD_BASE && item.id < ID_CARD_BASE + 100) {
            int idx = item.id - ID_CARD_BASE;
            if (idx >= 0 && idx < supportCards.size()) {
                org.telegram.messenger.DevGramRequisites.Card c = supportCards.get(idx);
                copyCard(c.bank, c.number);
            }
        } else if (item.id == ID_CRASHLYTICS) {
            DevGramConfig.setCrashlyticsEnabled(!DevGramConfig.crashlyticsEnabled);
            refreshList();
        } else if (item.id == ID_CRASH_TEST) {
            showCrashTestDialog();
        } else if (item.id == ID_ANALYTICS) {
            DevGramConfig.setAnalyticsEnabled(!DevGramConfig.analyticsEnabled);
            refreshList();
        } else if (item.id == ID_EXPORT) {
            exportSettings();
        } else if (item.id == ID_IMPORT) {
            importSettings();
        } else if (item.id == ID_RESET) {
            confirmReset();
        } else if (item.id == ID_DELETE_ACCOUNT) {
            confirmDeleteAccount();
        } else if (item.id == ID_CLEANUP_ACCOUNT) {
            DevGramAccountCleanupSheet.show(this);
        }
    }

    private static String formatCard(String card) {
        return card.substring(0, 4) + " " + card.substring(4, 8) + " "
                + card.substring(8, 12) + " " + card.substring(12, 16);
    }

    private UItem bankButton(Context context, int id, int icon, CharSequence text, CharSequence value) {
        return bankButton(context, id, icon, text, value, null);
    }

    private UItem bankButton(Context context, int id, int icon, CharSequence text, CharSequence value, String logoUrl) {
        android.graphics.drawable.Drawable drawable = context != null ? context.getDrawable(icon) : null;
        UItem item = value == null ? UItem.asButton(id, drawable, text) : UItem.asButton(id, drawable, text, value);
        return item.onBind(view -> {
            if (view instanceof org.telegram.ui.Cells.TextCell) {
                org.telegram.ui.Cells.TextCell cell = (org.telegram.ui.Cells.TextCell) view;
                // UniversalAdapter вызывает setColors после установки Drawable; очищаем его фильтр последними.
                cell.getImageView().setColorFilter(null);
                cell.getImageView().setTag(null);
                // Логотип нового банка с сервера (если задан) грузим поверх generic-иконки.
                if (logoUrl != null && !logoUrl.isEmpty()) {
                    org.telegram.messenger.DevGramRequisites.applyLogo(cell.getImageView(), logoUrl);
                }
                // Как в CherryGram: дополнительный воздух между карточкой и названием.
                cell.setOffsetFromImage(66);
            }
        });
    }

    private void copyCard(String bank, String card) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(bank, card));
            BulletinFactory.of(this).createSimpleBulletin(R.raw.copy, "Номер карты скопирован").show();
        }
    }

    private void refreshList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    // Экспорт: обычная пересылка — выбор чата и отправка файла .devgram.
    private void exportSettings() {
        java.io.File f = DevGramSettingsIO.writeExportFile();
        if (f == null) {
            BulletinFactory.of(this).createErrorBulletin("Не удалось подготовить файл").show();
            return;
        }
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment1, dids, m, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids.isEmpty()) {
                return false;
            }
            long did = dids.get(0).dialogId;
            // 12.9.7: 17-арг (…, Uri, String mime, String caption, long dialogId, …, InputContentInfoCompat, SendMessageChatArguments, boolean)
            org.telegram.messenger.SendMessagesHelper.prepareSendingDocument(
                    org.telegram.messenger.AccountInstance.getInstance(currentAccount),
                    f.getAbsolutePath(), f.getAbsolutePath(), null,
                    "application/octet-stream", null, did, null, null, null, null, null,
                    true, 0, null, null, false);
            Bundle a = new Bundle();
            if (did > 0) {
                a.putLong("user_id", did);
            } else {
                a.putLong("chat_id", -did);
            }
            presentFragment(new ChatActivity(a), true);
            return true;
        });
        presentFragment(picker);
    }

    // Импорт: системный выбор файла .devgram → сравнение/применение в DevGramSettingsIO.
    private void importSettings() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "Выбери файл " + DevGramSettingsIO.EXT), REQ_IMPORT);
        } catch (Throwable e) {
            BulletinFactory.of(this).createErrorBulletin("Не удалось открыть выбор файла").show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_IMPORT && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            String content = DevGramSettingsIO.readUri(getContext(), data.getData());
            if (content == null) {
                BulletinFactory.of(this).createErrorBulletin("Не удалось прочитать файл").show();
                return;
            }
            DevGramSettingsIO.handleImport(this, content, this::refreshList);
        }
    }

    private void confirmReset() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Сбросить настройки");
        b.setMessage("Все настройки DevGram вернутся к значениям по умолчанию. Продолжить?");
        b.setPositiveButton("Сбросить", (d, w) -> {
            DevGramConfig.resetToDefaults();
            refreshList();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Настройки сброшены").show();
        });
        b.setNegativeButton("Отмена", null);
        AlertDialog dlg = b.create();
        showDialog(dlg);
        TextView btn = (TextView) dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        if (btn != null) {
            btn.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    // Удаление аккаунта необратимо — открываем официальный экран Telegram (Конфиденциальность).
    private void confirmDeleteAccount() {
        // Реальное удаление аккаунта прямо из клиента (account.deleteAccount + 2FA).
        // Раньше кнопка лишь открывала раздел «Конфиденциальность» (только автоудаление по TTL).
        DevGramDeleteAccount.confirmAndDelete(this);
    }

    // Tonkeeper: сперва пытаемся открыть кошелёк напрямую (ton://transfer), иначе — универсальная ссылка.
    private void openTonkeeper() {
        String addr = supportTon != null && !supportTon.isEmpty()
                ? supportTon
                : org.telegram.messenger.DevGramRequisites.load().tonkeeper;
        android.app.Activity act = getParentActivity();
        if (act != null) {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("ton://transfer/" + addr)));
                return;
            } catch (Throwable ignore) {
            }
        }
        Browser.openUrl(getContext(), "https://app.tonkeeper.com/transfer/" + addr);
    }

    // Проверка всей цепочки отчётов: приложение падает нарочно, при следующем запуске отчёт
    // уходит в RTDB (DevGramExitReport) и приходит в группу (statbot).
    private void showCrashTestDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Проверить отчёт о сбое");
        builder.setMessage("Приложение сразу закроется. Откройте его снова — отчёт отправится автоматически.");
        builder.setItems(new CharSequence[]{
                "Java-исключение",
                "Нативный сбой (SIGSEGV)",
                "Зависание (ANR) — потом коснитесь экрана"
        }, (dialog, which) -> AndroidUtilities.runOnUIThread(() -> {
            if (which == 0) {
                throw new RuntimeException("DevGram: проверка отчёта о сбое");
            } else if (which == 1) {
                android.os.Process.sendSignal(android.os.Process.myPid(), 11);
            } else {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException ignore) {
                }
            }
        }, 300));
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }
}

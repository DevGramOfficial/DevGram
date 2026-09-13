package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

// DevGram: главный экран настроек мода: шапка + категории + ссылки. Поддержка/сбор данных/сервис —
// в отдельном разделе «Другое» (DevGramOtherActivity).
public class DevGramSettingsActivity extends BaseFragment {

    private UniversalRecyclerView listView;
    private View headerView;

    // Категории (каждая открывает свой экран)
    private static final int ID_CAT_GENERAL = 1;
    private static final int ID_CAT_GHOST = 2;
    private static final int ID_CAT_SPY = 3;
    private static final int ID_CAT_APPEARANCE = 7;
    private static final int ID_CAT_CHATS = 8;
    private static final int ID_CAT_FILTERS = 9;
    private static final int ID_BADGES = 4; // выдача значков — только для команды
    private static final int ID_PLUGINS = 5; // менеджер плагинов
    private static final int ID_OTHER = 6;   // раздел «Другое» (поддержка/сбор данных/сервис)
    private static final int ID_LINK_CHANNEL = 10;
    private static final int ID_LINK_CHAT = 11;
    private static final int ID_LINK_DOCS = 12;
    private static final int ID_LINK_SITE = 13;
    private static final int ID_CHECK_UPDATE = 20;   // проверить обновления сейчас
    private static final int ID_UPDATE_INTERVAL = 21; // интервал авто-проверки
    private static final int ID_UPDATE_CHANNEL = 22;  // канал обновлений (основной/бета)

    private static final String LINK_CHANNEL = "https://t.me/devgramnews";
    private static final String LINK_CHAT = "https://t.me/DevGramForum";
    private static final String LINK_DOCS = "https://docs.devgram.space";
    private static final String LINK_SITE = "https://devgram.space";

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setTitle("DevGram");

        headerView = createHeader(context);

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    private View createHeader(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        layout.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18));

        ImageView icon = new ImageView(context);
        try {
            icon.setImageDrawable(context.getDrawable(R.mipmap.ic_launcher));
        } catch (Throwable ignore) {
        }
        layout.addView(icon, LayoutHelper.createLinear(76, 76));

        TextView name = new TextView(context);
        name.setText("DevGram");
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        name.setTypeface(AndroidUtilities.bold());
        layout.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 10f, 0f, 0f));

        TextView ver = new TextView(context);
        ver.setText(getVersionString());
        ver.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        layout.addView(ver, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 4f, 0f, 0f));

        return layout;
    }

    private String getVersionString() {
        try {
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            return pInfo.versionName + " (" + pInfo.versionCode + ")";
        } catch (Throwable e) {
            return "";
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (headerView != null) {
            items.add(UItem.asCustom(headerView));
        }

        // Категории — сами опции живут внутри разделов
        items.add(UItem.asHeader("Категории"));
        items.add(UItem.asButton(ID_CAT_GENERAL, R.drawable.devgram_cat_general, "Основные"));
        items.add(UItem.asButton(ID_CAT_GHOST, R.drawable.devgram_cat_ghost, "Режим призрака"));
        items.add(UItem.asButton(ID_CAT_SPY, R.drawable.devgram_cat_spy, "Слежка"));
        items.add(UItem.asButton(ID_CAT_FILTERS, R.drawable.msg_search, "Фильтры сообщений"));
        items.add(UItem.asButton(ID_CAT_APPEARANCE, R.drawable.msg_photo_settings, "Внешний вид"));
        items.add(UItem.asButton(ID_CAT_CHATS, R.drawable.msg_discussion, "Чаты"));
        items.add(UItem.asButton(ID_PLUGINS, R.drawable.devgram_cat_general, "Плагины"));
        items.add(UItem.asButton(ID_OTHER, R.drawable.msg_settings, "Другое"));
        items.add(UItem.asShadow(null));

        // Обновления DevGram (GitHub-релизы firedragoq/DevGram)
        items.add(UItem.asHeader("Обновления"));
        items.add(UItem.asButton(ID_CHECK_UPDATE, R.drawable.msg_download, "Проверить обновления"));
        items.add(UItem.asButton(ID_UPDATE_INTERVAL, R.drawable.msg_autodelete, "Интервал проверки", getUpdateIntervalText()));
        // Канал обновлений (основной/бета) — виден ТОЛЬКО поддержавшим (значок ✈️)
        if (org.telegram.messenger.DevGramBeta.hasAccess(currentAccount)) {
            items.add(UItem.asButton(ID_UPDATE_CHANNEL, R.drawable.msg_channel,
                    "Канал обновлений",
                    org.telegram.messenger.DevGramConfig.updateChannel == 1 ? "Бета" : "Основной"));
        }
        items.add(UItem.asShadow(null));

        // Ссылки на официальный канал и форум
        items.add(UItem.asHeader("Ссылки"));
        items.add(UItem.asButton(ID_LINK_CHANNEL, R.drawable.devgram_channel, "Канал", "@DevGramNews"));
        items.add(UItem.asButton(ID_LINK_CHAT, R.drawable.devgram_chat, "Форум", "@DevGramForum"));
        items.add(UItem.asButton(ID_LINK_DOCS, R.drawable.msg_info, "Документация", "docs.devgram.space"));
        items.add(UItem.asButton(ID_LINK_SITE, R.drawable.msg_language, "Сайт", "devgram.space"));
        items.add(UItem.asShadow(null));

        // Раздел для команды проекта: выдача значков. Видно только участникам команды.
        if (DevGramBadges.isTeam(getUserConfig().getClientUserId())) {
            items.add(UItem.asHeader("Разработчику"));
            items.add(UItem.asButton(ID_BADGES, R.drawable.devgram_supporter, "Значки DevGram"));
            items.add(UItem.asShadow("Выдача значков пользователям и чатам по ID."));
        }
    }

    // DevGram: зажатие категории/раздела → «копировать / поделиться ссылкой»
    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        String code = null;
        if (item.id == ID_CAT_GENERAL) code = "general";
        else if (item.id == ID_CAT_GHOST) code = "ghost";
        else if (item.id == ID_CAT_SPY) code = "spy";
        else if (item.id == ID_CAT_FILTERS) code = "filters";
        else if (item.id == ID_CAT_APPEARANCE) code = "appearance";
        else if (item.id == ID_CAT_CHATS) code = "chats";
        else if (item.id == ID_OTHER) code = "other";
        if (code == null) return false;
        return DevGramSettingsLink.showLinkOptions(this, view, code, 0);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CAT_GENERAL) {
            presentFragment(new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_GENERAL));
        } else if (item.id == ID_CAT_GHOST) {
            presentFragment(new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_GHOST));
        } else if (item.id == ID_CAT_SPY) {
            presentFragment(new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_SPY));
        } else if (item.id == ID_CAT_FILTERS) {
            presentFragment(new DevGramFiltersActivity());
        } else if (item.id == ID_CAT_APPEARANCE) {
            presentFragment(new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_APPEARANCE));
        } else if (item.id == ID_CAT_CHATS) {
            presentFragment(new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_CHATS));
        } else if (item.id == ID_PLUGINS) {
            presentFragment(new DevGramPluginsActivity());
        } else if (item.id == ID_OTHER) {
            presentFragment(new DevGramOtherActivity());
        } else if (item.id == ID_BADGES) {
            presentFragment(new DevGramBadgesActivity());
        } else if (item.id == ID_LINK_CHANNEL) {
            Browser.openUrl(getContext(), LINK_CHANNEL);
        } else if (item.id == ID_LINK_CHAT) {
            Browser.openUrl(getContext(), LINK_CHAT);
        } else if (item.id == ID_LINK_DOCS) {
            Browser.openUrl(getContext(), LINK_DOCS);
        } else if (item.id == ID_LINK_SITE) {
            Browser.openUrl(getContext(), LINK_SITE);
        } else if (item.id == ID_CHECK_UPDATE) {
            org.telegram.messenger.forkgram.AppUpdater.checkForDevGram(getParentActivity(), getParentActivity(), true);
        } else if (item.id == ID_UPDATE_INTERVAL) {
            showUpdateIntervalDialog();
        } else if (item.id == ID_UPDATE_CHANNEL) {
            toggleUpdateChannel();
        }
    }

    // Переключение основной/бета. Бета включается только после успешной активации токена
    // (клиент сам, в фоне, получает его от бота по значку поддержавшего).
    private void toggleUpdateChannel() {
        if (org.telegram.messenger.DevGramConfig.updateChannel == 1) {
            org.telegram.messenger.DevGramConfig.setUpdateChannel(0);
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
            return;
        }
        if (!org.telegram.messenger.DevGramBeta.hasAccess(currentAccount)) {
            BulletinFactory.of(this).createErrorBulletin("Бета — только для поддержавших (значок ✈️)").show();
            return;
        }
        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Активирую бета-канал…").show();
        org.telegram.messenger.DevGramBeta.activate(currentAccount, (ok, error) -> {
            if (ok) {
                org.telegram.messenger.DevGramConfig.setUpdateChannel(1);
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Бета-канал включён").show();
            } else {
                BulletinFactory.of(this).createErrorBulletin(error != null ? error : "Не удалось активировать бету").show();
            }
        });
    }

    // ---------------------------------------------------------------- Обновления

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    private String getUpdateIntervalText() {
        long interval = prefs().getLong("updateForkCheckInterval", 30 * 60 * 1000);
        if (interval == 0) {
            return LocaleController.getString(R.string.Disable);
        } else if (interval < 60 * 1000) {
            return LocaleController.formatPluralString("Seconds", (int) (interval / 1000));
        } else if (interval < 60 * 60 * 1000) {
            return LocaleController.formatPluralString("Minutes", (int) (interval / (60 * 1000)));
        } else if (interval < 24 * 60 * 60 * 1000) {
            return LocaleController.formatPluralString("Hours", (int) (interval / (60 * 60 * 1000)));
        } else {
            return LocaleController.formatPluralString("Days", (int) (interval / (24 * 60 * 60 * 1000)));
        }
    }

    private void showUpdateIntervalDialog() {
        final long[] intervals = {
            0,
            5 * 60 * 1000L,
            15 * 60 * 1000L,
            30 * 60 * 1000L,
            60 * 60 * 1000L,
            2 * 60 * 60 * 1000L,
            6 * 60 * 60 * 1000L,
            12 * 60 * 60 * 1000L,
            24 * 60 * 60 * 1000L,
            2 * 24 * 60 * 60 * 1000L,
            7 * 24 * 60 * 60 * 1000L
        };
        final String[] options = new String[intervals.length];
        options[0] = LocaleController.getString(R.string.Disable);
        for (int i = 1; i < intervals.length; i++) {
            long interval = intervals[i];
            if (interval < 60 * 60 * 1000L) {
                options[i] = LocaleController.formatPluralString("Minutes", (int) (interval / (60 * 1000L)));
            } else if (interval < 24 * 60 * 60 * 1000L) {
                options[i] = LocaleController.formatPluralString("Hours", (int) (interval / (60 * 60 * 1000L)));
            } else {
                options[i] = LocaleController.formatPluralString("Days", (int) (interval / (24 * 60 * 60 * 1000L)));
            }
        }

        long currentInterval = prefs().getLong("updateForkCheckInterval", 30 * 60 * 1000);
        int selectedIndex = 3;
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i] == currentInterval) {
                selectedIndex = i;
                break;
            }
        }

        showRadioDialog(LocaleController.getString(R.string.UpdateCheckInterval), options, selectedIndex, index -> {
            SharedPreferences.Editor editor = prefs().edit();
            editor.putLong("updateForkCheckInterval", intervals[index]);
            editor.commit();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
    }

    private void showRadioDialog(CharSequence title, String[] options, int selectedIndex, Utilities.Callback<Integer> onSelected) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);

        for (int i = 0; i < options.length; i++) {
            RadioColorCell cell = new RadioColorCell(activity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(i);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(options[i], selectedIndex == i);
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            linearLayout.addView(cell);

            cell.setOnClickListener(v -> {
                onSelected.run((Integer) v.getTag());
                builder.getDismissRunnable().run();
            });
        }

        builder.setView(linearLayout);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}

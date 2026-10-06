/*
 * DevGram: экран настроек одного плагина. Плагин описывает строки методом settings(),
 * значения хранятся через get_setting/set_setting.
 */

package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.PluginSettingsCardCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DevGramPluginSettingsActivity extends DevGramSettingsFragment {

    private final String pluginId;
    private final String pluginName;
    private UniversalRecyclerView listView;
    private final ArrayList<String[]> rows = new ArrayList<>(); // type,key,title по индексу

    // Перевод пунктов настроек в один тап (кнопка в шапке; видна, если язык текстов другой)
    private static final int MENU_TRANSLATE = 1;
    private final java.util.HashMap<String, String> translations = new java.util.HashMap<>();
    private final java.util.LinkedHashSet<String> visibleTexts = new java.util.LinkedHashSet<>();
    private boolean translated;
    private boolean translateChecked;
    private org.telegram.ui.ActionBar.ActionBarMenuItem translateItem;
    private org.telegram.ui.Components.DevGramTranslateButton translateButton;

    public DevGramPluginSettingsActivity(String pluginId, String pluginName) {
        this.pluginId = pluginId;
        this.pluginName = pluginName;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(pluginName != null ? pluginName : "Плагин");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_TRANSLATE) {
                    onTranslateClick();
                }
            }
        });
        translateItem = actionBar.createMenu().addItem(MENU_TRANSLATE, 0);
        translateButton = new org.telegram.ui.Components.DevGramTranslateButton(context,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        translateItem.addView(translateButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        translateItem.setContentDescription("Перевести");
        translateItem.setVisibility(View.GONE);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        rows.clear();
        visibleTexts.clear();
        Context context = getParentActivity();
        List<String> list = DevGramPlugins.pluginSettings(pluginId);
        for (int i = 0; i < list.size(); i++) {
            String[] r = list.get(i).split("\u001f", -1); // type, key, title
            rows.add(r);
            String type = r.length > 0 ? r[0] : "";
            String key = r.length > 1 ? r[1] : "";
            String rawTitle = r.length > 2 ? r[2] : "";
            String options = r.length > 3 ? r[3] : "";
            if (!"custom_view".equals(type)) visibleTexts.add(rawTitle);
            String title = tr(rawTitle);
            if ("header".equals(type)) {
                items.add(UItem.asHeader(title));
            } else if ("switch".equals(type)) {
                boolean on = "1".equals(DevGramPlugins.pluginGet(pluginId, key, "0"));
                items.add(UItem.asCheck(i, title).setChecked(on));
            } else if ("text".equals(type)) {
                String val = DevGramPlugins.pluginGet(pluginId, key, "");
                items.add(UItem.asButton(i, R.drawable.msg_edit, title, val.isEmpty() ? "—" : val));
            } else if ("button".equals(type)) {
                items.add(UItem.asButton(i, R.drawable.msg_settings, title));
            } else if ("info".equals(type)) {
                // Чисто информационная строка (devgram.ui.Text) — без иконки и без клика,
                // в отличие от настоящей кнопки (Button), которая та же "button" не должна быть.
                items.add(UItem.asShadow(title));
            } else if ("card".equals(type) && context != null) {
                // Цветная карточка-баннер (devgram.ui.Card): options = "иконкаподзаголовокцвет".
                String[] parts = options.split("\u0001", -1);
                String icon = parts.length > 0 ? parts[0] : "";
                String subtitle = parts.length > 1 ? parts[1] : "";
                visibleTexts.add(subtitle);
                subtitle = tr(subtitle);
                int color;
                try {
                    color = parts.length > 2 ? Integer.parseInt(parts[2]) : 0xFF3B82F6;
                } catch (Throwable e) {
                    color = 0xFF3B82F6;
                }
                PluginSettingsCardCell cell = new PluginSettingsCardCell(context);
                cell.set(icon, title, subtitle, color);
                // Раньше была подобранная на глаз фиксированная высота (76dp) — не хватало
                // места, обрезало подпись у любого более длинного текста разработчика плагина.
                // WRAP_CONTENT — ячейка сама подстраивается под содержимое (без/с подписью,
                // 1-2 строки подписи и т.п.), без гадания с числом под конкретный текст.
                UItem cardItem = UItem.asCustom(i, cell);
                cardItem.intValue = LayoutHelper.WRAP_CONTENT;
                items.add(cardItem);
            } else if ("custom_view".equals(type) && context != null) {
                // devgram.ui.Custom — разработчик плагина сам построил View (java_class/jclass
                // на Python-стороне) и целиком отвечает за её стиль, размер и клики. Ядро
                // только встраивает готовую вью в список, ничего не навязывает.
                View custom = DevGramPlugins.pluginSettingsCustomView(pluginId, i);
                if (custom != null) {
                    int heightDp = 0;
                    try { heightDp = Integer.parseInt(options.trim()); } catch (Throwable ignore) { }
                    UItem customItem = UItem.asCustom(i, custom);
                    customItem.intValue = heightDp > 0 ? heightDp : LayoutHelper.WRAP_CONTENT;
                    items.add(customItem);
                }
            } else if ("selector".equals(type)) {
                String value = DevGramPlugins.pluginGet(pluginId, key, "0");
                int selected = 0; try { selected = Integer.parseInt(value); } catch (Exception ignore) { }
                String[] choices = options.split("\\|", -1);
                for (String ch : choices) visibleTexts.add(ch);
                String current = choices.length > 0 && selected >= 0 && selected < choices.length ? tr(choices[selected]) : "—";
                items.add(UItem.asButton(i, R.drawable.msg_list, title, current));
            }
        }
        if (rows.isEmpty()) {
            items.add(UItem.asShadow("У этого плагина нет настроек."));
        }
        if (!translateChecked && !visibleTexts.isEmpty()) {
            translateChecked = true;
            org.telegram.messenger.DevGramPluginTranslation.checkNeeded(new ArrayList<>(visibleTexts), needed -> {
                if (translateItem != null) translateItem.setVisibility(needed ? View.VISIBLE : View.GONE);
            });
        }
    }

    private String tr(String text) {
        if (!translated || text == null) return text;
        String t = translations.get(text);
        return t != null ? t : text;
    }

    private void onTranslateClick() {
        if (translateButton == null) return;
        int st = translateButton.getState();
        if (st == org.telegram.ui.Components.DevGramTranslateButton.STATE_LOADING) return;
        if (translated) {
            translated = false;
            translateButton.setState(org.telegram.ui.Components.DevGramTranslateButton.STATE_TRANSLATE);
            refresh();
            return;
        }
        translateButton.setState(org.telegram.ui.Components.DevGramTranslateButton.STATE_LOADING);
        org.telegram.messenger.DevGramPluginTranslation.translate(new ArrayList<>(visibleTexts), map -> {
            if (map == null) {
                translateButton.setState(org.telegram.ui.Components.DevGramTranslateButton.STATE_TRANSLATE);
                org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin("Не удалось перевести").show();
                return;
            }
            translations.putAll(map);
            translated = true;
            translateButton.setState(org.telegram.ui.Components.DevGramTranslateButton.STATE_RESET);
            refresh();
        });
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        int idx = item.id;
        if (idx < 0 || idx >= rows.size()) {
            return;
        }
        String[] r = rows.get(idx);
        String type = r.length > 0 ? r[0] : "";
        String key = r.length > 1 ? r[1] : "";
        String title = r.length > 2 ? r[2] : "";
        String options = r.length > 3 ? r[3] : "";
        if ("switch".equals(type)) {
            boolean on = "1".equals(DevGramPlugins.pluginGet(pluginId, key, "0"));
            DevGramPlugins.pluginSet(pluginId, key, on ? "0" : "1");
            DevGramPlugins.pluginSettingChanged(pluginId, key, on ? "0" : "1");
            refresh();
        } else if ("button".equals(type) || "card".equals(type)) {
            DevGramPlugins.pluginSettingClick(pluginId, key);
        } else if ("text".equals(type)) {
            editText(key, tr(title));
        } else if ("selector".equals(type)) {
            String[] choices = options.split("\\|", -1);
            int selected = 0; try { selected = Integer.parseInt(DevGramPlugins.pluginGet(pluginId, key, "0")); } catch (Exception ignore) { }
            AlertDialog.Builder dialog = new AlertDialog.Builder(getParentActivity());
            dialog.setTitle(tr(title));
            CharSequence[] labels = new CharSequence[choices.length];
            for (int i = 0; i < choices.length; i++) labels[i] = (i == selected ? "• " : "    ") + tr(choices[i]);
            dialog.setItems(labels, (d, which) -> {
                DevGramPlugins.pluginSet(pluginId, key, String.valueOf(which));
                DevGramPlugins.pluginSettingChanged(pluginId, key, String.valueOf(which));
                d.dismiss(); refresh();
            });
            dialog.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(dialog.create());
        }
    }

    private void editText(String key, String title) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 17);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(DevGramPlugins.pluginGet(pluginId, key, ""));
        et.setSelection(et.getText().length());
        LinearLayout box = new LinearLayout(context);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        box.addView(et, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(title);
        b.setView(box);
        b.setPositiveButton("OK", (d, w) -> {
            DevGramPlugins.pluginSet(pluginId, key, et.getText().toString());
            DevGramPlugins.pluginSettingChanged(pluginId, key, et.getText().toString());
            refresh();
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        et.requestFocus();
        AndroidUtilities.showKeyboard(et);
    }

    private void refresh() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}

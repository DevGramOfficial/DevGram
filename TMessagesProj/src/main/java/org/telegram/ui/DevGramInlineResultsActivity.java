package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.devgram.inline.ExchangeRates;
import org.telegram.messenger.devgram.inline.docs.InlineExampleGroup;
import org.telegram.messenger.devgram.inline.docs.InlineExamples;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Результаты в поле ввода: калькулятор, валюты, единицы и часовые пояса. */
public class DevGramInlineResultsActivity extends DevGramSettingsFragment {
    private static final int ID_SHOW = 1;
    private static final int ID_MATH = 2;
    private static final int ID_CURRENCY = 3;
    private static final int ID_UNITS = 4;
    private static final int ID_TIME_ZONES = 5;
    private static final int ID_GROUPING = 6;
    private static final int ID_ANGLE = 7;
    private static final int ID_TARGET_CURRENCY = 8;
    private static final int ID_MEASURE = 9;
    private static final int ID_EXAMPLE = 100;

    private static final String[] ANGLES = {"Градусы", "Радианы"};
    private static final String[] MEASURES = {"Авто", "Метрическая", "Имперская"};

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Результаты в поле ввода");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        return fragmentView = new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        adapter.whiteSectionStart();
        items.add(UItem.asRippleCheck(ID_SHOW, "Показывать результаты").setChecked(DevGramConfig.inlineCalc));
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow("Напишите выражение и поставьте «=» — ответ появится серым прямо в поле ввода. Пробел, Enter или → подставляют его, Backspace сразу после — отменяет."));
        if (!DevGramConfig.inlineCalc) {
            return;
        }

        adapter.whiteSectionStart();
        items.add(UItem.asHeader("Что считать"));
        items.add(UItem.asCheck(ID_MATH, "Калькулятор").setChecked(DevGramConfig.inlineMath));
        items.add(UItem.asCheck(ID_CURRENCY, "Курсы валют").setChecked(DevGramConfig.inlineCurrency));
        items.add(UItem.asCheck(ID_UNITS, "Единицы измерения").setChecked(DevGramConfig.inlineUnits));
        items.add(UItem.asCheck(ID_TIME_ZONES, "Часовые пояса").setChecked(DevGramConfig.inlineTimeZones));
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow(null));

        adapter.whiteSectionStart();
        items.add(UItem.asHeader("Настройки"));
        items.add(UItem.asCheck(ID_GROUPING, "Разделять разряды").setChecked(DevGramConfig.inlineDigitGrouping));
        if (DevGramConfig.inlineMath) {
            items.add(UItem.asButton(ID_ANGLE, "Углы", ANGLES[clamp(DevGramConfig.inlineAngleUnit, ANGLES.length)]));
        }
        if (DevGramConfig.inlineCurrency) {
            items.add(UItem.asButton(ID_TARGET_CURRENCY, "Валюта по умолчанию", TextUtils.isEmpty(DevGramConfig.inlineTargetCurrency) ? "Авто" : DevGramConfig.inlineTargetCurrency));
        }
        if (DevGramConfig.inlineUnits) {
            items.add(UItem.asButton(ID_MEASURE, "Система мер", MEASURES[clamp(DevGramConfig.inlineMeasureSystem, MEASURES.length)]));
        }
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow("«Авто» для валюты берёт валюту страны телефона. Курсы обновляются раз в несколько минут."));

        adapter.whiteSectionStart();
        items.add(UItem.asHeader("Примеры"));
        int id = ID_EXAMPLE;
        InlineExamples ex = InlineExamples.INSTANCE;
        if (DevGramConfig.inlineMath) {
            id = addExamples(items, id, ex.getArithmetic(), ex.getArithmeticFunctions(), ex.getBases(), ex.getPercentages(), ex.getShorthands());
        }
        if (DevGramConfig.inlineCurrency) {
            id = addExamples(items, id, ex.getCurrencyDefault(), ex.getCurrencyTarget());
        }
        if (DevGramConfig.inlineUnits) {
            id = addExamples(items, id, ex.getUnitsDefault(), ex.getUnitsTarget(), ex.getDurations());
        }
        if (DevGramConfig.inlineTimeZones) {
            addExamples(items, id, ex.getTimeZones(), ex.getCurrentTime());
        }
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow("Нажмите на пример, чтобы скопировать его. Функции: " + TextUtils.join(", ", ex.getFunctions()) + "."));
    }

    private int addExamples(ArrayList<UItem> items, int id, InlineExampleGroup... groups) {
        for (InlineExampleGroup group : groups) {
            List<String> list = group.examples(true);
            for (String example : list) {
                UItem item = UItem.asButton(id++, example);
                item.object = example;
                items.add(item);
            }
        }
        return id;
    }

    private static int clamp(int i, int n) {
        return i >= 0 && i < n ? i : 0;
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_SHOW:
                DevGramConfig.setInlineCalc(!DevGramConfig.inlineCalc);
                break;
            case ID_MATH:
                DevGramConfig.setInlineBoolean("inlineMath", !DevGramConfig.inlineMath);
                break;
            case ID_CURRENCY:
                DevGramConfig.setInlineBoolean("inlineCurrency", !DevGramConfig.inlineCurrency);
                break;
            case ID_UNITS:
                DevGramConfig.setInlineBoolean("inlineUnits", !DevGramConfig.inlineUnits);
                break;
            case ID_TIME_ZONES:
                DevGramConfig.setInlineBoolean("inlineTimeZones", !DevGramConfig.inlineTimeZones);
                break;
            case ID_GROUPING:
                DevGramConfig.setInlineBoolean("inlineDigitGrouping", !DevGramConfig.inlineDigitGrouping);
                break;
            case ID_ANGLE:
                choose("Углы", ANGLES, DevGramConfig.inlineAngleUnit, DevGramConfig::setInlineAngleUnit);
                return;
            case ID_MEASURE:
                choose("Система мер", MEASURES, DevGramConfig.inlineMeasureSystem, DevGramConfig::setInlineMeasureSystem);
                return;
            case ID_TARGET_CURRENCY: {
                List<String> codes = ExchangeRates.getSupportedCurrencies();
                String[] options = new String[codes.size() + 1];
                options[0] = "Авто";
                int selected = 0;
                for (int i = 0; i < codes.size(); i++) {
                    options[i + 1] = codes.get(i);
                    if (codes.get(i).equalsIgnoreCase(DevGramConfig.inlineTargetCurrency)) {
                        selected = i + 1;
                    }
                }
                choose("Валюта по умолчанию", options, selected, which -> DevGramConfig.setInlineTargetCurrency(which == 0 ? "" : options[which]));
                return;
            }
            default:
                if (item.object instanceof String) {
                    AndroidUtilities.addToClipboard((String) item.object);
                    BulletinFactory.of(this).createCopyBulletin("Пример скопирован").show();
                }
                return;
        }
        listView.adapter.update(true);
    }

    private interface IntSetter {
        void set(int value);
    }

    private void choose(String title, String[] options, int selected, IntSetter setter) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        builder.setTitle(title);
        CharSequence[] marked = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            marked[i] = (i == selected ? "✓ " : "") + options[i];
        }
        builder.setItems(marked, (dialog, which) -> {
            setter.set(which);
            listView.adapter.update(true);
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }
}

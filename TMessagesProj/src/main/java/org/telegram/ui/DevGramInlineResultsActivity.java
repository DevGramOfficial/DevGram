package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.devgram.inline.ExchangeRates;
import org.telegram.messenger.devgram.inline.InlineConfig;
import org.telegram.messenger.devgram.inline.InlineRates;
import org.telegram.messenger.devgram.inline.InlineResults;
import org.telegram.messenger.devgram.inline.docs.InlineExampleGroup;
import org.telegram.messenger.devgram.inline.docs.InlineExamples;
import org.telegram.messenger.devgram.inline.math.MathExpression;
import org.telegram.messenger.devgram.inline.math.MathOptions;
import org.telegram.messenger.devgram.inline.math.MathSuggestion;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.devgram.InlineDocBlock;
import org.telegram.ui.Components.devgram.InlineDocExample;
import org.telegram.ui.Components.devgram.InlineFeature;
import org.telegram.ui.Components.devgram.InlineFeatureCell;

import java.text.Collator;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Результаты в поле ввода: калькулятор, валюты, единицы и часовые пояса. */
public class DevGramInlineResultsActivity extends DevGramSettingsFragment {
    private static final int ID_SHOW = 1;
    private static final int ID_CALCULATOR = 2;
    private static final int ID_CURRENCIES = 3;
    private static final int ID_UNITS = 4;
    private static final int ID_TIME_ZONES = 5;
    private static final int ID_DEFAULT_CURRENCY = 6;
    private static final int ID_MEASURE_SYSTEM = 7;
    private static final int ID_ANGLE_UNIT = 8;
    private static final int ID_DIGIT_GROUPING = 9;

    /** Разделы «Возможностей»: каждая карточка раскрывается и показывает правила и примеры. */
    private enum Section {
        ARITHMETIC(10, R.drawable.msg_calculator, R.string.InlineDocArithmetic, R.string.InlineDocArithmeticInfo),
        PERCENTAGES(11, R.drawable.msg_percent, R.string.InlineDocPercent, R.string.InlineDocPercentInfo),
        CURRENCIES(12, R.drawable.menu_feature_paid, R.string.InlineCurrencies, R.string.InlineDocCurrenciesInfo),
        UNITS(13, R.drawable.msg_ruler, R.string.InlineUnits, R.string.InlineDocUnitsInfo),
        TIME(14, R.drawable.msg_language, R.string.InlineTimeZones, R.string.InlineDocTimeInfo);

        final int id, icon, title, subtitle;

        Section(int id, int icon, int title, int subtitle) {
            this.id = id;
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
        }

        boolean enabled() {
            switch (this) {
                case CURRENCIES: return DevGramConfig.inlineCurrency;
                case UNITS: return DevGramConfig.inlineUnits;
                case TIME: return DevGramConfig.inlineTimeZones;
                default: return DevGramConfig.inlineMath;
            }
        }
    }

    private final LinkedHashMap<String, String> cryptoNames = new LinkedHashMap<>();
    private final HashSet<Section> expanded = new HashSet<>();
    private final InlineRates rates = new InlineRates(this::onRatesLoaded);
    private String[] angleOptions, measureOptions, currencyOptions;
    private List<String> currencyCodes;
    private UniversalRecyclerView listView;

    {
        cryptoNames.put("GRAM", "Gram");
        cryptoNames.put("BTC", "Bitcoin");
        cryptoNames.put("ETH", "Ethereum");
        cryptoNames.put("SOL", "Solana");
        cryptoNames.put("USDT", "Tether");
    }

    @Override
    public View createView(Context context) {
        initOptionStrings();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.InlineMathResult));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        return fragmentView = new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
    }

    private void onRatesLoaded() {
        if (fragmentView != null && listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void initOptionStrings() {
        angleOptions = new String[]{LocaleController.getString(R.string.InlineAngleDegrees), LocaleController.getString(R.string.InlineAngleRadians)};
        measureOptions = new String[]{LocaleController.getString(R.string.QualityAuto), LocaleController.getString(R.string.InlineMeasureMetric), LocaleController.getString(R.string.InlineMeasureImperial)};
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale == null) {
            locale = Locale.getDefault();
        }
        ArrayList<String> codes = new ArrayList<>();
        for (String code : ExchangeRates.getSupportedCurrencies()) {
            codes.add(InlineRates.INSTANCE.normalize(code));
        }
        // фиатные валюты по названию на языке интерфейса, криптовалюты — в конце
        ArrayList<String[]> fiat = new ArrayList<>();
        for (String code : codes) {
            if (cryptoNames.containsKey(code)) {
                continue;
            }
            String name;
            try {
                name = Currency.getInstance(code).getDisplayName(locale);
                if (!name.isEmpty()) {
                    name = name.substring(0, 1).toUpperCase(locale) + name.substring(1);
                }
            } catch (IllegalArgumentException e) {
                name = code;
            }
            fiat.add(new String[]{code, name});
        }
        Collator collator = Collator.getInstance(locale);
        fiat.sort((a, b) -> collator.compare(a[1], b[1]));
        for (Map.Entry<String, String> crypto : cryptoNames.entrySet()) {
            if (codes.contains(crypto.getKey())) {
                fiat.add(new String[]{crypto.getKey(), crypto.getValue()});
            }
        }
        currencyCodes = new ArrayList<>();
        currencyCodes.add("");
        ArrayList<String> labels = new ArrayList<>();
        labels.add(LocaleController.getString(R.string.QualityAuto));
        for (String[] pair : fiat) {
            currencyCodes.add(pair[0]);
            labels.add(pair[1] + " — " + pair[0]);
        }
        currencyOptions = labels.toArray(new String[0]);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asRippleCheck(ID_SHOW, LocaleController.getString(R.string.InlineShowResults)).setChecked(DevGramConfig.inlineCalc));
        items.add(UItem.asShadow(LocaleController.getString(R.string.InlineMathResultHint)));
        if (DevGramConfig.inlineCalc) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.InlineFeatures)));
            items.add(check(ID_CALCULATOR, R.string.InlineCalculator, DevGramConfig.inlineMath, R.drawable.msg_calculator));
            items.add(check(ID_CURRENCIES, R.string.InlineCurrencies, DevGramConfig.inlineCurrency, R.drawable.menu_feature_paid));
            items.add(check(ID_UNITS, R.string.InlineUnits, DevGramConfig.inlineUnits, R.drawable.msg_ruler));
            items.add(check(ID_TIME_ZONES, R.string.InlineTimeZones, DevGramConfig.inlineTimeZones, R.drawable.msg_language));
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(LocaleController.getString(R.string.Settings)));
            items.add(check(ID_DIGIT_GROUPING, R.string.InlineDigitGrouping, DevGramConfig.inlineDigitGrouping, 0));
            if (DevGramConfig.inlineMath) {
                items.add(UItem.asButton(ID_ANGLE_UNIT, LocaleController.getString(R.string.InlineAngleUnit), angleOptions[InlineConfig.getInlineAngleUnit().ordinal()]));
            }
            if (DevGramConfig.inlineCurrency) {
                items.add(UItem.asButton(ID_DEFAULT_CURRENCY, LocaleController.getString(R.string.InlineDefaultCurrency), defaultCurrencyValue()));
            }
            if (DevGramConfig.inlineUnits) {
                items.add(UItem.asButton(ID_MEASURE_SYSTEM, LocaleController.getString(R.string.InlineMeasureSystem), measureOptions[InlineConfig.getInlineMeasureSystem().ordinal()]));
            }
            items.add(UItem.asShadow(settingsInfo()));
        }
        items.add(UItem.asHeader(LocaleController.getString(R.string.InlineCapabilities)));
        MathOptions options = expanded.isEmpty() ? null : InlineResults.INSTANCE.options(rates, decimalSeparator(), true);
        for (Section section : Section.values()) {
            CharSequence badge = DevGramConfig.inlineCalc && !section.enabled() ? LocaleController.getString(R.string.InlineFeatureOff) : null;
            List<InlineDocBlock> docs = options != null && expanded.contains(section) ? blocks(section, options) : null;
            items.add(InlineFeatureCell.Factory.INSTANCE.of(section.id, new InlineFeature(section.icon, LocaleController.getString(section.title), LocaleController.getString(section.subtitle), badge, docs), this::onFeatureClick));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.InlineTapToCopy)));
    }

    private static UItem check(int id, int title, boolean checked, int icon) {
        UItem item = UItem.asCheck(id, LocaleController.getString(title)).setChecked(checked);
        item.iconResId = icon;
        return item;
    }

    private void onFeatureClick(View view) {
        Object tag = view.getTag();
        if (tag instanceof String) {
            if (AndroidUtilities.addToClipboard((CharSequence) tag)) {
                BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
            }
            return;
        }
        if (tag instanceof Integer) {
            for (Section section : Section.values()) {
                if (section.id == (Integer) tag) {
                    if (!expanded.remove(section)) {
                        expanded.add(section);
                    }
                    listView.adapter.update(true);
                    return;
                }
            }
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_SHOW:
                DevGramConfig.setInlineCalc(!DevGramConfig.inlineCalc);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setBackgroundColorAnimated(DevGramConfig.inlineCalc, getThemedColor(DevGramConfig.inlineCalc ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                }
                break;
            case ID_CALCULATOR:
                DevGramConfig.setInlineBoolean("inlineMath", !DevGramConfig.inlineMath);
                break;
            case ID_CURRENCIES:
                DevGramConfig.setInlineBoolean("inlineCurrency", !DevGramConfig.inlineCurrency);
                break;
            case ID_UNITS:
                DevGramConfig.setInlineBoolean("inlineUnits", !DevGramConfig.inlineUnits);
                break;
            case ID_TIME_ZONES:
                DevGramConfig.setInlineBoolean("inlineTimeZones", !DevGramConfig.inlineTimeZones);
                break;
            case ID_DIGIT_GROUPING:
                DevGramConfig.setInlineBoolean("inlineDigitGrouping", !DevGramConfig.inlineDigitGrouping);
                break;
            case ID_DEFAULT_CURRENCY: {
                int current = Math.max(0, currencyCodes.indexOf(InlineRates.INSTANCE.normalize(DevGramConfig.inlineTargetCurrency)));
                showRadioDialog(LocaleController.getString(R.string.InlineDefaultCurrency), currencyOptions, current, which -> DevGramConfig.setInlineTargetCurrency(currencyCodes.get(which)));
                return;
            }
            case ID_ANGLE_UNIT:
                showRadioDialog(LocaleController.getString(R.string.InlineAngleUnit), angleOptions, InlineConfig.getInlineAngleUnit().ordinal(), DevGramConfig::setInlineAngleUnit);
                return;
            case ID_MEASURE_SYSTEM:
                showRadioDialog(LocaleController.getString(R.string.InlineMeasureSystem), measureOptions, InlineConfig.getInlineMeasureSystem().ordinal(), DevGramConfig::setInlineMeasureSystem);
                return;
            default:
                return;
        }
        listView.adapter.update(true);
    }

    private interface IntSetter {
        void set(int value);
    }

    /** Выбор одного значения из списка с радиокнопками. */
    private void showRadioDialog(String title, String[] options, int selected, IntSetter setter) {
        if (getParentActivity() == null) return;
        Context context = getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(title);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] dialog = new AlertDialog[1];
        for (int i = 0; i < options.length; i++) {
            RadioColorCell cell = new RadioColorCell(context, resourceProvider);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resourceProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourceProvider));
            cell.setTextAndValue(options[i], i == selected);
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), Theme.RIPPLE_MASK_ALL));
            final int which = i;
            cell.setOnClickListener(v -> {
                if (which != selected) {
                    setter.set(which);
                    listView.adapter.update(true);
                }
                if (dialog[0] != null) {
                    dialog[0].dismiss();
                }
            });
            linearLayout.addView(cell);
        }
        if (options.length > 8) {
            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(linearLayout);
            builder.setView(scrollView);
        } else {
            builder.setView(linearLayout);
        }
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        dialog[0] = builder.create();
        showDialog(dialog[0]);
    }

    private String defaultCurrencyValue() {
        String code = InlineRates.INSTANCE.normalize(DevGramConfig.inlineTargetCurrency);
        return rates.supports(code) ? code : LocaleController.getString(R.string.QualityAuto);
    }

    private String settingsInfo() {
        if (!DevGramConfig.inlineCurrency) {
            return null;
        }
        String info = LocaleController.getString(R.string.InlineAutoCurrencyInfo);
        ExchangeRates.State state = ExchangeRates.peekCached();
        if (state == null) {
            return info;
        }
        return info + " " + LocaleController.formatString(R.string.InlineRatesUpdated, LocaleController.formatDateAudio(state.time() / 1000, true));
    }

    private char decimalSeparator() {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale == null) {
            locale = Locale.US;
        }
        return DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
    }

    private List<InlineDocBlock> blocks(Section section, MathOptions options) {
        InlineExamples ex = InlineExamples.INSTANCE;
        switch (section) {
            case ARITHMETIC:
                return Arrays.asList(examples(ex.getArithmetic(), options), text(R.string.InlineDocFunctionsText), new InlineDocBlock.Codes(ex.getFunctions()),
                    examples(ex.getArithmeticFunctions(), options), text(R.string.InlineDocBasesText), examples(ex.getBases(), options));
            case PERCENTAGES:
                return Arrays.asList(text(R.string.InlineDocPercentText), examples(ex.getPercentages(), options), text(R.string.InlineDocShorthandsText), examples(ex.getShorthands(), options));
            case CURRENCIES:
                return Arrays.asList(text(R.string.InlineDocCurrencyText), examples(ex.getCurrencyDefault(), options), examples(ex.getCurrencyTarget(), options));
            case UNITS:
                return Arrays.asList(text(R.string.InlineDocUnitsText), examples(ex.getUnitsDefault(), options), examples(ex.getUnitsTarget(), options),
                    text(R.string.InlineDocDurationsText), examples(ex.getDurations(), options));
            default:
                return Arrays.asList(text(R.string.InlineDocTimeText), examples(ex.getTimeZones(), options), text(R.string.InlineDocCurrentTimeText), examples(ex.getCurrentTime(), options));
        }
    }

    private static InlineDocBlock text(int res) {
        return new InlineDocBlock.Text(LocaleController.getString(res));
    }

    /** Примеры на языке интерфейса с готовым ответом справа. */
    private static InlineDocBlock examples(InlineExampleGroup group, MathOptions options) {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        String language = locale != null ? locale.getLanguage() : null;
        List<String> list = group.examples("ru".equals(language) || "be".equals(language));
        ArrayList<InlineDocExample> result = new ArrayList<>();
        for (String example : list) {
            MathSuggestion suggestion = MathExpression.INSTANCE.suggestionAt(example, example.length(), options);
            result.add(new InlineDocExample(example, suggestion != null ? suggestion.getValue() : null));
        }
        return new InlineDocBlock.Examples(result);
    }
}

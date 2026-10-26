package org.telegram.messenger.devgram.inline;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.devgram.AngleUnit;
import org.telegram.messenger.devgram.MeasureSystem;

/** Настройки умных результатов в поле ввода (калькулятор, валюты, единицы, часовые пояса). */
public final class InlineConfig {

    private static Gson gson;

    private InlineConfig() {
    }

    public static Gson getGSON() {
        if (gson == null) {
            gson = new Gson();
        }
        return gson;
    }

    /** Кэш курсов и время последнего запроса. */
    public static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("devgram_inline", Context.MODE_PRIVATE);
    }

    public static SharedPreferences.Editor getEditor() {
        return getPreferences().edit();
    }

    public static boolean getInlineResults() {
        return DevGramConfig.inlineCalc;
    }

    public static boolean getInlineMathResult() {
        return DevGramConfig.inlineMath;
    }

    public static boolean getInlineCurrencyRates() {
        return DevGramConfig.inlineCurrency;
    }

    public static boolean getInlineUnitConversion() {
        return DevGramConfig.inlineUnits;
    }

    public static boolean getInlineTimeZones() {
        return DevGramConfig.inlineTimeZones;
    }

    public static String getInlineTargetCurrency() {
        return DevGramConfig.inlineTargetCurrency;
    }

    public static AngleUnit getInlineAngleUnit() {
        AngleUnit[] values = AngleUnit.values();
        int i = DevGramConfig.inlineAngleUnit;
        return i >= 0 && i < values.length ? values[i] : AngleUnit.DEGREES;
    }

    public static MeasureSystem getInlineMeasureSystem() {
        MeasureSystem[] values = MeasureSystem.values();
        int i = DevGramConfig.inlineMeasureSystem;
        return i >= 0 && i < values.length ? values[i] : MeasureSystem.AUTO;
    }

    public static boolean getInlineDigitGrouping() {
        return DevGramConfig.inlineDigitGrouping;
    }
}

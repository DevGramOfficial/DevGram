package org.telegram.messenger.devgram.inline;

import kotlin.NoWhenBranchMatchedException;

import org.telegram.messenger.devgram.AngleUnit;
import org.telegram.messenger.devgram.inline.InlineConfig;
import org.telegram.messenger.devgram.MeasureSystem;
import org.telegram.messenger.devgram.inline.math.MathOptions;
import org.telegram.messenger.devgram.inline.time.TimeZoneOptions;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import org.telegram.messenger.LocaleController;
public final class InlineResults {
    public static final InlineResults INSTANCE = new InlineResults();
    private static final Set<String> imperialRegions = SetsKt.setOf(new String[]{"US", "LR", "MM"});

    public static final /* synthetic */ class WhenMappings {
        public static final /* synthetic */ int[] $EnumSwitchMapping$0;

        static {
            int[] iArr = new int[MeasureSystem.values().length];
            try {
                iArr[MeasureSystem.AUTO.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                iArr[MeasureSystem.METRIC.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                iArr[MeasureSystem.IMPERIAL.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
            $EnumSwitchMapping$0 = iArr;
        }
    }

    private InlineResults() {
    }

    public final boolean isImperial() {
        int i = WhenMappings.$EnumSwitchMapping$0[InlineConfig.getInlineMeasureSystem().ordinal()];
        if (i == 1) {
            Set<String> set = imperialRegions;
            Locale systemDefaultLocale = LocaleController.getInstance().getSystemDefaultLocale();
            return CollectionsKt.contains(set, systemDefaultLocale != null ? systemDefaultLocale.getCountry() : null);
        }
        if (i != 2) {
            if (i == 3) {
                return true;
            }
            throw new NoWhenBranchMatchedException();
        }
        return false;
    }

    public final boolean isEnabled() {
        if (InlineConfig.getInlineResults()) {
            return InlineConfig.getInlineMathResult() || InlineConfig.getInlineCurrencyRates() || InlineConfig.getInlineUnitConversion() || InlineConfig.getInlineTimeZones();
        }
        return false;
    }

    public static /* synthetic */ MathOptions options$default(InlineResults inlineResults, InlineRates inlineRates, char c2, boolean z, int i, Object obj) {
        if ((i & 4) != 0) {
            z = false;
        }
        return inlineResults.options(inlineRates, c2, z);
    }

    public final MathOptions options(InlineRates rates, char decimalSeparator, boolean allFeatures) {
        boolean z = allFeatures || InlineConfig.getInlineCurrencyRates();
        return new MathOptions(decimalSeparator, z ? rates : null, z ? rates.localCurrency() : null, allFeatures || InlineConfig.getInlineMathResult(), allFeatures || InlineConfig.getInlineUnitConversion(), InlineConfig.getInlineAngleUnit() == AngleUnit.DEGREES, InlineConfig.getInlineDigitGrouping(), (allFeatures || InlineConfig.getInlineTimeZones()) ? new TimeZoneOptions(ZoneId.systemDefault(), null, 2, null) : null, isImperial());
    }
}

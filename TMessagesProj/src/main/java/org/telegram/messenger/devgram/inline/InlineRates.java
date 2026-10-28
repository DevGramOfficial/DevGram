package org.telegram.messenger.devgram.inline;

import android.os.SystemClock;
import org.telegram.messenger.devgram.inline.InlineConfig;
import org.telegram.messenger.devgram.inline.rates.CurrencyRates;
import org.telegram.messenger.devgram.inline.ExchangeRates;
import java.math.BigDecimal;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.time.DurationKt;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
public final class InlineRates implements CurrencyRates {
    public static final Companion INSTANCE = new Companion(null);
    private int localAccount = -1;
    private String localCurrency;
    private final Runnable onUpdated;
    private long requestedAt;
    private boolean requesting;

    public InlineRates(Runnable runnable) {
        this.onUpdated = runnable;
    }
    public static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public final String normalize(String code) {
            return Intrinsics.areEqual(code, "TON") ? "GRAM" : code;
        }
        public final String rateCode(String code) {
            return Intrinsics.areEqual(code, "GRAM") ? "TON" : code;
        }
    }

    @Override
    public boolean supports(String code) {
        return Intrinsics.areEqual(code, "XTR") || ExchangeRates.isSupportedCurrency(INSTANCE.rateCode(code));
    }

    @Override
    public Double rate(String from, String to) {
        Double dUsdRate = usdRate(from);
        if (dUsdRate != null) {
            double dDoubleValue = dUsdRate.doubleValue();
            Double dUsdRate2 = usdRate(to);
            if (dUsdRate2 != null) {
                double dDoubleValue2 = dUsdRate2.doubleValue();
                if (dDoubleValue2 == 0.0d) {
                    return null;
                }
                return Double.valueOf(dDoubleValue / dDoubleValue2);
            }
        }
        return null;
    }

    private final Double usdRate(String code) {
        if (Intrinsics.areEqual(code, "XTR")) {
            float f = MessagesController.getInstance(UserConfig.selectedAccount).starsUsdSellRate1000;
            if (f > 0.0f) {
                return Double.valueOf(((double) f) / 100000.0d);
            }
            return null;
        }
        ExchangeRates.State statePeekCached = ExchangeRates.peekCached();
        if (statePeekCached != null && System.currentTimeMillis() - statePeekCached.time() <= DurationKt.MILLIS_IN_DAY) {
            BigDecimal usdRate = statePeekCached.getUsdRate(INSTANCE.rateCode(code));
            if (usdRate == null) {
                request(30000);
                return null;
            }
            request(300000L);
            return Double.valueOf(usdRate.doubleValue());
        }
        request(30000);
        return null;
    }

    public final String localCurrency() {
        String strNormalize = INSTANCE.normalize(InlineConfig.getInlineTargetCurrency());
        if (supports(strNormalize)) {
            return strNormalize;
        }
        int i = UserConfig.selectedAccount;
        if (i != this.localAccount) {
            this.localAccount = i;
            this.localCurrency = ExchangeRates.getLocalCurrency(i);
        }
        return this.localCurrency;
    }

    private final void request(long interval) {
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        if (this.requesting) {
            return;
        }
        long j = this.requestedAt;
        if (j == 0 || jElapsedRealtime - j >= interval) {
            this.requesting = true;
            this.requestedAt = jElapsedRealtime;
            Utilities.globalQueue.postRunnable(() -> InlineRates.$r8$lambda$yWUFhWyDLzDwP9o8ODAyGTM3ggw(this));
        }
    }

    public static void $r8$lambda$yWUFhWyDLzDwP9o8ODAyGTM3ggw(final InlineRates inlineRates) {
        ExchangeRates.getCached();
        AndroidUtilities.runOnUIThread(() -> {
ExchangeRates.fetch(obj -> InlineRates.$r8$lambda$dU6anpoGdv6MDeTOASjEEliZ4JE(inlineRates, (ExchangeRates.State) obj));
});
    }

    public static void $r8$lambda$dU6anpoGdv6MDeTOASjEEliZ4JE(InlineRates inlineRates, ExchangeRates.State state) {
        inlineRates.requesting = false;
        if (inlineRates.onUpdated != null) {
            inlineRates.onUpdated.run();
        }
    }
}

package org.telegram.messenger.devgram.inline.rates;

public interface CurrencyRates {
    Double rate(String from, String to);

    boolean supports(String code);
}

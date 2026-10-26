package org.telegram.messenger;

import android.app.Activity;

import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.util.HashMap;

/**
 * Подтверждение опасных действий плагинов: перевод звёзд и подарков, платежи, управление чатом,
 * безопасность аккаунта и кошелёк. Запрос из кода плагина уходит на сервер только после «Разрешить».
 */
public final class DevGramSensitiveRequests {

    public static final String CATEGORY_GIFTS = "Подарки";
    public static final String CATEGORY_PAYMENTS = "Платежи";
    public static final String CATEGORY_SECURITY = "Безопасность аккаунта";
    public static final String CATEGORY_CHAT = "Управление чатом";
    public static final String CATEGORY_WALLET = "Кошелёк";

    private static final HashMap<String, String> CATEGORIES = new HashMap<>();
    private static final HashMap<String, String> TITLES = new HashMap<>();

    static {
        gift("transferStarGift", "Передача подарка");
        gift("updateStarGiftPrice", "Изменение цены подарка");
        gift("convertStarGift", "Обмен подарка на звёзды");
        gift("upgradeStarGift", "Улучшение подарка");
        gift("getStarGiftWithdrawalUrl", "Вывод подарка");
        gift("withdrawStarGift", "Вывод подарка");
        gift("deleteStarGift", "Удаление подарка");
        gift("craftStarGift", "Создание подарка");
        gift("TL_sendStarGiftOffer", "Предложение за подарок");
        put("TL_payments_sendStarsForm", CATEGORY_PAYMENTS, "Оплата звёздами");
        put("TL_payments_sendPaymentForm", CATEGORY_PAYMENTS, "Оплата");
        put("TL_messages_sendPaidReaction", CATEGORY_PAYMENTS, "Платная реакция");
        put("TL_fulfillStarsSubscription", CATEGORY_PAYMENTS, "Продление подписки за звёзды");
        put("TL_changeStarsSubscription", CATEGORY_PAYMENTS, "Изменение подписки за звёзды");
        put("TL_payments_getStarsRevenueWithdrawalUrl", CATEGORY_PAYMENTS, "Вывод звёзд");
        put("TL_auth_acceptLoginToken", CATEGORY_SECURITY, "Вход на другом устройстве по QR");
        put("TL_auth_exportAuthorization", CATEGORY_SECURITY, "Экспорт авторизации");
        put("TL_auth_resetAuthorizations", CATEGORY_SECURITY, "Завершение всех сеансов");
        put("resetAuthorization", CATEGORY_SECURITY, "Завершение сеанса");
        put("resetWebAuthorization", CATEGORY_SECURITY, "Отключение сайта");
        put("resetWebAuthorizations", CATEGORY_SECURITY, "Отключение всех сайтов");
        put("deleteAccount", CATEGORY_SECURITY, "Удаление аккаунта");
        put("updatePasswordSettings", CATEGORY_SECURITY, "Изменение облачного пароля");
        put("resetPassword", CATEGORY_SECURITY, "Сброс облачного пароля");
        put("changePhone", CATEGORY_SECURITY, "Смена номера телефона");
        put("updateConnectedBot", CATEGORY_SECURITY, "Подключение бота к аккаунту");
        put("acceptAuthorization", CATEGORY_SECURITY, "Передача данных через Telegram Passport");
        put("updateUsername", CATEGORY_SECURITY, "Смена имени пользователя");
        put("toggleUsername", CATEGORY_SECURITY, "Изменение имени пользователя");
        put("setAuthorizationTTL", CATEGORY_SECURITY, "Срок автозавершения сеансов");
        put("TL_channels_editCreator", CATEGORY_CHAT, "Передача прав владельца");
        put("TL_channels_editAdmin", CATEGORY_CHAT, "Изменение прав администратора");
        put("TL_channels_deleteChannel", CATEGORY_CHAT, "Удаление канала или группы");
        put("TL_channels_updateUsername", CATEGORY_CHAT, "Смена ссылки канала или группы");
        put("TL_channels_toggleUsername", CATEGORY_CHAT, "Изменение ссылки канала или группы");
        put("sendTransfer", CATEGORY_WALLET, "Перевод из кошелька");
        put("exportSecretPhrase", CATEGORY_WALLET, "Экспорт секретной фразы");
        put("replaceWallet", CATEGORY_WALLET, "Замена кошелька");
        put("enableBackup", CATEGORY_WALLET, "Включение резервной копии");
        put("disableBackup", CATEGORY_WALLET, "Отключение резервной копии");
        put("tonConnectSubmitConnectResult", CATEGORY_WALLET, "Подключение TON Connect");
        put("tonConnectSubmitResponse", CATEGORY_WALLET, "Подтверждение TON Connect");
    }

    private static void gift(String name, String title) {
        put(name, CATEGORY_GIFTS, title);
    }

    private static void put(String name, String category, String title) {
        CATEGORIES.put(name, category);
        TITLES.put(name, title);
    }

    public interface Result {
        void run(boolean allowed);
    }

    private DevGramSensitiveRequests() {
    }

    /** Опасный запрос, отправленный из кода плагина. */
    public static boolean needsConfirmation(TLObject object) {
        if (object == null || !CATEGORIES.containsKey(object.getClass().getSimpleName())) {
            return false;
        }
        return isFromPlugin(Thread.currentThread().getStackTrace());
    }

    static boolean isFromPlugin(StackTraceElement[] stack) {
        for (StackTraceElement e : stack) {
            if (e.getClassName().startsWith("com.chaquo.python")) {
                return true;
            }
        }
        if (stack.length == 0) {
            return false;
        }
        // поток, созданный кодом Python (Chaquopy вызывает Java напрямую через JNI): у него нет
        // своей точки входа Java, а самый нижний кадр — это сам вызов отправки или моста плагинов
        StackTraceElement bottom = stack[stack.length - 1];
        String cls = bottom.getClassName();
        if (cls.equals("org.telegram.tgnet.ConnectionsManager")) {
            return bottom.getMethodName().startsWith("sendRequest");
        }
        return cls.startsWith("org.telegram.messenger.DevGramPlugin") || cls.startsWith("org.telegram.messenger.DevGramRequestUtils");
    }

    public static String getCategory(TLObject object) {
        return CATEGORIES.get(object.getClass().getSimpleName());
    }

    public static String getTitle(TLObject object) {
        String title = TITLES.get(object.getClass().getSimpleName());
        return title != null ? title : object.getClass().getSimpleName();
    }

    /** Показать окно подтверждения; ответ приходит на UI-потоке, закрытие окна — это отказ. */
    public static void confirm(int account, TLObject object, Result result) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                BaseFragment fragment = LaunchActivity.getLastFragment();
                Activity activity = fragment != null ? fragment.getParentActivity() : null;
                if (activity == null || activity.isFinishing()) {
                    result.run(false);
                    return;
                }
                new org.telegram.ui.Components.DevGramSensitiveRequestSheet(activity, fragment.getResourceProvider(), account, object, result).show();
            } catch (Exception e) {
                FileLog.e(e);
                result.run(false);
            }
        });
    }

    // ---- подробности для окна подтверждения ----

    private static final class Payment {
        final long amount;
        final String currency;
        final long created = android.os.SystemClock.elapsedRealtime();

        Payment(long amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }
    }

    private static final java.util.LinkedHashMap<String, Payment> payments = new java.util.LinkedHashMap<>();

    /** Запомнить сумму из ответа на запрос платёжной формы (по form_id), чтобы показать её при оплате. */
    public static synchronized void rememberPaymentForm(int account, TLObject response) {
        if (!(response instanceof org.telegram.tgnet.TLRPC.PaymentForm)) {
            return;
        }
        org.telegram.tgnet.TLRPC.PaymentForm form = (org.telegram.tgnet.TLRPC.PaymentForm) response;
        if (form.invoice == null || form.invoice.prices == null || form.invoice.prices.isEmpty()) {
            return;
        }
        long total = 0;
        try {
            for (org.telegram.tgnet.TLRPC.TL_labeledPrice price : form.invoice.prices) {
                if (price == null) return;
                total = Math.addExact(total, price.amount);
            }
        } catch (ArithmeticException e) {
            return;
        }
        payments.put(account + ":" + form.form_id, new Payment(total, form.invoice.currency));
        while (payments.size() > 128) {
            payments.remove(payments.keySet().iterator().next());
        }
    }

    private static synchronized Payment payment(int account, long formId) {
        String key = account + ":" + formId;
        Payment p = payments.get(key);
        if (p != null && android.os.SystemClock.elapsedRealtime() - p.created > 15 * 60_000) {
            payments.remove(key);
            return null;
        }
        return p;
    }

    private static String peerName(int account, long dialogId) {
        MessagesController mc = MessagesController.getInstance(account);
        if (dialogId > 0) {
            org.telegram.tgnet.TLRPC.User user = mc.getUser(dialogId);
            return user != null ? UserObject.getUserName(user) + (org.telegram.messenger.UserObject.getPublicUsername(user) != null ? " (@" + UserObject.getPublicUsername(user) + ")" : "") : "ID " + dialogId;
        }
        org.telegram.tgnet.TLRPC.Chat chat = mc.getChat(-dialogId);
        return chat != null ? chat.title : "ID " + (-dialogId);
    }

    private static String stars(long amount) {
        return LocaleController.formatNumber(amount, ',') + " ⭐";
    }

    private static String ton(long nano) {
        return java.math.BigDecimal.valueOf(nano, 9).stripTrailingZeros().toPlainString() + " TON";
    }

    private static String savedGift(org.telegram.tgnet.tl.TL_stars.InputSavedStarGift gift) {
        if (gift instanceof org.telegram.tgnet.tl.TL_stars.TL_inputSavedStarGiftUser) {
            return "ID " + ((org.telegram.tgnet.tl.TL_stars.TL_inputSavedStarGiftUser) gift).msg_id;
        } else if (gift instanceof org.telegram.tgnet.tl.TL_stars.TL_inputSavedStarGiftChat) {
            return "ID " + ((org.telegram.tgnet.tl.TL_stars.TL_inputSavedStarGiftChat) gift).saved_id;
        } else if (gift instanceof org.telegram.tgnet.tl.TL_stars.TL_inputSavedStarGiftSlug) {
            return ((org.telegram.tgnet.tl.TL_stars.TL_inputSavedStarGiftSlug) gift).slug;
        }
        return null;
    }

    /** Строки «название — значение» для окна: аккаунт, получатель, чат, сумма, подарок, запрос. */
    public static java.util.ArrayList<CharSequence[]> details(int account, TLObject req) {
        java.util.ArrayList<CharSequence[]> rows = new java.util.ArrayList<>();
        org.telegram.tgnet.TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        if (self != null) {
            rows.add(new CharSequence[]{"Аккаунт", peerName(account, self.id)});
        }
        try {
            if (req instanceof org.telegram.tgnet.tl.TL_stars.transferStarGift) {
                org.telegram.tgnet.tl.TL_stars.transferStarGift r = (org.telegram.tgnet.tl.TL_stars.transferStarGift) req;
                rows.add(new CharSequence[]{"Получатель", peerName(account, DialogObject.getPeerDialogId(r.to_id))});
                String g = savedGift(r.stargift);
                if (g != null) rows.add(new CharSequence[]{"Подарок", g});
            } else if (req instanceof org.telegram.tgnet.tl.TL_stars.updateStarGiftPrice) {
                org.telegram.tgnet.tl.TL_stars.updateStarGiftPrice r = (org.telegram.tgnet.tl.TL_stars.updateStarGiftPrice) req;
                String g = savedGift(r.stargift);
                if (g != null) rows.add(new CharSequence[]{"Подарок", g});
                if (r.resell_amount != null) rows.add(new CharSequence[]{"Цена", stars(r.resell_amount.amount)});
            } else if (req instanceof org.telegram.tgnet.tl.TL_stars.convertStarGift) {
                String g = savedGift(((org.telegram.tgnet.tl.TL_stars.convertStarGift) req).stargift);
                if (g != null) rows.add(new CharSequence[]{"Подарок", g});
            } else if (req instanceof org.telegram.tgnet.tl.TL_stars.upgradeStarGift) {
                String g = savedGift(((org.telegram.tgnet.tl.TL_stars.upgradeStarGift) req).stargift);
                if (g != null) rows.add(new CharSequence[]{"Подарок", g});
            } else if (req instanceof org.telegram.tgnet.tl.TL_stars.withdrawStarGift) {
                String g = savedGift(((org.telegram.tgnet.tl.TL_stars.withdrawStarGift) req).stargift);
                if (g != null) rows.add(new CharSequence[]{"Подарок", g});
            } else if (req instanceof org.telegram.tgnet.tl.TL_stars.deleteStarGift) {
                String g = savedGift(((org.telegram.tgnet.tl.TL_stars.deleteStarGift) req).stargift);
                if (g != null) rows.add(new CharSequence[]{"Подарок", g});
            } else if (req instanceof org.telegram.tgnet.tl.TL_payments.TL_sendStarGiftOffer) {
                org.telegram.tgnet.tl.TL_payments.TL_sendStarGiftOffer r = (org.telegram.tgnet.tl.TL_payments.TL_sendStarGiftOffer) req;
                rows.add(new CharSequence[]{"Получатель", peerName(account, DialogObject.getPeerDialogId(r.peer))});
                if (r.price != null) rows.add(new CharSequence[]{"Сумма", stars(r.price.amount)});
            } else if (req instanceof org.telegram.tgnet.TLRPC.TL_messages_sendPaidReaction) {
                org.telegram.tgnet.TLRPC.TL_messages_sendPaidReaction r = (org.telegram.tgnet.TLRPC.TL_messages_sendPaidReaction) req;
                rows.add(new CharSequence[]{"Чат", peerName(account, DialogObject.getPeerDialogId(r.peer))});
                rows.add(new CharSequence[]{"Сумма", stars(r.count)});
            } else if (req instanceof org.telegram.tgnet.tl.TL_stars.TL_payments_sendStarsForm || req instanceof org.telegram.tgnet.TLRPC.TL_payments_sendPaymentForm) {
                long formId = req instanceof org.telegram.tgnet.tl.TL_stars.TL_payments_sendStarsForm
                    ? ((org.telegram.tgnet.tl.TL_stars.TL_payments_sendStarsForm) req).form_id
                    : ((org.telegram.tgnet.TLRPC.TL_payments_sendPaymentForm) req).form_id;
                Payment p = payment(account, formId);
                String debit = "Не удалось определить сумму. Это не значит, что операция бесплатная.";
                if (p != null && "XTR".equals(p.currency)) {
                    debit = stars(p.amount);
                } else if (p != null && "TON".equals(p.currency)) {
                    debit = ton(p.amount);
                } else if (p != null) {
                    debit = BillingController.getInstance().formatCurrency(p.amount, p.currency);
                }
                rows.add(new CharSequence[]{"Будет списано", debit});
            } else if (req instanceof org.telegram.tgnet.TLRPC.TL_channels_editCreator) {
                org.telegram.tgnet.TLRPC.TL_channels_editCreator r = (org.telegram.tgnet.TLRPC.TL_channels_editCreator) req;
                rows.add(new CharSequence[]{"Чат", peerName(account, -r.channel.channel_id)});
                rows.add(new CharSequence[]{"Новый владелец", peerName(account, r.user_id.user_id)});
            } else if (req instanceof org.telegram.tgnet.TLRPC.TL_channels_editAdmin) {
                org.telegram.tgnet.TLRPC.TL_channels_editAdmin r = (org.telegram.tgnet.TLRPC.TL_channels_editAdmin) req;
                rows.add(new CharSequence[]{"Чат", peerName(account, -r.channel.channel_id)});
                rows.add(new CharSequence[]{"Пользователь", peerName(account, r.user_id.user_id)});
            } else if (req instanceof org.telegram.tgnet.TLRPC.TL_channels_deleteChannel) {
                rows.add(new CharSequence[]{"Чат", peerName(account, -((org.telegram.tgnet.TLRPC.TL_channels_deleteChannel) req).channel.channel_id)});
            } else if (req instanceof org.telegram.tgnet.TLRPC.TL_channels_updateUsername) {
                org.telegram.tgnet.TLRPC.TL_channels_updateUsername r = (org.telegram.tgnet.TLRPC.TL_channels_updateUsername) req;
                rows.add(new CharSequence[]{"Чат", peerName(account, -r.channel.channel_id)});
                rows.add(new CharSequence[]{"Новая ссылка", "@" + r.username});
            } else if (req instanceof org.telegram.tgnet.tl.TL_wallet.sendTransfer) {
                org.telegram.tgnet.tl.TL_wallet.sendTransfer r = (org.telegram.tgnet.tl.TL_wallet.sendTransfer) req;
                if (r.user_id != null) rows.add(new CharSequence[]{"Получатель", peerName(account, r.user_id.user_id)});
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        rows.add(new CharSequence[]{"Запрос", req.getClass().getSimpleName()});
        return rows;
    }
}

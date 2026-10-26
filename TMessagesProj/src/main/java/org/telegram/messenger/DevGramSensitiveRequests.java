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
}

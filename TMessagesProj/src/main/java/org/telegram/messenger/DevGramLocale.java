package org.telegram.messenger;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** Russian mod settings for Russian app locales, with English as the fallback for all others. */
public final class DevGramLocale {
    private DevGramLocale() { }

    public static boolean isRussian() {
        LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
        if (info == null || info.shortName == null) return false;
        String language = info.shortName.toLowerCase(Locale.ROOT).replace('_', '-');
        return language.equals("ru") || language.startsWith("ru-");
    }

    public static String text(String russian) {
        if (russian == null || isRussian()) return russian;
        String english = Translations.ENGLISH.get(russian);
        return english != null ? english : russian;
    }

    /** Use for interpolated strings, whose values cannot be dictionary keys. */
    public static String text(String russian, String english) {
        return isRussian() ? russian : english;
    }

    public static String getString(int resourceId) {
        if (isRussian()) {
            // These mod strings originally lived only in the default resources. Keep Russian
            // available even in builds that strip translated Android resource configurations.
            if (resourceId == R.string.DevGramDisguise) return "Маскировка";
            if (resourceId == R.string.DevGramDisguiseInfo) return "Подмените иконку и название приложения в списке приложений. Например, DevGram будет выглядеть как «Калькулятор».";
            if (resourceId == R.string.DevGramDisguiseNone) return "Без маскировки (DevGram)";
            if (resourceId == R.string.DevGramMaskCalculator) return "Калькулятор";
            if (resourceId == R.string.DevGramMaskNotes) return "Заметки";
            if (resourceId == R.string.DevGramMaskWeather) return "Погода";
            if (resourceId == R.string.DevGramMaskClock) return "Часы";
            if (resourceId == R.string.DevGramMaskSettings) return "Настройки";
            if (resourceId == R.string.DevGramAppIconSupSynthwave) return "Синтвейв";
            if (resourceId == R.string.DevGramAppIconSupMatrix) return "Матрица";
            if (resourceId == R.string.DevGramAppIconSupPixel) return "8-бит";
            if (resourceId == R.string.DevGramAppIconSupHolo) return "Голограмма";
            if (resourceId == R.string.DevGramAppIconSupGlitch) return "Глитч";
            if (resourceId == R.string.DevGramAppIconSupSpace) return "Космос";
            if (resourceId == R.string.DevGramAppIconSupTerminal) return "Терминал";
            if (resourceId == R.string.DevGramAppIconSupBlueprint) return "Чертёж";
            if (resourceId == R.string.DevGramAppIconSupLava) return "Лава-лампа";
            if (resourceId == R.string.DevGramAppIconSupComic) return "Комикс";
            if (resourceId == R.string.DevGramAppIconSupNeonSign) return "Неоновая вывеска";
            if (resourceId == R.string.DevGramAppIconSupMosaic) return "Мозаика";
            if (resourceId == R.string.DevGramAppIconSupMarble) return "Мрамор";
            if (resourceId == R.string.DevGramAppIconSupCrt) return "Ретро-ТВ";
            if (resourceId == R.string.DevGramAppIconSupHalloween) return "Хэллоуин";
            if (resourceId == R.string.DevGramAppIconSupGarland) return "Гирлянда";
            if (resourceId == R.string.DevGramCopyLink) return "Копировать ссылку";
            if (resourceId == R.string.DevGramShareLink) return "Поделиться ссылкой";
            if (resourceId == R.string.DevGramNoSuchSetting) return "Такой настройки нет";
            if (resourceId == R.string.DevGramLockedChats) return "Безопасность";
            if (resourceId == R.string.DevGramProfileActionsMore) return "Ещё";
            if (resourceId == R.string.DevGramLockedChatsInfo) return "Спрячьте выбранные чаты из списка. Они откроются только после разблокировки — по пасскоду или отпечатку/лицу.";
            if (resourceId == R.string.DevGramLockedChatsHide) return "Скрыть чат";
            if (resourceId == R.string.DevGramLockedChatsShow) return "Показать чат";
            if (resourceId == R.string.DevGramLockedChatsHidden) return "Чат скрыт";
            if (resourceId == R.string.DevGramLockedChatsShown) return "Чат снова виден";
            if (resourceId == R.string.DevGramLockedChatsUnlockHint) return "Разблокируйте, чтобы увидеть скрытые чаты";
            if (resourceId == R.string.DevGramLockedChatsUsePasscode) return "Ввести пасскод";
            if (resourceId == R.string.DevGramLockedChatsEnterPasscode) return "Пасскод";
            if (resourceId == R.string.DevGramLockedChatsWrongPasscode) return "Неверный пасскод";
            if (resourceId == R.string.DevGramLockedChatsPasscode) return "Пасскод";
            if (resourceId == R.string.DevGramLockedChatsSetPasscode) return "Задать пасскод";
            if (resourceId == R.string.DevGramLockedChatsChangePasscode) return "Сменить пасскод";
            if (resourceId == R.string.DevGramLockedChatsRemovePasscode) return "Убрать пасскод";
            if (resourceId == R.string.DevGramLockedChatsNewPasscode) return "Новый пасскод (мин. 4 цифры)";
            if (resourceId == R.string.DevGramLockedChatsRepeatPasscode) return "Повторите пасскод";
            if (resourceId == R.string.DevGramLockedChatsPasscodeShort) return "Пасскод должен быть не короче 4 цифр";
            if (resourceId == R.string.DevGramLockedChatsPasscodeMismatch) return "Пасскоды не совпадают";
            if (resourceId == R.string.DevGramLockedChatsPasscodeSaved) return "Пасскод сохранён";
            if (resourceId == R.string.DevGramLockedChatsBiometric) return "Разблокировка отпечатком/лицом";
            if (resourceId == R.string.DevGramLockedChatsHideNotify) return "Скрывать уведомления";
            if (resourceId == R.string.DevGramLockedChatsHideNotifyInfo) return "Не показывать уведомления от скрытых чатов, пока они заблокированы.";
            if (resourceId == R.string.DevGramLockedChatsList) return "Скрытые чаты";
            if (resourceId == R.string.DevGramLockedChatsEmpty) return "Пока нет скрытых чатов. Откройте чат → меню → «Скрыть чат».";
            if (resourceId == R.string.DevGramLockedChatsNeedPasscode) return "Сначала задайте пасскод для скрытых чатов";
            if (resourceId == R.string.DevGramLockedChatsReveal) return "Показать скрытые чаты в списке";
            if (resourceId == R.string.DevGramLockedChatsQuickShow) return "Показать скрытые чаты";
            if (resourceId == R.string.DevGramLockedChatsQuickHide) return "Скрыть скрытые чаты";
            if (resourceId == R.string.DevGramLockedChatsProtect) return "Отпечаток на вход";
            if (resourceId == R.string.DevGramLockedChatsUnprotect) return "Убрать отпечаток на вход";
            if (resourceId == R.string.DevGramLockedChatsProtected) return "Вход в чат — по отпечатку";
            if (resourceId == R.string.DevGramLockedChatsUnprotected) return "Отпечаток на вход снят";
            if (resourceId == R.string.DevGramLockedChatsTtl) return "Держать разблокировку";
            if (resourceId == R.string.DevGramLockedChatsTtlOnBackground) return "До сворачивания";
            if (resourceId == R.string.DevGramLockedChatsTtl1Min) return "1 минуту";
            if (resourceId == R.string.DevGramLockedChatsTtl5Min) return "5 минут";
            if (resourceId == R.string.DevGramLockedChatsTtl15Min) return "15 минут";
            if (resourceId == R.string.DevGramLockedChatsTtlUntilRestart) return "До перезапуска";
            if (resourceId == R.string.DevGramLockedChatsBioSection) return "Отпечаток на открытие";
            if (resourceId == R.string.DevGramLockedChatsBioArchive) return "Отпечаток на архив";
            if (resourceId == R.string.DevGramLockedChatsBioSaved) return "Отпечаток на «Избранное»";
            if (resourceId == R.string.DevGramLockedChatsBioSecret) return "Отпечаток на секретные чаты";
            if (resourceId == R.string.DevGramLockedChatsBioDelete) return "Отпечаток на удаление";
            if (resourceId == R.string.DevGramLockedChatsBioInfo) return "Спрашивать отпечаток/лицо при открытии выбранного и при удалении. Конкретные чаты помечаются в профиле → меню → «Отпечаток на вход».";
            return LocaleController.getString(resourceId);
        }
        // Telegram strings use generated IDs backed by localization assets, not Android resources.
        return LocaleController.getEnglishString(resourceId);
    }

    private static final class Translations {
        static final Map<String, String> ENGLISH = load();

        private static Map<String, String> load() {
            try (InputStream input = ApplicationLoader.applicationContext.getResources().openRawResource(R.raw.devgram_settings_en);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                JSONObject json = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
                Map<String, String> translations = new HashMap<>();
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    translations.put(key, json.getString(key));
                }
                return Collections.unmodifiableMap(translations);
            } catch (Exception e) {
                FileLog.e(e);
                return Collections.emptyMap();
            }
        }
    }
}

package org.telegram.messenger;

// DevGram: действия двойного нажатия — раздельно для входящих и исходящих сообщений (как у exteraGram).
// Индекс в списке настроек маппится на «канонический» ACTION_*, а тот — на пункт штатного меню в ChatActivity.
public abstract class DevGramDoubleTapUtils {

    public static final int ACTION_NONE = 0;
    public static final int ACTION_REACTIONS = 1;
    public static final int ACTION_REPLY = 2;
    public static final int ACTION_COPY = 3;
    public static final int ACTION_FORWARD = 4;
    public static final int ACTION_EDIT = 5;   // только для исходящих
    public static final int ACTION_SAVE = 6;
    public static final int ACTION_DELETE = 7;
    public static final int ACTION_TRANSLATE = 8;

    // Порядок пунктов в списке настроек (index -> ACTION_*). У исходящих добавлено «Изменить».
    private static final int[] IN = {
            ACTION_NONE, ACTION_REACTIONS, ACTION_REPLY, ACTION_COPY, ACTION_FORWARD, ACTION_SAVE, ACTION_DELETE, ACTION_TRANSLATE
    };
    private static final int[] OUT = {
            ACTION_NONE, ACTION_REACTIONS, ACTION_REPLY, ACTION_COPY, ACTION_FORWARD, ACTION_EDIT, ACTION_SAVE, ACTION_DELETE, ACTION_TRANSLATE
    };

    public static int[] order(boolean out) {
        return out ? OUT : IN;
    }

    public static int clampSetting(int setting, boolean out) {
        int len = order(out).length;
        if (setting < 0) return 0;
        return Math.min(setting, len - 1);
    }

    // ACTION_* по индексу настройки
    public static int actionFor(int setting, boolean out) {
        return order(out)[clampSetting(setting, out)];
    }

    // Текущее действие с учётом сохранённой настройки
    public static int resolveAction(boolean out) {
        int setting = out ? DevGramConfig.getDoubleTapActionOut() : DevGramConfig.getDoubleTapActionIn();
        return actionFor(setting, out);
    }

    public static CharSequence[] labels(boolean out) {
        int[] o = order(out);
        CharSequence[] r = new CharSequence[o.length];
        for (int i = 0; i < o.length; i++) {
            r[i] = label(o[i]);
        }
        return r;
    }

    public static CharSequence label(int action) {
        switch (action) {
            case ACTION_REACTIONS: return DevGramLocale.text("Реакции");
            case ACTION_REPLY: return DevGramLocale.text("Ответить");
            case ACTION_COPY: return DevGramLocale.text("Копировать");
            case ACTION_FORWARD: return DevGramLocale.text("Переслать");
            case ACTION_EDIT: return DevGramLocale.text("Изменить");
            case ACTION_SAVE: return DevGramLocale.text("Сохранить");
            case ACTION_DELETE: return DevGramLocale.text("Удалить");
            case ACTION_TRANSLATE: return DevGramLocale.text("Перевести");
            default: return DevGramLocale.text("Отключено");
        }
    }

    // Текущая подпись действия для стороны (для строки настроек)
    public static CharSequence currentLabel(boolean out) {
        return label(resolveAction(out));
    }

    public static int icon(int action) {
        switch (action) {
            case ACTION_REACTIONS: return R.drawable.msg2_reactions2;
            case ACTION_REPLY: return R.drawable.menu_reply;
            case ACTION_COPY: return R.drawable.msg_copy;
            case ACTION_FORWARD: return R.drawable.msg_forward;
            case ACTION_EDIT: return R.drawable.msg_edit;
            case ACTION_SAVE: return R.drawable.msg_saved;
            case ACTION_DELETE: return R.drawable.msg_delete;
            case ACTION_TRANSLATE: return R.drawable.msg_translate;
            default: return R.drawable.msg_block;
        }
    }

    // Иконка текущего действия для стороны (для превью)
    public static int currentIcon(boolean out) {
        return icon(resolveAction(out));
    }

    // Иконки для пунктов списка настроек (в порядке order)
    public static int[] icons(boolean out) {
        int[] o = order(out);
        int[] r = new int[o.length];
        for (int i = 0; i < o.length; i++) {
            r[i] = icon(o[i]);
        }
        return r;
    }
}

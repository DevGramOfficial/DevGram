package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Actions available in the message swipe selector. The persisted value is a comma-separated id list. */
public enum DevGramSwipeAction {
    REACTION(1, R.drawable.msg_reactions2, "Реакция", 1.0f),
    REPLY(2, R.drawable.menu_reply, "Ответить", 1.09f),
    COPY(3, R.drawable.msg_copy, "Копировать", 1.0f),
    FORWARD(4, R.drawable.msg_forward, "Переслать", 1.09f),
    EDIT(5, R.drawable.msg_edit, "Редактировать", 1.0f),
    SAVE(6, R.drawable.msg_saved, "В Избранное", 1.0f),
    REPEAT(7, R.drawable.msg_retry, "Повторить", 1.09f),
    DELETE(8, R.drawable.msg_delete, "Удалить", 1.09f),
    TRANSLATE(9, R.drawable.msg_translate, "Перевести", 1.09f);

    public static final String DEFAULT_ORDER = "1,2,3,4,5,6,7,8,9";

    public final int id;
    public final int iconRes;
    public final String title;
    public final float iconTrim;

    DevGramSwipeAction(int id, int iconRes, String title, float iconTrim) {
        this.id = id;
        this.iconRes = iconRes;
        this.title = title;
        this.iconTrim = iconTrim;
    }

    public static DevGramSwipeAction fromId(int id) {
        for (DevGramSwipeAction action : values()) {
            if (action.id == id) return action;
        }
        return null;
    }

    public static ArrayList<DevGramSwipeAction> parse(String value) {
        ArrayList<DevGramSwipeAction> result = new ArrayList<>();
        if (value == null) return result;
        for (String part : value.split(",")) {
            try {
                DevGramSwipeAction action = fromId(Integer.parseInt(part.trim()));
                if (action != null && !result.contains(action)) result.add(action);
            } catch (Throwable ignore) {
            }
        }
        return result;
    }

    public static String serialize(List<DevGramSwipeAction> actions) {
        StringBuilder result = new StringBuilder();
        for (DevGramSwipeAction action : actions) {
            if (result.length() > 0) result.append(',');
            result.append(action.id);
        }
        return result.toString();
    }

    public static ArrayList<DevGramSwipeAction> enabled() {
        return parse(DevGramConfig.swipeActions);
    }

    public static ArrayList<DevGramSwipeAction> disabled(List<DevGramSwipeAction> enabled) {
        ArrayList<DevGramSwipeAction> result = new ArrayList<>();
        Collections.addAll(result, values());
        result.removeAll(enabled);
        return result;
    }
}

package org.telegram.ui;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

// DevGram: deep-ссылки на настройки/категории (как t.me/exteraSettings у exteraGram).
// Формат: https://t.me/DevGramSettings?s=<код_экрана>[__<id_пункта>]
//   • ссылка на категорию/экран   → s=<код>            (открывает экран)
//   • ссылка на конкретный пункт   → s=<код>__<id>      (открывает экран + подсветка)
// Зажатие пункта в наших экранах → меню «Копировать ссылку» / «Поделиться ссылкой».
public final class DevGramSettingsLink {

    public static final String USERNAME = "DevGramSettings";

    // ждёт подсветки пункта после открытия экрана по ссылке (одноразово)
    public static volatile int pendingHighlight = 0;

    private DevGramSettingsLink() {}

    // ---------- построение ссылки ----------
    public static String build(String code, int itemId) {
        String s = code + (itemId > 0 ? "__" + itemId : "");
        return "https://t.me/" + USERNAME + "?s=" + s;
    }

    // ---------- открытие по параметру s= ----------
    public static boolean open(String s) {
        if (s == null || s.isEmpty()) return false;
        String code = s;
        int itemId = 0;
        int sep = s.indexOf("__");
        if (sep > 0) {
            code = s.substring(0, sep);
            try {
                itemId = Integer.parseInt(s.substring(sep + 2));
            } catch (Throwable ignore) {
            }
        }
        BaseFragment target = fragmentForCode(code);
        BaseFragment last = LaunchActivity.getLastFragment();
        if (target == null || last == null) {
            if (last != null) {
                BulletinFactory.of(last).createErrorBulletin(
                        LocaleController.getString(R.string.DevGramNoSuchSetting)).show();
            }
            return false;
        }
        pendingHighlight = itemId;
        last.presentFragment(target);
        return true;
    }

    private static BaseFragment fragmentForCode(String code) {
        switch (code) {
            case "main":       return new DevGramSettingsActivity();
            case "general":    return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_GENERAL);
            case "ghost":      return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_GHOST);
            case "spy":        return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_SPY);
            case "filters":    return new DevGramFiltersActivity();
            case "appearance": return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_APPEARANCE);
            case "chats":      return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_CHATS);
            case "ai":         return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_AI);
            case "other":      return new DevGramOtherActivity();
            case "locked":     return new DevGramLockedChatsActivity();
            default:           return null;
        }
    }

    // код экрана для категории DevGramCategoryActivity
    public static String codeForCategory(int category) {
        switch (category) {
            case DevGramCategoryActivity.CATEGORY_GHOST:      return "ghost";
            case DevGramCategoryActivity.CATEGORY_SPY:        return "spy";
            case DevGramCategoryActivity.CATEGORY_APPEARANCE: return "appearance";
            case DevGramCategoryActivity.CATEGORY_CHATS:      return "chats";
            case DevGramCategoryActivity.CATEGORY_AI:         return "ai";
            default:                                          return "general";
        }
    }

    // ---------- меню «копировать / поделиться» ----------
    public static boolean showLinkOptions(BaseFragment fragment, View anchor, String code, int itemId) {
        if (fragment == null || fragment.getParentActivity() == null) return false;
        final String link = build(code, itemId);
        ItemOptions.makeOptions(fragment, anchor)
                .add(R.drawable.msg_link2, LocaleController.getString(R.string.DevGramCopyLink), () -> {
                    AndroidUtilities.addToClipboard(link);
                    BulletinFactory.of(fragment).createCopyLinkBulletin().show();
                })
                .add(R.drawable.msg_share, LocaleController.getString(R.string.DevGramShareLink), () -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, link);
                        fragment.getParentActivity().startActivity(Intent.createChooser(intent,
                                LocaleController.getString(R.string.DevGramShareLink)));
                    } catch (Throwable ignore) {
                    }
                })
                .setGravity(Gravity.RIGHT)
                .show();
        return true;
    }

    // ---------- подсветка пункта после открытия по ссылке ----------
    public static void consumeHighlight(UniversalRecyclerView listView) {
        final int id = pendingHighlight;
        pendingHighlight = 0;
        if (id <= 0 || listView == null) return;
        AndroidUtilities.runOnUIThread(() -> {
            int pos = positionOf(listView, id);
            if (pos < 0) return;
            // сперва проматываем к пункту (highlightRow подсвечивает только видимые строки,
            // иначе анимация ждёт, пока пользователь сам долистает), затем подсвечиваем
            try {
                listView.layoutManager.scrollToPositionWithOffset(pos, AndroidUtilities.dp(80));
            } catch (Throwable ignore) {}
            AndroidUtilities.runOnUIThread(() -> listView.highlightRow(() -> positionOf(listView, id)), 160);
        }, 240);
    }

    private static int positionOf(UniversalRecyclerView listView, int id) {
        if (listView.adapter == null) return -1;
        for (int i = 0; i < listView.adapter.getItemCount(); i++) {
            UItem it = listView.adapter.getItem(i);
            if (it != null && it.id == id) return i;
        }
        return -1;
    }
}

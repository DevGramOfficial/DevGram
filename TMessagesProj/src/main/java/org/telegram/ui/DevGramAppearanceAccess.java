package org.telegram.ui;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramProfilePolicy;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ColoredImageSpan;

/** Locked rows stay visible and clickable, including inside option selectors. */
public final class DevGramAppearanceAccess {
    private DevGramAppearanceAccess() {}

    public static boolean allowed(BaseFragment fragment, String feature) {
        return feature == null || DevGramProfilePolicy.allowed(fragment.getUserConfig().getClientUserId(), feature);
    }

    public static CharSequence label(BaseFragment fragment, String feature, CharSequence text) {
        if (allowed(fragment, feature)) return text;
        SpannableStringBuilder result = new SpannableStringBuilder("  ");
        ColoredImageSpan lock = new ColoredImageSpan(R.drawable.msg_secret);
        lock.setSize(AndroidUtilities.dp(14));
        result.setSpan(lock, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.append(text);
        return result;
    }

    public static boolean check(BaseFragment fragment, String feature) {
        if (allowed(fragment, feature)) return true;
        if (fragment.getParentActivity() == null) return false;
        AlertDialog.Builder dialog = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        int tier = DevGramProfilePolicy.tier(feature);
        if (tier == 1) {
            dialog.setTitle(DevGramLocale.text("Эксклюзивно для поддержавших"));
            dialog.setMessage(DevGramLocale.text("Поддержите проект и получите больше эксклюзивных функций."));
            // Positive actions occupy the trailing/right side in the client's native dialog.
            dialog.setPositiveButton(DevGramLocale.text("Подробнее"), (d, w) -> DevGramSupportSheet.show(fragment));
        } else {
            dialog.setTitle(DevGramLocale.text(tier == 2 ? "Для разработчиков" : "Функция временно отключена"));
            dialog.setMessage(DevGramLocale.text(tier == 2 ? "Эта возможность доступна со значком с функционалом разработчика." : "Команда проекта временно отключила эту возможность."));
            dialog.setPositiveButton(DevGramLocale.text("Понятно"), null);
        }
        dialog.setNegativeButton(DevGramLocale.getString(R.string.Cancel), null);
        fragment.showDialog(dialog.create());
        return false;
    }
}

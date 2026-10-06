package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.DevGramColorTierRow;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Locale;

/**
 * DevGram: экран цвета точек «последнего захода» — по отдельному цвету на каждый из 4 уровней.
 * Для каждого уровня: палитра-пресеты + свотч «Своя» с вводом HEX (#RRGGBB). 0 = цвет по умолчанию.
 * Идея смены цвета — @katieska_2011.
 */
public class DevGramLastSeenColorsActivity extends DevGramSettingsFragment {

    // pref-ключи цвета по уровням (0 = по умолчанию)
    public static final String KEY_ONLINE = "lastSeenColorOnline";
    public static final String KEY_15 = "lastSeenColor15";
    public static final String KEY_30 = "lastSeenColor30";
    public static final String KEY_60 = "lastSeenColor60";

    // дефолтные цвета уровней (совпадают с константами DialogCell)
    public static final int DEF_15 = 0xFFEAEA1E;
    public static final int DEF_30 = 0xFFEA841E;
    public static final int DEF_60 = 0xFFEA1E1E;

    private static final int[] PALETTE = {
        0xFFEA1E1E, 0xFFEA841E, 0xFFEAEA1E, 0xFF4CAF50,
        0xFF2196F3, 0xFF9C27B0, 0xFF00BCD4, 0xFFE91E63
    };

    private UniversalRecyclerView listView;
    private final DevGramColorTierRow[] rows = new DevGramColorTierRow[4];

    private static final String[] TIER_TITLES = {
        "🟢 В сети", "🟡 Недавно (до 15 мин)", "🟠 Был 15–30 мин назад", "🔴 Был 30–60 мин назад"
    };
    private final String[] tierKeys = {KEY_ONLINE, KEY_15, KEY_30, KEY_60};
    private final int[] tierDefaults = new int[4];

    @Override
    public boolean onFragmentCreate() {
        tierDefaults[0] = Theme.getColor(Theme.key_chats_onlineCircle);
        tierDefaults[1] = DEF_15;
        tierDefaults[2] = DEF_30;
        tierDefaults[3] = DEF_60;
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Цвет точек");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        return fragmentView = content;
    }

    private int prefColor(String key) {
        return MessagesController.getGlobalMainSettings().getInt(key, 0);
    }

    private void setPrefColor(String key, int color) {
        MessagesController.getGlobalMainSettings().edit().putInt(key, color).apply();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow("Отдельный цвет точки для каждого статуса «был(а) недавно». "
                + "Нажми «Своя», чтобы ввести цвет в формате #RRGGBB."));
        for (int t = 0; t < 4; t++) {
            items.add(UItem.asHeader(TIER_TITLES[t]));
            if (rows[t] == null) {
                final int tier = t;
                rows[t] = new DevGramColorTierRow(getContext(), resourceProvider, PALETTE, tierDefaults[t], prefColor(tierKeys[t]));
                rows[t].setOnPick(swatch -> onPick(tier, swatch));
            } else {
                rows[t].setCurrentColor(prefColor(tierKeys[t]));
            }
            items.add(UItem.asCustom(1000 + t, rows[t]));
        }
        items.add(UItem.asShadow("«Авто» возвращает цвет по умолчанию для этого статуса."));
    }

    private void onPick(int tier, int swatch) {
        DevGramColorTierRow row = rows[tier];
        if (swatch == row.autoIndex()) {
            setPrefColor(tierKeys[tier], 0);
            row.setCurrentColor(0);
        } else if (swatch == row.customIndex()) {
            showHexDialog(tier);
            return;
        } else {
            int color = row.presetColor(swatch);
            setPrefColor(tierKeys[tier], color);
            row.setCurrentColor(color);
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        // клики обрабатываются самой строкой-палитрой
    }

    private void showHexDialog(int tier) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        int currentColor = prefColor(tierKeys[tier]);
        if (currentColor == 0) {
            currentColor = tierDefaults[tier];
        }

        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        edit.setHintText("#RRGGBB");
        edit.setHintColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
        edit.setBackgroundColor(0);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        edit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        edit.setCursorSize(AndroidUtilities.dp(20));
        edit.setCursorWidth(1.5f);
        edit.setText(String.format(Locale.US, "#%06X", currentColor & 0xFFFFFF));
        edit.setSelection(edit.length());

        final FrameLayout wrap = new FrameLayout(context);
        wrap.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        // предпросмотр как валидный HEX — сразу нормализуем #-префикс
        edit.addTextChangedListener(new TextWatcher() {
            boolean ignore;
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (ignore) return;
                String v = s.toString();
                if (!v.startsWith("#")) {
                    ignore = true;
                    edit.setText("#" + v.replace("#", ""));
                    edit.setSelection(edit.length());
                    ignore = false;
                }
            }
        });

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Свой цвет (HEX)");
        b.setView(wrap);
        b.setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
            Integer parsed = parseHex(edit.getText().toString());
            if (parsed == null) {
                if (getParentActivity() != null) {
                    org.telegram.ui.Components.BulletinFactory.of(this)
                            .createErrorBulletin("Неверный HEX. Пример: #FFAA00").show();
                }
                return;
            }
            setPrefColor(tierKeys[tier], parsed);
            rows[tier].setCurrentColor(parsed);
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        showDialog(dialog);
        edit.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(edit), 100);
    }

    // Парсинг #RGB / #RRGGBB → ARGB (непрозрачный). null при ошибке.
    private static Integer parseHex(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim().replace("#", "");
        if (s.length() == 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                sb.append(s.charAt(i)).append(s.charAt(i));
            }
            s = sb.toString();
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            return 0xFF000000 | (int) (Long.parseLong(s, 16) & 0xFFFFFF);
        } catch (Throwable e) {
            return null;
        }
    }
}

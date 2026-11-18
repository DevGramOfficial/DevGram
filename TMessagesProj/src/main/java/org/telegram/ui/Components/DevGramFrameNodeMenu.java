package org.telegram.ui.Components;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.devgram.frame.FrameNodeType;

/** The reference's centered, searchable node catalog with accordion categories. */
public final class DevGramFrameNodeMenu {
    private final BaseFragment host;
    private final IntPredicate allow;
    private final IntConsumer pick;
    private final LinearLayout list;
    private final EditText search;
    private final Dialog dialog;
    private int opened = FrameNodeType.CAT_SHAPE;
    private static final int BACK = 0xff1b1b21, INK = 0xffefeff5, DIM = 0xff9a9aaa;
    private static final int ACCENT = 0xff3390ec, LINE = 0x22ffffff;

    public static void show(BaseFragment host, IntPredicate allow, IntConsumer pick) {
        if (host.getParentActivity() != null) new DevGramFrameNodeMenu(host, allow, pick).show();
    }

    private DevGramFrameNodeMenu(BaseFragment host, IntPredicate allow, IntConsumer pick) {
        this.host = host;
        this.allow = allow;
        this.pick = pick;
        Context context = host.getParentActivity();
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(6), 0, dp(6));
        GradientDrawable background = new GradientDrawable();
        background.setColor(BACK);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), LINE);
        body.setBackground(background);
        body.setFocusableInTouchMode(true);
        LinearLayout searchRow = new LinearLayout(context);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(14), dp(2), dp(14), dp(6));
        android.widget.ImageView icon = new android.widget.ImageView(context);
        icon.setImageResource(org.telegram.messenger.R.drawable.msg_search);
        icon.setColorFilter(DIM);
        searchRow.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        search = new EditText(context);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setTextSize(15);
        search.setTextColor(INK);
        search.setHintTextColor(DIM);
        search.setHint(t("Поиск узла"));
        search.setPadding(dp(8), dp(6), 0, dp(6));
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1));
        body.addView(searchRow);
        android.view.View divider = new android.view.View(context);
        divider.setBackgroundColor(LINE);
        body.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        scroll.addView(list);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(body);
        dialog.setCanceledOnTouchOutside(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { fill(); }
            public void afterTextChanged(Editable value) {}
        });
        fill();
    }

    private void show() {
        host.showDialog(dialog);
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(0));
        window.setGravity(Gravity.CENTER);
        window.setLayout(Math.round(AndroidUtilities.displaySize.x * .86f), Math.round(AndroidUtilities.displaySize.y * .70f));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(host.getParentActivity());
        view.setText(t(value));
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void fill() {
        list.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (int category = 0; category < FrameNodeType.CATEGORIES.length; category++) {
            ArrayList<FrameNodeType.Kind> matches = new ArrayList<>();
            for (FrameNodeType.Kind kind : FrameNodeType.all()) {
                if (kind.category == category && allow.test(kind.type)
                        && (query.isEmpty() || (t(kind.name) + " " + t(kind.hint)).toLowerCase(Locale.ROOT).contains(query))) matches.add(kind);
            }
            if (matches.isEmpty()) continue;
            boolean expanded = !query.isEmpty() || opened == category;
            final int index = category;
            TextView heading = text((expanded ? "▾  " : "▸  ") + t(FrameNodeType.CATEGORIES[category]), 15, expanded ? ACCENT : INK);
            heading.setTypeface(Typeface.DEFAULT_BOLD);
            heading.setPadding(dp(14), dp(11), dp(14), dp(11));
            heading.setOnClickListener(v -> { opened = opened == index ? -1 : index; fill(); });
            list.addView(heading);
            if (!expanded) continue;
            for (FrameNodeType.Kind kind : matches) {
                LinearLayout row = new LinearLayout(host.getParentActivity());
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(32), dp(9), dp(16), dp(9));
                TextView name = text(kind.name, 15, INK), hint = text(kind.hint, 12, DIM);
                row.addView(name);
                row.addView(hint);
                row.setBackground(org.telegram.ui.ActionBar.Theme.createSelectorDrawable(0x223390ec, 2));
                row.setOnClickListener(v -> { dialog.dismiss(); pick.accept(kind.type); });
                row.setContentDescription(name.getText() + ". " + hint.getText());
                list.addView(row);
            }
        }
        if (list.getChildCount() == 0) {
            TextView empty = text("Ничего не нашлось", 14, DIM);
            empty.setPadding(dp(18), dp(14), dp(18), dp(14));
            list.addView(empty);
        }
    }

    private static String t(String value) { return DevGramLocale.text(value); }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
}

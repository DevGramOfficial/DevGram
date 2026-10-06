package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Роли ИИ (повтор RolesActivity exteraGram): предложенные роли + свои. Выбранная роль = системный промпт. */
public class DevGramAiRolesActivity extends BaseFragment {
    private static final int MENU_ADD = 1;
    private static final int ID_BASE_SUGGESTED = 1000;
    private static final int ID_BASE_CUSTOM = 2000;

    // Предложенные роли (как у exteraGram).
    private static final String[][] SUGGESTED = {
            {"Assistant", "You are a helpful personal assistant integrated into Telegram. Answer concisely and helpfully."},
            {"Summarizer", "Summarize the provided message or conversation clearly and concisely."},
            {"Proofreader", "Proofread and improve the user's text. Reply only with the corrected text."},
    };

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Роли");
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_ADD, R.drawable.msg_add);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_ADD) showNewRoleDialog();
            }
        });

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        return fragmentView = content;
    }

    private static String selectedRole() {
        return MessagesController.getGlobalMainSettings().getString("dg_aiRole", "Assistant");
    }

    private static JSONArray customRoles() {
        try {
            return new JSONArray(MessagesController.getGlobalMainSettings().getString("dg_aiCustomRoles", "[]"));
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    private static String preview(String prompt) {
        if (prompt == null || prompt.isEmpty()) return "Стандартный ассистент";
        return prompt.length() > 60 ? prompt.substring(0, 60) + "…" : prompt;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView("Роли", "Создавайте роли для ваших нужд!", "RestrictedEmoji", "🎭"));
        items.add(UItem.asShadow(null));

        final String sel = selectedRole();
        items.add(UItem.asHeader("Предложения"));
        for (int i = 0; i < SUGGESTED.length; i++) {
            items.add(UItem.asRadio2(ID_BASE_SUGGESTED + i, SUGGESTED[i][0], preview(SUGGESTED[i][1]))
                    .setChecked(sel.equals(SUGGESTED[i][0])));
        }

        JSONArray custom = customRoles();
        if (custom.length() > 0) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader("Мои роли"));
            for (int i = 0; i < custom.length(); i++) {
                JSONObject r = custom.optJSONObject(i);
                if (r == null) continue;
                items.add(UItem.asRadio2(ID_BASE_CUSTOM + i, r.optString("name"), preview(r.optString("prompt")))
                        .setChecked(sel.equals(r.optString("name"))));
            }
            items.add(UItem.asShadow("Долгое нажатие — удалить свою роль."));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_BASE_SUGGESTED && item.id < ID_BASE_CUSTOM) {
            int i = item.id - ID_BASE_SUGGESTED;
            selectRole(SUGGESTED[i][0], SUGGESTED[i][1]);
        } else if (item.id >= ID_BASE_CUSTOM) {
            int i = item.id - ID_BASE_CUSTOM;
            JSONObject r = customRoles().optJSONObject(i);
            if (r != null) selectRole(r.optString("name"), r.optString("prompt"));
        }
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < ID_BASE_CUSTOM) {
            return false;
        }
        final int i = item.id - ID_BASE_CUSTOM;
        final JSONArray arr = customRoles();
        final JSONObject r = arr.optJSONObject(i);
        if (r == null || getParentActivity() == null) {
            return false;
        }
        final String name = r.optString("name");
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Удалить роль?");
        b.setMessage(name);
        b.setPositiveButton("Удалить", (d, w) -> {
            arr.remove(i);
            MessagesController.getGlobalMainSettings().edit().putString("dg_aiCustomRoles", arr.toString()).apply();
            if (selectedRole().equals(name)) {
                MessagesController.getGlobalMainSettings().edit()
                        .putString("dg_aiRole", "Assistant").putString("dg_aiSystemPrompt", "").apply();
            }
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        });
        b.setNegativeButton("Отмена", null);
        showDialog(b.create());
        return true;
    }

    private void selectRole(String name, String prompt) {
        MessagesController.getGlobalMainSettings().edit()
                .putString("dg_aiRole", name)
                .putString("dg_aiSystemPrompt", prompt == null ? "" : prompt)
                .apply();
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }

    private void showNewRoleDialog() {
        if (getParentActivity() == null) return;
        android.widget.LinearLayout box = new android.widget.LinearLayout(getParentActivity());
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(20);
        box.setPadding(pad, 0, pad, 0);
        android.widget.EditText name = new android.widget.EditText(getParentActivity());
        name.setHint("Название");
        name.setSingleLine(true);
        name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        name.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        box.addView(name, new android.widget.LinearLayout.LayoutParams(-1, -2));
        android.widget.EditText prompt = new android.widget.EditText(getParentActivity());
        prompt.setHint("Промпт");
        prompt.setMinLines(2);
        prompt.setMaxLines(6);
        prompt.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        prompt.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        box.addView(prompt, new android.widget.LinearLayout.LayoutParams(-1, -2));

        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Новая роль");
        b.setView(box);
        b.setPositiveButton("Создать", (d, w) -> {
            String n = name.getText().toString().trim();
            String p = prompt.getText().toString().trim();
            if (n.isEmpty()) return;
            JSONArray arr = customRoles();
            try { arr.put(new JSONObject().put("name", n).put("prompt", p)); } catch (Throwable ignore) {}
            MessagesController.getGlobalMainSettings().edit().putString("dg_aiCustomRoles", arr.toString()).apply();
            selectRole(n, p);
        });
        b.setNegativeButton("Отмена", null);
        showDialog(b.create());
    }
}

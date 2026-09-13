package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DevGramGhostSettings;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Per-dialog read and typing overrides for Ghost Mode. */
public class DevGramGhostExceptionsActivity extends BaseFragment {
    private static final int BASE_ID = 24000;
    private final ArrayList<Long> dialogIds = new ArrayList<>();
    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Исключения для чатов");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        reloadDialogs();
        FrameLayout content = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = content;
    }

    private void reloadDialogs() {
        dialogIds.clear();
        ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getAllDialogs();
        for (int i = 0; i < dialogs.size(); i++) {
            long id = dialogs.get(i).id;
            if (id != 0 && !DialogObject.isFolderDialogId(id)) dialogIds.add(id);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Прочтение и набор текста"));
        for (int i = 0; i < dialogIds.size(); i++) {
            long did = dialogIds.get(i);
            items.add(UItem.asButton(BASE_ID + i, title(did), summary(did)));
        }
        items.add(UItem.asShadow("Для каждого чата можно оставить общее поведение, всегда скрывать активность или всегда отправлять её."));
    }

    private String title(long did) {
        if (did > 0) {
            TLRPC.User user = getMessagesController().getUser(did);
            return user == null ? String.valueOf(did) : UserObject.getUserName(user);
        }
        TLRPC.Chat chat = getMessagesController().getChat(-did);
        return chat == null ? String.valueOf(did) : chat.title;
    }

    private static String mode(int value) {
        return value == 1 ? "никогда" : value == 2 ? "всегда" : "по умолчанию";
    }

    private String summary(long did) {
        int read = DevGramGhostSettings.getDialogOverride(currentAccount, did, "read");
        int typing = DevGramGhostSettings.getDialogOverride(currentAccount, did, "typing");
        return "Прочтение: " + mode(read) + " · Набор: " + mode(typing);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        int index = item.id - BASE_ID;
        if (index < 0 || index >= dialogIds.size()) return;
        long did = dialogIds.get(index);
        CharSequence[] choices = {
                "Прочтение — по умолчанию", "Прочтение — никогда", "Прочтение — всегда",
                "Набор текста — по умолчанию", "Набор текста — никогда", "Набор текста — всегда"
        };
        new AlertDialog.Builder(getParentActivity())
                .setTitle(title(did))
                .setItems(choices, (dialog, which) -> {
                    if (which < 3) DevGramGhostSettings.setDialogOverride(currentAccount, did, "read", which);
                    else DevGramGhostSettings.setDialogOverride(currentAccount, did, "typing", which - 3);
                    if (listView != null) listView.adapter.update(true);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}

package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.DevGramAppearancePreview;
import org.telegram.ui.Components.DevGramBadgePickerView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

// DevGram: выбор значка у ника для поддержавшего — живое превью сверху и значки, предложенные командой.
public class DevGramBadgeChoiceActivity extends DevGramSettingsFragment {

    private UniversalRecyclerView listView;
    private DevGramAppearancePreview preview;
    private DevGramBadgePickerView picker;
    private ArrayList<DevGramBadges.Option> options;
    private boolean saving;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Значок у ника");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        preview = new DevGramAppearancePreview(context, currentAccount, resourceProvider);
        picker = new DevGramBadgePickerView(context, currentAccount, resourceProvider);
        picker.setOnSelect(this::choose);
        listView = new UniversalRecyclerView(this, this::fillItems, null, null);
        DevGramBadges.fetchOptions(list -> {
            options = list;
            refreshPicker();
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        });
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(preview));
        items.add(UItem.asShadow("Значок рядом с ником видят все пользователи DevGram."));
        items.add(UItem.asHeader("Значок"));
        if (options == null) {
            items.add(UItem.asShadow("Загружаю значки…"));
        } else if (options.isEmpty()) {
            items.add(UItem.asShadow("Скоро здесь появятся значки на выбор."));
        } else {
            items.add(UItem.asCustom(picker));
            items.add(UItem.asShadow("Первый — значок, выданный командой. К нему можно вернуться в любой момент."));
        }
    }

    private long original() {
        DevGramBadges.Badge b = DevGramBadges.badgeOf(getUserConfig().getClientUserId());
        return b != null ? b.originalEmojiId : DevGramBadges.EMOJI_SUPPORTER;
    }

    private void refreshPicker() {
        if (picker == null || options == null) return;
        DevGramBadges.Badge b = DevGramBadges.badgeOf(getUserConfig().getClientUserId());
        long original = original();
        picker.setData(original, b != null ? b.emojiId : original, options);
        preview.setBadge(b != null ? b.emojiId : original);
    }

    private void choose(long emojiId) {
        if (saving) return;
        saving = true;
        picker.setSelected(emojiId);
        preview.setBadge(emojiId == 0 ? original() : emojiId);
        DevGramBadges.updateSelf(currentAccount, emojiId, null, (ok, error) -> {
            saving = false;
            refreshPicker();
            if (ok) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Значок обновлён").show();
            } else {
                BulletinFactory.of(this).createErrorBulletin(error != null ? error : "Не удалось сменить значок").show();
            }
        });
    }
}

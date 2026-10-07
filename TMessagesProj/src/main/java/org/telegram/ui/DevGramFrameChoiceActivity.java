package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.DevGramAppearancePreview;
import org.telegram.ui.Components.DevGramFramePickerView;
import org.telegram.ui.Components.DevGramMessageFrame;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

// DevGram: выбор обводки сообщений поддержавшего — живое превью сверху и сетка стилей.
public class DevGramFrameChoiceActivity extends DevGramSettingsFragment {

    private UniversalRecyclerView listView;
    private DevGramAppearancePreview preview;
    private DevGramFramePickerView picker;
    private boolean saving;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Обводка сообщений");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        preview = new DevGramAppearancePreview(context, currentAccount, resourceProvider);
        picker = new DevGramFramePickerView(context, resourceProvider);
        picker.setSelected(DevGramBadges.borderOf(getUserConfig().getClientUserId()));
        picker.setOnSelect(this::choose);
        listView = new UniversalRecyclerView(this, this::fillItems, null, null);
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(preview));
        items.add(UItem.asShadow("Так ваши сообщения видят все пользователи DevGram."));
        items.add(UItem.asHeader("Стиль"));
        items.add(UItem.asCustom(picker));
        items.add(UItem.asShadow(null));
    }

    private void choose(int style) {
        if (saving) return;
        saving = true;
        int previous = DevGramBadges.borderOf(getUserConfig().getClientUserId());
        picker.setSelected(style);
        preview.setFrame(style);
        DevGramBadges.updateSelf(currentAccount, null, style, (ok, error) -> {
            saving = false;
            if (ok) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                        style == 0 ? "Обводка убрана" : "Обводка «" + DevGramMessageFrame.get(style).title + "» включена").show();
            } else {
                picker.setSelected(previous);
                preview.setFrame(previous);
                BulletinFactory.of(this).createErrorBulletin(error != null ? error : "Не удалось сменить обводку").show();
            }
        });
    }
}

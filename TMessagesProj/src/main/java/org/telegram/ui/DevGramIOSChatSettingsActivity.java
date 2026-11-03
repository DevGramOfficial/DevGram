package org.telegram.ui;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.view.View;

import org.telegram.messenger.DevGramIOSConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Components.DevGramIOSChatHeaderPreviewView;
import org.telegram.ui.Components.DevGramIOSChatInputPreviewView;
import org.telegram.ui.Components.DevGramSettingsLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Cherrygram-compatible iOS chat appearance settings with live previews. */
public class DevGramIOSChatSettingsActivity extends DevGramSettingsFragment {
    private static final int ID_HEADER = 1;
    private static final int ID_CENTER_TITLE = 2;
    private static final int ID_ADAPTIVE_WIDTH = 3;
    private static final int ID_UNREAD_COUNTER = 4;
    private static final int ID_IOS_COUNTER = 5;
    private static final int ID_INPUT = 10;
    private static final int ID_IOS_INPUT = 11;
    private static final int ID_SOUND = 20;
    private static final int ID_MENU = 30;
    private static final int ID_IOS_MENU = 31;
    private static final int ID_NATIVE_BLUR = 32;
    private static final int ID_WHOLE_ALBUM = 33;
    private static final int ID_REMEMBER_KEYBOARD = 34;
    private static final int ID_COMPACT_ACTIONS = 35;
    private static final int ID_IOS_APP_DESIGN = 40;
    private static final int ID_IOS_PROFILE = 41;

    private UniversalRecyclerView listView;
    private DevGramIOSChatHeaderPreviewView headerPreview;
    private DevGramIOSChatInputPreviewView inputPreview;
    private boolean headerExpanded;
    private boolean inputExpanded;
    private boolean menuExpanded;
    private MediaPlayer previewPlayer;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("iOS");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        return fragmentView = new DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        adapter.whiteSectionStart();
        items.add(UItem.asHeader("Интерфейс iOS"));
        items.add(UItem.asCheck(ID_IOS_APP_DESIGN, "Оформление приложения в стиле iOS")
                .setChecked(org.telegram.messenger.DevGramConfig.getDesignMode()
                        == org.telegram.messenger.DevGramConfig.DESIGN_MODE_IOS));
        items.add(UItem.asRoundCheckbox(ID_IOS_PROFILE, "Профиль в стиле iOS")
                .setChecked(org.telegram.messenger.DevGramConfig.iosProfile).pad());
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow("Пресет включает iOS-навигацию, группировку настроек, круглые аватарки и все параметры чата ниже."));

        adapter.whiteSectionStart();
        items.add(UItem.asExpandableSwitch(ID_HEADER, "Шапка чата", " ")
                .setChecked(false)
                .setCollapsed(!headerExpanded)
                .setClickCallback(v -> {
                    headerExpanded = !headerExpanded;
                    updatePreviews();
                })
                .hideCheckbox(true));
        if (headerExpanded) {
            items.add(UItem.asShadow(null));
            headerPreview = new DevGramIOSChatHeaderPreviewView(getContext(), this, resourceProvider);
            items.add(UItem.asCustomWithBackground(headerPreview, 70));
            items.add(UItem.asShadow(null));
            items.add(UItem.asRoundCheckbox(ID_CENTER_TITLE, "Заголовок по центру")
                    .setChecked(DevGramIOSConfig.centerChatTitle()).pad());
            if (DevGramIOSConfig.centerChatTitle()) {
                items.add(UItem.asRoundCheckbox(ID_ADAPTIVE_WIDTH, "Адаптивная ширина заголовка")
                        .setChecked(DevGramIOSConfig.adaptiveTitleWidth()).pad());
            }
            items.add(UItem.asRoundCheckbox(ID_UNREAD_COUNTER, "Показывать счётчик непрочитанных")
                    .setChecked(DevGramIOSConfig.showUnreadCounter()).pad());
            if (DevGramIOSConfig.centerChatTitle() && DevGramIOSConfig.showUnreadCounter()) {
                items.add(UItem.asRoundCheckbox(ID_IOS_COUNTER, "Счётчик в стиле iOS")
                        .setChecked(DevGramIOSConfig.iosUnreadCounter()).pad());
            }
        }
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow("Заголовок остаётся по центру и автоматически освобождает место под кнопку назад, счётчик и аватар."));

        adapter.whiteSectionStart();
        items.add(UItem.asExpandableSwitch(ID_INPUT, "Нижняя панель в чате", " ")
                .setChecked(false)
                .setCollapsed(!inputExpanded)
                .setClickCallback(v -> {
                    inputExpanded = !inputExpanded;
                    updatePreviews();
                })
                .hideCheckbox(true));
        if (inputExpanded) {
            items.add(UItem.asShadow(null));
            inputPreview = new DevGramIOSChatInputPreviewView(getContext(), this, resourceProvider);
            items.add(UItem.asCustomWithBackground(inputPreview, 62));
            items.add(UItem.asShadow(null));
            items.add(UItem.asRoundCheckbox(ID_IOS_INPUT, "Поле ввода в стиле iOS")
                    .setChecked(DevGramIOSConfig.iosInputPanel()).pad());
        }
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow("Кнопки вложения и отправки становятся отдельными стеклянными островками, как в Cherrygram."));

        adapter.whiteSectionStart();
        items.add(UItem.asHeader("Уведомления"));
        items.add(UItem.asButton(ID_SOUND, "Звук сообщения в открытом чате", soundLabel()));
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow("Звук iOS воспроизводится только для входящего сообщения, когда чат открыт."));

        adapter.whiteSectionStart();
        items.add(UItem.asExpandableSwitch(ID_MENU, "Меню сообщения", " ")
                .setChecked(false)
                .setCollapsed(!menuExpanded)
                .setClickCallback(v -> {
                    menuExpanded = !menuExpanded;
                    updatePreviews();
                })
                .hideCheckbox(true));
        if (menuExpanded) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asRoundCheckbox(ID_IOS_MENU, "Меню в стиле iOS")
                    .setChecked(DevGramIOSConfig.iosMessageMenu()).pad());
            if (DevGramIOSConfig.iosMessageMenu()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    items.add(UItem.asRoundCheckbox(ID_NATIVE_BLUR, "Системное размытие")
                            .setChecked(DevGramIOSConfig.nativeMenuBlur()).pad());
                }
                items.add(UItem.asRoundCheckbox(ID_WHOLE_ALBUM, "Показывать весь альбом")
                        .setChecked(DevGramIOSConfig.showWholeAlbumInMenu()).pad());
                items.add(UItem.asRoundCheckbox(ID_REMEMBER_KEYBOARD, "Запоминать состояние клавиатуры")
                        .setChecked(DevGramIOSConfig.rememberKeyboardForMenu()).pad());
                items.add(UItem.asRoundCheckbox(ID_COMPACT_ACTIONS, "Компактные действия")
                        .setChecked(DevGramIOSConfig.compactMenuActions()).pad());
            }
        }
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow("Меню сохраняет положение клавиатуры, поддерживает альбомы и использует iOS-анимацию появления."));
    }

    private String soundLabel() {
        switch (DevGramIOSConfig.notificationSound()) {
            case DevGramIOSConfig.NOTIFICATION_SOUND_DISABLED:
                return "Отключён";
            case DevGramIOSConfig.NOTIFICATION_SOUND_TELEGRAM:
                return "Telegram";
            default:
                return "iOS";
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_IOS_APP_DESIGN:
                boolean enableIos = org.telegram.messenger.DevGramConfig.getDesignMode()
                        != org.telegram.messenger.DevGramConfig.DESIGN_MODE_IOS;
                org.telegram.messenger.DevGramConfig.applyDesignPreset(enableIos
                        ? org.telegram.messenger.DevGramConfig.DESIGN_MODE_IOS
                        : org.telegram.messenger.DevGramConfig.DESIGN_MODE_DEVGRAM);
                break;
            case ID_IOS_PROFILE:
                org.telegram.messenger.DevGramConfig.setIosProfile(!org.telegram.messenger.DevGramConfig.iosProfile);
                break;
            case ID_HEADER:
                headerExpanded = !headerExpanded;
                break;
            case ID_CENTER_TITLE:
                DevGramIOSConfig.setCenterChatTitle(!DevGramIOSConfig.centerChatTitle());
                break;
            case ID_ADAPTIVE_WIDTH:
                DevGramIOSConfig.setAdaptiveTitleWidth(!DevGramIOSConfig.adaptiveTitleWidth());
                break;
            case ID_UNREAD_COUNTER:
                DevGramIOSConfig.setShowUnreadCounter(!DevGramIOSConfig.showUnreadCounter());
                break;
            case ID_IOS_COUNTER:
                DevGramIOSConfig.setIosUnreadCounter(!DevGramIOSConfig.iosUnreadCounter());
                break;
            case ID_INPUT:
                inputExpanded = !inputExpanded;
                break;
            case ID_IOS_INPUT:
                DevGramIOSConfig.setIosInputPanel(!DevGramIOSConfig.iosInputPanel());
                break;
            case ID_SOUND:
                showSoundPicker();
                return;
            case ID_MENU:
                menuExpanded = !menuExpanded;
                break;
            case ID_IOS_MENU:
                DevGramIOSConfig.setIosMessageMenu(!DevGramIOSConfig.iosMessageMenu());
                break;
            case ID_NATIVE_BLUR:
                DevGramIOSConfig.setNativeMenuBlur(!DevGramIOSConfig.nativeMenuBlur());
                break;
            case ID_WHOLE_ALBUM:
                DevGramIOSConfig.setShowWholeAlbumInMenu(!DevGramIOSConfig.showWholeAlbumInMenu());
                break;
            case ID_REMEMBER_KEYBOARD:
                DevGramIOSConfig.setRememberKeyboardForMenu(!DevGramIOSConfig.rememberKeyboardForMenu());
                break;
            case ID_COMPACT_ACTIONS:
                DevGramIOSConfig.setCompactMenuActions(!DevGramIOSConfig.compactMenuActions());
                break;
            default:
                return;
        }
        updatePreviews();
    }

    private void updatePreviews() {
        if (headerPreview != null) headerPreview.refresh();
        if (inputPreview != null) inputPreview.invalidate();
        if (listView != null) listView.adapter.update(true);
    }

    private void showSoundPicker() {
        if (getParentActivity() == null) return;
        String[] options = {"Отключён", "Telegram", "iOS"};
        int selected = DevGramIOSConfig.notificationSound();
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = (i == selected ? "• " : "    ") + options[i];
        }
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                .setTitle("Звук сообщения")
                .setItems(labels, (dialog, which) -> {
                    DevGramIOSConfig.setNotificationSound(which);
                    playSoundPreview(which);
                    updatePreviews();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void playSoundPreview(int sound) {
        if (sound == DevGramIOSConfig.NOTIFICATION_SOUND_DISABLED || getParentActivity() == null) return;
        if (previewPlayer != null) {
            previewPlayer.release();
            previewPlayer = null;
        }
        previewPlayer = MediaPlayer.create(getParentActivity(),
                sound == DevGramIOSConfig.NOTIFICATION_SOUND_IOS ? R.raw.sound_in_ios : R.raw.sound_in);
        if (previewPlayer != null) {
            previewPlayer.setOnCompletionListener(player -> {
                player.release();
                if (previewPlayer == player) previewPlayer = null;
            });
            previewPlayer.start();
        }
    }

    @Override
    public void onFragmentDestroy() {
        if (previewPlayer != null) {
            previewPlayer.release();
            previewPlayer = null;
        }
        super.onFragmentDestroy();
    }
}

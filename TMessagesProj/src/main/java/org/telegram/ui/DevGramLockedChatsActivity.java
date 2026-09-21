package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.biometric.BiometricManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DevGramLockedChats;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

// DevGram: экран управления скрытыми (запароленными) чатами — пасскод, биометрия,
// уведомления и список скрытых чатов. Открывается только после разблокировки.
public class DevGramLockedChatsActivity extends BaseFragment {

    private static final int ID_SET_PASSCODE = 1;
    private static final int ID_REMOVE_PASSCODE = 2;
    private static final int ID_BIOMETRIC = 3;
    private static final int ID_HIDE_NOTIFY = 4;
    private static final int ID_REVEAL = 5;
    private static final int ID_TTL = 6;
    private static final int ID_BIO_ARCHIVE = 7;
    private static final int ID_BIO_SAVED = 8;
    private static final int ID_BIO_SECRET = 9;
    private static final int ID_BIO_DELETE = 10;
    private static final int ID_MOD_SETTINGS_LOCK = 11; // замок на вход в настройки мода DevGram
    // Приватность (перенос новых тумблеров 12.10.4 из скрытой Fork-панели)
    private static final int ID_BLOCK_SCREENSHOTS = 20;
    private static final int ID_HIDE_SENSITIVE = 21;
    private static final int ID_HIDE_SENS_PHONE = 22;
    private static final int ID_HIDE_SENS_USERNAME = 23;
    private static final int ID_HIDE_SENS_BIO = 24;
    private static final int ID_HIDE_SENS_ID = 25;

    private static boolean gPref(String key, boolean def) {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean(key, def);
    }

    private static void gToggle(String key, boolean def) {
        org.telegram.messenger.MessagesController.getGlobalMainSettings()
                .edit().putBoolean(key, !gPref(key, def)).apply();
    }
    private static final int ID_CHAT_BASE = 1000;

    private UniversalRecyclerView listView;
    private ArrayList<Long> chatIds = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.DevGramLockedChats));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout content = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        DevGramSettingsLink.consumeHighlight(listView);
        return fragmentView = content;
    }

    @Override
    public boolean onFragmentCreate() {
        // экран показывает скрытые чаты — на выходе снова прячем
        return super.onFragmentCreate();
    }

    private boolean biometricAvailable() {
        try {
            return BiometricManager.from(getContext()).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Throwable t) {
            return false;
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean hasPass = DevGramLockedChats.hasPasscode();

        // — красивая «шапка» с иконкой замка
        items.add(UItem.asCustom(makeHero(getContext())));

        items.add(UItem.asShadow(null));

        // — Замок настроек мода: 4-значный код на вход в панель настроек DevGram
        //   (перенесён из скрытой Fork-панели; хранилище — DevGramSettingsLock/ForkSettingsLock).
        items.add(UItem.asGraySection("Настройки мода"));
        items.add(UItem.asButton(ID_MOD_SETTINGS_LOCK, R.drawable.outline_header_lock_24,
                "Замок настроек мода", DevGramSettingsLock.label()));
        items.add(UItem.asShadow("Спрашивает 4-значный код при входе в настройки DevGram. Защищает только меню мода."));

        // — Приватность (перенос из скрытой Fork-панели)
        items.add(UItem.asGraySection("Приватность"));
        items.add(UItem.asCheck(ID_BLOCK_SCREENSHOTS, LocaleController.getString(R.string.ForceBlockScreenshots))
                .setChecked(gPref("forceBlockScreenshots", false)));
        items.add(UItem.asCheck(ID_HIDE_SENSITIVE, LocaleController.getString(R.string.HideSensitiveData))
                .setChecked(gPref("hideSensitiveData", false)));
        if (gPref("hideSensitiveData", false)) {
            items.add(UItem.asCheck(ID_HIDE_SENS_PHONE, LocaleController.getString(R.string.HideSensitivePhone))
                    .setChecked(gPref("hideSensitivePhone", true)));
            items.add(UItem.asCheck(ID_HIDE_SENS_USERNAME, LocaleController.getString(R.string.HideSensitiveUsername))
                    .setChecked(gPref("hideSensitiveUsername", true)));
            items.add(UItem.asCheck(ID_HIDE_SENS_BIO, LocaleController.getString(R.string.HideSensitiveBio))
                    .setChecked(gPref("hideSensitiveBio", true)));
            items.add(UItem.asCheck(ID_HIDE_SENS_ID, LocaleController.getString(R.string.HideSensitiveId))
                    .setChecked(gPref("hideSensitiveId", true)));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.ForceBlockScreenshotsInfo)));

        items.add(UItem.asGraySection(LocaleController.getString(R.string.DevGramLockedChatsPasscode)));
        items.add(UItem.asButton(ID_SET_PASSCODE, R.drawable.outline_header_lock_24, hasPass
                ? LocaleController.getString(R.string.DevGramLockedChatsChangePasscode)
                : LocaleController.getString(R.string.DevGramLockedChatsSetPasscode)));
        if (hasPass) {
            items.add(UItem.asButton(ID_REMOVE_PASSCODE, R.drawable.msg_delete, LocaleController.getString(R.string.DevGramLockedChatsRemovePasscode)).red());
        }
        if (biometricAvailable()) {
            items.add(UItem.asCheck(ID_BIOMETRIC, LocaleController.getString(R.string.DevGramLockedChatsBiometric))
                    .setChecked(DevGramLockedChats.biometricEnabled()));
        }
        items.add(UItem.asCheck(ID_HIDE_NOTIFY, LocaleController.getString(R.string.DevGramLockedChatsHideNotify))
                .setChecked(DevGramLockedChats.hideNotifications()));
        items.add(UItem.asButton(ID_TTL, LocaleController.getString(R.string.DevGramLockedChatsTtl), ttlTitle()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.DevGramLockedChatsHideNotifyInfo)));

        // — Отпечаток на открытие / удаление
        items.add(UItem.asGraySection(LocaleController.getString(R.string.DevGramLockedChatsBioSection)));
        items.add(UItem.asCheck(ID_BIO_ARCHIVE, LocaleController.getString(R.string.DevGramLockedChatsBioArchive))
                .setChecked(DevGramLockedChats.bioArchive()));
        items.add(UItem.asCheck(ID_BIO_SAVED, LocaleController.getString(R.string.DevGramLockedChatsBioSaved))
                .setChecked(DevGramLockedChats.bioSaved()));
        items.add(UItem.asCheck(ID_BIO_SECRET, LocaleController.getString(R.string.DevGramLockedChatsBioSecret))
                .setChecked(DevGramLockedChats.bioSecret()));
        items.add(UItem.asCheck(ID_BIO_DELETE, LocaleController.getString(R.string.DevGramLockedChatsBioDelete))
                .setChecked(DevGramLockedChats.bioBeforeDelete()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.DevGramLockedChatsBioInfo)));

        chatIds = DevGramLockedChats.getAll(currentAccount);
        String sectionTitle = LocaleController.getString(R.string.DevGramLockedChatsList)
                + (chatIds.isEmpty() ? "" : "  •  " + chatIds.size());
        items.add(UItem.asGraySection(sectionTitle));
        items.add(UItem.asCheck(ID_REVEAL, LocaleController.getString(R.string.DevGramLockedChatsReveal))
                .setChecked(DevGramLockedChats.isRevealed()));

        if (chatIds.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.DevGramLockedChatsEmpty)));
        } else {
            for (int i = 0; i < chatIds.size(); i++) {
                long did = chatIds.get(i);
                TLObject obj = dialogObject(did);
                if (obj != null) {
                    UItem it = UItem.asProfileCell(obj);
                    it.id = ID_CHAT_BASE + i;
                    it.subtext = "🔒 " + LocaleController.getString(R.string.DevGramLockedChatsHidden);
                    items.add(it);
                } else {
                    items.add(UItem.asButton(ID_CHAT_BASE + i, dialogTitle(did)));
                }
            }
            items.add(UItem.asShadow(LocaleController.getString(R.string.DevGramLockedChatsInfo)));
        }
    }

    private TLObject dialogObject(long did) {
        try {
            if (DialogObject.isEncryptedDialog(did)) return null;
            if (DialogObject.isUserDialog(did)) return getMessagesController().getUser(did);
            return getMessagesController().getChat(-did);
        } catch (Throwable t) {
            return null;
        }
    }

    // Красивая «шапка»: замок в цветном круге + заголовок + подпись.
    private View makeHero(Context ctx) {
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER_HORIZONTAL);
        ll.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        ll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));

        int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider);
        FrameLayout circle = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Theme.multAlpha(accent, 0.14f));
        circle.setBackground(bg);
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.msg_secret);
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        circle.addView(icon, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
        ll.addView(circle, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        TextView title = new TextView(ctx);
        title.setText(LocaleController.getString(R.string.DevGramLockedChats));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        ll.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        TextView subtitle = new TextView(ctx);
        subtitle.setText(LocaleController.getString(R.string.DevGramLockedChatsInfo));
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(AndroidUtilities.dp(2), 1f);
        ll.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 6, 12, 8));

        return ll;
    }

    private String dialogTitle(long did) {
        try {
            if (DialogObject.isEncryptedDialog(did)) {
                return "🔒 Секретный чат";
            }
            if (DialogObject.isUserDialog(did)) {
                TLRPC.User u = getMessagesController().getUser(did);
                if (u != null) {
                    if (UserObject.isUserSelf(u)) return LocaleController.getString(R.string.SavedMessages);
                    return ContactsController.formatName(u.first_name, u.last_name);
                }
            } else {
                TLRPC.Chat c = getMessagesController().getChat(-did);
                if (c != null) return c.title;
            }
        } catch (Throwable ignore) {}
        return String.valueOf(did);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ID_MOD_SETTINGS_LOCK) {
            DevGramSettingsLock.manage(this, () -> listView.adapter.update(true));
        } else if (id == ID_BLOCK_SCREENSHOTS) {
            gToggle("forceBlockScreenshots", false);
            org.telegram.messenger.NotificationCenter.getGlobalInstance()
                    .postNotificationName(org.telegram.messenger.NotificationCenter.didSetPasscode, false);
            listView.adapter.update(true);
        } else if (id == ID_HIDE_SENSITIVE) {
            gToggle("hideSensitiveData", false);
            listView.adapter.update(true);
        } else if (id == ID_HIDE_SENS_PHONE) {
            gToggle("hideSensitivePhone", true);
            listView.adapter.update(true);
        } else if (id == ID_HIDE_SENS_USERNAME) {
            gToggle("hideSensitiveUsername", true);
            listView.adapter.update(true);
        } else if (id == ID_HIDE_SENS_BIO) {
            gToggle("hideSensitiveBio", true);
            listView.adapter.update(true);
        } else if (id == ID_HIDE_SENS_ID) {
            gToggle("hideSensitiveId", true);
            listView.adapter.update(true);
        } else if (id == ID_SET_PASSCODE) {
            DevGramPasscodeSheet.showSet(getContext(), pin -> {
                DevGramLockedChats.setPasscode(pin);
                listView.adapter.update(true);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                        LocaleController.getString(R.string.DevGramLockedChatsPasscodeSaved)).show();
            });
        } else if (id == ID_REMOVE_PASSCODE) {
            AlertDialog.Builder b = new AlertDialog.Builder(getContext());
            b.setTitle(LocaleController.getString(R.string.DevGramLockedChatsRemovePasscode));
            b.setMessage(LocaleController.getString(R.string.DevGramLockedChatsInfo));
            b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
                DevGramLockedChats.setPasscode(null);
                listView.adapter.update(true);
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(b.create());
        } else if (id == ID_BIOMETRIC) {
            DevGramLockedChats.setBiometricEnabled(!DevGramLockedChats.biometricEnabled());
            listView.adapter.update(true);
        } else if (id == ID_HIDE_NOTIFY) {
            DevGramLockedChats.setHideNotifications(!DevGramLockedChats.hideNotifications());
            listView.adapter.update(true);
        } else if (id == ID_TTL) {
            showTtlDialog();
        } else if (id == ID_BIO_ARCHIVE) {
            DevGramLockedChats.setBioArchive(!DevGramLockedChats.bioArchive());
            listView.adapter.update(true);
        } else if (id == ID_BIO_SAVED) {
            DevGramLockedChats.setBioSaved(!DevGramLockedChats.bioSaved());
            listView.adapter.update(true);
        } else if (id == ID_BIO_SECRET) {
            DevGramLockedChats.setBioSecret(!DevGramLockedChats.bioSecret());
            listView.adapter.update(true);
        } else if (id == ID_BIO_DELETE) {
            DevGramLockedChats.setBioBeforeDelete(!DevGramLockedChats.bioBeforeDelete());
            listView.adapter.update(true);
        } else if (id == ID_REVEAL) {
            DevGramLockedChats.setRevealed(!DevGramLockedChats.isRevealed());
            listView.adapter.update(true);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        } else if (id >= ID_CHAT_BASE && id - ID_CHAT_BASE < chatIds.size()) {
            long did = chatIds.get(id - ID_CHAT_BASE);
            showChatActions(did, view);
        }
    }

    private int[] ttlValues() {
        return new int[]{
                DevGramLockedChats.TTL_ON_BACKGROUND, DevGramLockedChats.TTL_1_MIN,
                DevGramLockedChats.TTL_5_MIN, DevGramLockedChats.TTL_15_MIN,
                DevGramLockedChats.TTL_UNTIL_RESTART};
    }

    private String ttlTitleFor(int v) {
        switch (v) {
            case DevGramLockedChats.TTL_1_MIN: return LocaleController.getString(R.string.DevGramLockedChatsTtl1Min);
            case DevGramLockedChats.TTL_5_MIN: return LocaleController.getString(R.string.DevGramLockedChatsTtl5Min);
            case DevGramLockedChats.TTL_15_MIN: return LocaleController.getString(R.string.DevGramLockedChatsTtl15Min);
            case DevGramLockedChats.TTL_UNTIL_RESTART: return LocaleController.getString(R.string.DevGramLockedChatsTtlUntilRestart);
            default: return LocaleController.getString(R.string.DevGramLockedChatsTtlOnBackground);
        }
    }

    private CharSequence ttlTitle() {
        return ttlTitleFor(DevGramLockedChats.getTtl());
    }

    private void showTtlDialog() {
        int[] vals = ttlValues();
        CharSequence[] labels = new CharSequence[vals.length];
        for (int i = 0; i < vals.length; i++) labels[i] = ttlTitleFor(vals[i]);
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle(LocaleController.getString(R.string.DevGramLockedChatsTtl));
        b.setItems(labels, (dialog, which) -> {
            DevGramLockedChats.setTtl(vals[which]);
            listView.adapter.update(true);
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    // Красивое контекст-меню по тапу на скрытый чат (современный ItemOptions с иконками).
    private void showChatActions(long did, View anchor) {
        ItemOptions.makeOptions(this, anchor)
                .add(R.drawable.msg_openprofile, LocaleController.getString(R.string.Open),
                        () -> openChat(did))
                .add(R.drawable.devgram_deleted_eye, LocaleController.getString(R.string.DevGramLockedChatsShow),
                        () -> {
                            DevGramLockedChats.setLocked(currentAccount, did, false);
                            listView.adapter.update(true);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_unmute,
                                    LocaleController.getString(R.string.DevGramLockedChatsShown)).show();
                        })
                .setGravity(Gravity.RIGHT)
                .show();
    }

    private void openChat(long did) {
        android.os.Bundle args = new android.os.Bundle();
        if (DialogObject.isUserDialog(did)) {
            args.putLong("user_id", did);
        } else if (!DialogObject.isEncryptedDialog(did)) {
            args.putLong("chat_id", -did);
        } else {
            return;
        }
        if (getMessagesController().checkCanOpenChat(args, this)) {
            presentFragment(new ChatActivity(args));
        }
    }

}

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramProfileBackground;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

// DevGram: свой фон шапки профиля (фото/видео/GIF) для поддержавших. Видят все пользователи DevGram,
// поэтому новый фон сначала проходит модерацию; видео — первые 15 секунд, без звука, до 720p.
public class DevGramProfileBgActivity extends DevGramSettingsFragment {

    private static final int REQUEST_PICK = 7301;
    private static final int ID_PICK = 1;
    private static final int ID_REMOVE = 2;
    private static final long PENDING_TTL = 3L * 24 * 60 * 60 * 1000;

    private UniversalRecyclerView listView;
    private FrameLayout previewView;
    private BackupImageView previewImage;
    private TextView previewEmpty;
    private int shownMsg = -1;
    private boolean uploading;

    public static String statusText(int account) {
        long uid = org.telegram.messenger.UserConfig.getInstance(account).getClientUserId();
        if (isPending(uid)) return "На модерации";
        return DevGramBadges.backgroundOf(uid) != 0 ? "Свой" : "Нет";
    }

    private static SharedPreferences prefs() {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings();
    }

    // На модерации: файл ушёл боту, а номер фона в значке с тех пор не поменялся (и прошло не больше 3 дней)
    private static boolean isPending(long uid) {
        long at = prefs().getLong("devgramBgPendingAt_" + uid, 0);
        if (at == 0) return false;
        int before = prefs().getInt("devgramBgPendingFrom_" + uid, 0);
        if (DevGramBadges.backgroundOf(uid) != before || System.currentTimeMillis() - at > PENDING_TTL) {
            prefs().edit().remove("devgramBgPendingAt_" + uid).remove("devgramBgPendingFrom_" + uid).apply();
            return false;
        }
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Фон профиля");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        previewView = new FrameLayout(context);
        previewView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        previewView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        previewImage = new BackupImageView(context);
        previewImage.setRoundRadius(AndroidUtilities.dp(14));
        previewImage.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider)));
        previewView.addView(previewImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 180));
        previewEmpty = new TextView(context);
        previewEmpty.setGravity(Gravity.CENTER);
        previewEmpty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        previewEmpty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        previewView.addView(previewEmpty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 180));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        updatePreview();
        return fragmentView = content;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreview();
        if (listView != null && listView.adapter != null) listView.adapter.update(false);
    }

    private void updatePreview() {
        if (previewImage == null) return;
        long uid = getUserConfig().getClientUserId();
        int msg = DevGramBadges.backgroundOf(uid);
        boolean pending = isPending(uid);
        previewEmpty.setText(pending ? "⏳ Новый фон на модерации" : "Фон не выбран");
        previewEmpty.setVisibility(msg == 0 || pending ? View.VISIBLE : View.GONE);
        if (msg == shownMsg) return;
        shownMsg = msg;
        previewImage.setImageDrawable(null);
        if (msg == 0) return;
        DevGramProfileBackground.load(currentAccount, msg, mo -> {
            if (mo == null || shownMsg != msg) return;
            setPreview(mo);
        });
    }

    private void setPreview(MessageObject mo) {
        TLRPC.Document doc = mo.getDocument();
        if (doc != null) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 320);
            previewImage.getImageReceiver().setAllowStartAnimation(true);
            previewImage.getImageReceiver().setAutoRepeat(1);
            previewImage.setImage(ImageLocation.getForDocument(doc), ImageLoader.AUTOPLAY_FILTER,
                    ImageLocation.getForDocument(thumb, doc), "b", null, doc.size, 1, mo);
        } else if (mo.messageOwner.media != null && mo.messageOwner.media.photo != null) {
            TLRPC.Photo photo = mo.messageOwner.media.photo;
            TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 800);
            TLRPC.PhotoSize small = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 90);
            previewImage.setImage(ImageLocation.getForObject(big, photo), null, ImageLocation.getForObject(small, photo), "b", null, 0, 1, mo);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(previewView));
        items.add(UItem.asShadow("Фон шапки вашего профиля видят все пользователи DevGram."));
        items.add(UItem.asButton(ID_PICK, R.drawable.msg_photos, uploading ? "Отправляю…" : "Выбрать фото, видео или GIF"));
        long uid = getUserConfig().getClientUserId();
        if (DevGramBadges.backgroundOf(uid) != 0) {
            items.add(UItem.asButton(ID_REMOVE, R.drawable.msg_delete, "Убрать фон").red());
        }
        items.add(UItem.asShadow("Новый фон сначала проверяет модератор — бот напишет, когда его одобрят. "
                + "Видео обрезается до 15 секунд и проигрывается без звука."));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_PICK) {
            if (uploading || getParentActivity() == null) return;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                getParentActivity().startActivityForResult(Intent.createChooser(intent, "Фон профиля"), REQUEST_PICK);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (item.id == ID_REMOVE) {
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), resourceProvider);
            b.setTitle("Убрать фон профиля?");
            b.setMessage("Шапка профиля снова станет обычной у всех пользователей DevGram.");
            b.setPositiveButton("Убрать", (d, w) -> {
                try {
                    org.json.JSONObject f = new org.json.JSONObject();
                    f.put("g", 0);
                    DevGramBadges.updateSelfFields(currentAccount, f, (ok, error) -> {
                        if (ok) {
                            updatePreview();
                            if (listView != null && listView.adapter != null) listView.adapter.update(true);
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Фон убран").show();
                        } else {
                            BulletinFactory.of(this).createErrorBulletin(error != null ? error : "Не удалось убрать фон").show();
                        }
                    });
                } catch (Exception ignore) {
                }
            });
            b.setNegativeButton(org.telegram.messenger.LocaleController.getString(R.string.Cancel), null);
            showDialog(b.create());
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        uploading = true;
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        Utilities.globalQueue.postRunnable(() -> {
            String mime = null;
            File file = null;
            try {
                mime = ApplicationLoader.applicationContext.getContentResolver().getType(uri);
                String ext = mime == null ? "bin" : mime.equals("image/gif") ? "gif" : mime.startsWith("video/") ? "mp4" : "jpg";
                file = new File(ApplicationLoader.applicationContext.getCacheDir(), "dgbg_" + System.currentTimeMillis() + "." + ext);
                try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(file)) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            } catch (Throwable e) {
                FileLog.e(e);
                file = null;
            }
            final String fMime = mime;
            final File fFile = file;
            AndroidUtilities.runOnUIThread(() -> {
                if (fFile == null || fMime == null || !(fMime.startsWith("image/") || fMime.startsWith("video/"))) {
                    uploading = false;
                    if (listView != null && listView.adapter != null) listView.adapter.update(true);
                    BulletinFactory.of(this).createErrorBulletin("Нужно фото, видео или GIF").show();
                    return;
                }
                DevGramProfileBackground.upload(currentAccount, fFile.getAbsolutePath(), fMime, error -> {
                    uploading = false;
                    long uid = getUserConfig().getClientUserId();
                    if (error == null) {
                        prefs().edit().putLong("devgramBgPendingAt_" + uid, System.currentTimeMillis())
                                .putInt("devgramBgPendingFrom_" + uid, DevGramBadges.backgroundOf(uid)).apply();
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Фон отправлен на модерацию").show();
                    } else {
                        BulletinFactory.of(this).createErrorBulletin(error).show();
                    }
                    updatePreview();
                    if (listView != null && listView.adapter != null) listView.adapter.update(true);
                });
            });
        });
    }
}

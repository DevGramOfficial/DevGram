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

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramProfileBackground;
import org.telegram.messenger.DevGramProfileCustomization;
import org.telegram.messenger.DevGramProfilePolicy;
import org.telegram.messenger.DevGramProfileMedia;
import org.json.JSONArray;
import org.json.JSONObject;
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

// DevGram: статичные фоны доступны всем, видео/GIF — по отдельному праву. Видят все пользователи DevGram,
// поэтому новый фон сначала проходит модерацию; видео — первые 15 секунд, до 720p; звук включается в редакторе.
public class DevGramProfileBgActivity extends DevGramSettingsFragment implements org.telegram.messenger.NotificationCenter.NotificationCenterDelegate {

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
    private final DevGramStudioActivity.Draft draft;
    private final String slot;
    private JSONArray library=new JSONArray();
    private boolean loadingLibrary;
    private String libraryError;
    private int mediaType;
    public DevGramProfileBgActivity(){this(null,"banner");}
    public DevGramProfileBgActivity(DevGramStudioActivity.Draft draft,String slot){this(draft,slot,draft==null?0:DevGramProfileMedia.type(draft.document,slot));}
    public DevGramProfileBgActivity(DevGramStudioActivity.Draft draft,String slot,int type){this.draft=draft;this.slot=slot.equals("page")?"page":"banner";mediaType=type==1?1:0;}
    private String mediaFeature(){return mediaType==1?"profile.media.animated":"profile.media";}

    public static String statusText(int account) {
        long uid = org.telegram.messenger.UserConfig.getInstance(account).getClientUserId();
        if (isPending(uid)) return DevGramLocale.text("На модерации");
        return DevGramBadges.backgroundOf(uid) != 0 ? DevGramLocale.text("Свой") : DevGramLocale.text("Нет");
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
        actionBar.setTitle(DevGramLocale.text("Фон профиля"));
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

    // решение модератора приходит обновлением значков — экран обновляется сам
    @Override
    public boolean onFragmentCreate() {
        if (!DevGramProfileCustomization.enabled()) return false;
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(this, org.telegram.messenger.NotificationCenter.reloadInterface);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(this, org.telegram.messenger.NotificationCenter.reloadInterface);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == org.telegram.messenger.NotificationCenter.reloadInterface) {
            updatePreview();
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!DevGramProfileCustomization.enabled()) { DevGramAccessNavigation.close(this); return; }
        if (draft!=null) loadLibrary();
        DevGramBadges.syncFromCloud(); // живой поток у части операторов режется — берём свежие значки сразу
        updatePreview();
        if (listView != null && listView.adapter != null) listView.adapter.update(false);
    }

    private void updatePreview() {
        if (previewImage == null) return;
        long uid = getUserConfig().getClientUserId();
        int selected=draft==null?-1:DevGramProfileMedia.message(draft.document,uid,slot);
        int msg = selected<0?DevGramBadges.backgroundOf(uid):selected;
        boolean pending = draft==null&&isPending(uid);
        previewEmpty.setText(pending ? DevGramLocale.text(withoutReview() ? "Фон загружается…" : "⏳ Новый фон на модерации") : DevGramLocale.text("Фон не выбран"));
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

    private boolean withoutReview() {
        long target = draft != null ? draft.target(currentAccount) : getUserConfig().getClientUserId();
        DevGramBadges.Badge badge = DevGramBadges.badgeOf(target);
        return badge != null && badge.backgroundWithoutReview;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(previewView));
        CharSequence[] types={DevGramAppearanceAccess.label(this,"profile.media",DevGramLocale.text("Статичный фон")),DevGramAppearanceAccess.label(this,"profile.media.animated",DevGramLocale.text("Анимированный фон"))};
        items.add(UItem.asCustom(org.telegram.ui.Components.DevGramStudioControls.choices(getParentActivity(),"media",new String[]{"Статичный фон","Анимированный фон"},types,mediaType,draft==null?null:draft.theme,resourceProvider,value->{if(!uploading&&DevGramAppearanceAccess.check(this,value==1?"profile.media.animated":"profile.media")){mediaType=value;listView.adapter.update(true);}})));

        if(draft!=null){
            items.add(UItem.asShadow(DevGramLocale.text(withoutReview()
                    ? "Медиа добавляется в библиотеку без проверки. Выберите вариант ниже: оформление изменится только после сохранения профиля."
                    : "Загрузка добавляет медиа в библиотеку после проверки. Выберите одобренный вариант ниже: оформление изменится только после сохранения профиля.")));
            items.add(UItem.asButton(ID_PICK,R.drawable.msg_add,DevGramAppearanceAccess.label(this,mediaFeature(),DevGramLocale.text(uploading?"Загружаю…":mediaType==1?"Добавить GIF или видео":"Добавить статичное изображение"))).setEnabled(!uploading));
            items.add(UItem.asButton(3,R.drawable.msg_retry,DevGramLocale.text(loadingLibrary?"Загрузка…":"Обновить библиотеку")).setEnabled(!loadingLibrary));
            items.add(UItem.asButton(ID_REMOVE,R.drawable.msg_delete,DevGramLocale.text("Убрать медиа из оформления")));
            items.add(UItem.asHeader(DevGramLocale.text("Моя медиатека")));
            if(libraryError!=null)items.add(UItem.asShadow(libraryError));
            if(!loadingLibrary&&library.length()==0)items.add(UItem.asShadow(DevGramLocale.text("Здесь появятся ваши одобренные фото и видео, а также материалы установленных работ.")));
            JSONArray shownLibrary=new JSONArray();for(int i=0;i<library.length();i++){JSONObject asset=library.optJSONObject(i);if(asset!=null&&(asset.optString("kind").equals("video")?1:0)==mediaType)shownLibrary.put(asset);}
            for(int start=0;start<shownLibrary.length();start+=2){
                android.widget.LinearLayout row=new android.widget.LinearLayout(getContext());row.setPadding(AndroidUtilities.dp(12),AndroidUtilities.dp(6),AndroidUtilities.dp(12),AndroidUtilities.dp(6));
                for(int n=start;n<Math.min(start+2,shownLibrary.length());n++){
                    JSONObject asset=shownLibrary.optJSONObject(n);if(asset==null)continue;DevGramProfileMedia.remember(asset);
                    FrameLayout tile=new FrameLayout(getContext());BackupImageView image=new BackupImageView(getContext());image.setRoundRadius(AndroidUtilities.dp(14));image.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14),Theme.getColor(Theme.key_windowBackgroundGray,resourceProvider)));tile.addView(image,LayoutHelper.createFrame(-1,-1));
                    TextView caption=new TextView(getContext());caption.setTextColor(android.graphics.Color.WHITE);caption.setTextSize(13);caption.setPadding(AndroidUtilities.dp(10),AndroidUtilities.dp(6),AndroidUtilities.dp(10),AndroidUtilities.dp(6));caption.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),0x99000000));
                    boolean chosen=draft.document.optLong(slot+"Asset")==asset.optLong("id");caption.setText((chosen?"✓  ":"")+DevGramLocale.text(asset.optString("kind").equals("video")?"Видео":"Фото"));tile.addView(caption,LayoutHelper.createFrame(-1,-2,Gravity.BOTTOM,5,0,5,5));
                    tile.setContentDescription(caption.getText());tile.setOnClickListener(v->{if(!DevGramAppearanceAccess.check(this,mediaFeature()))return;DevGramProfileMedia.select(draft.document,slot,asset);try{draft.document.put(slot+"Mode",3);}catch(Exception ignored){}draft.dirty=true;if(draft.onChanged!=null)draft.onChanged.run();updatePreview();listView.adapter.update(true);});
                    org.telegram.messenger.DevGramProfileBackground.load(currentAccount,asset.optInt("message"),object->{if(object==null||isFinishing())return;TLRPC.Document document=object.getDocument();if(document!=null){TLRPC.PhotoSize thumb=FileLoader.getClosestPhotoSizeWithSize(document.thumbs,320);image.setImage(ImageLocation.getForDocument(thumb,document),"320_320",(android.graphics.drawable.Drawable)null,object);}else if(object.messageOwner.media!=null&&object.messageOwner.media.photo!=null){TLRPC.PhotoSize size=FileLoader.getClosestPhotoSizeWithSize(object.messageOwner.media.photo.sizes,320);image.setImage(ImageLocation.getForObject(size,object.messageOwner.media.photo),"320_320",(android.graphics.drawable.Drawable)null,object);}});
                    android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(0,AndroidUtilities.dp(130),1);lp.setMargins(AndroidUtilities.dp(4),0,AndroidUtilities.dp(4),0);row.addView(tile,lp);
                }
                items.add(UItem.asCustom(row,142));
            }
            return;
        }
        items.add(UItem.asShadow(DevGramLocale.text("Фон шапки вашего профиля видят все пользователи DevGram.\n\n")
                + DevGramLocale.text("Лучше всего подходит горизонтальный кадр 4:3: фото 1280×960, видео 960×720. ")
                + DevGramLocale.text("Другие пропорции обрежутся по краям. При прокрутке профиля остаётся видна верхняя часть, ")
                + DevGramLocale.text("поэтому главное держите ближе к верху и центру.")));
        items.add(UItem.asButton(ID_PICK, R.drawable.msg_photos, uploading ? DevGramLocale.text("Отправляю…") : DevGramAppearanceAccess.label(this,mediaFeature(),DevGramLocale.text(mediaType==1?"Добавить GIF или видео":"Добавить статичное изображение"))));
        long uid = getUserConfig().getClientUserId();
        if (DevGramBadges.backgroundOf(uid) != 0) {
            items.add(UItem.asButton(ID_REMOVE, R.drawable.msg_delete, DevGramLocale.text("Убрать фон")).red());
        }
        items.add(UItem.asShadow(DevGramLocale.text(withoutReview()
                ? "Для этого получателя включён фон без проверки. "
                : "Новый фон сначала проверяет модератор — бот напишет, когда его одобрят. ")
                + DevGramLocale.text("Видео обрезается до 15 секунд. Звук можно включить в настройках баннера или фона.")));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if(item.id==3){loadLibrary();return;}
        if(item.id==ID_REMOVE&&draft!=null){DevGramProfileMedia.select(draft.document,slot,null);draft.dirty=true;if(draft.onChanged!=null)draft.onChanged.run();updatePreview();listView.adapter.update(true);return;}
        if (item.id == ID_PICK) {
            if (!DevGramAppearanceAccess.check(this, mediaFeature())) return;
            if (uploading || getParentActivity() == null) return;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType(mediaType==1?"*/*":"image/*");
            if(mediaType==1)intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/gif", "video/*"});
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                getParentActivity().startActivityForResult(Intent.createChooser(intent, DevGramLocale.text("Фон профиля")), REQUEST_PICK);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (item.id == ID_REMOVE) {
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), resourceProvider);
            b.setTitle(DevGramLocale.text("Убрать фон профиля?"));
            b.setMessage(DevGramLocale.text("Шапка профиля снова станет обычной у всех пользователей DevGram."));
            b.setPositiveButton(DevGramLocale.text("Убрать"), (d, w) -> {
                try {
                    org.json.JSONObject f = new org.json.JSONObject();
                    f.put("g", 0);
                    DevGramBadges.updateSelfFields(currentAccount, f, (ok, error) -> {
                        if (ok) {
                            updatePreview();
                            if (listView != null && listView.adapter != null) listView.adapter.update(true);
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, DevGramLocale.text("Фон убран")).show();
                        } else {
                            BulletinFactory.of(this).createErrorBulletin(error != null ? error : DevGramLocale.text("Не удалось убрать фон")).show();
                        }
                    });
                } catch (Exception ignore) {
                }
            });
            b.setNegativeButton(DevGramLocale.getString(R.string.Cancel), null);
            showDialog(b.create());
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        if (!DevGramAppearanceAccess.check(this, mediaFeature())) return;
        final Uri uri = data.getData();
        final int selectedType=mediaType;
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
                    long total=0;while ((n = in.read(buf)) > 0){total+=n;if(total>20L*1024*1024)throw new java.io.IOException();out.write(buf, 0, n);}
                }
                if(selectedType==0){
                    if(mime==null||!mime.startsWith("image/"))throw new java.io.IOException();
                    android.graphics.Bitmap bitmap=ImageLoader.loadBitmap(file.getAbsolutePath(),null,1280,1280,true);
                    if(bitmap==null)throw new java.io.IOException();
                    File still=new File(ApplicationLoader.applicationContext.getCacheDir(),"dgbg_static_"+System.currentTimeMillis()+".jpg");
                    try(OutputStream output=new FileOutputStream(still)){if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,92,output))throw new java.io.IOException();}finally{bitmap.recycle();}
                    file.delete();file=still;mime="image/jpeg";
                }

            } catch (Throwable e) {
                FileLog.e(e);
                if(file!=null)file.delete();
                file = null;
            }
            final String fMime = mime;
            final File fFile = file;
            AndroidUtilities.runOnUIThread(() -> {
                if (fFile == null || fMime == null || !(fMime.startsWith("image/") || fMime.startsWith("video/"))) {
                    uploading = false;
                    if (listView != null && listView.adapter != null) listView.adapter.update(true);
                    BulletinFactory.of(this).createErrorBulletin(DevGramLocale.text("Нужно фото, видео или GIF")).show();
                    return;
                }
                DevGramProfileBackground.upload(currentAccount, fFile.getAbsolutePath(), fMime, draft!=null, draft != null ? draft.subject : 0, error -> {
                    uploading = false;
                    long uid = getUserConfig().getClientUserId();
                    if (error == null) {
                        prefs().edit().putLong("devgramBgPendingAt_" + uid, System.currentTimeMillis())
                                .putInt("devgramBgPendingFrom_" + uid, DevGramBadges.backgroundOf(uid)).apply();
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, DevGramLocale.text("Фон отправлен на модерацию")).show();
                    } else {
                        BulletinFactory.of(this).createErrorBulletin(error).show();
                    }
                    updatePreview();
                    if (listView != null && listView.adapter != null) listView.adapter.update(true);
                });
            });
        });
    }

    private void loadLibrary(){
        if(loadingLibrary||!DevGramProfileMedia.any(getUserConfig().getClientUserId()))return;
        loadingLibrary=true;libraryError=null;
        DevGramProfilePolicy.request(currentAccount,"studio/media",new JSONObject(),(result,error)->{loadingLibrary=false;if(result!=null){JSONArray values=result.optJSONArray("media");library=values==null?new JSONArray():values;for(int i=0;i<library.length();i++)DevGramProfileMedia.remember(library.optJSONObject(i));}else libraryError=DevGramLocale.text("Не удалось загрузить медиатеку. Попробуйте ещё раз.");if(listView!=null)listView.adapter.update(true);});
    }
}

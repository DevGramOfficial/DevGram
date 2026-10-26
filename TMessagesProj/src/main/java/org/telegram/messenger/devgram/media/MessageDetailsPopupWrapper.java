package org.telegram.messenger.devgram.media;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;




import com.google.zxing.Dimension;
import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.ProfileActivity;
public abstract class MessageDetailsPopupWrapper {
    private String filePath;
    private final BaseFragment fragment;
    private String[] geo;
    private long ownerId;
    private JpegFingerprint photoFingerprint;
    private final Theme.ResourcesProvider resourcesProvider;
    private Mp4Fingerprint roundVideoFingerprint;
    public LinearLayout swipeBack;
    private OpusFingerprint voiceFingerprint;
    private final int SET_OWNER = 0;
    private final int FILE_PATH = 1;
    private final int LOCATION = 2;
    private final int BITRATE = 3;
    private final int RESOLUTION = 4;
    private final int PLATFORM = 5;

    public void closeMenu() {
    }

    public abstract void copy(String str);

    /* JADX WARN: Code duplicated, block: B:139:0x03dd  */
    public MessageDetailsPopupWrapper(final BaseFragment baseFragment, final PopupSwipeBackLayout popupSwipeBackLayout, MessageObject messageObject, Theme.ResourcesProvider resourcesProvider) {
        int i;
        int i2;
        ScrollView scrollView;
        final boolean z;
        TLRPC.InputStickerSet inputStickerSet;
        int i3;
        final MessageObject messageObject2 = messageObject;
        this.ownerId = 0L;
        this.fragment = baseFragment;
        this.resourcesProvider = resourcesProvider;
        Activity parentActivity = baseFragment.getParentActivity();
        LinearLayout linearLayout = new LinearLayout(parentActivity);
        this.swipeBack = linearLayout;
        linearLayout.setOrientation(1);
        ScrollView scrollView2 = new ScrollView(parentActivity) {
            final AnimatedFloat alphaFloat = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
            Drawable topShadowDrawable;
            private boolean wasCanScrollVertically;

            @Override
            public void onNestedScroll(View view, int i4, int i5, int i6, int i7) {
                super.onNestedScroll(view, i4, i5, i6, i7);
                boolean zCanScrollVertically = canScrollVertically(-1);
                if (this.wasCanScrollVertically != zCanScrollVertically) {
                    invalidate();
                    this.wasCanScrollVertically = zCanScrollVertically;
                }
            }

            @Override
            public void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                float f = this.alphaFloat.set(canScrollVertically(-1) ? 1.0f : 0.0f) * 0.5f;
                if (f > 0.0f) {
                    if (this.topShadowDrawable == null) {
                        this.topShadowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.header_shadow);
                    }
                    Drawable drawable = this.topShadowDrawable;
                    if (drawable != null) {
                        drawable.setBounds(0, getScrollY(), getWidth(), getScrollY() + this.topShadowDrawable.getIntrinsicHeight());
                        this.topShadowDrawable.setAlpha((int) (f * 255.0f));
                        this.topShadowDrawable.draw(canvas);
                    }
                }
            }
        };
        LinearLayout linearLayout2 = new LinearLayout(parentActivity);
        scrollView2.addView(linearLayout2);
        linearLayout2.setOrientation(1);
        ActionBarMenuSubItem actionBarMenuSubItem = new ActionBarMenuSubItem((Context) baseFragment.getParentActivity(), true, false, resourcesProvider);
        actionBarMenuSubItem.setItemHeight(44);
        actionBarMenuSubItem.setTextAndIcon(LocaleController.getString(R.string.Back), R.drawable.msg_arrow_back);
        actionBarMenuSubItem.getTextView().setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(40.0f), 0, LocaleController.isRTL ? AndroidUtilities.dp(40.0f) : 0, 0);
        actionBarMenuSubItem.setOnClickListener(view -> popupSwipeBackLayout.closeForeground());
        this.swipeBack.addView(actionBarMenuSubItem, LayoutHelper.createLinear(-1, -2));
        linearLayout2.addView(createGap(), LayoutHelper.createLinear(-1, 8));
        ArrayList arrayList = new ArrayList();
        int i4 = messageObject2.messageOwner.views;
        if (i4 > 0) {
            arrayList.add(new Item(R.drawable.msg_view_file, String.format(LocaleController.getPluralString("Views", i4), AndroidUtilities.formatCount(messageObject2.messageOwner.views)), (String) null));
        }
        int i5 = messageObject2.messageOwner.forwards;
        if (i5 > 0) {
            arrayList.add(new Item(R.drawable.msg_forward, String.format(LocaleController.getPluralString("Shares", i5), AndroidUtilities.formatCount(messageObject2.messageOwner.forwards)), (String) null));
        }
        if (!arrayList.isEmpty()) {
            arrayList.add(null);
        }
        arrayList.add(new Item(R.drawable.msg_info, "ID", messageObject2.messageOwner.id));
        if (messageObject2.messageOwner.date > 0) {
            arrayList.add(new Item(R.drawable.msg_calendar2, LocaleController.getString(R.string.Date), formatTime(messageObject2.messageOwner.date, true)));
        }
        TLRPC.Message message = messageObject2.messageOwner;
        TLRPC.MessageFwdHeader messageFwdHeader = message.fwd_from;
        if (messageFwdHeader != null && (i3 = messageFwdHeader.date) > 0 && i3 != message.date) {
            arrayList.add(new Item(R.drawable.msg_recent, LocaleController.getString(R.string.ForwardedDate), formatTime(messageObject2.messageOwner.fwd_from.date, true)));
        }
        TLRPC.Message message2 = messageObject2.messageOwner;
        int i6 = message2.edit_date;
        if (i6 > 0 && i6 != message2.date && !message2.edit_hide) {
            arrayList.add(new Item(R.drawable.msg_edit, LocaleController.getString(R.string.EditedDate), formatTime(messageObject2.messageOwner.edit_date, true)));
        }
        arrayList.add(null);
        if (messageObject2.getSize() > 0) {
            arrayList.add(new Item(R.drawable.msg_sendfile, LocaleController.getString(R.string.FileSize), AndroidUtilities.formatFileSize(messageObject2.getSize())));
        }
        if (messageObject2.getMimeType() != null && !messageObject2.getMimeType().isEmpty()) {
            arrayList.add(new Item(R.drawable.msg_media, LocaleController.getString(R.string.MimeType), messageObject2.getMimeType()));
        }
        if (MessageObject.getMedia(messageObject2.messageOwner) != null && MessageObject.getMedia(messageObject2.messageOwner).document != null) {
            ArrayList<TLRPC.DocumentAttribute> arrayList2 = MessageObject.getMedia(messageObject2.messageOwner).document.attributes;
            int size = arrayList2.size();
            int i7 = 0;
            while (i7 < size) {
                TLRPC.DocumentAttribute documentAttribute = arrayList2.get(i7);
                i7++;
                TLRPC.DocumentAttribute documentAttribute2 = documentAttribute;
                if (documentAttribute2 instanceof TLRPC.TL_documentAttributeFilename) {
                    arrayList.add(new Item(R.drawable.msg_log, LocaleController.getString(R.string.FileName), documentAttribute2.file_name));
                }
                if ((documentAttribute2 instanceof TLRPC.TL_documentAttributeSticker) && (inputStickerSet = documentAttribute2.stickerset) != null) {
                    long jExtractOwnerId = DevGramChatUtils.extractOwnerId(inputStickerSet.id);
                    this.ownerId = jExtractOwnerId;
                    if (jExtractOwnerId > 0) {
                        arrayList.add(new Item(0, R.drawable.msg_sticker, LocaleController.getString(R.string.ChannelCreator), String.valueOf(this.ownerId)));
                    }
                }
            }
        }
        String pathToMessage = DevGramChatUtils.getPathToMessage(messageObject2);
        this.filePath = pathToMessage;
        if (!TextUtils.isEmpty(pathToMessage)) {
            arrayList.add(new Item(1, R.drawable.msg_map, LocaleController.getString(R.string.FilePath), LocaleController.getString(R.string.Open)));
        }
        boolean z2 = messageObject2.isVoice() || messageObject2.isMusic();
        boolean z3 = messageObject2.isVideo() || messageObject2.isRoundVideo() || messageObject2.isVideoSticker() || messageObject2.isGif();
        boolean zIsPhotoAsDocument = isPhotoAsDocument(messageObject2);
        boolean z4 = zIsPhotoAsDocument || messageObject2.isPhoto() || messageObject2.isSticker();
        if ((z4 || messageObject2.isVoice() || messageObject2.isRoundVideo()) && !TextUtils.isEmpty(this.filePath)) {
            arrayList.add(new Item(5, R.drawable.menu_devices, LocaleController.getString(R.string.Platform), LocaleController.getString(R.string.NumberUnknown)));
        }
        if (z3 || z4) {
            arrayList.add(new Item(4, R.drawable.msg_photo_crop, LocaleController.getString(R.string.Resolution), "0x0"));
        }
        if (zIsPhotoAsDocument && !TextUtils.isEmpty(this.filePath)) {
            arrayList.add(new Item(2, R.drawable.msg_location, LocaleController.getString(R.string.ShareLocation), "0.0, 0.0"));
        }
        if (z3 || z2) {
            arrayList.add(new Item(3, R.drawable.msg_noise_on, LocaleController.getString(R.string.Bitrate), "0 Kbps"));
            int duration = (int) messageObject2.getDuration();
            if (duration > 0) {
                arrayList.add(new Item(R.drawable.msg2_animations, LocaleController.getString(R.string.Duration), AndroidUtilities.formatShortDuration(duration)));
            }
        }
        if (MessageObject.getMedia(messageObject2.messageOwner) == null) {
            i = 0;
        } else if (MessageObject.getMedia(messageObject2.messageOwner).photo != null && MessageObject.getMedia(messageObject2.messageOwner).photo.dc_id > 0) {
            i = MessageObject.getMedia(messageObject2.messageOwner).photo.dc_id;
        } else if (MessageObject.getMedia(messageObject2.messageOwner).document != null && MessageObject.getMedia(messageObject2.messageOwner).document.dc_id > 0) {
            i = MessageObject.getMedia(messageObject2.messageOwner).document.dc_id;
        } else if (MessageObject.getMedia(messageObject2.messageOwner).webpage != null && MessageObject.getMedia(messageObject2.messageOwner).webpage.photo != null && MessageObject.getMedia(messageObject2.messageOwner).webpage.photo.dc_id > 0) {
            i = MessageObject.getMedia(messageObject2.messageOwner).webpage.photo.dc_id;
        } else if (MessageObject.getMedia(messageObject2.messageOwner).webpage == null || MessageObject.getMedia(messageObject2.messageOwner).webpage.document == null || MessageObject.getMedia(messageObject2.messageOwner).webpage.document.dc_id <= 0) {
            i = 0;
        } else {
            i = MessageObject.getMedia(messageObject2.messageOwner).webpage.document.dc_id;
        }
        if (i != 0) {
            arrayList.add(new Item(R.drawable.msg_satellite, LocaleController.getString(R.string.Datacenter), String.format(Locale.ROOT, "DC%d, %s", Integer.valueOf(i), DevGramChatUtils.getDCName(i))));
        }
        if (arrayList.get(arrayList.size() - 1) == null) {
            arrayList.remove(arrayList.size() - 1);
        }
        final boolean isVideoLike = z3;
        final boolean isPhotoLike = z4;
        int height = 0;
        for (Object entry : arrayList) {
            final Item item = (Item) entry;
            if (item == null) {
                linearLayout2.addView(createGap(), LayoutHelper.createLinear(-1, 8));
                height += 8;
                continue;
            }
            final Activity activity = parentActivity;
            final ActionBarMenuSubItem subItem = new ActionBarMenuSubItem((Context) baseFragment.getParentActivity(), false, false, resourcesProvider);
            subItem.setTextAndIcon(item.title, item.resId);
            subItem.setMinimumWidth(AndroidUtilities.dp(196.0f));
            linearLayout2.addView(subItem, LayoutHelper.createLinear(-1, 48));
            height += 48;
            if (item.subtitle != null) {
                subItem.setSubtext(item.subtitle);
                subItem.subtextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                subItem.subtextView.setMarqueeRepeatLimit(-1);
                subItem.subtextView.setSelected(true);
                subItem.setItemHeight(56);
                height += 8;
            }
            if (item.id == SET_OWNER && this.ownerId > 0) {
                DevGramChatUtils.searchUserById(this.ownerId, user -> MessageDetailsPopupWrapper.$r8$lambda$dE2BmWFMp3ZO7Tcpe0bsPv1CgxY(item, subItem, user));
            } else if (item.id == LOCATION) {
                DevGramChatUtils.utilsQueue.postRunnable(() -> lambda_new_4(subItem, item));
            } else if (item.id == BITRATE) {
                DevGramChatUtils.utilsQueue.postRunnable(() -> lambda_new_6(messageObject2, subItem, item));
            } else if (item.id == RESOLUTION) {
                DevGramChatUtils.utilsQueue.postRunnable(() -> lambda_new_8(isVideoLike, messageObject2, subItem, item));
            } else if (item.id == PLATFORM) {
                DevGramChatUtils.utilsQueue.postRunnable(() -> lambda_new_10(messageObject2, subItem, item));
            }
            subItem.setTag(item);
            subItem.setOnClickListener(view -> lambda_new_11(item, activity, isPhotoLike, isVideoLike, messageObject2, baseFragment, view));
            subItem.setOnLongClickListener(view -> lambda_new_12(item, view));
        }
        if (height > 380 && Math.abs(height - 380) > 112) {
            this.swipeBack.addView(scrollView2, LayoutHelper.createLinear(-1, 380));
        } else {
            this.swipeBack.addView(scrollView2, LayoutHelper.createLinear(-1, -2));
        }
    }

    public static /* synthetic */ void $r8$lambda$dE2BmWFMp3ZO7Tcpe0bsPv1CgxY(Item item, ActionBarMenuSubItem actionBarMenuSubItem, TLRPC.User user) {
        if (user != null) {
            if (!TextUtils.isEmpty(UserObject.getPublicUsername(user))) {
                item.subtitle = "@" + UserObject.getPublicUsername(user);
            } else {
                item.subtitle = ContactsController.formatName(user);
            }
            actionBarMenuSubItem.setSubtext(item.subtitle);
        }
    }
    public /* synthetic */ void lambda_new_4(final ActionBarMenuSubItem actionBarMenuSubItem, final Item item) {
        this.geo = getLatLongFromPhoto(new File(this.filePath));
        AndroidUtilities.runOnUIThread(() -> lambda_new_3(actionBarMenuSubItem, item));
    }
    public /* synthetic */ void lambda_new_3(ActionBarMenuSubItem actionBarMenuSubItem, Item item) {
        if (this.geo != null) {
            actionBarMenuSubItem.setSubtext(this.geo[0] + ", " + this.geo[1]);
            item.subtitle = this.geo[0] + ", " + this.geo[1];
            return;
        }
        actionBarMenuSubItem.setVisibility(8);
    }
    public /* synthetic */ void lambda_new_6(MessageObject messageObject, final ActionBarMenuSubItem actionBarMenuSubItem, final Item item) {
        final int bitrate = getBitrate(messageObject, this.filePath);
        AndroidUtilities.runOnUIThread(() -> MessageDetailsPopupWrapper.$r8$lambda$f_aN3ZZNn5AFFp878etwVxDefTs(bitrate, actionBarMenuSubItem, item));
    }

    public static /* synthetic */ void $r8$lambda$f_aN3ZZNn5AFFp878etwVxDefTs(int i, ActionBarMenuSubItem actionBarMenuSubItem, Item item) {
        if (i > 0) {
            actionBarMenuSubItem.setSubtext(i + " Kbps");
            item.subtitle = i + " Kbps";
            return;
        }
        actionBarMenuSubItem.setVisibility(8);
    }
    public /* synthetic */ void lambda_new_8(boolean z, MessageObject messageObject, final ActionBarMenuSubItem actionBarMenuSubItem, final Item item) {
        String str = this.filePath;
        final Dimension videoResolution = z ? getVideoResolution(messageObject, str) : getPhotoResolution(messageObject, str);
        AndroidUtilities.runOnUIThread(() -> MessageDetailsPopupWrapper.$r8$lambda$nKLUxPKKYKjJIhJVpxjuwM7e7_4(videoResolution, actionBarMenuSubItem, item));
    }

    public static /* synthetic */ void $r8$lambda$nKLUxPKKYKjJIhJVpxjuwM7e7_4(Dimension dimension, ActionBarMenuSubItem actionBarMenuSubItem, Item item) {
        if (dimension != null) {
            actionBarMenuSubItem.setSubtext(dimension.toString());
            item.subtitle = dimension.toString();
        } else {
            actionBarMenuSubItem.setVisibility(8);
        }
    }
    public /* synthetic */ void lambda_new_10(MessageObject messageObject, final ActionBarMenuSubItem actionBarMenuSubItem, final Item item) {
        final String photoPlatform;
        if (messageObject.isVoice()) {
            OpusFingerprint opusFingerprint = OpusFingerprint.parse(this.filePath);
            this.voiceFingerprint = opusFingerprint;
            photoPlatform = MediaUtils.getVoicePlatform(opusFingerprint);
        } else {
            boolean zIsRoundVideo = messageObject.isRoundVideo();
            String str = this.filePath;
            if (zIsRoundVideo) {
                Mp4Fingerprint mp4Fingerprint = Mp4Fingerprint.parse(str);
                this.roundVideoFingerprint = mp4Fingerprint;
                photoPlatform = MediaUtils.getRoundVideoPlatform(mp4Fingerprint);
            } else {
                JpegFingerprint jpegFingerprint = JpegFingerprint.parse(str);
                this.photoFingerprint = jpegFingerprint;
                photoPlatform = MediaUtils.getPhotoPlatform(jpegFingerprint);
            }
        }
        AndroidUtilities.runOnUIThread(() -> MessageDetailsPopupWrapper.m1222$r8$lambda$8KY68BRgfBrUh0xLJdGa54yNS4(photoPlatform, actionBarMenuSubItem, item));
    }
    public static /* synthetic */ void m1222$r8$lambda$8KY68BRgfBrUh0xLJdGa54yNS4(String str, ActionBarMenuSubItem actionBarMenuSubItem, Item item) {
        if (!TextUtils.isEmpty(str)) {
            actionBarMenuSubItem.setSubtext(str);
            item.subtitle = str;
        } else {
            actionBarMenuSubItem.setVisibility(8);
        }
    }
    public /* synthetic */ void lambda_new_11(Item item, Activity activity, boolean z, boolean z2, MessageObject messageObject, BaseFragment baseFragment, View view) {
        closeMenu();
        if (item.id == 1 && !TextUtils.isEmpty(this.filePath)) {
            try {
                Uri uriForFile = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", new File(this.filePath));
                if (z || z2) {
                    Intent intent = new Intent("android.intent.action.VIEW");
                    intent.setFlags(1);
                    intent.setDataAndType(uriForFile, messageObject.getMimeType());
                    if (!activity.getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
                        activity.startActivity(intent);
                        return;
                    }
                }
                Intent intent2 = new Intent("android.intent.action.SEND");
                intent2.setFlags(1);
                intent2.putExtra("android.intent.extra.STREAM", uriForFile);
                intent2.setDataAndType(uriForFile, messageObject.getMimeType());
                activity.startActivityForResult(Intent.createChooser(intent2, LocaleController.getString(R.string.ShareFile)), 500);
                return;
            } catch (IllegalArgumentException e) {
                FileLog.e(e);
                return;
            }
        }
        int i = item.id;
        if (i == 0) {
            if (item.subtitle.startsWith("@")) {
                Bundle bundle = new Bundle();
                bundle.putLong("user_id", this.ownerId);
                baseFragment.presentFragment(new ProfileActivity(bundle));
                return;
            }
            copy(String.valueOf(this.ownerId));
            return;
        }
        if (i == 2) {
            Activity parentActivity = baseFragment.getParentActivity();
            String[] strArr = this.geo;
            Browser.openUrl(parentActivity, String.format("https://maps.google.com/?q=%s,%s", strArr[0], strArr[1]));
        } else {
            String str = item.subtitle;
            if (str == null) {
                str = item.title;
            }
            copy(str);
        }
    }
    public /* synthetic */ boolean lambda_new_12(Item item, View view) {
        String strDescribe;
        Mp4Fingerprint mp4Fingerprint;
        OpusFingerprint opusFingerprint;
        JpegFingerprint jpegFingerprint;
        if (item.id == 1 && !TextUtils.isEmpty(this.filePath)) {
            strDescribe = this.filePath;
        } else {
            int i = item.id;
            if (i == 0) {
                strDescribe = String.valueOf(this.ownerId);
            } else if (i == 5 && (jpegFingerprint = this.photoFingerprint) != null) {
                strDescribe = jpegFingerprint.describe();
            } else if (i == 5 && (opusFingerprint = this.voiceFingerprint) != null) {
                strDescribe = opusFingerprint.vendor;
            } else if (i == 5 && (mp4Fingerprint = this.roundVideoFingerprint) != null) {
                strDescribe = mp4Fingerprint.describe();
            } else {
                String str = item.subtitle;
                strDescribe = str != null ? str : item.title;
            }
        }
        copy(strDescribe);
        return true;
    }

    public static int getBitrate(MessageObject messageObject, String str) {
        int bitrateFromPath;
        if (TextUtils.isEmpty(str)) {
            bitrateFromPath = -1;
        } else {
            try {
                bitrateFromPath = getBitrateFromPath(str);
            } catch (Exception e) {
                FileLog.e(e);
                bitrateFromPath = -1;
            }
        }
        if (bitrateFromPath != -1) {
            return bitrateFromPath;
        }
        try {
            return getBitrateFromAttributes(messageObject);
        } catch (Exception e2) {
            FileLog.e(e2);
            return bitrateFromPath;
        }
    }

    public static int getBitrateFromPath(String str) {
        int i;
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        try {
            mediaMetadataRetriever.setDataSource(str);
            String strExtractMetadata = mediaMetadataRetriever.extractMetadata(20);
            Objects.requireNonNull(strExtractMetadata);
            i = Integer.parseInt(strExtractMetadata) / 1000;
        } catch (Exception e) {
            FileLog.e(e);
            i = -1;
        }
        try {
            mediaMetadataRetriever.release();
        } catch (Throwable th) {
            FileLog.e(th);
        }
        return i;
    }

    public static int getBitrateFromAttributes(MessageObject messageObject) {
        long messageSize = MessageObject.getMessageSize(messageObject.messageOwner);
        if (messageSize > 0 && MessageObject.getMedia(messageObject.messageOwner) != null && MessageObject.getMedia(messageObject.messageOwner).document != null) {
            ArrayList<TLRPC.DocumentAttribute> arrayList = MessageObject.getMedia(messageObject.messageOwner).document.attributes;
            int size = arrayList.size();
            int i = 0;
            while (i < size) {
                TLRPC.DocumentAttribute documentAttribute = arrayList.get(i);
                i++;
                TLRPC.DocumentAttribute documentAttribute2 = documentAttribute;
                if ((documentAttribute2 instanceof TLRPC.TL_documentAttributeAudio) || (documentAttribute2 instanceof TLRPC.TL_documentAttributeVideo)) {
                    double d = documentAttribute2.duration;
                    if (d > 0.0d) {
                        return (int) (((messageSize / d) * 8.0d) / 1000.0d);
                    }
                }
            }
        }
        return -1;
    }

    public static Dimension getPhotoResolution(MessageObject messageObject, String str) {
        Dimension photoResolutionFromPath;
        if (TextUtils.isEmpty(str)) {
            photoResolutionFromPath = null;
        } else {
            try {
                photoResolutionFromPath = getPhotoResolutionFromPath(str);
            } catch (Exception e) {
                FileLog.e(e);
                photoResolutionFromPath = null;
            }
        }
        if (photoResolutionFromPath != null) {
            return photoResolutionFromPath;
        }
        try {
            return getPhotoResolutionFromAttributes(messageObject);
        } catch (Exception e2) {
            FileLog.e(e2);
            return photoResolutionFromPath;
        }
    }

    public static Dimension getPhotoResolutionFromPath(String str) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(str, options);
        return new Dimension(options.outWidth, options.outHeight);
    }

    public static Dimension getPhotoResolutionFromAttributes(MessageObject messageObject) {
        int i;
        int i2;
        TLRPC.VideoSize closestVideoSizeWithSize;
        int i3;
        int i4;
        int i5;
        int i6;
        int i7 = 0;
        Dimension dimension = null;
        if (MessageObject.getMedia(messageObject.messageOwner) != null && MessageObject.getMedia(messageObject.messageOwner).photo != null) {
            TLRPC.PhotoSize closestPhotoSizeWithSize = FileLoader.getClosestPhotoSizeWithSize(MessageObject.getMedia(messageObject.messageOwner).photo.sizes, AndroidUtilities.getPhotoSize(), false, null, true);
            if (closestPhotoSizeWithSize != null && (i5 = closestPhotoSizeWithSize.w) > 0 && (i6 = closestPhotoSizeWithSize.h) > 0) {
                dimension = new Dimension(i5, i6);
            }
            return (dimension != null || (closestVideoSizeWithSize = FileLoader.getClosestVideoSizeWithSize(MessageObject.getMedia(messageObject.messageOwner).photo.video_sizes, AndroidUtilities.getPhotoSize(), false, true)) == null || (i3 = closestVideoSizeWithSize.w) <= 0 || (i4 = closestVideoSizeWithSize.h) <= 0) ? dimension : new Dimension(i3, i4);
        }
        if (MessageObject.getMedia(messageObject.messageOwner) != null && MessageObject.getMedia(messageObject.messageOwner).document != null) {
            ArrayList<TLRPC.DocumentAttribute> arrayList = MessageObject.getMedia(messageObject.messageOwner).document.attributes;
            int size = arrayList.size();
            while (i7 < size) {
                TLRPC.DocumentAttribute documentAttribute = arrayList.get(i7);
                i7++;
                TLRPC.DocumentAttribute documentAttribute2 = documentAttribute;
                if ((documentAttribute2 instanceof TLRPC.TL_documentAttributeImageSize) && (i = documentAttribute2.w) > 0 && (i2 = documentAttribute2.h) > 0) {
                    return new Dimension(i, i2);
                }
            }
        }
        return null;
    }

    public static Dimension getVideoResolution(MessageObject messageObject, String str) {
        Dimension videoResolutionFromPath;
        if (TextUtils.isEmpty(str)) {
            videoResolutionFromPath = null;
        } else {
            try {
                videoResolutionFromPath = getVideoResolutionFromPath(str);
            } catch (Exception e) {
                FileLog.e(e);
                videoResolutionFromPath = null;
            }
        }
        if (videoResolutionFromPath != null) {
            return videoResolutionFromPath;
        }
        try {
            return getVideoResolutionFromAttributes(messageObject);
        } catch (Exception e2) {
            FileLog.e(e2);
            return videoResolutionFromPath;
        }
    }

    public static Dimension getVideoResolutionFromPath(String str) {
        int width = 0, height = 0;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(str);
            width = Integer.parseInt(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)));
            height = Integer.parseInt(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)));
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            retriever.release();
        } catch (Throwable th) {
            FileLog.e(th);
        }
        return new Dimension(width, height);
    }

    public static Dimension getVideoResolutionFromAttributes(MessageObject messageObject) {
        int i;
        int i2;
        if (MessageObject.getMedia(messageObject.messageOwner) == null || MessageObject.getMedia(messageObject.messageOwner).document == null) {
            return null;
        }
        ArrayList<TLRPC.DocumentAttribute> arrayList = MessageObject.getMedia(messageObject.messageOwner).document.attributes;
        int size = arrayList.size();
        int i3 = 0;
        while (i3 < size) {
            TLRPC.DocumentAttribute documentAttribute = arrayList.get(i3);
            i3++;
            TLRPC.DocumentAttribute documentAttribute2 = documentAttribute;
            if ((documentAttribute2 instanceof TLRPC.TL_documentAttributeVideo) && (i = documentAttribute2.w) > 0 && (i2 = documentAttribute2.h) > 0) {
                return new Dimension(i, i2);
            }
        }
        return null;
    }

    public static String[] getLatLongFromPhoto(File file) {
        try {
            ExifInterface exifInterface = new ExifInterface(file.getAbsolutePath());
            String attribute = exifInterface.getAttribute("GPSLatitude");
            String attribute2 = exifInterface.getAttribute("GPSLongitude");
            String attribute3 = exifInterface.getAttribute("GPSLatitudeRef");
            String attribute4 = exifInterface.getAttribute("GPSLongitudeRef");
            if (attribute == null || attribute2 == null || attribute3 == null || attribute4 == null) {
                return null;
            }
            double dConvertToDegrees = convertToDegrees(attribute);
            if ("S".equalsIgnoreCase(attribute3)) {
                dConvertToDegrees = -dConvertToDegrees;
            }
            double dConvertToDegrees2 = convertToDegrees(attribute2);
            if ("W".equalsIgnoreCase(attribute4)) {
                dConvertToDegrees2 = -dConvertToDegrees2;
            }
            DecimalFormat decimalFormat = new DecimalFormat("#.######");
            decimalFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
            return new String[]{decimalFormat.format(dConvertToDegrees), decimalFormat.format(dConvertToDegrees2)};
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static double convertToDegrees(String str) {
        String[] strArrSplit = str.split(",");
        return convertToDouble(strArrSplit[0]) + (convertToDouble(strArrSplit[1]) / 60.0d) + (convertToDouble(strArrSplit[2]) / 3600.0d);
    }

    private static double convertToDouble(String str) {
        String[] strArrSplit = str.split("/");
        if (strArrSplit.length == 1) {
            return Double.parseDouble(strArrSplit[0]);
        }
        if (strArrSplit.length == 2) {
            double d = Double.parseDouble(strArrSplit[0]);
            double d2 = Double.parseDouble(strArrSplit[1]);
            if (d2 != 0.0d) {
                return d / d2;
            }
            FileLog.e("Division by zero in GPS data");
            return 0.0d;
        }
        FileLog.e("Invalid rational number format: ".concat(str));
        return 0.0d;
    }

    private boolean isPhotoAsDocument(MessageObject messageObject) {
        try {
            if (MessageObject.getMedia(messageObject.messageOwner) != null && MessageObject.getMedia(messageObject.messageOwner).document != null) {
                ArrayList<TLRPC.DocumentAttribute> arrayList = MessageObject.getMedia(messageObject.messageOwner).document.attributes;
                int size = arrayList.size();
                int i = 0;
                while (i < size) {
                    TLRPC.DocumentAttribute documentAttribute = arrayList.get(i);
                    i++;
                    TLRPC.DocumentAttribute documentAttribute2 = documentAttribute;
                    if ((documentAttribute2 instanceof TLRPC.TL_documentAttributeImageSize) && documentAttribute2.w > 0 && documentAttribute2.h > 0) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private String formatTime(int i, boolean z) {
        if (i == 2147483646) {
            return LocaleController.getString(R.string.SendWhenOnline);
        }
        if (z) {
            long j = ((long) i) * 1000;
            return LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterYear().format(new Date(j)), LocaleController.getInstance().getFormatterDayWithSeconds().format(new Date(j)));
        }
        return LocaleController.formatDateAudio(i, true);
    }

    private View createGap() {
        return new ActionBarPopupWindow.GapView(this.fragment.getContext(), this.resourcesProvider);
    }

    public static class Item {
        int id;
        int resId;
        String subtitle;
        String title;

        public Item(int i, String str, String str2) {
            this(-1, i, str, str2);
        }

        public Item(int i, String str, int i2) {
            this(-1, i, str, String.valueOf(i2));
        }

        public Item(int i, int i2, String str, String str2) {
            this.id = i;
            this.resId = i2;
            this.title = str;
            this.subtitle = str2;
        }
    }
}

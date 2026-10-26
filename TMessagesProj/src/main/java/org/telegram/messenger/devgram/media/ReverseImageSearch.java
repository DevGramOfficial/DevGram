package org.telegram.messenger.devgram.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
public abstract class ReverseImageSearch {
    private static final String UPLOAD_URL = "https://yandex.com/images/touch/search?rpt=imageview&format=json&request=" + Uri.encode("{\"blocks\":[{\"block\":\"cbir-uploader__get-cbir-id\"}]}");

    public enum Provider {
        YANDEX,
        GOOGLE,
        BING,
        TINEYE
    }

    public static Runnable search(final File file, final Provider provider, final Utilities.Callback<String> callback) {
        final AtomicBoolean atomicBoolean = new AtomicBoolean();
        final AtomicReference atomicReference = new AtomicReference();
        Utilities.globalQueue.postRunnable(() -> ReverseImageSearch.m1238$r8$lambda$3o5J5mMV6jBElrtsegbb3jkqqk(file, atomicBoolean, callback, atomicReference, provider));
        return () -> ReverseImageSearch.$r8$lambda$AQNPVPdHoWFPzxBgtBUBUecJwjg(atomicBoolean, atomicReference);
    }
    public static /* synthetic */ void m1238$r8$lambda$3o5J5mMV6jBElrtsegbb3jkqqk(File file, final AtomicBoolean atomicBoolean, final Utilities.Callback callback, AtomicReference atomicReference, final Provider provider) {
        byte[] bArrEncodeImage;
        try {
            bArrEncodeImage = encodeImage(file);
        } catch (Throwable th) {
            FileLog.e(th);
            bArrEncodeImage = null;
        }
        if (atomicBoolean.get()) {
            return;
        }
        if (bArrEncodeImage == null) {
            deliver(atomicBoolean, callback, null);
            return;
        }
        Call callNewCall = org.telegram.messenger.devgram.player.PlayerHttp.client().newCall(buildUploadRequest(bArrEncodeImage));
        atomicReference.set(callNewCall);
        if (atomicBoolean.get()) {
            callNewCall.cancel();
        } else {
            callNewCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException iOException) {
                    if (!call.isCanceled()) {
                        FileLog.e(iOException);
                    }
                    ReverseImageSearch.deliver(atomicBoolean, callback, null);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    String strBuildUrl = null;
                    try {
                        try {
                            ResponseBody responseBodyBody = response.body();
                            if (response.isSuccessful() && responseBodyBody != null) {
                                JSONObject jSONObject = new JSONObject(responseBodyBody.string()).getJSONArray("blocks").getJSONObject(0).getJSONObject("params");
                                String strOptString = jSONObject.optString("cbirId");
                                String strOptString2 = jSONObject.optString("originalImageUrl");
                                if (!TextUtils.isEmpty(strOptString) && !TextUtils.isEmpty(strOptString2)) {
                                    strBuildUrl = ReverseImageSearch.buildUrl(provider, response.request().url().host(), strOptString, strOptString2);
                                }
                            }
                            response.close();
                        } catch (Throwable th2) {
                            if (response != null) {
                                try {
                                    response.close();
                                } catch (Throwable th3) {
                                    th2.addSuppressed(th3);
                                }
                            }
                            throw th2;
                        }
                    } catch (Throwable th4) {
                        FileLog.e(th4);
                    }
                    ReverseImageSearch.deliver(atomicBoolean, callback, strBuildUrl);
                }
            });
        }
    }

    public static /* synthetic */ void $r8$lambda$AQNPVPdHoWFPzxBgtBUBUecJwjg(AtomicBoolean atomicBoolean, AtomicReference atomicReference) {
        atomicBoolean.set(true);
        Call call = (Call) atomicReference.get();
        if (call != null) {
            call.cancel();
        }
    }

    public static void open(Context context, String str) {
        seedConsentCookies();
        Browser.openInTelegramBrowser(context, str, null);
    }
    public static void deliver(final AtomicBoolean atomicBoolean, final Utilities.Callback<String> callback, final String str) {
        AndroidUtilities.runOnUIThread(() -> ReverseImageSearch.m1239$r8$lambda$qwn5HLXZuahNnpwTi6itIh9jYs(atomicBoolean, callback, str));
    }
    public static /* synthetic */ void m1239$r8$lambda$qwn5HLXZuahNnpwTi6itIh9jYs(AtomicBoolean atomicBoolean, Utilities.Callback callback, String str) {
        if (atomicBoolean.get()) {
            return;
        }
        callback.run(str);
    }

    private static Request buildUploadRequest(byte[] bArr) {
        Request.Builder builderHeader = new Request.Builder().url(UPLOAD_URL).post(new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("upfile", "image.jpg", RequestBody.create(bArr, MediaType.get("image/jpeg"))).build()).header("Accept", "application/json").header("X-Requested-With", "XMLHttpRequest").header("Cookie", "gdpr=0");
        try {
            String defaultUserAgent = WebSettings.getDefaultUserAgent(ApplicationLoader.applicationContext);
            if (!TextUtils.isEmpty(defaultUserAgent)) {
                builderHeader.header("User-Agent", defaultUserAgent);
            }
        } catch (Throwable th) {
            FileLog.e(th);
        }
        return builderHeader.build();
    }
    public static String buildUrl(Provider provider, String str, String str2, String str3) {
        String strEncode = Uri.encode(str3);
        switch (provider) {
            case YANDEX:
                return "https://" + str + "/images/search?rpt=imageview&cbir_id=" + Uri.encode(str2) + "&url=" + strEncode;
            case GOOGLE:
                return "https://lens.google.com/uploadbyurl?url=" + strEncode;
            case BING:
                return "https://www.bing.com/images/search?view=detailv2&iss=sbi&form=SBIVSP&sbisrc=UrlPaste&q=imgurl:" + strEncode;
            default:
                return "https://tineye.com/search?url=" + strEncode;
        }
    }

    private static void seedConsentCookies() {
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setCookie("https://www.google.com", "SOCS=CAISHAgBEhJnd3NfMjAyNjA2MjYtMF9SQzEaAmVuIAEaBgiAjozSBg; Domain=.google.com; Path=/; Secure; SameSite=Lax");
            cookieManager.setCookie("https://www.bing.com", "BCP=AD=1&AL=1&SM=1; Domain=.bing.com; Path=/; Secure");
            cookieManager.setCookie("https://tineye.com", "cookie_consent=accepted; Path=/");
            cookieManager.setCookie("https://yandex.com", "gdpr=0; Domain=.yandex.com; Path=/; Secure");
            cookieManager.setCookie("https://yandex.ru", "gdpr=0; Domain=.yandex.ru; Path=/; Secure");
            cookieManager.flush();
        } catch (Throwable th) {
            FileLog.e(th);
        }
    }

    private static byte[] encodeImage(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        BitmapFactory.Options options2 = new BitmapFactory.Options();
        int i = 1;
        while (Math.max(options.outWidth, options.outHeight) / i > 2560) {
            i *= 2;
        }
        options2.inSampleSize = i;
        Bitmap bitmapDecodeFile = BitmapFactory.decodeFile(file.getAbsolutePath(), options2);
        if (bitmapDecodeFile == null) {
            return null;
        }
        int width = bitmapDecodeFile.getWidth();
        int height = bitmapDecodeFile.getHeight();
        int iMax = Math.max(width, height);
        if (iMax > 1280) {
            float f = 1280.0f / iMax;
            Bitmap bitmapCreateScaledBitmap = Bitmap.createScaledBitmap(bitmapDecodeFile, Math.round(width * f), Math.round(height * f), true);
            if (bitmapCreateScaledBitmap != bitmapDecodeFile) {
                bitmapDecodeFile.recycle();
                bitmapDecodeFile = bitmapCreateScaledBitmap;
            }
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmapDecodeFile.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
        bitmapDecodeFile.recycle();
        return byteArrayOutputStream.toByteArray();
    }
}

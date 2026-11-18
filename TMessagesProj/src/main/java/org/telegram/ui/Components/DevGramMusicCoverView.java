package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.LruCache;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

/** Small presence artwork is decoded once off the UI thread, and never written to disk. */
public final class DevGramMusicCoverView extends BackupImageView {
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(2*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
    public static void trimMemory(){CACHE.evictAll();}
    private String encoded="",url="";private int generation;
    public DevGramMusicCoverView(Context context){super(context);setRoundRadius(AndroidUtilities.dp(13));}
    public void bind(String encoded,String url){if(this.encoded.equals(encoded)&&this.url.equals(url))return;this.encoded=encoded;this.url=url;if(encoded.isEmpty()&&!url.startsWith("https://")){generation++;setImageDrawable(null);return;}load();}
    private void load(){
        int request=++generation;String source=encoded;
        if(source.isEmpty()){if(url.startsWith("https://"))setImage(url,"256_256",null);return;}
        if(source.length()>44000)return;
        Bitmap cached=CACHE.get(source);if(cached!=null&&!cached.isRecycled()){setImageBitmap(cached);return;}
        Utilities.globalQueue.postRunnable(()->{Bitmap decoded=null;try{byte[] bytes=Base64.decode(source,Base64.NO_WRAP);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outWidth<=512&&bounds.outHeight<=512)decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(Exception ignored){}Bitmap ready=decoded;if(ready!=null)CACHE.put(source,ready);AndroidUtilities.runOnUIThread(()->{if(request==generation&&ready!=null)setImageBitmap(ready);});});
    }
    public static void decode(String encoded,org.telegram.messenger.Utilities.Callback<Bitmap> callback){
        if(encoded.isEmpty()||encoded.length()>44000){callback.run(null);return;}
        Bitmap cached=CACHE.get(encoded);if(cached!=null){callback.run(cached);return;}
        Utilities.globalQueue.postRunnable(()->{Bitmap ready=null;try{byte[] bytes=Base64.decode(encoded,Base64.NO_WRAP);BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,b);if(b.outWidth>0&&b.outHeight>0&&b.outWidth<=512&&b.outHeight<=512)ready=BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(Exception ignored){}Bitmap decoded=ready;if(decoded!=null)CACHE.put(encoded,decoded);AndroidUtilities.runOnUIThread(()->callback.run(decoded));});
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();load();}
    @Override protected void onDetachedFromWindow(){generation++;super.onDetachedFromWindow();}
}

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;

/** Only artwork from the selected, opted-in media session. Compression runs on the publish queue. */
public final class DevGramMusicArtwork {
    private static final LinkedHashMap<String,Entry> cache=new LinkedHashMap<>();
    private static final DispatchQueue queue=new DispatchQueue("DevGram-music-artwork");
    private static class Entry {WeakReference<Bitmap> bitmap;int generation;String encoded,uri="",source;boolean pending;}
    private static String key(long uid,String source,String title,String artist){return uid+"\n"+source+"\n"+title+"\n"+artist;}
    public static void remember(long uid,String source,String title,String artist,Bitmap bitmap){remember(uid,source,title,artist,bitmap,"");}
    public static synchronized void remember(long uid,String source,String title,String artist,Bitmap bitmap,String uri){
        String key=key(uid,source,title,artist);Entry old=cache.get(key);int generation=bitmap!=null&&!bitmap.isRecycled()?bitmap.getGenerationId():(uri==null?0:uri.hashCode());
        if(old!=null&&old.generation==generation)return;
        Entry e=new Entry();e.bitmap=new WeakReference<>(bitmap);e.generation=generation;e.uri=uri==null?"":uri;e.source=source;cache.put(key,e);
        while(cache.size()>12)cache.remove(cache.keySet().iterator().next());
    }
    public static synchronized int version(long uid,String source,String title,String artist){Entry e=cache.get(key(uid,source,title,artist));return e==null?0:e.generation^(e.encoded==null?0:e.encoded.hashCode());}
    public static String encoded(long uid,String source,String title,String artist){
        Entry e; synchronized(DevGramMusicArtwork.class){e=cache.get(key(uid,source,title,artist));}
        if(e==null)return "";
        synchronized(DevGramMusicArtwork.class){if(e.encoded!=null)return e.encoded;if(e.pending)return "";e.pending=true;}
        queue.postRunnable(()->{Bitmap original=e.bitmap.get();boolean owned=false;String result="";
            try{if(original==null&&!e.uri.isEmpty()){original=read(e.source,e.uri);owned=true;}
                if(original!=null&&!original.isRecycled()){
                    float scale=Math.min(1,256f/Math.max(original.getWidth(),original.getHeight()));Bitmap small=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*scale)),Math.max(1,Math.round(original.getHeight()*scale)),true);
                    try{ByteArrayOutputStream output=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,75,output);if(output.size()<=32768)result=Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP);}finally{if(small!=original)small.recycle();}
                }
            }catch(Exception ignored){}finally{if(owned&&original!=null)original.recycle();}
            synchronized(DevGramMusicArtwork.class){e.encoded=result;e.pending=false;}
            DevGramMusicPresence.artworkReady();
        });return "";
    }
    private static Bitmap read(String source,String value)throws Exception{
        if(value.length()>4096)return null;android.net.Uri uri=android.net.Uri.parse(value);java.io.InputStream stream=null;java.net.HttpURLConnection connection=null;
        try{
            if("content".equals(uri.getScheme())||"android.resource".equals(uri.getScheme())){
                String authority=uri.getAuthority();if(authority==null||!(authority.equals("media")||authority.startsWith(source+".")||authority.equals(source)||authority.equals("com.android.providers.media")))return null;
                stream=ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
            }else if("https".equals(uri.getScheme())){
                java.net.URL url=new java.net.URL(value);String host=url.getHost().toLowerCase(java.util.Locale.ROOT);boolean allowed=false;for(String domain:new String[]{"scdn.co","spotifycdn.com","sndcdn.com","yandex.net","yandex.ru","userapi.com"})allowed|=host.equals(domain)||host.endsWith("."+domain);
                if(!allowed||url.getUserInfo()!=null||url.getPort()!=-1&&url.getPort()!=443)return null;connection=(java.net.HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(3000);connection.setReadTimeout(3000);if(connection.getResponseCode()!=200)return null;stream=connection.getInputStream();
            }
            if(stream==null)return null;ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1){if(bytes.size()+n>1024*1024)return null;bytes.write(buffer,0,n);}byte[] raw=bytes.toByteArray();android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.length,options);if(options.outWidth<=0||options.outHeight<=0||options.outWidth>8192||options.outHeight>8192)return null;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>512)options.inSampleSize*=2;options.inJustDecodeBounds=false;return android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.length,options);
        }finally{if(stream!=null)try{stream.close();}catch(Exception ignored){}if(connection!=null)connection.disconnect();}
    }
    public static synchronized void clear(){cache.clear();}
}

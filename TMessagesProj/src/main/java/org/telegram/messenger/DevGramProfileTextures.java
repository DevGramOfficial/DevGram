package org.telegram.messenger;

import android.net.Uri;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.util.LinkedHashSet;

/** Uploads source images, while the surrounding profile draft remains unpublished. */
public final class DevGramProfileTextures {
    private DevGramProfileTextures() {}
    public static void upload(int account,Uri uri,DevGramProfilePolicy.Callback callback){
        upload(account,uri,"frame",callback);
    }
    public static void upload(int account,Uri uri,String kind,DevGramProfilePolicy.Callback callback){upload(account,uri,kind,false,callback);}
    public static void uploadGalleryPhoto(int account,Uri uri,String kind,DevGramProfilePolicy.Callback callback){upload(account,uri,kind,true,callback);}
    private static byte[] readTexture(InputStream input)throws IOException{
        if(input==null)throw new IOException();ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){bytes.write(buffer,0,count);if(bytes.size()>5*1024*1024)throw new IOException();}
        if(bytes.size()<12)throw new IOException();return bytes.toByteArray();
    }
    private static byte[] galleryPhoto(Uri uri)throws IOException{
        try(BufferedInputStream input=new BufferedInputStream(ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri))){
            input.mark(6);byte[] header=new byte[6];int count=input.read(header);input.reset();
            if(count==6&&header[0]=='G'&&header[1]=='I'&&header[2]=='F')return readTexture(input);
        }
        android.graphics.Bitmap bitmap=ImageLoader.loadBitmap(null,uri,2048,2048,true);if(bitmap==null)throw new IOException();
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            android.graphics.Bitmap.CompressFormat format=bitmap.hasAlpha()?android.graphics.Bitmap.CompressFormat.PNG:android.graphics.Bitmap.CompressFormat.JPEG;
            if(!bitmap.compress(format,90,bytes))throw new IOException();
            if(bytes.size()>5*1024*1024){android.graphics.Bitmap smaller=android.graphics.Bitmap.createScaledBitmap(bitmap,Math.max(1,bitmap.getWidth()/2),Math.max(1,bitmap.getHeight()/2),true);if(smaller!=bitmap){bitmap.recycle();bitmap=smaller;}bytes.reset();if(!bitmap.compress(format,90,bytes))throw new IOException();}
            if(bytes.size()<12||bytes.size()>5*1024*1024)throw new IOException();return bytes.toByteArray();
        }finally{bitmap.recycle();}
    }
    private static void upload(int account,Uri uri,String kind,boolean preparePhoto,DevGramProfilePolicy.Callback callback){
        long owner=UserConfig.getInstance(account).getClientUserId();
        String feature=kind.startsWith("badge")?"chat.badge":kind.startsWith("row")?"profile.blocks.media":"avatar.nodes";if(kind.endsWith("_animated"))feature+=".animated";
        final String required=feature;
        if(!DevGramProfilePolicy.allowed(owner,required)){callback.run(null,"feature_locked");return;}
        DevGramNetworkQueue.postRunnable(()->{
            JSONObject body=null;
            try{
                byte[] bytes;if(preparePhoto&&!kind.endsWith("_animated"))bytes=galleryPhoto(uri);else try(InputStream input=ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)){bytes=readTexture(input);}
                body=new JSONObject().put("kind",kind).put("data",Base64.encodeToString(bytes,Base64.NO_WRAP));
            }catch(Exception ignored){}
            final JSONObject fields=body;
            AndroidUtilities.runOnUIThread(()->{
                if(fields==null){callback.run(null,"bad_texture");return;}
                if(UserConfig.getInstance(account).getClientUserId()!=owner||!DevGramProfilePolicy.allowed(owner,required)){callback.run(null,"feature_locked");return;}
                DevGramProfilePolicy.request(account,"studio/textures/upload",fields,callback);
            });
        });
    }
    public static JSONArray sources(JSONObject document){
        LinkedHashSet<String> selected=new LinkedHashSet<>();JSONObject graph=document.optJSONObject("frameGraph");JSONArray nodes=graph==null?null:graph.optJSONArray("nodes");
        String badge=document.optString("chatBadgeSource");if(badge.matches("asset:[a-f0-9]{64}\\.(png|jpg|gif|webp)"))selected.add(badge);
        if(nodes!=null)for(int i=0;i<nodes.length();i++){JSONObject node=nodes.optJSONObject(i);JSONArray values=node==null?null:node.optJSONArray("s");if(values!=null)for(int k=0;k<values.length();k++){String value=values.optString(k);if(value.matches("asset:[a-f0-9]{64}\\.(png|jpg|gif|webp)"))selected.add(value);}}
        JSONArray blocks=document.optJSONArray("blocks");if(blocks!=null)for(int i=0;i<blocks.length();i++){JSONObject block=blocks.optJSONObject(i);String source=block==null?"":block.optString("url");if(source.matches("asset:[a-f0-9]{64}\\.(png|jpg|gif|webp)"))selected.add(source);}
        JSONArray result=new JSONArray();for(String value:selected)result.put(value);return result;
    }
    public static String imageUrl(String source){if(source!=null&&source.matches("asset:[a-f0-9]{64}\\.(png|jpg|gif|webp)"))return "https://api.devgram.space/badges/studio/textures/"+source.substring(6);return source!=null&&source.startsWith("https://")?source:"";}
}

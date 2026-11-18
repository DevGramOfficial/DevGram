package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.devgram.profile.ProfileSlotsStore;
import java.io.*;
import java.util.concurrent.Executor;

/** Retain cached immutable assets and native archive files without exposing file paths in profiles. */
public final class DevGramProfileSlotMedia {
    private DevGramProfileSlotMedia(){}
    public static JSONObject snapshot(JSONObject document,long subject){JSONObject result=DevGramProfileDocument.copy(document);try{JSONObject media=new JSONObject();for(String slot:new String[]{"banner","page"}){JSONObject asset=DevGramProfileMedia.resolve(document,subject,slot);if(asset!=null)media.put(slot,asset);}result.put("media",media);}catch(Exception ignored){}return result;}
    public static void cached(ProfileSlotsStore store,JSONObject document,boolean restore)throws IOException{
        JSONArray sources=DevGramProfileTextures.sources(document);for(int i=0;i<sources.length();i++){String url=DevGramProfileTextures.imageUrl(sources.optString(i));resource(store,"texture:"+url,ImageLoader.getHttpFilePath(url,"jpg"),restore);}
        for(String field:new String[]{"nameFontFile","chatFontFile"}){JSONObject font=document.optJSONObject(field);String hash=font==null?"":font.optString("sha256");if(hash.matches("[a-f0-9]{64}"))resource(store,"font:"+hash,new File(ApplicationLoader.applicationContext.getCacheDir(),"devgram-profile-fonts/"+hash+".ttf"),restore);}
    }
    private static void resource(ProfileSlotsStore store,String key,File file,boolean restore)throws IOException{if(!restore){store.preserve(key,file);return;}File backup=store.resource(key);if(file.isFile()||backup==null)return;File folder=file.getParentFile();if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("slot_storage");File temp=new File(folder,file.getName()+".slot");try(InputStream input=new FileInputStream(backup);FileOutputStream output=new FileOutputStream(temp)){byte[] bytes=new byte[65536];int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count);output.getFD().sync();}if(!temp.renameTo(file)){temp.delete();throw new IOException("slot_storage");}}
    /** Call on UI; file copies run on the dedicated slot executor, completion returns to UI. */
    public static void archives(int account,ProfileSlotsStore store,JSONObject document,boolean restore,Executor io,Runnable done){
        JSONObject media=document.optJSONObject("media");java.util.LinkedHashSet<Integer> messages=new java.util.LinkedHashSet<>();for(String slot:new String[]{"banner","page"}){JSONObject asset=media==null?null:media.optJSONObject(slot);int message=asset==null?0:asset.optInt("message");if(message>0)messages.add(message);}if(messages.isEmpty()){if(done!=null)done.run();return;}final int[] pending={messages.size()};
        for(int message:messages)DevGramProfileBackground.load(account,message,object->{io.execute(()->{try{if(object!=null){File file=FileLoader.getInstance(account).getPathToMessage(object.messageOwner);resource(store,"archive:"+message,file,restore);}}catch(Exception error){FileLog.e(error);}AndroidUtilities.runOnUIThread(()->{if(--pending[0]==0&&done!=null)done.run();});});});
    }
}

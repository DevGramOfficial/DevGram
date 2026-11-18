package org.telegram.messenger;

import android.content.Context;
import android.graphics.*;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Immutable local image guide and portable .cpbt templates. Never submits pixels. */
public final class DevGramCanvasOverlay {
    private static final int MAX_IMAGE=16*1024*1024,MAX_FILE=MAX_IMAGE+262144;
    public final JSONObject settings;public final Bitmap pixels;private final byte[] image;
    private DevGramCanvasOverlay(byte[] image,JSONObject settings)throws Exception{this(image,settings,null);}
    private DevGramCanvasOverlay(byte[] image,JSONObject settings,Bitmap shared)throws Exception{
        this.image=image;this.settings=new JSONObject(settings.toString());
        int side=clamp(settings.optInt("side",32),4,384),colors=clamp(settings.optInt("colors",16),2,64);this.settings.put("version",1).put("side",side).put("colors",colors).put("alpha",clamp(settings.optInt("alpha",160),24,230)).put("x",clamp(settings.optInt("x"),-99808,99808)).put("y",clamp(settings.optInt("y"),-99808,99808));
        if(shared!=null){pixels=shared;return;}
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(image,0,image.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>32768||bounds.outHeight>32768)throw new IOException("image");BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;Bitmap original=BitmapFactory.decodeByteArray(image,0,image.length,options);if(original==null)throw new IOException("image");int[] size=DevGramCanvasQuantizer.fit(original.getWidth(),original.getHeight(),side);Bitmap scaled=Bitmap.createScaledBitmap(original,size[0],size[1],false);int[] raw=new int[size[0]*size[1]];scaled.getPixels(raw,0,size[0],0,0,size[0],size[1]);pixels=Bitmap.createBitmap(DevGramCanvasQuantizer.quantize(raw,colors),size[0],size[1],Bitmap.Config.ARGB_8888);if(scaled!=original)scaled.recycle();original.recycle();
    }
    public static DevGramCanvasOverlay image(InputStream stream,int x,int y)throws Exception{return new DevGramCanvasOverlay(read(stream,MAX_IMAGE),new JSONObject().put("x",x).put("y",y).put("visible",true));}
    public DevGramCanvasOverlay with(JSONObject values)throws Exception{JSONObject merged=new JSONObject(settings.toString());for(Iterator<String> keys=values.keys();keys.hasNext();){String key=keys.next();merged.put(key,values.get(key));}return new DevGramCanvasOverlay(image,merged,merged.optInt("side",32)==settings.optInt("side",32)&&merged.optInt("colors",16)==settings.optInt("colors",16)?pixels:null);}
    public int x(){return settings.optInt("x")-pixels.getWidth()/2;}public int y(){return settings.optInt("y")-pixels.getHeight()/2;}
    public int colorAt(int x,int y){x-=x();y-=y();return x>=0&&y>=0&&x<pixels.getWidth()&&y<pixels.getHeight()?pixels.getPixel(x,y):0;}
    public int[] palette(){HashMap<Integer,Integer> counts=new HashMap<>();int[] values=new int[pixels.getWidth()*pixels.getHeight()];pixels.getPixels(values,0,pixels.getWidth(),0,0,pixels.getWidth(),pixels.getHeight());for(int color:values)if((color>>>24)!=0)counts.put(color|0xff000000,counts.getOrDefault(color|0xff000000,0)+1);ArrayList<Integer> colors=new ArrayList<>(counts.keySet());colors.sort((a,b)->{int diff=Integer.compare(counts.get(b),counts.get(a));return diff==0?Integer.compare(a,b):diff;});return colors.stream().mapToInt(Integer::intValue).toArray();}
    public byte[] pack(String name,long author,long target,String block)throws Exception{
        JSONObject manifest=new JSONObject(settings.toString()).put("version",1).put("name",name.substring(0,Math.min(80,name.length()))).put("author",author).put("canvas_target",target).put("canvas_block",block).put("created",System.currentTimeMillis()).put("collaborative",true);ByteArrayOutputStream output=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(output)){zip.putNextEntry(new ZipEntry("template.json"));zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new ZipEntry("image.jpg"));zip.write(image);zip.closeEntry();}return output.toByteArray();
    }
    public static DevGramCanvasOverlay unpack(InputStream stream)throws Exception{
        byte[] packed=read(stream,MAX_FILE);JSONObject manifest=null;byte[] image=null;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(packed))){int entries=0;ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(++entries>2||entry.isDirectory())throw new IOException("template");if(entry.getName().equals("template.json")&&manifest==null)manifest=new JSONObject(new String(read(zip,262144),StandardCharsets.UTF_8));else if(entry.getName().equals("image.jpg")&&image==null)image=read(zip,MAX_IMAGE);else throw new IOException("template");zip.closeEntry();}}if(manifest==null||manifest.optInt("version")!=1||image==null)throw new IOException("template");return new DevGramCanvasOverlay(image,manifest);
    }
    private static File directory(Context context,long viewer){File root=new File(context.getFilesDir(),"devgram_canvas/"+viewer);if(!root.isDirectory()&&!root.mkdirs())throw new IllegalStateException("storage");return root;}
    private static File current(Context context,long viewer,long owner,String block)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest((owner+":"+block).getBytes(StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(Locale.ROOT,"%02x",b&255));return new File(directory(context,viewer),key+".cpbt");}
    public void persist(Context context,long viewer,long owner,String block)throws Exception{write(current(context,viewer,owner,block),pack(settings.optString("name",""),viewer,owner,block));}
    public static DevGramCanvasOverlay restore(Context context,long viewer,long owner,String block){try(InputStream stream=new AtomicFile(current(context,viewer,owner,block)).openRead()){return unpack(stream);}catch(Exception e){return null;}}
    public static void clear(Context context,long viewer,long owner,String block)throws Exception{new AtomicFile(current(context,viewer,owner,block)).delete();}
    public File saveTemplate(Context context,long viewer,long owner,String block,String name)throws Exception{File root=new File(directory(context,viewer),"library");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("storage");File file=new File(root,UUID.randomUUID()+".cpbt");write(file,pack(name,viewer,owner,block));return file;}
    public static File[] library(Context context,long viewer){File root=new File(directory(context,viewer),"library");File[] files=root.listFiles((dir,name)->name.endsWith(".cpbt"));if(files==null)return new File[0];Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));return files;}
    public static void deleteTemplate(Context context,long viewer,File file)throws Exception{File root=new File(directory(context,viewer),"library");if(!file.getCanonicalFile().getParentFile().equals(root.getCanonicalFile())||!file.delete())throw new IOException("storage");}
    private static void write(File file,byte[] bytes)throws Exception{AtomicFile target=new AtomicFile(file);FileOutputStream stream=target.startWrite();try{stream.write(bytes);target.finishWrite(stream);}catch(Exception error){target.failWrite(stream);throw error;}}
    private static byte[] read(InputStream stream,int limit)throws IOException{if(stream==null)throw new IOException("stream");ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int count;while((count=stream.read(buffer))!=-1){if(output.size()+count>limit)throw new IOException("size");output.write(buffer,0,count);}return output.toByteArray();}
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}

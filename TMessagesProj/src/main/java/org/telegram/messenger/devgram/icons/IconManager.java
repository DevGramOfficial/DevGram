package org.telegram.messenger.devgram.icons;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.LruCache;
import android.util.SparseIntArray;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.LaunchActivity;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Full resource-pack stack: custom overrides in priority order, then the selected base pack. */
public final class IconManager {
    public static final IconManager INSTANCE=new IconManager();
    public static final String[] BASE_IDS={"base.default","base.solar","base.remix"};
    private final ExecutorService mutations=Executors.newSingleThreadExecutor(), decode=Executors.newFixedThreadPool(2), prewarm=Executors.newSingleThreadExecutor();
    private final AtomicLong generation=new AtomicLong(),writtenSincePrune=new AtomicLong();
    private final java.util.concurrent.atomic.AtomicBoolean pruning=new java.util.concurrent.atomic.AtomicBoolean();
    private final LruCache<String,Bitmap> bitmaps=new LruCache<String,Bitmap>((int)Math.max(2*1024*1024,Math.min(12*1024*1024,Runtime.getRuntime().maxMemory()/32))) {
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private final Map<Long,String> resolved=Collections.synchronizedMap(new LinkedHashMap<>());
    private final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    private volatile Map<String,IconPack> catalog=Collections.emptyMap(), owners=Collections.emptyMap();
    private volatile List<String> order=Collections.singletonList("base.default"), hidden=Collections.emptyList();
    private volatile SparseIntArray baseMap=new SparseIntArray();
    private final Map<String,IconPack> bases=new LinkedHashMap<>();
    private final Map<String,Integer> resourceIds=new ConcurrentHashMap<>();
    private final Set<String> blocked=new HashSet<>();
    private final ThreadLocal<Boolean> resolving=new ThreadLocal<>();
    private volatile Resources original;
    private volatile boolean ready,started;
    private IconPackStorage storage;
    private SharedPreferences prefs;
    private static Resources wrappedSource; private static IconResources wrapped;
    private IconManager() {}
    public static synchronized Resources resources(Resources source) {
        if(source instanceof IconResources)return source;
        if(wrapped==null || wrappedSource!=source){wrappedSource=source;wrapped=new IconResources(source);}
        return wrapped;
    }
    public void start(Resources resources) {
        synchronized(this){if(started)return;started=true; original=resources instanceof IconResources?((IconResources)resources).source:resources;}
        prefs=ApplicationLoader.applicationContext.getSharedPreferences("devgram_icon_packs",0);
        storage=IconPackStorage.create();
        mutations.execute(()->{
            try {
                loadBasePacks();
                for(Field f:R.drawable.class.getFields())try {String name=f.getName();if(!blacklisted(name))resourceIds.put(name,f.getInt(null));}catch(Exception ignored){}
                reload(false);
            }catch(Exception e){FileLog.e(e);ready=true;}
        });
    }
    private void loadBasePacks() throws Exception {
        JSONObject maps;
        try(InputStream in=original.openRawResource(R.raw.devgram_icon_base_maps);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);maps=new JSONObject(out.toString("UTF-8"));
        }
        bases.put("base.default",new IconPack("base.default","Стандартный","DevGram","1.0",Collections.emptyMap(),null,new SparseIntArray()));
        for(String variant:new String[]{"solar","remix"}) {
            SparseIntArray ids=new SparseIntArray();JSONObject entries=maps.getJSONObject(variant);
            for(Iterator<String> it=entries.keys();it.hasNext();) {String name=it.next();int from=original.getIdentifier(name,"drawable",ApplicationLoader.applicationContext.getPackageName()),to=original.getIdentifier(entries.getString(name),"drawable",ApplicationLoader.applicationContext.getPackageName());if(from!=0&&to!=0)ids.put(from,to);}
            bases.put("base."+variant,new IconPack("base."+variant,variant.equals("solar")?"Solar":"Remix","DevGram","1.0",Collections.emptyMap(),null,ids));
        }
        // The reference excludes backgrounds, launcher assets and composite graphics from the editor.
        try(InputStream in=original.openRawResource(R.raw.devgram_icon_blacklist);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);JSONArray a=new JSONArray(out.toString("UTF-8"));for(int i=0;i<a.length();i++)blocked.add(a.getString(i));
        }
    }
    public boolean blacklisted(String name) {return blocked.contains(name)||name.endsWith("_solar")||name.endsWith("_remix")||name.startsWith("abc_")||name.startsWith("mtrl_")||name.startsWith("notification_bg");}
    public Map<String,Integer> systemIcons(){return Collections.unmodifiableMap(resourceIds);}
    public boolean ready(){return ready;}
    public long revision(){return generation.get();}
    /** Optional override for programmatically drawn UI, without replacing the stock animation otherwise. */
    public Drawable overrideDrawable(int id){
        if(!ready)return null;
        Drawable custom=getDrawable(id,0,original);
        return custom!=null?custom:baseMap.get(id,id)!=id?baseDrawable(id):null;
    }
    public List<String> activeIds(){return new ArrayList<>(order);}
    public List<String> hiddenIds(){return new ArrayList<>(hidden);}
    public IconPack pack(String id){IconPack base=bases.get(id);return base!=null?base:catalog.get(id);}
    public String editingId(){return prefs==null?null:prefs.getString("editing",null);}
    public void editing(String id){if(prefs!=null)prefs.edit().putString("editing",id).apply();fire(false);}
    public void addListener(Runnable listener){listeners.addIfAbsent(listener);}
    public void removeListener(Runnable listener){listeners.remove(listener);}
    private List<String> ids(String key){List<String> result=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++){String s=a.getString(i);if(!result.contains(s))result.add(s);}}catch(Exception ignored){}return result;}
    private void persist(List<String> active,List<String> disabled) {prefs.edit().putString("active",new JSONArray(active).toString()).putString("hidden",new JSONArray(disabled).toString()).apply();}
    private void reload(boolean apply) throws Exception {
        Map<String,IconPack> installed=new LinkedHashMap<>();for(IconPack p:storage.list())installed.put(p.id,p);
        List<String> active=ids("active"),disabled=ids("hidden");
        active.removeIf(id->!installed.containsKey(id)&&!bases.containsKey(id));disabled.removeIf(id->!installed.containsKey(id));
        String base="base.default";for(String id:active)if(bases.containsKey(id)){base=id;break;}
        active.removeIf(id->id.startsWith("base."));active.add(base);
        for(String id:installed.keySet())if(!active.contains(id)&&!disabled.contains(id))disabled.add(id);
        disabled.removeAll(active);persist(active,disabled);
        Map<String,IconPack> map=IconPackStack.owners(active,installed);
        boolean previouslyReady=ready;
        catalog=Collections.unmodifiableMap(installed);order=Collections.unmodifiableList(active);hidden=Collections.unmodifiableList(disabled);owners=Collections.unmodifiableMap(map);baseMap=bases.get(base).preinstalledMap;
        if(editingId()!=null&&!active.contains(editingId()))prefs.edit().remove("editing").apply();
        long g=generation.incrementAndGet();resolved.clear();ready=true;
        fire(apply || (!previouslyReady && (active.size()>1 || !base.equals("base.default"))));
        prewarm.execute(()->{for(Map.Entry<String,IconPack> e:map.entrySet()){if(g!=generation.get())break;Integer id=resourceIds.get(e.getKey());if(id!=null)bitmap(e.getValue(),e.getKey(),id,0,0,0);}});
    }
    private void fire(boolean rebuild) {
        AndroidUtilities.runOnUIThread(()->{
            if(rebuild){LaunchActivity a=LaunchActivity.instance;if(a!=null&&!a.isFinishing()&&!a.isDestroyed()){ThemeReload.apply(a);}}
            org.telegram.ui.devgram.icons.IconPickerController.sync(LaunchActivity.instance);
            IconPackIntents.flushPending();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.devgramIconPacksUpdated);
            for(Runnable r:listeners)r.run();
        });
    }
    private static final class ThemeReload {static void apply(LaunchActivity a){org.telegram.ui.ActionBar.Theme.reloadAllResources(a);a.rebuildAllFragments(true);}}
    public void setBase(String id){mutate(()->{if(!bases.containsKey(id))return;List<String> active=activeIds();active.removeIf(s->s.startsWith("base."));active.add(id);persist(active,hiddenIds());},null);}
    public void toggle(String id){mutate(()->{List<String> active=activeIds(),disabled=hiddenIds();if(active.remove(id))disabled.add(0,id);else{disabled.remove(id);active.add(0,id);}persist(active,disabled);},null);}
    public void reorder(List<String> custom,boolean enabled){mutate(()->{if(enabled){List<String> active=new ArrayList<>(custom);for(String s:order)if(s.startsWith("base."))active.add(s);persist(active,hiddenIds());}else persist(activeIds(),custom);},null);}
    public void delete(String id,Runnable done){mutate(()->{invalidatePack(id);storage.delete(id);},done);}
    public void create(String name,String author,String version,java.util.function.Consumer<IconPack> done){
        final IconPack[] created=new IconPack[1];
        mutate(()->{String id=UUID.randomUUID().toString();created[0]=storage.save(new IconPack(id,name,author,version,Collections.emptyMap(),null,null));List<String> active=activeIds();active.add(0,id);persist(active,hiddenIds());prefs.edit().putString("editing",id).apply();},()->done.accept(created[0]));
    }
    public void updateMetadata(IconPack pack,String name,String author,String version,Runnable done){mutate(()->storage.save(storage.find(pack.id).withMetadata(name,author,version)),done);}
    public void replace(String id,String resource,File source,String extension,Runnable done){mutate(()->{try{storage.replace(id,resource,source,extension);}finally{source.delete();}},done);}
    public void reset(String id,String resource,Runnable done){mutate(()->storage.reset(id,resource),done);}
    private interface Change {void run() throws Exception;}
    private void mutate(Change action,Runnable done){mutations.execute(()->{try{action.run();reload(true);if(done!=null)AndroidUtilities.runOnUIThread(done);}catch(Exception e){FileLog.e(e);IconPackIntents.showError(e);}});}
    public void preview(File archive,java.util.function.Consumer<IconPackStorage.Preview> callback,Runnable failed){decode.execute(()->{try{IconPackStorage.Preview p=storage.preview(archive);AndroidUtilities.runOnUIThread(()->callback.accept(p));}catch(Exception e){FileLog.e(e);IconPackIntents.showError(e);AndroidUtilities.runOnUIThread(failed);}});}
    public void install(IconPackStorage.Preview preview,boolean enable,Runnable done){mutate(()->{IconPack p=storage.install(preview);invalidatePack(p.id);if(enable){List<String> active=activeIds(),disabled=hiddenIds();if(!active.contains(p.id))active.add(0,p.id);disabled.remove(p.id);persist(active,disabled);}},done);}
    public void export(String id,java.util.function.Consumer<File> done){mutations.execute(()->{try{File f=storage.export(id);AndroidUtilities.runOnUIThread(()->done.accept(f));}catch(Exception e){IconPackIntents.showError(e);}});}
    public Drawable original(int id){try{return original.getDrawable(id,null).getConstantState()!=null?original.getDrawable(id,null).mutate():original.getDrawable(id,null);}catch(Exception e){return null;}}
    public Drawable packDrawable(IconPack pack,int id){if(pack.isBase())return original(pack.preinstalledMap.get(id,id));Bitmap b=bitmap(pack,resourceName(id),id,0,0,0);return b==null?null:new BitmapDrawable(original,b);}
    public Drawable baseDrawable(int id){return original(baseMap.get(id,id));}
    public boolean isBasePackOnly(String base){return order.size()==1&&order.get(0).equals(base);}
    public int baseIcon(int id){return ready?baseMap.get(id,id):id;}
    public String resourceName(int id){try{return original.getResourceEntryName(id);}catch(Exception e){return "";}}
    public Drawable getDrawable(int id,int density,Resources target) {
        if(!ready||Boolean.TRUE.equals(resolving.get()))return null;
        String name=resourceName(id);IconPack p=owners.get(name);if(p==null)return null;
        int dpi=density==0?original.getDisplayMetrics().densityDpi:density;
        long lookup=((long)id<<32)|(dpi&0xffffffffL);String cacheKey=resolved.get(lookup);Bitmap cached=cacheKey==null?null:bitmaps.get(cacheKey);
        if(cached!=null)return new BitmapDrawable(target,cached);
        resolving.set(true);try{Bitmap b=bitmap(p,name,id,dpi,0,0);return b==null?null:new BitmapDrawable(target,b);}finally{resolving.remove();}
    }
    public void request(IconPack pack,int id,int size,java.util.function.Consumer<Drawable> callback){
        long g=generation.get();decode.execute(()->{Bitmap b=pack==null||pack.isBase()?null:bitmap(pack,resourceName(id),id,0,size,size);
            Drawable d=b==null?(pack==null?baseDrawable(id):(pack.isBase()?original(pack.preinstalledMap.get(id,id)):null)):new BitmapDrawable(original,b);
            AndroidUtilities.runOnUIThread(()->{if(g==generation.get())callback.accept(d);});});
    }
    public void previewImage(IconPack pack,int id,java.util.function.Consumer<File> callback){
        long epoch=generation.get();
        decode.execute(()->{
            File result=null,temp=null;Bitmap decoded=null,rendered=null;
            try{
                File source=null;String name=resourceName(id);
                if(pack!=null&&!pack.isBase()){
                    String path=pack.icons.get(name);if(path==null)throw new IOException("Missing image");
                    source=IconPackStorage.inside(pack.location,path);if(!source.isFile())throw new IOException("Missing image");
                }
                String identity="preview-v1:"+epoch+":"+id+":"+(pack==null?"original":pack.id)+":"+(source==null?"":source.lastModified()+":"+source.length());
                File file=decodedFile(identity);
                if(!file.isFile()){
                    Drawable drawable;int rotation=0,invert=0;
                    if(source==null)drawable=original(pack==null?id:pack.preinstalledMap.get(id,id));
                    else{
                        if(source.getName().toLowerCase(Locale.ROOT).endsWith(".svg")){
                            try(InputStream input=new FileInputStream(source)){decoded=SvgHelper.getBitmap(input,1024,1024,false);}
                        }else{
                            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),options);
                            if(options.outWidth<=0||options.outHeight<=0)throw new IOException("Invalid image");
                            options.inSampleSize=1;while(options.outWidth/options.inSampleSize>2048||options.outHeight/options.inSampleSize>2048)options.inSampleSize*=2;
                            options.inJustDecodeBounds=false;decoded=BitmapFactory.decodeFile(source.getPath(),options);
                            android.util.Pair<Integer,Integer> orientation=AndroidUtilities.getImageOrientation(source);rotation=orientation.first;invert=orientation.second;
                        }
                        if(decoded==null)throw new IOException("Invalid image");
                        drawable=new BitmapDrawable(original,decoded);
                    }
                    if(drawable==null)throw new IOException("Missing image");
                    rendered=IconPreviewRenderer.render(drawable,rotation,invert);
                    temp=new File(file.getPath()+"."+Thread.currentThread().getId()+".tmp");
                    try(OutputStream output=new FileOutputStream(temp)){if(!rendered.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("Preview failed");}
                    if(!temp.renameTo(file))throw new IOException("Preview failed");
                    schedulePrune(file.getParentFile(),file.length());
                }
                file.setLastModified(System.currentTimeMillis());result=file;
            }catch(Exception|OutOfMemoryError error){FileLog.e(error);}
            finally{if(rendered!=null)rendered.recycle();if(decoded!=null)decoded.recycle();if(temp!=null&&temp.exists())temp.delete();}
            File ready=result;AndroidUtilities.runOnUIThread(()->{if(epoch==generation.get())callback.accept(ready);});
        });
    }
    private Bitmap bitmap(IconPack pack,String name,int id,int density,int width,int height){
        long epoch=generation.get();boolean standardSize=width==0&&height==0;
        String path=pack.icons.get(name);if(path==null)return null;
        try {
            File file=IconPackStorage.inside(pack.location,path);if(!file.isFile())return null;
            if(density==0)density=original.getDisplayMetrics().densityDpi;
            if(width==0||height==0){Drawable d=original(id);width=d==null?AndroidUtilities.dp(24):Math.max(1,d.getIntrinsicWidth());height=d==null?AndroidUtilities.dp(24):Math.max(1,d.getIntrinsicHeight());}
            width=Math.min(512,width);height=Math.min(512,height);
            String key=file.getCanonicalPath()+":"+file.lastModified()+":"+file.length()+":"+width+":"+height+":"+density+(standardSize?"":":preview-fit");
            Bitmap bitmap=bitmaps.get(key);if(bitmap!=null)return bitmap;
            File disk=decodedFile(key);if(disk.isFile()){bitmap=BitmapFactory.decodeFile(disk.getPath());if(bitmap==null)disk.delete();}
            if(bitmap==null){
                if(path.toLowerCase(Locale.ROOT).endsWith(".svg"))bitmap=SvgHelper.getBitmap(file,width,height,false);
                else {BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);if(options.outWidth<=0||options.outHeight<=0)throw new IOException("INVALID_IMAGE");options.inSampleSize=1;while(options.outWidth/options.inSampleSize>width*2||options.outHeight/options.inSampleSize>height*2)options.inSampleSize*=2;options.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeFile(file.getPath(),options);if(bitmap!=null&&(bitmap.getWidth()!=width||bitmap.getHeight()!=height)){int[] target=standardSize?new int[]{width,height}:IconPreviewRenderer.fit(bitmap.getWidth(),bitmap.getHeight(),width,height);Bitmap scaled=Bitmap.createScaledBitmap(bitmap,target[0],target[1],true);if(scaled!=bitmap)bitmap.recycle();bitmap=scaled;}}
                if(bitmap!=null){File tmp=new File(disk.getParent(),disk.getName()+"."+Thread.currentThread().getId()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}if(!tmp.renameTo(disk))tmp.delete();schedulePrune(disk.getParentFile(),disk.length());}
            }
            if(bitmap!=null){bitmap.setDensity(density);if(epoch==generation.get()){bitmaps.put(key,bitmap);if(standardSize&&owners.get(name)==pack){synchronized(resolved){if(resolved.size()>=2048)resolved.clear();resolved.put(((long)id<<32)|(density&0xffffffffL),key);}}}}return bitmap;
        }catch(Exception|OutOfMemoryError e){FileLog.e(e);return null;}
    }
    private File decodedFile(String key) throws Exception {File dir=new File(ApplicationLoader.applicationContext.getCacheDir(),"dg_icon_bitmaps");dir.mkdirs();byte[] hash=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:hash)s.append(String.format(Locale.ROOT,"%02x",b&255));return new File(dir,s+".png");}
    private void invalidatePack(String id){IconPack p=catalog.get(id);if(p==null||p.location==null)return;String prefix=p.location.getAbsolutePath()+File.separator;for(String key:bitmaps.snapshot().keySet())if(key.startsWith(prefix))bitmaps.remove(key);resolved.clear();}
    private void schedulePrune(File dir,long bytes){if(writtenSincePrune.addAndGet(bytes)<1024*1024||!pruning.compareAndSet(false,true))return;writtenSincePrune.set(0);prewarm.execute(()->{try{pruneDisk(dir);}finally{pruning.set(false);}});}
    private void pruneDisk(File dir){File[] files=dir.listFiles();if(files==null)return;long size=0;for(File f:files)size+=f.length();if(size<=32L*1024*1024)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(File f:files){if(size<=24L*1024*1024)break;long n=f.length();if(f.delete())size-=n;}}
    public androidx.core.graphics.drawable.IconCompat notificationIcon(){
        IconPack pack=owners.get("notification");
        if(pack!=null)try{
            if(android.os.Build.VERSION.SDK_INT>=31){android.net.Uri uri=new android.net.Uri.Builder().scheme("content").authority(ApplicationLoader.applicationContext.getPackageName()+".icon_pack_provider").appendPath("icon").appendPath(pack.id).appendPath("notification").appendQueryParameter("v",Long.toString(generation.get())).build();ApplicationLoader.applicationContext.grantUriPermission("com.android.systemui",uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);return androidx.core.graphics.drawable.IconCompat.createWithContentUri(uri);}
            Bitmap b=bitmap(pack,"notification",R.drawable.notification,0,0,0);if(b!=null)return androidx.core.graphics.drawable.IconCompat.createWithBitmap(b);
        }catch(Exception e){FileLog.e(e);}
        return androidx.core.graphics.drawable.IconCompat.createWithResource(ApplicationLoader.applicationContext,baseIcon(R.drawable.notification));
    }
    public File notificationFile(String packId,String resource)throws Exception{
        IconPack p=IconPackStorage.create().find(packId);Integer id=resourceIds.get(resource);if(id==null)id=original.getIdentifier(resource,"drawable",ApplicationLoader.applicationContext.getPackageName());if(id==0)throw new IOException("Unknown resource");Bitmap b=bitmap(p,resource,id,0,0,0);if(b==null)throw new IOException("Invalid icon");
        File source=IconPackStorage.inside(p.location,p.icons.get(resource));
        File f=decodedFile("provider:"+packId+":"+resource+":"+source.lastModified()+":"+source.length());if(!f.exists()){File temp=new File(f.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){b.compress(Bitmap.CompressFormat.PNG,100,out);}if(!temp.renameTo(f))temp.delete();}return f;
    }
    public File defaultNotificationFile()throws Exception{
        int density=original.getDisplayMetrics().densityDpi;File file=decodedFile("notification-default:"+density+":"+baseIcon(R.drawable.notification));if(file.exists())return file;
        Drawable icon=baseDrawable(R.drawable.notification);int w=Math.max(1,icon.getIntrinsicWidth()),h=Math.max(1,icon.getIntrinsicHeight());Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);icon.setBounds(0,0,w,h);icon.draw(new Canvas(bitmap));
        File tmp=new File(file.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}if(!tmp.renameTo(file))tmp.delete();return file;
    }
    public boolean validateImage(File file,String extension) {try{if(!extension.equalsIgnoreCase(".svg"))return validRaster(file);Bitmap b=SvgHelper.getBitmap(file,48,48,false);if(b==null)return false;b.recycle();return true;}catch(Exception|OutOfMemoryError e){return false;}}
    private boolean validRaster(File file){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);return o.outWidth>0&&o.outHeight>0&&o.outWidth<=16384&&o.outHeight<=16384;}
}

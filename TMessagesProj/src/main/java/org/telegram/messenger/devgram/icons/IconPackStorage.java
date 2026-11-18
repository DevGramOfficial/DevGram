package org.telegram.messenger.devgram.icons;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Archive layout and installation behavior mirror the server reference; the extension is .dgicons. */
public final class IconPackStorage {
    public static final long MAX_FILE=10L*1024*1024, MAX_TOTAL=100L*1024*1024;
    public static final int MAX_ENTRIES=3000, MAX_METADATA=1024*1024;
    private final File root, cache;
    public IconPackStorage(File root,File cache) { this.root=root; this.cache=cache; root.mkdirs(); cache.mkdirs(); }
    public static IconPackStorage create() {
        return new IconPackStorage(new File(ApplicationLoader.applicationContext.getFilesDir(),"dg_icon_packs"),
                new File(ApplicationLoader.applicationContext.getCacheDir(),"dg_icon_imports"));
    }
    public static boolean validId(String id) {
        return id!=null && id.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") && !id.startsWith("base.");
    }
    public static boolean imageName(String name) {
        String n=name.toLowerCase(Locale.ROOT);
        return n.endsWith(".svg")||n.endsWith(".png")||n.endsWith(".webp")||n.endsWith(".jpg")||n.endsWith(".jpeg");
    }
    public static File inside(File folder,String path) throws IOException {
        if(path==null || path.isEmpty() || path.indexOf('\0')>=0 || path.indexOf('\\')>=0 || path.startsWith("/")) throw new IOException("Invalid archive path");
        File f=new File(folder,path).getCanonicalFile();
        if(!f.getPath().startsWith(folder.getCanonicalPath()+File.separator)) throw new IOException("Archive path escapes pack");
        return f;
    }
    private IconPack parse(File directory) throws Exception {
        File f=inside(directory,"metadata.json");
        if(!f.isFile()) throw new IOException("MISSING_METADATA");
        if(f.length()>MAX_METADATA) throw new IOException("METADATA_TOO_LARGE");
        JSONObject j=new JSONObject(new String(read(f,MAX_METADATA),StandardCharsets.UTF_8));
        String id=j.getString("packId"),name=j.getString("packName").trim();
        if(!validId(id)||name.isEmpty()||name.length()>64) throw new IOException("INVALID_METADATA");
        Map<String,String> icons=new LinkedHashMap<>(); JSONObject entries=j.optJSONObject("icons");
        if(entries!=null) for(Iterator<String> it=entries.keys();it.hasNext();) {
            String key=it.next(),path=entries.getString(key);
            if(!key.matches("[A-Za-z0-9_]+")||!imageName(path)) throw new IOException("INVALID_METADATA");
            File icon=inside(directory,path);
            if(!icon.isFile()||icon.length()>MAX_FILE) throw new IOException("INVALID_METADATA");
            icons.put(key,path);
        }
        return new IconPack(id,name,j.optString("author",""),j.optString("version","1.0"),icons,directory,null);
    }
    public synchronized List<IconPack> list() {
        List<IconPack> result=new ArrayList<>(); File[] dirs=root.listFiles();
        if(dirs!=null) for(File dir:dirs) if(dir.isDirectory() && validId(dir.getName())) {
            try { IconPack p=parse(dir); if(p.id.equals(dir.getName())) result.add(p); } catch(Exception ignored) {}
        }
        result.sort(Comparator.comparing(p->p.name.toLowerCase(Locale.ROOT))); return result;
    }
    public synchronized IconPack find(String id) throws Exception {
        if(!validId(id)) throw new IOException("INVALID_METADATA"); return parse(new File(root,id));
    }
    public static final class Preview implements AutoCloseable {
        public final IconPack pack; public final File directory;
        Preview(IconPack pack,File directory) {this.pack=pack;this.directory=directory;}
        @Override public void close() { deleteTree(directory); }
    }
    public Preview preview(File archive) throws Exception {
        if(!archive.isFile() || archive.length()>MAX_TOTAL) throw new IOException("ARCHIVE_TOO_LARGE");
        File temp=new File(cache,"preview-"+UUID.randomUUID());
        if(!temp.mkdirs()) throw new IOException("STORAGE_ERROR");
        try { extract(archive,temp); return new Preview(parse(temp),temp); }
        catch(Exception e) { deleteTree(temp); throw e; }
    }
    public static void extract(File archive,File target) throws IOException {
        long total=0; int count=0; Set<String> names=new HashSet<>();
        try(ZipFile zip=new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();
                if(++count>MAX_ENTRIES) throw new IOException("TOO_MANY_FILES");
                File out=inside(target,entry.getName());
                if(!names.add(out.getCanonicalPath())) throw new IOException("INVALID_ARCHIVE");
                if(entry.isDirectory()) {if(!out.isDirectory()&&!out.mkdirs()) throw new IOException("STORAGE_ERROR");continue;}
                long limit=entry.getName().equals("metadata.json")?MAX_METADATA:MAX_FILE;
                if(entry.getSize()>limit) throw new IOException("FILE_TOO_LARGE");
                if(entry.getSize()>0 && entry.getCompressedSize()>0 && entry.getSize()/entry.getCompressedSize()>250) throw new IOException("COMPRESSION_RATIO");
                File parent=out.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs()) throw new IOException("STORAGE_ERROR");
                long size=0;
                try(InputStream in=zip.getInputStream(entry);OutputStream dest=new FileOutputStream(out)) {
                    byte[] buffer=new byte[16384]; int n;
                    while((n=in.read(buffer))!=-1) {
                        size+=n; total+=n;
                        if(size>limit)throw new IOException("FILE_TOO_LARGE");
                        if(total>MAX_TOTAL)throw new IOException("ARCHIVE_TOO_LARGE");
                        dest.write(buffer,0,n);
                    }
                }
            }
        }
    }
    public synchronized IconPack install(Preview preview) throws Exception {
        IconPack p=parse(preview.directory); File target=new File(root,p.id), backup=new File(root,".backup-"+UUID.randomUUID());
        boolean existed=target.exists();
        if(existed&&!target.renameTo(backup)) throw new IOException("STORAGE_ERROR");
        try {
            if(!preview.directory.renameTo(target)) {copyTree(preview.directory,target);deleteTree(preview.directory);}
            IconPack installed=parse(target);deleteTree(backup);return installed;
        } catch(Exception e) {deleteTree(target);if(existed&&!backup.renameTo(target))throw new IOException("STORAGE_ERROR: rollback",e);throw e;}
    }
    public synchronized IconPack save(IconPack p) throws Exception {
        if(!validId(p.id)||p.name==null||p.name.trim().isEmpty()||p.name.length()>64) throw new IOException("INVALID_METADATA");
        File dir=new File(root,p.id);if(!dir.isDirectory()&&!dir.mkdirs()) throw new IOException("STORAGE_ERROR");
        for(Map.Entry<String,String> entry:p.icons.entrySet()) {
            if(!entry.getKey().matches("[A-Za-z0-9_]+")||!imageName(entry.getValue())||!inside(dir,entry.getValue()).isFile())throw new IOException("INVALID_METADATA");
        }
        File tmp=new File(dir,".metadata-"+UUID.randomUUID());
        try(FileOutputStream out=new FileOutputStream(tmp)) {out.write(p.metadata().toString(2).getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        if(!tmp.renameTo(new File(dir,"metadata.json"))) {tmp.delete();throw new IOException("STORAGE_ERROR");}
        return parse(dir);
    }
    public synchronized void delete(String id) throws Exception {if(!validId(id))throw new IOException("INVALID_METADATA");deleteTree(new File(root,id));}
    public synchronized IconPack replace(String id,String resource,File source,String extension) throws Exception {
        if(!resource.matches("[A-Za-z0-9_]+")||!imageName("icon"+extension)) throw new IOException("INVALID_METADATA");
        IconPack p=find(id);String name=resource+"_"+UUID.randomUUID()+extension.toLowerCase(Locale.ROOT);
        File dest=inside(p.location,name); try{copyFile(source,dest,MAX_FILE);}catch(Exception e){dest.delete();throw e;}
        Map<String,String> map=new LinkedHashMap<>(p.icons);String old=map.put(resource,name);
        try {p=save(p.withIcons(map));}catch(Exception e){dest.delete();throw e;}
        if(old!=null&&!map.containsValue(old)) inside(p.location,old).delete();return p;
    }
    public synchronized IconPack reset(String id,String resource) throws Exception {
        IconPack p=find(id);Map<String,String> icons=new LinkedHashMap<>(p.icons);String old=icons.remove(resource);
        IconPack result=save(p.withIcons(icons));if(old!=null&&!icons.containsValue(old))inside(p.location,old).delete();return result;
    }
    public synchronized File export(String id) throws Exception {
        IconPack p=find(id); File file=new File(cache,p.name.replaceAll("[^\\p{L}\\p{N}_.-]","_")+"-"+UUID.randomUUID()+".dgicons");
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))) {
            zip.setLevel(0);Set<String> names=new LinkedHashSet<>(p.icons.values());names.add("metadata.json");
            for(String name:names) {
                zip.putNextEntry(new ZipEntry(name));try(InputStream in=new FileInputStream(inside(p.location,name))) {byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)zip.write(b,0,n);}zip.closeEntry();
            }
        }catch(Exception e){file.delete();throw e;}return file;
    }
    public static byte[] read(File f,long limit) throws IOException {
        try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] b=new byte[8192];int n;long size=0;while((n=in.read(b))!=-1){size+=n;if(size>limit)throw new IOException("FILE_TOO_LARGE");out.write(b,0,n);}return out.toByteArray();}
    }
    public static void copyFile(File from,File to,long limit) throws IOException {
        try(InputStream in=new FileInputStream(from);OutputStream out=new FileOutputStream(to)) {byte[] b=new byte[16384];long count=0;int n;while((n=in.read(b))!=-1){count+=n;if(count>limit)throw new IOException("FILE_TOO_LARGE");out.write(b,0,n);}}
    }
    private static void copyTree(File from,File to) throws IOException {
        if(from.isDirectory()){if(!to.isDirectory()&&!to.mkdirs())throw new IOException("STORAGE_ERROR");File[] files=from.listFiles();if(files==null)throw new IOException("STORAGE_ERROR");for(File f:files)copyTree(f,new File(to,f.getName()));}
        else copyFile(from,to,MAX_FILE);
    }
    public static void deleteTree(File file) {if(file==null||!file.exists())return;if(file.isDirectory()){File[] files=file.listFiles();if(files!=null)for(File f:files)deleteTree(f);}file.delete();}
}

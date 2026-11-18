package org.telegram.ui.Components.devgram.frame;

import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Compatible version-2 .frame ZIP container; no paths or executable entries are extracted. */
public final class FrameArchive {
    public static final int MAX=32*1024*1024,TEXTURE=8*1024*1024;
    public interface Files {byte[] read(String source)throws Exception;}
    public static final class Imported {
        public final String name,author,source;
        public final FrameNodeDoc graph;
        public final Map<String,byte[]> textures;
        Imported(JSONObject manifest,FrameNodeDoc graph,Map<String,byte[]> textures){name=manifest.optString("name");author=manifest.optString("author");source=manifest.optString("source");this.graph=graph;this.textures=textures;}
    }
    private FrameArchive(){}
    public static byte[] bounded(InputStream stream,int maximum)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(maximum,16384));byte[] chunk=new byte[8192];int count;while((count=stream.read(chunk,0,Math.min(chunk.length,maximum-out.size()+1)))!=-1){if(count==0)continue;if(out.size()+count>maximum)throw new IOException("frame_size");out.write(chunk,0,count);}return out.toByteArray();}
    public static String hash(byte[] data)throws Exception{StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hex.toString();}
    public static Imported read(InputStream input)throws Exception{
        final long[] compressed={0};InputStream limited=new FilterInputStream(input){public int read()throws IOException{int r=super.read();if(r>=0&&++compressed[0]>MAX)throw new IOException("frame_size");return r;}public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);if(n>0&&(compressed[0]+=n)>MAX)throw new IOException("frame_size");return n;}};
        JSONObject manifest=null;Map<String,byte[]> textures=new LinkedHashMap<>();Map<String,String> names=new HashMap<>();Set<String> seen=new HashSet<>();int total=0;long decoded=0;
        try(ZipInputStream zip=new ZipInputStream(limited)){ZipEntry entry;while((entry=zip.getNextEntry())!=null){String name=entry.getName();if(entry.isDirectory()||seen.size()>=256||!seen.add(name)||(!name.equals("frame.json")&&(!name.matches("textures/[A-Za-z0-9][A-Za-z0-9._-]{0,127}")||name.contains(".."))))throw new IOException("frame_entry");boolean isManifest=name.equals("frame.json");int cap=Math.min(isManifest?1048576:TEXTURE,MAX-total);if(entry.getSize()>cap)throw new IOException("frame_size");byte[] raw=bounded(zip,cap);total+=raw.length;if(isManifest){manifest=new JSONObject(new String(raw,"UTF-8"));}else{FrameImageInfo image=FrameImageInfo.read(raw);String key="asset:"+hash(raw)+"."+image.extension;if(!textures.containsKey(key))decoded+=image.decodedBytes;if(decoded>64L*1024*1024)throw new IOException("frame_memory");if(!textures.containsKey(key)&&textures.size()>=50)throw new IOException("frame_images");textures.put(key,raw);names.put(name,key);}zip.closeEntry();}}
        if(manifest==null||manifest.optInt("version")!=2||manifest.optString("name").length()>256||manifest.optString("author").length()>256||manifest.optString("source").length()>1024)throw new IOException("frame_manifest");String encoded=manifest.getString("spec");if(encoded.length()>1048576||new JSONArray(encoded).length()>200)throw new IOException("frame_spec");FrameSpec spec=FrameSpec.parse(encoded);if(spec.isEmpty())throw new IOException("frame_spec");
        String graphSource=manifest.optString("graph");FrameNodeDoc graph=graphSource.isEmpty()?FrameNodeBuild.of(spec):FrameNodeDoc.parse(graphSource);if(graph.count()==0)throw new IOException("frame_graph");
        for(String source:sources(spec,graph))if(!source.startsWith("blank:")&&!FrameRuntime.isAvatar(source)&&!names.containsKey(source))throw new IOException("frame_source");
        graph=graph.swap(names);return new Imported(manifest,graph,textures);
    }
    private static Set<String> sources(FrameSpec spec,FrameNodeDoc graph){Set<String> sources=new LinkedHashSet<>();for(FrameSpec.Layer l:spec.layers()){if(l.src!=null&&!l.src.isEmpty())sources.add(l.src);if(l.corner!=null&&!l.corner.isEmpty())sources.add(l.corner);}sources.addAll(graph.sources());sources.remove("");return sources;}
    public static void write(OutputStream output,String name,String author,FrameNodeDoc graph,Files files)throws Exception{
        FrameSpec spec=FrameNodeBuild.spec(graph);if(spec.isEmpty())throw new IOException("frame_empty");Map<String,String> names=new LinkedHashMap<>();for(String source:sources(spec,graph))if(!source.startsWith("blank:")&&!FrameRuntime.isAvatar(source)){if(!source.matches("asset:[a-f0-9]{64}\\.(png|jpg|gif|webp)"))throw new IOException("frame_source");names.put(source,"textures/"+source.substring(6));}
        JSONObject manifest=new JSONObject().put("version",2).put("name",name).put("author",author).put("spec",FrameSpec.swap(spec,names).encode()).put("graph",graph.swap(names).encode());byte[] data=manifest.toString().getBytes("UTF-8");int total=data.length;if(total>1048576||names.size()>50)throw new IOException("frame_size");
        try(ZipOutputStream zip=new ZipOutputStream(output)){zip.putNextEntry(new ZipEntry("frame.json"));zip.write(data);zip.closeEntry();for(Map.Entry<String,String> e:names.entrySet()){byte[] raw=files.read(e.getKey());if(raw==null||raw.length>TEXTURE||raw.length==0||(total+=raw.length)>MAX||!e.getKey().equals("asset:"+hash(raw)+"."+FrameImageInfo.read(raw).extension))throw new IOException("frame_texture");ZipEntry entry=new ZipEntry(e.getValue());entry.setMethod(ZipEntry.STORED);entry.setSize(raw.length);entry.setCompressedSize(raw.length);CRC32 crc=new CRC32();crc.update(raw);entry.setCrc(crc.getValue());zip.putNextEntry(entry);zip.write(raw);zip.closeEntry();}zip.finish();}
    }
}

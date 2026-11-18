package org.telegram.messenger.devgram.nodes;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
/** Immutable, read-only code files and a single atomically replaced package index. */
public final class NodePackageFiles {
 public static final int MAX_BYTES=2*1024*1024;
 public static final class Entry {
  public final String pkg,main,sha256,name,hint,file;public final int version;private final JSONObject data;
  public Entry(JSONObject raw)throws IOException{try{data=new JSONObject(raw.toString());pkg=raw.getString("pkg");main=raw.getString("main");sha256=raw.getString("sha256");name=raw.getString("name");hint=raw.optString("hint");file=raw.getString("file");version=raw.getInt("version");if(!pkg.matches("[a-z][a-z0-9_.-]{2,79}")||!main.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")||!sha256.matches("[a-f0-9]{64}")||!file.matches("[a-zA-Z0-9_.-]{1,150}\\.dex")||version<=0||name.length()>120||hint.length()>500||raw.getJSONArray("nodes").length()==0||raw.getJSONArray("nodes").length()>64||raw.toString().length()>131072)throw new IOException("node_package_metadata");}catch(JSONException error){throw new IOException(error);}}
  public JSONObject json(){try{return new JSONObject(data.toString());}catch(Exception error){throw new IllegalArgumentException(error);}}
 }
 private final File directory,index;private List<Entry> entries=new ArrayList<>();
 public NodePackageFiles(File root)throws IOException{directory=new File(root,"devgram-node-packages");index=new File(directory,"index.json");if(index.isFile())try{JSONObject object=new JSONObject(new String(read(index,2*1024*1024),"UTF-8"));JSONArray list=object.getJSONArray("packages");if(list.length()>100)throw new IOException("node_package_limit");HashSet<String> ids=new HashSet<>();for(int i=0;i<list.length();i++){Entry entry=new Entry(list.getJSONObject(i));if(!ids.add(entry.pkg))throw new IOException("duplicate_package");entries.add(entry);}}catch(JSONException error){throw new IOException(error);}}
 public synchronized List<Entry> entries(){return new ArrayList<>(entries);}
 public synchronized int version(String pkg){for(Entry entry:entries)if(entry.pkg.equals(pkg))return entry.version;return 0;}
 public File file(Entry entry){return new File(directory,entry.sha256+".dex");}
 public synchronized File stage(Entry entry,byte[] bytes)throws IOException{if(bytes==null||bytes.length<112||bytes.length>MAX_BYTES||bytes[0]!='d'||bytes[1]!='e'||bytes[2]!='x'||bytes[3]!='\n'||!hash(bytes).equals(entry.sha256))throw new IOException("node_package_hash");File file=file(entry);if(file.isFile()&&hash(read(file,MAX_BYTES)).equals(entry.sha256))return file;write(file,bytes);if(!file.setReadOnly())throw new IOException("node_package_readonly");return file;}
 public synchronized void commit(Entry entry)throws IOException{if(!file(entry).isFile()||!hash(read(file(entry),MAX_BYTES)).equals(entry.sha256))throw new IOException("node_package_missing");List<Entry> next=new ArrayList<>();for(Entry old:entries)if(!old.pkg.equals(entry.pkg))next.add(old);next.add(entry);if(next.size()>100)throw new IOException("node_package_limit");persist(next);prune();}
 public synchronized void remove(String pkg)throws IOException{List<Entry> next=new ArrayList<>();for(Entry entry:entries)if(!entry.pkg.equals(pkg))next.add(entry);persist(next);prune();}
 private void persist(List<Entry> next)throws IOException{JSONArray list=new JSONArray();for(Entry entry:next)list.put(entry.json());try{write(index,new JSONObject().put("version",1).put("packages",list).toString().getBytes("UTF-8"));entries=next;}catch(JSONException error){throw new IOException(error);}}
 private void prune(){HashSet<String> kept=new HashSet<>();for(Entry entry:entries)kept.add(entry.sha256+".dex");File[] files=directory.listFiles();if(files!=null)for(File file:files)if(file.getName().matches("[a-f0-9]{64}\\.dex")&&!kept.contains(file.getName())){file.setWritable(true,true);file.delete();}}
 public static String hash(byte[] bytes)throws IOException{try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder text=new StringBuilder();for(byte value:hash)text.append(String.format(Locale.US,"%02x",value&255));return text.toString();}catch(Exception error){throw new IOException(error);}}
 public static byte[] read(File file,int max)throws IOException{try(InputStream input=new FileInputStream(file)){return read(input,max);}}
 public static byte[] read(InputStream input,int max)throws IOException{ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(result.size()+count>max)throw new IOException("node_package_size");result.write(buffer,0,count);}return result.toByteArray();}
 private static void write(File file,byte[] bytes)throws IOException{File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("node_package_storage");File temp=new File(parent,file.getName()+".new");try(FileOutputStream output=new FileOutputStream(temp)){output.write(bytes);output.getFD().sync();}if(!temp.renameTo(file)){temp.delete();throw new IOException("node_package_storage");}}
}

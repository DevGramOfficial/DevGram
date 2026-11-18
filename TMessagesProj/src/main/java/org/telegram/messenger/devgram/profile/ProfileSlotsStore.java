package org.telegram.messenger.devgram.profile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;

/** One atomic manifest owns all snapshots. Media and previews live outside disposable caches. */
public final class ProfileSlotsStore {
    public static final int MAX=40;
    public static final class Slot {
        public final String id,name;private final JSONObject value;
        Slot(JSONObject value){this.value=copy(value);id=value.optString("id");name=value.optString("name");}
        public JSONObject document(){return copy(value.optJSONObject("document"));}
        public JSONObject theme(){return copy(value.optJSONObject("theme"));}
    }
    private final File directory;
    private JSONObject state;
    public ProfileSlotsStore(File root,int account,long uid,long subject)throws IOException{
        directory=new File(root,"devgram-profile-slots/"+account+"_"+uid+"_"+subject);
        File file=new File(directory,"index.json");state=file.isFile()?read(file):new JSONObject();
        JSONArray slots=state.optJSONArray("slots");if(slots==null){try{state.put("slots",new JSONArray()).put("active","").put("version",1);}catch(Exception error){throw new IOException(error);}}
        else{if(state.optInt("version")!=1||slots.length()>MAX)throw new IOException("slots_format");HashSet<String> ids=new HashSet<>();for(int i=0;i<slots.length();i++){JSONObject slot=slots.optJSONObject(i);if(slot==null||!slot.optString("id").matches("[a-f0-9-]{36}")||!ids.add(slot.optString("id"))||slot.optJSONObject("document")==null)throw new IOException("slots_format");}if(slots.length()>0&&!ids.contains(active()))throw new IOException("slots_active");}
    }
    public synchronized String active(){return state.optString("active");}
    public synchronized List<Slot> slots(){ArrayList<Slot> result=new ArrayList<>();JSONArray slots=state.optJSONArray("slots");for(int i=0;i<slots.length();i++)result.add(new Slot(slots.optJSONObject(i)));return result;}
    public synchronized Slot get(String id){for(Slot slot:slots())if(slot.id.equals(id))return slot;return null;}
    public synchronized Slot create(String name,JSONObject document,JSONObject theme)throws IOException{
        if(slots().size()>=MAX)throw new IOException("slots_full");JSONObject next=copy(state);String id=UUID.randomUUID().toString();try{JSONObject slot=new JSONObject().put("id",id).put("name",name(name)).put("document",copy(document)).put("theme",copy(theme));checkSnapshot(slot);next.getJSONArray("slots").put(slot);next.put("active",id);commit(next);return new Slot(slot);}catch(org.json.JSONException error){throw new IOException(error);}
    }
    public synchronized void save(JSONObject document,JSONObject theme)throws IOException{
        if(slots().isEmpty()){create("Profile 1",document,theme);return;}JSONObject next=copy(state);JSONArray slots=next.optJSONArray("slots");for(int i=0;i<slots.length();i++){JSONObject slot=slots.optJSONObject(i);if(slot.optString("id").equals(active()))try{slot.put("document",copy(document)).put("theme",copy(theme));checkSnapshot(slot);commit(next);return;}catch(org.json.JSONException error){throw new IOException(error);}}throw new IOException("slot_missing");
    }
    public synchronized Slot select(String id)throws IOException{Slot slot=get(id);if(slot==null)throw new IOException("slot_missing");JSONObject next=copy(state);try{next.put("active",id);}catch(Exception error){throw new IOException(error);}commit(next);return slot;}
    public synchronized void rename(String id,String name)throws IOException{JSONObject next=copy(state);boolean found=false;JSONArray slots=next.optJSONArray("slots");for(int i=0;i<slots.length();i++)if(slots.optJSONObject(i).optString("id").equals(id)){try{slots.optJSONObject(i).put("name",name(name));}catch(Exception error){throw new IOException(error);}found=true;}if(!found)throw new IOException("slot_missing");commit(next);}
    public synchronized void remove(String id,String switchTo)throws IOException{
        List<Slot> slots=slots();if(slots.size()<=1)throw new IOException("slot_last");if(get(id)==null)throw new IOException("slot_missing");if(active().equals(id)&&(switchTo==null||switchTo.equals(id)||get(switchTo)==null))throw new IOException("slot_active");
        JSONObject next=copy(state);JSONArray kept=new JSONArray();for(Slot slot:slots)if(!slot.id.equals(id))kept.put(slot.value);try{next.put("slots",kept);if(active().equals(id))next.put("active",switchTo);}catch(Exception error){throw new IOException(error);}commit(next);File preview=new File(directory,id+".jpg");if(preview.exists())preview.delete();
    }
    public File preview(String id){if(!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException();return new File(directory,id+".jpg");}
    public synchronized void savePreview(String id,byte[] bytes)throws IOException{if(get(id)==null||bytes==null||bytes.length>4*1024*1024)throw new IOException("slot_preview");write(preview(id),bytes);}
    /** Copy a known native cached resource. References are keys, never rewritten into the public profile. */
    public synchronized void preserve(String key,File source)throws IOException{
        if(key==null||key.length()>512||source==null||!source.isFile())return;if(source.length()>128L*1024*1024)throw new IOException("slot_media_size");
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];try(InputStream input=new FileInputStream(source)){int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(java.util.Locale.US,"%02x",value&255));File media=new File(directory,"media/"+hash);if(!media.isFile()){File parent=media.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("slot_storage");File temp=new File(parent,hash+".new");try(InputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(temp)){int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);output.getFD().sync();}if(!temp.renameTo(media))throw new IOException("slot_storage");}
            JSONObject next=copy(state),refs=next.optJSONObject("resources");if(refs==null)refs=new JSONObject();refs.put(key,hash.toString());next.put("resources",refs);commit(next);
        }catch(IOException error){throw error;}catch(Exception error){throw new IOException(error);}
    }
    public synchronized File resource(String key){JSONObject refs=state.optJSONObject("resources");String hash=refs==null?"":refs.optString(key);if(!hash.matches("[a-f0-9]{64}"))return null;File file=new File(directory,"media/"+hash);return file.isFile()?file:null;}
    private static void checkSnapshot(JSONObject slot)throws IOException{if(slot.toString().length()>1024*1024)throw new IOException("slot_size");}
    private void commit(JSONObject next)throws IOException{byte[] bytes=next.toString().getBytes("UTF-8");if(bytes.length>40*1024*1024)throw new IOException("slots_size");write(new File(directory,"index.json"),bytes);state=next;}
    private static String name(String name)throws IOException{String result=name==null?"":name.trim().replaceAll("\\s+"," ");if(result.isEmpty()||result.codePointCount(0,result.length())>64)throw new IOException("slot_name");return result;}
    private static JSONObject copy(JSONObject value){try{return value==null?new JSONObject():new JSONObject(value.toString());}catch(Exception error){throw new IllegalArgumentException(error);}}
    private static JSONObject read(File file)throws IOException{if(file.length()>40*1024*1024)throw new IOException("slots_size");try(InputStream input=new FileInputStream(file);ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);return new JSONObject(output.toString("UTF-8"));}catch(org.json.JSONException error){throw new IOException(error);}}
    private static void write(File file,byte[] bytes)throws IOException{File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("slot_storage");File temp=new File(parent,file.getName()+".new");try(FileOutputStream output=new FileOutputStream(temp)){output.write(bytes);output.getFD().sync();}if(!temp.renameTo(file)){temp.delete();throw new IOException("slot_storage");}}
}

package org.telegram.messenger.devgram.icons;

import android.util.SparseIntArray;
import org.json.JSONObject;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** DevGram port of exteraGram's named resource icon packs. */
public final class IconPack {
    public final String id, name, author, version;
    public final Map<String, String> icons;
    public final File location;
    public final SparseIntArray preinstalledMap;

    public IconPack(String id, String name, String author, String version, Map<String, String> icons, File location, SparseIntArray preinstalledMap) {
        this.id=id; this.name=name; this.author=author; this.version=version;
        this.icons=Collections.unmodifiableMap(new LinkedHashMap<>(icons)); this.location=location; this.preinstalledMap=preinstalledMap;
    }
    public String getId(){return id;} public String getName(){return name;} public String getAuthor(){return author;} public String getVersion(){return version;}
    public Map<String,String> getIcons(){return icons;} public File getLocation(){return location;} public SparseIntArray getPreinstalledMap(){return preinstalledMap;}
    public boolean isBase() { return id.startsWith("base."); }
    public JSONObject metadata() throws Exception {
        JSONObject json=new JSONObject(); json.put("packId",id); json.put("packName",name);
        json.put("author",author); json.put("version",version); json.put("icons",new JSONObject(icons)); return json;
    }
    public IconPack withMetadata(String name,String author,String version) {
        return new IconPack(id,name,author,version,icons,location,preinstalledMap);
    }
    public IconPack withIcons(Map<String,String> replacements) {
        return new IconPack(id,name,author,version,replacements,location,preinstalledMap);
    }
}

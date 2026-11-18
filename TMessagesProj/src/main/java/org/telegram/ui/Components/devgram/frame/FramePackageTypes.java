package org.telegram.ui.Components.devgram.frame;
import org.json.*;
import java.util.*;
/** Data-only extension descriptions keep a graph intact even when its optional code is absent. */
public final class FramePackageTypes {
    private static final Map<Integer,JSONObject> definitions=new HashMap<>();
    public static int typeOf(String id){return (id.hashCode()&0x3fffffff)|0x40000000;}
    public static synchronized void register(JSONObject definition){register(definition,false);}
    public static synchronized void register(JSONObject definition,boolean trusted){
        try{int type=definition.getInt("type");String id=definition.getString("id");if(!id.matches("[a-zA-Z][a-zA-Z0-9_.-]{2,119}")||type!=typeOf(id)||definition.toString().length()>16384)throw new IllegalArgumentException();int category=definition.optInt("category",7);if(category<0||category>7)throw new IllegalArgumentException();JSONArray values=definition.getJSONArray("knobs");if(values.length()>32)throw new IllegalArgumentException();FrameNodeType.Knob[] knobs=new FrameNodeType.Knob[values.length()];for(int i=0;i<knobs.length;i++){JSONObject item=values.getJSONObject(i);int kind=item.getInt("kind"),min=item.getInt("min"),max=item.getInt("max"),start=item.getInt("default");if(kind<0||kind>4||kind==3||min>max||start<min||start>max||item.optString("name").length()>120)throw new IllegalArgumentException();knobs[i]=new FrameNodeType.Knob(item.optString("name"),kind,min,max,start,null,0);}if(definition.optString("name").length()>120||definition.optString("hint").length()>300)throw new IllegalArgumentException();
            JSONObject existing=definitions.get(type);if(!trusted&&existing!=null&&!existing.optString("id").equals(id))throw new IllegalArgumentException("node collision");
            // Documents cannot replace a definition already loaded from the trusted catalog.
            if(!trusted&&existing!=null&&!existing.toString().equals(definition.toString()))return;
            if(definitions.size()>=512&&existing==null)throw new IllegalArgumentException();definitions.put(type,new JSONObject(definition.toString()));FrameNodeType.registerExternal(new FrameNodeType.Kind(type,category,definition.optString("name"),definition.optString("hint"),new FrameNodeType.Pin[]{new FrameNodeType.Pin("Слой",4,false)},4,false,knobs));
        }catch(JSONException error){throw new IllegalArgumentException(error);}
    }
    public static synchronized void registerAll(JSONArray values){if(values==null)return;if(values.length()>512)throw new IllegalArgumentException();for(int i=0;i<values.length();i++){JSONObject value=values.optJSONObject(i);if(value==null)throw new IllegalArgumentException();register(value);}}
    public static synchronized JSONArray used(List<FrameNodeDoc.Node> nodes){JSONArray result=new JSONArray();HashSet<Integer> seen=new HashSet<>();for(FrameNodeDoc.Node node:nodes)if(seen.add(node.type)&&definitions.containsKey(node.type))result.put(definitions.get(node.type));return result;}
}

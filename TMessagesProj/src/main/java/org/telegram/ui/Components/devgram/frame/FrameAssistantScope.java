package org.telegram.ui.Components.devgram.frame;

import org.json.JSONArray;
import org.json.JSONObject;

/** Frame proposals cannot replace unrelated profile settings or their private fields. */
public final class FrameAssistantScope {
    private static final String[] FIELDS={"frameGraph","layers","nodes"};
    private FrameAssistantScope() {}
    public static boolean field(String key){for(String field:FIELDS)if(field.equals(key))return true;return false;}
    public static JSONObject frame(JSONObject source){
        JSONObject result=new JSONObject();if(source==null)return result;
        try{JSONObject copy=new JSONObject(source.toString());for(String key:FIELDS)if(copy.has(key))result.put(key,copy.opt(key));}
        catch(Exception error){throw new IllegalArgumentException(error);}
        return result;
    }
    public static boolean hasFrame(JSONObject source){return source!=null&&(source.optJSONObject("frameGraph")!=null||source.optJSONArray("layers")!=null||source.optJSONArray("nodes")!=null);}
    public static JSONObject merge(JSONObject current,JSONObject proposal){
        if(!hasFrame(proposal))throw new IllegalArgumentException("Missing frame proposal");
        try{JSONObject result=new JSONObject(current.toString()),replacement=frame(proposal);for(String key:FIELDS){result.remove(key);if(replacement.has(key))result.put(key,replacement.opt(key));}return result;}
        catch(Exception error){throw new IllegalArgumentException(error);}
    }
    public static JSONObject schema(){
        JSONObject result=new JSONObject();JSONArray types=new JSONArray();
        try{
            result.put("format","frameGraph: {nodes:[{id,t,x,y,o,v:[knob integer values],s:[knob strings]}],wires:[[fromNode,toNode,inputPin,outputPin]],look:3}. Knob input pins use 100+knob index. Every visible decoration must reach node type 60 (Frame output). Up to 512 nodes; no cycles. Return the complete frameGraph, retaining stable node IDs unless removed.");
            result.put("sources","Use existing asset: source IDs or blank:rim, blank:ring, blank:stripe, blank:bead, blank:dot, blank:gem, blank:star, blank:spark. Never invent an asset ID or URL.");
            for(FrameNodeType.Kind kind:FrameNodeType.all()){
                JSONObject type=new JSONObject().put("type",kind.type).put("name",kind.name).put("hint",kind.hint).put("outputKind",kind.gives).put("signalOutput",kind.signal);
                JSONArray pins=new JSONArray(),knobs=new JSONArray();for(FrameNodeType.Pin pin:kind.pins)pins.put(new JSONObject().put("name",pin.name).put("kind",pin.kind).put("many",pin.many));
                for(FrameNodeType.Knob knob:kind.knobs){JSONObject value=new JSONObject().put("name",knob.name).put("kind",knob.kind).put("min",knob.min).put("max",knob.max).put("default",knob.start);if(knob.options!=null){JSONArray options=new JSONArray();for(String option:knob.options)options.put(option);value.put("options",options);}knobs.put(value);}
                type.put("inputs",pins).put("knobs",knobs);types.put(type);
            }
            result.put("nodeTypes",types);
        }catch(Exception error){throw new IllegalArgumentException(error);}
        return result;
    }
}

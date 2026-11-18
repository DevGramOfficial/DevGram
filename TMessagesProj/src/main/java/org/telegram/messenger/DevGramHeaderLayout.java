package org.telegram.messenger;

import org.json.JSONObject;
import java.util.Iterator;

/** The reference's versioned header config, including independent anchor edge points. */
public final class DevGramHeaderLayout {
    public static final String[] PARTS={"avatar","name","status","actions","title"};
    private DevGramHeaderLayout(){}
    public static boolean usesReference(JSONObject doc){
        if(doc.has("headerMode"))return true;if(doc.optInt("layout")!=0)return false;
        for(String part:new String[]{"avatar","name","status","buttons","title"}){
            for(String suffix:new String[]{"X","Y","OffsetX","OffsetY","Rotation","AnchorX","AnchorY"})if(doc.optDouble(part+suffix)!=0)return false;
            for(String suffix:new String[]{"Scale","ScaleX","ScaleY"})if(doc.has(part+suffix)&&doc.optDouble(part+suffix)!=100)return false;
        }return true;
    }
    public static int displayMode(JSONObject doc){return usesReference(doc)?mode(doc):4;}
    public static int mode(JSONObject doc){return doc.optInt("headerMode",0);}
    public static JSONObject config(JSONObject doc){
        if(mode(doc)==4)return normalized(doc.optJSONObject("headerConfig"));
        JSONObject value=normalized(null);
        put(value,"anchor_title_to",3);put(value,"anchor_title_to_y",0);put(value,"anchor_title_from_y",2);put(value,"anchor_title_y",-10);
        if(mode(doc)==1){
            put(value,"name_anchor",1);put(value,"anchor_title_to_x",0);put(value,"anchor_title_from_x",0);
            put(value,"anchor_avatar_to",1);put(value,"anchor_avatar_to_x",0);put(value,"anchor_avatar_to_y",0);put(value,"anchor_avatar_from_x",0);put(value,"anchor_avatar_from_y",0);put(value,"anchor_avatar_x",30);put(value,"anchor_avatar_y",150);
            put(value,"anchor_name_to",3);put(value,"anchor_name_to_x",2);put(value,"anchor_name_to_y",1);put(value,"anchor_name_from_x",0);put(value,"anchor_name_from_y",1);put(value,"anchor_name_x",15);
            put(value,"anchor_status_to",3);put(value,"anchor_status_to_x",2);put(value,"anchor_status_to_y",2);put(value,"anchor_status_from_x",0);put(value,"anchor_status_from_y",2);put(value,"anchor_status_x",30);put(value,"anchor_status_y",-5);
        }
        return value;
    }
    public static JSONObject normalized(JSONObject raw){
        JSONObject result=new JSONObject();put(result,"version",3);
        for(String part:PARTS){
            for(String suffix:new String[]{"x","y","rotate","scale_x","scale_y"}){
                boolean scale=suffix.startsWith("scale");double low=scale?10:suffix.equals("rotate")?-180:-45,high=scale?300:suffix.equals("rotate")?180:45;
                put(result,part+"_"+suffix,bounded(raw,part+"_"+suffix,scale?bounded(raw,part+"_scale",100,10,300):0,low,high));
            }
            for(String suffix:new String[]{"to","to_x","to_y","from_x","from_y","x","y"}){
                boolean margin=suffix.equals("x")||suffix.equals("y"),target=suffix.equals("to");String key="anchor_"+part+"_"+suffix;
                put(result,key,Math.round(bounded(raw,key,margin||target?0:1,margin?-600:0,margin?600:target?7:2)));
            }
        }
        for(String key:new String[]{"name_anchor","vertical_actions","actions_plain_content","actions_content_scale_x","actions_content_scale_y"}){
            boolean scale=key.contains("scale");put(result,key,Math.round(bounded(raw,key,scale?100:0,scale?10:0,scale?300:key.equals("name_anchor")?2:1)));
        }
        return result;
    }
    private static double bounded(JSONObject raw,String key,double fallback,double low,double high){Object value=raw==null?null:raw.opt(key);double number=value instanceof Number?((Number)value).doubleValue():fallback;return Double.isNaN(number)||Double.isInfinite(number)?fallback:Math.max(low,Math.min(high,number));}
    private static void put(JSONObject doc,String key,Object value){try{doc.put(key,value);}catch(Exception ignored){}}
    public static JSONObject custom(JSONObject doc){
        JSONObject config=config(doc);if(doc.has("headerMode"))return config;
        for(int i=0;i<PARTS.length;i++){String part=PARTS[i],nativeKey=i==3?"buttons":part;float width=Math.max(1,AndroidUtilities.displaySize.x);
            put(config,part+"_x",doc.optDouble(nativeKey+"OffsetX")+AndroidUtilities.dpf2(doc.optInt(nativeKey+"X"))*100/width);put(config,part+"_y",doc.optDouble(nativeKey+"OffsetY")+AndroidUtilities.dpf2(doc.optInt(nativeKey+"Y"))*100/width);
            put(config,part+"_rotate",doc.optInt(nativeKey+"Rotation"));put(config,part+"_scale_x",doc.optInt(nativeKey+"ScaleX",doc.optInt(nativeKey+"Scale",100)));put(config,part+"_scale_y",doc.optInt(nativeKey+"ScaleY",doc.optInt(nativeKey+"Scale",100)));
            int ax=doc.optInt(nativeKey+"AnchorX"),ay=doc.optInt(nativeKey+"AnchorY");if(ax>0&&ax==ay){put(config,"anchor_"+part+"_to",ax+2);for(String axis:new String[]{"x","y"}){int point=doc.optInt(nativeKey+"Align"+axis.toUpperCase(java.util.Locale.ROOT));put(config,"anchor_"+part+"_to_"+axis,point);put(config,"anchor_"+part+"_from_"+axis,point);}}
        }return normalized(config);
    }
    public static void set(JSONObject doc,String key,double value){JSONObject config=custom(doc);put(config,key,value);put(doc,"headerMode",4);put(doc,"headerConfig",normalized(config));}
    public static void replace(JSONObject doc,JSONObject config){put(doc,"headerMode",4);put(doc,"headerConfig",normalized(config));}
    public static String export(JSONObject doc){return custom(doc).toString();}
    public static JSONObject parse(String input)throws Exception{
        if(input==null||input.length()>4096)throw new IllegalArgumentException("header config");
        JSONObject raw=new JSONObject(input);Object version=raw.opt("version");if(version!=null&&(!(version instanceof Number)||(((Number)version).doubleValue()!=2&&((Number)version).doubleValue()!=3)))throw new IllegalArgumentException("header version");
        JSONObject known=normalized(null);Iterator<String> keys=raw.keys();while(keys.hasNext()){String key=keys.next();boolean legacyScale=false;for(String part:PARTS)if(key.equals(part+"_scale"))legacyScale=true;if((!known.has(key)&&!legacyScale)||!(raw.opt(key) instanceof Number)||!Double.isFinite(((Number)raw.opt(key)).doubleValue()))throw new IllegalArgumentException("header value");}
        return normalized(raw);
    }
}

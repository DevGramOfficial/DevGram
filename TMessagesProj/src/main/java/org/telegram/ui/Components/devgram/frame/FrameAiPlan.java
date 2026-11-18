package org.telegram.ui.Components.devgram.frame;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Preflight the complete assistant plan before any visible mutation; replay bounded typed edits. */
public final class FrameAiPlan {
    public static final class Step {
        public final String kind;public final int id,other,pin,out,x,y,value;public final String text;
        Step(String kind,int id,int other,int pin,int out,int x,int y,int value,String text){this.kind=kind;this.id=id;this.other=other;this.pin=pin;this.out=out;this.x=x;this.y=y;this.value=value;this.text=text;}
        public void apply(FrameNodeDoc graph){
            switch(kind){
                case "add":if(graph.add(value,x,y)!=id)throw bad();break;
                case "move":need(graph,id);graph.move(id,x,y);break;
                case "knob":need(graph,id);if(text==null)graph.set(id,pin,value);else graph.set(id,pin,text);break;
                case "link":if(!graph.link(id,out,other,pin))throw bad();break;
                case "cut":need(graph,id);graph.cut(id,pin);break;
                case "remove":need(graph,id);graph.drop(id);break;
                case "open":need(graph,id).open=value!=0;break;
                default:throw bad();
            }
        }
    }
    public final String before,after;
    public final List<Step> steps;
    public final int added,moved,knobs,linked,removed;
    private FrameAiPlan(String before,FrameNodeDoc graph,List<Step> steps){this.before=before;after=graph.encode();this.steps=Collections.unmodifiableList(steps);int a=0,m=0,k=0,l=0,r=0;for(Step step:steps)switch(step.kind){case "add":a++;break;case "move":m++;break;case "knob":k++;break;case "link":l++;break;case "remove":r++;break;}added=a;moved=m;knobs=k;linked=l;removed=r;}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid frame action plan");}
    private static FrameNodeDoc.Node need(FrameNodeDoc graph,int id){FrameNodeDoc.Node node=graph.node(id);if(node==null)throw bad();return node;}
    private static int coordinate(JSONObject value,String key,int fallback){Object raw=value.opt(key);if(raw==null)return fallback;if(!(raw instanceof Number)||!Double.isFinite(((Number)raw).doubleValue()))throw bad();double n=((Number)raw).doubleValue();if(n<-10000||n>10000)throw bad();return (int)n;}
    private static JSONArray list(JSONObject actions,String key){Object raw=actions.opt(key);if(raw==null||raw==JSONObject.NULL)return new JSONArray();if(raw instanceof JSONArray)return (JSONArray)raw;if(raw instanceof JSONObject)return new JSONArray().put(raw);throw bad();}
    private static int resolve(int id,List<Integer> created){if(id<0){long index=-(long)id-1;if(index>=created.size())throw bad();return created.get((int)index);}if(id==0)throw bad();return id;}
    private static void add(List<Step> steps,FrameNodeDoc graph,Step step){if(steps.size()>=1024)throw bad();step.apply(graph);steps.add(step);}
    public static FrameAiPlan compile(FrameNodeDoc current,JSONObject actions){
        if(actions==null||actions.toString().length()>FrameNodeDoc.MAX_TEXT)throw bad();
        FrameNodeDoc graph=current.copy();String before=current.encode();ArrayList<Step> steps=new ArrayList<>();ArrayList<Integer> created=new ArrayList<>();
        JSONArray nodes=list(actions,"add_node");if(nodes.length()>512)throw bad();
        for(int i=0;i<nodes.length();i++){
            JSONObject node=nodes.optJSONObject(i);if(node==null)throw bad();int type=node.optInt("type",-1);if(FrameNodeType.of(type)==null)throw bad();int id=graph.nextId();
            int x=coordinate(node,"x",(i%5)*310),y=coordinate(node,"y",(i/5)*300);
            add(steps,graph,new Step("add",id,0,0,0,x,y,type,null));created.add(id);
            JSONArray knobs=list(node,"knobs");for(int k=0;k<knobs.length();k++){JSONObject knob=knobs.optJSONObject(k);if(knob==null)throw bad();knob(steps,graph,id,knob.optInt("n",-1),knob);}
        }
        JSONArray moves=list(actions,"move_node");for(int i=0;i<moves.length();i++){JSONObject item=moves.optJSONObject(i);if(item==null)throw bad();int id=resolve(item.optInt("id"),created);FrameNodeDoc.Node node=need(graph,id);add(steps,graph,new Step("move",id,0,0,0,coordinate(item,"x",node.x),coordinate(item,"y",node.y),0,null));}
        JSONArray knobs=list(actions,"set_knob");for(int i=0;i<knobs.length();i++){JSONObject item=knobs.optJSONObject(i);if(item==null)throw bad();knob(steps,graph,resolve(item.optInt("id"),created),item.optInt("knob",-1),item);}
        JSONArray links=list(actions,"link");for(int i=0;i<links.length();i++){JSONObject item=links.optJSONObject(i);if(item==null)throw bad();add(steps,graph,new Step("link",resolve(item.optInt("from"),created),resolve(item.optInt("to"),created),item.optInt("pin",0),item.optInt("out",0),0,0,0,null));}
        for(int i=0;i<nodes.length();i++){JSONObject connection=nodes.optJSONObject(i).optJSONObject("connect");if(connection!=null)add(steps,graph,new Step("link",created.get(i),resolve(connection.optInt("to"),created),connection.optInt("pin",0),connection.optInt("out",0),0,0,0,null));}
        JSONArray cuts=list(actions,"cut");for(int i=0;i<cuts.length();i++){JSONObject item=cuts.optJSONObject(i);if(item==null)throw bad();int id=resolve(item.optInt("id"),created);int pin=item.optInt("pin",-1);if(pin<0||pin>=200)throw bad();add(steps,graph,new Step("cut",id,0,pin,0,0,0,0,null));}
        for(String key:new String[]{"open_node","close_node","remove_node"}){JSONArray ids=list(actions,key);for(int i=0;i<ids.length();i++){int id=resolve(ids.optInt(i),created);FrameNodeDoc.Node node=need(graph,id);if(key.equals("remove_node")){if(node.type==60||node.type==61)throw bad();add(steps,graph,new Step("remove",id,0,0,0,0,0,0,null));}else add(steps,graph,new Step("open",id,0,0,0,0,0,key.equals("open_node")?1:0,null));}}
        if(actions.optBoolean("tidy")){java.util.HashMap<Integer,Integer> depths=new java.util.HashMap<>(),rows=new java.util.HashMap<>();for(FrameNodeDoc.Node node:graph.nodes())depths.put(node.id,0);for(int pass=0;pass<graph.count();pass++){boolean changed=false;for(FrameNodeDoc.Wire wire:graph.wires()){int rank=Math.min(25,depths.get(wire.from)+1);if(rank>depths.get(wire.to)){depths.put(wire.to,rank);changed=true;}}if(!changed)break;}for(FrameNodeDoc.Node node:new ArrayList<>(graph.nodes())){int rank=depths.get(node.id),row=rows.getOrDefault(rank,0);rows.put(rank,row+1);int height=82+Math.max(graph.pins(node.id),graph.outs(node.id))*30;add(steps,graph,new Step("move",node.id,0,0,0,rank*310,Math.min(9800,row*(height+50)),0,null));}}
        if(steps.isEmpty()||graph.encode().length()>FrameNodeDoc.MAX_TEXT)throw bad();return new FrameAiPlan(before,graph,steps);
    }
    private static void knob(List<Step> steps,FrameNodeDoc graph,int id,int index,JSONObject item){FrameNodeDoc.Node node=need(graph,id);FrameNodeType.Kind type=FrameNodeType.of(node.type);if(index<0||index>=type.knobs())throw bad();String text=null;int value=0;if(item.has("text")){Object raw=item.opt("text");if(!(raw instanceof String)||((String)raw).length()>8192)throw bad();text=(String)raw;}else{Object raw=item.opt("value");if(!(raw instanceof Number)||!Double.isFinite(((Number)raw).doubleValue()))throw bad();long n=((Number)raw).longValue();if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw bad();value=(int)n;}add(steps,graph,new Step("knob",id,0,index,0,0,0,value,text));}
    public static String schema(){return "actions: {add_node:[{type,x,y,knobs:[{n,value}|{n,text}],connect:{to,pin,out}}],move_node:[{id,x,y}],set_knob:[{id,knob,value}|{id,knob,text}],link:[{from,to,pin,out}],cut:[{id,pin}],remove_node:[id],open_node:[id],close_node:[id],tidy:true}. Negative IDs refer to newly added nodes: -1 first, -2 second. Preserve output nodes 60 and 61. All links must be typed and acyclic. Changes animate after explicit user approval and validation.";}
}

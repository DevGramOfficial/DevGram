package org.telegram.messenger;

import org.json.*;
import java.util.*;
import java.util.function.*;

/** Serial writes, monotonic snapshots and optimistic pixel overlays for a single public row. */
public final class DevGramRowStateController {
    public interface Callback {void done(JSONObject result,String error);}
    public interface Transport {void send(JSONObject body,boolean readOnly,Callback callback);}
    private static final class Mutation {final JSONObject body;final Consumer<JSONArray> done;final boolean persistent;int retries;Mutation(JSONObject body,Consumer<JSONArray> done,boolean persistent){this.body=body;this.done=done;this.persistent=persistent;}}
    public static final int[] PALETTE={0,-16777216,-1,-8355712,-3881788,-2024163,-30208,-7168,-8658914,-14703872,-16722979,-16743481,-13750550,-3182876,-8257408,-22575};
    private final Transport transport;
    private final BooleanSupplier allowed;
    private final Function<String,byte[]> decode;
    private final Runnable changed;
    private final Consumer<String> failed;
    private final ArrayDeque<Mutation> queue=new ArrayDeque<>();
    private final LinkedHashMap<Long,int[]> chunks=new LinkedHashMap<>(256,.75f,true);
    private final HashMap<Long,Integer> overlay=new HashMap<>();
    private int x,y,span=32,viewport,viewportVersion=-1,knownVersion=-1,baseCount,optimisticCount,revision;
    private boolean reading,writing,closed;
    private String readError;
    private long updated;
    private JSONObject player=new JSONObject();private long playerVersion=-1,playerAt=-1,playerReceived;private boolean canPaint;
    public DevGramRowStateController(Transport transport,BooleanSupplier allowed,Function<String,byte[]> decode,Runnable changed,Consumer<String> failed){this.transport=transport;this.allowed=allowed;this.decode=decode;this.changed=changed;this.failed=failed;}
    private static long key(int x,int y){return ((long)x<<32)|(y&0xffffffffL);}
    public static int color(int value){return value>=0&&value<PALETTE.length?PALETTE[value]:value|0xff000000;}
    public int revision(){return revision;}
    public boolean busy(){return writing||!queue.isEmpty();}
    public boolean hasSnapshot(){return viewportVersion>=0&&viewportVersion==knownVersion;}
    public int count(){return (int)Math.min(Integer.MAX_VALUE,(long)baseCount+optimisticCount);}
    public long updated(){return updated;}
    public JSONObject player(){try{return new JSONObject(player.toString());}catch(Exception e){return new JSONObject();}}
    public boolean canPaint(){return canPaint;}
    public String readError(){return readError;}
    public int ready(){double elapsed=Math.max(0,System.nanoTime()-playerReceived)/1e9;return Math.max(0,Math.min(500,(int)Math.min(player.optInt("max",0),player.optDouble("charges",0)+elapsed/Math.max(1,player.optDouble("regen",30)))));}
    public void buy(String item){try{enqueueFields("buy",new JSONObject().put("purchase",item));}catch(Exception ignored){}}
    public void ban(long uid,boolean value){try{enqueueFields("ban",new JSONObject().put("user",uid).put("banned",value));}catch(Exception ignored){}}
    private void enqueueFields(String action,JSONObject fields){if(closed||!allowed.getAsBoolean())return;try{JSONObject body=view().put("action",action).put("nonce",UUID.randomUUID().toString());for(Iterator<String> keys=fields.keys();keys.hasNext();){String key=keys.next();body.put(key,fields.get(key));}queue.addLast(new Mutation(body,null,false));notifyChanged();pump();}catch(Exception e){failed.accept("bad_document");}}
    public void cell(int x,int y,Consumer<JSONObject> callback){if(closed||!allowed.getAsBoolean())return;try{transport.send(new JSONObject().put("action","cell").put("x",x).put("y",y),false,(result,error)->{if(closed||!allowed.getAsBoolean())return;if(result!=null)callback.accept(result);else failed.accept(error==null?"network":error);});}catch(Exception e){failed.accept("bad_document");}}
    public void viewport(int x,int y,int span){x=Math.max(-100000,Math.min(100000,x));y=Math.max(-100000,Math.min(100000,y));span=Math.max(8,Math.min(384,span));if(this.x==x&&this.y==y&&this.span==span)return;this.x=x;this.y=y;this.span=span;viewport++;viewportVersion=-1;revision++;notifyChanged();}
    private JSONObject view(){try{return new JSONObject().put("action","read").put("x",x).put("y",y).put("span",span).put("version",viewportVersion);}catch(Exception e){return new JSONObject();}}
    public void poll(){if(closed||reading||!allowed.getAsBoolean())return;reading=true;int generation=viewport;JSONObject fields=view();transport.send(fields,true,(result,error)->{reading=false;if(closed||!allowed.getAsBoolean())return;if(result!=null){readError=null;accept(result,generation);}else{String next=error==null?"network":error;if(!java.util.Objects.equals(readError,next)){readError=next;notifyChanged();}}});}
    public int pixel(int x,int y){Integer pending=overlay.get(key(x,y));if(pending!=null)return pending;int[] chunk=chunks.get(key(Math.floorDiv(x,32),Math.floorDiv(y,32)));return chunk==null?0:chunk[Math.floorMod(y,32)*32+Math.floorMod(x,32)];}
    /** Read-only tile access for native drawing without a hash lookup per screen pixel. */
    public void tiles(BiConsumer<Long,int[]> visitor){chunks.forEach(visitor);}
    /** Tile arrays are immutable after acceptance; copy maps before background inspection. */
    public IntBinaryOperator snapshot(){Map<Long,int[]> saved=new HashMap<>(chunks);Map<Long,Integer> pending=new HashMap<>(overlay);return (x,y)->{Integer point=pending.get(key(x,y));if(point!=null)return point;int[] tile=saved.get(key(Math.floorDiv(x,32),Math.floorDiv(y,32)));return tile==null?0:tile[Math.floorMod(y,32)*32+Math.floorMod(x,32)];};}
    public void countTap(){enqueue("count",null,null);}
    public void paint(JSONArray points,Consumer<JSONArray> done){if(points==null||points.length()==0)return;if(points.length()>512)throw new IllegalArgumentException("pixel batch exceeds512");enqueue("paint",points,done);}
    public void paintPersistent(JSONArray points,Consumer<JSONArray> done){if(points==null||points.length()==0)return;if(points.length()>512)throw new IllegalArgumentException("pixel batch exceeds512");enqueue("paint",points,done,true);}
    public void clear(){enqueue("clear",null,null);}
    private void enqueue(String action,JSONArray points,Consumer<JSONArray> done){
        enqueue(action,points,done,false);
    }
    private void enqueue(String action,JSONArray points,Consumer<JSONArray> done,boolean persistent){
        if(closed||!allowed.getAsBoolean())return;
        try{JSONObject body=view().put("action",action).put("nonce",UUID.randomUUID().toString());if(points!=null)body.put("points",new JSONArray(points.toString()));queue.addLast(new Mutation(body,done,persistent));rebuildOverlay();notifyChanged();pump();}catch(Exception e){failed.accept("bad_document");}
    }
    private void pump(){
        if(writing||queue.isEmpty())return;
        if(!allowed.getAsBoolean()){queue.clear();rebuildOverlay();notifyChanged();return;}
        Mutation head=queue.peekFirst();int generation=viewport;writing=true;
        transport.send(head.body,false,(result,error)->{
            writing=false;if(queue.peekFirst()!=head)return;
            if(result==null&&"network".equals(error)&&head.retries++<1&&allowed.getAsBoolean()){pump();return;}
            queue.removeFirst();
            rebuildOverlay();
            if(result!=null&&allowed.getAsBoolean()){accept(result,generation);if(head.done!=null&&(!closed||head.persistent))head.done.accept(result.optJSONArray("accepted")!=null?result.optJSONArray("accepted"):result.optJSONArray("applied"));}
            else if(!closed)failed.accept(error==null?"network":error);
            notifyChanged();pump();
        });
    }
    private void rebuildOverlay(){
        overlay.clear();optimisticCount=0;
        for(Mutation mutation:queue){if(mutation.body.optString("action").equals("count"))optimisticCount++;JSONArray points=mutation.body.optJSONArray("points");if(points!=null)for(int i=0;i<points.length();i++){JSONArray point=points.optJSONArray(i);if(point!=null)overlay.put(key(point.optInt(0),point.optInt(1)),color(point.optInt(2)));}}
        revision++;
    }
    private void accept(JSONObject result,int generation){
        JSONObject next=result.optJSONObject("player");if(next!=null){long pv=next.optLong("version",0),at=next.optLong("at",0);if(pv>playerVersion||pv==playerVersion&&at>=playerAt){player=next;playerVersion=pv;playerAt=at;playerReceived=System.nanoTime();canPaint=result.optBoolean("canPaint",false);}}
        int version=result.optInt("version",-1);
        if(version>=knownVersion){knownVersion=version;baseCount=result.optInt("count",baseCount);updated=result.optLong("updated",updated);}
        JSONObject data=result.optJSONObject("chunks");
        if(data!=null&&generation==viewport&&version>=viewportVersion){
            int rx=result.optInt("x",x),ry=result.optInt("y",y),rs=result.optInt("span",span);
            if(rx==x&&ry==y&&rs==span){
                if(version!=viewportVersion){
                    int minX=Math.floorDiv(x,32),maxX=Math.floorDiv(x+span-1,32),minY=Math.floorDiv(y,32),maxY=Math.floorDiv(y+span-1,32);
                    for(int xx=minX;xx<=maxX;xx++)for(int yy=minY;yy<=maxY;yy++){
                        String name=xx+","+yy;if(!data.has(name)){chunks.remove(key(xx,yy));continue;}
                        try{byte[] raw=decode.apply(data.optString(name));if(raw==null||raw.length!=4096&&raw.length!=1024)continue;int[] pixels=new int[1024];for(int i=0;i<pixels.length;i++)pixels[i]=raw.length==1024?PALETTE[Math.max(0,Math.min(PALETTE.length-1,raw[i]&255))]:(raw[i*4]&255)<<24|(raw[i*4+1]&255)<<16|(raw[i*4+2]&255)<<8|raw[i*4+3]&255;chunks.put(key(xx,yy),pixels);}catch(Exception ignored){}
                    }
                    while(chunks.size()>256)chunks.remove(chunks.keySet().iterator().next());
                }
                viewportVersion=version;
            }
        }
        revision++;notifyChanged();
    }
    private void notifyChanged(){if(!closed)changed.run();}
    /** Already queued user edits finish; polling and all view callbacks stop. */
    public void close(){closed=true;}
}

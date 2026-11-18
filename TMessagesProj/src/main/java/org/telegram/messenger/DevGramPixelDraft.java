package org.telegram.messenger;

import org.json.JSONArray;
import java.util.*;

/** Unsent local pixels. Eraser/history affect this draft; only Send mutates the shared canvas. */
public final class DevGramPixelDraft {
    private LinkedHashMap<Long,int[]> points=new LinkedHashMap<>();
    private final ArrayDeque<LinkedHashMap<Long,int[]>> past=new ArrayDeque<>(),future=new ArrayDeque<>();
    private static long key(int x,int y){return ((long)x<<32)|(y&0xffffffffL);}
    private static LinkedHashMap<Long,int[]> copy(Map<Long,int[]> source){LinkedHashMap<Long,int[]> result=new LinkedHashMap<>();for(Map.Entry<Long,int[]> entry:source.entrySet())result.put(entry.getKey(),entry.getValue().clone());return result;}
    private void remember(){past.push(copy(points));while(past.size()>64)past.removeLast();future.clear();}
    public int size(){return points.size();}
    public void visit(java.util.function.Consumer<int[]> visitor){for(int[] point:points.values())visitor.accept(point);}
    public Integer colorAt(int x,int y){int[] point=points.get(key(x,y));return point==null?null:point[2];}
    public boolean paint(int x,int y,int color,int ready){
        if(x<-100000||x>100000||y<-100000||y>100000)return false;color=DevGramRowStateController.color(color);long key=key(x,y);int[] old=points.get(key);
        if(old!=null&&old[2]==color)return true;if(old==null&&points.size()>=Math.min(512,Math.max(0,ready)))return false;
        remember();points.put(key,new int[]{x,y,color});return true;
    }
    public boolean erase(int x,int y){long key=key(x,y);if(!points.containsKey(key))return false;remember();points.remove(key);return true;}
    public void clear(){if(points.isEmpty())return;remember();points.clear();}
    public boolean history(boolean forward){ArrayDeque<LinkedHashMap<Long,int[]>> from=forward?future:past,to=forward?past:future;if(from.isEmpty())return false;to.push(copy(points));points=from.pop();return true;}
    public JSONArray points(){JSONArray result=new JSONArray();for(int[] point:points.values())result.put(new JSONArray().put(point[0]).put(point[1]).put(point[2]));return result;}
    /** A later edit of the same cell survives acknowledgement of an earlier Send. */
    public void acknowledge(JSONArray accepted){
        if(accepted==null)return;boolean changed=false;
        for(int i=0;i<accepted.length();i++){JSONArray point=accepted.optJSONArray(i);if(point==null||point.length()<3)continue;long key=key(point.optInt(0),point.optInt(1));int[] current=points.get(key);if(current!=null&&current[2]==DevGramRowStateController.color(point.optInt(2))){points.remove(key);changed=true;}}
        if(changed){past.clear();future.clear();}
    }
}

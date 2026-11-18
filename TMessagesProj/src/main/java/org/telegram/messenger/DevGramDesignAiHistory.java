package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;

/** Bounded, memory-only tool conversations. An account, target and editor never share context. */
public final class DevGramDesignAiHistory {
    public static final int PROFILE=0,NODES=1,BLOCKS=2;
    private static final LinkedHashMap<String,ArrayDeque<String[]>> histories=new LinkedHashMap<>(16,.75f,true);
    private DevGramDesignAiHistory(){}
    public static String scope(int account,long uid,long target,int mode,String block){
        return account+":"+uid+":"+target+":"+mode+":"+(block==null?"":block);
    }
    public static synchronized JSONArray history(String key){
        JSONArray result=new JSONArray();ArrayDeque<String[]> pairs=histories.get(key);
        if(pairs!=null)for(String[] pair:pairs)try{result.put(new JSONObject().put("role","user").put("content",pair[0]));result.put(new JSONObject().put("role","assistant").put("content",pair[1]));}catch(Exception ignored){}
        return result;
    }
    public static synchronized void remember(String key,String request,String answer){
        if(request==null||answer==null||request.trim().isEmpty()||answer.trim().isEmpty())return;
        ArrayDeque<String[]> pairs=histories.get(key);if(pairs==null){pairs=new ArrayDeque<>();histories.put(key,pairs);}
        pairs.addLast(new String[]{trim(request),trim(answer)});while(pairs.size()>5)pairs.removeFirst();
        while(histories.size()>64)histories.remove(histories.keySet().iterator().next());
    }
    public static synchronized void forget(String key){histories.remove(key);}
    private static String trim(String value){int end=Math.min(600,value.length());if(end<value.length()&&end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;return value.substring(0,end);}
}

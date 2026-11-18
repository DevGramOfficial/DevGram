package org.telegram.messenger;

import java.util.Arrays;
import java.util.HashMap;

/** Stable dependency order. UI positions are independent of evaluation order. */
public final class DevGramNodeGraph {
    private DevGramNodeGraph(){}
    public static int[] order(int[] ids,int[][] inputs){
        int count=ids.length;if(count>512||inputs.length!=count)return null;
        HashMap<Integer,Integer> indices=new HashMap<>();
        for(int i=0;i<count;i++)if(ids[i]<0||ids[i]>=512||indices.put(ids[i],i)!=null)return null;
        int[] pending=new int[count],result=new int[count];boolean[] emitted=new boolean[count];
        for(int i=0;i<count;i++){
            if(inputs[i]==null||inputs[i].length>2)return null;
            for(int source:inputs[i])if(!indices.containsKey(source)||source==ids[i])return null;
            pending[i]=inputs[i].length;
        }
        for(int at=0;at<count;at++){
            int chosen=-1;for(int i=0;i<count;i++)if(!emitted[i]&&pending[i]==0){chosen=i;break;}
            if(chosen<0)return null;result[at]=chosen;emitted[chosen]=true;
            for(int i=0;i<count;i++)if(!emitted[i])for(int source:inputs[i])if(source==ids[chosen])pending[i]--;
        }
        return result;
    }
}

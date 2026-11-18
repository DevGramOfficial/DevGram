package org.telegram.messenger;

/** Reference integration selection: ordered fields, a default primary and at least one selected field. */
public final class DevGramServiceSelection {
    private DevGramServiceSelection(){}
    public static int[] effective(int[] values,int count){
        java.util.LinkedHashSet<Integer> unique=new java.util.LinkedHashSet<>();
        if(values!=null)for(int value:values)if(value>=0&&value<count)unique.add(value);
        if(unique.isEmpty()&&count>0)unique.add(0);
        int[] result=new int[unique.size()];int i=0;for(int value:unique)result[i++]=value;return result;
    }
    public static int[] toggle(int[] values,int index,int count){
        int[] current=effective(values,count);if(index<0||index>=count)return current;
        boolean found=false;for(int value:current)found|=value==index;
        if(found&&current.length==1)return current;
        int[] result=new int[current.length+(found?-1:1)];int i=0;for(int value:current)if(value!=index)result[i++]=value;if(!found)result[i]=index;return result;
    }
}

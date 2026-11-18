package org.telegram.messenger;

import java.util.*;

/** Deterministic, bounded palette reduction for a local canvas drawing guide. */
public final class DevGramCanvasQuantizer {
    private DevGramCanvasQuantizer() {}
    public static int[] fit(int width,int height,int side){side=Math.max(4,Math.min(384,side));if(width<=0||height<=0)return new int[]{side,side};return width>=height?new int[]{side,Math.max(1,Math.round((float)height*side/width))}:new int[]{Math.max(1,Math.round((float)width*side/height)),side};}
    public static int[] quantize(int[] input,int count){
        count=Math.max(2,Math.min(64,count));int[] histogram=new int[4096];for(int pixel:input)if((pixel>>>24)!=0)histogram[((pixel>>12)&0xf00)|((pixel>>8)&0xf0)|((pixel>>4)&15)]++;
        Integer[] bins=new Integer[4096];for(int i=0;i<bins.length;i++)bins[i]=i;Arrays.sort(bins,(a,b)->Integer.compare(histogram[b],histogram[a]));int used=0;while(used<count&&histogram[bins[used]]>0)used++;if(used==0)return input.clone();
        int[][] centers=new int[used][3];for(int i=0;i<used;i++){int key=bins[i];centers[i]=new int[]{((key>>8)&15)*17,((key>>4)&15)*17,(key&15)*17};}
        for(int round=0;round<2;round++){long[][] sums=new long[used][4];for(int pixel:input)if((pixel>>>24)!=0){int[] rgb={(pixel>>16)&255,(pixel>>8)&255,pixel&255};long[] sum=sums[nearest(rgb,centers)];for(int c=0;c<3;c++)sum[c]+=rgb[c];sum[3]++;}for(int i=0;i<used;i++)if(sums[i][3]>0)for(int c=0;c<3;c++)centers[i][c]=(int)(sums[i][c]/sums[i][3]);}
        int[] output=new int[input.length];for(int i=0;i<input.length;i++){int pixel=input[i];if((pixel>>>24)==0)continue;int[] rgb={(pixel>>16)&255,(pixel>>8)&255,pixel&255},chosen=centers[nearest(rgb,centers)];output[i]=(pixel&0xff000000)|(chosen[0]<<16)|(chosen[1]<<8)|chosen[2];}return output;
    }
    private static int nearest(int[] rgb,int[][] centers){int best=0,distance=Integer.MAX_VALUE;for(int i=0;i<centers.length;i++){int next=0;for(int c=0;c<3;c++){int delta=rgb[c]-centers[i][c];next+=delta*delta;}if(next<distance){distance=next;best=i;}}return best;}
    public static Map<Integer,Integer> remaining(int[] guide,int width,int x,int y,java.util.function.IntBinaryOperator actual){if(width<1||guide.length%width!=0||guide.length>384*384)throw new IllegalArgumentException("guide dimensions");HashMap<Integer,Integer> result=new HashMap<>();for(int i=0;i<guide.length;i++){int color=guide[i];if((color>>>24)==0)continue;color|=0xff000000;int painted=actual.applyAsInt(x+i%width,y+i/width);if(painted!=0)painted|=0xff000000;result.putIfAbsent(color,0);if(painted!=color)result.put(color,result.get(color)+1);}return result;}
}

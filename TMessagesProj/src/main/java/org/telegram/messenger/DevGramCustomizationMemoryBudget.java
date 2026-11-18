package org.telegram.messenger;

/** Exact reference heap thresholds and bitmap budgets; pure arithmetic for tests. */
public final class DevGramCustomizationMemoryBudget {
    private static final long MB=1048576L;
    private DevGramCustomizationMemoryBudget(){}
    public static boolean tight(long used,long max){return max>0&&used>=Math.min(360*MB,max/8*5);}
    public static boolean critical(long used,long max){return max>0&&used>=Math.min(368*MB,max/4*3);}
    public static long animation(long max){return Math.min(8*MB,Math.max(0,max)/64);}
    public static long painter(long max){return Math.min(24*MB,Math.max(0,max)/24);}
    public static long chatFrames(long max){return Math.min(48*MB,Math.max(0,max)/12);}
    public static long previews(long max){return Math.min(16*MB,Math.max(0,max)/32);}
    public static boolean canExpand(int w,int h,int frames,long used,long max){return w>0&&h>0&&frames>0&&max>0&&(long)w*h<=Math.min(animation(max),Math.max(0,max-used)/4)/4/((long)frames+3);}
}

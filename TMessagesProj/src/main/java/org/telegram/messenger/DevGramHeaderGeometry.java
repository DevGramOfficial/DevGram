package org.telegram.messenger;

/** Small, deterministic anchor solver shared by the live header and design previews. */
public final class DevGramHeaderGeometry {
    public static final String[] PARTS = {"avatar", "name", "status", "buttons", "title"};
    private DevGramHeaderGeometry() { }

    /** Target zero preserves the native position; 1..N refers to another part. */
    public static float[] resolve(float[] nativePositions, float[] sizes, int[] targets,
                                  int[] alignments, float[] offsets, float pageWidth) {
        int count = nativePositions.length;
        if (sizes.length != count || targets.length != count || alignments.length != count || offsets.length != count) {
            throw new IllegalArgumentException("header geometry dimensions");
        }
        float[] result = new float[count];
        boolean[] done = new boolean[count];
        for (int i = 0; i < count; i++) {
            if (cyclic(i, targets)) {
                result[i] = finite(nativePositions[i]) + offset(offsets[i], pageWidth);
                done[i] = true;
            }
        }
        for (int i = 0; i < count; i++) solve(i, nativePositions, sizes, targets, alignments, offsets, pageWidth, result, done);
        return result;
    }

    private static float solve(int part, float[] original, float[] size, int[] targets,
                               int[] aligns, float[] offsets, float width, float[] result, boolean[] done) {
        if (done[part]) return result[part];
        int target = targets[part] - 1;
        float start = finite(original[part]);
        if (target >= 0 && target < targets.length && target != part) {
            float alignment = Math.max(0, Math.min(2, aligns[part])) / 2f;
            start = solve(target, original, size, targets, aligns, offsets, width, result, done)
                    + (Math.max(0, finite(size[target])) - Math.max(0, finite(size[part]))) * alignment;
        }
        done[part] = true;
        return result[part] = start + offset(offsets[part], width);
    }

    public static boolean cyclic(int part, int[] targets) {
        boolean[] path = new boolean[targets.length];
        int cursor = part;
        while (cursor >= 0 && cursor < targets.length) {
            if (path[cursor]) return true;
            path[cursor] = true;
            cursor = targets[cursor] - 1;
        }
        return false;
    }

    /** Source target IDs: 0 native, 1 screen, 2 thought, 3..7 header parts. */
    public static float[] anchors(float[] base,float[] sizes,int[] targets,int[] from,int[] to,float[] margins,float[] external,float[] externalSizes){
        float[] out=base.clone();int[] state=new int[base.length];
        for(int i=0;i<base.length;i++)anchor(i,base,sizes,targets,from,to,margins,external,externalSizes,out,state);
        return out;
    }
    private static float anchor(int i,float[] base,float[] size,int[] target,int[] from,int[] to,float[] margin,float[] external,float[] externalSize,float[] out,int[] state){
        if(state[i]==2)return out[i];if(state[i]==1){state[i]=2;return out[i]=base[i];}state[i]=1;
        int destination=target[i],part=destination-3;float start=Float.NaN,length=0;
        if(part>=0&&part<base.length&&part!=i){start=anchor(part,base,size,target,from,to,margin,external,externalSize,out,state);length=size[part];if(state[i]==2)return out[i];}
        else if(destination>=1&&destination<=2&&externalSize[destination-1]>0){start=external[destination-1];length=externalSize[destination-1];}
        out[i]=Float.isNaN(start)?base[i]:start+point(to[i])*length+margin[i]-point(from[i])*size[i];state[i]=2;return out[i];
    }
    private static float point(int edge){return edge==0?0:edge==2?1:.5f;}
    /** A pinned part moves through its anchor margin in dp; a free part uses page-width %. */
    public static float dragOffset(float initial, float pixels, float pageWidth, float density, boolean anchored) {
        float limit = anchored ? 600 : 45;
        float divisor = anchored ? Math.max(.01f, density) : Math.max(1, pageWidth) / 100;
        return Math.max(-limit, Math.min(limit, finite(initial) + finite(pixels) / divisor));
    }
    public static float headerAmount(float expanded,float pull){pull=Math.max(0,Math.min(1,pull));return Math.max(0,Math.min(1,expanded))*(pull<=.4f?1:1-(pull-.4f)/.6f);}
    /** Freeze anchor deltas after native geometry settles; never feed our previous offsets back in. */
    public static final class ReferenceState {
        private long signature=Long.MIN_VALUE,geometry=Long.MIN_VALUE,settled=Long.MIN_VALUE,lastSolved=Long.MIN_VALUE,openedAt;
        private int geometryFrames,stable;private boolean ready;
        private final float[] dx=new float[5],dy=new float[5];
        public float[][] update(long style,long now,boolean always,boolean full,boolean pulling,float[] x,float[] y,float[] w,float[] h,int[] targets,int[] fromX,int[] fromY,int[] toX,int[] toY,float[] marginsX,float[] marginsY,float[] externalX,float[] externalY,float[] externalW,float[] externalH){
            if(style!=signature){signature=style;openedAt=now;ready=false;stable=geometryFrames=0;geometry=settled=lastSolved=Long.MIN_VALUE;java.util.Arrays.fill(dx,0);java.util.Arrays.fill(dy,0);}
            long measured=17;boolean valid=true;
            for(int i=0;i<5;i++){for(float value:new float[]{x[i],y[i],w[i],h[i]})measured=measured*31+Math.round(value/4);if(targets[i]!=0&&(w[i]<=0||h[i]<=0))valid=false;}
            geometryFrames=measured==geometry?geometryFrames+1:0;geometry=measured;if(geometryFrames>=4)settled=measured;
            boolean frozen=now-openedAt>=6000||now-openedAt>=700&&stable>=4;
            if(!pulling&&full&&valid&&(!ready||always||!frozen||settled!=Long.MIN_VALUE&&settled!=lastSolved)){
                float[] rx=anchors(x,w,targets,fromX,toX,marginsX,externalX,externalW),ry=anchors(y,h,targets,fromY,toY,marginsY,externalY,externalH);float change=0;
                for(int i=0;i<5;i++){float nx=targets[i]==0?0:rx[i]-x[i],ny=targets[i]==0?0:ry[i]-y[i];change=Math.max(change,Math.max(Math.abs(nx-dx[i]),Math.abs(ny-dy[i])));dx[i]=nx;dy[i]=ny;}
                stable=change<=.5f?stable+1:0;ready=true;lastSolved=settled;
            }
            float[][] result={dx.clone(),dy.clone()};return result;
        }
        public boolean needsFrame(long now,boolean always){return always||!ready||now-openedAt<6000&&(now-openedAt<700||stable<4);}
    }

    private static float offset(float percent, float width) {
        return Math.max(-45, Math.min(45, finite(percent))) * Math.max(0, finite(width)) / 100f;
    }
    private static float finite(float value) { return Float.isNaN(value) || Float.isInfinite(value) ? 0 : value; }
}

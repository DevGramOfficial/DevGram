package org.telegram.ui.Components.devgram.frame;

/** Bounded obstacle routing for the three canvas wire styles. */
public final class FrameNodeRouting {
    private FrameNodeRouting() {}
    public static boolean crosses(float ax,float ay,float bx,float by,float left,float top,float right,float bottom){
        float low=0,high=1,dx=bx-ax,dy=by-ay;
        for(int edge=0;edge<4;edge++){
            float p=edge==0?-dx:edge==1?dx:edge==2?-dy:dy;
            float q=edge==0?ax-left:edge==1?right-ax:edge==2?ay-top:bottom-ay;
            if(p==0){if(q<=0)return false;continue;}float ratio=q/p;
            if(p<0)low=Math.max(low,ratio);else high=Math.min(high,ratio);
            if(low>=high)return false;
        }
        return low<high;
    }
    private static boolean endpoint(float[] r,int at,float x,float y){return x>=r[at]&&x<=r[at+2]&&y>=r[at+1]&&y<=r[at+3];}
    public static float[] route(float ax,float ay,float bx,float by,float[] rectangles,int count){
        float[] points=new float[32];points[0]=ax;points[1]=ay;points[2]=bx;points[3]=by;int size=2;
        count=Math.max(0,Math.min(count,rectangles.length/4));
        for(int pass=0;pass<6&&size<=14;pass++){
            int segment=-1,obstacle=-1;
            for(int p=0;p<size-1&&segment<0;p++)for(int i=0;i<count;i++){
                int at=i*4;if(endpoint(rectangles,at,ax,ay)||endpoint(rectangles,at,bx,by))continue;
                if(crosses(points[p*2],points[p*2+1],points[p*2+2],points[p*2+3],rectangles[at],rectangles[at+1],rectangles[at+2],rectangles[at+3])){segment=p;obstacle=at;break;}
            }
            if(segment<0)break;
            float x1=points[segment*2],y1=points[segment*2+1],x2=points[segment*2+2],y2=points[segment*2+3];
            float l=rectangles[obstacle]-20,t=rectangles[obstacle+1]-20,r=rectangles[obstacle+2]+20,b=rectangles[obstacle+3]+20;
            float best=Float.MAX_VALUE,cx1=0,cy1=0,cx2=0,cy2=0;
            for(int option=0;option<4;option++){
                float a,b1,c,d;
                if(option<2){a=x1<=x2?l:r;c=x1<=x2?r:l;b1=d=option==0?t:b;}
                else{a=c=option==2?l:r;b1=y1<=y2?t:b;d=y1<=y2?b:t;}
                if(crosses(x1,y1,a,b1,rectangles[obstacle],rectangles[obstacle+1],rectangles[obstacle+2],rectangles[obstacle+3])||crosses(a,b1,c,d,rectangles[obstacle],rectangles[obstacle+1],rectangles[obstacle+2],rectangles[obstacle+3])||crosses(c,d,x2,y2,rectangles[obstacle],rectangles[obstacle+1],rectangles[obstacle+2],rectangles[obstacle+3]))continue;
                float cost=(float)(Math.hypot(x1-a,y1-b1)+Math.hypot(a-c,b1-d)+Math.hypot(c-x2,d-y2));
                for(int i=0;i<count;i++){int at=i*4;if(endpoint(rectangles,at,ax,ay)||endpoint(rectangles,at,bx,by))continue;if(crosses(x1,y1,a,b1,rectangles[at],rectangles[at+1],rectangles[at+2],rectangles[at+3])||crosses(a,b1,c,d,rectangles[at],rectangles[at+1],rectangles[at+2],rectangles[at+3])||crosses(c,d,x2,y2,rectangles[at],rectangles[at+1],rectangles[at+2],rectangles[at+3]))cost+=100000;}
                if(cost<best){best=cost;cx1=a;cy1=b1;cx2=c;cy2=d;}
            }
            if(best==Float.MAX_VALUE)break;
            System.arraycopy(points,(segment+1)*2,points,(segment+3)*2,(size-segment-1)*2);points[(segment+1)*2]=cx1;points[(segment+1)*2+1]=cy1;points[(segment+2)*2]=cx2;points[(segment+2)*2+1]=cy2;size+=2;
        }
        return java.util.Arrays.copyOf(points,size*2);
    }
}

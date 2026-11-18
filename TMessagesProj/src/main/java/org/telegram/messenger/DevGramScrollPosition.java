package org.telegram.messenger;

/** A bounded back-and-forth scroll clock with readable pauses and manual override. */
public final class DevGramScrollPosition {
    private float range,position;
    private int direction=1;
    private long last,holdUntil;
    private boolean configured;
    public void configure(float range,float position,boolean rtl,long now){
        float oldRange=this.range,oldPosition=this.position;boolean initial=!configured||oldRange<=0;
        this.range=Math.max(0,range);this.position=Math.max(0,Math.min(this.range,position));
        if(initial)direction=rtl?-1:1;
        if(this.position<=0)direction=1;else if(this.position>=this.range)direction=-1;
        // A changing album/statistic width must not restart the clock or shorten a touch pause.
        if(!initial&&this.range>oldRange&&oldPosition>=oldRange)direction=1;
        configured=true;if(initial){last=now;holdUntil=now+1400;}
    }
    /** Even a long liked-track title must reveal the final statistic within one short pass. */
    public float pixelsPerSecond(float minimum){return Math.max(minimum,range/12f);}
    public void resume(long now){last=now;holdUntil=Math.max(holdUntil,now+700);}
    public void manual(float position,long now){this.position=Math.max(0,Math.min(range,position));if(this.position<=0)direction=1;else if(this.position>=range)direction=-1;last=now;holdUntil=now+6000;}
    public float advance(long now,float pixelsPerSecond){
        long elapsed=Math.min(80,Math.max(0,now-last));last=now;
        if(range<=0||now<holdUntil)return position;
        position+=direction*Math.max(0,pixelsPerSecond)*elapsed/1000f;
        if(position>=range){position=range;direction=-1;holdUntil=now+2400;}
        else if(position<=0){position=0;direction=1;holdUntil=now+2400;}
        return position;
    }
    public long delay(long now){return now<holdUntil?holdUntil-now:33;}
}

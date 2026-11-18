package org.telegram.messenger;

/** Shared monotonic playback clock. Network snapshots never turn a pause into playback. */
public final class DevGramPlaybackState {
    public final long duration,receivedAt;
    public final boolean playing;
    public final double speed;
    private final long position,age;
    public DevGramPlaybackState(long position,long duration,boolean playing,double speed,long updatedAt,long serverTime,long receivedAt){
        this.duration=Math.max(0,duration);this.position=Math.max(0,position);this.playing=playing;
        this.speed=playing&&Double.isFinite(speed)?Math.max(0,Math.min(4,speed)):0;
        this.age=updatedAt>0&&serverTime>=updatedAt?Math.min(120000,serverTime-updatedAt):0;
        this.receivedAt=receivedAt;
    }
    public long positionAt(long now){
        long elapsed=Math.max(0,now-receivedAt);long value=position+(long)((age+elapsed)*speed);
        return duration>0?Math.min(duration,Math.max(0,value)):Math.max(0,value);
    }
    public float progressAt(long now){return duration>0?(float)positionAt(now)/duration:-1;}
    public boolean liveAt(long now){return age+Math.max(0,now-receivedAt)<120000;}
    public static String clock(long millis){long seconds=Math.max(0,millis)/1000;return seconds/60+":"+(seconds%60<10?"0":"")+seconds%60;}
}

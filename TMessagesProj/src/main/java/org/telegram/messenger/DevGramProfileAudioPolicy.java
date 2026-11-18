package org.telegram.messenger;

/** Shared foreground/priority rules, independent of decoders and Android views. */
public final class DevGramProfileAudioPolicy {
    private DevGramProfileAudioPolicy() {}
    public static int volume(int percent){return Math.max(0,Math.min(65,percent));}
    public static int select(boolean bannerVideo,boolean bannerSound,int bannerVolume,boolean pageVideo,boolean pageSound,int pageVolume){
        if(bannerVideo&&bannerSound&&volume(bannerVolume)>0)return 1;
        return pageVideo&&pageSound&&volume(pageVolume)>0?2:0;
    }
    public static boolean mayPlay(boolean visible,boolean attached,boolean interactive,boolean enabled,boolean allowed,boolean suppressed,boolean competing){
        return visible&&attached&&interactive&&enabled&&allowed&&!suppressed&&!competing;
    }
}

package org.telegram.messenger;

/** Each visitor controls their own view of a frame; authors cannot change that preference. */
public final class DevGramFrameEvents {
    private DevGramFrameEvents() {}
    private static String key(int account,long profile,String block){return "devgramFrameEvent_"+UserConfig.getInstance(account).getClientUserId()+"_"+profile+"_"+block;}
    public static boolean checked(int account,long profile,String block){return MessagesController.getGlobalMainSettings().getBoolean(key(account,profile,block),false);}
    public static void set(int account,long profile,String block,boolean enabled){if(!DevGramProfilePolicy.allowed(profile,"avatar.nodes"))return;MessagesController.getGlobalMainSettings().edit().putBoolean(key(account,profile,block),enabled).apply();DevGramProfilePolicy.changed();}
}

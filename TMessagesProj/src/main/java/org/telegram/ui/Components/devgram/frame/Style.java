package org.telegram.ui.Components.devgram.frame;
final class Style {
 static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
 static float clampF(float n,float min,float max){return Float.isNaN(n)?min:Math.max(min,Math.min(max,n));}
 static int color(String text,int fallback){try{String value=text.trim();if(value.startsWith("#"))value=value.substring(1);if(value.length()==6)return (int)(0xff000000L|Long.parseLong(value,16));if(value.length()==8)return (int)Long.parseLong(value,16);}catch(Exception ignored){}return fallback;}
 static String hex(int color){return String.format(java.util.Locale.US,"#%08X",color);}
}

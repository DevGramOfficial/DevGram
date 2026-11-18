package org.telegram.messenger;
import java.util.Locale;
/** The reference's #RGB/#RRGGBB/#AARRGGBB and rgb/rgba editor input. */
public final class DevGramColorCode {
    private DevGramColorCode(){}
    public static Integer parse(String input,int alpha){
        if(input==null||input.length()>128)return null;String value=input.trim().toLowerCase(Locale.ROOT);
        try{
            if(value.startsWith("#")||value.matches("[0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8}")){
                if(value.startsWith("#"))value=value.substring(1);if(!value.matches("[0-9a-f]+"))return null;
                if(value.length()==3)value=""+value.charAt(0)+value.charAt(0)+value.charAt(1)+value.charAt(1)+value.charAt(2)+value.charAt(2);
                if(value.length()==8)return (int)Long.parseLong(value,16);return value.length()==6?(alpha<<24)|Integer.parseInt(value,16):null;
            }
            int expected=0;if(value.startsWith("rgba(")){expected=4;value=value.substring(5);}else if(value.startsWith("rgb(")){expected=3;value=value.substring(4);}if(expected!=0){if(!value.endsWith(")"))return null;value=value.substring(0,value.length()-1);}
            String[] parts=value.split(",",-1);if(expected!=0&&parts.length!=expected||parts.length!=3&&parts.length!=4)return null;int rgb=0;
            for(int i=0;i<3;i++){String channel=parts[i].trim();if(!channel.matches("[0-9]{1,3}"))return null;int n=Integer.parseInt(channel);if(n>255)return null;rgb=(rgb<<8)|n;}
            if(parts.length==4){String opacity=parts[3].trim();if(!opacity.matches("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)%?"))return null;boolean percent=opacity.endsWith("%");double n=Double.parseDouble(percent?opacity.substring(0,opacity.length()-1):opacity),max=percent?100:1;if(Double.isInfinite(n)||Double.isNaN(n)||n<0||n>max)return null;alpha=(int)Math.round(n*255/max);}
            return (alpha<<24)|rgb;
        }catch(NumberFormatException error){return null;}
    }
    /** HEX-only input; RGB editors reject alpha rather than silently discarding it. */
    public static Integer parseHex(String input, int alpha, boolean alphaEnabled) {
        if (input == null || input.length() > 128) return null;
        String value = input.trim();
        if (!value.matches(alphaEnabled ? "#?(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})"
                : "#?(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})")) return null;
        return parse(value, alphaEnabled ? alpha : 255);
    }
    public static String hex(int color) { return hex(color, true); }
    public static String hex(int color, boolean alphaEnabled) {
        return String.format(Locale.US, alphaEnabled ? "#%08X" : "#%06X", alphaEnabled ? color : color & 0xffffff);
    }
}

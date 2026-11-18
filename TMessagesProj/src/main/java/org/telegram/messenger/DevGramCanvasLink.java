package org.telegram.messenger;

/** Case-preserving canvas coordinates carried by the existing DevGram settings link. */
public final class DevGramCanvasLink {
    public final long profile;public final int x,y;public final String block;
    private DevGramCanvasLink(long profile,String block,int x,int y){this.profile=profile;this.block=block;this.x=x;this.y=y;}
    public static DevGramCanvasLink parse(String value){try{if(value==null||value.length()>110)return null;String[] parts=value.split("__",5);if(parts.length!=5||!parts[0].equalsIgnoreCase("canvas"))return null;long profile=Long.parseLong(parts[1]);int x=Integer.parseInt(parts[2]),y=Integer.parseInt(parts[3]);if(profile<=0||profile>9007199254740991L||Math.abs((long)x)>100000||Math.abs((long)y)>100000||!parts[4].matches("[A-Za-z0-9_-]{1,36}"))return null;return new DevGramCanvasLink(profile,parts[4],x,y);}catch(Exception e){return null;}}
    public static String build(long profile,String block,int x,int y){String value="canvas__"+profile+"__"+x+"__"+y+"__"+block;if(parse(value)==null)throw new IllegalArgumentException("canvas link");return "https://t.me/DevGramSettings?s="+value;}
}

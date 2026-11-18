package org.telegram.ui.Components.devgram.frame;

import java.io.*;
import java.util.*;

/** Animated WebP fragments for the same bounded incremental compositor as APNG. */
public final class FrameWebp {
    private FrameWebp(){}
    private static int le16(byte[] b,int p){return b[p]&255|(b[p+1]&255)<<8;}
    private static int le24(byte[] b,int p){return le16(b,p)|(b[p+2]&255)<<16;}
    private static long le32(byte[] b,int p){return (long)le24(b,p)|(long)(b[p+3]&255)<<24;}
    private static void put(byte[] b,int p,long n,int count){for(int i=0;i<count;i++)b[p+i]=(byte)(n>>>(8*i));}
    private static boolean tag(byte[] b,int p,String s){if(p<0||p>b.length-s.length())return false;for(int i=0;i<s.length();i++)if((b[p+i]&255)!=s.charAt(i))return false;return true;}
    private static byte[] frame(byte[] source,int start,int length,int w,int h,byte[] icc)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] header=new byte[30];System.arraycopy("RIFF".getBytes("US-ASCII"),0,header,0,4);System.arraycopy("WEBPVP8X".getBytes("US-ASCII"),0,header,8,8);put(header,16,10,4);header[20]=(byte)(0x10|(icc==null?0:0x20));put(header,24,w-1,3);put(header,27,h-1,3);out.write(header);if(icc!=null)out.write(icc);out.write(source,start,length);byte[] result=out.toByteArray();put(result,4,result.length-8,4);return result;}
    public static FrameApng.Doc parse(byte[] source)throws IOException{
        FrameImageInfo info=FrameImageInfo.read(source);if(!info.extension.equals("webp")||info.frames<=1)return null;int loops=0,background=0;byte[] icc=null;ArrayList<FrameApng.Frame> frames=new ArrayList<>();long encoded=0;
        for(int p=12;p<=source.length-8;){int n=(int)le32(source,p+4),at=p+8;if(n<0||n>source.length-at)throw new IOException("webp_chunk");if(tag(source,p,"ICCP"))icc=Arrays.copyOfRange(source,p,at+n+(n&1));else if(tag(source,p,"ANIM")){if(n!=6)throw new IOException("webp_animation");background=(int)le32(source,at);loops=le16(source,at+4);}else if(tag(source,p,"ANMF")){if(n<16)throw new IOException("webp_frame");int x=le24(source,at)*2,y=le24(source,at+3)*2,w=le24(source,at+6)+1,h=le24(source,at+9)+1,delay=Math.max(20,le24(source,at+12)),flags=source[at+15]&255;if((flags&~3)!=0||(long)x+w>info.width||(long)y+h>info.height)throw new IOException("webp_bounds");byte[] image=frame(source,at+16,n-16,w,h,icc);encoded+=image.length;if(encoded>16*1024*1024)throw new IOException("webp_size");frames.add(new FrameApng.Frame(image,new int[]{w,h,x,y,delay,flags&1,(flags&2)!=0?0:1}));}p=at+n+(n&1);}
        if(frames.size()!=info.frames)throw new IOException("webp_frames");return new FrameApng.Doc(info.width,info.height,loops,frames,background);
    }
}

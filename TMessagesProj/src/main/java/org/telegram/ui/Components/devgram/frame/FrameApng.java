package org.telegram.ui.Components.devgram.frame;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

/** APNG control and disposal data, with independent PNG fragments for background decoding. */
public final class FrameApng {
    private static final byte[] SIGNATURE={(byte)137,80,78,71,13,10,26,10};
    public static final class Frame {public final byte[] png;public final int x,y,width,height,delay,dispose,blend;Frame(byte[] png,int[] c){this.png=png;width=c[0];height=c[1];x=c[2];y=c[3];delay=c[4];dispose=c[5];blend=c[6];}}
    public static final class Doc {public final int width,height,loops,background;public final List<Frame> frames;Doc(int w,int h,int loops,List<Frame> frames){this(w,h,loops,frames,0);}Doc(int w,int h,int loops,List<Frame> frames,int background){width=w;height=h;this.loops=loops;this.frames=frames;this.background=background;}public long encodedBytes(){long n=0;for(Frame f:frames)n+=f.png.length;return n;}}
    private FrameApng(){}
    private static int u16(byte[] b,int at){return (b[at]&255)<<8|b[at+1]&255;}
    private static long u32(byte[] b,int at){return (long)(b[at]&255)<<24|(long)(b[at+1]&255)<<16|(long)(b[at+2]&255)<<8|b[at+3]&255;}
    private static int integer(byte[] b,int at)throws IOException{long n=u32(b,at);if(n>Integer.MAX_VALUE)throw new IOException("apng_integer");return (int)n;}
    private static void put32(byte[] b,int at,int n){b[at]=(byte)(n>>>24);b[at+1]=(byte)(n>>>16);b[at+2]=(byte)(n>>>8);b[at+3]=(byte)n;}
    private static void chunk(OutputStream output,String type,byte[] data)throws IOException{byte[] length=new byte[4];put32(length,0,data.length);output.write(length);byte[] tag=type.getBytes("US-ASCII");output.write(tag);output.write(data);CRC32 crc=new CRC32();crc.update(tag);crc.update(data);put32(length,0,(int)crc.getValue());output.write(length);}
    private static Frame frame(byte[] ihdr,List<byte[]> carried,List<byte[]> data,int[] control)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(SIGNATURE);byte[] header=ihdr.clone();put32(header,0,control[0]);put32(header,4,control[1]);chunk(out,"IHDR",header);for(byte[] raw:carried)out.write(raw);for(byte[] raw:data)chunk(out,"IDAT",raw);chunk(out,"IEND",new byte[0]);return new Frame(out.toByteArray(),control);}
    public static Doc parse(byte[] bytes)throws IOException{
        if(bytes==null||bytes.length<33||bytes.length>8*1024*1024||!Arrays.equals(SIGNATURE,Arrays.copyOf(bytes,8)))return null;
        byte[] ihdr=null;int width=0,height=0,declared=0,loops=0,sequence=0;boolean animated=false,ended=false;ArrayList<byte[]> carried=new ArrayList<>(),data=new ArrayList<>();ArrayList<Frame> frames=new ArrayList<>();int[] control=null;long encoded=0;
        for(int at=8;at<=bytes.length-12;){int length=integer(bytes,at);if(length>bytes.length-at-12)throw new IOException("apng_chunk");String type=new String(bytes,at+4,4,"US-ASCII");CRC32 crc=new CRC32();crc.update(bytes,at+4,length+4);if(crc.getValue()!=u32(bytes,at+8+length))throw new IOException("apng_crc");int start=at+8;
            if(type.equals("IHDR")){if(ihdr!=null||at!=8||length!=13)throw new IOException("apng_header");ihdr=Arrays.copyOfRange(bytes,start,start+13);width=integer(ihdr,0);height=integer(ihdr,4);if(width<=0||height<=0||width>8192||height>8192||(long)width*height>4194304)throw new IOException("apng_dimensions");}
            else if(type.equals("acTL")){if(animated||length!=8)throw new IOException("apng_control");animated=true;declared=integer(bytes,start);loops=integer(bytes,start+4);if(declared<1||declared>512)throw new IOException("apng_frames");}
            else if(type.equals("fcTL")){if(!animated||length!=26||integer(bytes,start)!=sequence++)throw new IOException("apng_sequence");if(control!=null){if(data.isEmpty())throw new IOException("apng_data");Frame f=frame(ihdr,carried,data,control);frames.add(f);encoded+=f.png.length;data.clear();}
                int w=integer(bytes,start+4),h=integer(bytes,start+8),x=integer(bytes,start+12),y=integer(bytes,start+16),num=u16(bytes,start+20),den=u16(bytes,start+22),dispose=bytes[start+24]&255,blend=bytes[start+25]&255;
                if(w<=0||h<=0||(long)x+w>width||(long)y+h>height||dispose>2||blend>1||frames.size()>=512)throw new IOException("apng_bounds");control=new int[]{w,h,x,y,num==0?100:Math.max(20,Math.round(num*1000f/(den==0?100:den))),dispose,blend};
            }else if(type.equals("IDAT")){if(control!=null)data.add(Arrays.copyOfRange(bytes,start,start+length));}
            else if(type.equals("fdAT")){if(control==null||length<4||integer(bytes,start)!=sequence++)throw new IOException("apng_sequence");data.add(Arrays.copyOfRange(bytes,start+4,start+length));}
            else if(type.equals("IEND")){if(length!=0)throw new IOException("apng_end");ended=true;break;}
            else if(Arrays.asList("PLTE","tRNS","gAMA","cHRM","sRGB","iCCP","sBIT","pHYs").contains(type))carried.add(Arrays.copyOfRange(bytes,at,at+length+12));
            if(encoded>16*1024*1024)throw new IOException("apng_size");at+=length+12;
        }
        if(!animated)return null;if(!ended||control==null||data.isEmpty())throw new IOException("apng_data");Frame last=frame(ihdr,carried,data,control);frames.add(last);encoded+=last.png.length;
        if(frames.size()!=declared||encoded>16*1024*1024||(long)width*height*4*(declared+3L)>64*1024*1024)throw new IOException("apng_budget");return new Doc(width,height,loops,frames);
    }
}

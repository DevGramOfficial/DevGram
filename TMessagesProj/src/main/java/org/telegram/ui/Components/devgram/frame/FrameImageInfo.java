package org.telegram.ui.Components.devgram.frame;

import java.io.IOException;

/** Header-only image limits before a frame archive reaches any Android decoder. */
public final class FrameImageInfo {
    public final String extension;
    public final int width,height,frames;
    public final long decodedBytes;
    private FrameImageInfo(String ext,int w,int h,int n)throws IOException{extension=ext;width=w;height=h;frames=n;if(w<=0||h<=0||w>8192||h>8192||(long)w*h>4194304||n<1||n>512)throw new IOException("frame_dimensions");decodedBytes=(long)w*h*4*(n>1?n+3L:1);if(decodedBytes>64L*1024*1024)throw new IOException("frame_memory");}
    private static boolean tag(byte[] b,int at,String text){if(at<0||at>b.length-text.length())return false;for(int i=0;i<text.length();i++)if((b[at+i]&255)!=text.charAt(i))return false;return true;}
    private static int le16(byte[] b,int p){return b[p]&255|(b[p+1]&255)<<8;}
    private static int be16(byte[] b,int p){return (b[p]&255)<<8|b[p+1]&255;}
    private static int le24(byte[] b,int p){return le16(b,p)|(b[p+2]&255)<<16;}
    private static long u32(byte[] b,int p,boolean little)throws IOException{if(p<0||p>b.length-4)throw new IOException("frame_header");long n=0;for(int i=0;i<4;i++)n=n<<8|(b[p+(little?3-i:i)]&255);return n;}
    private static int dimension(byte[] b,int p,boolean little)throws IOException{long n=u32(b,p,little);if(n>Integer.MAX_VALUE)throw new IOException("frame_dimension");return (int)n;}
    private static int blocks(byte[] b,int p)throws IOException{while(p<b.length){int n=b[p++]&255;if(n==0)return p;if(n>b.length-p)throw new IOException("frame_gif_block");p+=n;}throw new IOException("frame_gif_block");}
    public static FrameImageInfo read(byte[] b)throws IOException{
        if(b==null||b.length<12||b.length>8*1024*1024)throw new IOException("frame_image");FrameImageInfo result;
        if((b[0]&255)==137&&tag(b,1,"PNG\r\n\u001a\n")){if(b.length<33||u32(b,8,false)!=13||!tag(b,12,"IHDR"))throw new IOException("frame_png");FrameApng.Doc apng=FrameApng.parse(b);result=new FrameImageInfo("png",dimension(b,16,false),dimension(b,20,false),apng==null?1:apng.frames.size());}
        else if(tag(b,0,"GIF87a")||tag(b,0,"GIF89a")){if(b.length<13)throw new IOException("frame_gif");int w=le16(b,6),h=le16(b,8),p=13+((b[10]&128)!=0?3*(1<<((b[10]&7)+1)):0),frames=0;boolean end=false;while(p<b.length){int marker=b[p++]&255;if(marker==59){end=true;break;}if(marker==33){if(p>=b.length)throw new IOException("frame_gif");p=blocks(b,p+1);}else if(marker==44){if(p>b.length-9)throw new IOException("frame_gif");int x=le16(b,p),y=le16(b,p+2),fw=le16(b,p+4),fh=le16(b,p+6),flags=b[p+8]&255;if(fw<=0||fh<=0||(long)x+fw>w||(long)y+fh>h)throw new IOException("frame_gif_bounds");p+=9+((flags&128)!=0?3*(1<<((flags&7)+1)):0);if(p>=b.length||++frames>512)throw new IOException("frame_gif");p=blocks(b,p+1);}else throw new IOException("frame_gif");}if(!end)throw new IOException("frame_gif");result=new FrameImageInfo("gif",w,h,frames);}
        else if(tag(b,0,"RIFF")&&tag(b,8,"WEBP")){if(u32(b,4,true)+8>b.length)throw new IOException("frame_webp");int w=0,h=0,frames=0;boolean animated=false;for(int p=12;p<=b.length-8;){long length=u32(b,p+4,true);if(length>b.length-p-8)throw new IOException("frame_webp");int at=p+8;if(tag(b,p,"VP8X")&&length>=10){animated=(b[at]&2)!=0;w=le24(b,at+4)+1;h=le24(b,at+7)+1;}else if(tag(b,p,"ANMF")){if(length<16||w<=0||h<=0||(long)le24(b,at)*2+le24(b,at+6)+1>w||(long)le24(b,at+3)*2+le24(b,at+9)+1>h||++frames>512)throw new IOException("frame_webp_bounds");}else if(w==0&&tag(b,p,"VP8L")&&length>=5&&(b[at]&255)==47){long bits=u32(b,at+1,true);w=(int)(bits&16383)+1;h=(int)((bits>>>14)&16383)+1;}else if(w==0&&tag(b,p,"VP8 ")&&length>=10&&(b[at+3]&255)==157&&b[at+4]==1&&b[at+5]==42){w=le16(b,at+6)&16383;h=le16(b,at+8)&16383;}p+=8+(int)length+((int)length&1);}result=new FrameImageInfo("webp",w,h,animated?frames:1);}
        else if((b[0]&255)==255&&(b[1]&255)==216){int w=0,h=0,p=2;while(p<b.length-3){if((b[p++]&255)!=255)throw new IOException("frame_jpeg");while(p<b.length&&(b[p]&255)==255)p++;if(p>=b.length)break;int marker=b[p++]&255;if(marker==218||marker==217)break;if(marker==1||marker>=208&&marker<=215)continue;if(p>b.length-2)throw new IOException("frame_jpeg");int length=be16(b,p);if(length<2||length>b.length-p)throw new IOException("frame_jpeg");if(marker>=192&&marker<=207&&marker!=196&&marker!=200&&marker!=204&&length>=8){h=be16(b,p+3);w=be16(b,p+5);break;}p+=length;}result=new FrameImageInfo("jpg",w,h,1);}
        else throw new IOException("frame_image");
        if(b.length>(result.extension.equals("png")&&result.frames>1?8*1024*1024:5*1024*1024))throw new IOException("frame_size");return result;
    }
}

package org.telegram.ui.Components.devgram.export;

import android.graphics.Bitmap;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.*;
import java.nio.ByteBuffer;

/** Software-fed native AVC recording; Image plane strides are honored on each codec. */
public final class ProfileVideoEncoder implements Closeable {
    private final int width,height;private MediaCodec codec;private MediaMuxer muxer;private boolean started,finished;private int track=-1,frames;
    private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    public ProfileVideoEncoder(File file,int width,int height,int fps)throws IOException{
        if(width<=0||height<=0||(width&1)!=0||(height&1)!=0||(long)width*height>4_000_000)throw new IllegalArgumentException();this.width=width;this.height=height;
        try{MediaFormat format=MediaFormat.createVideoFormat("video/avc",width,height);format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);format.setInteger(MediaFormat.KEY_BIT_RATE,Math.max(1_000_000,width*height*5));format.setInteger(MediaFormat.KEY_FRAME_RATE,fps);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);codec=MediaCodec.createEncoderByType("video/avc");codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();muxer=new MediaMuxer(file.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);}catch(Exception error){close();throw new IOException(error);}
    }
    public void addFrame(Bitmap bitmap,long micros)throws IOException{if(finished||bitmap.getWidth()!=width||bitmap.getHeight()!=height)throw new IOException("video_frame");try{int index=codec.dequeueInputBuffer(1_000_000);if(index<0){drain(false);index=codec.dequeueInputBuffer(1_000_000);}if(index<0)throw new IOException("video_input_timeout");Image image=codec.getInputImage(index);if(image==null)throw new IOException("video_input_image");try{Image.Plane[] planes=image.getPlanes();if(planes.length<3)throw new IOException("video_planes");int[] row=new int[width];for(int y=0;y<height;y++){bitmap.getPixels(row,0,width,0,y,width,1);for(int x=0;x<width;x++){int color=row[x],r=color>>16&255,g=color>>8&255,b=color&255;put(planes[0],x,y,clamp(((66*r+129*g+25*b+128)>>8)+16));if((x&1)==0&&(y&1)==0){put(planes[1],x/2,y/2,clamp(((-38*r-74*g+112*b+128)>>8)+128));put(planes[2],x/2,y/2,clamp(((112*r-94*g-18*b+128)>>8)+128));}}}}finally{image.close();}codec.queueInputBuffer(index,0,width*height*3/2,micros,0);frames++;drain(false);}catch(Exception error){throw new IOException(error);}}
    private static int clamp(int v){return Math.max(0,Math.min(255,v));}
    private static void put(Image.Plane plane,int x,int y,int value){ByteBuffer buffer=plane.getBuffer();buffer.put(buffer.position()+y*plane.getRowStride()+x*plane.getPixelStride(),(byte)value);}
    private void drain(boolean end)throws IOException{long deadline=System.nanoTime()+10_000_000_000L;while(true){int index=codec.dequeueOutputBuffer(info,end?100_000:0);if(index==MediaCodec.INFO_TRY_AGAIN_LATER){if(!end)return;if(System.nanoTime()>deadline)throw new IOException("video_output_timeout");}else if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(started)throw new IOException("video_format");track=muxer.addTrack(codec.getOutputFormat());muxer.start();started=true;}else if(index>=0){ByteBuffer bytes=codec.getOutputBuffer(index);if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;if(info.size>0){if(!started||bytes==null)throw new IOException("video_muxer");bytes.position(info.offset);bytes.limit(info.offset+info.size);muxer.writeSampleData(track,bytes,info);}boolean eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false);if(eos)return;}}}
    public void finish(long micros)throws IOException{if(finished)return;try{int index=codec.dequeueInputBuffer(1_000_000);if(index<0)throw new IOException("video_eos");codec.queueInputBuffer(index,0,0,micros,MediaCodec.BUFFER_FLAG_END_OF_STREAM);drain(true);if(frames==0||!started)throw new IOException("video_empty");muxer.stop();started=false;finished=true;}catch(Exception error){throw new IOException(error);}finally{close();}}
    public void close(){if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();codec=null;}if(muxer!=null){if(started)try{muxer.stop();}catch(Exception ignored){}muxer.release();muxer=null;}started=false;}
}

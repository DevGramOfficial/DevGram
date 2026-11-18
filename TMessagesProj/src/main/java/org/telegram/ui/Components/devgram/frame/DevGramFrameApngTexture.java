package org.telegram.ui.Components.devgram.frame;

import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import org.telegram.messenger.*;

/** Incremental off-UI APNG compositing, with independent display and worker buffers. */
final class DevGramFrameApngTexture implements DevGramCustomizationMemory.Client {
    static final DispatchQueue QUEUE=new DispatchQueue("devgram-frame-animation");
    private final FrameApng.Doc doc;
    private final View host;
    private final int pool,width,height,sample;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect rect=new Rect();
    private Bitmap canvas,backup,display;
    private Canvas painter;
    private volatile boolean closed;
    private volatile long weight;
    private boolean pending;private volatile boolean done;
    private int index=-1,loops;
    private long nextAt;
    private DevGramFrameApngTexture(FrameApng.Doc doc,View host,int pool,int sample){this.doc=doc;this.host=host;this.pool=pool;this.sample=sample;width=Math.max(1,doc.width/sample);height=Math.max(1,doc.height/sample);weight=doc.encodedBytes()+width*(long)height*16;}
    static DevGramFrameApngTexture open(FrameApng.Doc doc,View host,int pool){Runtime runtime=Runtime.getRuntime();int sample=1;while(sample<8192&&(Math.max(doc.width,doc.height)/sample>256||!DevGramCustomizationMemoryBudget.canExpand(Math.max(1,doc.width/sample),Math.max(1,doc.height/sample),1,runtime.totalMemory()-runtime.freeMemory(),runtime.maxMemory())))sample*=2;DevGramFrameApngTexture texture=new DevGramFrameApngTexture(doc,host,pool,sample);long expected=texture.weight;texture.weight=0;if(!DevGramCustomizationMemory.allow(texture,expected)){DevGramCustomizationMemory.remove(texture);return null;}texture.weight=expected;return texture;}
    @Override public int pool(){return pool;}@Override public long bytes(){return weight;}
    @Override public void trim(boolean critical){close();}
    Bitmap bitmap(boolean motion){if(closed)return null;DevGramCustomizationMemory.touch(this);long now=SystemClock.uptimeMillis();if(!pending&&!done&&(display==null||motion&&now>=nextAt)){pending=true;QUEUE.postRunnable(()->decode());}if(motion&&!done&&host.isShown()&&host.hasWindowFocus())host.postInvalidateDelayed(Math.max(20,nextAt-now));return display;}
    private void decode(){Bitmap output=null;int delay=100;boolean failed=false;try{
        if(closed)return;if(canvas==null){canvas=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);canvas.eraseColor(doc.background);painter=new Canvas(canvas);painter.scale(width/(float)doc.width,height/(float)doc.height);}
        int next=index+1;if(next>=doc.frames.size()){next=0;loops++;if(doc.loops>0&&loops>=doc.loops){done=true;return;}}
        if(index>=0){FrameApng.Frame previous=doc.frames.get(index);if(previous.dispose==1){int save=painter.save();painter.clipRect(previous.x,previous.y,previous.x+previous.width,previous.y+previous.height);painter.drawColor(doc.background,PorterDuff.Mode.SRC);painter.restoreToCount(save);}else if(previous.dispose==2&&backup!=null){paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));painter.drawBitmap(backup,null,new Rect(0,0,doc.width,doc.height),paint);paint.setXfermode(null);backup.recycle();backup=null;}}
        if(next==0&&loops>0)canvas.eraseColor(doc.background);FrameApng.Frame frame=doc.frames.get(next);if(backup!=null){backup.recycle();backup=null;}if(frame.dispose==2)backup=canvas.copy(Bitmap.Config.ARGB_8888,false);
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;Bitmap decoded=BitmapFactory.decodeByteArray(frame.png,0,frame.png.length,options);if(decoded==null)throw new IllegalArgumentException();rect.set(frame.x,frame.y,frame.x+frame.width,frame.y+frame.height);paint.setXfermode(frame.blend==0?new PorterDuffXfermode(PorterDuff.Mode.SRC):null);painter.drawBitmap(decoded,null,rect,paint);paint.setXfermode(null);decoded.recycle();index=next;delay=frame.delay;output=canvas.copy(Bitmap.Config.ARGB_8888,false);
    }catch(Exception|OutOfMemoryError error){done=true;failed=true;}
        finally{Bitmap result=output;int wait=delay;boolean failure=failed;AndroidUtilities.runOnUIThread(()->{pending=false;if(closed){if(result!=null)result.recycle();return;}if(result!=null){Bitmap old=display;display=result;nextAt=SystemClock.uptimeMillis()+wait;if(old!=null)old.recycle();host.invalidate();}else if(failure)close();});}
    }
    boolean closed(){return closed;}
    void close(){if(closed)return;closed=true;DevGramCustomizationMemory.remove(this);if(host.isShown())host.invalidate();Bitmap old=display;display=null;if(old!=null)old.recycle();QUEUE.postRunnable(()->{if(canvas!=null){canvas.recycle();canvas=null;}if(backup!=null){backup.recycle();backup=null;}weight=0;});}
}

package org.telegram.ui.Components.devgram.frame;

import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.Components.DevGramProfilePainter;
import java.util.*;

/** Draws compiled native frame layers against the actual avatar contour. */
public final class DevGramTypedFrameRenderer implements DevGramCustomizationMemory.Client {
    private static final float UNIT=256f/84;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF box=new RectF();
    private final Matrix transform=new Matrix();
    private final DevGramProfilePainter shape=new DevGramProfilePainter(1);
    private final FrameRibbon ribbon=new FrameRibbon();
    private final float[] sample=new float[4],points=new float[512],places=new float[64*5],scratch=new float[4];
    private float[] mesh=new float[FrameStrip.vertsLength(FrameStrip.COLUMNS_MAX,2)];
    private FrameContour contour=FrameContour.EMPTY;
    private String contourKey="";
    private JSONObject previousGraph,contourDocument;
    private int previousContourShape=-1;
    private static final java.util.regex.Pattern ASSET=java.util.regex.Pattern.compile("asset:[a-f0-9]{64}\\.(png|jpg|gif|webp)");
    private FrameSpec spec=FrameSpec.EMPTY;
    private final HashMap<String,ImageReceiver> textures=new HashMap<>();
    private final LinkedHashMap<String,Bitmap> extrasCache=new LinkedHashMap<>(8,.75f,true);
    private final LinkedHashMap<String,Bitmap> glowCache=new LinkedHashMap<>(8,.75f,true);
    private final HashMap<String,DevGramFrameApngTexture> apngs=new HashMap<>();
    private final HashSet<String> apngTried=new HashSet<>();
    private int apngGeneration;
    private Bitmap apng(String name,boolean motion){
        DevGramFrameApngTexture ready=apngs.get(name);if(ready!=null){if(!ready.closed())return ready.bitmap(motion);apngs.remove(name);}
        if(apngTried.contains(name)||host==null)return null;
        java.io.File file=ImageLoader.getHttpFilePath(DevGramProfileTextures.imageUrl(name),name.substring(name.lastIndexOf('.')+1));if(!file.isFile())return null;
        apngTried.add(name);int request=apngGeneration;
        DevGramFrameApngTexture.QUEUE.postRunnable(()->{FrameApng.Doc doc=null;try(java.io.InputStream input=new java.io.FileInputStream(file)){byte[] bytes=FrameArchive.bounded(input,8*1024*1024);FrameImageInfo info=FrameImageInfo.read(bytes);doc=info.extension.equals("webp")?FrameWebp.parse(bytes):FrameApng.parse(bytes);}catch(Exception|OutOfMemoryError ignored){}FrameApng.Doc result=doc;
            AndroidUtilities.runOnUIThread(()->{if(request!=apngGeneration||host==null||!spec.assets().contains(name)||result==null)return;DevGramFrameApngTexture texture=DevGramFrameApngTexture.open(result,host,memoryPool);if(texture!=null){apngs.put(name,texture);host.invalidate();}});
        });return null;
    }
    private View host;
    private ImageReceiver avatar;
    private long profile;
    private int account;
    private Bitmap baked;
    private long bakedKey=Long.MIN_VALUE;
    private long lastGlowTime,lastMemoryTouch;
    private int memoryPool=DevGramCustomizationMemory.CHAT;
    @Override public int pool(){return memoryPool;}
    @Override public long bytes(){long total=baked==null?0:baked.getAllocationByteCount();for(Bitmap b:glowCache.values())if(!b.isRecycled())total+=b.getAllocationByteCount();for(Bitmap b:extrasCache.values())if(!b.isRecycled())total+=b.getAllocationByteCount();return total;}
    private void clearExtras(){for(Bitmap bitmap:extrasCache.values())if(!bitmap.isRecycled())bitmap.recycle();extrasCache.clear();}
    @Override public void trim(boolean critical){clearBaked();clearExtras();for(Bitmap b:glowCache.values())if(!b.isRecycled())b.recycle();glowCache.clear();for(ImageReceiver receiver:textures.values()){receiver.stopAnimation();receiver.setAllowStartAnimation(false);if(critical)receiver.clearImage();}if(critical){for(ImageReceiver receiver:textures.values())receiver.onDetachedFromWindow();textures.clear();}if(host!=null&&host.isShown())host.invalidate();}

    public void sources(View host,ImageReceiver avatar,long profile,int account){this.host=host;this.avatar=avatar;this.profile=profile;this.account=account;memoryPool=DevGramCustomizationMemory.CHAT;android.view.ViewParent parent=host==null?null:host.getParent();
        if(host instanceof org.telegram.ui.Components.DevGramStudioPreview)memoryPool=DevGramCustomizationMemory.PREVIEW;
        for(int i=0;parent!=null&&i<12;i++,parent=parent.getParent())if(parent instanceof org.telegram.ui.Components.DevGramStudioPreview||parent instanceof org.telegram.ui.Components.DevGramQuickSetupPreview){memoryPool=DevGramCustomizationMemory.PREVIEW;break;}
        DevGramCustomizationMemory.touch(this);
    }
    private boolean checked(String block){return DevGramFrameEvents.checked(account,profile,block);}
    private boolean showing(FrameSpec.Layer layer,float seconds){
        if(layer.off||layer.visible<=0||!FrameLive.showing(layer,seconds))return false;
        for(FrameSpec.When when:layer.whens)if(checked(when.block)!=when.on)return false;
        return true;
    }
    private Bitmap source(String name,boolean motion){
        if(FrameRuntime.isBlank(name))return FrameBlanks.bitmap(name);
        if(FrameRuntime.isAvatar(name))return avatar==null?null:avatar.getBitmap();
        if(!ASSET.matcher(name).matches())return null;
        if(name.endsWith(".png")||name.endsWith(".webp")){Bitmap animated=apng(name,motion);if(animated!=null)return animated;}
        ImageReceiver receiver=textures.get(name);
        if(receiver==null){
            receiver=new ImageReceiver(host);receiver.setCurrentAccount(account);receiver.setAllowDecodeSingleFrame(true);receiver.setAllowStartAnimation(motion);receiver.setAutoRepeat(1);receiver.onAttachedToWindow();
            String asset=name.substring(6);receiver.setImage(DevGramProfilePolicy.BASE+"studio/textures/"+asset,"256_256",null,asset.substring(asset.lastIndexOf('.')+1),0);
            textures.put(name,receiver);
        }
        receiver.setAllowStartAnimation(motion);
        if(motion)receiver.startAnimation();else receiver.stopAnimation();
        if(motion&&receiver.getAnimation()!=null)receiver.getAnimation().updateCurrentFrame(SystemClock.uptimeMillis(),false);
        return receiver.getBitmap();
    }
    private void prepare(JSONObject graph,JSONObject document,int avatarShape){
        if(graph!=null&&graph.optJSONArray("catalog")!=null)DevGramNodePackages.ensure();
        if(previousGraph!=graph){
            previousGraph=graph;spec=graph==null?FrameSpec.EMPTY:FrameNodeBuild.spec(FrameNodeDoc.parse(graph.toString()));
            Set<String> assets=spec.assets();Iterator<Map.Entry<String,ImageReceiver>> entries=textures.entrySet().iterator();
            while(entries.hasNext()){Map.Entry<String,ImageReceiver> entry=entries.next();if(!assets.contains(entry.getKey())){entry.getValue().onDetachedFromWindow();entry.getValue().clearImage();entries.remove();}}
            Iterator<Map.Entry<String,DevGramFrameApngTexture>> animated=apngs.entrySet().iterator();while(animated.hasNext()){Map.Entry<String,DevGramFrameApngTexture> e=animated.next();if(!assets.contains(e.getKey())){e.getValue().close();animated.remove();}}
            apngTried.retainAll(assets);
            clearBaked();clearExtras();
        }
        if(contourDocument!=document||previousContourShape!=avatarShape){
            contourDocument=document;previousContourShape=avatarShape;
        String key=avatarShape+":"+document.optInt("avatarRadius",18)+":"+document.optInt("avatarRound")+":"+document.optJSONArray("customShape");
        if(!key.equals(contourKey)){
            contourKey=key;shape.setAvatarDocument(document);Path path=shape.avatarPath(0,0,256,avatarShape);PathMeasure measure=new PathMeasure(path,true);
            for(int i=0;i<256;i++){measure.getPosTan(measure.getLength()*i/256f,scratch,null);points[i*2]=scratch[0];points[i*2+1]=scratch[1];}
            contour=FrameContour.of(points,128,128);ribbon.contour(contour);clearBaked();
        }
        }
    }
    private void clearBaked(){if(baked!=null){baked.recycle();baked=null;}bakedKey=Long.MIN_VALUE;}
    public void draw(Canvas canvas,float left,float top,float size,int avatarShape,JSONObject document,boolean motion){
        JSONObject graph=document.optJSONObject("frameGraph");if(graph==null)return;
        prepare(graph,document,avatarShape);if(spec.isEmpty()||contour.isEmpty())return;
        drawPrepared(canvas,left,top,size,motion);
    }
    /** Fixed examples use the same contour/layer renderer as user frame graphs. */
    public void drawSpec(Canvas canvas,float left,float top,float size,int avatarShape,JSONObject document,FrameSpec example,boolean motion){
        prepare(null,document,avatarShape);spec=example;if(spec.isEmpty()||contour.isEmpty())return;drawPrepared(canvas,left,top,size,motion);
    }
    private void drawPrepared(Canvas canvas,float left,float top,float size,boolean motion){
        long now=SystemClock.uptimeMillis();if(now-lastMemoryTouch>=1000){lastMemoryTouch=now;DevGramCustomizationMemory.touch(this);}
        motion=motion&&!DevGramCustomizationMemory.tight();
        if(DevGramCustomizationMemory.tight()&&bytes()>0)trim(false);

        float seconds=motion?(SystemClock.uptimeMillis()%3600000)/1000f:0;
        boolean staticFrame=true,ready=true,animatedFrame=false;long key=(spec.signature()*31+contourKey.hashCode())*31+DevGramNodePackages.epoch();
        for(FrameSpec.Layer layer:spec.layers()){
            if(!showing(layer,seconds))continue;
            Bitmap texture=source(layer.src,motion);if(texture==null||texture.isRecycled()){ready=false;continue;}
            key=key*31+texture.getGenerationId();
            boolean animated=motion&&((textures.get(layer.src)!=null&&textures.get(layer.src).getAnimation()!=null)||apngs.containsKey(layer.src)||layer.animated||layer.mode==4||layer.spin!=0||!layer.alive.isEmpty()||layer.queue!=null);
            animatedFrame|=animated;if(animated||!layer.bake)staticFrame=false;
        }
        for(FrameSpec.Layer layer:spec.layers())for(FrameSpec.When when:layer.whens)key=key*31+(checked(when.block)?1:0);
        int save=canvas.save();canvas.translate(left,top);canvas.scale(size/256,size/256);
        if(staticFrame&&ready&&(baked!=null||DevGramCustomizationMemory.allow(this,512L*512*4))){
            if(baked==null||key!=bakedKey){
                clearBaked();try{baked=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);}catch(OutOfMemoryError pressure){trim(true);canvas.restoreToCount(save);return;}Canvas bake=new Canvas(baked);bake.translate(128,128);bake.scale(1,1);render(bake,seconds,motion);bakedKey=key;
            }
            paint.setColorFilter(null);paint.setAlpha(255);box.set(-128,-128,384,384);canvas.drawBitmap(baked,null,box,paint);
        }else render(canvas,seconds,motion);
        canvas.restoreToCount(save);
        if(animatedFrame&&motion&&host!=null&&host.isShown()&&host.hasWindowFocus())host.postInvalidateDelayed(33);
    }
    private Bitmap extraTexture(Bitmap source,FrameSpec.Layer layer){
        if(source==null||source.isRecycled()||layer.extras.isEmpty())return source;DevGramNodePackages.ensure();if(!DevGramNodePackages.has(layer.extras)||DevGramCustomizationMemory.tight())return source;
        StringBuilder key=new StringBuilder().append(DevGramNodePackages.epoch()).append(':').append(source.getGenerationId());for(FrameSpec.Effect effect:layer.extras)key.append(':').append(effect.type).append(java.util.Arrays.toString(effect.knobs));String id=key.toString();Bitmap cached=extrasCache.get(id);if(cached!=null&&!cached.isRecycled())return cached;
        if(!DevGramCustomizationMemory.allow(this,source.getAllocationByteCount()*4L))return source;Bitmap result=DevGramNodePackages.process(source,layer.extras);if(result!=source){extrasCache.put(id,result);while(extrasCache.size()>8){String oldest=extrasCache.keySet().iterator().next();Bitmap removed=extrasCache.remove(oldest);if(removed!=result&&!removed.isRecycled())removed.recycle();}}return result;
    }
    private void render(Canvas canvas,float seconds,boolean motion){
        for(FrameSpec.Layer original:spec.layers()){
            if(!showing(original,seconds))continue;FrameSpec.Layer layer=FrameLive.now(original,seconds);Bitmap bitmap=extraTexture(source(layer.src,motion),layer);
            if(bitmap==null||bitmap.isRecycled())continue;
            int save=canvas.save();if(layer.mode!=3)canvas.rotate(layer.orbit,128,128);
            paint.setColorFilter(layer.tint==0?null:new PorterDuffColorFilter(layer.tint,PorterDuff.Mode.SRC_IN));paint.setAlpha(layer.visible*255/100);
            Bitmap glow=layer.glow>0?glow(bitmap,layer.glowRadius,layer.animated):null;
            if(layer.mode==0){
                int columns=FrameStrip.columns(contour.length());
                float half=layer.width*UNIT/2f;
                if(FrameStrip.verts(ribbon,columns,2,layer.offset*UNIT-half,layer.offset*UNIT+half,layer.at+seconds*layer.spin/360f,mesh)){
                    if(glow!=null){paint.setAlpha(Math.min(255,layer.visible*layer.glow/40));canvas.drawBitmapMesh(glow,columns,2,mesh,0,null,0,paint);paint.setAlpha(layer.visible*255/100);}
                    canvas.drawBitmapMesh(bitmap,columns,2,mesh,0,null,0,paint);
                }
            }else if(layer.mode==4){
                int count=FrameParticles.place(layer,seconds,256+layer.field*UNIT*2,128+(layer.x-.5f)*256,128+(layer.y-.5f)*256,layer.spread*UNIT,places);
                for(int i=0;i<count;i++){int at=i*5;stamp(canvas,bitmap,glow,layer,places[at],places[at+1],places[at+2],places[at+3],places[at+4]);}
            }else if(layer.mode==3){
                float angle=layer.orbit+layer.at*360;
                FrameStamps.hung(layer.x,layer.y,angle,layer.offset/84f,scratch);
                stamp(canvas,bitmap,glow,layer,scratch[0]*256,scratch[1]*256,layer.turn+angle,1,1);
            }else{
                int count=layer.mode==2?1:layer.repeat;
                for(int i=0;i<count;i++){
                    float at=layer.at+i/(float)count+seconds*layer.spin/360f;
                    contour.posTan(at,sample);float x=sample[0]+contour.normalX(sample[3])*layer.offset*UNIT,y=sample[1]+contour.normalY(sample[2])*layer.offset*UNIT;
                    stamp(canvas,bitmap,glow,layer,x,y,contour.angle(at)+90+layer.turn,1,1);
                }
            }
            canvas.restoreToCount(save);
        }
        paint.setColorFilter(null);paint.setAlpha(255);
    }
    private void stamp(Canvas canvas,Bitmap bitmap,Bitmap glow,FrameSpec.Layer layer,float x,float y,float degrees,float alpha,float scale){
        float height=layer.width*UNIT*layer.scale/100f*scale,width=height*bitmap.getWidth()/Math.max(1f,bitmap.getHeight());
        if(height<=0||width<=0)return;int save=canvas.save();canvas.translate(x,y);canvas.rotate(degrees);box.set(-width/2,-height/2,width/2,height/2);
        if(glow!=null){paint.setAlpha(Math.max(0,Math.min(255,(int)(layer.visible*layer.glow*alpha/40))));canvas.drawBitmap(glow,null,box,paint);}
        paint.setAlpha(Math.max(0,Math.min(255,(int)(layer.visible*2.55f*alpha))));canvas.drawBitmap(bitmap,null,box,paint);canvas.restoreToCount(save);
    }
    private Bitmap glow(Bitmap bitmap,int radius,boolean animated){
        String key=System.identityHashCode(bitmap)+":"+radius;
        Bitmap existing=glowCache.get(key);long now=SystemClock.uptimeMillis();
        if(existing!=null&&(!animated||now-lastGlowTime<120))return existing;
        int width=Math.min(256,bitmap.getWidth()),height=Math.min(256,bitmap.getHeight());
        if(!DevGramCustomizationMemory.allow(this,(long)width*height*8))return existing;
        Bitmap result;try{result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);new Canvas(result).drawBitmap(bitmap,null,new Rect(0,0,width,height),new Paint(Paint.FILTER_BITMAP_FLAG));}catch(OutOfMemoryError pressure){return existing;}

        Utilities.stackBlurBitmap(result,Math.max(1,Math.min(24,radius/4+1)));lastGlowTime=now;
        glowCache.put(key,result);if(existing!=null)existing.recycle();
        while(glowCache.size()>8){String oldest=glowCache.keySet().iterator().next();Bitmap evicted=glowCache.remove(oldest);evicted.recycle();}
        return result;
    }
    public void release(){clearExtras();apngGeneration++;for(DevGramFrameApngTexture texture:apngs.values())texture.close();apngs.clear();apngTried.clear();DevGramCustomizationMemory.remove(this);for(ImageReceiver receiver:textures.values()){receiver.onDetachedFromWindow();receiver.clearImage();}textures.clear();for(Bitmap bitmap:glowCache.values())bitmap.recycle();glowCache.clear();clearBaked();previousGraph=null;contourDocument=null;spec=FrameSpec.EMPTY;host=null;avatar=null;}
}

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.*;

/** Page background is independent from the profile header and never blurs foreground rows. */
public final class DevGramProfilePageRenderer {
    private final View owner;
    private final int account;
    private final long uid;
    private final String prefix;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ImageReceiver media;
    private final DevGramProfilePainter motion=new DevGramProfilePainter();
    private int message=-1, generation;
    private long gradientSignature = Long.MIN_VALUE, fadeSignature = Long.MIN_VALUE;
    private Shader gradientShader, fadeShader, blendShader, firstGlow, secondGlow;
    private long blendSignature=Long.MIN_VALUE,glowSignature=Long.MIN_VALUE;
    private final android.graphics.Matrix glowMatrix=new android.graphics.Matrix();
    private static final android.graphics.PorterDuffXfermode MASK=new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN);
    private boolean active=true,attached,still;
    public long playbackPosition(){AnimatedFileDrawable animation=media.getAnimation();return animation!=null&&animation.isRunning()?animation.getCurrentProgressMs():-1;}
    public void setStill(boolean value){if(still==value)return;still=value;syncAnimation();owner.invalidate();}
    private Api31 gpu;
    public DevGramProfilePageRenderer(View owner,int account,long uid){this(owner,account,uid,"page");}
    public DevGramProfilePageRenderer(View owner,int account,long uid,String prefix){this.owner=owner;this.account=account;this.uid=uid;this.prefix=prefix;media=new ImageReceiver(owner);media.setLayerNum(512);}
    public boolean ready(JSONObject document){return document.optInt(prefix+"Mode")!=3||DevGramProfileMedia.message(document,uid,prefix)<=0||media.hasNotThumb();}
    public void attach(){if(active&&!attached){attached=true;media.onAttachedToWindow();}}
    public void detach(){generation++;if(attached){attached=false;media.onDetachedFromWindow();}media.clearImage();message=-1;}
    public void setActive(boolean value){if(active==value)return;active=value;if(value)attach();else detach();owner.invalidate();}
    public static boolean enabled(JSONObject doc){return DevGramProfileCustomization.enabled()&&doc.optInt("pageMode")>0;}
    public void draw(Canvas canvas,int width,int height,JSONObject doc){
        if(!active||width<=0||height<=0)return;
        if(!DevGramProfileCustomization.enabled()||doc.optInt(prefix+"Mode")==0){if(message>0){generation++;media.clearImage();message=-1;}return;}
        int mode=doc.optInt(prefix+"Mode");if(!DevGramProfilePolicy.allowed(uid,mode==3?DevGramProfileMedia.feature(doc,prefix):DevGramProfileDocument.pageFeature(mode))){generation++;media.clearImage();message=-1;return;}
        int next=mode==3?Math.max(0,DevGramProfileMedia.message(doc,uid,prefix)):0;
        if(next!=message){int request=++generation;message=next;media.clearImage();if(next>0)DevGramProfileBackground.load(account,next,object->{
            if(!active||request!=generation||message!=next||object==null||!DevGramProfilePolicy.allowed(uid,object.isVideo()||object.isGif()?"profile.media.animated":"profile.media"))return;org.telegram.tgnet.TLRPC.Document file=object.getDocument();
            if(file!=null){org.telegram.tgnet.TLRPC.PhotoSize thumb=FileLoader.getClosestPhotoSizeWithSize(file.thumbs,320);media.setAllowStartAnimation(!still&&motion.motion());media.setAutoRepeat(1);if(still)media.setImage(ImageLocation.getForDocument(thumb,file),"320_320",null,null,0,null,object,1);else media.setImage(ImageLocation.getForDocument(file),ImageLoader.AUTOPLAY_FILTER,ImageLocation.getForDocument(thumb,file),"b",file.size,null,object,1);}
            else if(object.messageOwner.media!=null&&object.messageOwner.media.photo!=null){org.telegram.tgnet.TLRPC.Photo photo=object.messageOwner.media.photo;org.telegram.tgnet.TLRPC.PhotoSize size=FileLoader.getClosestPhotoSizeWithSize(photo.sizes,1280);media.setImage(ImageLocation.getForObject(size,photo),null,null,size==null?0:size.size,null,object,1);}owner.invalidate();
        });}
        if(mode==3)syncAnimation();
        int blur=doc.optInt(prefix.equals("banner")?"blur":"pageBlur");
        if(Build.VERSION.SDK_INT>=31&&canvas.isHardwareAccelerated()&&blur>0){if(gpu==null)gpu=new Api31();gpu.draw(canvas,width,height,blur,scene->paint(scene,width,height,doc));}
        else paint(canvas,width,height,doc);
        if(mode>=4&&!still&&motion.motion()&&owner.isShown()&&owner.hasWindowFocus())owner.postInvalidateDelayed(33);
    }
    private void syncAnimation(){
        boolean allowed=active&&attached&&!still&&motion.motion()&&owner.isShown()&&owner.hasWindowFocus()&&!DevGramCustomizationMemory.tight();
        if(media.getAllowStartAnimation()!=allowed)media.setAllowStartAnimation(allowed);
        AnimatedFileDrawable drawable=media.getAnimation();
        if(drawable!=null){if(allowed&&!drawable.isRunning())media.startAnimation();else if(!allowed&&drawable.isRunning())media.stopAnimation();}
    }
    private void paint(Canvas canvas,int width,int height,JSONObject doc){
        int opacity=doc.optInt(prefix+"Alpha",100)*255/100;
        boolean blend=prefix.equals("banner")&&doc.optInt("bannerBlend")==1;
        int fade=doc.optInt(prefix+"FadeType");
        int save=opacity==255&&!blend&&fade==0?canvas.save():canvas.saveLayerAlpha(0,0,width,height,opacity,Canvas.ALL_SAVE_FLAG);
        int mode=doc.optInt(prefix+"Mode"),first=0xff000000|doc.optInt(prefix+"Color",0x101526),second=0xff000000|doc.optInt(prefix+"Color2",0x5077cf);
        paint.setShader(mode>=2&&mode!=3?gradient(width,height,doc,first,second):null);paint.setColor(first);canvas.drawRect(0,0,width,height,paint);paint.setShader(null);
        if(mode==3&&message>0){media.setImageCoords(0,0,width,height);media.draw(canvas);}
        if(mode>=4){
            long key=(((long)width*31+height)*31+first)*31+second;
            if(firstGlow==null||glowSignature!=key){
                glowSignature=key;
                firstGlow=new android.graphics.RadialGradient(0,0,Math.max(width,height)*.7f,Color.argb(160,Color.red(second),Color.green(second),Color.blue(second)),Color.TRANSPARENT,Shader.TileMode.CLAMP);
                secondGlow=new android.graphics.RadialGradient(0,0,Math.max(width,height)*.6f,Color.argb(130,Color.red(first),Color.green(first),Color.blue(first)),Color.TRANSPARENT,Shader.TileMode.CLAMP);
            }
            float time=!still&&motion.motion()?SystemClock.uptimeMillis()/1000f:0;
            float x=width*(.5f+.22f*(float)Math.sin(time*.25f)),y=height*(.3f+.13f*(float)Math.cos(time*.17f));
            glowMatrix.setTranslate(x,y);firstGlow.setLocalMatrix(glowMatrix);paint.setShader(firstGlow);canvas.drawRect(0,0,width,height,paint);
            if(mode==5){glowMatrix.setTranslate(width-x,height-y);secondGlow.setLocalMatrix(glowMatrix);paint.setShader(secondGlow);canvas.drawRect(0,0,width,height,paint);}
            paint.setShader(null);
        }
        if(doc.optInt(prefix+"Dim")>0){paint.setColor(doc.optInt(prefix+"Dim")*255/100<<24);canvas.drawRect(0,0,width,height,paint);}
        if(fade>0){paint.setColor(Color.WHITE);paint.setShader(fade(width,height,doc,fade));paint.setXfermode(MASK);canvas.drawRect(0,0,width,height,paint);paint.setXfermode(null);paint.setShader(null);}
        if(blend){float band=height*doc.optInt("bannerBlendRadius",16)/100f;paint.setColor(Color.WHITE);long key=((long)width*31+height)*31+doc.optInt("bannerBlendRadius",16);if(blendShader==null||blendSignature!=key){blendSignature=key;blendShader=new android.graphics.LinearGradient(0,height-band,0,height,Color.WHITE,Color.TRANSPARENT,Shader.TileMode.CLAMP);}paint.setShader(blendShader);paint.setXfermode(MASK);canvas.drawRect(0,0,width,height,paint);paint.setXfermode(null);paint.setShader(null);}
        canvas.restoreToCount(save);
    }
    private Shader gradient(int width,int height,JSONObject doc,int first,int second){
        int third=0xff000000|doc.optInt(prefix+"Color3",0x8e74e2),use3=doc.optInt(prefix+"Use3"),type=doc.optInt(prefix+"GradientType");
        int angle=doc.optInt(prefix+"GradientAngle"),spread=doc.optInt(prefix+"GradientSpread",100),cx=doc.optInt(prefix+"GradientCenterX",50),cy=doc.optInt(prefix+"GradientCenterY",50);
        boolean legacy=!doc.has(prefix+"GradientAngle")&&!doc.has(prefix+"GradientType")&&!doc.has(prefix+"GradientSpread")&&!doc.has(prefix+"GradientCenterX")&&!doc.has(prefix+"GradientCenterY");
        long signature=width;signature=signature*31+height;signature=signature*31+first;signature=signature*31+second;signature=signature*31+third;signature=signature*31+use3;signature=signature*31+type;signature=signature*31+angle;signature=signature*31+spread;signature=signature*31+cx;signature=signature*31+cy;signature=signature*31+(legacy?1:0);
        if(gradientShader!=null&&gradientSignature==signature)return gradientShader;
        gradientSignature=signature;
        int[] colors=use3==1?new int[]{first,second,third}:new int[]{first,second};
        if(type==1)gradientShader=new android.graphics.RadialGradient(width*cx/100f,height*cy/100f,DevGramSurfaceGeometry.radius(width,height,spread),colors,null,Shader.TileMode.CLAMP);
        else{
            float[] line=legacy?new float[]{0,0,width,height}:DevGramSurfaceGeometry.line(width,height,cx,cy,angle,spread);
            gradientShader=new android.graphics.LinearGradient(line[0],line[1],line[2],line[3],colors,null,Shader.TileMode.CLAMP);
        }
        return gradientShader;
    }
    private Shader fade(int width,int height,JSONObject doc,int type){
        int angle=doc.optInt(prefix+"FadeAngle"),spread=doc.optInt(prefix+"FadeSpread",100),cx=doc.optInt(prefix+"FadeCenterX",50),cy=doc.optInt(prefix+"FadeCenterY",50);
        long signature=width;signature=signature*31+height;signature=signature*31+type;signature=signature*31+angle;signature=signature*31+spread;signature=signature*31+cx;signature=signature*31+cy;
        if(fadeShader!=null&&fadeSignature==signature)return fadeShader;
        fadeSignature=signature;
        if(type==2)fadeShader=new android.graphics.RadialGradient(width*cx/100f,height*cy/100f,DevGramSurfaceGeometry.radius(width,height,spread)*.5f,new int[]{Color.WHITE,Color.TRANSPARENT},new float[]{0,1},Shader.TileMode.CLAMP);
        else{float[] line=DevGramSurfaceGeometry.fadeLine(width,height,cx,cy,angle,spread);fadeShader=new android.graphics.LinearGradient(line[0],line[1],line[2],line[3],Color.WHITE,Color.TRANSPARENT,Shader.TileMode.CLAMP);}
        return fadeShader;
    }
    private static class Api31 {
        private final android.graphics.RenderNode node=new android.graphics.RenderNode("DevGramPageBackground");
        private int lastBlur=-1;
        void draw(Canvas canvas,int width,int height,int blur,java.util.function.Consumer<Canvas> painter){node.setPosition(0,0,width,height);if(lastBlur!=blur){lastBlur=blur;node.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(AndroidUtilities.dp(blur),AndroidUtilities.dp(blur),Shader.TileMode.CLAMP));}android.graphics.RecordingCanvas recording=node.beginRecording(width,height);try{painter.accept(recording);}finally{node.endRecording();}canvas.drawRenderNode(node);}
    }
}

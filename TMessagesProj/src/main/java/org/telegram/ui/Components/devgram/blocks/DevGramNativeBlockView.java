package org.telegram.ui.Components.devgram.blocks;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.DevGramProfilePainter;
import java.util.*;

/** Native reference components/CSS/BlockScript. Programs have no host, file or network API. */
public final class DevGramNativeBlockView extends View {
    public static final class Diagnostic {
        public final String section;public final int line;public final String detail;
        Diagnostic(String section,int line,String detail){this.section=section;this.line=line;this.detail=detail;}
    }
    private static final class Program {
        BlockComponents components;BlockCss css;BlockScript script;Diagnostic error;boolean animated;
    }
    private static final LruCache<String,Program> CACHE=new LruCache<>(24);
    public static String sample(String section){return section.equals("components")?BlockComponents.SAMPLE:section.equals("css")?BlockCss.SAMPLE:BlockScript.SAMPLE;}
    private static Program compile(JSONObject block){
        String components=block.optString("components"),css=block.optString("css"),script=block.optString("script");String key=components+'\0'+css+'\0'+script;
        Program ready=CACHE.get(key);if(ready!=null)return ready;Program p=new Program();
        try{p.components=BlockComponents.compile(components);}catch(BlockComponents.Error e){p.error=new Diagnostic("components",0,e.getMessage());}
        if(p.error==null)try{p.css=BlockCss.compile(css.trim().isEmpty()&&script.trim().isEmpty()?BlockCss.SAMPLE:css,p.components);}catch(BlockCss.Error e){p.error=new Diagnostic("CSS",e.line,e.getMessage());}
        if(p.error==null)try{p.script=BlockScript.compile(script);}catch(BlockScript.Error e){p.error=new Diagnostic("BlockScript",e.line,e.getMessage());}
        p.animated=p.script!=null&&p.script.animated||css.contains("animation");CACHE.put(key,p);return p;
    }
    public static Diagnostic validate(JSONObject block){try{return compile(block).error;}catch(RuntimeException e){return new Diagnostic("CSS / BlockScript",0,e.getMessage());}}
    private final long uid;
    private final Theme.ResourcesProvider resources;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds=new RectF();
    private final Path path=new Path();
    private final Matrix matrix=new Matrix();
    private final Paint composite=new Paint();
    private final LruCache<String,BlurPass> blurPasses=new LruCache<>(8);
    private final ImageReceiver image=new ImageReceiver(this);
    private final DevGramProfilePainter power=new DevGramProfilePainter();
    private final HashMap<String,BlockTransition.State> transitions=new HashMap<>();
    private final LruCache<String,StaticLayout> textCache=new LruCache<>(48);
    private JSONObject block=new JSONObject();
    private Program program;
    private int generation;
    private long started=SystemClock.uptimeMillis();
    private boolean still,attached,pressed;
    private String displayValue;public void setDisplayValue(String value){displayValue=value;invalidate();}
    private JSONObject media=new JSONObject();private String previousMediaTitle="",previousMediaArtist="",mediaEvent="";private long mediaEventAt,mediaTrackAt,mediaStateAt,mediaRevision;private org.telegram.messenger.DevGramPlaybackState mediaClock;private int mediaGeneration;private String mediaArt="";
    public void setMedia(JSONObject track,long serverTime,long receivedAt){
        JSONObject previous=media;long moment=SystemClock.elapsedRealtime();String oldTitle=previous.optString("title",previous.optString("track")),newTitle=track==null?"":track.optString("title",track.optString("track"));String oldState=previous.optString("state"),newState=track==null?"stopped":track.optString("state",track.optBoolean("playing",true)?"playing":"paused");
        boolean trackChanged=!oldTitle.equals(newTitle)||!previous.optString("artist").equals(track==null?"":track.optString("artist"));
        if(trackChanged){previousMediaTitle=oldTitle;previousMediaArtist=previous.optString("artist");mediaTrackAt=moment;mediaEvent="track";mediaEventAt=moment;mediaRevision++;}
        if(!oldState.equals(newState)){mediaStateAt=moment;mediaEvent=newState.equals("paused")?"pause":newState.equals("playing")?(oldState.equals("paused")?"resume":"play"):"stop";mediaEventAt=moment;mediaRevision++;}
        media=track==null?new JSONObject():org.telegram.messenger.DevGramProfileDocument.copy(track);long updated=media.optLong("updatedMs",media.optLong("updated")*1000);
        mediaClock=new org.telegram.messenger.DevGramPlaybackState(media.optLong("position"),media.optLong("duration"),media.optString("state",media.optBoolean("playing",true)?"playing":"paused").equals("playing"),media.optDouble("speed",1),updated,serverTime,receivedAt);
        String encoded=media.optString("coverData");if(!encoded.equals(mediaArt)){mediaArt=encoded;loadArtwork();}
        invalidate();
    }
    private void loadArtwork(){int request=++mediaGeneration;if(!mediaArt.isEmpty())org.telegram.ui.Components.DevGramMusicCoverView.decode(mediaArt,bitmap->{if(request==mediaGeneration&&bitmap!=null&&allowed()){image.setImageBitmap(bitmap);invalidate();}});}
    private String imageUrl="";
    private final Runnable policy=()->{if(!allowed()){generation++;image.clearImage();imageUrl="";}else if(program==null)set(block);invalidate();};
    public DevGramNativeBlockView(Context context,long uid,Theme.ResourcesProvider resources){super(context);this.uid=uid;this.resources=resources;setWillNotDraw(false);setClickable(true);}
    public void setStill(boolean value){still=value;invalidate();}
    private boolean allowed(){return DevGramProfilePolicy.allowed(uid,"profile.blocks")&&DevGramProfilePolicy.allowed(uid,"profile.blocks.code");}
    public void set(JSONObject value){
        block=DevGramProfileDocument.copy(value);final JSONObject source=block;final int request=++generation;program=null;transitions.clear();textCache.evictAll();started=SystemClock.uptimeMillis();
        if(!allowed()){invalidate();return;}
        Utilities.globalQueue.postRunnable(()->{Program result;try{result=compile(source);}catch(RuntimeException e){result=new Program();result.error=new Diagnostic("CSS / BlockScript",0,e.getMessage());}final Program ready=result;
            AndroidUtilities.runOnUIThread(()->{if(request!=generation||!allowed())return;program=ready;requestLayout();invalidate();});});
    }
    @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);int height=program!=null&&program.css!=null?program.css.heightFor(width/AndroidUtilities.density):block.optInt("height",180);setMeasuredDimension(width,resolveSize(AndroidUtilities.dp(Math.max(64,Math.min(1600,height))),h));}
    private String t(String value){return DevGramLocale.text(value);}
    private int color(int key){return Theme.getColor(key,resources);}
    private float dp(float value){return value*AndroidUtilities.density;}
    private float number(BlockScript.Node n,String field,float fallback){double value=BlockScript.number(n,field,fallback);return Double.isFinite(value)?Math.max(-10000,Math.min(10000,(float)value)):fallback;}
    private int ink(BlockScript.Node n,String field,int fallback,BlockScript.Context context){return BlockScript.color(n,field,fallback,context);}
    private String value(BlockScript.Node n,String field,String fallback,BlockScript.Context context){return BlockScript.text(n,field,fallback,context);}
    private BlockScript.Context context(boolean motion){long now=SystemClock.elapsedRealtime();double position=mediaClock==null?0:mediaClock.positionAt(now)/1000.0,duration=media.optLong("duration")/1000.0;String state=media.optString("state",media.optBoolean("playing",false)?"playing":"stopped");return new BlockScript.Context()
        .number("time",motion?(mediaClock==null?SystemClock.uptimeMillis()-started:SystemClock.uptimeMillis())/1000.0:0).number("phase",motion?(SystemClock.uptimeMillis()-started)%10000/10000.0:0)
        .number("progress",mediaClock==null?-1:mediaClock.progressAt(now)).number("position",position).number("duration",duration).number("remaining",Math.max(0,duration-position)).number("has_track",media.optString("title",media.optString("track")).isEmpty()?0:1).number("event_active",now-mediaEventAt<4000?1:0).number("track_changed",now-mediaTrackAt<4000?1:0).number("state_changed",now-mediaStateAt<4000?1:0).number("event_age",Math.max(0,now-mediaEventAt)/1000.0).number("track_age",Math.max(0,now-mediaTrackAt)/1000.0).number("state_age",Math.max(0,now-mediaStateAt)/1000.0).number("event_revision",mediaRevision).number("play_event",mediaEvent.equals("play")&&now-mediaEventAt<4000?1:0).number("pause_event",mediaEvent.equals("pause")&&now-mediaEventAt<4000?1:0).number("resume_event",mediaEvent.equals("resume")&&now-mediaEventAt<4000?1:0).number("stop_event",mediaEvent.equals("stop")&&now-mediaEventAt<4000?1:0).number("pressed",pressed?1:0).number("hover",pressed?1:0).number("playing",state.equals("playing")?1:0).number("paused",state.equals("paused")?1:0).number("stopped",state.equals("stopped")?1:0)
        .text("label",block.optString("title")).text("value",displayValue!=null?displayValue:media.optString("title",media.optString("track",block.optString("text")))).text("service",org.telegram.messenger.DevGramLocale.text(org.telegram.messenger.DevGramProfileRowSpec.SERVICES[Math.max(0,Math.min(8,block.optInt("service")))])).text("title",media.optString("title",media.optString("track",block.optString("title")))).text("artist",media.optString("artist")).text("album",media.optString("album")).text("trackId",media.optString("trackId")).text("playbackState",state).text("event",mediaEvent).text("previousTitle",previousMediaTitle).text("previousArtist",previousMediaArtist).text("cover","$cover").text("poster","$poster")
        .color("textColor",color(Theme.key_windowBackgroundWhiteBlackText)).color("softColor",color(Theme.key_windowBackgroundWhiteGrayText)).color("serviceColor",color(Theme.key_windowBackgroundWhiteBlueText));}
    @Override protected void onDraw(Canvas canvas){
        if(!allowed()){message(canvas,"Оформление блока недоступно");return;}if(program==null){message(canvas,"Проверка оформления…");return;}if(program.error!=null){message(canvas,"Ошибка оформления блока");return;}
        boolean motion=!still&&power.motion();BlockScript.Context context=context(motion);boolean active=program.animated;
        try{
            if(program.css!=null)active|=draw(canvas,program.css.nodes(getWidth()/AndroidUtilities.density,getHeight()/AndroidUtilities.density,context),context,motion,"css:");
            if(program.script!=null)active|=draw(canvas,program.script.run(context),context,motion,"script:");
        }catch(RuntimeException e){message(canvas,"Ошибка оформления блока");active=false;}
        if(active&&motion&&isShown()&&hasWindowFocus())postInvalidateDelayed(33);else if(mediaClock!=null&&mediaClock.playing&&mediaClock.liveAt(SystemClock.elapsedRealtime())&&motion&&isShown()&&hasWindowFocus())postInvalidateDelayed(100);
    }
    private void message(Canvas canvas,String message){text.setTextSize(dp(13));text.setColor(color(Theme.key_windowBackgroundWhiteGrayText));canvas.drawText(t(message),dp(12),dp(28),text);}
    private boolean draw(Canvas canvas,List<BlockScript.Node> nodes,BlockScript.Context context,boolean motion,String prefix){
        boolean active=false;int outer=canvas.save();int index=0;
        try{for(BlockScript.Node original:nodes){
            if(index>=96)break;String key=prefix+value(original,"transitionid",String.valueOf(index++),context);BlockScript.Node n=original;
            if(motion){BlockTransition.Result transition=BlockTransition.apply(n,context,transitions,key,SystemClock.uptimeMillis());n=transition.node;active|=transition.active;}
            BlockAnimation.Spec animation=BlockAnimation.parse(n.attrs);BlockAnimation.Frame frame=motion?animation.frame(SystemClock.uptimeMillis()-started):BlockAnimation.Frame.STILL;active|=frame.active;
            float x=getWidth()*number(n,"x",0)/1000,y=getHeight()*number(n,"y",0)/1000,w=getWidth()*number(n,"w",0)/1000,h=getHeight()*number(n,"h",0)/1000,radius=dp(Math.max(0,number(n,"radius",0)));
            float alpha=Math.max(0,Math.min(1,number(n,"alpha",100)/100f))*frame.alpha;
            float blur=filter(n,context,alpha);int save=canvas.saveLayer(null,composite);float px=x+w*number(n,"originx",50)/100,py=y+h*number(n,"originy",50)/100;
            canvas.translate(dp(number(n,"translatex",0)+frame.x),dp(number(n,"translatey",0)+frame.y));canvas.rotate(number(n,"rotation",0)+frame.rotation,px,py);canvas.scale(number(n,"scalex",1)*frame.scaleX,number(n,"scaley",1)*frame.scaleY,px,py);
            clip(canvas,n,context,x,y,w,h,radius);paint.reset();paint.setAntiAlias(true);paint.setColor(ink(n,"color",color(Theme.key_windowBackgroundWhiteBlueText),context));
            BlurPass blurred=null;Canvas parent=canvas;
            if(blur>0&&android.os.Build.VERSION.SDK_INT>=31&&canvas.isHardwareAccelerated()){
                blurred=blurPasses.get(key);if(blurred==null){blurred=new BlurPass();blurPasses.put(key,blurred);}canvas=blurred.begin(getWidth(),getHeight(),x,y,w>0?w:getWidth(),h>0?h:getHeight(),dp(blur));
            }
            try{shadows(canvas,n,context,x,y,w,h,radius);effect(canvas,n,context,x,y,w,h,radius,false,motion);
            switch(n.kind){
                case "rect":bounds.set(x,y,x+w,y+h);canvas.drawRoundRect(bounds,radius,radius,paint);break;
                case "gradient":gradient(canvas,n,context,x,y,w,h,radius);break;
                case "text":text(canvas,n,context,x,y,w);break;
                case "pill":{
                    text.setTextSize(dp(number(n,"size",12)));String label=value(n,"value","",context);float padX=dp(number(n,"padx",8)),padY=dp(number(n,"pady",4));if(w<=0)w=text.measureText(label)+2*padX;if(h<=0)h=text.getFontSpacing()+2*padY;
                    paint.setColor(ink(n,"back",0x225077cf,context));bounds.set(x,y,x+w,y+h);canvas.drawRoundRect(bounds,radius>0?radius:h/2,radius>0?radius:h/2,paint);text(canvas,n,context,x+padX,y+padY,w-2*padX);break;
                }
                case "image":image(canvas,n,context,x,y,w,h,radius);break;
                case "progress":{
                    float progress=Math.max(0,Math.min(1,number(n,"value",0)));bounds.set(x,y,x+w,y+h);paint.setColor(ink(n,"back",0x33ffffff,context));canvas.drawRoundRect(bounds,radius,radius,paint);bounds.right=x+w*progress;paint.setColor(ink(n,"color",color(Theme.key_windowBackgroundWhiteBlueText),context));canvas.drawRoundRect(bounds,radius,radius,paint);break;
                }
                case "arrow":{
                    float size=dp(number(n,"size",18))/2;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(number(n,"stroke",2)));path.rewind();path.moveTo(x-size,y-size);path.lineTo(x,y);path.lineTo(x-size,y+size);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);break;
                }
            }
            float stroke=dp(Math.max(0,number(n,"stroke",0)));if(stroke>0&&!n.kind.equals("text")&&!n.kind.equals("arrow")){paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(ink(n,"strokecolor",color(Theme.key_windowBackgroundWhiteBlueText),context));canvas.drawRoundRect(x,y,x+w,y+h,radius,radius,paint);paint.setStyle(Paint.Style.FILL);}
            effect(canvas,n,context,x,y,w,h,radius,true,motion);
            float outline=dp(Math.max(0,number(n,"outlinewidth",0))),offset=dp(number(n,"outlineoffset",0));if(outline>0&&w>0&&h>0){paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(outline);paint.setColor(ink(n,"outlinecolor",0x66ffffff,context));canvas.drawRoundRect(x-offset,y-offset,x+w+offset,y+h+offset,Math.max(0,radius+offset),Math.max(0,radius+offset),paint);paint.setStyle(Paint.Style.FILL);}
            }finally{if(blurred!=null){blurred.end();canvas=parent;blurred.draw(canvas);}canvas.restoreToCount(save);}
            active|=motion&&(value(n,"effect","",context).equals("shine")||value(n,"effect","",context).equals("hologram"));
            if("hidden".equalsIgnoreCase(value(n,"overflow","",context))&&w>0&&h>0){path.rewind();path.addRoundRect(x,y,x+w,y+h,radius,radius,Path.Direction.CW);canvas.clipPath(path);}
        }
        }finally{canvas.restoreToCount(outer);}return active;
    }
    private float filter(BlockScript.Node node,BlockScript.Context ctx,float alpha){
        composite.reset();composite.setAlpha(Math.round(alpha*255));String filters=value(node,"filter","",ctx);if(filters.isEmpty())return 0;ColorMatrix all=new ColorMatrix();float blur=0;
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("([a-z]+)\\(([^)]*)\\)").matcher(value(node,"filter","",ctx).toLowerCase(Locale.US));
        while(matcher.find())try{String raw=matcher.group(2).trim();float v=Float.parseFloat(raw.replace("%","").replace("px","").replace("dp",""));if(raw.endsWith("%"))v/=100;if(matcher.group(1).equals("blur")){blur=Math.max(0,Math.min(64,v));continue;}v=Math.max(0,Math.min(10,v));ColorMatrix next=new ColorMatrix();switch(matcher.group(1)){
            case "blur":blur=Math.min(64,v);continue;
            case "brightness":next.set(new float[]{v,0,0,0,0,0,v,0,0,0,0,0,v,0,0,0,0,0,1,0});break;
            case "contrast":float bias=128*(1-v);next.set(new float[]{v,0,0,0,bias,0,v,0,0,bias,0,0,v,0,bias,0,0,0,1,0});break;
            case "saturate":next.setSaturation(v);break;
            case "grayscale":next.setSaturation(1-Math.min(1,v));break;
            case "opacity":composite.setAlpha(Math.round(composite.getAlpha()*Math.min(1,v)));continue;
            case "invert":v=Math.min(1,v);float k=1-2*v,b=255*v;next.set(new float[]{k,0,0,0,b,0,k,0,0,b,0,0,k,0,b,0,0,0,1,0});break;
            case "sepia":v=Math.min(1,v);next.set(new float[]{1-.607f*v,.769f*v,.189f*v,0,0,.349f*v,1-.314f*v,.168f*v,0,0,.272f*v,.534f*v,1-.869f*v,0,0,0,0,0,1,0});break;
            default:continue;
        }all.postConcat(next);}catch(NumberFormatException ignored){}
        composite.setColorFilter(new ColorMatrixColorFilter(all));return blur;
    }
    private void effect(Canvas c,BlockScript.Node n,BlockScript.Context ctx,float x,float y,float w,float h,float r,boolean over,boolean motion){
        if(w<=0||h<=0)return;String type=value(n,"effect","",ctx);float strength=Math.max(0,Math.min(1,number(n,"effectstrength",55)/100)),size=dp(number(n,"effectsize",14));int color=ink(n,"effectcolor",Color.WHITE,ctx);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
        if(!over&&type.equals("neon")){paint.setStyle(Paint.Style.STROKE);for(int i=7;i>=1;i--){float halo=Math.max(1,size*i/7);paint.setStrokeWidth(halo*.7f);paint.setColor((Math.round(255*strength*(8-i)/49)<<24)|(color&0xffffff));c.drawRoundRect(x-halo/2,y-halo/2,x+w+halo/2,y+h+halo/2,r+halo/2,r+halo/2,paint);}paint.setStyle(Paint.Style.FILL);}
        if(over&&(type.equals("glass")||type.equals("shine")||type.equals("hologram"))){int save=c.save();path.rewind();path.addRoundRect(x,y,x+w,y+h,r,r,Path.Direction.CW);c.clipPath(path);int light=(Math.round(255*strength)<<24)|(color&0xffffff);
            if(type.equals("glass"))paint.setShader(new LinearGradient(x,y,x+w,y+h,new int[]{light,0x00ffffff,light&0x33ffffff},new float[]{0,.56f,1},Shader.TileMode.CLAMP));
            else{float speed=Math.max(.25f,Math.min(20,number(n,"effectspeed",2.4f))),phase=motion?(SystemClock.uptimeMillis()-started)%(long)(speed*1000)/(speed*1000):.5f,at=x-h+phase*(w+h+size*2);int[] colors=type.equals("hologram")?new int[]{0x00ff4d8d,0x66ff4d8d,0x6666ccff,0x6698f0ff,0x0066ccff}:new int[]{0x00ffffff,light,0x00ffffff};paint.setShader(new LinearGradient(at-size,y+h,at+size,y,colors,null,Shader.TileMode.CLAMP));}
            c.drawRect(x,y,x+w,y+h,paint);paint.setShader(null);c.restoreToCount(save);
        }
        paint.setColor(ink(n,"color",color(Theme.key_windowBackgroundWhiteBlueText),ctx));
    }
    private static final class BlurPass {
        final android.graphics.RenderNode node=new android.graphics.RenderNode("DevGramBlockFilter");int left,top;
        Canvas begin(int width,int height,float x,float y,float w,float h,float blur){left=Math.max(0,(int)(x-blur*2));top=Math.max(0,(int)(y-blur*2));int right=Math.max(left+1,Math.min(width,(int)Math.ceil(x+w+blur*2))),bottom=Math.max(top+1,Math.min(height,(int)Math.ceil(y+h+blur*2)));node.setPosition(left,top,right,bottom);node.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(Math.max(.1f,blur),Math.max(.1f,blur),Shader.TileMode.CLAMP));Canvas canvas=node.beginRecording(right-left,bottom-top);canvas.translate(-left,-top);return canvas;}
        void end(){node.endRecording();}
        void draw(Canvas canvas){canvas.drawRenderNode(node);}
    }
    private void clip(Canvas c,BlockScript.Node n,BlockScript.Context ctx,float x,float y,float w,float h,float r){String shape=value(n,"clippath","",ctx).toLowerCase(Locale.US);if(w<=0||h<=0||shape.isEmpty())return;bounds.set(x,y,x+w,y+h);path.rewind();if(shape.startsWith("circle")){float radius=Math.min(w,h)/2;path.addCircle(bounds.centerX(),bounds.centerY(),radius,Path.Direction.CW);}else if(shape.startsWith("ellipse"))path.addOval(bounds,Path.Direction.CW);else if(shape.startsWith("round"))path.addRoundRect(bounds,r,r,Path.Direction.CW);else return;c.clipPath(path);}
    private void gradient(Canvas c,BlockScript.Node n,BlockScript.Context ctx,float x,float y,float w,float h,float r){
        if(w<=0||h<=0)return;int first=ink(n,"from",color(Theme.key_windowBackgroundWhiteBlueText),ctx),last=ink(n,"to",0,ctx);int[] colors=n.attrs.containsKey("mid")?new int[]{first,ink(n,"mid",first,ctx),last}:new int[]{first,last};
        float cx=x+w*number(n,"cx",50)/100,cy=y+h*number(n,"cy",50)/100;String kind=value(n,"gradient","linear",ctx);
        if(kind.equals("radial"))paint.setShader(new RadialGradient(cx,cy,Math.max(1,(float)Math.hypot(Math.max(cx-x,x+w-cx),Math.max(cy-y,y+h-cy))),colors,null,Shader.TileMode.CLAMP));
        else if(kind.equals("sweep")){Shader shader=new SweepGradient(cx,cy,colors,null);matrix.setRotate(number(n,"angle",0),cx,cy);shader.setLocalMatrix(matrix);paint.setShader(shader);}
        else{double angle=Math.toRadians(number(n,"angle",0));float dx=(float)Math.cos(angle)*w/2,dy=(float)Math.sin(angle)*h/2;paint.setShader(new LinearGradient(x+w/2-dx,y+h/2-dy,x+w/2+dx,y+h/2+dy,colors,null,Shader.TileMode.CLAMP));}
        c.drawRoundRect(x,y,x+w,y+h,r,r,paint);paint.setShader(null);
    }
    private void shadows(Canvas c,BlockScript.Node n,BlockScript.Context ctx,float x,float y,float w,float h,float radius){if(w<=0||h<=0)return;for(BlockShadow.Spec spec:BlockShadow.parse(value(n,"shadow","",ctx))){paint.setColor(spec.color);paint.setMaskFilter(spec.blur>0?new BlurMaskFilter(dp(spec.blur),BlurMaskFilter.Blur.NORMAL):null);if(spec.inset){int save=c.save();path.rewind();path.addRoundRect(x,y,x+w,y+h,radius,radius,Path.Direction.CW);c.clipPath(path);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(Math.max(1,spec.spread+spec.blur)));c.drawRoundRect(x+dp(spec.x),y+dp(spec.y),x+w+dp(spec.x),y+h+dp(spec.y),radius,radius,paint);paint.setStyle(Paint.Style.FILL);c.restoreToCount(save);}else c.drawRoundRect(x+dp(spec.x-spec.spread),y+dp(spec.y-spec.spread),x+w+dp(spec.x+spec.spread),y+h+dp(spec.y+spec.spread),radius,radius,paint);paint.setMaskFilter(null);}paint.setColor(ink(n,"color",color(Theme.key_windowBackgroundWhiteBlueText),ctx));}
    private void text(Canvas c,BlockScript.Node n,BlockScript.Context ctx,float x,float y,float width){
        String label=value(n,"value","",ctx);if(number(n,"uppercase",0)!=0)label=label.toUpperCase(Locale.getDefault());if(label.length()>8192)label=label.substring(0,8192);
        text.reset();text.setAntiAlias(true);text.setTextSize(dp(Math.max(4,Math.min(96,number(n,"size",16)))));text.setColor(ink(n,"color",color(Theme.key_windowBackgroundWhiteBlackText),ctx));int weight=(int)number(n,"weight",400);text.setTypeface(Typeface.create(value(n,"fontfamily","sans-serif",ctx),weight>=600?Typeface.BOLD:Typeface.NORMAL));text.setTextSkewX(number(n,"italic",0)!=0?-.2f:0);text.setLetterSpacing(number(n,"letter",0)/100);String decoration=value(n,"textdecoration","",ctx);text.setUnderlineText(decoration.contains("underline"));text.setStrikeThruText(decoration.contains("line-through"));
        List<BlockShadow.Spec> shadow=BlockShadow.parse(value(n,"textshadow","",ctx));if(!shadow.isEmpty()){BlockShadow.Spec s=shadow.get(0);text.setShadowLayer(dp(s.blur),dp(s.x),dp(s.y),s.color);}
        float max=number(n,"maxw",width>0?width*1000/getWidth():1000)*getWidth()/1000;max=Math.max(1,Math.min(getWidth()*4,max));String align=value(n,"align","left",ctx);boolean multi=value(n,"whitespace","",ctx).contains("pre");
        String cacheKey=label+'|'+n.attrs+'|'+text.getColor()+'|'+max;StaticLayout layout=textCache.get(cacheKey);
        if(layout==null){TextPaint brush=new TextPaint(text);layout=StaticLayout.Builder.obtain(label,0,label.length(),brush,Math.max(1,(int)max)).setAlignment(align.equals("center")?android.text.Layout.Alignment.ALIGN_CENTER:align.equals("right")?android.text.Layout.Alignment.ALIGN_OPPOSITE:android.text.Layout.Alignment.ALIGN_NORMAL).setMaxLines(multi?32:1).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).setLineSpacing(Math.max(0,dp(number(n,"lineheight",0))-brush.getFontSpacing()),1).build();textCache.put(cacheKey,layout);}
        int save=c.save();c.translate(x-(align.equals("center")?max/2:align.equals("right")?max:0),y);layout.draw(c);c.restoreToCount(save);
    }
    private void image(Canvas c,BlockScript.Node n,BlockScript.Context ctx,float x,float y,float w,float h,float radius){
        if(!DevGramProfilePolicy.allowed(uid,block.optInt("mediaType")==1?"profile.blocks.media.animated":"profile.blocks.media")){image.clearImage();imageUrl="";return;}
        String source=value(n,"source","$cover",ctx);if(!(source.equals("$cover")||source.equals("$poster")||source.equals("cover")||source.equals("poster")))return;
        String url=media.optString("cover",block.optString("url"));if(mediaArt.isEmpty()&&!url.equals(imageUrl)){imageUrl=url;image.clearImage();if(url.startsWith("https://"))image.setImage(url,"600_400",null,null,0);}
        if(w<=0||h<=0)return;int save=c.save();path.rewind();path.addRoundRect(x,y,x+w,y+h,radius,radius,Path.Direction.CW);c.clipPath(path);image.setRoundRadius(0);image.setImageCoords(x,y,w,h);image.setAllowStartAnimation(block.optInt("mediaType")==1&&!still&&power.motion());image.draw(c);c.restoreToCount(save);
    }
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){pressed=true;invalidate();}else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){pressed=false;invalidate();}return super.onTouchEvent(event);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;image.onAttachedToWindow();loadArtwork();DevGramProfilePolicy.listen(policy);if(program==null)set(block);}
    @Override protected void onDetachedFromWindow(){attached=false;generation++;mediaGeneration++;image.onDetachedFromWindow();image.clearImage();imageUrl="";DevGramProfilePolicy.unlisten(policy);super.onDetachedFromWindow();}
    public void release(){generation++;image.clearImage();imageUrl="";transitions.clear();textCache.evictAll();}
}

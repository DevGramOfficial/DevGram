package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.*;
import org.telegram.ui.Business.ProfileHoursCell;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.utils.glass.GlassSettings;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceRenderNode;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Samples only the painted page background, so surface blur never captures profile text. */
public final class DevGramProfileSurfaces {
    private final WeakHashMap<View,Card> cards=new WeakHashMap<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DevGramProfileVolume volume=new DevGramProfileVolume();
    private final Path clip=new Path();
    private final RectF rect=new RectF();
    private final float[] radii=new float[8];
    private final View root;
    private final long uid;
    private final int[] origin=new int[2],cellOrigin=new int[2];
    private JSONObject document=new JSONObject();
    private java.util.function.Function<View,JSONObject> rowColors;
    public void setRowColorResolver(java.util.function.Function<View,JSONObject> resolver){rowColors=resolver;}
    private JSONObject rowStyle(View view){return view==null||rowColors==null?null:rowColors.apply(view);}
    private boolean hasRowBackground(View view){JSONObject style=rowStyle(view);return style!=null&&style.has("background");}
    private boolean sameBackground(View a,View b){
        JSONObject first=rowStyle(a),second=rowStyle(b);
        boolean x=first!=null&&first.has("background"),y=second!=null&&second.has("background");
        return x==y&&(!x||first.optInt("background")==second.optInt("background"));
    }
    private NativeSources sources;
    public DevGramProfileSurfaces(View root,long uid){this.root=root;this.uid=uid;}
    private float dp(float n){return n*AndroidUtilities.density;}
    public static boolean painted(JSONObject d){return d.optInt("cardEnabled",d.has("cardColor")?1:0)==1;}
    public static boolean buttonsPainted(JSONObject d,long uid){if(!org.telegram.messenger.DevGramProfilePolicy.allowed(uid,"profile.buttons"))return false;return d.optInt("buttonCopyCards",1)==1?painted(d):d.has("buttonCopyCards")||d.has("buttonColor")||d.has("buttonAlpha")||d.has("buttonBlur");}
    public static boolean needsBackground(JSONObject d,long uid){
        boolean cardBlur=d.optInt("cardBlur")>0||d.optInt("cardGlass")==1&&Build.VERSION.SDK_INT>=33;
        boolean card=painted(d)&&cardBlur;
        boolean buttons=buttonsPainted(d,uid)&&(d.optInt("buttonCopyCards",1)==1?cardBlur:d.optInt("buttonBlur")>0);
        return card||buttons;
    }
    private boolean supported(View view){return view instanceof TextDetailCell||view instanceof AboutLinkCell||view instanceof HeaderCell||view instanceof TextCell||view instanceof ProfileHoursCell||view instanceof DevGramProfileBlocksView&&!((DevGramProfileBlocksView)view).transparentRows();}
    /** Native section fills must not sit beneath custom translucent row surfaces. */
    public boolean coversSection(ViewGroup list,RectF bounds){
        boolean found=false;
        for(int i=0;i<list.getChildCount();i++){
            View child=list.getChildAt(i);
            if(child.getVisibility()!=View.VISIBLE||child.getHeight()==0||child.getY()>=bounds.bottom||child.getY()+child.getHeight()<=bounds.top)continue;
            if(!supported(child))return false;
            found=true;
        }
        return found;
    }
    public void update(Canvas canvas,ViewGroup list,JSONObject doc,int width,int height,Consumer<Canvas> background){
        document=doc;
        if(needsBackground(doc,uid)&&Build.VERSION.SDK_INT>=29&&canvas.isHardwareAccelerated()){
            if(sources==null)sources=new NativeSources();sources.capture(width,height,doc,background);
        }
        if(list==null)return;root.getLocationOnScreen(origin);
        for(int i=0;i<list.getChildCount();i++){
            View view=list.getChildAt(i);Card card=cards.get(view);
            boolean ownBackground=hasRowBackground(view);
            if(!(painted(doc)&&supported(view)||ownBackground)){if(card!=null&&view.getBackground()==card)view.setBackground(card.original);continue;}
            if(card==null){card=new Card(view,view.getBackground());cards.put(view,card);}else if(view.getBackground()!=card)card.original=view.getBackground();
            if(view.getBackground()!=card)view.setBackground(card);
            card.bind(doc,rowStyle(view));
            float gap=dp(doc.optInt("cardJoin")==1?12:.5f);
            View previous=i>0?list.getChildAt(i-1):null,next=i+1<list.getChildCount()?list.getChildAt(i+1):null;
            card.joinTop=previous!=null&&(supported(previous)||hasRowBackground(previous))&&sameBackground(view,previous)&&Math.abs(view.getY()-previous.getY()-previous.getHeight())<=gap;
            card.joinBottom=next!=null&&(supported(next)||hasRowBackground(next))&&sameBackground(view,next)&&Math.abs(next.getY()-view.getY()-view.getHeight())<=gap;
        }
    }
    private final class Card extends Drawable {
        private final java.lang.ref.WeakReference<View> owner;
        private Drawable original;
        boolean joinTop,joinBottom;
        BlurredBackgroundDrawable glass;
        long glassKey=Long.MIN_VALUE;
        JSONObject sourceDocument,sourceStyle,surfaceDocument;
        void bind(JSONObject doc,JSONObject style){
            if(doc==sourceDocument&&style==sourceStyle)return;
            sourceDocument=doc;sourceStyle=style;surfaceDocument=doc;
            if(style==null||!style.has("background"))return;
            surfaceDocument=new JSONObject();
            try{
                java.util.Iterator<String> keys=doc.keys();while(keys.hasNext()){String key=keys.next();if(key.startsWith("card"))surfaceDocument.put(key,doc.opt(key));}
                surfaceDocument.put("cardEnabled",1).put("cardColor",style.optInt("background")&0xffffff).put("cardAlpha",100);
            }catch(Exception ignored){}
        }
        Card(View view,Drawable original){owner=new java.lang.ref.WeakReference<>(view);this.original=original;}
        @Override public void draw(Canvas canvas){View view=owner.get();if(view==null)return;JSONObject d=surfaceDocument==null?document:surfaceDocument;if(!painted(d)){if(original!=null){original.setBounds(getBounds());original.draw(canvas);}return;}
            view.getLocationOnScreen(cellOrigin);rect.set(getBounds()); // Section decoration already inset the cell.
            float radius=dp(d.optInt("cardRadiusEnabled")==1?d.optInt("cardRadius",12):12);
            drawSurface(canvas,rect,d,false,radius,joinTop,joinBottom,1,cellOrigin[0]-origin[0],cellOrigin[1]-origin[1],this);
        }
        @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private final java.util.ArrayList<Card> previewCards=new java.util.ArrayList<>();
    public void drawPreviewCard(Canvas canvas,RectF bounds,float radius,int index){if(!painted(document))return;while(previewCards.size()<=index)previewCards.add(new Card(root,null));drawSurface(canvas,bounds,document,false,radius,false,false,1,0,0,previewCards.get(index));}
    private final WeakHashMap<Object,Card> actionCards=new WeakHashMap<>();
    public void drawButtons(Canvas canvas,View owner,RectF bounds,float radius,float alpha){drawButtons(canvas,owner,bounds,radius,alpha,owner);}
    public void drawButtons(Canvas canvas,View owner,RectF bounds,float radius,float alpha,Object identity){
        if(!buttonsPainted(document,uid))return;Card holder=actionCards.get(identity);if(holder==null){holder=new Card(owner,null);actionCards.put(identity,holder);}owner.getLocationOnScreen(cellOrigin);root.getLocationOnScreen(origin);
        drawSurface(canvas,bounds,document,true,radius,false,false,alpha,cellOrigin[0]-origin[0],cellOrigin[1]-origin[1],holder);
    }
    private void drawSurface(Canvas canvas,RectF bounds,JSONObject d,boolean buttons,float radius,boolean joinTop,boolean joinBottom,float alpha,float offsetX,float offsetY,Card holder){
        boolean copy=!buttons||d.optInt("buttonCopyCards",1)==1;
        int rgb=d.optInt(copy?"cardColor":"buttonColor",0x293450),density=d.optInt(copy?"cardAlpha":"buttonAlpha",100);
        int tint=Math.round(density*2.55f)<<24|rgb;
        boolean glass=copy&&d.optInt("cardGlass")==1&&Build.VERSION.SDK_INT>=33;
        int save=canvas.saveLayerAlpha(bounds,Math.round(alpha*255));
        for(int i=0;i<8;i++)radii[i]=(i<4?joinTop:joinBottom)?0:radius;
        clip.rewind();clip.addRoundRect(bounds,radii,Path.Direction.CW);canvas.clipPath(clip);
        if(sources!=null&&canvas.isHardwareAccelerated()&&(glass||d.optInt(copy?"cardBlur":"buttonBlur")>0)){
            long key=tint;for(int n:new int[]{glass?1:0,Math.round(radius),d.optInt("cardGlassBend",40),d.optInt("cardGlassThick",8),d.optInt("cardGlassLight",100),joinTop?1:0,joinBottom?1:0})key=key*31+n;
            if(holder.glass==null||holder.glassKey!=key){
                holder.glassKey=key;final int fill=tint,edge=glass?Math.min(255,Math.round(d.optInt("cardGlassLight",100)*1.2f)):0;
                if(holder.glass==null)holder.glass=(buttons?sources.buttons:sources.cards).createDrawable();
                holder.glass.setPadding(0).setHasPadding(false).setRadius(joinTop?0:radius,joinTop?0:radius,joinBottom?0:radius,joinBottom?0:radius);
                holder.glass.setColorProvider(new BlurredBackgroundColorProvider(){public int getBackgroundColor(){return fill;}public int getShadowColor(){return 0;}public int getStrokeColorTop(){return edge<<24|0xffffff;}public int getStrokeColorBottom(){return edge/3<<24|0xffffff;}});
                holder.glass.setShadowAlpha(0);holder.glass.setThickness(AndroidUtilities.dp(d.optInt("cardGlassThick",8)));holder.glass.setIntensity(d.optInt("cardGlassBend",40)/100f);
                if(glass)holder.glass.setLiquidGlassEffectAllowed();
            }
            holder.glass.setGlassSettings(new GlassSettings(true,glass));holder.glass.setSourceOffset(offsetX,offsetY);holder.glass.setBounds((int)bounds.left,(int)bounds.top,(int)bounds.right,(int)bounds.bottom);holder.glass.draw(canvas);
        }else{paint.setShader(null);paint.setColor(tint);canvas.drawPath(clip,paint);}
        if(copy&&d.optInt("cardVolume")==1&&!glass){
            volume.draw(canvas,clip,bounds,d);
        }
        paint.setShader(null);canvas.restoreToCount(save);
    }
    private static final class NativeSources {
        static GlassSourceColor fallback(){GlassSourceColor source=new GlassSourceColor();source.setColor(0xff293450);return source;}
        final GlassSourceRenderNode cards=new GlassSourceRenderNode(fallback());
        final GlassSourceRenderNode buttons=new GlassSourceRenderNode(fallback());
        void capture(int width,int height,JSONObject d,Consumer<Canvas> background){
            for(int i=0;i<2;i++){
                GlassSourceRenderNode source=i==0?cards:buttons;boolean copy=i==0||d.optInt("buttonCopyCards",1)==1;
                Canvas recording=source.beginRecording(width,height);try{background.accept(recording);}finally{source.endRecording();}
                if(Build.VERSION.SDK_INT>=31)source.setBlur(AndroidUtilities.dp(d.optInt(copy?(d.optInt("cardGlass")==1?"cardGlassBlur":"cardBlur"):"buttonBlur",0)));
                source.invalidateDisplayListForDrawables();
            }
        }
    }
}

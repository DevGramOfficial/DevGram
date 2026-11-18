package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.Toast;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.Theme;
import java.util.*;

/** Shared sparse tiles with a private draft, explicit Send and local undo/redo. */
public final class DevGramPixelCanvasView extends View {
    private final int account;private final long owner,viewer;private final JSONObject row;private final boolean thumbnail;
    private final Theme.ResourcesProvider resources;private final Paint paint=new Paint();private final RectF square=new RectF(),tileRect=new RectF();
    private DevGramRowStateController state;private boolean attached,hand,eraser,picker,grid=true,pinching;
    private int color=0xff000000,span,tool,brush=1,lastX,lastY;private float centerX,centerY,downX,downY;
    private static final LinkedHashMap<String,DevGramPixelDraft> drafts=new LinkedHashMap<>(32,.75f,true);
    private final DevGramPixelDraft draft;
    private DevGramCanvasOverlay guide;private boolean showFocus;
    public DevGramCanvasOverlay overlay(){return guide;}public void overlay(DevGramCanvasOverlay value){guide=value;changed();}
    public void remaining(java.util.function.Consumer<Map<Integer,Integer>> callback){if(guide==null||state==null||!allowed())return;DevGramCanvasOverlay selected=guide;if(!state.hasSnapshot()||selected.x()<left()||selected.y()<top()||selected.x()+selected.pixels.getWidth()>left()+span||selected.y()+selected.pixels.getHeight()>top()+span){callback.accept(java.util.Collections.emptyMap());return;}java.util.function.IntBinaryOperator sent=state.snapshot();HashMap<Long,Integer> pending=new HashMap<>();draft.visit(point->pending.put(((long)point[0]<<32)|(point[1]&0xffffffffL),point[2]));Utilities.globalQueue.postRunnable(()->{int width=selected.pixels.getWidth();int[] pixels=new int[width*selected.pixels.getHeight()];selected.pixels.getPixels(pixels,0,width,0,0,width,selected.pixels.getHeight());Map<Integer,Integer> counts=DevGramCanvasQuantizer.remaining(pixels,width,selected.x(),selected.y(),(x,y)->{Integer value=pending.get(((long)x<<32)|(y&0xffffffffL));return value==null?sent.applyAsInt(x,y):value;});AndroidUtilities.runOnUIThread(()->{if(attached&&guide==selected&&allowed())callback.accept(counts);});});}
    private static final class Tile {final int[] pixels;final Bitmap bitmap;Tile(int[] pixels){this.pixels=pixels;bitmap=Bitmap.createBitmap(pixels,32,32,Bitmap.Config.ARGB_8888);}}
    private final LinkedHashMap<Long,Tile> tiles=new LinkedHashMap<>(32,.75f,true);
    private final ScaleGestureDetector zoom;private java.util.function.IntConsumer picked;private java.util.function.Consumer<JSONObject> inspected;private Runnable viewportChanged,changed;
    private final Runnable tick=new Runnable(){public void run(){if(!attached)return;if(isShown()&&hasWindowFocus()&&state!=null)state.poll();postDelayed(this,4000);}};
    private final Runnable policy=()->{if(!allowed())invalidate();else if(state!=null)state.poll();};
    public DevGramPixelCanvasView(Context context,int account,long owner,JSONObject row,Theme.ResourcesProvider resources,boolean thumbnail){
        super(context);this.account=account;this.owner=owner;viewer=UserConfig.getInstance(account).getClientUserId();this.row=row;this.resources=resources;this.thumbnail=thumbnail;showFocus=!thumbnail&&row.has("focusX")&&row.has("focusY");centerX=row.optInt("viewX");centerY=row.optInt("viewY");span=Math.max(8,Math.min(384,row.optInt("viewSpan",32)));setContentDescription(DevGramLocale.text("Пиксельное полотно"));
        String identity=viewer+":"+owner+":"+row.optString("id");DevGramPixelDraft cached=drafts.get(identity);if(cached==null){cached=new DevGramPixelDraft();drafts.put(identity,cached);while(drafts.size()>64)drafts.remove(drafts.keySet().iterator().next());}draft=cached;
        zoom=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){float scaleSpan;public boolean onScaleBegin(ScaleGestureDetector detector){pinching=true;scaleSpan=span;return true;}public boolean onScale(ScaleGestureDetector detector){scaleSpan=Math.max(8,Math.min(384,scaleSpan/detector.getScaleFactor()));span=Math.round(scaleSpan);viewport();return true;}});
    }
    private void changed(){invalidate();if(changed!=null)changed.run();}
    public void onDraftChanged(Runnable callback){changed=callback;}public int draftSize(){return draft.size();}
    public boolean sending(){return state!=null&&state.busy();}
    public void send(){if(state!=null&&state.canPaint()&&!state.busy()&&allowed()&&draft.size()>0){DevGramPixelDraft pending=draft;state.paintPersistent(draft.points(),pending::acknowledge);}}
    public int ready(){return state==null?0:state.ready();}public boolean canPaint(){return state!=null&&state.canPaint();}public JSONObject player(){return state==null?new JSONObject():state.player();}
    public String status(){
        if(!allowed())return DevGramLocale.text("Рисование недоступно");
        if(state==null)return DevGramLocale.text("Загрузка полотна…");
        if("not_found".equals(state.readError())){
            if(viewer==DevGramCommunityProfiles.owner(owner)&&!DevGramProfileRowSpec.savedCanvas(DevGramProfileDocument.raw(owner),row))return DevGramLocale.text("Сначала сохраните оформление с полотном");
            return DevGramLocale.text("Полотно недоступно или удалено");
        }
        if(state.readError()!=null)return DevGramLocale.text("Не удалось загрузить полотно");
        return DevGramLocale.text(state.hasSnapshot()?"Рисование недоступно":"Загрузка полотна…");
    }
    public void buy(String item){if(state!=null&&allowed())state.buy(item);}public void ban(long uid,boolean value){if(state!=null&&viewer==org.telegram.messenger.DevGramCommunityProfiles.owner(owner)&&allowed())state.ban(uid,value);}
    public void onInspected(java.util.function.Consumer<JSONObject> callback){inspected=callback;}
    public void clearDraft(){if(allowed()){draft.clear();changed();}}
    public void onPicked(java.util.function.IntConsumer callback){picked=callback;}public void onViewport(Runnable callback){viewportChanged=callback;}
    public void color(int value){color=value;eraser=false;picker=false;hand=false;tool=0;invalidate();}public void tool(int value){tool=value;hand=value==1;eraser=value==2;picker=value==3;invalidate();}public int tool(){return tool;}public int color(){return color;}public void brush(int value){brush=Math.max(1,Math.min(8,value));}public void grid(boolean value){grid=value;invalidate();}
    public void resetView(){centerX=row.optInt("viewX");centerY=row.optInt("viewY");span=row.optInt("viewSpan",32);viewport();}
    public int centerX(){return Math.round(centerX);}public int centerY(){return Math.round(centerY);}public int span(){return span;}
    private boolean allowed(){return UserConfig.getInstance(account).isClientActivated()&&UserConfig.getInstance(account).getClientUserId()==viewer&&DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(owner,"profile.blocks")&&DevGramProfilePolicy.allowed(owner,"profile.blocks.pixels")&&(!row.optBoolean("ownOnly")||viewer==org.telegram.messenger.DevGramCommunityProfiles.owner(owner));}
    private int left(){return Math.round(centerX-span/2f);}private int top(){return Math.round(centerY-span/2f);}
    private void viewport(){centerX=Math.max(-99808,Math.min(99808,centerX));centerY=Math.max(-99808,Math.min(99808,centerY));if(state!=null){state.viewport(left(),top(),span);state.poll();}if(viewportChanged!=null)viewportChanged.run();invalidate();}
    private void bounds(){float size=Math.min(getWidth(),getHeight());square.set((getWidth()-size)/2,(getHeight()-size)/2,(getWidth()+size)/2,(getHeight()+size)/2);}
    @Override protected void onDraw(Canvas canvas){
        bounds();canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray,resources));float unit=square.width()/span;if(unit<=0)return;int save=canvas.save();canvas.clipRect(square);paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite,resources));canvas.drawRect(square,paint);
        if(state!=null&&allowed()){
            state.tiles((identity,pixels)->{int chunkX=(int)(identity>>32),chunkY=(int)(long)identity,x=chunkX*32-left(),y=chunkY*32-top();if(x>=span||y>=span||x+32<=0||y+32<=0)return;Tile tile=tiles.get(identity);if(tile==null||tile.pixels!=pixels){tile=new Tile(pixels);tiles.put(identity,tile);}tileRect.set(square.left+x*unit,square.top+y*unit,square.left+(x+32)*unit,square.top+(y+32)*unit);paint.setColor(Color.WHITE);canvas.drawBitmap(tile.bitmap,null,tileRect,paint);});
            while(tiles.size()>256){Long first=tiles.keySet().iterator().next();tiles.remove(first).bitmap.recycle();}
            if(!thumbnail&&guide!=null&&guide.settings.optBoolean("visible",true)){tileRect.set(square.left+(guide.x()-left())*unit,square.top+(guide.y()-top())*unit,square.left+(guide.x()+guide.pixels.getWidth()-left())*unit,square.top+(guide.y()+guide.pixels.getHeight()-top())*unit);paint.setAlpha(guide.settings.optInt("alpha",160));canvas.drawBitmap(guide.pixels,null,tileRect,paint);paint.setAlpha(255);}
            if(!thumbnail)draft.visit(point->{int x=point[0]-left(),y=point[1]-top();if(x<0||y<0||x>=span||y>=span)return;paint.setColor(point[2]==0?Theme.getColor(Theme.key_windowBackgroundWhite,resources):point[2]);canvas.drawRect(square.left+x*unit,square.top+y*unit,square.left+(x+1)*unit+.2f,square.top+(y+1)*unit+.2f,paint);});
        }
        if(grid&&unit>=AndroidUtilities.dp(7)){paint.setColor(Theme.getColor(Theme.key_divider,resources));paint.setStrokeWidth(1);for(int i=0;i<=span;i++){float at=i*unit;canvas.drawLine(square.left+at,square.top,square.left+at,square.bottom,paint);canvas.drawLine(square.left,square.top+at,square.right,square.top+at,paint);}}
        if(showFocus){float fx=square.left+(row.optInt("focusX")-left())*unit,fy=square.top+(row.optInt("focusY")-top())*unit;paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(AndroidUtilities.dp(2));canvas.drawRect(fx,fy,fx+unit,fy+unit,paint);canvas.drawCircle(fx+unit/2,fy+unit/2,Math.max(unit,AndroidUtilities.dp(12)),paint);paint.setStyle(Paint.Style.FILL);}
        canvas.restoreToCount(save);
    }
    private int worldX(float x){return left()+(int)Math.floor((x-square.left)*span/Math.max(1,square.width()));}private int worldY(float y){return top()+(int)Math.floor((y-square.top)*span/Math.max(1,square.height()));}
    private void dot(int x,int y){if(state==null)return;for(int yy=y-brush/2;yy<y+(brush+1)/2;yy++)for(int xx=x-brush/2;xx<x+(brush+1)/2;xx++){if(eraser)draft.erase(xx,yy);else if(state.canPaint())draft.paint(xx,yy,color,state.ready());}changed();}
    private void line(int x,int y){int dx=Math.abs(x-lastX),sx=lastX<x?1:-1,dy=-Math.abs(y-lastY),sy=lastY<y?1:-1,err=dx+dy;for(int n=0;n<1024;n++){dot(lastX,lastY);if(lastX==x&&lastY==y)break;int twice=err*2;if(twice>=dy){err+=dy;lastX+=sx;}if(twice<=dx){err+=dx;lastY+=sy;}}lastX=x;lastY=y;}
    @Override public boolean onTouchEvent(MotionEvent event){if(thumbnail)return false;if(!allowed())return true;bounds();showFocus=false;zoom.onTouchEvent(event);int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);downX=event.getX();downY=event.getY();pinching=false;lastX=worldX(downX);lastY=worldY(downY);if(!hand&&!picker&&tool!=4&&square.contains(downX,downY))dot(lastX,lastY);return true;}
        if(action==MotionEvent.ACTION_POINTER_DOWN){pinching=true;return true;}
        if(action==MotionEvent.ACTION_MOVE){if(zoom.isInProgress()||pinching)return true;if(hand){centerX-=(event.getX()-downX)*span/Math.max(1,square.width());centerY-=(event.getY()-downY)*span/Math.max(1,square.height());downX=event.getX();downY=event.getY();viewport();}else if(!picker&&tool!=4&&square.contains(event.getX(),event.getY()))line(worldX(event.getX()),worldY(event.getY()));return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(action==MotionEvent.ACTION_UP&&!pinching&&state!=null&&square.contains(event.getX(),event.getY())){int wx=worldX(event.getX()),wy=worldY(event.getY());if(tool==4&&inspected!=null)state.cell(wx,wy,inspected);else if(picker){Integer pending=draft.colorAt(wx,wy);int value=pending==null?state.pixel(wx,wy):pending;if(guide!=null&&guide.settings.optBoolean("visible",true)&&guide.colorAt(wx,wy)!=0)value=guide.colorAt(wx,wy);color(value);if(picked!=null)picked.accept(value);}}changed();getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public void history(boolean forward){if(allowed()){draft.history(forward);changed();}}
    public void clear(){if(state!=null&&viewer==org.telegram.messenger.DevGramCommunityProfiles.owner(owner)&&allowed()){draft.clear();state.clear();changed();}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;state=DevGramRowStateApi.create(account,owner,row,this::changed,error->Toast.makeText(getContext(),DevGramLocale.text(error.equals("coins")?"Недостаточно монет":error.equals("charges_full")?"Запас пикселей уже полон":"Не удалось обновить полотно"),Toast.LENGTH_SHORT).show(),!thumbnail);viewport();DevGramProfilePolicy.listen(policy);post(tick);}
    @Override protected void onDetachedFromWindow(){attached=false;removeCallbacks(tick);DevGramProfilePolicy.unlisten(policy);for(Tile tile:tiles.values())tile.bitmap.recycle();tiles.clear();if(state!=null){state.close();state=null;}super.onDetachedFromWindow();}
}

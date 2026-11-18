package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramNodeGraph;
import org.telegram.messenger.DevGramProfilePolicy;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** A world-space canvas: moving nodes never changes their evaluation order. */
public final class DevGramNodeCanvas extends View {
    private final BaseFragment host;
    private final Supplier<JSONObject> document;
    private final Consumer<JSONArray> changed;
    private final IntConsumer edit;
    private final Runnable locked;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wire=new Path();
    private final JSONObject[] frameNodes=new JSONObject[512];
    private final int[] frameIndices=new int[512];
    private final RectF rect=new RectF();
    private final ScaleGestureDetector pinch;
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private float zoom=.85f,panX=18,panY=24,lastX,lastY,downX,downY,dragX,dragY,wireX,wireY;
    private int selected=-1,drag=-1,source=-1;
    private boolean moved,multi;
    private String before;
    private final Runnable longPress=()->{if(drag>=0&&!moved&&!multi){int id=drag;drag=-1;multi=true;context(id);}};
    private static final float WIDTH=160,HEIGHT=86;
    public DevGramNodeCanvas(Context context,BaseFragment host,Supplier<JSONObject> document,Consumer<JSONArray> changed,IntConsumer edit,Runnable locked){
        super(context);this.host=host;this.document=document;this.changed=changed;this.edit=edit;this.locked=locked;
        setContentDescription(t("Студия узлов"));setBackground(DevGramStudioControls.surface(color(Theme.key_windowBackgroundWhite),color(Theme.key_divider)));
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector detector){removeCallbacks(longPress);rollbackDrag();multi=true;drag=-1;source=-1;return true;}
            @Override public boolean onScale(ScaleGestureDetector detector){float fx=detector.getFocusX()/AndroidUtilities.density,fy=detector.getFocusY()/AndroidUtilities.density;float worldX=(fx-panX)/zoom,worldY=(fy-panY)/zoom;zoom=Math.max(.01f,Math.min(2.5f,zoom*detector.getScaleFactor()));panX=fx-worldX*zoom;panY=fy-worldY*zoom;invalidate();return true;}
        });
    }
    private String t(String value){return DevGramLocale.text(value);}
    private int color(int key){return Theme.getColor(key,host.getResourceProvider());}
    private boolean access(){return DevGramProfilePolicy.allowed(host.getUserConfig().getClientUserId(),"avatar.nodes");}
    private JSONArray nodes(){JSONArray value=document.get().optJSONArray("nodes");return value==null?new JSONArray():value;}
    private JSONObject node(int id){JSONArray all=nodes();for(int i=0;i<all.length();i++){JSONObject n=all.optJSONObject(i);if(n!=null&&n.optInt("id")==id)return n;}return null;}
    private int index(int id){JSONArray all=nodes();for(int i=0;i<all.length();i++)if(all.optJSONObject(i).optInt("id")==id)return i;return -1;}
    private float x(JSONObject n,int index){return n.optInt("x",20+index%3*205);}
    private float y(JSONObject n,int index){return n.optInt("y",20+index/3*120);}
    private float height(JSONObject n){return n.optInt("collapsed")==1?36:HEIGHT;}
    private void positions(){JSONArray all=nodes();for(int i=0;i<all.length();i++){JSONObject n=all.optJSONObject(i);try{n.put("x",Math.round(x(n,i))).put("y",Math.round(y(n,i)));}catch(Exception ignored){}}}
    private float worldX(float screen){return (screen/AndroidUtilities.density-panX)/zoom;}
    private float worldY(float screen){return (screen/AndroidUtilities.density-panY)/zoom;}
    private int hit(float x,float y){JSONArray all=nodes();for(int i=all.length()-1;i>=0;i--){JSONObject n=all.optJSONObject(i);rect.set(x(n,i)-12,y(n,i)-8,x(n,i)+WIDTH+12,y(n,i)+height(n)+8);if(rect.contains(x,y))return n.optInt("id");}return -1;}
    private int input(JSONObject n,float x,float y){int i=index(n.optInt("id"));float left=x(n,i),top=y(n,i);if(Math.abs(x-left)>20)return -1;return n.optInt("collapsed")==1?0:Math.abs(y-(top+46))<15?0:Math.abs(y-(top+68))<15?1:-1;}
    private boolean output(JSONObject n,float x,float y){int i=index(n.optInt("id"));return Math.hypot(x-(x(n,i)+WIDTH),y-(y(n,i)+height(n)/2))<20;}
    private void curve(Canvas canvas,float ax,float ay,float bx,float by){float bend=Math.max(38,Math.abs(bx-ax)*.5f);wire.rewind();wire.moveTo(ax,ay);wire.cubicTo(ax+bend,ay,bx-bend,by,bx,by);canvas.drawPath(wire,paint);}
    @Override protected void onDraw(Canvas canvas){
        int save=canvas.save();canvas.scale(AndroidUtilities.density,AndroidUtilities.density);
        if(!access()){paint.setColor(color(Theme.key_windowBackgroundWhiteGrayText));paint.setTextSize(14);canvas.drawText(t("Студия узлов недоступна"),20,40,paint);canvas.restoreToCount(save);return;}
        canvas.translate(panX,panY);canvas.scale(zoom,zoom);float left=-panX/zoom,top=-panY/zoom,right=(getWidth()/AndroidUtilities.density-panX)/zoom,bottom=(getHeight()/AndroidUtilities.density-panY)/zoom;
        paint.setStyle(Paint.Style.FILL);paint.setColor(color(Theme.key_divider));float gridStep=24*(float)Math.pow(2,Math.max(0,Math.ceil(Math.log(12/zoom/24)/Math.log(2))));for(float xx=(float)Math.floor(left/gridStep)*gridStep;xx<right;xx+=gridStep)for(float yy=(float)Math.floor(top/gridStep)*gridStep;yy<bottom;yy+=gridStep)canvas.drawCircle(xx,yy,.9f/zoom,paint);
        JSONArray all=nodes();java.util.Arrays.fill(frameNodes,null);for(int i=0;i<all.length();i++){JSONObject n=all.optJSONObject(i);int id=n.optInt("id",-1);if(id>=0&&id<512){frameNodes[id]=n;frameIndices[id]=i;}}paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(color(Theme.key_windowBackgroundWhiteBlueText));
        for(int i=0;i<all.length();i++){JSONObject n=all.optJSONObject(i);JSONArray inputs=n.optJSONArray("inputs");if(inputs!=null)for(int j=0;j<inputs.length();j++){int sourceId=inputs.optInt(j,-1);JSONObject src=sourceId>=0&&sourceId<512?frameNodes[sourceId]:null;if(src==null)continue;int from=frameIndices[sourceId];curve(canvas,x(src,from)+WIDTH,y(src,from)+height(src)/2,x(n,i),y(n,i)+(n.optInt("collapsed")==1?18:j==0?46:68));}}
        if(source>=0){JSONObject src=node(source);if(src!=null){int from=index(source);curve(canvas,x(src,from)+WIDTH,y(src,from)+height(src)/2,wireX,wireY);}}
        for(int i=0;i<all.length();i++){
            JSONObject n=all.optJSONObject(i);float x=x(n,i),y=y(n,i),h=height(n);if(x>right||x+WIDTH<left||y>bottom||y+h<top)continue;
            paint.setStyle(Paint.Style.FILL);paint.setColor(color(Theme.key_windowBackgroundGray));canvas.drawRoundRect(x,y,x+WIDTH,y+h,12,12,paint);
            if(n.optInt("id")==selected){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(color(Theme.key_windowBackgroundWhiteBlueText));canvas.drawRoundRect(x,y,x+WIDTH,y+h,12,12,paint);paint.setStyle(Paint.Style.FILL);}
            paint.setColor(color(Theme.key_windowBackgroundWhiteBlackText));paint.setTextSize(13);paint.setTypeface(AndroidUtilities.bold());canvas.drawText("#"+n.optInt("id")+" "+title(n.optString("type")),x+12,y+23,paint);paint.setTypeface(null);
            if(h>36){paint.setTextSize(11);paint.setColor(color(Theme.key_windowBackgroundWhiteGrayText));canvas.drawText(t("Значение")+": "+n.optInt("value",100)/100f,x+15,y+48,paint);canvas.drawText(t("Слой")+" "+(n.optInt("layer")+1),x+15,y+70,paint);}
            paint.setColor(color(Theme.key_windowBackgroundWhiteBlueText));canvas.drawCircle(x+WIDTH,y+h/2,5,paint);canvas.drawCircle(x,y+(h==36?18:46),5,paint);if(h>36)canvas.drawCircle(x,y+68,5,paint);
        }
        canvas.restoreToCount(save);
    }
    public static String title(String type){switch(type){case "time":return DevGramLocale.text("Время");case "sine":return DevGramLocale.text("Синус");case "multiply":return DevGramLocale.text("Умножение");case "add":return DevGramLocale.text("Сложение");case "opacity":return DevGramLocale.text("Прозрачность");case "rotation":return DevGramLocale.text("Поворот");case "scale":return DevGramLocale.text("Размер");case "color":return DevGramLocale.text("Цвет");default:return type;}}
    private void rollbackDrag(){if(drag>=0&&moved&&before!=null)try{document.get().put("nodes",new JSONArray(before));}catch(Exception ignored){}}
    private void remember(String old){if(old==null||old.equals(nodes().toString()))return;undo.push(old);while(undo.size()>20)undo.removeLast();redo.clear();}
    private void commit(){remember(before);before=null;changed.accept(nodes());invalidate();}
    public void refreshAccess(){if(!access()){removeCallbacks(longPress);rollbackDrag();drag=source=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}invalidate();}
    public void undo(boolean forward){if(!access()){locked.run();return;}ArrayDeque<String> from=forward?redo:undo,to=forward?undo:redo;if(from.isEmpty())return;try{to.push(nodes().toString());changed.accept(new JSONArray(from.pop()));selected=-1;invalidate();}catch(Exception ignored){}}
    public void fit(){JSONArray all=nodes();if(all.length()==0){zoom=.85f;panX=18;panY=24;}else{float left=Float.MAX_VALUE,top=Float.MAX_VALUE,right=-Float.MAX_VALUE,bottom=-Float.MAX_VALUE;for(int i=0;i<all.length();i++){JSONObject n=all.optJSONObject(i);left=Math.min(left,x(n,i));top=Math.min(top,y(n,i));right=Math.max(right,x(n,i)+WIDTH);bottom=Math.max(bottom,y(n,i)+height(n));}zoom=Math.max(.01f,Math.min(1.2f,Math.min((getWidth()/AndroidUtilities.density-32)/(right-left),(getHeight()/AndroidUtilities.density-32)/(bottom-top))));panX=16-left*zoom;panY=16-top*zoom;}invalidate();}
    private void connect(int from,int to,int slot){
        JSONObject target=node(to);if(target==null||from==to||slot<0)return;
        JSONArray old=target.optJSONArray("inputs");if(slot==1&&(old==null||old.length()==0)){BulletinFactory.of(host).createErrorBulletin(t("Сначала выберите первый вход")).show();return;}
        try{
            positions();JSONArray input=new JSONArray();if(slot==1)input.put(old.optInt(0));input.put(from);if(slot==0&&old!=null&&old.length()>1)input.put(old.optInt(1));target.put("inputs",input);
            JSONArray all=nodes();int[] ids=new int[all.length()];int[][] inputs=new int[all.length()][];
            for(int i=0;i<all.length();i++){JSONObject n=all.optJSONObject(i);ids[i]=n.optInt("id");JSONArray in=n.optJSONArray("inputs");inputs[i]=new int[in==null?0:in.length()];for(int j=0;j<inputs[i].length;j++)inputs[i][j]=in.optInt(j);}
            int[] order=DevGramNodeGraph.order(ids,inputs);if(order==null){if(old==null)target.remove("inputs");else target.put("inputs",old);BulletinFactory.of(host).createErrorBulletin(t("Это соединение создаёт цикл")).show();return;}
            JSONArray sorted=new JSONArray();for(int i:order)sorted.put(all.optJSONObject(i));remember(before);before=null;changed.accept(sorted);invalidate();
        }catch(Exception ignored){}
    }
    private void context(int id){
        if(!access()){locked.run();return;}JSONObject n=node(id);if(n==null)return;
        ItemOptions menu=ItemOptions.makeOptions(host,this);menu.add(org.telegram.messenger.R.drawable.msg_edit,t("Изменить"),()->edit.accept(index(id)));
        menu.add(org.telegram.messenger.R.drawable.msg_copy,t("Дублировать"),()->{if(!access()){locked.run();return;}JSONArray all=nodes();if(all.length()>=512)return;try{before=all.toString();positions();int free=0;while(node(free)!=null)free++;JSONObject copy=new JSONObject(n.toString());copy.put("id",free).put("x",n.optInt("x")+24).put("y",n.optInt("y")+24);all.put(copy);commit();}catch(Exception ignored){}});
        menu.add(org.telegram.messenger.R.drawable.msg_customize,t("Свернуть или развернуть"),()->{if(!access()){locked.run();return;}try{before=nodes().toString();n.put("collapsed",n.optInt("collapsed")==1?0:1);commit();}catch(Exception ignored){}});
        menu.add(org.telegram.messenger.R.drawable.msg_delete,t("Разорвать входы"),()->{if(!access()){locked.run();return;}try{before=nodes().toString();n.put("inputs",new JSONArray());commit();}catch(Exception ignored){}});menu.show();
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!access()){removeCallbacks(longPress);rollbackDrag();drag=source=-1;if(event.getActionMasked()==MotionEvent.ACTION_UP)locked.run();return true;}
        pinch.onTouchEvent(event);float sx=event.getX(),sy=event.getY(),wx=worldX(sx),wy=worldY(sy);
        if(event.getPointerCount()>1){removeCallbacks(longPress);rollbackDrag();drag=source=-1;multi=true;lastX=sx;lastY=sy;return true;}
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:getParent().requestDisallowInterceptTouchEvent(true);downX=lastX=sx;downY=lastY=sy;moved=false;multi=false;before=nodes().toString();selected=hit(wx,wy);JSONObject n=node(selected);source=n!=null&&output(n,wx,wy)?selected:-1;drag=source>=0?-1:selected;wireX=wx;wireY=wy;if(drag>=0){dragX=x(n,index(drag));dragY=y(n,index(drag));postDelayed(longPress,450);}invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(multi)return true;if(Math.hypot(sx-downX,sy-downY)>AndroidUtilities.dp(5)){moved=true;removeCallbacks(longPress);}
                if(source>=0){wireX=wx;wireY=wy;}else if(drag>=0&&moved){JSONObject moving=node(drag);if(moving!=null)try{moving.put("x",Math.max(-10000,Math.min(10000,Math.round(dragX+(sx-downX)/AndroidUtilities.density/zoom)))).put("y",Math.max(-10000,Math.min(10000,Math.round(dragY+(sy-downY)/AndroidUtilities.density/zoom))));}catch(Exception ignored){}}else if(drag<0){panX+=(sx-lastX)/AndroidUtilities.density;panY+=(sy-lastY)/AndroidUtilities.density;}lastX=sx;lastY=sy;invalidate();return true;
            case MotionEvent.ACTION_UP:removeCallbacks(longPress);if(!multi){if(source>=0){int target=hit(wx,wy);JSONObject to=node(target);if(to!=null)connect(source,target,input(to,wx,wy));}else if(drag>=0){if(moved)commit();else edit.accept(index(drag));}}source=drag=-1;getParent().requestDisallowInterceptTouchEvent(false);performClick();invalidate();return true;
            case MotionEvent.ACTION_CANCEL:removeCallbacks(longPress);rollbackDrag();source=drag=-1;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
        }return true;
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(longPress);super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
}

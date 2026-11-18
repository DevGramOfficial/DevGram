package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.devgram.frame.*;
import java.util.*;
import java.util.function.*;

/** Typed sockets and knob inputs, persistent world coordinates and group editing. */
public final class DevGramTypedNodeCanvas extends View {
    private final BaseFragment host;
    private final Supplier<FrameNodeDoc> graph;
    private final Consumer<String> changed;
    private final IntConsumer edit;
    private final Runnable locked;
    private DevGramNodeCursor aiCursor;
    private boolean aiPlaying;
    public void beginAi(){if(aiCursor==null)aiCursor=new DevGramNodeCursor();PointF at=center();aiCursor.appear(at.x,at.y);aiCursor.wear(this,0);aiPlaying=true;invalidate();}
    public void endAi(){aiPlaying=false;if(aiCursor!=null)aiCursor.hide();invalidate();}
    public void aimAi(FrameAiPlan.Step step){if(aiCursor==null)return;FrameNodeDoc.Node node=graph.get().node(step.id);float x=step.x,y=step.y;
        if(node!=null){x=node.x+WIDTH/2;y=node.y+24;if(step.kind.equals("link")){x=node.x+WIDTH;y=outputY(node,step.out);}else if(step.kind.equals("cut")){x=node.x;y=inputY(node,step.pin);}}
        if(node!=null)focus(node.id);else{panX=getWidth()/AndroidUtilities.density/2-x*zoom;panY=getHeight()/AndroidUtilities.density/2-y*zoom;}
        aiCursor.say(t(step.kind.equals("add")?"Добавляю узел":step.kind.equals("move")?"Перемещаю узел":step.kind.equals("knob")?"Настраиваю параметр":step.kind.equals("link")?"Соединяю узлы":step.kind.equals("cut")?"Убираю соединение":step.kind.equals("remove")?"Удаляю узел":"Меняю вид узла"));aiCursor.press(false);aiCursor.moveTo(x,y,420);invalidate();}
    public void finishAiStep(FrameAiPlan.Step step){if(aiCursor==null)return;aiCursor.press(true);if(step.kind.equals("move"))aiCursor.moveTo(step.x+WIDTH/2,step.y+24,450);else if(step.kind.equals("link")){FrameNodeDoc.Node target=graph.get().node(step.other);if(target!=null){aiCursor.dragFrom(aiCursor.x(),aiCursor.y());aiCursor.moveTo(target.x,inputY(target,step.pin),450);}}invalidate();}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wire=new Path();
    private final RectF rect=new RectF(),selectionRect=new RectF();
    private final LinkedHashSet<Integer> selected=new LinkedHashSet<>();
    private final HashMap<Integer,PointF> starting=new HashMap<>();
    private final ScaleGestureDetector pinch;
    private float zoom=.8f,panX=20,panY=20,downX,downY,lastX,lastY,wireX,wireY,selectionX,selectionY;
    private int source,sourceOut,drag;
    private boolean moved,multi,selectMode,selecting;
    private String before;
    private static final float WIDTH=220,ROW=30,HEADER=62;
    private FrameNodeSkin skin=FrameNodeSkin.DARK;
    private int wireStyle,lastSelection=-1;
    private boolean dodge;
    private final float[] obstacles=new float[512*4];
    private int obstacleCount;
    private FrameNodeDoc routedGraph;
    private final HashMap<String,float[]> routes=new HashMap<>();
    public void graphChanged(){routes.clear();invalidate();}
    public void setDodge(boolean value){dodge=value;routes.clear();invalidate();}
    private IntConsumer selectionChanged;
    private boolean fitPending=true;
    public void setSkin(FrameNodeSkin value){skin=value;setBackgroundColor(skin.back);invalidate();}
    public void setWireStyle(int value){wireStyle=Math.max(0,Math.min(2,value));invalidate();}
    public void setSelectionListener(IntConsumer listener){selectionChanged=listener;notifySelection();}
    private void notifySelection(){if(lastSelection!=selected.size()){lastSelection=selected.size();if(selectionChanged!=null)selectionChanged.accept(lastSelection);}}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(fitPending&&w>0&&h>0){fitPending=false;fit();}}
    public void focus(int id){FrameNodeDoc.Node node=graph.get().node(id);if(node==null)return;selected.clear();selected.add(id);zoom=Math.max(zoom,Math.max(.01f,Math.min(1f,Math.min((getWidth()/AndroidUtilities.density-32)/WIDTH,(getHeight()/AndroidUtilities.density-32)/height(node)))));panX=getWidth()/AndroidUtilities.density/2-(node.x+WIDTH/2)*zoom;panY=getHeight()/AndroidUtilities.density/2-(node.y+height(node)/2)*zoom;notifySelection();invalidate();}
    public void tidy(){HashMap<Integer,Integer> depth=new HashMap<>(),rows=new HashMap<>();for(FrameNodeDoc.Node node:graph.get().nodes())depth.put(node.id,0);for(int pass=0;pass<graph.get().count();pass++){boolean changed=false;for(FrameNodeDoc.Wire w:graph.get().wires()){int rank=Math.min(25,depth.getOrDefault(w.from,0)+1);if(rank>depth.getOrDefault(w.to,0)){depth.put(w.to,rank);changed=true;}}if(!changed)break;}for(FrameNodeDoc.Node node:graph.get().nodes()){int rank=depth.get(node.id),row=rows.getOrDefault(rank,0);rows.put(rank,row+1);graph.get().move(node.id,rank*310,Math.min(9500,row*Math.round(height(node)+30)));}invalidate();}
    private final Runnable longPress=()->{if(drag>0&&!moved&&!multi){int id=drag;drag=0;multi=true;context(id);}};
    public DevGramTypedNodeCanvas(Context context,BaseFragment host,Supplier<FrameNodeDoc> graph,Consumer<String> changed,IntConsumer edit,Runnable locked){
        super(context);this.host=host;this.graph=graph;this.changed=changed;this.edit=edit;this.locked=locked;setContentDescription(t("Студия узлов"));
        setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector detector){removeCallbacks(longPress);cancelDrag();restoreCut();multi=true;source=drag=0;return true;}
            @Override public boolean onScale(ScaleGestureDetector detector){float x=detector.getFocusX()/AndroidUtilities.density,y=detector.getFocusY()/AndroidUtilities.density,wx=(x-panX)/zoom,wy=(y-panY)/zoom;zoom=Math.max(.01f,Math.min(3,zoom*detector.getScaleFactor()));panX=x-wx*zoom;panY=y-wy*zoom;invalidate();return true;}
        });
    }
    private String t(String value){return DevGramLocale.text(value);}
    private int color(int key){return Theme.getColor(key,host.getResourceProvider());}
    private boolean access(){return DevGramProfilePolicy.allowed(host.getUserConfig().getClientUserId(),"avatar.nodes");}
    private float wx(float value){return (value/AndroidUtilities.density-panX)/zoom;}
    private float wy(float value){return (value/AndroidUtilities.density-panY)/zoom;}
    private int pinCount(FrameNodeDoc.Node node){return graph.get().pins(node.id);}
    private ArrayList<Integer> inputPins(FrameNodeDoc.Node node){
        ArrayList<Integer> result=new ArrayList<>();for(int i=0;i<pinCount(node);i++)result.add(i);
        FrameNodeType.Kind kind=FrameNodeType.of(node.type);if(node.open)for(int i=0;i<kind.knobs();i++)if(FrameNodeType.pinKind(kind,100+i)>0)result.add(100+i);
        return result;
    }
    private float height(FrameNodeDoc.Node node){return HEADER+Math.max(1,Math.max(inputPins(node).size(),graph.get().outs(node.id)))*ROW+10;}
    private FrameNodeDoc.Node hit(float x,float y){List<FrameNodeDoc.Node> nodes=graph.get().nodes();for(int i=nodes.size()-1;i>=0;i--){FrameNodeDoc.Node node=nodes.get(i);rect.set(node.x-15,node.y-6,node.x+WIDTH+15,node.y+height(node)+6);if(rect.contains(x,y))return node;}return null;}
    private int input(FrameNodeDoc.Node node,float x,float y){if(Math.abs(x-node.x)>18)return -1;int index=Math.round((y-node.y-HEADER-ROW/2)/ROW);ArrayList<Integer> pins=inputPins(node);return index>=0&&index<pins.size()?pins.get(index):-1;}
    private int output(FrameNodeDoc.Node node,float x,float y){if(Math.abs(x-node.x-WIDTH)>18)return -1;int index=Math.round((y-node.y-HEADER-ROW/2)/ROW);return index>=0&&index<graph.get().outs(node.id)?index:-1;}
    private float inputY(FrameNodeDoc.Node node,int pin){int index=inputPins(node).indexOf(pin);return node.y+HEADER+Math.max(0,index)*ROW+ROW/2;}
    private float outputY(FrameNodeDoc.Node node,int out){return node.y+HEADER+out*ROW+ROW/2;}
    private int socketColor(int type){int[] colors={0xff8291a0,0xff5b9cf0,0xff8291a0,0xffddad46,0xffa77de0,0xffe08c67,0xff57b99b,0xffc97fa4,0xffe36e77};return colors[Math.max(0,Math.min(colors.length-1,type))];}
    private void curve(Canvas canvas,float ax,float ay,float bx,float by){
        wire.rewind();wire.moveTo(ax,ay);float[] path=null;
        if(dodge){String key=ax+":"+ay+":"+bx+":"+by;path=routes.get(key);if(path==null){path=FrameNodeRouting.route(ax,ay,bx,by,obstacles,obstacleCount);routes.put(key,path);}}
        int points=path==null?2:path.length/2;float x=ax,y=ay;
        for(int i=1;i<points;i++){float nextX=path==null?bx:path[i*2],nextY=path==null?by:path[i*2+1];
            if(wireStyle==1||dodge&&points>2)wire.lineTo(nextX,nextY);
            else if(wireStyle==2){float middle=(x+nextX)/2;wire.lineTo(middle,y);wire.lineTo(middle,nextY);wire.lineTo(nextX,nextY);}
            else{float bend=Math.max(40,Math.abs(nextX-x)*.5f);wire.cubicTo(x+bend,y,nextX-bend,nextY,nextX,nextY);}x=nextX;y=nextY;
        }canvas.drawPath(wire,paint);
    }
    @Override protected void onDraw(Canvas canvas){
        if(!access()){paint.setColor(color(Theme.key_windowBackgroundWhiteGrayText));paint.setTextSize(AndroidUtilities.dp(14));canvas.drawText(t("Студия узлов недоступна"),AndroidUtilities.dp(20),AndroidUtilities.dp(40),paint);return;}
        int save=canvas.save();canvas.scale(AndroidUtilities.density,AndroidUtilities.density);canvas.translate(panX,panY);canvas.scale(zoom,zoom);
        float left=-panX/zoom,top=-panY/zoom,right=(getWidth()/AndroidUtilities.density-panX)/zoom,bottom=(getHeight()/AndroidUtilities.density-panY)/zoom;
        paint.setStyle(Paint.Style.FILL);paint.setColor(skin.grid);float step=24*(float)Math.pow(2,Math.max(0,Math.ceil(Math.log(12/zoom/24)/Math.log(2))));
        for(float x=(float)Math.floor(left/step)*step;x<right;x+=step)for(float y=(float)Math.floor(top/step)*step;y<bottom;y+=step)canvas.drawCircle(x,y,1/zoom,paint);
        FrameNodeDoc doc=graph.get();if(routedGraph!=doc){routes.clear();routedGraph=doc;}obstacleCount=0;for(FrameNodeDoc.Node node:doc.nodes()){int at=obstacleCount*4;obstacles[at]=node.x-12;obstacles[at+1]=node.y-12;obstacles[at+2]=node.x+WIDTH+12;obstacles[at+3]=node.y+height(node)+12;obstacleCount++;}paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);
        for(FrameNodeDoc.Wire connection:doc.wires()){
            FrameNodeDoc.Node a=doc.node(connection.from),b=doc.node(connection.to);if(a==null||b==null)continue;
            paint.setColor(socketColor(FrameNodeType.outKind(FrameNodeType.of(a.type),connection.out)));curve(canvas,a.x+WIDTH,outputY(a,connection.out),b.x,inputY(b,connection.pin));
        }
        if(source>0){FrameNodeDoc.Node node=doc.node(source);if(node!=null){paint.setColor(socketColor(FrameNodeType.outKind(FrameNodeType.of(node.type),sourceOut)));curve(canvas,node.x+WIDTH,outputY(node,sourceOut),wireX,wireY);}}
        paint.setStyle(Paint.Style.FILL);
        for(FrameNodeDoc.Node node:doc.nodes()){
            float h=height(node);if(node.x>right||node.x+WIDTH<left||node.y>bottom||node.y+h<top)continue;
            FrameNodeType.Kind kind=FrameNodeType.of(node.type);paint.setColor(skin.card);canvas.drawRoundRect(node.x,node.y,node.x+WIDTH,node.y+h,12,12,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setColor(selected.contains(node.id)?FrameNodeSkin.ACCENT:skin.line);paint.setStrokeWidth(selected.contains(node.id)?2:1);canvas.drawRoundRect(node.x,node.y,node.x+WIDTH,node.y+h,12,12,paint);paint.setStyle(Paint.Style.FILL);
            paint.setColor(skin.title);paint.setTextSize(14);paint.setTypeface(AndroidUtilities.bold());canvas.drawText(fitText(t(kind.name),WIDTH-44),node.x+12,node.y+23,paint);paint.setTypeface(null);paint.setColor(skin.note);paint.setTextSize(10);canvas.drawText(fitText(t(kind.hint),WIDTH-24),node.x+12,node.y+43,paint);paint.setColor(skin.dim);canvas.drawText("#"+node.id,node.x+WIDTH-32,node.y+22,paint);
            paint.setTextSize(10);ArrayList<Integer> pins=inputPins(node);
            for(int i=0;i<pins.size();i++){
                int pin=pins.get(i);float y=node.y+HEADER+i*ROW+ROW/2;if(y<top-ROW||y>bottom+ROW)continue;
                paint.setColor(socketColor(FrameNodeType.pinKind(kind,pin)));canvas.drawCircle(node.x,y,5,paint);
                String label=pin>=100?kind.knobs[pin-100].name:FrameNodeType.pinName(kind,pin);paint.setColor(skin.dim);canvas.drawText(fitText(t(label),WIDTH*.5f-24),node.x+12,y+4,paint);
            }
            for(int i=0;i<doc.outs(node.id);i++){
                float y=outputY(node,i);paint.setColor(socketColor(FrameNodeType.outKind(kind,i)));canvas.drawCircle(node.x+WIDTH,y,5,paint);
                String label=i==1&&kind.signal?t("Сигнал"):t("Выход")+(doc.outs(node.id)>1?" "+(i+1):"");paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(label,node.x+WIDTH-12,y+4,paint);paint.setTextAlign(Paint.Align.LEFT);
            }
        }
        if(selecting){selectionRect.set(Math.min(selectionX,wireX),Math.min(selectionY,wireY),Math.max(selectionX,wireX),Math.max(selectionY,wireY));paint.setColor(0x334f8cff);canvas.drawRect(selectionRect,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(color(Theme.key_windowBackgroundWhiteBlueText));canvas.drawRect(selectionRect,paint);paint.setStyle(Paint.Style.FILL);}
        if(aiPlaying&&aiCursor!=null){aiCursor.draw(canvas,zoom);postInvalidateOnAnimation();}
        canvas.restoreToCount(save);
    }
    private String fitText(String value,float width){return android.text.TextUtils.ellipsize(value,new android.text.TextPaint(paint),width,android.text.TextUtils.TruncateAt.END).toString();}
    public void selectionMode(){selectMode=!selectMode;selected.clear();notifySelection();invalidate();}
    public int selectedCount(){return selected.size();}
    public void clearSelection(){selected.clear();notifySelection();invalidate();}
    public void fit(){if(getWidth()==0||getHeight()==0){fitPending=true;return;}List<FrameNodeDoc.Node> nodes=graph.get().nodes();if(nodes.isEmpty()){zoom=.8f;panX=20;panY=20;}else{float x1=Float.MAX_VALUE,y1=Float.MAX_VALUE,x2=-Float.MAX_VALUE,y2=-Float.MAX_VALUE;for(FrameNodeDoc.Node node:nodes){x1=Math.min(x1,node.x);y1=Math.min(y1,node.y);x2=Math.max(x2,node.x+WIDTH);y2=Math.max(y2,node.y+height(node));}zoom=Math.max(.01f,Math.min(1.2f,Math.min((getWidth()/AndroidUtilities.density-40)/(x2-x1),(getHeight()/AndroidUtilities.density-40)/(y2-y1))));panX=20-x1*zoom;panY=20-y1*zoom;}invalidate();}
    public PointF center(){return new PointF(wx(getWidth()/2f),wy(getHeight()/2f));}
    public void duplicate(){if(!access()){locked.run();return;}String old=graph.get().encode();List<Integer> copies=graph.get().duplicate(new ArrayList<>(selected));selected.clear();selected.addAll(copies);changed.accept(old);notifySelection();invalidate();}
    public void delete(){if(!access()){locked.run();return;}String old=graph.get().encode();for(int id:selected)graph.get().drop(id);selected.clear();changed.accept(old);notifySelection();invalidate();}
    private void context(int id){
        FrameNodeDoc.Node node=graph.get().node(id);if(node==null)return;
        ItemOptions menu=ItemOptions.makeOptions(host,this);menu.add(R.drawable.msg_edit,t("Изменить"),()->edit.accept(id));
        menu.add(R.drawable.msg_copy,t("Дублировать"),this::duplicate);
        menu.add(R.drawable.msg_customize,t("Свернуть или развернуть"),()->{if(!access()){locked.run();return;}String old=graph.get().encode();graph.get().toggle(id);changed.accept(old);notifySelection();invalidate();});
        menu.add(R.drawable.msg_delete,t("Удалить"),this::delete);menu.show();
    }
    private void cancelDrag(){if(drag>0&&moved)for(Map.Entry<Integer,PointF> entry:starting.entrySet())graph.get().move(entry.getKey(),(int)entry.getValue().x,(int)entry.getValue().y);starting.clear();}
    public void refreshAccess(){if(!access()){removeCallbacks(longPress);cancelDrag();drag=source=0;selecting=false;}invalidate();}
    @Override protected void onDetachedFromWindow(){endAi();removeCallbacks(longPress);cancelDrag();super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(aiPlaying)return true;
        if(!access()){if(event.getActionMasked()==MotionEvent.ACTION_UP)locked.run();return true;}
        pinch.onTouchEvent(event);float x=event.getX(),y=event.getY(),wx=wx(x),wy=wy(y);
        if(event.getPointerCount()>1){removeCallbacks(longPress);cancelDrag();restoreCut();source=drag=0;selecting=false;multi=true;return true;}
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);downX=lastX=x;downY=lastY=y;moved=multi=false;before=graph.get().encode();FrameNodeDoc.Node node=hit(wx,wy);
                if(node==null){drag=source=0;selected.clear();selecting=selectMode;selectionX=wireX=wx;selectionY=wireY=wy;}
                else{
                    int out=output(node,wx,wy),pin=input(node,wx,wy);
                    if(out>=0){source=node.id;sourceOut=out;drag=0;wireX=wx;wireY=wy;}
                    else if(pin>=0&&graph.get().input(node.id,pin)>0){source=graph.get().input(node.id,pin);sourceOut=graph.get().inputOut(node.id,pin);drag=0;wireX=wx;wireY=wy;graph.get().cut(node.id,pin);}
                    else{if(!selected.contains(node.id)){if(!selectMode)selected.clear();selected.add(node.id);}drag=node.id;source=0;starting.clear();for(int id:selected){FrameNodeDoc.Node item=graph.get().node(id);if(item!=null)starting.put(id,new PointF(item.x,item.y));}postDelayed(longPress,450);}
                }
                notifySelection();invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(multi)return true;if(Math.hypot(x-downX,y-downY)>AndroidUtilities.dp(5)){moved=true;removeCallbacks(longPress);}
                if(selecting||source>0){wireX=wx;wireY=wy;}
                else if(drag>0&&moved){for(Map.Entry<Integer,PointF> entry:starting.entrySet()){PointF at=entry.getValue();graph.get().move(entry.getKey(),Math.max(-10000,Math.min(10000,Math.round(at.x+(x-downX)/AndroidUtilities.density/zoom))),Math.max(-10000,Math.min(10000,Math.round(at.y+(y-downY)/AndroidUtilities.density/zoom))));}}
                else if(drag==0){panX+=(x-lastX)/AndroidUtilities.density;panY+=(y-lastY)/AndroidUtilities.density;}
                if((drag>0&&moved)||source>0)routes.clear();lastX=x;lastY=y;invalidate();return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if(!multi){
                    if(selecting){for(FrameNodeDoc.Node item:graph.get().nodes())if(selectionRect.intersects(item.x,item.y,item.x+WIDTH,item.y+height(item)))selected.add(item.id);}
                    else if(source>0){FrameNodeDoc.Node target=hit(wx,wy);int pin=target==null?-1:input(target,wx,wy);if(target!=null&&pin>=0){if(graph.get().link(source,sourceOut,target.id,pin))changed.accept(before);else{restoreCut();BulletinFactory.of(host).createErrorBulletin(t("Несовместимые входы или замкнутый цикл")).show();}}else changed.accept(before);}
                    else if(drag>0){if(moved)changed.accept(before);else edit.accept(drag);}
                }
                source=drag=0;selecting=false;starting.clear();getParent().requestDisallowInterceptTouchEvent(false);performClick();notifySelection();invalidate();return true;
            case MotionEvent.ACTION_CANCEL:removeCallbacks(longPress);cancelDrag();restoreCut();source=drag=0;selecting=false;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
        }
        return true;
    }
    private void restoreCut(){if(before==null)return;FrameNodeDoc old=FrameNodeDoc.parse(before);for(FrameNodeDoc.Wire w:old.wires())if(w.from==source)graph.get().link(w.from,w.out,w.to,w.pin);}
}

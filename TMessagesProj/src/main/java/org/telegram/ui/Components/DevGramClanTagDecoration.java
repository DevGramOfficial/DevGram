package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.DevGramClanTagsActivity;

/** One badge per recycled name: cached layout, lifecycle and a bounded tap target. */
public final class DevGramClanTagDecoration implements DevGramClanDirectory.Listener {
    private final View view;
    private final Runnable rebuild;
    private final java.util.function.IntSupplier foreground;
    private final RectF hit=new RectF();
    private long peer;
    private int account;
    private JSONObject tag;
    private DevGramClanTagDrawable drawable;
    private boolean attached,pressed,editing,placeholder;
    private float downX,downY;
    private int renderedAccount=-1;
    private long renderedPeer=Long.MIN_VALUE;
    private boolean renderedPlaceholder;
    public DevGramClanTagDecoration(View view,Runnable rebuild){this(view,rebuild,null);}
    public DevGramClanTagDecoration(View view,Runnable rebuild,java.util.function.IntSupplier foreground){this.view=view;this.rebuild=rebuild;this.foreground=foreground;}
    public void bind(int account,long peer){boolean changed=this.account!=account||this.peer!=peer;this.account=account;this.peer=peer;editing=false;if(changed)hit.setEmpty();refresh();}
    public void bindSimple(int account,long peer,boolean editing){this.account=account;this.peer=peer;this.editing=editing;refresh();}
    private boolean refresh(){
        if(peer!=0)DevGramClanDirectory.observe(peer,this);
        JSONObject next=DevGramClanDirectory.peek(peer);
        boolean empty=DevGramProfileCustomization.enabled()&&editing&&peer==UserConfig.getInstance(account).getClientUserId()&&DevGramProfilePolicy.allowed(peer,"workshop.clans");
        if(renderedAccount==account&&renderedPeer==peer&&tag==next&&renderedPlaceholder==empty)return false;
        renderedAccount=account;renderedPeer=peer;renderedPlaceholder=empty;tag=next;placeholder=empty;
        if(drawable!=null&&!(view instanceof SimpleTextView)){drawable.detach();drawable.setCallback(null);}
        drawable=next==null&&!empty?null:new DevGramClanTagDrawable(view,account,next,foreground);
        if(view instanceof SimpleTextView){((SimpleTextView)view).setDevGramClanTag(drawable,drawable==null?null:v->open());}
        else if(drawable!=null){drawable.setCallback(view);if(attached)drawable.attach();}
        hit.setEmpty();return true;
    }
    public int width(){refresh();return drawable==null?0:drawable.getIntrinsicWidth()+AndroidUtilities.dp(3);}
    private static int ColorAlpha(int color){return android.graphics.Color.alpha(color);}
    public void clearHit(){hit.setEmpty();}
    public void draw(Canvas canvas,float x,float centerY,int color,float alpha,float right){
        refresh();hit.setEmpty();if(drawable==null||x+drawable.getIntrinsicWidth()>right||alpha<=0)return;
        int top=Math.round(centerY-drawable.getIntrinsicHeight()/2f),left=Math.round(x);
        drawable.color(color);drawable.setAlpha(Math.round(ColorAlpha(color)*Math.max(0,Math.min(1,alpha))));
        drawable.setBounds(left,top,left+drawable.getIntrinsicWidth(),top+drawable.getIntrinsicHeight());drawable.draw(canvas);
        hit.set(left-AndroidUtilities.dp(3),top-AndroidUtilities.dp(5),left+drawable.getIntrinsicWidth()+AndroidUtilities.dp(3),top+drawable.getIntrinsicHeight()+AndroidUtilities.dp(5));
    }
    public boolean touch(MotionEvent event){
        if(!DevGramProfileCustomization.enabled()){pressed=false;return false;}
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){pressed=drawable!=null&&hit.contains(event.getX(),event.getY());if(pressed){downX=event.getX();downY=event.getY();}return pressed;}
        if(!pressed)return false;
        if(action==MotionEvent.ACTION_MOVE&&Math.hypot(event.getX()-downX,event.getY()-downY)>AndroidUtilities.dp(8)){pressed=false;return false;}
        if(action==MotionEvent.ACTION_UP){pressed=false;if(hit.contains(event.getX(),event.getY()))open();return true;}
        if(action==MotionEvent.ACTION_CANCEL){pressed=false;return true;}return true;
    }
    private void open(){
        BaseFragment host=org.telegram.ui.LaunchActivity.getLastFragment();
        if(host==null||host.getParentActivity()==null||host.getCurrentAccount()!=account||!DevGramProfileCustomization.enabled())return;
        if(editing&&placeholder&&peer==host.getUserConfig().getClientUserId())host.presentFragment(new DevGramClanTagsActivity());
        else if(tag!=null)DevGramClanTagsActivity.showTag(host,tag);
    }
    public void attach(){attached=true;refresh();if(!(view instanceof SimpleTextView)&&drawable!=null)drawable.attach();}
    public void detach(){attached=false;pressed=false;hit.setEmpty();if(!(view instanceof SimpleTextView)&&drawable!=null)drawable.detach();}
    @Override public void onClanTagChanged(){if(!view.isAttachedToWindow())return;boolean changed=refresh();if(changed&&rebuild!=null)rebuild.run();view.invalidate();}
}

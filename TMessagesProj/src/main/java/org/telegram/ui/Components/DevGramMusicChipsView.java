package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Rect;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.widget.HorizontalScrollView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramScrollPosition;
import org.telegram.messenger.MessagesController;

/** Reveals overflowing music chips inside their viewport; touching always takes priority. */
public final class DevGramMusicChipsView extends HorizontalScrollView {
    private final DevGramScrollPosition position=new DevGramScrollPosition();
    private final Rect visibleRect=new Rect();
    private boolean attached,running,touching,automaticScroll,motionAllowed=true;
    private long policyAt;
    private int range=-1;
    private final ViewTreeObserver.OnScrollChangedListener parentScroll=this::sync;
    private final Runnable frame=new Runnable(){@Override public void run(){
        if(!wanted()){running=false;return;}
        long now=SystemClock.uptimeMillis();automaticScroll=true;try{scrollTo(Math.round(position.advance(now,position.pixelsPerSecond(AndroidUtilities.dp(24)))),0);}finally{automaticScroll=false;}
        if(running)postDelayed(this,position.delay(now));
    }};
    public DevGramMusicChipsView(Context context){super(context);setHorizontalScrollBarEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);setClipToPadding(true);}
    private boolean wanted(){
        if(!attached||touching||range<=0||!isShown()||!hasWindowFocus()||getWindowVisibility()!=VISIBLE||!getGlobalVisibleRect(visibleRect))return false;
        if(!MessagesController.getGlobalMainSettings().getBoolean("devgramProfileMotion",true))return false;
        long now=SystemClock.uptimeMillis();if(now>=policyAt){policyAt=now+2000;
            PowerManager power=(PowerManager)getContext().getSystemService(Context.POWER_SERVICE);
            AccessibilityManager accessibility=(AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            motionAllowed=(power==null||!power.isPowerSaveMode())&&(accessibility==null||!accessibility.isTouchExplorationEnabled())&&Settings.Global.getFloat(getContext().getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;
        }
        return motionAllowed;
    }
    private void sync(){
        boolean wanted=wanted();if(wanted==running)return;running=wanted;removeCallbacks(frame);
        if(running){position.resume(SystemClock.uptimeMillis());postDelayed(frame,position.delay(SystemClock.uptimeMillis()));}
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);
        int next=getChildCount()==0?0:Math.max(0,getChildAt(0).getWidth()-(getWidth()-getPaddingLeft()-getPaddingRight()));
        if(next!=range){range=next;position.configure(range,getScrollX(),getLayoutDirection()==LAYOUT_DIRECTION_RTL,SystemClock.uptimeMillis());}
        sync();
    }
    @Override protected void onScrollChanged(int left,int top,int oldLeft,int oldTop){
        super.onScrollChanged(left,top,oldLeft,oldTop);
        if(position!=null&&!automaticScroll&&range>0&&left!=oldLeft)position.manual(left,SystemClock.uptimeMillis());
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){touching=true;sync();}
        boolean handled=super.dispatchTouchEvent(event);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;position.manual(getScrollX(),SystemClock.uptimeMillis());sync();}
        return handled;
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;getViewTreeObserver().addOnScrollChangedListener(parentScroll);sync();}
    @Override protected void onDetachedFromWindow(){attached=false;sync();if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnScrollChangedListener(parentScroll);super.onDetachedFromWindow();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibleRect!=null)sync();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibleRect!=null)sync();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);policyAt=0;sync();}
}

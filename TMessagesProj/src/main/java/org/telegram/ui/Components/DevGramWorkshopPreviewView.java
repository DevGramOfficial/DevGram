package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.Theme;

/** Reference workshop: still GIF + marker in the feed, visible animated detail, 66ms redraws. */
public final class DevGramWorkshopPreviewView extends FrameLayout {
    private DevGramStudioPreview nativePreview;
    private PreviewImage capture;
    private final Rect visibleRect=new Rect();
    private final DevGramProfilePainter power=new DevGramProfilePainter();
    private final boolean nativeMotion,assetMotion,motionMark;
    private Paint markFill,markInk;
    private RectF markBounds;
    private Path markPlay;
    private boolean active=true,attached,released;
    public DevGramWorkshopPreviewView(Context context,int account,JSONObject work,boolean motion) {
        super(context);
        nativeMotion=motion;
        String source=work.optString("preview");
        assetMotion=motion;motionMark=!motion&&source.endsWith(".gif");
        setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_windowBackgroundGray),8));
        setClipToOutline(true);
        setContentDescription(DevGramLocale.text("Предпросмотр профиля"));
        if(source.matches("asset:[a-f0-9]{64}\\.(png|jpg|gif|webp)")) {
            capture=new PreviewImage(context);
            capture.setAspectFit(true);
            capture.getImageReceiver().setAllowDecodeSingleFrame(true);
            capture.getImageReceiver().setAllowStartAnimation(false);
            capture.getImageReceiver().setAllowStartLottieAnimation(false);
            capture.getImageReceiver().setInvalidateAll(true);
            capture.getImageReceiver().setAutoRepeat(1);
            capture.setImage(DevGramProfileTextures.imageUrl(source),motionMark?"firstframe":null,null);
            addView(capture,new FrameLayout.LayoutParams(-1,-1,Gravity.TOP));
        } else {
            nativePreview=new DevGramStudioPreview(context,account,work.optLong("uid"));
            nativePreview.setEdgeToEdge(true);nativePreview.setStill(!motion);
            nativePreview.setFrameOnly(work.optString("kind").equals("frame"));
            nativePreview.set(work.optJSONObject("document"),new DevGramProfileStyle.Style(work.optJSONObject("theme")));
            addView(nativePreview,new FrameLayout.LayoutParams(-1,-1));
        }
    }
    @Override protected void dispatchDraw(Canvas canvas){
        super.dispatchDraw(canvas);
        if(!motionMark||capture==null||!capture.getImageReceiver().hasBitmapImage())return;
        if(markFill==null){markFill=new Paint(Paint.ANTI_ALIAS_FLAG);markFill.setColor(0x99000000);markInk=new Paint(Paint.ANTI_ALIAS_FLAG);markInk.setColor(0xffffffff);markInk.setTextSize(AndroidUtilities.dpf2(10));markInk.setTypeface(Typeface.DEFAULT_BOLD);markBounds=new RectF();markPlay=new Path();}
        float height=AndroidUtilities.dpf2(18),pad=AndroidUtilities.dpf2(7),arrow=AndroidUtilities.dpf2(7),gap=AndroidUtilities.dpf2(4);
        float width=pad*2+arrow+gap+markInk.measureText("GIF"),right=getWidth()-getPaddingRight()-AndroidUtilities.dpf2(8),top=getPaddingTop()+AndroidUtilities.dpf2(8);
        markBounds.set(right-width,top,right,top+height);canvas.drawRoundRect(markBounds,height/2,height/2,markFill);
        float x=markBounds.left+pad,y=markBounds.centerY();markPlay.reset();markPlay.moveTo(x,y-arrow/2);markPlay.lineTo(x+arrow,y);markPlay.lineTo(x,y+arrow/2);markPlay.close();canvas.drawPath(markPlay,markInk);
        canvas.drawText("GIF",x+arrow+gap,y-(markInk.descent()+markInk.ascent())/2,markInk);
    }
    public void setActive(boolean value){active=value;power.invalidateMotion();syncPlayback();}
    private boolean visible(){return !released&&active&&attached&&isShown()&&getWindowVisibility()==View.VISIBLE&&getGlobalVisibleRect(visibleRect);}
    private void syncPlayback(){
        boolean visible=visible(),motion=visible&&power.motion();
        if(nativePreview!=null)nativePreview.setStill(!nativeMotion||!motion);
        if(capture==null)return;
        ImageReceiver receiver=capture.getImageReceiver();boolean allowed=assetMotion&&motion;
        receiver.setAllowStartAnimation(allowed);receiver.setAllowStartLottieAnimation(allowed);
        AnimatedFileDrawable animation=receiver.getAnimation();
        if(animation!=null){animation.setLimitFps(true);if(allowed&&!animation.isRunning())receiver.startAnimation();else if(!allowed&&animation.isRunning())receiver.stopAnimation();}
        if(!allowed){receiver.stopAnimation();capture.cancelFrame();}
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;power.invalidateMotion();syncPlayback();}
    @Override protected void onDetachedFromWindow(){attached=false;syncPlayback();super.onDetachedFromWindow();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);syncPlayback();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);syncPlayback();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){power.invalidateMotion();syncPlayback();}}
    private final class PreviewImage extends BackupImageView {
        private long lastInvalidate=-66;
        private boolean pending;
        private final Runnable nextFrame=()->{pending=false;if(visible()){lastInvalidate=SystemClock.uptimeMillis();super.invalidate();}};
        PreviewImage(Context context){super(context);}
        void cancelFrame(){if(nextFrame!=null)removeCallbacks(nextFrame);pending=false;}
        @Override public void invalidate(){
            if(nextFrame==null||!attached){super.invalidate();return;}
            if(!visible()){syncPlayback();return;}
            long delay=Math.max(0,66-Math.max(0,SystemClock.uptimeMillis()-lastInvalidate));
            if(delay==0){cancelFrame();lastInvalidate=SystemClock.uptimeMillis();super.invalidate();}
            else if(!pending){pending=true;postDelayed(nextFrame,delay);}
        }
        @Override protected void onDraw(Canvas canvas){syncPlayback();super.onDraw(canvas);}
        @Override protected void onDetachedFromWindow(){cancelFrame();super.onDetachedFromWindow();}
    }
    public void release(){if(released)return;released=true;syncPlayback();if(nativePreview!=null)nativePreview.release();if(capture!=null){capture.cancelFrame();capture.getImageReceiver().clearImage();}}
}

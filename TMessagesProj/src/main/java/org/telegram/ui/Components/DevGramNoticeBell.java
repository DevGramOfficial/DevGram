package org.telegram.ui.Components;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.FrameLayout;
import org.telegram.messenger.*;
import org.telegram.ui.*;
import org.telegram.ui.ActionBar.*;

/** Four reference dock targets, long-press dragging and native folder/menu reservations. */
public final class DevGramNoticeBell extends View {
    private static final int SLOT=948163;
    private final BaseFragment host;
    private final FrameLayout root;
    private final ActionBar bar;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable icon;
    private View tabs,fab,stories;
    private ActionBarMenuItem slot;
    private ViewGroup.MarginLayoutParams originalParams;
    private int originalLeft,originalRight,dock,candidate,startDock;
    private View guide;
    private final float[] x=new float[4],y=new float[4];
    private final int[] rootAt=new int[2],anchorAt=new int[2];
    private float touchX,touchY;
    private boolean dragging,settling,closed;
    private final Runnable change=()->{if(!closed){sync();invalidate();}};
    private final android.view.ViewTreeObserver.OnPreDrawListener layout=()->{sync();return true;};
    public static DevGramNoticeBell attach(BaseFragment host,DevGramNoticeBell existing,View tabs,View fab,View stories){if(!(host.getFragmentView() instanceof FrameLayout)||host.getActionBar()==null)return existing;DevGramCustomizationNotices.wake(host.getCurrentAccount());if(existing!=null&&existing.root==host.getFragmentView()){existing.bind(tabs,fab,stories);return existing;}if(existing!=null)existing.close();return new DevGramNoticeBell(host,(FrameLayout)host.getFragmentView(),tabs,fab,stories);}
    private DevGramNoticeBell(BaseFragment host,FrameLayout root,View tabs,View fab,View stories){super(root.getContext());this.host=host;this.root=root;bar=host.getActionBar();icon=getResources().getDrawable(R.drawable.msg_notifications).mutate();dock=Math.max(0,Math.min(3,MessagesController.getGlobalMainSettings().getInt("devgramNoticeBellDock",0)));setContentDescription(DevGramLocale.text("Уведомления. Удерживайте, чтобы переместить"));setClickable(true);setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarDefaultSelector,host.getResourceProvider()),1));setOnClickListener(v->{if(DevGramCustomizationNotices.allowed())DevGramNoticesSheet.show(host);});setOnLongClickListener(v->{if(!isEnabled())return false;dragging=true;settling=false;candidate=startDock=dock;animate().cancel();showGuide();animate().scaleX(1.08f).scaleY(1.08f).setDuration(140).start();setPressed(false);setElevation(dp(12));getParent().requestDisallowInterceptTouchEvent(true);reserve(false);invalidate();DevGramStudioControls.haptic(this,HapticFeedbackConstants.LONG_PRESS);return true;});slot=bar.createMenu().addItem(SLOT,R.drawable.msg_notifications);slot.setVisibility(GONE);bind(tabs,fab,stories);root.addView(this,LayoutHelper.createFrame(40,48,Gravity.TOP|Gravity.LEFT));root.getViewTreeObserver().addOnPreDrawListener(layout);DevGramCustomizationNotices.listen(change);DevGramProfilePolicy.listen(change);sync();}
    private static int dp(float v){return AndroidUtilities.dp(v);}
    private void bind(View nextTabs,View fab,View stories){if(tabs!=nextTabs){reserve(false);tabs=nextTabs;originalParams=tabs!=null&&tabs.getLayoutParams() instanceof ViewGroup.MarginLayoutParams?(ViewGroup.MarginLayoutParams)tabs.getLayoutParams():null;if(originalParams!=null){originalLeft=originalParams.leftMargin;originalRight=originalParams.rightMargin;}}this.fab=fab;this.stories=stories;}
    private boolean tabsVisible(){return tabs!=null&&tabs.isShown()&&tabs.getHeight()>0;}
    private boolean floatingVisible(){return fab!=null&&fab.isShown()&&fab.getWidth()>0&&fab.getAlpha()>.01f;}
    private void position(View anchor){root.getLocationOnScreen(rootAt);anchor.getLocationOnScreen(anchorAt);}
    private void targets(){float right=Math.max(dp(4),root.getWidth()-dp(44)),bottom=Math.max(dp(4),root.getHeight()-dp(52));x[0]=right;x[1]=dp(4);if(tabsVisible()){position(tabs);y[0]=y[1]=anchorAt[1]-rootAt[1];}else if(slot!=null&&slot.getVisibility()!=GONE){position(slot);x[0]=anchorAt[0]-rootAt[0]+(slot.getWidth()-dp(40))/2f;y[0]=anchorAt[1]-rootAt[1]+(slot.getHeight()-dp(48))/2f;x[1]=x[0];y[1]=y[0];}else{position(bar);y[0]=y[1]=anchorAt[1]-rootAt[1]+bar.getHeight()-dp(48);}
        if(floatingVisible()){position(fab);float left=anchorAt[0]-rootAt[0],top=anchorAt[1]-rootAt[1],height=fab.getHeight();if(stories!=null&&stories.isShown()){position(stories);top=Math.min(top,anchorAt[1]-rootAt[1]);}x[2]=Math.max(dp(4),Math.min(right,left+(fab.getWidth()-dp(40))/2f));y[2]=Math.max(dp(4),Math.min(bottom,top-dp(56)));x[3]=Math.max(dp(4),Math.min(right,left-dp(48)));position(fab);y[3]=Math.max(dp(4),Math.min(bottom,anchorAt[1]-rootAt[1]+(height-dp(48))/2f));}else{x[2]=x[0];y[2]=y[0];x[3]=x[1];y[3]=y[1];}}
    private void reserve(boolean enabled){if(originalParams==null||tabs==null)return;int left=originalLeft+(enabled&&dock==1?dp(48):0),right=originalRight+(enabled&&dock==0?dp(48):0);if(originalParams.leftMargin!=left||originalParams.rightMargin!=right){originalParams.leftMargin=left;originalParams.rightMargin=right;tabs.setLayoutParams(originalParams);}}
    private void sync(){if(closed)return;boolean visible=DevGramCustomizationNotices.allowed()&&!bar.isSearchFieldVisible()&&!host.isPaused()&&(dock<2||floatingVisible());setVisibility(visible?VISIBLE:INVISIBLE);setEnabled(visible);boolean inMenu=visible&&dock<2&&!tabsVisible();if(slot!=null)slot.setVisibility(inMenu?INVISIBLE:GONE);reserve(visible&&dock<2&&tabsVisible()&&!dragging);if(!visible){if(dragging)settle(true);return;}targets();if(!dragging&&!settling){setTranslationX(x[dock]);setTranslationY(y[dock]);setAlpha(dock>=2&&fab!=null?fab.getAlpha():1);setScaleX(dock>=2&&fab!=null?fab.getScaleX():1);setScaleY(dock>=2&&fab!=null?fab.getScaleY():1);}}
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){touchX=event.getX();touchY=event.getY();}else if(dragging&&event.getActionMasked()==MotionEvent.ACTION_MOVE){root.getLocationOnScreen(rootAt);setTranslationX(Math.max(dp(4),Math.min(root.getWidth()-getWidth()-dp(4),event.getRawX()-rootAt[0]-touchX)));setTranslationY(Math.max(dp(4),Math.min(root.getHeight()-getHeight()-dp(4),event.getRawY()-rootAt[1]-touchY)));targets();int next=nearest(event.getRawX()-rootAt[0],event.getRawY()-rootAt[1]);if(next!=candidate){candidate=next;DevGramStudioControls.haptic(this,HapticFeedbackConstants.CLOCK_TICK);}if(guide!=null)guide.invalidate();invalidate();return true;}else if(dragging&&(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)){settle(event.getActionMasked()==MotionEvent.ACTION_CANCEL);return true;}return super.onTouchEvent(event);}
    private int nearest(float px,float py){float best=Float.MAX_VALUE;int selected=dock;for(int i=0;i<(floatingVisible()?4:2);i++){float dx=px-x[i]-dp(20),dy=py-y[i]-dp(24),distance=dx*dx+dy*dy;if(distance<best){best=distance;selected=i;}}return selected;}
    private void showGuide(){targets();if(guide!=null)root.removeView(guide);guide=new View(getContext()){
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override protected void onDraw(Canvas canvas){int color=Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,host.getResourceProvider())&0xffffff;for(int i=0;i<(floatingVisible()?4:2);i++){ink.setColor((i==candidate?72:24)<<24|color);canvas.drawRoundRect(x[i],y[i]+dp(4),x[i]+dp(40),y[i]+dp(44),dp(12),dp(12),ink);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp(i==candidate?2:1));ink.setColor((i==candidate?210:90)<<24|color);canvas.drawCircle(x[i]+dp(20),y[i]+dp(24),dp(7),ink);ink.setStyle(Paint.Style.FILL);}}
    };guide.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(guide,new FrameLayout.LayoutParams(-1,-1));bringToFront();}
    private void settle(boolean cancel){dragging=false;targets();dock=cancel?startDock:candidate;MessagesController.getGlobalMainSettings().edit().putInt("devgramNoticeBellDock",dock).apply();View previous=guide;guide=null;if(previous!=null)previous.animate().alpha(0).setDuration(120).withEndAction(()->root.removeView(previous)).start();settling=true;animate().translationX(x[dock]).translationY(y[dock]).scaleX(1).scaleY(1).setDuration(220).withEndAction(()->{settling=false;setElevation(0);sync();}).start();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    @Override protected void onDraw(Canvas canvas){int color=Theme.getColor(Theme.key_actionBarDefault,host.getResourceProvider());paint.setColor(color);canvas.drawRoundRect(0,dp(4),getWidth(),getHeight()-dp(4),dp(12),dp(12),paint);int side=(int)(Math.min(getWidth(),getHeight())*.6f),left=(getWidth()-side)/2,top=(getHeight()-side)/2;icon.setBounds(left,top,left+side,top+side);icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon,host.getResourceProvider()),PorterDuff.Mode.SRC_IN));icon.draw(canvas);if(DevGramCustomizationNotices.unread()>0){float cx=left+side-dp(1.75f),cy=top+dp(1.75f);paint.setColor(color);canvas.drawCircle(cx,cy,dp(5),paint);paint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton,host.getResourceProvider()));canvas.drawCircle(cx,cy,dp(3.5f),paint);}}
    public void close(){if(closed)return;closed=true;if(guide!=null){root.removeView(guide);guide=null;}if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);animate().cancel();reserve(false);DevGramCustomizationNotices.forget(change);DevGramProfilePolicy.unlisten(change);if(root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnPreDrawListener(layout);if(slot!=null)slot.setVisibility(GONE);if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);}
}

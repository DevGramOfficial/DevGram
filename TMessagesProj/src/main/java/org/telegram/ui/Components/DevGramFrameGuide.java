package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.ui.ActionBar.Theme;

/** A local guided tour over the real frame controls, without editing the user's graph. */
public final class DevGramFrameGuide extends FrameLayout {
    private final View[] targets;
    private final String[] descriptions;
    private final Theme.ResourcesProvider resources;
    private final TextView title,description,back,next;
    private final LinearLayout card;
    private final RectF target=new RectF();
    private final int[] rootPosition=new int[2],position=new int[2];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shade=new Path();
    private int step;

    public DevGramFrameGuide(Context context,Theme.ResourcesProvider resources,View[] targets,String[] descriptions,int bottomInset){
        super(context);this.targets=targets;this.descriptions=descriptions;this.resources=resources;setWillNotDraw(false);setFocusableInTouchMode(true);
        card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(14),dp(18),dp(10));card.setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_dialogBackground,resources),Theme.getColor(Theme.key_divider,resources)));
        title=DevGramStudioControls.text(context,"",16,true,resources);card.addView(title);
        description=DevGramStudioControls.text(context,"",14,false,resources);description.setPadding(0,dp(8),0,dp(10));card.addView(description);
        LinearLayout actions=new LinearLayout(context);back=button("Назад",()->{if(step>0){step--;bind();}});next=button("Далее",()->{if(step+1>=targets.length)close();else{step++;bind();}});actions.addView(button("Закрыть",this::close),new LinearLayout.LayoutParams(0,dp(44),1));actions.addView(back,new LinearLayout.LayoutParams(0,dp(44),1));actions.addView(next,new LinearLayout.LayoutParams(0,dp(44),1));card.addView(actions);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);params.setMargins(dp(12),dp(12),dp(12),Math.max(dp(12),bottomInset+dp(8)));addView(card,params);bind();
    }
    private TextView button(String label,Runnable action){TextView view=DevGramStudioControls.text(getContext(),label,14,true,resources);view.setGravity(Gravity.CENTER);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources));view.setOnClickListener(v->action.run());return view;}
    private void bind(){targets[step].requestRectangleOnScreen(new android.graphics.Rect(0,0,targets[step].getWidth(),targets[step].getHeight()),true);title.setText(DevGramLocale.text("Гайд по узлам")+"  ·  "+(step+1)+" / "+targets.length);description.setText(DevGramLocale.text(descriptions[step]));back.setVisibility(step==0?View.INVISIBLE:View.VISIBLE);next.setText(DevGramLocale.text(step+1==targets.length?"Готово":"Далее"));postInvalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);getLocationOnScreen(rootPosition);View view=targets[step];view.getLocationOnScreen(position);target.set(position[0]-rootPosition[0],position[1]-rootPosition[1],position[0]-rootPosition[0]+view.getWidth(),position[1]-rootPosition[1]+view.getHeight());target.inset(-dp(3),-dp(3));
        shade.rewind();shade.setFillType(Path.FillType.EVEN_ODD);shade.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);shade.addRoundRect(target,dp(10),dp(10),Path.Direction.CW);paint.setStyle(Paint.Style.FILL);paint.setColor(0xbb000000);canvas.drawPath(shade,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources));canvas.drawRoundRect(target,dp(10),dp(10),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event){return true;}
    public void close(){if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);}
    private static int dp(float value){return AndroidUtilities.dp(value);}
}

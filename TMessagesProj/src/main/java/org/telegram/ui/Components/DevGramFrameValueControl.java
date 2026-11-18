package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.ui.ActionBar.Theme;

/** Drag-to-steer unrestricted integer values, with exact entry on tap. */
public final class DevGramFrameValueControl extends LinearLayout {
    private final TextView value;
    private int current;
    public DevGramFrameValueControl(Context context,Theme.ResourcesProvider resources,String title,int initial,BooleanSupplier allowed,IntConsumer sink,Runnable exact){
        super(context);setOrientation(VERTICAL);setPadding(dp(20),dp(8),dp(20),dp(4));current=initial;
        LinearLayout heading=new LinearLayout(context);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=DevGramStudioControls.text(context,title,14,false,resources);heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        value=DevGramStudioControls.text(context,Integer.toString(initial),14,true,resources);value.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources));value.setPadding(dp(12),dp(8),0,dp(8));value.setOnClickListener(v->{if(allowed.getAsBoolean())exact.run();});heading.addView(value);addView(heading);
        View pad=new View(context){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float startX,offset;int start;boolean moved;
            @Override protected void onDraw(Canvas canvas){float cy=getHeight()/2f;paint.setColor(Theme.getColor(Theme.key_dialogInputField,resources));canvas.drawRoundRect(0,dp(5),getWidth(),getHeight()-dp(5),dp(10),dp(10),paint);paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resources));paint.setTextSize(dp(18));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("−",dp(20),cy+dp(6),paint);canvas.drawText("+",getWidth()-dp(20),cy+dp(6),paint);paint.setStrokeWidth(dp(2));canvas.drawLine(getWidth()/2f,cy-dp(8),getWidth()/2f,cy+dp(8),paint);paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,resources));canvas.drawCircle(getWidth()/2f+Math.max(-getWidth()/3f,Math.min(getWidth()/3f,offset)),cy,dp(5),paint);}
            @Override public boolean onTouchEvent(MotionEvent event){if(!allowed.getAsBoolean())return false;switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:startX=event.getX();start=current;offset=0;moved=false;getParent().requestDisallowInterceptTouchEvent(true);return true;
                case MotionEvent.ACTION_MOVE:offset=event.getX()-startX;if(Math.abs(offset)>dp(4))moved=true;if(moved){current=(int)Math.max(-1073741824L,Math.min(1073741823L,(long)start+Math.round(offset/AndroidUtilities.density/2.5f)));value.setText(Integer.toString(current));sink.accept(current);invalidate();}return true;
                case MotionEvent.ACTION_UP:if(!moved){if(event.getX()<dp(40)||event.getX()>getWidth()-dp(40)){current=(int)Math.max(-1073741824L,Math.min(1073741823L,(long)current+(event.getX()<dp(40)?-1:1)));value.setText(Integer.toString(current));sink.accept(current);}else exact.run();}offset=0;getParent().requestDisallowInterceptTouchEvent(false);invalidate();performClick();return true;
                case MotionEvent.ACTION_CANCEL:offset=0;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
            }return false;}
            @Override public boolean performClick(){super.performClick();return true;}
        };
        pad.setContentDescription(DevGramLocale.text("Потяните влево или вправо для изменения числа. Нажмите для точного ввода."));addView(pad,new LinearLayout.LayoutParams(-1,dp(44)));
    }
    private static int dp(float value){return AndroidUtilities.dp(value);}
}

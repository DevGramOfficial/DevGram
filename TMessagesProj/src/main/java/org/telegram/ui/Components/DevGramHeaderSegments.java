package org.telegram.ui.Components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.HapticFeedbackConstants;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import java.util.ArrayList;
import java.util.function.IntConsumer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.ui.ActionBar.Theme;

/** The reference editor's responsive segment grid (up to three 140 dp columns). */
public final class DevGramHeaderSegments extends LinearLayout {
    private final String[] options;private final IntConsumer sink;private final ArrayList<TextView> buttons=new ArrayList<>();
    private int selected,columns;private boolean available=true;
    public DevGramHeaderSegments(Context context,String[] options,int selected,IntConsumer sink){super(context);this.options=options;this.selected=selected;this.sink=sink;setOrientation(VERTICAL);setPadding(dp(16),dp(4),dp(16),dp(8));}
    private static int dp(float value){return AndroidUtilities.dp(value);}
    public void bind(int selected,boolean available){this.selected=selected;this.available=available;refresh();}
    @Override protected void onMeasure(int width,int height){int count=Math.max(1,Math.min(options.length,Math.min(3,(MeasureSpec.getSize(width)-getPaddingLeft()-getPaddingRight())/Math.max(1,dp(Math.round(Math.max(1,getResources().getConfiguration().fontScale)*140))))));if(count!=columns)build(count);super.onMeasure(width,height);}
    private void build(int count){columns=count;removeAllViews();buttons.clear();LinearLayout row=null;
        for(int i=0;i<options.length;i++){final int index=i;int column=i%count;if(column==0){row=new LinearLayout(getContext());LayoutParams p=new LayoutParams(-1,-2);p.bottomMargin=dp(6);addView(row,p);}
            TextView button=new TextView(getContext());button.setTextSize(14);button.setGravity(17);button.setMinHeight(dp(48));button.setPadding(dp(10),dp(10),dp(10),dp(10));button.setHorizontallyScrolling(false);
            button.setOnClickListener(v->{if(index==selected&&available)return;if(available){selected=index;refresh();performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}sink.accept(index);});buttons.add(button);LayoutParams p=new LayoutParams(0,-2,1);if(column!=0)p.leftMargin=dp(6);row.addView(button,p);
        }
        int remainder=options.length%count;if(remainder!=0&&row!=null)while(remainder++<count){View space=new View(getContext());LayoutParams p=new LayoutParams(0,1,1);p.leftMargin=dp(6);row.addView(space,p);}refresh();
    }
    private void refresh(){int accent=Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),text=Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),base=Theme.getColor(Theme.key_windowBackgroundGray);
        for(int i=0;i<buttons.size();i++){TextView button=buttons.get(i);boolean picked=i==selected;GradientDrawable shape=new GradientDrawable();shape.setColor(picked?ColorUtils.blendARGB(base,accent,.18f):base);shape.setCornerRadius(dp(8));shape.setStroke(dp(1),picked?accent:ColorUtils.blendARGB(base,text,.12f));button.setBackground(new RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent,45)),shape,null));button.setText(DevGramLocale.text(options[i])+(available?"":" 🔒"));button.setTextColor(picked?accent:text);button.setTypeface(null,picked?1:0);button.setSelected(picked);button.setContentDescription(button.getText()+(picked?DevGramLocale.text(", выбрано",", selected"):""));}
    }
}

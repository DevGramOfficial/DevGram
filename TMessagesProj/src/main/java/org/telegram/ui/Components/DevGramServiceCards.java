package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramProfileRowSpec;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import java.util.function.IntConsumer;

/** EditServiceCards geometry and original IntegrationMarks, with native accessible buttons. */
public final class DevGramServiceCards extends LinearLayout {
    private static final int[][] FAMILIES={{3,4,5,8},{0,1,2,7}};
    private static final int[] COLORS={-2813945,-14407377,-14997448,-13312,-14829228,-43776,-16746497,-10983950,-11243910};
    private static final int[] MARKS={0,R.drawable.devgram_service_github,R.drawable.devgram_service_steam,R.drawable.devgram_service_yamusic,R.drawable.devgram_service_spotify,R.drawable.devgram_service_soundcloud,0,R.drawable.devgram_service_discord,0};
    private static int dp(float n){return AndroidUtilities.dp(n);}
    public DevGramServiceCards(Context context,int selected,Theme.ResourcesProvider provider,IntConsumer choice){
        super(context);setOrientation(VERTICAL);setPadding(dp(21),0,dp(21),dp(9));
        int accent=Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,provider);
        for(int[] family:FAMILIES){
            LinearLayout line=new LinearLayout(context);line.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
            for(int index=0;index<family.length;index++){
                int service=family[index];boolean picked=service==selected;
                LinearLayout card=new LinearLayout(context);card.setOrientation(VERTICAL);card.setGravity(Gravity.CENTER);card.setMinimumHeight(dp(92));card.setPadding(dp(2),dp(9),dp(2),dp(5));
                card.setBackground(background(accent,picked));card.setSelected(picked);
                ImageView mark=new ImageView(context);mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                mark.setImageDrawable(mark(context,service));mark.setBackground(tile(COLORS[service],0));mark.setPadding(dp(9),dp(9),dp(9),dp(9));
                card.addView(mark,new LinearLayout.LayoutParams(dp(40),dp(40)));
                TextView name=DevGramStudioControls.text(context,DevGramProfileRowSpec.SERVICES[service],12,false,provider);name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setTextColor(picked?accent:Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,provider));
                card.addView(name,LayoutHelper.createLinear(-1,-2,0,7,0,0));
                card.setContentDescription(DevGramLocale.text(DevGramProfileRowSpec.SERVICES[service]));card.setOnClickListener(v->{DevGramStudioControls.haptic(v,android.view.HapticFeedbackConstants.CLOCK_TICK);choice.accept(service);});
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,1);if(index>0)params.setMarginStart(dp(9));line.addView(card,params);
            }
            addView(line,LayoutHelper.createLinear(-1,-2,0,9,0,0));
        }
    }
    private static GradientDrawable tile(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));if(stroke!=0)d.setStroke(dp(2),stroke);return d;}
    private static Drawable background(int accent,boolean picked){
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},tile(withAlpha(accent,145),picked?accent:0));states.addState(new int[]{},tile(withAlpha(accent,picked?117:66),picked?accent:0));return states;
    }
    private static int withAlpha(int color,int alpha){return(color&0xffffff)|(alpha<<24);}
    public static Drawable mark(Context context,int service){
        Drawable d=context.getResources().getDrawable(MARKS[service]==0?R.drawable.bot_music:MARKS[service]).mutate();
        if(service!=2&&service!=7){int c=COLORS[service];int luminance=(Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000;d.setTint(luminance>150?-15723496:Color.WHITE);}return d;
    }
}

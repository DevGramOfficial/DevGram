package org.telegram.ui.Components;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;

/** The same bounded name brush is used by the real header and the studio preview. */
public final class DevGramNameEffects {
    private final Paint saved = new Paint();
    private final Matrix matrix = new Matrix();
    private final DevGramProfilePainter power = new DevGramProfilePainter();
    private Shader shader;
    private int previousMode=-1,previousFirst,previousSecond,previousWidth;
    private JSONObject document;
    private float visibility=1;
    public void set(JSONObject value,float visible){document=value;visibility=visible;}
    public boolean enabled(){return document!=null&&visibility>.01f&&(document.optInt("nameEffect")!=0||document.optInt("nameGlow")!=0||document.optInt("nameShadow")!=0||document.optInt("nameColorEnabled")==1);}
    public void apply(Paint paint,float width,float height,boolean still){
        saved.set(paint);JSONObject d=document;if(d==null)return;
        int effect=d.optInt("nameEffect"),first=paint.getColor(),second=(Math.round(d.optInt("nickAlpha2",100)*2.55f)<<24)|d.optInt("nickColor2",0x8e74e2);
        if(d.optInt("nameColorEnabled",d.optInt("nickColor")!=0?1:0)==1||effect==2)first=(Math.round(d.optInt("nickAlpha",100)*2.55f)<<24)|d.optInt("nickColor",0xffffff);
        paint.setShader(null);paint.setColor(first);
        boolean motion=!still&&power.motion();float time=motion?SystemClock.uptimeMillis()/1000f*d.optInt("nameSpeed",100)/100f:0;
        float pulse=.5f+.5f*(float)Math.sin(time*2.6f);
        if(effect==1)paint.setAlpha(Math.round(saved.getAlpha()*(1-visibility*(.45f-.45f*pulse))));
        if(effect>=2&&effect!=5){
            // The shader carries both color alphas; a transparent base title
            // must not multiply the complete gradient down to zero.
            paint.setAlpha(255);
            int w=Math.max(1,Math.round(width));
            if(shader==null||previousMode!=effect||previousFirst!=first||previousSecond!=second||previousWidth!=w){
                int[] colors;
                if(effect==3)colors=new int[]{first,first,Color.WHITE,first,first};
                else if(effect==4)colors=new int[]{0xffff5353,0xffffc247,0xff76df94,0xff63caff,0xffad8aff,0xffff5353};
                else if(effect==6)colors=new int[]{0xffd62626,0xffff9029,0xffffe9a8,0xffff9029,0xffd62626};
                else if(effect==7)colors=new int[]{0xff348acb,0xff93dfff,0xffffffff,0xff93dfff,0xff348acb};
                else colors=new int[]{first,second,first};
                shader=new LinearGradient(0,0,w,0,colors,null,Shader.TileMode.MIRROR);
                previousMode=effect;previousFirst=first;previousSecond=second;previousWidth=w;
            }
            matrix.reset();
            if(effect==2)matrix.setTranslate((time*.35f%1)*width,0);
            else matrix.setTranslate((time%4)/4*width*2,0);
            matrix.postRotate(d.optInt("nameAngle"),width/2,0);
            shader.setLocalMatrix(matrix);paint.setShader(shader);
        }
        if(effect==5){int strength=Math.round(120+135*pulse);paint.setShadowLayer(AndroidUtilities.dp(10+6*pulse),0,0,(strength<<24)|(first&0xffffff));}
        if(d.optInt("nameGlow")==1)paint.setShadowLayer(AndroidUtilities.dp(d.optInt("nameGlowRadius",12)),0,0,(Math.round(d.optInt("nameGlowStrength",100)*2.55f)<<24)|(first&0xffffff));
        if(d.optInt("nameShadow")==1)paint.setShadowLayer(AndroidUtilities.dp(d.optInt("nameShadowRadius",6)),AndroidUtilities.dp(d.optInt("nameShadowX",2)),AndroidUtilities.dp(d.optInt("nameShadowY",2)),(Math.round(d.optInt("nameShadowStrength",70)*2.55f)<<24)|d.optInt("nameShadowColor"));
    }
    public void restore(Paint paint){paint.set(saved);}
    public void animate(View view,boolean still){if(!still&&document!=null&&document.optInt("nameEffect")!=0&&power.motion()&&view.isShown()&&view.hasWindowFocus())view.postInvalidateDelayed(33);}
}

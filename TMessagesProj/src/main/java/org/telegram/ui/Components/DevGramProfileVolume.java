package org.telegram.ui.Components;

import android.graphics.*;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;

/** Directional face and edge lighting. Center brightness never paints a black fill. */
final class DevGramProfileVolume {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final float[] STOPS={0,.46f,1};
    void draw(Canvas canvas,Path shape,RectF bounds,JSONObject doc){
        float strength=doc.optInt("cardLightStrength",65)/100f;
        boolean side=doc.optInt("cardSideLight")==1;
        float face=side?0:strength,center=doc.optInt("cardCenterBrightness",100)/100f;
        double angle=Math.toRadians(doc.optInt("cardLightAngle",135));
        float dx=(float)Math.sin(angle),dy=-(float)Math.cos(angle);
        float distance=(Math.abs(dx)*bounds.width()+Math.abs(dy)*bounds.height())*.5f*doc.optInt("cardLightDistance")/500f;
        float fade=Math.max(.5f,AndroidUtilities.dp(doc.optInt("cardLightRadius",100))*.5f);
        float cx=bounds.centerX(),cy=bounds.centerY(),lightX=cx-dx*distance,lightY=cy-dy*distance;
        float shadeX=cx+dx*(distance-fade),shadeY=cy+dy*(distance-fade);
        int rgb=doc.optInt("cardLightColor",0xffffff)&0xffffff;
        paint.setStyle(Paint.Style.FILL);
        gradient(canvas,shape,lightX,lightY,dx,dy,fade,rgb,140*face,56*face,0);
        gradient(canvas,shape,shadeX,shadeY,dx,dy,fade,0,0,50*face,160*face);
        if(side)gradient(canvas,shape,lightX,lightY,dx,dy,fade,rgb,48*center,17*center,0);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(AndroidUtilities.density*1.15f);
        gradient(canvas,shape,lightX,lightY,dx,dy,fade,rgb,220*strength,76*strength,0);
        gradient(canvas,shape,shadeX,shadeY,dx,dy,fade,0,0,42*strength,190*strength);
        if(side){paint.setStrokeWidth(AndroidUtilities.density*1.65f);gradient(canvas,shape,lightX,lightY,dx,dy,fade,rgb,245*strength,92*strength,0);gradient(canvas,shape,shadeX,shadeY,dx,dy,fade,0,0,78*strength,235*strength);}
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);
    }
    private int color(int rgb,float alpha){return (Math.max(0,Math.min(255,Math.round(alpha)))<<24)|rgb;}
    private void gradient(Canvas canvas,Path path,float x,float y,float dx,float dy,float radius,int rgb,float a,float b,float c){
        if(a==0&&b==0&&c==0)return;
        paint.setColor(Color.WHITE);paint.setShader(new LinearGradient(x,y,x+dx*radius,y+dy*radius,new int[]{color(rgb,a),color(rgb,b),color(rgb,c)},STOPS,Shader.TileMode.CLAMP));canvas.drawPath(path,paint);
    }
}

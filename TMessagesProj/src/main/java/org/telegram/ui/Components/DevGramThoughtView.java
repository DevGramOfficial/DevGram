package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramProfileDocument;

/** Public cosmetic thought next to the avatar; it never replaces a server role badge. */
public final class DevGramThoughtView extends View {
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint background=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tail=new Path();
    private StaticLayout layout;
    private JSONObject document=new JSONObject();
    private String signature="";
    public DevGramThoughtView(Context context){super(context);text.setTextSize(dp(12));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    private int dp(float n){return AndroidUtilities.dp(n);}
    public void set(JSONObject doc){
        StringBuilder key=new StringBuilder(doc.optString("thoughtText"));for(String field:new String[]{"thoughtColor","thoughtBackground","thoughtAlpha","thoughtTextAlpha","thoughtFontCopy","thoughtFont","thoughtShadow","thoughtShadowColor","thoughtShadowRadius","thoughtShadowStrength","thoughtShadowX","thoughtShadowY","font","nameFont"})key.append('|').append(doc.opt(field));
        if(signature.contentEquals(key))return;signature=key.toString();document=doc;
        String value=doc.optString("thoughtText");setContentDescription(value);setVisibility(value.isEmpty()?GONE:VISIBLE);
        text.setTypeface(DevGramProfileDocument.thoughtFont(doc));text.setColor((Math.round(doc.optInt("thoughtTextAlpha",100)*2.55f)<<24)|doc.optInt("thoughtColor",0xffffff));
        layout=StaticLayout.Builder.obtain(value,0,value.length(),text,dp(130)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setMaxLines(5).setEllipsize(android.text.TextUtils.TruncateAt.END).build();
        setLayerType(doc.optInt("thoughtShadow")==1?LAYER_TYPE_SOFTWARE:LAYER_TYPE_NONE,null);requestLayout();invalidate();
    }
    @Override protected void onMeasure(int width,int height){float content=0;if(layout!=null)for(int line=0;line<layout.getLineCount();line++)content=Math.max(content,layout.getLineWidth(line));setMeasuredDimension(resolveSize(Math.min(dp(154),Math.max(dp(36),(int)Math.ceil(content)+dp(24))),width),resolveSize((layout==null?0:layout.getHeight())+dp(30),height));}
    @Override protected void onDraw(Canvas canvas){
        if(layout==null)return;int save=canvas.save();float bottom=layout.getHeight()+dp(18);
        background.setColor((Math.round(document.optInt("thoughtAlpha",100)*2.55f)<<24)|document.optInt("thoughtBackground",0x293450));background.clearShadowLayer();
        if(document.optInt("thoughtShadow")==1)background.setShadowLayer(dp(document.optInt("thoughtShadowRadius",6)),dp(document.optInt("thoughtShadowX")),dp(document.optInt("thoughtShadowY",3)),(Math.round(document.optInt("thoughtShadowStrength",70)*2.55f)<<24)|document.optInt("thoughtShadowColor"));
        canvas.drawRoundRect(dp(5),dp(4),getWidth()-dp(5),bottom,dp(16),dp(16),background);background.clearShadowLayer();
        canvas.drawCircle(dp(16),bottom+dp(3),dp(4),background);canvas.drawCircle(dp(9),bottom+dp(10),dp(2),background);
        canvas.translate(dp(12),dp(10));layout.draw(canvas);canvas.restoreToCount(save);
    }
}

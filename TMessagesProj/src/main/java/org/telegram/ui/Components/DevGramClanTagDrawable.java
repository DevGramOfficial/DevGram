package org.telegram.ui.Components;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.*;

/** Source-tag pill, sharing the reference's emoji + compact 1–5 symbol label. */
public final class DevGramClanTagDrawable extends Drawable {
    private final Paint back=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;
    private final String label;
    private final int width,height;
    private int color=Color.WHITE,alpha=255;
    public DevGramClanTagDrawable(View host,int account,JSONObject tag){
        this(host,account,tag,null);
    }
    public DevGramClanTagDrawable(View host,int account,JSONObject tag,java.util.function.IntSupplier foreground){
        text.setTextSize(AndroidUtilities.dp(10));text.setTypeface(AndroidUtilities.bold());
        label=tag==null?DevGramLocale.text("тег"):tag.optString("tag");height=AndroidUtilities.dp(17);
        width=Math.round(text.measureText(label))+AndroidUtilities.dp(28);
        emoji=new DevGramBadgeEmojiDrawable(host,AndroidUtilities.dp(13),AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS,foreground);emoji.setCurrentAccount(account);if(tag!=null)emoji.set(tag.optLong("emoji"),false);
    }
    public void attach(){emoji.attach();}public void detach(){emoji.detach();}
    public void color(int value){color=value;}
    @Override public int getIntrinsicWidth(){return width;}@Override public int getIntrinsicHeight(){return height;}
    @Override public void draw(Canvas canvas){
        Rect bounds=getBounds();int save=canvas.save();canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/(float)width,bounds.height()/(float)height);
        back.setColor((color&0xffffff)|(Math.round(alpha*0.18f)<<24));box.set(0,0,width,height);canvas.drawRoundRect(box,AndroidUtilities.dp(6),AndroidUtilities.dp(6),back);
        text.setColor((color&0xffffff)|(alpha<<24));int icon=AndroidUtilities.dp(13),left=AndroidUtilities.dp(5),top=(height-icon)/2;
        if(emoji.getDrawable()!=null){emoji.setColor(color|0xff000000);emoji.setAlpha(alpha);emoji.setBounds(left,top,left+icon,top+icon);emoji.draw(canvas);}else canvas.drawText("+",left+AndroidUtilities.dp(2),height/2f-(text.ascent()+text.descent())/2,text);
        canvas.drawText(label,left+icon+AndroidUtilities.dp(3),height/2f-(text.ascent()+text.descent())/2,text);canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

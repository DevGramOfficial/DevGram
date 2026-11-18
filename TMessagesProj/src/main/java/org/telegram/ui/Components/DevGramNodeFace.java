package org.telegram.ui.Components;
import android.graphics.Canvas;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
/** Native AnimatedEmoji lifecycle for the reference assistant faces. */
final class DevGramNodeFace {
    private static final long[] FACES={5467810048631658566L,5271803701340706125L,5467426001245984304L,5467832232137741705L,5361643529430928253L,5235592192117972239L,5314493395635285021L,5436402945660838021L};
    private static final java.util.HashSet<Integer> used=new java.util.HashSet<>();
    private final View host;private AnimatedEmojiDrawable emoji;
    DevGramNodeFace(View host){this.host=host;}
    void wear(int requested){clear();if(requested<0)return;int index=Math.floorMod(requested,FACES.length);synchronized(used){if(used.size()==FACES.length)used.clear();while(used.contains(index))index=(index+1)%FACES.length;used.add(index);}emoji=AnimatedEmojiDrawable.make(UserConfig.selectedAccount,AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE,FACES[index]);emoji.addView(host);wake();final AnimatedEmojiDrawable current=emoji;AndroidUtilities.runOnUIThread(()->{if(emoji==current)wake();},350);}
    void wake(){if(emoji!=null&&emoji.getImageReceiver()!=null){emoji.getImageReceiver().setAutoRepeat(1);emoji.getImageReceiver().startAnimation();}}
    void clear(){if(emoji!=null){emoji.removeView(host);emoji=null;}}
    boolean draw(Canvas canvas,float x,float y,float size){if(emoji==null)return false;int side=Math.round(size*1.6f);emoji.setBounds(Math.round(x),Math.round(y),Math.round(x)+side,Math.round(y)+side);emoji.draw(canvas);return true;}
}

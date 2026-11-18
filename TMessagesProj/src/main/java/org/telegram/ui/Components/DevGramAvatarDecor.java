package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.*;
import java.util.function.BooleanSupplier;

/** The same frame/shape scene in chat avatars, headers and user lists. Owned by its receiver. */
public final class DevGramAvatarDecor {
    private final DevGramProfilePainter painter=new DevGramProfilePainter();
    private final DevGramFrameStudioRenderer frame=new DevGramFrameStudioRenderer();
    private ImageReceiver receiver;private long owner;private boolean attached,result;
    private long nextStyleRead;
    private final Runnable policy=this::invalidate;
    private void invalidate(){if(receiver!=null){frame.release();View host=receiver.getParentView();if(host!=null)host.invalidate();}}
    public static boolean eligible(ImageReceiver receiver,long uid){View host=receiver.getParentView();return uid>0&&DevGramProfileCustomization.enabled()&&MessagesController.getGlobalMainSettings().getBoolean("devgramChatDecor",true)&&host!=null&&!(host instanceof org.telegram.ui.ProfileActivity.AvatarImageView)&&!(host instanceof DevGramStudioPreview)&&!(host instanceof DevGramAppearancePreview)&&receiver.getVisible()&&receiver.getImageWidth()>0&&Math.abs(receiver.getImageWidth()-receiver.getImageHeight())<AndroidUtilities.dp(2);}
    public boolean draw(Canvas canvas,ImageReceiver receiver,long uid,BooleanSupplier original){
        this.receiver=receiver;if(owner!=uid){owner=uid;frame.release();nextStyleRead=0;}
        if(!attached&&receiver.isAttachedToWindow()){attached=true;DevGramProfilePolicy.listen(policy);}
        View host=receiver.getParentView();long now=android.os.SystemClock.elapsedRealtime();if(now>=nextStyleRead){nextStyleRead=now+30000;DevGramProfileStyle.load(uid,null);}
        JSONObject document=DevGramProfileDocument.get(uid);DevGramProfileStyle.Style style=DevGramProfileStyle.get(uid);float x=receiver.getImageX(),y=receiver.getImageY(),size=receiver.getImageWidth();
        boolean hasFrame=style.get("frame")!=0||document.has("layers")||document.has("frameGraph"),shape=style.get("shape")!=0||document.has("avatarAlpha")||document.has("avatarDim")||document.has("avatarFade");
        if(!hasFrame&&!shape){frame.release();return original.getAsBoolean();}
        if(shape){int[] corners=receiver.getRoundRadius().clone();try{receiver.setRoundRadiusDirect(0);painter.avatar(canvas,x,y,size,style.get("shape"),document,()->result=original.getAsBoolean());}finally{receiver.setRoundRadiusDirect(corners[0],corners[1],corners[2],corners[3]);}}
        else result=original.getAsBoolean();
        if(!result)return false;
        boolean motion=painter.motion()&&!MessagesController.getGlobalMainSettings().getBoolean("devgramBakeOutside",false);
        int save=canvas.saveLayerAlpha(x-size/2,y-size/2,x+size*1.5f,y+size*1.5f,Math.round(receiver.getAlpha()*255),Canvas.ALL_SAVE_FLAG);
        painter.setAvatarDocument(document);painter.frame(canvas,x,y,size,style,motion);frame.setSources(host,receiver,uid,receiver.getCurrentAccount());frame.draw(canvas,x,y,size,style.get("shape"),document,motion);canvas.restoreToCount(save);
        if(motion&&(style.get("frame")>=3||document.has("layers")||document.has("frameGraph"))&&host.isShown()&&host.hasWindowFocus())host.postInvalidateDelayed(33);
        return result;
    }
    public void release(){DevGramProfilePolicy.unlisten(policy);attached=false;frame.release();receiver=null;}
}

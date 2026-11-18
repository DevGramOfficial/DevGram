package org.telegram.ui.Components;

import android.graphics.*;
import android.view.View;
import org.json.JSONObject;
import org.telegram.messenger.*;

/** Image behind row content, aligned right with the reference's left fade and crop focus. */
public final class DevGramChatBadgeRenderer {
    private final View host;
    private ImageReceiver receiver;
    private String source="";
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix=new Matrix();
    private Bitmap bitmap;
    private int bitmapGeneration,lastWidth,lastHeight;
    private String style="";
    private Shader shader;
    private final DevGramProfilePainter motion=new DevGramProfilePainter();
    public DevGramChatBadgeRenderer(View host){this.host=host;}
    public void draw(Canvas canvas,int account,long uid){
        if(uid<=0||!DevGramProfileCustomization.enabled()||!MessagesController.getGlobalMainSettings().getBoolean("devgramChatBadges",true)){release();return;}
        draw(canvas,account,DevGramProfileDocument.get(uid),host.getWidth(),host.getHeight());
    }
    public Bitmap bitmap(){return receiver==null?null:receiver.getBitmap();}
    public void draw(Canvas canvas,int account,JSONObject document,int width,int height){
        String next=document.optString("chatBadgeSource");
        if(document.optInt("chatBadgeEnabled")==0||!next.matches("asset:[a-f0-9]{64}\\.(png|jpg|gif|webp)")||width<=0||height<=0){release();return;}
        if(!next.equals(source)||receiver==null){
            release();source=next;receiver=new ImageReceiver(host);receiver.setCurrentAccount(account);receiver.setAllowStartAnimation(false);receiver.setAutoRepeat(1);receiver.onAttachedToWindow();String asset=next.substring(6);receiver.setImage(DevGramProfilePolicy.BASE+"studio/textures/"+asset,document.optInt("chatBadgeMediaType")==1?"512_256_g":"512_256",null,asset.substring(asset.lastIndexOf('.')+1),0);
        }
        boolean animate=document.optInt("chatBadgeMediaType")==1&&motion.motion()&&host.isShown()&&host.hasWindowFocus()&&!DevGramCustomizationMemory.tight();
        receiver.setAllowStartAnimation(animate);
        AnimatedFileDrawable animation=receiver.getAnimation();
        if(animation!=null){if(animate&&!animation.isRunning())receiver.startAnimation();else if(!animate&&animation.isRunning())receiver.stopAnimation();}
        Bitmap image=receiver.getBitmap();if(image==null||image.isRecycled())return;
        int fraction=Math.max(35,Math.min(100,document.optInt("chatBadgeWidth",65))),alpha=Math.max(0,Math.min(100,document.optInt("chatBadgeAlpha",70))),fade=Math.max(15,Math.min(100,document.optInt("chatBadgeFade",100))),x=Math.max(0,Math.min(100,document.optInt("chatBadgeX",50))),y=Math.max(0,Math.min(100,document.optInt("chatBadgeY",50)));
        String key=fraction+":"+alpha+":"+fade+":"+x+":"+y;
        float badgeWidth=width*fraction/100f,left=width-badgeWidth;
        if(bitmap!=image||bitmapGeneration!=image.getGenerationId()||lastWidth!=width||lastHeight!=height||!style.equals(key)){
            bitmap=image;bitmapGeneration=image.getGenerationId();lastWidth=width;lastHeight=height;style=key;
            float scale=Math.max(badgeWidth/image.getWidth(),height/(float)image.getHeight());matrix.reset();matrix.setScale(scale,scale);matrix.postTranslate(left-(image.getWidth()*scale-badgeWidth)*x/100f,-(image.getHeight()*scale-height)*y/100f);
            BitmapShader texture=new BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);texture.setLocalMatrix(matrix);
            Shader mask=new LinearGradient(left,0,left+badgeWidth*fade/100f,0,new int[]{0x00ffffff,(alpha*255/100<<24)|0xffffff},null,Shader.TileMode.CLAMP);
            shader=new ComposeShader(texture,mask,PorterDuff.Mode.DST_IN);
        }
        paint.setShader(shader);canvas.drawRect(left,0,width,height,paint);paint.setShader(null);
    }
    public void release(){if(receiver!=null){receiver.onDetachedFromWindow();receiver.clearImage();receiver=null;}source="";bitmap=null;shader=null;}
}

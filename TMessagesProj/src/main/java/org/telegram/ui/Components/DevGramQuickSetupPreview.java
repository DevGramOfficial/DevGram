package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.devgram.frame.*;
import java.util.Arrays;

/** Live reference quick-setup sample, rendered with native frames and message drawable. */
public final class DevGramQuickSetupPreview extends FrameLayout {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Bitmap landscape=landscape(384,160);
    private final FrameSpec example;
    private final Card avatar,profile;
    private final TextView message;
    private final MessageDrawable bubble;
    private final Theme.ResourcesProvider resources;
    private boolean badge,customBubble;
    public DevGramQuickSetupPreview(Context context,int account,long uid,Theme.ResourcesProvider resources){
        super(context);this.resources=resources;setWillNotDraw(false);setClipChildren(false);int accent=color(Theme.key_windowBackgroundWhiteBlueText);
        example=FrameSpec.of(Arrays.asList(new FrameSpec.Builder().src(FrameBlanks.RIM).width(7).tint(accent).build(),new FrameSpec.Builder().src(FrameBlanks.SPARK).mode(1).repeat(9).width(10).offset(3).spin(35).tint(accent).build()));
        avatar=new Card(context,account,uid,1);addView(avatar,LayoutHelper.createFrame(66,66,Gravity.LEFT|Gravity.TOP,2,6,0,0));
        addView(text(DevGramLocale.text("Алекс · пример профиля"),16,true),LayoutHelper.createFrame(-1,30,Gravity.TOP|Gravity.LEFT,77,12,12,0));
        addView(text(DevGramLocale.text("Так выглядит список чатов"),12,false),LayoutHelper.createFrame(-1,26,Gravity.TOP|Gravity.LEFT,77,40,12,0));
        bubble=new MessageDrawable(MessageDrawable.TYPE_TEXT,true,false,resources);message=text(DevGramLocale.text("Привет! Это пример сообщения"),14,false);message.setPadding(dp(14),0,dp(14),0);message.setBackground(new PreviewBubble());addView(message,LayoutHelper.createFrame(-1,48,Gravity.TOP|Gravity.LEFT,12,99,100,0));
        profile=new Card(context,account,uid,2);addView(profile,LayoutHelper.createFrame(72,72,Gravity.RIGHT|Gravity.TOP,0,80,6,0));TextView caption=text(DevGramLocale.text("В профиле"),10,false);caption.setGravity(Gravity.CENTER);addView(caption,LayoutHelper.createFrame(88,18,Gravity.RIGHT|Gravity.TOP,0,148,0,0));refresh();
    }
    private static int dp(float v){return AndroidUtilities.dp(v);}
    private int color(int key){return Theme.getColor(key,resources);}
    private TextView text(String text,int size,boolean bold){TextView v=DevGramStudioControls.text(getContext(),"",size,bold,resources);v.setText(text);v.setSingleLine();v.setEllipsize(android.text.TextUtils.TruncateAt.END);v.setGravity(Gravity.CENTER_VERTICAL);v.setTextColor(color(bold?Theme.key_windowBackgroundWhiteBlackText:Theme.key_windowBackgroundWhiteGrayText));return v;}
    public void refresh(){android.content.SharedPreferences p=MessagesController.getGlobalMainSettings();badge=p.getBoolean("devgramChatBadges",true);customBubble=p.getBoolean("devgramChatBubbles",true);avatar.frames=p.getBoolean("devgramChatDecor",true);avatar.motion=!p.getBoolean("devgramBakeOutside",false);profile.motion=!p.getBoolean("devgramBakeProfile",false);message.setTextColor(customBubble?Color.WHITE:color(Theme.key_chat_messageTextOut));message.getBackground().invalidateSelf();avatar.invalidate();profile.invalidate();invalidate();}
    @Override protected void onDraw(Canvas canvas){canvas.drawColor(color(Theme.key_windowBackgroundGray));if(badge){fill.setAlpha(180);canvas.drawBitmap(landscape,null,new RectF(0,0,getWidth(),dp(80)),fill);fill.setAlpha(255);}fill.setColor(color(Theme.key_divider));canvas.drawRect(dp(12),dp(80),getWidth()-dp(12),dp(80)+1,fill);}
    private final class Card extends View {
        final DevGramTypedFrameRenderer renderer=new DevGramTypedFrameRenderer();final JSONObject document=new JSONObject();final int shape;final long uid;final int account;boolean motion=true,frames=true;
        Card(Context context,int account,long uid,int shape){super(context);this.shape=shape;this.uid=uid;this.account=account;}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();renderer.sources(this,null,uid,account);}
        @Override protected void onDetachedFromWindow(){renderer.release();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){float left=getWidth()*.13f,top=getHeight()*.13f,size=Math.min(getWidth(),getHeight())*.74f;int save=canvas.save();Path clip=new DevGramProfilePainter().avatarPath(left,top,size,shape);canvas.clipPath(clip);canvas.drawBitmap(landscape,null,new RectF(left,top,left+size,top+size),fill);canvas.restoreToCount(save);if(frames)renderer.drawSpec(canvas,left,top,size,shape,document,example,motion&&isShown()&&hasWindowFocus());}
    }
    private final class PreviewBubble extends Drawable {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);private int alpha=255;
        PreviewBubble(){ink.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));}
        @Override public void draw(Canvas canvas){bubble.setBounds(getBounds());bubble.setAlpha(alpha);if(!customBubble){bubble.draw(canvas);return;}int save=canvas.saveLayer(new RectF(getBounds()),null);bubble.draw(canvas);ink.setShader(new LinearGradient(getBounds().left,getBounds().centerY(),getBounds().right,getBounds().centerY(),-12425019,-8955718,Shader.TileMode.CLAMP));ink.setAlpha(alpha);canvas.drawRect(getBounds(),ink);canvas.restoreToCount(save);}
        @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    public void release(){avatar.renderer.release();profile.renderer.release();}
    private static Bitmap landscape(int i, int i2) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        canvas.scale(i / 384.0f, i2 / 160.0f);
        Paint paint = new Paint(1);
        paint.setShader(new LinearGradient(0.0f, 0.0f, 384.0f, 160.0f, -13349757, -5203248, Shader.TileMode.CLAMP));
        canvas.drawRect(0.0f, 0.0f, 384.0f, 160.0f, paint);
        paint.setShader(null);
        paint.setColor(-9052);
        canvas.drawCircle(275.0f, 48.0f, 24.0f, paint);
        Path path = new Path();
        path.moveTo(0.0f, 132.0f);
        path.lineTo(78.0f, 65.0f);
        path.lineTo(144.0f, 127.0f);
        path.lineTo(214.0f, 81.0f);
        path.lineTo(303.0f, 140.0f);
        path.lineTo(350.0f, 108.0f);
        path.lineTo(384.0f, 131.0f);
        path.lineTo(384.0f, 160.0f);
        path.lineTo(0.0f, 160.0f);
        path.close();
        paint.setColor(-12428168);
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(0.0f, 146.0f);
        path.quadTo(88.0f, 105.0f, 182.0f, 144.0f);
        path.quadTo(285.0f, 172.0f, 384.0f, 130.0f);
        path.lineTo(384.0f, 160.0f);
        path.lineTo(0.0f, 160.0f);
        path.close();
        paint.setColor(-14074277);
        canvas.drawPath(path, paint);
        return bitmapCreateBitmap;
    }

}

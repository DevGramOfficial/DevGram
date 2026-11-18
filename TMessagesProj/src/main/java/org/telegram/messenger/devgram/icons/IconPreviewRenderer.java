package org.telegram.messenger.devgram.icons;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** On-demand image preview; never resizes or recolors the installed resource. */
public final class IconPreviewRenderer {
    private IconPreviewRenderer() {}

    public static int[] fit(int width,int height,int maxWidth,int maxHeight){
        width=Math.max(1,width);height=Math.max(1,height);
        float scale=Math.min(maxWidth/(float)width,maxHeight/(float)height);
        return new int[]{Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale))};
    }
    public static int[] size(int width,int height,boolean scalable,int rotation){
        width=Math.max(1,width);height=Math.max(1,height);
        int limit=scalable?1024:Math.min(2048,Math.max(width,height));
        int[] fitted=fit(width,height,limit,limit);
        return rotation%180==0?fitted:new int[]{fitted[1],fitted[0]};
    }

    public static Bitmap render(Drawable drawable,int rotation,int invert){
        boolean raster=drawable instanceof BitmapDrawable;
        int width=raster?((BitmapDrawable)drawable).getBitmap().getWidth():drawable.getIntrinsicWidth();
        int height=raster?((BitmapDrawable)drawable).getBitmap().getHeight():drawable.getIntrinsicHeight();
        int[] size=size(width,height,!raster,rotation);
        Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
        try{
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xffd9dde3);
        Paint paint=new Paint();paint.setColor(0xffaeb5bf);
        int step=Math.max(8,Math.min(size[0],size[1])/16);
        for(int y=0;y<size[1];y+=step)for(int x=0;x<size[0];x+=step)if((x/step+y/step)%2==0)canvas.drawRect(x,y,Math.min(size[0],x+step),Math.min(size[1],y+step),paint);
        canvas.save();canvas.translate(size[0]/2f,size[1]/2f);canvas.rotate(rotation);canvas.scale(invert==1?-1:1,invert==2?-1:1);
        int w=rotation%180==0?size[0]:size[1],h=rotation%180==0?size[1]:size[0];
        drawable.setBounds(-w/2,-h/2,-w/2+w,-h/2+h);drawable.draw(canvas);canvas.restore();
        return bitmap;
        }catch(RuntimeException|Error error){bitmap.recycle();throw error;}
    }
}

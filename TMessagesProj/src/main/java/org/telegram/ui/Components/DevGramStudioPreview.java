package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.View;
import android.view.MotionEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Same frame/background renderer as ProfileActivity; export never captures private profile rows. */
public class DevGramStudioPreview extends View {
    private final int account;
    private final long uid;
    private final DevGramProfilePainter painter = new DevGramProfilePainter(1f);
    private final DevGramFrameStudioRenderer frames = new DevGramFrameStudioRenderer();
    private final DevGramProfileSurfaces surfaces;
    private final DevGramNameEffects nameEffects = new DevGramNameEffects();
    private final DevGramThoughtView thought;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final ImageReceiver avatar = new ImageReceiver(this);
    private final ImageReceiver background = new ImageReceiver(this);
    private int backgroundMsg = -1;
    private JSONObject doc = new JSONObject();
    private JSONObject rawDocument;
    private DevGramProfileStyle.Style theme;
    private boolean still, frameOnly;
    private int framePreviewShape=-1;
    public void setFramePreviewShape(int shape){framePreviewShape=shape;invalidate();}
    private boolean edgeToEdge;
    public void setEdgeToEdge(boolean value) { edgeToEdge=value;invalidate(); }
    private final DevGramProfilePageRenderer pageSurface,headerSurface;
    public void setStill(boolean value) { still=value;pageSurface.setStill(value);headerSurface.setStill(value); }
    public void setFrameOnly(boolean value) { boolean changed=frameOnly!=value;frameOnly=value;if(changed&&rawDocument!=null)set(rawDocument,theme);else invalidate(); }
    public interface Editor {
        boolean select(int element);
        void move(int element, int x, int y, boolean stopped);
    }
    private Editor editor;
    private int selectedElement = -1;
    private final RectF[] targets = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF[] nativeTargets = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
    private final float[] headerX = new float[5], headerY = new float[5], headerScaleX = new float[5], headerScaleY = new float[5];
    private final float[] headerPivotX = new float[5], headerPivotY = new float[5], headerOriginX = new float[5], headerOriginY = new float[5];
    private float touchX, touchY;
    private int startX, startY;
    public void setEditor(Editor value) { editor = value; }
    public void selectElement(int element) { selectedElement = element; invalidate(); }

    public DevGramStudioPreview(Context context, int account, long uid) {
        super(context);avatar.setCurrentAccount(account); this.account = account; this.uid = uid;thought=new DevGramThoughtView(context);surfaces=new DevGramProfileSurfaces(this,uid);
        pageSurface=new DevGramProfilePageRenderer(this,account,uid);headerSurface=new DevGramProfilePageRenderer(this,account,uid,"banner");
        org.telegram.tgnet.TLRPC.User user = uid>0?MessagesController.getInstance(account).getUser(uid):null;
        org.telegram.tgnet.TLRPC.Chat chat = uid<0?MessagesController.getInstance(account).getChat(org.telegram.messenger.DevGramCommunityProfiles.chatId(uid)):null;
        AvatarDrawable fallback = new AvatarDrawable(); if(user!=null)fallback.setInfo(account,user);else if(chat!=null)fallback.setInfo(account,chat);
        org.telegram.tgnet.TLObject peer=user!=null?user:chat;
        avatar.setImage(ImageLocation.getForUserOrChat(peer, ImageLocation.TYPE_SMALL), "100_100", fallback, null, peer, 0);
        setContentDescription(DevGramLocale.text("Предпросмотр профиля"));
    }
    private String displayName(){if(uid>0)return UserObject.getFirstName(MessagesController.getInstance(account).getUser(uid));org.telegram.tgnet.TLRPC.Chat chat=MessagesController.getInstance(account).getChat(org.telegram.messenger.DevGramCommunityProfiles.chatId(uid));return chat==null?"":chat.title;}
    public void set(JSONObject raw, DevGramProfileStyle.Style theme) {
        rawDocument=raw;
        doc = DevGramProfileDocument.effective(raw, uid);nameEffects.set(doc,1);thought.set(doc);thought.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(154),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(180),MeasureSpec.AT_MOST));thought.layout(0,0,thought.getMeasuredWidth(),thought.getMeasuredHeight());
        this.theme = theme; invalidate();
        int selectedMessage=DevGramProfileMedia.message(doc,uid,"banner");int message=frameOnly||doc.has("bannerMode")?0:selectedMessage<0?DevGramBadges.backgroundOf(uid):selectedMessage;
        if (backgroundMsg != message) {
            backgroundMsg = message; background.clearImage();
            if (message > 0) DevGramProfileBackground.load(account, message, media -> {
                if (backgroundMsg != message || media == null) return;
                if((media.isVideo()||media.isGif())&&!DevGramProfilePolicy.allowed(uid,"profile.media.animated"))return;
                org.telegram.tgnet.TLRPC.Document file = media.getDocument();
                if (file != null) {
                    org.telegram.tgnet.TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(file.thumbs, 320);
                    if (still) { background.setImage(ImageLocation.getForDocument(thumb,file),"320_320",null,null,0,null,media,1);invalidate();return; }
                    background.setAllowStartAnimation(true); background.setAutoRepeat(1);
                    background.setImage(ImageLocation.getForDocument(file), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForDocument(thumb, file), "b", file.size, null, media, 1);
                } else if (media.messageOwner.media != null && media.messageOwner.media.photo != null) {
                    org.telegram.tgnet.TLRPC.Photo photo = media.messageOwner.media.photo;
                    org.telegram.tgnet.TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 800);
                    background.setImage(ImageLocation.getForObject(size, photo), null, null, size == null ? 0 : size.size, null, media, 1);
                }
                invalidate();
            });
        }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); avatar.onAttachedToWindow(); background.onAttachedToWindow();pageSurface.attach();headerSurface.attach(); }
    @Override protected void onDetachedFromWindow() { frames.release();avatar.onDetachedFromWindow(); background.onDetachedFromWindow();pageSurface.detach();headerSurface.detach(); super.onDetachedFromWindow(); }
    public void release() { frames.release();avatar.clearImage(); background.clearImage();pageSurface.detach();headerSurface.detach(); }
    @Override protected void onDraw(Canvas canvas) {
        float margin = edgeToEdge ? 0 : AndroidUtilities.dp(16), width = getWidth() - margin * 2;
        canvas.save(); canvas.translate(margin, margin);
        rect.set(0, 0, width, getHeight() - margin * 2); clip.reset(); clip.addRoundRect(rect, AndroidUtilities.dp(22), AndroidUtilities.dp(22), Path.Direction.CW); canvas.clipPath(clip);
        canvas.scale(width / 360f, width / 360f);
        draw(canvas, 360, (getHeight() - margin * 2) * 360 / width, -1, !still);
        canvas.restore();
        DevGramProfileStyle.Style style = theme == null ? DevGramProfileStyle.get(uid) : theme.effective(uid);
        if (!still) painter.animate(this, style);
        if (!still && doc.optJSONArray("layers") != null && isShown() && hasWindowFocus() && painter.motion()) postInvalidateDelayed(33);
    }
    private void draw(Canvas canvas, float width, float height, int layer, boolean motion) {
        DevGramProfileStyle.Style sourceStyle = theme == null ? DevGramProfileStyle.get(uid) : theme.effective(uid);
        final DevGramProfileStyle.Style style=frameOnly&&framePreviewShape>=0?sourceStyle.with("shape",framePreviewShape):sourceStyle;
        float avatarSize = 72 * doc.optInt("avatarScale", 100) / 100f, left = (width - avatarSize) / 2 + doc.optInt("avatarX"), top = 24 + doc.optInt("avatarY");
        if (doc.optInt("layout") == 2) { left = 24 + doc.optInt("avatarX"); avatarSize *= .8f; }
        if (frameOnly) { avatarSize=Math.min(width*.56f,height*.7f);left=(width-avatarSize)/2;top=(height-avatarSize)/2; }
        if (!frameOnly) prepareHeader(width, left, top, avatarSize);
        if (frameOnly && (layer < 0 || layer == 0)) {
            // Frame editing is independent of the profile banner and its header transforms.
            int back = org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundGray);
            paint.setColor(back); canvas.drawRect(0, 0, width, height, paint);
        }
        if (!frameOnly && (layer < 0 || layer == 0)) {
            paint.setColor(DevGramProfilePainter.PALETTES[style.get("palette")][0]); canvas.drawRect(0, 0, width, height, paint);
            if(doc.has("pageMode"))pageSurface.draw(canvas,(int)width,(int)height,doc);
            int bgSave=canvas.save();if(doc.has("pageMode"))canvas.clipRect(0,0,width,200);
            if(!doc.has("bannerMode"))painter.background(canvas, width, height, style);
            if(doc.has("bannerMode"))headerSurface.draw(canvas,(int)width,200,doc);
            painter.pattern(canvas, width, height, style);
            if (backgroundMsg > 0 && DevGramProfilePolicy.allowed(uid, DevGramProfileMedia.feature(doc,"banner"))) {
                background.setImageCoords(0, 0, width, height); background.draw(canvas);
                paint.setColor((style.get("dim") * 255 / 100) << 24); canvas.drawRect(0, 0, width, height, paint);
                painter.pattern(canvas, width, height, style);
            }
            canvas.restoreToCount(bgSave);
        }
        if(layer<0&& !frameOnly)surfaces.update(canvas,null,doc,(int)width,(int)height,scene->{
            paint.setColor(DevGramProfilePainter.PALETTES[style.get("palette")][0]);scene.drawRect(0,0,width,height,paint);
            if(doc.has("pageMode"))pageSurface.draw(scene,(int)width,(int)height,doc);
            int save=scene.save();if(doc.has("pageMode"))scene.clipRect(0,0,width,200);
            if(doc.has("bannerMode"))headerSurface.draw(scene,(int)width,200,doc);else painter.background(scene,width,height,style);
            if(backgroundMsg>0){background.setImageCoords(0,0,width,height);background.draw(scene);}painter.pattern(scene,width,height,style);scene.restoreToCount(save);
        });
        if (layer < 0 || layer == 1) {
            int partSave = frameOnly ? canvas.save() : beginPart(canvas, 0);
            avatar.setRoundRadius(0); avatar.setImageCoords(left, top, avatarSize, avatarSize);
            painter.avatar(canvas,left,top,avatarSize,style.get("shape"),doc,()->avatar.draw(canvas));
            painter.frame(canvas, left, top, avatarSize, style);
            frames.setSources(this,avatar,uid,account);frames.draw(canvas, left, top, avatarSize, style.get("shape"), doc, motion && painter.frameMotion());
            canvas.restoreToCount(partSave);
            if(!frameOnly&&!doc.optString("thoughtText").isEmpty()){
                int thoughtSave=canvas.save();canvas.translate(Math.max(4,Math.min(width-thought.getWidth()/AndroidUtilities.density-4,left+avatarSize*.65f)),Math.max(4,top-thought.getHeight()/AndroidUtilities.density+14));canvas.scale(1/AndroidUtilities.density,1/AndroidUtilities.density);thought.draw(canvas);canvas.restoreToCount(thoughtSave);
            }
        }
        if (!frameOnly && (layer < 0 || layer == 2)) {
            String name = displayName();
            float x = doc.optInt("layout") == 2 ? width * .65f : width / 2;
            float y = doc.optInt("layout") == 2 ? top + 25 : top + avatarSize + 26;
            paint.setColor(doc.optInt("nameColorEnabled",doc.optInt("nickColor")!=0?1:0)==0?previewColor("key_profile_title",Color.WHITE):0xff000000|doc.optInt("nickColor",0xffffff));
            paint.setTypeface(DevGramProfileDocument.nameFont(doc)); paint.setTextSize(21 * doc.optInt("nameScale", 100) / 100f * doc.optInt("nameSize",100) / 100f); paint.setTextAlign(Paint.Align.CENTER);
            String shown = TextUtils.ellipsize(name, new android.text.TextPaint(paint), width - 32, TextUtils.TruncateAt.END).toString();
            int nameSave = beginPart(canvas, 1);
            if(nameEffects.enabled())nameEffects.apply(paint,paint.measureText(shown),paint.getTextSize(),still);
            canvas.drawText(shown, x + doc.optInt("nameX"), y + doc.optInt("nameY"), paint);
            if(nameEffects.enabled())nameEffects.restore(paint);nameEffects.animate(this,still);
            canvas.restoreToCount(nameSave);
            paint.setTypeface(null); paint.setColor(previewColor("key_profile_status",0xcfffffff)); paint.setTextSize(12 * doc.optInt("statusScale", 100) / 100f);
            int statusSave = beginPart(canvas, 2);
            canvas.drawText(DevGramLocale.text("Ваш профиль в DevGram"), x + doc.optInt("statusX"), y + 21 + doc.optInt("statusY"), paint);
            canvas.restoreToCount(statusSave);
            if (!doc.optString("titleText").isEmpty()) {
                int titleSave = beginPart(canvas, 4);
                paint.setTextSize(12); paint.setTextAlign(Paint.Align.LEFT); paint.setColor(previewColor("key_profile_status",Color.WHITE));
                canvas.drawText(doc.optString("titleText"), nativeTargets[4].left, nativeTargets[4].bottom - 3, paint);
                canvas.restoreToCount(titleSave); paint.setTextAlign(Paint.Align.CENTER);
            }
            String badge = doc.optString("badge");
            if (!badge.isEmpty()) {
                paint.setTextSize(10); float bw = Math.min(width - 32, paint.measureText(badge) + 16);
                paint.setColor(0x445077cf); rect.set((width - bw) / 2, y + 30, (width + bw) / 2, y + 48); canvas.drawRoundRect(rect, 9, 9, paint);
                paint.setColor(Color.WHITE); canvas.drawText(TextUtils.ellipsize(badge, new android.text.TextPaint(paint), width - 48, TextUtils.TruncateAt.END).toString(), width / 2, y + 43, paint);
            }
        }
        if (!frameOnly && (layer < 0 || layer == 3)) {
            float buttonY = top + avatarSize + (doc.optString("badge").isEmpty() ? 62 : 86) + doc.optInt("buttonsY");
            float buttonScale = doc.optInt("buttonsScale", 100) / 100f;
            int buttonSave = beginPart(canvas, 3);
            paint.setColor(0x445077cf); rect.set(nativeTargets[3]);if(DevGramProfileSurfaces.buttonsPainted(doc,uid))surfaces.drawButtons(canvas,this,rect,doc.optInt("buttonShape")==1?15:8,1);else canvas.drawRoundRect(rect, doc.optInt("buttonShape") == 1 ? 15 : 8, doc.optInt("buttonShape") == 1 ? 15 : 8, paint);
            paint.setColor(previewColor("key_windowBackgroundWhiteBlackText",Color.WHITE)); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(11 * buttonScale);
            canvas.drawText(DevGramLocale.text("Сообщение") + "    ·    " + DevGramLocale.text("Звонок"), rect.centerX(), rect.centerY() + 4, paint);
            canvas.restoreToCount(buttonSave);
            float y = buttonY + 36;
            JSONArray blocks = doc.optJSONArray("blocks");
            int count = blocks == null || blocks.length() == 0 ? 2 : Math.min(4, blocks.length());
            for (int i = 0; i < count && y + 42 <= height - 8; i++) {
                int cardColor = !doc.has("cardColor") ? 0x22ffffff : 0xff000000 | doc.optInt("cardColor");
                int alpha = Color.alpha(cardColor) * doc.optInt("cardAlpha", 100) / 100;
                cardColor = (cardColor & 0xffffff) | alpha << 24;
                paint.setColor(cardColor);
                rect.set(16, y, width - 16, y + 42);if(DevGramProfileSurfaces.painted(doc))surfaces.drawPreviewCard(canvas,rect,doc.optInt("cardRadiusEnabled")==1?doc.optInt("cardRadius",12):12,i);else canvas.drawRoundRect(rect, 12, 12, paint);
                int surface = androidx.core.graphics.ColorUtils.compositeColors(cardColor, DevGramProfilePainter.PALETTES[style.get("palette")][0]);
                paint.setColor(androidx.core.graphics.ColorUtils.calculateLuminance(surface) < .4 ? Color.WHITE : 0xff181818);
                paint.setTextAlign(Paint.Align.LEFT); paint.setTextSize(12);
                JSONObject row=blocks==null?null:blocks.optJSONObject(i);
                String text = row==null ? DevGramLocale.text(i == 0 ? "О себе" : "Внешние профили") : row.optString("title");
                if(text.isEmpty()&&row!=null)text=row.optString("text");
                if(row!=null&&row.optInt("rowType")==12)text="♫  "+DevGramLocale.text("Сейчас слушаю");
                canvas.drawText(TextUtils.ellipsize(text, new android.text.TextPaint(paint), width - 64, TextUtils.TruncateAt.END).toString(), 28, y + 26, paint);
                y += 50;
            }
        }
    }

    private int previewColor(String key,int fallback){JSONObject palette=doc.optJSONObject("profilePalette");if(palette!=null&&palette.has(key))return palette.optInt(key,fallback);if(doc.optInt("profileTextEnabled")==1)return doc.optInt("profileTextColor",fallback);return fallback;}

    private void prepareHeader(float width, float left, float top, float avatarSize) {
        nativeTargets[0].set(left, top, left + avatarSize, top + avatarSize);
        float x = doc.optInt("layout") == 2 ? width * .65f : width / 2;
        float y = doc.optInt("layout") == 2 ? top + 25 : top + avatarSize + 26;
        paint.setTypeface(DevGramProfileDocument.nameFont(doc));paint.setTextSize(21 * doc.optInt("nameScale",100)/100f * doc.optInt("nameSize",100)/100f);
        String name = displayName();
        String shown = TextUtils.ellipsize(name,new android.text.TextPaint(paint),width-32,TextUtils.TruncateAt.END).toString();
        float half = Math.min(width/2-12,paint.measureText(shown)/2);
        nativeTargets[1].set(x-half+doc.optInt("nameX"),y-22+doc.optInt("nameY"),x+half+doc.optInt("nameX"),y+3+doc.optInt("nameY"));
        nativeTargets[2].set(x-90+doc.optInt("statusX"),y+8+doc.optInt("statusY"),x+90+doc.optInt("statusX"),y+26+doc.optInt("statusY"));
        float buttonY = top+avatarSize+(doc.optString("badge").isEmpty()?62:86)+doc.optInt("buttonsY");
        nativeTargets[3].set(28+doc.optInt("buttonsX"),buttonY,width-28+doc.optInt("buttonsX"),buttonY+26*doc.optInt("buttonsScale",100)/100f);
        paint.setTypeface(null);paint.setTextSize(12);
        nativeTargets[4].set(left,top-24,left+Math.min(width-32,paint.measureText(doc.optString("titleText"))),top-6);
        float[] sizesX=new float[5],sizesY=new float[5],offsetX=new float[5],offsetY=new float[5];
        int[] anchorX=new int[5],anchorY=new int[5],alignX=new int[5],alignY=new int[5];
        for(int i=0;i<5;i++){
            String key=DevGramHeaderGeometry.PARTS[i];RectF base=nativeTargets[i];
            headerScaleX[i]=frameOnly?1:doc.optInt(key+"ScaleX",100)/100f;headerScaleY[i]=frameOnly?1:doc.optInt(key+"ScaleY",100)/100f;
            headerPivotX[i]=i==0?base.left:base.centerX();headerPivotY[i]=i==0?base.top:base.centerY();
            headerOriginX[i]=base.left+(base.left-headerPivotX[i])*(headerScaleX[i]-1);
            headerOriginY[i]=base.top+(base.top-headerPivotY[i])*(headerScaleY[i]-1);
            sizesX[i]=base.width()*headerScaleX[i];sizesY[i]=base.height()*headerScaleY[i];
            if(!frameOnly){offsetX[i]=doc.optInt(key+"OffsetX");offsetY[i]=doc.optInt(key+"OffsetY");anchorX[i]=doc.optInt(key+"AnchorX");anchorY[i]=doc.optInt(key+"AnchorY");alignX[i]=doc.optInt(key+"AlignX");alignY[i]=doc.optInt(key+"AlignY");}
        }
        float[] resolvedX=DevGramHeaderGeometry.resolve(headerOriginX,sizesX,anchorX,alignX,offsetX,width);
        float[] resolvedY=DevGramHeaderGeometry.resolve(headerOriginY,sizesY,anchorY,alignY,offsetY,width);
        for(int i=0;i<5;i++){
            headerX[i]=resolvedX[i]-headerOriginX[i];headerY[i]=resolvedY[i]-headerOriginY[i];
            targets[i].set(resolvedX[i],resolvedY[i],resolvedX[i]+sizesX[i],resolvedY[i]+sizesY[i]);
        }
        if(doc.optString("titleText").isEmpty())targets[4].setEmpty();
    }
    private int beginPart(Canvas canvas,int part) {
        int saved=canvas.save();canvas.translate(headerX[part],headerY[part]);
        canvas.rotate(frameOnly?0:doc.optInt(DevGramHeaderGeometry.PARTS[part]+"Rotation"),headerPivotX[part],headerPivotY[part]);
        canvas.scale(headerScaleX[part],headerScaleY[part],headerPivotX[part],headerPivotY[part]);return saved;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (editor == null) return super.onTouchEvent(event);
        float factor = 360f / Math.max(1, getWidth() - AndroidUtilities.dp(32));
        float x = (event.getX() - AndroidUtilities.dp(16)) * factor, y = (event.getY() - AndroidUtilities.dp(16)) * factor;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int hit = -1; for (int i = targets.length - 1; i >= 0; i--) if (targets[i].contains(x, y)) { hit = i; break; }
            if (hit < 0 || !editor.select(hit)) return false;
            selectedElement = hit; touchX = x; touchY = y;
            String prefix = DevGramHeaderGeometry.PARTS[hit]; startX = doc.optInt(prefix + "OffsetX"); startY = doc.optInt(prefix + "OffsetY");
            getParent().requestDisallowInterceptTouchEvent(true); invalidate(); return true;
        }
        if (selectedElement < 0) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE || event.getActionMasked() == MotionEvent.ACTION_UP) editor.move(selectedElement, Math.max(-45, Math.min(45, Math.round(startX + (x - touchX) * 100 / 360))), Math.max(-45, Math.min(45, Math.round(startY + (y - touchY) * 100 / 360))), event.getActionMasked() == MotionEvent.ACTION_UP);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) { getParent().requestDisallowInterceptTouchEvent(false); performClick(); }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }

    public byte[] export(boolean layers) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (layers) {
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                String[] names = {"01-background.png", "02-avatar-frame.png", "03-name-status.png", "04-cards.png"};
                for (int layer = 0; layer < names.length; layer++) {
                    zip.putNextEntry(new ZipEntry(names[layer])); Bitmap bitmap = bitmap(layer); try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip); } finally { bitmap.recycle(); } zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry("profile.dgprofile"));
                zip.write(org.telegram.ui.DevGramStudioActivity.json("format", "devgram-profile-1", "document", doc,
                        "theme", (theme == null ? DevGramProfileStyle.get(uid) : theme).json()).toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        } else { Bitmap bitmap = bitmap(-1); try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); } finally { bitmap.recycle(); } }
        return bytes.toByteArray();
    }
    public byte[] portrait() {
        Bitmap bitmap=Bitmap.createBitmap(360,812,Bitmap.Config.ARGB_8888);
        try { draw(new Canvas(bitmap),360,812,-1,false);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);return bytes.toByteArray(); }
        finally { bitmap.recycle(); }
    }
    private Bitmap bitmap(int layer) {
        Bitmap bitmap = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.scale(3, 3); draw(canvas, 360, 480, layer, false); return bitmap;
    }
}

package org.telegram.ui;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.view.*;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.ArrayList;

/** The reference image badge controls, using the exact same renderer as user rows. */
public final class DevGramChatBadgeActivity extends DevGramSettingsFragment {
    private final DevGramStudioActivity.Draft draft;
    private UniversalRecyclerView list;
    private Sample sample;
    private Focus focus;
    private long owner;
    private boolean closed,uploading,saving,badgeDirty;
    private final boolean dirtyBeforeBadge;
    private JSONObject savedBadge;
    private int generation;
    private int mediaType;
    private static final int PICK=7392;
    private final Runnable live=()->{if((!DevGramProfileCustomization.enabled()||owner!=0&&getUserConfig().getClientUserId()!=owner)&&!closed){dismissCurrentDialog();DevGramAccessNavigation.close(this);return;}if(list!=null)list.adapter.update(true);if(sample!=null)sample.invalidate();if(focus!=null)focus.invalidate();};
    public DevGramChatBadgeActivity(DevGramStudioActivity.Draft draft){this.draft=draft;mediaType=draft.document.optInt("chatBadgeMediaType");dirtyBeforeBadge=draft.dirty;savedBadge=DevGramProfileDocument.copy(draft.document);}
    private String t(String value){return DevGramLocale.text(value);}
    private String activeFeature(){return draft.document.optInt("chatBadgeMediaType")==1?"chat.badge.animated":"chat.badge";}
    private boolean allowed(){return !closed&&!saving&&getUserConfig().getClientUserId()==owner&&(DevGramAppearanceAccess.allowed(this,"chat.badge")||DevGramAppearanceAccess.allowed(this,"chat.badge.animated"));}
    @Override public View createView(Context context){owner=getUserConfig().getClientUserId();actionBar.setTitle(t("Бейджик профиля"));actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1&&onBackPressed(true))finishFragment();else if(id==100)publish(false);}});actionBar.createMenu().addItem(100,R.drawable.ic_ab_done).setContentDescription(t("Сохранить бейджик"));sample=new Sample(context);focus=new Focus(context);list=new UniversalRecyclerView(this,this::fill,this::click,null);fragmentView=new DevGramSettingsLayout(context,actionBar,list,resourceProvider);DevGramProfilePolicy.listen(live);return fragmentView;}
    private void fill(ArrayList<UItem> items,UniversalAdapter adapter){
        items.add(UItem.asCustom(sample,96));items.add(UItem.asCheck(1,DevGramAppearanceAccess.label(this,activeFeature(),t("Показывать бейджик"))).setChecked(draft.document.optInt("chatBadgeEnabled")==1));
        CharSequence[] types={DevGramAppearanceAccess.label(this,activeFeature(),t("Статичный фон")),DevGramAppearanceAccess.label(this,"chat.badge.animated",t("Анимированный фон"))};
        items.add(UItem.asCustom(DevGramStudioControls.choices(getParentActivity(),"media",new String[]{"Статичный фон","Анимированный фон"},types,mediaType,draft.theme,resourceProvider,value->{if(!uploading&&DevGramAppearanceAccess.check(this,value==1?"chat.badge.animated":"chat.badge")){mediaType=value;live.run();}})));
        items.add(UItem.asButton(2,R.drawable.msg_gallery,DevGramAppearanceAccess.label(this,mediaType==1?"chat.badge.animated":"chat.badge",t(uploading?"Загружаю изображение…":"Выбрать изображение"))));
        items.add(UItem.asHeader(t("Фокус изображения")));items.add(UItem.asCustom(focus,180));
        slider(items,"chatBadgeWidth","Ширина",35,100,65);slider(items,"chatBadgeAlpha","Непрозрачность",0,100,70);slider(items,"chatBadgeFade","Длина перехода",15,100,100);
        items.add(UItem.asShadow(t("Изображение появляется справа в вашей строке списка чатов и плавно растворяется слева. Имя, сообщения и счётчики остаются поверх него.")));
        items.add(UItem.asShadow(t("Нажмите «Сохранить бейджик», чтобы изменения появились у других. Выберите PNG, JPEG, WebP или GIF до 5 МБ.")));items.add(UItem.asButton(4,DevGramAppearanceAccess.label(this,activeFeature(),t(saving?"Сохраняю бейджик…":"Сохранить бейджик"))).setEnabled(!saving&&!uploading));items.add(UItem.asButton(3,t("Убрать изображение")));
    }
    private void slider(ArrayList<UItem> items,String key,String title,int min,int max,int fallback){DevGramStudioControls.Slider slider=new DevGramStudioControls.Slider(getParentActivity(),resourceProvider);slider.bind(DevGramAppearanceAccess.label(this,activeFeature(),t(title)),draft.document.optInt(key,fallback),min,max,"%",allowed(),value->put(key,value),()->{},()->DevGramAppearanceAccess.check(this,activeFeature()));items.add(UItem.asCustom(slider,82));}
    private void put(String key,Object value){if(!allowed())return;if(!badgeDirty)savedBadge=DevGramProfileDocument.copy(draft.document);try{draft.document.put(key,value);draft.dirty=true;badgeDirty=true;if(draft.onChanged!=null)draft.onChanged.run();sample.invalidate();focus.invalidate();}catch(Exception ignored){}}
    private void click(UItem item,View view,int position,float x,float y){if(item.id==4){publish(false);return;}if(!allowed())return;if(item.id==1){put("chatBadgeEnabled",draft.document.optInt("chatBadgeEnabled")==1?0:1);live.run();}else if(item.id==2&&!uploading){if(!DevGramAppearanceAccess.check(this,mediaType==1?"chat.badge.animated":"chat.badge"))return;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);try{getParentActivity().startActivityForResult(intent,PICK);}catch(Exception e){error("Не удалось открыть выбор файла");}}else if(item.id==3&&!uploading){put("chatBadgeSource",null);put("chatBadgeMediaType",0);put("chatBadgeEnabled",0);live.run();}}
    @Override public void onActivityResultFragment(int requestCode,int resultCode,Intent data){if(requestCode!=PICK||resultCode!=Activity.RESULT_OK||data==null||data.getData()==null||!allowed())return;int request=++generation;uploading=true;live.run();DevGramProfileTextures.upload(currentAccount,data.getData(),mediaType==1?"badge_animated":"badge_static",(result,error)->{if(closed||request!=generation)return;uploading=false;if(result!=null&&allowed()&&DevGramAppearanceAccess.allowed(this,result.optInt("frames")>1?"chat.badge.animated":"chat.badge")){put("chatBadgeMediaType",result.optInt("frames")>1?1:0);put("chatBadgeSource",result.optString("source"));put("chatBadgeEnabled",1);}else error("Не удалось загрузить изображение");live.run();});}
    private void replaceBadge(JSONObject source) {
        JSONObject next=DevGramProfileDocument.copy(draft.document);
        try { for(String key:DevGramProfileDocument.BADGE_FIELDS){if(source.has(key))next.put(key,source.opt(key));else next.remove(key);} }catch(Exception ignored){}
        draft.document=next;
    }
    private void publish(boolean exit) {
        if(saving||uploading||closed||getUserConfig().getClientUserId()!=owner)return;
        if(!DevGramAppearanceAccess.check(this,draft.document.optInt("chatBadgeMediaType")==1?"chat.badge.animated":"chat.badge"))return;
        if(!badgeDirty){if(exit)finishFragment();return;}
        saving=true;live.run();
        DevGramProfileDocument.saveBadge(currentAccount,draft.subject,draft.document,savedBadge,(result,failure)->{
            if(closed||getUserConfig().getClientUserId()!=owner)return;
            saving=false;
            if(result==null||result.optJSONObject("document")==null){error("Не удалось сохранить бейджик");live.run();return;}
            replaceBadge(result.optJSONObject("document"));
            savedBadge=DevGramProfileDocument.copy(draft.document);badgeDirty=false;draft.dirty=dirtyBeforeBadge;
            if(draft.onChanged!=null)draft.onChanged.run();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,t("Бейджик сохранён")).show();
            live.run();if(exit)finishFragment();
        });
    }
    @Override public boolean onBackPressed(boolean invoked) {
        if(saving)return false;
        if(!badgeDirty)return super.onBackPressed(invoked);
        if(invoked){
            AlertDialog.Builder dialog=new AlertDialog.Builder(getParentActivity(),resourceProvider);
            dialog.setTitle(t("Сохранить изменения бейджика?"));
            dialog.setPositiveButton(t("Сохранить"),(d,w)->publish(true));
            dialog.setNegativeButton(t("Не сохранять"),(d,w)->{replaceBadge(savedBadge);draft.dirty=dirtyBeforeBadge;badgeDirty=false;if(draft.onChanged!=null)draft.onChanged.run();finishFragment();});
            dialog.setNeutralButton(DevGramLocale.getString(R.string.Cancel),null);showDialog(dialog.create());
        }
        return false;
    }
    private void error(String message){if(getParentActivity()!=null)BulletinFactory.of(this).createErrorBulletin(t(message)).show();}
    private final class Sample extends View{
        final DevGramChatBadgeRenderer badge=new DevGramChatBadgeRenderer(this);final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Sample(Context context){super(context);setContentDescription(t("Предпросмотр бейджика"));}
        @Override protected void onDraw(Canvas canvas){canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite,resourceProvider));badge.draw(canvas,currentAccount,DevGramProfileDocument.effective(draft.document,owner),getWidth(),getHeight());float density=AndroidUtilities.density;paint.setColor(0xff5077cf);canvas.drawCircle(40*density,getHeight()/2f,24*density,paint);paint.setColor(Color.WHITE);paint.setTextSize(22*density);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("D",40*density,getHeight()/2f+8*density,paint);paint.setTextAlign(Paint.Align.LEFT);paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,resourceProvider));paint.setTypeface(AndroidUtilities.bold());paint.setTextSize(17*density);canvas.drawText("DevGram",78*density,36*density,paint);paint.setTypeface(null);paint.setTextSize(14*density);paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resourceProvider));canvas.drawText(t("Так выглядит ваша строка профиля"),78*density,61*density,paint);paint.setTextSize(12*density);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText("12:30",getWidth()-16*density,32*density,paint);paint.setTextAlign(Paint.Align.LEFT);}
        @Override protected void onDetachedFromWindow(){badge.release();super.onDetachedFromWindow();}
    }
    private final class Focus extends View{
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float x,y;
        private ImageReceiver image;private String source="";
        private Bitmap bitmap(){String selected=DevGramProfileDocument.effective(draft.document,owner).optString("chatBadgeSource");if(!selected.matches("asset:[a-f0-9]{64}\\.(png|jpg|gif|webp)")){release();return null;}if(image==null||!source.equals(selected)){release();source=selected;image=new ImageReceiver(this);image.setCurrentAccount(currentAccount);image.setAllowStartAnimation(false);image.onAttachedToWindow();image.setImage(DevGramProfileTextures.imageUrl(selected),"512_256",null,selected.substring(selected.lastIndexOf('.')+1),0);}return image.getBitmap();}
        void release(){if(image!=null){image.onDetachedFromWindow();image.clearImage();image=null;}source="";}
        @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
        Focus(Context context){super(context);setContentDescription(t("Перетащите фокус изображения"));}
        @Override protected void onDraw(Canvas canvas){canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray,resourceProvider));Bitmap bitmap=bitmap();if(bitmap!=null&&!bitmap.isRecycled()){float scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight()),width=bitmap.getWidth()*scale,height=bitmap.getHeight()*scale;canvas.drawBitmap(bitmap,null,new RectF((getWidth()-width)/2,(getHeight()-height)/2,(getWidth()+width)/2,(getHeight()+height)/2),paint);}paint.setColor(0xff5077cf);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(AndroidUtilities.dp(2));float cx=getWidth()*draft.document.optInt("chatBadgeX",50)/100f,cy=getHeight()*draft.document.optInt("chatBadgeY",50)/100f;canvas.drawCircle(cx,cy,AndroidUtilities.dp(12),paint);canvas.drawLine(cx-AndroidUtilities.dp(18),cy,cx+AndroidUtilities.dp(18),cy,paint);canvas.drawLine(cx,cy-AndroidUtilities.dp(18),cx,cy+AndroidUtilities.dp(18),paint);paint.setStyle(Paint.Style.FILL);}
        @Override public boolean onTouchEvent(MotionEvent event){if(!allowed())return true;if(event.getActionMasked()==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE){put("chatBadgeX",Math.max(0,Math.min(100,Math.round(event.getX()*100/Math.max(1,getWidth())))));put("chatBadgeY",Math.max(0,Math.min(100,Math.round(event.getY()*100/Math.max(1,getHeight())))));return true;}if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
    @Override public void onResume(){super.onResume();live.run();}
    @Override public void onFragmentDestroy(){closed=true;generation++;DevGramProfilePolicy.unlisten(live);if(sample!=null)sample.badge.release();if(focus!=null)focus.release();super.onFragmentDestroy();}
}

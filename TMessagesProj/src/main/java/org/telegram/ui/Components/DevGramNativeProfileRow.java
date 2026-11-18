package org.telegram.ui.Components;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.util.Linkify;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Cells.*;

/** The reference row types rendered by Telegram cells, also used by the type catalog. */
public final class DevGramNativeProfileRow extends FrameLayout {
    private final int account,type;
    private final long owner;
    private final JSONObject row;
    private final boolean preview;
    private final Theme.ResourcesProvider resources;
    private TextCell counter;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;
    private DevGramRowStateController state;
    private DevGramPixelCanvasView pixels;
    private boolean attached;
    private final Runnable ticker=new Runnable(){public void run(){if(!attached)return;if(isShown()&&hasWindowFocus()&&state!=null)state.poll();postDelayed(this,1000);}};
    private final Runnable policy=()->{if(!available()){setVisibility(GONE);removeCallbacks(ticker);}else{setVisibility(VISIBLE);removeCallbacks(ticker);post(ticker);}};
    public DevGramNativeProfileRow(Context context,int account,long owner,JSONObject row,JSONObject document,Theme.ResourcesProvider resources,boolean preview){
        super(context);this.account=account;this.owner=owner;this.row=row;this.resources=resources;this.preview=preview;type=DevGramProfileRowSpec.type(row);
        boolean divider=row.optBoolean("divider",true);String title=row.optString("title"),value=row.optString("url"),text=row.optString("text");
        int titleColor=color("titleColor",Theme.key_windowBackgroundWhiteBlackText),valueColor=color("valueColor",Theme.key_windowBackgroundWhiteGrayText2),iconColor=color("iconColor",Theme.key_windowBackgroundWhiteGrayIcon);
        View cell;
        switch(type){
            case 1:{TextDetailCell detail=new TextDetailCell(context,resources,true,true);detail.setTextAndValue(value,title,divider);detail.textView.setTextColor(valueColor);detail.valueTextView.setTextColor(titleColor);cell=detail;break;}
            case 2:{HeaderCell header=new HeaderCell(context,resources);header.setText(title);header.getTextView().setTextColor(titleColor);cell=header;break;}
            case 3:cell=new View(context);cell.setMinimumHeight(AndroidUtilities.dp(12));break;
            case 4:case 6:{TextInfoPrivacyCell info=new TextInfoPrivacyCell(context,resources);info.setText(text);info.getTextView().setTextColor(titleColor);info.getTextView().setAutoLinkMask(Linkify.WEB_URLS);info.getTextView().setMaxLines(type==6?5:Integer.MAX_VALUE);info.setOnClickListener(v->{if(type==6){boolean expanded=info.getTextView().getMaxLines()>5;info.getTextView().setMaxLines(expanded?5:Integer.MAX_VALUE);}});cell=info;break;}
            case 8:{TextCheckCell check=new TextCheckCell(context,resources);String key=switchKey();check.setTextAndCheck(title,MessagesController.getGlobalMainSettings().getBoolean(key,false),divider);check.setOnClickListener(v->{if(!preview&&available()){boolean next=!MessagesController.getGlobalMainSettings().getBoolean(key,false);MessagesController.getGlobalMainSettings().edit().putBoolean(key,next).apply();check.setTextAndCheck(title,next,divider);DevGramFrameEvents.set(account,owner,row.optString("id"),next);}});cell=check;break;}
            case 10:{DevGramProfileImageView image=new DevGramProfileImageView(context);image.setCrop(row);image.setRoundRadius(AndroidUtilities.dp(row.optInt("radius",16)));String url=DevGramProfileTextures.imageUrl(value);if(!url.isEmpty()){image.getImageReceiver().setAllowStartAnimation(row.optInt("mediaType")==1&&new DevGramProfilePainter().motion());image.getImageReceiver().setAutoRepeat(1);image.setImage(url,row.optInt("mediaType")==1?"1200_800_g":"1200_800",null);}cell=image;break;}
            case 11:{pixels=new DevGramPixelCanvasView(context,account,owner,row,resources,true);cell=pixels;break;}
            case 12:cell=new DevGramIntegrationRowView(context,account,owner,row,document,resources,preview);break;
            default:{TextCell nativeCell=new TextCell(context,resources);int icon=DevGramProfileRowSpec.icon(row.optString("icon","msg_link"));if(type==9){counter=nativeCell;value=preview?row.optString("url","42"):"0";}
                String display=type==9?value:row.optBoolean("showLink",true)?shortValue(value):"";
                if(type==5)nativeCell.setTextAndValueAndColorfulIcon(title,display,false,icon,color("iconBack",Theme.key_windowBackgroundWhiteBlueText),color("iconBack",Theme.key_windowBackgroundWhiteBlueText),divider);
                else if(type==7&&row.optLong("emoji")!=0){emoji=new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nativeCell,24);emoji.setCurrentAccount(account);emoji.set(row.optLong("emoji"),false);nativeCell.setTextAndValueAndIcon(title,display,emoji,divider);}
                else nativeCell.setTextAndValueAndIcon(title,display,icon,divider);
                nativeCell.setTextColor(titleColor);nativeCell.getValueTextView().setTextColor(valueColor);nativeCell.getImageView().setColorFilter(iconColor);cell=nativeCell;break;}
        }
        int height=type==10?Math.max(80,Math.min(480,row.optInt("height",180))):type==11?220:-2;
        addView(cell,new FrameLayout.LayoutParams(-1,height==-2?-2:AndroidUtilities.dp(height)));
        if(preview){setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
        else if(type!=3&&type!=4&&type!=6&&type!=8){cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector,resources),2));cell.setOnClickListener(v->{if(!available())return;if(type==12){if(!((DevGramIntegrationRowView)cell).performPrimaryAction())action(row.optInt("action",1));}else if(type==9&&state!=null)state.countTap();else if(type==11){BaseFragment host=LaunchActivity.getSafeLastFragment();if(host!=null)host.presentFragment(new DevGramPixelCanvasActivity(account,owner,row));}else action(row.optInt("action",1));});cell.setOnLongClickListener(v->{if(!available())return false;int action=row.optInt("longAction");if(action==0)return false;action(action);return true;});}
    }
    public void fillImageCard(){if(type==10&&getChildAt(0) instanceof BackupImageView){BackupImageView image=(BackupImageView)getChildAt(0);image.setRoundRadius(0);image.setAspectFit(false);}}
    private String switchKey(){return "devgramProfileSwitch_"+UserConfig.getInstance(account).getClientUserId()+"_"+owner+"_"+row.optString("id");}
    private boolean available(){return DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(owner,"profile.blocks")&&DevGramProfilePolicy.allowed(owner,DevGramProfileRowSpec.feature(type))&&(!row.optBoolean("ownOnly")||UserConfig.getInstance(account).getClientUserId()==org.telegram.messenger.DevGramCommunityProfiles.owner(owner));}
    private int color(String key,int fallback){int value=row.optInt(key);return value==0?Theme.getColor(fallback,resources):value;}
    private static String shortValue(String text){return text.length()>44?text.substring(0,41)+"…":text;}
    private void action(int action){if(type==12&&(action==1||action==4)){String link=((DevGramIntegrationRowView)getChildAt(0)).serviceLink();if(!link.isEmpty())Browser.openUrl(getContext(),link);return;}String value=row.optString("url").trim();if(value.isEmpty())value=row.optString("text");if(value.isEmpty())return;switch(action){case 1:if(value.startsWith("https://")||value.startsWith("http://")||value.startsWith("tg://")||value.startsWith("tel:")||value.startsWith("mailto:"))Browser.openUrl(getContext(),value);else if(value.matches("[A-Za-z0-9][A-Za-z0-9.-]+\\.[A-Za-z]{2,}(/.*)?"))Browser.openUrl(getContext(),"https://"+value);break;case 2:AndroidUtilities.addToClipboard(value);Toast.makeText(getContext(),DevGramLocale.text("Скопировано"),Toast.LENGTH_SHORT).show();break;case 3:Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value);try{getContext().startActivity(Intent.createChooser(share,DevGramLocale.text("Поделиться")));}catch(Exception ignored){}break;case 4:String handle=value.replaceFirst("^@","");if(handle.matches("[A-Za-z0-9_]{5,32}"))Browser.openUrl(getContext(),"https://t.me/"+handle);else if(value.startsWith("https://t.me/")||value.startsWith("tg://"))Browser.openUrl(getContext(),value);break;}}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return preview||super.onInterceptTouchEvent(event);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;if(emoji!=null)emoji.attach();if(!preview){DevGramProfilePolicy.listen(policy);if(type==9){state=DevGramRowStateApi.create(account,owner,row,()->{if(counter!=null&&state!=null)counter.getValueTextView().setText(Integer.toString(state.count()),true);},error->Toast.makeText(getContext(),DevGramLocale.text("Не удалось обновить строку"),Toast.LENGTH_SHORT).show());post(ticker);}policy.run();}}
    @Override protected void onDetachedFromWindow(){attached=false;removeCallbacks(ticker);DevGramProfilePolicy.unlisten(policy);if(state!=null){state.close();state=null;}if(emoji!=null)emoji.detach();super.onDetachedFromWindow();}
}

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramColorCode;
import org.telegram.messenger.DevGramLocale;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

/** Native ColorPicker + full color code and opacity, as in the supplied plugin. */
public final class DevGramProfileColorPicker {
    private DevGramProfileColorPicker(){}
    public static void show(BaseFragment host,String title,int initial,int fallback,IntConsumer sink,Runnable dismissed){
        if(host.getParentActivity()==null)return;Context context=host.getParentActivity();int[] current={initial};boolean[] syncing={true},typing={false},dragging={false};
        TextView caption=new TextView(context);caption.setTextSize(14);caption.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));caption.setPadding(dp(24),dp(12),dp(24),dp(4));
        EditTextBoldCursor input=new EditTextBoldCursor(context);SeekBarView opacity=new SeekBarView(context,host.getResourceProvider());opacity.setReportChanges(true);
        Runnable[] refresh={null};ColorPicker picker=new ColorPicker(context,false,new ColorPicker.ColorPickerDelegate(){public int getDefaultColor(int num){return fallback;}public void setColor(int color,int num,boolean applyNow){if(syncing[0])return;current[0]=(current[0]&0xff000000)|(color&0xffffff);sink.accept(current[0]);if(refresh[0]!=null)refresh[0].run();}});
        picker.setResourcesProvider(host.getResourceProvider());picker.setColor(initial|0xff000000,0);
        EditTextBoldCursor[] fields=picker.devgramColorFields();EditTextBoldCursor old=fields[1];ViewGroup parent=(ViewGroup)old.getParent();
        input.setTextColor(old.getTextColors());input.setHintTextColor(old.getHintTextColors());input.setCursorColor(old.getCurrentTextColor());input.setTextSize(0,old.getTextSize());input.setTypeface(old.getTypeface());input.setGravity(old.getGravity());
        GradientDrawable background=new GradientDrawable();background.setColor(Theme.getColor(Theme.key_dialogBackground,host.getResourceProvider()));background.setCornerRadius(dp(16));input.setBackground(new InsetDrawable(background,0,dp(5),0,dp(17)));input.setPadding(dp(12),old.getPaddingTop(),dp(12),old.getPaddingBottom());input.setSingleLine(true);input.setHint(DevGramLocale.text("HEX-код цвета"));input.setContentDescription(DevGramLocale.text("HEX-код цвета"));input.setInputType(524289);input.setImeOptions(268435462);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128)});
        int index=parent.indexOfChild(old);ViewGroup.LayoutParams params=old.getLayoutParams();if(params instanceof LinearLayout.LayoutParams){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams((LinearLayout.LayoutParams)params);p.width=-2;p.weight=0;params=p;}parent.removeViewAt(index);parent.addView(input,index,params);fields[1]=input;fields[0].setVisibility(View.GONE);parent.setWillNotDraw(true);
        Runnable resize=()->{int width=parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight();if(width<=0)width=AndroidUtilities.displaySize.x-dp(44);View reset=picker.devgramResetButton();if(reset!=null)width-=Math.max(dp(72),reset.getMeasuredWidth())+dp(8);for(int i=0;i<parent.getChildCount();i++){View child=parent.getChildAt(i);if(child!=input&&child.getVisibility()!=View.GONE)width-=child.getMeasuredWidth();}int target=Math.min(Math.max(dp(48),(int)Math.ceil(input.getPaint().measureText(input.getText().toString()))+input.getCompoundPaddingLeft()+input.getCompoundPaddingRight()+dp(3)),Math.max(dp(48),width));ViewGroup.LayoutParams p=input.getLayoutParams();if(p!=null&&p.width!=target){p.width=target;input.setLayoutParams(p);}};
        parent.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->resize.run());
        refresh[0]=()->{if(syncing[0])return;syncing[0]=true;try{int percent=Math.round(Color.alpha(current[0])*100/255f);caption.setText(DevGramLocale.text("Прозрачность — ")+percent+"%");if(!dragging[0])opacity.setProgress(percent/100f);if(!typing[0]){input.setText(DevGramColorCode.hex(current[0]));input.setSelection(input.length());input.setError(null);}resize.run();}finally{syncing[0]=false;}};
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable value){resize.run();if(syncing[0]||picker.ignoreTextChange)return;input.setError(null);Integer parsed=DevGramColorCode.parse(value.toString(),current[0]>>>24);if(parsed==null||parsed==current[0])return;String text=value.toString();int selection=input.getSelectionStart();typing[0]=true;syncing[0]=true;try{current[0]=parsed;picker.setColor(parsed|0xff000000,0);input.setText(text);input.setSelection(Math.max(0,Math.min(text.length(),selection)));sink.accept(parsed);}finally{syncing[0]=false;}refresh[0].run();typing[0]=false;}});
        input.setOnEditorActionListener((view,action,event)->{if(action!=6)return false;if(DevGramColorCode.parse(input.getText().toString(),current[0]>>>24)==null){input.setError(DevGramLocale.text("Проверьте формат цвета и диапазон значений"));return true;}InputMethodManager keyboard=(InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();return true;});
        opacity.setDelegate(new SeekBarView.SeekBarViewDelegate(){public void onSeekBarDrag(boolean stop,float progress){if(syncing[0])return;current[0]=(Math.round(Math.round(progress*100)*255/100f)<<24)|(current[0]&0xffffff);sink.accept(current[0]);refresh[0].run();}public void onSeekBarPressed(boolean pressed){dragging[0]=pressed;}public CharSequence getContentDescription(){return caption.getText();}});
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setFocusableInTouchMode(true);body.setPadding(0,0,0,Math.max(dp(8),AndroidUtilities.navigationBarHeight));body.addView(picker,new LinearLayout.LayoutParams(-1,dp(280)));TextView formats=new TextView(context);formats.setText(DevGramLocale.text("Форматы: #RGB, #RRGGBB или #AARRGGBB"));formats.setTextSize(12);formats.setTextColor(Theme.getColor(Theme.key_dialogTextGray,host.getResourceProvider()));formats.setPadding(dp(24),0,dp(24),dp(4));body.addView(formats,new LinearLayout.LayoutParams(-1,-2));body.addView(caption,new LinearLayout.LayoutParams(-1,-2));body.addView(opacity,new LinearLayout.LayoutParams(-1,dp(48)));
        ScrollView scroll=new ScrollView(context){@Override protected void onMeasure(int width,int height){boolean keyboard=android.os.Build.VERSION.SDK_INT>=30&&getRootWindowInsets()!=null&&getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime());int bottom=keyboard?dp(8):Math.max(dp(8),AndroidUtilities.navigationBarHeight);if(body.getPaddingBottom()!=bottom)body.setPadding(0,0,0,bottom);super.onMeasure(width,DevGramStudioControls.sheetHeightSpec(height,.82f));}};scroll.addView(body);
        BottomSheet sheet=new BottomSheet(context,true,host.getResourceProvider());sheet.setTitle(title,true);sheet.setApplyBottomPadding(false);sheet.setFocusable(true);sheet.setCustomView(scroll);host.showDialog(sheet,d->{if(dismissed!=null)dismissed.run();});sheet.fixNavigationBar();if(sheet.getWindow()!=null)sheet.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        syncing[0]=false;refresh[0].run();
    }
    private static int dp(float value){return AndroidUtilities.dp(value);}
}

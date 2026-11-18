package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;

/** A selected media session's application icon; no installed-app inventory. */
public final class DevGramMusicSourceCell extends FrameLayout {
    private final boolean checked;
    public DevGramMusicSourceCell(Context context,String source,String name,boolean checked,Theme.ResourcesProvider provider){
        super(context);this.checked=checked;setMinimumHeight(AndroidUtilities.dp(56));setClickable(true);setContentDescription(name);
        setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector,provider),2));
        LinearLayout content=new LinearLayout(context);content.setGravity(Gravity.CENTER_VERTICAL);content.setLayoutDirection(LAYOUT_DIRECTION_LOCALE);content.setPadding(AndroidUtilities.dp(21),AndroidUtilities.dp(10),AndroidUtilities.dp(21),AndroidUtilities.dp(10));
        ImageView icon=new ImageView(context);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        try{Drawable app=context.getPackageManager().getApplicationIcon("devgram".equals(source)?context.getPackageName():source);icon.setImageDrawable(app);}catch(Exception e){icon.setImageResource(R.drawable.bot_music);icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon,provider));}
        content.addView(icon,new LinearLayout.LayoutParams(AndroidUtilities.dp(32),AndroidUtilities.dp(32)));
        TextView label=DevGramStudioControls.text(context,"",16,false,provider);label.setText(name);label.setMaxLines(2);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams text=new LinearLayout.LayoutParams(0,-2,1);text.setMargins(AndroidUtilities.dp(16),0,AndroidUtilities.dp(16),0);content.addView(label,text);
        RadioButton radio=new RadioButton(context);radio.setSize(AndroidUtilities.dp(20));ThemeBind.radioColors(radio,Theme.key_radioBackground,Theme.key_radioBackgroundChecked,provider);radio.setChecked(checked,false);radio.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);content.addView(radio,new LinearLayout.LayoutParams(AndroidUtilities.dp(22),AndroidUtilities.dp(22)));
        addView(content,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL));setFocusable(true);
    }
    @Override protected void onMeasure(int width,int height){super.onMeasure(width,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));setMeasuredDimension(getMeasuredWidth(),Math.max(getMeasuredHeight(),AndroidUtilities.dp(56)));}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.RadioButton");info.setCheckable(true);info.setChecked(checked);}
}

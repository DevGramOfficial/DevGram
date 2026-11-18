package org.telegram.ui.devgram.icons;
import android.app.Activity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.messenger.devgram.icons.*;
import org.telegram.ui.LaunchActivity;

public final class IconPickerController {
    private static IconPickerView picker;
    private IconPickerController(){}
    public static void sync(LaunchActivity a){setActive(a,!org.telegram.messenger.SharedConfig.appLocked && !org.telegram.messenger.SharedConfig.isWaitingForPasscodeEnter && IconManager.INSTANCE.editingId()!=null);}
    public static void setActive(Activity activity,boolean active){
        if(!(activity instanceof LaunchActivity)||activity.isFinishing()||activity.isDestroyed())return;
        LaunchActivity a=(LaunchActivity)activity;
        if(active && IconManager.INSTANCE.editingId()!=null){if(picker==null){picker=new IconPickerView(a);a.getMainContainerFrameLayout().addView(picker,new FrameLayout.LayoutParams(-1,-1));picker.showFab();}}
        else destroy();
    }
    public static boolean onBackPressed(boolean invoked){return picker!=null&&picker.onBackPressed(invoked);}
    public static void destroy(){if(picker!=null){picker.saveConfig();if(picker.getParent() instanceof ViewGroup)((ViewGroup)picker.getParent()).removeView(picker);picker=null;}IconObserver.INSTANCE.clear();}
}

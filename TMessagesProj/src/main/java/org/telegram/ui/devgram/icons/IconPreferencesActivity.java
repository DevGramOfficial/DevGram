package org.telegram.ui.devgram.icons;

import android.content.Context;
import android.os.Parcelable;
import android.view.*;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.devgram.icons.IconManager;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;
import java.util.ArrayList;

/** Uses the same UniversalAdapter sections and glass header as the server reference. */
abstract class IconPreferencesActivity extends BaseFragment {
    UniversalRecyclerView listView;
    FrameLayout content;
    private Parcelable scrollState;
    private final Runnable changed=()->{if(listView!=null&&listView.adapter!=null)listView.adapter.update(true);onIconsChanged();};
    protected abstract String title();
    protected abstract void fill(ArrayList<UItem> items,UniversalAdapter adapter);
    protected abstract void click(UItem item,View view,int position,float x,float y);
    protected boolean longClick(UItem item,View view,int position,float x,float y){return false;}
    protected void onIconsChanged(){}
    @Override public boolean onFragmentCreate(){IconManager.INSTANCE.addListener(changed);return super.onFragmentCreate();}
    @Override public void onFragmentDestroy(){IconManager.INSTANCE.removeListener(changed);super.onFragmentDestroy();}
    @Override protected boolean useSimpleActionBar(){return true;}
    @Override protected boolean useFadeDrawableForActionBar(){return false;}
    @Override protected RecyclerListView getListViewForSimpleGlass(){return listView;}
    @Override public View createView(Context context){
        content=new FrameLayout(context);content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView=content;
        if(simpleActionBar!=null){simpleActionBar.setTitle(title());simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});}
        listView=new UniversalRecyclerView(this,this::fill,this::click,this::longClick);
        listView.setClipToPadding(true);content.addView(listView,LayoutHelper.createFrame(-1,-1));
        if(scrollState!=null){listView.getLayoutManager().onRestoreInstanceState(scrollState);scrollState=null;}
        return content;
    }
    @Override public void clearViews(){if(fragmentView!=null&&listView!=null)scrollState=listView.getLayoutManager().onSaveInstanceState();super.clearViews();listView=null;content=null;}
    void refresh(){if(listView!=null)listView.adapter.update(true);}
    @Override protected void onViewCreated(View view){super.onViewCreated(view);updateListInsets(mSystemInsets.left,mSystemInsets.top,mSystemInsets.right,mSystemInsets.bottom);}
    private void updateListInsets(int left,int top,int right,int bottom){
        if(listView==null)return;
        int headerTop=simpleActionBar!=null&&simpleActionBar.getOccupyStatusBar()?Math.max(top,AndroidUtilities.statusBarHeight):top;
        int headerExtra=simpleActionBar==null?0:simpleActionBar.getExtraHeight();
        BaseFragmentSimpleGlass.setListViewParams(listView,headerTop,bottom,ActionBar.getCurrentActionBarHeight()+headerExtra,AndroidUtilities.dp(80));
        listView.setPadding(left,listView.getPaddingTop(),right,listView.getPaddingBottom());
        listView.setClipToPadding(true);
    }
    @Override public void onInsets(int left,int top,int right,int bottom){super.onInsets(left,top,right,bottom);updateListInsets(left,top,right,bottom);}
}

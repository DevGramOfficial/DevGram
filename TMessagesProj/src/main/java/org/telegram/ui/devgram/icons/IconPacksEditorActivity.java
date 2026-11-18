package org.telegram.ui.devgram.icons;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import org.telegram.messenger.*;
import org.telegram.messenger.devgram.icons.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.*;
import java.util.*;

public final class IconPacksEditorActivity extends IconPreferencesActivity {
    private final String packId;
    private String query="";
    private int filter;
    private boolean searching;
    private SimpleActionBarMenuItem otherItem;
    private final ActionBarMenuSubItem[] filterItems = new ActionBarMenuSubItem[3];
    public IconPacksEditorActivity(String id){packId=id;}
    @Override protected String title(){IconPack p=IconManager.INSTANCE.pack(packId);return p==null?IconUi.t("Редактор иконок"):p.name;}
    @Override public View createView(Context c){
        View v=super.createView(c);SimpleActionBarMenu menu=simpleActionBar.createMenu();
        menu.addItem(10,R.drawable.outline_header_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener(){
            @Override public void onSearchExpand(){searching=true;}
            @Override public void onSearchCollapse(){searching=false;query="";refresh();}
            @Override public void onTextChanged(EditText e){query=e.getText().toString();refresh();}
        });
        SimpleActionBarMenuItem other=menu.addItem(20,R.drawable.ic_ab_other);otherItem=other;
        other.addSwipeBackItem(R.drawable.msg_select,null,IconUi.t("Фильтр"),createFilterLayout(c));
        other.addSubItem(4,R.drawable.msg_edit,IconUi.t("Информация о наборе"));other.addSubItem(5,R.drawable.ic_ab_done,IconUi.t("Сохранить и выйти"));
        updateFilterChecks();
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){
            if(id==-1)finishFragment();
            else if(id==4){IconPack p=IconManager.INSTANCE.pack(packId);if(p!=null)new NewIconPackBottomSheet(IconPacksEditorActivity.this,p).show();}
            else if(id==5){IconManager.INSTANCE.editing(null);IconPickerController.setActive(getParentActivity(),false);finishFragment();}
        }});
        if(searching||!query.isEmpty())simpleActionBar.openSearchField(query,false);
        return v;
    }
    private ActionBarPopupWindow.ActionBarPopupWindowLayout createFilterLayout(Context c){
        ActionBarPopupWindow.ActionBarPopupWindowLayout layout=new ActionBarPopupWindow.ActionBarPopupWindowLayout(c,0,getResourceProvider());layout.setFitItems(true);
        ActionBarMenuItem.addItem(layout,R.drawable.msg_arrow_back,IconUi.t("Назад"),false,getResourceProvider()).setOnClickListener(v->{if(otherItem!=null&&otherItem.getPopupLayout()!=null&&otherItem.getPopupLayout().getSwipeBack()!=null)otherItem.getPopupLayout().getSwipeBack().closeForeground();});
        View gap=ActionBarMenuItem.addGap(0,layout);gap.setLayoutParams(LayoutHelper.createLinear(-1,8));gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator));
        String[] labels={"Все иконки","Заменённые иконки","Незаменённые иконки"};
        for(int i=0;i<3;i++){final int selected=i;ActionBarMenuSubItem row=new ActionBarMenuSubItem(c,true,false,false,getResourceProvider());row.setTextAndIcon(IconUi.t(labels[i]),0);row.setMinimumWidth(AndroidUtilities.dp(196));layout.addView(row,LayoutHelper.createLinear(-1,48));row.setOnClickListener(v->{filter=selected;updateFilterChecks();refresh();});filterItems[i]=row;}
        return layout;
    }
    private void updateFilterChecks(){for(int i=0;i<3;i++)if(filterItems[i]!=null)filterItems[i].setChecked(filter==i);}
    @Override protected void onIconsChanged(){if(simpleActionBar!=null)simpleActionBar.setTitle(title());}
    @Override protected void fill(ArrayList<UItem> items,UniversalAdapter adapter){
        IconPack pack=IconManager.INSTANCE.pack(packId);if(pack==null)return;
        List<String> names=new ArrayList<>(IconManager.INSTANCE.systemIcons().keySet());Collections.sort(names);String q=query.toLowerCase(Locale.ROOT);
        adapter.whiteSectionStart();
        for(String name:names){boolean replaced=pack.icons.containsKey(name);if(!name.toLowerCase(Locale.ROOT).contains(q)||(filter==1&&!replaced)||(filter==2&&replaced))continue;Integer id=IconManager.INSTANCE.systemIcons().get(name);if(id!=null)items.add(EditorIconCell.Factory.of(id,name,pack,this));}
        adapter.whiteSectionEnd();
        if(items.isEmpty())items.add(UItem.asShadow(IconUi.t("Иконки не найдены")));
        items.add(UItem.asShadow(IconUi.t("Слева — исходная иконка, справа — вариант набора. Нажмите на значок, чтобы открыть изображение, или на название, чтобы заменить или сбросить его.")));
    }
    @Override protected void click(UItem item,View view,int pos,float x,float y){if(item.id!=0)getParentLayout().getLastFragment().showDialog(new ReplaceIconBottomSheet(this,packId,item.id));}
    @Override public boolean onBackPressed(boolean invoked){if(searching){if(invoked)simpleActionBar.closeSearchField();return false;}return super.onBackPressed(invoked);}
    public static final class EditorIconCell extends TextCell {
        private int bound;private IconPack boundPack;
        public EditorIconCell(Context c,Theme.ResourcesProvider provider){super(c,provider);}
        private void configurePreviews(){
            for(ImageView image:new ImageView[]{getImageView(),getValueImageView()}){
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setCropToPadding(true);
                int padding=AndroidUtilities.dp(12);image.setPadding(padding,padding,padding,padding);
            }
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            super.onMeasure(widthSpec,heightSpec);
            int square=MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48),MeasureSpec.EXACTLY);
            getImageView().measure(square,square);getValueImageView().measure(square,square);
            textView.measure(MeasureSpec.makeMeasureSpec(Math.max(0,getMeasuredWidth()-AndroidUtilities.dp(offsetFromImage+64)),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20),MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
            super.onLayout(changed,left,top,right,bottom);
            int size=AndroidUtilities.dp(48),inset=AndroidUtilities.dp(8),y=(getMeasuredHeight()-size)/2;
            int start=LocaleController.isRTL?getMeasuredWidth()-inset-size:inset;
            int end=LocaleController.isRTL?inset:getMeasuredWidth()-inset-size;
            getImageView().layout(start,y,start+size,y+size);getValueImageView().layout(end,y,end+size,y+size);
        }
        public static final class Factory extends UItem.UItemFactory<EditorIconCell> {
            static {UItem.UItemFactory.setup(new Factory());}
            @Override public EditorIconCell createView(Context c,RecyclerListView list,int account,int guid,Theme.ResourcesProvider provider){return new EditorIconCell(c,provider);}
            @Override public void bindView(View view,UItem item,boolean divider,UniversalAdapter adapter,UniversalRecyclerView list){
                EditorIconCell cell=(EditorIconCell)view;cell.bound=item.id;cell.boundPack=(IconPack)item.object;
                Drawable original=IconUi.tinted(IconManager.INSTANCE.original(item.id),Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
                cell.setTextAndIcon(item.text,original,divider);cell.configurePreviews();cell.getImageView().setEnabled(original!=null);cell.getImageView().setOnClickListener(item.clickCallback);cell.getImageView().setContentDescription(IconUi.t("Открыть исходное изображение")+": "+item.text);
                cell.getValueImageView().setImageDrawable(null);cell.getValueImageView().setVisibility(View.VISIBLE);cell.getValueImageView().setEnabled(false);cell.getValueImageView().setOnClickListener(item.clickCallback2);cell.getValueImageView().setContentDescription(IconUi.t("Открыть изображение набора")+": "+item.text);
                IconManager.INSTANCE.request(cell.boundPack,item.id,AndroidUtilities.dp(24),d->{if(cell.bound==item.id&&cell.boundPack==item.object){cell.getValueImageView().setImageDrawable(IconUi.tinted(d,Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon)));cell.getValueImageView().setEnabled(d!=null);}});
            }
            @Override public boolean contentsEquals(UItem a,UItem b){return a.id==b.id&&a.object==b.object;}
            static UItem of(int id,String name,IconPack p){return of(id,name,p,null);}
            static UItem of(int id,String name,IconPack p,BaseFragment host){UItem item=UItem.ofFactory(Factory.class);item.id=id;item.text=name;item.object=p;item.clickCallback=v->IconImageViewer.open(host,null,id);item.clickCallback2=v->IconImageViewer.open(host,p,id);return item;}
        }
    }
}

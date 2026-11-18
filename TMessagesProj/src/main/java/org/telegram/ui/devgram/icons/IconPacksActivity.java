package org.telegram.ui.devgram.icons;

import android.content.Context;
import android.content.Intent;
import android.view.*;
import android.widget.FrameLayout;
import androidx.core.content.FileProvider;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.devgram.icons.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.io.File;
import java.util.*;

public final class IconPacksActivity extends IconPreferencesActivity {
    private static final int ICON_CHANNEL = 10001;
    private FragmentFloatingButton add;
    @Override protected String title(){return IconUi.t("Наборы иконок");}
    @Override public View createView(Context c){
        View result=super.createView(c);
        add=new FragmentFloatingButton(c,getResourceProvider());add.imageView.setImageResource(R.drawable.msg_add);add.setContentDescription(IconUi.t("Создать набор"));
        add.setOnClickListener(v->new NewIconPackBottomSheet(this,null).show());content.addView(add,FragmentFloatingButton.createDefaultLayoutParams());
        listView.allowReorder(true);listView.setReorderLongPressEnabled(false);
        listView.listenReorder((section,items)->{List<String> ids=new ArrayList<>();for(UItem item:items)if(item.object instanceof IconPack)ids.add(((IconPack)item.object).id);if(!ids.isEmpty())IconManager.INSTANCE.reorder(ids,IconManager.INSTANCE.activeIds().contains(ids.get(0)));});
        listView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener(){@Override public void onScrolled(androidx.recyclerview.widget.RecyclerView r,int dx,int dy){if(dy!=0)add.setButtonVisible(dy<0,true);}});
        if(simpleActionBar!=null){simpleActionBar.createMenu().addItem(1,R.drawable.msg2_folder).setContentDescription(IconUi.t("Импортировать набор"));simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==1)IconPackIntents.pickPack(getParentActivity());else if(id==-1)finishFragment();}});}
        return result;
    }
    @Override public void onInsets(int l,int t,int r,int b){super.onInsets(l,t,r,b);if(add!=null)add.setTranslationY(-b);}
    @Override protected void fill(ArrayList<UItem> items,UniversalAdapter adapter){
        IconManager m=IconManager.INSTANCE;List<String> active=m.activeIds();
        adapter.whiteSectionStart();
        items.add(UItem.asButtonWithSubtext(ICON_CHANNEL,R.drawable.msg_channel,IconUi.t("Канал иконок"),"@DevGramIcons"));
        adapter.whiteSectionEnd();items.add(UItem.asShadow(null));
        adapter.whiteSectionStart();items.add(UItem.asHeader(IconUi.t("Базовые наборы")));
        for(String id:IconManager.BASE_IDS){IconPack p=m.pack(id);if(p!=null){UItem item=IconPackCell.Factory.asIconPackCell(p);item.checked=active.contains(id);items.add(item);}}
        adapter.whiteSectionEnd();items.add(UItem.asShadow(IconUi.t("Базовый набор используется для значков, которые не заменены другими наборами.")));
        section(items,adapter,"Включённые наборы",active);section(items,adapter,"Все наборы",m.hiddenIds());
        items.add(UItem.asShadow(IconUi.t("Нажмите на набор, чтобы включить или выключить его. Удерживайте для редактирования, отправки или удаления. Верхние наборы имеют приоритет.")));
    }
    private void section(ArrayList<UItem> items,UniversalAdapter adapter,String title,List<String> ids){
        List<IconPack> packs=new ArrayList<>();for(String id:ids){IconPack p=IconManager.INSTANCE.pack(id);if(p!=null&&!p.isBase())packs.add(p);}if(packs.isEmpty())return;
        items.add(UItem.asShadow(null));adapter.whiteSectionStart();items.add(UItem.asHeader(IconUi.t(title)));
        if(packs.size()>1)adapter.reorderSectionStart();for(IconPack p:packs)items.add(IconPackCell.Factory.asIconPackCell(p).setReordering(packs.size()>1));if(packs.size()>1)adapter.reorderSectionEnd();adapter.whiteSectionEnd();
    }
    @Override protected void click(UItem item,View view,int pos,float x,float y){if(item.id==ICON_CHANNEL){Browser.openUrl(getContext(),"https://t.me/DevGramIcons");return;}if(item.object instanceof IconPack){IconPack p=(IconPack)item.object;if(p.isBase())IconManager.INSTANCE.setBase(p.id);else IconManager.INSTANCE.toggle(p.id);}}
    @Override protected boolean longClick(UItem item,View view,int pos,float x,float y){
        if(!(item.object instanceof IconPack))return false;IconPack p=(IconPack)item.object;if(p.isBase())return false;
        ItemOptions options=ItemOptions.makeOptions(this,view);
        options.addIf(IconManager.INSTANCE.activeIds().contains(p.id),R.drawable.msg_edit,IconUi.t("Редактировать"),()->{IconManager.INSTANCE.editing(p.id);IconPickerController.setActive(getParentActivity(),true);presentFragment(new IconPacksEditorActivity(p.id));});
        options.add(R.drawable.msg_share,IconUi.t("Поделиться файлом"),()->IconManager.INSTANCE.export(p.id,this::share));
        options.add(R.drawable.msg_delete,IconUi.t("Удалить"),()->showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(IconUi.t("Удалить набор?")).setMessage(p.name).setNegativeButton(IconUi.t("Отмена"),null).setPositiveButton(IconUi.t("Удалить"),(d,w)->IconManager.INSTANCE.delete(p.id,null)).create()));options.show();return true;
    }
    private void share(File f){if(getParentActivity()==null)return;Intent i=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,FileProvider.getUriForFile(getParentActivity(),ApplicationLoader.getApplicationId()+".provider",f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);getParentActivity().startActivity(Intent.createChooser(i,IconUi.t("Поделиться набором")));}
}

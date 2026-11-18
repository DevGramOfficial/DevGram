package org.telegram.ui.devgram.icons;
import android.content.Context;
import android.text.InputFilter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.devgram.icons.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

/** Server-reference create/edit sheet, with name, author and version fields. */
public final class NewIconPackBottomSheet extends BottomSheet {
    public NewIconPackBottomSheet(BaseFragment parent,IconPack pack){
        super(parent.getParentActivity(),true,parent.getResourceProvider());
        fixNavigationBar();waitingKeyboard=true;smoothKeyboardAnimationEnabled=true;
        setTitle(IconUi.t(pack==null?"Новый набор иконок":"Информация о наборе"),true);
        Context c=getContext();ScrollView scroll=new ScrollView(c);LinearLayout form=new LinearLayout(c);form.setOrientation(1);form.setPadding(AndroidUtilities.dp(20),0,AndroidUtilities.dp(20),AndroidUtilities.dp(16));scroll.addView(form);
        OutlineEditText name=field(form,"Название набора",pack==null?"":pack.name,64);
        OutlineEditText author=field(form,"Автор (необязательно)",pack==null?"":pack.author,128);
        OutlineEditText version=field(form,"Версия",pack==null?"1.0":pack.version,32);
        ButtonWithCounterView save=new ButtonWithCounterView(c,true,resourcesProvider);save.setRound();save.setText(IconUi.t(pack==null?"Создать":"Сохранить"),false);form.addView(save,LayoutHelper.createLinear(-1,48,0,16,0,0));
        save.setOnClickListener(v->{String n=name.getEditText().getText().toString().trim();if(n.isEmpty()){AndroidUtilities.shakeView(name);name.requestFocus();return;}
            String a=author.getEditText().getText().toString().trim(),ver=version.getEditText().getText().toString().trim();if(ver.isEmpty())ver="1.0";
            save.setEnabled(false);dismiss();
            if(pack==null)IconManager.INSTANCE.create(n,a,ver,p->{IconPickerController.setActive(parent.getParentActivity(),true);parent.presentFragment(new IconPacksEditorActivity(p.id));});
            else IconManager.INSTANCE.updateMetadata(pack,n,a,ver,null);
        });setCustomView(scroll);
    }
    private OutlineEditText field(LinearLayout form,String title,String value,int limit){OutlineEditText field=new OutlineEditText(getContext());field.setHint(IconUi.t(title));field.getEditText().setSingleLine(true);field.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});field.getEditText().setText(value);form.addView(field,LayoutHelper.createLinear(-1,58,0,0,0,10));return field;}
}

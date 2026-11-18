package org.telegram.ui.devgram.icons;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.messenger.devgram.icons.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public final class InstallIconPackBottomSheet extends BottomSheet {
    private IconPackStorage.Preview preview;
    public InstallIconPackBottomSheet(BaseFragment parent,IconPackStorage.Preview preview){
        super(parent.getParentActivity(),true,parent.getResourceProvider());this.preview=preview;fixNavigationBar();IconPack p=preview.pack;
        LinearLayout root=new LinearLayout(getContext());root.setOrientation(1);root.setPadding(0,AndroidUtilities.dp(20),0,AndroidUtilities.dp(16));
        IconPackPreviewView icons=new IconPackPreviewView(getContext());icons.setCircularMode(true);icons.setIconPack(p);root.addView(icons,LayoutHelper.createLinear(-1,-2));
        TextView name=IconUi.text(getContext(),p.name,20,Theme.key_dialogTextBlack);name.setTypeface(AndroidUtilities.bold());name.setGravity(Gravity.CENTER);root.addView(name,LayoutHelper.createLinear(-1,-2,24,12,24,0));
        IconPack existing=IconManager.INSTANCE.pack(p.id);TextView info=IconUi.text(getContext(),IconUi.t("Версия")+" "+(existing==null?"":existing.version+" → ")+p.version,14,Theme.key_windowBackgroundWhiteGrayText);if(existing!=null){SpannableStringBuilder versionText=new SpannableStringBuilder(IconUi.t("Версия")+" ");int begin=versionText.length();versionText.append(existing.version);versionText.setSpan(new StrikethroughSpan(),begin,versionText.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);versionText.append(" → ").append(p.version);info.setText(versionText);}info.setGravity(Gravity.CENTER);root.addView(info,LayoutHelper.createLinear(-1,-2,24,4,24,0));
        if(!p.author.isEmpty()){TextView author=IconUi.text(getContext(),"",14,Theme.key_dialogTextLink);author.setText(IconUi.author(p.author));author.setMovementMethod(LinkMovementMethod.getInstance());author.setGravity(Gravity.CENTER);root.addView(author,LayoutHelper.createLinear(-1,-2,24,4,24,0));}
        ButtonWithCounterView install=new ButtonWithCounterView(getContext(),true,resourcesProvider);install.setRound();install.setText(IconUi.t(existing==null?"Установить набор":"Обновить набор"),false);install.setCount(p.icons.size(),false);root.addView(install,LayoutHelper.createLinear(-1,48,16,24,16,0));
        CheckBoxRow enable=new CheckBoxRow(getContext(),IconUi.t("Включить после установки"),true,resourcesProvider);boolean active=IconManager.INSTANCE.activeIds().contains(p.id);if(!active)root.addView(enable,LayoutHelper.createLinear(-2,-2,Gravity.CENTER,0,0,0,8));
        install.setOnClickListener(v->{IconPackStorage.Preview selected=this.preview;this.preview=null;dismiss();IconManager.INSTANCE.install(selected,active||enable.isChecked(),null);});setCustomView(root);
    }
    @Override public void dismiss(){super.dismiss();if(preview!=null){preview.close();preview=null;}}
}

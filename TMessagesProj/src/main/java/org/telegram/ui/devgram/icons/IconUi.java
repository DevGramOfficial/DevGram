package org.telegram.ui.devgram.icons;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import java.util.regex.*;
final class IconUi {
    static String t(String value){return DevGramLocale.text(value);}
    static TextView text(Context c,String text,int size,int color){TextView v=new TextView(c);v.setText(text);v.setTextSize(size);v.setTextColor(Theme.getColor(color));return v;}
    static CharSequence author(String value){SpannableString s=new SpannableString(value);Matcher m=Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{4,32})").matcher(value);while(m.find())s.setSpan(new URLSpan("https://t.me/"+m.group(1)),m.start(),m.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return s;}
    static Drawable tinted(Drawable d,int color){if(d==null)return null;Drawable copy=d.getConstantState()==null?d.mutate():d.getConstantState().newDrawable().mutate();copy.setColorFilter(new PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN));return copy;}
    static void showReplace(int resource) {BaseFragment f=org.telegram.ui.LaunchActivity.getSafeLastFragment();String id=org.telegram.messenger.devgram.icons.IconManager.INSTANCE.editingId();if(f!=null&&id!=null)f.showDialog(new ReplaceIconBottomSheet(f,id,resource));}
    static void error(BaseFragment f,String message){if(f!=null&&f.getParentActivity()!=null)f.showDialog(new AlertDialog.Builder(f.getParentActivity(),f.getResourceProvider()).setTitle(t("Наборы иконок")).setMessage(message).setPositiveButton(t("ОК"),null).create());}
}

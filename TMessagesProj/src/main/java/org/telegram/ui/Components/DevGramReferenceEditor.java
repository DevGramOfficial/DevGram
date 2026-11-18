package org.telegram.ui.Components;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import java.util.function.IntConsumer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
/** Shared native widgets matching the supplied EditCells/EditHelp, without reflective hooks. */
public final class DevGramReferenceEditor {
 private DevGramReferenceEditor(){}
 private static int dp(float n){return AndroidUtilities.dp(n);}
 public static View section(Context c,String title){LinearLayout group=new LinearLayout(c);group.setOrientation(1);group.setPadding(dp(24),dp(24),dp(24),dp(10));int accent=Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);TextView text=new TextView(c);text.setText(DevGramLocale.text(title));text.setTextSize(22);text.setTypeface(Typeface.create("sans-serif-black",0));text.setTextColor(accent);group.addView(text,new LinearLayout.LayoutParams(-1,-2));View line=new View(c);line.setBackgroundColor(ColorUtils.setAlphaComponent(accent,85));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(2));p.topMargin=dp(10);group.addView(line,p);return group;}
 public static View slider(BaseFragment host,String title,int value,int low,int high,String unit,boolean available,IntConsumer sink,Runnable locked){Context c=host.getParentActivity();LinearLayout body=new LinearLayout(c);body.setOrientation(1);TextView label=new TextView(c);label.setTextSize(14);label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));label.setPadding(dp(24),dp(12),dp(24),dp(4));label.setText(DevGramLocale.text(title)+": "+value+" "+unit);body.addView(label,new LinearLayout.LayoutParams(-1,-2));SeekBarView seek=new SeekBarView(c,host.getResourceProvider());seek.setReportChanges(true);seek.setProgress((value-low)/(float)(high-low));seek.setEnabled(available);seek.setAlpha(available?1:.35f);if(!available)label.setOnClickListener(v->locked.run());body.addView(seek,new LinearLayout.LayoutParams(-1,dp(48)));seek.setDelegate(new SeekBarView.SeekBarViewDelegate(){public void onSeekBarDrag(boolean stop,float progress){int next=Math.round(low+progress*(high-low));label.setText(DevGramLocale.text(title)+": "+next+" "+unit);sink.accept(next);}public CharSequence getContentDescription(){return label.getText();}});return body;}
 public static View help(BaseFragment host,String summary,String detail){Context c=host.getParentActivity();LinearLayout group=new LinearLayout(c);group.setGravity(Gravity.TOP);group.setPadding(dp(24),0,dp(16),dp(2));TextView text=new TextView(c);text.setText(DevGramLocale.text(summary));text.setTextSize(13);text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));text.setPadding(0,dp(6),dp(8),0);group.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView question=new TextView(c);question.setText("?");question.setTextSize(18);question.setGravity(Gravity.CENTER);int accent=Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);question.setTextColor(accent);GradientDrawable circle=new GradientDrawable();circle.setShape(1);circle.setStroke(dp(1),ColorUtils.setAlphaComponent(accent,110));circle.setColor(ColorUtils.setAlphaComponent(accent,18));question.setBackground(new InsetDrawable(circle,dp(8),dp(8),dp(8),dp(8)));question.setContentDescription(DevGramLocale.text("Подробнее: ")+DevGramLocale.text(summary));question.setOnClickListener(v->new AlertDialog.Builder(c,host.getResourceProvider()).setTitle(DevGramLocale.text("Результат настройки")).setMessage(DevGramLocale.text(detail)).setPositiveButton(DevGramLocale.text("Понятно"),null).show());group.addView(question,new LinearLayout.LayoutParams(dp(44),dp(44)));return group;}
 public static View primary(BaseFragment host,String label,Runnable click){Context c=host.getParentActivity();LinearLayout parent=new LinearLayout(c);parent.setPadding(dp(16),dp(10),dp(16),dp(8));org.telegram.ui.Stories.recorder.ButtonWithCounterView button=new org.telegram.ui.Stories.recorder.ButtonWithCounterView(c,host.getResourceProvider());button.setText(DevGramLocale.text(label),false);button.setRoundRadius(24);DevGramReferenceActionButtons.style(button);button.setOnClickListener(v->click.run());parent.addView(button,new LinearLayout.LayoutParams(-1,dp(48)));return parent;}
}

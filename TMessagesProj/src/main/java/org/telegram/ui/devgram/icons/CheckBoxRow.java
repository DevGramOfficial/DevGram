package org.telegram.ui.devgram.icons;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.JvmOverloads;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.DefaultConstructorMarker;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

/* JADX INFO: loaded from: classes4.dex */
@SuppressLint({"ViewConstructor"})
public final class CheckBoxRow extends LinearLayout {
    private final CheckBox2 checkBox;
    private Function1<? super Boolean, Unit> onCheckedChange;

    public /* synthetic */ CheckBoxRow(Context context, CharSequence charSequence, boolean z, Theme.ResourcesProvider resourcesProvider, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(context, charSequence, (i & 4) != 0 ? false : z, (i & 8) != 0 ? null : resourcesProvider);
    }

    @JvmOverloads
    public CheckBoxRow(Context context, CharSequence charSequence, boolean z, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        CheckBox2 checkBox2 = new CheckBox2(context, 21, resourcesProvider);
        checkBox2.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
        checkBox2.setDrawUnchecked(true);
        checkBox2.setChecked(z, false);
        checkBox2.setDrawBackgroundAsArc(10);
        this.checkBox = checkBox2;
        setOrientation(0);
        setPadding(AndroidUtilities.dp(8.0f), AndroidUtilities.dp(6.0f), AndroidUtilities.dp(12.0f), AndroidUtilities.dp(6.0f));
        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 18, 18));
        ScaleStateListAnimator.apply(this, 0.05f, 1.2f);
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.addView(checkBox2, LayoutHelper.createFrame(21, 21.0f, 17, 0.0f, 0.0f, 0.0f, 0.0f));
        addView(frameLayout, LayoutHelper.createLinear(24, 24, 16, 0, 0, 6, 0));
        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(1, 14.0f);
        textView.setTypeface(AndroidUtilities.regular());
        textView.setText(charSequence);
        addView(textView, LayoutHelper.createLinear(-2, -2, 16));
        setOnClickListener(new View.OnClickListener() { // from class: com.exteragram.messenger.components.CheckBoxRow$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CheckBoxRow.$r8$lambda$3vuXnORUzuzxWlFKMBwpGVji5CU(CheckBoxRow.this, view);
            }
        });
    }

    public final Function1<? super Boolean, Unit> getOnCheckedChange() {
        return this.onCheckedChange;
    }

    public final void setOnCheckedChange(Function1<? super Boolean, Unit> function1) {
        this.onCheckedChange = function1;
    }

    public final boolean isChecked() {
        return this.checkBox.isChecked();
    }

    public final void setChecked(boolean z) {
        this.checkBox.setChecked(z, true);
    }

    public static void $r8$lambda$3vuXnORUzuzxWlFKMBwpGVji5CU(CheckBoxRow checkBoxRow, View view) {
        CheckBox2 checkBox2 = checkBoxRow.checkBox;
        checkBox2.setChecked(!checkBox2.isChecked(), true);
        Function1<? super Boolean, Unit> function1 = checkBoxRow.onCheckedChange;
        if (function1 != null) {
            function1.invoke(Boolean.valueOf(checkBoxRow.checkBox.isChecked()));
        }
    }
}

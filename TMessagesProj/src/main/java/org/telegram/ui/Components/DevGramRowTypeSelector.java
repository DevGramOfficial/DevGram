package org.telegram.ui.Components;

import android.R;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.ui.*;
import org.telegram.ui.ActionBar.*;
import java.util.function.IntConsumer;


/** Native adaptation of the supplied plugin's row type cards and full-screen catalog. */
public final class DevGramRowTypeSelector extends LinearLayout {
    private final int accent;
    private Dialog catalogDialog;
    private final int ink;
    private final int muted;
    private final BaseFragment profile;
    private int selected;
    private final IntConsumer sink;
    private final Runnable policy=this::accessChanged;
    private long accessMask=Long.MIN_VALUE;
    private long currentAccessMask(){long mask=DevGramProfileCustomization.enabled()?1:0;for(int i=0;i<DETAILS.length;i++)if(DevGramAppearanceAccess.allowed(profile,DevGramProfileRowSpec.feature(i)))mask|=2L<<i;return mask;}
    private void accessChanged(){long mask=currentAccessMask();if(mask==accessMask)return;accessMask=mask;rebuild();if(catalogDialog!=null&&catalogDialog.isShowing()){catalogDialog.dismiss();if(DevGramProfileCustomization.enabled())openCatalog();}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();DevGramProfilePolicy.listen(policy);}
    private static final int[][] GROUPS = {new int[]{1, 6, 3}, new int[]{0, 8, 9}, new int[]{10, 11, 12}};
    private static final String[] ICONS = {"msg_link", "msg_info", "msg_folders", "msg_copy", "msg_message", "msg_settings", "msg_edit", "msg_emoji_cat", "msg_notifications", "msg_fave", "msg_gallery", "msg_theme", "msg_media"};
    private static final String[] DETAILS = {"Подпись с иконкой и действием. Адрес справа и фон иконки можно включить отдельно.", "Значение сверху, подпись снизу — как телефон в профиле Telegram.", "Название раздела. Отделяет одну группу строк от другой.", "Пустой промежуток между блоками, без текста и действий.", "Небольшое пояснение под блоками. Ссылки в тексте можно открыть.", "Строка с цветной иконкой — как пункт настроек Telegram. Действие выбирается отдельно.", "Большой текст со ссылками, который можно развернуть по нажатию.", "Подпись с эмодзи или стикером. Это строка, а не отдельная большая картинка.", "Тумблер с подписью. Каждый посетитель переключает его только у себя.", "Общее число нажатий: посетители увеличивают один счётчик.", "Отдельное изображение со скруглением. Подходит для фото и иллюстраций.", "Общее пиксельное полотно, на котором могут рисовать посетители.", "Карточка музыки, Steam, GitHub и других сервисов с обновляемыми данными."};

    public DevGramRowTypeSelector(Context context, BaseFragment obj, int i, IntConsumer intSink) {
        super(context);
        this.profile = obj;
        this.accent = themeColor("key_windowBackgroundWhiteBlueText", -11625473);
        this.ink = themeColor("key_windowBackgroundWhiteBlackText", -1381654);
        this.muted = themeColor("key_windowBackgroundWhiteGrayText", -6710887);
        this.sink = intSink;
        this.selected = i == 5 ? 0 : Math.max(0, Math.min(i, DevGramProfileRowSpec.labels().length - 1));
        setOrientation(1);
        setPadding(AndroidUtilities.dp(16.0f), 0, AndroidUtilities.dp(16.0f), AndroidUtilities.dp(8.0f));
        accessMask=currentAccessMask();rebuild();
    }

    private void haptic(View view){DevGramStudioControls.haptic(view,android.view.HapticFeedbackConstants.CLOCK_TICK);}
    private int themeColor(String key,int fallback){int field;switch(key){case "key_windowBackgroundWhiteBlueText":field=Theme.key_windowBackgroundWhiteBlueText;break;case "key_windowBackgroundWhiteBlackText":field=Theme.key_windowBackgroundWhiteBlackText;break;case "key_windowBackgroundWhiteGrayText":field=Theme.key_windowBackgroundWhiteGrayText;break;case "key_windowBackgroundGray":field=Theme.key_windowBackgroundGray;break;case "key_actionBarDefault":field=Theme.key_actionBarDefault;break;case "key_actionBarDefaultTitle":field=Theme.key_actionBarDefaultTitle;break;default:field=Theme.key_windowBackgroundWhite;}return Theme.getColor(field,profile.getResourceProvider());}

    private TextView label(CharSequence str, float f, int i, boolean z) {
        TextView textView = new TextView(getContext());
        textView.setText(str instanceof String?DevGramLocale.text((String)str):str);
        textView.setTextSize(f);
        textView.setTextColor(i);
        if (z) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return textView;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void rebuild() {
        removeAllViews();
        View viewCard = card(this.selected, true);
        viewCard.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                haptic(view);
                DevGramRowTypeSelector.this.openCatalog();
            }
        });
        addView(viewCard);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Multi-variable type inference failed */
    public void openCatalog() {
        View textView;
        Dialog dialog = this.catalogDialog;
        if (dialog == null || !dialog.isShowing()) {
            Activity activityActivityOf = this.profile.getParentActivity();
            if (activityActivityOf == null) {
                activityActivityOf = null;
            }
            if (activityActivityOf == null || activityActivityOf.isFinishing()) {
                android.widget.Toast.makeText(getContext(),DevGramLocale.text("Выбор вида сейчас недоступен"),android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            final Dialog dialog2 = new Dialog(activityActivityOf, R.style.Theme_Material_NoActionBar);
            this.catalogDialog = dialog2;
            LinearLayout linearLayout = new LinearLayout(activityActivityOf);
            boolean z = true;
            linearLayout.setOrientation(1);
            linearLayout.setFitsSystemWindows(true);
            int iThemeColor = themeColor("key_windowBackgroundWhite", -14803426);
            int iThemeColor2 = themeColor("key_actionBarDefault", iThemeColor);
            int iThemeColor3 = themeColor("key_actionBarDefaultTitle", this.ink);
            linearLayout.setBackgroundColor(iThemeColor);
            LinearLayout linearLayout2 = new LinearLayout(activityActivityOf);
            linearLayout2.setGravity(16);
            linearLayout2.setBackgroundColor(iThemeColor2);
            int iDrawableId = DevGramProfileRowSpec.icon("ic_ab_back");
            boolean z2 = false;
            if (iDrawableId != 0) {
                ImageView imageView = new ImageView(activityActivityOf);
                imageView.setImageResource(iDrawableId);
                imageView.setColorFilter(iThemeColor3);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                textView = imageView;
            } else {
                TextView textViewLabel = label("‹", 32.0f, iThemeColor3, false);
                textViewLabel.setGravity(17);
                textView = textViewLabel;
            }
            textView.setContentDescription(DevGramLocale.text("Назад"));
            textView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector,profile.getResourceProvider()),2));
            textView.setOnClickListener(new View.OnClickListener() {
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    haptic(view);
                    dialog2.dismiss();
                }
            });
            linearLayout2.addView(textView, new LinearLayout.LayoutParams(AndroidUtilities.dp(56.0f), -1));
            linearLayout2.addView(label(DevGramLocale.text("Вид строки"), 20.0f, iThemeColor3, true), new LinearLayout.LayoutParams(0, -2, 1.0f));
            linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(56.0f)));
            ScrollView scrollView = new ScrollView(activityActivityOf);
            scrollView.setFillViewport(true);
            LinearLayout linearLayout3 = new LinearLayout(activityActivityOf);
            linearLayout3.setOrientation(1);
            linearLayout3.setPadding(AndroidUtilities.dp(16.0f), 0, AndroidUtilities.dp(16.0f), AndroidUtilities.dp(16.0f));
            scrollView.addView(linearLayout3, new FrameLayout.LayoutParams(-1, -2));
            linearLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            String[] strArr = {DevGramLocale.text("Текст и разделы"), DevGramLocale.text("Ссылки и действия"), DevGramLocale.text("Медиа и сервисы")};
            int i = 0;
            while (true) {
                int[][] iArr = GROUPS;
                if (i >= iArr.length) {
                    break;
                }
                TextView textViewLabel2 = label(strArr[i], 18.0f, this.ink, z);
                textViewLabel2.setPadding(AndroidUtilities.dp(4.0f), AndroidUtilities.dp(20.0f), AndroidUtilities.dp(4.0f), AndroidUtilities.dp(10.0f));
                if (Build.VERSION.SDK_INT >= 28) {
                    textViewLabel2.setAccessibilityHeading(z);
                }
                linearLayout3.addView(textViewLabel2);
                int[] iArr2 = iArr[i];
                int length = iArr2.length;
                int i2 = 0;
                while (i2 < length) {
                    final int i3 = iArr2[i2];
                    View viewCard = card(i3, z2);
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
                    layoutParams.bottomMargin = AndroidUtilities.dp(10.0f);
                    linearLayout3.addView(viewCard, layoutParams);
                    viewCard.setOnClickListener(new View.OnClickListener() {
                        @Override // android.view.View.OnClickListener
                        public void onClick(View view) {
                            haptic(view);
                            if(!DevGramAppearanceAccess.check(profile,DevGramProfileRowSpec.feature(i3)))return;
                            int i4 = DevGramRowTypeSelector.this.selected;
                            int i5 = i3;
                            boolean z3 = i4 != i5;
                            DevGramRowTypeSelector.this.selected = i5;
                            dialog2.dismiss();
                            DevGramRowTypeSelector.this.rebuild();
                            if (!z3 || DevGramRowTypeSelector.this.sink == null) {
                                return;
                            }
                            DevGramRowTypeSelector.this.sink.accept(i3);
                        }
                    });
                    i2++;
                    z2 = false;
                }
                i++;
                z = true;
                z2 = false;
            }
            dialog2.setContentView(linearLayout);
            dialog2.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override // android.content.DialogInterface.OnDismissListener
                public void onDismiss(DialogInterface dialogInterface) {
                    if (DevGramRowTypeSelector.this.catalogDialog == dialog2) {
                        DevGramRowTypeSelector.this.catalogDialog = null;
                    }
                }
            });
            dialog2.show();
            Window window = dialog2.getWindow();
            if (window != null) {
                window.setLayout(-1, -1);
                window.setBackgroundDrawable(new ColorDrawable(iThemeColor));
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(iThemeColor2);
                window.setNavigationBarColor(iThemeColor);
                int i4 = (Build.VERSION.SDK_INT < 23 || !light(iThemeColor2)) ? 0 : 8192;
                if (Build.VERSION.SDK_INT >= 26 && light(iThemeColor)) {
                    i4 |= 16;
                }
                window.getDecorView().setSystemUiVisibility(i4);
            }
        }
    }

    private static boolean light(int i) {
        return ((((float) Color.red(i)) * 0.299f) + (((float) Color.green(i)) * 0.587f)) + (((float) Color.blue(i)) * 0.114f) > 160.0f;
    }

    @Override // android.view.ViewGroup, android.view.View
    protected void onDetachedFromWindow() {
        DevGramProfilePolicy.unlisten(policy);
        Dialog dialog = this.catalogDialog;
        if (dialog != null) {
            dialog.dismiss();
        }
        super.onDetachedFromWindow();
    }

    private View card(int i, boolean z) {
        String strT;
        LinearLayout linearLayout = new LinearLayout(getContext());
        linearLayout.setOrientation(1);
        linearLayout.setPadding(AndroidUtilities.dp(14.0f), AndroidUtilities.dp(12.0f), AndroidUtilities.dp(14.0f), AndroidUtilities.dp(10.0f));
        int iThemeColor = themeColor("key_windowBackgroundGray", -14342875);
        boolean z2 = i == this.selected;
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(iThemeColor);
        gradientDrawable.setCornerRadius(AndroidUtilities.dpf2(8.0f));
        gradientDrawable.setStroke(AndroidUtilities.dp(1.0f), z2 ? this.accent : androidx.core.graphics.ColorUtils.blendARGB(iThemeColor, this.ink, 0.12f));
        linearLayout.setBackground(new RippleDrawable(ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(this.accent, 35)), gradientDrawable, null));
        LinearLayout linearLayout2 = new LinearLayout(getContext());
        linearLayout2.setGravity(16);
        ImageView imageView = new ImageView(getContext());
        int iDrawableId = DevGramProfileRowSpec.icon(ICONS[i]);
        if (iDrawableId != 0) {
            imageView.setImageResource(iDrawableId);
        }
        imageView.setColorFilter(this.accent);
        linearLayout2.addView(imageView, new LinearLayout.LayoutParams(AndroidUtilities.dp(24.0f), AndroidUtilities.dp(24.0f)));
        TextView textViewLabel = label(DevGramAppearanceAccess.label(profile,DevGramProfileRowSpec.feature(i),DevGramProfileRowSpec.labels()[i]), 16.0f, this.ink, true);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = AndroidUtilities.dp(10.0f);
        linearLayout2.addView(textViewLabel, layoutParams);
        if (z2) {
            TextView textViewLabel2 = label("✓", 19.0f, this.accent, true);
            textViewLabel2.setPadding(AndroidUtilities.dp(8.0f), 0, 0, 0);
            linearLayout2.addView(textViewLabel2);
        }
        linearLayout.addView(linearLayout2);
        String[] strArr = DETAILS;
        TextView textViewLabel3 = label(strArr[i], 13.0f, this.muted, false);
        textViewLabel3.setPadding(0, AndroidUtilities.dp(6.0f), 0, AndroidUtilities.dp(10.0f));
        linearLayout.addView(textViewLabel3);
        FrameLayout frameLayout = new FrameLayout(getContext()) {
            @Override // android.view.ViewGroup
            public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
                return true;
            }
        };
        frameLayout.setImportantForAccessibility(4);
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setColor(themeColor("key_windowBackgroundWhite", -14803426));
        gradientDrawable2.setCornerRadius(AndroidUtilities.dpf2(6.0f));
        if (i != 6) {
            frameLayout.setBackground(gradientDrawable2);
        }
        frameLayout.setClipToOutline(true);
        frameLayout.addView(preview(i), new FrameLayout.LayoutParams(-1, -2));
        linearLayout.addView(frameLayout, new LinearLayout.LayoutParams(-1, -2));
        if (z) {
            TextView textViewLabel4 = label(DevGramLocale.text("Выбрать другой вид ›"), 14.0f, this.accent, true);
            textViewLabel4.setPadding(0, AndroidUtilities.dp(10.0f), 0, AndroidUtilities.dp(2.0f));
            linearLayout.addView(textViewLabel4);
        }
        linearLayout2.setImportantForAccessibility(4);
        textViewLabel3.setImportantForAccessibility(2);
        StringBuilder sb = new StringBuilder();
        sb.append(DevGramProfileRowSpec.labels()[i]);
        sb.append(". ");
        sb.append(DevGramLocale.text(strArr[i]));
        if (z) {
            strT = DevGramLocale.text(". Нажмите для выбора вида");
        } else {
            strT = z2 ? DevGramLocale.text(". Выбрано") : "";
        }
        sb.append(strT);
        linearLayout.setContentDescription(sb.toString());
        linearLayout.setSelected(z2);
        return linearLayout;
    }

    private View preview(int i) {
        String strT;
        Bitmap bitmap;
        if (i == 10 || i == 11 || i == 12 || i == 7 || i == 3) {
            return new Illustration(getContext(), i);
        }
        String strT2 = DevGramLocale.text("Мой сайт");
        String strT3 = DevGramLocale.text("Здесь можно рассказать о себе и добавить ссылки. Нажмите, чтобы прочитать больше.");
        if (i == 1) {
            strT2 = DevGramLocale.text("Город");
            strT = DevGramLocale.text("Москва");
        } else {
            strT = "example.com";
        }
        if (i == 2) {
            strT2 = DevGramLocale.text("Обо мне");
        }
        String str = "";
        if (i == 6) {
            strT2 = DevGramLocale.text("Обо мне");
            strT = "";
        }
        if (i == 4) {
            strT3 = DevGramLocale.text("Больше подробностей — по ссылке в профиле.");
        }
        if (i == 5) {
            strT2 = DevGramLocale.text("Мой канал");
        } else {
            str = strT;
        }
        if (i == 8) {
            strT2 = DevGramLocale.text("Мне нравится");
        }
        if (i == 9) {
            strT2 = DevGramLocale.text("Поддержать");
            str = "42";
        }
        org.json.JSONObject row=DevGramProfileRowSpec.create();DevGramProfileRowSpec.type(row,i);
        try{row.put("id","type-preview").put("title",strT2).put("text",strT3).put("url",str).put("icon",ICONS[i]);}catch(Exception ignored){}
        return new DevGramNativeProfileRow(getContext(),profile.getCurrentAccount(),profile.getUserConfig().getClientUserId(),row,new org.json.JSONObject(),profile.getResourceProvider(),true);
    }

    private final class Illustration extends View {
        private final Paint paint;
        private final int type;

        Illustration(Context context, int i) {
            super(context);
            this.paint = new Paint(1);
            this.type = i;
        }

        @Override // android.view.View
        protected void onMeasure(int i, int i2) {
            setMeasuredDimension(View.MeasureSpec.getSize(i), AndroidUtilities.dp(this.type == 3 ? 36.0f : 80.0f));
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            String strT;
            float width = getWidth();
            float height = getHeight();
            float fDpf = AndroidUtilities.dpf2(12.0f);
            this.paint.setColor(androidx.core.graphics.ColorUtils.blendARGB(themeColor("key_windowBackgroundWhite", -14803426), DevGramRowTypeSelector.this.accent, 0.15f));
            int i = this.type;
            if (i == 11) {
                float f = fDpf * 2.0f;
                float fMin = Math.min((width - f) / 12.0f, (height - f) / 5.0f);
                float f2 = (width - (12.0f * fMin)) / 2.0f;
                int i2 = 0;
                while (i2 < 5) {
                    int i3 = 0;
                    while (i3 < 12) {
                        this.paint.setColor(((i3 + i2) % 5 == 0 || (i2 == 2 && i3 > 3 && i3 < 9)) ? DevGramRowTypeSelector.this.accent : androidx.core.graphics.ColorUtils.setAlphaComponent(DevGramRowTypeSelector.this.muted, 35));
                        int i4 = i3 + 1;
                        canvas.drawRect((i3 * fMin) + f2, (i2 * fMin) + fDpf, ((i4 * fMin) + f2) - 1.0f, (((i2 + 1) * fMin) + fDpf) - 1.0f, this.paint);
                        i3 = i4;
                    }
                    i2++;
                }
                return;
            }
            if (i == 10) {
                float f3 = width - fDpf;
                float f4 = height - fDpf;
                canvas.drawRoundRect(fDpf, fDpf, f3, f4, AndroidUtilities.dpf2(6.0f), AndroidUtilities.dpf2(6.0f), this.paint);
                this.paint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(DevGramRowTypeSelector.this.accent, 150));
                Path path = new Path();
                path.moveTo(fDpf, f4);
                path.lineTo(0.35f * width, 0.3f * height);
                path.lineTo(0.56f * width, 0.65f * height);
                path.lineTo(width * 0.72f, height * 0.45f);
                path.lineTo(f3, f4);
                path.close();
                canvas.drawPath(path, this.paint);
                return;
            }
            if (i != 12) {
                this.paint.setColor(i == 3 ? DevGramRowTypeSelector.this.muted : DevGramRowTypeSelector.this.ink);
                this.paint.setTextSize(AndroidUtilities.dpf2(14.0f));
                if (this.type == 3) {
                    strT = DevGramLocale.text("↕  Промежуток между блоками");
                } else {
                    strT = DevGramLocale.text("⭐  Любимый эмодзи");
                }
                canvas.drawText(strT, fDpf, height * 0.55f, this.paint);
                return;
            }
            float f5 = height - fDpf;
            canvas.drawRoundRect(fDpf, fDpf, f5, f5, AndroidUtilities.dpf2(6.0f), AndroidUtilities.dpf2(6.0f), this.paint);
            this.paint.setColor(DevGramRowTypeSelector.this.accent);
            this.paint.setTextSize(AndroidUtilities.dpf2(26.0f));
            canvas.drawText("♪", 0.35f * height, 0.62f * height, this.paint);
            this.paint.setColor(DevGramRowTypeSelector.this.ink);
            this.paint.setTextSize(AndroidUtilities.dpf2(14.0f));
            canvas.drawText(DevGramLocale.text("Название трека"), AndroidUtilities.dpf2(4.0f) + height, AndroidUtilities.dpf2(28.0f), this.paint);
            this.paint.setColor(DevGramRowTypeSelector.this.muted);
            this.paint.setTextSize(AndroidUtilities.dpf2(12.0f));
            canvas.drawText(DevGramLocale.text("Исполнитель"), AndroidUtilities.dpf2(4.0f) + height, AndroidUtilities.dpf2(47.0f), this.paint);
            this.paint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(DevGramRowTypeSelector.this.muted, 40));
            float f6 = height + 4.0f;
            float f7 = f5 - 4.0f;
            canvas.drawRoundRect(f6, f7, width - fDpf, f5, 2.0f, 2.0f, this.paint);
            this.paint.setColor(DevGramRowTypeSelector.this.accent);
            canvas.drawRoundRect(f6, f7, height + ((width - height) * 0.45f), f5, 2.0f, 2.0f, this.paint);
        }
    }
}

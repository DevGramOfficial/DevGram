package org.telegram.ui.Components;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.lang.reflect.Field;

public final class DevGramLyricsPicker {

    public interface Ask {
        void asked(String str, int i);
    }

    public interface Pick {
        void picked(int i);
    }

    public static final class Handle {
        private LinearLayout box;
        private Card card;
        private boolean closed;
        private boolean collapsed;
        private boolean collapsing;
        private Dialog dialog;
        private View[] fading;
        private TextView mark;
        private Boolean pending;
        private LinearLayout row;
        private ProgressBar spinner;

        Handle() {
        }

        public void dismiss(){if(dialog!=null)dialog.dismiss();}
        public void collapse(String str, int i) {
            if (this.collapsing || this.closed) {
                return;
            }
            this.collapsing = true;
            final int height = this.card.getHeight();
            this.row.setAlpha(0.0f);
            this.row.setVisibility(0);
            ((TextView) this.row.getChildAt(1)).setText(str);
            this.row.measure(View.MeasureSpec.makeMeasureSpec(this.card.getWidth(), 1073741824), View.MeasureSpec.makeMeasureSpec(0, 0));
            final int measuredHeight = this.row.getMeasuredHeight() + this.box.getPaddingTop() + this.box.getPaddingBottom();
            final ViewGroup.LayoutParams layoutParams = this.card.getLayoutParams();
            layoutParams.height = height;
            this.card.setLayoutParams(layoutParams);
            this.card.kick(-0.1f);
            ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(0.0f, 1.0f);
            valueAnimatorOfFloat.setDuration(420L);
            valueAnimatorOfFloat.setInterpolator(new DecelerateInterpolator(1.6f));
            valueAnimatorOfFloat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: cpb.DevGramLyricsPicker.Handle.1
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fFloatValue = ((Float) valueAnimator.getAnimatedValue()).floatValue();
                    ViewGroup.LayoutParams layoutParams2 = layoutParams;
                    int i2 = height;
                    layoutParams2.height = Math.round(i2 + ((measuredHeight - i2) * fFloatValue));
                    Handle.this.card.setLayoutParams(layoutParams);
                    float fMax = Math.max(0.0f, 1.0f - (2.2f * fFloatValue));
                    for (View view : Handle.this.fading) {
                        view.setAlpha(fMax);
                    }
                    Handle.this.row.setAlpha(Math.max(0.0f, (fFloatValue - 0.35f) / 0.65f));
                }
            });
            valueAnimatorOfFloat.addListener(new AnimatorListenerAdapter() { // from class: cpb.DevGramLyricsPicker.Handle.2
                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationEnd(Animator animator) {
                    for (View view : Handle.this.fading) {
                        view.setVisibility(8);
                    }
                    Handle.this.collapsed = true;
                    Handle.this.card.kick(0.06f);
                    if (Handle.this.pending != null) {
                        Handle handle = Handle.this;
                        handle.finish(handle.pending.booleanValue());
                    }
                }
            });
            valueAnimatorOfFloat.start();
            this.card.postDelayed(new Runnable() { // from class: cpb.DevGramLyricsPicker.Handle.3
                @Override // java.lang.Runnable
                public void run() {
                    Handle.this.finish(false);
                }
            }, 15000L);
        }

        public void finish(boolean z) {
            if (this.closed) {
                return;
            }
            if (!this.collapsed) {
                this.pending = Boolean.valueOf(z);
                return;
            }
            this.closed = true;
            this.spinner.setVisibility(8);
            this.mark.setText(z ? "✓" : "—");
            this.mark.setVisibility(0);
            this.mark.setScaleX(0.4f);
            this.mark.setScaleY(0.4f);
            this.mark.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180L).start();
            this.card.kick(0.05f);
            this.card.postDelayed(new Runnable() { // from class: cpb.DevGramLyricsPicker.Handle.4
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        Handle.this.dialog.dismiss();
                    } catch (Throwable unused) {
                    }
                }
            }, z ? 420L : 700L);
        }
    }

    private static int alpha(int color,int amount){return (color&0xffffff)|(Math.max(0,Math.min(255,amount))<<24);}
    private static View style(View view){ScaleStateListAnimator.apply(view);return view;}
    private DevGramLyricsPicker() {
    }

    public static Handle show(Activity activity, Bitmap bitmap, boolean z, final int i, String str, String str2, final String[] strArr, int i2, final Pick pick) {
        final Handle handle = new Handle();
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(1);
        final Card card = new Card(activity, bitmap, z);
        int iDp = AndroidUtilities.dp(18.0f);
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(iDp, AndroidUtilities.dp(18.0f), iDp, AndroidUtilities.dp(14.0f));
        TextView textView = new TextView(activity);
        textView.setText(str);
        textView.setTextSize(18.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(i);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout.addView(textView);
        TextView textView2 = new TextView(activity);
        textView2.setText(str2);
        textView2.setTextSize(13.0f);
        textView2.setTextColor(alpha(i, 153));
        textView2.setSingleLine(true);
        textView2.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.topMargin = AndroidUtilities.dp(2.0f);
        layoutParams.bottomMargin = AndroidUtilities.dp(12.0f);
        linearLayout.addView(textView2, layoutParams);
        int i3 = activity.getResources().getDisplayMetrics().heightPixels;
        final TextView textView3 = new TextView(activity);
        final GradientDrawable gradientDrawable = new GradientDrawable();
        final boolean[] zArr = {false};
        final GlassList glassList = new GlassList(activity, card, z, i, strArr, i2, (int) (i3 * 0.6f), new GlassList.Done() { // from class: cpb.DevGramLyricsPicker.1
            @Override // cpb.DevGramLyricsPicker.GlassList.Done
            public void changed(final boolean z2) {
                boolean[] zArr2 = zArr;
                if (zArr2[0] == z2) {
                    return;
                }
                zArr2[0] = z2;
                textView3.animate().cancel();
                textView3.animate().alpha(0.0f).scaleX(0.94f).scaleY(0.94f).setDuration(90L).withEndAction(new Runnable() { // from class: cpb.DevGramLyricsPicker.1.1
                    @Override // java.lang.Runnable
                    public void run() {
                        String str3;
                        String str4;
                        TextView textView4 = textView3;
                        if (z2) {
                            str3 = "Готово";
                            str4 = "Done";
                        } else {
                            str3 = "Отмена";
                            str4 = "Cancel";
                        }
                        textView4.setText(DevGramLocale.text(str3, str4));
                        textView3.setTextColor(z2 ? i : alpha(i, 179));
                        textView3.setTypeface(z2 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                        gradientDrawable.setColor(alpha(i, z2 ? 42 : 14));
                        textView3.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(140L).start();
                    }
                }).start();
            }
        });
        card.list = glassList;
        android.widget.ScrollView choices=new android.widget.ScrollView(activity);choices.addView(glassList);linearLayout.addView(choices,new LinearLayout.LayoutParams(-1,Math.min((int)(i3*.52f),glassList.names.length*(glassList.rowH+glassList.gap)+AndroidUtilities.dp(8))));
        textView3.setText(DevGramLocale.text("Отмена", "Cancel"));
        textView3.setTextSize(15.0f);
        textView3.setTextColor(alpha(i, 179));
        textView3.setGravity(17);
        textView3.setPadding(AndroidUtilities.dp(16.0f), AndroidUtilities.dp(10.0f), AndroidUtilities.dp(16.0f), AndroidUtilities.dp(10.0f));
        gradientDrawable.setColor(alpha(i, 14));
        gradientDrawable.setCornerRadius(AndroidUtilities.dp(14.0f));
        gradientDrawable.setStroke(AndroidUtilities.dp(1.0f), alpha(z ? -1 : -16777216, 34));
        textView3.setBackground(gradientDrawable);
        style(textView3);
        textView3.setOnClickListener(new View.OnClickListener() { // from class: cpb.DevGramLyricsPicker.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                if (!zArr[0]) {
                    dialog.dismiss();
                    return;
                }
                glassList.lock();
                textView3.setEnabled(false);
                handle.collapse(strArr[glassList.selected()], i);
                pick.picked(glassList.selected());
            }
        });
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = AndroidUtilities.dp(10.0f);
        linearLayout.addView(textView3, layoutParams2);
        LinearLayout linearLayout2 = new LinearLayout(activity);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(17);
        linearLayout2.setVisibility(8);
        ProgressBar progressBar = new ProgressBar(activity);
        progressBar.setIndeterminate(true);
        try {
            progressBar.getIndeterminateDrawable().setColorFilter(i, PorterDuff.Mode.SRC_IN);
        } catch (Throwable unused) {
        }
        linearLayout2.addView(progressBar, new LinearLayout.LayoutParams(AndroidUtilities.dp(22.0f), AndroidUtilities.dp(22.0f)));
        TextView textView4 = new TextView(activity);
        textView4.setTextSize(15.0f);
        textView4.setTypeface(Typeface.DEFAULT_BOLD);
        textView4.setTextColor(i);
        textView4.setSingleLine(true);
        textView4.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams3.leftMargin = AndroidUtilities.dp(12.0f);
        linearLayout2.addView(textView4, layoutParams3);
        TextView textView5 = new TextView(activity);
        textView5.setText("✓");
        textView5.setTextSize(18.0f);
        textView5.setTypeface(Typeface.DEFAULT_BOLD);
        textView5.setTextColor(i);
        textView5.setVisibility(8);
        LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams4.leftMargin = AndroidUtilities.dp(10.0f);
        linearLayout2.addView(textView5, layoutParams4);
        linearLayout2.setPadding(0, AndroidUtilities.dp(6.0f), 0, AndroidUtilities.dp(6.0f));
        linearLayout.addView(linearLayout2, 0, new LinearLayout.LayoutParams(-1, -2));
        handle.dialog = dialog;
        handle.card = card;
        handle.box = linearLayout;
        handle.row = linearLayout2;
        handle.spinner = progressBar;
        handle.mark = textView5;
        handle.fading = new View[]{textView, textView2, glassList, textView3};
        card.addView(linearLayout, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout frameLayout = new FrameLayout(activity);
        frameLayout.setClipChildren(false);
        frameLayout.setClipToPadding(false);
        frameLayout.addView(card, new FrameLayout.LayoutParams(Math.min(activity.getResources().getDisplayMetrics().widthPixels - AndroidUtilities.dp(40.0f), AndroidUtilities.dp(360.0f)), -2, 17));
        frameLayout.setOnClickListener(new View.OnClickListener() { // from class: cpb.DevGramLyricsPicker.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                if (handle.collapsing) {
                    return;
                }
                dialog.dismiss();
            }
        });
        card.setClickable(true);
        dialog.setContentView(frameLayout);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.setLayout(-1, -1);
            window.setDimAmount(0.5f);
            View decorView = window.getDecorView();
            if (decorView instanceof ViewGroup) {
                ((ViewGroup) decorView).setClipChildren(false);
            }
        }
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: cpb.DevGramLyricsPicker.4
            @Override // android.content.DialogInterface.OnDismissListener
            public void onDismiss(DialogInterface dialogInterface) {
                card.release();
            }
        });
        dialog.show();
        return handle;
    }

                    public static android.app.Dialog ask(Activity activity, Bitmap bitmap, boolean z, int i, String str, String str2, String str3, String[] strArr, final int i2, String str4, final Ask ask) {

        GlassList glassList;
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(1);
        final Card card = new Card(activity, bitmap, z);
        int iDp = AndroidUtilities.dp(18.0f);
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(iDp, AndroidUtilities.dp(18.0f), iDp, AndroidUtilities.dp(14.0f));
        TextView textView = new TextView(activity);
        textView.setText(str);
        textView.setTextSize(18.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(i);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout.addView(textView);
        TextView textView2 = new TextView(activity);
        textView2.setText(str2);
        textView2.setTextSize(13.0f);
        textView2.setTextColor(alpha(i, 153));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = AndroidUtilities.dp(2.0f);
        layoutParams.bottomMargin = AndroidUtilities.dp(12.0f);
        linearLayout.addView(textView2, layoutParams);
        final EditText editText = new EditText(activity);
        editText.setHint(str3);
        editText.setSingleLine(true);
        editText.setTextSize(15.0f);
        editText.setTextColor(i);
        editText.setHintTextColor(alpha(i, 128));
        editText.setPadding(AndroidUtilities.dp(16.0f), AndroidUtilities.dp(12.0f), AndroidUtilities.dp(16.0f), AndroidUtilities.dp(12.0f));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(alpha(i, 20));
        gradientDrawable.setCornerRadius(AndroidUtilities.dp(14.0f));
        gradientDrawable.setStroke(AndroidUtilities.dp(1.0f), alpha(z ? -1 : -16777216, 34));
        editText.setBackground(gradientDrawable);
        try {
            Field declaredField = TextView.class.getDeclaredField("mCursorDrawableRes");
            declaredField.setAccessible(true);
            declaredField.set(editText, 0);
        } catch (Throwable unused) {
        }
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.bottomMargin = AndroidUtilities.dp(10.0f);
        linearLayout.addView(editText, layoutParams2);
        int i3 = activity.getResources().getDisplayMetrics().heightPixels;
        if (strArr == null || strArr.length == 0) {

            glassList = null;
        } else {

            glassList = new GlassList(activity, card, z, i, strArr, i2, (int) (i3 * 0.4f), new GlassList.Done() { // from class: cpb.DevGramLyricsPicker.5
                @Override // cpb.DevGramLyricsPicker.GlassList.Done
                public void changed(boolean z2) {
                }
            });
        }
        if (glassList != null) {
            card.list = glassList;
            linearLayout.addView(glassList, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout linearLayout2 = new LinearLayout(activity);
        linearLayout2.setOrientation(0);
        TextView textViewPillButton = pillButton(activity, i, z, DevGramLocale.text("Отмена", "Cancel"), false);
        textViewPillButton.setOnClickListener(new View.OnClickListener() { // from class: cpb.DevGramLyricsPicker.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        TextView textViewPillButton2 = pillButton(activity, i, z, str4, true);
        final GlassList glassList2 = glassList;
        textViewPillButton2.setOnClickListener(new View.OnClickListener() { // from class: cpb.DevGramLyricsPicker.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                String strTrim = editText.getText().toString().trim();
                if (strTrim.length() == 0) {
                    editText.requestFocus();
                    card.kick(-0.06f);
                } else {
                    dialog.dismiss();
                    Ask ask2 = ask;
                    GlassList glassList3 = glassList2;
                    ask2.asked(strTrim, glassList3 == null ? i2 : glassList3.selected());
                }
            }
        });
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams3.rightMargin = AndroidUtilities.dp(4.0f);
        LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams4.leftMargin = AndroidUtilities.dp(4.0f);
        linearLayout2.addView(textViewPillButton, layoutParams3);
        linearLayout2.addView(textViewPillButton2, layoutParams4);
        LinearLayout.LayoutParams layoutParams5 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams5.topMargin = AndroidUtilities.dp(10.0f);
        linearLayout.addView(linearLayout2, layoutParams5);
        card.addView(linearLayout, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout frameLayout = new FrameLayout(activity);
        frameLayout.setClipChildren(false);
        frameLayout.setClipToPadding(false);
        frameLayout.addView(card, new FrameLayout.LayoutParams(Math.min(activity.getResources().getDisplayMetrics().widthPixels - AndroidUtilities.dp(40.0f), AndroidUtilities.dp(360.0f)), -2, 17));
        frameLayout.setOnClickListener(new View.OnClickListener() { // from class: cpb.DevGramLyricsPicker.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        card.setClickable(true);
        dialog.setContentView(frameLayout);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.setLayout(-1, -1);
            window.setDimAmount(0.5f);
            window.setSoftInputMode(20);
            View decorView = window.getDecorView();
            if (decorView instanceof ViewGroup) {
                ((ViewGroup) decorView).setClipChildren(false);
            }
        }
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: cpb.DevGramLyricsPicker.9
            @Override // android.content.DialogInterface.OnDismissListener
            public void onDismiss(DialogInterface dialogInterface) {
                card.release();
            }
        });
        dialog.show();
        editText.requestFocus();
        return dialog;
    }

    private static TextView pillButton(Activity activity, int i, boolean z, String str, boolean z2) {
        TextView textView = new TextView(activity);
        textView.setText(str);
        textView.setTextSize(15.0f);
        textView.setTypeface(z2 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        textView.setTextColor(z2 ? i : alpha(i, 179));
        textView.setGravity(17);
        textView.setPadding(AndroidUtilities.dp(16.0f), AndroidUtilities.dp(10.0f), AndroidUtilities.dp(16.0f), AndroidUtilities.dp(10.0f));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(alpha(i, z2 ? 42 : 14));
        gradientDrawable.setCornerRadius(AndroidUtilities.dp(14.0f));
        gradientDrawable.setStroke(AndroidUtilities.dp(1.0f), alpha(z ? -1 : -16777216, 34));
        textView.setBackground(gradientDrawable);
        return (TextView) style(textView);
    }

    static Bitmap blurred(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() >= 2 && bitmap.getHeight() >= 2) {
            try {
                int iMax = Math.max(2, Math.min(128, Math.round((bitmap.getHeight() * 14f) / bitmap.getWidth())));
                Bitmap tiny=Bitmap.createScaledBitmap(bitmap,14,iMax,true);try{return Bitmap.createScaledBitmap(tiny,112,iMax*8,true);}finally{if(tiny!=bitmap)tiny.recycle();}
            } catch (Throwable unused) {
            }
        }
        return null;
    }

    static final class GlassList extends View {
        private static final float LIFT_PILL = 0.1f;
        private static final float LIFT_TEXT = 0.14f;
        private final Card card;
        private final Path clip;
        private final boolean dark;
        private final Done done;
        private float dragOffset;
        private boolean dragging;
        private final Paint fill;
        private final Runnable frame;
        private final int gap;
        private int hover;
        private final int initial;
        private final int ink;
        private long lastFrame;
        private final Paint lens;
        private float lift;
        private boolean locked;
        private boolean looping;
        private final String[] names;
        final RectF pill;
        float radius;
        private final Paint rim;
        private final int rowH;
        private int selected;
        private final Paint shadow;
        private final Paint shine;
        private final Rect src;
        private float target;
        private final TextPaint text;
        private float v;
        private float y;

        interface Done {
            void changed(boolean z);
        }

        private void cancelSettle() {
        }

        static /* synthetic */ float access$1416(GlassList glassList, float f) {
            float f2 = glassList.y + f;
            glassList.y = f2;
            return f2;
        }

        static /* synthetic */ float access$1516(GlassList glassList, float f) {
            float f2 = glassList.v + f;
            glassList.v = f2;
            return f2;
        }

        static /* synthetic */ float access$1616(GlassList glassList, float f) {
            float f2 = glassList.lift + f;
            glassList.lift = f2;
            return f2;
        }

        GlassList(Context context, Card card, boolean z, int i, String[] strArr, int i2, int i3, Done done) {
            super(context);
            int iDp = AndroidUtilities.dp(6.0f);
            this.gap = iDp;
            this.hover = -1;
            TextPaint textPaint = new TextPaint(1);
            this.text = textPaint;
            this.fill = new Paint(1);
            this.shine = new Paint(1);
            Paint paint = new Paint(1);
            this.rim = paint;
            this.shadow = new Paint(1);
            this.lens = new Paint(3);
            this.clip = new Path();
            this.pill = new RectF();
            this.src = new Rect();
            this.frame = new Runnable() { // from class: cpb.DevGramLyricsPicker.GlassList.1
                @Override // java.lang.Runnable
                public void run() {
                    GlassList.this.looping = false;
                    long jNanoTime = System.nanoTime();
                    float fMin = GlassList.this.lastFrame == 0 ? 0.016f : Math.min(0.032f, (jNanoTime - GlassList.this.lastFrame) / 1.0E9f);
                    GlassList.this.lastFrame = jNanoTime;
                    float f = GlassList.this.dragging ? 900.0f : 240.0f;
                    GlassList.access$1516(GlassList.this, ((f * (GlassList.this.target - GlassList.this.y)) - (((float) ((Math.sqrt(f) * 2.0d) * (GlassList.this.dragging ? 0.85d : 0.5d))) * GlassList.this.v)) * fMin);
                    GlassList glassList = GlassList.this;
                    GlassList.access$1416(glassList, glassList.v * fMin);
                    float f2 = GlassList.this.dragging ? 1.0f : 0.0f;
                    GlassList glassList2 = GlassList.this;
                    GlassList.access$1616(glassList2, (f2 - glassList2.lift) * Math.min(1.0f, fMin * 11.0f));
                    if (Math.abs(f2 - GlassList.this.lift) < 0.01f) {
                        GlassList.this.lift = f2;
                    }
                    if (GlassList.this.dragging || Math.abs(GlassList.this.target - GlassList.this.y) >= 0.4f || Math.abs(GlassList.this.v) >= 4.0f || GlassList.this.lift != 0.0f) {
                        GlassList.this.loop();
                    } else {
                        GlassList glassList3 = GlassList.this;
                        glassList3.y = glassList3.target;
                        GlassList.this.v = 0.0f;
                        GlassList.this.lastFrame = 0L;
                    }
                    GlassList.this.place();
                    GlassList.this.invalidate();
                    GlassList.this.card.wobble();
                    GlassList.this.card.invalidate();
                }
            };
            this.card = card;
            this.dark = z;
            this.ink = i;
            this.names = strArr;
            this.done = done;
            int iDp2 = AndroidUtilities.dp(46.0f);
            this.rowH = Math.max(AndroidUtilities.dp(34.0f), Math.min(iDp2, strArr.length == 0 ? iDp2 : (i3 - ((strArr.length - 1) * iDp)) / strArr.length));
            int iMax = Math.max(0, Math.min(strArr.length - 1, i2));
            this.selected = iMax;
            this.initial = iMax;
            float fRowTop = rowTop(iMax);
            this.target = fRowTop;
            this.y = fRowTop;
            textPaint.setTextSize(AndroidUtilities.dp(15.0f));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(1.2f));
            setClickable(true);
            place();
        }

        private int rowTop(int i) {
            return i * (this.rowH + this.gap);
        }

        private int nearest(float f) {
            return Math.max(0, Math.min(this.names.length - 1, Math.round(f / (this.rowH + this.gap))));
        }

        @Override // android.view.View
        protected void onMeasure(int i, int i2) {
            String[] strArr = this.names;
            setMeasuredDimension(View.MeasureSpec.getSize(i), (strArr.length * this.rowH) + (Math.max(0, strArr.length - 1) * this.gap) + AndroidUtilities.dp(8.0f));
        }

        @Override // android.view.View
        public boolean onTouchEvent(MotionEvent motionEvent) {
            if (this.locked) {
                return true;
            }
            float y = motionEvent.getY();
            int actionMasked = motionEvent.getActionMasked();
            if (actionMasked == 0) {
                getParent().requestDisallowInterceptTouchEvent(y >= this.y - AndroidUtilities.dp(6) && y <= this.y + this.rowH + AndroidUtilities.dp(6));
                if (y >= this.y - AndroidUtilities.dp(6.0f) && y <= this.y + this.rowH + AndroidUtilities.dp(6.0f)) {
                    this.dragging = true;
                    this.dragOffset = y - this.y;
                    this.hover = this.selected;
                    cancelSettle();
                    loop();
                }
                return true;
            }
            if (actionMasked == 1) {
                if (this.dragging) {
                    this.dragging = false;
                    choose(nearest(this.target + (this.v * 0.06f)));
                } else {
                    int iNearest = nearest(y - (this.rowH / 2.0f));
                    if (iNearest != this.selected) {
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    }
                    choose(iNearest);
                }
                return true;
            }
            if (actionMasked != 2) {
                if (actionMasked == 3) {
                    if (this.dragging) {
                        this.dragging = false;
                        choose(nearest(this.target));
                    }
                    return true;
                }
                return super.onTouchEvent(motionEvent);
            }
            if (this.dragging) {
                float fMax = Math.max((-this.rowH) * 0.25f, Math.min(rowTop(this.names.length - 1) + (this.rowH * 0.25f), y - this.dragOffset));
                this.target = fMax;
                int iNearest2 = nearest(fMax);
                if (iNearest2 != this.hover) {
                    this.hover = iNearest2;
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                }
                loop();
            }
            return true;
        }

        private void choose(int i) {
            this.selected = i;
            this.hover = i;
            this.target = rowTop(i);
            loop();
            this.done.changed(i != this.initial);
        }

        int selected() {
            return this.selected;
        }

        void lock() {
            this.locked = true;
            this.dragging = false;
        }

        float velocity() {
            return this.v;
        }

        float lift() {
            return this.lift;
        }

                public void loop() {
            if (this.looping) {
                return;
            }
            this.looping = true;
            postOnAnimation(this.frame);
        }

                public void place() {
            int iMax = Math.max(1, getWidth());
            float fMin = Math.min(1.0f, Math.abs(this.v) / 2600.0f) * 0.28f;
            float f = (this.lift * LIFT_PILL) + 1.0f;
            int i = this.rowH;
            float f2 = i * (fMin + 1.0f) * f;
            float f3 = iMax;
            float f4 = (1.0f - (0.22f * fMin)) * f3 * f;
            float fSignum = this.y + (i / 2.0f) + (Math.signum(this.v) * fMin * this.rowH * 0.35f);
            float f5 = f3 / 2.0f;
            float f6 = f4 / 2.0f;
            float f7 = f2 / 2.0f;
            this.pill.set(f5 - f6, fSignum - f7, f5 + f6, fSignum + f7);
            this.radius = Math.min(AndroidUtilities.dp(16.0f), f7);
        }

        @Override // android.view.View
        protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
            super.onLayout(z, i, i2, i3, i4);
            place();
        }

        void drawRows(Canvas canvas, boolean z) {
            int width = getWidth();
            float fCenterY = this.pill.centerY();
            for (int i = 0; i < this.names.length; i++) {
                float fRowTop = rowTop(i) + (this.rowH / 2.0f);
                float fMin = 1.0f - Math.min(1.0f, Math.abs(fRowTop - fCenterY) / (this.rowH + this.gap));
                this.text.setColor(alpha(this.ink, z ? Math.round(79.0f * fMin) + 176 : 192));
                this.text.setTypeface((!z || fMin <= 0.5f) ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
                String string = TextUtils.ellipsize(this.names[i], this.text, width - AndroidUtilities.dp(36.0f), TextUtils.TruncateAt.END).toString();
                float fDescent = fRowTop - ((this.text.descent() + this.text.ascent()) / 2.0f);
                float f = (this.lift * LIFT_TEXT * fMin) + 1.0f;
                int iSave = canvas.save();
                if (f > 1.0f) {
                    canvas.scale(f, f, AndroidUtilities.dp(18.0f), fRowTop);
                }
                canvas.drawText(string, AndroidUtilities.dp(18.0f), fDescent, this.text);
                canvas.restoreToCount(iSave);
            }
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            if (!this.card.nativeGlass) {
                drawOwnPill(canvas);
            }
            drawRows(canvas, true);
        }

        private void drawOwnPill(Canvas canvas) {
            int width = getWidth();
            float f = this.radius;
            Bitmap bitmapBlurredCover = this.card.blurredCover();
            for (int i = 3; i >= 1; i--) {
                this.shadow.setColor(this.dark ? 167772160 : 301989888);
                float f2 = i;
                canvas.drawRoundRect(new RectF(this.pill.left + f2, this.pill.top + (AndroidUtilities.dp(2.0f) * i), this.pill.right - f2, this.pill.bottom + (AndroidUtilities.dp(2.0f) * i)), f, f, this.shadow);
            }
            this.clip.reset();
            this.clip.addRoundRect(this.pill, f, f, Path.Direction.CW);
            int iSave = canvas.save();
            canvas.clipPath(this.clip);
            if (bitmapBlurredCover != null && !bitmapBlurredCover.isRecycled()) {
                float width2 = bitmapBlurredCover.getWidth() / width;
                float height = bitmapBlurredCover.getHeight() / Math.max(1, getHeight());
                float fCenterX = this.pill.centerX() * width2;
                float fCenterY = this.pill.centerY() * height;
                float fWidth = ((this.pill.width() * width2) / 1.14f) / 2.0f;
                float fHeight = ((this.pill.height() * height) / 1.14f) / 2.0f;
                this.src.set(Math.round(fCenterX - fWidth), Math.round(fCenterY - fHeight), Math.round(fCenterX + fWidth), Math.round(fCenterY + fHeight));
                canvas.drawBitmap(bitmapBlurredCover, this.src, this.pill, this.lens);
            }
            this.fill.setShader(null);
            this.fill.setColor(this.dark ? 956301311 : -1929379841);
            canvas.drawRoundRect(this.pill, f, f, this.fill);
            this.shine.setShader(new LinearGradient(0.0f, this.pill.top, 0.0f, this.pill.top + (this.pill.height() * 0.55f), this.dark ? 1728053247 : -1275068417, 16777215, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(this.pill, f, f, this.shine);
            canvas.restoreToCount(iSave);
            Paint paint = this.rim;
            float f3 = this.pill.left;
            float f4 = this.pill.top;
            float f5 = this.pill.right;
            float f6 = this.pill.bottom;
            boolean z = this.dark;
            paint.setShader(new LinearGradient(f3, f4, f5, f6, z ? -1056964609 : -1, z ? 822083583 : 1728053247, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(this.pill, f, f, this.rim);
        }
    }

    static final class Card extends FrameLayout {
        private Bitmap blurredCover;
        private final RectF box;
        private final Path clip;
        private final Bitmap cover;
        private final boolean dark;
        private final Paint edge;
        private final Paint gloss;
        private final Paint glow;
        private final Runnable jelly;
        private long jellyFrame;
        private boolean jellyLoop;
        private float jellyVy;
        private float jellyY;
        GlassList list;
        boolean nativeGlass;
        private final Paint paint;
        private final RectF pillAt;
        private float squash;
        private float squashV;
        private final RectF src;
        private final Paint thinVeil;
        private final Rect tmp;
        private boolean told;
        private final Paint veil;

        static /* synthetic */ float access$2416(Card card, float f) {
            float f2 = card.jellyVy + f;
            card.jellyVy = f2;
            return f2;
        }

        static /* synthetic */ float access$2516(Card card, float f) {
            float f2 = card.jellyY + f;
            card.jellyY = f2;
            return f2;
        }

        static /* synthetic */ float access$2616(Card card, float f) {
            float f2 = card.squashV + f;
            card.squashV = f2;
            return f2;
        }

        static /* synthetic */ float access$2716(Card card, float f) {
            float f2 = card.squash + f;
            card.squash = f2;
            return f2;
        }

        Card(Context context, Bitmap bitmap, boolean z) {
            super(context);
            this.paint = new Paint(3);
            Paint paint = new Paint();
            this.veil = paint;
            Paint paint2 = new Paint();
            this.thinVeil = paint2;
            Paint paint3 = new Paint(1);
            this.edge = paint3;
            this.gloss = new Paint(1);
            this.clip = new Path();
            this.box = new RectF();
            this.src = new RectF();
            this.pillAt = new RectF();
            this.tmp = new Rect();
            this.jelly = new Runnable() { // from class: cpb.DevGramLyricsPicker.Card.1
                @Override // java.lang.Runnable
                public void run() {
                    boolean z2 = false;
                    Card.this.jellyLoop = false;
                    long jNanoTime = System.nanoTime();
                    float fMin = Card.this.jellyFrame == 0 ? 0.016f : Math.min(0.032f, (jNanoTime - Card.this.jellyFrame) / 1.0E9f);
                    Card.this.jellyFrame = jNanoTime;
                    int iMax = Math.max(1, Card.this.getHeight());
                    float fCenterY = Card.this.pillWhere() ? Card.this.pillAt.centerY() : iMax / 2.0f;
                    float f = (-(fCenterY - (iMax / 2.0f))) * 0.12f;
                    float fMax = (Card.this.list == null ? 0.0f : Math.max(-1.0f, Math.min(1.0f, Card.this.list.velocity() / 2600.0f))) * 0.06f;
                    float fSqrt = (float) (Math.sqrt(170.0f) * 2.0d * 0.5d);
                    Card card = Card.this;
                    Card.access$2416(card, (((f - card.jellyY) * 170.0f) - (fSqrt * Card.this.jellyVy)) * fMin);
                    Card card2 = Card.this;
                    Card.access$2516(card2, card2.jellyVy * fMin);
                    float fSqrt2 = (float) (Math.sqrt(240.0f) * 2.0d * 0.45d);
                    Card card3 = Card.this;
                    Card.access$2616(card3, (((fMax - card3.squash) * 240.0f) - (fSqrt2 * Card.this.squashV)) * fMin);
                    Card card4 = Card.this;
                    Card.access$2716(card4, card4.squashV * fMin);
                    if (Math.abs(f - Card.this.jellyY) < 0.3f && Math.abs(Card.this.jellyVy) < 3.0f && Math.abs(fMax - Card.this.squash) < 0.002f && Math.abs(Card.this.squashV) < 0.02f) {
                        z2 = true;
                    }
                    if (z2) {
                        Card.this.jellyFrame = 0L;
                        Card.this.squash = 0.0f;
                        Card.this.jellyY = f;
                    }
                    float fAbs = Math.abs(Card.this.squash);
                    Card card5 = Card.this;
                    card5.setPivotX(card5.getWidth() / 2.0f);
                    Card.this.setPivotY(fCenterY);
                    Card.this.setScaleY((0.9f * fAbs) + 1.0f);
                    Card.this.setScaleX(1.0f - (fAbs * 0.35f));
                    Card card6 = Card.this;
                    card6.setTranslationY(card6.jellyY * 0.45f);
                    if (!z2) {
                        Card.this.wobble();
                    }
                    Card.this.invalidate();
                }
            };
            this.glow = new Paint(1);
            this.cover = bitmap;
            this.dark = z;
            setWillNotDraw(false);
            paint.setColor(z ? -1509949440 : -1191182337);
            paint2.setColor(z ? 1493172224 : 1946157055);
            paint3.setStyle(Paint.Style.STROKE);
            paint3.setStrokeWidth(AndroidUtilities.dp(1.0f));
            this.nativeGlass = false;
        }

        Bitmap blurredCover() {
            if (this.blurredCover == null) {
                this.blurredCover = DevGramLyricsPicker.blurred(this.cover);
            }
            return this.blurredCover;
        }

        void release() {
            removeCallbacks(jelly);if(list!=null)list.removeCallbacks(list.frame);if(blurredCover!=null&&!blurredCover.isRecycled())blurredCover.recycle();blurredCover=null;
        }

                public boolean pillWhere() {
            GlassList glassList = this.list;
            if (glassList == null || glassList.getVisibility() != 0 || this.list.getAlpha() <= 0.01f) {
                return false;
            }
            this.tmp.set(0, 0, this.list.getWidth(), this.list.getHeight());
            offsetDescendantRectToMyCoords(this.list, this.tmp);
            this.pillAt.set(this.list.pill);
            this.pillAt.offset(this.tmp.left, this.tmp.top);
            return true;
        }

        void kick(float f) {
            this.squashV += f * 30.0f;
            wobble();
        }

        void wobble() {
            if (this.jellyLoop) {
                return;
            }
            this.jellyLoop = true;
            postOnAnimation(this.jelly);
        }

        private void drawLiveBack(Canvas canvas, int i, int i2, boolean z) {
            drawLiveBack(canvas, i, i2, z, true);
        }

        private void drawLiveBack(Canvas canvas, int i, int i2, boolean z, boolean z2) {
            boolean z3 = pillWhere() && z2;
            float fCenterY = z3 ? this.pillAt.centerY() : i2 / 2.0f;
            float fCenterX = z3 ? this.pillAt.centerX() : i / 2.0f;
            int iSave = canvas.save();
            float f = this.squash;
            float f2 = i;
            canvas.scale((1.0f - (0.5f * f)) * 1.1f, (f + 1.0f) * 1.1f, f2 / 2.0f, fCenterY);
            canvas.translate(0.0f, this.jellyY);
            drawCover(canvas, i, i2, z);
            canvas.restoreToCount(iSave);
            if (z3) {
                float f3 = f2 * 0.55f;
                int i3 = this.dark ? 956301311 : 1308622847;
                this.glow.setShader(new RadialGradient(fCenterX, fCenterY, f3, new int[]{i3, 1090519039 & i3, 16777215}, new float[]{0.0f, 0.45f, 1.0f}, Shader.TileMode.CLAMP));
                canvas.drawCircle(fCenterX, fCenterY, f3, this.glow);
            }
        }

        private void drawCover(Canvas canvas, int i, int i2, boolean z) {
            Bitmap bitmapBlurredCover = z ? this.cover : blurredCover();
            if (bitmapBlurredCover != null && !bitmapBlurredCover.isRecycled()) {
                float width = bitmapBlurredCover.getWidth();
                float height = bitmapBlurredCover.getHeight();
                float f = i;
                float f2 = i2;
                float fMax = Math.max(f / width, f2 / height);
                float f3 = width * fMax;
                float f4 = height * fMax;
                this.src.set((f - f3) / 2.0f, (f2 - f4) / 2.0f, (f + f3) / 2.0f, (f2 + f4) / 2.0f);
                canvas.drawBitmap(bitmapBlurredCover, (Rect) null, this.src, this.paint);
                return;
            }
            canvas.drawColor(this.dark ? -15066594 : -723721);
        }

        @Override // android.view.ViewGroup, android.view.View
        protected void dispatchDraw(Canvas canvas) {
            int width = getWidth();
            int height = getHeight();
            float fDp = AndroidUtilities.dp(26.0f);
            this.box.set(0.0f, 0.0f, width, height);
            drawOwn(canvas, width, height, fDp);
        }

        private void drawOwn(Canvas canvas, int i, int i2, float f) {
            this.clip.reset();
            this.clip.addRoundRect(this.box, f, f, Path.Direction.CW);
            int iSave = canvas.save();
            canvas.clipPath(this.clip);
            drawLiveBack(canvas, i, i2, false);
            canvas.drawRect(this.box, this.veil);
            float f2 = i2;
            this.gloss.setShader(new LinearGradient(0.0f, 0.0f, 0.0f, f2 * 0.35f, this.dark ? 587202559 : -2130706433, 16777215, Shader.TileMode.CLAMP));
            canvas.drawRect(this.box, this.gloss);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(iSave);
            Paint paint = this.edge;
            float f3 = i;
            boolean z = this.dark;
            paint.setShader(new LinearGradient(0.0f, 0.0f, f3, f2, z ? -2130706433 : -1, z ? 452984831 : 1090519039, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(this.box, f, f, this.edge);
        }
    }
}

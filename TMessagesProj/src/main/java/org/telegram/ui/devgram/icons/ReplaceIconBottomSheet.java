package org.telegram.ui.devgram.icons;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Locale;
import org.telegram.messenger.*;
import org.telegram.messenger.devgram.icons.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public final class ReplaceIconBottomSheet extends BottomSheet {
    private final BaseFragment parent;
    private final String packId,resource;
    private final int resId;
    private boolean waitingForResult;
    private File selected;
    private String extension,selectedName;
    private IconInfoView current,newIcon;
    private ButtonWithCounterView save,reset;
    public ReplaceIconBottomSheet(BaseFragment parent,String packId,int resId){
        super(parent.getParentActivity(),true,parent.getResourceProvider());this.parent=parent;this.packId=packId;this.resId=resId;resource=IconManager.INSTANCE.resourceName(resId);fixNavigationBar();setTitle(IconUi.t("Заменить иконку"),true);
        LinearLayout root=new LinearLayout(getContext());root.setOrientation(1);root.setPadding(0,AndroidUtilities.dp(16),0,AndroidUtilities.dp(16));
        LinearLayout compare=new LinearLayout(getContext());compare.setGravity(Gravity.CENTER);compare.setPadding(AndroidUtilities.dp(16),0,AndroidUtilities.dp(16),0);
        current=new IconInfoView(getContext(),false);newIcon=new IconInfoView(getContext(),true);
        compare.addView(current,LayoutHelper.createLinear(0,-2,1f));compare.addView(new ArrowView(getContext()),LayoutHelper.createLinear(24,60,0,24,0,24,0));compare.addView(newIcon,LayoutHelper.createLinear(0,-2,1f));root.addView(compare,LayoutHelper.createLinear(-1,-2,0,0,0,24));
        Drawable original=IconManager.INSTANCE.original(resId);current.update(original,resource,0,0);if(original!=null)newIcon.setTargetDimensions(original.getIntrinsicWidth(),original.getIntrinsicHeight());
        IconPack pack=IconManager.INSTANCE.pack(packId);if(pack!=null)IconManager.INSTANCE.request(pack,resId,AndroidUtilities.dp(60),d->{if(!isDismissed())newIcon.update(d,pack.icons.containsKey(resource)?pack.icons.get(resource):IconUi.t("Выберите изображение"),0,0);});
        newIcon.getIconView().setContentDescription(IconUi.t("Выбрать изображение иконки"));newIcon.getIconView().setOnClickListener(this::choose);
        LinearLayout buttons=new LinearLayout(getContext());buttons.setOrientation(1);buttons.setPadding(AndroidUtilities.dp(16),0,AndroidUtilities.dp(16),0);
        save=new ButtonWithCounterView(getContext(),true,resourcesProvider);save.setRound();save.setText(IconUi.t("Сохранить"),false);save.setEnabled(false);buttons.addView(save,LayoutHelper.createLinear(-1,48));save.setOnClickListener(v->{if(selected==null)return;File f=selected;selected=null;dismiss();IconManager.INSTANCE.replace(packId,resource,f,extension,null);});
        reset=new ButtonWithCounterView(getContext(),false,resourcesProvider);reset.setRound().setNeutral();reset.setText(IconUi.t(pack!=null&&pack.icons.containsKey(resource)?"Сбросить":"Отмена"),false);buttons.addView(reset,LayoutHelper.createLinear(-1,48,0,8,0,0));reset.setOnClickListener(v->{IconPack p=IconManager.INSTANCE.pack(packId);dismiss();if(p!=null&&p.icons.containsKey(resource))IconManager.INSTANCE.reset(packId,resource,null);});root.addView(buttons);setCustomView(root);
    }
    private void choose(View anchor){
        ClipboardManager clipboard=(ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);boolean has=clipboard!=null&&clipboard.hasPrimaryClip();
        ItemOptions.makeOptions(containerView,anchor).addIf(has,R.drawable.msg_copy,IconUi.t("Вставить из буфера"),()->{
            ClipData data=clipboard.getPrimaryClip();if(data==null||data.getItemCount()==0)return;ClipData.Item item=data.getItemAt(0);
            if(item.getUri()!=null)IconPackIntents.copyImage(item.getUri(),this::selected);
            else if(item.getText()!=null){String text=item.getText().toString().trim();if(text.contains("<svg")||text.contains("<SVG"))IconPackIntents.svgText(text,this::selected);else if(text.startsWith("/"))IconPackIntents.copyImage(Uri.fromFile(new File(text)),this::selected);else IconUi.error(parent,IconUi.t("В буфере нет изображения или SVG."));}
        }).add(R.drawable.msg_photos,IconUi.t("Выбрать из галереи"),()->pick(false))
          .add(R.drawable.msg2_folder,IconUi.t("Выбрать из файлов"),()->pick(true)).setDrawScrim(false).setDimAlpha(0).show();
    }
    private void pick(boolean files){waitingForResult=true;IconPackIntents.pickImage(parent.getParentActivity(),files,image->{waitingForResult=false;if(image!=null)selected(image);});}
    private void selected(IconPackIntents.Image image){
        if(image==null)return;
        if(isDismissed()){image.file.delete();return;}if(selected!=null)selected.delete();selected=image.file;extension=image.extension;selectedName=image.name;
        Drawable drawable;if(extension.equals(".svg"))drawable=new BitmapDrawable(getContext().getResources(),SvgHelper.getBitmap(image.file,AndroidUtilities.dp(60),AndroidUtilities.dp(60),false));else drawable=Drawable.createFromPath(image.file.getPath());
        newIcon.update(drawable,selectedName,image.width,image.height);save.setEnabled(drawable!=null);
    }
    @Override public void dismiss(){if(!waitingForResult)super.dismiss();}
    @Override public void dismissInternal(){super.dismissInternal();if(selected!=null){selected.delete();selected=null;}}
    public static class IconInfoView extends LinearLayout {
        private final BorderedImageView iconView;
        private final TextView infoName;
        private final TextView infoResolution;
        private float targetAspectRatio;

        public IconInfoView(Context context, boolean z) {
            super(context);
            this.targetAspectRatio = -1.0f;
            setOrientation(1);
            setGravity(1);
            BorderedImageView borderedImageView = new BorderedImageView(context);
            this.iconView = borderedImageView;
            borderedImageView.setDashed(z);
            borderedImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            borderedImageView.setPadding(AndroidUtilities.dp(6.0f), AndroidUtilities.dp(6.0f), AndroidUtilities.dp(6.0f), AndroidUtilities.dp(6.0f));
            borderedImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(borderedImageView, LayoutHelper.createLinear(60, 60));
            TextView textView = new TextView(context);
            this.infoName = textView;
            textView.setTextSize(1, 13.0f);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setGravity(17);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            addView(textView, LayoutHelper.createLinear(-2, -2, 0.0f, 12.0f, 0.0f, 0.0f));
            TextView textView2 = new TextView(context);
            this.infoResolution = textView2;
            textView2.setTextSize(1, 13.0f);
            textView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            textView2.setGravity(17);
            addView(textView2, LayoutHelper.createLinear(-2, -2, 0.0f, 2.0f, 0.0f, 0.0f));
        }

        public void setTargetDimensions(int i, int i2) {
            if (i > 0 && i2 > 0) {
                this.targetAspectRatio = (float)i / i2;
            } else {
                this.targetAspectRatio = -1.0f;
            }
        }

        public void update(Drawable drawable, String str, int i, int i2) {
            if (drawable != null) {
                if (i <= 0) {
                    i = drawable.getIntrinsicWidth();
                }
                if (i2 <= 0) {
                    i2 = drawable.getIntrinsicHeight();
                }
                this.infoResolution.setText(String.format("%s (%s)", String.format(Locale.ROOT, "%d×%d", Integer.valueOf(i), Integer.valueOf(i2)), getAspectRatioString(i, i2)));
                this.infoResolution.setVisibility(0);
                float f = this.targetAspectRatio;
                if (f > 0.0f && i2 > 0 && Math.abs(((float)i / i2) - f) > 0.1f) {
                    this.infoResolution.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                } else {
                    this.infoResolution.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                }
                this.iconView.setImageDrawable(drawable);
            } else {
                this.infoResolution.setVisibility(4);
                this.iconView.setImageDrawable(null);
            }
            TextView textView = this.infoName;
            if (str != null) {
                textView.setText(str);
                this.infoName.setVisibility(0);
            } else {
                textView.setVisibility(4);
            }
        }

        public BorderedImageView getIconView() {
            return this.iconView;
        }

        private String getAspectRatioString(int i, int i2) {
            if (i2 == 0) {
                return "?";
            }
            int iGcd = gcd(i, i2);
            return (i / iGcd) + ":" + (i2 / iGcd);
        }

        private int gcd(int i, int i2) {
            return i2 == 0 ? i : gcd(i2, i % i2);
        }
    }

    public static class ArrowView extends View {
        private final Paint paint;
        private final Path path;

        public ArrowView(Context context) {
            super(context);
            Paint paint = new Paint(1);
            this.paint = paint;
            this.path = new Path();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2.0f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
        }

        @Override // android.view.View
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth() / 2.0f;
            float height = getHeight() / 2.0f;
            float fDp = AndroidUtilities.dp(18.0f);
            this.path.reset();
            float f = fDp / 2.0f;
            this.path.moveTo(width - f, height);
            float f2 = width + f;
            this.path.lineTo(f2, height);
            this.path.moveTo(f2 - AndroidUtilities.dp(7.0f), height - AndroidUtilities.dp(7.0f));
            this.path.lineTo(f2, height);
            this.path.lineTo(f2 - AndroidUtilities.dp(7.0f), height + AndroidUtilities.dp(7.0f));
            canvas.drawPath(this.path, this.paint);
        }
    }

    public static class BorderedImageView extends ImageView {
        private final Paint bgPaint;
        private final float cornerRadius;
        private final Paint dashedPaint;
        private boolean isDashed;
        private final Path path;
        private final RectF rect;
        private final Paint solidPaint;
        private final float strokeWidth;

        public BorderedImageView(Context context) {
            this(context, null);
        }

        public BorderedImageView(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);
            this.isDashed = false;
            this.path = new Path();
            this.rect = new RectF();
            this.cornerRadius = AndroidUtilities.dp(12.0f);
            float fDpf2 = AndroidUtilities.dpf2(1.25f);
            this.strokeWidth = fDpf2;
            Paint paint = new Paint(1);
            this.bgPaint = paint;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            Paint paint2 = new Paint(1);
            this.solidPaint = paint2;
            Paint.Style style = Paint.Style.STROKE;
            paint2.setStyle(style);
            int i = Theme.key_windowBackgroundWhiteGrayText;
            paint2.setColor(AndroidUtilities.multiplyAlphaComponent(Theme.getColor(i), 0.3f));
            paint2.setStrokeWidth(fDpf2);
            Paint paint3 = new Paint(1);
            this.dashedPaint = paint3;
            paint3.setStyle(style);
            paint3.setColor(AndroidUtilities.multiplyAlphaComponent(Theme.getColor(i), 0.3f));
            paint3.setStrokeWidth(fDpf2);
            paint3.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dp(8.0f), AndroidUtilities.dp(8.0f)}, 0.0f));
        }

        public void setDashed(boolean z) {
            this.isDashed = z;
            invalidate();
        }

        @Override // android.widget.ImageView, android.view.View
        public void onDraw(Canvas canvas) {
            float f = this.strokeWidth / 2.0f;
            this.rect.set(f, f, getWidth() - f, getHeight() - f);
            RectF rectF = this.rect;
            float f2 = this.cornerRadius;
            canvas.drawRoundRect(rectF, f2, f2, this.bgPaint);
            super.onDraw(canvas);
            this.path.reset();
            Path path = this.path;
            RectF rectF2 = this.rect;
            float f3 = this.cornerRadius;
            path.addRoundRect(rectF2, f3, f3, Path.Direction.CW);
            canvas.drawPath(this.path, this.isDashed ? this.dashedPaint : this.solidPaint);
        }
    }

}

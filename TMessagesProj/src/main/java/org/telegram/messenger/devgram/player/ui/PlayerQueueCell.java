package org.telegram.messenger.devgram.player.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.SpringAnimation;
import org.telegram.messenger.devgram.player.PlayerActions;
import org.telegram.messenger.devgram.player.PlayerColors;
import org.telegram.messenger.devgram.player.PlayerCovers;
import org.telegram.messenger.devgram.utils.ui.Material3Tokens;
import java.util.ArrayList;
import java.util.Locale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.DotDividerSpan;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.FilteredSearchView;
@SuppressLint({"ViewConstructor"})
public class PlayerQueueCell extends FrameLayout implements DownloadController.FileDownloadProgressListener, ItemOptions.ScrimView {
    private int accentColor;
    private int backgroundColor;
    private int buttonState;
    private final int[] coverRadii;
    private final RectF coverRect;
    private boolean current;
    private final int currentAccount;
    private float currentProgress;
    private final SpringAnimation currentSpring;
    private SpannableStringBuilder dotSpan;
    private boolean drawMini;
    private boolean globalSearch;
    private int hasMiniProgress;
    private final MediaActionDrawable iconDrawable;
    private final ImageReceiver imageReceiver;
    private int liftColor;
    private float liftProgress;
    private final SpringAnimation liftSpring;
    private boolean lifted;
    private MessageObject messageObject;
    private boolean miniButtonPressed;
    private int miniButtonState;
    private final MediaActionDrawable miniDrawable;
    private final int observerTag;
    private int onAccentColor;
    private int onTonalColor;
    private final ExpressiveStateDrawable optionsBackground;
    private final ImageView optionsButton;
    private final Paint paint;
    private final RectF rect;
    private int secondaryColor;
    private int sideInset;
    private AnimatedEmojiSpan.EmojiGroupedSpans subtitleEmojis;
    private StaticLayout subtitleLayout;
    private final TextPaint subtitlePaint;
    private int textColor;
    private AnimatedEmojiSpan.EmojiGroupedSpans titleEmojis;
    private StaticLayout titleLayout;
    private final TextPaint titlePaint;
    private int tonalColor;

    @Override
    public void onProgressUpload(String str, long j, long j2, boolean z) {
    }

    public PlayerQueueCell(Context context, int i) {
        super(context);
        TextPaint textPaint = new TextPaint(1);
        this.titlePaint = textPaint;
        TextPaint textPaint2 = new TextPaint(1);
        this.subtitlePaint = textPaint2;
        this.paint = new Paint(1);
        this.coverRect = new RectF();
        this.rect = new RectF();
        this.coverRadii = new int[4];
        this.miniButtonState = -1;
        this.sideInset = AndroidUtilities.dp(20.0f);
        setWillNotDraw(false);
        setFocusable(true);
        this.currentAccount = i;
        this.observerTag = DownloadController.getInstance(i).generateObserverTag();
        this.imageReceiver = new ImageReceiver(this);
        MediaActionDrawable mediaActionDrawable = new MediaActionDrawable();
        this.iconDrawable = mediaActionDrawable;
        mediaActionDrawable.setDelegate(() -> invalidate());
        MediaActionDrawable mediaActionDrawable2 = new MediaActionDrawable();
        this.miniDrawable = mediaActionDrawable2;
        mediaActionDrawable2.setMini(true);
        mediaActionDrawable2.setIcon(4, false);
        mediaActionDrawable2.setDelegate(() -> invalidate());
        textPaint.setTextSize(AndroidUtilities.dp(15.0f));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint2.setTextSize(AndroidUtilities.dp(13.0f));
        textPaint2.setTypeface(AndroidUtilities.regular());
        ImageView imageView = new ImageView(context);
        this.optionsButton = imageView;
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        ExpressiveStateDrawable expressiveStateDrawable = new ExpressiveStateDrawable(12.0f);
        this.optionsBackground = expressiveStateDrawable;
        imageView.setBackground(expressiveStateDrawable);
        addView(imageView);
        this.currentSpring = Material3Tokens.createSpring(0.6f, 800.0f, 0.002f, obj -> lambda_new_0((Float) obj));
        this.liftSpring = Material3Tokens.createSpring(1.0f, 1600.0f, 0.002f, obj -> lambda_new_1((Float) obj));
    }
    public /* synthetic */ void lambda_new_0(Float f) {
        this.currentProgress = f.floatValue();
        invalidate();
    }
    public /* synthetic */ void lambda_new_1(Float f) {
        this.liftProgress = Utilities.clamp01(f.floatValue());
        invalidate();
    }

    public void setColors(PlayerColors playerColors) {
        this.textColor = playerColors.text;
        this.secondaryColor = playerColors.textSecondary;
        this.accentColor = playerColors.accent;
        this.onAccentColor = playerColors.onAccent;
        this.tonalColor = playerColors.tonal;
        this.onTonalColor = playerColors.onTonal;
        this.backgroundColor = playerColors.background;
        this.liftColor = playerColors.surfaceContainer;
        this.optionsButton.setColorFilter(new PorterDuffColorFilter(playerColors.textSecondary, PorterDuff.Mode.SRC_IN));
        this.optionsBackground.setColors(playerColors.icon, 0);
        invalidate();
    }

    public void setSideInset(int i) {
        if (this.sideInset != i) {
            this.sideInset = i;
            requestLayout();
        }
    }

    public void setGlobalSearch(boolean z) {
        this.globalSearch = z;
        if (z && this.dotSpan == null) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(".");
            this.dotSpan = spannableStringBuilder;
            spannableStringBuilder.setSpan(new DotDividerSpan(), 0, 1, 0);
        }
    }

    @SuppressLint({"ClickableViewAccessibility"})
    public void setMessageObject(MessageObject messageObject, boolean z, View.OnClickListener onClickListener, View.OnTouchListener onTouchListener) {
        this.messageObject = messageObject;
        this.optionsButton.setImageResource(z ? R.drawable.list_reorder : R.drawable.ic_ab_other);
        this.optionsButton.setContentDescription(z ? null : LocaleController.getString(R.string.AccDescrMoreOptions));
        this.optionsButton.setVisibility((z || onClickListener != null) ? 0 : 8);
        this.optionsButton.setOnClickListener(onClickListener);
        this.optionsButton.setOnTouchListener(onTouchListener);
        PlayerCovers.setCover(this.imageReceiver, messageObject, 44, false);
        requestLayout();
        updateButtonState(false, false);
        invalidate();
    }

    public MessageObject getMessageObject() {
        return this.messageObject;
    }

    public void setCurrent(boolean z, boolean z2) {
        if (this.current == z) {
            if (z2) {
                return;
            }
            if (this.currentProgress == (z ? 1.0f : 0.0f)) {
                return;
            }
        }
        this.current = z;
        if (z2 && isAttachedToWindow()) {
            Material3Tokens.animate(this.currentSpring, this.currentProgress, z ? 1.0f : 0.0f);
            return;
        }
        this.currentSpring.cancel();
        this.currentProgress = z ? 1.0f : 0.0f;
        invalidate();
    }

    public void setLifted(boolean z) {
        if (this.lifted != z) {
            this.lifted = z;
            Material3Tokens.animate(this.liftSpring, this.liftProgress, z ? 1.0f : 0.0f);
        }
    }

    private int getTextLeft() {
        return this.sideInset + AndroidUtilities.dp(60.0f);
    }

    @Override
    public void onMeasure(int i, int i2) {
        int size = View.MeasureSpec.getSize(i);
        int iMakeMeasureSpec = View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40.0f), TLObject.FLAG_30);
        this.optionsButton.measure(iMakeMeasureSpec, iMakeMeasureSpec);
        buildLayouts(Math.max(AndroidUtilities.dp(24.0f), ((size - this.sideInset) - (this.optionsButton.getVisibility() != 8 ? AndroidUtilities.dp(48.0f) : 0)) - getTextLeft()));
        setMeasuredDimension(size, AndroidUtilities.dp(56.0f));
    }

    @Override
    public void onLayout(boolean z, int i, int i2, int i3, int i4) {
        int measuredWidth = this.optionsButton.getMeasuredWidth();
        int measuredWidth2 = (getMeasuredWidth() - this.sideInset) - measuredWidth;
        int measuredHeight = (getMeasuredHeight() - this.optionsButton.getMeasuredHeight()) / 2;
        ImageView imageView = this.optionsButton;
        imageView.layout(measuredWidth2, measuredHeight, measuredWidth + measuredWidth2, imageView.getMeasuredHeight() + measuredHeight);
    }

    private void buildLayouts(int i) {
        this.titleLayout = null;
        this.subtitleLayout = null;
        MessageObject messageObject = this.messageObject;
        if (messageObject == null) {
            return;
        }
        try {
            StaticLayout staticLayoutMakeLayout = makeLayout(highlight(TextUtils.ellipsize(messageObject.getMusicTitle().replace('\n', ' '), this.titlePaint, i, TextUtils.TruncateAt.END)), this.titlePaint, AndroidUtilities.dp(4.0f) + i);
            this.titleLayout = staticLayoutMakeLayout;
            this.titleEmojis = AnimatedEmojiSpan.update(0, this, this.titleEmojis, staticLayoutMakeLayout);
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            CharSequence charSequenceReplace = this.messageObject.getMusicAuthor().replace('\n', ' ');
            if (this.globalSearch && this.dotSpan != null) {
                charSequenceReplace = new SpannableStringBuilder(charSequenceReplace).append(' ').append((CharSequence) this.dotSpan).append(' ').append(FilteredSearchView.createFromInfoString(this.messageObject, 2));
            }
            StaticLayout staticLayoutMakeLayout2 = makeLayout(highlight(TextUtils.ellipsize(charSequenceReplace, this.subtitlePaint, i, TextUtils.TruncateAt.END)), this.subtitlePaint, i + AndroidUtilities.dp(4.0f));
            this.subtitleLayout = staticLayoutMakeLayout2;
            this.subtitleEmojis = AnimatedEmojiSpan.update(0, this, this.subtitleEmojis, staticLayoutMakeLayout2);
        } catch (Exception e2) {
            FileLog.e(e2);
        }
    }

    private static StaticLayout makeLayout(CharSequence charSequence, TextPaint textPaint, int i) {
        return StaticLayout.Builder.obtain(charSequence, 0, charSequence.length(), textPaint, i).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0.0f, 1.0f).setIncludePad(false).build();
    }

    private CharSequence highlight(CharSequence charSequence) {
        ArrayList<String> arrayList = this.messageObject.highlightedWords;
        if (arrayList != null && !arrayList.isEmpty() && !TextUtils.isEmpty(charSequence)) {
            String lowerCase = charSequence.toString().toLowerCase(Locale.getDefault());
            SpannableStringBuilder spannableStringBuilderValueOf = null;
            for (int i = 0; i < arrayList.size(); i++) {
                String str = arrayList.get(i);
                if (!TextUtils.isEmpty(str)) {
                    for (int iIndexOf = lowerCase.indexOf(str); iIndexOf >= 0; iIndexOf = lowerCase.indexOf(str, iIndexOf + 1)) {
                        if (spannableStringBuilderValueOf == null) {
                            spannableStringBuilderValueOf = SpannableStringBuilder.valueOf(charSequence);
                        }
                        spannableStringBuilderValueOf.setSpan(new AccentSpan(), iIndexOf, Math.min(str.length() + iIndexOf, charSequence.length()), 33);
                    }
                }
            }
            if (spannableStringBuilderValueOf != null) {
                return spannableStringBuilderValueOf;
            }
        }
        return charSequence;
    }

    public class AccentSpan extends CharacterStyle {
        private AccentSpan() {
        }

        @Override
        public void updateDrawState(TextPaint textPaint) {
            textPaint.setColor(PlayerQueueCell.this.accentColor);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        float f = this.liftProgress;
        if (f > 0.0f) {
            drawLift(canvas, f);
        }
        drawContent(canvas, this.liftProgress);
    }

    @Override
    public void drawScrim(Canvas canvas, float f) {
        float fMax = Math.max(f, this.liftProgress);
        drawLift(canvas, fMax);
        drawContent(canvas, fMax);
        canvas.save();
        canvas.translate(this.optionsButton.getLeft(), this.optionsButton.getTop());
        this.optionsButton.draw(canvas);
        canvas.restore();
    }

    @Override
    public void getBounds(RectF rectF) {
        rectF.set(this.sideInset - AndroidUtilities.dp(8.0f), 0.0f, (getWidth() - this.sideInset) + AndroidUtilities.dp(8.0f), getHeight());
    }

    private void drawLift(Canvas canvas, float f) {
        if (f <= 0.0f) {
            return;
        }
        this.rect.set(this.sideInset - AndroidUtilities.dp(8.0f), AndroidUtilities.dp(2.0f), (getWidth() - this.sideInset) + AndroidUtilities.dp(8.0f), getHeight() - AndroidUtilities.dp(2.0f));
        this.paint.setColor(this.liftColor);
        this.paint.setAlpha((int) (Color.alpha(this.liftColor) * Utilities.clamp01(f)));
        canvas.drawRoundRect(this.rect, AndroidUtilities.dpf2(16.0f), AndroidUtilities.dpf2(16.0f), this.paint);
    }

    private void drawContent(Canvas canvas, float f) {
        float fDp = AndroidUtilities.dp(44.0f);
        float f2 = this.sideInset;
        float height = (getHeight() - fDp) / 2.0f;
        this.coverRect.set(f2, height, f2 + fDp, height + fDp);
        float f3 = fDp / 2.0f;
        float fMax = Math.max(0.0f, Math.min(f3, AndroidUtilities.lerp(f3, AndroidUtilities.dpf2(12.0f), this.currentProgress)));
        boolean zHasBitmapImage = this.imageReceiver.hasBitmapImage();
        float currentAlpha = zHasBitmapImage ? this.imageReceiver.getCurrentAlpha() : 0.0f;
        if (currentAlpha < 1.0f) {
            this.paint.setColor(this.tonalColor);
            canvas.drawRoundRect(this.coverRect, fMax, fMax, this.paint);
        }
        if (zHasBitmapImage) {
            ImageReceiver imageReceiver = this.imageReceiver;
            RectF rectF = this.coverRect;
            imageReceiver.setImageCoords(rectF.left, rectF.top, fDp, fDp);
            int[] iArr = this.coverRadii;
            int i = (int) fMax;
            if (iArr[0] != i) {
                iArr[3] = i;
                iArr[2] = i;
                iArr[1] = i;
                iArr[0] = i;
                this.imageReceiver.setRoundRadius(iArr);
            }
            this.imageReceiver.draw(canvas);
            this.paint.setColor(-16777216);
            this.paint.setAlpha((int) (77.0f * currentAlpha));
            canvas.drawRoundRect(this.coverRect, fMax, fMax, this.paint);
        }
        this.iconDrawable.setColor(ColorUtils.blendARGB(this.onTonalColor, -1, currentAlpha));
        this.iconDrawable.setBackColor(this.tonalColor);
        this.iconDrawable.setHasOverlayImage(zHasBitmapImage);
        MediaActionDrawable mediaActionDrawable = this.iconDrawable;
        RectF rectF2 = this.coverRect;
        mediaActionDrawable.setBounds((int) rectF2.left, (int) rectF2.top, (int) rectF2.right, (int) rectF2.bottom);
        this.iconDrawable.draw(canvas);
        if (this.drawMini) {
            float transitionProgress = this.miniDrawable.getCurrentIcon() == 4 ? 1.0f - this.miniDrawable.getTransitionProgress() : 1.0f;
            if (transitionProgress <= 0.0f) {
                this.drawMini = false;
            } else {
                float fCenterX = this.coverRect.centerX() + AndroidUtilities.dp(16.0f);
                float fCenterY = this.coverRect.centerY() + AndroidUtilities.dp(16.0f);
                Paint paint = this.paint;
                int i2 = this.backgroundColor;
                paint.setColor(ColorUtils.blendARGB(i2, ColorUtils.compositeColors(this.liftColor, i2), Utilities.clamp01(f)));
                canvas.drawCircle(fCenterX, fCenterY, AndroidUtilities.dp(12.0f) * transitionProgress, this.paint);
                this.paint.setColor(this.accentColor);
                canvas.drawCircle(fCenterX, fCenterY, AndroidUtilities.dp(10.0f) * transitionProgress, this.paint);
                int iDp = (int) (AndroidUtilities.dp(10.0f) * transitionProgress);
                this.miniDrawable.setColor(this.onAccentColor);
                int i3 = (int) fCenterX;
                int i4 = (int) fCenterY;
                this.miniDrawable.setBounds(i3 - iDp, i4 - iDp, i3 + iDp, i4 + iDp);
                this.miniDrawable.draw(canvas);
            }
        }
        float textLeft = getTextLeft();
        if (this.titleLayout != null) {
            this.titlePaint.setColor(this.textColor);
            canvas.save();
            canvas.translate(textLeft, AndroidUtilities.dp(9.0f));
            this.titleLayout.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, this.titleLayout, this.titleEmojis, 0.0f, null, 0.0f, 0.0f, 0.0f, 1.0f);
            canvas.restore();
        }
        if (this.subtitleLayout != null) {
            this.subtitlePaint.setColor(this.secondaryColor);
            canvas.save();
            canvas.translate(textLeft, AndroidUtilities.dp(29.0f));
            this.subtitleLayout.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, this.subtitleLayout, this.subtitleEmojis, 0.0f, null, 0.0f, 0.0f, 0.0f, 1.0f);
            canvas.restore();
        }
    }

    private void setIcon(int i, boolean z, boolean z2) {
        if (z && i == this.iconDrawable.getCurrentIcon()) {
            return;
        }
        this.iconDrawable.setIcon(i, z2);
        invalidate();
    }

    private void setMiniIcon(int i, boolean z, boolean z2) {
        if (z && i == this.miniDrawable.getCurrentIcon()) {
            return;
        }
        this.miniDrawable.setIcon(i, z2);
        this.drawMini = i != 4 || this.miniDrawable.getTransitionProgress() < 1.0f;
        invalidate();
    }

    private void setProgress(float f, boolean z) {
        if (this.drawMini) {
            this.miniDrawable.setProgress(f, z);
        } else {
            this.iconDrawable.setProgress(f, z);
        }
        invalidate();
    }

    private int getIconForCurrentState() {
        int i = this.buttonState;
        if (i == 1) {
            return 1;
        }
        if (i == 2) {
            return 2;
        }
        return i == 4 ? 3 : 0;
    }

    private int getMiniIconForCurrentState() {
        int i = this.miniButtonState;
        if (i < 0) {
            return 4;
        }
        return i == 0 ? 2 : 3;
    }

    public void updateButtonState(boolean z, boolean z2) {
        MessageObject messageObject = this.messageObject;
        if (messageObject == null) {
            return;
        }
        String fileName = messageObject.getFileName();
        if (TextUtils.isEmpty(fileName)) {
            return;
        }
        boolean zExists = PlayerActions.getLocalFile(this.messageObject).exists();
        if (SharedConfig.streamMedia && this.messageObject.getDialogId() != 0 && !DialogObject.isEncryptedDialog(this.messageObject.getDialogId())) {
            this.hasMiniProgress = zExists ? 1 : 2;
            zExists = true;
        } else {
            this.hasMiniProgress = 0;
            this.miniButtonState = -1;
        }
        MediaController mediaController = MediaController.getInstance();
        int i = (!mediaController.isPlayingMessage(this.messageObject) || mediaController.isMessagePaused()) ? 0 : 1;
        if (this.hasMiniProgress != 0) {
            this.buttonState = i;
            setIcon(getIconForCurrentState(), z, z2);
            int i2 = this.hasMiniProgress;
            int i3 = this.currentAccount;
            if (i2 == 1) {
                DownloadController.getInstance(i3).removeLoadingFileObserver(this);
                this.miniButtonState = -1;
                setMiniIcon(getMiniIconForCurrentState(), z, z2);
                return;
            }
            DownloadController.getInstance(i3).addLoadingFileObserver(fileName, this.messageObject, this);
            if (!FileLoader.getInstance(this.currentAccount).isLoadingFile(fileName)) {
                this.miniButtonState = 0;
                setMiniIcon(getMiniIconForCurrentState(), z, z2);
                return;
            } else {
                this.miniButtonState = 1;
                setMiniIcon(getMiniIconForCurrentState(), z, z2);
                Float fileProgress = ImageLoader.getInstance().getFileProgress(fileName);
                setProgress(fileProgress != null ? fileProgress.floatValue() : 0.0f, z2);
                return;
            }
        }
        int i4 = this.currentAccount;
        if (zExists) {
            DownloadController.getInstance(i4).removeLoadingFileObserver(this);
            this.buttonState = i;
            setProgress(1.0f, z2);
            setIcon(getIconForCurrentState(), z, z2);
            return;
        }
        DownloadController.getInstance(i4).addLoadingFileObserver(fileName, this);
        if (!FileLoader.getInstance(this.currentAccount).isLoadingFile(fileName)) {
            this.buttonState = 2;
        } else {
            this.buttonState = 4;
            Float fileProgress2 = ImageLoader.getInstance().getFileProgress(fileName);
            setProgress(fileProgress2 != null ? fileProgress2.floatValue() : 0.0f, z2);
        }
        setIcon(getIconForCurrentState(), z, z2);
    }

    public void didPressedButton() {
        if (this.messageObject == null) {
            return;
        }
        int i = this.buttonState;
        if (i == 0) {
            if (this.miniButtonState == 0) {
                FileLoader.getInstance(this.currentAccount).loadFile(this.messageObject.getDocument(), this.messageObject, 1, 0);
            }
            if (MediaController.getInstance().findMessageInPlaylistAndPlay(this.messageObject)) {
                if (this.hasMiniProgress == 2 && this.miniButtonState != 1) {
                    this.miniButtonState = 1;
                    setProgress(0.0f, false);
                    setMiniIcon(getMiniIconForCurrentState(), false, true);
                }
                this.buttonState = 1;
                setIcon(getIconForCurrentState(), false, true);
                return;
            }
            return;
        }
        if (i == 1) {
            if (MediaController.getInstance().pauseMessage(this.messageObject)) {
                this.buttonState = 0;
                setIcon(getIconForCurrentState(), false, true);
                return;
            }
            return;
        }
        if (i == 2) {
            setProgress(0.0f, false);
            FileLoader.getInstance(this.currentAccount).loadFile(this.messageObject.getDocument(), this.messageObject, 1, 0);
            this.buttonState = 4;
            setIcon(getIconForCurrentState(), false, true);
            return;
        }
        if (i == 4) {
            FileLoader.getInstance(this.currentAccount).cancelLoadFile(this.messageObject.getDocument());
            this.buttonState = 2;
            setIcon(getIconForCurrentState(), false, true);
        }
    }

    private void didPressedMiniButton() {
        int i = this.miniButtonState;
        if (i == 0) {
            this.miniButtonState = 1;
            setProgress(0.0f, false);
            FileLoader.getInstance(this.currentAccount).loadFile(this.messageObject.getDocument(), this.messageObject, 3, 0);
            setMiniIcon(getMiniIconForCurrentState(), false, true);
            return;
        }
        if (i == 1) {
            if (MediaController.getInstance().isPlayingMessage(this.messageObject)) {
                MediaController.getInstance().pauseAndStopLoading(this.messageObject);
            }
            this.miniButtonState = 0;
            FileLoader.getInstance(this.currentAccount).cancelLoadFile(this.messageObject.getDocument());
            setMiniIcon(getMiniIconForCurrentState(), false, true);
        }
    }

    private boolean isInMiniButton(float f, float f2) {
        if (this.miniButtonState < 0) {
            return false;
        }
        return Math.abs(f - ((((float) this.sideInset) + (((float) AndroidUtilities.dp(44.0f)) / 2.0f)) + ((float) AndroidUtilities.dp(16.0f)))) <= ((float) AndroidUtilities.dp(18.0f)) && Math.abs(f2 - ((((float) getHeight()) / 2.0f) + ((float) AndroidUtilities.dp(16.0f)))) <= ((float) AndroidUtilities.dp(18.0f));
    }

    @Override
    @SuppressLint({"ClickableViewAccessibility"})
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (this.messageObject == null) {
            return super.onTouchEvent(motionEvent);
        }
        int action = motionEvent.getAction();
        boolean zIsInMiniButton = isInMiniButton(motionEvent.getX(), motionEvent.getY());
        if (action == 0) {
            if (zIsInMiniButton) {
                this.miniButtonPressed = true;
                return true;
            }
        } else if (this.miniButtonPressed) {
            if (action == 1) {
                this.miniButtonPressed = false;
                playSoundEffect(0);
                didPressedMiniButton();
            } else if (action == 3 || (action == 2 && !zIsInMiniButton)) {
                this.miniButtonPressed = false;
            }
            return this.miniButtonPressed || action == 1;
        }
        return false;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.imageReceiver.onAttachedToWindow();
        this.titleEmojis = AnimatedEmojiSpan.update(0, this, this.titleEmojis, this.titleLayout);
        this.subtitleEmojis = AnimatedEmojiSpan.update(0, this, this.subtitleEmojis, this.subtitleLayout);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.imageReceiver.onDetachedFromWindow();
        DownloadController.getInstance(this.currentAccount).removeLoadingFileObserver(this);
        AnimatedEmojiSpan.release(this, this.titleEmojis);
        AnimatedEmojiSpan.release(this, this.subtitleEmojis);
        this.currentSpring.cancel();
        this.liftSpring.cancel();
        this.currentProgress = this.current ? 1.0f : 0.0f;
        this.liftProgress = this.lifted ? 1.0f : 0.0f;
    }

    @Override
    public void onFailedDownload(String str, boolean z) {
        updateButtonState(true, z);
    }

    @Override
    public void onSuccessDownload(String str) {
        setProgress(1.0f, true);
        updateButtonState(false, true);
    }

    @Override
    public void onProgressDownload(String str, long j, long j2) {
        setProgress(j2 > 0 ? Math.min(1.0f, j / j2) : 0.0f, true);
        if (this.hasMiniProgress != 0) {
            if (this.miniButtonState != 1) {
                updateButtonState(false, true);
            }
        } else if (this.buttonState != 4) {
            updateButtonState(false, true);
        }
    }

    @Override
    public int getObserverTag() {
        return this.observerTag;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo accessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(accessibilityNodeInfo);
        MessageObject messageObject = this.messageObject;
        if (messageObject == null) {
            return;
        }
        if (messageObject.isMusic()) {
            accessibilityNodeInfo.setText(LocaleController.formatString(R.string.AccDescrMusicInfo, this.messageObject.getMusicAuthor(), this.messageObject.getMusicTitle()));
        } else if (this.titleLayout != null && this.subtitleLayout != null) {
            accessibilityNodeInfo.setText(((Object) this.titleLayout.getText()) + ", " + ((Object) this.subtitleLayout.getText()));
        }
        accessibilityNodeInfo.setSelected(this.current);
    }
}

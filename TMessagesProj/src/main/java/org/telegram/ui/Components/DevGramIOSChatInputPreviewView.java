/*
 * Adapted from Cherrygram's ChatInputPreviewView (GPL-2.0-or-later).
 * The preview embeds Telegram's production compose view instead of drawing a mock.
 */
package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.DevGramIOSConfig;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped;
import org.telegram.ui.Components.chat.ChatInputViewsContainer;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;
import org.telegram.ui.Stories.recorder.StoryEntry;

@SuppressLint("ViewConstructor")
public class DevGramIOSChatInputPreviewView extends FrameLayout {

    private final ChatInputViewsContainer chatInputViewsContainer;
    private final ChatActivityEnterView enterView;
    private Drawable backgroundDrawable;
    private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::checkInsets);
    private final int bubbleHeight = dp(44);

    public DevGramIOSChatInputPreviewView(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        SizeNotifierFrameLayout content = new SizeNotifierFrameLayout(context);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        chatInputViewsContainer = new ChatInputViewsContainer(context);
        chatInputViewsContainer.setClipChildren(false);
        chatInputViewsContainer.setWindowInsetsProvider(windowInsetsStateHolder);
        content.addView(chatInputViewsContainer,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        setupGlass(resourcesProvider);

        FrameLayout bubbleContainer = new FrameLayout(context);
        bubbleContainer.setClipChildren(false);
        bubbleContainer.setClipToPadding(false);
        chatInputViewsContainer.addView(bubbleContainer,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        enterView = new ChatActivityEnterView(fragment.getParentActivity(), content, null, true,
                resourcesProvider, DevGramIOSConfig.iosInputPanel()) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                return false;
            }
        };
        enterView.setClickable(false);
        enterView.setChatInputViewsContainer(chatInputViewsContainer);
        enterView.shouldDrawBackground = false;
        enterView.setAllowStickersAndGifs(true, true, true);
        enterView.setDialogId(fragment.getUserConfig().clientUserId, UserConfig.selectedAccount);
        enterView.updateSendAsButtonPreview();
        enterView.updateAudioVideoButton();
        enterView.setDelegate(new EmptyEnterViewDelegate());
        bubbleContainer.addView(enterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 7, 0, 7, 1));

        if (enterView.messageEditText != null) {
            enterView.messageEditText.setTranslationX(DevGramIOSConfig.iosInputPanel() ? 30 : 0);
            enterView.messageEditText.setText(null);
            enterView.messageEditText.setHintText(getString(R.string.TypeMessage));
            enterView.messageEditText.setFocusable(false);
            enterView.messageEditText.setClickable(false);
            enterView.messageEditText.setCursorVisible(false);
        }
        enterView.setFocusable(false);
        updateInputBubbleOffsets();
    }

    private void setupGlass(Theme.ResourcesProvider resourcesProvider) {
        BlurredBackgroundDrawableViewFactory factory;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ViewPositionWatcher watcher = new ViewPositionWatcher(this);
            BlurredBackgroundSourceWrapped wallpaper = new BlurredBackgroundSourceWrapped();
            BlurredBackgroundSourceRenderNode renderNode = new BlurredBackgroundSourceRenderNode(wallpaper);
            renderNode.setUnderSource(wallpaper);
            factory = new BlurredBackgroundDrawableViewFactory(renderNode);
            factory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
            factory.setSourceRootView(watcher, this);
        } else {
            BlurredBackgroundSourceColor source = new BlurredBackgroundSourceColor();
            source.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            factory = new BlurredBackgroundDrawableViewFactory(source);
        }
        BlurredBackgroundColorProvider colors = BlurredBackgroundProviderImpl.topPanelChatActivity(resourcesProvider);
        chatInputViewsContainer.setInputIslandBubbleDrawable(factory.create(chatInputViewsContainer, colors));
        chatInputViewsContainer.setUnderKeyboardBackgroundDrawable(factory.create(chatInputViewsContainer, colors));
        if (DevGramIOSConfig.iosInputPanel()) {
            chatInputViewsContainer.setLeftBubbleDrawable(factory.create(chatInputViewsContainer, colors));
            chatInputViewsContainer.setRightBubbleDrawable(factory.create(chatInputViewsContainer, colors));
        }
    }

    private void updateInputBubbleOffsets() {
        chatInputViewsContainer.checkInsets();
        chatInputViewsContainer.setInputBubbleHeight(bubbleHeight);
        enterView.updateIOSInputBubbleBoundsForPreview();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateInputBubbleOffsets();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateInputBubbleOffsets();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Drawable drawable = Theme.getCachedWallpaperNonBlocking();
        if (Theme.wallpaperLoadTask != null) invalidate();
        if (drawable != backgroundDrawable) {
            if (backgroundGradientDisposable != null) backgroundGradientDisposable.dispose();
            backgroundGradientDisposable = null;
            backgroundDrawable = drawable;
        }
        if (drawable == null) {
            canvas.drawColor(Theme.getColor(Theme.key_chat_wallpaper));
        } else {
            drawable.setAlpha(255);
            if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable || drawable instanceof MotionBackgroundDrawable) {
                drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                if (drawable instanceof BackgroundGradientDrawable) {
                    backgroundGradientDisposable = ((BackgroundGradientDrawable) drawable).drawExactBoundsSize(canvas, this);
                } else {
                    drawable.draw(canvas);
                }
            } else if (drawable instanceof BitmapDrawable) {
                ((BitmapDrawable) drawable).setFilterBitmap(true);
                float scale = Math.max((float) getMeasuredWidth() / drawable.getIntrinsicWidth(),
                        (float) getMeasuredHeight() / drawable.getIntrinsicHeight());
                int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                int x = (getMeasuredWidth() - width) / 2;
                int y = (getMeasuredHeight() - height) / 2;
                drawable.setBounds(x, y, x + width, y + height);
                drawable.draw(canvas);
            } else {
                StoryEntry.drawBackgroundDrawable(canvas, drawable, getWidth(), getHeight());
            }
        }
        super.onDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (backgroundGradientDisposable != null) {
            backgroundGradientDisposable.dispose();
            backgroundGradientDisposable = null;
        }
        super.onDetachedFromWindow();
    }

    private void checkInsets() {
        chatInputViewsContainer.checkInsets();
    }

    private static class EmptyEnterViewDelegate implements ChatActivityEnterView.ChatActivityEnterViewDelegate {
        @Override public void onMessageSend(CharSequence message, boolean notify, int scheduleDate, int repeat, long payStars) {}
        @Override public void needSendTyping() {}
        @Override public void onTextChanged(CharSequence text, boolean bigChange, boolean fromDraft) {}
        @Override public void onTextSelectionChanged(int start, int end) {}
        @Override public void onTextSpansChanged(CharSequence text) {}
        @Override public void onAttachButtonHidden() {}
        @Override public void onAttachButtonShow() {}
        @Override public void onWindowSizeChanged(int size) {}
        @Override public void onStickersTab(boolean opened) {}
        @Override public void onMessageEditEnd(boolean loading) {}
        @Override public void didPressAttachButton() {}
        @Override public void needStartRecordVideo(int state, boolean notify, int scheduleDate, int repeat, int ttl, long effectId, long stars) {}
        @Override public void toggleVideoRecordingPause() {}
        @Override public boolean isVideoRecordingPaused() { return false; }
        @Override public void needChangeVideoPreviewState(int state, float seekProgress) {}
        @Override public void onSwitchRecordMode(boolean video) {}
        @Override public void onPreAudioVideoRecord() {}
        @Override public void needStartRecordAudio(int state) {}
        @Override public void needShowMediaBanHint() {}
        @Override public void onStickersExpandedChange() {}
        @Override public void onUpdateSlowModeButton(View button, boolean show, CharSequence time) {}
        @Override public void onSendLongClick() {}
        @Override public void onAudioVideoInterfaceUpdated() {}
    }
}

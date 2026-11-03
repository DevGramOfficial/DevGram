/*
 * Adapted from Cherrygram's ChatHeaderPreviewView (GPL-2.0-or-later).
 * Uses the same production ActionBar and ChatAvatarContainer as a real chat.
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
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DevGramIOSConfig;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Stories.recorder.StoryEntry;

@SuppressLint("ViewConstructor")
public class DevGramIOSChatHeaderPreviewView extends FrameLayout {

    private final ActionBar actionBar;
    private final ChatAvatarContainer avatarContainer;
    private Drawable backgroundDrawable;
    private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
    private boolean titleCentered;

    public DevGramIOSChatHeaderPreviewView(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        titleCentered = DevGramIOSConfig.centerChatTitle();

        actionBar = new ActionBar(context);
        actionBar.setOccupyStatusBar(false);
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider));
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector, resourcesProvider), false);
        actionBar.setCastShadows(false);

        BackDrawable backDrawable = new BackDrawable(false);
        backDrawable.setShowStick(!titleCentered);
        actionBar.setBackButtonDrawable(backDrawable);
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.setContentDescription(getString(R.string.AccDescrMoreOptions));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ViewPositionWatcher watcher = new ViewPositionWatcher(this);
            BlurredBackgroundSourceWrapped wallpaper = new BlurredBackgroundSourceWrapped();
            BlurredBackgroundSourceRenderNode renderNode = new BlurredBackgroundSourceRenderNode(wallpaper);
            renderNode.setUnderSource(wallpaper);
            BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(renderNode);
            factory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
            factory.setSourceRootView(watcher, this);
            actionBar.setupGlass(factory, BlurredBackgroundProviderImpl.topPanelChatActivity(resourcesProvider));
        } else {
            BlurredBackgroundSourceColor source = new BlurredBackgroundSourceColor();
            source.setColor(fragment.getThemedColor(Theme.key_windowBackgroundWhite));
            actionBar.setupGlass(new BlurredBackgroundDrawableViewFactory(source),
                    BlurredBackgroundProviderImpl.topPanelChatActivity(resourcesProvider));
        }
        addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, Gravity.CENTER));

        avatarContainer = new ChatAvatarContainer(context, fragment, true, resourcesProvider) {
            @Override
            public boolean isCentered() {
                return titleCentered;
            }

            @Override
            protected boolean useAnimatedSubtitle() {
                return true;
            }

        };
        avatarContainer.setActionBar(actionBar);
        avatarContainer.setOccupyStatusBar(false);
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        if (user != null) {
            // Cherrygram deliberately previews the current account as a regular chat user.
            // Otherwise Telegram treats `self` as Saved Messages and the avatar/title pill
            // in the preview does not match the real iOS header.
            user.self = false;
            avatarContainer.setUserAvatar(user);
        }
        avatarContainer.setGlassMode();
        avatarContainer.allowShorterStatus = true;
        avatarContainer.premiumIconHiddable = false;
        avatarContainer.allowDrawStories = false;
        avatarContainer.setClipChildren(false);
        avatarContainer.setTitle(chatTitle(user));
        avatarContainer.setSubtitle(getString(R.string.Online));
        applyTitleLayout();

        actionBar.addView(avatarContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.MATCH_PARENT, Gravity.START | Gravity.TOP, 54, 0, titleCentered ? 0 : 54, 0));
        actionBar.setChatAvatarContainer2(avatarContainer);
        refresh();
    }

    private String chatTitle(TLRPC.User user) {
        if (user != null && (!TextUtils.isEmpty(user.first_name) || !TextUtils.isEmpty(user.last_name))) {
            return ContactsController.formatName(user.first_name, user.last_name);
        }
        return getString(R.string.AppName);
    }

    private void applyTitleLayout() {
        titleCentered = DevGramIOSConfig.centerChatTitle();
        avatarContainer.setMoveText(titleCentered);
        avatarContainer.getTitleTextView().setGravity(titleCentered ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        if (avatarContainer.getSubtitleTextView() instanceof SimpleTextView) {
            SimpleTextView subtitle = (SimpleTextView) avatarContainer.getSubtitleTextView();
            subtitle.setGravity(titleCentered ? Gravity.CENTER_HORIZONTAL : Gravity.START);
            subtitle.setTranslationX(titleCentered ? -dp(DevGramIOSConfig.iosUnreadCounter() ? 46 : 54) : 0);
        } else if (avatarContainer.getSubtitleTextView() instanceof AnimatedTextView) {
            ((AnimatedTextView) avatarContainer.getSubtitleTextView()).setGravity(
                    titleCentered ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        }
    }

    public void refresh() {
        applyTitleLayout();
        if (actionBar.getBackButtonDrawable() instanceof BackDrawable) {
            ((BackDrawable) actionBar.getBackButtonDrawable()).setShowStick(!titleCentered);
        }
        actionBar.setForceAdaptiveWidth(titleCentered && DevGramIOSConfig.adaptiveTitleWidth());
        actionBar.setIOSUnreadBadgeAvailable(titleCentered && DevGramIOSConfig.showUnreadCounter()
                && DevGramIOSConfig.iosUnreadCounter());
        if (actionBar.backButtonImageView != null) {
            actionBar.backButtonImageView.checkUnreadView(DevGramIOSConfig.showUnreadCounter() ? 10 : 0);
        }
        actionBar.updateBackPillWidth(true);
        avatarContainer.requestLayout();
        actionBar.requestLayout();
        invalidate();
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
}

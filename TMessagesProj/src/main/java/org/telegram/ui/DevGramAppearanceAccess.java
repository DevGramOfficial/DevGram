package org.telegram.ui;

import android.app.Dialog;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramProfilePolicy;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.DevGramBadgeEmojiDrawable;

/** Locked rows stay visible and clickable, including inside option selectors. */
public final class DevGramAppearanceAccess {
    private DevGramAppearanceAccess() {}

    public static boolean allowed(BaseFragment fragment, String feature) {
        return feature == null || DevGramProfilePolicy.allowed(fragment.getUserConfig().getClientUserId(), feature);
    }

    public static CharSequence label(BaseFragment fragment, String feature, CharSequence text) {
        if (allowed(fragment, feature)) return text;
        SpannableStringBuilder result = new SpannableStringBuilder("  ");
        ColoredImageSpan lock = new ColoredImageSpan(R.drawable.msg_secret);
        lock.setSize(AndroidUtilities.dp(14));
        result.setSpan(lock, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.append(text);
        return result;
    }

    public static boolean check(BaseFragment fragment, String feature) {
        return check(fragment, feature, null);
    }

    public static boolean check(BaseFragment fragment, String feature, View source) {
        if (allowed(fragment, feature)) return true;
        if (fragment.getParentActivity() == null) return false;
        int tier = DevGramProfilePolicy.tier(feature);
        if (tier == 1) {
            showSupporterBulletin(fragment, source);
            return false;
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        dialog.setTitle(DevGramLocale.text(tier == 2 ? "Для разработчиков" : "Функция временно отключена"));
        dialog.setMessage(DevGramLocale.text(tier == 2 ? "Эта возможность доступна со значком с функционалом разработчика." : "Команда проекта временно отключила эту возможность."));
        dialog.setPositiveButton(DevGramLocale.text("Понятно"), null);
        dialog.setNegativeButton(DevGramLocale.getString(R.string.Cancel), null);
        fragment.showDialog(dialog.create());
        return false;
    }

    private static FrameLayout bulletinContainer(BaseFragment fragment, View source) {
        View fragmentRoot = fragment.getFragmentView() == null ? null : fragment.getFragmentView().getRootView();
        // Standalone catalogs use their own Dialog, outside BaseFragment.visibleDialog.
        if (source != null && source.isAttachedToWindow() && source.getRootView() != fragmentRoot) {
            FrameLayout window = windowContainer(source.getRootView());
            if (window != null) return window;
        }
        Dialog dialog = fragment.getVisibleDialog();
        if (dialog != null && dialog.isShowing()) {
            if (dialog instanceof BottomSheet) return ((BottomSheet) dialog).getContainer();
            if (dialog.getWindow() != null) {
                FrameLayout window = windowContainer(dialog.getWindow().getDecorView());
                if (window != null) return window;
            }
        }
        return fragment.getLayoutContainer();
    }

    private static FrameLayout windowContainer(View root) {
        View content = root.findViewById(android.R.id.content);
        if (content instanceof FrameLayout) return (FrameLayout) content;
        return root instanceof FrameLayout ? (FrameLayout) root : null;
    }

    private static int missingBottomInset(int rootTop, int rootHeight, int containerTop, int containerHeight,
                                          int paddingBottom, int systemInset, float existingOffset) {
        int safeBottom = rootTop + rootHeight - systemInset;
        int contentBottom = containerTop + containerHeight - paddingBottom;
        return (int) Math.ceil(Math.max(0, contentBottom - safeBottom - Math.max(0, existingOffset)));
    }

    private static void showSupporterBulletin(BaseFragment fragment, View source) {
        FrameLayout container = bulletinContainer(fragment, source);
        if (container == null) return;
        Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(fragment.getParentActivity(), fragment.getResourceProvider()) {
            private final int[] rootLocation = new int[2], containerLocation = new int[2];
            private final DevGramBadgeEmojiDrawable plane = new DevGramBadgeEmojiDrawable(
                    imageView, AndroidUtilities.dp(32), AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS,
                    () -> Theme.isCurrentThemeDark() ? 0xffffffff : 0xff000000);
            {
                plane.setCurrentAccount(fragment.getCurrentAccount());
                plane.set(DevGramBadges.EMOJI_SUPPORTER, false);
                imageView.setImageDrawable(plane);
            }

            @Override protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                plane.attach();
            }

            @Override protected void onDetachedFromWindow() {
                plane.detach();
                super.onDetachedFromWindow();
            }

            @Override public void updatePosition() {
                super.updatePosition();
                if (rootLocation == null || containerLocation == null) return;
                View root = container.getRootView();
                androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(container);
                int bottom = insets == null ? AndroidUtilities.navigationBarHeight
                        : Math.max(insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom,
                                   insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom);
                root.getLocationOnScreen(rootLocation);
                container.getLocationOnScreen(containerLocation);
                int missing = missingBottomInset(rootLocation[1], root.getHeight(), containerLocation[1], container.getHeight(),
                        container.getPaddingBottom(), bottom, getBottomOffset());
                setTranslationY(getTranslationY() - missing);
            }
        };
        layout.titleTextView.setText(DevGramLocale.text("Эксклюзив для поддержавших"));
        layout.subtitleTextView.setText(DevGramLocale.text("Поддержите проект и откройте доступ ко всем возможностям."));
        layout.setButton(new Bulletin.UndoButton(fragment.getParentActivity(), true, fragment.getResourceProvider())
                .setText(DevGramLocale.text("Подробнее"))
                .setUndoAction(() -> DevGramSupportSheet.show(fragment)));
        BulletinFactory.of(container, fragment.getResourceProvider()).create(layout, Bulletin.DURATION_PROLONG).show();
    }
}

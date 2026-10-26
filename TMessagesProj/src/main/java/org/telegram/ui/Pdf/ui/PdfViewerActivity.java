package org.telegram.ui.Pdf.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowInsetsCompat;
import org.telegram.ui.Pdf.PdfDocumentRenderer;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FlagSecureReason;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.layouts.ButtonOnClickListener;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;
import org.telegram.ui.Components.chat.layouts.ChatActivitySideControlsButtonsLayout;
import org.telegram.ui.Components.inset.WindowAnimatedInsetsProvider;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;
import org.telegram.ui.LaunchActivity;
public class PdfViewerActivity extends BaseFragment {
    private WindowAnimatedInsetsProvider animatedInsetsProvider;
    private int bottomInset;
    private ChatActivityFadeView fadeView;
    private final File file;
    private FlagSecureReason flagSecure;
    private float headerFadeAlpha;
    private ValueAnimator headerFadeAnimator;
    private boolean headerFadeShown;
    private final RectF iBlur3PositionActionBar;
    private final RectF iBlur3PositionBottom;
    private final ArrayList<RectF> iBlur3Positions;
    private final BlurredBackgroundSourceColor iBlur3SourceColor;
    private final BlurredBackgroundSourceRenderNode iBlur3SourceGlass;
    private final MessageObject messageObject;
    private final boolean noforwards;
    private ActionBarMenuItem otherItem;
    private PdfPagesView pagesView;
    private String pendingQuery;
    private final PdfDocumentRenderer renderer;
    private final DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private ChatActivitySideControlsButtonsLayout searchButtonsLayout;
    private SearchCounterView searchCounterView;
    private boolean searchPanelShown;
    private boolean searching;
    private int topInset;
    private final Runnable searchRunnable = () -> lambda_new_0();
    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(() -> updateBottomControlsPosition());

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
    public /* synthetic */ void lambda_new_0() {
        PdfPagesView pdfPagesView = this.pagesView;
        if (pdfPagesView != null) {
            pdfPagesView.search(this.pendingQuery);
        }
    }

    public static boolean isPdf(MessageObject messageObject) {
        String mimeType;
        return (messageObject == null || messageObject.getDocument() == null || (((mimeType = messageObject.getMimeType()) == null || !mimeType.toLowerCase(Locale.ROOT).startsWith("application/pdf")) && !"PDF".equals(messageObject.getExtension()))) ? false : true;
    }

    private static boolean fileExists(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.isFile();
    }

    private static String getPathToMessage(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        org.telegram.messenger.FileLoader fileLoader = org.telegram.messenger.FileLoader.getInstance(messageObject.currentAccount);
        if (messageObject.messageOwner != null && fileExists(messageObject.messageOwner.attachPath)) {
            return messageObject.messageOwner.attachPath;
        }
        if (messageObject.messageOwner != null) {
            String path = fileLoader.getPathToMessage(messageObject.messageOwner).toString();
            if (fileExists(path)) {
                return path;
            }
        }
        if (messageObject.getDocument() != null) {
            String path = fileLoader.getPathToAttach(messageObject.getDocument(), false).toString();
            if (fileExists(path)) {
                return path;
            }
            path = fileLoader.getPathToAttach(messageObject.getDocument(), true).toString();
            if (fileExists(path)) {
                return path;
            }
        }
        return null;
    }

    public static boolean open(final BaseFragment baseFragment, final MessageObject messageObject) {
        Activity parentActivity = baseFragment == null ? null : baseFragment.getParentActivity();
        if (parentActivity == null) {
            return false;
        }
        String pathToMessage = getPathToMessage(messageObject);
        if (TextUtils.isEmpty(pathToMessage)) {
            return false;
        }
        final File file = new File(pathToMessage);
        final AlertDialog alertDialog = new AlertDialog(parentActivity, 3, baseFragment.getResourceProvider());
        alertDialog.setCanceledOnTouchOutside(false);
        final boolean[] zArr = {false};
        alertDialog.setOnCancelListener(dialogInterface -> PdfViewerActivity.$r8$lambda$UpJB47WVJo_PH68Y_6gGd0l7Drk(zArr, dialogInterface));
        alertDialog.showDelayed(150L);
        PdfDocumentRenderer.open(file, (obj, obj2) -> PdfViewerActivity.$r8$lambda$Fa1oIGZSGWUNS3wead9KO99U1pA(alertDialog, zArr, baseFragment, messageObject, file, (PdfDocumentRenderer) obj, (Throwable) obj2));
        return true;
    }

    public static /* synthetic */ void $r8$lambda$UpJB47WVJo_PH68Y_6gGd0l7Drk(boolean[] zArr, DialogInterface dialogInterface) {
        zArr[0] = true;
    }

    public static /* synthetic */ void $r8$lambda$Fa1oIGZSGWUNS3wead9KO99U1pA(AlertDialog alertDialog, boolean[] zArr, BaseFragment baseFragment, MessageObject messageObject, File file, PdfDocumentRenderer pdfDocumentRenderer, Throwable th) {
        try {
            alertDialog.dismiss();
        } catch (Throwable unused) {
        }
        if (pdfDocumentRenderer == null) {
            FileLog.e(th);
            if (zArr[0] || baseFragment.getParentActivity() == null) {
                return;
            }
            openExternally(baseFragment, messageObject);
            return;
        }
        if (zArr[0] || baseFragment.getParentActivity() == null || !baseFragment.presentFragment(new PdfViewerActivity(messageObject, file, pdfDocumentRenderer))) {
            pdfDocumentRenderer.close();
        }
    }
    public static void openExternally(BaseFragment baseFragment, MessageObject messageObject) {
        try {
            if (AndroidUtilities.openForView(messageObject, baseFragment.getParentActivity(), baseFragment.getResourceProvider(), false)) {
                return;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        BulletinFactory.of(baseFragment).createErrorBulletin(LocaleController.formatString(R.string.NoHandleAppInstalled, messageObject.getMimeType())).show();
    }

    private PdfViewerActivity(MessageObject messageObject, File file, PdfDocumentRenderer pdfDocumentRenderer) {
        int i = Build.VERSION.SDK_INT;
        this.scrollableViewNoiseSuppressor = i >= 31 ? new DownscaleScrollableNoiseSuppressor() : null;
        BlurredBackgroundSourceColor blurredBackgroundSourceColor = new BlurredBackgroundSourceColor();
        this.iBlur3SourceColor = blurredBackgroundSourceColor;
        this.iBlur3SourceGlass = i >= 31 ? new BlurredBackgroundSourceRenderNode(blurredBackgroundSourceColor) : null;
        ArrayList<RectF> arrayList = new ArrayList<>();
        this.iBlur3Positions = arrayList;
        RectF rectF = new RectF();
        this.iBlur3PositionActionBar = rectF;
        RectF rectF2 = new RectF();
        this.iBlur3PositionBottom = rectF2;
        this.messageObject = messageObject;
        this.file = file;
        this.renderer = pdfDocumentRenderer;
        setCurrentAccount(messageObject.currentAccount);
        long dialogId = messageObject.getDialogId();
        this.noforwards = !org.telegram.messenger.DevGramConfig.allowRestricted() && (messageObject.messageOwner.noforwards || DialogObject.isEncryptedDialog(dialogId) || MessagesController.getInstance(this.currentAccount).isPeerNoForwards(dialogId));
        arrayList.add(rectF);
        arrayList.add(rectF2);
    }

    @Override
    public View createView(Context context) {
        BlurredBackgroundDrawableViewFactory blurredBackgroundDrawableViewFactory;
        BlurredBackgroundSourceRenderNode blurredBackgroundSourceRenderNode;
        String documentName = this.messageObject.getDocumentName();
        this.actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        this.actionBar.setAllowOverlayTitle(false);
        ActionBar actionBar = this.actionBar;
        if (TextUtils.isEmpty(documentName)) {
            documentName = this.file.getName();
        }
        actionBar.setTitle(documentName);
        this.actionBar.setCastShadows(false);
        this.actionBar.setAddToContainer(false);
        this.actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int i) {
                if (i == -1) {
                    PdfViewerActivity.this.finishFragment();
                    return;
                }
                if (i == 3) {
                    PdfViewerActivity pdfViewerActivity = PdfViewerActivity.this;
                    PdfViewerActivity.openExternally(pdfViewerActivity, pdfViewerActivity.messageObject);
                } else if (i == 5) {
                    PdfViewerActivity.this.saveToDownloads();
                } else if (i == 4) {
                    PdfViewerActivity.this.share();
                }
            }
        });
        ContentView contentView = new ContentView(context);
        int i = Theme.key_windowBackgroundGray;
        contentView.setBackgroundColor(getThemedColor(i));
        ActionBarMenu actionBarMenuCreateMenu = this.actionBar.createMenu();
        this.iBlur3SourceColor.setColor(getThemedColor(i));
        if (Build.VERSION.SDK_INT >= 31 && (blurredBackgroundSourceRenderNode = this.iBlur3SourceGlass) != null) {
            blurredBackgroundDrawableViewFactory = new BlurredBackgroundDrawableViewFactory(blurredBackgroundSourceRenderNode);
            blurredBackgroundDrawableViewFactory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(262144));
        } else {
            blurredBackgroundDrawableViewFactory = new BlurredBackgroundDrawableViewFactory(this.iBlur3SourceColor);
        }
        blurredBackgroundDrawableViewFactory.setSourceRootView(new ViewPositionWatcher(contentView), contentView);
        BlurredBackgroundProvider blurredBackgroundProvider = BlurredBackgroundProviderImpl.topPanelChatActivity(getResourceProvider());
        this.actionBar.setGlassPadding(AndroidUtilities.dp(12.0f));
        this.actionBar.setGlassTitleTextSize(20);
        this.actionBar.setupGlass(blurredBackgroundDrawableViewFactory, blurredBackgroundProvider);
        this.actionBar.setDrawGlassMiddlePill(false);
        this.actionBar.setGlassShadowAlpha(0.0f);
        ActionBar actionBar2 = this.actionBar;
        int i2 = Theme.key_windowBackgroundWhiteBlackText;
        actionBar2.setTitleColor(getThemedColor(i2));
        this.actionBar.setItemsColor(getThemedColor(i2), false);
        this.actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_listSelector), false);
        if (this.renderer.supportsSearch) {
            ActionBarMenuItem actionBarMenuItemSearchListener = actionBarMenuCreateMenu.addItem(1, R.drawable.outline_header_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    PdfViewerActivity.this.searching = true;
                    if (PdfViewerActivity.this.otherItem != null) {
                        PdfViewerActivity.this.otherItem.setVisibility(8);
                    }
                }

                @Override
                public void onSearchCollapse() {
                    PdfViewerActivity.this.searching = false;
                    PdfViewerActivity.this.pendingQuery = null;
                    AndroidUtilities.cancelRunOnUIThread(PdfViewerActivity.this.searchRunnable);
                    if (PdfViewerActivity.this.otherItem != null) {
                        PdfViewerActivity.this.otherItem.setVisibility(0);
                    }
                    if (PdfViewerActivity.this.pagesView != null) {
                        PdfViewerActivity.this.pagesView.search(null);
                    }
                }

                @Override
                public void onTextChanged(EditText editText) {
                    PdfViewerActivity.this.pendingQuery = editText.getText().toString();
                    AndroidUtilities.cancelRunOnUIThread(PdfViewerActivity.this.searchRunnable);
                    AndroidUtilities.runOnUIThread(PdfViewerActivity.this.searchRunnable, 300L);
                }

                @Override
                public void onSearchPressed(EditText editText) {
                    PdfViewerActivity.this.pendingQuery = editText.getText().toString();
                    AndroidUtilities.cancelRunOnUIThread(PdfViewerActivity.this.searchRunnable);
                    PdfViewerActivity.this.searchRunnable.run();
                    AndroidUtilities.hideKeyboard(editText);
                }
            });
            actionBarMenuItemSearchListener.setSearchPaddingStart(7);
            actionBarMenuItemSearchListener.setSearchFieldHint(LocaleController.getString(R.string.Search));
            actionBarMenuItemSearchListener.setContentDescription(LocaleController.getString(R.string.Search));
        }
        if (!this.noforwards) {
            ActionBarMenuItem actionBarMenuItemAddItem = actionBarMenuCreateMenu.addItem(2, R.drawable.ic_ab_other);
            this.otherItem = actionBarMenuItemAddItem;
            actionBarMenuItemAddItem.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
            this.otherItem.addSubItem(3, R.drawable.msg_openin, LocaleController.getString(R.string.OpenInExternalApp));
            this.otherItem.addSubItem(5, R.drawable.msg_download, LocaleController.getString(R.string.SaveToDownloads));
            this.otherItem.addSubItem(4, R.drawable.msg_shareout, LocaleController.getString(R.string.ShareFile));
        } else {
            this.flagSecure = new FlagSecureReason(getParentActivity().getWindow(), () -> PdfViewerActivity.m1596$r8$lambda$D3m0gykqlu_GBZEc78O8Q386gs());
        }
        PdfPagesView pdfPagesView = new PdfPagesView(context, this.renderer, new PdfPagesView.Delegate() {
            @Override
            public void onLinkClick(String str) {
                AlertsCreator.showOpenUrlAlert(PdfViewerActivity.this, str, true, true);
            }

            @Override
            public void onViewportChanged(float f, float f2) {
                PdfViewerActivity.this.updateHeaderFade(true);
                if (Build.VERSION.SDK_INT < 31 || PdfViewerActivity.this.scrollableViewNoiseSuppressor == null) {
                    return;
                }
                PdfViewerActivity.this.scrollableViewNoiseSuppressor.onScrolled(f, f2);
                PdfViewerActivity.this.blur3_InvalidateBlur();
            }

            @Override
            public void onSearchUpdated(int i3, int i4, boolean z) {
                PdfViewerActivity.this.updateSearchPanel(i3, i4, z);
            }

            @Override
            public void onTextCopied() {
                BulletinFactory.of(PdfViewerActivity.this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
            }

            @Override
            public void onTranslateText(CharSequence charSequence, String str, Runnable runnable) {
                Activity parentActivity = PdfViewerActivity.this.getParentActivity();
                PdfViewerActivity pdfViewerActivity = PdfViewerActivity.this;
                TranslateAlert2.showAlert(parentActivity, pdfViewerActivity, pdfViewerActivity.getCurrentAccount(), str, TranslateAlert2.getToLanguage(), charSequence, null, false, null, runnable);
            }

            @Override
            public void onShareText(String str) {
                PdfViewerActivity.this.shareText(str);
            }
        }, getResourceProvider());
        this.pagesView = pdfPagesView;
        pdfPagesView.setTextSelectionEnabled(true ^ this.noforwards);
        contentView.addView(this.pagesView, LayoutHelper.createFrame(-1, -1.0f));
        ChatActivityFadeView chatActivityFadeView = new ChatActivityFadeView(context);
        this.fadeView = chatActivityFadeView;
        chatActivityFadeView.setupColorKey(i);
        this.fadeView.setFadeTopAlpha(0);
        contentView.addView(this.fadeView, LayoutHelper.createFrameMatchParent());
        contentView.addView(this.pagesView.createOverlayView(context), LayoutHelper.createFrameMatchParent());
        if (this.renderer.supportsSearch) {
            SearchCounterView searchCounterView = new SearchCounterView(context, blurredBackgroundDrawableViewFactory, blurredBackgroundProvider);
            this.searchCounterView = searchCounterView;
            searchCounterView.setVisibility(8);
            contentView.addView(this.searchCounterView, LayoutHelper.createFrame(-1, 48.0f, 83, 7.0f, 0.0f, 70.0f, 0.0f));
            ChatActivitySideControlsButtonsLayout chatActivitySideControlsButtonsLayout = new ChatActivitySideControlsButtonsLayout(context, getResourceProvider(), blurredBackgroundProvider, blurredBackgroundDrawableViewFactory);
            this.searchButtonsLayout = chatActivitySideControlsButtonsLayout;
            chatActivitySideControlsButtonsLayout.setOnClickListener((i3, view) -> lambda_createView_4(i3, view));
            contentView.addView(this.searchButtonsLayout, LayoutHelper.createFrame(57, 300.0f, 85, 0.0f, 0.0f, 2.0f, 0.0f));
            updateBottomControlsPosition();
        }
        contentView.addView(this.actionBar, LayoutHelper.createFrame(-1, -2, 48));
        if (context instanceof LaunchActivity) {
            WindowAnimatedInsetsProvider rootAnimatedInsetsListener = ((LaunchActivity) context).getRootAnimatedInsetsListener();
            this.animatedInsetsProvider = rootAnimatedInsetsListener;
            this.windowInsetsStateHolder.setupAnimatedInsetsProvider(rootAnimatedInsetsListener, contentView);
        }
        this.fragmentView = contentView;
        return contentView;
    }
    public static /* synthetic */ boolean m1596$r8$lambda$D3m0gykqlu_GBZEc78O8Q386gs() {
        return true;
    }
    public /* synthetic */ void lambda_createView_4(int i, View view) {
        PdfPagesView pdfPagesView = this.pagesView;
        if (pdfPagesView != null) {
            pdfPagesView.searchNavigate(i == 5);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        FlagSecureReason flagSecureReason = this.flagSecure;
        if (flagSecureReason != null) {
            flagSecureReason.attach();
        }
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int i) {
                return PdfViewerActivity.this.topInset;
            }

            @Override
            public int getBottomOffset(int i) {
                return PdfViewerActivity.this.getPagesBottomInset();
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        Bulletin.removeDelegate(this);
    }

    @Override
    public void onBecomeFullyHidden() {
        super.onBecomeFullyHidden();
        FlagSecureReason flagSecureReason = this.flagSecure;
        if (flagSecureReason != null) {
            flagSecureReason.detach();
        }
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(this.searchRunnable);
        WindowAnimatedInsetsProvider windowAnimatedInsetsProvider = this.animatedInsetsProvider;
        if (windowAnimatedInsetsProvider != null) {
            windowAnimatedInsetsProvider.unsubscribeFromWindowInsetsAnimation(this.windowInsetsStateHolder);
            this.animatedInsetsProvider = null;
        }
        ValueAnimator valueAnimator = this.headerFadeAnimator;
        if (valueAnimator != null) {
            valueAnimator.cancel();
            this.headerFadeAnimator = null;
        }
        FlagSecureReason flagSecureReason = this.flagSecure;
        if (flagSecureReason != null) {
            flagSecureReason.detach();
        }
        PdfPagesView pdfPagesView = this.pagesView;
        if (pdfPagesView != null) {
            pdfPagesView.destroy();
        }
        this.renderer.close();
        super.onFragmentDestroy();
    }

    @Override
    public boolean onBackPressed(boolean z) {
        PdfPagesView pdfPagesView = this.pagesView;
        if (pdfPagesView != null && pdfPagesView.hasTextSelection()) {
            if (z) {
                this.pagesView.clearTextSelection();
            }
            return false;
        }
        if (!this.searching) {
            return super.onBackPressed(z);
        }
        if (z) {
            this.actionBar.closeSearchField();
        }
        return false;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent motionEvent) {
        PdfPagesView pdfPagesView = this.pagesView;
        return pdfPagesView == null || !pdfPagesView.isZoomed();
    }

    @Override
    public boolean isLightStatusBar() {
        ActionBar actionBar = this.actionBar;
        if (actionBar == null) {
            return super.isLightStatusBar();
        }
        return AndroidUtilities.computePerceivedBrightness(actionBar.isActionModeShowed() ? actionBar.getBackgroundColor() : getThemedColor(Theme.key_windowBackgroundGray)) > 0.721f;
    }

    @Override
    public WindowInsetsCompat onInsetsInternal(View view, WindowInsetsCompat windowInsetsCompat) {
        WindowInsetsCompat windowInsetsCompatOnInsetsInternal = super.onInsetsInternal(view, windowInsetsCompat);
        this.windowInsetsStateHolder.setInsets(windowInsetsCompat);
        return windowInsetsCompatOnInsetsInternal;
    }

    @Override
    public void onInsets(int i, int i2, int i3, int i4) {
        this.topInset = i2 + ActionBar.getCurrentActionBarHeight();
        this.bottomInset = i4;
        updateBottomControlsPosition();
    }

    private int getKeyboardAwareBottomInset() {
        return Math.max(this.bottomInset, Math.round(this.windowInsetsStateHolder.getAnimatedMaxBottomInset()));
    }
    public int getPagesBottomInset() {
        return getKeyboardAwareBottomInset() + (this.searchPanelShown ? AndroidUtilities.dp(64.0f) : 0);
    }
    public void updateBottomControlsPosition() {
        int keyboardAwareBottomInset = getKeyboardAwareBottomInset();
        ChatActivitySideControlsButtonsLayout chatActivitySideControlsButtonsLayout = this.searchButtonsLayout;
        if (chatActivitySideControlsButtonsLayout != null) {
            chatActivitySideControlsButtonsLayout.setTranslationY((-keyboardAwareBottomInset) - AndroidUtilities.dp(4.0f));
        }
        SearchCounterView searchCounterView = this.searchCounterView;
        if (searchCounterView != null) {
            searchCounterView.setTranslationY((-keyboardAwareBottomInset) - AndroidUtilities.dp(8.0f));
        }
        PdfPagesView pdfPagesView = this.pagesView;
        if (pdfPagesView != null) {
            pdfPagesView.setInsets(this.topInset, getPagesBottomInset());
        }
    }
    /* JADX WARN: Code duplicated, block: B:16:0x0022  */
    public void updateSearchPanel(int i, int i2, boolean z) {
        boolean z2;
        String string;
        if (this.searchCounterView == null || this.searchButtonsLayout == null) {
            return;
        }
        if (this.searching) {
            String str = this.pendingQuery;
            if (TextUtils.isEmpty(str == null ? null : str.trim())) {
                z2 = false;
            } else {
                z2 = true;
            }
        } else {
            z2 = false;
        }
        if (z2) {
            if (i2 <= 0) {
                string = LocaleController.getString(z ? R.string.NoResult : R.string.Loading);
            } else {
                string = LocaleController.formatString(R.string.Of, Integer.valueOf(i + 1), Integer.valueOf(i2));
            }
            this.searchCounterView.textView.setText(string, this.searchPanelShown);
        }
        this.searchButtonsLayout.showButton(5, z2 && i2 > 0, true);
        this.searchButtonsLayout.showButton(6, z2 && i2 > 0, true);
        this.searchButtonsLayout.setButtonEnabled(5, i2 > 1, true);
        this.searchButtonsLayout.setButtonEnabled(6, i2 > 1, true);
        if (this.searchPanelShown != z2) {
            this.searchPanelShown = z2;
            this.searchCounterView.animate().cancel();
            if (z2) {
                this.searchCounterView.setVisibility(0);
                this.searchCounterView.setAlpha(0.0f);
                this.searchCounterView.setScaleX(0.7f);
                this.searchCounterView.setScaleY(0.7f);
            }
            this.searchCounterView.animate().alpha(z2 ? 1.0f : 0.0f).scaleX(z2 ? 1.0f : 0.7f).scaleY(z2 ? 1.0f : 0.7f).setDuration(280L).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> lambda_updateSearchPanel_5()).start();
            updateBottomControlsPosition();
        }
    }
    public /* synthetic */ void lambda_updateSearchPanel_5() {
        if (this.searchPanelShown) {
            return;
        }
        this.searchCounterView.setVisibility(8);
    }
    public void blur3_InvalidateBlur() {
        if (this.fragmentView == null || this.pagesView == null || Build.VERSION.SDK_INT < 31 || this.scrollableViewNoiseSuppressor == null || !SharedConfig.chatBlurEnabled()) {
            return;
        }
        int measuredWidth = this.fragmentView.getMeasuredWidth();
        int measuredHeight = this.fragmentView.getMeasuredHeight();
        int iDp = AndroidUtilities.dp(48.0f);
        float f = measuredWidth;
        this.iBlur3PositionActionBar.set(0.0f, -iDp, f, this.actionBar.getMeasuredHeight() + iDp);
        this.iBlur3PositionBottom.set(0.0f, (measuredHeight - getKeyboardAwareBottomInset()) - AndroidUtilities.dp(160.0f), f, measuredHeight);
        this.scrollableViewNoiseSuppressor.setupRenderNodes(this.iBlur3Positions, this.searching ? 2 : 1);
        this.scrollableViewNoiseSuppressor.invalidateResultRenderNodes(this.pagesView, measuredWidth, measuredHeight);
    }
    public void updateTopFade() {
        ChatActivityFadeView chatActivityFadeView = this.fadeView;
        if (chatActivityFadeView == null) {
            return;
        }
        int barHeight = this.actionBar.getMeasuredHeight();
        int fadeZone = barHeight + AndroidUtilities.dp(42.0f);
        int fadeZoneBase = barHeight + AndroidUtilities.dp(42.0f);
        chatActivityFadeView.setFadeZoneTop(barHeight == 0 || fadeZoneBase >= fadeZone ? fadeZone : fadeZoneBase + Math.round((fadeZone - fadeZoneBase) * 0.5f));
        chatActivityFadeView.setTopFadeColor(getThemedColor(Theme.key_windowBackgroundGray));
        chatActivityFadeView.setFadeHeightTop(AndroidUtilities.dp(78.0f));
        this.fadeView.setFadeTopAlpha(Math.round(this.headerFadeAlpha * 255.0f));
    }
    public void updateHeaderFade(boolean z) {
        PdfPagesView pdfPagesView;
        boolean zCanScrollVertically;
        if (this.fadeView == null || (pdfPagesView = this.pagesView) == null || this.headerFadeShown == (zCanScrollVertically = pdfPagesView.canScrollVertically(-1))) {
            return;
        }
        this.headerFadeShown = zCanScrollVertically;
        ValueAnimator valueAnimator = this.headerFadeAnimator;
        if (valueAnimator != null) {
            valueAnimator.cancel();
            this.headerFadeAnimator = null;
        }
        if (!z) {
            setHeaderFadeAlpha(zCanScrollVertically ? 1.0f : 0.0f);
            return;
        }
        ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(this.headerFadeAlpha, zCanScrollVertically ? 1.0f : 0.0f);
        this.headerFadeAnimator = valueAnimatorOfFloat;
        valueAnimatorOfFloat.addUpdateListener(valueAnimator2 -> lambda_updateHeaderFade_6(valueAnimator2));
        this.headerFadeAnimator.setDuration(320L);
        this.headerFadeAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        this.headerFadeAnimator.start();
    }
    public /* synthetic */ void lambda_updateHeaderFade_6(ValueAnimator valueAnimator) {
        setHeaderFadeAlpha(((Float) valueAnimator.getAnimatedValue()).floatValue());
    }

    private void setHeaderFadeAlpha(float f) {
        this.headerFadeAlpha = f;
        ChatActivityFadeView chatActivityFadeView = this.fadeView;
        if (chatActivityFadeView != null) {
            chatActivityFadeView.setFadeTopAlpha(Math.round(f * 255.0f));
        }
    }
    public void shareText(String str) {
        Activity parentActivity = getParentActivity();
        if (parentActivity == null) {
            return;
        }
        try {
            Intent intent = new Intent("android.intent.action.SEND");
            intent.setType("text/plain");
            intent.putExtra("android.intent.extra.TEXT", str);
            parentActivity.startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile)), 500);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
    public void saveToDownloads() {
        Activity parentActivity = getParentActivity();
        if (parentActivity == null) {
            return;
        }
        if ((Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && parentActivity.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != 0) {
            parentActivity.requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 4);
            return;
        }
        ArrayList arrayList = new ArrayList();
        arrayList.add(this.messageObject);
        MediaController.saveFilesFromMessages(parentActivity, getAccountInstance(), arrayList, i -> lambda_saveToDownloads_7(i));
    }
    public /* synthetic */ void lambda_saveToDownloads_7(int i) {
        if (getParentActivity() == null || this.fragmentView == null || i <= 0) {
            return;
        }
        BulletinFactory.of(this).createDownloadBulletin(BulletinFactory.FileType.UNKNOWN, getResourceProvider()).show();
    }
    public void share() {
        Activity parentActivity = getParentActivity();
        if (parentActivity == null) {
            return;
        }
        try {
            Intent intent = new Intent("android.intent.action.SEND");
            intent.setType("application/pdf");
            intent.putExtra("android.intent.extra.STREAM", FileProvider.getUriForFile(parentActivity, ApplicationLoader.getApplicationId() + ".provider", this.file));
            intent.addFlags(1);
            parentActivity.startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile)), 500);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public class ContentView extends FrameLayout implements Theme.Colorable {
        public ContentView(Context context) {
            super(context);
        }

        @Override
        public void onMeasure(int i, int i2) {
            super.onMeasure(i, i2);
            PdfViewerActivity.this.updateTopFade();
        }

        @Override
        public void onLayout(boolean z, int i, int i2, int i3, int i4) {
            super.onLayout(z, i, i2, i3, i4);
            PdfViewerActivity.this.updateHeaderFade(false);
        }

        @Override
        public void dispatchDraw(Canvas canvas) {
            if (Build.VERSION.SDK_INT >= 31 && PdfViewerActivity.this.scrollableViewNoiseSuppressor != null && PdfViewerActivity.this.iBlur3SourceGlass != null) {
                PdfViewerActivity.this.blur3_InvalidateBlur();
                if (!PdfViewerActivity.this.iBlur3SourceGlass.inRecording()) {
                    RecordingCanvas recordingCanvasBeginRecording = PdfViewerActivity.this.iBlur3SourceGlass.beginRecording(getMeasuredWidth(), getMeasuredHeight());
                    recordingCanvasBeginRecording.drawColor(PdfViewerActivity.this.getThemedColor(Theme.key_windowBackgroundGray));
                    if (SharedConfig.chatBlurEnabled()) {
                        PdfViewerActivity.this.scrollableViewNoiseSuppressor.draw(recordingCanvasBeginRecording, -2);
                    }
                    PdfViewerActivity.this.iBlur3SourceGlass.endRecording();
                }
            }
            super.dispatchDraw(canvas);
        }

        @Override
        public void updateColors() {
            int themedColor = PdfViewerActivity.this.getThemedColor(Theme.key_windowBackgroundGray);
            setBackgroundColor(themedColor);
            PdfViewerActivity.this.iBlur3SourceColor.setColor(themedColor);
            PdfViewerActivity.this.updateTopFade();
            if (PdfViewerActivity.this.pagesView != null) {
                PdfViewerActivity.this.pagesView.updateColors();
            }
            if (PdfViewerActivity.this.searchCounterView != null) {
                PdfViewerActivity.this.searchCounterView.updateColors();
            }
            if (PdfViewerActivity.this.searchButtonsLayout != null) {
                PdfViewerActivity.this.searchButtonsLayout.updateColors();
            }
        }
    }

    @SuppressLint({"ViewConstructor"})
    public class SearchCounterView extends FrameLayout {
        private final BlurredBackgroundDrawable backgroundDrawable;
        private final AnimatedTextView textView;

        public SearchCounterView(Context context, BlurredBackgroundDrawableViewFactory blurredBackgroundDrawableViewFactory, BlurredBackgroundColorProvider blurredBackgroundColorProvider) {
            super(context);
            setWillNotDraw(false);
            BlurredBackgroundDrawable blurredBackgroundDrawableCreate = blurredBackgroundDrawableViewFactory.create(this, blurredBackgroundColorProvider);
            this.backgroundDrawable = blurredBackgroundDrawableCreate;
            blurredBackgroundDrawableCreate.setRadius(AndroidUtilities.dp(24.0f));
            AnimatedTextView animatedTextView = new AnimatedTextView(context, true, true, true) {
                @Override
                public void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    SearchCounterView.this.invalidate();
                }
            };
            this.textView = animatedTextView;
            animatedTextView.setAnimationProperties(0.25f, 0L, 280L, CubicBezierInterpolator.EASE_OUT_QUINT);
            animatedTextView.setTextSize(AndroidUtilities.dp(15.0f));
            animatedTextView.setTypeface(AndroidUtilities.bold());
            animatedTextView.setGravity(3);
            addView(animatedTextView, LayoutHelper.createFrame(-1, 30.0f, 16, 18.0f, 0.0f, 18.0f, 0.0f));
            updateColors();
        }

        public void updateColors() {
            this.textView.setTextColor(PdfViewerActivity.this.getThemedColor(Theme.key_glass_defaultText));
            this.backgroundDrawable.updateColors();
            invalidate();
        }

        @Override
        public void onSizeChanged(int i, int i2, int i3, int i4) {
            super.onSizeChanged(i, i2, i3, i4);
            setPivotX(0.0f);
            setPivotY(i2);
        }

        @Override
        public void draw(Canvas canvas) {
            this.backgroundDrawable.setBounds(0, 0, Math.min(getWidth(), AndroidUtilities.dp(36.0f) + ((int) Math.ceil(this.textView.getDrawable().getCurrentWidth()))), getHeight());
            this.backgroundDrawable.draw(canvas);
            super.draw(canvas);
        }
    }
}

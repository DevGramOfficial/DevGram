package org.telegram.messenger.devgram.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import org.telegram.messenger.devgram.player.PlayerConfig;
import org.telegram.messenger.devgram.player.lyrics.Lyrics;
import org.telegram.messenger.devgram.player.lyrics.LyricsRepository;
import org.telegram.messenger.devgram.player.motion.ArtworkRepository;
import org.telegram.messenger.devgram.player.motion.MotionCoverRepository;
import org.telegram.messenger.devgram.player.rhythm.RhythmTap;
import org.telegram.messenger.devgram.player.ui.ExpressiveStateDrawable;
import org.telegram.messenger.devgram.player.ui.LyricsView;
import org.telegram.messenger.devgram.player.ui.PlayerControlsView;
import org.telegram.messenger.devgram.player.ui.PlayerCoverView;
import org.telegram.messenger.devgram.player.ui.PlayerQueueCell;
import org.telegram.messenger.devgram.player.ui.PlayerQueueView;
import org.telegram.messenger.devgram.player.ui.PlayerSettingsSheet;
import org.telegram.messenger.devgram.player.ui.RhythmBackground;
import org.telegram.messenger.devgram.player.ui.SpeedTextDrawable;
import org.telegram.messenger.devgram.player.ui.WavySeekBar;
import org.telegram.messenger.devgram.utils.ui.ColorTones;
import org.telegram.messenger.devgram.utils.ui.Material3Tokens;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSlider;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.CastSync;
import org.telegram.ui.ChooseQualityLayout;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CastMediaRouteButton;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekSpeedDrawable;
import org.telegram.ui.Components.SpeedIconDrawable;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.HintView2;
public class MusicPlayerSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, DownloadController.FileDownloadProgressListener, PlayerActions.Host {
    public static MusicPlayerSheet instance;
    private final int TAG;
    private final PlayerActions actions;
    private final AudioPlayerAlert.ClippingTextViewSwitcher artistView;
    private final Theme.ResourcesProvider baseResourcesProvider;
    private final SpringAnimation bufferSpring;
    private boolean castAvailable;
    private CastMediaRouteButton castButton;
    private final ExpressiveStateDrawable collapseBackground;
    private final ImageView collapseButton;
    private ValueAnimator colorAnimator;
    private final PlayerColors colors;
    private final PlayerControlsView controlsView;
    private final CoverColorsWait coverColorsWait;
    private final PlayerCoverView coverView;
    private final int currentAccount;
    private boolean currentAudioFinishedLoading;
    private String currentFile;
    private final TextView durationView;
    private final TextView headerSubtitle;
    private final TextView headerTitle;
    private long lastBufferedPositionCheck;
    private int lastDuration;
    private MessageObject lastMessageObject;
    private long lastRewindingTime;
    private long lastSpeedClick;
    private int lastTime;
    private long lastUpdateRewindingPlayerTime;
    private boolean lightStatusBar;
    private float lyricsAlpha;
    private final ExpressiveStateDrawable lyricsBackground;
    private final ImageView lyricsButton;
    private String lyricsKey;
    private int lyricsRequest;
    private final SpringAnimation lyricsSpring;
    private final LyricsView lyricsView;
    private int mode;
    private float modeProgress;
    private final SpringAnimation modeSpring;
    private final ExpressiveStateDrawable moreBackground;
    private final ImageView moreButton;
    private final ChooseQualityLayout.QualityIcon moreIcon;
    private int motionRequest;
    private boolean noforwards;
    private NotificationCenter.ObserversGroup observers;
    private final LaunchActivity parentActivity;
    private int pendingDirection;
    private final PlayerLayout playerLayout;
    private final ExpressiveStateDrawable profileBackground;
    private final ImageView profileButton;
    private float queueAlpha;
    private final ExpressiveStateDrawable queueBackground;
    private final ImageView queueButton;
    private final SpringAnimation queueSpring;
    private final PlayerQueueView queueView;
    private final ExpressiveStateDrawable repeatBackground;
    private final ImageView repeatButton;
    private int rewindPressedCount;
    private final Runnable rewindSeek;
    private final Runnable rewindStepRunnable;
    private float rewindingProgress;
    private int rewindingState;
    private final RhythmBackground rhythmBackground;
    private MessagesController.SavedMusicList savedMusicList;
    private final WavySeekBar seekBar;
    private float seekPlate;
    private final SpringAnimation seekPlateSpring;
    private final SeekSpeedDrawable seekSpeedDrawable;
    private PlayerSettingsSheet settingsSheet;
    private boolean showRemaining;
    private final ExpressiveStateDrawable shuffleBackground;
    private final ImageView shuffleButton;
    private final ExpressiveStateDrawable speedBackground;
    private final ImageView speedButton;
    private HintView2 speedHintView;
    private final SpeedTextDrawable speedIcon;
    private boolean speedMenuShown;
    private boolean swipeDismissAllowed;
    private PlayerColors targetColors;
    private PlayerColors themeColors;
    private final TextView timeView;
    private final AudioPlayerAlert.ClippingTextViewSwitcher titleView;
    private static final float[] SPEEDS = {0.5f, 1.0f, 1.2f, 1.5f, 1.7f, 2.0f};
    private static final float[] TOGGLE_SPEEDS = {1.0f, 1.5f, 2.0f};
    private static final int[] SPEED_ICONS = {R.drawable.msg_speed_slow, R.drawable.msg_speed_normal, R.drawable.msg_speed_medium, R.drawable.msg_speed_fast, R.drawable.msg_speed_veryfast, R.drawable.msg_speed_superfast};
    private static final int[] SPEED_NAMES = {R.string.SpeedSlow, R.string.SpeedNormal, R.string.SpeedMedium, R.string.SpeedFast, R.string.SpeedVeryFast, R.string.SpeedSuperFast};

    @Override
    public void onFailedDownload(String str, boolean z) {
    }

    @Override
    public void onProgressUpload(String str, long j, long j2, boolean z) {
    }

    @Override
    public void onSuccessDownload(String str) {
    }
    public /* synthetic */ void lambda_new_0() {
        updateCoverColors(true);
    }

    public MusicPlayerSheet(final Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);
        this.coverColorsWait = new CoverColorsWait(() -> PlayerConfig.getPlayerCoverColors(), () -> lambda_new_0());
        this.rhythmBackground = new RhythmBackground();
        this.mode = 0;
        this.lastTime = -1;
        this.rewindingProgress = -1.0f;
        this.rewindStepRunnable = new Runnable() {
            @Override
            public void run() {
                if (MusicPlayerSheet.this.rewindingState == 0 || MusicPlayerSheet.this.rewindPressedCount >= 3) {
                    return;
                }
                MusicPlayerSheet.this.rewindPressedCount++;
                if (MusicPlayerSheet.this.rewindingState > 0 && !MediaController.getInstance().isMessagePaused()) {
                    MediaController.getInstance().setRewindPlaybackSpeed(MusicPlayerSheet.this.getRewindMultiplier());
                }
                MusicPlayerSheet.this.seekSpeedDrawable.setSpeed(MusicPlayerSheet.this.rewindingState * MusicPlayerSheet.this.getRewindMultiplier(), true);
                if (MusicPlayerSheet.this.rewindPressedCount < 3) {
                    AndroidUtilities.runOnUIThread(this, 2000L);
                }
            }
        };
        this.rewindSeek = new Runnable() {
            @Override
            public void run() {
                int i = MusicPlayerSheet.this.rewindingState;
                MediaController mediaController = MediaController.getInstance();
                long duration = mediaController.getDuration();
                if (i == 0) {
                    return;
                }
                if (duration == 0 || duration == -9223372036854775807L) {
                    MusicPlayerSheet.this.lastRewindingTime = SystemClock.elapsedRealtime();
                    return;
                }
                long jElapsedRealtime = SystemClock.elapsedRealtime();
                long rewindMultiplier = (long) ((jElapsedRealtime - MusicPlayerSheet.this.lastRewindingTime) * MusicPlayerSheet.this.getRewindMultiplier());
                MusicPlayerSheet.this.lastRewindingTime = jElapsedRealtime;
                long j = jElapsedRealtime - MusicPlayerSheet.this.lastUpdateRewindingPlayerTime;
                MusicPlayerSheet musicPlayerSheet = MusicPlayerSheet.this;
                float f = duration;
                musicPlayerSheet.rewindingProgress = Utilities.clamp01(((musicPlayerSheet.rewindingProgress * f) + (((long) i) * rewindMultiplier)) / f);
                MessageObject playingMessageObject = mediaController.getPlayingMessageObject();
                if (playingMessageObject != null && playingMessageObject.isMusic()) {
                    if (i > 0 && !mediaController.isMessagePaused()) {
                        playingMessageObject.audioProgress = MusicPlayerSheet.this.rewindingProgress;
                    }
                    MusicPlayerSheet.this.updateProgress(playingMessageObject);
                }
                if (MusicPlayerSheet.this.rewindPressedCount > 0) {
                    if (i <= 0 || mediaController.isMessagePaused()) {
                        if (j > 200 || MusicPlayerSheet.this.rewindingProgress == 0.0f) {
                            MusicPlayerSheet.this.lastUpdateRewindingPlayerTime = jElapsedRealtime;
                            mediaController.seekToProgress(playingMessageObject, MusicPlayerSheet.this.rewindingProgress);
                            if (i < 0 && MusicPlayerSheet.this.rewindingProgress == 0.0f) {
                                mediaController.pauseByRewind();
                            }
                        }
                        if (MusicPlayerSheet.this.rewindingProgress > 0.0f) {
                            if (i < 0 || MusicPlayerSheet.this.rewindingProgress < 1.0f) {
                                AndroidUtilities.runOnUIThread(this, 16L);
                            }
                        }
                    }
                }
            }
        };
        this.useLightStatusBar = false;
        this.baseResourcesProvider = resourcesProvider;
        Activity activityFindActivity = AndroidUtilities.findActivity(context);
        LaunchActivity launchActivity = activityFindActivity instanceof LaunchActivity ? (LaunchActivity) activityFindActivity : LaunchActivity.instance;
        this.parentActivity = launchActivity;
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        int i = playingMessageObject != null ? playingMessageObject.currentAccount : UserConfig.selectedAccount;
        this.currentAccount = i;
        this.TAG = DownloadController.getInstance(i).generateObserverTag();
        this.actions = new PlayerActions(i, launchActivity, this);
        this.savedMusicList = MediaController.getInstance().currentSavedMusicList;
        PlayerColors playerColorsFromTheme = PlayerColors.fromTheme(resourcesProvider);
        this.themeColors = playerColorsFromTheme;
        PlayerColors playerColorsCopy = playerColorsFromTheme.copy();
        this.colors = playerColorsCopy;
        this.doNotOverlayNavigationBar = true;
        this.occupyNavigationBarWithoutKeyboard = true;
        this.useSmoothKeyboard = !AndroidUtilities.isTablet();
        this.allowNestedScroll = false;
        updateNavigationBar(playerColorsCopy);
        setCanDismissWithSwipe(true);
        final PlayerLayout playerLayout = new PlayerLayout(context);
        this.playerLayout = playerLayout;
        int i2 = this.backgroundPaddingLeft;
        playerLayout.setPadding(i2, 0, i2, 0);
        this.containerView = playerLayout;
        int i3 = R.drawable.arrow_more;
        ExpressiveStateDrawable expressiveStateDrawable = new ExpressiveStateDrawable(12.0f);
        this.collapseBackground = expressiveStateDrawable;
        ImageView imageViewCreateIconButton = createIconButton(context, i3, expressiveStateDrawable);
        this.collapseButton = imageViewCreateIconButton;
        imageViewCreateIconButton.setContentDescription(LocaleController.getString(R.string.PlayerCollapse));
        imageViewCreateIconButton.setOnClickListener(view -> lambda_new_1(view));
        playerLayout.addOwnView(imageViewCreateIconButton);
        ChooseQualityLayout.QualityIcon chooseQualityLayout$QualityIcon = new ChooseQualityLayout.QualityIcon(context, R.drawable.ic_ab_other, resourcesProvider);
        this.moreIcon = chooseQualityLayout$QualityIcon;
        ExpressiveStateDrawable expressiveStateDrawable2 = new ExpressiveStateDrawable(12.0f);
        this.moreBackground = expressiveStateDrawable2;
        ImageView imageViewCreateIconButton2 = createIconButton(context, 0, expressiveStateDrawable2);
        this.moreButton = imageViewCreateIconButton2;
        imageViewCreateIconButton2.setImageDrawable(chooseQualityLayout$QualityIcon);
        imageViewCreateIconButton2.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        imageViewCreateIconButton2.setOnClickListener(view -> lambda_new_2(view));
        playerLayout.addOwnView(imageViewCreateIconButton2);
        TextView textView = new TextView(context);
        this.headerTitle = textView;
        textView.setTextSize(1, 16.0f);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setSingleLine(true);
        TextUtils.TruncateAt truncateAt = TextUtils.TruncateAt.END;
        textView.setEllipsize(truncateAt);
        textView.setGravity(17);
        playerLayout.addOwnView(textView);
        TextView textView2 = new TextView(context);
        this.headerSubtitle = textView2;
        textView2.setTextSize(1, 13.0f);
        textView2.setTypeface(AndroidUtilities.regular());
        textView2.setSingleLine(true);
        textView2.setEllipsize(truncateAt);
        textView2.setGravity(17);
        playerLayout.addOwnView(textView2);
        LyricsView lyricsView = new LyricsView(context, resourcesProvider);
        this.lyricsView = lyricsView;
        lyricsView.setProgressMode(PlayerConfig.getLyricsProgress());
        lyricsView.setDelegate(new LyricsView.Delegate() {
            @Override
            public void onSeekTo(long j) {
                MessageObject playingMessageObject2 = MediaController.getInstance().getPlayingMessageObject();
                if (playingMessageObject2 != null) {
                    MediaController.getInstance().seekToProgressMs(playingMessageObject2, j);
                    if (MediaController.getInstance().isMessagePaused()) {
                        MediaController.getInstance().playMessage(playingMessageObject2);
                    }
                }
            }

            @Override
            public void onConsent() {
                PlayerConfig.setLyricsOnline(true);
                MusicPlayerSheet.this.lyricsKey = null;
                MusicPlayerSheet.this.loadLyrics(true);
            }

            @Override
            public void onRetry() {
                MusicPlayerSheet.this.lyricsKey = null;
                MusicPlayerSheet.this.loadLyrics(true);
            }
        });
        lyricsView.setAlpha(0.0f);
        lyricsView.setVisibility(4);
        playerLayout.addOwnView(lyricsView);
        PlayerQueueView playerQueueView = new PlayerQueueView(context, i);
        this.queueView = playerQueueView;
        playerQueueView.setDelegate(new AnonymousClass4());
        playerQueueView.setAlpha(0.0f);
        playerQueueView.setVisibility(4);
        playerLayout.addOwnView(playerQueueView);
        PlayerCoverView playerCoverView = new PlayerCoverView(context);
        this.coverView = playerCoverView;
        playerCoverView.setDelegate(new PlayerCoverView.Delegate() {
            @Override
            public void onSwipe(int i4) {
                MusicPlayerSheet musicPlayerSheet = MusicPlayerSheet.this;
                if (i4 > 0) {
                    musicPlayerSheet.pendingDirection = 1;
                    MediaController.getInstance().playNextMessage();
                } else {
                    musicPlayerSheet.pendingDirection = -1;
                    MessageObject playingMessageObject2 = MediaController.getInstance().getPlayingMessageObject();
                    if (playingMessageObject2 != null) {
                        playingMessageObject2.audioProgressSec = 0;
                    }
                    MediaController.getInstance().playPreviousMessage();
                }
                MusicPlayerSheet.this.coverView.cancelPendingSwipe();
            }

            @Override
            public void onCoverLoaded(MessageObject messageObject, ImageReceiver imageReceiver) {
                MusicPlayerSheet.this.onCoverBitmapLoaded(messageObject, imageReceiver);
            }

            @Override
            public boolean canDrag() {
                return MusicPlayerSheet.this.mode == 0 && MediaController.getInstance().getPlaylist().size() > 1;
            }

            @Override
            public MessageObject getNeighbor(int i4) {
                return MusicPlayerSheet.this.getNeighborMessage(i4);
            }
        });
        playerLayout.addOwnView(playerCoverView);
        AudioPlayerAlert.ClippingTextViewSwitcher clippingTextViewSwitcher = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            public TextView createTextView() {
                TextView textView3 = new TextView(context);
                textView3.setTextSize(1, 20.0f);
                textView3.setTypeface(AndroidUtilities.bold());
                textView3.setSingleLine(true);
                textView3.setEllipsize(TextUtils.TruncateAt.END);
                textView3.setGravity(19);
                textView3.setTextColor(MusicPlayerSheet.this.colors.text);
                return textView3;
            }
        };
        this.titleView = clippingTextViewSwitcher;
        clippingTextViewSwitcher.setPivotX(0.0f);
        clippingTextViewSwitcher.setPivotY(0.0f);
        playerLayout.addOwnView(clippingTextViewSwitcher);
        AnonymousClass7 anonymousClass7 = new AnonymousClass7(context, context);
        this.artistView = anonymousClass7;
        anonymousClass7.setPivotX(0.0f);
        anonymousClass7.setPivotY(0.0f);
        playerLayout.addOwnView(anonymousClass7);
        int i4 = R.drawable.media_like;
        ExpressiveStateDrawable expressiveStateDrawableCreateToggle = createToggle();
        this.profileBackground = expressiveStateDrawableCreateToggle;
        ImageView imageViewCreateIconButton3 = createIconButton(context, i4, expressiveStateDrawableCreateToggle);
        this.profileButton = imageViewCreateIconButton3;
        imageViewCreateIconButton3.setOnClickListener(view -> lambda_new_3(view));
        playerLayout.addOwnView(imageViewCreateIconButton3);
        WavySeekBar wavySeekBar = new WavySeekBar(context);
        this.seekBar = wavySeekBar;
        wavySeekBar.setDelegate(new WavySeekBar.Delegate() {
            @Override
            public void onSeek(float f) {
                MessageObject playingMessageObject2 = MediaController.getInstance().getPlayingMessageObject();
                if (playingMessageObject2 == null) {
                    return;
                }
                MediaController.getInstance().seekToProgress(playingMessageObject2, f);
                MusicPlayerSheet.this.updateProgress(playingMessageObject2);
            }

            @Override
            public void onDragStateChanged(boolean z) {
                MessageObject playingMessageObject2 = MediaController.getInstance().getPlayingMessageObject();
                if (playingMessageObject2 != null) {
                    MusicPlayerSheet.this.updateProgress(playingMessageObject2);
                }
            }

            @Override
            public CharSequence getTimeLabel(float f) {
                MessageObject playingMessageObject2 = MediaController.getInstance().getPlayingMessageObject();
                if (playingMessageObject2 == null) {
                    return null;
                }
                return AndroidUtilities.formatShortDuration((int) (playingMessageObject2.getDuration() * ((double) f)));
            }

            @Override
            public CharSequence getAccessibilityValue() {
                int iMax = Math.max(0, MusicPlayerSheet.this.lastTime);
                return LocaleController.formatString(R.string.AccDescrPlayerDuration, LocaleController.formatPluralString("Minutes", iMax / 60, new Object[0]) + ' ' + LocaleController.formatPluralString("Seconds", iMax % 60, new Object[0]), LocaleController.formatPluralString("Minutes", MusicPlayerSheet.this.lastDuration / 60, new Object[0]) + ' ' + LocaleController.formatPluralString("Seconds", MusicPlayerSheet.this.lastDuration % 60, new Object[0]));
            }
        });
        playerLayout.addOwnView(wavySeekBar);
        TextView textView3 = new TextView(context);
        this.timeView = textView3;
        textView3.setTextSize(1, 12.0f);
        textView3.setTypeface(AndroidUtilities.regular());
        textView3.setGravity(19);
        textView3.setImportantForAccessibility(2);
        playerLayout.addOwnView(textView3);
        TextView textView4 = new TextView(context);
        this.durationView = textView4;
        textView4.setTextSize(1, 12.0f);
        textView4.setTypeface(AndroidUtilities.regular());
        textView4.setGravity(21);
        textView4.setContentDescription(LocaleController.getString(R.string.PlayerShowRemaining));
        textView4.setOnClickListener(view -> lambda_new_4(view));
        playerLayout.addOwnView(textView4);
        PlayerControlsView playerControlsView = new PlayerControlsView(context);
        this.controlsView = playerControlsView;
        playerControlsView.setDelegate(new PlayerControlsView.Delegate() {
            @Override
            public void onPrevious() {
                if (MediaController.getInstance().getPlayingMessageObject() == null) {
                    return;
                }
                MusicPlayerSheet.this.pendingDirection = -1;
                MediaController.getInstance().playPreviousMessage();
            }

            @Override
            public void onPlayPause() {
                MusicPlayerSheet.this.togglePlay();
            }

            @Override
            public void onNext() {
                if (MediaController.getInstance().getPlayingMessageObject() == null) {
                    return;
                }
                MusicPlayerSheet.this.pendingDirection = 1;
                MediaController.getInstance().playNextMessage();
            }

            @Override
            public void onHold(int i5, boolean z) {
                MusicPlayerSheet.this.onRewindHold(i5, z);
            }
        });
        playerLayout.addOwnView(playerControlsView);
        int i5 = R.drawable.player_new_shuffle;
        ExpressiveStateDrawable expressiveStateDrawableCreateToggle2 = createToggle();
        this.shuffleBackground = expressiveStateDrawableCreateToggle2;
        ImageView imageViewCreateIconButton4 = createIconButton(context, i5, expressiveStateDrawableCreateToggle2);
        this.shuffleButton = imageViewCreateIconButton4;
        imageViewCreateIconButton4.setOnClickListener(view -> lambda_new_5(view));
        imageViewCreateIconButton4.setOnLongClickListener(view -> lambda_new_6(view));
        playerLayout.addOwnView(imageViewCreateIconButton4);
        int i6 = R.drawable.player_new_repeatall;
        ExpressiveStateDrawable expressiveStateDrawableCreateToggle3 = createToggle();
        this.repeatBackground = expressiveStateDrawableCreateToggle3;
        ImageView imageViewCreateIconButton5 = createIconButton(context, i6, expressiveStateDrawableCreateToggle3);
        this.repeatButton = imageViewCreateIconButton5;
        imageViewCreateIconButton5.setOnClickListener(view -> lambda_new_7(view));
        imageViewCreateIconButton5.setOnLongClickListener(view -> lambda_new_8(view));
        playerLayout.addOwnView(imageViewCreateIconButton5);
        ExpressiveStateDrawable inset = new ExpressiveStateDrawable(12.0f).setSelectedRadius(24.0f).setInset(0.0f, 6.0f);
        this.speedBackground = inset;
        ImageView imageViewCreateIconButton6 = createIconButton(context, 0, inset);
        this.speedButton = imageViewCreateIconButton6;
        SpeedTextDrawable speedTextDrawable = new SpeedTextDrawable();
        this.speedIcon = speedTextDrawable;
        imageViewCreateIconButton6.setImageDrawable(speedTextDrawable);
        imageViewCreateIconButton6.setOnClickListener(view -> lambda_new_9(view));
        imageViewCreateIconButton6.setOnLongClickListener(view -> lambda_new_10(view));
        playerLayout.addOwnView(imageViewCreateIconButton6);
        int i7 = R.drawable.formatting_quote;
        ExpressiveStateDrawable expressiveStateDrawableCreateModeSegment = createModeSegment(2);
        this.lyricsBackground = expressiveStateDrawableCreateModeSegment;
        ImageView imageViewCreateIconButton7 = createIconButton(context, i7, expressiveStateDrawableCreateModeSegment);
        this.lyricsButton = imageViewCreateIconButton7;
        imageViewCreateIconButton7.setContentDescription(LocaleController.getString(R.string.PlayerLyrics));
        imageViewCreateIconButton7.setOnClickListener(view -> lambda_new_11(view));
        playerLayout.addOwnView(imageViewCreateIconButton7);
        int i8 = R.drawable.player_new_queue;
        ExpressiveStateDrawable expressiveStateDrawableCreateModeSegment2 = createModeSegment(1);
        this.queueBackground = expressiveStateDrawableCreateModeSegment2;
        ImageView imageViewCreateIconButton8 = createIconButton(context, i8, expressiveStateDrawableCreateModeSegment2);
        this.queueButton = imageViewCreateIconButton8;
        imageViewCreateIconButton8.setContentDescription(LocaleController.getString(R.string.PlayerQueue));
        imageViewCreateIconButton8.setOnClickListener(view -> lambda_new_12(view));
        playerLayout.addOwnView(imageViewCreateIconButton8);
        Objects.requireNonNull(playerLayout);
        this.seekSpeedDrawable = new SeekSpeedDrawable(() -> playerLayout.invalidate(), false, true);
        SpringAnimation springAnimationCreateSpring = Material3Tokens.createSpring(0.8f, 380.0f, 0.002f, obj -> lambda_new_13((Float) obj));
        this.seekPlateSpring = springAnimationCreateSpring;
        springAnimationCreateSpring.addEndListener((dynamicAnimation, z, f, f2) -> lambda_new_14(dynamicAnimation, z, f, f2));
        this.modeSpring = createSpring(0.8f, 380.0f, obj -> lambda_new_15((Float) obj));
        this.lyricsSpring = createSpring(1.0f, 1600.0f, obj -> lambda_new_16((Float) obj));
        this.queueSpring = createSpring(1.0f, 1600.0f, obj -> lambda_new_17((Float) obj));
        this.bufferSpring = Material3Tokens.createSpring(1.0f, 750.0f, 1.0f, obj -> lambda_new_18((Float) obj));
        setupCast(context);
        this.observers = NotificationCenter.getInstance(i).createObserversGroup(this).add(NotificationCenter.messagePlayingDidReset).add(NotificationCenter.messagePlayingPlayStateChanged).add(NotificationCenter.messagePlayingDidStart).add(NotificationCenter.messagePlayingProgressDidChanged).add(NotificationCenter.messagePlayingDidSeek).add(NotificationCenter.fileLoaded).add(NotificationCenter.fileLoadProgressChanged).add(NotificationCenter.musicDidLoad).add(NotificationCenter.moreMusicDidLoad).add(NotificationCenter.musicIdsLoaded).addGlobal(NotificationCenter.messagePlayingSpeedChanged);
        ExpressiveStateDrawable[] expressiveStateDrawableArr = {expressiveStateDrawableCreateToggle, expressiveStateDrawableCreateToggle2, expressiveStateDrawableCreateToggle3, inset};
        for (int i9 = 0; i9 < 4; i9++) {
            expressiveStateDrawableArr[i9].setOnProgressChanged(() -> onToggleProgress());
        }
        this.lyricsBackground.setOnProgressChanged(() -> onModeSegmentsChanged());
        this.queueBackground.setOnProgressChanged(() -> onModeSegmentsChanged());
        applyColors(this.colors);
        this.queueView.update();
        bindMessage(false, false);
        int playerMode = PlayerConfig.getPlayerMode();
        if (playerMode == 1 || playerMode == 2) {
            setMode(playerMode, false);
        }
        updateOrderButtons(false);
        updateSpeedButtons(false);
        updateHeader();
    }
    public /* synthetic */ void lambda_new_1(View view) {
        dismiss();
    }
    public /* synthetic */ void lambda_new_2(View view) {
        showMenu();
    }
    public class AnonymousClass4 implements PlayerQueueView.Delegate {
        public AnonymousClass4() {
        }

        @Override
        public void onShowOptions(PlayerQueueCell playerQueueCell, MessageObject messageObject) {
            MusicPlayerSheet.this.showTrackOptions(playerQueueCell, messageObject);
        }

        @Override
        public void onReverseOrder() {
            MediaController.getInstance().setPlaybackOrderType(!SharedConfig.playOrderReversed ? 1 : 0);
            MusicPlayerSheet.this.updateOrderButtons(true);
            MusicPlayerSheet.this.queueView.update();
            MusicPlayerSheet.this.queueView.scrollToCurrent(false);
        }

        @Override
        public void onAddToList() {
            MusicPlayerSheet.this.actions.addToMyList(() -> lambda_onAddToList_0());
        }
        public /* synthetic */ void lambda_onAddToList_0() {
            MusicPlayerSheet.this.queueView.update();
            MusicPlayerSheet.this.updateHeader();
            MusicPlayerSheet.this.updateProfileState(true);
        }

        @Override
        public void onSearchFocus(boolean z) {
            MusicPlayerSheet.this.setAllowNestedScroll(!z);
            MusicPlayerSheet.this.playerLayout.requestLayout();
        }

        @Override
        public boolean isMyList() {
            return MusicPlayerSheet.this.isMyList();
        }

        @Override
        public boolean isNoForwards() {
            return MusicPlayerSheet.this.noforwards;
        }

        @Override
        public MessagesController.SavedMusicList getSavedMusicList() {
            return MusicPlayerSheet.this.savedMusicList;
        }
    }
    public class AnonymousClass7 extends AudioPlayerAlert.ClippingTextViewSwitcher {
        final /* synthetic */ Context val$context;

        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public AnonymousClass7(Context context, Context context2) {
            super(context);
            this.val$context = context2;
        }

        @Override
        public TextView createTextView() {
            final TextView textView = new TextView(this.val$context);
            textView.setTextSize(1, 15.0f);
            textView.setTypeface(AndroidUtilities.regular());
            textView.setPadding(AndroidUtilities.dp(6.0f), 0, AndroidUtilities.dp(6.0f), 0);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity(19);
            textView.setTextColor(MusicPlayerSheet.this.colors.textSecondary);
            textView.setOnClickListener(view -> lambda_createTextView_0(textView, view));
            return textView;
        }
        public /* synthetic */ void lambda_createTextView_0(TextView textView, View view) {
            CharSequence text = textView.getText();
            if (TextUtils.isEmpty(text)) {
                return;
            }
            MusicPlayerSheet.this.actions.searchArtist(text.toString());
        }
    }
    public /* synthetic */ void lambda_new_3(View view) {
        toggleProfile();
    }
    public /* synthetic */ void lambda_new_4(View view) {
        boolean z = this.showRemaining;
        this.showRemaining = !z;
        this.durationView.setContentDescription(LocaleController.getString(!z ? R.string.PlayerShowDuration : R.string.PlayerShowRemaining));
        this.lastTime = -1;
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessageObject != null) {
            updateProgress(playingMessageObject);
        }
    }
    public /* synthetic */ void lambda_new_5(View view) {
        MediaController.getInstance().setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2);
        updateOrderButtons(true);
        this.queueView.update();
    }
    public /* synthetic */ boolean lambda_new_6(View view) {
        showOrderMenu(view);
        return true;
    }
    public /* synthetic */ void lambda_new_7(View view) {
        SharedConfig.setRepeatMode((SharedConfig.repeatMode + 1) % 3);
        updateOrderButtons(true);
    }
    public /* synthetic */ boolean lambda_new_8(View view) {
        showOrderMenu(view);
        return true;
    }
    public /* synthetic */ void lambda_new_9(View view) {
        cycleSpeed();
    }
    public /* synthetic */ boolean lambda_new_10(View view) {
        showSpeedMenu(view);
        return true;
    }
    public /* synthetic */ void lambda_new_11(View view) {
        selectMode(this.mode == 1 ? 0 : 1);
    }
    public /* synthetic */ void lambda_new_12(View view) {
        selectMode(this.mode == 2 ? 0 : 2);
    }
    public /* synthetic */ void lambda_new_13(Float f) {
        this.seekPlate = f.floatValue();
        this.playerLayout.invalidate();
    }
    public /* synthetic */ void lambda_new_14(DynamicAnimation dynamicAnimation, boolean z, float f, float f2) {
        if (z || f > 0.01f || this.rewindingState != 0) {
            return;
        }
        this.seekPlate = 0.0f;
        this.seekSpeedDrawable.setShown(false, false);
    }
    public /* synthetic */ void lambda_new_15(Float f) {
        this.modeProgress = f.floatValue();
    }
    public /* synthetic */ void lambda_new_16(Float f) {
        this.lyricsAlpha = Utilities.clamp01(f.floatValue());
    }
    public /* synthetic */ void lambda_new_17(Float f) {
        this.queueAlpha = Utilities.clamp01(f.floatValue());
    }
    public /* synthetic */ void lambda_new_18(Float f) {
        this.seekBar.setBufferedProgress(f.floatValue() / 1000.0f);
    }

    private ExpressiveStateDrawable createModeSegment(int i) {
        return new ExpressiveStateDrawable(24.0f).setInnerCorners(i, 8.0f).setAlwaysShowContainer(true);
    }
    public void onModeSegmentsChanged() {
        applyModeSegment(this.lyricsButton, this.lyricsBackground, this.colors);
        applyModeSegment(this.queueButton, this.queueBackground, this.colors);
        this.playerLayout.layoutModeSegments();
    }

    private static void applyModeSegment(ImageView imageView, ExpressiveStateDrawable expressiveStateDrawable, PlayerColors playerColors) {
        int iBlendARGB = ColorUtils.blendARGB(playerColors.icon, playerColors.onAccent, expressiveStateDrawable.getSelectProgress());
        setIconTint(imageView, expressiveStateDrawable, iBlendARGB);
        expressiveStateDrawable.setColors(iBlendARGB, playerColors.surfaceContainer).setSelectedContainerColor(playerColors.accent);
    }

    private ImageView createIconButton(Context context, int i, ExpressiveStateDrawable expressiveStateDrawable) {
        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        if (i != 0) {
            imageView.setImageResource(i);
        }
        imageView.setBackground(expressiveStateDrawable);
        return imageView;
    }

    private SpringAnimation createSpring(float f, float f2, final Utilities.Callback<Float> callback) {
        return Material3Tokens.createSpring(f, f2, 0.002f, obj -> lambda_createSpring_19(callback, (Float) obj));
    }
    public /* synthetic */ void lambda_createSpring_19(Utilities.Callback callback, Float f) {
        callback.run(f);
        applyTransitions();
    }

    private void setupCast(Context context) {
        CastMediaRouteButton castMediaRouteButton = new CastMediaRouteButton(context) {
            @Override
            public void stateUpdated(boolean z) {
                MusicPlayerSheet.this.moreIcon.setCasting(CastSync.isActive(), true);
            }
        };
        this.castButton = castMediaRouteButton;
        this.castAvailable = true;
        // трансляция на Chromecast требует Google Play Services, которых в DevGram нет
        this.castAvailable = false;
        this.castButton.setVisibility(4);
        this.moreIcon.setCasting(CastSync.isActive(), false);
    }
    public MessageObject getNeighborMessage(int i) {
        if (SharedConfig.shuffleMusic) {
            return null;
        }
        MediaController mediaController = MediaController.getInstance();
        ArrayList<MessageObject> playlist = mediaController.getPlaylist();
        int size = playlist.size();
        int playingMessageObjectNum = mediaController.getPlayingMessageObjectNum();
        if (size < 2 || playingMessageObjectNum < 0 || playingMessageObjectNum >= size) {
            return null;
        }
        int i2 = 0;
        int i3 = playingMessageObjectNum + ((i > 0) != SharedConfig.playOrderReversed ? -1 : 1);
        if (i3 < size) {
            i2 = i3 < 0 ? size - 1 : i3;
        }
        return playlist.get(i2);
    }

    private void showSeekPlate(boolean z) {
        if (z) {
            this.seekSpeedDrawable.setShown(true, false);
        }
        Material3Tokens.animate(this.seekPlateSpring, this.seekPlate, z ? 1.0f : 0.0f);
    }
    public boolean isMyList() {
        MessagesController.SavedMusicList savedMusicList = this.savedMusicList;
        return savedMusicList != null && savedMusicList.dialogId == UserConfig.getInstance(this.currentAccount).getClientUserId();
    }

    @Override
    public FrameLayout getBulletinContainer() {
        return this.playerLayout;
    }

    @Override
    public Theme.ResourcesProvider getBulletinResourcesProvider() {
        return getThemedResourcesProvider();
    }

    @Override
    public Theme.ResourcesProvider getSheetResourcesProvider() {
        PlayerColors playerColors = this.targetColors;
        if (playerColors == null) {
            playerColors = this.colors;
        }
        return playerColors.wrapSettings(this.baseResourcesProvider);
    }

    private Theme.ResourcesProvider getThemedResourcesProvider() {
        return this.colors.wrap(this.baseResourcesProvider);
    }

    @Override
    public MessagesController.SavedMusicList getSavedMusicList() {
        return this.savedMusicList;
    }

    @Override
    public void dismissPlayer() {
        dismiss();
    }

    private BulletinFactory bulletins() {
        return BulletinFactory.of(this.playerLayout, getThemedResourcesProvider());
    }
    public void togglePlay() {
        MediaController mediaController = MediaController.getInstance();
        MessageObject playingMessageObject = mediaController.getPlayingMessageObject();
        if (playingMessageObject == null || mediaController.isDownloadingCurrentMessage()) {
            return;
        }
        if (mediaController.isMessagePaused()) {
            mediaController.playMessage(playingMessageObject);
        } else {
            mediaController.pauseMessage(playingMessageObject);
        }
    }
    public float getRewindMultiplier() {
        int i = this.rewindingState;
        int i2 = this.rewindPressedCount;
        if (i < 0) {
            if (i2 <= 1) {
                return 3.0f;
            }
            return i2 == 2 ? 6.0f : 12.0f;
        }
        if (i2 <= 1) {
            return 4.0f;
        }
        return i2 == 2 ? 7.0f : 13.0f;
    }
    public void onRewindHold(int i, boolean z) {
        MediaController mediaController = MediaController.getInstance();
        MessageObject playingMessageObject = mediaController.getPlayingMessageObject();
        if (z) {
            if (playingMessageObject == null || this.seekBar.isDragging()) {
                return;
            }
            this.rewindingState = i;
            this.rewindPressedCount = 1;
            this.lastRewindingTime = SystemClock.elapsedRealtime();
            this.lastUpdateRewindingPlayerTime = 0L;
            if (i < 0) {
                this.rewindingProgress = playingMessageObject.audioProgress;
                AndroidUtilities.runOnUIThread(this.rewindSeek);
            } else if (mediaController.isMessagePaused()) {
                startForwardRewindingSeek();
            } else {
                mediaController.setRewindPlaybackSpeed(getRewindMultiplier());
            }
            this.seekSpeedDrawable.setSpeed(i * getRewindMultiplier(), false);
            showSeekPlate(true);
            AndroidUtilities.runOnUIThread(this.rewindStepRunnable, 2000L);
            this.playerLayout.invalidate();
            return;
        }
        if (this.rewindingState == 0) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(this.rewindStepRunnable);
        AndroidUtilities.cancelRunOnUIThread(this.rewindSeek);
        if (this.rewindingState < 0) {
            this.lastUpdateRewindingPlayerTime = 0L;
            this.rewindSeek.run();
            mediaController.resumeByRewind();
        } else {
            boolean z2 = getRewindMultiplier() >= 6.0f;
            mediaController.restoreMusicPlaybackSpeed();
            if (mediaController.isMessagePaused()) {
                this.lastUpdateRewindingPlayerTime = 0L;
                this.rewindSeek.run();
            } else if (z2 && playingMessageObject != null) {
                mediaController.seekToProgress(playingMessageObject, playingMessageObject.audioProgress);
            }
        }
        this.rewindingState = 0;
        this.rewindingProgress = -1.0f;
        this.rewindPressedCount = 0;
        showSeekPlate(false);
        if (playingMessageObject != null) {
            updateProgress(playingMessageObject);
        }
    }

    private void startForwardRewindingSeek() {
        MessageObject playingMessageObject;
        if (this.rewindingState != 1 || (playingMessageObject = MediaController.getInstance().getPlayingMessageObject()) == null) {
            return;
        }
        this.lastRewindingTime = SystemClock.elapsedRealtime();
        this.rewindingProgress = playingMessageObject.audioProgress;
        AndroidUtilities.cancelRunOnUIThread(this.rewindSeek);
        AndroidUtilities.runOnUIThread(this.rewindSeek);
    }

    private void cycleSpeed() {
        float[] fArr;
        MediaController mediaController = MediaController.getInstance();
        float playbackSpeed = mediaController.getPlaybackSpeed(true);
        int i = 0;
        while (true) {
            fArr = TOGGLE_SPEEDS;
            if (i >= fArr.length) {
                i = -1;
                break;
            } else if (playbackSpeed - 0.1f <= fArr[i]) {
                break;
            } else {
                i++;
            }
        }
        int i2 = i + 1;
        mediaController.setPlaybackSpeed(true, fArr[i2 < fArr.length ? i2 : 0]);
        checkSpeedHint();
    }

    private void checkSpeedHint() {
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        if (jElapsedRealtime - this.lastSpeedClick > 300) {
            int i = MessagesController.getGlobalNotificationsSettings().getInt("speedhint", 0) + 1;
            if (i > 2) {
                i = -10;
            }
            MessagesController.getGlobalNotificationsSettings().edit().putInt("speedhint", i).apply();
            if (i >= 0) {
                showSpeedHint();
            }
        }
        this.lastSpeedClick = jElapsedRealtime;
    }

    private void showSpeedHint() {
        if (this.speedHintView != null || this.speedButton.getWidth() == 0) {
            return;
        }
        final HintView2 hintView2 = new HintView2(getContext(), 3);
        this.speedHintView = hintView2;
        hintView2.setMultilineText(true);
        hintView2.setText(LocaleController.getString(R.string.SpeedHint));
        hintView2.setBgColor(this.colors.text);
        hintView2.setTextColor(this.colors.background);
        hintView2.setPadding(AndroidUtilities.dp(20.0f), 0, AndroidUtilities.dp(20.0f), 0);
        hintView2.setJointPx(0.0f, AndroidUtilities.dp(64.0f) / 2.0f);
        hintView2.setDuration(3500L);
        hintView2.setOnHiddenListener(() -> lambda_showSpeedHint_20(hintView2));
        this.playerLayout.addView(hintView2);
        hintView2.show();
    }
    public /* synthetic */ void lambda_showSpeedHint_20(HintView2 hintView2) {
        AndroidUtilities.removeFromParent(hintView2);
        if (this.speedHintView == hintView2) {
            this.speedHintView = null;
        }
    }

    private ItemOptions makeMenu(View view, boolean z, boolean z2) {
        return ItemOptions.makeOptions(this.container, getThemedResourcesProvider(), view, z, z2).setDimAlpha(102);
    }

    private Rect getMenuPadding() {
        Rect rect = new Rect();
        Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert4);
        if (drawable != null) {
            drawable.getPadding(rect);
        }
        return rect;
    }

    private Drawable createScrimBackground(View view) {
        if (view.getBackground() instanceof ExpressiveStateDrawable) {
            return ((ExpressiveStateDrawable) view.getBackground()).createShapeFill(this.colors.background);
        }
        return null;
    }

    private void alignMenu(ItemOptions itemOptions, View view) {
        boolean z = view.getX() + (((float) view.getWidth()) / 2.0f) > ((float) this.playerLayout.getWidth()) / 2.0f;
        itemOptions.setGravity(z ? 5 : 3);
        if (z) {
            return;
        }
        itemOptions.translate(-getMenuPadding().left, 0.0f);
    }
    public int getSpeedSliderColor() {
        return ColorTones.withTone(this.colors.accent, 40.0f, 64.0d);
    }

    private void showSpeedMenu(View view) {
        float playbackSpeed = MediaController.getInstance().getPlaybackSpeed(true);
        int i = 0;
        ItemOptions itemOptionsMakeMenu = makeMenu(view, false, true);
        final ActionBarMenuSlider.SpeedSlider speedSlider = new ActionBarMenuSlider.SpeedSlider(getContext(), getThemedResourcesProvider()) {
            @Override
            public int getColorValue(float f) {
                return MusicPlayerSheet.this.getSpeedSliderColor();
            }
        };
        speedSlider.setRoundRadiusDp(12.0f);
        speedSlider.setDrawShadow(false);
        speedSlider.setSpeed(playbackSpeed, false);
        speedSlider.setOnValueChange((obj, obj2) -> MusicPlayerSheet.$r8$lambda$fZhRTKMykk7j4m3KcCB8zY0NCQ4(speedSlider, (Float) obj, (Boolean) obj2));
        itemOptionsMakeMenu.addView(speedSlider, LayoutHelper.createLinear(-1, 44));
        itemOptionsMakeMenu.addSpaceGap();
        while (true) {
            float[] fArr = SPEEDS;
            if (i < fArr.length) {
                final float f = fArr[i];
                itemOptionsMakeMenu.add(SPEED_ICONS[i], LocaleController.getString(SPEED_NAMES[i]), () -> MediaController.getInstance().setPlaybackSpeed(true, f));
                if (Math.abs(playbackSpeed - f) < 0.05f && itemOptionsMakeMenu.getLast() != null) {
                    int i2 = this.colors.accent;
                    itemOptionsMakeMenu.getLast().setColors(i2, i2);
                }
                i++;
            } else {
                MessagesController.getGlobalNotificationsSettings().edit().putInt("speedhint", -15).apply();
                itemOptionsMakeMenu.addGap();
                itemOptionsMakeMenu.addChecked(PlayerConfig.getPlayerSpeedPitch(), R.drawable.msg_tone_on, LocaleController.getString(R.string.PlayerSpeedPitch), () -> MusicPlayerSheet.m1661$r8$lambda$VX0d1TsNedQwl9gjC0yyQf4es());
                matchFirstCardWidth(itemOptionsMakeMenu, speedSlider);
                alignMenu(itemOptionsMakeMenu, view);
                itemOptionsMakeMenu.setScrimViewBackground(createScrimBackground(view));
                itemOptionsMakeMenu.setDismissOnMoveOutside(true);
                itemOptionsMakeMenu.setOnDismiss(() -> lambda_showSpeedMenu_24());
                this.speedMenuShown = true;
                applySpeedColors(this.colors);
                itemOptionsMakeMenu.show();
                return;
            }
        }
    }

    public static /* synthetic */ void $r8$lambda$fZhRTKMykk7j4m3KcCB8zY0NCQ4(ActionBarMenuSlider.SpeedSlider speedSlider, Float f, Boolean bool) {
        float fClamp01 = Utilities.clamp01(f.floatValue());
        if (Math.abs(speedSlider.getValue() - fClamp01) > 0.001f) {
            speedSlider.setValue(fClamp01, true);
        }
        MediaController.getInstance().setPlaybackSpeed(true, speedSlider.getSpeed(fClamp01));
    }
    public static /* synthetic */ void m1661$r8$lambda$VX0d1TsNedQwl9gjC0yyQf4es() {
        PlayerConfig.setPlayerSpeedPitch(!PlayerConfig.getPlayerSpeedPitch());
        MediaController.getInstance().restoreMusicPlaybackSpeed();
    }
    public /* synthetic */ void lambda_showSpeedMenu_24() {
        this.speedMenuShown = false;
        applySpeedColors(this.colors);
    }

    private void matchFirstCardWidth(ItemOptions itemOptions, View view) {
        ViewGroup layout = itemOptions.getLayout();
        if (layout == null || layout.getChildCount() < 2 || view.getLayoutParams() == null) {
            return;
        }
        int iMax = 0;
        for (int i = 1; i < layout.getChildCount(); i++) {
            View childAt = layout.getChildAt(i);
            childAt.measure(View.MeasureSpec.makeMeasureSpec(this.container.getWidth(), Integer.MIN_VALUE), View.MeasureSpec.makeMeasureSpec(this.container.getHeight(), Integer.MIN_VALUE));
            iMax = Math.max(iMax, (childAt.getMeasuredWidth() - childAt.getPaddingLeft()) - childAt.getPaddingRight());
        }
        view.getLayoutParams().width = Math.max(AndroidUtilities.dp(120.0f), iMax);
    }

    private void showOrderMenu(View view) {
        ItemOptions itemOptionsMakeMenu = makeMenu(view, false, true);
        itemOptionsMakeMenu.addChecked(SharedConfig.repeatMode == 2, R.drawable.player_new_repeatone, LocaleController.getString(R.string.RepeatSong), () -> lambda_showOrderMenu_25());
        itemOptionsMakeMenu.addChecked(SharedConfig.repeatMode == 1, R.drawable.player_new_repeatall, LocaleController.getString(R.string.RepeatList), () -> lambda_showOrderMenu_26());
        itemOptionsMakeMenu.addGap();
        itemOptionsMakeMenu.addChecked(SharedConfig.shuffleMusic, R.drawable.player_new_shuffle, LocaleController.getString(R.string.ShuffleList), () -> lambda_showOrderMenu_27());
        itemOptionsMakeMenu.addGap();
        itemOptionsMakeMenu.addChecked(SharedConfig.playOrderReversed, R.drawable.player_new_order, LocaleController.getString(R.string.ReverseOrder), () -> lambda_showOrderMenu_28());
        alignMenu(itemOptionsMakeMenu, view);
        itemOptionsMakeMenu.setScrimViewBackground(createScrimBackground(view));
        itemOptionsMakeMenu.setDismissOnMoveOutside(true);
        itemOptionsMakeMenu.show();
    }
    public /* synthetic */ void lambda_showOrderMenu_25() {
        SharedConfig.setRepeatMode(SharedConfig.repeatMode == 2 ? 0 : 2);
        updateOrderButtons(true);
    }
    public /* synthetic */ void lambda_showOrderMenu_26() {
        SharedConfig.setRepeatMode(SharedConfig.repeatMode == 1 ? 0 : 1);
        updateOrderButtons(true);
    }
    public /* synthetic */ void lambda_showOrderMenu_27() {
        MediaController.getInstance().setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2);
        updateOrderButtons(true);
        this.queueView.update();
    }
    public /* synthetic */ void lambda_showOrderMenu_28() {
        MediaController.getInstance().setPlaybackOrderType(!SharedConfig.playOrderReversed ? 1 : 0);
        updateOrderButtons(true);
        this.queueView.update();
    }

    private void showMenu() {
        final MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessageObject == null) {
            return;
        }
        boolean zIsServiceTrack = PlayerActions.isServiceTrack(playingMessageObject);
        final ItemOptions itemOptionsMakeMenu = makeMenu(this.moreButton, true, false);
        if (!zIsServiceTrack) {
            if (!isMyList() && !this.noforwards) {
                final ItemOptions itemOptionsBuildSaveOptions = buildSaveOptions(itemOptionsMakeMenu, playingMessageObject);
                itemOptionsMakeMenu.add(R.drawable.msg_stories_save, LocaleController.getString(R.string.AudioSaveTo), () -> itemOptionsMakeMenu.openSwipeback(itemOptionsBuildSaveOptions));
                if (itemOptionsMakeMenu.getLast() != null) {
                    itemOptionsMakeMenu.getLast().setRightIcon(R.drawable.msg_arrowright);
                }
                itemOptionsMakeMenu.addGap();
            }
            addForwardAndShare(itemOptionsMakeMenu, playingMessageObject);
            itemOptionsMakeMenu.addIf(isMyList() && !this.noforwards, R.drawable.menu_download_round, LocaleController.getString(R.string.SaveToMusic), () -> lambda_showMenu_30(itemOptionsMakeMenu, playingMessageObject));
            itemOptionsMakeMenu.addIf(playingMessageObject.getId() > 0, R.drawable.msg_message, LocaleController.getString(R.string.ShowInChat), () -> lambda_showMenu_31(itemOptionsMakeMenu, playingMessageObject));
            if (this.castAvailable) {
                ActionBarMenuSubItem actionBarMenuSubItemAdd = itemOptionsMakeMenu.add();
                actionBarMenuSubItemAdd.setTextAndIcon(LocaleController.getString(R.string.VideoPlayerChromecast), R.drawable.menu_video_chromecast);
                actionBarMenuSubItemAdd.setOnClickListener(view -> lambda_showMenu_32(itemOptionsMakeMenu, view));
                AndroidUtilities.removeFromParent(this.castButton);
                actionBarMenuSubItemAdd.addView(this.castButton, 0, LayoutHelper.createFrame(-1, -1.0f));
                actionBarMenuSubItemAdd.setEnabledByColor(this.castButton.isConnected(), Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, getThemedResourcesProvider()), Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, getThemedResourcesProvider()), this.colors.accent);
            }
            itemOptionsMakeMenu.addIf(isMyList(), R.drawable.msg_delete, (CharSequence) LocaleController.getString(R.string.ProfilePlaylistRemoveFromProfile), true, () -> lambda_showMenu_33(itemOptionsMakeMenu, playingMessageObject));
            itemOptionsMakeMenu.addGap();
        }
        if (!zIsServiceTrack) {
            final ItemOptions itemOptionsBuildDetailsOptions = buildDetailsOptions(itemOptionsMakeMenu, playingMessageObject);
            itemOptionsMakeMenu.add(R.drawable.msg_info, LocaleController.getString(R.string.Details), () -> itemOptionsMakeMenu.openSwipeback(itemOptionsBuildDetailsOptions));
            if (itemOptionsMakeMenu.getLast() != null) {
                itemOptionsMakeMenu.getLast().setRightIcon(R.drawable.msg_arrowright);
            }
        }
        itemOptionsMakeMenu.add(R.drawable.msg_settings, LocaleController.getString(R.string.PlayerSettings), () -> lambda_showMenu_35(itemOptionsMakeMenu));
        itemOptionsMakeMenu.setGravity(5);
        itemOptionsMakeMenu.setSwipebackGravity(false, false);
        ViewGroup layout = itemOptionsMakeMenu.getLayout();
        if (layout instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
            ActionBarPopupWindow.ActionBarPopupWindowLayout actionBarPopupWindowLayout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) layout;
            if (actionBarPopupWindowLayout.getSwipeBack() != null) {
                actionBarPopupWindowLayout.getSwipeBack().setStickToRight(true);
            }
        }
        itemOptionsMakeMenu.setOnTopOfScrim();
        itemOptionsMakeMenu.translate(0.0f, -getMenuPadding().top);
        itemOptionsMakeMenu.show();
    }
    public /* synthetic */ void lambda_showMenu_30(ItemOptions itemOptions, MessageObject messageObject) {
        itemOptions.dismiss();
        this.actions.saveToMusic(messageObject);
    }
    public /* synthetic */ void lambda_showMenu_31(ItemOptions itemOptions, MessageObject messageObject) {
        itemOptions.dismiss();
        this.actions.showInChat(messageObject);
    }
    public /* synthetic */ void lambda_showMenu_32(ItemOptions itemOptions, View view) {
        itemOptions.dismiss();
    }
    public /* synthetic */ void lambda_showMenu_33(ItemOptions itemOptions, MessageObject messageObject) {
        itemOptions.dismiss();
        this.actions.removeFromMyList(messageObject, () -> onMyListChanged());
    }
    public /* synthetic */ void lambda_showMenu_35(ItemOptions itemOptions) {
        itemOptions.dismiss();
        openSettings();
    }

    private ItemOptions buildDetailsOptions(ItemOptions itemOptions, MessageObject messageObject) {
        ItemOptions itemOptionsMakeSwipeback = itemOptions.makeSwipeback();
        itemOptionsMakeSwipeback.add(R.drawable.ic_ab_back, LocaleController.getString(R.string.Back), itemOptions::closeSwipeback);
        itemOptionsMakeSwipeback.addGap();
        TrackQuality.Info info = TrackQuality.get(messageObject);
        File localFile = PlayerActions.getLocalFile(messageObject);
        String absolutePath = null;
        final ActionBarMenuSubItem actionBarMenuSubItemAddDetail = addDetail(itemOptions, itemOptionsMakeSwipeback, R.drawable.msg_media, LocaleController.getString(R.string.PlayerInfoFormat), info != null ? info.format : null);
        final ActionBarMenuSubItem actionBarMenuSubItemAddDetail2 = addDetail(itemOptions, itemOptionsMakeSwipeback, R.drawable.msg_noise_on, LocaleController.getString(R.string.Bitrate), info != null ? info.formatBitrate() : null);
        final ActionBarMenuSubItem actionBarMenuSubItemAddDetail3 = addDetail(itemOptions, itemOptionsMakeSwipeback, R.drawable.msg_tone_on, LocaleController.getString(R.string.PlayerInfoSampleRate), info != null ? info.formatSampleRate() : null);
        final ActionBarMenuSubItem actionBarMenuSubItemAddDetail4 = addDetail(itemOptions, itemOptionsMakeSwipeback, R.drawable.menu_quality_hd, LocaleController.getString(R.string.PlayerInfoBitDepth), info != null ? info.formatBitDepth() : null);
        long size = messageObject.getSize();
        addDetail(itemOptions, itemOptionsMakeSwipeback, R.drawable.msg_sendfile, LocaleController.getString(R.string.FileSize), size > 0 ? AndroidUtilities.formatFileSize(size) : null);
        int duration = (int) messageObject.getDuration();
        addDetail(itemOptions, itemOptionsMakeSwipeback, R.drawable.msg2_animations, LocaleController.getString(R.string.Duration), duration > 0 ? AndroidUtilities.formatShortDuration(duration) : null);
        addDetail(itemOptions, itemOptionsMakeSwipeback, R.drawable.msg_log, LocaleController.getString(R.string.FileName), FileLoader.getDocumentFileName(messageObject.getDocument()));
        int i = R.drawable.msg_map;
        String string = LocaleController.getString(R.string.FilePath);
        if (localFile != null && localFile.exists()) {
            absolutePath = localFile.getAbsolutePath();
        }
        addDetail(itemOptions, itemOptionsMakeSwipeback, i, string, absolutePath);
        if (info != null && !info.probed) {
            TrackQuality.load(messageObject, obj -> lambda_buildDetailsOptions_36(actionBarMenuSubItemAddDetail, actionBarMenuSubItemAddDetail2, actionBarMenuSubItemAddDetail3, actionBarMenuSubItemAddDetail4, (TrackQuality.Info) obj));
        }
        return itemOptionsMakeSwipeback;
    }
    public /* synthetic */ void lambda_buildDetailsOptions_36(ActionBarMenuSubItem actionBarMenuSubItem, ActionBarMenuSubItem actionBarMenuSubItem2, ActionBarMenuSubItem actionBarMenuSubItem3, ActionBarMenuSubItem actionBarMenuSubItem4, TrackQuality.Info info) {
        setDetail(actionBarMenuSubItem, info.format);
        setDetail(actionBarMenuSubItem2, info.formatBitrate());
        setDetail(actionBarMenuSubItem3, info.formatSampleRate());
        setDetail(actionBarMenuSubItem4, info.formatBitDepth());
    }

    private ActionBarMenuSubItem addDetail(final ItemOptions itemOptions, ItemOptions itemOptions2, int i, String str, String str2) {
        itemOptions2.add(i, str, (Runnable) null);
        final ActionBarMenuSubItem last = itemOptions2.getLast();
        if (last == null) {
            return null;
        }
        last.setMinimumWidth(AndroidUtilities.dp(196.0f));
        last.setOnClickListener(view -> lambda_addDetail_37(itemOptions, last, view));
        last.setOnLongClickListener(view -> lambda_addDetail_38(itemOptions, last, view));
        setDetail(last, str2);
        return last;
    }
    public /* synthetic */ void lambda_addDetail_37(ItemOptions itemOptions, ActionBarMenuSubItem actionBarMenuSubItem, View view) {
        copyDetail(itemOptions, actionBarMenuSubItem);
    }
    public /* synthetic */ boolean lambda_addDetail_38(ItemOptions itemOptions, ActionBarMenuSubItem actionBarMenuSubItem, View view) {
        copyDetail(itemOptions, actionBarMenuSubItem);
        return true;
    }

    private void setDetail(ActionBarMenuSubItem actionBarMenuSubItem, String str) {
        if (actionBarMenuSubItem == null) {
            return;
        }
        actionBarMenuSubItem.setVisibility(TextUtils.isEmpty(str) ? 8 : 0);
        if (TextUtils.isEmpty(str)) {
            return;
        }
        actionBarMenuSubItem.setSubtext(str);
        actionBarMenuSubItem.subtextView.setMaxWidth(AndroidUtilities.dp(220.0f));
        actionBarMenuSubItem.subtextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        actionBarMenuSubItem.subtextView.setMarqueeRepeatLimit(-1);
        actionBarMenuSubItem.subtextView.setSelected(true);
        actionBarMenuSubItem.setItemHeight(56);
    }

    private void copyDetail(ItemOptions itemOptions, ActionBarMenuSubItem actionBarMenuSubItem) {
        TextView textView = actionBarMenuSubItem.subtextView;
        if (textView == null || TextUtils.isEmpty(textView.getText())) {
            return;
        }
        itemOptions.dismiss();
        if (AndroidUtilities.addToClipboard(actionBarMenuSubItem.subtextView.getText()) && AndroidUtilities.shouldShowClipboardToast()) {
            bulletins().createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
        }
    }

    private void addForwardAndShare(final ItemOptions itemOptions, final MessageObject messageObject) {
        if (this.noforwards) {
            return;
        }
        itemOptions.add(R.drawable.msg_forward, LocaleController.getString(R.string.Forward), () -> lambda_addForwardAndShare_39(itemOptions, messageObject));
        ActionBarMenuSubItem last = itemOptions.getLast();
        if (last != null) {
            last.setOnLongClickListener(view -> lambda_addForwardAndShare_40(itemOptions, messageObject, view));
        }
        itemOptions.add(R.drawable.msg_shareout, LocaleController.getString(R.string.ShareFile), () -> lambda_addForwardAndShare_41(itemOptions, messageObject));
    }
    public /* synthetic */ void lambda_addForwardAndShare_39(ItemOptions itemOptions, MessageObject messageObject) {
        itemOptions.dismiss();
        this.actions.forward(messageObject, false);
    }
    public /* synthetic */ boolean lambda_addForwardAndShare_40(ItemOptions itemOptions, MessageObject messageObject, View view) {
        itemOptions.dismiss();
        this.actions.forward(messageObject, true);
        return true;
    }
    public /* synthetic */ void lambda_addForwardAndShare_41(ItemOptions itemOptions, MessageObject messageObject) {
        itemOptions.dismiss();
        this.actions.share(messageObject);
    }

    private void openSettings() {
        BaseFragment safeLastFragment = LaunchActivity.getSafeLastFragment();
        if (safeLastFragment == null) {
            return;
        }
        final PlayerSettingsSheet playerSettingsSheet = new PlayerSettingsSheet(getContext(), safeLastFragment, getSheetResourcesProvider(), new PlayerSettingsSheet.Delegate() {
            @Override
            public void onCoverColorsChanged() {
                if (PlayerConfig.getPlayerCoverColors() && MusicPlayerSheet.this.coverView.hasBitmap()) {
                    MusicPlayerSheet musicPlayerSheet = MusicPlayerSheet.this;
                    musicPlayerSheet.onCoverBitmapLoaded(musicPlayerSheet.coverView.getMessageObject(), MusicPlayerSheet.this.coverView.getImageReceiver());
                }
                MusicPlayerSheet.this.updateCoverColors(true);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.playerColorsChanged, new Object[0]);
            }

            @Override
            public void onMotionCoversChanged() {
                MusicPlayerSheet.this.coverView.setMotionCover(MusicPlayerSheet.this.coverView.getMessageObject(), null);
                MusicPlayerSheet musicPlayerSheet = MusicPlayerSheet.this;
                musicPlayerSheet.loadMotionCover(musicPlayerSheet.coverView.getMessageObject());
            }

            @Override
            public void onRhythmBackgroundChanged() {
                MusicPlayerSheet.this.updateRhythm();
            }

            @Override
            public void onLyricsOnlineChanged() {
                MusicPlayerSheet.this.lyricsKey = null;
                if (MusicPlayerSheet.this.mode == 1) {
                    MusicPlayerSheet.this.loadLyrics(false);
                }
            }

            @Override
            public void onLyricsProgressChanged() {
                MusicPlayerSheet.this.lyricsView.setProgressMode(PlayerConfig.getLyricsProgress());
            }
        });
        playerSettingsSheet.setOnHideListener(dialogInterface -> lambda_openSettings_42(playerSettingsSheet, dialogInterface));
        this.settingsSheet = playerSettingsSheet;
        playerSettingsSheet.show();
    }
    public /* synthetic */ void lambda_openSettings_42(PlayerSettingsSheet playerSettingsSheet, DialogInterface dialogInterface) {
        if (this.settingsSheet == playerSettingsSheet) {
            this.settingsSheet = null;
        }
    }

    private ItemOptions buildSaveOptions(final ItemOptions itemOptions, final MessageObject messageObject) {
        ItemOptions itemOptionsMakeSwipeback = itemOptions.makeSwipeback();
        itemOptionsMakeSwipeback.add(R.drawable.ic_ab_back, LocaleController.getString(R.string.Back), itemOptions::closeSwipeback);
        itemOptionsMakeSwipeback.addGap();
        itemOptionsMakeSwipeback.addIf(!this.actions.isInProfile(messageObject), R.drawable.left_status_profile, LocaleController.getString(R.string.AudioSaveToMyProfile), () -> lambda_buildSaveOptions_44(messageObject, itemOptions));
        itemOptionsMakeSwipeback.add(R.drawable.msg_saved, LocaleController.getString(R.string.AudioSaveToSavedMessages), () -> lambda_buildSaveOptions_45(messageObject, itemOptions));
        itemOptionsMakeSwipeback.add(R.drawable.menu_download_round, LocaleController.getString(R.string.AudioSaveToMusicFolder), () -> lambda_buildSaveOptions_46(messageObject, itemOptions));
        itemOptionsMakeSwipeback.addGap();
        itemOptionsMakeSwipeback.addText(LocaleController.getString(R.string.AudioSaveToInfo), 12, AndroidUtilities.dp(200.0f));
        return itemOptionsMakeSwipeback;
    }
    public /* synthetic */ void lambda_buildSaveOptions_44(MessageObject messageObject, final ItemOptions itemOptions) {
        this.actions.setInProfile(messageObject, true, () -> lambda_buildSaveOptions_43(itemOptions));
    }
    public /* synthetic */ void lambda_buildSaveOptions_43(ItemOptions itemOptions) {
        updateProfileState(true);
        bulletins().createSimpleBulletin(R.raw.saved_messages, LocaleController.getString(R.string.AudioSaveToMyProfileSaved)).show();
        itemOptions.dismiss();
    }
    public /* synthetic */ void lambda_buildSaveOptions_45(MessageObject messageObject, ItemOptions itemOptions) {
        this.actions.forwardTo(messageObject, UserConfig.getInstance(this.currentAccount).getClientUserId(), false);
        itemOptions.dismiss();
        bulletins().createSimpleBulletin(R.raw.saved_messages, LocaleController.getString(R.string.AudioSaveToSavedMessagesSaved)).show();
    }
    public /* synthetic */ void lambda_buildSaveOptions_46(MessageObject messageObject, ItemOptions itemOptions) {
        this.actions.saveToMusic(messageObject);
        itemOptions.dismiss();
    }
    public void showTrackOptions(PlayerQueueCell playerQueueCell, final MessageObject messageObject) {
        final ItemOptions itemOptionsMakeMenu = makeMenu(playerQueueCell, true, false);
        if (isMyList()) {
            addForwardAndShare(itemOptionsMakeMenu, messageObject);
            itemOptionsMakeMenu.addIf(!this.noforwards, R.drawable.menu_download_round, LocaleController.getString(R.string.SaveToMusic), () -> lambda_showTrackOptions_47(itemOptionsMakeMenu, messageObject));
            itemOptionsMakeMenu.add(R.drawable.msg_delete, (CharSequence) LocaleController.getString(R.string.Delete), true, () -> lambda_showTrackOptions_49(messageObject, itemOptionsMakeMenu));
        } else {
            final ItemOptions itemOptionsBuildSaveOptions = buildSaveOptions(itemOptionsMakeMenu, messageObject);
            itemOptionsMakeMenu.addIf(!this.noforwards, R.drawable.msg_stories_save, LocaleController.getString(R.string.AudioSaveTo), () -> itemOptionsMakeMenu.openSwipeback(itemOptionsBuildSaveOptions));
            if (!this.noforwards && itemOptionsMakeMenu.getLast() != null) {
                itemOptionsMakeMenu.getLast().setRightIcon(R.drawable.msg_arrowright);
            }
            itemOptionsMakeMenu.addGap();
            addForwardAndShare(itemOptionsMakeMenu, messageObject);
            itemOptionsMakeMenu.addIf(messageObject.getId() > 0, R.drawable.msg_view_file, LocaleController.getString(R.string.ShowInChat), () -> lambda_showTrackOptions_51(itemOptionsMakeMenu, messageObject));
        }
        itemOptionsMakeMenu.setGravity(LocaleController.isRTL ? 3 : 5);
        itemOptionsMakeMenu.setDismissOnMoveOutside(true);
        itemOptionsMakeMenu.show();
    }
    public /* synthetic */ void lambda_showTrackOptions_47(ItemOptions itemOptions, MessageObject messageObject) {
        itemOptions.dismiss();
        this.actions.saveToMusic(messageObject);
    }
    public /* synthetic */ void lambda_showTrackOptions_49(MessageObject messageObject, final ItemOptions itemOptions) {
        this.actions.removeFromMyList(messageObject, () -> lambda_showTrackOptions_48(itemOptions));
    }
    public /* synthetic */ void lambda_showTrackOptions_48(ItemOptions itemOptions) {
        onMyListChanged();
        itemOptions.dismiss();
        updateProfileState(true);
        bulletins().createSimpleBulletin(R.raw.ic_delete, LocaleController.getString(R.string.AudioSaveToMyProfileUnsaved)).show();
    }
    public /* synthetic */ void lambda_showTrackOptions_51(ItemOptions itemOptions, MessageObject messageObject) {
        itemOptions.dismiss();
        this.actions.showInChat(messageObject);
    }

    private void toggleProfile() {
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessageObject == null || this.actions.isProfileIdsLoading()) {
            return;
        }
        boolean zIsInProfile = this.actions.isInProfile(playingMessageObject);
        final boolean z = !zIsInProfile;
        this.profileButton.performHapticFeedback(3);
        final Runnable runnable = () -> lambda_toggleProfile_52(z);
        if (isMyList()) {
            Runnable runnable2 = () -> lambda_toggleProfile_53(runnable);
            PlayerActions playerActions = this.actions;
            if (!zIsInProfile) {
                playerActions.addBackToMyList(playingMessageObject, runnable2);
                return;
            } else {
                playerActions.removeFromMyList(playingMessageObject, runnable2);
                return;
            }
        }
        this.actions.setInProfile(playingMessageObject, z, runnable);
    }
    public /* synthetic */ void lambda_toggleProfile_52(boolean z) {
        updateProfileState(true);
        bulletins().createSimpleBulletin(z ? R.raw.saved_messages : R.raw.ic_delete, LocaleController.getString(z ? R.string.AudioSaveToMyProfileSaved : R.string.AudioSaveToMyProfileUnsaved)).show();
    }
    public /* synthetic */ void lambda_toggleProfile_53(Runnable runnable) {
        onMyListChanged();
        runnable.run();
    }
    public void onMyListChanged() {
        this.queueView.update();
        updateHeader();
    }

    private void selectMode(int i) {
        setMode(i, true);
        PlayerConfig.setPlayerMode(i);
    }

    private void setMode(int i, boolean z) {
        int i2 = this.mode;
        if (i2 == i && z) {
            return;
        }
        this.mode = i;
        if (i2 == 2 && i != 2) {
            this.queueView.clearSearch();
        }
        float f = i == 0 ? 0.0f : 1.0f;
        float f2 = i == 1 ? 1.0f : 0.0f;
        float f3 = i != 2 ? 0.0f : 1.0f;
        SpringAnimation springAnimation = this.modeSpring;
        if (z) {
            Material3Tokens.animate(springAnimation, this.modeProgress, f);
            Material3Tokens.animate(this.lyricsSpring, this.lyricsAlpha, f2);
            Material3Tokens.animate(this.queueSpring, this.queueAlpha, f3);
        } else {
            springAnimation.cancel();
            this.lyricsSpring.cancel();
            this.queueSpring.cancel();
            this.modeProgress = f;
            this.lyricsAlpha = f2;
            this.queueAlpha = f3;
        }
        this.lyricsBackground.setSelected(i == 1, z);
        this.queueBackground.setSelected(i == 2, z);
        updateToggleColors();
        if (i == 1) {
            loadLyrics(false);
        } else if (i == 2) {
            this.queueView.update();
            this.queueView.scrollToCurrent(false);
        }
        applyTransitions();
    }

    private void loadArtwork(final MessageObject messageObject) {
        TrackQuery trackQueryForMessage = PlayerCovers.needsArtwork(messageObject) ? TrackQuery.forMessage(messageObject) : null;
        if (trackQueryForMessage == null || ArtworkRepository.isResolved(trackQueryForMessage)) {
            return;
        }
        ArtworkRepository.fetch(trackQueryForMessage, str -> lambda_loadArtwork_54(messageObject, str));
    }
    public /* synthetic */ void lambda_loadArtwork_54(MessageObject messageObject, String str) {
        if (str == null || this.coverView.getMessageObject() != messageObject) {
            return;
        }
        this.coverView.refreshCover();
    }
    public void loadMotionCover(final MessageObject messageObject) {
        final int i = this.motionRequest + 1;
        this.motionRequest = i;
        TrackQuery trackQueryForMessage = PlayerConfig.getPlayerMotionCovers() ? TrackQuery.forMessage(messageObject) : null;
        if (trackQueryForMessage == null) {
            return;
        }
        Point point = AndroidUtilities.displaySize;
        MotionCoverRepository.fetch(trackQueryForMessage, Math.min(point.x, point.y) - AndroidUtilities.dp(40.0f), str -> lambda_loadMotionCover_55(i, messageObject, str));
    }
    public /* synthetic */ void lambda_loadMotionCover_55(int i, MessageObject messageObject, String str) {
        if (i != this.motionRequest || str == null) {
            return;
        }
        this.coverView.setMotionCover(messageObject, str);
    }
    public void loadLyrics(boolean z) {
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessageObject == null) {
            return;
        }
        TrackQuery trackQueryForMessage = TrackQuery.forMessage(playingMessageObject);
        StringBuilder sb = new StringBuilder();
        sb.append(trackQueryForMessage != null ? trackQueryForMessage.key() : "");
        sb.append("\u0002");
        sb.append(playingMessageObject.getId());
        sb.append("\u0002");
        sb.append(PlayerConfig.getLyricsOnline());
        String string = sb.toString();
        if (z || !string.equals(this.lyricsKey)) {
            this.lyricsKey = string;
            final int i = this.lyricsRequest + 1;
            this.lyricsRequest = i;
            Lyrics lyricsFromTags = LyricsRepository.fromTags(playingMessageObject);
            if (lyricsFromTags != null) {
                showLyrics(lyricsFromTags);
                return;
            }
            if (trackQueryForMessage == null) {
                showLyricsState(4);
                return;
            }
            Lyrics cached = LyricsRepository.getCached(trackQueryForMessage);
            if (cached != null) {
                showLyrics(cached);
                return;
            }
            if (!PlayerConfig.getLyricsOnline()) {
                showLyricsState(2);
                LyricsRepository.loadCached(trackQueryForMessage, (lyrics, i2) -> lambda_loadLyrics_56(i, lyrics, i2));
            } else if (!z && LyricsRepository.isKnownMissing(trackQueryForMessage)) {
                showLyricsState(4);
            } else {
                showLyricsState(2);
                LyricsRepository.fetch(trackQueryForMessage, (lyrics, i2) -> lambda_loadLyrics_57(i, lyrics, i2));
            }
        }
    }
    public /* synthetic */ void lambda_loadLyrics_56(int i, Lyrics lyrics, int i2) {
        if (i != this.lyricsRequest) {
            return;
        }
        if (lyrics != null) {
            showLyrics(lyrics);
        } else {
            showLyricsState(1);
        }
    }
    public /* synthetic */ void lambda_loadLyrics_57(int i, Lyrics lyrics, int i2) {
        if (i != this.lyricsRequest) {
            return;
        }
        if (lyrics != null) {
            showLyrics(lyrics);
        } else {
            showLyricsState(i2 == 2 ? 6 : 4);
        }
    }

    private void showLyrics(Lyrics lyrics) {
        if (lyrics.instrumental) {
            showLyricsState(5);
            return;
        }
        String creditName = lyrics.getCreditName();
        this.lyricsView.setLyrics(lyrics, creditName != null ? LocaleController.formatString(R.string.LyricsCredit, creditName) : null);
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessageObject != null) {
            this.lyricsView.setPosition(getPositionMs(playingMessageObject), !MediaController.getInstance().isMessagePaused());
            final LyricsView lyricsView = this.lyricsView;
            Objects.requireNonNull(lyricsView);
            lyricsView.post(() -> lyricsView.jumpToCurrent());
        }
    }

    private void showLyricsState(int i) {
        if (i == 1) {
            this.lyricsView.setState(i, LocaleController.getString(R.string.LyricsOfferTitle), LocaleController.getString(R.string.LyricsOfferText), LocaleController.getString(R.string.LyricsOfferButton));
            return;
        }
        if (i == 4) {
            this.lyricsView.setState(i, LocaleController.getString(R.string.LyricsNotFoundTitle), LocaleController.getString(PlayerConfig.getLyricsOnline() ? R.string.LyricsNotFoundText : R.string.LyricsNotFoundTextOffline), null);
            return;
        }
        if (i == 5) {
            this.lyricsView.setState(i, LocaleController.getString(R.string.LyricsInstrumentalTitle), LocaleController.getString(R.string.LyricsInstrumentalText), null);
            return;
        }
        LyricsView lyricsView = this.lyricsView;
        if (i == 6) {
            lyricsView.setState(i, LocaleController.getString(R.string.LyricsErrorTitle), LocaleController.getString(R.string.LyricsErrorText), LocaleController.getString(R.string.Retry));
        } else {
            lyricsView.setState(i, null, null, null);
        }
    }

    private static long getPositionMs(MessageObject messageObject) {
        long progressMs = MediaController.getInstance().getProgressMs(messageObject);
        if (progressMs >= 0) {
            return progressMs;
        }
        int i = messageObject.audioProgressMs;
        return i > 0 ? i : (long) (((double) messageObject.audioProgress) * messageObject.getDuration() * 1000.0d);
    }
    public void onCoverBitmapLoaded(final MessageObject messageObject, ImageReceiver imageReceiver) {
        if (messageObject == null || imageReceiver == null || !PlayerConfig.getPlayerCoverColors() || !imageReceiver.hasBitmapImage()) {
            return;
        }
        CoverColorExtractor.extract(messageObject, imageReceiver, obj -> lambda_onCoverBitmapLoaded_58(messageObject, (Integer) obj));
    }
    public /* synthetic */ void lambda_onCoverBitmapLoaded_58(MessageObject messageObject, Integer num) {
        if (this.lastMessageObject == messageObject) {
            updateCoverColors(true);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.playerColorsChanged, new Object[0]);
        }
    }
    public void updateCoverColors(boolean z) {
        MessageObject messageObject;
        Integer cachedSeed;
        if (this.coverColorsWait.shouldWait(this.lastMessageObject)) {
            return;
        }
        PlayerColors playerColorsFromSeed = this.themeColors;
        if (PlayerConfig.getPlayerCoverColors() && (messageObject = this.lastMessageObject) != null && (cachedSeed = CoverColorExtractor.getCachedSeed(messageObject)) != null && cachedSeed.intValue() != 0) {
            playerColorsFromSeed = PlayerColors.fromSeed(cachedSeed.intValue(), this.themeColors.dark);
            int[] cachedGlow = CoverColorExtractor.getCachedGlow(this.lastMessageObject);
            if (cachedGlow != null) {
                playerColorsFromSeed.setCoverGlow(cachedGlow);
            }
        }
        animateColors(playerColorsFromSeed, z);
    }

    private void animateColors(final PlayerColors playerColors, boolean z) {
        PlayerColors playerColors2 = this.targetColors;
        if (playerColors2 == null || !playerColors2.equalsTo(playerColors)) {
            if (this.targetColors == null && this.colors.equalsTo(playerColors)) {
                return;
            }
            this.targetColors = playerColors;
            PlayerSettingsSheet playerSettingsSheet = this.settingsSheet;
            if (playerSettingsSheet != null) {
                playerSettingsSheet.updateColors(playerColors.wrapSettings(this.baseResourcesProvider));
            }
            ValueAnimator valueAnimator = this.colorAnimator;
            if (valueAnimator != null) {
                valueAnimator.cancel();
                this.colorAnimator = null;
            }
            if (!z || !this.playerLayout.isAttachedToWindow()) {
                this.colors.set(playerColors);
                applyColors(this.colors);
                return;
            }
            final PlayerColors playerColorsCopy = this.colors.copy();
            ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(0.0f, 1.0f);
            this.colorAnimator = valueAnimatorOfFloat;
            valueAnimatorOfFloat.setDuration(450L);
            this.colorAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            this.colorAnimator.addUpdateListener(valueAnimator2 -> lambda_animateColors_59(playerColorsCopy, playerColors, valueAnimator2));
            this.colorAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (MusicPlayerSheet.this.colorAnimator == animator) {
                        MusicPlayerSheet.this.colorAnimator = null;
                        MusicPlayerSheet.this.colors.set(playerColors);
                        MusicPlayerSheet musicPlayerSheet = MusicPlayerSheet.this;
                        musicPlayerSheet.applyColors(musicPlayerSheet.colors);
                    }
                }
            });
            this.colorAnimator.start();
        }
    }
    public /* synthetic */ void lambda_animateColors_59(PlayerColors playerColors, PlayerColors playerColors2, ValueAnimator valueAnimator) {
        this.colors.blend(playerColors, playerColors2, ((Float) valueAnimator.getAnimatedValue()).floatValue());
        applyColors(this.colors);
    }
    public void applyColors(PlayerColors playerColors) {
        setBackgroundColor(playerColors.background);
        updateNavigationBar(playerColors);
        ImageView imageView = this.collapseButton;
        int i = playerColors.icon;
        PorterDuff.Mode mode = PorterDuff.Mode.SRC_IN;
        imageView.setColorFilter(new PorterDuffColorFilter(i, mode));
        this.moreButton.setColorFilter(new PorterDuffColorFilter(playerColors.icon, mode));
        int i2 = 0;
        this.collapseBackground.setColors(playerColors.icon, 0);
        this.moreBackground.setColors(playerColors.icon, 0);
        this.headerTitle.setTextColor(playerColors.text);
        this.headerSubtitle.setTextColor(playerColors.textSecondary);
        while (i2 < 2) {
            AudioPlayerAlert.ClippingTextViewSwitcher clippingTextViewSwitcher = this.titleView;
            TextView textView = i2 == 0 ? clippingTextViewSwitcher.getTextView() : clippingTextViewSwitcher.getNextTextView();
            AudioPlayerAlert.ClippingTextViewSwitcher clippingTextViewSwitcher2 = this.artistView;
            TextView textView2 = i2 == 0 ? clippingTextViewSwitcher2.getTextView() : clippingTextViewSwitcher2.getNextTextView();
            if (textView != null) {
                textView.setTextColor(playerColors.text);
            }
            if (textView2 != null) {
                textView2.setTextColor(playerColors.textSecondary);
                textView2.setBackground(Theme.createRadSelectorDrawable(ColorUtils.setAlphaComponent(playerColors.text, 20), 8, 8));
            }
            i2++;
        }
        this.coverView.setColors(playerColors.tonal, playerColors.onTonal);
        this.seekBar.setColors(playerColors.wave, playerColors.track, playerColors.buffer, ColorUtils.setAlphaComponent(playerColors.text, 240), playerColors.background);
        this.timeView.setTextColor(playerColors.textSecondary);
        this.durationView.setTextColor(playerColors.textSecondary);
        this.controlsView.setColors(playerColors.tonal, playerColors.onTonal, playerColors.accent, playerColors.onAccent);
        this.lyricsView.setColors(playerColors.text, playerColors.textSecondary, playerColors.accent, playerColors.onAccent);
        this.queueView.setColors(playerColors);
        this.rhythmBackground.setColors(playerColors.glow, playerColors.background, playerColors.dark);
        HintView2 hintView2 = this.speedHintView;
        if (hintView2 != null) {
            hintView2.setTextColor(playerColors.background);
            this.speedHintView.setBgColor(playerColors.text);
        }
        updateToggleColors();
        updateStatusBar();
        this.playerLayout.invalidate();
    }

    private void updateNavigationBar(PlayerColors playerColors) {
        setOverlayNavBarColor(ColorUtils.setAlphaComponent(playerColors.background, 0));
        AndroidUtilities.setLightNavigationBar(this, !playerColors.dark);
    }
    public void updateStatusBar() {
        PlayerLayout playerLayout = this.playerLayout;
        boolean z = playerLayout.fullscreen && !this.colors.dark && playerLayout.getTranslationY() < ((float) getStatusBarHeight());
        if (this.lightStatusBar != z) {
            this.lightStatusBar = z;
            AndroidUtilities.setLightStatusBar(getWindow(), z);
            AndroidUtilities.setLightStatusBar(this.container, z);
        }
    }

    private ExpressiveStateDrawable createToggle() {
        return new ExpressiveStateDrawable(8.0f).setSelectedRadius(20.0f);
    }
    public void onToggleProgress() {
        applyToggle(this.shuffleButton, this.shuffleBackground, this.colors, true);
        applyToggle(this.repeatButton, this.repeatBackground, this.colors, true);
        applyToggle(this.profileButton, this.profileBackground, this.colors, false);
        applySpeedColors(this.colors);
    }

    private void updateToggleColors() {
        PlayerColors playerColors = this.colors;
        applyToggle(this.shuffleButton, this.shuffleBackground, playerColors, true);
        applyToggle(this.repeatButton, this.repeatBackground, playerColors, true);
        applyModeSegment(this.lyricsButton, this.lyricsBackground, playerColors);
        applyModeSegment(this.queueButton, this.queueBackground, playerColors);
        applyToggle(this.profileButton, this.profileBackground, playerColors, false);
        applySpeedColors(playerColors);
    }

    private void applySpeedColors(PlayerColors playerColors) {
        int iBlendARGB = ColorUtils.blendARGB(playerColors.icon, playerColors.accent, this.speedBackground.getSelectProgress());
        this.speedBackground.setColors(iBlendARGB, 0).setOutline(this.speedMenuShown ? 0 : ColorUtils.blendARGB(ColorUtils.setAlphaComponent(playerColors.textSecondary, 128), playerColors.accent, this.speedBackground.getSelectProgress()), 1.5f);
        this.speedIcon.setColor(iBlendARGB);
        this.speedButton.invalidate();
    }

    private static void applyToggle(ImageView imageView, ExpressiveStateDrawable expressiveStateDrawable, PlayerColors playerColors, boolean z) {
        int iBlendARGB = ColorUtils.blendARGB(playerColors.icon, playerColors.accent, expressiveStateDrawable.getSelectProgress());
        setIconTint(imageView, expressiveStateDrawable, iBlendARGB);
        expressiveStateDrawable.setColors(iBlendARGB, 0).setDot(z ? playerColors.accent : 0, 15.0f);
    }

    private static void setIconTint(ImageView imageView, ExpressiveStateDrawable expressiveStateDrawable, int i) {
        if (expressiveStateDrawable.getContentColor() != i || imageView.getColorFilter() == null) {
            imageView.setColorFilter(new PorterDuffColorFilter(i, PorterDuff.Mode.SRC_IN));
        }
    }

    public void updateOrderButtons(boolean z) {
        int i;
        int i2 = SharedConfig.repeatMode;
        this.shuffleBackground.setSelected(SharedConfig.shuffleMusic, z);
        this.shuffleButton.setContentDescription(LocaleController.getString(R.string.ShuffleList));
        this.repeatBackground.setSelected(i2 != 0, z);
        this.repeatButton.setImageResource(i2 == 2 ? R.drawable.player_new_repeatone : R.drawable.player_new_repeatall);
        ImageView imageView = this.repeatButton;
        if (i2 == 0) {
            i = R.string.AccDescrRepeatOff;
        } else {
            i = i2 == 1 ? R.string.AccDescrRepeatList : R.string.AccDescrRepeatOne;
        }
        imageView.setContentDescription(LocaleController.getString(i));
        this.queueView.setReversed(SharedConfig.playOrderReversed, z);
        updateToggleColors();
        updateHeader();
    }

    private void updateSpeedButtons(boolean z) {
        float playbackSpeed = MediaController.getInstance().getPlaybackSpeed(true);
        this.speedIcon.setValue(playbackSpeed, z);
        this.speedBackground.setSelected(Math.abs(playbackSpeed - 1.0f) > 0.05f, z);
        this.speedButton.setContentDescription(LocaleController.formatString(R.string.PlayerSpeedDescription, SpeedIconDrawable.formatNumber(playbackSpeed)));
        updateToggleColors();
    }
    public void updateProfileState(boolean z) {
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        boolean z2 = playingMessageObject != null && this.actions.isInProfile(playingMessageObject);
        this.profileBackground.setSelected(z2, z);
        this.profileButton.setImageResource(z2 ? R.drawable.media_like_active : R.drawable.media_like);
        this.profileButton.setContentDescription(LocaleController.getString(z2 ? R.string.AudioRemoveFromProfile : R.string.AudioAddToProfile));
        this.profileButton.setEnabled(!this.actions.isProfileIdsLoading());
        this.profileButton.setVisibility((playingMessageObject == null || this.noforwards || playingMessageObject.getDocument() == null || PlayerActions.isServiceTrack(playingMessageObject)) ? 8 : 0);
        updateToggleColors();
    }
    public void updateHeader() {
        CharSequence chatTitle;
        String string;
        ArrayList<MessageObject> playlist;
        int iIndexOf;
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        MessagesController.SavedMusicList savedMusicList = this.savedMusicList;
        CharSequence string2 = LocaleController.getString(R.string.AttachMusic);
        if (savedMusicList != null) {
            string2 = savedMusicList.dialogId == UserConfig.getInstance(this.currentAccount).getClientUserId() ? LocaleController.getString(R.string.ProfilePlaylistTitleMine) : LocaleController.formatString(R.string.ProfilePlaylistTitle, DialogObject.getShortName(savedMusicList.dialogId));
        } else if (playingMessageObject != null && !MediaController.getInstance().currentPlaylistIsGlobalSearch() && (chatTitle = getChatTitle(playingMessageObject)) != null) {
            string2 = chatTitle;
        }
        this.headerTitle.setText(string2);
        if (playingMessageObject == null || (iIndexOf = (playlist = MediaController.getInstance().getPlaylist()).indexOf(playingMessageObject)) < 0 || playlist.size() <= 1) {
            string = null;
        } else {
            string = LocaleController.formatString(R.string.PlayerTrackOf, Integer.valueOf((savedMusicList == null) == SharedConfig.playOrderReversed ? iIndexOf + 1 : playlist.size() - iIndexOf), Integer.valueOf(playlist.size()));
        }
        this.headerSubtitle.setText(string);
        int i = TextUtils.isEmpty(string) ? 8 : 0;
        if (this.headerSubtitle.getVisibility() != i) {
            this.headerSubtitle.setVisibility(i);
            this.playerLayout.requestLayout();
        }
    }

    private CharSequence getChatTitle(MessageObject messageObject) {
        long dialogId = messageObject.getDialogId();
        if (dialogId == UserConfig.getInstance(this.currentAccount).getClientUserId()) {
            return LocaleController.getString(messageObject.getSavedDialogId() == UserObject.ANONYMOUS ? R.string.AnonymousForward : R.string.SavedMessages);
        }
        if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(this.currentAccount).getEncryptedChat(Integer.valueOf(DialogObject.getEncryptedChatId(dialogId)));
            if (encryptedChat == null) {
                return null;
            }
            dialogId = encryptedChat.user_id;
        }
        String name = DialogObject.getName(this.currentAccount, dialogId);
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        return name;
    }

    private void bindMessage(boolean z, boolean z2) {
        MediaController mediaController = MediaController.getInstance();
        MessageObject playingMessageObject = mediaController.getPlayingMessageObject();
        if ((playingMessageObject == null && z) || (playingMessageObject != null && !playingMessageObject.isMusic())) {
            dismiss();
            return;
        }
        if (playingMessageObject == null) {
            this.lastMessageObject = null;
            return;
        }
        MessageObject messageObject = this.lastMessageObject;
        boolean z3 = false;
        boolean z4 = playingMessageObject == messageObject;
        boolean z5 = (!z2 || z4 || messageObject == null) ? false : true;
        int i = this.pendingDirection;
        if (i == 0) {
            i = 1;
        }
        this.pendingDirection = 0;
        this.lastMessageObject = playingMessageObject;
        this.moreButton.setVisibility((playingMessageObject.eventId != 0 || playingMessageObject.getId() <= -2000000000) ? 4 : 0);
        this.noforwards = PlayerActions.isNoForwards(this.currentAccount, playingMessageObject);
        checkIfMusicDownloaded(playingMessageObject);
        updateProgress(playingMessageObject);
        boolean zIsMessagePaused = mediaController.isMessagePaused();
        this.controlsView.setPlaying(!zIsMessagePaused, z2);
        this.coverView.setPlaying(!zIsMessagePaused, z2);
        this.seekBar.setPlaying(!zIsMessagePaused, mediaController.getPlaybackSpeed(true), z2);
        updateRhythm();
        if (!z4) {
            this.coverView.setCover(playingMessageObject, z5, i);
            loadMotionCover(playingMessageObject);
            loadArtwork(playingMessageObject);
            this.titleView.setText(playingMessageObject.getMusicTitle(), z5);
            this.artistView.setText(playingMessageObject.getMusicAuthor(), z5);
            PlayerCovers.preloadNeighbors(this.currentAccount);
            this.lastDuration = (int) playingMessageObject.getDuration();
            this.lastTime = -1;
            updateProgress(playingMessageObject);
            updateCoverColors(z2);
            if (this.mode == 1) {
                loadLyrics(false);
            }
            this.queueView.updateCells();
        }
        if (z2 && z4) {
            z3 = true;
        }
        updateProfileState(z3);
        updateHeader();
        this.moreIcon.setCasting(CastSync.isActive(), z2);
    }

    private void checkIfMusicDownloaded(MessageObject messageObject) {
        File localFile = PlayerActions.getLocalFile(messageObject);
        boolean z = SharedConfig.streamMedia && messageObject.getDialogId() != 0 && !DialogObject.isEncryptedDialog(messageObject.getDialogId()) && messageObject.isMusic();
        String fileName = messageObject.getFileName();
        this.currentFile = FileLoader.getAttachFileName(messageObject.getDocument());
        this.currentAudioFinishedLoading = localFile.exists() || PlayerCovers.getPlayingAudioInfo(messageObject) != null;
        if (!localFile.exists() && !z) {
            DownloadController.getInstance(this.currentAccount).addLoadingFileObserver(fileName, this);
            Float fileProgress = ImageLoader.getInstance().getFileProgress(fileName);
            this.seekBar.setLoading(true, fileProgress != null ? fileProgress.floatValue() : 0.0f);
            this.controlsView.setLoading(true);
            this.controlsView.setPlayEnabled(false);
            return;
        }
        DownloadController.getInstance(this.currentAccount).removeLoadingFileObserver(this);
        this.seekBar.setLoading(false, 0.0f);
        this.controlsView.setLoading(false);
        this.controlsView.setPlayEnabled(true);
    }
    public void updateProgress(MessageObject messageObject) {
        int duration;
        int i;
        MediaController mediaController = MediaController.getInstance();
        if (this.seekBar.isDragging()) {
            duration = messageObject.audioProgressSec;
        } else {
            boolean z = this.rewindingProgress >= 0.0f && ((i = this.rewindingState) == -1 || (i == 1 && mediaController.isMessagePaused()));
            this.seekBar.setProgress(z ? this.rewindingProgress : messageObject.audioProgress);
            updateBuffer(messageObject);
            if (z) {
                duration = (int) (messageObject.getDuration() * ((double) this.rewindingProgress));
                messageObject.audioProgressSec = duration;
            } else {
                duration = messageObject.audioProgressSec;
            }
        }
        if (this.lastTime != duration) {
            this.lastTime = duration;
            this.timeView.setText(AndroidUtilities.formatShortDuration(duration));
            int i2 = this.lastDuration;
            if (i2 <= 0) {
                this.durationView.setText("-:--");
            } else {
                boolean z2 = this.showRemaining;
                TextView textView = this.durationView;
                if (z2) {
                    textView.setText(LocaleController.formatString(R.string.PlayerTimeRemaining, AndroidUtilities.formatShortDuration(Math.max(0, i2 - duration))));
                } else {
                    textView.setText(AndroidUtilities.formatShortDuration(i2));
                }
            }
        }
        if (this.mode == 1 || this.lyricsAlpha > 0.0f) {
            this.lyricsView.setPosition(getPositionMs(messageObject), !mediaController.isMessagePaused());
        }
    }

    private void updateBuffer(MessageObject messageObject) {
        float bufferedProgressFromPosition = 1.0f;
        if (!this.currentAudioFinishedLoading) {
            long jElapsedRealtime = SystemClock.elapsedRealtime();
            if (Math.abs(jElapsedRealtime - this.lastBufferedPositionCheck) >= 500) {
                bufferedProgressFromPosition = MediaController.getInstance().isStreamingCurrentAudio() ? FileLoader.getInstance(this.currentAccount).getBufferedProgressFromPosition(messageObject.audioProgress, this.currentFile) : 1.0f;
                this.lastBufferedPositionCheck = jElapsedRealtime;
            } else {
                bufferedProgressFromPosition = -1.0f;
            }
        }
        if (bufferedProgressFromPosition != -1.0f) {
            this.bufferSpring.getSpring().setFinalPosition(bufferedProgressFromPosition * 1000.0f);
            this.bufferSpring.start();
        }
    }

    @Override
    public int getLeftInset() {
        if (AndroidUtilities.isTablet()) {
            return super.getLeftInset();
        }
        return 0;
    }

    @Override
    public int getRightInset() {
        if (AndroidUtilities.isTablet()) {
            return super.getRightInset();
        }
        return 0;
    }
    public int getSafeLeftInset() {
        return super.getLeftInset();
    }
    public int getSafeRightInset() {
        return super.getRightInset();
    }

    @Override
    public int getBottomSheetWidth(boolean z, int i, int i2) {
        return AndroidUtilities.isTablet() ? super.getBottomSheetWidth(z, i, i2) : i;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        if (motionEvent.getActionMasked() == 0 && this.containerView != null) {
            this.swipeDismissAllowed = isSwipeDismissArea(motionEvent.getX() - this.containerView.getX(), motionEvent.getY() - this.containerView.getY());
        }
        return super.dispatchTouchEvent(motionEvent);
    }

    private boolean isSwipeDismissArea(float f, float f2) {
        PlayerLayout playerLayout = this.playerLayout;
        if (f2 < 0.0f) {
            return false;
        }
        if (playerLayout.keyboardLayout) {
            return f2 < ((float) playerLayout.headerBottom);
        }
        if (this.mode != 0 && f2 >= playerLayout.stageTop && f2 < playerLayout.stageBottom) {
            int i = playerLayout.stageLeft;
            if (f >= i && f < i + playerLayout.stageWidth) {
                return false;
            }
        }
        if (Math.abs(f2 - playerLayout.lineCenter) <= AndroidUtilities.dp(24.0f)) {
            int i2 = playerLayout.paneLeft;
            if (f >= i2 && f < i2 + playerLayout.paneWidth) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canDismissWithSwipe() {
        return this.swipeDismissAllowed;
    }
    public void applyTransitions() {
        PlayerLayout playerLayout = this.playerLayout;
        if (playerLayout.contentWidth <= 0) {
            return;
        }
        float f = this.modeProgress;
        float fDp = playerLayout.paneLeft + AndroidUtilities.dp(20.0f);
        float f2 = playerLayout.compactCoverSize;
        float f3 = playerLayout.keyboardLayout ? f : 0.0f;
        this.coverView.setCoverRect(AndroidUtilities.lerp(playerLayout.coverLeft, fDp, f), AndroidUtilities.lerp(playerLayout.coverTop, playerLayout.titleTop, f), AndroidUtilities.lerp(playerLayout.coverSize, f2, f), AndroidUtilities.lerp(AndroidUtilities.dp(28.0f), AndroidUtilities.dp(14.0f), f), f);
        this.coverView.setHideProgress(f3);
        float f4 = 1.0f - f3;
        float fDp2 = (f2 + AndroidUtilities.dp(16.0f)) * f;
        int iRound = Math.round(fDp2);
        setWidth(this.titleView, playerLayout.textWidth - iRound);
        this.titleView.setTranslationX(fDp2);
        this.titleView.setTranslationY(0.0f);
        this.titleView.setAlpha(f4);
        this.titleView.setVisibility(f4 < 0.01f ? 4 : 0);
        setWidth(this.artistView, (playerLayout.textWidth - iRound) + (AndroidUtilities.dp(6.0f) * 2));
        this.artistView.setTranslationX(fDp2);
        this.artistView.setTranslationY(0.0f);
        this.artistView.setAlpha(f4);
        this.artistView.setVisibility(f4 < 0.01f ? 4 : 0);
        this.profileButton.setTranslationY(0.0f);
        this.profileButton.setAlpha(f4);
        float fDp3 = AndroidUtilities.dp(16.0f);
        setPanel(this.lyricsView, this.lyricsAlpha * smooth(f), (1.0f - this.lyricsAlpha) * fDp3);
        setPanel(this.queueView, this.queueAlpha * smooth(f), (1.0f - this.queueAlpha) * fDp3);
        setPanel(this.shuffleButton, 1.0f, 0.0f);
        setPanel(this.repeatButton, 1.0f, 0.0f);
        float f5 = playerLayout.keyboardLayout ? 0.0f : 1.0f;
        setPanel(this.speedButton, f5, 0.0f);
        setPanel(this.lyricsButton, f5, 0.0f);
        setPanel(this.queueButton, f5, 0.0f);
        float f6 = playerLayout.keyboardLayout ? 0.0f : 1.0f;
        setPanel(this.seekBar, f6, 0.0f);
        setPanel(this.timeView, f6, 0.0f);
        setPanel(this.durationView, f6, 0.0f);
        setPanel(this.controlsView, f6, 0.0f);
        if (playerLayout.keyboardLayout) {
            setPanel(this.shuffleButton, 0.0f, 0.0f);
            setPanel(this.repeatButton, 0.0f, 0.0f);
        }
        playerLayout.invalidate();
    }

    private static void setWidth(View view, int i) {
        int iMax = Math.max(0, i);
        if (view.getWidth() == iMax && view.getMeasuredWidth() == iMax) {
            return;
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(iMax, TLObject.FLAG_30), View.MeasureSpec.makeMeasureSpec(view.getMeasuredHeight(), TLObject.FLAG_30));
        view.layout(view.getLeft(), view.getTop(), view.getLeft() + iMax, view.getTop() + view.getMeasuredHeight());
    }

    private static float smooth(float f) {
        float fClamp01 = Utilities.clamp01(f);
        return fClamp01 * fClamp01 * (3.0f - (fClamp01 * 2.0f));
    }

    private static void setPanel(View view, float f, float f2) {
        float fClamp01 = Utilities.clamp01(f);
        view.setAlpha(fClamp01);
        view.setTranslationY(f2);
        int i = fClamp01 < 0.01f ? 4 : 0;
        if (view.getVisibility() == i || view.getVisibility() == 8) {
            return;
        }
        view.setVisibility(i);
    }

    @Override
    public void didReceivedNotification(int i, int i2, Object... objArr) {
        MessageObject messageObject;
        int i3 = NotificationCenter.messagePlayingDidStart;
        if (i == i3 || i == NotificationCenter.messagePlayingPlayStateChanged || i == NotificationCenter.messagePlayingDidReset) {
            bindMessage(i == NotificationCenter.messagePlayingDidReset && objArr.length > 1 && ((Boolean) objArr[1]).booleanValue(), true);
            if (i != i3 || (messageObject = (MessageObject) objArr[0]) == null || messageObject.eventId == 0) {
                this.queueView.updateCells();
                if (i != NotificationCenter.messagePlayingPlayStateChanged || MediaController.getInstance().getPlayingMessageObject() == null) {
                    return;
                }
                if (MediaController.getInstance().isMessagePaused()) {
                    startForwardRewindingSeek();
                    return;
                }
                if (this.rewindingState != 1 || this.rewindingProgress == -1.0f) {
                    return;
                }
                AndroidUtilities.cancelRunOnUIThread(this.rewindSeek);
                this.lastUpdateRewindingPlayerTime = 0L;
                this.rewindSeek.run();
                this.rewindingProgress = -1.0f;
                return;
            }
            return;
        }
        if (i == NotificationCenter.messagePlayingProgressDidChanged || i == NotificationCenter.messagePlayingDidSeek) {
            MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
            if (playingMessageObject == null || !playingMessageObject.isMusic()) {
                return;
            }
            updateProgress(playingMessageObject);
            return;
        }
        if (i == NotificationCenter.messagePlayingSpeedChanged) {
            updateSpeedButtons(true);
            if (MediaController.getInstance().getPlayingMessageObject() != null) {
                this.seekBar.setPlaying(!MediaController.getInstance().isMessagePaused(), MediaController.getInstance().getPlaybackSpeed(true), true);
                return;
            }
            return;
        }
        if (i == NotificationCenter.musicDidLoad || i == NotificationCenter.moreMusicDidLoad) {
            this.savedMusicList = MediaController.getInstance().currentSavedMusicList;
            this.queueView.update();
            updateHeader();
            return;
        }
        if (i == NotificationCenter.fileLoaded) {
            String str = (String) objArr[0];
            if (str == null || !str.equals(this.currentFile)) {
                return;
            }
            this.currentAudioFinishedLoading = true;
            MessageObject playingMessageObject2 = MediaController.getInstance().getPlayingMessageObject();
            if (playingMessageObject2 != null) {
                checkIfMusicDownloaded(playingMessageObject2);
                this.coverView.refreshCover();
                if (this.mode != 1 || this.lyricsView.getState() == 3) {
                    return;
                }
                this.lyricsKey = null;
                loadLyrics(false);
                return;
            }
            return;
        }
        if (i == NotificationCenter.fileLoadProgressChanged) {
            String str2 = (String) objArr[0];
            MessageObject playingMessageObject3 = MediaController.getInstance().getPlayingMessageObject();
            if (str2 == null || !str2.equals(this.currentFile) || playingMessageObject3 == null) {
                return;
            }
            updateBuffer(playingMessageObject3);
            return;
        }
        if (i == NotificationCenter.musicIdsLoaded) {
            updateProfileState(true);
        }
    }

    @Override
    public void onProgressDownload(String str, long j, long j2) {
        this.seekBar.setLoading(true, j2 > 0 ? Math.min(1.0f, j / j2) : 0.0f);
        this.controlsView.setLoading(true);
    }

    @Override
    public int getObserverTag() {
        return this.TAG;
    }

    @Override
    public void show() {
        super.show();
        instance = this;
        updateRhythm();
    }
    public void updateRhythm() {
        boolean z = false;
        boolean z2 = (!PlayerConfig.getPlayerRhythmBackground() || AndroidUtilities.isTablet() || isDismissed()) ? false : true;
        boolean z3 = z2 && isShowing() && this.playerLayout.getWindowVisibility() == 0 && LiteMode.isEnabled(32);
        boolean z4 = z3 && !MediaController.getInstance().isMessagePaused();
        RhythmTap.setEnabled(z3);
        this.rhythmBackground.setActive(z2);
        this.rhythmBackground.setAnimating(z4);
        RhythmBackground rhythmBackground = this.rhythmBackground;
        if (PlayerConfig.getPlayerParticles() && LiteMode.isEnabled(131072)) {
            z = true;
        }
        rhythmBackground.setParticlesEnabled(z);
        this.playerLayout.invalidate();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.ObserversGroup observersGroup = this.observers;
        if (observersGroup != null) {
            observersGroup.removeAllObservers();
            this.observers = null;
        }
        DownloadController.getInstance(this.currentAccount).removeLoadingFileObserver(this);
        int i = this.rewindingState;
        if (i != 0) {
            onRewindHold(i, false);
        }
        AndroidUtilities.cancelRunOnUIThread(this.rewindStepRunnable);
        AndroidUtilities.cancelRunOnUIThread(this.rewindSeek);
        this.seekPlateSpring.cancel();
        ValueAnimator valueAnimator = this.colorAnimator;
        if (valueAnimator != null) {
            valueAnimator.cancel();
            this.colorAnimator = null;
        }
        this.coverColorsWait.cancel();
        RhythmTap.setEnabled(false);
        this.rhythmBackground.setAnimating(false);
        this.lyricsRequest++;
        this.motionRequest++;
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (this.mode == 2 && this.queueView.isSearchFocused()) {
            this.queueView.clearSearch();
        } else if (this.mode != 0) {
            selectMode(0);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate themeDescriptionDelegate = () -> lambda_getThemeDescriptions_60();
        int[] iArr = {Theme.key_player_background, Theme.key_player_actionBarTitle, Theme.key_player_time, Theme.key_player_button, Theme.key_player_progress, Theme.key_player_progressBackground, Theme.key_featuredStickers_addButton, Theme.key_featuredStickers_buttonText, Theme.key_windowBackgroundWhite, Theme.key_dialogBackground};
        for (int i = 0; i < 10; i++) {
            arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDescriptionDelegate, iArr[i]));
        }
        return arrayList;
    }
    public /* synthetic */ void lambda_getThemeDescriptions_60() {
        this.themeColors = PlayerColors.fromTheme(this.baseResourcesProvider);
        this.targetColors = null;
        updateCoverColors(false);
    }

    public class PlayerLayout extends FrameLayout {
        private final Paint backgroundPaint;
        private final RectF backgroundRect;
        int compactCoverSize;
        int contentLeft;
        int contentTop;
        int contentWidth;
        float coverLeft;
        float coverSize;
        float coverTop;
        boolean fullscreen;
        int headerBottom;
        boolean keyboardLayout;
        boolean landscape;
        int lineCenter;
        int modeTop;
        private final HashSet<View> ownViews;
        int paneLeft;
        int paneWidth;
        int stageBottom;
        int stageLeft;
        int stageTop;
        int stageWidth;
        int textWidth;
        int timesTop;
        int titleTop;
        int toolbarTop;
        private float topRadius;
        int transportTop;

        public PlayerLayout(Context context) {
            super(context);
            this.backgroundRect = new RectF();
            this.backgroundPaint = new Paint(1);
            this.ownViews = new HashSet<>();
            setWillNotDraw(false);
            setClipChildren(false);
        }

        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();
            Bulletin.addDelegate(this, new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int i) {
                    int measuredHeight = PlayerLayout.this.getMeasuredHeight();
                    PlayerLayout playerLayout = PlayerLayout.this;
                    return Math.max(0, measuredHeight - (playerLayout.keyboardLayout ? playerLayout.getMeasuredHeight() : playerLayout.toolbarTop));
                }
            });
        }

        @Override
        public void onWindowVisibilityChanged(int i) {
            super.onWindowVisibilityChanged(i);
            MusicPlayerSheet.this.updateRhythm();
        }

        @Override
        public void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            Bulletin.removeDelegate(this);
            if (MusicPlayerSheet.this.isDismissed()) {
                MusicPlayerSheet.this.controlsView.recycle();
            }
        }

        @Override
        public void setTranslationY(float f) {
            super.setTranslationY(f);
            float topRadius = getTopRadius();
            if (topRadius != this.topRadius) {
                this.topRadius = topRadius;
                invalidate();
            }
            MusicPlayerSheet.this.updateStatusBar();
        }

        private float getTopRadius() {
            if (this.fullscreen) {
                return AndroidUtilities.dp(28.0f) * Utilities.clamp01(getTranslationY() / AndroidUtilities.dp(28.0f));
            }
            return 0.0f;
        }

        @Override
        public void onMeasure(int i, int i2) {
            int size = View.MeasureSpec.getSize(i);
            int size2 = View.MeasureSpec.getSize(i2);
            boolean zIsTablet = AndroidUtilities.isTablet();
            this.fullscreen = !zIsTablet;
            if (zIsTablet) {
                size2 = Math.max(AndroidUtilities.dp(320.0f), (size2 - MusicPlayerSheet.this.getStatusBarHeight()) - AndroidUtilities.dp(8.0f));
            }
            int i3 = size2;
            int safeLeftInset = this.fullscreen ? MusicPlayerSheet.this.getSafeLeftInset() : 0;
            int safeRightInset = this.fullscreen ? MusicPlayerSheet.this.getSafeRightInset() : 0;
            this.contentLeft = MusicPlayerSheet.this.backgroundPaddingLeft + safeLeftInset;
            boolean z = this.fullscreen;
            MusicPlayerSheet musicPlayerSheet = MusicPlayerSheet.this;
            this.contentTop = z ? musicPlayerSheet.getStatusBarHeight() : AndroidUtilities.dp(8.0f) + musicPlayerSheet.backgroundPaddingTop;
            this.contentWidth = ((size - (MusicPlayerSheet.this.backgroundPaddingLeft * 2)) - safeLeftInset) - safeRightInset;
            boolean zIsImeVisible = isImeVisible();
            this.keyboardLayout = zIsImeVisible && MusicPlayerSheet.this.mode == 2;
            this.landscape = this.fullscreen && this.contentWidth > i3;
            computeGeometry(i3 - (zIsImeVisible ? 0 : getNavigationBottomInset()));
            int i4 = this.contentWidth;
            int i5 = this.paneWidth;
            int iDp = AndroidUtilities.dp(40.0f);
            measureExact(MusicPlayerSheet.this.collapseButton, iDp, iDp);
            measureExact(MusicPlayerSheet.this.moreButton, iDp, iDp);
            int iDp2 = i4 - (AndroidUtilities.dp(68.0f) * 2);
            measureExact(MusicPlayerSheet.this.headerTitle, iDp2, AndroidUtilities.dp(22.0f));
            measureExact(MusicPlayerSheet.this.headerSubtitle, iDp2, AndroidUtilities.dp(18.0f));
            measureExact(MusicPlayerSheet.this.coverView, size, i3);
            int iDp3 = (i5 - (AndroidUtilities.dp(20.0f) * 2)) - (MusicPlayerSheet.this.profileButton.getVisibility() != 8 ? AndroidUtilities.dp(48.0f) : 0);
            this.textWidth = iDp3;
            measureExact(MusicPlayerSheet.this.titleView, iDp3, AndroidUtilities.dp(28.0f));
            measureExact(MusicPlayerSheet.this.artistView, iDp3 + (AndroidUtilities.dp(6.0f) * 2), AndroidUtilities.dp(28.0f));
            measureExact(MusicPlayerSheet.this.profileButton, iDp, iDp);
            measureExact(MusicPlayerSheet.this.seekBar, i5 - (AndroidUtilities.dp(20.0f) * 2), AndroidUtilities.dp(68.0f));
            measureExact(MusicPlayerSheet.this.timeView, AndroidUtilities.dp(120.0f), AndroidUtilities.dp(18.0f));
            measureExact(MusicPlayerSheet.this.durationView, AndroidUtilities.dp(120.0f), AndroidUtilities.dp(18.0f));
            measureExact(MusicPlayerSheet.this.shuffleButton, iDp, iDp);
            measureExact(MusicPlayerSheet.this.repeatButton, iDp, iDp);
            measureExact(MusicPlayerSheet.this.controlsView, i5 - (AndroidUtilities.dp(68.0f) * 2), AndroidUtilities.dp(PlayerControlsView.getHeightDp()));
            measureExact(MusicPlayerSheet.this.speedButton, AndroidUtilities.dp(64.0f), AndroidUtilities.dp(48.0f));
            measureExact(MusicPlayerSheet.this.lyricsButton, AndroidUtilities.dp(64.0f), AndroidUtilities.dp(48.0f));
            measureExact(MusicPlayerSheet.this.queueButton, AndroidUtilities.dp(64.0f), AndroidUtilities.dp(48.0f));
            int iMax = Math.max(AndroidUtilities.dp(64.0f), this.stageBottom - this.stageTop);
            measureExact(MusicPlayerSheet.this.lyricsView, this.stageWidth, iMax);
            measureExact(MusicPlayerSheet.this.queueView, this.stageWidth, iMax);
            int iMakeMeasureSpec = View.MeasureSpec.makeMeasureSpec(size, TLObject.FLAG_30);
            int iMakeMeasureSpec2 = View.MeasureSpec.makeMeasureSpec(i3, TLObject.FLAG_30);
            int childCount = getChildCount();
            for (int i6 = 0; i6 < childCount; i6++) {
                View childAt = getChildAt(i6);
                if (childAt == MusicPlayerSheet.this.speedHintView) {
                    measureExact(childAt, this.paneWidth, this.toolbarTop + AndroidUtilities.dp(6.0f));
                } else if (childAt.getVisibility() != 8 && !this.ownViews.contains(childAt)) {
                    measureChildWithMargins(childAt, iMakeMeasureSpec, 0, iMakeMeasureSpec2, 0);
                }
            }
            setMeasuredDimension(size, i3);
        }

        private int getNavigationBottomInset() {
            WindowInsetsCompat rootWindowInsets = ViewCompat.getRootWindowInsets(this);
            if (rootWindowInsets == null) {
                return Math.max(0, MusicPlayerSheet.this.getBottomInset());
            }
            return rootWindowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        }

        private boolean isImeVisible() {
            WindowInsetsCompat rootWindowInsets = ViewCompat.getRootWindowInsets(this);
            if (rootWindowInsets == null) {
                return MusicPlayerSheet.this.keyboardVisible;
            }
            return rootWindowInsets.isVisible(WindowInsetsCompat.Type.ime());
        }

        public void addOwnView(View view) {
            this.ownViews.add(view);
            addView(view);
        }

        private void measureExact(View view, int i, int i2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, i), TLObject.FLAG_30), View.MeasureSpec.makeMeasureSpec(Math.max(0, i2), TLObject.FLAG_30));
        }

        private void computeGeometry(int i) {
            if (this.landscape) {
                computeLandscapeGeometry(i);
                return;
            }
            int i2 = this.contentLeft;
            this.stageLeft = i2;
            this.paneLeft = i2;
            int i3 = this.contentWidth;
            this.stageWidth = i3;
            this.paneWidth = i3;
            int iDp = AndroidUtilities.dp(PlayerControlsView.getHeightDp());
            int iDp2 = this.contentTop + AndroidUtilities.dp(56.0f);
            this.headerBottom = iDp2;
            this.stageTop = iDp2 + AndroidUtilities.dp(8.0f);
            int iDp3 = i3 - (AndroidUtilities.dp(20.0f) * 2);
            int iDp4 = this.headerBottom + AndroidUtilities.dp(16.0f);
            int iDp5 = AndroidUtilities.dp(159.0f) + iDp;
            int iDp6 = i - AndroidUtilities.dp(64.0f);
            int iRound = Math.round(iDp6 - Math.min(Math.max(0, ((iDp6 - iDp4) - iDp5) - iDp3) * 0.25f, AndroidUtilities.dp(12.0f)));
            this.toolbarTop = iRound;
            float fMax = Math.max(0, Math.min(iDp3, (iRound - iDp5) - iDp4));
            this.coverSize = fMax;
            this.coverLeft = this.contentLeft + ((i3 - fMax) / 2.0f);
            int iMax = Math.max(0, (this.toolbarTop - Math.round(iDp4 + fMax)) - iDp5);
            int iMin = Math.min(iMax, AndroidUtilities.dp(12.0f));
            int iDp7 = ((this.toolbarTop - AndroidUtilities.dp(8.0f)) - Math.min(iMax - iMin, AndroidUtilities.dp(32.0f))) - iDp;
            this.transportTop = iDp7;
            int iDp8 = ((iDp7 - AndroidUtilities.dp(12.0f)) - iMin) - AndroidUtilities.dp(18.0f);
            this.timesTop = iDp8;
            int iDp9 = iDp8 - AndroidUtilities.dp(14.0f);
            this.lineCenter = iDp9;
            int iDp10 = iDp9 - AndroidUtilities.dp(83.0f);
            this.titleTop = iDp10;
            int i4 = this.headerBottom;
            this.coverTop = i4 + (((iDp10 - i4) - this.coverSize) / 2.0f);
            this.compactCoverSize = AndroidUtilities.dp(50.0f);
            if (!this.keyboardLayout) {
                i = this.titleTop;
            }
            this.stageBottom = i - AndroidUtilities.dp(16.0f);
        }

        private void computeLandscapeGeometry(int i) {
            int iRound = Math.round(this.contentWidth * 0.5f);
            int i2 = this.contentLeft;
            this.paneLeft = i2 + iRound;
            int i3 = this.contentWidth;
            this.paneWidth = i3 - iRound;
            this.stageLeft = i2;
            if (!this.keyboardLayout) {
                i3 = iRound;
            }
            this.stageWidth = i3;
            int iDp = AndroidUtilities.dp(PlayerControlsView.getHeightDp());
            int iDp2 = this.contentTop + AndroidUtilities.dp(56.0f);
            this.headerBottom = iDp2;
            this.stageTop = iDp2 + AndroidUtilities.dp(8.0f);
            int iDp3 = i - AndroidUtilities.dp(8.0f);
            int iDp4 = iDp3 - AndroidUtilities.dp(56.0f);
            this.toolbarTop = iDp4;
            int iDp5 = (iDp4 - AndroidUtilities.dp(8.0f)) - iDp;
            this.transportTop = iDp5;
            int iDp6 = (iDp5 - AndroidUtilities.dp(12.0f)) - AndroidUtilities.dp(18.0f);
            this.timesTop = iDp6;
            int iDp7 = iDp6 - AndroidUtilities.dp(14.0f);
            this.lineCenter = iDp7;
            int iDp8 = iDp7 - AndroidUtilities.dp(83.0f);
            this.titleTop = iDp8;
            int iMax = Math.max(0, (iDp8 - this.headerBottom) / 2);
            this.titleTop -= iMax;
            this.lineCenter -= iMax;
            this.timesTop -= iMax;
            this.transportTop -= iMax;
            this.toolbarTop -= iMax;
            int i4 = iDp3 - this.headerBottom;
            float fMax = Math.max(0, Math.min(iRound - (AndroidUtilities.dp(20.0f) * 2), i4 - AndroidUtilities.dp(16.0f)));
            this.coverSize = fMax;
            this.coverLeft = this.contentLeft + ((iRound - fMax) / 2.0f);
            this.coverTop = this.headerBottom + ((i4 - fMax) / 2.0f);
            this.compactCoverSize = AndroidUtilities.dp(50.0f);
            this.stageBottom = i - AndroidUtilities.dp(16.0f);
        }

        @Override
        public void onLayout(boolean z, int i, int i2, int i3, int i4) {
            super.onLayout(z, i, i2, i3, i4);
            int iDp = this.contentLeft + AndroidUtilities.dp(20.0f);
            int iDp2 = (this.contentLeft + this.contentWidth) - AndroidUtilities.dp(20.0f);
            int iDp3 = this.paneLeft + AndroidUtilities.dp(20.0f);
            int iDp4 = (this.paneLeft + this.paneWidth) - AndroidUtilities.dp(20.0f);
            int iDp5 = AndroidUtilities.dp(40.0f);
            int iDp6 = this.contentTop + ((AndroidUtilities.dp(56.0f) - iDp5) / 2);
            place(MusicPlayerSheet.this.collapseButton, iDp, iDp6);
            place(MusicPlayerSheet.this.moreButton, iDp2 - iDp5, iDp6);
            int iDp7 = iDp + iDp5 + AndroidUtilities.dp(8.0f);
            int visibility = MusicPlayerSheet.this.headerSubtitle.getVisibility();
            MusicPlayerSheet musicPlayerSheet = MusicPlayerSheet.this;
            if (visibility == 8) {
                place(musicPlayerSheet.headerTitle, iDp7, this.contentTop + ((AndroidUtilities.dp(56.0f) - MusicPlayerSheet.this.headerTitle.getMeasuredHeight()) / 2));
            } else {
                int iDp8 = this.contentTop + ((AndroidUtilities.dp(56.0f) - (musicPlayerSheet.headerTitle.getMeasuredHeight() + MusicPlayerSheet.this.headerSubtitle.getMeasuredHeight())) / 2);
                place(MusicPlayerSheet.this.headerTitle, iDp7, iDp8);
                place(MusicPlayerSheet.this.headerSubtitle, iDp7, iDp8 + MusicPlayerSheet.this.headerTitle.getMeasuredHeight());
            }
            place(MusicPlayerSheet.this.coverView, 0, 0);
            place(MusicPlayerSheet.this.titleView, iDp3, this.titleTop);
            place(MusicPlayerSheet.this.artistView, iDp3 - AndroidUtilities.dp(6.0f), this.titleTop + AndroidUtilities.dp(26.0f));
            int i5 = iDp4 - iDp5;
            place(MusicPlayerSheet.this.profileButton, i5, this.titleTop + ((AndroidUtilities.dp(50.0f) - iDp5) / 2));
            place(MusicPlayerSheet.this.seekBar, iDp3, (this.lineCenter + AndroidUtilities.dp(16.0f)) - AndroidUtilities.dp(68.0f));
            place(MusicPlayerSheet.this.timeView, iDp3, this.timesTop);
            place(MusicPlayerSheet.this.durationView, iDp4 - MusicPlayerSheet.this.durationView.getMeasuredWidth(), this.timesTop);
            int measuredHeight = this.transportTop + ((MusicPlayerSheet.this.controlsView.getMeasuredHeight() - iDp5) / 2);
            place(MusicPlayerSheet.this.shuffleButton, iDp3, measuredHeight);
            place(MusicPlayerSheet.this.repeatButton, i5, measuredHeight);
            place(MusicPlayerSheet.this.controlsView, iDp5 + iDp3 + AndroidUtilities.dp(8.0f), this.transportTop);
            this.modeTop = this.toolbarTop + ((AndroidUtilities.dp(56.0f) - AndroidUtilities.dp(48.0f)) / 2);
            layoutModeSegments();
            place(MusicPlayerSheet.this.speedButton, iDp3, this.toolbarTop + ((AndroidUtilities.dp(56.0f) - AndroidUtilities.dp(48.0f)) / 2));
            if (MusicPlayerSheet.this.speedHintView != null) {
                place(MusicPlayerSheet.this.speedHintView, this.paneLeft, 0);
            }
            place(MusicPlayerSheet.this.lyricsView, this.stageLeft, this.stageTop);
            place(MusicPlayerSheet.this.queueView, this.stageLeft, this.stageTop);
            MusicPlayerSheet.this.applyTransitions();
        }

        public void layoutModeSegments() {
            int iDp = (this.paneLeft + this.paneWidth) - AndroidUtilities.dp(20.0f);
            int iDp2 = AndroidUtilities.dp(48.0f);
            int iDp3 = AndroidUtilities.dp(64.0f) + Math.round(AndroidUtilities.dp(8.0f) * Math.max(0.0f, MusicPlayerSheet.this.lyricsBackground.getPressProgress()));
            int iDp4 = AndroidUtilities.dp(64.0f) + Math.round(AndroidUtilities.dp(8.0f) * Math.max(0.0f, MusicPlayerSheet.this.queueBackground.getPressProgress()));
            ImageView imageView = MusicPlayerSheet.this.queueButton;
            int i = iDp - iDp4;
            int i2 = this.modeTop;
            imageView.layout(i, i2, iDp, i2 + iDp2);
            int iDp5 = i - AndroidUtilities.dp(2.0f);
            int i3 = this.modeTop;
            MusicPlayerSheet.this.lyricsButton.layout(iDp5 - iDp3, i3, iDp5, iDp2 + i3);
        }

        private void place(View view, int i, int i2) {
            view.layout(i, i2, view.getMeasuredWidth() + i, view.getMeasuredHeight() + i2);
        }

        @Override
        public void onDraw(Canvas canvas) {
            if (!this.fullscreen) {
                MusicPlayerSheet.this.shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight() + AndroidUtilities.dp(24.0f));
                MusicPlayerSheet.this.shadowDrawable.draw(canvas);
                return;
            }
            float topRadius = getTopRadius();
            this.backgroundPaint.setColor(MusicPlayerSheet.this.colors.background);
            this.backgroundRect.set(MusicPlayerSheet.this.backgroundPaddingLeft, 0.0f, getMeasuredWidth() - MusicPlayerSheet.this.backgroundPaddingLeft, getMeasuredHeight() + topRadius);
            canvas.drawRoundRect(this.backgroundRect, topRadius, topRadius, this.backgroundPaint);
            if (MusicPlayerSheet.this.rhythmBackground.isVisible() && MusicPlayerSheet.this.rhythmBackground.draw(canvas, this.backgroundRect, topRadius, MusicPlayerSheet.this.coverView.getCoverRect(), MusicPlayerSheet.this.coverView.getCoverRadius(), 1.0f - MusicPlayerSheet.this.coverView.getHideProgress(), MusicPlayerSheet.this.coverView.getCompactProgress())) {
                postInvalidateOnAnimation();
            }
        }

        @Override
        public void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (MusicPlayerSheet.this.seekPlate <= 0.01f || !MusicPlayerSheet.this.seekSpeedDrawable.isShown()) {
                return;
            }
            int iDp = this.transportTop - AndroidUtilities.dp(8.0f);
            int iDp2 = iDp - AndroidUtilities.dp(37.0f);
            float fClamp01 = Utilities.clamp01(MusicPlayerSheet.this.seekPlate);
            float f = (MusicPlayerSheet.this.seekPlate * 0.4f) + 0.6f;
            canvas.save();
            canvas.translate(0.0f, AndroidUtilities.dp(15.0f) * (1.0f - MusicPlayerSheet.this.seekPlate));
            canvas.scale(f, f, this.paneLeft + (this.paneWidth / 2.0f), iDp);
            int i = this.paneLeft;
            canvas.saveLayerAlpha(i, iDp2, i + this.paneWidth, AndroidUtilities.dp(48.0f) + iDp2, (int) (fClamp01 * 255.0f));
            SeekSpeedDrawable seekSpeedDrawable = MusicPlayerSheet.this.seekSpeedDrawable;
            int i2 = this.paneLeft;
            seekSpeedDrawable.setBounds(i2, iDp2, this.paneWidth + i2, AndroidUtilities.dp(48.0f) + iDp2);
            MusicPlayerSheet.this.seekSpeedDrawable.draw(canvas);
            canvas.restore();
            canvas.restore();
        }

        @Override
        @SuppressLint({"ClickableViewAccessibility"})
        public boolean onTouchEvent(MotionEvent motionEvent) {
            return !MusicPlayerSheet.this.isDismissed() && super.onTouchEvent(motionEvent);
        }
    }
}

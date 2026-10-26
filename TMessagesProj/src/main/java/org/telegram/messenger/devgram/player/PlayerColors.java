package org.telegram.messenger.devgram.player;

import android.graphics.Paint;
import android.os.Build;
import android.util.SparseIntArray;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.devgram.player.PlayerConfig;
import org.telegram.messenger.devgram.utils.ui.ColorTones;
import org.telegram.messenger.devgram.utils.ui.MonetUtils;
import com.google.android.material.color.ColorRoles;
import com.google.android.material.color.MaterialColors;
import java.util.Arrays;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.WrappedResourceProvider;
public final class PlayerColors {
    public int accent;
    public int background;
    public int buffer;
    public boolean dark;
    public int divider;
    public boolean fromCover;
    public final int[] glow = new int[4];
    public int icon;
    public int onAccent;
    public int onTonal;
    public int surfaceContainer;
    public int text;
    public int textSecondary;
    public int tonal;
    public int track;
    public int wave;
    private static final double[] GLOW_HUE_SHIFT = {0.0d, 50.0d, -35.0d, 0.0d};
    private static final float[] GLOW_TONE_DARK = {42.0f, 48.0f, 36.0f, 60.0f};
    private static final float[] GLOW_TONE_LIGHT = {78.0f, 84.0f, 74.0f, 88.0f};
    private static final double[] GLOW_COVER_TONE_DARK = {36.0d, 60.0d};
    private static final double[] GLOW_COVER_TONE_LIGHT = {72.0d, 88.0d};

    public PlayerColors copy() {
        PlayerColors playerColors = new PlayerColors();
        playerColors.set(this);
        return playerColors;
    }

    public void set(PlayerColors playerColors) {
        this.background = playerColors.background;
        this.surfaceContainer = playerColors.surfaceContainer;
        this.text = playerColors.text;
        this.textSecondary = playerColors.textSecondary;
        this.icon = playerColors.icon;
        this.accent = playerColors.accent;
        this.onAccent = playerColors.onAccent;
        this.tonal = playerColors.tonal;
        this.onTonal = playerColors.onTonal;
        this.wave = playerColors.wave;
        this.track = playerColors.track;
        this.buffer = playerColors.buffer;
        this.divider = playerColors.divider;
        int[] iArr = playerColors.glow;
        int[] iArr2 = this.glow;
        System.arraycopy(iArr, 0, iArr2, 0, iArr2.length);
        this.dark = playerColors.dark;
        this.fromCover = playerColors.fromCover;
    }

    public void blend(PlayerColors playerColors, PlayerColors playerColors2, float f) {
        this.background = ColorUtils.blendARGB(playerColors.background, playerColors2.background, f);
        this.surfaceContainer = ColorUtils.blendARGB(playerColors.surfaceContainer, playerColors2.surfaceContainer, f);
        this.text = ColorUtils.blendARGB(playerColors.text, playerColors2.text, f);
        this.textSecondary = ColorUtils.blendARGB(playerColors.textSecondary, playerColors2.textSecondary, f);
        this.icon = ColorUtils.blendARGB(playerColors.icon, playerColors2.icon, f);
        this.accent = ColorUtils.blendARGB(playerColors.accent, playerColors2.accent, f);
        this.onAccent = ColorUtils.blendARGB(playerColors.onAccent, playerColors2.onAccent, f);
        this.tonal = ColorUtils.blendARGB(playerColors.tonal, playerColors2.tonal, f);
        this.onTonal = ColorUtils.blendARGB(playerColors.onTonal, playerColors2.onTonal, f);
        this.wave = ColorUtils.blendARGB(playerColors.wave, playerColors2.wave, f);
        this.track = ColorUtils.blendARGB(playerColors.track, playerColors2.track, f);
        this.buffer = ColorUtils.blendARGB(playerColors.buffer, playerColors2.buffer, f);
        this.divider = ColorUtils.blendARGB(playerColors.divider, playerColors2.divider, f);
        int i = 0;
        while (true) {
            int[] iArr = this.glow;
            if (i >= iArr.length) {
                break;
            }
            iArr[i] = ColorUtils.blendARGB(playerColors.glow[i], playerColors2.glow[i], f);
            i++;
        }
        this.dark = f < 0.5f ? playerColors.dark : playerColors2.dark;
        if (f >= 0.5f) {
            playerColors = playerColors2;
        }
        this.fromCover = playerColors.fromCover;
    }

    public boolean equalsTo(PlayerColors playerColors) {
        return playerColors != null && this.background == playerColors.background && this.surfaceContainer == playerColors.surfaceContainer && this.text == playerColors.text && this.textSecondary == playerColors.textSecondary && this.icon == playerColors.icon && this.accent == playerColors.accent && this.onAccent == playerColors.onAccent && this.tonal == playerColors.tonal && this.onTonal == playerColors.onTonal && this.wave == playerColors.wave && this.track == playerColors.track && this.buffer == playerColors.buffer && this.divider == playerColors.divider && Arrays.equals(this.glow, playerColors.glow) && this.dark == playerColors.dark;
    }

    public static PlayerColors fromTheme(Theme.ResourcesProvider resourcesProvider) {
        PlayerColors playerColors = new PlayerColors();
        int color = Theme.getColor(Theme.key_player_background, resourcesProvider);
        playerColors.background = color;
        playerColors.dark = !ColorTones.isLightSurface(color);
        playerColors.text = Theme.getColor(Theme.key_player_actionBarTitle, resourcesProvider);
        playerColors.textSecondary = Theme.getColor(Theme.key_player_time, resourcesProvider);
        playerColors.icon = Theme.getColor(Theme.key_player_button, resourcesProvider);
        playerColors.accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        playerColors.onAccent = Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider);
        if (Build.VERSION.SDK_INT >= 31 && false) {
            playerColors.onAccent = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);
            playerColors.tonal = MonetUtils.getSecondaryContainerColor(playerColors.dark);
            playerColors.onTonal = MonetUtils.getOnSecondaryContainerColor(playerColors.dark);
        } else {
            playerColors.tonal = ColorTones.withTone(playerColors.accent, playerColors.dark ? 30.0f : 90.0f, 24.0d);
            int i = playerColors.accent;
            boolean z = playerColors.dark;
            playerColors.onTonal = ColorTones.withTone(i, z ? 90.0f : 30.0f, z ? 24.0d : 40.0d);
        }
        playerColors.wave = Theme.getColor(Theme.key_player_progress, resourcesProvider);
        int color2 = Theme.getColor(Theme.key_player_progressBackground, resourcesProvider);
        playerColors.track = color2;
        playerColors.buffer = ColorUtils.blendARGB(color2, playerColors.wave, 0.35f);
        playerColors.surfaceContainer = ColorTones.withTone(playerColors.background, playerColors.dark ? 16.0f : 94.0f, 8.0d);
        playerColors.divider = ColorUtils.setAlphaComponent(playerColors.textSecondary, 36);
        playerColors.setGlow(playerColors.accent);
        return playerColors;
    }

    public static PlayerColors fromSeed(int i, boolean z) {
        PlayerColors playerColors = new PlayerColors();
        ColorRoles colorRoles = MaterialColors.getColorRoles(i, !z);
        playerColors.dark = z;
        playerColors.fromCover = true;
        playerColors.background = ColorTones.withTone(i, z ? 10.0f : 98.0f, 6.0d);
        playerColors.surfaceContainer = ColorTones.withTone(i, z ? 17.0f : 93.0f, 8.0d);
        playerColors.text = ColorTones.withTone(i, z ? 93.0f : 10.0f, 4.0d);
        playerColors.textSecondary = ColorTones.withTone(i, z ? 70.0f : 44.0f, 10.0d);
        playerColors.icon = ColorTones.withTone(i, z ? 90.0f : 14.0f, 8.0d);
        playerColors.accent = colorRoles.getAccent();
        playerColors.onAccent = colorRoles.getOnAccent();
        playerColors.tonal = ColorTones.withTone(i, z ? 30.0f : 90.0f, 24.0d);
        playerColors.onTonal = ColorTones.withTone(i, z ? 90.0f : 30.0f, z ? 24.0d : 40.0d);
        playerColors.wave = colorRoles.getAccent();
        int iWithTone = ColorTones.withTone(i, z ? 24.0f : 90.0f, 16.0d);
        playerColors.track = iWithTone;
        playerColors.buffer = ColorUtils.blendARGB(iWithTone, playerColors.wave, 0.35f);
        playerColors.divider = ColorUtils.setAlphaComponent(playerColors.textSecondary, 36);
        playerColors.setGlow(i);
        return playerColors;
    }

    private void setGlow(int i) {
        double[] dArr = new double[3];
        ColorUtils.colorToLAB(i, dArr);
        double dMin = Math.min(Math.hypot(dArr[1], dArr[2]), 70.0d);
        double dAtan2 = Math.atan2(dArr[2], dArr[1]);
        for (int i2 = 0; i2 < this.glow.length; i2++) {
            double radians = Math.toRadians(GLOW_HUE_SHIFT[i2]) + dAtan2;
            this.glow[i2] = ColorUtils.LABToColor(this.dark ? GLOW_TONE_DARK[i2] : GLOW_TONE_LIGHT[i2], Math.cos(radians) * dMin, dMin * Math.sin(radians)) | (-16777216);
        }
    }

    public void setCoverGlow(int[] iArr) {
        double[] dArr = new double[3];
        double[] dArr2 = this.dark ? GLOW_COVER_TONE_DARK : GLOW_COVER_TONE_LIGHT;
        for (int i = 0; i < this.glow.length && i < iArr.length; i++) {
            ColorUtils.colorToLAB(iArr[i], dArr);
            double dHypot = Math.hypot(dArr[1], dArr[2]);
            double d = dHypot > 70.0d ? 70.0d / dHypot : 1.0d;
            this.glow[i] = ColorUtils.LABToColor(Math.max(dArr2[0], Math.min(dArr2[1], dArr[0])), dArr[1] * d, dArr[2] * d) | (-16777216);
        }
    }

    public static int getMiniPlayerAccent(int i, Theme.ResourcesProvider resourcesProvider) {
        Integer miniPlayerSeed = getMiniPlayerSeed();
        return miniPlayerSeed == null ? i : MaterialColors.getColorRoles(miniPlayerSeed.intValue(), !isDarkSurface(resourcesProvider)).getAccent();
    }

    public static int getMiniPlayerText(int i, Theme.ResourcesProvider resourcesProvider) {
        Integer miniPlayerSeed = getMiniPlayerSeed();
        if (miniPlayerSeed == null) {
            return i;
        }
        return ColorTones.withTone(miniPlayerSeed.intValue(), isDarkSurface(resourcesProvider) ? 93.0f : 10.0f, 4.0d);
    }

    public static int getMiniPlayerSecondary(int i, Theme.ResourcesProvider resourcesProvider) {
        Integer miniPlayerSeed = getMiniPlayerSeed();
        if (miniPlayerSeed == null) {
            return i;
        }
        return ColorTones.withTone(miniPlayerSeed.intValue(), isDarkSurface(resourcesProvider) ? 70.0f : 44.0f, 10.0d);
    }

    private static Integer getMiniPlayerSeed() {
        MessageObject playingMessageObject;
        Integer cachedSeed;
        if (!PlayerConfig.getPlayerMiniCoverColors() || (playingMessageObject = MediaController.getInstance().getPlayingMessageObject()) == null || playingMessageObject.isVoice() || playingMessageObject.isRoundVideo() || (cachedSeed = CoverColorExtractor.getCachedSeed(playingMessageObject)) == null || cachedSeed.intValue() == 0) {
            return null;
        }
        return cachedSeed;
    }

    private static boolean isDarkSurface(Theme.ResourcesProvider resourcesProvider) {
        return !ColorTones.isLightSurface(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
    }

    public Theme.ResourcesProvider wrap(Theme.ResourcesProvider resourcesProvider) {
        return !this.fromCover ? resourcesProvider : new CoverResourcesProvider(resourcesProvider, buildOverrides(), this.dark);
    }

    public Theme.ResourcesProvider wrapSettings(Theme.ResourcesProvider resourcesProvider) {
        if (!this.fromCover) {
            return new CoverResourcesProvider(resourcesProvider, new SparseIntArray(), this.dark);
        }
        SparseIntArray sparseIntArrayBuildOverrides = buildOverrides();
        int i = Theme.key_windowBackgroundGray;
        int color = Theme.getColor(i, resourcesProvider);
        int i2 = Theme.key_windowBackgroundWhite;
        float tone = ColorTones.getTone(Theme.getColor(i2, resourcesProvider));
        float tone2 = ColorTones.getTone(color);
        boolean z = this.dark;
        boolean z2 = z && color == -16777216;
        float fMax = z ? Math.max(tone, tone2 + 5.0f) : tone;
        if (!this.dark) {
            tone2 = Math.min(tone2, tone - 5.0f);
        }
        int iWithTone = ColorTones.withTone(this.background, fMax, 6.0d);
        int iWithTone2 = z2 ? -16777216 : ColorTones.withTone(this.background, tone2, 6.0d);
        sparseIntArrayBuildOverrides.put(Theme.key_dialogBackground, z2 ? iWithTone2 : iWithTone);
        sparseIntArrayBuildOverrides.put(i, iWithTone2);
        sparseIntArrayBuildOverrides.put(i2, iWithTone);
        sparseIntArrayBuildOverrides.put(Theme.key_windowBackgroundWhiteBlueHeader, this.accent);
        sparseIntArrayBuildOverrides.put(Theme.key_windowBackgroundWhiteBlueText, this.accent);
        sparseIntArrayBuildOverrides.put(Theme.key_windowBackgroundWhiteGrayIcon, this.textSecondary);
        sparseIntArrayBuildOverrides.put(Theme.key_windowBackgroundWhiteGrayText4, this.textSecondary);
        sparseIntArrayBuildOverrides.put(Theme.key_windowBackgroundWhiteLinkText, this.accent);
        sparseIntArrayBuildOverrides.put(Theme.key_windowBackgroundWhiteValueText, this.accent);
        sparseIntArrayBuildOverrides.put(Theme.key_switchTrack, this.dark ? this.textSecondary : ColorTones.withTone(this.background, 78.0f, 12.0d));
        sparseIntArrayBuildOverrides.put(Theme.key_switchTrackChecked, this.accent);
        sparseIntArrayBuildOverrides.put(Theme.key_switch2TrackChecked, this.accent);
        sparseIntArrayBuildOverrides.put(Theme.key_radioBackground, this.textSecondary);
        sparseIntArrayBuildOverrides.put(Theme.key_checkboxCheck, this.onAccent);
        sparseIntArrayBuildOverrides.put(Theme.key_switchTrackBlueThumbChecked, this.onAccent);
        sparseIntArrayBuildOverrides.put(Theme.key_switchTrackBlueSelector, ColorUtils.setAlphaComponent(this.text, 20));
        sparseIntArrayBuildOverrides.put(Theme.key_switchTrackBlueSelectorChecked, ColorUtils.setAlphaComponent(this.accent, 36));
        sparseIntArrayBuildOverrides.put(Theme.key_settings_listSelector, ColorUtils.setAlphaComponent(this.text, 20));
        sparseIntArrayBuildOverrides.put(Theme.key_sheet_scrollUp, ColorUtils.setAlphaComponent(this.textSecondary, 96));
        return new CoverResourcesProvider(resourcesProvider, sparseIntArrayBuildOverrides, this.dark);
    }

    private SparseIntArray buildOverrides() {
        SparseIntArray sparseIntArray = new SparseIntArray();
        sparseIntArray.put(Theme.key_dialogBackground, this.background);
        sparseIntArray.put(Theme.key_windowBackgroundWhite, this.background);
        sparseIntArray.put(Theme.key_player_background, this.background);
        sparseIntArray.put(Theme.key_dialogTextBlack, this.text);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteBlackText, this.text);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteGrayText, this.textSecondary);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteGrayText2, this.textSecondary);
        sparseIntArray.put(Theme.key_player_actionBarTitle, this.text);
        sparseIntArray.put(Theme.key_player_time, this.textSecondary);
        sparseIntArray.put(Theme.key_player_button, this.icon);
        sparseIntArray.put(Theme.key_player_buttonActive, this.accent);
        sparseIntArray.put(Theme.key_player_progress, this.wave);
        sparseIntArray.put(Theme.key_player_progressBackground, this.track);
        sparseIntArray.put(Theme.key_featuredStickers_addButton, this.accent);
        sparseIntArray.put(Theme.key_featuredStickers_addButtonPressed, this.accent);
        sparseIntArray.put(Theme.key_featuredStickers_buttonText, this.onAccent);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuBackground, this.surfaceContainer);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuItem, this.text);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuItemIcon, this.textSecondary);
        sparseIntArray.put(Theme.key_groupcreate_sectionText, this.textSecondary);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuSeparator, ColorUtils.setAlphaComponent(this.textSecondary, 24));
        sparseIntArray.put(Theme.key_listSelector, ColorUtils.setAlphaComponent(this.text, 20));
        sparseIntArray.put(Theme.key_dialogButtonSelector, ColorUtils.setAlphaComponent(this.text, 20));
        sparseIntArray.put(Theme.key_divider, this.divider);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteBlueText4, this.accent);
        sparseIntArray.put(Theme.key_chat_inLoader, this.accent);
        sparseIntArray.put(Theme.key_chat_inMediaIcon, this.onAccent);
        sparseIntArray.put(Theme.key_chat_inLoaderSelected, this.accent);
        sparseIntArray.put(Theme.key_chat_inMediaIconSelected, this.onAccent);
        sparseIntArray.put(Theme.key_chat_inAudioProgress, this.background);
        sparseIntArray.put(Theme.key_chat_inAudioSelectedProgress, this.background);
        sparseIntArray.put(Theme.key_dialogEmptyImage, this.textSecondary);
        sparseIntArray.put(Theme.key_dialogEmptyText, this.textSecondary);
        sparseIntArray.put(Theme.key_emptyListPlaceholder, this.textSecondary);
        sparseIntArray.put(Theme.key_progressCircle, this.accent);
        sparseIntArray.put(Theme.key_dialogSearchBackground, this.surfaceContainer);
        sparseIntArray.put(Theme.key_dialogSearchHint, this.textSecondary);
        sparseIntArray.put(Theme.key_dialogSearchIcon, this.textSecondary);
        sparseIntArray.put(Theme.key_dialogSearchText, this.text);
        int iWithTone = ColorTones.withTone(-1754827, this.dark ? 68.0f : 46.0f, 80.0d);
        sparseIntArray.put(Theme.key_text_RedRegular, iWithTone);
        sparseIntArray.put(Theme.key_text_RedBold, iWithTone);
        return sparseIntArray;
    }

    public static final class CoverResourcesProvider extends WrappedResourceProvider {
        private final Theme.ResourcesProvider base;
        private final boolean dark;
        private final Paint dividerPaint;

        public CoverResourcesProvider(Theme.ResourcesProvider resourcesProvider, SparseIntArray sparseIntArray, boolean z) {
            super(resourcesProvider);
            this.dividerPaint = new Paint();
            this.base = resourcesProvider;
            this.dark = z;
            this.sparseIntArray = sparseIntArray;
        }

        @Override
        public int getColorOrDefault(int i) {
            int iIndexOfKey = this.sparseIntArray.indexOfKey(i);
            return iIndexOfKey >= 0 ? this.sparseIntArray.valueAt(iIndexOfKey) : super.getColorOrDefault(i);
        }

        @Override
        public int getCurrentColor(int i) {
            int iIndexOfKey = this.sparseIntArray.indexOfKey(i);
            return iIndexOfKey >= 0 ? this.sparseIntArray.valueAt(iIndexOfKey) : super.getCurrentColor(i);
        }

        @Override
        public Paint getPaint(String str) {
            if ("paintDivider".equals(str)) {
                this.dividerPaint.setColor(Theme.getColor(Theme.key_divider, this));
                return this.dividerPaint;
            }
            return super.getPaint(str);
        }

        @Override
        public boolean isDark() {
            return this.dark;
        }

    }
}

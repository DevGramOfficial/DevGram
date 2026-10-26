package org.telegram.messenger.devgram.player;

import android.content.Context;
import org.telegram.messenger.devgram.player.PlayerConfig;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;
public abstract class PlayerLauncher {
    public static BottomSheet create(Context context, Theme.ResourcesProvider resourcesProvider) {
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (PlayerConfig.getNewPlayerStyle() && (playingMessageObject == null || playingMessageObject.isMusic())) {
            return new MusicPlayerSheet(context, resourcesProvider);
        }
        return new AudioPlayerAlert(context, resourcesProvider);
    }

    public static void onPlaybackOrderChanged() {
        MusicPlayerSheet musicPlayerSheet = MusicPlayerSheet.instance;
        if (musicPlayerSheet != null) {
            musicPlayerSheet.updateOrderButtons(true);
        }
    }
}

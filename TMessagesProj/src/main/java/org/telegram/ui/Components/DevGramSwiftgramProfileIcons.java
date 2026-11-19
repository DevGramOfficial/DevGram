package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramSwiftgramDesign;
import org.telegram.messenger.R;

import static org.telegram.ui.Components.ProfileActionsView.*;

/** Original PeerInfoHeaderButtonNode template artwork and animations, independent of Android profile icons. */
public final class DevGramSwiftgramProfileIcons {
    private static final class Icon { int resource, color; PorterDuffColorFilter filter; Drawable drawable; RLottieDrawable animation; }
    private final SparseArray<Icon> icons = new SparseArray<>();
    private final View owner;
    public DevGramSwiftgramProfileIcons(View owner) { this.owner = owner; }

    public void draw(Canvas canvas, int key, boolean notifications, boolean liked, float cx, float top, int color, float alpha, boolean play) {
        int raw = 0, resource;
        switch (key) {
            case KEY_MESSAGE: resource = R.drawable.swiftgram_profile_message; break;
            case KEY_NOTIFICATION: resource = 0; raw = notifications ? R.raw.swiftgram_anim_profileunmute : R.raw.swiftgram_anim_profilemute; break;
            case KEY_CALL: resource = R.drawable.swiftgram_profile_call; break;
            case KEY_VIDEO: resource = R.drawable.swiftgram_profile_video; break;
            case KEY_VOICE_CHAT:
            case KEY_STREAM: resource = 0; raw = R.raw.swiftgram_anim_profilevc; break;
            case KEY_MORE: resource = 0; raw = R.raw.swiftgram_anim_profilemore; break;
            case KEY_LEAVE: resource = 0; raw = R.raw.swiftgram_anim_profileleave; break;
            case KEY_STOP: resource = R.drawable.swiftgram_profile_stop; break;
            case KEY_JOIN: resource = R.drawable.swiftgram_profile_addmember; break;
            case KEY_DISCUSS: resource = R.drawable.swiftgram_msg_discussion; break;
            case KEY_GIFT: resource = R.drawable.swiftgram_settings_gift; break;
            case KEY_SHARE: resource = R.drawable.swiftgram_msg_share; break;
            case KEY_REPORT: resource = R.drawable.swiftgram_msg_report; break;
            case KEY_STORY: resource = R.drawable.swiftgram_msg_premium_liststar; break;
            case KEY_SET_PHOTO: resource = R.drawable.swiftgram_msg_camera; break;
            case KEY_EDIT_USERNAME: resource = R.drawable.swiftgram_msg_mention; break;
            case KEY_EDIT_INFO: resource = R.drawable.swiftgram_msg_edit; break;
            case KEY_SETTINGS: resource = R.drawable.swiftgram_msg_settings; break;
            case KEY_PROFILE_LIKE: resource = liked ? R.drawable.media_like_active : R.drawable.swiftgram_msg_reactions; break;
            default: return;
        }
        int id = raw != 0 ? raw : resource;
        Icon icon = icons.get(key);
        if (icon == null || icon.resource != id) {
            if (icon != null && icon.animation != null) icon.animation.recycle(true);
            icon = new Icon(); icon.resource = id;
            if (raw != 0) {
                icon.animation = new RLottieDrawable(raw, AndroidUtilities.dp(40), AndroidUtilities.dp(40), false, null);
                icon.animation.setMasterParent(owner);
                icon.animation.setAutoRepeat(0);
                icon.animation.setAllowDecodeSingleFrame(true);
                icon.animation.setCurrentFrame(key == KEY_NOTIFICATION ? icon.animation.getFramesCount() - 1 : 0, false);
            } else icon.drawable = owner.getResources().getDrawable(resource).mutate();
            icons.put(key, icon);
        }
        int size = AndroidUtilities.dp(40), x = Math.round(cx - size / 2f), y = Math.round(top);
        if (icon.filter == null || icon.color != color) { icon.color = color; icon.filter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN); }
        if (icon.animation != null) {
            icon.animation.setBounds(x, y, x + size, y + size);
            icon.animation.setColorFilter(icon.filter);
            icon.animation.setAlpha(Math.round(255 * alpha));
            if (play && DevGramSwiftgramDesign.section("animations")) { icon.animation.setCurrentFrame(0); icon.animation.start(); }
            icon.animation.draw(canvas);
        } else {
            int width = Math.min(AndroidUtilities.dp(28), icon.drawable.getIntrinsicWidth()), height = Math.min(AndroidUtilities.dp(28), icon.drawable.getIntrinsicHeight());
            icon.drawable.setBounds(Math.round(cx - width / 2f), y + (size - height) / 2, Math.round(cx + width / 2f), y + (size + height) / 2);
            icon.drawable.setColorFilter(icon.filter);
            icon.drawable.setAlpha(Math.round(255 * alpha)); icon.drawable.draw(canvas);
        }
    }

    public void release() { for (int i = 0; i < icons.size(); i++) if (icons.valueAt(i).animation != null) icons.valueAt(i).animation.recycle(true); icons.clear(); }
}

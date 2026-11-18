package org.telegram.ui.Components;

import org.json.JSONObject;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramProfileCustomization;
import org.telegram.messenger.DevGramProfilePolicy;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.DevGramAppearanceAccess;
import org.telegram.ui.DevGramStudioActivity;

/** One serialized profile vote. Requests belong to this screen and its signed-in viewer. */
public final class DevGramProfileLikesController {
    private final BaseFragment host;
    private final int account;
    private final long viewer, profile;
    private final Runnable changed;
    private final Runnable policyChanged = this::onPolicyChanged;
    private boolean active = true, liked, busy, loading, known;
    private int likes, generation;
    private long refreshed;
    private boolean permitted;

    public DevGramProfileLikesController(BaseFragment host, long profile, Runnable changed) {
        this.host = host;
        this.account = host.getCurrentAccount();
        this.viewer = UserConfig.getInstance(account).getClientUserId();
        this.profile = profile;
        this.changed = changed;
        permitted = allowed();
        DevGramProfilePolicy.listen(policyChanged);
    }
    public int count() { return known ? likes : -1; }
    public boolean liked() { return liked; }
    public boolean busy() { return busy || loading; }
    private boolean current() {
        return active && DevGramProfileCustomization.enabled()
                && UserConfig.getInstance(account).getClientUserId() == viewer;
    }
    private boolean allowed() {
        return current() && DevGramProfilePolicy.allowed(viewer, "workshop.social");
    }
    private void onPolicyChanged() {
        boolean next = allowed();
        if (permitted == next && current()) return;
        permitted = next;
        generation++;
        busy = loading = known = false;
        refreshed = 0;
        changed.run();
        if (next) refresh();
    }
    public void release() {
        active = false;
        generation++;
        DevGramProfilePolicy.unlisten(policyChanged);
    }
    private void accept(JSONObject result) {
        likes = Math.max(0, result.optInt("likes"));
        liked = result.optBoolean("liked");
        known = true;
        changed.run();
    }
    public void refresh() {
        if (!current() || busy || loading || System.currentTimeMillis() - refreshed < 15000) return;
        refreshed = System.currentTimeMillis();
        loading = true;
        int request = ++generation;
        changed.run();
        DevGramProfilePolicy.request(account, "studio/likes",
                DevGramStudioActivity.json("profile", profile, "action", "get"), (result, error) -> {
            if (!current() || request != generation) return;
            loading = false;
            if (result != null) accept(result);
            else changed.run();
        });
    }
    public void toggle() {
        if (!current() || busy || loading || !DevGramAppearanceAccess.check(host, "workshop.social")) return;
        if (!known) { refreshed = 0; refresh(); return; }
        final int previousCount = likes;
        final boolean previousVote = liked;
        final int request = ++generation;
        liked = !liked;
        likes = Math.max(0, likes + (liked ? 1 : -1));
        busy = true;
        changed.run();
        DevGramProfilePolicy.request(account, "studio/likes",
                DevGramStudioActivity.json("profile", profile, "action", "set", "liked", liked), (result, error) -> {
            if (!current() || request != generation) return;
            busy = false;
            if (result != null) accept(result);
            else {
                likes = previousCount;
                liked = previousVote;
                changed.run();
                if (host.getParentActivity() != null) {
                    BulletinFactory.of(host).createErrorBulletin(DevGramLocale.text("Не удалось изменить лайк")).show();
                }
            }
        });
    }
}

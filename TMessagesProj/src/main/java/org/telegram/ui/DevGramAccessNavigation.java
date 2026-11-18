package org.telegram.ui;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Removes revoked editors without closing a different screen or reentering onResume. */
public final class DevGramAccessNavigation {
    private static final Set<BaseFragment> pending = Collections.newSetFromMap(new WeakHashMap<>());

    private DevGramAccessNavigation() {}

    public static void close(BaseFragment fragment) {
        if (fragment.getParentLayout() == null || !pending.add(fragment)) return;
        // Handler.post must defer even on the UI thread. runOnUIThread can execute
        // inline while ActionBarLayout is still resuming the previous fragment.
        ApplicationLoader.applicationHandler.post(new Runnable() {
            @Override public void run() {
                INavigationLayout layout = fragment.getParentLayout();
                if (layout == null || !layout.getFragmentStack().contains(fragment)) {
                    pending.remove(fragment);
                    return;
                }
                if (layout.checkTransitionAnimation()) {
                    ApplicationLoader.applicationHandler.postDelayed(this, 50);
                    return;
                }
                pending.remove(fragment);
                if (layout.getLastFragment() == fragment) fragment.finishFragment(false);
                else fragment.removeSelfFromStack(true);
            }
        });
    }
}

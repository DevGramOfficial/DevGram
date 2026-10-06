package org.telegram.ui;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.DevGramSettingsLayout;

/** Base for DevGram settings screens with exteraGram-style edge-to-edge glass headers. */
public abstract class DevGramSettingsFragment extends BaseFragment {
    @Override
    @Deprecated
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        if (fragmentView instanceof DevGramSettingsLayout) {
            ((DevGramSettingsLayout) fragmentView).setInsets(top, bottom);
        }
    }
}

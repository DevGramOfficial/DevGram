package org.telegram.ui;

import android.os.Bundle;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.DevGramCommunityProfiles;

/** Explicit entry into in-place editing, separate from the customization settings screen. */
public final class DevGramProfileEditActivity extends ProfileActivity {
    public DevGramProfileEditActivity(DevGramStudioActivity.Draft draft,boolean guide,int account){super(arguments(draft,guide,account));setCurrentAccount(account);devgramIncomingDraft=draft;}
    @Override public boolean onFragmentCreate(){return org.telegram.messenger.DevGramProfileCustomization.enabled()&&super.onFragmentCreate();}
    private static Bundle arguments(DevGramStudioActivity.Draft draft,boolean guide,int account){Bundle args=new Bundle();if(draft!=null&&draft.subject<0)args.putLong("chat_id",DevGramCommunityProfiles.chatId(draft.subject));else{args.putLong("user_id",UserConfig.getInstance(account).getClientUserId());args.putBoolean("my_profile",true);}args.putBoolean("devgram_edit",true);args.putBoolean("devgram_guide",guide);return args;}
}

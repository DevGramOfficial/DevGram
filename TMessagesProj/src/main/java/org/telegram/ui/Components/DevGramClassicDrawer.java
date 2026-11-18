package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.devgram.icons.IconManager;
import org.telegram.messenger.forkgram.HiddenAccountHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.*;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.SimpleTextView;

/** The reference's 320dp side navigation, account header and 48dp menu rows. */
public final class DevGramClassicDrawer extends FrameLayout implements Theme.Colorable {
    private final MainTabsActivity host;
    private final ScrollView panel;
    private final LinearLayout items;
    private final Paint scrim = new Paint();
    private ValueAnimator animator, accountsAnimator;
    private LinearLayout accountRows;
    private TextView accountsToggleText;
    private View accountsToggleIcon;
    private int accountCount;
    private float progress, downX, downY, startProgress;
    private boolean tracking, edge, accountsShown, accountsExpanded;
    private int panelWidth;
    private int headerColor;
    private final int touchSlop;

    public DevGramClassicDrawer(MainTabsActivity host) {
        super(host.getParentActivity());
        this.host = host;
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        setWillNotDraw(false);
        setVisibility(INVISIBLE);
        panel = new ScrollView(getContext());
        panel.setFillViewport(true);
        panel.setVerticalScrollBarEnabled(false);
        panel.setElevation(dp(8));
        items = new LinearLayout(getContext());
        items.setOrientation(LinearLayout.VERTICAL);
        panel.addView(items, new ScrollView.LayoutParams(-1, -2));
        addView(panel, LayoutHelper.createFrame(DevGramClassicDesign.DRAWER_WIDTH, -1, Gravity.LEFT));
        java.util.ArrayList<Integer> visibleAccounts = new java.util.ArrayList<>();
        HiddenAccountHelper.collectVisibleAccountNumbers(visibleAccounts);
        accountsShown = visibleAccounts.size() > 1;
        rebuild();
    }

    private String text(int res) { return LocaleController.getString(res); }
    private int color(int key) { return Theme.getColor(key, host.getResourceProvider()); }
    private TextView label(String value, int size, int key, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value); view.setTextSize(size); view.setTextColor(color(key));
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) view.setTypeface(AndroidUtilities.bold());
        return view;
    }

    private void rebuild() {
        cancelAccountsAnimation();
        accountRows = null;
        accountsToggleText = null;
        accountsToggleIcon = null;
        items.removeAllViews();
        items.setBackgroundColor(color(Theme.key_chats_menuBackground));
        boolean themeHeader = Theme.hasThemeKey(Theme.key_chats_menuTopBackground)
                && color(Theme.key_chats_menuTopBackground) != 0;
        FrameLayout header = new FrameLayout(getContext()) {
            private final Rect source = new Rect(), destination = new Rect();
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                Drawable wallpaper = Theme.getCachedWallpaper();
                if (!themeHeader && Theme.isCustomTheme() && !Theme.isPatternWallpaper() && wallpaper instanceof BitmapDrawable) {
                    android.graphics.Bitmap bitmap = ((BitmapDrawable)wallpaper).getBitmap();
                    if (bitmap.isRecycled() || getWidth()==0 || getHeight()==0) return;
                    float scale = Math.max(getWidth()/(float)bitmap.getWidth(), getHeight()/(float)bitmap.getHeight());
                    int width = (int)(getWidth()/scale), height = (int)(getHeight()/scale);
                    source.set((bitmap.getWidth()-width)/2, (bitmap.getHeight()-height)/2, (bitmap.getWidth()+width)/2, (bitmap.getHeight()+height)/2);
                    destination.set(0,0,getWidth(),getHeight());
                    canvas.drawBitmap(bitmap,source,destination,null);
                }
            }
        };
        header.setWillNotDraw(false);
        headerColor = color(themeHeader ? Theme.key_chats_menuTopBackground : Theme.key_chats_menuTopBackgroundCats);
        header.setBackgroundColor(headerColor);
        items.addView(header, new LinearLayout.LayoutParams(-1, dp(DevGramClassicDesign.DRAWER_HEADER_HEIGHT) + AndroidUtilities.statusBarHeight));
        TLRPC.User user = UserConfig.getInstance(host.getCurrentAccount()).getCurrentUser();
        BackupImageView avatar = new BackupImageView(getContext());
        avatar.setRoundRadius(dp(32));
        avatar.setForUserOrChat(user, new AvatarDrawable(user));
        header.addView(avatar, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.TOP, 16, 17 + AndroidUtilities.statusBarHeight / AndroidUtilities.density, 0, 0));
        avatar.setOnClickListener(v -> open(profile(true)));
        SimpleTextView name = new SimpleTextView(getContext()) {
            private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable status = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, dp(20));
            {
                if (user != null && user.premium) {
                    long emoji = DialogObject.getEmojiStatusDocumentId(user.emoji_status);
                    if (emoji != 0) status.set(emoji, false);
                    else status.set(getContext().getDrawable(R.drawable.msg_premium_liststar).mutate(), false);
                    status.setColor(color(Theme.key_chats_menuName));setRightDrawable(status);setDrawablePadding(dp(6));
                    setRightDrawableOnClick(view -> showStatusPicker());
                }
            }
            @Override protected void onAttachedToWindow() { super.onAttachedToWindow();if(isShown())status.attach(); }
            @Override public void onVisibilityAggregated(boolean visible) { super.onVisibilityAggregated(visible);if(visible)status.attach();else status.detach(); }
            @Override protected void onDetachedFromWindow() { status.detach();super.onDetachedFromWindow(); }
        };
        name.setText(UserObject.getUserName(user));name.setTextSize(15);name.setTypeface(AndroidUtilities.bold());name.setTextColor(color(Theme.key_chats_menuName));name.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        header.addView(name, LayoutHelper.createFrame(-1, 24, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 52, 30));
        String phone = user == null || TextUtils.isEmpty(user.phone) || SharedConfig.hideSensitivePhone() ? "" : PhoneFormat.getInstance().format("+" + user.phone);
        TextView phoneView = label(phone, 13, themeHeader ? Theme.key_chats_menuPhone : Theme.key_chats_menuPhoneCats, false);
        header.addView(phoneView, LayoutHelper.createFrame(-1, 22, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 52, 9));
        ImageView accounts = new ImageView(getContext());
        accounts.setImageResource(R.drawable.msg_expand); accounts.setRotation(accountsShown ? 180 : 0);
        accounts.setColorFilter(color(Theme.key_chats_menuName)); accounts.setScaleType(ImageView.ScaleType.CENTER);
        accounts.setContentDescription(text(R.string.SettingsAccounts));
        header.addView(accounts, LayoutHelper.createFrame(59, 59, Gravity.RIGHT | Gravity.BOTTOM));
        accounts.setOnClickListener(v -> { accountsShown = !accountsShown; rebuild(); });
        header.setOnClickListener(v -> { accountsShown = !accountsShown; rebuild(); });
        RLottieImageView night = new RLottieImageView(getContext());
        night.setAnimation(R.raw.sun, 28, 28);
        night.setColorFilter(color(Theme.key_chats_menuName));
        night.setContentDescription(text(Theme.isCurrentThemeDark() ? R.string.AccDescrSwitchToDayTheme : R.string.AccDescrSwitchToNightTheme));
        header.addView(night, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 6 + AndroidUtilities.statusBarHeight / AndroidUtilities.density, 8, 0));
        night.setOnClickListener(v -> switchTheme(night));
        night.setOnLongClickListener(v -> { open(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));return true; });

        if (accountsShown) {
            java.util.ArrayList<Integer> visibleAccounts = new java.util.ArrayList<>();
            HiddenAccountHelper.collectVisibleAccountNumbers(visibleAccounts);
            visibleAccounts.removeIf(a -> !UserConfig.getInstance(a).isClientActivated());
            accountCount = visibleAccounts.size();
            accountRows = new LinearLayout(getContext());
            accountRows.setOrientation(LinearLayout.VERTICAL);
            accountRows.setClipChildren(true);
            for (int i = 0; i < accountCount; i++) {
                View row = accountView(visibleAccounts.get(i));
                row.setVisibility(i < 3 || accountsExpanded ? VISIBLE : GONE);
                accountRows.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
            }
            items.addView(accountRows, new LinearLayout.LayoutParams(-1, -2));
            if (accountCount > 3) {
                FrameLayout toggle = add(R.drawable.msg_expand, accountsToggleCaption(), this::toggleAccountsExpansion);
                accountsToggleIcon = toggle.getChildAt(0);
                accountsToggleText = (TextView) toggle.getChildAt(1);
                accountsToggleIcon.setRotation(accountsExpanded ? 180 : 0);
            }
            if (UserConfig.getActivatedAccountsCount() < UserConfig.MAX_ACCOUNT_COUNT) add(R.drawable.msg_add, text(R.string.AddAccount), () -> {
                for (int a=0;a<UserConfig.MAX_ACCOUNT_COUNT;a++) if (!UserConfig.getInstance(a).isClientActivated()) { open(new LoginActivity(a)); break; }
            });
            divider();
        }
        add(R.drawable.devgram_classic_left_status_profile, text(R.string.MyProfile), () -> open(profile(true)));
        if (user != null && user.premium) add(DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0 ? R.drawable.devgram_classic_msg_status_edit : R.drawable.devgram_classic_msg_status_set,
                text(DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0 ? R.string.ChangeEmojiStatus : R.string.SetEmojiStatus), this::showStatusPicker);
        divider();
        add(R.drawable.devgram_classic_msg_groups, text(R.string.NewGroup), () -> open(new GroupCreateActivity(new Bundle())));
        add(R.drawable.devgram_classic_msg_secret, text(R.string.NewSecretChat), () -> { Bundle args=new Bundle();args.putBoolean("createSecretChat",true);open(new ContactsActivity(args)); });
        add(R.drawable.devgram_classic_msg_channel, text(R.string.NewChannel), () -> { Bundle args=new Bundle();args.putInt("step",0);open(new ChannelCreateActivity(args)); });
        add(R.drawable.devgram_classic_msg_contacts, text(R.string.Contacts), () -> open(new ContactsActivity(new Bundle())));
        add(R.drawable.devgram_classic_msg_calls, text(R.string.Calls), () -> open(new CallLogActivity()));
        add(R.drawable.devgram_classic_msg_saved, text(R.string.SavedMessages), () -> { Bundle args=new Bundle();args.putLong("user_id",UserConfig.getInstance(host.getCurrentAccount()).getClientUserId());open(new ChatActivity(args)); });
        add(R.drawable.devgram_classic_msg_archive, text(R.string.ArchivedChats), () -> { Bundle args=new Bundle();args.putInt("folderId",1);open(new DialogsActivity(args)); });
        add(R.drawable.devgram_classic_msg_settings_old, text(R.string.Settings), () -> open(new SettingsActivity()));
        divider();
        add(R.drawable.devgram_classic_msg_contacts, text(R.string.InviteFriends), () -> open(new InviteContactsActivity()));
        add(R.drawable.devgram_classic_msg_help, text(R.string.TelegramFaq), () -> close(() -> Browser.openUrl(getContext(), text(R.string.TelegramFaqUrl))));
        TextView version = label("DevGram " + BuildConfig.VERSION_NAME, 12, Theme.key_windowBackgroundWhiteGrayText, false);
        version.setPadding(dp(16), 0, dp(16), AndroidUtilities.navigationBarHeight);
        items.addView(version, new LinearLayout.LayoutParams(-1, dp(48) + AndroidUtilities.navigationBarHeight));
    }

    private String accountsToggleCaption() {
        return text(accountsExpanded ? R.string.ShowLess : R.string.ShowMore)
                + (accountsExpanded ? "" : " · " + (accountCount - 3));
    }

    private void cancelAccountsAnimation() {
        if (accountsAnimator != null) {
            accountsAnimator.cancel();
            accountsAnimator = null;
        }
        if (accountsToggleIcon != null) accountsToggleIcon.animate().cancel();
    }

    private void toggleAccountsExpansion() {
        if (accountRows == null || accountCount <= 3) return;
        int fromHeight = accountRows.getHeight();
        cancelAccountsAnimation();
        accountsExpanded = !accountsExpanded;
        accountsToggleText.setText(accountsToggleCaption());
        accountsToggleIcon.animate().rotation(accountsExpanded ? 180 : 0)
                .setDuration(240).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) accountRows.getLayoutParams();
        params.height = fromHeight;
        accountRows.setLayoutParams(params);
        for (int i = 3; i < accountCount; i++) accountRows.getChildAt(i).setVisibility(VISIBLE);
        int toHeight = dp(48) * (accountsExpanded ? accountCount : 3);
        ValueAnimator next = ValueAnimator.ofInt(fromHeight, toHeight);
        accountsAnimator = next;
        next.setDuration(240);
        next.setInterpolator(CubicBezierInterpolator.DEFAULT);
        next.addUpdateListener(value -> {
            params.height = (int) value.getAnimatedValue();
            accountRows.setLayoutParams(params);
        });
        next.addListener(new AnimatorListenerAdapter() {
            boolean canceled;
            @Override public void onAnimationCancel(Animator animation) { canceled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (accountsAnimator == animation) accountsAnimator = null;
                if (canceled) return;
                for (int i = 3; i < accountCount; i++) {
                    accountRows.getChildAt(i).setVisibility(accountsExpanded ? VISIBLE : GONE);
                }
                params.height = -2;
                accountRows.setLayoutParams(params);
            }
        });
        next.start();
    }

    private View accountView(int account) {
        TLRPC.User accountUser = UserConfig.getInstance(account).getCurrentUser();
        FrameLayout row = new FrameLayout(getContext());
        BackupImageView image = new BackupImageView(getContext());
        image.setRoundRadius(dp(18));
        image.setForUserOrChat(accountUser, new AvatarDrawable(accountUser));
        row.addView(image, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
        String fullName = UserObject.getUserName(accountUser);
        TextView title = label(fullName, 15, Theme.key_chats_menuItemText, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, LayoutHelper.createFrame(-1, -1, Gravity.FILL, 72, 0, 32, 0));
        if (account == host.getCurrentAccount()) {
            ImageView check = new ImageView(getContext());
            check.setImageResource(R.drawable.ic_ab_done);
            check.setColorFilter(color(Theme.key_windowBackgroundWhiteBlueText));
            row.addView(check, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
            row.setSelected(true);
        }
        row.setContentDescription(fullName);
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(v -> close(() -> {
            if (host.getParentActivity() instanceof LaunchActivity) ((LaunchActivity)host.getParentActivity()).switchToAccount(account, true);
        }));
        return row;
    }

    private BaseFragment profile(boolean personal) {
        Bundle args = new Bundle();args.putLong("user_id",UserConfig.getInstance(host.getCurrentAccount()).getClientUserId());args.putBoolean("my_profile",personal);
        return new ProfileActivity(args);
    }
    private FrameLayout add(int icon, String title, Runnable action) {
        FrameLayout row=new FrameLayout(getContext()); row.setBackground(Theme.getSelectorDrawable(false));
        ImageView image=new ImageView(getContext());
        String name=getResources().getResourceEntryName(icon);
        Integer original=name.startsWith("devgram_classic_")?IconManager.INSTANCE.systemIcons().get(name.substring("devgram_classic_".length())):null;
        Drawable replacement=original==null?null:IconManager.INSTANCE.overrideDrawable(original);
        if(replacement==null)image.setImageResource(icon);else image.setImageDrawable(replacement);
        image.setColorFilter(color(Theme.key_chats_menuItemIcon));
        row.addView(image,LayoutHelper.createFrame(24,24,Gravity.LEFT|Gravity.CENTER_VERTICAL,19,0,0,0));
        TextView text=label(title,15,Theme.key_chats_menuItemText,true);
        row.addView(text,LayoutHelper.createFrame(-1,-1,Gravity.FILL,72,0,16,0));
        row.setOnClickListener(v->action.run());items.addView(row,new LinearLayout.LayoutParams(-1,dp(DevGramClassicDesign.DRAWER_ROW_HEIGHT)));
        return row;
    }
    private void divider() {
        View line=new View(getContext());line.setBackgroundColor(color(Theme.key_divider));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,1);lp.topMargin=dp(8);lp.bottomMargin=dp(8);items.addView(line,lp);
    }
    private void open(BaseFragment fragment) {
        if (!(fragment instanceof LoginActivity)) fragment.setCurrentAccount(host.getCurrentAccount());
        close(() -> host.presentFragment(fragment));
    }
    private void showStatusPicker() { close(() -> { if(host.getDialogsActivity()!=null)host.getDialogsActivity().showSelectStatusDialog(); }); }
    private void switchTheme(View source) {
        boolean dark=!Theme.isCurrentThemeDark();
        SharedPreferences prefs=getContext().getSharedPreferences("themeconfig",Context.MODE_PRIVATE);
        Theme.ThemeInfo theme=Theme.getTheme(prefs.getString(dark?"lastDarkTheme":"lastDayTheme",dark?"Dark Blue":"Blue"));
        if(theme==null||theme.isDark()!=dark)theme=Theme.getTheme(dark?"Dark Blue":"Blue");
        if(theme==null)return;
        int[] position=new int[2];source.getLocationInWindow(position);position[0]+=source.getWidth()/2;position[1]+=source.getHeight()/2;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme,theme,false,position,-1,dark,source);
    }
    @Override public void updateColors() { rebuild();invalidate(); }
    public boolean isOpen() { return progress>0 || animator!=null; }
    public boolean isLightStatusBar() { return AndroidUtilities.computePerceivedBrightness(headerColor) > .721f; }
    public void open() { rebuild();animateTo(1,null); }
    public void close(Runnable after) { animateTo(0,after); }
    private void setProgress(float value) {
        boolean wasVisible = progress > 0;
        progress=Math.max(0,Math.min(1,value));setVisibility(progress>0?VISIBLE:INVISIBLE);
        panel.setTranslationX((progress-1)*panelWidth);invalidate();
        if (wasVisible != (progress > 0)) host.classicDrawerVisibilityChanged();
    }
    private void animateTo(float target,Runnable after) {
        if(animator!=null)animator.cancel();
        ValueAnimator next=ValueAnimator.ofFloat(progress,target);animator=next;
        next.setDuration(220);next.setInterpolator(CubicBezierInterpolator.DEFAULT);
        next.addUpdateListener(a->setProgress((float)a.getAnimatedValue()));
        next.addListener(new AnimatorListenerAdapter(){boolean canceled;@Override public void onAnimationCancel(Animator a){canceled=true;}@Override public void onAnimationEnd(Animator a){if(animator==a)animator=null;if(!canceled&&after!=null)after.run();}});next.start();
    }
    /** The home root calls this before its pager, so a closed drawer also gets edge swipes. */
    public boolean intercept(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();startProgress=progress;edge=downX<dp(20)||isOpen();tracking=false;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&edge&&Math.abs(event.getX()-downX)>touchSlop&&Math.abs(event.getX()-downX)>Math.abs(event.getY()-downY)*1.5f){tracking=true;if(animator!=null)animator.cancel();}
        return tracking || (isOpen() && event.getX()>panelWidth*progress);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){return intercept(e)||super.onInterceptTouchEvent(e);}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&tracking){setProgress(startProgress+(e.getX()-downX)/Math.max(1,panelWidth));return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){
            float delta=e.getX()-downX;
            animateTo(tracking?(Math.abs(delta)>dp(48)?(delta>0?1:0):(progress>.5f?1:0)):0,null);tracking=false;return true;
        }
        return isOpen()||edge;
    }
    @Override protected void onMeasure(int w,int h){panelWidth=Math.min(dp(DevGramClassicDesign.DRAWER_WIDTH),Math.max(0,MeasureSpec.getSize(w)-dp(DevGramClassicDesign.DRAWER_EDGE)));panel.getLayoutParams().width=panelWidth;super.onMeasure(w,h);panel.setTranslationX((progress-1)*panelWidth);}
    @Override protected void onDraw(Canvas canvas){scrim.setColor(Color.argb((int)(153*progress),0,0,0));canvas.drawRect(0,0,getWidth(),getHeight(),scrim);}
    @Override protected void onDetachedFromWindow(){cancelAccountsAnimation();if(animator!=null){animator.cancel();animator=null;}super.onDetachedFromWindow();}
}

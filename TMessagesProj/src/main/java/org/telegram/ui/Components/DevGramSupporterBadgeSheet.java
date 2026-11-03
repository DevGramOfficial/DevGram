package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.DevGramBadgeChoiceActivity;
import org.telegram.ui.DevGramSupportSheet;
import org.telegram.ui.DevGramSupporterActivity;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Locale;

/** Numbered supporter sheet matching the current Extera layout, adapted to DevGram's plane. */
public final class DevGramSupporterBadgeSheet {
    private DevGramSupporterBadgeSheet() { }

    public static void show(BaseFragment fragment, long userId, CharSequence name, DevGramBadges.Badge badge) {
        if (fragment == null || fragment.getParentActivity() == null || badge == null) {
            return;
        }
        SupporterSheet sheet = new SupporterSheet(fragment, userId, name, badge);
        if (fragment.showDialog(sheet) != null) {
            sheet.playEnter();
        }
    }

    private static final class SupporterSheet extends BottomSheetWithRecyclerListView {
        private final BaseFragment fragment;
        private final ArrayList<View> views = new ArrayList<>();
        private final PlaneHeaderView header;
        private final LinearLayout bottomContainer;
        private UniversalAdapter adapter;
        private CharSequence title;

        SupporterSheet(BaseFragment fragment, long userId, CharSequence name, DevGramBadges.Badge badge) {
            super(fragment, false, false, false, fragment.getResourceProvider());
            this.fragment = fragment;
            currentAccount = fragment.getCurrentAccount();
            boolean own = userId == UserConfig.getInstance(currentAccount).getClientUserId();

            occupyNavigationBar = true;
            setApplyBottomPadding(false);
            topPadding = 0.2f;
            fixNavigationBar(getThemedColor(Theme.key_dialogBackground));
            recyclerListView.setClipToPadding(false);
            recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

            header = new PlaneHeaderView(getContext(), resourcesProvider, badge.number);
            views.add(header);

            title = own ? "Спасибо за поддержку!" : name + " поддерживает DevGram";
            TextView titleView = text(getContext(), title, 20, true,
                    Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
            views.add(titleView);

            String number = badge.number > 0 ? formatNumber(badge.number) : "уникальный значок";
            TextView subtitleView = text(getContext(), own
                            ? "Ваш самолётик " + number + " виден рядом с именем."
                            : "Номерной самолётик виден всем пользователям DevGram.",
                    14, false, Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            subtitleView.setGravity(Gravity.CENTER);
            subtitleView.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(6),
                    AndroidUtilities.dp(22), AndroidUtilities.dp(14));
            views.add(subtitleView);

            views.add(feature(R.drawable.devgram_ic_badge, "Номерной значок",
                    badge.number > 0
                            ? "Уникальный номер " + number + " сохраняется за владельцем значка."
                            : "Уникальный самолётик рядом с именем.", null));
            views.add(feature(R.drawable.msg_customize, "Оформление профиля",
                    "Свой значок, цвет имени, фон и обводка сообщений.", own ? () -> {
                        dismiss();
                        fragment.presentFragment(new DevGramBadgeChoiceActivity());
                    } : null));
            views.add(feature(R.drawable.msg_calendar2, "Даты регистрации",
                    "Примерная дата создания аккаунтов прямо в профиле.", null));
            views.add(feature(R.drawable.msg_settings, "Ранние сборки",
                    "Новые возможности раньше остальных пользователей.", own ? () -> {
                        dismiss();
                        fragment.presentFragment(new DevGramSupporterActivity());
                    } : null));

            bottomContainer = new LinearLayout(getContext());
            bottomContainer.setOrientation(LinearLayout.VERTICAL);
            bottomContainer.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft,
                    AndroidUtilities.navigationBarHeight);
            bottomContainer.setBackground(new BottomBackdrop(getThemedColor(Theme.key_dialogBackground)));

            ButtonWithCounterView primary = new ButtonWithCounterView(getContext(), true, resourcesProvider);
            primary.setRound();
            primary.setText(own ? "Настроить значок" : "Как получить значок", false);
            primary.setOnClickListener(v -> {
                dismiss();
                if (own) {
                    fragment.presentFragment(new DevGramBadgeChoiceActivity());
                } else {
                    DevGramSupportSheet.show(fragment);
                }
            });
            bottomContainer.addView(primary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48,
                    0, 12, 16, 12, own ? 8 : 12));

            if (own) {
                ButtonWithCounterView transfer = new ButtonWithCounterView(getContext(), true, resourcesProvider).setNeutral();
                transfer.setRound();
                transfer.setText("Передать другому аккаунту", false);
                transfer.setOnClickListener(v -> showTransfer(fragment, this, v));
                bottomContainer.addView(transfer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48,
                        0, 12, 0, 12, 12));
            }

            bottomContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                int padding = b - t;
                if (recyclerListView.getPaddingBottom() != padding) {
                    recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, padding);
                }
            });
            containerView.addView(bottomContainer, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
            adapter.update(false);
        }

        private View feature(int icon, CharSequence title, CharSequence subtitle, Runnable action) {
            FeatureCell cell = new FeatureCell(getContext(), icon, title, subtitle, resourcesProvider);
            cell.setAction(action);
            return cell;
        }

        void playEnter() {
            header.playEnter();
        }

        @Override
        public CharSequence getTitle() {
            return title == null ? "" : title;
        }

        @Override
        public RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
            adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true,
                    (items, universalAdapter) -> {
                        if (views == null) {
                            return;
                        }
                        for (View view : views) {
                            UItem item = UItem.asCustom(view);
                            item.transparent = true;
                            items.add(item);
                        }
                        items.add(UItem.asSpace(AndroidUtilities.dp(8)));
                    }, resourcesProvider);
            return adapter;
        }
    }

    private static void showTransfer(BaseFragment fragment, SupporterSheet sheet, View anchor) {
        ItemOptions options = ItemOptions.makeOptions(
                        sheet.getContainer(), fragment.getResourceProvider(), anchor, false, true)
                .setGravity(Gravity.CENTER)
                .setMinWidth(270);
        int source = fragment.getCurrentAccount();
        int count = 0;
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (account == source || !UserConfig.getInstance(account).isClientActivated()) {
                continue;
            }
            long uid = UserConfig.getInstance(account).getClientUserId();
            if (DevGramBadges.hasSupporterFeatures(uid)) {
                continue;
            }
            final int target = account;
            options.addAccount(account, false, () -> {
                sheet.dismiss();
                confirmTransfer(fragment, source, target);
            });
            count++;
        }
        if (count > 0) {
            options.addGap();
        }
        options.add(R.drawable.msg_link2, "Другой аккаунт", "Перенести по одноразовой ссылке", () -> {
            sheet.dismiss();
            Browser.openUrl(fragment.getParentActivity(),
                    "https://t.me/officialdevgram_bot?start=transfer");
        });
        options.show();
    }

    private static void confirmTransfer(BaseFragment fragment, int source, int target) {
        String targetName = UserObject.getUserName(UserConfig.getInstance(target).getCurrentUser());
        DevGramBadges.Badge badge = DevGramBadges.badgeOf(UserConfig.getInstance(source).getClientUserId());
        String number = badge != null && badge.number > 0 ? formatNumber(badge.number) : "";
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        builder.setTitle("Передача значка");
        builder.setMessage(AndroidUtilities.replaceTags("Передать значок **" + number + "** аккаунту **"
                + targetName + "**? Этот аккаунт его потеряет, а следующая передача будет доступна через 30 дней."));
        builder.setPositiveButton("Передать", (dialog, which) ->
                transfer(fragment, source, target, targetName));
        builder.setNegativeButton("Отмена", null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        View positive = dialog.getButton(-1);
        if (positive instanceof TextView) {
            ((TextView) positive).setTextColor(Theme.getColor(
                    Theme.key_text_RedBold, fragment.getResourceProvider()));
        }
    }

    private static void transfer(BaseFragment fragment, int source, int target, String targetName) {
        AlertDialog progress = new AlertDialog(fragment.getParentActivity(),
                AlertDialog.ALERT_TYPE_SPINNER, fragment.getResourceProvider());
        progress.setCanCancel(false);
        progress.showDelayed(300);
        DevGramBadges.transfer(source, target, (ok, error) -> {
            progress.dismiss();
            if (fragment.isFinished) {
                return;
            }
            if (ok) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                        "Значок передан аккаунту «" + targetName + "»").show();
            } else {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                        TextUtils.isEmpty(error) ? "Не удалось передать значок" : error).show();
            }
        });
    }

    private static TextView text(Context context, CharSequence value, int size, boolean bold,
                                 int colorKey, Theme.ResourcesProvider resourcesProvider) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(Theme.getColor(colorKey, resourcesProvider));
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        view.setTypeface(bold ? AndroidUtilities.bold() : AndroidUtilities.regular());
        return view;
    }

    private static String formatNumber(int number) {
        return String.format(Locale.US, "#%,d", number);
    }

    private static final class FeatureCell extends FrameLayout {
        private final FrameLayout content;
        private final ImageView next;
        private final Theme.ResourcesProvider resourcesProvider;

        FeatureCell(Context context, int iconRes, CharSequence title, CharSequence subtitle,
                    Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            boolean rtl = LocaleController.isRTL;
            int side = rtl ? Gravity.RIGHT : Gravity.LEFT;
            content = new FrameLayout(context);
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            icon.setImageResource(iconRes);
            icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            content.addView(icon, LayoutHelper.createFrame(24, 24, side | Gravity.TOP,
                    rtl ? 0 : 21, 10, rtl ? 21 : 0, 0));

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView titleView = text(context, title, 15, true,
                    Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            titleView.setGravity(side);
            labels.addView(titleView, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            TextView subtitleView = text(context, subtitle, 14, false,
                    Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            subtitleView.setGravity(side);
            subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1f);
            labels.addView(subtitleView, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
            content.addView(labels, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, side | Gravity.TOP,
                    rtl ? 48 : 64, 8, rtl ? 64 : 48, 9));

            next = new ImageView(context);
            next.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            next.setImageResource(R.drawable.msg_arrowright);
            next.setColorFilter(Theme.getColor(Theme.key_switchTrack, resourcesProvider));
            next.setScaleX(rtl ? -1f : 1f);
            next.setVisibility(GONE);
            content.addView(next, LayoutHelper.createFrame(24, 24,
                    (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL,
                    18, 0, 18, 0));
        }

        void setAction(Runnable action) {
            next.setVisibility(action == null ? GONE : VISIBLE);
            content.setBackground(action == null ? null : Theme.getSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector, resourcesProvider), false));
            content.setClickable(action != null);
            content.setOnClickListener(action == null ? null : v -> action.run());
        }
    }

    private static final class PlaneHeaderView extends FrameLayout {
        private final ImageView plane;
        private final int color1;
        private final int color2;
        private float downX;
        private float downY;
        private float startRotationX;
        private float startRotationY;

        PlaneHeaderView(Context context, Theme.ResourcesProvider resourcesProvider, int number) {
            super(context);
            setClipChildren(false);
            setWillNotDraw(false);
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            color1 = shiftColor(accent, -10f, .75f, 1.15f);
            color2 = shiftColor(accent, 18f, .6f, 1.1f);

            int performance = SharedConfig.getDevicePerformanceClass();
            StarParticlesView particles = new StarParticlesView(context,
                    performance == 2 ? 90 : performance == 1 ? 50 : 25) {
                @Override
                public void configure() {
                    super.configure();
                    drawable.useGradient = false;
                    drawable.useBlur = false;
                    drawable.forceMaxAlpha = true;
                    drawable.checkBounds = true;
                    drawable.colorKey = Theme.key_featuredStickers_addButton;
                    drawable.resourcesProvider = resourcesProvider;
                    drawable.init();
                }
            };
            addView(particles, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 190, Gravity.TOP));

            plane = new ImageView(context);
            plane.setImageResource(R.drawable.devgram_support_logo);
            plane.setElevation(AndroidUtilities.dp(12));
            addView(plane, LayoutHelper.createFrame(132, 132, Gravity.CENTER, 0, -8, 0, 0));

            if (number > 0) {
                TextView pill = text(context, "Значок " + formatNumber(number), 13, true,
                        Theme.key_featuredStickers_buttonText, resourcesProvider);
                pill.setGravity(Gravity.CENTER);
                pill.setTextColor(resolveContrastColor(blend(color1, color2)));
                GradientDrawable background = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT, new int[]{color1, color2});
                background.setCornerRadius(AndroidUtilities.dp(12));
                pill.setBackground(background);
                pill.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
                addView(pill, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 24,
                        Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 3));
            }

            setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        startRotationX = plane.getRotationX();
                        startRotationY = plane.getRotationY();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        plane.setRotationY(startRotationY + (event.getX() - downX) * .72f);
                        plane.setRotationX(startRotationX - (event.getY() - downY) * .42f);
                        plane.setRotation((event.getX() - downX) * .04f);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        AnimatorSet reset = new AnimatorSet();
                        reset.playTogether(
                                ObjectAnimator.ofFloat(plane, View.ROTATION_X, 0f),
                                ObjectAnimator.ofFloat(plane, View.ROTATION_Y,
                                        Math.round(plane.getRotationY() / 360f) * 360f),
                                ObjectAnimator.ofFloat(plane, View.ROTATION, 0f));
                        reset.setDuration(420);
                        reset.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                        reset.start();
                        return true;
                }
                return false;
            });
        }

        void playEnter() {
            plane.setScaleX(.35f);
            plane.setScaleY(.35f);
            plane.setAlpha(0f);
            plane.setRotationY(-360f);
            AnimatorSet enter = new AnimatorSet();
            enter.playTogether(
                    ObjectAnimator.ofFloat(plane, View.SCALE_X, 1f),
                    ObjectAnimator.ofFloat(plane, View.SCALE_Y, 1f),
                    ObjectAnimator.ofFloat(plane, View.ALPHA, 1f),
                    ObjectAnimator.ofFloat(plane, View.ROTATION_Y, 0f));
            enter.setDuration(600);
            enter.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            enter.start();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    AndroidUtilities.dp(200), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new RadialGradient(getWidth() / 2f,
                    getHeight() / 2f - AndroidUtilities.dp(4), AndroidUtilities.dp(100),
                    Color.argb(38, Color.red(color1), Color.green(color1), Color.blue(color1)),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f - AndroidUtilities.dp(4),
                    AndroidUtilities.dp(100), paint);
        }

        private static int shiftColor(int color, float hue, float saturation, float value) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hsv[0] = (hsv[0] + hue + 360f) % 360f;
            hsv[1] = Math.min(1f, hsv[1] * saturation);
            hsv[2] = Math.min(1f, hsv[2] * value);
            return Color.HSVToColor(Color.alpha(color), hsv);
        }

        private static int blend(int first, int second) {
            return Color.rgb((Color.red(first) + Color.red(second)) / 2,
                    (Color.green(first) + Color.green(second)) / 2,
                    (Color.blue(first) + Color.blue(second)) / 2);
        }

        private static int resolveContrastColor(int color) {
            double luminance = (.299 * Color.red(color) + .587 * Color.green(color)
                    + .114 * Color.blue(color)) / 255d;
            return luminance > .67 ? Color.BLACK : Color.WHITE;
        }
    }

    private static final class BottomBackdrop extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;

        BottomBackdrop(int color) {
            this.color = color;
        }

        @Override
        public void draw(Canvas canvas) {
            int fade = AndroidUtilities.dp(16);
            paint.setShader(new LinearGradient(0, getBounds().top, 0, getBounds().top + fade,
                    Color.TRANSPARENT, color, Shader.TileMode.CLAMP));
            canvas.drawRect(getBounds().left, getBounds().top, getBounds().right,
                    getBounds().top + fade, paint);
            paint.setShader(null);
            paint.setColor(color);
            canvas.drawRect(getBounds().left, getBounds().top + fade,
                    getBounds().right, getBounds().bottom, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}

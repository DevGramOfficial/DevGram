package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.DevGramBadgeChoiceActivity;
import org.telegram.ui.DevGramSupportSheet;
import org.telegram.ui.DevGramSupporterActivity;

import java.util.ArrayList;

/** Extera-style numbered supporter card, adapted to DevGram's airplane badge. */
public final class DevGramSupporterBadgeSheet {
    private DevGramSupporterBadgeSheet() { }

    public static void show(BaseFragment fragment, long userId, CharSequence name, DevGramBadges.Badge badge) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        Context context = fragment.getParentActivity();
        int account = fragment.getCurrentAccount();
        boolean own = userId == UserConfig.getInstance(account).getClientUserId();

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12),
                AndroidUtilities.dp(12) + AndroidUtilities.navigationBarHeight);

        PlaneHeaderView header = new PlaneHeaderView(context, badge.number);
        content.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 202));

        TextView title = text(context, own ? "Спасибо за поддержку!" : name + " поддерживает DevGram", 20, true,
                Theme.key_windowBackgroundWhiteBlackText);
        title.setGravity(Gravity.CENTER);
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 10, 0, 10, 0));

        String number = badge.number > 0 ? formatNumber(badge.number) : "уникальный";
        TextView subtitle = text(context, own
                        ? "Значок " + number + " — вот что вам доступно:"
                        : "Номерной значок виден всем пользователям DevGram", 14, false,
                Theme.key_windowBackgroundWhiteGrayText2);
        subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 6, 22, 14));

        final BottomSheet[] sheetRef = new BottomSheet[1];
        addFeature(content, R.drawable.devgram_ic_badge, "Номерной значок",
                badge.number > 0 ? "Самолётик " + formatNumber(badge.number) + " рядом с именем" : "Уникальный самолётик рядом с именем", null);
        addFeature(content, R.drawable.msg_customize, "Оформление профиля",
                "Свой значок, цвет имени, фон и обводка сообщений", own ? () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    fragment.presentFragment(new DevGramBadgeChoiceActivity());
                } : null);
        addFeature(content, R.drawable.msg_calendar2, "Даты регистрации",
                "Примерная дата создания аккаунтов прямо в профиле", null);
        addFeature(content, R.drawable.msg_settings, "Ранние сборки",
                "Доступ к новым возможностям раньше остальных", own ? () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    fragment.presentFragment(new DevGramSupporterActivity());
                } : null);

        TextView primary = button(context, own ? "Настроить значок" : "Как получить значок", true);
        primary.setOnClickListener(v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            if (own) fragment.presentFragment(new DevGramBadgeChoiceActivity());
            else DevGramSupportSheet.show(fragment);
        });
        content.addView(primary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 12, 16, 12, 0));

        if (own) {
            TextView transfer = button(context, "Передать другому аккаунту", false);
            transfer.setCompoundDrawablesWithIntrinsicBounds(R.drawable.msg_forward, 0, 0, 0);
            transfer.setCompoundDrawablePadding(AndroidUtilities.dp(8));
            transfer.setOnClickListener(v -> showTransfer(fragment, account, sheetRef[0]));
            content.addView(transfer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 12, 6, 12, 0));
        }

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, fragment.getResourceProvider());
        builder.setApplyBottomPadding(false);
        builder.setCustomView(scroll);
        BottomSheet sheet = builder.create();
        sheetRef[0] = sheet;
        sheet.show();
        header.playEnter();
    }

    private static void showTransfer(BaseFragment fragment, int sourceAccount, BottomSheet sheet) {
        ArrayList<Integer> accounts = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (a == sourceAccount || !UserConfig.getInstance(a).isClientActivated()) continue;
            long uid = UserConfig.getInstance(a).getClientUserId();
            if (DevGramBadges.hasSupporterFeatures(uid)) continue;
            accounts.add(a);
            names.add(UserObject.getUserName(UserConfig.getInstance(a).getCurrentUser()));
        }
        if (accounts.isEmpty()) {
            new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                    .setTitle("Передача значка")
                    .setMessage("Добавьте в DevGram другой аккаунт. После этого он появится здесь и сможет принять значок.")
                    .setPositiveButton("Понятно", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle("Передать значок")
                .setItems(names.toArray(new CharSequence[0]), (dialog, which) -> confirmTransfer(fragment, sourceAccount, accounts.get(which), names.get(which), sheet))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static void confirmTransfer(BaseFragment fragment, int source, int target, String targetName, BottomSheet sheet) {
        AlertDialog progress = new AlertDialog.Builder(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER, fragment.getResourceProvider()).create();
        new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle("Передача значка")
                .setMessage("Передать номерной значок аккаунту «" + targetName + "»?\n\nОн исчезнет с текущего аккаунта. Следующая передача будет доступна через 30 дней.")
                .setPositiveButton("Передать", (d, w) -> {
                    progress.show();
                    DevGramBadges.transfer(source, target, (ok, error) -> {
                        progress.dismiss();
                        if (ok) {
                            if (sheet != null) sheet.dismiss();
                            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, "Значок передан аккаунту «" + targetName + "»").show();
                        } else {
                            BulletinFactory.of(fragment).createErrorBulletin(error).show();
                        }
                    });
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static void addFeature(LinearLayout root, int iconRes, String title, String subtitle, Runnable action) {
        Context context = root.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(9), AndroidUtilities.dp(8), AndroidUtilities.dp(9));
        if (action != null) {
            row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 7));
            row.setOnClickListener(v -> action.run());
        }
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton));
        row.addView(icon, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(context, title, 15, true, Theme.key_windowBackgroundWhiteBlackText),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        labels.addView(text(context, subtitle, 14, false, Theme.key_windowBackgroundWhiteGrayText2),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        row.addView(labels, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        if (action != null) {
            ImageView arrow = new ImageView(context);
            arrow.setImageResource(R.drawable.arrow_newchat);
            arrow.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            row.addView(arrow, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL));
        }
        root.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private static TextView text(Context context, String value, int size, boolean bold, int colorKey) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(Theme.getColor(colorKey));
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        if (bold) view.setTypeface(AndroidUtilities.bold());
        return view;
    }

    private static TextView button(Context context, String value, boolean filled) {
        TextView view = text(context, value, 15, true,
                filled ? Theme.key_featuredStickers_buttonText : Theme.key_featuredStickers_addButton);
        view.setGravity(Gravity.CENTER);
        int radius = AndroidUtilities.dp(12);
        if (filled) {
            view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(radius,
                    Theme.getColor(Theme.key_featuredStickers_addButton),
                    Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        } else {
            view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(radius,
                    Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton), .12f),
                    Theme.getColor(Theme.key_listSelector)));
        }
        return view;
    }

    private static String formatNumber(int number) {
        return "#" + String.format(java.util.Locale.US, "%04d", number);
    }

    private static final class PlaneHeaderView extends FrameLayout {
        private final ImageView plane;
        private float downX, downY, baseX, baseY;

        PlaneHeaderView(Context context, int number) {
            super(context);
            setWillNotDraw(false);
            setClipChildren(false);
            plane = new ImageView(context);
            plane.setImageResource(R.drawable.devgram_support_logo);
            plane.setElevation(AndroidUtilities.dp(18));
            addView(plane, LayoutHelper.createFrame(128, 128, Gravity.CENTER, 0, -5, 0, 0));
            if (number > 0) {
                TextView pill = text(context, "Значок " + formatNumber(number), 13, true, Theme.key_featuredStickers_buttonText);
                pill.setGravity(Gravity.CENTER);
                GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_chat_messageLinkIn)});
                bg.setCornerRadius(AndroidUtilities.dp(12));
                pill.setBackground(bg);
                pill.setPadding(AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11), 0);
                addView(pill, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 24, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 9));
            }
            setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX(); downY = event.getY();
                    baseX = plane.getRotationY(); baseY = plane.getRotationX();
                    return true;
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    plane.setRotationY(baseX + (event.getX() - downX) * .72f);
                    plane.setRotationX(baseY - (event.getY() - downY) * .35f);
                    plane.setRotation((event.getX() - downX) * .04f);
                    return true;
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    AnimatorSet reset = new AnimatorSet();
                    reset.playTogether(ObjectAnimator.ofFloat(plane, View.ROTATION_X, 0),
                            ObjectAnimator.ofFloat(plane, View.ROTATION_Y, Math.round(plane.getRotationY() / 360f) * 360f),
                            ObjectAnimator.ofFloat(plane, View.ROTATION, 0));
                    reset.setDuration(420);
                    reset.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    reset.start();
                    return true;
                }
                return false;
            });
        }

        void playEnter() {
            plane.setScaleX(.35f); plane.setScaleY(.35f); plane.setAlpha(0f); plane.setRotationY(-360f);
            AnimatorSet enter = new AnimatorSet();
            enter.playTogether(ObjectAnimator.ofFloat(plane, View.SCALE_X, 1f), ObjectAnimator.ofFloat(plane, View.SCALE_Y, 1f),
                    ObjectAnimator.ofFloat(plane, View.ALPHA, 1f), ObjectAnimator.ofFloat(plane, View.ROTATION_Y, 0f));
            enter.setDuration(620);
            enter.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            enter.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            p.setShader(new RadialGradient(getWidth() / 2f, getHeight() / 2f, AndroidUtilities.dp(100),
                    (accent & 0x00ffffff) | 0x28000000, accent & 0x00ffffff, Shader.TileMode.CLAMP));
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, AndroidUtilities.dp(98), p);
            p.setShader(null);
            p.setColor((accent & 0x00ffffff) | 0x55000000);
            for (int i = 0; i < 18; i++) {
                double a = i * 2.39996;
                float r = AndroidUtilities.dp(55 + (i * 17 % 48));
                canvas.drawCircle(getWidth()/2f + (float)Math.cos(a)*r, getHeight()/2f + (float)Math.sin(a)*r,
                        AndroidUtilities.dpf2(i % 3 == 0 ? 2.1f : 1.2f), p);
            }
        }
    }
}

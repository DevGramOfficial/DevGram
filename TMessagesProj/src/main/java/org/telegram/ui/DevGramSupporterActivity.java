package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramBeta;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.DevGramProxy;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

// DevGram: раздел для поддержавших проект. Виден только с правом поддержавшего (значок ✈️).
// Здесь собраны все возможности, доступные только поддержавшим, и благодарность за поддержку.
public class DevGramSupporterActivity extends DevGramSettingsFragment {

    private static final int ID_PROXY = 1;
    private static final int ID_UPDATE_CHANNEL = 2;
    private static final int ID_BADGE = 3;
    private static final int ID_FRAME = 4;
    private static final int ID_BETA_CHANNEL = 5;
    private static final int ID_PROFILE_BG = 6;
    private static final int ID_NICK = 7;
    private boolean welcomeShown;

    private UniversalRecyclerView listView;
    private HeroView heroView;

    public static boolean isAvailable(int account) {
        try {
            long uid = org.telegram.messenger.UserConfig.getInstance(account).getClientUserId();
            return DevGramBadges.hasSupporterFeatures(uid);
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Поддержавшим");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        heroView = new HeroView(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        DevGramSettingsLink.consumeHighlight(listView);
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(heroView));

        items.add(UItem.asHeader("Оформление"));
        long uid = getUserConfig().getClientUserId();
        DevGramBadges.Badge badge = DevGramBadges.badgeOf(uid);
        boolean customBadge = badge != null && badge.emojiId != badge.originalEmojiId;
        items.add(UItem.asButton(ID_BADGE, R.drawable.msg_premium_badge, "Значок у ника", customBadge ? "Свой" : "Выданный"));
        int frame = DevGramBadges.borderOf(uid);
        org.telegram.ui.Components.DevGramMessageFrame.Style st = org.telegram.ui.Components.DevGramMessageFrame.get(frame);
        items.add(UItem.asButton(ID_FRAME, R.drawable.msg_photo_settings, "Обводка сообщений", st != null ? st.title : "Нет"));
        items.add(UItem.asButton(ID_NICK, R.drawable.msg_colors, "Цвет ника", nickTitle(uid)));
        items.add(UItem.asButton(ID_PROFILE_BG, R.drawable.msg_photos, "Фон профиля", DevGramProfileBgActivity.statusText(currentAccount)));
        items.add(UItem.asShadow("Значок, обводку и фон профиля видят все пользователи DevGram."));

        items.add(UItem.asHeader("Ваши возможности"));
        items.add(UItem.asIconButtonCheck(ID_PROXY, R.drawable.drawer_proxy_on, "Прокси DevGram", "Защищённое подключение")
                .setChecked(DevGramProxy.isEnabled()));
        if (DevGramBeta.hasAccess(currentAccount)) {
            items.add(UItem.asButton(ID_UPDATE_CHANNEL, R.drawable.msg_channel, "Канал обновлений",
                    DevGramConfig.updateChannel == 1 ? "Бета" : "Основной"));
            items.add(UItem.asButton(ID_BETA_CHANNEL, R.drawable.msg_groups, "Закрытый канал беты",
                    DevGramBeta.isBetaChannelMember(currentAccount) ? "Открыть" : "Вступить"));
        }
        items.add(UItem.asShadow("Бета-канал получает новые версии раньше всех. Новые возможности для поддержавших будут появляться в этом разделе."));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_PROXY) {
            boolean enable = !DevGramProxy.isEnabled();
            DevGramProxy.setEnabled(enable);
            DevGramConfig.setVpnEnabled(enable);
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        } else if (item.id == ID_UPDATE_CHANNEL) {
            toggleUpdateChannel();
        } else if (item.id == ID_BADGE) {
            presentFragment(new DevGramBadgeChoiceActivity());
        } else if (item.id == ID_FRAME) {
            presentFragment(new DevGramFrameChoiceActivity());
        } else if (item.id == ID_NICK) {
            presentFragment(new DevGramNickColorActivity());
        } else if (item.id == ID_PROFILE_BG) {
            presentFragment(new DevGramProfileBgActivity());
        } else if (item.id == ID_BETA_CHANNEL) {
            openBetaChannel();
        }
    }

    private static String nickTitle(long uid) {
        int[] c = DevGramBadges.nickColorsOf(uid);
        if (c == null) return "Обычный";
        for (org.telegram.ui.Components.DevGramNickColor.Preset p : org.telegram.ui.Components.DevGramNickColor.PRESETS) {
            if (java.util.Arrays.equals(p.colors, c)) return p.title;
        }
        return "Свой";
    }

    // Первый заход: благодарность и приглашение в закрытый канал беты (один раз на аккаунт)
    private void maybeShowWelcome() {
        String key = "devgramBetaInvite_" + getUserConfig().getClientUserId();
        android.content.SharedPreferences prefs = getMessagesController().getGlobalMainSettings();
        if (!DevGramBeta.hasAccess(currentAccount) || DevGramBeta.isBetaChannelMember(currentAccount)) return;
        if (prefs.getBoolean(key, false) || welcomeShown || getParentActivity() == null) return;
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), resourceProvider);
        b.setTopAnimation(R.raw.email_setup_heart, 72, false, Theme.getColor(Theme.key_dialogTopBackground, resourceProvider));
        b.setTitle("Спасибо за поддержку!");
        b.setMessage("Вы помогаете DevGram развиваться — спасибо ❤️\n\nВ благодарность вам открыт закрытый канал беты: "
                + "новые версии и новости там появляются раньше всех.\n\n"
                + "Ссылка персональная и сработает один раз — только для вас. Вступить можно и позже, кнопкой в этом разделе.");
        b.setPositiveButton("Присоединиться", (d, w) -> {
            prefs.edit().putBoolean(key, true).apply();
            openBetaChannel();
        });
        b.setNegativeButton("Отказаться", (d, w) -> prefs.edit().putBoolean(key, true).apply());
        // пока идёт анимация открытия экрана, showDialog отказывает — тогда покажем в следующий раз
        welcomeShown = showDialog(b.create()) != null;
    }

    // Участник — открываем канал, иначе запрашиваем персональную ссылку-заявку (бот одобрит её сразу)
    private void openBetaChannel() {
        if (DevGramBeta.isBetaChannelMember(currentAccount)) {
            android.os.Bundle args = new android.os.Bundle();
            args.putLong("chat_id", DevGramBeta.getBetaChannel());
            presentFragment(new ChatActivity(args));
            return;
        }
        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Получаю персональную ссылку…").show();
        DevGramBeta.requestInvite(currentAccount, (link, error) -> {
            if (link == null) {
                if ("Канал беты ещё не готов".equals(error)) {
                    // канал ещё не подключён — приглашение покажем снова, когда появится
                    getMessagesController().getGlobalMainSettings().edit()
                            .remove("devgramBetaInvite_" + getUserConfig().getClientUserId()).apply();
                }
                BulletinFactory.of(this).createErrorBulletin(error != null ? error : "Не удалось получить ссылку").show();
                return;
            }
            if (getParentActivity() != null) {
                org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), link);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        DevGramBadges.syncFromCloud();
        if (listView != null && listView.adapter != null) listView.adapter.update(false);
        AndroidUtilities.runOnUIThread(this::maybeShowWelcome, 400);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen) maybeShowWelcome();
        if (heroView != null) heroView.updateBadge();
    }

    // Основной/бета. Бета включается только после активации токена (клиент сам получает его у бота).
    private void toggleUpdateChannel() {
        if (DevGramConfig.updateChannel == 1) {
            DevGramConfig.setUpdateChannel(0);
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
            return;
        }
        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Активирую бета-канал…").show();
        DevGramBeta.activate(currentAccount, (ok, error) -> {
            if (ok) {
                DevGramConfig.setUpdateChannel(1);
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Бета-канал включён").show();
            } else {
                BulletinFactory.of(this).createErrorBulletin(error != null ? error : "Не удалось активировать бету").show();
            }
        });
    }

    // ---- шапка: живой градиент, звёзды, значок поддержавшего и благодарность ----

    private class HeroView extends FrameLayout {
        private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF card = new RectF();
        private final Matrix matrix = new Matrix();
        private LinearGradient gradient;
        private final StarParticlesView stars;
        private AnimatedEmojiDrawable badge;
        private final long startTime = System.currentTimeMillis();

        HeroView(Context context) {
            super(context);
            setWillNotDraw(false);
            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(6));

            stars = new StarParticlesView(context) {
                @Override
                protected int getStarsRectWidth() {
                    return getMeasuredWidth();
                }
            };
            stars.drawable.colorKey = Theme.key_premiumStartSmallStarsColor2;
            stars.drawable.isCircle = false;
            stars.drawable.init();
            addView(stars, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150, Gravity.TOP, 14, 10, 14, 0));

            badge = makeBadge();

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setGravity(Gravity.CENTER_HORIZONTAL);

            TextView title = new TextView(context);
            title.setText("Спасибо за поддержку!");
            title.setTextColor(0xffffffff);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            title.setTypeface(AndroidUtilities.bold());
            title.setGravity(Gravity.CENTER);
            texts.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            TLRPC.User me = getUserConfig().getCurrentUser();
            String name = me != null ? UserObject.getFirstName(me) : null;
            TextView subtitle = new TextView(context);
            subtitle.setText((TextUtils.isEmpty(name) ? "Вы" : name + ", вы")
                    + " помогаете DevGram развиваться. Ниже — возможности, которые открыты только поддержавшим проект.");
            subtitle.setTextColor(0xe6ffffff);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setLineSpacing(AndroidUtilities.dp(2), 1f);
            texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 34, 150, 34, 26));
        }

        private AnimatedEmojiDrawable makeBadge() {
            long emojiId = DevGramBadges.emojiIdOf(getUserConfig().getClientUserId());
            if (emojiId == 0) emojiId = DevGramBadges.EMOJI_SUPPORTER;
            return AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, emojiId);
        }

        void updateBadge() {
            if (badge != null && isAttachedToWindow()) badge.removeView(this);
            badge = makeBadge();
            if (isAttachedToWindow()) badge.addView(this);
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (badge != null) badge.addView(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (badge != null) badge.removeView(this);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            gradient = new LinearGradient(0, 0, w, h,
                    new int[]{0xff6A3DE8, 0xff3D7BF7, 0xff2BB3F2, 0xffB44DE0, 0xff6A3DE8},
                    null, Shader.TileMode.MIRROR);
            cardPaint.setShader(gradient);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            card.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
            float r = AndroidUtilities.dp(24);
            if (gradient != null) {
                // градиент медленно «переливается»
                float t = (System.currentTimeMillis() - startTime) / 6000f;
                matrix.setTranslate((float) Math.sin(t) * getWidth() * 0.35f, (float) Math.cos(t * 0.8f) * getHeight() * 0.25f);
                gradient.setLocalMatrix(matrix);
            }
            canvas.drawRoundRect(card, r, r, cardPaint);
            canvas.save();
            canvas.clipRect(card);
            super.dispatchDraw(canvas);
            canvas.restore();

            // значок поддержавшего с мягким свечением
            float cx = getWidth() / 2f, cy = getPaddingTop() + AndroidUtilities.dp(80);
            float pulse = 1f + 0.04f * (float) Math.sin((System.currentTimeMillis() - startTime) / 500f);
            glowPaint.setShader(new android.graphics.RadialGradient(cx, cy, AndroidUtilities.dp(70) * pulse,
                    0x66ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(70) * pulse, glowPaint);
            if (badge != null) {
                int half = (int) (AndroidUtilities.dp(40) * pulse);
                badge.setBounds((int) cx - half, (int) cy - half, (int) cx + half, (int) cy + half);
                badge.draw(canvas);
            }
            invalidate();
        }
    }
}

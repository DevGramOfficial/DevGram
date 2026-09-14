package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;

public class DevGramPluginReviewsActivity extends BaseFragment {
    private final DevGramPlugins.CatalogEntry entry;
    private LinearLayout content;

    public DevGramPluginReviewsActivity(DevGramPlugins.CatalogEntry entry) {
        this.entry = entry;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Отзывы · " + entry.name);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        load(context);
        return fragmentView = scroll;
    }

    private void load(Context context) {
        DevGramPlugins.fetchReviews(entry.id, reviews -> {
            content.removeAllViews();
            LinearLayout summary = new LinearLayout(context);
            summary.setOrientation(LinearLayout.VERTICAL);
            summary.setGravity(Gravity.CENTER);
            summary.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(18), AndroidUtilities.dp(18), AndroidUtilities.dp(18));
            summary.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(22), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
            double total = 0; for (DevGramPlugins.Review review : reviews) total += review.rating;
            String score = reviews.isEmpty() ? "—" : String.format(java.util.Locale.US, "%.1f", total / reviews.size());
            TextView scoreView = label(context, score, 34, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            scoreView.setGravity(Gravity.CENTER); summary.addView(scoreView);
            int roundedRating = reviews.isEmpty() ? 0 : Math.max(0, Math.min(5, (int) Math.round(total / reviews.size())));
            TextView stars = label(context, "★".repeat(roundedRating) + "☆".repeat(5 - roundedRating), 20, true, 0xFFE0A400);
            stars.setGravity(Gravity.CENTER); summary.addView(stars, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
            TextView count = label(context, reviews.isEmpty() ? "Отзывов пока нет" : reviews.size() + " " + plural(reviews.size()), 14, false,
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            count.setGravity(Gravity.CENTER); summary.addView(count, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 0));
            content.addView(summary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));
            if (reviews.isEmpty()) {
                TextView empty = label(context, "Станьте первым, кто поделится впечатлением о плагине.", 15, false,
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
                empty.setGravity(Gravity.CENTER); empty.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(40), AndroidUtilities.dp(24), AndroidUtilities.dp(40));
                content.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return;
            }
            for (DevGramPlugins.Review review : reviews) content.addView(reviewCard(context, review), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        });
    }

    private View reviewCard(Context context, DevGramPlugins.Review review) {
        LinearLayout card = new LinearLayout(context); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13), AndroidUtilities.dp(10), AndroidUtilities.dp(15));
        card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(20), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider), Theme.getColor(Theme.key_listSelector, resourceProvider)));
        card.setElevation(AndroidUtilities.dp(1));
        LinearLayout head = new LinearLayout(context); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = label(context, review.name == null || review.name.isEmpty() ? "?" : review.name.substring(0, 1).toUpperCase(), 16, true,
                Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        avatar.setGravity(Gravity.CENTER); avatar.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider)));
        head.addView(avatar, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 11, 0));
        LinearLayout author = new LinearLayout(context); author.setOrientation(LinearLayout.VERTICAL);
        author.addView(label(context, review.name, 15, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)));
        if (review.date > 0) author.addView(label(context, android.text.format.DateFormat.format("dd.MM.yyyy", review.date).toString(), 12, false,
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider)), LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));
        head.addView(author, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView menu = label(context, "⋮", 28, true, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        menu.setGravity(Gravity.CENTER); menu.setContentDescription("Действия с отзывом");
        menu.setOnClickListener(v -> showActions(context, review, card));
        head.addView(menu, LayoutHelper.createLinear(44, 44)); card.addView(head);
        TextView stars = label(context, "★".repeat(Math.max(0, Math.min(5, review.rating))) + "☆".repeat(Math.max(0, 5 - review.rating)), 16, true, 0xFFE0A400);
        card.addView(stars, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 7));
        TextView body = label(context, review.text, 15, false, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        body.setLineSpacing(AndroidUtilities.dp(3), 1f); card.addView(body);
        ScaleStateListAnimator.apply(card, .015f, 1.2f);
        return card;
    }

    private void showActions(Context context, DevGramPlugins.Review review, View card) {
        boolean own = review.userId == DevGramPlugins.myId();
        boolean developer = DevGramBadges.hasDeveloperFeatures(DevGramPlugins.myId());
        if (!own) {
            DevGramPlugins.hasReportedReview(entry.id, review.userId, reported -> showActionsResolved(context, review, card, developer, reported));
            return;
        }
        showActionsResolved(context, review, card, developer, false);
    }

    private void showActionsResolved(Context context, DevGramPlugins.Review review, View card, boolean developer, boolean reported) {
        boolean own = review.userId == DevGramPlugins.myId();
        LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(20));
        root.addView(label(context, own ? "Ваш отзыв" : "Действия с отзывом", 20, true,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 12));
        BottomSheet[] ref = new BottomSheet[1];
        if (!own) addAction(root, context, reported ? "Вы уже подавали жалобу" : "Пожаловаться", reported ? "Повторная жалоба на этот отзыв недоступна" : "Сообщить команде DevGram о нарушении", false, () -> { ref[0].dismiss(); if(reported)BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,"Вы уже подавали жалобу").show();else showReportReasonMenu(context, review.userId); });
        if (own || developer) addAction(root, context, "Удалить отзыв", own ? "Отзыв исчезнет из карточки плагина" : "Удаление доступно команде DevGram", true, () -> {
            if (own) DevGramPlugins.deleteOwnReview(entry.id, ok -> afterDelete(ok, card));
            else DevGramPlugins.deleteReviewAsDeveloper(entry.id, review.userId, ok -> afterDelete(ok, card));
            ref[0].dismiss();
        });
        BottomSheet.Builder builder = new BottomSheet.Builder(context); builder.setApplyBottomPadding(false); builder.setCustomView(root); ref[0] = builder.create(); ref[0].show();
    }

    private void afterDelete(boolean ok, View card) {
        if (ok) { card.setVisibility(View.GONE); BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Отзыв удалён").show(); }
        else BulletinFactory.of(this).createErrorBulletin("Не удалось удалить отзыв").show();
    }

    private void showReportReasonMenu(Context context, long uid) {
        String[] reasons = {"Спам или реклама", "Оскорбления и травля", "Ложная информация", "Другая причина"};
        DevGramPluginUi.showChoices(this, "Жалоба на отзыв", "Выберите нарушение — команда DevGram проверит жалобу.",
                reasons, new int[]{R.drawable.msg_report, R.drawable.msg_block, R.drawable.msg_info, R.drawable.msg_edit}, reasons.length, which -> {
                    if (which == 3) DevGramPluginUi.showTextInput(this, "Другая причина", "Опишите нарушение коротко и по существу.",
                            "Что именно нарушает этот отзыв?", "", "Отправить жалобу", true, reason -> sendReport(uid, reason));
                    else sendReport(uid, reasons[which]);
                });
    }

    private void sendReport(long uid, String reason) {
        reason = reason == null ? "" : reason.trim();
        if (reason.isEmpty()) { BulletinFactory.of(this).createErrorBulletin("Укажите причину жалобы").show(); return; }
        DevGramPlugins.reportReview(entry.id, uid, reason, ok -> {if(!ok)DevGramPlugins.hasReportedReview(entry.id,uid,reported->{if(reported)BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,"Вы уже подавали жалобу").show();});BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, ok ? "Жалоба отправлена" : "Не удалось отправить жалобу").show();});
    }

    private void addAction(LinearLayout root, Context context, String title, String subtitle, boolean danger, Runnable click) {
        LinearLayout row = new LinearLayout(context); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider), Theme.getColor(Theme.key_listSelector, resourceProvider)));
        row.addView(label(context, title, 16, true, danger ? Theme.getColor(Theme.key_text_RedRegular, resourceProvider) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)));
        row.addView(label(context, subtitle, 13, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        row.setOnClickListener(v -> click.run()); root.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
    }

    private TextView label(Context context, String value, int size, boolean bold, int color) { TextView text = new TextView(context); text.setText(value); text.setTextSize(size); text.setTextColor(color); if (bold) text.setTypeface(AndroidUtilities.bold()); return text; }
    private String plural(int count) { int n = count % 100, n1 = count % 10; return n > 10 && n < 20 ? "отзывов" : n1 == 1 ? "отзыв" : n1 >= 2 && n1 <= 4 ? "отзыва" : "отзывов"; }
}

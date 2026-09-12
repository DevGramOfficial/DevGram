package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class DevGramPluginDetailsActivity extends BaseFragment {
    private final DevGramPlugins.CatalogEntry entry;
    private LinearLayout content;
    private TextView ratingView;
    private TextView heroRatingView;
    private TextView reviewButton;
    private TextView reportButton;
    private DevGramPlugins.Review ownReview;

    public DevGramPluginDetailsActivity(DevGramPlugins.CatalogEntry entry) {
        this.entry = entry;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(entry.name);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(30));
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        LinearLayout hero = section(context, 22);
        LinearLayout heroRow = new LinearLayout(context); heroRow.setGravity(Gravity.TOP);
        ImageView avatar = new ImageView(context); avatar.setImageResource(R.drawable.devgram_plugins); avatar.setColorFilter(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider)); avatar.setPadding(AndroidUtilities.dp(17),AndroidUtilities.dp(17),AndroidUtilities.dp(17),AndroidUtilities.dp(17)); avatar.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(20),Theme.getColor(Theme.key_featuredStickers_addButton,resourceProvider))); if(entry.icon!=null&&!entry.icon.isEmpty())loadIcon(avatar,entry.icon); heroRow.addView(avatar,LayoutHelper.createLinear(82,82,Gravity.TOP,0,0,16,0));
        LinearLayout heroInfo = new LinearLayout(context); heroInfo.setOrientation(LinearLayout.VERTICAL);
        TextView heroTitle = text(context, entry.name, 24, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)); heroInfo.addView(heroTitle);
        String meta = (entry.version.isEmpty() ? "" : "v" + entry.version) + (entry.author.isEmpty() ? "" : (entry.version.isEmpty() ? "" : "  •  ") + entry.author);
        if (!meta.isEmpty()) heroInfo.addView(text(context, meta, 13, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resourceProvider)),LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,LayoutHelper.WRAP_CONTENT,0,4,0,0));
        heroRatingView = text(context, entry.rating > 0 ? String.format(java.util.Locale.US, "★ %.1f  ·  %d отзывов", entry.rating, entry.reviews) : "Новый плагин", 13, true, 0xFFE0A400); heroInfo.addView(heroRatingView,LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,LayoutHelper.WRAP_CONTENT,0,8,0,0));
        heroRow.addView(heroInfo,LayoutHelper.createLinear(0,LayoutHelper.WRAP_CONTENT,1f)); hero.addView(heroRow);
        content.addView(hero, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        if (!entry.author.isEmpty() && entry.author.contains("@")) addLink("Открыть автора  →", entry.author);
        if (!entry.channel.isEmpty()) addLink("🧩 " + entry.channel, entry.channel);
        if (entry.submittedAt > 0) {
            String dates = "Опубликован: " + android.text.format.DateFormat.format("dd.MM.yyyy", entry.submittedAt);
            if (entry.updatedAt > entry.submittedAt) dates += "  ·  Обновлён: " + android.text.format.DateFormat.format("dd.MM.yyyy", entry.updatedAt);
            addText(dates, 13, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        if (!entry.desc.isEmpty()) { LinearLayout info = section(context, 18); info.addView(text(context, "О плагине", 18, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))); info.addView(text(context, entry.desc, 15, false, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0)); content.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 4)); }

        TextView install = button(context, DevGramPlugins.isInstalled(entry.id) ? "Обновить плагин" : "Установить плагин");
        install.setOnClickListener(v -> {
            if (entry.isPackage) {
                // .dgplugin-пакет: исходника нет — качаем бинарь из архивного канала.
                // Раньше тут звался install(entry.source), а у пакета source пустой → «Не удалось».
                if (entry.packageMsg == 0) {
                    BulletinFactory.of(this).createErrorBulletin("Пакет ещё не размещён в архиве").show();
                    return;
                }
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Скачиваю пакет «" + entry.name + "»…").show();
                org.telegram.messenger.DevGramPackages.installCatalogPackage(entry, ok ->
                        BulletinFactory.of(this).createSimpleBulletin(ok ? R.raw.contact_check : R.raw.error,
                                ok ? "Плагин установлен: " + entry.name : "Не удалось установить пакет").show());
                return;
            }
            if (entry.source == null || entry.source.isEmpty()) {
                BulletinFactory.of(this).createErrorBulletin("У плагина нет исходника в каталоге").show();
                return;
            }
            DevGramPlugins.trustFromChannel(entry.source);
            if (DevGramPlugins.install(entry.source, entry.id, true))
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Плагин установлен").show();
            else BulletinFactory.of(this).createErrorBulletin("Не удалось установить плагин").show();
        });
        content.addView(install, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54, 0, 16, 0, 18));

        // DevGram: автор опубликованного плагина может обновить файл в каталоге БЕЗ модерации —
        // статистика (рейтинг/отзывы) сохраняется, меняется только файл и версия.
        if (entry.submitterId != 0 && entry.submitterId == DevGramPlugins.myId()) {
            // Кнопку показываем только если установленный файл НОВЕЕ/отличается от каталога —
            // иначе обновлять нечего (после апдейта версии совпадают → «✓ Актуально»).
            String installedVer = installedPluginVersion(entry.id);
            boolean sameVersion = installedVer != null && !installedVer.isEmpty()
                    && installedVer.equals(entry.version);
            if (sameVersion) {
                TextView okBtn = button(context, "✓ Актуально в каталоге");
                okBtn.setEnabled(false);
                okBtn.setAlpha(0.6f);
                content.addView(okBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54, 0, 0, 0, 18));
            } else {
                TextView updateBtn = button(context, "Обновить в каталоге");
                updateBtn.setOnClickListener(v -> publishCatalogUpdate(context, updateBtn));
                content.addView(updateBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54, 0, 0, 0, 18));
                addText("Заменит файл в каталоге без модерации, сохранив рейтинг и отзывы. Сначала установите обновлённую версию плагина.",
                        13, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            }
        }

        addSectionTitle("Отзывы");
        ratingView = addText("Загрузка рейтинга…", 14, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        DevGramPlugins.fetchReviews(entry.id, reviews -> {
            ownReview = null;
            double sum = 0;
            for (DevGramPlugins.Review r : reviews) {
                sum += r.rating;
                if (r.userId == DevGramPlugins.myId()) ownReview = r;
            }
            updateRatingLabels(reviews, sum);
            int shown = 0;
            for (DevGramPlugins.Review r : reviews) {
                if (shown++ >= 3) break;
                LinearLayout review = reviewCard(context,r);
                if (r.userId == DevGramPlugins.myId()) {
                    review.setOnClickListener(v -> showReviewActions(context,r,review));
                }
            }
            if (reviewButton != null) reviewButton.setText(ownReview == null ? "Оставить отзыв" : "Изменить мой отзыв");
        });

        reviewButton = button(context, "Оставить отзыв");
        reviewButton.setOnClickListener(v -> showReviewDialog(context, ownReview));
        content.addView(reviewButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 12, 0, 0));
        TextView allReviews = new TextView(context);
        allReviews.setText("Все отзывы"); allReviews.setGravity(Gravity.CENTER); allReviews.setTextSize(14);
        allReviews.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider)); allReviews.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4));
        allReviews.setOnClickListener(v -> presentFragment(new DevGramPluginReviewsActivity(entry)));
        content.addView(allReviews, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView history = new TextView(context);
        history.setText("История публикации"); history.setGravity(Gravity.CENTER); history.setTextSize(14);
        history.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider)); history.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(8));
        history.setOnClickListener(v -> presentFragment(new DevGramPluginHistoryActivity(entry.id, entry.name)));
        content.addView(history, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        reportButton = new TextView(context);
        reportButton.setText("Пожаловаться на плагин"); reportButton.setGravity(Gravity.CENTER); reportButton.setTextSize(14);
        reportButton.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider)); reportButton.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(8));
        reportButton.setOnClickListener(v -> showPluginReportMenu(context));
        content.addView(reportButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        DevGramPlugins.hasReportedPlugin(entry.id, this::setPluginReportState);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(content, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        return fragmentView = scroll;
    }

    private TextView addText(String text, int size, boolean bold, int color) {
        TextView t = new TextView(getContext()); t.setText(text); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(AndroidUtilities.bold());
        t.setPadding(0, AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3));
        content.addView(t, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));
        return t;
    }

    private TextView addSectionTitle(String value) {
        TextView title = addText(value.toUpperCase(java.util.Locale.ROOT), 12, true,
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        title.setLetterSpacing(0.08f);
        title.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(5));
        return title;
    }

    // DevGram: опубликовать ОБНОВЛЕНИЕ своего плагина (update=true). Модбот применит без
    // модерации (тот же автор), заменив только файл + версию; статистика сохранится.
    private void publishCatalogUpdate(Context context, final TextView btn) {
        java.io.File f = DevGramPlugins.pluginInstalledFile(entry.id);
        if (f == null) {
            BulletinFactory.of(this).createErrorBulletin(
                    "Сначала установите обновлённую версию плагина, затем нажмите «Обновить в каталоге»").show();
            return;
        }
        final String path = f.getAbsolutePath();
        DevGramPlugins.CatalogEntry ce = new DevGramPlugins.CatalogEntry();
        ce.id = entry.id;
        ce.name = entry.name;
        ce.author = entry.author;
        ce.desc = entry.desc;
        ce.icon = entry.icon;
        ce.channel = entry.channel;
        ce.filter = entry.filter;
        ce.version = entry.version;
        ce.update = true;
        // Кнопка меняет состояние прямо на экране — без перезахода в меню.
        if (btn != null) { btn.setText("⏳ Отправляется…"); btn.setOnClickListener(null); }
        final DevGramPlugins.SubmissionCallback cb = r -> {
            if (r == 1) {
                if (btn != null) btn.setText("⏳ Применяется…");
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                        "Обновление отправлено — применяется, статистика сохранится").show();
                waitCatalogApplied(entry.id, 0, () -> {
                    if (btn != null) {
                        btn.setText("✓ Обновлено в каталоге");
                        btn.setOnClickListener(null);
                    }
                }, () -> {
                    if (btn != null) {
                        btn.setText("Обновить в каталоге");
                        btn.setOnClickListener(v -> publishCatalogUpdate(context, btn));
                    }
                });
            } else {
                if (btn != null) {
                    btn.setText("Обновить в каталоге");
                    btn.setOnClickListener(v -> publishCatalogUpdate(context, btn));
                }
                String msg = (r == -1 ? "Плагин заблокирован — обновление запрещено"
                        : (r == -2 ? "Файл не прошёл проверку" : "Не удалось отправить обновление, попробуйте ещё раз"));
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, msg).show();
            }
        };
        if (path.endsWith(".dgplugin")) {
            String verr = DevGramPlugins.packageValidationError(path);
            if (!verr.isEmpty()) { BulletinFactory.of(this).createErrorBulletin(verr).show(); return; }
            String[] m = DevGramPlugins.packageMeta(path).split("\u001f", -1);
            if (m.length > 2 && !m[2].isEmpty()) ce.version = m[2]; // новая версия из файла
            org.telegram.messenger.DevGramPackages.publishPackage(path, ce, cb);
        } else {
            String src = readFileText(f);
            if (src == null || src.isEmpty()) {
                BulletinFactory.of(this).createErrorBulletin("Не удалось прочитать файл плагина").show();
                return;
            }
            String meta = DevGramPlugins.parseMeta(src);
            if (meta == null || meta.isEmpty()) {
                BulletinFactory.of(this).createErrorBulletin("Это не похоже на плагин DevGram").show();
                return;
            }
            String[] m = meta.split("\u001f", -1);
            if (m.length > 2 && !m[2].isEmpty()) ce.version = m[2];
            ce.isPackage = false;
            ce.source = src;
            DevGramPlugins.publishToCatalog(ce, cb);
        }
    }

    // Версия установленного файла плагина (.py или .dgplugin) или "" если не установлен.
    private static String installedPluginVersion(String pluginId) {
        try {
            java.io.File f = DevGramPlugins.pluginInstalledFile(pluginId);
            if (f == null) return "";
            String path = f.getAbsolutePath();
            String meta = path.endsWith(".dgplugin")
                    ? DevGramPlugins.packageMeta(path)
                    : DevGramPlugins.parseMeta(readFileText(f));
            if (meta == null || meta.isEmpty()) return "";
            String[] m = meta.split("", -1);
            return m.length > 2 ? m[2] : "";
        } catch (Throwable e) {
            return "";
        }
    }

    // Ждём, пока сервер применит обновление автора (заявка уйдёт из pending) → «✓ Обновлено».
    private void waitCatalogApplied(String pluginId, int attempt, Runnable onDone, Runnable onTimeout) {
        if (attempt >= 8) { if (onTimeout != null) onTimeout.run(); return; }
        AndroidUtilities.runOnUIThread(() -> DevGramPlugins.getPluginSubmissionStatus(pluginId, "", status -> {
            if (status == 2) {
                if (onDone != null) onDone.run();
            } else {
                waitCatalogApplied(pluginId, attempt + 1, onDone, onTimeout);
            }
        }), attempt == 0 ? 6000 : 8000);
    }

    private static String readFileText(java.io.File f) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        } catch (Throwable e) {
            return null;
        }
    }

    private LinearLayout section(Context context, int radius) { LinearLayout box = new LinearLayout(context); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(16)); box.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(radius), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider))); box.setElevation(AndroidUtilities.dp(2)); return box; }
    private TextView text(Context context, String value, int size, boolean bold, int color) { TextView t = new TextView(context); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(AndroidUtilities.dp(2), 1f); if (bold) t.setTypeface(AndroidUtilities.bold()); return t; }

    private void addLink(String label, String target) {
        TextView t = addText(label, 14, false, Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        t.setOnClickListener(v -> Browser.openUrl(getContext(), target.startsWith("http") ? target : "https://t.me/" + target.replace("@", "")));
    }

    private TextView button(Context c, String text) {
        TextView t = new TextView(c); t.setText(text); t.setGravity(Gravity.CENTER); t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        t.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider), Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        return t;
    }

    private void showReviewDialog(Context context, DevGramPlugins.Review existing) {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        LinearLayout stars = new LinearLayout(context);
        stars.setGravity(Gravity.CENTER);
        final int[] selected = {existing == null ? 5 : existing.rating};
        TextView[] starViews = new TextView[5];
        for (int i = 0; i < starViews.length; i++) {
            final int score = i + 1;
            TextView star = new TextView(context);
            star.setTextSize(32); star.setGravity(Gravity.CENTER); star.setTextColor(0xFFE0A400);
            star.setOnClickListener(v -> { selected[0] = score; updateStars(starViews, selected[0]); });
            starViews[i] = star;
            stars.addView(star, LayoutHelper.createLinear(46, 52));
        }
        updateStars(starViews, selected[0]);
        form.addView(stars, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
        EditText input = new EditText(context); input.setHint("Ваш отзыв"); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.setMinLines(3);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        input.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        input.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_dialogBackground, resourceProvider)));
        input.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        if (existing != null) input.setText(existing.text);
        form.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        new org.telegram.ui.ActionBar.AlertDialog.Builder(context).setTitle(existing == null ? "Отзыв о плагине" : "Изменить отзыв").setView(form)
                .setPositiveButton(existing == null ? "Отправить" : "Сохранить", (d, w) -> DevGramPlugins.saveReview(entry.id, selected[0], input.getText().toString(), ok -> {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, ok ? "Отзыв сохранён" : "Не удалось сохранить отзыв").show();
                    if (ok) DevGramPlugins.fetchReviews(entry.id, reviews -> {
                        double sum = 0; for (DevGramPlugins.Review r : reviews) sum += r.rating;
                        for (DevGramPlugins.Review r : reviews) if (r.userId == DevGramPlugins.myId()) ownReview = r;
                        if (reviewButton != null) reviewButton.setText("Изменить мой отзыв");
                        updateRatingLabels(reviews, sum);
                    });
                }))
                .setNegativeButton("Отмена", null).show();
    }

    private void updateStars(TextView[] stars, int selected) {
        for (int i = 0; i < stars.length; i++) stars[i].setText(i < selected ? "★" : "☆");
    }

    private void updateRatingLabels(java.util.ArrayList<DevGramPlugins.Review> reviews, double sum) {
        String compact = reviews.isEmpty() ? "Новый плагин" : String.format(java.util.Locale.US, "★ %.1f  ·  %d отзывов", sum / reviews.size(), reviews.size());
        if (ratingView != null) ratingView.setText(reviews.isEmpty() ? "Пока нет отзывов" : String.format(java.util.Locale.US, "%.1f ★  ·  %d отзывов", sum / reviews.size(), reviews.size()));
        if (heroRatingView != null) heroRatingView.setText(compact);
    }

    private void showCustomReportDialog(Context context, long reviewUserId) {
        EditText input = new EditText(context);
        input.setHint("Что именно нарушает этот отзыв?");
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        input.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setMaxLines(6);
        new org.telegram.ui.ActionBar.AlertDialog.Builder(context)
                .setTitle("Причина жалобы")
                .setView(input)
                .setPositiveButton("Отправить", (d, w) -> {
                    String reason = input.getText() == null ? "" : input.getText().toString().trim();
                    if (reason.isEmpty()) BulletinFactory.of(this).createErrorBulletin("Напишите причину жалобы").show();
                    else sendReviewReport(reviewUserId, reason);
                })
                .setNegativeButton("Отмена", null).show();
    }

    private void sendReviewReport(long reviewUserId, String reason) {
        DevGramPlugins.reportReview(entry.id, reviewUserId, reason, ok -> {
            if (!ok) DevGramPlugins.hasReportedReview(entry.id, reviewUserId, reported -> {
                if (reported) BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Вы уже подавали жалобу").show();
            });
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, ok ? "Жалоба отправлена" : "Не удалось отправить жалобу").show();
        });
    }

    private LinearLayout reviewCard(Context context, DevGramPlugins.Review r){LinearLayout card=section(context,16);LinearLayout head=new LinearLayout(context);head.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(context,"★".repeat(Math.max(0,Math.min(5,r.rating)))+"  "+r.name,14,true,0xFFE0A400);head.addView(title,LayoutHelper.createLinear(0,LayoutHelper.WRAP_CONTENT,1f));TextView more=text(context,"⋮",28,true,Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resourceProvider));more.setGravity(Gravity.CENTER);more.setOnClickListener(v->showReviewActions(context,r,card));head.addView(more,LayoutHelper.createLinear(42,42));card.addView(head);card.addView(text(context,r.text,14,false,Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,resourceProvider)));content.addView(card,LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,LayoutHelper.WRAP_CONTENT,0,6,0,6));return card;}
    private void showReviewActions(Context context,DevGramPlugins.Review r,View card){boolean own=r.userId==DevGramPlugins.myId(),dev=org.telegram.messenger.DevGramBadges.hasDeveloperFeatures(DevGramPlugins.myId());if(!own){DevGramPlugins.hasReportedReview(entry.id,r.userId,reported->showReviewActionsResolved(context,r,card,dev,reported));return;}showReviewActionsResolved(context,r,card,dev,false);}
    private void showReviewActionsResolved(Context context,DevGramPlugins.Review r,View card,boolean dev,boolean reported){boolean own=r.userId==DevGramPlugins.myId();java.util.ArrayList<String>a=new java.util.ArrayList<>();if(own){a.add("Изменить отзыв");a.add("Удалить отзыв");}else{a.add(reported?"Вы уже подавали жалобу":"Пожаловаться на отзыв");if(dev)a.add("Удалить отзыв");}new org.telegram.ui.ActionBar.AlertDialog.Builder(context).setTitle(r.name).setItems(a.toArray(new CharSequence[0]),(d,w)->{String x=a.get(w);if(x.startsWith("Изменить"))showReviewDialog(context,r);else if(x.startsWith("Пожаловаться"))showReportReasonMenu(context,r.userId);else if(x.startsWith("Вы уже"))BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,"Вы уже подавали жалобу").show();else if(own)DevGramPlugins.deleteOwnReview(entry.id,ok->afterReviewDeleted(ok,card));else DevGramPlugins.deleteReviewAsDeveloper(entry.id,r.userId,ok->afterReviewDeleted(ok,card));}).setNegativeButton("Закрыть",null).show();}
    private void afterReviewDeleted(boolean ok,View card){if(!ok){BulletinFactory.of(this).createErrorBulletin("Не удалось удалить отзыв").show();return;}card.setVisibility(View.GONE);ownReview=null;if(reviewButton!=null)reviewButton.setText("Оставить отзыв");DevGramPlugins.fetchReviews(entry.id,reviews->{double sum=0;for(DevGramPlugins.Review review:reviews)sum+=review.rating;updateRatingLabels(reviews,sum);});}
    void showReportReasonMenu(Context context,long uid){new org.telegram.ui.ActionBar.AlertDialog.Builder(context).setTitle("Жалоба на отзыв").setMessage("Выберите нарушение. Команда DevGram проверит отзыв и сообщит решение.").setItems(new CharSequence[]{"Спам или реклама","Оскорбления и травля","Ложная информация","Другая причина"},(d,w)->{if(w==3)showCustomReportDialog(context,uid);else sendReviewReport(uid,w==0?"Спам или реклама":w==1?"Оскорбления и травля":"Ложная информация");}).setNegativeButton("Отмена",null).show();}
    private void showPluginReportMenu(Context context){new org.telegram.ui.ActionBar.AlertDialog.Builder(context).setTitle("Жалоба на плагин").setMessage("Выберите, что не так с плагином.").setItems(new CharSequence[]{"Вредоносный код","Спам или обман","Нарушение авторских прав","Плагин не работает","Другая причина"},(d,w)->{if(w==4){EditText i=DevGramPluginReportsActivity.themedInput(context,"Что именно не так с плагином?");new org.telegram.ui.ActionBar.AlertDialog.Builder(context).setTitle("Своя причина").setMessage("Опишите проблему коротко и по существу.").setView(i).setPositiveButton("Отправить",(dd,ww)->sendPluginReport(i.getText().toString())).setNegativeButton("Отмена",null).show();}else sendPluginReport(new String[]{"Вредоносный код","Спам или обман","Нарушение авторских прав","Плагин не работает"}[w]);}).setNegativeButton("Отмена",null).show();}
    private void sendPluginReport(String reason){reason=reason==null?"":reason.trim();if(reason.isEmpty()){BulletinFactory.of(this).createErrorBulletin("Укажите причину").show();return;}DevGramPlugins.reportPlugin(entry.id,reason,ok->{if(ok)setPluginReportState(true);else DevGramPlugins.hasReportedPlugin(entry.id,reported->{if(reported)setPluginReportState(true);});BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,ok?"Жалоба отправлена команде":"Не удалось отправить жалобу").show();});}
    private void setPluginReportState(boolean reported){if(reportButton==null||!reported)return;reportButton.setText("Вы уже подавали жалобу");reportButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resourceProvider));reportButton.setOnClickListener(null);reportButton.setEnabled(false);reportButton.setAlpha(.75f);}
    private void loadIcon(ImageView view,String url){org.telegram.messenger.Utilities.globalQueue.postRunnable(()->{try{java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);android.graphics.Bitmap b=android.graphics.BitmapFactory.decodeStream(c.getInputStream());AndroidUtilities.runOnUIThread(()->{if(b!=null){view.clearColorFilter();view.setImageBitmap(b);}});}catch(Throwable ignore){}});}
}

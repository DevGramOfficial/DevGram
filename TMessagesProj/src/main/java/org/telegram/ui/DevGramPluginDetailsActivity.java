package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramPluginBlocks;
import org.telegram.messenger.DevGramPluginInfo;
import org.telegram.messenger.DevGramPluginTranslation;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.R;
import org.telegram.messenger.devgram.plugins.PluginAnalysisReport;
import org.telegram.messenger.devgram.plugins.PluginAnalyzer;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.DevGramPluginInfoViews;
import org.telegram.ui.Components.DevGramTranslateButton;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.devgram.PluginAnalysisReportSheet;
import org.telegram.ui.Components.devgram.PluginAnalysisViews;

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
        // одна ссылка, если автор и канал — один и тот же адрес; две — только если они разные
        String authorUser = username(entry.author), channelUser = username(entry.channel);
        if (!entry.channel.isEmpty()) addLink("🧩 " + entry.channel, entry.channel);
        if (authorUser != null && !authorUser.equalsIgnoreCase(channelUser)) addLink("Открыть автора @" + authorUser, "@" + authorUser);
        if (entry.submittedAt > 0) {
            String dates = "Опубликован: " + android.text.format.DateFormat.format("dd.MM.yyyy", entry.submittedAt);
            if (entry.updatedAt > entry.submittedAt) dates += "  ·  Обновлён: " + android.text.format.DateFormat.format("dd.MM.yyyy", entry.updatedAt);
            addText(dates, 13, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        if (!entry.desc.isEmpty()) {
            LinearLayout info = section(context, 18);
            LinearLayout infoHead = new LinearLayout(context);
            infoHead.setGravity(Gravity.CENTER_VERTICAL);
            infoHead.addView(text(context, "О плагине", 18, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            TextView descView = text(context, entry.desc, 15, false, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            infoHead.addView(translateButton(context, heroTitle, descView), LayoutHelper.createLinear(40, 40, 0, -8, -8, -8));
            info.addView(infoHead, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            info.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
            content.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 4));
        }
        addPluginInfo(context);

        final String installLabel = DevGramPlugins.isInstalled(entry.id) ? "Обновить плагин" : "Установить плагин";
        final String blockHash = entry.isPackage ? entry.packageSha : DevGramPlugins.sha256(entry.source);
        final DevGramPluginBlocks.Fingerprint blockFingerprint = entry.isPackage
                ? DevGramPluginBlocks.Fingerprint.EMPTY : DevGramPluginBlocks.fingerprintSource(entry.source);
        final boolean[] blockKnown = {false};
        final boolean[] blockState = {false};
        TextView install = button(context, "Проверяю блокировку…");
        install.setEnabled(false);
        Runnable performInstall = () -> {
            if (entry.isPackage) {
                // .dgplugin-пакет: исходника нет — качаем бинарь из архивного канала.
                // Раньше тут звался install(entry.source), а у пакета source пустой → «Не удалось».
                if (entry.packageMsg == 0) {
                    BulletinFactory.of(this).createErrorBulletin("Пакет ещё не размещён в архиве").show();
                    return;
                }
                install.setEnabled(false);
                install.setText("Скачиваю и проверяю…");
                org.telegram.messenger.DevGramPackages.installCatalogPackage(entry, ok -> {
                    install.setEnabled(!ok);
                    install.setAlpha(ok ? .72f : 1f);
                    install.setText(ok ? "✓ Плагин установлен" : "Повторить установку");
                    BulletinFactory.of(this).createSimpleBulletin(ok ? R.raw.contact_check : R.raw.error,
                            ok ? "Плагин установлен: " + entry.name : "Не удалось установить пакет").show();
                });
                return;
            }
            if (entry.source == null || entry.source.isEmpty()) {
                BulletinFactory.of(this).createErrorBulletin("У плагина нет исходника в каталоге").show();
                return;
            }
            DevGramPlugins.trustFromChannel(entry.source);
            if (DevGramPlugins.install(entry.source, entry.id, true)) {
                install.setText("✓ Плагин установлен");
                install.setEnabled(false);
                install.setAlpha(.72f);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Плагин установлен").show();
            } else BulletinFactory.of(this).createErrorBulletin("Не удалось установить плагин").show();
        };
        install.setOnClickListener(v -> {
            install.setEnabled(false);
            install.setText("Проверяю блокировку…");
            DevGramPluginBlocks.check(entry.id, blockHash, blockFingerprint, status -> {
                blockKnown[0] = status.success;
                blockState[0] = status.success && status.blocked;
                if (!status.success || status.blocked) {
                    install.setText(status.success ? "⛔ Установка заблокирована" : "Повторить проверку");
                    install.setEnabled(!status.success);
                    BulletinFactory.of(this).createErrorBulletin(
                            status.success ? "Установка этого плагина заблокирована" : status.reason).show();
                    return;
                }
                install.setText(installLabel);
                install.setEnabled(true);
                performInstall.run();
            });
        });
        content.addView(install, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54, 0, 16, 0, 18));

        final TextView[] blockButton = new TextView[1];
        Runnable applyBlockState = () -> {
            if (!blockKnown[0]) {
                install.setText("Повторить проверку");
                install.setEnabled(true);
            } else if (blockState[0]) {
                install.setText("⛔ Установка заблокирована");
                install.setEnabled(false);
            } else {
                install.setText(installLabel);
                install.setEnabled(true);
            }
            if (blockButton[0] != null) {
                blockButton[0].setEnabled(true);
                blockButton[0].setText(!blockKnown[0] ? "Повторить проверку блокировки  ›"
                        : (blockState[0] ? "Разблокировать установку  ›" : "Заблокировать установку  ›"));
            }
        };
        Runnable refreshBlock = () -> DevGramPluginBlocks.check(entry.id, blockHash, blockFingerprint, status -> {
            blockKnown[0] = status.success;
            blockState[0] = status.success && status.blocked;
            applyBlockState.run();
        });

        if (DevGramPluginBlocks.canManage(getCurrentAccount())) {
            TextView manageBlock = secondaryAction(context, "Заблокировать установку", true);
            blockButton[0] = manageBlock;
            manageBlock.setOnClickListener(v -> {
                if (!blockKnown[0]) {
                    manageBlock.setEnabled(false);
                    manageBlock.setText("Проверяю…");
                    refreshBlock.run();
                    return;
                }
                manageBlock.setEnabled(false);
                manageBlock.setText("Сохраняю…");
                DevGramPluginBlocks.setBlocked(getCurrentAccount(), entry.id, blockHash, entry.name, blockFingerprint,
                        !blockState[0], status -> {
                            if (status.success) {
                                blockKnown[0] = true;
                                blockState[0] = status.blocked;
                                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                                        status.blocked ? "Установка плагина заблокирована" : "Блокировка снята").show();
                            } else {
                                BulletinFactory.of(this).createErrorBulletin(status.reason).show();
                            }
                            applyBlockState.run();
                        });
            });
            content.addView(manageBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 18));
        }
        refreshBlock.run();

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
        TextView allReviews = secondaryAction(context, "Все отзывы", false);
        allReviews.setOnClickListener(v -> presentFragment(new DevGramPluginReviewsActivity(entry)));
        content.addView(allReviews, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 10, 0, 0));
        TextView history = secondaryAction(context, "История публикации", false);
        history.setOnClickListener(v -> presentFragment(new DevGramPluginHistoryActivity(entry.id, entry.name)));
        content.addView(history, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 8, 0, 0));
        reportButton = secondaryAction(context, "Пожаловаться на плагин", true);
        reportButton.setOnClickListener(v -> showPluginReportMenu(context));
        content.addView(reportButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 8, 0, 0));
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
                        : (r == -2 ? "Файл не прошёл проверку"
                        : (r == -3 ? "Пакет должен содержать открытые исходники .py"
                        : "Не удалось отправить обновление, попробуйте ещё раз")));
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

    private LinearLayout section(Context context, int radius) { LinearLayout box = new LinearLayout(context); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(16)); box.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(radius), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider))); box.setElevation(AndroidUtilities.dp(1)); return box; }
    private TextView text(Context context, String value, int size, boolean bold, int color) { TextView t = new TextView(context); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(AndroidUtilities.dp(2), 1f); if (bold) t.setTypeface(AndroidUtilities.bold()); return t; }

    // Перевод названия и описания в один тап — кнопка видна, только если язык другой
    private View translateButton(Context context, TextView titleView, TextView descView) {
        DevGramTranslateButton button = new DevGramTranslateButton(context, Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourceProvider));
        button.setVisibility(View.GONE);
        ArrayList<String> originals = new ArrayList<>();
        originals.add(entry.name);
        originals.add(entry.desc);
        DevGramPluginTranslation.checkNeeded(originals, needed -> button.setVisibility(needed ? View.VISIBLE : View.GONE));
        button.setOnClickListener(v -> {
            int st = button.getState();
            if (st == DevGramTranslateButton.STATE_LOADING) return;
            if (st == DevGramTranslateButton.STATE_RESET) {
                titleView.setText(entry.name);
                descView.setText(entry.desc);
                button.setState(DevGramTranslateButton.STATE_TRANSLATE);
                return;
            }
            button.setState(DevGramTranslateButton.STATE_LOADING);
            DevGramPluginTranslation.translate(originals, map -> {
                if (map == null) {
                    button.setState(DevGramTranslateButton.STATE_TRANSLATE);
                    BulletinFactory.of(this).createErrorBulletin("Не удалось перевести").show();
                    return;
                }
                if (map.containsKey(entry.name)) titleView.setText(map.get(entry.name));
                if (map.containsKey(entry.desc)) descView.setText(map.get(entry.desc));
                button.setState(DevGramTranslateButton.STATE_RESET);
            });
        });
        return button;
    }

    private static String username(String value) {
        if (value == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:@|t\\.me/)([A-Za-z0-9_]{4,})").matcher(value);
        return m.find() ? m.group(1) : null;
    }

    // В каталоге используется тот же отчёт проверки, что и при открытии файла плагина.
    // Для пакета сначала загружаем весь .dgplugin: анализировать только main.py небезопасно.
    private void addPluginInfo(Context context) {
        LinearLayout analysisBox = section(context, 18);
        analysisBox.setPadding(0, 0, 0, 0);
        PluginAnalysisViews.SummaryView summary = new PluginAnalysisViews.SummaryView(context, resourceProvider);
        analysisBox.addView(summary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(analysisBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 4));

        LinearLayout infoBox = section(context, 18);
        infoBox.addView(text(context, "Сведения", 18, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)));
        DevGramPluginInfoViews.addRow(context, infoBox, "ID", entry.id, null);
        if (!entry.version.isEmpty()) DevGramPluginInfoViews.addRow(context, infoBox, "Версия", entry.version, null);
        if (!entry.author.isEmpty()) DevGramPluginInfoViews.addRow(context, infoBox, "Автор", entry.author, null);
        DevGramPluginInfoViews.addRow(context, infoBox, "Формат", entry.isPackage ? ".dgplugin — пакет" : "исходник Python", null);
        LinearLayout codeRows = new LinearLayout(context);
        codeRows.setOrientation(LinearLayout.VERTICAL);
        infoBox.addView(codeRows, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(infoBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 4));

        if (entry.isPackage) {
            if (entry.packageSize > 0) DevGramPluginInfoViews.addRow(context, codeRows, "Размер", DevGramPluginInfo.formatSize(entry.packageSize), null);
            addHashRow(context, codeRows, entry.packageSha);
            loadPackageCode(context, summary, codeRows, 0);
        } else {
            String src = entry.source == null ? "" : entry.source;
            if (!src.isEmpty()) {
                DevGramPluginInfoViews.addRow(context, codeRows, "Размер",
                        DevGramPluginInfo.formatSize(src.getBytes(java.nio.charset.StandardCharsets.UTF_8).length), null);
            }
            fillCodeMetadata(context, codeRows, src, true);
            if (!src.isEmpty()) addHashRow(context, codeRows, DevGramPlugins.sha256(src));
            analyzePlugin(summary, src, null);
        }
    }

    /**
     * После одобрения modbot переносит пакет в архив и дописывает packageMsg не мгновенно.
     * Карточка могла открыться в это короткое окно и навсегда оставить сообщение об ошибке.
     * Обновляем запись каталога и мягко повторяем скачивание, пока координаты не появились.
     */
    private void loadPackageCode(Context context, PluginAnalysisViews.SummaryView summary,
                                 LinearLayout codeRows, int attempt) {
        if (getParentActivity() == null) return;
        if (entry.packageMsg == 0) {
            if (attempt >= 12) {
                summary.setFailed(PluginAnalyzer.STATUS_UNAVAILABLE);
                return;
            }
            summary.setRunning("Плагин размещается в каталоге…", "Отчёт появится сразу после загрузки файла");
            DevGramPlugins.fetchCatalogEntry(entry.id, freshEntries -> {
                if (!freshEntries.isEmpty()) {
                    DevGramPlugins.CatalogEntry fresh = freshEntries.get(0);
                    entry.packageChat = fresh.packageChat;
                    entry.packageMsg = fresh.packageMsg;
                    if (fresh.packageSize > 0) entry.packageSize = fresh.packageSize;
                    if (fresh.packageSha != null && !fresh.packageSha.isEmpty()) entry.packageSha = fresh.packageSha;
                }
                AndroidUtilities.runOnUIThread(
                        () -> loadPackageCode(context, summary, codeRows, attempt + 1), 2500);
            });
            return;
        }
        summary.setRunning(attempt == 0 ? "Загружаем плагин…" : "Загружаем плагин, попытка " + (attempt + 1) + "…",
                "После загрузки проверим весь пакет");
        org.telegram.messenger.DevGramPackages.fetchCatalogPackage(entry, file -> {
            if (file == null) {
                if (attempt < 4) {
                    AndroidUtilities.runOnUIThread(
                            () -> loadPackageCode(context, summary, codeRows, attempt + 1), 2000);
                } else {
                    summary.setFailed(PluginAnalyzer.STATUS_UNAVAILABLE);
                }
                return;
            }
            String path = file.getAbsolutePath();
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                String src = DevGramPlugins.packageMainSource(path);
                AndroidUtilities.runOnUIThread(() -> {
                    fillCodeMetadata(context, codeRows, src, false);
                    analyzePlugin(summary, null, path);
                });
            });
        });
    }

    private void fillCodeMetadata(Context context, LinearLayout codeRows, String src, boolean beforeHash) {
        if (getParentActivity() == null) return;
        int at = beforeHash ? codeRows.getChildCount() : 0;
        String minVersion = DevGramPluginInfo.metaValue(src, "__min_version__");
        int lines = DevGramPluginInfo.codeLines(src);
        LinearLayout tmp = new LinearLayout(context);
        tmp.setOrientation(LinearLayout.VERTICAL);
        if (minVersion != null) DevGramPluginInfoViews.addRow(context, tmp, "Мин. версия DevGram", minVersion, null);
        if (lines > 0) DevGramPluginInfoViews.addRow(context, tmp, "Строк кода", String.valueOf(lines), null);
        while (tmp.getChildCount() > 0) {
            View row = tmp.getChildAt(0);
            tmp.removeView(row);
            codeRows.addView(row, at++);
        }
    }

    private void analyzePlugin(PluginAnalysisViews.SummaryView summary, String source, String packagePath) {
        java.io.File installed = DevGramPlugins.pluginInstalledFile(entry.id);
        String previous = installed != null && installed.isFile() ? installed.getAbsolutePath() : null;
        PluginAnalyzer.analyze(source, packagePath, entry.name, previous, result -> {
            if (getParentActivity() == null) return;
            if (result.status != PluginAnalyzer.STATUS_OK || result.report == null) {
                summary.setFailed(result.status);
                return;
            }
            PluginAnalysisReport report = result.report;
            summary.setReport(report, result.newStatements.size(), v -> {
                if (getParentActivity() == null) return;
                new PluginAnalysisReportSheet(this, report, result.newStatements, null, null).show();
            });
        });
    }

    private void addHashRow(Context context, LinearLayout box, String hash) {
        if (hash == null || hash.isEmpty()) return;
        DevGramPluginInfoViews.addRow(context, box, "SHA-256", DevGramPluginInfoViews.shortHash(hash), () -> {
            AndroidUtilities.addToClipboard(hash);
            BulletinFactory.of(this).createCopyBulletin("Хеш скопирован").show();
        });
    }

    private void addLink(String label, String target) {
        TextView t = secondaryAction(getContext(), label, false);
        t.setOnClickListener(v -> Browser.openUrl(getContext(), target.startsWith("http") ? target : "https://t.me/" + target.replace("@", "")));
        content.addView(t, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 0, 0, 8));
    }

    private TextView secondaryAction(Context context, String value, boolean danger) {
        TextView t = new TextView(context);
        t.setText(value + "  ›");
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        t.setTextSize(14);
        t.setTypeface(AndroidUtilities.bold());
        int color = Theme.getColor(danger ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteBlueText, resourceProvider);
        t.setTextColor(color);
        t.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                Theme.getColor(Theme.key_listSelector, resourceProvider)));
        ScaleStateListAnimator.apply(t, .02f, 1.1f);
        return t;
    }

    private TextView button(Context c, String text) {
        TextView t = new TextView(c); t.setText(text); t.setGravity(Gravity.CENTER); t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        t.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider), Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        ScaleStateListAnimator.apply(t, .025f, 1.2f);
        return t;
    }

    private void showReviewDialog(Context context, DevGramPlugins.Review existing) {
        DevGramPluginUi.showReview(this, existing == null ? 5 : existing.rating,
                existing == null ? null : existing.text, (rating, reviewText) ->
                DevGramPlugins.saveReview(entry.id, rating, reviewText, ok -> {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, ok ? "Отзыв сохранён" : "Не удалось сохранить отзыв").show();
                    if (ok) DevGramPlugins.fetchReviews(entry.id, reviews -> {
                        double sum = 0; for (DevGramPlugins.Review r : reviews) sum += r.rating;
                        for (DevGramPlugins.Review r : reviews) if (r.userId == DevGramPlugins.myId()) ownReview = r;
                        if (reviewButton != null) reviewButton.setText("Изменить мой отзыв");
                        updateRatingLabels(reviews, sum);
                    });
                }));
    }

    private void updateRatingLabels(java.util.ArrayList<DevGramPlugins.Review> reviews, double sum) {
        String compact = reviews.isEmpty() ? "Новый плагин" : String.format(java.util.Locale.US, "★ %.1f  ·  %d отзывов", sum / reviews.size(), reviews.size());
        if (ratingView != null) ratingView.setText(reviews.isEmpty() ? "Пока нет отзывов" : String.format(java.util.Locale.US, "%.1f ★  ·  %d отзывов", sum / reviews.size(), reviews.size()));
        if (heroRatingView != null) heroRatingView.setText(compact);
    }

    private void showCustomReportDialog(Context context, long reviewUserId) {
        DevGramPluginUi.showTextInput(this, "Своя причина",
                "Опишите нарушение коротко и по существу.", "Что именно нарушает этот отзыв?",
                "", "Отправить жалобу", true, reason -> sendReviewReport(reviewUserId, reason));
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
    private void showReviewActionsResolved(Context context, DevGramPlugins.Review r, View card, boolean dev, boolean reported) {
        boolean own = r.userId == DevGramPlugins.myId();
        java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> icons = new java.util.ArrayList<>();
        if (own) {
            actions.add("Изменить отзыв"); icons.add(R.drawable.msg_edit);
            actions.add("Удалить отзыв"); icons.add(R.drawable.msg_delete);
        } else {
            actions.add(reported ? "Жалоба уже отправлена" : "Пожаловаться на отзыв"); icons.add(R.drawable.msg_report);
            if (dev) { actions.add("Удалить отзыв"); icons.add(R.drawable.msg_delete); }
        }
        int[] iconArray = new int[icons.size()];
        for (int i = 0; i < icons.size(); i++) iconArray[i] = icons.get(i);
        DevGramPluginUi.showChoices(this, r.name, "Действия с отзывом", actions.toArray(new String[0]),
                iconArray, actions.size() - (actions.get(actions.size() - 1).startsWith("Удалить") ? 1 : 0), which -> {
                    String action = actions.get(which);
                    if (action.startsWith("Изменить")) showReviewDialog(context, r);
                    else if (action.startsWith("Пожаловаться")) showReportReasonMenu(context, r.userId);
                    else if (action.startsWith("Жалоба")) BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Вы уже подавали жалобу").show();
                    else if (own) DevGramPlugins.deleteOwnReview(entry.id, ok -> afterReviewDeleted(ok, card));
                    else DevGramPlugins.deleteReviewAsDeveloper(entry.id, r.userId, ok -> afterReviewDeleted(ok, card));
                });
    }
    private void afterReviewDeleted(boolean ok,View card){if(!ok){BulletinFactory.of(this).createErrorBulletin("Не удалось удалить отзыв").show();return;}card.setVisibility(View.GONE);ownReview=null;if(reviewButton!=null)reviewButton.setText("Оставить отзыв");DevGramPlugins.fetchReviews(entry.id,reviews->{double sum=0;for(DevGramPlugins.Review review:reviews)sum+=review.rating;updateRatingLabels(reviews,sum);});}
    void showReportReasonMenu(Context context,long uid){String[] reasons={"Спам или реклама","Оскорбления и травля","Ложная информация","Другая причина"};DevGramPluginUi.showChoices(this,"Жалоба на отзыв","Команда DevGram проверит отзыв и сообщит решение.",reasons,new int[]{R.drawable.msg_report,R.drawable.msg_block,R.drawable.msg_info,R.drawable.msg_edit},reasons.length,w->{if(w==3)showCustomReportDialog(context,uid);else sendReviewReport(uid,reasons[w]);});}
    private void showPluginReportMenu(Context context){String[] reasons={"Вредоносный код","Спам или обман","Нарушение авторских прав","Плагин не работает","Другая причина"};DevGramPluginUi.showChoices(this,"Жалоба на плагин","Выберите, что именно нужно проверить команде DevGram.",reasons,new int[]{R.drawable.msg_block,R.drawable.msg_report,R.drawable.msg_copy,R.drawable.msg_retry,R.drawable.msg_edit},reasons.length,w->{if(w==4)DevGramPluginUi.showTextInput(this,"Своя причина","Опишите проблему коротко и по существу.","Что именно не так с плагином?","","Отправить жалобу",true,this::sendPluginReport);else sendPluginReport(reasons[w]);});}
    private void sendPluginReport(String reason){reason=reason==null?"":reason.trim();if(reason.isEmpty()){BulletinFactory.of(this).createErrorBulletin("Укажите причину").show();return;}DevGramPlugins.reportPlugin(entry.id,reason,ok->{if(ok)setPluginReportState(true);else DevGramPlugins.hasReportedPlugin(entry.id,reported->{if(reported)setPluginReportState(true);});BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,ok?"Жалоба отправлена команде":"Не удалось отправить жалобу").show();});}
    private void setPluginReportState(boolean reported){if(reportButton==null||!reported)return;reportButton.setText("Вы уже подавали жалобу");reportButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,resourceProvider));reportButton.setOnClickListener(null);reportButton.setEnabled(false);reportButton.setAlpha(.75f);}
    private void loadIcon(ImageView view,String url){org.telegram.messenger.Utilities.globalQueue.postRunnable(()->{try{java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);android.graphics.Bitmap b=android.graphics.BitmapFactory.decodeStream(c.getInputStream());AndroidUtilities.runOnUIThread(()->{if(b!=null){view.clearColorFilter();view.setImageBitmap(b);}});}catch(Throwable ignore){}});}
}

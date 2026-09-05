/*
 * DevGram: лист установки плагина. Появляется при тапе на файл .plugin/.dgplugin в чате/канале —
 * показывает метаданные (иконка, имя, версия, автор, описание), бейдж проверки, предупреждение,
 * возможности и кнопку установки. Карточка ЕДИНАЯ для обоих форматов (см. showCard).
 */

package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.DevGramBadges;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextBoldCursor;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;

public class DevGramPluginInstallSheet {

    // Интерфейс установки: реализация зависит от формата (.py-исходник или .dgplugin-архив).
    private interface InstallAction { boolean install(boolean enableAfter); }

    // Лист установки .dgplugin-пакета — ТА ЖЕ карточка, что и у .plugin (единая логика).
    public static void showPackage(BaseFragment fragment, String path, long sourceDialogId) {
        Context context = fragment.getParentActivity();
        if (context == null || path == null) return;
        String validationError = DevGramPlugins.packageValidationError(path);
        if (!validationError.isEmpty()) {
            org.telegram.ui.Components.BulletinFactory.of(fragment)
                    .createErrorBulletin(validationError).show();
            return;
        }
        String[] m = DevGramPlugins.packageMeta(path).split("\u001f", -1);
        final String id = m.length > 0 ? m[0] : "";
        String name = m.length > 1 && !m[1].isEmpty() ? m[1] : (id.isEmpty() ? "Плагин" : id);
        String ver = m.length > 2 ? m[2] : "";
        String author = m.length > 3 ? m[3] : "";
        String desc = m.length > 4 ? m[4] : "";
        String iconUrl = m.length > 5 ? m[5] : "";
        // возможности сканируем по входному файлу архива (как по исходнику у .plugin)
        String capabilities = DevGramPlugins.packageMainSource(path);
        // у пакета нет единого «source»-текста для реестра/каталога — verifySource=null:
        // это отключает source-зависимые кнопки (проверка/публикация), карточка при этом та же.
        String pkgSha = org.telegram.messenger.DevGramPackages.sha256File(new java.io.File(path));
        showCard(fragment, id, name, ver, author, desc, iconUrl, sourceDialogId, null, pkgSha, capabilities, path,
                enableAfter -> DevGramPlugins.installPackage(path, id, enableAfter));
    }

    public static void show(BaseFragment fragment, String source) {
        show(fragment, source, 0);
    }

    // Показать лист установки для исходника плагина (.plugin/.py).
    public static void show(BaseFragment fragment, String source, long sourceDialogId) {
        Context context = fragment.getParentActivity();
        if (context == null || source == null) {
            return;
        }
        String meta = DevGramPlugins.parseMeta(source);
        if (meta == null || meta.isEmpty()) {
            org.telegram.ui.Components.BulletinFactory.of(fragment)
                    .createErrorBulletin("Это не похоже на плагин DevGram").show();
            return;
        }
        String[] m = meta.split("\u001f", -1);
        final String id = m.length > 0 ? m[0] : "";
        String name = m.length > 1 && !m[1].isEmpty() ? m[1] : (id.isEmpty() ? "Плагин" : id);
        String ver = m.length > 2 ? m[2] : "";
        String author = m.length > 3 ? m[3] : "";
        String desc = m.length > 4 ? m[4] : "";
        String iconUrl = m.length > 5 ? m[5] : "";
        final String fSource = source;
        showCard(fragment, id, name, ver, author, desc, iconUrl, sourceDialogId, source, DevGramPlugins.sha256(source), source, null,
                enableAfter -> DevGramPlugins.install(fSource, id, enableAfter));
    }

    // ЕДИНАЯ карточка установки (BottomSheet) для .plugin и .dgplugin.
    // sourceDialogId — диалог, откуда открыт плагин (у канала отрицательный). Если это
    // канал-разработчик плагинов (значок 🧩), плагин автоматически считается проверенным.
    // verifySource != null → source-зависимые фичи (проверка команды, публикация в каталог)
    // включены (только для .py); для .dgplugin verifySource=null — они скрыты, карточка та же.
    private static void showCard(BaseFragment fragment, final String id, final String name,
                                 String ver, String author, String desc, String iconUrl,
                                 long sourceDialogId, final String verifySource, final String verifyHash, String capabilitiesSource,
                                 final String packagePath, final InstallAction installAction) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final boolean fromDevChannel = org.telegram.messenger.DevGramBadges.isPluginDevChannel(sourceDialogId);
        final boolean verified = fromDevChannel || (verifyHash != null && !verifyHash.isEmpty() && DevGramPlugins.isVerifiedByHash(verifyHash));
        final boolean installed = DevGramPlugins.isInstalled(id);
        if (fromDevChannel && verifyHash != null && !verifyHash.isEmpty()) {
            DevGramPlugins.trustFromChannelByHash(verifyHash);
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(12));

        // иконка (по умолчанию — общая; если у плагина задан icon-URL, подгрузим аву)
        android.widget.ImageView icon = new android.widget.ImageView(context);
        try {
            icon.setImageResource(R.drawable.devgram_cat_general);
        } catch (Throwable ignore) {
        }
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        icon.setClipToOutline(true);
        icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), AndroidUtilities.dp(16));
            }
        });
        if (!iconUrl.isEmpty()) {
            loadIcon(icon, iconUrl);
        }
        root.addView(icon, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        // название
        TextView title = new TextView(context);
        title.setText(name);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        // версия + автор
        StringBuilder sub = new StringBuilder();
        if (!ver.isEmpty()) {
            sub.append("Версия ").append(ver);
        }
        if (!author.isEmpty()) {
            if (sub.length() > 0) {
                sub.append("  •  ");
            }
            sub.append(author);
        }
        if (sub.length() > 0) {
            TextView subtitle = new TextView(context);
            subtitle.setText(sub.toString());
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setGravity(Gravity.CENTER);
            root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        }

        // бейдж проверки (как у exteraGram, в нашем стиле)
        TextView badge = new TextView(context);
        badge.setText(verified ? (fromDevChannel ? "🧩 Проверенный разработчик" : "🛡️ Проверено DevGram") : "⚠️ Неизвестный источник");
        badge.setTextColor(verified ? 0xFFFFFFFF : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        badge.setTypeface(AndroidUtilities.bold());
        badge.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(7), AndroidUtilities.dp(14), AndroidUtilities.dp(7));
        badge.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                verified ? 0xFF3BA55D : Theme.getColor(Theme.key_windowBackgroundGray)));
        root.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));

        // описание
        if (!desc.isEmpty()) {
            TextView description = new TextView(context);
            description.setText(desc);
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            description.setGravity(Gravity.CENTER);
            root.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        }

        // предупреждение / подтверждение (по статусу проверки)
        LinearLayout warn = new LinearLayout(context);
        warn.setOrientation(LinearLayout.VERTICAL);
        warn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                verified ? 0x223BA55D : Theme.getColor(Theme.key_windowBackgroundGray)));
        warn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        TextView warnText = new TextView(context);
        warnText.setText(verified
                ? (fromDevChannel
                    ? "✓ Проверенный источник\nПлагин из канала-разработчика плагинов DevGram (🧩). Такие каналы проверены командой — плагинам из них можно доверять."
                    : "✓ Проверенный плагин\nОн есть в реестре DevGram — код проверен, можно доверять.")
                : "ⓘ Внимание: неизвестный источник\nПлагина нет в реестре проверенных DevGram, он выполняет код в приложении. Устанавливай только если доверяешь автору.");
        warnText.setTextColor(verified ? 0xFF3BA55D : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        warnText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        warn.addView(warnText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(warn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));

        String lowerSource = capabilitiesSource == null ? "" : capabilitiesSource.toLowerCase(java.util.Locale.US);
        java.util.ArrayList<String> capabilities = new java.util.ArrayList<>();
        if (lowerSource.contains("wants_request_hooks") || lowerSource.contains("on_request")) capabilities.add("перехват сетевых запросов");
        if (lowerSource.contains("on_message") || lowerSource.contains("message")) capabilities.add("обработка сообщений");
        if (lowerSource.contains("open(") || lowerSource.contains("pathlib") || lowerSource.contains("os.path")) capabilities.add("доступ к локальным файлам");
        if (lowerSource.contains("http://") || lowerSource.contains("https://") || lowerSource.contains("requests.")) capabilities.add("обращение к интернету");
        if (lowerSource.contains("register_pill")) capabilities.add("виджеты строки поиска (Pill Stack)");
        if (!capabilities.isEmpty()) {
            TextView access = new TextView(context);
            access.setText("Возможности плагина:\n• " + android.text.TextUtils.join("\n• ", capabilities));
            access.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            access.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            access.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(11), AndroidUtilities.dp(14), AndroidUtilities.dp(11));
            access.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundGray)));
            root.addView(access, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        }

        // галка «включить после установки»
        final boolean[] enableAfter = {true};
        CheckBoxCell check = new CheckBoxCell(context, 1, fragment.getResourceProvider());
        check.setText("Включить после установки", "", true, false);
        check.setOnClickListener(v -> {
            enableAfter[0] = !enableAfter[0];
            check.setChecked(enableAfter[0], true);
        });
        root.addView(check, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        // Для уже установленного ID карточка выполняет обновление тем же безопасным путём.
        TextView install = new TextView(context);
        install.setText(installed ? "Обновить плагин"
                : (verified ? "Установить плагин" : "Установить неизвестный плагин"));
        install.setGravity(Gravity.CENTER);
        install.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        install.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        install.setTypeface(AndroidUtilities.bold());
        install.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        root.addView(install, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 0));

        // ---- перекраска по статусу проверки (живьём) ----
        final boolean[] vState = {verified};
        final TextView badgeRef = badge, warnTextRef = warnText, installRef = install;
        final LinearLayout warnRef = warn;
        Runnable applyVerified = () -> {
            boolean vv = vState[0];
            badgeRef.setText(vv ? "🛡️ Проверено DevGram" : "⚠️ Неизвестный источник");
            badgeRef.setTextColor(vv ? 0xFFFFFFFF : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            badgeRef.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                    vv ? 0xFF3BA55D : Theme.getColor(Theme.key_windowBackgroundGray)));
            warnRef.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                    vv ? 0x223BA55D : Theme.getColor(Theme.key_windowBackgroundGray)));
            warnTextRef.setText(vv
                    ? "✓ Проверенный плагин\nОн есть в реестре DevGram — код проверен, можно доверять."
                    : "ⓘ Внимание: неизвестный источник\nПлагина нет в реестре проверенных DevGram, он выполняет код в приложении. Устанавливай только если доверяешь автору.");
            warnTextRef.setTextColor(vv ? 0xFF3BA55D : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            installRef.setText(installed ? "Обновить плагин"
                    : (vv ? "Установить плагин" : "Установить неизвестный плагин"));
        };

        final boolean team = org.telegram.messenger.DevGramBadges.isTeam(fragment.getUserConfig().getClientUserId());

        // команда: сделать проверенным / убрать (реестр по хешу: исходник .py или sha архива .dgplugin)
        if (team && verifyHash != null && !verifyHash.isEmpty()) {
            TextView teamBtn = new TextView(context);
            teamBtn.setGravity(Gravity.CENTER);
            teamBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            teamBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            teamBtn.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(6));
            final TextView tb = teamBtn;
            Runnable applyTeam = () -> tb.setText(vState[0] ? "✕ Убрать из проверенных" : "✓ Сделать проверенным");
            applyTeam.run();
            teamBtn.setOnClickListener(v -> ensureAdmin(fragment, () -> {
                boolean ok = vState[0] ? DevGramPlugins.unverifyByHash(verifyHash) : DevGramPlugins.verifyByHash(verifyHash);
                if (ok) {
                    vState[0] = !vState[0];
                    applyVerified.run();
                    applyTeam.run();
                    org.telegram.ui.Components.BulletinFactory.of(fragment)
                            .createSimpleBulletin(R.raw.contact_check, vState[0] ? "Плагин помечен проверенным" : "Проверка снята").show();
                }
            }));
            root.addView(teamBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        // Опубликовать в каталог — команда ИЛИ АДМИН канала-разработчика (🧩). Работает и для
        // .py (исходник в RTDB), и для .dgplugin (файл в архив через бота) — единая кнопка.
        boolean channelAdmin = false;
        if (fromDevChannel && sourceDialogId < 0) {
            org.telegram.tgnet.TLRPC.Chat pubChat = fragment.getMessagesController().getChat(-sourceDialogId);
            channelAdmin = org.telegram.messenger.ChatObject.hasAdminRights(pubChat);
        }
        if ((team || channelAdmin) && (verifySource != null || packagePath != null)) {
            TextView pubBtn = new TextView(context);
            pubBtn.setGravity(Gravity.CENTER);
            pubBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            pubBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            pubBtn.setGravity(Gravity.CENTER);
            pubBtn.setTypeface(AndroidUtilities.bold());
            pubBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            pubBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                    Theme.getColor(Theme.key_featuredStickers_addButton),
                    Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            pubBtn.setText("📚 Опубликовать в каталог");
            final TextView[] rejectionRef = new TextView[1];
            final String fId = id, fName = name, fVer = ver, fAuthor = author, fDesc = desc, fIcon = iconUrl, fSource = verifySource == null ? "" : verifySource, fPackage = packagePath;
            final long fChannelId = sourceDialogId;
            pubBtn.setOnClickListener(v -> {
                DevGramPlugins.CatalogEntry ce = new DevGramPlugins.CatalogEntry();
                ce.id = fId;
                ce.name = fName;
                ce.version = fVer;
                ce.author = fAuthor;
                ce.desc = fDesc;
                ce.icon = fIcon;
                ce.source = fSource;
                ce.channel = channelName(fragment, fChannelId);
                choosePublishFilter(fragment, ce, fPackage, () -> applyPendingState(pubBtn, rejectionRef[0]));
            });
            TextView rejectionInfo = new TextView(context);
            rejectionRef[0] = rejectionInfo;
            rejectionInfo.setVisibility(View.GONE);
            rejectionInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            rejectionInfo.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            rejectionInfo.setLineSpacing(AndroidUtilities.dp(3), 1f);
            rejectionInfo.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
            rejectionInfo.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14),
                    Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular), 0.12f)));
            root.addView(rejectionInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 8));
            root.addView(pubBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

            // Для ожидающей заявки оставляем понятный статус вместо кнопки повторной подачи.
            final TextView pubBtnRef = pubBtn;
            DevGramPlugins.getPluginSubmissionStatus(fId, fSource, status -> {
                if (status == 1) {
                    rejectionInfo.setVisibility(View.GONE);
                    pubBtnRef.setText("⏳ Плагин находится на модерации");
                    pubBtnRef.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    pubBtnRef.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundGray)));
                    pubBtnRef.setOnClickListener(null);
                    DevGramPlugins.canWithdrawPending(fId, allowed -> {
                        if (allowed) {
                            pubBtnRef.setText("⏳ На модерации  ·  Отозвать");
                            pubBtnRef.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                            pubBtnRef.setOnClickListener(v -> DevGramPlugins.withdrawPending(fId, ok -> {
                                if (ok) {
                                    pubBtnRef.setVisibility(android.view.View.GONE);
                                    org.telegram.ui.Components.BulletinFactory.of(fragment)
                                            .createSimpleBulletin(R.raw.contact_check, "Заявка отозвана").show();
                                }
                            }));
                        }
                    });
                } else if (status == 3) {
                    DevGramPlugins.fetchRejectionReason(fId, reason -> {
                        pubBtnRef.setVisibility(android.view.View.VISIBLE);
                        rejectionInfo.setVisibility(View.VISIBLE);
                        rejectionInfo.setText("Заявка отклонена\n" + (reason.isEmpty() ? "Модератор не указал причину." : reason));
                        pubBtnRef.setText("Исправить и отправить снова  →");
                        pubBtnRef.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                        pubBtnRef.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                                Theme.getColor(Theme.key_featuredStickers_addButton),
                                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
                        pubBtnRef.setOnClickListener(v -> {
                            DevGramPlugins.clearRejectedForResubmit(fId);
                            DevGramPlugins.CatalogEntry ce = new DevGramPlugins.CatalogEntry();
                            ce.id = fId; ce.name = fName; ce.version = fVer; ce.author = fAuthor;
                            ce.desc = fDesc; ce.icon = fIcon; ce.source = fSource;
                            ce.channel = channelName(fragment, fChannelId);
                            choosePublishFilter(fragment, ce, fPackage, () -> applyPendingState(pubBtnRef, rejectionInfo));
                        });
                    });
                } else if (status != 0) {
                    pubBtnRef.setVisibility(android.view.View.GONE);
                }
            });
        }

        // «Обновить в каталоге» — для АВТОРА уже опубликованного плагина прямо при тапе на
        // файл в посте/чате: заменяет файл в каталоге БЕЗ модерации (статистика сохраняется).
        // Работает и для .py (исходник), и для .dgplugin (пакет). Не требует экрана каталога.
        {
            final String uId = id, uName = name, uAuthor = author, uDesc = desc, uIcon = iconUrl, uVer = ver;
            final String uSource = verifySource == null ? "" : verifySource;
            final String uPackage = packagePath;
            final LinearLayout rootRef = root;
            DevGramPlugins.fetchCatalogEntry(id, entries -> {
                if (entries == null || entries.isEmpty()) return;
                final DevGramPlugins.CatalogEntry cat = entries.get(0);
                if (cat.submitterId == 0 || cat.submitterId != DevGramPlugins.myId()) return;
                if (uSource.isEmpty() && uPackage == null) return; // нечем обновлять
                // Версия этого файла vs версия в каталоге. Если совпали — обновлять нечего:
                // показываем «✓ Актуально» (иначе кнопка «Обновить» висела бы после апдейта).
                final boolean sameVersion = uVer != null && !uVer.isEmpty() && uVer.equals(cat.version);
                TextView upd = new TextView(context);
                upd.setGravity(Gravity.CENTER);
                upd.setTypeface(AndroidUtilities.bold());
                upd.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                upd.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
                if (sameVersion) {
                    upd.setText("✓ Актуально в каталоге");
                    upd.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    upd.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14),
                            Theme.getColor(Theme.key_windowBackgroundGray)));
                    rootRef.addView(upd, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 4));
                    return;
                }
                upd.setText("🔄 Обновить в каталоге");
                upd.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                upd.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                        Theme.getColor(Theme.key_featuredStickers_addButton),
                        Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
                final TextView updRef = upd;
                // Кнопка меняет состояние прямо в карточке — без перезахода в меню.
                final Runnable makeButton = () -> {
                    updRef.setText("🔄 Обновить в каталоге");
                    updRef.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                    updRef.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                            Theme.getColor(Theme.key_featuredStickers_addButton),
                            Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
                };
                final Runnable makeDone = () -> {
                    updRef.setText("✓ Обновлено в каталоге");
                    updRef.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    updRef.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14),
                            Theme.getColor(Theme.key_windowBackgroundGray)));
                    updRef.setOnClickListener(null);
                };
                final String[] pubVer = { uVer };
                upd.setOnClickListener(v -> {
                    updRef.setText("⏳ Отправляется…");
                    updRef.setOnClickListener(null);
                    DevGramPlugins.CatalogEntry ce = new DevGramPlugins.CatalogEntry();
                    ce.id = uId; ce.name = uName; ce.author = uAuthor; ce.desc = uDesc;
                    ce.icon = uIcon; ce.channel = cat.channel; ce.filter = cat.filter;
                    ce.version = uVer; ce.update = true;
                    DevGramPlugins.SubmissionCallback cb = r -> {
                        if (r == 1) {
                            updRef.setText("⏳ Применяется…");
                            org.telegram.ui.Components.BulletinFactory.of(fragment)
                                    .createSimpleBulletin(R.raw.contact_check,
                                            "Обновление отправлено — применяется, статистика сохранится").show();
                            waitCatalogApplied(uId, pubVer[0], 0, makeDone, makeButton);
                        } else {
                            makeButton.run();
                            String msg = (r == -1 ? "Плагин заблокирован — обновление запрещено"
                                    : (r == -2 ? "Файл не прошёл проверку" : "Не удалось отправить обновление, попробуйте ещё раз"));
                            org.telegram.ui.Components.BulletinFactory.of(fragment)
                                    .createSimpleBulletin(R.raw.error, msg).show();
                        }
                    };
                    if (uPackage != null && uPackage.endsWith(".dgplugin")) {
                        String verr = DevGramPlugins.packageValidationError(uPackage);
                        if (!verr.isEmpty()) {
                            makeButton.run();
                            org.telegram.ui.Components.BulletinFactory.of(fragment).createErrorBulletin(verr).show();
                            return;
                        }
                        String[] mm = DevGramPlugins.packageMeta(uPackage).split("", -1);
                        if (mm.length > 2 && !mm[2].isEmpty()) { ce.version = mm[2]; pubVer[0] = mm[2]; }
                        ce.isPackage = true;
                        org.telegram.messenger.DevGramPackages.publishPackage(uPackage, ce, cb);
                    } else {
                        ce.isPackage = false; ce.source = uSource;
                        DevGramPlugins.publishToCatalog(ce, cb);
                    }
                });
                rootRef.addView(upd, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 4));
            });
        }

        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setApplyBottomPadding(false);
        builder.setCustomView(scroll);
        BottomSheet sheet = builder.create();

        install.setOnClickListener(v -> {
            boolean ok = installAction.install(enableAfter[0]);
            sheet.dismiss();
            if (ok) {
                org.telegram.ui.Components.BulletinFactory.of(fragment)
                        .createSimpleBulletin(R.raw.contact_check,
                                "Плагин «" + name + "» " + (installed ? "обновлён" : "установлен")).show();
            } else {
                org.telegram.ui.Components.BulletinFactory.of(fragment)
                        .createErrorBulletin(installed ? "Не удалось обновить плагин" : "Не удалось установить плагин").show();
            }
        });

        sheet.show();
    }

    // Публикация в каталог: сначала выбор фильтра (категории), затем запись. Заблокированные плагины
    // (удалённые командой) публиковать нельзя.
    private static void choosePublishFilter(BaseFragment fragment, DevGramPlugins.CatalogEntry ce) { choosePublishFilter(fragment, ce, null, null); }
    private static void choosePublishFilter(BaseFragment fragment, DevGramPlugins.CatalogEntry ce, final String packagePath, Runnable onSubmitted) {
        if (DevGramPlugins.isBlocked(ce.source)) {
            org.telegram.ui.Components.BulletinFactory.of(fragment)
                    .createErrorBulletin("Этот плагин удалён командой — публиковать нельзя").show();
            return;
        }
        DevGramPlugins.fetchFilters(filters -> {
            Context ctx = fragment.getParentActivity();
            if (ctx == null) {
                return;
            }
            final java.util.ArrayList<String> opts = new java.util.ArrayList<>();
            opts.add("");
            opts.addAll(filters);

            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(18), AndroidUtilities.dp(20), AndroidUtilities.dp(14));

            TextView title = new TextView(ctx);
            title.setText("Категория публикации");
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView subtitle = new TextView(ctx);
            subtitle.setText("Выберите раздел, где пользователям будет проще найти «" + ce.name + "».");
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
            subtitle.setLineSpacing(AndroidUtilities.dp(2), 1f);
            root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 14));

            final BottomSheet[] sheetRef = new BottomSheet[1];
            for (String option : opts) {
                final String selected = option;
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(11), AndroidUtilities.dp(14), AndroidUtilities.dp(11));
                row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                        Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));

                android.widget.ImageView icon = new android.widget.ImageView(ctx);
                icon.setImageResource(R.drawable.msg_folders);
                icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon));
                icon.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(9));
                icon.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                        Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon), 0.12f)));
                row.addView(icon, LayoutHelper.createLinear(42, 42));

                LinearLayout texts = new LinearLayout(ctx);
                texts.setOrientation(LinearLayout.VERTICAL);
                TextView name = new TextView(ctx);
                name.setText(selected.isEmpty() ? "Без категории" : selected);
                name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                name.setTypeface(AndroidUtilities.bold());
                name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                texts.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                TextView hint = new TextView(ctx);
                hint.setText(selected.isEmpty() ? "Показывать только в общем списке" : "Опубликовать в этой категории");
                hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                hint.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
                texts.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
                row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 12, 0, 8, 0));

                android.widget.ImageView arrow = new android.widget.ImageView(ctx);
                arrow.setImageResource(R.drawable.msg_arrowright);
                arrow.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
                row.addView(arrow, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL));
                row.setOnClickListener(v -> {
                    row.setEnabled(false);
                    name.setText("Отправляем…");
                    ce.filter = selected;
                    DevGramPlugins.SubmissionCallback scb = r -> {
                        if (r == 1 && sheetRef[0] != null) sheetRef[0].dismiss();
                        String msg = r == 1 ? "Заявка отправлена на модерацию"
                                : (r == -1 ? "Плагин заблокирован — публикация запрещена"
                                : (r == -2 ? DevGramPlugins.validateCatalogEntry(ce) : "Firebase не подтвердил отправку. Нажмите ещё раз"));
                        org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(r == 1 ? R.raw.contact_check : R.raw.error, msg).show();
                        if (r == 1) { if (onSubmitted != null) onSubmitted.run(); }
                        else { row.setEnabled(true); name.setText(selected.isEmpty() ? "Без категории" : selected); }
                    };
                    // Пакет .dgplugin: сначала шлём файл боту (dgpkg:<id>), потом заявку; иначе обычная публикация исходника.
                    if (packagePath != null) {
                        org.telegram.messenger.DevGramPackages.publishPackage(packagePath, ce, scb);
                    } else {
                        DevGramPlugins.publishToCatalog(ce, scb);
                    }
                });
                root.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            }

            ScrollView scroll = new ScrollView(ctx);
            scroll.addView(root);
            BottomSheet.Builder builder = new BottomSheet.Builder(ctx);
            builder.setApplyBottomPadding(false);
            builder.setCustomView(scroll);
            sheetRef[0] = builder.create();
            sheetRef[0].show();
        });
    }

    private static void applyPendingState(TextView button, TextView rejectionInfo) {
        if (button == null) return;
        if (rejectionInfo != null) rejectionInfo.setVisibility(View.GONE);
        button.setVisibility(View.VISIBLE);
        button.setText("⏳ Плагин находится на модерации");
        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        button.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundGray)));
        button.setOnClickListener(null);
    }

    // Имя канала-источника (@username или заголовок) по dialogId (у канала он отрицательный).
    private static String channelName(BaseFragment fragment, long dialogId) {
        if (dialogId >= 0) {
            return "";
        }
        try {
            org.telegram.tgnet.TLRPC.Chat chat = fragment.getMessagesController().getChat(-dialogId);
            if (chat != null) {
                String u = org.telegram.messenger.ChatObject.getPublicUsername(chat);
                if (u != null && !u.isEmpty()) {
                    return "@" + u;
                }
                if (chat.title != null) {
                    return chat.title;
                }
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    // Требуем вход команды (email + пароль) прямо здесь, если ещё не вошли.
    private static void ensureAdmin(BaseFragment fragment, Runnable onReady) {
        if (DevGramPlugins.canManageVerified()) {
            onReady.run();
            return;
        }
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        EditTextBoldCursor emailEt = makeInput(context, "Email команды", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
        EditTextBoldCursor passEt = makeInput(context, "Пароль", InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        passEt.setTransformationMethod(PasswordTransformationMethod.getInstance());

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        box.addView(emailEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        box.addView(passEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 12f, 0f, 0f));

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Вход команды DevGram");
        b.setView(box);
        b.setPositiveButton("Войти", (d, w) -> {
            String email = emailEt.getText().toString().trim();
            String pass = passEt.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) {
                return;
            }
            DevGramBadges.signIn(email, pass, (ok, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (ok) {
                    onReady.run();
                } else {
                    org.telegram.ui.Components.BulletinFactory.of(fragment).createErrorBulletin("Не удалось войти: " + err).show();
                }
            }));
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
        emailEt.requestFocus();
        AndroidUtilities.showKeyboard(emailEt);
    }

    // Аватарка плагина по URL: кеш → иначе качаем в фоне → ставим на UI-потоке.
    private static void loadIcon(final android.widget.ImageView iv, final String url) {
        final Context ctx = iv.getContext();
        if (ctx == null) return;
        java.io.File dir = new java.io.File(ctx.getCacheDir(), "devgram_icons");
        dir.mkdirs();
        final java.io.File cache = new java.io.File(dir, Integer.toHexString(url.hashCode()) + ".img");
        if (cache.exists() && cache.length() > 0) {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(cache.getAbsolutePath());
            if (bmp != null) {
                applyIcon(iv, bmp);
                return;
            }
        }
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "DevGram");
                java.io.InputStream in = c.getInputStream();
                java.io.File tmp = new java.io.File(cache.getAbsolutePath() + ".tmp");
                java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                c.disconnect();
                tmp.renameTo(cache);
                final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(cache.getAbsolutePath());
                if (bmp != null) {
                    AndroidUtilities.runOnUIThread(() -> applyIcon(iv, bmp));
                }
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
            }
        }).start();
    }

    private static void applyIcon(android.widget.ImageView iv, android.graphics.Bitmap bmp) {
        iv.setColorFilter(null);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(bmp);
    }

    private static EditTextBoldCursor makeInput(Context context, String hint, int inputType) {
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setInputType(inputType);
        et.setHint(hint);
        et.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        return et;
    }

    // Ждём, пока сервер (modbot, опрос ~20с) применит обновление автора в каталог, и
    // переключаем кнопку на «✓ Обновлено» БЕЗ перезахода в меню. Признак применения:
    // заявка ушла из plugins_pending (статус != 1) — надёжно даже если версия не менялась.
    // Поллим до ~60с, иначе возвращаем кнопку (обновление всё равно применится позже).
    private static void waitCatalogApplied(String pluginId, String targetVersion, int attempt,
                                           Runnable onDone, Runnable onTimeout) {
        if (attempt >= 8) {           // 8 × 8с ≈ 64с
            if (onTimeout != null) onTimeout.run();
            return;
        }
        AndroidUtilities.runOnUIThread(() -> DevGramPlugins.getPluginSubmissionStatus(pluginId, "", status -> {
            if (status == 2) {                 // применено и видно в каталоге
                if (onDone != null) onDone.run();
            } else {                            // 1 = ещё в pending; ждём дальше
                waitCatalogApplied(pluginId, targetVersion, attempt + 1, onDone, onTimeout);
            }
        }), attempt == 0 ? 6000 : 8000);
    }
}

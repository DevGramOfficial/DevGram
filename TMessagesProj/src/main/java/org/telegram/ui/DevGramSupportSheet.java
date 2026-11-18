package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

// DevGram: всплывающий лист «Поддержать DevGram». Сумма $3, эквивалент в TON и ₽ считается по
// текущему курсу (CoinGecko). Иконки шагов — векторные (SVG), не эмодзи. Реквизиты — в «Другое».
public class DevGramSupportSheet {

    private static final int USD = 3;                 // сумма пожертвования в долларах
    public static final String SUPPORT_BOT = "@officialdevgram_bot";
    public static final String SUPPORT_URL = "https://devgram.space/support";
    public static final String BOT_URL = "https://t.me/officialdevgram_bot?start=support";

    public static void show(BaseFragment fragment) {
        if (fragment == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(18), AndroidUtilities.dp(20), AndroidUtilities.dp(12));

        ImageView heart = new ImageView(context);
        heart.setImageResource(R.drawable.devgram_support_logo);
        root.addView(heart, LayoutHelper.createLinear(76, 76, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 12));

        TextView title = new TextView(context);
        title.setText(DevGramLocale.text("Поддержать DevGram"));
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView subtitle = new TextView(context);
        subtitle.setText(DevGramLocale.text("DevGram делают энтузиасты в свободное время. Любой донат помогает держать проект ")
                + DevGramLocale.text("живым — а тебе за это прилетает эксклюзивный значок в профиль."));
        subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        // держатель ссылки на лист — нужен кликабельному «Другое» внутри текста (закрыть лист)
        final BottomSheet[] sheetRef = new BottomSheet[1];

        // шаг 1 — сумма (эквивалент подставим после запроса курса); «Другое» кликабельно
        TextView donationBody = new TextView(context);
        addStep(context, root, R.drawable.devgram_ic_pay, DevGramLocale.text("Перевести донат"), donationBody,
                amountText(context, fragment, sheetRef, "…", "…"));
        donationBody.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        donationBody.setHighlightColor(0);
        // Both submission paths use the same authenticated application queue.
        TextView payProofBody = new TextView(context);
        addStep(context, root, R.drawable.devgram_ic_photo, DevGramLocale.text("Прислать чек"), payProofBody,
                botSpannable(context, DevGramLocale.text("Войдите через Telegram в форму на сайте или отправьте чек боту ")
                        + SUPPORT_BOT + DevGramLocale.text(". Выберите получателя: пользователя или канал. На чеке должны быть видны сумма, дата и время.")));
        payProofBody.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        payProofBody.setHighlightColor(0);
        // шаг 3
        TextView badgeBody = new TextView(context);
        addStep(context, root, R.drawable.devgram_ic_badge, DevGramLocale.text("Забрать значок"), badgeBody,
                DevGramLocale.text("После проверки перевода мы выдадим тебе уникальный значок — он появится в профиле ")
                        + DevGramLocale.text("и будет виден другим пользователям."));

        TextView close = new TextView(context);
        close.setText(DevGramLocale.text("Подать заявку на сайте"));
        close.setGravity(Gravity.CENTER);
        close.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        close.setTypeface(AndroidUtilities.bold());
        close.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        close.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        close.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setApplyBottomPadding(false);
        builder.setCustomView(scroll);
        BottomSheet sheet = builder.create();
        sheetRef[0] = sheet;

        root.addView(close, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 4));
        close.setOnClickListener(v -> {
            sheet.dismiss();
            org.telegram.messenger.browser.Browser.openUrl(context, SUPPORT_URL);
        });
        TextView bot = new TextView(context);
        bot.setText(DevGramLocale.text("Подать заявку через бота"));
        bot.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bot.setGravity(Gravity.CENTER);
        bot.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        bot.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        root.addView(bot, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        bot.setOnClickListener(v -> {
            sheet.dismiss();
            org.telegram.messenger.browser.Browser.openUrl(context, BOT_URL);
        });

        sheet.show();

        // курс TON/₽ — в фоне, затем обновляем текст суммы
        fetchRates(fragment, sheetRef, donationBody);
    }

    // Текст шага 1: сумма с inline-значком TON (серый, как текст) + кликабельным «Другое».
    private static CharSequence amountText(Context ctx, BaseFragment fragment, BottomSheet[] sheetRef, String ton, String rub) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(DevGramLocale.text("Отправь $")).append(String.valueOf(USD)).append(" (≈ ");
        int iconStart = sb.length();
        sb.append("◈"); // плейсхолдер под значок TON
        org.telegram.ui.Components.ColoredImageSpan ton_span =
                new org.telegram.ui.Components.ColoredImageSpan(R.drawable.devgram_ic_ton,
                        org.telegram.ui.Components.ColoredImageSpan.ALIGN_CENTER);
        ton_span.setSize(AndroidUtilities.dp(14)); // тон-значок берёт цвет текста абзаца (серый)
        sb.setSpan(ton_span, iconStart, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(" ").append(ton).append(DevGramLocale.text(" TON или ")).append(rub).append(DevGramLocale.text(" ₽) любым удобным способом. "))
                .append(DevGramLocale.text("Банковские карты и Tonkeeper находятся в разделе "));
        int oStart = sb.length();
        sb.append(DevGramLocale.text("«Другое»"));
        sb.setSpan(new android.text.style.ClickableSpan() {
            @Override
            public void onClick(android.view.View widget) {
                if (sheetRef[0] != null) {
                    sheetRef[0].dismiss();
                }
                if (fragment != null && !(fragment instanceof DevGramOtherActivity)) {
                    fragment.presentFragment(new DevGramOtherActivity());
                }
            }

            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Theme.getColor(Theme.key_dialogTextBlue2));
                ds.setUnderlineText(true);
                ds.setTypeface(AndroidUtilities.bold());
            }
        }, oStart, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(".");
        return sb;
    }

    private static CharSequence botSpannable(Context context, String text) {
        int idx = text.indexOf(SUPPORT_BOT);
        if (idx < 0) {
            return text;
        }
        android.text.SpannableString sp = new android.text.SpannableString(text);
        final Context ctx = context;
        sp.setSpan(new android.text.style.ClickableSpan() {
            @Override
            public void onClick(android.view.View widget) {
                org.telegram.messenger.browser.Browser.openUrl(ctx, BOT_URL);
            }

            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Theme.getColor(Theme.key_dialogTextBlue2));
                ds.setUnderlineText(true);
                ds.setTypeface(AndroidUtilities.bold());
            }
        }, idx, idx + SUPPORT_BOT.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    // строка шага: слева векторная иконка (тонированная акцентом), справа жирный заголовок + серый текст
    private static void addStep(Context context, LinearLayout root, int iconRes, String header, TextView body, CharSequence bodyText) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), PorterDuff.Mode.SRC_IN));
        row.addView(icon, LayoutHelper.createLinear(26, 26, 0, 2, 14, 0));

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView h = new TextView(context);
        h.setText(header);
        h.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        h.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        h.setTypeface(AndroidUtilities.bold());
        col.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        body.setText(bodyText);
        body.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        col.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        row.addView(col, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
    }

    // Курс: CoinGecko (TON в usd и rub). $USD в TON = USD/ton_usd; $USD в ₽ = USD*ton_rub/ton_usd.
    private static void fetchRates(BaseFragment fragment, BottomSheet[] sheetRef, TextView donationBody) {
        org.telegram.messenger.DevGramNetworkQueue.postRunnable(() -> {
            String ton = null, rub = null;
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(
                        "https://api.coingecko.com/api/v3/simple/price?ids=the-open-network&vs_currencies=usd,rub").openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", "DevGram");
                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();
                    org.json.JSONObject o = new org.json.JSONObject(sb.toString())
                            .optJSONObject("the-open-network");
                    if (o != null) {
                        double tonUsd = o.optDouble("usd", 0);
                        double tonRub = o.optDouble("rub", 0);
                        if (tonUsd > 0) {
                            ton = String.format(java.util.Locale.US, "%.2f", USD / tonUsd);
                            if (tonRub > 0) {
                                rub = String.valueOf(Math.round(USD * tonRub / tonUsd));
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
            final String tonF = ton != null ? ton : "≈";
            final String rubF = rub != null ? rub : "≈";
            AndroidUtilities.runOnUIThread(() ->
                    donationBody.setText(amountText(donationBody.getContext(), fragment, sheetRef, tonF, rubF)));
        });
    }
}

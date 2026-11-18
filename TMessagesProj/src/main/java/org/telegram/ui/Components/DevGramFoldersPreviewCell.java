package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью вкладок папок (как FilterTabsPreviewCell в exteraGram) — вкладки-пилюли с иконкой,
// названием и бейджем-счётчиком; учитывает стиль (Текст/Значок+текст/Значок) и «Счётчик уведомлений».
public class DevGramFoldersPreviewCell extends View {

    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint countText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private static String[] getTabs() {
        return new String[]{DevGramLocale.text("Все чаты"), DevGramLocale.text("Работа"), DevGramLocale.text("Личное")};
    }
    private static final int[] COUNTS = {34, 17, 3};

    public DevGramFoldersPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        text.setTextSize(AndroidUtilities.dp(14));
        text.setTypeface(AndroidUtilities.bold());
        countText.setTextSize(AndroidUtilities.dp(11));
        countText.setTypeface(AndroidUtilities.bold());
    }

    private static boolean showCounter() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_tabCounter", true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int accent = Theme.getColor(Theme.key_switchTrack);
        int ar = Color.red(accent), ag = Color.green(accent), ab = Color.blue(accent);
        int style = DevGramConfig.getFolderTabsStyle(); // 0 текст, 1 значок+текст, 2 значок
        boolean counter = showCounter();
        float cy = getMeasuredHeight() / 2f;
        boolean rtl = LocaleController.isRTL;
        float x = rtl ? getMeasuredWidth() - AndroidUtilities.dp(16) : AndroidUtilities.dp(16);

        for (int i = 0; i < getTabs().length; i++) {
            boolean sel = i == 0;
            String label = getTabs()[i];
            float iconW = (style == 1 || style == 2) ? AndroidUtilities.dp(16) : 0;
            float textW = (style == 2) ? 0 : text.measureText(label);
            float iconGap = (style == 1 && textW > 0) ? AndroidUtilities.dp(6) : 0;
            String cnt = String.valueOf(COUNTS[i]);
            float badgeD = (counter && COUNTS[i] > 0) ? AndroidUtilities.dp(17) : 0;
            float badgeW = badgeD > 0 ? Math.max(badgeD, countText.measureText(cnt) + AndroidUtilities.dp(10)) : 0;
            float badgeGap = badgeD > 0 ? AndroidUtilities.dp(6) : 0;
            float padH = AndroidUtilities.dp(13);
            float pillW = padH * 2 + iconW + iconGap + textW + badgeGap + badgeW;
            float left = rtl ? x - pillW : x;

            pill.setColor(sel ? accent : Color.argb(28, ar, ag, ab));
            rect.set(left, cy - AndroidUtilities.dp(16), left + pillW, cy + AndroidUtilities.dp(16));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), pill);

            int fg = sel ? Color.WHITE : Color.argb(210, ar, ag, ab);
            float cx = left + padH;
            if (iconW > 0) {
                icon.setColor(fg);
                drawFolderIcon(canvas, cx, cy, iconW);
                cx += iconW + iconGap;
            }
            if (textW > 0) {
                text.setColor(fg);
                canvas.drawText(label, cx, cy + AndroidUtilities.dp(5), text);
                cx += textW;
            }
            if (badgeD > 0) {
                cx += badgeGap;
                badge.setColor(sel ? Color.argb(70, 255, 255, 255) : Color.argb(45, ar, ag, ab));
                rect.set(cx, cy - badgeD / 2f, cx + badgeW, cy + badgeD / 2f);
                canvas.drawRoundRect(rect, badgeD / 2f, badgeD / 2f, badge);
                countText.setColor(fg);
                float ctw = countText.measureText(cnt);
                canvas.drawText(cnt, cx + (badgeW - ctw) / 2f, cy + AndroidUtilities.dp(4), countText);
            }
            x = rtl ? left - AndroidUtilities.dp(8) : left + pillW + AndroidUtilities.dp(8);
        }
    }

    private final android.graphics.Path folderPath = new android.graphics.Path();

    // силуэт папки (тело + вкладка) в квадрате iconW, вертикально по центру cy
    private void drawFolderIcon(Canvas canvas, float left, float cy, float s) {
        float top = cy - s * 0.34f;
        float bot = cy + s * 0.36f;
        float r = left + s;
        float tabW = s * 0.42f;
        float tabTop = cy - s * 0.44f;
        folderPath.reset();
        // вкладка
        rect.set(left, tabTop, left + tabW, top + AndroidUtilities.dp(3));
        folderPath.addRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), android.graphics.Path.Direction.CW);
        // тело
        rect.set(left, top, r, bot);
        folderPath.addRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), android.graphics.Path.Direction.CW);
        canvas.drawPath(folderPath, icon);
    }
}

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramProfileDocument;
import org.telegram.messenger.DevGramProfileStyle;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import java.util.function.Consumer;

/** Touch controls shared by the appearance editor and advanced studio. */
public final class DevGramStudioControls {
    private DevGramStudioControls() {}
    public static int sheetHeightSpec(int incoming, float fraction) {
        return sheetHeightSpec(incoming, (int) (AndroidUtilities.displaySize.y * fraction));
    }
    public static int sheetHeightSpec(int incoming, int maximum) {
        maximum = Math.max(1, maximum);
        if (View.MeasureSpec.getMode(incoming) != View.MeasureSpec.UNSPECIFIED) maximum = Math.min(maximum, View.MeasureSpec.getSize(incoming));
        return View.MeasureSpec.makeMeasureSpec(maximum, View.MeasureSpec.AT_MOST);
    }
    public static void haptic(View view,int kind){if(org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean("devgramCustomizationHaptics",true))view.performHapticFeedback(kind);}
    private static int dp(float value) { return AndroidUtilities.dp(value); }
    private static String t(String value) { return DevGramLocale.text(value); }
    private static int color(int key, Theme.ResourcesProvider resources) { return Theme.getColor(key, resources); }

    public static TextView text(Context context, String value, int size, boolean bold, Theme.ResourcesProvider resources) {
        TextView text = new TextView(context); text.setText(t(value)); text.setTextSize(size);
        text.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText, resources));
        if (bold) text.setTypeface(AndroidUtilities.bold());
        return text;
    }

    public static GradientDrawable surface(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(18));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    public static LinearLayout column(Context context) {
        LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL); return view;
    }

    public static LinearLayout grid(Context context, String[] titles, String[] subtitles, int[] icons,
                                    Theme.ResourcesProvider resources, Consumer<Integer> click) {
        LinearLayout grid = column(context); grid.setPadding(dp(12), dp(6), dp(12), dp(6));
        for (int start = 0; start < titles.length; start += 2) {
            LinearLayout row = new LinearLayout(context);
            for (int i = start; i < Math.min(start + 2, titles.length); i++) {
                final int index = i;
                LinearLayout card = column(context); card.setPadding(dp(16), dp(14), dp(12), dp(12));
                card.setBackground(surface(color(Theme.key_windowBackgroundWhite, resources), color(Theme.key_divider, resources)));
                android.widget.ImageView icon = new android.widget.ImageView(context); icon.setImageResource(icons[i]);
                icon.setColorFilter(color(Theme.key_windowBackgroundWhiteBlueText, resources));
                card.addView(icon, LayoutHelper.createLinear(28, 28, 0, 0, 0, 8));
                TextView title = text(context, titles[i], 15, true, resources); title.setMaxLines(2);
                card.addView(title, LayoutHelper.createLinear(-1, -2));
                TextView description = text(context, subtitles[i], 12, false, resources);
                description.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText, resources)); description.setMaxLines(2);
                card.addView(description, LayoutHelper.createLinear(-1, -2, 0, 4, 0, 0));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(142), 1); lp.setMargins(dp(4), dp(4), dp(4), dp(4)); row.addView(card, lp);
                card.setContentDescription(t(titles[i]) + ". " + t(subtitles[i])); card.setOnClickListener(v -> click.accept(index));
            }
            if (start + 1 == titles.length) row.addView(new View(context), new LinearLayout.LayoutParams(0, dp(142), 1));
            grid.addView(row);
        }
        return grid;
    }

    public static class Slider extends LinearLayout {
        private final SeekBarView seek;
        private final TextView label, value;
        private int low, high, current;
        private boolean dragging;
        private Consumer<Integer> change;
        private Runnable stopped, locked;
        private String unit;

        public Slider(Context context, Theme.ResourcesProvider resources) {
            super(context); setOrientation(VERTICAL); setPadding(dp(20), dp(10), dp(20), dp(2));
            LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
            label = text(context, "", 15, false, resources); value = text(context, "", 14, true, resources);
            value.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText, resources));
            header.addView(label, new LinearLayout.LayoutParams(0, -2, 1)); header.addView(value, LayoutHelper.createLinear(-2, -2)); addView(header);
            seek = new SeekBarView(context, resources); seek.setReportChanges(true);
            addView(seek, LayoutHelper.createLinear(-1, 40));
            seek.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override public void onSeekBarDrag(boolean stop, float progress) {
                    int next = Math.round(low + progress * (high - low));
                    if (next != current) { current = next; updateValue(); if (change != null) change.accept(next); }
                    if (stop && stopped != null) stopped.run();
                }
                @Override public void onSeekBarPressed(boolean pressed) { dragging = pressed; }
                @Override public CharSequence getContentDescription() { return label.getText() + ": " + value.getText(); }
            });
        }
        public void bind(CharSequence title, int current, int low, int high, String unit, boolean available,
                         Consumer<Integer> change, Runnable stopped, Runnable locked) {
            this.low = low; this.high = high; this.unit = unit; this.change = change; this.stopped = stopped; this.locked = locked;
            label.setText(title); seek.setEnabled(available); seek.setAlpha(available ? 1 : .35f);
            setOnClickListener(available ? null : v -> { if (this.locked != null) this.locked.run(); });
            if (!dragging) { this.current = Math.max(low, Math.min(high, current)); seek.setProgress((this.current - low) / (float) (high - low)); updateValue(); }
        }
        private void updateValue() { value.setText(current + unit); seek.setContentDescription(label.getText() + ": " + value.getText()); }
    }

    public static class Option extends FrameLayout {
        private final String kind;
        private final int index;
        private final DevGramProfileStyle.Style theme;
        private final DevGramProfilePainter painter = new DevGramProfilePainter();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private boolean selected;
        private final Theme.ResourcesProvider resources;
        public Option(Context context, String kind, int index, String title, CharSequence accessible,
                      boolean selected, DevGramProfileStyle.Style theme, Theme.ResourcesProvider resources) {
            super(context); this.kind = kind; this.index = index; this.theme = theme; this.resources = resources;
            this.selected = selected; setWillNotDraw(false); setContentDescription(accessible); setPadding(dp(10), dp(8), dp(10), dp(8));
            TextView name = text(context, title, 12, false, resources); name.setText(accessible); name.setGravity(Gravity.CENTER);
            name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(name, LayoutHelper.createFrame(-1, 34, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 5, 0, 5, 6));
            if (kind.equals("font")) name.setTypeface(DevGramProfileDocument.font(index));
            updateSelected(selected);
        }
        public void updateSelected(boolean value) {
            selected = value; setSelected(value);
            int accent = color(Theme.key_windowBackgroundWhiteBlueText, resources);
            setBackground(surface(value ? androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 25) : color(Theme.key_windowBackgroundWhite, resources), value ? accent : color(Theme.key_divider, resources)));
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight() - dp(46), x = w / 2, y = h / 2 + dp(3);
            int accent = color(Theme.key_windowBackgroundWhiteBlueText, resources);
            paint.setColor(accent); paint.setStyle(Paint.Style.FILL); paint.setShader(null);
            if (kind.equals("palette") || kind.equals("background") || kind.equals("pattern")) {
                DevGramProfileStyle.Style style = theme == null ? new DevGramProfileStyle.Style() : theme;
                if (kind.equals("palette")) style = style.with("palette", index).with("background", 2);
                else style = style.with(kind, index);
                int save = canvas.save(); bounds.set(dp(10), dp(10), w - dp(10), h - dp(2));
                android.graphics.Path clip = new android.graphics.Path(); clip.addRoundRect(bounds, dp(12), dp(12), android.graphics.Path.Direction.CW); canvas.clipPath(clip);
                canvas.translate(bounds.left, bounds.top); paint.setColor(DevGramProfilePainter.PALETTES[style.get("palette")][0]); canvas.drawRect(0, 0, bounds.width(), bounds.height(), paint);
                painter.background(canvas, bounds.width(), bounds.height(), style); painter.pattern(canvas, bounds.width(), bounds.height(), style);
                canvas.restoreToCount(save);
            } else if (kind.equals("shape") || kind.equals("frame")) {
                DevGramProfileStyle.Style style = theme == null ? new DevGramProfileStyle.Style() : theme;
                style = style.with(kind, index); float size = Math.min(h - dp(20), dp(54));
                paint.setColor(DevGramProfilePainter.PALETTES[style.get("palette")][1]); canvas.drawPath(painter.avatarPath(x - size / 2, y - size / 2, size, style.get("shape")), paint);
                painter.frame(canvas, x - size / 2, y - size / 2, size, style);
            } else if (kind.equals("media")) {
                android.graphics.drawable.Drawable icon=getResources().getDrawable(index==0?org.telegram.messenger.R.drawable.msg_photos:org.telegram.messenger.R.drawable.msg_video).mutate();
                icon.setTint(accent);int radius=dp(17);icon.setBounds((int)x-radius,(int)y-radius,(int)x+radius,(int)y+radius);icon.draw(canvas);
            } else if (kind.equals("layout")) {
                float avatarX = index == 2 ? w * .25f : w * .5f;
                canvas.drawCircle(avatarX, y - dp(8), dp(11), paint);
                paint.setAlpha(100); bounds.set(index == 2 ? w * .43f : dp(16), y + dp(8), w - dp(16), y + dp(13)); canvas.drawRoundRect(bounds, dp(3), dp(3), paint); paint.setAlpha(255);
                if (index == 3) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); bounds.set(dp(10), dp(9), w-dp(10), h); canvas.drawRoundRect(bounds,dp(6),dp(6),paint);paint.setStyle(Paint.Style.FILL); }
            } else if (kind.equals("buttonShape")) {
                int[] radii = {5, 16, 10, 22}; bounds.set(dp(15), y - dp(14), w - dp(15), y + dp(14)); canvas.drawRoundRect(bounds, dp(radii[index]), dp(radii[index]), paint);
                paint.setColor(Color.WHITE); paint.setTextSize(dp(14)); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText("✓", x, y + dp(5), paint);
            } else {
                paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(kind.equals("sticker") ? 28 : 25));
                paint.setTypeface(kind.equals("font") ? DevGramProfileDocument.font(index) : Typeface.DEFAULT);
                String[] symbols = {"◉", "✺", "⠿", "✦", "♥", "≈"};
                canvas.drawText(kind.equals("sticker") ? getContentDescription().toString() : kind.equals("layer") ? symbols[index % symbols.length] : kind.equals("font") ? "Aa" : "✦", x, y + dp(9), paint);
                paint.setTypeface(null);
            }
        }
    }

    public static LinearLayout choices(Context context, String kind, String[] labels, CharSequence[] accessible,
                                       int selected, DevGramProfileStyle.Style theme, Theme.ResourcesProvider resources,
                                       Consumer<Integer> click) {
        LinearLayout wrap = column(context); wrap.setPadding(dp(12), dp(4), dp(12), dp(4));
        int columns = kind.equals("layout") || kind.equals("buttonShape") || kind.equals("media") ? 2 : 3;
        for (int start = 0; start < labels.length; start += columns) {
            LinearLayout row = new LinearLayout(context);
            for (int col = 0; col < columns; col++) {
                int index = start + col;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(116), 1); lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                if (index >= labels.length) { row.addView(new View(context), lp); continue; }
                Option option = new Option(context, kind, index, labels[index], accessible == null ? t(labels[index]) : accessible[index], index == selected, theme, resources);
                row.addView(option, lp); option.setOnClickListener(v -> click.accept(index));
            }
            wrap.addView(row);
        }
        return wrap;
    }

    public static HorizontalScrollView pills(Context context, String[] labels, int selected,
                                              Theme.ResourcesProvider resources, Consumer<Integer> click) {
        return pills(context, labels, null, selected, resources, click);
    }
    public static HorizontalScrollView pills(Context context, String[] labels, CharSequence[] accessible, int selected,
                                              Theme.ResourcesProvider resources, Consumer<Integer> click) {
        HorizontalScrollView scroll = new HorizontalScrollView(context); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context); row.setPadding(dp(16), dp(8), dp(16), dp(8));
        for (int i = 0; i < labels.length; i++) {
            int index = i; TextView pill = text(context, labels[i], 13, i == selected, resources);
            if (accessible != null) pill.setText(accessible[i]);
            pill.setGravity(Gravity.CENTER); pill.setPadding(dp(14), 0, dp(14), 0);
            int accent = color(Theme.key_windowBackgroundWhiteBlueText, resources);
            pill.setTextColor(i == selected ? accent : color(Theme.key_windowBackgroundWhiteGrayText, resources));
            pill.setBackground(surface(i == selected ? androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 30) : color(Theme.key_windowBackgroundWhite, resources), 0));
            pill.setSelected(i == selected); pill.setOnClickListener(v -> click.accept(index));
            row.addView(pill, LayoutHelper.createLinear(-2, 36, 0, 0, 6, 0));
        }
        scroll.addView(row); return scroll;
    }

    public static final int[] COLORS = {0x5077cf, 0x9a5ac9, 0xe970a8, 0xf29b65, 0xdfbd68, 0x58b8a2, 0x54bde0, 0xffffff, 0x1c2030};
    public static LinearLayout swatches(Context context, int selected, Theme.ResourcesProvider resources, Consumer<Integer> click) {
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER); row.setPadding(dp(10), dp(8), dp(10), dp(8));
        for (int rgb : COLORS) {
            FrameLayout cell = new FrameLayout(context); View dot = new View(context);
            GradientDrawable bg = surface(0xff000000 | rgb, selected == rgb ? color(Theme.key_windowBackgroundWhiteBlueText, resources) : color(Theme.key_divider, resources));
            bg.setCornerRadius(dp(18)); if (selected == rgb) bg.setStroke(dp(3), color(Theme.key_windowBackgroundWhiteBlueText, resources));
            dot.setBackground(bg); cell.addView(dot, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
            cell.setContentDescription(String.format(java.util.Locale.US, "#%06X", rgb)); cell.setOnClickListener(v -> click.accept(rgb)); row.addView(cell, new LinearLayout.LayoutParams(0, dp(40), 1));
        }
        return row;
    }
}

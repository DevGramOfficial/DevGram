package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DevGramColorPickerView;
import org.telegram.ui.Components.DevGramNickColor;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// DevGram: цвет или градиент ника поддержавшего. Превью как в Telegram (имя, пузырь с ответом),
// сетка готовых вариантов и свой цвет: палитра, один цвет или градиент из двух.
public class DevGramNickColorActivity extends DevGramSettingsFragment {

    private static final int ID_GRADIENT = 1;
    private static final int ID_SAVE = 2;
    private static final int ID_HEX = 3;

    private UniversalRecyclerView listView;
    private PreviewView preview;
    private GridView grid;
    private SwatchesView swatches;
    private DevGramColorPickerView picker;

    private int[] saved;        // сохранённый на сервере цвет
    private int[] shown;        // что сейчас в превью
    private boolean customMode;
    private boolean customGradient = true;
    private final int[] custom = {0xFF00B4D8, 0xFFB04BF2};
    private int editing;        // 0 — начало, 1 — конец градиента
    private boolean saving;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Цвет ника");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && onBackPressed(true)) finishFragment();
            }
        });
        saved = DevGramBadges.nickColorsOf(getUserConfig().getClientUserId());
        shown = saved;
        if (saved != null && presetIndex(saved) < 0) {
            customMode = true;
            customGradient = saved.length > 1;
            custom[0] = saved[0];
            custom[1] = saved.length > 1 ? saved[1] : saved[0];
        }
        preview = new PreviewView(context);
        grid = new GridView(context);
        swatches = new SwatchesView(context);
        picker = new DevGramColorPickerView(context);
        picker.setColor(custom[0]);
        picker.setListener(color -> {
            custom[editing] = color;
            shown = customColors();
            preview.invalidate();
            swatches.invalidate();
            grid.invalidate();
        });
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        return fragmentView = content;
    }

    private int[] customColors() {
        return customGradient ? new int[]{custom[0], custom[1]} : new int[]{custom[0]};
    }

    private static int presetIndex(int[] colors) {
        if (colors == null) return -1;
        for (int i = 0; i < DevGramNickColor.PRESETS.length; i++) {
            if (Arrays.equals(DevGramNickColor.PRESETS[i].colors, colors)) return i;
        }
        return -1;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(preview));
        items.add(UItem.asShadow("Так ваше имя видят все пользователи DevGram: в чатах, списке чатов, профиле и шапке чата."));
        items.add(UItem.asHeader("Цвет"));
        items.add(UItem.asCustom(grid));
        if (customMode) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader("Свой цвет"));
            items.add(UItem.asCheck(ID_GRADIENT, "Градиент").setChecked(customGradient));
            if (customGradient) items.add(UItem.asCustom(swatches));
            items.add(UItem.asCustom(picker));
            items.add(UItem.asButton(ID_HEX, R.drawable.msg_palette, "Ввести HEX-код"));
            items.add(UItem.asButton(ID_SAVE, R.drawable.msg_select, "Сохранить свой цвет").accent());
        }
        items.add(UItem.asShadow(null));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_GRADIENT) {
            customGradient = !customGradient;
            if (!customGradient) editing = 0;
            picker.setColor(custom[editing]);
            shown = customColors();
            preview.invalidate();
            listView.adapter.update(true);
        } else if (item.id == ID_SAVE) {
            save(customColors());
        } else if (item.id == ID_HEX) {
            askHex();
        }
    }

    private static String hex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    private static final Pattern HEX_CODE = Pattern.compile("#?([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3})(?![0-9a-fA-F])");

    // Разобрать коды из строки: #RGB, #RRGGBB или #AARRGGBB (прозрачность игнорируем — ник всегда непрозрачный)
    private static ArrayList<Integer> parseHex(String text) {
        ArrayList<Integer> out = new ArrayList<>();
        Matcher m = HEX_CODE.matcher(text);
        while (m.find() && out.size() < 2) {
            String h = m.group(1);
            if (h.length() == 3) {
                h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            } else if (h.length() == 8) {
                h = h.substring(2);
            }
            out.add(0xFF000000 | Integer.parseInt(h, 16));
        }
        return out;
    }

    private void askHex() {
        Context context = getParentActivity();
        if (context == null) return;
        EditTextBoldCursor et = DevGramBadgesActivity.makeInput(this, context, customGradient ? "#00B4D8 #B04BF2" : "#00B4D8",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setText(customGradient ? hex(custom[0]) + " " + hex(custom[1]) : hex(custom[0]));
        et.setSelection(et.length());
        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(et, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(context, resourceProvider);
        b.setTitle("HEX-код цвета");
        b.setMessage(customGradient ? "Два кода через пробел — начало и конец градиента. Один код покрасит выбранный кружок." : "Например, #FF5722");
        b.setView(container);
        b.setPositiveButton(LocaleController.getString(R.string.Done), (d, w) -> {
            ArrayList<Integer> colors = parseHex(et.getText().toString());
            if (colors.isEmpty()) {
                BulletinFactory.of(this).createErrorBulletin("Не похоже на HEX-код").show();
                return;
            }
            if (customGradient && colors.size() > 1) {
                custom[0] = colors.get(0);
                custom[1] = colors.get(1);
            } else {
                custom[customGradient ? editing : 0] = colors.get(0);
            }
            picker.setColor(custom[editing]);
            shown = customColors();
            preview.invalidate();
            swatches.invalidate();
            grid.invalidate();
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        et.requestFocus();
        AndroidUtilities.showKeyboard(et);
    }

    // Свой цвет подобран, но кнопку «Сохранить» не нажали
    private boolean hasUnsavedCustom() {
        return customMode && !saving && shown != null && presetIndex(shown) < 0 && !Arrays.equals(shown, saved);
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (hasUnsavedCustom()) {
            if (invoked) showUnsavedAlert();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return !hasUnsavedCustom();
    }

    private void showUnsavedAlert() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        b.setTitle("Сохранить цвет?");
        b.setMessage("Свой цвет ника подобран, но ещё не сохранён.");
        b.setPositiveButton("Сохранить", (d, w) -> save(customColors(), true));
        b.setNegativeButton("Не сохранять", (d, w) -> finishFragment());
        showDialog(b.create());
    }

    private void openCustom() {
        if (!customMode) {
            customMode = true;
            listView.adapter.update(true);
        }
        shown = customColors();
        picker.setColor(custom[editing]);
        preview.invalidate();
        grid.invalidate();
    }

    private void save(int[] colors) {
        save(colors, false);
    }

    private void save(int[] colors, boolean closeAfter) {
        if (saving) return;
        saving = true;
        final int[] previous = saved;
        shown = colors;
        preview.invalidate();
        grid.invalidate();
        try {
            JSONArray arr = new JSONArray();
            if (colors != null) for (int c : colors) arr.put(c & 0xFFFFFFFFL);
            JSONObject f = new JSONObject();
            f.put("n", arr);
            DevGramBadges.updateSelfFields(currentAccount, f, (ok, error) -> {
                saving = false;
                if (ok) {
                    saved = colors;
                    if (closeAfter) {
                        finishFragment();
                        return;
                    }
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, colors == null ? "Обычный цвет ника" : "Цвет ника сохранён").show();
                } else {
                    shown = previous;
                    BulletinFactory.of(this).createErrorBulletin(error != null ? error : "Не удалось сохранить").show();
                }
                preview.invalidate();
                grid.invalidate();
            });
        } catch (Exception e) {
            saving = false;
        }
    }

    private String myName() {
        TLRPC.User me = getUserConfig().getCurrentUser();
        String n = me != null ? UserObject.getUserName(me) : "";
        return TextUtils.isEmpty(n) ? "DevGram" : n;
    }

    // Превью в духе Telegram: имя как в профиле и пузырь сообщения группы с цитатой-ответом
    private class PreviewView extends View {
        private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final DevGramNickColor.Cache bigShader = new DevGramNickColor.Cache();
        private final DevGramNickColor.Cache nameShader = new DevGramNickColor.Cache();
        private final DevGramNickColor.Cache replyShader = new DevGramNickColor.Cache();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        PreviewView(Context context) {
            super(context);
            big.setTextSize(AndroidUtilities.dp(22));
            big.setTypeface(AndroidUtilities.bold());
            name.setTextSize(AndroidUtilities.dp(14));
            name.setTypeface(AndroidUtilities.bold());
            text.setTextSize(AndroidUtilities.dp(15));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(200), MeasureSpec.EXACTLY));
        }

        private void tint(TextPaint p, int def, DevGramNickColor.Cache cache, float w) {
            p.setShader(null);
            p.setColor(def);
            if (shown != null) DevGramNickColor.apply(p, shown, w, cache);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
            String n = myName();
            int c0 = shown != null ? shown[0] : Theme.getColor(Theme.key_chat_inForwardedNameText, resourceProvider);

            // имя как в шапке профиля
            float bw = big.measureText(n);
            canvas.save();
            canvas.translate((getWidth() - bw) / 2f, AndroidUtilities.dp(42));
            tint(big, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), bigShader, bw);
            canvas.drawText(n, 0, 0, big);
            canvas.restore();

            // пузырь сообщения
            float left = AndroidUtilities.dp(16), top = AndroidUtilities.dp(64);
            float width = Math.min(getWidth() - left * 2, AndroidUtilities.dp(300));
            rect.set(left, top, left + width, top + AndroidUtilities.dp(118));
            paint.setShader(null);
            paint.setColor(Theme.getColor(Theme.key_chat_inBubble, resourceProvider));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), paint);

            float nw = name.measureText(n);
            canvas.save();
            canvas.translate(left + AndroidUtilities.dp(12), top + AndroidUtilities.dp(24));
            tint(name, c0, nameShader, nw);
            canvas.drawText(n, 0, 0, name);
            canvas.restore();

            // цитата-ответ: полоска и подложка в цвете ника
            RectF quote = new RectF(left + AndroidUtilities.dp(10), top + AndroidUtilities.dp(34), rect.right - AndroidUtilities.dp(10), top + AndroidUtilities.dp(78));
            paint.setColor(Theme.multAlpha(c0, 0.12f));
            canvas.drawRoundRect(quote, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
            paint.setColor(c0);
            if (shown != null && shown.length > 1) {
                paint.setShader(new LinearGradient(0, quote.top, 0, quote.bottom, shown[0], shown[1], Shader.TileMode.CLAMP));
            }
            canvas.drawRoundRect(new RectF(quote.left, quote.top, quote.left + AndroidUtilities.dp(3), quote.bottom), AndroidUtilities.dp(1.5f), AndroidUtilities.dp(1.5f), paint);
            paint.setShader(null);
            canvas.save();
            canvas.translate(quote.left + AndroidUtilities.dp(10), quote.top + AndroidUtilities.dp(18));
            tint(name, c0, replyShader, nw);
            canvas.drawText(n, 0, 0, name);
            canvas.restore();
            text.setColor(Theme.getColor(Theme.key_chat_messageTextIn, resourceProvider));
            text.setTextSize(AndroidUtilities.dp(14));
            canvas.drawText("Ответ на сообщение", quote.left + AndroidUtilities.dp(10), quote.top + AndroidUtilities.dp(36), text);
            text.setTextSize(AndroidUtilities.dp(15));
            canvas.drawText("Как вам мой новый цвет? ✨", left + AndroidUtilities.dp(12), top + AndroidUtilities.dp(102), text);
        }
    }

    // Сетка кружков: «Обычный», готовые варианты и «Свой». Выделение и нажатие анимированы.
    private class GridView extends View {
        private static final int COLS = 5;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint plus = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Shader[] shaders = new Shader[DevGramNickColor.PRESETS.length];
        private Shader rainbow;
        private final AnimatedFloat[] selectT = new AnimatedFloat[DevGramNickColor.PRESETS.length + 2];
        private final AnimatedFloat[] pressT = new AnimatedFloat[DevGramNickColor.PRESETS.length + 2];
        private int downIndex = -1;

        GridView(Context context) {
            super(context);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(AndroidUtilities.dp(2.5f));
            plus.setStrokeWidth(AndroidUtilities.dp(2.5f));
            plus.setStrokeCap(Paint.Cap.ROUND);
            plus.setColor(Color.WHITE);
            for (int i = 0; i < selectT.length; i++) {
                selectT[i] = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
                pressT[i] = new AnimatedFloat(this, 0, 160, CubicBezierInterpolator.EASE_OUT);
            }
        }

        private int count() {
            return DevGramNickColor.PRESETS.length + 2;
        }

        private float cellW() {
            return getWidth() / (float) COLS;
        }

        private float rowH() {
            return AndroidUtilities.dp(66);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int rows = (count() + COLS - 1) / COLS;
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) (rows * rowH()) + AndroidUtilities.dp(12), MeasureSpec.EXACTLY));
        }

        private boolean isSelected(int i) {
            if (i == 0) return shown == null;
            if (i == count() - 1) return customMode && shown != null && presetIndex(shown) < 0;
            return Arrays.equals(DevGramNickColor.PRESETS[i - 1].colors, shown);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            float cw = cellW();
            float r = AndroidUtilities.dp(21);
            for (int i = 0; i < count(); i++) {
                float cx = cw * (i % COLS) + cw / 2f;
                float cy = rowH() * (i / COLS) + AndroidUtilities.dp(36);
                float sel = selectT[i].set(isSelected(i));
                float press = pressT[i].set(downIndex == i);
                int ringColor;
                if (i == 0) {
                    fill.setShader(null);
                    fill.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
                    ringColor = fill.getColor();
                } else if (i == count() - 1) {
                    if (rainbow == null) {
                        rainbow = new SweepGradient(0, 0, new int[]{0xFFFF3D00, 0xFFFFC400, 0xFF00C853, 0xFF00B0FF, 0xFFAA00FF, 0xFFFF3D00}, null);
                    }
                    fill.setShader(rainbow);
                    ringColor = customMode ? custom[0] : 0xFF00B0FF;
                } else {
                    int[] colors = DevGramNickColor.PRESETS[i - 1].colors;
                    if (colors.length == 1) {
                        fill.setShader(null);
                        fill.setColor(colors[0]);
                    } else {
                        if (shaders[i - 1] == null) {
                            shaders[i - 1] = new LinearGradient(-r, -r, r, r, colors[0], colors[1], Shader.TileMode.CLAMP);
                        }
                        fill.setShader(shaders[i - 1]);
                    }
                    ringColor = colors[0];
                }
                canvas.save();
                canvas.translate(cx, cy);
                float scale = 1f - 0.08f * press;
                canvas.scale(scale, scale);
                // выбранный кружок сжимается, вокруг появляется кольцо его цвета — как в Telegram
                canvas.drawCircle(0, 0, r - AndroidUtilities.dp(5) * sel, fill);
                if (sel > 0) {
                    ring.setColor(ringColor);
                    ring.setAlpha((int) (255 * sel));
                    canvas.drawCircle(0, 0, r - AndroidUtilities.dp(1.25f), ring);
                }
                if (i == 0) {
                    plus.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
                    float d = AndroidUtilities.dp(7) * (1f - 0.2f * sel);
                    canvas.drawLine(-d, d, d, -d, plus);
                } else if (i == count() - 1) {
                    plus.setColor(Color.WHITE);
                    float d = AndroidUtilities.dp(6) * (1f - 0.2f * sel);
                    canvas.drawLine(-d, 0, d, 0, plus);
                    canvas.drawLine(0, -d, 0, d, plus);
                }
                canvas.restore();
            }
        }

        private int indexAt(float x, float y) {
            int col = (int) (x / cellW());
            int row = (int) (y / rowH());
            int i = row * COLS + col;
            return col < COLS && i >= 0 && i < count() ? i : -1;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downIndex = indexAt(e.getX(), e.getY());
                    invalidate();
                    return downIndex >= 0;
                case MotionEvent.ACTION_UP: {
                    int i = indexAt(e.getX(), e.getY());
                    if (i >= 0 && i == downIndex) {
                        if (i == count() - 1) {
                            openCustom();
                        } else {
                            save(i == 0 ? null : DevGramNickColor.PRESETS[i - 1].colors);
                        }
                    }
                    downIndex = -1;
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    downIndex = -1;
                    invalidate();
                    return true;
            }
            return true;
        }
    }

    // Два образца градиента «Начало» / «Конец»: какой выбран, тот и красит палитра
    private class SwatchesView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        SwatchesView(Context context) {
            super(context);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(AndroidUtilities.dp(2.5f));
            label.setTextSize(AndroidUtilities.dp(14));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            label.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            float half = getWidth() / 2f;
            for (int i = 0; i < 2; i++) {
                float cx = half * i + AndroidUtilities.dp(40);
                float cy = getHeight() / 2f;
                fill.setColor(custom[i]);
                canvas.drawCircle(cx, cy, AndroidUtilities.dp(15), fill);
                if (editing == i) {
                    ring.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(20), ring);
                }
                canvas.drawText(i == 0 ? "Начало" : "Конец", cx + AndroidUtilities.dp(30), cy + AndroidUtilities.dp(5), label);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                editing = e.getX() < getWidth() / 2f ? 0 : 1;
                picker.setColor(custom[editing]);
                invalidate();
            }
            return true;
        }
    }
}

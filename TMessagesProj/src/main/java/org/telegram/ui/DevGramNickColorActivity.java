package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.DevGramNickColor;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;

// DevGram: цвет или градиент ника поддержавшего — превью своего имени и сетка вариантов.
public class DevGramNickColorActivity extends DevGramSettingsFragment {

    private UniversalRecyclerView listView;
    private PreviewView preview;
    private GridView grid;
    private int[] selected;
    private boolean saving;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Цвет ника");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        selected = DevGramBadges.nickColorsOf(getUserConfig().getClientUserId());
        preview = new PreviewView(context);
        grid = new GridView(context);
        listView = new UniversalRecyclerView(this, this::fillItems, null, null);
        org.telegram.ui.Components.DevGramSettingsLayout content =
                new org.telegram.ui.Components.DevGramSettingsLayout(context, actionBar, listView, resourceProvider);
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(preview));
        items.add(UItem.asShadow("Так ваше имя видят все пользователи DevGram: в чатах, списке чатов, профиле и шапке чата."));
        items.add(UItem.asHeader("Цвет"));
        items.add(UItem.asCustom(grid));
        items.add(UItem.asShadow(null));
    }

    private void choose(int[] colors) {
        if (saving || Arrays.equals(colors, selected)) return;
        saving = true;
        final int[] previous = selected;
        selected = colors;
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
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, colors == null ? "Обычный цвет ника" : "Цвет ника сохранён").show();
                } else {
                    selected = previous;
                    preview.invalidate();
                    grid.invalidate();
                    BulletinFactory.of(this).createErrorBulletin(error != null ? error : "Не удалось сохранить").show();
                }
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

    // Превью: имя крупно, как в шапке профиля, и мелко, как в сообщении группы
    private class PreviewView extends View {
        private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final DevGramNickColor.Cache bigShader = new DevGramNickColor.Cache();
        private final DevGramNickColor.Cache smallShader = new DevGramNickColor.Cache();
        private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        PreviewView(Context context) {
            super(context);
            big.setTextSize(AndroidUtilities.dp(24));
            big.setTypeface(AndroidUtilities.bold());
            small.setTextSize(AndroidUtilities.dp(14));
            small.setTypeface(AndroidUtilities.bold());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(150), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            String name = myName();
            int def = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
            float w = big.measureText(name);
            float x = (getWidth() - w) / 2f;
            canvas.save();
            canvas.translate(x, AndroidUtilities.dp(52));
            big.setShader(null);
            big.setColor(def);
            if (selected != null) DevGramNickColor.apply(big, selected, w, bigShader);
            canvas.drawText(name, 0, 0, big);
            canvas.restore();

            // мини-сообщение группы
            float sw = small.measureText(name);
            float bw = Math.max(sw, AndroidUtilities.dp(150)) + AndroidUtilities.dp(24);
            rect.set(AndroidUtilities.dp(20), AndroidUtilities.dp(76), AndroidUtilities.dp(20) + bw, AndroidUtilities.dp(134));
            bubble.setColor(Theme.getColor(Theme.key_chat_inBubble, resourceProvider));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), bubble);
            canvas.save();
            canvas.translate(rect.left + AndroidUtilities.dp(12), rect.top + AndroidUtilities.dp(22));
            small.setShader(null);
            small.setColor(Theme.getColor(Theme.key_chat_inForwardedNameText, resourceProvider));
            if (selected != null) DevGramNickColor.apply(small, selected, sw, smallShader);
            canvas.drawText(name, 0, 0, small);
            canvas.restore();
            small.setShader(null);
            small.setTypeface(null);
            small.setColor(Theme.getColor(Theme.key_chat_messageTextIn, resourceProvider));
            canvas.drawText("Привет! 👋", rect.left + AndroidUtilities.dp(12), rect.top + AndroidUtilities.dp(44), small);
            small.setTypeface(AndroidUtilities.bold());
        }
    }

    // Сетка: «Обычный» + все варианты. Кружок-образец, подпись, выбранный — в рамке.
    private class GridView extends View {
        private static final int COLS = 4;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Shader[] shaders = new Shader[DevGramNickColor.PRESETS.length];
        private int downIndex = -1;

        GridView(Context context) {
            super(context);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(AndroidUtilities.dp(2.5f));
            label.setTextSize(AndroidUtilities.dp(12));
            label.setTextAlign(Paint.Align.CENTER);
        }

        private int count() {
            return DevGramNickColor.PRESETS.length + 1;
        }

        private float cellW() {
            return getWidth() / (float) COLS;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int rows = (count() + COLS - 1) / COLS;
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(rows * AndroidUtilities.dp(84) + AndroidUtilities.dp(8), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            float cw = cellW();
            float r = AndroidUtilities.dp(20);
            label.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            ring.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
            for (int i = 0; i < count(); i++) {
                float cx = cw * (i % COLS) + cw / 2f;
                float cy = AndroidUtilities.dp(84) * (i / COLS) + AndroidUtilities.dp(34);
                int[] colors = i == 0 ? null : DevGramNickColor.PRESETS[i - 1].colors;
                if (colors == null) {
                    fill.setShader(null);
                    fill.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
                } else if (colors.length == 1) {
                    fill.setShader(null);
                    fill.setColor(colors[0]);
                } else {
                    if (shaders[i - 1] == null) {
                        shaders[i - 1] = new LinearGradient(-r, 0, r, 0, colors[0], colors[1], Shader.TileMode.CLAMP);
                    }
                    fill.setShader(shaders[i - 1]);
                }
                canvas.save();
                canvas.translate(cx, cy);
                canvas.drawCircle(0, 0, r, fill);
                if (Arrays.equals(colors, selected)) canvas.drawCircle(0, 0, r + AndroidUtilities.dp(4), ring);
                canvas.restore();
                String t = i == 0 ? "Обычный" : DevGramNickColor.PRESETS[i - 1].title;
                canvas.drawText(t, cx, cy + r + AndroidUtilities.dp(18), label);
            }
        }

        private int indexAt(float x, float y) {
            int col = (int) (x / cellW());
            int row = (int) (y / AndroidUtilities.dp(84));
            int i = row * COLS + col;
            return i >= 0 && i < count() && col < COLS ? i : -1;
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                downIndex = indexAt(e.getX(), e.getY());
                return downIndex >= 0;
            } else if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                int i = indexAt(e.getX(), e.getY());
                if (i >= 0 && i == downIndex) {
                    choose(i == 0 ? null : DevGramNickColor.PRESETS[i - 1].colors);
                }
                downIndex = -1;
                return true;
            } else if (e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                downIndex = -1;
            }
            return true;
        }
    }
}

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramColorCode;
import org.telegram.messenger.DevGramLocale;
import org.telegram.ui.ActionBar.Theme;

/** Shared visual palette with an editable HEX field for every customization editor. */
public class DevGramColorPickerView extends LinearLayout {
    public interface Listener {
        void onColor(int color);
    }

    private final Spectrum spectrum;
    private final EditTextBoldCursor code;
    private final boolean alphaEnabled;
    private Listener listener;
    private int color = 0xffff0000;
    private boolean syncing;

    public DevGramColorPickerView(Context context) {
        this(context, null);
    }

    public DevGramColorPickerView(Context context, Theme.ResourcesProvider resources) {
        this(context, resources, false);
    }

    public DevGramColorPickerView(Context context, Theme.ResourcesProvider resources, boolean alphaEnabled) {
        super(context);
        this.alphaEnabled = alphaEnabled;
        setOrientation(VERTICAL);
        setFocusableInTouchMode(true);
        spectrum = new Spectrum(context);
        addView(spectrum, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout entry = new LinearLayout(context);
        entry.setOrientation(VERTICAL);
        entry.setPadding(dp(20), 0, dp(20), dp(8));
        TextView label = new TextView(context);
        label.setText(DevGramLocale.text("HEX-код цвета"));
        label.setTextSize(13);
        label.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        entry.addView(label, new LinearLayout.LayoutParams(-1, -2));

        code = new EditTextBoldCursor(context);
        code.setSingleLine(true);
        code.setTextSize(16);
        code.setTypeface(Typeface.MONOSPACE);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        code.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        code.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, resources));
        code.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resources));
        code.setHighlightColor(Theme.getColor(Theme.key_chat_textSelectBackground, resources));
        code.setBackground(Theme.createEditTextDrawable(context,
                Theme.getColor(Theme.key_dialogInputField, resources),
                Theme.getColor(Theme.key_dialogInputFieldActivated, resources)));
        code.setPadding(0, dp(6), 0, dp(6));
        code.setHint(alphaEnabled ? "#AARRGGBB" : "#RRGGBB");
        code.setContentDescription(label.getText());
        entry.addView(code, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView formats = new TextView(context);
        formats.setText(DevGramLocale.text(alphaEnabled
                ? "Форматы: #RGB, #RRGGBB или #AARRGGBB"
                : "Форматы: #RGB или #RRGGBB"));
        formats.setTextSize(12);
        formats.setTextColor(Theme.getColor(Theme.key_dialogTextGray, resources));
        entry.addView(formats, new LinearLayout.LayoutParams(-1, -2));
        addView(entry, new LinearLayout.LayoutParams(-1, -2));

        code.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable value) {
                if (syncing) return;
                code.setError(null);
                Integer parsed = DevGramColorCode.parseHex(value.toString(), color >>> 24, alphaEnabled);
                if (parsed == null) return;
                color = parsed;
                spectrum.setColor(color);
                if (listener != null) listener.onColor(color);
            }
        });
        code.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            if (!validateCode()) return true;
            refreshCode();
            InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(code.getWindowToken(), 0);
            code.clearFocus();
            return true;
        });
        code.setOnFocusChangeListener((view, focused) -> {
            if (!focused && validateCode()) refreshCode();
        });
        refreshCode();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setColor(int color) {
        this.color = alphaEnabled ? color : 0xff000000 | color;
        spectrum.setColor(this.color);
        refreshCode();
    }

    public int getColor() {
        return color;
    }

    private void paletteChanged(int rgb) {
        color = (color & 0xff000000) | (rgb & 0xffffff);
        refreshCode();
        if (listener != null) listener.onColor(color);
    }

    private boolean validateCode() {
        if (DevGramColorCode.parseHex(code.getText().toString(), color >>> 24, alphaEnabled) != null) return true;
        code.setError(DevGramLocale.text("Не похоже на HEX-код"));
        return false;
    }

    private void refreshCode() {
        syncing = true;
        try {
            String value = DevGramColorCode.hex(color, alphaEnabled);
            if (!value.contentEquals(code.getText())) {
                code.setText(value);
                code.setSelection(code.length());
            }
            code.setError(null);
        } finally {
            syncing = false;
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = resolveSize(dp(346), heightSpec);
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    // Shaders are rebuilt only when the palette size or hue changes.
    private final class Spectrum extends View {
        private final float[] hsv = {0f, 1f, 1f};
        private final Paint svPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF svRect = new RectF();
        private final RectF hueRect = new RectF();
        private float shaderHue = -1;
        private int shaderW, shaderH;
        private int dragging; // 0 — нет, 1 — квадрат, 2 — оттенок

        Spectrum(Context context) {
            super(context);
            knob.setStyle(Paint.Style.STROKE);
            knob.setStrokeWidth(AndroidUtilities.dp(3));
            knob.setColor(Color.WHITE);
            knob.setShadowLayer(AndroidUtilities.dp(2), 0, 0, 0x66000000);
            setLayerType(LAYER_TYPE_SOFTWARE, knob); // тень у кольца-ползунка
        }

        public void setColor(int color) {
            Color.colorToHSV(color, hsv);
            invalidate();
        }

        public int getColor() {
            return Color.HSVToColor(hsv);
        }

        private void layoutRects() {
            float pad = AndroidUtilities.dp(20);
            svRect.set(pad, AndroidUtilities.dp(12), getWidth() - pad, getHeight() - AndroidUtilities.dp(62));
            hueRect.set(pad, getHeight() - AndroidUtilities.dp(40), getWidth() - pad, getHeight() - AndroidUtilities.dp(16));
        }

        private void updateShaders() {
            int w = (int) svRect.width(), h = (int) svRect.height();
            if (shaderHue == hsv[0] && shaderW == w && shaderH == h) return;
            shaderHue = hsv[0];
            shaderW = w;
            shaderH = h;
            int pure = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});
            Shader sat = new LinearGradient(svRect.left, 0, svRect.right, 0, Color.WHITE, pure, Shader.TileMode.CLAMP);
            Shader val = new LinearGradient(0, svRect.top, 0, svRect.bottom, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);
            svPaint.setShader(new ComposeShader(val, sat, PorterDuff.Mode.MULTIPLY));
            if (huePaint.getShader() == null || hueRect.width() != shaderW) {
                int[] colors = new int[7];
                for (int i = 0; i < 7; i++) colors[i] = Color.HSVToColor(new float[]{i * 60f % 360f, 1f, 1f});
                colors[6] = colors[0];
                huePaint.setShader(new LinearGradient(hueRect.left, 0, hueRect.right, 0, colors, null, Shader.TileMode.CLAMP));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (getWidth() <= dp(40) || getHeight() <= dp(90)) return;
            layoutRects();
            updateShaders();
            float r = AndroidUtilities.dp(14);
            canvas.drawRoundRect(svRect, r, r, svPaint);
            canvas.drawRoundRect(hueRect, hueRect.height() / 2f, hueRect.height() / 2f, huePaint);

            float sx = svRect.left + hsv[1] * svRect.width();
            float sy = svRect.top + (1f - hsv[2]) * svRect.height();
            knobFill.setColor(getColor());
            canvas.drawCircle(sx, sy, AndroidUtilities.dp(11), knobFill);
            canvas.drawCircle(sx, sy, AndroidUtilities.dp(11), knob);

            float hx = hueRect.left + hsv[0] / 360f * hueRect.width();
            knobFill.setColor(Color.HSVToColor(new float[]{hsv[0], 1f, 1f}));
            canvas.drawCircle(hx, hueRect.centerY(), AndroidUtilities.dp(13), knobFill);
            canvas.drawCircle(hx, hueRect.centerY(), AndroidUtilities.dp(13), knob);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (getWidth() <= dp(40) || getHeight() <= dp(90)) return false;
            float x = e.getX(), y = e.getY();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    layoutRects();
                    if (y >= hueRect.top - AndroidUtilities.dp(12) && y <= hueRect.bottom + AndroidUtilities.dp(12)) {
                        dragging = 2;
                    } else if (svRect.contains(x, y)) {
                        dragging = 1;
                    } else {
                        return false;
                    }
                    getParent().requestDisallowInterceptTouchEvent(true);
                    // fall through
                case MotionEvent.ACTION_MOVE:
                    if (dragging == 1) {
                        hsv[1] = clamp((x - svRect.left) / svRect.width());
                        hsv[2] = 1f - clamp((y - svRect.top) / svRect.height());
                    } else if (dragging == 2) {
                        hsv[0] = clamp((x - hueRect.left) / hueRect.width()) * 359.9f;
                    }
                    invalidate();
                    if (dragging != 0) paletteChanged(getColor());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = 0;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
            }
            return false;
        }

        private float clamp(float v) {
            return v < 0 ? 0 : v > 1 ? 1 : v;
        }
    }
}

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProfileMusicView;

/** DevGram's large, interactive profile music card. */
public class ProfileMusicCardCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final BackupImageView coverView;
    private final TextView titleView;
    private final TextView authorView;
    private final TextView albumView;
    private final PlayPauseView playView;

    private final RectF cardRect = new RectF();
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint patternPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint equalizerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    private final Path planePath = new Path();

    private int color1;
    private int color2;
    private boolean currentTrack;
    private boolean playing;
    private float progress;

    public ProfileMusicCardCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);
        setClipChildren(false);

        coverView = new BackupImageView(context);
        coverView.setRoundRadius(dp(12));
        addView(coverView, LayoutHelper.createFrame(70, 70, Gravity.LEFT | Gravity.TOP, 24, 19, 0, 0));

        playView = new PlayPauseView(context);
        addView(playView, LayoutHelper.createFrame(34, 34, Gravity.LEFT | Gravity.TOP, 42, 37, 0, 0));

        titleView = createTextView(context, 17, true, Color.WHITE);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, Gravity.LEFT | Gravity.TOP, 106, 22, 48, 0));

        authorView = createTextView(context, 14, false, 0xCCFFFFFF);
        addView(authorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 21, Gravity.LEFT | Gravity.TOP, 106, 48, 48, 0));

        albumView = createTextView(context, 13, false, 0xA8FFFFFF);
        addView(albumView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.LEFT | Gravity.TOP, 106, 69, 48, 0));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(AndroidUtilities.dpf2(.7f));
        borderPaint.setColor(0x30FFFFFF);
        patternPaint.setStyle(Paint.Style.STROKE);
        patternPaint.setStrokeWidth(AndroidUtilities.dpf2(1.15f));
        patternPaint.setStrokeCap(Paint.Cap.ROUND);
        patternPaint.setStrokeJoin(Paint.Join.ROUND);
        patternPaint.setColor(0x12FFFFFF);
        progressPaint.setColor(0xDFFFFFFF);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(AndroidUtilities.dpf2(1.7f));
        equalizerPaint.setColor(0xD9FFFFFF);
        equalizerPaint.setStrokeCap(Paint.Cap.ROUND);
        equalizerPaint.setStrokeWidth(AndroidUtilities.dpf2(1.55f));
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setStrokeWidth(AndroidUtilities.dpf2(1.5f));
        arrowPaint.setColor(0xB8FFFFFF);
        arrowPath.moveTo(0, -dp(3));
        arrowPath.lineTo(dp(3), 0);
        arrowPath.lineTo(0, dp(3));

        setPeerColor(null);
    }

    private TextView createTextView(Context context, int size, boolean bold, int color) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        view.setTextColor(color);
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) {
            view.setTypeface(AndroidUtilities.bold());
        }
        return view;
    }

    public void updateDevGramRowColors(){
        int text=org.telegram.ui.Components.DevGramProfilePalette.rowColor(resourcesProvider,"text",Color.WHITE);
        titleView.setTextColor(text);
        authorView.setTextColor(org.telegram.ui.Components.DevGramProfilePalette.rowColor(resourcesProvider,"text",0xCCFFFFFF));
        albumView.setTextColor(org.telegram.ui.Components.DevGramProfilePalette.rowColor(resourcesProvider,"text",0xA8FFFFFF));
        int icon=org.telegram.ui.Components.DevGramProfilePalette.rowColor(resourcesProvider,"icon",Color.WHITE);
        playView.iconPaint.setColor(icon);playView.invalidate();
        arrowPaint.setColor(org.telegram.ui.Components.DevGramProfilePalette.rowColor(resourcesProvider,"icon",0xB8FFFFFF));
        equalizerPaint.setColor(org.telegram.ui.Components.DevGramProfilePalette.rowColor(resourcesProvider,"icon",0xD9FFFFFF));
        Integer background=org.telegram.ui.Components.DevGramProfilePalette.rowColor(resourcesProvider,"background");
        if(background!=null)color1=color2=background;
    }

    public void setPeerColor(MessagesController.PeerColor peerColor) {
        if (peerColor != null) {
            color1 = peerColor.getBgColor1(Theme.isCurrentThemeDark());
            color2 = peerColor.getBgColor2(Theme.isCurrentThemeDark());
        } else {
            int base = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
            color1 = Theme.adaptHSV(base, .02f, -.26f);
            color2 = Theme.adaptHSV(base, -.02f, -.39f);
        }
        updateDevGramRowColors();invalidate();
    }

    public void setMusic(TLRPC.Document document, MessageObject messageObject) {
        CharSequence title = ProfileMusicView.getTitle(document);
        CharSequence author = ProfileMusicView.getAuthor(document);
        titleView.setText(TextUtils.isEmpty(title) ? getString(R.string.AudioUnknownTitle) : title);
        authorView.setText(TextUtils.isEmpty(author) ? getString(R.string.AudioUnknownArtist) : author);
        albumView.setText(getString(R.string.AccDescrProfileMusic));
        setContentDescription(getString(R.string.AccDescrProfileMusic) + ", " + titleView.getText() + ", " + authorView.getText());

        TLRPC.PhotoSize thumb = document == null ? null : FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
        ImageLocation thumbLocation = thumb instanceof TLRPC.TL_photoSize || thumb instanceof TLRPC.TL_photoSizeProgressive
                ? ImageLocation.getForDocument(thumb, document) : null;
        String artwork = MessageObject.getArtworkUrl(document, false);
        ImageLocation artworkLocation = TextUtils.isEmpty(artwork) ? null : ImageLocation.getForPath(artwork);
        coverView.setImage(artworkLocation, "160_160", thumbLocation, "160_160", new MusicPlaceholderDrawable(), messageObject != null ? messageObject : document);
    }

    public void setOnPlayClickListener(Runnable listener) {
        playView.setOnClickListener(v -> {
            if (listener != null) {
                listener.run();
            }
        });
    }

    public void setPlaybackState(boolean currentTrack, boolean playing, float progress) {
        this.currentTrack = currentTrack;
        this.playing = playing;
        this.progress = Utilities.clamp(progress, 1f, 0f);
        playView.setPlaying(currentTrack && playing);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(108), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        cardRect.set(dp(12), dp(7), getWidth() - dp(12), getHeight() - dp(7));
        cardPaint.setShader(new LinearGradient(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                color1, color2, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(cardRect, dp(20), dp(20), cardPaint);
        cardPaint.setShader(null);
        canvas.drawRoundRect(cardRect, dp(20), dp(20), borderPaint);

        canvas.save();
        canvas.clipRect(cardRect.left + dp(92), cardRect.top, cardRect.right, cardRect.bottom);
        drawPlanePattern(canvas);
        canvas.restore();

        if (currentTrack) {
            float barsX = cardRect.right - dp(35);
            float cy = cardRect.centerY();
            float phase = (SystemClock.uptimeMillis() % 900L) / 900f * (float) (Math.PI * 2);
            for (int i = 0; i < 3; i++) {
                float factor = playing ? .3f + .7f * Math.abs((float) Math.sin(phase + i * 1.5f)) : .4f + i * .15f;
                float half = dp(4.5f) * factor;
                canvas.drawLine(barsX + dp(i * 3.2f), cy - half, barsX + dp(i * 3.2f), cy + half, equalizerPaint);
            }
        }

        canvas.save();
        canvas.translate(cardRect.right - dp(15), cardRect.centerY());
        canvas.drawPath(arrowPath, arrowPaint);
        canvas.restore();

        if (currentTrack && progress > 0) {
            float left = dp(106);
            float right = cardRect.right - dp(48);
            canvas.drawLine(left, cardRect.bottom - dp(8), left + (right - left) * progress, cardRect.bottom - dp(8), progressPaint);
        }
        if (currentTrack && playing) {
            postInvalidateOnAnimation();
        }
    }

    private void drawPlanePattern(Canvas canvas) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                float x = cardRect.left + dp(112 + col * 50 + (row % 2) * 19);
                float y = cardRect.top + dp(18 + row * 31);
                planePath.rewind();
                planePath.moveTo(x - dp(6), y - dp(3));
                planePath.lineTo(x + dp(7), y - dp(7));
                planePath.lineTo(x + dp(2), y + dp(7));
                planePath.lineTo(x - dp(1), y + dp(1));
                planePath.close();
                canvas.drawPath(planePath, patternPaint);
            }
        }
    }

    private static class PlayPauseView extends View {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ButtonBounce bounce = new ButtonBounce(this);
        private boolean playing;

        PlayPauseView(Context context) {
            super(context);
            setClickable(true);
            backgroundPaint.setColor(0xB81D1F24);
            iconPaint.setColor(Color.WHITE);
            iconPaint.setStrokeWidth(AndroidUtilities.dpf2(2));
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setPlaying(boolean playing) {
            if (this.playing != playing) {
                this.playing = playing;
                invalidate();
            }
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            bounce.setPressed(pressed);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float scale = bounce.getScale(.08f);
            canvas.save();
            canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            canvas.drawCircle(cx, cy, dp(15), backgroundPaint);
            if (playing) {
                canvas.drawLine(cx - dp(3), cy - dp(5), cx - dp(3), cy + dp(5), iconPaint);
                canvas.drawLine(cx + dp(3), cy - dp(5), cx + dp(3), cy + dp(5), iconPaint);
            } else {
                Path path = new Path();
                path.moveTo(cx - dp(3), cy - dp(6));
                path.lineTo(cx + dp(6), cy);
                path.lineTo(cx - dp(3), cy + dp(6));
                path.close();
                canvas.drawPath(path, iconPaint);
            }
            canvas.restore();
        }
    }

    private static class MusicPlaceholderDrawable extends Drawable {
        private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);

        MusicPlaceholderDrawable() {
            background.setColor(0xFF3B3D46);
            note.setColor(0xDFFFFFFF);
            note.setStrokeWidth(dp(3));
            note.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawRect(getBounds(), background);
            float cx = getBounds().exactCenterX();
            float cy = getBounds().exactCenterY();
            canvas.drawLine(cx + dp(4), cy - dp(12), cx + dp(4), cy + dp(6), note);
            canvas.drawLine(cx + dp(4), cy - dp(12), cx + dp(12), cy - dp(9), note);
            canvas.drawCircle(cx, cy + dp(8), dp(5), note);
            canvas.drawCircle(cx + dp(8), cy + dp(5), dp(5), note);
        }

        @Override public void setAlpha(int alpha) { background.setAlpha(alpha); note.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { background.setColorFilter(colorFilter); note.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}

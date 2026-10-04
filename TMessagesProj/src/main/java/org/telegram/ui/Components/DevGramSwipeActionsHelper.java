package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.DevGramSwipeAction;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

/** Extera-style swipe action selector: gesture tracking, spring motion and service-bubble drawing. */
public class DevGramSwipeActionsHelper {
    private static final float SPRING_UNIT = 2000f;

    private final View parent;
    private final Theme.ResourcesProvider resourcesProvider;
    private final ArrayList<DevGramSwipeAction> actions = new ArrayList<>();
    private final SparseArray<Drawable> icons = new SparseArray<>();
    private final SparseArray<Drawable> spareIcons = new SparseArray<>();
    private final SparseArray<Float> iconFits = new SparseArray<>();
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final AnimatedFloat liveAlpha;
    private final FloatValueHolder position = new FloatValueHolder(0f);
    private final FloatValueHolder visibility = new FloatValueHolder(0f);
    private final FloatValueHolder fill = new FloatValueHolder(0f);
    private final FloatValueHolder ring = new FloatValueHolder(0f);
    private final SpringAnimation positionSpring;
    private final SpringAnimation visibilitySpring;
    private final SpringAnimation fillSpring;
    private final SpringAnimation ringSpring;

    private Paint framePaint;
    private boolean frameGradient;
    private boolean frameDark;
    private ColorFilter iconFilter;
    private int iconFilterColor;
    private Drawable reactionDrawable;
    private AnimatedEmojiDrawable animatedReaction;
    private long animatedReactionId;
    private ImageReceiver reactionImage;
    private String reactionEmojicon;

    private int selected;
    private int slot;
    private int direction;
    private boolean looped;
    private boolean reversed;
    private boolean armed;
    private boolean scrubbing;
    private boolean stepped;
    private boolean beyondMax;
    private float lastX;
    private float lastY;
    private float driftX;
    private float driftY;
    private float anchorY;

    public DevGramSwipeActionsHelper(View parent, Theme.ResourcesProvider resourcesProvider) {
        this.parent = parent;
        this.resourcesProvider = resourcesProvider;
        liveAlpha = new AnimatedFloat(parent::invalidate, 0L, 280L, CubicBezierInterpolator.EASE_OUT);

        positionSpring = spring(position, 430f, .55f, false);
        visibilitySpring = spring(visibility, 1500f, 1f, true);
        visibilitySpring.setMaxValue(SPRING_UNIT);
        fillSpring = spring(fill, 400f, .5f, true);
        ringSpring = spring(ring, 200f, 1f, true);

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    private SpringAnimation spring(FloatValueHolder holder, float stiffness, float damping, boolean minZero) {
        SpringAnimation animation = new SpringAnimation(holder)
                .setSpring(new SpringForce(0f).setStiffness(stiffness).setDampingRatio(damping));
        if (minZero) animation.setMinValue(0f);
        animation.addUpdateListener((value, current, velocity) -> parent.invalidate());
        return animation;
    }

    public void setReaction(int account, String emoticon, long documentId) {
        if (documentId != 0) {
            if (animatedReactionId != documentId) {
                detachReaction();
                animatedReaction = AnimatedEmojiDrawable.make(account, 0, documentId);
                animatedReaction.addView(parent);
                animatedReactionId = documentId;
            }
            reactionDrawable = animatedReaction;
            return;
        }
        TLRPC.TL_availableReaction reaction = emoticon == null ? null
                : MediaDataController.getInstance(account).getReactionsMap().get(emoticon);
        if (reaction != null && reaction.center_icon != null) {
            if (reactionImage == null || !TextUtils.equals(reactionEmojicon, emoticon)) {
                detachReaction();
                reactionImage = new ImageReceiver(parent);
                reactionImage.ignoreNotifications = true;
                reactionImage.onAttachedToWindow();
                reactionImage.setImage(ImageLocation.getForDocument(reaction.center_icon), "40_40_lastreactframe",
                        DocumentObject.getSvgThumb(reaction.static_icon, Theme.key_windowBackgroundGray, 1f),
                        "webp", reaction, 1);
                reactionEmojicon = emoticon;
            }
            return;
        }
        detachReaction();
        reactionDrawable = emoticon == null ? null : Emoji.getEmojiDrawable(emoticon);
    }

    private void detachReaction() {
        if (animatedReaction != null) {
            animatedReaction.removeView(parent);
            animatedReaction = null;
            animatedReactionId = 0;
        }
        if (reactionImage != null) {
            reactionImage.onDetachedFromWindow();
            reactionImage = null;
            reactionEmojicon = null;
        }
        reactionDrawable = null;
    }

    public void detach() {
        detachReaction();
        positionSpring.cancel();
        visibilitySpring.cancel();
        fillSpring.cancel();
        ringSpring.cancel();
    }

    public void start(List<DevGramSwipeAction> available) {
        actions.clear();
        if (available != null) actions.addAll(available);
        looped = DevGramConfig.swipeActionsLoop;
        reversed = DevGramConfig.swipeActionsReversed;
        selected = 0;
        slot = 0;
        direction = 0;
        armed = false;
        scrubbing = false;
        stepped = false;
        beyondMax = false;
        anchorY = driftX = driftY = 0f;
        liveAlpha.set(0f, true);
        reset(positionSpring, position, 0f);
        reset(visibilitySpring, visibility, 0f);
        reset(fillSpring, fill, 0f);
        reset(ringSpring, ring, 0f);
        parent.invalidate();
    }

    private static void reset(SpringAnimation animation, FloatValueHolder holder, float value) {
        animation.cancel();
        animation.getSpring().setFinalPosition(value);
        holder.setValue(value);
    }

    public int size() {
        return actions.size();
    }

    public DevGramSwipeAction getSelected() {
        return selected >= 0 && selected < actions.size() ? actions.get(selected) : null;
    }

    public void update(float translationX, float rawX, float rawY) {
        if (actions.size() < 2) return;
        if (Math.abs(translationX) < AndroidUtilities.dp(50)) {
            armed = false;
            scrubbing = false;
            return;
        }
        if (!armed) {
            armed = true;
            lastX = rawX;
            lastY = rawY;
            driftX = driftY = 0f;
            return;
        }
        float dx = Math.abs(rawX - lastX);
        float dy = rawY - lastY;
        lastX = rawX;
        lastY = rawY;
        if (!scrubbing) {
            driftX = driftX * .7f + dx * .3f;
            driftY = driftY * .7f + Math.abs(dy) * .3f;
            if (driftY < AndroidUtilities.dp(.5f) || driftY <= driftX * 1.5f) return;
            scrubbing = true;
            anchorY = rawY;
            return;
        }
        float distance = (rawY - anchorY) * (direction < 0 ? -1 : 1) * (reversed ? -1 : 1);
        float threshold = AndroidUtilities.dp(stepped ? 36 : 44);
        if (distance >= threshold) {
            anchorY = rawY;
            stepped = true;
            if (looped || selected < actions.size() - 1) move(1);
        } else if (distance <= -threshold) {
            anchorY = rawY;
            stepped = true;
            if (looped || selected > 0) move(-1);
        }
    }

    private int chooseDirection(float centerY) {
        float needed = AndroidUtilities.dp(44) + (actions.size() - 2) * AndroidUtilities.dp(36);
        float below = parent.getHeight() - centerY;
        return below >= needed || below >= centerY ? 1 : -1;
    }

    public void select(int index) {
        scrubbing = true;
        if (index == selected || index < 0 || index >= actions.size()) return;
        int delta = index - selected;
        if (looped) {
            delta = Math.floorMod(delta, actions.size());
            if (delta > actions.size() / 2) delta -= actions.size();
        }
        selected = index;
        slot += delta;
        positionSpring.getSpring().setFinalPosition(slot * SPRING_UNIT);
        positionSpring.start();
        parent.invalidate();
    }

    public boolean remove(DevGramSwipeAction action) {
        int index = actions.indexOf(action);
        if (scrubbing || actions.size() < 2 || index < 0
                || index == selected && visibility.getValue() != 0f) return false;
        actions.remove(index);
        if (selected >= actions.size()) {
            selected = actions.size() - 1;
            slot = selected;
            reset(positionSpring, position, selected * SPRING_UNIT);
        }
        parent.invalidate();
        return true;
    }

    private void move(int delta) {
        slot += delta;
        selected = Math.floorMod(slot, actions.size());
        try {
            parent.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Throwable ignore) {
        }
        positionSpring.getSpring().setFinalPosition(slot * SPRING_UNIT);
        positionSpring.start();
        parent.invalidate();
    }

    public void draw(Canvas canvas, float translationX, boolean outgoing, float centerY, float bubbleRight) {
        if (actions.isEmpty() || Thread.currentThread() != Looper.getMainLooper().getThread()) return;
        if (direction == 0) direction = chooseDirection(centerY);

        framePaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider);
        outlinePaint.setColor(framePaint.getColor());
        outlinePaint.setShader(framePaint.getShader());
        frameGradient = resourcesProvider != null && resourcesProvider.hasGradientService();
        frameDark = ColorUtils.calculateLuminance(
                Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) <= .5;

        float fillValue = fill.getValue() / SPRING_UNIT;
        if (fillValue > 1f) beyondMax = true;
        if (visibility.getValue() == 0f) {
            reset(fillSpring, fill, 0f);
            reset(ringSpring, ring, 0f);
            beyondMax = false;
        }
        boolean filled = fillSpring.getSpring().getFinalPosition() == SPRING_UNIT;
        float pull = filled ? 1f : MathUtils.clamp(
                (-translationX - AndroidUtilities.dp(20)) / AndroidUtilities.dp(30), 0f, 1f);
        if (pull == 1f && !filled) {
            fillSpring.getSpring().setFinalPosition(SPRING_UNIT);
            fillSpring.start();
            ringSpring.getSpring().setFinalPosition(SPRING_UNIT);
            ringSpring.start();
        }
        float visibilityTarget = translationX <= -AndroidUtilities.dp(20) ? SPRING_UNIT : 0f;
        if (visibilityTarget != visibilitySpring.getSpring().getFinalPosition()) {
            visibilitySpring.getSpring().setFinalPosition(visibilityTarget);
            if (!visibilitySpring.isRunning()) visibilitySpring.start();
        }

        float visible = visibility.getValue() / SPRING_UNIT;
        float parentWidth = parent.getMeasuredWidth();
        float x = Math.max(translationX * (outgoing ? .5f : 1f) + parentWidth,
                Math.min((bubbleRight + parentWidth) / 2f, parentWidth));
        float outer = beyondMax ? fillValue : visible;
        float inner = beyondMax ? 0f : 1f - fillValue;
        float positionValue = position.getValue() / SPRING_UNIT;
        float step = AndroidUtilities.dp(36) * direction;
        float neighborsAlpha = liveAlpha.set(scrubbing);
        int size = actions.size();
        int first = (int) Math.ceil(positionValue - 2f);
        int last = (int) Math.floor(positionValue + 2f);
        if (!looped || size < 2) {
            first = Math.max(first, 0);
            last = Math.min(last, size - 1);
        }
        for (int itemSlot = first; itemSlot <= last; itemSlot++) {
            if (itemSlot == slot) continue;
            float delta = itemSlot - positionValue;
            drawBubble(canvas, x, centerY + delta * step, outer, inner, visible, pull,
                    Math.abs(delta), neighborsAlpha, itemSlot, false);
        }
        drawBubble(canvas, x, centerY + (slot - positionValue) * step, outer, inner, visible,
                pull, Math.abs(slot - positionValue), 1f, slot, true);
    }

    private void drawBubble(Canvas canvas, float x, float y, float outer, float inner,
                            float visible, float pull, float distance, float live,
                            int itemSlot, boolean current) {
        if (distance >= 2f) return;
        float near = Math.max(0f, 1f - distance);
        float neighbor = Math.max(near, (2f - distance) * .45f);
        float pop = (near * .3f + .7f)
                * (CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(live) * .15f + .85f);
        float scale = Math.max(.01f, outer * pop);
        float alpha = visible * neighbor * live;
        if (alpha <= 0f) return;

        float radius = AndroidUtilities.dp(16) * scale;
        float innerRadius = AndroidUtilities.dp(16) * Math.max(0f, inner * pop);
        setRect(x, y, radius);
        applyServiceShaderMatrix();
        path.rewind();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        drawServicePath(canvas, alpha * .6f * pull);
        if (innerRadius > 0f) {
            setRect(x, y, innerRadius);
            path.rewind();
            path.addRoundRect(rect, innerRadius, innerRadius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path, Region.Op.DIFFERENCE);
        }
        setRect(x, y, radius);
        path.rewind();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        drawServicePath(canvas, alpha * .4f);
        if (innerRadius > 0f) canvas.restore();

        if (current && pull > 0f && fill.getValue() == 0f) {
            float arcRadius = Math.max(0f, radius - outlinePaint.getStrokeWidth() / 2f);
            setRect(x, y, arcRadius);
            applyServiceShaderMatrix();
            int oa = outlinePaint.getAlpha();
            outlinePaint.setAlpha((int) (oa * alpha));
            canvas.drawArc(rect, -90f, 360f * pull, false, outlinePaint);
            outlinePaint.setAlpha(oa);
        }

        float ringValue = ring.getValue() / SPRING_UNIT;
        if (current && ringValue > 0f && ringValue < 1f) {
            float ringRadius = radius * (1f + ringValue);
            float stroke = (1f - ringValue) * AndroidUtilities.dp(2);
            if (stroke > 0f) {
                setRect(x, y, ringRadius - stroke / 2f);
                int oa = outlinePaint.getAlpha();
                float oldStroke = outlinePaint.getStrokeWidth();
                outlinePaint.setAlpha((int) (oa * alpha));
                outlinePaint.setStrokeWidth(stroke);
                canvas.drawRoundRect(rect, ringRadius, ringRadius, outlinePaint);
                outlinePaint.setStrokeWidth(oldStroke);
                outlinePaint.setAlpha(oa);
            }
        }

        DevGramSwipeAction action = actions.get(Math.floorMod(itemSlot, actions.size()));
        drawAction(canvas, action, itemSlot, x, y, scale, alpha);
    }

    private void drawServicePath(Canvas canvas, float alpha) {
        if (alpha <= 0f) return;
        int frameAlpha = framePaint.getAlpha();
        framePaint.setAlpha((int) (frameAlpha * alpha));
        canvas.drawPath(path, framePaint);
        framePaint.setAlpha(frameAlpha);
        if (frameGradient) {
            Paint darken = Theme.chat_actionBackgroundGradientDarkenPaint;
            int darkAlpha = darken.getAlpha();
            int darkColor = darken.getColor();
            if (frameDark) darken.setColor(0xffffffff);
            darken.setAlpha((int) (darkAlpha * alpha));
            canvas.drawPath(path, darken);
            darken.setAlpha(darkAlpha);
            darken.setColor(darkColor);
        }
    }

    private void drawAction(Canvas canvas, DevGramSwipeAction action, int itemSlot,
                            float x, float y, float scale, float alpha) {
        if (action == DevGramSwipeAction.REACTION && reactionImage != null) {
            float half = AndroidUtilities.dp(10) * scale;
            reactionImage.setImageCoords(x - half, y - half, half * 2, half * 2);
            reactionImage.setAlpha(alpha);
            reactionImage.draw(canvas);
            return;
        }
        if (action == DevGramSwipeAction.REACTION && reactionDrawable != null) {
            float half = AndroidUtilities.dp(10) * scale;
            reactionDrawable.setAlpha((int) (255 * alpha));
            reactionDrawable.setBounds((int) (x - half), (int) (y - half),
                    (int) (x + half), (int) (y + half));
            reactionDrawable.draw(canvas);
            reactionDrawable.setAlpha(255);
            return;
        }

        boolean alternateReply = actions.size() != 1 && action == DevGramSwipeAction.REPLY;
        Drawable icon = actions.size() == 1 && action == DevGramSwipeAction.REPLY
                ? Theme.getThemeDrawable(Theme.key_drawable_replyIcon, resourcesProvider)
                : getIcon(action, alternateReply && (Math.floorDiv(itemSlot, actions.size()) & 1) != 0);
        if (icon == null) return;
        Float fit = iconFits.get(action.iconRes);
        float iconScale = scale * action.iconTrim * (fit == null ? 1f : fit);
        float halfWidth = icon.getIntrinsicWidth() > 0
                ? icon.getIntrinsicWidth() * .5f * iconScale : AndroidUtilities.dp(9) * iconScale;
        float halfHeight = icon.getIntrinsicHeight() > 0
                ? icon.getIntrinsicHeight() * .5f * iconScale : AndroidUtilities.dp(9) * iconScale;
        icon.setColorFilter(getIconFilter());
        icon.setAlpha((int) (255 * alpha));
        icon.setBounds((int) (x - halfWidth), (int) (y - halfHeight),
                (int) (x + halfWidth), (int) (y + halfHeight));
        icon.draw(canvas);
        icon.setAlpha(255);
    }

    private void setRect(float x, float y, float radius) {
        rect.set(x - radius, y - radius, x + radius, y + radius);
    }

    private void applyServiceShaderMatrix() {
        float y = parent.getY() + rect.top;
        if (resourcesProvider != null) {
            resourcesProvider.applyServiceShaderMatrix(parent.getMeasuredWidth(), AndroidUtilities.displaySize.y, 0f, y);
        } else {
            Theme.applyServiceShaderMatrix(parent.getMeasuredWidth(), AndroidUtilities.displaySize.y, 0f, y);
        }
    }

    private ColorFilter getIconFilter() {
        int color = Theme.getColor(Theme.key_chat_serviceIcon, resourcesProvider);
        if (iconFilter == null || iconFilterColor != color) {
            iconFilterColor = color;
            iconFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
        }
        return iconFilter;
    }

    private float measureIconFit(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) return 1f;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(new Canvas(bitmap));
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        bitmap.recycle();
        int left = width, top = height, right = -1, bottom = -1;
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                if ((pixels[py * width + px] >>> 24) >= 8) {
                    left = Math.min(left, px);
                    right = Math.max(right, px);
                    top = Math.min(top, py);
                    bottom = Math.max(bottom, py);
                }
            }
        }
        if (right < left || bottom < top) return 1f;
        return Math.min(AndroidUtilities.dpf2(15) / (right - left + 1),
                AndroidUtilities.dpf2(16.5f) / (bottom - top + 1));
    }

    private Drawable getIcon(DevGramSwipeAction action, boolean spare) {
        SparseArray<Drawable> cache = spare ? spareIcons : icons;
        Drawable drawable = cache.get(action.iconRes);
        if (drawable != null) return drawable;
        Drawable source = parent.getContext().getDrawable(action.iconRes);
        if (source == null) return null;
        if (iconFits.get(action.iconRes) == null) {
            iconFits.put(action.iconRes, measureIconFit(source));
        }
        drawable = source.mutate();
        cache.put(action.iconRes, drawable);
        return drawable;
    }
}

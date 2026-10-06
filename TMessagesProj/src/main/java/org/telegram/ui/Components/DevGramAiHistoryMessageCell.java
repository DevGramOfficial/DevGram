package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;

/** Compact expandable history row matching exteraGram's AI history layout. */
@SuppressLint("ViewConstructor")
public class DevGramAiHistoryMessageCell extends FrameLayout {
    private final Theme.ResourcesProvider resourcesProvider;
    private final TextView titleView;
    private final TextView textView;
    private boolean needDivider;

    public DevGramAiHistoryMessageCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 22, 10, 22, 11));

        titleView = new TextView(context);
        titleView.setTextSize(14);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.LEFT);
        content.addView(titleView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        textView.setTextSize(15);
        textView.setGravity(Gravity.LEFT);
        content.addView(textView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            canvas.drawLine(AndroidUtilities.dp(22), getMeasuredHeight() - 1,
                    getMeasuredWidth() - AndroidUtilities.dp(22), getMeasuredHeight() - 1,
                    Theme.dividerPaint);
        }
    }

    private void set(JSONObject message, CharSequence content, boolean expanded, boolean divider) {
        needDivider = divider;
        textView.setMaxLines(expanded ? Integer.MAX_VALUE : 6);
        textView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
        boolean assistant = "assistant".equals(message.optString("role"));
        titleView.setText(assistant ? "ИИ-ассистент" : "Вы");
        titleView.setTextColor(Theme.getColor(assistant
                ? Theme.key_windowBackgroundWhiteGrayText2
                : Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        CharSequence safe = content == null ? "" : content;
        textView.setText(Emoji.replaceEmoji(safe, textView.getPaint().getFontMetricsInt(), false));
        invalidate();
    }

    public static final class Factory extends UItem.UItemFactory<DevGramAiHistoryMessageCell> {
        static {
            UItem.UItemFactory.setup(new Factory());
        }

        @Override
        public DevGramAiHistoryMessageCell createView(Context context, RecyclerListView listView,
                int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new DevGramAiHistoryMessageCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                UniversalRecyclerView listView) {
            if (view instanceof DevGramAiHistoryMessageCell && item.object instanceof JSONObject) {
                ((DevGramAiHistoryMessageCell) view).set(
                        (JSONObject) item.object, item.text, item.checked, divider);
            }
        }

        @Override
        public boolean equals(UItem first, UItem second) {
            return first.id == second.id;
        }

        @Override
        public boolean contentsEquals(UItem first, UItem second) {
            return first.checked == second.checked
                    && TextUtils.equals(first.text, second.text)
                    && role(first).equals(role(second));
        }

        private static String role(UItem item) {
            return item.object instanceof JSONObject
                    ? ((JSONObject) item.object).optString("role") : "";
        }

        public static UItem asHistoryCell(int id, JSONObject message, CharSequence content, boolean expanded) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.object = message;
            item.text = content;
            item.checked = expanded;
            return item;
        }
    }
}

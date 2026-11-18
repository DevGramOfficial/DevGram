package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramDisguise;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LauncherIconController.LauncherIcon;

import java.util.ArrayList;
import java.util.List;

// DevGram: красивый каталог маскировок — сетка карточек с превью иконки (реальной с
// телефона, если приложение-аналог установлено) и названием. Заменяет старый список-диалог.
public class DevGramDisguiseSheet extends BottomSheet {

    public interface OnChosen {
        void run();
    }

    private final List<LauncherIcon> masks;
    private final OnChosen onChosen;

    public DevGramDisguiseSheet(Context context, OnChosen onChosen) {
        super(context, false);
        this.onChosen = onChosen;
        this.masks = DevGramDisguise.masks();

        FrameLayout container = new FrameLayout(context);

        TextView title = new TextView(context);
        title.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(DevGramLocale.getString(R.string.DevGramDisguise));
        title.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(18), AndroidUtilities.dp(22), AndroidUtilities.dp(2));
        container.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        TextView subtitle = new TextView(context);
        subtitle.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setText(DevGramLocale.text("Как приложение выглядит в списке приложений"));
        subtitle.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(44), AndroidUtilities.dp(22), AndroidUtilities.dp(8));
        container.addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        RecyclerListView listView = new RecyclerListView(context);
        GridLayoutManager layoutManager = new GridLayoutManager(context, 3);
        listView.setLayoutManager(layoutManager);
        listView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(74), AndroidUtilities.dp(10), AndroidUtilities.dp(16));
        listView.setClipToPadding(false);
        listView.setAdapter(new Adapter());
        listView.setOnItemClickListener((view, position) -> {
            if (position == 0) {
                DevGramDisguise.clear();
            } else {
                DevGramDisguise.apply(masks.get(position - 1));
            }
            if (onChosen != null) onChosen.run();
            dismiss();
        });
        container.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 420, Gravity.TOP));

        setCustomView(container);
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MaskCard(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            MaskCard card = (MaskCard) holder.itemView;
            if (position == 0) {
                // «Без маскировки» — обычный DevGram.
                card.bind(getContext().getResources().getDrawable(R.mipmap.icon_01_launcher),
                        DevGramLocale.getString(R.string.DevGramDisguiseNone), !DevGramDisguise.isDisguised());
            } else {
                LauncherIcon mask = masks.get(position - 1);
                Drawable icon = DevGramDisguise.realIcon(getContext(), mask);
                if (icon == null) {
                    icon = getContext().getResources().getDrawable(DevGramDisguise.previewRes(mask));
                }
                CharSequence label = DevGramDisguise.realLabel(getContext(), mask);
                card.bind(icon, label, DevGramDisguise.current() == mask);
            }
        }

        @Override
        public int getItemCount() {
            return masks.size() + 1;
        }
    }

    // Карточка: крупная иконка + подпись + обводка-галочка при выборе.
    private class MaskCard extends FrameLayout {
        private final ImageView iconView;
        private final TextView labelView;
        private boolean selected;
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        MaskCard(Context context) {
            super(context);
            setWillNotDraw(false);
            setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(iconView, LayoutHelper.createFrame(58, 58, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));

            labelView = new TextView(context);
            labelView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            labelView.setGravity(Gravity.CENTER);
            labelView.setMaxLines(1);
            labelView.setEllipsize(TextUtils.TruncateAt.END);
            addView(labelView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 4, 68, 4, 0));

            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));

            setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(104)));
        }

        void bind(Drawable icon, CharSequence label, boolean selected) {
            iconView.setImageDrawable(icon);
            labelView.setText(label);
            this.selected = selected;
            labelView.setTypeface(selected ? AndroidUtilities.bold() : null);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (selected) {
                int cx = getWidth() / 2;
                int cy = AndroidUtilities.dp(4 + 29 + 10);
                int r = AndroidUtilities.dp(33);
                ringPaint.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
                canvas.drawCircle(cx, cy, r, ringPaint);
            }
        }
    }
}

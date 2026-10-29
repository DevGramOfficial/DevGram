package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

// DevGram (команда): список значков, которые поддержавшие могут выбрать себе сами.
// Добавление — через тот же пикер кастом-эмодзи, что и при выдаче значка.
public class DevGramBadgeOptionsActivity extends org.telegram.ui.ActionBar.BaseFragment {

    private static final int ID_ADD = 1;
    private static final int ID_ROW_BASE = 1000;

    private UniversalRecyclerView listView;
    private ArrayList<DevGramBadges.Option> options;
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow pickerPopup;
    private View addAnchor;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Значки для поддержавших");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addAnchor = new View(context);
        root.addView(addAnchor, LayoutHelper.createFrame(1, 1, Gravity.TOP | Gravity.LEFT, 0, 40, 0, 0));
        reload();
        return fragmentView = root;
    }

    private void reload() {
        DevGramBadges.fetchOptions(list -> {
            options = list;
            // ответ может прийти, когда экран уже закрыт (контекста нет)
            if (getContext() != null && listView != null && listView.adapter != null) listView.adapter.update(true);
        });
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_ADD, R.drawable.msg_add, "Добавить значок"));
        items.add(UItem.asShadow("Поддержавшие увидят эти значки в разделе «Поддержавшим» и смогут выбрать любой. Выданный командой значок остаётся у них первым вариантом."));
        if (options == null) {
            items.add(UItem.asShadow("Загружаю…"));
            return;
        }
        if (options.isEmpty()) {
            items.add(UItem.asShadow("Список пока пуст."));
            return;
        }
        items.add(UItem.asHeader("На выбор (" + options.size() + ")"));
        for (int i = 0; i < options.size(); i++) {
            DevGramBadges.Option o = options.get(i);
            items.add(UItem.asCustom(ID_ROW_BASE + i, new OptionRow(getContext(), o)));
        }
        items.add(UItem.asShadow("Нажмите на значок, чтобы убрать его из списка."));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            openEmojiPicker(view);
        } else if (item.id >= ID_ROW_BASE && options != null) {
            int idx = item.id - ID_ROW_BASE;
            if (idx >= 0 && idx < options.size()) confirmRemove(options.get(idx));
        }
    }

    private void openEmojiPicker(View anchor) {
        if (pickerPopup != null) return;
        SelectAnimatedEmojiDialog layout = new SelectAnimatedEmojiDialog(
                this, getContext(), false, null, SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, resourceProvider) {
            @Override
            protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                if (pickerPopup != null) {
                    pickerPopup.dismiss();
                    pickerPopup = null;
                }
                if (documentId != null && documentId != 0) askTitle(documentId);
            }
        };
        pickerPopup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                pickerPopup = null;
            }
        };
        pickerPopup.showAsDropDown(anchor != null ? anchor : addAnchor, AndroidUtilities.dp(16), AndroidUtilities.dp(4), Gravity.TOP | Gravity.LEFT);
        pickerPopup.dimBehind();
    }

    private void askTitle(long emojiId) {
        Context context = getParentActivity();
        if (context == null) return;
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setHint("Название (необязательно)");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        input.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        input.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        input.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8));
        FrameLayout wrap = new FrameLayout(context);
        wrap.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 20, 4, 20, 0));
        AlertDialog.Builder b = new AlertDialog.Builder(context, resourceProvider);
        b.setTitle("Новый значок");
        b.setView(wrap);
        b.setPositiveButton("Добавить", (d, w) -> DevGramBadges.putOption(emojiId, input.getText().toString().trim(), (ok, error) -> {
            if (ok) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Значок добавлен").show();
                reload();
            } else {
                BulletinFactory.of(this).createErrorBulletin(error != null ? error : "Не удалось добавить").show();
            }
        }));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void confirmRemove(DevGramBadges.Option o) {
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        b.setTitle("Убрать значок?");
        b.setMessage("У тех, кто его уже выбрал, он останется, но выбрать его снова будет нельзя.");
        b.setPositiveButton("Убрать", (d, w) -> DevGramBadges.removeOption(o.emojiId, (ok, error) -> {
            if (ok) reload();
            else BulletinFactory.of(this).createErrorBulletin(error != null ? error : "Не удалось убрать").show();
        }));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button != null) button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourceProvider));
    }

    // строка: анимированный значок + название + id
    private class OptionRow extends LinearLayout {
        private final AnimatedEmojiDrawable drawable;
        private final View icon;

        OptionRow(Context context, DevGramBadges.Option o) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(8), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
            drawable = DevGramBadges.tinted(AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, o.emojiId), Theme.getColor(Theme.key_chats_verifiedBackground, resourceProvider));
            icon = new View(context) {
                @Override
                protected void onDraw(android.graphics.Canvas canvas) {
                    drawable.setBounds(0, 0, getWidth(), getHeight());
                    drawable.draw(canvas);
                }
            };
            addView(icon, LayoutHelper.createLinear(32, 32));
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            TextView title = new TextView(context);
            title.setText(o.title.isEmpty() ? "Без названия" : o.title);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            texts.addView(title);
            TextView id = new TextView(context);
            id.setText("ID " + o.emojiId);
            id.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            id.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            texts.addView(id);
            addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 16, 0, 0, 0));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            drawable.addView(icon);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            drawable.removeView(icon);
        }
    }
}

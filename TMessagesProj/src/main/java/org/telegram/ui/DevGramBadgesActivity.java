/*
 * DevGram: экран выдачи значков (доступен команде проекта).
 * Флоу: ввод ID -> выбор значка (готовый ИЛИ любой эмодзи/премиум-эмодзи через тот же пикер,
 * что в чате) -> выбор подписи (готовая ИЛИ своя) -> выдать. Значок = {эмодзи, подпись} и
 * хранится в облаке (Firebase RTDB), появляется у всех сразу, пересборка не нужна.
 */

package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class DevGramBadgesActivity extends BaseFragment {

    private static final int ID_LIST_BASE = 1000; // выданные значки: ID_LIST_BASE + индекс

    private UniversalRecyclerView listView;
    private EditTextBoldCursor recipientInput;
    private ArrayList<Long> granted = new ArrayList<>(); // dialogId'ы
    private String query = ""; // фильтр поиска (по ID и юзернейму/имени)

    @Override
    public View createView(Context context) {
        DevGramBadges.syncFromCloud(); // подтянуть свежий список из облака при открытии
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Значки DevGram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        root.addView(createGrantCard(context), LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 12, 12, 6));

        // Поиск по выданным значкам (по ID и юзернейму/имени)
        android.widget.FrameLayout searchWrap = new android.widget.FrameLayout(context);
        searchWrap.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(21),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        android.widget.ImageView searchIcon = new android.widget.ImageView(context);
        searchIcon.setImageResource(R.drawable.msg_search);
        searchIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        searchWrap.addView(searchIcon, LayoutHelper.createFrame(20, 20, Gravity.CENTER_VERTICAL | Gravity.LEFT, 14, 0, 0, 0));
        EditTextBoldCursor search = new EditTextBoldCursor(context);
        search.setHint("Поиск по ID или @юзернейму");
        search.setSingleLine(true);
        search.setBackground(null);
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        search.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        search.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        search.setCursorSize(AndroidUtilities.dp(20));
        search.setCursorWidth(1.5f);
        search.setPadding(AndroidUtilities.dp(44), 0, AndroidUtilities.dp(14), 0);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                query = s.toString().trim().toLowerCase();
                update();
            }
        });
        searchWrap.addView(search, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42));
        root.addView(searchWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 12, 4, 12, 6));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        root.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = root;
    }

    private View createGrantCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));

        TextView title = new TextView(context);
        title.setText("Выдать новый значок");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        card.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subtitle = new TextView(context);
        subtitle.setText("Введите ID пользователя или отрицательный ID чата");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        card.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        recipientInput = makeInput(context, "Например, 7101191373 или -100…",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        recipientInput.setSingleLine(true);
        recipientInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        recipientInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                openGrantFromInput();
                return true;
            }
            return false;
        });
        inputRow.addView(recipientInput, LayoutHelper.createLinear(0, 48, 1f));

        TextView next = new TextView(context);
        next.setText("Далее");
        next.setGravity(Gravity.CENTER);
        next.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        next.setTypeface(AndroidUtilities.bold());
        next.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        next.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        next.setOnClickListener(v -> openGrantFromInput());
        inputRow.addView(next, LayoutHelper.createLinear(88, 44, 12, 0, 0, 0));
        card.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 0));
        return card;
    }

    private void openGrantFromInput() {
        if (recipientInput == null) return;
        // Канал в bot-API формате (-100…) приводим к внутреннему dialogId, иначе значок «не появится».
        long id = DevGramBadges.normalizeDialogId(Utilities.parseLong(recipientInput.getText().toString()));
        if (id == 0) {
            recipientInput.animate().translationX(AndroidUtilities.dp(5)).setDuration(70).withEndAction(() ->
                    recipientInput.animate().translationX(0).setDuration(70).start()).start();
            return;
        }
        AndroidUtilities.hideKeyboard(recipientInput);
        presentFragment(new DevGramBadgeGrantActivity(id));
    }

    // Имя + @юзернейм по dialogId (для отображения и поиска). "" если не резолвится.
    private String nameFor(long dialogId) {
        try {
            if (dialogId > 0) {
                org.telegram.tgnet.TLRPC.User u = getMessagesController().getUser(dialogId);
                if (u != null) {
                    String nm = org.telegram.messenger.ContactsController.formatName(u.first_name, u.last_name);
                    String un = org.telegram.messenger.UserObject.getPublicUsername(u);
                    return (nm != null ? nm : "") + (un != null && !un.isEmpty() ? " @" + un : "");
                }
            } else {
                org.telegram.tgnet.TLRPC.Chat c = getMessagesController().getChat(-dialogId);
                if (c != null) {
                    String un = org.telegram.messenger.ChatObject.getPublicUsername(c);
                    return (c.title != null ? c.title : "") + (un != null && !un.isEmpty() ? " @" + un : "");
                }
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    private static long shownId(long dialogId) {
        return dialogId < 0 ? -dialogId : dialogId;
    }

    private static String shortText(String t) {
        if (t == null) return "";
        t = t.replace("{name}", "").trim();
        return t.length() > 34 ? t.substring(0, 34) + "…" : t;
    }

    private static String emojiLabel(long emojiId) {
        for (int i = 0; i < DevGramBadges.READY_EMOJI.length; i++) {
            if (DevGramBadges.READY_EMOJI[i] == emojiId) {
                return DevGramBadges.READY_EMOJI_LABELS[i];
            }
        }
        return "Свой эмодзи";
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        granted = DevGramBadges.listGranted();
        if (!granted.isEmpty()) {
            items.add(UItem.asHeader("Выдано вручную (" + granted.size() + ")"));
            int shown = 0;
            for (int i = 0; i < granted.size(); i++) {
                long dialogId = granted.get(i);
                String name = nameFor(dialogId);
                // фильтр: по ID и по имени/юзернейму
                if (!query.isEmpty()
                        && !String.valueOf(shownId(dialogId)).contains(query)
                        && !name.toLowerCase().contains(query)) {
                    continue;
                }
                DevGramBadges.Badge b = DevGramBadges.badgeOf(dialogId);
                String title = name.isEmpty() ? ("ID " + shownId(dialogId)) : name;
                String value = b == null ? ("ID " + shownId(dialogId))
                        : emojiLabel(b.emojiId) + "  ·  ID " + shownId(dialogId);
                items.add(UItem.asButton(ID_LIST_BASE + i, title, value));
                shown++;
            }
            if (shown == 0) {
                items.add(UItem.asShadow("Ничего не найдено."));
            } else {
                items.add(UItem.asShadow("Нажмите на строку, чтобы изменить или снять значок."));
            }
        } else if (query.isEmpty()) {
            items.add(UItem.asShadow("Выданных значков пока нет."));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_LIST_BASE) {
            int idx = item.id - ID_LIST_BASE;
            if (idx >= 0 && idx < granted.size()) {
                showBadgeActions(granted.get(idx));
            }
        }
    }

    private void showBadgeActions(long dialogId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        String name = nameFor(dialogId);
        builder.setTitle(name.isEmpty() ? "ID " + shownId(dialogId) : name);
        builder.setItems(new CharSequence[]{"Изменить значок", "Снять значок"}, (dialog, which) -> {
            if (which == 0) {
                presentFragment(new DevGramBadgeGrantActivity(dialogId));
            } else {
                showRevokeDialog(dialogId);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    // Ввод ID -> открываем ОТДЕЛЬНЫЙ экран выдачи (не диалог, иначе пикер эмодзи его закроет).
    private void askId() {
        Context context = getParentActivity();
        EditTextBoldCursor editText = makeInput(context, "ID пользователя (или -ID чата)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Кому выдать значок?");
        builder.setView(container);
        builder.setPositiveButton("Далее", (d, w) -> {
            long id = Utilities.parseLong(editText.getText().toString());
            if (id != 0) {
                presentFragment(new DevGramBadgeGrantActivity(id));
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
    }

    // Обновляем список при возврате с экрана выдачи.
    @Override
    public void onResume() {
        super.onResume();
        DevGramBadges.syncFromCloud();
        update();
    }

    private void showRevokeDialog(long dialogId) {
        DevGramBadges.Badge b = DevGramBadges.badgeOf(dialogId);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Снять значок");
        builder.setMessage("Убрать значок у ID " + shownId(dialogId) + "?"
                + (b != null ? "\n\n«" + shortText(b.text) + "»" : ""));
        builder.setPositiveButton("Снять", (d, w) -> ensureAdminSignedIn(() -> {
            DevGramBadges.revoke(dialogId);
            update();
        }));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    // Писать значки в облако можно только после входа команды (Firebase Auth REST).
    private void ensureAdminSignedIn(Runnable onReady) {
        if (DevGramBadges.isSignedIn()) {
            onReady.run();
            return;
        }
        Context context = getParentActivity();
        EditTextBoldCursor emailEt = makeInput(context, "Email команды", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
        EditTextBoldCursor passEt = makeInput(context, "Пароль", InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        passEt.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());

        LinearLayout boxx = new LinearLayout(context);
        boxx.setOrientation(LinearLayout.VERTICAL);
        boxx.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        boxx.addView(emailEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        boxx.addView(passEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 12f, 0f, 0f));

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Вход команды DevGram");
        b.setView(boxx);
        b.setPositiveButton("Войти", (d, w) -> {
            String email = emailEt.getText().toString().trim();
            String pass = passEt.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) {
                return;
            }
            DevGramBadges.signIn(email, pass, (ok, err) -> {
                if (ok) {
                    onReady.run();
                } else {
                    Toast.makeText(getParentActivity(), "Не удалось войти: " + err, Toast.LENGTH_LONG).show();
                }
            });
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        emailEt.requestFocus();
        AndroidUtilities.showKeyboard(emailEt);
    }

    private EditTextBoldCursor makeInput(Context context, String hint, int inputType) {
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        et.setInputType(inputType);
        et.setHint(hint);
        // видимый hint управляется setHintTextColor (setHintColor — анимированный, не тот)
        et.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        et.setCursorSize(AndroidUtilities.dp(20));
        et.setCursorWidth(1.5f);
        et.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        return et;
    }

    private void update() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        org.telegram.messenger.NotificationCenter.getGlobalInstance()
                .postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
    }
}

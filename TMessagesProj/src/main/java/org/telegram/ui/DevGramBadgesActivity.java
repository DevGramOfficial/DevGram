/*
 * DevGram: экран значков для команды. Сверху сразу выдача: ввёл ID / @юзернейм / ссылку (или
 * выбрал из чатов) -> открывается экран выдачи. Список выданных — отдельный экран с поиском и
 * фильтрами. Значок хранится в облаке и появляется у всех сразу, пересборка не нужна.
 */

package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramPackages;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DevGramBadgesActivity extends BaseFragment {

    private static final int ID_LIST = 1;
    private static final int ID_OPTIONS = 2;
    private static final int ID_ACCOUNT = 3;
    private static final int ID_SIGN_IN = 4;
    private static final int ID_SIGN_OUT = 5;

    private static final Pattern USERNAME = Pattern.compile("^(?:https?://)?(?:t\\.me/|telegram\\.me/|@)?([A-Za-z][A-Za-z0-9_]{3,31})(?:[/?].*)?$");

    private UniversalRecyclerView listView;
    private View grantCard;
    private EditTextBoldCursor recipientInput;
    private TextView nextButton;
    private boolean resolving;

    @Override
    public View createView(Context context) {
        DevGramBadges.syncFromCloud();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Значки DevGram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        grantCard = createGrantCard(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = listView;
    }

    @Override
    public void onResume() {
        super.onResume();
        DevGramBadges.syncFromCloud();
        update();
    }

    private View createGrantCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        card.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));

        TextView title = new TextView(context);
        title.setText("Выдать значок");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        card.addView(title);

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        recipientInput = makeInput(this, context, "ID, @юзернейм или ссылка", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        recipientInput.setSingleLine(true);
        recipientInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        recipientInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openGrantFromInput();
                return true;
            }
            return false;
        });
        inputRow.addView(recipientInput, LayoutHelper.createLinear(0, 48, 1f));

        nextButton = new TextView(context);
        nextButton.setText("Далее");
        nextButton.setGravity(Gravity.CENTER);
        nextButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        nextButton.setTypeface(AndroidUtilities.bold());
        nextButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        nextButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        nextButton.setOnClickListener(v -> openGrantFromInput());
        inputRow.addView(nextButton, LayoutHelper.createLinear(88, 44, 12, 0, 0, 0));
        card.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 8, 0, 0));

        TextView fromChats = new TextView(context);
        fromChats.setText("Выбрать из чатов");
        fromChats.setGravity(Gravity.CENTER);
        fromChats.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        fromChats.setTypeface(AndroidUtilities.bold());
        fromChats.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
        fromChats.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        android.graphics.drawable.Drawable icon = context.getResources().getDrawable(R.drawable.msg_contacts).mutate();
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider), android.graphics.PorterDuff.Mode.SRC_IN));
        icon.setBounds(0, 0, AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        fromChats.setCompoundDrawables(icon, null, null, null);
        fromChats.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider), 0.10f),
                Theme.getColor(Theme.key_listSelector, resourceProvider)));
        fromChats.setOnClickListener(v -> pickFromChats());
        card.addView(fromChats, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 12, 0, 0));
        return card;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(grantCard));
        items.add(UItem.asShadow("Можно ввести ID пользователя, ID канала (в том числе -100…), @юзернейм или ссылку t.me/…"));

        items.add(UItem.asHeader("Управление"));
        int count = DevGramBadges.listGranted().size();
        items.add(UItem.asButton(ID_LIST, R.drawable.msg_list, "Выданные значки", count > 0 ? String.valueOf(count) : ""));
        items.add(UItem.asButton(ID_OPTIONS, R.drawable.msg_input_like, "Значки для поддержавших"));
        items.add(UItem.asShadow("Поддержавшие выбирают себе значок из этого списка в разделе «Поддержавшим»."));

        items.add(UItem.asHeader("Аккаунт команды"));
        if (DevGramBadges.isSignedIn()) {
            String email = DevGramBadges.getAdminEmail();
            items.add(UItem.asButton(ID_ACCOUNT, R.drawable.msg_admins, "Вы вошли", email == null ? "" : email));
            items.add(UItem.asButton(ID_SIGN_OUT, R.drawable.msg_leave, "Выйти").red());
            items.add(UItem.asShadow("Сессия сохраняется после перезапуска — входить заново не нужно."));
        } else {
            items.add(UItem.asButton(ID_SIGN_IN, R.drawable.msg_admins, "Войти"));
            items.add(UItem.asShadow("Вход нужен, чтобы выдавать и снимать значки."));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_LIST) {
            presentFragment(new DevGramBadgeListActivity());
        } else if (item.id == ID_OPTIONS) {
            ensureAdminSignedIn(this, () -> presentFragment(new DevGramBadgeOptionsActivity()));
        } else if (item.id == ID_SIGN_IN) {
            ensureAdminSignedIn(this, this::update);
        } else if (item.id == ID_SIGN_OUT) {
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), resourceProvider);
            b.setTitle("Выйти из аккаунта команды?");
            b.setMessage("Чтобы снова выдавать значки, понадобится войти по почте и паролю.");
            b.setPositiveButton("Выйти", (d, w) -> {
                DevGramBadges.signOut();
                update();
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            AlertDialog dialog = b.create();
            showDialog(dialog);
            TextView button = (TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            if (button != null) button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourceProvider));
        }
    }

    private void pickFromChats() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("allowSwitchAccount", false);
        args.putBoolean("checkCanWrite", false); // каналы, куда нельзя писать, тоже выбираются
        DialogsActivity fragment = new DialogsActivity(args) {
            @Override
            public View createView(Context context) {
                View view = super.createView(context);
                actionBar.setTitle("Кому выдать значок");
                return view;
            }
        };
        fragment.setDelegate((f, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty() || dids.get(0).dialogId == 0) return false;
            // выдача заменяет список чатов (закрыть и сразу открыть нельзя — переход отбрасывается)
            f.presentFragment(new DevGramBadgeGrantActivity(dids.get(0).dialogId), true);
            return true;
        });
        presentFragment(fragment);
    }

    // ID сразу открывает выдачу, юзернейм/ссылку сначала резолвим
    private void openGrantFromInput() {
        if (recipientInput == null || resolving) return;
        String raw = recipientInput.getText().toString().trim();
        if (raw.matches("^-?\\d+$")) {
            long id = DevGramBadges.normalizeDialogId(Utilities.parseLong(raw));
            if (id == 0) {
                shake();
                return;
            }
            openGrant(id);
            return;
        }
        Matcher m = USERNAME.matcher(raw);
        if (!m.matches()) {
            shake();
            return;
        }
        setResolving(true);
        DevGramPackages.resolveUsernameThen(currentAccount, m.group(1), (user, chat) -> {
            setResolving(false);
            if (user != null) {
                openGrant(user.id);
            } else if (chat != null) {
                openGrant(-chat.id);
            } else {
                shake();
                Toast.makeText(getParentActivity(), "Не нашёл @" + m.group(1), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openGrant(long dialogId) {
        AndroidUtilities.hideKeyboard(recipientInput);
        recipientInput.setText("");
        presentFragment(new DevGramBadgeGrantActivity(dialogId));
    }

    private void setResolving(boolean value) {
        resolving = value;
        if (nextButton != null) {
            nextButton.setText(value ? "…" : "Далее");
            nextButton.setEnabled(!value);
        }
    }

    private void shake() {
        AndroidUtilities.shakeViewSpring(recipientInput, -6);
    }

    private void update() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    // Писать значки в облако можно только после входа команды (сессия сохраняется в DevGramBadges).
    static void ensureAdminSignedIn(BaseFragment fragment, Runnable onReady) {
        if (DevGramBadges.isSignedIn()) {
            onReady.run();
            return;
        }
        Context context = fragment.getParentActivity();
        if (context == null) return;
        EditTextBoldCursor emailEt = makeInput(fragment, context, "Почта команды", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
        EditTextBoldCursor passEt = makeInput(fragment, context, "Пароль", InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        passEt.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        box.addView(emailEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        box.addView(passEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 12f, 0f, 0f));

        AlertDialog.Builder b = new AlertDialog.Builder(context, fragment.getResourceProvider());
        b.setTitle("Вход команды DevGram");
        b.setView(box);
        b.setPositiveButton("Войти", (d, w) -> {
            String email = emailEt.getText().toString().trim();
            String pass = passEt.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) return;
            DevGramBadges.signIn(email, pass, (ok, err) -> {
                if (ok) {
                    onReady.run();
                } else if (fragment.getParentActivity() != null) {
                    Toast.makeText(fragment.getParentActivity(), "Не удалось войти: " + err, Toast.LENGTH_LONG).show();
                }
            });
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
        emailEt.requestFocus();
        AndroidUtilities.showKeyboard(emailEt);
    }

    static EditTextBoldCursor makeInput(BaseFragment fragment, Context context, String hint, int inputType) {
        Theme.ResourcesProvider rp = fragment.getResourceProvider();
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        et.setInputType(inputType);
        et.setHint(hint);
        et.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, rp));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        et.setCursorSize(AndroidUtilities.dp(20));
        et.setCursorWidth(1.5f);
        et.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        return et;
    }
}

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramAiClient;
import org.telegram.messenger.DevGramGeminiNano;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.DevGramAiRolesActivity;
import org.telegram.ui.DevGramAiServicesActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

/** Large AI response sheet closely following exteraGram's refreshed message generation UI. */
public class DevGramAiResponseAlert extends BottomSheet {
    private final BaseFragment fragment;
    private final Utilities.Callback<String> onInsert;
    private final TextView promptView;
    private final TextView responseView;
    private final TextView roleView;
    private final TextView modelView;
    private final ImageView insertView;
    private final ImageView optionsView;
    private final ButtonWithCounterView mainButton;
    private final TypingDotsDrawable typingDots;
    private String prompt;
    private boolean useHistory;
    private String response;
    private int generation;
    private boolean generating;
    private boolean failed;

    public DevGramAiResponseAlert(Context context, BaseFragment fragment, String prompt,
            boolean useHistory, Utilities.Callback<String> onInsert) {
        super(context, true, fragment.getResourceProvider());
        this.fragment = fragment;
        this.prompt = prompt;
        this.useHistory = useHistory;
        this.onInsert = onInsert;
        fixNavigationBar();
        smoothKeyboardAnimationEnabled = true;
        setCanDismissWithSwipe(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setMinimumHeight(Math.min(AndroidUtilities.dp(680),
                (int) (AndroidUtilities.displaySize.y * 0.76f)));
        root.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));

        FrameLayout header = new FrameLayout(context);
        header.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 78));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        titles.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        header.addView(titles, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.LEFT | Gravity.TOP, 0, 0, 92, 0));

        roleView = new TextView(context);
        roleView.setText(currentRole() + "  ▾");
        roleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        roleView.setTypeface(AndroidUtilities.bold());
        roleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        roleView.setSingleLine(true);
        roleView.setEllipsize(TextUtils.TruncateAt.END);
        roleView.setOnClickListener(this::showRoles);
        titles.addView(roleView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 32));

        modelView = new TextView(context);
        modelView.setText(currentModel());
        modelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        modelView.setTypeface(AndroidUtilities.bold());
        modelView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        modelView.setSingleLine(true);
        modelView.setEllipsize(TextUtils.TruncateAt.END);
        modelView.setGravity(Gravity.CENTER_VERTICAL);
        modelView.setPadding(AndroidUtilities.dp(9), 0, AndroidUtilities.dp(9), 0);
        modelView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),
                Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlueText), .10f)));
        modelView.setOnClickListener(this::showModels);
        titles.addView(modelView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, 22, 0, 0, 0, 0));

        insertView = icon(context, R.drawable.msg_send);
        insertView.setContentDescription("Вставить");
        insertView.setVisibility(View.GONE);
        insertView.setOnClickListener(v -> insert());
        header.addView(insertView, LayoutHelper.createFrame(44, 44,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 44, 0));

        optionsView = icon(context, R.drawable.ic_ab_other);
        optionsView.setContentDescription("Действия");
        optionsView.setVisibility(View.GONE);
        optionsView.setOnClickListener(this::showOptions);
        header.addView(optionsView, LayoutHelper.createFrame(44, 44,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        View headerDivider = new View(context);
        headerDivider.setBackgroundColor(getThemedColor(Theme.key_divider));
        header.addView(headerDivider, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(16),
                AndroidUtilities.dp(22), AndroidUtilities.dp(22));
        scrollView.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        boolean responseOnly = MessagesController.getGlobalMainSettings()
                .getBoolean("dg_aiShowResponseOnly", false);
        if (!responseOnly) addContext(body, context);

        promptView = new TextView(context);
        promptView.setText("→  " + prompt);
        promptView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        promptView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        promptView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        promptView.setMaxLines(6);
        promptView.setEllipsize(TextUtils.TruncateAt.END);
        promptView.setVisibility(responseOnly ? View.GONE : View.VISIBLE);
        body.addView(promptView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        responseView = new TextView(context);
        responseView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        responseView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        responseView.setLinkTextColor(getThemedColor(Theme.key_windowBackgroundWhiteLinkText));
        responseView.setTextIsSelectable(true);
        responseView.setLineSpacing(AndroidUtilities.dp(3), 1.03f);
        body.addView(responseView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        typingDots = new TypingDotsDrawable(true);
        typingDots.setColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        typingDots.setIsChat(false);
        typingDots.setIgnoreAnimationLocks();

        FrameLayout bottom = new FrameLayout(context);
        bottom.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8),
                AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        root.addView(bottom, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 68));
        mainButton = new ButtonWithCounterView(context, resourcesProvider).setRound();
        mainButton.setOnClickListener(v -> onMainButton());
        bottom.addView(mainButton, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER));

        setCustomView(root);
        generate();
    }

    private ImageView icon(Context context, int resource) {
        ImageView view = new ImageView(context);
        view.setImageResource(resource);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setColorFilter(new PorterDuffColorFilter(
                getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
        view.setBackground(Theme.createSelectorDrawable(
                getThemedColor(Theme.key_listSelector), 1));
        ScaleStateListAnimator.apply(view, 0.08f, 1.2f);
        return view;
    }

    private String currentRole() {
        return MessagesController.getGlobalMainSettings().getString("dg_aiRole", "Assistant");
    }

    private String currentModel() {
        if (DevGramGeminiNano.isSelected()) return DevGramGeminiNano.modelLabel();
        return MessagesController.getGlobalMainSettings().getString("dg_aiModel", "gpt-4o-mini");
    }

    private void showRoles(View anchor) {
        ItemOptions options = ItemOptions.makeOptions(getContainerView(), resourcesProvider, anchor);
        String selected = currentRole();
        addRoleOption(options, selected, "Assistant",
                "You are a helpful personal assistant integrated into Telegram. Answer concisely and helpfully.");
        addRoleOption(options, selected, "Summarizer",
                "Summarize the provided message or conversation clearly and concisely.");
        addRoleOption(options, selected, "Proofreader",
                "Proofread and improve the user's text. Reply only with the corrected text.");
        try {
            JSONArray custom = new JSONArray(MessagesController.getGlobalMainSettings()
                    .getString("dg_aiCustomRoles", "[]"));
            for (int i = 0; i < custom.length(); i++) {
                JSONObject role = custom.optJSONObject(i);
                if (role == null) continue;
                addRoleOption(options, selected, role.optString("name"), role.optString("prompt"));
            }
        } catch (Throwable ignore) {}
        options.addGap();
        options.add(R.drawable.msg_settings, "Настроить роли", () -> {
            dismiss();
            fragment.presentFragment(new DevGramAiRolesActivity());
        });
        options.show();
    }

    private void addRoleOption(ItemOptions options, String selected, String name, String instruction) {
        if (TextUtils.isEmpty(name)) return;
        options.addChecked(name.equals(selected), name, () -> {
            MessagesController.getGlobalMainSettings().edit()
                    .putString("dg_aiRole", name)
                    .putString("dg_aiSystemPrompt", instruction == null ? "" : instruction)
                    .apply();
            roleView.setText(name + "  ▾");
            generate();
        });
    }

    private void showModels(View anchor) {
        ItemOptions options = ItemOptions.makeOptions(getContainerView(), resourcesProvider, anchor);
        if (DevGramGeminiNano.getStatus() == DevGramGeminiNano.STATUS_AVAILABLE) {
            options.addChecked(DevGramGeminiNano.isSelected(), DevGramGeminiNano.modelLabel(), () -> {
                MessagesController.getGlobalMainSettings().edit().putBoolean("dg_aiUseNano", true).apply();
                modelView.setText(currentModel());
                generate();
            });
        }
        JSONArray services = DevGramAiServicesActivity.services();
        int selected = MessagesController.getGlobalMainSettings().getInt("dg_aiServiceSel", 0);
        for (int i = 0; i < services.length(); i++) {
            JSONObject service = services.optJSONObject(i);
            if (service == null) continue;
            final int index = i;
            String title = service.optString("name", service.optString("model", "Сервис"));
            options.addChecked(!DevGramGeminiNano.isSelected() && selected == i, title, () -> {
                DevGramAiServicesActivity.applyActive(index);
                modelView.setText(currentModel());
                generate();
            });
        }
        options.addGap();
        options.add(R.drawable.msg_settings, "Настроить сервисы", () -> {
            dismiss();
            fragment.presentFragment(new DevGramAiServicesActivity());
        });
        options.show();
    }

    private void addContext(LinearLayout body, Context context) {
        if (!useHistory) return;
        try {
            JSONArray history = new JSONArray(MessagesController.getGlobalMainSettings()
                    .getString("dg_aiHistory", "[]"));
            int start = Math.max(0, history.length() - 6);
            for (int i = start; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) continue;
                String value = item.optString("content", "").trim();
                if (value.isEmpty()) continue;
                TextView line = new TextView(context);
                boolean assistant = "assistant".equals(item.optString("role"));
                line.setText((assistant ? "ИИ: " : "Вы: ") + value);
                line.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                line.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
                line.setMaxLines(3);
                line.setEllipsize(TextUtils.TruncateAt.END);
                body.addView(line, LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 7));
            }
            if (history.length() > 0) {
                View divider = new View(context);
                divider.setBackgroundColor(getThemedColor(Theme.key_divider));
                body.addView(divider, LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, 1, 0, 5, 0, 15));
            }
        } catch (Throwable ignore) {}
    }

    private void onMainButton() {
        if (generating) {
            generation++;
            generating = false;
            typingDots.stop();
            responseView.setCompoundDrawables(null, null, null, null);
            if (TextUtils.isEmpty(response)) responseView.setText("Генерация остановлена");
            finishState();
        } else if (failed) {
            generate();
        } else {
            dismiss();
        }
    }

    private void showOptions(View anchor) {
        ItemOptions options = ItemOptions.makeOptions(getContainerView(), resourcesProvider, anchor);
        options.add(R.drawable.msg_copy, "Копировать", () -> {
            if (!TextUtils.isEmpty(response) && AndroidUtilities.addToClipboard(response)) {
                BulletinFactory.of(fragment).createCopyBulletin("Ответ скопирован").show();
            }
        });
        options.add(R.drawable.msg_retry, "Повторить", this::generate);
        options.add(R.drawable.msg_edit, "Изменить запрос", () -> editPrompt(prompt));
        options.add(R.drawable.msg_add, "Спросить ещё", () -> editPrompt(""));
        options.show();
    }

    private void editPrompt(String initial) {
        new DevGramGenerateFromMessageBottomSheet(initial, fragment, getContext(), data -> {
            prompt = data.prompt;
            useHistory = data.useHistory;
            promptView.setText("→  " + prompt);
            generate();
        }).show();
    }

    private void insert() {
        if (TextUtils.isEmpty(response)) return;
        if (useHistory) DevGramAiClient.saveTurn(prompt, response);
        onInsert.run(response);
        dismiss();
    }

    private void generate() {
        final int current = ++generation;
        response = "";
        generating = true;
        failed = false;
        insertView.setVisibility(View.GONE);
        optionsView.setVisibility(View.GONE);
        responseView.setAlpha(1f);
        responseView.setTranslationY(0);
        responseView.setText("ИИ думает");
        responseView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        responseView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        responseView.setCompoundDrawablesWithIntrinsicBounds(null, null, typingDots, null);
        typingDots.start();
        mainButton.setLoading(false);
        mainButton.setText("Остановить", true);

        boolean streaming = MessagesController.getGlobalMainSettings()
                .getBoolean("dg_aiStreaming", true);
        DevGramAiClient.generatePreview(prompt, useHistory, streaming, new DevGramAiClient.StreamCallback() {
            @Override
            public void onDelta(String delta) {
                if (current != generation || !generating || TextUtils.isEmpty(delta)) return;
                if (TextUtils.isEmpty(response)) {
                    typingDots.stop();
                    responseView.setCompoundDrawables(null, null, null, null);
                    responseView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
                    responseView.setText("");
                }
                response += delta;
                responseView.append(delta);
            }

            @Override
            public void onDone(String full, Throwable error) {
                if (current != generation || isDismissed()) return;
                generating = false;
                typingDots.stop();
                responseView.setCompoundDrawables(null, null, null, null);
                if (error != null || TextUtils.isEmpty(full)) {
                    failed = true;
                    response = "";
                    responseView.setText(error != null && !TextUtils.isEmpty(error.getMessage())
                            ? "Не удалось получить ответ\n\n" + error.getMessage()
                            : "ИИ-сервис не вернул ответ.");
                    responseView.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    mainButton.setText("Повторить", true);
                    return;
                }
                response = full.trim();
                responseView.setText(response);
                responseView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
                responseView.setAlpha(0f);
                responseView.setTranslationY(AndroidUtilities.dp(6));
                responseView.animate().alpha(1f).translationY(0).setDuration(220).start();
                finishState();
            }
        });
    }

    private void finishState() {
        failed = false;
        mainButton.setText("Закрыть", true);
        if (!TextUtils.isEmpty(response)) {
            AndroidUtilities.updateViewVisibilityAnimated(insertView, true, 1f, true);
            AndroidUtilities.updateViewVisibilityAnimated(optionsView, true, 1f, true);
        }
    }

    @Override
    public void dismiss() {
        generation++;
        generating = false;
        typingDots.stop();
        super.dismiss();
    }
}

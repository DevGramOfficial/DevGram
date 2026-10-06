package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramAiClient;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class DevGramAiChatActivity extends BaseFragment {
    private static final int MENU_CLEAR = 1;

    private TextView conversation;
    private EditText input;
    private Button send;
    private ScrollView scroll;

    // История диалога {role: user|assistant, content}. Отправляется в модель как контекст.
    private final JSONArray history = new JSONArray();
    private final StringBuilder transcript = new StringBuilder();
    private boolean generating;

    private static boolean pref(String key, boolean def) {
        return MessagesController.getGlobalMainSettings().getBoolean(key, def);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("ИИ-чат");
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_CLEAR, R.drawable.msg_delete);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_CLEAR) {
                    clearHistory();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        scroll = new ScrollView(context);
        conversation = new TextView(context);
        conversation.setTextSize(16);
        conversation.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        conversation.setPadding(0, 0, 0, AndroidUtilities.dp(16));
        scroll.addView(conversation, new ScrollView.LayoutParams(-1, -2));
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        input = new EditText(context);
        input.setHint("Сообщение");
        input.setMinLines(1);
        input.setMaxLines(5);
        column.addView(input, new LinearLayout.LayoutParams(-1, -2));

        send = new Button(context);
        send.setText("Отправить");
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setOnClickListener(v -> send());
        column.addView(send, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(48)));

        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        loadHistory();
        renderTranscript();
        return fragmentView = root;
    }

    private void loadHistory() {
        if (!pref("dg_aiSaveHistory", true)) {
            return;
        }
        try {
            String saved = MessagesController.getGlobalMainSettings().getString("dg_aiHistory", "");
            if (!saved.isEmpty()) {
                JSONArray arr = new JSONArray(saved);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.optJSONObject(i);
                    if (m != null) history.put(m);
                }
            }
        } catch (Throwable ignore) {}
    }

    private void persistHistory() {
        if (!pref("dg_aiSaveHistory", true)) {
            return;
        }
        MessagesController.getGlobalMainSettings().edit().putString("dg_aiHistory", history.toString()).apply();
    }

    private void clearHistory() {
        for (int i = history.length() - 1; i >= 0; i--) {
            history.remove(i);
        }
        MessagesController.getGlobalMainSettings().edit().remove("dg_aiHistory").apply();
        renderTranscript();
    }

    // Полная перерисовка ленты из истории (учитывает «Показывать только ответ»).
    private void renderTranscript() {
        transcript.setLength(0);
        boolean responseOnly = pref("dg_aiShowResponseOnly", false);
        if (history.length() == 0) {
            transcript.append("DevGram AI\n\nЗадайте вопрос или вставьте текст для редактирования и пересказа.");
        } else {
            for (int i = 0; i < history.length(); i++) {
                JSONObject m = history.optJSONObject(i);
                if (m == null) continue;
                boolean user = "user".equals(m.optString("role"));
                if (user && responseOnly) continue;
                if (transcript.length() > 0) transcript.append("\n\n");
                transcript.append(user ? "Вы:\n" : "ИИ:\n").append(m.optString("content"));
            }
        }
        if (conversation != null) conversation.setText(transcript.toString());
        scrollToBottom();
    }

    private void send() {
        if (generating || input == null) {
            return;
        }
        final String prompt = input.getText().toString().trim();
        if (prompt.isEmpty()) {
            return;
        }
        if (!DevGramAiClient.isConfigured()) {
            transcript.append(transcript.length() > 0 ? "\n\n" : "").append("Настройте ИИ-сервис в AI Chat.");
            if (conversation != null) conversation.setText(transcript.toString());
            return;
        }
        input.setText("");
        try {
            history.put(new JSONObject().put("role", "user").put("content", prompt));
        } catch (Throwable ignore) {}

        generating = true;
        send.setEnabled(false);

        final boolean responseOnly = pref("dg_aiShowResponseOnly", false);
        final boolean streaming = pref("dg_aiStreaming", true);
        final boolean saveHistory = pref("dg_aiSaveHistory", true);

        // Убираем плейсхолдер, если он ещё показан.
        if (history.length() == 1) {
            transcript.setLength(0);
        }
        if (!responseOnly) {
            if (transcript.length() > 0) transcript.append("\n\n");
            transcript.append("Вы:\n").append(prompt);
        }
        if (transcript.length() > 0) transcript.append("\n\n");
        transcript.append("ИИ:\n");
        if (conversation != null) conversation.setText(transcript.toString());
        scrollToBottom();

        // Контекст: если история включена — весь диалог, иначе только текущее сообщение.
        JSONArray context = new JSONArray();
        try {
            if (saveHistory) {
                for (int i = 0; i < history.length(); i++) {
                    JSONObject m = history.optJSONObject(i);
                    if (m != null) context.put(m);
                }
            } else {
                context.put(new JSONObject().put("role", "user").put("content", prompt));
            }
        } catch (Throwable ignore) {}

        DevGramAiClient.generate(context, streaming, new DevGramAiClient.StreamCallback() {
            @Override public void onDelta(String delta) {
                transcript.append(delta);
                if (conversation != null) conversation.setText(transcript.toString());
                scrollToBottom();
            }

            @Override public void onDone(String full, Throwable error) {
                generating = false;
                if (send != null) send.setEnabled(true);
                if (error != null) {
                    transcript.append("\n[Ошибка: ").append(error.getMessage()).append("]");
                    if (conversation != null) conversation.setText(transcript.toString());
                    scrollToBottom();
                    return;
                }
                String answer = full != null ? full : "";
                if (!streaming) {
                    transcript.append(answer);
                    if (conversation != null) conversation.setText(transcript.toString());
                }
                try {
                    history.put(new JSONObject().put("role", "assistant").put("content", answer));
                } catch (Throwable ignore) {}
                persistHistory();
                scrollToBottom();
            }
        });
    }

    private void scrollToBottom() {
        if (scroll != null) {
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
    }
}

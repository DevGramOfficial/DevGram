package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramCommunityProfiles;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramProfileCustomization;
import org.telegram.messenger.DevGramProfileIntegrations;
import org.telegram.messenger.DevGramProfilePolicy;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The reference Steam library sheet, using recycled rows and DevGram's own API. */
public final class DevGramSteamGamesSheet {
    private final BaseFragment host;
    private final long owner, viewer;
    private final int account;
    private final JSONObject row;
    private final String profileLink;
    private final Theme.ResourcesProvider resources;
    private final JSONArray games = new JSONArray();
    private JSONArray loadedGames = games;
    private JSONObject profile, hero;
    private long minutes;
    private int count;
    private String error;
    private boolean loading = true, closed;
    private BottomSheet sheet;
    private LibraryAdapter adapter;
    private TextView retry;
    private int generation;
    private final Runnable policy = () -> { if (!valid() && sheet != null) sheet.dismiss(); };

    private DevGramSteamGamesSheet(BaseFragment host, long owner, JSONObject row, String profileLink) {
        this.host = host; this.owner = owner; this.row = row; this.profileLink = profileLink;
        resources = host.getResourceProvider(); account = host.getCurrentAccount();
        viewer = UserConfig.getInstance(account).getClientUserId();
    }

    public static void show(BaseFragment host, long owner, JSONObject row, String profileLink) {
        if (host == null || host.getParentActivity() == null) return;
        DevGramSteamGamesSheet controller = new DevGramSteamGamesSheet(host, owner, row, profileLink);
        if (controller.valid()) controller.open();
    }

    private boolean valid() {
        return !closed && host.getParentActivity() != null && host.getCurrentAccount() == account
                && UserConfig.getInstance(account).getClientUserId() == viewer
                && DevGramProfileCustomization.enabled() && DevGramProfilePolicy.allowed(owner, "profile.blocks")
                && DevGramProfilePolicy.allowed(owner, "profile.integrations")
                && (!row.optBoolean("ownOnly") || DevGramCommunityProfiles.owner(owner) == viewer);
    }

    private static int dp(float value) { return AndroidUtilities.dp(value); }
    private static String t(String value) { return DevGramLocale.text(value); }
    private String format(String value, Object... args) { return String.format(Locale.getDefault(), t(value), args); }
    private static String hours(long value) {
        return value % 60 == 0 ? Long.toString(value / 60) : String.format(Locale.getDefault(), "%.1f", value / 60.0);
    }

    private void open() {
        Context context = host.getParentActivity();
        LinearLayout body = DevGramStudioControls.column(context);
        body.setPadding(dp(18), 0, dp(18), dp(14));
        RecyclerListView list = new RecyclerListView(context, resources) {
            @Override protected void onMeasure(int width, int height) {
                super.onMeasure(width, DevGramStudioControls.sheetHeightSpec(height, .62f));
            }
        };
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(true);
        list.setPadding(0, dp(4), 0, dp(8));
        list.setAdapter(adapter = new LibraryAdapter());
        list.setOnItemClickListener((view, position) -> {
            if (!valid() || loading || error != null) return;
            JSONObject game = position == 0 && hero != null ? hero : gameAt(position);
            if (game != null) openGame(game.optLong("id"));
        });
        body.addView(list, LayoutHelper.createLinear(-1, -2));
        retry = DevGramStudioControls.text(context, "Попробовать снова", 14, true, resources);
        retry.setGravity(Gravity.CENTER); retry.setVisibility(View.GONE);
        retry.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resources));
        retry.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resources), 2));
        retry.setOnClickListener(v -> load());
        body.addView(retry, LayoutHelper.createLinear(-1, 44, 0, 0, 0, 6));
        TextView open = DevGramStudioControls.text(context, "Профиль в Steam", 15, true, resources);
        int linkColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resources);
        open.setTextColor(linkColor);
        open.setGravity(Gravity.CENTER);
        open.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10),
                (linkColor & 0x00ffffff) | 0x22000000,
                (linkColor & 0x00ffffff) | 0x33000000));
        open.setOnClickListener(v -> { if (valid() && !profileLink.isEmpty()) Browser.openUrl(context, profileLink); });
        open.setEnabled(!profileLink.isEmpty());
        body.addView(open, LayoutHelper.createLinear(-1, 48));
        sheet = new BottomSheet.Builder(context, false, resources).setTitle("Steam").setCustomView(body).create();
        sheet.fixNavigationBarKey(Theme.key_dialogBackground);
        DevGramProfilePolicy.listen(policy);
        host.showDialog(sheet, dialog -> {
            closed = true; generation++; DevGramProfilePolicy.unlisten(policy);
        });
        load();
    }

    private void load() {
        if (!valid()) return;
        int request = ++generation;
        loading = true; error = null; retry.setVisibility(View.GONE); adapter.notifyDataSetChanged();
        DevGramProfileIntegrations.loadSteamLibrary(account, owner, row, (data, failure) -> {
            if (!valid() || request != generation || !sheet.isShowing()) return;
            loading = false;
            if (data == null || !data.optBoolean("ok")) {
                error = data == null ? "net" : data.optString("err", "net");
                retry.setVisibility(error.equals("net") || error.equals("rate") ? View.VISIBLE : View.GONE);
            } else {
                loadedGames = data.optJSONArray("games"); if (loadedGames == null) loadedGames = games;
                profile = data.optJSONObject("profile");
                hero = profile == null ? null : profile.optJSONObject("game");
                if (hero == null && loadedGames.length() > 0) hero = loadedGames.optJSONObject(0);
                count = data.optInt("count"); minutes = data.optLong("minutes");
            }
            adapter.notifyDataSetChanged();
        });
    }

    private JSONObject gameAt(int position) {
        int index = position - (hero == null ? 1 : 2);
        return index < 0 ? null : loadedGames.optJSONObject(index);
    }
    private void openGame(long id) {
        if (id > 0 && id <= Integer.MAX_VALUE) Browser.openUrl(host.getParentActivity(), "https://store.steampowered.com/app/" + id + "/");
    }
    private static String artwork(long id, boolean large) {
        return id > 0 && id <= Integer.MAX_VALUE ? "https://cdn.cloudflare.steamstatic.com/steam/apps/" + id + (large ? "/header.jpg" : "/capsule_184x69.jpg") : "";
    }
    private String trouble() {
        if ("nokey".equals(error)) return t("У сервера нет ключа Steam: библиотеку показать нечем. Профиль откроется кнопкой ниже.");
        if ("closed".equals(error)) return t("Библиотека скрыта настройками приватности Steam.");
        if ("account".equals(error)) return t("Такого профиля в Steam нет.");
        if ("rate".equals(error)) return t("Слишком часто, попробуйте через минуту.");
        return t("Steam не ответил. Попробуйте позже.");
    }

    private final class LibraryAdapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position >= 0 && !loading && error == null && (getItemViewType(position) == 0 || getItemViewType(position) == 2);
        }
        @Override public int getItemCount() { return loading || error != null ? 1 : (hero == null ? 1 : 2) + Math.max(1, loadedGames.length()); }
        @Override public int getItemViewType(int position) {
            if (loading || error != null) return 3;
            if (hero != null && position == 0) return 0;
            if (position == (hero == null ? 0 : 1)) return 1;
            return loadedGames.length() == 0 ? 3 : 2;
        }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            View view = type == 0 ? new HeroCell(parent.getContext()) : type == 2 ? new GameCell(parent.getContext())
                    : DevGramStudioControls.text(parent.getContext(), "", type == 1 ? 15 : 13, type == 1, resources);
            view.setLayoutParams(new RecyclerView.LayoutParams(-1, type == 0 ? dp(160) : type == 2 ? dp(60) : -2));
            if (view instanceof TextView) {
                view.setPadding(dp(4), dp(8), dp(4), dp(12));
                if (type == 3) ((TextView) view).setGravity(Gravity.CENTER);
            }
            return new RecyclerListView.Holder(view);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof HeroCell) { ((HeroCell) holder.itemView).bind(hero); return; }
            if (holder.itemView instanceof GameCell) { ((GameCell) holder.itemView).bind(gameAt(position)); return; }
            TextView text = (TextView) holder.itemView;
            if (loading) text.setText(t("Загружаю библиотеку…"));
            else if (error != null) text.setText(trouble());
            else if (getItemViewType(position) == 3) text.setText(t("В библиотеке пусто."));
            else {
                String name = profile == null ? "" : profile.optString("name");
                String level = profile != null && profile.has("level") ? " · " + format("уровень %s", profile.optInt("level")) : "";
                text.setText(name + level + " · " + format("%s игр", count) + " · " + format("%s ч", hours(minutes)));
            }
        }
    }

    private final class GameCell extends LinearLayout {
        final BackupImageView image;
        final TextView title, subtitle;
        GameCell(Context context) {
            super(context); setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(4), dp(4), dp(4), dp(4));
            image = new BackupImageView(context); image.setRoundRadius(dp(8)); image.setAspectFit(false);
            addView(image, LayoutHelper.createLinear(92, 43, 0, 0, 10, 0));
            LinearLayout labels = DevGramStudioControls.column(context);
            title = DevGramStudioControls.text(context, "", 15, true, resources);
            subtitle = DevGramStudioControls.text(context, "", 13, false, resources);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resources));
            title.setSingleLine(true); subtitle.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END); subtitle.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(title, LayoutHelper.createLinear(-1, -2));
            labels.addView(subtitle, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));
            addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        }
        void bind(JSONObject game) {
            if (game == null) return;
            image.setImage(artwork(game.optLong("id"), false), "184_86", null);
            title.setText(game.optString("name"));
            String text = format("%s ч всего", hours(game.optLong("minutes")));
            if (game.optLong("recentMinutes") > 0) text += " · " + format("%s ч за 2 нед", hours(game.optLong("recentMinutes")));
            if (game.optLong("last") > 0) text += " · " + new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(new Date(game.optLong("last") * 1000));
            subtitle.setText(text);
        }
    }

    private final class HeroCell extends FrameLayout {
        final BackupImageView image;
        final TextView caption, title;
        HeroCell(Context context) {
            super(context); setPadding(0, 0, 0, dp(10));
            FrameLayout poster = new FrameLayout(context);
            poster.setBackground(DevGramStudioControls.surface(0xff20242b, 0)); poster.setClipToOutline(true);
            addView(poster, LayoutHelper.createFrame(-1, 150, Gravity.TOP));
            image = new BackupImageView(context); image.setAspectFit(false);
            poster.addView(image, LayoutHelper.createFrame(-1, -1));
            LinearLayout labels = DevGramStudioControls.column(context); labels.setPadding(dp(14), dp(28), dp(14), dp(12));
            labels.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0, 0xcc000000}));
            caption = DevGramStudioControls.text(context, "", 12, false, resources); caption.setTextColor(0xddffffff);
            title = DevGramStudioControls.text(context, "", 18, true, resources); title.setTextColor(Color.WHITE);
            title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(caption); labels.addView(title, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));
            poster.addView(labels, LayoutHelper.createFrame(-1, -2, Gravity.BOTTOM));
        }
        void bind(JSONObject game) {
            image.setImage(artwork(game.optLong("id"), true), "600_300", null);
            boolean playing = profile != null && profile.optJSONObject("game") != null;
            caption.setText(t(playing ? "Сейчас играет" : "Последняя игра")); title.setText(game.optString("name"));
        }
    }
}

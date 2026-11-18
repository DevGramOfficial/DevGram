package org.telegram.ui.Components;

import android.net.Uri;
import org.json.JSONObject;

/** Shared link resolution for service cards, including rows that store an ID rather than a URL. */
public final class DevGramIntegrationActions {
    private DevGramIntegrationActions() {}

    public static String link(JSONObject row, JSONObject data) {
        String supplied = data == null ? "" : data.optString("link").trim();
        if (https(supplied)) return supplied;
        String handle = row.optString("url").trim();
        String encoded = Uri.encode(handle);
        switch (row.optInt("service")) {
            case 0: return handle.isEmpty() ? "" : "https://www.last.fm/user/" + encoded;
            case 1: return handle.isEmpty() ? "" : "https://github.com/" + encoded;
            case 2: return handle.isEmpty() ? "" : "https://steamcommunity.com/" + (handle.matches("[0-9]{17}") ? "profiles/" : "id/") + encoded;
            case 3: return "https://music.yandex.ru/";
            case 4: return "https://open.spotify.com/";
            case 5: return "https://soundcloud.com/";
            case 7: return handle.matches("[0-9]{16,22}") ? "https://discord.com/users/" + encoded : "https://discord.com/";
            case 8:
                JSONObject track = data == null ? null : data.optJSONObject("nowPlaying");
                supplied = track == null ? "" : track.optString("link");
                return https(supplied) ? supplied : "";
            default: return "";
        }
    }

    private static boolean https(String value) {
        if (!value.startsWith("https://")) return false;
        Uri uri = Uri.parse(value);
        return uri.getHost() != null && !uri.getHost().isEmpty();
    }
}

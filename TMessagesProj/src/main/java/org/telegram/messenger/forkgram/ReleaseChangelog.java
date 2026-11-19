package org.telegram.messenger.forkgram;

import java.util.Locale;
import java.util.regex.Pattern;

/** Display release changes, rather than GitHub's banner/navigation Markdown. */
public final class ReleaseChangelog {
    private static final Pattern LINKS = Pattern.compile("\\[([^\\]]+)\\]\\(((?:[^()\\s]|\\([^()]*\\))*)\\)");
    private static final Pattern IMAGES = Pattern.compile("!\\[[^\\]]*\\]\\((?:[^()\\s]|\\([^()]*\\))*\\)");
    private static final Pattern VERSION = Pattern.compile("^(?:✈️\\s*)?DevGram\\s+\\d.*", Pattern.CASE_INSENSITIVE);

    private ReleaseChangelog() { }

    private static int language(String line) {
        String label = line.replace("**", "").replace("#", "").replace("🇷🇺", "").replace("🇺🇸", "").trim().toLowerCase(Locale.ROOT);
        if (label.equals("русский") || label.equals("russian")) return 1;
        if (label.equals("english") || label.equals("английский")) return 2;
        return 0;
    }

    public static String clean(String raw, String version, boolean russian) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String[] lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        int start = 0, end = lines.length, wanted = russian ? 1 : 2;
        for (int i = 0; i < lines.length; i++) {
            if (language(lines[i]) == wanted) {
                start = i + 1;
                for (int j = start; j < lines.length; j++) if (language(lines[j]) != 0) { end = j; break; }
                break;
            }
        }
        StringBuilder out = new StringBuilder();
        for (int i = start; i < end; i++) {
            String line = lines[i].trim();
            if (language(line) != 0) continue;
            boolean heading = line.startsWith("#");
            if (heading) line = line.replaceFirst("^#+\\s*", "").trim();
            String plain = line.replace("**", "").trim();
            if (VERSION.matcher(plain).matches() || (heading && version != null && !version.isEmpty() && plain.contains(version)) || line.startsWith("![")
                    || line.startsWith("[Скачать / Download APK]") || line.startsWith("[Download APK]")
                    || plain.startsWith("*Некоторые идеи") || plain.startsWith("Некоторые идеи")
                    || plain.startsWith("*Some ideas were taken") || plain.startsWith("Some ideas were taken")) continue;
            line = IMAGES.matcher(line).replaceAll("");
            line = LINKS.matcher(line).replaceAll("$1").replace("`", "").trim();
            if (line.isEmpty()) { out.append('\n'); continue; }
            if (heading && !line.startsWith("**")) line = "**" + line + "**";
            out.append(line.replaceFirst("^[-*+]\\s+", "•  ")).append('\n');
        }
        return out.toString().trim().replaceAll("\\n{3,}", "\n\n");
    }
}

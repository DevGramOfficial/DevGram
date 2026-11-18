package org.telegram.ui.Components.devgram.blocks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* Adapted from the user-supplied Custom Profile plugin by @RoflPlugins.
 * See docs/PROFILE_STUDIO_REFERENCE_AUDIT.md for the reference and restoration evidence. */
final class BlockComponents {
    static final int MAX_COMPONENTS = 64;
    static final int MAX_SOURCE = 8192;
    static final String SAMPLE = "<component id=\"card\" type=\"rect\" />\n<component id=\"poster\" type=\"image\" source=\"$poster\" />\n<component id=\"cover\" type=\"image\" source=\"$cover\" />\n<component id=\"label\" type=\"text\" content=\"$label\" />\n<component id=\"value\" type=\"text\" content=\"$value\" />\n<component id=\"track-title\" type=\"text\" content=\"$title\" />\n<component id=\"artist\" type=\"text\" content=\"$artist\" />\n<component id=\"playback-state\" type=\"text\" content=\"$playbackState\" />\n<component id=\"progress\" type=\"progress\" value=\"progress\" />\n<component id=\"arrow\" type=\"arrow\" />\n<!-- Свой компонент: <component id=\"badge\" type=\"pill\" content=\"${service}\" /> -->";
    private final Map<String, Def> definitions;
    final String source;
    private static final Pattern TAG = Pattern.compile("<component\\s+([^<>]*?)/>", 2);
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-z][a-z0-9_-]{0,31})\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", 2);

    private BlockComponents(String str, Map<String, Def> map) {
        this.source = str;
        this.definitions = Collections.unmodifiableMap(map);
    }

    static BlockComponents defaults() {
        try {
            return compile(SAMPLE);
        } catch (Error e) {
            throw new IllegalStateException(e);
        }
    }

    static BlockComponents compile(String str) throws Error {
        String strTrim = str == null ? "" : str.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (strTrim.length() == 0) {
            strTrim = SAMPLE;
        }
        if (strTrim.length() > MAX_SOURCE) {
            throw new Error("компоненты длиннее 8192 знаков");
        }
        String strComments = comments(strTrim);
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        Matcher matcher = TAG.matcher(strComments);
        int iEnd = 0;
        while (matcher.find()) {
            if (strComments.substring(iEnd, matcher.start()).trim().length() > 0) {
                throw new Error("неизвестный код между компонентами");
            }
            LinkedHashMap<String, String> linkedHashMapAttributes = attributes(matcher.group(1));
            String strLower = lower(linkedHashMapAttributes.remove("id"));
            String strLower2 = lower(linkedHashMapAttributes.remove("type"));
            if (!strLower.matches("[a-z][a-z0-9_-]{0,31}")) {
                throw new Error("у компонента неверный id");
            }
            if (!strLower2.matches("rect|gradient|text|pill|image|progress|arrow")) {
                throw new Error("компонент «" + strLower + "»: неизвестный type");
            }
            if (linkedHashMap.containsKey(strLower)) {
                throw new Error("компонент «" + strLower + "» указан дважды");
            }
            LinkedHashMap linkedHashMap2 = new LinkedHashMap();
            for (Map.Entry<String, String> entry : linkedHashMapAttributes.entrySet()) {
                String key = entry.getKey();
                if ("content".equals(key)) {
                    key = "value";
                }
                if (!"value".equals(key) && !"source".equals(key)) {
                    throw new Error("компонент «" + strLower + "»: неизвестный атрибут «" + entry.getKey() + "»");
                }
                linkedHashMap2.put(key, entry.getValue());
            }
            linkedHashMap.put(strLower, new Def(strLower, strLower2, Collections.unmodifiableMap(linkedHashMap2)));
            if (linkedHashMap.size() > 64) {
                throw new Error("слишком много компонентов");
            }
            iEnd = matcher.end();
        }
        if (strComments.substring(iEnd).trim().length() > 0) {
            throw new Error("неизвестный код после компонентов");
        }
        if (linkedHashMap.isEmpty()) {
            throw new Error("список компонентов пуст");
        }
        return new BlockComponents(strTrim, linkedHashMap);
    }

    Def get(String str) {
        if (str == null) {
            return null;
        }
        if (str.startsWith("#")) {
            str = str.substring(1);
        }
        String lowerCase = str.toLowerCase(Locale.US);
        Def def = this.definitions.get(lowerCase);
        if (def != null) {
            return def;
        }
        if ("track-title".equals(lowerCase)) {
            return builtIn(lowerCase, "$title");
        }
        if ("artist".equals(lowerCase)) {
            return builtIn(lowerCase, "$artist");
        }
        if ("playback-state".equals(lowerCase)) {
            return builtIn(lowerCase, "$playbackState");
        }
        return null;
    }

    private static Def builtIn(String str, String str2) {
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        linkedHashMap.put("value", str2);
        return new Def(str, "text", Collections.unmodifiableMap(linkedHashMap));
    }

    private static LinkedHashMap<String, String> attributes(String str) throws Error {
        LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(str);
        int iEnd = 0;
        while (matcher.find()) {
            if (str.substring(iEnd, matcher.start()).trim().length() > 0) {
                throw new Error("не удалось разобрать атрибут компонента");
            }
            String strLower = lower(matcher.group(1));
            if (linkedHashMap.containsKey(strLower)) {
                throw new Error("атрибут «" + strLower + "» указан дважды");
            }
            int i = 2;
            if (matcher.group(2) == null) {
                i = 3;
            }
            linkedHashMap.put(strLower, matcher.group(i));
            iEnd = matcher.end();
        }
        if (str.substring(iEnd).trim().length() <= 0) {
            return linkedHashMap;
        }
        throw new Error("не удалось разобрать атрибут компонента");
    }

    private static String comments(String str) throws Error {
        int i;
        StringBuilder sb = new StringBuilder();
        int i2 = 0;
        while (i2 < str.length()) {
            if (str.startsWith("<!--", i2)) {
                int iIndexOf = str.indexOf("-->", i2 + 4);
                if (iIndexOf < 0) {
                    throw new Error("комментарий компонентов не закрыт");
                }
                while (true) {
                    i = iIndexOf + 3;
                    if (i2 >= i) {
                        break;
                    }
                    char c = '\n';
                    if (str.charAt(i2) != '\n') {
                        c = ' ';
                    }
                    sb.append(c);
                    i2++;
                }
                i2 = i;
            } else {
                sb.append(str.charAt(i2));
                i2++;
            }
        }
        return sb.toString();
    }

    private static String lower(String str) {
        return str == null ? "" : str.trim().toLowerCase(Locale.US);
    }

    static final class Def {
        final Map<String, String> attrs;
        final String id;
        final String type;

        Def(String str, String str2, Map<String, String> map) {
            this.id = str;
            this.type = str2;
            this.attrs = map;
        }
    }

    static final class Error extends Exception {
        Error(String str) {
            super(str);
        }
    }
}

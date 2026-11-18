package org.telegram.ui.Components.devgram.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* Adapted from the user-supplied Custom Profile plugin by @RoflPlugins.
 * See docs/PROFILE_STUDIO_REFERENCE_AUDIT.md for the reference and restoration evidence. */
final class BlockCss {
    static final int MAX_RULES = 64;
    static final int MAX_SOURCE = 16384;
    static final String SAMPLE = "/* Block CSS 1 */\ncard {\n  height: 106dp;\n  /* Для вертикального блока вместо height: aspect-ratio: 9 / 16; */\n  border-radius: 18dp;\n  background: linear-gradient(20deg, $serviceColor, #10141C00);\n}\n\ncover {\n  left: 14dp; top: 30dp;\n  width: 46dp; height: 46dp;\n  border-radius: 14dp;\n}\n\nlabel {\n  left: 73dp; top: 30dp; right: 18dp;\n  color: $softColor; font-size: 12dp;\n}\n\nvalue {\n  left: 73dp; top: 51dp; right: 18dp;\n  color: $textColor; font-size: 17dp; font-weight: 700;\n}\n\nprogress {\n  left: 73dp; right: 18dp; top: 76dp; height: 3dp;\n  color: $serviceColor; background: #FFFFFF33; border-radius: 2dp;\n}\n\narrow { right: 12dp; top: 50%; size: 18dp; color: $softColor; }\n";
    final float cardAspectRatio;
    final int cardHeight;
    final BlockComponents components;
    final List<Rule> rules;
    final String source;

    private static int clamp(int i, int i2, int i3) {
        return i < i2 ? i2 : i > i3 ? i3 : i;
    }

    private BlockCss(String str, List<Rule> list, int i, float f, BlockComponents blockComponents) {
        this.source = str;
        this.rules = list;
        this.cardHeight = i;
        this.cardAspectRatio = f;
        this.components = blockComponents;
    }

    static BlockCss compile(String str) throws Error {
        return compile(str, BlockComponents.defaults());
    }

    static BlockCss compile(String str, BlockComponents blockComponents) throws Error {
        int i;
        float f;
        boolean z;
        String str2 = "";
        String strTrim = str == null ? "" : str.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (strTrim.length() == 0) {
            return null;
        }
        BlockComponents blockComponentsDefaults = blockComponents == null ? BlockComponents.defaults() : blockComponents;
        if (strTrim.length() > MAX_SOURCE) {
            throw new Error(0, "CSS длиннее 16384 знаков");
        }
        String strComments = comments(strTrim);
        ArrayList arrayList = new ArrayList();
        int i2 = 0;
        while (i2 < strComments.length()) {
            while (i2 < strComments.length() && Character.isWhitespace(strComments.charAt(i2))) {
                i2++;
            }
            if (i2 >= strComments.length()) {
                break;
            }
            int iIndexOf = strComments.indexOf(123, i2);
            if (iIndexOf < 0) {
                throw errorAt(strComments, i2, "не хватает {");
            }
            int i3 = iIndexOf + 1;
            int iClosingBrace = closingBrace(strComments, i3);
            if (iClosingBrace < 0) {
                throw errorAt(strComments, iIndexOf, "не хватает }");
            }
            String lowerCase = strComments.substring(i2, iIndexOf).trim().toLowerCase(Locale.US);
            LinkedHashMap<String, String> linkedHashMapDeclarations = declarations(strComments.substring(i3, iClosingBrace), strComments, i3);
            validateAnimation(linkedHashMapDeclarations, strComments, i2);
            String[] strArrSplit = lowerCase.split(lowerCase.indexOf(44) >= 0 ? "\\s*,\\s*" : "\\s+");
            int length = strArrSplit.length;
            int i4 = 0;
            while (i4 < length) {
                String str3 = strArrSplit[i4];
                if (!str3.matches("#?[a-z][a-z0-9_-]{0,31}(?::[a-z][a-z0-9_-]{0,31})?")) {
                    throw errorAt(strComments, i2, "неизвестный селектор «" + lowerCase + "»");
                }
                int iIndexOf2 = str3.indexOf(58);
                String str4 = str2;
                String strSubstring = iIndexOf2 < 0 ? str3 : str3.substring(0, iIndexOf2);
                String strSubstring2 = iIndexOf2 < 0 ? str4 : str3.substring(iIndexOf2 + 1);
                if (strSubstring2.length() > 0 && !strSubstring2.matches("playing|paused|stopped|has-track|event|track-changed|state-changed|play|pause|resume|stop")) {
                    throw errorAt(strComments, i2, "неизвестное состояние «" + strSubstring2 + "»");
                }
                if (strSubstring.startsWith("#") && blockComponentsDefaults.get(strSubstring) != null) {
                    strSubstring = strSubstring.substring(1);
                }
                int i5 = length;
                String str5 = strTrim;
                arrayList.add(new Rule(strSubstring, strSubstring2, new LinkedHashMap(linkedHashMapDeclarations), arrayList.size()));
                if (arrayList.size() > 64) {
                    throw errorAt(strComments, i2, "слишком много селекторов");
                }
                i4++;
                str2 = str4;
                length = i5;
                strTrim = str5;
            }
            i2 = iClosingBrace + 1;
        }
        String str6 = strTrim;
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Rule rule = (Rule) it.next();
            if (blockComponentsDefaults.get(rule.selector) == null) {
                Iterator it2 = arrayList.iterator();
                while (true) {
                    if (!it2.hasNext()) {
                        z = false;
                        break;
                    }
                    Rule rule2 = (Rule) it2.next();
                    if (rule2.selector.equals(rule.selector) && rule2.values.containsKey("type")) {
                        z = true;
                        break;
                    }
                }
                if (!rule.selector.startsWith("#") || !z) {
                    throw new Error(0, "компонент «" + rule.selector + "» не объявлен");
                }
            }
        }
        Rule rule3 = (Rule)cascade(arrayList, null).get("card");
        if (rule3 != null) {
            String str7 = rule3.values.get("height");
            int iClamp = str7 != null ? clamp(Math.round(dp(str7, 106.0f)), 64, 1600) : 106;
            String str8 = rule3.values.get("aspect-ratio");
            float fAspect = aspect(str8);
            if (str8 != null && !"auto".equalsIgnoreCase(str8.trim()) && fAspect <= 0.0f) {
                throw new Error(0, "неверный aspect-ratio «" + str8 + "»");
            }
            i = iClamp;
            f = fAspect;
        } else {
            i = 106;
            f = 0.0f;
        }
        return new BlockCss(str6, Collections.unmodifiableList(arrayList), i, f, blockComponentsDefaults);
    }

    int heightFor(float f) {
        float f2 = this.cardAspectRatio;
        return f2 <= 0.0f ? this.cardHeight : clamp(Math.round(f / f2), 64, 1600);
    }

    List<BlockScript.Node> nodes(float f, float f2) {
        return nodes(f, f2, null);
    }

    List<BlockScript.Node> nodes(float f, float f2, BlockScript.Context context) throws RuntimeException {
        Map mapUnmodifiableMap;
        ArrayList arrayList = new ArrayList();
        Map<String, Rule> mapCascade = cascade(this.rules, context);
        if (context == null) {
            mapUnmodifiableMap = Collections.EMPTY_MAP;
        } else {
            mapUnmodifiableMap = Collections.unmodifiableMap(new LinkedHashMap(context.numbers));
        }
        for (Rule rule : mapCascade.values()) {
            if (!"none".equalsIgnoreCase(rule.values.get("display")) && !"hidden".equalsIgnoreCase(rule.values.get("visibility"))) {
                String strKind = kind(rule);
                if (strKind.length() != 0) {
                    LinkedHashMap linkedHashMap = new LinkedHashMap();
                    float f3 = f;
                    float f4 = f2;
                    place(rule.values, linkedHashMap, f3, f4, strKind, rule.selector);
                    appearance(rule, strKind, linkedHashMap);
                    linkedHashMap.put("transitionid", rule.selector);
                    arrayList.add(new Made(new BlockScript.Node(0, strKind, Collections.unmodifiableMap(linkedHashMap), mapUnmodifiableMap), integer(rule.values.get("z-index"), rule.order), rule.order));
                    f = f3;
                    f2 = f4;
                }
            }
        }
        Collections.sort(arrayList, new Comparator<Made>() { // from class: cpb.BlockCss.1
            @Override // java.util.Comparator
            public int compare(Made made, Made made2) {
                if (made.z != made2.z) {
                    return made.z < made2.z ? -1 : 1;
                }
                return made.order - made2.order;
            }
        });
        ArrayList arrayList2 = new ArrayList(arrayList.size());
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            arrayList2.add(((Made) it.next()).node);
        }
        return arrayList2;
    }

    private static Map<String, Rule> cascade(List<Rule> list, BlockScript.Context context) {
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        for (Rule rule : list) {
            if (active(rule.state, context)) {
                Rule rule2 = (Rule) linkedHashMap.get(rule.selector);
                if (rule2 == null) {
                    rule2 = new Rule(rule.selector, "", new LinkedHashMap(), rule.order);
                    linkedHashMap.put(rule.selector, rule2);
                }
                rule2.values.putAll(rule.values);
                if (animationDeclared(rule.values)) {
                    String strAnimationClock = animationClock(rule.state, context);
                    if (strAnimationClock.length() > 0) {
                        rule2.animationClock = strAnimationClock;
                    }
                }
            }
        }
        return linkedHashMap;
    }

    private static boolean animationDeclared(Map<String, String> map) {
        return map.containsKey("animation") || map.containsKey("animation-name");
    }

    private static void validateAnimation(Map<String, String> map, String str, int i) throws Error {
        String str2 = map.get("animation-name");
        String str3 = map.get("animation");
        if (str2 == null && str3 != null) {
            String[] strArrSplit = str3.trim().toLowerCase(Locale.US).split("\\s+");
            str2 = strArrSplit.length == 0 ? "" : strArrSplit[0];
        }
        if (str2 != null && str2.length() > 0 && !"none".equalsIgnoreCase(str2) && !BlockAnimation.animationName(str2.toLowerCase(Locale.US))) {
            throw errorAt(str, i, "неизвестная анимация «" + str2 + "»");
        }
        String str4 = map.get("animation-clock");
        if (str4 != null && !"local".equals(str4) && !"event".equals(str4) && !"track".equals(str4) && !"state".equals(str4)) {
            throw errorAt(str, i, "animation-clock: local, event, track или state");
        }
    }

    private static String animationClock(String str, BlockScript.Context context) {
        return "track-changed".equals(str) ? "track" : ("playing".equals(str) || "paused".equals(str) || "stopped".equals(str) || "state-changed".equals(str)) ? "state" : "play".equals(str) ? activeNumber(context, "play_event") ? "event" : "state" : "pause".equals(str) ? activeNumber(context, "pause_event") ? "event" : "state" : "stop".equals(str) ? activeNumber(context, "stop_event") ? "event" : "state" : (str == null || str.length() <= 0) ? "" : "event";
    }

    private static boolean active(String str, BlockScript.Context context) {
        if (str == null || str.length() == 0) {
            return true;
        }
        if (context == null) {
            return false;
        }
        if ("has-track".equals(str)) {
            str = "has_track";
        } else if ("track-changed".equals(str)) {
            str = "track_changed";
        } else if ("state-changed".equals(str)) {
            str = "state_changed";
        } else if ("event".equals(str)) {
            str = "event_active";
        } else {
            if ("play".equals(str)) {
                return activeNumber(context, "play_event") || activeNumber(context, "playing");
            }
            if ("pause".equals(str)) {
                return activeNumber(context, "pause_event") || activeNumber(context, "paused");
            }
            if ("resume".equals(str)) {
                str = "resume_event";
            } else if ("stop".equals(str)) {
                return activeNumber(context, "stop_event") || activeNumber(context, "stopped");
            }
        }
        return activeNumber(context, str);
    }

    private static boolean activeNumber(BlockScript.Context context, String str) {
        Double d = context.numbers.get(str);
        return d != null && Math.abs(d.doubleValue()) > 1.0E-6d;
    }

    private String kind(Rule rule) throws RuntimeException {
        BlockComponents.Def def = this.components.get(rule.selector);
        String lowerCase = value(rule.values, "type", def == null ? "rect" : def.type).toLowerCase(Locale.US);
        return ("rect".equals(lowerCase) && gradient(rule.values.containsKey("background-image") ? rule.values.get("background-image") : rule.values.get("background"))) ? "gradient" : lowerCase.matches("rect|gradient|text|pill|image|progress|arrow") ? lowerCase : "";
    }

    private static void place(Map<String, String> map, Map<String, String> map2, float f, float f2, String str, String str2) {
        float fMin;
        float fMin2;
        float length = length(map.get("left"), f, 0.0f);
        float length2 = length(map.get("top"), f2, 0.0f);
        boolean zContainsKey = map.containsKey("width");
        boolean zContainsKey2 = map.containsKey("height");
        boolean zEquals = "card".equals(str2);
        if (zContainsKey) {
            fMin = length(map.get("width"), f, 0.0f);
        } else {
            fMin = zEquals ? f : 0.0f;
        }
        if (zContainsKey2) {
            fMin2 = length(map.get("height"), f2, 0.0f);
        } else {
            fMin2 = zEquals ? f2 : 0.0f;
        }
        if (map.containsKey("right")) {
            float length3 = length(map.get("right"), f, 0.0f);
            if (zContainsKey) {
                length = (f - length3) - fMin;
            } else if ("arrow".equals(str)) {
                length = f - length3;
            } else {
                fMin = Math.max(0.0f, (f - length) - length3);
            }
        }
        if (map.containsKey("bottom")) {
            float length4 = length(map.get("bottom"), f2, 0.0f);
            if (zContainsKey2) {
                length2 = (f2 - length4) - fMin2;
            } else {
                fMin2 = Math.max(0.0f, (f2 - length2) - length4);
            }
        }
        String[] strArrBox = box(map.get("margin"));
        if (strArrBox != null) {
            float length5 = length(strArrBox[0], f2, 0.0f);
            float length6 = length(strArrBox[1], f, 0.0f);
            float length7 = length(strArrBox[2], f2, 0.0f);
            float length8 = length(strArrBox[3], f, 0.0f);
            length += length8;
            length2 += length5;
            if (fMin > 0.0f) {
                fMin = Math.max(0.0f, (fMin - length8) - length6);
            }
            if (fMin2 > 0.0f) {
                fMin2 = Math.max(0.0f, (fMin2 - length5) - length7);
            }
        }
        if (map.containsKey("min-width")) {
            fMin = Math.max(fMin, length(map.get("min-width"), f, 0.0f));
        }
        if (map.containsKey("max-width")) {
            fMin = Math.min(fMin, length(map.get("max-width"), f, fMin));
        }
        if (map.containsKey("min-height")) {
            fMin2 = Math.max(fMin2, length(map.get("min-height"), f2, 0.0f));
        }
        if (map.containsKey("max-height")) {
            fMin2 = Math.min(fMin2, length(map.get("max-height"), f2, fMin2));
        }
        map2.put("x", decimal((length * 1000.0f) / Math.max(1.0f, f)));
        map2.put("y", decimal((length2 * 1000.0f) / Math.max(1.0f, f2)));
        if (fMin > 0.0f) {
            map2.put("w", decimal((fMin * 1000.0f) / Math.max(1.0f, f)));
        }
        if (fMin2 > 0.0f) {
            map2.put("h", decimal((fMin2 * 1000.0f) / Math.max(1.0f, f2)));
        }
    }

    private void appearance(Rule rule, String str, Map<String, String> map) {
        String str2;
        String strRemove;
        Map<String, String> map2 = rule.values;
        BlockComponents.Def def = this.components.get(rule.selector);
        if (def != null) {
            map.putAll(def.attrs);
        }
        if (map2.containsKey("background-image") && gradient(map2.get("background-image"))) {
            str2 = map2.get("background-image");
        } else if (map2.containsKey("background")) {
            str2 = map2.get("background");
        } else {
            str2 = map2.get("background-color");
        }
        if ("gradient".equals(str) || ("card".equals(rule.selector) && gradient(str2))) {
            Gradient gradient = parseGradient(str2);
            map.put("from", gradient.from);
            map.put("to", gradient.to);
            map.put("angle", gradient.angle);
            if (!"linear".equals(gradient.kind)) {
                map.put("gradient", gradient.kind);
                map.put("cx", gradient.cx);
                map.put("cy", gradient.cy);
            }
            if (gradient.mid.length() > 0) {
                map.put("mid", gradient.mid);
            }
        } else if (str2 != null) {
            map.put("back", cssColor(str2));
            if ("rect".equals(str)) {
                map.put("color", cssColor(str2));
            }
        }
        copyColor(map2, map, "color", "color");
        copyLength(map2, map, "border-radius", "radius");
        copy(map2, map, "opacity", "opacity");
        if (map2.containsKey("opacity")) {
            try {
                map.put("alpha", decimal(Double.parseDouble(map2.get("opacity")) * 100.0d));
            } catch (Throwable unused) {
            }
        }
        String str3 = map2.get("transform");
        if (str3 != null) {
            transform(str3, map);
        }
        if (map2.containsKey("transform-origin")) {
            transformOrigin(map2.get("transform-origin"), map);
        }
        String str4 = map2.get("border");
        if (str4 != null) {
            String[] strArrSplit = str4.trim().split("\\s+");
            if (strArrSplit.length > 0) {
                map.put("stroke", stripDp(strArrSplit[0]));
            }
            if (strArrSplit.length > 1) {
                map.put("strokecolor", cssColor(strArrSplit[strArrSplit.length - 1]));
            }
        }
        copyLength(map2, map, "border-width", "stroke");
        copyColor(map2, map, "border-color", "strokecolor");
        copy(map2, map, "content", "value");
        copy(map2, map, "source", "source");
        copy(map2, map, "filter", "filter");
        copy(map2, map, "box-shadow", "shadow");
        copy(map2, map, "text-shadow", "textshadow");
        copy(map2, map, "overflow", "overflow");
        copy(map2, map, "clip-path", "clippath");
        copy(map2, map, "text-overflow", "textoverflow");
        copyLength(map2, map, "line-height", "lineheight");
        copy(map2, map, "white-space", "whitespace");
        copy(map2, map, "object-fit", "objectfit");
        if (map2.containsKey("object-position")) {
            objectPosition(map2.get("object-position"), map);
        }
        copyLength(map2, map, "font-size", "size");
        copy(map2, map, "font-weight", "weight");
        copy(map2, map, "font-family", "fontfamily");
        copy(map2, map, "text-decoration", "textdecoration");
        if ("italic".equalsIgnoreCase(map2.get("font-style"))) {
            map.put("italic", "1");
        }
        copyLength(map2, map, "letter-spacing", "letter");
        if ("uppercase".equalsIgnoreCase(map2.get("text-transform"))) {
            map.put("uppercase", "1");
        }
        copy(map2, map, "text-align", "align");
        copyLength(map2, map, "size", "size");
        copyLength(map2, map, "stroke-width", "stroke");
        copyLength(map2, map, "padding-x", "padx");
        copyLength(map2, map, "padding-y", "pady");
        if (map2.containsKey("padding")) {
            padding(map2.get("padding"), map);
        }
        copy(map2, map, "animation", "animation");
        copy(map2, map, "animation-name", "animationname");
        copy(map2, map, "animation-duration", "animationduration");
        copy(map2, map, "animation-delay", "animationdelay");
        copy(map2, map, "animation-timing-function", "animationtiming");
        copy(map2, map, "animation-iteration-count", "animationiterations");
        copy(map2, map, "animation-direction", "animationdirection");
        copyLength(map2, map, "animation-distance", "animationdistance");
        copy(map2, map, "animation-intensity", "animationintensity");
        copy(map2, map, "animation-phase", "animationphase");
        copy(map2, map, "animation-clock", "animationclock");
        if (!map2.containsKey("animation-clock") && rule.animationClock.length() > 0) {
            map.put("animationclock", rule.animationClock);
        }
        copy(map2, map, "transition", "transition");
        copy(map2, map, "transition-property", "transitionproperty");
        copy(map2, map, "transition-duration", "transitionduration");
        copy(map2, map, "transition-timing-function", "transitiontiming");
        copy(map2, map, "transition-delay", "transitiondelay");
        copy(map2, map, "effect", "effect");
        copyColor(map2, map, "effect-color", "effectcolor");
        copy(map2, map, "effect-strength", "effectstrength");
        copyLength(map2, map, "effect-size", "effectsize");
        copy(map2, map, "effect-speed", "effectspeed");
        String str5 = map2.get("outline");
        if (str5 != null) {
            String[] strArrSplit2 = str5.trim().split("\\s+");
            if (strArrSplit2.length > 0) {
                map.put("outlinewidth", stripDp(strArrSplit2[0]));
            }
            if (strArrSplit2.length > 1) {
                map.put("outlinecolor", cssColor(strArrSplit2[strArrSplit2.length - 1]));
            }
        }
        copyLength(map2, map, "outline-width", "outlinewidth");
        copyColor(map2, map, "outline-color", "outlinecolor");
        copyLength(map2, map, "outline-offset", "outlineoffset");
        if (("text".equals(str) || "pill".equals(str)) && map2.containsKey("right") && (strRemove = map.remove("w")) != null) {
            map.put("maxw", strRemove);
        }
    }

    private static LinkedHashMap<String, String> declarations(String str, String str2, int i) throws Error {
        LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<>();
        for (String str3 : split(str, ';')) {
            if (str3.trim().length() != 0) {
                int iIndexOf = str3.indexOf(58);
                if (iIndexOf <= 0) {
                    throw errorAt(str2, i, "свойство без двоеточия");
                }
                String lowerCase = str3.substring(0, iIndexOf).trim().toLowerCase(Locale.US);
                String strUnquote = unquote(str3.substring(iIndexOf + 1).trim());
                if (!lowerCase.matches("[a-z][a-z0-9-]{0,31}") || strUnquote.length() == 0) {
                    throw errorAt(str2, i, "неверное свойство");
                }
                if (!property(lowerCase)) {
                    throw errorAt(str2, i, "свойство «" + lowerCase + "» пока не поддерживается");
                }
                linkedHashMap.put(lowerCase, strUnquote);
            }
        }
        return linkedHashMap;
    }

    private static List<String> split(String str, char c) {
        ArrayList arrayList = new ArrayList();
        int i = 0;
        int i2 = 0;
        char c2 = 0;
        int i3 = 0;
        while (i < str.length()) {
            char cCharAt = str.charAt(i);
            if (c2 != 0) {
                if (cCharAt == '\\') {
                    i++;
                } else if (cCharAt == c2) {
                    c2 = 0;
                }
            } else if (cCharAt == '\'' || cCharAt == '\"') {
                c2 = cCharAt;
            } else if (cCharAt == '(') {
                i3++;
            } else if (cCharAt == ')') {
                i3--;
            } else if (cCharAt == c && i3 == 0) {
                arrayList.add(str.substring(i2, i));
                i2 = i + 1;
            }
            i++;
        }
        arrayList.add(str.substring(i2));
        return arrayList;
    }

    private static int closingBrace(String str, int i) {
        char c = 0;
        while (i < str.length()) {
            char cCharAt = str.charAt(i);
            if (c != 0) {
                if (cCharAt == '\\') {
                    i++;
                } else if (cCharAt == c) {
                    c = 0;
                }
            } else if (cCharAt == '\'' || cCharAt == '\"') {
                c = cCharAt;
            } else if (cCharAt == '}') {
                return i;
            }
            i++;
        }
        return -1;
    }

    /* JADX WARN: Removed duplicated region for block: B:50:0x011c  */
    /* JADX WARN: Removed duplicated region for block: B:51:0x0127  */
    /* JADX WARN: Removed duplicated region for block: B:54:0x0131  */
    /* JADX WARN: Removed duplicated region for block: B:55:0x013e  */
    /* JADX WARN: Removed duplicated region for block: B:58:0x0145  */
    /* JADX WARN: Removed duplicated region for block: B:59:0x0150  */
    /* JADX WARN: Removed duplicated region for block: B:65:0x0168  */
    /* JADX WARN: Removed duplicated region for block: B:67:0x016c  */
    /* JADX WARN: Removed duplicated region for block: B:73:0x017f  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private static Gradient parseGradient(String source){
        if(source==null)return new Gradient("linear","0","50","50","$serviceColor","","#00000000");
        String lower=source.trim().toLowerCase(Locale.US);boolean radial=lower.startsWith("radial-gradient("),sweep=lower.startsWith("conic-gradient(");
        List<String> stops=split(source.substring(source.indexOf('(')+1,source.lastIndexOf(')')),',');
        String angle="0",cx="50",cy="50";int start=0;
        if(!stops.isEmpty()){
            String head=stops.get(0).trim().toLowerCase(Locale.US);
            if(radial||sweep){int at=head.indexOf(" at ");if(at>=0){String[] point=head.substring(at+4).trim().split("\\s+");if(point.length>0)cx=percent(point[0],50);if(point.length>1)cy=percent(point[1],50);}
                if(at>=0||head.startsWith("circle")||head.startsWith("ellipse")||head.startsWith("from ")){start=1;if(sweep&&head.startsWith("from "))angle=head.substring(5,at<0?head.length():at).replace("deg","").trim();}
            }else if(head.endsWith("deg")){angle=head.substring(0,head.length()-3).trim();start=1;}
        }
        String from=start<stops.size()?colorToken(stops.get(start)):"$serviceColor",mid=stops.size()>start+2?colorToken(stops.get(start+1)):"",to=stops.size()>start+2?colorToken(stops.get(start+2)):stops.size()>start+1?colorToken(stops.get(start+1)):"#00000000";
        return new Gradient(sweep?"sweep":radial?"radial":"linear",angle,cx,cy,from,mid,to);
    }

    private static String colorToken(String str) {
        String strTrim = str.trim();
        if (strTrim.toLowerCase(Locale.US).startsWith("rgb")) {
            return cssColor(strTrim);
        }
        String[] strArrSplit = strTrim.split("\\s+");
        return strArrSplit.length == 0 ? "#00000000" : cssColor(strArrSplit[0]);
    }

    private static boolean gradient(String str) {
        if (str == null || !str.trim().endsWith(")")) {
            return false;
        }
        String lowerCase = str.trim().toLowerCase(Locale.US);
        return lowerCase.startsWith("linear-gradient(") || lowerCase.startsWith("radial-gradient(") || lowerCase.startsWith("conic-gradient(");
    }

    private static String percent(String str, float f) {
        String lowerCase = str.trim().toLowerCase(Locale.US);
        try {
            if (lowerCase.endsWith("%")) {
                lowerCase = lowerCase.substring(0, lowerCase.length() - 1);
            }
            return decimal(Math.max(0.0f, Math.min(100.0f, Float.parseFloat(lowerCase))));
        } catch (Throwable unused) {
            return decimal(f);
        }
    }

    private static void padding(String str, Map<String, String> map) {
        String[] strArrBox = box(str);
        if (strArrBox == null) {
            return;
        }
        String strStripDp = stripDp(strArrBox[0]);
        String strStripDp2 = stripDp(strArrBox[1]);
        String strStripDp3 = stripDp(strArrBox[2]);
        String strStripDp4 = stripDp(strArrBox[3]);
        map.put("padtop", strStripDp);
        map.put("padright", strStripDp2);
        map.put("padbottom", strStripDp3);
        map.put("padleft", strStripDp4);
        if (strStripDp4.equals(strStripDp2)) {
            map.put("padx", strStripDp4);
        }
        if (strStripDp.equals(strStripDp3)) {
            map.put("pady", strStripDp);
        }
    }

    private static String[] box(String str) {
        if (str == null) {
            return null;
        }
        String[] strArrSplit = str.trim().split("\\s+");
        if (strArrSplit.length < 1 || strArrSplit.length > 4) {
            return null;
        }
        String str2 = strArrSplit[0];
        String str3 = strArrSplit.length > 1 ? strArrSplit[1] : str2;
        return new String[]{str2, str3, strArrSplit.length > 2 ? strArrSplit[2] : str2, strArrSplit.length > 3 ? strArrSplit[3] : str3};
    }

    private static void transform(String str, Map<String, String> map) {
        Matcher matcher = Pattern.compile("([a-zA-Z]+)\\s*\\(([^)]*)\\)").matcher(str);
        while (matcher.find()) {
            String lowerCase = matcher.group(1).toLowerCase(Locale.US);
            String[] strArrSplit = matcher.group(2).trim().split("\\s*,\\s*|\\s+");
            if ("rotate".equals(lowerCase) && strArrSplit.length > 0) {
                map.put("rotation", strArrSplit[0].replace("deg", "").trim());
            } else if ("translate".equals(lowerCase) && strArrSplit.length > 0) {
                map.put("translatex", stripDp(strArrSplit[0]));
                map.put("translatey", stripDp(strArrSplit.length > 1 ? strArrSplit[1] : "0"));
            } else if ("translatex".equals(lowerCase) && strArrSplit.length > 0) {
                map.put("translatex", stripDp(strArrSplit[0]));
            } else if ("translatey".equals(lowerCase) && strArrSplit.length > 0) {
                map.put("translatey", stripDp(strArrSplit[0]));
            } else if ("scale".equals(lowerCase) && strArrSplit.length > 0) {
                map.put("scalex", strArrSplit[0]);
                map.put("scaley", strArrSplit.length > 1 ? strArrSplit[1] : strArrSplit[0]);
            } else if ("scalex".equals(lowerCase) && strArrSplit.length > 0) {
                map.put("scalex", strArrSplit[0]);
            } else if ("scaley".equals(lowerCase) && strArrSplit.length > 0) {
                map.put("scaley", strArrSplit[0]);
            }
        }
    }

    private static void objectPosition(String str, Map<String, String> map) {
        positionPair(str, map, "objectx", "objecty");
    }

    private static void transformOrigin(String str, Map<String, String> map) {
        positionPair(str, map, "originx", "originy");
    }

    private static void positionPair(String str, Map<String, String> map, String str2, String str3) {
        String[] strArrSplit = str.trim().toLowerCase(Locale.US).split("\\s+");
        if (strArrSplit.length == 0) {
            return;
        }
        String str4 = strArrSplit[0];
        String str5 = strArrSplit.length > 1 ? strArrSplit[1] : "";
        boolean z = "top".equals(str4) || "bottom".equals(str4);
        boolean z2 = "left".equals(str5) || "right".equals(str5);
        if (strArrSplit.length == 1 && z) {
            map.put(str2, "50");
            map.put(str3, position(str4, 50.0f));
            return;
        }
        if (z || z2) {
            if (str5.length() == 0) {
                str5 = "50%";
            }
            map.put(str2, position(str5, 50.0f));
            map.put(str3, position(str4, 50.0f));
            return;
        }
        map.put(str2, position(str4, 50.0f));
        if (str5.length() == 0) {
            str5 = "50%";
        }
        map.put(str3, position(str5, 50.0f));
    }

    private static String position(String str, float f) {
        if ("left".equals(str) || "top".equals(str)) {
            return "0";
        }
        if ("center".equals(str)) {
            return "50";
        }
        if ("right".equals(str) || "bottom".equals(str)) {
            return "100";
        }
        return percent(str, f);
    }

    private static float length(String str, float f, float f2) {
        if (str != null) {
            String lowerCase = str.trim().toLowerCase(Locale.US);
            try {
                if (!lowerCase.startsWith("calc(") || !lowerCase.endsWith(")")) {
                    return lowerCase.endsWith("%") ? (f * Float.parseFloat(lowerCase.substring(0, lowerCase.length() - 1))) / 100.0f : Float.parseFloat(stripDp(lowerCase));
                }
                String strReplace = lowerCase.substring(5, lowerCase.length() - 1).replace(" ", "");
                float length = 0.0f;
                int i = 0;
                for (int i2 = 1; i2 <= strReplace.length(); i2++) {
                    if (i2 == strReplace.length() || strReplace.charAt(i2) == '+' || strReplace.charAt(i2) == '-') {
                        String strSubstring = strReplace.substring(i, i2);
                        if (strSubstring.length() > 0) {
                            float f3 = strSubstring.charAt(0) == '-' ? -1.0f : 1.0f;
                            if (strSubstring.charAt(0) == '+' || strSubstring.charAt(0) == '-') {
                                strSubstring = strSubstring.substring(1);
                            }
                            length += f3 * length(strSubstring, f, 0.0f);
                        }
                        i = i2;
                    }
                }
                return length;
            } catch (Throwable unused) {
            }
        }
        return f2;
    }

    private static float dp(String str, float f) {
        return length(str, 100.0f, f);
    }

    private static float aspect(String str) {
        float f;
        if (str == null) {
            return 0.0f;
        }
        String strReplace = str.trim().toLowerCase(Locale.US).replace(" ", "");
        if ("auto".equals(strReplace)) {
            return 0.0f;
        }
        try {
            int iIndexOf = strReplace.indexOf(47);
            if (iIndexOf < 0) {
                iIndexOf = strReplace.indexOf(58);
            }
            if (iIndexOf > 0) {
                float f2 = Float.parseFloat(strReplace.substring(0, iIndexOf));
                float f3 = Float.parseFloat(strReplace.substring(iIndexOf + 1));
                f = f3 == 0.0f ? 0.0f : f2 / f3;
            } else {
                f = Float.parseFloat(strReplace);
            }
        } catch (Throwable unused) {
            return 0;
        }
        if (f < 0.1f || f > 10.0f) {
            return 0.0f;
        }
        return f;
    }

    private static String stripDp(String str) {
        String lowerCase = str.trim().toLowerCase(Locale.US);
        return (lowerCase.endsWith("dp") || lowerCase.endsWith("px")) ? lowerCase.substring(0, lowerCase.length() - 2).trim() : lowerCase;
    }

    private static void copy(Map<String, String> map, Map<String, String> map2, String str, String str2) {
        if (map.containsKey(str)) {
            map2.put(str2, map.get(str));
        }
    }

    private static void copyColor(Map<String, String> map, Map<String, String> map2, String str, String str2) {
        if (map.containsKey(str)) {
            map2.put(str2, cssColor(map.get(str)));
        }
    }

    private static void copyLength(Map<String, String> map, Map<String, String> map2, String str, String str2) {
        if (map.containsKey(str)) {
            String strTrim = map.get(str).trim();
            map2.put(str2, strTrim.endsWith("%") ? "999" : stripDp(strTrim));
        }
    }

    private static String value(Map<String, String> map, String str, String str2) {
        String str3 = map.get(str);
        return str3 == null ? str2 : str3;
    }

    private static int integer(String str, int i) {
        try {
            return Integer.parseInt(str);
        } catch (Throwable unused) {
            return i;
        }
    }

    private static String decimal(double d) {
        return String.format(Locale.US, "%.4f", Double.valueOf(d));
    }

    private static String unquote(String str) {
        return str.length() >= 2 ? ((str.charAt(0) == '\"' && str.charAt(str.length() - 1) == '\"') || (str.charAt(0) == '\'' && str.charAt(str.length() - 1) == '\'')) ? str.substring(1, str.length() - 1) : str : str;
    }

    private static String cssColor(String str) {
        String strTrim = str.trim();
        if (strTrim.startsWith("$")) {
            return strTrim;
        }
        if ("transparent".equalsIgnoreCase(strTrim)) {
            return "#00000000";
        }
        if ("white".equalsIgnoreCase(strTrim)) {
            return "#FFFFFFFF";
        }
        if ("black".equalsIgnoreCase(strTrim)) {
            return "#FF000000";
        }
        if (strTrim.matches("#[0-9a-fA-F]{3}")) {
            return "#FF" + strTrim.substring(1, 2) + strTrim.substring(1, 2) + strTrim.substring(2, 3) + strTrim.substring(2, 3) + strTrim.substring(3, 4) + strTrim.substring(3, 4);
        }
        if (strTrim.matches("#[0-9a-fA-F]{4}")) {
            return "#" + strTrim.substring(4, 5) + strTrim.substring(4, 5) + strTrim.substring(1, 2) + strTrim.substring(1, 2) + strTrim.substring(2, 3) + strTrim.substring(2, 3) + strTrim.substring(3, 4) + strTrim.substring(3, 4);
        }
        if (strTrim.matches("#[0-9a-fA-F]{6}")) {
            return "#FF" + strTrim.substring(1);
        }
        if (strTrim.matches("#[0-9a-fA-F]{8}")) {
            return "#" + strTrim.substring(7, 9) + strTrim.substring(1, 7);
        }
        String lowerCase = strTrim.toLowerCase(Locale.US);
        if ((!lowerCase.startsWith("rgb(") && !lowerCase.startsWith("rgba(")) || !lowerCase.endsWith(")")) {
            return strTrim;
        }
        List<String> listSplit = split(lowerCase.substring(lowerCase.indexOf(40) + 1, lowerCase.length() - 1), ',');
        if (listSplit.size() < 3) {
            return strTrim;
        }
        try {
            int iRound = Math.round(Float.parseFloat(listSplit.get(0).trim()));
            int iClamp = 255;
            int iClamp2 = clamp(iRound, 0, 255);
            int iClamp3 = clamp(Math.round(Float.parseFloat(listSplit.get(1).trim())), 0, 255);
            int iClamp4 = clamp(Math.round(Float.parseFloat(listSplit.get(2).trim())), 0, 255);
            if (listSplit.size() > 3) {
                iClamp = clamp(Math.round(Float.parseFloat(listSplit.get(3).trim()) * 255.0f), 0, 255);
            }
            return String.format(Locale.US, "#%02X%02X%02X%02X", Integer.valueOf(iClamp), Integer.valueOf(iClamp2), Integer.valueOf(iClamp3), Integer.valueOf(iClamp4));
        } catch (Throwable unused) {
            return strTrim;
        }
    }

    private static boolean property(String str) {
        return "left,right,top,bottom,width,height,min-width,max-width,min-height,max-height,margin,aspect-ratio,z-index,display,visibility,position,overflow,clip-path,color,background,background-color,background-image,border,border-width,border-color,border-style,border-radius,outline,outline-width,outline-color,outline-offset,opacity,transform,transform-origin,type,content,source,filter,box-shadow,text-shadow,padding,text-overflow,white-space,object-fit,object-position,font-size,font-weight,font-family,font-style,line-height,letter-spacing,text-transform,text-align,text-decoration,size,stroke-width,padding-x,padding-y,animation,animation-name,animation-duration,animation-delay,animation-timing-function,animation-iteration-count,animation-direction,animation-distance,animation-intensity,animation-phase,animation-clock,transition,transition-property,transition-duration,transition-timing-function,transition-delay,effect,effect-color,effect-strength,effect-size,effect-speed,".contains(str + ",");
    }

    private static String comments(String str) throws Error {
        int i;
        StringBuilder sb = new StringBuilder();
        int i2 = 0;
        while (i2 < str.length()) {
            int i3 = i2 + 1;
            if (i3 < str.length() && str.charAt(i2) == '/' && str.charAt(i3) == '*') {
                int iIndexOf = str.indexOf("*/", i2 + 2);
                if (iIndexOf < 0) {
                    throw errorAt(str, i2, "комментарий не закрыт");
                }
                while (true) {
                    i = iIndexOf + 2;
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
                i2 = i3;
            }
        }
        return sb.toString();
    }

    private static Error errorAt(String str, int i, String str2) {
        int i2 = 1;
        for (int i3 = 0; i3 < i && i3 < str.length(); i3++) {
            if (str.charAt(i3) == '\n') {
                i2++;
            }
        }
        return new Error(i2, str2);
    }

    static final class Error extends Exception {
        final int line;

        Error(int i,String str){super((i>0?"Line "+i+": ":"")+str);this.line=i;}
    }

    private static final class Rule {
        String animationClock = "";
        final int order;
        final String selector;
        final String state;
        final Map<String, String> values;

        Rule(String str, String str2, Map<String, String> map, int i) {
            this.selector = str;
            this.state = str2;
            this.values = map;
            this.order = i;
        }
    }

    private static final class Made {
        final BlockScript.Node node;
        final int order;
        final int z;

        Made(BlockScript.Node node, int i, int i2) {
            this.node = node;
            this.z = i;
            this.order = i2;
        }
    }

    private static final class Gradient {
        final String angle;
        final String cx;
        final String cy;
        final String from;
        final String kind;
        final String mid;
        final String to;

        Gradient(String str, String str2, String str3, String str4, String str5, String str6, String str7) {
            this.kind = str;
            this.angle = str2;
            this.cx = str3;
            this.cy = str4;
            this.from = str5;
            this.mid = str6;
            this.to = str7;
        }
    }
}

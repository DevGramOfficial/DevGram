package org.telegram.ui.Components.devgram.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* Adapted from the user-supplied Custom Profile plugin by @RoflPlugins. */
final class BlockTransition {
    private static final String COLORS = "color,back,from,mid,to,strokecolor,outlinecolor,effectcolor,";
    private static final String FILTERS = "blur,brightness,contrast,saturate,grayscale,sepia,invert,opacity,";
    private static final String NUMERIC = "x,y,w,h,maxw,radius,alpha,rotation,translatex,translatey,scalex,scaley,originx,originy,size,stroke,outlinewidth,outlineoffset,letter,lineheight,padx,pady,padtop,padright,padbottom,padleft,effectstrength,effectsize,effectspeed,";

    private static float bezier(float f, float f2, float f3) {
        float f4 = 1.0f - f;
        float f5 = 3.0f * f4;
        return (f4 * f5 * f * f2) + (f5 * f * f * f3) + (f * f * f);
    }

    private static float clamp(float f, float f2, float f3) {
        return f < f2 ? f2 : f > f3 ? f3 : f;
    }

    private BlockTransition() {
    }

    static Result apply(BlockScript.Node node, BlockScript.Context context, Map<String, State> map, String str, long j) {
        List<Part> listParts = parts(node.attrs);
        if (listParts.isEmpty()) {
            map.remove(str);
            return new Result(node, false);
        }
        Snapshot snapshotOf = Snapshot.of(node, context, listParts);
        State state = map.get(str);
        if (state == null) {
            map.put(str, new State(snapshotOf, snapshotOf, j));
            return new Result(node, false);
        }
        Snapshot snapshotAt = state.at(j);
        if (!state.to.same(snapshotOf)) {
            state = new State(snapshotAt, snapshotOf, j);
            map.put(str, state);
        }
        Snapshot snapshotAt2 = state.at(j);
        boolean zActive = state.active(j);
        if (!zActive && map.size() > 64) {
            map.clear();
        }
        return new Result(snapshotAt2.node(node), zActive);
    }

    private static List<Part> parts(Map<String, String> map) {
        String strTrim = value(map, "transition", "").trim();
        if ("none".equalsIgnoreCase(strTrim)) {
            return Collections.EMPTY_LIST;
        }
        if (strTrim.length() > 0) {
            ArrayList arrayList = new ArrayList();
            Iterator<String> it = split(strTrim, ',').iterator();
            while (it.hasNext()) {
                Part partShorthand = Part.shorthand(it.next());
                if (partShorthand != null) {
                    arrayList.add(partShorthand);
                }
            }
            return arrayList;
        }
        String strTrim2 = value(map, "transitionproperty", "").trim();
        String strTrim3 = value(map, "transitionduration", "").trim();
        if (strTrim2.length() == 0 && strTrim3.length() == 0) {
            return Collections.EMPTY_LIST;
        }
        if (strTrim2.length() == 0) {
            strTrim2 = "all";
        }
        List<String> listSplit = split(strTrim2, ',');
        if (strTrim3.length() == 0) {
            strTrim3 = "0s";
        }
        List<String> listSplit2 = split(strTrim3, ',');
        List<String> listSplit3 = split(value(map, "transitiontiming", "ease"), ',');
        List<String> listSplit4 = split(value(map, "transitiondelay", "0s"), ',');
        ArrayList arrayList2 = new ArrayList();
        for (int i = 0; i < listSplit.size(); i++) {
            arrayList2.add(new Part(listSplit.get(i).trim().toLowerCase(Locale.US), time(listSplit2.get(i % listSplit2.size()), 0L), listSplit3.get(i % listSplit3.size()).trim().toLowerCase(Locale.US), time(listSplit4.get(i % listSplit4.size()), 0L)));
        }
        return arrayList2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean selected(String str, List<Part> list) {
        Iterator<Part> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().matches(str)) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Part part(String str, List<Part> list) {
        Part part = null;
        for (Part part2 : list) {
            if ("all".equals(part2.property)) {
                part = part2;
            }
            if (part2.matchesExact(str)) {
                return part2;
            }
        }
        return part;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String cssProperty(String str) {
        if ("alpha".equals(str)) {
            return "opacity";
        }
        if ("radius".equals(str)) {
            return "border-radius";
        }
        if ("x".equals(str)) {
            return "left";
        }
        if ("y".equals(str)) {
            return "top";
        }
        if ("w".equals(str) || "maxw".equals(str)) {
            return "width";
        }
        if ("h".equals(str)) {
            return "height";
        }
        if ("size".equals(str)) {
            return "font-size";
        }
        if ("stroke".equals(str)) {
            return "border-width";
        }
        if ("strokecolor".equals(str)) {
            return "border-color";
        }
        if ("outlinewidth".equals(str)) {
            return "outline-width";
        }
        if ("outlinecolor".equals(str)) {
            return "outline-color";
        }
        if ("outlineoffset".equals(str)) {
            return "outline-offset";
        }
        if ("letter".equals(str)) {
            return "letter-spacing";
        }
        if ("lineheight".equals(str)) {
            return "line-height";
        }
        if (str.startsWith("pad")) {
            return "padding";
        }
        if ("back".equals(str) || "from".equals(str) || "mid".equals(str) || "to".equals(str) || "angle".equals(str) || "cx".equals(str) || "cy".equals(str)) {
            return "background";
        }
        if (str.startsWith("translate") || str.startsWith("scale") || "rotation".equals(str)) {
            return "transform";
        }
        return str.startsWith("origin") ? "transform-origin" : str.startsWith("effect") ? str.replace("effect", "effect-") : str;
    }

    private static List<String> split(String str, char c) {
        ArrayList arrayList = new ArrayList();
        int i = 0;
        int i2 = 0;
        for (int i3 = 0; i3 < str.length(); i3++) {
            char cCharAt = str.charAt(i3);
            if (cCharAt == '(') {
                i2++;
            } else if (cCharAt == ')') {
                i2--;
            } else if (cCharAt == c && i2 == 0) {
                arrayList.add(str.substring(i, i3).trim());
                i = i3 + 1;
            }
        }
        arrayList.add(str.substring(i).trim());
        return arrayList;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static List<String> words(String str) {
        ArrayList arrayList = new ArrayList();
        int i = 0;
        int i2 = 0;
        int i3 = 0;
        while (i <= str.length()) {
            char cCharAt = i == str.length() ? ' ' : str.charAt(i);
            if (cCharAt == '(') {
                i2++;
            } else if (cCharAt == ')') {
                i2--;
            }
            if (Character.isWhitespace(cCharAt) && i2 == 0) {
                if (i > i3) {
                    arrayList.add(str.substring(i3, i).trim());
                }
                i3 = i + 1;
            }
            i++;
        }
        return arrayList;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static long time(String str, long j) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.US);
        if (lowerCase.endsWith("ms")) {
            return Math.max(0L, Math.round(Double.parseDouble(lowerCase.substring(0, lowerCase.length() - 2))));
        }
        if (lowerCase.endsWith("s")) {
            return Math.max(0L, Math.round(Double.parseDouble(lowerCase.substring(0, lowerCase.length() - 1)) * 1000.0d));
        }
        return j;
    }

    private static String value(Map<String, String> map, String str, String str2) {
        String str3 = map.get(str);
        return str3 == null ? str2 : str3;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static float ease(float f, String str) {
        float fClamp = clamp(f, 0.0f, 1.0f);
        if ("linear".equals(str)) {
            return fClamp;
        }
        if ("step-start".equals(str)) {
            return fClamp <= 0.0f ? 0.0f : 1.0f;
        }
        if ("step-end".equals(str)) {
            return fClamp < 1.0f ? 0.0f : 1.0f;
        }
        if (str.startsWith("cubic-bezier(") && str.endsWith(")")) {
            List<String> listSplit = split(str.substring(13, str.length() - 1), ',');
            if (listSplit.size() == 4) {
                try {
                    return cubic(fClamp, Float.parseFloat(listSplit.get(0)), Float.parseFloat(listSplit.get(1)), Float.parseFloat(listSplit.get(2)), Float.parseFloat(listSplit.get(3)));
                } catch (Throwable unused) {
                }
            }
        }
        return "ease-in".equals(str) ? cubic(fClamp, 0.42f, 0.0f, 1.0f, 1.0f) : "ease-out".equals(str) ? cubic(fClamp, 0.0f, 0.0f, 0.58f, 1.0f) : "ease-in-out".equals(str) ? cubic(fClamp, 0.42f, 0.0f, 0.58f, 1.0f) : cubic(fClamp, 0.25f, 0.1f, 0.25f, 1.0f);
    }

    private static float cubic(float f, float f2, float f3, float f4, float f5) {
        float f6 = 0.0f;
        float f7 = 1.0f;
        float f8 = f;
        for (int i = 0; i < 12; i++) {
            f8 = (f6 + f7) / 2.0f;
            if (bezier(f8, f2, f4) < f) {
                f6 = f8;
            } else {
                f7 = f8;
            }
        }
        return clamp(bezier(f8, f3, f5), 0.0f, 1.0f);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String decimal(double d) {
        return Math.abs(d - Math.rint(d)) < 1.0E-5d ? Long.toString(Math.round(d)) : String.format(Locale.US, "%.4f", Double.valueOf(d)).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String color(int i) {
        return String.format(Locale.US, "#%08X", Integer.valueOf(i));
    }

    static final class Result {
        final boolean active;
        final BlockScript.Node node;

        Result(BlockScript.Node node, boolean z) {
            this.node = node;
            this.active = z;
        }
    }

    static final class State {
        final Snapshot from;
        final long started;
        final Snapshot to;

        State(Snapshot snapshot, Snapshot snapshot2, long j) {
            this.from = snapshot;
            this.to = snapshot2;
            this.started = j;
        }

        Snapshot at(long j) {
            return Snapshot.mix(this.from, this.to, j - this.started);
        }

        boolean active(long j) {
            return this.to.active(j - this.started);
        }
    }

    private static final class Snapshot {
        final Map<String, String> attrs;
        final Map<String, Integer> colors;
        final List<String> filterOrder;
        final Map<String, Double> filters;
        final Map<String, Double> numbers;
        final List<Part> parts;

        Snapshot(Map<String, Double> map, Map<String, Integer> map2, Map<String, Double> map3, List<String> list, Map<String, String> map4, List<Part> list2) {
            this.numbers = map;
            this.colors = map2;
            this.filters = map3;
            this.filterOrder = list;
            this.attrs = map4;
            this.parts = list2;
        }

        static Snapshot of(BlockScript.Node node, BlockScript.Context context, List<Part> list) {
            LinkedHashMap linkedHashMap = new LinkedHashMap();
            LinkedHashMap linkedHashMap2 = new LinkedHashMap();
            for (String str : node.attrs.keySet()) {
                if (BlockTransition.selected(str, list)) {
                    if (BlockTransition.NUMERIC.contains(str + ",")) {
                        linkedHashMap.put(str, Double.valueOf(BlockScript.number(node, str, defaultNumber(str))));
                    } else {
                        if (BlockTransition.COLORS.contains(str + ",")) {
                            linkedHashMap2.put(str, Integer.valueOf(resolveColor(node, str, context)));
                        }
                    }
                }
            }
            LinkedHashMap linkedHashMap3 = new LinkedHashMap();
            ArrayList arrayList = new ArrayList();
            if (BlockTransition.selected("filter", list)) {
                parseFilter(node.attrs.get("filter"), linkedHashMap3, arrayList);
            }
            return new Snapshot(linkedHashMap, linkedHashMap2, linkedHashMap3, arrayList, new LinkedHashMap(node.attrs), list);
        }

        static Snapshot mix(Snapshot snapshot, Snapshot snapshot2, long j) {
            float fProgress;
            LinkedHashMap linkedHashMap = new LinkedHashMap();
            LinkedHashMap linkedHashMap2 = new LinkedHashMap();
            LinkedHashMap linkedHashMap3 = new LinkedHashMap();
            LinkedHashSet linkedHashSet = new LinkedHashSet(snapshot.numbers.keySet());
            linkedHashSet.addAll(snapshot2.numbers.keySet());
            Iterator it = linkedHashSet.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                String str = (String) it.next();
                Part part = BlockTransition.part(str, snapshot2.parts);
                double dDoubleValue = snapshot.numbers.containsKey(str) ? snapshot.numbers.get(str).doubleValue() : defaultNumber(str);
                double dDoubleValue2 = snapshot2.numbers.containsKey(str) ? snapshot2.numbers.get(str).doubleValue() : defaultNumber(str);
                fProgress=part==null?1:part.progress(j);
                linkedHashMap.put(str, Double.valueOf(dDoubleValue + ((dDoubleValue2 - dDoubleValue) * fProgress)));
            }
            LinkedHashSet linkedHashSet2 = new LinkedHashSet(snapshot.colors.keySet());
            linkedHashSet2.addAll(snapshot2.colors.keySet());
            Iterator it2 = linkedHashSet2.iterator();
            while (it2.hasNext()) {
                String str2 = (String) it2.next();
                Part part2 = BlockTransition.part(str2, snapshot2.parts);
                int iIntValue = snapshot.colors.containsKey(str2) ? snapshot.colors.get(str2).intValue() : snapshot2.colors.get(str2).intValue();
                linkedHashMap2.put(str2, Integer.valueOf(mixColor(iIntValue, snapshot2.colors.containsKey(str2) ? snapshot2.colors.get(str2).intValue() : iIntValue, part2 == null ? 1.0f : part2.progress(j))));
            }
            LinkedHashSet linkedHashSet3 = new LinkedHashSet(snapshot2.filterOrder);
            linkedHashSet3.addAll(snapshot.filterOrder);
            Part part3 = BlockTransition.part("filter", snapshot2.parts);
            fProgress = part3 != null ? part3.progress(j) : 1.0f;
            Iterator it3 = linkedHashSet3.iterator();
            while (it3.hasNext()) {
                String str3 = (String) it3.next();
                double dFilterDefault = filterDefault(str3);
                double dDoubleValue3 = snapshot.filters.containsKey(str3) ? snapshot.filters.get(str3).doubleValue() : dFilterDefault;
                if (snapshot2.filters.containsKey(str3)) {
                    dFilterDefault = snapshot2.filters.get(str3).doubleValue();
                }
                linkedHashMap3.put(str3, Double.valueOf(dDoubleValue3 + ((dFilterDefault - dDoubleValue3) * fProgress)));
            }
            return new Snapshot(linkedHashMap, linkedHashMap2, linkedHashMap3, new ArrayList(linkedHashSet3), snapshot2.attrs, snapshot2.parts);
        }

        boolean active(long j) {
            Iterator<Part> it = this.parts.iterator();
            while (it.hasNext()) {
                if (it.next().active(j)) {
                    return true;
                }
            }
            return false;
        }

        boolean same(Snapshot snapshot) {
            return this.numbers.equals(snapshot.numbers) && this.colors.equals(snapshot.colors) && this.filters.equals(snapshot.filters) && this.filterOrder.equals(snapshot.filterOrder) && this.parts.equals(snapshot.parts);
        }

        BlockScript.Node node(BlockScript.Node node) {
            LinkedHashMap linkedHashMap = new LinkedHashMap(this.attrs);
            for (Map.Entry<String, Double> entry : this.numbers.entrySet()) {
                linkedHashMap.put(entry.getKey(), BlockTransition.decimal(entry.getValue().doubleValue()));
            }
            for (Map.Entry<String, Integer> entry2 : this.colors.entrySet()) {
                linkedHashMap.put(entry2.getKey(), BlockTransition.color(entry2.getValue().intValue()));
            }
            if (!this.filters.isEmpty()) {
                linkedHashMap.put("filter", filterText(this.filters, this.filterOrder));
            }
            return new BlockScript.Node(node.line, node.kind, Collections.unmodifiableMap(linkedHashMap), node.vars);
        }

        private static double defaultNumber(String str) {
            if ("alpha".equals(str)) {
                return 100.0d;
            }
            if ("scalex".equals(str) || "scaley".equals(str)) {
                return 1.0d;
            }
            return ("originx".equals(str) || "originy".equals(str)) ? 50.0d : 0.0d;
        }

        private static int resolveColor(BlockScript.Node node, String str, BlockScript.Context context) {
            String str2 = node.attrs.get(str);
            if (str2 == null) {
                return 0;
            }
            if (str2.startsWith("$")) {
                Integer num = context.colors.get(str2.substring(1));
                if (num == null) {
                    return 0;
                }
                return num.intValue();
            }
            if (str2.matches("#[0-9a-fA-F]{8}")) {
                try {
                    return (int) Long.parseLong(str2.substring(1), 16);
                } catch (Throwable unused) {
                }
            }
            if (str2.matches("#[0-9a-fA-F]{6}")) {
                try {
                    return Integer.parseInt(str2.substring(1), 16) | (-16777216);
                } catch (Throwable unused2) {
                }
            }
            return BlockScript.color(node, str, 0, context);
        }

        private static void parseFilter(String str, Map<String, Double> map, List<String> list) {
            if (str == null || str.trim().length() == 0 || "none".equalsIgnoreCase(str.trim())) {
                return;
            }
            Matcher matcher = Pattern.compile("([a-z-]+)\\s*\\(([^)]*)\\)").matcher(str.toLowerCase(Locale.US));
            while (matcher.find()) {
                String strGroup = matcher.group(1);
                if (BlockTransition.FILTERS.contains(strGroup + ",")) {
                    String strTrim = matcher.group(2).trim();
                    try {
                        boolean zEndsWith = strTrim.endsWith("%");
                        if (zEndsWith) {
                            strTrim = strTrim.substring(0, strTrim.length() - 1).trim();
                        }
                        if ("blur".equals(strGroup)) {
                            strTrim = strTrim.replace("dp", "").trim();
                        }
                        double d = Double.parseDouble(strTrim);
                        if (zEndsWith) {
                            d /= 100.0d;
                        }
                        map.put(strGroup, Double.valueOf(d));
                        if (!list.contains(strGroup)) {
                            list.add(strGroup);
                        }
                    } catch (Throwable unused) {
                    }
                }
            }
        }

        private static double filterDefault(String str) {
            return ("brightness".equals(str) || "contrast".equals(str) || "saturate".equals(str) || "opacity".equals(str)) ? 1.0d : 0.0d;
        }

        private static String filterText(Map<String, Double> map, List<String> list) {
            StringBuilder sb = new StringBuilder();
            for (String str : list) {
                Double d = map.get(str);
                if (d != null) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(str);
                    sb.append('(');
                    sb.append(BlockTransition.decimal(d.doubleValue()));
                    if ("blur".equals(str)) {
                        sb.append("dp");
                    }
                    sb.append(')');
                }
            }
            return sb.toString();
        }

        private static int mixColor(int i, int i2, float f) {
            int iRound = Math.round(((i >>> 24) & 255) + ((((i2 >>> 24) & 255) - ((i >>>24)&255)) * f));
            int iRound2 = Math.round(((i >>> 16) & 255) + ((((i2 >>> 16) & 255) - ((i >>>16)&255)) * f));
            int iRound3 = Math.round(((i >>> 8) & 255) + ((((i2 >>> 8) & 255) - ((i >>>8)&255)) * f));
            return Math.round((i & 255) + (((i2 & 255) - (i&255)) * f)) | (iRound << 24) | (iRound2 << 16) | (iRound3 << 8);
        }
    }

    private static final class Part {
        final long delay;
        final long duration;
        final String easing;
        final String property;

        Part(String str, long j, String str2, long j2) {
            this.property = str.length() == 0 ? "all" : str;
            this.duration = j;
            this.easing = str2;
            this.delay = j2;
        }

        static Part shorthand(String str) {
            long jTime = -1;
            String str2 = "all";
            String str3 = "ease";
            long jTime2 = 0;
            for (String str4 : BlockTransition.words(str.trim().toLowerCase(Locale.US))) {
                if (str4.endsWith("ms") || str4.endsWith("s")) {
                    if (jTime < 0) {
                        jTime = BlockTransition.time(str4, 0L);
                    } else {
                        jTime2 = BlockTransition.time(str4, 0L);
                    }
                } else if (str4.startsWith("cubic-bezier(") || str4.startsWith("steps(") || "linear".equals(str4) || str4.startsWith("ease") || str4.startsWith("step-")) {
                    str3 = str4;
                } else {
                    str2 = str4;
                }
            }
            return new Part(str2, Math.max(0L, jTime), str3, jTime2);
        }

        boolean matches(String str) {
            return "all".equals(this.property) || matchesExact(str);
        }

        boolean matchesExact(String str) {
            String strCssProperty = BlockTransition.cssProperty(str);
            if (this.property.equals(strCssProperty)) {
                return true;
            }
            if ("background-color".equals(this.property) && "background".equals(strCssProperty)) {
                return true;
            }
            return "border".equals(this.property) && strCssProperty.startsWith("border-");
        }

        float progress(long j) {
            long j2 = this.duration;
            if (j2 <= 0) {
                return j < this.delay ? 0.0f : 1.0f;
            }
            return BlockTransition.ease((j - this.delay) / (float)j2, this.easing);
        }

        boolean active(long j) {
            return j < this.delay + this.duration;
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof Part)) {
                return false;
            }
            Part part = (Part) obj;
            return this.property.equals(part.property) && this.duration == part.duration && this.delay == part.delay && this.easing.equals(part.easing);
        }

        public int hashCode() {
            int iHashCode = this.property.hashCode() * 31;
            long j = this.duration;
            int iHashCode2 = (((iHashCode + ((int) (j ^ (j >>> 32)))) * 31) + this.easing.hashCode()) * 31;
            long j2 = this.delay;
            return iHashCode2 + ((int) (j2 ^ (j2 >>> 32)));
        }
    }
}

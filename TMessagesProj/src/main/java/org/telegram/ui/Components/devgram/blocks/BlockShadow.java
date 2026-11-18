package org.telegram.ui.Components.devgram.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/* Adapted from the user-supplied Custom Profile plugin by @RoflPlugins. */
final class BlockShadow {
    private static float clamp(float f, float f2, float f3) {
        return f < f2 ? f2 : f > f3 ? f3 : f;
    }

    private BlockShadow() {
    }

    static List<Spec> parse(String str) {
        String strTrim = str == null ? "" : str.trim();
        if (strTrim.length() == 0 || "none".equalsIgnoreCase(strTrim)) {
            return Collections.EMPTY_LIST;
        }
        ArrayList arrayList = new ArrayList();
        Iterator<String> it = split(strTrim, ',').iterator();
        while (it.hasNext()) {
            Spec specOne = one(it.next().trim());
            if (specOne != null) {
                arrayList.add(specOne);
            }
            if (arrayList.size() >= 4) {
                break;
            }
        }
        return Collections.unmodifiableList(arrayList);
    }

    private static Spec one(String str) {
        List<String> listWords = words(str);
        ArrayList arrayList = new ArrayList();
        boolean z = false;
        int iColor = 1711276032;
        boolean z2 = false;
        for (String str2 : listWords) {
            if ("inset".equalsIgnoreCase(str2)) {
                z2 = true;
            } else if (isColor(str2)) {
                iColor = color(str2, 1711276032);
                z = true;
            } else {
                try {
                    arrayList.add(Float.valueOf(length(str2)));
                } catch (Throwable unused) {
                    return null;
                }
            }
        }
        if (arrayList.size() < 2 || arrayList.size() > 4) {
            return null;
        }
        return new Spec(((Float) arrayList.get(0)).floatValue(), ((Float) arrayList.get(1)).floatValue(), Math.min(200.0f, arrayList.size() > 2 ? Math.max(0.0f, ((Float) arrayList.get(2)).floatValue()) : 0.0f), clamp(arrayList.size() > 3 ? ((Float) arrayList.get(3)).floatValue() : 0.0f, -200.0f, 200.0f), z ? iColor : 1711276032, z2);
    }

    private static List<String> words(String str) {
        ArrayList arrayList = new ArrayList();
        int i = 0;
        int i2 = 0;
        int i3 = 0;
        while (i <= str.length()) {
            char cCharAt = i < str.length() ? str.charAt(i) : ' ';
            if (cCharAt == '(') {
                i2++;
            } else if (cCharAt == ')') {
                i2--;
            }
            if (Character.isWhitespace(cCharAt) && i2 == 0) {
                if (i > i3) {
                    arrayList.add(str.substring(i3, i));
                }
                i3 = i + 1;
            }
            i++;
        }
        return arrayList;
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
                arrayList.add(str.substring(i, i3));
                i = i3 + 1;
            }
        }
        arrayList.add(str.substring(i));
        return arrayList;
    }

    private static float length(String str) {
        String lowerCase = str.trim().toLowerCase(Locale.US);
        if (lowerCase.endsWith("dp") || lowerCase.endsWith("px")) {
            lowerCase = lowerCase.substring(0, lowerCase.length() - 2).trim();
        }
        return Float.parseFloat(lowerCase);
    }

    private static boolean isColor(String str) {
        String lowerCase = str.toLowerCase(Locale.US);
        return lowerCase.startsWith("#") || lowerCase.startsWith("rgb(") || lowerCase.startsWith("rgba(") || "black".equals(lowerCase) || "white".equals(lowerCase) || "transparent".equals(lowerCase);
    }

    private static int color(String str, int i) {
        String lowerCase = str.trim().toLowerCase(Locale.US);
        if ("black".equals(lowerCase)) {
            return -16777216;
        }
        if ("white".equals(lowerCase)) {
            return -1;
        }
        if ("transparent".equals(lowerCase)) {
            return 0;
        }
        if (lowerCase.matches("#[0-9a-f]{3}")) {
            return Integer.parseInt(lowerCase.substring(3, 4) + lowerCase.substring(3, 4), 16) | (Integer.parseInt(lowerCase.substring(1, 2) + lowerCase.substring(1, 2), 16) << 16) | (-16777216) | (Integer.parseInt(lowerCase.substring(2, 3) + lowerCase.substring(2, 3), 16) << 8);
        }
        if (lowerCase.matches("#[0-9a-f]{6}")) {
            return ((int) Long.parseLong(lowerCase.substring(1), 16)) | (-16777216);
        }
        if (lowerCase.matches("#[0-9a-f]{8}")) {
            long j = Long.parseLong(lowerCase.substring(1), 16);
            return (int) ((j >>> 8) | ((255 & j) << 24));
        }
        if ((lowerCase.startsWith("rgb(") || lowerCase.startsWith("rgba(")) && lowerCase.endsWith(")")) {
            String[] strArrSplit = lowerCase.substring(lowerCase.indexOf(40) + 1, lowerCase.length() - 1).split("\\s*,\\s*");
            if (strArrSplit.length >= 3) {
                return ((strArrSplit.length > 3 ? Math.round(clamp(Float.parseFloat(strArrSplit[3]), 0.0f, 1.0f) * 255.0f) : 255) << 24) | (channel(strArrSplit[0]) << 16) | (channel(strArrSplit[1]) << 8) | channel(strArrSplit[2]);
            }
        }
        return i;
    }

    private static int channel(String str) {
        String strTrim = str.trim();
        if (strTrim.endsWith("%")) {
            return Math.round(clamp(Float.parseFloat(strTrim.substring(0, strTrim.length() - 1)), 0.0f, 100.0f) * 2.55f);
        }
        return Math.round(clamp(Float.parseFloat(strTrim), 0.0f, 255.0f));
    }

    static final class Spec {
        final float blur;
        final int color;
        final boolean inset;
        final float spread;
        final float x;
        final float y;

        Spec(float f, float f2, float f3, float f4, int i, boolean z) {
            this.x = f;
            this.y = f2;
            this.blur = f3;
            this.spread = f4;
            this.color = i;
            this.inset = z;
        }
    }
}

package org.telegram.ui.Components.devgram.export;

import java.util.List;
import java.util.Locale;

public final class ProfileExportPlan {
    private ProfileExportPlan() {
    }

    public static String rowName(int i, String str) {
        String strClean = clean(str);
        String str2 = String.format(Locale.US, "row_%02d", Integer.valueOf(Math.max(0, i) + 1));
        if (strClean.length() == 0) {
            return str2;
        }
        return str2 + "_" + strClean;
    }

    public static String extraName(int i, String str) {
        String strClean = clean(str);
        String str2 = String.format(Locale.US, "extra_%02d", Integer.valueOf(Math.max(0, i) + 1));
        if (strClean.length() == 0) {
            return str2;
        }
        return str2 + "_" + strClean;
    }

    public static String clean(String str) {
        int i;
        if (str == null) {
            return "";
        }
        int iMax = Math.max(str.lastIndexOf(46), str.lastIndexOf(36));
        if (iMax >= 0 && (i = iMax + 1) < str.length()) {
            str = str.substring(i);
        }
        StringBuilder sb = new StringBuilder(str.length());
        for (int i2 = 0; i2 < str.length() && sb.length() < 24; i2++) {
            char cCharAt = str.charAt(i2);
            if ((cCharAt >= 'a' && cCharAt <= 'z') || ((cCharAt >= 'A' && cCharAt <= 'Z') || (cCharAt >= '0' && cCharAt <= '9'))) {
                sb.append(cCharAt);
            }
        }
        return sb.toString();
    }

    public static int[] bounds(int[] iArr, int i, int i2) {
        if (iArr == null || i <= 0 || i2 <= 0 || iArr.length < i * i2) {
            return null;
        }
        int i3 = -1;
        int i4 = i;
        int i5 = -1;
        int i6 = -1;
        for (int i7 = 0; i7 < i2; i7++) {
            int i8 = i7 * i;
            for (int i9 = 0; i9 < i; i9++) {
                if ((iArr[i8 + i9] >>> 24) != 0) {
                    if (i3 < 0) {
                        i3 = i7;
                    }
                    if (i9 < i4) {
                        i4 = i9;
                    }
                    i6 = i7;
                    if (i9 > i5) {
                        i5 = i9;
                    }
                }
            }
        }
        if (i3 < 0) {
            return null;
        }
        return new int[]{i4, i3, i5, i6};
    }

    public static final class Sheet {
        public final int height;
        public final String name;
        public final String video;
        public final int width;
        public final int x;
        public final int y;

        public Sheet(String str, int i, int i2, int i3, int i4) {
            this(str, i, i2, i3, i4, "");
        }

        public Sheet(String str, int i, int i2, int i3, int i4, String str2) {
            this.name = str == null ? "" : str;
            this.x = i;
            this.y = i2;
            this.width = Math.max(0, i3);
            this.height = Math.max(0, i4);
            this.video = str2 == null ? "" : str2;
        }
    }

    public static String manifest(int i, int i2, String str, List<Sheet> list) {
        String str2;
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\n  \"canvas\": {\"width\": ");
        int i3 = 0;
        sb.append(Math.max(0, i));
        sb.append(", \"height\": ");
        sb.append(Math.max(0, i2));
        sb.append("},\n  \"base_color\": \"");
        if (str == null) {
            str = "";
        }
        sb.append(str);
        sb.append("\",\n  \"layers\": [\n");
        int size = list == null ? 0 : list.size();
        while (i3 < size) {
            Sheet sheet = list.get(i3);
            sb.append("    {\"name\": \"");
            sb.append(sheet.name);
            sb.append("\", \"z\": ");
            sb.append(i3);
            sb.append(", \"x\": ");
            sb.append(sheet.x);
            sb.append(", \"y\": ");
            sb.append(sheet.y);
            sb.append(", \"width\": ");
            sb.append(sheet.width);
            sb.append(", \"height\": ");
            sb.append(sheet.height);
            sb.append(", \"file\": \"");
            if (sheet.video.length() > 0) {
                str2 = sheet.video;
            } else {
                str2 = sheet.name + ".png";
            }
            sb.append(str2);
            sb.append("\"}");
            i3++;
            sb.append(i3 < size ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }
}

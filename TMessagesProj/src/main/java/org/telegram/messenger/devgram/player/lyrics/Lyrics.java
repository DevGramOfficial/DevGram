package org.telegram.messenger.devgram.player.lyrics;

import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public final class Lyrics {
    public final boolean instrumental;
    public final ArrayList<Line> lines;
    public final String provider;
    public final int source;
    public final boolean synced;
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIME = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]", 2);
    private static final Pattern META = Pattern.compile("^\\[[a-zA-Z#]+:.*]$");

    public static final class Line {
        public final String text;
        public final long time;

        public Line(long j, String str) {
            this.time = j;
            this.text = str;
        }
    }

    private Lyrics(ArrayList<Line> arrayList, boolean z, boolean z2, int i, String str) {
        this.lines = arrayList;
        this.synced = z;
        this.instrumental = z2;
        this.source = i;
        this.provider = str;
    }

    public String getCreditName() {
        String strSubstring;
        if (this.source == 0 || (strSubstring = this.provider) == null) {
            return null;
        }
        int iIndexOf = strSubstring.indexOf(" + ");
        if (iIndexOf >= 0) {
            strSubstring = strSubstring.substring(0, iIndexOf);
        }
        int iLastIndexOf = strSubstring.lastIndexOf(" · ");
        if (iLastIndexOf >= 0) {
            strSubstring = strSubstring.substring(iLastIndexOf + 3);
        } else if (strSubstring.equalsIgnoreCase("lrcmux")) {
            return null;
        }
        String strTrim = strSubstring.trim();
        if (strTrim.isEmpty()) {
            return null;
        }
        return strTrim;
    }

    public static Lyrics instrumental(int i, String str) {
        return new Lyrics(new ArrayList(), false, true, i, str);
    }

    public static Lyrics synced(ArrayList<Line> arrayList, int i, String str) {
        return new Lyrics(arrayList, true, false, i, str);
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        ArrayList<Line> arrayList = this.lines;
        int size = arrayList.size();
        int i = 0;
        while (i < size) {
            Line line = arrayList.get(i);
            i++;
            Line line2 = line;
            if (this.synced) {
                long jMax = Math.max(0L, line2.time);
                sb.append(String.format(Locale.US, "[%02d:%02d.%02d]", Long.valueOf(jMax / 60000), Long.valueOf((jMax / 1000) % 60), Long.valueOf((jMax % 1000) / 10)));
            }
            sb.append(line2.text);
            sb.append('\n');
        }
        return sb.toString();
    }

    public static Lyrics parse(String str, int i) {
        return parse(str, i, null);
    }

    public static Lyrics parse(String str, int i, String str2) {
        ArrayList arrayList = null;
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        String[] strArrSplit = str.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        ArrayList arrayList2 = new ArrayList();
        int length = strArrSplit.length;
        long j = 0;
        int i2 = 0;
        while (i2 < length) {
            String strTrim = strArrSplit[i2].trim();
            Matcher matcher = OFFSET.matcher(strTrim);
            if (matcher.find()) {
                try {
                    j = Long.parseLong(matcher.group(1));
                } catch (NumberFormatException unused) {
                }
            } else {
                Matcher matcher2 = TIME.matcher(strTrim);
                ArrayList arrayList3 = arrayList;
                int iEnd = 0;
                while (matcher2.find() && matcher2.start() == iEnd) {
                    if (arrayList3 == null) {
                        arrayList3 = new ArrayList();
                    }
                    arrayList3.add(Long.valueOf(toMillis(matcher2.group(1), matcher2.group(2), matcher2.group(3))));
                    iEnd = matcher2.end();
                }
                if (arrayList3 != null) {
                    String strTrim2 = WORD_TIME.matcher(strTrim.substring(iEnd)).replaceAll("").trim();
                    int size = arrayList3.size();
                    int i3 = 0;
                    while (i3 < size) {
                        Object obj = arrayList3.get(i3);
                        i3++;
                        arrayList2.add(new Line(Math.max(0L, ((Long) obj).longValue() - j), strTrim2));
                    }
                }
            }
            i2++;
            arrayList = null;
        }
        if (!arrayList2.isEmpty()) {
            return fromTimedLines(arrayList2, i, str2);
        }
        ArrayList arrayList4 = new ArrayList();
        for (String str3 : strArrSplit) {
            String strTrim3 = str3.trim();
            if (!META.matcher(strTrim3).matches() && (!strTrim3.isEmpty() || (!arrayList4.isEmpty() && !((Line) arrayList4.get(arrayList4.size() - 1)).text.isEmpty()))) {
                arrayList4.add(new Line(-1L, strTrim3));
            }
        }
        while (!arrayList4.isEmpty() && ((Line) arrayList4.get(arrayList4.size() - 1)).text.isEmpty()) {
            arrayList4.remove(arrayList4.size() - 1);
        }
        if (arrayList4.isEmpty()) {
            return null;
        }
        return new Lyrics(arrayList4, false, false, i, str2);
    }

    public static Lyrics fromTimedLines(ArrayList<Line> arrayList, int i, String str) {
        arrayList.sort(Comparator.comparingLong(obj -> ((Lyrics.Line) obj).time));
        ArrayList arrayList2 = new ArrayList(arrayList.size());
        for (int i2 = 0; i2 < arrayList.size(); i2++) {
            Line line = arrayList.get(i2);
            if (!line.text.isEmpty() || (!arrayList2.isEmpty() && !((Line) arrayList2.get(arrayList2.size() - 1)).text.isEmpty() && i2 != arrayList.size() - 1)) {
                arrayList2.add(line);
            }
        }
        if (arrayList2.isEmpty()) {
            return null;
        }
        return new Lyrics(arrayList2, true, false, i, str);
    }

    /** мин:сек[.дробь] в миллисекунды; дробь из 1, 2 или 3 цифр (десятые, сотые, тысячные). */
    private static long toMillis(String str, String str2, String str3) {
        long base = (Long.parseLong(str) * 60000) + (Long.parseLong(str2) * 1000);
        if (str3 == null) {
            return base;
        }
        long fraction = Integer.parseInt(str3);
        if (str3.length() == 1) {
            fraction *= 100;
        } else if (str3.length() == 2) {
            fraction *= 10;
        }
        return base + fraction;
    }

    public int indexAt(long j) {
        int i = -1;
        if (!this.synced) {
            return -1;
        }
        int size = this.lines.size() - 1;
        int i2 = 0;
        while (i2 <= size) {
            int i3 = (i2 + size) >>> 1;
            if (this.lines.get(i3).time <= j) {
                i2 = i3 + 1;
                i = i3;
            } else {
                size = i3 - 1;
            }
        }
        return i;
    }
}

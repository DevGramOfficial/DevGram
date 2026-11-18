package org.telegram.ui.Components.devgram.export;

import java.util.List;
import java.util.Locale;

public final class ProfileExportAm {
    private static final String AM_APP = "com.alightcreative.motion/5.0.275";
    private static final String AM_VERSION = "1002592";
    private static final String CLIP_FPS = "30";
    private static final String FF_VERSION = "106";

    private ProfileExportAm() {
    }

    public static final class Piece {
        final long bytes;
        final long durationUs;
        final String file;
        final int height;
        final String label;
        final int mediaHeight;
        final int mediaWidth;
        final String mime;
        final String uri;
        final int width;
        final int x;
        final int y;

        public Piece(String str, String str2, String str3, long j, int i, int i2, int i3, int i4) {
            this(str, str2, str3, "image/png", j, i, i2, i3, i4, i3, i4, 0L);
        }

        public Piece(String str, String str2, String str3, String str4, long j, int i, int i2, int i3, int i4, int i5, int i6, long j2) {
            this.label = str == null ? "" : str;
            this.uri = str2 == null ? "" : str2;
            this.file = str3 == null ? "" : str3;
            this.mime = (str4 == null || str4.length() == 0) ? "image/png" : str4;
            this.bytes = Math.max(0L, j);
            this.x = i;
            this.y = i2;
            this.width = Math.max(0, i3);
            this.height = Math.max(0, i4);
            this.mediaWidth = Math.max(0, i5);
            this.mediaHeight = Math.max(0, i6);
            this.durationUs = Math.max(0L, j2);
        }

        boolean moving() {
            return this.durationUs > 0;
        }
    }

    public static String project(String str, int i, int i2, int i3, int i4, String str2, List<Piece> list, long j) {
        int iMax = Math.max(1, i);
        int iMax2 = Math.max(1, i2);
        int iMax3 = Math.max(1, i4);
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<?xml version='1.0' encoding='UTF-8' ?>\n<scene title=\"");
        sb.append(escape(str));
        sb.append("\" width=\"");
        sb.append(iMax);
        sb.append("\" height=\"");
        sb.append(iMax2);
        sb.append("\" exportWidth=\"");
        sb.append(iMax);
        sb.append("\" exportHeight=\"");
        sb.append(iMax2);
        sb.append("\" precompose=\"dynamicResolution\" bgcolor=\"");
        sb.append(escape(color(str2)));
        sb.append("\" totalTime=\"");
        sb.append(iMax3);
        sb.append("\" fps=\"");
        sb.append(Math.max(1, i3));
        sb.append("\" modifiedTime=\"");
        sb.append(Math.max(0L, j));
        sb.append("\" amver=\"1002592\" ffver=\"106\" am=\"com.alightcreative.motion/5.0.275\" amplatform=\"android\" retime=\"freeze\" retimeAdaptFPS=\"false\">\n");
        int size = list == null ? 0 : list.size();
        for (int i5 = 0; i5 < size; i5++) {
            media(sb, list.get(i5), j);
        }
        for (int i6 = 0; i6 < size; i6++) {
            shape(sb, list.get(i6), i6, iMax3);
        }
        sb.append("</scene>\n");
        return sb.toString();
    }

    private static void media(StringBuilder sb, Piece piece, long j) {
        sb.append("  <media uri=\"");
        sb.append(escape(piece.uri));
        sb.append("\"");
        sb.append(" filename=\"");
        sb.append(escape(piece.file));
        sb.append("\"");
        sb.append(" title=\"");
        sb.append(escape(piece.file));
        sb.append("\"");
        sb.append(" type=\"");
        sb.append(escape(piece.mime));
        sb.append("\"");
        if (piece.moving()) {
            sb.append(" duration=\"");
            sb.append(piece.durationUs);
            sb.append("\"");
        }
        sb.append(" size=\"");
        sb.append(piece.bytes);
        sb.append("\"");
        if (piece.moving()) {
            sb.append(" fps=\"");
            sb.append(CLIP_FPS);
            sb.append("\"");
        }
        sb.append(" orientation=\"0\" infoUpdated=\"");
        sb.append(Math.max(0L, j));
        sb.append("\"");
        sb.append(" width=\"");
        sb.append(piece.mediaWidth);
        sb.append("\"");
        sb.append(" height=\"");
        sb.append(piece.mediaHeight);
        sb.append("\" />\n");
    }

    private static void shape(StringBuilder sb, Piece piece, int i, int i2) {
        sb.append("  <shape id=\"");
        sb.append(i + 1);
        sb.append("\"");
        sb.append(" label=\"");
        sb.append(escape(piece.label));
        sb.append("\"");
        sb.append(" startTime=\"0\" endTime=\"");
        sb.append(i2);
        sb.append("\"");
        sb.append(" fillType=\"media\"");
        sb.append(piece.moving() ? " fillVideo=\"" : " fillImage=\"");
        sb.append(escape(piece.uri));
        sb.append("\"");
        if (piece.moving()) {
            sb.append(" outTime=\"");
            sb.append(Math.min(i2, piece.durationUs / 1000));
            sb.append("\"");
        }
        sb.append(" mediaFillMode=\"fill\" s=\".rect\">\n");
        sb.append("    <transform>\n");
        sb.append("      <location value=\"");
        sb.append(number(piece.x + (piece.width / 2.0f)));
        sb.append(",");
        sb.append(number(piece.y + (piece.height / 2.0f)));
        sb.append(",0.000000\" />\n");
        sb.append("    </transform>\n");
        sb.append("    <property name=\"size\" type=\"vec2\" value=\"");
        sb.append(number(piece.width / 2.0f));
        sb.append(",");
        sb.append(number(piece.height / 2.0f));
        sb.append("\" />\n");
        sb.append("  </shape>\n");
    }

    private static String number(float f) {
        return String.format(Locale.US, "%.6f", Float.valueOf(f));
    }

    private static String color(String str) {
        return (str == null || str.length() == 0) ? "#00000000" : str;
    }

    static String escape(String str) {
        if (str == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() + 8);
        for (int i = 0; i < str.length(); i++) {
            char cCharAt = str.charAt(i);
            if (cCharAt == '&') {
                sb.append("&amp;");
            } else if (cCharAt == '<') {
                sb.append("&lt;");
            } else if (cCharAt == '>') {
                sb.append("&gt;");
            } else if (cCharAt == '\"') {
                sb.append("&quot;");
            } else if (cCharAt == '\'') {
                sb.append("&apos;");
            } else {
                sb.append(cCharAt);
            }
        }
        return sb.toString();
    }
}

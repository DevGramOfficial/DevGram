package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Перевод с сохранением форматирования: entities превращаются во вложенные теги
 * {@code <span class="dgN">}, сервис переводит текст в HTML-режиме, теги разбираются обратно.
 * Код, формулы и кастомные эмодзи помечаются translate="no", чтобы их не трогали.
 */
public final class DevGramTranslationMarkup {

    private DevGramTranslationMarkup() {
    }

    public static boolean hasFormatting(TLRPC.TL_textWithEntities text) {
        return text != null && text.entities != null && !text.entities.isEmpty();
    }

    private static boolean isProtected(TLRPC.MessageEntity e) {
        return e instanceof TLRPC.TL_messageEntityCode || e instanceof TLRPC.TL_messageEntityPre
            || e instanceof TLRPC.TL_messageEntityCustomEmoji || e instanceof TLRPC.TL_messageEntityUrl
            || e instanceof TLRPC.TL_messageEntityEmail || e instanceof TLRPC.TL_messageEntityMention
            || e instanceof TLRPC.TL_messageEntityMentionName || e instanceof TLRPC.TL_inputMessageEntityMentionName
            || e instanceof TLRPC.TL_messageEntityHashtag || e instanceof TLRPC.TL_messageEntityCashtag
            || e instanceof TLRPC.TL_messageEntityBotCommand || e instanceof TLRPC.TL_messageEntityPhone
            || e instanceof TLRPC.TL_messageEntityBankCard;
    }

    /** Текст с entities → HTML; вложенность сохраняется, пересекающиеся entities режутся на куски. */
    public static String toHtml(TLRPC.TL_textWithEntities source) {
        String text = source.text == null ? "" : source.text;
        int n = text.length();
        ArrayList<TLRPC.MessageEntity> entities = source.entities;
        // для каждой позиции — список entities, покрывающих символ
        StringBuilder out = new StringBuilder(n + entities.size() * 24);
        ArrayList<Integer> open = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            ArrayList<Integer> active = new ArrayList<>();
            if (i < n) {
                for (int k = 0; k < entities.size(); k++) {
                    TLRPC.MessageEntity e = entities.get(k);
                    if (e.offset <= i && i < e.offset + e.length) {
                        active.add(k);
                    }
                }
                // внешние (длинные) — раньше вложенных
                Collections.sort(active, (a, b) -> {
                    TLRPC.MessageEntity ea = entities.get(a), eb = entities.get(b);
                    if (ea.offset != eb.offset) return Integer.compare(ea.offset, eb.offset);
                    return Integer.compare(eb.length, ea.length);
                });
            }
            int common = 0;
            while (common < open.size() && common < active.size() && open.get(common).equals(active.get(common))) {
                common++;
            }
            for (int k = open.size() - 1; k >= common; k--) {
                out.append("</span>");
                open.remove(k);
            }
            for (int k = common; k < active.size(); k++) {
                int idx = active.get(k);
                out.append("<span class=\"dg").append(idx).append('"');
                if (isProtected(entities.get(idx))) {
                    out.append(" translate=\"no\"");
                }
                out.append('>');
                open.add(idx);
            }
            if (i < n) {
                char c = text.charAt(i);
                switch (c) {
                    case '&': out.append("&amp;"); break;
                    case '<': out.append("&lt;"); break;
                    case '>': out.append("&gt;"); break;
                    case '\n': out.append("<br>"); break;
                    default: out.append(c);
                }
            }
        }
        return out.toString();
    }

    /** HTML из переводчика → текст с entities; null — если разметку не удалось разобрать. */
    public static TLRPC.TL_textWithEntities fromHtml(String html, TLRPC.TL_textWithEntities source) {
        StringBuilder text = new StringBuilder(html.length());
        ArrayList<int[]> stack = new ArrayList<>(); // {индекс entity или -1, начало}
        ArrayList<int[]> ranges = new ArrayList<>();
        int i = 0;
        int n = html.length();
        while (i < n) {
            char c = html.charAt(i);
            if (c == '<') {
                int end = html.indexOf('>', i);
                if (end < 0) {
                    return null;
                }
                String tag = html.substring(i + 1, end).trim();
                String lower = tag.toLowerCase();
                if (lower.startsWith("/")) {
                    String name = lower.substring(1).trim();
                    if (name.equals("span")) {
                        if (stack.isEmpty()) {
                            return null;
                        }
                        int[] top = stack.remove(stack.size() - 1);
                        if (top[0] >= 0 && text.length() > top[1]) {
                            ranges.add(new int[]{top[0], top[1], text.length() - top[1]});
                        }
                    }
                } else if (lower.startsWith("br")) {
                    text.append('\n');
                    if (end + 1 < n && html.charAt(end + 1) == ' ') {
                        end++;
                    }
                } else if (lower.startsWith("span")) {
                    int idx = -1;
                    int p = lower.indexOf("dg");
                    if (p >= 0) {
                        int q = p + 2;
                        while (q < lower.length() && Character.isDigit(lower.charAt(q))) {
                            q++;
                        }
                        if (q > p + 2) {
                            idx = Integer.parseInt(lower.substring(p + 2, q));
                        }
                    }
                    stack.add(new int[]{idx, text.length()});
                }
                i = end + 1;
            } else if (c == '&') {
                int end = html.indexOf(';', i);
                if (end < 0 || end - i > 10) {
                    text.append(c);
                    i++;
                    continue;
                }
                String ent = html.substring(i + 1, end);
                if (ent.equals("amp")) text.append('&');
                else if (ent.equals("lt")) text.append('<');
                else if (ent.equals("gt")) text.append('>');
                else if (ent.equals("quot")) text.append('"');
                else if (ent.equals("apos") || ent.equals("#39")) text.append('\'');
                else if (ent.equals("nbsp")) text.append(' ');
                else if (ent.startsWith("#x") || ent.startsWith("#X")) text.appendCodePoint(Integer.parseInt(ent.substring(2), 16));
                else if (ent.startsWith("#")) text.appendCodePoint(Integer.parseInt(ent.substring(1)));
                else text.append(html, i, end + 1);
                i = end + 1;
            } else {
                text.append(c);
                i++;
            }
        }
        if (!stack.isEmpty()) {
            return null;
        }
        TLRPC.TL_textWithEntities result = new TLRPC.TL_textWithEntities();
        result.text = text.toString();
        // соседние куски одной entity склеиваем обратно
        ranges.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        int[] last = null;
        List<int[]> merged = new ArrayList<>();
        for (int[] r : ranges) {
            if (last != null && last[0] == r[0] && last[1] + last[2] >= r[1]) {
                last[2] = Math.max(last[1] + last[2], r[1] + r[2]) - last[1];
            } else {
                last = new int[]{r[0], r[1], r[2]};
                merged.add(last);
            }
        }
        for (int[] r : merged) {
            if (r[0] < 0 || r[0] >= source.entities.size()) {
                continue;
            }
            TLRPC.MessageEntity copy = copyEntity(source.entities.get(r[0]));
            if (copy == null) {
                continue;
            }
            copy.offset = r[1];
            copy.length = r[2];
            result.entities.add(copy);
        }
        result.entities.sort((a, b) -> Integer.compare(a.offset, b.offset));
        return result;
    }

    private static TLRPC.MessageEntity copyEntity(TLRPC.MessageEntity e) {
        try {
            org.telegram.tgnet.NativeByteBuffer buffer = new org.telegram.tgnet.NativeByteBuffer(e.getObjectSize());
            e.serializeToStream(buffer);
            buffer.rewind();
            TLRPC.MessageEntity copy = TLRPC.MessageEntity.TLdeserialize(buffer, buffer.readInt32(false), false);
            buffer.reuse();
            return copy;
        } catch (Exception ex) {
            FileLog.e(ex);
            return null;
        }
    }
}

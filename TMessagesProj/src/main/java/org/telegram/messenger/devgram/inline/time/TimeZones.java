package org.telegram.messenger.devgram.inline.time;

import org.telegram.messenger.devgram.KtCompat;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.MatchResult;
import kotlin.text.Regex;
import kotlin.text.StringsKt;
import kotlin.text.Typography;
public final class TimeZones {
    public static final TimeZones INSTANCE = new TimeZones();
    private static final Set<String> targetMarkers = SetsKt.setOf(new String[]{"в", "во", "to", "in"});
    private static final Set<String> sourceMarkers = SetsKt.setOf("по");
    private static final Set<String> nowWords = SetsKt.setOf(new String[]{"сейчас", "now"});
    private static final Set<String> meridiems = SetsKt.setOf(new String[]{"am", "pm", "a.m.", "p.m."});
    private static final Regex timePattern = new Regex("^(\\d{1,2})(?:[:.](\\d{2}))?(am|pm|a\\.m\\.|p\\.m\\.)?$");
    private static final Regex offsetPattern = new Regex("^(utc|gmt|msk|мск) ?([+-]) ?(\\d{1,2})(?::?(\\d{2}))?$");
    private static final HashMap<String, String> zones = new HashMap<>();

    private TimeZones() {
    }

    static {
        _init_$add("UTC", "utc");
        _init_$add("GMT", "gmt");
        _init_$add("Europe/Moscow", "msk", "мск", "москва", "москве", "moscow", "питер", "питере", "спб", "петербург", "петербурге", "санкт-петербург", "санкт-петербурге", "st petersburg", "saint petersburg");
        _init_$add("Europe/Kaliningrad", "калининград", "калининграде", "kaliningrad");
        _init_$add("Europe/Samara", "самара", "самаре", "samara");
        _init_$add("Asia/Yekaterinburg", "екб", "екатеринбург", "екатеринбурге", "yekaterinburg");
        _init_$add("Asia/Omsk", "омск", "омске", "omsk");
        _init_$add("Asia/Novosibirsk", "нск", "новосибирск", "новосибирске", "novosibirsk");
        _init_$add("Asia/Krasnoyarsk", "красноярск", "красноярске", "krasnoyarsk");
        _init_$add("Asia/Irkutsk", "иркутск", "иркутске", "irkutsk");
        _init_$add("Asia/Yakutsk", "якутск", "якутске", "yakutsk");
        _init_$add("Asia/Vladivostok", "владивосток", "владивостоке", "vladivostok");
        _init_$add("Asia/Magadan", "магадан", "магадане", "magadan");
        _init_$add("Asia/Kamchatka", "камчатка", "камчатке", "kamchatka");
        _init_$add("Europe/Kiev", "киев", "киеве", "київ", "києві", "kyiv", "kiev");
        _init_$add("Europe/Minsk", "минск", "минске", "minsk");
        _init_$add("Asia/Almaty", "алматы", "almaty", "астана", "астане", "astana");
        _init_$add("Asia/Tashkent", "ташкент", "ташкенте", "tashkent");
        _init_$add("Asia/Bishkek", "бишкек", "бишкеке", "bishkek");
        _init_$add("Asia/Dushanbe", "душанбе", "dushanbe");
        _init_$add("Asia/Tbilisi", "тбилиси", "tbilisi");
        _init_$add("Asia/Yerevan", "ереван", "ереване", "yerevan");
        _init_$add("Asia/Baku", "баку", "baku");
        _init_$add("Europe/Chisinau", "кишинев", "кишиневе", "chisinau");
        _init_$add("Europe/Riga", "рига", "риге", "riga");
        _init_$add("Europe/Vilnius", "вильнюс", "вильнюсе", "vilnius");
        _init_$add("Europe/Tallinn", "таллин", "таллине", "tallinn");
        _init_$add("Europe/Helsinki", "eet", "eest", "хельсинки", "helsinki");
        _init_$add("Europe/Warsaw", "варшава", "варшаве", "warsaw");
        _init_$add("Europe/Prague", "прага", "праге", "prague");
        _init_$add("Europe/Berlin", "cet", "cest", "берлин", "берлине", "berlin");
        _init_$add("Europe/Paris", "париж", "париже", "paris");
        _init_$add("Europe/Rome", "рим", "риме", "rome");
        _init_$add("Europe/Madrid", "мадрид", "мадриде", "madrid", "барселона", "барселоне", "barcelona");
        _init_$add("Europe/Amsterdam", "амстердам", "амстердаме", "amsterdam");
        _init_$add("Europe/London", "bst", "лондон", "лондоне", "london");
        _init_$add("Europe/Lisbon", "wet", "west", "лиссабон", "лиссабоне", "lisbon");
        _init_$add("Europe/Istanbul", "стамбул", "стамбуле", "istanbul", "анталья", "анталье", "antalya");
        _init_$add("Asia/Jerusalem", "тель-авив", "тель-авиве", "tel aviv", "иерусалим", "иерусалиме", "jerusalem");
        _init_$add("Asia/Dubai", "дубай", "дубае", "dubai");
        _init_$add("Africa/Cairo", "каир", "каире", "cairo");
        _init_$add("Asia/Kolkata", "ist", "дели", "delhi", "new delhi", "мумбаи", "mumbai");
        _init_$add("Asia/Bangkok", "бангкок", "бангкоке", "bangkok", "пхукет", "пхукете", "phuket");
        _init_$add("Asia/Makassar", "бали", "bali");
        _init_$add("Asia/Singapore", "sgt", "сингапур", "сингапуре", "singapore");
        _init_$add("Asia/Hong_Kong", "hkt", "гонконг", "гонконге", "hong kong");
        _init_$add("Asia/Shanghai", "пекин", "пекине", "beijing", "шанхай", "шанхае", "shanghai");
        _init_$add("Asia/Seoul", "kst", "сеул", "сеуле", "seoul");
        _init_$add("Asia/Tokyo", "jst", "токио", "tokyo");
        _init_$add("Australia/Perth", "awst", "перт", "перте", "perth");
        _init_$add("Australia/Sydney", "aest", "aedt", "сидней", "сиднее", "sydney");
        _init_$add("Australia/Melbourne", "мельбурн", "мельбурне", "melbourne");
        _init_$add("Pacific/Auckland", "nzst", "nzdt", "окленд", "окленде", "auckland");
        _init_$add("America/New_York", "est", "edt", "нью-йорк", "нью-йорке", "new york", "nyc", "вашингтон", "вашингтоне", "washington", "майами", "miami");
        _init_$add("America/Toronto", "торонто", "toronto");
        _init_$add("America/Chicago", "cst", "cdt", "чикаго", "chicago");
        _init_$add("America/Denver", "mst", "mdt", "денвер", "денвере", "denver");
        _init_$add("America/Los_Angeles", "pst", "pdt", "лос-анджелес", "лос-анджелесе", "los angeles", "сан-франциско", "san francisco", "сиэтл", "сиэтле", "seattle");
        _init_$add("America/Vancouver", "ванкувер", "ванкувере", "vancouver");
        _init_$add("America/Anchorage", "akst", "akdt", "анкоридж", "анкоридже", "anchorage");
        _init_$add("Pacific/Honolulu", "hst", "гонолулу", "honolulu");
        _init_$add("America/Mexico_City", "мехико", "mexico city");
        _init_$add("America/Sao_Paulo", "сан-паулу", "sao paulo");
        _init_$add("America/Argentina/Buenos_Aires", "буэнос-айрес", "буэнос-айресе", "buenos aires");
    }

    public final String valueAt(CharSequence text, int equalsIndex, TimeZoneOptions options) {
        ZoneId zoneIdZone;
        ZoneId zoneIdZone2;
        List listSplit$default = KtCompat.split((CharSequence) KtCompat.replace(KtCompat.replace(text.subSequence(Math.max(0, equalsIndex - 64), equalsIndex).toString().toLowerCase(Locale.ROOT), (char) 1105, (char) 1077, false, 4, (Object) null), (char) 8722, '-', false, 4, (Object) null), new char[]{' ', '\t', Typography.nbsp}, false, 0, 6, (Object) null);
        ArrayList arrayList = new ArrayList();
        for (Object obj : listSplit$default) {
            if (((String) obj).length() > 0) {
                arrayList.add(obj);
            }
        }
        int size = arrayList.size();
        int i = 3;
        while (true) {
            if (i <= 0) {
                zoneIdZone = null;
                break;
            }
            int i2 = size - i;
            int i3 = i2 - 1;
            if (i3 >= 0 && targetMarkers.contains(arrayList.get(i3)) && (zoneIdZone = zone(arrayList, i2, size)) != null) {
                size -= i + 1;
                break;
            }
            i--;
        }
        int i4 = 3;
        while (true) {
            if (i4 <= 0) {
                zoneIdZone2 = null;
                break;
            }
            int i5 = size - i4;
            if (i5 >= 1 && (zoneIdZone2 = zone(arrayList, i5, size)) != null) {
                if (sourceMarkers.contains(arrayList.get(i5 - 1)) && i5 > 1) {
                    i5--;
                }
                size = i5;
                break;
            }
            i4--;
        }
        if (size < 1 || (zoneIdZone2 == null && zoneIdZone == null)) {
            return null;
        }
        String str = (String) arrayList.get(size - 1);
        if (meridiems.contains(str) && size >= 2) {
            str = arrayList.get(size - 2) + str;
        }
        ZoneId local = options.getLocal();
        ZonedDateTime now = options.getNow();
        if (now == null) {
            now = ZonedDateTime.now(local);
        }
        if (nowWords.contains(str)) {
            if (zoneIdZone == null || zoneIdZone2 != null) {
                return null;
            }
            return format(now.withZoneSameInstant(zoneIdZone).toLocalTime(), null);
        }
        MatchResult matchResultMatchEntire = timePattern.matchEntire(str);
        if (matchResultMatchEntire == null) {
            return null;
        }
        int i6 = Integer.parseInt(matchResultMatchEntire.getGroupValues().get(1));
        String str2 = matchResultMatchEntire.getGroupValues().get(2);
        String str3 = matchResultMatchEntire.getGroupValues().get(3);
        if (str3.length() == 0) {
            str3 = null;
        }
        String str4 = str3;
        if (str2.length() == 0 && str4 == null && zoneIdZone2 == null) {
            return null;
        }
        int i7 = str2.length() == 0 ? 0 : Integer.parseInt(str2);
        if (i7 > 59) {
            return null;
        }
        if (str4 != null) {
            if (1 > i6 || i6 >= 13) {
                return null;
            }
            i6 = (i6 % 12) + (KtCompat.startsWith((CharSequence) str4, 'p', false, 2, (Object) null) ? 12 : 0);
        } else if (i6 > 23) {
            return null;
        }
        if (zoneIdZone2 == null) {
            zoneIdZone2 = local;
        }
        if (zoneIdZone == null) {
            zoneIdZone = local;
        }
        ZonedDateTime zonedDateTimeOf = ZonedDateTime.of(now.withZoneSameInstant(zoneIdZone2).toLocalDate(), LocalTime.of(i6, i7), zoneIdZone2);
        ZonedDateTime zonedDateTimeO = zonedDateTimeOf.withZoneSameInstant(zoneIdZone);
        if (Intrinsics.areEqual(zonedDateTimeO.toLocalDateTime(), zonedDateTimeOf.toLocalDateTime())) {
            return null;
        }
        long epochDay = zonedDateTimeO.toLocalDate().toEpochDay() - zonedDateTimeOf.toLocalDate().toEpochDay();
        String str5 = format(zonedDateTimeO.toLocalTime(), str4);
        if (epochDay > 0) {
            return str5 + " (+" + epochDay + ")";
        }
        if (epochDay >= 0) {
            return str5;
        }
        return str5 + " (" + epochDay + ")";
    }

    private final String format(LocalTime time, String meridiem) {
        String strPadStart = StringsKt.padStart(String.valueOf(time.getMinute()), 2, '0');
        if (meridiem == null) {
            return StringsKt.padStart(String.valueOf(time.getHour()), 2, '0') + ":" + strPadStart;
        }
        return (((time.getHour() + 11) % 12) + 1) + ":" + strPadStart + " " + (time.getHour() < 12 ? "am" : "pm");
    }

    private final ZoneId zone(List<String> words, int from, int to) {
        String strJoinToString$default = KtCompat.joinToString(words.subList(from, to), " ", null, null, 0, null, null, 62, null);
        String str = zones.get(normalize(strJoinToString$default));
        if (str != null) {
            try {
                return ZoneId.of(str);
            } catch (DateTimeException unused) {
                return null;
            }
        }
        MatchResult matchResultMatchEntire = offsetPattern.matchEntire(strJoinToString$default);
        if (matchResultMatchEntire == null) {
            return null;
        }
        int i = (Intrinsics.areEqual(matchResultMatchEntire.getGroupValues().get(1), "msk") || Intrinsics.areEqual(matchResultMatchEntire.getGroupValues().get(1), "мск")) ? 180 : 0;
        int i2 = Integer.parseInt(matchResultMatchEntire.getGroupValues().get(3));
        String str2 = matchResultMatchEntire.getGroupValues().get(4);
        if (str2.length() == 0) {
            str2 = "0";
        }
        int i3 = Integer.parseInt(str2);
        if (i2 <= 18 && i3 <= 59) {
            try {
                return ZoneOffset.ofTotalSeconds((i + ((Intrinsics.areEqual(matchResultMatchEntire.getGroupValues().get(2), "-") ? -1 : 1) * ((i2 * 60) + i3))) * 60);
            } catch (DateTimeException unused2) {
            }
        }
        return null;
    }

    private final String normalize(String name) {
        return KtCompat.replace(KtCompat.replace(name.toLowerCase(Locale.ROOT), (char) 1105, (char) 1077, false, 4, (Object) null), '-', ' ', false, 4, (Object) null);
    }

    private static final void _init_$add(String str, String... strArr) {
        for (String str2 : strArr) {
            zones.put(INSTANCE.normalize(str2), str);
        }
    }
}

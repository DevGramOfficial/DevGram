package org.telegram.messenger.devgram.inline.time;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import kotlin.jvm.internal.DefaultConstructorMarker;
public final class TimeZoneOptions {
    private final ZoneId local;
    private final ZonedDateTime now;

    public TimeZoneOptions(ZoneId zoneId, ZonedDateTime zonedDateTime) {
        this.local = zoneId;
        this.now = zonedDateTime;
    }

    public /* synthetic */ TimeZoneOptions(ZoneId zoneId, ZonedDateTime zonedDateTime, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(zoneId, (i & 2) != 0 ? null : zonedDateTime);
    }

    public final ZoneId getLocal() {
        return this.local;
    }

    public final ZonedDateTime getNow() {
        return this.now;
    }
}

package kr.seungmin.satisskyfactory.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static long nowMillis() {
        return Instant.now().toEpochMilli();
    }

    public static long hoursFromNowMillis(long hours) {
        return Instant.now().plus(Duration.ofHours(Math.max(0L, hours))).toEpochMilli();
    }

    public static long startOfTodayMillis() {
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public static String todayKey() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }
}

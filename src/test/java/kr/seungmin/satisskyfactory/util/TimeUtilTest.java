package kr.seungmin.satisskyfactory.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeUtilTest {
    @Test
    void exposesStableDailyMarketKeys() {
        assertEquals(LocalDate.now(ZoneId.systemDefault()).toString(), TimeUtil.todayKey());
    }

    @Test
    void calculatesStartOfTodayInTheServerZone() {
        long start = TimeUtil.startOfTodayMillis();
        long expected = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        assertEquals(expected, start);
        assertTrue(TimeUtil.nowMillis() >= start);
    }

    @Test
    void treatsNegativeExpiryHoursAsImmediate() {
        long before = TimeUtil.nowMillis();
        long expires = TimeUtil.hoursFromNowMillis(-3);
        long after = TimeUtil.nowMillis();

        assertTrue(expires >= before);
        assertTrue(expires <= after);
    }
}

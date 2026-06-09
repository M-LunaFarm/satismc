package kr.seungmin.satisskyfactory.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberFormatterTest {
    @Test
    void formatsStableGoalUiNumbers() {
        assertEquals("0.75", NumberFormatter.ratio(0.754));
        assertEquals("12.3", NumberFormatter.decimal(12.25, 1));
        assertEquals("120", NumberFormatter.whole(119.6));
    }

    @Test
    void clampsMinutesUntilToZero() {
        assertEquals(5, NumberFormatter.minutesUntil(400_000L, 100_000L));
        assertEquals(0, NumberFormatter.minutesUntil(100_000L, 400_000L));
    }
}

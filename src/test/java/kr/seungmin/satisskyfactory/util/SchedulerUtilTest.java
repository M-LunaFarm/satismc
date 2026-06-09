package kr.seungmin.satisskyfactory.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulerUtilTest {
    @Test
    void clampsRepeatingTaskPeriodsToAtLeastOneTick() {
        assertEquals(1, SchedulerUtil.positiveTicks(0));
        assertEquals(1, SchedulerUtil.positiveTicks(-20));
        assertEquals(40, SchedulerUtil.positiveTicks(40));
    }
}

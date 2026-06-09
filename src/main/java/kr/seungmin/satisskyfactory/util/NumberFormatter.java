package kr.seungmin.satisskyfactory.util;

import java.util.Locale;

public final class NumberFormatter {
    private NumberFormatter() {
    }

    public static String decimal(double value, int places) {
        int safePlaces = Math.max(0, Math.min(6, places));
        return String.format(Locale.US, "%." + safePlaces + "f", value);
    }

    public static String whole(double value) {
        return decimal(value, 0);
    }

    public static String ratio(double value) {
        return decimal(value, 2);
    }

    public static long minutesUntil(long timestampMillis, long nowMillis) {
        return Math.max(0L, (timestampMillis - nowMillis) / 60_000L);
    }
}

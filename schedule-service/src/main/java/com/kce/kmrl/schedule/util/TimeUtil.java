package com.kce.kmrl.schedule.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class TimeUtil {

    public static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    public static final int SERVICE_START_MIN = 6 * 60;
    public static final int SERVICE_END_MIN = 23 * 60;

    public static final int MORNING_PEAK_START = 8 * 60;
    public static final int MORNING_PEAK_END = 10 * 60;
    public static final int EVENING_PEAK_START = 16 * 60;
    public static final int EVENING_PEAK_END = 19 * 60;

    private TimeUtil() {
    }

    public static int nowMinutes() {
        LocalTime now = LocalTime.now(KOLKATA);
        return now.getHour() * 60 + now.getMinute();
    }

    public static LocalDate todayDate() {
        return LocalDate.now(KOLKATA);
    }

    public static String today() {
        return todayDate().toString();
    }

    public static boolean isPeak(int minutesSinceMidnight) {
        return (minutesSinceMidnight >= MORNING_PEAK_START && minutesSinceMidnight < MORNING_PEAK_END)
                || (minutesSinceMidnight >= EVENING_PEAK_START && minutesSinceMidnight < EVENING_PEAK_END);
    }

    public static boolean isNight(int minutesSinceMidnight) {
        return minutesSinceMidnight < SERVICE_START_MIN || minutesSinceMidnight >= SERVICE_END_MIN;
    }

    public static String formatMinutesToTime(int totalMinutes) {
        int hours = (totalMinutes / 60) % 24;
        int mins = totalMinutes % 60;
        String ampm = hours >= 12 ? "PM" : "AM";
        int displayHours = hours % 12;
        if (displayHours == 0) displayHours = 12;
        return String.format("%02d:%02d %s", displayHours, mins, ampm);
    }
}

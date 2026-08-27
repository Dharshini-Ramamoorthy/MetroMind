package com.kce.kmrl.schedule.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ServiceDayProfileResolver {

    public enum DayType {
        WEEKDAY,
        SATURDAY,
        SUNDAY,
        PUBLIC_HOLIDAY,
        FESTIVAL
    }

    public record DayProfile(DayType dayType, double headwayMultiplier) {}

    private final Set<LocalDate> publicHolidayDates;
    private final Set<LocalDate> festivalDates;
    private final double weekdayMultiplier;
    private final double saturdayMultiplier;
    private final double sundayMultiplier;
    private final double publicHolidayMultiplier;
    private final double festivalMultiplier;

    public ServiceDayProfileResolver(
            @Value("${schedule.public-holiday-dates:}") String publicHolidayDates,
            @Value("${schedule.festival-dates:}") String festivalDates,
            @Value("${schedule.day-profile.weekday-headway-multiplier:1.0}") double weekdayMultiplier,
            @Value("${schedule.day-profile.saturday-headway-multiplier:1.05}") double saturdayMultiplier,
            @Value("${schedule.day-profile.sunday-headway-multiplier:1.20}") double sundayMultiplier,
            @Value("${schedule.day-profile.public-holiday-headway-multiplier:1.15}") double publicHolidayMultiplier,
            @Value("${schedule.day-profile.festival-headway-multiplier:1.35}") double festivalMultiplier) {
        this.publicHolidayDates = parseDates(publicHolidayDates);
        this.festivalDates = parseDates(festivalDates);
        this.weekdayMultiplier = validateMultiplier(weekdayMultiplier, "weekday");
        this.saturdayMultiplier = validateMultiplier(saturdayMultiplier, "saturday");
        this.sundayMultiplier = validateMultiplier(sundayMultiplier, "sunday");
        this.publicHolidayMultiplier = validateMultiplier(publicHolidayMultiplier, "public holiday");
        this.festivalMultiplier = validateMultiplier(festivalMultiplier, "festival");
    }

    public DayProfile resolve(LocalDate date) {
        if (festivalDates.contains(date)) {
            return new DayProfile(DayType.FESTIVAL, festivalMultiplier);
        }
        if (publicHolidayDates.contains(date)) {
            return new DayProfile(DayType.PUBLIC_HOLIDAY, publicHolidayMultiplier);
        }

        DayOfWeek day = date.getDayOfWeek();
        return switch (day) {
            case SUNDAY -> new DayProfile(DayType.SUNDAY, sundayMultiplier);
            case SATURDAY -> new DayProfile(DayType.SATURDAY, saturdayMultiplier);
            default -> new DayProfile(DayType.WEEKDAY, weekdayMultiplier);
        };
    }

    private static Set<LocalDate> parseDates(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(LocalDate::parse)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static double validateMultiplier(double value, String name) {
        if (value <= 0.0) {
            throw new IllegalArgumentException(name + " headway multiplier must be > 0");
        }
        return value;
    }
}

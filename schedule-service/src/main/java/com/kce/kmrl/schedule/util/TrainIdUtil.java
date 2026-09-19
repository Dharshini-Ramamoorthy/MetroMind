package com.kce.kmrl.schedule.util;

import com.kce.kmrl.schedule.dto.TrainAssetDto;
import com.kce.kmrl.schedule.model.ScheduleTrip;

import java.util.OptionalLong;
import java.util.Set;

public final class TrainIdUtil {

    private TrainIdUtil() {}

    /**
     * Strips non-digits, returns empty when the result is blank or unparseable,
     * and parses as long rather than int.
     */
    public static OptionalLong digitsOf(String s) {
        if (s == null || s.isBlank()) {
            return OptionalLong.empty();
        }
        String digits = s.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(digits));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    public static boolean matchesTrainIdentifier(ScheduleTrip trip, String targetTrainId) {
        if (trip == null || targetTrainId == null || targetTrainId.isBlank()) return false;
        OptionalLong targetOpt = digitsOf(targetTrainId);
        if (targetOpt.isEmpty()) {
            String rawId = targetTrainId.trim().toUpperCase();
            String assignedId = trip.getAssignedTrainId() != null ? trip.getAssignedTrainId().trim().toUpperCase() : "";
            String assignedName = trip.getAssignedTrainName() != null ? trip.getAssignedTrainName().trim().toUpperCase() : "";
            return assignedId.equalsIgnoreCase(rawId) || assignedName.equalsIgnoreCase(rawId);
        }

        long targetNum = targetOpt.getAsLong();
        OptionalLong idOpt = digitsOf(trip.getAssignedTrainId());
        if (idOpt.isPresent() && idOpt.getAsLong() == targetNum) {
            return true;
        }
        OptionalLong nameOpt = digitsOf(trip.getAssignedTrainName());
        return nameOpt.isPresent() && nameOpt.getAsLong() == targetNum;
    }

    public static boolean matchesTrainAssetIdentifier(TrainAssetDto train, String targetId) {
        if (train == null || targetId == null || targetId.isBlank()) return false;
        OptionalLong targetOpt = digitsOf(targetId);
        if (targetOpt.isEmpty()) {
            String rawId = targetId.trim().toUpperCase();
            String trainId = train.getId() != null ? train.getId().trim().toUpperCase() : "";
            String trainNum = train.getTrainNumber() != null ? train.getTrainNumber().trim().toUpperCase() : "";
            return trainId.equalsIgnoreCase(rawId) || trainNum.equalsIgnoreCase(rawId);
        }

        long targetNum = targetOpt.getAsLong();
        OptionalLong idOpt = digitsOf(train.getId());
        if (idOpt.isPresent() && idOpt.getAsLong() == targetNum) {
            return true;
        }
        OptionalLong numOpt = digitsOf(train.getTrainNumber());
        return numOpt.isPresent() && numOpt.getAsLong() == targetNum;
    }

    public static boolean matchesMaintenanceSet(TrainAssetDto train, Set<String> activeTickets) {
        if (train == null || activeTickets == null || activeTickets.isEmpty()) return false;

        String id = train.getId() != null ? train.getId().trim().toUpperCase() : "";
        String number = train.getTrainNumber() != null ? train.getTrainNumber().trim().toUpperCase() : "";

        for (String active : activeTickets) {
            if (active == null || active.isBlank()) continue;
            String actUpper = active.trim().toUpperCase();

            if (!id.isEmpty() && id.equals(actUpper)) return true;
            if (!number.isEmpty() && number.equals(actUpper)) return true;

            OptionalLong actOpt = digitsOf(actUpper);
            if (actOpt.isPresent()) {
                long actNum = actOpt.getAsLong();
                OptionalLong idOpt = digitsOf(id);
                if (idOpt.isPresent() && idOpt.getAsLong() == actNum) return true;

                OptionalLong numOpt = digitsOf(number);
                if (numOpt.isPresent() && numOpt.getAsLong() == actNum) return true;
            }
        }
        return false;
    }
}

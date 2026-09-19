package com.kce.kmrl.schedule.service;

import com.kce.kmrl.schedule.model.ScheduleTrip;
import com.kce.kmrl.schedule.model.TripStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ScheduleSafetyValidator {

    private static final Logger log = LoggerFactory.getLogger(ScheduleSafetyValidator.class);

    @Value("${schedule.min-departure-headway-seconds:420}")
    private int minDepartureHeadwaySeconds;

    @Value("${schedule.min-arrival-headway-seconds:420}")
    private int minArrivalHeadwaySeconds;

    @Value("${schedule.min-section-separation-seconds:120}")
    private int minSectionSeparationSeconds;

    @Value("${schedule.min-turnaround-minutes:3}")
    private int minTurnaroundMinutes;

    @Value("${schedule.min-dwell-minutes:2}")
    private int minDwellMinutes;

    @Value("${schedule.max-terminal-idle-minutes:60}")
    private int maxTerminalIdleMinutes;

    public boolean validateTrackOccupancy(ScheduleTrip newTrip, List<ScheduleTrip> dayTrips, StringBuilder reason) {
        if (newTrip == null || newTrip.getRouteName() == null) return true;
        int minSeparationMin = Math.max(2, minSectionSeparationSeconds / 60);

        for (ScheduleTrip existing : dayTrips) {
            if (Objects.equals(existing.getId(), newTrip.getId())) continue;
            if (existing.getStatus() == TripStatus.CANCELLED || existing.getStatus() == TripStatus.MISSED) continue;

            if (normalizeRoute(existing.getRouteName()).equals(normalizeRoute(newTrip.getRouteName()))) {
                int depGap = Math.abs(existing.getStartMinutes() - newTrip.getStartMinutes());
                if (depGap < minSeparationMin) {
                    if (reason != null) {
                        reason.append("Track separation conflict with trip ").append(existing.getTripCode())
                              .append(" [").append(existing.getStartTime()).append(" - ").append(existing.getEndTime()).append("]")
                              .append(": departure gap is ").append(depGap).append(" min (minimum ").append(minSeparationMin).append(" min required).");
                    }
                    return false;
                }

                int arrGap = Math.abs(existing.getEndMinutes() - newTrip.getEndMinutes());
                if (arrGap < minSeparationMin) {
                    if (reason != null) {
                        reason.append("Track separation conflict with trip ").append(existing.getTripCode())
                              .append(" [").append(existing.getStartTime()).append(" - ").append(existing.getEndTime()).append("]")
                              .append(": arrival gap is ").append(arrGap).append(" min (minimum ").append(minSeparationMin).append(" min required).");
                    }
                    return false;
                }

                if (existing.getStartMinutes() < newTrip.getStartMinutes() && existing.getEndMinutes() >= newTrip.getEndMinutes()) {
                    if (reason != null) {
                        reason.append("Track overtake hazard: Trip ").append(existing.getTripCode())
                              .append(" departs earlier (").append(existing.getStartTime()).append(") but arrives at/after proposed trip (")
                              .append(existing.getEndTime()).append(" vs ").append(newTrip.getEndTime()).append(") on the same track.");
                    }
                    return false;
                }
                if (newTrip.getStartMinutes() < existing.getStartMinutes() && newTrip.getEndMinutes() >= existing.getEndMinutes()) {
                    if (reason != null) {
                        reason.append("Track overtake hazard: Proposed trip departs earlier (")
                              .append(newTrip.getStartTime()).append(") but arrives at/after existing trip ")
                              .append(existing.getTripCode()).append(" (").append(newTrip.getEndTime()).append(" vs ")
                              .append(existing.getEndTime()).append(") on the same track.");
                    }
                    return false;
                }
            }
        }
        return true;
    }

    public boolean validatePlatformOccupancy(ScheduleTrip newTrip, List<ScheduleTrip> dayTrips, StringBuilder reason) {
        return validatePlatformOccupancy(newTrip, dayTrips, null, reason);
    }

    public boolean validatePlatformOccupancy(ScheduleTrip newTrip, List<ScheduleTrip> dayTrips,
                                             Map<String, List<ScheduleTrip>> tripsByTrain, StringBuilder reason) {
        Map<String, List<ScheduleTrip>> byTrain;
        if (tripsByTrain != null) {
            byTrain = new HashMap<>(tripsByTrain.size() + 1);
            for (Map.Entry<String, List<ScheduleTrip>> entry : tripsByTrain.entrySet()) {
                byTrain.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            if (newTrip.getAssignedTrainId() != null && !newTrip.getAssignedTrainId().equals("Train Not Assigned")) {
                byTrain.computeIfAbsent(newTrip.getAssignedTrainId(), k -> new ArrayList<>()).add(newTrip);
            }
        } else {

            List<ScheduleTrip> trips = new ArrayList<>(dayTrips);
            trips.removeIf(t -> t.getId().equals(newTrip.getId()));
            trips.add(newTrip);
            trips.removeIf(t -> t.getStatus() == TripStatus.CANCELLED || t.getStatus() == TripStatus.MISSED);

            byTrain = trips.stream()
                    .filter(t -> t.getAssignedTrainId() != null && !t.getAssignedTrainId().equals("Train Not Assigned"))
                    .collect(Collectors.groupingBy(ScheduleTrip::getAssignedTrainId));
        }

        byTrain.values().forEach(list -> list.sort(Comparator.comparingInt(ScheduleTrip::getStartMinutes)));

        int checkStart = Math.max(0, newTrip.getStartMinutes() - 10);
        int checkEnd   = Math.min(1440, newTrip.getEndMinutes() + 10);

        for (int m = checkStart; m <= checkEnd; m += 2) {
            int aluvaCount = 0;
            int thrippuniCount = 0;

            for (Map.Entry<String, List<ScheduleTrip>> entry : byTrain.entrySet()) {
                List<ScheduleTrip> trainTrips = entry.getValue();
                if (trainTrips == null || trainTrips.isEmpty()) continue;

                String loc = getTrainLocationAtMinute(trainTrips, m);
                if ("Aluva".equals(loc)) {
                    aluvaCount++;
                } else if ("Thrippunithura".equals(loc)) {
                    thrippuniCount++;
                }
            }

            if (aluvaCount > 2) {
                if (reason != null) {
                    reason.append("Platform count at Aluva exceeds capacity limit (2) at minute ").append(m)
                          .append(" (").append(formatMinutesToTime(m)).append(").");
                }
                return false;
            }

            if (thrippuniCount > 3) {
                if (reason != null) {
                    reason.append("Platform count at Thrippunithura exceeds capacity limit (3) at minute ").append(m)
                          .append(" (").append(formatMinutesToTime(m)).append(").");
                }
                return false;
            }
        }
        return true;
    }

    private String getTrainLocationAtMinute(List<ScheduleTrip> sortedTrips, int m) {

        ScheduleTrip firstTrip = sortedTrips.get(0);
        if (m < firstTrip.getStartMinutes()) {
            if (m >= firstTrip.getStartMinutes() - minDwellMinutes) {
                return getStartStation(firstTrip);
            }
            return "MUTTOM";
        }

        ScheduleTrip lastTrip = sortedTrips.get(sortedTrips.size() - 1);
        if (m >= lastTrip.getEndMinutes()) {
            if (m < lastTrip.getEndMinutes() + minTurnaroundMinutes) {
                return getEndStation(lastTrip);
            }
            return "MUTTOM";
        }

        for (ScheduleTrip trip : sortedTrips) {
            if (m >= trip.getStartMinutes() && m < trip.getEndMinutes()) {

                if (m < trip.getStartMinutes() + minDwellMinutes) {
                    return getStartStation(trip);
                }
                if (m >= trip.getEndMinutes() - minDwellMinutes) {
                    return getEndStation(trip);
                }
                return "TRANSIT";
            }
        }

        for (int i = 0; i < sortedTrips.size() - 1; i++) {
            ScheduleTrip curr = sortedTrips.get(i);
            ScheduleTrip next = sortedTrips.get(i + 1);

            if (m >= curr.getEndMinutes() && m < next.getStartMinutes()) {
                String arrivalStation = getEndStation(curr);
                String nextStation = getStartStation(next);

                if (m < curr.getEndMinutes() + minTurnaroundMinutes) {
                    return arrivalStation;
                }

                if (m >= next.getStartMinutes() - minDwellMinutes) {
                    return nextStation;
                }

                return "POCKET_SIDING";
            }
        }

        return "MUTTOM";
    }

    private String getStartStation(ScheduleTrip trip) {
        if (trip == null || trip.getRouteName() == null) {
            return "Aluva";
        }
        return trip.getRouteName().contains("Aluva to Thrippunithura") ? "Aluva" : "Thrippunithura";
    }

    private String getEndStation(ScheduleTrip trip) {
        if (trip == null || trip.getRouteName() == null) {
            return "Thrippunithura";
        }
        return trip.getRouteName().contains("Aluva to Thrippunithura") ? "Thrippunithura" : "Aluva";
    }

    public boolean validateHeadways(ScheduleTrip newTrip, List<ScheduleTrip> dayTrips, StringBuilder reason) {
        int floorDepartureMin = minDepartureHeadwaySeconds / 60;
        int floorArrivalMin = minArrivalHeadwaySeconds / 60;

        for (ScheduleTrip existing : dayTrips) {
            if (Objects.equals(existing.getId(), newTrip.getId())) continue;
            if (existing.getStatus() == TripStatus.CANCELLED || existing.getStatus() == TripStatus.MISSED) continue;

            if (normalizeRoute(existing.getRouteName()).equals(normalizeRoute(newTrip.getRouteName()))) {
                int depGap = Math.abs(existing.getStartMinutes() - newTrip.getStartMinutes());
                if (depGap < floorDepartureMin) {
                    if (reason != null) {
                        reason.append("Departure headway violation: ").append(depGap)
                              .append(" min gap with ").append(existing.getTripCode())
                              .append(" (minimum ").append(floorDepartureMin).append(" min required).");
                    }
                    return false;
                }

                int arrGap = Math.abs(existing.getEndMinutes() - newTrip.getEndMinutes());
                if (arrGap < floorArrivalMin) {
                    if (reason != null) {
                        reason.append("Arrival headway violation: ").append(arrGap)
                              .append(" min gap with ").append(existing.getTripCode())
                              .append(" (minimum ").append(floorArrivalMin).append(" min required).");
                    }
                    return false;
                }
            }
        }
        return true;
    }

    public boolean validateTrainTurnaroundAndOverlap(ScheduleTrip newTrip, List<ScheduleTrip> trainTrips, StringBuilder reason) {
        for (ScheduleTrip existing : trainTrips) {
            if (Objects.equals(existing.getId(), newTrip.getId())) continue;
            if (existing.getStatus() == TripStatus.CANCELLED || existing.getStatus() == TripStatus.MISSED) continue;

            boolean overlap = !(newTrip.getEndMinutes() <= existing.getStartMinutes() || newTrip.getStartMinutes() >= existing.getEndMinutes());
            if (overlap) {
                if (reason != null) {
                    reason.append("Train duty overlap with trip ").append(existing.getTripCode())
                          .append(" [").append(existing.getStartTime()).append(" - ").append(existing.getEndTime()).append("].");
                }
                return false;
            }

            if (newTrip.getEndMinutes() <= existing.getStartMinutes()) {
                int gap = existing.getStartMinutes() - newTrip.getEndMinutes();
                if (gap < minTurnaroundMinutes) {
                    if (reason != null) {
                        reason.append("Insufficient turnaround buffer before ").append(existing.getTripCode())
                              .append(": only ").append(gap).append(" mins (required ").append(minTurnaroundMinutes).append(" mins).");
                    }
                    return false;
                }
            }

            if (newTrip.getStartMinutes() >= existing.getEndMinutes()) {
                int gap = newTrip.getStartMinutes() - existing.getEndMinutes();
                if (gap < minTurnaroundMinutes) {
                    if (reason != null) {
                        reason.append("Insufficient turnaround buffer after ").append(existing.getTripCode())
                              .append(": only ").append(gap).append(" mins (required ").append(minTurnaroundMinutes).append(" mins).");
                    }
                    return false;
                }
            }
        }
        return true;
    }

    public boolean validateTrainDirectionContinuity(ScheduleTrip newTrip, List<ScheduleTrip> trainTrips, StringBuilder reason) {
        List<ScheduleTrip> sorted = new ArrayList<>(trainTrips);
        sorted.removeIf(t -> t.getId().equals(newTrip.getId()));
        sorted.add(newTrip);
        sorted.removeIf(t -> t.getStatus() == TripStatus.CANCELLED || t.getStatus() == TripStatus.MISSED);
        sorted.sort(Comparator.comparingInt(ScheduleTrip::getStartMinutes));

        for (int i = 0; i < sorted.size() - 1; i++) {
            ScheduleTrip first = sorted.get(i);
            ScheduleTrip second = sorted.get(i + 1);

            String endFirst = getEndStation(first);
            String startSecond = getStartStation(second);

            int gap = second.getStartMinutes() - first.getEndMinutes();
            if (gap <= maxTerminalIdleMinutes) {

                if (!endFirst.equals(startSecond)) {
                    if (reason != null) {
                        reason.append("Direction continuity error: Train ends ").append(first.getTripCode())
                              .append(" at ").append(endFirst).append(" but starts ").append(second.getTripCode())
                              .append(" at ").append(startSecond).append(".");
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private static String normalizeRoute(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }

    private String formatMinutesToTime(int totalMinutes) {
        int hrs = totalMinutes / 60;
        int mins = totalMinutes % 60;
        return String.format("%02d:%02d", hrs, mins);
    }
}

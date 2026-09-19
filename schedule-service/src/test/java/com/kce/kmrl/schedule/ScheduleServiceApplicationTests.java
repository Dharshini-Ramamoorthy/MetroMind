package com.kce.kmrl.schedule;

import com.kce.kmrl.schedule.client.ResilientFleetClient;
import com.kce.kmrl.schedule.dto.TrainAssetDto;
import com.kce.kmrl.schedule.model.ScheduleTrip;
import com.kce.kmrl.schedule.model.TripStatus;
import com.kce.kmrl.schedule.service.ScheduleSafetyValidator;
import com.kce.kmrl.schedule.service.TrainAssignmentService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SpringBootTest(properties = {
    "schedule.security.gateway-secret=test-gateway-secret",
    "internal.service-secret=test-internal-secret"
})
class ScheduleServiceApplicationTests {

    @Autowired
    private ScheduleSafetyValidator safetyValidator;

    @Autowired
    private TrainAssignmentService trainAssignmentService;

    @MockBean
    private ResilientFleetClient fleetClient;

    @Test
    void contextLoads() {
        Assertions.assertNotNull(safetyValidator);
        Assertions.assertNotNull(trainAssignmentService);
    }

    @Test
    void testTrainOverlapConflict() {

        ScheduleTrip t1 = new ScheduleTrip();
        t1.setId("TR-1");
        t1.setTripCode("RUN-AI-001");
        t1.setRouteName("Aluva to Thrippunithura");
        t1.setStartMinutes(360);
        t1.setEndMinutes(405);
        t1.setStartTime("06:00");
        t1.setEndTime("06:45");
        t1.setAssignedTrainId("TS-01");
        t1.setStatus(TripStatus.PROPOSED);

        ScheduleTrip t2 = new ScheduleTrip();
        t2.setId("TR-2");
        t2.setTripCode("RUN-AI-002");
        t2.setRouteName("Thrippunithura to Aluva");
        t2.setStartMinutes(380);
        t2.setEndMinutes(425);
        t2.setStartTime("06:20");
        t2.setEndTime("07:05");
        t2.setAssignedTrainId("TS-01");
        t2.setStatus(TripStatus.PROPOSED);

        StringBuilder reason = new StringBuilder();
        boolean valid = safetyValidator.validateTrainTurnaroundAndOverlap(t2, Arrays.asList(t1), reason);
        Assertions.assertFalse(valid);
        Assertions.assertTrue(reason.toString().contains("overlap"));
    }

    @Test
    void testInsufficientTurnaround() {

        ScheduleTrip t1 = new ScheduleTrip();
        t1.setId("TR-1");
        t1.setTripCode("RUN-AI-001");
        t1.setRouteName("Aluva to Thrippunithura");
        t1.setStartMinutes(360);
        t1.setEndMinutes(405);
        t1.setStartTime("06:00");
        t1.setEndTime("06:45");
        t1.setAssignedTrainId("TS-01");
        t1.setStatus(TripStatus.PROPOSED);

        ScheduleTrip t2 = new ScheduleTrip();
        t2.setId("TR-2");
        t2.setTripCode("RUN-AI-002");
        t2.setRouteName("Thrippunithura to Aluva");
        t2.setStartMinutes(407);
        t2.setEndMinutes(452);
        t2.setStartTime("06:47");
        t2.setEndTime("07:32");
        t2.setAssignedTrainId("TS-01");
        t2.setStatus(TripStatus.PROPOSED);

        StringBuilder reason = new StringBuilder();
        boolean valid = safetyValidator.validateTrainTurnaroundAndOverlap(t2, Arrays.asList(t1), reason);
        Assertions.assertFalse(valid);
        Assertions.assertTrue(reason.toString().contains("turnaround"));
    }

    @Test
    void testTrackSeparationConflict() {

        ScheduleTrip t1 = new ScheduleTrip();
        t1.setId("TR-1");
        t1.setTripCode("RUN-AI-001");
        t1.setRouteName("Aluva to Thrippunithura");
        t1.setStartMinutes(360);
        t1.setEndMinutes(405);
        t1.setStatus(TripStatus.PROPOSED);

        ScheduleTrip t2 = new ScheduleTrip();
        t2.setId("TR-2");
        t2.setTripCode("RUN-AI-003");
        t2.setRouteName("Aluva to Thrippunithura");
        t2.setStartMinutes(361);
        t2.setEndMinutes(406);
        t2.setStatus(TripStatus.PROPOSED);

        StringBuilder reason = new StringBuilder();
        boolean valid = safetyValidator.validateTrackOccupancy(t2, Arrays.asList(t1), reason);
        Assertions.assertFalse(valid);
        Assertions.assertTrue(reason.toString().contains("Track separation conflict") || reason.toString().contains("gap is 1 min"));
    }

    @Test
    void testHeadwayFloorRejectionInPrimaryAssignment() {

        ScheduleTrip t1 = new ScheduleTrip();
        t1.setId("TR-1");
        t1.setTripCode("RUN-AI-001");
        t1.setRouteName("Aluva to Thrippunithura");
        t1.setStartMinutes(420);
        t1.setEndMinutes(465);
        t1.setStartTime("07:00");
        t1.setEndTime("07:45");
        t1.setAssignedTrainId("TS-01");
        t1.setStatus(TripStatus.PROPOSED);

        ScheduleTrip t2 = new ScheduleTrip();
        t2.setId("TR-2");
        t2.setTripCode("RUN-AI-002");
        t2.setRouteName("Aluva to Thrippunithura");
        t2.setStartMinutes(424);
        t2.setEndMinutes(469);
        t2.setStartTime("07:04");
        t2.setEndTime("07:49");
        t2.setServiceDate("2026-08-21");

        TrainAssetDto train = new TrainAssetDto();
        train.setId("TS-02");
        train.setTrainNumber("KMRL SET 02");
        train.setStatus("OPERATIONAL");
        train.setTotalMileageKm(10000);

        Mockito.when(fleetClient.checkTrainAvailability(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(true);

        StringBuilder reason = new StringBuilder();
        TrainAssetDto assigned = trainAssignmentService.findSafeTrain(t2, Arrays.asList(t1), Arrays.asList(train), reason);

        Assertions.assertNull(assigned, "Trip placed closer than 7-min headway floor must be rejected at primary assignment");
        Assertions.assertTrue(reason.toString().contains("Headway violation"), "Reason must state headway violation");
    }

    @Test
    void testRouteSubstitutionHeadwayRejection() {

        ScheduleTrip t1 = new ScheduleTrip();
        t1.setId("TR-1");
        t1.setTripCode("RUN-AI-001");
        t1.setRouteName("Thrippunithura to Aluva");
        t1.setStartMinutes(420);
        t1.setEndMinutes(465);
        t1.setStartTime("07:00");
        t1.setEndTime("07:45");
        t1.setAssignedTrainId("TS-01");
        t1.setStatus(TripStatus.PROPOSED);

        ScheduleTrip t2 = new ScheduleTrip();
        t2.setId("TR-2");
        t2.setTripCode("RUN-AI-002");
        t2.setRouteName("Thrippunithura to Aluva");
        t2.setStartMinutes(423);
        t2.setEndMinutes(468);
        t2.setStartTime("07:03");
        t2.setEndTime("07:48");
        t2.setServiceDate("2026-08-21");

        TrainAssetDto train = new TrainAssetDto();
        train.setId("TS-02");
        train.setTrainNumber("KMRL SET 02");
        train.setStatus("OPERATIONAL");
        train.setTotalMileageKm(10000);

        StringBuilder reason = new StringBuilder();
        TrainAssetDto assigned = trainAssignmentService.findSafeTrain(t2, Arrays.asList(t1), Arrays.asList(train), reason);

        Assertions.assertNull(assigned, "Route-substituted trip closer than headway floor must be rejected");
        Assertions.assertTrue(reason.toString().contains("Headway violation"));
    }

    @Test
    void testPlatformOverCapacityAluva() {

        ScheduleTrip t1 = new ScheduleTrip();
        t1.setId("TR-1");
        t1.setTripCode("RUN-AI-001");
        t1.setRouteName("Thrippunithura to Aluva");
        t1.setStartMinutes(500);
        t1.setEndMinutes(545);
        t1.setAssignedTrainId("TS-01");
        t1.setStatus(TripStatus.PROPOSED);

        ScheduleTrip t2 = new ScheduleTrip();
        t2.setId("TR-2");
        t2.setTripCode("RUN-AI-002");
        t2.setRouteName("Thrippunithura to Aluva");
        t2.setStartMinutes(501);
        t2.setEndMinutes(546);
        t2.setAssignedTrainId("TS-02");
        t2.setStatus(TripStatus.PROPOSED);

        ScheduleTrip t3 = new ScheduleTrip();
        t3.setId("TR-3");
        t3.setTripCode("RUN-AI-003");
        t3.setRouteName("Thrippunithura to Aluva");
        t3.setStartMinutes(502);
        t3.setEndMinutes(547);
        t3.setAssignedTrainId("TS-03");
        t3.setStatus(TripStatus.PROPOSED);

        ScheduleTrip dummy = new ScheduleTrip();
        dummy.setId("DUMMY");
        dummy.setStartMinutes(546);
        dummy.setEndMinutes(591);
        dummy.setRouteName("Aluva to Thrippunithura");
        dummy.setAssignedTrainId("TS-04");

        StringBuilder reason = new StringBuilder();
        boolean valid = safetyValidator.validatePlatformOccupancy(dummy, Arrays.asList(t1, t2, t3), reason);
        Assertions.assertFalse(valid);
        Assertions.assertTrue(reason.toString().contains("Platform count at Aluva"));
    }
}


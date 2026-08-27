package com.kce.kmrl.maintenance;

import com.kce.kmrl.dto.CertificateOfFitnessRequest;
import com.kce.kmrl.dto.CreateTicketRequest;
import com.kce.kmrl.dto.MaintenanceResponse;
import com.kce.kmrl.entity.Maintenance;
import com.kce.kmrl.entity.MaintenanceStatus;
import com.kce.kmrl.entity.RepairType;
import com.kce.kmrl.entity.TrainWithdrawalStatus;
import com.kce.kmrl.exception.InvalidRequestException;
import com.kce.kmrl.exception.TicketNotFoundException;
import com.kce.kmrl.exception.WithdrawalFailedException;
import com.kce.kmrl.repository.MaintenanceRepository;
import com.kce.kmrl.service.MaintenanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MaintenanceServiceImplTest {

    private MaintenanceRepository repository;
    private MaintenanceServiceImpl service;

    @TempDir
    Path cofUploadDir;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(MaintenanceRepository.class);
        RestClient.Builder restClientBuilder = RestClient.builder();
        service = new MaintenanceServiceImpl(repository, restClientBuilder, cofUploadDir.toString());
    }

    private MultipartFile validCofDocument() {
        return new MockMultipartFile(
                "document", "cof.pdf", "application/pdf",
                "not a real pdf, just test bytes".getBytes());
    }

    private MultipartFile emptyDocument() {
        return new MockMultipartFile(
                "document", "empty.pdf", "application/pdf", new byte[0]);
    }

    @Nested
    class RoutineCheckCreation {

        @Test
        void createsScheduledTicket_withFutureDate() {
            CreateTicketRequest req = new CreateTicketRequest();
            req.setTrainNumber("KMRL-101");
            req.setDescription("Routine brake inspection");
            req.setRepairType(RepairType.ROUTINE_CHECK);
            req.setPlannedMaintenanceDate(LocalDate.now().plusDays(3));
            req.setCreatedBy("mds-user");

            when(repository.findByTrainNumber("KMRL-101")).thenReturn(List.of());
            Maintenance saved = new Maintenance();
            saved.setId("rc-001");
            saved.setTrainNumber("KMRL-101");
            saved.setRepairType(RepairType.ROUTINE_CHECK);
            saved.setStatus(MaintenanceStatus.SCHEDULED);
            saved.setPlannedMaintenanceDate(req.getPlannedMaintenanceDate());
            saved.setTrainPulled(false);
            saved.setWithdrawalStatus(TrainWithdrawalStatus.NOT_REQUIRED);
            when(repository.save(any())).thenReturn(saved);

            MaintenanceResponse res = service.createTicket(req);

            assertEquals(MaintenanceStatus.SCHEDULED, res.getStatus());
            assertEquals(RepairType.ROUTINE_CHECK, res.getRepairType());
            assertFalse(res.isTrainPulled());
            assertEquals(TrainWithdrawalStatus.NOT_REQUIRED, res.getWithdrawalStatus());
        }

        @Test
        void rejectsRoutineCheck_withoutPlannedDate() {
            CreateTicketRequest req = new CreateTicketRequest();
            req.setTrainNumber("KMRL-102");
            req.setDescription("Missing date");
            req.setRepairType(RepairType.ROUTINE_CHECK);
            req.setPlannedMaintenanceDate(null);
            req.setCreatedBy("mds-user");

            when(repository.findByTrainNumber("KMRL-102")).thenReturn(List.of());

            assertThrows(InvalidRequestException.class, () -> service.createTicket(req));
        }

        @Test
        void rejectsRoutineCheck_withPastDate() {
            CreateTicketRequest req = new CreateTicketRequest();
            req.setTrainNumber("KMRL-103");
            req.setDescription("Past date");
            req.setRepairType(RepairType.ROUTINE_CHECK);
            req.setPlannedMaintenanceDate(LocalDate.now().minusDays(1));
            req.setCreatedBy("mds-user");

            when(repository.findByTrainNumber("KMRL-103")).thenReturn(List.of());

            assertThrows(InvalidRequestException.class, () -> service.createTicket(req));
        }

        @Test
        void rejectsOtherRepairType_withPlannedDate() {
            CreateTicketRequest req = new CreateTicketRequest();
            req.setTrainNumber("KMRL-104");
            req.setDescription("Corrective with date");
            req.setRepairType(RepairType.CORRECTIVE);
            req.setPlannedMaintenanceDate(LocalDate.now().plusDays(2));
            req.setCreatedBy("mds-user");

            when(repository.findByTrainNumber("KMRL-104")).thenReturn(List.of());

            assertThrows(InvalidRequestException.class, () -> service.createTicket(req));
        }
    }

    @Nested
    class ImmediateWithdrawalCreation {

        @Test
        void rejectsCreation_whenUnresolvedTicketExists() {
            Maintenance existing = buildTicket("existing", "KMRL-200", MaintenanceStatus.IN_PROGRESS);
            when(repository.findByTrainNumber("KMRL-200")).thenReturn(List.of(existing));

            CreateTicketRequest req = new CreateTicketRequest();
            req.setTrainNumber("KMRL-200");
            req.setDescription("Duplicate");
            req.setRepairType(RepairType.CORRECTIVE);
            req.setCreatedBy("sada-user");

            assertThrows(InvalidRequestException.class, () -> service.createTicket(req));
        }

        @Test
        void rejectsCreation_whenScheduledRoutineCheckExists() {
            Maintenance existing = buildTicketWithRepairType(
                    "rc-existing", "KMRL-201", MaintenanceStatus.SCHEDULED, RepairType.ROUTINE_CHECK);
            existing.setTrainPulled(false);
            when(repository.findByTrainNumber("KMRL-201")).thenReturn(List.of(existing));

            CreateTicketRequest req = new CreateTicketRequest();
            req.setTrainNumber("KMRL-201");
            req.setDescription("Another ticket while SCHEDULED exists");
            req.setRepairType(RepairType.CORRECTIVE);
            req.setCreatedBy("sada-user");

            assertThrows(InvalidRequestException.class, () -> service.createTicket(req));
        }
    }

    @Nested
    class BeginRoutineMaintenance {

        @Test
        void rejects_whenTicketNotScheduled() {
            Maintenance ticket = buildTicketWithRepairType(
                    "rc-ip", "KMRL-300", MaintenanceStatus.IN_PROGRESS, RepairType.ROUTINE_CHECK);
            when(repository.findById("rc-ip")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class,
                    () -> service.beginRoutineMaintenance("rc-ip"));
        }

        @Test
        void rejects_whenRepairTypeNotRoutineCheck() {
            Maintenance ticket = buildTicketWithRepairType(
                    "wrong-type", "KMRL-301", MaintenanceStatus.SCHEDULED, RepairType.CORRECTIVE);
            when(repository.findById("wrong-type")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class,
                    () -> service.beginRoutineMaintenance("wrong-type"));
        }

        @Test
        void rejects_whenCalledBeforePlannedDate() {
            Maintenance ticket = buildTicketWithRepairType(
                    "future", "KMRL-302", MaintenanceStatus.SCHEDULED, RepairType.ROUTINE_CHECK);
            ticket.setPlannedMaintenanceDate(LocalDate.now().plusDays(5));
            when(repository.findById("future")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class,
                    () -> service.beginRoutineMaintenance("future"));
        }
    }

    @Nested
    class ApproverDecision {

        @Test
        void acceptsCompleted_fromPendingClosure() {
            Maintenance ticket = buildTicket("ad-001", "KMRL-400", MaintenanceStatus.PENDING_CLOSURE);
            when(repository.findById("ad-001")).thenReturn(Optional.of(ticket));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            MaintenanceResponse res = service.applyApproverDecision("ad-001", "COMPLETED", "Looks good");
            assertEquals(MaintenanceStatus.COMPLETED, res.getStatus());
        }

        @Test
        void acceptsInProgress_fromPendingClosure() {
            Maintenance ticket = buildTicket("ad-002", "KMRL-401", MaintenanceStatus.PENDING_CLOSURE);
            when(repository.findById("ad-002")).thenReturn(Optional.of(ticket));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            MaintenanceResponse res = service.applyApproverDecision("ad-002", "IN_PROGRESS", "Redo the work");
            assertEquals(MaintenanceStatus.IN_PROGRESS, res.getStatus());
        }

        @Test
        void rejects_invalidDecisionStatus() {
            Maintenance ticket = buildTicket("ad-003", "KMRL-402", MaintenanceStatus.PENDING_CLOSURE);
            when(repository.findById("ad-003")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class,
                    () -> service.applyApproverDecision("ad-003", "CANCELLED", null));
        }

        @Test
        void throwsTicketNotFound() {
            when(repository.findById("ghost")).thenReturn(Optional.empty());
            assertThrows(TicketNotFoundException.class,
                    () -> service.applyApproverDecision("ghost", "COMPLETED", null));
        }
    }

    @Nested
    class CertificateOfFitness {

        @Test
        void rejectsCoF_whenTicketNotInProgress() {
            Maintenance ticket = buildTicket("cof-bad-state", "KMRL-500", MaintenanceStatus.PENDING_CLOSURE);
            when(repository.findById("cof-bad-state")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class,
                    () -> service.submitCertificateOfFitness("cof-bad-state",
                            cofRequest("Eng"), validCofDocument()));
        }

        @Test
        void rejectsCoF_whenDocumentMissing() {
            Maintenance ticket = buildTicket("cof-nodoc", "KMRL-501", MaintenanceStatus.IN_PROGRESS);
            when(repository.findById("cof-nodoc")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class,
                    () -> service.submitCertificateOfFitness("cof-nodoc", cofRequest("Eng"), null));
        }

        @Test
        void rejectsCoF_whenDocumentEmpty() {
            Maintenance ticket = buildTicket("cof-empty", "KMRL-502", MaintenanceStatus.IN_PROGRESS);
            when(repository.findById("cof-empty")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class,
                    () -> service.submitCertificateOfFitness("cof-empty", cofRequest("Eng"), emptyDocument()));
        }

        @Test
        void acceptsCoF_withValidDocument() {
            Maintenance ticket = buildTicket("cof-ok", "KMRL-503", MaintenanceStatus.IN_PROGRESS);
            when(repository.findById("cof-ok")).thenReturn(Optional.of(ticket));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            MaintenanceResponse res = service.submitCertificateOfFitness(
                    "cof-ok", cofRequest("Engineer A"), validCofDocument());

            assertEquals(MaintenanceStatus.PENDING_CLOSURE, res.getStatus());
        }

        private CertificateOfFitnessRequest cofRequest(String name) {
            CertificateOfFitnessRequest r = new CertificateOfFitnessRequest();
            r.setEngineerName(name);
            r.setRemarks("All checks passed");
            return r;
        }
    }

    @Nested
    class RetryApproval {

        @Test
        void retryApproval_throwsWhenNotPendingClosure() {
            Maintenance ticket = buildTicket("id-retry-bad", "KMRL-600", MaintenanceStatus.IN_PROGRESS);
            ticket.setApprovalSubmissionFailed(true);
            when(repository.findById("id-retry-bad")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class,
                    () -> service.retryApprovalSubmission("id-retry-bad"));
        }

        @Test
        void retryApproval_throwsWhenFlagNotSet() {
            Maintenance ticket = buildTicket("id-retry-noflag", "KMRL-601", MaintenanceStatus.PENDING_CLOSURE);
            ticket.setApprovalSubmissionFailed(false);
            when(repository.findById("id-retry-noflag")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class,
                    () -> service.retryApprovalSubmission("id-retry-noflag"));
        }
    }

    @Nested
    class DeletionGuard {

        @Test
        void allowsDeletionOfOpenTicket() {
            Maintenance ticket = buildTicket("del-open", "KMRL-700", MaintenanceStatus.OPEN);
            ticket.setTrainPulled(false);
            when(repository.findById("del-open")).thenReturn(Optional.of(ticket));
            when(repository.findByTrainNumber("KMRL-700")).thenReturn(List.of());

            assertDoesNotThrow(() -> service.deleteTicket("del-open"));
            Mockito.verify(repository).deleteById("del-open");
        }

        @Test
        void allowsDeletionOfScheduledRoutineCheck() {
            Maintenance ticket = buildTicketWithRepairType(
                    "del-sched", "KMRL-701", MaintenanceStatus.SCHEDULED, RepairType.ROUTINE_CHECK);
            ticket.setTrainPulled(false);
            when(repository.findById("del-sched")).thenReturn(Optional.of(ticket));
            when(repository.findByTrainNumber("KMRL-701")).thenReturn(List.of());

            assertDoesNotThrow(() -> service.deleteTicket("del-sched"));
            Mockito.verify(repository).deleteById("del-sched");
        }

        @Test
        void allowsDeletionOfInProgressTicket() {
            Maintenance ticket = buildTicket("del-ip", "KMRL-702", MaintenanceStatus.IN_PROGRESS);
            ticket.setTrainPulled(true);
            when(repository.findById("del-ip")).thenReturn(Optional.of(ticket));
            when(repository.findByTrainNumber("KMRL-702")).thenReturn(List.of());

            assertDoesNotThrow(() -> service.deleteTicket("del-ip"));
            Mockito.verify(repository).deleteById("del-ip");
        }

        @Test
        void rejectsDeletionOfPendingClosureTicket() {
            Maintenance ticket = buildTicket("del-pc", "KMRL-703", MaintenanceStatus.PENDING_CLOSURE);
            when(repository.findById("del-pc")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class, () -> service.deleteTicket("del-pc"));
            Mockito.verify(repository, Mockito.never()).deleteById(any());
        }

        @Test
        void rejectsDeletionOfCompletedTicket() {
            Maintenance ticket = buildTicket("del-done", "KMRL-704", MaintenanceStatus.COMPLETED);
            when(repository.findById("del-done")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class, () -> service.deleteTicket("del-done"));
        }

        @Test
        void rejectsDeletionOfCancelledTicket() {
            Maintenance ticket = buildTicket("del-cancel", "KMRL-705", MaintenanceStatus.CANCELLED);
            when(repository.findById("del-cancel")).thenReturn(Optional.of(ticket));

            assertThrows(InvalidRequestException.class, () -> service.deleteTicket("del-cancel"));
        }
    }

    @Test
    void getAllTickets_mapsEntitiesToResponses() {
        Maintenance ticket = new Maintenance();
        ticket.setId("id-all");
        ticket.setTrainNumber("KMRL-205");
        ticket.setRepairType(RepairType.CORRECTIVE);
        when(repository.findAll()).thenReturn(List.of(ticket));

        List<MaintenanceResponse> results = service.getAllTickets();

        assertEquals(1, results.size());
        assertEquals("KMRL-205", results.get(0).getTrainNumber());
    }

    @Test
    void getTicketById_throwsWhenNotFound() {
        when(repository.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(TicketNotFoundException.class, () -> service.getTicketById("ghost"));
    }

    @Test
    void repairTypeFromString_rejectsInvalidValue() {
        assertThrows(InvalidRequestException.class, () -> RepairType.fromString("URGENT-ISH"));
    }

    @Test
    void repairTypeFromString_isCaseInsensitive() {
        assertEquals(RepairType.CORRECTIVE, RepairType.fromString("corrective"));
    }

    @Test
    void routineCheck_doesNotRequireImmediateWithdrawal() {
        assertFalse(RepairType.ROUTINE_CHECK.requiresImmediateWithdrawal());
    }

    @Test
    void otherRepairTypes_requireImmediateWithdrawal() {
        assertTrue(RepairType.CORRECTIVE.requiresImmediateWithdrawal());
        assertTrue(RepairType.EMERGENCY.requiresImmediateWithdrawal());
        assertTrue(RepairType.OVERHAUL.requiresImmediateWithdrawal());
    }

    @Test
    void maintenanceStatusFromString_rejectsInvalidValue() {
        assertThrows(InvalidRequestException.class, () -> MaintenanceStatus.fromString("DONE_ISH"));
    }

    @Test
    void assertValidTransition_allowsValidTransitions() {
        assertDoesNotThrow(() -> MaintenanceStatus.assertValidTransition(
                MaintenanceStatus.OPEN, MaintenanceStatus.IN_PROGRESS));
        assertDoesNotThrow(() -> MaintenanceStatus.assertValidTransition(
                MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS));
        assertDoesNotThrow(() -> MaintenanceStatus.assertValidTransition(
                MaintenanceStatus.SCHEDULED, MaintenanceStatus.CANCELLED));
        assertDoesNotThrow(() -> MaintenanceStatus.assertValidTransition(
                MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.PENDING_CLOSURE));
        assertDoesNotThrow(() -> MaintenanceStatus.assertValidTransition(
                MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.CANCELLED));
        assertDoesNotThrow(() -> MaintenanceStatus.assertValidTransition(
                MaintenanceStatus.PENDING_CLOSURE, MaintenanceStatus.COMPLETED));
        assertDoesNotThrow(() -> MaintenanceStatus.assertValidTransition(
                MaintenanceStatus.PENDING_CLOSURE, MaintenanceStatus.IN_PROGRESS));
    }

    @Test
    void assertValidTransition_rejectsInvalidTransitions() {
        assertThrows(InvalidRequestException.class, () ->
                MaintenanceStatus.assertValidTransition(MaintenanceStatus.OPEN, MaintenanceStatus.COMPLETED));
        assertThrows(InvalidRequestException.class, () ->
                MaintenanceStatus.assertValidTransition(MaintenanceStatus.COMPLETED, MaintenanceStatus.IN_PROGRESS));
        assertThrows(InvalidRequestException.class, () ->
                MaintenanceStatus.assertValidTransition(MaintenanceStatus.PENDING_CLOSURE, MaintenanceStatus.CANCELLED));
        assertThrows(InvalidRequestException.class, () ->
                MaintenanceStatus.assertValidTransition(MaintenanceStatus.CANCELLED, MaintenanceStatus.OPEN));
        assertThrows(InvalidRequestException.class, () ->
                MaintenanceStatus.assertValidTransition(MaintenanceStatus.SCHEDULED, MaintenanceStatus.COMPLETED));
    }

    private Maintenance buildTicket(String id, String trainNumber, MaintenanceStatus status) {
        return buildTicketWithRepairType(id, trainNumber, status, RepairType.CORRECTIVE);
    }

    private Maintenance buildTicketWithRepairType(String id, String trainNumber,
                                                   MaintenanceStatus status, RepairType repairType) {
        Maintenance m = new Maintenance();
        m.setId(id);
        m.setTrainNumber(trainNumber);
        m.setStatus(status);
        m.setRepairType(repairType);
        m.setTrainPulled(status == MaintenanceStatus.IN_PROGRESS
                || status == MaintenanceStatus.PENDING_CLOSURE);
        m.setWithdrawalStatus(repairType == RepairType.ROUTINE_CHECK
                ? TrainWithdrawalStatus.NOT_REQUIRED
                : TrainWithdrawalStatus.PULLED);
        return m;
    }
}

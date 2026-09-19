package com.kce.kmrl.schedule.client;

import com.kce.kmrl.schedule.dto.MaintenanceTicketDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@FeignClient(name = "maintenance-service", url = "${maintenance.service.url:http://maintenance-service:8084}")
public interface MaintenanceClient {

    @GetMapping("/api/v1/maintenance/tickets")
    List<MaintenanceTicketDto> getAllTickets();

    @PostMapping("/api/v1/maintenance/tickets/by-train/{trainNumber}/mark-pulled")
    void markTrainPulled(@PathVariable("trainNumber") String trainNumber);
}

package com.kce.kmrl.client;

import com.kce.kmrl.dto.MaintenanceTicketDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "maintenance-service")
public interface MaintenanceClient {

    @GetMapping("/api/v1/maintenance/tickets")
    List<MaintenanceTicketDto> getTickets();
}

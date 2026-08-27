package com.kce.kmrl.alert.service;

import com.kce.kmrl.alert.dto.AlertResponse;
import com.kce.kmrl.alert.dto.AlertSummaryResponse;
import com.kce.kmrl.alert.dto.LedgerEntryResponse;

import java.util.List;

public interface AlertService {

    List<AlertResponse> getAllAlerts(String callerRole, String callerUserId);

    AlertResponse getAlertById(String id, String callerRole, String callerUserId);

    LedgerEntryResponse clearAlert(String alertId, String operatorUserId, String operatorRole);

    String isolateAlert(String alertId, String operatorUserId, String operatorRole);

    AlertResponse dispatchAlert(String alertId, String operatorUserId, String operatorRole);

    List<LedgerEntryResponse> getLedger();

    AlertSummaryResponse getSummary(String callerRole, String callerUserId);
}

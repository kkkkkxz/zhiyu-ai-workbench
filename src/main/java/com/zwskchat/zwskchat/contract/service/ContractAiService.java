package com.zwskchat.zwskchat.contract.service;

import com.zwskchat.zwskchat.contract.entity.BizContract;
import com.zwskchat.zwskchat.contract.entity.BizPaymentReminder;

import java.util.List;
import java.util.Map;

public interface ContractAiService {

    BizContract analyzeRisk(Long contractId);

    List<BizPaymentReminder> generatePaymentReminders(int daysAhead);

    List<BizPaymentReminder> pendingPaymentReminders();

    Map<String, Object> paymentRiskSummary();
}

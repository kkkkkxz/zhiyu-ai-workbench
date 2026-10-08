package com.zwskchat.zwskchat.analytics.service;

import java.util.Map;

public interface AiRiskDashboardService {

    Map<String, Object> overview();

    Map<String, Object> customerRisk();

    Map<String, Object> contractRisk();

    Map<String, Object> workOrderRisk();

    Map<String, Object> dispatchRisk();
}

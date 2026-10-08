package com.zwskchat.zwskchat.workorder.service;

import com.zwskchat.zwskchat.workorder.entity.BizWorkOrder;

import java.util.Map;

public interface WorkOrderAiService {

    BizWorkOrder analyzeAndSuggest(Long workOrderId);

    BizWorkOrder smartAssign(Long workOrderId);

    Map<String, Object> progressReport();
}

package com.zwskchat.zwskchat.workflow.service;

import java.util.Map;

public interface WorkflowAiService {

    Map<String, Object> approvalSuggestion(Long taskId);

    Map<String, Object> bottleneckReport();
}

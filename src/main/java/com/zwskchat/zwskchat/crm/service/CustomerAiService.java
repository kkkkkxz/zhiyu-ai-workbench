package com.zwskchat.zwskchat.crm.service;

import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.entity.BizCustomerScoreLog;

import java.util.List;
import java.util.Map;

public interface CustomerAiService {

    BizCustomer scoreCustomer(Long customerId);

    List<BizCustomer> batchScore();

    List<BizCustomerScoreLog> scoreHistory(Long customerId);

    Map<String, Object> lifecycleSummary();
}

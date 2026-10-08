package com.zwskchat.zwskchat.ai.hub;

public enum BizActionType {
    DISPATCH,        // 派发任务
    CREATE_CUSTOMER, // 创建客户
    CREATE_CONTRACT, // 创建合同
    CREATE_WORKORDER,// 创建工单
    CREATE_SCHEDULE, // 创建日程
    START_APPROVAL,  // 发起审批
    QUERY_CUSTOMER,  // 查询客户
    QUERY_CONTRACT,  // 查询合同
    QUERY_WORKORDER, // 查询工单
    QUERY_SCHEDULE,  // 查询日程
    ADD_FOLLOW_UP,   // 添加跟进记录
    UNKNOWN
}

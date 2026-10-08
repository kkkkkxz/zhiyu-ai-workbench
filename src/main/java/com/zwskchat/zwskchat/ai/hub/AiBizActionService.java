package com.zwskchat.zwskchat.ai.hub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.ai.AiService;
import com.zwskchat.zwskchat.ai.ModelTier;
import com.zwskchat.zwskchat.ai.ModelTierContext;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.contract.entity.BizContract;
import com.zwskchat.zwskchat.contract.service.ContractService;
import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.entity.BizFollowUp;
import com.zwskchat.zwskchat.crm.service.CustomerService;
import com.zwskchat.zwskchat.dispatch.service.AiDispatchService;
import com.zwskchat.zwskchat.dispatch.service.AiDispatchService.DispatchPreview;
import com.zwskchat.zwskchat.notification.service.NotificationService;
import com.zwskchat.zwskchat.schedule.entity.BizSchedule;
import com.zwskchat.zwskchat.schedule.entity.BizTaskReminder;
import com.zwskchat.zwskchat.schedule.mapper.BizScheduleMapper;
import com.zwskchat.zwskchat.schedule.mapper.BizTaskReminderMapper;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrder;
import com.zwskchat.zwskchat.workorder.service.WorkOrderService;
import com.zwskchat.zwskchat.workflow.service.WorkflowService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AI 全业务动作服务：解析自然语言 → 分类动作 → 执行/确认
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBizActionService {

    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final AiDispatchService aiDispatchService;
    private final CustomerService customerService;
    private final ContractService contractService;
    private final WorkOrderService workOrderService;
    private final WorkflowService workflowService;
    private final BizScheduleMapper scheduleMapper;
    private final BizTaskReminderMapper reminderMapper;
    private final NotificationService notificationService;

    private static final String ACTION_CLASSIFY_PROMPT = """
            你是业务意图分类器。根据用户输入，判断属于以下哪种操作，只返回类别名称：
            - DISPATCH: 派发任务给某人
            - CREATE_CUSTOMER: 创建/新增客户
            - CREATE_CONTRACT: 创建/新增合同
            - CREATE_WORKORDER: 创建/新增/报修/提交工单
            - CREATE_SCHEDULE: 创建日程/安排会议/添加提醒
            - START_APPROVAL: 发起审批/提交审批
            - QUERY_CUSTOMER: 查询/搜索客户信息
            - QUERY_CONTRACT: 查询合同信息/合同到期/合同统计
            - QUERY_WORKORDER: 查询工单/我的工单/工单状态
            - QUERY_SCHEDULE: 查询日程/今天有什么安排
            - ADD_FOLLOW_UP: 添加跟进记录/记录拜访
            - UNKNOWN: 无法判断
            
            用户输入: %s
            类别:""";

    private static final String EXTRACT_PROMPT = """
            从用户输入中提取业务信息，返回JSON（缺失的字段填null）：
            %s
            
            用户输入: %s
            JSON:""";

    /**
     * 第一步：解析并预览
     */
    public BizActionResult parseAndPreview(String userInput) {
        BizActionType actionType = classifyAction(userInput);
        log.info("[AiBizAction] input='{}' -> type={}", userInput.substring(0, Math.min(50, userInput.length())), actionType);

        return switch (actionType) {
            case DISPATCH -> handleDispatchPreview(userInput);
            case CREATE_CUSTOMER -> handleCreateCustomerPreview(userInput);
            case CREATE_CONTRACT -> handleCreateContractPreview(userInput);
            case CREATE_WORKORDER -> handleCreateWorkOrderPreview(userInput);
            case CREATE_SCHEDULE -> handleCreateSchedulePreview(userInput);
            case START_APPROVAL -> handleStartApprovalPreview(userInput);
            case QUERY_CUSTOMER -> handleQueryCustomer(userInput);
            case QUERY_CONTRACT -> handleQueryContract(userInput);
            case QUERY_WORKORDER -> handleQueryWorkOrder(userInput);
            case QUERY_SCHEDULE -> handleQuerySchedule(userInput);
            case ADD_FOLLOW_UP -> handleAddFollowUpPreview(userInput);
            default -> BizActionResult.error("我没有理解您的意图，请具体描述一下要做什么操作。");
        };
    }

    /**
     * 第二步：确认执行
     */
    @Transactional
    public BizActionResult confirmExecute(BizActionType actionType, Map<String, Object> data) {
        LoginUser user = SecurityUtils.currentUser();
        return switch (actionType) {
            case DISPATCH -> executeDispatch(data);
            case CREATE_CUSTOMER -> executeCreateCustomer(data, user);
            case CREATE_CONTRACT -> executeCreateContract(data, user);
            case CREATE_WORKORDER -> executeCreateWorkOrder(data, user);
            case CREATE_SCHEDULE -> executeCreateSchedule(data, user);
            case START_APPROVAL -> executeStartApproval(data, user);
            case ADD_FOLLOW_UP -> executeAddFollowUp(data, user);
            default -> BizActionResult.error("不支持的操作类型");
        };
    }

    // ===== 分类 =====
    private BizActionType classifyAction(String userInput) {
        // 业务动作分类是轻量任务，走 LIGHT 档模型。
        try (ModelTierContext.Scope ignored = ModelTierContext.with(ModelTier.LIGHT)) {
            String prompt = String.format(ACTION_CLASSIFY_PROMPT, userInput);
            String result = aiService.processTextWithAi(prompt, null).trim().toUpperCase();
            for (BizActionType type : BizActionType.values()) {
                if (result.contains(type.name())) return type;
            }
            return BizActionType.UNKNOWN;
        }
    }

    // ===== 派发 =====
    private BizActionResult handleDispatchPreview(String input) {
        try {
            DispatchPreview preview = aiDispatchService.parseAndPreview(input);
            Map<String, Object> data = objectMapper.convertValue(preview, new TypeReference<>() {});
            return BizActionResult.confirm(BizActionType.DISPATCH, preview.getConfirmMessage(), data);
        } catch (BusinessException e) {
            return BizActionResult.error(e.getMessage());
        }
    }

    private BizActionResult executeDispatch(Map<String, Object> data) {
        DispatchPreview preview = objectMapper.convertValue(data, DispatchPreview.class);
        aiDispatchService.confirmAndDispatch(preview);
        return BizActionResult.success(BizActionType.DISPATCH,
                "✅ 已成功派发任务给 " + preview.getTargetUserName() + "，对方已收到通知和日程提醒。");
    }

    // ===== 创建客户 =====
    private BizActionResult handleCreateCustomerPreview(String input) {
        String schema = """
                {"name":"客户名称","shortName":"简称","type":"类型(enterprise/personal)","industry":"行业","phone":"电话","email":"邮箱","address":"地址","remark":"备注"}""";
        Map<String, Object> parsed = extractJson(schema, input);
        if (parsed.get("name") == null) return BizActionResult.error("请说明客户名称");

        String msg = String.format("即将创建客户：\n🏢 名称：%s\n📞 电话：%s\n📧 邮箱：%s\n🏭 行业：%s\n\n确认创建？",
                parsed.get("name"), nvl(parsed.get("phone")), nvl(parsed.get("email")), nvl(parsed.get("industry")));
        return BizActionResult.confirm(BizActionType.CREATE_CUSTOMER, msg, parsed);
    }

    private BizActionResult executeCreateCustomer(Map<String, Object> data, LoginUser user) {
        BizCustomer c = new BizCustomer();
        c.setName((String) data.get("name"));
        c.setShortName((String) data.get("shortName"));
        c.setType((String) data.getOrDefault("type", "enterprise"));
        c.setIndustry((String) data.get("industry"));
        c.setPhone((String) data.get("phone"));
        c.setEmail((String) data.get("email"));
        c.setAddress((String) data.get("address"));
        c.setRemark((String) data.get("remark"));
        c.setStatus("active");
        BizCustomer created = customerService.create(c);
        return BizActionResult.success(BizActionType.CREATE_CUSTOMER,
                "✅ 客户「" + created.getName() + "」创建成功！");
    }

    // ===== 创建合同 =====
    private BizActionResult handleCreateContractPreview(String input) {
        String schema = """
                {"title":"合同标题","contractNo":"合同编号","type":"类型(sales/purchase/service)","amount":"金额(数字)","startDate":"开始日期yyyy-MM-dd","endDate":"结束日期yyyy-MM-dd","remark":"备注"}""";
        Map<String, Object> parsed = extractJson(schema, input);
        if (parsed.get("title") == null) return BizActionResult.error("请说明合同标题");

        String msg = String.format("即将创建合同：\n📄 标题：%s\n🔢 编号：%s\n💰 金额：%s\n📅 期限：%s ~ %s\n\n确认创建？",
                parsed.get("title"), nvl(parsed.get("contractNo")), nvl(parsed.get("amount")),
                nvl(parsed.get("startDate")), nvl(parsed.get("endDate")));
        return BizActionResult.confirm(BizActionType.CREATE_CONTRACT, msg, parsed);
    }

    private BizActionResult executeCreateContract(Map<String, Object> data, LoginUser user) {
        BizContract c = new BizContract();
        c.setTitle((String) data.get("title"));
        c.setContractNo((String) data.getOrDefault("contractNo", "CT-" + System.currentTimeMillis()));
        c.setType((String) data.getOrDefault("type", "sales"));
        if (data.get("amount") != null) {
            c.setAmount(new java.math.BigDecimal(data.get("amount").toString()));
        }
        c.setStatus("draft");
        BizContract created = contractService.create(c);
        return BizActionResult.success(BizActionType.CREATE_CONTRACT,
                "✅ 合同「" + created.getTitle() + "」创建成功（状态：草稿）！");
    }

    // ===== 创建工单 =====
    private BizActionResult handleCreateWorkOrderPreview(String input) {
        String schema = """
                {"title":"工单标题","type":"类型(support/bug/feature/maintenance)","priority":"优先级(urgent/high/medium/low)","description":"问题描述"}""";
        Map<String, Object> parsed = extractJson(schema, input);
        if (parsed.get("title") == null) return BizActionResult.error("请说明工单标题或问题描述");

        String msg = String.format("即将创建工单：\n📋 标题：%s\n🔴 优先级：%s\n📝 描述：%s\n\n确认创建？",
                parsed.get("title"), nvl(parsed.getOrDefault("priority", "medium")), nvl(parsed.get("description")));
        return BizActionResult.confirm(BizActionType.CREATE_WORKORDER, msg, parsed);
    }

    private BizActionResult executeCreateWorkOrder(Map<String, Object> data, LoginUser user) {
        BizWorkOrder o = new BizWorkOrder();
        o.setTitle((String) data.get("title"));
        o.setType((String) data.getOrDefault("type", "support"));
        o.setPriority((String) data.getOrDefault("priority", "medium"));
        o.setDescription((String) data.get("description"));
        BizWorkOrder created = workOrderService.create(o);
        return BizActionResult.success(BizActionType.CREATE_WORKORDER,
                "✅ 工单「" + created.getTitle() + "」创建成功，编号：" + created.getOrderNo());
    }

    // ===== 创建日程 =====
    private BizActionResult handleCreateSchedulePreview(String input) {
        String schema = """
                {"title":"日程标题","type":"类型(event/meeting/reminder/follow_up)","startTime":"开始时间yyyy-MM-dd HH:mm","endTime":"结束时间yyyy-MM-dd HH:mm","location":"地点","description":"备注"}""";
        Map<String, Object> parsed = extractJson(schema, input);
        if (parsed.get("title") == null) return BizActionResult.error("请说明日程标题");

        String msg = String.format("即将创建日程：\n📅 标题：%s\n⏰ 时间：%s ~ %s\n📍 地点：%s\n\n确认创建？",
                parsed.get("title"), nvl(parsed.get("startTime")), nvl(parsed.get("endTime")), nvl(parsed.get("location")));
        return BizActionResult.confirm(BizActionType.CREATE_SCHEDULE, msg, parsed);
    }

    private BizActionResult executeCreateSchedule(Map<String, Object> data, LoginUser user) {
        BizSchedule s = new BizSchedule();
        s.setTenantId(user.getTenantId());
        s.setUserId(user.getUserId());
        s.setTitle((String) data.get("title"));
        s.setType((String) data.getOrDefault("type", "event"));
        s.setDescription((String) data.get("description"));
        s.setLocation((String) data.get("location"));
        LocalDateTime startTime = parseDateTime(data.get("startTime"));
        if (startTime == null) startTime = LocalDateTime.now();
        s.setStartTime(startTime);
        LocalDateTime endTime = parseDateTime(data.get("endTime"));
        if (endTime != null) s.setEndTime(endTime);
        s.setAllDay(0);
        s.setStatus("active");
        scheduleMapper.insert(s);

        // 同时创建提醒
        BizTaskReminder reminder = new BizTaskReminder();
        reminder.setTenantId(user.getTenantId());
        reminder.setUserId(user.getUserId());
        reminder.setTitle(s.getTitle());
        reminder.setRemindTime(s.getStartTime());
        reminder.setRemindType("before_15min");
        reminder.setChannel("web");
        reminder.setBizType("schedule");
        reminder.setBizId(s.getId());
        reminder.setStatus("pending");
        reminderMapper.insert(reminder);

        return BizActionResult.success(BizActionType.CREATE_SCHEDULE,
                "✅ 日程「" + s.getTitle() + "」已创建，并设置了提醒。");
    }

    // ===== 发起审批 =====
    private BizActionResult handleStartApprovalPreview(String input) {
        String schema = """
                {"title":"审批标题","bizType":"业务类型(contract/expense/leave/other)","reason":"审批原因/说明"}""";
        Map<String, Object> parsed = extractJson(schema, input);
        if (parsed.get("title") == null) return BizActionResult.error("请说明审批内容");

        String msg = String.format("即将发起审批：\n📋 标题：%s\n📂 类型：%s\n💬 说明：%s\n\n确认提交？",
                parsed.get("title"), nvl(parsed.get("bizType")), nvl(parsed.get("reason")));
        return BizActionResult.confirm(BizActionType.START_APPROVAL, msg, parsed);
    }

    private BizActionResult executeStartApproval(Map<String, Object> data, LoginUser user) {
        // 简单实现：用第一个流程定义
        var defs = workflowService.listDefinitions();
        if (defs.isEmpty()) {
            return BizActionResult.error("当前没有可用的审批流程定义，请联系管理员配置。");
        }
        var inst = workflowService.startInstance(
                defs.get(0).getId(),
                (String) data.getOrDefault("bizType", "other"),
                0L,
                (String) data.get("title"),
                (String) data.get("reason"));
        return BizActionResult.success(BizActionType.START_APPROVAL,
                "✅ 审批「" + data.get("title") + "」已提交，当前节点：" + inst.getCurrentNode());
    }

    // ===== 添加跟进记录 =====
    private BizActionResult handleAddFollowUpPreview(String input) {
        String schema = """
                {"customerName":"客户名称","type":"跟进方式(visit/call/wechat/email)","content":"跟进内容","nextPlan":"下一步计划","nextTime":"下次跟进时间yyyy-MM-dd"}""";
        Map<String, Object> parsed = extractJson(schema, input);
        if (parsed.get("customerName") == null) return BizActionResult.error("请说明跟进哪个客户");

        String msg = String.format("即将添加跟进记录：\n🏢 客户：%s\n📞 方式：%s\n📝 内容：%s\n\n确认添加？",
                parsed.get("customerName"), nvl(parsed.get("type")), nvl(parsed.get("content")));
        return BizActionResult.confirm(BizActionType.ADD_FOLLOW_UP, msg, parsed);
    }

    private BizActionResult executeAddFollowUp(Map<String, Object> data, LoginUser user) {
        String customerName = (String) data.get("customerName");
        // 模糊搜索客户
        IPage<BizCustomer> page = customerService.listCustomers(new Page<>(1, 1), null, customerName);
        if (page.getRecords().isEmpty()) {
            return BizActionResult.error("找不到客户「" + customerName + "」");
        }
        BizCustomer customer = page.getRecords().get(0);
        BizFollowUp f = new BizFollowUp();
        f.setCustomerId(customer.getId());
        f.setType((String) data.getOrDefault("type", "call"));
        f.setContent((String) data.get("content"));
        f.setNextPlan((String) data.get("nextPlan"));
        customerService.createFollowUp(f);
        return BizActionResult.success(BizActionType.ADD_FOLLOW_UP,
                "✅ 已为客户「" + customer.getName() + "」添加跟进记录。");
    }

    // ===== 查询类 =====
    private BizActionResult handleQueryCustomer(String input) {
        String schema = """
                {"keyword":"搜索关键词","status":"状态(active/potential/inactive)"}""";
        Map<String, Object> parsed = extractJson(schema, input);
        String keyword = (String) parsed.get("keyword");
        String status = (String) parsed.get("status");
        IPage<BizCustomer> page = customerService.listCustomers(new Page<>(1, 10), status, keyword);

        List<String> cols = List.of("名称", "行业", "电话", "状态");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizCustomer c : page.getRecords()) {
            rows.add(Map.of("名称", nvl(c.getName()), "行业", nvl(c.getIndustry()), "电话", nvl(c.getPhone()), "状态", nvl(c.getStatus())));
        }
        String summary = String.format("共找到 %d 个客户", page.getTotal());
        return BizActionResult.query(BizActionType.QUERY_CUSTOMER, summary, cols, rows);
    }

    private BizActionResult handleQueryContract(String input) {
        String schema = """
                {"keyword":"搜索关键词","status":"状态(draft/active/completed/terminated)"}""";
        Map<String, Object> parsed = extractJson(schema, input);
        IPage<BizContract> page = contractService.list(new Page<>(1, 10), (String) parsed.get("status"), (String) parsed.get("keyword"));

        List<String> cols = List.of("标题", "编号", "金额", "状态", "到期");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizContract c : page.getRecords()) {
            rows.add(Map.of("标题", nvl(c.getTitle()), "编号", nvl(c.getContractNo()),
                    "金额", c.getAmount() != null ? c.getAmount().toString() : "-",
                    "状态", nvl(c.getStatus()), "到期", c.getEndDate() != null ? c.getEndDate().toString() : "-"));
        }
        return BizActionResult.query(BizActionType.QUERY_CONTRACT,
                String.format("共找到 %d 份合同", page.getTotal()), cols, rows);
    }

    private BizActionResult handleQueryWorkOrder(String input) {
        String schema = """
                {"keyword":"搜索关键词","status":"状态(open/assigned/in_progress/resolved/closed)","priority":"优先级"}""";
        Map<String, Object> parsed = extractJson(schema, input);
        IPage<BizWorkOrder> page = workOrderService.list(new Page<>(1, 10),
                (String) parsed.get("status"), (String) parsed.get("priority"), (String) parsed.get("keyword"));

        List<String> cols = List.of("编号", "标题", "优先级", "状态", "创建时间");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizWorkOrder o : page.getRecords()) {
            rows.add(Map.of("编号", nvl(o.getOrderNo()), "标题", nvl(o.getTitle()),
                    "优先级", nvl(o.getPriority()), "状态", nvl(o.getStatus()),
                    "创建时间", o.getCreateTime() != null ? o.getCreateTime().toString().substring(0, 16) : "-"));
        }
        return BizActionResult.query(BizActionType.QUERY_WORKORDER,
                String.format("共找到 %d 个工单", page.getTotal()), cols, rows);
    }

    private BizActionResult handleQuerySchedule(String input) {
        LoginUser user = SecurityUtils.currentUser();
        // 查最近7天日程
        LocalDateTime now = LocalDateTime.now();
        List<BizSchedule> list = new ArrayList<>();
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizSchedule>()
                .eq(BizSchedule::getUserId, user.getUserId())
                .ge(BizSchedule::getStartTime, now.minusDays(1))
                .le(BizSchedule::getStartTime, now.plusDays(7))
                .orderByAsc(BizSchedule::getStartTime);
        list = scheduleMapper.selectList(qw);

        List<String> cols = List.of("标题", "类型", "开始时间", "地点");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizSchedule s : list) {
            rows.add(Map.of("标题", nvl(s.getTitle()), "类型", nvl(s.getType()),
                    "开始时间", s.getStartTime() != null ? s.getStartTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "-",
                    "地点", nvl(s.getLocation())));
        }
        String summary = list.isEmpty() ? "未来 7 天暂无日程安排" : String.format("未来 7 天有 %d 个日程", list.size());
        return BizActionResult.query(BizActionType.QUERY_SCHEDULE, summary, cols, rows);
    }

    // ===== 工具方法 =====
    private Map<String, Object> extractJson(String schema, String input) {
        String prompt = String.format(EXTRACT_PROMPT, schema, input);
        // 结构化字段抽取是轻量任务，走 LIGHT 档模型。
        String json;
        try (ModelTierContext.Scope ignored = ModelTierContext.with(ModelTier.LIGHT)) {
            json = aiService.processTextWithAi(prompt, null).trim();
        }
        if (json.startsWith("```")) json = json.replaceAll("```(?:json)?", "").trim();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[AiBizAction] json parse failed: {}", json);
            return Map.of();
        }
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime time) return time;
        String s = value.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "-".equals(s)) return null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(s);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private String nvl(Object o) {
        return o == null ? "-" : o.toString();
    }
}

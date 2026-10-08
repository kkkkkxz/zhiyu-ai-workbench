package com.zwskchat.zwskchat.template.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.mapper.BizCustomerMapper;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.template.entity.TplInstallRecord;
import com.zwskchat.zwskchat.template.entity.TplPackage;
import com.zwskchat.zwskchat.template.mapper.TplInstallRecordMapper;
import com.zwskchat.zwskchat.template.mapper.TplPackageMapper;
import com.zwskchat.zwskchat.template.service.TemplateService;
import com.zwskchat.zwskchat.tenant.service.TenantFeatureService;
import com.zwskchat.zwskchat.timeline.service.TimelineService;
import com.zwskchat.zwskchat.workflow.entity.WfDefinition;
import com.zwskchat.zwskchat.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private static final String INSTALLED = "installed";
    private static final String DEFAULT_VERSION = "1.0.0";

    private final TplPackageMapper packageMapper;
    private final TplInstallRecordMapper installRecordMapper;
    private final WorkflowService workflowService;
    private final BizCustomerMapper customerMapper;
    private final TimelineService timelineService;
    private final TenantFeatureService tenantFeatureService;

    @Override
    public List<TplPackage> listPackages() {
        return packageMapper.selectList(Wrappers.<TplPackage>lambdaQuery()
                .eq(TplPackage::getStatus, 1)
                .orderByAsc(TplPackage::getSort)
                .orderByAsc(TplPackage::getId));
    }

    @Override
    public List<TplInstallRecord> listInstalled() {
        LoginUser user = SecurityUtils.currentUser();
        return installRecordMapper.selectList(Wrappers.<TplInstallRecord>lambdaQuery()
                .eq(TplInstallRecord::getTenantId, user.getTenantId())
                .orderByDesc(TplInstallRecord::getInstalledAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TplInstallRecord install(String packageCode) {
        if (packageCode == null || packageCode.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "模板编码不能为空");
        }
        LoginUser user = SecurityUtils.currentUser();
        TplPackage templatePackage = packageMapper.selectOne(Wrappers.<TplPackage>lambdaQuery()
                .eq(TplPackage::getCode, packageCode)
                .eq(TplPackage::getStatus, 1)
                .last("LIMIT 1"));
        if (templatePackage == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "模板不存在或已停用");
        }
        TplInstallRecord exists = installRecordMapper.selectOne(Wrappers.<TplInstallRecord>lambdaQuery()
                .eq(TplInstallRecord::getTenantId, user.getTenantId())
                .eq(TplInstallRecord::getPackageCode, packageCode)
                .last("LIMIT 1"));
        if (exists != null) {
            exists.setStatus(INSTALLED);
            exists.setInstalledBy(user.getUserId());
            exists.setInstalledAt(LocalDateTime.now());
            installRecordMapper.updateById(exists);
            tenantFeatureService.enableTemplateFeatures(user.getTenantId(), packageCode);
            initializeTemplateData(templatePackage, user);
            return installRecordMapper.selectById(exists.getId());
        }

        TplInstallRecord record = new TplInstallRecord();
        record.setTenantId(user.getTenantId());
        record.setPackageCode(templatePackage.getCode());
        record.setPackageName(templatePackage.getName());
        record.setVersion(DEFAULT_VERSION);
        record.setStatus(INSTALLED);
        record.setConfigJson("{}");
        record.setInstalledBy(user.getUserId());
        record.setInstalledAt(LocalDateTime.now());
        installRecordMapper.insert(record);

        // ===== 实际初始化：根据模板内容为租户生成数据 =====
        tenantFeatureService.enableTemplateFeatures(user.getTenantId(), packageCode);
        initializeTemplateData(templatePackage, user);

        return record;
    }

    private void initializeTemplateData(TplPackage templatePackage, LoginUser user) {
        String code = templatePackage.getCode();
        log.info("[Template] initializing '{}' for tenant={}", code, user.getTenantId());

        // 1. 创建默认审批流程定义
        initWorkflowDefinition(code, user);

        // 2. 创建示范客户数据
        initSampleCustomers(code, user);

        // 3. 记录安装时间线
        timelineService.record("template", templatePackage.getId(),
                "created", "模板「" + templatePackage.getName() + "」已安装",
                "模板包含模块: " + templatePackage.getModulesJson());
    }

    private void initWorkflowDefinition(String packageCode, LoginUser user) {
        String nodesJson;
        String defName;
        String defCode;

        switch (packageCode) {
            case "general-business" -> {
                defName = "通用审批流程";
                defCode = "general_approval";
                nodesJson = """
                    [{"code":"submit","name":"提交申请","type":"start","nextNode":"dept_leader"},
                     {"code":"dept_leader","name":"部门负责人审批","type":"approve","assigneeType":"dept_leader","nextNode":"end"},
                     {"code":"end","name":"结束","type":"end"}]""";
            }
            case "project-delivery" -> {
                defName = "项目验收审批";
                defCode = "project_acceptance";
                nodesJson = """
                    [{"code":"submit","name":"提交验收","type":"start","nextNode":"pm_review"},
                     {"code":"pm_review","name":"项目经理审核","type":"approve","assigneeType":"dept_leader","nextNode":"client_confirm"},
                     {"code":"client_confirm","name":"客户确认","type":"approve","assigneeType":"initiator","nextNode":"end"},
                     {"code":"end","name":"结束","type":"end"}]""";
            }
            case "after-sales-service" -> {
                defName = "工单升级审批";
                defCode = "ticket_escalation";
                nodesJson = """
                    [{"code":"submit","name":"提交升级","type":"start","nextNode":"service_manager"},
                     {"code":"service_manager","name":"服务主管审批","type":"approve","assigneeType":"dept_leader","nextNode":"end"},
                     {"code":"end","name":"结束","type":"end"}]""";
            }
            case "xingtian-pm" -> {
                defName = "兴天工程造价三审流";
                defCode = "xingtian_cost_audit";
                nodesJson = """
                    [{"code":"submit","name":"提交一审成果","type":"start","nextNode":"second_audit"},
                     {"code":"second_audit","name":"造价师二审复核","type":"approve","assigneeType":"dept_leader","nextNode":"third_audit"},
                     {"code":"third_audit","name":"总师三审签发","type":"approve","assigneeType":"dept_leader","nextNode":"end"},
                     {"code":"end","name":"结束","type":"end"}]""";
            }
            default -> {
                defName = "默认审批流程";
                defCode = "default_approval";
                nodesJson = """
                    [{"code":"submit","name":"提交","type":"start","nextNode":"approver"},
                     {"code":"approver","name":"审批","type":"approve","assigneeType":"dept_leader","nextNode":"end"},
                     {"code":"end","name":"结束","type":"end"}]""";
            }
        }

        List<WfDefinition> existing = workflowService.listDefinitions();
        boolean exists = existing.stream().anyMatch(def -> defCode.equals(def.getCode()));
        if (exists) {
            log.info("[Template] tenant={} already has workflow definition '{}', skip", user.getTenantId(), defCode);
            return;
        }

        WfDefinition def = new WfDefinition();
        def.setTenantId(user.getTenantId());
        def.setCode(defCode);
        def.setName(defName);
        def.setNodesJson(nodesJson);
        def.setVersion(1);
        def.setStatus(1);
        workflowService.createDefinition(def);
        log.info("[Template] created workflow definition '{}' for tenant={}", defCode, user.getTenantId());
    }

    private void initSampleCustomers(String packageCode, LoginUser user) {
        String[][] samples;
        switch (packageCode) {
            case "general-business" -> samples = new String[][]{
                    {"示范客户-科技有限公司", "enterprise", "互联网", "active"},
                    {"示范客户-贸易集团", "enterprise", "贸易", "potential"},
                    {"示范客户-张先生", "individual", "零售", "active"},
            };
            case "project-delivery" -> samples = new String[][]{
                    {"示范客户-软件开发公司", "enterprise", "IT", "active"},
                    {"示范客户-工程集团", "enterprise", "建筑", "active"},
            };
            case "after-sales-service" -> samples = new String[][]{
                    {"示范客户-设备维护公司", "enterprise", "制造", "active"},
                    {"示范客户-物业服务中心", "enterprise", "物业", "active"},
            };
            case "xingtian-pm" -> samples = new String[][]{
                    {"示范建设方-四川路桥建设集团", "enterprise", "建筑施工", "active"},
                    {"示范建设方-成都建工集团有限公司", "enterprise", "房屋建筑", "active"},
                    {"示范业主-成都交投集团", "enterprise", "市政交通", "active"},
            };
            default -> samples = new String[][]{
                    {"示范客户", "enterprise", "通用", "active"},
            };
        }

        int created = 0;
        for (String[] s : samples) {
            Long exists = customerMapper.selectCount(Wrappers.<BizCustomer>lambdaQuery()
                    .eq(BizCustomer::getTenantId, user.getTenantId())
                    .eq(BizCustomer::getName, s[0]));
            if (exists > 0) {
                continue;
            }
            BizCustomer c = new BizCustomer();
            c.setTenantId(user.getTenantId());
            c.setName(s[0]);
            c.setType(s[1]);
            c.setIndustry(s[2]);
            c.setStatus(s[3]);
            c.setOwnerId(user.getUserId());
            c.setCreateBy(user.getUserId());
            customerMapper.insert(c);
            created++;
        }
        log.info("[Template] created {} sample customers for tenant={}", created, user.getTenantId());
    }
}

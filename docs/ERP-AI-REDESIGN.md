# 展望数科 · 知语 AI — ERP AI 化系统重设计方案

> 版本: v1.1 | 日期: 2025-05-19 | 审核状态: 已审核通过

---

## 一、系统定位与目标

**从"企业知识问答助手"升级为"企业 ERP AI 中枢"**

| 维度 | 现状（v1.x） | 目标（v2.0） |
|------|-------------|-------------|
| AI 能力 | 文档 RAG + 语音问答 | RAG + Text-to-SQL + AI Agent（读写业务数据） |
| 数据源 | PDF/Word 文档 | 文档 + 结构化业务数据 + 外部 API |
| 业务覆盖 | 知识库管理 | 客户、合同、工单、审批、派发、日程、BI 分析 |
| 集成能力 | 无 | SSO + 企微/钉钉 + 邮件 + Webhook |
| 用户角色 | 知识管理员 / 员工 | 管理层 / 业务员 / 客服 / 财务 / 运维 |

---

## 二、整体架构

```
┌─────────────────────────────────────────────────────────┐
│                     前端层 (React)                        │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐  │
│  │AI对话│ │工作台│ │客户  │ │合同  │ │工单  │ │BI看板│  │
│  │知识库│ │审批流│ │供应商│ │项目  │ │日程  │ │设置  │  │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘  │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────┐
│                   API 网关层                              │
│  认证(JWT/SSO) │ 限流 │ 路由 │ API Key 鉴权 │ 审计日志   │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────┐
│                  业务服务层 (Spring Boot)                  │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │              AI 中枢 (AI Hub)                        │ │
│  │  意图识别 → 路由分发:                                │ │
│  │    ├─ 知识问答 → RAG Pipeline (已有)                 │ │
│  │    ├─ 数据查询 → Text-to-SQL Engine (新增)          │ │
│  │    ├─ 业务操作 → AI Agent + Function Calling (新增) │ │
│  │    ├─ 图表生成 → Chart Generator (新增)             │ │
│  │    └─ 闲聊/通用 → 大模型直答 (已有)                 │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ 客户管理 │ │ 合同管理 │ │ 工单管理 │ │ 审批流引擎 │  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ 工作派发 │ │ 日程管理 │ │ 任务提醒 │ │ 文档导出   │  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ 知识库   │ │ 消息通道 │ │ 数据导入 │ │ BI 报表    │  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘  │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────┐
│                    基础设施层                              │
│  MySQL │ Redis │ RabbitMQ │ Qdrant │ MinIO │ DashScope   │
└─────────────────────────────────────────────────────────┘
```

---

## 三、模块设计

### 3.1 AI 中枢（AI Hub）— 核心新增

现有 `RagService` 只做文档检索。新设计引入**意图路由器**，让 AI 自动判断用户意图并分发到不同处理管线：

```
用户输入
  │
  ▼
┌─────────────┐
│ IntentRouter │  ← 用 LLM 做意图分类（一次轻量调用）
└──────┬──────┘
       │
  ┌────┴────┬──────────┬──────────┬──────────┐
  ▼         ▼          ▼          ▼          ▼
 RAG    Text-to-SQL  BizAction  ChartGen  General
(已有)   (查数据)    (写数据)   (生成图表) (闲聊)
```

语音入口补充：

- 语音实时识别阶段，前端可继续展示 ASR 中间结果。
- 语音一轮结束后，后端先对 ASR 最终文本做语义优化，再进入意图识别、RAG、Text-to-SQL 或业务动作管线。
- 后端通过 `ASR_FINAL` 同时保留原始 ASR 文本和优化后的语义文本。
- 前端最终展示以后端返回的优化语义文本为准，避免操作者看到的内容与实际传给 AI 的请求不一致。

#### 后端新增类

| 类 | 职责 |
|----|------|
| `IntentRouter` | 调用 LLM 判断意图类型，返回 enum: `RAG / DATA_QUERY / BIZ_ACTION / CHART / GENERAL` |
| `TextToSqlEngine` | 加载表元数据 → 拼 prompt → LLM 生成 SQL → 沙盒执行 → 格式化结果 |
| `BizActionAgent` | Function Calling：定义工具集（创建客户、提交工单、发起审批等），LLM 选择并调用 |
| `ChartGenerator` | 根据用户描述生成 ECharts 配置 JSON，前端渲染 |
| `SqlSandbox` | 只读数据源 + 超时 + 行数限制 + SQL 白名单（仅 SELECT） |

#### 数据库

```sql
-- AI 可查询的业务数据源注册
CREATE TABLE `ai_data_source` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `name`          VARCHAR(100) NOT NULL COMMENT '数据源名称（如：销售系统、ERP主库）',
    `type`          VARCHAR(20)  NOT NULL DEFAULT 'mysql' COMMENT 'mysql / api / csv',
    `connection_url` VARCHAR(500) COMMENT '数据库连接（加密存储）',
    `username`      VARCHAR(100),
    `password_enc`  VARCHAR(500) COMMENT 'AES 加密后的密码',
    `schema_json`   JSON         COMMENT '表结构元数据缓存（表名、列名、列注释、示例值）',
    `status`        TINYINT      NOT NULL DEFAULT 1,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    INDEX `idx_tenant` (`tenant_id`)
) COMMENT='AI 业务数据源';

-- Text-to-SQL 执行日志（审计 + 优化）
CREATE TABLE `ai_sql_execution_log` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL,
    `session_id`    BIGINT,
    `question`      TEXT         NOT NULL COMMENT '用户原始问题',
    `generated_sql` TEXT         COMMENT 'AI 生成的 SQL',
    `result_rows`   INT          COMMENT '返回行数',
    `execution_ms`  INT          COMMENT '执行耗时',
    `status`        VARCHAR(20)  NOT NULL COMMENT 'SUCCESS / SQL_ERROR / TIMEOUT / REJECTED',
    `error_msg`     TEXT,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_tenant_time` (`tenant_id`, `create_time`)
) COMMENT='SQL 执行审计日志';
```

---

### 3.2 客户管理（CRM 基础）

```sql
CREATE TABLE `biz_customer` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `name`          VARCHAR(200) NOT NULL COMMENT '客户名称',
    `short_name`    VARCHAR(100) COMMENT '简称',
    `type`          VARCHAR(20)  NOT NULL DEFAULT 'enterprise' COMMENT 'enterprise/individual',
    `industry`      VARCHAR(50)  COMMENT '行业',
    `scale`         VARCHAR(20)  COMMENT '规模：small/medium/large',
    `source`        VARCHAR(50)  COMMENT '来源渠道',
    `owner_id`      BIGINT       COMMENT '负责业务员',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'potential' COMMENT 'potential/active/inactive/lost',
    `phone`         VARCHAR(30),
    `email`         VARCHAR(100),
    `address`       VARCHAR(300),
    `remark`        TEXT,
    `create_by`     BIGINT,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    INDEX `idx_tenant_status` (`tenant_id`, `status`),
    INDEX `idx_owner` (`owner_id`)
) COMMENT='客户档案';

CREATE TABLE `biz_contact` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `customer_id`   BIGINT       NOT NULL,
    `name`          VARCHAR(50)  NOT NULL COMMENT '联系人姓名',
    `position`      VARCHAR(50)  COMMENT '职位',
    `phone`         VARCHAR(30),
    `email`         VARCHAR(100),
    `is_primary`    TINYINT      NOT NULL DEFAULT 0 COMMENT '主要联系人',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    INDEX `idx_customer` (`customer_id`)
) COMMENT='客户联系人';

CREATE TABLE `biz_follow_up` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `customer_id`   BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL COMMENT '跟进人',
    `type`          VARCHAR(20)  NOT NULL COMMENT 'call/visit/email/wechat/meeting',
    `content`       TEXT         NOT NULL COMMENT '跟进内容',
    `ai_summary`    TEXT         COMMENT 'AI 自动生成的摘要',
    `next_plan`     VARCHAR(500) COMMENT '下次计划',
    `next_time`     DATETIME     COMMENT '下次跟进时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_customer_time` (`customer_id`, `create_time`)
) COMMENT='客户跟进记录';
```

**AI 增强点：**
- 跟进记录保存时，AI 自动生成 `ai_summary`
- 用户问"张三最近跟进了哪些客户"，走 Text-to-SQL
- 客户超过 N 天未跟进，AI 推送提醒

---

### 3.3 合同管理

```sql
CREATE TABLE `biz_contract` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `contract_no`   VARCHAR(50)  NOT NULL COMMENT '合同编号',
    `title`         VARCHAR(200) NOT NULL,
    `customer_id`   BIGINT       COMMENT '关联客户',
    `type`          VARCHAR(30)  NOT NULL COMMENT 'sales/purchase/service/other',
    `amount`        DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '合同金额',
    `currency`      VARCHAR(10)  NOT NULL DEFAULT 'CNY',
    `sign_date`     DATE,
    `start_date`    DATE,
    `end_date`      DATE,
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'draft' COMMENT 'draft/pending/active/completed/terminated',
    `owner_id`      BIGINT       COMMENT '负责人',
    `file_key`      VARCHAR(300) COMMENT 'MinIO 文件 key',
    `ai_risk_score` TINYINT      COMMENT 'AI 风险评分 0-100',
    `ai_risk_note`  TEXT         COMMENT 'AI 风险提示',
    `remark`        TEXT,
    `create_by`     BIGINT,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    UNIQUE INDEX `uk_contract_no` (`tenant_id`, `contract_no`),
    INDEX `idx_customer` (`customer_id`),
    INDEX `idx_status` (`tenant_id`, `status`)
) COMMENT='合同';

CREATE TABLE `biz_contract_milestone` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `contract_id`   BIGINT       NOT NULL,
    `name`          VARCHAR(100) NOT NULL COMMENT '里程碑名称',
    `amount`        DECIMAL(15,2) COMMENT '关联金额',
    `due_date`      DATE         COMMENT '到期日',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/completed/overdue',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_contract` (`contract_id`)
) COMMENT='合同里程碑/回款节点';
```

**AI 增强点：**
- 合同 PDF 上传后，AI 自动提取关键条款（甲乙方、金额、期限、违约条款）填入字段
- AI 风险评分：对比知识库中的法律模板，标记不利条款
- "本月有哪些合同即将到期" → Text-to-SQL

---

### 3.4 工单管理

```sql
CREATE TABLE `biz_work_order` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `order_no`      VARCHAR(30)  NOT NULL COMMENT '工单号（自动生成）',
    `title`         VARCHAR(200) NOT NULL,
    `type`          VARCHAR(30)  NOT NULL COMMENT 'bug/feature/support/complaint/maintenance',
    `priority`      VARCHAR(10)  NOT NULL DEFAULT 'medium' COMMENT 'low/medium/high/urgent',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'open' COMMENT 'open/assigned/in_progress/resolved/closed',
    `customer_id`   BIGINT       COMMENT '关联客户',
    `reporter_id`   BIGINT       NOT NULL COMMENT '提交人',
    `assignee_id`   BIGINT       COMMENT '指派人',
    `dept_id`       BIGINT       COMMENT '所属部门',
    `description`   TEXT,
    `ai_category`   VARCHAR(50)  COMMENT 'AI 自动分类',
    `ai_suggestion` TEXT         COMMENT 'AI 处理建议',
    `sla_deadline`  DATETIME     COMMENT 'SLA 截止时间',
    `resolved_at`   DATETIME,
    `closed_at`     DATETIME,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    UNIQUE INDEX `uk_order_no` (`tenant_id`, `order_no`),
    INDEX `idx_assignee_status` (`assignee_id`, `status`),
    INDEX `idx_customer` (`customer_id`)
) COMMENT='工单';

CREATE TABLE `biz_work_order_comment` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `order_id`      BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL,
    `content`       TEXT         NOT NULL,
    `is_internal`   TINYINT      NOT NULL DEFAULT 0 COMMENT '内部备注（客户不可见）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_order` (`order_id`)
) COMMENT='工单评论';
```

**AI 增强点：**
- 新工单提交时 AI 自动分类 + 推荐指派人（基于历史工单）
- AI 从知识库检索相似工单的解决方案作为 `ai_suggestion`
- "我负责的工单里有几个超期的" → Text-to-SQL

---

### 3.5 通用审批流引擎

```sql
-- 流程定义（管理员配置）
CREATE TABLE `wf_definition` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `code`          VARCHAR(50)  NOT NULL COMMENT '流程编码：contract_approve / leave / expense',
    `name`          VARCHAR(100) NOT NULL,
    `nodes_json`    JSON         NOT NULL COMMENT '流程节点定义（见下方 JSON 结构）',
    `version`       INT          NOT NULL DEFAULT 1,
    `status`        TINYINT      NOT NULL DEFAULT 1,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_code_ver` (`tenant_id`, `code`, `version`)
) COMMENT='审批流程定义';

-- 流程实例（每次发起审批创建一个）
CREATE TABLE `wf_instance` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `definition_id` BIGINT       NOT NULL,
    `biz_type`      VARCHAR(50)  NOT NULL COMMENT '业务类型',
    `biz_id`        BIGINT       NOT NULL COMMENT '关联业务记录 ID',
    `title`         VARCHAR(200) NOT NULL,
    `initiator_id`  BIGINT       NOT NULL COMMENT '发起人',
    `current_node`  VARCHAR(50)  COMMENT '当前节点编码',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'running' COMMENT 'running/approved/rejected/canceled',
    `form_data`     JSON         COMMENT '表单快照',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finish_time`   DATETIME,
    INDEX `idx_initiator` (`initiator_id`),
    INDEX `idx_biz` (`biz_type`, `biz_id`)
) COMMENT='审批流程实例';

-- 审批任务（每个审批节点生成一条待办）
CREATE TABLE `wf_task` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `instance_id`   BIGINT       NOT NULL,
    `node_code`     VARCHAR(50)  NOT NULL COMMENT '节点编码',
    `node_name`     VARCHAR(100),
    `assignee_id`   BIGINT       NOT NULL COMMENT '审批人',
    `action`        VARCHAR(20)  COMMENT 'approve/reject/delegate/rollback',
    `comment`       TEXT         COMMENT '审批意见',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/completed',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finish_time`   DATETIME,
    INDEX `idx_assignee_status` (`assignee_id`, `status`),
    INDEX `idx_instance` (`instance_id`)
) COMMENT='审批任务（待办）';
```

**`nodes_json` 结构示例：**
```json
[
  {
    "code": "dept_lead",
    "name": "部门主管审批",
    "type": "approve",
    "assignee_strategy": "dept_leader",
    "conditions": null
  },
  {
    "code": "finance",
    "name": "财务审核",
    "type": "approve",
    "assignee_strategy": "role:FINANCE",
    "conditions": { "field": "amount", "op": ">=", "value": 10000 }
  },
  {
    "code": "gm",
    "name": "总经理审批",
    "type": "approve",
    "assignee_strategy": "role:GM",
    "conditions": { "field": "amount", "op": ">=", "value": 50000 }
  }
]
```

**AI 增强点：**
- 金额 < 阈值时 AI 自动预审通过
- AI 催办：超过 24h 未处理的待办，推送提醒
- "我有几个待审批的" → 走 BizAction Agent 查 wf_task

---

### 3.6 消息通知中心

```sql
CREATE TABLE `sys_notification` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL COMMENT '接收人',
    `type`          VARCHAR(30)  NOT NULL COMMENT 'approval/mention/system/reminder/alert',
    `title`         VARCHAR(200) NOT NULL,
    `content`       TEXT,
    `biz_type`      VARCHAR(50)  COMMENT '关联业务类型',
    `biz_id`        BIGINT       COMMENT '关联业务 ID',
    `is_read`       TINYINT      NOT NULL DEFAULT 0,
    `channel`       VARCHAR(20)  NOT NULL DEFAULT 'web' COMMENT 'web/email/wechat/dingtalk/sms',
    `send_status`   VARCHAR(20)  NOT NULL DEFAULT 'sent',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_read` (`user_id`, `is_read`),
    INDEX `idx_tenant_time` (`tenant_id`, `create_time`)
) COMMENT='通知消息';

CREATE TABLE `sys_notify_channel_config` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `channel`       VARCHAR(20)  NOT NULL COMMENT 'email/wechat_bot/dingtalk_bot/sms',
    `config_json`   JSON         NOT NULL COMMENT '通道配置（webhook_url / smtp / api_key 等，加密存储）',
    `enabled`       TINYINT      NOT NULL DEFAULT 1,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_tenant_channel` (`tenant_id`, `channel`)
) COMMENT='通知通道配置';
```

---

### 3.7 SSO / 第三方登录

```sql
CREATE TABLE `sys_oauth_provider` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `provider`      VARCHAR(30)  NOT NULL COMMENT 'wechat_work/dingtalk/feishu/ldap/saml',
    `client_id`     VARCHAR(200),
    `client_secret_enc` VARCHAR(500) COMMENT 'AES 加密',
    `config_json`   JSON         COMMENT '额外配置（LDAP base_dn, SAML metadata_url 等）',
    `enabled`       TINYINT      NOT NULL DEFAULT 1,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_tenant_provider` (`tenant_id`, `provider`)
) COMMENT='第三方登录配置';

-- 用户与第三方账号绑定
CREATE TABLE `sys_user_oauth` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL,
    `provider`      VARCHAR(30)  NOT NULL,
    `open_id`       VARCHAR(200) NOT NULL COMMENT '第三方唯一 ID',
    `union_id`      VARCHAR(200),
    `nickname`      VARCHAR(100),
    `avatar_url`    VARCHAR(500),
    `bind_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_provider_openid` (`provider`, `open_id`),
    INDEX `idx_user` (`user_id`)
) COMMENT='用户第三方账号绑定';
```

---

### 3.8 API 开放平台

```sql
CREATE TABLE `open_api_key` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `name`          VARCHAR(100) NOT NULL COMMENT 'Key 用途说明',
    `key_prefix`    VARCHAR(10)  NOT NULL COMMENT 'Key 前缀（明文，用于日志展示）',
    `key_hash`      VARCHAR(128) NOT NULL COMMENT 'SHA-256 哈希',
    `scope`         JSON         NOT NULL COMMENT '权限范围：["chat","kb:read","sql:read"]',
    `rate_limit`    INT          NOT NULL DEFAULT 60 COMMENT '每分钟最大请求数',
    `expires_at`    DATETIME,
    `last_used_at`  DATETIME,
    `status`        TINYINT      NOT NULL DEFAULT 1,
    `create_by`     BIGINT,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    UNIQUE INDEX `uk_key_hash` (`key_hash`)
) COMMENT='开放 API Key';

CREATE TABLE `open_api_usage_log` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `api_key_id`    BIGINT       NOT NULL,
    `tenant_id`     BIGINT       NOT NULL,
    `endpoint`      VARCHAR(100) NOT NULL,
    `method`        VARCHAR(10),
    `status_code`   INT,
    `latency_ms`    INT,
    `token_used`    INT          COMMENT 'AI token 消耗',
    `ip`            VARCHAR(45),
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_key_time` (`api_key_id`, `create_time`)
) COMMENT='API 调用日志';
```

---

### 3.9 工作派发

```sql
CREATE TABLE `biz_dispatch` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `title`         VARCHAR(200) NOT NULL COMMENT '派发标题',
    `description`   TEXT         COMMENT '任务描述',
    `type`          VARCHAR(30)  NOT NULL DEFAULT 'task' COMMENT 'task/project/inspection/delivery',
    `priority`      VARCHAR(10)  NOT NULL DEFAULT 'medium' COMMENT 'low/medium/high/urgent',
    `dispatcher_id` BIGINT       NOT NULL COMMENT '派发人',
    `dept_id`       BIGINT       COMMENT '所属部门',
    `due_date`      DATETIME     COMMENT '截止时间',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/in_progress/completed/rejected',
    `completion_note` TEXT       COMMENT '完成说明',
    `completed_at`  DATETIME,
    `biz_type`      VARCHAR(50)  COMMENT '关联业务类型（contract/work_order/customer）',
    `biz_id`        BIGINT       COMMENT '关联业务 ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    INDEX `idx_tenant_status` (`tenant_id`, `status`),
    INDEX `idx_dispatcher` (`dispatcher_id`)
) COMMENT='工作派发';

CREATE TABLE `biz_dispatch_assignee` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `dispatch_id`   BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL COMMENT '被指派人',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/completed/rejected',
    `progress`      TINYINT      NOT NULL DEFAULT 0 COMMENT '进度 0-100',
    `note`          TEXT         COMMENT '执行备注',
    `completed_at`  DATETIME,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_dispatch` (`dispatch_id`),
    INDEX `idx_user_status` (`user_id`, `status`)
) COMMENT='派发指派人（支持一派多人）';
```

**AI 增强点：**
- "把这个工单派给张三" → AI Agent 自动创建派发记录
- 超期未完成自动推送提醒
- 智能推荐指派人（基于历史完成率和当前工作量）

---

### 3.10 日程管理 + 任务提醒

```sql
CREATE TABLE `biz_schedule` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL COMMENT '所属用户',
    `title`         VARCHAR(200) NOT NULL,
    `description`   TEXT,
    `type`          VARCHAR(30)  NOT NULL DEFAULT 'event' COMMENT 'event/meeting/reminder/follow_up',
    `start_time`    DATETIME     NOT NULL,
    `end_time`      DATETIME,
    `all_day`       TINYINT      NOT NULL DEFAULT 0 COMMENT '全天事件',
    `location`      VARCHAR(200),
    `biz_type`      VARCHAR(50)  COMMENT '关联业务（customer/contract/work_order/dispatch）',
    `biz_id`        BIGINT,
    `recurrence`    VARCHAR(20)  COMMENT '重复规则：none/daily/weekly/monthly',
    `color`         VARCHAR(10)  COMMENT '日历颜色标记',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active/completed/canceled',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    INDEX `idx_user_time` (`user_id`, `start_time`),
    INDEX `idx_tenant_time` (`tenant_id`, `start_time`)
) COMMENT='日程';

CREATE TABLE `biz_schedule_participant` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `schedule_id`   BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL,
    `response`      VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/declined',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_schedule` (`schedule_id`),
    INDEX `idx_user` (`user_id`)
) COMMENT='日程参与人';

CREATE TABLE `biz_task_reminder` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL COMMENT '提醒接收人',
    `title`         VARCHAR(200) NOT NULL,
    `content`       TEXT,
    `remind_time`   DATETIME     NOT NULL COMMENT '提醒触发时间',
    `remind_type`   VARCHAR(20)  NOT NULL DEFAULT 'once' COMMENT 'once/daily/weekly',
    `channel`       VARCHAR(30)  NOT NULL DEFAULT 'web' COMMENT 'web/email/wechat/dingtalk',
    `biz_type`      VARCHAR(50)  COMMENT '关联业务',
    `biz_id`        BIGINT,
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/sent/dismissed',
    `sent_at`       DATETIME,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_time` (`user_id`, `remind_time`),
    INDEX `idx_status_time` (`status`, `remind_time`)
) COMMENT='任务提醒';
```

**AI 增强点：**
- "明天下午两点提醒我跟进华为" → AI 自动创建日程 + 提醒
- "我今天有什么安排" → Text-to-SQL 查日程
- 定时任务扫描 `biz_task_reminder`，到时间触发通知
- 工作派发/工单/合同到期自动生成提醒记录

---

### 3.11 文档导出（Word / PDF）

| 导出场景 | 模板 | 格式 |
|----------|------|------|
| 合同详情 | 合同信息 + 里程碑 + AI 风险评估 | Word + PDF |
| 客户报告 | 客户档案 + 跟进记录汇总 | Word + PDF |
| 工单报告 | 工单详情 + 处理过程 + 评论 | PDF |
| 会话导出 | AI 对话记录完整导出 | Word + PDF |
| BI 报表 | 看板数据 + 图表截图 | PDF |
| 工作派发单 | 派发详情 + 执行进度 | Word |

**技术方案：**
- **Word 生成**：Apache POI（后端已有 PDFBox，POI 同系列，零学习成本）
- **PDF 生成**：iText / Flying Saucer（HTML → PDF，复用前端模板样式）
- **接口设计**：`GET /api/v1/export/{bizType}/{bizId}?format=docx|pdf`
- **异步导出**：大报表走 RabbitMQ 异步生成 → MinIO 存储 → 通知用户下载

---

## 四、前端页面结构

```
/                           → 工作台（待办 + 通知 + 快捷入口）
/chat                       → AI 对话（已有，增强：支持查数据、操作业务、展示语音优化语义文本）
/voice-ai                   → 语音助手（已有，增强：后端语义优化后回显）
/kb                         → 知识库管理（已有）
│
├── /crm
│   ├── /customers          → 客户列表（表格 + 筛选 + 搜索）
│   ├── /customers/:id      → 客户详情（联系人 + 跟进记录 + 关联合同）
│   └── /follow-ups         → 我的跟进（日历视图 + 列表视图）
│
├── /contracts
│   ├── /list               → 合同列表
│   └── /:id                → 合同详情（AI 风险评分 + 里程碑）
│
├── /work-orders
│   ├── /list               → 工单列表（看板视图 / 表格视图）
│   └── /:id                → 工单详情
│
├── /dispatch
│   ├── /sent               → 我派发的
│   ├── /received            → 我收到的
│   └── /:id                → 派发详情（进度跟踪）
│
├── /schedule
│   ├── /calendar           → 日历视图（月/周/日）
│   ├── /list               → 日程列表
│   └── /reminders          → 我的提醒
│
├── /workflow
│   ├── /my-tasks           → 我的待办
│   ├── /my-initiated       → 我发起的
│   └── /definitions        → 流程配置（管理员）
│
├── /bi
│   ├── /dashboard          → BI 看板（可配置卡片）
│   └── /reports            → 报表（AI 自然语言生成图表）
│
├── /platform               → 平台管理（已有，扩展）
│   ├── /dashboard          → 运营看板（已有）
│   ├── /tenants            → 租户管理（已有）
│   ├── /departments        → 部门管理（已有）
│   ├── /notifications      → 通知管理
│   ├── /audit-logs         → 操作日志
│   ├── /oauth              → SSO 配置
│   └── /open-api           → API Key 管理
│
└── /settings               → 个人设置（已有）
```

---

## 五、AI 对话增强示例

升级后的 AI 对话能力矩阵：

| 用户问 | 意图 | 处理方式 |
|--------|------|----------|
| "RAG 检索是怎么实现的" | RAG | 知识库文档检索（已有） |
| "本月销售额多少" | DATA_QUERY | Text-to-SQL → `SELECT SUM(amount) FROM biz_contract WHERE ...` |
| "帮我建一个客户叫华为" | BIZ_ACTION | Function Calling → `createCustomer({name:"华为"})` |
| "画一个近半年月度合同金额趋势" | CHART | SQL 查数据 → 生成 ECharts JSON → 前端渲染 |
| "我有几个待审批" | BIZ_ACTION | Function Calling → `queryMyTasks()` |
| "把这个工单派给张三" | BIZ_ACTION | Function Calling → `createDispatch({...})` |
| "明天下午两点提醒我跟进华为" | BIZ_ACTION | Function Calling → `createSchedule({...}) + createReminder({...})` |
| "我今天有什么安排" | DATA_QUERY | Text-to-SQL → `SELECT * FROM biz_schedule WHERE user_id=? AND DATE(start_time)=CURDATE()` |
| "导出华为的客户报告" | BIZ_ACTION | Function Calling → `exportDocument({type:'customer', id:..., format:'pdf'})` |
| "今天天气怎么样" | GENERAL | 大模型直答 |

---

## 六、实施计划

### Phase 1（第 1-2 周）：AI 中枢 + 数据查询
- [ ] `IntentRouter` 意图识别
- [ ] `TextToSqlEngine` + `SqlSandbox`
- [ ] `ai_data_source` + `ai_sql_execution_log` 表
- [ ] 语音 ASR 后端语义优化：优化文本回传前端展示，原始文本保留审计
- [ ] 前端 AI 对话增强：支持展示表格结果
- [ ] 前端 AI 对话增强：消息反馈👍👎 + 引用来源展示

### Phase 2（第 3-4 周）：CRM + 合同
- [ ] 客户 CRUD（后端 + 前端）
- [ ] 联系人 + 跟进记录
- [ ] 合同 CRUD + AI 条款提取
- [ ] AI Agent Function Calling（创建客户、查合同）

### Phase 3（第 5-6 周）：审批流 + 工单 + 派发
- [ ] 审批流引擎（定义 → 发起 → 审批 → 完成）
- [ ] 工单 CRUD + AI 自动分类
- [ ] 工作派发（派发 + 指派 + 进度跟踪）
- [ ] 工作台页面（待办 + 通知 + 派发）
- [ ] 通知中心（站内 + 企微/钉钉 webhook）

### Phase 4（第 7-8 周）：日程 + 导出 + 集成
- [ ] 日程管理（日历视图 + CRUD）
- [ ] 任务提醒引擎（定时扫描 + 多通道推送）
- [ ] 文档导出（Word + PDF：合同/客户/工单/会话）
- [ ] SSO 对接（企业微信 / 钉钉）
- [ ] API 开放平台（Key 管理 + OpenAI 兼容接口）
- [ ] BI 看板 + AI 图表生成

---

## 七、技术选型补充

| 组件 | 选型 | 理由 |
|------|------|------|
| 图表库 | ECharts（前端） | 国产，文档全，AI 生成 option JSON 即可 |
| Excel 处理 | EasyExcel（后端） | 阿里出品，低内存，支持大文件 |
| Word 导出 | Apache POI（后端） | 与已有 PDFBox 同生态，成熟稳定 |
| PDF 导出 | iText + Flying Saucer | HTML→PDF，样式复用，支持中文 |
| 日历组件 | FullCalendar（@fullcalendar/react） | 功能全，月/周/日视图，拖拽 |
| SSO | Spring Security OAuth2 Client | 已有 Spring Security 基础，扩展成本低 |
| 数据脱敏 | 自研注解 `@Sensitive` + Jackson Serializer | 轻量，不引入新依赖 |
| 前端表单 | React Hook Form + Zod | 已用 Zod，表单校验一致性好 |
| 看板拖拽 | @dnd-kit/sortable | 工单看板 Kanban 视图 |

---

## 八、安全设计要点

1. **Text-to-SQL 沙盒**：独立只读数据库连接，`SET max_execution_time=5000`，结果限 1000 行，禁止 DDL/DML
2. **数据源密码**：AES-256-GCM 加密存储，密钥从环境变量注入，不落 yaml
3. **API Key**：只存 SHA-256 哈希，创建时返回一次明文，之后不可再查看
4. **Function Calling 白名单**：AI Agent 能调用的函数在代码中硬编码注册，不允许动态反射执行
5. **多租户隔离**：所有新表继承 `tenant_id` 字段 + MyBatis-Plus TenantLineInnerInterceptor
6. **语音审计一致性**：同时记录原始 ASR 文本、后端优化语义文本和最终执行管线，便于回溯操作者看到的内容与 AI 实际请求

---

> **v1.1 已审核通过，全量实施中。**

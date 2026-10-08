# 知语 AI 工作台

> ZhiYu AI Workbench

面向企业知识管理与业务协同的 AI 工作台，提供知识库 RAG 问答、实时语音对话、多租户权限管理，以及客户、合同、工单、日程和工作流等能力。

建议 GitHub 仓库名：`zhiyu-ai-workbench`

**当前阶段**：M0 基础底座 + M1 文本 RAG + M1.1 RabbitMQ 异步文档索引

## 技术栈

| 类别 | 选型 |
|------|------|
| 框架 | Spring Boot 3.5 / Java 17 |
| 安全 | Spring Security + JWT (HS256) + Redis Token Store |
| ORM | MyBatis-Plus 3.5（多租户 / 逻辑删除 / 审计字段自动填充） |
| 缓存 | Redis + Lettuce 连接池 |
| 消息队列 | RabbitMQ（文档解析 / 向量化异步任务，含 DLX + 重试） |
| 对象存储 | MinIO |
| 向量数据库 | Qdrant |
| AI 模型 | 阿里云百炼 DashScope（qwen-plus / text-embedding-v3 / paraformer-realtime-v2 / cosyvoice-v2） |
| 前端 | Vite + React + TypeScript + Tailwind CSS |
| API 文档 | Knife4j + OpenAPI 3 |

## 功能

- **认证授权**：JWT 登录/刷新/注销，BCrypt 密码加密，登录失败锁定
- **RBAC + 多租户**：角色权限控制，租户级数据隔离（SQL 拦截器），逻辑删除
- **审计日志**：AOP 关键操作记录，敏感字段脱敏
- **知识库 RAG**：文档上传 → 解析（Tika）→ 切片 → 向量化 → Qdrant 检索 → 流式问答
- **实时语音**：WebSocket 语音识别（ASR）→ AI 回答 → 语音合成（TTS）
- **接口限流**：Redis Lua 滑动窗口，按 API 分级限流
- **内容安全**：关键词 + LLM 双重审核防线
- **AI 业务动作**：客户/合同/工单/调度/日程 CRUD + AI 解析
- **工作流引擎**：自定义流程 + 审批

## 模块结构

```
com.zwskchat.zwskchat
├── ai/hub           AI 会话中心（会话/消息/流式 SSE）
├── ai/provider      AI 供应商抽象（DashScope / OpenAI 兼容）
├── ai/usage         AI 用量统计
├── attachment       附件管理
├── audit            审计日志（AOP 异步入库）
├── auth             认证（JWT 登录/刷新/注销）
├── chat             聊天模块
├── kb               知识库管理（文档/切片/向量化）
├── rag              RAG 检索增强（融合/重排/回答治理）
├── safety           内容安全审核（关键词 + 模型双防线）
├── schedule         日程管理
├── security         Spring Security / JWT / TokenStore
├── tenant           多租户上下文
├── user             用户/角色/权限
├── voice            语音对话（ASR/TTS/WebSocket）
├── workflow          工作流引擎
└── workorder         工单管理
```

## 快速开始

### 本地开发

```bash
# 前端（端口 3001）
cd web
npm install
npm run dev

# 后端（端口 8081，需 MySQL/Redis/RabbitMQ/MinIO/Qdrant）
./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

详细本机开发文档参见 `scripts/win/README.md` 和 `deploy/README.md`。

### 生产部署

Docker Compose 一键部署，包含 Nginx + Spring Boot + MySQL + Redis + RabbitMQ + MinIO + Qdrant 全栈。

```bash
cd deploy
./deploy.sh         # 首次运行生成 .env → 填 DASHSCOPE_API_KEY → 再运行即上线
```

详细部署教程：[deploy/HONGKONG-ECS-GUIDE.md](deploy/HONGKONG-ECS-GUIDE.md)

## 安全特性

| 要求 | 实现 |
|------|------|
| 身份认证 / Token 黑名单 / 强制下线 | `JwtTokenProvider` + `TokenStore` |
| BCrypt 加密 / 登录失败锁定 | `SecurityConfig` + `AuthServiceImpl` |
| RBAC + 数据权限 | `SysRoleService` + `@EnableMethodSecurity` |
| 接口限流 | `RateLimitAspect`（Redis Lua） |
| 多租户数据隔离 | `TenantContext` + MyBatis-Plus 拦截器 |
| 审计日志 | `@AuditLog` AOP 切面 |
| 日志脱敏 | 手机号/身份证/邮箱模式匹配替换 |
| SQL 注入防护 | MyBatis-Plus 参数绑定 |
| 安全头 | HSTS / X-Content-Type-Options / X-Frame-Options / Permissions-Policy |
| 内容安全 | 关键词 + LLM 模型双重审核 |

## 合规

- [x] AI 生成内容标识（"AI 生成内容，仅供参考"）
- [x] 用户协议 + 隐私政策
- [x] 内容安全双重防线（keyword + model）
- [x] 联系邮箱 `privacy@noweyai.com`
- 详细风险预案：[docs/RISK-ASSESSMENT.md](docs/RISK-ASSESSMENT.md)

## 许可证

本项目代码仅供学习、演示和经授权的二次开发使用。涉及企业数据、模型服务和第三方组件时，请遵守相应的法律法规、服务条款和许可证要求。

## 安全说明

- 仓库不提供可用于生产环境的默认密码或 API Key。
- `.env`、外部 secret 文件、证书、数据库备份和本地运行数据不会提交到 Git。
- 生产环境请使用强随机 JWT 密钥，并通过环境变量、Docker Secret、Kubernetes Secret 或独立密钥管理服务注入凭据。
- 如果密钥曾经出现在聊天记录、日志、截图或 Git 历史中，应立即撤销并重新生成。
- 上传到知识库的文件可能包含企业内部信息，请根据实际部署场景配置访问权限、备份和数据保留策略。

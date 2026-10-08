# 企业级集成接入指南：OIDC SSO / OpenTelemetry / 模型微调

> 对应《企业第二大脑智能问答助手工程计划书》中“部署运维（OAuth2/OIDC、可观测）”
> 与“模型、微调与规则融合”章节。
>
> ✅ **已落地（代码 + profile 配置）**：OIDC 登录链路（`oidc` profile，`OidcSecurityConfig`）、
> OpenTelemetry 追踪导出（`otel` profile）、多模型分层路由（`zwsk.ai.chat.tier`）、
> 模型级内容安全（`zwsk.content-safety.mode`）。这些能力默认关闭，启用只需激活对应 profile /
> 配置项 + 注入外部环境参数（IdP / Collector），不改动现有 JWT 登录链路与默认行为。
>
> ⚠️ 启用前请在测试环境按步骤验证并做集成测试后再上生产。

---

## 1. OAuth2 / OIDC 单点登录（SSO）

现状：系统默认用自管 JWT（`security.JwtTokenProvider` + Redis 黑名单），并已内置一条
**OIDC 登录链路**（`sso.OidcSecurityConfig`，`@Profile("oidc")`），与自管 JWT 并存、不替换。
启用方式：`--spring.profiles.active=prod,oidc` + `application-oidc.yaml`（见仓库 `src/main/resources/`）。
登录成功后由 `AuthService.oidcLogin` 把 IdP 身份映射到本系统用户并签发本系统 JWT。

### 1.1 依赖（pom.xml）

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<!-- 若同时把后端做成资源服务器，校验 IdP 下发的 JWT： -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

### 1.2 配置（application-oidc.yaml，按 profile 启用）

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          enterprise:
            client-id: ${OIDC_CLIENT_ID}
            client-secret: ${OIDC_CLIENT_SECRET}
            scope: openid,profile,email
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/enterprise"
        provider:
          enterprise:
            issuer-uri: ${OIDC_ISSUER_URI}   # 如 https://keycloak.example.com/realms/zwsk
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URI}
```

### 1.3 SecurityFilterChain（新增独立链，不动现有 JWT 链）

用 `@Order` 让 OIDC 链只匹配 `/sso/**` 与 `/login/oauth2/**`，其余请求仍走现有 JWT 链：

```java
@Bean
@Order(1)
SecurityFilterChain oidcChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/sso/**", "/login/oauth2/**", "/oauth2/**")
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2Login(o -> o.defaultSuccessUrl("/sso/exchange", true));
    return http.build();
}
```

登录成功后在 `/sso/exchange` 里：从 `OidcUser` 取 email/sub → 映射或自动开户本系统用户 →
签发本系统 JWT（复用 `JwtTokenProvider`）→ 重定向前端带 token。这样 OIDC 只负责“认证”，
鉴权与会话仍由现有体系统一管理。

### 1.4 验证方式（需环境）
1. 起一个 Keycloak（`docker run quay.io/keycloak/keycloak start-dev`），建 realm + client；
2. 设置 `OIDC_*` 环境变量，用 `--spring.profiles.active=dev,oidc` 启动；
3. 浏览器访问 `/oauth2/authorization/enterprise` → 跳转 IdP 登录 → 回调 → 拿到本系统 JWT；
4. 集成测试：`@SpringBootTest` + WireMock 模拟 issuer 的 discovery/jwks。

---

## 2. OpenTelemetry 可观测（零代码 javaagent 方式，推荐）

现状：已有 Micrometer + Prometheus 指标（`/actuator/prometheus`）。补齐**分布式链路追踪**
最稳的方式是 OTel Java Agent —— 不改一行业务代码，启动加 `-javaagent` 即可自动埋点
（HTTP、JDBC、Redis、RabbitMQ、HTTP client 全自动）。

### 2.1 部署（Dockerfile / k8s 启动参数）

```dockerfile
# 下载 agent（构建阶段）
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /app/otel-agent.jar
```

启动命令追加：

```bash
java -javaagent:/app/otel-agent.jar \
     -Dotel.service.name=zwskchat \
     -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
     -Dotel.traces.exporter=otlp -Dotel.metrics.exporter=otlp \
     -jar app.jar
```

在 k8s 里通过环境变量同样可配：`OTEL_SERVICE_NAME` / `OTEL_EXPORTER_OTLP_ENDPOINT`。

### 2.2 采集后端
- Collector：`otel/opentelemetry-collector`；
- 链路存储：Jaeger / Tempo；指标继续进 Prometheus；
- 关键追踪指标（计划书要求）：p95 延迟、QPS、cache hit、retrieval recall drift、模型错误率。

### 2.3 验证方式（需环境）
起 `otel-collector` + `jaeger-all-in-one`，带 agent 启动应用，发一次 RAG 问答，
在 Jaeger UI 应看到 `POST /chat/.../completions/stream` 的完整 span 树（含 embedding、Qdrant、LLM 调用）。

---

## 3. 模型微调（SFT / DPO / LoRA）

微调是**独立的离线 ML 流水线**，不属于本 Spring Boot 应用的运行时；本系统的职责是
“产出可训练数据”和“切换微调后模型”。按计划书“先评估→提示词→轻微调→强化对齐”的顺序推进。

### 3.1 训练数据从哪来
系统已沉淀可用语料：
- `ai_message`（会话问答对）+ `ai_message_reference`（命中证据）→ 适合 SFT「带证据回答」；
- `ai_model_usage_log`（调用记录）→ 评估与采样；
- 金集（`eval.GoldenCase`）→ 验证集，**不可**混入训练集。

### 3.2 导出格式（SFT，JSONL / ShareGPT 风格）

```json
{"messages":[
  {"role":"system","content":"<企业知识助手 system prompt>"},
  {"role":"user","content":"差旅报销流程是什么"},
  {"role":"assistant","content":"<带证据、带适用边界的标准回答>"}
]}
```

建议先只导出“稳定、规则化”的场景（案例标准卡片、三段式需求归纳、售前纪要 JSON），
人工校对后再训练；偏好/语气问题优先用 DPO（构造 chosen/rejected 对）。

### 3.3 训练命令（开源私有化，二选一）

LLaMA-Factory（LoRA 微调 Qwen）：
```bash
llamafactory-cli train \
  --model_name_or_path Qwen/Qwen3-8B \
  --dataset zwsk_sft --template qwen \
  --finetuning_type lora --lora_rank 16 \
  --output_dir ./out/zwsk-qwen-lora
```

MS-SWIFT（等价路径）：`swift sft --model Qwen/Qwen3-8B --train_type lora --dataset zwsk_sft.jsonl`。

### 3.4 上线与回归
1. 量化导出（如 GGUF / AWQ），用 vLLM / Ollama 起 OpenAI 兼容端点；
2. 在 `zwsk.ai.chat` 把 `provider: openai-compat` 指向该端点，**固定模型 snapshot**；
3. **必须跑金集回归**：用 `RagEvaluator` 对比微调前后 faithfulness / keywordCoverage / 风险拒答率，
   不回退才放量；按计划书“每次模型切换都走回归测试”。

### 3.5 验证方式（需环境）
需要 GPU 训练机 + 推理服务；本仓库侧的可验证部分是“数据导出格式”和“金集回归”
（`RagEvaluatorTest` 已覆盖指标计算逻辑）。

---

## 4. 落地校验清单

| 集成项 | 本仓库已就绪 | 需外部环境验证 |
| --- | --- | --- |
| OIDC SSO | 依赖/配置/链路设计 | IdP（Keycloak/Entra）+ 集成测试 |
| OpenTelemetry | javaagent 启动参数 + 采集设计 | Collector + Jaeger/Tempo |
| 模型微调 | 数据来源/导出格式/回归指标 | GPU 训练机 + 推理服务 |

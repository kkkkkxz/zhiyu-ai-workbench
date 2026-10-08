# 生产上线 Checklist

## 1. 凭据

- 轮换所有曾经暴露过的 DashScope、JWT、MySQL、Redis、RabbitMQ、Qdrant、MinIO 密钥。
- 只把真实值写入服务器 `deploy/.env`、阿里云 Secret/KMS 或 CI/CD Secret。
- 确认 `src/main/resources/application-prod.yaml` 不含真实密钥。
- 首次登录后修改或禁用默认 `admin` 账号。

## 2. 阿里云网络

- 安全组公网入方向只放行 `80/443`。
- `22` 只允许你的固定 IP。
- 禁止公网开放 `3306/6379/5672/15672/6333/9000/9001`。
- 如果使用 RDS/Redis 托管服务，白名单只允许 ECS/ACK 内网 IP。

## 3. HTTPS

- 配置域名解析到 ECS/SLB/CDN/WAF。
- `deploy/.env` 填写 `PUBLIC_DOMAIN`；Nginx `server_name` 必须与真实域名一致。
- 推荐前端和 `/api` 使用同一个域名；如果分域名，必须填写 `CORS_ALLOWED_ORIGINS`。
- 启用 HTTPS 证书。
- HTTP 80 只做 301 跳转到 HTTPS。
- 访问登录、聊天、语音 WebSocket，确认浏览器无 mixed content。

## 4. 数据

- 初始化生产库后导入必要业务数据。
- 不要把 `zwskchat_backup_20260614` 这类临时库带到生产。
- 运行一次 MySQL 备份并恢复到临时库验证。
- 迁移知识库 MySQL 数据后，必须恢复或重建 Qdrant 向量。

## 5. RAG/Qdrant

先 dry-run：

```bash
cd deploy
DRY_RUN=true ./reindex-qdrant.sh
```

正式重建：

```bash
./reindex-qdrant.sh
```

Windows：

```powershell
cd deploy
.\reindex-qdrant.ps1 -DryRun
.\reindex-qdrant.ps1
```

重建后用旧知识库问题测试检索命中。

## 6. 部署前预检

```bash
cd deploy
./preflight.sh
```

Windows：

```powershell
.\preflight.ps1
```

预检失败不要上线。

## 7. 备份

```bash
./backup-mysql.sh
```

Windows：

```powershell
.\backup-mysql.ps1
```

把备份同步到 OSS 或使用 RDS 自动备份。每周至少做一次恢复演练。

## 8. 验收

- `/api/actuator/health` 返回 UP。
- 登录、刷新 token、退出登录正常。
- 文本聊天 SSE 正常。
- 语音 WebSocket 正常。
- 知识库问答能命中旧知识库。
- 文档上传、解析、索引成功。
- 服务器重启后容器自动恢复。

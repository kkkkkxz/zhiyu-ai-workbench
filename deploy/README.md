# 知语 AI 生产部署

本目录用于把整套系统部署到一台服务器：Nginx + Spring Boot + MySQL + Redis + Qdrant + RabbitMQ + MinIO。适合单机生产、小规模客户交付和演示环境。

如果是正式公网业务，必须先完成下面的生产要求，再对外开放。

## 生产要求

- 只对公网开放 `80/443`，`22` 仅允许你的固定 IP。
- 不要开放 `3306/6379/5672/15672/6333/9000/9001` 到公网。
- 必须启用 HTTPS；HTTP 只做 301 跳转。
- `deploy/.env` 只保存在服务器，不提交到 Git。
- 已经发到聊天窗口、日志、截图或代码文件里的密钥都视为泄露，必须轮换。
- 管理员初始密码由部署环境设置，首次登录后应立即修改或禁用初始账号。
- 如果迁移了 MySQL 知识库数据，还要恢复或重建 Qdrant 向量，否则 RAG 可能看得到文档但搜不到内容。

## 三步部署

```bash
cd deploy

# 1. 首次运行：生成 deploy/.env，脚本会退出并提醒你填 DASHSCOPE_API_KEY
./deploy.sh

# 2. 编辑 .env，填入 DASHSCOPE_API_KEY；确认所有密码都不是默认值
vim .env

# 3. 运行预检并启动
./deploy.sh
```

Windows PowerShell：

```powershell
cd deploy
.\deploy.ps1
notepad .env
.\deploy.ps1
```

部署脚本会自动执行 `preflight`。你也可以单独运行：

```bash
./preflight.sh
```

```powershell
.\preflight.ps1
```

## HTTPS

推荐在阿里云使用以下任一方案：

- ECS 单机 Nginx 终止 HTTPS。
- SLB/ALB 终止 HTTPS，后端 ECS 只收内网流量。
- CDN/WAF + HTTPS，源站安全组只允许回源 IP。

ECS 单机 Nginx 方式：

1. 在 `deploy/.env` 填写：

```bash
PUBLIC_DOMAIN=app.noweyai.com
```

同域部署时 `CORS_ALLOWED_ORIGINS` 可以留空；如果前端和 API 分开部署，必须填前端完整 origin：

```bash
CORS_ALLOWED_ORIGINS=https://www.example.com,https://example.com
```

2. 申请证书，放到：
   - `deploy/certs/fullchain.pem`
   - `deploy/certs/privkey.pem`
3. 当前配置已默认启用 `443:443`、证书挂载和 80 到 HTTPS 跳转；如更换域名，只需同步修改 `.env` 的 `PUBLIC_DOMAIN` 和 `nginx/app.conf` 的 `server_name`。
4. 重启：

```bash
./deploy.sh restart
```

## 常用命令

| 操作 | Linux/macOS | Windows |
|------|-------------|---------|
| 启动 / 更新 | `./deploy.sh` | `.\deploy.ps1` |
| 预检 | `./preflight.sh` | `.\preflight.ps1` |
| 看后端日志 | `./deploy.sh logs` | `.\deploy.ps1 logs` |
| 看容器状态 | `./deploy.sh ps` | `.\deploy.ps1 ps` |
| 重启 | `./deploy.sh restart` | `.\deploy.ps1 restart` |
| 停止（保留数据） | `./deploy.sh down` | `.\deploy.ps1 down` |

## 数据备份

MySQL 备份：

```bash
./backup-mysql.sh
```

Windows：

```powershell
.\backup-mysql.ps1
```

建议把 `deploy/backups/mysql` 同步到阿里云 OSS，或改用阿里云 RDS 自动备份。备份必须定期做恢复演练。

如果继续使用本机 Docker 数据卷，还要备份：

- `mysql-data`
- `redis-data`
- `qdrant-data`
- `rabbitmq-data`
- `minio-data`

## 数据库建议

更稳的生产方案是使用阿里云 RDS MySQL：

- 应用使用独立账号，不使用 root。
- 开启自动备份和日志备份。
- 白名单只允许 ECS/ACK 内网 IP。
- 开启 SSL 或至少确保走内网。
- 上线前做一次从备份恢复到临时库的演练。

## RAG 向量恢复

知识库依赖两类数据：

- MySQL：`knowledge_base`、`knowledge_document`、`knowledge_chunk`
- Qdrant：向量集合

如果只迁移 MySQL，没有迁移 Qdrant，需要重新索引文档 chunk。否则聊天接口可能无法检索旧知识。

## 前端更新

```bash
cd ../web
npm install
npm run build
cd ../deploy
./deploy.sh restart
```

Nginx 直接挂载 `web/dist`。

## 故障排查

- 后端起不来：`./deploy.sh logs`
- 查看健康：`curl http://localhost/api/actuator/health`
- 看容器状态：`./deploy.sh ps`
- 预检失败：先修复 `preflight` 输出的错误，不要绕过。

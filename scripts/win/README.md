# 知语 AI · Windows 原生中间件部署脚本

不依赖 Docker，全部走原生 Windows 进程 / 服务。所有可执行文件、数据、日志、配置都集中在 `D:\conf\` 下，方便迁移备份。

## 安全前置

⚠️ **API Key 等敏感信息不进 Git**。所有秘密都从 `D:\conf\zwsk\zwsk-secret.yaml` 读取，由 `application.yaml` 通过 `spring.config.import` 自动加载。该文件已被 `.gitignore` 排除。

如果你曾在公开场合（聊天、邮件、截图）粘贴过任何真实 Key，**请立即在百炼控制台吊销并重发**，再把新 Key 填到本地 yaml 里。

## 目录约定

```
D:\conf\
├─ mysql\        # 02-install-mysql.ps1 安装的 MySQL 8.4 LTS
│   ├─ bin, data, log, my.ini
├─ qdrant\       # 04-install-qdrant.ps1 安装的 Qdrant 1.13
│   ├─ bin, storage, snapshots, config.yaml, qdrant.log
│
├─ [Program Files] Erlang OTP\          # 06 脚本装 Erlang 27.3.3
│
├─ [Program Files] RabbitMQ Server\     # 06 脚本装 RabbitMQ 4.0.5
│   └─ rabbitmq_server-4.0.5\sbin\   # rabbitmqctl.bat / rabbitmq-plugins.bat
└─ zwsk\
    ├─ logs\
    │   ├─ app.log         # Spring Boot 输出
    │   └─ app.pid         # 当前后端进程 pid
    └─ zwsk-secret.yaml    # 你填的 API Key / 数据库密码（600 权限）
```

## 一次性安装（全部以管理员 PowerShell 运行）

```powershell
cd D:\project\IdeaProjects\zwskchat\scripts\win

# 1. 创建目录树 + 复制秘密配置模板
.\01-prepare-dirs.ps1

# 2. ★ 用你的编辑器打开 D:\conf\zwsk\zwsk-secret.yaml
#    把 REPLACE_WITH_* 替换为真实值（DashScope API Key、MySQL 密码等）

# 3. 安装 MySQL 8.4 LTS（注册 Windows 服务 ZwskMySQL）
.\02-install-mysql.ps1

# 4. 安装 Memurai Developer (Redis 7 兼容 Windows 原生)
.\03-install-redis.ps1

# 5. 下载 Qdrant Windows 二进制 + 注册任务计划自启
.\04-install-qdrant.ps1

# 6. 安装 Erlang OTP 27.3.3 + RabbitMQ 4.0.5（含 zwsk 用户，Windows 服务自启）
#    会从 GitHub Release 官方下载两个 NSIS 安装包静默安装；需要管理员 PowerShell
.\06-install-rabbitmq.ps1

# 7. 重启 RabbitMQ 以启用管理插件 + 创建 zwsk 用户（如果 06 未走到后面）
.\07-config-rabbitmq.ps1

# 8. 创建 zwskchat 数据库 + zwsk 用户 + 导入 schema
.\05-init-mysql-db.ps1

# 9. 注册 Spring Boot 应用本身的登录后自启（任务名 ZwskChatApp）
.\10-register-autostart.ps1
```

> **重要 · Erlang cookie**
>
> RabbitMQ Windows 服务以 SYSTEM 账号启动，使用 `C:\Windows\System32\config\systemprofile\.erlang.cookie`；
> 而 `rabbitmqctl.bat` 以当前用户身份运行，默认读 `%USERPROFILE%\.erlang.cookie`。
> 06 脚本会自动同步；如果你手动多装了 Erlang 或 cookie 被重置，记得补一句（管理员）：
>
> ```powershell
> Copy-Item 'C:\Windows\System32\config\systemprofile\.erlang.cookie' "$env:USERPROFILE\.erlang.cookie" -Force
> ```

## 日常使用

```powershell
# 启动整套（依赖 + Spring Boot）
.\start-zwsk.ps1

# 仅停 Spring Boot
.\stop-zwsk.ps1

# 停所有（连 MySQL/Memurai/Qdrant 都关）
.\stop-zwsk.ps1 -All

# 查看应用日志
Get-Content D:\conf\zwsk\logs\app.log -Wait

# 通过任务计划手动触发自启
Start-ScheduledTask -TaskName ZwskChatApp
```

## 验证

启动完毕后浏览器打开：

| 页面 | 用途 |
| --- | --- |
| http://127.0.0.1:8080/api/doc.html | Knife4j API 文档 |
| http://127.0.0.1:8080/api/voice-demo.html | 语音对话浏览器 Demo |
| http://127.0.0.1:15672 | RabbitMQ 管理 UI（用户名和密码由部署环境设置） |

REST 联调示例参考仓库根 `README.md` 第 7 章。

## 卸载

```powershell
# 停服务
Stop-Service ZwskMySQL, Memurai -ErrorAction SilentlyContinue

# 卸 MySQL 服务（不删数据）
& 'D:\conf\mysql\bin\mysqld.exe' --remove ZwskMySQL

# 卸 Qdrant 任务
Unregister-ScheduledTask -TaskName ZwskQdrant -Confirm:$false
Unregister-ScheduledTask -TaskName ZwskChatApp -Confirm:$false

# 卸 Memurai
winget uninstall Memurai.MemuraiDeveloper

# 删配置 / 数据（确认无误后再执行！）
# Remove-Item -Recurse -Force D:\conf\mysql, D:\conf\qdrant, D:\conf\zwsk
```

## 常见问题

**Q：02-install-mysql.ps1 提示需要管理员？**
A：右键开始菜单 → "Windows Terminal (管理员)" / "PowerShell (管理员)"，再 cd 到本目录运行。

**Q：03-install-redis.ps1 提示 winget 不存在？**
A：从微软商店安装 "App Installer"，重启 PowerShell 后 winget 就会可用；或直接到 https://www.memurai.com/get-memurai 下载 .msi 双击安装。

**Q：Qdrant 启动失败？**
A：查看 `D:\conf\qdrant\qdrant.log`。常见原因是 6333/6334 端口被占用。

**Q：Spring Boot 起不来，报 datasource 连接失败？**
A：检查 `D:\conf\zwsk\zwsk-secret.yaml` 中 MySQL 密码是否与 05 脚本里的一致；或先用 `mysql -uzwsk -p` 手工验证。

**Q：百炼 API 返回 401 / 403？**
A：检查 yaml 里 `zwsk.ai.dashscope.api-key`；若密钥曾外泄，**先吊销重发**再填。

**Q：rabbitmqctl 报 `Erlang distribution failed` / `cookie mismatch`？**
A：Windows 上 SYSTEM 账号跟当前用户的 `.erlang.cookie` 不一致。以管理员运行：
```powershell
Copy-Item 'C:\Windows\System32\config\systemprofile\.erlang.cookie' "$env:USERPROFILE\.erlang.cookie" -Force
```

**Q：上传文档后 `parseStatus` 一直 PENDING，不进 PARSING？**
A：检查：(1) RabbitMQ 服务是否运行 `Get-Service RabbitMQ`；(2) 队列是否有 consumer。验证：
```powershell
$env:ERLANG_HOME = 'C:\Program Files\Erlang OTP'
& 'C:\Program Files\RabbitMQ Server\rabbitmq_server-4.0.5\sbin\rabbitmqctl.bat' list_queues name messages consumers
```
应看到 `zwsk.ingest.queue` / `zwsk.ingest.dlq` 各有 `consumers=1`。若为 0，重启 Spring Boot。

**Q：上传后状态变 FAILED，`parseError=Invalid API-key`？**
A：DashScope Key 被服务端拒绝。到百炼控制台验证 Key 状态、必要时重生一把填进 yaml，重启。

**Q：想临时禁用异步索引走同步路径？**
A：在 `D:\conf\zwsk\zwsk-secret.yaml` 加：
```yaml
zwsk:
  ingest:
    async:
      enabled: false
```
重启后上传会在请求线程同步跑 embedding，适合 RabbitMQ 不可用的环境。

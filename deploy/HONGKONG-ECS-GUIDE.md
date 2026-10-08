# 香港 ECS 部署教程 · 知语 AI

> 目标域名：`app.noweyai.com`
> 部署方式：Docker Compose 全栈单机（Nginx + Spring Boot + MySQL + Redis + RabbitMQ + MinIO + Qdrant）
> 预计耗时：首次约 30-45 分钟

---

## 前置准备（在你本机 Windows 上做）

### 1. 确认前端能编译

```powershell
cd D:\新建文件夹\zwskchat\zwskchat\web
npm.cmd run build
```

看到 `✓ built in xx.xxs` 即通过。`web/dist/` 目录会被挂载到 Nginx 容器里。

### 2. 确认 Key 可用

对话和向量模型已验证通过（qwen-plus / text-embedding-v3，HTTP 200）。

---

## 第一步：买香港 ECS（阿里云控制台）

登录 [ecs.console.aliyun.com](https://ecs.console.aliyun.com) → 创建实例：

| 配置项 | 选择 |
|--------|------|
| 地域 | **香港** |
| 镜像 | Ubuntu 22.04 LTS |
| 规格 | 4 vCPU / 8 GiB（最低） |
| 系统盘 | ESSD 40GB → **改成 100GB** |
| 带宽 | 5 Mbps 固定带宽 |
| 登录凭证 | 创建密钥对（推荐）或密码 |

**安全组**（创建时就要配好）：

| 方向 | 端口 | 来源 | 说明 |
|------|------|------|------|
| 入 | 80 | 0.0.0.0/0 | HTTP |
| 入 | 443 | 0.0.0.0/0 | HTTPS |
| 入 | 22 | **你的宽带公网 IP/32** | SSH |

> 不要开 3306 / 6379 / 5672 / 6333 / 9000 / 9001。

买完记下 **公网 IP**。

---

## 第二步：配 DNS（阿里云 DNS 控制台）

进入 [dns.console.aliyun.com](https://dns.console.aliyun.com) → 选择 `noweyai.com` → 添加记录：

| 记录类型 | 主机记录 | 记录值 | TTL |
|----------|----------|--------|-----|
| A | app | 香港 ECS 公网 IP | 10 分钟 |

生效后 `ping app.noweyai.com` 应该解析到你的 ECS IP。

---

## 第三步：搞 SSL 证书

你有三个选择：

**A（推荐，免费）**：阿里云 SSL 证书控制台申请免费 DV 证书，域名填 `app.noweyai.com`，验证方式选 DNS（自动添加验证记录），几分钟就签发。下载 **Nginx 格式**。

**B（免费，自动化）**：服务器上用 certbot + ACME 自动申请 Let's Encrypt 证书。需要域名已解析到服务器。

**C（花钱）**：买阿里云付费证书，有保险和售后。

> 不管选哪个，最终你需要两个文件：`fullchain.pem`（证书）和 `privkey.pem`（私钥）。

---

## 第四步：SSH 连服务器 + 装 Docker

```bash
ssh root@<ECS公网IP>
```

进去后：

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 装必要工具
sudo apt install -y git curl vim ca-certificates gnupg unzip

# 装 Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER

# 退出重新登录
exit
ssh root@<ECS公网IP>

# 验证
docker version
docker compose version
# 两条命令都有输出即成功
```

---

## 第五步：上传项目

### 方式 A：从本机直接上传（如果没推 Git）

在本机 Windows PowerShell：

```powershell
# 先打包（排除 node_modules / target / logs）
cd D:\新建文件夹\zwskchat
tar --exclude='zwskchat/web/node_modules' --exclude='zwskchat/target' --exclude='zwskchat/logs' --exclude='zwskchat/.tmp-edge-cdp-profile' -acf zwskchat.tar.gz zwskchat/

# 上传到服务器
scp zwskchat.tar.gz root@<ECS公网IP>:/root/
```

在服务器上解压：

```bash
cd /root
tar -xzf zwskchat.tar.gz
# 现在项目在 /root/zwskchat/zwskchat/
```

### 方式 B：从 Git 克隆

```bash
cd /root
git clone <你的仓库地址>
# 项目在 /root/zwskchat/
```

---

## 第六步：服务器上构建前端

```bash
# 安装 Node.js 20.x
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# 构建前端
cd /root/zwskchat/zwskchat/web
npm install
npm run build
# 看到 ✓ built in xx.xxs 就 OK
```

> `web/dist/` 目录生成后，docker-compose 会直接挂载它给 Nginx。

---

## 第七步：部署配置

```bash
cd /root/zwskchat/zwskchat/deploy

# 1. 放证书
mkdir -p certs
```

把本机的 `fullchain.pem` 和 `privkey.pem` 上传到 `deploy/certs/`：

```powershell
# 本机 Windows PowerShell
scp fullchain.pem root@<ECS公网IP>:/root/zwskchat/zwskchat/deploy/certs/
scp privkey.pem root@<ECS公网IP>:/root/zwskchat/zwskchat/deploy/certs/
```

```bash
# 2. 首次生成 .env（自动生成所有随机密钥）
cd /root/zwskchat/zwskchat/deploy
chmod +x deploy.sh preflight.sh backup-mysql.sh
./deploy.sh
# 脚本会提示你填写 DASHSCOPE_API_KEY，然后退出
```

```bash
# 3. 编辑 .env，填 DashScope Key
vim .env
```

找到这一行：

```
DASHSCOPE_API_KEY=
```

填你的 Key（用之前测试通过的那个），其他密码脚本已自动生成了强随机值，**不要改**。

确认 `PUBLIC_DOMAIN=app.noweyai.com`（默认已经是）。

`:wq` 保存退出。

---

## 第八步：预检 + 上线

```bash
# 预检
./preflight.sh
```

预检会检查：
- Docker 是否可用
- 证书文件是否存在
- DashScope API Key 是否填写
- PUBLIC_DOMAIN 是否配置
- 磁盘剩余空间

全部通过后：

```bash
# 上线！
./deploy.sh
```

首次运行会：
1. 拉取 MySQL / Redis / Qdrant / RabbitMQ / MinIO / Nginx 镜像（约 2-3 分钟）
2. 构建后端 Docker 镜像（约 3-5 分钟，要下载 Maven 依赖 + 编译）
3. 等 MySQL 就绪后自动执行 SQL 迁移脚本（`sql/V1.0__...` 到 `sql/V3.5__...`）
4. 等所有容器健康后输出访问地址

看到这行就说明成功了：

```
✅ 应用已就绪！
访问地址： https://app.noweyai.com/
```

---

## 第九步：验收

### 浏览器验收

打开 `https://app.noweyai.com`：

1.  地址栏出现 **小锁**（HTTPS 正常）
2.  看到登录页
3.  使用部署时设置的管理员初始凭据登录 —— **能进**
4.  新建一个对话，发一条消息 —— **AI 正常回复**
5.  进知识库页，上传一个文档，等解析完成 —— **文档解析正常**
6.  在聊天里问这个文档相关的问题 —— **RAG 检索命中**
7.  进入语音页面，说一句话 —— **语音识别 + AI 回复 + 语音播报**

### 命令行验收

```bash
# 健康检查
curl -fsS https://app.noweyai.com/api/actuator/health

# 容器状态
cd /root/zwskchat/zwskchat/deploy
./deploy.sh ps
```

---

## 第十步：上线后立刻做

### 1. 改默认密码

登录后在设置页修改 admin 密码。管理员凭据不得写入代码仓库或部署文档。

### 2. 跑一次备份

```bash
cd /root/zwskchat/zwskchat/deploy
./backup-mysql.sh
```

备份文件在 `deploy/backups/mysql/`。

### 3. 设自动备份

```bash
# 每天凌晨 3 点自动备份
(crontab -l 2>/dev/null; echo "0 3 * * * cd /root/zwskchat/zwskchat/deploy && ./backup-mysql.sh >> backups/cron.log 2>&1") | crontab -
```

### 4. 开启防火墙

```bash
sudo apt install -y ufw
sudo ufw allow 22
sudo ufw allow 80
sudo ufw allow 443
sudo ufw enable
```

---

## 常用运维命令

```bash
cd /root/zwskchat/zwskchat/deploy

# 看状态
./deploy.sh ps

# 看后端日志
./deploy.sh logs

# 重启
./deploy.sh restart

# 停止（保留数据）
./deploy.sh down

# 前端更新（改了前端代码后）
cd ../web && npm run build && cd ../deploy && ./deploy.sh restart

# 后端更新（改了 Java 代码后）
./deploy.sh      # 会重新构建镜像并重启
```

---

## 故障排查

| 现象 | 检查 |
|------|------|
| 浏览器打不开 | `./deploy.sh ps` 看 nginx 是否 Up；安全组 443 是否开了 |
| HTTPS 不安全 | `ls deploy/certs/` 证书在不在；`cat deploy/.env` 看 PUBLIC_DOMAIN 对不对 |
| 登录报 500 | `./deploy.sh logs` 看后端起没起来；MySQL 迁移跑了没 |
| AI 不回复 | `.env` 里 DASHSCOPE_API_KEY 填了没；Key 是否过期/欠费 |
| 知识库检索为空 | 跑 `./reindex-qdrant.sh` 重建向量索引 |
| 容器无限重启 | `docker logs zwsk-app` 看具体错误 |

---

## 费用预估（香港 ECS）

| 项目 | 月费（约） |
|------|-----------|
| ECS 4核8G + 100GB ESSD | ~400-500 元 |
| 5Mbps 固定带宽 | ~115 元 |
| 合计 | ~500-600 元/月 |

> 这是单机方案。如果未来客户量和数据量上去，再拆成 RDS + 独立 Redis + 多台 ECS。

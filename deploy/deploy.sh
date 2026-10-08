#!/usr/bin/env bash
# =============================================================================
# 知语 AI · 一键部署脚本（Linux / macOS）
#
# 功能：
#   1) 检查 docker / docker compose 是否可用；
#   2) 首次运行：生成强随机密钥写入 .env（已存在则复用，不覆盖）；
#   3) 校验 DASHSCOPE_API_KEY 是否已填；
#   4) 运行生产预检；
#   5) docker compose 构建并启动全栈，等待健康；
#   6) 打印访问地址。
#
# 用法：
#   cd deploy
#   ./deploy.sh                 # 启动 / 更新
#   ./deploy.sh down            # 停止并保留数据
#   ./deploy.sh logs            # 跟踪应用日志
#   ./deploy.sh restart         # 重启
# =============================================================================
set -euo pipefail

cd "$(dirname "$0")"
COMPOSE_FILE="docker-compose.deploy.yml"
ENV_FILE=".env"

# ---- compose 命令探测（v2 插件优先，回退 v1）----
if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "❌ 未找到 docker compose。请先安装 Docker Engine + Compose 插件。"
  echo "   Ubuntu/Debian: https://docs.docker.com/engine/install/"
  exit 1
fi

# ---- 随机串生成（openssl 优先，回退 /dev/urandom）----
rand() {
  local n="${1:-32}"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c "$n"
  else
    LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c "$n"
  fi
}

# ---- 子命令 ----
case "${1:-up}" in
  down)    exec $DC -f "$COMPOSE_FILE" down ;;
  restart) exec $DC -f "$COMPOSE_FILE" restart ;;
  logs)    exec $DC -f "$COMPOSE_FILE" logs -f app ;;
  ps)      exec $DC -f "$COMPOSE_FILE" ps ;;
  up|"")   ;; # 继续往下
  *) echo "用法: ./deploy.sh [up|down|restart|logs|ps]"; exit 1 ;;
esac

# ---- 首次生成 .env ----
if [[ ! -f "$ENV_FILE" ]]; then
  echo "🔐 首次部署：生成强随机密钥到 $ENV_FILE ..."
  JWT_SECRET="$(rand 64)"
  cat > "$ENV_FILE" <<EOF
# 知语 AI 部署配置（本文件含密钥，已被 .gitignore 排除，切勿提交）
# 生成时间：$(date '+%Y-%m-%d %H:%M:%S')

# ===== 必填：阿里云百炼 API Key（对话/向量/语音共用）=====
# 控制台：https://bahuabai.console.aliyun.com/  申请后填到这里
DASHSCOPE_API_KEY=

# ===== 对外端口（默认 80；如被占用改成 8088 等）=====
HTTP_PORT=80

# ===== 域名配置（接入现有域名时填写，不含 https://）=====
# 例：PUBLIC_DOMAIN=ai.example.com
PUBLIC_DOMAIN=app.noweyai.com

# 同域部署留空即可；如果前端和 API 分不同域名，填前端完整 origin
# 例：CORS_ALLOWED_ORIGINS=https://www.example.com,https://example.com
CORS_ALLOWED_ORIGINS=

# ===== JVM 内存（演示/低配建议 50%，生产可调回 75%）=====
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError

# ===== 以下为自动生成的强随机密钥，无需修改 =====
JWT_SECRET=$JWT_SECRET

MYSQL_DB=zwskchat
MYSQL_USER=zwsk
MYSQL_PASSWORD=$(rand 28)
MYSQL_ROOT_PASSWORD=$(rand 28)

REDIS_PASSWORD=$(rand 28)

RABBIT_USER=zwsk
RABBIT_PASSWORD=$(rand 28)

QDRANT_API_KEY=$(rand 28)

MINIO_ACCESS_KEY=$(rand 20)
MINIO_SECRET_KEY=$(rand 40)
MINIO_BUCKET=zwsk-kb

APP_VERSION=latest
EOF
  chmod 600 "$ENV_FILE"
  echo "✅ 已生成 $ENV_FILE"
  echo ""
  echo "⚠️  请编辑 $ENV_FILE 填入 DASHSCOPE_API_KEY 后，重新运行 ./deploy.sh"
  exit 0
fi

# ---- 校验 DASHSCOPE_API_KEY ----
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
if [[ -z "${DASHSCOPE_API_KEY:-}" ]]; then
  echo "❌ $ENV_FILE 里 DASHSCOPE_API_KEY 还是空的。请填入阿里云百炼 Key 后再运行。"
  exit 1
fi

echo "🔎 运行生产预检..."
bash ./preflight.sh "$ENV_FILE"

echo "🚀 构建并启动全栈（首次构建后端镜像约需几分钟）..."
$DC -f "$COMPOSE_FILE" up -d --build

echo ""
echo "⏳ 等待应用就绪（最多 3 分钟）..."
for i in $(seq 1 36); do
  if curl -fsS "http://127.0.0.1:${HTTP_PORT:-80}/api/actuator/health/readiness" >/dev/null 2>&1; then
    echo "✅ 应用已就绪！"
    IP="$(hostname -I 2>/dev/null | awk '{print $1}' || echo '<服务器IP>')"
    echo ""
    echo "==================================================="
    if [[ -n "${PUBLIC_DOMAIN:-}" ]]; then
      echo "  访问地址： https://${PUBLIC_DOMAIN}/"
    else
      echo "  访问地址： http://${IP}:${HTTP_PORT:-80}/"
    fi
    echo "  本机访问： http://localhost:${HTTP_PORT:-80}/"
    echo "  查看日志： ./deploy.sh logs"
    echo "==================================================="
    exit 0
  fi
  sleep 5
done

echo "⚠️  等待超时。用 ./deploy.sh logs 查看后端启动日志排查（多为 DASHSCOPE_API_KEY 错误或端口占用）。"
exit 1

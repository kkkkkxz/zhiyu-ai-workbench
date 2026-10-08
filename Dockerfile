# =============================================================================
# 展望数科 · 知语 AI 后端镜像
# =============================================================================
# 多阶段构建：build 阶段拉依赖 + 打包，runtime 阶段只放 JRE + jar，最终镜像 < 300MB。
# 安全要点：
#   1) 用 eclipse-temurin（adoptium 维护，CVE 跟进及时），固定到 17-jre-jammy
#   2) runtime 用 nonroot 用户跑（uid=10001），不给容器 root，防容器逃逸
#   3) 不打 COPY .（避免误把 .env / D:/conf 类秘密塞进镜像）
#   4) 健康检查走 /actuator/health/liveness（K8s 也可以接管）
#   5) JVM 容器化参数：UseContainerSupport + MaxRAMPercentage 让 JVM 自动按 cgroup
#      限制感知堆大小，避免 OOMKilled
# =============================================================================

# ----- Build stage -----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# 先只拷 pom.xml 拉依赖，利用 docker layer 缓存——后续只改 java 文件不必重拉依赖
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw
RUN mvn -B -e -ntp dependency:go-offline

# 再拷源码 + 资源
COPY src ./src

# 跳过测试构建（CI 单独一步 mvn test，镜像构建专注产出 jar）
RUN mvn -B -e -ntp clean package -DskipTests \
 && cp target/*.jar /workspace/app.jar

# ----- Runtime stage -----
FROM eclipse-temurin:17-jre-jammy AS runtime

# 非 root 用户：uid 10001，避免容器以 root 跑
RUN groupadd --system --gid 10001 zwsk \
 && useradd  --system --uid 10001 --gid zwsk --home-dir /app --shell /sbin/nologin zwsk \
 && mkdir -p /app/logs \
 && chown -R zwsk:zwsk /app

WORKDIR /app

# 安装 curl 用于 HEALTHCHECK；用完清理 apt 缓存让镜像更瘦
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tini ca-certificates fontconfig \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=zwsk:zwsk /workspace/app.jar /app/app.jar

# 时区：和 application.yaml 的 jackson.time-zone=GMT+8 对齐
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    # JVM 容器感知 + 默认堆为容器内存的 75%，留 25% 给非堆（Metaspace/直接内存/Tomcat NIO 缓冲）
    JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom \
               -Duser.timezone=Asia/Shanghai" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

EXPOSE 8080

USER zwsk

# tini 做 PID 1：处理子进程信号、回收僵尸进程，K8s SIGTERM 能优雅传递到 JVM
ENTRYPOINT ["/usr/bin/tini", "--"]

# 用 sh -c 是为了让 ${JAVA_OPTS} 等环境变量在运行时展开，便于用户覆盖
CMD ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]

# 容器内自检（K8s 通常用 livenessProbe/readinessProbe 替代，但单机 docker 部署有用）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/api/actuator/health/liveness || exit 1

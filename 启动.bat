@echo off
chcp 65001 >nul
title 知语 AI · 一键启动

echo.
echo  ╔══════════════════════════════════════════╗
echo  ║     知语 AI · ZWSK Chat  演示版         ║
echo  ║     企业知识库 RAG 智能问答平台          ║
echo  ╚══════════════════════════════════════════╝
echo.

:: ============================================================
:: 第 1 步：检查 Docker
:: ============================================================
echo  [1/5] 检查 Docker...
docker version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo  ❌ 未检测到 Docker Desktop！
    echo.
    echo  📌 请先安装 Docker Desktop：
    echo     https://www.docker.com/products/docker-desktop/
    echo.
    echo  安装完成后重启电脑，再双击本脚本。
    echo.
    pause
    exit /b 1
)
echo  ✅ Docker 已就绪
echo.

:: ============================================================
:: 第 2 步：切换到脚本所在目录
:: ============================================================
cd /d "%~dp0"
cd deploy

:: ============================================================
:: 第 3 步：检查前端是否已构建
:: ============================================================
echo  [2/5] 检查前端...
if not exist "..\web\dist\index.html" (
    echo  ⚠️  前端未构建，正在构建（仅首次需要，约 1-2 分钟）...
    cd ..\web
    call npm install --silent 2>nul
    call npm run build 2>nul
    cd ..\deploy
    if not exist "..\web\dist\index.html" (
        echo  ❌ 前端构建失败，请检查是否安装了 Node.js
        echo  📌 下载地址：https://nodejs.org/
        pause
        exit /b 1
    )
)
echo  ✅ 前端已就绪
echo.

:: ============================================================
:: 第 4 步：创建 .env（如果不存在）
:: ============================================================
echo  [3/5] 检查配置...
if not exist ".env" (
    echo  首次运行，生成本机随机配置...
    powershell -NoProfile -ExecutionPolicy Bypass -File "..\scripts\win\generate-demo-env.ps1" -Path "%CD%\.env"
    if %errorlevel% neq 0 (
        echo  ❌ 配置生成失败。
        pause
        exit /b 1
    )
    echo  ✅ 已生成本机配置（不会提交到 Git）
    echo.
    echo  ⚠️  AI 对话功能需要阿里云百炼 API Key
    echo  如有 Key，请编辑 deploy\.env 文件，修改 DASHSCOPE_API_KEY=你的Key
    echo  没有 Key 也能启动，但 AI 对话会提示 Key 无效。
) else (
    echo  ✅ 配置文件已存在
)
echo.

:: ============================================================
:: 第 5 步：启动
:: ============================================================
echo  [4/5] 启动服务（首次需下载镜像和编译，约 5-10 分钟）...
echo  请耐心等待，不要关闭此窗口...
echo.

docker compose -f docker-compose.demo.yml up -d --build 2>&1
if %errorlevel% neq 0 (
    echo.
    echo  ❌ 启动失败，请检查上方错误信息。
    pause
    exit /b 1
)

:: ============================================================
:: 等待就绪
:: ============================================================
echo.
echo  [5/5] 等待服务就绪...
echo  正在启动 MySQL、Redis、RabbitMQ、MinIO、Qdrant、应用、Nginx...
echo.

set /a count=0
:wait_loop
set /a count+=1
timeout /t 10 /nobreak >nul
curl -s -o nul http://127.0.0.1/api/actuator/health 2>nul
if %errorlevel% equ 0 goto ready
if %count% geq 30 (
    echo.
    echo  ⚠️  等待超时（5 分钟），可能还在编译中。
    echo  请稍后打开浏览器访问 http://127.0.0.1
    echo  如需查看日志：cd deploy ^&^& docker compose -f docker-compose.demo.yml logs app
    echo.
    pause
    exit /b 0
)
echo  等待中... (%count%/30)
goto wait_loop

:ready
echo.
echo  ╔══════════════════════════════════════════╗
echo  ║     ✅ 启动成功！                       ║
echo  ║                                        ║
echo  ║  🌐 浏览器打开：http://127.0.0.1        ║
echo  ║  👤 用户名：admin                       ║
echo  ║  🔑 初始密码：见 deploy\.env             ║
echo  ╚══════════════════════════════════════════╝
echo.

:: 自动打开浏览器
start http://127.0.0.1

echo  按任意键关闭此窗口（不会停止服务）...
pause >nul
exit /b 0

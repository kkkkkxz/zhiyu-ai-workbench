# 展望 AI · 控制台前端 (`web/`)

> React 18 + Vite 5 + TypeScript + TailwindCSS + shadcn/ui
> 与 `zwskchat` 后端配套，提供登录、知识库管理、SSE 流式问答、语音对话等能力。

## 技术栈

- **核心**：React 18 / TypeScript / Vite 5
- **样式**：TailwindCSS 3 / shadcn/ui（Radix UI 原语）/ lucide-react 图标
- **状态**：Zustand（含 persist）
- **路由**：React Router v6
- **HTTP**：Axios + 自定义拦截器
- **大整数 ID**：`json-bigint` 处理后端 Snowflake Long → 字符串
- **流式问答**：原生 `fetch` + `ReadableStream` 解析 SSE
- **Markdown**：react-markdown + remark-gfm
- **通知**：sonner

## 目录

```
web/
├── index.html
├── vite.config.ts            # /api 代理至 http://127.0.0.1:18080
├── tailwind.config.ts
├── components.json           # shadcn/ui 配置
├── src/
│   ├── main.tsx              # 入口（应用主题、router、toaster）
│   ├── App.tsx               # 顶层 Outlet
│   ├── router.tsx            # 路由表（含 ProtectedRoute）
│   ├── index.css             # Tailwind + 主题 CSS 变量
│   ├── lib/
│   │   ├── api.ts            # axios 实例 + json-bigint 解析 + 401 拦截
│   │   ├── sse.ts            # fetch + ReadableStream SSE 解析
│   │   ├── utils.ts          # cn 类名合并
│   │   └── format.ts         # 字节/时间/截断
│   ├── stores/
│   │   ├── auth.ts           # 登录态 + token（持久化）
│   │   └── theme.ts          # 主题偏好（light/dark/system）
│   ├── api/                  # auth / user / kb / doc / chat
│   ├── types/                # api.ts / voice.ts
│   ├── components/
│   │   ├── ui/               # shadcn 组件（button / dialog / dropdown / scroll-area / ...）
│   │   ├── layout/           # AppShell / Sidebar / TopBar / ProtectedRoute
│   │   ├── common/           # ThemeToggle / EmptyState / ConfirmDialog
│   │   ├── chat/             # MessageItem / MessageComposer / References / KbSelector
│   │   ├── kb/               # CreateKbDialog / DocUpload / ParseStatusBadge
│   │   └── voice/            # useVoiceWs（getUserMedia + WebSocket + PCM 播放）
│   └── pages/                # Login / Chat / KbList / KbDetail / Voice / Settings / 404
└── public/
    └── favicon.svg
```

## 功能清单

| 模块 | 路径 | 说明 |
| --- | --- | --- |
| 登录 | `/login` | 用户名 + 密码；登录后跳转到 `?next` 或 `/chat` |
| 对话 | `/chat`、`/chat/:id` | 流式 SSE；支持引用展开、token/延迟统计、停止生成、知识库挂载 |
| 知识库列表 | `/kb` | 创建 / 进入详情 / 删除 |
| 知识库详情 | `/kb/:id` | 拖拽上传（PDF/Word/PPT/Excel/HTML/MD/TXT），轮询 `parseStatus`，删除 |
| 语音 | `/voice` | WebSocket（`/api/voice/ws?token=...`），16kHz PCM 上行 + PCM 播放 |
| 设置 | `/settings` | 账号信息、主题切换 |

## 环境与运行

### 必要前置

后端 `zwskchat` 运行中（推荐 `127.0.0.1:18080`，与 `vite.config.ts` 的 `/api` 代理目标一致）。

### 开发

```bash
cd web
npm install
npm run dev     # http://localhost:3000
```

如需修改后端代理目标：

```bash
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8080'
npm run dev
```

如需临时通过 ngrok / trycloudflare / cpolar 等隧道访问 Vite 开发服务，显式放开 Host：

```bash
$env:VITE_DEV_ALLOWED_HOSTS = '*'
npm run dev
```

### 生产构建

```bash
npm run build
npm run preview
```

构建产物在 `web/dist`，可由后端 Spring Boot 静态托管或独立 nginx 前置。

## 关键设计

### 1. Long ID 精度

后端把雪花 Long 直接序列化为 JSON 数字（如 `2048684388430503937`），超过 JS `Number.MAX_SAFE_INTEGER`。
本前端在 `lib/api.ts` 中对所有 `application/json` 响应统一使用 `json-bigint` 解析，自动把大整数转为字符串。
所有 `types/api.ts` 中的 ID 字段类型都声明为 `string`。

### 2. SSE 流式

```ts
chatApi.streamCompletion(sessionId, { question, kbIds }, {
  onEvent: (event, data) => { ... },  // event=references / delta / done / error
});
```

后端事件：
- `references`：JSON 数组（命中片段）
- `delta`：纯文本增量
- `done`：JSON `{ messageId, latencyMs, tokenIn, tokenOut }`
- `error`：JSON `{ code, message }`

### 3. 路由守卫

`ProtectedRoute` 检查 `useAuthStore.isAuthenticated()`；未登录时携带 `?next=...` 跳到 `/login`。
`api.ts` 的 401 拦截会自动清空 token 并跳登录。

### 4. 语音协议（与后端 ServerMessage / ClientMessage 对齐）

- 文本控制帧：`START` / `END` / `INTERRUPT` / `RESET` / `PING`
- 二进制：上行 16kHz / 16bit / mono PCM；下行 PCM（默认按 24kHz 解释，CosyVoice 默认输出）

## 默认账号

| 用户名 | 密码 |
| --- | --- |
| `admin` | 部署时设置的初始管理员密码 |

> 仅用于本地开发库；登录页不会预填密码，生产请立即修改或禁用默认账号。

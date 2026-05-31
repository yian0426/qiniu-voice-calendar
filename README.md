# 🗣️ 七牛语音日历 — Qiniu Voice Calendar

> 一款以 **AI + 语音交互** 为核心的智能日历管理工具。用户通过语音或文字与日历助手对话，即可完成日程的创建、查询、修改、删除和标记完成等操作。

**Demo 视频：** [bilibili — 七牛云语音日历 demo](https://www.bilibili.com/video/BV1ePVQ6BEPx/)

---

## ✨ 核心特性

| 特性                | 说明                                                                    |
| ------------------- | ----------------------------------------------------------------------- |
| 🎙️ **语音输入**     | 基于浏览器 Web Speech API 的实时语音识别，支持中文                      |
| 🤖 **AI 智能理解**  | 接入小米 MiMo V2.5 大模型，通过 Tool Calling 自动解析自然语言为日程操作 |
| 📅 **三种日历视图** | 日程视图（按日分组）、周视图（24h×7d 网格）、月视图（传统月历）         |
| 💬 **流式对话**     | SSE 实时流式输出，支持文本聊天 + 语音聊天双模式                         |
| 🔄 **实时同步**     | AI 执行操作后，左侧日历通过 `event_data` SSE 事件即时响应式更新         |
| 🔐 **JWT 鉴权**     | 无状态 Token 认证，支持注册/登录/个人中心                               |
| 🏷️ **标签管理**     | 自定义标签，自动随事件创建，按标签筛选日程                              |
| 🌌 **星空主题**     | 暗色玻璃拟态 UI，星空动画背景，视觉体验优秀                             |

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────┐
│                    Frontend (Vue 3)                  │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │ 语音输入  │  │ 文本聊天  │  │ 日历视图 (3种)    │  │
│  │ Web Speech│  │ SSE 流式  │  │ 响应式更新        │  │
│  └────┬─────┘  └────┬─────┘  └───────────────────┘  │
│       └──────────────┼───────────────────────────────┘
│                      │  Axios / fetch (SSE)
├──────────────────────┼──────────────────────────────
│                      ▼
│              Backend (Spring Boot)                   │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │ 语音端点  │  │ 聊天端点  │  │ REST API (15+)   │  │
│  │ VoiceAPI  │  │ ChatSSE   │  │ 事件/标签/用户    │  │
│  └────┬─────┘  └────┬─────┘  └───────────────────┘  │
│       └──────────────┼───────────────────────────────┘
│                      ▼
│          AI Service (MiMo V2.5 Pro)                 │
│    ┌───────────────────────────────────────────┐     │
│    │  Tool Calling: create_event / list_events  │     │
│    │              update_event / delete_event    │     │
│    │  → 自动执行日程操作 → 返回结构化 JSON       │     │
│    └───────────────────────────────────────────┘     │
│                      │                               │
│              MySQL 8.0  (users, events,              │
│                          tags, conversations,        │
│                          messages)                   │
└─────────────────────────────────────────────────────┘
```

### 前端技术栈

| 技术                                       | 用途           |
| ------------------------------------------ | -------------- |
| Vue 3 (Composition API + `<script setup>`) | UI 框架        |
| TypeScript                                 | 类型安全       |
| Vite                                       | 构建工具       |
| Pinia                                      | 状态管理       |
| Vue Router                                 | 路由           |
| Element Plus (暗色主题)                    | UI 组件库      |
| Lucide Vue                                 | 图标库         |
| Axios                                      | HTTP 请求      |
| Web Speech API                             | 浏览器语音识别 |
| SCSS                                       | 样式           |

### 后端技术栈

| 技术                                | 用途                     |
| ----------------------------------- | ------------------------ |
| Spring Boot 3.3.4                   | Web 框架                 |
| Java 21                             | 运行时                   |
| Spring Security + JWT (jjwt 0.12.7) | 认证鉴权                 |
| MyBatis-Plus 3.5.10.1               | ORM                      |
| MySQL 8.0                           | 数据库                   |
| 小米 MiMo V2.5 Pro                  | AI 大模型 (Tool Calling) |
| Lombok                              | 代码简化                 |
| Maven                               | 构建工具                 |

---

## 🎬 功能演示

### 语音创建日程

```
用户: "安排明天下午3点的会议讨论项目进度"
  ↓ Web Speech API 识别
  ↓ MiMo AI 解析 → Tool Calling (create_event)
  ↓ 日历即时更新

助手: "已为你创建明天 15:00-16:00 的会议「讨论项目进度」✅"
```

### 语音查询日程

```
用户: "我今天有什么安排？"
  ↓ MiMo AI → list_events
  ↓ 返回当天事件列表

助手: "今天有 2 个事件：14:00 周会、16:00 代码 Review"
```

### 语音修改日程

```
用户: "把面试改到后天上午10点"
  ↓ MiMo AI → update_event
  ↓ 日历即时更新

助手: "已将面试时间修改为后天上午 10:00-11:00 ✅"
```

---

## 🚀 快速开始

### 环境要求

- **Node.js** ≥ 18
- **Java** ≥ 21
- **Maven** ≥ 3.8
- **MySQL** ≥ 8.0
- **Chrome / Edge** 浏览器（语音识别功能需要）

### 1. 数据库初始化

```sql
-- 执行建表脚本
source sql/create_tables.sql
```

### 2. 启动后端

```bash
cd voice_calendar-backend

# 配置数据库连接（修改 application.yaml 或设置环境变量）
export MYSQL_PASSWORD=your_password
export AI_API_KEY=your_mimo_api_key

# 编译并启动
mvn clean package -DskipTests
java -jar target/voice_calendar-0.0.1-SNAPSHOT.jar
```

后端默认运行在 `http://localhost:8080`

### 3. 启动前端

```bash
cd fronted

npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，自动代理 `/api` 到后端 8080 端口。

### 4. 开始使用

1. 打开浏览器访问 `http://localhost:5173`
2. 注册账号并登录
3. 点击右下角麦克风图标，用语音输入日程指令
4. 也可以在文本框中直接输入文字指令

---

## 📁 项目结构

```
七牛云/
├── fronted/                          # 前端项目
│   ├── src/
│   │   ├── main.ts                   # 入口
│   │   ├── App.vue                   # 根组件
│   │   ├── router/index.ts           # 路由配置
│   │   ├── stores/                   # Pinia 状态管理
│   │   │   ├── auth.ts               # 认证状态
│   │   │   └── events.ts             # 事件状态（含 AI 集成）
│   │   ├── utils/
│   │   │   └── request.ts            # Axios + SSE 流式请求
│   │   ├── composables/
│   │   │   └── useSpeechRecognition.ts  # 语音识别封装
│   │   ├── views/
│   │   │   └── HomeView.vue          # 首页布局
│   │   ├── features/
│   │   │   ├── calendar/             # 日历模块
│   │   │   │   ├── AgendaPanel.vue   # 日程面板（3种视图）
│   │   │   │   └── CalendarDayCell.vue
│   │   │   ├── chat/                 # 聊天模块
│   │   │   │   ├── ChatPanel.vue     # 聊天面板（文本+语音）
│   │   │   │   └── ChatMessageItem.vue
│   │   │   └── auth/
│   │   │       └── LoginDialog.vue   # 登录弹窗
│   │   ├── components/
│   │   │   ├── SidebarAvatar.vue     # 侧边栏头像
│   │   │   └── ThemeTag.vue          # 主题标签
│   │   └── assets/
│   │       └── layout.scss           # 全局星空暗色主题样式
│   └── package.json
│
├── voice_calendar-backend/           # 后端项目
│   ├── src/main/java/com/qiniu/voice_calendar/
│   │   ├── VoiceCalendarBackendApplication.java
│   │   ├── common/Result.java        # 统一响应包装
│   │   ├── config/                   # 安全配置 + AI 配置
│   │   │   ├── SecurityConfig.java
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   └── AiProperties.java
│   │   ├── controller/               # 控制器层
│   │   │   ├── AuthController.java   # 认证 (4 个端点)
│   │   │   ├── EventController.java  # 事件 CRUD (7 个端点)
│   │   │   ├── TagController.java    # 标签 CRUD (4 个端点)
│   │   │   ├── ChatController.java   # AI 聊天 (SSE 流式)
│   │   │   └── VoiceController.java  # 语音处理 (SSE 流式)
│   │   ├── dto/                      # 请求/响应 DTO (16 个)
│   │   ├── entity/                   # 数据库实体
│   │   ├── mapper/                   # MyBatis-Plus Mapper
│   │   ├── service/                  # 业务逻辑层
│   │   │   ├── AiService.java        # AI 服务接口
│   │   │   ├── ChatService.java      # 聊天服务
│   │   │   ├── VoiceService.java     # 语音服务
│   │   │   └── impl/
│   │   │       ├── OpenAiCompatibleService.java  # MiMo API 对接
│   │   │       ├── ChatServiceImpl.java          # 聊天+工具调用
│   │   │       └── VoiceServiceImpl.java         # 语音处理
│   │   ├── exception/                # 全局异常处理
│   │   └── util/                     # JWT + 安全上下文工具
│   └── pom.xml
│
├── sql/                              # 数据库脚本
│   └── create_tables.sql
├── docs/                             # 设计文档
│   ├── api-design.md                 # 接口设计文档
│   └── database-design.md            # 数据库设计文档
├── DEVELOPMENT_DOC.md                # 开发设计文档
└── CLAUDE.md                         # AI 辅助开发指引
```

---

## 🔌 后端 API 接口

### 认证模块

| Method | Path                 | 说明               |
| ------ | -------------------- | ------------------ |
| POST   | `/api/auth/register` | 用户注册，返回 JWT |
| POST   | `/api/auth/login`    | 用户登录，返回 JWT |
| GET    | `/api/auth/profile`  | 获取个人信息       |
| PUT    | `/api/auth/profile`  | 更新个人信息       |

### 事件模块

| Method | Path                      | 说明                                          |
| ------ | ------------------------- | --------------------------------------------- |
| GET    | `/api/events`             | 分页查询事件（支持日期/状态/标签/关键词筛选） |
| GET    | `/api/events/{id}`        | 获取单个事件详情                              |
| POST   | `/api/events`             | 创建事件                                      |
| PUT    | `/api/events/{id}`        | 全量更新事件                                  |
| PATCH  | `/api/events/{id}`        | 部分更新事件                                  |
| DELETE | `/api/events/{id}`        | 删除事件                                      |
| PATCH  | `/api/events/{id}/status` | 切换完成状态                                  |

### 标签模块

| Method | Path             | 说明                       |
| ------ | ---------------- | -------------------------- |
| GET    | `/api/tags`      | 查询用户标签（含事件计数） |
| POST   | `/api/tags`      | 创建标签                   |
| PUT    | `/api/tags/{id}` | 更新标签                   |
| DELETE | `/api/tags/{id}` | 删除标签（级联）           |

### AI 对话模块

| Method | Path                               | 说明                     |
| ------ | ---------------------------------- | ------------------------ |
| POST   | `/api/chat/stream`                 | 文本对话（SSE 流式）     |
| POST   | `/api/voice/process`               | 语音指令处理（SSE 流式） |
| GET    | `/api/conversations`               | 获取对话列表             |
| GET    | `/api/conversations/{id}/messages` | 获取对话消息             |
| DELETE | `/api/conversations/{id}`          | 删除对话                 |

---

## 🗃️ 数据库设计

```sql
users ──1:N── events ──N:N── tags (via event_tags)
  │
  └──1:N── conversations ──1:N── messages
```

| 表名            | 说明          | 核心字段                                                |
| --------------- | ------------- | ------------------------------------------------------- |
| `users`         | 用户表        | username, password(BCrypt), email, avatar_url           |
| `events`        | 事件表        | title, start_time, end_time, status, participants(JSON) |
| `tags`          | 标签表        | name, color (用户级唯一)                                |
| `event_tags`    | 事件-标签关联 | event_id, tag_id (多对多)                               |
| `conversations` | 对话会话      | user_id, title                                          |
| `messages`      | 对话消息      | conversation_id, role, content                          |

---

## 🧠 AI 工作原理

### 语音处理流程

```
1. 浏览器 Web Speech API 实时识别语音 → 转录为文本
2. 前端通过 SSE 发送文本到 /api/voice/process
3. 后端构建 LLM 上下文（系统提示词 + 当前时间 + 用户输入）
4. MiMo V2.5 通过 Tool Calling 决定操作：
   - create_event → 自动创建日历事件 → 返回 event_data SSE 事件
   - list_events  → 查询事件列表
   - update_event → 修改事件 → 返回 event_data SSE 事件
   - delete_event → 删除事件 → 返回 event_data SSE 事件
5. 前端收到 event_data → eventStore.applyFromAI() → 日历即时更新
6. MiMo 生成自然语言回复 → SSE 流式推送到前端
```

### Tool Calling 工具定义

| 工具名         | 说明         | 必填参数                          |
| -------------- | ------------ | --------------------------------- |
| `create_event` | 创建日历事件 | title, startTime, endTime         |
| `list_events`  | 查询事件     | (可选) startDate, endDate, status |
| `update_event` | 修改事件     | eventId + 需修改的字段            |
| `delete_event` | 删除事件     | eventId                           |
| `get_event`    | 查看详情     | eventId                           |

---

## 📊 开发历程

| 日期       | 提交      | 说明                                           |
| ---------- | --------- | ---------------------------------------------- |
| 2026-05-29 | `fe046fb` | 搭建前端基础框架 (Vue 3 + Vite + TypeScript)   |
| 2026-05-30 | `0a2b010` | 完善首页左侧日历内容，使用模拟数据调整 UI      |
| 2026-05-30 | `e38e96f` | 初始化后端项目 (Spring Boot + MyBatis-Plus)    |
| 2026-05-30 | `612026f` | 添加开发文档、数据库设计文档、接口文档         |
| 2026-05-30 | `5bdf3bd` | 添加数据库建表脚本                             |
| 2026-05-30 | `5dc0b09` | 实现用户注册、登录、个人中心、主题切换         |
| 2026-05-31 | `de3c100` | 实现事件增删改查、分页查询、状态切换           |
| 2026-05-31 | `016720c` | 实现标签增删改查功能                           |
| 2026-05-31 | `d4ebf56` | 完成 AI 对话功能模块 (SSE 流式 + Tool Calling) |
| 2026-05-31 | `af6089c` | 基本实现语音日历功能，优化前后端交互           |
| 2026-05-31 | `bebfc2e` | 完善语音功能，接入 MiMo AI 生成日历事件        |

---

## 📝 开发文档

- [开发设计文档](DEVELOPMENT_DOC.md) — 产品概述、功能设计、NLP 意图定义
- [接口设计文档](docs/api-design.md) — 完整 API 接口规范
- [数据库设计文档](docs/database-design.md) — ER 图、表结构、索引设计
- [数据库建表脚本](sql/create_tables.sql) — 可直接执行的 SQL

---

## 📄 License

MIT License © 2026 [yian0426](https://github.com/yian0426)

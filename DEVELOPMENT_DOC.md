# 七牛语音日历 — 开发设计文档

## 1. 产品概述

七牛语音日历是一款以**语音交互为核心**的智能日历管理工具。用户通过自然语言与日历助手对话，即可完成事件的创建、查询、修改、删除、标记完成等操作，大幅提升日历管理的效率和便捷性。

### 1.1 核心交互流程

```
用户语音输入 → 前端录音 → 后端语音识别(ASR) → NLP意图解析 → 业务处理 → 生成回复 → 语音合成(TTS) → 前端播放
```

同时支持**文本输入**作为降级方案，直接发送文字指令。

---

## 2. 功能设计

### 2.1 用户模块

| 功能 | 描述 |
|------|------|
| 注册 | 用户名 + 密码注册，可选邮箱 |
| 登录 | 账号密码登录，返回 JWT Token |
| 个人中心 | 查看/修改个人信息（昵称、头像） |
| 切换主题 | 明暗主题切换（前端已预留入口） |

### 2.2 语音/文本对话

| 功能 | 描述 |
|------|------|
| 语音输入 | 前端录音（Web Audio API），发送音频流到后端，后端调用 ASR 引擎转文字 |
| 文本输入 | 直接输入文字指令，作为语音的降级方案 |
| 意图识别 | 后端解析用户输入，识别意图（创建/查询/修改/删除/完成/闲聊） |
| 多轮对话 | 当信息不完整时（如只说"添加一个会议"但未指定时间），助手反问补充信息 |
| 语音播报 | 后端 TTS 合成回复语音，前端播放 |
| 对话历史 | 保存对话记录，支持查看和恢复历史会话 |

### 2.3 事件管理

| 功能 | 描述 | 语音示例 |
|------|------|----------|
| 创建事件 | 创建包含标题、时间、描述、标签、提醒的事件 | "明天下午3点开会讨论项目进度" |
| 查询事件 | 按日期/关键词/标签查询事件 | "明天有什么安排"、"查看所有工作标签的任务" |
| 修改事件 | 修改事件的任意字段 | "把面试时间改到后天上午10点" |
| 删除事件 | 删除指定事件 | "删除周报整理这个任务" |
| 标记完成 | 标记事件为已完成/未完成 | "健身已经完成了" |
| 添加备注 | 为已有事件添加备注或图片笔记 | "给早上的会议加上会议纪要链接" |

### 2.4 日历视图（前端已实现）

- **日程视图**：按日期分组的事件列表
- **本周视图**：7天 × 24小时的时间网格
- **本月视图**：传统月历格子，显示每日事件缩略
- **筛选**：按标签筛选、显示/隐藏已完成事件

### 2.5 NLP 意图定义

| 意图 | 说明 | 触发示例 |
|------|------|----------|
| `CREATE_EVENT` | 创建新事件 | "帮我添加…"、"新建…"、"安排…"、"定一个…" |
| `QUERY_EVENT` | 查询事件 | "今天有什么"、"查看…"、"几点的…"、"列出…" |
| `UPDATE_EVENT` | 修改事件 | "把…改成…"、"修改…"、"…推迟到…"、"…提前到…" |
| `DELETE_EVENT` | 删除事件 | "删除…"、"取消…"、"去掉…" |
| `COMPLETE_EVENT` | 标记完成 | "完成了…"、"做完了…"、"…已经结束了" |
| `GENERAL_CHAT` | 闲聊/非指令 | 问候、感谢、无明确意图的对话 |

---

## 3. 数据库表设计

### 3.1 ER 关系概览

```
users ──1:N── events ──1:N── event_tags ──N:1── tags
  │               │
  │               └──1:N── reminders
  │               └──1:N── attachments
  └──1:N── conversations ──1:N── messages
```

### 3.2 表结构

#### `users` 用户表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) | UNIQUE, NOT NULL | 用户名 |
| password_hash | VARCHAR(255) | NOT NULL | 密码哈希(BCrypt) |
| email | VARCHAR(100) | UNIQUE, DEFAULT NULL | 邮箱 |
| avatar_url | VARCHAR(500) | DEFAULT NULL | 头像URL |
| created_at | DATETIME | NOT NULL, DEFAULT NOW() | 创建时间 |
| updated_at | DATETIME | NOT NULL, DEFAULT NOW() ON UPDATE | 更新时间 |

#### `events` 事件表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | FK → users.id, NOT NULL | 所属用户 |
| title | VARCHAR(500) | NOT NULL | 事件标题 |
| description | TEXT | DEFAULT NULL | 事件描述 |
| start_time | DATETIME | NOT NULL | 开始时间 |
| end_time | DATETIME | NOT NULL | 结束时间 |
| duration | VARCHAR(20) | DEFAULT NULL | 时长(前端展示用: "1h", "30m") |
| location | VARCHAR(500) | DEFAULT NULL | 地点 |
| status | TINYINT | NOT NULL, DEFAULT 0 | 0=未完成, 1=已完成 |
| participants | JSON | DEFAULT NULL | 参与人列表 `["张三", "李四"]` |
| reminder_before | INT | DEFAULT NULL | 提醒提前分钟数(5/10/15/30/60) |
| created_at | DATETIME | NOT NULL, DEFAULT NOW() | 创建时间 |
| updated_at | DATETIME | NOT NULL, DEFAULT NOW() ON UPDATE | 更新时间 |

索引：`INDEX idx_user_start (user_id, start_time)`, `INDEX idx_user_status (user_id, status)`

#### `tags` 标签表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | FK → users.id, NOT NULL | 所属用户 |
| name | VARCHAR(50) | NOT NULL | 标签名 |
| color | VARCHAR(20) | DEFAULT '#909399' | 标签颜色 |
| created_at | DATETIME | NOT NULL, DEFAULT NOW() | 创建时间 |

索引：`UNIQUE uk_user_name (user_id, name)`

#### `event_tags` 事件-标签关联表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| event_id | BIGINT | FK → events.id ON DELETE CASCADE | 事件ID |
| tag_id | BIGINT | FK → tags.id ON DELETE CASCADE | 标签ID |

索引：`UNIQUE uk_event_tag (event_id, tag_id)`

#### `reminders` 提醒记录表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| event_id | BIGINT | FK → events.id ON DELETE CASCADE | 关联事件 |
| remind_at | DATETIME | NOT NULL | 提醒时间 |
| sent | TINYINT | NOT NULL, DEFAULT 0 | 0=未发送, 1=已发送 |
| sent_at | DATETIME | DEFAULT NULL | 实际发送时间 |
| created_at | DATETIME | NOT NULL, DEFAULT NOW() | 创建时间 |

索引：`INDEX idx_remind_pending (remind_at, sent)`

#### `conversations` 对话会话表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | FK → users.id, NOT NULL | 所属用户 |
| title | VARCHAR(200) | DEFAULT '新对话' | 会话标题 |
| created_at | DATETIME | NOT NULL, DEFAULT NOW() | 创建时间 |
| updated_at | DATETIME | NOT NULL, DEFAULT NOW() ON UPDATE | 更新时间 |

#### `messages` 消息表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| conversation_id | BIGINT | FK → conversations.id ON DELETE CASCADE | 所属会话 |
| role | VARCHAR(10) | NOT NULL | `user` 或 `assistant` |
| content | TEXT | NOT NULL | 消息文本内容(用户原文或助手回复) |
| intent | VARCHAR(30) | DEFAULT NULL | 用户消息的意图(仅user消息) |
| audio_url | VARCHAR(500) | DEFAULT NULL | 关联的音频文件URL(用户语音/助手的TTS) |
| metadata | JSON | DEFAULT NULL | 扩展数据(如意图解析的实体参数) |
| created_at | DATETIME | NOT NULL, DEFAULT NOW() | 创建时间 |

#### `attachments` 附件表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| event_id | BIGINT | FK → events.id ON DELETE CASCADE | 关联事件 |
| file_url | VARCHAR(500) | NOT NULL | 文件URL |
| file_type | VARCHAR(20) | NOT NULL | 文件类型: `image`, `file` |
| file_name | VARCHAR(200) | DEFAULT NULL | 原始文件名 |
| created_at | DATETIME | NOT NULL, DEFAULT NOW() | 创建时间 |

---

## 4. 接口设计

### 4.1 通用约定

**Base URL：** `/api`

**鉴权：** 除注册/登录外，所有接口需在 Header 中携带 `Authorization: Bearer <token>`。

**响应格式：** 统一使用以下 JSON 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

| code | 含义 |
|------|------|
| 200 / 0 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录 / Token 过期 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

### 4.2 用户认证

#### POST /api/auth/register — 注册

```
Request:
{
  "username": "zhangsan",
  "password": "123456",
  "email": "zhangsan@example.com"   // 可选
}

Response:
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "userId": 1,
    "username": "zhangsan",
    "token": "eyJhbGciOi..."
  }
}
```

#### POST /api/auth/login — 登录

```
Request:
{
  "username": "zhangsan",
  "password": "123456"
}

Response:
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "userId": 1,
    "username": "zhangsan",
    "token": "eyJhbGciOi..."
  }
}
```

#### GET /api/auth/profile — 获取个人信息

```
Response:
{
  "code": 200,
  "data": {
    "id": 1,
    "username": "zhangsan",
    "email": "zhangsan@example.com",
    "avatarUrl": "https://...",
    "createdAt": "2026-05-01T10:00:00"
  }
}
```

### 4.3 事件管理

#### GET /api/events — 查询事件列表

```
Query Params:
  startDate   string   开始日期 yyyy-MM-dd (可选)
  endDate     string   结束日期 yyyy-MM-dd (可选)
  status      int      0=未完成, 1=已完成 (可选, 默认全部)
  tag         string   标签名筛选 (可选)
  keyword     string   标题搜索关键词 (可选)
  page        int      页码 (默认1)
  size        int      每页条数 (默认50)

Response:
{
  "code": 200,
  "data": {
    "total": 12,
    "page": 1,
    "size": 50,
    "records": [
      {
        "id": 1,
        "title": "团队站会",
        "description": "每日站会，同步进度",
        "startTime": "2026-05-30T09:30:00",
        "endTime": "2026-05-30T09:45:00",
        "duration": "15m",
        "location": "会议室A",
        "status": 0,
        "participants": ["张三", "李四"],
        "tags": ["工作"],
        "reminderBefore": 5,
        "createdAt": "2026-05-28T08:00:00"
      }
    ]
  }
}
```

#### POST /api/events — 创建事件

```
Request:
{
  "title": "项目讨论会",
  "description": "讨论新功能设计方案",
  "startTime": "2026-05-31T14:00:00",
  "endTime": "2026-05-31T15:30:00",
  "duration": "1h 30m",
  "location": "3楼会议室",
  "participants": ["张三", "李四"],
  "tags": ["工作", "会议"],
  "reminderBefore": 15
}

Response:
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 13,
    "title": "项目讨论会",
    ...
  }
}
```

#### GET /api/events/{id} — 获取事件详情

#### PUT /api/events/{id} — 更新事件

```
Request: (全量更新，字段同创建)
{
  "title": "项目讨论会（改期）",
  "description": "...",
  "startTime": "2026-06-01T14:00:00",
  ... 
}
```

#### DELETE /api/events/{id} — 删除事件

```
Response:
{
  "code": 200,
  "message": "已删除"
}
```

#### PATCH /api/events/{id}/status — 切换完成状态

```
Request:
{
  "status": 1    // 0=未完成, 1=已完成
}

Response:
{
  "code": 200,
  "message": "已标记为已完成",
  "data": { "status": 1 }
}
```

### 4.4 标签管理

#### GET /api/tags — 获取用户标签列表

```
Response:
{
  "code": 200,
  "data": [
    { "id": 1, "name": "工作", "color": "#409eff" },
    { "id": 2, "name": "学习", "color": "#67c23a" }
  ]
}
```

#### POST /api/tags — 创建标签

```
Request:  { "name": "健康", "color": "#e6a23c" }
Response: { "code": 200, "message": "创建成功", "data": { "id": 3, "name": "健康", "color": "#e6a23c" } }
```

#### DELETE /api/tags/{id} — 删除标签（自动解除关联）

### 4.5 语音/文本对话

#### POST /api/chat — 发送文本消息

```
Request:
{
  "conversationId": 1,    // 可选，不传则创建新会话
  "content": "明天下午3点开会讨论项目进度"
}

Response:
{
  "code": 200,
  "data": {
    "conversationId": 1,
    "message": {
      "id": 10,
      "role": "assistant",
      "content": "好的，已为你创建事件：明天（5月31日）下午3:00「开会讨论项目进度」。需要设置提醒吗？",
      "intent": null,
      "audioUrl": "https://.../response-10.mp3",
      "createdAt": "2026-05-30T10:00:00"
    },
    "action": {                       // 当意图为操作类时返回
      "intent": "CREATE_EVENT",
      "event": {
        "id": 14,
        "title": "开会讨论项目进度",
        "startTime": "2026-05-31T15:00:00",
        "endTime": "2026-05-31T16:00:00",
        ...
      }
    }
  }
}
```

#### POST /api/chat/voice — 上传语音消息

```
Content-Type: multipart/form-data

Fields:
  audio        File    音频文件 (webm/wav/mp3)
  conversationId  Long    会话ID (可选)

Response: 与 POST /api/chat 结构相同，额外返回:
{
  "data": {
    "transcription": "明天下午3点开会讨论项目进度",   // ASR识别原文
    ...
  }
}
```

#### GET /api/conversations — 获取对话历史列表

```
Query Params: page, size

Response:
{
  "code": 200,
  "data": {
    "total": 5,
    "records": [
      { "id": 1, "title": "创建事件: 开会讨论项目进度", "updatedAt": "2026-05-30T10:00:00" }
    ]
  }
}
```

#### GET /api/conversations/{id}/messages — 获取对话消息记录

```
Response:
{
  "code": 200,
  "data": [
    { "id": 9,  "role": "user",      "content": "明天下午3点开会", "createdAt": "..." },
    { "id": 10, "role": "assistant", "content": "好的，已为你创建...", "audioUrl": "...", "createdAt": "..." }
  ]
}
```

### 4.6 附件上传

#### POST /api/events/{id}/attachments — 上传附件

```
Content-Type: multipart/form-data
Field: file (图片/文件)

Response:
{
  "code": 200,
  "data": {
    "id": 1,
    "fileUrl": "https://.../uploads/xxx.png",
    "fileType": "image",
    "fileName": "photo.png"
  }
}
```

#### DELETE /api/events/{eventId}/attachments/{id} — 删除附件

---

## 5. 语音交互技术方案

### 5.1 整体架构

```
┌─────────┐     ┌──────────────────────────────────────┐
│  前端    │     │              后端                    │
│         │     │                                      │
│ 录音 ───┼────→│ POST /api/chat/voice                 │
│ (Web    │     │   │                                  │
│  Audio  │     │   ├─→ 七牛ASR / 语音识别服务          │
│  API)   │     │   │                                  │
│         │     │   ├─→ LLM 意图解析 + 实体提取         │
│ 播放 ←──┼─────│   │                                  │
│ (Audio) │     │   ├─→ 业务层 (CRUD Events)            │
│         │     │   │                                  │
│ 文本 ───┼────→│   ├─→ 七牛TTS / 语音合成              │
│         │     │   │                                  │
│         │     │   └─→ 落库 (messages) + 返回响应      │
└─────────┘     └──────────────────────────────────────┘
```

### 5.2 意图解析 Prompt 设计（LLM）

使用 LLM 进行意图识别和实体提取，核心 Prompt 如下：

```
你是一个日历助手的意图识别器。根据用户的输入，识别意图并提取相关实体。

意图类型：
- CREATE_EVENT: 创建新事件
- QUERY_EVENT: 查询事件
- UPDATE_EVENT: 修改事件  
- DELETE_EVENT: 删除事件
- COMPLETE_EVENT: 标记完成
- GENERAL_CHAT: 闲聊

输出JSON格式：
{
  "intent": "意图类型",
  "entities": {
    "title": "事件标题",
    "startTime": "2026-05-31T15:00:00",
    "endTime": "2026-05-31T16:00:00",
    "description": "描述内容",
    "location": "地点",
    "participants": ["参与人"],
    "tags": ["标签"],
    "reminderBefore": 15,
    "targetEventKeyword": "要修改/删除的事件关键词"
  },
  "confidence": 0.95
}

用户输入：{user_message}

注意：
1. 时间表达（"明天下午3点"、"下周三"）需转换为当前日期基准的ISO格式
2. 如果信息不完整，对应字段留null，系统会引导用户补充
3. 当前日期：{current_date}
```

### 5.3 多轮对话状态机

```
┌──────────┐    信息不完整    ┌──────────────┐
│  空闲     │ ──────────────→ │ 等待补充信息  │
│  IDLE    │ ←────────────── │ PENDING      │
└──────────┘   补充完成/超时   └──────────────┘
     │                              │
     │ 信息完整                      │ 补充后完整
     ↓                              ↓
┌──────────┐                  ┌──────────┐
│ 执行业务  │ ←─────────────── │ 确认操作  │
│ EXECUTE  │                  │ CONFIRM  │
└──────────┘                  └──────────┘
```

会话上下文通过 `conversation_id` 关联，后端维护当前待处理的 `intent + partial_entities`，直到信息完整再执行业务操作。

### 5.4 语音服务建议

| 服务 | 推荐方案 | 备选方案 |
|------|----------|----------|
| ASR（语音识别） | 七牛云语音识别 | 百度AI / 讯飞 / OpenAI Whisper |
| TTS（语音合成） | 七牛云语音合成 | 百度AI / 讯飞 / OpenAI TTS |
| NLP（意图理解） | DeepSeek / 通义千问 API | 规则匹配 + 关键词提取 |

---

## 6. 开发阶段规划

### 第一阶段：基础后端 + 文本交互（MVP）

- [ ] 数据库建表 + MyBatis-Plus 集成
- [ ] 用户注册/登录（JWT）
- [ ] 事件 CRUD 接口
- [ ] 标签管理接口
- [ ] 文本消息对话接口（LLM 意图解析 + 业务执行）
- [ ] 前端接入真实 API，替换 mock 数据

### 第二阶段：语音交互

- [ ] 前端录音 + 上传（Web Audio API → webm）
- [ ] 后端接入七牛 ASR（语音转文字）
- [ ] 后端接入七牛 TTS（文字转语音）
- [ ] 前端播放 TTS 音频
- [ ] 多轮对话上下文管理

### 第三阶段：增强体验

- [ ] 定时提醒推送（轮询 / WebSocket / 邮件）
- [ ] 附件上传（七牛云存储）
- [ ] 事件分享/协作（多人参与）
- [ ] 数据统计面板（完成率、时间分布）
- [ ] 移动端适配 / PWA

---

## 7. 当前项目状态与对接指引

### 7.1 前端已有功能
- 完整的日历 UI（日程/本周/本月三视图）
- 事件详情编辑弹窗（标题、描述、时间、状态、提醒、标签）
- 对话面板 UI（文本输入、图片上传、语音按钮、建议快捷语）
- 用户侧边栏（个人中心/切换主题/退出登录入口）
- HTTP 请求模块已封装（`utils/request.ts`），默认 baseURL 为 `/api`

### 7.2 前端待对接
- 移除 `AgendaPanel.vue` 中的 mock 数据（`tasks` ref），替换为 API 调用
- 实现登录/注册页面和路由守卫
- ChatPanel 接入对话接口（录音上传 + 文本发送 + 回复展示）
- localStorage Token 管理

### 7.3 后端起点
- Spring Boot 4.0.6 + Java 21 + Maven
- MySQL 驱动已引入，需在 `application.yaml` 配置数据源
- 需添加依赖：`spring-boot-starter-security`(JWT)、`mybatis-plus`、`qiniu-java-sdk`、`spring-boot-starter-validation`

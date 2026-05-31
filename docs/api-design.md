# 七牛语音日历 — 后端接口文档

> 版本：v1.0  
> Base URL：`/api`  
> Content-Type：`application/json;charset=UTF-8`（文件上传使用 `multipart/form-data`）  
> 鉴权方式：Bearer Token（JWT），Header: `Authorization: Bearer <token>`

---

## 1. 通用约定

### 1.1 响应格式

所有接口统一返回以下 JSON 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

**成功时：** `data` 为接口返回的业务数据（可以是对象、数组或 null）。

**失败时：** `data` 为 null，`message` 包含错误描述。校验失败时 `data` 返回字段级错误详情：

```json
{
  "code": 400,
  "message": "参数校验失败",
  "data": {
    "errors": {
      "title": "标题不能为空",
      "startTime": "开始时间格式不正确"
    }
  }
}
```

### 1.2 状态码

| code | 含义 | 触发场景 |
|------|------|----------|
| 200 | 成功 | 请求正常处理 |
| 400 | 请求参数错误 | 必填字段缺失、格式错误、业务规则校验不通过 |
| 401 | 未认证 | Token 缺失、过期、无效 |
| 403 | 无权限 | 尝试操作其他用户的数据 |
| 404 | 资源不存在 | 事件/标签/会话 ID 不存在 |
| 409 | 资源冲突 | 标签名重复、时间冲突 |
| 500 | 服务器错误 | 未知异常 |

### 1.3 分页约定

需要分页的接口统一使用以下参数和响应格式：

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码（从 1 开始） |
| size | int | 20 | 每页条数（最大 100） |

**响应格式：**

```json
{
  "code": 200,
  "data": {
    "total": 50,
    "page": 1,
    "size": 20,
    "pages": 3,
    "records": []
  }
}
```

### 1.4 时间格式

所有日期时间字段使用 **ISO 8601 格式**：`yyyy-MM-ddTHH:mm:ss`，如 `2026-05-30T14:30:00`。时区统一为服务器本地时区（北京时间 UTC+8），暂不处理跨时区场景。

### 1.5 鉴权说明

- 注册和登录接口无需 Token
- 其余所有接口必须在 Header 中携带 `Authorization: Bearer <token>`
- JWT Token 有效期建议：access token 2 小时，refresh token 7 天（refresh token 在第一阶段 MVP 可暂不实现，先使用较长有效期的单 token）
- Token 中携带 `userId`，后端通过解析 Token 确定当前请求用户，所有数据操作限定在该用户范围内

---

## 2. 认证模块 `POST/GET /api/auth`

### 2.1 注册 — `POST /api/auth/register`

```
Request:
{
  "username": "zhangsan",            // 必填, 3-50字符, 字母/数字/下划线/中文
  "password": "123456",              // 必填, 6-100字符
  "email": "zhangsan@example.com"    // 可选
}

Response 200:
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "userId": 1,
    "username": "zhangsan",
    "token": "eyJhbGciOiJIUzI1NiIs..."
  }
}

Response 400 (用户名已存在):
{ "code": 400, "message": "用户名已被注册", "data": null }
```

### 2.2 登录 — `POST /api/auth/login`

```
Request:
{
  "username": "zhangsan",   // 必填
  "password": "123456"      // 必填
}

Response 200:
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "userId": 1,
    "username": "zhangsan",
    "token": "eyJhbGciOiJIUzI1NiIs..."
  }
}

Response 401:
{ "code": 401, "message": "用户名或密码错误", "data": null }
```

### 2.3 获取个人信息 — `GET /api/auth/profile`

```
Response 200:
{
  "code": 200,
  "data": {
    "id": 1,
    "username": "zhangsan",
    "email": "zhangsan@example.com",
    "avatarUrl": "https://cdn.example.com/avatars/1.png",
    "createdAt": "2026-05-01T10:00:00"
  }
}
```

### 2.4 修改个人信息 — `PUT /api/auth/profile`

```
Request:
{
  "email": "newemail@example.com",     // 可选
  "avatarUrl": "https://..."           // 可选
}
// 至少传一个字段

Response 200:
{ "code": 200, "message": "修改成功", "data": null }
```

---

## 3. 事件模块 `GET/POST/PUT/PATCH/DELETE /api/events`

### 3.1 查询事件列表 — `GET /api/events`

```
Query Params:
  startDate   string   开始日期 yyyy-MM-dd (可选，不传则不限制)
  endDate     string   结束日期 yyyy-MM-dd (可选，不传则不限制)
  status      int      0=未完成, 1=已完成 (可选，不传则返回全部)
  tag         string   按标签名筛选 (可选)
  keyword     string   标题和描述模糊搜索 (可选)
  page        int      页码 (默认1)
  size        int      每页条数 (默认20, 最大100)

Response 200:
{
  "code": 200,
  "data": {
    "total": 12,
    "page": 1,
    "size": 20,
    "pages": 1,
    "records": [
      {
        "id": 1,
        "title": "团队站会",
        "description": "每日站会，同步进度和阻塞项",
        "startTime": "2026-05-30T09:30:00",
        "endTime": "2026-05-30T09:45:00",
        "duration": "15m",
        "location": "会议室A",
        "status": 0,
        "participants": ["张三", "李四"],
        "tags": ["工作"],
        "reminderBefore": 5,
        "createdAt": "2026-05-28T08:00:00",
        "updatedAt": "2026-05-28T08:00:00"
      }
    ]
  }
}
```

> **查询逻辑说明：**
> - `startDate` + `endDate`：筛选 `start_time` 在日期范围内的所有事件（跨天事件包含在内）
> - `status`：不传时返回所有状态的事件，传值后精确筛选
> - `tag`：通过 `event_tags` 关联表 JOIN 查询，精确匹配标签名
> - `keyword`：对 `title` 和 `description` 做 LIKE 模糊匹配
> - 所有筛选条件可以组合使用（AND 逻辑）
> - 结果按 `start_time DESC` 排序

### 3.2 获取事件详情 — `GET /api/events/{id}`

```
Response 200:
{
  "code": 200,
  "data": {
    "id": 1,
    "title": "团队站会",
    "description": "每日站会，同步进度和阻塞项",
    "startTime": "2026-05-30T09:30:00",
    "endTime": "2026-05-30T09:45:00",
    "duration": "15m",
    "location": "会议室A",
    "status": 0,
    "participants": ["张三", "李四"],
    "tags": ["工作"],
    "reminderBefore": 5,
    "attachments": [
      { "id": 1, "fileUrl": "https://...", "fileType": "image", "fileName": "agenda.png" }
    ],
    "createdAt": "2026-05-28T08:00:00",
    "updatedAt": "2026-05-28T08:00:00"
  }
}

Response 403:
{ "code": 403, "message": "无权访问该事件", "data": null }

Response 404:
{ "code": 404, "message": "事件不存在", "data": null }
```

### 3.3 创建事件 — `POST /api/events`

```
Request:
{
  "title": "项目讨论会",                    // 必填, 1-500字符
  "description": "讨论新功能设计方案",       // 可选
  "startTime": "2026-05-31T14:00:00",     // 必填
  "endTime": "2026-05-31T15:30:00",       // 必填, 必须 > startTime
  "duration": "1h 30m",                   // 可选, 不传则后端根据时间差自动计算
  "location": "3楼会议室",                  // 可选
  "participants": ["张三", "李四"],          // 可选
  "tags": ["工作", "会议"],                  // 可选, 不存在的标签自动创建
  "reminderBefore": 15                    // 可选, 枚举值: 5/10/15/30/60
}

Response 200:
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 14,
    "title": "项目讨论会",
    "startTime": "2026-05-31T14:00:00",
    "endTime": "2026-05-31T15:30:00",
    "duration": "1h 30m",
    "status": 0,
    "tags": ["工作", "会议"],
    "reminderBefore": 15,
    "createdAt": "2026-05-30T10:00:00"
  }
}

Response 400:
{ "code": 400, "message": "结束时间必须大于开始时间", "data": null }
```

> **标签处理逻辑：** 若传入的标签名在用户标签库中已存在则直接关联，不存在则自动创建新标签并关联。

### 3.4 更新事件（全量） — `PUT /api/events/{id}`

全量替换事件的所有字段。未传入的可选字段将被置为 null/默认值。

```
Request: (字段同 POST /api/events)
{
  "title": "项目讨论会（改期）",
  "startTime": "2026-06-01T14:00:00",
  "endTime": "2026-06-01T15:30:00",
  ...
}

Response 200:
{ "code": 200, "message": "修改成功", "data": { ...更新后的事件对象 } }
```

### 3.5 部分更新事件 — `PATCH /api/events/{id}`

仅更新传入的字段，未传字段保持不变。适用于语音指令场景（"把面试改到后天" 只改时间，不改其他字段）。

```
Request: (只传需要更新的字段)
{
  "startTime": "2026-06-02T10:00:00",
  "endTime": "2026-06-02T11:00:00"
}

Response 200:
{ "code": 200, "message": "修改成功", "data": { ...更新后的事件对象 } }
```

> **注意：** PUT 和 PATCH 都需校验 `endTime > startTime`（如果传入时间字段）。变更 `reminderBefore` 或 `startTime` 时需重新生成提醒记录。

### 3.6 删除事件 — `DELETE /api/events/{id}`

```
Response 200:
{ "code": 200, "message": "已删除", "data": null }

Response 404:
{ "code": 404, "message": "事件不存在", "data": null }
```

> 级联删除：事件关联的标签关系、提醒记录、附件同步删除。

### 3.7 切换完成状态 — `PATCH /api/events/{id}/status`

```
Request:
{
  "status": 1    // 0=标记未完成, 1=标记已完成
}

Response 200:
{
  "code": 200,
  "message": "已标记为已完成",
  "data": { "id": 1, "status": 1 }
}
```

---

## 4. 标签模块 `GET/POST/DELETE /api/tags`

### 4.1 获取标签列表 — `GET /api/tags`

```
Response 200:
{
  "code": 200,
  "data": [
    { "id": 1, "name": "工作",   "color": "#409eff", "eventCount": 5 },
    { "id": 2, "name": "学习",   "color": "#67c23a", "eventCount": 2 },
    { "id": 3, "name": "健康",   "color": "#e6a23c", "eventCount": 1 }
  ]
}
```

> `eventCount` 为该标签关联的未删除事件数量，便于前端展示标签使用频率。

### 4.2 创建标签 — `POST /api/tags`

```
Request:
{
  "name": "健康",        // 必填, 1-50字符
  "color": "#e6a23c"    // 可选, 默认 #909399
}

Response 200:
{
  "code": 200,
  "message": "创建成功",
  "data": { "id": 3, "name": "健康", "color": "#e6a23c" }
}

Response 409:
{ "code": 409, "message": "标签名已存在", "data": null }
```

### 4.3 修改标签 — `PUT /api/tags/{id}`

```
Request:
{
  "name": "运动健康",    // 可选
  "color": "#f56c6c"    // 可选
}

Response 200:
{ "code": 200, "message": "修改成功", "data": { ...更新后的标签对象 } }
```

### 4.4 删除标签 — `DELETE /api/tags/{id}`

```
Response 200:
{ "code": 200, "message": "已删除", "data": null }
```

> 级联删除 `event_tags` 中的关联记录，但不会删除事件本身。

---

## 5. 对话模块 `POST/GET /api/chat, /api/conversations`

### 5.1 发送文本消息 — `POST /api/chat`

语音日历的核心接口。接收用户文本输入，经过 LLM 意图解析 → 业务执行 → 生成回复，返回结果。

```
Request:
{
  "conversationId": 1,                                              // 可选, 不传则自动创建新会话
  "content": "明天下午3点在3楼会议室开会讨论项目进度，提醒我提前15分钟"    // 必填
}

Response 200 (CREATE_EVENT):
{
  "code": 200,
  "data": {
    "conversationId": 1,
    "message": {
      "id": 12,
      "role": "assistant",
      "content": "好的，已为你创建事件：明天（5月31日）15:00-16:00「开会讨论项目进度」，地点 3楼会议室，已设置提前15分钟提醒。",
      "audioUrl": "https://cdn.example.com/tts/response-12.mp3",
      "createdAt": "2026-05-30T10:00:05"
    },
    "action": {
      "intent": "CREATE_EVENT",
      "event": {
        "id": 14,
        "title": "开会讨论项目进度",
        "startTime": "2026-05-31T15:00:00",
        "endTime": "2026-05-31T16:00:00",
        "location": "3楼会议室",
        "reminderBefore": 15,
        "tags": []
      }
    }
  }
}

Response 200 (QUERY_EVENT):
{
  "code": 200,
  "data": {
    "conversationId": 1,
    "message": {
      "id": 15,
      "role": "assistant",
      "content": "明天（5月31日）你有2个安排：15:00-16:00「开会讨论项目进度」在3楼会议室；17:00-18:00「健身」记得带运动服。",
      "audioUrl": null,
      "createdAt": "2026-05-30T18:00:00"
    },
    "action": {
      "intent": "QUERY_EVENT",
      "events": [
        { "id": 14, "title": "开会讨论项目进度", "startTime": "2026-05-31T15:00:00", "endTime": "2026-05-31T16:00:00" },
        { "id": 15, "title": "健身", "startTime": "2026-05-31T17:00:00", "endTime": "2026-05-31T18:00:00" }
      ]
    }
  }
}

Response 200 (UPDATE_EVENT):
{
  "code": 200,
  "data": {
    "conversationId": 1,
    "message": {
      "id": 16,
      "role": "assistant",
      "content": "已将「开会讨论项目进度」的时间改为 6月1日 15:00-16:00。",
      "audioUrl": null,
      "createdAt": "2026-05-30T10:01:00"
    },
    "action": {
      "intent": "UPDATE_EVENT",
      "event": { "id": 14, "title": "开会讨论项目进度", "startTime": "2026-06-01T15:00:00", "endTime": "2026-06-01T16:00:00" }
    }
  }
}

Response 200 (DELETE_EVENT):
{
  "code": 200,
  "data": {
    "conversationId": 1,
    "message": {
      "id": 17,
      "role": "assistant",
      "content": "已删除事件「周报整理」。",
      "audioUrl": null,
      "createdAt": "2026-05-30T10:02:00"
    },
    "action": {
      "intent": "DELETE_EVENT",
      "eventId": 7,
      "eventTitle": "周报整理"
    }
  }
}

Response 200 (COMPLETE_EVENT):
{
  "code": 200,
  "data": {
    "conversationId": 1,
    "message": {
      "id": 18,
      "role": "assistant",
      "content": "已将「健身」标记为已完成 ✅，今天的运动打卡完成！",
      "audioUrl": null,
      "createdAt": "2026-05-30T10:03:00"
    },
    "action": {
      "intent": "COMPLETE_EVENT",
      "event": { "id": 8, "title": "健身", "status": 1 }
    }
  }
}

Response 200 (GENERAL_CHAT — 无 action):
{
  "code": 200,
  "data": {
    "conversationId": 1,
    "message": {
      "id": 19,
      "role": "assistant",
      "content": "你好！我是七牛语音日历助手，可以帮你管理日程。比如你可以说「明天下午3点开会」来创建事件，或者问「今天有什么安排」来查看日程。",
      "audioUrl": null,
      "createdAt": "2026-05-30T10:04:00"
    },
    "action": null
  }
}

Response 200 (PENDING — 信息不完整，反问补充):
{
  "code": 200,
  "data": {
    "conversationId": 1,
    "message": {
      "id": 20,
      "role": "assistant",
      "content": "好的，你想添加一个会议。请问会议的具体时间是？",
      "audioUrl": null,
      "createdAt": "2026-05-30T10:05:00"
    },
    "action": null
  }
}
```

> **`action` 字段规则：**
> - 当意图为 `CREATE_EVENT / UPDATE_EVENT / COMPLETE_EVENT` 时，`action.event` 为操作后的事件对象
> - 当意图为 `QUERY_EVENT` 时，`action.events` 为查询到的事件列表
> - 当意图为 `DELETE_EVENT` 时，`action.eventId` + `action.eventTitle` 标识被删除的事件
> - 当意图为 `GENERAL_CHAT` 或信息不完整（PENDING）时，`action` 为 null
> - 前端根据 `action` 字段决定是否需要刷新日历视图中的事件数据

### 5.2 发送语音消息 — `POST /api/chat/voice`

```
Content-Type: multipart/form-data

Fields:
  audio            File    音频文件 (支持 webm / wav / mp3, 最大 10MB)
  conversationId   Long    会话ID (可选)

Response 200:
{
  "code": 200,
  "data": {
    "transcription": "明天下午3点开会讨论项目进度",     // ASR 识别原文
    "conversationId": 1,
    "message": { ... },   // 同 POST /api/chat 的 message 结构
    "action": { ... }     // 同 POST /api/chat 的 action 结构
  }
}

Response 400 (音频格式不支持):
{ "code": 400, "message": "不支持的音频格式，请使用 webm/wav/mp3", "data": null }

Response 400 (ASR 识别失败):
{
  "code": 400,
  "message": "语音识别失败，请重试或使用文字输入",
  "data": { "transcription": null }
}
```

> **后端处理流程：**
> 1. 接收音频文件 → 临时存储
> 2. 调用七牛 ASR 服务转文字
> 3. 将文字和 `conversationId` 传给与 `/api/chat` 相同意图解析逻辑
> 4. 用户消息落库时保存 `audio_url`（用户原始录音）和 `content`（转写文本）
> 5. 若启用了 TTS，调用七牛 TTS 合成回复语音，助手消息的 `audioUrl` 指向合成音频

### 5.3 获取对话列表 — `GET /api/conversations`

```
Query Params: page, size

Response 200:
{
  "code": 200,
  "data": {
    "total": 5,
    "page": 1,
    "size": 20,
    "pages": 1,
    "records": [
      {
        "id": 1,
        "title": "创建事件: 开会讨论项目进度",
        "lastMessage": "好的，已为你创建事件...",
        "updatedAt": "2026-05-30T10:00:05"
      }
    ]
  }
}
```

> 按 `updated_at DESC` 排序。`title` 由后端根据会话最后一条有意图的消息自动生成（如 "创建事件: xxx"）。

### 5.4 获取对话消息 — `GET /api/conversations/{id}/messages`

```
Query Params: page, size (消息量大时分页)

Response 200:
{
  "code": 200,
  "data": {
    "total": 4,
    "records": [
      {
        "id": 11,
        "role": "user",
        "content": "明天下午3点开会",
        "intent": "CREATE_EVENT",
        "audioUrl": "https://cdn.example.com/audio/user-11.webm",
        "createdAt": "2026-05-30T10:00:00"
      },
      {
        "id": 12,
        "role": "assistant",
        "content": "好的，已为你创建事件...",
        "audioUrl": "https://cdn.example.com/tts/response-12.mp3",
        "createdAt": "2026-05-30T10:00:05"
      }
    ]
  }
}
```

### 5.5 删除对话 — `DELETE /api/conversations/{id}`

```
Response 200:
{ "code": 200, "message": "已删除", "data": null }
```

> 级联删除该对话下的所有消息。

---

## 6. 附件模块 `POST/DELETE /api/events/{id}/attachments`

### 6.1 上传附件 — `POST /api/events/{eventId}/attachments`

```
Content-Type: multipart/form-data
Field: file (图片支持 jpg/png/gif/webp, 文件支持 pdf/doc/xlsx, 最大 20MB)

Response 200:
{
  "code": 200,
  "data": {
    "id": 1,
    "fileUrl": "https://cdn.example.com/uploads/abc123.png",
    "fileType": "image",
    "fileName": "meeting-notes.png",
    "createdAt": "2026-05-30T10:30:00"
  }
}
```

> 文件上传至七牛云对象存储，`fileUrl` 返回 CDN 加速域名。前端通过 `fileType` 决定渲染图片预览或文件下载链接。

### 6.2 删除附件 — `DELETE /api/events/{eventId}/attachments/{id}`

```
Response 200:
{ "code": 200, "message": "已删除", "data": null }
```

> 同时删除七牛云存储中的文件对象（或标记为待清理，避免误删）。

---

## 7. 接口索引

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/auth/register` | 用户注册 | 否 |
| POST | `/api/auth/login` | 用户登录 | 否 |
| GET | `/api/auth/profile` | 获取个人信息 | 是 |
| PUT | `/api/auth/profile` | 修改个人信息 | 是 |
| GET | `/api/events` | 查询事件列表 | 是 |
| POST | `/api/events` | 创建事件 | 是 |
| GET | `/api/events/{id}` | 获取事件详情 | 是 |
| PUT | `/api/events/{id}` | 全量更新事件 | 是 |
| PATCH | `/api/events/{id}` | 部分更新事件 | 是 |
| DELETE | `/api/events/{id}` | 删除事件 | 是 |
| PATCH | `/api/events/{id}/status` | 切换完成状态 | 是 |
| GET | `/api/tags` | 获取标签列表 | 是 |
| POST | `/api/tags` | 创建标签 | 是 |
| PUT | `/api/tags/{id}` | 修改标签 | 是 |
| DELETE | `/api/tags/{id}` | 删除标签 | 是 |
| POST | `/api/chat` | 发送文本消息 | 是 |
| POST | `/api/chat/voice` | 发送语音消息 | 是 |
| GET | `/api/conversations` | 对话列表 | 是 |
| GET | `/api/conversations/{id}/messages` | 对话消息记录 | 是 |
| DELETE | `/api/conversations/{id}` | 删除对话 | 是 |
| POST | `/api/events/{eventId}/attachments` | 上传附件 | 是 |
| DELETE | `/api/events/{eventId}/attachments/{id}` | 删除附件 | 是 |

---

## 8. 与原开发文档的差异说明

本接口文档在原始设计文档基础上做了以下调整和补充：

| 调整项 | 原设计 | 本设计 | 原因 |
|--------|--------|--------|------|
| 事件更新方式 | 仅 PUT 全量更新 | 同时保留 PUT + PATCH | 语音场景天然适合部分更新（"把时间改到..."） |
| 标签修改接口 | 无 | 新增 `PUT /api/tags/{id}` | 用户可能需要重命名标签或更换颜色 |
| 个人信息修改 | 无 | 新增 `PUT /api/auth/profile` | 原设计遗漏 |
| 删除对话 | 无 | 新增 `DELETE /api/conversations/{id}` | 用户需要清理不需要的对话记录 |
| 标签列表响应 | 仅 id/name/color | 增加 `eventCount` | 前端可展示标签使用频率，优化 UI |
| 错误响应详情 | 未定义 | 增加 400 时 `data.errors` | 前端可精确展示字段级校验错误 |
| 状态码 | 仅 200/400/401/404/500 | 增加 403/409 | 区分"无权访问"和"资源冲突" |
| 分页响应 | 无 `pages` 字段 | 增加 `pages` 总页数 | 前端分页组件通常需要此字段 |
| chat 响应 action | 仅示例 CREATE_EVENT | 详细列出 6 种意图的 action 结构 | 消除歧义，前端可按意图类型判断是否需要刷新日历 |
| PENDING 状态 | 仅在状态机章节提及 | 在接口响应中显式定义 | 明确"信息不完整时反问"这个关键交互 |

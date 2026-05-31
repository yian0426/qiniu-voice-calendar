# 七牛语音日历 — 数据库表设计文档

> 版本：v1.0  
> 数据库：MySQL 8.0+  
> 字符集：utf8mb4  
> 排序规则：utf8mb4_unicode_ci  
> ORM：MyBatis-Plus（推荐）

---

## 1. ER 关系图

```
users ──1:N── events ──1:N── event_tags ──N:1── tags
  │               │
  │               ├──1:N── reminders
  │               └──1:N── attachments
  │
  └──1:N── conversations ──1:N── messages
```

---

## 2. 建表 DDL

### 2.1 users — 用户表

存储注册用户的基本信息。密码使用 BCrypt 哈希存储，不保存明文。

```sql
CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    username    VARCHAR(50)  NOT NULL                 COMMENT '用户名',
    password    VARCHAR(255) NOT NULL                 COMMENT '密码哈希(BCrypt)',
    email       VARCHAR(100) DEFAULT NULL             COMMENT '邮箱',
    avatar_url  VARCHAR(500) DEFAULT NULL             COMMENT '头像URL',
    created_at  DATETIME     NOT NULL DEFAULT NOW()   COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE  KEY uk_username (username),
    UNIQUE  KEY uk_email    (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
```

### 2.2 events — 事件表

日历的核心表，存储用户创建的每一个事件/待办。与标签通过 `event_tags` 多对多关联。

```sql
CREATE TABLE events (
    id              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    user_id         BIGINT       NOT NULL                 COMMENT '所属用户ID',
    title           VARCHAR(500) NOT NULL                 COMMENT '事件标题',
    description     TEXT         DEFAULT NULL             COMMENT '事件描述',
    start_time      DATETIME     NOT NULL                 COMMENT '开始时间',
    end_time        DATETIME     NOT NULL                 COMMENT '结束时间',
    duration        VARCHAR(20)  DEFAULT NULL             COMMENT '时长展示(如"1h","30m")',
    location        VARCHAR(500) DEFAULT NULL             COMMENT '地点',
    status          TINYINT      NOT NULL DEFAULT 0       COMMENT '状态: 0=未完成, 1=已完成',
    participants    JSON         DEFAULT NULL             COMMENT '参与人列表["张三","李四"]',
    reminder_before INT          DEFAULT NULL             COMMENT '提前提醒分钟数(5/10/15/30/60)',
    created_at      DATETIME     NOT NULL DEFAULT NOW()   COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',

    PRIMARY KEY (id),
    INDEX idx_user_start  (user_id, start_time),
    INDEX idx_user_status (user_id, status),
    CONSTRAINT fk_events_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件表';
```

> **设计说明：**
> - `duration` 是冗余字段，由 `end_time - start_time` 计算得出，仅用于前端快速展示，避免每次渲染都做时间差计算。后端在创建/更新事件时应自动计算并写入。
> - `participants` 使用 JSON 类型存储，当前阶段参与人仅作为文本标签使用，不关联用户账号。若未来需要多人协作（分配任务、共享事件），应拆为独立的 `event_participants` 关联表。
> - `reminder_before` 为 NULL 表示不设置提醒，非 NULL 时表示提前 X 分钟提醒。

### 2.3 tags — 标签表

每个用户拥有独立的标签体系，标签名在用户级别唯一。

```sql
CREATE TABLE tags (
    id         BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    user_id    BIGINT      NOT NULL                 COMMENT '所属用户ID',
    name       VARCHAR(50) NOT NULL                 COMMENT '标签名称',
    color      VARCHAR(20) DEFAULT '#909399'        COMMENT '标签颜色(hex)',
    created_at DATETIME    NOT NULL DEFAULT NOW()   COMMENT '创建时间',

    PRIMARY KEY (id),
    UNIQUE  KEY uk_user_tag (user_id, name),
    CONSTRAINT fk_tags_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';
```

### 2.4 event_tags — 事件-标签关联表

标准的多对多中间表，使用联合唯一索引防止重复关联。

```sql
CREATE TABLE event_tags (
    id       BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_id BIGINT NOT NULL                COMMENT '事件ID',
    tag_id   BIGINT NOT NULL                COMMENT '标签ID',

    PRIMARY KEY (id),
    UNIQUE  KEY uk_event_tag (event_id, tag_id),
    CONSTRAINT fk_event_tags_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_tags_tag   FOREIGN KEY (tag_id)   REFERENCES tags(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件标签关联表';
```

> **级联删除策略：**
> - 删除事件时自动删除其所有标签关联（`ON DELETE CASCADE`）
> - 删除标签时自动解除所有事件的该标签关联（`ON DELETE CASCADE`）

### 2.5 reminders — 提醒记录表

存储每个事件的实际提醒时间点。当事件被修改（时间或 `reminder_before` 变更）时，需重新计算并更新提醒记录。

```sql
CREATE TABLE reminders (
    id         BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    event_id   BIGINT   NOT NULL                 COMMENT '关联事件ID',
    remind_at  DATETIME NOT NULL                 COMMENT '提醒触发时间',
    sent       TINYINT  NOT NULL DEFAULT 0       COMMENT '0=未发送, 1=已发送',
    sent_at    DATETIME DEFAULT NULL             COMMENT '实际发送时间',
    created_at DATETIME NOT NULL DEFAULT NOW()   COMMENT '创建时间',

    PRIMARY KEY (id),
    INDEX idx_remind_pending (remind_at, sent),
    CONSTRAINT fk_reminders_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒记录表';
```

> **提醒生成规则：**
> - 事件创建/更新时，若 `reminder_before` 不为 NULL，则生成一条提醒记录：`remind_at = start_time - INTERVAL reminder_before MINUTE`
> - 事件被删除时，提醒记录级联删除
> - 后台定时任务每分钟扫描 `idx_remind_pending` 索引，查询 `remind_at <= NOW() AND sent = 0` 的记录进行推送

### 2.6 conversations — 对话会话表

每次用户开始一个新的对话会话（新建聊天窗），创建一条记录。

```sql
CREATE TABLE conversations (
    id         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    user_id    BIGINT       NOT NULL                 COMMENT '所属用户ID',
    title      VARCHAR(200) DEFAULT '新对话'          COMMENT '会话标题',
    created_at DATETIME     NOT NULL DEFAULT NOW()   COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',

    PRIMARY KEY (id),
    INDEX idx_conv_user_updated (user_id, updated_at DESC),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';
```

### 2.7 messages — 消息表

记录对话中的每一条消息。用户的语音消息和助手的 TTS 回复都存储 `audio_url`。

```sql
CREATE TABLE messages (
    id              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    conversation_id BIGINT       NOT NULL                 COMMENT '所属会话ID',
    role            VARCHAR(10)  NOT NULL                 COMMENT '消息角色: user / assistant',
    content         TEXT         NOT NULL                 COMMENT '消息文本内容',
    intent          VARCHAR(30)  DEFAULT NULL             COMMENT '意图类型(仅user消息)',
    audio_url       VARCHAR(500) DEFAULT NULL             COMMENT '关联音频文件URL',
    metadata        JSON         DEFAULT NULL             COMMENT '扩展数据(实体参数等)',
    created_at      DATETIME     NOT NULL DEFAULT NOW()   COMMENT '创建时间',

    PRIMARY KEY (id),
    INDEX idx_msg_conv (conversation_id, created_at),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';
```

> **字段说明：**
> - `role`：`user` 表示用户消息，`assistant` 表示助手回复
> - `intent`：仅 `role='user'` 时有效，值为 `CREATE_EVENT / QUERY_EVENT / UPDATE_EVENT / DELETE_EVENT / COMPLETE_EVENT / GENERAL_CHAT`
> - `audio_url`：用户消息时为用户录音文件 URL，助手消息时为 TTS 合成音频 URL
> - `metadata`：JSON 扩展字段，存储意图解析的完整实体参数，便于调试和上下文恢复

### 2.8 attachments — 附件表

存储事件关联的图片、文件等附件。

```sql
CREATE TABLE attachments (
    id         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    event_id   BIGINT       NOT NULL                 COMMENT '关联事件ID',
    file_url   VARCHAR(500) NOT NULL                 COMMENT '文件URL(七牛云存储)',
    file_type  VARCHAR(20)  NOT NULL                 COMMENT '文件类型: image / file',
    file_name  VARCHAR(200) DEFAULT NULL             COMMENT '原始文件名',
    created_at DATETIME     NOT NULL DEFAULT NOW()   COMMENT '创建时间',

    PRIMARY KEY (id),
    INDEX idx_att_event (event_id),
    CONSTRAINT fk_attachments_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='附件表';
```

---

## 3. 索引设计汇总

| 表 | 索引名 | 类型 | 字段 | 用途 |
|----|--------|------|------|------|
| users | `uk_username` | UNIQUE | username | 登录查找 + 唯一约束 |
| users | `uk_email` | UNIQUE | email | 邮箱唯一约束 |
| events | `idx_user_start` | INDEX | (user_id, start_time) | 按时间范围查询用户事件 |
| events | `idx_user_status` | INDEX | (user_id, status) | 按完成状态筛选 |
| tags | `uk_user_tag` | UNIQUE | (user_id, name) | 用户标签唯一 + 标签列表查询 |
| event_tags | `uk_event_tag` | UNIQUE | (event_id, tag_id) | 防止重复关联 |
| reminders | `idx_remind_pending` | INDEX | (remind_at, sent) | 定时任务扫描待发送提醒 |
| conversations | `idx_conv_user_updated` | INDEX | (user_id, updated_at DESC) | 用户会话列表排序 |
| messages | `idx_msg_conv` | INDEX | (conversation_id, created_at) | 按会话查询消息记录 |
| attachments | `idx_att_event` | INDEX | (event_id) | 按事件查询附件 |

---

## 4. 数据完整性约束

| 约束类型 | 说明 |
|----------|------|
| `NOT NULL` | 核心业务字段（标题、时间、用户ID等）不允许为空 |
| `DEFAULT NULL` | 可选字段（邮箱、头像、描述、地点等）允许为空 |
| `ON DELETE CASCADE` | 子表外键全部级联删除：删除事件 → 自动删除其标签关联、提醒、附件；删除对话 → 自动删除所有消息 |
| JSON 字段 | `participants` 和 `metadata` 使用 MySQL 原生 JSON 类型，支持索引和查询 |

---

## 5. 设计权衡与演进方向

### 5.1 当前阶段简化项

| 简化点 | 说明 | 演进方向 |
|--------|------|----------|
| participants | JSON 存文本列表 | 需求明确后拆为 `event_participants` 表，关联真实用户 |
| 重复事件 | 不支持 | 后续添加 `recurrence_rule` 字段（RRULE 格式），按需展开实例 |
| reminders | 单条提醒 | 后续支持多次提醒（事件前 1h + 10min），扩展为一对多 |
| 软删除 | 直接物理删除 | 如有回收站需求，events 表可加 `deleted_at` 字段实现软删除 |

### 5.2 性能注意事项

- `events` 表按 `user_id` 分区或分表（当单用户事件量 > 10万时考虑）
- `messages` 表增长最快（每次对话产生 2+ 条记录），建议按月归档历史数据
- `reminders` 定时轮询扫描依赖 `idx_remind_pending` 索引，初期数据量下性能无虞
- JSON 字段 `participants` 和 `metadata` 不适合频繁的条件查询，如需要"按参与人搜索事件"，应拆表

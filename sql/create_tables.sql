-- ============================================================
-- 七牛语音日历 — 数据库建表脚本
-- DB: MySQL 8.0+
-- Charset: utf8mb4 / utf8mb4_unicode_ci
-- ============================================================

CREATE DATABASE IF NOT EXISTS voice_calendar
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE voice_calendar;

-- ============================================================
-- 1. users  用户表
-- ============================================================
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

-- ============================================================
-- 2. events  事件表
-- ============================================================
CREATE TABLE events (
    id              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    user_id         BIGINT       NOT NULL                 COMMENT '所属用户ID',
    title           VARCHAR(500) NOT NULL                 COMMENT '事件标题',
    description     TEXT         DEFAULT NULL             COMMENT '事件描述',
    start_time      DATETIME     NOT NULL                 COMMENT '开始时间',
    end_time        DATETIME     NOT NULL                 COMMENT '结束时间',
    duration        VARCHAR(20)  DEFAULT NULL             COMMENT '时长展示(如1h,30m)',
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

-- ============================================================
-- 3. tags  标签表
-- ============================================================
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

-- ============================================================
-- 4. event_tags  事件-标签关联表
-- ============================================================
CREATE TABLE event_tags (
    id       BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_id BIGINT NOT NULL                COMMENT '事件ID',
    tag_id   BIGINT NOT NULL                COMMENT '标签ID',

    PRIMARY KEY (id),
    UNIQUE  KEY uk_event_tag (event_id, tag_id),
    CONSTRAINT fk_event_tags_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_tags_tag   FOREIGN KEY (tag_id)   REFERENCES tags(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件标签关联表';

-- ============================================================
-- 5. reminders  提醒记录表
-- ============================================================
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

-- ============================================================
-- 6. conversations  对话会话表
-- ============================================================
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

-- ============================================================
-- 7. messages  消息表
-- ============================================================
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

-- ============================================================
-- 8. attachments  附件表
-- ============================================================
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

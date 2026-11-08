-- ============================================================
-- AI 客服模块建库脚本（对应 docs/ai 设计方案 §7 + §9.4）
-- 内容：9 张 ai_ 表 + ai_kb_qa 加 embedding 列 + 9 个工具种子
--       + 2 个内置聊天角色 + 菜单/按钮权限记录 + 角色授权
-- 幂等：可重复执行。本地执行后必须同步生产库（项目硬规则）。
-- 说明：P1 的三张表（ai_chat_role_knowledge / ai_kb_dataset /
--       ai_kb_segment）一并建好，避免二次同步生产。
-- ============================================================

-- ---------- 7.1 工具定义表 ----------
CREATE TABLE IF NOT EXISTS ai_tool (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(128) NOT NULL COMMENT '工具名，与代码 @tool 函数名一致',
  description VARCHAR(512)          DEFAULT NULL COMMENT '工具描述（给大模型看）',
  perm_code   VARCHAR(100)          DEFAULT NULL COMMENT '权限码，对应 admin_menu 一条 F 类型记录',
  scope       VARCHAR(16)  NOT NULL DEFAULT 'both' COMMENT 'mall / admin / both',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  sort_order  INT          NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_tool_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 工具定义表';

-- ---------- 7.2 聊天角色表 ----------
CREATE TABLE IF NOT EXISTS ai_chat_role (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  name           VARCHAR(128)  NOT NULL COMMENT '角色名称',
  avatar         VARCHAR(256)           DEFAULT NULL COMMENT '头像',
  description    VARCHAR(256)           DEFAULT NULL,
  category       VARCHAR(32)            DEFAULT NULL COMMENT '分类',
  system_message VARCHAR(1024)          DEFAULT NULL COMMENT '角色设定（system prompt）',
  admin_id       BIGINT                 DEFAULT NULL COMMENT '创建者，NULL=系统内置',
  public_status  TINYINT       NOT NULL DEFAULT 0 COMMENT '0 私有 1 公开',
  status         TINYINT       NOT NULL DEFAULT 1,
  sort           INT           NOT NULL DEFAULT 0,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 聊天角色表';

-- ---------- 7.3 角色-工具绑定表 ----------
CREATE TABLE IF NOT EXISTS ai_chat_role_tool (
  id      BIGINT NOT NULL AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  tool_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_tool (role_id, tool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 角色-工具绑定表';

-- ---------- 7.4 角色-知识库绑定表（P1 用，先建表） ----------
CREATE TABLE IF NOT EXISTS ai_chat_role_knowledge (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  role_id    BIGINT NOT NULL,
  dataset_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_dataset (role_id, dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 角色-知识库绑定表';

-- ---------- 7.5 对话表 ----------
CREATE TABLE IF NOT EXISTS ai_chat_conversation (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  title          VARCHAR(256)  NOT NULL DEFAULT '新对话',
  admin_id       BIGINT                 DEFAULT NULL COMMENT '后台对话发起人，商城为 NULL',
  user_id        BIGINT                 DEFAULT NULL COMMENT '商城对话发起人，后台为 NULL',
  source         VARCHAR(16)   NOT NULL COMMENT 'mall / admin',
  role_id        BIGINT                 DEFAULT NULL,
  system_message VARCHAR(1024)          DEFAULT NULL,
  temperature    DOUBLE        NOT NULL DEFAULT 0.7,
  max_tokens     INT           NOT NULL DEFAULT 2048,
  max_contexts   INT           NOT NULL DEFAULT 20 COMMENT '保留的上下文轮数',
  pinned         TINYINT       NOT NULL DEFAULT 0,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_conv_admin (admin_id, source),
  KEY idx_conv_user (user_id, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 对话表';

-- ---------- 7.6 消息表 ----------
CREATE TABLE IF NOT EXISTS ai_chat_message (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT       NOT NULL,
  admin_id        BIGINT                DEFAULT NULL,
  user_id         BIGINT                DEFAULT NULL,
  role_id         BIGINT                DEFAULT NULL,
  type            VARCHAR(16)  NOT NULL COMMENT 'user / assistant / tool',
  content         TEXT,
  tool_calls      TEXT                  COMMENT 'assistant 发起的工具调用 JSON',
  tool_call_id    VARCHAR(128)          DEFAULT NULL COMMENT 'type=tool 时',
  tool_name       VARCHAR(128)          DEFAULT NULL COMMENT 'type=tool 时',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_msg_conv (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 消息表';

-- ---------- 7.7 调用审计表 ----------
CREATE TABLE IF NOT EXISTS ai_call_log (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  admin_id          BIGINT                DEFAULT NULL,
  user_id           BIGINT                DEFAULT NULL,
  conversation_id   BIGINT                DEFAULT NULL,
  scene             VARCHAR(64)  NOT NULL COMMENT 'chat / generate_copy / tool:xxx',
  model             VARCHAR(128)          DEFAULT NULL,
  prompt_tokens     INT                   DEFAULT NULL,
  completion_tokens INT                   DEFAULT NULL,
  total_tokens      INT                   DEFAULT NULL,
  duration_ms       INT                   DEFAULT NULL,
  success           TINYINT      NOT NULL DEFAULT 1,
  error_msg         VARCHAR(512)          DEFAULT NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_call_conv (conversation_id),
  KEY idx_call_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 调用审计表';

-- ---------- 7.9 运营知识库表（P1 用，先建表） ----------
CREATE TABLE IF NOT EXISTS ai_kb_dataset (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(256) NOT NULL COMMENT '知识库名称',
  description VARCHAR(512)          DEFAULT NULL,
  category    VARCHAR(64)           DEFAULT NULL,
  status      TINYINT      NOT NULL DEFAULT 1,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 运营知识库表';

-- ---------- 7.10 运营知识分段表（P1 用，先建表） ----------
CREATE TABLE IF NOT EXISTS ai_kb_segment (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  dataset_id BIGINT       NOT NULL COMMENT '所属知识库',
  source_doc VARCHAR(256)          DEFAULT NULL COMMENT '来源文档名',
  title      VARCHAR(256)          DEFAULT NULL COMMENT '分段标题',
  content    TEXT         NOT NULL COMMENT '分段正文',
  embedding  TEXT                  DEFAULT NULL COMMENT '向量 JSON 数组',
  status     TINYINT      NOT NULL DEFAULT 1,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_segment_dataset (dataset_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 运营知识分段表';

-- ---------- 7.8 ai_kb_qa 加 embedding 列（幂等） ----------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_kb_qa' AND COLUMN_NAME = 'embedding'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE ai_kb_qa ADD COLUMN embedding TEXT NULL COMMENT ''向量 JSON 数组（P1 RAG）'' AFTER hit_count',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 菜单与权限（§9.4）：目录 1000，菜单 1001~1006，按钮 1010~1017
-- 实际一级菜单最大 sort_order 为 11（系统监控），AI 客服取 12
-- ============================================================
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES (1000, 0, 'AI 客服', 'M', '/ai', NULL, NULL, 'RobotOutlined', 12, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES
  (1001, 1000, 'AI 对话',      'C', '/ai/chat',      NULL, 'ai:chat:use',       NULL, 1, 1, 1, NOW(), NOW()),
  (1002, 1000, '商品文案助手', 'C', '/ai/copy',      NULL, 'ai:copy:generate',  NULL, 2, 1, 1, NOW(), NOW()),
  (1003, 1000, '聊天角色',     'C', '/ai/roles',     NULL, 'ai:role:list',      NULL, 3, 1, 1, NOW(), NOW()),
  (1004, 1000, '工具管理',     'C', '/ai/tools',     NULL, 'ai:tool:list',      NULL, 4, 1, 1, NOW(), NOW()),
  (1005, 1000, '知识库管理',   'C', '/ai/knowledge', NULL, 'ai:knowledge:list', NULL, 5, 1, 1, NOW(), NOW()),
  (1006, 1000, '聊天管理',     'C', '/ai/admin',     NULL, 'ai:chat:admin',     NULL, 6, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), perms = VALUES(perms),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

-- 工具权限码按钮（F 类型）。generate_copy 的权限码 ai:copy:generate 已由菜单 1002 承载，不重复建。
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES
  (1010, 1001, 'AI 查询商品',   'F', NULL, NULL, 'ai:tool:query',     NULL, 1, 1, 1, NOW(), NOW()),
  (1011, 1001, 'AI 搜索商品',   'F', NULL, NULL, 'ai:tool:search',    NULL, 2, 1, 1, NOW(), NOW()),
  (1012, 1001, 'AI 推荐商品',   'F', NULL, NULL, 'ai:tool:recommend', NULL, 3, 1, 1, NOW(), NOW()),
  (1013, 1001, 'AI 知识库检索', 'F', NULL, NULL, 'ai:tool:knowledge', NULL, 4, 1, 1, NOW(), NOW()),
  (1014, 1001, 'AI 查询库存',   'F', NULL, NULL, 'ai:tool:stock',     NULL, 5, 1, 1, NOW(), NOW()),
  (1015, 1001, 'AI 查询订单',   'F', NULL, NULL, 'ai:tool:order',     NULL, 6, 1, 1, NOW(), NOW()),
  (1016, 1001, 'AI 查询售后',   'F', NULL, NULL, 'ai:tool:aftersale', NULL, 7, 1, 1, NOW(), NOW()),
  (1017, 1002, 'AI 修改商品',   'F', NULL, NULL, 'ai:tool:update',    NULL, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  perms = VALUES(perms), sort_order = VALUES(sort_order), visible = VALUES(visible),
  status = VALUES(status), update_time = NOW();

-- 角色授权：超管 + 运营全有；客服仅「AI 对话」；仓管无
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON m.id BETWEEN 1000 AND 1017
WHERE r.role_key IN ('admin', 'operator');

INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON m.id IN (1000, 1001)
WHERE r.role_key = 'service';

-- ============================================================
-- 工具种子（9 个）
-- ============================================================
INSERT INTO ai_tool (id, name, description, perm_code, scope, status, sort_order, created_at, updated_at) VALUES
  (1, 'query_product',     '查询单个商品的详细信息，传入商品ID返回名称、价格、库存、规格等', 'ai:tool:query',     'both',  1, 1, NOW(), NOW()),
  (2, 'search_product',    '按关键词、分类、价格区间搜索商品，返回商品列表',               'ai:tool:search',    'both',  1, 2, NOW(), NOW()),
  (3, 'recommend_product', '根据用户需求推荐商品，返回商品卡片数据（名称、价格、主图）',     'ai:tool:recommend', 'both',  1, 3, NOW(), NOW()),
  (4, 'search_knowledge',  '检索客服知识库，回答退换货、发货时间、尺码、运费等问题',         'ai:tool:knowledge', 'both',  1, 4, NOW(), NOW()),
  (5, 'query_stock',       '查询指定 SKU 的实时库存',                                     'ai:tool:stock',     'both',  1, 5, NOW(), NOW()),
  (6, 'query_order',       '查询当前用户的订单列表与订单状态',                             'ai:tool:order',     'both',  1, 6, NOW(), NOW()),
  (7, 'query_aftersale',   '查询售后单的进度与处理状态',                                   'ai:tool:aftersale', 'both',  1, 7, NOW(), NOW()),
  (8, 'generate_copy',     '为商品生成营销文案（标题/副标题/详情描述）',                   'ai:copy:generate',  'admin', 1, 8, NOW(), NOW()),
  (9, 'update_product',    '修改商品的文案类字段（名称/副标题/详情），执行前必须二次确认',   'ai:tool:update',    'admin', 1, 9, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name), description = VALUES(description), perm_code = VALUES(perm_code),
  scope = VALUES(scope), status = VALUES(status), sort_order = VALUES(sort_order), updated_at = NOW();

-- ============================================================
-- 内置聊天角色（2 个，admin_id = NULL 表示系统内置）
-- ============================================================
INSERT INTO ai_chat_role (id, name, avatar, description, category, system_message, admin_id, public_status, status, sort, created_at, updated_at) VALUES
  (1, '商城客服', NULL, '商城默认 AI 客服，解答商品、订单、售后问题并推荐商品', '客服',
   '你是「杰米多多」商城的 AI 客服小助手。职责：1. 解答顾客关于商品、库存、订单、售后的问题；2. 根据顾客需求主动推荐合适的商品；3. 回答客服类问题（退换货、发货、运费等）。规则：商品信息必须以工具查询结果为准，禁止编造价格、库存；语气亲切简洁，单次回答控制在 200 字以内；无法解决的问题引导顾客联系人工客服；不回答与购物无关的问题。',
   NULL, 1, 1, 1, NOW(), NOW()),
  (2, '运营助手', NULL, '后台运营 AI 助手，查数据、生成文案、辅助商品维护', '运营',
   '你是电商后台的运营助手。能力：查询商品、库存、订单、售后数据，生成商品营销文案，经运营确认后修改商品文案字段。规则：所有数据以工具查询结果为准，禁止编造；执行修改类操作前必须先用一句话说明将要变更的内容并等待用户确认；回答简洁专业，突出关键数据。',
   NULL, 1, 1, 2, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name), description = VALUES(description), category = VALUES(category),
  system_message = VALUES(system_message), public_status = VALUES(public_status),
  status = VALUES(status), sort = VALUES(sort), updated_at = NOW();

-- 商城客服绑定 7 个客户工具；运营助手绑定全部 9 个
INSERT IGNORE INTO ai_chat_role_tool (role_id, tool_id)
SELECT 1, t.id FROM ai_tool t WHERE t.id BETWEEN 1 AND 7;

INSERT IGNORE INTO ai_chat_role_tool (role_id, tool_id)
SELECT 2, t.id FROM ai_tool t WHERE t.id BETWEEN 1 AND 9;

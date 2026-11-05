-- 客服机器人知识库
-- 小程序 AI 客服只走知识库，不调用任何大模型。
-- 执行库：e_commerce_db
-- 数据来源：docs/QandA.xlsx，由 recommender-python/scripts/import_kb.py 导入

-- 问答主表：一条标准问法一行
CREATE TABLE IF NOT EXISTS kb_qa (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    category    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '分类，用于同分类加权',
    question    VARCHAR(255) NOT NULL COMMENT '标准问法',
    answer      TEXT         NOT NULL COMMENT '答案原文，直接返回给用户',
    keywords    VARCHAR(255) NOT NULL DEFAULT '' COMMENT '关键词，逗号分隔',
    priority    INT          NOT NULL DEFAULT 0 COMMENT '同分排序，越大越优先',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    hit_count   BIGINT       NOT NULL DEFAULT 0 COMMENT '命中次数，运营参考',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_kb_category_question (category, question),
    KEY idx_kb_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='客服知识库问答';

-- 未命中记录：用于补库，不参与检索
CREATE TABLE IF NOT EXISTS kb_miss (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    question    VARCHAR(255) NOT NULL COMMENT '用户原问题',
    user_id     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '提问用户',
    session_id  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '会话 ID',
    best_score  INT          NOT NULL DEFAULT 0 COMMENT '最高分，便于判断差多少',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb_miss_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='客服知识库未命中记录';

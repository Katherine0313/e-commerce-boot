-- ============================================================================
-- 通用操作日志审计表（operation_log）
--
-- 用途：
--   记录「谁 / 何时 / 哪个 IP / 哪个接口 / 什么参数 / 成功还是失败 / 耗时多久」。
--   由 common 模块的 AOP 切面（@OperationLog 注解）自动采集、异步写入。
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/log/01-operation-log-schema.sql
--
-- 说明：
--   1. 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
--   2. 不加 is_deleted：审计日志只增不删（要删也走归档），加软删列反而没人用。
--   3. COLLATE utf8mb4_general_ci：生产是 MySQL 5.7，不支持 utf8mb4_0900_ai_ci。
--   4. created_at 由 Java 传参写入，不用 NOW() —— 服务器时区是 UTC，NOW() 比本地时间早 8 小时。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '操作日志 ID',
    `user_id`    BIGINT      DEFAULT NULL            COMMENT '操作人 ID（admin.id 或 user.id）',
    `user_type`  VARCHAR(16) DEFAULT NULL            COMMENT '操作人类型：admin / user',
    `module`     VARCHAR(64) NOT NULL                COMMENT '业务模块：aftersales / product / order …',
    `action`     VARCHAR(64) NOT NULL                COMMENT '动作：REVIEW / DELETE / UPDATE_PRICE …',
    `biz_id`     BIGINT      DEFAULT NULL            COMMENT '业务对象 ID',
    `detail`     VARCHAR(255) DEFAULT NULL           COMMENT '补充说明（SpEL 求值）',
    `method`     VARCHAR(16) DEFAULT NULL            COMMENT 'HTTP 方法',
    `uri`        VARCHAR(255) DEFAULT NULL           COMMENT '请求路径',
    `ip`         VARCHAR(64) DEFAULT NULL            COMMENT '客户端 IP（取 X-Forwarded-For 首段）',
    `params`     VARCHAR(2000) DEFAULT NULL          COMMENT '请求参数（JSON，超长截断）',
    `success`    TINYINT     NOT NULL DEFAULT 1      COMMENT '是否成功：1 成功 0 失败',
    `error_msg`  VARCHAR(512) DEFAULT NULL           COMMENT '失败原因',
    `cost_ms`    BIGINT      DEFAULT NULL            COMMENT '耗时（毫秒）',
    `created_at` DATETIME    DEFAULT NULL            COMMENT '创建时间（Java 传参）',
    PRIMARY KEY (`id`),
    KEY `idx_operation_log_module` (`module`),
    KEY `idx_operation_log_user` (`user_id`),
    KEY `idx_operation_log_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志审计表';

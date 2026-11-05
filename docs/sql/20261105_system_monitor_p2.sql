-- 系统监控 P2：动态定时任务 + 任务执行日志 + 新增按钮权限
-- 本地执行后必须同步生产库（项目硬规则）

-- 1) 任务配置
CREATE TABLE IF NOT EXISTS admin_monitor_task (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_name       VARCHAR(100)    NOT NULL COMMENT '任务名称，页面展示用，唯一',
    task_group      VARCHAR(64)     NOT NULL DEFAULT 'default' COMMENT '分组',
    bean_name       VARCHAR(200)    NOT NULL COMMENT 'Spring Bean 名称',
    method_name     VARCHAR(100)    NOT NULL COMMENT '执行方法名',
    method_params   VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '方法参数字符串',
    task_type       TINYINT         NOT NULL DEFAULT 1 COMMENT '1 cron 2 fixedDelay 3 fixedRate',
    schedule_value  VARCHAR(100)    NOT NULL COMMENT 'cron 表达式，或 fixedDelay/fixedRate 的毫秒数',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '0 暂停 1 运行',
    concurrent      TINYINT         NOT NULL DEFAULT 0 COMMENT '0 允许并发 1 禁止并发',
    remark          VARCHAR(255)             DEFAULT NULL COMMENT '备注',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_name (task_name),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务配置';

-- 2) 任务执行记录（只保留最近 30 天，由 monitor-service 每天清理）
CREATE TABLE IF NOT EXISTS admin_monitor_task_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id         BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
    task_name       VARCHAR(100)    NOT NULL COMMENT '任务名称冗余，任务删除后日志仍可读',
    trigger_type    TINYINT         NOT NULL DEFAULT 1 COMMENT '1 定时触发 2 手动触发',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1 成功 0 失败',
    error_msg       VARCHAR(1024)            DEFAULT NULL COMMENT '失败原因',
    cost_ms         INT             NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    started_at      DATETIME        NOT NULL COMMENT '开始时间',
    finished_at     DATETIME                 DEFAULT NULL COMMENT '结束时间',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_started (task_id, started_at),
    KEY idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务执行记录';

-- 3) 初始化现有 4 个任务：迁移到动态调度后，各服务不再用 @Scheduled 直接触发
--    task_name 必须与代码里的 MonitoredTaskRunner#taskName 一致
INSERT INTO admin_monitor_task
  (task_name, task_group, bean_name, method_name, task_type, schedule_value, status, concurrent, remark)
VALUES
  ('logCleanup', 'auth-service', 'logCleanupTaskRunner', 'logCleanup', 1, '0 0 3 * * ?', 1, 1, '清理超过 180 天的操作日志/登录日志'),
  ('closeExpiredOrders', 'order-service', 'orderTaskRunner', 'closeExpiredOrders', 2, '60000', 1, 1, '超时关闭待付款订单并恢复库存'),
  ('unpaidNotify', 'order-service', 'orderTaskRunner', 'unpaidNotify', 2, '60000', 1, 1, '待付款订单订阅消息提醒'),
  ('autoConfirmOrders', 'order-service', 'orderTaskRunner', 'autoConfirmOrders', 2, '60000', 1, 1, '发货 7 天后自动确认收货')
ON DUPLICATE KEY UPDATE
  task_group = VALUES(task_group), bean_name = VALUES(bean_name), method_name = VALUES(method_name),
  task_type = VALUES(task_type), schedule_value = VALUES(schedule_value), status = VALUES(status),
  concurrent = VALUES(concurrent), remark = VALUES(remark), update_time = NOW();

-- 4) 动态任务管理按钮权限（908~910）
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES
  (908, 904, '新增任务', 'F', NULL, NULL, 'monitor:task:add', NULL, 2, 1, 1, NOW(), NOW()),
  (909, 904, '编辑任务', 'F', NULL, NULL, 'monitor:task:edit', NULL, 3, 1, 1, NOW(), NOW()),
  (910, 904, '删除任务', 'F', NULL, NULL, 'monitor:task:delete', NULL, 4, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON m.id BETWEEN 900 AND 910
WHERE r.role_key = 'admin';

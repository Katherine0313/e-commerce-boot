-- 孤儿上传文件清理任务：注册到动态任务调度（admin_monitor_task）
-- 任务由 product-service 执行：扫描 upload/ 目录，删掉数据库里已无引用的上传图片
-- 本地执行后必须同步生产库（项目硬规则）
-- 幂等：可重复执行

-- task_name 必须与代码里的 MonitoredTaskRunner#taskName 一致（orphanImageCleanup）
-- task_group 决定由哪个服务执行，调度器按这个字段把任务派给对应服务
-- schedule_value 用 cron：每天 04:30（凌晨跑，避开业务高峰）
-- concurrent=1：禁止并发，防止上一轮没跑完又排一轮
INSERT INTO admin_monitor_task
  (task_name, task_group, bean_name, method_name, task_type, schedule_value, status, concurrent, remark)
VALUES
  ('orphanImageCleanup', 'product-service', 'orphanImageCleanupTaskRunner', 'orphanImageCleanup', 1, '0 30 4 * * ?', 1, 1, '清理磁盘上已无数据库引用的上传图片')
ON DUPLICATE KEY UPDATE
  task_group = VALUES(task_group), bean_name = VALUES(bean_name), method_name = VALUES(method_name),
  task_type = VALUES(task_type), schedule_value = VALUES(schedule_value), status = VALUES(status),
  concurrent = VALUES(concurrent), remark = VALUES(remark), update_time = NOW();

-- 验证
SELECT id, task_name, task_group, schedule_value, status, concurrent FROM admin_monitor_task WHERE task_name = 'orphanImageCleanup';

-- 停用（不想让它跑时执行，回滚用）：
-- UPDATE admin_monitor_task SET status = 0 WHERE task_name = 'orphanImageCleanup';

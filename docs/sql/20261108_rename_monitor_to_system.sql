-- monitor-service 更名为 system-service：系统管理 + 系统监控统一由 system-service 承载
-- logCleanup 任务的归属服务必须同步改名，否则后台“手动执行”会去 Nacos 找不到旧服务
UPDATE admin_monitor_task
   SET task_group = 'system-service', update_time = NOW()
 WHERE task_group = 'auth-service';

-- 接口文档历史数据同步改名：监控接口归到 system-service
UPDATE admin_api_doc
   SET service_name = 'system-service', update_time = NOW()
 WHERE service_name = 'monitor-service';

-- 原属 auth-service 的系统管理接口（用户/角色/菜单/日志/数据库/接口管理）改归 system-service
UPDATE admin_api_doc
   SET service_name = 'system-service', update_time = NOW()
 WHERE service_name = 'auth-service'
   AND path LIKE '/api/admin/system%';

# common

所有业务服务依赖的框架层（非独立进程）。

## 关键文件

| 文件 | 目的 |
|------|------|
| `Result.java` / `PageVO.java` | 统一响应 |
| `JwtInterceptor` / `JwtUtil` / `UserContext` | 登录态 |
| `PermissionInterceptor` / `RequirePerm` | 按钮权限 |
| `InternalApiInterceptor` | AI 内部调用 |
| `WebMvcConfig` | 静态资源 `/image` `/upload`、拦截器注册 |
| `PromotionCalculator` | 报价促销 |
| `OperationLogAspect` | 操作日志 |
| `DynamicTaskScheduler` / `MonitoredTask*` | 可监控任务 |
| `GlobalExceptionHandler` | 异常转 Result |

## 规范

新增拦截器或静态映射必须考虑网关是否转发该前缀。不要在 common 写具体业务表逻辑（促销与保险策略是已有的共享领域）。

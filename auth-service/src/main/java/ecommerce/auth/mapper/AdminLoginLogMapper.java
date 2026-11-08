package ecommerce.auth.mapper;

import ecommerce.auth.entity.AdminLoginLog;

/**
 * 后台登录/登出日志 Mapper（admin_login_log 表）。
 * <p>
 * auth-service 只保留写入侧（登录/登出同步 insert）；
 * 查询、导出、清理已随系统管理迁移至 system-service。
 */
public interface AdminLoginLogMapper {

    /** 插入一条登录/登出日志 */
    int insert(AdminLoginLog log);
}

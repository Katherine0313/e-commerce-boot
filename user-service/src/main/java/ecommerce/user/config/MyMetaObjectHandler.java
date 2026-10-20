package ecommerce.user.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充：insert 填 create_time/update_time，update 填 update_time。
 * <p>
 * 用 Java 时间（LocalDateTime.now()）而非数据库 NOW()：MySQL 服务器时区是 UTC，
 * NOW() 会比本地时间早 8 小时，项目约定时间戳统一由 Java 传参。
 * <p>
 * 配合 User 上的 @TableField(fill = ...) 使用；update 时 Service 层传实体（如 new User()）
 * 触发 updateFill（entity 为 null 时 fill 不生效）。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}

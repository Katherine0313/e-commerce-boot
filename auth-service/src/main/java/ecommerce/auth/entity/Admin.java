package ecommerce.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台管理员实体（对应 admin 表，与 C 端 user 表分开）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admin {
    private Long id;
    private String username;
    /** 密码（开发期明文存储，生产换 BCrypt 后此处存密文） */
    private String password;
    private String name;
    private String phone;
    private Integer status;
    /** 软删标记：0 正常 1 已删除（已删除的账号查不到，也不能登录） */
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
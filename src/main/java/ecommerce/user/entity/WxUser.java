package ecommerce.user.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 微信用户实体，对应数据库 wx_user 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WxUser {
    /** 主键，自增 */
    private Long id;
    /** 微信 openid，唯一标识一个用户 */
    private String openid;
    /** 昵称 */
    private String name;
    /** 手机号 */
    private String phone;
    /** 性别 */
    private String sex;
    /** 身份证号 */
    private String idNumber;
    /** 头像地址 */
    private String avatar;
    /** 创建时间，由数据库默认值填充，insert 时不传该字段 */
    private LocalDateTime createTime;
}

package ecommerce.user.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String openid;
    private String name;
    private String phone;
    private String sex;
    private String avatar;
    /** 软删除：1 已注销 0 正常（注销走置 1，不物理删；orders.user_id 是 RESTRICT 删不掉） */
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

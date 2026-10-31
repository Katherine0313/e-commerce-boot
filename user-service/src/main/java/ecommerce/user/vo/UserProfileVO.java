package ecommerce.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO {
    /** 用户 ID */
    @Schema (description = "用户 ID")
    private Long id;
    /** 昵称 */
    @Schema (description = "昵称，未设置时为 null")
    private String name;
    /** 登录用户名（PC 注册账号有，小程序用户为 null） */
    @Schema(description = "登录用户名，小程序用户为 null")
    private String username;
    /** 头像地址 */
    @Schema (description = "头像地址，未上传时为 null")
    private String avatar;
    /** 手机号 */
    @Schema (description = "手机号，未绑定时为 null")
    private String phone;
    /** 性别 */
    @Schema(description = "性别，未设置时为 null")
    private String sex;
    /** 注册时间 */
    @Schema (description = "注册时间")
    private LocalDateTime createTime;

}

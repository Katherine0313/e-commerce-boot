package ecommerce.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("`user`") // user 是 MySQL 关键字，必须反引号包裹，否则生成的 FROM user 会报错
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 微信 openid；PC 账号没有微信身份，用 pc_ 前缀占位（该列 NOT NULL + 唯一键） */
    private String openid;
    private String name;
    private String phone;
    /**
     * PC 商城登录密码（BCrypt）。
     * select=false：常规查询不把密文带到业务层，登录时由 UserMapper.findForLoginByPhone 单独读取。
     */
    @TableField(select = false)
    private String password;
    private String sex;
    private String avatar;
    /** 会员等级 ID（member_level.id），默认 1 普通会员 */
    private Long memberLevelId;
    /** 成长值：已支付实付 - 退款，1 元 = 1 成长值 */
    private Integer growthValue;
    /** 累计实付（已扣退款） */
    private java.math.BigDecimal totalPaidAmount;
    /** 已支付订单数 */
    private Integer paidOrderCount;
    /**
     * 软删除：1 已注销 0 正常（注销走置 1，不物理删；orders.user_id 是 RESTRICT 删不掉）。
     * 故意不接 @TableLogic：登录查 openid 要查全部用户（含已注销），逻辑删除会自动拼 is_deleted=0，
     * 导致注销用户的 openid「查不到 → 重新 INSERT 撞 uk_openid」。是否可用交给 Service 手动判。
     */
    @TableField("is_deleted")
    private Integer isDeleted;
    /** 创建时间：insert 时由 MyMetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间：insert/update 时由 MyMetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

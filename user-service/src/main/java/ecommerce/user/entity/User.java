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
    /** 所属店铺（模板店=1，多租户沙箱隔离键） */
    private String openid;
    private String name;
    private String phone;
    private String sex;
    private String avatar;
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

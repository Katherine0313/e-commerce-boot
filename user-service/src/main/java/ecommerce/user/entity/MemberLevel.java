package ecommerce.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会员等级（member_level 表）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("member_level")
public class MemberLevel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 成长值门槛 */
    private Integer growthThreshold;
    /** 权益文案，C 端展示 */
    private String benefits;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

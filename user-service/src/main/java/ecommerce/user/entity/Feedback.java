package ecommerce.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 意见反馈实体（feedback 表）。
 * <p>
 * images 存 JSON 数组字符串（截图相对路径 /image/feedback/xxx），与 product_reviews.images 同款；
 * createdAt / updatedAt 由 Service 用 Java 时间写入，不用 MySQL NOW()（服务器时区 UTC 会差 8 小时）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("feedback")
public class Feedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 反馈类型：suggestion / product / order / other */
    private String type;
    private String content;
    /** 截图 JSON 数组 */
    private String images;
    private String contact;
    /** 处理状态：0 未处理 1 已处理 */
    private Integer status;
    @TableField("is_deleted")
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

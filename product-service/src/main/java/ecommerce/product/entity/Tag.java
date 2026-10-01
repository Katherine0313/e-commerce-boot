package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品标签实体。productCount 是物化计数列（写操作维护），读路径直接读列。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag {
    private Long id;
    private Long shopId;
    private String name;
    private String code;
    private String type;
    private String color;
    private Integer sortOrder;
    private Integer status;
    private Integer productCount;
    private LocalDateTime createTime;
    private Long createdBy;
    private LocalDateTime updateTime;
    private Long updatedBy;
}

package ecommerce.order.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款账户 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceAccountVO {
    private Long id;
    private String accountName;
    private String channel;
    private String channelDesc;
    private String merchantNo;
    private Integer isDefault;
    private Integer status;
    private String statusDesc;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

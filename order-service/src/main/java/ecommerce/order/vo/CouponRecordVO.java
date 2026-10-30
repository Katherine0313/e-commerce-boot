package ecommerce.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台券领取/使用记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRecordVO {
    private Long id;
    private String code;
    private Long userId;
    private String userName;
    private String phone;
    private String status;
    private String statusDesc;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receiveTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usedTime;
    private Long orderId;
}

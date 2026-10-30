package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 后台定向发券请求。
 */
@Data
public class AdminCouponGrantDTO {
    @Schema(description = "指定用户 ID 列表")
    private List<Long> userIds;
    @Schema(description = "是否发给全部有效用户（与 userIds 二选一）")
    private Boolean allUsers;
}

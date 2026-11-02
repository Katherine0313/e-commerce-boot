package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台评价列表筛选条件（列表与导出 Excel 共用同一套，保证「导出的」和「看到的」一致）。
 * <p>
 * 时间用 String 收，服务层解析成 LocalDateTime 再查 —— 解析失败就当不筛（与 AdminOrderServiceImpl 同款）。
 */
@Data
public class AdminReviewQueryDTO {

    @Schema(description = "商品名模糊匹配")
    private String keyword;

    @Schema(description = "订单号模糊匹配")
    private String orderNo;

    @Schema(description = "星级：1~5")
    private Integer rating;

    @Schema(description = "是否虚拟评价：1 虚拟 0 真实")
    private Integer isVirtual;

    @Schema(description = "显示状态：1 显示 0 隐藏（对应 product_reviews.status）")
    private Integer visible;

    @Schema(description = "创建时间起，格式 yyyy-MM-dd HH:mm")
    private String startDate;

    @Schema(description = "创建时间止，格式 yyyy-MM-dd HH:mm")
    private String endDate;

    /** 解析后的创建时间起（服务层填充） */
    private transient LocalDateTime start;

    /** 解析后的创建时间止（服务层填充） */
    private transient LocalDateTime end;
}

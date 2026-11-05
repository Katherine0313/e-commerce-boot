package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收款单列表筛选条件（列表、统计、导出共用一套，保证「看到的」「导出的」「统计的」一致）。
 */
@Data
public class FinanceReceiptQueryDTO {

    @Schema(description = "收款单号模糊匹配")
    private String receiptNo;

    @Schema(description = "订单号模糊匹配")
    private String orderNo;

    @Schema(description = "买家用户 ID")
    private Long userId;

    @Schema(description = "渠道：WeChat / Alipay / BankCard")
    private String channel;

    @Schema(description = "状态：0 待审核 / 1 已审核")
    private Integer status;

    @Schema(description = "收款时间起，格式 yyyy-MM-dd HH:mm:ss")
    private String startDate;

    @Schema(description = "收款时间止，格式 yyyy-MM-dd HH:mm:ss")
    private String endDate;

    /** 解析后的收款时间起（服务层填充） */
    private transient LocalDateTime start;

    /** 解析后的收款时间止（服务层填充） */
    private transient LocalDateTime end;
}

package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 收款账户列表筛选条件。
 */
@Data
public class FinanceAccountQueryDTO {

    @Schema(description = "渠道：WeChat / Alipay / BankCard")
    private String channel;

    @Schema(description = "状态：1 启用 / 0 停用")
    private Integer status;

    @Schema(description = "账户名称 / 商户号模糊匹配")
    private String keyword;
}

package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 收款账户新增 / 编辑入参。
 * <p>
 * 编辑只改名称、商户号、备注；启停与「设为默认」走单独接口，避免一次提交改太多状态。
 */
@Data
public class FinanceAccountDTO {

    @Schema(description = "账户名称，如 微信商户-主店")
    private String accountName;

    @Schema(description = "渠道：WeChat / Alipay / BankCard")
    private String channel;

    @Schema(description = "商户号，同渠道唯一")
    private String merchantNo;

    @Schema(description = "是否设为同渠道默认：1 是 / 0 否（仅新增时生效）")
    private Integer isDefault;

    @Schema(description = "状态：1 启用 / 0 停用（仅新增时生效）")
    private Integer status;

    private String remark;
}

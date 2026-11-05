package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收款账户实体（对应 finance_account 表）。
 * <p>
 * 一个渠道可以配置多个商户账户，其中最多一个 is_default=1，收款单生成时按渠道取默认启用账户。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceAccount {
    private Long id;
    /** 账户名称，如「微信商户-主店」 */
    private String accountName;
    /** 渠道：WeChat / Alipay / BankCard（与 orders.pay_type 同字典） */
    private String channel;
    /** 商户号，仅展示与对账用，不存密钥 */
    private String merchantNo;
    /** 同渠道默认：1 是 / 0 否 */
    private Integer isDefault;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    private String remark;
    /** 软删除：1 已删除 / 0 正常 */
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

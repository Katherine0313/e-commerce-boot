package ecommerce.order.service;

import ecommerce.common.PageVO;
import ecommerce.order.dto.FinanceReceiptQueryDTO;
import ecommerce.order.entity.Order;
import ecommerce.order.vo.FinanceReceiptSummaryVO;
import ecommerce.order.vo.FinanceReceiptVO;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

/**
 * 收款单 Service。
 */
public interface FinanceReceiptService {

    PageVO<FinanceReceiptVO> list(int page, int pageSize, FinanceReceiptQueryDTO query);

    FinanceReceiptVO detail(Long id);

    /** 审核：只有待审核可审核，审核后即为终态 */
    void audit(Long id, String remark);

    /** 改备注：已审核单据只读 */
    void updateRemark(Long id, String remark);

    FinanceReceiptSummaryVO summary(FinanceReceiptQueryDTO query);

    void exportExcel(HttpServletResponse response, FinanceReceiptQueryDTO query);

    /**
     * 支付成功时生成收款单（与 payOrder 同一事务）。
     * 已存在则直接跳过，account_id 取渠道默认启用账户，取不到留空并告警。
     */
    void createForPaidOrder(Order order, String payType, LocalDateTime paidAt);
}

package ecommerce.order.service;

import ecommerce.common.PageVO;
import ecommerce.order.dto.FinanceAccountDTO;
import ecommerce.order.dto.FinanceAccountQueryDTO;
import ecommerce.order.vo.FinanceAccountVO;

import java.util.List;

/**
 * 收款账户 Service。
 */
public interface FinanceAccountService {

    PageVO<FinanceAccountVO> list(int page, int pageSize, FinanceAccountQueryDTO query);

    /** 启用中的账户（收款单页/其他下拉用） */
    List<FinanceAccountVO> listEnabled();

    FinanceAccountVO create(FinanceAccountDTO dto);

    FinanceAccountVO update(Long id, FinanceAccountDTO dto);

    void updateStatus(Long id, Integer status);

    /** 设为同渠道默认（互斥） */
    void setDefault(Long id);

    /** 软删除，历史单据仍可查 */
    void delete(Long id);
}

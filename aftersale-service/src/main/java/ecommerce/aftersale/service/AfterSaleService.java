package ecommerce.aftersale.service;

import ecommerce.aftersale.dto.CreateAfterSaleDTO;
import ecommerce.aftersale.dto.SubmitReturnDTO;
import ecommerce.aftersale.vo.AfterSaleLogVO;
import ecommerce.aftersale.vo.AfterSaleStatusConstantsVO;
import ecommerce.aftersale.vo.AfterSaleVO;

import java.util.List;

/**
 * 售后 Service。
 * <p>
 * 当前登录用户从 UserContext.getUserId() 取（单店模式）。
 * C 端接口按用户归属隔离（通过 order_id JOIN orders 的 user_id）。
 */
public interface AfterSaleService {

    /** 申请售后（整单），初始状态 reviewing */
    AfterSaleVO create(CreateAfterSaleDTO dto);

    /** 当前用户售后列表（可选状态/关键词/时间范围筛选，不含明细/子表字段） */
    List<AfterSaleVO> list(String status, String search, String startDate, String endDate, String dateField);

    /** 售后详情（含明细 + 子表平铺字段） */
    AfterSaleVO getById(Long id);

    /** 按订单查售后单（判断订单是否已申请售后用），无则返回 null */
    AfterSaleVO getByOrderId(Long orderId);

    /** 用户提交退货方式（status=returning 时可调用） */
    AfterSaleVO submitReturn(Long id, SubmitReturnDTO dto);

    /** 售后变更流水（买家可见，不含商家内部备注） */
    List<AfterSaleLogVO> listLogs(Long id);

    /** 售后状态/原因/类型常量 */
    AfterSaleStatusConstantsVO statusConstants();
}

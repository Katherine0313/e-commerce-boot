package ecommerce.aftersale.service;

import ecommerce.aftersale.dto.AdminAfterSaleDTO;
import ecommerce.aftersale.vo.AfterSaleLogVO;
import ecommerce.aftersale.vo.AfterSaleVO;

import java.util.List;

/**
 * 后台售后管理 Service（/api/admin/after-sales）。
 */
public interface AdminAfterSaleService {
    // 售后列表（按店铺隔离，可选状态/关键词/时间范围筛选）
    List<AfterSaleVO> list(String status, String search, String startDate, String endDate, String dateField);

    // 售后详情（含明细 + 子表平铺字段 + 当前可执行动作 availableActions）
    AfterSaleVO getById(Long id);

    // 审核售后：更新主表状态 + 商家备注 + 子表对应时间戳（只允许状态机白名单内的跃迁）
    void review(Long id, AdminAfterSaleDTO dto);

    // 删除售后：主表 + 明细 + 子表软删
    void delete(Long id);

    // 售后变更流水（后台可见，含商家内部备注）
    List<AfterSaleLogVO> listLogs(Long id);
}

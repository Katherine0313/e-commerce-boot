package ecommerce.aftersale.service;

import ecommerce.aftersale.dto.AdminAfterSaleDTO;

/**
 * 后台售后管理 Service（/api/admin/after-sales）。
 */
public interface AdminAfterSaleService {
    // 审核售后：更新主表状态 + 商家备注 + 子表对应时间戳
    void review(Long id, AdminAfterSaleDTO dto);

    // 删除售后：主表 + 明细 + 子表软删
    void delete(Long id);
}
